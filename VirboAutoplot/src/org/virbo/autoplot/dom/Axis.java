/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.util.ArrayList;
import java.util.List;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;

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
        try {
            DatumRange oldRange = this.range;
            this.range = range;
            propertyChangeSupport.firePropertyChange(PROP_RANGE, oldRange, range);
        } catch ( Exception e ) {
            System.err.println( propertyChangeSupport.toString() );
            e.printStackTrace();
        }
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
    
    protected boolean drawTickLabels = true;
    public static final String PROP_DRAWTICKLABELS = "drawTickLabels";

    public boolean isDrawTickLabels() {
        return drawTickLabels;
    }

    public void setDrawTickLabels(boolean drawTickLabels) {
        boolean oldDrawTickLabels = this.drawTickLabels;
        this.drawTickLabels = drawTickLabels;
        propertyChangeSupport.firePropertyChange(PROP_DRAWTICKLABELS, oldDrawTickLabels, drawTickLabels);
    }

    AxisController controller;
    
    public AxisController getController() {
        return controller;
    }

    public void syncTo(DomNode n) {
        Axis that= (Axis)n;
        this.setLog( that.isLog() );
        this.setRange( that.getRange() );
        this.setLabel( that.getLabel() );
        
    }

    public List<Diff> diffs(DomNode node) {
        Axis that= (Axis)node;
        List<Diff> result= new ArrayList<Diff>();
        boolean b;
        
        b= that.log==this.log ;
        if ( !b ) result.add( new PropertyChangeDiff( "log" , that.log, this.log) );
        b=  that.range.equals(this.range) ;
        if ( !b ) result.add(new PropertyChangeDiff("range", that.range , this.range ) );
        b=  that.label.equals(this.label) ;
        if ( !b ) result.add(new PropertyChangeDiff("label", that.label , this.label ) );
        b=  that.drawTickLabels==this.drawTickLabels;
        if ( !b ) result.add(new PropertyChangeDiff("drawTickLabels", that.drawTickLabels, this.drawTickLabels ) );
        
        return result;
    }

}
