
package org.autoplot.hapiserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * helpful functions.
 * @author jbf
 */
public class Util {
    
    /**
     * return the duration in a easily-human-consumable form.
     * @param dt the duration in milliseconds.
     * @return a duration like "2.6 hours"
     */
    public static String getDurationForHumans( long dt ) {
        if ( dt<2*1000 ) {
            return dt+" milliseconds";
        } else if ( dt<2*60000 ) {
            return String.format( Locale.US, "%.1f",dt/1000.)+" seconds";
        } else if ( dt<2*3600000 ) {
            return String.format( Locale.US, "%.1f",dt/60000.)+" minutes";
        } else if ( dt<2*86400000 ) {
            return String.format( Locale.US, "%.1f",dt/3600000.)+" hours";
        } else {
            return String.format( Locale.US, "%.1f",dt/86400000.)+" days";
        }
    }
    
    private static volatile File HAPI_HOME=null;
    
    /**
     * return the root of the HAPI server.
     * @return the root of the HAPI server.
     */
    public static File getHapiHome() {
        if ( Util.HAPI_HOME==null ) {
            throw new IllegalArgumentException("Util.HAPI_HOME is not set.");
        } else {
            return Util.HAPI_HOME;
        }
    }
    
    public static void setHapiHome( File f ) {
        File HAPI_HOME_ = Util.HAPI_HOME;
        if ( HAPI_HOME_==null ) {
            synchronized ( Util.class ) {
                HAPI_HOME_ = Util.HAPI_HOME;
                if ( HAPI_HOME_==null ) {
                    Util.HAPI_HOME= HAPI_HOME_ = f;
                }
            }
        } else {
            // it has been set already.
        }
    }
    
    /**
     * This should point to the name of the directory containing HAPI configuration.
     * This directory should contain catalog.json, capabilities.json and a 
     * subdirectory "info" which contains files with the name &lt;ID&gt;.json,
     * each containing the info response.  Note these should also contain a
     * tag "uri" which is the Autoplot URI that serves this data.
     */
    public static final String HAPI_SERVER_HOME_PROPERTY = "HAPI_SERVER_HOME";

    public static final String hapiVersion() {
        return "1.1";
    }
    
    static boolean isKey(String key) {
        Pattern p= Pattern.compile("\\d{8}+");
        return p.matcher(key).matches();
    }
    
    /**
     * this key can create place for the data
     * @param id
     * @param key
     * @return 
     */
    static boolean keyCanCreate(String id,String key) {
        if ( !isKey(key) ) {
            throw new IllegalArgumentException("is not a key: "+key);
        }
        File keyFile= new File( getHapiHome(), "keys" );
        if ( !keyFile.exists() ) return false;
        keyFile= new File( keyFile, id + ".json" );
        if ( !keyFile.exists() ) return false;
        try {
            JSONObject jo= HapiServerSupport.readJSON(keyFile);
            if ( jo.has(key) ) {
                jo= jo.getJSONObject(key);
                return jo.getBoolean("create");
            } else {
                return false;
            }
        } catch ( IOException | JSONException ex ) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * this key can modify or add records to the data
     * @param id
     * @param key
     * @return 
     */
    static boolean keyCanModify(String id,String key) {
        if ( !isKey(key) ) {
            throw new IllegalArgumentException("is not a key: "+key);
        }
        File keyFile= new File( getHapiHome(), "keys" );
        if ( !keyFile.exists() ) return false;
        keyFile= new File( keyFile, id + ".json" );
        if ( !keyFile.exists() ) return false;
        try {
            JSONObject jo= HapiServerSupport.readJSON(keyFile);
            if ( jo.has(key) ) {
                jo= jo.getJSONObject(key);
                return jo.getBoolean("modify") || jo.getBoolean("create");
            } else {
                return false;
            }
        } catch ( IOException | JSONException ex ) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * this key can delete records from the data
     * @param id
     * @param key
     * @return 
     */
    static boolean keyCanDelete(String id,String key) {
        if ( !isKey(key) ) {
            throw new IllegalArgumentException("is not a key: "+key);
        }
        File keyFile= new File( getHapiHome(), "keys" );
        if ( !keyFile.exists() ) return false;
        keyFile= new File( keyFile, id + ".json" );
        if ( !keyFile.exists() ) return false;
        try {
            JSONObject jo= HapiServerSupport.readJSON(keyFile);
            if ( jo.has(key) ) {
                jo= jo.getJSONObject(key);
                return jo.getBoolean("delete") || jo.getBoolean("create");
            } else {
                return false;
            }
        } catch ( IOException | JSONException ex ) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * transfers the data from one channel to another.  src and dest are
     * closed after the operation is complete.
     * @param src
     * @param dest
     * @throws java.io.IOException
     */
    public static void transfer( InputStream src, OutputStream dest ) throws IOException {
        final byte[] buffer = new byte[ 16 * 1024 ];

        int i= src.read(buffer);
        while ( i != -1) {
            dest.write(buffer,0,i);
            i= src.read(buffer);
        }
        dest.close();
        src.close();
    }
    
}
