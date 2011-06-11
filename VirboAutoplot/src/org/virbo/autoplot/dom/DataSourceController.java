/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

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
import javax.swing.JOptionPane;
import org.das2.CancelledOperationException;
import org.das2.dataset.NoDataInIntervalException;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.graph.DasPlot;
import org.das2.system.RequestProcessor;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.autoplot.AutoplotUtil;
import org.virbo.autoplot.util.RunLaterListener;
import org.virbo.dataset.ArrayDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.RankZeroDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.dataset.TransposeRank2DataSet;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.capability.Caching;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.datasource.capability.Updating;
import org.virbo.dsutil.AutoHistogram;
import org.virbo.metatree.MetadataUtil;

/**
 *
 * @author jbf
 */
public class DataSourceController extends DomNodeController {

    static final Logger logger = Logger.getLogger("vap.dataSourceController");
    DataSourceFilter dsf;
    private ApplicationModel model;
    private Application dom;

    /**
     * the current load being monitored.
     */
    private ProgressMonitor mon;
    private PropertyChangeListener updateSlicePropertyChangeListener = new PropertyChangeListener() {

        @Override
        public String toString() {
            return "" + dsf + " controller updateSlicePropertyChangeListener";
        }

        public void propertyChange(PropertyChangeEvent e) {
            if (dataSet != null && dataSet.rank() == 3) {
                logger.fine("updateSlice ->updateFillSoon()");
                int delay=0;
                if ( e.getPropertyName().equals( DataSourceFilter.PROP_SLICEDIMENSION ) ) { //kludge for 2795481: switch slice dimension can result in inconvertable units
                    delay= 100;
                }
                updateFillSoon(delay);
            }
        }
    };
    private PropertyChangeListener updateMePropertyChangeListener = new PropertyChangeListener() {

        @Override
        public String toString() {
            return "" + dsf + " controller updateMePropertyChangeListener";
        }

        public void propertyChange(PropertyChangeEvent e) {
            if (dataSet != null) {
                logger.fine("change in fill or valid range ->updateFillSoon()");
                updateFillSoon(0);
            }
        }
    };

    //TODO: This is the only thing listening to the dsf.uri.  
    private PropertyChangeListener resetMePropertyChangeListener = new PropertyChangeListener() {

        @Override
        public String toString() {
            return "" + dsf + " controller resetMePropertyChangeListener";
        }

        public void propertyChange(PropertyChangeEvent e) {
            logger.log( Level.FINE, "resetMe: {0} {1}->{2}", new Object[]{e.getPropertyName(), e.getOldValue(), e.getNewValue()});
            if (e.getNewValue() == null && e.getOldValue() == null) {
                return;
            } else {
                List<Object> whoIsChanging= changesSupport.whoIsChanging( PENDING_SET_DATA_SOURCE );
                if ( whoIsChanging.size()>0 ) {
                    System.err.println("!!! "+whoIsChanging +" !!!"); // we probably need to do something with this.
                    return;
                }
                DataSourceController.this.changesSupport.registerPendingChange( resetMePropertyChangeListener, PENDING_RESOLVE_DATA_SOURCE );
                setUriNeedsResolution(true);
                if (!dom.controller.isValueAdjusting()) {
                    DataSourceController.this.changesSupport.performingChange( resetMePropertyChangeListener, PENDING_RESOLVE_DATA_SOURCE );
                    resolveDataSource(false,getMonitor("resetting data source", "resetting data source"));
                    DataSourceController.this.changesSupport.changePerformed( resetMePropertyChangeListener, PENDING_RESOLVE_DATA_SOURCE );
                } else {
                    new RunLaterListener(ChangesSupport.PROP_VALUEADJUSTING, dom.controller, false ) {
                        @Override
                        public void run() {
                            DataSourceController.this.changesSupport.performingChange( resetMePropertyChangeListener, PENDING_RESOLVE_DATA_SOURCE );
                            if ( uriNeedsResolution ) resolveDataSource(true,getMonitor("resetting data source", "resetting data source"));
                            DataSourceController.this.changesSupport.changePerformed( resetMePropertyChangeListener, PENDING_RESOLVE_DATA_SOURCE );
                        }
                    };
                }
            }
        }
    };
    private TimeSeriesBrowseController timeSeriesBrowseController;
    private static final String PENDING_DATA_SOURCE = "dataSource";
    private static final String PENDING_RESOLVE_DATA_SOURCE = "resolveDataSource";
    private static final String PENDING_SET_DATA_SOURCE = "setDataSource"; //we are setting the datasource, so don't try to resolve, etc.
    private static final String PENDING_FILL_DATASET = "fillDataSet";
    private static final String PENDING_UPDATE = "update";

