/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.system.MutatorLock;

/**
 * Base class for controller objects that are responsible for managing a node.
 * 
 * @author jbf
 */
public class DomNodeController {

    DomNode node;

    protected PropertyChangeSupport propertyChangeSupport = new DebugPropertyChangeSupport(this);
    protected ChangesSupport changesSupport = new ChangesSupport(propertyChangeSupport, this);

    public DomNodeController( DomNode node ) {
        this.node= node;
    }

    /**
     * return the controller for the node, if it exists, through introspection.
     * @param n
     * @return the controller or null.
     */
    public static DomNodeController getController( DomNode n ) {
        try {
            Method m= n.getClass().getMethod( "getController" );
            return (DomNodeController) m.invoke( n );
        } catch (IllegalAccessException ex) {
            Logger.getLogger(DomNodeController.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(DomNodeController.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(DomNodeController.class.getName()).log(Level.SEVERE, null, ex);
        } catch ( NoSuchMethodException ex ) {
        }
        return null;
    }

    private List<DomNodeController> getChildControllers() {
        List<DomNodeController> result= new ArrayList();
        List<DomNode> kids= node.childNodes();
        for ( DomNode k: kids ) {
            DomNodeController kc= getController( k );
            if ( kc!=null ) {
                result.add(kc);
            }
        }
        return result;
    }

    /**
     * Some sort of processing is going on, so wait until idle.
     * @return
     */
    public boolean isPendingChanges() {
        if (changesSupport.isPendingChanges()) {
            return true;
        } else {
            List<DomNodeController> kids= getChildControllers();
            for ( DomNodeController k: kids ) {
                if ( k.isPendingChanges() ) return true;
            }
        }
        return false;
    }

    /**
     * return a list of all the pending changes.  These are returned in a
     * Map that goes from pending change to change manager.  Note this will
     * recurse through all the children, so to see pending changes
     * for the application, just call this on it's controller.
     *
     * @param changes a Map to which the changes will be added.
     */
    public void pendingChanges( Map<Object,Object> changes ) {
        if (changesSupport.isPendingChanges()) {
            changes.putAll( changesSupport.changesPending );
        }
        List<DomNodeController> kids= getChildControllers();
        for ( DomNodeController k: kids ) {
            k.pendingChanges(changes);
        }
    }

    /**
     * the application state is rapidly changing.
     * @return
     */
    public boolean isValueAdjusting() {
        return changesSupport.isValueAdjusting();
    }

    protected Lock mutatorLock() {
        return changesSupport.mutatorLock();
    }

    public void registerPendingChange(Object client, Object lockObject) {
        changesSupport.registerPendingChange(client, lockObject);
    }

    public void performingChange(Object client, Object lockObject) {
        changesSupport.performingChange(client, lockObject);
    }

    public void changePerformed(Object client, Object lockObject) {
        changesSupport.changePerformed(client, lockObject);
    }

    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(propertyName, listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }


}
