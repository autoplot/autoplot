/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.util.ArrayList;
import java.util.List;

/**
 *
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

    public DomNode copy() {
        Template result= (Template)super.copy();
        result.controller= null;  // CanvasController
        // handle children
        return result;
    }

    public void syncTo(DomNode n) {
        
    }

    public List<Diff> diffs(DomNode node) {
        return new ArrayList<Diff>();
    }

}
