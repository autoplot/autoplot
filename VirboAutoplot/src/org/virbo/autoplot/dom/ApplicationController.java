/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.awt.AWTEventMulticaster;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import org.das2.DasApplication;
import org.das2.event.MouseModule;
import org.das2.graph.ColumnColumnConnector;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasCanvasComponent;
import org.das2.graph.DasColorBar;
import org.das2.graph.DasColumn;
import org.das2.graph.DasPlot;
import org.das2.graph.DasRow;
import org.das2.graph.Renderer;
import org.das2.system.MonitorFactory;
import org.das2.util.monitor.ProgressMonitor;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.jdesktop.beansbinding.Converter;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.autoplot.ColumnColumnConnectorMouseModule;
import org.virbo.autoplot.LayoutListener;
import org.virbo.autoplot.dom.ChangesSupport.DomLock;
import org.virbo.autoplot.layout.LayoutConstants;
import org.virbo.autoplot.util.RunLaterListener;

/**
 *
 * @author jbf
 */
public class ApplicationController extends DomNodeController implements RunLaterListener.PropertyChange {

    Application application;
    ApplicationModel model;
    DasRow outerRow;
    DasColumn outerColumn;
    LayoutListener layoutListener;
    boolean headless;
    final Map<Object, BindingGroup> bindingContexts;
    //final Map<Object, BindingGroup> implBindingContexts; // these are for controllers to use.
    protected BindingSupport bindingSupport= new BindingSupport();

    final Map<BindingModel, Binding> bindingImpls;
    final Map<Connector, ColumnColumnConnector> connectorImpls;
    private final static Logger logger = Logger.getLogger("virbo.controller");

    private static AtomicInteger canvasIdNum = new AtomicInteger(0);
    private static AtomicInteger plotIdNum = new AtomicInteger(0);
    private static AtomicInteger plotElementIdNum = new AtomicInteger(0);
    private static AtomicInteger dsfIdNum = new AtomicInteger(0);
    private static AtomicInteger rowIdNum = new AtomicInteger(0);
    private static AtomicInteger columnIdNum = new AtomicInteger(0);
    private static AtomicInteger appIdNum= new AtomicInteger(0);

    ApplicationControllerSyncSupport syncSupport;
    ApplicationControllerSupport support;
    
    ActionListener eventListener;

