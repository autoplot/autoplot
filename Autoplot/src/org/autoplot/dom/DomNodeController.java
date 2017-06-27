
package org.autoplot.dom;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.autoplot.dom.ChangesSupport.DomLock;

/**
 * Base class for controller objects that are responsible for managing a node.
 * 
 * @author jbf
 */
public class DomNodeController {

    protected static final Logger logger= org.das2.util.LoggerManager.getLogger( "autoplot.dom" );

    DomNode node;

    protected PropertyChangeSupport propertyChangeSupport = new DebugPropertyChangeSupport(this);
    protected final ChangesSupport changesSupport = new ChangesSupport(propertyChangeSupport, this);

    private static final WeakHashMap<DomNode,Long> instances= new WeakHashMap();

    private static final long t0= System.currentTimeMillis();

    public DomNodeController( DomNode node ) {
        this.node= node;
        instances.put( node, ( System.currentTimeMillis()-t0 ) );
    }

    public static void printStats() {
        ArrayList<String> list= new ArrayList();
        for ( Entry<DomNode,Long> t: instances.entrySet() ) {
            list.add( t.getKey() + " " +t.getValue() );
        }
        Collections.sort(list);
        for ( String s: list ) {
            System.err.println(s);
        }
        System.err.println("("+list.size()+" items)");
    }
    /**
     * replace %{LABEL} or $(LABEL) with value.
     * @param title
     * @param label
     * @param value
     * @deprecated see LabelConverter.insertString( title, label, value );
     * @return
     */
    protected static String insertString( String title, String label, String value ) {
        return LabelConverter.insertString( title, label, value );
    }

    /**
     * return true if %{LABEL} or $(LABEL) is found.
     * @param ptitle
     * @param label
     * @param value
     * @deprecated see LabelConverter.containsString( ptitle, label, value );
     * @return
     */
    protected static boolean containsString( String ptitle, String label, String value ) {
        return LabelConverter.containsString( ptitle, label, value );
    }

    /**
     * return the controller for the node, if it exists.
     * This appeared to take a significant amount of time using introspection, 
     * so was recoded.  Note this is much faster, but it's trivial either way
     * and this runs the risk of a future new node not being handled.
     * (Test on 2016-03-14 showed 1e6 invocations of with introspection took
     * ~700ms, while this took 7ms.)
     * @param n the node
     * @return the controller or null.
     */
    public static DomNodeController getController( DomNode n ) {
        DomNodeController result;
        if ( n instanceof PlotElement ) {
            result= ((PlotElement)n).getController();
        } else if ( n instanceof Plot ) {
            result= ((Plot)n).getController();
        } else if ( n instanceof DataSourceFilter ) {
            result= ((DataSourceFilter)n).getController();
        } else if ( n instanceof Application ) {
            result= ((Application)n).getController();
        } else if ( n instanceof Axis ) {
            result= ((Axis)n).getController();
        } else if ( n instanceof Row ) {
            result= ((Row)n).getController();
        } else if ( n instanceof Column ) {
            result= ((Column)n).getController();
        } else if ( n instanceof Canvas ) {
            result= ((Canvas)n).getController();
        } else if ( n instanceof Annotation ) {
            result= ((Annotation)n).getController();
        } else {
            result= null;
        }
        return result;
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
     * @return true if there are changes pending.
     */
    public boolean isPendingChanges() {
        if (changesSupport.isPendingChanges()) {
            return true;
        } else {
            List<DomNodeController> kids= getChildControllers();
            for ( DomNodeController k: kids ) {
                if ( k.isPendingChanges() ) {
                    //Map<Object,Object> changes= new HashMap();
                    //k.pendingChanges(changes);
                    //if ( changes.isEmpty() ) {
                    //    k.pendingChanges(changes);
                    //    if ( k.isPendingChanges() ) {
                    //        System.err.println("Wei's problem!");
                    //    }
                    //}
                    //useful for debugging, see https://sourceforge.net/p/autoplot/bugs/756/
                    //for ( Object o:  k.changesSupport.changesPending.keySet() ) {
                    //    System.err.println( "pending change "+o );
                    //}
                    if ( logger.isLoggable(Level.FINER) ) {
                        logger.log(Level.FINER, "Node is pending changes: {0}", k);
                        Map<Object,Object> changesPending= new LinkedHashMap<>();
                        k.pendingChanges(changesPending);
                        for ( Entry<Object,Object> e : changesPending.entrySet() ) {
                            logger.log(Level.FINER, "{0} -> {1}", new Object[]{e.getKey(), e.getValue()});
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * return a list of all the pending changes.  These are returned in a
     * Map that goes from pending change to change manager.  Note this will
     * recurse through all the children, so to see pending changes
     * for the application, just call this on its controller.
     *
     * @param changes a Map to which the changes will be added.
     */
    public void pendingChanges( Map<Object,Object> changes ) {
        Map lchangesPending= changesSupport.getChangesPending();
        if ( !lchangesPending.isEmpty() ) {
            changes.putAll( lchangesPending );
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
        return changesSupport.isValueAdjusting()!=null;
    }

    /**
     * lock the node so that others cannot modify it.  Call the lock object's lock method with a name for the operation,
     * then unlock it when the operation is complete.  A try/finally block should be used in case exceptions occur, otherwise
     * the application will be left in an unusable state!
     * <tt>
     * DomLock lock = dom.controller.mutatorLock();
     * lock.lock( "Sync to Application" );
     *    do atomic operation...
     * lock.unlock()
     * </tt>
     * @return
     */
    public DomLock mutatorLock() {
        return changesSupport.mutatorLock();
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
     * @see #performingChange(java.lang.Object, java.lang.Object) 
     * @see #changePerformed(java.lang.Object, java.lang.Object) 
     * 
     */
    public void registerPendingChange(Object client, Object lockObject) {
        changesSupport.registerPendingChange(client, lockObject);
    }

    /**
     * performingChange tells that the change is about to be performed.  This
     * is a place holder in case we use a mutator lock, but currently does
     * nothing.  If the change has not been registered, it will be registered implicitly.
     * @param client the object that is mutating the bean.
     * @param lockObject an object identifying the change.  
     * @see #registerPendingChange(java.lang.Object, java.lang.Object) 
     * @see #changePerformed(java.lang.Object, java.lang.Object) 
     */
    public void performingChange(Object client, Object lockObject) {
        changesSupport.performingChange(client, lockObject);
    }

    /**
     * the change is complete, and as far as the client is concerned, the canvas
     * is valid.
     * @param client the object that is mutating the bean.
     * @param lockObject an object identifying the change.  
     * @see #registerPendingChange(java.lang.Object, java.lang.Object) 
     * @see #performingChange(java.lang.Object, java.lang.Object) 
     */    
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
