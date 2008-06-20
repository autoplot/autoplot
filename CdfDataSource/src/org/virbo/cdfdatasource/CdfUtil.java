/*
 * CdfUtil.java
 *
 * Created on July 24, 2007, 12:56 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.cdfdatasource;

import edu.uiowa.physics.pw.das.datum.DatumRange;
import edu.uiowa.physics.pw.das.datum.EnumerationUnits;
import edu.uiowa.physics.pw.das.datum.Units;
import gsfc.nssdc.cdf.Attribute;
import gsfc.nssdc.cdf.CDF;
import gsfc.nssdc.cdf.CDFException;
import gsfc.nssdc.cdf.Entry;
import gsfc.nssdc.cdf.Variable;
import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import org.virbo.dataset.BDataSet;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.FDataSet;
import org.virbo.dataset.IDataSet;
import org.virbo.dataset.SDataSet;
import org.virbo.dataset.WritableDataSet;
import org.virbo.datasource.DataSetURL;

/**
 * static methods supporting CdfFileDataSource
 *
 * @author jbf
 */
public class CdfUtil {

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

    private static WritableDataSet wrapSingle(long varType, String name, Object o) {
        WritableDataSet result;
        if (o instanceof Number) {
            result = DDataSet.wrap(new double[]{((Number) o).doubleValue()});

        } else if (varType == Variable.CDF_CHAR) {
            EnumerationUnits units = EnumerationUnits.create(name);
            String sdata = (String) o;
            double[] back = new double[1];
            back[0] = units.createDatum(sdata).doubleValue(units);

            result = DDataSet.wrap(back);
            result.putProperty(QDataSet.UNITS, units);
        } else {

            throw new RuntimeException("Unsupported Data Type " + varType + " java type " + o.getClass());
        }

        return result;
    }

    /**
     * wraps response from CDFVariable.getHyperData() into QDataSet.  The object
     * should be float[], float[][], double[], double[][], etc.
     * @param reccount reccount -1 indicates read the one and only record and do a reform.
     */
    public static WritableDataSet wrapCdfHyperData(Variable variable, long recStart, long recCount) throws CDFException {
        long varType = variable.getDataType();
        long recInterval = 1;
        long[] dimIndeces = new long[]{0};

        long[] dimSizes = variable.getDimSizes();
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
//        } else if ( dims==2 ) {
//            dimCounts= new long[] { dimSizes[0], dimSizes[1] };
//            dimIntervals= new long[] { 1, 1 };
        } else {
            if (recCount != -1) {
                throw new IllegalArgumentException("rank 3 not implemented");
            } else {
                dimCounts = new long[]{dimSizes[0]};
                dimIntervals = new long[]{1};
            }
        }

        Object odata = variable.getHyperData(recStart, Math.max(1, recCount), recInterval, dimIndeces, dimCounts, dimIntervals);
        //Object odata= variable.getHyperData( 0, 1, recInterval, dimIndeces, dimCounts, dimIntervals );        

        WritableDataSet result;

        int rank = 1;
        if (recCount == 1) {
            if ( ! odata.getClass().isArray() ) {
                return wrapSingle(varType, variable.getName(), odata);
            }
        }
        
        Object element = Array.get(odata, 0);
        if (element.getClass().isArray()) {
            rank = 2;
        }

        if (rank == 1) {
            if (varType == Variable.CDF_REAL4 || varType == Variable.CDF_FLOAT) {
                result = FDataSet.wrap((float[]) odata);

            } else if (varType == Variable.CDF_REAL8 || varType == Variable.CDF_DOUBLE || varType == Variable.CDF_EPOCH) {
                result = DDataSet.wrap((double[]) odata);

            } else if (varType == Variable.CDF_INT4 || varType == Variable.CDF_UINT4) {
                result = IDataSet.wrap((int[]) odata);

            } else if (varType == Variable.CDF_INT2 || varType == Variable.CDF_UINT2 || varType == Variable.CDF_UINT1) {
                result = SDataSet.wrap((short[]) odata);

            } else if (varType == Variable.CDF_INT1) {
                result = BDataSet.wrap((byte[]) odata);

            } else if (varType == Variable.CDF_CHAR) {
                EnumerationUnits units = EnumerationUnits.create(variable.getName());
                String[] sdata = (String[]) odata;
                double[] back = new double[sdata.length];
                for (int i = 0; i < sdata.length; i++) {
                    back[i] = units.createDatum(sdata[i]).doubleValue(units);
                }
                result = DDataSet.wrap(back);
                result.putProperty(QDataSet.UNITS, units);
            } else {

                throw new RuntimeException("Unsupported Data Type " + variable.getDataType() + " java type " + odata.getClass());
            }

        } else if (rank == 2) {
            if (varType == Variable.CDF_REAL4 || varType == Variable.CDF_FLOAT) {
                float[][] data = (float[][]) odata;
                int nx = data.length;
                int ny = data[0].length;
                float[] back = new float[nx * ny];
                for (int i = 0; i < nx; i++) {
                    for (int j = 0; j < ny; j++) {
                        float[] dd = data[i];
                        back[i * ny + j] = dd[j];
                    }
                }
                result = FDataSet.wrap(back, nx, ny);

            } else if (varType == Variable.CDF_REAL8 || varType == Variable.CDF_DOUBLE) {
                double[][] data = (double[][]) odata;
                int nx = data.length;
                int ny = data[0].length;
                double[] back = new double[nx * ny];
                for (int i = 0; i < nx; i++) {
                    for (int j = 0; j < ny; j++) {
                        double[] dd = data[i];
                        back[i * ny + j] = dd[j];
                    }
                }
                result = DDataSet.wrap(back, nx, ny);

            } else if (varType == Variable.CDF_EPOCH) {
                double[] data = (double[]) odata;  // kludge for CAA, which returns [1,900]
                result = DDataSet.wrap(data);

            } else if (varType == Variable.CDF_INT2 || varType == Variable.CDF_UINT2 || varType == Variable.CDF_UINT1) {
                short[][] data = (short[][]) odata;
                int nx = data.length;
                int ny = data[0].length;
                short[] back = new short[nx * ny];
                for (int i = 0; i < nx; i++) {
                    for (int j = 0; j < ny; j++) {
                        short[] dd = data[i];
                        back[i * ny + j] = dd[j];
                    }
                }
                result = SDataSet.wrap(back, nx, ny);

            } else if (varType == Variable.CDF_INT1) {
                byte[][] data = (byte[][]) odata;
                int nx = data.length;
                int ny = data[0].length;
                byte[] back = new byte[nx * ny];
                for (int i = 0; i < nx; i++) {
                    for (int j = 0; j < ny; j++) {
                        byte[] dd = data[i];
                        back[i * ny + j] = dd[j];
                    }
                }
                result = BDataSet.wrap(back, nx, ny);

            } else if (varType == Variable.CDF_CHAR) {
                EnumerationUnits units = EnumerationUnits.create(variable.getName());
                String[] sdata = (String[]) odata;
                double[] back = new double[sdata.length];
                for (int i = 0; i < sdata.length; i++) {
                    back[i] = units.createDatum(sdata[i]).doubleValue(units);
                }
                result = DDataSet.wrap(back);
                result.putProperty(QDataSet.UNITS, units);
            } else {
                throw new RuntimeException("Unsupported Data Type " + variable.getDataType() + " java type " + odata.getClass());
            }
        } else {
            throw new IllegalArgumentException("rank 3 not implemented");

        }

