/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.InterruptedIOException;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import org.das2.CancelledOperationException;
import org.das2.graph.DasPlot;
import org.das2.system.RequestProcessor;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.autoplot.AutoplotUtil;
import org.virbo.autoplot.util.RunLaterListener;
import org.virbo.dataset.DDataSet;
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
import org.virbo.datasource.capability.Updating;
import org.virbo.dsutil.AutoHistogram;

/**
 *
 * @author jbf
 */
public class DataSourceController {

    Logger logger = Logger.getLogger("vap.dataSourceController");
    DataSourceFilter dsf;
    private ApplicationModel model;
    private Application dom;
    private ChangesSupport changesSupport;
    /**
     * the current load being monitored.
     */
    private ProgressMonitor mon;
    private PropertyChangeListener updateSlicePropertyChangeListener = new PropertyChangeListener() {

        public String toString() {
            return "" + dsf + " controller updateSlicePropertyChangeListener";
        }

        public void propertyChange(PropertyChangeEvent e) {
            if (dataSet != null && dataSet.rank() == 3) {
                updateFillSoon();
            }
        }
    };
    private PropertyChangeListener updateMePropertyChangeListener = new PropertyChangeListener() {

        public String toString() {
            return "" + dsf + " controller updateMePropertyChangeListener";
        }

        public void propertyChange(PropertyChangeEvent e) {
            if (dataSet != null) {
                updateFillSoon();
            }
        }
    };

    //TODO: This is the only thing listening to the dsf.uri.  TSB resets uri by setting the field directly
    // to avoid the property change event.  This should probably be refactored so other clients can
    // listen for changes in dsf.uri.  TSB must reset the URI without triggering reset ranges,etc.
    private PropertyChangeListener resetMePropertyChangeListener = new PropertyChangeListener() {

        public String toString() {
            return "" + dsf + " controller resetMePropertyChangeListener";
        }

        public void propertyChange(PropertyChangeEvent e) {
            logger.fine( "resetMe: "+e.getPropertyName()+ " "+e.getOldValue()+"->"+e.getNewValue());
            if (e.getNewValue() == null && e.getOldValue() == null) {
                return;
            } else {
                if (!dom.getController().isValueAdjusting()) {
                    resolveDataSource(false,getMonitor("resetting data source", "resetting data source"));
                } else {
                    new RunLaterListener(ChangesSupport.PROP_VALUEADJUSTING, dom.getController()) {
                        @Override
                        public void run() {
                            resolveDataSource(true,getMonitor("resetting data source", "resetting data source"));
                        }
                    };
                }
            }
        }
    };
    private TimeSeriesBrowseController timeSeriesBrowseController;
    private static final String PENDING_DATA_SOURCE = "dataSource";
    private static final String PENDING_FILL_DATASET = "fillDataSet";
    private static final String PENDING_UPDATE = "update";

    public DataSourceController(ApplicationModel model, DataSourceFilter dsf) {

        this.model = model;
        this.dom = model.getDocumentModel();
        this.changesSupport = new ChangesSupport(this.propertyChangeSupport, this);
        this.dsf = dsf;
        this.dsf.controller = this;

        dsf.addPropertyChangeListener(DataSourceFilter.PROP_SLICEDIMENSION, updateSlicePropertyChangeListener);
        dsf.addPropertyChangeListener(DataSourceFilter.PROP_SLICEINDEX, updateSlicePropertyChangeListener);
        dsf.addPropertyChangeListener(DataSourceFilter.PROP_TRANSPOSE, updateSlicePropertyChangeListener);

        dsf.addPropertyChangeListener(DataSourceFilter.PROP_FILL, updateMePropertyChangeListener);
        dsf.addPropertyChangeListener(DataSourceFilter.PROP_VALID_RANGE, updateMePropertyChangeListener);

        dsf.addPropertyChangeListener(DataSourceFilter.PROP_URI, resetMePropertyChangeListener);

    }

