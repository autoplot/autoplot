package org.virbo.binarydatasource;

import java.nio.DoubleBuffer;
import org.virbo.dataset.WritableDataSet;

public class Double extends BufferDataSet implements WritableDataSet {

    DoubleBuffer back;

    public Double(int rank, int len0, int reclen0, int recoffs0, int len1, int reclen1, int recoffs1, DoubleBuffer back ) {
        super(rank, len0, reclen0, recoffs0, len1, reclen1, recoffs1);
        
        this.back = back;
    }

    public double value(int i0) {
        //if ( RANGE_CHECK ) rangeCheck(i0, 0,0 );
        return back.get( offset(i0, 0, 0));
    }

    public double value(int i0, int i1) {
        //if (RANGE_CHECK) rangeCheck( i0, i1, 0 );
        return back.get( offset(i0, i1, 0));
    }

    public double value(int i0, int i1, int i2) {
        //if (RANGE_CHECK) rangeCheck( i0, i1, i2 );
        return back.get( offset(i0, i1, i2));
    }

    public void putValue(int i0, double d) {
        back.put( offset(i0, 0, 0), d );
    }

    public void putValue(int i0, int i1, double d) {
        back.put( offset(i0, i1, 0), d );
    }

    public void putValue(int i0, int i1, int i2, double d) {
        back.put( offset(i0, i1, i2), d );
    }
    
    
}
