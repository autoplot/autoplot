
package org.autoplot.pds;

import gov.nasa.pds.ppi.label.PDSException;
import gov.nasa.pds.ppi.label.PDSLabel;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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
     * @return PDS3DataObject
     * @throws IOException
     * @throws PDSException
     * @throws XPathExpressionException 
     */
    public static PDS3DataObject getDataObjectPds3( URL url, String name ) throws IOException, PDSException, XPathExpressionException {

        Document doc;
        doc= getDocumentWithImports(url);

        XPathFactory factory = XPathFactory.newInstance();    
        XPath xpath = factory.newXPath();
        
        Node table= (Node) xpath.evaluate(String.format("/LABEL/TABLE[1]",name),doc,XPathConstants.NODE);
        Node column= (Node) xpath.evaluate(String.format("/LABEL/TABLE[1]/COLUMN[NAME='%s']",name),doc,XPathConstants.NODE);

        FilePointer p= null;
        
        if ( table==null || column==null) {
            table= (Node) xpath.evaluate(String.format("/LABEL/*[contains(name(),'_TABLE')][1]",name),doc,XPathConstants.NODE);
            column= (Node) xpath.evaluate(String.format("/LABEL/*[contains(name(),'_TABLE')]/COLUMN[NAME='%s']",name),doc,XPathConstants.NODE);
            if ( table!=null ) {
                String tableName= table.getNodeName();
                String pointer= (String)xpath.evaluate("/LABEL/POINTER[@object='"+tableName+"']/text()",doc,XPathConstants.STRING);
                if ( pointer!=null && pointer.length()>0 ) {
                    p= new FilePointer(url,pointer);
                }
            }
        }
        
        if ( table==null || column==null) {
            table= (Node) xpath.evaluate(String.format("/LABEL/BINARY_TABLE[1]",name),doc,XPathConstants.NODE);
            column= (Node) xpath.evaluate(String.format("/LABEL/BINARY_TABLE[1]/COLUMN[NAME='%s']",name),doc,XPathConstants.NODE);
            if ( table!=null ) {
                String pointer= (String)xpath.evaluate("/LABEL/POINTER[@object='BINARY_TABLE']/text()",doc,XPathConstants.STRING);
                if ( pointer!=null && pointer.length()>0 ) {
                    p= new FilePointer(url,pointer);
                }
            }
        }

        if ( table==null || column==null) {
            table= (Node) xpath.evaluate(String.format("/LABEL/ASCII_TABLE[1]",name),doc,XPathConstants.NODE);
            column= (Node) xpath.evaluate(String.format("/LABEL/ASCII_TABLE[1]/COLUMN[NAME='%s']",name),doc,XPathConstants.NODE);
            if ( table!=null ) {
                String pointer= (String)xpath.evaluate("/LABEL/POINTER[@object='ASCII_TABLE']/text()",doc,XPathConstants.STRING);
                if ( pointer!=null && pointer.length()>0 ) {
                    p= new FilePointer(url,pointer);
                }
            }
        }

        if ( table==null || column==null ) {
            table= (Node) xpath.evaluate(String.format("/LABEL/TIME_SERIES[1]",name),doc,XPathConstants.NODE);
            column= (Node) xpath.evaluate(String.format("/LABEL/TIME_SERIES[1]/COLUMN[NAME='%s']",name),doc,XPathConstants.NODE); 
            if ( table!=null ) {
                String pointer= (String)xpath.evaluate("/LABEL/POINTER[@object='TIME_SERIES']/text()",doc,XPathConstants.STRING);
                if ( pointer!=null && pointer.length()>0 ) {
                    p= new FilePointer(url,pointer);
                }
            }
        }
        
        if ( table==null || column==null ) { ///LABEL/FILE/SPREADSHEET/FIELD/NAME/text()
            table= (Node) xpath.evaluate(String.format("/LABEL/FILE/SPREADSHEET"),doc,XPathConstants.NODE);
            column= (Node) xpath.evaluate(String.format("/LABEL/FILE/SPREADSHEET/FIELD[NAME='%s']",name),doc,XPathConstants.NODE); 
            if ( table!=null ) {
                String pointer= (String)xpath.evaluate("/LABEL/FILE/POINTER[@object='SPREADSHEET']/text()",doc,XPathConstants.STRING);
                if ( pointer!=null && pointer.length()>0 ) {
                    p= new FilePointer(url,pointer);
                }
            }
        }
        
        if ( table==null || column==null ) { ///LABEL/SPREADSHEET/FIELD/NAME/text()
            table= (Node) xpath.evaluate(String.format("/LABEL/SPREADSHEET"),doc,XPathConstants.NODE);
            column= (Node) xpath.evaluate(String.format("/LABEL/SPREADSHEET/FIELD[NAME='%s']",name),doc,XPathConstants.NODE); 
            if ( table!=null ) {
                String pointer= (String)xpath.evaluate("/LABEL/POINTER[@object='SPREADSHEET']/text()",doc,XPathConstants.STRING);
                if ( pointer!=null && pointer.length()>0 ) {
                    p= new FilePointer(url,pointer);
                }
            }
        }        
        if ( table==null ) {
            // maybe they are high-rank
            table= (Node) xpath.evaluate(String.format("/LABEL/TABLE[1]",name),doc,XPathConstants.NODE);
            if ( table==null ) {            
                throw new IllegalArgumentException("Unable to find table" );
            } else {
                column= (Node) xpath.evaluate(String.format("/LABEL/TABLE[1]/CONTAINER/COLUMN[NAME='%s']",name),doc,XPathConstants.NODE);
                if ( column==null ) {
                    column= (Node) xpath.evaluate(String.format("/LABEL/TABLE[1]/CONTAINER/CONTAINER/COLUMN[NAME='%s']",name),doc,XPathConstants.NODE);
                    if ( column!=null ) {   
                        column= column.getParentNode().getParentNode();
                    }
                } else {
                    column= column.getParentNode();
                }
            }
        }
        
        if ( column==null ) {
            throw new IllegalArgumentException("Unable to find column: "+name );
        }
        PDS3DataObject obj= new PDS3DataObject( doc.getDocumentElement(), table,column);
        obj.setFilePointer( p );
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
                    getDataObjectPds3( split.resourceUri.toURL(), id ); 
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
     * read in the PDS label, resolving STRUCTURES which are loaded with a pointer.  This will look in the current
     * directory, and in the LABEL directory next to the DATA directory.
     * @param labelUrl the URL of the LABEL file
     * @return a document model of the LABEL, with imports resolved.
     * @throws IOException
     * @throws PDSException 
     */    
    public static Document getDocumentWithImports( URL labelUrl ) throws IOException, PDSException {
        return getDocumentWithImports( labelUrl, 0 );
    }
    
    /**
     * identify the root of the volume.This was motivated by a LABL in SOFTWARE in a volume which 
     * didn't have DATA, and I had everything mounted in /project/pds/mirror/DATA.The root will 
     * often have DATA, DOCUMENT, and a README.txt file.
     * 
     * @param location a location within the volume
     * @return the root of the volume or null if it can't be found.
     * @throws org.das2.util.filesystem.FileSystem.FileSystemOfflineException
     * @throws java.net.UnknownHostException
     * @throws java.io.FileNotFoundException
     */
    public static URL identifyRoot( URL location ) throws FileSystem.FileSystemOfflineException, UnknownHostException, FileNotFoundException, IOException {
        String p= location.toString();
        int r= p.lastIndexOf("DATA/");
        FileSystem fs= FileSystem.create( p.substring(0,r) );
        String[] ffs= fs.listDirectory("/");
        for ( String f: ffs ) {
            if ( f.equalsIgnoreCase("document") ) {
                return fs.getRootURI().toURL();
            }
        }
        Pattern patt= Pattern.compile("/cassini-|/clps-|/CO-|/DS1-|/galileo-|/GIO-|/GO-|/go-|/ICE-|/insight-"
                + "|/JNO-|/juno-|/lp-|/LP-|/LRO-|/lunar-|/M10-|/maven-|/mess-|/messenger-|/MEX-|/MGN-|/mgs-|/MR9"
                + "|/MSL-|/NEAR-|/NH-|/ODY-|/P10-|/P11-|/pvo-|/radiojove-|/suisei-|/ULY-|/ulysses-"
                + "|/V15_V16-|/VEGA1-|/VEGA2-|/vex-|/vg1-|/vg2-|/voyager-|/voyager1-|/voyager2-", Pattern.CASE_INSENSITIVE );
        //patt= Pattern.compile("co-",Pattern.CASE_INSENSITIVE);
        Matcher m= patt.matcher(p);
        if ( m.find() ) {
            int i2= p.indexOf("/",m.start()+1);
            return new URL(p.substring(0,i2));
        }
        return null;
    } 
    
    /**
     * read in the PDS label, resolving STRUCTURES which are loaded with a pointer.  This will look in the current
     * directory, and in the LABEL directory next to the DATA directory.  Note the Pointer must appear 
     * at /LABEL/&ast;/POINTER[@object="STRUCTURE"] or /LABEL/POINTER[@object="STRUCTURE"], and
     * Pointer is left in the Document.
     * @param labelUrl the URL of the LABEL file
     * @param depth number of levels deep, this is limited to 4.
     * @return a document model of the LABEL, with imports resolved.
     * @throws IOException
     * @throws PDSException 
     */
    public static Document getDocumentWithImports( URL labelUrl, int depth ) throws IOException, PDSException {
        
        logger.entering( "Pds3DataSourceFactory", "getDocumentWithImports", labelUrl );
        
        if ( depth>4 ) throw new IllegalArgumentException("something has gone terribly wrong, too many nested structures");
        
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
            if ( structures.getLength()==0 ) {
                structures = (NodeList) xpath.evaluate("/LABEL/POINTER[@object=\"STRUCTURE\"]",doc,XPathConstants.NODESET);
            }
        } catch (XPathExpressionException ex) {
            throw new RuntimeException(ex);
        }
        for ( int i=0; i<structures.getLength(); i++ ) {
            Node child= structures.item(i);
            Node parent= child.getParentNode();
            
            URL childUrl= new URL( labelUrl, child.getTextContent() );
            File childfile;
            try {
                childfile= DataSetURI.getFile( childUrl,new NullProgressMonitor());
            } catch ( FileNotFoundException ex ) {
                String p= labelUrl.toString();
                int r= p.lastIndexOf("DATA/");
                childUrl= new URL( new URL( p.substring(0,r) + "LABEL/" ), child.getTextContent() );
                try {
                    childfile= DataSetURI.getFile( childUrl,new NullProgressMonitor());
                } catch ( FileNotFoundException ex2 ) {
                    URL root= identifyRoot(labelUrl);
                    childUrl= new URL( new URL( root.toString() + "/LABEL/" ), child.getTextContent() );
                    try { 
                        childfile= DataSetURI.getFile( childUrl,new NullProgressMonitor());
                    } catch ( FileNotFoundException ex3 ) {
                        throw ex;
                    }
                }
            }
            
            Document doc2= getDocumentWithImports( childUrl, depth+1 );
            Node newChild= doc2.getDocumentElement();
            NodeList importKids= newChild.getChildNodes();
            List<Node> importK= removeComments( importKids );
            for ( int j=0; j<importK.size(); j++ ) {
                Node kid= importK.get(j);
                doc.adoptNode(kid);
                parent.insertBefore(kid, child);
            }
        }
        //DocumentUtil.dumpToXML( doc, new File("/tmp/ap/label-with-imports.xml") );
        logger.exiting( "Pds3DataSourceFactory", "getDocumentWithImports" );
        return doc;
    }
    
    /**
     * shorten the description to about a sentence by removing the first 
     * sentence (presumed to be a summary) or the first 80 characters.
     * @param desc
     * @return 
     */
    private String summarizeDescription( String desc ) {
        int i= desc.indexOf(".");
        int l= desc.length();
        int limit=86;
        if ( i==-1 ) {
            if ( l>limit ) {
                i= desc.lastIndexOf(" ",limit);
                if ( i==-1 ) i=limit;
                desc= desc.substring(0,i)+"...";
            } 
        } else {
            if ( i>limit ) {
                i= desc.lastIndexOf(" ",limit);
                if ( i==-1 ) i=limit;
                desc= desc.substring(0,i)+"...";
            } else {
                desc= desc.substring(0,i+1);
            }
        }
        desc= String.join(" ",desc.split("[\\s|\\&\\#13\\;]+"));
        return desc;
    }
    
    private static List appendNodeList( List nl1, NodeList nodeList2 ) {
        for (int i = 0; i < nodeList2.getLength(); i++) {
            Node node = nodeList2.item(i);
            Node copiedNode = node.cloneNode(true);
            nl1.add(copiedNode);
        }
        return nl1;
    }
    
    private static List removeComments(NodeList nodeList2) {
        List<Node> result= new ArrayList<>();
        for (int i = 0; i < nodeList2.getLength(); i++) {
            Node node = nodeList2.item(i);
            if ( node.getNodeName().equals("COLUMN") ) {
                result.add(node);
            }
        }
        return result;
    }
    
    
    /**
     * return a list of parameters and their summarized descriptions.  The
     * summaries are the first sentence of the description.  The description
     * will be HTML and will be formatted properly when used with JLabels, etc.
     * @param url the LBL file location.
     * @param mon a monitor.
     * @return a map from id to description of the id.  
     */
    private Map<String,String> getDataObjectNames( URL url, ProgressMonitor mon) throws Exception {
        
        Map<String,String> result= new LinkedHashMap<>();
        
        Document doc= getDocumentWithImports(url);
        //String s= url.getFile();
        //int iss=s.lastIndexOf("/");
        //s= s.substring(iss+1,s.length()-4);        
        //DocumentUtil.dumpToXML( doc, new File("/tmp/ap/"+s + ".xml" ) );
        
        XPathFactory factory = XPathFactory.newInstance();
        XPath xpath = factory.newXPath();
        
        List<Node> alldat= new ArrayList<>();
        // page 4-1 (p53) of https://pds.nasa.gov/datastandards/pds3/standards/sr/StdRef_20090227_v3.8.pdf
        // https://pds.nasa.gov/datastandards/pds3/standards/sr/AppendixA.pdf
        NodeList dat= (NodeList) xpath.evaluate("/LABEL/TABLE/COLUMN/NAME/text()",doc,XPathConstants.NODESET);
        if ( dat.getLength()>0 ) {
            appendNodeList( alldat, dat );
        }
        dat= (NodeList) xpath.evaluate("/LABEL/*[contains(name(),'_TABLE')]/COLUMN/NAME/text()",doc,XPathConstants.NODESET);
        if ( dat.getLength()>0 ) {
            appendNodeList( alldat, dat );
        }
        dat= (NodeList) xpath.evaluate("/LABEL/BINARY_TABLE/COLUMN/NAME/text()",doc,XPathConstants.NODESET);
        if ( dat.getLength()>0 ) {
            appendNodeList( alldat, dat );
        }
        dat= (NodeList) xpath.evaluate("/LABEL/ASCII_TABLE/COLUMN/NAME/text()",doc,XPathConstants.NODESET);
        if ( dat.getLength()>0 ) {
            appendNodeList( alldat, dat );
        }
        dat= (NodeList) xpath.evaluate("/LABEL/TIME_SERIES/COLUMN/NAME/text()",doc,XPathConstants.NODESET);
        if ( dat.getLength()>0 ) {
            appendNodeList( alldat, dat );
        }
        dat= (NodeList) xpath.evaluate("/LABEL/FILE/SPREADSHEET/FIELD/NAME/text()",doc,XPathConstants.NODESET);
        if ( dat.getLength()>0 ) {
            appendNodeList( alldat, dat );
        }
        dat= (NodeList) xpath.evaluate("/LABEL/SPREADSHEET/FIELD/NAME/text()",doc,XPathConstants.NODESET);
        if ( dat.getLength()>0 ) {
            appendNodeList( alldat, dat );
        }        
        for ( int i=0; i<alldat.size(); i++ ) {
            Node n= alldat.get(i);
            String name= n.getTextContent();
            PDS3DataObject dd= getDataObjectPds3( url, name );
            result.put( name, summarizeDescription(dd.getDescription()) );
        }
        
        // check for high-rank containers
        Node table=null;
        if ( table==null ) {
            // maybe they are high-rank
            
            Node n= doc.getDocumentElement();
            
            table= (Node) xpath.evaluate(String.format("/LABEL/TABLE[1]"),n,XPathConstants.NODE);
            if ( table==null ) {            
                
            } else {
                NodeList columns= (NodeList) xpath.evaluate("CONTAINER/COLUMN",table,XPathConstants.NODESET);
                for ( int i=0; i<columns.getLength(); i++ ) {
                    Node column= columns.item(i);
                    String name= (String)xpath.evaluate("NAME/text()",column,XPathConstants.STRING);
                    PDS3DataObject dd= getDataObjectPds3( url, name );
                    result.put( name, summarizeDescription(dd.getDescription()) );                      
                }
                columns= (NodeList) xpath.evaluate("CONTAINER/CONTAINER/COLUMN",table,XPathConstants.NODESET);
                for ( int i=0; i<columns.getLength(); i++ ) {
                    Node column= columns.item(i);
                    if ( column!=null ) {   
                        String name= (String)xpath.evaluate("NAME/text()",column,XPathConstants.STRING);
                        PDS3DataObject dd= getDataObjectPds3( url, name );
                        result.put( name, summarizeDescription(dd.getDescription()) );  
                    }
                }
            }
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
        if ( dat==null || dat.getLength()==0 ) {
            // https://pds-ppi.igpp.ucla.edu/data/GO-J-PWS-5-DDR-PLASMA-DENSITY-FULL-V1.0/DATA/00_JUPITER/FPE_1996_05_25_V01.LBL
            dat= (NodeList) xpath.evaluate("/LABEL/FILE/POINTER/text()",doc,XPathConstants.NODESET);
        }
        
        String f= dat.item(0).getNodeValue();
        FilePointer result= new FilePointer(labelFile,f);
        return result;
        //<POINTER object="TABLE">JAD_L50_LRS_ELC_ANY_DEF_2016240_V01.DAT</POINTER>

    }
    
    /**
     * other codes now try hard to HTML-ize the description, but sometimes we don't 
     * need this, and we want just the first line.
     * @param description for example &lt;html&gt;&lt;br&gt; Spacecraft Event Time (SCET) at the start of the first sweep in year, &lt;br&gt;...
     * @return for example "Spacecraft Event Time (SCET) at the start of the first sweep in year..."
     */
    private static String removeHtml( String description ) {
        if ( description==null ) return null;
        if ( description.startsWith("<html>") ) {
            description= description.substring(6);
            if ( description.startsWith("<br>") ) {
                description= description.substring(4);
            }
        }
        
        String more = "";
        int i= description.indexOf("<br>");
        if ( i>-1 ) {
            description= description.substring(0,i);
            more= "...";
        }
        return description.trim() + more;
    }
    
    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        if ( cc.context.equals(CompletionContext.CONTEXT_PARAMETER_NAME) ) {
            
            logger.log(Level.FINE, "getCompletions {0}", cc.resourceURI);
            
            DataSetURI.getFile(cc.resourceURI.toURL(),new NullProgressMonitor());
             
            Map<String,String> result;
            result = getDataObjectNames(cc.resourceURI.toURL(), mon);
            
            List<CompletionContext> ccresult= new ArrayList<>();
            ccresult.add( new CompletionContext( 
                    CompletionContext.CONTEXT_PARAMETER_NAME, 
                    "", 
                    this, "arg_0", 
                    "Select parameter to plot", "", false ) );
            for ( java.util.Map.Entry<String,String> e:result.entrySet() ) {
                String key= e.getKey();
                String desc= e.getValue();
                desc= removeHtml( desc );
                CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, 
                        key, this, "arg_0", key + ": " +desc, null, true );
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
                    desc= removeHtml( desc );
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
