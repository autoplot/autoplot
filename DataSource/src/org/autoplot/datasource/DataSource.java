/*
 * DataSource.java
 *
 * Created on March 31, 2007, 7:56 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.autoplot.datasource;

import org.das2.util.monitor.ProgressMonitor;
import java.util.Map;
import org.das2.qds.QDataSet;

/**
 * Like the DataLoader of das2, but provides minimal dataset discovery metadata
 * @author jbf
 */
public interface DataSource {
    
    /**
     * retrieve the dataset.  This allowed to be sub-interactive or batch time scale, and will block
     * until the dataset is produced.
     *
     * This may return null when no data is available, or NoDataInIntervalException can be thrown.
     *
     * If the user cancelled the operation, then java.io.InterrupedIOExcaption or
     * better yet org.das2.CancelledOperationException should be called.  These will
     * simply display "cancelled" (or similar) on the status bar.
     *
     * @param mon
     * @return the DataSet for the URI, or null if no data is available.
     * @throws java.lang.Exception
     */
    QDataSet getDataSet( ProgressMonitor mon ) throws Exception;
    
    /**
     *loading the data is slow, so load the data asynchronously (on a separate thread).  This
     *should return true if getDataSet will take more than 100 milliseconds (interactive time).
     * Note this is currently ignored, and may be indefinitely ignored.
     */
    boolean asynchronousLoad();
    
    /**
     * return a MetadataModel that scrapes the Metadata tree returned to provide a
     * set of properties identified in QDataSet.  
     * @return
     */
    MetadataModel getMetadataModel();
    
    /**
     * Return arbitrary metadata for the dataset.  This is a map of String to Objects,
     * and to form a tree structure, property name may map to another Map&lt;String,Object&gt;.
     * Note the order of the properties may be controlled by using LinkedHashMap for the
     * implementation.  Even though this takes a monitor, it will be called after getDataSet,
     * and the monitor may be safely ignored.
     * This should return new HashMap() if no metadata is found.
     * @param mon
     * @return 
     * @throws java.lang.Exception 
     */
    Map<String,Object> getMetadata( ProgressMonitor mon ) throws Exception ;

    /**
     * return the fully-qualified URI of this data source, including the "vap+<ext>:" scheme.
     * @return
     */
    String getURI();
    
    /**
     * discovery properties for the dataset.  These should follow the QDataSet conventions, such as
     * TITLE, LABEL, etc, and should mirror the structure of the dataset.  Note
     * getMetadataModel().getProperties( getMetaData() ) should return the same thing.
     * @return the properties.
     */
    Map<String,Object> getProperties();
   
    /**
     * cookie jar of capabilities, see org.autoplot.datasource.capability.  Each capability can be 
     * queried, and either an object implementing the capability or null is returned.  Example
     * capabilities include:<table>
     * <tr><td>TimeSeriesBrowse</td><td>which allows the user to request data from a different interval. </td></tr>
     * <tr><td>Caching</td><td>which should be queried before the loader is called again.</td></tr>
     * </table>
     * Note the DataSourceFactory also has a getCapability method, solely to provide TimeSeriesBrowse so that 
     * new URIs can be created in without reading data.
     */
    <T> T getCapability( Class<T> clazz );
}
