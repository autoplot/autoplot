/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.InterruptedIOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import org.das2.CancelledOperationException;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasPlot;
import org.das2.system.RequestProcessor;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.autoplot.AutoplotUtil;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.TransposeRank2DataSet;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.URLSplit;
import org.virbo.datasource.capability.Caching;
import org.virbo.datasource.capability.TimeSeriesBrowse;

/**
 *
 * @author jbf
 */
public class DataSourceController {

    Logger logger = Logger.getLogger("vap.dataSourceController");
    DataSourceFilter dsf;
    private ApplicationModel model;
    private Application dom;
    private Panel panel;
    /**
     * the current load being monitored.
     */
    private ProgressMonitor mon;
    
    private PropertyChangeListener updateSlicePropertyChangeListener = new PropertyChangeListener() {

        public void propertyChange(PropertyChangeEvent e) {
            if ( getDataSet() != null &&  getDataSet().rank() == 3) updateFill();
        }
    };
    private PropertyChangeListener updateMePropertyChangeListener = new PropertyChangeListener() {

        public void propertyChange(PropertyChangeEvent e) {
            updateFill();
        }
    };
    private PropertyChangeListener resetMePropertyChangeListener = new PropertyChangeListener() {

        public void propertyChange(PropertyChangeEvent e) {
            if (e.getNewValue() == null && e.getOldValue() == null) {
                return;
            } else {
                resolveDataSource(getMonitor("resetting data source", "resetting data source"));
            }
        }
    };
    private TimeSeriesBrowseController timeSeriesBrowseController;
    private PanelController panelController;
    private static final String PENDING_DATA_SOURCE = "dataSource";
    private static final String PENDING_FILL_DATASET = "fillDataSet";
    private static final String PENDING_UPDATE = "update";

    public DataSourceController( ApplicationModel model, final Panel panel ) {

        this.model = model;
        this.dom = model.getDocumentModel();
        this.panel = panel;
        this.panelController = panel.getController();
        this.dsf = panel.getDataSourceFilter();
        this.dsf.controller = this;

        dsf.addPropertyChangeListener(DataSourceFilter.PROP_SLICEDIMENSION, updateSlicePropertyChangeListener);
        dsf.addPropertyChangeListener(DataSourceFilter.PROP_SLICEINDEX, updateSlicePropertyChangeListener);
        dsf.addPropertyChangeListener(DataSourceFilter.PROP_TRANSPOSE, updateSlicePropertyChangeListener);

        dsf.addPropertyChangeListener(DataSourceFilter.PROP_FILL, updateMePropertyChangeListener);
        dsf.addPropertyChangeListener(DataSourceFilter.PROP_VALID_RANGE, updateMePropertyChangeListener);

        dsf.addPropertyChangeListener(DataSourceFilter.PROP_SURI, resetMePropertyChangeListener);

    }

    /**
     * returns the biggest index that a dimension can be sliced at.  Note for non-qubes, this will be 0 when the dimension
     * is not 0.
     * 
     * @param i the dimension to slice
     * @return the number of slice indeces.
     */
    public int getMaxSliceIndex(int i) {
        if ( getDataSet() == null) {
            return 0;
        }
        int sliceDimension = i;
        if (sliceDimension == 0) {
            return  getDataSet().length();
        }
        int[] qube = DataSetUtil.qubeDims( getDataSet());
        if (qube == null || qube.length <= sliceDimension) {
            return 0;
        } else {
            try {
                return qube[sliceDimension];
            } catch ( ArrayIndexOutOfBoundsException ex ) {
                throw ex;
            }
        }
    }

    /**
     * calculate the dimensions for the dataset.  Rank 3 datasets must be reduced, and this sets
     * the names so that slicing can be done.
     * 
     * preconditions:
     *    dsf.getDataSet() is non-null
     * postconditions:
     *    dsf.getDepnames returns the names of the dimensions.
     *    dsf.getSliceDimension returns the preferred dimension to slice.
     */
    private void doDimensionNames() {

        QDataSet ds =  getDataSet();

        String[] depNames = new String[3];
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

        logger.fine("dep names: " + Arrays.asList(depNames));
        setDepnames(Arrays.asList(depNames));

        if (ds.rank() > 2) {
            guessSliceDimension();
        }
    }

