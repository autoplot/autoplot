
package org.autoplot.cdaweb;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.autoplot.cdf.CdfDataSource;
import org.das2.dataset.NoDataInIntervalException;
import org.das2.datum.CacheTag;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.CancelledOperationException;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.SubTaskMonitor;
//import org.virbo.cdf.CdfJavaDataSource;
import org.autoplot.cdf.CdfVirtualVars;
import org.das2.qds.ArrayDataSet;
import org.das2.qds.DDataSet;
import org.das2.qds.DataSetUtil;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;
import org.autoplot.datasource.DataSourceRegistry;
import org.autoplot.datasource.MetadataModel;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.DataSetBuilder;
import org.autoplot.metatree.IstpMetadataModel;

/**
 * Special data source for reading time series from NASA Goddard's CDAWeb
 * database.  This uses an XML file to locate data files (soon a web service), and
 * delegates to the CDF file reader to read them.  This code handles the
 * aggregation into a time series.
 *
 * @author jbf
 */
public class CDAWebDataSource extends AbstractDataSource {

    protected static final Logger logger= LoggerManager.getLogger("apdss.cdaweb");

    public static final String PARAM_ID= "id";
    public static final String PARAM_DS= "ds";
    public static final String PARAM_TIMERANGE= "timerange";
    public static final String PARAM_WS= "ws";
    public static final String PARAM_AVAIL= "avail";
    
    public CDAWebDataSource( URI uri ) {
        super(uri);
        String timerange= getParam( "timerange", "2010-01-17" ).replaceAll("\\+", " ");
        try {
            tr = DatumRangeUtil.parseTimeRange(timerange);
        } catch (ParseException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            throw new IllegalArgumentException(ex);
        }
        ds= getParam( "ds","ac_k0_epm" );
        id= getParam( "arg_0", null );
        ws= getParam( PARAM_WS, null );
        
        savail= getParam( PARAM_AVAIL,"F");
        
        if ( id==null ) id= getParam("id","H_lo");

        if ( id==null ) throw new IllegalArgumentException("param not specified");

    }

    Map<String,Object> metadata;

    DatumRange tr;
    String ds;
    String id;
    String ws; // web service.  Null means "T"
    String savail;
    
    /**
     * return the DataSourceFactory that will read the CDF files.  This was once
     * the binary CDF library, and now is the java one.  Either way, it must
     * use the spec: <file>?<id>
     * @return
     */
    private DataSourceFactory getDelegateFactory() {
        DataSourceFactory cdfFileDataSourceFactory= DataSourceRegistry.getInstance().getSource("cdfj");
        return cdfFileDataSourceFactory;
    }

