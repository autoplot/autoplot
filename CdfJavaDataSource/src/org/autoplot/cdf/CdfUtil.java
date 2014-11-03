/*
 * CdfUtil.java
 *
 * Created on July 24, 2007, 12:56 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.autoplot.cdf;

import gov.nasa.gsfc.spdf.cdfj.CDFException;
import gov.nasa.gsfc.spdf.cdfj.CDFReader;
import org.virbo.cdf.*;
import java.util.logging.Level;
import org.das2.datum.DatumRange;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import java.lang.reflect.Array;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Logger;
import org.das2.datum.InconvertibleUnitsException;
import org.das2.datum.UnitsConverter;
import org.das2.datum.UnitsUtil;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.binarydatasource.BufferDataSet;
import org.virbo.dataset.ArrayDataSet;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.Slice0DataSet;
import org.virbo.datasource.DataSourceUtil;
import org.virbo.dsops.Ops;

/**
 * static methods supporting CdfFileDataSource
 *
 * @author jbf
 */
public class CdfUtil {

    private final static Logger logger= LoggerManager.getLogger("apdss.cdfjava");

    /**
     * return the Java type used to store the CDF data type.
     * @param type 45, 44, or 51
     * @return String like double, float or string
     */
    private static String getTargetType(int type) {
        if ( type==CDFConstants.CDF_DOUBLE || type==CDFConstants.CDF_REAL8 || type==CDFConstants.CDF_EPOCH ) {
            return "double";
        } else if ( type==CDFConstants.CDF_EPOCH16 ) {
            return "double";
        } else if ( type==CDFConstants.CDF_FLOAT || type==CDFConstants.CDF_REAL4 ) {
            return "float";
        } else if ( type==CDFConstants.CDF_UINT4 ) {
            return "double";
        } else if ( type==CDFConstants.CDF_INT8 || type==CDFConstants.CDF_TT2000 ) {
            return "long";
        } else if ( type==CDFConstants.CDF_INT4 || type==CDFConstants.CDF_UINT2 ) {
            return "int";
        } else if ( type==CDFConstants.CDF_INT2 || type==CDFConstants.CDF_UINT1 ) {
            return "short";
        } else if ( type==CDFConstants.CDF_INT1 || type==CDFConstants.CDF_BYTE ) {
            return "byte";
        } else if ( type==CDFConstants.CDF_CHAR || type==CDFConstants.CDF_UCHAR ) {
            return "string";
        } else {
            throw new IllegalArgumentException("unsupported type: "+type);
        }
    }
    
    private static Object byteBufferType( int type ) {
        if ( type==CDFConstants.CDF_DOUBLE || type==CDFConstants.CDF_REAL8 || type==CDFConstants.CDF_EPOCH ) {
            return BufferDataSet.DOUBLE;
        } else if ( type==CDFConstants.CDF_FLOAT || type==CDFConstants.CDF_REAL4 ) {
            return BufferDataSet.FLOAT;
        } else if ( type==CDFConstants.CDF_UINT4) {
            return BufferDataSet.DOUBLE;
        } else if ( type==CDFConstants.CDF_INT8 || type==CDFConstants.CDF_TT2000 ) {
            return BufferDataSet.LONG;
        } else if ( type==CDFConstants.CDF_INT4 || type==CDFConstants.CDF_UINT2 ) {
            return BufferDataSet.INT;
        } else if ( type==CDFConstants.CDF_INT2 || type==CDFConstants.CDF_UINT1 ) {
            return BufferDataSet.SHORT;
        } else if ( type==CDFConstants.CDF_INT1 || type==CDFConstants.CDF_BYTE ) {
            return BufferDataSet.BYTE;
        } else if ( type==CDFConstants.CDF_CHAR ) {
            return BufferDataSet.BYTE; // determined experimentally: vap+cdfj:file:///home/jbf/ct/hudson/data.backup/cdf/ac_k0_mfi_20080602_v01.cdf?BGSEc
        } else if (type==CDFConstants.CDF_UCHAR ) {
            return BufferDataSet.BYTE; // TODO: I think...
        } else if (type==CDFConstants.CDF_EPOCH16 ) {
            return BufferDataSet.DOUBLE;
        } else {
            throw new IllegalArgumentException("unsupported type: "+type);
        }        
    }
    
