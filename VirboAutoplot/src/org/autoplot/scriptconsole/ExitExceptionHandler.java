
package org.autoplot.scriptconsole;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.ExceptionHandler;
import org.das2.util.LoggerManager;

/**
 * Exception handler introduced for Hudson/Jenkins tests.  
 * Prints the exception and exits.
 * @author jbf
 */
public class ExitExceptionHandler implements ExceptionHandler {

    static final Logger logger= LoggerManager.getLogger("autoplot");
    
    @Override
    public void handle(Throwable t) {
        logger.log( Level.WARNING, "exception occurred: "+t.getMessage(), t );
        t.printStackTrace(System.err);
        System.exit(1);  // findbugs OKAY
    }

    @Override
    public void handleUncaught(Throwable t) {
        logger.log( Level.WARNING, "uncaught exception occurred: "+t.getMessage(), t );
        t.printStackTrace(System.err);
        System.exit(1);  // findbugs OKAY
    }
}
