/*
 * AggregatingDataSource.java
 *
 * Created on October 25, 2007, 10:29 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.aggregator;

import java.io.FileNotFoundException;
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
import java.net.URI;
import java.text.ParseException;
import java.util.Map;
import java.util.logging.Logger;
import org.das2.dataset.NoDataInIntervalException;
import org.das2.datum.Units;
import org.das2.fsm.FileStorageModelNew;
import org.das2.util.filesystem.FileSystem;
import org.virbo.dataset.ArrayDataSet;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.JoinDataSet;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.MetadataModel;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.datasource.capability.Updating;

/**
 *
 * http://www.papco.org:8080/opendap/cdf/polar/hyd_h0/%Y/po_h0_hyd_%Y%m%d_v...cdf.dds?ELECTRON_DIFFERENTIAL_ENERGY_FLUX
 * @author jbf
 */
public final class AggregatingDataSource extends AbstractDataSource {

    private FileStorageModelNew fsm;
    DataSourceFactory delegateDataSourceFactory;
    AggregationPollUpdating upd; // allow a group of files to be watched.  This is experimental.
    
    /**
     * metadata from the last read.
     */
    Map<String, Object> metadata;
    MetadataModel metadataModel;

    private DatumRange quantize(DatumRange timeRange) {
        try {
            String[] ss = fsm.getNamesFor(timeRange); // 3523483 there's a bug here when reading from a zip file, because we need to download it first.
            if (ss.length == 0) {
                //TODO: Juno uses wildcard in the name.
                return new DatumRange(TimeUtil.prevMidnight(timeRange.min()), TimeUtil.nextMidnight(timeRange.max())); // do what we did before
            }
            DatumRange result = fsm.getRangeFor(ss[0]);
            for (int i = 0; i < ss.length; i++) {
                DatumRange r1 = fsm.getRangeFor(ss[i]);
                result = result.include(r1.max()).include(r1.min());
            }
            if ( timeRange.contains(result) ) {
                return timeRange;
            } else {
                return result;
            }
        } catch (IOException ex) {
            Logger.getLogger(AggregatingDataSource.class.getName()).log(Level.SEVERE, null, ex);
            timeRange = new DatumRange(TimeUtil.prevMidnight(timeRange.min()), TimeUtil.nextMidnight(timeRange.max()));
            return timeRange;
        }
    }

    /** Creates a new instance of AggregatingDataSource */
    public AggregatingDataSource(URI uri,DataSourceFactory delegateFactory) throws MalformedURLException, FileSystem.FileSystemOfflineException, IOException, ParseException {
        super(uri);
        this.delegateDataSourceFactory = delegateFactory;
        addCability(TimeSeriesBrowse.class, createTimeSeriesBrowse() );
        String stimeRange= super.params.get( URISplit.PARAM_TIME_RANGE );
        if ( stimeRange!=null ) {
            if ( super.params.get("timeRange")!=null && stimeRange==null ) {
                stimeRange= super.params.get("timeRange");
            }
            if ( stimeRange==null ) {
                throw new IllegalArgumentException("timerange not found");
            }
            stimeRange= stimeRange.replaceAll("\\+"," " );
            viewRange= DatumRangeUtil.parseTimeRange( stimeRange );
        }

        String filePollUpdates= getParam(  URISplit.PARAM_FILE_POLL_UPDATES,"" );
        if ( filePollUpdates.length()>0 ) {
            String surl = DataSetURI.fromUri( uri );
            FileStorageModelNew fsm1 = AggregatingDataSourceFactory.getFileStorageModel(surl);
            double ffilePollUpdates= Math.ceil( Double.parseDouble( filePollUpdates ) );
            upd= new AggregationPollUpdating(fsm1, viewRange, (long)(ffilePollUpdates) );
            addCability( Updating.class, upd );
        }

    }


    private TimeSeriesBrowse createTimeSeriesBrowse() {
        return new AggTimeSeriesBrowse();
    }

    public class AggTimeSeriesBrowse implements TimeSeriesBrowse {

        public void setTimeRange(DatumRange dr) {
            viewRange = quantize(dr);
            Logger.getLogger("virbo.datasource.agg").log(Level.FINE, "set timerange={0}", viewRange);
        }

        public void setTimeResolution(Datum d) {
            resolution= d;
            Logger.getLogger("virbo.datasource.agg").log(Level.FINE, "set resolution={0}", d);
        }

