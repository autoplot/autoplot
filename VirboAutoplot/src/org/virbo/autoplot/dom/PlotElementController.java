/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import org.das2.components.DasProgressPanel;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.graph.DasColorBar;
import org.das2.graph.DasPlot;
import org.das2.graph.DefaultPlotSymbol;
import org.das2.graph.DigitalRenderer;
import org.das2.graph.EventsRenderer;
import org.das2.graph.ImageVectorDataSetRenderer;
import org.das2.graph.PitchAngleDistributionRenderer;
import org.das2.graph.PsymConnector;
import org.das2.graph.RGBImageRenderer;
import org.das2.graph.Renderer;
import org.das2.graph.SeriesRenderer;
import org.das2.graph.SpectrogramRenderer;
import org.das2.system.RequestProcessor;
import org.das2.util.monitor.ProgressMonitor;
import org.jdesktop.beansbinding.Converter;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.autoplot.RenderType;
import org.virbo.autoplot.AutoplotUtil;
import org.virbo.autoplot.RenderTypeUtil;
import org.virbo.autoplot.dom.ChangesSupport.DomLock;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.JoinDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.dsops.Ops;
import org.virbo.metatree.MetadataUtil;

/**
 * PlotElementController manages the PlotElement, for example resolving the datasource and loading the dataset.
 * Once the data is loaded, the listening PlotElementController receives the update and does the following:
 *   1. resolve the plot type: spectrogram, lineplot, stack of lineplot, etc, using AutoplotUtil.guessRenderType(fillDs);
 *   2. reset the plot element, setting the plot type and creating children if needed.  For example, a B-GSM (demo 5) is
 *      resolved by creating three children, and handing the components off to them.
 *   3. if the component property is not empty, then we implement the component and display that.
 *   4. adjusting the component slice index will not affect ranging when the index is changed.
 * @author jbf
 */
public class PlotElementController extends DomNodeController {

    private static final String PENDING_RESET_RANGE = "resetRanges";
    private static final String PENDING_SET_DATASET= "setDataSet";
    private static final String PENDING_COMPONENT_OP= "componentOp";

    static final Logger logger = Logger.getLogger("vap.plotElementController");
    final private Application dom;
    private PlotElement plotElement;
    private DataSourceFilter dsf; // This is the one we are listening to.
    /**
     * switch over between fine and course points.
     */
    public static final int SYMSIZE_DATAPOINT_COUNT = 500;
    public static final int LARGE_DATASET_COUNT = 30000;

    private QDataSet processDataSet= null;
    String procressStr= null;

