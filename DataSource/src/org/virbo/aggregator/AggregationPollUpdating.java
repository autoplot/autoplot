/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.aggregator;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.DatumRange;
import org.das2.fsm.FileStorageModelNew;
import org.das2.util.filesystem.FileObject;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.FileSystem.FileSystemOfflineException;
import org.das2.util.filesystem.LocalFileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.datasource.capability.Updating;

/**
 *
 * @author jbf
 */
public class AggregationPollUpdating implements Updating {

    FileStorageModelNew fsm;
    DatumRange dr;
    long dirHash;
    long pollCyclePeriodSeconds= -1;
    boolean dirty= false; //true indicates the hash has changed and we need to clean.
    boolean polling= false; //true indicates we are polling.

    private final static int LIMIT_SHORT_CYCLE_PERIOD= 1;
    private final static int LIMIT_SHORT_REMOTE_CYCLE_PERIOD= 10;

    public AggregationPollUpdating( FileStorageModelNew fsm, DatumRange dr, long pollCyclePeriodSeconds ) {
        this.fsm= fsm;
        this.dr= dr;
        if ( fsm.getFileSystem() instanceof LocalFileSystem ) {
            if ( pollCyclePeriodSeconds<LIMIT_SHORT_CYCLE_PERIOD ) {
                System.err.println("pollCyclePeriodSeconds too low, for local files it must be at least "+LIMIT_SHORT_CYCLE_PERIOD+" seconds");
                pollCyclePeriodSeconds= LIMIT_SHORT_CYCLE_PERIOD;
            }
        } else {
            if ( pollCyclePeriodSeconds<LIMIT_SHORT_REMOTE_CYCLE_PERIOD ) {
                System.err.println("pollCyclePeriodSeconds too low, for remote files it must be at least "+LIMIT_SHORT_REMOTE_CYCLE_PERIOD+" seconds");
                pollCyclePeriodSeconds= LIMIT_SHORT_REMOTE_CYCLE_PERIOD;
            }
        }
        this.pollCyclePeriodSeconds= pollCyclePeriodSeconds;
    }

    PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
        try {
            startPolling();
        } catch ( IOException ex ) {
            ex.printStackTrace();
        }
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
        if ( ! pcs.hasListeners(null) ) {
            stopPolling();
        }
    }

    long dirHash( DatumRange datumRange ) throws IOException {
        String[] ss= fsm.getBestNamesFor( datumRange, new NullProgressMonitor() );

        long hash= 1;
        for ( int i=0; i<ss.length; i++ ) {
            FileObject fo= fsm.getFileSystem().getFileObject(ss[i]);
            //Date lm= fo.lastModified(); //HTML FS doesn't give good dates.
            long sz= fo.getSize();
            //hash= hash + 17 * lm.hashCode() + 31 * sz + 31 * ss[i].hashCode();
            hash= hash + 31 * sz + 31 * ss[i].hashCode();
        }

        return hash;

    }
    public void startPolling( ) throws IOException {

        //System.err.println("start polling");
        if ( dirHash!=0 || polling ) {
            return;
        }
        dirHash= dirHash( this.dr );
        //System.err.println("start polling "+this.fsm+ " in " + this.dr);
        Runnable run= new Runnable() {
            public void run() {
                while ( dirHash!=0 ) {
                    try {
                        Thread.sleep( pollCyclePeriodSeconds*1000 );
                    } catch (InterruptedException ex) {
                        Logger.getLogger(AggregationPollUpdating.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    try {
                        long dirHash1= dirHash(dr);
                        if ( dirHash!=0 && dirHash1!=dirHash ) {
                            dirty= true;
                            dirHash= dirHash1;
                        }
                        if ( dirty && dirHash==dirHash1 ) {
                            pcs.firePropertyChange( Updating.PROP_DATASET, null, null );
                            dirty= false;
                        }
                    } catch ( IOException ex ) {
                        System.err.println(ex);
                        throw new RuntimeException(ex);
                    }
                }
                polling= false;
            }
        };
        polling= true;
        new Thread( run, "FilePollUpdating_" + dirHash ).start();
    }

    public void stopPolling() {
        //System.err.println("stop polling "+this.fsm+ " in " + this.dr);
        dirHash=0;
        polling= false;
    }

    public boolean isPolling() {
        return polling;
    }

    public static void main( String[] args ) throws FileSystemOfflineException, UnknownHostException, URISyntaxException, IOException, InterruptedException {
        FileStorageModelNew fsm= FileStorageModelNew.create( FileSystem.create( new URI("file:/home/jbf/eg/data/agg/") ), "hk_h0_mag_$Y$m$d_v02.cdf" );
        AggregationPollUpdating a= new AggregationPollUpdating( fsm, null, 1 );
        a.addPropertyChangeListener( new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                System.err.println( evt.toString() );
            } 
        });

        a.addPropertyChangeListener( new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                System.err.println( evt.getNewValue() + "  " + Thread.currentThread().getName() );
            }
        });

        Thread.sleep(6000);
        a.addPropertyChangeListener( new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                System.err.println( evt.getNewValue() + "  " + Thread.currentThread().getName() + "  *** ");
            }
        });

    }
}
