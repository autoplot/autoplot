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
import gov.nasa.gsfc.voyager.cdf.Extractor;
import gov.nasa.gsfc.voyager.cdf.Variable;
import org.das2.datum.DatumRange;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import java.io.File;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Logger;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.binarydatasource.BufferDataSet;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.WritableDataSet;
import org.virbo.datasource.DataSourceUtil;

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

    public static MutablePropertyDataSet wrapCdfHyperDataHacked(
            CDF cdf, Variable variable, long recStart, long recCount, long recInterval ) throws Exception {
        return wrapCdfHyperDataHacked( cdf, variable, recStart, recCount, recInterval, new NullProgressMonitor() );
    }
    
    public static MutablePropertyDataSet wrapCdfHyperDataHacked(
            CDF cdf, Variable variable, long recStart, long recCount, long recInterval, ProgressMonitor mon ) throws Exception {

        if ( mon==null ) mon= new org.das2.util.monitor.NullProgressMonitor();
        
        long varType = variable.getType();
        long[] dimIndeces = new long[]{0};

        int[] dimSizes = variable.getDimensions();
        boolean[] dimVaries= variable.getVarys();
        
        int dims;
        if (dimSizes == null) {
            dims = 0;
        } else {
            dims = dimSizes.length;
        }

        long[] dimCounts;
        long[] dimIntervals;

        if (dims == 0) {
            dimCounts = new long[]{0};
            dimIntervals = new long[]{0};
        } else if (dims == 1) {
            dimCounts = new long[]{dimSizes[0]};
            dimIntervals = new long[]{1};
        } else if (dims == 2) {
            dimIndeces = new long[]{0, 0};
            dimCounts = new long[]{dimSizes[0], dimSizes[1]};
            dimIntervals = new long[]{1, 1};
        } else if (dims == 3) {
            dimIndeces = new long[]{0, 0, 0};
            dimCounts = new long[]{dimSizes[0], dimSizes[1], dimSizes[2]};
            dimIntervals = new long[]{1, 1, 1};
        } else {
            if (recCount != -1) {
                throw new IllegalArgumentException("rank 5 not implemented");
            } else {
                dimCounts = new long[]{dimSizes[0]};
                dimIntervals = new long[]{1};
            }
        }

        int varRecCount= variable.getNumberOfValues();
        if ( recCount==-1 && recStart>0 && varRecCount==1 ) { // another kludge for Rockets, where depend was assigned variance
            recStart= 0;
        }

        //CDFData cdfData= CDFData.get( variable, recStart, Math.max(1, recCount), recInterval, dimIndeces, dimCounts, dimIntervals, false );
        Object odata=null;
        ByteBuffer buf=null;

        long rc= recCount;
        if ( rc==-1 ) rc= 1;  // -1 is used as a flag for a slice, we still really read one record.

        try {
            int[] stride= new int[dims+1];
            for ( int i=0; i<dims+1; i++ ) stride[i]= 1;
            stride[0]= (int)recInterval;
            //if ( recInterval>1 ) throw new IllegalArgumentException("stride not supported with this cdf reader");
            if ( recStart==0 && ( recCount==-1 || recCount==varRecCount ) && recInterval==1 ) {
                if ( false && ( variable.getType()==44 || variable.getType()==21 ) ) {
                    buf= Extractor.get1DSeriesNio( cdf, variable, null);
                    //odata= cdf.get1D( variable.getName() );
                } else {
                    odata= cdf.get1D( variable.getName() ); // this is my hack
                }
            } else {
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

        if ( ( odata==null && buf==null ) && varType != CDFConstants.CDF_CHAR ) {
            System.err.println("something went wrong");
            throw new NullPointerException("something went wrong");
        }

        MutablePropertyDataSet result;

        if ( dims==0 ) dimSizes= new int[0]; // to simplify code

        // Nand's library
        if ( dimVaries.length>0 && dimVaries[0]==false ) {
            dimSizes= new int[0];
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
            if ( buf!=null ) {
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

                    result= BufferDataSet.makeDataSet( qube.length, reclen, 0, qube[0],
                                qube.length < 2 ? 1 : qube[1],
                                qube.length < 3 ? 1 : qube[2],
                                buf, type );
                } else {
                    throw new IllegalArgumentException("internal error unimplemented: "+variable.getType() );
                }
            } else {
                result = DDataSet.wrap((double[]) odata, qube);
            }
        } else {
            if ( recCount==-1 ) {
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
                int [] qqube= new int[qube.length+1];
                qqube[0]= 1;
                System.arraycopy(qube, 0, qqube, 1, qube.length);
                result= (MutablePropertyDataSet) TrDDataSet.wrap((double[]) odata, qqube).slice(0);
            } else {
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
                result = TrDDataSet.wrap((double[]) odata, qube);
            }
        }

        if ( varType == CDFConstants.CDF_CHAR ) {
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
                    sdata[j]= (String) Array.get(o0, j);
                }
            } else {
                throw new IllegalArgumentException("not handled single array where expected double array");
            }

            double[] back = new double[sdata.length];
            for (int i = 0; i < sdata.length; i++) {
                back[i] = units.createDatum(sdata[i]).doubleValue(units);
            }
            result = DDataSet.wrap(back, qube);
            result.putProperty(QDataSet.UNITS, units);

        } else if ( varType == CDFConstants.CDF_EPOCH ) {
            if ( qube.length==2 && qube[1]==1 ) {// kludge for c4_cp_fgm_spin_20030102_v01.cdf?B_vec_xyz_gse__C4_CP_FGM_SPIN
                qube= new int[] { qube[0] };
            }
            result = DDataSet.wrap((double[]) odata, qube);
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
            for ( int i=0; i<qube.length-1; i++ ) qube1[i]= qube[i];

            result = DDataSet.wrap(dresult, qube1);
            result.putProperty(QDataSet.UNITS, Units.us2000);

        }

