/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.cdaweb;

import gsfc.nssdc.cdf.CDF;
import java.io.File;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.fsm.FileStorageModelNew;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.cdfdatasource.CdfUtil;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.FileSystemUtil;
import org.virbo.datasource.URISplit;

/**
 *
 * @author jbf
 */
public class CDAWebDataSourceFactory implements DataSourceFactory {

    static {
        loadCdfLibraries();
    }

    /** copied from CdfFileDataSourceFactory **/

    private static void loadCdfLibraries() {
        String cdfLib1 = System.getProperty("cdfLib1");
        String cdfLib2 = System.getProperty("cdfLib2");

        if ( cdfLib1==null && cdfLib2==null ) {
            System.err.println("System properties for cdfLib not set, setting up for debugging");
            String os= System.getProperty("os.name");
            if ( os.startsWith("Windows") ) {
                cdfLib1= "dllcdf";
                cdfLib2= "cdfNativeLibrary";
            } else {
                System.err.println("no values set identifying cdf libraries, hope you're on a mac or linux!");
                System.err.println( System.getProperty("java.library.path" ));
                cdfLib2= "cdfNativeLibrary";
            }
        }

        try {
            // TODO: on Linux systems, may not be able to execute from plug-in media.
            if (cdfLib1 != null) System.loadLibrary(cdfLib1);
            if (cdfLib2 != null) System.loadLibrary(cdfLib2);
        } catch ( UnsatisfiedLinkError ex ) {
            ex.printStackTrace();
            System.err.println( System.getProperty("java.library.path" ));
            throw ex;
        }
    }

    public DataSource getDataSource(URI uri) throws Exception {
        return new CDAWebDataSource(uri);
    }

    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        CDAWebDB.getInstance().maybeRefresh(mon);
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            List<CompletionContext> ccresult= new ArrayList<CompletionContext>(10);
            ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "ds=" ) );
            ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "id=" ) );
            ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "timerange=" ) );
            return ccresult;
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            String param= CompletionContext.get( CompletionContext.CONTEXT_PARAMETER_NAME, cc );
            if ( param.equals("ds") ) {
                Map<String,String> dss= CDAWebDB.getInstance().getServiceProviderIds();
                List<CompletionContext> ccresult= new ArrayList<CompletionContext>(dss.size());
                for ( String ds:dss.keySet() ) {
                    CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, ds, this, null, ds, ds, false  );
                    ccresult.add(cc1);
                }
                return ccresult;
            } else if ( param.equals("timerange") ) {
                URISplit split= URISplit.parse(cc.surl);
                Map<String,String> params= URISplit.parseParams(split.params);
                String ds= params.get("ds").toUpperCase();
                if ( ds!=null ) {
                    CDAWebDB db= CDAWebDB.getInstance();

                    String tr= db.getTimeRange(ds);

                    String tmpl= db.getNaming(ds);
                    String base= db.getBaseUrl(ds);

                    FileSystem fs= FileSystem.create( new URI( base ) );
                    FileStorageModelNew fsm= FileStorageModelNew.create( fs, tmpl );

                    DatumRange dr= DatumRangeUtil.parseTimeRangeValid(tr);
                    String[] names= fsm.getNamesFor(dr);  //TODO: this could be slow
                    String name= names.length>1 ? names[1] : names[0];
                    DatumRange one= fsm.getRangeFor(name);

                    List<CompletionContext> ccresult= new ArrayList<CompletionContext>();
                    String key= one.toString().replaceAll(" ","+");
                    CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, key, this, null, key, key, true  );
                    ccresult.add(cc1);
                    return ccresult;
                    
                }
            } else if ( param.equals("id") ) {
                URISplit split= URISplit.parse(cc.surl);
                Map<String,String> params= URISplit.parseParams(split.params);
                String ds= params.get("ds").toUpperCase();
                if ( ds!=null ) {
                    String master= CDAWebDB.getInstance().getMasterFile( ds.toLowerCase() );

                    File f= FileSystemUtil.doDownload( master, mon );

                    CDF cdf= CDF.open( f.toString(), CDF.READONLYoff );
                    Map<String,String> result= CdfUtil.getPlottable( cdf, true, 4);
                    cdf.close();

                    List<CompletionContext> ccresult= new ArrayList<CompletionContext>();
                    for ( String key:result.keySet() ) {
                        CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, key, this, null, key, result.get(key), true  );
                        ccresult.add(cc1);
                    }
                    return ccresult;
                }


            }
        }
        return new ArrayList<CompletionContext>() {};
    }

    public boolean reject(String surl, ProgressMonitor mon) {
        URISplit split= URISplit.parse(surl);
        Map<String,String> params= URISplit.parseParams(split.params);

        if ( !( params.containsKey("ds") && params.containsKey("id" )&& params.containsKey("timerange") ) ) return true;

        String tr= params.get("timerange");
        try {
            DatumRangeUtil.parseTimeRange(tr);
        } catch ( ParseException ex ) {
            return true;
        }

        if ( params.get("id").equals("") ) return true;
        if ( params.get("ds").equals("") ) return true;

        return false;
    }

}
