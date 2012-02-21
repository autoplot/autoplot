/*
 * SpaseRecordDataSourceFactory.java
 *
 * Created on October 8, 2007, 6:57 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.spase;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.URISplit;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
public class SpaseRecordDataSourceFactory implements DataSourceFactory {
    
    /** Creates a new instance of SpaseRecordDataSourceFactory */
    public SpaseRecordDataSourceFactory() {
    }
    
    public DataSource getDataSource(URI uri) throws Exception {
        return new SpaseRecordDataSource(uri);
    }
    
    public List<CompletionContext> getCompletions(CompletionContext cc,org.das2.util.monitor.ProgressMonitor mon) throws Exception {
        return java.util.Collections.emptyList();
    }
    
    public String editPanel(String surl) throws Exception {
        return surl;
    }
    
    
    public boolean reject( String surl, ProgressMonitor mon ) throws IllegalArgumentException {

        DocumentBuilder builder= null;
        
        try {
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

            URISplit split = URISplit.parse( surl );

            URL url= new URL( split.file );

            InputStream in=  url.openStream() ;
            InputSource source = new InputSource(in );
            Document document = builder.parse(source);
            in.close();

            //String[] lookFor= new String[] { "Spase", "NumericalData", "AccessInformation", "AccessURL", "URL" };

            Node n= document.getDocumentElement();

            //String localName= n.getLocalName();  //TODO: why doesn't this work?
            String localName= n.getNodeName();
            int i= localName.indexOf(":");
            if ( i>-1  ) {
                localName= localName.substring(i+1);
            }

            if ( localName.equals("Spase") ) {  // Spase record
                return false;
            } else if ( localName.equals("Eventlist")) {  // HELM from Goddard SPDF
                return false;
            } else if ( localName.equals("VOTABLE")) {  // VOTABLE from VxOs and AMDA
                return false;
            } else {
                return true;
            }
            
        } catch ( Exception ex) {
            Logger.getLogger(SpaseRecordDataSourceFactory.class.getName()).log(Level.SEVERE, null, ex);
            return true;
        }
    }

    public String urlForServer(String surl) {
        return surl; //TODO
    }

    public <T> T getCapability(Class<T> clazz) {
        return null;
    }
    
}
