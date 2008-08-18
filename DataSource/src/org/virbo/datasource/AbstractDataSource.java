/*
 * AbstractDataSource.java
 *
 * Created on April 1, 2007, 7:12 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.datasource;

import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.virbo.dataset.QDataSet;

/**
 *
 * @author jbf
 */
public abstract class AbstractDataSource implements DataSource {
    
    protected URL url;
    
    /**
     * available to subclasses for convenience.  This is the name of the file,
     * without the parameters.
     */
    protected URL resourceURL;
    
    /**
     * returns the url's extension for convenience.  This does not canonize
     * the extension, case is preserved.  The extension does contain the initial
     * period.  Returns an empty string if no extension is found.
     * 
     * @return extension with a period, or empty string.
     */
    protected String getExt( URL url ) {
        String s= url.getFile();
        int i= s.lastIndexOf(s);
        if ( i==-1 ) {
            return "";
        } else {
            return s.substring(i);
        }
    }
    
    /**
     * available to subclasses for convenience.  
     */
    protected Map<String,String> params;
    
    public AbstractDataSource( URL url ) {
        try {
            this.url = url;
            DataSetURL.URLSplit split = DataSetURL.parse(url.toString());
            params = DataSetURL.parseParams(split.params);
            resourceURL = new URL(split.file);
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    public abstract QDataSet getDataSet(ProgressMonitor mon) throws Exception;
    
    public boolean asynchronousLoad() {
        return true;
    }
    
    public String toString() {
        return url.toString();
    }
    
    public String getURL() {
        return url.toString();
    }
    
    /**
     * make the remote file available.
     */
    protected File getFile( ProgressMonitor mon ) throws IOException {
       return DataSetURL.getFile( url, mon ); 
    }
    
    /**
     * return the parameters from the URL.
     */
    protected Map getParams() {
       return params; 
    }
    
    /**
     * abstract class version returns an empty tree.  Override this method
     * to provide metadata.
     */
    public Map<String,Object> getMetaData( ProgressMonitor mon ) throws Exception {
        return new HashMap();
    }
    
    public MetadataModel getMetadataModel() {
        return MetadataModel.createNullModel();
    }
    
    public Map<String,Object> getProperties() {
        try {
            return getMetadataModel().properties( getMetaData( new NullProgressMonitor() ) );
        } catch (Exception e) {
	    e.printStackTrace();
            return Collections.singletonMap( "Exception",  (Object)e );
        }
    }
   
    HashMap<Class,Object> capabilities= new HashMap<Class,Object>();
    
    /**
     * attempt to get a capability.  null will be returned if the 
     * capability doesn't exist.
     */
    public <T> T getCapability( Class<T> clazz ) {
        return (T) capabilities.get(clazz);
    }
    
    /**
     * attach a capability
     */
    public <T> void addCability( Class<T> clazz, T o ) {
        capabilities.put( clazz, o );
    }
    
    
}
