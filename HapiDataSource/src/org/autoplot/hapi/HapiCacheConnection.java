
package org.autoplot.hapi;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.LoggerManager;

/**
 * The Connection implementation wraps the command line version 
 * of cache-tools.
 * @author jbf
 */
public class HapiCacheConnection extends Connection {

    private static final Logger logger= LoggerManager.getLogger("apdss.hapi");
    
    Process p;
    
    public HapiCacheConnection( URL url ) throws IOException {
        super(url);
        String scommand = System.getProperty( "hapi-cache-command" );
        if ( scommand==null ) scommand = System.getenv( "hapi-cache-command" );
        // "java -jar /home/jbf/ct/hapi/git/cache-tools/java/dist/cache-tools.jar"
        if ( scommand==null ) {
            throw new IllegalArgumentException("System property hapi-cache-command is not set.");
        }
        if ( scommand.trim().length()==0 ) {
            throw new IllegalArgumentException("hapi-cache-command should not be empty here");
        }
        scommand = scommand + " --fetchOnce --url="+url+"";
        logger.log(Level.FINE, "executing: {0}", scommand);
        String[] command= scommand.split("\\s+");
        p= new ProcessBuilder(command).start();
    }
    
    @Override
    InputStream getInputStream() throws IOException {
        return p.getInputStream();
    }

    @Override
    InputStream getErrorStream() throws IOException {
        return p.getErrorStream();
    }

    @Override
    int getResponseCode() throws IOException {
        return 200;
    }

    @Override
    String getResponseMessage() throws IOException {
        return "";
    }

    @Override
    void disconnect() {
        p.destroy();
    }
    
}