    /**
     * returns the biggest index that a dimension can be sliced at.  Note for non-qubes, this will be 0 when the dimension
     * is not 0.
     * 
     * @param i the dimension to slice
     * @return the number of slice indeces.
     */
    public int getMaxSliceIndex(int i) {
        if (getDataSet() == null) {
            return 0;
        }
        int sliceDimension = i;
        if (sliceDimension == 0) {
            return getDataSet().length();
        }
        int[] qube = DataSetUtil.qubeDims(getDataSet());
        if (qube == null || qube.length <= sliceDimension) {
            return 0;
        } else {
            try {
                return qube[sliceDimension];
            } catch (ArrayIndexOutOfBoundsException ex) {
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

        QDataSet ds = getDataSet();

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

    public synchronized void setDataSource(boolean valueWasAdjusting,DataSource dataSource) {

        if (timeSeriesBrowseController != null) {
            timeSeriesBrowseController.release();
        }

        DataSource oldSource = _getDataSource();

        if (dataSource == null) {
            _setCaching(null);
            _setTsb(null);
            _setTsbSuri(null);
            if ( dsf.getUri()!=null && !dsf.getUri().startsWith("vap+internal" ) ) dsf.setUri("vap+internal:");

        } else {

            _setCaching(dataSource.getCapability(Caching.class));
            _setTsb(dataSource.getCapability(TimeSeriesBrowse.class));

        }

        this.dsf.setValidRange("");
        this.dsf.setFill("");

        _setDataSource(dataSource);

        if (oldSource == null || !oldSource.equals(dataSource)) {
            if (getTsb() != null) {
                _setDataSet(null);
                List<Panel> ps = dom.getController().getPanelsFor(dsf);
                if (ps.size() > 0) {
                    timeSeriesBrowseController = new TimeSeriesBrowseController(ps.get(0));
                    timeSeriesBrowseController.setup(valueWasAdjusting);
                }

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
    public synchronized void setDataSetInternal(QDataSet ds) {

        List<String> problems = new ArrayList<String>();

        if (ds != null && !DataSetUtil.validate(ds, problems)) {
            StringBuffer message = new StringBuffer("data set is invalid:\n");
            new Exception().printStackTrace();
            for (String s : problems) {
                message.append(s + "\n");
            }
            if (dom.getController().isHeadless()) {
                throw new IllegalArgumentException(message.toString());
            } else {
                JOptionPane.showMessageDialog(model.getCanvas(), message); //TODO: View code in controller
            }

            if (ds instanceof MutablePropertyDataSet) {
                //MutablePropertyDataSet mds= (MutablePropertyDataSet)ds;
                //DataSetUtil.makeValid(mds);
                //  we would also have to modify the metadata, labels etc.
                return;
            } else {
                return;
            }
        }

        ApplicationController ac= this.dom.getController();

        if (ac.isValueAdjusting()) {
            final QDataSet fds = ds;
            new RunLaterListener( ChangesSupport.PROP_VALUEADJUSTING, ac ) {
                @Override
                public void run() {
                    setDataSetInternal(fds);
                }
            };
        } else {
            _setDataSet(ds);
            setRawProperties(null);  // TODO: what if this is from a datasource?  We might want to avoid null in raw properties tree view.

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
            _setHistogram(new AutoHistogram().doit(ds, null));
            //doFillValidRange();  the QDataSet returned 
            updateFill();
        }
    }
    
    DataSourceFilter[] parentSources;

    protected synchronized DataSourceFilter[] getParentSources() {
        if ( parentSources==null ) {
            return new DataSourceFilter[0];
        } else {
            DataSourceFilter[] parentSources1= new DataSourceFilter[parentSources.length];
            System.arraycopy(parentSources, 0, parentSources1, 0, parentSources.length );
            return parentSources1;
        }
    }

    private synchronized void resolveParents() {
        if ( dsf.getUri()==null ) return; //TODO: remove
        URLSplit split= URLSplit.parse(dsf.getUri());
        String[] ss = split.surl.split(",", -2);
        for (int i = 0; i < ss.length; i++) {
            DataSourceFilter dsf = (DataSourceFilter) DomUtil.getElementById(dom, ss[i]);
            if ( dsf!=null ) {
                dsf.getController().addPropertyChangeListener(DataSourceController.PROP_FILLDATASET,parentListener);
                parentSources[i] = dsf;
            }else {
                parentSources[i] = null;
            }
        }
        System.err.println( Arrays.asList(parentSources));
    }
    
    /**
     * check to see if all the parent sources have updated and have
     * datasets that are compatible, so a new dataset can be created.
     */
    private synchronized boolean checkParents() {

        QDataSet x;
        QDataSet y = null;
        QDataSet z = null;
        x = parentSources[0].getController().getFillDataSet();
        if (parentSources.length > 1) {
            y = parentSources[1].getController().getFillDataSet();
        }
        if (parentSources.length > 2) {
            z = parentSources[2].getController().getFillDataSet();
        }
        if (parentSources.length == 2) {
            if (x == null || y == null) {
                return false;
            }
            DDataSet yds = DDataSet.copy(y);
            if (x != null) {
                yds.putProperty(QDataSet.DEPEND_0, x);
            }
            if ( DataSetUtil.validate(yds, null ) ) {
                setDataSetInternal(yds);
            }
        } else if (parentSources.length == 3) {
            if (x == null || y == null || z == null) {
                return false;
            }
            if (z.rank() == 1) {
                DDataSet yds = DDataSet.copy(y);
                yds.putProperty(QDataSet.DEPEND_0, x);
                yds.putProperty(QDataSet.PLANE_0, z);
                if ( DataSetUtil.validate(yds, null ) ) {
                    setDataSetInternal(yds);
                }
            } else {
                DDataSet zds = DDataSet.copy(z);
                if (x != null) {
                    zds.putProperty(QDataSet.DEPEND_0, x);
                }
                if (y != null) {
                    zds.putProperty(QDataSet.DEPEND_1, y);
                }
                if ( DataSetUtil.validate(zds, null ) ) {
                    setDataSetInternal(zds);
                }
            }
        }
        return true;
    }
    
    PropertyChangeListener parentListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            checkParents();
        }
    };

    PropertyChangeListener dsfListener= new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                resolveParents();
            }
        } ;

    private synchronized void doInternal(String path) {
        if ( parentSources!=null ) {
            for ( int i=0; i<parentSources.length; i++ ) {
                if ( parentSources[i]!=null ) parentSources[i].getController().removePropertyChangeListener(DataSourceController.PROP_FILLDATASET,parentListener);
            }
        }
        if ( path.trim().length()==0 ) return;
        String[] ss = path.split(",", -2);
        parentSources = new DataSourceFilter[ss.length];
        resolveParents();
        checkParents();
        dom.addPropertyChangeListener( Application.PROP_DATASOURCEFILTERS, dsfListener );
    }

    protected void unbind() {
        dom.removePropertyChangeListener( Application.PROP_DATASOURCEFILTERS, dsfListener );
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
        if (_getDataSource() != null) {
            properties = AutoplotUtil.mergeProperties(_getDataSource().getProperties(), properties);
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
        } else {
            dsf.setValidRange("");
        }
    }

    /**
     * call updateFill in new thread
     */
    private void updateFillSoon() {
        changesSupport.performingChange(this, PENDING_FILL_DATASET);
        RequestProcessor.invokeLater(new Runnable() {

            public void run() {
                updateFill();
                changesSupport.changePerformed(this, PENDING_FILL_DATASET);
            }
        });
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
        changesSupport.performingChange(this, PENDING_FILL_DATASET);

        logger.fine("enter updateFill");

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
            this.fillDataSet = null;
        }
        _setFillDataSet(fillDs);
        changesSupport.changePerformed(this, PENDING_FILL_DATASET);
    }

    /**
     * do update on this thread, ensuring that only one data load is occuring at a
     * time.  Note if a dataSource doesn't check mon.isCancelled(), then processing
     * will block until the old load is done.
     */
    private synchronized void updateImmediately() {
        /*** here is the data load ***/
        setStatus("busy: loading dataset");

        try {
            if (_getDataSource() != null) {
                QDataSet dataset = loadDataSet();
                setStatus("done loading dataset");
                setDataSetInternal(dataset);
            } else {
                setDataSetInternal(null);
            }

            if (getTsb() != null) {
                String oldsurl = dsf.getUri();
                String newsurl = getTsb().getURL().toString();
                URLSplit split = URLSplit.parse(newsurl);
                if (oldsurl != null) {
                    URLSplit oldSplit = URLSplit.parse(oldsurl);
                    split.vapScheme = oldSplit.vapScheme;
                }
                newsurl = URLSplit.format(split);
                dsf.uri = newsurl; // don't fire off event. TODO: should we?
            }
            setStatus("ready");
        } catch (RuntimeException ex) {
            setStatus("error: " + ex.toString());
            throw ex;
        }

    }
    private Updating updating;
    private PropertyChangeListener updatesListener = new PropertyChangeListener() {

        public void propertyChange(PropertyChangeEvent evt) {
            QDataSet ds = (QDataSet) evt.getNewValue();
            if (ds != null) {
                setDataSetInternal(ds);
            } else {
                update(false, false);
            }
        }
    };

    /**
     * update the model and view using the new DataSource to create a new dataset,
     * then inspecting the dataset to decide on axis settings.
     * @param autorange if false, then no autoranging is done, just the fill part.
     */
    public synchronized void update(final boolean autorange, final boolean interpretMeta) {
        changesSupport.performingChange(this, PENDING_UPDATE);

        _setDataSet(null);

        Runnable run = new Runnable() {

            public void run() {
                synchronized (DataSourceController.this) {
                    updateImmediately();
                    if (dataSource != null) {
                        if (updating != null) {
                            updating.removePropertyChangeListener(updatesListener);
                        }
                        updating = dataSource.getCapability(Updating.class);
                        if (updating != null) {
                            updating.addPropertyChangeListener(updatesListener);
                        }
                    }
                }
                changesSupport.changePerformed(this, PENDING_UPDATE);
            }
        };

        if (_getDataSource() != null && _getDataSource().asynchronousLoad() && !dom.getController().isHeadless()) {
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
    /**
     * when the dataset fails to load, then the exception thrown is here.
     */
    protected Exception exception = null;
    public static final String PROP_EXCEPTION = "exception";

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        Exception oldException = this.exception;
        this.exception = exception;
        propertyChangeSupport.firePropertyChange(PROP_EXCEPTION, oldException, exception);
    }
    protected QDataSet histogram = null;
    public static final String PROP_HISTOGRAM = "histogram";

    public QDataSet getHistogram() {
        return histogram;
    }

    public void _setHistogram(QDataSet histogram) {
        QDataSet oldHistogram = this.histogram;
        this.histogram = histogram;
        propertyChangeSupport.firePropertyChange(PROP_HISTOGRAM, oldHistogram, histogram);
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

    /**
     * load the data set from the DataSource.
     */
    private synchronized QDataSet loadDataSet() {

        ProgressMonitor mymon;

        QDataSet result = null;
        mymon = getMonitor("loading data", "loading " + _getDataSource());
        this.mon = mymon;
        try {
            result = _getDataSource().getDataSet(mymon);
            setRawProperties(_getDataSource().getMetaData(new NullProgressMonitor()));

        //embedDsDirty = true;
        } catch (InterruptedIOException ex) {
            setException(ex);
            _setDataSet(null);
        } catch (CancelledOperationException ex) {
            setException(ex);
            _setDataSet(null);
        } catch (Exception e) {
            setException(e);
            _setDataSet(null);
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
     * @param uri
     * @param mon
     */
    public synchronized void setSuri(String suri, ProgressMonitor mon) {
        dsf.setUri(suri);
    }

    /**
     * set the data source uri, forcing a reload if it is the same.
     * @param surl
     * @param mon
     */
    public synchronized void resetSuri(String suri, ProgressMonitor mon) {
        String old = dsf.getUri();
        if (old != null && old.equals(suri)) {
            dsf.setUri(null);
        }
        setSuri(suri, mon);
    }

    /**
     * Preconditions: 
     *   dsf.getUri is set.
     *   Any or no datasource is set.
     * Postconditions: 
     *   A dataSource object is created 
     *   dsf._getDataSource returns the data source.
     *   A thread has been started that will load the dataset.
     * Side Effects:
     *   update is called to start the download, unless 
     *   if this is headless, then the dataset has been loaded sychronously.
     */
    private void resolveDataSource( boolean valueWasAdjusting, ProgressMonitor mon ) {
        changesSupport.performingChange(this, PENDING_DATA_SOURCE);

        Caching caching = getCaching();

        String surl = dsf.getUri();
        if (surl == null) {
            setDataSource(valueWasAdjusting,null);
            changesSupport.changePerformed(this, PENDING_DATA_SOURCE);
        } else {
            URLSplit split = URLSplit.parse(surl);
            surl = URLSplit.format(split);

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

                if (split.vapScheme.equals("vap+internal")) {
                    URI uri;
                    doInternal(split.path);
                    setDataSource(valueWasAdjusting,null);
                } else {
                    DataSource source = DataSetURL.getDataSource(surl);
                    setDataSource(valueWasAdjusting,source);
                }
                changesSupport.changePerformed(this, PENDING_DATA_SOURCE);

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
            dsf.setTranspose(true);
        }

        dsf.setSliceDimension(sliceIndex);

    }

    public TimeSeriesBrowseController getTimeSeriesBrowseController() {
        return timeSeriesBrowseController;
    }

    public boolean isPendingChanges() {
        TimeSeriesBrowseController tsbc= timeSeriesBrowseController;
        if (tsbc != null && tsbc.isPendingChanges()) {
            return true;
        }
        return changesSupport.isPendingChanges();
    }

    private void handleException(Exception e) {
        model.getCanvas().getApplication().getExceptionHandler().handle(e);
    }

    /**
     * return the first panel that is using this data source.
     * @return null or a panel.
     */
    private Panel getPanel() {
        List<Panel> panels = dom.getController().getPanelsFor(dsf);
        if (panels.size() == 0) {
            return null;
        } else {
            return panels.get(0);
        }

    }

    private ProgressMonitor getMonitor(String label, String description) {
        Panel panel = getPanel();
        DasPlot p = null;
        if (panel != null) {
            Plot plot = dom.getController().getPlotFor(panel);
            if (plot != null) {
                p = plot.getController().getDasPlot();
            }
        }
        if (p != null) {
            return dom.getController().getMonitorFactory().getMonitor(p, label, description);
        } else {
            return dom.getController().getMonitorFactory().getMonitor(label, description);
        }

    }

    private void setStatus(String string) {
        dom.getController().setStatus(string);
    }
}
