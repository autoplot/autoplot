
package org.autoplot.dom;

import java.awt.AWTEventMulticaster;
import java.awt.Color;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.das2.DasApplication;
import org.das2.components.propertyeditor.PropertyEditor;
import org.das2.datum.Datum;
import org.das2.datum.DatumRangeUtil;
import org.das2.event.MouseModule;
import org.das2.graph.AnchorPosition;
import org.das2.graph.AnchorType;
import org.das2.graph.ColumnColumnConnector;
import org.das2.graph.DasAnnotation;
import org.das2.graph.DasAxis;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasCanvasComponent;
import org.das2.graph.DasColorBar;
import org.das2.graph.DasColumn;
import org.das2.graph.DasPlot;
import org.das2.graph.DasRow;
import org.das2.graph.Renderer;
import org.das2.system.MonitorFactory;
import org.das2.system.RequestProcessor;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.jdesktop.beansbinding.Converter;
import org.jdesktop.beansbinding.Property;
import org.autoplot.ApplicationModel;
import org.autoplot.AutoplotUI;
import org.autoplot.AutoplotUtil;
import org.autoplot.ColumnColumnConnectorMouseModule;
import org.autoplot.GuiSupport;
import org.autoplot.LayoutListener;
import org.autoplot.ScriptContext2023;
import org.autoplot.dom.ChangesSupport.DomLock;
import org.autoplot.layout.LayoutConstants;
import org.autoplot.renderer.AnnotationEditorPanel;
import org.autoplot.util.RunLaterListener;
import org.das2.graph.DasDevicePosition;
import org.das2.qds.QDataSet;
import org.das2.system.DefaultMonitorFactory;
import org.das2.system.DefaultMonitorFactory.MonitorEntry;

/**
 * The ApplicationController, one per dom, is in charge of managing the 
 * application as a whole, for example, adding and deleting plots,
 * managing bindings, and managing focus.
 * 
 * @author jbf
 */
public class ApplicationController extends DomNodeController implements RunLaterListener.PropertyChange {

    Application application;
    ApplicationModel model;
    DasRow outerRow;
    DasColumn outerColumn;
    LayoutListener layoutListener;
    
    /**
     * binding contexts store each set of bindings as a group.  For example, 
     * bind( src, srcprop, dst, dstprop ) stores the binding in the context of the src
     * object, and unbind(src) will remove all of that context's bindings.  
     * TODO: this all needs review.  I'm not sure what is what...
     */
    final Map<Object, BindingGroup> bindingContexts;
    
    //final Map<Object, BindingGroup> implBindingContexts; // these are for controllers to use.
    protected BindingSupport bindingSupport= new BindingSupport();

    final Map<BindingModel, Binding> bindingImpls;
    final Map<Connector, ColumnColumnConnector> connectorImpls;
    final Map<Annotation, DasAnnotation> annotationImpls;
    
    private final static Logger logger = LoggerManager.getLogger( "autoplot.dom" );

    private static final AtomicInteger canvasIdNum = new AtomicInteger(0);
    private static final AtomicInteger plotIdNum = new AtomicInteger(0);
    private static final AtomicInteger plotElementIdNum = new AtomicInteger(0);
    private static final AtomicInteger dsfIdNum = new AtomicInteger(0);
    private static final AtomicInteger rowIdNum = new AtomicInteger(0);
    private static final AtomicInteger columnIdNum = new AtomicInteger(0);
    private static final AtomicInteger annotationNum = new AtomicInteger(0);
    private static final AtomicInteger appIdNum= new AtomicInteger(0);

    ApplicationControllerSyncSupport syncSupport;
    ApplicationControllerSupport support;
    
    ActionListener eventListener;

    private static final String PENDING_BREAK_APART = "breakApart";
    
    /**
     * use this value to blur the focus.
     */
    public static final String VALUE_BLUR_FOCUS= "";
    
    public ApplicationController(ApplicationModel model, Application application) {
        super( application );

        // kludge to trigger undo/redo support.
        changesSupport.addPropertyChangeListener( new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                LoggerManager.logPropertyChangeEvent(evt);                
                if ( evt.getPropertyName().equals("status")
                        && "ready".equals(evt.getNewValue() ) ) {
                    //fireActionEvent( new ActionEvent(this,0,"ready") );
                }
                if ( evt.getPropertyName().equals(ChangesSupport.PROP_VALUEADJUSTING) && evt.getNewValue()==null ) { //put in state after atomtic operation
                    String description= (String) evt.getOldValue();
                    if ( description==null ) {
                        logger.severe("description is null");  // debugging observed 7/23/2012
                    } else {
                        if ( description.length()>0 ) {
                            fireActionEvent( new ActionEvent(this,0,"label: "+description ) );
                        } else {
                            fireActionEvent( new ActionEvent(this,0,"ready") );
                        }
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
        if ( application.getOptions().getController()==null ) {
            OptionsPrefsController opc= new OptionsPrefsController( model, application.getOptions());
            logger.log(Level.FINE, "adding controller {0}", opc );
            opc.loadPreferencesWithEvents();
        }

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
        annotationImpls = new HashMap();
        
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
        ActionListener l= AWTEventMulticaster.remove(eventListener, list);
        logger.log(Level.FINEST, "removed {0}", l);
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
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt,"controllerListener");            
            // only go into logger stuff if we know it's going to log.  This is for performance, I noticed a large number of Object instances when profiling and this could help performance.
            if ( logger.isLoggable(Level.FINEST) ) logger.log(Level.FINEST, "controller change: {0}.{1} ({2}->{3})", new Object[]{evt.getSource(), evt.getPropertyName(), evt.getOldValue(), evt.getNewValue()});
            //if( evt.getPropertyName().equals("resetDimensions") && evt.getNewValue().equals(Boolean.TRUE) ) {
            //    logger.warning("here here here");
            //}
        }

    };

    /**
     * This plugs in listeners as arrays of DomNodes change.
     */
    PropertyChangeListener domListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt,"domListener");
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

            if ( evt.getPropertyName().equals("id") ) {
                String newV= (String)evt.getNewValue();
                if ( newV.length()>0 ) {
                    List<String> ids= new ArrayList<>();
                    ids.add( ApplicationController.this.getApplication().getId() );
                    for ( DomNode n: ApplicationController.this.getApplication().getAnnotations() ) ids.add( n.getId() );
                    for ( DomNode n: ApplicationController.this.getApplication().getCanvases() ) ids.add( n.getId() );
                    for ( DomNode n: ApplicationController.this.getApplication().getCanvases(0).getColumns() ) ids.add( n.getId() );
                    for ( DomNode n: ApplicationController.this.getApplication().getCanvases(0).getRows() ) ids.add( n.getId() );
                    ids.add( ApplicationController.this.getApplication().getCanvases(0).getMarginColumn().getId() );
                    ids.add( ApplicationController.this.getApplication().getCanvases(0).getMarginRow().getId() );
                    for ( DomNode n: ApplicationController.this.getApplication().getDataSourceFilters() ) ids.add( n.getId() );
                    for ( DomNode n: ApplicationController.this.getApplication().getPlotElements() ) ids.add( n.getId() );
                    for ( DomNode n: ApplicationController.this.getApplication().getPlots() ) ids.add( n.getId() );
                    int count=0;
                    for ( String s: ids ) {
                        if ( s.equals(newV) ) {
                            count++;
                        }
                    }
                    if ( count>1 ) {
                        System.err.println("duplicate ID, count: "+count + " id: "+newV );
                    }
                }
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
                            if ( c!=null ) c.addPropertyChangeListener(controllerListener);
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
        if ( p.controller==null ) return "";
        DataSourceFilter dsf= p.controller.getDataSourceFilter();
        if ( dsf!=null ) {
            return dsf.getUri();
        } else {
            return "";
        }
    }
    
    private Plot currentFocusPlot= null;
    
    // listen for focus changes and update the focus plot and plotElement.
    FocusAdapter focusAdapter = new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e) {
            LoggerManager.logGuiEvent(e);
            super.focusGained(e);

            //if ( e.getComponent() instanceof ColumnColumnConnector ) {
                //logger.fine( "focus on column column connector");
            //}
            
            Plot domPlot = getPlotFor(e.getComponent());
            if (domPlot == null) {
                return;
            }

            DasPlot dasPlot = domPlot.controller.getDasPlot();

            PlotElement p = null;

            Renderer r = dasPlot.getFocusRenderer();

            if (r != null) {
                try {
                    p = findPlotElement(r);
                    setPlotElement(p);
                } catch ( IllegalArgumentException ex ) {
                    // transitional case.  TODO: thread locking would presumably fix this.  
                }
            }

            // if there's just one plotElement in the plot, then go ahead and set the focus uri.
            List<PlotElement> ps = ApplicationController.this.getPlotElementsFor(domPlot);
            if ( p==null && ps.size() == 1) {
                setFocusUri( getFocusUriFor( ps.get(0) ) );
            }

            if ( p==null && ps.size()>0 ) { // allow the user to select the first inactive plot element
                for ( int i=ps.size()-1; i>=0; i-- ) {
                    PlotElement pe1= ps.get(i);
                    if ( pe1.isActive()==false ) {
                        p= pe1;
                        break;
                    }
                }
            }
            
            final Plot fdomPlot= domPlot;
            final PlotElement fp;
            if (p != null) {
                logger.log(Level.FINE, "focus due to plot getting focus: {0}", p);
                setFocusUri( getFocusUriFor( p ) );
                fp= p;
                if ( getPlotElement()!=p ) { 
                    setStatus("" + domPlot + ", " + p + " selected");
                    if ( ApplicationController.this.getApplication().getPlotElements().length>1 ) {  // don't flash single plot.
                        Canvas c= getCanvas();
                        c.controller.indicateSelection( Collections.singletonList((DomNode)p) ); // don't flash plot node, just the plot element.
                    }
                }
            } else {
                if ( domPlot!=currentFocusPlot ) {
                    setStatus("" + domPlot + " selected");
                    currentFocusPlot= domPlot;
                }
                fp= null;
            }

            Runnable run= () -> {
                setPlot(fdomPlot);
                if ( fp!=null ) setPlotElement(fp);
            };
            new Thread(run,"focusPlot").start();
            
            LoggerManager.logExitGuiEvent(e);
        }
    };
    
    /**
     * convert domPlot into a stack of plots that are bound by the x-axis and have
     * the labels turned off.
     * @param domPlot the plot to break up.
     */
    private void breakIntoStackPlot( Plot domPlot ) {
        DomLock lock = mutatorLock();
        lock.lock("Break into Stack of Plots");
        Lock clock= canvas.controller.dasCanvas.mutatorLock();
        clock.lock();
        try {
            List<PlotElement> peles = getPlotElementsFor(domPlot);
            for (PlotElement pele : peles) {
                if ( pele.isActive()==false ) {
                    ApplicationController.this.deletePlotElement(pele);
                }
            }
            peles = getPlotElementsFor(domPlot);

            if ( peles.size()<2 ) {
                setStatus("Only one plot element...");
            } else if ( peles.size()>12 ) {
                setStatus("Too many plots...");
            } else {
                Application dom= ApplicationController.this.getApplication();
                peles = getPlotElementsFor(domPlot);
                PlotElement firstPE= peles.get(0);
                PlotElement lastPE= peles.get(peles.size()-1);
                for (PlotElement pele : peles) {
                    PlotElement pelement = pele;
                    Plot dstPlot = ApplicationController.this.addPlot(LayoutConstants.ABOVE);
                    pelement.setPlotId(dstPlot.getId());
                    if ( pele==lastPE ) {
                        dstPlot.getXaxis().setDrawTickLabels(true);                            
                    } else {
                        dstPlot.getXaxis().setDrawTickLabels(false);
                    }
                    if ( pele==firstPE ) {
                        dstPlot.setDisplayTitle(true);                            
                    } else {
                        dstPlot.setDisplayTitle(false);                            
                    }
                    dstPlot.getXaxis().setLog(false);
                    bind( dstPlot.getXaxis(), Axis.PROP_RANGE, dom, Application.PROP_TIMERANGE );
                    
                    // we have to find some x-axis to listen to.
                    DataSourceFilter dsf= getDataSourceFilterFor(pele);
                    TimeSeriesBrowseController tsbc= dsf.getController().getTimeSeriesBrowseController();
                    if ( tsbc!=null ) {
                        if ( tsbc.isListeningToAxis() && tsbc.getPlot()==domPlot ) {
                            tsbc.release();
                            tsbc.setupAxis( dstPlot, dstPlot.getXaxis() );
                        }
                    }
                }
                ApplicationController.this.deletePlot(domPlot);
                org.autoplot.dom.DomOps.newCanvasLayout(dom);
                
            }
            
            
        } finally {
            lock.unlock();
            clock.unlock();
        }
    }

    public void fillEditPlotMenu( final JMenu editPlotMenu, final Plot domPlot) {
        JMenuItem item;
        item = new JMenuItem(new AbstractAction("Delete Plot") {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);
                if (application.getPlots().length > 1) {
                    List<PlotElement> plotElements = getPlotElementsFor(domPlot);
                    plotElements.forEach((pele) -> {
                        if (application.getPlotElements().length > 1) {
                            deletePlotElement(pele);
                        } else {
                            setStatus("warning: the last plot element may not be deleted");
                        }
                    });
                    deletePlot(domPlot);
                } else {
                    ArrayList<PlotElement> pes= new ArrayList( getPlotElementsFor(domPlot) );
                    Collections.reverse(pes);
                    pes.forEach((pe) -> {
                        deletePlotElement(pe);
                    });
                    domPlot.syncTo( new Plot(), Arrays.asList( "id", "rowId", "columnId" ) );
                }
            }
        });
        editPlotMenu.add(item);

        item = new JMenuItem(new AbstractAction("Break into Stack of Plots") {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);
                registerPendingChange( ApplicationController.this, PENDING_BREAK_APART );
                Runnable run= new Runnable() {
                    @Override
                    public void run() {
                        performingChange(ApplicationController.this, PENDING_BREAK_APART);
                        breakIntoStackPlot(domPlot);
                        changePerformed(ApplicationController.this, PENDING_BREAK_APART);
                        
                    }
                };
                RequestProcessor.invokeLater(run);
            }

        });
        item.setToolTipText("Replace the focus plot with stack of plots.");
        editPlotMenu.add(item);
        
        item= new JMenuItem( new AbstractAction("Copy Plot to Clipboard") {
            @Override
            public void actionPerformed(ActionEvent e) {
                String s= DomUtil.getPlotAsString( application, domPlot );
                StringSelection stringSelection = new StringSelection(s);
                Clipboard clpbrd = Toolkit.getDefaultToolkit().getSystemClipboard();
                clpbrd.setContents(stringSelection, null);
            }
        } );
        editPlotMenu.add(item);

        item= new JMenuItem( new AbstractAction("Replace Plot with Clipboard Plot") {
            @Override
            public void actionPerformed(ActionEvent e) {
                GuiSupport.pasteClipboardIntoPlot( editPlotMenu, ApplicationController.this, domPlot );
            }
        } );
        editPlotMenu.add(item);
        
        item= new JMenuItem( new AbstractAction("Insert Plot Elements from Clipboard Plot...") {
            @Override
            public void actionPerformed(ActionEvent e) {
                GuiSupport.pasteClipboardPlotElementsIntoPlot( plot.getController().getDasPlot(), ApplicationController.this, domPlot );
            }
        } );
        editPlotMenu.add(item);
        
        item = new JMenuItem(new AbstractAction("Remove Bindings") {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);
                List<BindingModel> bms= new ArrayList<>();

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

        item = new JMenuItem(new AbstractAction("Bind Plot Context to Application Time Range") {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);
                bind( application, Application.PROP_TIMERANGE, plot, Plot.PROP_CONTEXT );
            }
        });
        item.setToolTipText("bind the plot's context property to the application timerange, for example when browsing histograms of data.");
        editPlotMenu.add(item);

