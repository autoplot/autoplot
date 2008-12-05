/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import org.das2.graph.DasAxis;

/**
 *
 * @author jbf
 */
public class AxisController {
    
    DasAxis dasAxis;
    private Application dom;
    
    public AxisController( Application dom, Axis axis, DasAxis dasAxis ) {
        this.dom= dom;
        this.dasAxis= dasAxis;
        axis.controller= this;
    }
    
    public boolean valueIsAdjusting() {
        return dasAxis.valueIsAdjusting();
    }

}
