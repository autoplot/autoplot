/*
 * CdfUtil.java
 *
 * Created on July 24, 2007, 12:56 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.cdfdatasource;

import org.das2.datum.DatumRange;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
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
import java.util.logging.Logger;
import org.virbo.dataset.AbstractDataSet;
import org.virbo.dataset.BDataSet;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.FDataSet;
import org.virbo.dataset.IDataSet;
import org.virbo.dataset.LDataSet;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.SDataSet;
import org.virbo.dataset.WritableDataSet;

/**
 * static methods supporting CdfFileDataSource
 *
 * @author jbf
 */
public class CdfUtil {

    private final static Logger logger = Logger.getLogger("virbo.cdfdatasource");

    private static void flatten(double[][] data, double[] back, int offset, int nx, int ny) {
        for (int i = 0; i < nx; i++) {
            double[] dd = data[i];
            System.arraycopy(dd, 0, back, offset + i * ny, ny);
        }
    }

    private static void flatten(float[][] data, float[] back, int offset, int nx, int ny) {
        for (int i = 0; i < nx; i++) {
            float[] dd = data[i];
            System.arraycopy(dd, 0, back, offset + i * ny, ny);
        }
    }

    private static void flatten(int[][] data, int[] back, int offset, int nx, int ny) {
        for (int i = 0; i < nx; i++) {
            int[] dd = data[i];
            System.arraycopy(dd, 0, back, offset + i * ny, ny);
        }
    }

    private static void flatten(short[][] data, short[] back, int offset, int nx, int ny) {
        for (int i = 0; i < nx; i++) {
            short[] dd = data[i];
            System.arraycopy(dd, 0, back, offset + i * ny, ny);
        }
    }

    private static void flatten(byte[][] data, byte[] back, int offset, int nx, int ny) {
        for (int i = 0; i < nx; i++) {
            byte[] dd = data[i];
            System.arraycopy(dd, 0, back, offset + i * ny, ny);
        }
    }

    private static void flatten(double[][][] data, double[] back, int offset, int nx, int ny, int nz) {
        offset = 0;
        for (int i = 0; i < nx; i++) {
            double[][] ff = data[i];
            flatten(ff, back, offset, ny, nz);
            offset += ny * nz;
        }
    }

    private static void flatten(float[][][] data, float[] back, int offset, int nx, int ny, int nz) {
        offset = 0;
        for (int i = 0; i < nx; i++) {
            float[][] ff = data[i];
            flatten(ff, back, offset, ny, nz);
            offset += ny * nz;
        }
    }

    private static void flatten(int[][][] data, int[] back, int offset, int nx, int ny, int nz) {
        offset = 0;
        for (int i = 0; i < nx; i++) {
            int[][] ff = data[i];
            flatten(ff, back, offset, ny, nz);
            offset += ny * nz;
        }
    }

    private static void flatten(short[][][] data, short[] back, int offset, int nx, int ny, int nz) {
        offset = 0;
        for (int i = 0; i < nx; i++) {
            short[][] ff = data[i];
            flatten(ff, back, offset, ny, nz);
            offset += ny * nz;
        }
    }

    private static void flatten(byte[][][] data, byte[] back, int offset, int nx, int ny, int nz) {
        offset = 0;
        for (int i = 0; i < nx; i++) {
            byte[][] ff = data[i];
            flatten(ff, back, offset, ny, nz);
            offset += ny * nz;
        }
    }

    private static void flatten(double[][][][] data, double[] back, int offset, int nx, int ny, int nz, int nzz ) {
        offset = 0;
        for (int i = 0; i < nx; i++) {
            double[][][] ff = data[i];
            flatten(ff, back, offset, ny, nz, nzz);
            offset += ny * nz;
        }
    }

    private static void flatten(float[][][][] data, float[] back, int offset, int nx, int ny, int nz, int nzz) {
        offset = 0;
        for (int i = 0; i < nx; i++) {
            float[][][] ff = data[i];
            flatten(ff, back, offset, ny, nz, nzz);
            offset += ny * nz;
        }
    }

