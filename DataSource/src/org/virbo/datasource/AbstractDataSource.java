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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.capability.Updating;

/**
 * Base class for file-based DataSources that keeps track of the uri, makes
 * the parameters available, manages capabilities and has do-nothing
 * implementations for rarely-used methods of DataSource.
 *
 * Also this provides the filePollUpdating parameter and Updating capability.
 *
 * @author jbf
 */
public abstract class AbstractDataSource implements DataSource {

    protected static final Logger logger= Logger.getLogger("apdss");

    protected URI uri;
    /**
     * available to subclasses for convenience.  This is the name of the file,
     * without the parameters.
     */
    protected URI resourceURI;

    public AbstractDataSource(java.net.URI uri) {
        this.uri = uri;
        String s = DataSetURI.fromUri(uri);
        if ( !s.startsWith("vap") ) {
            logger.fine( "uri didn't start with vap!" );
        }
        URISplit split = URISplit.parse(s);

        params = URISplit.parseParams(split.params);

        String f= split.file;
        if ( split.scheme!=null ) {
            try {
                resourceURI = DataSetURI.toUri(f);
            } catch (Exception e) {
                //URI syntax exception
                logger.fine(e.toString()); // InlineDataSource is subclass, need to fix this...
            }
        }
    }

    /**
     * returns the uri's canonical extension for convenience.
     * The extension does contain the initial period and is folded to lower case.  
     * Returns an empty string if no extension is found.
     * 
     * Note that this is not necessarily the extension associated with the DataSource.  For example,
     * ImageDataSource has a canonical extension of ".jpg", but for a png file this will return .png.
     * 
     * @return lower-case extension with a period, or empty string.
     */
    protected String getExt(URL url) {
        try {
            return getExt(url.toURI());
        } catch (URISyntaxException e) {
            logger.fine("Failed to convert URL to URI.");
            return "";
        }
        /*String s = url.getFile();
        int i = s.lastIndexOf("."); // URI okay
        if (i == -1) {
        return "";
        } else {
        return s.substring(i).toLowerCase();
        }*/    }

    protected String getExt(URI uri) {
        String s = uri.getPath();
        int i = s.lastIndexOf(".");
        if (i == -1) {
            return "";
        } else {
            return s.substring(i).toLowerCase();
        }
    }
    /**
     * available to subclasses for convenience.  
     */
    protected Map<String, String> params;

    public abstract QDataSet getDataSet(ProgressMonitor mon) throws Exception;

    public boolean asynchronousLoad() {
        return true;
    }

    @Override
    public String toString() {
        return DataSetURI.fromUri(uri);
    }

    public String getURI() {
        return DataSetURI.fromUri(uri);
    }


    FilePollUpdating pollingUpdater;

    /**
     * make the remote file available.
     */
    protected File getFile(ProgressMonitor mon) throws IOException {
        if ( resourceURI==null || resourceURI.toString().equals("")  ) {
            throw new IllegalArgumentException("expected file but didn't find one, check URI for question mark");
        }
        return getFile( resourceURI, mon );
    }

    // Practically identical to the URL version below...
    protected File getFile(URI uri, ProgressMonitor mon) throws IOException {
        File f = DataSetURI.getFile(uri, mon);
         if (params.containsKey("filePollUpdates")) {
            pollingUpdater= new FilePollUpdating();
            pollingUpdater.startPolling( f,(long)(1000*Double.parseDouble(params.get("filePollUpdates")) ) );
            capabilities.put(Updating.class,pollingUpdater );
        }
        return f;
    }
    /**
     * make the remote file available.  If the parameter "filePollUpdates" is set to
     * a float, a thread will be started to monitor the local file for updates.
     * This is done by monitoring for file length and modification time changes.
     */
    protected File getFile( URL url, ProgressMonitor mon ) throws IOException {
        File f = DataSetURI.getFile( url, mon );
        if (params.containsKey("filePollUpdates")) {
            pollingUpdater= new FilePollUpdating();
            pollingUpdater.startPolling( f,(long)(1000*Double.parseDouble(params.get("filePollUpdates")) ) );
            capabilities.put(Updating.class,pollingUpdater );
        }
        return f;
    }

    /**
     * get the file, allowing content to be html.
     * @param url
     * @param mon
     * @return
     * @throws IOException
     */
    protected File getHtmlFile( URL url, ProgressMonitor mon ) throws IOException {
        File f = DataSetURI.getHtmlFile( url, mon );
        if (params.containsKey("filePollUpdates")) {
            pollingUpdater= new FilePollUpdating();
            pollingUpdater.startPolling( f,(long)(1000*Double.parseDouble(params.get("filePollUpdates")) ) );
            capabilities.put(Updating.class,pollingUpdater );
        }
        return f;
    }
    /**
     * return the parameters from the URL.
     */
    protected Map getParams() {
        return new LinkedHashMap(params);
    }

    /**
     * return the named parameter, or the default.  
     * Note arg_0, arg_1, etc are for unnamed positional parameters.  It's recommended
     * that there be only one positional parameter.
     */
    protected String getParam( String name, String dflt ) {
        String result= params.get(name);
        if (result!=null ) {
            return result;
        } else {
            return dflt;
        }
    }

    /**
     * abstract class version returns an empty tree.  Override this method
     * to provide metadata.
     */
    public Map<String, Object> getMetadata(ProgressMonitor mon) throws Exception {
        return new HashMap<String, Object>();
    }

    public MetadataModel getMetadataModel() {
        return MetadataModel.createNullModel();
    }

    public Map<String, Object> getProperties() {
        try {
            Map<String, Object> meta = getMetadata(new NullProgressMonitor());
            return getMetadataModel().properties(meta);
        } catch (Exception e) {
            logger.log( Level.SEVERE, "exception in getProperties", e );
            return Collections.singletonMap("Exception", (Object) e);
        }
    }

    private HashMap<Class, Object> capabilities = new HashMap<Class, Object>();

    /**
     * attempt to get a capability.  null will be returned if the 
     * capability doesn't exist.
     */
    public <T> T getCapability(Class<T> clazz) {
        return (T) capabilities.get(clazz);
    }

    /**
     * attach a capability
     */
    public <T> void addCability(Class<T> clazz, T o) {
        capabilities.put(clazz, o);
    }
}
