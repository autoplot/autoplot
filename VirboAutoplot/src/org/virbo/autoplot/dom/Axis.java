/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;

/**
 * The state of an axis, X, Y, or a Z axis colorbar.
 * @author jbf
 */
public class Axis extends DomNode {

    public static final DatumRange DEFAULT_RANGE=  new DatumRange(0, 100, Units.dimensionless);

    protected DatumRange range = DEFAULT_RANGE;
    
    public static final String PROP_RANGE = "range";

    public DatumRange getRange() {
        return range;
    }

    /**
     * set the range property.  Right now, if the axis is log, and the range
     * contains negative values, then the axis will be in an invalid state.
     * We cannot change the range setting automatically because the next setting
     * may be the log property, then the order of property setter calls would
     * matter.  TODO: consider mutatorLock...  TODO: consider making the axis
     * linear...
     * @param range
     */
    public void setRange(DatumRange range) {
        if ( range==null ) {
            logger.log( Level.WARNING, "range set to null!");
        }
        DatumRange oldRange = this.range;
        this.range= range;
        propertyChangeSupport.firePropertyChange(PROP_RANGE, oldRange, range);
    }
    
    protected boolean log = false;
    public static final String PROP_LOG = "log";

    public boolean isLog() {
        return log;
    }


    /**
     * set the log property.  If the value makes the range invalid (log and zero),
     * then the range is adjusted to make it valid.  This works because the order
     * of property setters doesn't matter.
     * @param log
     */
    public void setLog(boolean log) {
        boolean oldLog = this.log;
        this.log = log;
        propertyChangeSupport.firePropertyChange(PROP_LOG, oldLog, log);
    }
    protected String label = "";
    /**
     * concise label for the axis.
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

    /**
     * false indicates the component will not be drawn.  Note the x and y axes
     * are only drawn if the plot is drawn, and the colorbar may be drawn
     * if the plot is not drawn.
     */
    public static final String PROP_VISIBLE = "visible";
    protected boolean visible = true;

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        boolean oldVisible = this.visible;
        this.visible = visible;
        propertyChangeSupport.firePropertyChange(PROP_VISIBLE, oldVisible, visible);
    }

    /**
     * true indicates the axis hasn't been changed and may/should be autoranged.
     */
    public static final String PROP_AUTORANGE = "autoRange";
    protected boolean autoRange = false;

    public boolean isAutoRange() {
        return autoRange;
    }

    public void setAutoRange(boolean autorange) {
        boolean oldAutorange = this.autoRange;
        this.autoRange = autorange;
        propertyChangeSupport.firePropertyChange(PROP_AUTORANGE, oldAutorange, autorange);
    }

    /**
     * true indicates the axis label hasn't been changed by a human and may/should be autoranged.
     */
    public static final String PROP_AUTOLABEL = "autoLabel";
    protected boolean autoLabel = false;

    public boolean isAutoLabel() {
        return autoLabel;
    }

    public void setAutoLabel(boolean autolabel) {
        boolean oldAutolabel = this.autoLabel;
        this.autoLabel = autolabel;
        propertyChangeSupport.firePropertyChange(PROP_AUTOLABEL, oldAutolabel, autolabel);
    }


    AxisController controller;

    public AxisController getController() {
        return controller;
    }

    @Override
    public void syncTo(DomNode n) {
        super.syncTo(n);
        if ( controller!=null ) {
            controller.syncTo(n,new ArrayList<String>());
        } else {
            syncTo(n,new ArrayList<String>() );
        }
    }

    @Override
    public void syncTo(DomNode n, List<String> exclude ) {
        super.syncTo(n,exclude);
        if ( controller!=null ) {
            controller.syncTo(n,exclude);
        } else {
            Axis that = (Axis) n;
            if ( !exclude.contains( PROP_LOG ) ) this.setLog(that.isLog());
            if ( !exclude.contains( PROP_RANGE ) ) this.setRange(that.getRange());
            if ( !exclude.contains( PROP_LABEL ) ) this.setLabel(that.getLabel());
            if ( !exclude.contains( PROP_AUTORANGE ) ) this.setAutoRange(that.isAutoRange());
            if ( !exclude.contains( PROP_AUTOLABEL ) ) this.setAutoLabel(that.isAutoLabel());
            if ( !exclude.contains( PROP_DRAWTICKLABELS ) ) this.setDrawTickLabels(that.isDrawTickLabels());
            if ( !exclude.contains( PROP_VISIBLE ) ) this.setVisible(that.isVisible());
        }
    }

    @Override
    public DomNode copy() {
        Axis result= (Axis) super.copy();
        result.controller= null;
        return result;
    }



    @Override
    public List<Diff> diffs(DomNode node) {
        Axis that = (Axis) node;
        List<Diff> result = new ArrayList<Diff>();
        boolean b;

        b= that.log==this.log ;
        if ( !b ) result.add( new PropertyChangeDiff( PROP_LOG , that.log, this.log) );
        b=  that.range.equals(this.range) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_RANGE, that.range , this.range ) );
        b=  that.label.equals(this.label) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_LABEL, that.label , this.label ) );
        b=  that.autoRange==this.autoRange;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_AUTORANGE, that.autoRange , this.autoRange ) );
        b=  that.autoLabel==this.autoLabel;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_AUTOLABEL, that.autoLabel , this.autoLabel ) );
        b=  that.drawTickLabels==this.drawTickLabels;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_DRAWTICKLABELS, that.drawTickLabels, this.drawTickLabels ) );
        b=  that.visible==this.visible;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_VISIBLE, that.visible, this.visible ) );

        return result;
    }
}
