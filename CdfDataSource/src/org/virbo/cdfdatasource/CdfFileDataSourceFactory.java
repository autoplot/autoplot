/*
 * CdfFileDataSourceFactory.java
 *
 * Created on July 23, 2007, 8:29 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.cdfdatasource;

import edu.uiowa.physics.pw.das.util.NullProgressMonitor;
import gsfc.nssdc.cdf.CDF;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.MetadataModel;

/**
 *
 * @author jbf
 */
public class CdfFileDataSourceFactory implements DataSourceFactory {
    
    /** Creates a new instance of CdfFileDataSourceFactory */
    public CdfFileDataSourceFactory() {
    }
    
    static {
        loadCdfLibraries();
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
            }
        }
        
        if (cdfLib1 != null) System.loadLibrary(cdfLib1);
        if (cdfLib2 != null) System.loadLibrary(cdfLib2);
        
    }
    
    
    public DataSource getDataSource(URL url) throws Exception {
        DataSetURL.URLSplit split= DataSetURL.parse( url.toString() );
        return new CdfFileDataSource( url );
    }
    
    
    public String editPanel(String surl) throws Exception {
        return surl;
    }
    
    public MetadataModel getMetadataModel(URL url) {
        return new IstpMetadataModel();
    }
    
    public List<CompletionContext> getCompletions(CompletionContext cc) throws Exception {
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            String file= CompletionContext.get( CompletionContext.CONTEXT_FILE, cc );
            
            File cdfFile= DataSetURL.getFile( DataSetURL.getURL(file), new NullProgressMonitor() );
            String fileName= cdfFile.toString();
            if ( System.getProperty("os.name").startsWith("Windows") ) fileName= CdfUtil.win95Name( cdfFile );
            
            CDF cdf= CDF.open( fileName, CDF.READONLYon );
            List result= CdfUtil.getPlottable( cdf, false , 2);
            cdf.close();
            
            List<CompletionContext> ccresult= new ArrayList<CompletionContext>();
            for ( int it=0; it<result.size(); it++ ) {
                CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, (String) result.get(it), this, "arg_0" );
                ccresult.add(cc1);
            }
            return ccresult;
            
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            String parmname= CompletionContext.get( CompletionContext.CONTEXT_PARAMETER_NAME, cc );
            if ( parmname.equals("id") ) {
                String file= CompletionContext.get( CompletionContext.CONTEXT_FILE, cc );
                
                File cdfFile= DataSetURL.getFile( DataSetURL.getURL(file), new NullProgressMonitor() );
                String fileName= cdfFile.toString();
                if ( System.getProperty("os.name").startsWith("Windows") ) fileName= CdfUtil.win95Name( cdfFile );
                
                CDF cdf= CDF.open( fileName, CDF.READONLYon );
                List result= CdfUtil.getPlottable( cdf, false , 2);
                cdf.close();
                
                List<CompletionContext> ccresult= new ArrayList<CompletionContext>();
                for ( int it=0; it<result.size(); it++ ) {
                    CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, (String) result.get(it), this, null );
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
    
    public boolean reject( String surl ) {
        return ! surl.contains("?") || surl.indexOf("?")==surl.length()-1;
    }
    
    public String urlForServer(String surl) {
        return surl; // TODO
    }
    
    public List<String> extensions() {
        return Collections.singletonList(".cdf");
    }
    
    public List<String> mimeTypes() {
        return Collections.emptyList();
    }
}