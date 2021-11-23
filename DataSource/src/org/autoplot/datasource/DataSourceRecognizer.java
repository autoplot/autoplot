
package org.autoplot.datasource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import jdk.nashorn.internal.parser.JSONParser;
import org.das2.datum.LoggerManager;
import org.das2.util.FileUtil;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * One-stop for guessing the data source type based on schemas and tags.  Presently
 * this recognizes just JSON and XML types, but might also be used to identify files
 * by magic numbers (bytes f3 cd for CDF, ASCII %PDF for PDF).
 * @author jbf
 */
public class DataSourceRecognizer {

    public static String guessDataSourceType( File f ) throws IOException {
        FileChannel channel= new FileInputStream(f).getChannel();
        ByteBuffer buf= channel.map( FileChannel.MapMode.READ_ONLY, 0, 1024 );
        ByteBuffer fer= channel.map( FileChannel.MapMode.READ_ONLY, channel.size()-1024, 1024 );
        if ( buf.limit()>5 && 
                buf.get(0)=='<' && buf.get(1)=='?' && buf.get(2)=='x' && buf.get(3)=='m' && buf.get(4)=='l' ) {
            return guessDataSourceTypeXML(f);
        } else if ( buf.limit()>1 && 
                buf.get(0)=='{' ) {
            for ( int i=1023; i>1000; i-- ) {
                byte c= fer.get(i);
                if ( !Character.isWhitespace(c) ) {
                    if ( c=='}' ) {
                        return guessDataSourceTypeJSON(f);
                    } else {
                        break;
                    }
                }
            }
            return null;
        } else {
            return null;
        }
    }
    
    private static final Logger logger= LoggerManager.getLogger("apdss.xml");
    
    public static final String TYPE_HELM = "HELM";
    public static final String TYPE_SPASE = "SPASE";
    public static final String TYPE_VOTABLE = "VOTABLE";
    public static final String TYPE_MISC = "MISC";
    
    private Object type;
    
    private static final String ID_XML_TYPE = "got the type";

    private static class MyDefaultHandler extends DefaultHandler {
        
        private String type;
        
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
            switch (localName) {
                case "VOTABLE":
                    type= "vap+xml";
                    throw new RuntimeException(ID_XML_TYPE); // abort from parsing
                    
                case "Spase":
                    type= "vap+xml";
                    throw new RuntimeException(ID_XML_TYPE); // abort from parsing
                    
                case "Eventlist":
                    type= "vap+xml";
                    throw new RuntimeException(ID_XML_TYPE); // abort from parsing
                
                case "Product_Observational":
                    type= "vap+pds4";
                    throw new RuntimeException(ID_XML_TYPE); // abort from parsing
                    
                default:
                    throw new IllegalArgumentException("Unrecognized XML type: "+uri);
            }

        }
            
    };
            

    /**
     * return the type for the XML file.  Three types, SPASE, EventList, and VOTABLE
     * are all handled by the vap+xmln, and the fourth is vap+pds4.
     * @param f
     * @return
     * @throws IOException 
     */
    public static String guessDataSourceTypeXML( File f ) throws IOException {
        XMLReader xmlReader=null;
        MyDefaultHandler handler= new MyDefaultHandler();
        
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            SAXParser saxParser = spf.newSAXParser();
            xmlReader = saxParser.getXMLReader();
            xmlReader.setContentHandler( handler );
            
        } catch (SAXException | ParserConfigurationException ex) {
            logger.log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
        
        try {
            xmlReader.parse( f.toURI().toString() );
            
        } catch ( RuntimeException ex ) {
            // should happen because we threw ID_XML_TYPE
        } catch (SAXException ex) {
            logger.log(Level.SEVERE, null, ex);
        }

        return handler.type;
        
    }
    
    /**
     * return the type, if recognized, of the JSON file.
     * @param f
     * @return
     * @throws IOException 
     */
    public static String guessDataSourceTypeJSON( File f ) throws IOException {
        try {
            long t0= System.currentTimeMillis();
            String json= FileUtil.readFileToString(f);
            System.err.println("read to string: "+(System.currentTimeMillis()-t0));
            JSONObject obj= new JSONObject(json);
            System.err.println("read to JSONObject: "+(System.currentTimeMillis()-t0));
            if ( obj.has("crs") && obj.has("fields") ) {
                return "vap+tfcat";
            } else {
                return null;
            }
        } catch (JSONException ex) {
            return null;
        }
    }
    
}
