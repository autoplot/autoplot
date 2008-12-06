/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.beans.binding.Binding;
import javax.beans.binding.BindingContext;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import org.das2.DasApplication;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.das2.event.BoxZoomMouseModule;
import org.das2.event.MouseModule;
import org.das2.event.PointSlopeDragRenderer;
import org.das2.event.ZoomPanMouseModule;
import org.das2.graph.DasAxis;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasCanvasComponent;
import org.das2.graph.DasColorBar;
import org.das2.graph.DasColumn;
import org.das2.graph.DasPlot;
import org.das2.graph.DasRow;
import org.das2.graph.Renderer;
import org.das2.system.MonitorFactory;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.autoplot.AutoplotUtil;
import org.virbo.autoplot.GuiSupport;
import org.virbo.autoplot.LayoutListener;

/**
 *
 * @author jbf
 */
public class ApplicationController {

    Application application;
    ApplicationModel model;
    DasRow outerRow;
    DasColumn outerColumn;
    LayoutListener layoutListener;
    boolean headless;
    Map<Object, BindingContext> bindingContexts;
    Map<BindingModel,Binding> bindings;
    
    private final static Logger logger = Logger.getLogger("virbo.controller");

    public ApplicationController(ApplicationModel model, Application application) {
        this.application = application;
        application.setId("app_0");
        this.model = model;
        application.controller = this;
        this.headless = "true".equals(AutoplotUtil.getProperty("java.awt.headless", "false"));
        if (!headless) {
            application.getOptions().loadPreferences();
        }
        bindingContexts = new HashMap<Object, BindingContext>();
        bindings= new HashMap<BindingModel, Binding>();
    }

    public DasCanvas addCanvas() {
        logger.fine("enter addCanvas");
        //if ( canvas!=null ) throw new IllegalArgumentException("only one canvas for now");
        DasCanvas dasCanvas = new DasCanvas();
        Canvas canvas = new Canvas();
        new CanvasController(application, canvas).setDasCanvas(dasCanvas);

        outerRow = new DasRow(dasCanvas, null, 0, 1, 0, -3, 0, 0);
        outerColumn = new DasColumn(dasCanvas, null, 0, 1, 5, -3, 0, 0);

        layoutListener = new LayoutListener(model);

        canvas.setId("canvas_0");

        application.setCanvas(canvas);
        application.bindTo(dasCanvas);

        canvas.getController().bindTo(outerRow, outerColumn);

        dasCanvas.setPrintingTag("");
        return dasCanvas;
    }

    public void deletePanel(Panel panel) {
        try {
        int currentIdx = application.panels.indexOf(panel);
        DasPlot p = panel.getController().getPlot();
        if (p != null) {
            p.removeRenderer(panel.getController().getRenderer());
        }
        assert (currentIdx != -1);
        ArrayList<Panel> panels = new ArrayList<Panel>(Arrays.asList(application.getPanels()));
        panels.remove(panel);
        if (!panels.contains(application.panel)) {
            if ( panels.size()==0 ) {
                application.setPanel(null);
            } else {
                application.setPanel(panels.get(0)); // maybe use currentIdx
            }
        }
        application.setPanels(panels.toArray(new Panel[panels.size()]));
        } catch ( NullPointerException ex ) {
            throw ex;
        }
    }

    private void movePanel(Panel p, Plot src, Plot dst) {
        assert (p.getPlotId().equals(src.getId()) || p.getPlotId().equals(dst.getId()));

        DasPlot srcPlot = src.getController().getDasPlot();
        DasPlot dstPlot = dst.getController().getDasPlot();

        Renderer rr = p.getController().getRenderer();

        srcPlot.removeRenderer(rr);
        dstPlot.addRenderer(rr);

        p.setPlotId(dst.getId());

        ApplicationModel.RenderType rt = p.getRenderType();
        p.getController().setRenderType(rt);

    }

