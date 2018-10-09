
package org.autoplot.hapi;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.das2.qds.buffer.BufferDataSet;
import org.das2.dataset.NoDataInIntervalException;
import org.das2.datum.CacheTag;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystemUtil;
import org.das2.util.filesystem.HtmlUtil;
import org.das2.util.filesystem.HttpUtil;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.das2.qds.ArrayDataSet;
import org.das2.qds.DDataSet;
import org.das2.qds.DataSetUtil;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.das2.qds.SparseDataSetBuilder;
import org.das2.qds.WritableDataSet;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.datasource.DefaultTimeSeriesBrowse;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.capability.Caching;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.das2.datum.DatumUtil;
import org.das2.datum.TimeParser;
import org.das2.datum.TimeUtil;
import org.das2.fsm.FileStorageModel;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.DataSetBuilder;
import org.das2.qstream.TransferType;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.FileSystemSettings;
import org.das2.util.monitor.CancelledOperationException;

/**
 * HAPI data source uses transactions with HAPI servers to collect data.
 * @author jbf
 */
public final class HapiDataSource extends AbstractDataSource {

    protected final static Logger logger= LoggerManager.getLogger("apdss.hapi");
    
    /**
     * this logger is for opening connections to remote sites.
     */
    protected static final Logger loggerUrl= org.das2.util.LoggerManager.getLogger( "das2.url" );
    
    TimeSeriesBrowse tsb;
    
    public HapiDataSource(URI uri) {
        super(uri);
        tsb= new DefaultTimeSeriesBrowse();
        String str= params.get( URISplit.PARAM_TIME_RANGE );
        if ( str!=null ) {
            try {
                tsb.setURI(uri.toString());
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
        addCapability( TimeSeriesBrowse.class, tsb );
        addCapability( Caching.class, new Caching() {
            @Override
            public boolean satisfies(String surl) {
                return false;
            }

            @Override
            public void resetURI(String surl) {
            }

            @Override
            public void reset() {
                logger.fine("reset cache");
                HapiDataSource.cache.clear();
            }
            
        });
    }

    private static QDataSet getJSONBins( JSONObject binsObject ) throws JSONException {
        JSONArray bins=null;
        if ( binsObject.has("values") ) {
            logger.fine("using deprecated bins");
            bins= binsObject.getJSONArray("values");
        } else if ( binsObject.has("centers") ) {
            bins= binsObject.getJSONArray("centers");
        }
        
        JSONArray ranges= null;
        if ( binsObject.has("ranges") ) {
            ranges= binsObject.getJSONArray("ranges");
        }
        
        int len;
        if ( ranges==null && bins==null ) {
            throw new IllegalArgumentException("ranges or centers must be specified");
        } else {
            len= ranges==null ? bins.length() : ranges.length();
        }
        
        DDataSet result= DDataSet.createRank1(len);
        DDataSet max= DDataSet.createRank1(len);
        DDataSet min= DDataSet.createRank1(len);
        boolean hasMin= false;
        boolean hasMax= false;
        boolean hasCenter= false;
        if ( len==0 ) {
            throw new IllegalArgumentException("bins must have ranges or centers specified");
        } else {
            if ( bins!=null ) {
                hasCenter= true;
                Object o= bins.get(0);
                if ( o instanceof Number ) {
                    for ( int j=0; j<len; j++ ) {
                        result.putValue( j, bins.getDouble(j) );
                    }
                } else if ( o instanceof JSONObject ) {
                    for ( int j=0; j<len; j++ ) {       
                        JSONObject jo= bins.getJSONObject(j);
                        result.putValue(j,jo.getDouble("center"));
                        if ( hasMin || jo.has("min") ) {
                            hasMin= true;
                            min.putValue(j,jo.getDouble("min"));
                        }
                        if ( hasMax || jo.has("max") ) {
                            hasMax= true;
                            max.putValue(j,jo.getDouble("max"));
                        }
                    }
                }
            }
            if ( ranges!=null ) {
                for ( int j=0; j<len; j++ ) {  
                    JSONArray ja1= ranges.getJSONArray(j);
                    hasMax= true;
                    hasMin= true;
                    min.putValue(j,ja1.getDouble(0));
                    max.putValue(j,ja1.getDouble(1));
                }
            }
        }

        if ( binsObject.has("units") ) {
            Object uo= binsObject.get("units");
            if ( uo instanceof String ) {
                String sunits= (String)uo;
                Units u= Units.lookupUnits(sunits);
                result.putProperty( QDataSet.UNITS, u );
                if ( hasMin && hasMax ) {
                    min.putProperty( QDataSet.UNITS, u );
                    max.putProperty( QDataSet.UNITS, u );
                }
            }
        }
        
        if ( hasCenter ) {
            if ( hasMin && hasMax ) {
                result.putProperty( QDataSet.BIN_MIN, min );
                result.putProperty( QDataSet.BIN_MAX, max );
            } else if ( hasMin || hasMax ) {
                logger.warning("need both min and max for bins.");
            }
        } else {
            result= (DDataSet)ArrayDataSet.copy( double.class, Ops.bundle( min, max ) );
            result.putProperty( QDataSet.BINS_1, QDataSet.VALUE_BINS_MIN_MAX );
        }
        
        if ( binsObject.has("name") ) {
            result.putProperty( QDataSet.NAME, binsObject.getString("name") );
        }
        
        if ( binsObject.has("description") ) {
            result.putProperty( QDataSet.TITLE, binsObject.getString("description") );
            result.putProperty( QDataSet.LABEL, binsObject.getString("description") );
        }
        
        return result;
    }
    
    public static final double FILL_VALUE= -1e38;
    
    private JSONObject getInfo( ) throws MalformedURLException, IOException, JSONException {
        URI server = this.resourceURI;
        String id= getParam("id","" );
        if ( id.equals("") ) throw new IllegalArgumentException("missing id");
        id = URLDecoder.decode(id,"UTF-8");
        return HapiServer.getInfo(server.toURL(), id);
    }
    
    private static class ParamDescription {
        boolean hasFill= false;
        double fillValue= -1e38;
        Units units= Units.dimensionless;
        String name= "";
        String description= "";
        String label="";
        String type= "";
        /**
         * number of indeces in each index.
         */
        int[] size= new int[0]; 
        /**
         * total number of fields
         */
        int nFields= 1;
        
        /**
         * length in bytes when transferring with binary.
         */
        int length= 0; 
        QDataSet[] depend= null;
        /**
         *  for time-varying depend1 (not in HAPI1.1)
         */
        String[] dependName= null; 
        
        /**
         * date the parameter was last modified, or 0 if not known.
         */
        long modifiedDateMillis= 0;
        
        /**
         * may contain hint for renderer, such as nnspectrogram
         */
        String renderType=null; 
        private ParamDescription( String name ) {
            this.name= name;
        }
        @Override
        public String toString() {
            return this.name;
        }
    }
    
    private static final Map<String,Datum> lastRecordFound= new HashMap<>();
          
    private static String[] cacheFilesFor( URL url, ParamDescription[] pp, Datum xx ) {
        String s= AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_FSCACHE);
        if ( s.endsWith("/") ) s= s.substring(0,s.length()-1);
        StringBuilder ub= new StringBuilder( url.getProtocol() + "/" + url.getHost() + "/" + url.getPath() );
        if ( url.getQuery()!=null ) {
            String[] querys= url.getQuery().split("\\&");
            Pattern p= Pattern.compile("id=(.+)");
            for ( String q : querys ) {
                Matcher m= p.matcher(q);
                if ( m.matches() ) {
                    ub.append("/").append(m.group(1));
                    break;
                }
            }
        } else {
            throw new IllegalArgumentException("query must be specified, implementation error");
        }
        
        TimeParser tp= TimeParser.create( "$Y/$m/$Y$m$d" );
        String sxx= tp.format(xx);
        
        String u= ub.toString();
        String[] result= new String[pp.length];
        for ( int i=0; i<pp.length; i++ ){
            result[i]= s + "/" + u + "/" + sxx + "." + pp[0].name + ".csv";
        }
        
        return result;
    }
    
    private static final Map<String,ArrayList<String>> cache= new ConcurrentHashMap<>();
    
    /**
     * print the cache stats.
     * @see https://sourceforge.net/p/autoplot/bugs/1996/
     */
    public static void printCacheStats() {
        if ( cache==null || cache.isEmpty() ) {
            System.err.println( "(cache is empty)" );
        } else {
            for ( Entry<String,ArrayList<String>> s: cache.entrySet() ) {
                System.err.println( "" + s.getKey() +": "+s.getValue().size()+" records");
            }
        }
    }
    
    /**
     * return the location of the cache for HAPI data.
     * @return 
     */
    public static String getHapiCache() {
        String hapiCache= System.getProperty("HAPI_DATA");
        if ( hapiCache!=null ) {
            String home=System.getProperty("user.home") ;
            if ( hapiCache.contains("${HOME}") ) { // the filesystem settings used ${}, %{} seems more conventional.
                hapiCache= hapiCache.replace("${HOME}", home );
            } else if ( hapiCache.contains("%{HOME}") ) {
                hapiCache= hapiCache.replace("%{HOME}", home );
            }            
        }
        if ( hapiCache!=null && hapiCache.contains("\\") ) { // Windows path sep
            hapiCache= hapiCache.replaceAll("\\\\", "/" );
        }
        if ( hapiCache==null ) {
            String s= AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_FSCACHE);
            if ( s.endsWith("/") ) s= s.substring(0,s.length()-1);
            hapiCache= s + "/hapi/";
        }
        if ( !hapiCache.endsWith("/") ) hapiCache= hapiCache + "/";
        
        if ( !hapiCache.endsWith("/") ) {
            throw new IllegalArgumentException("hapiCache must end with /");
        }
        if ( HapiServer.useCache() ) {
            if ( !new File(hapiCache).exists() ) {
                new File(hapiCache).mkdirs();
            }
        }
        return hapiCache;

    }
     
