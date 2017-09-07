
package org.autoplot;

import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JComponent;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.xmlrpc.StandardClientProfile;
import org.astrogrid.samp.gui.GuiHubConnector;
import org.astrogrid.samp.client.AbstractMessageHandler;
import org.astrogrid.samp.client.HubConnection;
import org.das2.util.LoggerManager;
import org.autoplot.datasource.DataSetSelector;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.FileSystemUtil;
import org.autoplot.datasource.URISplit;
import org.das2.util.FileUtil;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;

/**
 * Listener for the Cluster Final Archive SAMP protocol.  Other clients
 * include the SOHO archive viewer, and Ulysses, will include the
 * Cluster Final Archive viewer.
 * @author jbf
 */
public class AddSampListener {

    private static final Logger logger= LoggerManager.getLogger("autoplot.samp");
    
    private static final Map<Integer,AbstractMessageHandler> listeners= new HashMap();
    
    private static void maybePlot( DataSetSelector sel, String uri ) {
        sel.setValue(uri);
        sel.maybePlot(true);
    }

    /**
     * Add the SAMP listener to Autoplot.
     * @param app the Autoplot application root.
     */
    public synchronized static void addSampListener( AutoplotUI app ) {
        final DataSetSelector sel= app.getDataSetSelector();
        addSampListener( sel );
    }
    
    /**
     * Add the SAMP listener to Autoplot.
     * @param sel the selector.
     */
    public synchronized static void addSampListener( final DataSetSelector sel ) {

        AbstractMessageHandler l= listeners.get(sel.hashCode()); //why is my HashMap not working???
        if ( listeners.size()>0 ) {
            logger.info("handler is already running.");
            return;
        }
        
        logger.info( "starting up SAMP listener" );

        StandardClientProfile profile = StandardClientProfile.getInstance();
        
        if ( !profile.isHubRunning() ) {
            try {
                logger.info("starting SAMP hub...");
                org.astrogrid.samp.hub.Hub.runMain(new String[0]);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, "error when starting hub");
                logger.log(Level.SEVERE, null, ex);
            }
        } else {
            logger.info("Client is already running.");
        }
        
        GuiHubConnector hubConnector = new GuiHubConnector(profile);
        hubConnector.setAutoconnect(3);

        if ( ! "true".equals( System.getProperty("java.awt.headless") ) ) {
            //hubConnector.createMonitorPanel();
            JComponent cc= hubConnector.createMonitorPanel();
            //cc.setMinimumSize( new Dimension(400,400));
            //cc.setPreferredSize( new Dimension(400,400));
            ScriptContext.addTab( "samp", cc );
        }
        
        Metadata meta = new Metadata();
        meta.setName("Autoplot");
        meta.setDescriptionText("Autoplot");
        meta.setIconUrl("http://autoplot.org/wiki/images/Logo32x32.png");
        meta.setDocumentationUrl("http://autoplot.org");

        hubConnector.declareMetadata(meta);

        class ConcreteMessageReceiver extends AbstractMessageHandler {
            String mType;

            ConcreteMessageReceiver( String mType ) {
                super( mType );
                this.mType= mType;
            }

            @Override
            public Map processCall( HubConnection connection, String senderId, Message message ) {
                logger.log(Level.FINE, "got message: {0}", message.toString());
                String s= (String) message.getParam("url");

                if ( s.startsWith( "file://" ) ) {
                    if ( s.startsWith( "file://localhost" ) ) s= s.substring(16);
                    if ( s.startsWith( "file://" ) ) s= s.substring(7);
                } else {
                    try {
                        if ( !FileSystemUtil.hasParent( new URL(s) ) ) {
                            try {
                                File file= DataSetURI.downloadResourceAsTempFile( new URL(s), new NullProgressMonitor() );
                                // remove the @ part.
                                String s1= file.getAbsolutePath();
                                int i1= s1.lastIndexOf("@");
                                File nnfile= new File( s1.substring(0,i1) );
                                if ( !file.renameTo( nnfile ) ) {
                                    logger.log(Level.WARNING, "unable to rename resource: {0}", file);
                                }
                                s= nnfile.toURI().toASCIIString();                                
                            } catch (MalformedURLException ex) {
                                logger.log(Level.SEVERE, null, ex);
                            } catch (IOException ex) {
                                logger.log(Level.SEVERE, null, ex);
                            }
                        }
                    } catch (MalformedURLException ex) {
                        logger.log(Level.SEVERE, null, ex);
                        //do what we did before.
                    }
                }
                String ext= s.endsWith(".cdf") ? ext="cdf" : null;
                
                if ( "cdf".equals(ext) || mType.equals("table.load.cdf") ) {
                    maybePlot( sel, "vap+cdf:" + s );
                } else if ( mType.equals("image.load.fits") )  {
                    maybePlot( sel, "vap+fits:" + s );
                }  else if ( mType.equals("table.load.fits") ) {
                    maybePlot( sel, "vap+fits:" + s );
                }
                return null;
            }
        }
        
        listeners.put( sel.hashCode(), l );

        ConcreteMessageReceiver messageHandler;
        messageHandler= new ConcreteMessageReceiver("image.load.fits");
        hubConnector.addMessageHandler(messageHandler);
        messageHandler= new ConcreteMessageReceiver("table.load.fits");
        hubConnector.addMessageHandler(messageHandler);
        messageHandler= new ConcreteMessageReceiver("table.load.cdf");
        hubConnector.addMessageHandler(messageHandler);
        hubConnector.declareSubscriptions(hubConnector.computeSubscriptions());
    }
    
}
