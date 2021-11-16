/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.dom;

import java.beans.IntrospectionException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.autoplot.util.TickleTimer;
import org.das2.datum.CacheTag;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.graph.DasAxis;
import org.das2.graph.DasPlot;
import org.das2.datum.format.DateTimeDatumFormatter;
import org.das2.util.LoggerManager;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.capability.TimeSeriesBrowse;

/**
 * When the data source supports loading additional data when the time axis (or plot context) changes, then
 * this is responsible for loading additional data.
 * @author jbf
 */
public class TimeSeriesBrowseController {

    private PlotElement p;
    private DasAxis xAxis;
    
    /**
     * set to true after release, to make sure it is not used again.  This can 
     * be reset to false when the TSB is rebound to the context property.
     */
    private boolean released= false; // 
    private DasPlot dasPlot;
    private Plot domPlot;
    private PlotElementController plotElementController;
    private final DataSourceController dataSourceController;
    private DataSourceFilter dsf;
    private ChangesSupport changesSupport;

    private static final String PENDING_AXIS_DIRTY= "tsbAxisDirty";
    private static final String PENDING_TIMERANGE_DIRTY= "tsbTimerangeDirty";
    private static final String PENDING_AXIS_OR_TIMERANGE_DIRTY= "tsbAxisOrTimerangeDirty";

    private static final Logger logger = org.das2.util.LoggerManager.getLogger("autoplot.tsb");
    TickleTimer updateTsbTimer;
    PropertyChangeListener timeSeriesBrowseListener;
    private DomNode listenNode=null;
    private String listenProp=null;

    private static List<Axis> getTimerangeBoundAxes( Application dom ) {
        List<BindingModel> bms= DomUtil.findBindings( dom, dom, Application.PROP_TIMERANGE );
        List<Axis> result= new ArrayList<>();
        for ( BindingModel bm: bms ) {
            if ( bm.getDstProperty().equals( Axis.PROP_RANGE ) ) {
                result.add( (Axis)DomUtil.getElementById( dom, bm.getDstId() ) );
            }
        }
        return result;
    }
    
    private static List<Axis> getOtherBoundAxes( Application dom, Axis axis ) {
        List<BindingModel> bms= DomUtil.findBindings( dom, axis, Axis.PROP_RANGE );
        List<Axis> result= new ArrayList<>();
        for ( BindingModel bm: bms ) {
            if ( bm.getSrcProperty().equals(Application.PROP_TIMERANGE) ) {
                result.addAll( getTimerangeBoundAxes( dom ) );
            } else if ( bm.getSrcId().equals(axis.getId()) ) {
                DomNode n= DomUtil.getElementById( dom, bm.getDstId() );
                if ( n instanceof Axis ) result.add( (Axis)n  );
            } else if ( bm.getDstId().equals(axis.getId()) ) {
                DomNode n= DomUtil.getElementById( dom, bm.getSrcId() );
                if ( n instanceof Axis ) result.add( (Axis)n  );
            }
        }
        return result;
    }
    
