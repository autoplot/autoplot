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
import org.das2.datum.CacheTag;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.das2.dataset.NoDataInIntervalException;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.fsm.FileStorageModelNew;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.CancelledOperationException;
import org.virbo.dataset.ArrayDataSet;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.JoinDataSet;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.DataSourceUtil;
import org.virbo.datasource.MetadataModel;
import org.virbo.datasource.ReferenceCache;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.datasource.capability.Updating;
import org.virbo.dsops.Ops;
import org.virbo.dsutil.BundleBuilder;
import org.virbo.dsutil.DataSetBuilder;
import org.virbo.dsutil.Reduction;

/**
 * Data Source that aggregates (or combines) the data from granule files containing 
 * data for intervals.  For example, 
 * http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hydra/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v$v.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=20000109
 * is the aggregation of daily files from the CDAWeb.  This provides an 
 * easy method for storing a long time series without having a fancy data server.
 * @author jbf
 */
public final class AggregatingDataSource extends AbstractDataSource {

    private static final Logger logger= LoggerManager.getLogger("apdss.agg");

    public static final String MSG_NO_FILES_FOUND = "No files in interval";


    private FileStorageModelNew fsm;
    DataSourceFactory delegateDataSourceFactory;
    AggregationPollUpdating upd; // allow a group of files to be watched.  This is experimental.
    
    /**
     * metadata from the last read.
     */
    Map<String, Object> metadata;
    MetadataModel metadataModel;

    TimeSeriesBrowse tsb;

