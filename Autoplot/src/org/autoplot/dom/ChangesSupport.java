/**
 * 
 */

package org.autoplot.dom;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Support class for encapsulating implementation of pendingChanges and mutator locks.
 *
 * PendingChanges are a way of notifying the bean and other clients using the bean that changes are coming to
 * the bean.  Clients should use registerPendingChange to indicate that changes are coming.
 * performingChange indicates the change is in progress.  changePerformed indicates to other clients
 * and the bean that the change is complete.  For example, event thread registers pending change
 * and creates runnable object.  A new thread is started.  On the new thread, performingChange
 * and changePerformed is called.
 *
 * mutatorLock() is a way for a client to get exclusive, read-only access to a bean.
 * This also sets the valueAdjusting property.  WARNING: this is improperly implemented,
 * and clients must check valueIsAdjusting to see if another lock is out.
 *
 * See http://das2.org/wiki/index.php/Pending_changes
 * @author jbf
 */
public final class ChangesSupport {
    Map<Object,Object> changesPending;
    
    // number of said changes, typically 1.
    Map<Object,Integer> changeCount;
    
    // threads, for debugging.
    Map<Object,String> threads;
    
    WeakReference<Object> parent;
    private static final Logger logger= org.das2.util.LoggerManager.getLogger( "autoplot.dom" );

    /**
     * if the propertyChangeSupport is provided, then change messages will be sent to
     * it directly.  If null, then one is created with the parent as the source.
     * @param pcs
     * @param parent  the object this is supporting, for debugging purposes.
     */
    ChangesSupport( PropertyChangeSupport pcs, Object parent ) {
        this.parent= new WeakReference<>(parent);
        this.changesPending= new HashMap<>(); // lockObject -> client
        this.changeCount= new HashMap<>();
        this.threads= new HashMap<>(); // lockObject -> thread ID.
        if ( pcs==null ) {
            pcs= new PropertyChangeSupport(parent);
        }
        this.propertyChangeSupport= pcs;
    }

    /**
     * returns the clients who have registered the change.  Note this
     * implementation only allows for one client for each lock object.
     * @param lockObject object identifying the change.
     * @return clients who have registered the change.
     */
    synchronized List<Object> whoIsChanging( Object lockObject ) {
        String msg= "whoIsChanging "+lockObject;
        logger.fine( msg );
        Object client= changesPending.get(lockObject);
        if ( client==null ) {
            return Collections.emptyList();
        } else {
            return Collections.singletonList(client);
        }
    }
    
    /**
     * the client knows a change will be coming, and the canvas' clients should
     * know that its current state will change soon.  Example pending changes
     * would be:
     *   layout because tick labels are changing
     *   data is loading
     *
     * @param client the object that will perform the change.  This allows the
     *   canvas (and developers) identify who has registered the change.
     * @param lockObject object identifying the change.
     */
    synchronized void registerPendingChange( Object client, Object lockObject ) {
        String msg= "registerPendingChange "+lockObject+" by "+client + "  in "+ parent.get() ;
        logger.fine( msg );
        Object existingClient= changesPending.get(lockObject);
        if ( existingClient!=null ) {
            if ( existingClient!=client ) {
                throw new IllegalStateException( "lock object in use: "+lockObject + ", by "+changesPending.get(lockObject) );
            } else if ( existingClient==client ) {
                logger.log( Level.FINE, "bug 1075: second change registered but the first was not done."); // this is somewhat harmless.  Don't bug clients with this message.
            } else {
                return;
            }
        }
        boolean oldVal= this.isPendingChanges();
        changesPending.put( lockObject, client );
        threads.put( lockObject, Thread.currentThread().toString() );
        propertyChangeSupport.firePropertyChange( PROP_PENDINGCHANGES, oldVal, isPendingChanges() );
    }

