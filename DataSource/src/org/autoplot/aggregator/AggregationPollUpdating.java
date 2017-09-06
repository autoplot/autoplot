/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.aggregator;

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
import org.das2.fsm.FileStorageModel;
import org.das2.util.filesystem.FileObject;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.FileSystem.FileSystemOfflineException;
import org.das2.util.filesystem.LocalFileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.autoplot.datasource.capability.Updating;

/**
 * Aggregation polling checks for updates of the set, for a given timerange. 
 * @author jbf
 */
public class AggregationPollUpdating implements Updating {

    private static final Logger logger= Logger.getLogger("apdss.agg.updating");

    FileStorageModel fsm;
    DatumRange dr;
    long dirHash;
    long pollCyclePeriodSeconds= -1;
    boolean dirty= false; //true indicates the hash has changed and we need to clean.
    boolean polling= false; //true indicates we are polling.

    private final static int LIMIT_SHORT_CYCLE_PERIOD_SECONDS= 1;
    private final static int LIMIT_SHORT_REMOTE_CYCLE_PERIOD_SECONDS= 10;

    public AggregationPollUpdating( FileStorageModel fsm, DatumRange dr, long pollCyclePeriodSeconds ) {
        this.fsm= fsm;
        this.dr= dr;
        if ( fsm.getFileSystem() instanceof LocalFileSystem ) {
            if ( pollCyclePeriodSeconds<LIMIT_SHORT_CYCLE_PERIOD_SECONDS ) {
                logger.log(Level.FINE, "pollCyclePeriodSeconds too low, for local files it must be at least {0} seconds", LIMIT_SHORT_CYCLE_PERIOD_SECONDS);
                pollCyclePeriodSeconds= LIMIT_SHORT_CYCLE_PERIOD_SECONDS;
            }
        } else {
            if ( pollCyclePeriodSeconds<LIMIT_SHORT_REMOTE_CYCLE_PERIOD_SECONDS ) {
                logger.log(Level.FINE, "pollCyclePeriodSeconds too low, for remote files it must be at least {0} seconds", LIMIT_SHORT_REMOTE_CYCLE_PERIOD_SECONDS);
                pollCyclePeriodSeconds= LIMIT_SHORT_REMOTE_CYCLE_PERIOD_SECONDS;
            }
        }
        this.pollCyclePeriodSeconds= pollCyclePeriodSeconds;
    }

    PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
        try {
            startPolling();
        } catch ( IOException ex ) {
            logger.severe(ex.getLocalizedMessage());
        }
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
        if ( ! pcs.hasListeners(null) ) {
            stopPolling();
        }
    }

    private long dirHash( DatumRange datumRange ) throws IOException {
        String[] ss= fsm.getBestNamesFor( datumRange, new NullProgressMonitor() );

        long hash= 1L;
        for (String s : ss) {
            FileObject fo = fsm.getFileSystem().getFileObject(s);
            Date lm= fo.lastModified(); //HTML FS doesn't give good dates.
            long sz= fo.getSize();
            hash = hash + 17L * lm.hashCode() + 31L * sz + 31L * s.hashCode();
            //hash= hash + 31 * sz + 31 * ss[i].hashCode();
        }

        return hash;

    }
    public void startPolling( ) throws IOException {

        logger.fine("start polling");
        if ( dirHash!=0 || polling ) {
            return;
        }
        dirHash= dirHash( this.dr );
        logger.log(Level.FINE, "start polling {0} in {1}", new Object[]{this.fsm, this.dr});
        Runnable run= new Runnable() {
            public void run() {
                while ( dirHash!=0 ) {
                    logger.log(Level.FINER, "polling {0} in {1}", new Object[]{AggregationPollUpdating.this.fsm, AggregationPollUpdating.this.dr});
                    try {
                        Thread.sleep( pollCyclePeriodSeconds*1000 );
                    } catch (InterruptedException ex) {
                        logger.log(Level.SEVERE, ex.getMessage(), ex);
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
                        logger.severe(ex.toString());
                        throw new RuntimeException(ex);
                    }
                }
                polling= false;
            }
        };
        polling= true;
        new Thread( run, "FilePollUpdating_" + dirHash ).start();
    }

    /**
     * stop the polling.
     */
    public void stopPolling() {
        logger.log(Level.FINE, "stop polling {0} in {1}", new Object[]{this.fsm, this.dr});
        dirHash=0;
        polling= false;
    }

    public boolean isPolling() {
        return polling;
    }

    public static void main( String[] args ) throws FileSystemOfflineException, UnknownHostException, URISyntaxException, IOException, InterruptedException {
        FileStorageModel fsm= FileStorageModel.create( FileSystem.create( new URI("file:/home/jbf/eg/data/agg/") ), "hk_h0_mag_$Y$m$d_v02.cdf" );
        AggregationPollUpdating a= new AggregationPollUpdating( fsm, null, 1 );
        a.addPropertyChangeListener( new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                logger.fine( evt.toString() );
            } 
        });

        a.addPropertyChangeListener( new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                logger.log( Level.FINE, "{0}  {1}", new Object[]{evt.getNewValue(), Thread.currentThread().getName()});
            }
        });

        Thread.sleep(6000);
        a.addPropertyChangeListener( new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                logger.log( Level.FINE, "{0}  {1}  *** ", new Object[]{evt.getNewValue(), Thread.currentThread().getName()});
            }
        });

    }
}
