/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.binarydatasource;

import java.nio.ByteBuffer;
import org.virbo.dataset.AbstractDataSet;
import org.virbo.dataset.WritableDataSet;

/**
 * rank 1,2,or 3 dataset backed by double array. 
 *
 * @author jbf
 */
public abstract class BufferDataSet extends AbstractDataSet implements WritableDataSet {

    int rank;
    int len0;
    int len1;
    int len2;
    
    
    /**
     * the number of bytes per record
     */
    int reclen;
    
    /**
     * the byte offset into each record
     */
    int recoffset;
    
    /**
     * the number of bytes of the field in each record
     */
    int fieldLen;
    
    /**
     * the array backing the data
     */
    protected ByteBuffer back;
    
    private static final boolean RANGE_CHECK = true;

    public final static String DOUBLE= "double";
    public final static String FLOAT= "float";
    public final static String LONG= "long";
    public final static String INT= "int";
    public final static String SHORT= "short";
    public final static String BYTE= "byte";
    public final static String UBYTE= "ubyte";
    
    public static int byteCount(String type) {
        if (type.equals(DOUBLE)) {
            return 8;
        } else if (type.equals(FLOAT)) {
            return 4;
        } else if (type.equals(LONG)) {
            return 8;
        } else if (type.equals(INT)) {
            return 4;
        } else if (type.equals(SHORT)) {
            return 2;
        } else if (type.equals(BYTE)) {
            return 1;
        } else if (type.equals(UBYTE)) {
            return 1;
        } else {
            throw new IllegalArgumentException("bad type: " + type);
        }
    }
    
    public static BufferDataSet makeDataSet( int rank, int reclen, int recoffs, int len0, int len1, int len2, ByteBuffer buf, String type ) {
        
        if ( type.equals(DOUBLE) ) {
            return new Double( rank, reclen, recoffs, len0, len1, len2, buf );
        } else if ( type.equals(FLOAT) ) {
            return new  Float( rank, reclen, recoffs, len0, len1, len2, buf );
        } else if ( type.equals(LONG) ) {
            return new  Long( rank, reclen, recoffs, len0, len1, len2, buf );
        } else if ( type.equals(INT) ) {
            return new  Int( rank, reclen, recoffs, len0, len1, len2, buf );
        } else if ( type.equals(SHORT) ) {
            return new  Short( rank, reclen, recoffs, len0, len1, len2, buf );
        } else if ( type.equals(BYTE) ) {
            return new  Byte( rank, reclen, recoffs, len0, len1, len2, buf );
        } else if (type.equals(UBYTE) ) {
            return new UByte( rank, reclen, recoffs, len0, len1, len2, buf );           
        } else {
            throw new IllegalArgumentException("bad data type: "+type);
        }
    }

    public BufferDataSet( int rank, int reclen, int recoffs, int len0, int len1, int len2, int fieldLen, ByteBuffer back  ) {
        this.back= back;
        this.rank = rank;
        this.reclen= reclen;
        this.recoffset= recoffs;
        this.len0 = len0;
        this.len1 = len1;
        this.len2 = len2;
        this.fieldLen= fieldLen;
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
    
    /**
     * return the offset, in bytes, of the element.
     * @param i0
     * @param i1
     * @param i2
     * @return the offset, in bytes, of the element.
     */
    protected int offset(int i0, int i1, int i2) {
        if (RANGE_CHECK) {
            rangeCheck(i0, i1, i2);
        }
        return reclen * i0 + recoffset + i1 * fieldLen * len2  + i2 * fieldLen ;
    }
    
    /**
     * return the offset, in bytes, of the element.
     * @param i0
     * @param i1
     * @return the offset, in bytes, of the element.
     */
    protected int offset(int i0, int i1 ) {
        if (RANGE_CHECK) {
            rangeCheck(i0, i1, 0);
        }        
        return reclen * i0 + recoffset + i1 * fieldLen ;
    }

    /**
     * return the offset, in bytes, of the element.
     * @param i0
     * @return the offset, in bytes, of the element.
     */
    protected int offset(int i0 ) {
        if (RANGE_CHECK) {
            rangeCheck(i0, 0, 0);
        }
        return reclen * i0 + recoffset;
    }

    @Override
    public abstract double value(int i0);

    @Override
    public abstract double value(int i0, int i1);

    @Override
    public abstract double value(int i0, int i1, int i2);
    
    /*public abstract double putValue(int i0, double d );

    public abstract double putValue(int i0, int i1, double d );

    public abstract double putValue(int i0, int i1, int i2, double d );     */
}
