
package org.autoplot.hapi;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Abstract the interface to get a connection so that the HAPI Cache 
 * can be used to proxy.    
 * Something, maybe a caching layer, which provides connections.   
 * @author jbf
 */
public abstract class Connection {
    URL url;
    public Connection( URL url ) {
        this.url= url;
    }
    public URL getURL() {
        return url;
    }
    public static Connection openConnection( URL url ) throws IOException {
        return new HttpConnection(url);
    }
    
    abstract InputStream getInputStream() throws IOException;
    abstract InputStream getErrorStream() throws IOException;
    abstract int getResponseCode() throws IOException;
    abstract String getResponseMessage() throws IOException;
    abstract void disconnect();    
}
