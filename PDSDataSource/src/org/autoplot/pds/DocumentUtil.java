
package org.autoplot.pds;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.das2.util.FileUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

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
}
