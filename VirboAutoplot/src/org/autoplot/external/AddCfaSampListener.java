/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.external;

import java.util.Map;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.xmlrpc.StandardClientProfile;
import org.astrogrid.samp.gui.GuiHubConnector;
import org.astrogrid.samp.client.AbstractMessageHandler;
import org.astrogrid.samp.client.HubConnection;
import org.virbo.datasource.DataSetSelector;

/**
 * Listener for the Cluster Final Archive SAMP protocol.  Other clients
 * include the SOHO archive viewer, and Ulysses, will include the
 * Cluster Final Archive viewer.
 * @author jbf
 */
public class AddCfaSampListener {

    private static void maybePlot( DataSetSelector sel, String uri ) {
        sel.setValue(uri);
        sel.maybePlot(true);
    }

    public static void addCfaSampListener( final DataSetSelector sel ) {
        
        System.err.println( "starting up SAMP listener for Cluster Final Archive" );

        StandardClientProfile profile = StandardClientProfile.getInstance();
        GuiHubConnector hubConnector = new GuiHubConnector(profile);
        hubConnector.setAutoconnect(3);

        Metadata meta = new Metadata();
        meta.setName("Autoplot");
        meta.setDescriptionText("Autoplot");
        meta.setIconUrl("http://autoplot.org:8080/hudson/job/autoplot-release/lastSuccessfulBuild/artifact/autoplot/VirboAutoplot/dist/logo16x16.png");
        meta.setDocumentationUrl("http://autoplot.org");

        hubConnector.declareMetadata(meta);

        class ConcreteMessageReceiver extends AbstractMessageHandler {
            String mType;

            ConcreteMessageReceiver( String mType ) {
                super( mType );
                this.mType= mType;
            }

            public Map processCall( HubConnection connection, String senderId, Message message ) {
                System.err.printf( "got message: %s%n", message.toString() );
                String s= (String) message.getParam("url");

                if ( s.startsWith( "file://localhost" ) ) s= s.substring(16);
                if ( s.startsWith( "file://" ) ) s= s.substring(7);
                String ext= s.endsWith(".cdf") ? ext="cdf" : null;
                if ( "cdf".equals(ext) || mType.equals("table.load.cdf") ) {
                    maybePlot( sel, "vap+cdf:file://" + java.net.URLDecoder.decode(s) );
                } else if ( mType.equals("image.load.fits") )  {
                    maybePlot( sel, "vap+fits:file://" + java.net.URLDecoder.decode(s) );
                }  else if ( mType.equals("table.load.fits") ) {
                    maybePlot( sel, "vap+fits:file://" + java.net.URLDecoder.decode(s) );
                }
                return null;
            }
        }

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
