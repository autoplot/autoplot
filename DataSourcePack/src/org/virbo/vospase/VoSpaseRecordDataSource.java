/*
 * VoSpaseRecordDataSource.java
 *
 * Created on October 8, 2007, 6:15 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.vospase;

import edu.uiowa.physics.pw.das.util.DasProgressMonitor;
import edu.uiowa.physics.pw.das.util.NullProgressMonitor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import javax.swing.tree.TreeModel;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.xerces.parsers.DOMParser;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
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
public class VoSpaseRecordDataSource implements DataSource {
    
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
    
    
    /**
     * Creates a new instance of VoSpaseRecordDataSource
     * @param url is the URL of the SPASE record.  It should contain the parameters "name" and "timerange" and "cadence"
     */
    public VoSpaseRecordDataSource( URL urlParms ) throws IllegalArgumentException, IOException, SAXException, Exception {
        
        DataSetURL.URLSplit split= DataSetURL.parse( url.toString() );
        this.url= new URL( split.file );
        
        Map parms= DataSetURL.parseParams( split.params );
        String id= (String) parms.get( "repositoryId" );
        String timerange= (String) parms.get("timerange" );
        String resolution= (String) parms.get( "cadence" );
        
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
            delegate= DataSetURL.getDataSource( new URL( surl ) );
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
    
    
    public QDataSet getDataSet(DasProgressMonitor mon) throws Exception {
        return delegate.getDataSet(mon);
    }
    
    public boolean asynchronousLoad() {
        return delegate.asynchronousLoad();
    }
    
    private void readXML() throws IOException, SAXException {
        DOMParser parser = new org.apache.xerces.parsers.DOMParser();
        
        Reader in = new BufferedReader( new InputStreamReader( url.openStream() ) );
        InputSource input = new org.xml.sax.InputSource(in);
        
        parser.parse(input);
        
        document = parser.getDocument();
        
    }
    
    public TreeModel getMetaData( DasProgressMonitor mon ) throws Exception {
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
        
        // Create a TreeWalker using the filter and the flags
        TreeWalker walker = traversal.createTreeWalker(document, whatToShow,
                filter, false);
        
        return new DOMTreeWalkerTreeModel(walker);
    }
    
    public String getURL() {
        return url.toString();
    }
    
    public Map<String, Object> getProperties() {
        try {
            return new SpaseMetadataModel().properties( getMetaData( new NullProgressMonitor() ) ); 
        } catch (Exception ex) {
            return Collections.singletonMap( "Exception", (Object)ex );
        } 
    }

    public <T> T getCapability(Class<T> clazz) {
        return null;
    }
    
    
}
