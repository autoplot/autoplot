package org.virbo.binarydatasource;

import java.nio.ByteBuffer;
import org.virbo.dataset.WritableDataSet;

public class Int extends BufferDataSet implements WritableDataSet {

    public Int(int rank, int reclen, int recoffs, int len0, int len1, int len2, ByteBuffer back) {
        super(rank, reclen, recoffs, len0, len1, len2, 4, back);
    }

    public double value(int i0) {
        return back.getInt(offset(i0, 0, 0));
    }

    public double value(int i0, int i1) {
        return back.getInt(offset(i0, i1, 0));
    }

    public double value(int i0, int i1, int i2) {
        return back.getInt(offset(i0, i1, i2));
    }
    
    public void putValue(int i0, double d) {
        back.putInt( offset(i0, 0, 0), (int)d );
    }

    public void putValue(int i0, int i1, double d) {
        back.putInt( offset(i0, i1, 0), (int)d );
    }

    public void putValue(int i0, int i1, int i2, double d) {
        back.putInt( offset(i0, i1, i2), (int)d );
    }
}
