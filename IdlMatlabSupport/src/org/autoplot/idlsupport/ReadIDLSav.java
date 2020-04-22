
package org.autoplot.idlsupport;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * read data from IDL Save File
 * http://www.physics.wisc.edu/~craigm/idl/savefmt/node20.html
 * @author jbf
 */
public class ReadIDLSav {
        
    private static final Logger logger= Logger.getLogger("autoplot.idlsav");
            
    private static final int RECTYPE_ENDMARKER = 6;
    private static final int RECTYPE_TIMESTAMP = 10;
    private static final int RECTYPE_VARIABLE = 2;
    private static final int RECTYPE_VERSION = 14;
    
    private static final int VARFLAG_ARRAY = 4;
    
    /**
     * return the next record buffer, or returns null at the end.
     * @param ch the bytebuffer
     * @param pos the position.
     * @return the record, including the twelve bytes at the beginning
     * @throws IOException 
     */    
    private ByteBuffer readRecord( ByteBuffer ch, int pos ) throws IOException {

        ch.order( ByteOrder.BIG_ENDIAN );
        
        int recType= ch.getInt(pos);
        int endpos= ch.getInt(pos+4);
        
        if ( recType==RECTYPE_ENDMARKER ) {
            return null;
        } else {
            return slice( ch, pos, endpos );
        }
    }
    
    private String readString( ByteBuffer rec, int pos ) {
        int endPos= pos;
        while ( rec.get(endPos)!=0 ) {
            endPos++;
        }
        byte[] mybytes= new byte[endPos-pos];
        rec.position(pos);
        rec.get(mybytes);
        return new String( mybytes );
    }
    
    private void printBuffer( ByteBuffer rec ) {
        for ( int i=0; i<rec.limit(); i++ ) {
            byte c= rec.get(i);
            if ( i % 4 == 0 ) {
                int theInt= rec.getInt(i);
                if (  c>32 && c<128 ) {
                    System.err.println( String.format( "%05d %d (%c)  I4: %d", i, c, c, theInt ) );
                } else {
                    System.err.println( String.format( "%05d %d      I4: %d", i, c, theInt ) );
                }                
            } else {
                if (  c>32 && c<128 ) {
                    System.err.println( String.format( "%05d %d (%c)", i, c, c ) );
                } else {
                    System.err.println( String.format( "%05d %d", i, c) );
                }
            }
        }
    }
    
    private static final int TYPECODE_BYTE=1;
    private static final int TYPECODE_INT16=2;
    private static final int TYPECODE_INT32=3;
    private static final int TYPECODE_FLOAT=4;
    private static final int TYPECODE_DOUBLE=5;
    private static final int TYPECODE_COMPLEX_FLOAT=6;
    private static final int TYPECODE_STRING=7;
    private static final int TYPECODE_STRUCT=8;
    private static final int TYPECODE_COMPLEX_DOUBLE=9;
    private static final int TYPECODE_INT64=14;

    /**
     * return true if the name refers to an array
     * @param in
     * @param name
     * @return 
     */
    public boolean isArray(ByteBuffer in, String name) throws IOException {
        int magic= in.getInt(0);
        if ( magic!=1397882884 ) {
            logger.warning("magic number is incorrect");
        }
        int pos= 4;
        
        ByteBuffer rec= readRecord( in, pos );
        while ( rec!=null ) {
            int type= rec.getInt(0);
            int nextPos= rec.getInt(4);
            logger.log(Level.CONFIG, "RecType: {0} Length: {1}", new Object[]{labelType(type), nextPos-pos});
            switch ( type ) {
                case RECTYPE_VARIABLE:
                    logger.config("variable");
                    String varName= readString( rec, 20 );
                    if ( varName.equals(name) ) {
                        int nextField= ( int)( 4 * Math.ceil( ( varName.length() ) / 4.0 ) ); // Note they use a silly trick where the next field is known to have a 0. 
                        ByteBuffer var= slice( rec, 20+nextField, rec.limit() );
                        TypeDesc td= readTypeDesc(var);
                        return td.isArray();
                    }
                    break;
                case RECTYPE_VERSION:
                    logger.config("version");
                    break;
                case RECTYPE_TIMESTAMP:
                    logger.config("timestamp");
                    break;
                default:
                    logger.config("???");
                    break;
            }
            pos= nextPos;
            rec= readRecord( in, pos );
        }
        throw new IllegalArgumentException("unable to find variable: "+name);
        

    }
        
