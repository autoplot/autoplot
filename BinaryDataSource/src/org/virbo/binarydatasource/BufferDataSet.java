/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.binarydatasource;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import org.virbo.dataset.AbstractDataSet;

/**
 * rank 1,2,or 3 dataset backed by double array. 
 *
 * @author jbf
 */
public abstract class BufferDataSet extends AbstractDataSet {

    int rank;
    int len0;
    int len1;
    int len2;
    int reclen0;
    int reclen1;
    int reclen2;
    int recoffs0;
    int recoffs1;
    int recoffs2;
    protected static final boolean RANGE_CHECK = true;

    public final static String DOUBLE= "double";
    public final static String FLOAT= "float";
    public final static String LONG= "long";
    public final static String INT= "int";
    public final static String SHORT= "short";
    public final static String BYTE= "byte";
    
    public static BufferDataSet makeDataSet( int rank, int len0, int reclen0, int recoffs0, int len1, int reclen1, int recoffs1, ByteBuffer buf, String type ) {
        
        if ( type.equals(DOUBLE) ) {
            DoubleBuffer dbuf = buf.asDoubleBuffer();
            return new Double( rank, len0, reclen0, recoffs0, len1, reclen1, recoffs1, dbuf );
        } else if ( type.equals(FLOAT) ) {
            FloatBuffer fbuf= buf.asFloatBuffer();
            return new Float( rank, len0, reclen0, recoffs0, len1, reclen1, recoffs1, fbuf );
        } else if ( type.equals(LONG) ) {
            LongBuffer fbuf= buf.asLongBuffer();
            return new Long( rank, len0, reclen0, recoffs0, len1, reclen1, recoffs1, fbuf );
        } else if ( type.equals(INT) ) {
            IntBuffer fbuf= buf.asIntBuffer();
            return new Int( rank, len0, reclen0, recoffs0, len1, reclen1, recoffs1, fbuf );
        } else if ( type.equals(SHORT) ) {
            ShortBuffer fbuf= buf.asShortBuffer();
            return new Short( rank, len0, reclen0, recoffs0, len1, reclen1, recoffs1, fbuf );
        } else if ( type.equals(BYTE) ) {
            return new Byte( rank, len0, reclen0, recoffs0, len1, reclen1, recoffs1, buf );
        } else {
            throw new IllegalArgumentException("bad data type: "+type);
        }
    }

    public BufferDataSet(int rank, int len0, int reclen0, int recoffs0, int len1, int reclen1, int recoffs1) {
        this.rank = rank;
        this.len0 = len0;
        this.len1 = len1;
        this.len2 = 1;
        this.reclen0 = reclen0;
        this.reclen1 = reclen1;
        this.reclen2 = 1;
        this.recoffs0 = recoffs0;
        this.recoffs1 = recoffs1;
    }

    public int rank() {
        return rank;
    }

    public int length() {
        return len0;
    }

    public int length(int i) {
        return len1;
    }

    public int length(int i0, int i1) {
        return len2;
    }

    protected void rangeCheck(int i0, int i1, int i2) {
        if (i0 < 0 || i0 >= len0) {
            throw new IndexOutOfBoundsException("i0=" + i0 + " " + this.toString());
        }
        if (i1 < 0 || i1 >= len1) {
            throw new IndexOutOfBoundsException("i1=" + i1 + " " + this.toString());
        }
        if (i2 < 0 || i2 >= len2) {
            throw new IndexOutOfBoundsException("i2=" + i2 + " " + this.toString());
        }

    }
    
    protected int offset(int i0, int i1, int i2) {
        return recoffs0 + i0 * reclen0 * reclen1 * reclen2 + i1 * len2 + i2;
    }
    
    @Override
    public abstract double value(int i0);

    @Override
    public abstract double value(int i0, int i1);

    @Override
    public abstract double value(int i0, int i1, int i2);
    
}
