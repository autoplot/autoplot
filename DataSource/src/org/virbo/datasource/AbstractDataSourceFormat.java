/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource;

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

    public String getParam( String name, String deflt ) {
        String result= params.get(name);
        if ( result==null ) {
            return deflt;
        } else {
            return result;
        }
    }
}