    /**
     * column major files require a transpose of each record.  This makes a copy of the input, because I'm nervous
     * that this might be backed by a writable cdf file.
     * @param recLenBytes length of each record in bytes.  (qube=2,3 bbType=float, then this is 2*4=8.)
     * @param qube dimensions, a 0,1,..,4 element array.
     * @param byteBuffer
     * @param bbType
     * @return the byte buffer.
     */
    private static ByteBuffer transpose( int recLenBytes, int[] qube, ByteBuffer byteBuffer, Object bbType ) {
        if ( qube.length<3 ) {
            return byteBuffer;
        }
        
        ByteBuffer temp= ByteBuffer.allocate(recLenBytes);
        ByteBuffer result= ByteBuffer.allocate(recLenBytes * qube[0]);
        result.order(byteBuffer.order());
                
        int fieldBytes= BufferDataSet.byteCount(bbType);
        if ( qube.length==3 ) {
            int len1= qube[1];
            int len2= qube[2];
            for ( int i0=0; i0<qube[0]; i0++ ) {
                for ( int i1=0; i1<qube[1]; i1++ ) {
                    for ( int i2=0; i2<qube[2]; i2++ ) {
                        int iin= fieldBytes * ( i1 * len2 + i2  );
                        int iout= fieldBytes * ( i0 * len1 * len2 + i2 * len1 + i1 );
                        for ( int j=0; j<fieldBytes; j++ ) {
                            temp.put( iin + j, byteBuffer.get(iout+j) );
                        }
                    }
                }
                result.put(temp);
                temp.flip();
            }
        } else if ( qube.length==4 ) {
            int len1= qube[1];
            int len2= qube[2];
            int len3= qube[3];            
            for ( int i0=0; i0<qube[0]; i0++ ) {
                for ( int i1=0; i1<qube[1]; i1++ ) {
                    for ( int i2=0; i2<qube[2]; i2++ ) {
                        for ( int i3=0; i3<qube[3]; i3++ ) {
                            int iin= fieldBytes * ( i1*len2*len3 + i2*len3 +i3 );
                            int iout= fieldBytes * ( i0*len1*len2*len3 + i3*len2*len1 + i2*len1 +i1 );
                            for ( int j=0; j<fieldBytes; j++ ) {
                                temp.put( iin + j, byteBuffer.get( iout + j ) );
                            }
                        }
                    }
                }            
                result.put(temp);
                temp.flip();
            }
        } else if ( qube.length<3 ) {
            return byteBuffer;
        } else {
            throw new IllegalArgumentException("number of dimensions must be less than 5: "+qube.length );
        }
        result.flip();
        
        return result;
    }
    
    /**
     * Creates a new instance of CdfUtil
     */
    public CdfUtil() {
    }

    /**
     * returns the Entry that is convertible to double as a double.
     * @throws NumberFormatException for strings Double.parseDouble
     */
    private static double doubleValue(Object o) {
        if (o instanceof Float) {
            return ((Float) o).doubleValue();
        } else if (o instanceof Double) {
            return ((Double) o);
        } else if (o instanceof Integer) {
            return ((Integer) o).doubleValue();
        } else if (o instanceof Short) {
            return ((Short) o).doubleValue();
        } else if (o instanceof String) {
            return Double.parseDouble((String) o);
        } else {
            throw new RuntimeException("Unsupported Data Type: " + o.getClass().getName());
        }
    }

    /**
     * returns the range of the data by looking for the SCALEMIN/SCALEMAX params,
     * or the required VALIDMIN/VALIDMAX parameters
     */
    public static DatumRange getRange(HashMap attrs) {
        DatumRange range;
        if (attrs.containsKey("SCALEMIN") && attrs.containsKey("SCALEMAX")) {
            range = new DatumRange(doubleValue(attrs.get("SCALEMIN")),
                    doubleValue(attrs.get("SCALEMAX")), Units.dimensionless);
        } else {
            range = new DatumRange(doubleValue(attrs.get("VALIDMIN")),
                    doubleValue(attrs.get("VALIDMAX")), Units.dimensionless);
        }
        return range;
    }

    public static String getScaleType(HashMap attrs) {
        String type = "linear";
        if (attrs.containsKey("SCALETYP")) {
            type = (String) attrs.get("SCALETYP");
        }
        return type;
    }

    /**
     * add the valid range only if it looks like it is correct.  It must contain some of the data.
     */
    public static void maybeAddValidRange( Map<String,Object> props, MutablePropertyDataSet ds ) {

        Units pu= (Units) props.get(QDataSet.UNITS);
        Units u= (Units) ds.property( QDataSet.UNITS );

        UnitsConverter uc;
        if ( pu==null || u==null ) {
            uc= UnitsConverter.IDENTITY;
        } else if ( u==Units.cdfEpoch ) {
            uc= UnitsConverter.IDENTITY;
        } else if ( pu==Units.microseconds && u==Units.us2000 ) { // epoch16
            uc= UnitsConverter.IDENTITY;
        } else {
            if ( pu==u ) {
                uc= UnitsConverter.IDENTITY;
            } else if ( UnitsUtil.isOrdinalMeasurement(u) || UnitsUtil.isOrdinalMeasurement(pu) ) {
                return;
            } else {
                try {
                    uc= UnitsConverter.getConverter( pu, u );
                } catch ( InconvertibleUnitsException ex ) { // PlasmaWave group Polar H7 files
                    uc= UnitsConverter.IDENTITY;
                }
            }
        }

        double dmin=Double.NEGATIVE_INFINITY;
        double dmax=Double.POSITIVE_INFINITY;
        if ( ds.rank()==1 && ds.length()>0 ) {
            QDataSet range= Ops.extent(ds,null,null);
            dmin= uc.convert(range.value(0));
            dmax= uc.convert(range.value(1));
        }

        Number nmin= (Number)props.get(QDataSet.VALID_MIN);
        double vmin= nmin==null ?  Double.POSITIVE_INFINITY : nmin.doubleValue();
        Number nmax= (Number)props.get(QDataSet.VALID_MAX);
        double vmax= nmax==null ?  Double.POSITIVE_INFINITY : nmax.doubleValue();

        boolean intersects= false;
        if ( dmax>vmin && dmin<vmax ) {
            intersects= true;
        }

        if ( u instanceof EnumerationUnits  )  {
            EnumerationUnits eu= (EnumerationUnits)u;
            if ( nmax!=null && nmax.intValue()<=eu.getHighestOrdinal() ) {
                nmax= Integer.valueOf(eu.getHighestOrdinal()+1); // rbsp-e_L1_mageisLOW-sp_20110922_V.06.0.0.cdf?channel_num
            }
        }
        
        if ( intersects || dmax==dmin || dmax<-1e30 || dmin>1e30 )  { //bugfix 3235447: all data invalid
            if ( nmax!=null ) ds.putProperty(QDataSet.VALID_MAX, uc.convert(nmax) );
            if ( nmin!=null ) ds.putProperty(QDataSet.VALID_MIN, uc.convert(nmin) );
        }

        String t= (String) props.get(QDataSet.SCALE_TYPE);
        if ( t!=null ) ds.putProperty( QDataSet.SCALE_TYPE, t );

    }