    private static class TypeDescScalar extends TypeDesc {
        @Override
        Object readData( ByteBuffer buf ) {
            int offs= 12;
            switch ( typeCode ) {
                case TYPECODE_INT16:
                    return (short)buf.getInt(offs);
                case TYPECODE_INT32:
                    return buf.getInt(offs);
                case TYPECODE_FLOAT:
                    return buf.getFloat(offs);
                case TYPECODE_STRING:
                    int len= buf.getInt(offs);
                    byte[] bb= new byte[len];
                    for ( int i=0; i<len; i++ ) {
                        bb[i]= buf.get(offs+8+i);
                    }
                    //buf.get( bb, offs+8, len );
                    try {
                        return new String( bb, "US-ASCII" );
                    } catch (UnsupportedEncodingException ex) {
                        throw new IllegalArgumentException(ex);
                    }
                default:
                    
                    throw new IllegalArgumentException("unsupported");
            }
        }
    }
    
    public static class ArrayDesc {
        int nbytesEl;
        int nbytes;
        int nelements;
        int ndims;
        int nmax;
        int[] dims;
    }
    
    private static class TypeDescArray extends TypeDesc {
        ArrayDesc arrayDesc;
        
        /**
         * read the data as an array.  Note Java's arrays are 1-D,
         * and only 1-D arrays are used to return the data.  For
         * 2-D arrays (and higher dimension), use the convenience
         * method readArrayData to get a Java N-D array.
         * 
         * @param buf
         * @return 
         */
        @Override
        Object readData( ByteBuffer buf ) {
            switch (typeCode) {
                case TYPECODE_INT16: {
                    short[] result= new short[arrayDesc.nelements];
                    for ( int i=0; i<result.length; i++ ) {
                        int offsToArray= 76;
                        result[i]= (short)buf.getInt(offsToArray+4*i);
                    }
                    return result;
                }
                case TYPECODE_INT32: {
                    int[] result= new int[arrayDesc.nelements];
                    for ( int i=0; i<result.length; i++ ) {
                        int offsToArray= 76;
                        result[i]= buf.getInt(offsToArray+4*i);
                    }
                    return result;
                }
                case TYPECODE_INT64: {
                    int[] result= new int[arrayDesc.nelements];
                    for ( int i=0; i<result.length; i++ ) {
                        int offsToArray= 76;
                        result[i]= buf.getInt(offsToArray+8*i);
                    }
                    return result;
                }
                case TYPECODE_FLOAT: {
                    float[] result= new float[arrayDesc.nelements];
                    for ( int i=0; i<result.length; i++ ) {
                        int offsToArray= 76;
                        result[i]= buf.getFloat(offsToArray+4*i);
                    }
                    return result;
                }   
                case TYPECODE_DOUBLE: {
                    double[] result= new double[arrayDesc.nelements];
                    for ( int i=0; i<result.length; i++ ) {
                        int offsToArray= 76;
                        result[i]= buf.getDouble(offsToArray+8*i);
                    }
                    return result;
                }
                default:
                    break;
            }
            return null;
        }
        
        /**
         * read the data into 1-D and 2-D arrays.
         * @param buf
         * @return 
         */
        public Object readArrayData( ByteBuffer buf ) {
            Object flattenedArray= readData(buf);
            if ( flattenedArray==null ) return null;
            
            switch (arrayDesc.ndims) {
                case 1:
                    return flattenedArray;
                case 2:
                    Object result= Array.newInstance( flattenedArray.getClass(), arrayDesc.dims[0] );
                    for ( int i=0; i<arrayDesc.dims[0]; i++ ) {
                        Object a1= Array.newInstance( flattenedArray.getClass().getComponentType(), arrayDesc.dims[1] );
                        int nj= arrayDesc.dims[1];
                        for ( int j=0; j<nj; j++ ) {
                            Array.set( a1, j, Array.get( flattenedArray, i*nj+j ) );
                        }
                        Array.set( result, i, a1 );
                    }
                    return result;
                default:
                    throw new UnsupportedOperationException("only 1-D and 2-D arrays are supported for now.");
            }
        }
        
        
    }
    