    private static void writeToCachedData(URL url, ParamDescription[] pp, Datum xx, String[] ss) throws IOException {
        
        StringBuilder ub= new StringBuilder( url.getProtocol() + "/" + url.getHost() + url.getPath() );
        if ( url.getQuery()!=null ) {
            String[] querys= url.getQuery().split("\\&");
            Pattern p= Pattern.compile("id=(.+)");
            for ( String q : querys ) {
                Matcher m= p.matcher(q);
                if ( m.matches() ) {
                    ub.append("/").append(m.group(1));
                    break;
                }
            }
        } else {
            throw new IllegalArgumentException("query must be specified, implementation error");
        }
                
        String hapiCache= getHapiCache();
        
        TimeParser tp= TimeParser.create( "$Y/$m/$Y$m$d" );
        
        String sxx= tp.format(xx);
                
        String u= ub.toString();
        Datum t0= lastRecordFound.get( u + "/" + sxx );
        if ( t0==null ) {
            String f= hapiCache + u + "/" + sxx + "." + pp[0].name + ".csv";
            File ff= new File(f);
            if ( ff.exists() ) {
                BufferedReader read= new BufferedReader(new FileReader(ff));
                String line= read.readLine();
                String lastLine= null;
                while ( line!=null ) {
                    lastLine= line;
                    line= read.readLine();
                }
                if ( lastLine!=null ) {
                    try {
                        t0= Units.us2000.parse(lastLine);
                        lastRecordFound.put( u + "/" + sxx,t0);
                    } catch (ParseException ex) {
                        t0= null;
                    }
                } else {
                    t0= null;
                }
            }
        }
        if ( t0!=null && t0.ge(xx) ) {
            logger.log(Level.FINE, "clear all cached files for {0}", sxx);
            for (ParamDescription pp1 : pp) {
                String f = hapiCache + u + "/" + sxx + "." + pp1.name + ".csv";
                File ff= new File(f);
                if ( ff.exists() ) {
                    if ( !ff.delete() ) logger.log(Level.INFO, "unable to delete file: {0}", ff);
                }
            }
        }
        
        int ifield=0;
        for (ParamDescription pp1 : pp) {
            String f = u + "/" + sxx + "." + pp1.name + ".csv" + "." + Thread.currentThread().getId();
            logger.log(Level.FINER, "cache.get({0})", f);
            ArrayList<String> sparam= cache.get(f);
            if ( sparam==null ) {
                sparam= new ArrayList<>();
                cache.put(f,sparam);
                logger.log(Level.FINE, "cache.put({0},ArrayList({1}))", new Object[]{f, sparam.size()});
            }
            
            StringBuilder build= new StringBuilder();
            
            int length = pp1.nFields;
            for ( int k=0; k<length; k++ ) {
                if ( k>0 ) build.append(",");
                build.append( ss[ifield++] );
            }
            
            sparam.add(build.toString());
            
        }
        
        lastRecordFound.put( u + "/" + sxx,xx);
        
    }
    
    /** 
     * TODO: this needs to use HAPI_DATA to locate the directory.
     * See https://sourceforge.net/p/autoplot/bugs/2043/
     * 
     */
    private static void writeToCachedDataFinish(URL url, ParamDescription[] pp, Datum xx ) throws IOException {
        logger.log(Level.FINE, "writeToCachedDataFinish: {0}", xx);

        StringBuilder ub= new StringBuilder( url.getProtocol() + "/" + url.getHost() + url.getPath() );
        if ( url.getQuery()!=null ) { // get the id from the url
            String[] querys= url.getQuery().split("\\&");
            Pattern p= Pattern.compile("id=(.+)");
            for ( String q : querys ) {
                Matcher m= p.matcher(q);
                if ( m.matches() ) {
                    ub.append("/").append(m.group(1));
                    break;
                }
            }
        } else {
            throw new IllegalArgumentException("query must be specified, implementation error");
        }
        
        String hapiCache= getHapiCache();
        
        long currentTimeMillis= pp[0].modifiedDateMillis;
        TimeParser tp= TimeParser.create( "$Y/$m/$Y$m$d" );
        String sxx= tp.format(xx);
        String u= ub.toString();
        for (ParamDescription pp1 : pp) {
            String f = u + "/" + sxx + "." + pp1.name + ".csv" + "."+ Thread.currentThread().getId();
            logger.log(Level.FINE, "remove from cache: {0}", f);
            ArrayList<String> sparam= cache.remove(f);
            File ff= new File( hapiCache + u + "/" + sxx + "." + pp1.name + ".csv" +".gz");
            if ( !ff.getParentFile().exists() ) {
                if ( !ff.getParentFile().mkdirs() ) {
                    throw new IOException("unable to mkdirs "+ff.getParent() );
                }
            }
            File ffTemp= new File( hapiCache + u + "/" + sxx + "." + pp1.name + ".csv"+".gz."+Thread.currentThread().getId() );
            //int line=0;
            try (final BufferedWriter w = new BufferedWriter( new OutputStreamWriter( new GZIPOutputStream( new FileOutputStream(ff) ) ) ) ) {
                if ( sparam!=null ) {
                    for ( String s123: sparam ) {
                        //line++;
                        w.write(s123);
                        w.newLine();
                    }
                }
            }
            
            synchronized ( HapiDataSource.class ) {
                ffTemp.renameTo(ff);
                if ( currentTimeMillis>0 ) ff.setLastModified(currentTimeMillis);
            }
        }
        
    }
    
    /**
     * To assist in getting the CDAWeb HAPI server going, handle a few differences
     * like:<ul>
     * <li>parameters are returned within the JSON code.
     * <li>Epoch is needed.
     * </ul>
     * @param monitor
     * @return
     * @throws Exception 
     */
    private QDataSet getDataSetCDAWeb( ProgressMonitor monitor) throws Exception {
        URI server = this.resourceURI;
        String id= getParam("id","" );
        if ( id.equals("") ) throw new IllegalArgumentException("missing id");
        id= URLDecoder.decode( id,"UTF-8" );

        String pp= getParam("parameters","");
        if ( !pp.equals("") && !pp.startsWith("Epoch,") ) {
            pp= "Epoch,"+pp;
        }
        
        if ( id.equals("") ) throw new IllegalArgumentException("missing id");
        id= URLDecoder.decode(id,"UTF-8");
        
        DatumRange tr; // TSB = DatumRangeUtil.parseTimeRange(timeRange);
        tr= tsb.getTimeRange();
        
        URL url= HapiServer.getDataURL( server.toURL(), id, tr, pp );
        url= new URL( url.toString()+"&include=header&format=json1" );
        
        monitor.started();
        monitor.setProgressMessage("server is preparing data");
         
        long t0= System.currentTimeMillis() - 100; // -100 so it updates after receiving first record.
       
        int lineNum=0;
        
        StringBuilder builder= new StringBuilder();
        logger.log(Level.FINE, "getDocument {0}", url.toString());
        loggerUrl.log(Level.FINE, "GET {0}", new Object[] { url } );
        HttpURLConnection httpConnect=  ((HttpURLConnection)url.openConnection());
        httpConnect.setConnectTimeout(FileSystem.settings().getConnectTimeoutMs());
        httpConnect.setReadTimeout(FileSystem.settings().getReadTimeoutMs());
        httpConnect= (HttpURLConnection) HttpUtil.checkRedirect( httpConnect );
        try ( BufferedReader in= new BufferedReader( new InputStreamReader( httpConnect.getInputStream() ) ) ) {
            String line= in.readLine();
            lineNum++;
            while ( line!=null ) {
                if ( System.currentTimeMillis()-t0 > 100 ) {
                    monitor.setProgressMessage("reading line "+lineNum);
                    t0= System.currentTimeMillis();
                }
                builder.append(line);
                line= in.readLine();
            }
        } catch ( IOException ex ) {
            ByteArrayOutputStream baos= new ByteArrayOutputStream();
            FileSystemUtil.copyStream( httpConnect.getErrorStream(), baos, new NullProgressMonitor() );
            String s= baos.toString("UTF-8");
            if ( s.contains("No data available") ) {
                logger.log(Level.FINE, "No data available, server responded with {0}: {1}", new Object[]{httpConnect.getResponseCode(), httpConnect.getResponseMessage()});
                throw new NoDataInIntervalException("No data available");
            } else {
                if ( s.length()<256 ) {
                    throw new IOException( ex.getMessage() + ": "+s );
                } else {
                    throw ex;
                }
            }
        }
        
        httpConnect.disconnect();
        
        JSONObject o= new JSONObject(builder.toString());
        
        JSONObject doc= o;
        
        ParamDescription[] pds= getParameterDescriptions(doc);
        
        monitor.setProgressMessage("parsing data");
        
        int[] nfields= new int[pds.length];
        for ( int i=0; i<pds.length; i++ ) {
            if ( pds[i].size.length==0 || ( pds[i].size.length==1 && pds[i].size[0]==1 ) ) {
                nfields[i]= 1;
            } else {
                nfields[i]= DataSetUtil.product(pds[i].size);
            }
        }
        
        boolean[] timeVary= new boolean[pds.length];
        
        //https://cdaweb.gsfc.nasa.gov/registry/hdp/hapi/data.xql?id=spase%3A%2F%2FVSPO%2FNumericalData%2FRBSP%2FB%2FEMFISIS%2FGEI%2FPT0.015625S&time.min=2012-10-09T00%3A00%3A00Z&time.max=2012-10-09T00%3A10%3A00Z&parameters=Magnitude
        QDataSet result= null;
        int ipd=0;
        for ( ParamDescription pd: pds ) {       
            JSONArray param;
            try {
                param= doc.getJSONArray(pd.name);
            } catch ( JSONException ex ) {
                timeVary[ipd]= false;
                continue; // DEPEND_1, etc...
            }
            timeVary[ipd]= true;
            Units u= pd.units;
            if ( nfields[ipd]>1 ) {
                int nf= nfields[ipd];
                DDataSet column= DDataSet.createRank2(param.length(),nfields[ipd]);
                for ( int i=0; i<param.length(); i++ ) {
                    JSONObject jo= param.getJSONObject(i);
                    JSONArray joa= jo.getJSONArray("elements");
                    for ( int j=0; j<nf; j++ ) {
                        column.putValue( i, j, u.parse( joa.getString(j) ).doubleValue(u) );
                    }
                }
                if ( pd.hasFill ) column.putProperty( QDataSet.FILL_VALUE, pd.fillValue );
                column.putProperty( QDataSet.TITLE, pd.description );
                column.putProperty( QDataSet.UNITS, pd.units );
                for ( int j=0; j<nf; j++ ) {
                    result= Ops.bundle( result, Ops.slice1(column,j) );
                }
            } else {
                DDataSet column= DDataSet.createRank1(param.length());
                for ( int i=0; i<param.length(); i++ ) {
                    column.putValue( i, u.parse(param.getString(i) ).doubleValue(u) );
                }
                if ( pd.hasFill ) column.putProperty( QDataSet.FILL_VALUE, pd.fillValue );
                column.putProperty( QDataSet.TITLE, pd.description );
                column.putProperty( QDataSet.UNITS, pd.units );
                result= Ops.bundle( result, column );
            }
            ipd++;
        }
        
        monitor.finished();
        
        int ntimeVary= 0;
        for ( boolean b: timeVary ){
            if ( b ) ntimeVary++;
        }
        ParamDescription[] newPds= new ParamDescription[ntimeVary];
        int k=0;
        for ( int j=0; j<pds.length; j++ ) {
            if ( timeVary[j] ) newPds[k++]= pds[j];
        }
        
        // the data are sometimes delivered backwards 
        int[] sort= null;
//        if ( UnitsUtil.isTimeLocation( newPds[ntimeVary-1].units ) ) {
//            sort= new int[result.length()];
//            for ( int j=0; j<ntimeVary; j++ ) {
//                sort[j]= ntimeVary-1-j;
//            }
//        }
        
        result = repackage(result,newPds,sort);
        
        return result;
    }
    
