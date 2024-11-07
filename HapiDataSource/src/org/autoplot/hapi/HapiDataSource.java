
package org.autoplot.hapi;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
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
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.datasource.DefaultTimeSeriesBrowse;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.capability.Caching;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import static org.autoplot.hapi.HapiServer.readFromURL;
import org.das2.datum.TimeParser;
import org.das2.datum.TimeUtil;
import org.das2.fsm.FileStorageModel;
import org.das2.graph.ColorUtil;
import org.das2.qds.IDataSet;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.DataSetBuilder;
import org.das2.qstream.TransferType;
import org.das2.util.filesystem.FileSystem;
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
    
    private static final String WARNING_TIME_MALFORMED= "time malformed";
    private static final String WARNING_TIME_ORDER= "time out-of-order";
    
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
                HapiDataSource.csvCache.clear();
            }
            
        });
    }

    private static QDataSet getJSONBins( JSONObject binsObject ) throws JSONException {
        boolean foundTimeVarying= false;
        JSONArray bins=null;
        if ( binsObject.has("values") ) {
            logger.fine("using deprecated bins");
            bins= binsObject.getJSONArray("values");
        } else if ( binsObject.has("centers") ) {
            bins= binsObject.optJSONArray("centers");
            if ( bins==null ) {
                logger.info("time-varying centers are not supported, yet");
                foundTimeVarying= true;
            }
        }
        
        JSONArray ranges= null;
        if ( binsObject.has("ranges") ) {
            ranges= binsObject.optJSONArray("ranges");
            if ( ranges==null ) {
                logger.info("time-varying ranges are not supported, yet");
                foundTimeVarying= true;
            }
        }
        
        int len;
        if ( ranges==null && bins==null ) {
            if ( foundTimeVarying ) {
                logger.info("time-varying detected, not supported yet");
                return null;
            }
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

        if ( binsObject.has(HapiUtil.KEY_UNITS) ) {
            Object uo= binsObject.get(HapiUtil.KEY_UNITS);
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
            result.putProperty( QDataSet.UNITS, min.property(QDataSet.UNITS) ); 
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
    
    /**
     * returns the info for the object to use, or null.
     * @return 
     */
    private JSONObject maybeGetDiffResolutionInfo(String id) {
        try {
            URL url= HapiServer.createURL( this.resourceURI.toURL(), "semantics");
            String s= readFromURL( url, "json" );
            JSONObject o= new JSONObject(s);
            JSONArray a= o.optJSONArray("cadenceVariants");
            if ( a!=null ) {
                for ( int i=0; i<a.length(); i++ ) {
                    Object o1= a.get(i);
                    if ( o1 instanceof JSONObject ) {
                        JSONObject jo2= (JSONObject)o1;
                        if ( jo2.optString("groupId","").equals(id) ) {
                            String sourceId= jo2.getString("sourceId");
                            return getInfo( sourceId );
                        }
                    }
                }
            }
            return null;
        } catch (JSONException ex) {
            Logger.getLogger(HapiDataSource.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(HapiDataSource.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    private JSONObject getInfo( String id ) throws MalformedURLException, IOException, JSONException {
        URI server = this.resourceURI;
        if ( id.equals("") ) throw new IllegalArgumentException("missing id");
        id = URLDecoder.decode(id,"UTF-8");
        JSONArray jo= HapiServer.getCatalog(server.toURL());
        for ( int i=0; i<jo.length(); i++ ) {
            JSONObject jo1= jo.getJSONObject(i);
            if ( jo1.get("id").equals(id) ) {
                return HapiServer.getInfo(server.toURL(), id);
            }
        }
        JSONObject r = maybeGetDiffResolutionInfo(id);
        if ( r==null ) {
            throw new IllegalArgumentException("Bad id: "+id );
        } else {
            return r;
        }
    }

    /**
     * reformat the URL with the new timerange.
     * @param url the URL containing time.min and time.max parameters.
     * @param tr the new timerange
     * @param vers the HAPI server version
     * @return the URL with time.min and time.max replaced.
     */
    private static URL replaceTimeRangeURL(URL url, DatumRange tr, String vers ) {
        try {
            URISplit split= URISplit.parse(url.toURI());
            Map<String,String> params= URISplit.parseParams(split.params);
            String smin= tr.min().toString();
            String smax= tr.max().toString();
            if ( smin.endsWith("00:00:00.000Z") ) smin= smin.substring(0,smin.length()-14) + "T00:00Z";
            if ( smax.endsWith("00:00:00.000Z") ) smax= smax.substring(0,smax.length()-14) + "T00:00Z";
            if ( vers.startsWith("1.") || vers.startsWith("2.")) {
                params.put("time.min",smin);
                params.put("time.max",smax);
            } else {
                params.put("start",smin);
                params.put("stop",smax);
            }
            split.params= URISplit.formatParams(params);
            String surl= URISplit.format(split);
            url= new URL(surl);
            return url;
        } catch (URISyntaxException | MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    public static class ParamDescription {
        boolean hasFill= false;
        double fillValue= -1e38;
        Units units= Units.dimensionless;
        String name= "";
        String description= "";
        String label="";
        String[] labels=null;
        String type= "";
        /**
         * number of indices in each index.
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
        
        JSONObject parameter = null;
        
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
    
    private static final Map<String,ArrayList<ByteBuffer>> binaryCache= new ConcurrentHashMap<>();
   
    private static final Map<String,ArrayList<String>> csvCache= new ConcurrentHashMap<>();
    
    /**
     * print the cache stats.
     * @see https://sourceforge.net/p/autoplot/bugs/1996/
     */
    public static void printCacheStats() {
        if ( csvCache==null || csvCache.isEmpty() ) {
            System.err.println( "(cache is empty)" );
        } else {
            for ( Entry<String,ArrayList<String>> s: csvCache.entrySet() ) {
                System.err.println( "" + s.getKey() +": "+s.getValue().size()+" records");
            }
        }
        if ( binaryCache==null || binaryCache.isEmpty() ) {
            System.err.println( "(cache is empty)" );
        } else {
            for ( Entry<String,ArrayList<ByteBuffer>> s: binaryCache.entrySet() ) {
                System.err.println( "" + s.getKey() +": "+s.getValue().size()+" records");
            }
        }        
    }
    
    /**
     * return the local folder of the cache for HAPI data.  This will end with
     * a slash.
     * @return the local folder of the cache for HAPI data.
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
                if ( !new File(hapiCache).mkdirs() ) {
                    logger.log(Level.WARNING, "unable to mkdir directories {0}", hapiCache);
                }
            }
        }
        return hapiCache;

    }
    
    private static void writeToBinaryCachedData(String location, ParamDescription[] pp, Datum xx, ByteBuffer buf) throws IOException {
                
        String hapiCache= getHapiCache();
        
        TimeParser tp= TimeParser.create( "$Y/$m/$Y$m$d" );
        
        String sxx= tp.format(xx);
                
        String format= "binary";
        
        String u= location;
        Datum t0= lastRecordFound.get( u + "/" + sxx );
        if ( t0==null ) {
            String f= hapiCache + u + "/" + sxx + "." + pp[0].name + "." + format;
            File ff= new File(f);
            if ( ff.exists() ) {
                try ( BufferedReader read= new BufferedReader(
                        new InputStreamReader( new FileInputStream(ff), HapiServer.UTF8 ) ) ) {
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
        }
        if ( t0!=null && t0.ge(xx) ) {
            logger.log(Level.FINE, "clear all cached files for {0}", sxx);
            for (ParamDescription pp1 : pp) {
                String f = hapiCache + u + "/" + sxx + "." + pp1.name + "." +format;
                File ff= new File(f);
                if ( ff.exists() ) {
                    if ( !ff.delete() ) logger.log(Level.INFO, "unable to delete file: {0}", ff);
                }
            }
        }
        
        for (ParamDescription pp1 : pp) {
            String f = u + "/" + sxx + "." + pp1.name + "." + format + "." + Thread.currentThread().getId();
            logger.log(Level.FINER, "cache.get({0})", f);
            ArrayList<ByteBuffer> sparam= binaryCache.get(f);
            if ( sparam==null ) {
                sparam= new ArrayList<>();
                binaryCache.put(f,sparam);
                logger.log(Level.FINE, "cache.put({0},ArrayList({1}))", new Object[]{f, sparam.size()});
            }
            
            ByteBuffer buf2= ByteBuffer.allocate( buf.capacity() );
            buf2.put(buf);
            
            sparam.add( buf2 );
            
        }
        
        lastRecordFound.put( u + "/" + sxx,xx);
        
    }
    
     
    private static void writeToCsvCachedData( String location, ParamDescription[] pp, Datum xx, String[] ss, boolean allParam) throws IOException {
        
        String hapiCache= getHapiCache();
        
        TimeParser tp= TimeParser.create( "$Y/$m/$Y$m$d" );
        
        String sxx= tp.format(xx);
                
        String u= location;
        
        Datum t0= lastRecordFound.get( u + "/" + sxx );
        if ( t0==null ) {
            String f= hapiCache + u + "/" + sxx + "." + pp[0].name + ".csv";
            File ff= new File(f);
            if ( ff.exists() ) {
                try ( BufferedReader read= new BufferedReader(
                        new InputStreamReader( new FileInputStream(ff), HapiServer.UTF8 ) ) ) {
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
            ArrayList<String> sparam= csvCache.get(f);
            if ( sparam==null ) {
                sparam= new ArrayList<>();
                csvCache.put(f,sparam);
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
     * See https://sourceforge.net/p/autoplot/bugs/2043/
     * @param url url used to locate position in cache.
     * @param pp parameters 
     * @param xx time used to id the file.
     */
    private static void writeToBinaryCachedDataFinish(String location, ParamDescription[] pp, Datum xx, boolean allParam) throws IOException {
        
        logger.log(Level.FINE, "writeToBinaryCachedDataFinish: {0}", xx);

        String hapiCache= getHapiCache();
        
        String format= "binary";

        long currentTimeMillis= pp[0].modifiedDateMillis;
        TimeParser tp= TimeParser.create( "$Y/$m/$Y$m$d" );
        String sxx= tp.format(xx);
        String u= location;
        
        int ipos=0;
        for (ParamDescription pp1 : pp) {
            String f = u + "/" + sxx + "." + pp1.name + "." + format + "."+ Thread.currentThread().getId();
            logger.log(Level.FINE, "remove from cache: {0}", f);
            
            File ff= new File( hapiCache + u + "/" + sxx + "." + pp1.name + "." + format +"");
            if ( !ff.getParentFile().exists() ) {
                if ( !ff.getParentFile().mkdirs() ) {
                    throw new IOException("unable to mkdirs "+ff.getParent() );
                }
            }
            File ffTemp= new File( hapiCache + u + "/" + sxx + "." + pp1.name + "."+ format + "."+Thread.currentThread().getId() );
            
            ArrayList<ByteBuffer> data= binaryCache.get(f); //TODO: use "remove" after debugging.
            
            int ilen= BufferDataSet.byteCount(pp1.type) * DataSetUtil.product(pp1.size);
            try ( FileChannel ffTempChannel= new FileOutputStream(ffTemp).getChannel() ) {
                for ( ByteBuffer buf: data ) {
                    buf.position(ipos);
                    buf.limit(ipos+ilen);
                    ffTempChannel.write(buf);
                }
            }
            ipos+= pp1.length;
        } 
        
        
        synchronized ( HapiDataSource.class ) {
            for (ParamDescription pp1 : pp) {
                File ffTemp= new File( hapiCache + u + "/" + sxx + "." + pp1.name + "."+ format + "."+Thread.currentThread().getId() );
                File ff= new File( hapiCache + u + "/" + sxx + "." + pp1.name + "." + format +"");
                if ( !ffTemp.renameTo(ff) ) {
                    logger.log(Level.WARNING, "rename to {0} failed", ff);
                }
                if ( currentTimeMillis>0 ) {
                    if ( !ff.setLastModified(currentTimeMillis) ) {
                        logger.log(Level.WARNING, "setLastModified for {0} failed", ff);
                    }
                }
            }
        }
    }
    
    /** 
     * See https://sourceforge.net/p/autoplot/bugs/2043/
     * @param url url used to locate position in cache.
     * @param pp parameters 
     * @param xx time used to id the file.
     */
    private static void writeToCsvCachedDataFinish(String location, ParamDescription[] pp, Datum xx) throws IOException {
        logger.log(Level.FINE, "writeToCachedDataFinish: {0}", xx);
        
        String hapiCache= getHapiCache();
        
        String format= "csv";

        long currentTimeMillis= pp[0].modifiedDateMillis;
        TimeParser tp= TimeParser.create( "$Y/$m/$Y$m$d" );
        String sxx= tp.format(xx);
        String u= location;
        for (ParamDescription pp1 : pp) {
            String f = u + "/" + sxx + "." + pp1.name + "." + format + "."+ Thread.currentThread().getId();
            logger.log(Level.FINE, "remove from cache: {0}", f);
            ArrayList<String> sparam= csvCache.remove(f);
            File ff= new File( hapiCache + u + "/" + sxx + "." + pp1.name + "." + format +".gz");
            if ( !ff.getParentFile().exists() ) {
                if ( !ff.getParentFile().mkdirs() ) {
                    throw new IOException("unable to mkdirs "+ff.getParent() );
                }
            }
            File ffTemp= new File( hapiCache + u + "/" + sxx + "." + pp1.name + "."+ format + ".gz."+Thread.currentThread().getId() );
            //int line=0;
            try (final BufferedWriter w = new BufferedWriter( new OutputStreamWriter( new GZIPOutputStream( new FileOutputStream(ffTemp) ) ) ) ) {
                if ( sparam!=null ) {
                    for ( String s123: sparam ) {
                        //line++;
                        w.write(s123);
                        w.newLine();
                    }
                }
            }
        }
         
        synchronized ( HapiDataSource.class ) {
            for (ParamDescription pp1 : pp) {
                File ff= new File( hapiCache + u + "/" + sxx + "." + pp1.name + "." + format +".gz");
                File ffTemp= new File( hapiCache + u + "/" + sxx + "." + pp1.name + "."+ format + ".gz."+Thread.currentThread().getId() );
                if ( !ffTemp.renameTo(ff) ) {
                    logger.log(Level.WARNING, "renameTo {0} failed", ff);
                }
                if ( currentTimeMillis>0 ) {
                    if ( !ff.setLastModified(currentTimeMillis) ) {
                        logger.log(Level.WARNING, "setLastModified for {0} failed", ff);
                    }
                }
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
        try ( BufferedReader in= new BufferedReader( 
                new InputStreamReader( httpConnect.getInputStream(), HapiServer.UTF8 ) ) ) {
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
    
    /**
     * return the index of the parameter name.
     * @param pds
     * @param name
     * @return 
     */
    private static int indexOfParameter( ParamDescription[] pds, String name ) {
        for ( int i=0; i<pds.length; i++ ) {
            if ( pds[i].name.equals(name) ) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * replace this with String.join after Java8 upgrade.
     * @param delim
     * @param pieces
     * @return 
     */
    private String join( String delim, List<String> pieces ) {
        if ( pieces.isEmpty() ) return "";
        StringBuilder b= new StringBuilder(pieces.get(0));
        for ( int i=1; i<pieces.size(); i++ ) {
            b.append(delim);
            b.append(pieces.get(i));
        }
        return b.toString();
    }
    
    Map<Datum,Color> lookupColorCache= new HashMap<>();
    
    private Color lookupColor( Map<Pattern,Color> lookup, Datum d ) {
        Color c= lookupColorCache.get(d);
        if ( c!=null ) return c;
        for ( Entry<Pattern,Color> e: lookup.entrySet() ) {
            Pattern p= e.getKey();
            if ( p.matcher(d.toString()).matches() ) {
                lookupColorCache.put( d, e.getValue() );
                break;
            }
        }
        return Color.GRAY;
    }
            
    @Override
    public synchronized QDataSet getDataSet(ProgressMonitor monitor) throws Exception {
        URI server = this.resourceURI;
        
        String format= getParam("format","csv");
        
        { // This kludge was to support an early HAPI server which did not give a conformant data response.
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
        if ( pp.contains("%2C") ) {  // commas are escaped
            pp= URLDecoder.decode(pp,"UTF-8");
        }
        
        JSONObject info;
        info= getInfo(id);
        
        info= HapiUtil.resolveRefs(info);
        
        String vers= info.getString("HAPI");
        
        monitor.setProgressMessage("got info");
        monitor.setTaskProgress(20);
        
        ParamDescription[] pds= getParameterDescriptions(info);
        
        DatumRange tr; // TSB = DatumRangeUtil.parseTimeRange(timeRange);
        tr= tsb.getTimeRange();
        
        if ( tr==null ) {
            throw new IllegalArgumentException("timerange is missing");
        }
        
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
            for ( int i=0; i<pps.length; i++ ) {
                if ( i==0 && ( pps[i].equals("Time") || pps[i].equals("time") ) ) {
                    pps[i] = parametersArray.getJSONObject(0).getString("name");
                }
                pps[i]= pps[i].replace("+"," ");
                pps[i]= pps[i].replaceAll("\\%2B","+");
            }
            Map<String,Integer> map= new HashMap();
            for ( int i=0; i<nparam; i++ ) {
                map.put( parametersArray.getJSONObject(i).getString("name"), i ); // really--should name/id are two names for the same thing...
            }
            if ( !pps[0].equals(parametersArray.getJSONObject(0).getString("name")) ) { // add Time if it wasn't specified.
                pp= parametersArray.getJSONObject(0).getString("name") + ","+ pp;
                pps= pp.split(",");
            }
            
            pp= String.join( ",", pps ); // because we might have changed "timestamp" to "Time"
            
            ArrayList namesNotFound= new ArrayList();
            ParamDescription[] subsetPds= new ParamDescription[pps.length];
            for ( int ip=0; ip<pps.length; ip++ ) {
                Integer ii= map.get(pps[ip]);
                if ( ii==null ) {
                    namesNotFound.add(pps[ip]);
                } else {
                    int i= map.get(pps[ip]);
                    subsetPds[ip]= pds[i];
                }
            }
            if ( namesNotFound.size()==1 ) {
                throw new IllegalArgumentException("Parameter name not found: "+namesNotFound.get(0) );
            } else if ( namesNotFound.size()>1 ) {
                throw new IllegalArgumentException("Parameter names not found: "+join(",",namesNotFound) );
            }
            if ( subsetPds.length==2 && subsetPds[1].size.length>0 ) {
                // Oooh, it's a spectrogram.  Maybe it has time-varying DEPEND_1.
                String dependName= null;
                String[] dependNames= null;
                if ( subsetPds[1].dependName!=null ) {
                    dependNames= new String[subsetPds[1].dependName.length];
                    for ( int i=0; i<subsetPds[1].dependName.length; i++ ) {
                        dependNames[i]= subsetPds[1].dependName[i];
                    }
                } else {
                    logger.warning("depend name missing!");
                }
                if ( dependNames!=null ) {
                    
                    List<ParamDescription> subsetPds1= new ArrayList<>();
                    for ( int i=0; i<subsetPds.length; i++ ) {
                        subsetPds1.add( subsetPds[i] );
                    }
                    
                    for ( int i=0; i<dependNames.length; i++ ) {
                        if ( dependNames[i]!=null ) {
                            int k= indexOfParameter( pds, dependNames[i] );
                            if ( k==-1 ) {
                                logger.log(Level.WARNING, "unable to find parameter: {0}", dependName);
                            } else {
                                subsetPds1.add(pds[k]);
                                pp= pp+","+dependNames[i];
                            }
                        }
                    }
                    subsetPds= subsetPds1.toArray( new ParamDescription[subsetPds1.size()] );
                }
            }
            
            pds= subsetPds;   

            nparam= pds.length;            

        }
        
        // 2043: trim the request to startDate/stopDate.  TODO: caching needs to consider this as well.
        DatumRange startStopDate= null;
        try {
            startStopDate= DatumRangeUtil.parseTimeRange( info.getString("startDate") + "/"+info.getString("stopDate") );
            if ( tr.intersects( startStopDate ) ) {
                tr= DatumRangeUtil.sloppyIntersection( tr, startStopDate );
            } else {
                if ( tr.max().lt(startStopDate.min() ) ) {
                    throw new NoDataInIntervalException("info startDate ("+info.getString("startDate")+") is after requested time range ("+tr+")" );
                } else {
                    throw new NoDataInIntervalException("info stopDate ("+info.getString("stopDate")+") is before requested time range ("+tr+")");
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
                ds= getDataSetViaBinary(totalFields, monitor, url, pds, 
                        tr, nparam, nfields, getParam( "cache", "" ) );
                break;
            case "json":
                ds= getDataSetViaJSON(totalFields, monitor, url, pds, tr, nparam, nfields);
                break;
            default:
                boolean useCache= useCache(getParam( "cache", "" ));
                if ( useCache ) { // round out to day boundaries, and load each day separately.
                    logger.finer("useCache, so make daily requests to form granules");
                    Datum minMidnight= TimeUtil.prevMidnight( tr.min() );
                    Datum maxMidnight= TimeUtil.nextMidnight( tr.max() );
                    tr= new DatumRange( minMidnight, maxMidnight );
                    tr= DatumRangeUtil.sloppyIntersection( tr, startStopDate );
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
                            DatumRange oneDaysRange= DatumRangeUtil.sloppyIntersection( currentDay, startStopDate );
                            if ( oneDaysRange.width().value()>0 ) {
                                URL url1= replaceTimeRangeURL(url,oneDaysRange,vers);
                                ds1 = getDataSetViaCsv(totalFields, mon1, url1, pds, 
                                        oneDaysRange, nparam, nfields, getParam( "cache", "" ) );
                                if ( ds1.length()>0 ) {
                                    dsall= Ops.append( dsall, ds1 );
                                }
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
                    ds= getDataSetViaCsv(totalFields, monitor, url, pds, tr, nparam, nfields, getParam( "cache", "" ) );
                }
                break;
        }
        
        if ( ds.length()==0 ) {
            monitor.finished();
            throw new NoDataInIntervalException("no records found");
        }
        
        ds = repackage(ds,pds,null);
        
        // look up colors for nominal data
        if ( ds.rank()==1 && pds.length>1 && ( pds[1].units instanceof EnumerationUnits ) && cadence!=null ) {
            JSONObject paramInfo= pds[1].parameter;
            if ( paramInfo.has( HapiUtil.KEY_X_COLOR_LOOKUP ) ) {
                JSONObject colorLookup= paramInfo.getJSONObject( HapiUtil.KEY_X_COLOR_LOOKUP );
                QDataSet dep0= (QDataSet) ds.property(QDataSet.DEPEND_0);
                QDataSet dep0Min= Ops.subtract( dep0, cadence.divide(2) );
                QDataSet dep0Max= Ops.add( dep0, cadence.divide(2) );
                IDataSet colors= IDataSet.createRank1(ds.length());
                Iterator iter= colorLookup.keys();
                Map<Pattern,Color> pelookUp= new HashMap<>();
                while ( iter.hasNext() ) {
                    String k= (String)iter.next();
                    try {
                        Pattern p= Pattern.compile(k);
                        pelookUp.put( p, ColorUtil.decodeColor(colorLookup.getString(k)) );
                    } catch ( PatternSyntaxException e ) {
                        logger.log( Level.WARNING, e.getMessage(), e );
                    }
                }
                EnumerationUnits eu= (EnumerationUnits)pds[1].units;
                for ( int i=0; i<ds.length(); i++ ) {
                    Datum d= eu.createDatum(ds.slice(i).svalue());
                    Color c= lookupColor( pelookUp, d );
                    colors.putValue( i, c.getRGB() );
                }
                ds= Ops.bundle( dep0Min, dep0Max, colors, ds );
            }
        }
        
        Units u= (Units) ds.property(QDataSet.UNITS);
        if ( u!=null && u.toString().trim().length()>0 ) {
            String l= (String) ds.property(QDataSet.LABEL);
            if ( l==null ) {
                ds= Ops.putProperty( ds, QDataSet.LABEL, "%{UNITS}" );
            } else {
                ds= Ops.putProperty( ds, QDataSet.LABEL, l.trim() + " (%{UNITS})" );
            }
        }
        
        // install a cacheTag.  The following code assumes depend_0 is mutable.
        QDataSet xds= (QDataSet) ds.property(QDataSet.DEPEND_0);
        if ( xds==null && ( UnitsUtil.isTimeLocation( SemanticOps.getUnits(ds) ) ) ) {
            xds= ds;
        }
        
        if ( timeStampLocation.equalsIgnoreCase("BEGIN") || timeStampLocation.equalsIgnoreCase("END" ) ) {
            if ( cadence==null ) {
                cadence= DataSetUtil.asDatum( DataSetUtil.guessCadenceNew( xds, null ) );
            }
            if ( cadence!=null ) {
                if ( timeStampLocation.equalsIgnoreCase("BEGIN") ) {
                    xds= Ops.add( xds, cadence.divide(2) );
                } else if ( timeStampLocation.equalsIgnoreCase("END") ) {
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
    
    private static boolean useCache( String useCacheUriParam ) {
        boolean useCache= HapiServer.useCache();
        String cacheParam= useCacheUriParam;
        if ( cacheParam.equals("F") ) {
            useCache= false;
        }
        return useCache;
    }
    
    private static AbstractLineReader getCsvReader( Connection hapiConnect ) throws IOException {
        InputStream ins1;
        ins1= hapiConnect.getInputStream();

        InputStreamReader isread= new InputStreamReader( ins1, HapiServer.UTF8 );
        AbstractLineReader result= new SingleFileBufferedReader( new BufferedReader(isread) );
        return result;
    }
    
    /**
     * read the interval using CSV.
     * @param totalFields
     * @param monitor
     * @param url
     * @param pds
     * @param tr
     * @param nparam
     * @param nfields
     * @param useCacheUriParam
     * @return
     * @throws IllegalArgumentException
     * @throws Exception
     * @throws IOException 
     */
    public static QDataSet getDataSetViaCsv(int totalFields, ProgressMonitor monitor, 
            URL url, ParamDescription[] pds, DatumRange tr,
            int nparam, int[] nfields, String useCacheUriParam ) throws IllegalArgumentException, Exception, IOException {
        
        DataSetBuilder builder= new DataSetBuilder(2,100,totalFields);
        monitor.setProgressMessage("reading data");
        monitor.setTaskProgress(20);
        long t0= System.currentTimeMillis() - 100; // -100 so it updates after receiving first record.
                        
        Connection connect= getConnection(url);
                
        logger.log(Level.FINER, "parse {0}", url);
                
        int linenumber=0;

        Map<String,Integer> warnings= new LinkedHashMap<>();
        
        try ( AbstractLineReader in = getCsvReader(connect) ) {

            String line= in.readLine();
            
            if ( line!=null && line.startsWith("{") ) {
                logger.log(Level.WARNING, "data response starts with \"{\", not data!");
                StringBuilder sb= new StringBuilder();
                while ( line!=null ) {
                    sb.append(line);
                    line= in.readLine();  // TODO: risk of reading entire data response.
                }
                String jsonResponse= sb.toString();
                JSONObject jo= new JSONObject(jsonResponse);
                if ( !jo.has("HAPI") ) throw new IllegalArgumentException("Expected HAPI version in JSON response");
                String vers= jo.getString("HAPI");
                if ( !( vers.startsWith("2") || vers.startsWith("1") ) ) {
                    String msg= "Only version 1 and 2 servers can have JSON response where CSV was expected";
                    if ( jsonResponse.length()<400 ) {
                        msg= msg + ": "+jsonResponse;
                    }
                    throw new IllegalArgumentException(msg);
                }
                if ( !jo.has("status") ) throw new IllegalArgumentException("Expected status in JSON response");
                JSONObject status= jo.getJSONObject("status");
                if ( status.getInt("code")==1201 ) {
                    throw new NoDataInIntervalException("server responds: "+status.getString("message") );
                } else {
                    throw new IllegalArgumentException("unsupported server response "+status.getInt("code")+": "+status.getString("message"));
                }
            }
            
            if ( line!=null && line.length()>0 && !Character.isDigit( line.charAt(0) ) ) {
                logger.log(Level.WARNING, "expected first character to be a digit (first of ISO8601 time), but got \"{0}\"", line);
            }
            
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
                        if ( line.trim().startsWith("{") ) {
                            throw new IllegalArgumentException( String.format( "expected %d fields, got \"{\" at line %d", new Object[]{totalFields, linenumber} ) );                            
                        } else {
                            throw new IllegalArgumentException( String.format( "expected %d fields, got %d at line %d", new Object[]{totalFields, ss.length,linenumber} ) );
                        }
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
                    if ( !warnings.containsKey( WARNING_TIME_MALFORMED ) ) {
                        logger.log(Level.INFO, "malformed time: {0}", ss[ifield]);
                        warnings.put( WARNING_TIME_MALFORMED, 1 );
                    } else {
                        warnings.put( WARNING_TIME_MALFORMED, warnings.get( WARNING_TIME_MALFORMED ) + 1 );
                    }
                    line= in.readLine();
                    continue;
                }
                                        
                builder.putValue( -1, ifield, xx );
                ifield++;
                for ( int i=1; i<nparam; i++ ) {  // nparam is number of parameters, which may have multiple fields.
                    for ( int j=0; j<nfields[i]; j++ ) {
                        try {
                            String s= ss[ifield];
                            if ( pds[i].units instanceof EnumerationUnits ) {
                                builder.putValue( -1, ifield, ((EnumerationUnits)pds[i].units).createDatum(s) );
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
                        
        } catch ( IOException e ) {
            logger.log( Level.WARNING, e.getMessage(), e );
            monitor.finished();
            if ( connect!=null ) {
                logger.log(Level.WARNING, "IOException when trying to read {0}", connect.getURL());
                throw new IOException( connect.getURL() + " results in\n"+String.valueOf(connect.getResponseCode())+": "+connect.getResponseMessage() );
            } else {
                throw e;
            }
            
        } catch ( Exception e ) {
            logger.log( Level.WARNING, e.getMessage(), e );
            monitor.finished();
            throw e;
        } finally {
            if ( connect!=null ) connect.disconnect();
        }
        
        if ( !warnings.isEmpty() ) {
            logger.warning("Warnings encountered:");
            for ( Entry<String,Integer> e: warnings.entrySet() ) {
                logger.log(Level.WARNING, " {0} ({1} times)", new Object[]{e.getKey(), e.getValue()});
            }
        }
        
        logger.log(Level.FINER, "done parsing {0}", url);
                
        monitor.setTaskProgress(95);
        QDataSet ds= builder.getDataSet();
        return ds;
    }

    private static TransferType getTimeTransferType( ParamDescription pdsi ) {
        final Units u= pdsi.units;
        final int length= pdsi.length;
        final byte[] bytes= new byte[length];

        return new TransferType() {
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
    }
    
    /**
     * read the interval using binary.
     * @param totalFields
     * @param monitor
     * @param url
     * @param pds
     * @param tr
     * @param nparam
     * @param nfields
     * @param useCacheUriParam
     * @return
     * @throws IllegalArgumentException
     * @throws Exception
     * @throws IOException 
     */
    public static QDataSet getDataSetViaBinary(int totalFields, ProgressMonitor monitor, URL url, 
            ParamDescription[] pds, DatumRange tr, 
            int nparam, int[] nfields, String useCacheUriParam ) throws IllegalArgumentException, Exception, IOException {

        DataSetBuilder builder = new DataSetBuilder(2, 100, totalFields);
        int icol=0;
        for ( int i=0; i<pds.length; i++ ) {
            ParamDescription pds1= pds[i];
            for ( int j=0; j<pds1.nFields; j++ ) {
                builder.setUnits( icol, pds1.units );
                icol++;
            }
        }
        monitor.setProgressMessage("reading data");
        monitor.setTaskProgress(20);
        long t0 = System.currentTimeMillis() - 100; // -100 so it updates after receiving first record.
                                        
        Connection httpConnect = getConnection(url);
        
        //Check to see what time ranges are from entire days, then only call writeToCachedData for these intervals. 
        Datum midnight= TimeUtil.prevMidnight( tr.min() );
        DatumRange currentDay= new DatumRange( midnight, TimeUtil.next( TimeUtil.DAY, midnight) );
        
        int recordLengthBytes = 0;
        TransferType[] tts = new TransferType[pds.length];

        for (int i = 0; i < pds.length; i++) {
            if (pds[i].type.startsWith("time")) {
                recordLengthBytes += Integer.parseInt(pds[i].type.substring(4));
                tts[i] = TransferType.getForName(pds[i].type, Collections.singletonMap(QDataSet.UNITS, (Object)pds[i].units));
            } else if (pds[i].type.startsWith("string")) {
                recordLengthBytes += pds[i].length;
                tts[i] = getTimeTransferType(pds[i]);
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
        
        int recordnumber=0;
        
        Map<String,Integer> warnings= new LinkedHashMap<>();
        
        try ( AbstractBinaryRecordReader in = new InputStreamBinaryRecordReader( httpConnect.getInputStream() ) ) {
            
            ByteBuffer buf = TransferType.allocate( recordLengthBytes,ByteOrder.LITTLE_ENDIAN );
            
            int bytesRead = in.readRecord(buf);
            
            if ( bytesRead>0 ) {
                if ( (char)buf.get(0)=='{' ) {
                    logger.log(Level.WARNING, "data response starts with \"{\", not data!");
                    
                    StringBuilder sb= new StringBuilder();
                    while ( bytesRead>-1 ) {
                        if ( buf.position()<buf.limit() ) {
                            while ( bytesRead>-1 ) {
                                bytesRead = in.readRecord(buf);
                            }
                        }
                        buf.flip();
                        sb.append(  new String( buf.array(), 0, buf.limit(), "UTF-8" ) );
                    }
                    
                    String jsonResponse= sb.toString();
                    JSONObject jo= new JSONObject(jsonResponse);
                    if ( !jo.has("HAPI") ) throw new IllegalArgumentException("Expected HAPI version in JSON response");
                    String vers= jo.getString("HAPI");
                    if ( !( vers.startsWith("2") || vers.startsWith("1") ) ) {
                        String msg= "Only version 1 and 2 servers can have JSON response where CSV was expected";
                        if ( jsonResponse.length()<400 ) {
                            msg= msg + ": "+jsonResponse;
                        }
                        throw new IllegalArgumentException(msg);
                    }
                    if ( !jo.has("status") ) throw new IllegalArgumentException("Expected status in JSON response");
                        JSONObject status= jo.getJSONObject("status");
                    if ( status.getInt("code")==1201 ) {
                        throw new NoDataInIntervalException("server responds: "+status.getString("message") );
                    } else {
                        throw new IllegalArgumentException("unsupported server response "+status.getInt("code")+": "+status.getString("message"));
                    }
                }
            }
            
            while (bytesRead != -1) {
                
                recordnumber++;
                logger.log(Level.FINER, "read record number {0}", recordnumber);
                
                buf.flip();
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
                try {
                    xx = pds[0].units.createDatum(result[0]);
                    if (System.currentTimeMillis() - t0 > 100) {
                        monitor.setProgressMessage("reading " + xx);
                        t0 = System.currentTimeMillis();
                        double d = DatumRangeUtil.normalize(tr, xx);
                        monitor.setTaskProgress(20 + (int) (75 * d));
                        if ( monitor.isCancelled() ) 
                            throw new CancelledOperationException("cancel was pressed");
                    }
                } catch ( RuntimeException ex ) {
                    if ( !warnings.containsKey( WARNING_TIME_MALFORMED ) ) {
                        logger.log(Level.INFO, "malformed time");
                        warnings.put( WARNING_TIME_MALFORMED, 1 );
                    } else {
                        warnings.put( WARNING_TIME_MALFORMED, warnings.get( WARNING_TIME_MALFORMED ) + 1 );
                    }
                    buf.flip();
                    bytesRead = in.readRecord(buf);
                    continue;
                }

                if ( !currentDay.contains(xx) ) {
                    if ( !warnings.containsKey( WARNING_TIME_ORDER ) ) {
                        logger.log(Level.INFO, "something's gone wrong, perhaps out-of-order timetags: {0}", xx);
                        warnings.put( WARNING_TIME_ORDER, 1 );
                    } else {
                        warnings.put( WARNING_TIME_ORDER, warnings.get( WARNING_TIME_ORDER ) + 1 );
                    }
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
                bytesRead = in.readRecord(buf);
                
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
            if ( httpConnect!=null ) httpConnect.disconnect();
        }
        
        monitor.setTaskProgress(95);
        QDataSet ds = builder.getDataSet();
        return ds;
    }
    
    /**
     * see if all traffic can come through here, so we can optionally cache results.
     * @param url
     * @return
     * @throws IOException 
     */
    private static Connection getConnection( final URL url ) throws IOException {
        
        boolean useCache= false;
        
        if ( useCache ) {
            throw new IllegalArgumentException("not yet supported");
        } else {
            return new HttpConnection(url);   
        }
        
    }

    /**
     * read data embedded within a JSON response.  This current reads in the entire JSON document,
     * but the final version should use a streaming JSON library.
     * @param monitor
     * @return the dataset.
     * @throws Exception 
     */
    private static QDataSet getDataSetViaJSON( int totalFields, ProgressMonitor monitor, URL url, ParamDescription[] pds, DatumRange tr, int nparam, int[] nfields) throws IllegalArgumentException, Exception, IOException {
        
        monitor.started();
        monitor.setProgressMessage("server is preparing data");
        
        long t0= System.currentTimeMillis() - 100; // -100 so it updates after receiving first record.
       
        int lineNum=0;
        
        StringBuilder builder= new StringBuilder();
        logger.log(Level.FINE, "getDocument {0}", url.toString());
        Connection connect= getConnection(url);
        try ( BufferedReader in= new BufferedReader( 
                new InputStreamReader( connect.getInputStream(), HapiServer.UTF8 ) ) ) {
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
            FileSystemUtil.copyStream( connect.getErrorStream(), baos, new NullProgressMonitor() );
            String s= baos.toString("UTF-8");
            if ( s.contains("No data available") ) {
                logger.log(Level.FINE, "No data available, server responded with {0}: {1}", new Object[]{connect.getResponseCode(), connect.getResponseMessage()});
                throw new NoDataInIntervalException("No data available");
            } else {
                if ( s.length()<256 ) {
                    throw new IOException( ex.getMessage() + ": "+s );
                } else {
                    throw ex;
                }
            }
        }
        connect.disconnect();  // See unix tcptrack which shows there are many connections to the server. jbf@gardenhousepi:~ $ sudo tcptrack -i eth0

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
    private static String[] lineSplit( String line ) {
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
            
    protected static Datum parseTime( String stopDate ) throws ParseException {
        try {
            return Units.ms1970.parse(stopDate);
        } catch ( ParseException ex ) {
            switch (stopDate) {
                case "lastday":
                    stopDate= TimeUtil.prevMidnight( TimeUtil.now() ).toString();
                    logger.warning("\"lastday\" is not a valid time, and this should be fixed.");
                    break;
                case "lasthour":
                    stopDate= TimeUtil.prev( TimeUtil.HOUR, TimeUtil.now() ).toString();
                    logger.warning("\"lasthour\" is not a valid time, and this should be fixed.");
                    break;
                case "now":
                    stopDate= TimeUtil.now().toString();
                    logger.warning("\"now\" is not a valid time, and this should be fixed.");
                    break;
                default:
                    throw ex;
            }
            return TimeUtil.create(stopDate); 
        }
    }
    
    public static ParamDescription[] getParameterDescriptions(JSONObject doc) throws IllegalArgumentException, ParseException, JSONException {
        JSONArray parameters= doc.getJSONArray("parameters");
        int nparameters= parameters.length();
        
        long modificationDate= 0L;
        if ( doc.has("modificationDate") ) {
            String s= doc.getString("modificationDate");
            try {
                Datum d= parseTime(s);
                modificationDate= (long)( d.doubleValue(Units.ms1970) );
            } catch ( ParseException ex ) {
                logger.log(Level.INFO, "Unable to use modificationDate, found: \"{0}\"", s);
            }
        }
        
        ParamDescription[] pds= new ParamDescription[nparameters];
        for ( int i=0; i<nparameters; i++ ) {
            
            final JSONObject jsonObjecti = parameters.getJSONObject(i);
            
            String name= jsonObjecti.getString("name"); // the name of one of the parameters.
            logger.log(Level.FINER, "unpacking {0}", name);
            if ( name==null ) {
                name="name"+i;
                logger.log(Level.WARNING, "name not found for {0}th parameter", i );
            }
            
            pds[i]= new ParamDescription( name );
            pds[i].modifiedDateMillis= modificationDate;
            
            String type;
            if ( jsonObjecti.has(HapiUtil.KEY_TYPE) ) {
                type= jsonObjecti.getString(HapiUtil.KEY_TYPE);
                if ( type==null ) type="";
            } else {
                type= "";
            }
            if ( type.equals("") ) {
                logger.log(Level.FINE, "type is not defined: {0}", name);
            }
            
            pds[i].parameter= jsonObjecti;
            
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
                if ( jsonObjecti.has(HapiUtil.KEY_UNITS) ) {
                    Object ou= jsonObjecti.get(HapiUtil.KEY_UNITS);
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
                
                if ( jsonObjecti.has(HapiUtil.KEY_FILL) ) {
                    String sfill= jsonObjecti.getString(HapiUtil.KEY_FILL);
                    if ( sfill!=null && !sfill.equals("null") ) {
                        if ( type.equals("string") ) {
                            pds[i].fillValue= ((EnumerationUnits)pds[i].units).createDatum( sfill ).doubleValue( pds[i].units );
                            pds[i].hasFill= true;
                        } else {
                            try {
                                pds[i].fillValue= pds[i].units.parse( sfill ).doubleValue( pds[i].units );
                                pds[i].hasFill= true;
                            } catch ( ParseException ex ) {
                                logger.log(Level.WARNING, "unable to use fill value: {0}", sfill);
                            }
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

                if ( jsonObjecti.has( HapiUtil.KEY_LABEL ) ) { // The verifier code incorrectly marks "label" as not allowed, but I believe it is.
                    Object olabel= jsonObjecti.get( HapiUtil.KEY_LABEL );
                    if ( olabel instanceof String ) {
                        pds[i].label= (String)olabel;
                        pds[i].labels= null;
                    } else if ( olabel instanceof JSONArray ) {
                        JSONArray array= (JSONArray)olabel;
                        pds[i].labels= new String[array.length()];
                        for ( int j=0; j<array.length(); j++ ) {
                            pds[i].labels[j]= array.getString(j);
                        }
                    }
                    if ( pds[i].label==null ) pds[i].label= name;
                } else {
                    pds[i].label= name;
                }
                
                if ( jsonObjecti.has( HapiUtil.KEY_LENGTH ) ) {
                    pds[i].length= jsonObjecti.getInt( HapiUtil.KEY_LENGTH );
                }

                if ( jsonObjecti.has( HapiUtil.KEY_SIZE ) ) {
                    Object o= jsonObjecti.get( HapiUtil.KEY_SIZE );
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
                    if ( jsonObjecti.has( HapiUtil.KEY_BINS ) ) {
                        o= jsonObjecti.get( HapiUtil.KEY_BINS );
                        if ( o instanceof JSONArray ) {
                            JSONArray ja= (JSONArray)o;
                            pds[i].depend= new QDataSet[ja.length()];
                            pds[i].dependName= new String[ja.length()];
                            for ( int j=0; j<ja.length(); j++ ) {
                                JSONObject bins= ja.getJSONObject(j);
                                if ( bins.has( HapiUtil.KEY_CENTERS ) ) {
                                    // rfe696: support time-varying DEPEND_1
                                    Object o1= bins.get( HapiUtil.KEY_CENTERS );
                                    if ( o1 instanceof String ) {
                                        pds[i].dependName[j]= (String)o1;
                                    } else {
                                        QDataSet dep= getJSONBins(ja.getJSONObject(j));
                                        pds[i].depend[j]= dep;
                                    }
                                } else if ( bins.has( HapiUtil.KEY_RANGES ) ) {
                                    // rfe696: support time-varying DEPEND_1
                                    Object o1= bins.get( HapiUtil.KEY_RANGES );
                                    if ( o1 instanceof String ) {
                                        pds[i].dependName[j]= (String)o1;
                                    } else {
                                        QDataSet dep= getJSONBins(ja.getJSONObject(j));
                                        pds[i].depend[j]= dep;
                                    }
                                    pds[i].renderType= QDataSet.VALUE_RENDER_TYPE_NNSPECTROGRAM;                                    
                                } else if ( bins.has( HapiUtil.KEY_PARAMETER ) ) {  // deprecated, see binsParameter below.  TODO: revisit this.
                                    logger.info("parameter found within bins, which is deprecated.");
                                    int n= pds[i].nFields;
                                    pds[i].depend[j]= Ops.findgen(n);
                                    pds[i].dependName[j]= bins.getString( HapiUtil.KEY_PARAMETER );
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
                            if ( bins.has( HapiUtil.KEY_PARAMETER ) ) { // this will be implemented later.
                                int n= DataSetUtil.product(pds[i].size);
                                pds[i].depend[0]= Ops.findgen(n);
                                pds[i].dependName[0]= bins.getString( HapiUtil.KEY_PARAMETER );
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
     * copy label and units from the ParamDescription to the QDataSet.
     * @param mpds the dataset
     * @param pds1 the parameter description
     * @return the data, possibly a copy, with the metadata.
     */
    private MutablePropertyDataSet copyProperties( QDataSet mpds, ParamDescription pds1 ) {
        MutablePropertyDataSet ds= Ops.putProperty( mpds, QDataSet.NAME, Ops.safeName(pds1.name) );
        ds= Ops.putProperty( ds, QDataSet.LABEL, pds1.label );
        ds= Ops.putProperty( ds, QDataSet.TITLE, pds1.description );
        ds= Ops.putProperty( ds, QDataSet.UNITS, pds1.units );
        if ( pds1.hasFill ) {
            ds= Ops.putProperty( ds, QDataSet.FILL_VALUE, pds1.fillValue );
        }
        if ( pds1.labels!=null ) {
            MutablePropertyDataSet bds= (MutablePropertyDataSet) ds.property(QDataSet.BUNDLE_1);
            if ( bds==null ) {
                ds= Ops.putProperty( ds, QDataSet.DEPEND_1, Ops.labelsDataset( pds1.labels ) );
            } else {
                for ( int i=0; i<pds1.labels.length; i++ ) {
                    bds.putProperty( QDataSet.LABEL, i, pds1.labels[i] );
                    bds.putProperty( QDataSet.NAME, i, Ops.safeName( pds1.labels[i] ) );
                }
                ds= Ops.putProperty( ds, QDataSet.BUNDLE_1, bds );
            }
            
        }
        return ds;
    }

    /**
     * Reform bundle into typical QDataSet schemes.  For example, a rank 2 bundle 
     * ds[;T,N] would be reformed into rank 1 N[T].
     * @param ds the bundle dataset
     * @param pds metadata for each column.
     * @param sort if non-null, resort the data with these indices.
     * @return 
     */
    private QDataSet repackage(QDataSet ds, ParamDescription[] pds, int[] sort ) {
        int nparameters= ds.length(0);
        
        boolean combineRank2Depend1= pds.length==3 && pds[1].dependName!=null;
        
        if ( ds.rank()==2 ) {
            QDataSet bds= (QDataSet)ds.property(QDataSet.BUNDLE_1);
            if ( bds!=null && bds.length()>1 ) {
                Units u1= (Units)bds.property(QDataSet.UNITS,0);
                Units u2= (Units)bds.property(QDataSet.UNITS,1);
                if ( u1!=null && u2!=null && UnitsUtil.isTimeLocation( u1 ) && UnitsUtil.isTimeLocation( u2 ) ) {
                    QDataSet start= Ops.slice1( ds,0 );
                    QDataSet stop= Ops.slice1( ds,1 );
                
                    // It's an events dataset, but we better check that all stops are greater than starts!
                    if ( Ops.reduceMax( Ops.lt( stop, start ),0 ).value()==0 ) {
                        return Ops.createEvents(ds);
                    }
                }
            }
        }
        
        QDataSet depend0= Ops.slice1( ds,0 ); //TODO: this will be unnecessary after debugging.
        if ( ds.length(0)==2 ) {
            ds= Ops.copy( Ops.slice1( ds, 1 ) );
            ds= Ops.putProperty( ds, QDataSet.DEPEND_0, depend0 );
            ds= copyProperties( ds, pds[1] );
        } else if ( pds.length==2 ) {
            ds= Ops.copy( Ops.trim1( ds, 1, ds.length(0) ) );
            if ( pds[1].size.length>1 ) {
                ds= Ops.reform( ds, ds.length(), pds[1].size );
            }
            ds= Ops.putProperty( ds, QDataSet.DEPEND_0, depend0 );
            ds= copyProperties( ds, pds[1] );
            if ( pds[1].depend!=null ) {
                for ( int j=0; j<pds[1].size.length; j++ ) {
                    ds= Ops.putProperty( ds, "DEPEND_"+(j+1), pds[1].depend[j] );
                }
            }
            if ( pds.length==2 && QDataSet.VALUE_RENDER_TYPE_NNSPECTROGRAM.equals( pds[1].renderType ) ) {
                ds= Ops.putProperty( ds, QDataSet.RENDER_TYPE, pds[1].renderType );
            }
        } else if ( pds.length==1 ) {
            ds= Ops.link( depend0, depend0 );
            ds= Ops.putProperty( ds, QDataSet.RENDER_TYPE, QDataSet.VALUE_RENDER_TYPE_EVENTS_BAR );
            return ds;
            
        } else if ( combineRank2Depend1 ) {
            MutablePropertyDataSet theScienceDs= Ops.maybeCopy( Ops.trim1( ds, 1, 1+pds[1].nFields ) );
            if ( pds[1].size.length>1 ) {
                theScienceDs= Ops.maybeCopy( Ops.reform( theScienceDs, theScienceDs.length(), pds[1].size ) );
            }
            
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
                                // wait until code below
                            } else {
                                sdsb.putProperty( "DEPEND_"+(j+1), startIndex, pds[i].depend[j]);
                                if ( i==1 ) {
                                    theScienceDs.putProperty( "DEPEND_"+(j+1), pds[i].depend[j]) ;
                                }
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
            start= start+length1;
            theScienceDs.putProperty( QDataSet.DEPEND_0, depend0 );
            //theScienceDs.putProperty( QDataSet.BUNDLE_1, sdsbs[1].getDataSet() );
            theScienceDs= copyProperties( theScienceDs, pds[1] );
            
            for ( int i=1; i<pds.length; i++ ) { // only works for rank2!!!
                if ( pds[i].dependName!=null ) {
                    for ( int j=0; j<pds[i].dependName.length; j++ ) {
                        String dependName= pds[i].dependName[j];
                        if (dependName != null) {
                            int k;
                            for (k=1; k<pds.length; k++) {
                                if (pds[k].name.equals(dependName)) {
                                    break;
                                }
                            }
                            if ( k<pds.length ) {
                                MutablePropertyDataSet depds= Ops.copy( Ops.trim1( ds, start, start+length1 ) );
                                depds.putProperty( QDataSet.DEPEND_0, depend0 );
                                depds.putProperty( QDataSet.BUNDLE_1, sdsbs[k].getDataSet() );    
                                depds= copyProperties( depds, pds[k] );
                                start= start+length1;
                                if ( pds[k].size.length>1 ) {
                                    theScienceDs.putProperty( "DEPEND_"+(j+1), Ops.reform( depds, depds.length(), pds[k].size ) );
                                } else {
                                    theScienceDs.putProperty( "DEPEND_"+(j+1), depds );
                                }
                            }
                        }
                    }
                }
            }
            
            ds= theScienceDs;            
            
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
