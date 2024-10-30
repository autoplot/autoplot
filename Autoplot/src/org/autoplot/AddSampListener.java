
package org.autoplot;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Frame;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ComboBoxEditor;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.xmlrpc.StandardClientProfile;
import org.astrogrid.samp.gui.GuiHubConnector;
import org.astrogrid.samp.client.AbstractMessageHandler;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.HubConnector;
import org.das2.util.LoggerManager;
import org.autoplot.datasource.DataSetSelector;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceEditorDialog;
import org.autoplot.datasource.DataSourceEditorPanel;
import org.autoplot.datasource.DataSourceEditorPanelUtil;
import org.autoplot.datasource.FileSystemUtil;
import org.autoplot.datasource.WindowManager;
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
    
    private static synchronized void maybePlot( DataSetSelector sel, String uri ) {
        
        if ( false ) {
            JPanel parent= new JPanel();
            parent.setLayout( new BorderLayout() );
            DataSourceEditorPanel p= DataSourceEditorPanelUtil.getDataSourceEditorPanel(parent,uri);

            AutoplotUI dialogParent= ScriptContext.getApplication();

            DataSourceEditorDialog dialog = new DataSourceEditorDialog( dialogParent, p.getPanel(), true);
            dialog.revalidate();

            Icon icon= new javax.swing.ImageIcon(AddSampListener.class.getResource("/org/autoplot/datasource/fileMag.png") );
            if ( JOptionPane.OK_OPTION==WindowManager.showConfirmDialog( dialogParent, parent, "Editing URI", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, icon ) ) {
                String newUri= p.getURI();
                ScriptContext.plot(newUri);        
            }       
        } else {
            sel.setValue(uri);
            final DataSetSelector fsel= sel;
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    fsel.maybePlot(KeyEvent.ALT_MASK); // this needs to be done on the event thread because inspectURI gets model data from GUI. TODO: fix this after AGU!
                }
            };
            try {
                SwingUtilities.invokeAndWait(run);
            } catch (InterruptedException | InvocationTargetException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Add the SAMP listener to Autoplot.
     * @param app the Autoplot application root.
     */
    public synchronized static void addSampListener( AutoplotUI app ) {
        final DataSetSelector sel= app.getDataSetSelector();
        addSampListener( sel );
    }
    
    private static GuiHubConnector hubConnector;
            
    /**
     * return the HubConnector for testing.
     * @return 
     */
    public static synchronized HubConnector getHubConnector() {
        return hubConnector;
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
        
        hubConnector = new GuiHubConnector(profile);
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
        meta.setIconUrl("https://autoplot.org/Logo32.png");
        meta.setDocumentationUrl("https://autoplot.org");

        hubConnector.declareMetadata(meta);

        class ConcreteMessageReceiver extends AbstractMessageHandler {
            String mType;
            boolean isFileResource;

            ConcreteMessageReceiver( String mType ) {
                super( mType );
                this.mType= mType;
                isFileResource=  !mType.equals("table.load.das2");
            }

            @Override
            public Map processCall( HubConnection connection, String senderId, Message message ) {
                logger.log(Level.FINE, "got message: {0}", message.toString());
                logger.log(Level.FINER, "handling as file resource: {0}", isFileResource);
                String s= (String) message.getParam("url");
                String n= (String) message.getParam("name");
                if ( n!=null && n.endsWith(" table") ) {
                    n= n.substring(0,n.length()-6).trim();
                }
                    
                if ( s.startsWith( "file://" ) ) {
                    if ( s.startsWith( "file://localhost" ) ) s= s.substring(16);
                    if ( s.startsWith( "file://" ) ) s= s.substring(7);
                } else {
                    try {
                        if ( isFileResource && !FileSystemUtil.hasParent( new URL(s) ) ) {
                            try {
                                logger.log(Level.FINER, "downloading file {0}", s );
                                File file= DataSetURI.downloadResourceAsTempFile( new URL(s), new NullProgressMonitor() );
                                // remove the @ part.
                                String s1= file.getAbsolutePath();
                                File nnfile;
                                if ( n!=null ) {
                                    int i1= s1.lastIndexOf('/');
                                    nnfile= new File( s1.substring(0,i1) +"/"+ n );
                                } else {
                                    int i1= s1.lastIndexOf('@');
                                    nnfile= new File( s1.substring(0,i1) );
                                }
                                if ( !file.renameTo( nnfile ) ) {
                                    logger.log(Level.WARNING, "unable to rename resource: {0}", file);
                                }
                                if ( n!=null ) {
                                    
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
                String ext= s.endsWith(".cdf") ? "cdf" : null;
                
                if ( "cdf".equals(ext) || mType.equals("table.load.cdf") ) {
                    maybePlot( sel, "vap+cdf:" + s );
                } else if ( mType.equals("table.load.das2") )  {
                    maybePlot( sel, "vap+das2server:" + s );
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
        messageHandler= new ConcreteMessageReceiver("table.load.das2");
        hubConnector.addMessageHandler(messageHandler);
        hubConnector.declareSubscriptions(hubConnector.computeSubscriptions());
    }
    
}
