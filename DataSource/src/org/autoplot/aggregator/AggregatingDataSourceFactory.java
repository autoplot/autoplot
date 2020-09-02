/*
 * AggregatingDataSourceFactory.java
 *
 * Created on October 25, 2007, 11:02 AM
 */
package org.autoplot.aggregator;

import java.io.File;
import java.io.FileNotFoundException;
import org.das2.datum.DatumRangeUtil;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.fsm.FileStorageModel;
import org.das2.util.filesystem.FileSystem;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.das2.datum.DatumRange;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;
import org.autoplot.datasource.DefaultTimeSeriesBrowse;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.das2.util.filesystem.LocalFileSystem;

/**
 * ftp://cdaweb.gsfc.nasa.gov/pub/data/noaa/noaa14/$Y/noaa14_meped1min_sem_$Y$m$d_v01.cdf?timerange=2000-01-01
 * @author jbf
 */
public class AggregatingDataSourceFactory implements DataSourceFactory {
    public static final String PROB_NO_TIMERANGE_PROVIDED = "no timerange provided";
    public static final String PROB_PARSE_ERROR_IN_TIMERANGE = "parse error in timeRange";
    
    private static final Logger logger= LoggerManager.getLogger("apdss.agg");
    
    private DataSourceFactory delegateFactory=null;

    /** Creates a new instance of AggregatingDataSourceFactory */
    public AggregatingDataSourceFactory() {
    }

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        String suri=  DataSetURI.fromUri(uri);
        if ( suri.contains("&timerange") && !suri.contains("?") ) {
            throw new IllegalArgumentException("data URI contains &timerange but no question mark.");
        }
        if ( delegateFactory==null ) {
            delegateFactory= AggregatingDataSourceFactory.getDelegateDataSourceFactory( suri );
        }
        if ( delegateFactory==null ) {
            throw new IllegalArgumentException("Unable to identify data source for "+suri);
        }
        AggregatingDataSource ads = new AggregatingDataSource(uri,delegateFactory);
        String surl = DataSetURI.fromUri( uri );
        FileStorageModel fsm = getFileStorageModel(surl);
        ads.setFsm(fsm);
        URISplit split = URISplit.parse(surl);
        Map parms = URISplit.parseParams(split.params);
        String stimeRange= (String) parms.get("timerange");
        if ( stimeRange!=null ) {
            stimeRange= stimeRange.replaceAll("\\+", " " );
            ads.setViewRange(DatumRangeUtil.parseTimeRange(stimeRange));
            parms.remove("timerange");
        }
        if (parms.size() > 0) {
            ads.setParams(URISplit.formatParams(parms));
        }