    @Override
    public synchronized QDataSet getDataSet(ProgressMonitor monitor) throws Exception {
        URI server = this.resourceURI;
        
        String format= getParam("format","csv");
        
        {
            String serverStr= server.toString();
            if ( format.equals("json1") ||
                    ( serverStr.startsWith("http://cdaweb") && serverStr.endsWith( "gsfc.nasa.gov/registry/hdp/hapi") ) )  {
                return getDataSetCDAWeb(monitor);
            }
        }
        
        monitor.setTaskSize(100);
        monitor.started();
        
        monitor.setProgressMessage("reading info");
        
        String id= getParam("id","" );  // the name of the set of identifiers.
        if ( id.equals("") ) throw new IllegalArgumentException("missing id");
        id= URLDecoder.decode( id,"UTF-8" );

        String pp= getParam("parameters","");
        
        JSONObject info= getInfo();
        monitor.setProgressMessage("got info");
        monitor.setTaskProgress(20);
        
        ParamDescription[] pds= getParameterDescriptions(info);
        
        DatumRange tr; // TSB = DatumRangeUtil.parseTimeRange(timeRange);
        tr= tsb.getTimeRange();
        
        Datum cadence= null;
        
        if ( info.has("cadence") ) { // add one cadence length to beginning and end.
            try {
                int[] ii= DatumRangeUtil.parseISO8601Duration(info.getString("cadence"));
                Datum t= TimeUtil.toDatumDuration(ii);
                cadence= t;
                tr= new DatumRange( tr.min().subtract(cadence), tr.max().add(cadence) );
            } catch ( ParseException ex ) {
                logger.log(Level.WARNING, "unable to parse cadence as ISO8601 duration: {0}", info.getString("cadence"));
            }
        }
        
        String timeStampLocation= "CENTER";
        if ( info.has("timeStampLocation" ) ) {
            timeStampLocation= info.getString("timeStampLocation");
        }
        
        JSONArray parametersArray= info.getJSONArray("parameters");
        int nparam= parametersArray.length(); // this is the actual number sent.
        if ( pp.length()>0 ) {
            String[] pps= pp.split(",");
            Map<String,Integer> map= new HashMap();
            for ( int i=0; i<nparam; i++ ) {
                map.put( parametersArray.getJSONObject(i).getString("name"), i ); // really--should name/id are two names for the same thing...
            }
            if ( !pps[0].equals(parametersArray.getJSONObject(0).getString("name")) ) { // add Time if it wasn't specified.
                pp= parametersArray.getJSONObject(0).getString("name") + ","+ pp;
                pps= pp.split(",");
            }
            nparam= pps.length;
            ParamDescription[] subsetPds= new ParamDescription[pps.length];
            for ( int ip=0; ip<pps.length; ip++ ) {
                int i= map.get(pps[ip]);
                subsetPds[ip]= pds[i];
            }
            //TODO: the parameters must also be sorted by position in stream.
            pds= subsetPds;   
        }
        
        // 2043: trim the request to startDate/stopDate.  TODO: caching needs to consider this as well.
        DatumRange startStopDate= null;
        try {
            startStopDate= DatumRangeUtil.parseTimeRange( info.getString("startDate") + "/"+info.getString("stopDate") );
            if ( tr.intersects( startStopDate ) ) {
                tr= tr.intersection( startStopDate );
            } else {
                if ( tr.max().lt(startStopDate.min() ) ) {
                    throw new NoDataInIntervalException("data begins after this time range");
                } else {
                    throw new NoDataInIntervalException("data ends before this time range");
                }
            }
            //TODO: caching when enabled may round out to day boundaries.
        } catch ( ParseException ex ) {
            logger.log(Level.INFO, "unable to parse startDate/stopDate: {0}", ex.getMessage());
        } catch ( NullPointerException ex ) {
            logger.info("startDate and stopDate was missing");
        }
        
        URL url= HapiServer.getDataURL( server.toURL(), id, tr, pp );
        
        if ( !format.equals("csv") ) {
            url= new URL( url+"&format="+format );
        }
        
        logger.log(Level.FINE, "getDataSet {0}", url.toString());
        
        int[] nfields= new int[nparam];
        for ( int i=0; i<nparam; i++ ) {
            if ( pds[i].size.length==0 || ( pds[i].size.length==1 && pds[i].size[0]==1 ) ) {
                nfields[i]= 1;
            } else {
                nfields[i]= DataSetUtil.product(pds[i].size);
            }
        }
        int totalFields= DataSetUtil.sum(nfields);

        QDataSet ds;
        switch (format) {
            case "binary":
                ds= getDataSetViaBinary(totalFields, monitor, url, pds, tr, nparam, nfields);
                break;
            case "json":
                ds= getDataSetViaJSON(totalFields, monitor, url, pds, tr, nparam, nfields);
                break;
            default:
                boolean useCache= useCache();
                if ( useCache ) { // round out to day boundaries, and load each day separately.
                    logger.finer("useCache, so make daily requests to form granules");
                    Datum minMidnight= TimeUtil.prevMidnight( tr.min() );
                    Datum maxMidnight= TimeUtil.nextMidnight( tr.max() );
                    tr= new DatumRange( minMidnight, maxMidnight );
                    Datum midnight= TimeUtil.prevMidnight( tr.min() );
                    DatumRange currentDay= new DatumRange( midnight, TimeUtil.next( TimeUtil.DAY, midnight) );
                    QDataSet dsall= null;
                    int nday= (int)Math.ceil( tr.width().doubleValue(Units.days) );
                    if ( nday>1 ) {
                        monitor.setTaskSize(nday*10);
                        monitor.started();
                    }
                    int iday=0;
                    while ( currentDay.min().le(tr.max()) ) {
                        logger.log(Level.FINER, "useCache, request {0}", currentDay);
                        ProgressMonitor mon1= nday==1 ? monitor : monitor.getSubtaskMonitor( 10*iday, 10*(iday+1), "read "+currentDay );
                        QDataSet ds1;
                        try {
                            ds1 = getDataSetViaCsv(totalFields, mon1, url, pds, currentDay, nparam, nfields);
                            if ( ds1.length()>0 ) {
                                dsall= Ops.append( dsall, ds1 );
                            }
                        } catch ( NoDataInIntervalException ex ) {
                            if ( ! FileSystem.settings().isOffline() ) {
                                throw ex;
                            } else {
                                logger.log(Level.FINE, "no granule found for day, but we are offline: {0}", currentDay);
                            }
                        }
                        currentDay= currentDay.next();
                        iday++;
                    }
                    if ( dsall==null ) {
                        logger.info("no records found");
                        return null;
                    }
                    logger.finer("done useCache, so make daily requests to form granules");
                    ds= dsall;
                    ds= Ops.putProperty( ds, QDataSet.UNITS, null ); // kludge, otherwise time units are messed up. TODO: who puts unit here?
                } else {
                    ds= getDataSetViaCsv(totalFields, monitor, url, pds, tr, nparam, nfields);
                }
                break;
        }
        
        if ( ds.length()==0 ) {
            monitor.finished();
            throw new NoDataInIntervalException("no records found");
        }
        
        ds = repackage(ds,pds,null);
        
        // install a cacheTag.  The following code assumes depend_0 is mutable.
        QDataSet xds= (QDataSet) ds.property(QDataSet.DEPEND_0);
        if ( xds==null && ( UnitsUtil.isTimeLocation( SemanticOps.getUnits(ds) ) ) ) {
            xds= ds;
        }
        
        if ( timeStampLocation.equals("BEGIN") || timeStampLocation.equals("END" ) ) {
            if ( cadence==null ) {
                cadence= DataSetUtil.asDatum( DataSetUtil.guessCadenceNew( xds, null ) );
            }
            if ( cadence!=null ) {
                if ( timeStampLocation.equals("BEGIN") ) {
                    xds= Ops.add( xds, cadence.divide(2) );
                } else if ( timeStampLocation.equals("END") ) {
                    xds= Ops.subtract( xds, cadence.divide(2) );
                } 
            } else {
                logger.info("timetags are identified as BEGIN, but cadence was not available to center the data");
            }
        }
        
        if ( xds!=null ) {
            ((MutablePropertyDataSet)xds).putProperty(QDataSet.CACHE_TAG, new CacheTag(tr,null) );
        }
        
        monitor.setTaskProgress(100);
        monitor.finished();
        
        return ds;
        
    }
    
