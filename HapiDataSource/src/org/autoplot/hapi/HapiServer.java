
package org.autoplot.hapi;

import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.DatumRange;
import org.das2.datum.TimeParser;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.autoplot.datasource.AutoplotSettings;

/**
 * Utility methods for interacting with HAPI servers.  
 * @author jbf
 */
public class HapiServer {
    
    protected final static Logger logger= Logger.getLogger("apdss.hapi");
    
    /**
     * get known servers.  
     * @return known servers
     */
    public static List<String> getKnownServers() {
        ArrayList<String> result= new ArrayList<>();
        if ( "true".equals(System.getProperty("hapiDeveloper","false")) ) {
            result.add("http://tsds.org/get/IMAGE/PT1M/hapi");
            result.add("https://cdaweb.gsfc.nasa.gov/registry/hdp/hapi");
            result.add("http://jfaden.net/HapiServerDemo/hapi");
        }            
        result.add("http://datashop.elasticbeanstalk.com/hapi");

        return result;
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
                r = new BufferedReader(new FileReader(hist));
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
        StringBuilder builder= new StringBuilder();
        logger.log(Level.FINE, "getCatalogIds {0}", url.toString());
        try ( BufferedReader in= new BufferedReader( new InputStreamReader( url.openStream() ) ) ) {
            String line= in.readLine();
            while ( line!=null ) {
                builder.append(line);
                line= in.readLine();
            }
        }
        JSONObject o= new JSONObject(builder.toString());
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
        StringBuilder builder= new StringBuilder();
        logger.log(Level.FINE, "getCatalog {0}", url.toString());
        try ( BufferedReader in= new BufferedReader( new InputStreamReader( url.openStream() ) ) ) {
            String line= in.readLine();
            while ( line!=null ) {
                builder.append(line);
                line= in.readLine();
            }
        }
        if ( builder.length()==0 ) {
            throw new IOException("empty response from "+url );
        }
        JSONObject o= new JSONObject(builder.toString());
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
    
    /**
     * return the URL for data requests.
     * @param server
     * @param id string like "data4" or "spase://..."
     * @param tr the time range
     * @param parameters zero-length, or a comma-delineated list of parameters.
     * @return the request, with the ID and parameters URL encoded.
     */
    public static URL getDataURL( URL server, String id, DatumRange tr, String parameters ) {
        TimeParser tp= TimeParser.create("$Y-$m-$dT$H:$M:$SZ");
        HashMap<String,String> map= new LinkedHashMap();
        map.put(HapiSpec.URL_PARAM_ID, id );
        map.put(HapiSpec.URL_PARAM_TIMEMIN, tp.format(tr.min()) );
        map.put(HapiSpec.URL_PARAM_TIMEMAX, tp.format(tr.max()) );
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
        try {
            return URLEncoder.encode( id, "UTF-8" );
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalArgumentException(ex);
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
        url= HapiServer.createURL(server, HapiSpec.INFO_URL, Collections.singletonMap(HapiSpec.URL_PARAM_ID, id ) );
        StringBuilder builder= new StringBuilder();
        logger.log(Level.FINE, "getInfo {0}", url.toString());
        try ( BufferedReader in= new BufferedReader( new InputStreamReader( url.openStream() ) ) ) {
            String line= in.readLine();
            while ( line!=null ) {
                builder.append(line);
                line= in.readLine();
            }
        }
        JSONObject o= new JSONObject(builder.toString());
        
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
        StringBuilder builder= new StringBuilder();
        logger.log(Level.FINE, "getCapabilities {0}", url.toString());
        try ( BufferedReader in= new BufferedReader( new InputStreamReader( url.openStream() ) ) ) {
            String line= in.readLine();
            while ( line!=null ) {
                builder.append(line);
                line= in.readLine();
            }
        }
        JSONObject o= new JSONObject(builder.toString());
        
        return o;
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
    
}
