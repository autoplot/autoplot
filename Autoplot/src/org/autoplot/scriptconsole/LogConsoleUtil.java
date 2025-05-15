
package org.autoplot.scriptconsole;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.XMLFormatter;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 *
 * @author jbf
 */
public class LogConsoleUtil {
    public static void serializeLogRecords( List<LogRecord> list, OutputStream out ) throws IOException {
        XMLFormatter formatter= new XMLFormatter();
        //out.write( formatter.getHead(h).getBytes() );
        out.write( "<log>\n".getBytes() );
        for ( LogRecord rec : list ) {
            out.write( formatter.format(rec).getBytes() );
        }
        out.write( "</log>".getBytes() );
        //out.write( formatter.getTail(h).getBytes() );        
    }
    public static List<LogRecord> deserializeLogRecords( InputStream in ) throws ParserConfigurationException, SAXException, IOException {
        SAXParser parser= SAXParserFactory.newInstance().newSAXParser();
        final List<LogRecord> records= new ArrayList<>();
        parser.parse( in, new DefaultHandler() {
            LogRecord rec;
            StringBuffer databuf;
            @Override
            public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                if ( qName.equals("record") ) {
                    rec= new LogRecord( Level.ALL, "" );
                }
                databuf= new StringBuffer();
                super.startElement(uri, localName, qName, attributes);
            }

            @Override
            public void characters(char[] ch, int start, int length) throws SAXException {
                databuf.append( ch, start, length );
            }


            @Override
            public void endElement(String uri, String localName, String qName) throws SAXException {
                String data= databuf.toString();
                switch (qName) {
                    case "record":
                        records.add(rec);
                        break;
                    case "millis":
                        rec.setMillis( (long) Long.parseLong(data));
                        break;
                    case "logger":
                        rec.setLoggerName(data);
                        break;
                    case "level":
                        rec.setLevel(Level.parse(data));
                        break;
                    case "sequence":
                        rec.setSequenceNumber(Integer.parseInt(data));
                        break;
                    case "thread":
                        rec.setLongThreadID(Integer.parseInt(data));
                        break;
                    case "message":
                        rec.setMessage(data);
                        break;
                    default:
                        break;
                }
                databuf= new StringBuffer();
                super.endElement(uri, localName, qName);
            }

            @Override
            public void endDocument() throws SAXException {
                super.endDocument();
            }

        } );
        return records;
    }

    /**
     * This method is here for the sole purpose of providing a place to insert a breakpoint when a log message is found for
     * matching highlite pattern.
     */
    public static void checkBreakpoint() {
        int i=0;
    }
}
