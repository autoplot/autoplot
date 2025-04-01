/*
 * AggregatingDataSource.java
 *
 * Created on October 25, 2007, 10:29 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.autoplot.aggregator;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.qds.buffer.BufferDataSet;
import org.das2.dataset.NoDataInIntervalException;
import org.das2.datum.CacheTag;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.fsm.FileStorageModel;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.ArrayDataSet;
import org.das2.qds.DDataSet;
import org.das2.qds.DataSetOps;
import org.das2.qds.DataSetUtil;
import org.das2.qds.JoinDataSet;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.das2.qds.WritableDataSet;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.MetadataModel;
import org.autoplot.datasource.ReferenceCache;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.Version;
import org.autoplot.datasource.capability.Streaming;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.autoplot.datasource.capability.Updating;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.BundleBuilder;
import org.das2.qds.util.DataSetBuilder;
import org.das2.qds.util.Reduction;

/**
 * Data Source that aggregates (or combines) the data from granule files containing 
 * data for intervals.  For example, 
 * https://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hydra/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v$v.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=20000109
 * is the aggregation of daily files from the CDAWeb.  This provides an 
 * easy method for storing a long time series without having a complex 
 * data server.
 * 
 * The result of this is not guaranteed to be monotonically increasing in 
 * time.  See https://sourceforge.net/p/autoplot/bugs/1326/
 * 
 * @author jbf
 */
public final class AggregatingDataSource extends AbstractDataSource {

    private static final Logger logger= LoggerManager.getLogger("apdss.agg");

    /**
     * message used when no files are found in the interval.
     */
    public static final String MSG_NO_FILES_FOUND = "No files in interval";

    public static final String PARAM_AVAIL= "avail";

    private FileStorageModel fsm;
    DataSourceFactory delegateDataSourceFactory;
    AggregationPollUpdating upd; // allow a group of files to be watched.  This is experimental.
    String delegateVapScheme= null;
    
    /**
     * metadata from the last read.
     */
    Map<String, Object> metadata;
    MetadataModel metadataModel;

    TimeSeriesBrowse tsb;


