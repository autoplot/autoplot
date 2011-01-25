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

    /**
     * true indicates the axis hasn't been changed and may/should be autoranged.
     */
    public static final String PROP_AUTORANGE = "autorange";
    protected boolean autorange = false;

    public boolean isAutorange() {
        return autorange;
    }

    public void setAutorange(boolean autorange) {
        boolean oldAutorange = this.autorange;
        this.autorange = autorange;
        propertyChangeSupport.firePropertyChange(PROP_AUTORANGE, oldAutorange, autorange);
    }

    /**
     * true indicates the axis label hasn't been changed by a human and may/should be autoranged.
     */
    public static final String PROP_AUTOLABEL = "autolabel";
    protected boolean autolabel = false;

    public boolean isAutolabel() {
        return autolabel;
    }

    public void setAutolabel(boolean autolabel) {
        boolean oldAutolabel = this.autolabel;
        this.autolabel = autolabel;
        propertyChangeSupport.firePropertyChange(PROP_AUTOLABEL, oldAutolabel, autolabel);
    }


}
