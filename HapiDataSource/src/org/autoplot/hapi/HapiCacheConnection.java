
package org.autoplot.hapi;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * The Connection implementation wraps the command line version 
 * of cache-tools.
 * @author jbf
 */
public class HapiCacheConnection extends Connection {

    Process p;
    
    public HapiCacheConnection( URL url ) throws IOException {
        super(url);
        String scommand = System.getProperty( "hapi-cache-command" );
        if ( scommand==null ) scommand = System.getenv( "hapi-cache-command" );
        // "java -jar /home/jbf/ct/hapi/git/cache-tools/java/dist/cache-tools.jar"
        if ( scommand==null ) {
            throw new IllegalArgumentException("System property hapi-cache-command is not set.");
        }
        scommand = scommand + " --fetchOnce --cache-dir=/home/jbf/hapi-cache/ --url="+url;
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