    private boolean useCache() {
        boolean useCache= HapiServer.useCache();
        String cacheParam= getParam( "cache", "" );
        if ( cacheParam.equals("F") ) {
            useCache= false;
        }
        return useCache;
    }
    
    private QDataSet getDataSetViaCsv(int totalFields, ProgressMonitor monitor, URL url, ParamDescription[] pds, DatumRange tr, int nparam, int[] nfields) throws IllegalArgumentException, Exception, IOException {
        DataSetBuilder builder= new DataSetBuilder(2,100,totalFields);
        monitor.setProgressMessage("reading data");
        monitor.setTaskProgress(20);
        long t0= System.currentTimeMillis() - 100; // -100 so it updates after receiving first record.
        
        boolean useCache= useCache();
        
        if ( useCache ) { // round out data request to day boundaries.
            Datum minMidnight= TimeUtil.prevMidnight( tr.min() );
            Datum maxMidnight= TimeUtil.nextMidnight( tr.max() );
            tr= new DatumRange( minMidnight, maxMidnight );
            URISplit split= URISplit.parse(url.toURI());
            Map<String,String> params= URISplit.parseParams(split.params);
            params.put("time.min",minMidnight.toString());
            params.put("time.max",maxMidnight.toString());
            split.params= URISplit.formatParams(params);
            String surl= URISplit.format(split);
            url= new URL(surl);
        }
        
        AbstractLineReader cacheReader;
        if ( useCache ) { // this branch posts the request, expecting that the server may respond with 304, indicating the cache should be used.
            String[] parameters= new String[pds.length];
            for ( int i=0; i<pds.length; i++ ) parameters[i]= pds[i].name;
            cacheReader= getCacheReader(url, parameters, tr, FileSystem.settings().isOffline(), 0L );
            if ( cacheReader!=null ) {
                logger.fine("reading from cache");
            }
        } else {
            cacheReader= null;
        }
        
        HttpURLConnection httpConnect;
        if ( cacheReader==null ) {
            if ( FileSystem.settings().isOffline() ) {
                throw new NoDataInIntervalException("HAPI server is offline.");
                //throw new FileSystem.FileSystemOfflineException("file system is offline");
            } else {
                loggerUrl.log(Level.FINE, "GET {0}", new Object[] { url } );            
                httpConnect= (HttpURLConnection)url.openConnection();
                httpConnect.setConnectTimeout(FileSystem.settings().getConnectTimeoutMs());
                httpConnect.setReadTimeout(FileSystem.settings().getReadTimeoutMs());
                httpConnect.setRequestProperty( "Accept-Encoding", "gzip" );
                httpConnect= (HttpURLConnection)HttpUtil.checkRedirect(httpConnect);
                httpConnect.connect();
            }
        } else {
            httpConnect= null;
        }
                
        //Check to see what time ranges are from entire days, then only call writeToCachedData for these intervals. 
        Datum midnight= TimeUtil.prevMidnight( tr.min() );
        DatumRange currentDay= new DatumRange( midnight, TimeUtil.next( TimeUtil.DAY, midnight) );
        boolean completeDay= tr.contains(currentDay);

        logger.log(Level.FINER, "parse {0}", cacheReader);
        boolean gzip= cacheReader==null ? "gzip".equals( httpConnect.getContentEncoding() ) : false;
        int linenumber=0;
        try ( AbstractLineReader in= ( cacheReader!=null ? cacheReader :
                new SingleFileBufferedReader( new BufferedReader( new InputStreamReader( gzip ? new GZIPInputStream( httpConnect.getInputStream() ) : httpConnect.getInputStream() ) ) ) ) ) {
            String line= in.readLine();
            while ( line!=null ) {
                linenumber++;
                String[] ss= lineSplit(line);
                if ( ss.length!=totalFields ) {
                    if ( line.trim().length()==0 ) {
                        logger.log(Level.WARNING, "expected {0} fields, got empty line at line {1}", new Object[]{totalFields,linenumber});
                        line= in.readLine();
                        continue;
                    } else {
                        logger.log(Level.WARNING, "expected {0} fields, got {1} at line {2}", new Object[]{totalFields, ss.length,linenumber});
                        throw new IllegalArgumentException( String.format( "expected %d fields, got %d at line %d", new Object[]{totalFields, ss.length,linenumber} ) );
                    }
                }
                int ifield=0;
                Datum xx;
                try {
                    xx= pds[ifield].units.parse(ss[ifield]);
                    if ( System.currentTimeMillis()-t0 > 100 ) {
                        monitor.setProgressMessage("reading "+xx);
                        t0= System.currentTimeMillis();
                        double d= DatumRangeUtil.normalize( tr, xx );
                        monitor.setTaskProgress( 20 + (int)( 75 * d ) );
                        if ( monitor.isCancelled() ) 
                            throw new CancelledOperationException("cancel was pressed");
                    }
                } catch ( ParseException ex ) {
                    line= in.readLine();
                    continue;
                }
                
                // "close" the current file, gzipping it.
                if ( cacheReader==null && useCache && !currentDay.contains(xx) && tr.intersects(currentDay) && completeDay ) {
                    // https://sourceforge.net/p/autoplot/bugs/1968/ HAPI caching must not cache after "modificationDate" or partial days remain in cache
                    if ( pds[0].modifiedDateMillis==0 || currentDay.middle().doubleValue(Units.ms1970) - pds[0].modifiedDateMillis <= 0 ) {
                        writeToCachedDataFinish( url, pds, currentDay.middle() );
                    } else {
                        logger.fine("data after modification date is not cached.");
                    }
                }
                
                while ( !currentDay.contains(xx) && tr.intersects(currentDay ) ) {
                    currentDay= currentDay.next();
                    completeDay= tr.contains(currentDay);
                    if ( cacheReader==null && useCache && !currentDay.contains(xx) && tr.intersects(currentDay ) ) {
                        if ( pds[0].modifiedDateMillis==0 || currentDay.middle().doubleValue(Units.ms1970) - pds[0].modifiedDateMillis <= 0 ) {
                            // put empty file which is placeholder.
                            writeToCachedDataFinish( url, pds, currentDay.middle() ); 
                        }
                    }
                }
                
                if ( !currentDay.contains(xx) ) {
                    logger.fine("something's gone wrong, perhaps out-of-order timetags.");
                    completeDay= false;
                }
                
                if ( completeDay ) {
                    if ( cacheReader==null && useCache ) {
                        if ( pds[0].modifiedDateMillis==0 || xx.doubleValue(Units.ms1970) - pds[0].modifiedDateMillis <= 0 ) {
                            writeToCachedData( url, pds, xx, ss );
                        }
                    }
                }
                        
                builder.putValue( -1, ifield, xx );
                ifield++;
                for ( int i=1; i<nparam; i++ ) {  // nparam is number of parameters, which may have multiple fields.
                    for ( int j=0; j<nfields[i]; j++ ) {
                        try {
                            String s= ss[ifield];
                            if ( pds[i].units instanceof EnumerationUnits ) {
                                builder.putValue( -1, ifield, ((EnumerationUnits)pds[i].units).createDatum(s).doubleValue(pds[i].units) );
                            } else {
                                builder.putValue( -1, ifield, pds[i].units.parse(s) );
                            }
                        } catch ( ParseException ex ) {
                            builder.putValue( -1, ifield, pds[i].fillValue );
                            pds[i].hasFill= true;
                        }
                        ifield++;
                    } 
                }
                builder.nextRecord();
                line= in.readLine();
            }
            while ( completeDay && tr.intersects(currentDay) ) {
                if ( cacheReader==null && useCache ) {
                    if ( pds[0].modifiedDateMillis==0 || currentDay.middle().doubleValue(Units.ms1970) - pds[0].modifiedDateMillis <= 0 ) {
                        // put empty file which is placeholder.
                        writeToCachedDataFinish( url, pds, currentDay.middle() ); 
                    }
                }
                currentDay= currentDay.next();
                completeDay= tr.contains(currentDay);
            }
        } catch ( IOException e ) {
            logger.log( Level.WARNING, e.getMessage(), e );
            monitor.finished();
            if ( httpConnect!=null ) {
                logger.log(Level.WARNING, "IOException when trying to read {0}", httpConnect.getURL());
                throw new IOException( httpConnect.getURL() + " results in\n"+String.valueOf(httpConnect.getResponseCode())+": "+httpConnect.getResponseMessage() );
            } else {
                throw e;
            }
            
        } catch ( Exception e ) {
            logger.log( Level.WARNING, e.getMessage(), e );
            monitor.finished();
            throw e;
        } finally {
            if ( httpConnect!=null ) httpConnect.disconnect();
        }
        
        logger.log(Level.FINER, "done parsing {0}", cacheReader);
        
        if ( cacheReader!=null ) {
            Map<String,String> cacheFiles= new HashMap<>();
            cacheFiles.put( "cached", "true" );
            builder.putProperty( QDataSet.USER_PROPERTIES, cacheFiles );
        }
        
        monitor.setTaskProgress(95);
        QDataSet ds= builder.getDataSet();
        return ds;
    }

