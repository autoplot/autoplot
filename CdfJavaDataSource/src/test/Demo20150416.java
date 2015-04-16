
package test;

import gov.nasa.gsfc.spdf.cdfj.CDFException;
import gov.nasa.gsfc.spdf.cdfj.CDFReader;
import gov.nasa.gsfc.spdf.cdfj.TimeSeries;
import java.nio.ByteBuffer;

/**
 * Demo the bug where UINT4 could not be read in with the 20150401 release.
 * @author faden@cottagesystems.com
 */
public class Demo20150416 {
    public static void main( String[] args ) throws CDFException.ReaderError {
        CDFReader cdf= new CDFReader("/home/jbf/ct/hudson/data/cdf/rbsp/lanl/rbspa_ect-hope-sci-L1_20140102_v2.0.0.cdf");
        TimeSeries ts= cdf.getTimeSeries("Detector");
        System.err.println("ts.getTimes().length="+ts.getTimes().length);
        ByteBuffer buff= cdf.getBuffer( "Detector", "long", new int[] { 0, 100 }, true );
        System.err.println("cdf.getBuffer="+buff);
        
    }
    
}