    public DataSourceController(ApplicationModel model, DataSourceFilter dsf) {
        super( dsf );
        
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

        setDepnames(Arrays.asList(depNames));

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
     * impossible logic of know if a plot element will support timeSeriesBrowse.  If
     * it doesn't, we would just want to load the data set and be done with it.
     * If it can then we want a tsb.
     *
     * The problem is that until the plot element does
     * its slicing, we don't really know if it will or not.  For now we kludge up
     * logic based on the component string or plotElements where the component string
     * won't be set automatically.
     *
     * @param p
     * @return
     */
    private boolean doesPlotElementSupportTsb( PlotElement p ) {
        return p.isAutoComponent() || (
                !( p.getComponent().contains("|slice0") || p.getComponent().contains("|collapse0") ) );

    }

    public synchronized void resetDataSource(boolean valueWasAdjusting,DataSource dataSource) {

        if ( dataSource==null ) {
            setDataSetNeedsLoading(false);
        } else {
            setDataSetNeedsLoading(true);
        }

        if (timeSeriesBrowseController != null) {
            timeSeriesBrowseController.release();
            this.timeSeriesBrowseController= null;
        }
        
        DataSource oldSource = getDataSource();

        if (dataSource == null) {
            setCaching(null);
            setTsb(null);
            setTsbSuri(null);
            if ( dsf.getUri()!=null && !dsf.getUri().startsWith("vap+internal" ) ) {
                dsf.setUri("vap+internal:");
            }

        } else {
            changesSupport.performingChange( this, PENDING_SET_DATA_SOURCE );
            setCaching(dataSource.getCapability(Caching.class));
            PlotElement pe= getPlotElement();
            if ( pe!=null && this.doesPlotElementSupportTsb( pe ) ) {  //TODO: less flakey
                setTsb(dataSource.getCapability(TimeSeriesBrowse.class));
            } else {
                //it might have an internal source listening to it.
                if ( pe==null ) {
                    setTsb(dataSource.getCapability(TimeSeriesBrowse.class));
                } else {
                    setTsb(null);
                }
            }
            if ( dsf.getUri()==null ) {
                dsf.setUri( dataSource.getURI() );
                setUriNeedsResolution(false);
            }
            changesSupport.changePerformed( this, PENDING_SET_DATA_SOURCE );
       }

        this.dsf.setValidRange("");
        this.dsf.setFill("");

        if ( valueWasAdjusting ) {
            this.dataSource = dataSource;
        } else {
            setDataSource(dataSource);
            setResetDimensions(true);
        }

        if (oldSource == null || !oldSource.equals(dataSource)) {
            List<PlotElement> ps = dom.controller.getPlotElementsFor(dsf);
            if ( getTsb() != null && ps.size()>0 ) {
                setDataSet(null);
                if (ps.size() > 0) {
                    timeSeriesBrowseController = new TimeSeriesBrowseController(this,ps.get(0));
                    timeSeriesBrowseController.setup(valueWasAdjusting);
                }
            } else if ( getTsb()!=null && ps.size()==0 ) {
                timeSeriesBrowseController = new TimeSeriesBrowseController(this,null);

                if ( !UnitsUtil.isTimeLocation( this.dom.getTimeRange().getUnits() ) ) {
                    List<BindingModel> bms= this.dom.getController().findBindings( this.dom, Application.PROP_TIMERANGE, null, null );
                    if ( bms==null || bms.size()==0 ) {
                        System.err.println("claiming dom timerange for TSB: "+this.dsf.getUri() );
                        this.dom.setTimeRange( getTsb().getTimeRange() );
                        System.err.println("about to setup Gen for "+this);
                        timeSeriesBrowseController.setupGen(this.dom,Application.PROP_TIMERANGE);  
                        update(!valueWasAdjusting, !valueWasAdjusting );
                    } else {
                        System.err.println("unable to use timerange as guide");
                        update(!valueWasAdjusting, !valueWasAdjusting );
                    }
                } else {
                    System.err.println("using dom timerange for TSB: "+this.dsf.getUri() );
                    timeSeriesBrowseController.setupGen(this.dom,Application.PROP_TIMERANGE);
                    update(!valueWasAdjusting, !valueWasAdjusting );
                }

            } else {
                update(!valueWasAdjusting, !valueWasAdjusting );
            }
        }

    }

    public synchronized void setDataSetInternal( QDataSet ds ) {
        setDataSetInternal( ds, null , this.dom.controller.isValueAdjusting() );
    }

    public static boolean isTimeSeries( QDataSet ds ) {
        QDataSet dep0= SemanticOps.xtagsDataSet(ds);
        if ( dep0!=null ) {
            Units u= SemanticOps.getUnits(dep0);
            if ( UnitsUtil.isTimeLocation(u) ) {
                return true;
            }
        }
        return false;
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
     *  slice dimensions are set for dataset.
     * 
     * @param ds
     * @param autorange if false, autoranging will not be done.  if false, autoranging
     *   might be done.
     * @param immediately if false, then this is done after the application is done adjusting.
     */
    public synchronized void setDataSetInternal( QDataSet ds, Map<String,Object> rawProperties, boolean immediately) {

        List<String> problems = new ArrayList<String>();

        if (ds != null && !DataSetUtil.validate(ds, problems)) {
            StringBuffer message = new StringBuffer("data set is invalid:\n");
            new Exception("data set is invalid").printStackTrace();
            for (String s : problems) {
                message.append(s).append("\n");
            }
            if (dom.controller.isHeadless()) {
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

        if ( this.getTimeSeriesBrowseController()!=null && ds!=null && !isTimeSeries(ds) && this.getTimeSeriesBrowseController().isListeningToAxis() ) {
            // the dataset we get back isn't part of a time series.  So we should connect the TSB
            // to the application TimeRange property.
            this.timeSeriesBrowseController.release();
            Axis xaxis= this.getTimeSeriesBrowseController().domPlot.getXaxis();
            dom.getController().unbind( dom, Application.PROP_TIMERANGE, xaxis, Axis.PROP_RANGE );
            dom.setTimeRange( this.timeSeriesBrowseController.getTimeRange() );//TODO: think about if this is really correct
            this.timeSeriesBrowseController.setupGen( dom, Application.PROP_TIMERANGE );
        }

        ApplicationController ac= this.dom.controller;

        if ( !immediately && ac.isValueAdjusting() ) {
            final QDataSet fds = ds;
            new RunLaterListener( ChangesSupport.PROP_VALUEADJUSTING, ac, false ) {
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
                setDepnames( Arrays.asList( "first", "second", "third" ) );
                return;
            }

            extractProperties();

            doDimensionNames();

            if ( DataSetUtil.totalLength(ds) < 200000 ) {
                setStatus("busy: do statistics on the data...");
                setHistogram(new AutoHistogram().doit(ds, null));
            } else {
                setHistogram( null );
            }

            setStatus("busy: apply fill");

            //doFillValidRange();  the QDataSet returned
            updateFill();

            setStatus("done, apply fill");

            List<PlotElement> pele= dom.controller.getPlotElementsFor(dsf);
            if ( pele.size()==0 ) {
                setStatus("warning: done loading data but no plot elements are listening");
            }


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

    private synchronized void clearParentSources() {
        this.parentSources= null; //TODO: are there listeners to dispose of?
    }

    /**
     * Introduced to support children that are TSBs.  All are assumed to be the same, the first is used for the getter.
     */
    class InternalTimeSeriesBrowse implements TimeSeriesBrowse {

        String uri;

        private InternalTimeSeriesBrowse( String uri ) {
            this.uri= uri; // "vap+internal:data_1,data_2"
        }

        List<TimeSeriesBrowse> parentTsbs= new ArrayList<TimeSeriesBrowse>();

        public void addTimeSeriesBrowse( TimeSeriesBrowse tsb ) {
            parentTsbs.add(tsb);
            if ( parentTsbs.size()==1 ) {
                setTimeRange( tsb.getTimeRange() );
                setTimeResolution( tsb.getTimeResolution() );
            }
        }

        public void setTimeRange(DatumRange dr) {
            for ( TimeSeriesBrowse tsb: parentTsbs ) {
                tsb.setTimeRange(dr);
            }
        }

        public DatumRange getTimeRange() {
            return parentTsbs.get(0).getTimeRange(); // TODO: this should probably be the union
        }

        public void setTimeResolution(Datum d) {
            for ( TimeSeriesBrowse tsb: parentTsbs ) {
                tsb.setTimeResolution(d);
            }
        }

        public Datum getTimeResolution() {
            return parentTsbs.get(0).getTimeResolution(); //TODO: this should probably be the coursest.
        }

        public String getURI() {
            Datum res= getTimeResolution();
            return this.uri + "?range="+getTimeRange() + ( res==null ? "" : "&resolution="+res );
        }

        
    }

    private synchronized void resolveParents() {
        if ( dsf.getUri()==null ) return; //TODO: remove
        URISplit split= URISplit.parse(dsf.getUri());
        if ( !dsf.getUri().startsWith("vap+internal:") ) {
            System.err.println("unbinding because this doesn't have parents.");
            unbind();
            return;
        }
        String[] ss = split.surl.split(",", -2);
        this.tsb= null; //TODO: who is listening?
        InternalTimeSeriesBrowse intTsb=null;
        for (int i = 0; i < ss.length; i++) {
            DataSourceFilter dsf = (DataSourceFilter) DomUtil.getElementById(dom, ss[i]);
            if ( dsf!=null ) {
                dsf.controller.addPropertyChangeListener(DataSourceController.PROP_FILLDATASET,parentListener);
                parentSources[i] = dsf;
                TimeSeriesBrowse parentTsb= dsf.controller.getTsb();
                if ( parentTsb!=null ) {
                    if ( intTsb==null ) {
                        intTsb= new InternalTimeSeriesBrowse(DataSourceController.this.dsf.getUri());
                    }
                    intTsb.addTimeSeriesBrowse(parentTsb);
                }
            }else {
                logger.log(Level.WARNING, "unable to find parent {0}", ss[i]);
                parentSources[i] = null;
            }
        }
        if ( intTsb!=null ) this.setTsb(intTsb);
        //System.err.println( Arrays.asList(parentSources));
    }

    private static Map maybeCopy( Map m ) {
        if ( m==null ) return new HashMap();
        return new HashMap(m);
    }

    /**
     * check to see if all the parent sources have updated and have
     * datasets that are compatible, so a new dataset can be created.
     * @return null if everything is okay, error message otherwise
     */
    private synchronized String checkParents() {

        QDataSet x;
        QDataSet y = null;
        QDataSet z = null;
        Map<String,Object> xprops=null,yprops=null,zprops=null;
        if ( parentSources==null ) return "no parent sources";
        if ( parentSources[0]==null ) return "first parent is null";
        x = parentSources[0].controller.getFillDataSet();
        xprops= maybeCopy( parentSources[0].controller.getFillProperties() );
        if (parentSources.length > 1) {
            if ( parentSources[1]==null ) return "second parent is null";
            y = parentSources[1].controller.getFillDataSet();
            yprops= maybeCopy( parentSources[1].controller.getFillProperties() );
        }
        if (parentSources.length > 2) {
            if ( parentSources[2]==null ) return "third parent is null";
            z = parentSources[2].controller.getFillDataSet();
            zprops= maybeCopy( parentSources[2].controller.getFillProperties() );
        }
        if ( parentSources.length==1 ) {
            if (x == null ) {
                return "parent dataset is null";
            }
            if ( DataSetUtil.validate(x, null ) ) {
                setDataSetInternal(x,xprops,this.dom.controller.isValueAdjusting());
            }
        } else if (parentSources.length == 2) {
            if (x == null || y == null) {
                return "first or second dataset is null";
            }
            ArrayDataSet yds = ArrayDataSet.copy(y);
            if (x != null) {
                yds.putProperty(QDataSet.DEPEND_0, x);
            }
            yprops.put(QDataSet.DEPEND_0, xprops);
            if ( DataSetUtil.validate(yds, null ) ) {
                setDataSetInternal(yds,yprops,this.dom.controller.isValueAdjusting());
            }
        } else if (parentSources.length == 3) {
            if (x == null || y == null || z == null) {
                return "at least one of the three datasets is null";
            }
            if (z.rank() == 1) {
                ArrayDataSet yds = ArrayDataSet.copy(y);
                yds.putProperty(QDataSet.DEPEND_0, x);
                yds.putProperty(QDataSet.PLANE_0, z);
                yprops.put(QDataSet.DEPEND_0, xprops );
                yprops.put(QDataSet.PLANE_0,zprops);
                if ( DataSetUtil.validate(yds, null ) ) {
                    setDataSetInternal(yds,yprops,this.dom.controller.isValueAdjusting());
                }
            } else {
                ArrayDataSet zds = ArrayDataSet.copy(z);
                if (x != null) {
                    zds.putProperty(QDataSet.DEPEND_0, x);
                }
                if (y != null) {
                    zds.putProperty(QDataSet.DEPEND_1, y);
                }
                zprops.put(QDataSet.DEPEND_0,xprops);
                zprops.put(QDataSet.DEPEND_1,yprops);
                if ( DataSetUtil.validate(zds, null ) ) {
                    setDataSetInternal(zds,zprops,this.dom.controller.isValueAdjusting());
                }
            }
        }
        return null;
    }
    
    PropertyChangeListener parentListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            String prob= checkParents();
            if ( prob!=null ) {
               setStatus("warning: "+prob );
            }
        }
    };

    PropertyChangeListener dsfListener= new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            resolveParents();
        }
    };

    
    /**
     * resolve a URI like vap+internal:data_0,data_1.
     * @param path
     * @return true if the resolution was successful.
     */
    private synchronized boolean doInternal(String path) {
        if ( parentSources!=null ) {
            for ( int i=0; i<parentSources.length; i++ ) {
                if ( parentSources[i]!=null ) parentSources[i].controller.removePropertyChangeListener(DataSourceController.PROP_FILLDATASET,parentListener);
            }
        }
        if ( path.trim().length()==0 ) return true;
        String[] ss = path.split(",", -2);
        parentSources = new DataSourceFilter[ss.length];
        resolveParents();
        String prob= checkParents();
        if ( prob!=null ) {
            setStatus("warning: "+prob);
            return false;
        }
        dom.addPropertyChangeListener( Application.PROP_DATASOURCEFILTERS, dsfListener );
        return true;
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
        Map<String, Object> props; // QDataSet properties.

        props = AutoplotUtil.extractProperties(getDataSet());
        if (getDataSource() != null) {
            props = AutoplotUtil.mergeProperties(getDataSource().getProperties(), props);
        }

        setProperties(props);

    }

    /**
     * look in the metadata for fill and valid range.
     * @param autorange
     * @param interpretMetadata
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
     * call updateFill in new thread
     * @param delay insert this delay so other threads may complete first.
     */
    private void updateFillSoon( final int delay ) {
        changesSupport.performingChange(this, PENDING_FILL_DATASET);
        Runnable run= new Runnable() {
            public void run() {
                if ( delay>0 ) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(DataSourceController.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                updateFill();
                changesSupport.changePerformed(this, PENDING_FILL_DATASET);
            }
        };
        if ( delay==0 ) {
            logger.finest("delay=0 means I should update fill in this thread");
            run.run();
        } else {
            RequestProcessor.invokeLater(run);
        }
    }

    /**
     * guess the cadence of the dataset tags, putting the rank 0 dataset cadence
     * into the property QDataSet.CADENCE of the tags ds.  fillDs is used to
     * identify missing values, which are skipped for the cadence guess.
     *
     * @param xds the tags for which the cadence is determined.
     * @param fillDs a dependent dataset possibly with fill values, or null.
     */
    private static void guessCadence( MutablePropertyDataSet xds, QDataSet fillDs ) {
        if ( xds.length()<2 ) return;

        RankZeroDataSet cadence = DataSetUtil.guessCadenceNew(xds, fillDs);
        if ( cadence!=null && "log".equals(cadence.property(QDataSet.SCALE_TYPE) ) ) {
            xds.putProperty( QDataSet.SCALE_TYPE, "log" );
        }
        if ( cadence!=null ) xds.putProperty(QDataSet.CADENCE, cadence);
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
     *     (which triggers plotting via PlotElement)
     */
    @SuppressWarnings("unchecked")
    private void updateFill() {
        changesSupport.performingChange(this, PENDING_FILL_DATASET);

        logger.fine("enter updateFill");

        if (getDataSet() == null) {
            return;
        }

        Map props = getProperties();

        MutablePropertyDataSet fillDs;

        int sliceDimension = dsf.getSliceDimension();
        int sliceIndex = dsf.getSliceIndex();

        boolean doSlice= ( sliceIndex>=0 && sliceDimension>=0  ); // kludge to support legacy datasets

        if ( doSlice && getDataSet().rank() == 3) { // plot element now does slicing

            QDataSet ds;
            QDataSet dep;

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
                reduceRankString = PlotElementUtil.describe(names.get(sliceDimension), dep, sliceIndex);
            }
            setReduceDataSetString(reduceRankString);

            props = MetadataUtil.sliceProperties(props, sliceDimension);

            if (dsf.isTranspose()) {
                ds = new TransposeRank2DataSet(ds);
                props = MetadataUtil.transposeProperties(props);
            }

            fillDs = DataSetOps.makePropertiesMutable(ds);

        } else {
            fillDs = DataSetOps.makePropertiesMutable(getDataSet());
            setReduceDataSetString(null);
        }

        // add the cadence property to each dimension of the dataset, so that
        // the plot element doesn't have to worry about it.
        for ( int i=0; i<fillDs.rank(); i++ ) {
            QDataSet dep= (QDataSet) fillDs.property("DEPEND_"+i);
            if ( dep!=null ) {
                dep= DataSetOps.makePropertiesMutable(dep);
                if ( i==0 ) {
                    guessCadence( (MutablePropertyDataSet) dep,fillDs);
                } else {
                    if ( dep.rank()==1 ) {
                        guessCadence( (MutablePropertyDataSet) dep,null);
                    } else if ( dep.rank()==2 && dep.length(0)>2 ) {
                        //guessCadence( (MutablePropertyDataSet) dep.slice(0),null);
                    }
                }
                fillDs.putProperty( "DEPEND_"+i, dep );
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
            ex.printStackTrace();
        }
        // check the dataset for fill data, inserting canonical fill values.
        AutoplotUtil.applyFillValidRange(fillDs, vmin, vmax, fill);

        setFillProperties(props);
        if (fillDs == getDataSet()) { //kludge to force reset renderer, because QDataSet is mutable.
            this.fillDataSet = null;
        }
        setFillDataSet(fillDs);
        changesSupport.changePerformed(this, PENDING_FILL_DATASET);
    }

    /**
     * do update on this thread, ensuring that only one data load is occurring at a
     * time.  Note if a dataSource doesn't check mon.isCancelled(), then processing
     * will block until the old load is done.
     */
    private synchronized void updateImmediately() {
        
        try {
            if (getDataSource() != null) {
                setStatus("busy: loading dataset");
                logger.log(Level.FINE, "loading dataset {0}", getDataSource());
                if ( tsb!=null ) {
                    logger.log(Level.FINE, "   tsb= {0}", tsb.getURI());
                }

                /*** here is the data load ***/
                loadDataSet();

                if ( dataSet!=null ) {
                    setStatus("done loading dataset");
                    if ( dsf.getUri()==null ) {
                        System.err.println("dsf.getUri was null");
                        return;
                    }
                } else {
                    if ( !dom.controller.getStatus().startsWith("warning:") ) setStatus("no data returned");
                }
            } else {
                if ( !(this.parentSources!=null) ) {
                    setDataSetInternal(null);
                } else {
                    String prob= checkParents();
                    if ( prob!=null ) {
                        System.err.println(prob);
                    }
                }
            }
            if ( dataSet!=null ) setStatus("ready");
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            setStatus("error: " + ex);
            model.getExceptionHandler().handleUncaught(ex);
        }

    }
    private Updating updating;
    private PropertyChangeListener updatesListener = new PropertyChangeListener() {

        public void propertyChange(PropertyChangeEvent evt) {
            QDataSet ds = (QDataSet) evt.getNewValue();
            if (ds != null) {
                setDataSetInternal(ds);
            } else {
                List<PlotElement> pelements = dom.controller.getPlotElementsFor(dsf);
                for ( PlotElement p: pelements )  {
                    p.getController().setResetRanges(true);
                }
                update(true, true);
            }
        }
    };

    /**
     * cancel the loading process. 
     */
    public void cancel() {
        DataSource dss= this.dataSource;  // Note not getDataSource, which is synchronized.
        if ( dss != null && dss.asynchronousLoad() && !dom.controller.isHeadless()) {
            ProgressMonitor monitor= mon;
            if (monitor != null) {
                logger.info("cancel running request");
                monitor.cancel();
            }
        }
    }
    
    /**
     * update the model and view using the new DataSource to create a new dataset,
     * then inspecting the dataset to decide on axis settings.
     *
     * NOTE autorange and interpretMeta have no effect!
     */
    public synchronized void update(final boolean autorange, final boolean interpretMeta) {
        changesSupport.performingChange(this, PENDING_UPDATE);

        setDataSet(null);

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

        if (getDataSource() != null && getDataSource().asynchronousLoad() && !dom.controller.isHeadless()) {
            // this code will never be invoked because this is synchronous.  See cancel().
            logger.fine("invoke later do load");
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
    
    public synchronized DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        DataSource oldDataSource = null;
        synchronized ( this ) {
            oldDataSource= this.dataSource;
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

    public void setDataSet(QDataSet dataSet) {
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
    public static final String PROP_HISTOGRAM = "histogram";

    public QDataSet getHistogram() {
        return histogram;
    }

    public void setHistogram(QDataSet histogram) {
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
    protected String reduceDataSetString = null;
    public static final String PROP_REDUCEDATASETSTRING = "reduceDataSetString";

    public String getReduceDataSetString() {
        return reduceDataSetString;
    }

    public void setReduceDataSetString(String reduceDataSetString) {
        String oldReduceDataSetString = this.reduceDataSetString;
        this.reduceDataSetString = reduceDataSetString;
        propertyChangeSupport.firePropertyChange(PROP_REDUCEDATASETSTRING, oldReduceDataSetString, reduceDataSetString);
    }

    /**
     * load the data set from the DataSource.
     */
    private synchronized QDataSet loadDataSet() {

        ProgressMonitor mymon;

        QDataSet result = null;
        mymon = getMonitor("loading data", "loading " + getDataSource());
        this.mon = mymon;
        try {
            result = getDataSource().getDataSet(mymon);
            if ( dsf.getUri()!=null ) this.model.addRecent(dsf.getUri());
            logger.log( Level.FINE, "{0} read dataset: {1}", new Object[]{this.getDataSource(), result});
            Map<String,Object> props= getDataSource().getMetadata(new NullProgressMonitor());
            setDataSetInternal(result,props,dom.controller.isValueAdjusting());
            // look again to see if it has timeSeriesBrowse now--JythonDataSource
            if ( getTsb()==null && getDataSource().getCapability( TimeSeriesBrowse.class ) !=null ) {
                TimeSeriesBrowse tsb= getDataSource().getCapability( TimeSeriesBrowse.class );
                PlotElement pe= getPlotElement();
                if ( pe!=null && this.doesPlotElementSupportTsb( pe ) ) {  //TODO: less flakey
                    setTsb(tsb);
                    timeSeriesBrowseController = new TimeSeriesBrowseController(this,pe);
                    timeSeriesBrowseController.setup(false);
                }
            }
        //embedDsDirty = true;
        } catch (InterruptedIOException ex) {
            setException(ex);
            setDataSet(null);
            setStatus("interrupted");
            if ( dsf.getUri()!=null ) this.model.addException( dsf.getUri(), ex );
        } catch (CancelledOperationException ex) {
            setException(ex);
            setDataSet(null);
            setStatus("operation cancelled");
            if ( dsf.getUri()!=null ) this.model.addException( dsf.getUri(), ex );
        } catch (NoDataInIntervalException e) {
            setException(e);
            setDataSet(null);
            setStatus("warning: "+ e.getMessage());

            if ( dsf.getController().getTsb()==null ) {
                String title= "no data in interval";
                model.showMessage( "warning: "+ e.getMessage(), title, JOptionPane.WARNING_MESSAGE );
            } else {
                // do nothing.
            }

        } catch (IOException e ) {
            if ( e instanceof FileNotFoundException || e.getMessage().contains("No such file") || e.getMessage().contains("timed out") ) {
                String message= e.getMessage();
                if ( message.startsWith("550 ") ) {
                    message= message.substring(4);
                }
                setException(e);
                setDataSet(null);
                setStatus("warning: " + message);
                String title= e.getMessage().contains("No such file") ? "File not found" : e.getMessage();
                model.showMessage( message, title, JOptionPane.WARNING_MESSAGE );
            } else if ( e.getMessage().contains("root does not exist") ) {  // bugfix 3053225
                setException(e);
                setDataSet(null);
                setStatus("warning: " + e.getMessage() );
                String title= e.getMessage().contains("No such file") ? "Root does not exist" : e.getMessage();
                model.showMessage( e.getMessage(), title, JOptionPane.WARNING_MESSAGE );
            } else {
                setException(e);
                setDataSet(null);
                setStatus("error: " + e.getMessage());
                handleException(e);
            }
            if ( dsf.getUri()!=null ) this.model.addException( dsf.getUri(), e );
        } catch (Exception e) {
            setException(e);
            setDataSet(null);
            setStatus("error: " + e.getMessage());
            handleException(e);
            if ( dsf.getUri()!=null ) this.model.addException( dsf.getUri(), e );
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
        setUriNeedsResolution(true);
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
        Caching cache1 = getCaching();

        String surl = dsf.getUri();
        if (surl == null) {
            resetDataSource(valueWasAdjusting,null);
            setUriNeedsResolution(false);
            setDataSetNeedsLoading(false);
        } else {
            URISplit split = URISplit.parse(surl);
            surl = URISplit.format(split);

            try {
                mon.started();
                mon.setProgressMessage("getting " + surl);

                if (cache1 != null) {
                    if (cache1.satisfies(surl)) {
                        cache1.resetURI(surl);
                        //trigger autorange
                        propertyChangeSupport.firePropertyChange(PROP_DATASOURCE, null, dataSource);
                        update(true, true);
                        return;
                    }
                }

                if ( URISplit.implicitVapScheme(split).equals("vap+internal")) {
                    resetDataSource(valueWasAdjusting,null);
                    boolean ok= doInternal(split.path);
                    String msg=null;
                    if ( !ok )  msg= dom.controller.getStatus();
                    //resetDataSource(valueWasAdjusting,null);
                    if ( !ok )  dom.controller.setStatus(msg);
                } else {
                    DataSource source = DataSetURI.getDataSource(surl);
                    clearParentSources();
                    resetDataSource(valueWasAdjusting,source);
                }
                setUriNeedsResolution(false);
                
                mon.setProgressMessage("done getting data source");

            } catch (Exception e) {
                e.printStackTrace();
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
     * true if the data source is changed and we need to reset the dimension names when
     * we get our first data set.
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


    @Override
    public boolean isPendingChanges() {
        TimeSeriesBrowseController tsbc= timeSeriesBrowseController;
        if (tsbc != null && tsbc.isPendingChanges()) {
            return true;
        }
        return super.isPendingChanges();
    }

    private void handleException(Exception e) {
        if ( model.getExceptionHandler()==null ) {
            e.printStackTrace();
        } else if ( e.getMessage()!=null && e.getMessage().contains("nsupported protocol") ) { //unsupport protocol
            model.showMessage( e.getMessage(), "Unsupported Protocol", JOptionPane.ERROR_MESSAGE );
        } else {
            model.getExceptionHandler().handle(e);
        }
    }

    /**
     * return the first plot element that is using this data source.
     * @return null or a plot element.
     */
    private PlotElement getPlotElement() {
        List<PlotElement> pele = dom.controller.getPlotElementsFor(dsf);
        if (pele.size() == 0) {
            return null;
        } else {
            return pele.get(0);
        }

    }

    private ProgressMonitor getMonitor(String label, String description) {
        PlotElement pele = getPlotElement();
        DasPlot p = null;
        if (pele != null) {
            Plot plot = dom.controller.getPlotFor(pele);
            if (plot != null) {
                p = plot.controller.getDasPlot();
            }
        }
        if (p != null) {
            return dom.controller.getMonitorFactory().getMonitor(p, label, description);
        } else {
            return dom.controller.getMonitorFactory().getMonitor(label, description);
        }

    }

    private void setStatus(String string) {
        dom.controller.setStatus(string);
    }

    @Override
    public String toString() {
        return this.dsf + " controller";
    }
}