    /**
     * returns the size of the variable in bytes.
     * @param dims number of dimensions in each record
     * @param dimSizes dimensions of each record
     * @param itype type of data, such as CDFConstants.CDF_FLOAT
     * @param rc number of records (rec count)
     * @return the size the variable in bytes 
     */
    private static long sizeOf( int dims, int[] dimSizes, long itype, long rc ) {
        long size= dims==0 ? rc : rc * DataSetUtil.product( dimSizes );
        long sizeBytes;
        if ( itype==CDFConstants.CDF_EPOCH16 ) {
            sizeBytes= 16;
        } else if(itype == CDFConstants.CDF_DOUBLE || itype == CDFConstants.CDF_REAL8 || itype == CDFConstants.CDF_EPOCH || itype==CDFConstants.CDF_TT2000 || itype==CDFConstants.CDF_INT8 ) {
            sizeBytes= 8;
        } else if( itype == CDFConstants.CDF_FLOAT || itype == CDFConstants.CDF_REAL4 || itype==CDFConstants.CDF_INT4 || itype==CDFConstants.CDF_UINT4 ) {
            sizeBytes=4; //sizeBytes= 4;
        } else if( itype == CDFConstants.CDF_INT2 || itype == CDFConstants.CDF_UINT2  ) {
            sizeBytes=8; //sizeBytes= 2;
        } else if( itype == CDFConstants.CDF_INT1 || itype == CDFConstants.CDF_UINT1 || itype==CDFConstants.CDF_BYTE || itype==CDFConstants.CDF_UCHAR || itype==CDFConstants.CDF_CHAR ) {
            sizeBytes=8; //sizeBytes= 1;
        } else {
            throw new IllegalArgumentException("didn't code for type");
        }
        size= size*sizeBytes;
        return size;
    }

    /**
     * subsample the array in-situ to save memory.  Be careful, this clobbers
     * the old array to save memory!  (Note Java5 doesn't save memory, but it will in Java6.)
     * Note too that this is a kludge, and the CDF library must be changed to support subsampling non-double arrays.
     * @param array the input array, which is clobbered
     * @param recStart
     * @param recCount
     * @param recInterval
     * @return
     */
    private static long[] subsampleTT2000( long[] array, int recStart, int recCount, int recInterval ) {
        int n= recCount;
        for ( int i=0; i<n; i++ ) {
            array[i]= array[ i*recInterval + recStart ];
        }
        //long[] result= Arrays.copyOfRange( array, 0, n ); //TODO: Java6. NOTE: I don't think Java6 improves anything.
        long[] result= new long[n];
        System.arraycopy( array, 0, result, 0, n );
        return result;
    }

