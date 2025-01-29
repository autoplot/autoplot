
package org.autoplot.hapi;

import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.datum.DatumRange;
import org.das2.datum.TimeParser;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.datasource.DataSetURI;
import org.das2.datum.Datum;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.HttpUtil;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.util.filesystem.FileSystem;

/**
 * Utility methods for interacting with HAPI servers.  
 * @author jbf
 */
public class HapiServer {
    
    protected final static Logger logger= Logger.getLogger("apdss.hapi");
    
    /**
     * this logger is for opening connections to remote sites.
     */
    protected static final Logger loggerUrl= org.das2.util.LoggerManager.getLogger( "das2.url" );
    
    /**
     * all transactions must be done in UTF-8
     */
    public static final Charset UTF8= Charset.forName("UTF-8");
    
    /**
     * get known servers.  
     * @return known servers
     */
    public static List<String> getKnownServers() {
        ArrayList<String> result= new ArrayList<>();
        try {
            URL url= new URL("https://raw.githubusercontent.com/hapi-server/servers/master/server_list.txt");
            try {
                String s= readFromURL(url,"");
                String[] ss= s.split("\n");
                result.addAll(Arrays.asList(ss));
            } catch ( IOException ex ) {
                url= new URL("https://raw.githubusercontent.com/hapi-server/servers/master/all.txt");
                String s= readFromURL(url,"");
                String[] ss= s.split("\n");
                result.addAll(Arrays.asList(ss));
            }
            if ( "true".equals(System.getProperty("hapiDeveloper","false")) ) {
                result.add("http://tsds.org/get/IMAGE/PT1M/hapi");
                result.add("https://cdaweb.gsfc.nasa.gov/registry/hdp/hapi");
                result.add("http://jfaden.net/HapiServerDemo/hapi");
            }
        } catch (IOException  ex) {
            Logger.getLogger(HapiServer.class.getName()).log(Level.SEVERE, null, ex);
        }
        result.remove("http://datashop.elasticbeanstalk.com/hapi");
        result.add("http://datashop.elasticbeanstalk.com/hapi");
        
        ArrayList<String> uniq= new ArrayList<>();
        for ( String s: result ) {
            if ( !uniq.contains(s) ) uniq.add(s);
        }

        return uniq;
    }
    
    /**
     * get known servers
     * @return known servers
     */
    public static String[] getKnownServersArray() {
        List<String> result= getKnownServers();
        return result.toArray( new String[result.size()] );
    }
     
    /**
     * add the default known servers, plus the ones we know about.
     * @return list of servers
     */
    public static String[] listHapiServersArray() {
        List<String> result= listHapiServers();
        return result.toArray( new String[result.size()] );        
    }
    
    /**
     * add the default known servers, plus the ones we know about.  
     * The zeroth server will be the last server used.
     * This should not be called from the event thread.
     * 
     * @return list of server URLs.
     */
    public static List<String> listHapiServers() {
        if ( EventQueue.isDispatchThread() ) {
            logger.warning("HAPI network call on event thread");
        }        
        List<String> d2ss1= new ArrayList( );
        d2ss1.addAll( getKnownServers() );

        File home = new File(AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA));
        File book = new File(home, "bookmarks");
        File hist = new File(book, "history.txt");
        long t0= System.currentTimeMillis();
        logger.log( Level.FINE, "reading recent datasources from {0}", hist.toString());

        if ( hist.exists() ) {
            BufferedReader r=null;
            try {
                String seek="hapi:";
                int ttaglen= 25;
                r = new BufferedReader( new FileReader(hist) ); //sf2295 what is the encoding of the history file?
                String s = r.readLine();
                LinkedHashSet dss = new LinkedHashSet();

                while (s != null) {
                    if ( s.length()>ttaglen+15 && s.substring(ttaglen+4,ttaglen+9).equalsIgnoreCase(seek)) {
                        int i= s.indexOf("?");
                        if ( i==-1 ) i= s.length();
                        String key= s.substring(ttaglen+4+seek.length(),i);
                        if ( dss.contains(key) ) dss.remove( key ); // move to the end
                        dss.add( key );
                    }
                    s = r.readLine();
                }

                d2ss1.removeAll(dss);  // remove whatever we have already
                List<String> d2ssDiscoveryList= new ArrayList(dss);
                Collections.reverse( d2ssDiscoveryList );
                d2ssDiscoveryList.addAll(d2ss1);
                d2ss1= d2ssDiscoveryList; // put the most recently used ones at the front of the list
                
                logger.log( Level.FINE, "read extra hapi servers in {0} millis\n", (System.currentTimeMillis()-t0) );
            } catch ( IOException ex ) {
                
            } finally {
                try {
                    if ( r!=null ) r.close();
                } catch (IOException ex) {
                    logger.log( Level.SEVERE, ex.getMessage(), ex );
                }
            }
        } else {
            logger.log( Level.FINE, "no history file found: {0}", hist );
        }
                
