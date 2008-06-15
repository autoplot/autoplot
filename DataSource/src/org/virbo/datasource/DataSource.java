/*
 * DataSource.java
 *
 * Created on March 31, 2007, 7:56 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.datasource;

import org.das2.util.monitor.ProgressMonitor;
import java.util.Map;
import javax.swing.tree.TreeModel;
import org.virbo.dataset.QDataSet;

/**
 * Like the DataLoader of das2, but provides minimal dataset discovery metadata
 * @author jbf
 */
public interface DataSource {
    
    /**
     * retrieve the dataset.  This allowed to be sub-interactive or batch time scale, and will block until the dataset is produced.  
     */
    QDataSet getDataSet( ProgressMonitor mon ) throws Exception;
    
    /**
     *loading the data is slow, so load the data asynchronously (on a separate thread).  This
     *should return true if getDataSet will take more than 100 milliseconds (interactive time).
     */
    boolean asynchronousLoad();
    
    MetadataModel getMetadataModel();
    
    /**
     * even though this takes a monitor, it should be called after getDataSet.
     */
    Map<String,Object> getMetaData( ProgressMonitor mon ) throws Exception ;

    String getURL();
    
    /**
     * discovery properties for the dataset
     */
    Map<String,Object> getProperties();
   
    /**
     * cookie jar of cabilities
     */
    <T> T getCapability( Class<T> clazz );
}