    private QDataSet getDataSetViaBinary(int totalFields, ProgressMonitor monitor, URL url, ParamDescription[] pds, DatumRange tr, int nparam,
            int[] nfields) throws IllegalArgumentException, Exception, IOException {
        DataSetBuilder builder = new DataSetBuilder(2, 100, totalFields);
        monitor.setProgressMessage("reading data");
        monitor.setTaskProgress(20);
        long t0 = System.currentTimeMillis() - 100; // -100 so it updates after receiving first record.
        loggerUrl.log(Level.FINE, "GET {0}", new Object[] { url } );
        HttpURLConnection httpConnect = (HttpURLConnection) url.openConnection();
        httpConnect.setConnectTimeout(FileSystem.settings().getConnectTimeoutMs());
        httpConnect.setReadTimeout(FileSystem.settings().getReadTimeoutMs());
        httpConnect.setRequestProperty("Accept-Encoding", "gzip");
        httpConnect.connect();
        boolean gzip = "gzip".equals(httpConnect.getContentEncoding());

        int recordLengthBytes = 0;
        TransferType[] tts = new TransferType[pds.length];

        for (int i = 0; i < pds.length; i++) {
            if (pds[i].type.startsWith("time")) {
                recordLengthBytes += Integer.parseInt(pds[i].type.substring(4));
                tts[i] = TransferType.getForName(pds[i].type, Collections.singletonMap(QDataSet.UNITS, (Object)pds[i].units));
            } else if (pds[i].type.startsWith("string")) {
                recordLengthBytes += pds[i].length;
                final Units u= pds[i].units;
                final int length= pds[i].length;
                final byte[] bytes= new byte[length];
                tts[i] = new TransferType() {
                    @Override
                    public void write(double d, ByteBuffer buffer) {
                        
                    }

                    @Override
                    public double read(ByteBuffer buffer) {
                        buffer.get(bytes);
                        //buf2.get(bytes);
                        String s= new String( bytes );
                        Datum d= ((EnumerationUnits)u).createDatum(s);
                        return d.doubleValue(u);
                    }

                    @Override
                    public int sizeBytes() {
                        return length;
                    }

                    @Override
                    public boolean isAscii() {
                        return false;
                    }

                    @Override
                    public String name() {
                        return "string"+length;
                    }
                    
                };
                
            } else {
                Object type= pds[i].type;
                recordLengthBytes += BufferDataSet.byteCount(type) * DataSetUtil.product(pds[i].size);
                tts[i] = TransferType.getForName( type.toString(), Collections.singletonMap(QDataSet.UNITS, (Object)pds[i].units) );
            }
            if ( tts[i]==null ) {
                throw new IllegalArgumentException("unable to identify transfer type for \""+pds[i].type+"\"");
            }
        }

        totalFields= DataSetUtil.sum(nfields);
        double[] result = new double[totalFields];

        try (InputStream in = gzip ? new GZIPInputStream(httpConnect.getInputStream()) : httpConnect.getInputStream()) {
            ByteBuffer buf = TransferType.allocate( recordLengthBytes,ByteOrder.LITTLE_ENDIAN );
            byte[] bytes = buf.array();
            int bytesRead = in.read(bytes);
            while (bytesRead != -1) {
                while ( bytesRead<recordLengthBytes ) {
                    int b= in.read( bytes, bytesRead, recordLengthBytes-bytesRead );
                    if ( b==-1 ) throw new InterruptedIOException("expected "+recordLengthBytes+" bytes to complete a record" );
                    bytesRead+= b;
                }
                int ifield = 0;
                for (int i = 0; i < pds.length; i++) {
                    for (int j = 0; j < nfields[i]; j++) {
                        result[ifield] = tts[i].read(buf);
                        ifield++;
                    }
                }
                if (ifield != totalFields) {
                    logger.log(Level.WARNING, "expected {0} got {1}", new Object[]{totalFields, ifield});
                }

                // copy the data from double array into the builder.
                ifield= 0;
                Datum xx;
                xx = pds[0].units.createDatum(result[0]);
                if (System.currentTimeMillis() - t0 > 100) {
                    monitor.setProgressMessage("reading " + xx);
                    t0 = System.currentTimeMillis();
                    double d = DatumRangeUtil.normalize(tr, xx);
                    monitor.setTaskProgress(20 + (int) (75 * d));
                }
                builder.putValue(-1, ifield, xx);
                ifield++;
                for (int i = 1; i < nparam; i++) {  // nparam is number of parameters, which may have multiple fields.
                    for (int j = 0; j < nfields[i]; j++) {
                        builder.putValue(-1, ifield, result[ifield]);
                        //TODO: fill?
                        ifield++;
                    }
                }
                builder.nextRecord();

                buf.flip();
                bytesRead = in.read(bytes);
            }

        } catch (IOException e) {
            logger.log( Level.WARNING, e.getMessage(), e );
            monitor.finished();
            throw new IOException(String.valueOf(httpConnect.getResponseCode()) + ":" + httpConnect.getResponseMessage());

        } catch (Exception e) {
            logger.log( Level.WARNING, e.getMessage(), e );
            monitor.finished();
            throw e;
        } finally {
            httpConnect.disconnect();
        }
        monitor.setTaskProgress(95);
        QDataSet ds = builder.getDataSet();
        return ds;
    }

    /**
     * read data embedded within a JSON response.  This current reads in the entire JSON document,
     * but the final version should use a streaming JSON library.
     * @param monitor
     * @return the dataset.
     * @throws Exception 
     */
    private QDataSet getDataSetViaJSON( int totalFields, ProgressMonitor monitor, URL url, ParamDescription[] pds, DatumRange tr, int nparam, int[] nfields) throws IllegalArgumentException, Exception, IOException {
        
        monitor.started();
        monitor.setProgressMessage("server is preparing data");
        
        long t0= System.currentTimeMillis() - 100; // -100 so it updates after receiving first record.
       
        int lineNum=0;
        
        StringBuilder builder= new StringBuilder();
        logger.log(Level.FINE, "getDocument {0}", url.toString());
        loggerUrl.log(Level.FINE, "GET {0}", new Object[] { url } );
        HttpURLConnection httpConnect=  ((HttpURLConnection)url.openConnection());
        httpConnect.setConnectTimeout(FileSystem.settings().getConnectTimeoutMs());
        httpConnect.setReadTimeout(FileSystem.settings().getReadTimeoutMs());
        httpConnect= (HttpURLConnection) HttpUtil.checkRedirect(httpConnect);
        try ( BufferedReader in= new BufferedReader( new InputStreamReader( httpConnect.getInputStream() ) ) ) {
            String line= in.readLine();
            lineNum++;
            while ( line!=null ) {
                if ( System.currentTimeMillis()-t0 > 100 ) {
                    monitor.setProgressMessage("reading line "+lineNum);
                    t0= System.currentTimeMillis();
                }
                //if ( line.startsWith("{ \"data\" :") ) { // TODO: kludge for Jon's server
                //    in.readLine();
                //    line= in.readLine();
                //}
                builder.append(line);
                line= in.readLine();
            }
        } catch ( IOException ex ) {
            ByteArrayOutputStream baos= new ByteArrayOutputStream();
            FileSystemUtil.copyStream( httpConnect.getErrorStream(), baos, new NullProgressMonitor() );
            String s= baos.toString("UTF-8");
            if ( s.contains("No data available") ) {
                logger.log(Level.FINE, "No data available, server responded with {0}: {1}", new Object[]{httpConnect.getResponseCode(), httpConnect.getResponseMessage()});
                throw new NoDataInIntervalException("No data available");
            } else {
                if ( s.length()<256 ) {
                    throw new IOException( ex.getMessage() + ": "+s );
                } else {
                    throw ex;
                }
            }
        }
        httpConnect.disconnect();  // See unix tcptrack which shows there are many connections to the server. jbf@gardenhousepi:~ $ sudo tcptrack -i eth0

        monitor.setProgressMessage("parsing data");
                
        JSONObject jo= new JSONObject(builder.toString());
        JSONArray data= jo.getJSONArray("data");
        
        DataSetBuilder build= new DataSetBuilder( 2, data.length(), totalFields );
                
        for ( int i=0; i<data.length(); i++ ) {
            
            int ipd=0;
            int ifield=0;
            JSONArray record= data.getJSONArray(i);
            
            for ( ParamDescription pd: pds ) {
                if ( nfields[ipd]>1 ) {
                    JSONArray fields= record.getJSONArray(ipd);
                    int nf= nfields[ipd];
                    int lastField= nf+ifield;
                    for ( ; ifield<lastField; ifield++ ) {
                        build.putValue( -1, ifield, pd.units.parse( fields.getString(ipd) ) );
                    }
                } else {
                    build.putValue( -1, ifield, pd.units.parse( record.getString(ipd) ) );
                }
                ifield+= nfields[ipd];
                ipd++;
            }
            build.nextRecord();
        }
                
        QDataSet result;
                
        result= build.getDataSet();
        
        return result;
    }
    
