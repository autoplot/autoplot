/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.idlsupport;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Calendar;

/**
 * write data to IDL Save File
 * http://www.physics.wisc.edu/~craigm/idl/savefmt/node20.html
 * @author jbf
 */
public final class WriteIDLSav {
    public static final int DATATYPE_DOUBLE = 5;
    public static final int RECTYPE_ENDMARKER = 6;
    public static final int RECTYPE_TIMESTAMP = 10;
    public static final int RECTYPE_VARIABLE = 2;
    public static final int RECTYPE_VERSION = 14;
    public static final int VARFLAG_ARRAY = 4;

    private static ByteBuffer timestamp() {
        ByteBuffer date= writeString( "Sat Feb 11 08:43:55 2012" ); //Calendar.getInstance().toString() );
        ByteBuffer user= writeString( "jbf" );
        ByteBuffer host= writeString( "Jeremy-Fadens-MacBook-Air.local" ); //TODO: get these from java props
        ByteBuffer result= ByteBuffer.allocateDirect( 257*4 + date.limit() + user.limit() + host.limit() );
        for ( int i=0; i<257*4; i++ ) {
            result.put((byte)0);
        }
        result.put(date); System.err.println(4+result.position());
        result.put(user); System.err.println(4+result.position());
        result.put(host); System.err.println(4+result.position());
        result.flip();
        return result;
    }

    private static ByteBuffer version() {
        ByteBuffer format= ByteBuffer.allocateDirect(4);
        format.order(ByteOrder.BIG_ENDIAN);
        format.putInt(9);
        format.flip();
        ByteBuffer arch= writeString( "x86_64");
        ByteBuffer os= writeString( "darwin");
        ByteBuffer release= writeString("8.1");
        ByteBuffer result= ByteBuffer.allocateDirect( 4 + 4 + arch.limit() + os.limit() + release.limit() );
        result.putInt( 0 );
        result.put( format );
        result.put( arch );
        result.put( os );
        result.put( release );
        result.flip();
        return result;
    }

    static ByteBuffer getBytesStr( String s ) {
        return ByteBuffer.wrap(s.getBytes());
    }

    static ByteBuffer getBytesByte( byte b ) {
        return ByteBuffer.wrap(new byte[]{b});
    }

    private static  ByteBuffer writeString( String s ) {
        int len= 4 * (int)Math.ceil( ( s.length()+4 ) / 4. );
        ByteBuffer result= ByteBuffer.allocateDirect(len);
        result.order( ByteOrder.BIG_ENDIAN );
        result.putInt(s.length());
        try {
            result.put(s.getBytes("US-ASCII"));
        } catch ( UnsupportedEncodingException ex ) { } ; // this doesn't happen with JavaSE
        for ( int i=result.position(); i<result.limit(); i++  ) {
            result.put((byte)0);
        }
        result.flip();
        return result;
    }

    private static ByteBuffer writeArrayDesc( Object data ) {
        int nmax= 8; // ?? see python code.
        int capacity= ( 8 + nmax ) * 4; //TODO: 1D
        int eleLen= 8; // bytes
        int ndims= 1;

        ByteBuffer result= ByteBuffer.allocateDirect( capacity );
        result.order(ByteOrder.BIG_ENDIAN);
        result.putInt( 8 ); // normal array.  There's a 64-bit array read as well that is not implemented.
        result.putInt( eleLen ); // bytes per element TODO: only 8 byte.
        result.putInt( Array.getLength(data)*eleLen ); //TODO: 1D
        result.putInt( Array.getLength(data) );
        result.putInt( ndims );
        result.putInt( 0 );
        result.putInt( 0 );

        result.putInt( nmax );

        for ( int i=0; i<nmax; i++ ) {
            result.putInt( i==0 ? Array.getLength(data) : 1 ); //TODO: guess
        }

        result.flip();
        return result;
    }

    private static ByteBuffer writeShortDesc( Object data ) {
        int nmax= 8; // ?? see python code.
        int capacity= ( 8 + nmax ) * 4; //TODO: 1D
        int eleLen= 8; // bytes
        int ndims= 1;

        ByteBuffer result= ByteBuffer.allocateDirect( capacity );
        result.order(ByteOrder.BIG_ENDIAN);
        result.putInt( 8 ); // normal array.  There's a 64-bit array read as well that is not implemented.
        result.putInt( eleLen ); // bytes per element TODO: only 8 byte.
        result.putInt( Array.getLength(data)*eleLen ); //TODO: 1D
        result.putInt( Array.getLength(data) );
        result.putInt( ndims );
        result.putInt( 0 );
        result.putInt( 0 );

        result.putInt( nmax );

        for ( int i=0; i<nmax; i++ ) {
            result.putInt( i==0 ? Array.getLength(data) : 1 ); //TODO: guess
        }

        result.flip();
        return result;
    }

