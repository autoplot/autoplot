/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.QDataSet;

/**
 * Provide a cache of datasets that are in memory, so that the same data is not loaded twice.  This first implementation
 * uses WeakReferences, so that this cache need not be emptied, but we will avoid the situation where the same data is loaded
 * twice.
 *
 * @author jbf
 */
public class ReferenceCache {

    private static final Logger logger= LoggerManager.getLogger("apdss.refcache");

    private static ReferenceCache instance;

    private final Map<String,ReferenceCacheEntry> uris= new LinkedHashMap();

    private final Map<String,ProgressMonitor> locks= new LinkedHashMap();

    // no one can directly instantiate, see getInstance.
    private ReferenceCache() {
        
    }
    
    /**
     * get the single instance of the ReferenceCache
     * @return
     */
    public synchronized static ReferenceCache getInstance() {
        if ( instance==null ) {
            instance= new ReferenceCache();
        }
        return instance;
    }

    public enum ReferenceCacheEntryStatus {
        LOADING, DONE
    }

    /**
     * Keep track of the status of a load.  This keeps track of the thread that is actually
     * loading the data and it's
     */
    public static class ReferenceCacheEntry {
        String uri= null;
        WeakReference<QDataSet> qds=null; // this is a weak reference to the data.
        Exception exception=null;
        ProgressMonitor monitor=null; // the progress monitor for the load.
        Thread loadThread=null; // the thread
        ReferenceCacheEntryStatus status=null;
        ReferenceCacheEntry( String uri, ProgressMonitor monitor ) {
            this.status= ReferenceCacheEntryStatus.LOADING;
            this.uri= uri;
            this.monitor= monitor;
        }

        /**
         * query this to see if the current thread should load the resource, or just park while
         * the loading thread loads the resource.
         * @param t the current thread (Thread.currentThread())
         * @return
         */
        public boolean shouldILoad( Thread t ) {
            logger.log( Level.FINE, "shouldILoad({0})= {1}", new Object[]{Thread.currentThread(), this.loadThread==t } );
            return ( this.loadThread==t );
        }

        /**
          * park this thread until the other guy has finished loading.
          * @param ent
          * @param monitor
          */
        public QDataSet park( ProgressMonitor mon ) throws Exception {
            logger.log( Level.FINE, "parking thread {0} {1}", new Object[]{Thread.currentThread(), uri} );
            getInstance().park( this, mon );
            if ( this.exception!=null ) {
                throw this.exception;
            } else {
                return this.qds.get();
            }
        }
        public void finished( QDataSet ds ) {
            logger.log( Level.FINE, "finished {0} {1} {2}", new Object[]{Thread.currentThread(), ds, uri} );
            this.qds= new WeakReference<QDataSet>(ds);
            this.status= ReferenceCacheEntryStatus.DONE;
            return;
        }
        public void exception( Exception ex ) {
            logger.log( Level.FINE, "finished {0} {1} {2}", new Object[]{Thread.currentThread(), ex, uri} );
            this.exception= ex;
            this.status= ReferenceCacheEntryStatus.DONE;
            return;
        }

        @Override
        public String toString( ) {
            QDataSet _qds= qds.get();
            return String.format( "loadThread=%s\tmonitor=%s\tstatus=%s\turi=%s\tqds=%s", loadThread.getName(), monitor, status, uri, String.valueOf(_qds) );
        }

    }

    /**
     * Query to see if the dataset exists in the cache.  Null is returned if it is not, or a QDataSet is returned if it is.
     * @param uri
     * @return
     */
    public synchronized QDataSet getDataSet( String uri ) {
        ReferenceCacheEntry entry= uris.get(uri);
        if ( entry==null ) {
            return null;
        } else {
            if ( entry.qds==null ) {
                return null;
            } else {
                QDataSet ds= entry.qds.get();
                return ds;
            }
        }
    }

    /**
     * Either return the dataset or null as with getDataSet, but claim the lock if the client will be creating the dataset.
     * The result will indicate the status, and the method shouldILoad will indicate if this thread should load.
     *
     * Be sure to use try/finally when using this cache!
     *
     * @param uri
     * @return null if a lock has been set and the client should compute, or the QDataSet
     */
    public ReferenceCacheEntry getDataSetOrLock( String uri, ProgressMonitor monitor ) {
        tidy();
        logger.log( Level.FINEST, "getDataSetOrLock on thread {0} {1}", new Object[]{Thread.currentThread(), uri});
        ReferenceCacheEntry result;
        synchronized (this) {
            result= uris.get(uri);
            if ( result!=null ) {
                if ( ( result.qds==null || result.qds.get()==null ) && ReferenceCacheEntryStatus.DONE==result.status ) { // it was garbage collected.
                    result= new ReferenceCacheEntry(uri,monitor);
                    result.loadThread= Thread.currentThread();
                    uris.put( uri, result );
                } else {

                }
            } else {
                result= new ReferenceCacheEntry(uri,monitor);
                result.status= ReferenceCacheEntryStatus.LOADING;
                result.loadThread= Thread.currentThread();
                uris.put( uri, result );
            }
        }
        return result;
    }

    /**
     * park this thread until the other guy has finished loading.
     * @param ent
     * @param monitor the monitor of the load.
     */
    public void park( ReferenceCacheEntry ent, ProgressMonitor monitor ) {
        if ( ent.loadThread==Thread.currentThread() ) {
            throw new IllegalStateException("This thread was supposed to load the data");
        }
        monitor.started();
        monitor.setProgressMessage("waiting for load");
        while ( true ) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Logger.getLogger(ReferenceCache.class.getName()).log(Level.SEVERE, null, ex);
            }
            if ( !monitor.isFinished() ) {
                monitor.setTaskSize( ent.monitor.getTaskSize());
                monitor.setTaskProgress( ent.monitor.getTaskProgress());
            }
            if ( ent.status==ReferenceCacheEntryStatus.DONE ) break;
        }
        monitor.finished();
    }

    public synchronized void putDataSet( String uri, QDataSet ds ) {
        ReferenceCacheEntry result= uris.get(uri);
        logger.log( Level.FINEST, "putDataSet on thread {0} {1}", new Object[]{Thread.currentThread(), uri});
        result.qds= new WeakReference<QDataSet>(ds);
        result.status= ReferenceCacheEntryStatus.DONE;
        return;
    }

    /**
     * remove all the entries that have been garbage collected.
     */
    public synchronized void tidy() {
        List<String> rm= new ArrayList();
        for ( Entry<String,ReferenceCacheEntry> ent : instance.uris.entrySet() ) {
            ReferenceCacheEntry ent1= ent.getValue();
            if ( ent1.status==ReferenceCacheEntryStatus.DONE && ent1.qds!=null && ent1.qds.get()==null ) {
                rm.add(ent1.uri);
            }
        }
        for ( String uri: rm ) {
            instance.uris.remove(uri);
        }
    }

    /**
     * display the status of all the entries.
     */
    public synchronized void printStatus() {

        int i;

        System.err.println("== uris ==");
        i=0;
        for ( Entry<String,ReferenceCacheEntry> ent : instance.uris.entrySet() ) {
            System.err.printf( "%3d %s\n", ++i, ent.getValue() );
        }

        System.err.println("== locks ==");
        i=0;
        for ( Entry<String,ProgressMonitor> ent : instance.locks.entrySet() ) {
            System.err.printf( "%3d %s\n", ++i, ent.getValue() );
        }
    }

}
