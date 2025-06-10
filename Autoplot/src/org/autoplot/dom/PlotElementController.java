
package org.autoplot.dom;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import org.das2.components.DasProgressPanel;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.event.HorizontalSlicerMouseModule;
import org.das2.event.MouseModule;
import org.das2.event.VerticalSlicerMouseModule;
import org.das2.graph.ContoursRenderer;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasCanvasComponent;
import org.das2.graph.DasColorBar;
import org.das2.graph.DasPlot;
import org.das2.graph.DefaultPlotSymbol;
import org.das2.graph.DigitalRenderer;
import org.das2.graph.EventsRenderer;
import org.das2.graph.HugeScatterRenderer;
import org.das2.graph.PitchAngleDistributionRenderer;
import org.das2.graph.PsymConnector;
import org.das2.graph.RGBImageRenderer;
import org.das2.graph.Renderer;
import org.das2.graph.SeriesRenderer;
import org.das2.graph.SpectrogramRenderer;
import org.das2.graph.TickCurveRenderer;
import org.das2.graph.VectorPlotRenderer;
import org.das2.system.RequestProcessor;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.CancelledOperationException;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.jdesktop.beansbinding.Converter;
import org.autoplot.ApplicationModel;
import org.autoplot.AutoRangeUtil;
import org.autoplot.RenderType;
import org.autoplot.AutoplotUtil;
import static org.autoplot.AutoplotUtil.SERIES_SIZE_LIMIT;
import org.autoplot.ExportDataPanel;
import org.autoplot.RenderTypeUtil;
import org.autoplot.datasource.AnonymousDataSource;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.datasource.DataSource;
import org.autoplot.dom.ChangesSupport.DomLock;
import org.autoplot.layout.LayoutConstants;
import org.autoplot.util.RunLaterListener;
import org.das2.qds.ArrayDataSet;
import org.das2.qds.DataSetOps;
import org.das2.qds.DataSetUtil;
import org.das2.qds.JoinDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.das2.qds.examples.Schemes;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.das2.qds.ops.Ops;
import org.autoplot.metatree.MetadataUtil;
import org.das2.components.VerticalSpectrogramAverager;
import org.das2.event.DataRangeSelectionListener;
import org.das2.event.HorizontalDragRangeSelectorMouseModule;
import org.das2.graph.BoundsRenderer;
import org.das2.graph.PolarPlotRenderer;
import org.das2.qds.util.QStreamFormatter;
import org.das2.qstream.SimpleStreamFormatter;
import org.das2.qstream.StreamException;

/**
 * PlotElementController manages the PlotElement, for example resolving the datasource and loading the dataset.
 * Once the data is loaded, the listening PlotElementController receives the update and does the following:<ol>
 *  <li> resolve the plot type: spectrogram, lineplot, stack of lineplot, etc, using AutoplotUtil.guessRenderType(fillDs);
 *  <li> reset the plot element, setting the plot type and creating children if needed.  For example, a B-GSM (demo 5) is
 *      resolved by creating three children, and handing the components off to them.
 *  <li> if the component property is not empty, then we implement the component and display that.
 *  <li> adjusting the component slice index will not affect ranging when the index is changed.
 * </ol>
 * @author jbf
 */
public class PlotElementController extends DomNodeController {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger( "autoplot.dom.pec" );

    private static final String PENDING_CREATE_DAS_PEER = "createDasPeer";
    private static final String PENDING_RESET_RANGE = "resetRanges";
    private static final String PENDING_SET_DATASET= "setDataSet";
    private static final String PENDING_COMPONENT_OP= "componentOp";
    private static final String PENDING_UPDATE_DATASET= "updateDataSet";
    private static final String PENDING_RESET_DATASOURCEFILTERID="resetDataSourceFilterId";
    /**
     * we need to reset the render type, but we can't do this from the event thread.
     */
    private static final String PENDING_RESET_RENDER_TYPE= "resetRenderType";

    final private Application dom;
    private PlotElement plotElement;
    private PlotElement parentPlotElement;
    
    private DataSourceFilter dsf; // This is the one we are listening to.
    /**
     * switch over between fine and course points.
     */
    public static final int SYMSIZE_DATAPOINT_COUNT = 500;
    public static final int LARGE_DATASET_COUNT = 30000;

    private final Object processLock= new Object();
    private QDataSet processDataSet= null;
    String procressStr= null;

    // introduced as tool for making sure PlotElementControllers are properly garbage collected.  This
    // will be set to true as the PlotElement and its controller are deleted.
    boolean deleted= false;

    public PlotElementController(final ApplicationModel model, final Application dom, final PlotElement plotElement) {
        super(plotElement);
        this.dom = dom;
        this.plotElement = plotElement;

        plotElement.addPropertyChangeListener(PlotElement.PROP_RENDERTYPE, plotElementListener);
        plotElement.addPropertyChangeListener(PlotElement.PROP_DATASOURCEFILTERID, plotElementListener);
        plotElement.addPropertyChangeListener(PlotElement.PROP_COMPONENT, plotElementListener);
        plotElement.addPropertyChangeListener(PlotElement.PROP_PARENT, parentElementListener );
        plotElement.getStyle().addPropertyChangeListener(styleListener);
    }

    /**
     * remove all property change listeners.
     */
    protected void disconnect() {
        plotElement.removePropertyChangeListener(PlotElement.PROP_RENDERTYPE, plotElementListener);
        plotElement.removePropertyChangeListener(PlotElement.PROP_DATASOURCEFILTERID, plotElementListener);
        plotElement.removePropertyChangeListener(PlotElement.PROP_COMPONENT, plotElementListener);
        plotElement.removePropertyChangeListener(PlotElement.PROP_PARENT, plotElementListener);
        plotElement.getStyle().removePropertyChangeListener(styleListener);
        PlotElement parent= getParentPlotElement();
        if ( parent!=null ) {
            parent.removePropertyChangeListener( getParentComponentLister() );
        }
        plotElement.removePropertyChangeListener( getParentComponentLister() );
    }

    /**
     * remove any direct references this controller has as it is being deleted.
     */
    protected void removeReferences() {
        this.processDataSet= null;
        //this.plotElement= null; // bug 2054: if the thing we link to has no other references, then there is no reason to clear the reference to it.
        //this.dsf= null;
        this.deleted= true;
        //this.dom= null;
    }
    
    /**
     * return child plotElements, which are plotElements that share a datasource but pull out
     * a component of the data.
     * @return
     */
    public List<PlotElement> getChildPlotElements() {
        ArrayList<PlotElement> result= new ArrayList();
        for ( PlotElement pp: dom.plotElements ) {
            if ( pp.getParent().equals( plotElement.getId() ) ) result.add(pp);
        }
        return result;
    }

    /**
     * set the child plotElements.
     * @param plotElements
     */
    protected void setChildPlotElements(List<PlotElement> plotElements) {
        for ( PlotElement p: plotElements ) {
            p.setParent(plotElement.getId());
        }
    }

    /**
     * set the parent plotElement.  this is used when copying.
     * @param p
     */
    protected void setParentPlotElement(PlotElement p) {
        plotElement.setParent( p.getId() );
    }

    /**
     * return the parent plotElement, or null if the plotElement doesn't have a parent.
     * @return
     */
    public PlotElement getParentPlotElement() {
        if ( plotElement.getParent().equals("") ) {
            return null;
        } else {
            for ( PlotElement pp: dom.plotElements ) {
                if ( pp.getId().equals( plotElement.getParent() ) ) return pp;
            }
            return null; // TODO: maybe throw exception!
        }
    }

    /** 
     * return the plot element.
     * @return  the plot element.
     */
    public PlotElement getPlotElement() {
        return plotElement;
    }
    
    /**
     * remove any bindings and listeners
     */
    void unbindDsf() {
        if ( dsf!=null ) { //TODO: This should never be null, but it is
            dsf.removePropertyChangeListener(DataSourceFilter.PROP_FILTERS, dsfListener);
            dsf.controller.removePropertyChangeListener(DataSourceController.PROP_FILLDATASET, fillDataSetListener);
            dsf.controller.removePropertyChangeListener(DataSourceController.PROP_DATASOURCE, dataSourceDataSetListener);
            dsf.controller.removePropertyChangeListener(DataSourceController.PROP_EXCEPTION, exceptionListener);
        }
    }
    
    PropertyChangeListener dsfListener = new PropertyChangeListener() {

        @Override
        public String toString() {
            return "" + PlotElementController.this;
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt,"dsfListener");
            if ( evt.getPropertyName().equals(DataSourceFilter.PROP_FILTERS) ) {
                if ( evt.getOldValue().toString().trim().equals( evt.getNewValue().toString().trim() ) ) {
                    return;
                }
                logger.log(Level.FINE, "property change in DSF means I need to autorange: {0}", evt.getPropertyName());
                setResetRanges(true);
                maybeSetPlotAutorange();
            }
        }
    };
    
    private void resetRenderTypeImp( RenderType oldRenderType, RenderType newRenderType ) {
        logger.entering( "PlotElementController", "resetRenderTypeImp", new Object[] { oldRenderType, newRenderType } );
        PlotElement parentEle= getParentPlotElement();
        if (parentEle != null) {
            logger.finest("parentEle!=null branch");
            if ( parentEle.getRenderType().equals(newRenderType) ) {
                if ( plotElement.getPlotId().length()>0 ) {  //https://sourceforge.net/p/autoplot/bugs/1038/
                    doResetRenderTypeInt(newRenderType);
                    updateDataSet();
                }
            } else {
                parentEle.setRenderType(newRenderType);
            }
        } else {
            if ( axisDimensionsChange(oldRenderType, newRenderType) ) {
                logger.finest("axisDimensionsChange branch");
                resetRanges= true;
                if ( plotElement.getComponent().equals("") ) {
                    resetPlotElement(getDataSourceFilter().getController().getFillDataSet(), plotElement.getRenderType(), "");
                } else {
                    QDataSet sliceDs= getDataSet();
                    if ( sliceDs==null ) {
                        // This happens when we load a vap.
                        sliceDs= getDataSourceFilter().getController().getFillDataSet(); // Since this is null, I suspect we can do the same behavior in either case.
                        resetPlotElement( sliceDs, plotElement.getRenderType(), "" );
                    } else {
                        resetPlotElement( sliceDs, plotElement.getRenderType(), ""); // I'm assuming that getDataSet() has been set already, which should be the case.
                    }
                }
                updateDataSet();
            } else {
                logger.finest("axis dimensions don't change, just reset render type.");
                doResetRenderType(newRenderType);
                updateDataSet();
            }
            setResetPlotElement(false);
        }
        logger.exiting("PlotElementController", "resetRenderTypeImp" );
    }

    /**
     * return true if the two are the same operation, but have only
     * different arguments.  For example, slice1(0) and slice1(10) are 
     * the same operation, but slice1(0) and slice2(0) are not.
     * @param c1 an operation, like "slice1(0)"
     * @param c2 an operation, like "slice1(10)"
     * @return true if the two are the same operation.
     */
    private boolean sameOperation( String c1, String c2 ) {
        int ic1= c1.indexOf("(");
        int ic2= c2.indexOf("(");
        if ( ic1!=ic2 ) {
            return false;
        } else {
            if ( c1.substring(0,ic1).equals(c2.substring(0,ic1) ) ) {
                return true;
            } else {
                return false;
            }
        }
    }
    
    /**
     * return true when the childComponents are compatible with the 
     * parentComponents, but then have additional operations.
     * Apologies for using component and operation interchangeably.
     * @param parentComponents ['slice1(0)']
     * @param childComponents ['slice1(0)','unbundle(Bx)']
     * @return true if the child components are an extension of the parent's
     */
    private boolean extendedOperation( String[] parentComponents, String[] childComponents ) {
        if ( childComponents.length<parentComponents.length ) {
            return false;
        }
        for ( int i=1; i<parentComponents.length; i++ ) {
            if ( !sameOperation( parentComponents[i], childComponents[i] ) ) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * the job of the parentComponentListener is to listen for changes
     * in the parent and to stay in sync with it.  For example, if the parent's
     * component is "|slice1(0)" and the child's is "|slice1(0)|unbundle('X')"
     * then a change in the parent to "|slice1(1)" should also change the child's
     * component.  Similarly, if the child's component is updated, then reset
     * the parent's component.  When the parent's component is no longer 
     */
    PropertyChangeListener parentComponentListener= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if ( evt.getSource()==parentPlotElement ) {                
                String s= (String)evt.getNewValue();
                String component= plotElement.component;
                String[] childComponents= component.split("\\|",-2);
                String[] parentComponents= s.split("\\|",-2);
                if ( childComponents.length==parentComponents.length ) {
                    if ( extendedOperation( Arrays.copyOfRange( parentComponents, 0, parentComponents.length-1 ), 
                        Arrays.copyOfRange( childComponents, 0, parentComponents.length-1 ) ) ) {
                        if ( !DataSetOps.changesDimensions(parentComponents[parentComponents.length-1]) ) {
                            String newc= "|" + parentComponents[parentComponents.length-1] + String.join("|", childComponents);
                            childComponents= newc.split("\\|",-2);
                        }
                    }
                }
                if ( !extendedOperation(parentComponents, childComponents) ) {
                    //logger.log(Level.INFO, "releasing child {0}", plotElement.id);
                    parentPlotElement.setActive(true);
                    //dom.controller.deletePlotElement(plotElement);
                    //plotElement.removePropertyChangeListener(parentComponentListener); // TODO:why?  This plotElement was still in memory!
                    return; // transitional state
                }
                System.arraycopy(parentComponents, 0, childComponents, 0, parentComponents.length);
                StringBuilder sb= new StringBuilder(childComponents[0]);
                for ( int i=1; i<parentComponents.length; i++ ) {
                    sb.append("|");
                    sb.append(parentComponents[i]);
                }
                for ( int i=parentComponents.length; i<childComponents.length; i++ ) {
                    sb.append("|");
                    sb.append(childComponents[i]);
                }
                plotElement.setComponent(sb.toString());
            } else if ( evt.getSource()==plotElement ) {
                String component= (String)evt.getNewValue();;
                String[] childComponents= component.split("\\|",-2);
                String[] parentComponents= parentPlotElement.component.split("\\|",-2);
                if ( !extendedOperation(parentComponents, childComponents) ) {
                    logger.log(Level.INFO, "releasing child {0}", plotElement.id);
                    parentPlotElement.setActive(true);
                    dom.controller.deletePlotElement(plotElement);
                    plotElement.removePropertyChangeListener(parentComponentListener); // TODO:why?  This plotElement was still in memory!
                    return; // transitional state
                }                
                StringBuilder sb= new StringBuilder();
                for ( int i=1; i<parentComponents.length; i++ ) {
                    sb.append("|");
                    sb.append(childComponents[i]);
                }
                parentPlotElement.setComponent(sb.toString());
            }
        }
    };
            
    PropertyChangeListener parentElementListener= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if ( parentPlotElement!=null ) {
                parentPlotElement.removePropertyChangeListener( "component", parentComponentListener );
                plotElement.removePropertyChangeListener( "component", parentComponentListener );
            }
            String pid= plotElement.getParent();
            if ( pid.trim().length()==0 ) {
                return;
            }
            PlotElement ppe=(PlotElement)dom.controller.getElementById(pid);
            if ( ppe!=null ) {
                parentPlotElement= ppe;
                parentPlotElement.addPropertyChangeListener( "component", parentComponentListener );
                plotElement.addPropertyChangeListener( "component", parentComponentListener );
            }
        }  
    };
            
    PropertyChangeListener plotElementListener = new PropertyChangeListener() {

        @Override
        public String toString() {
            return "" + PlotElementController.this;
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt,"plotElementListener");            
            logger.log(Level.FINE, "plotElementListener: {0} {1}->{2}", new Object[]{evt.getPropertyName(), evt.getOldValue(), evt.getNewValue()});
            if ( evt.getPropertyName().equals(PlotElement.PROP_RENDERTYPE) && !PlotElementController.this.isValueAdjusting() ) {
                //if ( dom.getController().isValueAdjusting() ) {
                    //return; // occasional NullPointerException, bug 2988979
                //}
                final RenderType newRenderType = (RenderType) evt.getNewValue();
                final RenderType oldRenderType = (RenderType) evt.getOldValue();
                changesSupport.registerPendingChange( PlotElementController.this, PENDING_RESET_RENDER_TYPE );
                Runnable run= () -> {
                    try {
                        changesSupport.performingChange( PlotElementController.this, PENDING_RESET_RENDER_TYPE );
                        resetRenderTypeImp( oldRenderType, newRenderType );
                    } finally {
                        changesSupport.changePerformed( PlotElementController.this, PENDING_RESET_RENDER_TYPE );
                    }
                };
                if ( SwingUtilities.isEventDispatchThread() ) {
                    new Thread(run,"updateDataSetOffEvent").start();
                } else {
                    run.run();
                }
            } else if (evt.getPropertyName().equals(PlotElement.PROP_DATASOURCEFILTERID)) {
                changeDataSourceFilter();
                if ( dsfReset ) {
                    if ( evt.getOldValue()!=null ) {
                        setResetPlotElement(true);
                        setResetRanges(true);
                    }
                    if ( evt.getNewValue().equals("") ) {
                        //updateDataSet();
                        if ( getRenderer()!=null ) getRenderer().setDataSet(null); // transitional state associated with undo.  https://sourceforge.net/tracker/?func=detail&aid=3316754&group_id=199733&atid=970682
                    } else {
                        changesSupport.registerPendingChange( PlotElementController.this, PENDING_RESET_DATASOURCEFILTERID );                
                        Runnable run= () -> {
                            try {
                                changesSupport.performingChange( PlotElementController.this, PENDING_RESET_DATASOURCEFILTERID  );
                                updateDataSet();
                            } finally {
                                changesSupport.changePerformed( PlotElementController.this, PENDING_RESET_DATASOURCEFILTERID  );
                            } 
                        };
                        if ( SwingUtilities.isEventDispatchThread() ) {
                            new Thread(run,"updateDataSetOffEvent").start();
                        } else {
                            run.run();
                        }
                    }
                }
            } else if ( evt.getPropertyName().equals( PlotElement.PROP_COMPONENT ) ) {
                String oldv= (String)evt.getOldValue();
                oldv= DataSetOps.makeProcessStringCanonical(oldv);
                String newv= (String)evt.getNewValue();
                newv= DataSetOps.makeProcessStringCanonical(newv);
                if ( DataSetOps.changesDimensions( oldv, newv ) ) { //TODO: why two methods see axisDimensionsChange 10 lines above
                    if ( DataSetOps.changesIndependentDimensions( oldv,newv ) ) {
                        logger.log(Level.FINER, "component property change requires we reset render and dimensions: {0}->{1}", new Object[]{(String) evt.getOldValue(), (String) evt.getNewValue()});
                        setResetPlotElement(true);
                        setResetRanges(true);
                        if ( !dom.getController().isValueAdjusting() ) {
                            maybeSetPlotAutorange();
                        }
                    } else {
                        logger.log(Level.FINER, "component property change requires we reset just the y-axis: {0}->{1}", new Object[]{(String) evt.getOldValue(), (String) evt.getNewValue()});
                        setResetPlotElement(true);
                        setResetRanges(true);
                        if ( !dom.getController().isValueAdjusting() ) {
                            maybeSetPlotYZAutorange();
                        }
                    }
                }
                if ( sliceAutoranges ) {
                    setResetRanges(true);
                    if ( !dom.getController().isValueAdjusting() ) maybeSetPlotAutorange();
                }
                if ( newv.startsWith("|") ) dom.getOptions().setDataVisible(true);
                if ( changesSupport==null ) {
                    logger.severe("changesSupport is null!!!");
                    logger.severe("this is a sad, leftover PlotElementController that should have been GC'd");
                    return;
                }
                changesSupport.registerPendingChange(plotElementListener, PENDING_COMPONENT_OP);
                Runnable run= () -> {
                    if ( changesSupport==null ) {
                        logger.severe("changesSupport is null!!!");
                        return;
                    }
                    // we reenter this code, so only set lock once.  See test.endtoend.Test015.java
                    // vap+cef:file:///home/jbf/ct/hudson/data.backup/cef/C1_CP_PEA_CP3DXPH_DNFlux__20020811_140000_20020811_150000_V061018.cef?Data__C1_CP_PEA_CP3DXPH_DNFlux
                    // bug 1480 insert breakpoint here
                    changesSupport.performingChange(plotElementListener, PENDING_COMPONENT_OP);
                    setStatus("busy: update data set");
                    try {
                        updateDataSet();
                        setStatus("done update data set");
                    } catch ( RuntimeException ex ) {
                        setStatus("warning: "+ex.toString());
                        throw ex;
                    } finally {
                        changesSupport.changePerformed(plotElementListener, PENDING_COMPONENT_OP);
                    }
                };
                
                RequestProcessor.invokeLater(run);
                
            }
        }
    };

    /**
     * listen for changes in the parent plotElement that this child can respond to.
     */
    PropertyChangeListener parentStyleListener= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt,"parentStyleListener");            
            try {
                if ( evt.getPropertyName().equals("color") ) {
                    logger.fine("ignoring change of parent color.");
                } else {
                    DomUtil.setPropertyValue(plotElement.style, evt.getPropertyName(), evt.getNewValue());
                }
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
    };

    /*
     * listen for changes that might change the renderType.  Try to pick one that is close.  Don't
     * fire changes.
     */
    PropertyChangeListener styleListener= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt,"styleListener");            
            if ( evt.getPropertyName().equals( PlotElementStyle.PROP_REBINMETHOD ) ) {
                if ( plotElement.getRenderType()==RenderType.nnSpectrogram || plotElement.getRenderType()==RenderType.spectrogram ) {
                    if ( evt.getNewValue()==SpectrogramRenderer.RebinnerEnum.nearestNeighbor ) {
                        plotElement.renderType= RenderType.nnSpectrogram;
                    } else if ( evt.getNewValue()==SpectrogramRenderer.RebinnerEnum.binAverage ) {
                        plotElement.renderType= RenderType.spectrogram;
                    }
                }
//            } else if ( evt.getPropertyName().equals( PlotElementStyle.PROP_SYMBOL_CONNECTOR ) ) {
//                plotElement.setAutoRenderType( false );
//            } else if ( evt.getPropertyName().equals( PlotElementStyle.PROP_SYMBOL_SIZE ) ) {
//                plotElement.setAutoRenderType( false );
//            } else if ( evt.getPropertyName().equals( PlotElementStyle.PROP_LINE_WIDTH ) ) {
//                plotElement.setAutoRenderType( false );
//            } else if ( evt.getPropertyName().equals( PlotElementStyle.PROP_PLOT_SYMBOL ) ) {
//                plotElement.setAutoRenderType( false );
            }
        }
    };

