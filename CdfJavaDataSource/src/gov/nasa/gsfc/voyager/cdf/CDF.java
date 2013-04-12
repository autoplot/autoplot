package gov.nasa.gsfc.voyager.cdf;
import java.nio.*;
import java.util.*;
/**
 * Interface that defines methods for getting attributes, variable
 * characteristics, and data from a CDF 
 */
public interface CDF {
    /**
     * returns the time series of the specified scalar variable.
     * A double[2][] array is returned. The 0th element is the
     * array containing times, and the 1st element is the array containing
     * corresponding values. If a fill value has been specified for this
     * variable via the FILLVAL attribute, then points where the value is
     * equal to fill value are excluded.
     * For numeric variables of dimension other than 0, and for
     * character string variables an  exception is thrown.
     */
    public Object getTimeSeries(String vname) throws Throwable;

    /**
     * returns the time series of the specified element of a 1 dimensional
     * variable. A double[2][] array is returned. The 0th element is the
     * array containing times, and the 1st element is the array containing
     * corresponding values. If a fill value has been specified for this
     * variable via the FILLVAL attribute, then points where the value is
     * equal to fill value are excluded.
     * For numeric variables of dimension other than 1, and for
     * character string variables an  exception is thrown.
     */
    public Object getTimeSeries(String vname, int element) throws Throwable;

    /**
     * returns the time series of the specified scalar variable in the
     * specified time range, optionally ignoring points whose value equals
     * fill value.
     * A double[2][] array is returned. The 0th element is the
     * array containing times, and the 1st element is the array containing
     * corresponding values. If a fill value has been specified for this
     * variable via the FILLVAL attribute, then if ignoreFill has the value
     * true, points where the value is equal to fill value are excluded.
     * For numeric variables of dimension other than 0, and for
     * character string variables an  exception is thrown.
     */
    public Object getTimeSeries(String vname, boolean ignoreFill,
        double[] timeRange) throws Throwable;

    public Object getTimeSeries(String vname, boolean ignoreFill,
        double startTime, double stopTime) throws Throwable;

    public Object getTimeSeries(String vname, boolean ignoreFill,
        Date startDate, Date stopDate) throws Throwable;

    /**
     * returns the time series of the specified element of a 1 dimensional
     * variable for the specified time range, optionally ignoring points whose
     * value equals fill value.
     * A double[2][] array is returned. The 0th element is the
     * array containing times, and the 1st element is the array containing
     * corresponding values. If a fill value has been specified for this
     * variable via the FILLVAL attribute, then if ignoreFill has the value
     * true, points where the value is equal to fill value are excluded.
     * For numeric variables of dimension other than 0, and for
     * character string variables an  exception is thrown.
     */
    public Object getTimeSeries(String vname, int element, boolean ignoreFill,
        double[] timeRange) throws Throwable;

    public Object getTimeSeries(String vname, int element, boolean ignoreFill,
        double startTime, double stopTime) throws Throwable;

    public Object getTimeSeries(String vname, int element, boolean ignoreFill,
        Date startDate, Date stopDate) throws Throwable;

    /**
     * Returns ByteOrder.LITTLE_ENDIAN, or ByteOrder.BIG_ENDIAN depending
     * the CDF encoding
     */
    public ByteOrder getByteOrder();

    /**
     * Returns whether the arrays are stored in row major order in the source
     */
    public boolean rowMajority();

    /**
     * Returns names of variables in the CDF
     */
    public String [] getVariableNames();

    /**
     * Returns the object that implements the {@link Variable} interface for
     * the named variable
     */
    public Variable getVariable(String name);

    /**
     * returns variable names of a given VAR_TYPE in a String[]
     */
    public String [] getVariableNames(String type);

    /**
     * returns names of global attributes.
     */
    public String [] globalAttributeNames();

    /**
     * returns names of variable attributes.
     */
    public String [] variableAttributeNames(String name);

    /**
     * returns value of the named global attribute.
     * For a  character string attribute, a Vector of String is returned
     * For a  numeric attribute, a long[] is returned for long type;
     * double[] is returned for all other numeric types.
     */
    public Object getAttribute(String atr);

    /**
     * returns value of the named attribute for specified variable.
     * For a  character string attribute, a String[] is returned
     * For a  numeric attribute, a long[] is returned for long type;
     * double[] is returned for all other numeric types.
     */
    public Object getAttribute(String vname, String aname);

    /**
     * returns times as millisecond since Jan 1, 1970. Times correspond
     * to values in the variable pointed to by the 'DEPEND_0' attribute
     * of the named variable. If 'DEPEND_0' attribute is not available
     * for the named variable, 'Epoch' is assumed to contain the epoch.
     */
    public double [] getTimes(String vname) throws Throwable;

    /**
     * returns times corresponding to the specfied range of records as
     * millisecond since Jan 1, 1970. Times correspond
     * to values in the variable pointed to by the 'DEPEND_0' attribute
     * of the named variable. If 'DEPEND_0' attribute is not available
     * for the named variable, 'Epoch' is assumed to contain the epoch.
     */
    public double [] getTimes(String vname, int[] recordRange) throws
        Throwable;

    public double [] getTimes(String vname, int firstRecord,
        int lastRecord) throws Throwable;

    public double [] getAvailableTimeRange(String vname) throws Throwable;

    /**
     * returns range of records corresponding to the specified time range. 
     */
    public int[] getRecordRange(String vname, double[] timeRange) throws
        Throwable;