//        if (varType == CDFConstants.CDF_REAL4 || varType == CDFConstants.CDF_FLOAT) {
//            result = FDataSet.wrap((float[]) odata, qube );
//
//        } else if (varType == CDFConstants.CDF_REAL8 || varType == CDFConstants.CDF_DOUBLE) {
//            result = DDataSet.wrap((double[]) odata, qube);
//
//        } else if (varType == CDFConstants.CDF_UINT4 ) {
//            result = LDataSet.wrap((long[]) odata, qube);
//
//        } else if (varType == CDFConstants.CDF_INT4 || varType == CDFConstants.CDF_UINT2) {
//            result = IDataSet.wrap((int[]) odata, qube);
//
//        } else if (varType == CDFConstants.CDF_INT2 || varType == CDFConstants.CDF_UINT1) {
//            result = SDataSet.wrap((short[]) odata, qube);
//
//        } else if (varType == CDFConstants.CDF_INT1 || varType == CDFConstants.CDF_BYTE) {
//            result = BDataSet.wrap((byte[]) odata, qube);
//
//        } else if (varType == CDFConstants.CDF_CHAR) {
//            EnumerationUnits units = EnumerationUnits.create(variable.getName());
//            String[] sdata = (String[]) odata;
//            double[] back = new double[sdata.length];
//            for (int i = 0; i < sdata.length; i++) {
//                back[i] = units.createDatum(sdata[i]).doubleValue(units);
//            }
//            result = DDataSet.wrap(back, qube);
//            result.putProperty(QDataSet.UNITS, units);
//
//        } else if (varType == CDFConstants.CDF_EPOCH) {
//            if ( qube.length==2 && qube[1]==1 ) {// kludge for c4_cp_fgm_spin_20030102_v01.cdf?B_vec_xyz_gse__C4_CP_FGM_SPIN
//                qube= new int[] { qube[0] };
//            }
//            result = DDataSet.wrap((double[]) odata, qube);
//            result.putProperty(QDataSet.UNITS, Units.cdfEpoch);
//            result.putProperty(QDataSet.VALID_MIN, 1.); // kludge for Timas, which has zeros.
//
//        } else if (varType == CDFConstants.CDF_EPOCH16) {
//            // adapt to das2 by translating to Units.us2000, which should be good enough.
//            // note when this is not good enough, new units types can be introduced, along with conversions.
//            double[] data = (double[]) odata;
//            double[] dresult = new double[data.length / 2];
//            for (int i = 0; i < dresult.length; i++) {
//                double t2000 = data[i * 2] - 6.3113904e+10; // seconds since midnight 2000
//                dresult[i] = t2000 * 1e6 + data[i * 2 + 1] / 1000000.;
//            }
//            result = DDataSet.wrap(dresult, qube);
//            result.putProperty(QDataSet.UNITS, Units.us2000);
//
//        } else {
//
//            throw new RuntimeException("Unsupported Data Type " + variable.getType() + " java type " + odata.getClass());
//        }
        return result;
    }
    


    public static Map<String, String> getPlottable(CDF cdf, boolean dataOnly, int rankLimit) throws Exception {
        return getPlottable(cdf, dataOnly, rankLimit, false);
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

    /**
     * keys are the names of the variables. values are descriptions.
     * @param cdf
     * @param dataOnly
     * @param rankLimit
     * @return map of parameter name to short description
     * @throws gsfc.nssdc.cdf.CDFException
     */
    public static Map<String, String> getPlottable(CDF cdf, boolean dataOnly, int rankLimit, boolean deep) throws Exception {

        Map<String, String> result = new LinkedHashMap<String, String>();
        Map<String, String> dependent= new LinkedHashMap<String, String>();

        logger.fine("getting CDF variables");
        String[] v = cdf.getVariableNames();
        logger.fine("got " + v.length + " variables");

        Attribute aAttr = null, bAttr = null, cAttr = null, dAttr = null;

        Attribute catDesc = null;
        Attribute varNotes= null;
        Attribute virtual= null;

        logger.fine("getting CDF attributes");
        try {
            aAttr = (Attribute) cdf.getAttribute("DEPEND_0");
        } catch (Exception ex) {
        }
        try {
            bAttr = (Attribute) cdf.getAttribute("DEPEND_1");  // check for PB5, Vectors
        } catch (Exception e) {
        }
        try {
            cAttr = (Attribute) cdf.getAttribute("DEPEND_2");  // check for too many dimensions
        } catch (Exception e) {
        }
        try {
            dAttr = (Attribute) cdf.getAttribute("DEPEND_3");  // check for too many dimensions
        } catch (Exception e) {
        }
        try {
            catDesc = (Attribute) cdf.getAttribute("CATDESC");
        } catch (Exception e) {
        }
        try {
            varNotes= (Attribute) cdf.getAttribute("VAR_NOTES");
        } catch (Exception e) {
        }
        try {
            virtual= (Attribute) cdf.getAttribute("VIRTUAL");
        } catch (Exception e) {
        }

        for (int i = 0; i < v.length; i++) {
            Variable var = cdf.getVariable(v[i]);

            if (var.getType() == CDFConstants.CDF_CHAR) {
                continue;
            }

            List<String> warn= new ArrayList();

            long maxRec = var.getNumberOfValues(); //TODO: might be off by one.

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

            if ( maxRec==0 && ( dims==null || dims.length<1 || dims[0]==1 ) ) {
                logger.fine("skipping "+var.getName()+" because maxWrittenRecord is 0");
                continue;
            }

            if ( var.getName().equals("Time_PB5") ) {
                logger.fine("skipping "+var.getName()+" because we always skip Time_PB5");
                continue;
            }

            if (!dataOnly) {
                result.put(var.getName(), null);
            } else {

                Variable xDependVariable = null;
                long xMaxRec = -1;
                Variable yDependVariable = null;
                long yMaxRec = -1;
                Variable zDependVariable = null;
                long zMaxRec = -1;
                Variable z1DependVariable = null;
                long z1MaxRec = -1;
                String scatDesc = null;
                String svarNotes = null;

                try {
                    if ( true && virtual!=null ) {
                        Object att= getAttribute( cdf, var.getName(), "VIRTUAL" );
                        if ( att!=null ) {
                            logger.fine("get attribute " + virtual.getName() + " entry for " + var.getName());
                            if ( String.valueOf(att).equals("TRUE") ) {
                                continue;
                            }
                        }
                    }
                } catch (Exception e) {
                    //e.printStackTrace();
                }
                try {
                    if (true || aAttr != null) {  // check for metadata for DEPEND_0
                        Object att= getAttribute( cdf, var.getName(), "DEPEND_0" );
                        if ( att!=null ) {
                            logger.fine("get attribute DEPEND_0 entry for " + var.getName());
                            xDependVariable = cdf.getVariable(String.valueOf(String.valueOf(att)));
                            xMaxRec = xDependVariable.getNumberOfValues();
                            if ( xMaxRec!=maxRec ) {
                                if ( maxRec==-1 ) maxRec+=1; //why?
                                warn.add("depend0 length is inconsistent with length ("+maxRec+")" );
                                //TODO: warnings are incorrect for Themis data.
                            }
                        }
                    }
                } catch (Exception e) {
                    //e.printStackTrace();
                }


                try {
                    if ( true || bAttr != null) {  // check for metadata for DEPEND_1
                        Object att= getAttribute( cdf, var.getName(), "DEPEND_1" );
                        if ( att!=null ) {
                            logger.fine("get attribute DEPEND_1 entry for " + var.getName());
                            yDependVariable = cdf.getVariable(String.valueOf(String.valueOf(att)));
                            yMaxRec = yDependVariable.getNumberOfValues();
                            if (yMaxRec == 0) {
                                yMaxRec = yDependVariable.getDimensions()[0] - 1;  //TODO: check
                            }
                            if ( dims.length>0 && (yMaxRec+1)!=dims[0] ) {
                                warn.add("depend1 length is inconsistent with length ("+dims[0]+")" );
                            }
                        }
                    }
                } catch (Exception e) {
                    //e.printStackTrace();
                }


                try {
                    if ( true || cAttr != null) { 
                        Object att= getAttribute( cdf, var.getName(), "DEPEND_2" );
                        if ( att!=null ) {
                            logger.fine("get attribute DEPEND_2 entry for " + var.getName());
                            zDependVariable = cdf.getVariable(String.valueOf(String.valueOf(att)));
                            zMaxRec = zDependVariable.getNumberOfValues();
                            if (zMaxRec == 0) {
                                zMaxRec = zDependVariable.getDimensions()[0] - 1; //TODO: check
                            }
                        }
                    }
                } catch (Exception e) {
                    //e.printStackTrace();
                }

                try {
                    if ( true || dAttr != null) {
                        Object att= getAttribute( cdf, var.getName(), "DEPEND_3" );
                        if ( att!=null ) {
                            logger.fine("get attribute DEPEND_3 entry for " + var.getName());
                            z1DependVariable = cdf.getVariable(String.valueOf(String.valueOf(att)));
                            z1MaxRec = z1DependVariable.getNumberOfValues();
                            if (z1MaxRec == 0) {
                                z1MaxRec = z1DependVariable.getDimensions()[0] - 1; //TODO: check
                            }
                        }
                    }
                } catch (Exception e) {
                    //e.printStackTrace();
                }

                /*if (deep) {
                    try {
                        if (catDesc != null) {
                            logger.fine("get attribute " + catDesc.getName() + " entry for " + var.getName());
                            Entry entry = catDesc.getEntry(var);
                            scatDesc = String.valueOf(entry.getData());
                        }
                        if (varNotes!=null ) {
                            logger.fine("get attribute " + varNotes.getName() + " entry for " + var.getName());
                            Entry entry = varNotes.getEntry(var);
                            svarNotes = String.valueOf(entry.getData());
                        }
                    } catch (CDFException e) {
                        //e.printStackTrace();
                    }
                }
                */
                String desc = "" + var.getName();
                if (xDependVariable != null) {
                    desc += "(" + xDependVariable.getName() + "=" + (xMaxRec + 1);
                    if (yDependVariable != null) {
                        desc += "," + yDependVariable.getName() + "=" + (yMaxRec + 1);
                        if (zDependVariable != null) {
                            desc += "," + zDependVariable.getName() + "=" + (zMaxRec + 1);
                            if (z1DependVariable != null) {
                                desc += "," + z1DependVariable.getName() + "=" + (z1MaxRec + 1);
                            }
                        }
                    } else if ( rank>1 ) {
                        desc += ","+DataSourceUtil.strjoin( dims, ",");
                    }
                    desc += ")";
                }

                if (deep) {
                    /*StringBuffer descbuf = new StringBuffer("<html><b>" + desc + "</b><br>");

                    StringBuffer sdims= new StringBuffer();
                    String recDesc= CDFUtils.getStringDataType(var);
                    if ( dims!=null ) {
                        recDesc= recDesc+"["+ DataSourceUtil.strjoin( dims, ",") + "]";
                    }
                    if (maxRec != xMaxRec)
                        descbuf.append("" + (maxRec + 1) + " records of "+recDesc+"<br>");
                    if (scatDesc != null)
                        descbuf.append("" + scatDesc + "<br>");
                    if (svarNotes !=null ) {
                        descbuf.append("<br><p><small>" + svarNotes + "<small></p>");
                    }

                    for ( String s: warn ) {
                        descbuf.append("<br>WARNING: "+s);
                    }
                    
                    descbuf.append("</html>");
                    if ( xDependVariable!=null ) {
                        dependent.put(var.getName(), descbuf.toString());
                    } else {
                        result.put(var.getName(), descbuf.toString());
                    }*/
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

    /**
     * condition the name so that it works with the library.  My recollection
     * is that spaces in the name cause problems, so we replace those directory
     * names with spaces with the dos xxxxxx~1 equivalent. --J Faden
     */
    public static String win95Name(File cdfFile) {
        String f = cdfFile.toString();
        f = f.replaceAll("\\\\", "/");
        String[] ss = f.split("/");
        String result = ss[0];
        for (int i = 1; i < ss.length - 1; i++) {
            if (ss[i].indexOf(" ") > -1) {
                String dosName = ss[i].substring(0, 6) + "~1";
                ss[i] = dosName;
            }
            result += "/" + ss[i];
        }
        result += "/" + ss[ss.length - 1];
        return result;
    }
}
