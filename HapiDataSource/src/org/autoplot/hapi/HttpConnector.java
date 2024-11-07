/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.autoplot.hapi;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.HttpUtil;

/**
 *
 * @author jbf
 */
public class HttpConnector implements Connector {
    
    static final Logger loggerUrl= org.das2.util.LoggerManager.getLogger( "das2.url" );
    final HttpURLConnection fhttpConnect;
    
    final URL url;
    
    public HttpConnector( URL url ) throws IOException {
        this.url= url;
        
        loggerUrl.log(Level.FINE, "GET {0}", new Object[] { url } );
        HttpURLConnection httpConnect=  ((HttpURLConnection)url.openConnection());
        loggerUrl.log(Level.FINE, "--> {0} {1}", new Object[]{httpConnect.getResponseCode(), httpConnect.getResponseMessage()});        
        httpConnect.setConnectTimeout(FileSystem.settings().getConnectTimeoutMs());
        httpConnect.setReadTimeout(FileSystem.settings().getReadTimeoutMs());
        httpConnect= (HttpURLConnection) HttpUtil.checkRedirect(httpConnect);
        
                //httpConnect= ((HttpURLConnection) url.openConnection());
                //if ( httpConnect.getResponseCode()==HttpURLConnection.HTTP_MOVED_PERM ||
                //       httpConnect.getResponseCode()==HttpURLConnection.HTTP_MOVED_TEMP ) {
                //    String newLocation = httpConnect.getHeaderField("Location");
                //    if ( !newLocation.contains("?") ) {
                //        String args= url.getQuery();
                //        newLocation= newLocation + args;
                //    }
                //    url= new URL( newLocation );
                //    httpConnect= ((HttpURLConnection) url.openConnection());
                //}

        
                //boolean doAllowGZip= false;
                //if ( doAllowGZip ) {
                //    httpConnect= (HttpURLConnection)url.openConnection();
                //    httpConnect.setConnectTimeout(FileSystem.settings().getConnectTimeoutMs());
                //    httpConnect.setReadTimeout(FileSystem.settings().getReadTimeoutMs());
                //    httpConnect.setRequestProperty( "Accept-Encoding", "gzip" );
                //    httpConnect= (HttpURLConnection)HttpUtil.checkRedirect(httpConnect); // There's a problem, because it looks like the entire response is read here.
                //    httpConnect.connect();
                //    loggerUrl.log(Level.FINE, "--> {0} {1}", new Object[]{httpConnect.getResponseCode(), httpConnect.getResponseMessage()});
                //    gzip=true;
                //}        
        fhttpConnect= httpConnect;
    }
    
            @Override
            public URL getURL() {
                return url;
            }
            
            @Override
            public InputStream getInputStream() throws IOException {
                return fhttpConnect.getInputStream();
            }

            @Override
            public InputStream getErrorStream() throws IOException {
                return fhttpConnect.getErrorStream();
            }
            
            @Override
            public String getResponseMessage() throws IOException {
                return fhttpConnect.getResponseMessage();
            }

            @Override
            public int getResponseCode() throws IOException {
                return fhttpConnect.getResponseCode();
            }
            
            @Override
            public void disconnect() {
                fhttpConnect.disconnect();
            }

}
