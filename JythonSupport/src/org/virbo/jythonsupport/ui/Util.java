/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.jythonsupport.ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.URISplit;
import org.virbo.jythonsupport.JythonUtil;

/**
 *
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

        List<JythonUtil.Param> r2= JythonUtil.getGetParams( new BufferedReader( new FileReader(src) ) );

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
        return getParams( src, new HashMap(), mon );
    }
    
    protected static Map<String,JythonUtil.Param> getParams( String src, Map<String,String> params, ProgressMonitor mon ) throws IOException {

        List<JythonUtil.Param> r2= JythonUtil.getGetParams( src, params );

        Map<String,JythonUtil.Param> result= new LinkedHashMap();

        for ( JythonUtil.Param r : r2 ) {
            result.put( r.name, r );
        }

        return result;

    }
        
}
