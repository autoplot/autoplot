/*
 * Main.java
 *
 * Created on October 1, 2007, 11:35 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.cdfdatasource;

import gsfc.nssdc.cdf.CDF;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Properties;

/**
 *
 * @author jbf
 */
public class Main {
    
    /** Creates a new instance of Main */
    public Main() {
    }
    
    private static boolean cdfLoaded= false;
    
    static {
        System.err.println("java.library.path="+System.getProperty("java.library.path") );
        try {
            System.err.println("" + new File(".").getCanonicalPath());
        } catch (IOException iOException) {
        }
        dumpProperties();
        if ( ! cdfLoaded ) loadCdfLibraries();
    }
    
    private static void dumpProperties() {
        Properties p= System.getProperties();
        for ( Iterator i= p.keySet().iterator(); i.hasNext(); ) {
            Object k= i.next();
            System.err.println( k+"="+p.get(k) );
        }
    }
    
    
    private static void loadCdfLibraries() {
        // CDF native library names ID'd by system property, set in the jnlp
        // Bernie Harris states in an email:
        // "The reason for this is that CDF is actually two native libraries.  The original 
        //  CDF library and the JNI native library that provides the Java interface.  The 
        //  JNLP specification doesn't exactly specify what should happen when one native 
        //  library (the JNI lib) depends upon another (the original CDF lib).  Things 
        //  are further complicated because the CDF libraries have different names on 
        //  different platforms and different platforms behave differently."
        String cdfLib1 = System.getProperty("cdfLib1");        
        String cdfLib2 = System.getProperty("cdfLib2");
        
        if ( cdfLib1==null && cdfLib2==null ) {
            System.err.println("System properties for cdfLib not set, setting up for debugging");
            String os= System.getProperty("os.name");
            if ( os.startsWith("Windows") ) {
                cdfLib1= "dllcdf";
                cdfLib2= "cdfNativeLibrary";
            } else {
                cdfLib2= "cdfNativeLibrary";
            }
        }
        
        if (cdfLib1 != null) System.loadLibrary(cdfLib1);
        if (cdfLib2 != null) System.loadLibrary(cdfLib2);
        
    }
    
    
    public static void main( String[] args ) throws Exception {
        
        System.err.println("java.library.path="+System.getProperty("java.library.path") );

        String file;
        if ( args.length==0 ) {
            file= "/net/spot3/mnt/data1/jbf_scratch/papco_data/cdf/ace/swe/2005/ac_k0_swe_20051017_v01.cdf";
        } else {
            file= args[0];
        }
        
        System.err.println( "CDF version= "+CDF.getLibraryVersion() );
        CDF cdf= CDF.open( file, CDF.READONLYon );
        System.err.println("cdf.getNumVars()="+cdf.getNumVars());
        System.err.println("cdf.getNumAttrs()="+cdf.getNumAttrs());
        cdf.close();
    }
    
}
