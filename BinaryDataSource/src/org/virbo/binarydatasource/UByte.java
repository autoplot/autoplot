package org.virbo.binarydatasource;

import java.nio.ByteBuffer;

/**
 * Unsigned Byte.
 * @author jbf
 */
public class UByte extends BufferDataSet {

    public UByte(int rank, int reclen, int recoffs, int len0, int len1, int len2, ByteBuffer back ) {
        super(rank, reclen, recoffs, len0, len1, len2, 1, back );
    }

    public double value(int i0) {
        byte b= back.get(offset(i0, 0, 0));
        return b < 0 ? b + 256 : b;
    }

    public double value(int i0, int i1) {
        byte b= back.get(offset(i0, i1, 0)); 
        return b < 0 ? b + 256 : b;
    }

    public double value(int i0, int i1, int i2) {
        byte b= back.get(offset(i0, i1, i2));
        return b < 0 ? b + 256 : b;
    }
    
    public void putValue(int i0, double d) {
        back.put( offset(i0), (byte)( d > 128 ? d - 256 : d ) );
    }

    public void putValue(int i0, int i1, double d) {
        back.put( offset(i0, i1), (byte)( d > 128 ? d - 256 : d ) );
    }

    public void putValue(int i0, int i1, int i2, double d) {
        back.put( offset(i0, i1, i2), (byte)( d > 128 ? d - 256 : d ) );
    }        
}
