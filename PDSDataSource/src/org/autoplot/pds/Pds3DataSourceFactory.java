
package org.autoplot.pds;

import gov.nasa.pds.ppi.label.PDSException;
import gov.nasa.pds.ppi.label.PDSLabel;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSetURI;
import static org.autoplot.datasource.DataSetURI.fromUri;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.URISplit;
import org.das2.datum.LoggerManager;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Factory for resolving PDS3 URIs.
 * @author jbf
 */
public class Pds3DataSourceFactory extends AbstractDataSourceFactory {

    private static Logger logger= LoggerManager.getLogger("apdss.pds");
    
    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        URISplit split= URISplit.parse(uri);
        if ( split.file.toLowerCase().endsWith(".lbl") ) {
            return new Pds3DataSource( uri );
        } else {
            return new PdsDataSource( uri );
        }
    }


    /**
     * return information about how this data is stored in PDS3DataObject.
     * @param url URL of label
     * @param name the object name
     * @return
     * @throws IOException
     * @throws PDSException
     * @throws XPathExpressionException 
     */
    protected static PDS3DataObject getDataObjectPds3( URL url, String name ) throws IOException, PDSException, XPathExpressionException {

        Document doc;
        doc= getDocumentWithImports(url);

        XPathFactory factory = XPathFactory.newInstance();    
        XPath xpath = factory.newXPath();

        int lineOffset=1;
        
        Node table= (Node) xpath.evaluate(String.format("/LABEL/TABLE[1]",name),doc,XPathConstants.NODE);
        Node column= (Node) xpath.evaluate(String.format("/LABEL/TABLE[1]/COLUMN[NAME='%s']",name),doc,XPathConstants.NODE);

        if ( table==null || column==null) {
            table= (Node) xpath.evaluate(String.format("/LABEL/BINARY_TABLE[1]",name),doc,XPathConstants.NODE);
            column= (Node) xpath.evaluate(String.format("/LABEL/BINARY_TABLE[1]/COLUMN[NAME='%s']",name),doc,XPathConstants.NODE);
            if ( table!=null ) {
                String pointer= (String)xpath.evaluate("/LABEL/POINTER[@object='BINARY_TABLE']/text()",doc,XPathConstants.STRING);
                if ( pointer!=null && pointer.length()>0 ) {
                    FilePointer p= new FilePointer(url,pointer);
                    lineOffset= p.getOffset();
                }
            }
        }

        if ( table==null || column==null) {
            table= (Node) xpath.evaluate(String.format("/LABEL/ASCII_TABLE[1]",name),doc,XPathConstants.NODE);
            column= (Node) xpath.evaluate(String.format("/LABEL/ASCII_TABLE[1]/COLUMN[NAME='%s']",name),doc,XPathConstants.NODE);
            if ( table!=null ) {
                String pointer= (String)xpath.evaluate("/LABEL/POINTER[@object='ASCII_TABLE']/text()",doc,XPathConstants.STRING);
                if ( pointer!=null && pointer.length()>0 ) {
                    FilePointer p= new FilePointer(url,pointer);
                    lineOffset= p.getOffset();
                }
            }
        }

        if ( table==null || column==null ) {
            table= (Node) xpath.evaluate(String.format("/LABEL/TIME_SERIES[1]",name),doc,XPathConstants.NODE);
            column= (Node) xpath.evaluate(String.format("/LABEL/TIME_SERIES[1]/COLUMN[NAME='%s']",name),doc,XPathConstants.NODE); 
            if ( table!=null ) {
                String pointer= (String)xpath.evaluate("/LABEL/POINTER[@object='TIME_SERIES']/text()",doc,XPathConstants.STRING);
                if ( pointer!=null && pointer.length()>0 ) {
                    FilePointer p= new FilePointer(url,pointer);
                    lineOffset= p.getOffset();
                }
            }
        }
        
        if ( table==null ) {
            throw new IllegalArgumentException("Unable to find table" );
        }
        if ( column==null ) {
            throw new IllegalArgumentException("Unable to find column: "+name );
        }
        PDS3DataObject obj= new PDS3DataObject( doc.getDocumentElement(), table,column);
        obj.setFileOffset( lineOffset );
        //obj.description=  (String)xpath.evaluate("/DESCRIPTION/text()",dat,XPathConstants.STRING);
        
        return obj;
        
    }

    @Override
    public boolean reject(String suri, List<String> problems, ProgressMonitor mon) {
        try {
            URISplit split= URISplit.parse( suri );
            Map<String,String> params= URISplit.parseParams(split.params);

            String id= params.get("arg_0");
            if ( id==null ) id= params.get("id");
            if ( id==null ) id= params.get("X");
            if ( id==null ) id= params.get("Y");
            if ( id==null ) id= params.get("Z");

            FilePointer filePointer;
            File xmlfile = DataSetURI.getFile( split.resourceUri.toURL() ,new NullProgressMonitor());
            try {
                filePointer= getFileResource( split.resourceUri.toURL(), mon);
            } catch ( IOException | URISyntaxException | ParserConfigurationException | XPathExpressionException | SAXException ex ) {
                problems.add("uri should point to xml or lblx file");
                return true;
            }
            DataSetURI.getFile(filePointer.getUrl(),mon );

            if ( id==null ) {
                return true;
            } else {
                try {
                    getDataObjectPds3( split.resourceUri.toURL(), id ); // note local copy
                    return false;
                } catch ( Exception ex ) {
                    problems.add(ex.getMessage());
                    return true;
                }
            }
        } catch ( IOException | IllegalArgumentException | PDSException ex ) {
            logger.log(Level.SEVERE, null, ex);
            problems.add(ex.getMessage());
            return true;
        }
    }

    /**
     * read in the PDS label, resolving STRUCTURES which are loaded with a pointer.
     * @param labelUrl
     * @return
     * @throws IOException
     * @throws PDSException 
     */
    protected static Document getDocumentWithImports( URL labelUrl ) throws IOException, PDSException {
        
        System.err.println("labelUrl: "+ labelUrl);
        
        File xmlfile = DataSetURI.getFile( labelUrl,new NullProgressMonitor());

        PDSLabel label = new PDSLabel(); 
        
        if ( !label.parse( xmlfile.getPath() ) ) {
            throw new IllegalArgumentException("unable to use file "+labelUrl);
        }
        Document doc;
        doc= label.getDocument();
         
        XPathFactory factory = XPathFactory.newInstance();
        XPath xpath = factory.newXPath();
        
        // check for pointers to STRUCTURE, where we will import the content of the file.
        NodeList structures;
        try {
            structures = (NodeList) xpath.evaluate("/LABEL/*/POINTER[@object=\"STRUCTURE\"]",doc,XPathConstants.NODESET);
        } catch (XPathExpressionException ex) {
            throw new RuntimeException(ex);
        }
        for ( int i=0; i<structures.getLength(); i++ ) {
            Node child= structures.item(i);
            Node parent= child.getParentNode();
            
            URL childUrl= new URL( labelUrl, child.getTextContent() );
            File childfile = DataSetURI.getFile( childUrl,new NullProgressMonitor());
            
            PDSLabel label2 = new PDSLabel();
            label2.parse(childfile.toPath());
            Document doc2= label2.getDocument();
            Node newChild= doc2.getDocumentElement();
            NodeList importKids= newChild.getChildNodes();
            for ( int j=0; j<importKids.getLength(); j++ ) {
                Node kid= importKids.item(j);
                doc.adoptNode(kid);
                parent.insertBefore(kid, child);
            }
        }
        //DocumentUtil.dumpToXML( doc, new File("/tmp/ap/label-with-imports.xml") );
        return doc;
    }
    
    /**
     * return a list of parameters.
     * @param f
     * @return 
     */
    private Map<String,String> getDataObjectNames( URL url, ProgressMonitor mon) throws Exception {
        
        Map<String,String> result= new LinkedHashMap<>();
        
        Document doc= getDocumentWithImports(url);
        
        XPathFactory factory = XPathFactory.newInstance();
        XPath xpath = factory.newXPath();
        
        NodeList dat= (NodeList) xpath.evaluate("/LABEL/TABLE/COLUMN/NAME/text()",doc,XPathConstants.NODESET);
        if ( dat.getLength()==0 ) {
            dat= (NodeList) xpath.evaluate("/LABEL/BINARY_TABLE/COLUMN/NAME/text()",doc,XPathConstants.NODESET);
        }
        if ( dat.getLength()==0 ) {
            dat= (NodeList) xpath.evaluate("/LABEL/ASCII_TABLE/COLUMN/NAME/text()",doc,XPathConstants.NODESET);
        }
        if ( dat.getLength()==0 ) {
            dat= (NodeList) xpath.evaluate("/LABEL/TIME_SERIES/COLUMN/NAME/text()",doc,XPathConstants.NODESET);
        }
        
        for ( int i=0; i<dat.getLength(); i++ ) {
            Node n= dat.item(i);
            String name= n.getTextContent();
            result.put( name, name );
        }
        return result;

    }
    
    protected static FilePointer getFileResource( URL labelFile, ProgressMonitor mon ) 
            throws IOException, URISyntaxException, ParserConfigurationException, SAXException, XPathExpressionException, PDSException {
        String suri= fromUri( labelFile.toURI() );
        File file = DataSetURI.getFile(suri,mon);
        File labelfile= file;
        PDSLabel label = new PDSLabel();
        Document doc;
        if ( !label.parse( labelfile.toString() ) ) {
            throw new IllegalArgumentException("unable to use file "+labelFile);
        }
        doc= label.getDocument();
        XPathFactory factory = XPathFactory.newInstance();
        XPath xpath = factory.newXPath();
        NodeList dat= (NodeList) xpath.evaluate("/LABEL/POINTER/text()",doc,XPathConstants.NODESET);
        String f= dat.item(0).getNodeValue();
        FilePointer result= new FilePointer(labelFile,f);
        return result;
        //<POINTER object="TABLE">JAD_L50_LRS_ELC_ANY_DEF_2016240_V01.DAT</POINTER>

    }
    
    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        if ( cc.context.equals(CompletionContext.CONTEXT_PARAMETER_NAME) ) {
            
            logger.log(Level.FINE, "getCompletions {0}", cc.resourceURI);
            
            File xmlfile = DataSetURI.getFile(cc.resourceURI.toURL(),new NullProgressMonitor());
             
            Map<String,String> result;
            result = getDataObjectNames(xmlfile.toURI().toURL(), mon);
            
            List<CompletionContext> ccresult= new ArrayList<>();
            ccresult.add( new CompletionContext( 
                    CompletionContext.CONTEXT_PARAMETER_NAME, 
                    "", 
                    this, "arg_0", 
                    "Select parameter to plot", "", false ) );
            for ( java.util.Map.Entry<String,String> e:result.entrySet() ) {
                String key= e.getKey();
                String desc= e.getValue();
                CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, 
                        key, this, "arg_0", desc, "", true );
                ccresult.add(cc1);
            }
            ccresult.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "X=",
                    "values typically displayed in horizontal dimension"));
            ccresult.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "Y=",
                    "values typically displayed in vertical dimension"));
            ccresult.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "Z=",
                    "values typically color coded"));
            
            
            return ccresult;
            
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            String parmname= CompletionContext.get( CompletionContext.CONTEXT_PARAMETER_NAME, cc );
            if ( parmname.equals("id") || parmname.equals("X") || parmname.equals("Y") || parmname.equals("Z") ) {

                Map<String,String> result= getDataObjectNames(cc.resourceURI.toURL(), mon);
            
                List<CompletionContext> ccresult= new ArrayList<>();
                ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "", this, "arg_0", 
                        "", null, true ) );
                for ( java.util.Map.Entry<String,String> e:result.entrySet() ) {
                    String key= e.getKey();
                    String desc= e.getValue();
                    CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, key, this, "arg_0", 
                            desc, null, true );
                    ccresult.add(cc1);
                }
                
                return ccresult;
            }                

        }
        
        return Collections.emptyList();        
        
    }
    
}
