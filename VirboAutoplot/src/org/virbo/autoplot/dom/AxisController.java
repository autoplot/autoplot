/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.das2.graph.DasAxis;
import org.das2.graph.DasAxis.Lock;
import org.das2.system.MutatorLock;

/**
 *
 * @author jbf
 */
public class AxisController extends DomNodeController {

    DasAxis dasAxis;
    private Application dom;
    Axis axis;

    public AxisController(Application dom, Axis axis, DasAxis dasAxis) {
        super( axis );
        this.dom = dom;
        this.dasAxis = dasAxis;
        this.axis = axis;
        axis.controller = this;
        bindTo(dasAxis);
        axis.addPropertyChangeListener(invalidRangeListener);
    }

    private PropertyChangeListener invalidRangeListener = new PropertyChangeListener() {

        private DatumRange logCheckRange(DatumRange range, boolean log) {

            Units u = range.getUnits();
            double dmin= range.min().doubleValue(u);
            double dmax= range.max().doubleValue(u);
            
            boolean changed = false;
            if ( log && dmax <= 0.) {
                dmax = 1000;
                changed = true;
            }
            if ( log && dmin <= 0.) {
                dmin = dmax / 10000;
                changed = true;
            }
            if ( !log && dmin>0 && dmin<=dmax/10000 ) {
                dmin = 0;
                changed = true;
            }

            if (changed) {
                return new DatumRange(dmin, dmax, u);
            } else {
                return range;
            }
        }

        public void propertyChange(PropertyChangeEvent evt) {
            // ensure that log doesn't make axis invalid, or min trivially close to zero.
            if ( dom.controller.isValueAdjusting() || valueIsAdjusting() ) return;
            DatumRange oldRange = axis.range;
            DatumRange range = logCheckRange(axis.range, axis.log);
            if (!range.equals(oldRange)) {
                axis.setRange(range);
            }
        }
    };

    //TODO: this will confuse with isValueAdjusting
    public boolean valueIsAdjusting() {
        return super.isValueAdjusting() || dasAxis.valueIsAdjusting();
    }

    public synchronized void bindTo(DasAxis p) {
        ApplicationController ac = dom.controller;
        ac.bind(axis, "range", p, "datumRange");
        ac.bind(axis, "log", p, "log");
        ac.bind(axis, "label", p, "label");
        ac.bind(axis, "drawTickLabels", p, "tickLabelsVisible");

    }

    public DasAxis getDasAxis() {
        return dasAxis;
    }

    void syncTo(DomNode n,List<String> exclude ) {
        Lock lock = null;
        if ( dasAxis!=null ) {
            lock= dasAxis.mutatorLock();
            lock.lock();
        }
        //TODO: should call ((DomNode)n).syncTo(n);
        Axis that = (Axis) n;
        if ( !exclude.contains( Axis.PROP_LOG ) ) axis.setLog(that.isLog());
        if ( !exclude.contains( Axis.PROP_RANGE ) ) axis.setRange(that.getRange());
        if ( !exclude.contains( Axis.PROP_LABEL ) ) axis.setLabel(that.getLabel());
        if ( !exclude.contains( Axis.PROP_DRAWTICKLABELS ) ) axis.setDrawTickLabels( that.isDrawTickLabels() );
        if ( lock!=null ) lock.unlock();
    }
}
