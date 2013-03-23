/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.awt.BorderLayout;
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
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.HtmlResponseIOException;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.capability.Caching;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.datasource.capability.Updating;
import org.virbo.dsutil.AutoHistogram;

/**
 * Controller node manages a DataSourceFilter node.
 * @author jbf
 */
public class DataSourceController extends DomNodeController {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger( "autoplot.dom.dsc" );

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
            if ( dataSet != null ) {
                updateFill();// this should be done quickly.  Some filters (ffts) are sub-interactive and should not be done here.
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
                // do nothing
            } else {
                List<Object> whoIsChanging= changesSupport.whoIsChanging( PENDING_SET_DATA_SOURCE );
                if ( whoIsChanging.size()>0 ) {
                    logger.log(Level.WARNING, "!!! someone is changing: {0} !!!  ignoring event.", whoIsChanging); // we probably need to do something with this.
                    logger.log(Level.WARNING, " !! {0}", e.getPropertyName());
                    logger.log(Level.WARNING, " !! {0}", e.getNewValue());
                    logger.log(Level.WARNING, " !! {0}", e.getOldValue());
                    return;
                }
                DataSourceController.this.changesSupport.registerPendingChange( resetMePropertyChangeListener, PENDING_RESOLVE_DATA_SOURCE );
                setUriNeedsResolution(true);
                if (!dom.controller.isValueAdjusting()) {
                    DataSourceController.this.changesSupport.performingChange( resetMePropertyChangeListener, PENDING_RESOLVE_DATA_SOURCE );
                    resolveDataSource(false,getMonitor("resetting data source", "resetting data source"));
                    DataSourceController.this.changesSupport.changePerformed( resetMePropertyChangeListener, PENDING_RESOLVE_DATA_SOURCE );
                } else {
                    new RunLaterListener(ChangesSupport.PROP_VALUEADJUSTING, dom.controller, true ) {
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

    /**
     * true if we have vap+internal and we have already checked for parent TSBs.
     */
    private boolean haveCheckedInternalTsb= false;

    private static final String PENDING_DATA_SOURCE = "dataSource";
    private static final String PENDING_RESOLVE_DATA_SOURCE = "resolveDataSource";
    private static final String PENDING_SET_DATA_SOURCE = "setDataSource"; //we are setting the datasource, so don't try to resolve, etc.
    private static final String PENDING_FILL_DATASET = "fillDataSet";
    private static final String PENDING_UPDATE = "update";

    public DataSourceController(ApplicationModel model, DataSourceFilter dsf) {
        super( dsf );
        
        this.model = model;
        this.dom = model.getDocumentModel();
        //this.changesSupport = new ChangesSupport(this.propertyChangeSupport, this);
        this.dsf = dsf;

        //dsf.addPropertyChangeListener(DataSourceFilter.PROP_SLICEDIMENSION, updateSlicePropertyChangeListener);
        //dsf.addPropertyChangeListener(DataSourceFilter.PROP_SLICEINDEX, updateSlicePropertyChangeListener);
        //dsf.addPropertyChangeListener(DataSourceFilter.PROP_TRANSPOSE, updateSlicePropertyChangeListener);
        dsf.addPropertyChangeListener( DataSourceFilter.PROP_FILTERS, updateSlicePropertyChangeListener );

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
        if (sliceDimension==-1 ) { // rank 0
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
        Plot plot= p.getController().getApplication().getController().getPlotFor(p);
        if ( plot==null ) return false;
        if ( UnitsUtil.isTimeLocation( plot.getXaxis().getRange().getUnits() )
                || UnitsUtil.isTimeLocation( plot.getContext().getUnits() ) ) return true;
        //return false;
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

        if ( haveCheckedInternalTsb ) {
            haveCheckedInternalTsb= false;
        }
        
        if (dataSource == null) {
            setCaching(null);
            setTsb(null);
            setTsbSuri(null);
            if ( dsf.getUri().length()>0 && !dsf.getUri().startsWith("vap+internal" ) ) {
                dsf.setUri("vap+internal:"); //TODO: when is this supposed to happen?  Test033 is hitting here.
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
                    List<PlotElement> pele = dom.controller.getPlotElementsFor(dsf);
                    pe= null;
                    for ( int i=0; i<pele.size(); i++ ) {
                        if ( doesPlotElementSupportTsb( pele.get(i) ) ) {
                            pe= pele.get(i);
                        }
                    }
                    if ( pe!=null ) {
                        setTsb(dataSource.getCapability(TimeSeriesBrowse.class));
                    } else {
                        setTsb(null);
                    }
                }
            }
            if ( dsf.getUri().length()>0 ) {
                dsf.setUri( dataSource.getURI() );
                setUriNeedsResolution(false);
            }
            changesSupport.changePerformed( this, PENDING_SET_DATA_SOURCE );
       }

        if ( valueWasAdjusting ) {
            this.dataSource = dataSource;
        } else {
            this.dsf.setValidRange("");
            this.dsf.setFill("");
            setDataSource(dataSource);
            setResetDimensions(true);
        }

        if (oldSource == null || !oldSource.equals(dataSource)) {
            List<PlotElement> ps = dom.controller.getPlotElementsFor(dsf);
            if ( getTsb() != null && ps.isEmpty() ) {
                setDataSet(null);
                if (ps.size() > 0) {
                    timeSeriesBrowseController = new TimeSeriesBrowseController(this,ps.get(0));
                    timeSeriesBrowseController.setup(valueWasAdjusting);
                }
            } else if ( getTsb()!=null && ps.isEmpty() ) {
                timeSeriesBrowseController = new TimeSeriesBrowseController(this,null);

                DomNode node1;
                String propertyName;

                Plot p= dom.controller.getFirstPlotFor(dsf);
                if ( p==null ) {
                    node1= dom;
                    propertyName= Application.PROP_TIMERANGE;
                } else {
                    node1= p;
                    propertyName= Plot.PROP_CONTEXT;
                }

                if ( !UnitsUtil.isTimeLocation( this.dom.getTimeRange().getUnits() ) ) {
                    List<BindingModel> bms= this.dom.getController().findBindings( this.dom, Application.PROP_TIMERANGE, null, null );
                    if ( bms==null || bms.isEmpty() ) {
                        logger.log(Level.FINE, "claiming dom timerange for TSB: {0}", this.dsf.getUri());
                        if ( p!=null ) p.setContext( getTsb().getTimeRange() );
                        this.dom.setTimeRange( getTsb().getTimeRange() );
                        logger.log(Level.FINE, "about to setup Gen for {0}", this);
                        timeSeriesBrowseController.setupGen( node1, propertyName );
                        if ( node1!=dom ) {
                            dom.controller.bind( dom, Application.PROP_TIMERANGE, p, Plot.PROP_CONTEXT );
                        }
                        update( );
                    } else {
                        logger.fine("unable to use timerange as guide");
                        if ( p!=null ) p.setContext( getTsb().getTimeRange() );
                        timeSeriesBrowseController.setupGen( node1, propertyName );
                        update( );
                    }
                } else {
                    logger.log(Level.FINE, "using plot context for TSB: {0}", this.dsf.getUri());
                    timeSeriesBrowseController.setupGen( node1, propertyName );
                    if ( node1!=dom ) {
                        if ( p!=null ) {
                            dom.controller.bind( dom, Application.PROP_TIMERANGE, p, Plot.PROP_CONTEXT );
                            BindingModel bm= dom.controller.findBinding( dom, Application.PROP_TIMERANGE, p.getXaxis(), Axis.PROP_RANGE );
                            //TODO: verify this! https://sourceforge.net/tracker/index.php?func=detail&aid=3516161&group_id=199733&atid=970682
                            if ( bm!=null ) dom.controller.deleteBinding(bm);
                        }
                    }
                    update( );
                }

            } else {
                update( );
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
            StringBuilder message = new StringBuilder("data set is invalid:\n");
            logger.log( Level.SEVERE, null, new Exception("data set is invalid") );
            for (String s : problems) {
                message.append(s).append("\n");
            }
            if (dom.controller.isHeadless()) {
                throw new IllegalArgumentException(message.toString());
            } else {
                this.model.showMessage( message.toString(), "Data Set is Invalid", JOptionPane.WARNING_MESSAGE );
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
            if ( pele.isEmpty() ) {
                setStatus("warning: done loading data but no plot elements are listening");
            }


        }
    }
    
    DataSourceFilter[] parentSources;

    /**
     * return the parent sources of data, which may contain null if the reference is not found.
     * @return
     */
    protected synchronized DataSourceFilter[] getParentSources() {
        if ( parentSources==null ) {
            return new DataSourceFilter[0];
        } else {
            DataSourceFilter[] parentSources1= new DataSourceFilter[parentSources.length];
            System.arraycopy(parentSources, 0, parentSources1, 0, parentSources.length );
            return parentSources1;
        }
    }

    /**
     * removes the parentSources link, and listeners to the parents.  The
     * parents are left in the DOM and will be removed later.
     */
    private synchronized void clearParentSources() {
        if ( this.parentSources!=null ) {
            for ( DataSourceFilter parentDsf: parentSources ) {
                if ( parentDsf!=null ) parentDsf.controller.removePropertyChangeListener(DataSourceController.PROP_FILLDATASET, parentListener);
            }
        }
        this.parentSources= null; 

    }

    /**
     * if the internal dataset points to DSF's with TimeSeriesBrowse, then add our
     * own TSB.
     */
    private synchronized void maybeAddInternalTimeSeriesBrowse() {

        if ( this.haveCheckedInternalTsb ) {
            return;
        }

        String uri= dsf.getUri();
        if ( uri==null ) {
            return; // when does this happen?  reset?
        }
        URISplit split= URISplit.parse(uri);

        String[] ss = split.surl.split(",", -2);
        this.tsb= null; //TODO: who is listening?

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
                    logger.log( Level.WARNING, "adding to internal tsb: {0}", parentTsb);
                    intTsb.addTimeSeriesBrowse(parentTsb);
                }
            } else {
                logger.log(Level.WARNING, "unable to find parent {0}", ss[i]);
                if ( parentSources==null ) {
                    logger.warning("strange case where parent sources is not resolved."); //TODO: looks like we can get here by adding scatter plot of two tsbs, then replace with demo 1.
                    return; //https://sourceforge.net/tracker/?func=detail&aid=3516161&group_id=199733&atid=970682
                }
                parentSources[i] = null;
            }
        }
        if (intTsb != null) {
            this.setTsb(intTsb);
            this.timeSeriesBrowseController= new TimeSeriesBrowseController( this, null );
            // find the plot that will control this.  Better not plot this twice!
            Plot p= getApplication().getController().getFirstPlotFor(dsf);
            if ( p!=null ) {
                this.timeSeriesBrowseController.setupGen( p, Plot.PROP_CONTEXT );
            } else {
                logger.warning("check into this case, shouldn't happen");
            }
        }
        haveCheckedInternalTsb= true;
    }

    /**
     * Introduced to support children that are TSBs.  All are assumed to be the same, the first is used for the getter.
     */
    class InternalTimeSeriesBrowse implements TimeSeriesBrowse {

        String uri;
        DatumRange timerange;

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
            this.timerange= dr;
            checkParents();
        }

        public DatumRange getTimeRange() {
            return timerange;
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

        @Override
        public String toString() {
            Datum res= getTimeResolution();
            return "inttsb: "+getTimeRange()+" " +( res==null ? "" : "&resolution="+res );
        }

        public void setURI(String suri) throws ParseException {
            throw new IllegalArgumentException("not implemented");
        }
        
    }

    private synchronized void resolveParents() {
        if ( dsf.getUri().length()==0 ) return; //TODO: remove
        URISplit split= URISplit.parse(dsf.getUri()); 
        if ( !dsf.getUri().startsWith("vap+internal:") ) {
            logger.fine("unbinding because this doesn't have parents.");
            unbind();
            return;
        }
 	String[] ss = split.surl.split(",", -2);
 	for (int i = 0; i < ss.length; i++) {
            DataSourceFilter dsf1 = (DataSourceFilter) DomUtil.getElementById(dom, ss[i]);
            if ( dsf1!=null ) {
                dsf1.controller.addPropertyChangeListener(DataSourceController.PROP_FILLDATASET,parentListener);
                parentSources[i] = dsf1;
            }else {
                logger.log(Level.WARNING, "unable to find parent {0}", ss[i]);
                parentSources[i] = null;
            }
 	}
    }

    private static Map maybeCopy( Map m ) {
        if ( m==null ) return new HashMap();
        return new HashMap(m);
    }

    /**
     * check to see if all the parent sources have updated and have
     * datasets that are compatible, so a new dataset can be created.
     * If there is a TimeSeriesBrowse on this, then attempt to trim the data
     * to the TimeSeriesBrowse.getTimeRange().
     * @return null if everything is okay, error message otherwise
     */
    private synchronized String checkParents() {

        QDataSet x;
        QDataSet y = null;
        QDataSet z = null;
        Map<String,Object> xprops=null,yprops=null,zprops=null;

        QDataSet ds=null;
        Map<String,Object> props=null;

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
                ds= x;
                props= xprops;
            }
        } else if (parentSources.length == 2) {
            if (x == null || y == null) {
                return "first or second dataset is null";
            }
            ArrayDataSet yds = ArrayDataSet.copy(y);
            if ( DataSetUtil.validate(x,yds,null) ) {
                yds.putProperty(QDataSet.DEPEND_0, x);
                yprops.put(QDataSet.DEPEND_0, xprops);
                if ( DataSetUtil.validate(yds, null ) ) {
                    ds= yds;
                    props= yprops;
                }
            } else {
                logger.fine("intermediate state where y and x have different lengths");
            }
        } else if (parentSources.length == 3) {
            if (x == null || y == null || z == null) {
                return "at least one of the three datasets is null";
            }
            if (z.rank() == 1) {
                ArrayDataSet yds = ArrayDataSet.copy(y);
                yds.putProperty(QDataSet.RENDER_TYPE,null);
                yds.putProperty(QDataSet.DEPEND_0, x);
                yds.putProperty(QDataSet.PLANE_0, z);
                yprops.put(QDataSet.DEPEND_0, xprops );
                yprops.put(QDataSet.PLANE_0,zprops);
                if ( DataSetUtil.validate(yds, null ) ) { //TODO: link should and probably does work here
                    ds= yds;
                    props= yprops;
                }
            } else {
                ArrayDataSet zds = ArrayDataSet.copy(z);
                zds.putProperty(QDataSet.DEPEND_0, x);
                zds.putProperty(QDataSet.DEPEND_1, y);
                if ( DataSetUtil.validate( x, y, z, null ) ) {
                    zprops.put(QDataSet.DEPEND_0,xprops);
                    zprops.put(QDataSet.DEPEND_1,yprops);
                    ds= zds;
                    props= zprops;
                }
            }
        }
        if ( ds!=null ) {
            //TODO: TSB trim dataset.  It's not clear to me that this should be implemented here, but we will for now.
            if ( this.tsb!=null && this.tsb instanceof InternalTimeSeriesBrowse ) {
                QDataSet xds= (QDataSet) ds.property(QDataSet.DEPEND_0);
                if ( xds!=null ) {
                    QDataSet xxds= (QDataSet) xds.property(QDataSet.DEPEND_0);
                    if ( xxds!=null && UnitsUtil.isTimeLocation( SemanticOps.getUnits( xxds ) ) && SemanticOps.isMonotonic( xxds ) ) { //okay we can trim
                        DatumRange dr= this.tsb.getTimeRange();
                        int idx0= DataSetUtil.getPreviousIndex( xxds, dr.min() );
                        int idx1= DataSetUtil.getNextIndex( xxds, dr.max() );
                        if ( idx0==idx1 ) {
                            setDataSetInternal(null);
                        } else if ( idx0>idx1 ) {
                            logger.warning("non mono error?");
                            setDataSetInternal(null);
                        } else {
                            QDataSet trim= ds.trim( idx0, idx1 );
                            ds= trim;
                        }
                    }
                }
            }
            setDataSetInternal(ds,props,this.dom.controller.isValueAdjusting());
        }
        return null;
    }
    