        if (varType == Variable.CDF_EPOCH || varType == Variable.CDF_EPOCH16) {
            result.putProperty(QDataSet.UNITS, Units.cdfEpoch);
        }

        return result;
    }

    /**
     * keys are the names of the variables. values are descriptions.
     * @param cdf
     * @param dataOnly
     * @param rankLimit
     * @return
     * @throws gsfc.nssdc.cdf.CDFException
     */
    public static Map<String,String> getPlottable(CDF cdf, boolean dataOnly, int rankLimit) throws CDFException {

        Map<String,String> result = new LinkedHashMap<String, String>();
        Vector v = cdf.getVariables();

        Attribute aAttr = null, bAttr = null, cAttr = null;

        try {
            aAttr = cdf.getAttribute("DEPEND_0");
        } catch (CDFException ex) {
        }
        try {
            bAttr = cdf.getAttribute("DEPEND_1");  // check for PB5, Vectors
        } catch (CDFException e) {
        }
        try {
            cAttr = cdf.getAttribute("DEPEND_2");  // check for too many dimensions
        } catch (CDFException e) {
        }

        for (int i = 0; i < v.size(); i++) {
            Variable var = (Variable) v.get(i);
            if (var.getDataType() == Variable.CDF_CHAR) {
                continue;
            }

            int rank;
            long[] dims = var.getDimSizes();
            if (dims == null) {
                rank = 1;
            } else {
                rank = dims.length + 1;
            }

            if (rank > rankLimit) {
                continue;
            }

            if (!dataOnly) {
                result.put( var.getName(), null );
            } else {

                Vector attr = var.getAttributes();
                
                Variable xDependVariable=null;
                Variable yDependVariable=null;
                Variable zDependVariable=null;
                
                try {
                    if (aAttr != null) {  // check for metadata for DEPEND_1
                        Entry xEntry = aAttr.getEntry(var);
                        xDependVariable = cdf.getVariable((String) xEntry.getData());
                    }
                } catch (CDFException e) {    
                }

                
                try {
                    if (bAttr != null) {  // check for metadata for DEPEND_1
                        Entry yEntry = bAttr.getEntry(var);
                        yDependVariable = cdf.getVariable((String) yEntry.getData());
                        Attribute varType = cdf.getAttribute("VAR_TYPE");
                        Entry e = varType.getEntry(yDependVariable);
                        if (e.getData().equals("metadata")) {
                            continue;
                        }
                    }
                } catch (CDFException e) {    
                }
                
                
                try {
                    if (cAttr != null) { // check for existence of DEPEND_2, dimensionality too high
                        Entry zEntry = cAttr.getEntry(var);
                        zDependVariable = cdf.getVariable((String) zEntry.getData());
                        Attribute varType = cdf.getAttribute("VAR_TYPE");
                        Entry e = varType.getEntry(zDependVariable);
                        if (e.getData().equals("metadata")) {
                            continue;
                        }
                    }
                } catch (CDFException e) {
                }

                
                String desc= "" + var.getName();
                if ( xDependVariable!=null ) {
                    desc+= "("+ xDependVariable.getName();
                    if ( yDependVariable!=null ) {
                        desc+= ","+ yDependVariable.getName();
                        if ( zDependVariable!=null ) {
                            desc+= ","+ zDependVariable.getName();
                        }
                    }
                    desc+=")";
                }
                
                        
                result.put(var.getName(),desc);

            }
        } // for

        return result;
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
