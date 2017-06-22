
package test;

import gov.nasa.gsfc.spdf.cdfj.CDFReader;
import gov.nasa.gsfc.spdf.cdfj.ReaderFactory;
import java.nio.ByteBuffer;
import java.util.Locale;
import org.das2.qds.buffer.BufferDataSet;

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

        System.gc();
        System.gc();
        System.gc();
        
        
        System.err.println( "====" );
        System.err.println( "before totalMemory=" + Runtime.getRuntime().totalMemory() );
        System.err.println( "before freeMemory=" + Runtime.getRuntime().freeMemory() );

        CDFReader cdf;
        if ( !allocateDirect ) {
            cdf= ReaderFactory.getReader("/tmp/c1_waveform_wbd_200404032100_v01.cdf");
        } else {
            cdf= new CDFReader("/tmp/c1_waveform_wbd_200404032100_v01.cdf");
        }

        System.err.println( "allocateDirect=" + allocateDirect );
        
        ByteBuffer buff= cdf.getBuffer( "WBD_Mag", "double", new int[] { 0, 16465368 }, true );
        System.err.println("cdf.getBuffer WBD_Mag = "+buff);
        
        BufferDataSet bds= BufferDataSet.makeDataSet( 1, 8, 0, 16465368+1, 1, 1, 1, buff, BufferDataSet.DOUBLE );
        System.err.println( String.format(  Locale.US, "%f %f", bds.value(695420), bds.value(bds.length()-1) ) );
        
        ByteBuffer buff2= cdf.getBuffer( "Epoch", "double", new int[] { 0, 16465368 }, true );
        System.err.println("cdf.getBuffer Epoch = "+buff2);
        
        System.err.println( "after totalMemory=" + Runtime.getRuntime().totalMemory() );
        System.err.println( "after freeMemory=" + Runtime.getRuntime().freeMemory() );
    }
}
