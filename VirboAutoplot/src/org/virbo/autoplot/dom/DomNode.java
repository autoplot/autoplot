/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 *
 * @author jbf
 */
public abstract class DomNode implements Cloneable {
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

    /**
     * bulk assignment of properties.  When the node's children differ, then a controller should be used
     * to implement the sync.
     * Syncing should include the node's ID.  (for now, it's not clear what the ramifications are...)
     * @param n
     */
    public void syncTo( DomNode n ) {
        //this.id= n.id;  don't sync ID!!!
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
    abstract List<Diff> diffs( DomNode node );

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
    
    
    public synchronized void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(propertyName, listener);
    }

    public synchronized void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    public synchronized void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
    }

    public synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    protected PropertyChangeSupport propertyChangeSupport = new DebugPropertyChangeSupport(this);

}
