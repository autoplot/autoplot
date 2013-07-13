/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

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
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.fsm.FileStorageModelNew;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.SubTaskMonitor;
import org.virbo.cdf.CdfJavaDataSource;
import org.virbo.cdf.CdfVirtualVars;
import org.virbo.dataset.ArrayDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.DataSourceRegistry;
import org.virbo.datasource.MetadataModel;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.metatree.IstpMetadataModel;

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

    public CDAWebDataSource( URI uri ) {
        super(uri);
        String timerange= getParam( "timerange", "2010-01-17" ).replaceAll("\\+", " ");
        try {
            tr = DatumRangeUtil.parseTimeRange(timerange);
        } catch (ParseException ex) {
            logger.log(Level.SEVERE, null, ex);
            throw new IllegalArgumentException(ex);
        }
        ds= getParam( "ds","ac_k0_epm" );
        param= getParam( "arg_0", null );
        if ( param==null ) param= getParam("id","H_lo");

        if ( param==null ) throw new IllegalArgumentException("param not specified");

    }

    Map<String,Object> metadata;

    DatumRange tr;
    String ds;
    String param;

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
            DataSetURI.getFile( "http://cdaweb.gsfc.nasa.gov/istp_public/data/OLD_MASTERS/a1_k0_mpa_00000000_v01.cdf", false, new NullProgressMonitor() );
        }

        mon.started();

        MutablePropertyDataSet result= null;
        ArrayDataSet accum = null;

        try {

            mon.started();

            try {
                db.maybeRefresh( SubTaskMonitor.create(mon,0,10) ); //TODO: this ain't right, what's the taskSize?
            } catch ( IOException ex ) {
                logger.log( Level.SEVERE, null, ex );
                mon.setProgressMessage("unable to connect via ftp");
                Thread.sleep(1000);
                throw ex;
            }

            boolean webService= getParam("ws", "F").equals("T");
            
            String[] files= null;

            String tmpl= db.getNaming(ds.toUpperCase());
            String base= db.getBaseUrl(ds.toUpperCase());

            FileSystem fs= FileSystem.create( new URI( base ) ); // bug3055130 okay
            FileStorageModelNew fsm= FileStorageModelNew.create( fs, tmpl );

            if ( webService ) {
                files= db.getFilesAndRangesFromWebService( ds.toUpperCase(), tr );
            } else {
                logger.log(Level.FINER, "looking for files matching {0}", fsm.toString());
                files= fsm.getBestNamesFor( tr, new NullProgressMonitor() );
            }

            DataSourceFactory cdfFileDataSourceFactory= getDelegateFactory();

            mon.setTaskSize(files.length*10+10);
            mon.setTaskProgress(0);

            //we need to look in the file to see if it is virtual
            mon.setProgressMessage("get metadata");
            getMetadata( SubTaskMonitor.create( mon,0,10) );

            String virtual= (String) metadata.get( "VIRTUAL" );

            DatumRange range=null;

            for ( int i=0; i<files.length; i++ ) {
                if ( mon.isCancelled() ) break;
                
                DatumRange range1=null;
                
                String file= files[i];
                String[] ss= file.split("\\|");
                if ( webService ) {
                    file=ss[0];
                    range1= DatumRangeUtil.parseTimeRange( ss[1]+ " to "+ ss[2] );
                }

                mon.setTaskProgress((i+1)*10);
                mon.setProgressMessage( "load "+file );

                ProgressMonitor t1= SubTaskMonitor.create( mon, (i+1)*10, (i+2)*10 );

                MutablePropertyDataSet ds1=null;
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
                            if ( webService ) {
                                file1= new URI( file + "?" + URISplit.formatParams(fileParams) );
                            } else {
                                file1= fs.getRootURI().resolve( file + "?" + URISplit.formatParams(fileParams) );
                            }
                            CdfJavaDataSource dataSource= (CdfJavaDataSource)cdfFileDataSourceFactory.getDataSource( file1 );
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
                        for ( int j=0; j<comps.size(); j++ ) {
                            if ( comps.get(j)==null ) {
                                missingComponent= true;
                            }
                        }
                        if ( !missingComponent ) {
                            try {
                                Map<String,Object> qmetadata= new IstpMetadataModel().properties( metadata );
                                ds1= (MutablePropertyDataSet)CdfVirtualVars.execute( qmetadata, function, comps, t1 );
                            } catch (IllegalArgumentException ex ){
                                throw new IllegalArgumentException("The virtual variable " + param + " cannot be plotted because the function is not supported: "+function );
                            }
                        }
                    } else {
                    throw new IllegalArgumentException("The virtual variable " + param + " cannot be plotted because the function is not identified" );
                    }
                } else {
                    Map<String,String> fileParams= new HashMap(getParams());
                    fileParams.remove( PARAM_TIMERANGE );
                    fileParams.remove( PARAM_DS );
                    URI file1;
                    if ( webService ) {
                        file1= new URI( file + "?" + URISplit.formatParams(fileParams) );
                    } else {
                        file1= fs.getRootURI().resolve( file + "?" + URISplit.formatParams(fileParams) );
                    }
                    logger.log( Level.FINE, "loading {0}", file1);
                    CdfJavaDataSource dataSource= (CdfJavaDataSource)cdfFileDataSourceFactory.getDataSource( file1 );
                    ds1= (MutablePropertyDataSet)dataSource.getDataSet( t1,metadata );
                }

                if ( ds1!=null ) {
                    if ( result==null && accum==null ) {
                        range= webService ? range1 : fsm.getRangeFor(files[i]);
                        if ( files.length==1 ) {
                            result= (MutablePropertyDataSet)ds1;
                        } else {
                            accum = ArrayDataSet.maybeCopy(ds1);
                            accum.grow(accum.length()*files.length*11/10);  //110%
                        }
                    } else {
                        ArrayDataSet ads1= ArrayDataSet.maybeCopy(accum.getComponentType(),ds1);
                        if ( accum.canAppend(ads1) ) {
                            accum.append( ads1 );
                        } else {
                            accum.grow( accum.length() + ads1.length() * ( files.length-i) );
                            accum.append( ads1 );
                        }
                        range= DatumRangeUtil.union( range, webService ? range1 : fsm.getRangeFor(files[i]) );
                    }
                } else {
                    logger.log(Level.FINE, "failed to read data for granule: {0}", files[i]);
                }

            }

            if ( result==null ) {
                result= accum;
            }

            if ( result!=null && result.property(QDataSet.DEPEND_1)==null ) { // kludge to learn about master file new HFR-SPECTRA_EMFISIS
                Map dep1p= (Map) metadata.get("DEPEND_1");
                if ( dep1p!=null && dep1p.containsKey("NAME") ) {
                    String dep1= (String)dep1p.get("NAME");
                    String master= db.getMasterFile( ds.toUpperCase(), new NullProgressMonitor() );
                    CdfJavaDataSource masterSource= (CdfJavaDataSource)cdfFileDataSourceFactory.getDataSource( new URI( master+"?"+dep1+"[0]&doDep=no" ) );
                    QDataSet ds1= (MutablePropertyDataSet)masterSource.getDataSet( new NullProgressMonitor() );
                    result.putProperty( QDataSet.DEPEND_1, ds1 );
                }
            }
            
            // kludge to get y labels when they are in the skeleton.
            if ( result!=null && result.rank()==2 ) {
                QDataSet labels= (QDataSet) result.property(QDataSet.DEPEND_1);
                String labelVar= (String)metadata.get( "LABL_PTR_1");
                if ( labelVar!=null && result.property(QDataSet.RENDER_TYPE).equals("time_series") ) {
                    labels=null;
                }
                if ( labels==null && labelVar!=null ) {
                    String master= db.getMasterFile( ds.toLowerCase(), mon );
                    DataSource labelDss= getDelegateFactory().getDataSource( DataSetURI.getURI(master+"?"+labelVar) );
                    QDataSet labelDs= (MutablePropertyDataSet)labelDss.getDataSet( new NullProgressMonitor() );
                    if ( labelDs!=null ) {
                        if ( labelDs.rank()>1 && labelDs.length()==1 ) labelDs= labelDs.slice(0);
                        result.putProperty( QDataSet.DEPEND_1, labelDs );
                    }
                }
            }

            // slice1 datasets should get the labels from the master if we sliced it already.
            String slice1=getParam("slice1", "" ); 
            if ( result!=null && slice1.length()>0 ) { 
                int islice1= Integer.parseInt(slice1);
                String labelVar= (String)metadata.get( "LABL_PTR_1");
                if ( labelVar!=null ) {
                    String master= db.getMasterFile( ds.toLowerCase(), mon );
                    DataSource labelDss= getDelegateFactory().getDataSource( DataSetURI.getURI(master+"?"+labelVar) );
                    QDataSet labelDs= (MutablePropertyDataSet)labelDss.getDataSet( new NullProgressMonitor() );
                    if ( labelDs!=null ) {
                        if ( labelDs.rank()>1 && labelDs.length()==1 ) labelDs= labelDs.slice(0);
                        result.putProperty( QDataSet.LABEL, DataSetUtil.getStringValue(labelDs.slice(islice1)) );
                    }
                }
                
            }
            
            // we know the ranges for timeseriesbrowse, kludge around autorange 10% bug.
            if ( result!=null ) {
                MutablePropertyDataSet dep0= (MutablePropertyDataSet) result.property(QDataSet.DEPEND_0);
                if ( dep0!=null && range!=null ) {
                    Units dep0units= (Units) dep0.property(QDataSet.UNITS);
                    dep0.putProperty( QDataSet.TYPICAL_MIN, range.min().doubleValue(dep0units) );
                    dep0.putProperty( QDataSet.TYPICAL_MAX, range.max().doubleValue(dep0units) );
                }

                Map<String,String> user= new HashMap<String, String>();
                for ( int i=0; i<Math.min( files.length,10); i++ ) {
                    user.put( "delegate_"+i, fs.toString() + files[i] );
                }
                if ( files.length>=10 ) {
                    user.put( "delegate_10", (files.length-10) + " more files from " + base + "/" + tmpl );
                }

                result.putProperty( QDataSet.USER_PROPERTIES, user );
            }
        } finally {
            mon.finished();
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
                    logger.fine("removing display type becuayse of ordinal units");
                    result.putProperty( QDataSet.RENDER_TYPE,null );
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
        if ( metadata==null ) {

            CDAWebDB db= CDAWebDB.getInstance();

            String master= db.getMasterFile( ds.toLowerCase(), mon );
            master= master+"?"+param;
            
            DataSource cdf= getDelegateFactory().getDataSource( DataSetURI.getURI(master) );

            metadata= cdf.getMetadata(mon); // note this is a strange branch, because usually we have read data first.

            String slice1= getParam("slice1","" ); // kludge to grab LABL_PTR_1 when slice1 is used.
            if ( !slice1.equals("") ) {
                metadata.remove( "LABLAXIS" );
                String labelVar= (String)metadata.get("LABL_PTR_1");
                if ( labelVar!=null ) {
                    String master1= db.getMasterFile( ds.toLowerCase(), mon );
                    DataSource labelDss= getDelegateFactory().getDataSource( DataSetURI.getURI(master1+"?"+labelVar) );
                    QDataSet labelDs= (MutablePropertyDataSet)labelDss.getDataSet( new NullProgressMonitor() );
                    if ( labelDs!=null ) {
                        if ( labelDs.rank()>1 && labelDs.length()==1 ) labelDs= labelDs.slice(0);
                        metadata.put( "LABLAXIS", DataSetUtil.getStringValue(labelDs.slice(Integer.parseInt(slice1)) ) );
                    }
                }
            }
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

                public void setTimeRange(DatumRange dr) {
                    tr= dr;
                }

                public DatumRange getTimeRange() {
                    return tr;
                }

                public void setTimeResolution(Datum d) {
                    
                }

                public Datum getTimeResolution() {
                    return null;
                }

                public String getURI() {
                    return "vap+cdaweb:ds="+ds+"&id="+param+"&timerange="+tr.toString().replace(" ", "+");
                }

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
