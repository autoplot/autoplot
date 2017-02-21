
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
    
    //TODO: this needs to come from a configuration variable.
    private static final File HAPI_HOME= new File("/home/jbf/autoplot_data/hapi/");
    
    /**
     * return the root of the HAPI server.
     * @return the root of the HAPI server.
     */
    protected static File getHapiHome() {
        return HAPI_HOME;
    }
}
