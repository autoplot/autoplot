
package org.autoplot.hapi;

import java.io.Closeable;
import java.io.IOException;

/**
 * Similar to BufferedReader, so that it can be extended to support caching.
 * @author jbf
 */
public interface AbstractLineReader extends Closeable {
    public String readLine() throws IOException;
}