    /**
     * performingChange tells that the change is about to be performed.  This
     * is a place holder in case we use a mutator lock, but currently does
     * nothing.  If the change has not been registered, it will be registered implicitly.
     * This will increment the internal count of how many times the change
     * ought to occur.
     * @param client the object that is mutating the bean.
     * @param lockObject an object identifying the change.  
     */
    synchronized void performingChange( Object client, Object lockObject ) {
        Object ownerClient= changesPending.get(lockObject);
        if ( ownerClient==null || ownerClient!=client ) {
            if ( ownerClient!=null && ownerClient!=client ) {
                logger.log(Level.INFO, "performingChange by client object is not owner {0}", client );
            }
            registerPendingChange( client, lockObject );
        }
        Integer count= changeCount.get(lockObject);
        if ( count==null ) {
            changeCount.put( lockObject, 1 );
        } else {
            changeCount.put( lockObject, count+1 );
        }

        logger.log( Level.FINE, "performingChange {0} by {1}  in {2}", new Object[]{lockObject, client, parent});
    }

    
    /**
     * the change is complete, and as far as the client is concerned, the canvas
     * is valid.  This will decrement the count of how many times the change ought to occur.
     * @param lockObject object identifying the change.
     */
    synchronized void changePerformed( Object client, Object lockObject ) {
        logger.log( Level.FINE, "clearPendingChange {0} by {1}  in {2}", new Object[]{lockObject, client, parent});
        Integer count= changeCount.get(lockObject);
        Object ownerClient= changesPending.get(lockObject);
        if ( ownerClient==null ) {
           // throw new IllegalStateException( "no such lock object: "+lockObject );  //TODO: handle multiple registrations by the same client
            logger.log(Level.INFO, "no lock object found for {0}", lockObject);
        } else if ( ownerClient!=client ) {
            logger.log(Level.INFO, "change performed client object is not owner {0}", ownerClient );
        }
        boolean oldVal= this.isPendingChanges();
        if ( count==null ) {
            logger.log(Level.INFO, "expect value for changeCount {0}, was performingChange called?", lockObject);
            count= 0;
        } else {
            count= count-1;
        }
        
        if ( count==0 ) {
            changesPending.remove(lockObject);
            threads.remove( lockObject );
            changeCount.remove(lockObject);
        } else if ( count>0) {
            changeCount.put(lockObject,count);
        } else {
            throw new IllegalStateException("what happened here--changeCount<0!");
        }


        propertyChangeSupport.firePropertyChange( PROP_PENDINGCHANGES, oldVal, isPendingChanges() );
    }

    public static final String PROP_PENDINGCHANGES = "pendingChanges";

    /**
     * someone has registered a pending change.
     * See getChangesPending.
     * @return true if someone has registered a pending change.
     */
    public boolean isPendingChanges() {
        return changesPending.size() > 0;
    }

    /**
     * allow check for particular change.
     * @param lockObject object identifying the change.
     * @return true if that particular change is pending.
     */
    public boolean isPendingChanges( Object lockObject ) {
        return changesPending.containsKey(lockObject);
    }
    
    /**
     * return a map listing the pending changes.  This is a thread-safe
     * read-only copy.
     * @return a map listing the pending changes.
     */
    public synchronized Map getChangesPending() {
        if ( changesPending.isEmpty() ) {
            return Collections.emptyMap();            
        } else {
            return new HashMap(changesPending);
        }
    }
    
    /**
     * null, "", or a description of the change
     */
    public static final String PROP_VALUEADJUSTING = "valueAdjusting";

    /**
     * Check if the bean state is rapidly changing.  This
     * returns the lock message, or null if the value
     * is not adjusting.
     * @return null or a message indicating that the value is adjusting.
     */
    public String isValueAdjusting() {
        return valueIsAdjusting;
    }

    private String valueIsAdjusting = null;

    private final DomLock mutatorLock = new DomLock();

    public class DomLock extends ReentrantLock {

        public DomLock( ) {
        }

        public void lock( String description ) {
            super.lock();
            if (valueIsAdjusting!=null) {
                //System.err.println("lock is already set!");
                //nested locks result in the outermost lock being used while inner locks are ignored.
            } else {
                valueIsAdjusting= description;
                propertyChangeSupport.firePropertyChange( PROP_VALUEADJUSTING, null, description );
            }
        }

        @Override
        public void lock() {
            this.lock("");
        }

        @Override
        public void unlock() {
            super.unlock();
            if ( !super.isLocked() ) {
                String old= valueIsAdjusting;
                valueIsAdjusting = null;
                propertyChangeSupport.firePropertyChange( PROP_VALUEADJUSTING, old, valueIsAdjusting );
            } else {
                //System.err.println("lock is still set, neat!");
            }
        }
    }

    /**
     * one client will have write access to the bean, and when unlock
     * is called, a "valueAdjusting" property change event is fired.
     * In the future, this
     * will return null if the lock is already out, but for now,
     * clients should check the valueIsAdjusting property.
     * @return
     */
    protected synchronized DomLock mutatorLock() {
        return mutatorLock;
    }

    
    private final PropertyChangeSupport propertyChangeSupport;

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    @Override
    public String toString() {
        return "changeSupport: "+ changesPending;
    }
}
