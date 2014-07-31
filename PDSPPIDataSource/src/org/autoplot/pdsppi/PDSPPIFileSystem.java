/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.pdsppi;

import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.das2.util.filesystem.FSTreeModel;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.WebFileSystem;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.DataSourceUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
public class PDSPPIFileSystem extends WebFileSystem {

    public PDSPPIFileSystem( String s ) throws URISyntaxException {
        super( new URI("http://ppi.pds.nasa.gov/"+s ), new File( FileSystem.settings().getLocalCacheDir(), "/PDSPPI/tmp/") );
        root= root + s;
    }
    
    //http://ppi.pds.nasa.gov/ditdos/inventory?sc=Cassini&facet=SPACECRAFT_NAME&title=Cassini&o=txt
    //http://draft-pdsppi.igpp.ucla.edu:8080/ditdos/inventory?t=Venus&facet=TARGET_NAME&title=Venus&o=txt
    //http://ppi.pds.nasa.gov/ditdos/inventory?sc=Galileo&facet=SPACECRAFT_NAME&title=Cassini&o=txt
    private String root= "http://ppi.pds.nasa.gov/writeFileSYS?id=pds:/";
    
    @Override
    protected void downloadFile(String filename, File f, File partfile, ProgressMonitor monitor) throws IOException {
        System.err.println("download file "+filename );
    }

    @Override
    public boolean isDirectory(String filename) throws IOException {
        return filename.endsWith("/");
    }

    @Override
    public String[] listDirectory(String directory) throws IOException {
        String s= root + directory;
        URL url= new URL(s);
        InputStream fin;
            
        Document document;
        
        try {
            logger.log(Level.FINE, "opening {0}", url);
            fin= url.openStream();

            InputSource source = new InputSource( fin );
            
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            document = builder.parse(source); 
            
            XPathFactory xpf= DataSourceUtil.getXPathFactory();
            XPath xp = xpf.newXPath();
            
            
            List<String> result= new ArrayList();
            
            NodeList nodes = (NodeList) xp.evaluate( "/tree/node", document, XPathConstants.NODESET );
            if ( nodes==null ) {
                return new String[0];
            } else {
                for ( int i=0; i<nodes.getLength(); i++ ) {
                    Element node= (Element)nodes.item(i);
                    result.add( node.getAttribute("term") + "/" );
                }
            }
            
            nodes = (NodeList) xp.evaluate( "/tree/leaf", document, XPathConstants.NODESET );
            if ( nodes==null ) {
                return new String[0];
            } else {
                for ( int i=0; i<nodes.getLength(); i++ ) {
                    Element node= (Element)nodes.item(i);
                    result.add( node.getAttribute("term") );
                }
            }
            String[] listing=result.toArray(new String[result.size()]);
           
            return listing;
            
        } catch ( IOException ex ){
            logger.log( Level.WARNING, ex.getMessage(), ex );
            throw new RuntimeException(ex);
        } catch (ParserConfigurationException ex) {
            logger.log( Level.WARNING, ex.getMessage(), ex );
            throw new RuntimeException(ex);
        } catch (SAXException ex) {
            logger.log( Level.WARNING, ex.getMessage(), ex );
            throw new RuntimeException(ex);
        } catch (XPathExpressionException ex) {
            logger.log( Level.WARNING, ex.getMessage(), ex );
            throw new RuntimeException(ex);
        }
    }
    
    public static void main( String[] args )throws Exception {
        PDSPPIFileSystem fs= new PDSPPIFileSystem("/PPI/CO-V_E_J_S_SS-RPWS-2-REFDR-ALL-V1.0/");
        //String[] ff= fs.listDirectory("/PPI/CO-V_E_J_S_SS-RPWS-2-REFDR-ALL-V1.0/DATA/RPWS_RAW_COMPLETE/");
        String[] ff= fs.listDirectory("/");
        for ( String s: ff ) {
            System.err.println(s);
        }
        //FileSystem fs= FileSystem.create("file:///Users/jbf/tmp/");
        JTree mytree= new JTree( new FSTreeModel(fs) );
        mytree.setMinimumSize( new Dimension(400,400) );
        mytree.setPreferredSize( new Dimension(400,400) );
        JOptionPane.showMessageDialog( null, mytree, "Test FSTREE", JOptionPane.INFORMATION_MESSAGE );
    }
    
}
