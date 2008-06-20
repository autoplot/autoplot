package org.virbo.binarydatasource;

import java.nio.ByteBuffer;

public class UByte extends BufferDataSet {

    ByteBuffer back;

    public UByte(int rank, int len0, int reclen0, int recoffs0, int len1, int reclen1, int recoffs1, ByteBuffer back) {
        super(rank, len0, reclen0, recoffs0, len1, reclen1, recoffs1);
        this.back = back;
    }

    public double value(int i0) {
        if (RANGE_CHECK) {
            rangeCheck(i0, 0, 0);
        }
        byte b= back.get(offset(i0, 0, 0));
        return b < 0 ? b + 256 : b;
    }

    public double value(int i0, int i1) {
        if (RANGE_CHECK) {
            rangeCheck(i0, i1, 0);
        }
        byte b= back.get(offset(i0, i1, 0)); 
        return b < 0 ? b + 256 : b;
    }

    public double value(int i0, int i1, int i2) {
        if (RANGE_CHECK) {
            rangeCheck(i0, i1, i2);
        }
        byte b= back.get(offset(i0, i1, i2));
        return b < 0 ? b + 256 : b;
    }
}