        public String getURI() {
            String surl = DataSetURI.fromUri( AggregatingDataSource.this.resourceURI ) + "?" ;
            if (sparams != null && !sparams.equals("") ) surl += sparams + "&";
            surl += "timerange=" + String.valueOf(viewRange);

            URISplit split = URISplit.parse(surl);
            Map<String,String> mparams = URISplit.parseParams(split.params);
            String stimeRange = viewRange.toString();
            mparams.put("timerange", stimeRange);
            split.params = URISplit.formatParams(mparams);

            URISplit split2= URISplit.parse(AggregatingDataSource.this.uri);
            split.vapScheme= split2.vapScheme;

            return URISplit.format(split);
        }

        public DatumRange getTimeRange() {
            return viewRange;
        }

        public Datum getTimeResolution() {
            return resolution;
        }

        @Override
        public String toString() {
            return "aggtsb: " + viewRange + "@" + ( resolution==null ? "intrinsic" : resolution );
        }

        public void setURI(String suri) throws ParseException {
            viewRange= URISplit.parseTimeRange(suri);
        }

    }
    
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        
        String[] ss = getFsm().getBestNamesFor( viewRange, new NullProgressMonitor() );

        Logger.getLogger("virbo.datasource.agg").log(Level.FINE, "aggregating {0} files for {1}", new Object[]{ss.length, viewRange});

        ArrayDataSet result = null;
        JoinDataSet altResult= null; // used when JoinDataSets are found

        if ( ss.length==0 ) {
            if ( null==getFsm().getRepresentativeFile( new NullProgressMonitor() ) ) {
                throw new FileNotFoundException("No such file: No files found matching "+getFsm().toString());
            }
        }
        if (ss.length > 1) {
            mon.setTaskSize(ss.length * 10);
            mon.started();
        }

        DatumRange cacheRange1 = null;

        for (int i = 0; i < ss.length; i++) {
            String scompUrl = getFsm().getFileSystem().getRootURI().toString() + ss[i];
            if (!sparams.equals("")) {
                scompUrl += "?" + sparams;
            }

            URI delegateUri= DataSetURI.getURIValid(scompUrl);

            DataSource delegateDataSource = delegateDataSourceFactory.getDataSource(delegateUri);

            if ( delegateDataSource.getCapability( TimeSeriesBrowse.class )!=null ) {
                TimeSeriesBrowse delegateTsb= delegateDataSource.getCapability( TimeSeriesBrowse.class );
                delegateTsb.setTimeRange(viewRange);
                delegateTsb.setTimeResolution(resolution);
                setResolution( delegateTsb.getTimeResolution() );
            } else {
                resolution= null;
            }
            
            metadataModel = delegateDataSource.getMetadataModel();

            ProgressMonitor mon1;
            if (ss.length > 1) {
                mon.setProgressMessage("getting " + ss[i]);
                mon1 = SubTaskMonitor.create(mon, i * 10, 10 * (i + 1));
                if ( mon1.isCancelled() ) break;
                mon1.setTaskProgress(0); // cause it to paint
            } else if ( ss.length==1 ) {
                mon1 = mon;
                if ( mon1.isCancelled() ) break;
                mon1.setLabel("loading " + ss[0] );
                mon1.started();
                mon1.setTaskProgress(0);
            } else {
                mon1= mon;
                if ( mon1.isCancelled() ) break;
            }

            try {
                QDataSet ds1 = delegateDataSource.getDataSet(mon1);

                DatumRange dr1 = getFsm().getRangeFor(ss[i]);

                if (result == null && altResult==null ) {
                    if ( ds1 instanceof JoinDataSet ) {
                        altResult= JoinDataSet.copy( (JoinDataSet)ds1 );
                        DDataSet mpds= DDataSet.create(new int[0]);
                        altResult.putProperty(QDataSet.JOIN_0,mpds );
                    } else {
                        if ( ss.length==1 ) {
                            result= ArrayDataSet.maybeCopy(ds1);
                        } else {
                            result = ArrayDataSet.maybeCopy(ds1);
                            result.grow(result.length()*ss.length*11/10);  //110%
                        }
                    }
                    this.metadata = delegateDataSource.getMetadata(new NullProgressMonitor());
                    cacheRange1 = dr1;

                } else {
                    if ( ds1 instanceof JoinDataSet ) {
                        altResult.joinAll( (JoinDataSet)ds1 );
                    } else {
                        ArrayDataSet ads1= ArrayDataSet.maybeCopy(result.getComponentType(),ds1);
                        try {
                            if ( result.canAppend(ads1) ) {
                                result.append( ads1 );
                            } else {
                                result.grow( result.length() + ads1.length() * (ss.length-i) );
                                result.append( ads1 );
                            }
                        } catch ( IllegalArgumentException ex ) {
                            throw new IllegalArgumentException( "can't append data from "+delegateUri, ex );
                        }
                    }

                    //TODO: combine metadata.  We don't have a way of doing this.
                    //this.metadata= null;
                    //this.metadataModel= null;
                    cacheRange1 = new DatumRange(cacheRange1.min(), dr1.max());
                }
            } catch ( Exception ex ) {
                if ( ex instanceof NoDataInIntervalException && ss.length>1 ) {
                    System.err.println("no data found in "+delegateUri );
                    // do nothing
                } else if ( ss.length==1 ) {
                    throw ex;
                } else {
                    //ex.printStackTrace(); //TODO: it would be nice to be able to attach the error to the result.
                    throw ex;
                    //do nothing
                }
            }
            if (ss.length > 1) {
                if (mon.isCancelled()) {
                    break;
                }
            }
        }
        cacheRange = cacheRange1;

