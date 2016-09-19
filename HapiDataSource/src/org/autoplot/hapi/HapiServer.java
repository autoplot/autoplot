
package org.autoplot.hapi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.DatumRange;
import org.das2.datum.TimeParser;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Utility methods for interacting with HAPI servers.
 * @author jbf
 */
public class HapiServer {
    
    protected final static Logger logger= Logger.getLogger("apdss.hapi");
    
    /**
     * get known servers.  The das2server scrapes through the user's history 
     * to find servers as well, but we might have a more transparent method
     * for doing this.
     * @return known servers
     */
    public static List<String> getKnownServers() {
        ArrayList<String> result= new ArrayList<>();
        result.add("http://tsds.org/get/IMAGE/PT1M/hapi");
        result.add("http://localhost:8084/HapiServerDemo");
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
     * return the list of datasets available at the server
     * @param server the root of the server, which should should contain "catalog"
     * @return list of datasets
     * @throws java.io.IOException
     * @throws org.json.JSONException
     */
    public static List<String> getCatalog( URL server ) throws IOException, JSONException {
        URL url;
        url= HapiServer.createURL( server, "catalog" );
        if ( server.toString().contains( "http://tsds.org/get/IMAGE/PT1M/hapi" ) ) {
            url= HapiServer.createURL( server, "catalog/" );
        }
        StringBuilder builder= new StringBuilder();
        logger.log(Level.FINE, "getCatalog {0}", url.toString());
        try ( BufferedReader in= new BufferedReader( new InputStreamReader( url.openStream() ) ) ) {
            String line= in.readLine();
            while ( line!=null ) {
                builder.append(line);
                line= in.readLine();
            }
        }
        JSONObject o= new JSONObject(builder.toString());
        JSONArray catalog= o.getJSONArray("catalog");
        List<String> result= new ArrayList<>(catalog.length());
        for ( int i=0; i<catalog.length(); i++ ) {
            result.add( i,catalog.getJSONObject(i).getString("id") );
        }
        return result;
    }
    
    /**
     * return the URL for getting info.
     * @param server
     * @param id
     * @return 
     */
    public static URL getInfoURL( URL server, String id ) {
        try {
            if (server.toString().contains( "http://tsds.org/get/IMAGE/PT1M/hapi" )  ) {
                return new URL( server.toString() + "/info/?id="+id );
            } else {
                return new URL( server.toString() + "/info?id="+id );
            }
        } catch ( MalformedURLException ex ) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * return the URL for data requests.
     * @param server
     * @param id
     * @param tr
     * @return 
     */
    public static URL getDataURL( URL server, String id, DatumRange tr ) {
        try {
            TimeParser tp= TimeParser.create(TimeParser.TIMEFORMAT_Z);
            if (server.toString().contains( "http://tsds.org/get/IMAGE/PT1M/hapi" )  ) {
                return new URL( server.toString()+"/data/?id="+id+"&time.min="+tp.format(tr.min())+"&time.max="+tp.format(tr.max()) );
            } else {
                return new URL( server.toString()+"/data?id="+id+"&time.min="+tp.format(tr.min())+"&time.max="+tp.format(tr.max()) );
            }
        } catch ( MalformedURLException ex ) {
            throw new RuntimeException(ex);
        }
    }
        
    /**
     * return the URL by appending the text to the end of the server URL.  This
     * avoids extra slashes, etc.
     * @param server
     * @param append
     * @return 
     */
    public static URL createURL( URL server, String append ) {
        String s= server.toString();
        if ( append.startsWith("/") ) {
            append= append.substring(1);
        }
        if ( s.endsWith("/") ) {
            s= s + append;
        } else {
            s= s + "/" + append;
        }
        try {
            return new URL(s);
        } catch ( MalformedURLException ex ) {
            throw new IllegalArgumentException(ex);
        }
    }


    
}
