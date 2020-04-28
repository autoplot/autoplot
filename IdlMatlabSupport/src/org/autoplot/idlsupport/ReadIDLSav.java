
package org.autoplot.idlsupport;

import java.io.File;
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
import java.util.Map.Entry;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Read data from IDL Save Files.  This was written using
 * http://www.physics.wisc.edu/~craigm/idl/savefmt/node20.html
 * https://cow.physics.wisc.edu/~craigm/idl/savefmt.pdf
 * and https://github.com/scipy/scipy/blob/master/scipy/io/idl.py
 * for reference, and with no involvement from individuals at
 * Harris Geospacial.  No warrenties are implied and this must
 * be used at your own risk.
 * 
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
     * return the size of the IDL data type in bytes.  Note shorts are stored
     * in 4-bytes.
     * @param typeCode
     * @return 
     */
    private static int sizeOf( int typeCode ) {
        int[] sizes= new int[] { 0, 4, 4, 4, 4,   8, 8, 0, 0, 16,   0, 0, 0, 0, 8 };
        return sizes[typeCode];
    }
    
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
                        ByteBuffer var= slice( rec, nextField, rec.limit() );
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
        int offs= 12;
        @Override
        Object readData( ByteBuffer buf ) {
            switch ( typeCode ) {
                case TYPECODE_INT16:
                    return (short)buf.getInt(offs);
                case TYPECODE_INT32:
                    return buf.getInt(offs);
                case TYPECODE_INT64:
                    return buf.getLong(offs);
                case TYPECODE_FLOAT:
                    return buf.getFloat(offs);
                case TYPECODE_DOUBLE:
                    return buf.getDouble(offs);
                case TYPECODE_STRING:
                    int len= buf.getInt(offs);
                    byte[] bb= new byte[len];
                    for ( int i=0; i<len; i++ ) {
                        bb[i]= buf.get(offs+8+i);
                    }
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
        Object array;
        int[] dims;
        /** 
         * number of bytes within the IDLSAV file.
         */
        int _lengthBytes; 
        @Override
        public String toString() {
            StringBuilder b= new StringBuilder("["+dims[0]);
            for ( int i=1; i<dims.length; i++ ) {
                b.append(",").append(dims[i]);
            }
            b.append("]");
            return "" + this.array.getClass().getComponentType().getName() + b.toString();            
        }
    }
    
    public static class ArrayDesc {
        int nbytesEl;
        int nbytes;
        int nelements;
        int ndims;
        int nmax;
        int[] dims;
        /**
         * for convenience, keep track of the total length of the descriptor within the IDLSAV file.
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
        /**
         * length of the descriptor within the IDLSAV file.
         */
        int _lengthBytes; 
    }
    
    private static class TypeDescArray extends TypeDesc {
        ArrayDesc arrayDesc;
        int offsToArray= 76;
        int _lengthBytes; // length of the array.
        
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
            _lengthBytes= sizeOf(typeCode) * arrayDesc.nelements;
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
        
        @Override
        public String toString() {
            StringBuilder b= new StringBuilder("["+this.arrayDesc.dims[0]);
            for ( int i=1; i<this.arrayDesc.ndims; i++ ) {
                b.append(",").append(this.arrayDesc.dims[i]);
            }
            b.append("]");
            return "" + this.typeCode + b.toString();
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

    private static Class getPrimativeClass( Class t ) {
        if ( t==Integer.class ) {
            return int.class;
        } else if ( t==Long.class ) {
            return long.class;
        } else if ( t==Short.class ) {
            return short.class;
        } else if ( t==Double.class ) {
            return double.class;
        } else if ( t==Float.class ) {
            return float.class;
        } else {
            throw new UnsupportedOperationException("not implemented: "+t);
        }
    }
        
    private static void accumulate( Map<String,Object> accumulator, Map<String,Object> rec, int j, int nj ) {
        if ( accumulator.entrySet().isEmpty() ) {
            for ( Entry<String,Object> e: rec.entrySet() ) {
                Object o;
                if ( e.getValue() instanceof ArrayData ) {
                    ArrayData ad= (ArrayData)e.getValue(); // Java 14 is coming and we won't need a cast, so exciting.
                    ArrayData ac= new ArrayData();
                    ac.dims= new int[ad.dims.length+1];
                    ac.dims[0]= nj;
                    System.arraycopy( ad.dims, 0, ac.dims, 1, ad.dims.length );
                    ac.array= Array.newInstance( ad.array.getClass(), nj );
                    Array.set( ac.array, j, ad.array );
                    o= ac;
                } else if ( e.getValue() instanceof Map ) {
                    Map accumulator1= new LinkedHashMap();
                    accumulate( accumulator1, (Map<String,Object>)e.getValue(), j, nj );
                    o= accumulator1;
                } else  {
                    Object d= e.getValue();
                    ArrayData ac= new ArrayData();
                    ac.dims= new int[] { nj };
                    Class t= getPrimativeClass( d.getClass() );
                    ac.array= Array.newInstance( t, nj );
                    Array.set( ac.array, j, d );
                    o= ac;
                }
                accumulator.put( e.getKey(), o );
            }
        }
        for ( Entry<String,Object> e: accumulator.entrySet() ) {
            Object o= rec.get(e.getKey());
            if ( o instanceof ArrayData ) {
                ArrayData ad= (ArrayData)o;
                ArrayData ac= (ArrayData)e.getValue();
                Array.set( ac.array, j, ad.array );
            } else if ( e.getValue() instanceof Map ) {
                accumulate( (Map<String,Object>)e.getValue(), (Map<String,Object>)o, j, nj);
            } else {
                ArrayData ac= (ArrayData)e.getValue();
                Array.set( ac.array, j, o );
            }
        }
    }
    
    private static class TypeDescStructure extends TypeDesc {
        ArrayDesc structArrayDesc;
        StructDesc structDesc;
        int offsetToData;
        boolean isSubstructure; // structure within a structure.
        /**
         * length of the data within the IDLSav file.
         */
        int _lengthBytes;
        
        @Override
        Object readData(ByteBuffer data) {
            LinkedHashMap<String,Object> result;
            int nj= structArrayDesc.nelements;
            if ( nj>1 ) {
                result= new LinkedHashMap<>();
                int iptr= offsetToData + ( isSubstructure ? 0 : 4 );
                int iptr0= iptr;
                for ( int j=0; j<nj; j++ ) {
                    int iarray= 0;
                    int istructure= 0;
                    for ( int i=0; i<structDesc.tagnames.length; i++ ) {
                        if ( isStructure( structDesc.tagtable[i].tagflags ) ) {
                            TypeDescStructure struct1= new TypeDescStructure();
                            StructDesc structDesc1= structDesc.structTable[istructure];
                            struct1.structDesc= structDesc1;
                            struct1.structArrayDesc= structDesc.arrTable[iarray];
                            struct1.offsetToData= iptr;
                            struct1.isSubstructure= true;
                            Object map1= struct1.readData(data);                        
                            if ( j==0 ) {
                                Map mapd= (Map)map1;
                                Map accumulator= new LinkedHashMap();
                                accumulate( accumulator, mapd, j, nj );
                                result.put( structDesc.tagnames[i], accumulator );
                            } else {
                                Map mapd= (Map)map1;
                                Map accumulator= (Map)result.get( structDesc.tagnames[i] );
                                accumulate( accumulator, mapd, j, nj );
                            }
                            iptr= iptr + struct1._lengthBytes;
                            iarray= iarray + 1;
                            istructure= istructure + 1;
                            //iptr= iptr + struct1._lengthBytes;
                        } else if ( isArray( structDesc.tagtable[i].tagflags ) ) {
                            TypeDescArray arr1= new TypeDescArray();
                            arr1.arrayDesc= structDesc.arrTable[iarray];
                            arr1.offsToArray= iptr;
                            arr1.typeCode= structDesc.tagtable[i].typecode;
                            arr1.varFlags= structDesc.tagtable[i].tagflags;
                            Object arr= arr1.readData(data);
                            if ( j==0 && arr instanceof ArrayData ) {
                                ArrayData ad= (ArrayData)arr;
                                ArrayData accumulator= new ArrayData();
                                accumulator.dims= new int[ad.dims.length+1];
                                accumulator.dims[0]= structArrayDesc.nelements;
                                System.arraycopy( ad.dims, 0, accumulator.dims, 1, ad.dims.length );
                                accumulator.array= Array.newInstance( ad.array.getClass(), structArrayDesc.nelements );
                                Array.set( accumulator.array, j, ad.array );
                                result.put( structDesc.tagnames[i], accumulator );
                            } else {
                                ArrayData ad= (ArrayData)arr;
                                ArrayData accumulator= (ArrayData) result.get( structDesc.tagnames[i] );
                                Array.set( accumulator.array, j, ad.array );
                                System.err.println(""+i+","+j+":"+ad);
                            }
                            iarray= iarray+1;
                            iptr= iptr + arr1._lengthBytes;
                        } else if ( !isStructure( structDesc.tagtable[i].tagflags ) ) {
                            TypeDescScalar scalarTypeDesc= new TypeDescScalar();
                            scalarTypeDesc.offs= iptr;
                            scalarTypeDesc.typeCode= structDesc.tagtable[i].typecode;
                            Object scalar= scalarTypeDesc.readData(data);                            
                            if ( j==0 ) {
                                if ( scalar.getClass().isArray() ) throw new IllegalArgumentException("scalar should not be an array");
                                ArrayData accumulator= new ArrayData();
                                accumulator.dims= new int[] {  structArrayDesc.nelements };
                                Class t= getPrimativeClass( scalar.getClass() );
                                accumulator.array= Array.newInstance( t, structArrayDesc.nelements );
                                Array.set( accumulator.array, j, scalar );
                                result.put( structDesc.tagnames[i], accumulator );
                                System.err.println(""+i+","+j+":"+scalar);
                            } else {
                                ArrayData accumulator= (ArrayData) result.get( structDesc.tagnames[i] );
                                Array.set( accumulator.array, j, scalar );
                                System.err.println(""+i+","+j+":"+scalar);
                            }
                            iptr= iptr + sizeOf( scalarTypeDesc.typeCode );
                        }
                    }
                }
                this._lengthBytes= iptr-iptr0;
            } else {
                result= new LinkedHashMap<>();
                int iptr= offsetToData + ( isSubstructure ? 0 : 4 );
                int iptr0= iptr;
                int iarray= 0;
                int istructure= 0;
                for ( int i=0; i<structDesc.tagnames.length; i++ ) {
                    if ( isStructure( structDesc.tagtable[i].tagflags ) ) {
                        TypeDescStructure struct1= new TypeDescStructure();
                        StructDesc structDesc1= structDesc.structTable[istructure];
                        struct1.structDesc= structDesc1;
                        struct1.structArrayDesc= structDesc.arrTable[iarray];
                        struct1.offsetToData= iptr;
                        struct1.isSubstructure= true;
                        Object map= struct1.readData(data);
                        result.put( structDesc.tagnames[i], map );
                        iptr= iptr + struct1._lengthBytes;
                        iarray= iarray + 1;
                        istructure= istructure + 1;
                        //iptr= iptr + struct1._lengthBytes;
                    } else if ( isArray( structDesc.tagtable[i].tagflags ) ) {
                        TypeDescArray arr1= new TypeDescArray();
                        arr1.arrayDesc= structDesc.arrTable[iarray];
                        arr1.offsToArray= iptr;
                        arr1.typeCode= structDesc.tagtable[i].typecode;
                        arr1.varFlags= structDesc.tagtable[i].tagflags;
                        Object arr= arr1.readData(data);
                        result.put( structDesc.tagnames[i], arr );
                        iarray= iarray+1;
                        iptr= iptr + arr1._lengthBytes;
                    } else { 
                        TypeDescScalar scalarTypeDesc= new TypeDescScalar();
                        scalarTypeDesc.offs= iptr;
                        scalarTypeDesc.typeCode= structDesc.tagtable[i].typecode;
                        Object scalar= scalarTypeDesc.readData(data);     
                        result.put( structDesc.tagnames[i], scalar );
                        iptr= iptr + sizeOf( scalarTypeDesc.typeCode );
                    }
                }
                this._lengthBytes= iptr-iptr0;
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
        result.structArrayDesc= readArrayDesc( slice( rec, 8, rec.limit() ) );
        result.structDesc= readStructDesc( slice( rec, 10*4+result.structArrayDesc.nmax*4, rec.limit() ) );
        result.offsetToData= 10*4+result.structArrayDesc.nmax*4 + result.structDesc._lengthBytes;
        result.isSubstructure= false;
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
        logger.log(Level.FINE, "variable name is {0}", varName );

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
                    
                    names.add(varName.string); 

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
        String name0= name; // keep name for reference.
        ByteBuffer rec= readRecord( in, pos );
        while ( rec!=null ) {
            int type= rec.getInt(0);
            int nextPos= rec.getInt(4);
            logger.log(Level.CONFIG, "RecType: {0} Length: {1}", new Object[]{labelType(type), nextPos-pos});
            switch ( type ) {
                case RECTYPE_VARIABLE:
                    logger.config("variable");
                    StringData varName= readString( rec, 20 );
                    String rest= null;
                    int i= name.indexOf(".");
                    if ( i>-1 ) {
                        rest= name.substring(i+1);
                        name= name.substring(0,i);
                    }
                    if ( i==-1 ) {
                        if ( varName.string.equals(name) ) {
                            Map<String,Object> result= new HashMap<>();
                            variable(rec, result);
                            return result.get(name);
                        }
                    } else {
                        if ( varName.string.equals(name) ) {
                            Map<String,Object> result= new HashMap<>();
                            variable(rec,result);
                            Map<String,Object> res= (Map<String,Object>) result.get(name);
                            assert rest!=null;
                            i= rest.indexOf('.');
                            while ( i>-1 ) {
                                i= rest.indexOf('.');
                                res= (Map<String,Object>)res.get( rest.substring(0,i) );
                                rest= rest.substring(i+1);                                
                            }
                            return res.get(rest);
                        }
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
    
    private static void arrayToString( Object o, StringBuilder b ) {
        char delim=',';
        for ( int j=0; j<4; j++ ) {
            Object i= Array.get(o,j);
            if ( i.getClass().isArray() ) {
                delim=';';
                if ( j>0 ) b.append(delim);
                arrayToString( i, b );
            } else {
                if ( j>0 ) b.append(delim);
                b.append(i.toString());
            }
        }
        if ( Array.getLength(o)>4 ) {
            b.append(delim);
            b.append("...");
        }
    }
    
    public static void main( String[] args ) throws IOException {
        Logger logger= Logger.getLogger("autoplot.idlsav");
        //logger.setLevel( Level.FINE );
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
        //RandomAccessFile aFile = new RandomAccessFile(
        //                  "/home/jbf/public_html/autoplot/data/sav/structureOfLonarr.idlsav","r");
        //RandomAccessFile aFile = new RandomAccessFile(
        //                    "/home/jbf/public_html/autoplot/data/sav/arrayOfStruct.idlsav","r");
        //RandomAccessFile aFile = new RandomAccessFile(
        //                    "/home/jbf/public_html/autoplot/data/sav/arrayOfStruct1Var.idlsav","r");
        //RandomAccessFile aFile = new RandomAccessFile(
        //                    "/home/jbf/public_html/autoplot/data/sav/structure.idlsav","r");
        //RandomAccessFile aFile = new RandomAccessFile(
        //                    "/home/jbf/public_html/autoplot/data/sav/structureWithinStructure.idlsav","r");
        //RandomAccessFile aFile = new RandomAccessFile(
        //                    "/home/jbf/public_html/autoplot/data/sav/stuctOfStruct.idlsav","r");
        RandomAccessFile aFile = new RandomAccessFile(
                            "/home/jbf/public_html/autoplot/data/sav/stuctOfStructOfStruct.idlsav","r");
        
        FileChannel inChannel = aFile.getChannel();
        long fileSize = inChannel.size();
        
        ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
        int bytesRead= 0;
        while ( bytesRead<fileSize ) {
            bytesRead+= inChannel.read(buffer);
        }
       
        Map<String,Object> vars= new ReadIDLSav().readVars(buffer);
        
        for ( Entry<String,Object> v : vars.entrySet() ) {
            System.err.println( v );
            if ( v.getValue() instanceof Map ) {
                Map<String,Object> m= (Map<String,Object>)v.getValue();
                for ( Entry<String,Object> j : m.entrySet() ) {
                    Object k= j.getValue();
                    if ( k instanceof ArrayData ) {
                        System.err.print(j.getKey()+":");
                        StringBuilder b= new StringBuilder();
                        arrayToString( ((ArrayData)k).array, b);
                        System.err.println(b.toString());
                    } else if ( k==null ) {
                        System.err.println("<<null>>");
                    } else {
                        System.err.println(k.toString());
                    }
                }
            } else {
                System.err.println(v.getValue());
            }
        }
        
    }

}
