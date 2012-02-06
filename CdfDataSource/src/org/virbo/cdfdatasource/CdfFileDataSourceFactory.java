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
import gsfc.nssdc.cdf.CDFException;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.URISplit;

/**
 *
 * @author jbf
 */
public class CdfFileDataSourceFactory implements DataSourceFactory {
    
    private static final Logger logger = Logger.getLogger("virbo.cdfdatasource");
    
    static {
        loadCdfLibraries();
    }
    /** Creates a new instance of CdfFileDataSourceFactory */
    public CdfFileDataSourceFactory() {
	
    }
    
    public static void loadCdfLibraries() {
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
            // TODO: on Linux systems, may not be able to execute from plug-in media.
            if (cdfLib1 != null) System.loadLibrary(cdfLib1);
            if (cdfLib2 != null) System.loadLibrary(cdfLib2);
        } catch ( UnsatisfiedLinkError ex ) {
            ex.printStackTrace();
            System.err.println( "java.library.path: " + System.getProperty("java.library.path" ));
            throw ex;
        }
        logger.fine("cdf binaries loaded");
    }
    
    
    public DataSource getDataSource(URI uri) throws Exception {
        return new CdfFileDataSource( uri );
    }
    
    
    public String editPanel(String surl) throws Exception {
        return surl;
    }
    
    public List<CompletionContext> getCompletions(CompletionContext cc, org.das2.util.monitor.ProgressMonitor mon) throws Exception {
        if ( cc.context.equals(CompletionContext.CONTEXT_PARAMETER_NAME) ) {
            
            File cdfFile= DataSetURI.getFile( cc.resourceURI, mon );
            String fileName= cdfFile.toString();
            //if ( System.getProperty("os.name").startsWith("Windows") ) fileName= CdfUtil.win95Name( cdfFile );
            
            logger.log(Level.FINEST, "opening cdf file {0}", fileName);
            CDF cdf= CdfFileDataSourceFactory.getCDFFile( fileName );
            
            logger.finest("inspect cdf for plottable parameters");
            Map<String,String> result= CdfUtil.getPlottable( cdf, false , 4 );
            
            logger.finest("close cdf");
            CdfFileDataSourceFactory.closeCDF(cdf);
            
            List<CompletionContext> ccresult= new ArrayList<CompletionContext>();
            for ( Entry<String,String> e:result.entrySet() ) {
                String key= e.getKey();
                CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, 
                        key, this, "arg_0", e.getValue(), null, true );
                ccresult.add(cc1);
            }

            ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "interpMeta=", "control interpretation of metadata"));
            ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "doDep=", "control dependencies between variables"));
            
            return ccresult;
            
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            String parmname= CompletionContext.get( CompletionContext.CONTEXT_PARAMETER_NAME, cc );
            if ( parmname.equals("id") ) {
                String file= CompletionContext.get( CompletionContext.CONTEXT_FILE, cc );
                
                File cdfFile= DataSetURI.getFile( DataSetURI.getURL(file), mon );
                String fileName= cdfFile.toString();
                //if ( System.getProperty("os.name").startsWith("Windows") ) fileName= CdfUtil.win95Name( cdfFile );
                
                CDF cdf= CdfFileDataSourceFactory.getCDFFile( fileName );
                Map<String,String> result= CdfUtil.getPlottable( cdf, false, 4 );
                CdfFileDataSourceFactory.closeCDF(cdf);
                
                List<CompletionContext> ccresult= new ArrayList<CompletionContext>();
                for ( Entry<String,String> e:result.entrySet() ) {
                    String key= e.getKey();
                    CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, key, this, null, key, e.getValue(), true  );
                    ccresult.add(cc1);
                }
                
                return ccresult;
            } else if ( parmname.equals("interpMeta") ) {
                return Arrays.asList(
                        new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "yes", "use metadata (default)" ),
                        new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "no", "inhibit use of metadata" ) );
            } else if ( parmname.equals("doDep") ) {
                return Arrays.asList(
                        new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "yes", "use dependency tags (default)" ),
                        new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "no", "inhibit use of dependency tags" ) );

            } else {
                return Collections.emptyList();
            }
            
        } else {
            return Collections.emptyList();
        }
    }
    
    public boolean reject( String surl, ProgressMonitor mon ) {
        try {
            if (!surl.contains("?") || surl.indexOf("?") == surl.length() - 1) {
                return true;
            }
            URISplit split = URISplit.parse(surl);
            Map<String,String> args= URISplit.parseParams( split.params );
            String param= args.get("arg_0");
            if ( param==null ) {
                param= args.get("id");
                if ( param==null ) {
                    return true;
                }
            }
            File file = DataSetURI.getFile(split.resourceUri, mon);
            if (!file.isFile()) {
                return true;
            } else {
                CDF cdf= CdfFileDataSourceFactory.getCDFFile( file.getPath() );
                Map<String,String> result= CdfUtil.getPlottable( cdf, false, 4 );
                CdfFileDataSourceFactory.closeCDF(cdf);
                int i= param.indexOf("[");
                if ( i>-1 ) {
                    param= param.substring(0,i);
                }
                return ! result.containsKey(param);
            }
        } catch (CDFException ex) {
            Logger.getLogger(CdfFileDataSourceFactory.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        } catch (IOException ex) {
            return false;
        }
    }
    
    //  new caching stuff.
    private static CDF currentCDF=null;
    private static String currentFile= null;

    /**
     * cache one open CDF, because tha_l2_esa_20080907_v01.cdf has lots of variables and it's expensive to open.
     * @param cdfFile
     * @return
     * @throws CDFException
     */
    protected static CDF getCDFFile( String cdfFile ) throws CDFException {
        //if ( currentFile!=null && currentFile.equals(cdfFile) ) {
        //    System.err.println("caching open CDF file satisfies");
        //    return currentCDF;
        //}

        logger.log(Level.FINE, "opening {0}", cdfFile);
        currentCDF= CDF.open(cdfFile);
        //currentFile= cdfFile;
        return currentCDF;
    }

    /**
     * this will allow for multiple files to be opened in the future.  Right now
     * we just leave it open.
     * @param cdf
     */
    protected static void closeCDF( CDF cdf ) throws CDFException {
        // do nothing
        cdf.close();
    }

    public <T> T getCapability(Class<T> clazz) {
        return null;
    }
    
}