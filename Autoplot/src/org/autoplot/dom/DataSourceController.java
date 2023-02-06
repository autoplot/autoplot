
package org.autoplot.dom;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import org.das2.CancelledOperationException;
import org.das2.dataset.NoDataInIntervalException;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.graph.DasPlot;
import org.das2.system.RequestProcessor;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.AlertNullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.ApplicationModel;
import org.autoplot.AutoplotUtil;
import org.autoplot.util.RunLaterListener;
import org.das2.qds.ArrayDataSet;
import org.das2.qds.DataSetOps;
import org.das2.qds.DataSetUtil;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.RankZeroDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.HtmlResponseIOException;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.capability.Caching;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.autoplot.datasource.capability.Updating;
import org.das2.components.DasProgressPanel;
import org.das2.qds.DataSetAnnotations;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.AutoHistogram;

/**
 * Controller node manages a DataSourceFilter node.
 *
 * @author jbf
 */
public class DataSourceController extends DomNodeController {

    private static final Logger logger = org.das2.util.LoggerManager.getLogger("autoplot.dom.dsc");

    DataSourceFilter dsf;
    private ApplicationModel model;
    private Application dom;

    private final Object internalLock = new Object();
    private final Object uriLock = new Object();
    private final Object dscLock = new Object(); // just to figure out synchronization