    /**
     * returns effective rank.  Nand's code looks for 1-element dimensions, which messes up Seth's file rbspb_pre_ect-mageisHIGH.
     * See files 
     *  vap+cdfj:ftp://cdaweb.gsfc.nasa.gov/pub/data/geotail/lep/2011/ge_k0_lep_20111016_v01.cdf?V0
     *  vap+cdfj:file:///home/jbf/ct/autoplot/data.backup/examples/cdf/seth/rbspb_pre_ect-mageisHIGH-sp-L1_20130709_v1.0.0.cdf?Histogram_prot
     */
    private static int getEffectiveRank( boolean[] varies ) {
        int rank = 0;
        for (int i = 0; i < varies.length; i++) {
            if (!varies[i]) continue;
            rank++;
        }
        return rank;
    }
    
    
    /**
     * Return the named variable as a QDataSet.
     * @param cdf the value of cdf
     * @param svariable name of the variable
     * @param recStart the first record to retrieve (0 is the first record in the file).
     * @param recCount the number of records to retrieve
     * @param recInterval the number of records to increment, typically 1 (e.g. 2= every other record).
     * @param slice1 if non-negative, return the slice at this point.
     * @param mon progress monitor (currently not used), or null.
     * @return the dataset
     * @throws Exception
     */
    public static MutablePropertyDataSet wrapCdfHyperDataHacked(
            CDFReader cdf, String svariable, long recStart, long recCount, long recInterval, int slice1, ProgressMonitor mon) throws Exception {
        logger.log( Level.FINE, "wrapCdfHyperDataSetHacked {0}[{1}:{2}:{3}]", new Object[] { svariable, String.valueOf(recStart), // no commas in {1}
                 ""+(recCount+recStart), recInterval } );
        
        try {
            
        {
//            String cdfFile;
//            synchronized ( CdfDataSource.lock ) {
//                cdfFile= CdfDataSource.openFilesRev.get(cdf);
//            }
//            
//            if ( false && cdfFile!=null ) { //TODO: explore why enabling the cache causes problems with test autoplot-test034.
//                String uri= cdfFile + "?" + svariable;
//                if ( recStart!=0 || recCount!=cdf.getNumberOfValues(svariable) || recInterval>1 ) {
//                    uri= uri + "["+recStart+":"+(recStart+recCount)+":"+recInterval+"]";
//                }
//                synchronized ( CdfDataSource.dslock ) {
//                    MutablePropertyDataSet result= CdfDataSource.dsCache.get( uri );
//                    if ( result!=null ) return result;
//                }
//            }
        }
        
        long varType = cdf.getType(svariable);

        int[] dimSizes = cdf.getDimensions(svariable);
        boolean[] dimVaries= cdf.getVarys(svariable);

        int dims;
        if (dimSizes == null) {
            dims = 0;
            dimSizes= new int[0]; // to simplify code
        } else {
            dims = dimSizes.length;
        }

        if ( getEffectiveRank(dimVaries) != dimSizes.length ) { // vap+cdfj:ftp://cdaweb.gsfc.nasa.gov/pub/data/geotail/lep/2011/ge_k0_lep_20111016_v01.cdf?V0
            int[] dimSizes1= new int[ cdf.getEffectiveRank(svariable) ];
            boolean[] varies= cdf.getVarys(svariable);
            int[] dimensions= cdf.getDimensions(svariable);
            int k=0;
            for ( int i=0; i<varies.length; i++ ) {
                if ( varies[i] && dimensions[i] != 1 ) {
                    dimSizes1[k]= dimSizes[i];
                    k++;
                }
            }
            dimSizes= dimSizes1;
        }
        
        if (dims > 3 ) {
            if (recCount != -1) {
                throw new IllegalArgumentException("rank 5 not implemented");
            }
        }

        int varRecCount= cdf.getNumberOfValues(svariable);
        if ( recCount==-1 && recStart>0 && varRecCount==1 ) { // another kludge for Rockets, where depend was assigned variance
            recStart= 0;
        }

        ByteBuffer[] buf=null;

        long rc= recCount;
        if ( rc==-1 ) rc= 1;  // -1 is used as a flag for a slice, we still really read one record.

        logger.log( Level.FINEST, "size of {0}: {1}MB  type: {2}", new Object[]{svariable, sizeOf(dims, dimSizes, varType, rc) / 1024. / 1024., varType});
        
        Object bbType= byteBufferType( cdf.getType(svariable) );
        int recLenBytes= BufferDataSet.byteCount(bbType);
        if ( dimSizes.length>0 ) recLenBytes= recLenBytes * DataSetUtil.product( dimSizes );            
        
        try {

            String stype = getTargetType( cdf.getType(svariable) );
            ByteBuffer buff2;
            if ( recInterval>1 ) {
                buff2= ByteBuffer.allocate((int)(recLenBytes*rc));
                for ( int i=0; i<rc; i++ ) {
                    int recNum= (int)recStart+(int)recInterval*i;
                    ByteBuffer buff1= cdf.getBuffer( svariable, stype, new int[] { recNum,recNum }, true );
                    buff2.put(buff1);
                    if ( i==0 ) buff2.order(buff1.order());
                }
                buff2.flip();
            } else {
                buff2= cdf.getBuffer( svariable, stype, new int[] { (int)recStart,(int)(recStart+recInterval*(rc-1)) }, true );
            }
            buf= new ByteBuffer[] { buff2 };
            
        } catch ( Throwable ex ) {
            if ( ex instanceof Exception ) {
                throw (Exception)ex;
            } else {
                throw new Exception(ex);
            }
        }

        MutablePropertyDataSet result;
        
        int[] qube;
        qube= new int[ 1+dimSizes.length ];
        for ( int i=0; i<dimSizes.length; i++ ) {
            qube[i+1]= (int)dimSizes[i];
        }
        if ( recCount==-1 ) {
            qube[0]= 1;
        } else {
            qube[0]= (int)recCount;
        }

        if ( slice1>-1 ) { 
            if ( recCount==-1 ) throw new IllegalArgumentException("recCount==-1 and slice1>-1");
            int[] nqube= new int[qube.length-1];
            nqube[0]= qube[0];
            for ( int i=2;i<qube.length;i++ ) {
                nqube[i-1]= qube[i];
            }
            qube= nqube;
        }
                          
        if ( cdf.rowMajority()  ) {

            if ( recCount==-1 ) {
                result= BufferDataSet.makeDataSet( qube.length, recLenBytes, 0, 
                        qube,
                        buf[0], bbType );
                result= (MutablePropertyDataSet)result.slice(0);
                
            } else {
                result= BufferDataSet.makeDataSet(qube.length, recLenBytes, 0, 
                        qube,
                        buf[0], bbType );
            }
        } else {
            if ( recCount==-1 ) {
                buf[0]= transpose(recLenBytes,qube,buf[0],bbType );
                
                result= BufferDataSet.makeDataSet( qube.length, recLenBytes, 0, 
                        qube,
                        buf[0], bbType );
                result= (MutablePropertyDataSet)result.slice(0);
            } else {
                buf[0]= transpose(recLenBytes,qube,buf[0],bbType );

                result= BufferDataSet.makeDataSet(qube.length, recLenBytes, 0, 
                        qube,
                        buf[0], bbType );
            }
        }

        if ( varType == CDFConstants.CDF_CHAR || varType==CDFConstants.CDF_UCHAR ) {
            EnumerationUnits units = EnumerationUnits.create(svariable);
            Object o;
            try {
                if ( recInterval>1 ) throw new IllegalArgumentException("recInterval>1 not supported here");
                o = cdf.get(svariable);
            } catch (Throwable ex) {
                throw new RuntimeException(ex);
            }
            Object o0= Array.get(o,0);
            String[] sdata;
            if ( o0.getClass().isArray() ) {
                sdata= new String[ Array.getLength(o0) ];
                for ( int j=0; j<Array.getLength(o0); j++ ) {
                    sdata[j]= (String) Array.get(o0, j);
                }
            } else if ( o0.getClass()==String.class ) {
                sdata= new String[ 1 ]; //vap+cdaweb:ds=ALOUETTE2_AV_LIM&id=freq_mark&timerange=1967-01-15+12:59:00+to+12:59:01
                sdata[0]= String.valueOf( o0 );
            } else {
                throw new IllegalArgumentException("not handled single array where expected double array");
            }

            int[] back = new int[sdata.length];
            for (int i = 0; i < sdata.length; i++) {
                back[i] = (int)( units.createDatum(sdata[i]).doubleValue(units) );
            }
            boolean[] varies= cdf.getVarys(svariable);
            boolean canSlice= recCount==-1;
            if ( canSlice ) {
                for ( int i=1; i<varies.length; i++ ) canSlice= canSlice && !varies[i];
            }
            if ( canSlice ) {
                qube= new int[] { qube[1] };
            }
            result= ArrayDataSet.wrap( back, qube, false );
            result.putProperty(QDataSet.UNITS, units);

        } else if ( varType == CDFConstants.CDF_EPOCH ) {
            result.putProperty(QDataSet.UNITS, Units.cdfEpoch);
            result.putProperty(QDataSet.VALID_MIN, 1.); // kludge for Timas, which has zeros.
            
        } else if ( varType==CDFConstants.CDF_EPOCH16 ) {

            result.putProperty(QDataSet.UNITS, Units.cdfEpoch);
            result.putProperty(QDataSet.VALID_MIN, 1.); // kludge for Timas, which has zeros.
            
            DDataSet result1= DDataSet.createRank1(result.length());
            for ( int i=0; i<result.length(); i++ ) {
                double t2000 = result.value(i,0) - 6.3113904e+10; // seconds since midnight 2000
                result1.putValue( i, t2000 * 1e6 + result.value(i,1) / 1000000. );
            }
            result1.putProperty( QDataSet.UNITS, Units.us2000 );
            result= result1;
            
            // adapt to das2 by translating to Units.us2000, which should be good enough.
            // note when this is not good enough, new units types can be introduced, along with conversions.


        } else if ( varType==CDFConstants.CDF_TT2000 ) {
            result.putProperty( QDataSet.UNITS, Units.cdfTT2000 );

        }

//logger.fine( "jvmMemory (MB): "+jvmMemory(result)/1024/1024 );
        if ( varType==CDFConstants.CDF_EPOCH || varType==CDFConstants.CDF_EPOCH16 || varType==CDFConstants.CDF_TT2000 ) {
            String cdfFile;
            synchronized ( CdfDataSource.lock ) {
                cdfFile= CdfDataSource.openFilesRev.get(cdf);
            }
            if ( cdfFile!=null ) {
                String uri= cdfFile + "?" + svariable;
                if ( recStart!=0 || recCount!=cdf.getNumberOfValues(svariable) || recInterval>1 ) {
                    uri= uri + "["+recStart+":"+(recStart+recCount)+":"+recInterval+"]";
                }
                CdfDataSource.dsCachePut( uri, result );
            }
        }
        
        //if ( slice1>-1 ) {
        //    result= DataSetOps.slice1(result,slice1);
        //}
                
        return result;
        } catch ( CDFException ex ) {
            throw new Exception( ex.getMessage(), ex );
        }
    }
    

