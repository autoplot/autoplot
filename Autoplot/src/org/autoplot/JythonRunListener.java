
package org.autoplot;

import java.io.File;
import org.python.core.PyException;

/**
 * Listens for feedback about run status
 * @author jbf
 */
public interface JythonRunListener {
    public void runningScript( File script );
    public void exceptionEncountered( File script, PyException exception );
}