    /** 
     * Creates a new instance of AggregatingDataSource
     * @param uri the URI
     * @param delegateFactory the factory used to read each granule.
     * @throws java.net.MalformedURLException
     * @throws org.das2.util.filesystem.FileSystem.FileSystemOfflineException
     * @throws java.text.ParseException 
     */
    public AggregatingDataSource(URI uri,DataSourceFactory delegateFactory) throws MalformedURLException, FileSystem.FileSystemOfflineException, IOException, ParseException {
        super(uri);
        this.delegateDataSourceFactory = delegateFactory;
        try {
            URISplit split= URISplit.parse(uri);
            this.delegateVapScheme= split.vapScheme;
        } catch ( RuntimeException ex ) {
            logger.log( Level.WARNING, null, ex );
        }
        if ( AggregatingDataSourceFactory.hasTimeFields( uri.toString() ) ) {
            tsb= new AggTimeSeriesBrowse();
            addCapability( TimeSeriesBrowse.class, tsb );
            addCapability( Streaming.class, new StreamingCapability(uri,this) );
        }
        
        String stimeRange= super.params.get( URISplit.PARAM_TIME_RANGE );
        if ( stimeRange!=null ) {
            if ( super.params.get("timeRange")!=null ) {
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
            FileStorageModel fsm1 = AggregatingDataSourceFactory.getFileStorageModel(surl);
            double ffilePollUpdates= Math.ceil( Double.parseDouble( filePollUpdates ) );
            upd= new AggregationPollUpdating(fsm1, viewRange, (long)(ffilePollUpdates) );
            addCapability( Updating.class, upd );
        }
    }

    /**
     * It's easy for programs to mess up and contain timetags that are 24 hours off, so check that the
     * tags are not more than 50% outside the bounds.
     * @param bounds the expected bounds for the data
     * @param ads0 the data which ought to be within these bounds.
     * @return the dataset, possibly trimmed to exclude miscalculated times.
     */
    private MutablePropertyDataSet checkBoundaries( DatumRange bounds, MutablePropertyDataSet ads0 ) {
        logger.entering( "org.autoplot.aggregator.AggregatingDataSource","checkBoundaries");
        QDataSet dep0_0= (QDataSet) ads0.property(QDataSet.DEPEND_0);
        if ( dep0_0==null && UnitsUtil.isTimeLocation(SemanticOps.getUnits(ads0) ) ) {
            dep0_0= ads0;
        } else if ( dep0_0==null ) {
            return ads0;
        }
        if ( !UnitsUtil.isTimeLocation( SemanticOps.getUnits(dep0_0) ) ) {
            return ads0;
        }
        if ( dep0_0.rank()!=1 ) return ads0;
        int ist= 0;
        while ( ist<dep0_0.length() && DatumRangeUtil.normalize( bounds, DataSetUtil.asDatum( dep0_0.slice(ist) ) ) < -0.5 ) ist++; // clip off krud at the beginning
        int ien= ads0.length();
        while ( ien>0 && DatumRangeUtil.normalize( bounds, DataSetUtil.asDatum( dep0_0.slice(ien-1) ) ) > 1.5 ) ien--;  // clip off krud at the end
        
        if ( ist>0 || ien<ads0.length() ) {
            if ( ist>0 ) logger.log(Level.INFO, "trimming records 0-{0} to remove timetags outside the bounds.", new Object[] { (ist-1) } );
            if ( ien<ads0.length() ) logger.log(Level.INFO, "trimming records {0}-{1} to remove timetags outside the bounds.", new Object[]{ien-1, ads0.length()-1});
            MutablePropertyDataSet result= Ops.maybeCopy( (MutablePropertyDataSet)ads0.trim( ist,ien ) );
            logger.exiting( "org.autoplot.aggregator.AggregatingDataSource","checkBoundaries");
            return result;
            
        } else {
            logger.exiting( "org.autoplot.aggregator.AggregatingDataSource","checkBoundaries");
            return ads0;
        }
    }
    
    /**
     * replacing ensureMonotonic, instead we just sort the data.  Note this
     * sorts the data in place.
     * @param ads0
     * @return dataset sorted by its times.
     */
    private MutablePropertyDataSet checkSort( MutablePropertyDataSet ads0 ) {
        logger.entering( "org.autoplot.aggregator.AggregatingDataSource","checkSort");
        QDataSet dep0_0= (QDataSet) ads0.property(QDataSet.DEPEND_0); 
        if ( dep0_0==null && UnitsUtil.isTimeLocation(SemanticOps.getUnits(ads0) ) ) {
            dep0_0= ads0;
        } else if ( dep0_0==null ) {
            return ads0;
        }
        QDataSet sort= Ops.sort(dep0_0);
        if ( ads0 instanceof WritableDataSet ) {
            if ( ads0.isImmutable() ) {
                ads0= Ops.copy(ads0);
            }
            DataSetOps.applyIndexInSitu( ((WritableDataSet)ads0), sort );
        } else {
            ads0= Ops.copy(ads0);
            DataSetOps.applyIndexInSitu( ((WritableDataSet)ads0), sort );
        }
        
        ((WritableDataSet)dep0_0).putProperty( QDataSet.MONOTONIC, Boolean.TRUE );
        //if ( DataSetUtil.isMonotonic((QDataSet)ads0.property(QDataSet.DEPEND_0) ) ) {
        //    logger.fine("indeed data DEPEND_0 is now monotonic.");
        //} else {
        //    logger.fine("why is data DEPEND_0 not monotonic, after I just sorted it");
        //    System.err.println("===");
        //    System.err.println(((QDataSet)ads0.property(QDataSet.DEPEND_0)).hashCode());
        //    for ( int i=0; i<4; i++ ) {
        //        System.err.println(((QDataSet)ads0.property(QDataSet.DEPEND_0)).value(i));
        //    }
        //}
        logger.exiting( "org.autoplot.aggregator.AggregatingDataSource","checkSort");
        return ads0;
    }

    /**
     * check/ensure that no data overlaps from ads0 to ads1.  See 
     * rfe https://sourceforge.net/p/autoplot/feature-requests/391/
     * @param ads0 the first dataset that will be appended
     * @param ads1 the second dataset that will be appended.
     * @return ads1, possibly trimmed.
     */
    private QDataSet trimOverlap(QDataSet ads0, QDataSet ads1) {
        logger.entering( "org.autoplot.aggregator.AggregatingDataSource","trimOverlap");
        QDataSet dep0_0= (QDataSet) ads0.property(QDataSet.DEPEND_0);
        QDataSet dep0_1= (QDataSet) ads1.property(QDataSet.DEPEND_0);
        if ( dep0_0==null && UnitsUtil.isTimeLocation(SemanticOps.getUnits(ads0) ) ) {
            dep0_0= ads0;
        }
        if ( dep0_1==null && UnitsUtil.isTimeLocation(SemanticOps.getUnits(ads1 ) ) ) {
            dep0_0= ads1;
        }
        if ( dep0_0==null || dep0_1==null ) return ads1;
        if ( dep0_1.rank()>1 ) throw new IllegalArgumentException("expected rank 1 depend0");
        if ( Ops.gt( dep0_1.slice(0), dep0_0.slice(dep0_0.length()-1) ).value()==1 ) {
            logger.exiting( "org.autoplot.aggregator.AggregatingDataSource","trimOverlap");
            return ads1;
        } else {
            int i=0;
            while ( i<dep0_1.length() && Ops.le( dep0_1.slice(i), dep0_0.slice(dep0_0.length()-1) ).value()==1 ) {
                i=i+1;
            }
            logger.exiting( "org.autoplot.aggregator.AggregatingDataSource","trimOverlap");
            return ads1.trim(i,ads1.length());
        }
    }

    /**
     * TimeSeriesBrowse allows users to look up new intervals automatically.
     */
    public class AggTimeSeriesBrowse implements TimeSeriesBrowse {

        @Override
        public void setTimeRange(DatumRange dr) {
            if ( getParam( "reduce", "F" ).equals("F") ) {
                viewRange = fsm.quantize( dr ); // does not communicate with FileSystem.
            } else {
                viewRange = dr;
            }
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
            mparams.remove("resolution");
            split.params = URISplit.formatParams(mparams);

            URISplit split2= URISplit.parse(AggregatingDataSource.this.uri);
            split.vapScheme= split2.vapScheme;

            return URISplit.format(split);
        }

    }
         
    
    /**
     * read the data.  This supports reference caching.
     * @param mon
     * @return
     * @throws Exception 
     */
    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {

        logger.log(Level.FINE, "getDataSet {0}", uri);
        
        boolean useReferenceCache= "true".equals( System.getProperty( ReferenceCache.PROP_ENABLE_REFERENCE_CACHE, "false" ) );
        
        String theUri= this.tsb!=null ? this.tsb.getURI() : this.uri.toString();
        
        ReferenceCache.ReferenceCacheEntry cacheEntry=null;
        if ( useReferenceCache ) {
            
            cacheEntry= ReferenceCache.getInstance().getDataSetOrLock( theUri, mon);
            if ( !cacheEntry.shouldILoad( Thread.currentThread() ) ) {
                try {
                    logger.log(Level.FINE, "wait for other thread {0}", uri);
                    QDataSet result= cacheEntry.park( mon );
                    return result;
                } catch ( Exception ex ) {
                    throw ex;
                }
            } else {
                logger.log(Level.FINE, "reference cache in use, {0} is loading {1}", new Object[] { Thread.currentThread().toString(), theUri } );
            }
        }
        
        logger.log(Level.FINE, "reading {0}", uri );
        
        try {
            QDataSet result= getDataSet( mon, viewRange, resolution );        
            if ( cacheEntry!=null ) cacheEntry.finished(result);
        
            return result;
        } catch ( Exception ex ) {
            if ( cacheEntry!=null ) cacheEntry.exception(ex);
            throw ex;

        }

    }
    
    /**
     * read the data, not using the reference cache.
     * @param mon monitor for the load
     * @param lviewRange the time span to load
     * @param lresolution resolution which is used where reduce=T
     * @return
     * @throws Exception 
     */
    public QDataSet getDataSet( ProgressMonitor mon, DatumRange lviewRange, Datum lresolution ) throws Exception {
        try {

            mon.started();
            
            String[] ss = getFsm().getBestNamesFor( lviewRange, mon.getSubtaskMonitor(sparams) );
            
            if ( "true".equals( System.getProperty( Version.PROP_ENABLE_CLEAN_CACHE ) ) ) {
                logger.fine("enableCleanCache is true");
                getFsm().cacheCleanup();
            }
            
            boolean avail= getParam( "avail", "F" ).equals("T");
            boolean fln= getParam( "filename", "F" ).equals("T");
            boolean reduce= getParam( "reduce", "F" ).equals("T");
            boolean filenameProvidesContext= getParam( "filenameProvidesContext", "F" ).equals("T");
            boolean addDimension= getParam( "addDim","F" ).equals("T");
            
            if ( avail ) {
                logger.log(Level.FINE, "availablility {0} ", new Object[]{ lviewRange});
                DataSetBuilder build= new DataSetBuilder(2,ss.length,4);
                Units u= Units.us2000;
                EnumerationUnits eu= new EnumerationUnits("default");
                for ( String s: ss ) {
                    DatumRange dr= getFsm().getRangeFor(s);
                    //if ( getFsm().hasField("x") ) {
                    //    s= getFsm().getField( "x", s );
                    //}
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

                mon.finished();
                
                return result;

            }

            logger.log(Level.FINE, "aggregating {0} files for {1}", new Object[]{ss.length, lviewRange});
            if ( logger.isLoggable(Level.FINE) ) {
                StringBuilder log= new StringBuilder( "== getDataSet will read the following ==" );
                for ( String s : ss ) {
                    log.append("\n").append(s);
                }
                logger.log( Level.FINE, log.toString() );
            }

            MutablePropertyDataSet result = null;
            JoinDataSet altResult= null; // used when JoinDataSets are found
            DataSetBuilder dep0Builder= null;  // used when joining non-time-series.
            
            if ( ss.length==0 ) {
                if ( null==getFsm().getRepresentativeFile( mon.getSubtaskMonitor("get representative file") ) ) {
                    throw new FileNotFoundException("Unable to find representative file: No files found matching "+getFsm().toString());
                } else {
                    throw new FileNotFoundException( MSG_NO_FILES_FOUND+" "+lviewRange );
                }
            }
            if (ss.length > 1) {
                mon.setTaskSize(ss.length * 10);
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

                URI delegateUri;
                if ( delegateVapScheme!=null ) { //TODO: I don't believe delegateVapScheme will be null.
                    delegateUri = DataSetURI.getURIValid(delegateVapScheme+":"+scompUrl);
                } else {
                    delegateUri = DataSetURI.getURIValid(scompUrl);
                }

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
                    mon1 = mon.getSubtaskMonitor( i * 10, 10 * (i + 1), "getting "+ss[i] );
                    if ( mon1.isCancelled() ) break;
                    mon1.setTaskProgress(0); // cause it to paint
                } else if ( ss.length==1 ) {
                    mon.setProgressMessage("getting " + ss[0] );
                    mon1 = mon.getSubtaskMonitor( "getting " + ss[0] );
                    if ( mon1.isCancelled() ) break;
                    mon1.started();
                    mon1.setTaskProgress(0);
                } else {
                    mon1= mon.getSubtaskMonitor("no files found");
                    if ( mon1.isCancelled() ) break;
                }

                DatumRange drex= null; // in case there is an exception, where did it occur?
                try {
                    DatumRange dr1 = getFsm().getRangeFor(ss[i]);
                    drex= dr1;

                    logger.log(Level.FINER, "delegate URI: {0}", new Object[]{ delegateDataSource.getURI() } );
                    
                    // Here is the single-granule read, the heart of aggregation.
                    QDataSet ds1 = delegateDataSource.getDataSet(mon1);
                   
                    logger.log(Level.FINER, "  read: {0}", new Object[]{ ds1 } );
                    
                    //https://sourceforge.net/p/autoplot/bugs/2206/
                    //logger.fine("ask for a garbage collection to get rid of junk");
                    //System.gc();
                    //logger.fine("done ask for a garbage collection to get rid of junk");
                    
                    if ( ds1==null ) {
                        logger.warning("delegate returned null");
                        ds1 = delegateDataSource.getDataSet(mon1);
                        if ( ds1==null ) continue;
                    }
                    
                    // check to see if it is enumeration and all values are present in the enumeration unit.
                    Units u= (Units) ds1.property(QDataSet.UNITS);
                    if ( u!=null && u instanceof EnumerationUnits && ds1.rank()==1 ) {
                        for ( int i2=0; i2<ds1.length(); i2++ ) {
                            try {
                                Datum d= u.createDatum(ds1.value(i2));
                            } catch ( IllegalArgumentException ex ) {
                                ex.printStackTrace();
                                System.err.println("no datum exists for this ordinal in agg");
                                Datum d= u.createDatum(ds1.value(i2));
                            }
                        }
                    }
                    
                    QDataSet xds= SemanticOps.xtagsDataSet(ds1);
                    
                    if ( xds!=null && filenameProvidesContext ) {
                        Units tu= SemanticOps.getUnits(xds);
                        if ( tu.isConvertibleTo(Units.hours) ) {
                            Datum d= Ops.datum(xds.slice(0));
                            if ( d.lt(Units.hours.createDatum(-48) ) || d.gt( Units.hours.createDatum(48) ) ) {
                                logger.warning("filenameProvidesContext, but times must be -48 to +48 hours");
                            } else {
                                xds= Ops.add( xds, dr1.min() );
                            }
                            ds1= Ops.link( xds, ds1 );
                        } else if ( UnitsUtil.isTimeLocation(tu) ) {
                            logger.fine("timetags already have context");
                        } else {
                            logger.log(Level.INFO, "timetags units cannot be added to time locations. (units={0})", tu);
                        }
                    }
                    
                    if ( xds!=null && UnitsUtil.isTimeLocation( SemanticOps.getUnits(xds) )) {
                        if ( SemanticOps.isJoin(xds) ) {
                            //TODO: check Ops.extent(xds), which I don't think handles joins.
                        } else {
                            if ( xds.length()>0 ) {
                                QDataSet exds= Ops.extent(xds);
                                if ( !( UnitsUtil.isTimeLocation( dr1.getUnits() ) && UnitsUtil.isTimeLocation(SemanticOps.getUnits(exds)) ) ) {
                                    logger.log(Level.WARNING, "Hey units! \"{0}\" \"{1}\"", new Object[] { dr1.getUnits(), SemanticOps.getUnits(exds) } );
                                }
                                if ( !dr1.intersects(DataSetUtil.asDatumRange(exds)) ) {
                                    logger.log(Level.WARNING, "file for {0} contains data from an unexpected interval: {1}", new Object[] { dr1, exds } );
                                }
                            }
                        }
                    }

                    List<String> problems= new ArrayList();
                    if ( !DataSetUtil.validate(ds1, problems) ) {
                        for ( String p: problems ) {
                            System.err.println("problem with aggregation element "+ss[i]+": "+p);
                            logger.log(Level.WARNING, "problem with aggregation element {0}: {1}", new Object[]{ss[i], p});
                            throw new RuntimeException("dataset doesn't validate for " + delegateUri );
                        }
                    }

                    if ( reduce && SemanticOps.isTimeSeries(ds1) ) {
                        QDataSet dep0= (QDataSet) ds1.property(QDataSet.DEPEND_0);
                        if ( dep0!=null ) {
                            if ( DataSetUtil.isMonotonic(dep0) ) {
                                logger.fine("trimming dataset to save memory");
                                mon1.setProgressMessage("trim to visible: "+lviewRange );
                                int imin= DataSetUtil.closestIndex( dep0, lviewRange.min() );
                                int imax= DataSetUtil.closestIndex( dep0, lviewRange.max() );
                                imax= imax+1;
                                if ( imin>0 || imax<ds1.length() ) {
                                    ds1= ds1.trim(imin,imax);
                                    logger.log(Level.FINER, "dataset trimmed to {0}", ds1);
                                } else {
                                    logger.log(Level.FINER, "dataset not trimmed" );
                                }   
                            }
                            logger.fine("reducing resolution to save memory");
                            mon1.setProgressMessage("reducing resolution");
                            if ( lresolution!=null ) {
                                ds1= Reduction.reducex( ds1, DataSetUtil.asDataSet(lresolution) );
                                logger.log(Level.FINER, "dataset reduced to {0}", ds1);
                            }
                        } else {
                            logger.fine("data is not time series, cannot reduce");
                        }
                    }
                    
                    if ( fln ) {
                        QDataSet tt= Ops.xtags(ds1);
                        String uri= delegateDataSource.getURI();
                        EnumerationUnits units= Units.nominal();
                        ds1= Ops.link( tt, Ops.replicate( Ops.dataset( units.createDatum(uri) ), tt.length() ) );
                    }
                    
                    if (result!=null ) { // check for special case where non-time-varying data has been loaded.
                        QDataSet dep0 = (QDataSet) result.property(QDataSet.DEPEND_0);
                        if ( dep0==null && result.rank()==1 && Ops.equivalent( result, ds1 ) ) {
                            continue; // do not append the results.
                        }
                    }

                    if (result == null && altResult==null ) {
                        if ( addDimension ) {
                            altResult= new JoinDataSet(ds1);
                            dep0Builder= new DataSetBuilder(1,ss.length);
                            dep0Builder.nextRecord(dr1.middle());
                        } else if ( ds1 instanceof JoinDataSet ) {
                            altResult= JoinDataSet.copy( (JoinDataSet)ds1 );
                            DDataSet mpds= DDataSet.create(new int[0]);
                            altResult.putProperty(QDataSet.JOIN_0,mpds );
                        } else {
                            QDataSet dep0= (QDataSet)ds1.property(QDataSet.DEPEND_0);
                            boolean isSeriesOfImages= dep0==null && ( 
                                    ds1.rank()>2  ||  // dep0==null && ds1.rank()>2 
                                    ( ds1.rank()==2 && ds1.length(0)>QDataSet.MAX_PLANE_COUNT ) );
                            if ( isSeriesOfImages ) { // rfe521: experiment with aggregation types.
                                result= new JoinDataSet(ds1);
                                dep0Builder= new DataSetBuilder(1,ss.length);
                                dep0Builder.nextRecord(dr1.middle());
                            } else {
                                if ( ds1 instanceof BufferDataSet ) {
                                    if ( ss.length==1 ) {
                                        result= BufferDataSet.maybeCopy(ds1);
                                    } else {
                                        result = BufferDataSet.copy(ds1);
                                        int newSize= result.length()*ss.length;
                                        if ( newSize<Integer.MAX_VALUE/2 ) { 
                                            ((BufferDataSet)result).grow((int)(newSize*1.10));  //110%
                                        } else {
                                            ((BufferDataSet)result).grow(newSize);
                                        }
                                    }
                                    //result= checkBoundaries( dr1, result );
                                    //result= checkSort(result);
                                } else {
                                    if ( ss.length==1 ) {
                                        result= ArrayDataSet.maybeCopy(ds1);
                                    } else {
                                        result = ArrayDataSet.copy(ds1);
                                        int newSize= result.length()*ss.length;
                                        if ( newSize<Integer.MAX_VALUE/2) {
                                            ((ArrayDataSet)result).grow((int)(newSize*1.10));  //110%
                                        } else {
                                            ((ArrayDataSet)result).grow(newSize);
                                        }
                                    }
                                    //result= checkBoundaries( dr1, result );
                                    //result= checkSort(result);
                                }
                            }
                        }
                        this.metadata = delegateDataSource.getMetadata(new NullProgressMonitor());
                        cacheRange1 = dr1;

                    } else {
                        if ( addDimension ) {
                            altResult.join(ds1);
                            assert result instanceof JoinDataSet;
                            assert dep0Builder!=null;
                            dep0Builder.nextRecord(dr1.middle());
                        } else if ( ds1 instanceof JoinDataSet ) {
                            assert altResult!=null;
                            altResult.joinAll( (JoinDataSet)ds1 );
                        } else if ( result instanceof BufferDataSet ) {
                            assert result!=null;
                            BufferDataSet bresult= (BufferDataSet)result;
                            BufferDataSet ads1= (BufferDataSet)Ops.maybeCopy( ds1 );
                            //ads1= (BufferDataSet) checkSort(ads1);
                            try {
                                if ( bresult.canAppend(ads1) ) {
                                    bresult.append( ads1 );
                                } else {
                                    bresult.grow( result.length() + ads1.length() * (ss.length-i) );
                                    bresult.append( ads1 );
                                }
                            } catch ( IllegalArgumentException ex ) {
                                throw new IllegalArgumentException( "can't append data from "+delegateUri, ex );
                            } catch ( Exception ex ) {
                                doThrow= true;
                                throw ex; // the exception occurring in the append step was hidden because the code assumed it was a problem with the read.
                            }
                        } else if ( result instanceof ArrayDataSet ) {
                            assert result!=null;
                            ArrayDataSet aresult= ((ArrayDataSet)result);
                            ArrayDataSet ads1= ArrayDataSet.maybeCopy( aresult.getComponentType(),ds1);
                            //ads1= ArrayDataSet.monotonicSubset(ads1);
                            try {
                                if ( aresult.canAppend(ads1) ) {
                                    QDataSet saveAds1= ads1; // note these will be backed by the same data.
                                    //ads1= (ArrayDataSet)checkBoundaries( dr1, ads1 );
                                    //ads1= (ArrayDataSet)trimOverlap( result, ads1 );
                                    if ( ads1.length()!=saveAds1.length() ) {
                                        QDataSet saveDep0= (QDataSet) saveAds1.property(QDataSet.DEPEND_0);
                                        logger.log(Level.WARNING, "data trimmed from dataset to avoid overlap at {0}", saveDep0.slice(0));
                                    }
                                    aresult.append( ads1 );
                                } else {
                                    aresult.grow( result.length() + ads1.length() * (ss.length-i) );
                                    aresult.append( ads1 );
                                }
                            } catch ( IllegalArgumentException ex ) {
                                throw new IllegalArgumentException( "can't append data from "+delegateUri, ex );
                            } catch ( Exception ex ) {
                                doThrow= true;
                                throw ex; // the exception occurring in the append step was hidden because the code assumed it was a problem with the read.
                            }                            
                        } else {
                            assert result instanceof JoinDataSet;
                            assert dep0Builder!=null;
                            ((JoinDataSet)result).join(ds1);
                            dep0Builder.nextRecord(dr1.middle());
                        }

                        //TODO: combine metadata.  We don't have a way of doing this.
                        //this.metadata= null;
                        //this.metadataModel= null;
                        if ( cacheRange1==null ) {
                            logger.info("something happened where cacheRange1 wasn't calculated earlier.");
                            cacheRange1= dr1;
                        } else {
                            cacheRange1 = new DatumRange(cacheRange1.min(), dr1.max());
                        }
                    }
                } catch ( Exception ex ) {
                    if ( doThrow ) {
                        throw ex;
                    }
                    if ( ss.length==1 && ex.getMessage()!=null && ex.getMessage().startsWith("CDFException CDF does not hava a variable named") ) {
                        String ff= getFsm().getRepresentativeFile(mon.getSubtaskMonitor(0,5,"get representative file"));
                        ff= ff + "?" + sparams;
                        delegateDataSource = delegateDataSourceFactory.getDataSource(new URI(ff));
                        try {
                            delegateDataSource.getDataSet(mon.getSubtaskMonitor("getting delegate to see if variable should exist"));
                        } catch ( Exception edelegate ) {
                            if ( edelegate.getMessage().startsWith("CDFException CDF does not hava a variable named") ) {
                                throw ex;
                            } else {
                                ex= new NoDataInIntervalException("one found file does not contain variable");
                            }
                        }
                    }
                    if ( ex instanceof NoDataInIntervalException && ss.length>1 ) {
                        logger.log(Level.FINE, "no data found in {0}", delegateUri);
                        // do nothing
                    } else if ( ss.length==1 ) {
                        throw ex;
                    } else {
                        if ( drex==null ) {
                            throw new RuntimeException("internal error where drex is null because the name didn't belong to the aggregation.");
                        }
                        notesBuilder.putValue(-1,0,drex.min().doubleValue(Units.us2000));
                        notesBuilder.putValue(-1,1,drex.max().doubleValue(Units.us2000));
                        notesBuilder.putValue(-1,2,exunits.createDatum(DataSourceUtil.getMessage(ex)).doubleValue(exunits) );
                        notesBuilder.nextRecord();
                        logger.log( Level.WARNING,ex.getMessage(),ex );
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
                            logger.log(Level.WARNING, "problem in aggregation: {0}", p);
                        }
                    }
                }
                
            }

            mon.finished();

            Map<String,Object> userProps= new HashMap();
            userProps.put( "files", ss );

            if ( cacheRange1!=null ) {
                DatumRange u= DatumRangeUtil.union( cacheRange1, lviewRange );
                if ( !cacheRange1.contains(u) ) {
                    logger.fine("missing files before or after requested span detected");
                    cacheRange1= u;
                }
            }
            
            if ( altResult!=null ) {
                
                if ( addDimension ) {
                    altResult.putProperty( QDataSet.JOIN_0, null );
                    altResult.putProperty( QDataSet.DEPEND_0, dep0Builder.getDataSet() );
                }
                
                DataSetUtil.validate( altResult, new ArrayList<String>() );
            
                ArrayDataSet dep0 = (ArrayDataSet) altResult.property(DDataSet.DEPEND_0);
                Units dep0units= dep0==null ? null : SemanticOps.getUnits(dep0);
                if ( dep0==null ) {
                    dep0= (ArrayDataSet) altResult.property(QDataSet.JOIN_0);
                    QDataSet d= (QDataSet) altResult.property(QDataSet.DEPEND_0,0);
                    if ( d!=null ) dep0units= SemanticOps.getUnits(d);
                    if ( dep0!=null ) dep0.putProperty(QDataSet.UNITS, dep0units );
                }
                if ( dep0 != null && dep0units!=null && cacheRange1.getUnits().isConvertibleTo( dep0units ) ) {
                    dep0.putProperty(QDataSet.CACHE_TAG, new CacheTag(cacheRange1,reduce?lresolution:null));
                    dep0.putProperty(QDataSet.TYPICAL_MIN, lviewRange.min().doubleValue(dep0units) );
                    dep0.putProperty(QDataSet.TYPICAL_MAX, lviewRange.max().doubleValue(dep0units) );
                }

                QDataSet notes= notesBuilder.getDataSet();
                if ( notes.length()>0 ) altResult.putProperty( QDataSet.NOTES, notes );
                altResult.putProperty( QDataSet.USER_PROPERTIES, userProps );

                logger.log(Level.FINE, "loaded {0} {1}", new Object[] { altResult, describeRange(result) } );
                return altResult;

            } else {
                if ( result!=null ) DataSetUtil.validate( result, new ArrayList<String>() );
                MutablePropertyDataSet dep0;
                if ( dep0Builder!=null ) {
                    assert result!=null;
                    dep0= dep0Builder.getDataSet();
                    result.putProperty(QDataSet.DEPEND_0,dep0);
                } else {
                    dep0 = result == null ? null : (MutablePropertyDataSet) result.property(DDataSet.DEPEND_0);
                }
                Units dep0units= dep0==null ? null : SemanticOps.getUnits(dep0);
                if ( dep0 != null && cacheRange1.getUnits().isConvertibleTo( dep0units ) ) {
                    dep0.putProperty(QDataSet.CACHE_TAG, new CacheTag(cacheRange1, reduce?lresolution:null));
                    dep0.putProperty(QDataSet.TYPICAL_MIN, lviewRange.min().doubleValue(dep0units) );
                    dep0.putProperty(QDataSet.TYPICAL_MAX, lviewRange.max().doubleValue(dep0units) );
                }

                QDataSet notes= notesBuilder.getDataSet();
                if ( result!=null && notes.length()>0 ) result.putProperty( QDataSet.NOTES, notes );
                if ( result!=null && !result.isImmutable() ) result.putProperty( QDataSet.USER_PROPERTIES, userProps );
                
                // check to see if all the notes are the same explaining the exception
                if ( result==null && notes.length()>0 ) { 
                    Units u= ((Units)((QDataSet)notes.property(QDataSet.BUNDLE_1)).property(QDataSet.UNITS,2));
                    int n0= (int)notes.value(0,2);
                    StringBuilder nns= new StringBuilder( u.createDatum(n0).toString() );
                    for ( int i=1; i<notes.length(); i++ ) {
                        if ( notes.value(i,2)!=n0 ) {
                            nns.append(",").append(u.createDatum(notes.value(i,2)).toString());
                        }
                    }
                    if ( nns.length()>120 ) {
                        throw new IllegalArgumentException(nns.substring(0,120)+"...");
                    } else {
                        throw new IllegalArgumentException(nns.toString());
                    }
                    
                }
                
                if ( reduce && result!=null && result.rank()==1 ) { // we need to use the series renderer if we have reduced the data.  It shows error bars.
                    result.putProperty( QDataSet.RENDER_TYPE, "series" );
                }
                logger.log(Level.FINE, "loaded {0} {1}", new Object[] { result, describeRange(result) }  );
                return result;
            }
        } catch ( RuntimeException ex ) {
            logger.fine("runtime exception thrown");
            throw ex;
            
        } catch ( Exception ex ) {
            logger.fine("exception thrown");
            throw ex;
        }

    }
    
