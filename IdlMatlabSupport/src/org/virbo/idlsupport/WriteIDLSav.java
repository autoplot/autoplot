/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.idlsupport;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

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

    private String nameFor( int type ) {
        if ( type==RECTYPE_VARIABLE ) {
                return "VARIABLE";
        } else if ( type==RECTYPE_TIMESTAMP ) {
          return "TIMESTAMP";
        } else if ( type==RECTYPE_VERSION) {
          return "VERSION";
        } else if ( type==RECTYPE_ENDMARKER ) {
          return "ENDMARKER";
        } else {
            return ""+type;
        }
    }
    
    private ByteBuffer timestamp() {
        //ByteBuffer date= writeString( "Sat Feb 11 08:43:55 2012" ); //Calendar.getInstance().toString() );
        //ByteBuffer user= writeString( "jbf" );
        //ByteBuffer host= writeString( "Jeremy-Fadens-MacBook-Air.local" ); //TODO: get these from java props
        //ByteBuffer date= writeString( "Mon Feb 20 06:49:42 2012" ); //Calendar.getInstance().toString() );
        //ByteBuffer user= writeString( "jbf" );
        //ByteBuffer host= writeString( "spot5" ); //TODO: get these from java props

        ByteBuffer date= writeString( new java.util.Date().toString() ); //Calendar.getInstance().toString() );
        ByteBuffer user= writeString( System.getProperty("user.name") );

        String shost;
        try {
            shost=  InetAddress.getLocalHost().getHostName();
        } catch ( UnknownHostException ex ) {
            shost= "localhost"; // this shouldn't happen
        }
        ByteBuffer host= writeString( shost );

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

    private ByteBuffer version() {
        ByteBuffer format= ByteBuffer.allocateDirect(4);
        format.order(ByteOrder.BIG_ENDIAN);
        format.putInt(9);
        format.flip();
        ByteBuffer arch= writeString( System.getProperty("os.arch") );
        ByteBuffer os= writeString( System.getProperty("os.name"));
        ByteBuffer release= writeString("(Autoplot)");
        
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

    private ByteBuffer writeString( String s ) {
        int len= 4 * (int)Math.ceil( ( s.length()+4 ) / 4. );
        ByteBuffer result= ByteBuffer.allocateDirect(len);
        result.order( ByteOrder.BIG_ENDIAN );
        result.putInt(s.length());
        try {
            result.put(s.getBytes("US-ASCII"));
        } catch ( UnsupportedEncodingException ex ) { // this doesn't happen with JavaSE
            throw new RuntimeException(ex);
        } 
        for ( int i=result.position(); i<result.limit(); i++  ) {
            result.put((byte)0);
        }
        result.flip();
        return result;
    }

    private ByteBuffer writeArrayDesc( Object data ) {
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
        result.putInt( 0 );  // should be 1*256+83
        result.putInt( 0 );  // should be 1*256+83

        result.putInt( nmax );

        for ( int i=0; i<nmax; i++ ) {
            result.putInt( i==0 ? Array.getLength(data) : 1 ); //TODO: guess
        }

        result.flip();
        return result;
    }

    private int dataTypeCode( Object data ) {
        if ( data.getClass()==Short.class ) {
            return 2;
        } else if ( data.getClass()==Integer.class ) {
            return 3;
        } else if ( data.getClass()==Float.class ) {
            return 4;
        } else if ( data.getClass()==Double.class ) {
            return 5;
        } else {
            throw new IllegalArgumentException("unsupported type: "+data.getClass() );
        }
    }

    private ByteBuffer writeScalarDesc( Object data ) {
        
        ByteBuffer result= ByteBuffer.allocateDirect( 8 );
        result.order(ByteOrder.BIG_ENDIAN);
        result.putInt( dataTypeCode(data) ); // 2=16bit 3=32bit int 5=float,etc.
        result.putInt( 0);  // bytes per element TODO: only 8 byte.

        result.flip();
        return result;
    }

    private ByteBuffer writeTypeDesc( Object data ) {
        if ( data.getClass().isArray() ) {
            return writeArrayDesc( data );
        } else if ( data.getClass()==Short.class ) {
            return writeScalarDesc( data );
        } else if ( data.getClass()==Integer.class ) {
            return writeScalarDesc( data );
        } else if ( data.getClass()==Float.class ) {
            return writeScalarDesc( data );
        } else if ( data.getClass()==Double.class ) {
            return writeScalarDesc( data );
        } else {
            throw new RuntimeException("not implemented");
        }
    }

    private ByteBuffer writeDoubleArray( double[] data ) {
        ByteBuffer buf= ByteBuffer.allocateDirect( data.length*8 );
        buf.order(ByteOrder.BIG_ENDIAN);
        for ( int i=0; i<data.length; i++ ) {
            buf.putDouble(data[i]);
        }
        buf.flip();
        return buf;
    }

    private ByteBuffer writeLongArray( long[] data ) {
        ByteBuffer buf= ByteBuffer.allocateDirect( data.length*8 );
        buf.order(ByteOrder.BIG_ENDIAN);
        for ( int i=0; i<data.length; i++ ) {
            buf.putLong(data[i]);
        }
        buf.flip();
        return buf;
    }
    
    private ByteBuffer writeShort( short data ) {
        ByteBuffer buf= ByteBuffer.allocateDirect( 4 );
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putShort((short)0);
        buf.putShort(data);
        buf.flip();
        return buf;
    }    

    private ByteBuffer writeTypeDescArray( Object data ) {

        ByteBuffer arrayDesc= writeArrayDesc(data);
        ByteBuffer result= ByteBuffer.allocateDirect( 8 + arrayDesc.limit() );
        result.order(ByteOrder.BIG_ENDIAN);
        result.putInt( DATATYPE_DOUBLE );
        result.putInt( VARFLAG_ARRAY);
        result.put(arrayDesc);

        result.flip();
        return result;
    }

    /**
     * format the data
     * @param name an IDL identifier.
     * @param data a supported type.
     * @param pos
     * @return the encoding of variable into a reset ByteBuffer.
     */
    private ByteBuffer variable( String name, Object data, long pos ) {

        checkVariableType(name, data);

        ByteBuffer nameBuf= writeString( name.toUpperCase() );
        ByteBuffer typedesc= writeTypeDesc( data );

        ByteBuffer varData;
        if ( data.getClass().isArray() && data.getClass().getComponentType()==double.class ) {
            varData= writeDoubleArray( (double[])data );
        } else if (data.getClass().isArray() && data.getClass().getComponentType() == long.class) {
            varData= writeLongArray( (long[])data );
        } else if ( data.getClass()==Short.class ) {
            varData= writeShort( (Short)data );
        } else {
            throw new RuntimeException("not supported "+data.getClass());
        }

        ByteBuffer result= ByteBuffer.allocateDirect( 4 + nameBuf.limit() + 8 + typedesc.limit() + 4 + varData.limit() );
        result.order(ByteOrder.BIG_ENDIAN);
        result.put(ByteBuffer.allocateDirect( 4 ) );
        result.put(nameBuf);
        //result.put(ByteBuffer.allocateDirect( 8 ) );
        result.putInt(5);   // why?
        result.putInt(20);  // why?
        result.put(typedesc);
        result.putInt(7);
        result.put(varData);

        result.flip();
        return result;
    }

    private ByteBuffer endMarker() {
        ByteBuffer result= ByteBuffer.allocate(4);
        for ( int i=0; i<4; i++ ) result.put((byte)0);
        result.flip();
        return result;
    }


     private int writeRecord( WritableByteChannel ch, int recType, ByteBuffer buf, int pos ) throws IOException {

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

        String stype= nameFor( recType ) ;


        //System.err.println( "writing "+stype + " to buffer bytes "+ pos + " to " + ( pos + rec.limit() ) + " " + padBytes );
        System.err.printf( "%d %d %s\n", pos, pos +rec.limit(), stype );
        ch.write(rec);

        pos+= rec.limit();
        return pos;
    }

    private LinkedHashMap<String,Object> variables= new LinkedHashMap();

    public void checkVariableType( String name, Object data ) {
        Class c= data.getClass();
        if ( !( c.isArray() && ( c.getComponentType()==double.class || c.getComponentType()==long.class ) ) && c!=Short.class ) {
            throw new IllegalArgumentException("\"" + name + "\" is unsupported data type: "+data.getClass() );
        }
    }

    public void addVariable( String name, Object data ) {
        checkVariableType( name, data );
        variables.put(name, data);
    }

    public void write( OutputStream out ) throws IOException {

        WritableByteChannel ch= Channels.newChannel(out);

        ch.write(getBytesStr("SR"));

        ch.write(getBytesByte((byte) 0));
        ch.write(getBytesByte((byte) 4));

        int pos= 4;
        pos= writeRecord( ch, RECTYPE_TIMESTAMP, timestamp(), pos );
        pos= writeRecord( ch, RECTYPE_VERSION, version(), pos );

        for ( Entry<String,Object> var: variables.entrySet() ) {
            pos= writeRecord( ch, RECTYPE_VARIABLE, variable( var.getKey(), var.getValue(), pos ), pos );
        }

        pos= writeRecord( ch, RECTYPE_ENDMARKER, endMarker(), pos );
        ch.close();

    }

    public static void main(String[] args) throws FileNotFoundException, IOException {

        FileOutputStream fos = new FileOutputStream(new File("/tmp/test.autoplot.idlsav"));
        
        WriteIDLSav widls= new WriteIDLSav();
        widls.addVariable( "myvar", new double[] { 120,100,120,45,46,47,48,49,120,100,120 } );
        widls.addVariable( "second", new double[] { -1,-1,-2,-3,-3,4,5,6,7,7,8,9,9,10 } );
        widls.addVariable( "mylong", new long[] { -1, 100000, 100000000000L } );

        //widls.addVariable( "oneval", 19.95 );
        widls.write(fos);

        fos.close();
    }
}