    /**
     * the current load being monitored.
     */
    private ProgressMonitor mon;
    private PropertyChangeListener updateSlicePropertyChangeListener = new PropertyChangeListener() {
        @Override
        public String toString() {
            return "" + dsf + " controller updateSlicePropertyChangeListener";
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt);  
            if (dataSet != null) {
                updateFillSoon(-1);
            }
        }
    };
    private PropertyChangeListener updateMePropertyChangeListener = new PropertyChangeListener() {
        @Override
        public String toString() {
            return "" + dsf + " controller updateMePropertyChangeListener";
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt);  
            if (dataSet != null) {
                logger.fine("change in fill or valid range ->updateFillSoon()");
                updateFillSoon(-1);
            }
        }
    };

    //TODO: This is the only thing listening to the dsf.uri.  
    private PropertyChangeListener resetMePropertyChangeListener = new PropertyChangeListener() {
        @Override
        public String toString() {
            return "" + dsf + " controller resetMePropertyChangeListener";
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt);  
            logger.log(Level.FINE, "resetMe: {0} {1}->{2}", new Object[]{evt.getPropertyName(), evt.getOldValue(), evt.getNewValue()});
            
            if (evt.getNewValue() == null && evt.getOldValue() == null) {
                // do nothing
            } else {
                List<Object> whoIsChanging = changesSupport.whoIsChanging(PENDING_SET_DATA_SOURCE);
                if (whoIsChanging.size() > 0) {
                    logger.log(Level.WARNING, "!!! someone is changing: {0} !!!  ignoring event.", whoIsChanging); // we probably need to do something with this.
                    logger.log(Level.WARNING, " !! {0}", evt.getPropertyName());
                    logger.log(Level.WARNING, " !! {0}", evt.getNewValue());
                    logger.log(Level.WARNING, " !! {0}", evt.getOldValue());
                    return;
                }
                //System.err.println("*** register "+resetMePropertyChangeListener);
                DataSourceController.this.changesSupport.registerPendingChange(resetMePropertyChangeListener, PENDING_RESOLVE_DATA_SOURCE);
                setUriNeedsResolution(true);
                if (!dom.controller.isValueAdjusting()) {
                    try {
                        //System.err.println("*** unregister "+resetMePropertyChangeListener);
                        DataSourceController.this.changesSupport.performingChange(resetMePropertyChangeListener, PENDING_RESOLVE_DATA_SOURCE);
                        dsf.setFilters(""); // reset filters
                        resolveDataSource(false, getMonitor("resetting data source", "resetting data source"));
                    } finally {
                        DataSourceController.this.changesSupport.changePerformed(resetMePropertyChangeListener, PENDING_RESOLVE_DATA_SOURCE);
                    }
                } else {
                    //System.err.println("*** runlater "+resetMePropertyChangeListener);
                    new RunLaterListener(ChangesSupport.PROP_VALUEADJUSTING, dom.controller, true) {
                        @Override
                        public void run() {
                            //System.err.println("*** unregister "+resetMePropertyChangeListener);
                            try {
                                DataSourceController.this.changesSupport.performingChange(resetMePropertyChangeListener, PENDING_RESOLVE_DATA_SOURCE);
                                if (uriNeedsResolution) {
                                    resolveDataSource(true, getMonitor("resetting data source", "resetting data source"));
                                }
                            } finally {
                                DataSourceController.this.changesSupport.changePerformed(resetMePropertyChangeListener, PENDING_RESOLVE_DATA_SOURCE);
                            }
                        }
                    };
                }
            }
        }
    };

    private TimeSeriesBrowseController timeSeriesBrowseController;

    /**
     * true if we have vap+internal and we have already checked for parent TSBs.
     */
    private boolean haveCheckedInternalTsb = false;

    private static final String PENDING_DATA_SOURCE = "dataSource";
    private static final String PENDING_RESOLVE_DATA_SOURCE = "resolveDataSource";
    private static final String PENDING_SET_DATA_SOURCE = "setDataSource"; //we are setting the datasource, so don't try to resolve, etc.
    private static final String PENDING_FILL_DATASET = "fillDataSet";
    private static final String PENDING_UPDATE = "update";

    public DataSourceController(ApplicationModel model, DataSourceFilter dsf) {
        super(dsf);

        this.model = model;
        this.dom = model.getDocumentModel();
        //this.changesSupport = new ChangesSupport(this.propertyChangeSupport, this);
        this.dsf = dsf;

        //dsf.addPropertyChangeListener(DataSourceFilter.PROP_SLICEDIMENSION, updateSlicePropertyChangeListener);
        //dsf.addPropertyChangeListener(DataSourceFilter.PROP_SLICEINDEX, updateSlicePropertyChangeListener);
        //dsf.addPropertyChangeListener(DataSourceFilter.PROP_TRANSPOSE, updateSlicePropertyChangeListener);
        dsf.addPropertyChangeListener(DataSourceFilter.PROP_FILTERS, updateSlicePropertyChangeListener);

        dsf.addPropertyChangeListener(DataSourceFilter.PROP_FILL, updateMePropertyChangeListener);
        dsf.addPropertyChangeListener(DataSourceFilter.PROP_VALID_RANGE, updateMePropertyChangeListener);

        dsf.addPropertyChangeListener(DataSourceFilter.PROP_URI, resetMePropertyChangeListener);

        dsf.addPropertyChangeListener(Plot.PROP_ID, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                LoggerManager.logPropertyChangeEvent(evt);  
                if (dom.controller.isValueAdjusting()) {
                    return;
                }
                ChangesSupport.DomLock lock = dom.controller.mutatorLock();
                lock.lock("Changing dsf id");
                try {
                    for (BindingModel b : dom.getBindings()) {
                        if (b.getSrcId().equals(evt.getOldValue())) {
                            b.srcId = (String) evt.getNewValue();
                        }
                        if (b.getDstId().equals(evt.getOldValue())) {
                            b.dstId = (String) evt.getNewValue();
                        }
                    }
                    for (PlotElement pe : dom.plotElements) {
                        if (pe.getDataSourceFilterId().equals(evt.getOldValue())) {
                            pe.setDataSourceFilterId((String) evt.getNewValue());
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }
        });

    }

    /**
     * returns the biggest index that a dimension can be sliced at. Note for
     * non-qubes, this will be 0 when the dimension is not 0.
     *
     * @param i the dimension to slice
     * @return the number of slice indeces.
     * @deprecated this is leftover from an ancient version of the code.
     */
    public int getMaxSliceIndex(int i) {
        if (getDataSet() == null) {
            return 0;
        }
        int sliceDimension = i;
        if (sliceDimension == 0) {
            if (getDataSet().rank() == 0) {
                return 0; // it doesn't really matter, we just need to return something.
            } else {
                return getDataSet().length();
            }
        }
        if (sliceDimension == -1) { // rank 0
            return 0;
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
     * calculate the dimensions for the dataset. Rank 3 datasets must be
     * reduced, and this sets the names so that slicing can be done.
     *
     * preconditions: dsf.getDataSet() is non-null postconditions:
     * dsf.getDepnames returns the names of the dimensions.
     * dsf.getSliceDimension returns the preferred dimension to slice.
     */
    private void doDimensionNames() {

        QDataSet ds = getDataSet();
        if (ds == null) {
            return; // while debugging Bill's bug, I hit this condition... 
        }
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

        logger.log(Level.FINE, "dep names: {0}", Arrays.asList(depNames));


//        if ( isResetDimensions() ) {
//
//            if (ds.rank() > 2 ) {
//                guessSliceDimension();
//            }
//        }
        setResetDimensions(false);

    }

    /**
     * We isolate the flakiness by introducing a method that encapsulates the
     * impossible logic of know if a plot element will support timeSeriesBrowse.
     * If it doesn't, we would just want to load the data set and be done with
     * it. If it can then we want a tsb.
     *
     * The problem is that until the plot element does its slicing, we don't
     * really know if it will or not. For now we kludge up logic based on the
     * component string or plotElements where the component string won't be set
     * automatically.
     *
     * @param plotElement
     * @return
     */
    private boolean doesPlotElementSupportTsb(PlotElement plotElement) {
        Plot plot = plotElement.getController().getApplication().getController().getPlotFor(plotElement);
        if (plot == null) {
            return false;
        }
        if (UnitsUtil.isTimeLocation(plot.getXaxis().getRange().getUnits())
                || UnitsUtil.isTimeLocation(plot.getContext().getUnits())) {
            return true;
        }
        //return false;
        return plotElement.isAutoComponent() || (!(plotElement.getComponent().contains("|slice0") || plotElement.getComponent().contains("|collapse0")));
    }

    /**
     * This might be considered the heart of the DataSourceController. This is
     * where TimeSeriesBrowse is set up as well as Caching.
     *
     * This might also be a good spot to make sure we are not on the event
     * thread, and this is being studied.
     *
     * @param valueWasAdjusting true if the app was loading a vap, or locked because of changes.
     * @param dataSource
     */
    public void resetDataSource(boolean valueWasAdjusting, DataSource dataSource) {

        if (EventQueue.isDispatchThread()) {
            // study where this is called from.
            logger.fine("resetDataSource on event thread");
        }

        synchronized (dscLock) {
            if (dataSource == null) {
                setDataSetNeedsLoading(false);
            } else {
                setDataSetNeedsLoading(true);
            }

            if (timeSeriesBrowseController != null) {
                timeSeriesBrowseController.release();
                this.timeSeriesBrowseController = null;
            }

            DataSource oldSource = getDataSource();

            if (haveCheckedInternalTsb) {
                haveCheckedInternalTsb = false;
            }

            if (dataSource == null) {
                setCaching(null);
                setTsb(null);
                setTsbSuri(null);
                if (dsf.getUri().length() > 0 && !dsf.getUri().startsWith("vap+internal")) {
                    dsf.setUri("vap+internal:"); //TODO: when is this supposed to happen?  Test033 is hitting here.
                }

            } else {
                changesSupport.performingChange(this, PENDING_SET_DATA_SOURCE);
                setCaching(dataSource.getCapability(Caching.class));
                PlotElement pe = getPlotElement(false);
                if (pe != null && this.doesPlotElementSupportTsb(pe)) {  //TODO: less flakey
                    setTsb(dataSource.getCapability(TimeSeriesBrowse.class));
                } else {
                    //it might have an internal source listening to it.
                    if (pe == null) {
                        setTsb(dataSource.getCapability(TimeSeriesBrowse.class));
                    } else {
                        List<PlotElement> pele = dom.controller.getPlotElementsFor(dsf);
                        pe = null;
                        for (PlotElement pele1 : pele) {
                            if (doesPlotElementSupportTsb(pele1)) {
                                pe = pele1;
                            }
                        }
                        if (pe != null) {
                            setTsb(dataSource.getCapability(TimeSeriesBrowse.class));
                        } else {
                            setTsb(null);
                        }
                    }
                }
                if (dsf.getUri().length() > 0) {
                    dsf.setUri(dataSource.getURI());
                    setUriNeedsResolution(false);
                }
                changesSupport.changePerformed(this, PENDING_SET_DATA_SOURCE);
            }

            if (valueWasAdjusting) {
                this.dataSource = dataSource;
            } else {
                this.dsf.setValidRange("");
                this.dsf.setFill("");
                setDataSource(dataSource);
                setResetDimensions(true);
            }

            if (oldSource == null || !oldSource.equals(dataSource)) {
                List<PlotElement> ps = dom.controller.getPlotElementsFor(dsf);
                if (getTsb() != null && !ps.isEmpty()) {
                    setDataSet(null);
                    if (ps.size() > 0) {
                        timeSeriesBrowseController = new TimeSeriesBrowseController(this, ps.get(0));
                        Plot p = dom.controller.getFirstPlotFor(dsf);
                        List<PlotElement> pes= dom.controller.getPlotElementsFor(p);
                        if ( pes.size()>1 ) {
                            boolean context= false;
                            for ( PlotElement pe: pes ) {
                                DataSourceFilter dsf1= dom.controller.getDataSourceFilterFor(pe);
                                if ( dsf1!=null && dsf1!=this.dsf ) {
                                    TimeSeriesBrowseController tsbc= dsf1.getController().getTimeSeriesBrowseController();
                                    if ( tsbc!=null ) {
                                        if ( !tsbc.isListeningToAxis() ) context= true;
                                    }
                                }
                            }
                            if ( context ) {
                                timeSeriesBrowseController.setupGen(p,Plot.PROP_CONTEXT);
                                timeSeriesBrowseController.updateTsb(false);
                            } else {
                                timeSeriesBrowseController.setup(valueWasAdjusting);
                            }
                        } else {
                            timeSeriesBrowseController.setup(valueWasAdjusting);
                            logger.fine("connect to timerange (bug2136)");
                            int bindingCount= dom.controller.findBindings( dom, Application.PROP_TIMERANGE ).size();
                            if ( !valueWasAdjusting && ( bindingCount<2 || dom.timeRange.intersects( p.xaxis.getRange() ) ) ) {
                                if ( UnitsUtil.isTimeLocation( dom.timeRange.getUnits()) && UnitsUtil.isTimeLocation(p.xaxis.range.getUnits()) ) {
                                    if ( !dom.timeRange.intersects( p.xaxis.getRange() ) ) {
                                        dom.setTimeRange( p.xaxis.getRange() );
                                    }
                                    dom.controller.bind( dom, Application.PROP_TIMERANGE, p.xaxis, Axis.PROP_RANGE );
                                    dom.controller.unbind(  dom, Application.PROP_TIMERANGE, p, Plot.PROP_CONTEXT );
                                }
                            }
                        }
                    }
                } else if (getTsb() != null && ps.isEmpty()) {
                    timeSeriesBrowseController = new TimeSeriesBrowseController(this, null);

                    DomNode node1;
                    String propertyName;

                    Plot p = dom.controller.getFirstPlotFor(dsf);
                    if (p == null) {
                        logger.fine("unable to identify a plot for the dsf, binding tsb to app.timerange");
                        node1 = dom;
                        propertyName = Application.PROP_TIMERANGE;
                    } else {
                        logger.log(Level.FINE, "binding tsb to plot.context of {0}", p.getId());
                        node1 = p;
                        propertyName = Plot.PROP_CONTEXT;
                    }

                    if (!UnitsUtil.isTimeLocation(this.dom.getTimeRange().getUnits())) {
                        List<BindingModel> bms = this.dom.getController().findBindings(this.dom, Application.PROP_TIMERANGE, null, null);
                        if ( bms.isEmpty() ) {
                            logger.log(Level.FINE, "claiming dom timerange for TSB: {0}", this.dsf.getUri());
                            if (p != null) {
                                p.setContext(getTsb().getTimeRange());
                            }
                            this.dom.setTimeRange(getTsb().getTimeRange());
                            logger.log(Level.FINE, "about to setup Gen for {0}", this);
                            timeSeriesBrowseController.setupGen(node1, propertyName);
                            if (node1 != dom) {
                                dom.controller.bind(dom, Application.PROP_TIMERANGE, p, Plot.PROP_CONTEXT);
                            }
                            update(true);
                        } else {
                            logger.fine("unable to use timerange as guide");
                            if (p != null) {
                                p.setContext(getTsb().getTimeRange());
                            }
                            timeSeriesBrowseController.setupGen(node1, propertyName);
                            update(true);
                        }
                    } else {
                        logger.log(Level.FINE, "using plot context for TSB: {0}", this.dsf.getUri());
                        timeSeriesBrowseController.setupGen(node1, propertyName);
                        if (node1 != dom) {
                            if (p != null) {
                                dom.controller.bind(dom, Application.PROP_TIMERANGE, p, Plot.PROP_CONTEXT);
                                BindingModel bm = dom.controller.findBinding(dom, Application.PROP_TIMERANGE, p.getXaxis(), Axis.PROP_RANGE);
                                //TODO: verify this! https://sourceforge.net/tracker/index.php?func=detail&aid=3516161&group_id=199733&atid=970682
                                if (bm != null) {
                                    dom.controller.deleteBinding(bm);
                                }
                            }
                        }
                        update(true);
                    }

                } else {
                    update(true);
                }
            }
        }
    }

    /**
     * set the dataset for the DataSourceFilter.
     * @param ds the dataset
     */
    public void setDataSetInternal(QDataSet ds) {
        setDataSetInternal(ds, null, this.dom.controller.isValueAdjusting());
    }

    /**
     * return true if the dataset is rank 1 or greater, and has timetags for the
     * xtagsDataSet. This will often be DEPEND_0, but for JoinDataSets which are
     * like an array of datasets, each dataset would have DEPEND_0.
     *
     * @param ds any dataset
     * @return true if the dataset is rank 1 or greater, and has timetags for
     * the xtagsDataSet.
     */
    public static boolean isTimeSeries(QDataSet ds) {
        if (ds.rank() == 0) {
            return false;
        }
        QDataSet dep0 = SemanticOps.xtagsDataSet(ds);
        if (dep0 != null) {
            Units u = SemanticOps.getUnits(dep0);
            if (UnitsUtil.isTimeLocation(u)) {
                return true;
            }
        }
        return false;
    }

    /**
     * set the new dataset, do autoranging and autolabelling.
     *
     * preconditions: <ul>
     * <li> autoplot is displaying any dataset.
     * <li> A new DataSource has been set, but the dataset is generally not from
     * the DataSource.
     * </ul>
     * postconditions: <ul>
     * <li> the dataset is set
     * <li> labels are set, axes are set.
     * <li> Labels reset might have triggered a timer that will redo layout.
     * <li> slice dimensions are set for dataset.
     * </ul>
     *
     * @param ds the dataset.
     * @param rawProperties additional properties provided by the data source.
     * @param immediately if false, then this is done after the application is
     * done adjusting.
     */
    public void setDataSetInternal(QDataSet ds, Map<String, Object> rawProperties, boolean immediately) {

        List<String> problems = new ArrayList<>();

        if (ds != null && !DataSetUtil.validate(ds, problems)) {
            String uri;
            DataSource dss = getDataSource();
            if (dss == null) {
                uri = "vap+internal:";
            } else {
                uri = dss.getURI();
            }
            if (this.tsb != null) {
                uri = this.tsbSuri;
            }
            if ( uri==null ){
                uri= "<null>";
            }
            
            if ( uri.length() > 80) {
                int n = uri.length();
                uri = uri.substring(0, 48) + " ... " + uri.substring(n - 30, n);
            }
            StringBuilder message = new StringBuilder("When loading " + uri + "\ndataset is invalid:\n");
            logger.log(Level.SEVERE, "dataset is invalid", new Exception("dataset is invalid"));
            for (String s : problems) {
                message.append(s).append("\n");
            }
            if (dom.controller.isHeadless()) {
                throw new IllegalArgumentException(message.toString());
            } else {
                this.model.showMessage(message.toString(), "Data Set is Invalid", JOptionPane.WARNING_MESSAGE);
            }

            return;

        }

        if (this.getTimeSeriesBrowseController() != null && ds != null && !isTimeSeries(ds) && this.getTimeSeriesBrowseController().isListeningToAxis()) {
            // the dataset we get back isn't part of a time series.  So we should connect the TSB
            // to the application TimeRange property.
            this.timeSeriesBrowseController.release();
            Plot plot= this.getTimeSeriesBrowseController().getPlot();
            Axis xaxis = plot.getXaxis();
            dom.getController().unbind(dom, Application.PROP_TIMERANGE, xaxis, Axis.PROP_RANGE);
            dom.setTimeRange(this.tsb.getTimeRange());//TODO: think about if this is really correct
            this.timeSeriesBrowseController.setupGen( plot, Plot.PROP_CONTEXT );
        }

        ApplicationController ac = this.dom.controller;

        if (!immediately && ac.isValueAdjusting()) {
            final QDataSet fds = ds;
            new RunLaterListener(ChangesSupport.PROP_VALUEADJUSTING, ac, false) {
                @Override
                public void run() {
                    setDataSetInternal(fds);
                }
            };
        } else {
            setDataSet(ds);
            setRawProperties(rawProperties);

            setDataSetNeedsLoading(false);

            if (ds == null) {
                setDataSet(null);
                setProperties(null);
                setFillProperties(null);
                setFillDataSet(null);
                return;
            }

            extractProperties(ds);

            doDimensionNames();

            long datasetSize= DataSetUtil.totalLengthAsLong(ds);
            if ( ds.rank()<=QDataSet.MAX_RANK && datasetSize < LIMIT_STATS_COUNT && UnitsUtil.isIntervalOrRatioMeasurement(SemanticOps.getUnits(ds)) ) {
                setStatus("busy: do statistics on the data...");
                try {
                    if ( datasetSize>0 && datasetSize<QDataSet.LIMIT_HUGE_DATASET ) {
                        logger.fine("do statistics on the data");
                        long t0= System.currentTimeMillis();
                        setHistogram(new AutoHistogram().doit(ds, null));
                        logger.log(Level.FINE, "done with statistics on the data ({0}ms)", System.currentTimeMillis()-t0);
                    }
                } catch ( RuntimeException ex ) {
                    logger.warning("runtime error during histogram usually means invalid data in data set.");
                    setHistogram(null);
                }
            } else {
                if ( datasetSize < ( LIMIT_STATS_COUNT * 4 ) ) {
                    Runnable run= new Runnable() {
                        public void run() {
                            long t0= System.currentTimeMillis();
                            setHistogram(new AutoHistogram().doit(ds, null));
                            logger.log(Level.FINE, "done with statistics on the data ({0}ms)", System.currentTimeMillis()-t0);                            
                        }
                    };
                    setHistogram(null);
                    logger.fine("do statistics on the data in the background");
                    new Thread(run).start();
                } else {
                    logger.fine("skipping stats because there is too much data");
                    setHistogram(null);
                }
            }

            setStatus("busy: apply fill");

            //doFillValidRange();  the QDataSet returned
            if ( datasetSize<QDataSet.LIMIT_HUGE_DATASET ) {
                updateFill();
            } else {
                logger.warning("dataset is too big to perform stats.  See QDataSet.LIMIT_HUGE_DATASET.");
                setFillDataSet(ds);
            }

            setStatus("done, apply fill");

            List<PlotElement> pele = dom.controller.getPlotElementsFor(dsf);
            if (pele.isEmpty()) {
                boolean parentUses= false;
                for ( DataSourceFilter dsf1: dom.dataSourceFilters ) {
                    if ( Arrays.asList( dsf1.getController().getParentSources() ).contains(dsf) ) {
                        parentUses= true;
                        break;
                    }
                }
                if ( !parentUses ) {
                    setStatus("warning: done loading data but no plot elements are listening");
                }
            }

        }
    }
    
    /**
     * this is the maximum number of points which we will perform stats on.
     */
    protected static final int LIMIT_STATS_COUNT = 150_000_000;

    DataSourceFilter[] parentSources;
    
    /**
     * return the parent sources of data, which may contain null if the
     * reference is not found.
     *
     * @return
     */
    protected DataSourceFilter[] getParentSources() {
        if (parentSources == null) {
            return new DataSourceFilter[0];
        } else {
            DataSourceFilter[] parentSources1;
            synchronized (dscLock) {
                parentSources1 = new DataSourceFilter[parentSources.length];
                System.arraycopy(parentSources, 0, parentSources1, 0, parentSources.length);
            }
            return parentSources1;
        }
    }
    
    /**
     * return a DSF for which this is a parent.
     * @return null or a child source.
     */
    protected DataSourceFilter getChildSource() {
        for ( DataSourceFilter dsf1: dom.getDataSourceFilters() ) {
            for ( DataSourceFilter dsf2: dsf1.controller.getParentSources() ) {
                if ( dsf2==this.dsf ) {
                    return dsf1;
                }
            }
        }
        return null;
    }

    /**
     * removes the parentSources link, and listeners to the parents. The parents
     * are left in the DOM and will be removed later.
     */
    private void clearParentSources() {
        synchronized (dscLock) {
            if (this.parentSources != null) {
                for (DataSourceFilter parentDsf : parentSources) {
                    if (parentDsf != null) {
                        parentDsf.controller.removePropertyChangeListener(DataSourceController.PROP_FILLDATASET, parentListener);
                    }
                }
            }
            this.parentSources = null;
        }
    }

    /**
     * if the internal dataset points to DSFs with TimeSeriesBrowse, then add
     * our own TSB.
     */
    private void maybeAddInternalTimeSeriesBrowse() {

        synchronized (dscLock) {
            if (this.haveCheckedInternalTsb) {
                return;
            }
            String uri = dsf.getUri();
            if (uri == null) {
                return; // when does this happen?  reset?
            }
            URISplit split = URISplit.parse(uri);

            String[] ss = split.surl.split(",", -2);
            this.tsb = null; //TODO: who is listening?

            InternalTimeSeriesBrowse intTsb = null;
            for (int i = 0; i < ss.length; i++) {
                DataSourceFilter parentDsf = (DataSourceFilter) DomUtil.getElementById(dom, ss[i]);
                if (parentDsf != null) {
                    parentDsf.controller.addPropertyChangeListener(DataSourceController.PROP_FILLDATASET, parentListener);
                    parentSources[i] = parentDsf;
                    TimeSeriesBrowse parentTsb = parentDsf.controller.getTsb();
                    if (parentTsb != null) {
                        // TODO: parents haven't been resolved yet!
                        if (intTsb == null) {
                            intTsb = new InternalTimeSeriesBrowse(DataSourceController.this.dsf.getUri());
                        }
                        logger.log(Level.FINE, "adding to internal tsb: {0}", parentTsb);
                        intTsb.addTimeSeriesBrowse(parentTsb);
                    }
                } else {
                    logger.log(Level.WARNING, "unable to find parent {0}", ss[i]);
                    if (parentSources == null) {
                        logger.warning("strange case where parent sources is not resolved."); //TODO: looks like we can get here by adding scatter plot of two tsbs, then replace with demo 1.
                        return; //https://sourceforge.net/tracker/?func=detail&aid=3516161&group_id=199733&atid=970682
                    }
                    parentSources[i] = null;
                }
            }
            if (intTsb != null) {
                this.setTsb(intTsb);
                this.timeSeriesBrowseController = new TimeSeriesBrowseController(this, null);
                // find the plot that will control this.  Better not plot this twice!
                Plot p = getApplication().getController().getFirstPlotFor(dsf);
                if (p != null) {
                    this.timeSeriesBrowseController.setupGen(p, Plot.PROP_CONTEXT);
                } else {
                    logger.warning("check into this case, shouldn't happen");
                }
            }
            haveCheckedInternalTsb = true;
        }
    }

    /**
     * Introduced to support children that are TSBs. All are assumed to be the
     * same, the first is used for the getter.
     */
    class InternalTimeSeriesBrowse implements TimeSeriesBrowse {

        String uri;
        DatumRange timerange;

        private InternalTimeSeriesBrowse(String uri) {
            this.uri = uri; // "vap+internal:data_1,data_2"
        }

        List<TimeSeriesBrowse> parentTsbs = new ArrayList<>();

        public void addTimeSeriesBrowse(TimeSeriesBrowse tsb) {
            parentTsbs.add(tsb);
            if (parentTsbs.size() == 1) {
                setTimeRange(tsb.getTimeRange());
                setTimeResolution(tsb.getTimeResolution());
            }
        }

        @Override
        public void setTimeRange(DatumRange dr) {
            for (TimeSeriesBrowse tsb : parentTsbs) {
                tsb.setTimeRange(dr);
            }
            this.timerange = dr;
            checkParents();
        }

        @Override
        public DatumRange getTimeRange() {
            return timerange;
        }

        @Override
        public void setTimeResolution(Datum d) {
            for (TimeSeriesBrowse tsb : parentTsbs) {
                tsb.setTimeResolution(d);
            }
        }

        @Override
        public Datum getTimeResolution() {
            return parentTsbs.get(0).getTimeResolution(); //TODO: this should probably be the coursest.
        }

        @Override
        public String getURI() {
            Datum res = getTimeResolution();
            return this.uri + "?range=" + getTimeRange() + (res == null ? "" : "&resolution=" + res);
        }

        @Override
        public String toString() {
            Datum res = getTimeResolution();
            return "inttsb: " + getTimeRange() + " " + (res == null ? "" : "&resolution=" + res);
        }

        @Override
        public void setURI(String suri) throws ParseException {
            throw new IllegalArgumentException("not implemented");
        }

        @Override
        public String blurURI() {
            return this.uri;
        }

    }

    /**
     * vap+internal: -> populate array of parent sources.
     * <br>preconditions: uri is "" or "data_1,data_2"
     * <br>postconditions: unbind() is called if the uri doesn't start with vap+internal, or parentSources array is containing references to the other data sources.
     * @see #unbind() 
     */
    private void resolveParents() {
        synchronized (dscLock) {
            if (dsf.getUri().length() == 0) {
                return; //TODO: remove
            }
            URISplit split = URISplit.parse(dsf.getUri());
            if (!dsf.getUri().startsWith("vap+internal:")) {
                logger.fine("unbinding because this doesn't have parents.");
                unbind();
                return;
            }
            String[] ss = split.surl.split(",", -2);
            for (int i = 0; i < ss.length; i++) {
                DataSourceFilter dsf1 = (DataSourceFilter) DomUtil.getElementById(dom, ss[i]);
                if (dsf1 != null) {
                    //TODO: where are these listeners removed?
                    dsf1.controller.addPropertyChangeListener(DataSourceController.PROP_FILLDATASET, parentListener);
                    parentSources[i] = dsf1;
                } else {
                    logger.log(Level.WARNING, "unable to find parent {0}", ss[i]);
                    parentSources[i] = null;
                }
            }
        }
    }

    private static Map maybeCopy(Map m) {
        if (m == null) {
            return new HashMap();
        }
        return new HashMap(m);
    }
    

    /**
     * check to see if all the parent sources have updated and have datasets
     * that are compatible, so a new dataset can be created. If there is a
     * TimeSeriesBrowse on this, then attempt to trim the data to the
     * TimeSeriesBrowse.getTimeRange().
     *
     * @return null if everything is okay, error message otherwise
     */
    private String checkParents() {

        QDataSet x;
        QDataSet y = null;
        QDataSet z = null;
        Map<String, Object> xprops, yprops = null, zprops = null;

        QDataSet ds = null;
        Map<String, Object> props = null;

        DataSourceFilter[] lparentSources = getParentSources();
        
        boolean willTrim= this.tsb != null && this.tsb instanceof InternalTimeSeriesBrowse;
        
        // https://sourceforge.net/p/autoplot/feature-requests/425/ mashing data.  This area needs to be
        // cleaned up.
        if (lparentSources.length == 0) {
            return "no parent sources";
        }
        if (lparentSources[0] == null) {
            return "first parent is null";
        }
        x = lparentSources[0].controller.getFillDataSet();
        xprops = maybeCopy(lparentSources[0].controller.getFillProperties());
        if (lparentSources.length > 1) {
            if (lparentSources[1] == null) {
                return "second parent is null";
            }
            y = lparentSources[1].controller.getFillDataSet();
            yprops = maybeCopy(lparentSources[1].controller.getFillProperties());
        }
        if (lparentSources.length > 2) {
            if (lparentSources[2] == null) {
                return "third parent is null";
            }
            z = lparentSources[2].controller.getFillDataSet();
            zprops = maybeCopy(lparentSources[2].controller.getFillProperties());
        }
        
        switch (lparentSources.length) {
            case 1: {
                if (x == null) {
                    return "parent dataset is null";
                }   
                if (DataSetUtil.validate(x, null)) {
                    ds = x;
                    props = xprops;
                }   
                break;
            }
            case 2: {
                if (x == null || y == null) {
                    return "first or second dataset is null";
                }   
                ArrayDataSet yds = ArrayDataSet.copy(y);
                assert yprops != null;
                if (DataSetUtil.validate(x, yds, null)) {
                    boolean dep0mismatch= false;
                    if ( willTrim ) {
                        QDataSet xdep0= (QDataSet) x.property( QDataSet.DEPEND_0 );
                        QDataSet ydep0= (QDataSet) y.property( QDataSet.DEPEND_0 );
                        if ( xdep0!=null && ydep0!=null && xdep0.length()>0 ) {
                            if ( Ops.eq( xdep0.slice(0), ydep0.slice(0) ).value()==0 ) {
                                dep0mismatch= true;
                            }
                        }
                    }
                    if ( dep0mismatch ) {
                        return "dataset DEPEND_0 do not line up";
                    } else {
                        yds.putProperty(QDataSet.DEPEND_0, x);
                        yprops.put(QDataSet.DEPEND_0, xprops);
                        if (DataSetUtil.validate(yds, null)) { //TODO: probably don't have to do check this twice.
                            ds = yds;
                            props = yprops;
                        }
                    }
                } else {
                    return "linked data doesn''t validate: "+x+" and "+y; 
                }   
                break;
            }
            case 3: {
                if (x == null || y == null || z == null) {
                    return "at least one of the three datasets is null";
                }   
                assert yprops != null;
                assert zprops != null;
                if (z.rank() == 1) {
                    ArrayDataSet yds = ArrayDataSet.copy(y);
                    yds.putProperty(QDataSet.RENDER_TYPE, null);
                    yds.putProperty(QDataSet.DEPEND_0, x);
                    yds.putProperty(QDataSet.PLANE_0, z);
                    yprops.put(QDataSet.DEPEND_0, xprops);
                    yprops.put(QDataSet.PLANE_0, zprops);
                    if (DataSetUtil.validate(yds, null)) { //TODO: link should and probably does work here
                        ds = yds;
                        props = yprops;
                    } else {
                        return "linked data doesn't validate";
                    }
                } else {
                    ArrayDataSet zds = ArrayDataSet.copy(z);
                    zds.putProperty(QDataSet.DEPEND_0, x);
                    zds.putProperty(QDataSet.DEPEND_1, y);
                    if (DataSetUtil.validate(x, y, z, null)) {
                        zprops.put(QDataSet.DEPEND_0, xprops);
                        zprops.put(QDataSet.DEPEND_1, yprops);
                        ds = zds;
                        props = zprops;
                    } else {
                        return "linked data doesn't validate";
                    }
                }
                break;
            }
            default:
                break;
        }

        logger.log(Level.FINE, "checkParents resolves {0}", ds);
        
        if (ds != null) {
            //TODO: TSB trim dataset.  It's not clear to me that this should be implemented here, but we will for now.
            //See https://sourceforge.net/p/autoplot/bugs/1559/, where the following code needs to appear elsewhere.
            if (this.tsb != null && this.tsb instanceof InternalTimeSeriesBrowse) {
                QDataSet xds = (QDataSet) ds.property(QDataSet.DEPEND_0);
                if (xds != null) {
                    QDataSet xxds = (QDataSet) xds.property(QDataSet.DEPEND_0);
                    if (xxds != null && UnitsUtil.isTimeLocation(SemanticOps.getUnits(xxds)) && SemanticOps.isMonotonic(xxds)) { //okay we can trim
                        DatumRange dr = this.tsb.getTimeRange();
                        int idx0 = DataSetUtil.getPreviousIndex(xxds, dr.min());
                        int idx1 = DataSetUtil.getNextIndex(xxds, dr.max());
                        if ( idx1<xxds.length()+1 ) { // getNextIndex is inclusive...
                            idx1= idx1+1;
                        }
                        logger.log(Level.FINE, "checkParents trimming parents ds.trim({0},{1})", new Object[]{idx0, idx1});
                        if (idx0 == idx1) {
                            ds = null;
                        } else if (idx0 > idx1) {
                            logger.warning("non mono error?");
                            ds = null;
                        } else {
                            QDataSet trim = ds.trim(idx0, idx1);                            
                            if ( DataSetUtil.validate( trim, null ) ) {
                                ds = trim;
                            } else {
                                return "data doesn't validate after trimming";
                            }
                        }
                    }
                }
            }
            setDataSetInternal(ds, props, this.dom.controller.isValueAdjusting());
        }
        return null;
    }

    PropertyChangeListener parentListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt);  
            String prob = checkParents();
            if (prob != null) {
                if ( !dom.controller.isPendingChanges() ) {
                    setStatus("warning: " + prob);
                }
                setDataSetInternal(null, null, dom.controller.isValueAdjusting());
            }
            if (DataSourceController.this.haveCheckedInternalTsb == false) {
                maybeAddInternalTimeSeriesBrowse();
            }
        }
    };

    PropertyChangeListener dsfListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt);  
            resolveParents();
        }
    };

    /**
     * resolve a URI like vap+internal:data_0,data_1. 
     * <br>preconditions: a vap+internal URI was found. 
     * <br>postconditions: parentSources array is
     * defined with one element per parent. a listener is installed for each
     * parent what will notify when a dataset is loaded.
     *
     * @param path string with internal references like "data_1,data_2,data_4".
     * @return true if the resolution was successful.
     */
    private boolean doInternal(String path) {
        synchronized (internalLock) {
            if (parentSources != null) {
                for (DataSourceFilter parentSource : parentSources) {
                    if (parentSource != null) {
                        parentSource.controller.removePropertyChangeListener(DataSourceController.PROP_FILLDATASET, parentListener);
                    }
                }
            }
            if (path.trim().length() == 0) {
                return true;
            }
            String[] ss = path.split(",", -2);
            parentSources = new DataSourceFilter[ss.length];
            resolveParents();
            String prob = checkParents();
            if (prob != null) {
                if ( !dom.controller.isPendingChanges() ) {
                    setStatus("warning: " + prob);
                }
                return false;
            }
            dom.addPropertyChangeListener(Application.PROP_DATASOURCEFILTERS, dsfListener);
        }
        return true;
    }

    /**
     * remove the propertyChangeListener for dom property dataSourceFilters
     */
    protected void unbind() {
        dom.removePropertyChangeListener(Application.PROP_DATASOURCEFILTERS, dsfListener);
    }

    /**
     * preconditions: dataSet is set. postconditions: properties is set.
     */
    private void extractProperties(QDataSet ds) {
        Map<String, Object> props; // QDataSet properties.

        props = AutoplotUtil.extractProperties(ds);
        DataSource dss = getDataSource();
        if (dss != null) {
            props = AutoplotUtil.mergeProperties(dss.getProperties(), props);
        }

        setProperties(props);

    }

    /**
     * look in the metadata for fill and valid range.
     */
    public void doFillValidRange() {
        Object v;

        Map<String, Object> props = getProperties();

        if ((v = props.get(QDataSet.FILL_VALUE)) != null) {
            dsf.setFill(String.valueOf(v));
        }

        Number vmin = (Number) props.get(QDataSet.VALID_MIN);
        Number vmax = (Number) props.get(QDataSet.VALID_MAX);
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
     * Rewrite the dataset so that fill values are set by the valid extent and
     * fill controls. The user can override these values, so make sure the
     * values that came with the dataset are observed as well.
     *
     * Note an old version of this would make fill canonical, now this simply
     * resets the VALID_MIN, VALID_MAX, and FILL_VALUE properties.
     *
     * Old values of vmin, vmax, and fill are ignored. moved over from
     * AutoplotUtil.
     */
    private static void applyFillValidRange(MutablePropertyDataSet ds, double vmin, double vmax, double fill) {

        // TODO bug 1141: reimplement this.
        if (vmin > (-1 * Double.MAX_VALUE)) {
            ds.putProperty(QDataSet.VALID_MIN, vmin);
        }
        if (vmax < Double.MAX_VALUE) {
            ds.putProperty(QDataSet.VALID_MAX, vmax);
        }
        if (!Double.isNaN(fill)) {
            ds.putProperty(QDataSet.FILL_VALUE, fill);
        }
    }

    /**
     * call updateFill in new thread, or immediately on this thread when 
     * delay is 0.
     *
     * @param delay insert this delay so other threads may complete first.  
     */
    private void updateFillSoon(final int delay) {
        changesSupport.registerPendingChange(this, PENDING_FILL_DATASET);
        Runnable run = new Runnable() {
            @Override
            public void run() {
                try {
                    changesSupport.performingChange(DataSourceController.this, PENDING_FILL_DATASET);
                    if (delay > 0) {
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ex) {
                            logger.log(Level.SEVERE, ex.getMessage(), ex);
                        }
                    }
                    updateFill();
                } finally {
                    changesSupport.changePerformed(DataSourceController.this, PENDING_FILL_DATASET);
                }
            }
        };
        if (delay == 0) {
            logger.finest("delay=0 means I should update fill in this thread");
            run.run();
        } else {
            RequestProcessor.invokeLater(run);
        }
    }

    /**
     * guess the cadence of the dataset tags, putting the rank 0 dataset cadence
     * into the property QDataSet.CADENCE of the tags ds. fillDs is used to
     * identify missing values, which are skipped for the cadence guess.
     *
     * Note this may set or override the cadence setting found within the dataset.
     * 
     * @param xds the tags for which the cadence is determined.
     * @param fillDs a dependent dataset possibly with fill values, or null.
     */
    private static void guessCadence(MutablePropertyDataSet xds, QDataSet fillDs) {
        if (xds.length() < 2) {
            return;
        }
        RankZeroDataSet cadence = DataSetUtil.guessCadenceNew(xds, fillDs);
        if ( xds.isImmutable() ) {
            logger.fine("MutablePropertyDataSet has been made immutable, adding cadence annotation instead.");
            DataSetAnnotations.getInstance().putAnnotation( xds, DataSetAnnotations.ANNOTATION_CADENCE, cadence );
        } else {
            if (cadence != null && "log".equals(cadence.property(QDataSet.SCALE_TYPE))) {
                xds.putProperty(QDataSet.SCALE_TYPE, "log");
            }
            if (cadence != null) {
                xds.putProperty(QDataSet.CADENCE, cadence);
            }
        }
    }

    /**
     * <p>
     * the fill parameters have changed, so fire an update to notify the
     * listeners. This creates the "fillDataSet" whose name comes from an early
     * version of Autoplot where fill data would be handled here.
     * The filters also modify fill.
     * </p>
     *
     * <p>
     * This should not be run on the AWT event thread!</p>
     *
     * preconditions: <ul>
     * <li>The dataset has been loaded
     * </ul>
     * postconditions: <ul>
     * <li>the "fill" dataset is created.
     * <li>internal operations like slice may be applied here if the .vap file
     * has instructed, but rarely are.
     * <li>reduceRankString is set to document any operation.
     * <li>the fillDataSet is set. the fillProperties are set.
     * <li>all parties interested in the fill dataset will be notified of the
     * new version. (which triggers plotting via PlotElement)
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private void updateFill() {

        logger.fine("enter updateFill");

        QDataSet ds = getDataSet();

        if (ds == null) {
            return;
        }

        changesSupport.performingChange(this, PENDING_FILL_DATASET);
        try {

            Map props = getProperties();

            MutablePropertyDataSet fillDs;

            String filters = dsf.getFilters();

            boolean doSlice = filters.length() > 0;

            if (doSlice) { // plot element now does slicing, but we can do it here as well.

                try {
                    ds = DataSetOps.sprocess(filters, ds, new AlertNullProgressMonitor("sprocess " + filters));
                    //TODO: must we process the props as well?

                    setAppliedFiltersString(filters);
                } catch (Exception ex) {
                    setException(ex);
                    throw new RuntimeException(ex);
                }

                fillDs = DataSetOps.makePropertiesMutable(ds);
//                if ( ds instanceof MutablePropertyDataSet && !((MutablePropertyDataSet)ds).isImmutable() ) {
//                    fillDs = Ops.copy( ds );
//                } else {
//                    fillDs= DataSetOps.makePropertiesMutable( ds );
//                }

            } else {
                fillDs = DataSetOps.makePropertiesMutable(ds);
//                if ( ds instanceof MutablePropertyDataSet && !((MutablePropertyDataSet)ds).isImmutable() ) {
//                    fillDs= Ops.copy( ds );
//                } else {
//                    fillDs= DataSetOps.makePropertiesMutable( ds );
//                }
                setAppliedFiltersString(null);

            }

            if ( true ) { // don't rely on code which assumes fillDs is mutable.
                // add the cadence property to each dimension of the dataset, so that
                // the plot element doesn't have to worry about it.  TODO: review this
                for (int i = 0; i < fillDs.rank(); i++) {
                    QDataSet dep = (QDataSet) fillDs.property("DEPEND_" + i);
                    if (dep != null) {
                        dep = DataSetOps.makePropertiesMutable(dep);
                        if (i == 0 && dep.rank() == 1) {
                            guessCadence((MutablePropertyDataSet) dep, fillDs);
                        } else {
                            if (dep.rank() == 1) {
                                guessCadence((MutablePropertyDataSet) dep, null);
                            }
                        }
                        try {
                            fillDs= Ops.putProperty( fillDs, "DEPEND_" + i, dep);
                        } catch ( IllegalArgumentException ex ) {
                            logger.info("dataset become immutable.  Making mutable copy.");
                            fillDs= Ops.copy(fillDs); //TODO: fix this, there should be no need to copy.
                            fillDs= Ops.putProperty( fillDs, "DEPEND_" + i, dep);
                        }
                    }
                }
            }

            //props.put( QDataSet.RENDER_TYPE, null );
            //DataSetUtil.putProperties( props, fillDs ); //NEW: just copy the properties into the dataset.

            /*  begin fill dataset  */
            double vmin = Double.NEGATIVE_INFINITY, vmax = Double.POSITIVE_INFINITY, fill = Double.NaN;

            try {
                double[] vminMaxFill = PlotElementUtil.parseFillValidRangeInternal(dsf.getValidRange(), dsf.getFill());
                vmin = vminMaxFill[0];
                vmax = vminMaxFill[1];
                fill = vminMaxFill[2];
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
            // check the dataset for fill data, inserting canonical fill values.
            applyFillValidRange(fillDs, vmin, vmax, fill);

            setFillProperties(props);
            if (fillDs == ds) { //kludge to force reset renderer, because QDataSet is mutable.
                this.fillDataSet = null;
            }
                        
            fillDs.makeImmutable();
            
            setFillDataSet(fillDs);
        } finally {
            changesSupport.changePerformed(this, PENDING_FILL_DATASET);
        }
    }

    /**
     * do update on this thread, ensuring that only one data load is occurring
     * at a time. Note if a dataSource doesn't check mon.isCancelled(), then
     * processing will block until the old load is done.
     */
    private void updateImmediately(Exception parentException) {

        try {
            DataSource dss = getDataSource();
            if (dss != null) {
                setStatus("busy: loading dataset");
                logger.log(Level.FINE, "loading dataset {0}", dss);
                if (tsb != null) {
                    logger.log(Level.FINE, "   tsb= {0}", tsb.getURI());
                }

                /**
                 * * here is the data load **
                 */
                loadDataSet(parentException);

                if (dataSet != null) {
                    setStatus("done loading dataset");
                    if (dsf.getUri().length() == 0) {
                        logger.fine("dsf.getUri was null"); // I'm not sure what the condition was here, but I never use this log message.
                        return;
                    }
                } else {
                    if (!dom.controller.getStatus().startsWith("warning:")) {
                        setStatus("no data returned");
                    }
                }
            } else {
                if (!(this.parentSources != null)) {
                    setDataSetInternal(null);
                } else {
                    String prob = checkParents();
                    if (prob != null) {
                        logger.warning(prob);
                    }
                }
            }
            if (dataSet != null) {
                setStatus("ready");
            }
        } catch (RuntimeException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            setStatus("error: " + ex);
            model.getExceptionHandler().handleUncaught(ex);
        }

    }
    private Updating updating;
    private PropertyChangeListener updatesListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt);  
            QDataSet ds = (QDataSet) evt.getNewValue();
            if (ds != null) {
                setDataSetInternal(ds);
            } else {
                List<PlotElement> pelements = dom.controller.getPlotElementsFor(dsf);
                for (PlotElement p : pelements) {
                    p.getController().setResetRanges(true);
                }
                update();
            }
        }
    };

    /**
     * cancel the loading process.
     */
    public void cancel() {
        DataSource dss = this.getDataSource();  // Note not getDataSource, which is synchronized.
        if (dss != null && dss.asynchronousLoad() && !dom.controller.isHeadless()) {
            ProgressMonitor monitor = getMonitor();
            if (monitor != null) {
                logger.fine("cancel running request");
                monitor.cancel();
            }
        }
    }

    /**
     * update the model and view using the new DataSource to create a new
     * dataset. This calls update(false), indicating this was not triggered in
     * response to a human event.
     */
    public void update() {
        update(false);
    }

    /**
     * update the model and view using the new DataSource to create a new
     * dataset.
     *
     * @param user true if this is in response to a user action (e.g. not
     * FilePollUpdates)
     */
    public void update(final boolean user) {

        synchronized (dscLock) {
            changesSupport.registerPendingChange(this, PENDING_UPDATE);
            changesSupport.performingChange(this, PENDING_UPDATE);

            DataSource dss = getDataSource();
            logger.log(Level.FINE, "request update {0}", dss);

            setDataSet(null);

            final RuntimeException fe = new RuntimeException("attempt to load "+ ( dss!=null ? dss.toString() : "(null)" ) );
            
            Runnable run = new Runnable() {
                @Override
                public void run() {
                    try {
                        synchronized (dscLock) {
                            updateImmediately(fe);
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
                    } finally {
                        changesSupport.changePerformed(DataSourceController.this, PENDING_UPDATE);
                    }
                }

                @Override
                public String toString() {
                    return "load " + String.valueOf(dataSource);
                }
            };

            if (dss != null && dss.asynchronousLoad() && !dom.controller.isHeadless()) {
                // this code will never be invoked because this is synchronous.  See cancel().
                logger.fine("invoke later do load");
                ProgressMonitor monitor = getMonitor();
                if (monitor != null) {
                    logger.warning("double load!");
                    monitor.cancel();
                }
                RequestProcessor.invokeLater(run);
            } else {
                run.run();
            }
        }
    }
    /**
     * **** controller properties ******
     */
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

    public void setTsb(TimeSeriesBrowse tsb) {
        TimeSeriesBrowse oldTsb = this.tsb;
        this.tsb = tsb;
        propertyChangeSupport.firePropertyChange(PROP_TSB, oldTsb, tsb);
    }
    protected String tsbSuri = null;
    public static final String PROP_TSBSURI = "tsbSuri";

    public String getTsbSuri() {
        return tsbSuri;
    }

    public void setTsbSuri(String tsbSuri) {
        String oldTsbSuri = this.tsbSuri;
        this.tsbSuri = tsbSuri;
        propertyChangeSupport.firePropertyChange(PROP_TSBSURI, oldTsbSuri, tsbSuri);
    }

    protected Caching caching = null;
    public static final String PROP_CACHING = "caching";

    public Caching getCaching() {
        return caching;
    }

    public void setCaching(Caching caching) {
        Caching oldCaching = this.caching;
        this.caching = caching;
        propertyChangeSupport.firePropertyChange(PROP_CACHING, oldCaching, caching);
    }

    /**
     * object that can provide data sets and capabilities.
     */
    public static final String PROP_DATASOURCE = "dataSource";

    protected DataSource dataSource = null;

    /**
     * return the controller's current datasource. This was synchronized, but
     * this would mean that external clients could not query what the current
     * source was. Since this is only reading the variable, this seems harmless.
     * Note, findbugs prompted the code change, not an observed bug. TODO: there
     * is probably a better way to do this, synchronizing properly on several
     * objects.
     *
     * @return the controller's current datasource.
     */
    public DataSource getDataSource() {
        logger.log(Level.FINER, "accessing data source");

        // Since the dataSource is immutable, this needn't be synchronized.  
        // Synchronizing caused a hang:
        //   plot vap+inline:http://autoplot.org/data/AMSR_E_L3_SeaIce6km_B06_20070307.hdf?SpPolarGrid06km/Data Fields/SI_06km_SH_89H_DSC
        //   edit URI, select a different one, plot below.
        //synchronized ( this ) {
        return dataSource;
        //}
    }

    public void setDataSource(DataSource dataSource) {
        DataSource oldDataSource;
        synchronized (dscLock) {
            oldDataSource = this.dataSource;
            this.dataSource = dataSource;
        }
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

    /**
     * see setDataSetInternal, which does autoranging, etc. TODO: fix this and
     * the fillDataSet stuff...
     * @see #setDataSetInternal(org.das2.qds.QDataSet) 
     * @param dataSet
     */
    public void setDataSet(QDataSet dataSet) {
        QDataSet oldDataSet = this.dataSet;
        this.dataSet = dataSet;
        try {
            propertyChangeSupport.firePropertyChange(PROP_DATASET, oldDataSet, dataSet);
        } catch ( NullPointerException ex ) {
            logger.log( Level.WARNING, "https://sourceforge.net/p/autoplot/bugs/1770/", ex ); // See rte_0031957296, https://sourceforge.net/p/autoplot/bugs/1770/
        }
    }
    /**
     * fill dataset is a copy of the loaded dataset, with fill data applied. If
     * dataset has mutable properties, then the fillDataSet will be the same as
     * the dataset, and the dataset's properties are modified.
     */
    protected QDataSet fillDataSet = null;
    public static final String PROP_FILLDATASET = "fillDataSet";

    public QDataSet getFillDataSet() {
        return fillDataSet;
    }

    public void setFillDataSet(QDataSet fillDataSet) {
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
    
    /**
     * @deprecated this function will be removed and put into the metadata tab.
     */
    public static final String PROP_HISTOGRAM = "histogram";

    /**
     * @deprecated this function will be removed and put into the metadata tab.
     */
    public QDataSet getHistogram() {
        return histogram;
    }

    /**
     * @deprecated this function will be removed and put into the metadata tab.
     * @param histogram 
     */
    public void setHistogram(QDataSet histogram) {
        QDataSet oldHistogram = this.histogram;
        this.histogram = histogram;
        propertyChangeSupport.firePropertyChange(PROP_HISTOGRAM, oldHistogram, histogram);
    }

    protected Map<String, Object> properties = null;
    public static final String PROP_PROPERTIES = "properties";

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        Map<String, Object> oldProperties = this.properties;
        this.properties = properties;
        propertyChangeSupport.firePropertyChange(PROP_PROPERTIES, oldProperties, properties);
    }
    protected Map<String, Object> fillProperties = null;
    public static final String PROP_FILLPROPERTIES = "fillProperties";

    public Map<String, Object> getFillProperties() {
        return fillProperties;
    }

    public void setFillProperties(Map<String, Object> fillProperties) {
        Map<String, Object> oldFillProperties = this.fillProperties;
        this.fillProperties = fillProperties;
        propertyChangeSupport.firePropertyChange(PROP_FILLPROPERTIES, oldFillProperties, fillProperties);
    }
    protected String reduceDataSetString = "";
    public static final String PROP_REDUCEDATASETSTRING = "reduceDataSetString";

    /**
     * return documentation of any processes applied to the data within the
     * DataSourceFilter. This will be an empty string when no processes were
     * applied. See getFilters which specified which should be applied.
     *
     * @return reduceDataSetString the string, which may be empty but will not
     * be null.
     */
    public String getAppliedFiltersString() {
        return reduceDataSetString;
    }

    /**
     * set the documentation of any processes applied to the data within the
     * DataSourceFilter. This will be an empty string when no processes were
     * applied. See getFilters which specified which should be applied.
     *
     * @param appliedFilters
     */
    public void setAppliedFiltersString(String appliedFilters) {
        assert appliedFilters != null;
        String oldReduceDataSetString = this.reduceDataSetString;
        this.reduceDataSetString = appliedFilters;
        propertyChangeSupport.firePropertyChange(PROP_REDUCEDATASETSTRING, oldReduceDataSetString, appliedFilters);
    }

    /**
     * add breaks to make the message more legible in labels.
     *
     * @param message
     * @return
     */
    private static String addHtmlBreaks(String message) {
        if (message.startsWith("<html>")) {
            return message;
        }
        String[] ss = message.split(": ");
        if (ss.length == 1) {
            return message;
        }
        StringBuilder result = new StringBuilder("<html>");
        result.append(ss[0]);
        for (int i = 1; i < ss.length; i++) {
            result.append(": <br>").append(ss[i]);
        }
        return result.toString();
    }

    private ProgressMonitor getMonitor() {
        return mon;
    }

    /**
     * load the data set from the DataSource.
     */
    private QDataSet loadDataSet(Exception parentException) {
        synchronized (dscLock) {
            ProgressMonitor mymon;

            QDataSet result = null;

            DataSource dss = getDataSource();

            mymon = getMonitor("loading data", "loading " + dss);
            this.mon = mymon;
            try {

                // Call the data source to get the data set.
                logger.log(Level.FINE, "load {0}", dss);
                result = dss.getDataSet(mymon);
                
                //if (dsf.getUri().length() > 0) {
                //    this.model.addRecent(dsf.getUri());
                //}
                logger.log(Level.FINE, "read dataset: {1} from {0}", new Object[]{dss, result});
                Map<String, Object> props = dss.getMetadata(new AlertNullProgressMonitor("getMetadata"));

                TimeSeriesBrowse ltsb= getTsb();
                TimeSeriesBrowseController ltsbc= getTimeSeriesBrowseController();
                if ( result != null && ltsb != null && ltsbc!=null && result.rank() > 0 
                        && !UnitsUtil.isTimeLocation(SemanticOps.getUnits(SemanticOps.xtagsDataSet(result))) ) {
                    Plot p = ltsbc.getPlot();
                    if ( p==null ) {
                        logger.warning("unexpected timeSeriesBrowseController.domPlot==null");
                    } else {
                        if ( UnitsUtil.isTimeLocation( p.getXaxis().getRange().getUnits() ) ) {
                            List<PlotElement> pes= dom.getController().getPlotElementsFor(p);
                            if ( pes.size()>1 ) {
                                logger.log(Level.INFO,"not resetting because others use this axis");
                            } else {
                                logger.log(Level.INFO, "resetting autorange=T because dataset is not time series: {0}", result);
                                p.getXaxis().setAutoRange(true);
                            }
                        }
                    }
                    //https://sourceforge.net/p/autoplot/bugs/1559/ Let's trim it...
                    int count= result.length();
                    result= DataSourceUtil.trimScatterToTimeRange( result, ltsb.getTimeRange() );
                    if ( result.length()==0 && count>0 ) {
                        logger.warning("trimScatterToTimeRange removes all points!");
                    }
                }

                setDataSetInternal(result, props, dom.controller.isValueAdjusting());

                // look again to see if it has timeSeriesBrowse now--JythonDataSource
                if ( ltsb== null && dss.getCapability(TimeSeriesBrowse.class) != null) {
                    TimeSeriesBrowse tsb1 = dss.getCapability(TimeSeriesBrowse.class);
                    PlotElement pe = getPlotElement(false);
                    if (pe != null && this.doesPlotElementSupportTsb(pe)) {  //TODO: less flakey
                        setTsb(tsb1);
                        ltsbc= new TimeSeriesBrowseController(this, pe);
                        timeSeriesBrowseController= ltsbc;
                        ltsbc.setup(false);
                    }
                }
                //embedDsDirty = true;
            } catch (InterruptedIOException ex) {
                setException(ex);
                setDataSet(null); //TODO: maybe we should allow the old dataset to stay, in case TSB....
                setStatus("interrupted");
                if (dsf.getUri().length() > 0) {
                    this.model.addException(dsf.getUri(), ex);
                }
            } catch ( org.das2.client.AccessDeniedException ex ) {
                setException(ex);
                setDataSet(null); //TODO: maybe we should allow the old dataset to stay, in case TSB....
                setStatus("access denied");
                if (dsf.getUri().length() > 0) {
                    this.model.addException(dsf.getUri(), ex);
                }
            } catch (org.das2.util.monitor.CancelledOperationException | CancelledOperationException ex) {
                setException(ex);
                setDataSet(null);
                setStatus("operation cancelled");
                if (dsf.getUri().length() > 0) {
                    this.model.addException(dsf.getUri(), ex);
                }
            } catch (NoDataInIntervalException ex) {
                setException(ex);
                setDataSet(null);
                setStatus("warning: " + ex.getMessage());

                if ( getTsb() == null) {
                    String title = "no data in interval";
                    model.showMessage("" + ex.getMessage(), title, JOptionPane.INFORMATION_MESSAGE);
                } else {
                    // do nothing.
                }
                if (dsf.getUri().length() > 0) {
                    this.model.addException(dsf.getUri(), ex);
                }
            } catch (HtmlResponseIOException ex) {
                final HtmlResponseIOException htmlEx = (HtmlResponseIOException) ex;
                if ( dom.controller.isHeadless() ) {
                    logger.log(Level.WARNING, ex.getMessage(), ex);
                    
                } else {
                    if (htmlEx.getURL() != null) {
                        final String link = htmlEx.getURL().toString();
                        final JPanel p = new JPanel(new BorderLayout());
                        p.add(new JLabel("<html>Unable to open URI: <br>" + dsf.getUri() + "<br><br>Downloaded file appears to be HTML.<br><a href=\"" + link + "\">" + link + "</a><br>"), BorderLayout.CENTER);
                        JPanel p1 = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                        p1.add(new JButton(new AbstractAction("Details") {
                            @Override
                            public void actionPerformed(ActionEvent ev) {
                                org.das2.util.LoggerManager.logGuiEvent(ev);
                                getApplication().controller.getApplicationModel().getExceptionHandler().handle(htmlEx);
                            }
                        }));
                        p1.add(new JButton(new AbstractAction("View Page") {
                            @Override
                            public void actionPerformed(ActionEvent ev) {
                                org.das2.util.LoggerManager.logGuiEvent(ev);
                                AutoplotUtil.openBrowser(link);
                            }
                        }));
                        p.add(p1, BorderLayout.SOUTH);
                        JOptionPane.showMessageDialog(DataSourceController.this.model.getCanvas(), p);
                    } else {
                        JOptionPane.showMessageDialog(DataSourceController.this.model.getCanvas(), "<html>Unable to open URI: <br>" + dsf.getUri() + "<br><br>" + ex);
                    }
                }
                if (dsf.getUri().length() > 0) {
                    this.model.addException(dsf.getUri(), ex);
                }

            } catch (IOException ex) {
                if (ex instanceof FileNotFoundException || (ex.getMessage() != null && (ex.getMessage().contains("No such file") || ex.getMessage().contains("timed out")))) {
                    String message = ex.getMessage();
                    if (message.startsWith("550 ")) {
                        message = message.substring(4);
                    }
                    if (message.startsWith("file:/") || message.startsWith("http://") || message.startsWith("https://")) {
                        message = "File not found: " + message;
                    }
                    setException(ex);
                    setDataSet(null);
                    setStatus("warning: " + message);
                    String title = (ex.getMessage().contains("No such file") || ex instanceof FileNotFoundException) ? "File not found" : ex.getMessage();
                    int i= title.indexOf('\n');
                    if (i>-1) {
                        title = title.substring(0,i);
                    }
                    if (message.contains(org.autoplot.aggregator.AggregatingDataSource.MSG_NO_FILES_FOUND)) {
                        // this implies that there are files in other intervals, so don't have popup
                    } else {
                        model.showMessage(addHtmlBreaks(message), title, JOptionPane.WARNING_MESSAGE);
                    }
                } else if (ex.getMessage() != null && ex.getMessage().contains("root does not exist")) {  // bugfix 3053225
                    setException(ex);
                    setDataSet(null);
                    setStatus("warning: " + ex.getMessage());
                    String title = ex.getMessage().contains("No such file") ? "Root does not exist" : ex.getMessage();
                    model.showMessage(addHtmlBreaks(ex.getMessage()), title, JOptionPane.WARNING_MESSAGE);
                } else if (ex.getMessage() == null) {
                    setException(ex);
                    logger.log(Level.WARNING, ex.getMessage(), ex);
                    setDataSet(null);
                    setStatus("error: " + ex.getClass());
                    ex.printStackTrace();
                    handleException(ex);
                } else {
                    setException(ex);
                    setDataSet(null);
                    setStatus("error: " + ex.getMessage());
                    ex.printStackTrace();
                    handleException(ex);
                }
                if (dsf.getUri().length() > 0) {
                    this.model.addException(dsf.getUri(), ex);
                }
            } catch (Exception ex) {
                setException(ex);
                setDataSet(null);
                logger.log(Level.WARNING, ex.getMessage(), ex);
                setStatus("error: " + ex.getMessage());
                if ( ex.getCause()==null ) {
                    try {
                        ex.initCause(parentException);
                    } catch ( RuntimeException ex2 ) {
                        logger.fine("unable to preserve the initial stack trace");
                    }
                }
                handleException(ex);
                if (dsf.getUri().length() > 0) {
                    this.model.addException(dsf.getUri(), ex);
                }
            } finally {
                // don't trust the data sources to call finished when an exception occurs.
                if (!mymon.isFinished()) {
                    mymon.finished();
                }
                if (mymon == this.mon) {
                    this.mon = null;
                } else {
                    logger.warning("not my mon, somebody better delete it!");
                }
            }
            return result;
        }
    }

    /**
     * Set the data source URI.
     *
     * @param suri
     * @param mon
     */
    public void setSuri(String suri, ProgressMonitor mon) {
        synchronized (dscLock) {
            suri = URISplit.makeCanonical(suri);
            synchronized (uriLock) {
                dsf.setUri(suri);
                setUriNeedsResolution(true);
            }
        }
    }

    /**
     * Set the data source URI, forcing a reload if it is the same.
     *
     * @param suri
     * @param mon
     */
    public void resetSuri(String suri, ProgressMonitor mon) {
        String old = dsf.getUri();
        suri = URISplit.makeCanonical(suri);
        synchronized (uriLock) {
            if (old.length() > 0 && old.equals(suri)) { // force reload
                dsf.setUri("");
            }
            dsf.setFilters("");
            setSuri(suri, mon);
        }
    }

    /**
     * Preconditions: dsf.getUri is set. Any or no datasource is set.
     * dom.getController().isValueAdjusting() is false Postconditions: A
     * dataSource object is created dsf._getDataSource returns the data source.
     * any parents the dataSource had (vap+internal:data_0,data_1) are deleted.
     * A thread has been started that will load the dataset. Side Effects:
     * update is called to start the download, unless if this is headless, then
     * the dataset has been loaded synchronously.
     * @param valueWasAdjusting true if the application was busy loading (see dom.controller.isValueAdjusting()), or if you don't want things to reset.
     * @param mon
     */
    protected void resolveDataSource(boolean valueWasAdjusting, ProgressMonitor mon) {
        Caching cache1 = getCaching();

        if (dom.getController().isValueAdjusting()) {
            logger.log(Level.WARNING, "return of bug first demoed by test033: where the adjusting property is breifly cleared. {0}", dom.getController().changesSupport.isValueAdjusting());
            logger.warning("See https://sourceforge.net/tracker/?func=detail&aid=3409414&group_id=199733&atid=970682");
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
        String surl = dsf.getUri();
        if (surl.length() == 0) {
            getApplication().getController().deleteAnyParentsOfDataSourceFilter(dsf);
            clearParentSources();
            resetDataSource(valueWasAdjusting, null);
            setUriNeedsResolution(false);
            setDataSetNeedsLoading(false);

        } else {
            getApplication().getController().deleteAnyParentsOfDataSourceFilter(dsf);

            URISplit split = URISplit.parse(surl);
            surl = URISplit.format(split);

            try {
                mon.started();
                mon.setProgressMessage("getting " + surl);

                if (cache1 != null) {
                    if (cache1.satisfies(surl)) {
                        cache1.resetURI(surl);
                        //trigger autorange
                        propertyChangeSupport.firePropertyChange(PROP_DATASOURCE, null, getDataSource());
                        update();
                        return;
                    }
                }

                if (URISplit.implicitVapScheme(split).equals("vap+internal")) {
                    clearParentSources();
                    resetDataSource(valueWasAdjusting, null);
                    boolean ok = doInternal(split.path);
                    String msg = null;
                    if (!ok) {
                        msg = dom.controller.getStatus();
                    }
                    //resetDataSource(valueWasAdjusting,null);
                    if (!ok) {
                        dom.controller.setStatus(msg);
                    }
                } else {
                    DataSource source = DataSetURI.getDataSource(surl);
                    clearParentSources();
                    resetDataSource(valueWasAdjusting, source);
                }
                setUriNeedsResolution(false);

                mon.setProgressMessage("done getting data source");

            } catch (Exception e) {
                logger.log(Level.WARNING, e.getMessage(), e);
                throw new RuntimeException(e);
            } finally {
                mon.finished();
            }
        }
    }

    /**
     * true if the URI has been changed, and must be resolved into a DataSource.
     */
    public static final String PROP_URINEEDSRESOLUTION = "uriNeedsResolution";

    protected boolean uriNeedsResolution = false;

    public boolean isUriNeedsResolution() {
        return uriNeedsResolution;
    }

    public void setUriNeedsResolution(boolean uriNeedsResolution) {
        boolean oldUriNeedsResolution = this.uriNeedsResolution;
        this.uriNeedsResolution = uriNeedsResolution;
        propertyChangeSupport.firePropertyChange(PROP_URINEEDSRESOLUTION, oldUriNeedsResolution, uriNeedsResolution);
    }

    /**
     * true is the DataSource has been changed, and we need to reload.
     */
    public static final String PROP_DATASETNEEDSLOADING = "dataSetNeedsLoading";

    protected boolean dataSetNeedsLoading = false;

    public boolean isDataSetNeedsLoading() {
        return dataSetNeedsLoading;
    }

    public void setDataSetNeedsLoading(boolean dataSetNeedsLoading) {
        boolean oldDataSetNeedsLoading = this.dataSetNeedsLoading;
        this.dataSetNeedsLoading = dataSetNeedsLoading;
        propertyChangeSupport.firePropertyChange(PROP_DATASETNEEDSLOADING, oldDataSetNeedsLoading, dataSetNeedsLoading);
    }

    protected boolean resetDimensions = false;
    /**
     * true if the data source is changed and we need to reset the dimension
     * names when we get our first data set.
     */
    public static final String PROP_RESETDIMENSIONS = "resetDimensions";

    public boolean isResetDimensions() {
        return resetDimensions;
    }

    public void setResetDimensions(boolean resetDimensions) {
        boolean oldResetDimensions = this.resetDimensions;
        this.resetDimensions = resetDimensions;
        propertyChangeSupport.firePropertyChange(PROP_RESETDIMENSIONS, oldResetDimensions, resetDimensions);
    }

    public Application getApplication() {
        return this.dom;
    }

    public TimeSeriesBrowseController getTimeSeriesBrowseController() {
        return timeSeriesBrowseController;
    }

    /**
     * try to fix leak.  https://sourceforge.net/p/autoplot/bugs/1584/
     */
    protected void releaseTimeSeriesBrowseController() {
        this.timeSeriesBrowseController= null;
    }
    
    @Override
    public boolean isPendingChanges() {
        TimeSeriesBrowseController tsbc = timeSeriesBrowseController;
        if (tsbc != null && tsbc.isPendingChanges()) {
            return true;
        }
        return super.isPendingChanges();
    }

    @Override
    public void pendingChanges(Map<Object, Object> changes) {
        super.pendingChanges(changes);
        TimeSeriesBrowseController tsbc = timeSeriesBrowseController;
        if (tsbc != null && tsbc.isPendingChanges()) {
            tsbc.pendingChanges(changes);
        }
    }

    private void handleException(Exception e) {
        if (model.getExceptionHandler() == null) {
            logger.log(Level.WARNING, e.getMessage(), e);
        } else if (e.getMessage() != null && e.getMessage().contains("nsupported protocol")) { //unsupport protocol
            model.showMessage(e.getMessage(), "Unsupported Protocol", JOptionPane.ERROR_MESSAGE);
        } else {
            logger.log(Level.WARNING, "model.getExceptionHandler: {0}", model.getExceptionHandler());
            model.getExceptionHandler().handle(e);
        }
    }

    /**
     * return the first plot element that is using this data source.
     *
     * @return null or a plot element.
     */
    private PlotElement getPlotElement(boolean checkChildren) {
        List<PlotElement> pele = dom.controller.getPlotElementsFor(dsf);
        if (pele.isEmpty()) {
            if ( checkChildren ) {
                DataSourceFilter dsf2= getChildSource();
                if ( dsf2==null ) {
                    return null;
                } else {
                    return dsf2.controller.getPlotElement(false);
                }
            } else {
                return null;
            }
        } else {
            return pele.get(0);
        }
    }

    private ProgressMonitor getMonitor(String label, String description) {

        PlotElement pele = getPlotElement(true);
        if ( pele==null ) {
            pele = getPlotElement(true);
        }
        
        DasPlot p = null;
        if (pele != null) {
            Plot plot = dom.controller.getPlotFor(pele);
            if (plot != null) {
                p = plot.controller.getDasPlot();
            }
        } else {
            Plot plot = dom.controller.getFirstPlotFor(this.dsf);
            if (plot != null) {
                p = plot.controller.getDasPlot();
            }
        }

        ProgressMonitor result;
        if (p != null) {
            result= dom.controller.getMonitorFactory().getMonitor(p, label, description);
        } else {
            result= dom.controller.getMonitorFactory().getMonitor(label, description);
        }
        
        DasProgressPanel.maybeCenter( result, p );
        
        return result;
    }

    private void setStatus(String string) {
        dom.controller.setStatus(string);
    }

    @Override
    public String toString() {
        return this.dsf + " controller";
    }
}
