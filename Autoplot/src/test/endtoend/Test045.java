/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.util.Map;
import org.autoplot.cdaweb.CDAWebDB;
import org.das2.util.monitor.NullProgressMonitor;


/**
 * Tests of cdaweb plugin.
 * @author jbf
 */
public class Test045 {
    
    public static void main( String[] args ) {
        
        try {
            long t0= System.currentTimeMillis();
            CDAWebDB db= CDAWebDB.getInstance();
            System.err.println("time get getInstance: "+ ( System.currentTimeMillis()-t0 ) +" ms" );
            
            t0= System.currentTimeMillis();
            db.maybeRefresh( new NullProgressMonitor() );
            System.err.println("time maybeRefresh: "+ ( System.currentTimeMillis()-t0 ) +" ms" );
            
            t0= System.currentTimeMillis();
            Map<String,String> dbs= db.getServiceProviderIds();
            System.err.println("time getServiceProviderIds: "+ ( System.currentTimeMillis()-t0 ) +" ms" );
            
            System.err.println("dbs.size(): " +dbs.size());
            
            t0= System.currentTimeMillis();
            db.getMasterFile( "voyager1_1hr_mag", new NullProgressMonitor() );
            System.err.println("time getMasterFile: "+ ( System.currentTimeMillis()-t0 ) +" ms" );
            
            
        } catch ( Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }

    }
}
