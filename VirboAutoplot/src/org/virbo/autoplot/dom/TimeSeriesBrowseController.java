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
    
    private static final Logger logger = Logger.getLogger("ap.tsb");
    Timer updateTsbTimer;
    PropertyChangeListener timeSeriesBrowseListener;

    TimeSeriesBrowseController( Panel p ) {
        updateTsbTimer = new Timer(100, new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                updateTsb(false);
            }
        });
        
        updateTsbTimer.setRepeats(false);
        this.p = p;
        this.panelController = p.getController();
        this.dataSourceController= p.getDataSourceFilter().getController();
        this.plot = panelController.getPlot();
        this.xAxis = panelController.getPlot().getXAxis();
    }

    public void setup() {
        boolean setTsbInitialResolution = true;
        if (setTsbInitialResolution) {
            DatumRange timeRange = dataSourceController.getTsb().getTimeRange();
            this.plot.getXAxis().resetRange(timeRange);
            updateTsb(true);
        }

        timeSeriesBrowseListener = new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent e) {
                if (plot.getXAxis().valueIsAdjusting()) {
                    return;
                } 
                if (e.getPropertyName().equals("datumRange")) {
                    updateTsbTimer.restart();
                }
            }
        };

        this.plot.getXAxis().addPropertyChangeListener(timeSeriesBrowseListener);

    }

    public void updateTsb(boolean autorange) {

        if ( p.getDataSourceFilter().getController().getTsb() == null) {
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
                if (plot.isOverSize()) {
                    visibleRange = DatumRangeUtil.rescale(visibleRange, -0.3, 1.3);
                }
                p.getDataSourceFilter().getController().getTsb().setTimeRange(visibleRange);
                p.getDataSourceFilter().getController().getTsb().setTimeResolution(newResolution);
                String surl;
                surl = DataSetURL.getDataSourceUri( p.getDataSourceFilter().getController()._getDataSource());
                // check the registry for URLs, compare to surl, append prefix if necessary.
                if (!autorange && surl.equals( this.dataSourceController.getTsbSuri())) {
                    logger.fine("we do no better with tsb");
                } else {
                    dataSourceController.update(autorange, autorange);
                    this.dataSourceController._setTsbSuri(surl);
                    //p.getDataSourceFilter().setSuri(surl);
                }
            } else {
                logger.fine("loaded dataset satifies request");
            }
        }

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

    public synchronized void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(propertyName, listener);
    }

    public synchronized void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    public synchronized void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
    }

    public synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }


}