    /**
     * returns the amount of JVM memory in bytes occupied by the dataset. This is an approximation,
     * calculated by taking the element type size (e.g. float=4 bytes) times the number of elements for
     * the dataset.
     * @param ds the ArrayDataSet, or TrArrayDataSet, or BufferDataSet.
     * @return the approximate memory consumption in bytes
     */
    public static int jvmMemory( QDataSet ds ) {
        if ( ds instanceof ArrayDataSet ) {
            return ((ArrayDataSet)ds).jvmMemory();
        } else if ( ds instanceof TrArrayDataSet ) {
            return ((TrArrayDataSet)ds).jvmMemory();
        } else if ( ds instanceof Slice0DataSet ) {
            return 0; // TODO: not worth chasing after.  TODO: really?  these are often backed by the original data.
        } else if ( ds instanceof BufferDataSet ) {
           return ((BufferDataSet)ds).jvmMemory();
        } else {
            throw new IllegalArgumentException("not supported type of QDataSet: "+ds);
        }
    }

    public static Map<String, String> getPlottable(CDFReader cdf, boolean dataOnly, int rankLimit) throws Exception {
        return getPlottable(cdf, dataOnly, rankLimit, false, false);
    }

    /**
     * return the data type for the encoding.  From
     * ftp://cdaweb.gsfc.nasa.gov/pub/cdf/doc/cdf33/cdf33ifd.pdf  page 33.
     * @param type integer type, such as 44 for CDF_FLOAT
     * @return string like "CDF_FLOAT"
     */
    public static String getStringDataType( int type ) {
        switch ( type ) {
            case 1: return "CDF_INT1";
            case 2: return "CDF_INT2";
            case 4: return "CDF_INT4";
            case 8: return "CDF_INT8";
            case 11: return "CDF_UINT1";
            case 12: return "CDF_UINT2";
            case 14: return "CDF_UINT4";
            case 41: return "CDF_BYTE";
            case 21:return "CDF_REAL4";
            case 22:return "CDF_REAL8";
            case 44:return "CDF_FLOAT";
            case 45:return "CDF_DOUBLE";
            case 31:return "CDF_EPOCH";
            case 32 :return "CDF_EPOCH16";
            case 33: return "CDF_TT2000";
            case 51 :return "CDF_CHAR";
            default: return String.valueOf(type);
        }
    }

