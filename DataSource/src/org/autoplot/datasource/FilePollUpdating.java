/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.datasource;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileObject;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.LocalFileSystem;
import org.autoplot.datasource.capability.Updating;

/**
 *
 * @author jbf
 */
public class FilePollUpdating implements Updating {

    private static final Logger logger= LoggerManager.getLogger("apdss.updating"); 
    URI pollURI;
    FileSystem fs; // remote source of the file
    FileObject fo; // remote FileObject.
    //long pollMtime;
    //long pollMsize;
    long dirHash;
    long pollCyclePeriodSeconds;
    private final static int LIMIT_SHORT_CYCLE_PERIOD_SECONDS= 1;
    private final static int LIMIT_SHORT_REMOTE_CYCLE_PERIOD_SECONDS= 10; 
    boolean dirty= false; //true indicates the hash has changed and we need to clean.
    //boolean polling= false; //true indicates we are polling.    
    
    private static Map<Thread,URI> myThreads= new HashMap();
    
    public FilePollUpdating( URI uri, long pollCyclePeriodSeconds ) throws FileSystem.FileSystemOfflineException, UnknownHostException, FileNotFoundException {
        this.pollURI= uri;
        URISplit split= URISplit.parse(uri);
        fs= FileSystem.create(split.path);
        fo= fs.getFileObject(split.file.substring(split.path.length()));

        if ( fs instanceof LocalFileSystem ) {
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

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
        if ( ! pcs.hasListeners(null) ) {
            stopPolling();
        }
    }
    
    private long dirHash( ) {
        Date lm= fo.lastModified(); // warning: HTML FS dates need to be verified.
        long sz= fo.getSize();

        long hash= 1 + 17 * lm.hashCode() + 31 * sz;

        return hash;

    }    
    
    public void startPolling( ) throws FileSystem.FileSystemOfflineException {

            //pollMsize= fo.getSize();
            //pollMtime= fo.lastModified().getTime();
            dirHash= dirHash();
            
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    logger.log(Level.FINE, "start polling {0}", pollURI );
                    while ( dirHash!=0 ) {
                        logger.log(Level.FINEST, "polling..." );
                        try {
                            Thread.sleep( pollCyclePeriodSeconds*1000 );  // there's a bug here, that we can't kill the thread for so many seconds.
                        } catch (InterruptedException ex) {
                            logger.log(Level.SEVERE, ex.getMessage(), ex);
                        }
                        long dirHash1= dirHash();
                        if ( dirHash!=0 && dirHash1!=dirHash ) {
                            dirty= true;
                            dirHash= dirHash1;
                        }
                        if ( dirty && dirHash==dirHash1 ) {
                            pcs.firePropertyChange( Updating.PROP_DATASET, null, null );
                            dirty= false;
                        }
                    }
                    myThreads.remove( Thread.currentThread() );
                }
            };
            
            if ( myThreads.size()<1000 ) {
                Thread t= new Thread( run, "FilePollUpdating" );
            
                myThreads.put( t,pollURI );
                t.start();
            } else {
                logger.warning("thread limit reached, FillPollUpdating fails.");
            }
                
    }
    
    public void stopPolling() {
        logger.log(Level.FINE, "stop polling {0}", pollURI );
        dirHash= 0;
    }

    
    public static void main( String[] args ) throws Exception {
        URI uri= URI.create("http://www-pw.physics.uiowa.edu/~jbf/autoplot/users/mark/filePollUpdate/foo.cdf");
        
        //URI uri= new File("/home/jbf/foo.vap").toURI();
        
        FilePollUpdating a= new FilePollUpdating( uri, 1 );
        
        a.startPolling();

        a.addPropertyChangeListener( new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                logger.log( Level.WARNING, "{0}  {1}", new Object[]{evt.getNewValue(), Thread.currentThread().getName()});
            }
        });

        Thread.sleep(6000);
        a.addPropertyChangeListener( new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                logger.log( Level.WARNING, "{0}  {1}  *** ", new Object[]{evt.getNewValue(), Thread.currentThread().getName()});
            }
        });

        
    }
}
