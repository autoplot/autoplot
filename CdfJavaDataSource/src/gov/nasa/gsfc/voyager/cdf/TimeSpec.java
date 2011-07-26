package gov.nasa.gsfc.voyager.cdf;
public interface TimeSpec extends java.lang.Cloneable {
    public double getBaseTime();
    public int getBaseTimeUnits();
    public int getOffsetUnits();
    public Object clone();
}
