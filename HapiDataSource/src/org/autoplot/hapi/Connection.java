
package org.autoplot.hapi;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.LoggerManager;

/**
 * Abstract the interface to get a connection so that the HAPI Cache 
 * can be used to proxy.    
 * Something, maybe a caching layer, which provides connections.   
 * @author jbf
 */
public abstract class Connection {
    
    private static final Logger logger= LoggerManager.getLogger("apdss.hapi");
    
    URL url;
    public Connection( URL url ) {
        this.url= url;
    }
    public URL getURL() {
        return url;
    }
    public static Connection openConnection( URL url ) throws IOException {
        boolean useCache= true;
        if ( useCache ) {
            String scommand = System.getProperty( "hapi-cache-command" );
            if ( scommand==null ) scommand = System.getenv( "hapi-cache-command" );
            if ( scommand==null ) {
                useCache = false;
            }
            if ( useCache ) {
                logger.log(Level.FINE, "using cache with: {0}", scommand);
            }
        }
        if ( useCache ) {
            return new HapiCacheConnection(url);
        } else {
            return new HttpConnection(url);
        }
    }
    
    abstract InputStream getInputStream() throws IOException;
    abstract InputStream getErrorStream() throws IOException;
    abstract int getResponseCode() throws IOException;
    abstract String getResponseMessage() throws IOException;
    abstract void disconnect();    
}
