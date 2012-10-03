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
import org.virbo.autoplot.util.DateTimeDatumFormatter;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.capability.TimeSeriesBrowse;

/**
 *
 * @author jbf
 */
public class TimeSeriesBrowseController {

    PlotElement p;
    DasAxis xAxis;
    DasPlot plot;
    Plot domPlot;
    PlotElementController panelController;
    DataSourceController dataSourceController;
    DataSourceFilter dsf;
    private ChangesSupport changesSupport;

    private static final String PENDING_AXIS_DIRTY= "tsbAxisDirty";
    private static final String PENDING_TIMERANGE_DIRTY= "tsbTimerangeDirty";

    private static final Logger logger = org.das2.util.LoggerManager.getLogger("autoplot.tsb");
    TickleTimer updateTsbTimer;
    PropertyChangeListener timeSeriesBrowseListener;
    private DomNode listenNode=null;
    private String listenProp=null;

    TimeSeriesBrowseController( DataSourceController dataSourceController, final PlotElement p ) {

        this.changesSupport= new ChangesSupport(this.propertyChangeSupport,this);
        
        updateTsbTimer = new TickleTimer(100, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ( dsf.getController().getApplication().getController().isValueAdjusting() ) {
                    updateTsbTimer.tickle();
                    return;
                } else {
                    if ( p!=null && p.getController().getDataSourceFilter().getController().getTsb() == null ) {
                        // leftover event doesn't need any special handling since TSB has been removed.
                        // System.err.println("entering that strange branch that probably isn't needed ");
                        return;
                    } else {
                        updateTsb(false);
                        changesSupport.changePerformed( this, PENDING_AXIS_DIRTY );
                        changesSupport.changePerformed( this, PENDING_TIMERANGE_DIRTY ); // little sloppy, since only one or the other is set.
                    }
                }
            }
        });

        this.dsf= dataSourceController.dsf;
        this.dataSourceController= dataSourceController;
        if ( p!=null ) {
            this.p = p;
            this.domPlot= dsf.getController().getApplication().getController().getPlotFor(p);
            this.panelController = p.getController();
        } else {
            logger.fine("no plotElement provided, better come back to set up from timerange.");
        }

        if ( p!=null ) {
            this.plot = panelController.getDasPlot();
            this.xAxis = panelController.getDasPlot().getXAxis();
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
            public void propertyChange(PropertyChangeEvent e) {
                //we should have something to listen for locks.
                if (e.getPropertyName().equals(property)) {
                    changesSupport.registerPendingChange( this, PENDING_TIMERANGE_DIRTY );
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
            logger.log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        listenNode= node;
        listenProp= property;
    }

    protected void setup( boolean valueWasAdjusting ) {
        if ( p!=null && !valueWasAdjusting ) {
            this.xAxis.setDatumRange( dataSourceController.getTsb().getTimeRange() );
        }
        
        boolean setTsbInitialResolution = true;
        if (setTsbInitialResolution) {
            try {
                DatumRange tr = dataSourceController.getTsb().getTimeRange();
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
                changesSupport.changePerformed( this, PENDING_AXIS_DIRTY );
            } catch ( RuntimeException e ) {
                throw e;
            }
        }

        timeSeriesBrowseListener = new PropertyChangeListener() {
            @Override
            public String toString() {
               return ""+TimeSeriesBrowseController.this;
            }
            public void propertyChange(PropertyChangeEvent e) {
                if (plot.getXAxis().valueIsAdjusting()) {
                    return;
                } 
                if (e.getPropertyName().equals("datumRange")) {
                    changesSupport.registerPendingChange( this, PENDING_AXIS_DIRTY );
                    DatumRange dr=(DatumRange)e.getNewValue();
                    if ( UnitsUtil.isTimeLocation(dr.getUnits()) ) {
                        setTimeRange( dr );
                        updateTsbTimer.tickle();
                    }
                } else if ( e.getPropertyName().equals( Plot.PROP_CONTEXT ) ) {
                    changesSupport.registerPendingChange( this, PENDING_AXIS_DIRTY );
                    DatumRange dr=(DatumRange)e.getNewValue();
                    if ( UnitsUtil.isTimeLocation(dr.getUnits()) ) {
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

            DatumRange visibleRange = null;
            Datum newResolution = null;
            CacheTag testCacheTag = null; // sloppy one
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

            if (tag == null || !tag.contains(testCacheTag)) {
                TimeSeriesBrowse tsb= dataSourceController.getTsb();
                if ( tsb==null ) {
                    logger.warning("tsbc253: tsb was null");
                    return;
                }
                if ( xAxis!=null ) {
                    if (plot.isOverSize() && autorange==false ) {
                        visibleRange = DatumRangeUtil.rescale(visibleRange, -0.3, 1.3); //TODO: be aware of
                    }
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
                    dataSourceController.cancel();
                    dataSourceController.update();
                    dataSourceController.setTsbSuri(surl);
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
}
