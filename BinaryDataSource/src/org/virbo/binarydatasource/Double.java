package org.virbo.binarydatasource;

import java.nio.ByteBuffer;
import org.virbo.dataset.WritableDataSet;

public class Double extends BufferDataSet implements WritableDataSet {

    public Double(int rank, int reclen, int recoffs, int len0, int len1, int len2, ByteBuffer back ) {
        super(rank, reclen, recoffs, len0, len1, len2, 8, back );
    }

    public double value(int i0) {
        return back.getDouble( offset(i0));
    }

    public double value(int i0, int i1) {
        return back.getDouble( offset(i0, i1));
    }

    public double value(int i0, int i1, int i2) {
        return back.getDouble( offset(i0, i1, i2));
    }

    public void putValue(int i0, double d) {
        back.putDouble( offset(i0), d );
    }

    public void putValue(int i0, int i1, double d) {
        back.putDouble( offset(i0, i1), d );
    }

    public void putValue(int i0, int i1, int i2, double d) {
        back.putDouble( offset(i0, i1, i2), d );
    }
    
    
}
