/*
 * AggregatingDataSource.java
 *
 * Created on October 25, 2007, 10:29 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.aggragator;

import org.das2.dataset.CacheTag;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.TimeUtil;
import java.util.logging.Level;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.SubTaskMonitor;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Map;
import java.util.logging.Logger;
import org.das2.fsm.FileStorageModel;
import org.das2.util.filesystem.FileSystem;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.MetadataModel;
import org.virbo.datasource.capability.TimeSeriesBrowse;

/**
 *
 * http://www.papco.org:8080/opendap/cdf/polar/hyd_h0/%Y/po_h0_hyd_%Y%m%d_v...cdf.dds?ELECTRON_DIFFERENTIAL_ENERGY_FLUX
 * @author jbf
 */
public class AggregatingDataSource extends AbstractDataSource {

    private FileStorageModel fsm;
    DataSourceFactory delegateDataSourceFactory;
    /**
     * metadata from the last read.
     */
    Map<String, Object> metadata;
    MetadataModel metadataModel;

    private DatumRange quantize(DatumRange timeRange) {
        try {
            //String s1= fsm.calculateNameFor(timeRange.min());
            //String s2= fsm.calculateNameFor(timeRange.max());

            String[] ss = fsm.getNamesFor(timeRange);
            if (ss.length == 0) return new DatumRange(TimeUtil.prevMidnight(timeRange.min()), TimeUtil.nextMidnight(timeRange.max()));
            DatumRange result = fsm.getRangeFor(ss[0]);
            for (int i = 0; i < ss.length; i++) {
                DatumRange r1 = fsm.getRangeFor(ss[i]);
                result = result.include(r1.max()).include(r1.min());
            }
            return result;
        } catch (IOException ex) {
            Logger.getLogger(AggregatingDataSource.class.getName()).log(Level.SEVERE, null, ex);
            timeRange = new DatumRange(TimeUtil.prevMidnight(timeRange.min()), TimeUtil.nextMidnight(timeRange.max()));
            return timeRange;
        }
    }

    /** Creates a new instance of AggregatingDataSource */
    public AggregatingDataSource(URL url) throws MalformedURLException, FileSystem.FileSystemOfflineException, IOException, ParseException {
        super(url);
        String surl = url.toString();
        delegateDataSourceFactory = AggregatingDataSourceFactory.getDelegateDataSourceFactory(surl);
        addCability(TimeSeriesBrowse.class, createTimeSeriesBrowse() );
        viewRange= DatumRangeUtil.parseTimeRange( super.params.get("timerange") );
    }

    private TimeSeriesBrowse createTimeSeriesBrowse() {
        return new TimeSeriesBrowse() {

            public void setTimeRange(DatumRange dr) {
                viewRange = quantize(dr);
                Logger.getLogger("virbo.datasource.agg").fine("set timerange=" + viewRange);
            }

            public void setTimeResolution(Datum d) {
            }

            public URL getURL() {
                try {
                    return new URL(AggregatingDataSource.this.getURL());
                } catch (MalformedURLException ex) {
                    Logger.getLogger(AggregatingDataSource.class.getName()).log(Level.SEVERE, null, ex);
                    return null;
                }
            }

            public DatumRange getTimeRange() {
                return viewRange;
            }

            public Datum getTimeResolution() {
                return null;
            }
        };        
    }
    
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        String[] ss = getFsm().getNamesFor(viewRange);

        Logger.getLogger("virbo.datasource.agg").info("aggregating " + ss.length + " files for " + viewRange);

        DDataSet result = null;

        if (ss.length > 1) {
            mon.setTaskSize(ss.length * 10);
            mon.started();
        }

        DatumRange cacheRange1 = null;

