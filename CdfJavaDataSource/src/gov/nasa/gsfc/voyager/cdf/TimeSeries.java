package gov.nasa.gsfc.voyager.cdf;
public interface TimeSeries {
    public double[] getTimes();
    public double[] getValues();
    public TimeSpec getTimeSpec();
}
