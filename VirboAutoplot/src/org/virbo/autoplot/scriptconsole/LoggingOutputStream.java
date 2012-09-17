package org.virbo.autoplot.scriptconsole;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 
 * makes stderr and stdout loggable.
 * from http://blogs.sun.com/nickstephen/entry/java_redirecting_system_out_and
 * An OutputStream that writes contents to a Logger upon each call to flush() 
 */
public class LoggingOutputStream extends ByteArrayOutputStream {

    private String lineSeparator;
    private Logger logger;
    private Level level;

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
            record = this.toString();

            if ( !record.contains(lineSeparator ) ) return;
            super.reset();


            if (record.length() == 0 || record.equals(lineSeparator)) {
                // avoid empty records 
                return;
            }

            String[] ss= record.split("\n");

            for ( int i=0; i<ss.length; i++ ) {
                logger.logp(level, "", "", ss[i]);
            }
            
        }
    }
} 