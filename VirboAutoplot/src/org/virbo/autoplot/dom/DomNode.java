/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import org.das2.util.LoggerManager;
import org.virbo.autoplot.LogNames;

/**
 * Autoplot's state is stored in a tree of nodes, with Java types constraining to particular abstractions.  Each node
 * can have children, and implements:
 *   copy -- make a copy of this node and its children.
 *   syncTo -- make this node look like another node.  (e.g. undo to old state). syncTo can also be told to exclude properties.
 *   diffs -- show differences between this and another node.
 * All nodes have the property "id" which must be unique within the tree.  Each node has properties of the following types:
 *   String
 *   double
 *   boolean
 *   Datum
 *   DatumRange
 *   Color
 *   RenderType
 *   PlotSymbol
 *   PsymConnector
 *   Enum
 *   Connector
 *   LegendPosition
 *
 * Any DomNode can be saved and restored using SerializeUtil, which uses Java introspection to look at all the properties.
 *
 * Some nodes currently have the method "setXAutomatically" where X is a property.  This is currently used to indicate who is setting
 * the property.  For example, PlotElement has setComponentAutomatically for use when a slice is set automatically by the application.
 * This is handled specially in SerializeUtil
 *
 * @author jbf
 */
public abstract class DomNode implements Cloneable {

    protected static final Logger logger= LoggerManager.getLogger( LogNames.AUTOPLOT_DOM );
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
     * @param node
     * @return
     */
    public List<Diff> diffs( DomNode that ) {
        List<Diff> result = new ArrayList<Diff>();

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
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    protected PropertyChangeSupport propertyChangeSupport = new DebugPropertyChangeSupport(this);

}
