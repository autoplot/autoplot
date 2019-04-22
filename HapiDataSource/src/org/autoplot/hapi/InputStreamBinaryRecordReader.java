
package org.autoplot.hapi;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 *
 * @author jbf
 */
public class InputStreamBinaryRecordReader implements AbstractBinaryRecordReader {

    ReadableByteChannel ch;
    
    InputStreamBinaryRecordReader( InputStream ins ) {
        ch= Channels.newChannel(ins);
    }
    
    @Override
    public int readRecord(ByteBuffer buf) throws IOException {
        int bytesRead= ch.read(buf);
        if ( bytesRead==-1 ) return -1;
        while ( bytesRead<buf.limit() ) {
            int c= ch.read(buf);
            if ( c==-1 ) {
                return -1;
            } else {
                bytesRead+= c;
            }
        }
        return bytesRead;
    }

    @Override
    public void close() throws IOException {
        ch.close();
    }
     
}
