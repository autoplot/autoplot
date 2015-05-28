
package test;

import gov.nasa.gsfc.spdf.cdfj.CDFReader;
import gov.nasa.gsfc.spdf.cdfj.ReaderFactory;
import java.nio.ByteBuffer;

/**
 * Demo where the old library fails on 32bit machines, and that 
 * the 20150525 jar file fixes the problem.
 * @author faden@cottagesystems.com
 */
public class Demo20150525 {
    
    public static void main( String[] args ) throws Throwable {
        doIt(false);
        doIt(true);
    } 
    
    private static void doIt( boolean allocateDirect ) throws Throwable {
        
        //CDFReader cdf= new CDFReader("/home/jbf/ct/hudson/data/cdf/rbsp/lanl/rbspa_ect-hope-sci-L1_20140102_v2.0.0.cdf");
        
        // This file can be retrieved from http://cdaweb.gsfc.nasa.gov/data/cluster/c1/wbd/2004/04/c1_waveform_wbd_200404032100_v01.cdf
        // wget -O /tmp/c1_waveform_wbd_200404032100_v01.cdf http://cdaweb.gsfc.nasa.gov/data/cluster/c1/wbd/2004/04/c1_waveform_wbd_200404032100_v01.cdf
        CDFReader cdf;
        if ( !allocateDirect ) {
            cdf= ReaderFactory.getReader("/tmp/c1_waveform_wbd_200404032100_v01.cdf");
        } else {
            cdf= new CDFReader("/tmp/c1_waveform_wbd_200404032100_v01.cdf");
        }

        System.err.println( "allocateDirect=" + allocateDirect );
        
        ByteBuffer buff= cdf.getBuffer( "WBD_Mag", "double", new int[] { 0, 16465368 }, true );
        System.err.println("cdf.getBuffer WBD_Mag = "+buff);
        ByteBuffer buff2= cdf.getBuffer( "Epoch", "double", new int[] { 0, 16465368 }, true );
        System.err.println("cdf.getBuffer Epoch = "+buff2);
        
    }
}
