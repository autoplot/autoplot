/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.datasource;

import java.io.OutputStream;
import java.net.URI;
import java.util.Iterator;
import java.util.Map;
import org.das2.qds.QDataSet;
import org.das2.qds.util.DataSetBuilder;

/**
 * provides getParam to extensions and the file part.  
 * @author jbf
 */
public abstract class AbstractDataSourceFormat implements DataSourceFormat {

    Map<String,String> params;
    URI resourceUri;

    protected AbstractDataSourceFormat( ) {
    }

    protected void setUri( String uri ) {
        URISplit split= URISplit.parse(uri);
        params= URISplit.parseParams(split.params);
        resourceUri= split.resourceUri;
    }

    /**
     * return the URI (file part) of the 
     * @return
     */
    public URI getResourceURI( ) {
        return resourceUri;
    }

    /**
     * return the string parameter.  Note setUri must be called with
     * the input URI.
     * @param name the parameter name.
     * @param deflt the default value should the parameter be missing.
     * @return the value
     */    
    public String getParam( String name, String deflt ) {
        String result= params.get(name);
        if ( result==null ) {
            return deflt;
        } else {
            return result;
        }
    }
    
    /**
     * return the boolean parameter.  Note setUri must be called with
     * the input URI.
     * @param name the parameter name.
     * @param deflt the default value should the parameter be missing or misformed.
     * @return the value
     */
    public boolean getBooleanParam( String name, boolean deflt ) {
        String s= params.get(name);
        if ( s==null || s.length()==0 ) {
            return deflt;
        } else {
            return 'F'==s.substring(0,1).toUpperCase().charAt(0);
        }
    }

    /**
     * return true if the format also supports streaming where each record is
     * formatted (roughly) as it is received, and there is a bound on the total 
     * size needed for any request.
     * @param params
     * @param data
     * @param out
     * @return
     * @throws Exception 
     */
    @Override
    public boolean streamData(Map<String, String> params, Iterator<QDataSet> data, OutputStream out) throws Exception {
        return false;
    }
    
    
}
