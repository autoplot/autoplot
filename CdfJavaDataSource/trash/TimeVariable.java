package gov.nasa.gsfc.voyager.cdf;
public interface TimeVariable {
    public String getName();
    public int getPrecision();
    public double [] getTimes();
    public double [] getTimes(int[] recordRange);
    public double [] getTimes(double[] timeRange);
    public double[][] getExtendedPrecisionTimes() throws Throwable;
    public int[] getRecordRange(double[] timeRange) throws Throwable;
}