    public PlotElementController(final ApplicationModel model, final Application dom, final PlotElement plotElement) {
        super(plotElement);
        this.dom = dom;
        this.plotElement = plotElement;

        plotElement.addPropertyChangeListener(PlotElement.PROP_RENDERTYPE, plotElementListener);
        plotElement.addPropertyChangeListener(PlotElement.PROP_DATASOURCEFILTERID, plotElementListener);
        plotElement.addPropertyChangeListener(PlotElement.PROP_COMPONENT, plotElementListener);
        plotElement.getStyle().addPropertyChangeListener(styleListener);
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
    protected void setChildPlotElements(List<PlotElement> peles) {
        for ( PlotElement p: peles ) {
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
     * remove any bindings and listeners
     */
    void unbindDsf() {
        dsf.removePropertyChangeListener(DataSourceFilter.PROP_FILTERS, dsfListener);
        dsf.controller.removePropertyChangeListener(DataSourceController.PROP_FILLDATASET, fillDataSetListener);
        dsf.controller.removePropertyChangeListener(DataSourceController.PROP_DATASOURCE, dataSourceDataSetListener);
    }
    
    PropertyChangeListener dsfListener = new PropertyChangeListener() {

        @Override
        public String toString() {
            return "" + PlotElementController.this;
        }

        public void propertyChange(PropertyChangeEvent evt) {
            if ( evt.getPropertyName().equals(DataSourceFilter.PROP_FILTERS) ) {
                logger.log(Level.FINE, "property change in DSF means I need to autorange: {0}", evt.getPropertyName());
                setResetRanges(true);
                maybeSetPlotAutorange();
            }
        }
    };
    
    PropertyChangeListener plotElementListener = new PropertyChangeListener() {

        @Override
        public String toString() {
            return "" + PlotElementController.this;
        }

        public void propertyChange(PropertyChangeEvent evt) {
            logger.log(Level.FINE, "plotElementListener: {0} {1}->{2}", new Object[]{evt.getPropertyName(), evt.getOldValue(), evt.getNewValue()});
            if ( evt.getPropertyName().equals(PlotElement.PROP_RENDERTYPE) && !PlotElementController.this.isValueAdjusting() ) {
                if ( dom.getController().isValueAdjusting() ) {
                    //return; // occasional NullPointerException, bug 2988979
                }
                RenderType newRenderType = (RenderType) evt.getNewValue();
                RenderType oldRenderType = (RenderType) evt.getOldValue();
                PlotElement parentEle= getParentPlotElement();
                if (parentEle != null) {
                    parentEle.setRenderType(newRenderType);
                } else {
                    if ( axisDimensionsChange(oldRenderType, newRenderType) ) {
                        resetRanges= true;
                        if ( plotElement.getComponent().equals("") ) {
                            resetPlotElement(getDataSourceFilter().getController().getFillDataSet(), plotElement.getRenderType());
                        } else {
                            QDataSet sliceDs= getDataSet();
                            if ( sliceDs==null ) {
                                // This happens when we load a vap.
                                sliceDs= getDataSourceFilter().getController().getFillDataSet(); // Since this is null, I suspect we can do the same behavior in either case.
                                resetPlotElement( sliceDs, plotElement.getRenderType() );
                            } else {
                                resetPlotElement( sliceDs, plotElement.getRenderType()); // I'm assuming that getDataSet() has been set already, which should be the case.
                            }
                        }
                    } else {
                        doResetRenderType(newRenderType);
                        updateDataSet();
                    }
                    setResetPlotElement(false);
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
                        updateDataSet();
                    }
                }
            } else if ( evt.getPropertyName().equals( PlotElement.PROP_COMPONENT ) ) {
                String newv= (String)evt.getNewValue();
                if ( DataSetOps.changesDimensions( (String)evt.getOldValue(), newv ) ) { //TODO: why two methods see axisDimensionsChange 10 lines above
                    logger.log(Level.FINER, "component property change requires we reset render and dimensions: {0}->{1}", new Object[]{(String) evt.getOldValue(), (String) evt.getNewValue()});
                    setResetPlotElement(true);
                    setResetRanges(true);
                    if ( !dom.getController().isValueAdjusting() ) maybeSetPlotAutorange();
                }
                if ( sliceAutoranges ) {
                    setResetPlotElement(true);
                    setResetRanges(true);
                    if ( !dom.getController().isValueAdjusting() ) maybeSetPlotAutorange();
                }
                if ( newv.startsWith("|") ) dom.getOptions().setDataVisible(true);
                Runnable run= new Runnable() {
                    public void run() {
                        // we reenter this code, so only set lock once.  See test.endtoend.Test015.java
                        // vap+cef:file:///home/jbf/ct/hudson/data.backup/cef/C1_CP_PEA_CP3DXPH_DNFlux__20020811_140000_20020811_150000_V061018.cef?Data__C1_CP_PEA_CP3DXPH_DNFlux
                        List<Object> lock= changesSupport.whoIsChanging(PENDING_COMPONENT_OP); 
                        if ( lock.isEmpty() ) {
                            changesSupport.performingChange(plotElementListener, PENDING_COMPONENT_OP);
                        } else {
                            if ( !lock.contains(plotElementListener) ) throw new IllegalStateException("shouldn't happen");
                        }
                        setStatus("busy: update data set");
                        updateDataSet();
                        setStatus("done update data set");
                        if ( lock.isEmpty() ) changesSupport.changePerformed(PlotElementController.this, PENDING_COMPONENT_OP);
                    }
                };
                if ( isAsyncProcess(newv) ) {
                    RequestProcessor.invokeLater(run);
                } else {
                    run.run();
                }
            }
        }
    };

    /**
     * listen for changes in the parent plotElement that this child can respond to.
     */
    PropertyChangeListener parentStyleListener= new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            try {
                DomUtil.setPropertyValue(plotElement.style, evt.getPropertyName(), evt.getNewValue());
            } catch (IllegalAccessException ex) {
                Logger.getLogger(PlotElementController.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IllegalArgumentException ex) {
                Logger.getLogger(PlotElementController.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InvocationTargetException ex) {
                Logger.getLogger(PlotElementController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    };

    /*
     * listen for changes that might change the renderType.  Try to pick one that is close.  Don't
     * fire changes.
     */
    PropertyChangeListener styleListener= new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            if ( evt.getPropertyName().equals( PlotElementStyle.PROP_REBINMETHOD ) ) {
                if ( plotElement.getRenderType()==RenderType.nnSpectrogram || plotElement.getRenderType()==RenderType.spectrogram ) {
                    if ( evt.getNewValue()==SpectrogramRenderer.RebinnerEnum.nearestNeighbor ) {
                        plotElement.renderType= RenderType.nnSpectrogram;
                    } else if ( evt.getNewValue()==SpectrogramRenderer.RebinnerEnum.binAverage ) {
                        plotElement.renderType= RenderType.spectrogram;
                    }
                }
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
            throw new NullPointerException("couldn't find the data for this plot element");
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
     * apply process to the data.  In general these can be done on the same thread (like
     * slice1), but some are slow (like fftPower).
     * 
     * @param c
     * @param fillDs
     * @return
     * @throws RuntimeException
     */
    private QDataSet processDataSet(String c, QDataSet fillDs) throws RuntimeException {
        String label= null;
        if ( c.length()>0 && !c.startsWith("|") ) {  // grab the component, then apply processes after the pipe.
            if (!plotElement.getComponent().equals("") && fillDs.length() > 0 && fillDs.rank() == 2) {
                String[] labels = SemanticOps.getComponentLabels(fillDs);
                String comp= plotElement.getComponent();
                int ip= comp.indexOf("|");
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
            int idx= c.indexOf("|");
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
                synchronized (this) {
                    if ( c.equals(this.procressStr) && this.processDataSet!=null ) {
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
                synchronized (this) {
                    this.processDataSet= null;
                    this.procressStr= null;
                }
                fillDs = DataSetOps.sprocess(c, fillDs, null);
            }
        } 
        return fillDs;
    }

    /**
     * calculate the interpreted metadata after the slicing.
     * @param c
     * @param properties
     * @return
     */
    Map<String,Object> processProperties( String c, Map<String,Object> properties ) {
        if (c.length() > 5 && c.contains("|")) {
            // slice and collapse specification
            properties = MetadataUtil.sprocess(c, properties );
        }
        return properties;  
    }

    private boolean rendererAcceptsData(QDataSet fillDs) {
        if ( getRenderer() instanceof SpectrogramRenderer ) {
            if ( fillDs.rank()==3 ) {
                QDataSet dep0= (QDataSet) fillDs.property( QDataSet.DEPEND_0 );  // only support das2 tabledataset scheme.
                if ( dep0!=null ) return false;
                return rendererAcceptsData( DataSetOps.slice0(fillDs,0) );
            } else {
                return fillDs.rank()==2;
            } 
        } else if ( getRenderer() instanceof SeriesRenderer) {
            if ( fillDs.rank()==1 ) {
                return true;
            } else if ( fillDs.rank()==2 ) {
                return SemanticOps.isBundle(fillDs);
            } else {
                return false;
            }
        } else if ( getRenderer() instanceof ImageVectorDataSetRenderer ) {
            if ( fillDs.rank()==1 ) {
                return true;
            } else if ( fillDs.rank()==2 ) {
                return SemanticOps.isBundle(fillDs);
            } else {
                return false;
            }
        } else if ( getRenderer() instanceof RGBImageRenderer ) {
            if ( fillDs.rank()==2 ) {
                return !SemanticOps.isBundle(fillDs);
            } else if ( fillDs.rank()==3 ) {
                return fillDs.length(0,0) < 5;
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    /**
     * returns true for commands (executed by sprocess, see the Operations element)
     * that cannot occur in interactive time.  They would block the AWT event thread
     * making the GUI uncontrollable while the process is running.  
     * Right now just the FFT processes are marked and run on a different thread.
     * 
     * @param cmd
     * @return
     */
    private boolean isAsyncProcess( String cmd ) {
        return cmd.contains("fft");
    }

    /**
     * set the dataset that will be plotted.  If the component property is non-null, then
     * additional filtering will be performed.  See http://papco.org/wiki/index.php/DataReductionSpecs
     * @param fillDs
     * @throws IllegalArgumentException
     */
    private void setDataSet(QDataSet fillDs, boolean checkUnits) throws IllegalArgumentException {

        // since we might delete sibling plotElements here, make sure each plotElement is still part of the application
        if (!Arrays.asList(dom.getPlotElements()).contains(plotElement)) {
            return;
        }

        String comp= plotElement.getComponent();
        try {
            if ( fillDs!=null ) {

                if ( comp.length()>0 ) fillDs = processDataSet(comp, fillDs );

                if ( checkUnits && doUnitsCheck( fillDs ) ) { // bug 3104572: slicing would drop units, so old vaps wouldn't work
                    Plot plot= this.dom.getController().getPlotFor(plotElement);
                    PlotController pc= plot.getController();
                    pc.doPlotElementDefaultsUnitsChange(plotElement);
                }
                QDataSet context= (QDataSet) fillDs.property(QDataSet.CONTEXT_0);
                if ( context!=null ) {
                    DatumRange cdr;
                    if ( context.rank()==1 ) {
                        cdr= DataSetUtil.asDatumRange( context, true );
                    } else {
                        cdr= DatumRange.newDatumRange( context.value(), context.value(), SemanticOps.getUnits(context) );
                    }
                    Plot plot= this.dom.getController().getPlotFor(plotElement);
                    DatumRange oldContext= plot.getContext();
                    if ( ( oldContext.equals( Axis.DEFAULT_RANGE ) || UnitsUtil.isTimeLocation( oldContext.getUnits() ) ) && UnitsUtil.isTimeLocation( cdr.getUnits() ) ) {
                        plot.setContext( cdr ); //TODO: this is really backwards, this is intended to be a controller, not a display.
                    }
                } else {
                    // TODO: ???
                }

            }
            _setDataSet(fillDs);
        } catch ( RuntimeException ex ) {
            if (getRenderer() != null) {
                getRenderer().setException(ex);
                getRenderer().setDataSet(null);
                _setDataSet(null);
            } else {
                throw ex;
            }
            return;
        }

        if ( fillDs!=null && getRenderer() != null) {
            if (rendererAcceptsData(fillDs)) {
                getRenderer().setDataSet(fillDs);
//                setStatus("adapting to legacy data model...");
//                org.das2.dataset.DataSet das2ds= DataSetAdapter.createLegacyDataSet(fillDs);
//                setStatus("done, adapting to legacy data model");
//                setStatus("pass dataset to renderer...");
//                getRenderer().setDataSet(das2ds);
//                setStatus("done, pass dataset to renderer");
            } else {
                getRenderer().setDataSet(null);
                getRenderer().setException(new Exception("renderer cannot plot " + fillDs));
            }
        }

    }

    /**
     * the current dataset plotted.  
     */
    public static final String PROP_DATASET = "dataSet";

    protected QDataSet dataSet = null;

    public QDataSet getDataSet() {
        return dataSet;
    }

    public void _setDataSet(QDataSet dataSet) {
        QDataSet oldDataSet = this.dataSet;
        this.dataSet = dataSet; //TODO: we should probably synchronize dataSet access.
        synchronized (this) {
            this.processDataSet= null;
            this.procressStr= null;
        }
        if ( ( plotElement.getLegendLabel().contains("%{") || plotElement.getLegendLabel().contains("$(") ) && renderer!=null ) {
            String s= (String)getLabelConverter().convertForward(plotElement.getLegendLabel());
            renderer.setLegendLabel(s);
        }
        propertyChangeSupport.firePropertyChange(PROP_DATASET, oldDataSet, dataSet);
    }


    PropertyChangeListener fillDataSetListener = new PropertyChangeListener() {

        public synchronized void propertyChange(PropertyChangeEvent evt) {
            if (!Arrays.asList(dom.getPlotElements()).contains(plotElement)) {
                //TODO: find a way to fix this properly or don't call it a kludge! System.err.println("kludge pec446 cannot be removed");
                return;  // TODO: kludge, I was deleted. I think this can be removed now.  The applicationController was preventing GC.
            }
            changesSupport.performingChange( this, PENDING_SET_DATASET );
            try {
                QDataSet fillDs = dsf.controller.getFillDataSet();
                logger.log(Level.FINE, "{0} got new dataset: {1}  resetComponent={2}  resetPele={3}  resetRanges={4}", new Object[]{plotElement, fillDs, resetComponent, resetPlotElement, resetRanges});
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
                updateDataSet();
            } finally {
                changesSupport.changePerformed( this, PENDING_SET_DATASET );
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
     * get the dataset from the dataSourceFilter, and plot it possibly after
     * slicing component.
     * @throws IllegalArgumentException
     */
    private void updateDataSet() throws IllegalArgumentException {
        if ( getRenderer()!=null ) getRenderer().setDataSet(null);
        QDataSet fillDs = dsf.controller.getFillDataSet();
        if (fillDs != null) {
            if (resetPlotElement) {
                if (plotElement.getComponent().equals("")) {
                    RenderType renderType = AutoplotUtil.guessRenderType(fillDs);
                    plotElement.renderType = renderType; // setRenderTypeAutomatically.  We don't want to fire off event here.
                    resetPlotElement(fillDs, renderType);
                    setResetPlotElement(false);
                } else if ( plotElement.getComponent().startsWith("|") ) {
                    try {
                        QDataSet fillDs2 = fillDs;
                        String comp= plotElement.getComponent();
                        if ( comp.length()>0 ) fillDs2= processDataSet( comp, fillDs2 );
                        RenderType renderType = AutoplotUtil.guessRenderType(fillDs2);
                        plotElement.renderType = renderType; // setRenderTypeAutomatically.  We don't want to fire off event here.
                        resetPlotElement(fillDs2, renderType);
                        setResetPlotElement(false);
                    } catch ( RuntimeException ex ) {
                        setStatus("warning: Exception in process: " + ex );
                        //getRenderer().setException(ex);
                        throw ex;
                    }
                } else {
                    if (renderer == null) maybeCreateDasPeer();
                    try {
                        if (resetRanges) doResetRanges();
                        setResetPlotElement(false);
                    } catch ( RuntimeException ex ) {
                        setStatus("warning: Exception in process: " + ex );
                        //getRenderer().setException(ex);
                        throw ex;
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
            setDataSet(null, false);
        } else {
            setDataSet(fillDs, true);
        }
    }

    /**
     * true indicates that the new renderType makes the axis dimensions change.
     * For example, switching from spectrogram to series (to get a stack of components)
     * causes the z axis to become the yaxis.
     * @param oldRenderType
     * @param newRenderType
     */
    private boolean axisDimensionsChange( RenderType oldRenderType, RenderType newRenderType ) {
        if ( oldRenderType==newRenderType ) return false;
        if ( newRenderType==RenderType.pitchAngleDistribution ) return true;
        if ( oldRenderType==RenderType.spectrogram && newRenderType==RenderType.nnSpectrogram ) {
            return false;
        } else if ( oldRenderType==RenderType.nnSpectrogram && newRenderType==RenderType.spectrogram ) {
            return false;
        } else if ( newRenderType==RenderType.spectrogram || newRenderType==RenderType.nnSpectrogram ) {
            return true;
        } else {
            if ( oldRenderType==RenderType.spectrogram || oldRenderType==RenderType.nnSpectrogram ) {
                return true;
            } else {
                return false;
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

        List<Integer> slicePref = new ArrayList( Arrays.asList( 2, 2, 2, 2, 2 ) ); // slicePref big means more likely to slice.
        for (int i = 0; i < depNames.length; i++) {
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
            } else if (n.contains("epoch") ) {
                slicePref.set( i,1 );
            } else if (n.contains("angle")) {
                slicePref.set( i,4 );
            } else if (n.contains("alpha") ) { // commonly used for pitch angle in space physics
                slicePref.set( i,4 );
            } else if (n.contains("bundle")) {
                slicePref.set( i,4 );
            } else if ( u instanceof EnumerationUnits ) {
                slicePref.set( i,5 );
            } else if ( fillDs.property( "BUNDLE_"+i )!=null ) {
                slicePref.set( i,5 );
            }

        }

        List<Integer> qube= new ArrayList();
        int[] a= DataSetUtil.qubeDims(fillDs);
        for ( int i=0; i<a.length; i++ ) {
            qube.add(a[i]);
        }

        String newResult="|slices(";
        boolean transpose= false;
        String result="";
        int nslice= fillDs.rank()-2;

        int idim=0;
        int ndim= fillDs.rank();

        for ( int islice=0; islice<nslice; islice++ ) {
            int sliceIndex = 0;
            int bestSlice = 0;
            boolean noPref= true;
            
            for (int i = 0; i < depNames.length; i++) {
                if ( i>0 && slicePref.get(i)!=slicePref.get(i-1) ) noPref= false;
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

            // pick a slice index near the middle, which is less likely to be all fill.
            int n= qube.get(sliceIndex)/2;

            if ( sliceIndex>0 ) {
                for ( int i=0; i<sliceIndex; i++ ) {
                    newResult+= "':',";
                    idim++;
                }
            }
            newResult+= n;
            idim++;

            if ( idim<(ndim-1) ) newResult+= ",";
            
            result+= "|slice"+sliceIndex+"("+n+")";
            if (lat > -1 && lon > -1 && lat < lon) {
                result+="|transpose()";
                transpose= true;
            }

            slicePref.remove(sliceIndex);
            qube.remove(sliceIndex);

        }

        for ( int i=idim; i<ndim; i++ ) {
            newResult+= "':'";
            if ( i<ndim-1 ) newResult+=",";
        }

        newResult+= ")";
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
        if ( ds.rank()==1 ) {
            return ds.property(QDataSet.BUNDLE_0)!=null;
        } else if ( ds.rank()==2 ) {
            return ds.property(QDataSet.BUNDLE_1)!=null;
        } else if ( ds.rank()==3 ) {
            boolean result= ds.property(QDataSet.BUNDLE_1,0)!=null;
            QDataSet dep1= (QDataSet) ds.property(QDataSet.DEPEND_1,0);
            if ( dep1!=null && ( dep1.property(QDataSet.UNITS) instanceof EnumerationUnits ) ) result=true;
            return result;
        } else {
            return false;
        }
    }

    /**
     * listen for changes in the parent's component property and propagate changes
     * to children.
     * @param plotElement
     * @param ele
     */
    private void addParentComponentListener( PlotElement plotElement, final PlotElement ele ) {
        PropertyChangeListener pcl= new PropertyChangeListener() { // need to listen for component changes for |slice1(x)|unbundle('A')
            public void propertyChange(PropertyChangeEvent evt) {
                if ( evt.getPropertyName().equals(PlotElement.PROP_COMPONENT) ) {
                    if ( DataSetOps.changesDimensions((String)evt.getOldValue(),(String)evt.getNewValue()) ) {
                        return;
                    }
                    Object v= evt.getNewValue();
                    int i= ele.getComponent().indexOf("|unbundle");
                    if ( i==-1 ) {
                        throw new IllegalArgumentException("expected to see unbundle");
                    }
                    String tail = ele.getComponent().substring(i);
                    if ( i!=-1 ) {
                        String sv= (String)v;
                        ele.setComponent( sv+tail );
                    }
                }
            }
        };
        plotElement.addPropertyChangeListener( pcl );

        ele.getController().setParentComponentListener( pcl );
    }

    /**
     * This is the heart of the PlotElementController, and to some degree Autoplot.  In this routine, we are given
     * dataset and a renderType, and we need to reconfigure Autoplot to implement this.  This will add child elements when
     * children are needed, for example when a Vector time series is plotted, we need to add children for each component.
     *
     * preconditions:
     *   the new renderType has been identified.
     *   The dataset to be rendered has been identified.
     * postconditions:
     *   old child plotElements have been deleted.
     *   child plotElements have been added when needed.
     * @param fillDs
     * @param renderType
     */
    private void resetPlotElement(QDataSet fillDs, RenderType renderType) {
        logger.log(Level.FINEST, "resetPlotElement({0} {1}) ele={2}", new Object[]{fillDs, renderType, plotElement});
        if (renderer != null) {
            renderer.setActive(true);
        }

        if (fillDs != null) {

            //boolean lastDimBundle= isLastDimBundle( fillDs );
            //boolean joinOfBundle= fillDs.property(QDataSet.JOIN_0)!=null && lastDimBundle;
            int ndim= Ops.dimensionCount(fillDs);
            boolean shouldSlice= ( fillDs.rank()>2 && ndim>3 && plotElement.isAutoComponent() );
            if ( renderType==RenderType.image && fillDs.rank()==3 ) {
                shouldSlice= false; //TODO: some how render types should indicate they can handle a slice.
            }
                    
            boolean shouldHaveChildren= fillDs.rank() == 2
                    &&  ( renderType != RenderType.spectrogram 
                    && renderType != RenderType.nnSpectrogram
                    && renderType != RenderType.digital
                    && renderType != RenderType.eventsBar
                    && renderType != RenderType.image
                    && renderType != RenderType.pitchAngleDistribution )
                    &&  fillDs.length(0) <= QDataSet.MAX_UNIT_BUNDLE_COUNT;
            //if ( joinOfBundle ) shouldHaveChildren= true;

            if ( fillDs.rank()==2 && SemanticOps.isBundle(fillDs) ) { //TODO: LANL has datasets with both BUNDLE_1 and DEPEND_1 set, so the user can pick.
                QDataSet bdesc= (QDataSet) fillDs.property(QDataSet.BUNDLE_1);
                Object context0= bdesc.property(QDataSet.CONTEXT_0,bdesc.length()-1); // in a bundleDescriptor, this can be a string.
                if ( context0==null ) context0=  bdesc.property(QDataSet.DEPEND_0,bdesc.length()-1); // according to guessRenderType DEPEND_0 should be used.
                if ( context0==null ) context0=  bdesc.property(QDataSet.DEPENDNAME_0,bdesc.length()-1); 
                if ( null!=context0 && context0 instanceof String ) {
                    shouldHaveChildren= false;
                }
            }

            String[] labels = null;
            if ( shouldHaveChildren ) labels= SemanticOps.getComponentLabels(fillDs);

            boolean weShallAddChildren=
                    plotElement.isAutoComponent()
                    && shouldHaveChildren;

            if ( !shouldHaveChildren || weShallAddChildren ) { // delete any old child plotElements
                List<PlotElement> childEles= getChildPlotElements();
                for ( PlotElement p : childEles ) {
                    if ( dom.plotElements.contains(p) ) { 
                        dom.controller.deletePlotElement(p);
                        PropertyChangeListener parentListener= p.getController().getParentComponentLister();
                        if ( parentListener!=null ) {
                            this.plotElement.removePropertyChangeListener( parentListener );
                        }
                        this.removePropertyChangeListener(dsfListener);

                    }
                    plotElement.getStyle().removePropertyChangeListener( p.getController().parentStyleListener );
                }
            }

            if ( !shouldSlice ) doResetRenderType(plotElement.getRenderType());
            setResetPlotElement(false);

            if ( resetRanges && !shouldSlice ) {
                doResetRanges();
                setResetRanges(false);
            }

            if ( shouldHaveChildren ) {
                renderer.setActive(false);
                plotElement.setDisplayLegend(false);
            }

            if ( shouldSlice ) {
                String component= guessSlice( fillDs );
                setSliceAutoranges( sliceShouldAutorange(fillDs, component) );
                String existingComponent= plotElement.getComponent();
                if ( !existingComponent.equals("") ) {
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
                    List<PlotElement> cp = new ArrayList<PlotElement>(fillDs.length(0));
                    int nsubsample= 1 + ( fillDs.length(0)-1 ) / 12; // 1-12 no subsample, 13-24 1 subsample, 25-36 2 subsample, etc.

                    //check for non-unique labels.
                    boolean uniqLabels= true;
                    for ( int i=0;i<labels.length; i++ ) {
                        for ( int j=i+1; j<labels.length; j++ ) {
                            if ( labels[i].equals(labels[j]) ) uniqLabels= false;
                        }
                    }

                    for (int i = 0; i < fillDs.length(0); i++) {
                        PlotElement ele = dom.controller.copyPlotElement(plotElement, domPlot, dsf);
                        ele.controller.getRenderer().setActive(false);
                        cp.add(ele);
                        ele.setParent( plotElement.getId() );
                        plotElement.getStyle().addPropertyChangeListener( ele.controller.parentStyleListener );
                        ele.getStyle().setColor(deriveColor(c, i/nsubsample));
                        ele.getStyle().setFillColor( deriveColor(fc,i/nsubsample).brighter() );
                        String s= plotElement.getComponent();
                        String label1= labels[i];
                        if ( s.equals("") && uniqLabels ) {
                            s= labels[i];
                            QDataSet ds1= DataSetOps.unbundle(fillDs,i);
                            String l1= (String) ds1.property(QDataSet.LABEL);
                            if ( l1!=null ) {
                                label1= l1;
                            }
                        } else {
                            if ( uniqLabels ) {
                                s= s+"|unbundle('"+labels[i]+"')";
                            } else {
                                s= s+"|unbundle('ch_"+i+"')";
                            }
                            addParentComponentListener(plotElement,ele);
                        }
                        ele.setComponentAutomatically(s);
                        ele.setDisplayLegend(true);
                        if ( ele.isAutoLabel() ) ele.setLegendLabelAutomatically(label1);
                        ele.setRenderTypeAutomatically(plotElement.getRenderType()); // this creates the das2 SeriesRenderer.
                        //ele.controller.setDataSet(fillDs, false);
                    }
                    for ( int i=0; i<fillDs.length(0); i++ ) {
                        PlotElement ele= cp.get(i);
                        if ( i % nsubsample == 0 ) {
                            ele.setActive(true); //TODO: test load/save
                            ele.controller.getRenderer().setActive(true);
                        }
                    }
                    renderer.setActive(false);
                    setChildPlotElements(cp);
                } finally {
                    lock.unlock();
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
        public void propertyChange(PropertyChangeEvent evt) {
            if ( dsfReset ) {
                setResetComponent(true);
                setResetPlotElement(true);
                setResetRanges(true);
                plotElement.setAutoLabel(true);
                plotElement.setAutoComponent(true);
                plotElement.setAutoRenderType(true);
                maybeSetPlotAutorange();
            }
        }
    };

    /**
     * we'd like the plot to autorange, so check to see if we are the only
     * plotElement, and if so, set its autorange and autoLabel flags.
     */
    private void maybeSetPlotAutorange() {
        Plot p= dom.controller.getPlotFor(plotElement);
        if ( p==null ) return;
        List<PlotElement> eles= dom.controller.getPlotElementsFor(p);
        if ( DomUtil.oneFamily(eles) ) {
            p.getXaxis().setAutoRange(true);
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
    }
    /**
     * true indicates the controller should autorange next time the fillDataSet is changed.
     */
    public static final String PROP_RESETRANGES = "resetRanges";
    private boolean resetRanges = false;

    public boolean isResetRanges() {
        return resetRanges;
    }

    public void setResetRanges(boolean resetRanges) {
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


    protected Renderer renderer = null;

    public Renderer getRenderer() {
        return renderer;
    }

    private void setRenderer(Renderer renderer) {
        Renderer oldRenderer= this.renderer;
        ApplicationController ac = this.dom.controller;
        if ( oldRenderer!=null ) {
            ac.unbind( plotElement, PlotElement.PROP_LEGENDLABEL, oldRenderer, Renderer.PROP_LEGENDLABEL );
            ac.unbind( plotElement, PlotElement.PROP_DISPLAYLEGEND, oldRenderer, Renderer.PROP_DRAWLEGENDLABEL);
            ac.unbind( plotElement, PlotElement.PROP_ACTIVE, oldRenderer, Renderer.PROP_ACTIVE );
        }
        this.renderer = renderer;
        ac.unbindImpl(node);
        ac.unbindImpl(((PlotElement)node).getStyle());

        if ( node!=plotElement ) {
            System.err.println("node!=plotElement");
        }
        if (renderer instanceof SeriesRenderer) {
            bindToSeriesRenderer((SeriesRenderer) renderer);
        } else if (renderer instanceof SpectrogramRenderer) {
            bindToSpectrogramRenderer((SpectrogramRenderer) renderer);
        } else if (renderer instanceof ImageVectorDataSetRenderer) {
            bindToImageVectorDataSetRenderer((ImageVectorDataSetRenderer) renderer);
        } else if (renderer instanceof EventsRenderer ) {
            bindToEventsRenderer((EventsRenderer)renderer);
        } else if (renderer instanceof DigitalRenderer ) {
            bindToDigitalRenderer((DigitalRenderer)renderer);
        }
        Plot mip= ac.getPlotFor(plotElement);
        if ( mip!=null ) {  // transitional state
            JMenuItem mi= mip.getController().getPlotElementPropsMenuItem();
            if ( mi!=null ) mi.setIcon( renderer.getListIcon() );
        }
        renderer.setId( "rend_"+plotElement.getId());
        ac.bind(plotElement, PlotElement.PROP_LEGENDLABEL, renderer, Renderer.PROP_LEGENDLABEL, getLabelConverter() );
        ac.bind(plotElement, PlotElement.PROP_DISPLAYLEGEND, renderer, Renderer.PROP_DRAWLEGENDLABEL);
        ac.bind(plotElement, PlotElement.PROP_ACTIVE, renderer, Renderer.PROP_ACTIVE );
    }

    /**
     * Do initialization to get the plotElement and attached plot to have reasonable
     * settings.
     * preconditions:
     *   renderType has been identified for the plotElement.
     * postconditions:
     *   plotElement's plotDefaults are set based on metadata and autoranging.
     *   listening plot may invoke its resetZoom method.
     */
    private synchronized void doResetRanges() {
        logger.finest("doResetRanges...");
        setStatus("busy: do autorange");
        changesSupport.performingChange(this, PENDING_RESET_RANGE);

        try {
            Plot plot = dom.controller.getPlotFor(plotElement);

            PlotElement peleCopy = (PlotElement) plotElement.copy();
            peleCopy.setId("");
            peleCopy.setParent("");
            peleCopy.getPlotDefaults().syncTo( plot, Arrays.asList(DomNode.PROP_ID, Plot.PROP_ROWID, Plot.PROP_COLUMNID) );

            DataSourceController dsc= getDataSourceFilter().getController();

            QDataSet fillDs = getDataSourceFilter().controller.getFillDataSet();
            Map props= dsc.getFillProperties();
            String comp= plotElement.getComponent();
            if ( comp.length()>0 ) {
                fillDs = processDataSet( comp, fillDs );
                props= processProperties( comp, props ); //TODO: support components
                if ( props.size()==0 ) { // many of the filters drop the propertyis
                  props= AutoplotUtil.extractProperties(fillDs);
                }
            }

            if ( props==null ) {
                System.err.println("null properties in doResetRanges");
            }

            if (dom.getOptions().isAutolabelling()) { //TODO: this is pre-autoLabel property.

                doMetadata(peleCopy, props, fillDs );

                String reduceRankString = getDataSourceFilter().controller.getReduceDataSetString();
                if (dsf.controller.getReduceDataSetString() != null) { //TODO remove dsf slicing
                    String title = peleCopy.getPlotDefaults().getTitle();
                    title += "!c" + reduceRankString;
                    peleCopy.getPlotDefaults().setTitle(title);
                }
                if ( !plotElement.getComponent().equals("") ) {
                    String title = peleCopy.getPlotDefaults().getTitle();
                    title += "!c%{CONTEXT}";
                    peleCopy.getPlotDefaults().setTitle(title);
                }
            }

            if (dom.getOptions().isAutoranging()) { //this is pre-autorange property, but saves time if we know we won't be autoranging.

                // See https://sourceforge.net/tracker/index.php?func=detail&aid=3405480&group_id=199733&atid=970682

                //DatumRange xdr= peleCopy.getPlotDefaults().getXaxis().getRange();
                //boolean log= peleCopy.getPlotDefaults().getXaxis().isLog();
                //if ( dsf.getController().getTimeSeriesBrowseController()!=null && dsf.getController().getTimeSeriesBrowseController().isListeningToAxis() ) {
                //    // this means we've already autoranged.
                //    peleCopy.getPlotDefaults().getXaxis().setAutoRange(false); // Why do we do this again?  Boy I wish I'd made some tests...
                //}

                logger.fine("doAutoranging");
                doAutoranging( peleCopy,props,fillDs, false );

                //if ( dsf.getController().getTimeSeriesBrowseController()!=null ) {
                //    peleCopy.getPlotDefaults().getXaxis().setAutoRange(true); // kludge again: since we actually set it, turn on the autorange flag again so that it can bind to dom.timerange property
                //}

                TimeSeriesBrowse tsb= getDataSourceFilter().getController().getTsb();
                if ( tsb!=null ) {
                    if ( fillDs!=null ) {
                        QDataSet xds= SemanticOps.xtagsDataSet(fillDs);
                        Units xunits;
                        if ( xds.rank()<=1 ) {
                            xunits= (Units)xds.property(QDataSet.UNITS);
                        } else {
                            //JOIN dataset
                            xunits= (Units)xds.property(QDataSet.UNITS,0);
                        }
                        if ( xunits!=null && UnitsUtil.isTimeLocation( xunits ) ) {
                            peleCopy.getPlotDefaults().getXaxis().setRange( tsb.getTimeRange() );
                        }
                    }
                }

                Renderer newRenderer = getRenderer();
                if (newRenderer instanceof SeriesRenderer && fillDs != null) {
                    QDataSet d = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
                    if (d != null) {
                        ((SeriesRenderer) newRenderer).setCadenceCheck((d.property(QDataSet.CADENCE) != null));
                    } else {
                        ((SeriesRenderer) newRenderer).setCadenceCheck(true);
                    }
                }

            } else {
                setStatus( "autoranging is disabled" );
            }

            if ( plotElement.getComponent().equals("") && plotElement.isAutoLabel() ) plotElement.setLegendLabelAutomatically( peleCopy.getLegendLabel() );

            peleCopy.getPlotDefaults().getXaxis().setAutoRange(true); // this is how we distinguish it from the original, useless plot defaults.
            peleCopy.getPlotDefaults().getYaxis().setAutoRange(true);
            peleCopy.getPlotDefaults().getZaxis().setAutoRange(true);

            if ( logger.isLoggable(Level.FINEST) ) {
                logger.finest( String.format( "done, autorange  x:%s, y:%s ",
                        peleCopy.getPlotDefaults().getXaxis().getRange().toString(),
                        peleCopy.getPlotDefaults().getYaxis().getRange().toString() ) );
            }

            plotElement.setPlotDefaults( peleCopy.getPlotDefaults() );  // bug 2992903 runs through here
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
     * preconditions:
     *    fillData is set.
     *    fillProperties is set.
     * postconditions:
     *    metadata is inspected to get axis labels, fill values, etc.
     *    renderType is determined and set.
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

        if ( spec == RenderType.spectrogram || spec==RenderType.nnSpectrogram ) {
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
        } else { // hugeScatter okay

            Map<String,Object> yprop=null, xprop=null, prop=null;

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

    public static void doAutoranging( PlotElement peleCopy, Map<String,Object> props, QDataSet fillDs ) {
        doAutoranging( peleCopy, props, fillDs, false );
    }

    /**
     * this is the old updateFillSeries and updateFillSpectrogram code.  This calculates
     * ranges and preferred symbol settings, and puts the values in peleCopy.plotDefaults.
     * The dom Plot containing this plotElement should be listening for changes in plotElement.plotDefaults,
     * and can then decide if it wants to use the autorange settings.
     *
     * This also sets the style node of the plotElement copy, so its values should be sync'ed as well.
     * 
     * @param peleCopy
     * @param props
     * @param spec
     */
    public static void doAutoranging( PlotElement peleCopy, Map<String,Object> props, QDataSet fillDs, boolean ignoreDsProps ) {

        RenderType spec = peleCopy.getRenderType();

        if ( fillDs.rank()==0 ) {
            //System.err.println("rank 0");
            spec= RenderType.digital;
        }

        if (props == null) {
            props = Collections.EMPTY_MAP;
        }


        if ( spec == RenderType.spectrogram || spec==RenderType.nnSpectrogram ) {

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
            Map<String,Object> yprops= (Map) props.get(QDataSet.DEPEND_1);
            if (yds == null) {
                if ( fillDs.property(QDataSet.JOIN_0)!=null ) {
                    JoinDataSet ds= new JoinDataSet(2);
                    for ( int i=0; i<fillDs.length(); i++ ) {
                        QDataSet yds1= (QDataSet)fillDs.property(QDataSet.DEPEND_1,i);
                        if ( yds1==null ) {
                            yds1= Ops.linspace( 0, fillDs.length(i,0)-1, fillDs.length(i,0) );
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
            if ( yds.length()==fillDs.length(0) && yds.length()>3 ) { // Dataset might have bundle, we need to ignore at the right time.  If fillDs.length(0)==3 avoid a bug.
                zds= fillDs;
            } else {
                zds= SemanticOps.getDependentDataSet(fillDs);
            }

            Units xunits= SemanticOps.getUnits(xds); 
            Units yunits= SemanticOps.getUnits(yds);
            Units zunits= SemanticOps.getUnits(zds);

            if ( UnitsUtil.isOrdinalMeasurement( xunits ) || UnitsUtil.isOrdinalMeasurement(yunits) || UnitsUtil.isOrdinalMeasurement(zunits) ) {
                return;
            }

            AutoplotUtil.AutoRangeDescriptor xdesc = AutoplotUtil.autoRange(xds, (Map) props.get(QDataSet.DEPEND_0), ignoreDsProps);

            AutoplotUtil.AutoRangeDescriptor ydesc = AutoplotUtil.autoRange(yds, yprops, ignoreDsProps );

            //QDataSet hist= getDataSourceFilter().controller.getHistogram();
            AutoplotUtil.AutoRangeDescriptor desc;
            //if ( false && hist!=null ) {
            //    desc= AutoplotUtil.autoRange( hist, fillDs, props );
            //} else {
                desc = AutoplotUtil.autoRange( zds, props, ignoreDsProps );
            //}

            peleCopy.getPlotDefaults().getZaxis().setRange(desc.range);
            peleCopy.getPlotDefaults().getZaxis().setLog(desc.log);

            if ( peleCopy.getPlotDefaults().getXaxis().isAutoRange() ) {
                peleCopy.getPlotDefaults().getXaxis().setLog(xdesc.log);
                peleCopy.getPlotDefaults().getXaxis().setRange(xdesc.range);
            }
            peleCopy.getPlotDefaults().getYaxis().setLog(ydesc.log);
            peleCopy.getPlotDefaults().getYaxis().setRange(ydesc.range);
            
        } else if ( spec==RenderType.pitchAngleDistribution ) {
            QDataSet qube= PitchAngleDistributionRenderer.doAutorange( fillDs );
            if ( qube==null ) {
                // nothing
            } else {
                peleCopy.getPlotDefaults().getXaxis().setRange( DataSetUtil.asDatumRange( qube.slice(0),true ) );
                peleCopy.getPlotDefaults().getYaxis().setRange( DataSetUtil.asDatumRange( qube.slice(1),true ) );
                peleCopy.getPlotDefaults().getZaxis().setRange( DataSetUtil.asDatumRange( qube.slice(2),true ) );
            }

        } else if ( spec==RenderType.digital ) {
            QDataSet qube= DigitalRenderer.doAutorange( fillDs );
            if ( qube==null ) {
                // nothing
            } else {
                peleCopy.getPlotDefaults().getXaxis().setRange( DataSetUtil.asDatumRange( qube.slice(0),true ) );
                peleCopy.getPlotDefaults().getYaxis().setRange( DataSetUtil.asDatumRange( qube.slice(1),true ) );
            }
        } else if ( spec==RenderType.eventsBar ) {
            QDataSet qube= EventsRenderer.doAutorange( fillDs );
            if ( qube==null ) {
                // nothing
            } else {
                peleCopy.getPlotDefaults().getXaxis().setRange( DataSetUtil.asDatumRange( qube.slice(0),true ) );
                peleCopy.getPlotDefaults().getYaxis().setRange( DataSetUtil.asDatumRange( qube.slice(1),true ) );
            }
        } else if ( spec==RenderType.image ) {
            QDataSet qube= RGBImageRenderer.doAutorange( fillDs );
            if ( qube==null ) {
                // nothing
            } else {
                peleCopy.getPlotDefaults().getXaxis().setRange( DataSetUtil.asDatumRange( qube.slice(0),true ) );
                peleCopy.getPlotDefaults().getYaxis().setRange( DataSetUtil.asDatumRange( qube.slice(1),true ) );
            }       
        } else {

            QDataSet hist= null; //getDataSourceFilter().controller.getHistogram();
            AutoplotUtil.AutoRangeDescriptor ydesc; //TODO: QDataSet can model AutoRangeDescriptors, it should be used instead.
            
            QDataSet depend0;

            if ( false && hist!=null ) {
                ydesc= AutoplotUtil.autoRange( hist, fillDs, props );
                depend0 = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
            } else {
                if ( SemanticOps.isBundle(fillDs) ) {
                    depend0= SemanticOps.xtagsDataSet(fillDs);
                    if ( spec==RenderType.colorScatter ) {
                        ydesc= AutoplotUtil.autoRange( DataSetOps.unbundle(fillDs, 1 ), props, ignoreDsProps );
                    } else {
                        ydesc= AutoplotUtil.autoRange( DataSetOps.unbundle(fillDs, fillDs.length(0)-1 ), props, ignoreDsProps ); //TODO: small problem that we don't support colorScatter here
                        for ( int i=fillDs.length(0)-2; i>=0; i-- ) {
                           AutoplotUtil.AutoRangeDescriptor ydesc1= AutoplotUtil.autoRange( DataSetOps.unbundle(fillDs,i ), props, ignoreDsProps );
                           if ( ydesc1.range.getUnits().isConvertableTo(ydesc.range.getUnits()) ) {
                               ydesc.range= DatumRangeUtil.union( ydesc.range, ydesc1.range );
                           } else {
                               break;
                           }
                        }
                    }
                } else {
                    ydesc = AutoplotUtil.autoRange( fillDs, props, ignoreDsProps );
                    depend0 = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
                }
            }

            peleCopy.getPlotDefaults().getYaxis().setLog(ydesc.log);
            peleCopy.getPlotDefaults().getYaxis().setRange(ydesc.range);

            QDataSet xds= depend0;
            if (xds == null) {
                xds = DataSetUtil.indexGenDataSet(fillDs.length());
            }

            if ( peleCopy.getPlotDefaults().getXaxis().isAutoRange() ) {
                AutoplotUtil.AutoRangeDescriptor xdesc = AutoplotUtil.autoRange(xds, (Map) props.get(QDataSet.DEPEND_0), ignoreDsProps);

                peleCopy.getPlotDefaults().getXaxis().setLog(xdesc.log);
                if ( UnitsUtil.isOrdinalMeasurement( xdesc.range.getUnits() ) ) {
                    xdesc.range= DatumRangeUtil.newDimensionless( xdesc.range.min().doubleValue(xdesc.range.getUnits() ), xdesc.range.max().doubleValue(xdesc.range.getUnits()) );
                }
                peleCopy.getPlotDefaults().getXaxis().setRange(xdesc.range);
            }

            if (spec == RenderType.colorScatter) {
                AutoplotUtil.AutoRangeDescriptor zdesc;
                if ( fillDs.property(QDataSet.BUNDLE_1)!=null ) {
                    zdesc= AutoplotUtil.autoRange((QDataSet) DataSetOps.unbundle( fillDs, fillDs.length(0)-1 ),null, ignoreDsProps);
                } else {
                    QDataSet plane0= (QDataSet) fillDs.property(QDataSet.PLANE_0);
                    if ( plane0!=null ) {
                        zdesc= AutoplotUtil.autoRange(plane0,
                            (Map) props.get(QDataSet.PLANE_0), ignoreDsProps);
                        peleCopy.getPlotDefaults().getZaxis().setLog(zdesc.log);
                        peleCopy.getPlotDefaults().getZaxis().setRange(zdesc.range);
                        peleCopy.getPlotDefaults().getZaxis().setRange(zdesc.range);
                    } else {
                        Logger.getLogger("autoplot.plotelementcontroller").warning("expected color plane_0");
                    }
                }
                 

            }

            if (fillDs.length() > LARGE_DATASET_COUNT) {
                peleCopy.getStyle().setSymbolConnector(PsymConnector.NONE);
                peleCopy.getStyle().setPlotSymbol(DefaultPlotSymbol.CIRCLES);
                peleCopy.getStyle().setSymbolSize(1.0);
            } else {
                peleCopy.getStyle().setPlotSymbol(DefaultPlotSymbol.CIRCLES);
                if (fillDs.length() > SYMSIZE_DATAPOINT_COUNT) {
                    peleCopy.getStyle().setSymbolSize(1.0);
                } else {
                    peleCopy.getStyle().setSymbolSize(3.0);
                }

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

        Map<String,Object> props= new HashMap();

        if (props == null) {
            props = Collections.EMPTY_MAP;
        }

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
            Map<String,Object> yprops= (Map) props.get(QDataSet.DEPEND_1);
            if (yds == null) {
                if ( fillDs.property(QDataSet.JOIN_0)!=null ) {
                    JoinDataSet ds= new JoinDataSet(2);
                    for ( int i=0; i<fillDs.length(); i++ ) {
                        QDataSet yds1= (QDataSet)fillDs.property(QDataSet.DEPEND_1,i);
                        if ( yds1==null ) {
                            yds1= Ops.linspace( 0, fillDs.length(i,0)-1, fillDs.length(i,0) );
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
                    yprops= null;
                }
            }

            xunits= SemanticOps.getUnits(xds);
            yunits= SemanticOps.getUnits(yds);
            zunits= SemanticOps.getUnits(fillDs);

        } else if ( spec==RenderType.pitchAngleDistribution ) {
            return true;

        } else if ( spec==RenderType.eventsBar ) {
            return true;

        } else if ( spec==RenderType.digital ) {
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
                        Logger.getLogger("autoplot.plotelementcontroller").warning("expected color plane_0");
                    }
                }


            }

        }

        boolean change= false;
        if ( xrange.getUnits()==Units.dimensionless && !UnitsUtil.isTimeLocation(xunits) && !UnitsUtil.isOrdinalMeasurement(xunits) && !xunits.isConvertableTo( xrange.getUnits() ) ) {
            plotElement.getPlotDefaults().getXaxis().setRange( new DatumRange( xrange.min().doubleValue(Units.dimensionless), xrange.max().doubleValue(Units.dimensionless), xunits ) );
            change= true;
        }
        if ( yrange.getUnits()==Units.dimensionless && !UnitsUtil.isTimeLocation(yunits) && !UnitsUtil.isOrdinalMeasurement(yunits) && !yunits.isConvertableTo( yrange.getUnits() ) ) {
            plotElement.getPlotDefaults().getYaxis().setRange( new DatumRange( yrange.min().doubleValue(Units.dimensionless), yrange.max().doubleValue(Units.dimensionless), yunits ) );
            change= true;
        }
        if ( zrange.getUnits()==Units.dimensionless && !UnitsUtil.isTimeLocation(zunits) && !UnitsUtil.isOrdinalMeasurement(zunits) && !zunits.isConvertableTo( zrange.getUnits() ) ) {
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
        Plot p= dom.controller.getPlotFor(plotElement);
        if ( p==null ) return null;
        return p.controller.getDasPlot();
    }

    private DasColorBar getColorbar() {
        Plot p= dom.controller.getPlotFor(plotElement);
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
        if ( ele.getRenderType()==RenderType.colorScatter ) {
            s.setPlotSymbol( DefaultPlotSymbol.CIRCLES );
            s.setSymbolConnector(PsymConnector.NONE);
            s.setFillToReference(false);
        } else if ( ele.getRenderType()==RenderType.series ) {
            s.setSymbolConnector(PsymConnector.SOLID);
            s.setPlotSymbol(DefaultPlotSymbol.CIRCLES);
            s.setFillToReference(false);
        } else if ( ele.getRenderType()==RenderType.scatter ) {
            s.setSymbolConnector(PsymConnector.NONE);
            s.setPlotSymbol(DefaultPlotSymbol.CIRCLES);
            s.setFillToReference(false);
        } else if ( ele.getRenderType()==RenderType.stairSteps ) {
            s.setSymbolConnector(PsymConnector.SOLID);
            s.setFillToReference(true);
        } else if ( ele.getRenderType()==RenderType.fillToZero ) {
            s.setSymbolConnector(PsymConnector.SOLID);
            s.setFillToReference(true);
        } else if ( ele.getRenderType()==RenderType.nnSpectrogram ) {//TODO: check nnSpectrogram preference.
            s.setRebinMethod( SpectrogramRenderer.RebinnerEnum.nearestNeighbor );
        } else if ( ele.getRenderType()==RenderType.spectrogram ) {
            s.setRebinMethod( SpectrogramRenderer.RebinnerEnum.binAverage );
        }
    }

    protected void maybeCreateDasPeer(){
        final Renderer oldRenderer = getRenderer();
        DasColorBar cb= null;
        if ( RenderTypeUtil.needsColorbar(plotElement.getRenderType()) ) cb= getColorbar();

        setupStyle( plotElement );

        final Renderer newRenderer =
                AutoplotUtil.maybeCreateRenderer( plotElement.getRenderType(),
                oldRenderer, cb, false );

        if ( cb!=null 
                && !dom.getController().isValueAdjusting()
                && RenderTypeUtil.needsColorbar(plotElement.getRenderType()) ) cb.setVisible( true );

        if (oldRenderer != newRenderer || getDasPlot()!=newRenderer.getParent() ) {
            if ( oldRenderer != newRenderer ) {
                setRenderer(newRenderer);
            }

            Runnable run = new Runnable() {
                public void run() {
                    DasPlot plot = getDasPlot();
                    if ( plot==null ) {
                        System.err.println("brace yourself for crash...");
                        plot = getDasPlot(); // for debugging  Spectrogram->Series
                        if ( oldRenderer==null && dom.controller.isValueAdjusting() ) { // I think this is an undo, and the plot has already been deleted.

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
                            if ( newRenderer instanceof SpectrogramRenderer ) {
                                plot.addRenderer(0,newRenderer);
                            } else {
                                if ( plot==null ) {
                                    throw new IllegalStateException("plot cannot be null");
                                }
                                Renderer[] rends= plot.getRenderers();
                                PlotElement[] pe= new PlotElement[rends.length];
                                for ( int i=0; i<rends.length; i++ ) {
                                    PlotElement pe1= dom.controller.findPlotElement(rends[i]);
                                    pe[i]= pe1;
                                }
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
                                            && arends.contains( dom.getPlotElements(i).getController().getRenderer() ) ) lastRend= dom.getPlotElements(i).getController().getRenderer();
                                }

                                // find the index of the renderer that is just underneath this one.
                                int indexUnder= -1;
                                for ( int j=0; j<rends.length; j++ ) {
                                    if ( rends[j]==lastRend ) indexUnder= j;
                                }

                                plot.addRenderer(indexUnder+1,newRenderer);
                            }
                        }

                    }
                    logger.log(Level.FINEST, "plot.addRenderer {0} {1}", new Object[]{plot, newRenderer});

                }
            };
            if ( SwingUtilities.isEventDispatchThread() ) {
                run.run();
            } else {
                SwingUtilities.invokeLater(run);
            }

            //if (getDataSourceFilter().controller.getFillDataSet() != null) {
                // this is danger code, I think inserted to support changing render type.
                // when we change renderType on vector dataset, this is called.
                //setDataSet( getDataSourceFilter().controller.getFillDataSet(), false );
            //}
        }

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
        DomLock lock= this.mutatorLock();
        lock.lock("Reset Render Rype");
        try {
            plotElement.propertyChangeSupport.firePropertyChange( PlotElement.PROP_RENDERTYPE, null, renderType );
        } finally {
            lock.unlock();
        }
        Renderer oldRenderer= getRenderer();
        maybeCreateDasPeer();
        if ( getRenderer()!=null && getRenderer()!=oldRenderer ) {
            QDataSet oldDs= oldRenderer==null ? null : oldRenderer.getDataSet();
            //QDataSet oldDs= getDataSet();  // TODO: what about sprocess?
            if ( oldDs!=null ) {
                getRenderer().setDataSet(oldDs);
            }
        }
    }

    public synchronized void bindToSeriesRenderer(SeriesRenderer seriesRenderer) {
        ApplicationController ac = this.dom.controller;
        ac.bind(plotElement.style, "lineWidth", seriesRenderer, "lineWidth");
        ac.bind(plotElement.style, "color", seriesRenderer, "color");
        ac.bind(plotElement.style, "symbolSize", seriesRenderer, "symSize");
        ac.bind(plotElement.style, "symbolConnector", seriesRenderer, "psymConnector");
        ac.bind(plotElement.style, "plotSymbol", seriesRenderer, "psym");
        ac.bind(plotElement.style, "fillColor", seriesRenderer, "fillColor");
        ac.bind(plotElement.style, PlotElementStyle.PROP_FILL_TO_REFERENCE, seriesRenderer, "fillToReference");
        ac.bind(plotElement.style, "reference", seriesRenderer, "reference");
        ac.bind(plotElement.style, "antiAliased", seriesRenderer, "antiAliased");
        ac.bind(plotElement, PlotElement.PROP_CADENCECHECK, seriesRenderer, "cadenceCheck");
    }

    public void bindToSpectrogramRenderer(SpectrogramRenderer spectrogramRenderer) {
        ApplicationController ac = this.dom.controller;

        ac.bind(plotElement.style, "rebinMethod", spectrogramRenderer, "rebinner");
        if ( spectrogramRenderer.getColorBar()!=null )
            ac.bind(plotElement.style, "colortable", spectrogramRenderer.getColorBar(), "type");

    }

    public void bindToImageVectorDataSetRenderer(ImageVectorDataSetRenderer renderer) {
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
    
    /**
     * special converter that fills in %{CONTEXT} macro, or inserts it when 
     * label is consistent with macro.  Also now does %{COMPONENT}.  Note
     * this won't do both right now.
     * @return
     */
    private Converter getLabelConverter() {
        return new Converter() {
            @Override
            public Object convertForward(Object value) {
                String title= (String)value;
                if ( title.contains("CONTEXT" ) ) {
                    String contextStr="";
                    if ( plotElement!=null ) {
                        if ( dataSet!=null ) {
                            contextStr= DataSetUtil.contextAsString(dataSet);
                            title= insertString( title, "CONTEXT", contextStr );
                        }
                    }
                }
                if ( title.contains("USER_PROPERTIES" ) ) {
                    if ( plotElement!=null ) {
                        if ( dataSet!=null ) {
                            Map<String,Object> props= (Map<String, Object>) dataSet.property(QDataSet.USER_PROPERTIES);
                            title= DomUtil.resolveProperties( title, "USER_PROPERTIES", props );
                        }
                    }
                }
                if ( title.contains("METADATA" ) ) {
                    if ( plotElement!=null ) {
                        if ( dataSet!=null ) {
                            DataSourceFilter dsf= (DataSourceFilter) DomUtil.getElementById( dom, plotElement.getDataSourceFilterId() );
                            if ( dsf!=null ) { // ought not to be!
                                Map<String,Object> props= (Map<String, Object>) dsf.getController().getRawProperties(); //TODO: this is a really old name that needs updating...
                                title= DomUtil.resolveProperties( title, "METADATA", props );
                            }
                        }
                    }
                }
                if ( title.contains("TIMERANGE") ) {
                    DatumRange tr= PlotElementControllerUtil.getTimeRange( dom, plotElement );
                    if ( tr==null ) {
                        title= insertString( title, "TIMERANGE", "(no timerange)" );
                    } else {
                        title= insertString( title, "TIMERANGE",tr.toString() );
                    }
                }
                //System.err.println("<--"+value + "-->"+title );
                //see convertReverse, which must be done as well.
                if ( title.contains("COMPONENT") ) {
                    String ss="";
                    if ( plotElement!=null ) {
                        ss= plotElement.getComponent();
                    }
                    title= insertString( title, "COMPONENT", ss );
                }
                return title;
            }

            @Override
            public Object convertReverse(Object value) {
                String title= (String)value;
                String ptitle=  plotElement.getLegendLabel();
                if ( containsString( ptitle, "CONTEXT", title) ) {
                    title= ptitle;
                } else if ( ptitle.contains( "%{USER_PROPERTIES" ) ) { //kludgy
                    title= ptitle;
                } else if ( ptitle.contains( "%{METADATA" ) ) { //kludgy
                    title= ptitle;
                } else if ( containsString( ptitle, "TIMERANGE", title ) ) {
                    title= ptitle;
                } else if ( containsString( ptitle, "COMPONENT", title ) ) {
                    title= ptitle;
                }
                return title;
            }
        };
    }

    @Override
    public boolean isPendingChanges() {
        return getDataSourceFilter().controller.isPendingChanges() || super.isPendingChanges();
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
