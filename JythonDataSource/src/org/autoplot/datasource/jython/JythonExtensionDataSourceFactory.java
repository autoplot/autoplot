
package org.autoplot.datasource.jython;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.URISplit;
import org.das2.datum.LoggerManager;
import org.das2.util.monitor.NullProgressMonitor;

/**
 * Creates JythonExceptionDataSource's, which are data sources defined
 * by Jython scripts, but the script need not be in the URI.
 * @author jbf
 */
public class JythonExtensionDataSourceFactory extends AbstractDataSourceFactory {

    private static final Logger logger= LoggerManager.getLogger("apdss.jyds");
    
    /**
     * this is the lookup table from URI (*.sps) to script (readTypeSps.jyds)
     * @param uri the Autoplot URI.
     * @return string containing the script.
     */
    public static String getScriptForResource( URI uri ) {
        String script;
        URISplit split= URISplit.parse(uri);
        String scheme= split.vapScheme;
        if ( scheme==null ) {
            scheme= "vap+"+split.ext.substring(1);
        }
        switch (scheme) {
            case "vap+sps":
                script= "https://github.com/autoplot/scripts/blob/master/formats/sps/readTypeSps.jyds";
                break;
            case "vap+spd":
                script= "https://github.com/autoplot/scripts/blob/master/formats/sps/readTypeSpd.jyds";
                break;
            case "vap+wdc":
                script= "https://raw.githubusercontent.com/autoplot/jyds/master/wdc_kp_ap.jyds";
                break;
            default:
                throw new IllegalArgumentException("resource extension is not supported: "+split.ext);
        }
        
        String ext= scheme.substring(4);
        
        String alt;
        alt= System.getProperty("jydsExtension_"+ext,"");
        
        if ( alt.length()>0 ) {
            logger.log(Level.FINE, "system property jydsExtension_{0}={1}", new Object[]{ ext, alt});
            script= alt;
        } else {
            logger.log(Level.FINER, "System.getProperty(\"jydsExtension_{0}\",\"\") returns \"\"", new Object[]{ ext });
        }
        
        logger.log(Level.FINE, "Using script {0}", script);
        
        return script;
    }
    
    /**
     * include an internal version of the script, so that the data source will
     * work offline, for example when presenting the data source to a group
     * of new users at a conference without network access.
     * @param uri the Autoplot URI.
     * @return null or the name of the file to use.
     */
    public static String getInternalScriptForResource(URI uri) {
        String script;
        URISplit split= URISplit.parse(uri);
        String scheme= split.vapScheme;
        if ( scheme==null ) {
            scheme= "vap+"+split.ext.substring(1);
        }
        switch (scheme) {
            case "vap+sps":
                script= "/readTypeSps.jyds";
                break;
            case "vap+spd":
                script= "/readTypeSpd.jyds";
                break;
            case "vap+wdc":
                script= "/wdc_kp_ap.jyds";
                break;
            default:
                throw new IllegalArgumentException("resource extension is not supported: "+split.ext);
        }
        try {
            File scriptFile= DataSetURI.downloadResourceAsTempFile( JythonExtensionDataSourceFactory.class.getResource(script), new NullProgressMonitor() );
            return scriptFile.toURI().toString();
        } catch ( IOException ex ) {
            logger.log( Level.WARNING, ex.getMessage(), ex );
            return null;
        }
    }
    
    
    
    /**
     * return the URI which would be used for this resource, if it were 
     * called directly.
     * @param uri
     * @return 
     */
    public static String getJydsUri( URI uri ) {
        String script= getScriptForResource(uri);
        try {
            File scriptFile= DataSetURI.getFile( new URL(script), new NullProgressMonitor() );
            logger.log(Level.FINE, "can be downloaded: {0}", scriptFile);
        } catch ( IOException ex ) {
            logger.log(Level.INFO, "unable to read remote script {0}, using internal copy", script);
            script= JythonExtensionDataSourceFactory.getInternalScriptForResource(uri);
            if ( script==null ) {
                throw new IllegalArgumentException("Unable to locate script");
            }
        }
        URISplit split= URISplit.parse(uri);
        Map<String,String> params= URISplit.parseParams(split.params);
        params.put( JythonDataSource.PARAM_SCRIPT, script );
        split.params= URISplit.formatParams(params);
        return URISplit.format(split);
    }

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new JythonExtensionDataSource(uri);
    }
    
}
