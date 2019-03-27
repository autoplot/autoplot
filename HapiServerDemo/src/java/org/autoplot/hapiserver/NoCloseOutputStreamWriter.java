
package org.autoplot.hapiserver;

import java.io.OutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.logging.Logger;

/**
 * Logger which wraps an output stream but will not close it when this
 * OutputStreamWriter is closed.
 * 
 * @author jbf
 */
public class NoCloseOutputStreamWriter extends OutputStreamWriter {
    private static final Logger logger= Logger.getLogger("hapi");
    
    NoCloseOutputStreamWriter( OutputStream out ) {
        super( out );
    }

    @Override
    public void close() throws IOException {
        logger.fine("not closing");
        super.flush();
        // do not close the wrapped stream.
    }
    
}
