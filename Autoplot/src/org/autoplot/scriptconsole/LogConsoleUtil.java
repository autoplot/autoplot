/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.scriptconsole;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.XMLFormatter;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
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
        final List<LogRecord> records= new ArrayList<LogRecord>();
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
                if ( qName.equals("record") ) {
                    records.add(rec);
                } else if ( qName.equals("millis") ) {
                    rec.setMillis( (long) Long.parseLong(data));
                } else if ( qName.equals("logger") ) {
                    rec.setLoggerName(data);
                } else if ( qName.equals("level") ) {
                    rec.setLevel(Level.parse(data));
                } else if ( qName.equals("sequence") ) {
                    rec.setSequenceNumber(Integer.parseInt(data));
                } else if ( qName.equals("thread") ) {
                    rec.setThreadID(Integer.parseInt(data));
                } else if ( qName.equals("message") ) {
                    rec.setMessage(data);
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
