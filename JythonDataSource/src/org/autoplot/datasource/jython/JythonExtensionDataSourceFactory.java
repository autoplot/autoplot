
package org.autoplot.datasource.jython;

import java.net.URI;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.URISplit;
import org.das2.datum.LoggerManager;

/**
 * Creates JythonExceptionDataSource's, which are data sources defined
 * by Jython scripts, but the script need not be in the URI.
 * @author jbf
 */
public class JythonExtensionDataSourceFactory extends AbstractDataSourceFactory {

    private static final Logger logger= LoggerManager.getLogger("apdss.jyds");
    
    /**
     * this is the lookup table from URI (*.sps) to script (readTypeSps.jyds)
     * @param uri
     * @return 
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
                script= "https://saturn.physics.uiowa.edu/svn/earth/public/jyds/readTypeSps.jyds";
                break;
            case "vap+spd":
                script= "https://saturn.physics.uiowa.edu/svn/earth/public/jyds/readTypeSpd.jyds";
                break;
            case "vap+wdc":
                script= "https://raw.githubusercontent.com/autoplot/jyds/master/wdc_kp_ap.jyds";
                break;
            default:
                throw new IllegalArgumentException("resource extension is not supported: "+split.ext);
        }
        String alt= System.getProperty("jydsExtension_"+split.ext.substring(1),"");
        logger.log(Level.FINER, "check for alternate system property jydsExtension_{0}", new Object[]{split.ext.substring(1)});
        
        if ( alt.length()>0 ) {
            logger.log(Level.FINE, "system property jydsExtension_{0}={1}", new Object[]{split.ext.substring(1), alt});
            script= alt;
        }
        
        logger.log(Level.FINE, "Using script {0}", script);
        
        return script;
    }
    
    /**
     * return the URI which would be used for this resource, if it were 
     * called directly.
     * @param uri
     * @return 
     */
    public static String getJydsUri( URI uri ) {
        String script= getScriptForResource(uri);
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