    @Override
    public synchronized QDataSet getDataSet(ProgressMonitor mon) throws Exception {

        CDAWebDB db= CDAWebDB.getInstance();

        {
            // get a file via http so we get a filesystem offline if we are at a hotel.
            // Note the file is small, and if the file is already downloaded, this will only result in a head request.
            if ( !db.isOnline() ) {
                throw new IOException("CDAWeb is not accessible.");
            }
        }

        mon.started();

        MutablePropertyDataSet result= null;
        ArrayDataSet accum = null;

        try {

            try {
                mon.setProgressMessage("refreshing database");
                db.maybeRefresh( SubTaskMonitor.create(mon,0,10) ); //TODO: this ain't right, what's the taskSize?
            } catch ( IOException ex ) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                mon.setProgressMessage("unable to connect via ftp");
                Thread.sleep(1000);
                throw ex;
            }

            String[] files;

            files= db.getFiles( ds.toUpperCase(), tr, ws, mon.getSubtaskMonitor("lookup files") );

            if ( "T".equals( savail ) ) {
                logger.log(Level.FINE, "availablility {0} ", new Object[]{ tr});
                DataSetBuilder build= new DataSetBuilder(2,files.length,4);
                Units u= Units.us2000;
                EnumerationUnits eu= new EnumerationUnits("default");
                for ( String file: files ) {
                    String[] ss= file.split("\\|");
                    file=ss[0];
                    DatumRange dr= DatumRangeUtil.parseTimeRange( ss[1]+ " to "+ ss[2] );
                    build.putValues( -1, DDataSet.wrap( new double[] { dr.min().doubleValue(u), dr.max().doubleValue(u), 0x80FF80, eu.createDatum(ss[0]).doubleValue(eu) } ), 4 );
                    build.nextRecord();
                }
                
                DDataSet tresult= build.getDataSet();

                DDataSet bds= DDataSet.createRank2( 4, 0 );
                bds.putProperty( "NAME__0", "StartTime" );
                bds.putProperty( "UNITS__0", u );
                bds.putProperty( "NAME__1", "StopTime" );
                bds.putProperty( "UNITS__1", u );
                bds.putProperty( "NAME__2", "Color" );
                bds.putProperty( "NAME__3", "Filename" );
                bds.putProperty( "UNITS__3", eu );

                tresult.putProperty( QDataSet.BUNDLE_1, bds );

                tresult.putProperty( QDataSet.RENDER_TYPE, "eventsBar" );
                tresult.putProperty( QDataSet.LABEL, ds.toUpperCase()+"!cAvailability");

                tresult.putProperty( QDataSet.TITLE, ds.toUpperCase()+" Availability" );
                
                return tresult;

            }
            
            DataSourceFactory cdfFileDataSourceFactory= getDelegateFactory();

            mon.setTaskSize(files.length*10+10);
            if ( mon.isCancelled() ) {
                throw new CancelledOperationException("user cancelled task");
            }
            mon.setTaskProgress(0);

            //we need to look in the file to see if it is virtual
            mon.setProgressMessage("getting metadata for "+ds);
            getMetadata( SubTaskMonitor.create( mon,0,10) );

            String virtual= (String) metadata.get( "VIRTUAL" );

            DatumRange range=null;
            
            for ( int i=0; i<files.length; i++ ) {
                if ( mon.isCancelled() ) break;
                
                DatumRange range1;
                
                String file= files[i];
                String[] ss= file.split("\\|");

                file=ss[0];
                range1= DatumRangeUtil.parseTimeRange( ss[1]+ " to "+ ss[2] );

                mon.setTaskProgress((i+1)*10);
                mon.setProgressMessage( "load "+file );

                ProgressMonitor t1= SubTaskMonitor.create( mon, (i+1)*10, (i+2)*10 );

                MutablePropertyDataSet ds1=null;
                try {
                    if ( virtual!=null && !virtual.equals("") ) {
                        int nc=0;
                        List<QDataSet> comps= new ArrayList();
                        String function= (String)metadata.get( "FUNCTION" );
                        if ( function==null ) {
                            function= (String)metadata.get( "FUNCT" ); // THA_L2_ESA
                        }
                        if ( function!=null ) {
                            String comp= (String)metadata.get( "COMPONENT_"  + nc );
                            while ( comp!=null ) {
                                Map<String,String> fileParams= new HashMap(getParams());
                                fileParams.remove( PARAM_TIMERANGE );
                                fileParams.remove( PARAM_DS );
                                fileParams.put( PARAM_ID, comp );
                                URI file1;
                                file1= new URI( file + "?" + URISplit.formatParams(fileParams) );

                                DataSource dataSource= cdfFileDataSourceFactory.getDataSource( file1 );
                                try {
                                    ds1= (MutablePropertyDataSet)dataSource.getDataSet( t1 );
                                } catch ( Exception ex ) {
                                    ds1= null; // !!!!
                                }
                                comps.add( ds1 );
                                nc++;
                                comp= (String) metadata.get( "COMPONENT_"  + nc );
                            }
                            boolean missingComponent= false;
                            for (QDataSet comp1 : comps) {
                                if (comp1 == null) {
                                    missingComponent= true;
                                }
                            }
                            if ( !missingComponent ) {
                                try {
                                    Map<String,Object> qmetadata= new IstpMetadataModel().properties( metadata );
                                    ds1= (MutablePropertyDataSet)CdfVirtualVars.execute( qmetadata, function, comps, t1 );
                                } catch (IllegalArgumentException ex ){
                                    throw new IllegalArgumentException("The virtual variable " + id + " cannot be plotted because the function is not supported: "+function );
                                }
                            }
                        } else {
                        throw new IllegalArgumentException("The virtual variable " + id + " cannot be plotted because the function is not identified" );
                        }
                    } else {
                        Map<String,String> fileParams= new HashMap(getParams());
                        fileParams.remove( PARAM_TIMERANGE );
                        fileParams.remove( PARAM_DS );
                        URI file1;
                        file1= new URI( file + "?" + URISplit.formatParams(fileParams) );

                        logger.log( Level.FINE, "loading {0}", file1);
                        CdfDataSource dataSource= (CdfDataSource)cdfFileDataSourceFactory.getDataSource( file1 );
                        try {
                            ds1= (MutablePropertyDataSet)dataSource.getDataSet( t1,metadata );
                        } catch ( IllegalArgumentException ex ) {
                            String p= params.get(PARAM_ID);
                            logger.log(Level.INFO, "parameter not found for interval: {0}", p );
                            throw new NoDataInIntervalException("parameter not found for interval: "+p );
                        }
                        
                    }
                } catch ( NoDataInIntervalException ex ) {
                    // thrown by where clause...
                }

                if ( ds1!=null ) {
                    if ( result==null && accum==null ) {
                        range= range1;
                        if ( files.length==1 ) {
                            result= (MutablePropertyDataSet)ds1;
                        } else {
                            accum = ArrayDataSet.maybeCopy(ds1);
                            accum.grow(accum.length()*files.length*11/10);  //110%
                        }
                    } else {
                        assert accum!=null; // because files.length>1.
                        ArrayDataSet ads1= ArrayDataSet.maybeCopy(accum.getComponentType(),ds1);
                        if ( accum.canAppend(ads1) ) {
                            accum.append( ads1 );
                        } else {
                            accum.grow( accum.length() + ads1.length() * ( files.length-i) );
                            accum.append( ads1 );
                        }
                        range= DatumRangeUtil.union( range, range1 );
                    }
                } else {
                    logger.log(Level.FINE, "failed to read data for granule: {0}", files[i]);
                }

            }

            if ( result==null ) {
                result= accum;
            }
            
            if ( result!=null && result.property(QDataSet.UNITS)==null && metadata.containsKey("UNIT_PTR_VALUE" ) ) {
                QDataSet unitss= (QDataSet) metadata.get("UNIT_PTR_VALUE");
                boolean allSame= true;
                for ( int i=0; i<unitss.length(); i++ ) {
                    if ( unitss.value(i)!=unitss.value(0) ) {
                        allSame= false;
                    }
                }
                if ( allSame ) result= Ops.putProperty( result, QDataSet.UNITS, Units.lookupUnits(unitss.slice(0).toString()) );
            } 

            if ( result!=null && result.property(QDataSet.DEPEND_1)==null ) { // kludge to learn about master file new HFR-SPECTRA_EMFISIS
                Map dep1p= (Map) metadata.get("DEPEND_1");
                if ( dep1p!=null && dep1p.containsKey("NAME") && result.rank()>1 ) {
                    String dep1= (String)dep1p.get("NAME");
                    String master= db.getMasterFile( ds.toUpperCase(), new NullProgressMonitor() );
                    DataSource masterSource= cdfFileDataSourceFactory.getDataSource( new URI( master+"?"+dep1+"[0]&doDep=no" ) );
                    QDataSet ds1= (MutablePropertyDataSet)masterSource.getDataSet( new NullProgressMonitor() );
                    result= Ops.putProperty( result, QDataSet.DEPEND_1, ds1 );
                }
            }
            
            // kludge to get y labels when they are in the skeleton.
            if ( result!=null && result.rank()==2 ) {
                QDataSet labels= (QDataSet) result.property(QDataSet.DEPEND_1);
                String labelVar= (String)metadata.get( "LABL_PTR_1");
                String renderType= (String)result.property(QDataSet.RENDER_TYPE);
                if ( labelVar!=null && ( renderType==null || renderType.equals("time_series") ) ) {
                    labels=null; 
                }
                if ( labels==null && labelVar!=null ) {
                    String master= db.getMasterFile( ds.toLowerCase(), mon.getSubtaskMonitor("get master file") );
                    DataSource labelDss= getDelegateFactory().getDataSource( DataSetURI.getURI(master+"?"+labelVar) );
                    QDataSet labelDs= (MutablePropertyDataSet)labelDss.getDataSet( new NullProgressMonitor() );
                    if ( labelDs!=null ) {
                        if ( labelDs.rank()>1 && labelDs.length()==1 ) labelDs= labelDs.slice(0);
                        //result= Ops.putProperty( result, QDataSet.BUNDLE_1, DataSetUtil.toBundleDs(labelDs));
                        //TODO: why doesn't this work?!?!?
                    }
                }
            }

            // slice1 datasets should get the labels from the master if we sliced it already.
            String slice1=getParam("slice1", "" ); 
            if ( result!=null && slice1.length()>0 ) { 
                int islice1= Integer.parseInt(slice1);
                String labelVar= (String)metadata.get( "LABL_PTR_1");
                if ( labelVar!=null ) {
                    String master= db.getMasterFile( ds.toLowerCase(), mon.getSubtaskMonitor("get master file")  );
                    DataSource labelDss= getDelegateFactory().getDataSource( DataSetURI.getURI(master+"?"+labelVar) );
                    QDataSet labelDs= (MutablePropertyDataSet)labelDss.getDataSet( new NullProgressMonitor() );
                    if ( labelDs!=null ) {
                        if ( labelDs.rank()>1 && labelDs.length()==1 ) labelDs= labelDs.slice(0);
                        result= Ops.putProperty( result, QDataSet.LABEL, DataSetUtil.getStringValue(labelDs.slice(islice1)).trim() );
                    }
                }
                
            }
            
            // we know the ranges for timeseriesbrowse, kludge around autorange 10% bug.
            if ( result!=null ) {
                MutablePropertyDataSet dep0= (MutablePropertyDataSet) result.property(QDataSet.DEPEND_0);
                if ( dep0!=null && range!=null ) {
                    Units dep0units= (Units) dep0.property(QDataSet.UNITS);
                    dep0= Ops.putProperty( dep0, QDataSet.TYPICAL_MIN, range.min().doubleValue(dep0units) );
                    dep0= Ops.putProperty( dep0, QDataSet.TYPICAL_MAX, range.max().doubleValue(dep0units) );
                    dep0= Ops.putProperty( dep0, QDataSet.CACHE_TAG, new CacheTag(range,null) );
                    result= Ops.putProperty( result, QDataSet.DEPEND_0, dep0 );
                }

                Map<String,String> user= new HashMap<>();
                for ( int i=0; i<Math.min( files.length,10); i++ ) {
                    user.put( "delegate_"+i, files[i] );
                }
                if ( files.length>=10 ) {
                    user.put( "delegate_10", (files.length-10) + " more files." );
                }

                if ( !result.isImmutable() ) {
                    result.putProperty( QDataSet.USER_PROPERTIES, user );
                }
            }
        } finally {
            if ( !mon.isFinished() ) mon.finished();
        }

