
package org.autoplot.idlsupport;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Read data from IDL Save Files.  This was written using
 * http://www.physics.wisc.edu/~craigm/idl/savefmt/node20.html
 * https://cow.physics.wisc.edu/~craigm/idl/savefmt.pdf
 * and https://github.com/scipy/scipy/blob/master/scipy/io/idl.py
 * for reference, and with no involvement from individuals at
 * Harris Geospacial.  No warranties are implied and this must
 * be used at your own risk.
 * 
 * <pre>{@code
 * from  org.autoplot.idlsupport import ReadIDLSav
 * reader= ReadIDLSav()
 * aFile= File('/tmp/aDataFile.sav')
 * inChannel = aFile.getChannel
 * fileSize = inChannel.size()
 * buffer = ByteBuffer.allocate( inChannel.size() )
 * bytesRead= 0;
 * while ( bytesRead<fileSize ) :
       bytesRead+= inChannel.read(buffer)
 * v= reader.readVar( buffer, 'avar' )
 * }</pre>
 * @author jbf
 */
public class ReadIDLSav {
        
    private static final Logger logger= Logger.getLogger("apdss.idlsav");
            
    private static final int RECTYPE_VARIABLE = 2;
    private static final int RECTYPE_ENDMARKER = 6;
    private static final int RECTYPE_TIMESTAMP = 10;
    private static final int RECTYPE_VERSION = 14;
    private static final int RECTYPE_PROMOTE64 = 17;
    
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
        
        int type= ch.getInt(pos);
        int endpos= ch.getInt(pos+4);
        