        return d2ss1;

    }
    
    /**
     * return the list of datasets available at the server.
     * This should not be called from the event thread.
     * @param server the root of the server, which should should contain "catalog"
     * @return list of dataset ids
     * @throws java.io.IOException
     * @throws org.json.JSONException
     */
    public static List<String> getCatalogIds( URL server ) throws IOException, JSONException {
        if ( EventQueue.isDispatchThread() ) {
            logger.warning("HAPI network call on event thread");
        }        
        URL url;
        url= HapiServer.createURL( server, "catalog" );
        String s= readFromURL(url, "json");
        JSONObject o= new JSONObject(s);
        JSONArray catalog= o.getJSONArray( HapiSpec.CATALOG );
        List<String> result= new ArrayList<>(catalog.length());
        for ( int i=0; i<catalog.length(); i++ ) {
            result.add(i,catalog.getJSONObject(i).getString(HapiSpec.URL_PARAM_ID) );
        }
        return result;
    }
     
    /**
     * return the list of datasets available at the server.  
     * This should not be called from the event thread.
     * @param server the root of the server, which should should contain "catalog"
     * @return list of catalog entries, which have "id" and "title" tags.
     * @throws java.io.IOException
     * @throws org.json.JSONException
     */
    public static JSONArray getCatalog( URL server ) throws IOException, JSONException {
        if ( EventQueue.isDispatchThread() ) {
            logger.warning("HAPI network call on event thread");
        }        
        URL url;
        url= HapiServer.createURL( server, HapiSpec.CATALOG_URL  );
        String s= readFromURL(url, "json");
        JSONObject o= new JSONObject(s);
        JSONArray catalog= o.getJSONArray( HapiSpec.CATALOG );
        return catalog;
    }
    
    /**
     * return the URL for getting info.
     * @param server
     * @param id
     * @return 
     */
    public static URL getInfoURL( URL server, String id ) {
        URL url= HapiServer.createURL(server, HapiSpec.INFO_URL, Collections.singletonMap(HapiSpec.URL_PARAM_ID, id) );
        return url;
    }
    
    private static Map<String,String> versions= new HashMap<>();
    private static Map<String,Long> versionFresh= new HashMap<>();
    
    public static String getHapiServerVersion( URL server ) throws JSONException, IOException {
        String sserver= server.toString();
        Long fresh= versionFresh.get(sserver);
        if ( fresh==null || ( fresh < ( System.currentTimeMillis() - 600000 ) ) ) {
            JSONObject capabilities= getCapabilities( server );
            String version = capabilities.getString("HAPI");
            versions.put( sserver, version );
            versionFresh.put( sserver, System.currentTimeMillis() );
            return version;
        } else {
            return versions.get( sserver );
        }
    }
    
    /**
     * return the URL for data requests.
     * @param server
     * @param id string like "data4" or "spase://..."
     * @param tr the time range
     * @param parameters zero-length, or a comma-delineated list of parameters.
     * @return the request, with the ID and parameters URL encoded.
     */
    public static URL getDataURL( URL server, String id, DatumRange tr, String parameters ) {
        TimeParser tp= TimeParser.create("$Y-$m-$dT$H:$M:$S.$(subsec;places=3)Z");
        HashMap<String,String> map= new LinkedHashMap();
        map.put(HapiSpec.URL_PARAM_ID, id );
        String version;
        try {
            version= getHapiServerVersion(server);
        } catch (JSONException | IOException ex) {
            version= "2.0";
        }
        if ( version.startsWith("2.") || version.startsWith("1.")) {
            map.put(HapiSpec.URL_PARAM_TIMEMIN, tp.format(tr.min()) );
            map.put(HapiSpec.URL_PARAM_TIMEMAX, tp.format(tr.max()) );
        } else {
            map.put(HapiSpec.URL_PARAM_START, tp.format(tr.min()) );
            map.put(HapiSpec.URL_PARAM_STOP, tp.format(tr.max()) );            
        }
        if ( parameters.length()>0 ) {
            map.put(HapiSpec.URL_PARAM_PARAMETERS, parameters );
        }
        URL serverUrl= createURL(server, HapiSpec.DATA_URL, map );
        return serverUrl;
    }
        
    /**
     * return the URL by appending the text to the end of the server URL.  This
     * avoids extra slashes, etc.
     * @param server
     * @param append
     * @return 
     */
    public static URL createURL( URL server, String append ) {
        return createURL( server, append, null );
    }
    
    /**
     * make sure spaces are encoded.
     * @param id
     * @return 
     */
    public static String urlEncode( String id ) {
        Pattern p= Pattern.compile("[a-zA-Z0-9_:\\-\\+,/\\.]+");
        if ( p.matcher(id).matches() ) {
            return id;
        } else {
            try {
                return URLEncoder.encode( id, "UTF-8" );
            } catch (UnsupportedEncodingException ex) {
                throw new IllegalArgumentException(ex);
            }
        }
    }

    public static JSONArray getParameters(URL server, String id) throws IOException, JSONException {
        JSONObject o= getInfo( server, id );
        JSONArray catalog= o.getJSONArray(HapiSpec.PARAMETERS);
        return catalog;
    }
    
    /**
     * return the info as a JSONObject.
     * This should not be called from the event thread.
     * @param server HAPI server.
     * @param id the parameter id.
     * @return JSONObject containing information.
     * @throws IOException
     * @throws JSONException 
     */
    public static JSONObject getInfo( URL server, String id) throws IOException, JSONException {
        if ( EventQueue.isDispatchThread() ) {
            logger.warning("HAPI network call on event thread");
        }
        URL url;
        Map<String,String> params= new HashMap<>();
        params.put( HapiSpec.URL_PARAM_ID, id );
        
        //// https://sourceforge.net/p/autoplot/feature-requests/696/
        //if ( server.toString().contains("http://hapi-server.org/servers/TestDataRef/hapi") ) {
        //    params.put( "resolve_references","false");
        //}
        
        url= HapiServer.createURL(server, HapiSpec.INFO_URL, params );
        logger.log(Level.FINE, "getInfo {0}", url.toString());
        String s= readFromURL(url, "json");
        JSONObject o= new JSONObject(s);
        return o;
    }
    
    /**
     * return the server capabilities document.  
     * This should not be called from the event thread.
     * @param server HAPI server.
     * @return JSONObject containing capabilities.
     * @throws IOException
     * @throws JSONException 
     */
    protected static JSONObject getCapabilities(URL server)  throws IOException, JSONException {
        if ( EventQueue.isDispatchThread() ) {
            logger.warning("HAPI network call on event thread");
        }
        URL url;
        url= HapiServer.createURL(server, HapiSpec.CAPABILITIES_URL);
        String s= readFromURL(url, "json" );
        JSONObject o= new JSONObject(s);
        return o;
    }

    /**
     * use cache of HAPI responses, to allow for use in offline mode.
     * @return 
     */
    protected static boolean useCache() {
        return ( "true".equals( System.getProperty("hapiServerCache","false") ) );
    }
    
    /**
     * allow cached files to be used for no more than 1 hour.
     * @return 
     */
    protected static long cacheAgeLimitMillis() {
        return 3600000;
    }
    
    /**
     * read the file into a string.  
     * @param f non-empty file
     * @return String containing file contents.
     * @throws IOException 
     */
    public static String readFromFile( File f ) throws IOException {
        StringBuilder builder= new StringBuilder();
        try ( BufferedReader in= new BufferedReader( new InputStreamReader( new FileInputStream(f), UTF8 ) ) ) {
            String line= in.readLine();
            while ( line!=null ) {
                builder.append(line);
                builder.append("\n");
                line= in.readLine();
            }
        }
        if ( builder.length()==0 ) {
            throw new IOException("file is empty:" + f );
        }
        String result=builder.toString();
        return result;
    }
    
    /**
     * read data from the URL.  
     * @param url the URL to read from
     * @param type the extension to use for the cache file (JSON).
     * @return non-empty string
     * @throws IOException 
     */
    public static String readFromURL( URL url, String type ) throws IOException {
        
        loggerUrl.log(Level.FINE, "GET {0}", new Object[] { url } );
        
        Connection urlc= Connection.openConnection(url);
        
        StringBuilder builder= new StringBuilder();
        try ( BufferedReader in= new BufferedReader( new InputStreamReader( urlc.getInputStream(), UTF8 ) ) ) {
            String line= in.readLine();
            while ( line!=null ) {
                builder.append(line);
                builder.append("\n");
                line= in.readLine();
            }
        } catch ( IOException ex ) {
            StringBuilder builder2= new StringBuilder();
            InputStream err= urlc.getErrorStream();
            if ( err==null ) {
                throw ex;
            }
            try ( BufferedReader in2= new BufferedReader( new InputStreamReader( err, UTF8 ) ) ) {
                String line= in2.readLine();
                while ( line!=null ) {
                    builder2.append(line);
                    builder2.append("\n");
                    line= in2.readLine();
                }
                String s2= builder2.toString().trim();
                if ( type.equals("json") && s2.length()>0 && s2.charAt(0)=='{' ) {
                    logger.warning("incorrect error code returned, content is JSON");
                    return s2;
                }
            } catch ( IOException ex2 ) {
                logger.log( Level.FINE, ex2.getMessage(), ex2 );
            }
            logger.log( Level.FINE, ex.getMessage(), ex );
            throw ex;
        }
        
        if ( builder.length()==0 ) {
            throw new IOException("empty response from "+url );
        }
        String result=builder.toString();
        
        return result;
    }
    
    /**
     * return the URL by appending the text to the end of the server URL.  This
     * avoids extra slashes, etc.
     * @param server the hapi server
     * @param append the folder to append.
     * @param singletonMap parameters to append.
     * @return the url.
     */
    public static URL createURL(URL server, String append, Map<String, String> singletonMap) {
        StringBuilder s= new StringBuilder( server.toString() );
        if ( append.startsWith("/") ) {
            append= append.substring(1);
        }
        if ( s.substring(s.length()-1).equals("/") ) {
            s= s.append( append );
        } else {
            s= s.append("/").append( append );
        }
        if ( singletonMap!=null && !singletonMap.isEmpty() ) {
            boolean firstArg= true;
            for ( Entry<String,String> entry: singletonMap.entrySet() ) {
                if ( entry.getValue()!=null ) {
                    if ( firstArg ) {
                        s.append("?");
                        firstArg=false;
                    } else {
                        s.append("&");
                    }
                    String svalue;
                    if ( entry.getKey().equals(HapiSpec.URL_PARAM_TIMEMIN) || entry.getKey().equals(HapiSpec.URL_PARAM_TIMEMAX) ) {
                        svalue= entry.getValue();  // the colons are needed on CDAWeb server.
                    } else {
                        svalue= urlEncode( entry.getValue() );
                    }
                    s.append(entry.getKey()).append("=").append( svalue );
                }
            }
        }
        try {
            return new URL(s.toString());
        } catch ( MalformedURLException ex ) {
            throw new IllegalArgumentException(ex);
        }
    }
    
    /**
     * [yr,mon,day,hour,min,sec,nanos]
     * @param array
     * @return approximate seconds
     */
    private static Datum cadenceArrayToDatum( int[] array ) {
        double seconds= array[6]/1e9;
        seconds+= array[5];
        seconds+= array[4]*60;
        seconds+= array[3]*3600;
        seconds+= array[2]*86400; //approx, just to get scale
        seconds+= array[1]*86400*30; //approx, just to get scale
        seconds+= array[0]*86400*365; // approx, just to get scale
        return Units.seconds.createDatum(seconds);
    }
    
    
    /**
     * return the range of available data. For example, Polar/Hydra data is available
     * from 1996-03-20 to 2008-04-15.  Note this supports old schemas.
     * @param info
     * @return the range of available data.
     */
    public static DatumRange getRange( JSONObject info ) {
        try {
            if ( info.has("firstDate") && info.has("lastDate") ) { // this is deprecated behavior
                String firstDate= info.getString("firstDate");
                String lastDate= info.getString("lastDate");
                if ( firstDate!=null && lastDate!=null ) {
                    Datum t1= Units.us2000.parse(firstDate);
                    Datum t2= Units.us2000.parse(lastDate);
                    if ( t1.le(t2) ) {
                        return new DatumRange( t1, t2 );
                    } else {
                        logger.warning( "firstDate and lastDate are out of order, ignoring.");
                    }
                }
            } else if ( info.has("startDate") ) { // note startDate is required.
                String startDate= info.getString("startDate");
				String stopDate;
				if ( info.has("stopDate") ) {
					stopDate= info.getString("stopDate");
				} else {
					stopDate= null;
				}
                if ( startDate!=null ) {
                    Datum t1= Units.us2000.parse(startDate);
                    Datum t2= Units.us2000.parse(stopDate);
                    if ( t1.le(t2) ) {
                        return new DatumRange( t1, t2 );
                    } else {
                        logger.warning( "firstDate and lastDate are out of order, ignoring.");
                    }
                }
			}
        } catch ( JSONException | ParseException ex ) {
            logger.log( Level.WARNING, ex.getMessage(), ex );
        }
        return null;
    }    

    /**
     * return a time which is a suitable time to discover the data.
     * @param info
     * @return 
     */
    public static DatumRange getSampleTimeRange( JSONObject info ) throws JSONException {
        DatumRange range= getRange(info);
        if ( range==null ) {
            logger.warning("server is missing required startDate and stopDate parameters.");
            throw new IllegalArgumentException("here fail");
        } else {
            DatumRange sampleRange=null;
            if ( info.has("sampleStartDate") && info.has("sampleStopDate") && !info.getString("sampleStartDate").trim().isEmpty() ) {
                try {
                    sampleRange = new DatumRange( Units.us2000.parse(info.getString("sampleStartDate")), Units.us2000.parse(info.getString("sampleStopDate")) );
                } catch (JSONException | ParseException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            } 
            if ( sampleRange==null ) {
                Datum cadence= Units.seconds.createDatum(60);  // assume default cadence of 1 minute results in 1 day sample range.
                if ( info.has("cadence") ) {
                    try{
                        int[] icadence= DatumRangeUtil.parseISO8601Duration(info.getString("cadence"));
                        cadence= cadenceArrayToDatum(icadence);
                    } catch ( ParseException ex ) {
                        logger.log(Level.WARNING, "parse error in cadence: {0}", info.getString("cadence"));
                    }
                }    
                if ( false ) { // range.max().ge(myValidTime)) { // Note stopDate is required since 2017-01-17.
                    logger.warning("server is missing required stopDate parameter.");
                    sampleRange = new DatumRange(range.min(), range.min().add(1, Units.days));
                } else {
                    if ( cadence.ge(Units.days.createDatum(1)) ) {
                        Datum end = TimeUtil.nextMidnight(range.max());
                        end= end.subtract( 10,Units.days );
                        if ( range.max().subtract(end).ge( Datum.create(1,Units.days ) ) ) {
                            sampleRange = new DatumRange( end, end.add(10,Units.days) );
                        } else {
                            sampleRange = new DatumRange( end.subtract(10,Units.days), end );
                        } 
                    } else if ( cadence.ge(Units.seconds.createDatum(1)) ) {
                        Datum end = TimeUtil.prevMidnight(range.max());
                        if ( range.max().subtract(end).ge( Datum.create(1,Units.hours ) ) ) {
                            sampleRange = new DatumRange( end, end.add(1,Units.days) );
                        } else {
                            sampleRange = new DatumRange( end.subtract(1,Units.days), end );
                        } 
                    } else {
                        Datum end = TimeUtil.prev( TimeUtil.HOUR, range.max() );
                        if ( range.max().subtract(end).ge( Datum.create(1,Units.minutes ) ) ) {
                            sampleRange = new DatumRange( end, end.add(1,Units.hours) );
                        } else {
                            sampleRange = new DatumRange( end.subtract(1,Units.hours), end );
                        } 
                    }
                    if ( !sampleRange.intersects(range) ) {
                        sampleRange= sampleRange.next();
                    }
                }
            }
            return sampleRange;                
        }
    }

    /**
     * encode the string into a URL, handling encoded characters.  Note this does 
     * nothing right now, but should still be used as the one place to handle URLs.
     * @param s
     * @return
     * @throws MalformedURLException 
     */
    public static final URL encodeURL(String s) throws MalformedURLException {
        try {
            if (true) {
                // s.matches("\\A\\p{ASCII}*\\z") ) {
                return new URL(s);
            } else {
                s = URLEncoder.encode(s); // re-decode these because they work and it makes the URL ledgible.
                s = s.replaceAll("\\%3A", ":");
                s = s.replaceAll("\\%2F", "/");
                s = s.replaceAll("\\%2B", "+");
                s = s.replaceAll("\\%2C", ",");
                return new URL(s);
            }
        } catch (MalformedURLException ex) {
            return new URL(URLEncoder.encode(s));
        }
    }

    /**
     * decode the URL into a string useful in Autoplot URIs.
     * @param s
     * @return 
     */
    public static final String decodeURL(URL s) {
        return URLDecoder.decode(s.toString());
    }

    /**
     * replace pluses with %2B and spaces with pluses.
     * @param s
     * @return
     */
    public static final String encodeURLParameters(String s) {
        s = s.replaceAll("\\+", "%2B");
        return s.replaceAll(" ", "+");
    }
    
    /**
     * replace %2B with pluses and pluses with spaces.
     * @param s
     * @return
     */
    public static final String decodeURLParameters(String s) {
        s = s.replaceAll("\\+", " ");
        return s.replaceAll("%2B", "+");
    }    
}
