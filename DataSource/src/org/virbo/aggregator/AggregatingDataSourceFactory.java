/*
 * AggregatingDataSourceFactory.java
 *
 * Created on October 25, 2007, 11:02 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.aggregator;

import org.das2.datum.DatumRangeUtil;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.fsm.FileStorageModelNew;
import org.das2.util.filesystem.FileSystem;
import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.das2.datum.DatumRange;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.URISplit;

/**
 * ftp://cdaweb.gsfc.nasa.gov/pub/istp/noaa/noaa14/$Y/noaa14_meped1min_sem_$Y$m$d_v01.cdf?timerange=2000-01-01
 * @author jbf
 */
public class AggregatingDataSourceFactory implements DataSourceFactory {

    private DataSourceFactory delegateFactory=null;

    /** Creates a new instance of AggregatingDataSourceFactory */
    public AggregatingDataSourceFactory() {
    }

    public DataSource getDataSource(URI uri) throws Exception {
        if ( delegateFactory==null ) {
            delegateFactory= AggregatingDataSourceFactory.getDelegateDataSourceFactory( DataSetURI.fromUri(uri) );
        }
        AggregatingDataSource ads = new AggregatingDataSource(uri,delegateFactory);
        String surl = DataSetURI.fromUri( uri );
        FileStorageModelNew fsm = getFileStorageModel(surl);
        ads.setFsm(fsm);
        URISplit split = URISplit.parse(surl);
        Map parms = URISplit.parseParams(split.params);
        String stimeRange= (String) parms.get("timerange");
        stimeRange= stimeRange.replaceAll("\\+", " " );
        ads.setViewRange(DatumRangeUtil.parseTimeRange(stimeRange));
        parms.remove("timerange");
        if (parms.size() > 0) {
            ads.setParams(URISplit.formatParams(parms));
        }

        return ads;
    }

    private static int splitIndex(String surl) {
        int i0 = surl.indexOf("%Y");
        if ( i0==-1 ) i0 = surl.indexOf("$Y");
        int i1;
        i1 = surl.indexOf("%y");
        if ( i1==-1 ) i1 = surl.indexOf("$y");
        if ( i0==-1 ) i0= Integer.MAX_VALUE;
        if ( i1==-1 ) i1= Integer.MAX_VALUE;
        int i= Math.min(i0,i1);
        i = surl.lastIndexOf('/', i);
        return i;
    }

    public static FileStorageModelNew getFileStorageModel(String suri) throws IOException {
        URISplit split= URISplit.parse(suri);
        String surl= split.surl; // support cases where resource URI is not yet valid.
        int i = surl.indexOf('?');

        String sansArgs = i == -1 ? surl : surl.substring(0, i);

        i = splitIndex(sansArgs);
        FileSystem fs;
        fs = FileSystem.create( DataSetURI.toUri(sansArgs.substring(0, i)));

        if ( sansArgs.charAt(i)=='/' ) i=i+1; // kludgy
        String spec= sansArgs.substring(i).replaceAll("\\$", "%");
        FileStorageModelNew fsm = FileStorageModelNew.create(fs, spec );

        return fsm;
    }

