/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import org.autoplot.AutoplotDataServer;

/**
 * Tests of the AutoplotDataServer.  Note that this is only a simulation, for
 * example each call to the AutoplotDataServer is typically started from a 
 * fresh session.  On http://sarahandjeremy.net:8080/hudson/job/autoplot-test036
 * this is implemented as a shell script.
 * @author jbf
 */
public class Test036 {
    
    private static void testit( int id, String uri, String tr, String format ) {
        String outFile= String.format( "test036_%03d."+format, id );
        String[] args= new String[] { 
            "-u", uri, "-t", tr, "-f", format, "-o", outFile, "--noexit"
        };
        try {
            AutoplotDataServer.main(args);
            
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }
    public static void main( String[] args ) {
        // note Test036 is implemented as a shell script, and this is for debugging.
        testit( 7, 
                "https://emfisis.physics.uiowa.edu/Flight/RBSP-A/Quick-Look/$Y/$m/$d/rbsp-a_HFR-spectra_emfisis-Quick-Look_$Y$m$d_v$(v,sep).cdf?HFR_Spectra",
                "2013-03-01/2013-03-05",
                "qds" );

    }
}
