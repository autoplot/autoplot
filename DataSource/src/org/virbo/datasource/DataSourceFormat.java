/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource;

import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.QDataSet;

/**
 * @author jbf
 */
public interface DataSourceFormat {
    /**
     * Format the dataset using the specified URI.  This should be parsed the same way 
     * read URIs are parsed, and arguments should reflect those of the reader 
     * when possible.
     * @param uri
     * @param data
     * @param mon
     * @throws Exception
     */
    public void formatData( String uri, QDataSet data, ProgressMonitor mon  ) throws Exception;

    /**
     * return true if the dataset can be formatted
     * @param ds
     * @return 
     */
    public boolean canFormat( QDataSet ds );

    /**
     * return a description of this format
     * @return 
     */
    public String getDescription();
        
    
}