    private static void flatten(int[][][][] data, int[] back, int offset, int nx, int ny, int nz, int nzz) {
        offset = 0;
        for (int i = 0; i < nx; i++) {
            int[][][] ff = data[i];
            flatten(ff, back, offset, ny, nz, nzz);
            offset += ny * nz;
        }
    }

    private static void flatten(short[][][][] data, short[] back, int offset, int nx, int ny, int nz, int nzz) {
        offset = 0;
        for (int i = 0; i < nx; i++) {
            short[][][] ff = data[i];
            flatten(ff, back, offset, ny, nz, nzz);
            offset += ny * nz;
        }
    }

    private static void flatten(byte[][][][] data, byte[] back, int offset, int nx, int ny, int nz, int nzz) {
        offset = 0;
        for (int i = 0; i < nx; i++) {
            byte[][][] ff = data[i];
            flatten(ff, back, offset, ny, nz, nzz);
            offset += ny * nz;
        }
    }

    private static WritableDataSet wrapRank2(long varType, Object odata, Variable variable) throws RuntimeException {
        WritableDataSet result;
        if (varType == Variable.CDF_REAL4 || varType == Variable.CDF_FLOAT) {
            float[][] data = (float[][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            float[] back = new float[nx * ny];
            flatten(data, back, 0, nx, ny);
            result = FDataSet.wrap(back, nx, ny);
        } else if (varType == Variable.CDF_REAL8 || varType == Variable.CDF_DOUBLE) {
            double[][] data = (double[][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            double[] back = new double[nx * ny];
            flatten(data, back, 0, nx, ny);
            result = DDataSet.wrap(back, nx, ny);
        } else if (varType == Variable.CDF_EPOCH) {
            double[] data = (double[]) odata; // kludge for CAA, which returns [1,900]
            result = DDataSet.wrap(data);
        } else if (varType == Variable.CDF_INT4 || varType == Variable.CDF_UINT4) {
            int[][] data = (int[][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int[] back = new int[nx * ny];
            flatten(data, back, 0, nx, ny);
            result = IDataSet.wrap(back, nx, ny);
        } else if (varType == Variable.CDF_INT2 || varType == Variable.CDF_UINT2 || varType == Variable.CDF_UINT1) {
            short[][] data = (short[][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            short[] back = new short[nx * ny];
            flatten(data, back, 0, nx, ny);
            result = SDataSet.wrap(back, nx, ny);
        } else if (varType == Variable.CDF_INT1 || varType == Variable.CDF_BYTE) {
            byte[][] data = (byte[][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            byte[] back = new byte[nx * ny];
            flatten(data, back, 0, nx, ny);
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
        return result;
    }

    private static WritableDataSet wrapRank3(long varType, Object odata, Variable variable) throws RuntimeException {
        WritableDataSet result;
        if (varType == Variable.CDF_REAL8 || varType == Variable.CDF_DOUBLE) {
            double[][][] data = (double[][][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int nz = data[0][0].length;
            double[] back = new double[nx * ny * nz];
            flatten(data, back, 0, nx, ny, nz);
            result = DDataSet.wrap(back, new int[]{nx, ny, nz});
        } else if (varType == Variable.CDF_REAL4 || varType == Variable.CDF_FLOAT) {
            float[][][] data = (float[][][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int nz = data[0][0].length;
            float[] back = new float[nx * ny * nz];
            flatten(data, back, 0, nx, ny, nz);
            result = FDataSet.wrap(back, nx, ny, nz);
        } else if (varType == Variable.CDF_INT4 || varType == Variable.CDF_UINT4) {
            int[][][] data = (int[][][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int nz = data[0][0].length;
            int[] back = new int[nx * ny * nz];
            flatten(data, back, 0, nx, ny, nz);
            result = IDataSet.wrap(back, nx, ny, nz);
        } else if (varType == Variable.CDF_INT2 || varType == Variable.CDF_UINT2 || varType == Variable.CDF_UINT1) {
            short[][][] data = (short[][][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int nz = data[0][0].length;
            short[] back = new short[nx * ny * nz];
            flatten(data, back, 0, nx, ny, nz);
            result = SDataSet.wrap(back, nx, ny, nz);
        } else if (varType == Variable.CDF_INT1 || varType == Variable.CDF_BYTE) {
            byte[][][] data = (byte[][][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int nz = data[0][0].length;
            byte[] back = new byte[nx * ny * nz];
            flatten(data, back, 0, nx, ny, nz);
            result = BDataSet.wrap(back, nx, ny, nz);

        } else {
            throw new RuntimeException("Unsupported Data Type " + variable.getDataType() + " java type " + odata.getClass());
        }
        return result;
    }

    private static WritableDataSet wrapRank4(long varType, Object odata, Variable variable) throws RuntimeException {
        WritableDataSet result;
        if (varType == Variable.CDF_REAL8 || varType == Variable.CDF_DOUBLE) {
            double[][][][] data = (double[][][][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int nz = data[0][0].length;
            int nzz = data[0][0][0].length;
            double[] back = new double[nx * ny * nz * nzz ];
            flatten(data, back, 0, nx, ny, nz, nzz );
            result = DDataSet.wrap(back, new int[]{nx, ny, nz, nzz});
        } else if (varType == Variable.CDF_REAL4 || varType == Variable.CDF_FLOAT) {
            float[][][][] data = (float[][][][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int nz = data[0][0].length;
            int nzz = data[0][0][0].length;
            float[] back = new float[nx * ny * nz * nzz];
            flatten(data, back, 0, nx, ny, nz, nzz );
            result = FDataSet.wrap(back, nx, ny, nz, nzz);
        } else if (varType == Variable.CDF_INT4 || varType == Variable.CDF_UINT4) {
            int[][][][] data = (int[][][][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int nz = data[0][0].length;
            int nzz = data[0][0][0].length;
            int[] back = new int[nx * ny * nz * nzz];
            flatten(data, back, 0, nx, ny, nz, nzz);
            result = IDataSet.wrap(back, nx, ny, nz, nzz);
        } else if (varType == Variable.CDF_INT2 || varType == Variable.CDF_UINT2 || varType == Variable.CDF_UINT1) {
            short[][][][] data = (short[][][][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int nz = data[0][0].length;
            int nzz = data[0][0][0].length;
            short[] back = new short[nx * ny * nz * nzz ];
            flatten(data, back, 0, nx, ny, nz, nzz);
            result = SDataSet.wrap(back, nx, ny, nz, nzz);
        } else if (varType == Variable.CDF_INT1 || varType == Variable.CDF_BYTE) {
            byte[][][][] data = (byte[][][][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int nz = data[0][0].length;
            int nzz = data[0][0][0].length;
            byte[] back = new byte[nx * ny * nz * nzz];
            flatten(data, back, 0, nx, ny, nz, nzz);
            result = BDataSet.wrap(back, nx, ny, nz, nzz);

        } else {
            throw new RuntimeException("Unsupported Data Type " + variable.getDataType() + " java type " + odata.getClass());
        }
        return result;
    }

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

        if (varType == Variable.CDF_EPOCH) {
            result = DDataSet.wrap(new double[]{(Double) o});
            result.putProperty(QDataSet.UNITS, Units.cdfEpoch);
            result.putProperty(QDataSet.VALID_MIN, 1.); // kludge for Timas, which has zeros.

        } else if (varType == Variable.CDF_EPOCH16) {
            throw new UnsupportedOperationException("single Epoch16 not supported, send us the file");

        } else if (o instanceof Number) {
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
     * @deprecated use 4 argument wrapCdfHyperData that takes interval.
     * @param reccount reccount -1 indicates read the one and only record and do a reform.
     */
    public static WritableDataSet wrapCdfHyperData(Variable variable, long recStart, long recCount) throws CDFException {
        return wrapCdfHyperData(variable, recStart, recCount, 1);
    }

    /**
     * wraps response from CDFVariable.getHyperData() into QDataSet.  The response object
     * should be float[], float[][], double[], double[][], etc.  recStart, recCount, recInterval
     * control subsetting in the zeroth dimension.
     * @param reccount reccount -1 indicates read the one and only record and do a reform.
     */
    public static WritableDataSet wrapCdfHyperData(Variable variable, long recStart, long recCount, long recInterval) throws CDFException {
        long varType = variable.getDataType();
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

        Object odata = variable.getHyperData(recStart, Math.max(1, recCount), recInterval, dimIndeces, dimCounts, dimIntervals);
        //Object odata= variable.getHyperData( 0, 1, recInterval, dimIndeces, dimCounts, dimIntervals );        

        WritableDataSet result;

        int rank = 1;

        Object element = Array.get(odata, 0);
        if (element.getClass().isArray()) {
            Object element2 = Array.get(element, 0);
            if (element2.getClass().isArray()) {
                Object element3 = Array.get(element2, 0);
                if (element3.getClass().isArray()) {
                    rank = 4;
                } else {
                    rank = 3;
                }
            } else {
                rank = 2;
            }
        }

        if (recCount == -1 && rank == 4) {
            result = wrapRank4(varType, Array.get(odata, 0), variable);
        }

        if (recCount == 1 || (recCount == -1 && rank == 2)) {
            if (!odata.getClass().isArray()) {
                return wrapSingle(varType, variable.getName(), odata);
            }
        }

        if (rank == 1) {
            if (varType == Variable.CDF_REAL4 || varType == Variable.CDF_FLOAT) {
                result = FDataSet.wrap((float[]) odata);

            } else if (varType == Variable.CDF_REAL8 || varType == Variable.CDF_DOUBLE) {
                result = DDataSet.wrap((double[]) odata);

            } else if (varType == Variable.CDF_INT4) {
                result = IDataSet.wrap((int[]) odata);

            } else if (varType == Variable.CDF_UINT4) {
                result = LDataSet.wrap((long[]) odata);

            } else if (varType == Variable.CDF_INT2 || varType == Variable.CDF_UINT2 || varType == Variable.CDF_UINT1) {
                result = SDataSet.wrap((short[]) odata);

            } else if (varType == Variable.CDF_INT1 || varType == Variable.CDF_BYTE) {
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

            } else if (varType == Variable.CDF_EPOCH) {
                result = DDataSet.wrap((double[]) odata);
                result.putProperty(QDataSet.UNITS, Units.cdfEpoch);
                result.putProperty(QDataSet.VALID_MIN, 1.); // kludge for Timas, which has zeros.

            } else if (varType == Variable.CDF_EPOCH16) {
                // adapt to das2 by translating to Units.us2000, which should be good enough.
                // note when this is not good enough, new units types can be introduced, along with conversions.
                double[] data = (double[]) odata;
                double[] dresult = new double[data.length / 2];
                for (int i = 0; i < dresult.length; i++) {
                    double t2000 = data[i * 2] - 6.3113904e+10; // seconds since midnight 2000
                    dresult[i] = t2000 * 1e6 + data[i * 2 + 1] / 1000000.;
                }
                result = DDataSet.wrap(dresult);
                result.putProperty(QDataSet.UNITS, Units.us2000);

            } else {

                throw new RuntimeException("Unsupported Data Type " + variable.getDataType() + " java type " + odata.getClass());
            }

        } else if (rank == 2) {
            result = wrapRank2(varType, odata, variable);

        } else if (rank == 3) {
            result = wrapRank3(varType, odata, variable);

        } else {
            result = leafSlice(varType, odata, 0);
        }

        return result;
    }

    private static int[] qubeDims(Object odata) {
        List<Integer> result = new ArrayList();
        result.add(Array.getLength(odata));
        odata = Array.get(odata, 0);
        while (odata.getClass().isArray()) {
            result.add(Array.getLength(odata));
            odata = Array.get(odata, 0);
        }
        int[] iresult = new int[result.size()];
        for (int i = 0; i < result.size(); i++) {
            iresult[i] = result.get(i);
        }
        return iresult;
    }

    private static WritableDataSet leafSlice(long varType, Object odata, int idx) {
        int[] qubeDims = qubeDims(odata);
        int[] newQubeDims = DataSetOps.removeElement(qubeDims, qubeDims.length - 1);
        int nele = newQubeDims[0];
        int skip = qubeDims[newQubeDims.length];

        for (int i = 1; i < newQubeDims.length; i++) {
            nele *= newQubeDims[i];
        }

        if (varType == Variable.CDF_REAL4 || varType == Variable.CDF_FLOAT) {
            float[][][][] fdata = ((float[][][][]) odata);
            float[] result = new float[nele];
            int iele = 0;
            for (int i0 = 0; i0 < qubeDims[0]; i0++) {
                for (int i1 = 0; i1 < qubeDims[1]; i1++) {
                    for (int i2 = 0; i2 < qubeDims[2]; i2++) {
                        result[iele++] = fdata[i0][i1][i2][idx];
                    }
                }
            }
            return FDataSet.wrap((float[]) result, newQubeDims);

        } else if (varType == Variable.CDF_REAL8 || varType == Variable.CDF_DOUBLE) {
            double[][][][] fdata = ((double[][][][]) odata);
            double[] result = new double[nele];
            int iele = 0;
            for (int i0 = 0; i0 < qubeDims[0]; i0++) {
                for (int i1 = 0; i1 < qubeDims[1]; i1++) {
                    for (int i2 = 0; i2 < qubeDims[2]; i2++) {
                        result[iele++] = fdata[i0][i1][i2][idx];
                    }
                }
            }
            return DDataSet.wrap((double[]) result, newQubeDims);

        } else if (varType == Variable.CDF_UINT4) {
            long[][][][] fdata = ((long[][][][]) odata);
            long[] result = new long[nele];
            int iele = 0;
            for (int i0 = 0; i0 < qubeDims[0]; i0++) {
                for (int i1 = 0; i1 < qubeDims[1]; i1++) {
                    for (int i2 = 0; i2 < qubeDims[2]; i2++) {
                        result[iele++] = fdata[i0][i1][i2][idx];
                    }
                }
            }
            return LDataSet.wrap((long[]) result, newQubeDims);

        } else if (varType == Variable.CDF_INT4 || varType == Variable.CDF_UINT2) {
            int[][][][] fdata = ((int[][][][]) odata);
            int[] result = new int[nele];
            int iele = 0;
            for (int i0 = 0; i0 < qubeDims[0]; i0++) {
                for (int i1 = 0; i1 < qubeDims[1]; i1++) {
                    for (int i2 = 0; i2 < qubeDims[2]; i2++) {
                        result[iele++] = fdata[i0][i1][i2][idx];
                    }
                }
            }
            return IDataSet.wrap((int[]) result, newQubeDims);

        } else if (varType == Variable.CDF_INT2 || varType == Variable.CDF_UINT2 || varType == Variable.CDF_UINT1) {
            short[][][][] fdata = ((short[][][][]) odata);
            short[] result = new short[nele];
            short iele = 0;
            for (short i0 = 0; i0 < qubeDims[0]; i0++) {
                for (short i1 = 0; i1 < qubeDims[1]; i1++) {
                    for (short i2 = 0; i2 < qubeDims[2]; i2++) {
                        result[iele++] = fdata[i0][i1][i2][idx];
                    }
                }
            }
            return SDataSet.wrap((short[]) result, newQubeDims);

        } else if (varType == Variable.CDF_INT1 || varType == Variable.CDF_BYTE) {
            byte[][][][] fdata = ((byte[][][][]) odata);
            byte[] result = new byte[nele];
            byte iele = 0;
            for (byte i0 = 0; i0 < qubeDims[0]; i0++) {
                for (byte i1 = 0; i1 < qubeDims[1]; i1++) {
                    for (byte i2 = 0; i2 < qubeDims[2]; i2++) {
                        result[iele++] = fdata[i0][i1][i2][idx];
                    }
                }
            }
            return BDataSet.wrap((byte[]) result, newQubeDims);

        } else {
            throw new IllegalArgumentException("bad type");
        }
    }

    public static Map<String, String> getPlottable(CDF cdf, boolean dataOnly, int rankLimit) throws CDFException {
        return getPlottable(cdf, dataOnly, rankLimit, false);
    }

    /**
     * keys are the names of the variables. values are descriptions.
     * @param cdf
     * @param dataOnly
     * @param rankLimit
     * @return map of parameter name to short description
     * @throws gsfc.nssdc.cdf.CDFException
     */
    public static Map<String, String> getPlottable(CDF cdf, boolean dataOnly, int rankLimit, boolean deep) throws CDFException {

        Map<String, String> result = new LinkedHashMap<String, String>();

        logger.fine("getting CDF variables");
        Vector v = cdf.getVariables();
        logger.fine("got " + v.size() + " variables");

        Attribute aAttr = null, bAttr = null, cAttr = null, dAttr = null;

        Attribute catDesc = null;

        logger.fine("getting CDF attributes");
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
        try {
            dAttr = cdf.getAttribute("DEPEND_3");  // check for too many dimensions
        } catch (CDFException e) {
        }
        try {
            catDesc = cdf.getAttribute("CATDESC");
        } catch (CDFException e) {
        }

        for (int i = 0; i < v.size(); i++) {
            Variable var = (Variable) v.get(i);
            if (var.getDataType() == Variable.CDF_CHAR) {
                continue;
            }
            long maxRec = var.getMaxWrittenRecord();

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

                try {
                    if (aAttr != null) {  // check for metadata for DEPEND_1
                        logger.fine("get attribute " + aAttr.getName() + " entry for " + var.getName());
                        Entry xEntry = aAttr.getEntry(var);
                        xDependVariable = cdf.getVariable(String.valueOf(xEntry.getData()));
                        xMaxRec = xDependVariable.getMaxWrittenRecord();
                    }
                } catch (CDFException e) {
                    //e.printStackTrace();
                }


                try {
                    if (bAttr != null) {  // check for metadata for DEPEND_1
                        logger.fine("get attribute " + bAttr.getName() + " entry for " + var.getName());
                        Entry yEntry = bAttr.getEntry(var);
                        yDependVariable = cdf.getVariable(String.valueOf(yEntry.getData()));
                        yMaxRec = yDependVariable.getMaxWrittenRecord();
                        if (yMaxRec == 0) {
                            yMaxRec = yDependVariable.getDimSizes()[0] - 1;
                        }
                    }
                } catch (CDFException e) {
                    //e.printStackTrace();
                }


                try {
                    if (cAttr != null) { // check for existence of DEPEND_2, dimensionality too high
                        logger.fine("get attribute " + cAttr.getName() + " entry for " + var.getName());
                        Entry zEntry = cAttr.getEntry(var);
                        zDependVariable = cdf.getVariable(String.valueOf(zEntry.getData()));
                        zMaxRec = zDependVariable.getMaxWrittenRecord();
                        if (zMaxRec == 0) {
                            zMaxRec = zDependVariable.getDimSizes()[0] - 1;
                        }
                    }
                } catch (CDFException e) {
                    //e.printStackTrace();
                }

                try {
                    if (dAttr != null) { // check for existence of DEPEND_2, dimensionality too high
                        logger.fine("get attribute " + dAttr.getName() + " entry for " + var.getName());
                        Entry zEntry = dAttr.getEntry(var);
                        z1DependVariable = cdf.getVariable(String.valueOf(zEntry.getData()));
                        z1MaxRec = z1DependVariable.getMaxWrittenRecord();
                        if (z1MaxRec == 0) {
                            z1MaxRec = z1DependVariable.getDimSizes()[0] - 1;
                        }
                    }
                } catch (CDFException e) {
                    //e.printStackTrace();
                }

                if (deep) {
                    try {
                        if (catDesc != null) {
                            logger.fine("get attribute " + catDesc.getName() + " entry for " + var.getName());
                            Entry entry = catDesc.getEntry(var);
                            scatDesc = String.valueOf(entry.getData());
                        }
                    } catch (CDFException e) {
                        //e.printStackTrace();
                    }
                }

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
                    }
                    desc += ")";
                }
                if (deep) {
                    StringBuffer descbuf = new StringBuffer("<html><b>" + desc + "</b><br>");
                    if (maxRec != xMaxRec)
                        descbuf.append("" + (maxRec + 1) + " records<br>");
                    if (scatDesc != null)
                        descbuf.append("" + scatDesc + "<br>");
                    descbuf.append("</html>");
                    result.put(var.getName(), descbuf.toString());
                } else {
                    result.put(var.getName(), desc);
                }

            }
        } // for

        logger.fine("done, get plottable ");
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
