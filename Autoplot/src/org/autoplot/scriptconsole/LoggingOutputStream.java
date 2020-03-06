package org.autoplot.scriptconsole;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 
 * makes stderr and stdout loggable.
 * from http://blogs.sun.com/nickstephen/entry/java_redirecting_system_out_and
 * An OutputStream that writes contents to a Logger upon each call to flush() 
 */
public class LoggingOutputStream extends ByteArrayOutputStream {

    private final String lineSeparator;
    private final Logger logger;
    private final Level level;

    /** 
     * Constructor 
     * @param logger Logger to write to 
     * @param level Level at which to write the log message 
     */
    public LoggingOutputStream(Logger logger, Level level) {
        super();
        this.logger = logger;
        this.level = level;
        lineSeparator = System.getProperty("line.separator"); // applet okay    
    }

    /** 
     * upon flush() write the existing contents of the OutputStream
     * to the logger as a log record. 
     * @throws java.io.IOException in case of error 
     */
    @Override
    public void flush() throws IOException {

        String record;
        synchronized (this) {
            super.flush();
            try {
                // Because of Jython 2.2, we need to use ISO-8859-1, which handles Units.microseconds2 properly.
                record = this.toString("ISO-8859-1");  // Try "print Units.microseconds3" (&micro;s) at the command line to see where the encoding is messed up.
            } catch ( UnsupportedEncodingException ex ) {
                record = this.toString(); 
            }
            
            if ( !record.contains(lineSeparator ) ) return;
            super.reset();


            if (record.length() == 0 || record.equals(lineSeparator)) {
                // avoid empty records 
                return;
            }

            String[] ss= record.split("\n");

            for (String s : ss) {
                logger.logp(level, "", "", s);
            }
            
        }
    }
} 