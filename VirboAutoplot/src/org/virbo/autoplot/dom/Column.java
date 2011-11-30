/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.util.List;

/**
 * Dom node peer for DasRow
 * @author jbf
 */
public class Column extends DomNode {

    protected String parent="";

    /**
     * the parent Column, or the canvas id.
     */
    public static final String PROP_PARENT = "parent";

    public String getParent() {
        return parent;
    }

    public void setParent(String parent) {
        String oldParent = this.parent;
        this.parent = parent;
        propertyChangeSupport.firePropertyChange(PROP_PARENT, oldParent, parent);
    }

    protected String left = "2em";
    public static final String PROP_LEFT = "left";

    public String getLeft() {
        return left;
    }

    public void setLeft(String left) {
        String oldLeft = this.left;
        this.left = left;
        propertyChangeSupport.firePropertyChange(PROP_LEFT, oldLeft, left);
    }

    protected String right = "100%-3em";
    public static final String PROP_RIGHT = "right";

    public String getRight() {
        return right;
    }

    public void setRight(String right) {
        String oldRight = this.right;
        this.right = right;
        propertyChangeSupport.firePropertyChange(PROP_RIGHT, oldRight, right);
    }

    protected ColumnController controller = null;

    public ColumnController getController() {
        return controller;
    }

    @Override
    public DomNode copy() {
        Column that= (Column)super.copy();
        that.controller= null;
        return that;
    }
    
    @Override
    public List<Diff> diffs(DomNode node) {
        return DomUtil.getDiffs( this, node );
    }

    @Override
    public void syncTo(DomNode n) {
        DomUtil.syncTo(this,n);
    }
    
    @Override
    public void syncTo(DomNode n, List<String> exclude) {
        DomUtil.syncTo(this,n,exclude);
    }

}
