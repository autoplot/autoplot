/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.virbo.datasource.capability.Updating;

/**
 *
 * @author jbf
 */
public class FilePollUpdating implements Updating {

    File pollFile;
    long pollMtime;
    long pollMsize;

    PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
        if ( ! pcs.hasListeners(null) ) {
            stopPolling();
        }
    }

    public void startPolling( File file, final long pollCyclePeriodMillis ) {
        pollMsize= file.length();
        pollMtime= file.lastModified();
        pollFile= file;
        Runnable run= new Runnable() {
            public void run() {
                //System.err.println("start polling "+pollFile);
                while ( pollFile!=null ) {
                    File lpollFile= pollFile; // make a local copy instead of synchronized block
                    if ( lpollFile==null ) continue;
                    try {
                        Thread.sleep(pollCyclePeriodMillis);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(FilePollUpdating.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    if ( lpollFile.exists() ) {
                        if ( lpollFile.length()!=pollMsize || lpollFile.lastModified()!=pollMtime ) {
                            pcs.firePropertyChange( Updating.PROP_DATASET, null, null );
                            pollMsize= lpollFile.length();
                            pollMtime= lpollFile.lastModified();
                        }
                    }
                }
            }
        };
        new Thread( run, "FillPollUpdating" ).start();
    }

    public void stopPolling() {
        //System.err.println("stop polling "+pollFile);
        pollFile= null;
    }

}