    /**
     * add a panel to the application.
     * @return
     */
    public synchronized Panel addPanel(Plot domPlot) {
        logger.fine("enter addPanel");
        final Panel panel = new Panel();
        new PanelController(this.model, application, panel);

        if (domPlot == null) {
            domPlot = addPlot();
        }
        
        domPlot.getController().getDasPlot().getMouseAdapter().addMenuItem(GuiSupport.createEZAccessMenu(panel));        

        final int panelIdNum = application.getPanels().length;
        panel.setId("panel_" + panelIdNum);
        panel.getDataSourceFilter().setId("data_" + panelIdNum);

       /*  final Plot fplot = domPlot;

       // bind it to the common range if it looks compatible
        panel.getPlotDefaults().getXaxis().addPropertyChangeListener(Axis.PROP_RANGE, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                DatumRange dr = panel.getPlotDefaults().getXaxis().getRange();
                DatumRange appRange = application.getTimeRange();
                if (appRange.getUnits().isConvertableTo(dr.getUnits()) && appRange.intersects(dr)) {
                    bind(application, Application.PROP_TIMERANGE, fplot, "xaxis." + Axis.PROP_RANGE);
                }
            }
        }); */

        panel.getStyle().setId("style_" + panelIdNum);
        panel.getPlotDefaults().setId("plot_defaults_" + panelIdNum);

        panel.addPropertyChangeListener(Panel.PROP_PLOTID, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                Panel p = (Panel) evt.getSource();
                Plot src = (Plot) DomUtil.getElementById(application, (String) evt.getOldValue());
                Plot dst = (Plot) DomUtil.getElementById(application, (String) evt.getNewValue());
                if (src != null && dst != null) {
                    movePanel(p, src, dst);
                    if (getPanelsFor(src).size() == 0) deletePlot(src, true);
                }
            }
        });

        panel.setPlotId(domPlot.getId());

        List<Panel> panels = new ArrayList<Panel>(Arrays.asList(this.application.getPanels()));
        panels.add(panel);
        this.application.setPanels(panels.toArray(new Panel[panels.size()]));
        panel.addPropertyChangeListener(application.childListener);
        application.setPanel(panel);

        return panel;
    }

    private void addPlotFocusListener(DasPlot plot) {
        logger.fine("add focus listener to " + plot);
        plot.addFocusListener(new FocusAdapter() {

            @Override
            public void focusGained(FocusEvent e) {
                super.focusGained(e);
                Plot domPlot = getPlotFor(((DasPlot) e.getComponent()));
                if (domPlot == null) {
                    return;
                }
                List<Panel> ps = ApplicationController.this.getPanelsFor(domPlot);
                if (ps.size() > 0) {
                    Panel p = ApplicationController.this.getPanelsFor(domPlot).get(0);
                    logger.fine("focus to " + p);
                    setFocusUri(p.getDataSourceFilter().getSuri());
                    setStatus("" + p + " selected");
                    application.setPanel(p);
                }
                application.setPlot(domPlot);

            }

            private Plot getPlotFor(DasPlot dasPlot) {
                Plot plot = null;
                for (Plot p : application.getPlots()) {
                    if (p.getController().getDasPlot() == dasPlot) {
                        plot = p;
                    }
                }
                return plot;
            }
        });
    }

    public synchronized Plot addPlot() {
        logger.fine("enter addPlot");
        final Plot domPlot = new Plot();

        domPlot.getXaxis().setRange(application.getTimeRange());
        DatumRange x = application.getTimeRange();
        DatumRange y = DatumRange.newDatumRange(0, 1000, Units.dimensionless);
        DasAxis xaxis = new DasAxis(x.min(), x.max(), DasAxis.HORIZONTAL);
        DasAxis yaxis = new DasAxis(y.min(), y.max(), DasAxis.VERTICAL);
        DasRow row = new DasRow(null, outerRow, 0, 1, 0, 0, 0, 0);
        DasColumn col = outerColumn; //new DasColumn( null, outerColumn, 0, 1, 0, 0, 0, 0); //only stack for now
        DasPlot plot = new DasPlot(xaxis, yaxis);

        plot.setPreviewEnabled(true);

        DatumRange colorRange = new DatumRange(0, 100, Units.dimensionless);
        DasColorBar colorbar = new DasColorBar(colorRange.min(), colorRange.max(), false);
        colorbar.setFillColor(new java.awt.Color(0, true));
        domPlot.setId("plot_" + application.getPlots().length);
        final PlotController plotController = new PlotController(application, domPlot, plot, colorbar);

        DasCanvas canvas = getDasCanvas();

        canvas.add(plot, row, col);

        // the axes need to know about the plot, so they can do reset axes units properly.
        plot.getXAxis().setPlot(plot);
        plot.getYAxis().setPlot(plot);

        plot.getMouseAdapter().addMouseModule(new MouseModule(plot, new PointSlopeDragRenderer(plot, plot.getXAxis(), plot.getYAxis()), "Slope"));

        plot.getMouseAdapter().removeMenuItem("Dump Data");

        plot.getMouseAdapter().addMenuItem(new JSeparator());

        plot.getMouseAdapter().addMenuItem(new JMenuItem(new AbstractAction("Reset Zoom") {

            public void actionPerformed(ActionEvent e) {
                plotController.resetZoom();
            }
        }));

        plot.getMouseAdapter().addMenuItem(new JMenuItem(new AbstractAction("Copy Panels") {

            public void actionPerformed(ActionEvent e) {
                copyPanels(domPlot);
            }
        }));

        plot.getMouseAdapter().addMenuItem(new JMenuItem(new AbstractAction("Copy Plot") {

            public void actionPerformed(ActionEvent e) {
                copyPlot(domPlot);
            }
        }));

        plot.getMouseAdapter().addMenuItem(new JMenuItem(new AbstractAction("Delete Plot") {

            public void actionPerformed(ActionEvent e) {
                deletePlot(domPlot, false);
            }
        }));

        BoxZoomMouseModule boxmm = (BoxZoomMouseModule) plot.getMouseAdapter().getModuleByLabel("Box Zoom");
        plot.getMouseAdapter().setPrimaryModule(boxmm);

        //plot.getMouseAdapter().addMouseModule( new AnnotatorMouseModule(plot) ) ;

        canvas.add(colorbar, plot.getRow(), DasColorBar.getColorBarColumn(plot.getColumn()));
        colorbar.setVisible(false);

        if (!headless) {
            boxmm.setAutoUpdate(true);
        }

        MouseModule zoomPan = new ZoomPanMouseModule(plot, plot.getXAxis(), plot.getYAxis());
        plot.getMouseAdapter().setSecondaryModule(zoomPan);

        MouseModule zoomPanX = new ZoomPanMouseModule(plot.getXAxis(), plot.getXAxis(), null);
        plot.getXAxis().getMouseAdapter().setSecondaryModule(zoomPanX);

        MouseModule zoomPanY = new ZoomPanMouseModule(plot.getYAxis(), null, plot.getYAxis());
        plot.getYAxis().getMouseAdapter().setSecondaryModule(zoomPanY);

        MouseModule zoomPanZ = new ZoomPanMouseModule(colorbar, null, colorbar);
        colorbar.getMouseAdapter().setSecondaryModule(zoomPanZ);

        canvas.revalidate();
        canvas.repaint();

        layoutListener.listenTo(plot);
        layoutListener.listenTo(colorbar);

        new AxisController(application, domPlot.getXaxis(), xaxis);
        new AxisController(application, domPlot.getYaxis(), yaxis);
        new AxisController(application, domPlot.getZaxis(), colorbar);

        domPlot.bindTo(plot);
        domPlot.bindTo(colorbar);

        addPlotFocusListener(plot);

        List<Plot> plots = new ArrayList<Plot>(Arrays.asList(application.getPlots()));
        plots.add(domPlot);
        application.setPlots(plots.toArray(new Plot[plots.size()]));

        domPlot.addPropertyChangeListener(application.childListener);
        application.setPlot(domPlot);

        if (plots.size() == 1) {
            bind(application, Application.PROP_TIMERANGE, domPlot, "xaxis." + Axis.PROP_RANGE);
        }

        bind(application, "options." + Options.PROP_DRAWGRID, plot, "drawGrid");
        bind(application, "options." + Options.PROP_DRAWMINORGRID, plot, "drawMinorGrid");
        bind(application, "options." + Options.PROP_OVERRENDERING, plot, "overSize");

        return domPlot;
    }

    private void copyPanels(Plot domPlot) {
        List<Panel> p = getPanelsFor(domPlot);
        if (p.size() == 0) {
            return;
        }

        Plot newPlot= copyPlot(domPlot);
        Panel newp = addPanel(newPlot);
        String plotId = newp.getPlotId();
        newp.syncTo(p.get(0)); //TODO: maybe syncTo with exclude
        newp.setPlotId(plotId);

    }

    public synchronized Plot copyPlot(Plot domPlot) {
        Plot that = addPlot();
        that.syncTo(domPlot);
        BindingModel bb= findBinding( application, Application.PROP_TIMERANGE,  domPlot, "xaxis."+Axis.PROP_RANGE );
        if ( bb==null ) {
            bind(domPlot, "xaxis." + Axis.PROP_RANGE, that, "xaxis." + Axis.PROP_RANGE);
        } else {
            bind( application, Application.PROP_TIMERANGE, that, "xaxis." + Axis.PROP_RANGE);
        }
        return that;
    }

    public synchronized void deletePlot(Plot domPlot, boolean allowOrphanPanels) {

        unbind(domPlot);

        DasPlot p = domPlot.getController().getDasPlot();
        this.getDasCanvas().remove(p);
        DasColorBar cb = domPlot.getController().getDasColorBar();
        this.getDasCanvas().remove(cb);
        List<Panel> panels = this.getPanelsFor(domPlot);
        
        if (!allowOrphanPanels) {
            for (Panel pan : panels) {
                deletePanel(pan);
            }
        }
        
        List<Plot> plots = new ArrayList<Plot>(Arrays.asList(application.getPlots()));
        plots.remove(domPlot);
        
        if (!plots.contains(application.getPlot())) {
            if ( plots.size()==0 ) {
                application.setPlot(null); 
            } else {
                application.setPlot(plots.get(0)); 
            }
        }
        application.setPlots(plots.toArray(new Plot[plots.size()]));

    }

    /**
     * binds two bean properties together.  Bindings are bidirectional, but
     * the initial copy is from src to dst.  In MVC terms, src should be the model
     * and dst should be a view.  The properties must fire property
     * change events for the binding mechanism to work.
     * 
     * Example:
     * model= getApplicationModel()
     * bind( model.getPlotDefaults(), "title", model.getPlotDefaults().getXAxis(), "label" )
     * 
     * @param src java bean such as model.getPlotDefaults()
     * @param srcProp a property name such as "title"
     * @param dst java bean such as model.getPlotDefaults().getXAxis()
     * @param dstProp a property name such as "label"
     */
    public void bind(DomNode src, String srcProp, Object dst, String dstProp) {
        BindingContext bc;
        synchronized (bindingContexts) {
            bc = bindingContexts.get(src);
            if (bc == null) {
                bc = new BindingContext();
            }
            bindingContexts.put(src, bc);
        }

        Binding b = bc.addBinding(src, "${" + srcProp + "}", dst, dstProp);

        BindingModel bb = new BindingModel();

        String srcId = "???";
        if (src instanceof DomNode) srcId = src.getId();
        String dstId = "???";
        if (dst instanceof DomNode) dstId = ((DomNode) dst).getId();

        bb.setBindingContextId(srcId);
        bb.setSrcId(srcId);

        bb.setDstId(dstId);
        bb.setSrcProperty(srcProp);
        bb.setDstProperty(dstProp);

        if (!dstId.equals("???")) {
            List<BindingModel> bindings = new ArrayList<BindingModel>(Arrays.asList(application.getBindings()));
            bindings.add(bb);
            application.setBindings(bindings.toArray(new BindingModel[bindings.size()]));
        }

        bc.bind();
        
        this.bindings.put(bb, b);
    }

    /**
     * unbind the object.  For example, when the object is about to be deleted.
     * @param src
     */
    public void unbind(DomNode src) {
        BindingContext bc;

        synchronized (bindings) {
            List<BindingModel> bb= new ArrayList( Arrays.asList( application.getBindings() ) );
            for ( BindingModel b: application.getBindings() ) {
                if ( b.getSrcId().equals(src.getId()) || b.getDstId().equals(src.getId()) ) {
                    bb.remove(b);
                    bindings.get(b).unbind();
                    bindings.remove(b);
                }
            }
            application.setBindings( bb.toArray( new BindingModel[ bb.size() ]) );
        }
        
        synchronized (bindingContexts) {
            bc = bindingContexts.get(src);
            if (bc != null) {

                bc.unbind();
                bindingContexts.remove(bc);

                String bcid = src.getId();
                List<BindingModel> bindings = new ArrayList<BindingModel>(Arrays.asList(application.getBindings()));
                List<BindingModel> bb2 = new ArrayList<BindingModel>(Arrays.asList(application.getBindings()));
                for (BindingModel bb : bb2) {
                    if (bb.getBindingContextId().equals(bcid)) {
                        bindings.remove(bb);
                    }
                }
                application.setBindings(bindings.toArray(new BindingModel[bindings.size()]));
                
            }
        }
        
    }
    
    /**
     * Find the binding, if it exists.  All bindings are symmetric, so the src and dst order is ignored in this
     * search.  
     * @param src
     * @param srcProp
     * @param dst
     * @param dstProp
     * @return the BindingModel or null if it doesn't exist.
     */
    public BindingModel findBinding( DomNode src, String srcProp, DomNode dst, String dstProp ) {
            for ( BindingModel b: application.getBindings() ) {
                if ( b.getSrcId().equals(src.getId()) 
                        && b.getDstId().equals(dst.getId()) 
                        && b.getSrcProperty().equals(srcProp)
                        && b.getDstProperty().equals(dstProp) ) return b;
                if ( b.getSrcId().equals(dst.getId()) 
                        && b.getDstId().equals(src.getId()) 
                        && b.getSrcProperty().equals(dstProp)
                        && b.getDstProperty().equals(srcProp) ) return b;
                
            }
            return null;
    }

    /**
     * Some sort of processing is going on, so wait until idle.
     * @return
     */
    public boolean isPendingChanges() {
        boolean result = false;
        for (Panel p : application.getPanels()) {
            result = result | p.getController().isPendingChanges();
        }
        return result;
    }

    /**
     * the application state is rapidly changing.
     * @return
     */
    public boolean isValueAdjusting() {
        return false;
    }
    protected String status = "";
    public static final String PROP_STATUS = "status";

    public String getStatus() {
        return status;
    }

    /**
     * clients can send messages to here.  The message may be conventionally 
     * prefixed with "busy:" "error:" or "warning:" (And these will be displayed
     * as icons, for example, in the view.)
     *
     * @param status
     */
    public void setStatus(String status) {
        String oldStatus = this.status;
        this.status = status;
        propertyChangeSupport.firePropertyChange(PROP_STATUS, oldStatus, status);
    }
    protected String focusUri = "";
    /**
     * property focusUri is the uri that has gained focus.  This can be the datasource uri, or the location of the .vap file.
     */
    public static final String PROP_FOCUSURI = "focusUri";

    public String getFocusUri() {
        return focusUri;
    }

    public void setFocusUri(String focusUri) {
        if (focusUri == null) focusUri = "";
        String oldFocusUri = this.focusUri;
        this.focusUri = focusUri;
        propertyChangeSupport.firePropertyChange(PROP_FOCUSURI, oldFocusUri, focusUri);
    }
    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public synchronized void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(propertyName, listener);
    }

    public synchronized void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    public synchronized void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
    }

    public synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    /**
     * return the plot containing this panel.
     * @param panel
     * @return the Plot or null if no plot is found.
     * @throws IllegalArgumentException if the panel is not a child of the application
     */
    public Plot getPlotFor(Panel panel) {
        if ( !application.panels.contains( panel ) ) {
            throw new IllegalArgumentException("the panel is not a child of the application");
        }
        String id = panel.getPlotId();
        Plot result= null;
        for (Plot p : application.getPlots()) {
            if (p.getId().equals(id)) {
                result= p;
            }
        }
        return result;
    }

    List<Panel> getPanelsFor(Plot plot) {
        String id = plot.getId();
        List<Panel> result = new ArrayList<Panel>();
        for (Panel p : application.getPanels()) {
            if (p.getPlotId().equals(id)) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * true if running in headless environment
     */
    public boolean isHeadless() {
        return headless;
    }

    public DasCanvas getDasCanvas() {
        return application.getCanvas().getController().getDasCanvas();
    }

    public DasRow getRow() {
        return this.outerRow;
    }

    public DasColumn getColumn() {
        return this.outerColumn;
    }

    public MonitorFactory getMonitorFactory() {
        return new MonitorFactory() {

            public ProgressMonitor getMonitor(DasCanvas canvas, String string, String desc) {
                return DasApplication.getDefaultApplication().getMonitorFactory().getMonitor(canvas, string, desc);
            }

            public ProgressMonitor getMonitor(DasCanvasComponent context, String label, String description) {
                return DasApplication.getDefaultApplication().getMonitorFactory().getMonitor(context, label, description);
            }

            public ProgressMonitor getMonitor(String label, String description) {
                return DasApplication.getDefaultApplication().getMonitorFactory().getMonitor(getDasCanvas(), label, description);
            }
        };
    }
}
