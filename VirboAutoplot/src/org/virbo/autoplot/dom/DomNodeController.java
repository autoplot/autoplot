/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.virbo.autoplot.LogNames;
import org.virbo.autoplot.dom.ChangesSupport.DomLock;

/**
 * Base class for controller objects that are responsible for managing a node.
 * 
 * @author jbf
 */
public class DomNodeController {

    protected static final Logger logger= org.das2.util.LoggerManager.getLogger( LogNames.AUTOPLOT_DOM );

    DomNode node;

    protected PropertyChangeSupport propertyChangeSupport = new DebugPropertyChangeSupport(this);
    protected final ChangesSupport changesSupport = new ChangesSupport(propertyChangeSupport, this);

    private static WeakHashMap<DomNode,Long> instances= new WeakHashMap();

    private static long t0= System.currentTimeMillis();

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
     * @return
     */
    protected static String insertString( String title, String label, String value ) {
        String search;
        search= "%{"+label+"}";
        if ( title.contains( search ) ) {
            title= title.replace( search, value );
        }
        search= "$("+label+")";
        if ( title.contains( search ) ) {
            title= title.replace( search, value );
        }
        return title;
    }

    /**
     * return true if %{LABEL} or $(LABEL) is found.
     * @param ptitle
     * @param label
     * @return
     */
    protected static boolean containsString( String ptitle, String label, String value ) {
        String search;
        String[] ss=null;
        search= "%{"+label+"}";
        if ( ptitle.contains( search ) ) {
            ss= ptitle.split("%\\{"+label+"\\}",-2);
        } else {
            search= "$("+label+")";
            if ( ptitle.contains( search ) ) {
                ss= ptitle.split("\\$\\("+label+"\\)",-2);
            }
        }
        if ( ss!=null && value.startsWith(ss[0]) && value.endsWith(ss[1]) ) {
            return true;
        } else {
            return false;
        }
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
            logger.log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            logger.log(Level.SEVERE, null, ex);
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
                if ( k.isPendingChanges() ) {
                    //Map<Object,Object> changes= new HashMap();
                    //k.pendingChanges(changes);
                    //if ( changes.isEmpty() ) {
                    //    k.pendingChanges(changes);
                    //    if ( k.isPendingChanges() ) {
                    //        System.err.println("Wei's problem!");
                    //    }
                    //}
                    //useful for debugging, see https://sourceforge.net/tracker/index.php?func=detail&aid=3410461&group_id=199733&atid=970682
                    //for ( Object o:  k.changesSupport.changesPending.keySet() ) {
                    //    System.err.println( "pending change "+o );
                    //}
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
