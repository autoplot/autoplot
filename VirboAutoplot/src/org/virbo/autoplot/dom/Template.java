/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.beans.PropertyChangeSupport;
import java.util.HashMap;
import java.util.Map;

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

    public Map<String, String> diffs(DomNode node) {
        return new HashMap<String,String>();
    }

}