    private DatumRange describeRange( QDataSet result ) {
        MutablePropertyDataSet dep0 = result == null ? null : (MutablePropertyDataSet) result.property(DDataSet.DEPEND_0);
        if ( dep0==null ) return null;
        QDataSet b= SemanticOps.bounds(dep0).slice(1);
        Units.us2000.createDatum(b.value(0));
        return DatumRangeUtil.roundSections( DataSetUtil.asDatumRange( b,true ), 24 ); 
    }

    @Override
    public MetadataModel getMetadataModel() {
        return metadataModel;
    }

    /**
     * returns the metadata provided by the first delegate dataset.
     * @return 
     * @throws java.lang.Exception 
     */
    @Override
    public Map<String, Object> getMetadata(ProgressMonitor mon) throws Exception {
        if (metadata == null) {
            mon.setTaskSize(10);
            mon.started();
            try {

                URISplit split= URISplit.parse( getURI() );
                Map<String,String> params1= URISplit.parseParams(split.params);
                
                String avail= params1.get( PARAM_AVAIL );
                
                if ( avail!=null && avail.equals("T") ) {
                    return new HashMap<>();
                }
                                
                // bug 1453: arbitrary file is picked from the file storage model, which could be a downloaded file but isn't.
                String scompUrl;
                DatumRange vr= getViewRange();
                if ( vr!=null ) {
                    scompUrl = getFsm().getFileSystem().getRootURI().toString() + getFsm().getRepresentativeFile(mon.getSubtaskMonitor(0,5,"get representative file"),null,vr);
                } else {
                    scompUrl = getFsm().getFileSystem().getRootURI().toString() + getFsm().getRepresentativeFile(mon.getSubtaskMonitor(0,5,"get representative file"));
                }
                if (!sparams.equals("")) {
                    scompUrl += "?" + sparams;
                }
                                
                URI delegateUri= DataSetURI.getURIValid(scompUrl);
                DataSource delegateDataSource = delegateDataSourceFactory.getDataSource(delegateUri);
                metadata = delegateDataSource.getMetadata(mon.getSubtaskMonitor(5,10,"get metadata"));
                metadataModel= delegateDataSource.getMetadataModel();
            } finally {
                mon.finished();
            }
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
    
    protected String getSParams() {
        return this.sparams;
    }

    @Override
    public String getURI() {
        return super.getURI();
    }
    
}