        for (int i = 0; i < ss.length; i++) {
            String scompUrl = getFsm().getFileSystem().getRootURL() + ss[i];
            if (!sparams.equals("")) {
                scompUrl += "?" + sparams;
            }
            URL compUrl = new URL(scompUrl);

            DataSource delegateDataSource = delegateDataSourceFactory.getDataSource(compUrl);
            metadataModel = delegateDataSource.getMetadataModel();

            ProgressMonitor mon1;
            if (ss.length > 1) {
                mon.setProgressMessage("getting " + ss[i]);
                mon1 = SubTaskMonitor.create(mon, i * 10, 10 * (i + 1));
            } else {
                mon1 = mon;
            }

            QDataSet ds1 = delegateDataSource.getDataSet(mon1);

            DatumRange dr1 = getFsm().getRangeFor(ss[i]);

            if (result == null) {
                result = DDataSet.copy(ds1);
                this.metadata = delegateDataSource.getMetaData(new NullProgressMonitor());
                cacheRange1 = dr1;
            } else {
                result.join(DDataSet.copy(ds1));
                cacheRange1 = new DatumRange(cacheRange1.min(), dr1.max());
            }
            if (ss.length > 1) {
                if (mon.isCancelled()) {
                    break;
                }
            }
        }
        cacheRange = cacheRange1;

        if (ss.length > 1) {
            mon.finished();
        }
        DDataSet dep0 = result == null ? null : (DDataSet) result.property(DDataSet.DEPEND_0);
        if (dep0 != null) {
            dep0.putProperty(DDataSet.CACHE_TAG, new CacheTag(cacheRange1, null));
        }

        return result;

    }

    @Override
    public MetadataModel getMetadataModel() {
        return metadataModel;
    }

    /**
     * returns the metadata provided by the first delegate dataset.
     */
    public Map<String, Object> getMetaData(ProgressMonitor mon) throws Exception {
        if (metadata == null) {
            Map<String, Object> retValue;
            retValue = super.getMetaData(mon);
            return retValue;
        } else {
            return metadata;
        }

    }
    /**
     * Holds value of property viewRange.
     */
    private DatumRange viewRange = DatumRangeUtil.parseTimeRangeValid("2006-07-03 to 2006-07-05");
    /**
     * this is the range of files that was loaded, based on the granularity of the
     * delegate.  This is used to calculate the new URL.
     */
    private DatumRange cacheRange = null;
    /**
     * Utility field used by bound properties.
     */
    private java.beans.PropertyChangeSupport propertyChangeSupport = new java.beans.PropertyChangeSupport(this);

    /**
     * Adds a PropertyChangeListener to the listener list.
     * @param l The listener to add.
     */
    public void addPropertyChangeListener(java.beans.PropertyChangeListener l) {
        propertyChangeSupport.addPropertyChangeListener(l);
    }

    /**
     * Removes a PropertyChangeListener from the listener list.
     * @param l The listener to remove.
     */
    public void removePropertyChangeListener(java.beans.PropertyChangeListener l) {
        propertyChangeSupport.removePropertyChangeListener(l);
    }

    /**
     * Getter for property viewRange.
     * @return Value of property viewRange.
     */
    public DatumRange getViewRange() {
        return this.viewRange;
    }

    /**
     * Setter for property viewRange.
     * @param viewRange New value of property viewRange.
     */
    public void setViewRange(DatumRange viewRange) {
        DatumRange oldViewRange = this.viewRange;
        this.viewRange = viewRange;
        propertyChangeSupport.firePropertyChange("viewRange", oldViewRange, viewRange);
    }

    public FileStorageModel getFsm() {
        return fsm;
    }

    public void setFsm(FileStorageModel fsm) {
        this.fsm = fsm;
    }
    /**
     * Holds value of property params.
     */
    private String sparams = "";

    /**
     * Setter for property args.
     * @param args New value of property args.
     */
    public void setParams(String params) {
        String oldParams = this.sparams;
        this.sparams = params;
        propertyChangeSupport.firePropertyChange("args", oldParams, params);
    }

    @Override
    public String getURL() {
        String surl = this.resourceURL.toString() + "?" ;
        if (sparams != null && !sparams.equals("") ) surl += sparams + "&";
        surl += "timerange=" + String.valueOf(viewRange);

        DataSetURL.URLSplit split = DataSetURL.parse(surl);
        Map<String,String> mparams = DataSetURL.parseParams(split.params);
        String stimeRange = viewRange.toString();
        stimeRange = stimeRange.replaceAll(" ", "+");
        mparams.put("timerange", stimeRange);
        split.params = DataSetURL.formatParams(mparams);

        return DataSetURL.format(split);
    }
}