    protected TimeSeriesBrowseController( DataSourceController dataSourceController, final PlotElement p ) {
        
        this.changesSupport= new ChangesSupport(this.propertyChangeSupport,this);
        
        updateTsbTimer = new TickleTimer(300, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                LoggerManager.logPropertyChangeEvent(evt,"updateTsbTimer");                
                //TODO: this would work, but the particular axis is not actually adjusting.  We need
                //to figure out who it's attached to, and see if any are adjusting.  Maybe even
                //put in new code in dasPlot.getXAxis.valueIsAdjusting to check bindings.
                
                if ( dsf==null ) {
                    return;  // transitional state
                }
                
                Application dom= dsf.getController().getApplication();
                
                // 
                if ( domPlot!=null ) {
                    List<Axis> otherAxes= getOtherBoundAxes( dom, domPlot.getXaxis() );
                    for ( Axis a: otherAxes ) {
                        if ( a.getController().getDasAxis().valueIsAdjusting() ) {
                            updateTsbTimer.tickle(); 
                            logger.log( Level.FINEST, "{0} is adjusting", a);
                            return;
                        }
                    }
                }

                final PlotElement fpe= p;
                final DataSourceFilter fdsf= p!=null ? p.getController().getDataSourceFilter() : null;
                final TimeSeriesBrowse ftsb= fdsf!=null ?  fdsf.getController().getTsb() : null;

                // TODO: why not just return if fpe is null?
                if ( dom.getController().isValueAdjusting() ) {
                    updateTsbTimer.tickle(); 
                    logger.log( Level.FINEST, "applicationController is adjusting" );
                } else {
                    Map<Object,Object> changes= new HashMap<>();
                    if ( fpe!=null ) {
                        PlotElementController pec= fpe.getController();
                        if ( pec!=null ) {
                            DataSourceFilter dsf= pec.getDataSourceFilter();
                            if ( dsf!=null ) {
                                DataSourceController dsc= dsf.getController();
                                if ( dsc!=null ) {
                                    dsc.pendingChanges(changes);
                                    changes.remove( PENDING_AXIS_OR_TIMERANGE_DIRTY );
                                }
                            }
                        }
                    }
                    if ( fpe!=null && !changes.isEmpty() ) {
                        logger.log( Level.FINEST, "DataSourceFilter is already pending changes, retickle");
                        //changesSupport.performingChange( TimeSeriesBrowseController.this, PENDING_AXIS_OR_TIMERANGE_DIRTY );
                        updateTsbTimer.tickle(); // bug 1253
                    } else {
                        try {                    
                            changesSupport.performingChange( TimeSeriesBrowseController.this, PENDING_AXIS_OR_TIMERANGE_DIRTY );
                            if ( fpe!=null && ( fdsf==null || ftsb == null ) ) {
                            } else {
                                if ( released ) {
                                    logger.fine("leftover update ignored because this is released.");
                                } else {
                                    updateTsb(false);
                                }
                            }
                        //}
                        } finally {
                            changesSupport.changePerformed( TimeSeriesBrowseController.this, PENDING_AXIS_OR_TIMERANGE_DIRTY );                        
                        }
                    }
                }
            }
        });

        this.dsf= dataSourceController.dsf;
        this.dataSourceController= dataSourceController;
        if ( p!=null ) {
            this.p = p;
            this.domPlot= dsf.getController().getApplication().getController().getPlotFor(p);
            this.plotElementController = p.getController();
        } else {
            logger.fine("no plotElement provided, better come back to set up from timerange.");
        }

        if ( p!=null ) {
            this.dasPlot = plotElementController.getDasPlot();
            this.xAxis = plotElementController.getDasPlot().getXAxis();
        }
    }

    private boolean isBoundTimeRange( BindingModel[] bms, String dstId ) {
        for ( int i=0; i<bms.length; i++ ) {
            if ( bms[i].getSrcProperty().equals("timeRange")
                    && bms[i].getDstProperty().equals("range") ) {
                if ( !bms[i].getDstId().equals(dstId) ) return true;
            }
        }
        return false;
    }

    /**
     * have this start listening to the axis.
     * @param plot 
     * @param axis 
     */
    protected void setupAxis( Plot plot, Axis axis ) {
        if ( this.xAxis!=null ) {
            throw new IllegalArgumentException("release old axis before binding to this new axis");
        }
        if ( axis!=plot.getXaxis() ) {
            throw new IllegalArgumentException("axis must be the x-axis for now.");
        }
        this.xAxis= axis.getController().getDasAxis();
        this.dasPlot= plot.getController().getDasPlot();
        this.domPlot= plot;
        setup(true);
    }
            
    /**
     * it's a little shocking that after all these years, there isn't a trivial
     * way to see the time and property that was are listening to.
     * @param node
     * @param property 
     */
    protected void setupGen( DomNode node, final String property ) {
        logger.entering("TimeSeriesBrowseController", "setupGen");
        timeSeriesBrowseListener = new PropertyChangeListener() {
            @Override
            public String toString() {
               return ""+TimeSeriesBrowseController.this;
            }
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                LoggerManager.logPropertyChangeEvent(evt,"timeSeriesBrowseListener");
                //we should have something to listen for locks.
                if (evt.getPropertyName().equals(property)) {
                    changesSupport.registerPendingChange( TimeSeriesBrowseController.this, PENDING_AXIS_OR_TIMERANGE_DIRTY );
                    DatumRange dr=(DatumRange)evt.getNewValue();
                    if ( UnitsUtil.isTimeLocation(dr.getUnits()) ) {
                        setTimeRange( dr );
                    } else {
                        release();
                    }
                    updateTsbTimer.tickle();
                }
            }
        };
        if ( !DomUtil.nodeHasProperty( node, property ) ) {
            throw new IllegalArgumentException("node "+node+" doesn't have property: "+property );
        }

        try {
            Class c = node.getClass();
            PropertyDescriptor pd = new PropertyDescriptor(property, c);
            Method getter = pd.getReadMethod();

            DatumRange defaultTimeRange= dsf.getController().getTsb().getTimeRange();
            DatumRange appTimeRange=  (DatumRange) getter.invoke( node );
            logger.log(Level.FINER, "using app time range: {0}", appTimeRange);
            if ( !appTimeRange.equals( Application.DEFAULT_TIME_RANGE ) && UnitsUtil.isTimeLocation( appTimeRange.getUnits() ) ) { //TODO: clean up.  This really should be based on if there is another guy controlling the timerange.
                setTimeRange( appTimeRange );
                dsf.getController().getTsb().setTimeRange( appTimeRange );
            } else {
                setTimeRange( defaultTimeRange );
                pd.getWriteMethod().invoke( node, defaultTimeRange );
            }
            //dsf.getController().getApplication().getController().bind( node, property, this, PROP_TIMERANGE ); // use node's property value.
            node.addPropertyChangeListener( property, timeSeriesBrowseListener );
        } catch (IntrospectionException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
        listenNode= node;
        listenProp= property;
        released= false;
        logger.exiting("TimeSeriesBrowseController", "setupGen");
    }

    /**
     * initialize the TSB to be listening to a time axis.
     * TODO: this contains inconsistent use of xAxis and this.domPlot.getXaxis().
     * @param valueWasAdjusting true if we are loading a vap or the application is locked.
     */
    protected void setup( boolean valueWasAdjusting ) {
        logger.log(Level.FINE, "setup({0})", valueWasAdjusting);
        
        TimeSeriesBrowse tsb= dataSourceController.getTsb();
        final DatumRange localTimeRange= tsb==null ? null : tsb.getTimeRange();
                
        if ( p!=null && !valueWasAdjusting ) {
            if ( localTimeRange==null ) {
                logger.warning("localTimeRange is null, leaving");
                return;
            }
            this.xAxis.setDatumRange( localTimeRange );
            this.domPlot.getXaxis().setAutoRange(false);
        }
        
        boolean setTsbInitialResolution = true;
        if (setTsbInitialResolution) {
            try {
                changesSupport.performingChange( TimeSeriesBrowseController.this, PENDING_AXIS_DIRTY );
                DatumRange tr = localTimeRange;
                if ( tr==null ) tr= this.domPlot.getXaxis().getRange();
                this.setTimeRange( tr );
                if ( this.domPlot.getXaxis().isAutoRange() && !valueWasAdjusting ) {
                    BindingModel[] bms= dsf.getController().getApplication().getBindings();
                    DatumRange appRange= dsf.getController().getApplication().getTimeRange();
                    if ( appRange.getUnits().isConvertibleTo( tr.getUnits() )
                            && isBoundTimeRange( bms, this.domPlot.getXaxis().getId() ) ) { // check to see if the dom has a compatible timerange.
                        this.dasPlot.getXAxis().resetRange(appRange);
                    } else {
                        this.dasPlot.getXAxis().resetRange(tr);
                    }
                    if ( !this.dasPlot.getXAxis().getLabel().contains("%{RANGE}") ) {
                        this.dasPlot.getXAxis().setUserDatumFormatter(new DateTimeDatumFormatter( dsf.getController().getApplication().getOptions().isDayOfYear() ? DateTimeDatumFormatter.OPT_DOY : 0 )); // See PlotController.createDasPeer and listener that doesn't get event
                    }
                    this.domPlot.getXaxis().setAutoRange(true); // need to turn it back on because resetRange
                    this.dasPlot.getXAxis().setScanRange(null);
                }
                updateTsb(true);
            } catch ( RuntimeException e ) {
                throw e;
            } finally {
                changesSupport.changePerformed( TimeSeriesBrowseController.this, PENDING_AXIS_DIRTY );
            }
        }

        timeSeriesBrowseListener = new PropertyChangeListener() {
            @Override
            public String toString() {
               return ""+TimeSeriesBrowseController.this;
            }
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                LoggerManager.logPropertyChangeEvent(evt,"timeSeriesBrowseListener");                
                if ( dasPlot==null ) {
                    return; // transition
                }
                if ( dasPlot.getXAxis().valueIsAdjusting()) {
                    return;
                } 
                //if ( domPlot.getController().getApplication().getController().isValueAdjusting() ) { // This must be commented out because of 1140.  I'm not sure why this was inserted in the first place.
                //    return;
                //}
                if (evt.getPropertyName().equals("datumRange")) {
                    DatumRange dr=(DatumRange)evt.getNewValue();
                    if ( UnitsUtil.isTimeLocation(dr.getUnits()) ) {
                        changesSupport.registerPendingChange( TimeSeriesBrowseController.this, PENDING_AXIS_OR_TIMERANGE_DIRTY );
                        logger.log(Level.FINE, "setTimeRange({0}) because of datumRange", dr);
                        setTimeRange( dr );
                        updateTsbTimer.tickle();
                    }
                } else if ( evt.getPropertyName().equals( Plot.PROP_CONTEXT ) ) {
                    DatumRange dr=(DatumRange)evt.getNewValue();
                    if ( UnitsUtil.isTimeLocation(dr.getUnits()) ) {
                        changesSupport.registerPendingChange( TimeSeriesBrowseController.this, PENDING_AXIS_OR_TIMERANGE_DIRTY );
                        logger.log(Level.FINE, "setTimeRange({0}) because of context", dr);
                        setTimeRange( dr );
                        updateTsbTimer.tickle();
                    }
                }

            }
        };

        this.dasPlot.getXAxis().addPropertyChangeListener( DasAxis.PROPERTY_DATUMRANGE, timeSeriesBrowseListener);
        this.domPlot.addPropertyChangeListener( Plot.PROP_CONTEXT, timeSeriesBrowseListener ) ;

        released= false;
    }

    /**
     * load the new dataset, etc.
     * @param autorange 
     */
    public void updateTsb(boolean autorange) {

        logger.log(Level.FINE, "updateTsb({0})...", autorange);
        
        DatumRange trange= this.getTimeRange();

        if ( trange!=null ) {

            // CacheTag "tag" identifies what we have already
            QDataSet ds = this.dataSourceController.getDataSet();
            QDataSet dep0 = ds == null ? null : (QDataSet) ds.property(QDataSet.DEPEND_0);
            Object join0 = ds == null ? null : ds.property(QDataSet.JOIN_0);
            CacheTag tag = dep0 == null ? null : (CacheTag) dep0.property(QDataSet.CACHE_TAG);
            if ( tag==null && join0!=null )  {
                if ( join0 instanceof QDataSet ) {
                    QDataSet qdsj= (QDataSet)join0;
                    tag= (CacheTag) qdsj.property( QDataSet.CACHE_TAG );
                } else {
                    logger.log( Level.FINE, "join property was not a QDataSet: {0}", join0.toString());
                }
            }

            DatumRange visibleRange;
            Datum newResolution = null;
            CacheTag testCacheTag; // sloppy one
            if ( xAxis!=null ) {
                visibleRange= xAxis.getDatumRange();
                if ( xAxis.getDLength()>2 ) {
                    newResolution = visibleRange.width().divide(xAxis.getDLength());
                } else {
                    Canvas c= this.dataSourceController.getApplication().getCanvases(0);
                    xAxis.getCanvas().setSize(c.width,c.height);
                    if ( xAxis.getDLength()<=2 ) {
                        logger.warning("xaxis isn't sized, loading data at full resolution!"); //TODO: check into this
                    } else {
                        newResolution = visibleRange.width().divide(xAxis.getDLength());
                    }
                }
                // don't waste time by chasing after 1.0% of a dataset.
                DatumRange newRange = visibleRange;
                testCacheTag = new CacheTag( DatumRangeUtil.rescale(newRange, 0.01, 0.99), newResolution );
                trange= newRange;
                if ( !UnitsUtil.isTimeLocation( visibleRange.getUnits() ) ) {
                   logger.log(Level.FINE, "x-axis for TSB not time location units: {0}", visibleRange);

                   trange= dsf.getController().getApplication().getTimeRange();
                   if ( UnitsUtil.isTimeLocation( trange.getUnits() ) ) {
                        logger.fine("  rebinding application timeRange" );
                        this.release();
                        //Axis xaxis= null;
                        //dom.getController().unbind( dom, Application.PROP_TIMERANGE, xaxis, Axis.PROP_RANGE );
                        //dom.setTimeRange( this.timeSeriesBrowseController.getTimeRange() );//TODO: think about if this is really correct
                        //this.setupGen( dsf.getController().getApplication(), Application.PROP_TIMERANGE );
                        this.setupGen( domPlot, Plot.PROP_CONTEXT );
                        Application dom= domPlot.getController().getApplication();
                        if ( !dom.getController().isPendingChanges() ) {
                            // I'm unable to figure out how to get the code to come through here
                            dom.getController().bind( dom, Application.PROP_TIMERANGE, domPlot, Plot.PROP_CONTEXT );
                        }
                        
                    } else {
                        logger.fine("  unable to bind to application timeRange because of units." );
                        return;
                    }

                }
            } else {
                testCacheTag= new CacheTag(trange,null);
            }

            if ( !UnitsUtil.isTimeLocation( testCacheTag.getRange().getUnits() ) ) {
                testCacheTag= new CacheTag( this.getTimeRange(), null ); // transitional state?  hudson autoplot-test034 was hitting this.
            }

            // 1224: if the xAxis is null, then we need to reload whenever the tag range changes.
            if (tag == null || ( xAxis==null ? !tag.equals(testCacheTag) : !tag.contains(testCacheTag) ) ) {
                TimeSeriesBrowse tsb= dataSourceController.getTsb();
                if ( tsb==null ) {
                    logger.warning("tsbc253: tsb was null");
                    return;
                }
                if ( xAxis!=null ) {
                    //if (dasPlot.isOverSize() && autorange==false ) {
                    //    visibleRange = DatumRangeUtil.rescale(visibleRange, -0.3, 1.3); //TODO: be aware of
                    //}
                    tsb.setTimeRange(trange);
                    tsb.setTimeResolution(newResolution);
                    logger.log( Level.FINE, "updateTsb: {0} (@{1})", new Object[]{trange, newResolution});
                } else {
                    tsb.setTimeRange(trange);
                    tsb.setTimeResolution(null);
                    logger.log( Level.FINE, "updateTsb: {0} (@ intrinsic)", trange);
                }

                String surl;
                surl = tsb.getURI();
                // check the registry for URLs, compare to surl, append prefix if necessary.
                if (!autorange && surl.equals( dataSourceController.getTsbSuri())) {
                    logger.fine("we do no better with tsb");
                    if ( xAxis==null ) {
                        // We really want this to clip off the data that we can't see.  This is a fairly impactive
                        // change and we will need to keep track of the original and the clipped dataset,  Further,
                        // this will also need to consider:
                        //  * not possible because there are no timetags.  We have to use the TimeSeriesBrowse time and all data returned
                        //  * TimeSeriesBrowse does the filtering.  This should already work, if it's URIs are correct.
                        //  * We do the filtering.
                    }
                } else {
                    if ( ! surl.equals( dataSourceController.getTsbSuri()) ) {
                        logger.log( Level.FINER, "update b/c surl!=tsbSuri:\n  {0}\n  {1}", new Object[]{surl, dataSourceController.getTsbSuri()});
                    }
                    dataSourceController.cancel();
                    logger.fine("calling update, which will reload data");
                    if ( this.released ) {
                        System.err.append("here were shouldn't be");
                    }
                    dataSourceController.update(false);
                    dataSourceController.setTsbSuri(surl);
                    if ( domPlot!=null ) {
                        String uriMode= "reset"; // "blur" "none"
                        if ( uriMode.equals("blur") ) { // this branch has issues, in particular inserting the timerange for histories.
                            String newUri= DataSetURI.blurTsbUri( surl );
                            if ( newUri!=null ) {
                                dataSourceController.dsf.uri= newUri;
                                domPlot.controller.dom.controller.setFocusUri(newUri);
                            }
                        } else if ( uriMode.equals("reset") ) {
                            String newUri= DataSetURI.blurTsbResolutionUri( surl );
                            if ( newUri!=null ) {
                                dataSourceController.dsf.uri= newUri;
                                domPlot.controller.dom.controller.setFocusUri(newUri);                        
                            }
                        } else {
                            //none
                        }
                    }
                    //String blurUri= DataSetURI.blurTsbUri( surl );
                    //if ( blurUri!=null ) dataSourceController.dsf.uri= blurUri;
                }
            } else {
                logger.fine("loaded dataset satifies request");
            }
        }

    }

    /**
     * return the dasPlot element that is responsible for handling this data source.  This
     * is the guy that was focused when this was created, and is attached to the dasPlot x-axis
     * that controls this (if there is one).
     * This was introduced because classes were accessing the local variable p.
     *
     * @return a PlotElement or null.
     */
    private PlotElement getPlotElement() {
        return p;
    }

    public boolean isPendingChanges() {
        if (  changesSupport.isPendingChanges() ) {
            return true;
        } else {
            return changesSupport.isPendingChanges();
        }
    }

    public void pendingChanges( Map<Object,Object> c ) {
        c.putAll( changesSupport.changesPending );
    }

    /**
     * the axis we were listening to turned out to not be a time axis (or
     * vise-versa), so we need to release the thing we were listening to.
     */
    protected void release() {
        if (  isListeningToAxis() ) {
            logger.fine("releasing TSB controller");
            this.dasPlot.getXAxis().removePropertyChangeListener(DasAxis.PROPERTY_DATUMRANGE,timeSeriesBrowseListener);
            this.domPlot.removePropertyChangeListener( Plot.PROP_CONTEXT, timeSeriesBrowseListener ) ;
            this.xAxis= null;
            this.released= true;
        } else {
            if ( listenNode!=null ) {
                if ( timeSeriesBrowseListener==null ) {
                    logger.fine("here timeSeriesBrowseListener is null");
                }
                listenNode.removePropertyChangeListener( listenProp, timeSeriesBrowseListener );
            }
        }
        timeSeriesBrowseListener = null;
    }
    
    /**
     * make sure there are no references causing memory leak.  
     * There's still a leak as profiling would show 
     * See https://sourceforge.net/p/autoplot/bugs/1584/
     */
    protected void releaseAll() {
        release();
        this.domPlot= null;
        this.xAxis= null;
        this.dasPlot= null;
        this.dsf= null;
        if ( listenNode!=null ) {
            listenNode.removePropertyChangeListener( listenProp, timeSeriesBrowseListener );
            this.listenNode= null;
        }
        this.plotElementController= null;
        this.dataSourceController.releaseTimeSeriesBrowseController();
        this.released= true; // it might have been listening to context.
        
    }
    
    protected DatumRange timeRange = null;
    public static final String PROP_TIMERANGE = "timeRange";

    private DatumRange getTimeRange() {
        return timeRange;
    }

    private void setTimeRange(DatumRange timeRange) {
        if ( !UnitsUtil.isTimeLocation( timeRange.getUnits() ) ) {
            logger.log(Level.FINE, "ignoring call to setTimeRange with non-time-location {0}", timeRange);
            return;
        }
        DatumRange oldTimeRange = this.timeRange;
        this.timeRange = timeRange;
        propertyChangeSupport.firePropertyChange(PROP_TIMERANGE, oldTimeRange, timeRange);
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

    @Override
    public String toString() {
        return this.dsf + " timeSeriesBrowse controller";
    }

    /**
     * returns true if the TSB is listening to an axis and not the application timeRange property.
     * This is the typical mode.
     * @return
     */
    public boolean isListeningToAxis() {
        return xAxis!=null;
    }

    /**
     * return the id of the plot to which we are listening.
     * @return the id of the plot 
     */
    public String getPlotId() {
        return this.domPlot.getId();
    }
    
    /**
     * provide access to the TSB dasPlot.
     * @return 
     */
    Plot getPlot() {
        return this.domPlot;
    }
}
