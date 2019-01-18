
package org.autoplot.pdsppi;

import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FSTreeModel;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.WebFileSystem;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.DataSourceUtil;
import org.das2.util.filesystem.HttpUtil;
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

    private static final Logger logger= LoggerManager.getLogger("apdss.pdsppi");
    
    public PDSPPIFileSystem( String s ) throws URISyntaxException {
        // the following URI is ignored.
        super( new URI(PDSPPIDB.PDSPPI+s ), new File( FileSystem.settings().getLocalCacheDir(), "/PDSPPI/tmp/") );
        if ( !s.startsWith("/") ) {
            root= root + "/" + s;
        } else {
            root= root + s;
        }
    }
    
    //http://pds-ppi.igpp.ucla.edu/ditdos/inventory?sc=Cassini&facet=SPACECRAFT_NAME&title=Cassini&o=txt
    //http://pds-ppi.igpp.ucla.edu/ditdos/inventory?t=Venus&facet=TARGET_NAME&title=Venus&o=txt
    //http://pds-ppi.igpp.ucla.edu/ditdos/inventory?sc=Galileo&facet=SPACECRAFT_NAME&title=Cassini&o=txt
    private String root= PDSPPIDB.PDSPPI+"ditdos/view?id=pds:/";
    
    @Override
    protected void downloadFile(String filename, File f, File partfile, ProgressMonitor monitor) throws IOException {
        logger.log(Level.WARNING, "download file {0}", filename);
    }

    @Override
    public boolean isDirectory(String filename) throws IOException {
        return filename.endsWith("/");
    }
    
    @Override
    public String[] listDirectory(String directory) throws IOException {
        
        DirectoryEntry[] cached= listDirectoryFromMemory(directory);
        if ( cached!=null ) {
            return FileSystem.getListing( cached );
        }
        
        boolean noTimes= true;  // turn of time filtering until we figure out cause of delays.
        String noTimeString= "";
        if ( noTimes ) {
            noTimeString= "&times=false";
        }
                
        URL url;
        if ( !directory.startsWith("/") ) {
            url= new URL( root + "/"+directory + noTimeString );
        } else {
            url= new URL( root + directory + noTimeString );
        }
        InputStream fin;
            
        Document document;
        
        try {
            logger.log(Level.FINE, "listDirectory {0}", url);
            loggerUrl.log(Level.FINE,"GET to get data {0}", url);
            URLConnection connect= url.openConnection();
            connect= HttpUtil.checkRedirect(connect);
            connect.connect();
            //if ( !connect.getContentType().equals("text/xml") ) {  //TODO: work with Todd to get response headers
            //    throw new IOException("bad request: "+url);
            //}
            fin= connect.getInputStream();
            PushbackInputStream pbin= new PushbackInputStream(fin);
            byte[] peek= new byte[1];
            int bytesRead= 0;
            while ( bytesRead<1 ) {
                int ch= pbin.read();
                if (ch>=0 ) {
                    peek[bytesRead]= (byte)ch;
                }
                bytesRead+=1;
            }
            if ( ! ( new String(peek).equals("<") ) ) { // I was having a heck of a time with 4 characters...
                pbin.unread(peek);
                BufferedReader read= new BufferedReader( new InputStreamReader(pbin) );
                String s= read.readLine();
                pbin.close();
                throw new IOException( "\"" + s + "\" from "+url );
            } else {
                pbin.unread(peek);
            }
            InputSource source = new InputSource( pbin );
            
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
            Arrays.sort(listing);
           
            DirectoryEntry[] des= new DirectoryEntry[listing.length];
            for ( int i=0; i<des.length; i++ ) {
                DirectoryEntry des1= new DirectoryEntry();
                des1.name= listing[i];
                des1.modified= 0;
                des1.size= 0;
                des1.type= listing[i].endsWith("/") ? 'd' : 'f';
                des[i]= des1;
            }
            cacheListing( directory, des );
            
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