    /**
     * returns null or the attribute.
     * @param cdf
     * @param var
     * @param attrname
     * @return null if there was a problem
     */
    private static Object getAttribute( CDFReader cdf, String var, String attrname ) {
        try {
            Object att= cdf.getAttribute( var,attrname );
            if ( att==null ) return null;
            if ( ((Vector)att).isEmpty() ) return null;
            att= ((Vector)att).get(0);
            return att;
        } catch ( CDFException ex ) {
            logger.fine(ex.getMessage());
            return null;
        }
    }


    public static boolean hasAttribute( CDFReader cdf, String var, String attrname ) {
        try {
            Object att= cdf.getAttribute( var,attrname );
            return !( att==null );
        } catch ( CDFException ex ) {
            return false;
        }
    }

    //container for description of depend
    private static class DepDesc {
        String dep;
        String labl;
        long nrec;
        boolean rank2;
    }

    /**
     * factor out common code that gets the properties for each dimension.
     * @param cdf
     * @param var
     * @param rank
     * @param dims
     * @param dim
     * @param warn
     * @return
     */
    private static DepDesc getDepDesc( CDFReader cdf, String svar, int rank, int[] dims, int dim, List<String> warn ) {
        DepDesc result= new DepDesc();

        result.nrec=-1;
        
        try {
            if ( hasAttribute( cdf, svar, "DEPEND_"+dim ) ) {  // check for metadata for DEPEND_1
                Object att= getAttribute( cdf, svar, "DEPEND_"+dim );
                if ( att!=null && rank>1 ) {
                    logger.log(Level.FINE, "get attribute DEPEND_"+dim+" entry for {0}", svar );
                    result.dep = String.valueOf(att);
                    if ( result.dep==null ) {
                        if ( !hasAttribute( cdf, svar, "LABL_PTR_"+dim ) ) {
                            throw new Exception("No such variable: "+String.valueOf(att));
                        } else {

                        }
                    } else {
                        if ( cdf.getDimensions( result.dep ).length>0 && cdf.getNumberOfValues( result.dep )>1 && cdf.recordVariance( result.dep ) ) {
                            result.rank2= true;
                            result.nrec = cdf.getDimensions( result.dep )[0];
                            warn.add( "NOTE: " + result.dep + " is record varying" );
                        } else {
                            result.nrec = cdf.getNumberOfValues( result.dep );
                            if (result.nrec == 1) {
                                result.nrec = cdf.getDimensions( result.dep )[0];
                            }
                        }
                        if ( dims.length>(dim-2) && (result.nrec)!=dims[dim-1] ) {
                            warn.add("DEPEND_"+dim+" length ("+result.nrec+") is inconsistent with length ("+dims[dim-1]+")" );
                        }
                    }
                }
            }
        } catch ( CDFException e) {
            warn.add( "problem with DEPEND_"+dim+": " + e.getMessage() );//e.printStackTrace();
        } catch ( Exception e) {
            warn.add( "problem with DEPEND_"+dim+": " + e.getMessage() );//e.printStackTrace();
        }

        try {
             if (result.nrec==-1 && hasAttribute( cdf, svar, "LABL_PTR_"+dim ) ) {  // check for metadata for LABL_PTR_1
                Object att= getAttribute( cdf, svar, "LABL_PTR_"+dim );
                if ( att!=null && rank>1  ) {
                    logger.log(Level.FINE, "get attribute LABL_PTR_"+dim+" entry for {0}", svar );
                    result.labl = String.valueOf(att);
                    if ( result.labl==null ) throw new Exception("No such variable: "+String.valueOf(att));
                    result.nrec = cdf.getNumberOfValues( result.labl );
                    if (result.nrec == 1) {
                        result.nrec = cdf.getDimensions(svar)[0];
                    }
                    if ( dims.length>(dim-2) && (result.nrec)!=dims[dim-1] ) {
                        warn.add("LABL_PTR_"+dim+" length is inconsistent with length ("+dims[dim-1]+")" );
                    }
                }
            } else if ( hasAttribute( cdf, svar, "LABL_PTR_"+dim ) ) { // check that the LABL_PTR_i is the right length as well.
                Object att= getAttribute( cdf, svar, "LABL_PTR_"+dim );
                if ( att!=null && rank>1  ) {
                    logger.log(Level.FINE, "get attribute LABL_PTR_"+dim+" entry for {0}", svar );
                    result.labl= String.valueOf(att);
                    if ( result.labl==null ) {
                        warn.add("LABL_PTR_"+dim+" refers to "+String.valueOf(att)+" but this is not found" );
                    } else {
                        int nrec = cdf.getNumberOfValues(result.labl);
                        if ( nrec == 1) {
                            nrec = cdf.getDimensions(result.labl)[0];
                        }
                        if ( dims.length>(dim-2) && (nrec)!=dims[dim-1] ) {
                            warn.add("LABL_PTR_"+dim+" length is inconsistent with length ("+dims[dim-1]+")" );
                        }
                    }
                }
            }
        } catch (CDFException e) {
            warn.add( "problem with LABL_PTR_"+dim+": " + e.getMessage() );//e.printStackTrace();
        } catch (Exception e) {
            warn.add( "problem with LABL_PTR_"+dim+": " + e.getMessage() );//e.printStackTrace();
        }
        return result;
    }

