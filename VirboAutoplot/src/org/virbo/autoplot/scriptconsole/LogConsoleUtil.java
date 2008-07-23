/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.scriptconsole;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.logging.LogRecord;
import java.util.logging.XMLFormatter;

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
}