    public ApplicationController(ApplicationModel model, Application application) {
        super( application );

        // kludge to trigger undo/redo support.
        changesSupport.addPropertyChangeListener( new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ( evt.getPropertyName().equals("status")
                        && "ready".equals(evt.getNewValue() ) ) {
                    fireActionEvent( new ActionEvent(this,0,"ready") );
                }
                if ( evt.getPropertyName().equals(ChangesSupport.PROP_VALUEADJUSTING) && evt.getNewValue()==null ) { //put in state after atomtic operation
                    String description= (String) evt.getOldValue();
                    if ( description.length()>0 ) {
                        fireActionEvent( new ActionEvent(this,0,"label: "+description ) );
                    } else {
                        fireActionEvent( new ActionEvent(this,0,"ready") );
                    }
                }
            }
        });

        this.application = application;
        this.syncSupport = new ApplicationControllerSyncSupport(this);
        this.support = new ApplicationControllerSupport(this);

        int i= appIdNum.getAndIncrement();
        application.setId("app_"+i);
        application.getOptions().setId("options_"+i);

        application.addPropertyChangeListener(domListener);
        for ( DomNode n: application.childNodes() ) {
            n.addPropertyChangeListener(domListener);
        }

        this.model = model;
        application.controller = this;
        bindingContexts = new HashMap();
        //implBindingContexts= new HashMap();
        bindingImpls = new HashMap();
        connectorImpls = new HashMap();

        addListeners();
    }

    public void addActionListener(ActionListener list) {
        eventListener = AWTEventMulticaster.add(eventListener, list);
    }

    public Application getApplication() {
        return application;
    }

    public ApplicationModel getApplicationModel() {
        return model;
    }

    public void removeActionListener(ActionListener list) {
        AWTEventMulticaster.remove(eventListener, list);
    }

    PropertyChangeSupport das2PeerListenerSupport= new DebugPropertyChangeSupport(this);

    /**
     * kludgy way to decouple the context menus from the DOM tree.
     * @param listener
     */
    public synchronized void addDas2PeerChangeListener(PropertyChangeListener listener) {
        das2PeerListenerSupport.addPropertyChangeListener(listener);
        if ( application.getPlots(0).getController()!=null ) {
            das2PeerListenerSupport.firePropertyChange( "das2peer", null, application.getPlots(0).getController() );
        }
    }


    /**
     * this is decouple the context menus from the DOM.  We'll keep a list of
     * interested parties and let them add context menus.
     * @param aThis
     */
    void maybeAddContextMenus(PlotController aThis) {
        das2PeerListenerSupport.firePropertyChange( "das2peer", null, aThis );
    }

    private void fireActionEvent(ActionEvent e) {
        ActionListener listener = eventListener;
        if (listener != null) {
            listener.actionPerformed(e);
        }
    }
    AtomicInteger eventId = new AtomicInteger();

    PropertyChangeListener controllerListener= new PropertyChangeListener() {

        public void propertyChange(PropertyChangeEvent evt) {
            
            // only go into logger stuff if we know it's going to log.  This is for performance, I noticed a large number of Object instances when profiling and this could help performance.
            if ( logger.isLoggable(Level.FINEST) ) logger.log(Level.FINEST, "controller change: {0}.{1} ({2}->{3})", new Object[]{evt.getSource(), evt.getPropertyName(), evt.getOldValue(), evt.getNewValue()});
            //if( evt.getPropertyName().equals("resetDimensions") && evt.getNewValue().equals(Boolean.TRUE) ) {
            //    System.err.println("here here here");
            //}
        }

    };

    PropertyChangeListener domListener = new PropertyChangeListener() {

        public void propertyChange(PropertyChangeEvent evt) {

            // only go into logger stuff if we know it's going to log.  This is for performance, I noticed a large number of Object instances when profiling and this could help performance.
            if ( logger.isLoggable(Level.FINEST) ) logger.log(Level.FINEST, "dom change: {0}.{1} ({2}->{3})", new Object[]{evt.getSource(), evt.getPropertyName(), evt.getOldValue(), evt.getNewValue()});

            Object src = evt.getSource();
            if ( src instanceof DomNode ) {
                DomNodeController c= DomNodeController.getController((DomNode)src);
            //if ( c!=null && c.isValueAdjusting()) return;
            }

            if ( !isValueAdjusting() ) { // undo/redo support notification
                fireActionEvent(new ActionEvent(evt.getSource(), eventId.incrementAndGet(), evt.getPropertyName()));
            }

            DomNodeController c;
            Object oldValue = evt.getOldValue();
            if (oldValue != null) {
                if (oldValue instanceof DomNode) {
                    final DomNode d = (DomNode) oldValue;
                    d.removePropertyChangeListener(domListener);
                    c= DomNodeController.getController(d);
                    if ( c!=null ) c.removePropertyChangeListener(controllerListener);
                    for ( DomNode k: d.childNodes() ) {
                        k.removePropertyChangeListener(domListener);
                        c= DomNodeController.getController(k);
                        if ( c!=null ) c.removePropertyChangeListener(controllerListener);
                    }
                } else if (oldValue.getClass().isArray() && DomNode.class.isAssignableFrom(oldValue.getClass().getComponentType())) {
                    for (int i = 0; i < Array.getLength(oldValue); i++) {
                        final DomNode d= ((DomNode) Array.get(oldValue, i));
                        d.removePropertyChangeListener(domListener);
                        c= DomNodeController.getController(d);
                        if ( c!=null ) c.removePropertyChangeListener(controllerListener);
                        for ( DomNode k: d.childNodes() ) {
                            k.removePropertyChangeListener(domListener);
                            c= DomNodeController.getController(k);
                            if ( c!=null ) c.removePropertyChangeListener(controllerListener);
                        }
                    }
                }
            }

            Object newValue = evt.getNewValue();
            if (newValue != null) {
                if (newValue instanceof DomNode) {
                    final DomNode d = (DomNode) newValue;
                    d.addPropertyChangeListener(domListener);
                    c= DomNodeController.getController(d);
                    if ( c!=null ) c.addPropertyChangeListener(controllerListener);
                    for ( DomNode k: d.childNodes() ) {
                        k.addPropertyChangeListener(domListener);
                        c= DomNodeController.getController(k);
                        if ( c!=null ) c.addPropertyChangeListener(controllerListener);
                    }
                } else if (newValue.getClass().isArray() && DomNode.class.isAssignableFrom(newValue.getClass().getComponentType())) {
                    for (int i = 0; i < Array.getLength(newValue); i++) {
                        final DomNode d= ((DomNode) Array.get(newValue, i));
                        d.addPropertyChangeListener(domListener);
                        c= DomNodeController.getController(d);
                        if ( c!=null ) c.addPropertyChangeListener(controllerListener);
                        for ( DomNode k: d.childNodes() ) {
                            k.addPropertyChangeListener(domListener);
                            c= DomNodeController.getController(k);
                            if ( c!=null ) c.removePropertyChangeListener(controllerListener);
                        }
                    }
                }
            }
        }
    };

    /**
     * returns the URI for the plotElement, or "".
     * @param p
     * @return
     */
    private static String getFocusUriFor( PlotElement p ) {
        DataSourceFilter dsf= p.controller.getDataSourceFilter();
        if ( dsf!=null ) {
                     return dsf.getUri();
        } else {
                    return "";
        }
    }
    
    FocusAdapter focusAdapter = new FocusAdapter() {

        @Override
        public void focusGained(FocusEvent e) {
            super.focusGained(e);

            if ( e.getComponent() instanceof ColumnColumnConnector ) {
                //System.err.println( "focus on column column connector");

            }
            Plot domPlot = getPlotFor(e.getComponent());
            if (domPlot == null) {
                return;
            }

            DasPlot dasPlot = domPlot.controller.getDasPlot();

            PlotElement p = null;

            Renderer r = dasPlot.getFocusRenderer();

            if (r != null) {
                p = findPlotElement(r);
                setPlotElement(p);
            }
            
            // if there's just one plotElement in the plot, then go ahead and set the focus uri.
            List<PlotElement> ps = ApplicationController.this.getPlotElementsFor(domPlot);
            if ( p==null && ps.size() == 1) {
                setFocusUri( getFocusUriFor( ps.get(0) ) );
            }

            setPlot(domPlot);

            if (p != null) {
                logger.log(Level.FINE, "focus due to plot getting focus: {0}", p);
                setFocusUri( getFocusUriFor( p ) );
                setPlotElement(p);
                setStatus("" + domPlot + ", " + p + " selected");
                if ( ApplicationController.this.getApplication().getPlotElements().length>1 ) {  // don't flash single plot.
                    canvas.controller.indicateSelection( Collections.singletonList((DomNode)p) ); // don't flash plot node, just the plot element.
                }
            } else {
                setStatus("" + domPlot + " selected");
            }

        }
    };

    public void fillEditPlotMenu(JMenu editPlotMenu, final Plot domPlot) {
        JMenuItem item;
        item = new JMenuItem(new AbstractAction("Remove Bindings") {

            public void actionPerformed(ActionEvent e) {
                List<BindingModel> bms= new ArrayList<BindingModel>();

                List<PlotElement> peles = getPlotElementsFor(domPlot);
                for (PlotElement pele : peles) {
                    bms.addAll( Arrays.asList( getBindingsFor(pele) ) );
                    unbind(pele);
                }
                bms.addAll( Arrays.asList( getBindingsFor(domPlot) ) );
                unbind(domPlot);
                bms.addAll( Arrays.asList( getBindingsFor(domPlot.xaxis) ) );
                unbind(domPlot.xaxis);
                bms.addAll( Arrays.asList( getBindingsFor(domPlot.yaxis) ) );
                unbind(domPlot.yaxis);
                bms.addAll( Arrays.asList( getBindingsFor(domPlot.zaxis) ) );
                unbind(domPlot.zaxis);
                setStatus("removed "+bms.size()+" bindings");
            }
        });
        item.setToolTipText("remove any plot and plot element property bindings");
        editPlotMenu.add(item);
        item = new JMenuItem(new AbstractAction("Delete Plot") {

            public void actionPerformed(ActionEvent e) {
                if (application.getPlots().length > 1) {
                    List<PlotElement> plotElements = getPlotElementsFor(domPlot);
                    for (PlotElement pele : plotElements) {
                        if (application.getPlotElements().length > 1) {
                            deletePlotElement(pele);
                        } else {
                            setStatus("warning: the last plot element may not be deleted");
                        }
                    }
                    deletePlot(domPlot);
                } else {
                    ArrayList<PlotElement> pes= new ArrayList( getPlotElementsFor(domPlot) );
                    Collections.reverse(pes);
                    for ( PlotElement pe: pes ) {
                        deletePlotElement(pe);
                    }
                    domPlot.syncTo( new Plot(), Arrays.asList( "id", "rowId", "columnId" ) );
                }
            }
        });
        editPlotMenu.add(item);
    }

    public Plot getPlotFor(Component c) {
        Plot plot1 = null;
        for (Plot p : application.getPlots()) {
            DasPlot p1 = p.controller.getDasPlot();
            if ( p1!=null && ( p1 == c || p1.getXAxis() == c || p1.getYAxis() == c ) ) {
                plot1 = p;
                break;
            }
            if (p.controller.getDasColorBar() == c) {
                plot1 = p;
                break;
            }
        }
        return plot1;
    }

    public void doplot(Plot plot, PlotElement pele, String secondaryUri, String teriaryUri, String primaryUri) {
        support.plot(plot, pele, secondaryUri, teriaryUri, primaryUri);
    }

    public void doplot(Plot plot, PlotElement pele, String secondaryUri, String primaryUri) {
        support.plot(plot, pele, secondaryUri, primaryUri);
    }

    public void doplot(Plot plot, PlotElement pele, String primaryUri) {
        support.plot(plot, pele, primaryUri);
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
        DataSourceController dsfc= new DataSourceController(this.model, dsf);
        dsf.controller = dsfc;
        assignId(dsf);
        List<DataSourceFilter> dataSourceFilters = new ArrayList<DataSourceFilter>(Arrays.asList(this.application.getDataSourceFilters()));
        dataSourceFilters.add(dsf);
        this.application.setDataSourceFilters(dataSourceFilters.toArray(new DataSourceFilter[dataSourceFilters.size()]));
        //dsf.addPropertyChangeListener(application.childListener);
        dsf.addPropertyChangeListener(domListener);
        return dsf;
    }

    private void addListeners() {
        this.addPropertyChangeListener(ApplicationController.PROP_PLOT_ELEMENT, new PropertyChangeListener() {

            @Override
            public String toString() {
                return "" + ApplicationController.this;
            }

            public void propertyChange(PropertyChangeEvent evt) {
                if (!isValueAdjusting()) {
                    PlotElement p = getPlotElement();
                    if (p != null) {
                        DataSourceFilter dsf= getDataSourceFilterFor(p);
                        if ( dsf!=null ) setDataSourceFilter(dsf);
                        setPlot(getPlotFor(p));
                    } else {
                        setDataSourceFilter(null);
                    }
                }
            }
        });
        
        // automatically enable layout plotElement when there are multiple plotElements.
        this.application.addPropertyChangeListener( Application.PROP_PLOT_ELEMENTS, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ( application.getPlotElements().length>1 ) {
                    application.options.setLayoutVisible(true);
                }
            }
        });

        this.application.addPropertyChangeListener( Application.PROP_BINDINGS, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                // is anyone listening to timerange?
                boolean noOneListening= true;
                BindingModel[] bms= application.getBindings();
                for ( int i=0; i<bms.length; i++ ) {
                    if ( bms[i].getSrcId().equals( application.getId() ) && bms[i].srcProperty.equals( Application.PROP_TIMERANGE ) ) {
                        noOneListening= false;
                    }
                }
                if ( noOneListening ) {
                    application.setTimeRange( Application.DEFAULT_TIME_RANGE );
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
        Canvas lcanvas = new Canvas();
        DasCanvas dasCanvas = new DasCanvas(lcanvas.getWidth(),lcanvas.getHeight());
        dasCanvas.setScaleFonts(false);

        assignId( lcanvas );

        new CanvasController(application, lcanvas).setDasCanvas(dasCanvas);

        new RowController( lcanvas.getMarginRow() ).createDasPeer( lcanvas, null );
        new ColumnController( lcanvas.getMarginColumn() ).createDasPeer( lcanvas, null );

        layoutListener = new LayoutListener(model);

        application.setCanvases(new Canvas[]{lcanvas});
        setCanvas(lcanvas);

        bindTo(dasCanvas);

        //lcanvas.addPropertyChangeListener(application.childListener);
        lcanvas.addPropertyChangeListener(this.domListener);

        dasCanvas.setPrintingTag("");
        return dasCanvas;
    }


    /**
     * delete the plot element completely, or if it is the last, then empty the
     * data source.
     * Earlier versions of this would throw an exception if the last panel were
     * deleted.
     * @param pelement
     */
    public void deletePlotElement(PlotElement pelement) {
        logger.log(Level.FINE, "deletePlotElement({0})", pelement);
        DomLock lock= changesSupport.mutatorLock();
        lock.lock("Delete Plot Element");
        int currentIdx = application.plotElements.indexOf(pelement);
        if (currentIdx == -1) {
            throw new IllegalArgumentException("deletePlotElement but plot element isn't part of application");
        }
        String id= pelement.getId();
        if (application.plotElements.size() > 1 ) {

            DasPlot p = pelement.controller.getDasPlot();
            if (p != null) {
                Renderer r = pelement.controller.getRenderer();
                if (r != null) {
                    p.removeRenderer(r);
                }
            }

            //plotElement.removePropertyChangeListener(application.childListener);
            pelement.removePropertyChangeListener(domListener);
            pelement.getStyle().removePropertyChangeListener(domListener);
            unbind(pelement);
            unbind(pelement.getStyle());
            unbindImpl(pelement); //TODO: I need to remind myself why there are two types of bindings...
            unbindImpl(pelement.getStyle());
            pelement.controller.unbindDsf();
            pelement.removePropertyChangeListener(plotIdListener);

        }

        DataSourceFilter dsf = getDataSourceFilterFor(pelement);

        ArrayList<PlotElement> elements =
                new ArrayList<PlotElement>(Arrays.asList(application.getPlotElements()));
        elements.remove(pelement);
        if ( elements.size()>0 ) {
            if (!elements.contains(getPlotElement())) {  // reset the focus element Id
                if (elements.size() == 0) {
                    setPlotElement(null);
                } else {
                    setPlotElement(elements.get(0)); // maybe use currentIdx
                }
            }
            application.setPlotElements(elements.toArray(new PlotElement[elements.size()]));
        } else {
            dsf.setUri("");  // this panel and the dsf should go together
            pelement.setLegendLabelAutomatically("");
            pelement.setActive(true);
        }

        if (dsf != null) {
            List<PlotElement> dsfElements = getPlotElementsFor(dsf);
            if ( dsfElements.size() == 0 && application.getDataSourceFilters().length>1 ) {
                deleteDataSourceFilter(dsf);
            }
        }
        for ( PlotElement p: application.plotElements ) {
            if ( p.getParent().equals(id) ) {
                p.setParent("");
            }
        }
        lock.unlock();
    }

    /**
     * adds a context overview plotId below the plotId.
     * @param domPlot
     */
    public void addConnector(Plot domPlot, Plot that) {
        logger.log( Level.FINE, "addConnector({0},{1})", new Object[]{domPlot, that});
        List<Connector> connectors = new ArrayList<Connector>(Arrays.asList(application.getConnectors()));
        final Connector connector = new Connector(domPlot.getId(), that.getId());
        connectors.add(connector);

        application.setConnectors(connectors.toArray(new Connector[connectors.size()]));

        DasCanvas lcanvas = getDasCanvas();
        DasPlot upper = domPlot.controller.getDasPlot();
        DasPlot lower = that.controller.getDasPlot();

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
        logger.log( Level.FINE, "deleteConnector({0})", connector);
        ColumnColumnConnector impl = connectorImpls.get(connector);
        if ( impl!=null ) {
            getDasCanvas().remove(impl);
        } else {
            // JUNO nightly build...test this.
        }

        List<Connector> connectors = DomUtil.asArrayList(application.getConnectors());
        connectors.remove(connector);

        connectorImpls.remove(connector);

        application.setConnectors(connectors.toArray(new Connector[connectors.size()]));
    }

    private void movePlotElement(PlotElement p, Plot src, Plot dst) {
        assert ( src==null || p.getPlotId().equals(src.getId()) || p.getPlotId().equals(dst.getId()));

        if ( src==dst ) return;
        if ( src!=null ) src.getController().removePlotElement( p );
        dst.getController().addPlotElement( p,false );

        p.setPlotId(dst.getId());

    }
    
    PropertyChangeListener plotIdListener = new PropertyChangeListener() {

        @Override
        public String toString() {
            return "" + ApplicationController.this;
        }

        public void propertyChange(PropertyChangeEvent evt) {
            PlotElement p = (PlotElement) evt.getSource();
            String srcid = (String) evt.getOldValue();
            String dstid = (String) evt.getNewValue();
            if ( srcid==null ) srcid=""; //TODO: this can probably be removed
            if ( dstid==null ) dstid="";
            Plot src = srcid.length()==0 ? null : ( Plot) DomUtil.getElementById(application, srcid);
            Plot dst = dstid.length()==0 ? null : (Plot) DomUtil.getElementById(application, dstid);
            if ( src==null ) {
                if ( dst!=null ) {
                    dst.getController().addPlotElement( p,false );
                } else {
                    return; // initialization state
                }
            }
            if ( dst==null ) {
                src.getController().removePlotElement(p);
                return;
            }
            if ( src != null && dst != null ) {
                movePlotElement(p, src, dst);
                //if (getPlotElementsFor(src).size() == 0) {
                //    deletePlot(src);
                //}
                if (getPlotElementsFor(dst).size() == 1) {
                    dst.syncTo(p.plotDefaults, Arrays.asList( DomNode.PROP_ID, Plot.PROP_ROWID, Plot.PROP_COLUMNID ) );
                }
            }
        }
    };


    /**
     * add a plotElement to the application, attaching it to the given Plot and DataSourceFilter.
     * @param domPlot if null, create a Plot, if non-null, add the plotElement to this plot.
     * @param dsf if null, create a DataSourceFilter.  If non-null, connect the plotElement to this data source.
     * @return
     */
    public PlotElement addPlotElement(Plot domPlot, DataSourceFilter dsf) {
        logger.log(Level.FINE, "enter addPlotElement({0},{1})", new Object[]{domPlot, dsf});

        final PlotElement pele1 = new PlotElement();

        if (dsf == null) {
            dsf = addDataSourceFilter();
        }

        PlotElementController pec= new PlotElementController(this.model, application, pele1);
        pele1.controller = pec;

        if (domPlot == null) {
            domPlot = addPlot(LayoutConstants.BELOW);
        }

        assignId(pele1);

        pele1.getStyle().setColor(application.getOptions().getColor());
        pele1.getStyle().setFillColor(application.getOptions().getFillColor());
        pele1.getStyle().setAntiAliased(application.getOptions().isDrawAntiAlias());

        pele1.addPropertyChangeListener(PlotElement.PROP_PLOTID, plotIdListener);

        pele1.setPlotId(domPlot.getId());
        pele1.setDataSourceFilterId(dsf.getId());

        pele1.setAutoLabel(true);

        synchronized (this) {
            PlotElement[] p = application.getPlotElements();
            PlotElement[] temp = new PlotElement[p.length + 1];
            System.arraycopy(p, 0, temp, 0, p.length);
            temp[p.length] = pele1;
            this.application.setPlotElements(temp);
            //pele1.addPropertyChangeListener(application.childListener);
            pele1.addPropertyChangeListener(domListener);
            pele1.getStyle().addPropertyChangeListener(domListener);
            if ( plotElement==null ) setPlotElement(pele1);
        }

        if ( domPlot.getController()!=null ) {
            domPlot.controller.addPlotElement(pele1);
        }
        
        return pele1;
    }

    PropertyChangeListener rendererFocusListener = new PropertyChangeListener() {

        @Override
        public String toString() {
            return "" + ApplicationController.this;
        }

        public void propertyChange(PropertyChangeEvent evt) {
            DasPlot dasPlot = (DasPlot) evt.getSource();
            Renderer r = dasPlot.getFocusRenderer();
            if (r == null) {
                return;
            }
            try {
                PlotElement p = findPlotElement(r);
                if (getPlotElement() != p) {
                    setPlotElement(p);
                }
            } catch ( IllegalArgumentException ex ) {
                System.err.println("unable to find the plot element, assuming transitional state...");
            }

        }
    };

    /**
     * add a plot to the canvas.  Direction is with respect to the current
     * focus plot, and currently only LayoutConstants.ABOVE and LayoutConstants.BELOW
     * are supported.
     * 
     * @param direction LayoutConstants.ABOVE, LayoutConstants.BELOW, or null.  
     * Null indicates the layout will be done elsewhere, and the new plot will
     * be on top of the old.
     * @return
     */
    public synchronized Plot addPlot(Object direction) {
        Plot focus = getPlot();
        return addPlot( focus, direction );
    }

    public synchronized Plot addPlot( final Plot focus, Object direction ) {

        if ( !SwingUtilities.isEventDispatchThread() ) {
            System.err.println("SF Bug 3516412: addPlot is called off the event thread!!!");
        }

        logger.fine("enter addPlot");
        DomLock lock= changesSupport.mutatorLock();
        lock.lock("Add Plot");
        final Plot domPlot = new Plot();

        CanvasController ccontroller=  ((CanvasController)canvas.controller);
        Row domRow;
        
        if ( focus!=null && ccontroller.getRowFor(focus)==canvas.marginRow ) { 
            String srcRow;
            if ( canvas.getRows().length>0 ) {
                srcRow= canvas.getRows(0).getId();
            } else {
                ccontroller.addRows(2);
                srcRow= canvas.getRows(0).getId();
            }
            if ( canvas.getRows().length>1 ) {
                domRow= canvas.getRows(1);
            } else {
                domRow = ccontroller.addInsertRow( ccontroller.getRowFor(focus), direction );
            }
            focus.setRowId(srcRow);
        } else if ( canvas.getRows().length == 0 && ( direction==LayoutConstants.BELOW || direction==LayoutConstants.ABOVE ) ) {
            domRow = ccontroller.addRow();
        } else if ( direction==null || direction==LayoutConstants.LEFT || direction==LayoutConstants.RIGHT ) {
            domRow= ccontroller.getRowFor(focus);
        } else {
            domRow = ccontroller.addInsertRow( ccontroller.getRowFor(focus), direction);
        }

        Column domColumn= canvas.getMarginColumn();

        // the logic for columns is different because we optimize the application for a stack of time
        // series.
        if ( direction==null || direction==LayoutConstants.ABOVE || direction==LayoutConstants.BELOW ) {
            domColumn= canvas.marginColumn;
        } else {
            if ( ccontroller.getColumnFor(focus)==canvas.marginColumn ) {
                String srcColumn;
                if ( canvas.getColumns().length>0 ) {
                    srcColumn= canvas.getColumns(0).getId();
                } else {
                    ccontroller.addColumns(2);
                    srcColumn= canvas.getColumns(0).getId();
                }
                if ( canvas.getColumns().length>1 ) {
                    domColumn= canvas.getColumns(1);
                } else {
                    domColumn = ccontroller.addInsertColumn( ccontroller.getColumnFor(focus), direction );
                }
                focus.setColumnId(srcColumn);
            } else {
                domColumn = ccontroller.addInsertColumn( ccontroller.getColumnFor(focus), direction);
            }
        }

        assignId( domPlot );

        final Column fcol= domColumn;
        final Row frow= domRow;

        Runnable run= new Runnable() { public void run() {
            new PlotController(application, domPlot).createDasPeer(canvas, frow ,fcol );
        } };
        if ( SwingUtilities.isEventDispatchThread() ) {
            run.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(run);
            } catch ( Exception ex ) {
                ex.printStackTrace();
            }
        }

        domPlot.getXaxis().setAutoRange(true);
        domPlot.getYaxis().setAutoRange(true);
        domPlot.getZaxis().setAutoRange(true);
        domPlot.getXaxis().setAutoLabel(true);
        domPlot.getYaxis().setAutoLabel(true);
        domPlot.getZaxis().setAutoLabel(true);
        domPlot.setAutoLabel(true);
        domPlot.setAutoBinding(true);

        domPlot.getZaxis().setVisible(false);
        
        domPlot.setRowId( domRow.getId() );
        domPlot.setColumnId( domColumn.getId() );

        List<Plot> plots = new ArrayList<Plot>(Arrays.asList(application.getPlots()));

        if (focus != null) {
            int idx = plots.indexOf(focus);
            if ( direction==null || direction == LayoutConstants.BELOW) {
                idx = idx + 1;
            }
            plots.add(idx, domPlot);
        } else {
            plots.add(domPlot);
        }

        application.setPlots(plots.toArray(new Plot[plots.size()]));
        if ( getPlot()==null ) setPlot(domPlot);

        //domPlot.addPropertyChangeListener(application.childListener);
        domPlot.addPropertyChangeListener(domListener);

        lock.unlock();
        return domPlot;
    }

    public Plot addPlot(Row get, Column get0) {
        Plot p= addPlot(null);
        p.setRowId( get.getId() );
        p.setColumnId( get0.getId() );
        return p;
    }

    /**
     * adds a block of plots to the canvas below the focus plot.  A plotElement
     * is added for each plot as well.
     * @param nrow
     * @param ncol
     * @param dir LayoutConstants.ABOVE or LayoutConstants.BELOW or null.  Null means use the current row.
     * @return a list of the newly added plots.
     */
    public List<Plot> addPlots( int nrow, int ncol, Object dir ) {
        DomLock lock = mutatorLock();
        lock.lock( String.format("addPlots(%d,%d,%s)",nrow,ncol,dir) );
        try {
            List<Plot> result= new ArrayList<Plot>(nrow*ncol);
            List<Column> cols;
            final CanvasController ccontroller = getCanvas().getController();
            if (ncol > 1) {
                cols = ccontroller.addColumns(ncol);
            } else {
                cols = Collections.singletonList(getCanvas().getMarginColumn());
            }
            List<Row> rows;
            if ( dir==null && nrow==1 ) {
                rows = Collections.singletonList(ccontroller.getRowFor(plot));
            } else {
                rows = ccontroller.addRows(nrow,dir);
            }
            for (int i = 0; i < nrow; i++) {
                for (int j = 0; j < ncol; j++) {
                    Plot p = addPlot(rows.get(i), cols.get(j));
                    result.add(p);
                    addPlotElement(p, null);
                }
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * find the plotElement using this renderer.
     * @param rend
     * @throw IllegalArgumentException if the plot element cannot be found.
     * @return 
     */
    protected PlotElement findPlotElement(Renderer rend) {
        for (PlotElement p : application.getPlotElements()) {
            PlotElementController pc = p.controller;
            if (pc.getRenderer() == rend) {
                return p;
            }
        }
        throw new IllegalArgumentException("unable to find plot element for das renderer");
    }

    /**
     * find the plot using this renderer.
     * @param rend
     * @return
     */
    private Plot findPlot(DasPlot plot) {
        for (Plot p : application.getPlots()) {
            PlotController pc = p.controller;
            if (pc.getDasPlot() == plot ) {
                return p;
            }
        }
        throw new IllegalArgumentException("unable to find plot for das plot");
    }

    /**
     * @deprecated use copyPlotAndPlotElements instead
     */
    public Plot copyPlotAndPanels( Plot domPlot, DataSourceFilter dsf, boolean bindx, boolean bindy) {
        return copyPlotAndPlotElements( domPlot, dsf, bindx, bindy );
    }

    /**
     * copy doplot and plotElements into a new doplot.
     * @param domPlot
     * @param dsf
     * @return
     */
    public Plot copyPlotAndPlotElements(Plot domPlot, DataSourceFilter dsf, boolean bindx, boolean bindy) {
        List<PlotElement> srcElements = getPlotElementsFor(domPlot);
        List<PlotElement> newElements;
        Plot newPlot;
        DomLock lock = mutatorLock();
        lock.lock("Copy Plot and Plot Elements");
        try {

            newPlot= copyPlot(domPlot, bindx, bindy, false);
            if (srcElements.size() == 0) {
                return newPlot;
            }

            newElements = new ArrayList<PlotElement>();
            for (PlotElement srcElement : srcElements) {
                if (!srcElement.getComponent().equals("")) {
                    if ( srcElement.getController().getParentPlotElement()==null ) {
                        PlotElement newp = copyPlotElement(srcElement, newPlot, dsf);
                        newElements.add(newp);
                    }
                } else {
                    PlotElement newp = copyPlotElement(srcElement, newPlot, dsf);
                    newElements.add(newp);
                    List<PlotElement> srcKids = srcElement.controller.getChildPlotElements();
                    List<PlotElement> newKids = new ArrayList();
                    DataSourceFilter dsf1 = getDataSourceFilterFor(newp);
                    for (PlotElement k : srcKids) {
                        if (srcElements.contains(k)) {
                            PlotElement kidp = copyPlotElement(k, newPlot, dsf1);
                            kidp.getController().setParentPlotElement(newp);
                            newElements.add(kidp);
                            newKids.add(kidp);
                        }
                    }
                }
            }

        } finally {
            lock.unlock();
        }

        for (PlotElement newp : newElements) {
            newp.getController().setResetRanges(false);
            newp.getController().setResetComponent(false);
            newp.getController().setResetPlotElement(false);
            newp.getController().setResetRenderType(false);
            newp.getController().setDsfReset(true);
        }

        return newPlot;

    }

    /**
     * @deprecated use copyPlotElement
     * @param srcElement
     * @param domPlot
     * @param dsf
     * @return
     */
    protected PlotElement copyPanel(PlotElement srcElement, Plot domPlot, DataSourceFilter dsf) {
        return copyPlotElement( srcElement, domPlot, dsf );
    }


    /**
     * copy the plotElement and put it in domPlot.
     * @param srcElement the plotElement to copy.
     * @param domPlot  plot to contain the new plotElement, which may be the same plot.
     * @param dsf     if non-null, then use this dataSourceFilter
     * @return
     */
    protected PlotElement copyPlotElement(PlotElement srcElement, Plot domPlot, DataSourceFilter dsf) {
        logger.log( Level.FINER, "copyPlotElement({0},{1},{2})", new Object[]{srcElement, domPlot, dsf});
        PlotElement newp = addPlotElement(domPlot, dsf);
        newp.getController().setResetPlotElement(false);// don't add children, trigger autoRange, etc.
        newp.getController().setDsfReset(false); // dont' reset when the dataset changes
        newp.syncTo(srcElement, Arrays.asList(DomNode.PROP_ID,PlotElement.PROP_PLOTID,
                PlotElement.PROP_DATASOURCEFILTERID));
        if (dsf == null) { // new DataSource, but with the same URI.
            DataSourceFilter dsfnew = newp.controller.getDataSourceFilter();
            DataSourceFilter dsfsrc = srcElement.controller.getDataSourceFilter();
            copyDataSourceFilter(dsfsrc, dsfnew);
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
     * @param addPlotElement add a plotElement attached to the new plot as well.
     * @return The duplicate plot
     */
    public Plot copyPlot(Plot srcPlot, boolean bindx, boolean bindy, boolean addPlotElement) {
        Plot that = addPlot(LayoutConstants.BELOW);
        that.setAutoBinding(false);
        that.getController().setAutoBinding(false);
        if (addPlotElement) {
            addPlotElement(that, null);
        }
        that.syncTo( srcPlot,Arrays.asList( DomNode.PROP_ID, Plot.PROP_ROWID, Plot.PROP_COLUMNID ) );

        if (bindx) {
            BindingModel bb = findBinding(application, Application.PROP_TIMERANGE, srcPlot.getXaxis(), Axis.PROP_RANGE);
            if (bb == null) {
                bind(srcPlot.getXaxis(), Axis.PROP_RANGE, that.getXaxis(), Axis.PROP_RANGE);
            } else {
                bind(application, Application.PROP_TIMERANGE, that.getXaxis(), Axis.PROP_RANGE);
            }

        }

        if (bindy) {
            bind(srcPlot.getYaxis(), Axis.PROP_RANGE, that.getYaxis(), Axis.PROP_RANGE);
        }

        return that;
    }

    /**
     * copy the dataSourceFilter, including its controller and loaded data.
     * @param dsf
     */
    protected void copyDataSourceFilter(DataSourceFilter dsfsrc, DataSourceFilter dsfnew) {
        DomLock lock= dsfnew.getController().mutatorLock();
        lock.lock("Copy Data Source Filter");
        try {
            final boolean dataSetNeedsLoading = dsfsrc.controller.isDataSetNeedsLoading();

            dsfnew.controller.setResetDimensions(false);
            if ( dsfsrc.getUri().length()>0  ) {
                dsfnew.setUri(dsfsrc.getUri());
            }

            if ( !dataSetNeedsLoading ) {
                dsfnew.controller.setUriNeedsResolution( false );
                dsfnew.controller.resetDataSource(false,dsfsrc.controller.getDataSource());
                dsfnew.controller.setDataSetNeedsLoading( false );
                dsfnew.controller.setResetDimensions(false);
                dsfnew.controller.setDataSetInternal(dsfsrc.controller.getDataSet(),dsfsrc.controller.getRawProperties(),isValueAdjusting()); // fire off data event.
                dsfnew.controller.setProperties(dsfsrc.controller.getProperties());
                dsfnew.setFilters( dsfsrc.getFilters() );
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * delete the plot from the application.
     * @param domPlot
     */
    public void deletePlot(Plot domPlot) {
        DomLock lock= changesSupport.mutatorLock();
        lock.lock("Delete Plot");
        if (!application.plots.contains(domPlot)) {
            throw new IllegalArgumentException("plot is not in this application");
        }
        if (application.plots.size() < 2) {
            throw new IllegalArgumentException("last plot cannot be deleted");
        }
        List<PlotElement> elements = this.getPlotElementsFor(domPlot);
        if (elements.size() > 0) { // transitional state
            for ( PlotElement p: elements ) {
                if ( application.plotElements.size()>1 ) deletePlotElement(p);
            }
        }
        for (Connector c : DomUtil.asArrayList(application.getConnectors())) {
            if (c.getPlotA().equals(domPlot.getId()) || c.getPlotB().equals(domPlot.getId())) {
                deleteConnector(c);
            }
        }

        Row deleteRow = null; // if non-null, delete this row.
        Row row = (Row) DomUtil.getElementById(application, domPlot.getRowId());
        List<DomNode> plotsUsingRow = DomUtil.rowUsages(application, row.getId());
        plotsUsingRow.remove(domPlot);
        if (plotsUsingRow.size() == 0) {
            deleteRow = row;
        }

        //domPlot.removePropertyChangeListener(application.childListener);
        domPlot.removePropertyChangeListener(domListener);
        unbind(domPlot);
        unbind(domPlot.getXaxis());
        unbind(domPlot.getYaxis());
        unbind(domPlot.getZaxis());

        final DasPlot p = domPlot.controller.getDasPlot();
        final DasColorBar cb = domPlot.controller.getDasColorBar();
        final DasCanvas lcanvas= this.getDasCanvas();
        SwingUtilities.invokeLater( new Runnable() { // see https://sourceforge.net/tracker/index.php?func=detail&aid=3471016&group_id=199733&atid=970682
            public void run() {
                lcanvas.remove(p);
                lcanvas.remove(cb);
            }
        } );

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

            if (deleteRow != null) {
                CanvasController cc = row.controller.getCanvas().controller;
                cc.deleteRow(deleteRow);
                cc.removeGaps();
            }
        }
        lock.unlock();

    }

    /**
     * Delete the parents of this DSF node, if any exist, and if they are no longer used.
     * Do not delete the node itself.  Do not fix focus.
     * @param dsf
     */
    protected synchronized void deleteAnyParentsOfDataSourceFilter(DataSourceFilter dsf) {
        DataSourceFilter[] parents = dsf.controller.getParentSources();
        // look for orphaned parents
        List<DataSourceFilter> alsoRemove = new ArrayList<DataSourceFilter>();
        for (DataSourceFilter pdf : parents) {
            if ( pdf==null ) continue; // bad reference
            String dsfId = pdf.getId();
            List<DomNode> usages = DomUtil.dataSourceUsages(application, dsfId);
            usages.remove(dsf);
            if (usages.size() == 0) {
                alsoRemove.add(pdf);
            }
        }

        if ( alsoRemove.size()>0 ) {
            List<DataSourceFilter> dsfs = new ArrayList<DataSourceFilter>(Arrays.asList(application.getDataSourceFilters()));
            dsfs.removeAll(alsoRemove);
            application.setDataSourceFilters(dsfs.toArray(new DataSourceFilter[dsfs.size()]));

            TimeSeriesBrowseController tsbc;
            for ( int i=0; i<alsoRemove.size(); i++ ) {
                tsbc= alsoRemove.get(i).controller.getTimeSeriesBrowseController();
                if ( tsbc!=null ) tsbc.release();
            }
        }
        
    }

    /**
     * delete the dsf and any parents that deleting it leaves orphaned. (??? maybe they should be called children...)
     * @param dsf
     */
    public synchronized void deleteDataSourceFilter(DataSourceFilter dsf) {
        if (!application.dataSourceFilters.contains(dsf)) {
            logger.fine("dsf wasn't part of the application");
            return;
        }
        if (application.dataSourceFilters.size() < 2) {
            throw new IllegalArgumentException("last plot cannot be deleted");
        }
        List<PlotElement> plotElements = this.getPlotElementsFor(dsf);
        if (plotElements.size() > 0) {
            throw new IllegalArgumentException("dsf must not have plot elements before deleting");
        }

        //dsf.removePropertyChangeListener(application.childListener);
        dsf.removePropertyChangeListener(domListener);
        unbind(dsf);
        dsf.controller.unbind();

        //TODO: this is a repeat of code in deleteAnyParentsOfDataSourceFilter.  Why can't it be used?
        DataSourceFilter[] parents = dsf.controller.getParentSources();
        // look for orphaned parents
        List<DataSourceFilter> alsoRemove = new ArrayList<DataSourceFilter>();
        for (DataSourceFilter pdf : parents) {
            if ( pdf==null ) continue;
            String dsfId = pdf.getId();
            List<DomNode> usages = DomUtil.dataSourceUsages(application, dsfId);
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

        TimeSeriesBrowseController tsbc= dsf.getController().getTimeSeriesBrowseController();
        if ( tsbc!=null ) tsbc.release();
        for ( int i=0; i<alsoRemove.size(); i++ ) {
            tsbc= alsoRemove.get(i).controller.getTimeSeriesBrowseController();
            if ( tsbc!=null ) tsbc.release();
        }

    }

    /**
     * resets the dom to the initial state by deleting added plotElements, plots and data sources.
     */
    public synchronized void reset() {
        DomLock lock= mutatorLock();
        lock.lock("Reset");
        Lock canvasLock = canvas.controller.getDasCanvas().mutatorLock();
        canvasLock.lock();
        try {

            Plot p0= getPlotFor(application.getPlotElements(0) );

            for ( int i=application.getPlots().length-1; i>0; i-- ) {
                deletePlot( application.getPlots(i) );
            }

            for ( int i=application.getPlotElements().length-1; i>0; i-- ) {
                deletePlotElement( application.getPlotElements(i) ); //may delete dsf and plots as well.
            }
            
            application.getDataSourceFilters(0).setId( "data_0" );//TODO: this should also reset the listening plotElement
            application.getPlotElements(0).setDataSourceFilterId("data_0");
            
            if ( p0!=application.getPlots(0) ) {
                movePlotElement(application.getPlotElements(0),p0,application.getPlots(0));
            }
            application.getPlots(0).setId("plot_0");//TODO: this should also reset the listening plotElement
            application.getPlotElements(0).setPlotId("plot_0");

            for ( int i=application.getDataSourceFilters().length-1; i>0; i-- ) {
                deleteDataSourceFilter( application.getDataSourceFilters(i) );
            }

            application.getPlotElements(0).setId("plotElement_0");

            application.getPlots(0).getXaxis().setLog(false); // TODO kludge
            application.getPlots(0).getYaxis().setLog(false); // TODO kludge
            application.getPlots(0).getZaxis().setLog(false); // TODO kludge
            application.getPlots(0).syncTo( new Plot(), Arrays.asList( DomNode.PROP_ID, Plot.PROP_ROWID, Plot.PROP_COLUMNID ) );
            application.getPlots(0).getXaxis().setAutoRange(true);
            application.getPlots(0).getYaxis().setAutoRange(true);
            application.getPlots(0).getZaxis().setAutoRange(true);

            for ( int i=application.getBindings().length-1; i>=0; i-- ) {
                deleteBinding( application.getBindings(i) );
            }

            // return canvas to ground state.  This is margin row, margin column, and
            // one row within.
            Canvas c= application.getCanvases(0);

            application.getPlots(0).setColumnId( c.getMarginColumn().getId() );
            application.getPlots(0).setRowId( c.getRows(0).getId() );

            for ( int i=c.getRows().length-1; i>=1; i-- ) {
                c.getController().deleteRow(c.getRows(i));
            }

            if ( c.getRows().length>0 ) {
                c.getRows(0).syncTo( new Row(), Arrays.asList(DomNode.PROP_ID, Row.PROP_TOP, Row.PROP_BOTTOM, Row.PROP_PARENT ) );
                c.getRows(0).setTop("+2em");
                c.getRows(0).setBottom("+100%-2em");
            }

            c.getMarginRow().setTop("2em");
            c.getMarginRow().setBottom("100%-3em");
            c.getMarginColumn().setLeft("+7.0em");
            c.getMarginColumn().setRight("100%-7.0em");

            for ( int i=c.getColumns().length-1; i>=0; i-- ) {
                c.getController().deleteColumn(c.getColumns(i));
            }

            c.setFitted(true);

            application.getDataSourceFilters(0).syncTo( new DataSourceFilter(), Collections.singletonList(DomNode.PROP_ID) );
            application.getDataSourceFilters(0).getController().setDataSetInternal(null,null,true);
            application.getPlots(0).syncTo( new Plot(), Arrays.asList( DomNode.PROP_ID, Plot.PROP_COLUMNID, Plot.PROP_ROWID ) );
            application.getPlotElements(0).syncTo( new PlotElement(), Arrays.asList( DomNode.PROP_ID, PlotElement.PROP_PLOTID,PlotElement.PROP_DATASOURCEFILTERID, PlotElement.PROP_RENDERTYPE ) );
            application.getPlots(0).syncTo( new Plot(), Arrays.asList( DomNode.PROP_ID, Plot.PROP_COLUMNID, Plot.PROP_ROWID ) );
            application.getPlots(0).setAutoLabel(true);
            application.getPlotElements(0).syncTo( new PlotElement(), Arrays.asList( DomNode.PROP_ID, PlotElement.PROP_PLOTID, PlotElement.PROP_DATASOURCEFILTERID ) );
            application.getPlotElements(0).setAutoLabel(true);
            application.getPlotElements(0).getPlotDefaults().setId("plot_defaults_0");
            application.getPlotElements(0).getStyle().setId("style_0");
            application.getPlotElements(0).getStyle().setFillColor( Color.decode("#404040") );
            application.getPlotElements(0).getStyle().setColor( application.getOptions().getColor() );
            if ( !application.getCanvases(0).getController().getDasCanvas().getBackground().equals( application.getOptions().getBackground() ) ) { // I think they are bound, so this really isn't necessary.
                application.getCanvases(0).getController().getDasCanvas().setBackground( application.getOptions().getBackground() );
            }
            application.getPlots(0).getXaxis().setAutoLabel(true);
            application.getPlots(0).getYaxis().setAutoLabel(true);
            application.getPlots(0).getZaxis().setAutoLabel(true);
            application.getPlots(0).getXaxis().setAutoRange(true);
            application.getPlots(0).getYaxis().setAutoRange(true);
            application.getPlots(0).getZaxis().setAutoRange(true);
            application.getPlotElements(0).controller.setDsfReset(true);
            application.getPlots(0).getZaxis().setVisible(false);
            
            application.setTimeRange( Application.DEFAULT_TIME_RANGE );
            application.getPlots(0).setTicksURI("");
            application.getPlots(0).setContext( application.getPlots(0).getXaxis().getRange() ); //TODO: this will be a timerange soon

            resetIdSequenceNumbers();

            //clean up controllers after seeing the junk left behind in the profiler.
            for ( PlotElement pe :application.getPlotElements() ) {
                pe.getController().dataSet=null;
            }
            for ( DataSourceFilter dsf :application.getDataSourceFilters() ) {
                dsf.getController().dataSet=null;
                dsf.getController().fillDataSet=null;
                dsf.getController().histogram=null;
            }

            //go ahead and check for leftover das2 plots and renderers that might have been left from a bug.  rfe3324592
            DasCanvasComponent[] dccs= canvas.controller.getDasCanvas().getCanvasComponents();
            for ( int i=0; i<dccs.length; i++ ) {
                if ( dccs[i] instanceof DasPlot ) {
                    DasPlot p= (DasPlot)dccs[i];
                    boolean okay=false;
                    for ( Plot pp: application.getPlots() ) {
                        if ( pp.getController().getDasPlot()==p ) okay=true;
                    }
                    if ( !okay ) {
                        canvas.controller.getDasCanvas().remove(p);
                    } else {
                        Renderer[] rr= p.getRenderers();
                        for ( int j=0; j<rr.length; j++ ) {
                            okay= false;
                            for ( PlotElement pes: application.getPlotElements() ) {
                               if ( pes.getController().getRenderer()==rr[j] ) okay=true;
                            }
                            if ( !okay ) {
                                p.removeRenderer(rr[j]);
                            }
                        }
                    }
                }
            }




        } finally {
            canvasLock.unlock();
            lock.unlock();
        }

        ArrayList problems= new ArrayList();
        if ( !DomUtil.validateDom(application, problems ) ) {
            System.err.println(problems);
        }
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
    public void bind(DomNode src, String srcProp, Object dst, String dstProp, Converter converter ) {
        
        logger.log( Level.FINER, "bind {0}.{1} to {2}.{3}", new Object[]{src, srcProp, dst, dstProp});
        
        String srcId = src.getId();

        String dstId = "???";
        if (dst instanceof DomNode) {
            dstId = ((DomNode) dst).getId();
        }
        if (dst instanceof DasCanvasComponent) {
            dstId = "das2:" + ((DasCanvasComponent) dst).getDasName();
        }

        BindingModel bindingModel = new BindingModel(srcId, srcId, srcProp, dstId, dstProp);

        if ( application.bindings.contains(bindingModel) ) {
            logger.finest("binding already exists, ignoring");
            setStatus("binding already exists: "+bindingModel );
            return;
        }

        try {
            // verify properties exist.
            DomUtil.getPropertyType(src, srcProp);
            if ( dst instanceof DomNode ) {
                 DomUtil.getPropertyType((DomNode)dst, dstProp);
            }
        } catch (IllegalAccessException ex) {
            throw new IllegalArgumentException(ex);
        } catch (InvocationTargetException ex) {
            throw new IllegalArgumentException(ex);
        }

        // now do the implementation
        BindingGroup bc;
        synchronized (bindingContexts) {
            bc = bindingContexts.get(src);
            if (bc == null) {
                bc = new BindingGroup();
                bindingContexts.put(src, bc);
            }
        }


        if (!dstId.equals("???") && !dstId.startsWith("das2:")) {
            Binding binding;

            binding = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, src, BeanProperty.create(srcProp), dst, BeanProperty.create(dstProp));
            if ( converter!=null ) binding.setConverter( converter );

            List<BindingModel> bindings = new ArrayList<BindingModel>(Arrays.asList(application.getBindings()));
            bindings.add(bindingModel);
            application.setBindings(bindings.toArray(new BindingModel[bindings.size()]));
            bc.addBinding(binding);
            binding.bind();
            this.bindingImpls.put(bindingModel, binding);
            
        } else {
            // these are bindings used to implement the application, such as from an Axis to DasAxis.
            // The user shouldn't be able to unbind these.
            bindingSupport.bind( src, srcProp, dst, dstProp, converter );

        }

    }

    /**
     * bind the dom node to another object.
     * @param src
     * @param srcProp string containing the property name.
     * @param dst
     * @param dstProp
     */
    public void bind( DomNode src, String srcProp, Object dst, String dstProp) {
        bind(src, srcProp, dst, dstProp, null );
    }

    /**
     * unbind the binding between a dom node and another object.
     * @param src
     * @param srcProp the property name.
     * @param dst
     * @param dstProp the property name.
     */
    public void unbind( DomNode src, String srcProp, Object dst, String dstProp ) {
        bindingSupport.unbind( src, srcProp, dst, dstProp );
    }
    /**
     * unbind the object, removing any binding to this node.  For example, when the object is about to be deleted.
     * @param src
     */
    public void unbind(DomNode src) {
        BindingGroup bc;

        synchronized (bindingImpls) {
            List<BindingModel> bb = new ArrayList(Arrays.asList(application.getBindings()));
            for (BindingModel b : application.getBindings()) {
                if (b.getSrcId().equals(src.getId()) || b.getDstId().equals(src.getId())) {
                    bb.remove(b);
                    Binding bimpl= bindingImpls.get(b);
                    if ( bimpl!=null ) {
                        bimpl.unbind();
                        bindingImpls.remove(b);
                    }
                }
            }
            application.setBindings(bb.toArray(new BindingModel[bb.size()]));
        }

        synchronized (bindingContexts) {
            bc = bindingContexts.get(src);
            if (bc != null) {

                bc.unbind();
                bindingContexts.remove(src);

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

    /**
     * unbind all implementation bindings associated with the dom node.
     * @param src
     */
    protected void unbindImpl( DomNode src ) {
        bindingSupport.unbind(src);
    }

    public void deleteBinding(BindingModel binding) {
        Binding b = bindingImpls.get(binding);
        if ( b==null ) {
            new IllegalArgumentException("didn't find the binding implementation for "+binding+", ignoring").printStackTrace();
            return; //TODO: why?
        }
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
        List<BindingModel> results= findBindings( src,  srcProp, dst, dstProp );
        if ( results.size()==0 ) {
            return null;
        } else {
            return results.get(0);  // TODO: this should be a singleton.
        }
    }

    /**
     * Find the bindings that match given constraints.  If a property name or node is null, then the
     * search is unconstrained.
     * @param src
     * @param srcProp
     * @param dst
     * @param dstProp
     * @return the BindingModel or null if it doesn't exist.
     */
    public List<BindingModel> findBindings(DomNode src, String srcProp, DomNode dst, String dstProp) {
        List<BindingModel> result= new ArrayList();
        for (BindingModel b : application.getBindings()) {
            try {
                if (  ( src==null || b.getSrcId().equals(src.getId()) )
                        && ( dst==null || b.getDstId().equals(dst.getId()) )
                        && ( srcProp==null || b.getSrcProperty().equals(srcProp) )
                        && ( dstProp==null || b.getDstProperty().equals(dstProp) ) ){
                    result.add(b);
                } else if ( ( dst==null || b.getSrcId().equals(dst.getId()) )
                        && ( src==null || b.getDstId().equals(src.getId()) )
                        && ( dstProp==null || b.getSrcProperty().equals(dstProp) )
                        && ( srcProp==null || b.getDstProperty().equals(srcProp) ) ) {
                    result.add(b);
                }
            } catch (NullPointerException ex) {
                throw ex;
            }

        }
        return result;
    }

    public BindingModel[] getBindingsFor(DomNode node) {
        List<BindingModel> results= findBindings( node, null, null, null );
        return results.toArray( new BindingModel[results.size()] );
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
        logger.log(Level.FINE, "{0} (status message)", status);
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

    /**
     * return a plot that is immediately above the given plot.  This
     * encapsulates the layout model, and implementation should change.
     * @param p
     * @return
     */
    public Plot getPlotAbove(Plot p) {
        return getPlot( p, LayoutConstants.ABOVE );
    }

    /**
     * return a plot that is immediately below the given plot.  This
     * encapsulates the layout model, and implementation should change.
     * @param p
     * @return
     */
    public Plot getPlotBelow(Plot p) {
        return getPlot( p, LayoutConstants.BELOW );
    }

    public Plot getNextPlotHoriz( Plot p, Object dir ) {
        Column r= getCanvas().getController().getColumnFor(p);
        Column left= getCanvas().getController().getColumn(r,dir);

        if ( left==null ) return null;
        int n = application.getPlots().length;
        Plot best= null;
        for (int i = 0; i < n; i++) {
            final Plot p1 = application.getPlots(i);
            if (p1.getColumnId().equals( left.getId() ) ) {
                if ( best==null ) {
                    best= p1;
                } else {
                    if ( p1.rowId.equals( p.getRowId() ) ) {
                        best= p1;
                    }
                }
            }
        }
        return best;
    }

    public Plot getPlot( Plot p, Object dir ) {
        Row r= getCanvas().getController().getRowFor(p);
        Row above= getCanvas().getController().getRow(r,dir);

        if ( above==null ) return null;
        int n = application.getPlots().length;
        Plot best= null;
        for (int i = 0; i < n; i++) {
            final Plot p1 = application.getPlots(i);
            if (p1.getRowId().equals( above.getId() ) ) {
                if ( best==null ) {
                    best= p1;
                } else {
                    if ( p1.columnId.equals( p.getColumnId() ) ) {
                        best= p1;
                    }
                }
            }
        }
        return best;
    }

    /**
     * return the plotId containing this plotElement.
     * @param element
     * @return the Plot or null if no plotId is found.
     * @throws IllegalArgumentException if the element is not a child of the application
     */
    public Plot getPlotFor(PlotElement element) {
        String id = element.getPlotId();
        Plot result = null;
        for (Plot p : application.getPlots()) {
            if (p.getId().equals(id)) {
                result = p;
            }
        }
        return result;
    }


    public List<PlotElement> getPlotElementsFor(Plot plot) {
        return DomUtil.getPlotElementsFor( application, plot );
    }

    /**
     * return the DataSourceFilter for the plotElement, or null if none exists.
     * See also getFirstPlotFor
     * @param plotElement
     * @return
     */
    public DataSourceFilter getDataSourceFilterFor(PlotElement element) {
        String id = element.getDataSourceFilterId();
        DataSourceFilter result = null;
        for (DataSourceFilter dsf : application.getDataSourceFilters()) {
            if (dsf.getId().equals(id)) {
                result = dsf;
            }
        }
        return result;
    }


    /**
     * return the PlotElements using the DataSourceFilter.  This does not
     * return indirect (via vap+internal) references.
     * @param plot
     * @return
     */
    public List<PlotElement> getPlotElementsFor(DataSourceFilter plot) {
        String id = plot.getId();
        List<PlotElement> result = new ArrayList<PlotElement>();
        for (PlotElement p : application.getPlotElements()) {
            if (p.getDataSourceFilterId().equals(id)) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * find the first plot that is connected to this data, following vap+internal
     * links.  This is used, for example, to get a timerange to control the 
     * DSF.
     * @param dsf
     * @return
     */
    public Plot getFirstPlotFor( DataSourceFilter dsf ) {
        String lookFor= dsf.getId();
        PlotElement f= null;

        List<DataSourceFilter> dsfs= new ArrayList();
        dsfs.add(dsf);

        for ( PlotElement pe : application.plotElements ) {
            if ( pe.getDataSourceFilterId().equals(lookFor) ) {
                f= pe;
            }
        }

        Pattern p= Pattern.compile("vap\\+internal:([a-z][a-z_0-9]*)(,([a-z][a-z_0-9]*))*");
        if ( f==null ) { // no one refers to dsf directly, look for vap+internal:
            for ( DataSourceFilter dsf1: application.getDataSourceFilters() ) {
                if ( dsf1.getUri().length()>0 ) {
                    Matcher m= p.matcher(dsf1.getUri());
                    if ( m.matches() ) {
                        int n= m.groupCount()+1;
                        for ( int i=1; i<n; i+=2 ) {
                            if ( m.group(i).equals(dsf.getId()) ) {
                                if ( Thread.currentThread().getStackTrace().length<100 ) { // TODO: remove once circular references are checked for elsewhere
                                    return getFirstPlotFor( dsf1 );
                                } else {
                                    throw new IllegalArgumentException("circular references in dsfs");
                                }
                            }
                        }
                    }
                }
            }
            return null;
        } else {
            return getPlotFor(f);
        }
    }

    /**
     * assign a unique name to this node, considering its type.
     * @param node
     */
    protected void assignId( DomNode node ) {
        if ( node instanceof Row ) {
            node.setId( "row_"+rowIdNum.getAndIncrement() );

        } else if ( node instanceof Column ) {
            node.setId( "column_"+columnIdNum.getAndIncrement() );

        } else if ( node instanceof DataSourceFilter ) {
            node.setId("data_" + dsfIdNum);
            dsfIdNum.getAndIncrement();
        } else if ( node instanceof Canvas ) {
            int i= canvasIdNum.getAndIncrement();
            node.setId("canvas_"+i);
            Canvas ca= (Canvas)node;
            ca.getMarginColumn().setId("marginColumn_"+i);
            ca.getMarginRow().setId("marginRow_"+i);

        } else if ( node instanceof PlotElement ) {
            int i= plotElementIdNum.getAndIncrement();
            node.setId("plotElement_"+i );
            ((PlotElement)node).getStyle().setId("style_"+i);
            ((PlotElement)node).getPlotDefaults().setId("plot_defaults_" + i);
        } else if ( node instanceof Plot ) {
            int num = plotIdNum.getAndIncrement();
            Plot domPlot= (Plot)node;
            domPlot.setId("plot_" + num);
            domPlot.getXaxis().setId("xaxis_" + num);
            domPlot.getYaxis().setId("yaxis_" + num);
            domPlot.getZaxis().setId("zaxis_" + num);

        } else {
            throw new IllegalArgumentException("unsupported type: "+node.getClass().getName() );
        }
    }

    /**
     * returns the maximum id number found, or -1.
     * @param nodes
     * @param pattern
     * @return
     */
    private int maxIdNum( List<DomNode> nodes, String pattern ) {
        int min= -1;
        Pattern p= Pattern.compile(pattern);
        for ( DomNode n: nodes ) {
            Matcher m= p.matcher(n.getId());
            if ( m.matches() ) {
                int idNum= Integer.parseInt( m.group(1) );
                if (idNum>min ) min= idNum;
            }
        }
        return min;
    }
    /**
     * reset the sequence id numbers based on the number if instances in the 
     * application.  For example, we sync to a new state, so the id numbers 
     * are now invalid.
     */
    private void resetIdSequenceNumbers() {
        List<DomNode> nodes;
        nodes= DomUtil.findElementsById( application, ".+_(\\d+)" );
        rowIdNum.set( maxIdNum( nodes, "row_(\\d+)" )+1 );
        columnIdNum.set( maxIdNum( nodes, "column_(\\d+)" )+1 );
        dsfIdNum.set( maxIdNum( nodes, "data_(\\d+)") + 1 );
        canvasIdNum.set( maxIdNum( nodes, "canvas_(\\d+)" ) + 1 );
        plotElementIdNum.set( maxIdNum( nodes, "plotElement_(\\d+)" ) + 1 );
        plotIdNum.set( maxIdNum( nodes, "plot_(\\d+)" ) + 1 );
    }

    /** focus **/
    /**
     * focus plot element
     */
    protected PlotElement plotElement;
    public static final String PROP_PLOT_ELEMENT = "plotElement";

    public PlotElement getPlotElement() {
        return plotElement;
    }

    public void setPlotElement(PlotElement plotElement) {
        PlotElement oldPlotElement = this.plotElement;
        if ( plotElement==null ) {
            setStatus("no plot element selected");
        } else {
            setStatus(plotElement + " selected");
            if ( application.getPlotElements().length>1 || ( plotElement!=oldPlotElement ) ) {
                getCanvas().controller.indicateSelection( Collections.singletonList((DomNode)plotElement) );
            }
            if ( plotElement!=oldPlotElement ) {
                Plot lplot= getPlotFor(plotElement);
                if ( lplot!=null && lplot.getController()!=null ) {
                    JMenuItem mi= lplot.getController().getPlotElementPropsMenuItem();
                    if ( mi!=null && plotElement.getController()!=null && plotElement.getController().getRenderer()!=null )
                        mi.setIcon( plotElement.getController().getRenderer().getListIcon() );
                }
            }
        }
        this.plotElement = plotElement;
        propertyChangeSupport.firePropertyChange(PROP_PLOT_ELEMENT, oldPlotElement, plotElement);
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
    protected Canvas canvas;
    public static final String PROP_CANVAS = "canvas";

    public Canvas getCanvas() {
        return canvas;
    }

    public synchronized void setCanvas(Canvas canvas) {
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
        ac.bind(application.options, "background", canvas, "background" );
        ac.bind(application.options, "foreground", canvas, "foreground" );
        ac.bind(application.options, "canvasFont", canvas, "baseFont", DomUtil.STRING_TO_FONT );
    }

    protected synchronized void syncTo( Application that, List<String> exclude ) {
        DomLock lock = changesSupport.mutatorLock();
        lock.lock( "Sync to Application" );
        Lock canvasLock = getCanvas().controller.getDasCanvas().mutatorLock();
        canvasLock.lock();

        try {

            if ( !exclude.contains("options") ) application.getOptions().syncTo(that.getOptions(),
                    Arrays.asList(Options.PROP_OVERRENDERING,
                    Options.PROP_LOGCONSOLEVISIBLE,
                    Options.PROP_SCRIPTVISIBLE,
                    Options.PROP_SERVERENABLED));


            Map<String,String> nameMap= new HashMap<String,String>() {
                @Override
                public String get(Object key) {
                    String result= super.get(key);
                    return (result==null) ? (String)key : result;
                }
            };

            if ( !this.application.id.equals( that.id ) ) nameMap.put( that.id, this.application.id );

            if ( !exclude.contains("canvases") ) syncSupport.syncToCanvases(that.getCanvases(),nameMap);

            if ( !exclude.contains("plots") ) syncSupport.syncToPlots( that.getPlots(),nameMap );

            if ( !exclude.contains("dataSourceFilters") ) syncSupport.syncToDataSourceFilters(that.getDataSourceFilters(), nameMap);

            if ( !exclude.contains("plotElements") )  syncSupport.syncToPlotElements(that.getPlotElements(), nameMap);

            application.setTimeRange(that.getTimeRange());

            syncSupport.syncBindingsNew( that.getBindings(), nameMap );
            syncSupport.syncConnectors(that.getConnectors());

            resetIdSequenceNumbers();
        } finally {
            canvasLock.unlock();
            lock.unlock();
        }

        for (PlotElement p : application.getPlotElements()) {  // kludge to avoid reset range
            p.controller.setResetPlotElement(false);  // see https://sourceforge.net/tracker/index.php?func=detail&aid=2985891&group_id=199733&atid=970682
            p.controller.setResetComponent(false);
            p.controller.setResetRanges(false);
            //p.controller.doResetRenderType( p.getRenderType() );
            p.controller.setResetRenderType(false);
            p.controller.setDsfReset(true); // dataSourcesShould be resolved.
        }
        for (DataSourceFilter dsf: application.getDataSourceFilters() ) {
            dsf.controller.setResetDimensions(false);
        }
    }

    /**
     * true if running in headless environment
     */
    public boolean isHeadless() {
        return headless;
    }

    public DasCanvas getDasCanvas() {
        return ((CanvasController)getCanvas().controller).getDasCanvas();
    }

    public DasRow getRow() {
        return getCanvas().getMarginRow().getController().getDasRow();
    }

    public DasColumn getColumn() {
        return getCanvas().getMarginColumn().getController().getDasColumn();
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

    /**
     * return true if the plot is part of a connector, the top or the bottom.
     * @param plot
     * @return
     */
    boolean isConnected( Plot plot ) {
        Connector[] connectors= this.application.getConnectors();
        for ( int i=0; i<connectors.length; i++ ) {
            if ( connectors[i].getPlotB().equals(plot.getId()) ) {
                return true;
            } else if ( connectors[i].getPlotA().equals(plot.getId()) ) {
                return true;
            }
        }
        return false;
    }

}
