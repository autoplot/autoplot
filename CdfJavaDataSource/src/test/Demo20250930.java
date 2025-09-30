
package test;

import gov.nasa.gsfc.spdf.cdfj.CDFException;
import gov.nasa.gsfc.spdf.cdfj.CDFReader;
import gov.nasa.gsfc.spdf.cdfj.TimeSeries;

/**
 * Demo the bug where UINT4 could not be read in with the 20150401 release.
 * @author faden@cottagesystems.com
 */
public class Demo20250930 {
    public static void main( String[] args ) throws CDFException.ReaderError {
        CDFReader cdf= new CDFReader("/home/jbf/ct/jenkins/data/cdf/rvar/ac_or_ssc_00000000_v01.cdf");
        TimeSeries ts= cdf.getTimeSeries("XYZ_GSE"); // zVariable
        System.err.println("ts.getTimes().length="+ts.getTimes().length);
        ts= cdf.getTimeSeries("RADIUS"); // rVariable
        System.err.println("ts.getTimes().length="+ts.getTimes().length);
        
        
    }
    
}
