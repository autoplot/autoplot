/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.JMenuItem;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.InconvertibleUnitsException;
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
import org.das2.graph.Renderer;
import org.das2.graph.SpectrogramRenderer;
import org.jdesktop.beansbinding.Converter;
import org.virbo.autoplot.RenderType;
import org.virbo.autoplot.RenderTypeUtil;
import org.virbo.autoplot.util.DateTimeDatumFormatter;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;

/**
 * Manages a Plot node, for example listening for autorange updates and layout
 * changes.
 * @author jbf
 */
public class PlotController extends DomNodeController {

    Application dom;
    Plot plot;
    private DasPlot dasPlot;
    private DasColorBar dasColorBar;

    private static Logger logger= Logger.getLogger( PlotController.class.getName() );

    public PlotController(Application dom, Plot domPlot, DasPlot dasPlot, DasColorBar colorbar) {
        this( dom, domPlot );
        this.dasPlot = dasPlot;
        this.dasColorBar = colorbar;
        dasPlot.addPropertyChangeListener(listener);
        dasPlot.getXAxis().addPropertyChangeListener(listener);
        dasPlot.getYAxis().addPropertyChangeListener(listener);
    }

    public PlotController( Application dom, Plot plot ) {
        super( plot );
        this.dom = dom;
        this.plot = plot;
        this.plot.addPropertyChangeListener( Plot.PROP_ISOTROPIC, new PropertyChangeListener() {
            public void propertyChange( PropertyChangeEvent e ) {
                if ( PlotController.this.plot.isIsotropic() ) checkIsotropic(null);
            }
        });
        this.plot.addPropertyChangeListener( Plot.PROP_TITLE, labelListener );

        plot.controller= this;
    }

