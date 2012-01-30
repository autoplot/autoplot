/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.binarydatasource;

import java.nio.ByteBuffer;
import org.virbo.dataset.AbstractDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
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
    int len3;
    
    
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
     * the field type
     */
    Object type;

    /**
     * the array backing the data
     */
    protected ByteBuffer back;
    
    private static final boolean RANGE_CHECK = true;

    public final static Object DOUBLE= "double";
    public final static Object FLOAT= "float";
    public final static Object TRUNCATEDFLOAT= "truncatedfloat"; // 16 bit real that has exponent like a FLOAT but mantissa precision is reduced.
    public final static Object LONG= "long";
    public final static Object INT= "int";
    public final static Object UINT= "uint";
    public final static Object SHORT= "short";
    public final static Object USHORT= "ushort";
    public final static Object BYTE= "byte";
    public final static Object UBYTE= "ubyte";
    
    public static int byteCount(Object type) {
        if (type.equals(DOUBLE)) {
            return 8;
        } else if (type.equals(FLOAT)) {
            return 4;
        } else if (type.equals(LONG)) {
            return 8;
        } else if (type.equals(INT)) {
            return 4;
        } else if (type.equals(UINT)) {
            return 4;
        } else if (type.equals(TRUNCATEDFLOAT)) {
            return 2;
        } else if (type.equals(SHORT)) {
            return 2;
        } else if (type.equals(USHORT)) {
            return 2;
        } else if (type.equals(BYTE)) {
            return 1;
        } else if (type.equals(UBYTE)) {
            return 1;
        } else {
            throw new IllegalArgumentException("bad type: " + type);
        }
    }
    
    /**
     * 
     * @param rank
     * @param reclen  length in bytes of each record
     * @param recoffs  byte offet of each record
     * @param len0   number of elements in the first index
     * @param len1   number of elements in the second index
     * @param len2   number of elements in the third index
     * @param buf   ByteBuffer containing the data, which should be at least reclen * len0 bytes long.
     * @param type   BufferDataSet.INT, BufferDataSet.DOUBLE, etc...
     * @return
     */
    public static BufferDataSet makeDataSet( int rank, int reclen, int recoffs, int len0, int len1, int len2, int len3, ByteBuffer buf, Object type ) {
        BufferDataSet result;
        if ( rank==1 && len1>1 ) throw new IllegalArgumentException("rank is 1, but len1 is not 1");
        if ( reclen < byteCount(type) ) throw new IllegalArgumentException("reclen " + reclen + " is smaller that length of type "+type); 
        if ( reclen * len0 > buf.limit() ) throw new IllegalArgumentException( String.format( "buffer length (%d bytes) is too small to contain data (%d %d-byte records)", buf.limit(), len0, reclen ) );
        if ( type.equals(DOUBLE) ) {
            result=new Double( rank, reclen, recoffs, len0, len1, len2, len3, buf );
        } else if ( type.equals(FLOAT) ) {
            result=new  Float( rank, reclen, recoffs, len0, len1, len2, len3, buf );
        } else if ( type.equals(LONG) ) {
            result=new  Long( rank, reclen, recoffs, len0, len1, len2, len3, buf );
        } else if ( type.equals(INT) ) {
            result=new  Int( rank, reclen, recoffs, len0, len1, len2, len3, buf );
        } else if ( type.equals(UINT) ) {
            result=new  UInt( rank, reclen, recoffs, len0, len1, len2, len3, buf );
        } else if ( type.equals(SHORT) ) {
            result=new  Short( rank, reclen, recoffs, len0, len1, len2, len3, buf );
        } else if ( type.equals(USHORT) ) {
            result=new  UShort( rank, reclen, recoffs, len0, len1, len2, len3, buf );
        } else if ( type.equals(TRUNCATEDFLOAT) ) {
            result=new  TruncatedFloat( rank, reclen, recoffs, len0, len1, len2, len3, buf );
        } else if ( type.equals(BYTE) ) {
            result=new  Byte( rank, reclen, recoffs, len0, len1, len2, len3, buf );
        } else if (type.equals(UBYTE) ) {
            result=new UByte( rank, reclen, recoffs, len0, len1, len2, len3, buf );
        } else {
            throw new IllegalArgumentException("bad data type: "+type);
        }
        return result;
    }

    /**
     * Create a new BufferDataSet of the given type.  Simple sanity checks are made, including:
     *   rank 1 dataset may not have len1>1.
     *   reclen cannot be shorter than the byte length of the field type.
     * @param rank
     * @param reclen  length in bytes of each record
     * @param recoffs  byte offet of each record
     * @param len0   number of elements in the first index
     * @param len1   number of elements in the second index
     * @param len2   number of elements in the third index
     * @param back   ByteBuffer containing the data, which should be at least reclen * len0 bytes long.
     * @param type   BufferDataSet.INT, BufferDataSet.DOUBLE, etc...
     * @return
     */
    public BufferDataSet( int rank, int reclen, int recoffs, int len0, int len1, int len2, Object type, ByteBuffer back  ) {
        this( rank, reclen, recoffs, len0, len1, len2, 11, type, back );
    }

    /**
     * Create a new BufferDataSet of the given type.  Simple sanity checks are made, including:
     *   rank 1 dataset may not have len1>1.
     *   reclen cannot be shorter than the byte length of the field type. 
     * @param rank
     * @param reclen  length in bytes of each record
     * @param recoffs  byte offet of each record
     * @param len0   number of elements in the first index
     * @param len1   number of elements in the second index
     * @param len2   number of elements in the third index
     * @param len3   number of elements in the fourth index
     * @param back   ByteBuffer containing the data, which should be at least reclen * len0 bytes long.
     * @param type   BufferDataSet.INT, BufferDataSet.DOUBLE, etc...
     * @return
     */
    public BufferDataSet( int rank, int reclen, int recoffs, int len0, int len1, int len2, int len3, Object type, ByteBuffer back  ) {
        if ( rank==1 && len1>1 ) throw new IllegalArgumentException("rank is 1, but len1 is not 1");
        if ( reclen < byteCount(type) ) throw new IllegalArgumentException("reclen " + reclen + " is smaller that length of type "+type);

        this.back= back;
        this.rank = rank;
        this.reclen= reclen;
        this.recoffset= recoffs;
        this.len0 = len0;
        this.len1 = len1;
        this.len2 = len2;
        this.len3 = len3;
        this.type= type;
        this.fieldLen= byteCount(type);
        if ( rank>1 ) {
            putProperty( QDataSet.QUBE, Boolean.TRUE );
        }
        if ( reclen>0 && fieldLen>reclen ) { // negative reclen supported 9-bit floats.
            System.err.println( String.format( "field length (%d) is greater than record length (%d) for len0=%d.", (int)fieldLen, (int)reclen, (int)len0 ) );
        }
        if ( reclen>0 && ( back.remaining()< ( reclen*len0 ) ) ) {
            System.err.println( String.format( "back buffer is too short (len=%d) for len0=%d.", back.remaining(), len0 ) );
        }
    }

    public Object getType() {
        return this.type;
    }

    public int rank() {
        return rank;
    }

    @Override
    public int length() {
        return len0;
    }

    @Override
    public int length(int i) {
        return len1;
    }

    @Override
    public int length(int i0, int i1) {
        return len2;
    }

    @Override
    public int length(int i0, int i1, int i2) {
        return len3;
    }
    protected void rangeCheck(int i0, int i1, int i2, int i3) {
        if (i0 < 0 || i0 >= len0) {
            throw new IndexOutOfBoundsException("i0=" + i0 + " " + this.toString());
        }
        if (i1 < 0 || i1 >= len1) {
            throw new IndexOutOfBoundsException("i1=" + i1 + " " + this.toString());
        }
        if (i2 < 0 || i2 >= len2) {
            throw new IndexOutOfBoundsException("i2=" + i2 + " " + this.toString());
        }
        if (i3 < 0 || i3 >= len3) {
            throw new IndexOutOfBoundsException("i3=" + i3 + " " + this.toString());
        }

    }

    /**
     * return the offset, in bytes, of the element.
     * @return the offset, in bytes, of the element.
     */
    protected int offset( ) {
        if ( this.rank!=0 ) throw new IllegalArgumentException("rank error");
        return recoffset;
    }

    /**
     * return the offset, in bytes, of the element.
     * @param i0
     * @return the offset, in bytes, of the element.
     */
    protected int offset(int i0 ) {
        if ( this.rank!=1 ) throw new IllegalArgumentException("rank error");
        if (RANGE_CHECK) {
            rangeCheck(i0, 0, 0, 0 );
        }
        return recoffset + reclen * i0;
    }
        
    /**
     * return the offset, in bytes, of the element.
     * @param i0
     * @param i1
     * @return the offset, in bytes, of the element.
     */
    protected int offset(int i0, int i1 ) {
        if ( this.rank!=2 ) throw new IllegalArgumentException("rank error");
        if (RANGE_CHECK) {
            rangeCheck(i0, i1, 0, 0 );
        }        
        return  recoffset + reclen * i0 + i1 * fieldLen;
    }

    /**
     * return the offset, in bytes, of the element.
     * @param i0
     * @param i1
     * @param i2
     * @return the offset, in bytes, of the element.
     */
    protected int offset(int i0, int i1, int i2) {
        if ( this.rank!=3 ) throw new IllegalArgumentException("rank error");
        if (RANGE_CHECK) {
            rangeCheck(i0, i1, i2, 0);
        }
        return recoffset + reclen * i0 + i1 * fieldLen * len2  + i2 * fieldLen ;
    }

    /**
     * return the offset, in bytes, of the element.
     * @param i0
     * @param i1
     * @param i2
     * @return the offset, in bytes, of the element.
     */
    protected int offset(int i0, int i1, int i2, int i3 ) {
        if ( this.rank!=4 ) throw new IllegalArgumentException("rank error");
        if (RANGE_CHECK) {
            rangeCheck(i0, i1, i2, i3);
        }
        return recoffset + reclen * i0 + i1 * fieldLen * len2  + i2 * fieldLen ;
    }

    @Override
    public abstract double value();

    @Override
    public abstract double value(int i0);

    @Override
    public abstract double value(int i0, int i1);

    @Override
    public abstract double value(int i0, int i1, int i2);

    @Override
    public abstract double value(int i0, int i1, int i2, int i3);

    /**
     * provide a subset of the dataset.  Note that writes to the result dataset
     * will affect the original dataset.  TODO: correct this since it's a WriteableDataSet.
     */
    @Override
    public QDataSet trim( int ist, int ien ) {
        BufferDataSet result= makeDataSet( rank, reclen, offset(ist), ien-ist, len1, len2, len3, back, type );
        DataSetUtil.putProperties( DataSetUtil.trimProperties( this, ist, ien ), result );
        return result;
    }

    /*public BufferDataSet slice( int i0 ) {
        if ( rank==0 ) {
            throw new IllegalArgumentException("rank limit, can't slice rank zero");
        } else {
            return makeDataSet( rank-1, reclen, recoffset, len1, len2, 1, back, type );
        }
    }*/
    
    /**
     * dump the contents to this buffer.
     * @param buf
     */
    public void copyTo( ByteBuffer buf ) {
        this.back.position( recoffset );
        this.back.mark();
        this.back.limit( recoffset + reclen * len0 );
        buf.put( this.back );
        this.back.reset();
    }

    /**
     * copy the data to a writable buffer if it's not already writable.
     */
    protected synchronized void ensureWritable() {
        if ( back.isReadOnly() ) {
            ByteBuffer wback= ByteBuffer.allocateDirect( back.capacity() );
            wback.put(back);
            back= wback;
        }
    }
    
    /*public abstract double putValue(int i0, double d );

    public abstract double putValue(int i0, int i1, double d );

    public abstract double putValue(int i0, int i1, int i2, double d );     */
}
