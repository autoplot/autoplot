
package org.autoplot.hapi;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * paste several BufferedReaders together to appear as 
 * one BufferedReader.
 * @author jbf
 */
public class PasteBinaryRecordReader implements AbstractBinaryRecordReader {

    List<AbstractBinaryRecordReader> readers;
    
    public PasteBinaryRecordReader() {
        readers= new ArrayList<>();
    }
    
    public void pasteBufferedReader( AbstractBinaryRecordReader r ) {
        readers.add(r);
    }
    
    @Override
    public int readRecord(ByteBuffer buf) throws IOException {
        int i=0;
        for ( AbstractBinaryRecordReader r: readers ) {
            int i1= r.readRecord(buf);
            i+= i1;
        }
        return i;
    }

    @Override
    public void close() throws IOException {
        for ( AbstractBinaryRecordReader r: readers ) {
            r.close();
        }
    }
        
}