    private static abstract class TypeDesc {
        int typeCode;
        int varFlags;
        boolean isArray( ) {
            return ( varFlags & 0x04 ) == 0x04;
        }
        /**
         * read the data, where data is a byte buffer starting
         * with the TypeDesc.
         * @param data
         * @return 
         */
        abstract Object readData( ByteBuffer data );
    }
    
    private TypeDescScalar readTypeDescScalar( ByteBuffer rec ) {
        TypeDescScalar result= new TypeDescScalar();
        result.typeCode= rec.getInt(0);
        result.varFlags= rec.getInt(4);
        return result;
    }
    
    private ArrayDesc readArrayDesc( ByteBuffer rec ) {
        ArrayDesc result= new ArrayDesc();
        if ( rec.getInt(0)!=8 ) {
            throw new IllegalArgumentException("expected 8 for ARRSTART");
        }
        result.nbytesEl= rec.getInt(4);
        result.nbytes= rec.getInt(8);
        result.nelements= rec.getInt(12);
        result.ndims= rec.getInt(16);
        result.nmax= rec.getInt(28);
        result.dims= new int[result.ndims];
        for ( int i=0; i<result.ndims; i++ ){
            result.dims[i]= rec.getInt(32+4*i);
        }
        return result;
    }
    
    private TypeDescArray readTypeDescArray( ByteBuffer rec ) {
        TypeDescArray result= new TypeDescArray();
        result.typeCode= rec.getInt(0);
        result.varFlags= rec.getInt(4);
        result.arrayDesc= readArrayDesc( slice( rec, 8, rec.limit() ) );
        return result;
    }
    
    /**
     * return the TypeDesc, which is after the name.
     * @param typeDescBuf
     * @return 
     */
    private TypeDesc readTypeDesc( ByteBuffer typeDescBuf ) {
        int varFlags= typeDescBuf.getInt(4);
        if ( ( varFlags & 0x04 ) == 0x04 ) {
            return readTypeDescArray(typeDescBuf);
        } else {
            return readTypeDescScalar(typeDescBuf);
        }
    }
    
    private ArrayDesc readArrayDescriptionForVariable( ByteBuffer rec ) {
        int type= rec.getInt(0);
        if ( type!=RECTYPE_VARIABLE ) {
            throw new IllegalArgumentException("not a variable");
        }
        //printBuffer(rec);
        String varName= readString( rec, 20 );
        logger.log(Level.INFO, "variable name is {0}", readString( rec, 20 ));

        int nextField= ( int)( 4 * Math.ceil( ( varName.length() ) / 4.0 ) ); // Note they use a silly trick where the next field is known to have a 0. 

        ByteBuffer var= slice( rec, 20+nextField, rec.limit() );
        TypeDescArray typeDesc= readTypeDescArray( var );
        return typeDesc.arrayDesc;
    }
    
    private Object variable( ByteBuffer rec, Map<String,Object> vars) {
        int type= rec.getInt(0);
        if ( type!=RECTYPE_VARIABLE ) {
            throw new IllegalArgumentException("not a variable");
        }
        //printBuffer(rec);
        String varName= readString( rec, 20 );
        logger.log(Level.INFO, "variable name is {0}", varName );

        int nextField= ( int)( 4 * Math.ceil( ( varName.length() ) / 4.0 ) ); // Note they use a silly trick where the next field is known to have a 0. 

        ByteBuffer var= slice( rec, 20+nextField, rec.limit() );
        TypeDesc typeDesc= readTypeDesc( var );
        
        Object result= typeDesc.readData( var );
        
        vars.put( varName, result );
        
        return result;
        
    }
    
    private ByteBuffer slice( ByteBuffer src, int position, int limit ) {
        int position0= src.position();
        int limit0= src.limit();
        src.position(position);
        src.limit(limit);
        ByteBuffer r1= ByteBuffer.allocate(limit-position);
        r1.put(src.slice());
        r1.flip();
        src.limit(limit0);
        src.position(position0);
        return r1;
    }
    