    public static CompletionContext getDelegateDataSourceCompletionContext(CompletionContext cc) throws IOException {

        String surl = cc.surl;
        int carotPos = cc.surlpos;
        int urlLen = 0; //this is the position as we parse and process surl.

        surl= surl.replaceAll("%25","%");
        FileStorageModelNew fsm = getFileStorageModel(surl);

        String delegateFile = fsm.getRepresentativeFile(new NullProgressMonitor());

        if (delegateFile == null) {
            throw new IllegalArgumentException("unable to find any files");
        }

        URISplit split = URISplit.parse(surl);

        String delegateFfile = fsm.getFileSystem().getRootURI().resolve(delegateFile).toString();
        urlLen += delegateFfile.length();
        carotPos -= urlLen - delegateFfile.length();
        split.file = delegateFfile;

        int i = surl.lastIndexOf("timerange=", cc.surlpos);

        if (i != -1) {
            int i1 = surl.indexOf("&", i);
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
        
        //delegatecc.resource= new URL( delegateFfile );
        delegatecc.resourceURI = DataSetURI.toUri(delegateFfile);

        return delegatecc;
    }

    /**
     * @throws IllegalArgumentException if it is not able to find any data files.
     */
    public static String getDelegateDataSourceFactoryUri(String suri) throws IOException, IllegalArgumentException {

        URISplit split= URISplit.parse(suri);

        Map parms = URISplit.parseParams(split.params);
        String timeRange= ((String) parms.remove("timerange"));
        if ( timeRange!=null ) timeRange= timeRange.replaceAll("\\+", " ");

        split.params = URISplit.formatParams(parms);

        FileStorageModelNew fsm = getFileStorageModel( DataSetURI.fromUri(split.resourceUri) );

        String file= null;
        if ( timeRange!=null && !timeRange.equals("") ) {
            try {
                DatumRange timeRangeDatum= DatumRangeUtil.parseTimeRange(timeRange);
                String[] names = fsm.getBestNamesFor(timeRangeDatum,new NullProgressMonitor());
                if ( names.length>0 ) {
                    file= names[0];
                }
            } catch (ParseException ex) {
                Logger.getLogger(AggregatingDataSourceFactory.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        if ( file==null ) {
            file = fsm.getRepresentativeFile(new NullProgressMonitor());
        }

        if (file == null) {
            throw new IllegalArgumentException("unable to find any files");
        }

        split.resourceUri= fsm.getFileSystem().getRootURI().resolve(file);
        String scompUrl = DataSetURI.fromUri( split.resourceUri );
        if (split.params.length() > 0) {
            scompUrl += "?" + split.params;
        }

        split.file=  DataSetURI.fromUri( split.resourceUri );
        //split.path= // URISplit.format ignores this.
        split.surl= scompUrl; 
        return URISplit.format(split);
    }

    public static DataSourceFactory getDelegateDataSourceFactory(String surl) throws IOException, IllegalArgumentException {
        String delegateSurl = getDelegateDataSourceFactoryUri(surl);
        URISplit split= URISplit.parse(surl);
        URISplit delegateSplit= URISplit.parse(delegateSurl);
        delegateSplit.vapScheme= split.vapScheme;
        URI uri= DataSetURI.toUri( URISplit.format(delegateSplit) );
        return DataSetURI.getDataSourceFactory( uri, new NullProgressMonitor());
    }


    public List<CompletionContext> getCompletions(CompletionContext cc,org.das2.util.monitor.ProgressMonitor mon) throws Exception {
        if ( delegateFactory==null ) {
            delegateFactory= getDelegateDataSourceFactory(cc.surl);
        }
        DataSourceFactory f = delegateFactory;
        List<CompletionContext> result = new ArrayList<CompletionContext>();
        String afile = getDelegateDataSourceFactoryUri(cc.surl);
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

    public boolean reject( String surl, ProgressMonitor mon) {
        URISplit split = URISplit.parse(surl);
        Map map = URISplit.parseParams(split.params);

        try {
            if (!map.containsKey("timerange")) {
                return true;
            }
            String timeRange = ((String) map.get("timerange"));
            timeRange= timeRange.replaceAll("\\+"," ");
            if (timeRange.length() < 4) {
                return true;
            }
            try {
                DatumRange dr= DatumRangeUtil.parseTimeRange(timeRange);
            } catch ( ParseException ex ) {
                return true;
            }

            String delegateSurl = getDelegateDataSourceFactoryUri(surl);
            if ( delegateFactory==null ) {
                delegateFactory= getDelegateDataSourceFactory(surl);
            }
            return delegateFactory.reject( delegateSurl, mon );
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return true;
        }
    }

    public void setDelegateDataSourceFactory(DataSourceFactory delegateFactory) {
        this.delegateFactory= delegateFactory;
    }

}
