/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource.capability;

/**
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
