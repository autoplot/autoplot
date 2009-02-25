/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.graph.DasAxis;
import org.das2.graph.DasColorBar;
import org.das2.graph.DasPlot;
import org.virbo.autoplot.util.DateTimeDatumFormatter;

/**
 *
 * @author jbf
 */
public class PlotController {

    Application dom;
    Plot domPlot;
    private DasPlot dasPlot;
    private DasColorBar dasColorBar;

    public PlotController(Application dom, Plot plot, DasPlot dasPlot, DasColorBar colorbar) {
        this.dom = dom;
        this.domPlot = plot;
        this.dasPlot = dasPlot;
        this.dasColorBar = colorbar;
        dasPlot.addPropertyChangeListener(listener);
        dasPlot.getXAxis().addPropertyChangeListener(listener);
        dasPlot.getYAxis().addPropertyChangeListener(listener);
        domPlot.addPropertyChangeListener( Plot.PROP_ISOTROPIC, new PropertyChangeListener() {
            public void propertyChange( PropertyChangeEvent e ) {
                if ( domPlot.isIsotropic() ) checkIsotropic(null);
            }
        });
        plot.controller = this;
    }

    private PropertyChangeListener listener = new PropertyChangeListener() {
        public String toString() {
            return ""+PlotController.this;
        }
        public void propertyChange(PropertyChangeEvent e) {
            if (e.getSource() instanceof DasAxis) {
                DasAxis axis = (DasAxis) e.getSource();
                // we can safely ignore these events.
                if (((DasAxis) e.getSource()).valueIsAdjusting()) {
                    return;
                }
                if (domPlot.isIsotropic()) {
                    checkIsotropic(axis);
                }

                if ( e.getPropertyName().equals(DasAxis.PROP_UNITS) ) {
                    if (UnitsUtil.isTimeLocation(axis.getUnits())) {
                        axis.setUserDatumFormatter(new DateTimeDatumFormatter());
                    } else {
                        axis.setUserDatumFormatter(null);
                    }
                }
            }


        }
    };

    public DasColorBar getDasColorBar() {
        return dasColorBar;
    }

    public DasPlot getDasPlot() {
        return dasPlot;
    }

    public void resetZoom() {
        List<Panel> panels = dom.getController().getPanelsFor(domPlot);
        Plot newSettings = null;
        for (Panel p : panels) {
            Plot plot1 = p.getPlotDefaults();
            if (newSettings == null) {
                newSettings = (Plot) plot1.copy();
            } else {
                newSettings.xaxis.range = DatumRangeUtil.union(newSettings.xaxis.range, plot1.getXaxis().getRange());
                newSettings.xaxis.log = newSettings.xaxis.log & plot1.xaxis.log;
                newSettings.yaxis.range = DatumRangeUtil.union(newSettings.yaxis.range, plot1.getYaxis().getRange());
                newSettings.yaxis.log = newSettings.yaxis.log & plot1.yaxis.log;
                newSettings.zaxis.range = DatumRangeUtil.union(newSettings.zaxis.range, plot1.getZaxis().getRange());
                newSettings.zaxis.log = newSettings.zaxis.log & plot1.zaxis.log;
            }
        }
        domPlot.syncTo(newSettings);
    }

    private void checkIsotropic(DasAxis axis) {
        Datum scalex = dasPlot.getXAxis().getDatumRange().width().divide(dasPlot.getXAxis().getDLength());
        Datum scaley = dasPlot.getYAxis().getDatumRange().width().divide(dasPlot.getYAxis().getDLength());

        if ( ! scalex.getUnits().isConvertableTo(scaley.getUnits())
                || dasPlot.getXAxis().isLog()
                || dasPlot.getYAxis().isLog() ) {
            return;
        }

        if ( axis==null ) {
            axis= scalex.gt(scaley) ? axis=  dasPlot.getXAxis()  : dasPlot.getYAxis() ;
        }

        if ( (axis == dasPlot.getXAxis() || axis == dasPlot.getYAxis()) ) {
            DasAxis otherAxis = dasPlot.getYAxis();
            if (axis == dasPlot.getYAxis()) {
                otherAxis = dasPlot.getXAxis();
            }
            Datum scale = axis.getDatumRange().width().divide(axis.getDLength());
            DatumRange otherRange = otherAxis.getDatumRange();
            Datum otherScale = otherRange.width().divide(otherAxis.getDLength());
            double expand = (scale.divide(otherScale).doubleValue(Units.dimensionless) - 1) / 2;
            if (Math.abs(expand) > 0.0001) {
                DatumRange newOtherRange = DatumRangeUtil.rescale(otherRange, 0 - expand, 1 + expand);
                otherAxis.setDatumRange(newOtherRange);
            }
        }
    }

    public synchronized void bindTo(DasPlot p) {
        ApplicationController ac= dom.getController();
        ac.bind( this.domPlot, "title", p, "title" );
        this.domPlot.getXaxis().getController().bindTo(p.getXAxis());
        this.domPlot.getYaxis().getController().bindTo(p.getYAxis());
    }
    
    public synchronized void bindTo(DasColorBar colorbar) {
        ApplicationController ac= dom.getController();
        ac.bind( this.domPlot.zaxis, Axis.PROP_RANGE, colorbar, "datumRange");
        ac.bind( this.domPlot.zaxis, Axis.PROP_LOG, colorbar, "log");
        ac.bind( this.domPlot.zaxis, Axis.PROP_LABEL, colorbar, "label" );
    }

    public BindingModel[] getBindings() {
        return dom.getController().getBindingsFor(domPlot);
    }

    public BindingModel getBindings(int index) {
        return getBindings()[index];
    }

    
    public boolean valueIsAdjusting() {
        return domPlot.getXaxis().getController().valueIsAdjusting() || domPlot.getYaxis().getController().valueIsAdjusting() || domPlot.getZaxis().getController().valueIsAdjusting();
    }

    public Application getApplication() {
        return dom;
    }
    public String toString() {
        return this.domPlot + " controller";
    }
}
