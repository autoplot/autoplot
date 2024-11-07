
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
public interface Connector {
    URL getURL();
    InputStream getInputStream() throws IOException;
    InputStream getErrorStream() throws IOException;
    int getResponseCode() throws IOException;
    String getResponseMessage() throws IOException;
    void disconnect();    
}
