/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.beans.IntrospectionException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.virbo.autoplot.util.TickleTimer;
import org.das2.datum.CacheTag;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.UnitsUtil;
import org.das2.graph.DasAxis;
import org.das2.graph.DasPlot;
import org.das2.datum.format.DateTimeDatumFormatter;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.capability.TimeSeriesBrowse;

/**
 * When the data source supports loading additional data when the time axis (or plot context) changes, then
 * this is responsible for loading additional data.
 * @author jbf
 */
public class TimeSeriesBrowseController {

    private PlotElement p;
    private DasAxis xAxis;
    private DasPlot plot;
    private Plot domPlot;
    private PlotElementController plotElementController;
    private DataSourceController dataSourceController;
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
        List<Axis> result= new ArrayList<Axis>();
        for ( BindingModel bm: bms ) {
            if ( bm.getDstProperty().equals( Axis.PROP_RANGE ) ) {
                result.add( (Axis)DomUtil.getElementById( dom, bm.getDstId() ) );
            }
        }
        return result;
    }
    
    private static List<Axis> getOtherBoundAxes( Application dom, Axis axis ) {
        List<BindingModel> bms= DomUtil.findBindings( dom, axis, Axis.PROP_RANGE );
        List<Axis> result= new ArrayList<Axis>();
        for ( BindingModel bm: bms ) {
            if ( bm.getSrcProperty().equals(Application.PROP_TIMERANGE) ) {
                result.addAll( getTimerangeBoundAxes( dom ) );
            } else if ( bm.getSrcId().equals(axis.getId()) ) {
                result.add( (Axis) DomUtil.getElementById( dom, bm.getDstId() ) );
            } else if ( bm.getDstId().equals(axis.getId()) ) {
                result.add( (Axis) DomUtil.getElementById( dom, bm.getSrcId() ) );
            }
        }
        return result;
    }
    
    TimeSeriesBrowseController( DataSourceController dataSourceController, final PlotElement p ) {

        this.changesSupport= new ChangesSupport(this.propertyChangeSupport,this);
        
        updateTsbTimer = new TickleTimer(300, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                //TODO: this would work, but the particular axis is not actually adjusting.  We need
                //to figure out who it's attached to, and see if any are adjusting.  Maybe even
                //put in new code in plot.getXAxis.valueIsAdjusting to check bindings.
                
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

                if ( dom.getController().isValueAdjusting() ) {
                    updateTsbTimer.tickle(); 
                    logger.log( Level.FINEST, "applicationController is adjusting" );
                } else {
                    Map<Object,Object> changes= new HashMap<Object, Object>();
                    if ( fpe!=null ) {
                        fpe.getController().getDataSourceFilter().getController().pendingChanges(changes);
                        changes.remove( PENDING_AXIS_OR_TIMERANGE_DIRTY );
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
                                updateTsb(false);
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
            this.plot = plotElementController.getDasPlot();
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

    protected void setupGen( DomNode node, final String property ) {
        timeSeriesBrowseListener = new PropertyChangeListener() {
            @Override
            public String toString() {
               return ""+TimeSeriesBrowseController.this;
            }
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                //we should have something to listen for locks.
                if (e.getPropertyName().equals(property)) {
                    changesSupport.registerPendingChange( TimeSeriesBrowseController.this, PENDING_AXIS_OR_TIMERANGE_DIRTY );
                    DatumRange dr=(DatumRange)e.getNewValue();
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
            if ( !appTimeRange.equals( Application.DEFAULT_TIME_RANGE ) && UnitsUtil.isTimeLocation( appTimeRange.getUnits() ) ) { //TODO: clean up.  This really should be based on if there is another guy controlling the timerange.
                setTimeRange( appTimeRange );
                dsf.getController().getTsb().setTimeRange( appTimeRange );
            } else {
                setTimeRange( defaultTimeRange );
                pd.getWriteMethod().invoke( node, defaultTimeRange );
            }

            //dsf.getController().getApplication().getController().bind( node, property, this, PROP_TIMERANGE ); // use node's property value.
            node.addPropertyChangeListener( property, timeSeriesBrowseListener );
        } catch (IntrospectionException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        } catch (IllegalAccessException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        } catch (InvocationTargetException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
        listenNode= node;
        listenProp= property;
    }

    protected void setup( boolean valueWasAdjusting ) {
        if ( p!=null && !valueWasAdjusting ) {
            this.xAxis.setDatumRange( dataSourceController.getTsb().getTimeRange() );
            this.domPlot.getXaxis().setAutoRange(false);
        }
        
        boolean setTsbInitialResolution = true;
        if (setTsbInitialResolution) {
            try {
                changesSupport.performingChange( TimeSeriesBrowseController.this, PENDING_AXIS_DIRTY );
                DatumRange tr = dataSourceController.getTsb().getTimeRange();
                if ( tr==null ) tr= this.domPlot.getXaxis().getRange();
                this.setTimeRange( tr );
                if ( this.domPlot.getXaxis().isAutoRange() && !valueWasAdjusting ) {
                    BindingModel[] bms= dsf.getController().getApplication().getBindings();
                    DatumRange appRange= dsf.getController().getApplication().getTimeRange();
                    if ( appRange.getUnits().isConvertableTo( tr.getUnits() )
                            && isBoundTimeRange( bms, this.domPlot.getXaxis().getId() ) ) { // check to see if the dom has a compatible timerange.
                        this.plot.getXAxis().resetRange(appRange);
                    } else {
                        this.plot.getXAxis().resetRange(tr);
                    }
                    if ( !this.plot.getXAxis().getLabel().contains("%{RANGE}") ) {
                        this.plot.getXAxis().setUserDatumFormatter(new DateTimeDatumFormatter( dsf.getController().getApplication().getOptions().isDayOfYear() ? DateTimeDatumFormatter.OPT_DOY : 0 )); // See PlotController.createDasPeer and listener that doesn't get event
                    }
                    this.domPlot.getXaxis().setAutoRange(true); // need to turn it back on because resetRange
                    this.plot.getXAxis().setScanRange(null);
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
            public void propertyChange(PropertyChangeEvent e) {
                if (plot.getXAxis().valueIsAdjusting()) {
                    return;
                } 
                //if ( domPlot.getController().getApplication().getController().isValueAdjusting() ) { // This must be commented out because of 1140.  I'm not sure why this was inserted in the first place.
                //    return;
                //}
                if (e.getPropertyName().equals("datumRange")) {
                    DatumRange dr=(DatumRange)e.getNewValue();
                    if ( UnitsUtil.isTimeLocation(dr.getUnits()) ) {
                        changesSupport.registerPendingChange( TimeSeriesBrowseController.this, PENDING_AXIS_OR_TIMERANGE_DIRTY );
                        logger.log(Level.FINE, "setTimeRange({0}) because of datumRange", dr);
                        setTimeRange( dr );
                        updateTsbTimer.tickle();
                    }
                } else if ( e.getPropertyName().equals( Plot.PROP_CONTEXT ) ) {
                    DatumRange dr=(DatumRange)e.getNewValue();
                    if ( UnitsUtil.isTimeLocation(dr.getUnits()) ) {
                        changesSupport.registerPendingChange( TimeSeriesBrowseController.this, PENDING_AXIS_OR_TIMERANGE_DIRTY );
                        logger.log(Level.FINE, "setTimeRange({0}) because of context", dr);
                        setTimeRange( dr );
                        updateTsbTimer.tickle();
                    }
                }

            }
        };

        this.plot.getXAxis().addPropertyChangeListener( DasAxis.PROPERTY_DATUMRANGE, timeSeriesBrowseListener);
        this.domPlot.addPropertyChangeListener( Plot.PROP_CONTEXT, timeSeriesBrowseListener ) ;

    }

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
                    logger.fine("WARNING: xaxis isn't sized, loading data at full resolution!"); //TODO: check into this
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
                        dom.getController().bind( dom, Application.PROP_TIMERANGE, domPlot, Plot.PROP_CONTEXT );
                        
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
                    //if (plot.isOverSize() && autorange==false ) {
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
                    dataSourceController.update(false);
                    dataSourceController.setTsbSuri(surl);
                    dataSourceController.dsf.uri= surl;
                    domPlot.controller.dom.controller.setFocusUri(surl);
                    //String blurUri= DataSetURI.blurTsbUri( surl );
                    //if ( blurUri!=null ) dataSourceController.dsf.uri= blurUri;
                }
            } else {
                logger.fine("loaded dataset satifies request");
            }
        }

    }

    /**
     * return the plot element that is responsible for handling this data source.  This
     * is the guy that was focused when this was created, and is attached to the plot x-axis
     * that controls this (if there is one).
     *
     * This was introduced because classes were accessing the local variable p.
     *
     * @return a PlotElement or null.
     */
    public PlotElement getPlotElement() {
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

    void release() {
        if (  isListeningToAxis() ) {
            this.plot.getXAxis().removePropertyChangeListener(DasAxis.PROPERTY_DATUMRANGE,timeSeriesBrowseListener);
            this.domPlot.removePropertyChangeListener( Plot.PROP_CONTEXT, timeSeriesBrowseListener ) ;
            this.xAxis= null;
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
    
    protected DatumRange timeRange = null;
    public static final String PROP_TIMERANGE = "timeRange";

    public DatumRange getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(DatumRange timeRange) {
        if ( !UnitsUtil.isTimeLocation( timeRange.getUnits() ) ) {
            logger.log(Level.FINE, "ignoring call to setTimeRange with non-time-location {0}", timeRange);
            return;
        }
        DatumRange oldTimeRange = this.timeRange;
        this.timeRange = timeRange;
        propertyChangeSupport.firePropertyChange(PROP_TIMERANGE, oldTimeRange, timeRange);
    }

    protected Datum resolution = null;
    public static final String PROP_RESOLUTION = "resolution";

    public Datum getResolution() {
        return resolution;
    }

    public void setResolution(Datum resolution) {
        Datum oldResolution = this.resolution;
        this.resolution = resolution;
        propertyChangeSupport.firePropertyChange(PROP_RESOLUTION, oldResolution, resolution);
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
    boolean isListeningToAxis() {
        return xAxis!=null;
    }

    /**
     * provide access to the TSB plot.
     * @return 
     */
    Plot getPlot() {
        return this.domPlot;
    }
}
