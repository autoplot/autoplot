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

/**
 *
 * @author jbf
 */
public class AxisController extends DomNodeController {

    DasAxis dasAxis;
    private Application dom;
    Plot plot;
    Axis axis;
    private final static Object PENDING_RANGE_TWEAK="pendingRangeTweak";

    public AxisController(Application dom, Plot plot, Axis axis, DasAxis dasAxis) {
        super( axis );
        this.dom = dom;
        this.dasAxis = dasAxis;
        this.plot= plot;
        this.axis = axis;
        axis.controller = this;
        bindTo(dasAxis);
        axis.addPropertyChangeListener(rangeChangeListener);
    }

    /**
     * checks to see that the axis is still valid and clears the autoRange property.
     */
    private PropertyChangeListener rangeChangeListener = new PropertyChangeListener() {

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
            //disable this near-zero test because with lightweight bindings we get stuck in a loop.
            //if ( !log && dmin>0 && dmin<=dmax/10000 ) {
            //    dmin = 0;
            //    changed = true;
            //}

            if (changed) {
                return new DatumRange(dmin, dmax, u);
            } else {
                return range;
            }
        }

        public synchronized void propertyChange(PropertyChangeEvent evt) {
            // ensure that log doesn't make axis invalid, or min trivially close to zero.
            if ( dom.controller.isValueAdjusting() || valueIsAdjusting() ) return;
            if ( evt.getPropertyName().equals( Axis.PROP_RANGE )
                    || evt.getPropertyName().equals( Axis.PROP_LOG ) ) axis.setAutoRange(false);
            if ( evt.getPropertyName().equals( Axis.PROP_LABEL ) ) {
                axis.setAutoLabel(false);
            }
            if ( evt.getPropertyName().equals(Axis.PROP_LOG) || evt.getPropertyName().equals(Axis.PROP_RANGE) ) {
                if ( isPendingChanges() ) return;
                DatumRange oldRange = axis.range;
                final DatumRange range = logCheckRange(axis.range, axis.log);
                if (!range.equals(oldRange)) {
                    changesSupport.registerPendingChange(this,PENDING_RANGE_TWEAK);
                    changesSupport.performingChange(this, PENDING_RANGE_TWEAK);
                    axis.setRange(range);
                    changesSupport.changePerformed(this, PENDING_RANGE_TWEAK);
                }
            }
        }
    };

    //TODO: this will confuse with isValueAdjusting
    public boolean valueIsAdjusting() {
        return super.isValueAdjusting() || dasAxis.valueIsAdjusting();
    }

    /**
     * set the range without affecting the auto state.
     */
    public void setRangeAutomatically( DatumRange range, boolean log ) {
        axis.range= range; // don't fire off property change events.
        axis.log= log;
        axis.setRange(range);
        axis.setLog(log);
        axis.setAutoRange(true);
    }

    /**
     * set the label, leaving its autoLabel property true.
     * @param label
     */
    public void setLabelAutomatically( String label ) {
        if ( axis.getLabel().contains("%{RANGE}") && !label.contains("%{RANGE}") ) {
            return;
        }
        axis.setLabel(label);
        axis.setAutoLabel(true);
    }

    public final synchronized void bindTo(DasAxis p) {
        ApplicationController ac = dom.controller;
        ac.bind(axis, "range", p, "datumRange");
        ac.bind(axis, "log", p, "log");
        ac.bind(axis, "label", p, "label", plot.getController().labelContextConverter(axis) );
        ac.bind(axis, "drawTickLabels", p, "tickLabelsVisible");
        ac.bind(axis, "visible", p, "visible" );
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
        if ( !exclude.contains( Axis.PROP_AUTORANGE ) ) axis.setAutoRange(that.isAutoRange());
        if ( !exclude.contains( Axis.PROP_AUTOLABEL ) ) axis.setAutoLabel(that.isAutoLabel());
        if ( !exclude.contains( Axis.PROP_DRAWTICKLABELS ) ) axis.setDrawTickLabels( that.isDrawTickLabels() );
        if ( !exclude.contains( Axis.PROP_VISIBLE ) ) axis.setVisible( that.isVisible() );
        if ( lock!=null ) lock.unlock();
    }
}
