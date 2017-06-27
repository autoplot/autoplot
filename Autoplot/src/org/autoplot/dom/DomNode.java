
package org.autoplot.dom;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import org.das2.util.LoggerManager;

/**
 * Autoplot's state is stored in a tree of nodes, with Java types constraining to particular abstractions.  Each node
 * can have children, and implements:<ul>
 *  <li> copy -- make a copy of this node and its children.
 *  <li> syncTo -- make this node look like another node.  (e.g. undo to old state). syncTo can also be told to exclude properties.
 *  <li> diffs -- show differences between this and another node.
 * </ul>
 * All nodes have the property "id" which must be unique within the tree.  Each node has properties of the following types:<ul>
 *  <li> String
 *  <li>  double
 *  <li>  boolean
 *  <li>  Datum
 *  <li>  DatumRange
 *  <li>  Color
 *  <li>  RenderType
 *  <li>  PlotSymbol
 *  <li>  PsymConnector
 *  <li>  Enum
 *  <li>  Connector
 *  <li>  LegendPosition
 *  <li>  AnchorPosition
 *</ul>
 * Any DomNode can be saved and restored using SerializeUtil, which uses Java introspection to look at all the properties.
 *
 * Some nodes currently have the method "setXAutomatically" where X is a property.  This is currently used to indicate who is setting
 * the property.  For example, PlotElement has setComponentAutomatically for use when a slice is set automatically by the application.
 * This is handled specially in SerializeUtil
 *
 * @author jbf
 */
public abstract class DomNode implements Cloneable {

    protected static final Logger logger= LoggerManager.getLogger( "autoplot.dom" );
    /**
     * returns a deep copy of the node.
     * @return
     */
    public DomNode copy() {
        try {
            DomNode result = (DomNode) clone();
            result.propertyChangeSupport = new DebugPropertyChangeSupport(result);
            return result;
        } catch (CloneNotSupportedException ex) {
            throw new RuntimeException(ex);
        }
    }

//    @Override
//    protected void finalize() throws Throwable {
//        super.finalize();
//        System.err.println("finalize DomNode: \""+ this.getId() + "\" " + this.getClass()  );
//    }


    /**
     * bulk assignment of properties.  When the node's children differ, then a controller should be used
     * to implement the sync.
     * Syncing should include the node's ID.  
     * @param n
     */
    public void syncTo( DomNode n ) {
        this.id= n.id; 
    }

    /**
     * Bulk assignment of properties, but allow specification of properties to exclude.  Note exclude
     * should be passed to children when this is overriden, normally to exclude id property.
     *
     * @param n
     * @param exclude
     */
    public void syncTo( DomNode n, List<String> exclude ) {
        if ( !exclude.contains(PROP_ID) ) this.id= n.id;
    }
    
    /**
     * return any child nodes.
     * @return
     */
    public List<DomNode> childNodes() {
        return Collections.emptyList();
    }

    /**
     * return a list of the differences between this and another node.  The
     * differences describe how to mutate that node to make it like this
     * node.
     * @param that
     * @return
     */
    public List<Diff> diffs( DomNode that ) {
        List<Diff> result = new ArrayList();

        boolean b;

        b= that.id.equals(this.id);
        if ( !b ) result.add( new PropertyChangeDiff( PROP_ID, that.id, this.id ));
        return result;

    }

    public DomNode() {        
        id= "";
    }
    
    
    protected String id;
    public static final String PROP_ID = "id";

    public String getId() {
        return id;
    }

    public void setId(String id) {
        String oldId = this.id;
        this.id = id;
        propertyChangeSupport.firePropertyChange(PROP_ID, oldId, id);
    }

    @Override
    public String toString() {
        return id;
    }
    
    
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(propertyName, listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
//        if ( propertyChangeSupport.getPropertyChangeListeners().length>120 ) {
//            System.err.println("== "+this.id+" pcls.length="+ propertyChangeSupport.getPropertyChangeListeners().length);
//            String [] props= ((DebugPropertyChangeSupport)propertyChangeSupport).getPropNames();
//            for ( String s: props ) {
//                System.err.println("  "+s );
//            }
//        }
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
//        if ( propertyChangeSupport.getPropertyChangeListeners().length>120 ) {
//            System.err.println("== "+this.id+" pcls.length="+ propertyChangeSupport.getPropertyChangeListeners().length);
//        }
    }

    protected PropertyChangeSupport propertyChangeSupport = new DebugPropertyChangeSupport(this);

    /**
     * try and find the leaks caused by binding...
     * @return 
     */
    public int boundCount() {
        return propertyChangeSupport.getPropertyChangeListeners().length;
    }
}
