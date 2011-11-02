/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.cdaweb;

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
import org.das2.fsm.FileStorageModelNew;
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

    public static final String PARAM_ID= "id";
    public static final String PARAM_DS= "ds";
    public static final String PARAM_TIMERANGE= "timerange";

    public CDAWebDataSource( URI uri ) {
        super(uri);
        String timerange= getParam( "timerange", "2010-01-17" ).replaceAll("\\+", " ");
        try {
            tr = DatumRangeUtil.parseTimeRange(timerange);
        } catch (ParseException ex) {
            Logger.getLogger(CDAWebDataSource.class.getName()).log(Level.SEVERE, null, ex);
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

        mon.started();

        MutablePropertyDataSet result= null;
        ArrayDataSet accum = null;

        try {
            mon.setProgressMessage("refreshing database");
            db.maybeRefresh( new NullProgressMonitor() );

            String tmpl= db.getNaming(ds.toUpperCase());
            String base= db.getBaseUrl(ds.toUpperCase());

            FileSystem fs= FileSystem.create( new URI( base ) );
            FileStorageModelNew fsm= FileStorageModelNew.create( fs, tmpl );

            String[] files= fsm.getBestNamesFor( tr, new NullProgressMonitor() );

            DataSourceFactory cdfFileDataSourceFactory= getDelegateFactory();

            mon.setTaskSize(files.length*10);
            mon.started();

            //we need to look in the file to see if it is virtual
            mon.setProgressMessage("get metadata");
            getMetadata(mon);
            mon.started(); //kludge

            String virtual= (String) metadata.get( "VIRTUAL" );

            DatumRange range=null;

            for ( int i=0; i<files.length; i++ ) {
                if ( mon.isCancelled() ) break;
                mon.setTaskProgress(i*10);
                mon.setProgressMessage( "load "+files[i] );

                ProgressMonitor t1= SubTaskMonitor.create( mon, i*10, (i+1)*10 );

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
                            CdfJavaDataSource dataSource= (CdfJavaDataSource)cdfFileDataSourceFactory.getDataSource( fs.getRootURI().resolve(files[i] + "?" + comp ) );
                            ds1= (MutablePropertyDataSet)dataSource.getDataSet( t1 );
                            comps.add( ds1 );
                            nc++;
                            comp= (String) metadata.get( "COMPONENT_"  + nc );
                        }
                        try {
                            Map<String,Object> qmetadata= new IstpMetadataModel().properties( metadata );
                            ds1= (MutablePropertyDataSet)CdfVirtualVars.execute( qmetadata, function, comps, mon );
                        } catch (IllegalArgumentException ex ){
                            throw new IllegalArgumentException("The virtual variable " + param + " cannot be plotted because the function is not supported: "+function );
                        }
                    } else {
                    throw new IllegalArgumentException("The virtual variable " + param + " cannot be plotted because the function is not identified" );
                    }
                } else {
                    Map<String,String> fileParams= getParams();
                    fileParams.remove( PARAM_TIMERANGE );
                    fileParams.remove( PARAM_DS );
                    System.err.println( "loading "+fs.getRootURI().resolve(files[i] + "?" + URISplit.formatParams(fileParams) ) );
                    CdfJavaDataSource dataSource= (CdfJavaDataSource)cdfFileDataSourceFactory.getDataSource( fs.getRootURI().resolve(files[i] + "?" + URISplit.formatParams(fileParams) ) );
                    ds1= (MutablePropertyDataSet)dataSource.getDataSet( t1,metadata );
                }

                if ( result==null && accum==null ) {
                    range= fsm.getRangeFor(files[i]);
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
                    range= DatumRangeUtil.union( range,fsm.getRangeFor(files[i]) );
                }

            }

            if ( result==null ) {
                result= accum;
            }

            // kludge to get y labels when they are in the skeleton.
            if ( result!=null && result.rank()==2 ) {
                QDataSet labels= (QDataSet) result.property(QDataSet.DEPEND_1);
                String labelVar= (String)metadata.get( "LABL_PTR_1");
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
            List<String> problems= new ArrayList();
            if ( ! DataSetUtil.validate(result,problems) ) {
                throw new Exception("calculated dataset is not well-formed: "+uri );
            }
        }
        return result;

    }

    @Override
    public Map<String, Object> getMetadata(ProgressMonitor mon) throws Exception {
        if ( metadata==null ) {

            CDAWebDB db= CDAWebDB.getInstance();

            String master= db.getMasterFile( ds.toLowerCase(), mon );

            DataSource cdf= getDelegateFactory().getDataSource( DataSetURI.getURI(master+"?"+param) );

            metadata= cdf.getMetadata(mon); // note this is a strange branch, because usually we have read data first.
        }
        return metadata;
    }

    @Override
    public MetadataModel getMetadataModel() {
        return new IstpMetadataModel();
    }



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
                    return "vap+cdaweb:ds="+ds+"&"+param+"&timerange="+tr.toString().replace(" ", "+");
                }

            };
        } else {
            return null;
        }
    }


    public static void main( String[] args ) throws URISyntaxException, Exception {
        CDAWebDataSource dss= new CDAWebDataSource( new URI( "vap+cdaweb:file:///foo.xml?ds=cl_sp_fgm&id=B_mag&timerange=2001-10-10") );
        QDataSet ds= dss.getDataSet( new NullProgressMonitor() );
        System.err.println(ds);
    }
}
