/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
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

    private static ReferenceCache instance;

    private final Map<String,ReferenceCacheEntry> uris= new LinkedHashMap();

    private final Map<String,ProgressMonitor> locks= new LinkedHashMap();

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
        public boolean shouldILoad( Thread t ) {
            return ( this.loadThread==t );
        }
        /**
          * park this thread until the other guy has finished loading.
          * @param ent
          * @param monitor
          */
        public QDataSet park( ProgressMonitor mon ) throws Exception {
            getInstance().park( this, monitor );
            if ( this.exception!=null ) {
                throw this.exception;
            } else {
                return this.qds.get();
            }
        }
        public void finished( QDataSet ds ) {
            this.qds= new WeakReference<QDataSet>(ds);
            this.status= ReferenceCacheEntryStatus.DONE;
            return;
        }
        public void exception( Exception ex ) {
            this.exception= ex;
            this.status= ReferenceCacheEntryStatus.DONE;
            return;
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
        ReferenceCacheEntry result;
        synchronized (this) {
            result= uris.get(uri);
            if ( result!=null ) {
                if ( result.qds==null && ReferenceCacheEntryStatus.DONE==result.status ) { // it was garbage collected.
                    result= new ReferenceCacheEntry(uri,monitor);
                    result.loadThread= Thread.currentThread();
                    uris.put( uri, result );
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
     * @param monitor
     */
    public void park( ReferenceCacheEntry ent, ProgressMonitor monitor ) {
        if ( ent.loadThread==Thread.currentThread() ) {
            throw new IllegalStateException("This thread was supposed to load the data");
        }
        monitor.started();
        while ( true ) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Logger.getLogger(ReferenceCache.class.getName()).log(Level.SEVERE, null, ex);
            }
            if ( !monitor.isFinished() ) {
                ent.monitor.setTaskSize( ent.monitor.getTaskSize());
                ent.monitor.setTaskProgress( ent.monitor.getTaskProgress());
            }
            if ( ent.status==ReferenceCacheEntryStatus.DONE ) break;
        }
        monitor.finished();
    }

    public synchronized void putDataSet( String uri, QDataSet ds ) {
        ReferenceCacheEntry result= uris.get(uri);
        result.qds= new WeakReference<QDataSet>(ds);
        result.status= ReferenceCacheEntryStatus.DONE;
        return;
    }

}
