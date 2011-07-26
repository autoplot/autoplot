package gov.nasa.gsfc.voyager.cdf;
public interface TimeVariable {
    public String getName();
    public int getPrecision();
    public double [] getTimes();
    public double[] getTimes(TimeSpec ts) throws Throwable;
    public double [] getTimes(int[] recordRange);
    public double [] getTimes(int[] recordRange, TimeSpec ts);
    public double [] getTimes(double[] timeRange);
    public double [] getTimes(double[] timeRange, TimeSpec ts);
    public int[] getRecordRange(double[] timeRange, TimeSpec ts) throws
    Throwable;
}

