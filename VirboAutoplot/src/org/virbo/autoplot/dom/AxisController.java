/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.util.List;
import org.das2.graph.DasAxis;

/**
 *
 * @author jbf
 */
public class AxisController {
    
    DasAxis dasAxis;
    private Application dom;
    Axis axis;
    
    public AxisController( Application dom, Axis axis, DasAxis dasAxis ) {
        this.dom= dom;
        this.dasAxis= dasAxis;
        this.axis= axis;
        axis.controller= this;
    }
    
    public boolean valueIsAdjusting() {
        return dasAxis.valueIsAdjusting();
    }

    public synchronized void bindTo( DasAxis p ) {
        ApplicationController ac= dom.getController();
        ac.bind( axis, "range", p, "datumRange" );
        ac.bind( axis, "log", p, "log" );
        ac.bind( axis, "label", p, "label" );
        ac.bind( axis, "drawTickLabels", p, "tickLabelsVisible" );

    }

    public DasAxis getDasAxis() {
        return dasAxis;
    }

}
