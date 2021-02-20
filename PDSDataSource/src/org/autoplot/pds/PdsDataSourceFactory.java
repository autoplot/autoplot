
package org.autoplot.pds;

import gov.nasa.pds.label.Label;
import gov.nasa.pds.label.object.ArrayObject;
import gov.nasa.pds.label.object.DataObject;
import gov.nasa.pds.label.object.FieldDescription;
import gov.nasa.pds.label.object.TableObject;
import gov.nasa.pds.objectAccess.ParseException;
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
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
public class PdsDataSourceFactory extends AbstractDataSourceFactory {

    private static Logger logger= LoggerManager.getLogger("apdss.pds");
    
    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new PdsDataSource( uri );
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
            
            URL fileUrl= getFileResource( split.resourceUri.toURL(), mon );
            File xmlfile = DataSetURI.getFile( split.resourceUri.toURL() ,new NullProgressMonitor());
            File datfile = DataSetURI.getFile(fileUrl,mon );
            
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
        } catch (IOException | URISyntaxException | ParserConfigurationException | SAXException | XPathExpressionException ex) {
            Logger.getLogger(PdsDataSourceFactory.class.getName()).log(Level.SEVERE, null, ex);
            return true;
        }
    }

    /**
     * return a list of parameters.
     * @param f
     * @return 
     */
    private Map<String,String> getDataObjectNames( URL url ) throws Exception {
        Map<String,String> result= new LinkedHashMap<>();
        
        Label label = Label.open( url ); // this works
        
        for ( TableObject t : label.getObjects( TableObject.class) ) {
            //TODO: can there be more than one table?
            for ( FieldDescription fd: t.getFields() ) {
                result.put( fd.getName(), fd.getName() );
            }
        }
        
        for ( ArrayObject a: label.getObjects(ArrayObject.class) ) {
            result.put( a.getName(), a.getName() );
        }
        
        return result;
    }
    
    protected static URL getFileResource( URL labelFile, ProgressMonitor mon ) throws IOException, URISyntaxException, ParserConfigurationException, SAXException, XPathExpressionException {
        String suri= fromUri( labelFile.toURI() );
        File xmlfile = DataSetURI.getFile(suri,mon);
        DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
        domFactory.setNamespaceAware(false);
        DocumentBuilder builder = domFactory.newDocumentBuilder();
        Document doc = builder.parse(xmlfile);
        
        XPathExpression xp= XPathFactory.newInstance().newXPath().compile("//Product_Observational/File_Area_Observational/File/file_name/text()");
        String fname= (String)xp.evaluate( doc, XPathConstants.STRING );
        
        if ( fname.length()==0 ) {
            throw new IllegalArgumentException("file name is empty / not found");
        }
        URL fnameUrl= new URL( labelFile, fname );
        return fnameUrl;
    }
    
    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        if ( cc.context.equals(CompletionContext.CONTEXT_PARAMETER_NAME) ) {
            
            logger.log(Level.FINE, "getCompletions {0}", cc.resourceURI);
            
            URL fileUrl= getFileResource( cc.resourceURI.toURL(), mon );
            File xmlfile = DataSetURI.getFile(cc.resourceURI.toURL(),new NullProgressMonitor());
            File datfile = DataSetURI.getFile(fileUrl,mon );
             
            Map<String,String> result= getDataObjectNames(xmlfile.toURI().toURL());
            
            List<CompletionContext> ccresult= new ArrayList<>();
            for ( java.util.Map.Entry<String,String> e:result.entrySet() ) {
                String key= e.getKey();
                CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, key, this, "arg_0", e.getValue(), null, true );
                ccresult.add(cc1);
            }
            
            return ccresult;
            
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            String parmname= CompletionContext.get( CompletionContext.CONTEXT_PARAMETER_NAME, cc );
            if ( parmname.equals("id") || parmname.equals("x") || parmname.equals("y") ) {
                
                Map<String,String> result= getDataObjectNames(cc.resourceURI.toURL());
            
                List<CompletionContext> ccresult= new ArrayList<>();
                for ( java.util.Map.Entry<String,String> e:result.entrySet() ) {
                    String key= e.getKey();
                    CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, key, this, "arg_0", e.getValue(), null, true );
                    ccresult.add(cc1);
                }
                
                return ccresult;
            }                

        }
        
        return Collections.emptyList();        
        
    }
    
    
    
    
}
