package gov.nasa.gsfc.voyager.cdf;
/**
 * Interface that defines methods for getting  properties of
 * a CDF variable.
 */
public interface Variable {

    /**
     * Determines whether the value of this variable is the same at
     * all time points. 
     * returns true if value may change, false otherwise
     */
    public abstract boolean recordVariance();

    /**
     * Determines whether the value of this variable is represented as
     * a compressed byte sequence in the CDF.
     */
    public abstract boolean isCompressed();

    /**
     * Determines whether the value of this variable is presented in
     * a row-major order in the CDF.
     */
    public abstract boolean rowMajority();

    /**
     * Gets the name of this of this variable
     */
    public abstract String getName();

    /**
     * Gets the type of values of the variable.
     * Supported types are defined in the CDF Internal Format Description
     */
    public abstract int getType();

    /**
     * Gets the size of an item (defined as number of bytes needed to
     * represent the value of this variable at a point).
     */
    public abstract int getDataItemSize();

    /**
     * Gets the sequence number of the variable inside the CDF. 
     */
    public abstract int getNumber();

    /**
     * Gets the number of elements (of type returned by getType()).
     */
    public abstract int getNumberOfElements();

    /**
     * Gets the number of values (size of time series)
     */
    public abstract int getNumberOfValues();

    /**
     * Gets the values that represent a padded instance
     * This feature is not used frequently. Most often the value of FILLVAL
     * attribute for the variable is used for this purpose.
     */
    public abstract double[] getPadValue();

    /**
     * Gets the dimensions.
     */
    public abstract int[] getDimensions();

    /**
     * Gets the dimensional variance. This determines the effective
     * dimensionality of values of the variable.
     */
    public abstract boolean[] getVarys();

    /**
     * Gets a list of regions that contain data for the variable.
     * Each element of the vector describes a region as an int[3] array.
     * Array elements are: time series index (record) of first point
     * in the region, time series index (record) of last point in the region,
     * and offset of the start of region.
     */
    public abstract VariableDataLocator getLocator();
    public abstract int getEffectiveRank();

}
