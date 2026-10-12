
package org.autoplot.pds;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
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
import org.autoplot.pds4.Pds4Reader;
import org.autoplot.pds4.Pds4Reader.DataObject;
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
            return new Pds4DataSource( uri );
        }
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
                    //Pds4DataSource.getDataObject( xmlfile.toURI().toURL(), id ); // TODO
                    return false;
                } catch ( Exception ex ) {
                    problems.add(ex.getMessage());
                    return true;
                }
            }
        } catch ( IOException | IllegalArgumentException ex ) {
            logger.log(Level.SEVERE, null, ex);
            problems.add(ex.getMessage());
            return false;
        }
    }

    
    protected static URL getFileResource( URL labelFile, ProgressMonitor mon ) 
            throws IOException, URISyntaxException, ParserConfigurationException, SAXException, XPathExpressionException {
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
                result = Pds4DataSource.getDataObjectNames(xmlfile.toURI().toURL(), mon);
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
                    result = Pds4DataSource.getDataObjectNames(cc.resourceURI.toURL(), mon);
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
