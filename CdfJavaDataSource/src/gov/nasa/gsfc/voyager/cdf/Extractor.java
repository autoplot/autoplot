package gov.nasa.gsfc.voyager.cdf;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.zip.*;
import java.lang.reflect.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Extractor {

    private static final Logger logger= Logger.getLogger( "apdss.cdfjava" );
    static int MAX_ARRAY = 3;
    static String[] functions = new String[] {"Series" , "Element",
           "Point" , "Range" , "Elements", "RangeForElements",
           "RangeForElement", "TimeSeries", "SampledTimeSeries",
           "TimeSeriesObject"};
    static Method[][][] methods =
        new Method[functions.length][MAX_ARRAY + 1][2];
    static Class[][][] args = new Class[functions.length][MAX_ARRAY + 1][];
    static {
        try {
            Class variableClass =
                Class.forName("gov.nasa.gsfc.voyager.cdf.Variable");
            Class cdfClass = Class.forName("gov.nasa.gsfc.voyager.cdf.CDF");
            Class timeSpecClass = Class.forName(
                "gov.nasa.gsfc.voyager.cdf.TimeSpec");
            int[] ia = new int[0];
            double[] da = new double[0];
            args[0][0] = new Class[] {cdfClass, variableClass};
            args[0][1] = args[0][0];
            args[0][2] = args[0][0];
            args[0][3] = args[0][0];
            args[1][0] = null;
            args[1][1] = new Class[] {cdfClass, variableClass, Integer.class};
            args[1][2] = new Class[] {cdfClass, variableClass, Integer.class,
                                      Integer.class};
            args[2][0] = new Class[] {cdfClass, variableClass, Integer.class};
            args[2][1] = args[2][0];
            args[2][2] = args[2][0];
            args[2][3] = args[2][0];

            args[3][0] = new Class[] {cdfClass, variableClass, Integer.class,
                                      Integer.class};
            args[3][1] = args[3][0];
            args[3][2] = args[3][0];
            args[4][0] = null;
            args[4][1] = new Class[] {cdfClass, variableClass, ia.getClass()};
            args[4][2] = null;
            args[5][0] = null;
            args[5][1] = new Class[] {cdfClass, variableClass, Integer.class,
                         Integer.class, ia.getClass()};
            args[5][2] = null;
            args[6][0] = null;
            args[6][1] = new Class[] {cdfClass, variableClass, Integer.class,
                         Integer.class, Integer.class};
            args[6][2] = null;
            args[7][0] = new Class[] {cdfClass, variableClass, Boolean.class,
                         da.getClass()};
            args[7][1] = new Class[] {cdfClass, variableClass, Integer.class,
                         Boolean.class, da.getClass()};
            args[7][2] = null;
            args[8][0] = new Class[] {cdfClass, variableClass, Boolean.class,
                         da.getClass(), ia.getClass()};
            args[8][1] = new Class[] {cdfClass, variableClass, Integer.class,
                         Boolean.class, da.getClass(), ia.getClass()};
            args[8][2] = null;
            args[9][0] = new Class[] {cdfClass, variableClass, Boolean.class,
                         da.getClass(), timeSpecClass};
            args[9][1] = new Class[] {cdfClass, variableClass, Integer.class,
                         Boolean.class, da.getClass(), timeSpecClass};
            args[9][2] = null;

        } catch (ClassNotFoundException ex) {
        }
    }
    public Extractor() throws NoSuchMethodException {
    }
    static {
        for (int i = 0; i < functions.length; i++) {
            for (int j = 0; j <= MAX_ARRAY; j++) {
                for (int k = 0; k < 2; k++) {
                    methods[i][j][k] = null;
                }
            }
        }
        try {
            Class cl = Class.forName("gov.nasa.gsfc.voyager.cdf.Extractor");
            for (int i = 0; i < functions.length; i++) {
                for (int j = 0; j <= MAX_ARRAY; j++) {
                    if (args[i][j] == null) continue;
                    methods[i][j][0] = cl.getMethod("get" + functions[i] + j,
                                          args[i][j]);
                }
            }
            methods[0][0][1] = cl.getMethod("getStringSeries0", args[0][0]);
            methods[0][1][1] = cl.getMethod("getStringSeries1", args[0][0]);
        } catch (ClassNotFoundException ex) {
        } catch (NoSuchMethodException ex) {
        }
    }
    public static Method getMethod(Variable var, String func) throws 
        IllegalAccessException, InvocationTargetException {
        int rank = var.getEffectiveRank();
        int index = -1;
        for (int i = 0; i < functions.length; i++) {
            if (func.equals(functions[i])) {
                index = i;
                break;
            }
        }
        if (index < 0) return null;
        if (DataTypes.isStringType(var.getType())) {
            return methods[index][rank][1];
        }
        return methods[index][rank][0];
    }

    static int[] getRecordRange(CDF thisCDF,Variable var, double[] timeRange) {
        try {
            double[] times = ((CDFImpl)thisCDF).getTimes(var, false);
            double start = timeRange[0];
            double stop = timeRange[1];
            int i = 0;
            for (; i < times.length; i++) {
                if (start > times[i]) continue;
                break;
            }
            if (i == times.length) return null;
            int low = i;
            for (; i < times.length; i++) {
                if (stop <= times[i]) break;
            }
            if (i == 0) return null;
            return new int[] {low, i - 1};
        } catch (Throwable t) {
        }
        return null;
    }

    public static Object getSeries0(CDF thisCDF, Variable var) throws 
        IllegalAccessException, InvocationTargetException {
        int numberOfValues = var.getNumberOfValues();
        if (numberOfValues == 0) return null;
        int type = var.getType();
        long[] ldata = null;
        double[] data = null;
        boolean longType = false;
        if (DataTypes.typeCategory[type] == DataTypes.LONG) {
            ldata = new long[numberOfValues];
            longType = true;
        } else {
            data = new double[numberOfValues];
        }
        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        for (int blk = 0; blk < locations.size(); blk++) {
            int [] loc = (int [])locations.elementAt(blk);
            int first = loc[0];
            int last = loc[1];
            ByteBuffer bv = positionBuffer((CDFImpl)thisCDF, var, loc[2],
                (last - first + 1));
            Method method;
            switch (DataTypes.typeCategory[type]) {
            case 0:
                FloatBuffer bvf = bv.asFloatBuffer();
                for (int n = first; n <= last; n++) {
                    data[n] = bvf.get();
                }
                break;
            case 1:
                DoubleBuffer bvd = bv.asDoubleBuffer();
                for (int n = first; n <= last; n++) {
                    data[n] = bvd.get();
                }
                break;
            case 2:
                method = DataTypes.method[type];
                for (int n = first; n <= last; n++) {
                    Number num = (Number)method.invoke(bv, new Object[] {});
                    data[n] = num.doubleValue();
                }
                break;
            case 3:
                method = DataTypes.method[type];
                long longInt = DataTypes.longInt[type];
                for (int n = first; n <= last; n++) {
                    Number num = (Number)method.invoke(bv, new Object[] {});
                    int x = num.intValue();
                    data[n] = (x >= 0)?(double)x:(double)(longInt + x);
                }
                break;
            case 5:
                LongBuffer bvl = bv.asLongBuffer();
                for (int n = first; n <= last; n++) {
                    ldata[n] = bvl.get();
                }
                break;
            }

        }
        if (!var.recordVariance()) {
            if (!longType) {
                for (int i = 1; i < numberOfValues; i++) {
                    data[i] = data[0];
                }
            } else {
                for (int i = 1; i < numberOfValues; i++) {
                    ldata[i] = ldata[0];
                }
            }
        }
        if (longType) return ldata;
        return data;
    }

    public static double [][] getTimeSeries0(CDF thisCDF, Variable var,
        Boolean ignoreFill, double[] timeRange) throws Throwable {
        return getTimeSeries(thisCDF, var, null, ignoreFill, timeRange);
    }
    public static double [][] getTimeSeries1(CDF thisCDF, Variable var,
        Integer which, Boolean ignoreFill, double[] timeRange)
        throws Throwable {
        return getTimeSeries(thisCDF, var, which, ignoreFill, timeRange);
    }
    static double[] castToDouble(Object o, boolean longType) {
        double[] vdata;
        if (!longType) {
            vdata = (double[])o;
        } else {
            long[] ldata = (long[])o;
            vdata = new double[ldata.length];
            for (int i = 0; i < ldata.length; i++) {
                vdata[i] = (double)ldata[i];
            }
        }
        return vdata;
    }
    /**
     * Loss of precision may occur if type of var is LONG
     * times obtained are millisecond since 1970 regardless of the
     * precision of time variable corresponding to variable var
     */
    public static double [][] getTimeSeries(CDF thisCDF, Variable var,
        Integer which, Boolean ignoreFill, double[] timeRange) throws Throwable
        {
        if (var.getNumberOfValues() == 0) return null;
        boolean ignore = ignoreFill.booleanValue();
        double [] vdata;
        int [] recordRange = null;
        double [] times = ((CDFImpl)thisCDF).getTimes(var, false);
        if (times == null) return null;
        double[] stimes;
        boolean longType = false;
        int type = var.getType();
        if (DataTypes.typeCategory[type] == DataTypes.LONG) {
            longType = true;
        }
        Object o = null;
        if (timeRange == null) {
            o = (which == null)?getSeries0(thisCDF, var):
                                getElement1(thisCDF, var, which);
        } else {
            recordRange = getRecordRange(thisCDF, var, timeRange); 
            if (recordRange == null) return null;
            if (which == null) {
                o = getRange0(thisCDF, var, recordRange[0],
                                  recordRange[1]);
            } else {
                o = getRangeForElement1(thisCDF, var,
                    recordRange[0], recordRange[1],
                    which);
            }
        }
        vdata = castToDouble(o, longType);
        if (!ignore) {
            if (timeRange == null) return new double [][] {times, vdata};
            stimes = new double[vdata.length];
            System.arraycopy(times, recordRange[0], stimes, 0, vdata.length);
            return new double [][] {stimes, vdata};
        }
        // fill values need to be filtered
        double [] fill = getFillValue(thisCDF, var);
        int first = (timeRange != null)?recordRange[0]:0;
        if (fill[0] != 0) { // there is no fill value
            stimes = new double[vdata.length];
            System.arraycopy(times, first, stimes, 0, vdata.length);
            return new double [][] {stimes, vdata};
        }
        return filterFill(times, vdata, fill[1], first);
    }

    public static double[] getFillValue(CDF thisCDF, Variable var) {
        Vector fill = (Vector)thisCDF.getAttribute(var.getName(), "FILLVAL");
        if (fill.size() != 0) {
            return new double[] {0, ((double [])fill.elementAt(0))[0]};
        } else {
            return new double[] {Double.NEGATIVE_INFINITY, 0};
        }
    }

    public static double [][] filterFill(double[] times, double [] vdata, 
        double fill, int first) {
        double [][] series;
        int count = 0;
        for (int i = 0; i < vdata.length; i++) {
            if (vdata[i] != fill) count++;
        }
        series = new double[2][count];
        int n = 0;
        for (int i = 0; i < vdata.length; i++) {
            if (vdata[i] == fill) continue;
            series[0][n] = times[i + first];
            series[1][n] = vdata[i];
            n++;
        }
        return series;
    }

    public static double [][] getSeries1(CDF thisCDF, Variable var) throws
        IllegalAccessException, InvocationTargetException {
        int nv = var.getNumberOfValues();
        if (nv == 0) return null;
        if (!var.recordVariance()) nv = 1;
        int elements = (((Integer)elementCount(var).elementAt(0))).intValue();
        double [][] data = new double[nv][elements];

        int type = var.getType();
        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        for (int blk = 0; blk < locations.size(); blk++) {
            int [] loc = (int [])locations.elementAt(blk);
            int first = loc[0];
            int last = loc[1];
            ByteBuffer bv = positionBuffer((CDFImpl)thisCDF, var, loc[2],
                (last - first + 1));
            Method method;
            switch (DataTypes.typeCategory[type]) {
            case 0:
                FloatBuffer bvf = bv.asFloatBuffer();
                for (int n = first; n <= last; n++) {
                    for (int m = 0; m < elements; m++) {
                        data[n][m] = bvf.get();
                    }
                }
                break;
            case 1:
                DoubleBuffer bvd = bv.asDoubleBuffer();
                for (int n = first; n <= last; n++) {
                    for (int m = 0; m < elements; m++) {
                        data[n][m] = bvd.get();
                    }
                }
                break;
            case 2:
                doSignedInteger(bv, type, first, last, elements, data);
                break;
            case 3:
                doUnsignedInteger(bv, type, first, last, elements, data);
                break;
            }

        }
        return data;
    }

    static void doSignedInteger(ByteBuffer bv, int type, int first, 
        int last, int count, double[][] data) throws
        IllegalAccessException, InvocationTargetException {
        Method method = DataTypes.method[type];
        for (int n = first; n <= last; n++) {
            for (int e = 0; e < count; e++) {
                Number num = (Number)method.invoke(bv, new Object[] {});
                data[n][e] = num.doubleValue();
            }
        }
    }

    static void doSignedInteger(ByteBuffer bv, int pos, int type,
        int size, int first, int last, double[] data) throws
        IllegalAccessException, InvocationTargetException {
        Method method = DataTypes.method[type];
        bv.position(pos);
        for (int n = first; n <= last; n++) {
            Number num = (Number)method.invoke(bv, new Object[] {});
            data[n] = num.doubleValue();
            pos += size;
            bv.position(pos);
        }
    }

    static int doSignedInteger(ByteBuffer bv, int pos, int type,
        int size, int first, int last, double[] data, int index) throws
        IllegalAccessException, InvocationTargetException {
        Method method = DataTypes.method[type];
        bv.position(pos);
        for (int n = first; n <= last; n++) {
            Number num = (Number)method.invoke(bv, new Object[] {});
            data[index++] = num.doubleValue();
            pos += size;
            bv.position(pos);
        }
        return index;
    }

    static void doSignedInteger(ByteBuffer bv, int pos, int type,
        int size, int first, int last, int[] offsets, double[][] data) throws
        IllegalAccessException, InvocationTargetException {
        Method method = DataTypes.method[type];
        bv.position(pos);
        int ne = offsets.length;
        for (int n = first; n <= last; n++) {
            for (int e = 0; e < ne; e++) {
                bv.position(pos + offsets[e]);
                Number num = (Number)method.invoke(bv, new Object[] {});
                data[n][e] = num.doubleValue();
            }
            pos += size;
        }
    }

    static int doSignedInteger(ByteBuffer bv, int pos, int type,
        int size, int first, int last, int[] offsets, double[][] data,
        int index) throws IllegalAccessException, InvocationTargetException {
        Method method = DataTypes.method[type];
        bv.position(pos);
        int ne = offsets.length;
        for (int n = first; n <= last; n++) {
            for (int e = 0; e < ne; e++) {
                bv.position(pos + offsets[e]);
                Number num = (Number)method.invoke(bv, new Object[] {});
                data[index][e] = num.doubleValue();
            }
            pos += size;
            index++;
        }
        return index;
    }

    static void doUnsignedInteger(ByteBuffer bv, int type, int first, 
        int last, int count, double[][] data) throws
        IllegalAccessException, InvocationTargetException {
        Method method = DataTypes.method[type];
        long longInt = DataTypes.longInt[type];
        for (int n = first; n <= last; n++) {
            for (int e = 0; e < count; e++) {
                Number num = (Number)method.invoke(bv, new Object[] {});
                int x = num.intValue();
                data[n][e] = (x >= 0)?(double)x:(double)(longInt + x);
            }
        }
    }

    static void doUnsignedInteger(ByteBuffer bv, int pos, int type,
        int size, int first, int last, double[] data) throws
        IllegalAccessException, InvocationTargetException {
        Method method = DataTypes.method[type];
        long longInt = DataTypes.longInt[type];
        bv.position(pos);
        for (int n = first; n <= last; n++) {
            Number num = (Number)method.invoke(bv, new Object[] {});
            int x = num.intValue();
            data[n] = (x >= 0)?(double)x:(double)(longInt + x);
            pos += size;
            bv.position(pos);
        }
    }

    static int doUnsignedInteger(ByteBuffer bv, int pos, int type,
        int size, int first, int last, double[] data, int index) throws
        IllegalAccessException, InvocationTargetException {
        Method method = DataTypes.method[type];
        long longInt = DataTypes.longInt[type];
        bv.position(pos);
        for (int n = first; n <= last; n++) {
            Number num = (Number)method.invoke(bv, new Object[] {});
            int x = num.intValue();
            data[index++] = (x >= 0)?(double)x:(double)(longInt + x);
            pos += size;
            bv.position(pos);
        }
        return index;
    }

    static void doUnsignedInteger(ByteBuffer bv, int pos, int type,
        int size, int first, int last, int[] offsets, double[][] data) throws
        IllegalAccessException, InvocationTargetException {
        Method method = DataTypes.method[type];
        long longInt = DataTypes.longInt[type];
        bv.position(pos);
        int ne = offsets.length;
        for (int n = first; n <= last; n++) {
            for (int e = 0; e < ne; e++) {
                bv.position(pos + offsets[e]);
                Number num = (Number)method.invoke(bv, new Object[] {});
                int x = num.intValue();
                data[n][e] = (x >= 0)?(double)x:(double)(longInt + x);
            }
            pos += size;
        }
    }

    static int doUnsignedInteger(ByteBuffer bv, int pos, int type,
        int size, int first, int last, int[] offsets, double[][] data,
        int index) throws IllegalAccessException, InvocationTargetException {
        Method method = DataTypes.method[type];
        long longInt = DataTypes.longInt[type];
        bv.position(pos);
        int ne = offsets.length;
        for (int n = first; n <= last; n++) {
            for (int e = 0; e < ne; e++) {
                bv.position(pos + offsets[e]);
                Number num = (Number)method.invoke(bv, new Object[] {});
                int x = num.intValue();
                data[index][e] = (x >= 0)?(double)x:(double)(longInt + x);
            }
            pos += size;
            index++;
        }
        return index;
    }

    public static Object getElement1(CDF thisCDF, Variable var, Integer idx) 
        throws Throwable {
        int element = idx.intValue();
        int nv = var.getNumberOfValues();
        if (nv == 0) return null;
        if (!var.recordVariance()) nv = 1;
        if (!validElement(var, new int[] {element})) return null;
        int size = var.getDataItemSize();

        int type = var.getType();
        long[] ldata = null;
        double[] data = null;
        boolean longType = false;
        if (DataTypes.typeCategory[type] == DataTypes.LONG) {
            ldata = new long[nv];
            longType = true;
        } else {
            data = new double[nv];
        }
        int loff = element*DataTypes.size[type];
        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        for (int blk = 0; blk < locations.size(); blk++) {
            int [] loc = (int [])locations.elementAt(blk);
            int first = loc[0];
            int last = loc[1];
            ByteBuffer bv = positionBuffer((CDFImpl)thisCDF, var, loc[2],
                (last - first + 1));
            Method method;
            int pos = bv.position() + loff;
            switch (DataTypes.typeCategory[type]) {
            case 0:
                for (int n = first; n <= last; n++) {
                    data[n] = bv.getFloat(pos);
                    pos += size;
                }
                break;
            case 1:
                for (int n = first; n <= last; n++) {
                    data[n] = bv.getDouble(pos);
                    pos += size;
                }
                break;
            case 2:
                doSignedInteger(bv, pos, type, size, first, last, data);
                break;
            case 3:
                doUnsignedInteger(bv, pos, type, size, first, last, data);
                break;
            case 5:
                for (int n = first; n <= last; n++) {
                    ldata[n] = bv.getLong(pos);
                    pos += size;
                }
                break;
            default:
                throw new Throwable(var.getName() + " has unsupported type " +
                "in this context.");
            }
        }
        if (longType) return ldata;
        return data;
    }

    public static double [][] getElements1(CDF thisCDF, Variable var,
        int[] idx) throws Throwable {
        int nv = var.getNumberOfValues();
        if (nv == 0) return null;
        if (!var.recordVariance()) {
            nv = 1;
        }
        if (!validElement(var, idx)) return null;
        int ne = idx.length;
        double [][] data = new double[nv][ne];
        int size = var.getDataItemSize();

        int type = var.getType();
        int[] loff = new int[ne];
        for (int i = 0; i < ne; i++) {
            loff[i] = idx[i]*DataTypes.size[type];
        }
        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        for (int blk = 0; blk < locations.size(); blk++) {
            int [] loc = (int [])locations.elementAt(blk);
            int first = loc[0];
            int last = loc[1];
            ByteBuffer bv = positionBuffer((CDFImpl)thisCDF, var, loc[2],
                (last - first + 1));
            Method method;
            int pos = bv.position();
            switch (DataTypes.typeCategory[type]) {
            case 0:
                for (int n = first; n <= last; n++) {
                    for (int e = 0; e < ne; e++) {
                        data[n][e] = bv.getFloat(pos + loff[e]);
                    }
                    pos += size;
                }
                break;
            case 1:
                for (int n = first; n <= last; n++) {
                    for (int e = 0; e < ne; e++) {
                        data[n][e] = bv.getDouble(pos + loff[e]);
                    }
                    pos += size;
                }
                break;
            case 2:
                doSignedInteger(bv, pos, type, size, first, last, loff, data);
                break;
            case 3:
                doUnsignedInteger(bv, pos, type, size, first, last, loff, data);
                break;
            }

        }
        return data;
    }

    public static double [][][] getSeries2(CDF thisCDF, Variable var) {
        int nv = var.getNumberOfValues();
        if (nv == 0) return null;
        if (!var.recordVariance()) nv = 1;
        int n0 = (((Integer)elementCount(var).elementAt(0))).intValue();
        int n1 = (((Integer)elementCount(var).elementAt(1))).intValue();
        double [][][] data = new double[nv][n0][n1];
        int type = var.getType();
        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        for (int blk = 0; blk < locations.size(); blk++) {
            int [] loc = (int [])locations.elementAt(blk);
            int first = loc[0];
            int last = loc[1];
            ByteBuffer bv = positionBuffer((CDFImpl)thisCDF, var, loc[2],
                (last - first + 1));
            switch (DataTypes.typeCategory[type]) {
            case 0:
                FloatBuffer bvf = bv.asFloatBuffer();
                if (var.rowMajority()) {
                    for (int n = first; n <= last; n++) {
                        for (int m = 0; m < n0; m++) {
                            for (int l = 0; l < n1; l++) {
                                data[n][m][l] = bvf.get();
                            }
                        }
                    }
                } else {
                    for (int n = first; n <= last; n++) {
                        for (int m = 0; m < n1; m++) {
                            for (int l = 0; l < n0; l++) {
                                data[n][l][m] = bvf.get();
                            }
                        }
                    }
                }
                break;
            case 1:
                DoubleBuffer bvd = bv.asDoubleBuffer();
                if (var.rowMajority()) {
                    for (int n = first; n <= last; n++) {
                        for (int m = 0; m < n0; m++) {
                            for (int l = 0; l < n1; l++) {
                                data[n][m][l] = bvd.get();
                            }
                        }
                    }
                } else {
                    for (int n = first; n <= last; n++) {
                        for (int m = 0; m < n1; m++) {
                            for (int l = 0; l < n0; l++) {
                                data[n][l][m] = bvd.get();
                            }
                        }
                    }
                }
                break;
            }
        }
        return data;
    }

    public static Double getPoint0(CDF thisCDF,Variable var, Integer pt) 
        throws Throwable {
        int point = pt.intValue();
        int type = var.getType();
        int itemSize = var.getDataItemSize();
        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        for (int blk = 0; blk < locations.size(); blk++) {
            int [] loc = (int [])locations.elementAt(blk);
            if (loc[1] < point) continue;
            ByteBuffer bv = positionBuffer((CDFImpl)thisCDF, var, loc[2],
                (loc[1] - loc[0] + 1));
            int pos = bv.position() + (point - loc[0])*itemSize;
            Method method;
            Number num;
            switch (DataTypes.typeCategory[type]) {
            case 0:
                return new Double((double)bv.getFloat(pos));
            case 1:
                return new Double(bv.getDouble(pos));
            case 2:
                method = DataTypes.method[type];
                num = (Number)method.invoke(bv, new Object[] {});
                return new Double(num.doubleValue());
            case 3:
                method = DataTypes.method[type];
                long longInt = DataTypes.longInt[type];
                num = (Number)method.invoke(bv, new Object[] {});
                int x = num.intValue();
                double d = (x >= 0)?(double)x:(double)(longInt + x);
                return new Double(d);
            }
        }
        return null;
    }

    public static double[] getPoint1(CDF thisCDF,Variable var, Integer pt) 
        throws Throwable {
        int point = pt.intValue();
        int type = var.getType();
        int itemSize = var.getDataItemSize();
        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        for (int blk = 0; blk < locations.size(); blk++) {
            int [] loc = (int [])locations.elementAt(blk);
            if (loc[1] < point) continue;
            ByteBuffer bv = positionBuffer((CDFImpl)thisCDF, var, loc[2],
                (loc[1] - loc[0] + 1));
            int pos = bv.position() + (point - loc[0])*itemSize;
            bv.position(pos);
            int n = (((Integer)elementCount(var).elementAt(0))).intValue();
            double [] da = new double[n];
            Method method;
            switch (DataTypes.typeCategory[type]) {
            case 0:
                FloatBuffer bvf = bv.asFloatBuffer();
                for (int i = 0; i < n; i++) {
                    da[i] = bvf.get();
                }
                return da;
            case 1:
                DoubleBuffer bvd = bv.asDoubleBuffer();
                for (int i = 0; i < n; i++) {
                    da[i] = bvd.get();
                }
                return da;
            case 2:
                method = DataTypes.method[type];
                for (int i = 0; i < n; i++) {
                    Number num = (Number)method.invoke(bv, new Object[] {});
                    da[n] = num.doubleValue();
                }
                break;
            case 3:
                method = DataTypes.method[type];
                long longInt = DataTypes.longInt[type];
                for (int i = 0; i < n; i++) {
                    Number num = (Number)method.invoke(bv, new Object[] {});
                    int x = num.intValue();
                    da[n] = (x >= 0)?(double)x:(double)(longInt + x);
                }
            }
        }
        return null;
    }

    public static double[][] getPoint2(CDF thisCDF, Variable var,
        Integer pt) throws Throwable {
        int point = pt.intValue();
        int type = var.getType();
        int itemSize = var.getDataItemSize();
        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        for (int blk = 0; blk < locations.size(); blk++) {
            int [] loc = (int [])locations.elementAt(blk);
            if (loc[1] < point) continue;
            ByteBuffer bv = positionBuffer((CDFImpl)thisCDF, var, loc[2],
                (loc[1] - loc[0] + 1));
            int pos = bv.position() + (point - loc[0])*itemSize;
            bv.position(pos);
            int n0 = (((Integer)elementCount(var).elementAt(0))).intValue();
            int n1 = (((Integer)elementCount(var).elementAt(1))).intValue();
            double [][] da = new double[n0][n1];
            Method method;
            switch (DataTypes.typeCategory[type]) {
            case 0:
                FloatBuffer bvf = bv.asFloatBuffer();
                if (var.rowMajority()) {
                    for (int i = 0; i < n0; i++) {
                        for (int j = 0; j < n1; j++) {
                            da[i][j] = bvf.get();
                        }
                    }
                } else {
                    for (int i = 0; i < n1; i++) {
                        for (int j = 0; j < n0; j++) {
                            da[j][i] = bvf.get();
                        }
                    }
                }
                return da;
            case 1:
                DoubleBuffer bvd = bv.asDoubleBuffer();
                if (var.rowMajority()) {
                    for (int i = 0; i < n0; i++) {
                        for (int j = 0; j < n1; j++) {
                            da[i][j] = bvd.get();
                        }
                    }
                } else {
                    for (int i = 0; i < n1; i++) {
                        for (int j = 0; j < n0; j++) {
                            da[j][i] = bvd.get();
                        }
                    }
                }
                return da;
            case 2:
                method = DataTypes.method[type];
                if (var.rowMajority()) {
                    for (int i = 0; i < n0; i++) {
                        for (int j = 0; j < n1; j++) {
                            Number num = 
                                (Number)method.invoke(bv, new Object[] {});
                            da[i][j] = num.doubleValue();
                        }
                    }
                } else {
                    for (int i = 0; i < n1; i++) {
                        for (int j = 0; j < n0; j++) {
                            Number num = 
                                (Number)method.invoke(bv, new Object[] {});
                            da[j][i] = num.doubleValue();
                        }
                    }
                }
                return da;
            case 3:
                method = DataTypes.method[type];
                long longInt = DataTypes.longInt[type];
                if (var.rowMajority()) {
                    for (int i = 0; i < n0; i++) {
                        for (int j = 0; j < n1; j++) {
                            Number num = 
                                (Number)method.invoke(bv, new Object[] {});
                            int x = num.intValue();
                            double d = (x >= 0)?(double)x:(double)(longInt + x);
                            da[i][j] = d;
                        }
                    }
                } else {
                    for (int i = 0; i < n1; i++) {
                        for (int j = 0; j < n0; j++) {
                            Number num = 
                                (Number)method.invoke(bv, new Object[] {});
                            int x = num.intValue();
                            double d = (x >= 0)?(double)x:(double)(longInt + x);
                            da[j][i] = d;
                        }
                    }
                }
                return da;
            }
        }
        return null;
    }

    public static double[] getElement2(CDF thisCDF,Variable var, Integer pt1,
         Integer pt2) {
        return null;
    }

    public static Object getRange0(CDF thisCDF, Variable var, Integer istart,
        Integer iend) throws Throwable {
        int start = istart.intValue();
        int end = iend.intValue();
        //int numberOfValues = var.getNumberOfValues();
        //int itemSize = var.getDataItemSize();
        int type = var.getType();
        long[] ldata = null;
        double[] data = null;
        boolean longType = false;
        if (DataTypes.typeCategory[type] == DataTypes.LONG) {
            ldata = new long[end - start + 1];
            longType = true;
        } else {
            data = new double[end - start + 1];
        }
        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        int [] blks =  
            getBlockRange(locations, var.recordVariance(), start, end);
        int firstBlock = blks[0];
        int lastBlock = blks[1];
        int index = 0;
        for (int blk = firstBlock; blk <= lastBlock; blk++) {
            Object[] oa = positionBuffer((CDFImpl)thisCDF, var, blks, blk,
                start, end);
            ByteBuffer bv = (ByteBuffer)oa[0];
            int first = ((Integer)oa[1]).intValue();
            int last = ((Integer)oa[2]).intValue();
            Method method;
            switch (DataTypes.typeCategory[type]) {
            case 0:
                FloatBuffer bvf = bv.asFloatBuffer();
                for (int n = first; n <= last; n++) {
                    data[index++] = bvf.get();
                }
                break;
            case 1:
                DoubleBuffer bvd = bv.asDoubleBuffer();
                for (int n = first; n <= last; n++) {
                    data[index++] = bvd.get();
                }
                break;
            case 2:
                method = DataTypes.method[type];
                for (int n = first; n <= last; n++) {
                    Number num = (Number)method.invoke(bv, new Object[] {});
                    data[index++] = num.doubleValue();
                }
                break;
            case 3:
                method = DataTypes.method[type];
                long longInt = DataTypes.longInt[type];
                for (int n = first; n <= last; n++) {
                    Number num = (Number)method.invoke(bv, new Object[] {});
                    int x = num.intValue();
                    data[index++] = (x >= 0)?(double)x:(double)(longInt + x);
                }
            case 5:
                LongBuffer bvl = bv.asLongBuffer();
                for (int n = first; n <= last; n++) {
                    ldata[index++] = bvl.get();
                }
                break;
            }
        }
        if (!var.recordVariance()) {
            if (!longType) {
                for (int i = start; i <= end; i++) {
                    data[i] = data[0];
                }
            } else {
                for (int i = start; i <= end; i++) {
                    ldata[i] = ldata[0];
                }
            }
        }
        if (longType) return ldata;
        return data;
    }

    public static double [][] getRange1(CDF thisCDF, Variable var,
        Integer istart, Integer iend) throws Throwable {
        int start = istart.intValue();
        int end = iend.intValue();
        //int numberOfValues = var.getNumberOfValues();
        //int itemSize = var.getDataItemSize();
        int elements = (((Integer)elementCount(var).elementAt(0))).intValue();
        double [][] data = new double[end - start + 1][elements];

        int type = var.getType();
        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        int [] blks =  
            getBlockRange(locations, var.recordVariance(), start, end);
        int firstBlock = blks[0];
        int lastBlock = blks[1];

        int index = 0;
        for (int blk = firstBlock; blk <= lastBlock; blk++) {
            Object[] oa = positionBuffer((CDFImpl)thisCDF, var, blks, blk,
                start, end);
            ByteBuffer bv = (ByteBuffer)oa[0];
            int first = ((Integer)oa[1]).intValue();
            int last = ((Integer)oa[2]).intValue();
            switch (DataTypes.typeCategory[type]) {
            case 0:
                FloatBuffer bvf = bv.asFloatBuffer();
                for (int n = first; n <= last; n++) {
                    for (int m = 0; m < elements; m++) {
                        data[index][m] = bvf.get();
                    }
                    index++;
                }
                break;
            case 1:
                DoubleBuffer bvd = bv.asDoubleBuffer();
                for (int n = first; n <= last; n++) {
                    for (int m = 0; m < elements; m++) {
                        data[index][m] = bvd.get();
                    }
                    index++;
                }
                break;
            case 2:
                doSignedInteger(bv, type, first, last, elements, data);
                break;
            case 3:
                doUnsignedInteger(bv, type, first, last, elements, data);
                break;
            }
        }
        if (!var.recordVariance()) {
            for (int i = start; i <= end; i++) {
                for (int m = 0; m < elements; m++) {
                    data[i - start][m] = data[0][m];
                }
            }
        }
        return data;
    }


    static Vector elementCount(Variable var) {
        int [] dimensions = var.getDimensions();
        Vector ecount = new Vector();
        for (int i = 0; i < dimensions.length; i++) {
                if (var.getVarys()[i]) ecount.add(dimensions[i]);
        }
        return ecount;
    }
    /** good for rank 1
     */
    static boolean validElement(Variable var, int[] idx) {
        int elements = (((Integer)elementCount(var).elementAt(0))).intValue();
        for (int i = 0; i < idx.length; i++) {
            if ((idx[i] >= 0) && (idx[i] < elements)) continue;
            return false;
        } 
        return true;
    }

    public static Object getRangeForElement1(CDF thisCDF, Variable var,
        Integer istart, Integer iend, Integer ielement) throws Throwable {
        int element = ielement.intValue();
        if (!validElement(var, new int[] {element})) return null;
        int start = istart.intValue();
        int end = iend.intValue();
        //int numberOfValues = var.getNumberOfValues();
        int size = var.getDataItemSize();
        int type = var.getType();
        long[] ldata = null;
        double[] data = null;
        boolean longType = false;
        if (DataTypes.typeCategory[type] == DataTypes.LONG) {
            ldata = new long[end - start + 1];
            longType = true;
        } else {
            data = new double[end - start + 1];
        }
        int loff = element*DataTypes.size[type];
        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        int [] blks =  
            getBlockRange(locations, var.recordVariance(), start, end);
        int firstBlock = blks[0];
        int lastBlock = blks[1];
        int index = 0;
        for (int blk = firstBlock; blk <= lastBlock; blk++) {
            Object[] oa = positionBuffer((CDFImpl)thisCDF, var, blks, blk,
                start, end);
            ByteBuffer bv = (ByteBuffer)oa[0];
            int first = ((Integer)oa[1]).intValue();
            int last = ((Integer)oa[2]).intValue();
            int pos = bv.position() + loff;
            switch (DataTypes.typeCategory[type]) {
            case 0:
                for (int n = first; n <= last; n++) {
                    data[index++] = bv.getFloat(pos);
                    pos += size;
                }
                break;
            case 1:
                for (int n = first; n <= last; n++) {
                    data[index++] = bv.getDouble(pos);
                    pos += size;
                }
                break;
            case 2:
                index = doSignedInteger(bv, pos, type, size, first, last,
                     data, index);
                break;
            case 3:
                index = doUnsignedInteger(bv, pos, type, size, first, last,
                     data, index);
                break;
            case 5:
                for (int n = first; n <= last; n++) {
                    ldata[index++] = bv.getLong(pos);
                    pos += size;
                }
                break;
            }
        }
        if (!var.recordVariance()) {
            if (!longType) {
                for (int i = start; i <= end; i++) {
                    data[i - start] = data[0];
                }
            } else {
                for (int i = start; i <= end; i++) {
                    ldata[i - start] = ldata[0];
                }
            }
        }
        if (longType) return ldata;
        return data;
    }

    /**
     * returns range of values for the specified elements of a one
     * dimensional variable.
     * returns null if any of the specified elements is not valid.
     * throws UnsupportedFeatureException
     */
    public static Object getRangeForElements1(CDF thisCDF, Variable var,
        Integer istart, Integer iend, int[] idx) throws Throwable {
        if (!validElement(var, idx)) return null;
        int start = istart.intValue();
        int end = iend.intValue();
        //int numberOfValues = var.getNumberOfValues();
        int size = var.getDataItemSize();
        int ne = idx.length;

        int type = var.getType();
        long[][] ldata = null;
        double[][] data = null;
        boolean longType = false;
        if (DataTypes.typeCategory[type] == DataTypes.LONG) {
            ldata = new long[end - start + 1][ne];
            longType = true;
        } else {
            data = new double[end - start + 1][ne];
        }
        int[] loff = new int[ne];
        for (int i = 0; i < ne; i++) {
            loff[i] = idx[i]*DataTypes.size[type];
        }
        // loff contains offsets from the beginning of the item
        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        int [] blks =  
            getBlockRange(locations, var.recordVariance(), start, end);
        int firstBlock = blks[0];
        int lastBlock = blks[1];

        int index = 0;
        for (int blk = firstBlock; blk <= lastBlock; blk++) {
            Object[] oa = positionBuffer((CDFImpl)thisCDF, var, blks, blk,
                start, end);
            ByteBuffer bv = (ByteBuffer)oa[0];
            int first = ((Integer)oa[1]).intValue();
            int last = ((Integer)oa[2]).intValue();
            int pos = bv.position();
            switch (DataTypes.typeCategory[type]) {
            case 0:
                for (int n = first; n <= last; n++) {
                    for (int e = 0; e < ne; e++) {
                        data[index][e] = bv.getFloat(pos + loff[e]);
                    }
                    pos += size;
                    index++;
                }
                break;
            case 1:
                for (int n = first; n <= last; n++) {
                    for (int e = 0; e < ne; e++) {
                        data[index][e] = bv.getDouble(pos + loff[e]);
                    }
                    pos += size;
                    index++;
                }
                break;
            case 2:
                index = doSignedInteger(bv, pos, type, size, first, last,
                    loff,  data, index);
                break;
            case 3:
                index = doUnsignedInteger(bv, pos, type, size, first, last,
                    loff,  data, index);
                break;
            case 5:
                for (int n = first; n <= last; n++) {
                    for (int e = 0; e < ne; e++) {
                        ldata[index][e] = bv.getLong(pos + loff[e]);
                    }
                    pos += size;
                    index++;
                }
                break;
            }
        }
        if (!var.recordVariance()) {
            if (!longType) {
                for (int i = start; i <= end; i++) {
                    for (int e = 0; e < ne; e++) {
                        data[i - start][e] = data[0][e];
                    }
                }
            } else {
                for (int i = start; i <= end; i++) {
                    for (int e = 0; e < ne; e++) {
                        ldata[i - start][e] = ldata[0][e];
                    }
                }
            }
        }
        if (longType) return ldata;
        return data;
    }

    public static double [][][] getRange2(CDF thisCDF, Variable var,
        Integer istart, Integer iend) {
        return null;
    }

    /**
     * returns String of length 'size' starting at current position
     * in the given ByteBuffer. On return, the buffer position is 1
     * advanced by the smaller of size, or the length of the null
     * terminated string
     */
    public static String getStringValue(ByteBuffer bv, int size) {
        byte [] ba = new byte[size];
        int i = 0;
        for (; i < size; i++) {
            ba[i] = bv.get();
            if (ba[i] == 0) break;
        }
        return new String(ba, 0, i);
    }

    public static String [] getStringSeries0(CDF thisCDF, Variable var) {
        int numberOfValues = var.getNumberOfValues();
        String [] data = new String[numberOfValues];
        int len = var.getNumberOfElements();
        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        for (int blk = 0; blk < locations.size(); blk++) {
            int [] loc = (int [])locations.elementAt(blk);
            ByteBuffer bv = positionBuffer((CDFImpl)thisCDF, var, loc[2],
                (loc[1] - loc[0] + 1));
            int pos = bv.position();
            for (int n = loc[0]; n <= loc[1]; n++) {
                data[n] = getStringValue(bv, len);
                pos += len;
                bv.position(pos);
            }
        }
        if (!var.recordVariance()) {
            for (int i = 1; i < numberOfValues; i++) {
                data[i] = data[0];
            }
        }
        return data;
    }
    public static String [][] getStringSeries1(CDF thisCDF, Variable var) {
        int nv = var.getNumberOfValues();
        if (nv == 0) return null;
        if (!var.recordVariance()) nv = 1;
        int elements = (((Integer)elementCount(var).elementAt(0))).intValue();
        String [][] data = new String[nv][elements];
        //int size = var.getDataItemSize();
        int len = var.getNumberOfElements();
        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        for (int blk = 0; blk < locations.size(); blk++) {
            int [] loc = (int [])locations.elementAt(blk);
            ByteBuffer bv = positionBuffer((CDFImpl)thisCDF, var, loc[2],
                (loc[1] - loc[0] + 1));
            int pos = bv.position();
            for (int n = loc[0]; n <= loc[1]; n++) {
                for (int m = 0; m < elements; m++) {
                    data[n][m] = getStringValue(bv, len);
                    pos += len;
                    bv.position(pos);
                }
            }
        }
        return data;
    }

    public static String [][][] getStringSeries2(CDF thisCDF, Variable var) {
        return null;
    }

    /**
     * returns range of blocks containing the range of records (start, end).
     */
    static int [] getBlockRange(Vector locations, boolean recordVariance,
        int start, int end) {

        int firstBlock;
        int lastBlock;
        if (recordVariance) {
            firstBlock = -1;
            int blk = 0;
            for (; blk < locations.size(); blk++) {
                int [] loc = (int [])locations.elementAt(blk);
                if (start > loc[1]) continue;
                firstBlock = blk;
                break;
            }
            if (firstBlock < 0) return null;
            blk = firstBlock;
            lastBlock = locations.size() - 1;
            for (; blk < locations.size(); blk++) {
                int [] loc = (int [])locations.elementAt(blk);
                if (end > loc[1]) continue;
                lastBlock = blk;
                break;
            }
        } else {
            firstBlock = 0;
            lastBlock = 0;
        }
        return new int[] {firstBlock, lastBlock};
    }

    /**
     * returns ByteBuffer containing count values for variable var starting at
     * CDF offset value offset.
     */
    static ByteBuffer positionBuffer(CDFImpl impl, Variable var, int offset,
        int count) {
        ByteBuffer bv;
        if (!var.isCompressed()) {
            bv = impl.getValueBuffer(offset);
        } else {
            int size = var.getDataItemSize();
            bv = impl.getValueBuffer(offset, size , count);
        }
        bv.order(impl.getByteOrder());
        return bv;
    }

    /**
     * returns ByteBuffer, index of first entry and index of last entry for
     * the specified block of data corresponding to variable 'var' for the
     * range of records (start, end).
     */
    static Object[] positionBuffer(CDFImpl impl, Variable var, int[] blockRange,
        int blk, int start, int end) {
        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        int [] loc = (int [])locations.elementAt(blk);
        int first = loc[0];
        int last = loc[1];
        ByteBuffer bv = positionBuffer(impl, var, loc[2], (last - first + 1));
        if (var.recordVariance()) {
            if (blk == blockRange[0]) {// position to first needed
                int size = var.getDataItemSize();
                bv.position(bv.position() + size*(start - first));
                first = start;
            }
            if (blk == blockRange[1]) {// position to first needed
                last = end;
            }
        }
        return new Object[] {bv, first, last};
    }
    public static double [][][][] getSeries3(CDF thisCDF, Variable var) 
        throws Throwable {
        int type = var.getType();
        int cat = DataTypes.typeCategory[type];
        if (cat > 1) throw new Throwable("Type category " + cat +
        " not supported in this context");
        int nv = var.getNumberOfValues();
        if (nv == 0) return null;
        if (!var.recordVariance()) nv = 1;
        int n0 = (((Integer)elementCount(var).elementAt(0))).intValue();
        int n1 = (((Integer)elementCount(var).elementAt(1))).intValue();
        int n2 = (((Integer)elementCount(var).elementAt(2))).intValue();
        double [][][][] data = new double[nv][n0][n1][n2];
        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        for (int blk = 0; blk < locations.size(); blk++) {
            int [] loc = (int [])locations.elementAt(blk);
            int first = loc[0];
            int last = loc[1];
            ByteBuffer bv = positionBuffer((CDFImpl)thisCDF, var, loc[2],
                (last - first + 1));
            switch (DataTypes.typeCategory[type]) {
            case 0:
                FloatBuffer bvf = bv.asFloatBuffer();
                if (var.rowMajority()) {
                    for (int n = first; n <= last; n++) {
                        for (int m = 0; m < n0; m++) {
                            for (int l = 0; l < n1; l++) {
                                for (int k = 0; k < n2; k++) {
                                    data[n][m][l][k] = bvf.get();
                                }
                            }
                        }
                    }
                } else {
                    for (int n = first; n <= last; n++) {
                        for (int m = 0; m < n2; m++) {
                            for (int l = 0; l < n1; l++) {
                                for (int k = 0; k < n0; k++) {
                                    data[n][k][l][m] = bvf.get();
                                }
                            }
                        }
                    }
                }
                break;
            case 1:
                DoubleBuffer bvd = bv.asDoubleBuffer();
                if (var.rowMajority()) {
                    for (int n = first; n <= last; n++) {
                        for (int m = 0; m < n0; m++) {
                            for (int l = 0; l < n1; l++) {
                                for (int k = 0; k < n2; k++) {
                                    data[n][m][l][k] = bvd.get();
                                }
                            }
                        }
                    }
                } else {
                    for (int n = first; n <= last; n++) {
                        for (int m = 0; m < n2; m++) {
                            for (int l = 0; l < n1; l++) {
                                for (int k = 0; k < n0; k++) {
                                    data[n][k][l][m] = bvd.get();
                                }
                            }
                        }
                    }
                }
                break;
            }
        }
        return data;
    }
    public static double[][][] getPoint3(CDF thisCDF, Variable var,
        Integer pt) throws Throwable {
        int point = pt.intValue();
        int type = var.getType();
        int itemSize = var.getDataItemSize();
        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        for (int blk = 0; blk < locations.size(); blk++) {
            int [] loc = (int [])locations.elementAt(blk);
            if (loc[1] < point) continue;
            ByteBuffer bv = positionBuffer((CDFImpl)thisCDF, var, loc[2],
                (loc[1] - loc[0] + 1));
            int pos = bv.position() + (point - loc[0])*itemSize;
            bv.position(pos);
            int n0 = (((Integer)elementCount(var).elementAt(0))).intValue();
            int n1 = (((Integer)elementCount(var).elementAt(1))).intValue();
            int n2 = (((Integer)elementCount(var).elementAt(2))).intValue();
            double [][][] da = new double[n0][n1][n2];
            Method method;
            switch (DataTypes.typeCategory[type]) {
            case 0:
                FloatBuffer bvf = bv.asFloatBuffer();
                if (var.rowMajority()) {
                    for (int i = 0; i < n0; i++) {
                        for (int j = 0; j < n1; j++) {
                            for (int k = 0; k < n2; k++) {
                                da[i][j][k] = bvf.get();
                            }
                        }
                    }
                } else {
                    for (int i = 0; i < n2; i++) {
                        for (int j = 0; j < n1; j++) {
                            for (int k = 0; k < n0; k++) {
                                da[k][j][i] = bvf.get();
                            }
                        }
                    }
                }
                return da;
            case 1:
                DoubleBuffer bvd = bv.asDoubleBuffer();
                if (var.rowMajority()) {
                    for (int i = 0; i < n0; i++) {
                        for (int j = 0; j < n1; j++) {
                            for (int k = 0; k < n2; k++) {
                                da[i][j][k] = bvd.get();
                            }
                        }
                    }
                } else {
                    for (int i = 0; i < n2; i++) {
                        for (int j = 0; j < n1; j++) {
                            for (int k = 0; k < n0; k++) {
                                da[k][j][i] = bvd.get();
                            }
                        }
                    }
                }
                return da;
            case 2:
                method = DataTypes.method[type];
                if (var.rowMajority()) {
                    for (int i = 0; i < n0; i++) {
                        for (int j = 0; j < n1; j++) {
                            for (int k = 0; k < n2; k++) {
                                Number num = 
                                    (Number)method.invoke(bv, new Object[] {});
                                da[i][j][k] = num.doubleValue();
                            }
                        }
                    }
                } else {
                    for (int i = 0; i < n2; i++) {
                        for (int j = 0; j < n1; j++) {
                            for (int k = 0; k < n0; k++) {
                                Number num = 
                                    (Number)method.invoke(bv, new Object[] {});
                                da[k][j][i] = num.doubleValue();
                            }
                        }
                    }
                }
                return da;
            case 3:
                method = DataTypes.method[type];
                long longInt = DataTypes.longInt[type];
                if (var.rowMajority()) {
                    for (int i = 0; i < n0; i++) {
                        for (int j = 0; j < n1; j++) {
                            for (int k = 0; k < n2; k++) {
                                Number num = 
                                    (Number)method.invoke(bv, new Object[] {});
                                int x = num.intValue();
                                double d = (x >= 0)?(double)x:
                                    (double)(longInt + x);
                                da[i][j][k] = d;
                            }
                        }
                    }
                } else {
                    for (int i = 0; i < n2; i++) {
                        for (int j = 0; j < n1; j++) {
                            for (int k = 0; k < n0; k++) {
                                Number num = 
                                    (Number)method.invoke(bv, new Object[] {});
                                int x = num.intValue();
                                double d = (x >= 0)?(double)x:
                                    (double)(longInt + x);
                                da[k][j][i] = d;
                            }
                        }
                    }
                }
                return da;
            }
        }
        return null;
    }

    public static double [] get1DSeries(CDF thisCDF, Variable var, int[] pt)
        throws IllegalAccessException, InvocationTargetException {
        return (double[])get1DSeries(thisCDF, var, pt, false);
    }

    /**
     * J.Faden added this to get an implementation of slice1, which allows
     * part of each record to be read.
     * @param thisCDF
     * @param var
     * @param slice1 the index on the first dimension.  If slice1=-1 then don't slice.
     * @param pt
     * @param preserve
     * @return
     * @throws IllegalAccessException
     * @throws InvocationTargetException 
     */
    public static Object get1DSeries(CDF thisCDF, Variable var, int slice1, int[] pt,
        boolean preserve) throws IllegalAccessException,InvocationTargetException {
        int end = -1;
        int begin = 0;
        int nv = 0;
        if (pt != null) {
            if (var.recordVariance()) {
                begin = pt[0];
                nv = 1;
                if (pt.length > 1) {
                    end = pt[1];
                    nv = end - begin + 1;
                }
            }
        } else {
            nv = var.getNumberOfValues();
            if (nv == 0) return null;
        }
        if (!var.recordVariance()) nv = 1;
        long[] ldata = null;
        double[] data = null;
        boolean longType = false;
        int type = var.getType();
        int itemSize = slice1==-1 ? var.getDataItemSize() : var.getDataItemSize() / var.getDimensions()[0] ;
        int elements = itemSize/DataTypes.size[type];
        if (DataTypes.typeCategory[type] == DataTypes.LONG) {
            if (preserve) ldata = new long[nv*elements];
            if (!preserve) data = new double[nv*elements];
            longType = true;
        } else {
            if ( ! ( preserve && DataTypes.typeCategory[type] == 0 ) ) { // special case where we just return float[]
                data = new double[nv*elements];
            }
        }

        Object temp = null;
        if (DataTypes.typeCategory[type] == 0) temp = new float[nv*elements];
        if (longType && !preserve) temp = new long[nv*elements];

        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        ByteBuffer bv;
        int blk = 0;
        int offset = 0;
        if (pt == null) {
            begin = ((int [])locations.elementAt(0))[0];
            end = ((int [])locations.elementAt(locations.size() - 1))[1];
        }
        ByteBuffer sl= ByteBuffer.allocate( itemSize*nv ); //slice1
        int otherDimensionsSize= itemSize;
        for ( int i=1; i<var.getDimensions().length-1; i++ ) {
            otherDimensionsSize*= var.getDimensions()[i];
        }
        for (; blk < locations.size(); blk++) {
            int [] loc = (int [])locations.elementAt(blk);
            int first = loc[0];
            int last = loc[1];
            if (last < begin) continue;
            int count = (last - first + 1);
            bv = positionBuffer((CDFImpl)thisCDF, var, loc[2], count);
            if ( slice1>-1 ) {
                doSlice1( bv, sl, offset, count, var.getDataItemSize(), slice1*otherDimensionsSize, otherDimensionsSize );
                bv= sl;
                sl.flip();
            }
            if (begin > first) {
                int pos = bv.position() + (begin - first)*itemSize;
                bv.position(pos);
            }
            if (end < 0) { // single point needed
                if (longType && preserve) {
                    do1D(bv, type, temp, ldata, 0, elements, preserve);
                } else {
                    do1D(bv, type, temp, data, 0, elements);
                }
                offset += elements;
            } else {
                int term = (end <= last)?end:last;
                int init = (begin > first)?begin:first;
                count = (term - init + 1);
                if (longType && preserve) {
                    do1D(bv, type, temp, ldata, offset, count*elements,
                    preserve);
                } else {
                    do1D(bv, type, temp, data, offset, count*elements,preserve);
                }
                offset += count*elements;
            }
            if (end <= last) break;
        }
        if (offset == 0) return null;
        if (longType && preserve) return ldata;
        if (DataTypes.typeCategory[type]==0 && preserve ) return temp;
        return data;
        
    }
    public static Object get1DSeries(CDF thisCDF, Variable var, int[] pt,
        boolean preserve) throws IllegalAccessException,
        InvocationTargetException {
        int end = -1;
        int begin = 0;
        int nv = 0;
        if (pt != null) {
            if (var.recordVariance()) {
                begin = pt[0];
                nv = 1;
                if (pt.length > 1) {
                    end = pt[1];
                    nv = end - begin + 1;
                }
            }
        } else {
            nv = var.getNumberOfValues();
            if (nv == 0) return null;
        }
        if (!var.recordVariance()) nv = 1;
        long[] ldata = null;
        double[] data = null;
        boolean longType = false;
        int type = var.getType();
        int itemSize = var.getDataItemSize();
        int elements = itemSize/DataTypes.size[type];
        if (DataTypes.typeCategory[type] == DataTypes.LONG) {
            if (preserve) ldata = new long[nv*elements];
            if (!preserve) data = new double[nv*elements];
            longType = true;
        } else {
            if ( ! ( preserve && DataTypes.typeCategory[type] == 0 ) ) { // special case where we just return float[]
                data = new double[nv*elements];
            }
        }

        Object temp = null;
        if (DataTypes.typeCategory[type] == 0) temp = new float[nv*elements];
        if (longType && !preserve) temp = new long[nv*elements];

        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        ByteBuffer bv;
        int blk = 0;
        int offset = 0;
        if (pt == null) {
            begin = ((int [])locations.elementAt(0))[0];
            end = ((int [])locations.elementAt(locations.size() - 1))[1];
        }
        for (; blk < locations.size(); blk++) {
            int [] loc = (int [])locations.elementAt(blk);
            int first = loc[0];
            int last = loc[1];
            if (last < begin) continue;
            int count = (last - first + 1);
            bv = positionBuffer((CDFImpl)thisCDF, var, loc[2], count);
            if (begin > first) {
                int pos = bv.position() + (begin - first)*itemSize;
                bv.position(pos);
            }
            if (end < 0) { // single point needed
                if (longType && preserve) {
                    do1D(bv, type, temp, ldata, 0, elements, preserve);
                } else {
                    do1D(bv, type, temp, data, 0, elements);
                }
                offset += elements;
            } else {
                int term = (end <= last)?end:last;
                int init = (begin > first)?begin:first;
                count = (term - init + 1);
                if (longType && preserve) {
                    do1D(bv, type, temp, ldata, offset, count*elements,
                    preserve);
                } else {
                    do1D(bv, type, temp, data, offset, count*elements,preserve);
                }
                offset += count*elements;
            }
            if (end <= last) break;
        }
        if (offset == 0) return null;
        if (longType && preserve) return ldata;
        if (DataTypes.typeCategory[type]==0 && preserve ) return temp;
        return data;
    }

    static void do1D(ByteBuffer bv, int type, Object temp, Object result,
       int offset, int number) throws IllegalAccessException,
       InvocationTargetException {
       do1D(bv, type, temp, result, offset, number, false);
    }

    /**
     * read supports reading a slice of a dataset.  For example, we want
     * just the Bx component of B-GSM.
     * @param bv the byte buffer for the CDF file.
     * @param type DataTypes.typeCategory[type]
     * @param result result array
     * @param offset offset into byteBuffer in bytes
     * @param nrec number of records to read.
     * @param recLen length of each record in bytes.
     * @param readOffset offset into each record in bytes.
     * @param readLen length of each record in bytes.
     */
    static void doSlice1(ByteBuffer bv, ByteBuffer result, int offset, int nrec, int recLen, int readOffset, int readLen ) {
        logger.log( Level.FINEST, "doSlice1( buf.position={0} )", 
                new Object[] { bv.position() } );

        int ipos= bv.position();
        for ( int i=0; i<nrec; i++ ) {
            bv.limit(ipos+readOffset+readLen);
            bv.position(ipos+readOffset);
            result.put(bv);
            ipos= ipos + recLen;
            System.err.println("i="+i);
            if ( i==40 ) {
                System.err.println("hereherehere");
            }
        }
        
    }
    
    static void do1D(ByteBuffer bv, int type, Object temp, Object result,
       int offset, int number, boolean preserve) throws IllegalAccessException,
       InvocationTargetException {

        logger.log( Level.FINEST, "do1D(buf.position={0},type={1},offset={2},number={3},preserve={4})", new Object[] { bv.position(), type, offset, number, preserve } );

        Method method;
        double[] data = null;
        if (DataTypes.typeCategory[type] != DataTypes.LONG) {
            data = (double[])result;
        }
        switch (DataTypes.typeCategory[type]) {
        case 0:
            float[] tf = (float[])temp;
            FloatBuffer bvf = bv.asFloatBuffer();
            bvf.get(tf, offset, number);
            if ( !preserve ) {
                for (int n = 0; n < number; n++) {
                    data[offset + n] = tf[n+offset];
                }
            }
            break;
        case 1:
            DoubleBuffer bvd = bv.asDoubleBuffer();
            bvd.get(data, offset, number);
            break;
        case 2:
            method = DataTypes.method[type];
            for (int e = 0; e < number; e++) {
                Number num = (Number)method.invoke(bv, new Object[] {});
                data[offset + e] = num.doubleValue();
            }
            break;
        case 3:
            method = DataTypes.method[type];
            long longInt = DataTypes.longInt[type];
            for (int e = 0; e < number; e++) {
                Number num = (Number)method.invoke(bv, new Object[] {});
                int x = num.intValue();
                data[offset + e] = (x >= 0)?(double)x:(double)(longInt + x);
            }
            break;
        case 5:
            LongBuffer bvl = bv.asLongBuffer();
            if (!preserve) {
                long[] tl = (long[])temp;
                data = (double[])result;
                bvl.get(tl, offset, number);
                for (int n = 0; n < number; n++) {
                    data[offset + n] = (double)tl[n];
                }
                break;
            }
            long[] ldata = (long[])result;
            bvl.get(ldata, offset, number);
        }
    }
    public static double [] get1DSeries(CDF thisCDF, Variable var, int[] pt,
        int[] stride) throws IllegalAccessException, InvocationTargetException,
        Throwable {
        double [] data;
        
        int end = -1;
        int begin = 0;
        int nv = 0;
        if (pt != null) {
            if (var.recordVariance()) {
                begin = pt[0];
                nv = 1;
                if (pt.length > 1) {
                    end = pt[1];
                    nv = end - begin + 1;
                }
            }
        } else {
            nv = var.getNumberOfValues();
        }
        if (nv == 0) return null;
        if (!var.recordVariance()) nv = 1;
        Stride strideObject = new Stride(stride);
        int _stride = strideObject.getStride(nv);
        if (_stride > 1) {
            nv = (nv/_stride);
            if ((nv % _stride) != 0) nv++;
        }
        int type = var.getType();
        int itemSize = var.getDataItemSize();
        int elements = itemSize/DataTypes.size[type];
        data = new double[nv*elements];

        float[] tf = null;
        if (_stride == 1) {
            if (DataTypes.typeCategory[type] == 0) tf = new float[nv*elements];
        }

        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        ByteBuffer bv;
        int blk = 0;
        int offset = 0;
        if (pt == null) {
            begin = ((int [])locations.elementAt(0))[0];
            end = ((int [])locations.elementAt(locations.size() - 1))[1];
        }
        int index = 0;
        for (; blk < locations.size(); blk++) {
            int [] loc = (int [])locations.elementAt(blk);
            int first = loc[0];
            int last = loc[1];
            if (last < begin) continue;
            int count = (last - first + 1);
            bv = positionBuffer((CDFImpl)thisCDF, var, loc[2], count);
            // position buffer at the first point desired
            // init is the index of the first point desired
            int pos = 0;
            int init;
            if (begin > first) {
                init = begin;
            } else {
                init = first;
                if (_stride > 1) {
                    int elapsed = first - begin;
                    if ((elapsed % _stride) != 0) {
                        init = first - (elapsed % _stride) + _stride;
                    }
                }
            }
            pos = bv.position() + (init - first)*itemSize;
            bv.position(pos);
            // compute number of points to be extracted and
            // allocate temporary storage if necessary
            if (end < 0) { // single point needed
                do1D(bv, type, tf, data, 0, elements);
                offset += elements;
            } else {
                int term = (end <= last)?end:last;
                if (_stride == 1) {
                    count = (term - init + 1);
                    do1D(bv, type, tf, data, offset, count*elements);
                } else {
                    count = (term - init)/_stride;
                    if (count*_stride < (term - init)) count++;
                    if (DataTypes.typeCategory[type] == 0) {
                        tf = new float[count*elements];
                    }
                    do1D(bv, type, tf, data, offset, count,
                        elements, _stride);
                }
                offset += count*elements;
            }
            if (end <= last) break;
        }
        if (offset == 0) return null;
        return data;
    }
    /**
     * extract the data into a 1-D double array.
     * @param bv the buffer containing the data, positioned at the first point desired
     * @param type the CDF variable type (21 is float,etc)
     * @param tf
     * @param data the output array that is populated
     * @param offset
     * @param count
     * @param elements
     * @param _stride
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws Throwable
     */
    // bv is positioned at the first point desired
    static void do1D(ByteBuffer bv, int type, float[] tf, double[] data,
       int offset, int count, int elements, int _stride) throws
       IllegalAccessException, InvocationTargetException, Throwable {
        Method method;
        logger.log( Level.FINEST, "do1D(buf.position={0},type={1},offset={2},count={3})", new Object[] { bv.position(), type, offset, count } );
        int span = _stride*elements;
        int pos = bv.position();
        switch (DataTypes.typeCategory[type]) {
        case 0:
            FloatBuffer bvf = bv.asFloatBuffer();
            for (int n = 0; n < count; n++) {
                bvf.position(n*span);
                bvf.get(tf, n*elements, elements);
            }
            for (int n = 0; n < count*elements; n++) {
                data[offset + n] = tf[n];
            }
            break;
        case 1:
            DoubleBuffer bvd = bv.asDoubleBuffer();
            for (int n = 0; n < count; n++) {
                bvd.position(n*span);
                bvd.get(data, offset, elements);
                offset += elements;
            }
            break;
        case 2:
            method = DataTypes.method[type];
            for (int n = 0; n < count; n++) {
                bv.position(pos + n*span);
                for (int e = 0; e < elements; e++) {
                    Number num = 
                        (Number)method.invoke(bv, new Object[] {});
                    data[offset++] = num.doubleValue();
                }
            }
            break;
        case 3:
            method = DataTypes.method[type];
            long longInt = DataTypes.longInt[type];
            for (int n = 0; n < count; n++) {
                bv.position(n*span);
                for (int e = 0; e < elements; e++) {
                    Number num = (Number)method.invoke(bv,
                        new Object[] {});
                    int x = num.intValue();
                    data[offset++] =
                        (x >= 0)?(double)x:(double)(longInt + x);
                }
            }
            break;
        case 5:
           throw new IllegalArgumentException("type catagory 5, long, not supported");
           
        default:
            throw new Throwable("Unsupported data type for this " +
                "context");
        }
    }

    public static double [][] getSampledTimeSeries0(CDF thisCDF, Variable var,
        Boolean ignoreFill, double[] timeRange, int[] stride) throws Throwable
        {
        return getSampledTimeSeries(thisCDF, var, null, ignoreFill, timeRange,
            stride);
    }
    public static double [][] getSampledTimeSeries1(CDF thisCDF, Variable var,
        Integer which, Boolean ignoreFill, double[] timeRange, int[] stride)
        throws Throwable {
        return getSampledTimeSeries(thisCDF, var, which, ignoreFill, timeRange,
            stride);
    }
    public static double [][] getSampledTimeSeries(CDF thisCDF, Variable var,
        Integer which, Boolean ignoreFill, double[] timeRange, int[] stride)
        throws Throwable {
        if (var.getNumberOfValues() == 0) return null;
        boolean ignore = ignoreFill.booleanValue();
        double [] vdata;
        int [] recordRange = null;
        double [] times = null;
        times = ((CDFImpl)thisCDF).getTimes(var, false);
        if (times == null) return null;
        double[] stimes;
        Stride strideObject = new Stride(stride);
        if (timeRange == null) {
            vdata = (which == null)?getSeries0(thisCDF, var, strideObject):
                                    getElement1(thisCDF, var, which,
                                    strideObject);
        } else {
            recordRange = getRecordRange(thisCDF, var, timeRange);
            if (recordRange == null) return null;
            if (which == null) {
                vdata = getRange0(thisCDF, var, recordRange[0],
                                  recordRange[1], strideObject);
            } else {
                vdata = getRangeForElement1(thisCDF, var,
                    recordRange[0], recordRange[1],
                    which, strideObject);
            }
        }
        int _stride = strideObject.getStride();
        double [] fill = getFillValue(thisCDF, var);
        if ((!ignore) || (fill[0] != 0)) {
            if (timeRange == null) {
                if (_stride == 1) {
                    return new double [][] {times, vdata};
                } else {
                    stimes = new double[vdata.length];
                    for (int i = 0; i < vdata.length; i ++) {
                        stimes[i] = times[i*_stride];
                    }
                    return new double [][] {stimes, vdata};
                }
            } else {
                stimes = new double[vdata.length];
                if (_stride == 1) {
                    System.arraycopy(times, recordRange[0], stimes, 0,
                        vdata.length);
                    return new double [][] {stimes, vdata};
                } else {
                    int srec = recordRange[0];
                    for (int i = 0; i < vdata.length; i ++) {
                        stimes[i] = times[srec + i*_stride];
                    }
                    return new double [][] {stimes, vdata};
                }
            }
        }
        // fill values need to be filtered
        if (timeRange == null) {
            if (_stride == 1) {
                return filterFill(times, vdata, fill[1], 0);
            } else {
                stimes = new double[vdata.length];
                for (int i = 0; i < vdata.length; i ++) {
                    stimes[i] = times[i*_stride];
                }
                return filterFill(stimes, vdata, fill[1], 0);
            }
        } else {
            stimes = new double[vdata.length];
            if (_stride == 1) {
                System.arraycopy(times, recordRange[0], stimes, 0,
                    vdata.length);
                return filterFill(stimes, vdata, fill[1], 0);
            } else {
                int srec = recordRange[0];
                for (int i = 0; i < vdata.length; i ++) {
                    stimes[i] = times[srec + i*_stride];
                }
                return filterFill(stimes, vdata, fill[1], 0);
            }
        }
    }
    public static double [] getSeries0(CDF thisCDF, Variable var,
        Stride strideObject) throws IllegalAccessException,
        InvocationTargetException, Throwable {
        int numberOfValues = var.getNumberOfValues();
        if (numberOfValues == 0) return null;
        int type = var.getType();
        int numpt;
        int _stride = strideObject.getStride(numberOfValues);
        if (_stride == 1) {
            numpt = numberOfValues;
        } else {
            int cat = DataTypes.typeCategory[type];
            if (cat > 1) throw new Throwable("Type category " + cat +
            " not supported in this context");
            numpt = numberOfValues/_stride;
            if ((numpt*_stride) <  numberOfValues) numpt++;
        }
        double [] data = new double[numpt];
        //int size = var.getDataItemSize();
        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        int next = 0;
        for (int blk = 0; blk < locations.size(); blk++) {
            int [] loc = (int [])locations.elementAt(blk);
            int first = loc[0];
            int last = loc[1];
            ByteBuffer bv = positionBuffer((CDFImpl)thisCDF, var, loc[2],
                (last - first + 1));
            Method method;
            int n = first % _stride;
            if (n == 0) {
                n = first;
            } else {
                n = (first - n) + _stride;
            }
            int pos = (n - first);
            switch (DataTypes.typeCategory[type]) {
            case 0:
                FloatBuffer bvf = bv.asFloatBuffer();
                for (; pos <= last; pos += _stride) {
                    data[next++] = bvf.get(pos);
                }
                break;
            case 1:
                DoubleBuffer bvd = bv.asDoubleBuffer();
                for (; pos <= last; pos += _stride) {
                    data[next++] = bvd.get(pos);
                }
                break;
            case 2:
                method = DataTypes.method[type];
                for (; n <= last; n += _stride) {
                    bv.position(4*n);
                    Number num = (Number)method.invoke(bv, new Object[] {});
                    data[next++] = num.doubleValue();
                }
                break;
            case 3:
                method = DataTypes.method[type];    
                long longInt = DataTypes.longInt[type];
                for (; n <= last; n += _stride) {
                    bv.position(4*n);
                    Number num = (Number)method.invoke(bv, new Object[] {});
                    int x = num.intValue();
                    data[next++] = (x >= 0)?(double)x:(double)(longInt + x);
                }
            }
        }
        if (!var.recordVariance()) {
            for (int i = 1; i < numpt; i++) {
                data[i] = data[0];
            }
        }
        return data;
    }
    public static double [] getElement1(CDF thisCDF, Variable var, Integer idx,
        Stride strideObject) throws Throwable {
        int element = idx.intValue();
        int nv = var.getNumberOfValues();
        if (nv == 0) return null;
        if (!var.recordVariance()) nv = 1;
        if (!validElement(var, new int[] {element})) return null;
        int type = var.getType();
        int numpt = nv;
        int _stride = strideObject.getStride(nv);
        if (_stride != 1) {
            int cat = DataTypes.typeCategory[type];
            if (cat > 1) throw new Throwable("Type category " + cat +
            " not supported in this context");
            numpt = nv/_stride;
            if ((numpt*_stride) <  nv) numpt++;
        }
        double [] data = new double[numpt];
        int size = var.getDataItemSize();
        int advance = size*_stride;

        int loff = element*DataTypes.size[type];
        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        int point = 0;
        for (int blk = 0; blk < locations.size(); blk++) {
            int [] loc = (int [])locations.elementAt(blk);
            int first = loc[0];
            int last = loc[1];
            ByteBuffer bv = positionBuffer((CDFImpl)thisCDF, var, loc[2],
                (last - first + 1));
            Method method;
            int n = first % _stride;
            if (n == 0) {
                n = first;
            } else {
                n = (first - n) + _stride;
            }
            int pos = bv.position() + (n - first)*size + loff;
            switch (DataTypes.typeCategory[type]) {
            case 0:
                while (n <= last) {
                    data[point++] = bv.getFloat(pos);
                    n += _stride;
                    pos += advance;
                }
                break;
            case 1:
                while (n <= last) {
                    data[point++] = bv.getDouble(pos);
                    n += _stride;
                    pos += advance;
                }
                break;
            case 2:
                int res = doSignedInteger(bv, pos, type, size, n, last, data,
                new int[]{_stride}, point);
                point = res;
                break;
            case 3:
                res = doUnsignedInteger(bv, pos, type, size, n, last, data,
                new int[]{_stride}, point);
                point = res;
                break;
            }
        }
        return data;
    }
    static int doSignedInteger(ByteBuffer bv, int pos, int type,
        int size, int first, int last, double[] data, int[] stride,
        int point) throws IllegalAccessException, InvocationTargetException {
        Method method = DataTypes.method[type];
        int index = point;
        bv.position(pos);
        int _stride = stride[0];
        int advance = _stride*size;
        int n = first;
        while (n <= last) {
            Number num = (Number)method.invoke(bv, new Object[] {});
            data[index++] = num.doubleValue();
            n += _stride;
            pos += advance;
            bv.position(pos);
        }
        return index;
    }
    static int doUnsignedInteger(ByteBuffer bv, int pos, int type,
        int size, int first, int last, double[] data, int[] stride,
        int point) throws IllegalAccessException, InvocationTargetException {
        Method method = DataTypes.method[type];
        long longInt = DataTypes.longInt[type];
        int index = point;
        bv.position(pos);
        int _stride = stride[0];
        int advance = _stride*size;
        int n = first;
        while (n <= last) {
            Number num = (Number)method.invoke(bv, new Object[] {});
            int x = num.intValue();
            data[index++] = (x >= 0)?(double)x:(double)(longInt + x);
            n += _stride;
            pos += advance;
            bv.position(pos);
        }
        return index;
    }
    public static double [] getRange0(CDF thisCDF, Variable var, Integer istart,
        Integer iend, Stride strideObject) throws Throwable {
        int begin = istart.intValue();
        if (begin < 0) throw new Throwable("getRange0 start < 0");
        int end = iend.intValue();
        int nv = var.getNumberOfValues();
        if (end > nv) throw new Throwable("getRange0 end > available " + nv);
        if (nv == 0) return null;
        if (!var.recordVariance()) nv = 1;
        nv = end - begin + 1;
        int _stride = strideObject.getStride(nv);
        if (_stride > 1) {
            int numpt = nv/_stride;
            if ((numpt*_stride) <  nv) numpt++;
            nv = numpt;
        }
        int type = var.getType();
        int itemSize = var.getDataItemSize();
        double [] data = new double[nv];

        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        int [] blks =
            getBlockRange(locations, var.recordVariance(), begin, end);
        int firstBlock = blks[0];
        int lastBlock = blks[1];
        int index = 0;
        for (int blk = firstBlock; blk <= lastBlock; blk++) {
            Object[] oa = positionBuffer((CDFImpl)thisCDF, var, blks, blk,
                begin, end);
            ByteBuffer bv = (ByteBuffer)oa[0];
            int first = ((Integer)oa[1]).intValue();
            int last = ((Integer)oa[2]).intValue() - first;
            int n;
            if (_stride > 1) {
                if (blk > firstBlock) {
                    int elapsed = first - begin;
                    if ((elapsed  % _stride) > 0) {
                        n = _stride - ((first - begin) % _stride);
                        int pos = bv.position() + n*itemSize;
                        bv.position(pos);
                        last -= n;
                    }
                }
            }
            n = 0;
            Method method;
            switch (DataTypes.typeCategory[type]) {
            case 0:
                FloatBuffer bvf = bv.asFloatBuffer();
                for (; n <= last; n += _stride) {
                    data[index++] = bvf.get(n);
                }
                break;
            case 1:
                DoubleBuffer bvd = bv.asDoubleBuffer();
                for (; n <= last; n += _stride) {
                    data[index++] = bvd.get();
                }
                break;
            case 2:
                method = DataTypes.method[type];
                for (; n <= last; n += _stride) {
                    Number num = (Number)method.invoke(bv, new Object[] {});
                    data[index++] = num.doubleValue();
                }
                break;
            case 3:
                method = DataTypes.method[type];
                long longInt = DataTypes.longInt[type];
                for (; n <= last; n += _stride) {
                    Number num = (Number)method.invoke(bv, new Object[] {});
                    int x = num.intValue();
                    data[index++] = (x >= 0)?(double)x:(double)(longInt + x);
                }
            }
        }
        if (!var.recordVariance()) {
            for (int i = begin; i <= end; i += _stride) {
                data[i - begin] = data[0];
            }
        }
        return data;
    }
    public static double [] getRangeForElement1(CDF thisCDF, Variable var,
        Integer istart, Integer iend, Integer ielement, Stride strideObject)
        throws Throwable {
        int element = ielement.intValue();
        if (!validElement(var, new int[] {element})) return null;
        int begin = istart.intValue();
        int end = iend.intValue();
        int nv = var.getNumberOfValues();
        if (end > nv) throw new Throwable("getRange0 end > available " + nv);
        if (nv == 0) return null;
        if (!var.recordVariance()) nv = 1;
        nv = end - begin + 1;
        int _stride = strideObject.getStride(nv);
        if (_stride > 1) {
            int numpt = nv/_stride;
            if ((numpt*_stride) <  nv) numpt++;
            nv = numpt;
        }
        int type = var.getType();
        int itemSize = var.getDataItemSize();
        int advance = itemSize*_stride;
        double [] data = new double[nv];
        int loff = element*DataTypes.size[type];
        Vector locations = ((CDFImpl.DataLocator)var.getLocator()).locations;
        int [] blks =
            getBlockRange(locations, var.recordVariance(), begin, end);
        int firstBlock = blks[0];
        int lastBlock = blks[1];
        int index = 0;
        for (int blk = firstBlock; blk <= lastBlock; blk++) {
            Object[] oa = positionBuffer((CDFImpl)thisCDF, var, blks, blk,
                begin, end);
            ByteBuffer bv = (ByteBuffer)oa[0];
            int first = ((Integer)oa[1]).intValue();
            int last = ((Integer)oa[2]).intValue();
            int pos = bv.position() + loff;
            int n = first;
            if (_stride > 1) {
                if (blk > firstBlock) {
                    int elapsed = first - begin;
                    if ((elapsed % _stride) != 0) {
                        n = first + _stride - (elapsed % _stride);
                        pos += (n - first)*itemSize;
                    }
                }
            }
            switch (DataTypes.typeCategory[type]) {
            case 0:
                for (; n <= last; n += _stride) {
                    data[index++] = bv.getFloat(pos);
                    pos += advance;
                }
                break;
            case 1:
                for (; n <= last; n += _stride) {
                    data[index++] = bv.getDouble(pos);
                    pos += advance;
                }
                break;
            case 2:
                index = doSignedInteger(bv, pos, type, itemSize, n, last,
                     data, index, new int[]{_stride});
                break;
            case 3:
                index = doUnsignedInteger(bv, pos, type, itemSize, n, last,
                     data, index, new int[]{_stride});
                break;
            }
        }
        if (!var.recordVariance()) {
            int i = begin;
            int n = 0;
            while (i <= end) {
                data[n++] = data[0];
                i += _stride;
            }
        }
        return data;
    }
    static int doSignedInteger(ByteBuffer bv, int pos, int type,
        int size, int first, int last, double[] data, int index,
        int[] stride) throws IllegalAccessException, InvocationTargetException {
        Method method = DataTypes.method[type];
        bv.position(pos);
        for (int n = first; n <= last; n += stride[0]) {
            Number num = (Number)method.invoke(bv, new Object[] {});
            data[index++] = num.doubleValue();
            pos += size;
            bv.position(pos);
        }
        return index;
    }
    static int doUnsignedInteger(ByteBuffer bv, int pos, int type,
        int size, int first, int last, double[] data, int index,
        int[] stride) throws IllegalAccessException, InvocationTargetException {
        Method method = DataTypes.method[type];
        long longInt = DataTypes.longInt[type];
        bv.position(pos);
        for (int n = first; n <= last; n += stride[0]) {
            Number num = (Number)method.invoke(bv, new Object[] {});
            int x = num.intValue();
            data[index++] = (x >= 0)?(double)x:(double)(longInt + x);
            pos += size;
            bv.position(pos);
        }
        return index;
    }
    /**
     * Loss of precision may occur if type of var is LONG
     * times obtained are millisecond since 1970 regardless of the
     * precision of time variable corresponding to variable var
     */
    public static class GeneralTimeSeries implements TimeSeries {
        double [] vdata;
        double [] times;
        TimeSpec tspec;
        public GeneralTimeSeries(CDF thisCDF, Variable var, Integer which,
            Boolean ignoreFill, double[] timeRange, TimeSpec ts) throws
            Throwable {
            boolean ignore = ignoreFill.booleanValue();
            int [] recordRange = null;
            if ( ts!=null ) {
                synchronized (ts) {
                    tspec = (TimeSpec)ts.clone();
                }
            } else {
                tspec= null;
            }
            times = ((CDFImpl)thisCDF).getTimes(var, tspec, false);
            if (times == null) throw new Throwable("times not available for " +
                var.getName());
            double[] stimes;
            boolean longType = false;
            int type = var.getType();
            if (DataTypes.typeCategory[type] == DataTypes.LONG) {
                longType = true;
            }
            Object o = null;
            if (timeRange == null) {
                o = (which == null)?getSeries0(thisCDF, var):
                    getElement1(thisCDF, var, which);
            } else {
                recordRange = getRecordRange(thisCDF, var, timeRange); 
                if (recordRange == null) throw new Throwable("no record range");
                if (which == null) {
                    o = getRange0(thisCDF, var, recordRange[0],
                                  recordRange[1]);
                } else {
                    o = getRangeForElement1(thisCDF, var,
                    recordRange[0], recordRange[1],
                    which);
                }
            }
            vdata = castToDouble(o, longType);
            if (!ignore) {
                if (timeRange != null) {
                    stimes = new double[vdata.length];
                    System.arraycopy(times, recordRange[0], stimes, 0,
                    vdata.length);
                    times = stimes;
                }
            } else {
                // fill values need to be filtered
                double [] fill = getFillValue(thisCDF, var);
                int first = (timeRange != null)?recordRange[0]:0;
                if (fill[0] != 0) { // there is no fill value
                    stimes = new double[vdata.length];
                    System.arraycopy(times, first, stimes, 0, vdata.length);
                    times = stimes;
                } else {
                    filterFill(times, vdata, fill[1], first);
                }
            }
        }
        public double[] getTimes() {return times;}
        public double[] getValues() {return vdata;}
        public TimeSpec getTimeSpec() {return tspec;}
    }

    public static TimeSeries getTimeSeriesObject0(CDF thisCDF, Variable var,
        Boolean ignoreFill, double[] timeRange, TimeSpec ts) throws Throwable {
        return new GeneralTimeSeries(thisCDF, var, null, ignoreFill, timeRange,
           ts);
    }

    public static TimeSeries getTimeSeriesObject1(CDF thisCDF, Variable var,
        Integer which, Boolean ignoreFill, double[] timeRange, TimeSpec ts)
        throws Throwable {
        return new GeneralTimeSeries(thisCDF, var, which, ignoreFill, timeRange,
           ts);
    }
}
