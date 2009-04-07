/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.logging.Logger;
import javax.swing.Timer;
import org.das2.dataset.CacheTag;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.UnitsUtil;
import org.das2.graph.DasAxis;
import org.das2.graph.DasPlot;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURL;

/**
 *
 * @author jbf
 */
public class TimeSeriesBrowseController {

    Panel p;
    DasAxis xAxis;
    DasPlot plot;
    PanelController panelController;
    DataSourceController dataSourceController;
    DataSourceFilter dsf;
    private ChangesSupport changesSupport;

    private static final String PENDING_AXIS_DIRTY= "tsbAxisDirty";

    private static final Logger logger = Logger.getLogger("ap.tsb");
    Timer updateTsbTimer;
    PropertyChangeListener timeSeriesBrowseListener;

    TimeSeriesBrowseController( Panel p ) {

        this.changesSupport= new ChangesSupport(this.propertyChangeSupport,this);
        
        updateTsbTimer = new Timer(100, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateTsb(false);
                changesSupport.changePerformed( this, PENDING_AXIS_DIRTY );
            }
        });
        
        updateTsbTimer.setRepeats(false);
        this.p = p;
        this.panelController = p.getController();
        this.dsf= p.getController().getDataSourceFilter();
        this.dataSourceController= dsf.getController();
        
        this.plot = panelController.getDasPlot();
        this.xAxis = panelController.getDasPlot().getXAxis();
    }

    public void setup( boolean valueWasAdjusting ) {
        boolean setTsbInitialResolution = true;
        if (setTsbInitialResolution) {
            DatumRange timeRange = dataSourceController.getTsb().getTimeRange();
            if ( !valueWasAdjusting ) this.plot.getXAxis().resetRange(timeRange);
            updateTsb(true);
        }

        timeSeriesBrowseListener = new PropertyChangeListener() {
            public String toString() {
               return ""+TimeSeriesBrowseController.this;
            }
            public void propertyChange(PropertyChangeEvent e) {
                if (plot.getXAxis().valueIsAdjusting()) {
                    return;
                } 
                if (e.getPropertyName().equals("datumRange")) {
                    changesSupport.registerPendingChange( this, PENDING_AXIS_DIRTY );
                    updateTsbTimer.restart();
                }
            }
        };

        this.plot.getXAxis().addPropertyChangeListener(timeSeriesBrowseListener);

    }

    public void updateTsb(boolean autorange) {

        if ( p.getController().getDataSourceFilter().getController().getTsb() == null) {
            return;
        }

        if (UnitsUtil.isTimeLocation(xAxis.getUnits())) {

            // CacheTag "tag" identifies what we have already
            QDataSet ds = this.dataSourceController.getDataSet();
            QDataSet dep0 = ds == null ? null : (QDataSet) ds.property(QDataSet.DEPEND_0);
            CacheTag tag = dep0 == null ? null : (CacheTag) dep0.property(QDataSet.CACHE_TAG);

            DatumRange visibleRange = xAxis.getDatumRange();

            Datum newResolution = visibleRange.width().divide(xAxis.getDLength());

            // don't waste time by chasing after 10% of a dataset.
            DatumRange newRange = visibleRange;
            newRange = DatumRangeUtil.rescale(newRange, 0.1, 0.9);

            CacheTag newCacheTag = new CacheTag(newRange, newResolution);

            if (tag == null || !tag.contains(newCacheTag)) {
                if (plot.isOverSize() && autorange==false ) {
                    visibleRange = DatumRangeUtil.rescale(visibleRange, -0.3, 1.3);
                }
                dataSourceController.getTsb().setTimeRange(visibleRange);
                dataSourceController.getTsb().setTimeResolution(newResolution);
                String surl;
                surl = DataSetURL.getDataSourceUri( dataSourceController._getDataSource());
                // check the registry for URLs, compare to surl, append prefix if necessary.
                if (!autorange && surl.equals( dataSourceController.getTsbSuri())) {
                    logger.fine("we do no better with tsb");
                } else {
                    dataSourceController.update(autorange, autorange);
                    dataSourceController._setTsbSuri(surl);
                }
            } else {
                logger.fine("loaded dataset satifies request");
            }
        }

    }

    public boolean isPendingChanges() {
        return changesSupport.isPendingChanges();
    }

    void release() {
        this.plot.getXAxis().removePropertyChangeListener(timeSeriesBrowseListener);
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

    public String toString() {
        return this.dsf + " timeSeriesBrowse controller";
    }
}