        String stype;
        if ( type==RECTYPE_ENDMARKER ) {
            return null;
        } else {
            switch ( type ) {
                case RECTYPE_VARIABLE:
                    stype= "variable";
                    StringData varName= readString( ch, pos+20 );
                    return slice(ch, pos, endpos, stype, varName.string );
                case RECTYPE_VERSION:
                    stype= "version";
                    break;
                case RECTYPE_TIMESTAMP:
                    stype="timestamp";
                    break;
                case RECTYPE_PROMOTE64:
                    stype="promote64";
                    break;
                default:
                    stype="???";
                    break;
            }
            return slice(ch, pos, endpos, stype, "" );
        }
    }
    
    /**
     * return the next record buffer, or returns null at the end.
     * @param inch the file channel
     * @param pos the position.
     * @return the record, including the twelve bytes at the beginning
     * @throws IOException 
     */    
    private ByteBuffer readRecord( FileChannel inch, int pos ) throws IOException {

        ByteBuffer b8= ByteBuffer.allocate(8);
        inch.read(b8,pos);
        b8.order( ByteOrder.BIG_ENDIAN );
        
        int type= b8.getInt(0);
        int endpos= b8.getInt(4);
                
        String stype;
        if ( type==RECTYPE_ENDMARKER ) {
            return null;
        } else {
            ByteBuffer ch1= ByteBuffer.allocateDirect(endpos-pos);
            inch.read(ch1,pos);
        
            switch ( type ) {
                case RECTYPE_VARIABLE:
                    stype= "variable";
                    StringData varName= readString( ch1, 20 );
                    return sliceLabel( ch1, pos, endpos, stype, varName.string );
                case RECTYPE_VERSION:
                    stype= "version";
                    break;
                case RECTYPE_TIMESTAMP:
                    stype="timestamp";
                    break;
                case RECTYPE_PROMOTE64:
                    stype="promote64";
                    break;
                default:
                    stype="???";
                    break;
            }
            return ch1;
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
    
    public static final int TYPECODE_COMPLEX_FLOAT_SCALAR=0;
    public static final int TYPECODE_BYTE=1;
    public static final int TYPECODE_INT16=2;
    public static final int TYPECODE_INT32=3;
    public static final int TYPECODE_FLOAT=4;
    public static final int TYPECODE_DOUBLE=5;
    public static final int TYPECODE_COMPLEX_FLOAT=6;
    public static final int TYPECODE_STRING=7;
    public static final int TYPECODE_STRUCT=8;
    public static final int TYPECODE_COMPLEX_DOUBLE=9;
    public static final int TYPECODE_INT64=14;
    public static final int TYPECODE_UINT64=15;

    /**
     * return a string representing the type code, if supported.
     * @param typeCode for example 4 which means float or 7 which means string.
     * @return "float" or "string" or whatever the code is, or the numeric code if not supported.
     */
    public static String decodeTypeCode( int typeCode ) {
        switch ( typeCode ) {
            case TYPECODE_BYTE: {
                return "byte";
            }
            case TYPECODE_INT16: {
                return "short";
            }
            case TYPECODE_INT32: {
                return "int";
            }
            case TYPECODE_INT64: {
                return "long";
            }
            case TYPECODE_FLOAT: {
                return "float";
            }   
            case TYPECODE_DOUBLE: {
                return "double";
            }
            case TYPECODE_COMPLEX_DOUBLE: {
                return "complex_double";
            }            
            case TYPECODE_COMPLEX_FLOAT: {
                return "complex_float";
            }            
            case TYPECODE_STRUCT: {
                return "struct";
            }            
            case TYPECODE_STRING: {
                return "string";
            }
            default:
                return String.valueOf(typeCode);
        }
    }
    
    /**
     * return the size of the IDL data type in bytes.  Note shorts are stored
     * in 4-bytes.
     * @param typeCode
     * @return 
     */
    private static int sizeOf( int typeCode ) {
        int[] sizes= new int[] { 0, 4, 4, 4, 4,   8, 16, 1, 0, 32,   0, 0, 0, 0, 8, 8 };
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
            logger.log(Level.CONFIG, "RecType: {0} Length: {1,number,#}", new Object[]{labelType(type), nextPos-pos});
            switch ( type ) {
                case RECTYPE_VARIABLE:
                    logger.config("variable");
                    StringData varName= readString( rec, 20 );
                    if ( name.startsWith(varName.string) ) {
                        int nextField= 20 + varName._lengthBytes;
                        ByteBuffer var= slice(rec, nextField, rec.limit(), "variablestruct", name );
                        TypeDesc td= readTypeDesc(var, nextField);
                        return td; // struct
                    } else if ( varName.string.equals(name) ) {
                        int nextField= 20 + varName._lengthBytes;
                        ByteBuffer var= slice(rec, nextField, rec.limit(), "variable", name );
                        TypeDesc td= readTypeDesc(var, nextField);
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

    private TagDesc findStructureTag(StructDesc structDesc, String s) {
        String[] ss= s.split("\\.",2);
        int istruct= 0;
        int iarray= 0;
        if ( ss.length==1 ) {
            int itagfind=-1;
            for ( int itag=0; itag<structDesc.ntags; itag++ ) {
                if ( ( structDesc.tagtable[itag].tagflags & VARFLAG_STRUCT )==VARFLAG_STRUCT ) {
                    if ( structDesc.tagnames[itag].equals(s) ) {
                        itagfind=itag;
                        break;
                    }
                    istruct++;
                }
                if ( ( structDesc.tagtable[itag].tagflags & VARFLAG_ARRAY )==VARFLAG_ARRAY ) {
                    if ( structDesc.tagnames[itag].equals(s) ) {
                        itagfind=itag;
                        break;
                    }
                    iarray++;
                }
                if ( structDesc.tagnames[itag].equals(s) ) {
                    itagfind=itag;
                    break;
                }
            }
            if ( itagfind==-1 ) {
                throw new IllegalArgumentException("tag not found");
            }
            if ( ( structDesc.tagtable[itagfind].tagflags & VARFLAG_STRUCT )==VARFLAG_STRUCT ) {
                return structDesc.structTable[istruct];
            } else if ( ( structDesc.tagtable[itagfind].tagflags & VARFLAG_ARRAY )==VARFLAG_ARRAY ) {
                return structDesc.arrTable[iarray];
            } else {
                return structDesc.tagtable[itagfind];
            }
        } else {
            TagDesc td= findStructureTag(structDesc, ss[0]);
            if ( td instanceof StructDesc ) {
                return findStructureTag( (StructDesc)td, ss[1] );
            } else {
                throw new IllegalArgumentException("no such location, expected structure at: "+ss[0]);
            }
        }
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
                case TYPECODE_COMPLEX_FLOAT:
                    return new float[] { buf.getFloat(offs), buf.getFloat(offs+4) };
                case TYPECODE_COMPLEX_DOUBLE:
                    return new double[] { buf.getDouble(offs), buf.getDouble(offs+8) };
                case TYPECODE_STRING:
                    int len= buf.getInt(offs);
                    if ( len<0 || len>1024 ) {
                        throw new IllegalArgumentException("unbelievable len, something has gone wrong.");
                    }
                    byte[] bb= new byte[len];
                    for ( int i=0; i<len; i++ ) {
                        bb[i]= buf.get(offs+8+i);
                    }
                    return new String( bb );
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
        /**
         * offset to the data within the IDLSAV file.
         */
        int _fileOffset;
        
        int typeCode;
        
        public ArrayData() {
            logger.fine("new ArrayData");
        }
        
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
    
    public static class ScalarDesc extends TagDesc {
        public String toString() {
            return "ScalarDesc nbytes: " + this._lengthBytes + " typeCode: " + this.typecode;
        }
    }
    
    public static class ArrayDesc extends TagDesc {
        int nbytesEl;
        int nbytes;
        int nelements;
        int ndims;
        int nmax;
        int[] dims;
        @Override
        public String toString() {
            return "ArrayDesc nbytes:"+nbytes+" nelements:" +nelements+ " ndims:" +ndims + " nmax:"+nmax + " nbytesEl:"+ nbytesEl;
        }
    }

    /**
     * represents a tag within a structure
     */
    public static class TagDesc {
        /**
         * offset into the structure of the thing described.  When the thing is a structure, the descriptor is the target.
         */
        int offset; 
        
        /**
         * offset into the file
         */
        int fileOffset;
        
        /**
         * the type of thing pointed to.
         */
        int typecode;
        int tagflags;
        /**
         * for convenience, keep track of the total length of the descriptor within the IDLSAV file.
         */
        int _lengthBytes;
        @Override
        public String toString() {
            return "tagdesc  offset: "+offset+ "  tagflags: " +tagflags + "  typecode: " + typecode;
        }
    }
    
    private TagDesc readTagDesc( ByteBuffer rec ) {
        TagDesc result= new TagDesc();
        result.offset= rec.getInt(0);
        result.fileOffset= bufferOffsets.get(getKeyFor(rec)) + result.offset;
        result.typecode= rec.getInt(4);
        result.tagflags= rec.getInt(8);
        return result;
    }    
    
    public static class StructDesc extends TagDesc {
        int predef;
        int ntags;
        int nbytes;
        TagDesc[] tagtable;
        String[] tagnames;
        ArrayDesc[] arrTable;
        StructDesc[] structTable;
        //String className;
        //int nsupClasses;
        //String[] supClassNames;
        //StructDesc[] supClassTable;
        
        @Override
        public String toString() {
            return "predef: "+ predef + " ntags:"+ntags+ " nbytes:"+nbytes;
        }
    }
    
    private static class TypeDescArray extends TypeDesc {
        ArrayDesc arrayDesc;
        int offsToArray= 76;
        int _lengthBytes; // length of the array.
        
        private ArrayData makeArrayData( Object array, int fileOffset, int lengthBytes ) {
            ArrayData result= new ArrayData();
            result.array= array;
            result.dims= arrayDesc.dims;
            result._fileOffset= fileOffset;
            result._lengthBytes= lengthBytes;
            result.typeCode= typeCode;
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
            
            int offsetToFile= bufferOffsets.get(getKeyFor(buf));
            logger.log(Level.CONFIG, "readData @ {0,number,#}", offsetToFile+ offsToArray );
            
            switch (typeCode) {
                case TYPECODE_BYTE: { // unsigned byte
                    short[] result= new short[arrayDesc.nelements];
                    for ( int i=0; i<result.length; i++ ) {
                        byte ubyte= buf.get(offsToArray+i+4); // I have no idea where the "4" is coming from...
                        if ( ubyte<0 ) {
                            result[i]= (short)(ubyte+256);
                        } else {
                            result[i]= (short)ubyte;
                        }
                    }
                    return makeArrayData(result, offsetToFile+ offsToArray, result.length*4 );
                }
                case TYPECODE_INT16: {
                    short[] result= new short[arrayDesc.nelements];
                    for ( int i=0; i<result.length; i++ ) {
                        result[i]= (short)buf.getInt(offsToArray+4*i);
                    }
                    return makeArrayData(result, offsetToFile+ offsToArray, result.length*4 );
                }
                case TYPECODE_INT32: {
                    
                    int[] result= new int[arrayDesc.nelements];
                    for ( int i=0; i<result.length; i++ ) {
                        result[i]= buf.getInt(offsToArray+4*i);
                    }
                    return makeArrayData(result, offsetToFile+ offsToArray, result.length*4 );
                }
                case TYPECODE_INT64: {
                    //TODO: test me
                    long[] result= new long[arrayDesc.nelements];
                    for ( int i=0; i<result.length; i++ ) {
                        result[i]= buf.getLong(offsToArray+8*i);
                    }
                    return makeArrayData(result, offsetToFile+ offsToArray, result.length*8 );
                }
                case TYPECODE_UINT64: {
                    logger.warning("unsigned longs handled with signed longs");
                    long[] result= new long[arrayDesc.nelements];
                    for ( int i=0; i<result.length; i++ ) {
                        result[i]= buf.getLong(offsToArray+8*i);
                    }
                    return makeArrayData(result, offsetToFile+ offsToArray, result.length*8 );
                }
                case TYPECODE_FLOAT: {
                    float[] result= new float[arrayDesc.nelements];
                    for ( int i=0; i<result.length; i++ ) {
                        result[i]= buf.getFloat(offsToArray+4*i);
                    }
                    return makeArrayData(result, offsetToFile+ offsToArray, result.length*4 );
                }
                case TYPECODE_COMPLEX_FLOAT: {
                    float[] result= new float[arrayDesc.nelements*2];
                    for ( int i=0; i<arrayDesc.nelements; i++ ) {
                        result[i*2]= buf.getFloat(offsToArray+8*i);
                        result[i*2+1]= buf.getFloat(offsToArray+8*i+4);
                    }
                    return makeArrayData(result, offsetToFile+ offsToArray, result.length*8 );
                }
                case TYPECODE_DOUBLE: {
                    double[] result= new double[arrayDesc.nelements];
                    for ( int i=0; i<result.length; i++ ) {
                        result[i]= buf.getDouble(offsToArray+8*i);
                    }
                    return makeArrayData(result, offsetToFile+ offsToArray, result.length*8 );
                }
                case TYPECODE_COMPLEX_DOUBLE: {
                    double[] result= new double[arrayDesc.nelements*2];
                    for ( int i=0; i<arrayDesc.nelements; i++ ) {
                        result[i*2]= buf.getDouble(offsToArray+16*i);
                        result[i*2+1]= buf.getDouble(offsToArray+16*i+8);
                    }
                    return makeArrayData(result, offsetToFile+ offsToArray, result.length*16 );
                }
                case TYPECODE_STRING: {
                    String[] result= new String[arrayDesc.nelements];
                    int offs= offsToArray;
                    //for ( int i=0; i<buf.limit(); i++ ) {
                    //    System.err.println( String.format( "%4d: %3d %c",i,buf.get(i), (char)buf.get(i) ) );
                    //}
                    //System.err.println("");
                    for ( int i=0; i<result.length; i++ ) {
                        int len= buf.getInt(offs);
                        buf.getInt(offs-4);
                        if ( len<0 || len>1024 ) {
                            logger.info("recovery kludge!");
                            offs= offs-4;
                            len = buf.getInt(offs);
                            if ( len<0 || len>1024 ) {
                                throw new IllegalArgumentException("string has unbelievable len, something has gone wrong.");
                            }
                        }
                        byte[] bb= new byte[len];
                        for ( int k=0; k<len; k++ ) {
                            bb[k]= buf.get(offs+8+k);
                        }
                        result[i]= new String( bb );
                        if ( result[i].length()==0 ) {
                            offs= offs+4;
                        } else {
                            offs= offs+sizeOfString(result[i])+8;
                        }
                    }
                    ArrayData adresult= makeArrayData(result, offsetToFile+ offsToArray, offs-offsToArray );
                    return adresult;
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
            return "" + decodeTypeCode(this.typeCode) + b.toString();
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
        } else if ( t==String.class ) {
            return String.class;
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
                    ac.typeCode= ad.typeCode;
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
                        String tag= structDesc.tagnames[i];
                        if ( isStructure( structDesc.tagtable[i].tagflags ) ) {
                            TypeDescStructure struct1= new TypeDescStructure();
                            StructDesc structDesc1= structDesc.structTable[istructure];
                            struct1.structDesc= structDesc1;
                            struct1.structArrayDesc= structDesc.arrTable[iarray];
                            struct1.offsetToData= iptr;
                            struct1.isSubstructure= true;
                            logger.log(Level.CONFIG, "readstruct {0} {1,number,#} {2,number,#} {3}", new Object[]{data.position(), 0, data.limit(), tag});
                            Object map1= struct1.readData(data);                        
                            if ( j==0 ) {
                                Map mapd= (Map)map1;
                                Map accumulator= new LinkedHashMap();
                                accumulate( accumulator, mapd, j, nj );
                                result.put( tag, accumulator );
                            } else {
                                Map mapd= (Map)map1;
                                Map accumulator= (Map)result.get( tag );
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
                            logger.log(Level.CONFIG, "readarray {0} {1,number,#} {2,number,#} {3}", new Object[]{data.position(), 0, data.limit(), tag});
                            Object arr= arr1.readData(data);
                            if ( j==0 && arr instanceof ArrayData ) {
                                ArrayData ad= (ArrayData)arr;
                                ArrayData accumulator= new ArrayData();
                                accumulator.dims= new int[ad.dims.length+1];
                                accumulator.dims[0]= structArrayDesc.nelements;
                                System.arraycopy( ad.dims, 0, accumulator.dims, 1, ad.dims.length );
                                accumulator.array= Array.newInstance( ad.array.getClass(), structArrayDesc.nelements );
                                Array.set( accumulator.array, j, ad.array );
                                result.put( tag, accumulator );
                            } else {
                                ArrayData ad= (ArrayData)arr;
                                ArrayData accumulator= (ArrayData) result.get( tag );
                                Array.set( accumulator.array, j, ad.array );
                            }
                            iarray= iarray+1;
                            iptr= iptr + arr1._lengthBytes;
                        } else if ( !isStructure( structDesc.tagtable[i].tagflags ) ) {
                            TypeDescScalar scalarTypeDesc= new TypeDescScalar();
                            scalarTypeDesc.offs= iptr;
                            scalarTypeDesc.typeCode= structDesc.tagtable[i].typecode;
                            logger.log(Level.CONFIG, "readscalar {0} {1,number,#} {2,number,#} {3}", new Object[]{data.position(), 0, data.limit(), tag});
                            Object scalar= scalarTypeDesc.readData(data);                            
                            if ( j==0 ) {
                                if ( scalar.getClass().isArray() ) throw new IllegalArgumentException("scalar should not be an array");
                                ArrayData accumulator= new ArrayData();
                                accumulator.dims= new int[] {  structArrayDesc.nelements };
                                Class t= getPrimativeClass( scalar.getClass() );
                                accumulator.array= Array.newInstance( t, structArrayDesc.nelements );
                                Array.set( accumulator.array, j, scalar );
                                result.put( tag, accumulator );
                            } else {
                                ArrayData accumulator= (ArrayData) result.get( tag );
                                Array.set( accumulator.array, j, scalar );
                            }
                            if ( scalar instanceof String ) {
                                String string= (String)scalar;
                                if ( string.length()==0 ) {
                                    iptr = iptr + 4;
                                } else {
                                    iptr = iptr + 8 + sizeOfString( string );
                                }
                            } else {
                                iptr= iptr + sizeOf( scalarTypeDesc.typeCode );
                            }
                            
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
                    String tag= structDesc.tagnames[i];
                    logger.log(Level.FINE, "reading tag {0}", tag);
                    if ( isStructure( structDesc.tagtable[i].tagflags ) ) {
                        TypeDescStructure struct1= new TypeDescStructure();
                        StructDesc structDesc1= structDesc.structTable[istructure];
                        struct1.structDesc= structDesc1;
                        struct1.structArrayDesc= structDesc.arrTable[iarray];
                        struct1.offsetToData= iptr;
                        struct1.isSubstructure= true;
                        logger.log(Level.CONFIG, "readstruct_1 {0} {1,number,#} {2,number,#} {3}", new Object[]{iptr, 0, data.limit(), tag});
                        Object map= struct1.readData(data);
                        result.put( tag, map );
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
                        logger.log(Level.CONFIG, "readarray_1 {0} {1,number,#} {2,number,#} {3}", new Object[]{iptr, 0, data.limit(), tag});
                        Object arr= arr1.readData(data);
                        int strLenBytes= ((ArrayData)arr)._lengthBytes;
                        result.put( tag, arr );
                        iarray= iarray+1;
                        iptr= iptr + strLenBytes;
                    } else { 
                        TypeDescScalar scalarTypeDesc= new TypeDescScalar();
                        scalarTypeDesc.offs= iptr;
                        scalarTypeDesc.typeCode= structDesc.tagtable[i].typecode;
                        logger.log(Level.CONFIG, "readscalar_1 {0} {1,number,#} {2,number,#} {3}", new Object[]{iptr, 0, data.limit(), tag});
                        Object scalar= scalarTypeDesc.readData(data);     
                        result.put( tag, scalar );
                        if ( scalarTypeDesc.typeCode==7 ) {
                            String string= (String)scalar;
                            if ( string.length()==0 ) {
                                iptr = iptr + 4;
                            } else {
                                iptr = iptr + 8 + sizeOfString( string );
                            }
                        } else {
                            iptr= iptr + sizeOf( scalarTypeDesc.typeCode );
                        }
                    }
                }
                this._lengthBytes= iptr-iptr0;
            }
            return result;
        }
        
    }
    
    private static int sizeOfString( String string ) {
        int n= string.length();
        if ( n==0 ) {
            return 0;
        }
        switch ( n%4 ) {
            case 0: return n;
            case 1: return n+3;
            case 2: return n+2;
            case 3: return n+1;
            default: throw new IllegalArgumentException("implementation error");
        }
    }
    
    private static boolean isArray( int varFlags ) {
        return ( varFlags & 0x04 ) == 0x04;
    }
    
    private static boolean isStructure( int varFlags ) {
        return ( varFlags & 0x20 ) == 0x20;
    }
    
    /**
     * a TypeDesc is a description of a thing that is in the IDLSav file.  Its readData
     * method will return something of the type.
     */
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
    
    private TypeDescScalar readTypeDescScalar( ByteBuffer rec, long fileOffset) {
        logger.log(Level.FINER, "readTypeDescScalar @ {0}", bufferOffsets.get(getKeyFor(rec)));
        TypeDescScalar result= new TypeDescScalar();
        result.typeCode= rec.getInt(0);
        result.varFlags= rec.getInt(4);
        return result;
    }
    
    private ArrayDesc readArrayDesc( ByteBuffer rec, long fileOffset) {
        logger.log(Level.FINER, "readArrayDesc @ {0}", bufferOffsets.get(getKeyFor(rec)));
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
    
    public StructDesc readStructDesc( ByteBuffer rec, long fileOffset) {
        logger.log(Level.FINER, "readStructDesc @ {0}", bufferOffsets.get(getKeyFor(rec)));
        StructDesc result= new StructDesc();
        if ( rec.getInt(0)!=9 ) {
            throw new IllegalArgumentException("expected 9 for STRUCTSTART");
        }
        StringData name= readString( rec, 4 );
        int nextField= name._lengthBytes + 4;
        
        final int PREDEF_PREDEF= 0x01;
        final int PREDEF_INHERITS= 0x02;
        final int PREDEF_IS_SUPER= 0x04;
        
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
        
        Map<Integer,Integer> arrayMap= new HashMap<>();
        Map<Integer,Integer> structMap= new HashMap<>();
        
        int narray= 0;
        int nstruct= 0;
        for ( int i=0; i<result.ntags; i++ ) {
            result.tagtable[i]= readTagDesc(slice(rec, ipos, ipos+12, "tagDesc", name.string ) );
            if ( ( result.tagtable[i].tagflags & VARFLAG_ARRAY ) == VARFLAG_ARRAY ) {
                arrayMap.put(narray,i);
                narray++;
            }
            if ( ( result.tagtable[i].tagflags & VARFLAG_STRUCT ) == VARFLAG_STRUCT ) {
                structMap.put(nstruct,i);
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
            ByteBuffer slice1= slice(rec, ipos, rec.limit(), "arrayDesc", result.tagnames[arrayMap.get(i)] );
            result.arrTable[i]= readArrayDesc(slice1, ipos+fileOffset );
            ipos+= result.arrTable[i]._lengthBytes;           
        }
        
        result.structTable= new StructDesc[nstruct];
        for ( int i=0; i<nstruct; i++ ) {
            ByteBuffer slice1= slice(rec, ipos, rec.limit(), "structDesc", result.tagnames[structMap.get(i)] );
            result.structTable[i]= readStructDesc( slice1, ipos+fileOffset );
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
    
    /**
     * read the type description starting at the fileOffset.
     * @param rec ByteBuffer at the fileOffset.
     * @param fileOffset the fileOffset.
     * @return 
     */
    private TypeDescStructure readTypeDescStructure( ByteBuffer rec, long fileOffset) {
        logger.log(Level.FINER, "readTypeDescStructure @ {0}", bufferOffsets.get(getKeyFor(rec)));
        TypeDescStructure result= new TypeDescStructure();
        result.typeCode= rec.getInt(0);
        result.varFlags= rec.getInt(4);
        result.structArrayDesc= readArrayDesc(slice(rec, 8, rec.limit(), "arrayDesc", "" ), fileOffset+8 );
        long fileOffsetSub= fileOffset + 10*4+result.structArrayDesc.nmax*4;
        result.structDesc= readStructDesc(slice(rec, 10*4+result.structArrayDesc.nmax*4, rec.limit(), "structDesc", "" ), fileOffsetSub );
        result.offsetToData= 10*4+result.structArrayDesc.nmax*4 + result.structDesc._lengthBytes;
        result.isSubstructure= false;
        return result;
    }
    
    private TypeDescArray readTypeDescArray( ByteBuffer rec, long fileOffset) {
        logger.log(Level.FINER, "readTypeDescStructure @ {0}", bufferOffsets.get(getKeyFor(rec)));
        TypeDescArray result= new TypeDescArray();
        result.typeCode= rec.getInt(0);
        result.varFlags= rec.getInt(4);
        result.arrayDesc= readArrayDesc(slice(rec, 8, rec.limit(), "arrayDesc", "" ), fileOffset + 8 );
        return result;
    }
    
    /**
     * return the TypeDesc, which is after the name.
     * @param typeDescBuf
     * @return 
     */
    private TypeDesc readTypeDesc( ByteBuffer typeDescBuf, long fileOffset) {
        logger.log(Level.FINER, "readTypeDesc @ {0}", bufferOffsets.get(getKeyFor(typeDescBuf)));
        int typeCode= typeDescBuf.getInt(0);
        int varFlags= typeDescBuf.getInt(4);
        if ( typeCode<0 || typeCode>15 ) {
            throw new IllegalArgumentException("expected 0-14 for type code in readTypeDesc");
        }
        if ( ( varFlags & VARFLAG_STRUCT ) == VARFLAG_STRUCT ) {
            return readTypeDescStructure(typeDescBuf, fileOffset);
        } else if ( ( varFlags & VARFLAG_ARRAY ) == VARFLAG_ARRAY ) {
            return readTypeDescArray(typeDescBuf, fileOffset);
        } else {
            return readTypeDescScalar(typeDescBuf, fileOffset);
        }
    }
    
    /**
     * read the scalar, array, or structure at this position.  An
     * array is returned flattened, and readTypeDesc should be used
     * to unflatten it.  Structures are returned as a LinkedHashMap.
     * @param rec the byte buffer
     * @param offset offset into rec
     * @param vars map containing read data.
     * @return the read data.
     */
    private Object variable( ByteBuffer rec, int offset, Map<String,Object> vars) {
        logger.log( Level.FINER, "variable @ {0}", bufferOffsets.get(getKeyFor(rec)) );
        int type= rec.getInt(0+offset);
        if ( type!=RECTYPE_VARIABLE ) {
            throw new IllegalArgumentException("not a variable");
        }
        //printBuffer(rec);
        StringData varName= readString( rec, 20+offset );
        logger.log(Level.FINE, "variable name is {0}", varName );

        int nextField= 20 + varName._lengthBytes + offset;

        ByteBuffer data= slice(rec, nextField, rec.limit(), "typeDesc", "" );
        TypeDesc typeDesc= readTypeDesc(data, nextField );
        
        logger.log(Level.CONFIG, "variable_972 {0} {1,number,#} {2,number,#} {3}", new Object[]{data.position(), 0, data.limit(), varName});
        Object result= typeDesc.readData( data );
        
        vars.put( varName.string, result );
        
        return result;
        
    }
    
    /**
     * read the scalar, array, or structure at this position.  An
     * array is returned flattened, and readTypeDesc should be used
     * to unflatten it.  Structures are returned as a LinkedHashMap.
     * @param rec the byte buffer
     * @param offset offset into rec
     * @param vars map containing read data.
     * @return the read data.
     */
    private Object variable( FileChannel inch, int offset, Map<String,Object> vars) throws IOException {
        
        ByteBuffer rec= ByteBuffer.allocateDirect(512);  // length of variable name
        inch.read(rec,offset);
        
        logger.log( Level.FINER, "variable @ {0}", bufferOffsets.get(getKeyFor(rec)) );
        int type= rec.getInt(0+offset);
        if ( type!=RECTYPE_VARIABLE ) {
            throw new IllegalArgumentException("not a variable");
        }
        
        StringData varName= readString( rec, 20+offset );
        logger.log(Level.FINE, "variable name is {0}", varName );

        int nextField= 20 + varName._lengthBytes + offset;

        rec=null;
        ByteBuffer data= ByteBuffer.allocateDirect(nextField-offset);
        inch.read( rec, offset );
                //inch.//slice(rec, nextField, rec.limit(), "typeDesc", "" );
        TypeDesc typeDesc= readTypeDesc(data, offset );
        
        logger.log(Level.CONFIG, "variable_972 {0} {1,number,#} {2,number,#} {3}", new Object[]{data.position(), 0, data.limit(), varName});
        Object result= typeDesc.readData( data );
        
        vars.put( varName.string, result );
        
        return result;
        
    }    
    
    /**
     * TODO: document me
     */
    private static final Map<Long,Integer> bufferOffsets= new HashMap<>();
    
    /**
     * Labels for each section of the 
     */
    private static final Map<Long,String> bufferLabels= new HashMap<>();
    
    private String nameFor( ByteBuffer buf ) {
        return bufferLabels.get(getKeyFor(buf));
    }
    
    private static Long getKeyFor( ByteBuffer buf ) {
        return ((long)buf.limit())*Integer.MAX_VALUE + buf.position();
    }
    
    private static Long getKeyFor( int position, int limit ) {
        return ((long)limit)* Integer.MAX_VALUE +position;
    }
    
    /**
     * slice out just the object 
     * @param src
     * @param position
     * @param limit
     * @param label
     * @return 
     */
    private ByteBuffer slice( ByteBuffer src, int position, int limit, String type, String label ) {
        if ( label==null ) throw new IllegalArgumentException("no label");
        Integer offset= bufferOffsets.get(getKeyFor(src));
        if ( offset!=null ) {
            logger.log(Level.CONFIG, "slice {0} {1,number,#} {2,number,#} {3}", 
                    new Object[]{ type, position+offset, limit+offset, label });
        } else {
            logger.log(Level.CONFIG, "slice {0} {1,number,#} {2,number,#} {3}", new Object[]{ type, position, limit, label });
            offset=0;
            if ( bufferLabels.get(getKeyFor(src))==null ) {
                bufferLabels.put(getKeyFor(src),"file");
            }
        }
        int position0= src.position();
        int limit0= src.limit();
        src.position(position);
        src.limit(limit);
        ByteBuffer r1= ByteBuffer.allocate(limit-position);
        r1.put(src.slice());
        r1.flip();
        src.limit(limit0);
        src.position(position0);
        
        bufferOffsets.put( getKeyFor(r1), position+offset );
        bufferLabels.put( getKeyFor(r1), label );
        return r1;
    }
    
    private ByteBuffer sliceLabel( ByteBuffer slice, int position, int limit, String type, String label ) {
        Integer offset= bufferOffsets.get(getKeyFor(position,limit));
        if ( offset!=null ) {
            logger.log(Level.CONFIG, "slice {0} {1,number,#} {2,number,#} {3}", 
                    new Object[]{ type, position+offset, limit+offset, label });
        } else {
            logger.log(Level.CONFIG, "slice {0} {1,number,#} {2,number,#} {3}", new Object[]{ type, position, limit, label });
            offset=0;
            if ( bufferLabels.get(getKeyFor(slice))==null ) {
                bufferLabels.put(getKeyFor(slice),"file");
            }
        }
        Long k= getKeyFor(position,position+slice.limit());
        bufferOffsets.put( k, position+offset );
        bufferLabels.put( k, label );
        return slice;
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
        if ( fileSize>Integer.MAX_VALUE ) {
            throw new IllegalArgumentException("file is too large to read, and must be less than 2GB: "+f);
        }
        ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);        
        int bytesRead= 0;
        while ( bytesRead<fileSize ) {
            bytesRead+= inChannel.read(buffer);
        }
        return buffer;
    }
   
    public static FileChannel readFileIntoChannel( File f ) throws IOException {
        RandomAccessFile aFile = new RandomAccessFile(f,"r");
        FileChannel inChannel = aFile.getChannel();
        return inChannel;
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
            logger.log(Level.CONFIG, "RecType: {0} Length: {1,number,#}", new Object[]{labelType(type), nextPos-pos});
            switch ( type ) {
                case RECTYPE_VARIABLE:
                    logger.config("variable");
                    variable(rec, 0, result);
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

    public Map<String,Object> readVars( FileChannel inChannel ) throws IOException {
        
        //  2  ch.write(getBytesStr("SR"));

        //  1 ch.write(getBytesByte((byte) 0));
        //  1 ch.write(getBytesByte((byte) 4));

        checkMagic(inChannel);

        int pos= 4;
        
        Map<String,Object> result= new LinkedHashMap<>();
        
        ByteBuffer rec= readRecord( inChannel, pos );
        while ( rec!=null ) {
            int type= rec.getInt(0);
            int nextPos= rec.getInt(4);
            if ( rec.getInt(8)!=0 ) {
                throw new IllegalArgumentException("records bigger than 2**32 bytes are not supported.");
            }
            logger.log(Level.CONFIG, "RecType: {0} Length: {1,number,#}", new Object[]{labelType(type), nextPos-pos});
            switch ( type ) {
                case RECTYPE_VARIABLE:
                    logger.config("variable");
                    variable(rec, 0, result);
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
            rec= readRecord( inChannel, pos );
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
            logger.log(Level.CONFIG, "RecType: {0} Length: {1,number,#}", new Object[]{labelType(type), nextPos-pos});
            switch ( type ) {
                case RECTYPE_VARIABLE:
                    logger.config("variable");
                    StringData varName= readString( rec, 20 );

                    int nextField= varName._lengthBytes;

                    ByteBuffer var= slice(rec, 20+nextField, rec.limit(), "var_x", "" );
                    
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
     * list the names in the IDLSav file.  This is only the supported
     * variable types.
     * @param in
     * @return the names found.
     * @throws IOException 
     */
    public String[] readVarNames( FileChannel inChannel ) throws IOException {
        checkMagic(inChannel);
        
        int pos= 4;
        
        List<String> names= new ArrayList<>();
        
        ByteBuffer rec= readRecord( inChannel, pos );  
        while ( rec!=null ) {
            int type= rec.getInt(0);
            int nextPos= rec.getInt(4);
            logger.log(Level.CONFIG, "RecType: {0} Length: {1,number,#}", new Object[]{labelType(type), nextPos-pos});
            switch ( type ) {
                case RECTYPE_VARIABLE:
                    logger.config("variable");
                    StringData varName= readString( rec, 20 );

                    int nextField= varName._lengthBytes;

                    ByteBuffer var= slice(rec, 20+nextField, rec.limit(), "var_x", "" );
                    
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
            rec= readRecord( inChannel, pos );
        }
        return names.toArray( new String[names.size()] );
    }    
    
    /**
     * scan through the IDLSav and return just the one variable.
     * @param in the IDLSav mapped into a NIO ByteBuffer.
     * @param name the variable name to look for.
     * @return
     * @throws IOException 
     */
    public Object readVar( ByteBuffer in, String name ) throws IOException {
        int magic= in.getInt(0);
        if ( magic!=1397882884 ) {
            logger.warning("magic number is incorrect, file should start with should be 1397882884");
        }
        if ( in.order()!=ByteOrder.BIG_ENDIAN ) {
            throw new IllegalArgumentException("buffer must be big endian");
        }
        if ( in.position()==0 ) {
            logger.log(Level.CONFIG, "readVar {0} buffer size: {1,number,#}", new Object[] { name, in.limit() } );
        }

        bufferOffsets.put( getKeyFor(in), 0 );
        bufferLabels.put( getKeyFor(in), "<file>" );

        int pos= 4;
        String name0= name; // keep name for reference.
        ByteBuffer rec= readRecord( in, pos );
        
        while ( rec!=null ) {
    
            int offset = bufferOffsets.get(getKeyFor(rec));
        
            int type= rec.getInt(0);
            int nextPos= rec.getInt(4);
            logger.log(Level.CONFIG, "RecType: {0} Length: {1,number,#}", new Object[]{labelType(type), nextPos-pos});
            switch ( type ) {
                case RECTYPE_VARIABLE:
                    StringData varName= readString( rec, 20 );
                    logger.log(Level.CONFIG, "variable {0} {1,number,#} {2,number,#} {3}", 
                            new Object[] { type, pos, nextPos, varName } );
                    String rest= null;
                    int i= name.indexOf(".");
                    if ( i>-1 ) {
                        rest= name.substring(i+1);
                        name= name.substring(0,i);
                    }
                    if ( i==-1 ) {
                        if ( varName.string.equals(name) ) {
                            Map<String,Object> result= new HashMap<>();
                            variable( in, offset, result);
                            return result.get(name);
                        }
                    } else {
                        if ( varName.string.equals(name) ) {
                            Map<String,Object> result= new HashMap<>();
                            variable( in, offset, result );
                            Map<String,Object> res= (Map<String,Object>) result.get(name);
                            assert rest!=null;
                            i= rest.indexOf('.');
                            while ( i>-1 ) {
                                res= (Map<String,Object>)res.get( rest.substring(0,i) );
                                rest= rest.substring(i+1);
                                i= rest.indexOf('.');
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
                case RECTYPE_PROMOTE64:
                    logger.config("promote64");
                    throw new IllegalArgumentException("promote64 is not supported.");
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
     * scan through the IDLSav and return just the one variable.
     * @param inch FileChannel for the IDLSav.
     * @param name the variable name to look for.
     * @return
     * @throws IOException 
     */
    public Object readVar( FileChannel inch, String name ) throws IOException {
        checkMagic(inch);

        bufferOffsets.put( getKeyFor(0,0), 0 );
        bufferLabels.put( getKeyFor(0,0), "<file>" );

        int pos= 4;
        String name0= name; // keep name for reference.
        ByteBuffer rec= readRecord( inch, pos );
        
        while ( rec!=null ) {
    
            int offset = bufferOffsets.get(getKeyFor(rec));
        
            int type= rec.getInt(0);
            int nextPos= rec.getInt(4);
            logger.log(Level.CONFIG, "RecType: {0} Length: {1,number,#}", new Object[]{labelType(type), nextPos-pos});
            switch ( type ) {
                case RECTYPE_VARIABLE:
                    StringData varName= readString( rec, 20 );
                    logger.log(Level.CONFIG, "variable {0} {1,number,#} {2,number,#} {3}", 
                            new Object[] { type, pos, nextPos, varName } );
                    String rest= null;
                    int i= name.indexOf(".");
                    if ( i>-1 ) {
                        rest= name.substring(i+1);
                        name= name.substring(0,i);
                    }
                    if ( i==-1 ) {
                        if ( varName.string.equals(name) ) {
                            Map<String,Object> result= new HashMap<>();
                            variable( inch, offset, result);
                            return result.get(name);
                        }
                    } else {
                        if ( varName.string.equals(name) ) {
                            Map<String,Object> result= new HashMap<>();
                            variable( inch, offset, result );
                            Map<String,Object> res= (Map<String,Object>) result.get(name);
                            assert rest!=null;
                            i= rest.indexOf('.');
                            while ( i>-1 ) {
                                res= (Map<String,Object>)res.get( rest.substring(0,i) );
                                rest= rest.substring(i+1);
                                i= rest.indexOf('.');
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
                case RECTYPE_PROMOTE64:
                    logger.config("promote64");
                    throw new IllegalArgumentException("promote64 is not supported.");
                default:
                    logger.config("???");
                    break;
            }
            pos= nextPos;
            rec= readRecord( inch, pos );
            
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
    public TagDesc readTagDesc( ByteBuffer in, String name ) throws IOException {
        int magic= in.getInt(0);
        if ( magic!=1397882884 ) {
            logger.warning("magic number is incorrect");
        }
        int pos= 4;
        
        ByteBuffer rec= readRecord( in, pos );
        while ( rec!=null ) {
            int type= rec.getInt(0);
            int nextPos= rec.getInt(4);
            logger.log(Level.CONFIG, "RecType: {0} Length: {1,number,#}", new Object[]{labelType(type), nextPos-pos});
            switch ( type ) {
                case RECTYPE_VARIABLE:
                    logger.config("variable");
                    StringData varName= readString( rec, 20 );
                    if ( name.startsWith(varName.string+".") || name.equals(varName.string) ) {
                        int nextField= varName._lengthBytes;
                        int fileOffset= 20+nextField;
                        ByteBuffer var= slice(rec, fileOffset, rec.limit(), "variable", varName.string );
                        if ( var.getInt(0)==8 ) { // TODO: what is 8?
                            if ( ( var.getInt(4) & VARFLAG_STRUCT ) == VARFLAG_STRUCT ) {
                                TypeDescStructure typeDescStructure= readTypeDescStructure(var, fileOffset);
                                if ( name.equals(varName.string) ) {
                                    return typeDescStructure.structDesc;
                                } else {
                                    return findStructureTag( typeDescStructure.structDesc, name.substring(varName.string.length()+1) );
                                }
                            } else {
                                return readTypeDescArray(var, fileOffset).arrayDesc;
                            }
                        } else {
                            if ( ( var.getInt(4) & VARFLAG_ARRAY ) == VARFLAG_ARRAY ) {
                                TagDesc dd= readTypeDescArray(var, fileOffset).arrayDesc;
                                dd.typecode= readTypeDescArray(var, fileOffset).typeCode;
                                return dd;
                            } else {
                                return readTagDesc(var);
                            }
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

    public static boolean checkMagic( FileChannel inChannel ) throws IOException {
        ByteBuffer buf= ByteBuffer.allocate(4);
        if ( !(inChannel.read(buf)==4) ) {
            throw new IllegalArgumentException("not 4 bytes");
        }
        int magic= buf.getInt(0);
        if ( magic!=1397882884 ) {
            logger.warning("magic number is incorrect");
            return false;
        } else {
            return true;
        }
    }
    
    /**
     * scan through the IDLSav and retrieve information about the array.
     * @param inch the FileChannel for the idlsav
     * @param name the name of the array
     * @return
     * @throws IOException 
     */    
    /**
     * scan through the IDLSav and retrieve information about the array.
     * @param inch the FileChannel for the idlsav
     * @param name the name of the array
     * @return
     * @throws IOException 
     */
    public TagDesc readTagDesc( FileChannel inch, String name ) throws IOException {
        checkMagic(inch);
        
        int pos= 4;
        
        ByteBuffer rec= readRecord( inch, pos );
        while ( rec!=null ) {
            int type= rec.getInt(0);
            int nextPos= rec.getInt(4);
            logger.log(Level.CONFIG, "RecType: {0} Length: {1,number,#}", new Object[]{labelType(type), nextPos-pos});
            switch ( type ) {
                case RECTYPE_VARIABLE:
                    logger.config("variable");
                    StringData varName= readString( rec, 20 );
                    if ( name.startsWith(varName.string+".") || name.equals(varName.string) ) {
                        int nextField= varName._lengthBytes;
                        int fileOffset= 20+nextField;
                        ByteBuffer var= slice(rec, fileOffset, rec.limit(), "variable", varName.string );
                        if ( var.getInt(0)==8 ) { // TODO: what is 8?
                            if ( ( var.getInt(4) & VARFLAG_STRUCT ) == VARFLAG_STRUCT ) {
                                TypeDescStructure typeDescStructure= readTypeDescStructure(var, 20+nextField);
                                if ( name.equals(varName.string) ) {
                                    return typeDescStructure.structDesc;
                                } else {
                                    return findStructureTag( typeDescStructure.structDesc, name.substring(varName.string.length()+1) );
                                }
                            } else {
                                return readTypeDescArray(var, fileOffset).arrayDesc;
                            }
                        } else {
                            if ( ( var.getInt(4) & VARFLAG_ARRAY ) == VARFLAG_ARRAY ) {
                                TagDesc dd= readTypeDescArray(var, fileOffset).arrayDesc;
                                dd.typecode= readTypeDescArray(var, fileOffset).typeCode;
                                return dd;
                            } else {
                                return readTagDesc(var);
                            }
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
            rec= readRecord( inch, pos );
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
    
//    public static void main( String[] args ) throws IOException {
//        Logger logger= Logger.getLogger("autoplot.idlsav");
//        //logger.setLevel( Level.FINE );
//        Handler h= new ConsoleHandler();
//        h.setLevel(Level.ALL);
//        logger.addHandler(h);
//            
////        FileOutputStream fos = new FileOutputStream(new File("/tmp/test.autoplot.idlsav"));
////        
////        WriteIDLSav widls= new WriteIDLSav();
////        //widls.addVariable( "wxyz", new double[] { 120,100,120,45,46,47,48,49,120,100,120 } );
////        widls.addVariable( "abcd", 240 );
////        //widls.addVariable( "oneval", 19.95 );
////        widls.write(fos);
////        
////        fos.close();
//
//        //RandomAccessFile aFile = new RandomAccessFile(
//        //                    "/home/jbf/public_html/autoplot/data/sav/simple.idlsav","r");
//        //RandomAccessFile aFile = new RandomAccessFile(
//        //                    "/home/jbf/public_html/autoplot/data/sav/vnames.idlsav","r");
//        //RandomAccessFile aFile = new RandomAccessFile(
//        //                    "/home/jbf/public_html/autoplot/data/sav/scalars.idlsav","r");
//        //RandomAccessFile aFile = new RandomAccessFile(
//        //                    "/home/jbf/public_html/autoplot/data/sav/arrayVsScalar.idlsav","r");
//        //RandomAccessFile aFile = new RandomAccessFile(
//        //                    "/home/jbf/public_html/autoplot/data/sav/floats.idlsav","r");
//        //RandomAccessFile aFile = new RandomAccessFile(
//        //                /home/jbf/public_html/autoplot/data/sav/structureOfLonarr.idlsav    "/home/jbf/public_html/autoplot/data/sav/doublearray.idlsav","r");
//        //RandomAccessFile aFile = new RandomAccessFile(
//        //                  "/home/jbf/public_html/autoplot/data/sav/structureOfLonarr.idlsav","r");
//        //RandomAccessFile aFile = new RandomAccessFile(
//        //                    "/home/jbf/public_html/autoplot/data/sav/arrayOfStruct.idlsav","r");
//        //RandomAccessFile aFile = new RandomAccessFile(
//        //                    "/home/jbf/public_html/autoplot/data/sav/arrayOfStruct1Var.idlsav","r");
//        //RandomAccessFile aFile = new RandomAccessFile(
//        //                    "/home/jbf/public_html/autoplot/data/sav/structure.idlsav","r");
//        //RandomAccessFile aFile = new RandomAccessFile(
//        //                    "/home/jbf/public_html/autoplot/data/sav/structureWithinStructure.idlsav","r");
//        //RandomAccessFile aFile = new RandomAccessFile(
//        //                    "/home/jbf/public_html/autoplot/data/sav/stuctOfStruct.idlsav","r");
//        //RandomAccessFile aFile = new RandomAccessFile(
//        //                    "/home/jbf/public_html/autoplot/data/sav/stuctOfStructOfStruct.idlsav","r");
//        //RandomAccessFile aFile = new RandomAccessFile(
//        //                    "/home/jbf/public_html/autoplot/data/sav/stuctOfStructOfStruct.idlsav","r");
//        RandomAccessFile aFile = new RandomAccessFile(
//                            "/home/jbf/ct/autoplot/data/sav/kristoff/test_fit.idlsav","r");
//                
//        FileChannel inChannel = aFile.getChannel();
//        long fileSize = inChannel.size();
//        
//        ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
//        int bytesRead= 0;
//        while ( bytesRead<fileSize ) {
//            bytesRead+= inChannel.read(buffer);
//        }
//       
//        Map<String,Object> vars= new ReadIDLSav().readVars(buffer);
//        
//        for ( Entry<String,Object> v : vars.entrySet() ) {
//            System.err.println( v );
//            if ( v.getValue() instanceof Map ) {
//                Map<String,Object> m= (Map<String,Object>)v.getValue();
//                for ( Entry<String,Object> j : m.entrySet() ) {
//                    Object k= j.getValue();
//                    if ( k instanceof ArrayData ) {
//                        System.err.print(j.getKey()+":");
//                        StringBuilder b= new StringBuilder();
//                        arrayToString( ((ArrayData)k).array, b);
//                        System.err.println(b.toString());
//                    } else if ( k==null ) {
//                        System.err.println("<<null>>");
//                    } else {
//                        System.err.println(k.toString());
//                    }
//                }
//            } else {
//                System.err.println(v.getValue());
//            }
//        }
//        
//    }

}
