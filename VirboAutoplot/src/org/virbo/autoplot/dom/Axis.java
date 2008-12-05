/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.beans.binding.BindingContext;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.das2.graph.DasAxis;

/**
 *
 * @author jbf
 */
public class Axis extends DomNode {
    protected DatumRange range = new DatumRange(0, 100, Units.dimensionless);
    public static final String PROP_RANGE = "range";

    public DatumRange getRange() {
        return range;
    }

    public void setRange(DatumRange range) {
        DatumRange oldRange = this.range;
        this.range = range;
        propertyChangeSupport.firePropertyChange(PROP_RANGE, oldRange, range);
    }

    protected boolean log = false;
    public static final String PROP_LOG = "log";

    public boolean isLog() {
        return log;
    }

    public void setLog(boolean log) {
        boolean oldLog = this.log;
        this.log = log;
        propertyChangeSupport.firePropertyChange(PROP_LOG, oldLog, log);
    }

    protected String label = "";
    
    /**
     * consise label for the axis.
     */
    public static final String PROP_LABEL = "label";

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        String oldLabel = this.label;
        this.label = label;
        propertyChangeSupport.firePropertyChange(PROP_LABEL, oldLabel, label);
    }
    
    AxisController controller;
    
    public AxisController getController() {
        return controller;
    }
    
    BindingContext axisBindingContext= null;
    DasAxis axis;
    
    public synchronized void bindTo( DasAxis p ) {
        if ( axisBindingContext!=null ) axisBindingContext.unbind();
        axisBindingContext= new BindingContext();
        axisBindingContext.addBinding( this, "${range}", p, "datumRange" );
        axisBindingContext.addBinding( this, "${log}", p, "log" );
        axisBindingContext.addBinding( this, "${label}", p, "label" );
        axisBindingContext.bind();
    }

    public void syncTo(DomNode n) {
        Axis that= (Axis)n;
        this.setLog( that.isLog() );
        this.setRange( that.getRange() );
        this.setLabel( that.getLabel() );
        
    }

    public Map<String, String> diffs(DomNode node) {
        Axis that= (Axis)node;
        LinkedHashMap<String,String> result= new  LinkedHashMap<String,String>();
        boolean b;
        
        b= that.log==this.log ;
        if ( !b ) result.put("log" , that.log+ " to " +this.log);
        b=  that.range.equals(this.range) ;
        if ( !b ) result.put("range", DomUtil.describe( that.range , this.range ) );
        b=  that.label.equals(this.label) ;
        if ( !b ) result.put("label", that.label + " to " + this.label );
        return result;
    }

}