        //if (ss.length > 1) {
            mon.finished();
        //}

        if ( altResult!=null ) {
            ArrayDataSet dep0 = (ArrayDataSet) altResult.property(DDataSet.DEPEND_0);
            Units dep0units= dep0==null ? null : SemanticOps.getUnits(dep0);
            if ( dep0==null ) {
                dep0= (ArrayDataSet) altResult.property(QDataSet.JOIN_0);
                QDataSet d= (QDataSet) altResult.property(QDataSet.DEPEND_0,0);
                if ( d!=null ) dep0units= SemanticOps.getUnits(d);
                dep0.putProperty(QDataSet.UNITS, dep0units );
            }
            if ( dep0 != null && cacheRange1.getUnits().isConvertableTo( dep0units ) ) {
                dep0.putProperty(QDataSet.CACHE_TAG, new CacheTag(cacheRange1, null));
                dep0.putProperty(QDataSet.TYPICAL_MIN, viewRange.min().doubleValue(dep0units) );
                dep0.putProperty(QDataSet.TYPICAL_MAX, viewRange.max().doubleValue(dep0units) );
            }

            return altResult;
            
        } else {
            MutablePropertyDataSet dep0 = result == null ? null : (MutablePropertyDataSet) result.property(DDataSet.DEPEND_0);
            Units dep0units= dep0==null ? null : SemanticOps.getUnits(dep0);
            if ( dep0 != null && cacheRange1.getUnits().isConvertableTo( dep0units ) ) {
                dep0.putProperty(QDataSet.CACHE_TAG, new CacheTag(cacheRange1, null));
                dep0.putProperty(QDataSet.TYPICAL_MIN, viewRange.min().doubleValue(dep0units) );
                dep0.putProperty(QDataSet.TYPICAL_MAX, viewRange.max().doubleValue(dep0units) );
            }

            return result;
        }

    }

    @Override
    public MetadataModel getMetadataModel() {
        return metadataModel;
    }

    /**
     * returns the metadata provided by the first delegate dataset.
     */
    @Override
    public Map<String, Object> getMetadata(ProgressMonitor mon) throws Exception {
        if (metadata == null) {
            Map<String, Object> retValue;
            getDataSet(mon); // sorry, we have to read the data...
            return metadata;
        } else {
            return metadata;
        }

    }
    /**
     * Holds value of property viewRange.
     */
    private DatumRange viewRange = DatumRangeUtil.parseTimeRangeValid("2006-07-03 to 2006-07-05");

    /**
     * resolution.  Only used if the delegates have a TimeSeriesBrowse.
     */
    private Datum resolution= null;

    public Datum getResolution() {
        return resolution;
    }

    public void setResolution(Datum resolution) {
        Datum old = this.resolution;
        this.resolution = resolution;
        propertyChangeSupport.firePropertyChange("resolution", old, resolution);
    }



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

    public FileStorageModelNew getFsm() {
        return fsm;
    }

    public void setFsm(FileStorageModelNew fsm) {
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
    public String getURI() {
        return super.getURI();
    }
    
}
