
package org.autoplot.pds;

import gov.nasa.pds.label.Label;
import gov.nasa.pds.label.object.ArrayObject;
import gov.nasa.pds.label.object.DataObject;
import gov.nasa.pds.label.object.FieldDescription;
import gov.nasa.pds.label.object.TableObject;
import gov.nasa.pds.objectAccess.ParseException;
import gov.nasa.pds.ppi.label.PDSException;
import gov.nasa.pds.ppi.label.PDSLabel;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
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
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
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
 *
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

            
    protected static PDS3DataObject getDataObjectPds3( URL url, String name ) throws IOException, PDSException, XPathExpressionException {

        File f= DataSetURI.getFile( url, new NullProgressMonitor() );
            
        PDSLabel label = new PDSLabel();
        Document doc;
        if ( !label.parse( f.toPath() ) ) {
            throw new IllegalArgumentException("unable to use file "+url);
        }
        doc= label.getDocument();
        XPathFactory factory = XPathFactory.newInstance();
            
        XPath xpath = factory.newXPath();
        
        Node table= (Node) xpath.evaluate(String.format("/LABEL/TABLE[1]",name),doc,XPathConstants.NODE);
        Node column= (Node) xpath.evaluate(String.format("/LABEL/TABLE/COLUMN[NAME='%s']",name),doc,XPathConstants.NODE);
        if ( table==null ) {
            table= (Node) xpath.evaluate(String.format("/LABEL/BINARY_TABLE[1]",name),doc,XPathConstants.NODE);
            column= (Node) xpath.evaluate(String.format("/LABEL/BINARY_TABLE/COLUMN[NAME='%s']",name),doc,XPathConstants.NODE);
        }
        
        PDS3DataObject obj= new PDS3DataObject( doc.getDocumentElement(), table,column);
        
        //obj.description=  (String)xpath.evaluate("/DESCRIPTION/text()",dat,XPathConstants.STRING);
        System.err.println("dat="+column);
        
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

            File xmlfile = DataSetURI.getFile( split.resourceUri.toURL() ,new NullProgressMonitor());
            URL fileUrl;
            try {
                fileUrl = getFileResource( split.resourceUri.toURL(), mon );
            } catch ( IOException | URISyntaxException | ParserConfigurationException | XPathExpressionException | SAXException ex ) {
                problems.add("uri should point to xml or lblx file");
                return true;
            }
            DataSetURI.getFile(fileUrl,mon );

            if ( id==null ) {
                return true;
            } else {
                try {
                    getDataObjectPds3( xmlfile.toURI().toURL(), id );
                    return false;
                } catch ( Exception ex ) {
                    problems.add(ex.getMessage());
                    return true;
                }
            }
        } catch ( IOException | IllegalArgumentException | PDSException ex ) {
            logger.log(Level.SEVERE, null, ex);
            problems.add(ex.getMessage());
            return false;
        }
    }

    /**
     * return a list of parameters.
     * @param f
     * @return 
     */
    private Map<String,String> getDataObjectNames( URL url, ProgressMonitor mon) throws Exception {
        
        URL fileUrl= getFileResource( url, mon );
        File xmlfile = DataSetURI.getFile( url,new NullProgressMonitor());
        DataSetURI.getFile( fileUrl,mon );
            
        Map<String,String> result= new LinkedHashMap<>();
        
        File labelfile= xmlfile;
        PDSLabel label = new PDSLabel();
        Document doc;
        if ( !label.parse( labelfile.toString() ) ) {
            throw new IllegalArgumentException("unable to use file "+labelfile);
        }
        doc= label.getDocument();
        label.printXML( new PrintStream("/tmp/ap/DS1-C-PEPE-2-EDR-BORRELLY-V1.0.xml") );
        
        XPathFactory factory = XPathFactory.newInstance();
        XPath xpath = factory.newXPath();
        NodeList dat= (NodeList) xpath.evaluate("/LABEL/TABLE/COLUMN/NAME/text()",doc,XPathConstants.NODESET);

        if ( dat.getLength()==0 ) {
            dat= (NodeList) xpath.evaluate("/LABEL/BINARY_TABLE/COLUMN/NAME/text()",doc,XPathConstants.NODESET);
        }
        for ( int i=0; i<dat.getLength(); i++ ) {
            Node n= dat.item(i);
            String name= n.getTextContent();
            result.put( name, name );
        }
        return result;

    }
    
    protected static URL getFileResource( URL labelFile, ProgressMonitor mon ) 
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
        return new URL( labelFile, dat.item(0).getNodeValue() );
        //<POINTER object="TABLE">JAD_L50_LRS_ELC_ANY_DEF_2016240_V01.DAT</POINTER>

    }
    
    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        if ( cc.context.equals(CompletionContext.CONTEXT_PARAMETER_NAME) ) {
            
            logger.log(Level.FINE, "getCompletions {0}", cc.resourceURI);
            
            File xmlfile = DataSetURI.getFile(cc.resourceURI.toURL(),new NullProgressMonitor());
            
            URL fileUrl;
            try {
                fileUrl = getFileResource( cc.resourceURI.toURL(), mon );
            } catch ( IOException | URISyntaxException | ParserConfigurationException | XPathExpressionException | SAXException ex ) {
                List<CompletionContext> ccresult= new ArrayList<>();
                ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "point to the xml or lblx file" ) );
                return ccresult;
            }
            DataSetURI.getFile(fileUrl,mon );
             
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