    private static boolean hasVariable( CDFReader cdf, String var ) {
        List<String> names= Arrays.asList( cdf.getVariableNames() );
        return names.contains(var);
    }    
    
    /**
     * keys are the names of the variables. values are descriptions.
     * @param cdf
     * @param dataOnly show only the DATA and not SUPPORT_DATA.  Note I reclaimed this parameter because I wasn't using it.
     * @param rankLimit show only variables with no more than this rank.
     * @param deep return more detailed descriptions in HTML
     * @param master cdf is a master cdf, so don't indicate record counts.
     * @return map of parameter name to short description
     * @throws gsfc.nssdc.cdf.CDFException
     */
    public static Map<String, String> getPlottable(CDFReader cdf, boolean dataOnly, int rankLimit, boolean deep, boolean master) throws Exception {

        Map<String, String> result = new LinkedHashMap<String, String>();
        Map<String, String> dependent= new LinkedHashMap<String, String>();

        boolean isMaster= master; //cdf.getName().contains("MASTERS"); // don't show of Epoch=0, just "Epoch"

        logger.fine("getting CDF variables");
        String[] v = cdf.getVariableNames();
        logger.log(Level.FINE, "got {0} variables", v.length);

        logger.fine("getting CDF attributes");

        int skipCount=0;
        for (int i=0; i<v.length; i++ ) {
            String svar = v[i];
            if ( dataOnly ) {
               Object attr= getAttribute( cdf, svar, "VAR_TYPE" );
               if ( attr==null || !attr.equals("data") ) {
                   skipCount++;
               }
            }
        }
        if ( skipCount==v.length ) {
            logger.fine( "turning off dataOnly because it rejects everything");
            dataOnly= false;
        }

        for (int i = 0; i < v.length; i++) {

            String svar=null;
            List<String> warn= new ArrayList();
            String xDependVariable=null;
            boolean isVirtual= false;
            long xMaxRec = -1;
            long maxRec= -1;
            long recCount= -1;
            String scatDesc = null;
            String svarNotes = null;                
            StringBuilder vdescr=null;
            int rank=-1;
            int[] dims=new int[0];
                    
            try {
                svar = v[i];
                int varType;
                try {
                    varType= cdf.getType(svar);
                } catch ( CDFException ex ) {
                    throw new RuntimeException(ex);
                }
                
                // reject variables that are ordinal data that do not have DEPEND_0.
                Object dep0= cdf.getAttribute("DEPEND_0");
                boolean hasDep0= false;
                if ( dep0!=null ) {
                    hasDep0= true;
                }
                if ( ( varType == CDFConstants.CDF_CHAR || varType==CDFConstants.CDF_UCHAR ) && ( !hasDep0 ) ) {
                    logger.log(Level.FINER, "skipping becuase ordinal and no depend_0: {0}", svar );
                    continue;
                }

                maxRec = cdf.getNumberOfValues(svar); 
                recCount= maxRec;

                dims = cdf.getDimensions(svar);

                boolean[] dimVary= cdf.getVarys(svar);

                // cdf java Nand
                if ( dimVary.length>0 && dimVary[0]==false ) {
                    dims= new int[0];
                }

                if (dims == null) {
                    rank = 1;
                } else {
                    rank = dims.length + 1;
                }

                if (rank > rankLimit) {
                    continue;
                }

                if ( svar.equals("Time_PB5") ) {
                    logger.log(Level.FINE, "skipping {0} because we always skip Time_PB5", svar );
                    continue;
                }

                if ( dataOnly ) {
                    Object attr= getAttribute( cdf, svar, "VAR_TYPE" );
                    if ( attr==null || !attr.equals("data") ) {
                        continue;
                    }
                }

                Object att= getAttribute( cdf, svar, "VIRTUAL" );
                if ( att!=null ) {
                    logger.log(Level.FINE, "get attribute VIRTUAL entry for {0}", svar );
                    if ( String.valueOf(att).toUpperCase().equals("TRUE") ) {
                        String funct= (String)getAttribute( cdf, svar, "FUNCTION" );
                        if ( funct==null ) funct= (String) getAttribute( cdf, svar, "FUNCT" ) ; // in alternate_view in IDL: 11/5/04 - TJK - had to change FUNCTION to FUNCT for IDL6.* compatibili
                        if ( !CdfVirtualVars.isSupported(funct) ) {
                            if ( !funct.startsWith("comp_themis") ) {
                                logger.log(Level.FINE, "virtual function not supported: {0}", funct);
                            }
                            continue;
                        } else {
                            vdescr= new StringBuilder(funct);
                            vdescr.append( "( " );
                            int icomp=0;
                            String comp= (String)getAttribute( cdf, svar, "COMPONENT_"+icomp );
                            if ( comp!=null ) {
                                vdescr.append( comp );
                                icomp++;
                            }
                            for ( ; icomp<5; icomp++ ) {
                                comp= (String)getAttribute( cdf, svar, "COMPONENT_"+icomp );
                                if ( comp!=null ) {
                                    vdescr.append(", ").append(comp);
                                } else {
                                    break;
                                }
                            }
                            vdescr.append(" )");
                        }
                        isVirtual= true;
                    }
                }
            } catch (CDFException e) {
                logger.fine(e.getMessage());
            } catch (Exception e) {
                logger.fine(e.getMessage());
            }
            
            try {
                if ( hasAttribute( cdf, svar, "DEPEND_0" )) {  // check for metadata for DEPEND_0
                    Object att= getAttribute( cdf, svar, "DEPEND_0" );
                    if ( att!=null ) {
                        logger.log(Level.FINE, "get attribute DEPEND_0 entry for {0}", svar);
                        xDependVariable = String.valueOf(att);
                        if ( !hasVariable(cdf,xDependVariable ) ) throw new Exception("No such variable: "+String.valueOf(att));
                        xMaxRec = cdf.getNumberOfValues( xDependVariable );
                        if ( xMaxRec!=maxRec && vdescr==null && cdf.recordVariance(svar) ) {
                            if ( maxRec==-1 ) maxRec+=1; //why?
                            if ( maxRec==0 ) {
                                warn.add("data contains no records" );
                            } else {
                                warn.add("depend0 length is inconsistent with length ("+(maxRec)+")" );
                            }
                            //TODO: warnings are incorrect for Themis data.
                        }
                    } else {
                        if ( dataOnly ) {
                            continue; // vap+cdaweb:ds=GE_K0_PWI&id=freq_b&timerange=2012-06-12
                        }
                    }
                }
            } catch (CDFException e) {
                warn.add( "problem with DEPEND_0: " + e.getMessage() );
            } catch (Exception e) {
                warn.add( "problem with DEPEND_0: " + e.getMessage() );
            }

            DepDesc dep1desc= getDepDesc( cdf, svar, rank, dims, 1, warn );

            DepDesc dep2desc= getDepDesc( cdf, svar, rank, dims, 2, warn );

            DepDesc dep3desc= getDepDesc( cdf, svar, rank, dims, 3, warn );

            if (deep) {
                Object o= (Object) getAttribute( cdf, svar, "CATDESC" );
                if ( o != null && o instanceof String ) {
                    logger.log(Level.FINE, "get attribute CATDESC entry for {0}", svar );
                    scatDesc = (String)o ;
                }
                o=  getAttribute( cdf, svar, "VAR_NOTES" );
                if ( o!=null  && o instanceof String ) {
                    logger.log(Level.FINE, "get attribute VAR_NOTES entry for {0}", svar );
                    svarNotes = (String)o ;
                }
            }

            String desc = svar;
            if (xDependVariable != null) {
                desc += "(" + xDependVariable;
                if ( xMaxRec>0 || !isMaster ) { // small kludge for CDAWeb, where we expect masters to be empty.
                     desc+= "=" + (xMaxRec);
                }
                if ( dep1desc.dep != null) {
                    desc += "," + dep1desc.dep + "=" + dep1desc.nrec + ( dep1desc.rank2 ? "*": "" );
                    if ( dep2desc.dep != null) {
                        desc += "," + dep2desc.dep + "=" + dep2desc.nrec + ( dep2desc.rank2 ? "*": "" );
                        if (dep3desc.dep != null) {
                            desc += "," + dep3desc.dep + "=" + dep3desc.nrec + ( dep3desc.rank2 ? "*": "" );
                        }
                    }
                } else if ( rank>1 ) {
                    desc += ","+DataSourceUtil.strjoin( dims, ",");
                }
                desc += ")";
            }

            if (deep) {
                StringBuilder descbuf = new StringBuilder("<html><b>" + desc + "</b><br>");

                int itype= -1;
                try { 
                    //assert svar is valid.
                    itype= cdf.getType(svar);
                } catch ( CDFException ex ) {} ;
                
                String recDesc= ""+ CdfUtil.getStringDataType( itype );
                if ( dims!=null ) {
                    recDesc= recDesc+"["+ DataSourceUtil.strjoin( dims, ",") + "]";
                }
                if (maxRec != xMaxRec)
                    if ( isVirtual ) {
                        descbuf.append("").append("(virtual function ").append(vdescr).append( ")<br>");
                    } else {
                        descbuf.append("").append( recCount ).append(" records of ").append(recDesc).append("<br>");
                    }
                if (scatDesc != null)
                    descbuf.append("").append(scatDesc).append("<br>");
                if (svarNotes !=null ) {
                    descbuf.append("<br><p><small>").append(svarNotes).append("<small></p>");
                }

                for ( String s: warn ) {
                    if ( s.startsWith("NOTE") ) {
                        descbuf.append("<br>").append(s);
                    } else {
                        descbuf.append("<br>WARNING: ").append(s);
                    }
                }

                descbuf.append("</html>");
                if ( xDependVariable!=null ) {
                    dependent.put(svar, descbuf.toString());
                } else {
                    result.put(svar, descbuf.toString());
                }
            } else {
                if ( xDependVariable!=null ) {
                    dependent.put(svar, desc);
                } else {
                    result.put(svar, desc);
                }
            }

        } // for

        logger.fine("done, get plottable ");

        dependent.putAll(result);

        return dependent;
    }

}
