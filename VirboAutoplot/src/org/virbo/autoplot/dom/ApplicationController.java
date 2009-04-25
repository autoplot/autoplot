/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.awt.Component;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.das2.DasApplication;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.das2.event.BoxZoomMouseModule;
import org.das2.event.MouseModule;
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
import org.virbo.autoplot.LayoutListener;
import org.virbo.autoplot.layout.LayoutConstants;
import org.virbo.autoplot.util.RunLaterListener;

/**
 *
 * @author jbf
 */
public class ApplicationController implements RunLaterListener.PropertyChange {

    Application application;
    ApplicationModel model;
    DasRow outerRow;
    DasColumn outerColumn;
    LayoutListener layoutListener;
    boolean headless;
    final Map<Object, BindingGroup> bindingContexts;
    final Map<BindingModel, Binding> bindingImpls;
    final Map<Connector, ColumnColumnConnector> connectorImpls;
    private final static Logger logger = Logger.getLogger("virbo.controller");
    private int plotIdNum = 0;
    private AtomicInteger panelIdNum = new AtomicInteger(0);
    private int dsfIdNum = 0;
    ChangesSupport changesSupport;
    ApplicationControllerSyncSupport syncSupport;
    ApplicationControllerSupport support;
    /** When non-null, we have the lock */
    MutatorLock canvasLock;

    public ApplicationController(ApplicationModel model, Application application) {
        this.application = application;
        this.syncSupport = new ApplicationControllerSyncSupport(this);
        this.support = new ApplicationControllerSupport(this);
        this.changesSupport = new ChangesSupport(propertyChangeSupport, this);
        application.setId("app_0");
        application.getOptions().setId("options_0");
        this.model = model;
        application.controller = this;
        this.headless = "true".equals(AutoplotUtil.getProperty("java.awt.headless", "false"));
        if (!headless && DasApplication.hasAllPermission()) {
            application.getOptions().loadPreferences();
        }
        bindingContexts = new HashMap<Object, BindingGroup>();
        bindingImpls = new HashMap<BindingModel, Binding>();
        connectorImpls = new HashMap<Connector, ColumnColumnConnector>();

        addListeners();
    }
    FocusAdapter focusAdapter = new FocusAdapter() {

        @Override
        public void focusGained(FocusEvent e) {
            super.focusGained(e);

            Plot domPlot = getPlotFor(e.getComponent());
            if (domPlot == null) {
                return;
            }

            if (getPlot() == domPlot) {
                return;
            }
            List<Panel> ps = ApplicationController.this.getPanelsFor(domPlot);
            if (ps.size() > 0) {
                Panel p = ApplicationController.this.getPanelsFor(domPlot).get(0);
                logger.fine("focus to " + p);
                setFocusUri(p.getController().getDataSourceFilter().getUri());
                if (getPanel() != p) {
                    setStatus("" + domPlot + ", " + p + " selected");
                    setPanel(p);
                }
            }
            setPlot(domPlot);

        }
    };

    public void fillEditPlotMenu(JMenu editPlotMenu, final Plot domPlot) {
        JMenuItem item;
        item = new JMenuItem(new AbstractAction("Remove Bindings") {

            public void actionPerformed(ActionEvent e) {
                List<Panel> panels = getPanelsFor(domPlot);
                for (Panel pan : panels) {
                    unbind(pan);
                }
                unbind(domPlot);
                unbind(domPlot.xaxis);
                unbind(domPlot.yaxis);
                unbind(domPlot.zaxis);
            }
        });
        item.setToolTipText("remove any plot and panel property bindings");
        editPlotMenu.add(item);
        item = new JMenuItem(new AbstractAction("Delete Plot") {

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
        });
        editPlotMenu.add(item);
    }

    public Plot getPlotFor(Component c) {
        Plot plot = null;
        for (Plot p : application.getPlots()) {
            DasPlot p1 = p.getController().getDasPlot();
            if (p1 == c || p1.getXAxis() == c || p1.getYAxis() == c) {
                plot = p;
                break;
            }
            if (p.getController().getDasColorBar() == c) {
                plot = p;
                break;
            }
        }
        return plot;
    }

    public void plot(Plot plot, Panel panel, String secondaryUri, String teriaryUri, String primaryUri) {
        support.plot(plot, panel, secondaryUri, teriaryUri, primaryUri);
    }

