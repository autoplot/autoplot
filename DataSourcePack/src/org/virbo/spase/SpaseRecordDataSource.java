/*
 * SpaseRecordDataSource.java
 *
 * Created on October 8, 2007, 6:15 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.spase;

import org.virbo.metatree.SpaseMetadataModel;
import javax.xml.parsers.ParserConfigurationException;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.MetadataModel;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.TreeWalker;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
public class SpaseRecordDataSource implements DataSource {
    
    /**
     * the DataSource URL found within Bob's SPASE record.
     * at /Spase/NumericalData/AccessInformation/AccessURL/URL
     */
    URL url;
    
    /**
     * the DOM of the SPASE record
     */
    Document document;
    
    /**
     * DataSource delegate that will do the loading
     */
    DataSource delegate;
    
    
    /** Creates a new instance of SpaseRecordDataSource */
    public SpaseRecordDataSource( URI uri ) throws IllegalArgumentException, IOException, SAXException, Exception {
        try {
            this.url= new URL( uri.getSchemeSpecificPart() );
        } catch (MalformedURLException ex) {
            System.err.println("Failed to convert URI to URL");
            throw new RuntimeException(ex);
        }
        readXML();
        
        String surl=null;
        
        try {
            XPathFactory factory= XPathFactory.newInstance();
            XPath xpath= factory.newXPath();
            //XPathExpression expr= xpath.compile( "//Spase/NumericalData/AccessInformation/AccessURL/URL/text()" );
            //XPathExpression expr= xpath.compile( "//Spase/NumericalData/AccessInformation/AccessURL()" );
            //XPathExpression expr= xpath.compile( "//book/title/text()" );
            //surl= xpath.evaluate( "//Spase/NumericalData/AccessInformation/AccessURL/URL/text()", document );
            surl= findSurl();
            delegate= DataSetURI.getDataSource( DataSetURI.getURI( surl ) );
        } catch ( XPathExpressionException ex) {
            throw new IllegalArgumentException("unable to get /Spase/NumericalData/AccessInformation/AccessURL/URL(): "+ex.getMessage() );
        } catch ( MalformedURLException ex) {
            throw new IllegalArgumentException("Spase record AccessURL is malformed: "+surl );
        } catch ( Exception ex ) {
            throw ex;
        }
        
    }
    
    private String findSurl() {
        
        String[] lookFor= new String[] { "Spase", "NumericalData", "AccessInformation", "AccessURL", "URL" };
        
        NodeList list= document.getElementsByTagName(lookFor[0]);
        Element pos= (Element)list.item(0);

        if ( pos==null ) {
            throw new IllegalArgumentException("Unable to find node Space/NumericalData/AccessInformation/AccessURL/URL in "+url );
        }
        
        for ( int i=1; i<lookFor.length; i++ ) {
            list= pos.getElementsByTagName(lookFor[i]);
            pos= (Element)list.item(0);
        }
        
        String result=null;
        
        list= pos.getChildNodes();
        for (int k = 0; k < list.getLength(); k++) {
            Node child = list.item(k);
            // really should to do this recursively
            if (child.getNodeType() == Node.TEXT_NODE) {
                result= child.getNodeValue();
            }
        }
        
        return result;
        
    }
    
    
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        return delegate.getDataSet(mon);
    }
    
    public boolean asynchronousLoad() {
        return delegate.asynchronousLoad();
    }
    
    private void readXML() throws IOException, SAXException {
        DocumentBuilder builder= null;
        try {
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }
        InputSource source = new InputSource( url.openStream() );
        document = builder.parse(source);
        
    }
    
    public Map<String,Object> getMetadata( ProgressMonitor mon ) throws Exception {
        // If we're using a DOM Level 2 implementation, then our Document
        // object ought to implement DocumentTraversal
        DocumentTraversal traversal = (DocumentTraversal)document;
        
        NodeFilter filter = new NodeFilter() {
            public short acceptNode(Node n) {
                if (n.getNodeType() == Node.TEXT_NODE) {
                    // Use trim() to strip off leading and trailing space.
                    // If nothing is left, then reject the node
                    if (((Text)n).getData().trim().length() == 0)
                        return NodeFilter.FILTER_REJECT;
                }
                return NodeFilter.FILTER_ACCEPT;
            }
        };
        
        // This set of flags says to "show" all node types except comments
        int whatToShow = NodeFilter.SHOW_ALL & ~NodeFilter.SHOW_COMMENT;
        
        
        Node root= document.getFirstChild();
        
        // Create a TreeWalker using the filter and the flags
        TreeWalker walker = traversal.createTreeWalker( root, whatToShow,
                filter, false);
        
        //TreeModel tree= new DOMTreeWalkerTreeModel(walker);
        DOMWalker walk= new DOMWalker(walker);
        return walk.getAttributes( walk.getRoot() );
        
    }
    
    public String getURI() {
        return url.toString();
    }
    
    public Map<String, Object> getProperties() {
        try {
            return new SpaseMetadataModel().properties( getMetadata( new NullProgressMonitor() ) );
        } catch (Exception ex) {
            return Collections.singletonMap( "Exception", (Object)ex );
        } 
    }

    public <T> T getCapability(Class<T> clazz) {
        return null; //TODO
    }

    public MetadataModel getMetadataModel() {
        return new SpaseMetadataModel();
    }
    
    
}