    public void setDataSource(DataSource dataSource) {

        if (timeSeriesBrowseController != null) {
            timeSeriesBrowseController.release();
        }

        DataSource oldSource = _getDataSource();

        if (dataSource == null) {
             _setCaching(null);
             _setTsb(null);
             _setTsbSuri(null);
            dsf.setSuri(null);

        } else {

             _setCaching(dataSource.getCapability(Caching.class));
             _setTsb(dataSource.getCapability(TimeSeriesBrowse.class));

        }

        this.dsf.setValidRange("");
        this.dsf.setFill("");
        
        _setDataSource(dataSource);

        if (oldSource == null || !oldSource.equals(dataSource)) {
            if ( getTsb() != null) {
                 _setDataSet(null);

                timeSeriesBrowseController = new TimeSeriesBrowseController(panel);
                timeSeriesBrowseController.setup();

            } else {
                update(true, true);
            }
        }

    }

    /**
     * set the new dataset, do autoranging and autolabelling.
     * 
     * preconditions: 
     *   autoplot is displaying any dataset.  
     *   A new DataSource has been set, but the dataset is generally not from the DataSource.
     *   
     * postconditions: 
     *  the dataset is set
     *  labels are set, axes are set.  
     *  Labels reset might have triggered a timer that will redo layout.
     *  slice dimensions are set for rank 3 dataset.
     * 
     * @param ds
     * @param autorange if false, autoranging will not be done.  if false, autoranging
     *   might be done.
     */
    public synchronized void setDataSetInternal(QDataSet ds, boolean autorange) {

        List<String> problems = new ArrayList<String>();

        if (ds != null && !DataSetUtil.validate(ds, problems)) {
            StringBuffer message = new StringBuffer("data set is invalid:\n");
            for (String s : problems) {
                message.append(s + "\n");
            }
            JOptionPane.showMessageDialog(model.getCanvas(), message); //TODO: View code in controller

            if ( ds instanceof MutablePropertyDataSet ) {
                //MutablePropertyDataSet mds= (MutablePropertyDataSet)ds;
                //DataSetUtil.makeValid(mds);
               //  we would also have to modify the metadata, labels etc.
                return;
            } else {
                return;
            }
        }

         _setDataSet(ds);

        if (ds == null) {
            _setDataSet(null);
            _setProperties(null);
            _setFillProperties(null);
            _setFillDataSet(null);
            return;
        }

        setStatus("busy: apply fill and autorange");

        extractProperties();
        doDimensionNames();
        doFillValidRange();
        updateFill();

    }

    /**
     * preconditions:
     *   dataSet is set.
     * postconditions:
     *   properties is set.
     */
    private void extractProperties() {
        Map<String, Object> properties; // QDataSet properties.

        properties = AutoplotUtil.extractProperties(getDataSet());
        if ( _getDataSource() != null) {
            properties = AutoplotUtil.mergeProperties( _getDataSource().getProperties(), properties);
        }

        _setProperties(properties);

    }

    /**
     * look in the metadata for fill and valid range.
     * @param autorange
     * @param interpretMetadata
     */
    public void doFillValidRange() {
        Object v;


        Map<String, Object> properties = getProperties();

        if ((v = properties.get(QDataSet.FILL_VALUE)) != null) {
            dsf.setFill(String.valueOf(v));
        }

        Double vmin = (Double) properties.get(QDataSet.VALID_MIN);
        Double vmax = (Double) properties.get(QDataSet.VALID_MAX);
        if (vmin != null || vmax != null) {
            if (vmin == null) {
                vmin = -1e38;
            }
            if (vmax == null) {
                vmax = 1e38;
            }
            dsf.setValidRange("" + vmin + " to " + vmax);
        }
    }

