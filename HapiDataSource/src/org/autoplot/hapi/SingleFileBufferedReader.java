
package org.autoplot.hapi;

import java.io.BufferedReader;
import java.io.IOException;

/**
 *
 * @author jbf
 */
public class SingleFileBufferedReader implements AbstractLineReader {

    BufferedReader reader;
    
    public SingleFileBufferedReader(BufferedReader reader) {
        this.reader= reader;
    }
            
    @Override
    public String readLine() throws IOException {
        return reader.readLine();
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
    
}
