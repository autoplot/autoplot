/*
 * CdfUtil.java
 *
 * Created on July 24, 2007, 12:56 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.cdf;

import gov.nasa.gsfc.voyager.cdf.Attribute;
import gov.nasa.gsfc.voyager.cdf.CDF;
import gov.nasa.gsfc.voyager.cdf.Variable;
import gov.nasa.gsfc.voyager.cdf.VariableDataLocator;
import java.util.logging.Level;
import org.das2.datum.DatumRange;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import java.lang.reflect.Array;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Logger;
import org.das2.datum.InconvertibleUnitsException;
import org.das2.datum.UnitsConverter;
import org.das2.datum.UnitsUtil;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.binarydatasource.BufferDataSet;
import org.virbo.dataset.ArrayDataSet;
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

    private final static Logger logger = Logger.getLogger("virbo.cdfdatasource");

    /**
     * Creates a new instance of CdfUtil
     */
    public CdfUtil() {
    }

    /**
     * returns the Entry that is convertable to double as a double.
     * @throws NumberFormatException for strings Double.parseDouble
     */
    private static double doubleValue(Object o) {
        if (o instanceof Float) {
            return ((Float) o).doubleValue();
        } else if (o instanceof Double) {
            return ((Double) o).doubleValue();
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
        if ( ds.rank()==1 ) {
            QDataSet range= Ops.extent(ds);
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
            if ( nmax.intValue()<=eu.getHighestOrdinal() ) {
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

    private static long sizeOf( int dims, int[] dimSizes, long itype, long rc ) {
        long size= dims==0 ? rc : rc * DataSetUtil.product( dimSizes );
        long sizeBytes;
        if ( itype==CDFConstants.CDF_EPOCH16 ) {
            sizeBytes= 16;
        } else if(itype == CDFConstants.CDF_DOUBLE || itype == CDFConstants.CDF_REAL8 || itype == CDFConstants.CDF_EPOCH) {
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

    public static MutablePropertyDataSet wrapCdfHyperDataHacked(
            CDF cdf, Variable variable, long recStart, long recCount, long recInterval, ProgressMonitor mon ) throws Exception {

        {
            String cdfFile= CdfJavaDataSource.openFilesRev.get(cdf);
            if ( false && cdfFile!=null ) {
                String uri= cdfFile + "?" + variable.getName();
                if ( recStart!=0 || recCount!=variable.getNumberOfValues() || recInterval>1 ) {
                    uri= uri + "["+recStart+":"+(recStart+recCount)+":"+recInterval+"]";
                }
                MutablePropertyDataSet result= CdfJavaDataSource.dsCache.get( uri );
                if ( result!=null ) return result;
            }
        }
        if ( mon==null ) mon= new org.das2.util.monitor.NullProgressMonitor();
        
        long varType = variable.getType();

        int[] dimSizes = variable.getDimensions();
        boolean[] dimVaries= variable.getVarys();

        if ( variable.getEffectiveRank() != dimSizes.length ) { // vap+cdfj:ftp://cdaweb.gsfc.nasa.gov/pub/istp/geotail/lep/2011/ge_k0_lep_20111016_v01.cdf?V0
            int[] dimSizes1= new int[ variable.getEffectiveRank() ];
            System.arraycopy(dimSizes, 0, dimSizes1, 0, variable.getEffectiveRank());
            dimSizes= dimSizes1;
        }

        int dims;
        if (dimSizes == null) {
            dims = 0;
        } else {
            dims = dimSizes.length;
        }

        if (dims > 3 ) {
            if (recCount != -1) {
                throw new IllegalArgumentException("rank 5 not implemented");
            }
        }

        int varRecCount= variable.getNumberOfValues();
        if ( recCount==-1 && recStart>0 && varRecCount==1 ) { // another kludge for Rockets, where depend was assigned variance
            recStart= 0;
        }

        //CDFData cdfData= CDFData.get( variable, recStart, Math.max(1, recCount), recInterval, dimIndeces, dimCounts, dimIntervals, false );
        Object odata=null;
        ByteBuffer[] buf=null;
        boolean useBuf= false;  // switch to turn NIO use on/off

        long rc= recCount;
        if ( rc==-1 ) rc= 1;  // -1 is used as a flag for a slice, we still really read one record.


        logger.log( Level.FINEST, "size of {0}: {1}  type: {2}", new Object[]{variable.getName(), sizeOf(dims, dimSizes, varType, rc) / 1024. / 1024., varType});

        try {
            if ( recStart==0 && ( recCount==-1 || recCount==varRecCount ) && recInterval==1 ) {
                if ( useBuf && variable.rowMajority() && ( variable.getType()==CDFConstants.CDF_FLOAT || variable.getType()==CDFConstants.CDF_REAL4 ) ) {
                    //buf= Extractor.get1DSeriesNio( cdf, variable, null);
                    //odata= cdf.get1D( variable.getName() );
                    VariableDataLocator loc= variable.getLocator();
                    int[][] intloc= loc.getLocations();
                    ByteBuffer buff= variable.getBuffer();

                    buf= new ByteBuffer[intloc.length];
                    // use to test: vap+cdfj:ftp://cdaweb.gsfc.nasa.gov/pub/istp/ace/cris_h2/2011/ac_h2_cris_20110802_v05.cdf?flux_B (158701 bytes)
                    for ( int part=0; part<intloc.length; part++ ) {
                        int nrec= intloc[part][1]-intloc[part][0] + 1;
                        int reclen= variable.getDataItemSize();
                        buff.limit( intloc[part][2] + nrec*reclen );
                        buff.position(intloc[part][2]);
                        Buffer chunk= buff.slice();
                        buf[part]= (ByteBuffer)chunk;
                    }
                    //odata= cdf.get1D( variable.getName() ); // this is my hack
                } else {
                    //System.err.println("reading variable "+variable.getName());
                    odata= cdf.get1D( variable.getName(), true );
                }
            } else {
                int[] stride= new int[dims+1];
                for ( int i=0; i<dims+1; i++ ) stride[i]= 1;
                stride[0]= (int)recInterval;
                odata= cdf.get1D( variable.getName(), (int)recStart, (int)(recStart+rc*recInterval), stride ); 
//                odata= cdf.get1D( variable.getName(), (int)recStart, (int)(recStart+(rc-1)*recInterval), stride ); //TODO: I think an extra record is extracted.  Try this with stride sometime.
            }
        } catch ( Throwable ex ) {
            if ( ex instanceof Exception ) {
                throw (Exception)ex;
            } else {
                throw new Exception(ex);
            }
        }

        if ( ( odata==null && buf==null ) && ( varType != CDFConstants.CDF_CHAR && varType!=CDFConstants.CDF_UCHAR ) ) {
            System.err.println("something went wrong");
            throw new NullPointerException("something went wrong");
        }

        MutablePropertyDataSet result;

        if ( dims==0 ) dimSizes= new int[0]; // to simplify code

        // Nand's library
        if ( dimVaries.length>0 && dimVaries[0]==false && dimSizes.length!=0 ) { //TODO: I don't think this is necessary now, see above "variable.getEffectiveRank() != dimSizes.length"
            System.err.println("here at 307 CdfUtil");
//            dimSizes= new int[0];
        }

        //kludge: in library, where majority has no effect on dimSizes.  See
        if ( ! variable.rowMajority()  ) {
            int n= dimSizes.length;
            for ( int i=0; i<n/2; i++ ) {
                int t= dimSizes[i];
                dimSizes[i]= dimSizes[n-i-1];
                dimSizes[n-i-1]= t;
            }
        }
        
        int[] qube;
        if ( recCount==-1 ) {
            qube= new int[ dimSizes.length ];
            for ( int i=0; i<dimSizes.length; i++ ) {
                qube[i]= (int)dimSizes[i];
            }
        } else {
            qube= new int[ 1+ dimSizes.length ];
            for ( int i=0; i<dimSizes.length; i++ ) {
                qube[1+i]= (int)dimSizes[i];
            }
            qube[0]= (int)recCount;
        }

        if ( variable.rowMajority()==false ) buf= null;   // we won't support this yet.

        if ( variable.rowMajority()  ) {
            if ( useBuf && buf!=null ) {
                if ( variable.getType()==CDFConstants.CDF_FLOAT || variable.getType()==CDFConstants.CDF_REAL4 ) {
                    int reclen= 1;
                    for ( int i=1; i<qube.length; i++ ) reclen*= qube[i];

                    Object type;
                    switch ( variable.getType() ) {
                        case (int)CDFConstants.CDF_FLOAT:
                            type= BufferDataSet.FLOAT;
                            break;
                        case (int)CDFConstants.CDF_REAL4:
                            type= BufferDataSet.FLOAT;
                            break;
                        default: throw new RuntimeException("bad type!");
                    }
                    reclen= reclen * BufferDataSet.byteCount(type);

                    ByteBuffer aggBuffer= ByteBuffer.allocate(reclen*qube[0]);
                    for ( int i=0; i<buf.length; i++ ) {
                        aggBuffer.put(buf[i]);
                    }
                    aggBuffer.flip();

                    result= BufferDataSet.makeDataSet( qube.length, reclen, 0, qube[0],
                                qube.length < 2 ? 1 : qube[1],
                                qube.length < 3 ? 1 : qube[2],
                                qube.length < 4 ? 1 : qube[3],
                                aggBuffer, type );
                } else {
                    throw new IllegalArgumentException("internal error unimplemented: "+variable.getType() );
                }
            } else {
                result= ArrayDataSet.wrap( odata, qube, false );
            }
        } else {
            if ( recCount==-1 ) {
                if ( true ) { //TODO: looks to me like there are two transposes here, see above
                    if ( qube.length==2 ) {
                        int tr= qube[1];
                        qube[1]= qube[0];
                        qube[0]= tr;
                    } else if ( qube.length==3 ) {
                        int tr= qube[2];
                        qube[2]= qube[0];
                        qube[0]= tr;
                    } else if ( qube.length>4 ) {
                        throw new IllegalArgumentException("rank limit");
                    }
                }
                int [] qqube= new int[qube.length+1];
                qqube[0]= 1;
                System.arraycopy(qube, 0, qqube, 1, qube.length);
                //result= TrArrayDataSet.wrap( odata, qqube, false );
                result= (MutablePropertyDataSet) TrArrayDataSet.wrap( odata, qqube, false ).slice(0);
            } else {
                if ( true ) {
                    if ( qube.length==3 ) {
                        int tr= qube[2];
                        qube[2]= qube[1];
                        qube[1]= tr;
                    } else if ( qube.length==4 ) {
                        int tr= qube[3];
                        qube[3]= qube[1];
                        qube[1]= tr;
                    } else if ( qube.length>4 ) {
                        throw new IllegalArgumentException("rank limit");
                    }
                }
                result= TrArrayDataSet.wrap( odata, qube, false );
            }
        }

        if ( varType == CDFConstants.CDF_CHAR || varType==CDFConstants.CDF_UCHAR ) {
            EnumerationUnits units = EnumerationUnits.create(variable.getName());
            Object o;
            try {
                if ( recInterval>1 ) throw new IllegalArgumentException("recInterval>1 not supported here");
                o = cdf.get(variable.getName());
            } catch (Throwable ex) {
                throw new RuntimeException(ex);
            }
            Object o0= Array.get(o,0);
            String[] sdata;
            if ( o0.getClass().isArray() ) {
                sdata= new String[ Array.getLength(o0) ];
                for ( int j=0; j<Array.getLength(o0); j++ ) {
                    sdata[j]= Ops.saferName( (String) Array.get(o0, j) );
                }
            } else if ( o0.getClass()==String.class ) {
                sdata= new String[ 1 ]; //vap+cdaweb:ds=ALOUETTE2_AV_LIM&id=freq_mark&timerange=1967-01-15+12:59:00+to+12:59:01
                sdata[0]= Ops.saferName( String.valueOf( o0 ) );
            } else {
                throw new IllegalArgumentException("not handled single array where expected double array");
            }

            int[] back = new int[sdata.length];
            for (int i = 0; i < sdata.length; i++) {
                back[i] = (int)( units.createDatum(sdata[i]).doubleValue(units) );
            }
            boolean[] varies= variable.getVarys();
            boolean canSlice= recCount==-1;
            if ( canSlice ) {
                for ( int i=1; i<varies.length; i++ ) canSlice= canSlice && !varies[i];
            }
            if ( canSlice ) {
                qube= new int[] { qube[0] };
            }
            result= ArrayDataSet.wrap( back, qube, false );
            result.putProperty(QDataSet.UNITS, units);

        } else if ( varType == CDFConstants.CDF_EPOCH ) {
            if ( qube.length==2 && qube[1]==1 ) {// kludge for c4_cp_fgm_spin_20030102_v01.cdf?B_vec_xyz_gse__C4_CP_FGM_SPIN
                qube= new int[] { qube[0] };
            }
            result= ArrayDataSet.wrap( odata, qube, false );
            result.putProperty(QDataSet.UNITS, Units.cdfEpoch);
            result.putProperty(QDataSet.VALID_MIN, 1.); // kludge for Timas, which has zeros.
        } else if ( varType==CDFConstants.CDF_EPOCH16 ) {
            // adapt to das2 by translating to Units.us2000, which should be good enough.
            // note when this is not good enough, new units types can be introduced, along with conversions.
            double[] data = (double[]) odata;
            double[] dresult = new double[data.length / 2];
            for (int i = 0; i < dresult.length; i++) {
                double t2000 = data[i * 2] - 6.3113904e+10; // seconds since midnight 2000
                dresult[i] = t2000 * 1e6 + data[i * 2 + 1] / 1000000.;
            }
            
            //pop off the 2 from the end of the qube.
            int[] qube1= new int[qube.length-1];
            System.arraycopy(qube, 0, qube1, 0, qube.length - 1);

            result= ArrayDataSet.wrap( dresult, qube1, false );
            result.putProperty(QDataSet.UNITS, Units.us2000);

        }

//System.out.println( "jvmMemory (MB): "+jvmMemory(result)/1024/1024 );
        if ( varType==CDFConstants.CDF_EPOCH || varType==CDFConstants.CDF_EPOCH16 ) {
            String cdfFile= CdfJavaDataSource.openFilesRev.get(cdf);
            if ( cdfFile!=null ) {
                String uri= cdfFile + "?" + variable.getName();
                if ( recStart!=0 || recCount!=variable.getNumberOfValues() || recInterval>1 ) {
                    uri= uri + "["+recStart+":"+(recStart+recCount)+":"+recInterval+"]";
                }
                CdfJavaDataSource.dsCache.put( uri, result );
                CdfJavaDataSource.dsCacheFresh.put( uri, System.currentTimeMillis() );
            }
        }
        return result;
    }
    

    /**
     * returns the amount of JVM memory occupied by the dataset. (Approx.)
     * @param ds
     * @return
     */
    public static int jvmMemory( QDataSet ds ) {
        if ( ds instanceof ArrayDataSet ) {
            return ((ArrayDataSet)ds).jvmMemory();
        } else if ( ds instanceof TrArrayDataSet ) {
            return ((TrArrayDataSet)ds).jvmMemory();
        } else if ( ds instanceof Slice0DataSet ) {
            return 0; // TODO: not worth chasing after
        } else {
            throw new IllegalArgumentException("not supported type of QDataSet: "+ds);
        }
    }

    public static Map<String, String> getPlottable(CDF cdf, boolean dataOnly, int rankLimit) throws Exception {
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
            case 51 :return "CDF_CHAR";
            default: return String.valueOf(type);
        }
    }

    /**
     * returns null or the attribute.
     * @param cdf
     * @param var
     * @param attrname
     * @return
     */
    private static Object getAttribute( CDF cdf, String var, String attrname ) {
        Object att= cdf.getAttribute( var,attrname );
        if ( att==null ) return null;
        if ( ((Vector)att).size()==0 ) return null;
        att= ((Vector)att).get(0);
        return att;
    }


    public static boolean hasAttribute( CDF cdf, String var, String attrname ) {
        Object att= cdf.getAttribute( var,attrname );
        return !( att==null );
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
    public static Map<String, String> getPlottable(CDF cdf, boolean dataOnly, int rankLimit, boolean deep, boolean master) throws Exception {

        Map<String, String> result = new LinkedHashMap<String, String>();
        Map<String, String> dependent= new LinkedHashMap<String, String>();

        boolean isMaster= master; //cdf.getName().contains("MASTERS"); // don't show of Epoch=0, just "Epoch"

        logger.fine("getting CDF variables");
        String[] v = cdf.getVariableNames();
        logger.log(Level.FINE, "got {0} variables", v.length);

        Attribute virtual= null;

        logger.fine("getting CDF attributes");
        try {
            virtual= (Attribute) cdf.getAttribute("VIRTUAL");
        } catch (Exception e) {
        }

        int skipCount=0;
        for (int i=0; i<v.length; i++ ) {
            Variable var = cdf.getVariable(v[i]);
            if ( dataOnly ) {
               Object varType= getAttribute( cdf, var.getName(), "VAR_TYPE" );
               if ( varType==null || !varType.equals("data") ) {
                   skipCount++;
               }
            }
        }
        if ( skipCount==v.length ) {
            System.err.println( "turning off dataOnly because it rejects everything");
            dataOnly= false;
        }

        for (int i = 0; i < v.length; i++) {
            String vdescr=null;

            Variable var = cdf.getVariable(v[i]);
            if (var.getType() == CDFConstants.CDF_CHAR || var.getType()==CDFConstants.CDF_UCHAR ) {
                continue;
            }

            List<String> warn= new ArrayList();

            long maxRec = var.getNumberOfValues(); 

            int rank;
            int[] dims = var.getDimensions();

            boolean[] dimVary= var.getVarys();

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

            if ( var.getName().equals("Time_PB5") ) {
                logger.log(Level.FINE, "skipping {0} because we always skip Time_PB5", var.getName());
                continue;
            }

            if ( false ) {
                result.put(var.getName(), null);
            } else {

                if ( dataOnly ) {
                    Object varType= getAttribute( cdf, var.getName(), "VAR_TYPE" );
                    if ( varType==null || !varType.equals("data") ) {
                        continue;
                    }
                }

                Variable xDependVariable = null;
                long xMaxRec = -1;
                Variable yDependVariable = null;
                long yNumRec = -1;
                Variable zDependVariable = null;
                long zNumRec = -1;
                Variable z1DependVariable = null;
                long z1NumRec = -1;
                String scatDesc = null;
                String svarNotes = null;
                try {
                    if ( true || virtual!=null ) {
                        Object att= getAttribute( cdf, var.getName(), "VIRTUAL" );
                        if ( att!=null ) {
                            logger.log(Level.FINE, "get attribute VIRTUAL entry for {0}", var.getName());
                            if ( String.valueOf(att).toUpperCase().equals("TRUE") ) {
                                String funct= (String)getAttribute( cdf, var.getName(), "FUNCTION" );
                                if ( funct==null ) funct= (String) getAttribute( cdf, var.getName(), "FUNCT" ) ; // in alternate_view in IDL: 11/5/04 - TJK - had to change FUNCTION to FUNCT for IDL6.* compatibili
                                if ( !CdfVirtualVars.isSupported(funct) ) {
                                    if ( !funct.startsWith("comp_themis") ) {
                                        System.err.println("virtual function not supported: "+funct );
                                    }
                                    continue;
                                } else {
                                    vdescr= funct + "( ";
                                    int icomp=0;
                                    String comp= (String)getAttribute( cdf, var.getName(), "COMPONENT_"+icomp );
                                    if ( comp!=null ) {
                                        vdescr= vdescr + comp;
                                        icomp++;
                                    }
                                    for ( ; icomp<5; icomp++ ) {
                                        comp= (String)getAttribute( cdf, var.getName(), "COMPONENT_"+icomp );
                                        if ( comp!=null ) {
                                            vdescr= vdescr+", "+comp;
                                        } else {
                                            break;
                                        }
                                    }
                                    vdescr= vdescr+" )";
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    //e.printStackTrace();
                }
                try {
                    if ( hasAttribute( cdf, var.getName(), "DEPEND_0" )) {  // check for metadata for DEPEND_0
                        Object att= getAttribute( cdf, var.getName(), "DEPEND_0" );
                        if ( att!=null ) {
                            logger.log(Level.FINE, "get attribute DEPEND_0 entry for {0}", var.getName());
                            xDependVariable = cdf.getVariable(String.valueOf(att));
                            xMaxRec = xDependVariable.getNumberOfValues();
                            if ( xMaxRec!=maxRec ) {
                                if ( maxRec==-1 ) maxRec+=1; //why?
                                warn.add("depend0 length is inconsistent with length ("+(maxRec)+")" );
                                //TODO: warnings are incorrect for Themis data.
                            }
                        }
                    }
                } catch (Exception e) {
                    warn.add( "problem with DEPEND_0: " + e.getMessage() );//e.printStackTrace();
                }


                try {
                    if ( hasAttribute( cdf, var.getName(), "DEPEND_1" ) ) {  // check for metadata for DEPEND_1
                        Object att= getAttribute( cdf, var.getName(), "DEPEND_1" );
                        if ( att!=null && rank>1 ) {
                            logger.log(Level.FINE, "get attribute DEPEND_1 entry for {0}", var.getName());
                            yDependVariable = cdf.getVariable(String.valueOf(att));
                            yNumRec = yDependVariable.getNumberOfValues();
                            if (yNumRec == 1) {
                                yNumRec = yDependVariable.getDimensions()[0];
                            }
                            if ( dims.length>0 && (yNumRec)!=dims[0] ) {
                                warn.add("depend1 length is inconsistent with length ("+dims[0]+")" );
                            }
                        }
                    }
                } catch (Exception e) {
                    warn.add( "problem with DEPEND_1: " + e.getMessage() );//e.printStackTrace();
                }

                try {
                    if ( yNumRec==-1 && hasAttribute( cdf, var.getName(), "LABL_PTR_1" ) ) {  // check for metadata for LABL_PTR_1
                        Object att= getAttribute( cdf, var.getName(), "LABL_PTR_1" );
                        if ( att!=null && rank>1  ) {
                            logger.log(Level.FINE, "get attribute LABL_PTR_1 entry for {0}", var.getName());
                            yDependVariable = cdf.getVariable(String.valueOf(att));
                            yNumRec = yDependVariable.getNumberOfValues();
                            if (yNumRec == 1) {
                                yNumRec = yDependVariable.getDimensions()[0];
                            }
                            if ( dims.length>0 && (yNumRec)!=dims[0] ) {
                                warn.add("LABL_PTR_1 length is inconsistent with length ("+dims[0]+")" );
                            }
                        }
                    }
                } catch (Exception e) {
                    warn.add( "problem with LABL_PTR_1: " + e.getMessage() );//e.printStackTrace();
                }

                try {
                    if ( hasAttribute( cdf, var.getName(), "DEPEND_2" )  ) {
                        Object att= getAttribute( cdf, var.getName(), "DEPEND_2" );
                        if ( att!=null && rank>2 ) {
                            logger.log(Level.FINE, "get attribute DEPEND_2 entry for {0}", var.getName());
                            zDependVariable = cdf.getVariable(String.valueOf(att));
                            zNumRec = zDependVariable.getNumberOfValues();
                            if (zNumRec == 1) {
                                zNumRec = zDependVariable.getDimensions()[0];
                            }
                        }
                    }
                } catch (Exception e) {
                    warn.add( "problem with DEPEND_2: " + e.getMessage() );//e.printStackTrace();
                }

                try {
                    if ( zNumRec==-1 && hasAttribute( cdf, var.getName(), "LABL_PTR_2" ) ) {  // check for metadata for LABL_PTR_1
                        Object att= getAttribute( cdf, var.getName(), "LABL_PTR_2" );
                        if ( att!=null && rank>2 ) {
                            logger.log(Level.FINE, "get attribute LABL_PTR_2 entry for {0}", var.getName());
                            zDependVariable = cdf.getVariable(String.valueOf(att));
                            zNumRec = zDependVariable.getNumberOfValues();
                            if (zNumRec == 0) {
                                zNumRec = zDependVariable.getDimensions()[0];
                            }
                        }
                    }
                } catch (Exception e) {
                    warn.add( "problem with LABL_PTR_2: " + e.getMessage() );//e.printStackTrace();
                }
                try {
                    if ( hasAttribute( cdf, var.getName(), "DEPEND_3" ) ) {
                        Object att= getAttribute( cdf, var.getName(), "DEPEND_3" );
                        if ( att!=null && rank>3 ) {
                            logger.log(Level.FINE, "get attribute DEPEND_3 entry for {0}", var.getName());
                            z1DependVariable = cdf.getVariable(String.valueOf(att));
                            z1NumRec = z1DependVariable.getNumberOfValues();
                            if (z1NumRec == 1) {
                                z1NumRec = z1DependVariable.getDimensions()[0]; //TODO: check
                            }
                        }
                    }
                } catch (Exception e) {
                    warn.add( "problem with DEPEND_3: " + e.getMessage() );//e.printStackTrace();
                }

                try {
                    if ( z1NumRec==-1 && hasAttribute( cdf, var.getName(), "LABL_PTR_3" ) ) {  // check for metadata for LABL_PTR_1
                        Object att= getAttribute( cdf, var.getName(), "LABL_PTR_3" );
                        if ( att!=null && rank>3 ) {
                            logger.log(Level.FINE, "get attribute LABL_PTR_3 entry for {0}", var.getName());
                            zDependVariable = cdf.getVariable(String.valueOf(att));
                            z1NumRec = zDependVariable.getNumberOfValues();
                            if (z1NumRec == 0) {
                                z1NumRec = zDependVariable.getDimensions()[0];
                            }
                        }
                    }
                } catch (Exception e) {
                    warn.add( "problem with LABL_PTR_3: " + e.getMessage() );//e.printStackTrace();
                }

                if (deep) {
                    Object o= (Object) getAttribute( cdf, var.getName(), "CATDESC" );
                    if ( o != null && o instanceof String ) {
                        logger.log(Level.FINE, "get attribute CATDESC entry for {0}", var.getName());
                        scatDesc = (String)o ;
                    }
                    o=  getAttribute( cdf, var.getName(), "VAR_NOTES" );
                    if ( o!=null  && o instanceof String ) {
                        logger.log(Level.FINE, "get attribute VAR_NOTES entry for {0}", var.getName());
                        svarNotes = (String)o ;
                    }
                }

                String desc = var.getName();
                if (xDependVariable != null) {
                    desc += "(" + xDependVariable.getName();
                    if ( xMaxRec>0 || !isMaster ) { // small kludge for CDAWeb, where we expect masters to be empty.
                         desc+= "=" + (xMaxRec);
                    }
                    if (yDependVariable != null) {
                        desc += "," + yDependVariable.getName() + "=" + (yNumRec);
                        if (zDependVariable != null) {
                            desc += "," + zDependVariable.getName() + "=" + (zNumRec);
                            if (z1DependVariable != null) {
                                desc += "," + z1DependVariable.getName() + "=" + (z1NumRec);
                            }
                        }
                    } else if ( rank>1 ) {
                        desc += ","+DataSourceUtil.strjoin( dims, ",");
                    }
                    desc += ")";
                }

                if (deep) {
                    StringBuilder descbuf = new StringBuilder("<html><b>" + desc + "</b><br>");

                    String recDesc= ""+ CdfUtil.getStringDataType(var.getType());
                    if ( dims!=null ) {
                        recDesc= recDesc+"["+ DataSourceUtil.strjoin( dims, ",") + "]";
                    }
                    if (maxRec != xMaxRec)
                        descbuf.append("").append(maxRec).append(" records of ").append(recDesc).append("<br>");
                    if (scatDesc != null)
                        descbuf.append("").append(scatDesc).append("<br>");
                    if (svarNotes !=null ) {
                        descbuf.append("<br><p><small>").append(svarNotes).append("<small></p>");
                    }

                    if ( vdescr!=null && vdescr.length()>0 ) {
                        descbuf.append("<br>virtual variable implemented by ").append(vdescr).append("<br>");
                    }

                    for ( String s: warn ) {
                        descbuf.append("<br>WARNING: ").append(s);
                    }
                    
                    descbuf.append("</html>");
                    if ( xDependVariable!=null ) {
                        dependent.put(var.getName(), descbuf.toString());
                    } else {
                        result.put(var.getName(), descbuf.toString());
                    }
                } else {
                    if ( xDependVariable!=null ) {
                        dependent.put(var.getName(), desc);
                    } else {
                        result.put(var.getName(), desc);
                    }
                }

            }
        } // for

        logger.fine("done, get plottable ");

        dependent.putAll(result);

        return dependent;
    }

}