    /**
     * the fill parameters have changed, so update the auto range stats.
     * This should not be run on the AWT event thread!
     * @param autorange if false, then no autoranging is done.
     * 
     * preconditions: The dataset has been loaded
     * postconditions: 
     *   the "fill" dataset is created, maybe by making the dataset mutable.  
     *   The dataset's rank has been reduced to two, if necessary.  
     *   the fillDataSet is set.  the fillProperties are set.
     *   reduceRankString is set to document the reduction
     *   all parties interested in the fill dataset will be notified of the new version.
     * 
     */
    @SuppressWarnings("unchecked")
    private void updateFill() {
        pendingChanges.add(PENDING_FILL_DATASET);
        logger.fine("enter updateFill");

        final DataSourceFilter dsf = panel.getDataSourceFilter();

        if (getDataSet() == null) {
            return;
        }

        Map properties = getProperties();

        MutablePropertyDataSet fillDs;

        if (getDataSet().rank() == 3) {

            QDataSet ds;
            QDataSet dep;
            int sliceDimension = dsf.getSliceDimension();
            int sliceIndex = dsf.getSliceIndex();

            if (sliceDimension == 2) {
                int index = Math.min(getDataSet().length(0, 0) - 1, sliceIndex);
                ds = DataSetOps.slice2(getDataSet(), index);
                dep = (QDataSet) getDataSet().property(QDataSet.DEPEND_2);
            } else if (sliceDimension == 1) {
                int index = Math.min(getDataSet().length(0) - 1, sliceIndex);
                ds = DataSetOps.slice1(getDataSet(), index);
                dep = (QDataSet) getDataSet().property(QDataSet.DEPEND_1);
            } else if (sliceDimension == 0) {
                int index = Math.min(getDataSet().length() - 1, sliceIndex);
                ds = DataSetOps.slice0(getDataSet(), index);
                dep = (QDataSet) getDataSet().property(QDataSet.DEPEND_0);
            } else {
                throw new IllegalStateException("sliceDimension");
            }

            String reduceRankString;
            List<String> names = getDepnames();
            if (dep == null) {
                reduceRankString = names.get(sliceDimension) + "=" + sliceIndex;
            } else {
                reduceRankString = PanelUtil.describe(names.get(sliceDimension), dep, sliceIndex);
            }
            _setReduceDataSetString(reduceRankString);

            properties = PanelUtil.sliceProperties(properties, sliceDimension);

            if (dsf.isTranspose()) {
                ds = new TransposeRank2DataSet(ds);
                properties = PanelUtil.transposeProperties(properties);
            }

            fillDs = DataSetOps.makePropertiesMutable(ds);

        } else {
            fillDs = DataSetOps.makePropertiesMutable(getDataSet());
            _setReduceDataSetString(null);
        }

        /*  begin fill dataset  */




        double vmin = Double.NEGATIVE_INFINITY, vmax = Double.POSITIVE_INFINITY, fill = Double.NaN;

        try {
            double[] vminMaxFill = PanelUtil.parseFillValidRangeInternal(dsf.getValidRange(), dsf.getFill());
            vmin = vminMaxFill[0];
            vmax = vminMaxFill[1];
            fill = vminMaxFill[2];
        } catch (ParseException ex) {
            ex.printStackTrace();
        }
        // check the dataset for fill data, inserting canonical fill values.
        AutoplotUtil.applyFillValidRange(fillDs, vmin, vmax, fill);

        _setFillProperties(properties);
        if (fillDs == getDataSet()) { //kludge to force reset renderer, because QDataSet is mutable.
            _setFillDataSet(null);
        }
        _setFillDataSet(fillDs);
        pendingChanges.remove(PENDING_FILL_DATASET);
    }