    PropertyChangeListener parentListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            String prob= checkParents();
            if ( prob!=null ) {
               setStatus("warning: "+prob );
               setDataSetInternal(null,null,dom.controller.isValueAdjusting());
            }
            if ( DataSourceController.this.haveCheckedInternalTsb==false ) {
                maybeAddInternalTimeSeriesBrowse();
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
                try {
                    if ( delay>0 ) {
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ex) {
                            logger.log(Level.SEVERE, null, ex);
                        }
                    }
                    updateFill();
                } finally {
                    changesSupport.changePerformed(this, PENDING_FILL_DATASET);
                }
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

        logger.fine("enter updateFill");

        if (getDataSet() == null) {
            return;
        }

        changesSupport.performingChange(this, PENDING_FILL_DATASET);
        try {

            Map props = getProperties();

            MutablePropertyDataSet fillDs;

            String filters= dsf.getFilters();

            boolean doSlice= filters.length()>0;

            if ( doSlice ) { // plot element now does slicing, but we can do it here as well.

                QDataSet ds;
                if ( DataSetOps.isProcessAsync(filters) ) {
                    logger.warning("asynchronous processes not supported here");
                    setReduceDataSetString(null);
                    ds= getDataSet();
                } else {
                    try {
                        ds= DataSetOps.sprocess( filters, getDataSet(), new NullProgressMonitor() );
                        setReduceDataSetString(filters);
                    } catch ( Exception ex ) {
                        setException(ex);
                        throw new RuntimeException(ex);
                    }
                }

                //TODO: must we process the props as well?
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
                logger.log( Level.SEVERE, "", ex );
            }
            // check the dataset for fill data, inserting canonical fill values.
            AutoplotUtil.applyFillValidRange(fillDs, vmin, vmax, fill);

            setFillProperties(props);
            if (fillDs == getDataSet()) { //kludge to force reset renderer, because QDataSet is mutable.
                this.fillDataSet = null;
            }
            setFillDataSet(fillDs);
        } finally {
            changesSupport.changePerformed(this, PENDING_FILL_DATASET);
        }
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
                    if ( dsf.getUri().length()==0 ) {
                        logger.warning("dsf.getUri was null");
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
                        logger.warning(prob);
                    }
                }
            }
            if ( dataSet!=null ) setStatus("ready");
        } catch (RuntimeException ex) {
            logger.log( Level.SEVERE, "", ex );
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
                update();
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
                logger.fine("cancel running request");
                monitor.cancel();
            }
        }
    }
    
    /**
     * support legacy.  reloadAllUris.jy calls this.
     * @param x
     * @param y
     */
    public synchronized void update( final boolean autorange, final boolean interpretMeta ) {
        update();
    }

    /**
     * update the model and view using the new DataSource to create a new dataset,
     * then inspecting the dataset to decide on axis settings.
     *
     */
    public synchronized void update() {
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
            
            @Override
            public String toString() {
                return "load "+dataSource.toString();
            }
        };

        if (getDataSource() != null && getDataSource().asynchronousLoad() && !dom.controller.isHeadless()) {
            // this code will never be invoked because this is synchronous.  See cancel().
            logger.fine("invoke later do load");
            if (mon != null) {
                logger.warning("double load!");
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

    /**
     * return the controller's current datasource.  This was synchronized, but
     * this would mean that external clients could not query what the current source
     * was.  Since this is only reading the variable, this seems harmless.
     * Note, findbugs prompted the code change, not an observed bug.
     * TODO: there is probably a better way to do this, synchronizing properly on
     * several objects.
     *
     * @return
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        DataSource oldDataSource;
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
        dsf.setFilters("");
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

            // Call the data source to get the data set.
            result = getDataSource().getDataSet(mymon);

            if ( dsf.getUri().length()>0 ) this.model.addRecent(dsf.getUri());
            logger.log( Level.FINE, "{0} read dataset: {1}", new Object[]{this.getDataSource(), result});
            Map<String,Object> props= getDataSource().getMetadata(new NullProgressMonitor());

            if ( result!=null && getTsb()!=null && !UnitsUtil.isTimeLocation( SemanticOps.getUnits( SemanticOps.xtagsDataSet(result)) ) ) {
                // we had turned off the autoranging, but turns out we need to turn it back on.
                if ( timeSeriesBrowseController.domPlot==null ) {
                    logger.warning("unexpected timeSeriesBrowseController.domPlot==null");
                } else {
                    timeSeriesBrowseController.domPlot.getXaxis().setAutoRange(true);
                }
            }

            setDataSetInternal(result,props,dom.controller.isValueAdjusting());

            // look again to see if it has timeSeriesBrowse now--JythonDataSource
            if ( getTsb()==null && getDataSource().getCapability( TimeSeriesBrowse.class ) !=null ) {
                TimeSeriesBrowse tsb1= getDataSource().getCapability( TimeSeriesBrowse.class );
                PlotElement pe= getPlotElement();
                if ( pe!=null && this.doesPlotElementSupportTsb( pe ) ) {  //TODO: less flakey
                    setTsb(tsb1);
                    timeSeriesBrowseController = new TimeSeriesBrowseController(this,pe);
                    timeSeriesBrowseController.setup(false);
                }
            }
        //embedDsDirty = true;
        } catch (InterruptedIOException ex) {
            setException(ex);
            setDataSet(null); //TODO: maybe we should allow the old dataset to stay, in case TSB....
            setStatus("interrupted");
            if ( dsf.getUri().length()>0 ) this.model.addException( dsf.getUri(), ex );
        } catch (CancelledOperationException ex) {
            setException(ex);
            setDataSet(null);
            setStatus("operation cancelled");
            if ( dsf.getUri().length()>0 ) this.model.addException( dsf.getUri(), ex );
        } catch (NoDataInIntervalException ex) {
            setException(ex);
            setDataSet(null);
            setStatus("warning: "+ ex.getMessage());

            if ( dsf.getController().getTsb()==null ) {
                String title= "no data in interval";
                model.showMessage( ""+ ex.getMessage(), title, JOptionPane.INFORMATION_MESSAGE );
            } else {
                // do nothing.
            }
            if ( dsf.getUri().length()>0 ) this.model.addException( dsf.getUri(), ex );
        } catch (HtmlResponseIOException ex ) {
            final HtmlResponseIOException htmlEx= (HtmlResponseIOException)ex;
            if ( htmlEx.getURL()!=null ) {
                final String link= htmlEx.getURL().toString();
                final JPanel p= new JPanel( new BorderLayout( ) );
                p.add( new JLabel(  "<html>Unable to open URI: <br>" +  dsf.getUri()+"<br><br>Downloaded file appears to be HTML.<br><a href=\""+link+"\">"+link+"</a><br>" ), BorderLayout.CENTER );
                JPanel p1= new JPanel( new FlowLayout( FlowLayout.RIGHT ) );
                p1.add( new JButton( new AbstractAction("Details") {
                    public void actionPerformed( ActionEvent ev ) {
                        getApplication().controller.getApplicationModel().getExceptionHandler().handle(htmlEx);
                    }
                }) );
                p1.add( new JButton( new AbstractAction("View Page") {
                    public void actionPerformed( ActionEvent ev ) {
                        AutoplotUtil.openBrowser(link);
                    }
                }) );
                p.add( p1, BorderLayout.SOUTH );
                JOptionPane.showMessageDialog( DataSourceController.this.model.getCanvas(), p );
            } else {
                JOptionPane.showMessageDialog( DataSourceController.this.model.getCanvas(), "<html>Unable to open URI: <br>" +  dsf.getUri()+"<br><br>"+ex );
            }
            if ( dsf.getUri().length()>0 ) this.model.addException( dsf.getUri(), ex );
            
        } catch (IOException ex ) {
            if ( ex instanceof FileNotFoundException || ( ex.getMessage()!=null && ( ex.getMessage().contains("No such file") || ex.getMessage().contains("timed out") ) ) ) {
                String message= ex.getMessage();
                if ( message.startsWith("550 ") ) {
                    message= message.substring(4);
                }
                setException(ex);
                setDataSet(null);
                setStatus("warning: " + message);
                String title= ex.getMessage().contains("No such file") ? "File not found" : ex.getMessage();
                if ( message.contains( org.virbo.aggregator.AggregatingDataSource.MSG_NO_FILES_FOUND ) ) {
                    // this implies that there are files in other intervals, so don't have popup
                } else {
                    model.showMessage( message, title, JOptionPane.WARNING_MESSAGE );
                }
            } else if ( ex.getMessage()!=null && ex.getMessage().contains("root does not exist") ) {  // bugfix 3053225
                setException(ex);
                setDataSet(null);
                setStatus("warning: " + ex.getMessage() );
                String title= ex.getMessage().contains("No such file") ? "Root does not exist" : ex.getMessage();
                model.showMessage( ex.getMessage(), title, JOptionPane.WARNING_MESSAGE );
            } else if ( ex.getMessage()==null  ) {
                setException(ex);
                logger.log( Level.WARNING, null, ex );
                setDataSet(null);
                setStatus("error: " + ex.getClass() );
                handleException(ex);
            } else {
                setException(ex);
                setDataSet(null);
                setStatus("error: " + ex.getMessage());
                handleException(ex);
            }
            if ( dsf.getUri().length()>0 ) this.model.addException( dsf.getUri(), ex );
        } catch (Exception ex) {
            setException(ex);
            setDataSet(null);
            logger.log( Level.WARNING, null, ex );
            setStatus("error: " + ex.getMessage());
            handleException(ex);
            if ( dsf.getUri().length()>0 ) this.model.addException( dsf.getUri(), ex );
        } finally {
            // don't trust the data sources to call finished when an exception occurs.
            mymon.finished();
            if (mymon == this.mon) {
                this.mon = null;
            } else {
                logger.warning("not my mon, somebody better delete it!");
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
        suri= URISplit.makeCanonical(suri);
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
        suri= URISplit.makeCanonical(suri);
        if (  old.length()>0 && old.equals(suri)) {
            dsf.setUri("");
        }
        setSuri(suri, mon);
    }

    /**
     * Preconditions: 
     *   dsf.getUri is set.
     *   Any or no datasource is set.
     *   dom.getController().isValueAdjusting() is false
     * Postconditions: 
     *   A dataSource object is created 
     *   dsf._getDataSource returns the data source.
     *   any parents the dataSource had (vap+internal:data_0,data_1) are deleted.
     *   A thread has been started that will load the dataset.
     * Side Effects:
     *   update is called to start the download, unless 
     *   if this is headless, then the dataset has been loaded synchronously.
     */
    private void resolveDataSource( boolean valueWasAdjusting, ProgressMonitor mon ) {
        Caching cache1 = getCaching();

        if ( dom.getController().isValueAdjusting() ) {
            logger.log( Level.WARNING, "return of bug first demoed by test033: where the adjusting property is breifly cleared. {0}", dom.getController().changesSupport.isValueAdjusting());
            logger.warning( "See https://sourceforge.net/tracker/?func=detail&aid=3409414&group_id=199733&atid=970682");
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
        String surl = dsf.getUri();
        if ( surl.length()==0 ) {
            getApplication().getController().deleteAnyParentsOfDataSourceFilter(dsf);
            clearParentSources();
            resetDataSource(valueWasAdjusting,null);
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
                        propertyChangeSupport.firePropertyChange(PROP_DATASOURCE, null, dataSource);
                        update();
                        return;
                    }
                }

                if ( URISplit.implicitVapScheme(split).equals("vap+internal")) {
                    clearParentSources();
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
                logger.log( Level.WARNING, null, e );
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

    @Override
    public void pendingChanges(Map<Object, Object> changes) {
        super.pendingChanges(changes);
        TimeSeriesBrowseController tsbc= timeSeriesBrowseController;
        if (tsbc != null && tsbc.isPendingChanges()) {
            tsbc.pendingChanges(changes);
        }
    }



    private void handleException(Exception e) {
        if ( model.getExceptionHandler()==null ) {
            logger.log( Level.WARNING, null, e );
        } else if ( e.getMessage()!=null && e.getMessage().contains("nsupported protocol") ) { //unsupport protocol
            model.showMessage( e.getMessage(), "Unsupported Protocol", JOptionPane.ERROR_MESSAGE );
        } else {
            logger.log(Level.WARNING, "model.getExceptionHandler: {0}", model.getExceptionHandler());
            model.getExceptionHandler().handle(e);
        }
    }

    /**
     * return the first plot element that is using this data source.
     * @return null or a plot element.
     */
    private PlotElement getPlotElement() {
        List<PlotElement> pele = dom.controller.getPlotElementsFor(dsf);
        if (pele.isEmpty()) {
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
        } else {
            Plot plot= dom.controller.getFirstPlotFor(this.dsf);
            if ( plot!=null ) {
                p= plot.controller.getDasPlot();
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