    /**
     * TODO: commas within quotes.  remove extra whitespace.
     * @param line
     * @return 
     */
    private String[] lineSplit( String line ) {
        String[] ss= line.split(",",-2);
        for ( int i=0; i<ss.length; i++ ) {
            String s= ss[i].trim();
            if ( s.startsWith("\"") && s.endsWith("\"") ) {
                s= s.substring(1,s.length()-1);
            }
            ss[i]= s;
        }
        return ss;
    }
    
    public static File cacheFolder( URL url, String id ) {
        String cache= AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_FSCACHE);
        if ( cache.endsWith("/") ) cache= cache.substring(0,cache.length()-1);
        
        String dsroot= cache + "/hapi/" + url.getProtocol() + "/" + url.getHost() + "/" + url.getPath() + "/" + id; 
        return new File( dsroot );
    }
    
    /**
     * return the files that would be used for these parameters and time interval.
     * This is repeated code from getCacheReader.
     * @param url HAPI data request URL
     * @param id identifier for the dataset on the server.
     * @param parameters
     * @param timeRange
     * @see #getCacheReader(java.net.URL, java.lang.String[], org.das2.datum.DatumRange, boolean, long) 
     * @return 
     */
    public static LinkedHashMap<String,DatumRange> getCacheFiles( URL url, String id, String[] parameters, DatumRange timeRange ) {
        String s= AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_FSCACHE);
        if ( s.endsWith("/") ) s= s.substring(0,s.length()-1);
        String u= url.getProtocol() + "/" + url.getHost() + "/" + url.getPath();
        u= u + "/data/" + id;        
                
        LinkedHashMap<String,DatumRange> result= new LinkedHashMap<>();
         
        try {
            for (String parameter : parameters) {
                String theFile= s + "/hapi/"+ u ;
                FileStorageModel fsm = FileStorageModel.create(FileSystem.create( "file:" +theFile ), "$Y/$m/$Y$m$d." + parameter + ".csv.gz");
                String[] ff= fsm.getNamesFor(null);
                for (String ff1 : ff) {
                    DatumRange tr1= fsm.getRangeFor(ff1);
                    if ( timeRange==null || timeRange.intersects(tr1)) {
                        result.put(ff1,tr1);
                    }
                }
            }
        } catch ( IOException | IllegalArgumentException ex) {
            logger.log(Level.FINE, "exception in cache", ex );
            return null;
        }
                        
        return result;
  
    }
    
    private static AbstractLineReader calculateCacheReader( File[][] files ) {
        
        ConcatenateBufferedReader cacheReader= new ConcatenateBufferedReader();
        for ( int i=0; i<files.length; i++ ) {
            boolean haveAllForDay= true;
            if ( haveAllForDay ) {
                PasteBufferedReader r1= new PasteBufferedReader();
                r1.setDelim(',');
                for ( int j=0; j<files[i].length; j++ ) {
                    try {
                        FileReader oneDayOneParam= new FileReader(files[i][j]);
                        r1.pasteBufferedReader( new SingleFileBufferedReader( new BufferedReader(oneDayOneParam) ) );
                    }catch ( IOException ex ) {
                        logger.log( Level.SEVERE, ex.getMessage(), ex );
                        return null;
                    }
                }
                cacheReader.concatenateBufferedReader(r1);                
            }   
        }
        return cacheReader;
    }
    
    /**
     * make a connection to the server, expecting that the server might send back a 
     * 304 indicating the cache files should be used.
     * @param url HAPI data request URL
     * @param files corresponding files within cache.
     * @param lastModified non-zero to indicate time stamp of the oldest file found locally.
     * @return null or the cache reader.
     * @throws IOException 
     */
    public static AbstractLineReader maybeGetCacheReader( URL url, File[][] files, long lastModified) throws IOException {
        HttpURLConnection httpConnect;
        if ( FileSystem.settings().isOffline() ) {
            return null;
            //throw new FileSystem.FileSystemOfflineException("file system is offline");
        } else {
            loggerUrl.log(Level.FINE, "GET {0}", new Object[] { url } );            
            httpConnect= (HttpURLConnection)url.openConnection();
            httpConnect.setConnectTimeout(FileSystem.settings().getConnectTimeoutMs());
            httpConnect.setReadTimeout(FileSystem.settings().getReadTimeoutMs());
            httpConnect.setRequestProperty( "Accept-Encoding", "gzip" );
            String s= new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z").format(new Date(lastModified));
            httpConnect.setRequestProperty( "If-Modified-Since", s );
            httpConnect= (HttpURLConnection)HttpUtil.checkRedirect(httpConnect);
            httpConnect.connect();
        }
        if ( httpConnect.getResponseCode()==304 ) {
            logger.fine("using cache files because server says nothing has changed (304)");
            return calculateCacheReader( files );
        }
        boolean gzip= "gzip".equals( httpConnect.getContentEncoding() );
        return new SingleFileBufferedReader( new BufferedReader( 
            new InputStreamReader( gzip ? new GZIPInputStream( httpConnect.getInputStream() ) : httpConnect.getInputStream() ) ) );
    }

    /**
     * See if it's possible to create a Reader based on the contents of the HAPI
     * cache.  null is returned when cached files cannot be used.
     * @param url URL data request URL, where the time range parameters are ignored.
     * @param parameters the parameters to load, from ParameterDescription.name
     * @param timeRange the span to cover.  This should be from midnight-to-midnight.
     * @param offline if true, we are offline and anything available should be used.
     * @return null or the reader to use.
     * @see HapiServer#cacheAgeLimitMillis()
     * @see #getCacheFiles which has copied code.  TODO: fix this.
     */
    public static AbstractLineReader getCacheReader( URL url, String[] parameters, DatumRange timeRange, boolean offline, long lastModified) {
        String s= AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_FSCACHE);
        if ( s.endsWith("/") ) s= s.substring(0,s.length()-1);
        StringBuilder ub= new StringBuilder( url.getProtocol() + "/" + url.getHost() + "/" + url.getPath() );
        if ( url.getQuery()!=null ) {
            String[] querys= url.getQuery().split("\\&");
            Pattern p= Pattern.compile("id=(.+)");
            for ( String q : querys ) {
                Matcher m= p.matcher(q);
                if ( m.matches() ) {
                    ub.append("/").append(m.group(1));
                    break;
                }
            }
        } else {
            throw new IllegalArgumentException("query must be specified, implementation error");
        }
        
        
        DatumRange aday= TimeUtil.dayContaining(timeRange.min());
        List<DatumRange> trs= DatumRangeUtil.generateList( timeRange, aday );
        
        long timeNow= System.currentTimeMillis();
        
        // which granules are available for all parameters?
        boolean[][] hits= new boolean[trs.size()][parameters.length];
        File[][] files= new File[trs.size()][parameters.length];
        
        boolean staleCacheFiles= false;
        
        String u= ub.toString();
        
        if ( ! new File( s + "/hapi/"+ u  ).exists() ) {
            return null;
        }
        
        try {
            for ( int i=0; i<trs.size(); i++ ) {
                DatumRange tr= trs.get(i);
                for ( int j=0; j<parameters.length; j++ ) {
                    String parameter= parameters[j];
                    FileStorageModel fsm = FileStorageModel.create(FileSystem.create( "file:" + s + "/hapi/"+ u ), "$Y/$m/$Y$m$d." + parameter + ".csv");
                    File[] ff= fsm.getFilesFor(tr);
                    if ( ff.length==0 ) {
                        FileStorageModel fsmgz = FileStorageModel.create(FileSystem.create( "file:" + s + "/hapi/"+ u ), "$Y/$m/$Y$m$d." + parameter + ".csv.gz");
                        ff= fsmgz.getFilesFor(tr);
                    }
                    if ( ff.length>1 ) {
                        throw new IllegalArgumentException("implementation error, should get just one file per day.");
                    } else if ( ff.length==0 ) {
                        hits[i][j]= false;
                    } else {
                        File f= ff[0];
                        long ageMillis= timeNow - f.lastModified();
                        boolean isStale= ( ageMillis > HapiServer.cacheAgeLimitMillis() );
                        if ( lastModified>0 ) {
                            isStale= f.lastModified() < lastModified; // Note FAT32 only has 4sec resolution, which could cause problems.
                            if ( !isStale ) {
                                logger.fine("server lastModified indicates the cache file can be used");
                            } else {
                                logger.fine("server lastModified indicates the cache file should be updated");
                            }
                        }
                        if ( offline || !isStale ) {
                            hits[i][j]= true;
                            files[i][j]= f;
                        } else {
                            logger.log(Level.FINE, "cached file is too old to use: {0}", f);
                            hits[i][j]= false;
                            staleCacheFiles= true;
                        }
                    }
                }
            }
        } catch ( IOException | IllegalArgumentException ex) {
            logger.log(Level.FINE, "exception in cache", ex );
            return null;
        }
                
        if ( staleCacheFiles && !offline ) {
            logger.fine("old cache files found, but new data is available and accessible");
            return null;
        }
    
        boolean haveSomething= false;
        boolean haveAll= true;
        for ( int i=0; i<trs.size(); i++ ) {
            for ( int j=0; j<parameters.length; j++ ) {
                if ( hits[i][j]==false ) {
                    haveAll= false;
                }
            }
            if ( haveAll ) {
                haveSomething= true;
            }
        }
        
        if ( !haveAll ) {
            DatumRange missingRange=null;
            for ( int i=0; i<trs.size(); i++ ) {
                for ( int j=0; j<parameters.length; j++ ) {
                    if ( hits[i][j]==false ) {
                        if ( missingRange==null ) {
                            missingRange= trs.get(i);
                        } else {
                            missingRange= DatumRangeUtil.union( missingRange, trs.get(i) );
                        }
                    }
                }
            }
            System.err.println("missingRange="+missingRange );
            if ( missingRange!=null && missingRange.min().equals(timeRange.min()) || missingRange.max().equals(timeRange.max()) ) {
                System.err.println("candidate for new partial cache, only "+missingRange+" needs to be loaded.");
            }
        }
        
        if ( !offline && !haveAll ) {
            logger.fine("some cache files missing, but we are on-line and should retrieve all of them");
            return null;
        }
        
        if ( !haveSomething ) {
            logger.fine("no cached data found");
            return null;
        }
        
        AbstractLineReader result;
        
        // digest all this into a single timestamp.  
        // For each day, what is the oldest any of the granules was created?
        // For each interval, what was the oldest of any granule?
        long timeStamp= Long.MAX_VALUE;
        for ( int i=0; i<trs.size(); i++ ) {
            for ( int j=0; j<parameters.length; j++ ) {
                timeStamp= Math.min( timeStamp, files[i][j].lastModified() );
            }
        }
        
        try {
            result= maybeGetCacheReader( url, files, timeStamp );
            if ( result!=null ) return result;
        } catch ( IOException ex ) {
            logger.log( Level.WARNING, null, ex );
        }
            
        AbstractLineReader cacheReader= calculateCacheReader( files );
        return cacheReader;
                
    }
    
    private ParamDescription[] getParameterDescriptions(JSONObject doc) throws IllegalArgumentException, ParseException, JSONException {
        JSONArray parameters= doc.getJSONArray("parameters");
        int nparameters= parameters.length();
        
        long modificationDate= 0L;
        if ( doc.has("modificationDate") ) {
            String s= doc.getString("modificationDate");
            Datum d= Units.ms1970.parse(s);
            modificationDate= (long)( d.doubleValue(Units.ms1970) );
        }
        
        ParamDescription[] pds= new ParamDescription[nparameters];
        for ( int i=0; i<nparameters; i++ ) {
            
            final JSONObject jsonObjecti = parameters.getJSONObject(i);
            
            String name= jsonObjecti.getString("name"); // the name of one of the parameters.
            
            if ( name==null ) {
                name="name"+i;
                logger.log(Level.WARNING, "name not found for {0}th parameter", i );
            }
            
            pds[i]= new ParamDescription( name );
            pds[i].modifiedDateMillis= modificationDate;
            
            String type;
            if ( jsonObjecti.has("type") ) {
                type= jsonObjecti.getString("type");
                if ( type==null ) type="";
            } else {
                type= "";
            }
            if ( type.equals("") ) {
                logger.log(Level.FINE, "type is not defined: {0}", name);
            }
            if ( type.equalsIgnoreCase("isotime") ) {
                if ( !type.equals("isotime") ) {
                    logger.log(Level.WARNING, "isotime should not be capitalized: {0}", type);
                }
                pds[i].units= Units.us2000;
                if ( jsonObjecti.has("length") ) {
                    pds[i].type= "time"+jsonObjecti.getInt("length");
                    pds[i].length= jsonObjecti.getInt("length");
                } else {
                    logger.log(Level.FINE, "server doesn''t report length for \"{0}\", assuming 24 characters, and that it doesn''t matter", name);
                    pds[i].type= "time24";
                }
                
            } else {
                pds[i].type= type;
                if ( jsonObjecti.has("units") ) {
                    Object ou= jsonObjecti.get("units");
                    if ( ou instanceof String ) {
                        String sunits= (String)ou;
                        pds[i].units= Units.lookupUnits(sunits);
                    }
                } else {
                    pds[i].units= Units.dimensionless;
                }
                
                if ( type.equals("String") ) {
                    type="string";
                    logger.warning("String used for type instead of string (lower case)");
                }

                if ( type.equals("string") ) {
                    pds[i].units= EnumerationUnits.create(name);
                }
                
                if ( jsonObjecti.has("fill") ) {
                    String sfill= jsonObjecti.getString("fill");
                    if ( sfill!=null && !sfill.equals("null") ) {
                        if ( type.equals("string") ) {
                            pds[i].fillValue= ((EnumerationUnits)pds[i].units).createDatum( sfill ).doubleValue( pds[i].units );
                            pds[i].hasFill= true;
                        } else {
                            pds[i].fillValue= pds[i].units.parse( sfill ).doubleValue( pds[i].units );
                            pds[i].hasFill= true;
                        }
                    }
                } else {
                    pds[i].fillValue= FILL_VALUE; // when a value cannot be parsed, but it is not identified.
                }
                if ( jsonObjecti.has("description") ) {
                    pds[i].description= jsonObjecti.getString("description");
                    if ( pds[i].description==null ) pds[i].description= "";
                } else {
                    pds[i].description= ""; // when a value cannot be parsed, but it is not identified.
                }

                if ( jsonObjecti.has("label") ) {
                    Object olabel= jsonObjecti.get("label");
                    if ( olabel instanceof String ) {
                        pds[i].label= (String)olabel;
                    }
                    if ( pds[i].label==null ) pds[i].label= name;
                } else {
                    pds[i].label= name;
                }
                
                if ( jsonObjecti.has("length") ) {
                    pds[i].length= jsonObjecti.getInt("length");
                }

                if ( jsonObjecti.has("size") ) {
                    Object o= jsonObjecti.get("size");
                    if ( !(o instanceof JSONArray) ) {
                        if ( o.getClass()==Integer.class ) {
                            pds[i].size= new int[] { ((Integer)o) };
                            pds[i].nFields= ((Integer)o);
                            logger.log( Level.WARNING, "size should be an int array, found int: {0}", name);
                        } else if ( o.getClass()==String.class ) {
                            pds[i].size= new int[] { Integer.parseInt( (String)o ) };
                            pds[i].nFields= (Integer.parseInt((String)o));
                            logger.log( Level.WARNING, "size should be an int array, found String: {0}", name);
                        } else {
                            throw new IllegalArgumentException( String.format( "size should be an int array: %s", name ) );
                        }
                    } else {
                        JSONArray a= (JSONArray)o;
                        pds[i].size= new int[a.length()];
                        int nFields=1;
                        for ( int j=0; j<a.length(); j++ ) {
                            pds[i].size[j]= a.getInt(j);
                            nFields*= pds[i].size[j];
                        }
                        pds[i].nFields= nFields;
                    }
                    if ( jsonObjecti.has("bins") ) {
                        o= jsonObjecti.get("bins");
                        if ( o instanceof JSONArray ) {
                            JSONArray ja= (JSONArray)o;
                            pds[i].depend= new QDataSet[ja.length()];
                            pds[i].dependName= new String[ja.length()];
                            for ( int j=0; j<ja.length(); j++ ) {
                                JSONObject bins= ja.getJSONObject(j);
                                if ( bins.has("parameter") ) {  // deprecated, see binsParameter below.  TODO: revisit this.
                                    int n= pds[i].nFields;
                                    pds[i].depend[j]= Ops.findgen(n);
                                    pds[i].dependName[j]= bins.getString("parameter");
                                } else if ( bins.has("ranges") ) {
                                    QDataSet dep= getJSONBins(ja.getJSONObject(j));
                                    pds[i].depend[j]= dep;
                                    pds[i].renderType= QDataSet.VALUE_RENDER_TYPE_NNSPECTROGRAM;
                                } else if ( bins.has("centers") ) {
                                    QDataSet dep= getJSONBins(ja.getJSONObject(j));
                                    pds[i].depend[j]= dep;
                                } else {
                                    int n= pds[i].size[j];
                                    pds[i].depend[j]= Ops.findgen(n);
                                }
                            }
                        } else {
                            logger.warning("bins should be an array");
                            JSONObject bins= jsonObjecti.getJSONObject("bins"); 
                            if ( pds[i].depend==null ) pds[i].depend= new QDataSet[1];
                            if ( pds[i].dependName==null ) pds[i].dependName= new String[1];
                            if ( bins.has("parameter") ) { // this will be implemented later.
                                int n= DataSetUtil.product(pds[i].size);
                                pds[i].depend[0]= Ops.findgen(n);
                                pds[i].dependName[0]= bins.getString("parameter");
                            } else if ( bins.has("values") ) {
                                QDataSet dep1= getJSONBins(bins);
                                pds[i].depend[0]= dep1;
                            } else {
                                int n= DataSetUtil.product(pds[i].size);
                                pds[i].depend[0]= Ops.findgen(n);
                            }
                        }
                    } else if ( jsonObjecti.has("binsParameter") ) {
                        o= jsonObjecti.get("binsParameter");
                        if ( o instanceof JSONArray ) {
                            JSONArray ja= (JSONArray)o;
                            pds[i].depend= new QDataSet[ja.length()];
                            pds[i].dependName= new String[ja.length()];
                            for ( int j=0; j<ja.length(); j++ ) {
                                String s= ja.getString(j);
                                int n= DataSetUtil.product(pds[i].size);
                                pds[i].depend[j]= Ops.findgen(n);
                                pds[i].dependName[j]= s;
                            }
                        }
                    } 
                }
            }
        }
        return pds;
    }

    /**
     * Reform bundle into typical QDataSet schemes.  For example, a rank 2 bundle 
     * ds[;T,N] would be reformed into rank 1 N[T].
     * @param ds the bundle dataset
     * @param pds metadata for each column.
     * @param sort if non-null, resort the data with these indeces.
     * @return 
     */
    private QDataSet repackage(QDataSet ds, ParamDescription[] pds, int[] sort ) {
        int nparameters= ds.length(0);
        
        boolean combineRank2Depend1= pds.length==3 && pds[1].dependName!=null;
        
        QDataSet depend0= Ops.copy( Ops.slice1( ds,0 ) ); //TODO: this will be unnecessary after debugging.
        if ( ds.length(0)==2 ) {
            ds= Ops.copy( Ops.slice1( ds, 1 ) );
            ds= Ops.putProperty( ds, QDataSet.DEPEND_0, depend0 );
            ds= Ops.putProperty( ds, QDataSet.NAME, Ops.safeName(pds[1].name) );
            ds= Ops.putProperty( ds, QDataSet.LABEL, pds[1].label );
            ds= Ops.putProperty( ds, QDataSet.TITLE, pds[1].description );
            ds= Ops.putProperty( ds, QDataSet.UNITS, pds[1].units );
            if ( pds[1].hasFill ) {
                ds= Ops.putProperty( ds, QDataSet.FILL_VALUE, pds[1].fillValue );
            }
        } else if ( pds.length==2 ) {
            ds= Ops.copy( Ops.trim1( ds, 1, ds.length(0) ) );
            if ( pds[1].size.length>1 ) {
                ds= Ops.reform( ds, ds.length(), pds[1].size );
            }
            ds= Ops.putProperty( ds, QDataSet.DEPEND_0, depend0 );
            ds= Ops.putProperty( ds, QDataSet.NAME, Ops.safeName(pds[1].name) );
            ds= Ops.putProperty( ds, QDataSet.LABEL, pds[1].label );
            ds= Ops.putProperty( ds, QDataSet.TITLE, pds[1].description );
            ds= Ops.putProperty( ds, QDataSet.UNITS, pds[1].units );
            if ( pds[1].hasFill ) {
                ds= Ops.putProperty( ds, QDataSet.FILL_VALUE, pds[1].fillValue );
            }
            //if ( pds[1].depend1!=null ) {
            //    ds= Ops.putProperty( ds, QDataSet.DEPEND_1, pds[1].depend1 );
            //}
            if ( pds[1].depend!=null ) {
                for ( int j=0; j<pds[1].size.length; j++ ) {
                    ds= Ops.putProperty( ds, "DEPEND_"+(j+1), pds[1].depend[j] );
                }
            }
            if ( pds.length==2 && QDataSet.VALUE_RENDER_TYPE_NNSPECTROGRAM.equals( pds[1].renderType ) ) {
                ds= Ops.putProperty( ds, QDataSet.RENDER_TYPE, pds[1].renderType );
            }
        } else if ( pds.length==1 ) {
            return depend0;
            
        } else if ( combineRank2Depend1 ) {
            // we need to remove Epoch to DEPEND_0.
            SparseDataSetBuilder[] sdsbs= new SparseDataSetBuilder[pds.length];
            int ifield=1;
            int length1=ds.length(0); // this should be overwritten
            for ( int i=1; i<pds.length; i++ ) {
                int nfields1= pds[i].nFields;
                SparseDataSetBuilder sdsb= new SparseDataSetBuilder(2);
                sdsb.setLength(nfields1);
                int startIndex= sort==null ? ifield-1 : sort[ifield]-1;
                if ( nfields1>1 ) {
                    //bdsb.putProperty( QDataSet.ELEMENT_DIMENSIONS, ifield-1, pds[i].size ); // not supported yet.
                    sdsb.putProperty( QDataSet.ELEMENT_NAME, startIndex, Ops.safeName( pds[i].name ) );
                    sdsb.putProperty( QDataSet.ELEMENT_LABEL, startIndex, pds[i].name );        
                    for ( int j=0; j<pds[i].size.length; j++ ) {
                        sdsb.putValue( startIndex, j, pds[i].size[j] );
                    }
                    if ( pds[i].depend!=null ) {
                        if ( pds[i].size.length!=pds[i].depend.length ) throw new IllegalArgumentException("pds[i].size.length!=pds[i].depend.length");
                        for ( int j=0; j<pds[i].size.length; j++ ) {
                            if ( pds[i].dependName[j]!=null ) {
                                // wait
                            } else {
                                sdsb.putProperty( "DEPEND_"+(j+1), startIndex, pds[i].depend[j]);
                            }
                        }
                    }
                    //sdsb.putValue( QDataSet.ELEMENT_DIMENSIONS, ifield-1, pds[i].size );                    
                }
                for ( int j=0; j<nfields1; j++ ) {
                    if ( nfields1>1 ) {
                        sdsb.putProperty( QDataSet.START_INDEX, startIndex + j, startIndex );
                        sdsb.putProperty( QDataSet.LABEL, startIndex + j, pds[i].name +" ch"+j );                    
                        sdsb.putProperty( QDataSet.NAME, startIndex + j, Ops.safeName(pds[i].name)+"_"+j );
                    } else {
                        sdsb.putProperty( QDataSet.LABEL, startIndex + j, pds[i].name );                    
                        sdsb.putProperty( QDataSet.NAME, startIndex + j, Ops.safeName(pds[i].name) ); 
                    }
                    
                    sdsb.putProperty( QDataSet.TITLE, startIndex + j, pds[i].description );
                    sdsb.putProperty( QDataSet.UNITS, startIndex + j, pds[i].units );
                    if ( pds[i].hasFill ) {
                        sdsb.putProperty( QDataSet.FILL_VALUE, startIndex + j,  pds[i].fillValue );
                    }
                    if ( nfields1>1 ) {
                        sdsb.putProperty( QDataSet.START_INDEX, startIndex + j, startIndex );
                    }                    
                    ifield++;
                }
                length1=  nfields1;
                sdsbs[i]= sdsb;
            }
            
            int start= 1;
            WritableDataSet wds= Ops.copy( Ops.trim1( ds, start, start+length1 ) );
            start= start+length1;
            wds.putProperty( QDataSet.DEPEND_0, depend0 );
            wds.putProperty( QDataSet.BUNDLE_1, sdsbs[1].getDataSet() );
            
            for ( int i=1; i<pds.length; i++ ) { // only works for rank2!!!
                if ( pds[i].dependName!=null ) {
                    for (String dependName : pds[i].dependName) {
                        if (dependName != null) {
                            int k;
                            for (k=1; k<pds.length; k++) {
                                if (pds[k].name.equals(dependName)) {
                                    break;
                                }
                            }
                            if ( k<pds.length ) {
                                WritableDataSet depds= Ops.copy( Ops.trim1( ds, start, start+length1 ) );
                                depds.putProperty( QDataSet.DEPEND_0, depend0 );
                                depds.putProperty( QDataSet.BUNDLE_1, sdsbs[k].getDataSet() );    
                                start= start+length1;
                                wds.putProperty( "DEPEND_"+i, depds );
                            }
                        }
                    }
                }
            }
            
            ds= wds;            
            
        } else {
            // we need to remove Epoch to DEPEND_0.
            SparseDataSetBuilder sdsb= new SparseDataSetBuilder(2);
            sdsb.setLength(nparameters-1);
            int ifield=1;
            for ( int i=1; i<pds.length; i++ ) {
                int nfields1= DataSetUtil.product(pds[i].size);
                int startIndex= sort==null ? ifield-1 : sort[ifield]-1;
                if ( nfields1>1 ) {
                    //bdsb.putProperty( QDataSet.ELEMENT_DIMENSIONS, ifield-1, pds[i].size ); // not supported yet.
                    sdsb.putProperty( QDataSet.ELEMENT_NAME, startIndex, Ops.safeName( pds[i].name ) );
                    sdsb.putProperty( QDataSet.ELEMENT_LABEL, startIndex, pds[i].name );        
                    for ( int j=0; j<pds[i].size.length; j++ ) {
                        sdsb.putValue( startIndex, j, pds[i].size[j] );
                    }
                    if ( pds[i].depend!=null ) {
                        if ( pds[i].size.length!=pds[i].depend.length ) throw new IllegalArgumentException("pds[i].size.length!=pds[i].depend.length");
                        for ( int j=0; j<pds[i].size.length; j++ ) {
                            sdsb.putProperty( "DEPEND_"+(j+1), startIndex, pds[i].depend[j]);
                        }
                    }
                    //sdsb.putValue( QDataSet.ELEMENT_DIMENSIONS, ifield-1, pds[i].size );                    
                }
                for ( int j=0; j<nfields1; j++ ) {
                    if ( nfields1>1 ) {
                        sdsb.putProperty( QDataSet.START_INDEX, startIndex + j, startIndex );
                        sdsb.putProperty( QDataSet.LABEL, startIndex + j, pds[i].name +" ch"+j );                    
                        sdsb.putProperty( QDataSet.NAME, startIndex + j, Ops.safeName(pds[i].name)+"_"+j );
                    } else {
                        sdsb.putProperty( QDataSet.LABEL, startIndex + j, pds[i].name );                    
                        sdsb.putProperty( QDataSet.NAME, startIndex + j, Ops.safeName(pds[i].name) ); 
                    }
                    
                    sdsb.putProperty( QDataSet.TITLE, startIndex + j, pds[i].description );
                    sdsb.putProperty( QDataSet.UNITS, startIndex + j, pds[i].units );
                    if ( pds[i].hasFill ) {
                        sdsb.putProperty( QDataSet.FILL_VALUE, startIndex + j,  pds[i].fillValue );
                    }
                    if ( nfields1>1 ) {
                        sdsb.putProperty( QDataSet.START_INDEX, startIndex + j, startIndex );
                    }                    
                    ifield++;
                }
            }
            
            ds= Ops.copy( Ops.trim1( ds, 1, ds.length(0) ) );
            ds= Ops.putProperty( ds, QDataSet.DEPEND_0, depend0 );
            ds= Ops.putProperty( ds, QDataSet.BUNDLE_1, sdsb.getDataSet() );
        }
        return ds;
    }
    
}