    /**
     * do update on this thread, ensuring that only one data load is occuring at a
     * time.  Note if a dataSource doesn't check mon.isCancelled(), then processing
     * will block until the old load is done.
     * @param autorange
     * @param interpretMeta
     */
    private synchronized void updateImmediately( final boolean autorange, boolean interpretMeta ) {
        /*** here is the data load ***/
        setStatus("busy: loading dataset");

        if ( _getDataSource() != null) {
            QDataSet dataset = loadDataSet();
            setStatus("done loading dataset");
            setDataSetInternal(dataset, autorange);
        } else {
            setDataSetInternal(null, autorange);
        }

        if ( getTsb() != null) {
            String oldsurl = panel.getDataSourceFilter().getSuri();
            String newsurl = getTsb().getURL().toString();
            URLSplit split = URLSplit.parse(newsurl);
            if (oldsurl != null) {
                URLSplit oldSplit = URLSplit.parse(oldsurl);
                split.vapScheme = oldSplit.vapScheme;
            }
            newsurl = URLSplit.format(split);
        }

        setStatus("ready");

    }

    /**
     * update the model and view using the new DataSource to create a new dataset,
     * then inspecting the dataset to decide on axis settings.
     * @param autorange if false, then no autoranging is done, just the fill part.
     */
    public synchronized void update(final boolean autorange, final boolean interpretMeta) {
        pendingChanges.add(PENDING_UPDATE);
        DataSourceFilter dsf = panel.getDataSourceFilter();
        _setDataSet(null);

        Runnable run = new Runnable() {
            public void run() {
                updateImmediately(autorange, interpretMeta);
                pendingChanges.remove(PENDING_UPDATE);
            }
        };

        if ( _getDataSource() != null &&  _getDataSource().asynchronousLoad() && !dom.getController().isHeadless()) {
            logger.info("invoke later do load");
            if (mon != null) {
                System.err.println("double load!");
                if (mon != null) {
                    mon.cancel();
                }
            }
            RequestProcessor.invokeLater(run);
        } else {
            run.run();
        }
    }

    /****** controller properties *******/
    
    /**
     * raw properties provided by the datasource after the data load.
     */
    public static final String PROP_RAWPROPERTIES = "rawProperties";
    protected Map<String, Object> rawProperties = null;

    public Map<String, Object> getRawProperties() {
        return rawProperties;
    }

    public void setRawProperties(Map<String, Object> rawProperties) {
        Map<String, Object> oldRawProperties = this.rawProperties;
        this.rawProperties = rawProperties;
        propertyChangeSupport.firePropertyChange(PROP_RAWPROPERTIES, oldRawProperties, rawProperties);
    }
    
    
    protected TimeSeriesBrowse tsb = null;
    public static final String PROP_TSB = "tsb";

    public TimeSeriesBrowse getTsb() {
        return tsb;
    }

    public void _setTsb(TimeSeriesBrowse tsb) {
        TimeSeriesBrowse oldTsb = this.tsb;
        this.tsb = tsb;
        propertyChangeSupport.firePropertyChange(PROP_TSB, oldTsb, tsb);
    }
    protected String tsbSuri = null;
    public static final String PROP_TSBSURI = "tsbSuri";

    public String getTsbSuri() {
        return tsbSuri;
    }

    public void _setTsbSuri(String tsbSuri) {
        String oldTsbSuri = this.tsbSuri;
        this.tsbSuri = tsbSuri;
        propertyChangeSupport.firePropertyChange(PROP_TSBSURI, oldTsbSuri, tsbSuri);
    }
    
    protected Caching caching = null;
    public static final String PROP_CACHING = "caching";

    public Caching getCaching() {
        return caching;
    }

    public void _setCaching(Caching caching) {
        Caching oldCaching = this.caching;
        this.caching = caching;
        propertyChangeSupport.firePropertyChange(PROP_CACHING, oldCaching, caching);
    }
    
    protected DataSource dataSource = null;
    public static final String PROP_DATASOURCE = "dataSource";
    
    public DataSource _getDataSource() {
        return dataSource;
    }

    public void _setDataSource(DataSource dataSource) {
        DataSource oldDataSource = this.dataSource;
        this.dataSource = dataSource;
        propertyChangeSupport.firePropertyChange(PROP_DATASOURCE, oldDataSource, dataSource);
    }

    
    /**
     * the dataset loaded from the data source.
     */
    protected QDataSet dataSet = null;
    public static final String PROP_DATASET = "dataSet";

    public QDataSet getDataSet() {
        return dataSet;
    }

