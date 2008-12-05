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
    Plot domplot;
    private DasPlot dasPlot;
    private DasColorBar dasColorBar;

    public PlotController(Application dom, Plot plot, DasPlot dasPlot, DasColorBar colorbar) {
        this.dom = dom;
        this.domplot = plot;
        this.dasPlot = dasPlot;
        this.dasColorBar = colorbar;
        dasPlot.addPropertyChangeListener(listener);
        dasPlot.getXAxis().addPropertyChangeListener(listener);
        dasPlot.getYAxis().addPropertyChangeListener(listener);
        plot.controller = this;
    }
    private PropertyChangeListener listener = new PropertyChangeListener() {

        public void propertyChange(PropertyChangeEvent e) {
            if (e.getSource() instanceof DasAxis) {
                DasAxis axis = (DasAxis) e.getSource();
                // we can safely ignore these events.
                if (((DasAxis) e.getSource()).valueIsAdjusting()) {
                    return;
                }
                if (domplot.isIsotropic()) {
                    checkIsotropic(axis);
                }

                if ( e.getPropertyName()==DasAxis.PROP_UNITS ) {
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
        List<Panel> panels = dom.getController().getPanelsFor(domplot);
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
        domplot.syncTo(newSettings);
    }

    private void checkIsotropic(DasAxis axis) {
        if ((axis == dasPlot.getXAxis() || axis == dasPlot.getYAxis()) && dasPlot.getXAxis().getUnits().isConvertableTo(dasPlot.getYAxis().getUnits()) && !dasPlot.getXAxis().isLog() && !dasPlot.getYAxis().isLog()) {
            DasAxis otherAxis = dasPlot.getYAxis();
            if (axis == dasPlot.getYAxis()) {
                otherAxis = dasPlot.getXAxis();
            }
            Datum ratio = axis.getDatumRange().width().divide(axis.getDLength());
            DatumRange otherRange = otherAxis.getDatumRange();
            Datum otherRatio = otherRange.width().divide(otherAxis.getDLength());
            double expand = (ratio.divide(otherRatio).doubleValue(Units.dimensionless) - 1) / 2;
            if (Math.abs(expand) > 0.0001) {
                DatumRange newOtherRange = DatumRangeUtil.rescale(otherRange, 0 - expand, 1 + expand);
                otherAxis.setDatumRange(newOtherRange);
            }
        }
    }

    public boolean valueIsAdjusting() {
        return domplot.getXaxis().getController().valueIsAdjusting() || domplot.getYaxis().getController().valueIsAdjusting() || domplot.getZaxis().getController().valueIsAdjusting();
    }
}
