/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.cottagesystems.jdiskhog;

/**
 *
 * @author jbf
 */
public interface ProgressMonitor {
    void setTaskSize( int size );
    void setTaskProgress( int pos );
    void setTaskMessage( String message );
    void started();
    void finished();
    boolean isCancelled();
}
