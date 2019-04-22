
package org.autoplot.hapi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 *
 * @author jbf
 */
public class SingleFileBinaryReader implements AbstractBinaryRecordReader {

    FileInputStream ins;
    
    public SingleFileBinaryReader( File f ) throws FileNotFoundException {
        ins= new FileInputStream(f);
    }
    
    @Override
    public int readRecord( ByteBuffer buf ) throws IOException {
        int bytesRead= ins.getChannel().read(buf);
        if ( bytesRead==-1 ) return -1;
        while ( bytesRead<buf.limit() ) {
            bytesRead+= ins.getChannel().read(buf);
        }
        return bytesRead;
    }

    @Override
    public void close() throws IOException {
        
    }
    
}
