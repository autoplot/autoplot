/*
 * CdfFileDataSourceFactory.java
 *
 * Created on July 23, 2007, 8:29 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.cdf;

import gov.nasa.gsfc.voyager.cdf.CDF;
import gov.nasa.gsfc.voyager.cdf.CDFFactory;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
public class CdfJavaDataSourceFactory implements DataSourceFactory {
    
    private static final Logger logger = Logger.getLogger("virbo.cdfdatasource");
    
    /** Creates a new instance of CdfFileDataSourceFactory */
    public CdfJavaDataSourceFactory() {
	
    }    
    
    public DataSource getDataSource(URI uri) throws Exception {
        return new CdfJavaDataSource( uri );
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

            CDF cdf;
            try {
                cdf = CDFFactory.getCDF(fileName);
            } catch (Throwable ex) {
                throw new Exception(ex);
            }

            logger.finest("inspect cdf for plottable parameters");
            Map<String,String> result= CdfUtil.getPlottable( cdf, false, 4 );
            
            logger.finest("close cdf");
            //cdf.close();
            
            List<CompletionContext> ccresult= new ArrayList<CompletionContext>();
            for ( String key:result.keySet() ) {
                CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, 
                        key, this, "arg_0", result.get(key), null, true );
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
                
                CDF cdf;
                try {
                    cdf = CDFFactory.getCDF(fileName);
                } catch (Throwable ex) {
                    throw new RuntimeException(ex);
                }
                Map<String,String> result= CdfUtil.getPlottable( cdf, false, 4 );
                //cdf.close();
                
                List<CompletionContext> ccresult= new ArrayList<CompletionContext>();
                for ( String key:result.keySet() ) {
                    CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, key, this, null, key, result.get(key), true  );
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
            URISplit split = URISplit.parse(surl.toString());
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
                CDF cdf;
                try {
                    cdf= CDFFactory.getCDF( file.getPath() );
                } catch ( Throwable ex ) {
                    throw new RuntimeException(ex);
                }
                Map<String,String> result= CdfUtil.getPlottable( cdf, false, 4 );
                //cdf.close();
                int i= param.indexOf("[");
                if ( i>-1 ) {
                    param= param.substring(0,i);
                }
                return ! result.containsKey(param);
            }
        } catch (Exception ex) {
            return false;
        }
    }

    public <T> T getCapability(Class<T> clazz) {
        return null;
    }
    
    
}