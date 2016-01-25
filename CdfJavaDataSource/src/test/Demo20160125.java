
package test;

import gov.nasa.gsfc.spdf.cdfj.CDFException;
import gov.nasa.gsfc.spdf.cdfj.CDFReader;
import gov.nasa.gsfc.spdf.cdfj.ReaderFactory;
//import org.autoplot.bufferdataset.BufferDataSet;

/**
 * Demonstrates a bug where small CDF files are not loaded properly with 
 * ReaderFactory.getReader("/tmp/"+file) call.
 * @author jbf
 */
public class Demo20160125 {
    public static void main( String[] args ) throws CDFException.ReaderError {

              
        //System.err.println("shouldAllocateDirect="+BufferDataSet.shouldAllocateDirect() ); 
        System.err.println("os.arch=" + System.getProperty("os.arch") );
        
        boolean allocateDirect= false;
        //wget http://autoplot.org/data/autoplot.cdf
        String file= "autoplot.cdf";
        
        //wget http://emfisis.physics.uiowa.edu/Flight/RBSP-A/L2/2014/07/10/rbsp-a_HFR-spectra_emfisis-L2_20140710_v1.3.3.cdf
        //String file= "rbsp-a_HFR-spectra_emfisis-L2_20140710_v1.3.3.cdf"; 
           
        CDFReader cdf;
        if ( !allocateDirect ) {
            cdf= ReaderFactory.getReader("/tmp/"+file);
        } else {
            cdf= new CDFReader("/tmp/"+file );
        }
        
        System.err.println("Here works");
        System.err.println("Number of variables: " + cdf.getVariableNames().length);
        
    }
}
