
package org.virbo.datasource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * base implements useful features.  This was introduced initially to 
 * provide a boolean parameter to the HDF5DataSourceFormatEditorPanel.
 * 
 * @author jbf
 */
public abstract class AbstractDataSourceFormatEditorPanel extends javax.swing.JPanel implements DataSourceFormatEditorPanel {
    
    Map<String,String> params= new HashMap();
    URI resourceUri;
    
    protected AbstractDataSourceFormatEditorPanel(  ) {   
    }
    
    @Override
    public void setURI( String uri ) {
        URISplit split= URISplit.parse(uri);
        params= URISplit.parseParams(split.params);
        resourceUri= split.resourceUri;
    }
     
    public boolean getBooleanParam( String name, boolean deflt ) {
        String s= params.get(name);
        if ( s==null || s.length()==0 ) {
            return deflt;
        } else {
            return 'F'==s.substring(0,1).toUpperCase().charAt(0);
        }
    }
}