        if ( result!=null ) {
            String displayType= (String) result.property( QDataSet.RENDER_TYPE );
            if ( displayType!=null && displayType.equals("spectrogram") ) {
                int rank= result.rank();
                int nphys= 0;
                for ( int i=1; i<rank; i++ ) {
                    QDataSet dep1= (QDataSet) result.property(QDataSet.DEPEND_1);
                    if ( dep1==null || !UnitsUtil.isNominalMeasurement(SemanticOps.getUnits(dep1)) ) nphys++;
                }
                if ( nphys==0 ) {
                    logger.fine("removing display type because of ordinal units");
                    result= Ops.putProperty( result, QDataSet.RENDER_TYPE,null );
                }

            }

            List<String> problems= new ArrayList();
            if ( ! DataSetUtil.validate(result,problems) ) {
                throw new Exception("calculated dataset is not well-formed: "+uri + ". " + problems );
            }
        }
        return result;

    }

    @Override
    public Map<String, Object> getMetadata(ProgressMonitor mon) throws Exception {
        if ( "T".equals(this.savail) ) {
            return null;
        }
        if ( metadata==null ) {
            mon.started();
            CDAWebDB db= CDAWebDB.getInstance();

            String master= db.getMasterFile( ds.toLowerCase(), mon.getSubtaskMonitor("getMasterFile") );
            master= master+"?"+id;
            String x= getParam("x",null);
            String y= getParam("y",null);
            if ( x!=null ) master+="&x="+x;
            if ( y!=null ) master+="&y="+y;
            DataSource cdf= getDelegateFactory().getDataSource( DataSetURI.getURI(master) );

            metadata= cdf.getMetadata(mon.getSubtaskMonitor("getMetadata")); // note this is a strange branch, because usually we have read data first.

            String slice1= getParam("slice1","" ); // kludge to grab LABL_PTR_1 when slice1 is used.
            if ( !slice1.equals("") ) {
                metadata.remove( "LABLAXIS" );
                String labelVar= (String)metadata.get("LABL_PTR_1");
                if ( labelVar!=null ) {
                    String master1= db.getMasterFile( ds.toLowerCase(), mon.getSubtaskMonitor("getMasterFile") );
                    DataSource labelDss= getDelegateFactory().getDataSource( DataSetURI.getURI(master1+"?"+labelVar) );
                    QDataSet labelDs= (MutablePropertyDataSet)labelDss.getDataSet( new NullProgressMonitor() );
                    if ( labelDs!=null ) {
                        if ( labelDs.rank()>1 && labelDs.length()==1 ) labelDs= labelDs.slice(0);
                        metadata.put( "LABLAXIS", DataSetUtil.getStringValue(labelDs.slice(Integer.parseInt(slice1)) ) );
                    }
                }
            }
            mon.finished();
        }
        return metadata;
    }

    @Override
    public MetadataModel getMetadataModel() {
        return new IstpMetadataModel();
    }



    /**
     * caution: see also CDAWebDataSourceFactory.getCapability!
     * @param <T>
     * @param clazz
     * @return
     */
    @Override
    public <T> T getCapability(Class<T> clazz) {
        if ( clazz==TimeSeriesBrowse.class ) {
            return (T) new TimeSeriesBrowse() {
                
                @Override
                public void setTimeRange(DatumRange dr) {
                    tr= dr;
                }

                @Override
                public DatumRange getTimeRange() {
                    return tr;
                }

                @Override
                public void setTimeResolution(Datum d) {
                    
                }

                @Override
                public Datum getTimeResolution() {
                    return null;
                }

                @Override
                public String getURI() {
                    Map<String,String> p= getParams();
                    p.put(PARAM_TIMERANGE,tr.toString().replace(' ','+'));
                    return "vap+cdaweb:" + URISplit.formatParams(p);
                }

                @Override
                public String blurURI() {
                    Map<String,String> p= getParams();
                    p.remove("timerange");
                    return "vap+cdaweb:" + URISplit.formatParams( p );
                }

                @Override
                public void setURI(String suri) throws ParseException {
                    URISplit split= URISplit.parse(suri);
                    Map<String,String> params= URISplit.parseParams(split.params);
                    tr= DatumRangeUtil.parseTimeRange(params.get( URISplit.PARAM_TIME_RANGE ) );
                }

            };
        } else {
            return null;
        }
    }


    public static void main( String[] args ) throws URISyntaxException, Exception {
        CDAWebDataSource dss= new CDAWebDataSource( new URI( "vap+cdaweb:file:///foo.xml?ds=cl_sp_fgm&id=B_mag&timerange=2001-10-10") );
        QDataSet ds= dss.getDataSet( new NullProgressMonitor() );
        logger.fine(ds.toString());
    }
}
