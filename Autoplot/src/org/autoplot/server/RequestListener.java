
package org.autoplot.server;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 
 * @author jbf
 */
public class RequestListener {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.server");

    public RequestListener() {
    }

    public void startListening() {
        logger.fine("start listening");
        this.listening = true;
        new Thread(run).start();
    }
    
    public void stopListening() {
        logger.fine("stop listening");
        this.listening= false;
    }
    
    private boolean readData = false;
    public static final String PROP_READDATA = "readData";

    public boolean isReadData() {
        return this.readData;
    }

    public void setReadData(boolean newreadData) {
        boolean oldreadData = readData;
        this.readData = newreadData;
        propertyChangeSupport.firePropertyChange(PROP_READDATA, oldreadData, newreadData);
    }

    private final Runnable run = new Runnable() {

        @Override
        public void run() {

            ServerSocket listen;
            try {
                listen= new ServerSocket(port, 1000);
            } catch ( IOException ex ) {
                listening = false;
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                listen= null;
            }
            while (listening) {
                assert listen!=null;
                try {
                    setPort( listen.getLocalPort() );
                    
                    System.err.println("Autoplot is listening on port "+port+".");
                    logger.log(Level.FINE, "Autoplot is listening on port {0}.", port);

                    // wait for connections forever
                    while (listening) {
                        Socket socket = listen.accept();
                        logger.log(Level.INFO, "connect @ {0}", new Date( System.currentTimeMillis() ));
                        setSocket(socket);

                        if (readData) {
                            try {
                                InputStream in = socket.getInputStream();

                                StringBuilder buf = new StringBuilder();

                                int i = in.read();
                                while (i != -1) {
                                    buf.append((char) i);
                                    i = in.read();
                                }
                                setData(buf.toString());
                            } catch (IOException ex) {
                                logger.log(Level.SEVERE, ex.getMessage(), ex);
                            }
                        }

                        setRequestCount(getRequestCount() + 1);

                    }
                } catch (IOException ex) {
                    listening = false;
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }

            }

            if ( listen!=null ) {
                try {
                    logger.info("closing connection");
                    listen.close();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }

        }
    };
    private Socket socket = null;
    public static final String PROP_SOCKET = "socket";

    public Socket getSocket() {
        return this.socket;
    }

    public void setSocket(Socket newsocket) {
        Socket oldsocket = socket;
        this.socket = newsocket;
        propertyChangeSupport.firePropertyChange(PROP_SOCKET, oldsocket, newsocket);
    }
    
    public static final String PROP_PORT = "port";
    private int port = 1234;

    /**
     * return the port being used.  Note if the port was zero, this
     * will be assigned the port that was used.
     * @return the port being used.
     */
    public int getPort() {
        return this.port;
    }

    /**
     * this may be zero to request that a port be chosen, or non-zero
     * for the port number.
     * @param newport 
     */
    public void setPort(int newport) {
        int old= this.port;
        this.port = newport;
        propertyChangeSupport.firePropertyChange( PROP_PORT, old, port );
    }

    public OutputStream getOutputStream() {
        throw new UnsupportedOperationException("Not yet implemented");
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
        logger.log(Level.FINE, "setData({0})", newdata);
        propertyChangeSupport.firePropertyChange(PROP_DATA, olddata, newdata);
    }
    
    private boolean listening = false;
    public static final String PROP_LISTENING = "listening";

    public boolean isListening() {
        return this.listening;
    }

    public void setListening(boolean newlistening) {
        logger.log(Level.FINE, "setListening({0})", newlistening);
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

    public synchronized void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
    }

    public synchronized void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(propertyName, listener);
    }

}
