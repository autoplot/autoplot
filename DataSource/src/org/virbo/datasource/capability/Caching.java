/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource.capability;

/**
 * Caching allows datasets to cache other URIs along with the one requested.
 * For example, a Jython script is executed resulting in four datasets being
 * calculated.  Since the URI must only correspond to one dataset, only one of
 * the datasets is used, but instead of throwing out the result, we keep them
 * around in case the client wants to plot a related URI.  
 *
 * @author jbf
 */
public interface Caching {
    /**
     * return true if the DataSource is able to quickly resolve the data set.
     * @param surl
     * @return
     */
    boolean satisfies( String surl );
    void resetURL( String surl );
}