    public PropertyChangeListener rowColListener= new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            if ( dasPlot!=null && evt.getPropertyName().equals(Plot.PROP_ROWID) ) {
                String id= (String)evt.getNewValue();
                Row row=  ( id.length()==0 ) ? null : (Row) DomUtil.getElementById( dom, id );
                if ( row==null ) row= dom.controller.getCanvas().marginRow;
                DasRow dasRow= row.controller.getDasRow();
                dasPlot.setRow(dasRow);
                plot.getXaxis().getController().getDasAxis().setRow(dasRow);
                plot.getYaxis().getController().getDasAxis().setRow(dasRow);
                plot.getZaxis().getController().getDasAxis().setRow(dasRow);
            } else if ( dasPlot!=null && evt.getPropertyName().equals(Plot.PROP_COLUMNID) ) {
                String id= (String)evt.getNewValue();
                Column col= ( id.length()==0 ) ? null : (Column) DomUtil.getElementById( dom, id );
                if ( col==null ) col= dom.controller.getCanvas().marginColumn;
                DasColumn dasColumn= col.controller.getDasColumn();
                dasPlot.setColumn(dasColumn);
                plot.getXaxis().getController().getDasAxis().setColumn(dasColumn);
                plot.getYaxis().getController().getDasAxis().setColumn(dasColumn);
            }
        }

    };

    public Plot getPlot() {
        return plot;
    }

    /**
     * true indicates that the controller is allowed to automatically add
     * bindings to the plot axes.
     */
    public static final String PROP_AUTOBINDING = "autoBinding";

    protected boolean autoBinding = true;

    public boolean isAutoBinding() {
        return autoBinding;
    }

    public void setAutoBinding(boolean autoBinding) {
        boolean oldAutoBinding = this.autoBinding;
        this.autoBinding = autoBinding;
        propertyChangeSupport.firePropertyChange(PROP_AUTOBINDING, oldAutoBinding, autoBinding);
    }

    /**
     * return the Canvas containing this plot, or null if this cannot be resolved.
     * 
     * @return
     */
    private Canvas getCanvasForPlot() {
        Canvas[] cc= dom.getCanvases();
        for ( Canvas c: cc ) {
            for ( Row r: c.getRows() ) {
                if ( r.getId().equals(plot.getRowId()) ) {
                    return c;
                }
            }
        }
        return null;
    }

    private PropertyChangeListener labelListener= new PropertyChangeListener() {
         public void propertyChange(PropertyChangeEvent evt) {
            if ( evt.getPropertyName().equals(Plot.PROP_TITLE) ) {
                plot.setAutoLabel(false);
            }
         }
    };

    protected void createDasPeer( Canvas canvas, Row domRow ,Column domColumn) {

        Application application= dom;

        DatumRange x = this.plot.xaxis.range;
        DatumRange y = this.plot.yaxis.range;
        DasAxis xaxis = new DasAxis(x.min(), x.max(), DasAxis.HORIZONTAL);
        DasAxis yaxis = new DasAxis(y.min(), y.max(), DasAxis.VERTICAL);

        xaxis.setEnableHistory(false);
        xaxis.setUseDomainDivider(true);
        yaxis.setEnableHistory(false);
        yaxis.setUseDomainDivider(true);

        if (UnitsUtil.isTimeLocation(xaxis.getUnits())) {
            xaxis.setUserDatumFormatter(new DateTimeDatumFormatter());
        } else {
            xaxis.setUserDatumFormatter(null);
        }
        
        if (UnitsUtil.isTimeLocation(yaxis.getUnits())) {
            yaxis.setUserDatumFormatter(new DateTimeDatumFormatter());
        } else {
            yaxis.setUserDatumFormatter(null);
        }
        
        plot.setRowId(domRow.getId());
        DasRow row = domRow.controller.getDasRow();
        plot.addPropertyChangeListener( Plot.PROP_ROWID, rowColListener );
        plot.addPropertyChangeListener( Plot.PROP_COLUMNID, rowColListener );

        DasColumn col= domColumn.controller.getDasColumn();
        
        final DasPlot dasPlot1 = new DasPlot(xaxis, yaxis);

        dasPlot1.setPreviewEnabled(true);

        DatumRange colorRange = new DatumRange(0, 100, Units.dimensionless);
        DasColorBar colorbar = new DasColorBar(colorRange.min(), colorRange.max(), false);
        colorbar.addFocusListener(application.controller.focusAdapter);
        colorbar.setFillColor(new java.awt.Color(0, true));
        colorbar.setEnableHistory(false);
        colorbar.setUseDomainDivider(true);

        DasCanvas dasCanvas = canvas.controller.getDasCanvas();

        dasCanvas.add(dasPlot1, row, col);

        // the axes need to know about the plotId, so they can do reset axes units properly.
        dasPlot1.getXAxis().setPlot(dasPlot1);
        dasPlot1.getYAxis().setPlot(dasPlot1);

        BoxZoomMouseModule boxmm = (BoxZoomMouseModule) dasPlot1.getDasMouseInputAdapter().getModuleByLabel("Box Zoom");
        dasPlot1.getDasMouseInputAdapter().setPrimaryModule(boxmm);

        //plotId.getDasMouseInputAdapter().addMouseModule( new AnnotatorMouseModule(plotId) ) ;

        dasCanvas.add(colorbar, dasPlot1.getRow(), DasColorBar.getColorBarColumn(dasPlot1.getColumn()));
        colorbar.setVisible(false);

        MouseModule zoomPan = new ZoomPanMouseModule(dasPlot1, dasPlot1.getXAxis(), dasPlot1.getYAxis());
        dasPlot1.getDasMouseInputAdapter().setSecondaryModule(zoomPan);

        MouseModule zoomPanX = new ZoomPanMouseModule(dasPlot1.getXAxis(), dasPlot1.getXAxis(), null);
        dasPlot1.getXAxis().getDasMouseInputAdapter().setSecondaryModule(zoomPanX);

        MouseModule zoomPanY = new ZoomPanMouseModule(dasPlot1.getYAxis(), null, dasPlot1.getYAxis());
        dasPlot1.getYAxis().getDasMouseInputAdapter().setSecondaryModule(zoomPanY);

        MouseModule zoomPanZ = new ZoomPanMouseModule(colorbar, null, colorbar);
        colorbar.getDasMouseInputAdapter().setSecondaryModule(zoomPanZ);

        dasCanvas.revalidate();
        dasCanvas.repaint();

        ApplicationController ac= application.controller;
        ac.layoutListener.listenTo(dasPlot1);
        ac.layoutListener.listenTo(colorbar);

        //TODO: clean up in an addDasPeer way
        new AxisController(application, this.plot.getXaxis(), xaxis);
        new AxisController(application, this.plot.getYaxis(), yaxis);
        new AxisController(application, this.plot.getZaxis(), colorbar);

        bindTo(dasPlot1);
        
        logger.fine("add focus listener to " + dasPlot1);
        dasPlot1.addFocusListener(ac.focusAdapter);
        dasPlot1.getXAxis().addFocusListener(ac.focusAdapter);
        dasPlot1.getYAxis().addFocusListener(ac.focusAdapter);
        dasPlot1.addPropertyChangeListener(DasPlot.PROP_FOCUSRENDERER, ac.rendererFocusListener);

        ac.bind(application.getOptions(), Options.PROP_DRAWGRID, dasPlot1, "drawGrid");
        ac.bind(application.getOptions(), Options.PROP_DRAWMINORGRID, dasPlot1, "drawMinorGrid");
        ac.bind(application.getOptions(), Options.PROP_OVERRENDERING, dasPlot1, "overSize");

        dasPlot1.addPropertyChangeListener(listener);
        dasPlot1.getXAxis().addPropertyChangeListener(listener);
        dasPlot1.getYAxis().addPropertyChangeListener(listener);
        this.plot.addPropertyChangeListener( Plot.PROP_ISOTROPIC, new PropertyChangeListener() {
            public void propertyChange( PropertyChangeEvent e ) {
                if ( plot.isIsotropic() ) checkIsotropic(null);
            }
        });

        this.dasPlot = dasPlot1;
        this.dasColorBar = colorbar;

        dasPlot.setEnableRenderPropertiesAction(false);
        //dasPlot.getDasMouseInputAdapter().removeMenuItem("Render Properties");

        application.controller.maybeAddContextMenus( this );

    }


    private PropertyChangeListener listener = new PropertyChangeListener() {
        public String toString() {
            return ""+PlotController.this;
        }
        public void propertyChange(PropertyChangeEvent e) {
            if (e.getSource() instanceof DasAxis) {
                DasAxis axis = (DasAxis) e.getSource();

                if ( e.getPropertyName().equals(DasAxis.PROP_UNITS) || e.getPropertyName().equals(DasAxis.PROP_LABEL) ) {
                    if ( UnitsUtil.isTimeLocation(axis.getUnits()) && !axis.getLabel().contains("%{RANGE}") ) {
                        axis.setUserDatumFormatter(new DateTimeDatumFormatter());
                    } else {
                        axis.setUserDatumFormatter(null);
                    }
                }

                // we can safely ignore these events.
                if (((DasAxis) e.getSource()).valueIsAdjusting()) {
                    return;
                }
                if (plot.isIsotropic()) {
                    checkIsotropic(axis);
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
    public void resetZoom(boolean x, boolean y, boolean z) {
        List<Panel> panels = dom.controller.getPanelsFor(plot);
        if ( panels.size()==0 ) return;
        Plot newSettings = null;
        for (Panel p : panels) {
            Plot plot1 = p.getPlotDefaults();
            if ( p.isActive() && plot1.getXaxis().isAutorange() ) {  // we use autorange to indicate these are real settings, not just the defaults.
                if (newSettings == null) {
                    newSettings = (Plot) plot1.copy();
                } else {
                    try {
                        newSettings.xaxis.range = DatumRangeUtil.union(newSettings.xaxis.range, plot1.getXaxis().getRange());
                        newSettings.xaxis.log = newSettings.xaxis.log & plot1.xaxis.log;
                        newSettings.yaxis.range = DatumRangeUtil.union(newSettings.yaxis.range, plot1.getYaxis().getRange());
                        newSettings.yaxis.log = newSettings.yaxis.log & plot1.yaxis.log;
                        newSettings.zaxis.range = DatumRangeUtil.union(newSettings.zaxis.range, plot1.getZaxis().getRange());
                        newSettings.zaxis.log = newSettings.zaxis.log & plot1.zaxis.log;
                    } catch ( InconvertibleUnitsException ex ) {
                        logger.info("panels on the same plot have inconsistent units");
                    }
                }
            }
        }
        
        if ( newSettings==null ) {
            plot.getXaxis().setAutorange(true);
            plot.getYaxis().setAutorange(true);
            plot.getZaxis().setAutorange(true);
            return;
        }

        if ( x ) {
            plot.getXaxis().setLog( newSettings.getXaxis().isLog() );
            plot.getXaxis().setRange(newSettings.getXaxis().getRange());
            plot.getXaxis().setAutorange(true);
        }
        if ( y ) {
            plot.getYaxis().setLog( newSettings.getYaxis().isLog() );
            plot.getYaxis().setRange(newSettings.getYaxis().getRange());
            plot.getYaxis().setAutorange(true);
        }
        if ( z ) {
            plot.getZaxis().setLog( newSettings.getZaxis().isLog() );
            plot.getZaxis().setRange(newSettings.getZaxis().getRange());
            plot.getZaxis().setAutorange(true);
        }
    }

    PropertyChangeListener plotDefaultsListener= new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            doPanelDefaultsChange((Panel)evt.getSource());
        }
    };

    PropertyChangeListener renderTypeListener= new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            checkRenderType();
        }
    };

    Panel panel;

    PropertyChangeListener panelDataSetListener= new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            if ( plot.getTitle().contains("%{CONTEXT}" ) ) {
                QDataSet pds= panel.getController().getDataSet();
                String title= plot.getTitle();
                if ( pds!=null ) {
                    String contextStr= DataSetUtil.contextAsString(pds);
                    title= title.replaceAll("%\\{CONTEXT\\}", contextStr );
                }
                dasPlot.setTitle(title);
            }
        }
    };

    private void checkRenderType() {
        boolean needsColorbar = false;
        for (Panel p : dom.getController().getPanelsFor(plot)) {
            if (RenderTypeUtil.needsColorbar(p.getRenderType())) {
                needsColorbar = true;
            }
        }
        dasColorBar.setVisible(needsColorbar);
    }

    void addPanel(Panel p) {
        Renderer rr= p.controller.getRenderer();
        if ( rr!=null ) {
            if ( rr instanceof SpectrogramRenderer ) {
                dasPlot.addRenderer(0,rr);
            } else {
                dasPlot.addRenderer(rr);
            }
        }
        RenderType rt = p.getRenderType();
        p.controller.doResetRenderType(rt);
        doPanelDefaultsChange(p);
        p.addPropertyChangeListener( Panel.PROP_PLOT_DEFAULTS, plotDefaultsListener );
        p.addPropertyChangeListener( Panel.PROP_RENDERTYPE, renderTypeListener );
        checkRenderType();
    }

    void removePanel(Panel p) {
        Renderer rr= p.controller.getRenderer();
        if ( rr!=null ) dasPlot.removeRenderer(rr);
        doPanelDefaultsChange(null);
        p.removePropertyChangeListener( Panel.PROP_PLOT_DEFAULTS, plotDefaultsListener );
        p.removePropertyChangeListener( Panel.PROP_RENDERTYPE, renderTypeListener );
        checkRenderType();
    }

    /**
     * check all the panels' plot defaults, so that properties marked as automatic can be reset.
     * @param panel
     */
    private void doPanelDefaultsChange( Panel panel ) {

        if ( panel!=null ) {
            if ( panel.getPlotDefaults().getXaxis().isAutorange()==false ) {
                return;
            }
        }
        
        if ( panel!=null && isAutoBinding() ) doCheckBindings( plot, panel.getPlotDefaults() );

        List<BindingModel> bms= dom.getController().findBindings( dom, Application.PROP_TIMERANGE, null, Axis.PROP_RANGE );
        BindingModel existingBinding= dom.getController().findBinding( dom, Application.PROP_TIMERANGE, plot.xaxis, Axis.PROP_RANGE );
        if ( bms.contains(existingBinding) ) {
            if ( bms.size()>1 ) {
                plot.getXaxis().setAutorange(false);
            }
        }

        if ( DomUtil.oneFamily( dom.getController().getPanelsFor(plot) ) ) {
            Panel p= dom.getController().getPanelsFor(plot).get(0);
            if ( !p.getParentPanel().equals("") ) p = p.getController().getParentPanel();
            if ( this.panel!=null ) {
                this.panel.getController().removePropertyChangeListener( PanelController.PROP_DATASET, panelDataSetListener );
            }
            this.panel= p;
            this.panel.getController().addPropertyChangeListener( PanelController.PROP_DATASET, panelDataSetListener );
            if ( plot.isAutoLabel() ) plot.setTitle( p.getPlotDefaults().getTitle() );
            if ( plot.getXaxis().isAutolabel() ) plot.getXaxis().setLabel( p.getPlotDefaults().getXaxis().getLabel() );
            if ( plot.getYaxis().isAutolabel() ) plot.getYaxis().setLabel( p.getPlotDefaults().getYaxis().getLabel() );
            if ( plot.getZaxis().isAutolabel() ) plot.getZaxis().setLabel( p.getPlotDefaults().getZaxis().getLabel() );
            if ( plot.getXaxis().isAutorange() && plot.getYaxis().isAutorange() ) {
                plot.setIsotropic( p.getPlotDefaults().isIsotropic() );
            }
        }

        resetZoom( plot.getXaxis().isAutorange(), plot.getYaxis().isAutorange(), plot.getZaxis().isAutorange() );

    }

    /**
     * after autoranging, we need to check to see if a panel's plot looks like
     * it should be automatically bound or unbound.
     *
     * We unbind if changing this plot's axis settings will make another plot
     * invalid.
     *
     * We bind if the axis setting is similar to the application timerange.
     * @param plot the plot whose binds we are checking.  Bindings with this node may be added or removed.
     * @param newSettings the new plot settings from autoranging.
     */
    private void doCheckBindings( Plot plot, Plot newSettings ) {
        boolean shouldBindX= false;
        List<BindingModel> bms= dom.getController().findBindings( dom, Application.PROP_TIMERANGE, null, Axis.PROP_RANGE );
        BindingModel bm= dom.getController().findBinding( dom, Application.PROP_TIMERANGE, plot.getXaxis(), Axis.PROP_RANGE );
        bms.remove(bm);

        if ( ! plot.isAutoBinding() ) {
            return;
        }

        // if we aren't autoranging, then only change the bindings if there will be a conflict.
        if ( plot.getXaxis().isAutorange()==false ) {
            shouldBindX= bm!=null;
            if ( bm!=null && !newSettings.getXaxis().getRange().getUnits().isConvertableTo( plot.getXaxis().getRange().getUnits() ) ) {
                shouldBindX= false;
                logger.finer("remove timerange binding that would cause inconvertable units");
            }
        }

        if ( newSettings.getXaxis().isLog()==false && plot.getXaxis().isAutorange() ) {
            if ( bms.size()==0 ) {
                dom.setTimeRange( plot.getXaxis().getRange() );
                shouldBindX= true;
            }
            DatumRange xrange= newSettings.getXaxis().getRange();
            if ( dom.timeRange.getUnits().isConvertableTo(xrange.getUnits()) ) {
                if ( dom.timeRange.intersects( xrange ) ) {
                    double reqOverlap= UnitsUtil.isTimeLocation( dom.timeRange.getUnits() ) ? 0.2 : 0.8;
                    double overlap= DatumRangeUtil.normalize( dom.timeRange, xrange.max() ) - DatumRangeUtil.normalize( dom.timeRange, xrange.min() );
                    if ( overlap > 1.0 ) overlap= 1/overlap;
                    if ( overlap > reqOverlap ) {
                        shouldBindX= true;
                    }
                }
            }
        }
        if ( bm==null && shouldBindX ) {
            logger.finer("add binding because ranges overlap: "+ plot.getXaxis());
            dom.getController().bind( dom, Application.PROP_TIMERANGE, plot.getXaxis(), Axis.PROP_RANGE );
            //if ( !CanvasUtil.getMostBottomPlot(dom.getController().getCanvasFor(plot))==plot ) {
            //    plot.getXaxis().setDrawTickLabels(false);
            //} //TODO: could disable tick label drawing automatically.

        } else if ( bm!=null && !shouldBindX ) {
            logger.finer("remove binding: "+bm);
            dom.getController().deleteBinding(bm);
        }

        plot.setAutoBinding(false);
        
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
        ac.bind( this.plot, "title", p, "title", new Converter() {
            @Override
            public Object convertForward(Object value) {
                String title= (String)value;
                if ( title.contains("%{CONTEXT}" ) ) {
                    QDataSet context;
                    String contextStr="";
                    if ( panel!=null && panel.getController()!=null ) {
                        QDataSet ds= panel.getController().getDataSet();
                        if ( ds!=null ) {
                            contextStr= DataSetUtil.contextAsString(ds);
                        }
                    }
                    title= title.replaceAll("%\\{CONTEXT\\}", contextStr );
                }
                return title;
            }

            @Override
            public Object convertReverse(Object value) {
                String title= (String)value;
                String ptitle=  plot.getTitle();
                if (ptitle.contains("%{CONTEXT}") ) {
                    String[] ss= ptitle.split("%\\{CONTEXT\\}",-2);
                    if ( title.startsWith(ss[0]) && title.endsWith(ss[1]) ) {
                        return ptitle;
                    }
                }
                return title;
            }
        } );
    }

    public BindingModel[] getBindings() {
        return dom.controller.getBindingsFor(plot);
    }

    public BindingModel getBindings(int index) {
        return getBindings()[index];
    }


    protected JMenuItem panelPropsMenuItem = null;
    public static final String PROP_PANELPROPSMENUITEM = "panelPropsMenuItem";

    public JMenuItem getPanelPropsMenuItem() {
        return panelPropsMenuItem;
    }

    public void setPanelPropsMenuItem(JMenuItem panelPropsMenuItem) {
        JMenuItem oldPanelPropsMenuItem = this.panelPropsMenuItem;
        this.panelPropsMenuItem = panelPropsMenuItem;
        propertyChangeSupport.firePropertyChange(PROP_PANELPROPSMENUITEM, oldPanelPropsMenuItem, panelPropsMenuItem);
    }

    public Application getApplication() {
        return dom;
    }
    public String toString() {
        return this.plot + " controller";
    }

    /**
     * set the title, leaving autoLabel true.
     * @param title
     */
    public void setTitleAutomatically(String title) {
        plot.setTitle(title);
        plot.setAutoLabel(true);
    }
}