//    private boolean needNewChildren(String[] labels, List<PlotElement> childPeles) {
//        if ( childPeles.isEmpty() ) return true;
//        List<String> ll= Arrays.asList(labels);
//        for ( PlotElement p: childPeles ) {
//            if ( !ll.contains( p.getComponent() ) ) {
//               return true;
//            }
//        }
//        return false;
//    }

    /**
     * the DataSourceFilter id has changed, so we need to stop listening to the
     * old one and connect to the new one.  Also, if the old dataSourceFilter is
     * now an orphan, delete it from the application.
     */
    private void changeDataSourceFilter() {
        if (dsf != null) {
            unbindDsf();
            List<DomNode> usages= DomUtil.dataSourceUsages(dom, dsf.getId() );
            if ( usages.isEmpty() ) {
                dom.controller.deleteDataSourceFilter(dsf);
            }
        }

        assert (plotElement.getDataSourceFilterId() != null);
        if ( plotElement.getDataSourceFilterId().equals("") ) return;

        dsf = dom.controller.getDataSourceFilterFor(plotElement);

        if ( dsf==null ) {
            logger.log(Level.WARNING, "Unable to find datasource for plotElement {0}", plotElement);
            return;
        } else {
            dsf.addPropertyChangeListener( DataSourceFilter.PROP_FILTERS, dsfListener );
        }
        setDataSourceFilterController( dsf.controller );
    }

    private Color deriveColor( Color color, int i ) {

        float[] colorHSV = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        if (colorHSV[2] < 0.7f) {
            colorHSV[2] = 0.7f;
        }
        if (colorHSV[1] < 0.7f) {
            colorHSV[1] = 0.7f;
        }
        return Color.getHSBColor(i / 6.f, colorHSV[1], colorHSV[2]);
    }

    /**
     * pop off to the root cause of the problem, since exceptions are wrapped again and again.
     * @param exception
     * @return
     */
    private Exception getRootCause( Exception exception ) {
        Throwable cause= exception.getCause();
        while ( cause!=null && cause!=exception && cause instanceof Exception ) { // TODO: review this, I will probably regret...
            exception= (Exception)cause;
            cause= exception.getCause();
        }
        if ( cause!=null && cause instanceof Exception ) {
            return (Exception)cause;
        } else {
            return exception;
        }

    }
    /**
     * apply process to the data.  In general these can be done on the same thread (like
     * slice1), but some are slow (like fftPower).
     * 
     * @param c
     * @param fillDs
     * @return
     * @throws RuntimeException
     * @throws Exception when the processStr cannot be processed.
     */
    private QDataSet processDataSet(String c, QDataSet fillDs) throws RuntimeException, Exception {
        setStatus("busy: process dataset");
        String label= null;
        c= c.trim();
        if ( c.length()>0 && !c.startsWith("|") ) {  // grab the component, then apply processes after the pipe.
            if (!plotElement.getComponent().equals("") && fillDs.length() > 0 && fillDs.rank() == 2) {
                String[] labels = SemanticOps.getComponentNames(fillDs);
                String comp= plotElement.getComponent();
                int ip= comp.indexOf('|');
                if ( ip!=-1 ) {
                    comp= comp.substring(0,ip);
                }
                comp= Ops.saferName(comp);
                if ( fillDs.property(QDataSet.BUNDLE_1)!=null ) {
                    fillDs= DataSetOps.unbundle( fillDs,comp ); //TODO: illegal argument exception
                    label= comp;
                } else {
                    boolean found= false;
                    for (int i = 0; i < labels.length; i++) {
                        if ( Ops.saferName(labels[i]).equals(comp)) {
                            fillDs = DataSetOps.slice1(fillDs, i);
                            label = labels[i];
                            found= true;
                            break;
                        }
                    }
                    if ( !found ) {
                        throw new IllegalArgumentException("component not found: "+comp );
                    }
                }
                if (label == null && !isPendingChanges()) {
                    RuntimeException ex = new RuntimeException("component not found: " + comp );
                    throw ex;
                }
            }
            int idx= c.indexOf('|');
            if ( idx==-1 ) {
                c="";
            } else {
                c= c.substring(idx);
            }
        }
        if (c.length() > 5 && c.startsWith("|")) {
            logger.log( Level.FINE, "component={0}", c);
            // slice and collapse specification
            if ( DataSetOps.isProcessAsync(c) ) {
                synchronized (processLock) {
                    if ( c.equals(this.procressStr) && this.processDataSet!=null ) { // caching
                        if ( logger.isLoggable( Level.FINE ) ) {
                            QDataSet bounds= DataSetOps.dependBounds( this.processDataSet );
                            logger.log(Level.FINE, "using cached dataset for {0} bounds:{1}", new Object[] { procressStr, bounds.slice(0) } );
                        }
                        
                        fillDs= this.processDataSet;
                    } else {
                        this.processDataSet= null;
                        this.procressStr= null;
                        ProgressMonitor mon= DasProgressPanel.createComponentPanel( getDasPlot(), "process data set" );
                        fillDs = DataSetOps.sprocess(c, fillDs, mon );
                        if ( mon.isCancelled() ) {
                            this.processDataSet= null; //TODO: this is going to cause a problem because we'll reenter almost immediately.  We need to cache the result and a flag that indicates it should be reloaded.
                            this.procressStr= null;
                        } else {
                            this.processDataSet= fillDs;
                            this.procressStr= c;
                        }
                    }
                }
            } else {
                synchronized (processLock) {
                    this.processDataSet= null;
                    this.procressStr= null;
                }
                fillDs = DataSetOps.sprocess(c, fillDs, null);
            }
        } 
        setStatus("done, process dataset");
        return fillDs;
    }

    /**
     * calculate the interpreted metadata after the slicing.
     * @param c
     * @param properties
     * @return
     */
    private static Map<String,Object> processProperties( String c, Map<String,Object> properties ) {
        c= c.trim();
        if (c.length() > 5 && c.contains("|")) {
            // slice and collapse specification
            properties = MetadataUtil.sprocess(c, properties );
        }
        return properties;  
    }

    private boolean rendererAcceptsData(QDataSet fillDs) {
        if ( getRenderer() instanceof SpectrogramRenderer ) {
            switch (fillDs.rank()) {
                case 3:
                    QDataSet dep0= (QDataSet) fillDs.property( QDataSet.DEPEND_0 );  // only support das2 tabledataset scheme.
                    if ( dep0!=null ) return false;
                    return rendererAcceptsData( DataSetOps.slice0(fillDs,0) );
                case 2:
                    // && !SemanticOps.isBundle(fillDs) ) {
                    return true;
                default:
                return fillDs.property(QDataSet.PLANE_0)!=null;

            }
        } else if ( getRenderer() instanceof SeriesRenderer) {
            switch (fillDs.rank()) {
                case 0:
                    return true;
                case 1:
                    return true;
                case 2:
                    return SemanticOps.isBundle(fillDs) || SemanticOps.isRank2Waveform(fillDs);
                case 3:
                    return SemanticOps.isJoin(fillDs) && SemanticOps.isRank2Waveform(fillDs.slice(0));
                default:
                    return false;
            }
        } else if ( getRenderer() instanceof HugeScatterRenderer ) {
            switch (fillDs.rank()) {
                case 1:
                    return true;
                case 2:
                    return SemanticOps.isBundle(fillDs) ||  SemanticOps.isRank2Waveform(fillDs);
                case 3:
                    return SemanticOps.isJoin(fillDs) && SemanticOps.isRank2Waveform(fillDs.slice(0));
                default:
                    return false;
            }
        } else if ( getRenderer() instanceof RGBImageRenderer ) {
            switch (fillDs.rank()) {
                case 2:
                    return !SemanticOps.isBundle(fillDs);
                case 3:
                    return fillDs.length(0,0) < 5;
                default:
                    return false;
            }
        } else {
                return true;
        }
    }


    /**
     * set the dataset that will be plotted.  If the component property is non-null, then
     * additional filtering will be performed.  See http://papco.org/wiki/index.php/DataReductionSpecs
     * @param fillDs
     * @throws IllegalArgumentException
     */
    private void setDataSet(QDataSet fillDs ) throws IllegalArgumentException {

        // since we might delete sibling plotElements here, make sure each plotElement is still part of the application
        if (!Arrays.asList(dom.getPlotElements()).contains(plotElement)) {
            return;
        }

        String comp= plotElement.getComponent();
        try {
            if ( fillDs!=null ) {

                if ( comp.length()>0 ) fillDs = processDataSet(comp, fillDs );

                if ( doUnitsCheck( fillDs ) ) { // bug 3104572: slicing would drop units, so old vaps wouldn't work
                    Plot plot= this.dom.getController().getPlotFor(plotElement);
                    PlotController pc= plot.getController();
                    pc.doPlotElementDefaultsUnitsChange(plotElement);
                }
                Object ocontext= fillDs.property(QDataSet.CONTEXT_0);
                if ( ocontext!=null ) {
                    Object altContext= fillDs.property( QDataSet.CONTEXT_1 );
                    if ( altContext!=null && altContext instanceof QDataSet ) {
                        Units acu= (Units)(((QDataSet)altContext).property(QDataSet.UNITS));
                        if ( acu!=null && UnitsUtil.isTimeLocation(acu) ) {
                            ocontext= altContext;
                        }
                    }
                }
                
                if ( ocontext!=null && !( ocontext instanceof QDataSet ) ) {
                    logger.warning("CONTEXT_0 is not a QDataSet");
                    ocontext= null;
                }
                QDataSet context= (QDataSet)ocontext;
                if ( context!=null ) {
                    DatumRange cdr;
                    try {
                        if ( context.rank()==1 ) {
                            cdr= DataSetUtil.asDatumRange( context, true );
                        } else {
                            cdr= DatumRange.newRange( context.value(), context.value(), SemanticOps.getUnits(context) );
                        }
                        Plot plot= this.dom.getController().getPlotFor(plotElement);
                        plot.getController().getDasPlot().setDisplayContext( cdr );  // note this property is just a placeholder, and is sensed by ColumnColumnConnector.
                    } catch ( IllegalArgumentException ex ) {
                        logger.fine(ex.toString());
                    }
                } else {
                    // TODO: ???
                }

            }

            logger.log(Level.FINE, "  postOpsDataSet: {0}", String.valueOf(fillDs) );

            setDataSetInternal(fillDs);
        } catch ( RuntimeException ex ) {
            logger.log(Level.FINE, "runtime exception caught: {0}", new Object[] { ex });
            if (getRenderer() != null) {
                getRenderer().setDataSet(null);
                getRenderer().setException(getRootCause(ex));
                setDataSetInternal(null);
            } else {
                throw ex;
            }
            return;
        } catch ( CancelledOperationException ex ) {
            getRenderer().setDataSet(null);
            getRenderer().setException(getRootCause(ex));
            setDataSetInternal(null);
            return;
            
        } catch ( Exception ex ) {
            getRenderer().setDataSet(null);
            getRenderer().setException(getRootCause(ex));
            setDataSetInternal(null);
            return;
        }

        if ( fillDs!=null && getRenderer() != null) {
            if (rendererAcceptsData(fillDs)) {
                getRenderer().setDataSet(fillDs);
            } else {
                getRenderer().setDataSet(null);
                getRenderer().setException(new Exception("renderer cannot plot " + fillDs));
            }
        }

    }

    /**
     * the current dataset plotted, after operations (component property) has been applied.
     */
    public static final String PROP_DATASET = "dataSet";

    protected QDataSet dataSet = null;

    /**
     * the current dataset plotted, after operations (component property) has been applied.
     * @return 
     */
    public QDataSet getDataSet() {
        return dataSet;
    }
    
    /**
     * finish off the components and data post processing, and set the 
     * dataset.  This does not set the renderer dataset.
     * @param dataSet 
     */
    private void setDataSetInternal(QDataSet dataSet) {
        logger.log(Level.FINE, "setDataSetInternal {0}", dataSet);
        QDataSet oldDataSet = this.dataSet;
        this.dataSet = dataSet; //TODO: we should probably synchronize dataSet access.
        if ( ( plotElement.getLegendLabel().contains("%{") || plotElement.getLegendLabel().contains("$(") ) && renderer!=null ) {
            String s= (String)getLabelConverter().convertForward(plotElement.getLegendLabel());
            renderer.setLegendLabel(s);
        }
        propertyChangeSupport.firePropertyChange(PROP_DATASET, oldDataSet, dataSet);
    }

    PropertyChangeListener exceptionListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt,"exceptionListener");
            changesSupport.performingChange( this, PENDING_SET_DATASET );
            try {
                Exception ex = dsf.controller.getException();
                logger.log(Level.FINE, "{0} got exception: {1}  ", new Object[]{plotElement, ex });
                if ( resetComponent ) {
                    //if ( !plotElement.component.equals("") )  {
                    //    plotElement.setComponent("");
                    //} else {
                    plotElement.setComponent("");
                    //    plotElement.component=""; // we must avoid firing an event here, causes problems //TODO: why?
                        plotElement.autoComponent=true;
                    //}
                    setResetComponent(false);
                }
                renderer.setDataSet(null);
                renderer.setException(ex); 
            } finally {
                changesSupport.changePerformed( this, PENDING_SET_DATASET );
            }
        }

        @Override
        public String toString() {
            return "" + PlotElementController.this;
        }

    };

    PropertyChangeListener fillDataSetListener = new PropertyChangeListener() {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            synchronized ( processLock ) {
                LoggerManager.logPropertyChangeEvent(evt,"fillDataSetListener");
                logger.fine("enter fillDataSetListener propertyChange");
                if (!Arrays.asList(dom.getPlotElements()).contains(plotElement)) {
                    //TODO: find a way to fix this properly or don't call it a kludge! logger.fine("kludge pec446 cannot be removed");
                    return;  // TODO: kludge, I was deleted. I think this can be removed now.  The applicationController was preventing GC.
                }
                changesSupport.performingChange( this, PENDING_SET_DATASET );
                try {
                    QDataSet fillDs = dsf.controller.getFillDataSet();
                    logger.log(Level.FINE, "{0} got new dataset: {1}  resetComponent={2}  resetPele={3}  resetRanges={4}", new Object[]{plotElement, fillDs, resetComponent, resetPlotElement, resetRanges});
                    if ( resetComponent ) {
                        plotElement.setComponentAutomatically("");
                        processDataSet= null;
                        procressStr= null;
                        setResetComponent(false);
                    } else {
                        processDataSet= null;
                    }
                    updateDataSet();
                } finally {
                    changesSupport.changePerformed( this, PENDING_SET_DATASET );
                }
            }
        }

        @Override
        public String toString() {
            return "" + PlotElementController.this;
        }

    };

    /**
     * indicate if changing slice index should result in autoranging being redone.  Presently, this is done when the
     * dimension we're slicing is nominal (ordinal).
     * @param fillDs
     * @param component
     * @return
     * @throws NumberFormatException
     */
    private boolean sliceShouldAutorange(QDataSet fillDs, String component) throws NumberFormatException {
        Units[] us = getDimensionUnits(fillDs);
        Pattern p = Pattern.compile("\\|slice(\\d)\\(\\d+\\)");
        Matcher m = p.matcher(component);
        if (m.matches()) {
            int dim = Integer.parseInt(m.group(1));
            if (UnitsUtil.isNominalMeasurement(us[dim])) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Resolve the renderType and renderControl for the dataset.  This may be
     * set explicitly by the dataset, in its RENDER_TYPE property, or resolved
     * using the dataset scheme.
     * 
     * @param fillds 
     * @return the render string with canonical types.  The result will always contain a greater than (&gt;).
     */
    public static String resolveRenderType( QDataSet fillds ) {
        String srenderType= (String) fillds.property(QDataSet.RENDER_TYPE);
        RenderType renderType;
        String renderControl="";
        if ( srenderType!=null && srenderType.trim().length()>0 ) {
            int i= srenderType.indexOf('>');
            if ( i==-1 ) {
                renderControl= "";
            } else {
                renderControl= srenderType.substring(i+1);
                srenderType= srenderType.substring(0,i);
            }
            boolean useHugeScatter= "true".equals( System.getProperty("useHugeScatter","true") );
            switch (srenderType) {
                case "time_series":
                    if ( useHugeScatter && fillds.length() > SERIES_SIZE_LIMIT) {
                        renderType = RenderType.hugeScatter;
                    } else {
                        renderType = RenderType.series;
                    }
                    break;
                case "waveform":
                    if ( useHugeScatter ) {
                        renderType = RenderType.hugeScatter;
                    } else {
                        renderType = RenderType.series;
                    }
                    break;
                case "spectrogram":
                    RenderType specPref= RenderType.spectrogram;
                    Options o= new Options();
                    Preferences prefs= AutoplotSettings.settings().getPreferences( o.getClass() );  //TODO: because this is static?
                    boolean nn= prefs.getBoolean(Options.PROP_NEARESTNEIGHBOR,o.isNearestNeighbor());
                    if ( nn ) {
                        specPref = RenderType.nnSpectrogram;
                    }
                    renderType= specPref;
                    break;
                default:
                    try {
                        renderType= RenderType.valueOf(srenderType);
                    } catch ( IllegalArgumentException ex ) {
                        renderType= AutoplotUtil.guessRenderType(fillds);
                    }
                    break;
            }
            return renderType.toString() + ">" + renderControl;
        } else {
            renderType = AutoplotUtil.guessRenderType(fillds);
            if ( renderType==RenderType.series ) {
                if ( Schemes.isScalarSeriesWithErrors(fillds) ) {
                    renderControl= "drawError=T";
                }
            }
            return renderType.toString() + ">" + renderControl;
        }
        
    }

    /**
     * Get the dataset from the DataSourceFilter and if resetPlotElement is true,
     * then guess at a reasonable rendering method.
     * 
     * @throws Exception 
     * @see #resolveRenderType(org.das2.qds.QDataSet) 
     */
    private void updateDataSetImmediately() throws Exception {
        performingChange( this, PENDING_UPDATE_DATASET );
        try {
            if ( dsf==null ) {
                getRenderer().setDataSet(null);
                getRenderer().setException(new RuntimeException("Data Source Reference"));
                return;
            }
            QDataSet fillDs = dsf.controller.getFillDataSet();
            Exception renderException= null;
            if (fillDs != null) {
                final String comp= DataSetOps.makeProcessStringCanonical( plotElement.getComponent().trim() );
                if ( logger.isLoggable(Level.FINE) ) {
                    logger.log(Level.FINE, "updateDataSetImmediately: {0} {1}", new Object[]{plotElement, plotElement.getRenderType() });
                    logger.log(Level.FINE, "  resetPlotElement: {0}", resetPlotElement );
                    logger.log(Level.FINE, "  resetRanges: {0}", resetRanges);
                    logger.log(Level.FINE, "  resetRenderType: {0}", resetRenderType );
                    logger.log(Level.FINE, "  component: {0}", comp );
                    logger.log(Level.FINE, "  dataSet: {0}", String.valueOf(fillDs) );
                }
                
                //This was to support the CdawebVapServlet, where partial vaps are handled.  See https://sourceforge.net/p/autoplot/bugs/1304/
                //if ( plotElement.isAutoRenderType() ) {
                    //resetPlotElement= true;
                //}
                
                if (resetPlotElement) {
                    if (comp.equals("")) {
                        String s= resolveRenderType( fillDs );
                        int i= s.indexOf('>');
                        RenderType renderType= RenderType.valueOf(s.substring(0,i));
                        if ( !renderType.equals(plotElement.renderType) &&  getRenderer()!=null ) getRenderer().setDataSet(null); //bug1065
                        plotElement.renderType = renderType; // setRenderTypeAutomatically.  We don't want to fire off event here.
                        resetPlotElement(fillDs, renderType, s.substring(i+1) );
                        setResetPlotElement(false);
                    } else if ( comp.startsWith("|") ) {
                        try {
                            QDataSet fillDs2 = fillDs;
                            //String srenderType= (String)fillDs2.property(QDataSet.RENDER_TYPE);
                            //if ( srenderType!=null ) {
                            //    srenderType= resolveRenderType( fillDs2 );
                            //}
                            if ( comp.length()>0 ) fillDs2= processDataSet( comp, fillDs2 );
                            if ( fillDs2==null ) throw new NullPointerException("operations result in null: "+comp);
                            String s= resolveRenderType( fillDs2 );
                            //if ( comp.length()>0 && comp.startsWith("|unbundle(") && srenderType!=null ) { // vap+inline:ripplesVectorTimeSeries(200)&RENDER_TYPE=hugeScatter
                            //    if ( !srenderType.contains(">") ) {
                            //        srenderType= srenderType + ">";
                            //    }
                            //    s= srenderType;
                            //}
                            int i= s.indexOf('>');
                            if ( i==-1 ) i=s.length();
                            RenderType renderType= RenderType.valueOf(s.substring(0,i));
                            if ( !renderType.equals(plotElement.renderType) &&  getRenderer()!=null ) getRenderer().setDataSet(null); //bug1065
                            plotElement.renderType = renderType; // setRenderTypeAutomatically.  We don't want to fire off event here.
                            resetPlotElement(fillDs2, renderType, s.substring(i+1) );
                            setResetPlotElement(false);
                        } catch ( CancelledOperationException ex ) {
                            setStatus("warning: Filters were cancelled: " + ex );
                            getRenderer().setDataSet(null);
                            getRenderer().setException(ex);
                            renderException= ex;
                        } catch ( RuntimeException ex ) {
                            if ( getRenderer()==null ) {
                                System.err.println("NullPointerEx has happened, see bug https://sourceforge.net/p/autoplot/bugs/2635/");
                                ex.printStackTrace();
                            }
                            setStatus("warning: Exception in process: " + ex );
                            getRenderer().setDataSet(null);
                            getRenderer().setException(getRootCause(ex));
                            renderException= ex;
                        }
                    } else {
                        if (renderer == null) maybeCreateDasPeer();
                        try {
                            if (resetRanges) doResetRanges();
                            setResetPlotElement(false);
                        } catch ( RuntimeException ex ) {
                            setStatus("warning: Exception in process: " + ex );
                            getRenderer().setDataSet(null);
                            getRenderer().setException(getRootCause(ex));
                            renderException= ex;
                        }
                    }
                } else if (resetRanges) {
                    doResetRanges();
                    setResetRanges(false);
                } else if (resetRenderType) {
                    doResetRenderType(plotElement.getRenderType());
                }
            }
            if (fillDs == null) {
                if (getRenderer() != null) {
                    getRenderer().setDataSet(null);
                    getRenderer().setException(null); // remove leftover message.
                }
                setDataSet(null);
            } else {
                if ( renderException==null ) {
                    setDataSet(fillDs);
                }
            }
        } finally {
            changePerformed( this, PENDING_UPDATE_DATASET );
        }
    }

    /**
     * get the dataset from the dataSourceFilter, and plot it possibly after
     * slicing component.  Changing the component (slice) will re-enter the
     * code here.
     * @throws IllegalArgumentException
     */
    private void updateDataSet() throws IllegalArgumentException {
        if ( EventQueue.isDispatchThread() ) {
            logger.warning("updateDataSet called from event thread.  Stack track follows.");
            new Exception("updateDataSet called from event thread").printStackTrace();
        }
        //if ( getRenderer()!=null ) getRenderer().setDataSet(null); //bug 1073 bug 1065.
        registerPendingChange( this, PENDING_UPDATE_DATASET );
        //TODO: we should hand off the dataset here instead of mucking around with it...  
        if (!dom.controller.isValueAdjusting()) {
            Runnable run= () -> {
                // java complains about method not override.
                try {
                    updateDataSetImmediately();
                } catch ( Exception ex ) {
                    logger.log( Level.WARNING, ex.getMessage(), ex ); // wrapping somehow didn't show original exception.
                    throw new IllegalArgumentException(ex);
                }
            };
            //RequestProcessor.invokeLater(run); // this allows listening PlotElements to each do their stuff.
            run.run();
            
        } else {
            new RunLaterListener(ChangesSupport.PROP_VALUEADJUSTING, dom.controller, true ) {
                @Override
                public void run() {
                    try {
                        updateDataSetImmediately();
                    } catch ( Exception ex ) {
                        throw new IllegalArgumentException(ex);
                    }
                }
            };
        }
    }

    /**
     * true indicates that the new renderType makes the axis dimensions change.
     * For example, switching from spectrogram to series (to get a stack of components)
     * causes the z axis to become the yaxis.
     * @param oldRenderType
     * @param newRenderType
     * @return true if the dimensions change.
     */
    public static boolean axisDimensionsChange( RenderType oldRenderType, RenderType newRenderType ) {
        if ( oldRenderType==newRenderType ) return false;
        if ( newRenderType==RenderType.pitchAngleDistribution ) return true;
        if ( newRenderType==RenderType.polar ) return true;
        if ( oldRenderType==RenderType.spectrogram && newRenderType==RenderType.nnSpectrogram ) {
            return false;
        } else if ( oldRenderType==RenderType.nnSpectrogram && newRenderType==RenderType.spectrogram ) {
            return false;
        } else if ( newRenderType==RenderType.spectrogram || newRenderType==RenderType.nnSpectrogram ) {
            return true;
        } else {
            if ( newRenderType==RenderType.eventsBar ) {
                if ( oldRenderType==RenderType.digital ) {
                    return true;
                } else {
                    return false;
                }
            } else {
                if ( oldRenderType==RenderType.spectrogram || oldRenderType==RenderType.nnSpectrogram ) {
                    return true;
                } else {
                    if ( oldRenderType==RenderType.scatter 
                            || oldRenderType==RenderType.series
                            || oldRenderType==RenderType.fillToZero 
                            || oldRenderType==RenderType.stairSteps ) {
                        return false;
                    } else {
                        return false;
                    }
                }
            }
        }
    }

    private static String[] getDimensionNames( QDataSet ds ) {

        String[] depNames = new String[ds.rank()];
        for (int i = 0; i < ds.rank(); i++) {
            depNames[i] = "dim" + i;
            QDataSet dep0 = (QDataSet) ds.property("DEPEND_" + i);
            if (dep0 != null) {
                String dname = (String) dep0.property(QDataSet.NAME);
                if (dname != null) {
                    depNames[i] = dname;
                }
            }
        }

        return depNames;
    }

    private static Units[] getDimensionUnits( QDataSet ds ) {

        Units[] depUnits = new Units[ds.rank()];
        for (int i = 0; i < ds.rank(); i++) {
            depUnits[i] = Units.dimensionless;
            QDataSet dep0 = (QDataSet) ds.property("DEPEND_" + i);
            if (dep0 != null) {
                Units u = (Units) dep0.property(QDataSet.UNITS);
                if (u != null) {
                    depUnits[i] = u;
                }
            }
        }

        return depUnits;
    }

    /**
     * calculate the slices based on the slices command.  This results in 
     * a trivial amount of extra work but makes the code cleaner.
     * 
     * @param fillDs
     * @param slicePref
     * @return
     */
    private static String guessSliceSlices( QDataSet fillDs, List<Integer> slicePref ) {
        StringBuilder newResult= new StringBuilder( "|slices(" );
        String[] slices= new String[fillDs.rank()];
        for ( int i=0; i<fillDs.rank(); i++ ) slices[i]= "':'";

        List<Integer> slicePref1= new ArrayList();
        slicePref1.addAll(slicePref);
        slicePref= slicePref1;

        int ndim= fillDs.rank();

        int nslice= fillDs.rank()-2;

        List<Integer> qube= new ArrayList(); // we remove elements from this one.
        int[] a= DataSetUtil.qubeDims(fillDs);
        for ( int i=0; i<a.length; i++ ) {
            qube.add(a[i]);
        }

        for ( int islice=0; islice<nslice; islice++ ) {
            int sliceIndex = 0;
            int bestSlice = 0;

            for (int i = 0; i < a.length; i++) {
                if (slicePref.get(i) > bestSlice) {
                    sliceIndex = i;
                    bestSlice = slicePref.get(i);
                }
            }

            slicePref.set( sliceIndex, 0 );

            slices[sliceIndex]= String.valueOf( qube.get(sliceIndex)/2 );
        }

        for ( int i=0; i<ndim; i++ ) {
            newResult.append( slices[i] );
            if ( i<ndim-1 ) newResult.append( "," );
        }

        newResult.append( ")" );
        return newResult.toString();
    }

    /**
     * guess the best sprocess to reduce the rank to something we can display.
     * guess the best dimension to slice by default, based on metadata.  Currently,
     * this looks for the names lat, lon, and angle.
     *
     * @param fillDs
     * @return sprocess string like "slice1(0)"
     */
    private static String guessSlice( QDataSet fillDs ) {
        String[] depNames= getDimensionNames(fillDs);
        Units[] depUnits= getDimensionUnits(fillDs);

        int lat = -1, lon = -1;
        
        int rank= fillDs.rank();

        if ( rank==3 ) {
            if ( Schemes.isPolyMesh(fillDs) ) {
                return "";
            }
        }
                        
        List<Integer> slicePref = new ArrayList( rank );
        for ( int i=0; i<fillDs.rank(); i++ ) slicePref.add(2);
                
        for (int i = 0; i <rank; i++) {
            String n = depNames[i].toLowerCase();
            Units u= depUnits[i];
            if (n.startsWith("lat")) {
                slicePref.set( i,0 );
                lat = i;
            } else if (n.startsWith("lon")) {
                slicePref.set( i,0 );
                lon = i;
            } else if (n.contains("time") ) {
                slicePref.set( i,1 );
            } else if (n.contains("epoch") || UnitsUtil.isTimeLocation(u) ) {
                slicePref.set( i,1 );
            } else if (n.contains("angle")) {
                slicePref.set( i,4 );
            } else if (n.contains("alpha") ) { // commonly used for pitch angle in space physics
                slicePref.set( i,4 );
            } else if (n.contains("bundle")) {
                slicePref.set( i,4 );
            } else if ( u instanceof EnumerationUnits ) {
                slicePref.set( i,5 );
            } else if ( fillDs.property( "BUNDLE_"+i )!=null && fillDs.property("DEPEND_"+i)==null ) {
                slicePref.set( i,5 );
            }

        }

        List<Integer> qube= new ArrayList(); // we remove elements from this one.
        int[] a= DataSetUtil.qubeDims(fillDs);
        if ( a==null ) {
            return "|slice0(" + fillDs.length()/2+")";
        }
        for ( int i=0; i<rank; i++ ) {
            qube.add(a[i]);
        }

        boolean transpose= false;
        String result="";
        int nslice= fillDs.rank()-2;

        String newResult= guessSliceSlices( fillDs, slicePref );

        if ( rank>4 ) {
            return newResult;
        }
        
        for ( int islice=0; islice<nslice; islice++ ) {
            int sliceIndex = 0;
            int bestSlice = 0;
            boolean noPref= true;
            
            for (int i = 0; i <slicePref.size(); i++) {
                if ( i>0 && slicePref.get(i).equals(slicePref.get(i-1)) ) noPref= false;
                if (slicePref.get(i) > bestSlice) {
                    sliceIndex = i;
                    bestSlice = slicePref.get(i);
                }
            }

            // if we have large dims and one small dim (image), then pick small dim.
            if ( noPref ) {
                int[] qubeDims= DataSetUtil.qubeDims(fillDs);
                if ( qubeDims!=null ) {
                    int imin= -1;
                    int min= Integer.MAX_VALUE;
                    int nextMin= Integer.MAX_VALUE;
                    for ( int i=0; i<qubeDims.length; i++ ) {
                        if ( qubeDims[i]<min ) {
                            nextMin= min;
                            min= qubeDims[i];
                            imin= i;
                        }
                    }
                    if ( min<4 && nextMin>10 ) {
                        sliceIndex= imin;
                    }
                }
            }
            
            if ( Schemes.isArrayOfBoundingBox(fillDs) ) {
                return "";
            }
            
            // see line above about triangleMesh

            // pick a slice index near the middle, which is less likely to be all fill.
            int n= Math.max( 0, qube.get(sliceIndex)/2-1 );

            result+= "|slice"+sliceIndex+"("+n+")";
            if (lat > -1 && lon > -1 && lat < lon) {
                result+="|transpose()";
                transpose= true;
            }

            slicePref.remove(sliceIndex);
            qube.remove(sliceIndex);

        }

        if ( transpose ) {
            newResult+="|transpose()";
        }

        if ( nslice<2 ) {
            return result;
        } else {
            return newResult;
        }
        
        //return result;

    }

    private boolean isLastDimBundle( QDataSet ds ) {
        switch (ds.rank()) {
            case 1:
                return ds.property(QDataSet.BUNDLE_0)!=null;
            case 2:
                return ds.property(QDataSet.BUNDLE_1)!=null;
            case 3:
                boolean result= ds.property(QDataSet.BUNDLE_1,0)!=null;
                QDataSet dep1= (QDataSet) ds.property(QDataSet.DEPEND_1,0);
                if ( dep1!=null && ( dep1.property(QDataSet.UNITS) instanceof EnumerationUnits ) ) result=true;
                return result;
            default:
                return false;
        }
    }

    /**
     * return true for a set of labels which seem to be describing different
     * things.  This is just simply checking to see:<ul>
     * <li> if all the first characters are number, then it is similar
     * <li> if all the first characters are the same letter, then it is similar
     * <li> otherwise it is dissimilar.
     * @param chs the array of labels.
     * @return true if they appear to be differing.
     * @see https://sourceforge.net/p/autoplot/bugs/2571/
     */
    private boolean dissimilarChannels( String[] chs ) {
        if ( chs[0].length()==0 ) return true; // unnamed channels probably shouldn't happen
        char c= chs[0].charAt(0);
        char allStartWith= c;
        boolean allNumbers= Character.isDigit(c) || c=='.' ;
        for ( String ch: chs ) {
            if ( ch.length()==0 ) return true;
            c= ch.charAt(0);
            if ( allNumbers ) {
                if ( !Character.isDigit(c) && c!='.' ) {
                    return true;
                }
            } else {
                if ( c!=allStartWith ) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * This is the heart of the PlotElementController, and to some degree Autoplot.  In this routine, we are given
     * dataset and a renderType, and we need to reconfigure Autoplot to implement this.  This will add child elements when
     * children are needed, for example when a Vector time series is plotted, we need to add children for each component.
     * 
     * preconditions:<ul>
     *   <li>the new renderType has been identified.
     *   <li>The dataset to be rendered has been identified.
     * </ul>
     * postconditions:<ul>
     *   <li>old child plotElements have been deleted.
     *   <li>child plotElements have been added when needed.
     * </ul>
     * @param fillDs
     * @param renderType
     * @param control renderer-type specific controls, see Renderer.setControl.
     * @see Renderer#setControl(java.lang.String) 
     */
    private synchronized void resetPlotElement( QDataSet fillDs, RenderType renderType, String renderControl ) {
        logger.log(Level.FINE, "resetPlotElement({0} {1}) ele={2}", new Object[]{fillDs, renderType, plotElement});

        if (fillDs != null) {

            //boolean lastDimBundle= isLastDimBundle( fillDs );
            //boolean joinOfBundle= fillDs.property(QDataSet.JOIN_0)!=null && lastDimBundle;
            int ndim= Ops.dimensionCount(fillDs);
            boolean isxyz= SemanticOps.isBundle(fillDs) && fillDs.property(QDataSet.DEPEND_0)==null;
            boolean shouldSlice= ( fillDs.rank()>2 && ndim>3 && plotElement.isAutoComponent() && !isxyz );
            if ( renderType==RenderType.image && fillDs.rank()==3 ) {
                shouldSlice= false; //TODO: some how render types should indicate they can handle a slice.
            }
            
            QDataSet sliceDs= fillDs; // dataset after initial slicing
            String existingComponent= plotElement.getComponent();

            //logger.fine("fillDs="+ fillDs + "  renderType="+renderType  + "  existingComponent="+existingComponent );

            if ( shouldSlice && existingComponent.length()>0 ) {
                try {
                    sliceDs = DataSetOps.sprocess( existingComponent, fillDs, new NullProgressMonitor() );
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }


            boolean isWaveform= false;
            if ( SemanticOps.isRank2Waveform(fillDs) ) {
                isWaveform= true;
            }

            boolean shouldHaveChildren= 
                    fillDs.rank() == 2 
                    && !isxyz
                    && !isWaveform
                    &&  ( renderType == RenderType.hugeScatter 
                    || renderType==RenderType.series 
                    || renderType==RenderType.scatter 
                    || renderType==RenderType.stairSteps );
            //if ( joinOfBundle ) shouldHaveChildren= true;

            if ( fillDs.rank()==2 && SemanticOps.isBundle(fillDs) ) { //TODO: LANL has datasets with both BUNDLE_1 and DEPEND_1 set, so the user can pick.
                QDataSet bdesc= (QDataSet) fillDs.property(QDataSet.BUNDLE_1);
                Object context0= bdesc.property(QDataSet.CONTEXT_0,bdesc.length()-1); // in a bundleDescriptor, this can be a string.
                if ( null!=context0 && context0 instanceof String ) {
                    shouldHaveChildren= false;
                }
            }
            
            List<PlotElement> children= getChildPlotElements();
            boolean alreadyHaveChildren= !children.isEmpty();

            if ( alreadyHaveChildren && shouldHaveChildren ) {
                //shouldHaveChildren= false;
            }
            
            if ( logger.isLoggable(Level.FINER) ) {
                synchronized (dom) {
                    logger.finer("###############");
                    logger.finer(""+this.plotElement.getId()+" " +getChildPlotElements() );
                    logger.finer("shouldHaveChildren: "+shouldHaveChildren );
                    logger.finer("###############");
                }
            }
            
            String[] lnames = null;
            String[] llabels= null;
            if ( shouldHaveChildren ) {
                lnames= SemanticOps.getComponentNames(fillDs);
                llabels= SemanticOps.getComponentLabels(fillDs);
            }

            boolean weShallAddChildren= shouldHaveChildren;

            if ( !shouldHaveChildren || weShallAddChildren ) { // delete any old child plotElements
                List<PlotElement> childEles= getChildPlotElements();
                for ( PlotElement p : childEles ) {
                    PlotElementController pec= p.getController();
                    if ( dom.plotElements.contains(p) ) { 
                        dom.controller.deletePlotElement(p); //TODO: there are times when things change between the contains(p) and deletePlotElement(p).
                        PropertyChangeListener parentListener= pec.getParentComponentLister();
                        if ( parentListener!=null ) {
                            this.plotElement.removePropertyChangeListener( parentListener );
                        }
                        this.removePropertyChangeListener(dsfListener);

                    }
                    plotElement.getStyle().removePropertyChangeListener( pec.parentStyleListener );
                }
            }

            if ( !shouldSlice ) doResetRenderType(plotElement.getRenderType());
            setResetPlotElement(false);

            if ( resetRanges && !shouldSlice && !weShallAddChildren ) {
                boolean doTurnOn= getParentPlotElement()==null && renderer.isActive()==false;
                if ( doTurnOn ) {
                    renderer.setActive(true); // we need this to be on for doResetRanges
                }
                doResetRanges();
                setResetRanges(false);
                if ( doTurnOn ) {
                    renderer.setActive(false);
                }
            } else {
                // get properties like the title and yrange for a stack of line plots.
                if ( weShallAddChildren ) doResetRanges();
            }

            if ( shouldHaveChildren ) {
                renderer.setActive(false);
                plotElement.setDisplayLegend(false);
            }

            if ( shouldSlice ) {
                String component= guessSlice( sliceDs );
                setSliceAutoranges( sliceShouldAutorange(fillDs, component) );
                if ( !existingComponent.equals("") ) {
                    if ( component.equals(existingComponent) ) {
                        logger.fine("here again...");
                    }
                    plotElement.setComponentAutomatically( existingComponent + component );
                } else {
                    plotElement.setComponentAutomatically( component );  // it'll reenter this code now.  problem--autorange is based on slice.
                }
                doResetRenderType(plotElement.getRenderType());
                return;
            }

            // add additional plotElements when it's a bundle of rank1 datasets.
            if ( weShallAddChildren ) {

                DomLock lock = dom.controller.mutatorLock();
                lock.lock("Add Child Elements");
                try {
                    Color c = plotElement.getStyle().getColor();
                    Color fc= plotElement.getStyle().getFillColor();
                    Plot domPlot = dom.controller.getPlotFor(plotElement);

                    int count= Math.min(QDataSet.MAX_UNIT_BUNDLE_COUNT, fillDs.length(0));
                    List<PlotElement> cp = new ArrayList<>(count);
                    int nsubsample= 1 + ( count-1 ) / 12; // 1-12 no subsample, 13-24 1 subsample, 25-36 2 subsample, etc.

                    // check for inconsistencies in names, which might indictate that these are not similar channels
                    boolean dissimilarChannels= dissimilarChannels(lnames);
                    if ( dissimilarChannels ) {
                        nsubsample= 1 + ( count-1 ) / 64; // 1-64 no subsample, 65...
                    }
                    
                    //check for non-unique labels, or labels that are simply numbers.
                    boolean uniqLabels= true;
                    assert lnames!=null;
                    Pattern p= Pattern.compile("ch_\\d+");
                    for ( int i=0;i<lnames.length; i++ ) {
                        if ( AutoplotUtil.isParsableDouble( lnames[i] ) ) uniqLabels= false;
                        if ( p.matcher(lnames[i]).matches() ) uniqLabels= false;
                        if ( uniqLabels ) {
                            for ( int j=i+1; j<lnames.length; j++ ) {
                                if ( lnames[i].equals(lnames[j]) ) uniqLabels= false;
                            }
                        }
                    }
                    
                    for (int i = 0; i < count; i++) {
                        //long t0= System.currentTimeMillis();
                        PlotElement ele = dom.controller.makeChildPlotElement(plotElement, domPlot, dsf);
                        cp.add(ele);
                        plotElement.getStyle().addPropertyChangeListener( ele.controller.parentStyleListener );
                        ele.getStyle().setColor(deriveColor(c, i/nsubsample));
                        ele.getStyle().setFillColor( deriveColor(fc,i/nsubsample).brighter() );
                        String s= existingComponent;
                        String label1= lnames[i];
                        if ( s.equals("") && uniqLabels ) {
                            s= lnames[i];
                            QDataSet ds1= DataSetOps.unbundle(fillDs,i);
                            String l1= (String) ds1.property(QDataSet.LABEL);
                            if ( l1==null ) { // TODO: kludge: das2 dataset doesn't unbundle properly and looses LABEL.
                                l1= llabels[i];
                            }
                            if ( l1!=null ) {
                                label1= l1;
                            }
                        } else {
                            if ( s.equals("") ) {
                                QDataSet ds1= DataSetOps.unbundle(fillDs,i);
                                QDataSet context= (QDataSet) ds1.property(QDataSet.CONTEXT_0);
                                if ( context!=null ) {
                                    if ( context.rank()==1 ) {
                                        context= ArrayDataSet.copy(( QDataSet) ds1.property(QDataSet.CONTEXT_0) );
                                        context= Ops.putProperty( context, QDataSet.DELTA_MINUS, null );
                                        context= Ops.putProperty( context, QDataSet.DELTA_PLUS, null );
                                        context= Ops.putProperty( context, QDataSet.BIN_MINUS, null );
                                        context= Ops.putProperty( context, QDataSet.BIN_PLUS, null );
                                        context= Ops.extent( context);
                                        if ( Ops.valid( context.slice(0) ).value()>0 ) {
                                            if ( context.value(0)==context.value(1) ) {
                                                label1= context.slice(0).toString();
                                            } else {
                                                label1= context.toString();
                                            }
                                        } else {
                                            label1= "fill";
                                        }
                                    } else {
                                        if ( !"slice1".equals( context.property(QDataSet.NAME) ) ) { // check for default name.
                                            label1= context.toString(); // rank 0.
                                        }
                                    }
                                    s= lnames[i];
                                } else {
                                    if ( uniqLabels ) {
                                        label1= llabels[i];
                                        s= lnames[i];
                                    } else {
                                        label1= "ch_"+i;
                                        s= s+"|unbundle('ch_"+i+"')";
                                    }
                                }
                                
                            } else {
                                if ( uniqLabels ) {
                                    s= s+"|unbundle('"+lnames[i]+"')";
                                } else {
                                    s= s+"|unbundle('ch_"+i+"')";
                                }
                                //addParentComponentListener(plotElement,ele);
                                label1= llabels[i];
                            }
                        }
                        ele.setComponentAutomatically(s);
                        ele.setActive(false); // setComponentAutomatically resets this
                        ele.setDisplayLegend(true);
                        ele.setLegendLabelAutomatically(label1);
                        ele.setRenderTypeAutomatically(plotElement.getRenderType()); // this creates the das2 SeriesRenderer.
                        ele.controller.maybeCreateDasPeer();
                        //ele.controller.setDataSet(fillDs, false);
                    }
                    for ( int i=0; i<count; i++ ) {
                        PlotElement ele= cp.get(i);
                        if ( i % nsubsample == 0 ) {
                            ele.setActive(true); //TODO: test load/save
                            ele.controller.getRenderer().setActive(true);
                        } else {
                            ele.setActive(false);
                            ele.controller.getRenderer().setActive(false);
                        }
                    }
                    renderer.setActive(false);
                    setChildPlotElements(cp);
                } finally {
                    lock.unlock();
                }
            } else {
                if ( plotElement.controller.getParentPlotElement()==null ) {
                    renderer.setControl(renderControl);
                    renderer.setActive(true);
                }
            }

        } else {
            doResetRenderType(plotElement.getRenderType());
            
        }
    }

    /**
     * When the data source changes, we will need to autorange so that the axis
     * units are set correctly and things are in a consistent state.  One exception
     * to this is when we are doing state transistions with save/load redo/undo, where
     * we need to avoid autoranging.  Note a kludge in ApplicationController sync
     * sets resetRanges to false after the load.
     */
    PropertyChangeListener dataSourceDataSetListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt,"dataSourceDataSetListener");            
            if ( dsfReset ) {
                setResetComponent(true);
                setResetPlotElement(true);
                setResetRanges(true);
                plotElement.setAutoLabel(true);
                plotElement.setAutoComponent(true);
                plotElement.setAutoRenderType(true);
                maybeSetPlotAutorange();
                //setDsfReset(false);
            }
        }
    };
    
     /**
     * to experiment with http://sourceforge.net/p/autoplot/bugs/1511/,
     * see if the xaxis is bound to other xaxes already.
     * @param p the plot.
     * @return true if the xaxis is bound to other xaxes.
     */
    private boolean xaxisFreeFromBindings( Plot p ) {
        boolean isNotBound= true;
        List<BindingModel> l = DomUtil.findBindings( dom, p.xaxis, Axis.PROP_RANGE );
        String xaxisId= p.getXaxis().getId();
        for ( BindingModel bm : l ) {
            if ( bm.getSrcId().equals(dom.getId() ) ) {
                List<BindingModel> l2 = DomUtil.findBindings( dom, dom, Application.PROP_TIMERANGE );
                if ( l2.size()>1 ) isNotBound= false;  // first one is the one we already found.
            } else if ( bm.getSrcId().equals( xaxisId ) ) {
                if ( !bm.getDstId().equals(xaxisId) && bm.getDstProperty().equals(Axis.PROP_RANGE) ) {
                    isNotBound= false;
                }
            } else if ( bm.getDstId().equals( xaxisId ) ) {
                if ( !bm.getSrcId().equals(xaxisId) && bm.getSrcProperty().equals(Axis.PROP_RANGE ) ) {
                    isNotBound= false;
                }
            }
        }
        return isNotBound;
    }

    /**
     * we'd like the plot to autorange, so check to see if we are the only
     * plotElement, and if so, set its autorange and autoLabel flags.
     */
    private void maybeSetPlotYZAutorange() {
        Plot p= dom.controller.getPlotFor(plotElement);
        if ( p==null ) return;
        List<PlotElement> eles= dom.controller.getPlotElementsFor(p);
        if ( DomUtil.oneFamily(eles) ) {
            p.getYaxis().setAutoRange(true);
            p.getZaxis().setAutoRange(true);
            p.getYaxis().setAutoLabel(true);
            p.getZaxis().setAutoLabel(true);
            p.setAutoLabel(true);
            p.setAutoBinding(true);
        }
    }    
    /**
     * we'd like the plot to autorange, so check to see if we are the only
     * plotElement, and if so, set its autorange and autoLabel flags.
     */
    private void maybeSetPlotAutorange() {
        Plot p= dom.controller.getPlotFor(plotElement);
        if ( p==null ) return;
        List<PlotElement> eles= dom.controller.getPlotElementsFor(p);
        if ( DomUtil.oneFamily(eles) ) {
            p.getXaxis().setAutoRange( xaxisFreeFromBindings(p) );
            p.getYaxis().setAutoRange(true);
            p.getZaxis().setAutoRange(true);
            p.getXaxis().setAutoLabel(true);
            p.getYaxis().setAutoLabel(true);
            p.getZaxis().setAutoLabel(true);
            p.setAutoLabel(true);
            p.setAutoBinding(true);
        }
    }
    private void setDataSourceFilterController(final DataSourceController dsc) {
        dsc.addPropertyChangeListener(DataSourceController.PROP_FILLDATASET, fillDataSetListener);
        dsc.addPropertyChangeListener(DataSourceController.PROP_DATASOURCE, dataSourceDataSetListener);
        dsc.addPropertyChangeListener(DataSourceController.PROP_EXCEPTION, exceptionListener);
    }
    /**
     * true indicates the controller should autorange next time the fillDataSet is changed.
     */
    public static final String PROP_RESETRANGES = "resetRanges";

    /**
     * true indicates the controller should autorange next time the fillDataSet is changed.
     */
    private boolean resetRanges = false;

    public boolean isResetRanges() {
        return resetRanges;
    }

    public void setResetRanges(boolean resetRanges) {
        logger.log(Level.FINE, "{0}.setResetRanges({1})", new Object[] { plotElement.id, resetRanges } );
        boolean oldResetRanges = this.resetRanges;
        this.resetRanges = resetRanges;
        propertyChangeSupport.firePropertyChange(PROP_RESETRANGES, oldResetRanges, resetRanges);
    }

    /**
     * true indicates the controller should install a new renderer to implement the
     * renderType selection.  This may mean that we introduce or remove child plotElements.
     * This implies resetRenderType.
     */
    public static final String PROP_RESETPLOTELEMENT = "resetPlotElement";
    private boolean resetPlotElement = false;

    public boolean isResetPlotElement() {
        return resetPlotElement;
    }

    public void setResetPlotElement(boolean resetPlotElement) {
        logger.log(Level.FINE, "{0}.setResetPlotElement({1})", new Object[] { plotElement.id, resetPlotElement } );
        boolean old = this.resetPlotElement;
        this.resetPlotElement = resetPlotElement;
        propertyChangeSupport.firePropertyChange(PROP_RESETPLOTELEMENT, old, resetPlotElement);
    }

    /**
     * true indicates that the component should be reset when the dataset arrives.  This
     * is added as a controller property so that clients can clear this setting if
     * they do not want the component to be reset.  This is only considered if resetPlotElement is
     * set.
     */
    public static final String PROP_RESETCOMPONENT = "resetComponent";

    private boolean resetComponent = false;

    public boolean isResetComponent() {
        return resetComponent;
    }

    public void setResetComponent(boolean resetComponent) {
        boolean oldResetComponent = this.resetComponent;
        this.resetComponent = resetComponent;
        propertyChangeSupport.firePropertyChange(PROP_RESETCOMPONENT, oldResetComponent, resetComponent);
    }


    /**
     * true indicates the peer should be reset to the current renderType.
     */
    public static final String PROP_RESETRENDERTYPE = "resetRenderType";
    private boolean resetRenderType = false;

    public boolean isResetRenderType() {
        return resetRenderType;
    }

    public void setResetRenderType(boolean resetRenderType) {
        boolean oldResetRenderType = this.resetRenderType;
        this.resetRenderType = resetRenderType;
        propertyChangeSupport.firePropertyChange(PROP_RESETRENDERTYPE, oldResetRenderType, resetRenderType);
    }

    /**
     * when true, a change in the DataSourceFilter should reset the plotElement.
     */
    public static final String PROP_DSFRESET = "dsfReset";

    private boolean dsfReset = true;

    public boolean isDsfReset() {
        return dsfReset;
    }

    public void setDsfReset(boolean dsfReset) {
        boolean oldDsfReset = this.dsfReset;
        this.dsfReset = dsfReset;
        propertyChangeSupport.firePropertyChange(PROP_DSFRESET, oldDsfReset, dsfReset);
    }

    /**
     * when true, changing the slice index should cause autorange.
     */
    private boolean sliceAutoranges = false;
    public static final String PROP_SLICEAUTORANGES = "sliceAutoranges";

    public boolean isSliceAutoranges() {
        return sliceAutoranges;
    }

    public void setSliceAutoranges(boolean sliceAutoranges) {
        boolean oldSliceAutoranges = this.sliceAutoranges;
        this.sliceAutoranges = sliceAutoranges;
        propertyChangeSupport.firePropertyChange(PROP_SLICEAUTORANGES, oldSliceAutoranges, sliceAutoranges);
    }

    private static final AtomicInteger renderCount= new AtomicInteger();

    protected Renderer renderer = null;

    public Renderer getRenderer() {
        return renderer;
    }

    /**
     * set the renderer controlled by this PlotElement controller.  
     * This should be used with caution.
     * @param renderer 
     * @see external.PlotCommand
     */
    public void setRenderer(Renderer renderer) {
        logger.entering("PlotElementController","setRenderer");
        Renderer oldRenderer= this.renderer;
        ApplicationController ac = this.dom.controller;
        if ( oldRenderer!=null ) {
            ac.unbind( plotElement, PlotElement.PROP_LEGENDLABEL, oldRenderer, Renderer.PROP_LEGENDLABEL );
            ac.unbind( plotElement, PlotElement.PROP_DISPLAYLEGEND, oldRenderer, Renderer.PROP_DRAWLEGENDLABEL);
            ac.unbind( plotElement, PlotElement.PROP_RENDERCONTROL, oldRenderer, Renderer.PROP_CONTROL );
            ac.unbind( plotElement, PlotElement.PROP_ACTIVE, oldRenderer, Renderer.PROP_ACTIVE );
        }
        this.renderer = renderer;
        ac.unbindImpl(node);
        ac.unbindImpl(((PlotElement)node).getStyle());

        if ( node!=plotElement ) {
            logger.fine("node!=plotElement");
        }
        if (renderer instanceof SeriesRenderer) {
            bindToSeriesRenderer((SeriesRenderer) renderer);
        } else if (renderer instanceof SpectrogramRenderer) {
            bindToSpectrogramRenderer((SpectrogramRenderer) renderer);
        } else if (renderer instanceof HugeScatterRenderer) {
            bindToImageVectorDataSetRenderer((HugeScatterRenderer) renderer);
        } else if (renderer instanceof EventsRenderer ) {
            bindToEventsRenderer((EventsRenderer)renderer);
        } else if (renderer instanceof DigitalRenderer ) {
            bindToDigitalRenderer((DigitalRenderer)renderer);
        } else if (renderer instanceof PolarPlotRenderer ) {
            bindToPolarPlotRenderer((PolarPlotRenderer)renderer);
        } else if (renderer instanceof TickCurveRenderer ) {
            bindToTickCurveRenderer((TickCurveRenderer)renderer);
        } else if (renderer instanceof BoundsRenderer ) {
            bindToBoundsRenderer((BoundsRenderer)renderer);
        } else if (renderer instanceof ContoursRenderer ) {
            bindToContoursRenderer((ContoursRenderer)renderer);
        }
        Plot mip= ac.getPlotFor(plotElement);
        if ( mip!=null ) {  // transitional state
            JMenuItem mi= mip.getController().getPlotElementPropsMenuItem();
            if ( mi!=null ) mi.setIcon( renderer.getListIcon() );
        }
        renderer.setId( "rend_"+plotElement.getId()+"_"+String.format( "%04d", PlotElementController.renderCount.incrementAndGet() ) ); // for debugging, make unique names
        ac.bind(plotElement, PlotElement.PROP_LEGENDLABEL, renderer, Renderer.PROP_LEGENDLABEL, getLabelConverter() );
        ac.bind(plotElement, PlotElement.PROP_DISPLAYLEGEND, renderer, Renderer.PROP_DRAWLEGENDLABEL);
        ac.bind(plotElement, PlotElement.PROP_RENDERCONTROL, renderer, Renderer.PROP_CONTROL );
        ac.bind(plotElement, PlotElement.PROP_ACTIVE, renderer, Renderer.PROP_ACTIVE );
        logger.exiting("PlotElementController","setRenderer");
    }

    /**
     * <p>Do initialization to get the plotElement and attached plot to have reasonable
     * settings.</p>
     * preconditions:<ul>
     *   <li>renderType has been identified for the plotElement.
     * </ul>
     * postconditions:<ul>
     *   <li>plotElement's plotDefaults are set based on metadata and autoranging.
     *   <li>listening plot may invoke its resetZoom method.
     * </ul>
     */
    private void doResetRanges() {

        setStatus("busy: do autorange");

        changesSupport.performingChange(this, PENDING_RESET_RANGE);

        DataSourceFilter ldsf= getDataSourceFilter(); // make local copy to avoid any synchronization problems and to clean up code.
        DataSourceController dsc= ldsf!=null ? ldsf.getController() : null;
        
        if ( dsc==null ) { // don't think this happens.
            logger.warning("expected dsc to be non-null.");
            return; 
        }
        
        try {
            Plot plot = dom.controller.getPlotFor(plotElement);
            if ( plot==null ) {
                throw new NullPointerException("unable to find plot for plotElement: "+plotElement );
            }

            logger.log(Level.FINE, "renderType: {0}", plotElement.getRenderType());
            
            PlotElement peleCopy = (PlotElement) plotElement.copy();
            peleCopy.setId("");
            peleCopy.setParent("");
            peleCopy.getPlotDefaults().syncTo( plot, Arrays.asList(DomNode.PROP_ID, Plot.PROP_ROWID, Plot.PROP_COLUMNID) );

            logger.log(Level.FINE, "doResetRanges for {0}", dsc);

            QDataSet fillDs = dsc.getFillDataSet();
            Map props= dsc.getFillProperties();
            if ( props==null || fillDs==null ) { // need a atomic operation here...
                return;
            }

            String comp= plotElement.getComponent();
            if ( comp.length()>0 ) {
                try {
                    fillDs = processDataSet(comp, fillDs);
                } catch (Exception ex) {
                    logger.log( Level.WARNING, null, ex );
                    return;
                }
                if ( fillDs==null ) {
                    throw new IllegalArgumentException("processDataSet resulted in null result: "+comp);
                }
                props= processProperties( comp, props ); //TODO: support components
                if ( props.isEmpty() ) { // many of the filters drop the properties
                  props= AutoplotUtil.extractProperties(fillDs);
                }
            }

            if (dom.getOptions().isAutolabelling()) { //TODO: this is pre-autoLabel property.

                doMetadata(peleCopy, props, fillDs );

                String appliedFilters = dsc.getAppliedFiltersString();
                if ( appliedFilters!=null ) appliedFilters= appliedFilters.trim();
                String title = peleCopy.getPlotDefaults().getTitle();
                if ( fillDs.property(QDataSet.CONTEXT_0)!=null && dsc.reduceDataSetString!=null ) { 
                    title += "!c%{CONTEXT}";
                } else if ( !plotElement.getComponent().equals("") ) {
                    title += "!c%{CONTEXT}";
                } else if ( appliedFilters != null && appliedFilters.length()>0 ) {
                    title += "!c"+appliedFilters;
                } else if ( fillDs.property(QDataSet.CONTEXT_0)!=null ) {
                    title += "!c%{CONTEXT}";
                }
                peleCopy.getPlotDefaults().setTitle(title);
            }

            if (dom.getOptions().isAutoranging()) { //this is pre-autorange property, but saves time if we know we won't be autoranging.
                
                logger.fine("doAutoranging");
                //long t0= System.currentTimeMillis();

                doAutoranging( peleCopy, props, fillDs, false );
                
                RenderType rt= peleCopy.getRenderType();
                if ( rt==RenderType.series 
                        || rt==RenderType.scatter 
                        || rt==RenderType.colorScatter 
                        || rt==RenderType.hugeScatter 
                        || rt==RenderType.fillToZero 
                        || rt==RenderType.stairSteps ) {
                    if (fillDs.length() > LARGE_DATASET_COUNT && !( rt==RenderType.colorScatter ) ) {
                        logger.fine("dataset has many points, turning off psym");
                        peleCopy.getStyle().setSymbolConnector(PsymConnector.SOLID);  // Interesting...  This was exactly the opposite of what I should do...
                        peleCopy.getStyle().setPlotSymbol(DefaultPlotSymbol.NONE);   
                    } else {
                        if (fillDs.length() > SYMSIZE_DATAPOINT_COUNT) {
                            logger.fine("dataset has a more than few points, using small symbols");
                            peleCopy.getStyle().setSymbolSize(1.0);
                            peleCopy.getStyle().setPlotSymbol( DefaultPlotSymbol.CIRCLES ); // 1215: make this NONE eventually
                        } else {
                            logger.fine("dataset has few points, using small large symbols");
                            peleCopy.getStyle().setSymbolSize(3.0);
                            if ( rt==RenderType.stairSteps ) {
                                peleCopy.getStyle().setPlotSymbol(DefaultPlotSymbol.NONE);
                            } else {
                                peleCopy.getStyle().setPlotSymbol(DefaultPlotSymbol.CIRCLES);
                            }
                        }
                    }
                }
                
                //logger.fine("  "+( System.currentTimeMillis()-t0 )+" ms spent autoranging "+fillDs );

                TimeSeriesBrowse tsb= dsc.getTsb();
                if ( tsb!=null ) {
                    if ( fillDs.rank()==0 ) {
                        logger.fine("data is rank 0, no autoranging needs to be done.");
                    } else {
                        QDataSet xds= SemanticOps.xtagsDataSet(fillDs);
                        Units xunits;
                        if ( xds.rank()<=1 ) {
                            xunits= (Units)xds.property(QDataSet.UNITS);
                        } else {
                            //JOIN dataset
                            xunits= (Units)xds.property(QDataSet.UNITS,0);
                        }
                        if ( xunits!=null && UnitsUtil.isTimeLocation( xunits ) ) {
                            DatumRange tr= tsb.getTimeRange();
                            if ( tr==null ) {
                                logger.fine( "tsb contains no timerange");
                            }
                            peleCopy.getPlotDefaults().getXaxis().setRange( tr );
                        }
                    }
                }

                Renderer newRenderer = getRenderer();
                if (newRenderer instanceof SeriesRenderer) {
                    QDataSet d = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
                    if (d != null) {
                        ((SeriesRenderer) newRenderer).setCadenceCheck((d.property(QDataSet.CADENCE) != null));
                    } else {
                        ((SeriesRenderer) newRenderer).setCadenceCheck(true);
                    }
                }

            } else {
                setStatus( "autoranging is disabled" );
                logger.info( "autoranging is disabled" );
            }

            if ( plotElement.getComponent().equals("") && plotElement.isAutoLabel() ) plotElement.setLegendLabelAutomatically( peleCopy.getLegendLabel() );

            // bugfix1157: the autorange property has a meaning now, and it is set for each plot element.
            //peleCopy.getPlotDefaults().getXaxis().setAutoRange(true); // this is how we distinguish it from the original, useless plot defaults.
            //peleCopy.getPlotDefaults().getYaxis().setAutoRange(true);
            //peleCopy.getPlotDefaults().getZaxis().setAutoRange(true);

            if ( logger.isLoggable(Level.FINER) ) {
                logger.finer( String.format( "done, autorange  x:%s, y:%s ",
                        peleCopy.getPlotDefaults().getXaxis().getRange().toString(),
                        peleCopy.getPlotDefaults().getYaxis().getRange().toString() ) );
            }

            plotElement.setPlotDefaults( peleCopy.getPlotDefaults() );  // bug https://sourceforge.net/p/autoplot/bugs/283/ runs through here
            plotElement.style.syncTo( peleCopy.style );
            plotElement.renderType= peleCopy.renderType;  // don't fire an event
            // and hope that the plot is listening.

            if ( dom.getOptions().isAutoranging() ) {
                setStatus("done, autorange");
            }
        } finally {
            changesSupport.changePerformed(this, PENDING_RESET_RANGE);
        }
    }


    /**
     * extract properties from the data and metadata to get axis labels, fill values, and
     * preconditions:<ul>
     * <li>fillData is set.
     * <li>fillProperties is set.
     * </ul>
     * postconditions:<ul>
     * <li>metadata is inspected to get axis labels, fill values, etc.
     * <li>renderType is determined and set.
     * </ul>
     * @param autorange
     * @param interpretMetadata
     */
    private static void doMetadata( PlotElement peleCopy, Map<String,Object> properties, QDataSet fillDs ) {

        peleCopy.getPlotDefaults().getXaxis().setLabel("");
        peleCopy.getPlotDefaults().getYaxis().setLabel("");
        peleCopy.getPlotDefaults().getZaxis().setLabel("");
        peleCopy.getPlotDefaults().setTitle("");
        peleCopy.setLegendLabelAutomatically("");
        
        doInterpretMetadata(peleCopy, properties, peleCopy.getRenderType());

        PlotElementUtil.unitsCheck(properties, fillDs); // DANGER--this may cause overplotting problems in the future by removing units

    }

    /**
     * pull out axis labels into plotDefaults.
     * @param peleCopy
     * @param properties
     * @param spec 
     */
    private static void doInterpretMetadata( PlotElement peleCopy, Map properties, RenderType spec) {

        Object v;
        final Plot plotDefaults = peleCopy.getPlotDefaults();

        if ((v = properties.get(QDataSet.TITLE)) != null) {
            plotDefaults.setTitle((String) v);
        }
        String legendLabel= null;
        if ((v = properties.get(QDataSet.NAME)) != null) {
            legendLabel= (String)v;
        }
        if ((v = properties.get(QDataSet.LABEL)) != null) {
            legendLabel= (String)v;
        }
        if ( legendLabel!=null ) {
            peleCopy.setLegendLabelAutomatically((String) legendLabel);
        }

        if ( spec == RenderType.spectrogram || spec==RenderType.nnSpectrogram 
                || spec==RenderType.stackedHistogram ) {
            if ( (v = properties.get(QDataSet.SCALE_TYPE)) != null) {
                plotDefaults.getZaxis().setLog(v.equals("log"));
            }

            if ( (v = properties.get(QDataSet.LABEL)) != null) {
                plotDefaults.getZaxis().setLabel((String) v);
            }

            if ( (v = properties.get(QDataSet.DEPEND_1)) != null) {
                Map m = (Map) v;
                Object v2 = m.get(QDataSet.LABEL);
                if (v2 != null) {
                    plotDefaults.getYaxis().setLabel((String) v2);
                }

            }
            Map m= (Map)properties.get(QDataSet.PLANE_0);
            if ( m!=null ) {
                v = m.get(QDataSet.LABEL);
                if (v != null) {
                    plotDefaults.getZaxis().setLabel((String) v);
                }
                v= m.get(QDataSet.TITLE);
                if (v != null) {
                    plotDefaults.setTitle((String) v);
                }
            }
        } else if ( spec == RenderType.image ) {
            Map<String,Object> yprop, xprop=null, prop;
            xprop= (Map<String, Object>) properties.get( QDataSet.DEPEND_0 );
            yprop= (Map<String, Object>) properties.get( QDataSet.DEPEND_1 ); 
            
            if ( xprop!=null ) {
                v = xprop.get(QDataSet.LABEL);
                if ( v!=null ) plotDefaults.xaxis.setLabel( (String)v );
                v = xprop.get(QDataSet.SCALE_TYPE);
                if ( v!=null) plotDefaults.getXaxis().setLog(v.equals("log"));
            }

            if ( yprop!=null ) {
                v= (String) yprop.get(QDataSet.LABEL);
                if ( v!=null ) plotDefaults.yaxis.setLabel( (String)v );
                v = yprop.get(QDataSet.SCALE_TYPE);
                if ( v!=null) plotDefaults.getYaxis().setLog(v.equals("log"));
            }
            
        } else { // hugeScatter okay

            Map<String,Object> yprop, xprop=null, prop;

            QDataSet bundle1= (QDataSet) properties.get(QDataSet.BUNDLE_1);
            if ( bundle1!=null ) {
                xprop= (Map<String, Object>) properties.get( QDataSet.DEPEND_0 );
                if ( xprop==null ) {
                    xprop= DataSetUtil.getProperties( DataSetOps.slice0( bundle1, 0 ) );
                }
                if ( !(spec==RenderType.colorScatter ) ) {  // why would you ever want to use second case?  the nightly tests will tell for sure...
                    prop= properties;
                    yprop= properties;
                } else {
                    prop= DataSetUtil.getProperties( DataSetOps.slice0( bundle1, bundle1.length()-1 ) );
                    yprop=  DataSetUtil.getProperties( DataSetOps.slice0( bundle1, 1 ) ); // may be the same as prop.
                }
            } else {
                prop= properties;
                
                yprop= properties;
                v = properties.get(QDataSet.PLANE_0);
                if ( v!=null ) {
                    yprop= prop;
                    prop= (Map<String, Object>) v;
                }
            }

            if ( (v = yprop.get(QDataSet.SCALE_TYPE)) != null) {
                plotDefaults.getYaxis().setLog(v.equals("log"));
            }

            if ( (v = yprop.get(QDataSet.LABEL)) != null) {
                plotDefaults.getYaxis().setLabel((String) v);
            }

            if ( xprop!=null && (v = xprop.get(QDataSet.LABEL)) != null) {
                plotDefaults.getXaxis().setLabel((String) v);
            }

            if (spec == RenderType.colorScatter) {
                v = prop.get(QDataSet.LABEL);
                if (v != null) {
                    plotDefaults.getZaxis().setLabel((String) v);
                }
                v= prop.get(QDataSet.TITLE);
                if (v != null) {
                    plotDefaults.setTitle((String) v);
                }
            }


        }

        if ((v = properties.get(QDataSet.DEPEND_0)) != null) {
            Map m = (Map) v;
            Object v2 = m.get(QDataSet.LABEL);
            if ( v2 != null) {
                plotDefaults.getXaxis().setLabel((String) v2);
            }

        }

    }

    /**
     * This is the old updateFillSeries and updateFillSpectrogram code.  
     * 
     * This calculates
     * ranges and preferred symbol settings, and puts the values in peleCopy.plotDefaults.
     * The dom Plot containing this plotElement should be listening for changes in plotElement.plotDefaults,
     * and can then decide if it wants to use the autorange settings.
     *
     * This also sets the style node of the plotElement copy, so its values should be sync'ed as well.
     * 
     * @param peleCopy the plot element.
     * @param props metadata provided by the data source, converted to uniform QDataSet scheme (e.g. get(DEPEND_0).get(TYPICAL_MIN) )
     * @param fillDs the dataset
     */
    public static void doAutoranging( PlotElement peleCopy, Map<String,Object> props, QDataSet fillDs ) {
        doAutoranging( peleCopy, props, fillDs, false );
    }

    /**
     * copy the settings in bounds to the plot.  For example, datumRange(bounds.slice(0)) will be the new x-axis
     * setting.
     * @param p the plot
     * @param bounds the bounding box or cube.
     */
    private static void copyAutorange( Plot p, QDataSet bounds ) {
        assert Schemes.isBoundingBox(bounds);
        p.xaxis.setRange( DataSetUtil.asDatumRange( bounds.slice(0),true ) );
        p.xaxis.setLog( "log".equals( bounds.slice(0).property(QDataSet.SCALE_TYPE) ) );
        p.yaxis.setRange( DataSetUtil.asDatumRange( bounds.slice(1),true ) );
        p.yaxis.setLog( "log".equals( bounds.slice(1).property(QDataSet.SCALE_TYPE) ) );
        if ( bounds.length()>2 ) {
            p.zaxis.setRange( DataSetUtil.asDatumRange( bounds.slice(2),true ) );
            p.zaxis.setLog( "log".equals( bounds.slice(2).property(QDataSet.SCALE_TYPE) ) );
        } else {
            p.zaxis.setAutoRange(false);
        }
    }
    
    /**
     * This is the old updateFillSeries and updateFillSpectrogram code.  
     * 
     * 
     * This calculates ranges and preferred symbol settings, and puts the values
     * in peleCopy.plotDefaults.  The dom Plot containing this plotElement 
     * should be listening for changes in plotElement.plotDefaults,
     * and can then decide if it wants to use the autorange settings.
     *
     * This also sets the style node of the plotElement copy, so its values 
     * should be sync'ed as well.
     * 
     * This routine can be found by searching for "liver," since it is not the 
     * heart but pretty close to it.  (2024-01-19: I forgot about liver, was 
     * thinking autorange9)
     * 
     * @param peleCopy the plot element.
     * @param props metadata provided by the data source, converted to uniform 
     * QDataSet scheme (e.g. get(DEPEND_0).get(TYPICAL_MIN) )
     * @param fillDs the dataset
     * @param ignoreDsProps 
     */
    public static void doAutoranging( PlotElement peleCopy, Map<String,Object> props, QDataSet fillDs, boolean ignoreDsProps ) {

        RenderType spec = peleCopy.getRenderType();

        if ( fillDs.rank()==0 ) {
            if ( spec==RenderType.eventsBar ) {
                // do nothing
            } else {
                //logger.fine("rank 0");
                spec= RenderType.digital;
            }
        }

        if (props == null) {
            props = Collections.EMPTY_MAP;
        }

        logger.log(Level.FINE, "doAutoranging for {0}", spec);

        if ( spec == RenderType.spectrogram || spec==RenderType.nnSpectrogram || spec==RenderType.stackedHistogram ) {

            QDataSet xds = (QDataSet) SemanticOps.xtagsDataSet(fillDs);
            if (xds == null) {
                if ( fillDs.property(QDataSet.JOIN_0)!=null ) {
                    JoinDataSet ds= new JoinDataSet(2);
                    int xtag=0;
                    for ( int i=0; i<fillDs.length(); i++ ) {
                        QDataSet xds1= (QDataSet)fillDs.property(QDataSet.DEPEND_0,i);
                        if ( xds1==null ) {
                            xds1= Ops.linspace( xtag, xtag+fillDs.length(i)-1, fillDs.length(i) );
                            xtag= xtag + fillDs.length(i);
                        }
                        ds.join(xds1);
                    }
                    xds = ds;
                } else {
                    xds = DataSetUtil.indexGenDataSet(fillDs.length());
                }
            }

            QDataSet yds = (QDataSet) SemanticOps.ytagsDataSet(fillDs);
            
            // spectrogram mode with join dataset -> so use implicit findgen dataset for Y.
            if ( fillDs.rank()==2 && SemanticOps.isJoin(fillDs) ) {
                yds= null; // code below calculates this.
            }
            
            Map<String,Object> yprops= (Map) props.get(QDataSet.DEPEND_1);
            if (yds == null) {
                if ( fillDs.property(QDataSet.JOIN_0)!=null ) {
                    JoinDataSet ds= new JoinDataSet(2);
                    for ( int i=0; i<fillDs.length(); i++ ) {
                        QDataSet yds1= (QDataSet)fillDs.property(QDataSet.DEPEND_1,i);
                        if ( yds1==null ) {
                            int n= fillDs.slice(i).length();
                            yds1= Ops.linspace( 0, n-1, n );
                        }
                        ds.join(yds1);
                    }
                    yds = ds;
                } else if ( fillDs.property(QDataSet.JOIN_0)==null
                        && fillDs.length()>0 
                        && fillDs.property(QDataSet.DEPEND_0,0)!=null ) { 
                    JoinDataSet ds= new JoinDataSet(2);
                    Units u= null;
                    for ( int i=0; i<fillDs.length(); i++ ) {
                        QDataSet tds= (QDataSet)fillDs.property(QDataSet.DEPEND_0,i);
                        if ( u==null ) {
                            u= SemanticOps.getUnits(tds);
                        } else {
                            Units tu= SemanticOps.getUnits(tds);
                            if ( tu==null ) tu= Units.dimensionless;
                            if ( tu!=u ) {
                                throw new IllegalArgumentException("Unequal units: wanted "+u+" got "+tu );
                            }
                        }
                        ds.join(tds);
                    }
                    ds.putProperty(QDataSet.UNITS, u);
                    yds = ds;
                } else if ( fillDs.rank()>1 ) {
                    yds = DataSetUtil.indexGenDataSet(fillDs.length(0)); //TODO: QUBE assumed
                } else {
                    yds = DataSetUtil.indexGenDataSet(10); // later the user will get a message "renderer cannot plot..."
                    yprops= null;
                }
            }

            QDataSet zds;
            if ( fillDs.rank()>1 && yds.length()==fillDs.length(0) && yds.length()>3 ) { // Dataset might have bundle, we need to ignore at the right time.  If fillDs.length(0)==3 avoid a bug.
                zds= fillDs;
            } else {
                zds= SemanticOps.getDependentDataSet(fillDs);
                if ( Schemes.isLegacyXYZScatter(zds) ) zds= (QDataSet)fillDs.property(QDataSet.PLANE_0 );
            }

            Units xunits= SemanticOps.getUnits(xds); 
            Units yunits= SemanticOps.getUnits(yds);
            Units zunits= SemanticOps.getUnits(zds);

            if ( UnitsUtil.isOrdinalMeasurement( xunits ) || UnitsUtil.isOrdinalMeasurement(yunits) || UnitsUtil.isOrdinalMeasurement(zunits) ) {
                return;
            }

            AutoRangeUtil.AutoRangeDescriptor desc;
            
            desc = AutoRangeUtil.autoRange( zds, props, ignoreDsProps );  // do the Z autoranging first for debugging.
            logger.log(Level.FINE, "desc.range={0}", desc.range);

            AutoRangeUtil.AutoRangeDescriptor xdesc = AutoplotUtil.autoRange(xds, (Map) props.get(QDataSet.DEPEND_0), ignoreDsProps);
            logger.log(Level.FINE, "xdesc.range={0}", xdesc.range);

            AutoRangeUtil.AutoRangeDescriptor ydesc = AutoplotUtil.autoRange(yds, yprops, ignoreDsProps );
            logger.log(Level.FINE, "ydesc.range={0}", ydesc.range);
            
            peleCopy.getPlotDefaults().getZaxis().setRange(desc.range);
            peleCopy.getPlotDefaults().getZaxis().setLog(desc.log);

            logger.log(Level.FINE, "xaxis.isAutoRange={0}", peleCopy.getPlotDefaults().getXaxis().isAutoRange());

            peleCopy.getPlotDefaults().getXaxis().setLog(xdesc.log);
            peleCopy.getPlotDefaults().getXaxis().setRange(xdesc.range);
            peleCopy.getPlotDefaults().getYaxis().setLog(ydesc.log);
            peleCopy.getPlotDefaults().getYaxis().setRange(ydesc.range);
            
        } else if ( spec==RenderType.pitchAngleDistribution ) {
            QDataSet qube= PitchAngleDistributionRenderer.doAutorange( fillDs );
            if ( qube==null ) {
                // nothing
            } else {
                peleCopy.getPlotDefaults().getXaxis().setRange( DataSetUtil.asDatumRange( qube.slice(0),true ) );
                String label=  (String) qube.slice(0).property( QDataSet.LABEL );
                peleCopy.getPlotDefaults().getXaxis().setLabel( label==null ? "" : label );
                peleCopy.getPlotDefaults().getYaxis().setRange( DataSetUtil.asDatumRange( qube.slice(1),true ) );
                label=  (String) qube.slice(1).property( QDataSet.LABEL );
                peleCopy.getPlotDefaults().getYaxis().setLabel( label==null ? "" : label );
                peleCopy.getPlotDefaults().getZaxis().setRange( DataSetUtil.asDatumRange( qube.slice(2),true ) );
                peleCopy.getPlotDefaults().getZaxis().setLog( "log".equals( qube.slice(2).property(QDataSet.SCALE_TYPE) ) );
            }
        } else if ( spec==RenderType.polar ) {
            QDataSet qube= PolarPlotRenderer.doAutorange( fillDs );
            if ( qube==null ) {
                // nothing
            } else {
                copyAutorange( peleCopy.getPlotDefaults(), qube );
                String label=  (String) qube.slice(0).property( QDataSet.LABEL );
                peleCopy.getPlotDefaults().getXaxis().setLabel( label==null ? "" : label );
                label=  (String) qube.slice(1).property( QDataSet.LABEL );
                peleCopy.getPlotDefaults().getYaxis().setLabel( label==null ? "" : label );
            }
        } else if ( spec==RenderType.digital ) {
            QDataSet qube= DigitalRenderer.doAutorange( fillDs );
            if ( qube==null ) {
                // nothing
            } else {
                copyAutorange( peleCopy.getPlotDefaults(), qube );
            }
        } else if ( spec==RenderType.contour ) {
            QDataSet qube= ContoursRenderer.doAutorange( fillDs );
            if ( qube==null ) {
                // nothing
            } else {
                copyAutorange( peleCopy.getPlotDefaults(), qube );
            }
        } else if ( spec==RenderType.eventsBar ) {
            QDataSet qube= EventsRenderer.doAutorange( fillDs );
            if ( qube==null ) {
                // nothing
            } else {
                copyAutorange( peleCopy.getPlotDefaults(), qube );
                peleCopy.getPlotDefaults().getYaxis().setAutoRange(false);
                peleCopy.getPlotDefaults().getYaxis().setVisible(false);
            }
        } else if ( spec==RenderType.vectorPlot ) { //TODO: this should be discoverable
            QDataSet qube= VectorPlotRenderer.doAutorange( fillDs );
            if ( qube==null ) {
                // nothing
            } else {
                copyAutorange( peleCopy.getPlotDefaults(), qube );
            }
        } else if ( spec==RenderType.orbitPlot ) { 
            QDataSet qube= TickCurveRenderer.doAutorange( fillDs );
            if ( qube==null ) {
                // nothing
            } else {
                copyAutorange( peleCopy.getPlotDefaults(), qube );
            }
        } else if ( spec==RenderType.image ) {
            QDataSet qube= RGBImageRenderer.doAutorange( fillDs );
            if ( qube==null ) {
                // nothing
            } else {
                copyAutorange( peleCopy.getPlotDefaults(), qube );                
            }
        } else if ( spec==RenderType.internal ) {
            // nothing
        } else if ( spec==RenderType.bounds ) {
            QDataSet qube= BoundsRenderer.doAutorange( fillDs );
            if ( qube==null ) {
                // nothing
            } else {
                DatumRange xrange= DataSetUtil.asDatumRange( qube.slice(0),true ); // angle maybe
                DatumRange yrange= DataSetUtil.asDatumRange( qube.slice(1),true );
                
                if ( props.containsKey(QDataSet.RENDER_TYPE) ) { // Let's kludge in a check for polar, whee!
                    // The renderer has an unfortunate mistake where the controls will affect the autoranging.  This should
                    // probably be redone.
                    String rt= (String)props.get(QDataSet.RENDER_TYPE);
                    if ( rt.contains("polar=T") ) {
                        xrange= DatumRange.newRange( yrange.max().negative(), yrange.max() );
                        yrange= xrange;
                    }
                }
                peleCopy.getPlotDefaults().getXaxis().setRange( xrange );
                peleCopy.getPlotDefaults().getYaxis().setRange( yrange );
                peleCopy.getPlotDefaults().getZaxis().setAutoRange(false);
            }
            
        } else { // spec==RenderType.SERIES and spec==RenderType.HUGE_SCATTER

            AutoRangeUtil.AutoRangeDescriptor ydesc; //TODO: QDataSet can model AutoRangeDescriptors, it should be used instead.
            
            QDataSet depend0;

            if ( SemanticOps.isBundle(fillDs) ) {
                depend0= SemanticOps.xtagsDataSet(fillDs);
                if ( spec==RenderType.colorScatter ) {
                    ydesc= AutoRangeUtil.autoRange( DataSetOps.unbundle(fillDs, 1 ), props, ignoreDsProps );
                } else if ( spec==RenderType.scatter && fillDs.property(QDataSet.DEPEND_0)==null ) { 
                    ydesc= AutoRangeUtil.autoRange( DataSetOps.unbundle(fillDs, 1 ), props, ignoreDsProps );
                } else {
                    ydesc= AutoRangeUtil.autoRange( DataSetOps.unbundle(fillDs, fillDs.length(0)-1 ), props, ignoreDsProps ); 
                    Units u= ydesc.range.getUnits();
                    for ( int i=fillDs.length(0)-2; i>=0; i-- ) {
                        AutoRangeUtil.AutoRangeDescriptor ydesc1= AutoRangeUtil.autoRange( DataSetOps.unbundle(fillDs,i ), props, ignoreDsProps );
                        if ( ydesc1.range.getUnits().isConvertibleTo(u) ) { // Bx, By, Bz
                            if ( i==0 && fillDs.length(0)==2 ) {
                                // special case for T->X,Y where we are plotting X,Y, as in an orbit plot.
                            } else {
                                ydesc.range= DatumRangeUtil.union( ydesc.range, ydesc1.range );
                            }
                        } else {
                            Units u1;
                            u1= ydesc1.range.getUnits();
                            DatumRange r1= new DatumRange( ydesc1.range.min().doubleValue(u1), ydesc1.range.max().doubleValue(u1), u );
                            ydesc.range= DatumRangeUtil.union( ydesc.range, r1 );
                       }
                    }
                }
            } else {
                ydesc = AutoRangeUtil.autoRange( fillDs, props, ignoreDsProps );
                depend0 = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
            }

            peleCopy.getPlotDefaults().getYaxis().setLog(ydesc.log);
            peleCopy.getPlotDefaults().getYaxis().setRange(ydesc.range);

            QDataSet xds= depend0;
            if ( SemanticOps.isJoin(fillDs) && fillDs.length()>0 && fillDs.rank()==3 ) {
                xds= (QDataSet) fillDs.slice(0).property(QDataSet.DEPEND_0);
                if ( xds!=null ) {
                    for ( int i=1; i<fillDs.length(); i++ ) {
                        xds= Ops.concatenate( xds, (QDataSet)fillDs.slice(i).property(QDataSet.DEPEND_0) );
                    }
                } else {
                    int n=fillDs.slice(0).length();
                    for ( int i=1; i<fillDs.length(); i++ ) {
                        n+= fillDs.slice(i).length();
                    }
                    xds = DataSetUtil.indexGenDataSet(n);
                }
            } else {
                if (xds == null) {
                    xds = DataSetUtil.indexGenDataSet(fillDs.length());
                }
            }

            AutoRangeUtil.AutoRangeDescriptor xdesc;
            
            if ( fillDs.length()==1 && SemanticOps.isRank2Waveform(fillDs) ) {
                QDataSet waveform= Ops.flattenWaveform(fillDs);
                xds= SemanticOps.xtagsDataSet(waveform);
                xdesc= AutoRangeUtil.autoRange(xds, (Map) props.get(QDataSet.DEPEND_0), ignoreDsProps);
            } else {
                xdesc= AutoRangeUtil.autoRange(xds, (Map) props.get(QDataSet.DEPEND_0), ignoreDsProps);
            }

            peleCopy.getPlotDefaults().getXaxis().setLog(xdesc.log);
            if ( UnitsUtil.isOrdinalMeasurement( xdesc.range.getUnits() ) ) {
                xdesc.range= DatumRange.newRange( xdesc.range.min().doubleValue(xdesc.range.getUnits() ), xdesc.range.max().doubleValue(xdesc.range.getUnits()) );
            }
            peleCopy.getPlotDefaults().getXaxis().setRange(xdesc.range);

            if (spec == RenderType.colorScatter) {
                AutoRangeUtil.AutoRangeDescriptor zdesc;
                if ( fillDs.property(QDataSet.BUNDLE_1)!=null ) {
                    zdesc= AutoRangeUtil.autoRange((QDataSet) DataSetOps.unbundle( fillDs, fillDs.length(0)-1 ),null, ignoreDsProps);
                    peleCopy.getPlotDefaults().getZaxis().setLog(zdesc.log);
                    peleCopy.getPlotDefaults().getZaxis().setRange(zdesc.range);
                } else {
                    QDataSet plane0= (QDataSet) fillDs.property(QDataSet.PLANE_0);
                    if ( plane0!=null ) {
                        zdesc= AutoRangeUtil.autoRange(plane0,
                            (Map) props.get(QDataSet.PLANE_0), ignoreDsProps);
                        peleCopy.getPlotDefaults().getZaxis().setLog(zdesc.log);
                        peleCopy.getPlotDefaults().getZaxis().setRange(zdesc.range);
                    } else {
                        logger.warning("expected color plane_0");
                    }
                }
                 

            } else {
                peleCopy.getPlotDefaults().getZaxis().setAutoRange(false);
            }

        }
    }

    /**
     * get the units of the datasets without autoranging, which is expensive.
     * This was introduced to fix bug 3104572, where slicing dropped units.
     * @param peleCopy
     * @param fillDs
     * @return true if we had to fix a unit and the plot should be adjusted.
     */
    private boolean doUnitsCheck( QDataSet fillDs ) {
        RenderType spec = plotElement.getRenderType();

        DatumRange xrange= plotElement.getPlotDefaults().getXaxis().getRange();
        DatumRange yrange= plotElement.getPlotDefaults().getYaxis().getRange();
        DatumRange zrange= plotElement.getPlotDefaults().getZaxis().getRange();

        Units xunits;
        Units yunits;
        Units zunits;

        if (spec == RenderType.spectrogram || spec==RenderType.nnSpectrogram ) {

            QDataSet xds = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
            if (xds == null) {
                if ( fillDs.property(QDataSet.JOIN_0)!=null ) {
                    JoinDataSet ds= new JoinDataSet(2);
                    int xtag=0;
                    for ( int i=0; i<fillDs.length(); i++ ) {
                        QDataSet xds1= (QDataSet)fillDs.property(QDataSet.DEPEND_0,i);
                        if ( xds1==null ) {
                            xds1= Ops.linspace( xtag, xtag+fillDs.length(i)-1, fillDs.length(i) );
                            xtag= xtag + fillDs.length(i);
                        }
                        ds.join(xds1);
                    }
                    xds = ds;
                } else {
                    xds = DataSetUtil.indexGenDataSet(fillDs.length());
                }
            }

            QDataSet yds = (QDataSet) fillDs.property(QDataSet.DEPEND_1);
            if (yds == null) {
                if ( fillDs.property(QDataSet.JOIN_0)!=null 
                        && fillDs.length()>0 ) {
                    QDataSet yds1= (QDataSet)fillDs.property(QDataSet.DEPEND_1,0);
                    if ( yds1!=null ) {
                        JoinDataSet ds= new JoinDataSet(yds1.rank()+1);
                        for ( int i=0; i<fillDs.length(); i++ ) {
                            yds1= (QDataSet)fillDs.property(QDataSet.DEPEND_1,i);
                            if ( yds1==null ) {
                                yds1= Ops.linspace( 0, fillDs.length(i,0)-1, fillDs.length(i,0) );
                            }
                            ds.join(yds1);
                        }
                        yds = ds;
                    } else {
                        yds= Ops.indgen(fillDs.slice(0).length());
                    }
                } else if ( fillDs.property(QDataSet.JOIN_0)==null
                        && fillDs.length()>0
                        && fillDs.property(QDataSet.DEPEND_0,0)!=null ) {
                    JoinDataSet ds= new JoinDataSet(2);
                    Units u= null;
                    for ( int i=0; i<fillDs.length(); i++ ) {
                        QDataSet tds= (QDataSet)fillDs.property(QDataSet.DEPEND_0,i);
                        if ( u==null ) {
                            u= (Units)tds.property( QDataSet.UNITS );
                            if ( u==null ) u= Units.dimensionless;
                        } else {
                            Units tu= (Units) tds.property( QDataSet.UNITS );
                            if ( tu==null ) tu= Units.dimensionless;
                            if ( tu!=u ) throw new IllegalArgumentException("Inconvertable units: wanted "+u);
                        }
                        ds.join(tds);
                    }
                    ds.putProperty(QDataSet.UNITS, u);
                    yds = ds;
                } else if ( fillDs.rank()>1 ) {
                    yds = DataSetUtil.indexGenDataSet(fillDs.length(0)); //TODO: QUBE assumed
                } else {
                    yds = DataSetUtil.indexGenDataSet(10); // later the user will get a message "renderer cannot plot..."
                }
            }

            xunits= SemanticOps.getUnits(xds);
            yunits= SemanticOps.getUnits(yds);
            zunits= SemanticOps.getUnits(fillDs);

        } else if ( spec==RenderType.pitchAngleDistribution ) {
            return true;
            
        } else if ( spec==RenderType.polar ) {
            return true;
            
        } else if ( spec==RenderType.eventsBar ) {
            return true;

        } else if ( spec==RenderType.vectorPlot ) {
            return true;

        } else if ( spec==RenderType.orbitPlot ) {
            return true;

        } else if ( spec==RenderType.digital ) {
            return true;

        } else if ( spec==RenderType.internal ) { // we don't know what this is
            return true;

        } else {

            QDataSet depend0;

            if ( SemanticOps.isBundle(fillDs) ) {
                depend0= (QDataSet) fillDs.property(QDataSet.DEPEND_0);
                if ( depend0==null ) {
                    yunits= SemanticOps.getUnits( DataSetOps.unbundle(fillDs, 1 ) );
                    depend0= DataSetOps.unbundle(fillDs,0);
                } else {
                    yunits= SemanticOps.getUnits( DataSetOps.unbundle(fillDs, 0 ) ); //TODO: why just the first?
                }
            } else {
                yunits = SemanticOps.getUnits( fillDs );
                depend0= (QDataSet) fillDs.property(QDataSet.DEPEND_0);
            }

            if ( fillDs.rank()==0 ) return true;

            QDataSet xds= depend0;
            if (xds == null) {
                xds = DataSetUtil.indexGenDataSet(fillDs.length());
            }

            xunits= SemanticOps.getUnits(xds);

            zunits= Units.dimensionless;
            if (spec == RenderType.colorScatter) {
                if ( fillDs.property(QDataSet.BUNDLE_1)!=null ) {
                    zunits= SemanticOps.getUnits( ( QDataSet) DataSetOps.unbundle( fillDs, 2 ) );
                } else {
                    QDataSet plane0= (QDataSet) fillDs.property(QDataSet.PLANE_0);
                    if ( plane0!=null ) {
                        zunits= Units.dimensionless; // NOT SUPPORTED
                    } else {
                        logger.warning("expected color plane_0");
                    }
                }


            }

        }

        boolean change= false;
        if ( xrange.getUnits()==Units.dimensionless && !UnitsUtil.isTimeLocation(xunits) && !UnitsUtil.isOrdinalMeasurement(xunits) && !xunits.isConvertibleTo( xrange.getUnits() ) ) {
            plotElement.getPlotDefaults().getXaxis().setRange( new DatumRange( xrange.min().doubleValue(Units.dimensionless), xrange.max().doubleValue(Units.dimensionless), xunits ) );
            change= true;
        }
        if ( yrange.getUnits()==Units.dimensionless && !UnitsUtil.isTimeLocation(yunits) && !UnitsUtil.isOrdinalMeasurement(yunits) && !yunits.isConvertibleTo( yrange.getUnits() ) ) {
            plotElement.getPlotDefaults().getYaxis().setRange( new DatumRange( yrange.min().doubleValue(Units.dimensionless), yrange.max().doubleValue(Units.dimensionless), yunits ) );
            change= true;
        }
        if ( zrange.getUnits()==Units.dimensionless && !UnitsUtil.isTimeLocation(zunits) && !UnitsUtil.isOrdinalMeasurement(zunits) && !zunits.isConvertibleTo( zrange.getUnits() ) ) {
            plotElement.getPlotDefaults().getZaxis().setRange( new DatumRange( zrange.min().doubleValue(Units.dimensionless), zrange.max().doubleValue(Units.dimensionless), zunits ) );
            change= true;
        }

        return change;

    }


    /**
     * return the plot containing this plotElement's renderer, or null.
     * @return the plot containing this plotElement's renderer, or null.
     */
    public DasPlot getDasPlot() {
        Plot p;
        DomLock lock= this.mutatorLock();
        lock.lock("getDasPlot");
        try {
            p= dom.controller.getPlotFor(plotElement);
        } finally {
            lock.unlock();
        }
        if ( p==null ) {
            return null;
        }
        return p.controller.getDasPlot();
    }

    private DasColorBar getColorbar() {
        Plot p;
        DomLock lock= this.mutatorLock();
        lock.lock("getColorbar");
        try {
            p= dom.controller.getPlotFor(plotElement);
        } finally {
            lock.unlock();
        }
        if ( p==null ) {
            throw new IllegalArgumentException("no plot found for element ("+plotElement+","+plotElement.getPlotId()+")");
        }
        return p.controller.getDasColorBar();
    }

    /**
     * return the data source and filter for this plotElement.
     * @return
     */
    public DataSourceFilter getDataSourceFilter() {
        return dsf;
    }

    /**
     * return the application that the plotElement belongs to.
     * @return
     */
    public Application getApplication() {
        return dom;
    }

    /**
     * copy style from renderType property to style node.
     * @param ele
     */
    private static void setupStyle( PlotElement ele ) {
        PlotElementStyle s= ele.getStyle();
        if ( null!=ele.getRenderType() ) switch (ele.getRenderType()) {
            case colorScatter:
                s.setPlotSymbol( DefaultPlotSymbol.CIRCLES );
                s.setSymbolConnector(PsymConnector.NONE);
                s.setFillToReference(false);
                break;
            case series:
                s.setSymbolConnector(PsymConnector.SOLID);
                int size= 0;
                if ( ele.controller!=null ) { // kludge to turn off plot symbols for large datasets.
                    QDataSet processDataSet= ele.controller.processDataSet;
                    QDataSet dataSet= ele.controller.dataSet;
                    if ( processDataSet!=null ) {
                        size= processDataSet.length();
                    } else if ( dataSet!=null ) {
                        size= dataSet.rank()>0 ? dataSet.length() : 1;
                    }
                }   if ( size>SYMSIZE_DATAPOINT_COUNT ) {
                    s.setPlotSymbol( DefaultPlotSymbol.NONE );
                } else {
                    s.setPlotSymbol( DefaultPlotSymbol.CIRCLES );
                }   s.setFillToReference(false);
                break;
            case scatter:
                s.setSymbolConnector(PsymConnector.NONE);
                s.setPlotSymbol(DefaultPlotSymbol.CIRCLES);
                s.setFillToReference(false);
                break;
            case stairSteps:
                s.setSymbolConnector(PsymConnector.SOLID);
                s.setPlotSymbol(DefaultPlotSymbol.NONE);
                s.setFillToReference(true);
                break;
            case fillToZero:
                s.setSymbolConnector(PsymConnector.SOLID);
                s.setFillToReference(true);
                break;
            case nnSpectrogram:
                SpectrogramRenderer.RebinnerEnum r;
                if ( "true".equals( System.getProperty("useLanlNearestNeighbor","false") ) ) {
                    r= SpectrogramRenderer.RebinnerEnum.lanlNearestNeighbor;
                } else {
                    r= SpectrogramRenderer.RebinnerEnum.nearestNeighbor;
                }   s.setRebinMethod( r );
                break;
            case spectrogram:
                s.setRebinMethod( SpectrogramRenderer.RebinnerEnum.binAverage );
                break;
            default:
                break;
        }
    }

    /**
     * see button added to the slicer.
     * @param ds 
     * @param y reference for dataset, similar to the CONTEXT property.
     * @param above if true, then plot above instead of below.
     */
    private void addPlotBelow( QDataSet ds, Datum y, boolean above) {
        ApplicationController controller= dom.getController();
        DomLock lock= mutatorLock();
        lock.lock("adding slice below");
        try {
            Plot focus= controller.getPlotFor(plotElement);
            Plot p= controller.addPlot( focus, above ? LayoutConstants.ABOVE : LayoutConstants.BELOW );
            PlotElement pe=controller.addPlotElement( p, null );
            DataSourceFilter dsfl= controller.getDataSourceFilterFor( pe );
            dsfl.getController().setDataSetInternal(ds); // setDataSet doesn't autorange, etc.
            p.getYaxis().syncTo(focus.zaxis);
            List<BindingModel> bms= controller.findBindings( dom, Application.PROP_TIMERANGE, focus.getXaxis(), Axis.PROP_RANGE );
            if ( !bms.isEmpty() && UnitsUtil.isTimeLocation( p.getXaxis().getRange().getUnits() ) ) {
                controller.bind( controller.getApplication(), Application.PROP_TIMERANGE, p.getXaxis(), Axis.PROP_RANGE );
            }
            p.setTitle(focus.getTitle() + " @ " + y );
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * reset the autoRebinner property.
     */
    PropertyChangeListener rebinnerListener= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt,"rebinnerListener");            
            if ( !PlotElementController.this.isValueAdjusting() ) {
                plotElement.setAutoRenderType(false); // https://sourceforge.net/p/autoplot/bugs/1217/
            }
        }
    };
    
    
    
    /**
     * create the peer that will actually do the painting.  This may be called from either the event thread or off the event thread,
     * but work will be done on the event thread in either case using SwingUtilities.invokeAndWait.
     *
     * preconditions: the new render type is identified.  The plotElement may already contain a corresponding renderer for this or
     * another type.
     * postconditions: The correct renderer is installed in the plot.
     */
    protected void maybeCreateDasPeer(){
        
        if ( changesSupport.isPendingChanges( PENDING_CREATE_DAS_PEER ) ) {
            logger.warning("someone else is also changing the peer.");
        }
        
        changesSupport.performingChange(this, PENDING_CREATE_DAS_PEER );                    
        
        final Renderer oldRenderer = getRenderer();

        Window p= null;
        if ( !( "true".equals( System.getProperty("java.awt.headless") ) ) ) {
            p= SwingUtilities.getWindowAncestor( plotElement.controller.getDasPlot().getCanvas() );
        }
        final Window parent= p;
                
        //logger.fine( "oldRenderer= "+oldRenderer + "  plotElementController="+ this + " ("+this.hashCode()+")" + " " + Thread.currentThread().getName() );
        DasColorBar cb= null;
        if ( RenderTypeUtil.needsColorbar(plotElement.getRenderType()) ) cb= getColorbar();

        setupStyle( plotElement );
        
        RenderType renderType = plotElement.getRenderType();
        if ( getApplication().getOptions().nearestNeighbor && renderType==RenderType.spectrogram ) {
            renderType= RenderType.nnSpectrogram;
        }
        
        final Renderer newRenderer =
                AutoplotUtil.maybeCreateRenderer( renderType,
                oldRenderer, cb, false );

        if ( newRenderer!=oldRenderer && newRenderer instanceof SpectrogramRenderer ) {
            ((SpectrogramRenderer)newRenderer).setSliceRebinnedData( dom.getOptions().isSliceRebinnedData() );
            newRenderer.addPropertyChangeListener( SpectrogramRenderer.PROP_REBINNER, rebinnerListener );
        }
        
        if ( newRenderer!=oldRenderer && oldRenderer instanceof SpectrogramRenderer ) {
            oldRenderer.addPropertyChangeListener( SpectrogramRenderer.PROP_REBINNER, rebinnerListener );
        }

        if ( cb!=null 
                && !dom.getController().isValueAdjusting()
                && RenderTypeUtil.needsColorbar(plotElement.getRenderType()) ) cb.setVisible( true );

        if (oldRenderer != newRenderer || getDasPlot()!=newRenderer.getParent() ) {
            if ( oldRenderer != newRenderer ) {
                if ( newRenderer instanceof SpectrogramRenderer ) {
                    plotElement.getStyle().setRebinMethod( ((SpectrogramRenderer) newRenderer).getRebinner() );
                    ((SpectrogramRenderer)newRenderer).getRebinner();
                }
                setRenderer(newRenderer);
                //logger.fine( "getRenderer= "+getRenderer() + "  plotElementController="+ this + " ("+this.hashCode()+")" );
                if ( oldRenderer!=null ) {
                    oldRenderer.setActive(false);
                    oldRenderer.setColorBar(null);
                }
                if ( newRenderer.getColorBar()==cb && cb!=null && cb.isVisible() ) {
                    DasCanvas c= getDasPlot().getCanvas();
                    DasCanvasComponent[] dcc=c.getCanvasComponents();
                    boolean hasColorbarInstalled= false;
                    for ( DasCanvasComponent dc: dcc ) {
                        if ( dc==cb ) {
                            hasColorbarInstalled=true;
                        }
                    }
                    if ( !hasColorbarInstalled ) {
                        c.add( cb, cb.getRow(), cb.getColumn() );
                    }                    
                }
            }

            Runnable run = new Runnable() {
                @Override
                public void run() {
                    try {
                        DasPlot plot = getDasPlot();
                        if ( plot==null ) {
                            System.err.println("pec2326: brace yourself for crash, plot is null!");
                            plot = getDasPlot(); // for debugging  Spectrogram->Series
                            //if ( oldRenderer==null && dom.controller.isValueAdjusting() ) { // I think this is an undo, and the plot has already been deleted.

                            //}
                            if ( plot==null ) {
                                throw new IllegalStateException("getDasPlot() result was null.");
                            }
                        }

                        DasPlot oldPlot=null;
                        if (oldRenderer != null) {
                            oldPlot= oldRenderer.getParent();
                            if ( oldPlot!=null && oldPlot!=getDasPlot() ) oldRenderer.getParent().removeRenderer(oldRenderer);
                            if ( oldRenderer!=newRenderer ) plot.removeRenderer(oldRenderer);
                        }
                        if ( oldPlot==null || oldRenderer!=newRenderer ) {
                            synchronized ( dom ) {
                                if ( false && newRenderer instanceof SpectrogramRenderer ) { // https://sourceforge.net/p/autoplot/bugs/2013/
                                    plot.addRenderer(0,newRenderer);
                                    setUpSpectrogramActions(plot);
                                } else {
                                    Renderer[] rends= plot.getRenderers();
                                    int best=-1;
                                    int myPos= -1;
                                    for ( int i=0; i<dom.getPlotElements().length; i++ ) {
                                        if ( dom.getPlotElements(i)==plotElement ) myPos= i;
                                    }

                                    List<Renderer> arends= Arrays.asList(rends);

                                    Renderer lastRend= null;
                                    int i;
                                    for ( i=0; i<myPos; i++ ) {
                                        if ( i>best && i<myPos
                                                && dom.getPlotElements(i).getPlotId().equals(plotElement.getPlotId())
                                                && arends.contains( dom.getPlotElements(i).getController().getRenderer() ) )
                                            lastRend= dom.getPlotElements(i).getController().getRenderer();
                                    }

                                    // find the index of the renderer that is just underneath this one.
                                    int indexUnder= -1;
                                    for ( int j=0; j<rends.length; j++ ) {
                                        if ( rends[j]==lastRend ) indexUnder= j;
                                    }

                                    plot.addRenderer(indexUnder+1,newRenderer);
                                }
                                if ( newRenderer instanceof SpectrogramRenderer ) {
                                    setUpSpectrogramActions(plot);
                                }
                            }

                        }
                        logger.log(Level.FINEST, "plot.addRenderer {0} {1}", new Object[]{plot, newRenderer});
                    } finally {
                        changesSupport.changePerformed( PlotElementController.this, PENDING_CREATE_DAS_PEER );
                    }
                    
                }

                private void setUpSpectrogramActions(DasPlot plot) {
                    MouseModule mm= plot.getDasMouseInputAdapter().getModuleByLabel("Horizontal Slice");
                    final HorizontalSlicerMouseModule hmm= ((HorizontalSlicerMouseModule)mm);
                    if ( hmm!=null ) { // for example in headless mode
                        hmm.getSlicer().addAction(new AbstractAction("Plot Below") {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                org.das2.util.LoggerManager.logGuiEvent(e);
                                final boolean above= ( e.getModifiers() & KeyEvent.SHIFT_MASK ) == KeyEvent.SHIFT_MASK;
                                final QDataSet ds= hmm.getSlicer().getDataSet();
                                final Datum y= hmm.getSlicer().getSliceY();
                                RequestProcessor.invokeLater( () -> {
                                    addPlotBelow(ds,y,above);
                                });
                            }
                        });
                        DataSource dss= new AnonymousDataSource() {
                            @Override
                            public QDataSet getDataSet(ProgressMonitor mon) throws Exception {
                                return hmm.getSlicer().getDataSet();
                            }
                        };
                        hmm.getSlicer().addAction( ExportDataPanel.createExportDataAction( parent, dss ) );
                    }
                    mm= plot.getDasMouseInputAdapter().getModuleByLabel("Vertical Slice");
                    final VerticalSlicerMouseModule vmm= ((VerticalSlicerMouseModule)mm);
                    if ( vmm!=null ) { // for example in headless mode
                        DataSource dss= new AnonymousDataSource() {
                            @Override
                            public QDataSet getDataSet(ProgressMonitor mon) throws Exception {
                                return vmm.getSlicer().getDataSet();
                            }
                        };
                        vmm.getSlicer().addAction( ExportDataPanel.createExportDataAction( parent, dss ) );
                    }
                    mm= plot.getDasMouseInputAdapter().getModuleByLabel("Interval Average");
                    final HorizontalDragRangeSelectorMouseModule vsa= ((HorizontalDragRangeSelectorMouseModule)mm);
                    if ( vsa!=null ) { // for example in headless mode
                        if ( vsa.getDataRangeSelectionListenerCount()>0 ) {
                            DataRangeSelectionListener ddr= vsa.getDataRangeSelectionListener(0);
                            if ( ddr instanceof VerticalSpectrogramAverager ) {
                                DataSource dss= new AnonymousDataSource() {
                                    @Override
                                    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {
                                        return ((VerticalSpectrogramAverager)ddr).getDataSet();
                                    }
                                };
                                ((VerticalSpectrogramAverager)ddr).addAction( ExportDataPanel.createExportDataAction( parent, dss ) );  //TODO
                            }
                        }

                    }
                }
            };
            if ( SwingUtilities.isEventDispatchThread() ) {
                run.run();
            } else {
                try {
                    SwingUtilities.invokeAndWait(run);
                } catch (InterruptedException | InvocationTargetException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }

            //if (getDataSourceFilter().controller.getFillDataSet() != null) {
                // this is danger code, I think inserted to support changing render type.
                // when we change renderType on vector dataset, this is called.
                //setDataSet( getDataSourceFilter().controller.getFillDataSet(), false );
            //}
        } else {
            // no changes needed.
            changesSupport.changePerformed( PlotElementController.this, PENDING_CREATE_DAS_PEER );
            
        }

    }

    /**
     * just reset this plot element's rendertype, ignoring parent.
     * @param renderType 
     */
    private void doResetRenderTypeInt( RenderType renderType ) {
        DomLock lock= this.mutatorLock();
        lock.lock("Reset Render Rype");
        try {
            plotElement.propertyChangeSupport.firePropertyChange( PlotElement.PROP_RENDERTYPE, null, renderType );
        } finally {
            lock.unlock();
        }
        //Renderer oldRenderer= getRenderer();
        maybeCreateDasPeer();
        //if ( getRenderer()!=null && getRenderer()!=oldRenderer ) {
            //QDataSet oldDs= getDataSet(); // TODO: this needs review.  There was a comment about slices, but this works fine.  Old code 
            //if ( oldDs!=null ) {
                //bug1355: This should not be done, I think.
                //getRenderer().setDataSet(oldDs);
            //}
        //}        
    }
    
    /**
     * used to explicitly set the rendering type.  This installs a das2 renderer
     * into the plot to implement the render type.
     *
     * preconditions:
     *   renderer type has been identified.
     * postconditions:
     *   das2 renderer peer is created and bindings made.
     * @param renderType
     */
    public void doResetRenderType(RenderType renderType) {
        PlotElement parentPele= getParentPlotElement();
        if ( parentPele != null ) {
            parentPele.setRenderType(renderType);
            return;
        }

        for ( PlotElement ch: getChildPlotElements() ) {
            Renderer oldRenderer= ch.getController().getRenderer();
            ch.renderType= renderType;  // we don't want to enter doResetRenderType.
            ch.getController().maybeCreateDasPeer();
            if ( oldRenderer!=ch.getController().getRenderer() ) {
                QDataSet oldDs= oldRenderer==null ? null : oldRenderer.getDataSet();
                ch.getController().getRenderer().setDataSet(oldDs);
            }
        }
        doResetRenderTypeInt(renderType);
    }

    public void bindToSeriesRenderer(SeriesRenderer seriesRenderer) {
        ApplicationController ac = this.dom.controller;
        ac.bind(plotElement.style, PlotElementStyle.PROP_LINE_WIDTH, seriesRenderer, "lineWidth");
        ac.bind(plotElement.style, PlotElementStyle.PROP_COLOR, seriesRenderer, Renderer.CONTROL_KEY_COLOR );
        ac.bind(plotElement.style, PlotElementStyle.PROP_SYMBOL_SIZE, seriesRenderer, "symSize");
        ac.bind(plotElement.style, PlotElementStyle.PROP_SYMBOL_CONNECTOR, seriesRenderer, "psymConnector");
        ac.bind(plotElement.style, PlotElementStyle.PROP_PLOT_SYMBOL, seriesRenderer, "psym");
        ac.bind(plotElement.style, PlotElementStyle.PROP_FILLCOLOR, seriesRenderer, Renderer.CONTROL_KEY_FILL_COLOR );
        ac.bind(plotElement.style, PlotElementStyle.PROP_FILL_TO_REFERENCE, seriesRenderer, "fillToReference");
        ac.bind(plotElement.style, PlotElementStyle.PROP_REFERENCE, seriesRenderer, "reference");
        ac.bind(plotElement.style, PlotElementStyle.PROP_FILL_DIRECTION, seriesRenderer, Renderer.CONTROL_KEY_FILL_DIRECTION );
        ac.bind(plotElement.style, PlotElementStyle.PROP_SHOWLIMITS, seriesRenderer, SeriesRenderer.PROP_SHOWLIMITS );
        ac.bind(plotElement.style, PlotElementStyle.PROP_DRAWERROR, seriesRenderer, Renderer.CONTROL_KEY_DRAW_ERROR );
        ac.bind(plotElement.style, PlotElementStyle.PROP_ERRORBARTYPE, seriesRenderer, SeriesRenderer.PROP_ERRORBARTYPE );
        ac.bind(plotElement.style, PlotElementStyle.PROP_ANTIALIASED, seriesRenderer, "antiAliased");
        ac.bind(plotElement, PlotElement.PROP_CADENCECHECK, seriesRenderer, "cadenceCheck");
        if ( seriesRenderer.getColorBar()!=null )
            ac.bind(plotElement.style, PlotElementStyle.PROP_COLORTABLE, seriesRenderer.getColorBar(), "type");
    }

    public void bindToSpectrogramRenderer(SpectrogramRenderer spectrogramRenderer) {
        ApplicationController ac = this.dom.controller;

        ac.bind(plotElement.style, "rebinMethod", spectrogramRenderer, "rebinner");
        ac.bind(plotElement, PlotElement.PROP_CADENCECHECK, spectrogramRenderer, "cadenceCheck");
        
        if ( spectrogramRenderer.getColorBar()!=null ) {
            ac.bind(plotElement.style, "colortable", spectrogramRenderer.getColorBar(), "type");
        }
    }

    public void bindToImageVectorDataSetRenderer(HugeScatterRenderer renderer) {
        ApplicationController ac = this.dom.controller;
        ac.bind(plotElement.style, "color", renderer, "color");
    }

    public void bindToEventsRenderer(EventsRenderer renderer) {
        ApplicationController ac = this.dom.controller;
        ac.bind(plotElement.style, "color", renderer, "color");
    }

    public void bindToDigitalRenderer(DigitalRenderer renderer) {
        ApplicationController ac = this.dom.controller;
        ac.bind(plotElement.style, "color", renderer, "color");
    }

    public void bindToPolarPlotRenderer(PolarPlotRenderer renderer) {
        ApplicationController ac = this.dom.controller;
        ac.bind(plotElement.style, "color", renderer, "color");
        ac.bind(plotElement.style, "lineWidth", renderer, "lineWidth");
    }
    
    public void bindToTickCurveRenderer( TickCurveRenderer renderer) {
        ApplicationController ac = this.dom.controller;
        ac.bind(plotElement.style, "color", renderer, "color");
        ac.bind(plotElement.style, "lineWidth", renderer, "lineWidth");
    }
    
    public void bindToContoursRenderer(Renderer renderer) {
        ApplicationController ac = this.dom.controller;
        ac.bind(plotElement.style, "color", renderer, "color");
        ac.bind(plotElement.style, "lineWidth", renderer, "lineThick");
    }
    
    private void bindToBoundsRenderer(BoundsRenderer renderer) {
        ApplicationController ac = this.dom.controller;
        ac.bind(plotElement.style, "color", renderer, "color");
    }
    
    /**
     * special converter that fills in %{CONTEXT} macro, or inserts it when 
     * label is consistent with macro.  Also now does %{COMPONENT}.  Note
     * this won't do both right now.
     * @return JavaBeans property converter.
     */
    private Converter getLabelConverter() {
        LabelConverter r= new LabelConverter( dom, null, null, plotElement, null );
        return r;
    }

    @Override
    public boolean isPendingChanges() {
        DataSourceFilter ldsf= getDataSourceFilter();
        if ( ldsf!=null ) {
            return ldsf.controller.isPendingChanges() || super.isPendingChanges();
        } else {
            return super.isPendingChanges();
        }
    }

    @Override
    public void pendingChanges(Map<Object, Object> changes) {
        super.pendingChanges(changes);
        DataSourceFilter ldsf= getDataSourceFilter();
        if ( ldsf!=null ) {
            ldsf.controller.pendingChanges(changes);
        } else {
            //System.err.println("here is null");
        }
    }



    private void setStatus(String string) {
        this.dom.controller.setStatus(string);
    }

    @Override
    public String toString() {
        return "" + this.plotElement + " controller";
    }

    private PropertyChangeListener parentComponentLister;

    private void setParentComponentListener(PropertyChangeListener pcl) {
        this.parentComponentLister= pcl;
    }

    private PropertyChangeListener getParentComponentLister() {
        return parentComponentLister;
    }
}
