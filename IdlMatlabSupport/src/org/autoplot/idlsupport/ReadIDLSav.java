
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
    
    private static final int VARFLAG_ARRAY = 0x04;
    private static final int VARFLAG_STRUCT = 0x20;
    
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
    
    /**
     * somehow I didn't notice the length before other strings.  In the Python 
     * code they have "_read_string" and "_read_string_data" which has a 
     * second length.
     * @param rec
     * @param pos
     * @return StringDesc to describe the string.
     */
    private StringData readStringData( ByteBuffer rec, int pos ) {
        int len= rec.getInt(pos);
        byte[] mybytes= new byte[len];
        rec.position(pos+4);
        rec.get(mybytes);
        StringData result= new StringData();
        result.string= new String( mybytes );
        result._lengthBytes= 4 + Math.max( 4, (int)( 4 * Math.ceil( ( len ) / 4.0 ) ) ); 
        return result;
    }
    
    
    private StringData readString( ByteBuffer rec, int pos ) {
        int endPos= pos;
        while ( rec.get(endPos)!=0 ) {
            endPos++;
        }
        byte[] mybytes= new byte[endPos-pos];
        rec.position(pos);
        rec.get(mybytes);
        StringData result= new StringData();
        result.string= new String( mybytes );
        result._lengthBytes= Math.max( 4, (int)( 4 * Math.ceil( ( result.string.length() ) / 4.0 ) ) ); 
        return result;
    }
    
    private static void printBuffer( ByteBuffer rec ) {
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
     * read the TypeDesc for the variable.
     * @param in
     * @param name
     * @return
     * @throws IOException 
     */
    private TypeDesc readTypeDesc( ByteBuffer in, String name ) throws IOException {
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
                    StringData varName= readString( rec, 20 );
                    if ( varName.string.equals(name) ) {
                        int nextField= 20 + varName._lengthBytes;
                        ByteBuffer var= slice( rec, 20+nextField, rec.limit() );
                        TypeDesc td= readTypeDesc(var);
                        return td;
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
    /**
     * return true if the name refers to an array
     * @param in ByteBuffer for the entire file
     * @param name the variable name
     * @return td.isStructure();
     */
    public boolean isArray(ByteBuffer in, String name) throws IOException {
        TypeDesc td= readTypeDesc(in, name);
        return isArray( td.varFlags );
    }
    
    /**
     * return true if the name refers to a structure
     * @param in ByteBuffer for the entire file
     * @param name the variable name
     * @return true if the name refers to a structure
     */
    public boolean isStructure(ByteBuffer in, String name) throws IOException {
        TypeDesc td= readTypeDesc(in, name);
        return isStructure( td.varFlags );
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
    
    /**
     * structure containing a string and metadata used to read it.
     */
    public static class StringData {
        public String string;
        int _lengthBytes; // note not necessarily the length of the string.
        @Override
        public String toString() {
            return string;
        }
    }
    
    /**
     * structure containing an array and dimension information.
     */
    public static class ArrayData {
        public Object array;
        int[] dims;
    }
    
    public static class ArrayDesc {
        int nbytesEl;
        int nbytes;
        int nelements;
        int ndims;
        int nmax;
        int[] dims;
        /**
         * for convenience, keep track of the total length.
         */
        int _lengthBytes;
    }

    public static class TagDesc {
        int offset;
        int typecode;
        int tagflags;
    }
    
    private TagDesc readTagDesc( ByteBuffer rec ) {
        TagDesc result= new TagDesc();
        result.offset= rec.getInt(0);
        result.typecode= rec.getInt(4);
        result.tagflags= rec.getInt(8);
        return result;
    }    
    
    public static class StructDesc {
        int predef;
        int ntags;
        int nbytes;
        TagDesc[] tagtable;
        String[] tagnames;
        ArrayDesc[] arrTable;
        StructDesc[] structTable;
        String className;
        int nsupClasses;
        String[] supClassNames;
        StructDesc[] supClassTable;
        int _lengthBytes; // convenient place to store
    }
    
    private static class TypeDescArray extends TypeDesc {
        ArrayDesc arrayDesc;
        int offsToArray= 76;
        
        private ArrayData makeArrayData( Object array ) {
            ArrayData result= new ArrayData();
            result.array= array;
            result.dims= arrayDesc.dims;
            return result;
        }
        
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
                        result[i]= (short)buf.getInt(offsToArray+4*i);
                    }
                    return makeArrayData( result );
                }
                case TYPECODE_INT32: {
                    int[] result= new int[arrayDesc.nelements];
                    for ( int i=0; i<result.length; i++ ) {
                        result[i]= buf.getInt(offsToArray+4*i);
                    }
                    return makeArrayData( result );
                }
                case TYPECODE_INT64: {
                    int[] result= new int[arrayDesc.nelements];
                    for ( int i=0; i<result.length; i++ ) {
                        result[i]= buf.getInt(offsToArray+8*i);
                    }
                    return makeArrayData( result );
                }
                case TYPECODE_FLOAT: {
                    float[] result= new float[arrayDesc.nelements];
                    for ( int i=0; i<result.length; i++ ) {
                        result[i]= buf.getFloat(offsToArray+4*i);
                    }
                    return makeArrayData( result );
                }   
                case TYPECODE_DOUBLE: {
                    double[] result= new double[arrayDesc.nelements];
                    for ( int i=0; i<result.length; i++ ) {
                        result[i]= buf.getDouble(offsToArray+8*i);
                    }
                    return makeArrayData( result );
                }
                default:
                    break;
            }
            return null;
        }        
        
    }
    
    /**
     * read the data into 1-D and 2-D arrays.  This is provided for reference, but 
     * can be extended to 3-D and higher arrays, if the need arrises.
     * @param data
     * @return 
     */
    public static Object readArrayDataIntoArrayOfArrays( ArrayData data ) {
        Object flattenedArray= data.array;
        if ( flattenedArray==null ) return null;

        switch ( data.dims.length ) {
            case 1:
                return flattenedArray;
            case 2:
                Object result= Array.newInstance( flattenedArray.getClass(), data.dims[0] );
                for ( int i=0; i<data.dims[0]; i++ ) {
                    Object a1= Array.newInstance( flattenedArray.getClass().getComponentType(), data.dims[1] );
                    int nj= data.dims[1];
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

        
    private static class TypeDescStructure extends TypeDesc {
        ArrayDesc arrayDesc;
        StructDesc structDesc;
        int offsetToData;
        
        @Override
        Object readData(ByteBuffer data) {
            LinkedHashMap<String,Object> result= new LinkedHashMap<>();
            int iptr= offsetToData + 4;
            int iarray= 0;
            for ( int i=0; i<structDesc.tagnames.length; i++ ) {
                if ( isArray( structDesc.tagtable[i].tagflags ) ) {
                    TypeDescArray arr1= new TypeDescArray();
                    arr1.arrayDesc= structDesc.arrTable[iarray];
                    arr1.offsToArray= iptr;
                    arr1.typeCode= structDesc.tagtable[i].typecode;
                    arr1.varFlags= structDesc.tagtable[i].tagflags;
                    Object arr= arr1.readData(data);
                    result.put( structDesc.tagnames[i], arr );
                    iarray= iarray+1;
                    iptr= iptr + arr1.arrayDesc.nbytes;
                }
            }
            return result;
        }
        
    }
    
    private static boolean isArray( int varFlags ) {
        return ( varFlags & 0x04 ) == 0x04;
    }
    
    private static boolean isStructure( int varFlags ) {
        return ( varFlags & 0x20 ) == 0x20;
    }
    
    private static abstract class TypeDesc {
        int typeCode;
        int varFlags;
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
            result.dims[result.ndims-1-i]= rec.getInt(32+4*i); 
        }
        result._lengthBytes= 32+4*result.nmax;
        return result;
    }
    
    private StructDesc readStructDesc( ByteBuffer rec ) {
        StructDesc result= new StructDesc();
        if ( rec.getInt(0)!=9 ) {
            throw new IllegalArgumentException("expected 9 for STRUCTSTART");
        }
        StringData name= readString( rec, 4 );
        int nextField= name._lengthBytes + 4;
        
        final int PREDEF_PREDEF= 0x01;
        final int PREDEF_INHERITS= 0x02;
        final int PREDEF_IS_SUPER= 0x02;
        
        result.predef= rec.getInt(nextField+0);
        
        if ( ( result.predef & PREDEF_PREDEF ) == PREDEF_PREDEF ) {
            //not supported.
            logger.warning("PREDEF predefined structures are not supported.");
            return null;
        }
        
        result.ntags= rec.getInt(nextField+4); 
        result.nbytes= rec.getInt(nextField+8);
        result.tagtable= new TagDesc[result.ntags];
        int ipos= nextField + 12;
        
        int narray= 0;
        int nstruct= 0;
        for ( int i=0; i<result.ntags; i++ ) {
            result.tagtable[i]= readTagDesc( slice( rec, ipos, ipos+12 ) );
            if ( ( result.tagtable[i].tagflags & VARFLAG_ARRAY ) == VARFLAG_ARRAY ) {
                narray++;
            }
            if ( ( result.tagtable[i].tagflags & VARFLAG_STRUCT ) == VARFLAG_STRUCT ) {
                nstruct++;
            }
            ipos+= 12;
        }
        
        result.tagnames= new String[result.ntags];
        for ( int i=0; i<result.ntags; i++ ) {
            StringData stringDesc= readStringData( rec, ipos );
            result.tagnames[i]= stringDesc.string;
            ipos+= stringDesc._lengthBytes;
        }
        
        result.arrTable= new ArrayDesc[narray];
        for ( int i=0; i<narray; i++ ) {
            result.arrTable[i]= readArrayDesc( slice( rec, ipos, rec.limit() ) );
            ipos+= result.arrTable[i]._lengthBytes;           
        }
        
        result.structTable= new StructDesc[nstruct];
        for ( int i=0; i<nstruct; i++ ) {
            result.structTable[i]= readStructDesc( slice( rec, ipos, rec.limit() ) );
            ipos+= result.structTable[i]._lengthBytes;           
        }
        if ( ( result.predef & PREDEF_INHERITS ) == PREDEF_INHERITS
              || ( result.predef & PREDEF_IS_SUPER ) == PREDEF_IS_SUPER ) {
            //StringDesc stringDesc= readStringData( rec, ipos );
            //result.className= stringDesc.string;
            //result.nsupClasses= rec.getInt(ipos);
            logger.warning("PREDEF classes are not supported.");
            return null;
        } else {
            result._lengthBytes= ipos;
            return result;
        }
                
    }
    
    private TypeDescStructure readTypeDescStructure( ByteBuffer rec ) {
        TypeDescStructure result= new TypeDescStructure();
        result.typeCode= rec.getInt(0);
        result.varFlags= rec.getInt(4);
        result.arrayDesc= readArrayDesc( slice( rec, 8, rec.limit() ) );
        result.structDesc= readStructDesc( slice( rec, 10*4+result.arrayDesc.nmax*4, rec.limit() ) );
        result.offsetToData= 10*4+result.arrayDesc.nmax*4 + result.structDesc._lengthBytes;
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
        int typeCode= typeDescBuf.getInt(0);
        if ( typeCode<0 || typeCode>14 ) {
            throw new IllegalArgumentException("expected 0-14 for type code in readTypeDesc");
        }
        int varFlags= typeDescBuf.getInt(4);
        if ( ( varFlags & VARFLAG_STRUCT ) == VARFLAG_STRUCT ) {
            return readTypeDescStructure(typeDescBuf);
        } else if ( ( varFlags & VARFLAG_ARRAY ) == VARFLAG_ARRAY ) {
            return readTypeDescArray(typeDescBuf);
        } else {
            return readTypeDescScalar(typeDescBuf);
        }
    }
    
    /**
     * read the scalar, array, or structure at this position.  An
     * array is returned flattened, and readTypeDesc should be used
     * to unflatten it.  Structures are returned as a LinkedHashMap.
     * @param rec
     * @param vars
     * @return 
     */
    private Object variable( ByteBuffer rec, Map<String,Object> vars) {
        int type= rec.getInt(0);
        if ( type!=RECTYPE_VARIABLE ) {
            throw new IllegalArgumentException("not a variable");
        }
        //printBuffer(rec);
        StringData varName= readString( rec, 20 );
        logger.log(Level.INFO, "variable name is {0}", varName );

        int nextField= 20 + varName._lengthBytes;

        ByteBuffer var= slice( rec, nextField, rec.limit() );
        TypeDesc typeDesc= readTypeDesc( var );
        
        Object result= typeDesc.readData( var );
        
        vars.put( varName.string, result );
        
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
            if ( rec.getInt(8)!=0 ) {
                throw new IllegalArgumentException("records bigger than 2**32 bytes are not supported.");
            }
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
     * list the names in the IDLSav file.  This is only the supported
     * variable types.
     * @param in
     * @return the names found.
     * @throws IOException 
     */
    public String[] readVarNames( ByteBuffer in ) throws IOException {
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
                    StringData varName= readString( rec, 20 );

                    int nextField= varName._lengthBytes;

                    ByteBuffer var= slice( rec, 20+nextField, rec.limit() );
                    TypeDesc typeDesc= readTypeDesc( var );
                    
                    if ( !isStructure(typeDesc.varFlags) ) {
                        names.add(varName.string); 
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
                    StringData varName= readString( rec, 20 );
                    if ( varName.string.equals(name) ) {
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
                    StringData varName= readString( rec, 20 );
                    if ( varName.string.equals(name) ) {
                        int nextField= varName._lengthBytes;
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
        //RandomAccessFile aFile = new RandomAccessFile(
        //                /home/jbf/public_html/autoplot/data/sav/structureOfLonarr.idlsav    "/home/jbf/public_html/autoplot/data/sav/doublearray.idlsav","r");
        RandomAccessFile aFile = new RandomAccessFile(
                            "/home/jbf/public_html/autoplot/data/sav/structureOfLonarr.idlsav","r");
        //RandomAccessFile aFile = new RandomAccessFile(
        //                    "/home/jbf/public_html/autoplot/data/sav/structure.idlsav","r");
        //RandomAccessFile aFile = new RandomAccessFile(
        //                    "/home/jbf/public_html/autoplot/data/sav/structureWithinStructure.idlsav","r");
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
