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
public class Row extends DomNode {

    protected String parent;

    /**
     * the parent Row, or the canvas id.
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

    protected String top = "2em";
    public static final String PROP_TOP = "top";

    public String getTop() {
        return top;
    }

    public void setTop(String top) {
        String oldTop = this.top;
        this.top = top;
        propertyChangeSupport.firePropertyChange(PROP_TOP, oldTop, top);
    }


    protected String bottom = "100%-3em";
    public static final String PROP_BOTTOM = "bottom";

    public String getBottom() {
        return bottom;
    }

    public void setBottom(String bottom) {
        System.err.println("setBottom("+bottom+")");
        String oldBottom = this.bottom;
        this.bottom = bottom;
        propertyChangeSupport.firePropertyChange(PROP_BOTTOM, oldBottom, bottom);
    }

    protected RowController controller = new RowController(this);

    public RowController getController() {
        return controller;
    }

    public void setController(RowController controller) {
        this.controller = controller;
    }

    @Override
    public DomNode copy() {
        Row that= (Row)super.copy();
        that.controller= null;
        return that;
    }


    @Override
    List<Diff> diffs(DomNode node) {
        return DomUtil.getDiffs( this, node );
    }

    List<Diff> diffs(DomNode node, List<String> exclude) {
        return DomUtil.getDiffs( this, node, exclude );
    }

    @Override
    public void syncTo(DomNode n) {
        DomUtil.syncTo(this,n);
    }

    public void syncTo(DomNode n, List<String> exclude ) {
        DomUtil.syncTo(this,n,exclude);
    }


}