    private DatumRange quantize(DatumRange timeRange) {
        try {
            String[] ss = fsm.getNamesFor(timeRange); // 3523483 there's a bug here when reading from a zip file, because we need to download it first.
            DatumRange result= timeRange;
            Datum oneDay= Units.hours.createDatum(24);
            while ( ss.length == 0 && result.width().value()>0 && result.width().lt( oneDay ) ) {
                result= DatumRangeUtil.rescale( result, -1, 2 );
                ss = fsm.getNamesFor(result);
            } 
            if ( ss.length==0 ) {
                return new DatumRange(TimeUtil.prevMidnight(timeRange.min()), TimeUtil.nextMidnight(timeRange.max())); // do what we did before
            }
            result = fsm.getRangeFor(ss[0]);
            for (int i = 1; i < ss.length; i++) {
                DatumRange r1 = fsm.getRangeFor(ss[i]);
                result = result.include(r1.max()).include(r1.min());
            }
            if ( timeRange.contains(result) ) {
                return timeRange;
            } else {
                if ( !result.intersects(timeRange) ) {
                    if ( result.max().lt(timeRange.min() ) ) {
                        result= DatumRangeUtil.rescale( result, 0, 2 );
                    } else if ( result.min().gt(timeRange.max()) ) {
                        result= DatumRangeUtil.rescale( result, -1, 1 );
                    }
                }
                return result;
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
            timeRange = new DatumRange(TimeUtil.prevMidnight(timeRange.min()), TimeUtil.nextMidnight(timeRange.max()));
            return timeRange;
        }
    }

    /** Creates a new instance of AggregatingDataSource */
    public AggregatingDataSource(URI uri,DataSourceFactory delegateFactory) throws MalformedURLException, FileSystem.FileSystemOfflineException, IOException, ParseException {
        super(uri);
        this.delegateDataSourceFactory = delegateFactory;
        tsb= createTimeSeriesBrowse();
        addCability(TimeSeriesBrowse.class, tsb );
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

        @Override
        public void setTimeRange(DatumRange dr) {
            viewRange = quantize(dr);
            logger.log(Level.FINE, "set timerange={0}", viewRange);
        }

        @Override
        public void setTimeResolution(Datum d) {
            resolution= d;
            logger.log(Level.FINE, "set resolution={0}", d);
        }

        @Override
        public String getURI() {
            String surl = DataSetURI.fromUri( AggregatingDataSource.this.resourceURI ) + "?" ;
            if (sparams != null && !sparams.equals("") ) surl += sparams + "&";
            surl += "timerange=" + String.valueOf(viewRange);

            URISplit split = URISplit.parse(surl);
            Map<String,String> mparams = URISplit.parseParams(split.params);
            String stimeRange = viewRange.toString();
            mparams.put("timerange", stimeRange);

            if ( resolution!=null ) {
                mparams.put("resolution", String.valueOf(resolution));
            }
            split.params = URISplit.formatParams(mparams);

            URISplit split2= URISplit.parse(AggregatingDataSource.this.uri);
            split.vapScheme= split2.vapScheme;

            return URISplit.format(split);
        }

        @Override
        public DatumRange getTimeRange() {
            return viewRange;
        }

        @Override
        public Datum getTimeResolution() {
            return resolution;
        }

        @Override
        public String toString() {
            return "aggtsb: " + viewRange + "@" + ( resolution==null ? "intrinsic" : resolution );
        }

        @Override
        public void setURI(String suri) throws ParseException {
            viewRange= URISplit.parseTimeRange(suri);
            logger.log( Level.FINE, "setURI sets viewRange to {0}", viewRange);
        }

        @Override
        public String blurURI() {
            String surl = DataSetURI.fromUri( AggregatingDataSource.this.resourceURI ) + "?";
            if (sparams != null && !sparams.equals("") ) surl += sparams + "&";

            URISplit split = URISplit.parse(surl);
            Map<String,String> mparams = URISplit.parseParams(split.params);

            split.params = URISplit.formatParams(mparams);

            URISplit split2= URISplit.parse(AggregatingDataSource.this.uri);
            split.vapScheme= split2.vapScheme;

            return URISplit.format(split);
        }

    }
    
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {

        boolean useReferenceCache= "true".equals( System.getProperty( ReferenceCache.PROP_ENABLE_REFERENCE_CACHE, "false" ) );
        
        ReferenceCache.ReferenceCacheEntry cacheEntry=null;
        if ( useReferenceCache ) {
            cacheEntry= ReferenceCache.getInstance().getDataSetOrLock( this.tsb.getURI(), mon);
            if ( !cacheEntry.shouldILoad( Thread.currentThread() ) ) {
                try {
                    QDataSet result= cacheEntry.park( mon );
                    return result;
                } catch ( Exception ex ) {
                    throw ex;
                }
            } else {
                logger.log(Level.FINE, "reference cache in use, {0} is loading {1}", new Object[] { Thread.currentThread().toString(), resourceURI } );
            }
        }

        try {
            DatumRange lviewRange= viewRange;
            Datum lresolution= resolution;

            String[] ss = getFsm().getBestNamesFor( lviewRange, new NullProgressMonitor() );
            
            boolean avail= !getParam( "avail", "F" ).equals("F");
            boolean reduce= getParam( "reduce", "F" ).equals("T");

            if ( avail ) {
                logger.log(Level.FINE, "availablility {0} ", new Object[]{ lviewRange});
                DataSetBuilder build= new DataSetBuilder(2,ss.length,4);
                Units u= Units.us2000;
                EnumerationUnits eu= new EnumerationUnits("default");
                for ( String s: ss ) {
                    DatumRange dr= getFsm().getRangeFor(s);
                    build.putValues( -1, DDataSet.wrap( new double[] { dr.min().doubleValue(u), dr.max().doubleValue(u), 0x80FF80, eu.createDatum(s).doubleValue(eu) } ), 4 );
                    build.nextRecord();
                }
                DDataSet result= build.getDataSet();

                DDataSet bds= DDataSet.createRank2( 4, 0 );
                bds.putProperty( "NAME__0", "StartTime" );
                bds.putProperty( "UNITS__0", u );
                bds.putProperty( "NAME__1", "StopTime" );
                bds.putProperty( "UNITS__1", u );
                bds.putProperty( "NAME__2", "Color" );
                bds.putProperty( "NAME__3", "Filename" );
                bds.putProperty( "UNITS__3", eu );

                result.putProperty( QDataSet.BUNDLE_1, bds );

                result.putProperty( QDataSet.RENDER_TYPE, "eventsBar" );
                result.putProperty( QDataSet.LABEL, "Availability");

                URISplit split= URISplit.parse(getURI() );
                result.putProperty( QDataSet.TITLE, split.file );

                if ( cacheEntry!=null ) cacheEntry.finished(result);
                
                return result;

            }

            logger.log(Level.FINE, "aggregating {0} files for {1}", new Object[]{ss.length, lviewRange});
            StringBuilder log= new StringBuilder( "== getDataSet will read the following ==" );
            for ( String s : ss ) {
                log.append("\n").append(s);
            }
            logger.log( Level.FINE, log.toString() );

            

            ArrayDataSet result = null;
            JoinDataSet altResult= null; // used when JoinDataSets are found

            if ( ss.length==0 ) {
                if ( null==getFsm().getRepresentativeFile( new NullProgressMonitor() ) ) {
                    throw new FileNotFoundException("Unable to find representative file: No files found matching "+getFsm().toString());
                } else {
                    throw new FileNotFoundException( MSG_NO_FILES_FOUND+" "+lviewRange );
                }
            }
            if (ss.length > 1) {
                mon.setTaskSize(ss.length * 10);
                mon.started();
            }

            DatumRange cacheRange1 = null;

            EnumerationUnits exunits= EnumerationUnits.create("notes");
            DataSetBuilder notesBuilder= new DataSetBuilder( 2, ss.length/2, 3 );  // container for messages will be an events list.
            BundleBuilder bds= new BundleBuilder(3);
            bds.putProperty( QDataSet.NAME, 0, "startTime" );
            bds.putProperty( QDataSet.NAME, 1, "stopTime" );
            bds.putProperty( QDataSet.NAME, 2, "note" );
            bds.putProperty( QDataSet.UNITS, 0, Units.us2000 );
            bds.putProperty( QDataSet.UNITS, 1, Units.us2000 );
            bds.putProperty( QDataSet.UNITS, 2, exunits );
            notesBuilder.putProperty( QDataSet.BUNDLE_1, bds.getDataSet() );

            if ( delegateDataSourceFactory==null ) {
                throw new IllegalArgumentException("unable to identify data source");
            }
            
            boolean doThrow= false; // this will be set to true if we really do want to throw the exception instead of simply making a note of it.
            
            for (int i = 0; i < ss.length; i++) {
                String scompUrl = getFsm().getFileSystem().getRootURI().toString() + ss[i];
                if (!sparams.equals("")) {
                    scompUrl += "?" + sparams;
                }

                URI delegateUri= DataSetURI.getURIValid(scompUrl);

                DataSource delegateDataSource = delegateDataSourceFactory.getDataSource(delegateUri);

                if ( delegateDataSource.getCapability( TimeSeriesBrowse.class )!=null ) {
                    TimeSeriesBrowse delegateTsb= delegateDataSource.getCapability( TimeSeriesBrowse.class );
                    delegateTsb.setTimeRange(lviewRange);
                    delegateTsb.setTimeResolution(lresolution);
                    setResolution( delegateTsb.getTimeResolution() );
                } else {
                    // resolution= null; TODO: verify there's no reason to do this.
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
                    mon1.setProgressMessage("getting " + ss[0] );
                    mon1.started();
                    mon1.setTaskProgress(0);
                } else {
                    mon1= mon;
                    if ( mon1.isCancelled() ) break;
                }

                DatumRange drex= null; // in case there is an exception, where did it occur?
                try {
                    DatumRange dr1 = getFsm().getRangeFor(ss[i]);
                    drex= dr1;

                    QDataSet ds1 = delegateDataSource.getDataSet(mon1);
                    if ( ds1==null ) {
                        logger.warning("delegate returned null");
                        ds1 = delegateDataSource.getDataSet(mon1);
                        continue;
                    }
                    QDataSet xds= SemanticOps.xtagsDataSet(ds1);
                    if ( xds!=null && UnitsUtil.isTimeLocation( SemanticOps.getUnits(xds) )) {
                        if ( SemanticOps.isJoin(xds) ) {
                            //TODO: check Ops.extent(xds), which I don't think handles joins.
                        } else {
                            QDataSet exds= Ops.extent(xds);
                            if ( !( UnitsUtil.isTimeLocation( dr1.getUnits() ) && UnitsUtil.isTimeLocation(SemanticOps.getUnits(exds)) ) ) {
                                logger.log(Level.WARNING, "Hey units! \"{0}\" \"{1}\"", new Object[] { dr1.getUnits(), SemanticOps.getUnits(exds) } );
                            }
                            if ( !dr1.intersects(DataSetUtil.asDatumRange(exds)) ) {
                                logger.log(Level.WARNING, "file for {0} contains data from an unexpected interval: {1}", new Object[] { dr1, exds } );
                            }
                        }
                    }

                    List<String> problems= new ArrayList();
                    if ( !DataSetUtil.validate(ds1, problems) ) {
                        for ( String p: problems ) {
                            System.err.println("problem with aggregation element "+ss[i]+": "+p);
                            logger.log(Level.WARNING, "problem with aggregation element {0}: {1}", new Object[]{ss[i], p});
                        }
                    }

                    if ( reduce && lresolution!=null && ds1.rank()<3 && SemanticOps.isTimeSeries(ds1) ) {
                        logger.info("reducing resolution to save memory");
                        mon1.setProgressMessage("reducing resolution");
                        ds1= Reduction.reducex( ds1, DataSetUtil.asDataSet(lresolution) );
                    }

                    if (result == null && altResult==null ) {
                        if ( ds1 instanceof JoinDataSet ) {
                            altResult= JoinDataSet.copy( (JoinDataSet)ds1 );
                            DDataSet mpds= DDataSet.create(new int[0]);
                            altResult.putProperty(QDataSet.JOIN_0,mpds );
                        } else {
                            if ( ss.length==1 ) {
                                result= ArrayDataSet.maybeCopy(ds1);
                            } else {
                                result = ArrayDataSet.copy(ds1);
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
                            } catch ( Exception ex ) {
                                doThrow= true;
                                throw ex; // the exception occurring in the append step was hidden because the code assumed it was a problem with the read.
                            }
                        }

                        //TODO: combine metadata.  We don't have a way of doing this.
                        //this.metadata= null;
                        //this.metadataModel= null;
                        cacheRange1 = new DatumRange(cacheRange1.min(), dr1.max());
                    }
                } catch ( Exception ex ) {
                    if ( doThrow ) {
                        throw ex;
                    }
                    if ( ex instanceof NoDataInIntervalException && ss.length>1 ) {
                        logger.log(Level.FINE, "no data found in {0}", delegateUri);
                        // do nothing
                    } else if ( ss.length==1 ) {
                        throw ex;
                    } else {
                        notesBuilder.putValue(-1,0,drex.min().doubleValue(Units.us2000));
                        notesBuilder.putValue(-1,1,drex.max().doubleValue(Units.us2000));
                        notesBuilder.putValue(-1,2,exunits.createDatum(DataSourceUtil.getMessage(ex)).doubleValue(exunits) );
                        notesBuilder.nextRecord();
                        ex.printStackTrace();
                    }
                }
                if (ss.length > 1) {
                    if (mon.isCancelled()) {
                        throw new org.das2.CancelledOperationException("cancel pressed");
                    }
                }

                if ( result!=null ) {
                    List<String> problems= new ArrayList();
                    if ( !DataSetUtil.validate( result, problems) ) {
                        for ( String p: problems ) {
                            System.err.println("problem in aggregation: "+p);
                            logger.warning("problem in aggregation: "+p);
                        }
                    }
                }

            }
            cacheRange = cacheRange1;

            mon.finished();

            Map<String,Object> userProps= new HashMap();
            userProps.put( "files", ss );

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
                    dep0.putProperty(QDataSet.CACHE_TAG, new CacheTag(cacheRange1,reduce?lresolution:null));
                    dep0.putProperty(QDataSet.TYPICAL_MIN, lviewRange.min().doubleValue(dep0units) );
                    dep0.putProperty(QDataSet.TYPICAL_MAX, lviewRange.max().doubleValue(dep0units) );
                }

                QDataSet notes= notesBuilder.getDataSet();
                if ( notes.length()>0 ) altResult.putProperty( QDataSet.NOTES, notes );
                altResult.putProperty( QDataSet.USER_PROPERTIES, userProps );

                if ( cacheEntry!=null ) cacheEntry.finished(altResult);
                return altResult;

            } else {
                MutablePropertyDataSet dep0 = result == null ? null : (MutablePropertyDataSet) result.property(DDataSet.DEPEND_0);
                Units dep0units= dep0==null ? null : SemanticOps.getUnits(dep0);
                if ( dep0 != null && cacheRange1.getUnits().isConvertableTo( dep0units ) ) {
                    dep0.putProperty(QDataSet.CACHE_TAG, new CacheTag(cacheRange1, reduce?lresolution:null));
                    dep0.putProperty(QDataSet.TYPICAL_MIN, lviewRange.min().doubleValue(dep0units) );
                    dep0.putProperty(QDataSet.TYPICAL_MAX, lviewRange.max().doubleValue(dep0units) );
                }

                QDataSet notes= notesBuilder.getDataSet();
                if ( result!=null && notes.length()>0 ) result.putProperty( QDataSet.NOTES, notes );
                if ( result!=null ) result.putProperty( QDataSet.USER_PROPERTIES, userProps );
                if ( cacheEntry!=null ) cacheEntry.finished(result);
                
                // check to see if all the notes are the same explaining the exception
                if ( result==null && notes.length()>0 ) { 
                    Units u= ((Units)((QDataSet)notes.property(QDataSet.BUNDLE_1)).property(QDataSet.UNITS,2));
                    int n0= (int)notes.value(0,2);
                    String nns= u.createDatum(n0).toString();
                    for ( int i=1; i<notes.length(); i++ ) {
                        if ( notes.value(i,2)!=n0 ) {
                            nns= nns+","+ u.createDatum(notes.value(i,2)).toString();
                        }
                    }
                    if ( nns.length()>120 ) nns=nns.substring(0,120)+"...";
                    throw new IllegalArgumentException(nns);
                }
                return result;
            }
        } catch ( Exception ex ) {
            if ( cacheEntry!=null ) cacheEntry.exception(ex);
            throw ex;
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
            mon.setTaskSize(10);
            mon.started();
            String scompUrl = getFsm().getFileSystem().getRootURI().toString() + getFsm().getRepresentativeFile(mon.getSubtaskMonitor(0,5,"get representative file"));
            if (!sparams.equals("")) {
                scompUrl += "?" + sparams;
            }

            URI delegateUri= DataSetURI.getURIValid(scompUrl);
            DataSource delegateDataSource = delegateDataSourceFactory.getDataSource(delegateUri);
            metadata = delegateDataSource.getMetadata(mon.getSubtaskMonitor(5,10,"get metadata"));
            metadataModel= delegateDataSource.getMetadataModel();
            return metadata;
        } else {
            return metadata;
        }

    }

    private static DatumRange DEFAULT_TIME_RANGE= DatumRangeUtil.parseTimeRangeValid( "1970-01-01" );
    /**
     * Holds value of property viewRange.
     */
    private DatumRange viewRange = DEFAULT_TIME_RANGE; // this should not be used!

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
        logger.log( Level.FINE, "setViewRange({0})", viewRange );
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
