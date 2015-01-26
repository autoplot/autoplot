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
import org.autoplot.bufferdataset.BufferDataSet;
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
import org.virbo.dataset.ArrayDataSet;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.JoinDataSet;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.dataset.SortDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.DataSourceUtil;
import org.virbo.datasource.MetadataModel;
import org.virbo.datasource.ReferenceCache;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.Version;
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

    /**
     * message used when no files are found in the interval.
     */
    public static final String MSG_NO_FILES_FOUND = "No files in interval";


    private FileStorageModel fsm;
    DataSourceFactory delegateDataSourceFactory;
    AggregationPollUpdating upd; // allow a group of files to be watched.  This is experimental.
    
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
        tsb= createTimeSeriesBrowse();
        addCability(TimeSeriesBrowse.class, tsb );
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
            addCability( Updating.class, upd );
        }
    }


    private TimeSeriesBrowse createTimeSeriesBrowse() {
        return new AggTimeSeriesBrowse();
    }
    
    /**
     * It's easy for programs to mess up and contain timetags that are 24 hours off, so check that the
     * tags are not more than 50% outside the bounds.
     * @param bounds the expected bounds for the data
     * @param ads0 the data which ought to be within these bounds.
     * @return the dataset, possibly trimmed to exclude miscalculated times.
     */
    private ArrayDataSet checkBoundaries( DatumRange bounds, ArrayDataSet ads0 ) {
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
            return ArrayDataSet.maybeCopy( ads0.trim( ist,ien ) );
        } else {
            return ads0;
        }
    }

    /**
     * check/ensure that no data overlaps from ads0 to ads1.  See 
     * rfe https://sourceforge.net/p/autoplot/feature-requests/391/
     * @param ads0 the first dataset that will be appended
     * @param ads1 the second dataset that will be appended.
     * @return ads1, possibly trimmed.
     */
    private ArrayDataSet trimOverlap(QDataSet ads0, ArrayDataSet ads1) {
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
            return ads1;
        } else {
            int i=0;
            while ( i<dep0_1.length() && Ops.le( dep0_1.slice(i), dep0_0.slice(dep0_0.length()-1) ).value()==1 ) {
                i=i+1;
            }
            return (ArrayDataSet)ads1.trim(i,ads1.length());
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
        
        ReferenceCache.ReferenceCacheEntry cacheEntry=null;
        if ( useReferenceCache ) {
            cacheEntry= ReferenceCache.getInstance().getDataSetOrLock( this.tsb.getURI(), mon);
            if ( !cacheEntry.shouldILoad( Thread.currentThread() ) ) {
                try {
                    logger.log(Level.FINE, "wait for other thread {0}", uri);
                    QDataSet result= cacheEntry.park( mon );
                    return result;
                } catch ( Exception ex ) {
                    throw ex;
                }
            } else {
                logger.log(Level.FINE, "reference cache in use, {0} is loading {1}", new Object[] { Thread.currentThread().toString(), resourceURI } );
            }
        }
        
        logger.log(Level.FINE, "reading {0}", uri );

        try {
            DatumRange lviewRange= viewRange;
            Datum lresolution= resolution;

            mon.started();
            
            String[] ss = getFsm().getBestNamesFor( lviewRange, mon.getSubtaskMonitor(sparams) );
            
            if ( "true".equals( System.getProperty( Version.PROP_ENABLE_CLEAN_CACHE ) ) ) {
                logger.fine("enableCleanCache is true");
                getFsm().cacheCleanup();
            }
            
            boolean avail= getParam( "avail", "F" ).equals("T");
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
                    
                    QDataSet ds1 = delegateDataSource.getDataSet(mon1);
                    logger.log(Level.FINER, "  read: {0}", new Object[]{ ds1 } );
                    
                    if ( ds1==null ) {
                        logger.warning("delegate returned null");
                        ds1 = delegateDataSource.getDataSet(mon1);
                        if ( ds1==null ) continue;
                    }
                    
                    QDataSet xds= SemanticOps.xtagsDataSet(ds1);
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
                        }
                    }

                    if ( reduce && ds1.rank()<3 && SemanticOps.isTimeSeries(ds1) ) {
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
                                }
                                logger.log(Level.FINER, "dataset trimmed to {0}", ds1);
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

                    if (result == null && altResult==null ) {
                        if ( ds1 instanceof JoinDataSet ) {
                            altResult= JoinDataSet.copy( (JoinDataSet)ds1 );
                            DDataSet mpds= DDataSet.create(new int[0]);
                            altResult.putProperty(QDataSet.JOIN_0,mpds );
                        } else {
//                            if ( ds1 instanceof BufferDataSet ) {
//                                if ( ss.length>1 ) {
//                                    result = BufferDataSet.copy(ds1);
//                                    result.grow(result.length()*ss.length*11/10);  //110%
//                                }
//                                result= checkBoundaries( dr1, result );
//                                result= ArrayDataSet.monotonicSubset(result);
//                            } else {
                                if ( ss.length==1 ) {
                                    result= ArrayDataSet.maybeCopy(ds1);
                                } else {
                                    result = ArrayDataSet.copy(ds1);
                                    result.grow(result.length()*ss.length*11/10);  //110%
                                }
                                result= checkBoundaries( dr1, result );
                                result= ArrayDataSet.monotonicSubset(result);
                            //}
                        }
                        this.metadata = delegateDataSource.getMetadata(new NullProgressMonitor());
                        cacheRange1 = dr1;

                    } else {
                        if ( ds1 instanceof JoinDataSet ) {
                            assert altResult!=null;
                            altResult.joinAll( (JoinDataSet)ds1 );
                        } else {
                            assert result!=null;
                            ArrayDataSet ads1= ArrayDataSet.maybeCopy(result.getComponentType(),ds1);
                            ads1= ArrayDataSet.monotonicSubset(ads1);
                            try {
                                if ( result.canAppend(ads1) ) {
                                    QDataSet saveAds1= ads1; // note these will be backed by the same data.
                                    ads1= checkBoundaries( dr1, ads1 );
                                    ads1= trimOverlap( result, ads1 );
                                    if ( ads1.length()!=saveAds1.length() ) {
                                        QDataSet saveDep0= (QDataSet) saveAds1.property(QDataSet.DEPEND_0);
                                        logger.log(Level.WARNING, "data trimmed from dataset to avoid overlap at {0}", saveDep0.slice(0));
                                    }
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

            if ( altResult!=null ) {
                ArrayDataSet dep0 = (ArrayDataSet) altResult.property(DDataSet.DEPEND_0);
                Units dep0units= dep0==null ? null : SemanticOps.getUnits(dep0);
                if ( dep0==null ) {
                    dep0= (ArrayDataSet) altResult.property(QDataSet.JOIN_0);
                    QDataSet d= (QDataSet) altResult.property(QDataSet.DEPEND_0,0);
                    if ( d!=null ) dep0units= SemanticOps.getUnits(d);
                    if ( dep0!=null ) dep0.putProperty(QDataSet.UNITS, dep0units );
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
                logger.log(Level.FINE, "loaded {0} {1}", new Object[] { altResult, describeRange(result) } );
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
        } catch ( Exception ex ) {
            if ( cacheEntry!=null ) cacheEntry.exception(ex);
            throw ex;
        }

    }
    
    private DatumRange describeRange( QDataSet result ) {
        MutablePropertyDataSet dep0 = result == null ? null : (MutablePropertyDataSet) result.property(DDataSet.DEPEND_0);
        if ( dep0==null ) return null;
        QDataSet b= SemanticOps.bounds(dep0).slice(1);
        return DatumRangeUtil.roundSections( DataSetUtil.asDatumRange( b,true ), 24 ); 
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
            try {
                String scompUrl = getFsm().getFileSystem().getRootURI().toString() + getFsm().getRepresentativeFile(mon.getSubtaskMonitor(0,5,"get representative file"));
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

    @Override
    public String getURI() {
        return super.getURI();
    }
    
}