    public int[] getRecordRange(String vname, double startTime,
        double stopTime) throws Throwable;

    public int[] getRecordRange(String vname, Date startDate,
        Date stopDate) throws Throwable;

    /**
     * returns value for the given variable.
     * Type of object returned depends on the number of varying dimensions
     * and type of the CDF variable.
     * <p>For a numeric variable,
     * a double[], double[][], double[][][], or double[][][][] object is
     * returned for
     * scalar, one-dimensional, two-dimensional, or three-dimensional
     * variable respectively.
     * <p>For a character string variable, a String[] or a String[][]
     * object is returned for a scalar or one-dimensional variable.
     * For numeric variables of dimension higher than 3, and for
     * character string variables of dimension higher than 1 an
     * exception is thrown.
     */
    public Object get(String varName) throws Throwable;

    /**
     * returns values of the nth element of a 1 dimensional variable.
     * <p>For a numeric variable, a double[]  is returned
     * <p>If the effective dimension of specified variable is not equal to 1,
     * or the variable is character string type, a Throwable is thrown.
     */
    public Object get(String vname, int n) throws Throwable;

    /**
     * returns values of specified elements of a 1 dimensional variable.
     * <p>If the effective dimension of specified variable is not equal to 1,
     * or the variable is character string type, a Throwable is thrown.
     */
    public Object get(String vname, int[] elements) throws Throwable;

    /**
     * returns values for the (m, n) element of a two dimensionable
     * variable.
     * <p>For a numeric variable, a double[]  is returned
     * <p>If the effective dimension of specified variable is not equal to 2,
     * or the variable is character string type, a Throwable is thrown.
     */
    public Object get(String vname, int m, int n) throws Throwable;

    /**
     * returns values for the specified element of a one dimensional
     * variable from the specied range of records.
     * <p>For a numeric variable, a double[]  is returned
     * <p>If the effective dimension of specified variable is not equal to 1,
     * or the variable is character string type, a Throwable is thrown.
     */
    public Object get(String vname, int first, int last, int element) throws
       Throwable;

    /**
     * returns values for the specified elements of a one dimensional
     * variable from the specied range of records.
     * <p>For a numeric variable, a double[last - first + 1][elements.length]
     *  is returned
     * <p>If the effective dimension of specified variable is not equal to 1,
     * or the variable is character string type, a Throwable is thrown.
     */
    public Object get(String vname, int first, int last, int[] elements) throws
       Throwable;


    /**
     * returns value of the given variable at the specified point.
     * <p>For a numeric variable,
     * a Double, double[], double[][], or double[][][] object  is returned for
     * scalar, one-dimensional, two-dimensional, or 3-dimensional
     * variable respectively.
     * <p>For a character string variable, a String or a String[]
     * is returned for a scalar or one-dimensional variable.
     * For numeric variables of dimension higher than 3, and for
     * character string variables of dimension higher than 1 a
     * Throwable is thrown.
     */
    public Object getPoint(String vname, int index) throws Throwable;

    /**
     * returns value of the given variable in the specified range of points.
     * <p>For a numeric variable,
     * a double[], double[][], or double[][][] object  is returned for
     * scalar, one-dimensional or two-dimensional variable respectively.
     * For numeric variables of dimension higher than 2, and for
     * character string variables an
     * exception is thrown.
     */
    public Object getRange(String vname, int index1, int index2) throws
        Throwable;

    /**
     * returns value of the specified element of the given 1 dimensional
     * variable in the specified range of points.
     * For numeric variables of dimension other than 1, and for
     * character string variables an  exception is thrown.
     */
    public Object getRange(String vname, int index1, int index2,
        int element) throws Throwable;

    /**
     * returns values of the specified elements of the given 1 dimensional
     * variable in the specified range of points.
     * For numeric variables of dimension other than 1, and for
     * character string variables an  exception is thrown.
     */
    public Object getRange(String vname, int index1, int index2,
        int[] elements) throws Throwable;

    /**
     * returns values of 1 dimensional variable.
     * May be faster than get(name) where cdf was written in large
     * blocks.
     */
    public double[] get1D(String varName) throws Throwable;

    /**
     * accessor that can preserve type.
     * @param varName
     * @param preserve true will preserve type, false will convert to double array
     * @return
     * @throws Throwablel
     */
    public Object get1D( String varName, boolean preserve ) throws Throwable;

    /**
     * accessor that can preserve type.
     * @param varName
     * @param preserve true will preserve type, false will convert to double array
     * @return
     * @throws Throwablel
     */
    public Object get1DSlice1( String varName,int slice1, boolean preserve ) throws Throwable;

    /**
     * returns value of 1 dimensional variable at the specified point.
     */
    public double[] get1D(String varName, int point) throws Throwable;

    /**
     * returns values of 1 dimensional variable for the specified 
     * range of points.
     */
    public double[] get1D(String varName, int first, int last) throws Throwable;
    /**
     * experimental
     */
    public Object getTimeSeries(String vname, int element, boolean ignoreFill,
        double[] timeRange, int[] stride) throws Throwable;
    public Object getTimeSeries(String vname, boolean ignoreFill,
        double[] timeRange, int[] stride) throws Throwable;
    //TODO: need preserve with stride

    public double[] get1D(String varName, int first, int last, int[] stride)
        throws Throwable;
    public double [] getTimes(String vname, int[] recordRange,
        int[] stride) throws Throwable;
}