    public void plot(Plot plot, Panel panel, String secondaryUri, String primaryUri) {
        support.plot(plot, panel, secondaryUri, primaryUri);
    }

    public void plot(Plot plot, Panel panel, String primaryUri) {
        support.plot(plot, panel, primaryUri);
    }

    /**
     * block the calling thread until the application is idle.
     * @see isPendingChanges.
     */
    public void waitUntilIdle() {
        while (this.isPendingChanges()) {
            try {
                Thread.sleep(30);
            } catch (InterruptedException ex) {
                Logger.getLogger(ApplicationController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    protected synchronized DataSourceFilter addDataSourceFilter() {
        DataSourceFilter dsf = new DataSourceFilter();
        new DataSourceController(this.model, dsf);
        dsf.setId("data_" + dsfIdNum);
        dsfIdNum++;
        List<DataSourceFilter> dataSourceFilters = new ArrayList<DataSourceFilter>(Arrays.asList(this.application.getDataSourceFilters()));
        dataSourceFilters.add(dsf);
        this.application.setDataSourceFilters(dataSourceFilters.toArray(new DataSourceFilter[dataSourceFilters.size()]));
        dsf.addPropertyChangeListener(application.childListener);
        return dsf;
    }

    private void addListeners() {
        this.addPropertyChangeListener(ApplicationController.PROP_PANEL, new PropertyChangeListener() {

            public String toString() {
                return "" + ApplicationController.this;
            }

            public void propertyChange(PropertyChangeEvent evt) {
                if (!isValueAdjusting()) {
                    Panel p = getPanel();
                    if (p != null) {
                        setDataSourceFilter(getDataSourceFilterFor(p));
                        setPlot(getPlotFor(p));
                    } else {
                        setDataSourceFilter(null);
                    }
                }
            }
        });
    }

    /**
     * add a canvas to the application.  Currently, only one canvas is supported, so this
     * will have unanticipated effects if called more than once.
     *
     * This must be public to provide access to org.virbo.autoplot.ApplicationModel
     * @return
     */
    public synchronized DasCanvas addCanvas() {
        logger.fine("enter addCanvas");

        DasCanvas.setDisableActions(true);

        //if ( canvas!=null ) throw new IllegalArgumentException("only one canvas for now");
        DasCanvas dasCanvas = new DasCanvas();
        Canvas lcanvas = new Canvas();
        lcanvas.setId("canvas_0");
        new CanvasController(application, lcanvas).setDasCanvas(dasCanvas);

        String[] ss = lcanvas.getRow().split(",");
        outerRow = DasRow.create(dasCanvas, null, ss[0], ss[1]);
        ss = lcanvas.getColumn().split(",");
        outerColumn = DasColumn.create(dasCanvas, null, ss[0], ss[1]);

        layoutListener = new LayoutListener(model);

        application.setCanvases(new Canvas[]{lcanvas});
        setCanvas(lcanvas);

        bindTo(dasCanvas);

        lcanvas.getController().bindTo(outerRow, outerColumn);

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

        panel.removePropertyChangeListener(application.childListener);
        unbind(panel);
        panel.getController().unbindDsf();
        panel.removePropertyChangeListener(plotIdListener);

        DataSourceFilter dsf = getDataSourceFilterFor(panel);

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

        if (dsf != null) {
            List<Panel> dsfPanels = getPanelsFor(dsf);
            if (dsfPanels.size() == 0) {
                deleteDataSourceFilter(dsf);
            }
        }

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

        DasCanvas lcanvas = getDasCanvas();
        DasPlot upper = domPlot.getController().getDasPlot();
        DasPlot lower = that.getController().getDasPlot();

        //overviewPlotConnector.getDasMouseInputAdapter().setPrimaryModule(overviewZoom);
        ColumnColumnConnector overviewPlotConnector =
                new ColumnColumnConnector(lcanvas, upper,
                DasRow.create(null, upper.getRow(), "0%", "100%+2em"), lower);

        connectorImpls.put(connector, overviewPlotConnector);

        overviewPlotConnector.setBottomCurtain(true);
        overviewPlotConnector.setCurtainOpacityPercent(80);

        MouseModule mm = new ColumnColumnConnectorMouseModule(upper, lower);
        overviewPlotConnector.getDasMouseInputAdapter().setSecondaryModule(mm);
        overviewPlotConnector.getDasMouseInputAdapter().setPrimaryModule(mm);

        lcanvas.add(overviewPlotConnector);
        lcanvas.revalidate();
    //TODO: disconnect/delete if one plotId is deleted.

    }

    public void deleteConnector(Connector connector) {
        ColumnColumnConnector impl = connectorImpls.get(connector);
        getDasCanvas().remove(impl);

        List<Connector> connectors = DomUtil.asArrayList(application.getConnectors());
        connectors.remove(connector);

        connectorImpls.remove(connector);

        application.setConnectors(connectors.toArray(new Connector[connectors.size()]));
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

    PropertyChangeListener plotIdListener = new PropertyChangeListener() {

        public String toString() {
            return "" + ApplicationController.this;
        }

        public void propertyChange(PropertyChangeEvent evt) {
            Panel p = (Panel) evt.getSource();
            String srcid = (String) evt.getOldValue();
            String dstid = (String) evt.getNewValue();
            if (srcid == null || srcid.equals("")) {
                return; // initialization state
            }
            if (dstid == null || dstid.equals("")) {
                return;
            }
            Plot src = (Plot) DomUtil.getElementById(application, srcid);
            Plot dst = (Plot) DomUtil.getElementById(application, dstid);
            if (src != null && dst != null) {
                movePanel(p, src, dst);
                if (getPanelsFor(src).size() == 0) {
                    deletePlot(src);
                }
                if (getPanelsFor(dst).size() == 1) {
                    dst.syncTo(p.plotDefaults);
                }
            }
        }
    };

    /**
     * add a panel to the application, attaching it to the given Plot and DataSourceFilter.
     * @param domPlot if null, create a Plot, if non-null, add the panel to this plot.
     * @param dsf if null, create a DataSourceFilter.  If non-null, connect the panel to this data source.
     * @return
     */
    public Panel addPanel(Plot domPlot, DataSourceFilter dsf) {
        logger.fine("enter addPanel");

        final int fpanelIdNum = this.panelIdNum.getAndIncrement();
        final Panel panel1 = new Panel();

        if (dsf == null) {
            dsf = addDataSourceFilter();
        }

        new PanelController(this.model, application, panel1);

        if (domPlot == null) {
            domPlot = addPlot(LayoutConstants.BELOW);
        }

        panel1.setId("panel_" + fpanelIdNum);

        panel1.getStyle().setId("style_" + fpanelIdNum);
        panel1.getPlotDefaults().setId("plot_defaults_" + fpanelIdNum);
        panel1.getStyle().setColor(application.getOptions().getColor());
        panel1.getStyle().setFillColor(application.getOptions().getFillColor());
        panel1.getStyle().setAntiAliased(application.getOptions().isDrawAntiAlias());

        panel1.addPropertyChangeListener(Panel.PROP_PLOTID, plotIdListener);

        panel1.setPlotId(domPlot.getId());
        panel1.setDataSourceFilterId(dsf.getId());

        synchronized (this) {
            Panel[] p = application.getPanels();
            Panel[] temp = new Panel[p.length + 1];
            System.arraycopy(p, 0, temp, 0, p.length);
            temp[p.length] = panel1;
            this.application.setPanels(temp);
            panel1.addPropertyChangeListener(application.childListener);
            setPanel(panel1);
        }

        return panel1;
    }

    private void addPlotFocusListener(DasPlot plot) {
        logger.fine("add focus listener to " + plot);
        plot.addFocusListener(focusAdapter);
        plot.getXAxis().addFocusListener(focusAdapter);
        plot.getYAxis().addFocusListener(focusAdapter);
    }


    PropertyChangeListener rendererFocusListener= new PropertyChangeListener() {

            public String toString() {
                return "" + ApplicationController.this;
            }

            public void propertyChange(PropertyChangeEvent evt) {
                DasPlot plot= (DasPlot)evt.getSource();
                Renderer r = plot.getFocusRenderer();
                if (r == null) {
                    return;
                }
                Panel p = findPanel(r);
                if (getPanel() != p) {
                    setStatus( p + " selected" );
                    setPanel(p);
                }

            }
        };

    /**
     * add a plot to the canvas.  Direction is with respect to the current
     * focus plot, and currently only LayoutConstants.ABOVE and LayoutConstants.BELOW
     * are supported.
     * 
     * @param direction
     * @return
     */
    public synchronized Plot addPlot(Object direction) {
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
        colorbar.addFocusListener(focusAdapter);
        colorbar.setFillColor(new java.awt.Color(0, true));

        int num = plotIdNum++;

        domPlot.setId("plot_" + num);
        domPlot.getXaxis().setId("xaxis_" + num);
        domPlot.getYaxis().setId("yaxis_" + num);
        domPlot.getZaxis().setId("zaxis_" + num);

        final PlotController plotController = new PlotController(application, domPlot, plot, colorbar);

        DasCanvas dasCanvas = getDasCanvas();

        MutatorLock myCanvasLock = null;

        if (canvasLock == null) {
            myCanvasLock = dasCanvas.mutatorLock();
            myCanvasLock.lock();
            canvasLock = myCanvasLock;
        }

        dasCanvas.add(plot, row, col); //TODO: consider making room on canvas, and setting row precisely

        // the axes need to know about the plotId, so they can do reset axes units properly.
        plot.getXAxis().setPlot(plot);
        plot.getYAxis().setPlot(plot);

        BoxZoomMouseModule boxmm = (BoxZoomMouseModule) plot.getDasMouseInputAdapter().getModuleByLabel("Box Zoom");
        plot.getDasMouseInputAdapter().setPrimaryModule(boxmm);

        //plotId.getDasMouseInputAdapter().addMouseModule( new AnnotatorMouseModule(plotId) ) ;

        dasCanvas.add(colorbar, plot.getRow(), DasColorBar.getColorBarColumn(plot.getColumn()));
        colorbar.setVisible(false);

        if (!headless) {
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
        if (myCanvasLock != null) {
            myCanvasLock.unlock();
        }

        layoutListener.listenTo(plot);
        layoutListener.listenTo(colorbar);

        new AxisController(application, domPlot.getXaxis(), xaxis);
        new AxisController(application, domPlot.getYaxis(), yaxis);
        new AxisController(application, domPlot.getZaxis(), colorbar);

        domPlot.getController().bindTo(plot);
        domPlot.getController().bindTo(colorbar);

        support.addPlotContextMenuItems(plot, plotController, domPlot);
        support.addAxisContextMenuItems(plot, plotController, domPlot, domPlot.getXaxis());
        support.addAxisContextMenuItems(plot, plotController, domPlot, domPlot.getYaxis());
        support.addAxisContextMenuItems(plot, plotController, domPlot, domPlot.getZaxis());

        addPlotFocusListener(plot);

        plot.addPropertyChangeListener(DasPlot.PROP_FOCUSRENDERER, rendererFocusListener );

        List<Plot> plots = new ArrayList<Plot>(Arrays.asList(application.getPlots()));
        Plot focus = getPlot();

        if (focus != null) {
            int idx = plots.indexOf(focus);
            if (direction == LayoutConstants.BELOW) {
                idx = idx + 1;
            }
            plots.add(idx, domPlot);
        } else {
            plots.add(domPlot);
        }

        application.setPlots(plots.toArray(new Plot[plots.size()]));

        domPlot.addPropertyChangeListener(application.childListener);
        setPlot(domPlot);

        if (plots.size() == 1) {
            bind(application, Application.PROP_TIMERANGE, domPlot.getXaxis(), Axis.PROP_RANGE);
        }

        bind(application.getOptions(), Options.PROP_DRAWGRID, plot, "drawGrid");
        bind(application.getOptions(), Options.PROP_DRAWMINORGRID, plot, "drawMinorGrid");
        bind(application.getOptions(), Options.PROP_OVERRENDERING, plot, "overSize");

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
    protected Plot copyPlotAndPanels(Plot domPlot, DataSourceFilter dsf, boolean bindx, boolean bindy) {
        List<Panel> p = getPanelsFor(domPlot);

        MutatorLock lock = mutatorLock();
        lock.lock();

        Plot newPlot = copyPlot(domPlot, bindx, bindy, false);
        if (p.size() == 0) {
            return newPlot;
        }

        for (Panel srcPanel : p) {
            Panel newp = copyPanel(srcPanel, newPlot, dsf);
        }

        lock.unlock();

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
        if (dsf == null) { // new DataSource, but with the same URI.
            DataSourceFilter dsfnew = newp.getController().getDataSourceFilter();
            DataSourceFilter dsfsrc = srcPanel.getController().getDataSourceFilter();
            dsfnew.getController().setRawProperties(dsfsrc.getController().getRawProperties());
            dsfnew.getController()._setProperties(dsfsrc.getController().getProperties());
            dsfnew.getController().setDataSetInternal(dsfsrc.getController().getDataSet());
            if (dsfsrc.getUri() != null) {
                dsfnew.setUri(dsfsrc.getUri());
            }
        }
        return newp;
    }

    /**
     * Copy the plot and its axis settings, optionally binding the axes. Whether
     * the axes are bound or not, the duplicate plot is initially synchronized to
     * the source plot.
     *
     * @param srcPlot The plot to be copied
     * @param bindx If true, X axes are bound.  If the srcPlot x axis is bound to the
     *    application timerange, then bind to that instead (kludge--handle higher)
     * @param bindy If true, Y axes are bound
     * @param addPanel add a panel attached to the new plot as well.
     * @return The duplicate plot
     */
    public Plot copyPlot(Plot srcPlot, boolean bindx, boolean bindy, boolean addPanel) {
        Plot that = addPlot(LayoutConstants.BELOW);
        if (addPanel) {
            addPanel(that, null);
        }
        that.syncTo(srcPlot);

        if (bindx) {
            BindingModel bb = findBinding(application, Application.PROP_TIMERANGE, srcPlot, "xaxis." + Axis.PROP_RANGE);
            if (bb == null) {
                bind(srcPlot, "xaxis." + Axis.PROP_RANGE, that, "xaxis." + Axis.PROP_RANGE);
            } else {
                bind(application, Application.PROP_TIMERANGE, that, "xaxis." + Axis.PROP_RANGE);
            }

        }

        if (bindy) {
            bind(srcPlot, "yaxis." + Axis.PROP_RANGE, that, "yaxis." + Axis.PROP_RANGE);
        }

        return that;
    }

    /**
     * delete the plot from the application.
     * @param domPlot
     */
    public void deletePlot(Plot domPlot) {

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

        domPlot.removePropertyChangeListener(application.childListener);
        unbind(domPlot);
        unbind(domPlot.getXaxis());
        unbind(domPlot.getYaxis());
        unbind(domPlot.getZaxis());

        DasPlot p = domPlot.getController().getDasPlot();
        this.getDasCanvas().remove(p);
        DasColorBar cb = domPlot.getController().getDasColorBar();
        this.getDasCanvas().remove(cb);

        synchronized (this) {
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

    }

    public synchronized void deleteDataSourceFilter(DataSourceFilter dsf) {
        if (!application.dataSourceFilters.contains(dsf)) {
            throw new IllegalArgumentException("dsf is not in this application");
        }
        if (application.dataSourceFilters.size() < 2) {
            throw new IllegalArgumentException("last plot cannot be deleted");
        }
        List<Panel> panels = this.getPanelsFor(dsf);
        if (panels.size() > 0) {
            throw new IllegalArgumentException("dsf must not have panels before deleting");
        }

        dsf.removePropertyChangeListener(application.childListener);
        unbind(dsf);
        dsf.getController().unbind();

        DataSourceFilter[] parents = dsf.getController().getParentSources();
        // look for orphaned parents
        List<DataSourceFilter> alsoRemove = new ArrayList<DataSourceFilter>();
        for (DataSourceFilter pdf : parents) {
            String plotId = pdf.getId();
            List<DomNode> usages = DomUtil.getDataSourceUsages(application, plotId);
            usages.remove(dsf);
            if (usages.size() == 0) {
                alsoRemove.add(pdf);
            }
        }

        List<DataSourceFilter> dsfs = new ArrayList<DataSourceFilter>(Arrays.asList(application.getDataSourceFilters()));
        dsfs.remove(dsf);
        dsfs.removeAll(alsoRemove);

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
     * BeansBinding library is appearently not thread-safe.
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
                bindingContexts.put(src, bc);
            }
        }

        Binding b;

        b = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, src, BeanProperty.create(srcProp), dst, BeanProperty.create(dstProp));
        bc.addBinding(b);

        String srcId = src.getId();

        String dstId = "???";
        if (dst instanceof DomNode) {
            dstId = ((DomNode) dst).getId();
        }
        if (dst instanceof DasCanvasComponent) {
            dstId = "das2:" + ((DasCanvasComponent) dst).getDasName();
        }

        BindingModel bb = new BindingModel(srcId, srcId, srcProp, dstId, dstProp);

        if (!dstId.equals("???") && !dstId.startsWith("das2:")) {
            List<BindingModel> bindings = new ArrayList<BindingModel>(Arrays.asList(application.getBindings()));
            bindings.add(bb);
            application.setBindings(bindings.toArray(new BindingModel[bindings.size()]));
        }

        b.bind();

        this.bindingImpls.put(bb, b);
    }

    /**
     * unbindDsf the object.  For example, when the object is about to be deleted.
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
                List<BindingModel> remove = new ArrayList<BindingModel>();
                for (BindingModel bb : bindings) { // avoid concurrent modification
                    if (bb.getBindingContextId().equals(bcid)) {
                        remove.add(bb);
                    }
                }
                bindings.removeAll(remove);
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
        application.setBindings(bindings.toArray(new BindingModel[bindings.size()]));

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

    /**
     * Some sort of processing is going on, so wait until idle.
     * @return
     */
    public boolean isPendingChanges() {
        boolean result = false;
        if (changesSupport.isPendingChanges()) {
            return true;
        }
        for (Canvas c : application.getCanvases()) {
            result = result | c.getController().isPendingChanges();
        }
        for (Panel p : application.getPanels()) {
            result = result | p.getController().isPendingChanges();
        }
        for (DataSourceFilter dsf : application.getDataSourceFilters()) {
            result = result | dsf.getController().isPendingChanges();
        }
        return result;
    }

    /**
     * the application state is rapidly changing.
     * @return
     */
    public boolean isValueAdjusting() {
        return changesSupport.isValueAdjusting();
    }

    protected MutatorLock mutatorLock() {
        return changesSupport.mutatorLock();
    }

    public void registerPendingChange(Object client, Object lockObject) {
        changesSupport.registerPendingChange(client, lockObject);
    }

    public void performingChange(Object client, Object lockObject) {
        changesSupport.performingChange(client, lockObject);
    }

    public void changePerformed(Object client, Object lockObject) {
        changesSupport.changePerformed(client, lockObject);
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

    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(propertyName, listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    /**
     * return a plot that is immediately above the given plot.  This
     * encapsulates the layout model, and implementation should change.
     * @param p
     * @return
     */
    public Plot getPlotAbove(Plot p) {
        int n = application.getPlots().length;
        for (int i = 0; i < n - 1; i++) {
            if (application.getPlots(i + 1) == p) {
                return application.getPlots(i);
            }
        }
        return null;
    }

    /**
     * return a plot that is immediately below the given plot.  This
     * encapsulates the layout model, and implementation should change.
     * @param p
     * @return
     */
    public Plot getPlotBelow(Plot p) {
        int n = application.getPlots().length;
        for (int i = 1; i < n; i++) {
            if (application.getPlots(i - 1) == p) {
                return application.getPlots(i);
            }
        }
        return null;
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

    public List<Panel> getPanelsFor(Plot plot) {
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
     * return the DataSourceFilter for the panel, or null if none exists.
     * @param panel
     * @return
     */
    public DataSourceFilter getDataSourceFilterFor(Panel panel) {
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

    protected synchronized void syncTo(Application that) {
        MutatorLock lock = changesSupport.mutatorLock();
        lock.lock();

        canvasLock = canvas.getController().getDasCanvas().mutatorLock();
        canvasLock.lock();

        application.getOptions().syncTo(that.getOptions(),
                Arrays.asList(Options.PROP_OVERRENDERING,
                Options.PROP_LOGCONSOLEVISIBLE,
                Options.PROP_SCRIPTVISIBLE,
                Options.PROP_SERVERENABLED));

        syncSupport.syncToPlotsAndPanels(that.getPlots(), that.getPanels(), that.getDataSourceFilters());

        syncSupport.syncBindings(that.getBindings());
        syncSupport.syncConnectors(that.getConnectors());

        application.setTimeRange(that.getTimeRange());

        canvasLock.unlock();

        lock.unlock();
        for (Panel p : application.getPanels()) {  // kludge to avoid reset range
            p.getController().setResetRanges(false);
            p.getController().setResetRenderer(true);
        }
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
