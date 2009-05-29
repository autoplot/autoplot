/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.logging.Logger;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.event.BoxZoomMouseModule;
import org.das2.event.MouseModule;
import org.das2.event.ZoomPanMouseModule;
import org.das2.graph.DasAxis;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasColorBar;
import org.das2.graph.DasColumn;
import org.das2.graph.DasPlot;
import org.das2.graph.DasRow;
import org.virbo.autoplot.util.DateTimeDatumFormatter;

/**
 *
 * @author jbf
 */
public class PlotController extends DomNodeController {

    Application dom;
    Plot domPlot;
    private DasPlot dasPlot;
    private DasColorBar dasColorBar;

    public PlotController(Application dom, Plot plot, DasPlot dasPlot, DasColorBar colorbar) {
        this( dom, plot );
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
    }

    public PlotController( Application dom, Plot plot ) {
        super( plot );
        this.dom = dom;
        this.domPlot = plot;
        plot.controller= this;
    }

    public PropertyChangeListener rowColListener= new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            if ( dasPlot!=null && evt.getPropertyName().equals(Plot.PROP_ROWID) ) {
                Row row= (Row) DomUtil.getElementById( dom, (String)evt.getNewValue() );
                DasRow dasRow= row.controller.getDasRow();
                dasPlot.setRow(dasRow);
                domPlot.getXaxis().getController().getDasAxis().setRow(dasRow);
                domPlot.getYaxis().getController().getDasAxis().setRow(dasRow);
                domPlot.getZaxis().getController().getDasAxis().setRow(dasRow);
            } else if ( dasPlot!=null && evt.getPropertyName().equals(Plot.PROP_COLUMNID) ) {
                Column col= (Column) DomUtil.getElementById( dom, (String)evt.getNewValue() );
                DasColumn dasColumn= col.controller.getDasColumn();
                dasPlot.setColumn(dasColumn);
                domPlot.getXaxis().getController().getDasAxis().setColumn(dasColumn);
                domPlot.getYaxis().getController().getDasAxis().setColumn(dasColumn);
            }
        }

    };

    protected void createDasPeer( Canvas canvas, Row domRow ,Column domColumn) {

        Application application= dom;

        DatumRange x = this.domPlot.xaxis.range;
        DatumRange y = this.domPlot.yaxis.range;
        DasAxis xaxis = new DasAxis(x.min(), x.max(), DasAxis.HORIZONTAL);
        DasAxis yaxis = new DasAxis(y.min(), y.max(), DasAxis.VERTICAL);

        domPlot.setRowId(domRow.getId());
        DasRow row = domRow.controller.getDasRow();
        domPlot.addPropertyChangeListener( Plot.PROP_ROWID, rowColListener );
        domPlot.addPropertyChangeListener( Plot.PROP_COLUMNID, rowColListener );

        DasColumn col= domColumn.controller.getDasColumn();
        
        final DasPlot plot = new DasPlot(xaxis, yaxis);

        plot.setPreviewEnabled(true);

        DatumRange colorRange = new DatumRange(0, 100, Units.dimensionless);
        DasColorBar colorbar = new DasColorBar(colorRange.min(), colorRange.max(), false);
        colorbar.addFocusListener(application.controller.focusAdapter);
        colorbar.setFillColor(new java.awt.Color(0, true));

        DasCanvas dasCanvas = canvas.controller.getDasCanvas();

        dasCanvas.add(plot, row, col);

        // the axes need to know about the plotId, so they can do reset axes units properly.
        plot.getXAxis().setPlot(plot);
        plot.getYAxis().setPlot(plot);

        BoxZoomMouseModule boxmm = (BoxZoomMouseModule) plot.getDasMouseInputAdapter().getModuleByLabel("Box Zoom");
        plot.getDasMouseInputAdapter().setPrimaryModule(boxmm);

        //plotId.getDasMouseInputAdapter().addMouseModule( new AnnotatorMouseModule(plotId) ) ;

        dasCanvas.add(colorbar, plot.getRow(), DasColorBar.getColorBarColumn(plot.getColumn()));
        colorbar.setVisible(false);

        if (!application.controller.headless) {
            boxmm.setAutoUpdate(true);
        }

        MouseModule zoomPan = new ZoomPanMouseModule(plot, plot.getXAxis(), plot.getYAxis());
        plot.getDasMouseInputAdapter().setSecondaryModule(zoomPan);

        MouseModule zoomPanX = new ZoomPanMouseModule(plot.getXAxis(), plot.getXAxis(), null);
        plot.getXAxis().getDasMouseInputAdapter().setSecondaryModule(zoomPanX);

        MouseModule zoomPanY = new ZoomPanMouseModule(plot.getYAxis(), null, plot.getYAxis());
        plot.getYAxis().getDasMouseInputAdapter().setSecondaryModule(zoomPanY);

        MouseModule zoomPanZ = new ZoomPanMouseModule(colorbar, null, colorbar);
        colorbar.getDasMouseInputAdapter().setSecondaryModule(zoomPanZ);

        dasCanvas.revalidate();
        dasCanvas.repaint();

        ApplicationController ac= application.controller;
        ac.layoutListener.listenTo(plot);
        ac.layoutListener.listenTo(colorbar);

        //TODO: clean up in an addDasPeer way
        new AxisController(application, domPlot.getXaxis(), xaxis);
        new AxisController(application, domPlot.getYaxis(), yaxis);
        new AxisController(application, domPlot.getZaxis(), colorbar);

        domPlot.controller.bindTo(plot);

        ac.support.addPlotContextMenuItems(plot, this, domPlot);
        ac.support.addAxisContextMenuItems(plot, this, domPlot, domPlot.getXaxis());
         ac.support.addAxisContextMenuItems(plot, this, domPlot, domPlot.getYaxis());
         ac.support.addAxisContextMenuItems(plot, this, domPlot, domPlot.getZaxis());

         Logger logger= Logger.getLogger(PlotController.class.getName());
        logger.fine("add focus listener to " + plot);
        plot.addFocusListener(ac.focusAdapter);
        plot.getXAxis().addFocusListener(ac.focusAdapter);
        plot.getYAxis().addFocusListener(ac.focusAdapter);
        plot.addPropertyChangeListener(DasPlot.PROP_FOCUSRENDERER, ac.rendererFocusListener);

        ac.bind(application.getOptions(), Options.PROP_DRAWGRID, plot, "drawGrid");
        ac.bind(application.getOptions(), Options.PROP_DRAWMINORGRID, plot, "drawMinorGrid");
        ac.bind(application.getOptions(), Options.PROP_OVERRENDERING, plot, "overSize");

        plot.addPropertyChangeListener(listener);
        plot.getXAxis().addPropertyChangeListener(listener);
        plot.getYAxis().addPropertyChangeListener(listener);
        domPlot.addPropertyChangeListener( Plot.PROP_ISOTROPIC, new PropertyChangeListener() {
            public void propertyChange( PropertyChangeEvent e ) {
                if ( domPlot.isIsotropic() ) checkIsotropic(null);
            }
        });

        this.dasPlot = plot;
        this.dasColorBar = colorbar;
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

    /**
     * set the zoom so that all of the panels' data is visible.  Thie means finding
     * the "union" of each panels' plotDefault ranges.  If any panel's default log
     * is false, then the new setting will be false.
     */
    public void resetZoom() {
        List<Panel> panels = dom.controller.getPanelsFor(domPlot);
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
        domPlot.getXaxis().setRange(newSettings.getXaxis().getRange());
        domPlot.getXaxis().setLog( newSettings.getXaxis().isLog() );
        domPlot.getYaxis().setRange(newSettings.getYaxis().getRange());
        domPlot.getYaxis().setLog( newSettings.getYaxis().isLog() );
        domPlot.getZaxis().setRange(newSettings.getZaxis().getRange());
        domPlot.getZaxis().setLog( newSettings.getZaxis().isLog() );
    }

    /**
     * delete the das peer that implements this node.
     */
    void deleteDasPeer() {
        DasPlot p = getDasPlot();
        DasColorBar cb = getDasColorBar();
        DasCanvas c= p.getCanvas();
        if ( c!=null ) {
            c.remove(p);
            c.remove(cb);
        }
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
            axis= scalex.gt(scaley) ?  dasPlot.getXAxis()  : dasPlot.getYAxis() ;
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

    private synchronized void bindTo(DasPlot p) {
        ApplicationController ac= dom.controller;
        ac.bind( this.domPlot, "title", p, "title" );
    }

    public BindingModel[] getBindings() {
        return dom.controller.getBindingsFor(domPlot);
    }

    public BindingModel getBindings(int index) {
        return getBindings()[index];
    }

    public Application getApplication() {
        return dom;
    }
    public String toString() {
        return this.domPlot + " controller";
    }
}
