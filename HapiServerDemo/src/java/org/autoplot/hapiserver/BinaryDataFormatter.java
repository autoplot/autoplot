
package org.autoplot.hapiserver;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.qstream.DoubleTransferType;
import org.virbo.qstream.TransferType;

/**
 * Format to doubles.
 * @author jbf
 */
public class BinaryDataFormatter implements DataFormatter {
    
    private static final String FORMAT_BINARY="binary";
    
    TransferType[] transferTypes;
    ByteBuffer b;
    int bufferSize;
    
    public BinaryDataFormatter( ) {
    }
        
    /**
     * configure the format.
     * @param out
     * @param record rank 1 bundle
     */
    @Override
    public void initialize( OutputStream out, QDataSet record  ) {
        transferTypes= new TransferType[record.length()];
        bufferSize= 0;
        
        for ( int i=0; i<record.length(); i++ ) {
            QDataSet d= record.slice(i);
            Units u= (Units)d.property(QDataSet.UNITS);
            if ( u==null ) u= Units.dimensionless;
            if ( UnitsUtil.isTimeLocation(u) ) {
                transferTypes[i]= new DoubleTransferType();
            } else {
                transferTypes[i]= new DoubleTransferType();
            }
            bufferSize+= transferTypes[i].sizeBytes();
        }
        b= ByteBuffer.allocate( bufferSize );
    }
    
    @Override
    public void sendRecord( OutputStream out, QDataSet record ) throws IOException {

        for ( int i=0; i<record.length(); i++ ) {
            QDataSet d= record.slice(i);
            transferTypes[i].write( d.value(), b );
        }
        byte[] bytes= b.array();
        
        out.write( bytes );
        
        b.flip();
                
    }
    
    /**
     * perform any final operations to the stream.  This 
     * DOES NOT close the stream!
     * @param out 
     */
    @Override
    public void finalize( OutputStream out ) {
        
    } 
    
}
