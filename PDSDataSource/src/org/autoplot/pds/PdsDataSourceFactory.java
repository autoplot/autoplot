
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
public class PdsDataSourceFactory extends AbstractDataSourceFactory {

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

    private DataObject getDataObject( URL url, String name ) throws MalformedURLException, ParseException, Exception {
        
        //Label label = Label.open( f ); // this doesn't work.
        Label label = Label.open( url ); // this works
        
        for ( TableObject t : label.getObjects( TableObject.class) ) {
            
            for ( FieldDescription fd: t.getFields() ) {
                if ( name.startsWith( fd.getName() ) ) {
                    return t;
                }
            }
        }
        
        for ( ArrayObject a: label.getObjects(ArrayObject.class) ) {
            if ( a.getName().equals(name) ) {
                return a;
            }
        }
        return null;
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
                    getDataObject( xmlfile.toURI().toURL(), id );
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
        
        Label label = Label.open( xmlfile.toURI().toURL() ); // this works

        for ( TableObject t : label.getObjects( TableObject.class) ) {
            //TODO: can there be more than one table?
            for ( FieldDescription fd: t.getFields() ) {
                result.put( fd.getName(), fd.getName() + " of a table" );
            }
        }

        DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
        domFactory.setNamespaceAware(false);
        DocumentBuilder builder = domFactory.newDocumentBuilder();
        Document doc = builder.parse(xmlfile);

        for ( ArrayObject a: label.getObjects(ArrayObject.class) ) {
            //result.put( a.getName(), a.getDescription() ); //TODO: update PDS4 library
            String n= a.getName();
            XPathExpression xp= XPathFactory.newInstance().newXPath().compile(
                "//Product_Observational/File_Area_Observational/Array[name='"+n+"']");
            org.w3c.dom.Node n1= (org.w3c.dom.Node)xp.evaluate(doc,XPathConstants.NODE);
            XPathExpression xp2= XPathFactory.newInstance().newXPath().compile(
                "Axis_Array/axis_name/text()");

            org.w3c.dom.NodeList nn= (org.w3c.dom.NodeList)xp2.evaluate(n1,XPathConstants.NODESET);
            String[] ss= new String[nn.getLength()];
            for ( int i=0; i<ss.length; i++ ) {
                ss[i]= nn.item(i).getTextContent();
            }
            result.put( a.getName(), a.getName() + " ("+ String.join(",", ss)+")");
        }

        return result;
    }
    
    protected static URL getFileResource( URL labelFile, ProgressMonitor mon ) 
            throws IOException, URISyntaxException, ParserConfigurationException, SAXException, XPathExpressionException, PDSException {
        String suri= fromUri( labelFile.toURI() );
        File file = DataSetURI.getFile(suri,mon);
        File xmlfile= file;
        DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
        domFactory.setNamespaceAware(false);
        DocumentBuilder builder = domFactory.newDocumentBuilder();
        Document doc = builder.parse(xmlfile);

        XPathExpression xp= XPathFactory.newInstance().newXPath().compile(
                "//Product_Observational/File_Area_Observational/File/file_name/text()");
        String fname= (String)xp.evaluate( doc, XPathConstants.STRING );

        if ( fname.length()==0 ) {
            throw new IllegalArgumentException("file name is empty or not found at "+
                    "//Product_Observational/File_Area_Observational/File/file_name/text()");
        }
        URL fnameUrl= new URL( labelFile, fname );
        return fnameUrl;
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
            try {
                result = getDataObjectNames(xmlfile.toURI().toURL(), mon);
            } catch ( java.lang.NoClassDefFoundError ex ) {
                result= Collections.singletonMap( "Java 8 needed", "Must run under Java 8, or use dmg, exe, deb or rpm" );
            }
            
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

                Map<String,String> result;
                try {
                    result = getDataObjectNames(cc.resourceURI.toURL(), mon);
                } catch ( java.lang.NoClassDefFoundError ex ) {
                    result= Collections.singletonMap( "Java 8 needed", "Must run under Java 8, or use dmg, exe, deb or rpm" );
                }
            
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
