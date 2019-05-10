
package org.autoplot.hapi;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * concatenates multiple readers so that they appear as one reader.
 * @author jbf
 */
public class ConcatenateBinaryRecordReader implements AbstractBinaryRecordReader {

    List<AbstractBinaryRecordReader> readers;
    int currentReader;
    
    /**
     * add the reader to the readers, so that this reader will be used after the
     * others are used.
     * @param r 
     */
    public void concatenateReader( AbstractBinaryRecordReader r ) {
        readers= new ArrayList<>();
        readers.add(r);
    }
    
    @Override
    public int readRecord(ByteBuffer buf) throws IOException {
        if ( currentReader==readers.size() ) {
            return -1;
        } else {
            int i= readers.get(currentReader).readRecord(buf);
            while ( i==-1 ) {
                readers.get(currentReader).close();
                currentReader++;
                if ( currentReader==readers.size() ) {
                    return -1;
                } else {
                    i= readers.get(currentReader).readRecord(buf);
                }
            }
            return i;
        }
    }

    @Override
    public void close() throws IOException {
        // nothing needs to be done.
    }
    
}
