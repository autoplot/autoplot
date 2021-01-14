/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.spase;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Checks to see if this sort of XML file is handled.  This works with a SAX
 * parser, and looks at the tags to see if a file appears to be of a given type.
 * 
 * @author jbf
 */
public class XMLTypeCheck extends DefaultHandler {

    public static final Object TYPE_HELM = "HELM";
    public static final Object TYPE_SPASE = "SPASE";
    public static final Object TYPE_VOTABLE = "VOTABLE";

    private Object type;
    
    private static final String ID_XML_TYPE = "got the type";

    /**
     * initialize the state to STATE_OPEN.
     */
    @Override
    public void startDocument() throws SAXException {

    }

    /**
     * As elements come in, we go through the state transitions to keep track of
     * whether we are reading FIELDS, Rows of the dataset, Individual columns, etc.
     */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if ( localName.equals("VOTABLE") ) {
            type= TYPE_VOTABLE;
            throw new RuntimeException(ID_XML_TYPE);
        } else if ( localName.equals("Spase") ) {
            type= TYPE_SPASE;
            throw new RuntimeException(ID_XML_TYPE);
        } else if ( localName.equals("Eventlist") ) {
            type= TYPE_HELM;
            throw new RuntimeException(ID_XML_TYPE);
        } else {
            throw new IllegalArgumentException("Unrecognized XML type: "+uri);
        }

    }

    /**
     * use a sax parser to get the type.  Return
     * TYPE_VOTABLE, TYPE_HELM, TYPE_SPASE
     * @param f
     * @return 
     */
    public Object calculateType( File f ) throws IOException, SAXException {

        XMLReader xmlReader=null;
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            SAXParser saxParser = spf.newSAXParser();
            xmlReader = saxParser.getXMLReader();
            xmlReader.setContentHandler(this);
        } catch (SAXException ex) {
            Logger.getLogger(SpaseRecordDataSource.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(SpaseRecordDataSource.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
        
        try {
            xmlReader.parse( f.toURI().toString() );
        } catch ( RuntimeException ex ) {
            // should happen because we threw ID_XML_TYPE
        }
        
        return type;
        
    }
}
