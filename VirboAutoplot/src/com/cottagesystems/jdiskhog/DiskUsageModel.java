/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cottagesystems.jdiskhog;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.das2.util.monitor.ProgressMonitor;

/**
 *
 * @author jbf
 */
public class DiskUsageModel {

    Map<File, Long> dirUsage = new HashMap<File, Long>();

    private boolean notLink(File f) {
        try {
            return f.getCanonicalFile().equals(f);
        } catch (IOException ex) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public void search(File f, int depth, ProgressMonitor mon) {

        try {
            File nf= f.getCanonicalFile();
            f= nf;
        } catch ( IOException ex ) {
            // this is the old behavoir.
        }

        File[] kids = f.listFiles();
        
        if (kids == null) {
            return;
        }  // c:\System Information?

        if (depth == 0) {
            mon.setTaskSize(kids.length);
            mon.started();
        }

        // recurse, first
        for (int i = 0; i < kids.length; i++) {
            if (depth == 0) {
                mon.setTaskProgress(i);
            }
            mon.setProgressMessage("" + kids[i]);
            if (mon.isCancelled()) {
                dirUsage.remove(f);
                return;
            }
            if (kids[i].isDirectory() && notLink(kids[i])) {
                search(kids[i], depth + 1, mon);
            } else {
                if ( kids[i].isDirectory() && ! notLink(kids[i]) ) {
                    System.err.println("appears to be a link: "+kids[i]);
                }
            }
        }

        if (depth == 0) {
            mon.finished();
        }

        try {
            // now total
            long totalSizeKB = 0;
            for (int i = 0; i < kids.length; i++) {
                File fkids1 = kids[i];
                if (notLink(fkids1) == false) {
                    totalSizeKB += 0; // link size is trivial

                } else if (fkids1.isDirectory()) {
                    Long l = dirUsage.get(fkids1);
                    if (l == null) {
                        totalSizeKB += 0;
                    } else {
                        totalSizeKB += l + 4; // 4Kb for directory entry
                    }

                } else {
                    totalSizeKB += fkids1.length() / 1000.;
                }
            }
            dirUsage.put(f, totalSizeKB);
        } catch (NullPointerException ex) {
            System.err.println("here NullPointerException in DiskUsageModel");
        }


    }

    public Long usage(File f) {
        try {
            File nf= f.getCanonicalFile();
            f= nf;
        } catch ( IOException ex ) {

        }
        return dirUsage.get(f);
    }
    private boolean ready = false;
    public static final String PROP_READY = "ready";

    /**
     * Get the value of ready
     *
     * @return the value of ready
     */
    public boolean isReady() {
        return this.ready;
    }

    /**
     * Set the value of ready
     *
     * @param newready new value of ready
     */
    public void setReady(boolean newready) {
        boolean oldready = ready;
        this.ready = newready;
        propertyChangeSupport.firePropertyChange(PROP_READY, oldready, newready);
    }
    private java.beans.PropertyChangeSupport propertyChangeSupport = new java.beans.PropertyChangeSupport(this);

    /**
     * Add PropertyChangeListener.
     *
     * @param listener
     */
    public void addPropertyChangeListener(java.beans.PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    /**
     * Remove PropertyChangeListener.
     *
     * @param listener
     */
    public void removePropertyChangeListener(java.beans.PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }
}
