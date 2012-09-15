/**
 * 
 */

package org.virbo.autoplot.dom;

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
import org.virbo.autoplot.util.TransparentLogger;

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
    WeakReference<Object> parent;
    private static final Logger logger= Logger.getLogger( "autoplot.dom" );

    /**
     * if the propertyChangeSupport is provided, then change messages will be sent to
     * it directly.  If null, then one is created with the parent as the source.
     * @param pcs
     * @param parent  the object this is supporting, for debugging purposes.
     */
    ChangesSupport( PropertyChangeSupport pcs, Object parent ) {
        this.parent= new WeakReference<Object>(parent);
        this.changesPending= new HashMap<Object,Object>(); // client->lock
        if ( pcs==null ) {
            pcs= new PropertyChangeSupport(parent);
        }
        this.propertyChangeSupport= pcs;
    }

    /**
     * returns the clients who have registed the change.  Note this
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
            } else {
                return;
            }
        }
        boolean oldVal= this.isPendingChanges();
        changesPending.put( lockObject, client );
        propertyChangeSupport.firePropertyChange( PROP_PENDINGCHANGES, oldVal, isPendingChanges() );
    }

    /**
     * performingChange tells that the change is about to be performed.  This
     * is a place holder in case we use a mutator lock, but currently does
     * nothing.  If the change has not been registered, it will be registered implicitly.
     * @param client the object that is mutating the bean.
     * @param lockObject an object identifying the change.  
     */
    synchronized void performingChange( Object client, Object lockObject ) {
        Object c= changesPending.get(lockObject);
        if ( c==null || c!=client ) {
            registerPendingChange( client, lockObject );
        }
        logger.log( Level.FINE, "performingChange {0} by {1}  in {2}", new Object[]{lockObject, client, parent});
    }

    /**
     * the change is complete, and as far as the client is concerned, the canvas
     * is valid.
     * @param lockObject
     */
    synchronized void changePerformed( Object client, Object lockObject ) {
        logger.log( Level.FINE, "clearPendingChange {0} by {1}  in {2}", new Object[]{lockObject, client, parent});
        if ( changesPending.get(lockObject)==null ) {
           // throw new IllegalStateException( "no such lock object: "+lockObject );  //TODO: handle multiple registrations by the same client
        }
        boolean oldVal= this.isPendingChanges();
        changesPending.remove(lockObject);
        propertyChangeSupport.firePropertyChange( PROP_PENDINGCHANGES, oldVal, isPendingChanges() );
    }

    public static final String PROP_PENDINGCHANGES = "pendingChanges";

    /**
     * someone has registered a pending change.
     */
    public boolean isPendingChanges() {
        if ( changesPending.size() > 0 ) {
            return true;
        } else {
            return false;
        }

    }

    /**
     * null, "", or a description of the change
     */
    public static final String PROP_VALUEADJUSTING = "valueAdjusting";

    /**
     * the bean state is rapidly changing.
     * @return
     */
    public String isValueAdjusting() {
        return valueIsAdjusting;
    }

    private String valueIsAdjusting = null;

    private DomLock mutatorLock = new DomLock();

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

    
    private PropertyChangeSupport propertyChangeSupport;

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