//        item = new JMenuItem(new AbstractAction("Move this plot below others") {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                LoggerManager.logGuiEvent(e);
//                int i= application.plots.indexOf(domPlot);
//                Application ap= (Application)getApplication().copy();
//                List<Plot> pps= new ArrayList<>( Arrays.asList(ap.getPlots()) );
//                Plot pp= pps.remove(i);
//                pps.add(0, (Plot)pp.copy() );
//                ap.setPlots(pps.toArray(new Plot[pps.size()]));
//                application.controller.reset();
//                application.syncTo(ap);
//                AutoplotUtil.reloadAll(application);
//            }
//        });
//        item.setToolTipText("Move this plot below others, so that it is drawn before.");
//        editPlotMenu.add(item);
    }

    /**
     * return the Plot corresponding to the das2 component.  
     * @param c Das2 component such as DasPlot or DasAxis.
     * @return 
     */
    public Plot getPlotFor(Component c) {
        Plot plot1 = null;
        for (Plot p : application.getPlots()) {
            if ( p.controller!=null ) {
                DasPlot p1 = p.controller.getDasPlot();
                if ( p1!=null && ( p1 == c || p1.getXAxis() == c || p1.getYAxis() == c ) ) {
                    plot1 = p;
                    break;
                }
                if (p.controller.getDasColorBar() == c) {
                    plot1 = p;
                    break;
                }
            } else {
                logger.warning("application contains plot without controller (rte_0492573640)");
            }
        }
        return plot1;
    }

    public PlotElement doplot(Plot plot, PlotElement pele, String secondaryUri, String teriaryUri, String primaryUri) {
        return support.plot(plot, pele, secondaryUri, teriaryUri, primaryUri);
    }

    public PlotElement doplot(Plot plot, PlotElement pele, String secondaryUri, String primaryUri) {
        return support.plot(plot, pele, secondaryUri, primaryUri);
    }

    public PlotElement doplot(Plot plot, PlotElement pele, String primaryUri) {
        return support.plot(plot, pele, primaryUri);
    }

    /**
     * provide method for plotting a URI without any axis resetting.
     * @param suri the URI to plot
     * @param resetPlot 
     */
    public void plotUri( final String suri, final boolean resetPlot ) {
        DomLock lock=null;
        if ( !resetPlot ) {
            lock= application.controller.changesSupport.mutatorLock();
            lock.lock("plotUriWithoutChanges");
        }
        //registerPendingChange( this, PENDING_CHANGE_REPLOTURI );
        //performingChange( this, PENDING_CHANGE_REPLOTURI );
        DataSourceFilter dsf= getDataSourceFilter();
        if ( dsf==null ) {
            logger.warning("dsf is null, doing nothing");
        } else {
            dsf.getController().setSuri(suri, new NullProgressMonitor() );
            //dsf.getController().resolveDataSource( false, new NullProgressMonitor() );
            dsf.getController().update(true);
            //changePerformed( this, PENDING_CHANGE_REPLOTURI );
        }
        if ( !resetPlot ) {
            assert lock!=null;
            lock.unlock();
        }
    }
    
    /**
     * provide method for plotting a URI without any axis resetting, at a 
     * given position.
     * @param position plot the URI at the position
     * @param suri the URI to plot
     * @param resetPlot 
     */
    public void plotUri( final int position, final String suri, final boolean resetPlot ) {
        DomLock lock=null;
        if ( !resetPlot ) {
            lock= application.controller.changesSupport.mutatorLock();
            lock.lock("plotUriWithoutChanges");
        }
        DataSourceFilter dsf= application.getDataSourceFilters(position);
        dsf.getController().setSuri(suri, new NullProgressMonitor() );
        dsf.getController().update(true);
        if ( !resetPlot ) {
            assert lock!=null;
            lock.unlock();
        }
    }
    
    /**
     * provide method for plotting a dataset without any axis resetting, at a 
     * given position.
     * @param position plot the URI at the position
     * @param ds the URI to plot
     * @param resetPlot 
     */
    public void plotUri( final int position, final QDataSet ds, final boolean resetPlot ) {
        model.setDataSet(position, null, ds, resetPlot);
    }
    
    /**
     * block the calling thread until the application is idle.
     * @see isPendingChanges.
     */
    public void waitUntilIdle() {
        int checkCount=0;
        Logger domLogger= org.das2.util.LoggerManager.getLogger( "autoplot.dom" );
        while (this.isPendingChanges()) {
            checkCount++;
//            Thread.yield(); // this is not a good idea.
            try {
                if ( domLogger.isLoggable(Level.FINE) ) { // otherwise the logging channel will be saturated.
                    Thread.sleep(470);
                }
                Thread.sleep(30);    
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
        logger.log(Level.FINE, "waitUntilIdle checkCount={0}", checkCount);
    }

    /**
     * add a DataSourceFilter to the dom.
     * @return the new DataSourceFilter
     */
    public synchronized DataSourceFilter addDataSourceFilter() {
        DataSourceFilter dsf = new DataSourceFilter();
        DataSourceController dsfc= new DataSourceController(this.model, dsf);
        dsf.controller = dsfc;
        assignId(dsf);
        List<DataSourceFilter> dataSourceFilters = new ArrayList<>(Arrays.asList(this.application.getDataSourceFilters()));
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
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                LoggerManager.logPropertyChangeEvent(evt);  
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
        this.application.addPropertyChangeListener(Application.PROP_PLOT_ELEMENTS, (PropertyChangeEvent evt) -> {
            LoggerManager.logPropertyChangeEvent(evt);
            if ( application.getPlotElements().length>1 ) {
                application.options.setLayoutVisible(true);
            }
        });

        this.application.addPropertyChangeListener(Application.PROP_BINDINGS, (PropertyChangeEvent evt) -> {
            LoggerManager.logPropertyChangeEvent(evt);
            if ( isValueAdjusting() ) {
                return;
            }
            
            //List<String> ss= new ArrayList<String>();
            
            // is anyone listening to timerange?
            boolean noOneListening= true;
            BindingModel[] bms= application.getBindings();
            for (BindingModel bm : bms) {
                if (bm.getSrcId().equals(application.getId()) && bm.srcProperty.equals(Application.PROP_TIMERANGE)) {
                    noOneListening= false;
                    //ss.add( bms[i].getDstId() );
                }
            }
            
            if ( noOneListening ) {
                logger.fine("we used to reset application to default time range");
                //application.setTimeRange( Application.DEFAULT_TIME_RANGE );
            }
        });
    }

    /**
     * add a canvas to the application.  Currently, only one canvas is supported, so this
     * will have unanticipated effects if called more than once.
     *
     * This must be public to provide access to org.autoplot.ApplicationModel
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

        dasCanvas.setName( "das_"+lcanvas.getId() );        
        
        new CanvasController(application, lcanvas).setDasCanvas(dasCanvas);

        new RowController( this, lcanvas.getMarginRow() ).createDasPeer( lcanvas, null );
        new ColumnController( this, lcanvas.getMarginColumn() ).createDasPeer( lcanvas, null );

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
     * delete the plot element completely, or if it is the last, then empty the data source.
     * Earlier versions of this would throw an exception if the last plotElement was deleted.
     * @param pelement
     */
    public void deletePlotElement(PlotElement pelement) {
        logger.log(Level.FINE, "deletePlotElement({0})", pelement);
        DomLock lock= changesSupport.mutatorLock();
        lock.lock("Delete Plot Element");
        int currentIdx = application.plotElements.indexOf(pelement);
        if (currentIdx == -1) {
            logger.warning("deletePlotElement but plot element isn't part of application, ignoring");
            lock.unlock(); //TODO: review this, maybe try/finally...
            return;
        }
        try {
            String id= pelement.getId();
            if (application.plotElements.size() > 1 ) {

                DasPlot p = pelement.controller.getDasPlot();
                Renderer r= pelement.controller.getRenderer();
                if (p != null && r != null ) {
                    p.removeRenderer(r);    
                }
                Plot domplot= getPlotFor(pelement);

                //plotElement.removePropertyChangeListener(application.childListener);
                pelement.removePropertyChangeListener(domListener);
                pelement.getStyle().removePropertyChangeListener(domListener);
                unbind(pelement);
                unbind(pelement.getStyle());
                unbindImpl(pelement); //TODO: I need to remind myself why there are two types of bindings...
                unbindImpl(pelement.getStyle());
                pelement.controller.unbindDsf();
                pelement.controller.disconnect();
                pelement.controller.dataSet= null; // get rid of these for now, until we can figure out why these are not G/C'd.
                if ( r!=null ) r.setColorBar(null);
                
                if ( domplot!=null ) {
                    domplot.controller.pdListen.remove(pelement);
                }
                if ( r!=null ) r.setDataSet(null);
                pelement.controller.deleted= true;
                pelement.controller.renderer=null;
                //pelement.controller.changesSupport=null;
                pelement.removePropertyChangeListener(plotIdListener);
                
                PlotElement parent= pelement.controller.getParentPlotElement();
                if ( parent!=null ) {
                    parent.getStyle().removePropertyChangeListener( pelement.controller.parentStyleListener );
                }

                pelement.controller.removeReferences();
                //pelement.controller= null; // causes problems to delete...

            }

            DataSourceFilter dsf = getDataSourceFilterFor(pelement);

            ArrayList<PlotElement> elements =
                    new ArrayList<>(Arrays.asList(application.getPlotElements()));
            elements.remove(pelement);
            PlotElement selected= getPlotElement();
            if ( elements.size()>0 ) {
                if ( selected!=null && !elements.contains(selected)) {  // reset the focus element Id
                    if (elements.isEmpty()) {
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
                List<Plot> plotsUsing= getPlotsFor(dsf);
                if ( plotsUsing.isEmpty() && application.getDataSourceFilters().length>1 ) {
                    deleteDataSourceFilter(dsf);
                }
            }
            for ( PlotElement p: application.plotElements ) {
                if ( p.getParent().equals(id) ) {
                    p.setParent("");
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * adds a context overview plotId below the plotId.
     * @param upperPlot the upper plot
     * @param lowerPlot the lower plot
     */
    public void addConnector(Plot upperPlot, Plot lowerPlot) {
        logger.log( Level.FINE, "addConnector({0},{1})", new Object[]{upperPlot, lowerPlot});
        List<Connector> connectors = new ArrayList(Arrays.asList(application.getConnectors()));
        final Connector connector = new Connector(upperPlot.getId(), lowerPlot.getId());
        connectors.add(connector);
        connector.setController( new ConnectorController(application, connector) );

        application.setConnectors(connectors.toArray(new Connector[connectors.size()]));

        DasCanvas lcanvas = getDasCanvas();
        DasPlot upper = upperPlot.controller.getDasPlot();
        DasPlot lower = lowerPlot.controller.getDasPlot();

        //overviewPlotConnector.getDasMouseInputAdapter().setPrimaryModule(overviewZoom);
        ColumnColumnConnector overviewPlotConnector =
                new ColumnColumnConnector(lcanvas, upper,
                DasRow.create(null, upper.getRow(), "0%", "100%+2em"), lower);

        connectorImpls.put(connector, overviewPlotConnector);

        overviewPlotConnector.setBottomCurtain(true);
        overviewPlotConnector.setCurtainOpacityPercent(80);

        connector.getController().bindTo(overviewPlotConnector);
        
        MouseModule mm = new ColumnColumnConnectorMouseModule(upper, lower);
        overviewPlotConnector.getDasMouseInputAdapter().setSecondaryModule(mm);
        overviewPlotConnector.getDasMouseInputAdapter().setPrimaryModule(mm);

        lcanvas.add(overviewPlotConnector);
        lcanvas.revalidate();

    }

    /**
     * delete the connector between two plot X axes.
     * @param connector 
     */
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
        connector.getController().removeBindings();
        
        application.setConnectors(connectors.toArray(new Connector[connectors.size()]));
    }

    /**
     * experiment with Jython-friendly method where the annotation is 
     * instantiated within the script.  This allows code like:
     *
     *<blockquote><pre><small>{@code
     * ann= Annotation( text='Feature', pointAtX=datum(tt), pointAtY=datum('100nT'), showArrow=True )
     *}</small></pre></blockquote>
     * 
     * @param annotation
     * @return the annotation, configured.
     */
    public Annotation addAnnotation( final Annotation annotation ) {
        
        if ( annotation.getPlotId().length()==0 ) {
            annotation.setPlotId( getApplication().getPlots(0).getId() );
        }
        
        Plot p= (Plot)DomUtil.getElementById( getApplication(), annotation.getPlotId() );
        
        Row r;
        if ( annotation.getRowId().length()>0 ) {
            r= (Row)DomUtil.getElementById( getApplication().getCanvases(0), annotation.getRowId() );
            if ( r==null ) {
                logger.log(Level.WARNING, "unable to find row with id=\"{0}\"", annotation.getRowId());
                if ( p==null ) {
                    r= application.getCanvases(0).getMarginRow();
                } else {
                    r= p.getController().getRow();
                }
            }
        } else {
            if ( p==null ) {
                r= application.getCanvases(0).getMarginRow();
            } else {
                r= p.getController().getRow();
            }
            
        }
        Column c;
        if ( annotation.getRowId().length()>0 ) {
            c= (Column)DomUtil.getElementById( getApplication().getCanvases(0), annotation.getColumnId() );
            if ( c==null ) {
                logger.log(Level.WARNING, "unable to find column with id=\"{0}\"", annotation.getColumnId());
                if ( p==null ) {
                    c= application.getCanvases(0).getMarginColumn();
                } else {
                    c= p.getController().getColumn();
                }
            }
        } else {
            if ( p==null ) {
                c= application.getCanvases(0).getMarginColumn();
            } else {
                c= p.getController().getColumn();
            }
        }
                
        final DasAnnotation impl= new DasAnnotation("");
        
        assignId(annotation);
        
        if ( p!=null ) {
            impl.setPlot( p.controller.getDasPlot() );
        }
                
        JMenuItem  mi= new JMenuItem(new AbstractAction("Annotation Properties") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                PropertyEditor pp = new PropertyEditor(annotation);
                pp.showDialog(application.getCanvases(0).getController().getDasCanvas());
            }
        });        
        impl.getDasMouseInputAdapter().addMenuItem(mi);
        
        mi= new JMenuItem(new AbstractAction("Annotation Editor") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                AnnotationEditorPanel pp= new AnnotationEditorPanel();
                Annotation ann0= (Annotation)annotation.copy();
                pp.doBindings(annotation);
                Component parent= application.getCanvases(0).getController().getDasCanvas();
                if ( JOptionPane.CANCEL_OPTION == AutoplotUtil.showConfirmDialog( parent, pp, "Edit Annotation", JOptionPane.OK_CANCEL_OPTION ) ) {
                    annotation.syncTo(ann0);
                }
                pp.releaseBindings();
            }
        });        
        impl.getDasMouseInputAdapter().addMenuItem(mi);
                
        mi= new JMenuItem(new AbstractAction("Delete Annotation") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                deleteAnnotation(annotation);
            }
        });        
        impl.getDasMouseInputAdapter().addMenuItem(mi);
        
        final JCheckBoxMenuItem cbmi= new JCheckBoxMenuItem(new AbstractAction("Anchor to Data") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                if ( ((JCheckBoxMenuItem)e.getSource()).isSelected() ) {
                    if ( annotation.isShowArrow() ) {
                        Datum x= annotation.getPointAtX();
                        Datum y= annotation.getPointAtY();
                        annotation.setXrange( DatumRangeUtil.union(x,x) );
                        annotation.setYrange( DatumRangeUtil.union(y,y) );
                        //annotation.setAnchorPosition(AnchorPosition.W);
                        double ix= plot.getController().getDasPlot().getXAxis().transform( x );
                        double iy= plot.getController().getDasPlot().getYAxis().transform( y );
                        double em= 12; //plot.getFontSize();

                        Rectangle r= impl.getActiveRegion().getBounds();
                        int rx= r.x + r.width/2;
                        int ry= r.y + r.height/2;
                        String anchorOffset;
                        switch (annotation.getAnchorPosition()) {
                            case NE:
                                anchorOffset = String.format( "%fem,%fem", -1*(rx-ix)/em, (ry-iy)/em );
                                break;
                            case NW:
                                anchorOffset = String.format( "%fem,%fem", (rx-ix)/em, (ry-iy)/em );
                                break;
                            case SW:
                                anchorOffset = String.format( "%fem,%fem", (rx-ix)/em, -1*(ry-iy)/em );
                                break;
                            case SE:
                                anchorOffset = String.format( "%fem,%fem", -1*(rx-ix)/em, -1*(ry-iy)/em );
                                break;
                            default:
                                anchorOffset = annotation.getAnchorOffset();
                                break;
                        }
                        annotation.setAnchorOffset( anchorOffset );
                    } else {
                        Rectangle r= impl.getActiveRegion().getBounds();
                        Datum x1= plot.getController().getDasPlot().getXAxis().invTransform(r.x);
                        Datum y1= plot.getController().getDasPlot().getYAxis().invTransform(r.y);
                        Datum x2= plot.getController().getDasPlot().getXAxis().invTransform(r.x+r.width);
                        Datum y2= plot.getController().getDasPlot().getYAxis().invTransform(r.y+r.height);                   
                        annotation.setXrange( DatumRangeUtil.union(x1,x2) );
                        annotation.setYrange( DatumRangeUtil.union(y1,y2) );
                        //annotation.setAnchorPosition(AnchorPosition.W);
                        annotation.setAnchorOffset("");
                    }
                    annotation.setAnchorType(AnchorType.DATA);
                } else {
                    annotation.setAnchorType(AnchorType.CANVAS);
                    annotation.setAnchorOffset("1em,1em");
                    annotation.setAnchorPosition(AnchorPosition.NE);
                }
            }
        }); 
        impl.getDasMouseInputAdapter().addMenuItem(cbmi);
        
        bind( annotation, "anchorType", cbmi, "selected", new Converter() {
            @Override
            public Object convertForward(Object s) {
                return !s.equals( AnchorType.CANVAS );
            }
            @Override
            public Object convertReverse(Object t) {
                if ( ((Boolean)t) ) return AnchorType.DATA; else return AnchorType.CANVAS;
            }
        } ); 
        
        impl.getDasMouseInputAdapter().addMenuItem(mi);
        impl.getDasMouseInputAdapter().removeMenuItem("Properties");
        impl.getDasMouseInputAdapter().removeMenuItem("remove arrow");
        impl.getDasMouseInputAdapter().removeMenuItem("remove");
        impl.getDasMouseInputAdapter().setSecondaryModuleByLabel("Move Annotation");
        
        annotationImpls.put(annotation, impl);
        
        List<Annotation> annotations=  DomUtil.asArrayList( application.getAnnotations() );
        annotations.add( annotation );
        
        application.setAnnotations( annotations.toArray( new Annotation[annotations.size()]) );
        
        DasCanvas lcanvas = getDasCanvas();

        annotation.setColumnId( c.getId() );
        annotation.setRowId( r.getId() );
        
        lcanvas.add( impl, r.controller.dasRow, c.controller.dasColumn );
        new AnnotationController( application, annotation, impl );
                        
        return annotation;
    }
    
    /**
     * add an annotation to the plot
     * @param p the plot
     * @param text initial text
     * @return the annotation
     */
    public Annotation addAnnotation( Plot p, String text ) {
        Row r= p.getController().getRow();
        Column c= p.getController().getColumn();
        
        Annotation a= addAnnotation( r, c, text );
        
        a.setPlotId( p.getId() );
            
        return a;
    }
    
    /**
     * add an annotation to the canvas.
     * @param row the row or null.
     * @param column the column or null.
     * @param text initial text
     * @return the annotation
     */
    public Annotation addAnnotation( Row row, Column column, String text ) {
        
        if ( row==null ) row= application.getCanvases(0).getMarginRow();
        if ( column==null ) column= application.getCanvases(0).getMarginColumn();
                    
        final Annotation annotation= new Annotation();
        annotation.setRowId( row.getId() );
        annotation.setColumnId( column.getId() );
        annotation.setText(text);
        
        addAnnotation(annotation);
        
        return annotation;
    }

    /**
     * delete the annotation
     * @param c the annotation
     */
    public void deleteAnnotation(Annotation c) {
        logger.log( Level.FINE, "deleteAnnotation({0})", c);
        
        DasAnnotation impl = annotationImpls.get(c);
        
        if ( impl!=null ) {
            getDasCanvas().remove(impl);
        } else {
            
        }

        List<Annotation> annotations = DomUtil.asArrayList(application.getAnnotations());
        annotations.remove(c);

        annotationImpls.remove(c);
        c.getController().removeBindings();
        c.controller.node= null;
        c.controller=null;
        
        application.setAnnotations(annotations.toArray(new Annotation[annotations.size()]));
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

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt,"plotIdListener");  
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
                assert src!=null;
                src.getController().removePlotElement(p);
                return;
            }
            if ( src != null ) {
                movePlotElement(p, src, dst);
                if (getPlotElementsFor(dst).size() == 1) {
                    dst.syncTo(p.plotDefaults, Arrays.asList( DomNode.PROP_ID, Plot.PROP_ROWID, Plot.PROP_COLUMNID, Plot.PROP_XAXIS, Plot.PROP_YAXIS ) );
                    List<BindingModel> bb= findBindings( dst.getXaxis(), Axis.PROP_RANGE );
                    if ( bb.isEmpty() ) {
                        dst.getXaxis().syncTo( p.getPlotDefaults().getXaxis(), Arrays.asList(Plot.PROP_ID) );
                    }
                    bb= findBindings( dst.getYaxis(), Axis.PROP_RANGE );
                    if ( bb.isEmpty() ) {
                        dst.getYaxis().syncTo( p.getPlotDefaults().getYaxis(), Arrays.asList(Plot.PROP_ID) );
                    }
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
        return addPlotElement( domPlot, null, dsf );
    }

    /**
     * add a plotElement to the application, attaching it to the given Plot and DataSourceFilter.
     * @param domPlot if null, create a Plot, if non-null, add the plotElement to this plot.
     * @param parent if non-null, then make this plotElement the child of parent.
     * @param dsf if null, create a DataSourceFilter.  If non-null, connect the plotElement to this data source.
     * @return
     */
    public PlotElement addPlotElement( Plot domPlot, PlotElement parent, DataSourceFilter dsf) {
        logger.log(Level.FINE, "enter addPlotElement({0},{1})", new Object[]{domPlot, dsf});

        final PlotElement pele1 = new PlotElement();

        if (dsf == null) {
            dsf = addDataSourceFilter();
        }

        PlotElementController pec= new PlotElementController(this.model, application, pele1);
        pele1.controller = pec;

        if (domPlot == null) {
            domPlot = addPlot(LayoutConstants.BELOW);
            domPlot.setColortable( application.getOptions().getColortable() );
        }

        assignId(pele1);

        pele1.getStyle().setColor(application.getOptions().getColor());
        pele1.getStyle().setFillColor(application.getOptions().getFillColor());
        pele1.getStyle().setColortable(application.getOptions().getColortable());
        pele1.getStyle().setAntiAliased(application.getOptions().isDrawAntiAlias());
        
        pele1.addPropertyChangeListener(PlotElement.PROP_PLOTID, plotIdListener);

        if ( parent!=null ) {
            pele1.setParent( parent.getId() );
            pele1.setRenderType(parent.getRenderType());
        }
        
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

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt,"renderFocusListener");  
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
                logger.fine("unable to find the plot element, assuming transitional state...");
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

    /**
     * add a plot to the canvas.  Direction is with respect to given
     * focus plot, and currently only LayoutConstants.ABOVE and LayoutConstants.BELOW
     * are supported.
     * 
     * @param focus the current focus plot.
     * @param direction LayoutConstants.ABOVE, LayoutConstants.BELOW, or null.  
     * Null indicates the layout will be done elsewhere, and the new plot will
     * be on top of the old.
     * @return
     */
    public synchronized Plot addPlot( final Plot focus, Object direction ) {

        if ( !SwingUtilities.isEventDispatchThread() ) {
            logger.fine("SF Bug 3516412: addPlot is called off the event thread!!!");
        }

        logger.fine("enter addPlot");
        DomLock lock= changesSupport.mutatorLock();
        lock.lock("Add Plot");
        
        final Plot domPlot = new Plot();
        try {

            final Canvas c= getCanvas();

            CanvasController ccontroller=  ((CanvasController)c.controller);
            Row domRow;

            if ( focus!=null && ccontroller.getRowFor(focus)==c.marginRow ) { 
                String srcRow;
                if ( c.getRows().length>0 ) {
                    srcRow= c.getRows(0).getId();
                } else {
                    ccontroller.addRows(2);
                    srcRow= c.getRows(0).getId();
                }
                if ( c.getRows().length>1 ) {
                    domRow= c.getRows(1);
                } else {
                    domRow = ccontroller.addInsertRow( ccontroller.getRowFor(focus), direction );
                }
                focus.setRowId(srcRow);
            } else if ( c.getRows().length == 0 && ( direction==LayoutConstants.BELOW || direction==LayoutConstants.ABOVE ) ) {
                domRow = ccontroller.addRow();
            } else if ( direction==null || direction==LayoutConstants.LEFT || direction==LayoutConstants.RIGHT ) {
                domRow= ccontroller.getRowFor(focus);
            } else {
                domRow = ccontroller.addInsertRow( ccontroller.getRowFor(focus), direction);
            }

            Column domColumn;

            // the logic for columns is different because we optimize the application for a stack of time
            // series.
            if ( direction==null || direction==LayoutConstants.ABOVE || direction==LayoutConstants.BELOW ) {
                if ( focus!=null ) {
                    domColumn= ccontroller.getColumnFor(focus);
                } else {
                    domColumn= c.marginColumn;
                }
            } else {
                if ( focus!=null && ccontroller.getColumnFor(focus)==c.marginColumn ) {
                    String srcColumn;
                    if ( c.getColumns().length>0 ) {
                        srcColumn= c.getColumns(0).getId();
                    } else {
                        ccontroller.addColumns(2);
                        srcColumn= c.getColumns(0).getId();
                    }
                    if ( c.getColumns().length>1 ) {
                        domColumn= c.getColumns(1);
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

            Runnable run= () -> {
                new PlotController(application, domPlot).createDasPeer(c, frow ,fcol ); 
            };
            if ( SwingUtilities.isEventDispatchThread() ) {
                run.run();
            } else {
                try {
                    SwingUtilities.invokeAndWait(run);
                } catch ( InterruptedException | InvocationTargetException ex ) {
                    logger.log( Level.WARNING, ex.getMessage(), ex );
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

            List<Plot> plots = new ArrayList<>(Arrays.asList(application.getPlots()));

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
        } finally {
            lock.unlock();
        }
        return domPlot;
    }

    /**
     * add a plot to the canvas to the row and column.
     * @param row row for the plot
     * @param column column for the plot
     * @return the new plot.
     */
    public Plot addPlot(Row row, Column column) {
        Plot p= addPlot(null);
        p.setRowId( row.getId() );
        p.setColumnId( column.getId() );
        return p;
    }

    /**
     * add a floating plot at arbitrary position.
     * @param xpos the new column e.g. "10%,90%"
     * @param ypos the new row e.g. "10%+6em,100%-6em"
     * @return 
     */
    public Plot addPlot( String xpos, String ypos ) {
        DomLock lock = mutatorLock();
        lock.lock( "addPlot" );
        try {
            final CanvasController ccontroller = getCanvas().getController();
            Column c= ccontroller.addColumn();
            String[] ss;
            ss= xpos.split(",");
            c.setLeft(ss[0]);
            c.setRight(ss[1]);
            Row r= ccontroller.addRow();
            ss= ypos.split(",");
            r.setTop(ss[0]);
            r.setBottom(ss[1]);
            Plot p = addPlot(r,c); 
            addPlotElement(p, null);
            return p;
        } finally {
            lock.unlock();
        }
    }
        
    
    /**
     * adds a block of plots to the canvas below the focus plot.  A plotElement
     * is added for each plot as well.
     * @param nrow number of rows
     * @param ncol number of columns
     * @param dir LayoutConstants.ABOVE, LayoutConstants.BELOW or null.  Null means use the current row.  RIGHT and LEFT for the margin column.
     * @return a list of the newly added plots.
     */
    public List<Plot> addPlots( int nrow, int ncol, Object dir ) {        
        boolean isMarginColumn= plot.getColumnId().equals(application.controller.canvas.marginColumn.id) ;
        if ( ! isMarginColumn && ( dir==LayoutConstants.LEFT || dir==LayoutConstants.RIGHT ) ) {
            throw new IllegalArgumentException("addPlots can only be done with original margin column when dir is "+dir);
        }
        DomLock lock = mutatorLock();
        lock.lock( String.format("addPlots(%d,%d,%s)",nrow,ncol,dir) );
        try {
            List<Plot> result= new ArrayList<>(nrow*ncol);
            List<Column> cols;
            final CanvasController ccontroller = getCanvas().getController();
            if ( dir==LayoutConstants.RIGHT || dir==LayoutConstants.LEFT ) {
                if ( isMarginColumn ) {
                    cols = ccontroller.addColumns(ncol+1);
                } else {
                    cols = ccontroller.addColumns(ncol);
                    //TODO:  ccontroller.addColumns(ncol,'LEFT')
                }
            } else {
                if (ncol > 1) {
                    cols = ccontroller.addColumns(ncol);
                } else {
                    cols = Collections.singletonList(getCanvas().getMarginColumn());
                }
            }
            List<Row> rows;
            if ( dir==null && nrow==1 ) {
                rows = Collections.singletonList(ccontroller.getRowFor(plot));
            } else {
                if ( dir==null ) {
                    rows = ccontroller.addRows(nrow,LayoutConstants.BELOW);
                } else {
                    if ( dir==LayoutConstants.RIGHT || dir==LayoutConstants.LEFT ) {
                        rows = ccontroller.addRows(nrow,dir);
                        CanvasController.removeGapsAndOverlaps( application, rows, null, false );
                    } else {
                        rows = ccontroller.addRows(nrow,dir);
                    }
                }
            }
            for (int i = 0; i < nrow; i++) {
                for (int j = 0; j < ncol; j++) {
                    Column col;
                    if ( dir==LayoutConstants.ABOVE || dir==LayoutConstants.BELOW || dir==LayoutConstants.LEFT ) {
                        col= cols.get(j);
                    } else if ( dir==LayoutConstants.RIGHT ) {
                        col= cols.get(j+1);
                    } else {
                        col= cols.get(j);
                    }
                    Plot p = addPlot(rows.get(i), col);
                    result.add(p);
                    addPlotElement(p, null);
                }
            }
            if ( isMarginColumn ) {
                if ( dir==LayoutConstants.RIGHT ) {
                    plot.setColumnId(cols.get(0).id);
                } else if ( dir==LayoutConstants.LEFT ) {
                    plot.setColumnId(cols.get(ncol).id);
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
     * copy plot and plotElements into a new plot.
     * @param domPlot copy this plot.
     * @param dsf if non-null, then use this dataSourceFilter
     * @param bindx If true, X axes are bound.  If the srcPlot x axis is bound to the
     *    application timerange, then bind to that instead (kludge--handle higher)
     * @param bindy If true, Y axes are bound
     * @return the new plot
     */
    public Plot copyPlotAndPlotElements(Plot domPlot, DataSourceFilter dsf, boolean bindx, boolean bindy) {
        List<PlotElement> srcElements = getPlotElementsFor(domPlot);
        List<PlotElement> newElements;
        Plot newPlot;
        DomLock lock = mutatorLock();
        lock.lock("Copy Plot and Plot Elements");
        try {

            newPlot= copyPlot(domPlot, bindx, bindy, false);
            if (srcElements.isEmpty()) {
                return newPlot;
            }
            
            Map<String,String> parentMap= new HashMap<>();

            newElements = new ArrayList<>();
            for (PlotElement srcElement : srcElements) {
                PlotElement newp = copyPlotElement(srcElement, newPlot, dsf);
                newElements.add(newp);
                if ( srcElement.getController().getParentPlotElement()==null ) {
                    parentMap.put( srcElement.getId(), newp.getId() );
                }
            }

            for (PlotElement newp : newElements) {
                if ( newp.getParent().trim().length()>0 ) {
                    String oldParent= newp.getParent().trim();
                    String newParent= parentMap.get(oldParent);
                    if ( newParent!=null ) {
                        newp.setParent(newParent);
                    }
                }
                newp.getController().setResetRanges(false);
                newp.getController().setResetComponent(false);
                newp.getController().setResetPlotElement(false);
                newp.getController().setResetRenderType(false);
                newp.getController().setDsfReset(true);
            }
            
            List<BindingModel> bms= findBindings( domPlot, Plot.PROP_CONTEXT );
            for ( BindingModel bm: bms ) {
                if ( bm.srcId.equals(domPlot.id) ) {
                    DomNode other= DomUtil.getElementById( node, bm.dstId );
                    if ( other!=null ) bind( newPlot, Plot.PROP_CONTEXT, other, bm.dstProperty );
                } else {
                    DomNode other= DomUtil.getElementById( node, bm.srcId );
                    if ( other!=null ) bind( other, bm.srcProperty, newPlot, Plot.PROP_CONTEXT );
                }
            }
            
            
        } finally {
            lock.unlock();
        }

        return newPlot;

    }

    /**
     * copy the plotElement and put it in domPlot.
     * @param srcElement the plotElement to copy.
     * @param domPlot  plot to contain the new plotElement, which may be the same plot.
     * @param dsf     if non-null, then use this dataSourceFilter
     * @return the new plotElement.
     */
    protected PlotElement copyPlotElement(PlotElement srcElement, Plot domPlot, DataSourceFilter dsf) {
        logger.log( Level.FINER, "copyPlotElement({0},{1},{2})", new Object[]{srcElement, domPlot, dsf});
        PlotElement newp = addPlotElement(domPlot, dsf);
        newp.getController().setResetPlotElement(false);// don't add children, trigger autoRange, etc.
        newp.getController().setResetRanges(false);
        newp.getController().setDsfReset(false); // don't reset when the dataset changes
        newp.syncTo(srcElement, Arrays.asList(DomNode.PROP_ID,PlotElement.PROP_PLOTID,
                PlotElement.PROP_DATASOURCEFILTERID));
        newp.getController().setResetRanges(false); // in case the renderType changed, we still don't need to reset.
        if (dsf == null) { // new DataSource, but with the same URI.
            DataSourceFilter dsfnew = newp.controller.getDataSourceFilter();
            DataSourceFilter dsfsrc = srcElement.controller.getDataSourceFilter();
            copyDataSourceFilter(dsfsrc, dsfnew);
        }
        return newp;
    }

    /**
     * copy the plotElement and put it in domPlot, but make it a child immediately.  This was introduced because
     * of code that was responding to changes, but didn't know that these were child elements.
     * @param srcElement the plotElement to copy.
     * @param domPlot  plot to contain the new plotElement, which may be the same plot.
     * @param dsf     if non-null, then use this dataSourceFilter
     * @return the new plotElement.
     */
    protected PlotElement makeChildPlotElement( PlotElement srcElement, Plot domPlot, DataSourceFilter dsf) {
        logger.log( Level.FINER, "makeChildPlotElement({0},{1},{2})", new Object[]{srcElement, domPlot, dsf});
        PlotElement newp = addPlotElement(domPlot, srcElement, dsf);
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
    };

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
     * @see DomOps#copyPlotAndPlotElements(org.autoplot.dom.Plot, boolean, boolean, boolean, java.lang.Object) 
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
            that.xaxis.setAutoRange( false );
        }

        if (bindy) {
            bind(srcPlot.getYaxis(), Axis.PROP_RANGE, that.getYaxis(), Axis.PROP_RANGE);
            that.yaxis.setAutoRange( false );
        }

        return that;
    }

    /**
     * copy the dataSourceFilter, including its controller and loaded data.
     * @param dsfsrc the source dataSourceFilter
     * @param dsfnew the new dataSourceFilter, which will get the URI and loaded data.
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
                dsfnew.controller.resetDataSource(true,dsfsrc.controller.getDataSource());
                dsfnew.controller.setDataSetNeedsLoading( false );
                dsfnew.controller.setResetDimensions(false);
                dsfnew.controller.setDataSetInternal(dsfsrc.controller.getDataSet(),
                        dsfsrc.controller.getRawProperties(),isValueAdjusting()); // fire off data event.
                dsfnew.controller.setProperties(dsfsrc.controller.getProperties());
                dsfnew.setFilters( dsfsrc.getFilters() );
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * delete the named plot, plotElement, annotation, or dataSource
     * @param id node name like "plot_5", see dom.plots[0].id.
     * @throws IllegalArgumentException if the node can't be found.
     * @throws IllegalArgumentException if the node type is not supported.
     */
    public void delete(String id) {
        DomNode n= getElementById(id);
        if ( n==null ) {
            throw new IllegalArgumentException("no dom node found with the name: "+id);
        }
        delete(n);
    }
    
    /**
     * delete the dom node.
     * @param n 
     */
    public void delete(DomNode n) {
        if ( n instanceof Plot ) {
            deletePlot((Plot)n);
        } else if ( n instanceof PlotElement ) {
            deletePlotElement((PlotElement)n);
        } else if ( n instanceof DataSourceFilter ) {
            deleteDataSourceFilter((DataSourceFilter)n);
        } else if ( n instanceof Annotation ) {
            deleteAnnotation((Annotation)n);
        } else if ( n instanceof Connector ) {
            deleteConnector((Connector)n);
        } else {
            throw new IllegalArgumentException("node type is not supported: "+n);
        }        
    }
    
    /**
     * delete the plot from the application.
     * TODO: this should really call the plot.controller.deleteDasPeer()
     * @param domPlot
     */
    public void deletePlot(Plot domPlot) {
        if ( domPlot==null ) throw new NullPointerException("plot is null");
        DomLock lock= changesSupport.mutatorLock();
        lock.lock("Delete Plot");
        try {
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
                    deleteConnector(c); // TODO: this has remove that occurs off the event thread.
                }
            }

            Row deleteRow = null; // if non-null, delete this row.
            Row row = (Row) DomUtil.getElementById(application, domPlot.getRowId());
            if ( row!=null ) { // leftover bug from "Add Hidden Plot"
                List<DomNode> plotsUsingRow = DomUtil.rowUsages(application, row.getId());
                plotsUsingRow.remove(domPlot);
                if (plotsUsingRow.isEmpty()) {
                    deleteRow = row;
                }
            }

            Column deleteColumn = null; // if non-null, delete this row.
            Column column = (Column) DomUtil.getElementById(application, domPlot.getColumnId());
            if ( column!=null ) { // leftover bug from "Add Hidden Plot"
                List<DomNode> plotsUsingColumn = DomUtil.columnUsages(application, column.getId());
                plotsUsingColumn.remove(domPlot);
                if (plotsUsingColumn.isEmpty()) {
                    deleteColumn = column;
                }
            }
            
            //domPlot.removePropertyChangeListener(application.childListener);
            domPlot.removePropertyChangeListener(domListener);
            unbind(domPlot);
            unbind(domPlot.getXaxis());
            unbind(domPlot.getYaxis());
            unbind(domPlot.getZaxis());
            unbindImpl(domPlot); //These are the bindings between the DOM Object and the Das2 DasPlot implementation.
            unbindImpl(domPlot.getXaxis());
            unbindImpl(domPlot.getYaxis());
            unbindImpl(domPlot.getZaxis());

            if ( domPlot.controller==null ) {
                logger.warning("domPlot.controller is null, this shouldn't happen");
            } else {
                domPlot.controller.removeBindings();
                final DasPlot p = domPlot.controller.getDasPlot();
                p.getDasMouseInputAdapter().releaseAll();
                p.getXAxis().getDasMouseInputAdapter().releaseAll();
                p.getYAxis().getDasMouseInputAdapter().releaseAll();
                final DasColorBar cb = domPlot.controller.getDasColorBar();
                cb.getDasMouseInputAdapter().releaseAll();
                final DasCanvas lcanvas= this.getDasCanvas();
                final ArrayList<Component> deleteKids= new ArrayList();
                deleteKids.add( p );
                deleteKids.add( cb );
                deleteKids.add( domPlot.controller.getDasPlot().getXAxis() );
                deleteKids.add( domPlot.controller.getDasPlot().getYAxis() );
                SwingUtilities.invokeLater(() -> {
                    for ( Component c: deleteKids ) {
                        lcanvas.remove(c);
                    }
                } ); // see https://sourceforge.net/tracker/index.php?func=detail&aid=3471016&group_id=199733&atid=970682
                cb.getColumn().removeListeners();
                
                domPlot.xaxis.controller.removeReferences();
                domPlot.yaxis.controller.removeReferences();
                domPlot.zaxis.controller.removeReferences();
                
                domPlot.xaxis.controller= null;
                domPlot.yaxis.controller= null;
                domPlot.zaxis.controller= null;
            }

            synchronized (this) {
                List<Plot> plots = new ArrayList<>(Arrays.asList(application.getPlots()));
                plots.remove(domPlot);

                if (!plots.contains(getPlot())) {
                    if (plots.isEmpty()) {
                        setPlot(null);
                    } else {
                        setPlot(plots.get(0));
                    }
                }
                application.setPlots(plots.toArray(new Plot[plots.size()]));

                if (deleteRow != null) {
                    assert row!=null;
                    CanvasController cc = row.controller.getCanvas().controller;
                    cc.deleteRow(deleteRow);
                    if ( application.getOptions().isAutolayout() ) {
                        cc.removeGaps();
                    }
                    deleteRow.getController().removeBindings();
                    deleteRow.getController().removeReferences();
                }
                
                if (deleteColumn != null) {
                    assert column!=null;
                    CanvasController cc = column.controller.getCanvas().controller;
                    cc.deleteColumn(deleteColumn);
                    deleteColumn.getController().removeBindings();
                    deleteColumn.getController().removeReferences();
                }                
            }

            if ( domPlot.controller==null ) {
                domPlot.getController().getDasPlot().releaseAll();
            }
        } finally {
            lock.unlock();
        }

    }

    /**
     * Delete the parents of this DSF node, if any exist, and if they are no longer used.
     * Do not delete the node itself.  Do not fix focus.
     * @param dsf
     */
    protected synchronized void deleteAnyParentsOfDataSourceFilter(DataSourceFilter dsf) {
        DataSourceFilter[] parents = dsf.controller.getParentSources();
        // look for orphaned parents
        List<DataSourceFilter> alsoRemove = new ArrayList<>();
        for (DataSourceFilter pdf : parents) {
            if ( pdf==null ) continue; // bad reference
            String dsfId = pdf.getId();
            List<DomNode> usages = DomUtil.dataSourceUsages(application, dsfId);
            usages.remove(dsf);
            if (usages.isEmpty()) {
                alsoRemove.add(pdf);
            }
        }

        if ( alsoRemove.size()>0 ) {
            List<DataSourceFilter> dsfs = new ArrayList<>(Arrays.asList(application.getDataSourceFilters()));
            dsfs.removeAll(alsoRemove);
            application.setDataSourceFilters(dsfs.toArray(new DataSourceFilter[dsfs.size()]));

            TimeSeriesBrowseController tsbc;
            for (DataSourceFilter alsoRemove1 : alsoRemove) {
                tsbc = alsoRemove1.controller.getTimeSeriesBrowseController();
                if ( tsbc!=null ) tsbc.release();
            }
        }
        
    }

    /**
     * delete the dsf and any parents that deleting it leaves orphaned. (??? maybe they should be called children...)
     * @param dsf
     * @see DomUtil#deleteDataSourceFilter(org.autoplot.dom.Application, org.autoplot.dom.DataSourceFilter) 
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
        List<DataSourceFilter> alsoRemove = new ArrayList<>();
        for (DataSourceFilter pdf : parents) {
            if ( pdf==null ) continue;
            String dsfId = pdf.getId();
            List<DomNode> usages = DomUtil.dataSourceUsages(application, dsfId);
            usages.remove(dsf);
            if (usages.isEmpty()) {
                alsoRemove.add(pdf);
            }
        }

        List<DataSourceFilter> dsfs = new ArrayList<>(Arrays.asList(application.getDataSourceFilters()));
        dsfs.remove(dsf);
        dsfs.removeAll(alsoRemove);

        if (!dsfs.contains(getDataSourceFilter())) {
            if (dsfs.isEmpty()) {
                setDataSourceFilter(null); //TODO: review this.  It might be better to never allow no DSFs.
            } else {
                setDataSourceFilter(dsfs.get(0));
            }
        }
        application.setDataSourceFilters(dsfs.toArray(new DataSourceFilter[dsfs.size()]));

        TimeSeriesBrowseController tsbc= dsf.getController().getTimeSeriesBrowseController();
        if ( tsbc!=null ) tsbc.releaseAll();
        for (DataSourceFilter alsoRemove1 : alsoRemove) {
            tsbc = alsoRemove1.controller.getTimeSeriesBrowseController();
            if ( tsbc!=null ) tsbc.releaseAll();
        }

    }

    /**
     * go through the monitors we keep track of, and cancel each one.
     */
    public void cancelAllPendingTasks() {
        MonitorFactory mf= application.controller.getMonitorFactory();
        if ( mf instanceof DefaultMonitorFactory ) {
            DefaultMonitorFactory dmf= (DefaultMonitorFactory)mf;
            MonitorEntry[] mes= dmf.getMonitors();
            for ( MonitorEntry me: mes ) {
                ProgressMonitor m= me.getMonitor();
                if ( !( m.isCancelled() || m.isFinished() ) ) {
                    m.cancel();
                }
            }
        }
    }
    
    
    /**
     * contain the logic which returns a reference to the AutoplotUI, so that 
     * this nasty bit of code is contained.
     * @return null or the application
     */
    public AutoplotUI maybeGetApplicatonGUI() {
        // this is probably midguided.  For instance, if the canvas is torn off then this won't work.
        Window w= SwingUtilities.getWindowAncestor( application.getCanvases(0).getController().getDasCanvas() );
        if ( w instanceof AutoplotUI ) {
            return ((AutoplotUI)w);
        } else {
            return null;
        }      
    }
    
    /**
     * resets the dom to the initial state by deleting added 
     * plotElements, plots and data sources.
     */
    public void reset() {
        logger.entering("ApplicationController", "reset");
        setStatus("resetting...");

        DomLock lock= mutatorLock();
        long t0= System.currentTimeMillis();
        while ( lock.isLocked() ) {
            logger.log( Level.INFO, "lock is not available: {0}", lock.toString());
            logger.finer( changesSupport.isValueAdjusting() );
            try {        
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
            if ( System.currentTimeMillis()-t0 > 10000 ) {
                logger.log(Level.WARNING, "Unable to get canvas lock to reset application because of lock: {0}", changesSupport.isValueAdjusting());
            }
        }
        lock.lock("Reset");
        Lock canvasLock = getCanvas().controller.getDasCanvas().mutatorLock();
        canvasLock.lock();
        logger.finer("got locks to reset application...");

        try {
            
            AutoplotUI au= maybeGetApplicatonGUI();
            if ( au!=null ) {
                int extraWidth= au.getWindowExtraWidth();
                int extraHeight= au.getWindowExtraHeight();
                au.resizeForCanvasSize( application.getOptions().getWidth(), application.getOptions().getHeight(), extraWidth, extraHeight ); 
            }
            
            // reset removes all annotations
            List<Annotation> annos= Arrays.asList( application.getAnnotations() );
            for ( Annotation anno : annos ) {
                application.controller.deleteAnnotation(anno);
            }
            
            // set the focus to the last plot remaining.
            application.controller.setPlot(application.getPlots(0));
            List<PlotElement> peles= application.controller.getPlotElementsFor(plot);
            if ( peles.size()>0 ) {
                application.controller.setPlotElement(peles.get(0));
            } else {
                application.controller.setPlotElement(application.getPlotElements(0));
            }
            
            for ( int i=application.getPlots().length-1; i>0; i-- ) {
                deletePlot( application.getPlots(i) );
            }

            Plot p0= application.getPlots(0);
            if ( p0==null ) throw new NullPointerException("p0 is null");
            if ( p0.getXaxis()==null )  throw new NullPointerException("p0.getXaxis() is null");
            if ( p0.getXaxis().getController()==null )  throw new NullPointerException("p0.getXaxis().getController() is null");
            if ( p0.getXaxis().getController().getDasAxis()==null )  throw new NullPointerException("p0.getXaxis().getController().getDasAxis() is null");
            if ( p0.getController().getDasPlot().getXAxis() != p0.getXaxis().getController().getDasAxis() ) {
                DasAxis rmme= p0.getController().getDasPlot().getXAxis();
                application.getCanvases(0).controller.getDasCanvas().remove(rmme);
                DasAxis oldAxis= p0.xaxis.controller.getDasAxis();
                application.getCanvases(0).controller.getDasCanvas().add( oldAxis, oldAxis.getRow(), oldAxis.getColumn() );
                p0.getController().getDasPlot().setXAxis(oldAxis);
            }
            if ( p0.getController().getDasPlot().getYAxis() != p0.getYaxis().getController().getDasAxis() ) {
                DasAxis rmme= p0.getController().getDasPlot().getYAxis();
                application.getCanvases(0).controller.getDasCanvas().remove(rmme);
                DasAxis oldAxis= p0.yaxis.controller.getDasAxis();
                application.getCanvases(0).controller.getDasCanvas().add( oldAxis, oldAxis.getRow(), oldAxis.getColumn() );
                p0.getController().getDasPlot().setYAxis(oldAxis);
            }
            p0.getXaxis().getController().getDasAxis().setTcaFunction(null);
            p0.getXaxis().setReference("");
            p0.getYaxis().setReference("");
            p0.getXaxis().getController().getDasAxis().setAxisOffset("");
            p0.getYaxis().getController().getDasAxis().setAxisOffset("");
            p0.getZaxis().getController().getDasAxis().setAxisOffset("");
            p0.getXaxis().getController().getDasAxis().setLabelOffset("");
            p0.getYaxis().getController().getDasAxis().setLabelOffset("");
            p0.getZaxis().getController().getDasAxis().setLabelOffset("");
            p0.getXaxis().setTickValues("");
            p0.getYaxis().setTickValues("");
            p0.getZaxis().setTickValues("");
            //p0.getXaxis().setForeground(application.options.foreground);
            //p0.getYaxis().setForeground(application.options.foreground);
            //p0.getZaxis().setForeground(application.options.foreground);
            p0.getXaxis().setAxisOffset("");
            p0.getYaxis().setAxisOffset("");
            p0.getZaxis().setAxisOffset("");
            
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

            application.controller.setPlotElement(application.getPlotElements(0));
            application.controller.setPlot(application.getPlots(0));            
                        
            for ( int i=application.getDataSourceFilters().length-1; i>0; i-- ) {
                deleteDataSourceFilter( application.getDataSourceFilters(i) );
            }
            TimeSeriesBrowseController tsbc= application.getDataSourceFilters(0).getController().getTimeSeriesBrowseController(); //TODO: clearing the URI should do this as well...
            if ( tsbc!=null ) {
                tsbc.releaseAll();
            }

            application.getPlotElements(0).setId("plotElement_0");

            application.getPlots(0).getXaxis().setLog(false); // TODO kludge
            application.getPlots(0).getYaxis().setLog(false); // TODO kludge
            application.getPlots(0).getZaxis().setLog(false); // TODO kludge

            Plot rawPlot= new Plot();
            rawPlot.setColortable( application.getOptions().getColortable() );
            application.getPlots(0).syncTo( rawPlot, Arrays.asList( DomNode.PROP_ID, Plot.PROP_ROWID, Plot.PROP_COLUMNID ) );
            application.getPlots(0).getXaxis().setAutoRange(true);
            application.getPlots(0).getYaxis().setAutoRange(true);
            application.getPlots(0).getZaxis().setAutoRange(true);
            application.getPlots(0).getXaxis().setAutoRangeHints("");
            application.getPlots(0).getYaxis().setAutoRangeHints("");
            application.getPlots(0).getZaxis().setAutoRangeHints("");
            
            for ( int i=application.getBindings().length-1; i>=0; i-- ) {
                removeBinding( application.getBindings(i) );
            }
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
            application.getPlots(0).syncTo( rawPlot, Arrays.asList( DomNode.PROP_ID, Plot.PROP_COLUMNID, Plot.PROP_ROWID ) );
            PlotElement rawPE= new PlotElement();
            rawPE.style.setColortable( application.getOptions().getColortable() );
            application.getPlotElements(0).syncTo( rawPE, Arrays.asList( DomNode.PROP_ID, PlotElement.PROP_PLOTID,PlotElement.PROP_DATASOURCEFILTERID, PlotElement.PROP_RENDERTYPE ) );
            application.getPlots(0).syncTo( rawPlot, Arrays.asList( DomNode.PROP_ID, Plot.PROP_COLUMNID, Plot.PROP_ROWID ) );
            application.getPlots(0).setAutoLabel(true);
            application.getPlotElements(0).syncTo( rawPE, Arrays.asList( DomNode.PROP_ID, PlotElement.PROP_PLOTID, PlotElement.PROP_DATASOURCEFILTERID ) );
            application.getPlotElements(0).setAutoLabel(true);
            application.getPlotElements(0).getPlotDefaults().setId("plot_defaults_0");
            application.getPlotElements(0).getStyle().setId("style_0");
            application.getPlotElements(0).getStyle().setFillColor( Color.decode("#404040") );
            
            application.getOptions().getController().loadPreferencesWithEvents();
            
            application.getPlotElements(0).getStyle().setColor( application.getOptions().getColor() );
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
            application.getPlots(0).setEphemerisLabels("");
            application.getPlots(0).setEphemerisLineCount(-1);
            application.getPlots(0).setContext( application.getPlots(0).getXaxis().getRange() );

            application.setEventsListUri("");
            
            resetIdSequenceNumbers();

            //clean up controllers after seeing the junk left behind in the profiler.
            for ( PlotElement pe :application.getPlotElements() ) {
                pe.getController().dataSet=null;
                pe.getController().getRenderer().setDataSet(null); // after seeing junk leftover in sftp://jbf@jfaden.net/home/jbf/ct/autoplot/script/test/stress/testServletSimulation.jy
            }
            for ( DataSourceFilter dsf :application.getDataSourceFilters() ) {
                dsf.getController().dataSet=null;
                dsf.getController().fillDataSet=null;
                dsf.getController().histogram=null;
            }
            
            unbind( application );
            //BindingGroup bc= bindingContexts.get(application);
            //bc.unbind();
            bind( application, Application.PROP_TIMERANGE, plot, Plot.PROP_CONTEXT );

            c.controller.getDasCanvas().removeBottomDecorators();
            c.controller.getDasCanvas().removeTopDecorators();
            
            List<DasCanvasComponent> extraCC= new ArrayList<>();
            for ( DasCanvasComponent cc: c.controller.getDasCanvas().getCanvasComponents() ) {
                if ( cc instanceof DasPlot || cc instanceof DasAxis
                        || cc instanceof ColumnColumnConnector || cc instanceof DasAnnotation ) {
                    
                } else {
                    extraCC.add( cc );
                }
            }
            for ( DasCanvasComponent cc : extraCC ) {
                c.controller.getDasCanvas().remove(cc);
            }    
            
            // reset das2 stuff which may be in a bad state.  This must be done on the event thread.
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    //go ahead and check for leftover das2 plots and renderers that might have been left from a bug.  rfe3324592
                    Canvas c= getCanvas();
                    c.setFont( application.options.canvasFont );
                    c.getController().dasCanvas.setSize( application.options.getWidth(), application.options.getHeight() );   
                    c.setWidth( application.options.getWidth() );
                    c.setHeight( application.options.getHeight() );
                    DasCanvasComponent[] dccs= c.controller.getDasCanvas().getCanvasComponents();
                    for (DasCanvasComponent dcc : dccs) {
                        if (dcc instanceof DasPlot) {
                            DasPlot p = (DasPlot) dcc;
                            boolean okay=false;
                            for ( Plot pp: application.getPlots() ) {
                                if ( pp.getController().getDasPlot()==p ) okay=true;
                            }
                            if ( !okay ) {
                                c.controller.getDasCanvas().remove(p);
                            } else {
                                Renderer[] rr= p.getRenderers();
                                for (Renderer rr1 : rr) {
                                    okay= false;
                                    for (PlotElement pes : application.getPlotElements()) {
                                        if (pes.getController().getRenderer() == rr1) {
                                            okay=true;
                                        }
                                    }
                                    if (!okay) {
                                        p.removeRenderer(rr1);
                                    }
                                    rr1.setBottomDecorator(null);
                                    rr1.setTopDecorator(null);    
                                }
                                p.setBottomDecorator(null);
                                p.setTopDecorator(null);
                                p.getXAxis().setTickV(null);
                                p.getYAxis().setTickV(null);
                            }
                        } else if (dcc instanceof DasColorBar) {
                            ((DasColorBar) dcc).setTickV(null);
                        }
                    }
                }
            };

            try {
                if ( SwingUtilities.isEventDispatchThread() ) {
                    run.run();
                } else {
                    SwingUtilities.invokeAndWait(run);
                }
            } catch (InterruptedException | InvocationTargetException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }

            cancelAllPendingTasks();                

        } finally {
            canvasLock.unlock();
            lock.unlock();
        }

        ArrayList problems= new ArrayList();
        if ( !DomUtil.validateDom(application, problems ) ) {
            logger.warning( problems.toString() );
        }
        logger.exiting("ApplicationController", "reset");
        setStatus("ready");

    }


    /**
     * for debugging in scripts.
     * @return the BindingSupport object.
     */
    public BindingSupport peekBindingSupport() {
        return this.bindingSupport;
    }    

    /**
     * binds two bean properties together.  Bindings are bidirectional, but
     * the initial copy is from src to dst.  In MVC terms, src should be the model
     * and dst should be a view.  The properties must fire property
     * change events for the binding mechanism to work.  A converter object can be 
     * provided that converts the object type between the two nodes.
     *
     * BeansBinding library is apparently not thread-safe.
     * 
     * Example:
     *<blockquote><pre><small>{@code
     * model= getApplicationModel()
     * bind( model.getPlotDefaults(), "title", model.getPlotDefaults().getXAxis(), "label" )
     *}</small></pre></blockquote>
     * @param src java bean such as model.getPlotDefaults()
     * @param srcProp a property name such as "title"
     * @param dst java bean such as model.getPlotDefaults().getXAxis()
     * @param dstProp a property name such as "label"
     * @param converter a converter object for the binding.  (e.g. Color name to Color object)
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
            if ( application.controller.isValueAdjusting() ) {
                logger.finest("binding already exists, ignoring");
                // just ignore this for now.
                return;
            } else {
                logger.finest("binding already exists, ignoring");
                setStatus("binding already exists: "+bindingModel );
                return;
            }
        }

        try {
            // verify properties exist.
            DomUtil.getPropertyType(src, srcProp);
            if ( dst instanceof DomNode ) {
                 DomUtil.getPropertyType((DomNode)dst, dstProp);
            }
        } catch (IllegalAccessException | InvocationTargetException ex) {
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


//        BeanProperty dstBeanProp= BeanProperty.create(dstProp);
//        
//        // double check for binding that already exists.  TODO: shouldn't the code above catch this?
//        for ( Binding b: bc.getBindings() ) {
//            if ( b.getTargetObject().equals(dst) ) {
//                if ( b.getTargetProperty().toString().equals(dstBeanProp.toString()) ) {
//                    logger.log(Level.FINE, "binding already exists: {0}", String.format( "bind {0}.{1} to {2}.{3}", new Object[]{src, srcProp, dst, dstProp}));
//                    return;
//                }
//            }
//        }
//        
        if (!dstId.equals("???") && !dstId.startsWith("das2:")) {
            Binding binding= hasBinding( src, srcProp, dst, dstProp );

            if ( binding!=null ) {
                logger.fine("binding already exists...");
            } else {
                binding = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, src, BeanProperty.create(srcProp), dst, BeanProperty.create(dstProp));
                if ( converter!=null ) binding.setConverter( converter );

                List<BindingModel> bindings = new ArrayList<>(Arrays.asList(application.getBindings()));
                bindings.add(bindingModel);
                application.setBindings(bindings.toArray(new BindingModel[bindings.size()]));
                bc.addBinding(binding);
                binding.bind();
                this.bindingImpls.put(bindingModel, binding);
            }
            
        } else {
            // these are bindings used to implement the application, such as from an Axis to DasAxis.
            // The user shouldn't be able to unbind these.
            bindingSupport.bind( src, srcProp, dst, dstProp, converter );

        }

    }
    
    /**
     * binds two bean properties together.  Bindings are bidirectional, but
     * the initial copy is from src to dst.  In MVC terms, src should be the model
     * and dst should be a view.  The properties must fire property
     * change events for the binding mechanism to work.
     *
     * BeansBinding library is apparently not thread-safe.
     * 
     * Example:
     *<blockquote><pre><small>{@code
     * model= getApplicationModel()
     * bind( model.getPlotDefaults(), "title", model.getPlotDefaults().getXAxis(), "label" )
     *}</small></pre></blockquote>
     * @param src java bean such as model.getPlotDefaults()
     * @param srcProp a property name such as "title"
     * @param dst java bean such as model.getPlotDefaults().getXAxis()
     * @param dstProp a property name such as "label"
     * @see org.autoplot.ScriptContext#bind(java.lang.Object, java.lang.String, java.lang.Object, java.lang.String) which can bind any two objects together.
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
        String dstId = "???";
        if (dst instanceof DomNode) {
            dstId = ((DomNode) dst).getId();
        }
        if (dst instanceof DasCanvasComponent) {
            dstId = "das2:" + ((DasCanvasComponent) dst).getDasName();
        }
        if (!dstId.equals("???") && !dstId.startsWith("das2:")) {
            BindingModel bm= findBinding( src, srcProp, ((DomNode)dst), dstProp );
            Binding binding= this.bindingImpls.get(bm);
            if ( binding!=null ) { 
                removeBinding(bm);
            } else {
                logger.log(Level.FINE, "expected to find binding for {0}.{1} to {2}.{3}", new Object[]{src.getId(), srcProp, ((DomNode)dst).getId(), dstProp});
            }
        } else {
            bindingSupport.unbind( src, srcProp, dst, dstProp );
        }
    }
    
    private static String propname( Property p ) {
        String srcProp= p.toString();
        int i1= srcProp.indexOf('[');
        int i2= srcProp.indexOf(']',i1);
        if ( i1>-1 && i2>i1 ) {
            srcProp= srcProp.substring(i1+1,i2);
        }
        return srcProp;
    }
    
    /**
     * show the bindings, for debugging purposes.
     */
    public void showBindings() {
        synchronized (bindingContexts) {
            for ( Entry<Object,BindingGroup> e: bindingContexts.entrySet() ) {
                List<Binding> bs= e.getValue().getBindings();
                System.err.println( "=== " +e.getKey()+" -> "+ e.getValue() + " (size="+bs.size()+")");
                if ( bs.size()>0 ) {
                    for ( Binding b: bs ) {
                        System.err.println( 
                                String.format( " %s.%s->%s.%s", 
                                b.getSourceObject(), propname( b.getSourceProperty() ), 
                                b.getTargetObject(), propname( b.getTargetProperty() ) ) );
                    }
                    System.err.println("");
                }
                
            }
        }
    }
    
    /**
     * return the binding if the binding exists already.  See also findBindings, which uses different logic.
     * @param s the source.
     * @param sp the source property
     * @param t the target
     * @param tp the target property.
     * @return the binding if it exists already.
     */
    private Binding hasBinding( Object s, String sp, Object t, String tp ) {
        BindingGroup bc= bindingContexts.get(s);
        List<Binding> bs= bc.getBindings();
        String lookfor= String.format( "%s.%s->%s.%s", s, sp, t, tp );
        for ( Binding b: bs ) {
            String test= String.format( "%s.%s->%s.%s", 
                    b.getSourceObject(), propname( b.getSourceProperty() ), 
                    b.getTargetObject(), propname( b.getTargetProperty() ) );
            if ( test.equals(lookfor ) ) return b;
        }
        return null;
    }
    
    /**
     * unbind the object, removing any binding to this node.  For example, when the object is about to be deleted.
     * @param src the node
     */
    public void unbind(DomNode src) {
        BindingGroup bc;

        synchronized (bindingImpls) {
            List<BindingModel> bb = new ArrayList(Arrays.asList(application.getBindings()));
            boolean changed= false;
            for (BindingModel b : application.getBindings()) {
                if (b.getSrcId().equals(src.getId()) || b.getDstId().equals(src.getId())) {
                    bb.remove(b);
                    Binding bimpl= bindingImpls.get(b);
                    if ( bimpl!=null ) {
                        try {
                            bimpl.unbind();
                        } catch ( IllegalStateException ex ) {
                            logger.log(Level.WARNING,ex.getMessage(),ex);
                        }
                        bindingImpls.remove(b);
                        logger.log(Level.FINE, "bindingImpls.size()={0}", bindingImpls.size());
                    }
                    changed= true;
                }
            }
            if ( changed ) {
                application.setBindings(bb.toArray(new BindingModel[bb.size()]));
            }
        }

        synchronized (bindingContexts) {
            bc = bindingContexts.get(src);
            if (bc != null) {

                bc.unbind();
                bindingContexts.remove(src);

                String bcid = src.getId();
                List<BindingModel> bindings = DomUtil.asArrayList(application.getBindings());
                List<BindingModel> remove = new ArrayList<>();
                boolean changed= false;
                for (BindingModel bb : bindings) { // avoid concurrent modification
                    if (bb.getBindingContextId().equals(bcid)) {
                        remove.add(bb);
                        changed= true;
                    }
                }
                if ( changed ) {
                    bindings.removeAll(remove);
                    application.setBindings(bindings.toArray(new BindingModel[bindings.size()]));
                }

            }
            for ( Entry<Object,BindingGroup> e: bindingContexts.entrySet() ) { // app_0.timeRange->plot_0.context
                BindingGroup bg= e.getValue();
                List<Binding> remove = new ArrayList<>();
                for ( Binding b: bg.getBindings() ) {
                    if ( b.getTargetObject().equals(src) ) {
                        remove.add(b);
                    }
                }
                for ( Binding b:remove ) {
                    bg.removeBinding(b);
                }
            }
        }
        
    }

    /**
     * unbind all implementation bindings associated with the dom node.
     * @param src the node
     */
    protected void unbindImpl( DomNode src ) {
        bindingSupport.unbind(src);
    }

    /**
     * remove the binding and its implementation.
     * @param binding 
     * @deprecated see removeBinding(binding)
     * @see #removeBinding(org.autoplot.dom.BindingModel) 
     */
    public void deleteBinding(BindingModel binding) {
        removeBinding(binding);
    }

    /**
     * remove the binding and its implementation.
     * @param binding 
     */
    public void removeBinding(BindingModel binding) {
        Binding b = bindingImpls.get(binding);
        if ( b==null ) {
            logger.log(Level.SEVERE, "didn''t find the binding implementation for {0}, ignoring", binding);
        } else if ( b.isBound() ) { // https://sourceforge.net/p/autoplot/bugs/1126/
            b.unbind();
        }
        bindingImpls.remove(binding);
        logger.log(Level.FINE, "bindingImpls.size()={0}", bindingImpls.size());

        BindingGroup bc= bindingContexts.get(DomUtil.getElementById(application,binding.srcId));
        if ( bc!=null ) {
            try {
                bc.removeBinding(b);
            } catch ( Exception e ) {
                logger.fine("deleteBinding still needs attention.");
            }
        }
        logger.log( Level.FINER, "dstId binding={0}", String.valueOf( bindingContexts.get(binding.dstId) ) );
        
        List<BindingModel> bindings = DomUtil.asArrayList(application.getBindings());
        bindings.remove(binding);
        application.setBindings(bindings.toArray(new BindingModel[bindings.size()]));

    }

    /**
     * Find the binding, if it exists.  All bindingImpls are symmetric, so the src and dst order is ignored in this
     * search.  
     * @param src the node (e.g. an Axis)
     * @param srcProp the property name (e.g. "range")
     * @param dst the other node
     * @param dstProp the other node's property name (e.g. "range")
     * @return the BindingModel or null if it doesn't exist.
     */
    public BindingModel findBinding(DomNode src, String srcProp, DomNode dst, String dstProp) {
        return DomUtil.findBinding( application, src, srcProp, dst, dstProp );
    }


    /**
     * returns a list of bindings of the node for the property
     * @param src
     * @param srcProp
     * @return
     */
    public List<BindingModel> findBindings( DomNode src, String srcProp ) {
        return DomUtil.findBindings( application, src, srcProp );
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
        return DomUtil.findBindings( application, src, srcProp, dst, dstProp );
    }

    public BindingModel[] getBindingsFor(DomNode node) {
        List<BindingModel> results= findBindings( node, null, null, null );
        return results.toArray( new BindingModel[results.size()] );
    }
    
    /**
     * return the dom element (plot,axis,etc) with this id.
     * @param id
     * @return the DomNode with this id.
     */
    public DomNode getElementById( String id ) {
        return DomUtil.getElementById(this.application, id );
    }
    
    protected String status = "";
    public static final String PROP_STATUS = "status";

    /**
     * clients can get status here.  
     * @return the last status message.
     * @see #waitUntilIdle() 
     */
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
        this.statusUpdateTime= System.currentTimeMillis();
        propertyChangeSupport.firePropertyChange(PROP_STATUS, oldStatus, status);
    }
    
    private long statusUpdateTime=0L;
    
    /**
     * return the number of milliseconds since the last status update
     * @return the number of milliseconds since the last status update 
     */
    public long getStatusAgeMillis() {
        return System.currentTimeMillis()-statusUpdateTime;
    }
    
    protected String focusUri = "";
    /**
     * property focusUri is the uri that has gained focus.  This can be the datasource uri, or the location of the .vap file.
     * @see #VALUE_BLUR_FOCUS
     */
    public static final String PROP_FOCUSURI = "focusUri";

    public String getFocusUri() {
        return focusUri;
    }

    public void setFocusUri(String focusUri) {
        logger.log(Level.FINE, "setFocusUri({0})", focusUri);
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
                    if ( p1.getRowId().equals( p.getRowId() ) ) {
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
                    if ( p1.getColumnId().equals( p.getColumnId() ) ) {
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


    /**
     * return the PlotElements for the plot, if any.
     * @param plot
     * @return list of PlotElements.
     */
    public List<PlotElement> getPlotElementsFor(Plot plot) {
        return DomUtil.getPlotElementsFor( application, plot );
    }

    /**
     * return the DataSourceFilter for the plotElement, or null if none exists.
     * @param element
     * @return the DataSourceFilter to which the plot element refers, or null.
     * @see #getFirstPlotFor(org.autoplot.dom.DataSourceFilter) 
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
     * @param dsf the data source filter.
     * @return return the PlotElements for the data source filter, if any.
     */
    public List<PlotElement> getPlotElementsFor(DataSourceFilter dsf) {
        String id = dsf.getId();
        List<PlotElement> result = new ArrayList<>();
        for (PlotElement pe : application.getPlotElements()) {
            if (pe.getDataSourceFilterId().equals(id)) {
                result.add(pe);
            }
        }
        return result;
    }
    
    /**
     * return the Plot using the DataSourceFilter, checking for ticksURI
     * as well.  This does not
     * return indirect (via vap+internal) references.
     * @param dsf the data source filter.
     * @return return the PlotElements for the data source filter, if any.
     */
    public List<Plot> getPlotsFor(DataSourceFilter dsf) {
        String id = dsf.getId();
        HashSet<Plot> result = new HashSet<>();
        for ( Plot p: application.getPlots() ) {
            for (PlotElement pe : getPlotElementsFor(p)) {
                if (pe.getDataSourceFilterId().equals(id)) {
                    result.add(p);
                }
            }
            if ( p.getTicksURI().equals(id) ) {
                result.add(p);
            }
        }
        return Arrays.asList(result.toArray(new Plot[result.size()]));
    }

    /**
     * find the first plot that is connected to this data, following vap+internal
     * links.  This is used, for example, to get a timerange to control the DSF.
     * @param dsf
     * @return
     */
    public Plot getFirstPlotFor( DataSourceFilter dsf ) {
        String lookFor= dsf.getId();
        PlotElement f= null;

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

        } else if ( node instanceof Annotation ) {
            node.setId("annotation_" + annotationNum);
            annotationNum.getAndIncrement();

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
     * reset the sequence id numbers based on the number of instances in the 
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
    protected PlotElement plotElement;
    public static final String PROP_PLOT_ELEMENT = "plotElement";

    /**
     * return the focus plot element or null
     * @return the focus plot element
     */
    public PlotElement getPlotElement() {
        return plotElement;
    }

    /**
     * set the focus plot element
     * @param plotElement the new focus plot element.
     */
    public void setPlotElement(PlotElement plotElement) {
        PlotElement oldPlotElement = this.plotElement;
        if ( plotElement==null ) {
            setStatus("no plot element selected");
        } else {
            setStatus(plotElement + " selected");
            if ( plotElement!=oldPlotElement ) {
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
        if ( SwingUtilities.isEventDispatchThread() && ( oldPlotElement!=plotElement ) ) {
            Logger.getLogger("gui").log(Level.FINE, "set plotElement {0}", plotElement);
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

    /**
     * This can take a while and should not be called on the event thread.
     * @param plot 
     */
    public void setPlot(Plot plot) {
        if ( plot==null ) {
            logger.warning("setPlot(null)");
        }
        Plot oldPlot = this.plot;
        if ( SwingUtilities.isEventDispatchThread() && ( oldPlot!=plot ) ) {
            Logger.getLogger("gui").log(Level.FINE, "set plot {0}", plot);
        }
        this.plot = plot;
        propertyChangeSupport.firePropertyChange(PROP_PLOT, oldPlot, plot);
    }
    
    /**
     * a comma-delimited list of plot ids.
     */
    protected String selectedPlots= "";
    
    public String getSelectedPlots() {
        return selectedPlots;
    }
    
    public void setSelectedPlots( String selectedPlots ) {
        this.selectedPlots= selectedPlots;
    }
    
    /**
     * convenient method for setting the selected plots
     * @param selectedPlots 
     */
    public void setSelectedPlotsArray( Plot[] selectedPlots ) {
        if ( selectedPlots.length==0 ) {
            setSelectedPlots("");
        } else if ( selectedPlots[0]==null ) {
            setSelectedPlots("");
        } else {
            StringBuilder sb= new StringBuilder(selectedPlots[0].id);
            for ( int i=1; i<selectedPlots.length; i++ ) {
                sb.append(",").append(selectedPlots[i].id);
            }
            setSelectedPlots(sb.toString());
        }
    }
    
    /**
     * convenient method for setting the selected plots
     * @return the plots
     * @throws IllegalArgumentException if a plot cannot be found
     */
    public Plot[] getSelectedPlotsArray( ) {
        if ( selectedPlots.length()==0 ) {
            return new Plot[0];
        } else {
            String[] ss= selectedPlots.split(",");
            Plot[] result= new Plot[ss.length];
            for ( int i=0; i<ss.length; i++ ) {
                DomNode n= DomUtil.getElementById( application, ss[i] );
                if ( n!=null && n instanceof Plot ) {
                    result[i]= (Plot)n;
                } else {
                    throw new IllegalArgumentException("unable to find plot with ID "+ss[i]);
                }
            }
            return result;
        }
    }
    
    private ScriptContext2023 scriptContext = null;

    public static final String PROP_SCRIPTCONTEXT = "scriptContext";

    public ScriptContext2023 getScriptContext() {
        return scriptContext;
    }

    public void setScriptContext(ScriptContext2023 scriptContext) {
        ScriptContext2023 oldScriptContext = this.scriptContext;
        this.scriptContext = scriptContext;
        this.scriptContextState= new ScriptContext2023.State();
        das2PeerListenerSupport.firePropertyChange(PROP_SCRIPTCONTEXT, oldScriptContext, scriptContext);
    }
    
    private ScriptContext2023.State scriptContextState;

    public ScriptContext2023.State getScriptContextState() {
        return scriptContextState;
    }

    /**
     * focus canvas.
     */
    protected Canvas canvas;
    public static final String PROP_CANVAS = "canvas";

    /**
     * focus canvas.  Note there is only one canvas allowed (for now).
     * @return the focus canvas.
     */
    public Canvas getCanvas() {
        return canvas;
    }

    /**
     * set focus canvas, which must be the one of the canvas the application
     * knows about.
     * @param canvas the new focus canvas.
     */
    public void setCanvas(Canvas canvas) {
        Canvas oldCanvas = getCanvas();
        if ( SwingUtilities.isEventDispatchThread() && ( oldCanvas!=canvas ) ) {
            Logger.getLogger("gui").log(Level.FINE, "set canvas {0}", canvas);
        }        
        this.canvas = canvas;
        propertyChangeSupport.firePropertyChange(PROP_CANVAS, oldCanvas, canvas);
    }
    /**
     * focus dataSourceFilter.
     */
    protected DataSourceFilter dataSourceFilter;
    /**
     * focus dataSourceFilter.
     */
    public static final String PROP_DATASOURCEFILTER = "dataSourceFilter";

    /**
     * return focus dataSourceFilter, or null.  This will return
     * the first dataSourceFilter when nothing is in focus.
     * @return the focus dataSourceFilter.
     */
    public DataSourceFilter getDataSourceFilter() {
        if ( this.dataSourceFilter==null && this.application.dataSourceFilters.size()>0 ) {
            return this.application.dataSourceFilters.get(0);
        } else {
            return dataSourceFilter;
        }
    }

    /**
     * set the focus dataSourceFilter.
     * @param dataSourceFilter the focus dataSourceFilter, or null.
     */
    public void setDataSourceFilter(DataSourceFilter dataSourceFilter) {
        if ( dataSourceFilter==null ) {
            logger.info("set dataSourceFilter to null");
        }
        DataSourceFilter oldDataSourceFilter = this.dataSourceFilter;
        this.dataSourceFilter = dataSourceFilter;
        propertyChangeSupport.firePropertyChange(PROP_DATASOURCEFILTER, oldDataSourceFilter, dataSourceFilter);
    }

    // See https://sourceforge.net/p/autoplot/bugs/2175/
//    private final PropertyChangeListener optionsListener= new PropertyChangeListener() {
//        @Override
//        public void propertyChange(PropertyChangeEvent evt) {
//            switch (evt.getPropertyName()) {
//                case Options.PROP_BACKGROUND:
//                    application.canvases.get(0).setBackground( (Color)evt.getNewValue() );
//                    break;
//                case Options.PROP_FOREGROUND:
//                    application.canvases.get(0).setForeground((Color)evt.getNewValue() );
//                    break;
//                case Options.PROP_CANVASFONT:
//                    application.canvases.get(0).setFont( (String)evt.getNewValue() );
//                    break;
//                default:
//                    break;
//            }
//        }
//    };
            
    private void bindTo(DasCanvas canvas) {
        ApplicationController ac = this;
        ac.bind(application.options, "background", canvas, "background" );
        ac.bind(application.options, "foreground", canvas, "foreground" );
        ac.bind(application.options, "canvasFont", canvas, "baseFont", DomUtil.STRING_TO_FONT );
        //this.application.options.addPropertyChangeListener( Options.PROP_BACKGROUND, optionsListener );
        //this.application.options.addPropertyChangeListener( Options.PROP_FOREGROUND, optionsListener );
        //this.application.options.addPropertyChangeListener( Options.PROP_CANVASFONT, optionsListener );        
    }

    /**
     * synchronize to the state "that" excluding any properties listed.
     * @param that the state
     * @param exclude list of properties to skip (e.g. options)
     */
    protected void syncTo( Application that, List<String> exclude ) {
        DomLock lock = changesSupport.mutatorLock();
        lock.lock( "Sync to Application" );
        Lock canvasLock = getCanvas().controller.getDasCanvas().mutatorLock();
        canvasLock.lock();

        try {

            if ( !exclude.contains("options") ) {
                List<String> excl= Arrays.asList(
                    Options.PROP_OVERRENDERING,
                    Options.PROP_LOGCONSOLEVISIBLE,
                    Options.PROP_SCRIPTVISIBLE,
                    Options.PROP_SERVERENABLED );
                // In basic mode, we don't want the vap to flip the time range editor back to URIs, so suppress this.
                AutoplotUI au= maybeGetApplicatonGUI();
                if ( au!=null ) {
                    if ( au.isBasicMode() && !that.getOptions().isUseTimeRangeEditor() ) {
                        excl= new ArrayList<>(excl);
                        excl.add( Options.PROP_USE_TIME_RANGE_EDITOR);
                    }
                }
                application.getOptions().syncTo(that.getOptions(),excl);
            }

            Map<String,String> nameMap= new HashMap<String,String>() {
                @Override
                public String get(Object key) {
                    String result= super.get(key);
                    return (result==null) ? (String)key : result;
                }
            };

            if ( !this.application.id.equals( that.id ) ) nameMap.put( that.id, this.application.id );

            if ( !exclude.contains("canvases") ) syncSupport.syncToCanvases(that.getCanvases(),nameMap);

            if ( logger.isLoggable(Level.FINE) ) {
                logger.log(Level.FINE, "layout: {0}", DomUtil.layoutToString(canvas)); //TODO 2202
            }
            
            if ( !exclude.contains("dataSourceFilters") ) syncSupport.syncToDataSourceFilters(that.getDataSourceFilters(), nameMap);

            if ( !exclude.contains("plots") ) syncSupport.syncToPlots( that.getPlots(),nameMap );
            for ( Plot p: this.application.getPlots() ) {
                if ( p.getController()!=null ) {
                    //TODO: this is a terrible kludge and I need to figure out how to do this correctly.
                    // https://sourceforge.net/p/autoplot/feature-requests/757/
                    DasColorBar cb= p.getController().getDasColorBar();
                    String x= DasDevicePosition.formatLayoutStr( cb.getColumn() );
                    if ( !x.equals(p.getColorbarColumnPosition() ) ) {
                        try {
                            DasDevicePosition.parseLayoutStr( cb.getColumn(), p.getColorbarColumnPosition() );
                        } catch (ParseException ex) {
                            logger.log(Level.SEVERE, null, ex);
                        }
                    }
                    
                }
            }

            if ( !exclude.contains("plotElements") )  syncSupport.syncToPlotElements(that.getPlotElements(), nameMap);

            application.setTimeRange(that.getTimeRange());
            application.setEventsListUri(that.getEventsListUri());

            syncSupport.syncBindings( that.getBindings(), nameMap );
            syncSupport.syncConnectors(that.getConnectors());

            syncSupport.syncAnnotations( that.getAnnotations() );
            
            resetIdSequenceNumbers();
        } finally {
            canvasLock.unlock();
            lock.unlock();
        }

        for (PlotElement p : application.getPlotElements()) {  // kludge to avoid reset range
            p.controller.setResetPlotElement(false);  // see https://sourceforge.net/tracker/index.php?func=detail&aid=2985891&group_id=199733&atid=970682
            p.controller.setResetComponent(false);
            //p.controller.setResetRanges(false);
            //p.controller.doResetRenderType( p.getRenderType() );
            p.controller.setResetRenderType(false);
            p.controller.setDsfReset(true); // dataSourcesShould be resolved.
        }
        for (DataSourceFilter dsf: application.getDataSourceFilters() ) {
            dsf.controller.setResetDimensions(false);
        }
//        System.err.println( "bindings size: " + this.application.bindings.size() + "  should be: " + that.bindings.size() );
//        if ( this.application.bindings.size()!=that.bindings.size() ) {
//            for ( int i=0; i<Math.max(this.application.bindings.size(),that.bindings.size()); i++ ) {
//                String s1= i<this.application.bindings.size() ? this.application.bindings.get(i).toString() : "";
//                String s2= i<that.bindings.size() ? that.bindings.get(i).toString() : "";
//                System.err.println( String.format( "%60s %60s\n", s1, s2 ) );
//            }
//        }
    }

    /**
     * true if running in headless environment
     * @return true if running in headless environment
     */
    public boolean isHeadless() {
        return model.isHeadless();
    }

    /**
     * return the das canvas.
     * @return the das canvas.
     */
    public DasCanvas getDasCanvas() {
        return ((CanvasController)getCanvas().controller).getDasCanvas();
    }

    /**
     * get the DasRow implementation for the marginRow.
     * @return the DasRow implementation for the marginRow.
     */
    public DasRow getRow() {
        return getCanvas().getMarginRow().getController().getDasRow();
    }

    /**
     * get the DasColumn implementation for the marginRow.
     * @return the DasColumn implementation for the marginColumn.
     */
    public DasColumn getColumn() {
        return getCanvas().getMarginColumn().getController().getDasColumn();
    }

    /**
     * return the source of monitors.
     * @return the source of monitors.
     */
    public MonitorFactory getMonitorFactory() {
        return DasApplication.getDefaultApplication().getMonitorFactory();
    }

    /**
     * return true if the plot is part of a connector, the top or the bottom.
     * @param plot
     * @return
     */
    boolean isConnected( Plot plot ) {
        Connector[] connectors= this.application.getConnectors();
        for (Connector connector : connectors) {
            if (connector.getPlotB().equals(plot.getId())) {
                return true;
            } else if (connector.getPlotA().equals(plot.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * return true if the axis is bound to any other property, such as another axis or the application timerange.
     * //TODO: ignore label bindings 
     * @param a an axis or the colorbar.
     * @return true if it is bound.
     */
    protected boolean isBoundAxis( Axis a ) {
        BindingModel[] bms= getBindingsFor(a);
        return bms.length>0;
    }

    /**
     * return true if the plot has the time series browse capability, meaning
     * something will go off and load more data if the time range is changed.
     * Note the TSB may be connected to the plot's context property, and 
     * the x-axis is not a time axis.
     * @param p the plot.
     * @return true if the plot has the time series browse.
     */
    public boolean isTimeSeriesBrowse(Plot p) {
        List<DataSourceFilter> dsfs= DomUtil.getDataSourceFiltersFor( application, p );
        for ( DataSourceFilter dsf: dsfs ) {
            if ( dsf!=null ) {
                DataSourceController dsfc= dsf.getController();
                if ( dsfc!=null && dsfc.getTsb()!=null ) {
                    return true;
                }
            } else {
                logger.log(Level.FINE, "bad dataset id for plot: {0}", p.getId());
            }
        }
        return false;
    }

    private int pendingChangeCount = 0;

    public static final String PROP_PENDINGCHANGECOUNT = "pendingChangeCount";

    /**
     * get the number of pending changes.  0 means the application is idle.
     * @return get the number of pending changes.
     */
    public int getPendingChangeCount() {
        return pendingChangeCount;
    }

    public void setPendingChangeCount(int pendingChangeCount) {
        int oldPendingChangeCount = this.pendingChangeCount;
        this.pendingChangeCount = pendingChangeCount;
        propertyChangeSupport.firePropertyChange(PROP_PENDINGCHANGECOUNT, oldPendingChangeCount, pendingChangeCount);
    }
    
}
