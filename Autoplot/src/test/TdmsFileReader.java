
package test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/**
 *
 * @author jbf
 */
public class TdmsFileReader {
    
    FileChannel ch;
    ByteBuffer buf;
    
    long pointer;
   
    private static final int TDMS_SEG_LEADIN_LENGTH = 28;
    
    public TdmsFileReader( File tdmsFile ) throws FileNotFoundException, IOException {
        ch= new FileInputStream(tdmsFile).getChannel();
        this.buf= ch.map( FileChannel.MapMode.READ_ONLY, 0, tdmsFile.length() );        
        this.buf.order( ByteOrder.LITTLE_ENDIAN );
        ch.close();
        pointer= 0;
        setByteOrder(this.buf);
    }
    
    /**
     * set the byte order based on the flag in ToC mask.
     * @param buf 
     */
    private static void setByteOrder( ByteBuffer buf ) {
        if ( buf.get(0)!=0x54 && buf.get(1)!=0x44 
                && buf.get(2)!=0x53 && buf.get(3)!=0x6d ) {
            throw new IllegalArgumentException("should be a TDSm flag.");
        }
        int t= 1<<6;
        if ( ( buf.getInt(4) & t ) == t ) {
            buf.order(ByteOrder.BIG_ENDIAN);
        } else {
            buf.order(ByteOrder.LITTLE_ENDIAN);
        }
    }
    
    /**
     * return the next segment
     * @return the next segment
     */
    public TdmsSegment getNextSegment() {
        buf.position((int)pointer);
        
        ByteBuffer buf1= buf.slice();
        buf1.order( buf.order() );
        
        if ( buf1.get(0)!=0x54 && buf1.get(1)!=0x44 
                && buf1.get(2)!=0x53 && buf1.get(3)!=0x6d ) {
            throw new IllegalArgumentException("should be a TDSm flag.");
        }
                
        //showBytes( buf1 );
        if ( buf1.getInt(8)!=4713 ) {
            throw new IllegalArgumentException("should be a version number here at byte offset 8.");
        }
        long nextPointer= pointer + buf.getLong((int)(pointer+12)) + TDMS_SEG_LEADIN_LENGTH;
        long oldPointer= this.pointer;
        this.pointer= nextPointer;
        return new TdmsSegment( buf1, oldPointer );
    }
    
    private static String showBytes( ByteBuffer buf ) {
        StringBuilder sb= new StringBuilder("buf order is ");
        sb.append( buf.order()==ByteOrder.BIG_ENDIAN ? "bigEndian" : "littleEndian" );
        sb.append( "\n");
        for ( int i= 0; i<28; i++ ) {
            sb.append( String.format( "%02d ", (int)i ) );
        }
        sb.append( "\n" );
        for ( int i= 0; i<28; i++ ) {
            sb.append( String.format( "%02x ", (int)buf.get(i) ) );
        }
        sb.append( "\n" );
        return sb.toString();
    }
    
    /**
     * return true if there are more segments in the file.
     * @return true if there are more segments in the file. 
     */
    public boolean hasNextSegment() {
        return this.pointer<buf.limit();
    }
    
    public static class TdmsSegment {
        ByteBuffer buf;        
        long offset;
        int type;
        ByteOrder byteOrder;
        
        public TdmsSegment( ByteBuffer buf, long offset ) {
            this.buf= buf;
            this.offset= offset;
            this.type= buf.getInt(4);
            this.byteOrder= ByteOrder.LITTLE_ENDIAN;
            if ( !( this.buf.get(0)==0x54 
                    || this.buf.get(1)==0x44 
                    || this.buf.get(2)==0x53 
                    || this.buf.get(3)==0x6d ) ) {
                throw new IllegalArgumentException("expected Tdms"
                        + "segment lead in to start with TDSm" );
            }
            this.byteOrder= buf.order();
        }
        
        public ByteBuffer getRawData() {
                    
            long nextSegmentOffset= buf.getLong(12);
            long rawDataOffset= buf.getLong(20);
            if (nextSegmentOffset>Integer.MAX_VALUE) throw new IllegalArgumentException("too big for Java NIO");
            if (rawDataOffset>Integer.MAX_VALUE) throw new IllegalArgumentException("too big for Java NIO");
            if (rawDataOffset>nextSegmentOffset) throw new IllegalArgumentException("rawDataOffset is greater than nextSegmentOffset");
            buf.position((int)rawDataOffset);
            buf.limit((int)nextSegmentOffset);
            ByteBuffer sl= buf.slice();
            showBytes(sl);
            sl.order( this.byteOrder );
            return sl;

        }
        
        /**
         * return the offset into the file.
         * @return  the offset into the file.
         */
        public long getOffset() {
            return this.offset;
        }
        
        
        @Override
        public String toString() {
            return "seg type " + type + " @ " + this.offset;
        }
    }
    
}
