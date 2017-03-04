
package org.autoplot.hapiserver;

import java.io.File;
import java.util.Locale;

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
}
