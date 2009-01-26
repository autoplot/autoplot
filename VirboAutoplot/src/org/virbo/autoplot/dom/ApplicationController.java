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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import org.das2.DasApplication;
import org.das2.components.propertyeditor.PropertyEditor;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.das2.event.BoxZoomMouseModule;
import org.das2.event.MouseModule;
import org.das2.event.PointSlopeDragRenderer;
import org.das2.event.ZoomPanMouseModule;
import org.das2.graph.ColumnColumnConnector;
import org.das2.graph.DasAxis;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasCanvasComponent;
import org.das2.graph.DasColorBar;
import org.das2.graph.DasColumn;
import org.das2.graph.DasPlot;
import org.das2.graph.DasRow;
import org.das2.graph.Renderer;
import org.das2.system.MonitorFactory;
import org.das2.system.MutatorLock;
import org.das2.util.monitor.ProgressMonitor;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.autoplot.AutoplotUtil;
import org.virbo.autoplot.ColumnColumnConnectorMouseModule;
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
    Map<Object, BindingGroup> bindingContexts;
    Map<BindingModel, Binding> bindingImpls;
    Map<Connector, ColumnColumnConnector> connectorImpls;
    private final static Logger logger = Logger.getLogger("virbo.controller");
    private int plotIdNum = 0;
    private int panelIdNum = 0;
    private int dsfIdNum = 0;

    public ApplicationController(ApplicationModel model, Application application) {
        this.application = application;
        application.setId("app_0");
        application.getOptions().setId("options_0");
        this.model = model;
        application.controller = this;
        this.headless = "true".equals(AutoplotUtil.getProperty("java.awt.headless", "false"));
        if (!headless) {
            application.getOptions().loadPreferences();
        }
        bindingContexts = new HashMap<Object, BindingGroup>();
        bindingImpls = new HashMap<BindingModel, Binding>();
        connectorImpls = new HashMap<Connector, ColumnColumnConnector>();

        addListeners();
    }

    private synchronized DataSourceFilter addDataSourceFilter() {
        DataSourceFilter dsf = new DataSourceFilter();
        new DataSourceController(this.model, dsf);
        dsf.setId("data_" + dsfIdNum);
        dsfIdNum++;
        List<DataSourceFilter> dataSourceFilters = new ArrayList<DataSourceFilter>(Arrays.asList(this.application.getDataSourceFilters()));
        dataSourceFilters.add(dsf);
        this.application.setDataSourceFilters(dataSourceFilters.toArray(new DataSourceFilter[dataSourceFilters.size()]));

        return dsf;
    }

    private void addListeners() {
        this.addPropertyChangeListener(ApplicationController.PROP_PANEL, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                Panel p = getPanel();
                if (p != null) {
                    setDataSourceFilter(getDataSourceFilterFor(p));
                    setPlot(getPlotFor(p));
                } else {
                    setDataSourceFilter(null);
                }
            }
        });
    }

    /**
     * add a canvas to the application.  Currently, only one canvas is supported, so this
     * will have unanticipated effects if called more than once.
     * @return
     */
    public DasCanvas addCanvas() {
        logger.fine("enter addCanvas");
        //if ( canvas!=null ) throw new IllegalArgumentException("only one canvas for now");
        DasCanvas dasCanvas = new DasCanvas();
        Canvas canvas = new Canvas();
        canvas.setId("canvas_0");
        new CanvasController(application, canvas).setDasCanvas(dasCanvas);

        outerRow = new DasRow(dasCanvas, null, 0, 1, 0, -3, 0, 0);
        outerColumn = new DasColumn(dasCanvas, null, 0, 1, 5, -3, 0, 0);

        layoutListener = new LayoutListener(model);

        application.setCanvases(new Canvas[]{canvas});
        setCanvas(canvas);

        bindTo(dasCanvas);

        canvas.getController().bindTo(outerRow, outerColumn);

        dasCanvas.setPrintingTag("");
        return dasCanvas;
    }

    public void deletePanel(Panel panel) {
        int currentIdx = application.panels.indexOf(panel);
        if (currentIdx == -1) {
            throw new IllegalArgumentException("deletePanel but panel isn't part of application");
        }
        if (application.panels.size() < 2) {
            throw new IllegalArgumentException("last panel may not be deleted");
        }
        DasPlot p = panel.getController().getDasPlot();
        if (p != null) {
            Renderer r = panel.getController().getRenderer();
            if (r != null) {
                p.removeRenderer(r);
            }
        }

        unbind(panel);

        ArrayList<Panel> panels = new ArrayList<Panel>(Arrays.asList(application.getPanels()));
        panels.remove(panel);
        if (!panels.contains(getPanel())) {  // reset the focus panelId
            if (panels.size() == 0) {
                setPanel(null);
            } else {
                setPanel(panels.get(0)); // maybe use currentIdx
            }
        }
        application.setPanels(panels.toArray(new Panel[panels.size()]));
    }

    /**
     * adds a context overview plotId below the plotId.
     * @param domPlot
     */
    protected void addConnector(Plot domPlot, Plot that) {

        List<Connector> connectors = new ArrayList<Connector>(Arrays.asList(application.getConnectors()));
        final Connector connector = new Connector(domPlot.getId(), that.getId());
        connectors.add(connector);

        application.setConnectors(connectors.toArray(new Connector[connectors.size()]));

        DasCanvas canvas = getDasCanvas();
        DasPlot upper = domPlot.getController().getDasPlot();
        DasPlot lower = that.getController().getDasPlot();

        //overviewPlotConnector.getMouseAdapter().setPrimaryModule(overviewZoom);
        ColumnColumnConnector overviewPlotConnector =
                new ColumnColumnConnector(canvas, upper,
                DasRow.create(null, upper.getRow(), "0%", "100%+2em"), lower);

        connectorImpls.put(connector, overviewPlotConnector);

        overviewPlotConnector.setBottomCurtain(true);
        overviewPlotConnector.setCurtainOpacityPercent(80);

        overviewPlotConnector.getMouseAdapter().setSecondaryModule(new ColumnColumnConnectorMouseModule(upper, lower));
        canvas.add(overviewPlotConnector);

    //TODO: disconnect/delete if one plotId is deleted.

    }

    public void deleteConnector(Connector connector) {
        ColumnColumnConnector impl = connectorImpls.get(connector);
        getDasCanvas().remove(impl);

        List<Connector> connectors = DomUtil.asArrayList(application.getConnectors());
        connectors.remove(connector);

        connectorImpls.remove(connector);

        application.setConnectors(connectors);
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
     * add a panelId to the application.
     * @return
     */
    public synchronized Panel addPanel(Plot domPlot, DataSourceFilter dsf) {
        logger.fine("enter addPanel");

        final int fpanelIdNum = this.panelIdNum++;
        final Panel panel = new Panel();

        if (dsf == null) {
            dsf = addDataSourceFilter();

            setDataSourceFilter(dsf);

        }

        new PanelController(this.model, application, panel);

        if (domPlot == null) {
            domPlot = addPlot();
        }

        panel.setId("panel_" + fpanelIdNum);

        /*  final Plot fplot = domPlot;
        
        // bind it to the common range if it looks compatible
        panelId.getPlotDefaults().getXaxis().addPropertyChangeListener(Axis.PROP_RANGE, new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
        DatumRange dr = panelId.getPlotDefaults().getXaxis().getRange();
        DatumRange appRange = application.getTimeRange();
        if (appRange.getUnits().isConvertableTo(dr.getUnits()) && appRange.intersects(dr)) {
        bind(application, Application.PROP_TIMERANGE, fplot, "xaxis." + Axis.PROP_RANGE);
        }
        }
        }); */

        panel.getStyle().setId("style_" + fpanelIdNum);
        panel.getPlotDefaults().setId("plot_defaults_" + fpanelIdNum);

        panel.addPropertyChangeListener(Panel.PROP_PLOTID, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                Panel p = (Panel) evt.getSource();
                String srcid = (String) evt.getOldValue();
                String dstid = (String) evt.getNewValue();
                if (srcid == null) {
                    return; // initialization state
                }
                assert !srcid.equals("");
                assert dstid != null && !dstid.equals("");
                Plot src = (Plot) DomUtil.getElementById(application, srcid);
                Plot dst = (Plot) DomUtil.getElementById(application, dstid);
                if (src != null && dst != null) {
                    movePanel(p, src, dst);
                    if (getPanelsFor(src).size() == 0) {
                        deletePlot(src);
                    }
                }
            }
        });

        panel.setPlotId(domPlot.getId());
        panel.setDataSourceFilterId(dsf.getId());

        List<Panel> panels = new ArrayList<Panel>(Arrays.asList(this.application.getPanels()));
        panels.add(panel);
        this.application.setPanels(panels.toArray(new Panel[panels.size()]));
        panel.addPropertyChangeListener(application.childListener);
        setPanel(panel);

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
                    setFocusUri(p.getController().getDataSourceFilter().getSuri());
                    if (getPanel() != p) {
                        setStatus("" + p + " selected");
                        setPanel(p);
                    }
                }
                setPlot(domPlot);

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
        final DasPlot plot = new DasPlot(xaxis, yaxis);

        plot.setPreviewEnabled(true);

        DatumRange colorRange = new DatumRange(0, 100, Units.dimensionless);
        DasColorBar colorbar = new DasColorBar(colorRange.min(), colorRange.max(), false);
        colorbar.setFillColor(new java.awt.Color(0, true));

        int num = plotIdNum++;

        domPlot.setId("plot_" + num);
        domPlot.getXaxis().setId("xaxis_" + num);
        domPlot.getYaxis().setId("yaxis_" + num);
        domPlot.getZaxis().setId("zaxis_" + num);

        final PlotController plotController = new PlotController(application, domPlot, plot, colorbar);

        DasCanvas canvas = getDasCanvas();

        canvas.add(plot, row, col);

        // the axes need to know about the plotId, so they can do reset axes units properly.
        plot.getXAxis().setPlot(plot);
        plot.getYAxis().setPlot(plot);

        addPlotContextMenuItems(plot, plotController, domPlot);

        BoxZoomMouseModule boxmm = (BoxZoomMouseModule) plot.getMouseAdapter().getModuleByLabel("Box Zoom");
        plot.getMouseAdapter().setPrimaryModule(boxmm);

        //plotId.getMouseAdapter().addMouseModule( new AnnotatorMouseModule(plotId) ) ;

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

        domPlot.getController().bindTo(plot);
        domPlot.getController().bindTo(colorbar);

        addPlotFocusListener(plot);

        plot.addPropertyChangeListener(DasPlot.PROP_FOCUSRENDERER, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                Renderer r = plot.getFocusRenderer();
                if (r == null) {
                    return;
                }
                Panel p = findPanel(r);
                
                if (getPanel() != p) {
                    setStatus("" + p + " selected");
                    setPanel(p);
                }
                
            }
        });

        List<Plot> plots = new ArrayList<Plot>(Arrays.asList(application.getPlots()));
        plots.add(domPlot);
        application.setPlots(plots.toArray(new Plot[plots.size()]));

        domPlot.addPropertyChangeListener(application.childListener);
        setPlot(domPlot);

        if (plots.size() == 1) {
            bind(application, Application.PROP_TIMERANGE, domPlot, "xaxis." + Axis.PROP_RANGE);
        }

        bind(application, "options." + Options.PROP_DRAWGRID, plot, "drawGrid");
        bind(application, "options." + Options.PROP_DRAWMINORGRID, plot, "drawMinorGrid");
        bind(application, "options." + Options.PROP_OVERRENDERING, plot, "overSize");

        return domPlot;
    }

    /**
     * find the panelId using this renderer.
     * @param rend
     * @return
     */
    private Panel findPanel(Renderer rend) {
        for (Panel p : application.getPanels()) {
            PanelController pc = p.getController();
            if (pc.getRenderer() == rend) {
                return p;
            }
        }
        throw new IllegalArgumentException("unable to find panel for das renderer");
    }

    /**
     * copy plot and panels into a new plot.
     * @param domPlot
     * @param dsf
     * @return
     */
    protected Plot copyPlotAndPanels(Plot domPlot, DataSourceFilter dsf) {
        List<Panel> p = getPanelsFor(domPlot);

        Plot newPlot = copyPlot(domPlot);
        if (p.size() == 0) {
            return newPlot;
        }

        for ( Panel srcPanel: p ) {
            Panel newp= copyPanel( srcPanel, newPlot, dsf );
        }
        return newPlot;

    }

    /**
     * copy the panel and put it in domPlot.
     * @param srcPanel the panel to copy.
     * @param domPlot  plot to contain the new panel, which may be the same plot.
     * @param dsf     if non-null, then use this dataSourceFilter
     * @return
     */
    protected Panel copyPanel(Panel srcPanel, Plot domPlot, DataSourceFilter dsf) {
        Panel newp = addPanel(domPlot, dsf);
        newp.syncTo(srcPanel, Arrays.asList("plotId", "dataSourceFilterId"));
        if ( dsf==null ) {
            newp.getController().getDataSourceFilter().getController().setDataSetInternal(srcPanel.getController().getDataSourceFilter().getController().getDataSet(), true);
            if (srcPanel.getController().getDataSourceFilter().getSuri() != null) {
                newp.getController().getDataSourceFilter().suri = srcPanel.getController().getDataSourceFilter().getSuri();
            }
        }
        return newp;
    }

    /**
     * copy the plor and its axis settings.  The xaxis is bound to the srcPlot.
     * @param srcPlot
     * @return
     */
    public synchronized Plot copyPlot(Plot srcPlot) {
        Plot that = addPlot();
        that.syncTo(srcPlot);
        BindingModel bb = findBinding(application, Application.PROP_TIMERANGE, srcPlot, "xaxis." + Axis.PROP_RANGE);
        if (bb == null) {
            bind(srcPlot, "xaxis." + Axis.PROP_RANGE, that, "xaxis." + Axis.PROP_RANGE);
        } else {
            bind(application, Application.PROP_TIMERANGE, that, "xaxis." + Axis.PROP_RANGE);
        }
        return that;
    }

    public synchronized void deletePlot(Plot domPlot) {

        if (!application.plots.contains(domPlot)) {
            throw new IllegalArgumentException("plot is not in this application");
        }
        if (application.plots.size() < 2) {
            throw new IllegalArgumentException("last plot cannot be deleted");
        }
        List<Panel> panels = this.getPanelsFor(domPlot);
        if (panels.size() > 0) {
            throw new IllegalArgumentException("plot must not have panels before deleting");
        }
        for (Connector c : DomUtil.asArrayList(application.getConnectors())) {
            if (c.getPlotA().equals(domPlot.getId()) || c.getPlotB().equals(domPlot.getId())) {
                deleteConnector(c);
            }
        }

        unbind(domPlot);
        unbind(domPlot.getXaxis());
        unbind(domPlot.getYaxis());
        unbind(domPlot.getZaxis());

        DasPlot p = domPlot.getController().getDasPlot();
        this.getDasCanvas().remove(p);
        DasColorBar cb = domPlot.getController().getDasColorBar();
        this.getDasCanvas().remove(cb);

        List<Plot> plots = new ArrayList<Plot>(Arrays.asList(application.getPlots()));
        plots.remove(domPlot);

        if (!plots.contains(getPlot())) {
            if (plots.size() == 0) {
                setPlot(null);
            } else {
                setPlot(plots.get(0));
            }
        }
        application.setPlots(plots.toArray(new Plot[plots.size()]));

    }

    public synchronized void deleteDataSourceFilter(DataSourceFilter dsf) {
        if (!application.dataSourceFilters.contains(dsf)) {
            throw new IllegalArgumentException("plot is not in this application");
        }
        if (application.dataSourceFilters.size() < 2) {
            throw new IllegalArgumentException("last plot cannot be deleted");
        }
        List<Panel> panels = this.getPanelsFor(dsf);
        if (panels.size() > 0) {
            throw new IllegalArgumentException("plot must not have panels before deleting");
        }
        unbind(dsf);

        List<DataSourceFilter> dsfs = new ArrayList<DataSourceFilter>(Arrays.asList(application.getDataSourceFilters()));
        dsfs.remove(dsf);

        if (!dsfs.contains(getDataSourceFilter())) {
            if (dsfs.size() == 0) {
                setDataSourceFilter(null);
            } else {
                setDataSourceFilter(dsfs.get(0));
            }
        }
        application.setDataSourceFilters(dsfs.toArray(new DataSourceFilter[dsfs.size()]));

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
        BindingGroup bc;
        synchronized (bindingContexts) {
            bc = bindingContexts.get(src);
            if (bc == null) {
                bc = new BindingGroup();
            }
            bindingContexts.put(src, bc);
        }

        Binding b= Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, src, BeanProperty.create(srcProp), dst, BeanProperty.create(dstProp) );
        bc.addBinding( b );

        BindingModel bb = new BindingModel();

        String srcId = "???";
        if (src instanceof DomNode) {
            srcId = src.getId();
        }
        String dstId = "???";
        if (dst instanceof DomNode) {
            dstId = ((DomNode) dst).getId();
        }
        if (dst instanceof DasCanvasComponent) {
            dstId = "das2:" + ((DasCanvasComponent) dst).getDasName();
        }
        bb.setBindingContextId(srcId);
        bb.setSrcId(srcId);

        bb.setDstId(dstId);
        bb.setSrcProperty(srcProp);
        bb.setDstProperty(dstProp);

        if (!dstId.equals("???") && !dstId.startsWith("das2:")) {
            List<BindingModel> bindings = new ArrayList<BindingModel>(Arrays.asList(application.getBindings()));
            bindings.add(bb);
            application.setBindings(bindings.toArray(new BindingModel[bindings.size()]));
        }

        b.bind();

        this.bindingImpls.put(bb, b);
    }

    /**
     * unbind the object.  For example, when the object is about to be deleted.
     * @param src
     */
    public void unbind(DomNode src) {
        BindingGroup bc;

        synchronized (bindingImpls) {
            List<BindingModel> bb = new ArrayList(Arrays.asList(application.getBindings()));
            for (BindingModel b : application.getBindings()) {
                if (b.getSrcId().equals(src.getId()) || b.getDstId().equals(src.getId())) {
                    bb.remove(b);
                    bindingImpls.get(b).unbind();
                    bindingImpls.remove(b);
                }
            }
            application.setBindings(bb.toArray(new BindingModel[bb.size()]));
        }

        synchronized (bindingContexts) {
            bc = bindingContexts.get(src);
            if (bc != null) {

                bc.unbind();
                bindingContexts.remove(bc);

                String bcid = src.getId();
                List<BindingModel> bindings = DomUtil.asArrayList(application.getBindings());
                List<BindingModel> bb2 = DomUtil.asArrayList(application.getBindings());
                for (BindingModel bb : bb2) { // avoid concurrent modification
                    if (bb.getBindingContextId().equals(bcid)) {
                        bindings.remove(bb);
                    }
                }
                application.setBindings(bindings.toArray(new BindingModel[bindings.size()]));

            }
        }

    }

    public void deleteBinding(BindingModel binding) {
        Binding b = bindingImpls.get(binding);
        b.unbind();
        bindingImpls.remove(binding);

        List<BindingModel> bindings = DomUtil.asArrayList(application.getBindings());
        bindings.remove(binding);
        application.setBindings(bindings);

    }

    /**
     * Find the binding, if it exists.  All bindingImpls are symmetric, so the src and dst order is ignored in this
     * search.  
     * @param src
     * @param srcProp
     * @param dst
     * @param dstProp
     * @return the BindingModel or null if it doesn't exist.
     */
    public BindingModel findBinding(DomNode src, String srcProp, DomNode dst, String dstProp) {
        for (BindingModel b : application.getBindings()) {
            try {
                if (b.getSrcId().equals(src.getId()) && b.getDstId().equals(dst.getId()) && b.getSrcProperty().equals(srcProp) && b.getDstProperty().equals(dstProp)) {
                    return b;
                }
                if (b.getSrcId().equals(dst.getId()) && b.getDstId().equals(src.getId()) && b.getSrcProperty().equals(dstProp) && b.getDstProperty().equals(srcProp)) {
                    return b;
                }
            } catch (NullPointerException ex) {
                throw ex;
            }

        }
        return null;
    }

    public BindingModel[] getBindingsFor(DomNode node) {
        List<BindingModel> result = new ArrayList<BindingModel>();
        for (BindingModel b : application.getBindings()) {
            if (b.getSrcId().equals(node.getId()) || b.getDstId().equals(node.getId())) {
                result.add(b);
            }
        }
        return result.toArray(new BindingModel[result.size()]);
    }

    private void addPlotContextMenuItems(final DasPlot plot, final PlotController plotController, final Plot domPlot) {

        plot.getMouseAdapter().addMouseModule(new MouseModule(plot, new PointSlopeDragRenderer(plot, plot.getXAxis(), plot.getYAxis()), "Slope"));

        plot.getMouseAdapter().removeMenuItem("Dump Data");
        plot.getMouseAdapter().removeMenuItem("Properties");

        JMenuItem item;

        plot.getMouseAdapter().addMenuItem(new JMenuItem(new AbstractAction("Plot Properties") {

            public void actionPerformed(ActionEvent e) {
                PropertyEditor pp = new PropertyEditor(domPlot);
                pp.showDialog(plot.getCanvas());
            }
        }));

        plot.getMouseAdapter().addMenuItem(new JMenuItem(new AbstractAction("Panel Properties") {

            public void actionPerformed(ActionEvent e) {
                Panel p = getPanel();
                PropertyEditor pp = new PropertyEditor(p);
                pp.showDialog(plot.getCanvas());
            }
        }));

        plot.getMouseAdapter().addMenuItem(new JSeparator());

        plot.getMouseAdapter().addMenuItem(new JMenuItem(new AbstractAction("Reset Zoom") {

            public void actionPerformed(ActionEvent e) {
                plotController.resetZoom();
            }
        }));


        item = new JMenuItem(new AbstractAction("Copy Panels") {

            public void actionPerformed(ActionEvent e) {
                copyPlotAndPanels(domPlot, null);
            }
        });
        item.setToolTipText("make a new plot, and copy the panels into it.  The plot's x axis will be bound to this plot's x axis");
        plot.getMouseAdapter().addMenuItem(item);

        item = new JMenuItem(new AbstractAction("Context Overview") {

            public void actionPerformed(ActionEvent e) {
                Plot that = copyPlotAndPanels(domPlot, null);
                unbind(that);
                addConnector(domPlot, that);
                List<Panel> panelThat = getPanelsFor(that);
                List<Panel> panelThis = getPanelsFor(domPlot);
            }
        });
        item.setToolTipText("make a new plot, and copy the panels into it.  The plot is not bound,\n" +
                "and a connector is drawn between the two.  The panel uris are bound as well.");
        plot.getMouseAdapter().addMenuItem(item);


        item = new JMenuItem(new AbstractAction("Remove Bindings") {

            public void actionPerformed(ActionEvent e) {
                List<Panel> panels = getPanelsFor(domPlot);
                for (Panel pan : panels) {
                    unbind(pan);
                }
                unbind(domPlot);
            }
        });
        item.setToolTipText("remove any plot and panel property bindings");
        plot.getMouseAdapter().addMenuItem(item);


        plot.getMouseAdapter().addMenuItem(new JMenuItem(new AbstractAction("Delete Plot") {

            public void actionPerformed(ActionEvent e) {
                if (application.getPlots().length > 1) {
                    List<Panel> panels = getPanelsFor(domPlot);
                    for (Panel pan : panels) {
                        if (application.getPanels().length > 1) {
                            deletePanel(pan);
                        } else {
                            setStatus("warning: the last panel may not be deleted");
                        }
                    }
                    deletePlot(domPlot);
                } else {
                    setStatus("warning: last plot may not be deleted");
                }
            }
        }));

        plot.getMouseAdapter().addMenuItem( GuiSupport.createEZAccessMenu(domPlot) );
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
        return valueIsAdjusting;
    }
    private boolean valueIsAdjusting = false;

    protected synchronized MutatorLock mutatorLock() {
        return new MutatorLock() {

            public void lock() {
                if (valueIsAdjusting) {
                    System.err.println("lock is already set!");
                }
                valueIsAdjusting = true;
            }

            public void unlock() {
                valueIsAdjusting = false;
            }
        };
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
        if (focusUri == null) {
            focusUri = "";
        }
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
     * return the plotId containing this panelId.
     * @param panelId
     * @return the Plot or null if no plotId is found.
     * @throws IllegalArgumentException if the panelId is not a child of the application
     */
    public Plot getPlotFor(Panel panel) {
        if (!application.panels.contains(panel)) {
            throw new IllegalArgumentException("the panel is not a child of the application");
        }
        String id = panel.getPlotId();
        Plot result = null;
        for (Plot p : application.getPlots()) {
            if (p.getId().equals(id)) {
                result = p;
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

    DataSourceFilter getDataSourceFilterFor(Panel panel) {
        String id = panel.getDataSourceFilterId();
        DataSourceFilter result = null;
        for (DataSourceFilter dsf : application.getDataSourceFilters()) {
            if (dsf.getId().equals(id)) {
                result = dsf;
            }
        }
        return result;
    }

    List<Panel> getPanelsFor(DataSourceFilter plot) {
        String id = plot.getId();
        List<Panel> result = new ArrayList<Panel>();
        for (Panel p : application.getPanels()) {
            if (p.getDataSourceFilterId().equals(id)) {
                result.add(p);
            }
        }
        return result;
    }
    /** focus **/
    /**
     * focus panel
     */
    protected Panel panel;
    public static final String PROP_PANEL = "panel";

    public Panel getPanel() {
        return panel;
    }

    public void setPanel(Panel panel) {
        Panel oldPanel = this.panel;
        this.panel = panel;
        propertyChangeSupport.firePropertyChange(PROP_PANEL, oldPanel, panel);
    }
    /**
     * focus plot.
     */
    protected Plot plot;
    public static final String PROP_PLOT = "plot";

    public Plot getPlot() {
        return plot;
    }

    public void setPlot(Plot plot) {
        Plot oldPlot = this.plot;
        this.plot = plot;
        propertyChangeSupport.firePropertyChange(PROP_PLOT, oldPlot, plot);
    }
    /**
     * focus canvas.
     */
    protected Canvas canvas = new Canvas();
    public static final String PROP_CANVAS = "canvas";

    public Canvas getCanvas() {
        return canvas;
    }

    public void setCanvas(Canvas canvas) {
        Canvas oldCanvas = this.canvas;
        this.canvas = canvas;
        propertyChangeSupport.firePropertyChange(PROP_CANVAS, oldCanvas, canvas);
    }
    /**
     * focus dataSourceFilter.
     */
    protected DataSourceFilter dataSourceFilter;
    public static final String PROP_DATASOURCEFILTER = "dataSourceFilter";

    public DataSourceFilter getDataSourceFilter() {
        return dataSourceFilter;
    }

    public void setDataSourceFilter(DataSourceFilter dataSourceFilter) {
        DataSourceFilter oldDataSourceFilter = this.dataSourceFilter;
        this.dataSourceFilter = dataSourceFilter;
        propertyChangeSupport.firePropertyChange(PROP_DATASOURCEFILTER, oldDataSourceFilter, dataSourceFilter);
    }

    private void bindTo(DasCanvas canvas) {
        ApplicationController ac = this;
        ac.bind(application, "options.background", canvas, "background");
        ac.bind(application, "options.foreground", canvas, "foreground");
        ac.bind(application, "options.canvasFont", canvas, "font");
    }

    /**
     * true if running in headless environment
     */
    public boolean isHeadless() {
        return headless;
    }

    public DasCanvas getDasCanvas() {
        return getCanvas().getController().getDasCanvas();
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
