
package org.autoplot.jythonsupport.ui;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.URISplit;
import org.autoplot.jythonsupport.JythonUtil;

/**
 * utility methods, for example getting parameters from a script.
 * @author jbf
 */
public class Util {
    
    private static final Logger logger= LoggerManager.getLogger("jython");
    
    /**
     * scrape the script for getParameter calls.
     * @param uri location of the script, or resourceURI with script= parameter.
     * @param mon monitor for loading the script
     * @return
     * @throws IOException 
     */
    protected static Map<String,JythonUtil.Param> getParams( URI uri, ProgressMonitor mon ) throws IOException {

        URISplit split= URISplit.parse(uri);
        Map<String,String> params= URISplit.parseParams(split.params);
        String furi;
        if ( params.containsKey("script") ) {
            furi= params.get("script");
        } else {
            furi= split.resourceUri.toString();
        }

        File src = DataSetURI.getFile(furi, mon );

        List<JythonUtil.Param> r2= JythonUtil.getGetParams( new FileReader(src) );

        Map<String,JythonUtil.Param> result= new LinkedHashMap();

        for ( JythonUtil.Param r : r2 ) {
            result.put( r.name, r );
        }

        return result;

    }

    /**
     * scrape the script for getParameter calls.
     * @param src the script, all in one string.
     * @param mon
     * @return
     * @throws IOException 
     */
    protected static Map<String,JythonUtil.Param> getParams( String src, ProgressMonitor mon ) throws IOException {
        return getParams( null, src, new HashMap(), mon );
    }
    
    protected static Map<String,JythonUtil.Param> getParams( String src, Map<String,String> params, ProgressMonitor mon ) throws IOException {
        return getParams( null, src, params, mon );
    }

    /**
     * get the parameters for the script.
     * @param env null, or a script context that can contain values such as dom and PWD.
     * @param src the script, all in one string.
     * @param params null or default values for the parameters.
     * @param mon
     * @return list of parameters.
     * @throws IOException 
     * @see org.autoplot.jythonsupport.ui.ParametersFormPanel#doVariables(java.util.Map, java.lang.String, java.util.Map, javax.swing.JPanel) 
     */
    public static Map<String,JythonUtil.Param> getParams( Map<String,Object> env, String src, Map<String,String> params, ProgressMonitor mon ) throws IOException {
        logger.finer("enter getParams");
        List<JythonUtil.Param> r2= JythonUtil.getGetParams( env, src, params );

        Map<String,JythonUtil.Param> result= new LinkedHashMap();

        for ( JythonUtil.Param r : r2 ) {
            result.put( r.name, r );
        }

        return result;

    }
        
}