    private static ByteBuffer writeTypeDesc( Object data ) {
        if ( data.getClass().isArray() ) {
            return writeArrayDesc( data );
        } else if ( data.getClass()==short.class ) {
            return writeShortDesc( data );
        } else {
            throw new RuntimeException("not implemented");
        }
    }

    private static ByteBuffer writeDoubleArray( double[] data ) {
        ByteBuffer buf= ByteBuffer.allocateDirect( data.length*8 );
        buf.order(ByteOrder.BIG_ENDIAN);
        for ( int i=0; i<data.length; i++ ) {
            buf.putDouble(data[i]);
        }
        buf.flip();
        return buf;
    }

    private static ByteBuffer writeTypeDescArray( Object data ) {

        ByteBuffer arrayDesc= writeArrayDesc(data);
        ByteBuffer result= ByteBuffer.allocateDirect( 8 + arrayDesc.limit() );
        result.order(ByteOrder.BIG_ENDIAN);
        result.putInt( DATATYPE_DOUBLE );
        result.putInt( VARFLAG_ARRAY);
        result.put(arrayDesc);

        result.flip();
        return result;
    }

    private static ByteBuffer variable( String name, Object data ) {

        ByteBuffer nameBuf= writeString( name.toUpperCase() );
        ByteBuffer typedesc= writeTypeDesc( data );

        ByteBuffer varData;
        if ( data.getClass().isArray() && data.getClass().getComponentType()==double.class ) {
            varData= writeDoubleArray( (double[])data );
        } else {
            throw new RuntimeException("not supported "+data.getClass());
        }

        ByteBuffer result= ByteBuffer.allocateDirect( nameBuf.limit() + typedesc.limit() + 4 + varData.limit() );
        result.order(ByteOrder.BIG_ENDIAN);
        result.put(nameBuf);
        result.put(typedesc);
        result.putInt(7);
        result.put(varData);

        result.flip();
        return result;
    }

    private static ByteBuffer endMarker() {
        ByteBuffer result= ByteBuffer.allocate(4);
        for ( int i=0; i<4; i++ ) result.put((byte)0);
        result.flip();
        return result;
    }


     private static int writeRecord( FileChannel ch, int recType, ByteBuffer buf, int pos ) throws IOException {

        int len= (int)( 4 * Math.ceil( ( buf.limit()+12. ) / 4 ) );

        ByteBuffer rec= ByteBuffer.allocateDirect( len );
        rec.order( ByteOrder.BIG_ENDIAN );

        rec.putInt( recType );

        if ( recType==RECTYPE_ENDMARKER ) {
            rec.putInt( 0 );
        } else {
            rec.putInt( pos + len );
        }
        rec.putInt( 0 ); // skip 4 bytes.
        rec.put(buf);

        int padBytes=0;
        for ( int i=rec.position(); i<rec.limit(); i++ ) {
            rec.put((byte)0);  // pad out to 4 byte boundary
            padBytes++;
        }

        rec.flip();

        System.err.println( "writing "+recType + " to buffer bytes "+ pos + " to " + ( pos + rec.limit() ) + " " + padBytes );
        ch.write(rec);

        pos+= rec.limit();
        return pos;
    }

    public static void main(String[] args) throws FileNotFoundException, IOException {

        FileOutputStream fos = new FileOutputStream(new File("/Users/jbf/ct/autoplot/idlsave/channel.idlsav"));
        FileChannel ch = fos.getChannel();

        ch.write(getBytesStr("SR"));

        ch.write(getBytesByte((byte) 0));
        ch.write(getBytesByte((byte) 4));

        int pos= 4;
        pos= writeRecord( ch, RECTYPE_TIMESTAMP, timestamp(), pos );
        pos= writeRecord( ch, RECTYPE_VERSION, version(), pos );
        //writeRecord( ch, RECTYPE_VARIABLE, variable( "xyxyxyxy", new double[] { 45,46,47,48,49 } ), pos );
        writeRecord( ch, RECTYPE_VARIABLE, variable( "myvar", (short)99 ), pos );
        pos= writeRecord( ch, RECTYPE_ENDMARKER, endMarker(), pos );
        ch.close();

        // variable
//throw new IllegalArgumentException("This is a work in progress....");

    }
}
