/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.dom;

import java.util.ArrayList;
import java.util.List;

/**
 * Template for making new nodes.  There's a lot to get right when 
 * changing the dom, and this should help.  
 * Make sure the node is mutable.  I added properties to the Connector
 * and this was tricky because before it was not mutable.
 * @author jbf
 */
public class Template extends DomNode {

    protected CanvasController controller;
    public static final String PROP_CONTROLLER = "controller";

    public Template() {
        //childListener
    }
    
    public CanvasController getController() {
        return controller;
    }

    protected void setController(CanvasController controller) {
        CanvasController oldController = this.controller;
        this.controller = controller;
        propertyChangeSupport.firePropertyChange(PROP_CONTROLLER, oldController, controller);
    }

    @Override
    public DomNode copy() {
        Template result= (Template)super.copy();
        result.controller= null;  // CanvasController
        // handle children
        return result;
    }

    @Override
    public void syncTo(DomNode n) {
        syncTo(n,new ArrayList<String>());
    }

    @Override
    public void syncTo(DomNode n,List<String> exclude ) {
        super.syncTo(n,exclude);
    }

    @Override
    public List<Diff> diffs(DomNode node) {
        return DomUtil.getDiffs( this, node );
    }

}
