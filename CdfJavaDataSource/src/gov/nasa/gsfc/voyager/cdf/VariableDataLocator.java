package gov.nasa.gsfc.voyager.cdf;
public interface VariableDataLocator {  
    /**
     * Gets a list of regions that contain data for the variable.
     * Each element of the vector describes a region as an int[3] array.
     * Array elements are: time series index (record) of first point
     * in the region, time series index (record) of last point in the region,
     * and offset of the start of region.
     */
    public int[][] getLocations();
}