    public void _setDataSet(QDataSet dataSet) {
        QDataSet oldDataSet = this.dataSet;
        this.dataSet = dataSet;
        propertyChangeSupport.firePropertyChange(PROP_DATASET, oldDataSet, dataSet);
    }
    /**
     * fill dataset is a copy of the loaded dataset, with fill data applied.  If dataset has mutable properties,
     * then the fillDataSet will be the same as the dataset, and the dataset's properties are modified.
     */
    protected QDataSet fillDataSet = null;
    public static final String PROP_FILLDATASET = "fillDataSet";

    public QDataSet getFillDataSet() {
        return fillDataSet;
    }

    public void _setFillDataSet(QDataSet fillDataSet) {
        QDataSet oldFillDataSet = this.fillDataSet;
        this.fillDataSet = fillDataSet;
        propertyChangeSupport.firePropertyChange(PROP_FILLDATASET, oldFillDataSet, fillDataSet);
    }
    
    private List<String> depnames = Arrays.asList(new String[]{"first", "second", "last"});
    public static final String PROP_DEPNAMES = "depnames";

    public List<String> getDepnames() {
        return this.depnames;
    }

    public void setDepnames(List<String> newdepnames) {
        List<String> olddepnames = depnames;
        this.depnames = newdepnames;
        if (!newdepnames.equals(olddepnames)) {
            propertyChangeSupport.firePropertyChange(PROP_DEPNAMES, olddepnames, newdepnames);
        }
    }

    protected Map<String, Object> properties = null;
    public static final String PROP_PROPERTIES = "properties";

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void _setProperties(Map<String, Object> properties) {
        Map<String, Object> oldProperties = this.properties;
        this.properties = properties;
        propertyChangeSupport.firePropertyChange(PROP_PROPERTIES, oldProperties, properties);
    }
    
    protected Map<String, Object> fillProperties = null;
    public static final String PROP_FILLPROPERTIES = "fillProperties";

    public Map<String, Object> getFillProperties() {
        return fillProperties;
    }

    public void _setFillProperties(Map<String, Object> fillProperties) {
        Map<String, Object> oldFillProperties = this.fillProperties;
        this.fillProperties = fillProperties;
        propertyChangeSupport.firePropertyChange(PROP_FILLPROPERTIES, oldFillProperties, fillProperties);
    }

    protected String reduceDataSetString = null;
    public static final String PROP_REDUCEDATASETSTRING = "reduceDataSetString";

    public String getReduceDataSetString() {
        return reduceDataSetString;
    }

    public void _setReduceDataSetString(String reduceDataSetString) {
        String oldReduceDataSetString = this.reduceDataSetString;
        this.reduceDataSetString = reduceDataSetString;
        propertyChangeSupport.firePropertyChange(PROP_REDUCEDATASETSTRING, oldReduceDataSetString, reduceDataSetString);
    }
    
    
    private PropertyChangeSupport propertyChangeSupport = new DebugPropertyChangeSupport(this);

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


    /**
     * load the data set from the DataSource.
     */
    private QDataSet loadDataSet() {

        ProgressMonitor mymon;

        QDataSet result = null;
        mymon = getMonitor("loading data", "loading " + _getDataSource());
        this.mon = mymon;
        try {
            result = _getDataSource().getDataSet(mymon);
            setRawProperties( _getDataSource().getMetaData( new NullProgressMonitor() ) );
            
        //embedDsDirty = true;
        } catch (InterruptedIOException ex) {
            panel.getController().getRenderer().setException(ex);
            panel.getController().getRenderer().setDataSet(null);
            panel.getController().getRenderer().setException(ex);
            panel.getController().getRenderer().setDataSet(null);
        } catch (CancelledOperationException ex) {
            panel.getController().getRenderer().setException(ex);
            panel.getController().getRenderer().setDataSet(null);
            panel.getController().getRenderer().setException(ex);
            panel.getController().getRenderer().setDataSet(null);
        } catch (Exception e) {
            setStatus("error: " + e.getMessage());
            handleException(e);
        } finally {
            // don't trust the data sources to call finished when an exception occurs.
            mymon.finished();
            if (mymon == this.mon) {
                this.mon = null;
            } else {
                System.err.println("not my mon, somebody better delete it!");
            }
        }
        return result;
    }

