
package org.autoplot.pds;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.das2.util.FileUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Document and XML utilities.
 * @author jbf
 */
public class DocumentUtil {
    /**
     * dump the document to an XML file
     * @param doc
     * @param f
     * @throws IllegalArgumentException 
     */
    public static void dumpToXML( Document doc, File f ) throws IllegalArgumentException {
        Transformer transformer;
        try {
            transformer = TransformerFactory.newInstance().newTransformer();
        } catch (TransformerConfigurationException ex) {
            throw new RuntimeException(ex);
        }
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        // initialize StreamResult with File object to save to file
        StreamResult result = new StreamResult(new StringWriter());
        DOMSource source = new DOMSource(doc);
        try {
            transformer.transform(source, result);
        } catch (TransformerException ex) {
            throw new RuntimeException(ex);
        }
        String xmlString = result.getWriter().toString();
        try {
            FileUtil.writeStringToFile(f, xmlString);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        
    }
    
    /**
     * dump the JSONObject to a file for inspection while debugging.
     * @param jo the JSON Object.
     * @param f the file
     */
    public static void dumpToFile( JSONObject jo, File f ) throws Exception {
        try ( PrintWriter p= new PrintWriter( new FileWriter( f ) ) ) {
            p.print( jo.toString(4) );
        } catch ( Exception e ) {
            e.printStackTrace();       
        }
    }

    /**
     * return true if the node has no children other than the text node.
     * @param node the node
     * @return 
     */
    /**
     * return true if the node has no children other than the text node.
     * @param node the node
     * @return 
     */
    public static boolean isLeaf(Node node) {
        return node.getChildNodes().getLength() == 1 && node.getFirstChild().getNodeType() == Node.TEXT_NODE;
    }

    /**
     * Create map from the document so that it can be used as metadata.  Note
     * this looks for the DESCRIPTION (or description) node and will remove
     * extra whitespace which was used to format assuming a fixed-width font.
     * @param root the document.
     * @return a map representing the document.
     */
    public static Map<String, Object> convertDocumentToMap(Node root) {
        Map<String, Object> resultMap = new HashMap<>();
        NodeList nodeList = root.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            String key = node.getNodeName();
            if (isLeaf(node)) {
                String value = node.getTextContent(); // or another method to extract the value
                if (key.equalsIgnoreCase("DESCRIPTION")) {
                    value = cleanDescriptionString(value);
                }
                resultMap.put(key, value);
            } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                Map<String, Object> subNode = convertDocumentToMap(node);
                resultMap.put(key, subNode);
            }
        }
        return resultMap;
    }

    /**
     * remove whitespace intended to format nicely with fixed-with fonts and replace &#13; with &lt;br&gt;.
     * @param desc
     * @return
     */
    public static String cleanDescriptionString(String desc) {
        if (desc == null) {
            return null;
        }
        //desc= String.join(" ",desc.split("[\\s|\\&\\#13\\;]+"));
        desc = String.join(" ", desc.trim().split("\\s+"));
        desc = String.join("<br>", desc.split("\\&\\#13\\;"));
        return "<html>" + desc;
    }
    
    public static String cleanString( String desc ) {
        if (desc == null) {
            return null;
        }
        //desc= String.join(" ",desc.split("[\\s|\\&\\#13\\;]+"));
        desc = String.join(" ", desc.trim().split("\\s+"));
        desc = String.join("<br>", desc.split("\\&\\#13\\;"));
        return "<html>" + desc;
    }
    
    /**
     * remove whitespace intended to format nicely with fixed-with fonts and replace &#13; with &lt;br&gt;.
     * @param jo
     * @param desc
     * @return
     */
    public static JSONArray cleanJSONArray( JSONArray jo ) {
        for ( int i=0; i<jo.length(); i++ ) {
            try {
                Object o2= jo.get(i);
                if ( o2 instanceof JSONObject ) {
                    cleanJSONObject( (JSONObject)o2 );
                } else if ( o2 instanceof JSONArray ) {
                    cleanJSONArray( (JSONArray)o2 );
                } else if ( o2 instanceof String ) {
                    jo.put( i, o2 ); // maybe
                }
            } catch (JSONException ex) {
                Logger.getLogger(DocumentUtil.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return jo;
    }
    
    /**
     * remove whitespace intended to format nicely with fixed-with fonts and replace &#13; with &lt;br&gt;.
     * @param desc
     * @return
     */
    public static JSONObject cleanJSONObject( JSONObject jo ) {
        Iterator o= jo.keys();
        while ( o.hasNext() ) {
            try {
                String k= (String)o.next();
                Object o2= jo.get(k);
                if ( o2 instanceof JSONObject ) {
                    cleanJSONObject( (JSONObject)o2 );
                } else if ( o2 instanceof JSONArray ) {
                    cleanJSONArray( (JSONArray)o2 );
                } else if ( o2 instanceof String ) {
                    if ( k.equals("DESCRIPTION") ) {
                        jo.put( k, cleanString((String)o2) );
                    } else {
                        jo.put( k, (String)o2 );
                    }
                }
            } catch (JSONException ex) {
                Logger.getLogger(DocumentUtil.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return jo;
    }
    
    /**
     * Misguided attempt to create a title assuming the first sentence
     * is a summary.  For example:
     * <pre>RTN normal component of the magnetic field component in nT. The normal component (N) completes the right handed ...</pre>
     * becomes
     * <pre>RTN normal component of the magnetic field component in nT.</pre>
     * In this day of ChatGPT, this seems quite silly, but what else can be done?
     * @param desc a longer description.
     * @return a title
     */
    public static String createTitleFrom(String desc) {
        if (desc == null) {
            return null;
        }
        desc= cleanString(desc);
        int i= desc.indexOf(". ");
        if ( i>10 ) {
            desc= desc.substring(0,i);
        }
        if ( desc.startsWith("<html>") ) {
            desc= desc.substring(6);
        }
        return desc;
    }
    
    /**
     * Read the XML file into a document.
     * @param f the file
     * @return the document object
     * @throws IOException
     * @throws SAXException
     */
    public static Document readXML(File f) throws IOException, SAXException {
        DocumentBuilder builder = null;
        try {
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }
        Document document;
        try (InputStream in = new FileInputStream(f)) {
            InputSource source = new InputSource(in);
            document = builder.parse(source);
        }
        return document;
    }
}
