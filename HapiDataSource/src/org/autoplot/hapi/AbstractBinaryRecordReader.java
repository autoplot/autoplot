
package org.autoplot.hapi;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * read data record-by-record, so the client doesn't need to worry if the file
 * is local or coming from the network.
 * @author jbf
 */
public interface AbstractBinaryRecordReader extends Closeable {
    /**
     * read data to fill the buffer, or return null if no more data
     * is available.
     * @param buf
     * @return the number of bytes read, which will be the length of the buffer or -1.
     * @throws IOException 
     */
    public int readRecord( ByteBuffer buf ) throws IOException;
}