    /**
     * set the data source uri.
     * @param suri
     * @param mon
     */
    public void setSuri(String suri, ProgressMonitor mon) {
        panel.getDataSourceFilter().setSuri(suri);
    }

    /**
     * set the data source uri, forcing a reload if it is the same.
     * @param surl
     * @param mon
     */
    public void resetSuri(String suri, ProgressMonitor mon) {
        String old= panel.getDataSourceFilter().getSuri();
        if ( old!=null && old.equals( suri ) ) {
            panel.getDataSourceFilter().setSuri(null);
        }
        setSuri(suri,mon);
    }

    /**
     * Preconditions: 
     *   dsf.getSuri is set.
     *   Any or no datasource is set.
     * Postconditions: 
     *   A dataSource object is created 
     *   dsf._getDataSource returns the data source.
     *   A thread has been started that will load the dataset.
     * Side Effects:
     *   update is called to start the download, unless 
     *   if this is headless, then the dataset has been loaded sychronously.
     */
    public void resolveDataSource(ProgressMonitor mon) {
        pendingChanges.add(PENDING_DATA_SOURCE);

        Caching caching = getCaching();

        String surl = dsf.getSuri();
        if (surl == null) {
            setDataSource(null);
            pendingChanges.remove(PENDING_DATA_SOURCE);
        } else {
            surl = URLSplit.format(URLSplit.parse(surl));
            //surl = DataSetURL.maybeAddFile(surl);

            try {
                mon.started();
                mon.setProgressMessage("getting data source " + surl);

                if (caching != null) {
                    if (caching.satisfies(surl)) {
                        caching.resetURL(surl);
                        update(true, true);
                        return;
                    }
                }

                DataSource source = DataSetURL.getDataSource(surl);
                setDataSource(source);
                pendingChanges.remove(PENDING_DATA_SOURCE);

                mon.setProgressMessage("done getting data source");

            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                mon.finished();
            }
        }
    }

    /**
     * guess the best dimension to slice by default, based on metadata.  Currently,
     * this looks for the names lat, lon, and angle.
     */
    private void guessSliceDimension() {
           int lat = -1, lon = -1;

        Panel p = dom.getPanel();

        int[] slicePref = new int[]{1, 1, 1};
        for (int i = 0; i < getDepnames().size(); i++) {
            String n = getDepnames().get(i);
            if (n.equals("lat")) {
                slicePref[i] = 0;
                lat = i;
            }
            if (n.equals("lon")) {
                slicePref[i] = 0;
                lon = i;
            }
            if (n.equals("angle")) {
                slicePref[i] = 2;
            }
            if (n.equals("bundle")) {
                slicePref[i] = 2;
            }
        }

        int sliceIndex = 0;
        int bestSlice = 0;
        for (int i = 0; i < 3; i++) {
            if (slicePref[i] > bestSlice) {
                sliceIndex = i;
                bestSlice = slicePref[i];
            }
        }

        if (lat > -1 && lon > -1 && lat < lon) {
            p.getDataSourceFilter().setTranspose(true);
        }

        p.getDataSourceFilter().setSliceDimension(sliceIndex);

    }

    public TimeSeriesBrowseController getTimeSeriesBrowseController() {
        return timeSeriesBrowseController;
    }
    Set<String> pendingChanges = new HashSet<String>();

    public boolean isPendingChanges() {
        return pendingChanges.size() > 0;
    }

    private void handleException(Exception e) {
        model.getCanvas().getApplication().getExceptionHandler().handle(e);
    }

    private ProgressMonitor getMonitor(String label, String description) {
        DasCanvas canvas = model.getCanvas();
        DasPlot p = this.panel.getController().getPlot();
        return canvas.getApplication().getMonitorFactory().getMonitor(p, label, description);

    }

    private void setStatus(String string) {
        dom.getController().setStatus(string);
    }
}