        return ads;
    }
    
    /**
     * return the index of the agg part of the uri.  Like 
     * FileStorageModel.splitIndex(surl), but also treats * as $x.
     * @param surl the URI as a string.
     * @return the index of the first part of the aggregation, which will
     * be one more than the position of the static part's last slash.
     */
    public static int splitIndex(String surl) { // See also org/autoplot/pngwalk/WalkUtil.java splitIndex...
        surl= surl.replaceAll("\\*","\\$x");
        return FileStorageModel.splitIndex(surl);
    }

    /**
     * return the FileStorageModel for the URI.  
     * @param suri eg. file:/tmp/foo/$Y/$Y$m.dat
     * @return the FileStorageModel, eg. for $Y/$Y$m.dat.
     * @throws IOException 
     * @see #splitIndex(java.lang.String) which splits the static part from the agg part.
     */
    public static FileStorageModel getFileStorageModel(String suri) throws IOException {
        URISplit split= URISplit.parse(suri);
        String surl= split.surl; // support cases where resource URI is not yet valid.
        int i = surl.indexOf('?');

        String sansArgs = i == -1 ? surl : surl.substring(0, i);

        i = splitIndex(sansArgs);
        FileSystem fs;
        
        if ( i==-1 ) i= sansArgs.lastIndexOf("/");
        
        fs = FileSystem.create( DataSetURI.toUri(sansArgs.substring(0, i)));

        if ( sansArgs.charAt(i)=='/' ) i=i+1; // kludgy
        String spec= sansArgs.substring(i).replaceAll("\\%", "\\$");
        FileStorageModel fsm = FileStorageModel.create(fs, spec );

        return fsm;
    }

    /**
     * get a representative file, using one from the timerange in the surl if available.  This will help
     * users to work around problems where the first folder (2012) doesn't contain a representative (2012/20120212.dat)
     * but the second (2013) does.
     * @param fsm
     * @param surl
     * @return
     * @throws IOException
     */
    private static String getRepresentativeFile( FileStorageModel fsm, String surl, ProgressMonitor mon ) throws IOException {

        if ( mon==null ) {
            mon= new NullProgressMonitor();
        }

        //System.err.println("getRepr "+Integer.toHexString( mon.hashCode() ) );

        try {

            URISplit split = URISplit.parse(surl);
            Map<String,String> params= URISplit.parseParams(split.params);

            String delegateFile;
            String stimeRange= params.get( "timerange" );
            if ( stimeRange!=null ) {
                DatumRange tdr= null;
                try {
                    tdr= DatumRangeUtil.parseTimeRange(stimeRange);
                } catch ( ParseException ex ) {
                    logger.finer("unable to parse timerange, just use default delegate");
                }
                if ( tdr!=null ) {
                    String[] names= fsm.getBestNamesFor( tdr, mon.getSubtaskMonitor("get best names") );
                    if ( names.length>0 ) {
                        delegateFile= names[0];
                    } else {
                        delegateFile= fsm.getRepresentativeFile( mon.getSubtaskMonitor("get delegate") );
                    }
                } else {
                    delegateFile= fsm.getRepresentativeFile( mon.getSubtaskMonitor("get delegate") );
                }
            } else {
                delegateFile= fsm.getRepresentativeFile( mon.getSubtaskMonitor("get delegate") );
            }
            return delegateFile;
            
        } finally {
            mon.finished();
        }
    }

    private static CompletionContext getDelegateDataSourceCompletionContext(CompletionContext cc) throws IOException {

        String surl = cc.surl;
        int carotPos = cc.surlpos;
        int urlLen = 0; //this is the position as we parse and process surl.

        surl= surl.replaceAll("%25","%");
        FileStorageModel fsm = getFileStorageModel(surl);
        
        String delegateFile= getRepresentativeFile( fsm, surl, null );

        if (delegateFile == null) {
            throw new FileNotFoundException("unable to find any files matching\n"+surl);
        }

        URISplit split = URISplit.parse(surl);

        String encodedDelegateFile= delegateFile.replaceAll(":","%3A");
        String delegateFfile;
        if ( fsm.getFileSystem() instanceof LocalFileSystem ) {
            delegateFfile= new File( ((LocalFileSystem)fsm.getFileSystem()).getLocalRoot(), URISplit.uriEncode(encodedDelegateFile) ).toString();
        } else {
            delegateFfile= fsm.getFileSystem().getRootURI().resolve(URISplit.uriEncode(encodedDelegateFile)).toString();
        }
        urlLen += delegateFfile.length();
        carotPos -= urlLen - delegateFfile.length();
        split.file = delegateFfile;

        int i = surl.lastIndexOf("timerange=", cc.surlpos);

        if (i != -1) {
            int i1 = surl.indexOf('&', i);
            carotPos -= (i1 - i);
        }

        Map parms = URISplit.parseParams(split.params);

        parms.remove("timerange");

        split.params = URISplit.formatParams(parms);

        String delegateUrl = URISplit.format(split);

        CompletionContext delegatecc = new CompletionContext();
        delegatecc.surl = delegateUrl;
        delegatecc.surlpos = carotPos;
        delegatecc.context = cc.context;
        
        String decodedDelegateFile= delegateFfile.replaceAll("%3A",":");
        //delegatecc.resource= new URL( delegateFfile );
        delegatecc.resourceURI = DataSetURI.toUri(decodedDelegateFile);

        return delegatecc;
    }

    /**
     * @param suri the aggregation, containing the template and the timerange parameter.
     * @param mon a progress monitor.
     * @return the URI for each granule.
     * @throws java.io.IOException
     * @throws IllegalArgumentException if it is not able to find any data files.
     */
    public static String getDelegateDataSourceFactoryUri(String suri, ProgressMonitor mon) throws IOException, IllegalArgumentException {

        URISplit split= URISplit.parse(suri);

        Map parms = URISplit.parseParams(split.params);
        String timeRange= ((String) parms.remove("timerange"));
        if ( timeRange!=null ) timeRange= timeRange.replaceAll("\\+", " ");

        parms.remove("reduce");
        
        split.params = URISplit.formatParams(parms);

        FileStorageModel fsm = getFileStorageModel( DataSetURI.fromUri(split.resourceUri) );

        String file= null;
        if ( timeRange!=null && !timeRange.equals("") ) {
            try {
                DatumRange timeRangeDatum= DatumRangeUtil.parseTimeRange(timeRange);
                String[] names = fsm.getBestNamesFor(timeRangeDatum,new NullProgressMonitor());
                FileSystem fs= fsm.getFileSystem();
                for (String name : names) {
                    // look for a file which is not empty.
                    if (fs.getFileObject(name).getSize() > 0) {
                        file = name;
                        break;
                    }
                }
                if ( file==null ){
                    if ( names.length>0 ) {
                        file= names[0];
                    }
                }
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }

        if ( file==null ) {
            file = getRepresentativeFile( fsm, suri, mon );
        }

        if (file == null) {
            throw new IllegalArgumentException( "unable to find any files in "+fsm );
        }

        if ( fsm.getFileSystem() instanceof LocalFileSystem ) {
            split.resourceUri= fsm.getFileSystem().getFileObject(file).getFile().toURI();
        } else {
            split.resourceUri= fsm.getFileSystem().getRootURI().resolve(file.replaceAll(":","%3A"));
        }
        String scompUrl = DataSetURI.fromUri( split.resourceUri );
        if (split.params.length() > 0) {
            scompUrl += "?" + split.params;
        }

        split.file=  DataSetURI.fromUri( split.resourceUri );
        //split.path= // URISplit.format ignores this.
        split.surl= scompUrl; 
        return URISplit.format(split);
    }

    public static DataSourceFactory getDelegateDataSourceFactory(String surl) throws IOException, IllegalArgumentException, URISyntaxException {
        String delegateSurl = getDelegateDataSourceFactoryUri(surl, new NullProgressMonitor() );
        URISplit split= URISplit.parse(surl);
        URISplit delegateSplit= URISplit.parse(delegateSurl);
        delegateSplit.vapScheme= split.vapScheme; // TODO: verify this
        URI uri= DataSetURI.toUri( URISplit.format(delegateSplit) );
        return DataSetURI.getDataSourceFactory( uri, new NullProgressMonitor());
    }


    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc,org.das2.util.monitor.ProgressMonitor mon) throws Exception {
        if ( delegateFactory==null ) {
            delegateFactory= getDelegateDataSourceFactory(cc.surl);
        }
        DataSourceFactory f = delegateFactory;
        List<CompletionContext> result = new ArrayList<>();
        CompletionContext delegatecc = getDelegateDataSourceCompletionContext(cc);

        List<CompletionContext> delegateCompletions = f.getCompletions(delegatecc,mon);
        result.addAll(delegateCompletions);

        if (cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {
            result.add(new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "timerange=" ));

        } else if (cc.context == CompletionContext.CONTEXT_PARAMETER_VALUE) {
            String paramName = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            if (paramName.equals("timerange")) {
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<timerange>"));
            }
        } else {
        }
        return result;
    }

    protected static boolean hasTimeFields( String surl ) {
        if ( surl.contains("%Y") || surl.contains("%25Y" ) ) {
            logger.warning("URIs should no longer contain percents (%s).");
            return true;
        }
        int ipercy = surl.lastIndexOf("%Y");
        if (ipercy == -1) ipercy = surl.lastIndexOf("$Y");
        if (ipercy == -1) ipercy = surl.lastIndexOf("$y");
        if (ipercy == -1) ipercy = surl.lastIndexOf("$(o");
        if (ipercy == -1) ipercy = surl.lastIndexOf("$(periodic");
        return ipercy != -1;
    }
    
    @Override
    public boolean reject( String surl, List<String> problems, ProgressMonitor mon) {
        URISplit split = URISplit.parse(surl);
        Map map = URISplit.parseParams(split.params);

        try {
            if ( hasTimeFields(surl) && DefaultTimeSeriesBrowse.reject( map, problems ) ) {
                return true;
            }

            String delegateSurl = getDelegateDataSourceFactoryUri(surl,mon);
            
            String avail= (String) map.get("avail");
            if ( avail==null || !avail.equals("T") ) {
                if ( delegateFactory==null ) {
                    delegateFactory= getDelegateDataSourceFactory(surl);
                    if ( delegateFactory==null ) {
                        return true;
                    }
                }
             
                boolean delegateRejects= delegateFactory.reject( delegateSurl, problems, mon );
                if ( delegateRejects && problems.size()==1 && problems.get(0).equals(TimeSeriesBrowse.PROB_NO_TIMERANGE_PROVIDED) ) {
                    delegateRejects= false;
                }
                return delegateRejects;
            } else {
                return false;
            }
            
        } catch (URISyntaxException | IOException e) {
            problems.add(e.getMessage());
            logger.log( Level.SEVERE, surl, e );
            return false;
        } catch (IllegalArgumentException e) {
            problems.add(e.getMessage());
            logger.log( Level.SEVERE, surl, e );
            return true;
        }
    }

    public void setDelegateDataSourceFactory(DataSourceFactory delegateFactory) {
        this.delegateFactory= delegateFactory;
    }

    @Override
    public <T> T getCapability(Class<T> clazz) {
        if ( clazz==TimeSeriesBrowse.class ) {
           return (T) new DefaultTimeSeriesBrowse();
        }
        return null;
    }

    @Override
    public boolean supportsDiscovery() {
        return false;
    }

    @Override
    public boolean isFileResource() {
        return false;
    }

    @Override
    public String getDescription() {
        return "Combination of Files From a Supported Data Source";
    }
    
}
