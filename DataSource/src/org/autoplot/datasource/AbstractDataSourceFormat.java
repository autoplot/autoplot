/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.datasource;

import java.net.URI;
import java.util.Map;

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
}
