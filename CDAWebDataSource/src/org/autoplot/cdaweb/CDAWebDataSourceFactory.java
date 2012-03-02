/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.cdaweb;

import gov.nasa.gsfc.voyager.cdf.CDFFactory;
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
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.DefaultTimeSeriesBrowse;
import org.virbo.datasource.FileSystemUtil;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.capability.TimeSeriesBrowse;

/**
 * Create a CDAWebDataSource.  The source contains knowledge of the CDAWeb database, knowing
 * how the files are stored, aggregation templates, and metadata describing the dataset.
 * @author jbf
 */
public class CDAWebDataSourceFactory implements DataSourceFactory {

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
                    String master= CDAWebDB.getInstance().getMasterFile( ds.toLowerCase(), mon );

                    File f= FileSystemUtil.doDownload( master, mon );

                    Map<String,String> result;
                    //boolean useNewLibrary= true; // Use Nand's Java library
                    //if ( useNewLibrary ) {
                        gov.nasa.gsfc.voyager.cdf.CDF cdf;
                        try {
                            cdf = CDFFactory.getCDF(f.toString());
                        } catch (Throwable ex) {
                            throw new RuntimeException(ex);
                        }
                        result= org.virbo.cdf.CdfUtil.getPlottable( cdf, true, 4);
                    //} else {
                        // This code is for the C-based Java library
                        //CDF cdf= CDF.open( f.toString(), CDF.READONLYoff );
                        //result= CdfUtil.getPlottable( cdf, true, 4);
                        //cdf.close();
                    //}
                    

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

    public <T> T getCapability(Class<T> clazz) {
        if ( clazz==TimeSeriesBrowse.class ) {
           return (T) new DefaultTimeSeriesBrowse();
        }
        return null;
    }


    public boolean reject(String surl, ProgressMonitor mon) {
        URISplit split= URISplit.parse(surl);
        Map<String,String> params= URISplit.parseParams(split.params);

        if ( !( params.containsKey("ds") && params.containsKey("id" )&& params.containsKey("timerange") ) ) return true;

        String tr= params.get("timerange");
        if ( tr==null ) {
            return true;
        } else {
            tr= tr.replaceAll("\\+", " ");
        }
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
