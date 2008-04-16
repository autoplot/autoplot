/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jbf
 */
public class RequestListener {

    public RequestListener() {
    }

    public void startListening() {
        this.listening= true;
        new Thread(run).start();
    }
    
    private Runnable run = new Runnable() {

        public void run() {
            
            while (listening) {
                try {
                    ServerSocket listen = new ServerSocket(port, 1000);

                    // wait for connections forever
                    while (listening) {
                        Socket socket = listen.accept();
                        requestCount++;

                        InputStream in = socket.getInputStream();

                        StringBuffer buf = new StringBuffer();

                        int i = in.read();
                        while (i != -1) {
                            buf.append((char) i);
                            i = in.read();
                        }
                        setData( buf.toString() );
                    }
                } catch (IOException ex) {
                    Logger.getLogger(RequestListener.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            

        }
    };
    
    private int port = 1234;

    public int getPort() {
        return this.port;
    }

    public void setPort(int newport) {
        this.port = newport;
    }
    private String data = null;
    public static final String PROP_DATA = "data";

    /**
     * Get the value of data
     *
     * @return the value of data
     */
    public String getData() {
        return this.data;
    }

    /**
     * Set the value of data
     *
     * @param newdata new value of data
     */
    public void setData(String newdata) {
        String olddata = data;
        this.data = newdata;
        propertyChangeSupport.firePropertyChange(PROP_DATA, olddata, newdata);
    }
    
    private boolean listening = false;
    public static final String PROP_LISTENING = "listening";

    public boolean isListening() {
        return this.listening;
    }

    public void setListening(boolean newlistening) {
        boolean oldlistening = listening;
        this.listening = newlistening;
        propertyChangeSupport.firePropertyChange(PROP_LISTENING, oldlistening, newlistening);
    }
    private int requestCount = 0;
    public static final String PROP_REQUESTCOUNT = "requestCount";

    public int getRequestCount() {
        return this.requestCount;
    }

    public void setRequestCount(int newrequestCount) {
        int oldrequestCount = requestCount;
        this.requestCount = newrequestCount;
        propertyChangeSupport.firePropertyChange(PROP_REQUESTCOUNT, oldrequestCount, newrequestCount);
    }
    private java.beans.PropertyChangeSupport propertyChangeSupport = new java.beans.PropertyChangeSupport(this);

    /**
     * Add PropertyChangeListener.
     *
     * @param listener
     */
    public void addPropertyChangeListener(java.beans.PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    /**
     * Remove PropertyChangeListener.
     *
     * @param listener
     */
    public void removePropertyChangeListener(java.beans.PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }
}
