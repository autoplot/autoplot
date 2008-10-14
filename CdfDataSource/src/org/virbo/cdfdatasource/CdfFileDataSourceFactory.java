/*
 * CdfFileDataSourceFactory.java
 *
 * Created on July 23, 2007, 8:29 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.cdfdatasource;

import gsfc.nssdc.cdf.CDF;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.URLSplit;

/**
 *
 * @author jbf
 */
public class CdfFileDataSourceFactory implements DataSourceFactory {
    
    private static Logger logger = Logger.getLogger("virbo.cdfdatasource");
    
    static {
        loadCdfLibraries();
    }
    /** Creates a new instance of CdfFileDataSourceFactory */
    public CdfFileDataSourceFactory() {
	
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
                System.err.println("no values set identifying cdf libraries, hope you're on a mac or linux!");
                System.err.println( System.getProperty("java.library.path" ));
                cdfLib2= "cdfNativeLibrary";
            }
        }
        
        try {
            if (cdfLib1 != null) System.loadLibrary(cdfLib1);
            if (cdfLib2 != null) System.loadLibrary(cdfLib2);
        } catch ( UnsatisfiedLinkError ex ) {
            ex.printStackTrace();
            System.err.println( System.getProperty("java.library.path" ));
            throw ex;
        }
        logger.info("cdf binaries loaded");
    }
    
    
    public DataSource getDataSource(URL url) throws Exception {
        URLSplit split= DataSetURL.parse( url.toString() );
        return new CdfFileDataSource( url );
    }
    
    
    public String editPanel(String surl) throws Exception {
        return surl;
    }
    
    public List<CompletionContext> getCompletions(CompletionContext cc, org.das2.util.monitor.ProgressMonitor mon) throws Exception {
        if ( cc.context.equals(CompletionContext.CONTEXT_PARAMETER_NAME) ) {
            String file= CompletionContext.get( CompletionContext.CONTEXT_FILE, cc );
            
            File cdfFile= DataSetURL.getFile( DataSetURL.getURL(file), mon );
            String fileName= cdfFile.toString();
            if ( System.getProperty("os.name").startsWith("Windows") ) fileName= CdfUtil.win95Name( cdfFile );
            
            logger.fine("opening cdf file "+fileName);
            CDF cdf= CDF.open( fileName, CDF.READONLYon );
            
            logger.fine("inspect cdf for plottable parameters");
            Map<String,String> result= CdfUtil.getPlottable( cdf, true , 3);
            
            logger.fine("close cdf");
            cdf.close();
            
            List<CompletionContext> ccresult= new ArrayList<CompletionContext>();
            for ( String key:result.keySet() ) {
                CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, 
                        key, this, "arg_0", result.get(key), null, true );
                ccresult.add(cc1);
            }
            return ccresult;
            
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            String parmname= CompletionContext.get( CompletionContext.CONTEXT_PARAMETER_NAME, cc );
            if ( parmname.equals("id") ) {
                String file= CompletionContext.get( CompletionContext.CONTEXT_FILE, cc );
                
                File cdfFile= DataSetURL.getFile( DataSetURL.getURL(file), mon );
                String fileName= cdfFile.toString();
                if ( System.getProperty("os.name").startsWith("Windows") ) fileName= CdfUtil.win95Name( cdfFile );
                
                CDF cdf= CDF.open( fileName, CDF.READONLYon );
                Map<String,String> result= CdfUtil.getPlottable( cdf, true, 3);
                cdf.close();
                
                List<CompletionContext> ccresult= new ArrayList<CompletionContext>();
                for ( String key:result.keySet() ) {
                    CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, key, this, null, key, result.get(key), true  );
                    ccresult.add(cc1);
                }
                
                return ccresult;
                
            } else {
                return Collections.emptyList();
            }
            
        } else {
            return Collections.emptyList();
        }
    }
    
    public boolean reject( String surl, ProgressMonitor mon ) {
        return ! surl.contains("?") || surl.indexOf("?")==surl.length()-1;
    }
    
    
}