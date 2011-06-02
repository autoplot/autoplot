/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.logging.Logger;
import org.virbo.autoplot.util.TickleTimer;
import org.das2.dataset.CacheTag;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.UnitsUtil;
import org.das2.graph.DasAxis;
import org.das2.graph.DasPlot;
import org.virbo.autoplot.util.DateTimeDatumFormatter;
import org.virbo.dataset.QDataSet;

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

    private static final Logger logger = Logger.getLogger("ap.tsb");
    TickleTimer updateTsbTimer;
    PropertyChangeListener timeSeriesBrowseListener;

    TimeSeriesBrowseController( DataSourceController dataSourceController, PlotElement p ) {

        this.changesSupport= new ChangesSupport(this.propertyChangeSupport,this);
        
        updateTsbTimer = new TickleTimer(100, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ( domPlot.getController().getApplication().getController().isValueAdjusting() ) {
                    updateTsbTimer.tickle();
                    return;
                } else {
                    updateTsb(false);
                    changesSupport.changePerformed( this, PENDING_AXIS_DIRTY );
                }
            }
        });
        
        this.p = p;
        this.domPlot= p.getController().getApplication().getController().getPlotFor(p);
        this.panelController = p.getController();
        this.dsf= dataSourceController.dsf;
        this.dataSourceController= dataSourceController;

        this.plot = panelController.getDasPlot();
        this.xAxis = panelController.getDasPlot().getXAxis();
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
            public String toString() {
               return ""+TimeSeriesBrowseController.this;
            }
            public void propertyChange(PropertyChangeEvent e) {
                //we should have something to listen for locks.
                if (e.getPropertyName().equals(property)) {
                    changesSupport.registerPendingChange( this, PENDING_TIMERANGE_DIRTY );
                    DatumRange dr=(DatumRange)e.getNewValue();
                    setTimeRange( UnitsUtil.isTimeLocation(dr.getUnits()) ? dr : null );
                    updateTsbTimer.tickle();
                }
            }
        };
        node.addPropertyChangeListener( property, timeSeriesBrowseListener );
    }

    protected void setup( boolean valueWasAdjusting ) {
        boolean setTsbInitialResolution = true;
        if (setTsbInitialResolution) {
            try {
                DatumRange tr = dataSourceController.getTsb().getTimeRange();
                this.setTimeRange( tr );
                if ( this.domPlot.getXaxis().isAutoRange() ) {
                    BindingModel[] bms= this.panelController.getApplication().getBindings();
                    DatumRange appRange= this.panelController.getApplication().getTimeRange();
                    if ( appRange.getUnits().isConvertableTo( tr.getUnits() )
                            && isBoundTimeRange( bms, this.domPlot.getXaxis().getId() ) ) { // check to see if the dom has a compatible timerange.
                        this.plot.getXAxis().resetRange(appRange);
                    } else {
                        this.plot.getXAxis().resetRange(tr);
                    }
                    this.plot.getXAxis().setUserDatumFormatter(new DateTimeDatumFormatter()); // See PlotController.createDasPeer and listener that doesn't get event
                    this.domPlot.getXaxis().setAutoRange(true); // need to turn it back on because resetRange
                }
                updateTsb(true);
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
                    setTimeRange( UnitsUtil.isTimeLocation(dr.getUnits()) ? dr : null );
                    updateTsbTimer.tickle();
                }
            }
        };

        this.plot.getXAxis().addPropertyChangeListener( DasAxis.PROPERTY_DATUMRANGE, timeSeriesBrowseListener);

    }

    public void updateTsb(boolean autorange) {

        if ( p.getController().getDataSourceFilter().getController().getTsb() == null) {
            return;
        }

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
                    System.err.println( "join property was not a QDataSet: "+join0.toString() );
                }
            }

            DatumRange visibleRange = null;
            Datum newResolution = null;
            CacheTag newCacheTag = null;  // new one we'll retrieve
            CacheTag testCacheTag = null; // sloppy one
            if ( xAxis!=null ) {
                visibleRange= xAxis.getDatumRange();
                newResolution = visibleRange.width().divide(xAxis.getDLength());
                // don't waste time by chasing after 10% of a dataset.
                DatumRange newRange = visibleRange;
                testCacheTag = new CacheTag( DatumRangeUtil.rescale(newRange, 0.01, 0.99), newResolution );
                newCacheTag = new CacheTag(newRange, newResolution);
                trange= newRange;
            } else {
                newCacheTag = new CacheTag(trange,null);
                testCacheTag= newCacheTag;
            }

            if (tag == null || !tag.contains(testCacheTag)) {
                if ( xAxis!=null ) {
                    if (plot.isOverSize() && autorange==false ) {
                        visibleRange = DatumRangeUtil.rescale(visibleRange, -0.3, 1.3); //TODO: be aware of
                    }
                    dataSourceController.getTsb().setTimeRange(trange);
                    dataSourceController.getTsb().setTimeResolution(newResolution);
                } else {
                    dataSourceController.getTsb().setTimeRange(trange);
                    dataSourceController.getTsb().setTimeResolution(null);
                }

                String surl;
                surl = dataSourceController.tsb.getURI();
                // check the registry for URLs, compare to surl, append prefix if necessary.
                if (!autorange && surl.equals( dataSourceController.getTsbSuri())) {
                    logger.fine("we do no better with tsb");
                } else {
                    dataSourceController.cancel();
                    dataSourceController.update(autorange, autorange);
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
        return changesSupport.isPendingChanges();
    }

    void release() {
        this.plot.getXAxis().removePropertyChangeListener(DasAxis.PROPERTY_DATUMRANGE,timeSeriesBrowseListener);
        this.xAxis= null;
        timeSeriesBrowseListener = null;
    }
    
    protected DatumRange timeRange = null;
    public static final String PROP_TIMERANGE = "timeRange";

    public DatumRange getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(DatumRange timeRange) {
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