    private String labelType( int type ) {
        switch (type) {
            case RECTYPE_TIMESTAMP:
                return "timeStamp";                
            case RECTYPE_VERSION:
                return "version";                
            case RECTYPE_VARIABLE:
                return "variable";
            case RECTYPE_ENDMARKER:
                return "endmarker";
            default:
                return "<unsupported>";
        }
    }
    
    public static ByteBuffer readFileIntoByteBuffer( File f ) throws IOException {
        RandomAccessFile aFile = new RandomAccessFile(f,"r");
        FileChannel inChannel = aFile.getChannel();
        long fileSize = inChannel.size();
        ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);        
        int bytesRead= 0;
        while ( bytesRead<fileSize ) {
            bytesRead+= inChannel.read(buffer);
        }
        return buffer;
    }
   
    
    public Map<String,Object> readVars( ByteBuffer in ) throws IOException {
        
        //  2  ch.write(getBytesStr("SR"));

        //  1 ch.write(getBytesByte((byte) 0));
        //  1 ch.write(getBytesByte((byte) 4));

        int magic= in.getInt(0);
        if ( magic!=1397882884 ) {
            logger.warning("magic number is incorrect");
        }
        int pos= 4;
        
        Map<String,Object> result= new LinkedHashMap<>();
        
        ByteBuffer rec= readRecord( in, pos );
        while ( rec!=null ) {
            int type= rec.getInt(0);
            int nextPos= rec.getInt(4);
            logger.log(Level.CONFIG, "RecType: {0} Length: {1}", new Object[]{labelType(type), nextPos-pos});
            switch ( type ) {
                case RECTYPE_VARIABLE:
                    logger.config("variable");
                    variable(rec, result);
                    break;
                case RECTYPE_VERSION:
                    logger.config("version");
                    break;
                case RECTYPE_TIMESTAMP:
                    logger.config("timestamp");
                    break;
                default:
                    logger.config("???");
                    break;
            }
            pos= nextPos;
            rec= readRecord( in, pos );
        }
        return result;
    }

    /**
     * list the names in the IDLSav file.
     * @param in
     * @return the names found.
     * @throws IOException 
     */
    public String[] listVars( ByteBuffer in ) throws IOException {
        int magic= in.getInt(0);
        if ( magic!=1397882884 ) {
            logger.warning("magic number is incorrect");
        }
        int pos= 4;
        
        List<String> names= new ArrayList<>();
        
        ByteBuffer rec= readRecord( in, pos );  
        while ( rec!=null ) {
            int type= rec.getInt(0);
            int nextPos= rec.getInt(4);
            logger.log(Level.CONFIG, "RecType: {0} Length: {1}", new Object[]{labelType(type), nextPos-pos});
            switch ( type ) {
                case RECTYPE_VARIABLE:
                    logger.config("variable");
                    String varName= readString( rec, 20 );
                    names.add(varName);
                    break;
                case RECTYPE_VERSION:
                    logger.config("version");
                    break;
                case RECTYPE_TIMESTAMP:
                    logger.config("timestamp");
                    break;
                default:
                    logger.config("???");
                    break;
            }
            pos= nextPos;
            rec= readRecord( in, pos );
        }
        return names.toArray( new String[names.size()] );
    }
    
    /**
     * scan through the IDLSav and return just the one variable.
     * @param in
     * @param name
     * @return
     * @throws IOException 
     */
    public Object readVar( ByteBuffer in, String name ) throws IOException {
        int magic= in.getInt(0);
        if ( magic!=1397882884 ) {
            logger.warning("magic number is incorrect");
        }
        int pos= 4;
        
        ByteBuffer rec= readRecord( in, pos );
        while ( rec!=null ) {
            int type= rec.getInt(0);
            int nextPos= rec.getInt(4);
            logger.log(Level.CONFIG, "RecType: {0} Length: {1}", new Object[]{labelType(type), nextPos-pos});
            switch ( type ) {
                case RECTYPE_VARIABLE:
                    logger.config("variable");
                    String varName= readString( rec, 20 );
                    if ( varName.equals(name) ) {
                        Map<String,Object> result= new HashMap<>();
                        variable(rec, result);
                        return result.get(name);
                    }
                    break;
                case RECTYPE_VERSION:
                    logger.config("version");
                    break;
                case RECTYPE_TIMESTAMP:
                    logger.config("timestamp");
                    break;
                default:
                    logger.config("???");
                    break;
            }
            pos= nextPos;
            rec= readRecord( in, pos );
        }
        return null;        
        
    }    
    /**
     * scan through the IDLSav and retrieve information about the array.
     * @param in the idlsav loaded into a ByteBuffer.
     * @param name the name of the array
     * @return
     * @throws IOException 
     */
    public ArrayDesc readArrayDesc( ByteBuffer in, String name ) throws IOException {
        int magic= in.getInt(0);
        if ( magic!=1397882884 ) {
            logger.warning("magic number is incorrect");
        }
        int pos= 4;
        
        ByteBuffer rec= readRecord( in, pos );
        while ( rec!=null ) {
            int type= rec.getInt(0);
            int nextPos= rec.getInt(4);
            logger.log(Level.CONFIG, "RecType: {0} Length: {1}", new Object[]{labelType(type), nextPos-pos});
            switch ( type ) {
                case RECTYPE_VARIABLE:
                    logger.config("variable");
                    String varName= readString( rec, 20 );
                    if ( varName.equals(name) ) {
                        int nextField= ( int)( 4 * Math.ceil( ( varName.length() ) / 4.0 ) ); // Note they use a silly trick where the next field is known to have a 0. 
                        ByteBuffer var= slice( rec, 20+nextField, rec.limit() );                        
                        return readTypeDescArray(var).arrayDesc;
                    }
                    break;
                case RECTYPE_VERSION:
                    logger.config("version");
                    break;
                case RECTYPE_TIMESTAMP:
                    logger.config("timestamp");
                    break;
                default:
                    logger.config("???");
                    break;
            }
            pos= nextPos;
            rec= readRecord( in, pos );
        }
        return null;        
    }
    
    public static void main( String[] args ) throws IOException {
        Logger logger= Logger.getLogger("autoplot.idlsav");
        logger.setLevel( Level.FINE );
        Handler h= new ConsoleHandler();
        h.setLevel(Level.ALL);
        logger.addHandler(h);
            
//        FileOutputStream fos = new FileOutputStream(new File("/tmp/test.autoplot.idlsav"));
//        
//        WriteIDLSav widls= new WriteIDLSav();
//        //widls.addVariable( "wxyz", new double[] { 120,100,120,45,46,47,48,49,120,100,120 } );
//        widls.addVariable( "abcd", 240 );
//        //widls.addVariable( "oneval", 19.95 );
//        widls.write(fos);
//        
//        fos.close();

        //RandomAccessFile aFile = new RandomAccessFile(
        //                    "/home/jbf/public_html/autoplot/data/sav/simple.idlsav","r");
        //RandomAccessFile aFile = new RandomAccessFile(
        //                    "/home/jbf/public_html/autoplot/data/sav/vnames.idlsav","r");
        //RandomAccessFile aFile = new RandomAccessFile(
        //                    "/home/jbf/public_html/autoplot/data/sav/scalars.idlsav","r");
        //RandomAccessFile aFile = new RandomAccessFile(
        //                    "/home/jbf/public_html/autoplot/data/sav/arrayVsScalar.idlsav","r");
        //RandomAccessFile aFile = new RandomAccessFile(
        //                    "/home/jbf/public_html/autoplot/data/sav/floats.idlsav","r");
        RandomAccessFile aFile = new RandomAccessFile(
                            "/home/jbf/public_html/autoplot/data/sav/doublearray.idlsav","r");
        FileChannel inChannel = aFile.getChannel();
        long fileSize = inChannel.size();
        ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
        int bytesRead= 0;
        while ( bytesRead<fileSize ) {
            bytesRead+= inChannel.read(buffer);
        }
       
        Map<String,Object> vars= new ReadIDLSav().readVars(buffer);
        vars.get("da");
    }

}
