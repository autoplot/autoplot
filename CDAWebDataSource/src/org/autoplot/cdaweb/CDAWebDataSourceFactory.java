/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.cdaweb;

import gov.nasa.gsfc.spdf.cdfj.CDFReader;
import java.io.File;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import org.autoplot.cdf.CdfDataSource;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.fsm.FileStorageModel;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;
import org.autoplot.datasource.DefaultTimeSeriesBrowse;
import org.autoplot.datasource.FileSystemUtil;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.das2.datum.Datum;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;

/**
 * Create a CDAWebDataSource.  The source contains knowledge of the CDAWeb database, knowing
 * how the files are stored, aggregation templates, and metadata describing the dataset.
 * @author jbf
 */
public class CDAWebDataSourceFactory implements DataSourceFactory {

    protected static final Logger logger= LoggerManager.getLogger("apdss.cdaweb");

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new CDAWebDataSource(uri);
    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        CDAWebDB.getInstance().maybeRefresh(mon);
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            List<CompletionContext> ccresult= new ArrayList<>(10);
            ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "ds=" ) );
            ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "id=" ) );
            ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "timerange=" ) );
            ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "ws=", "ws=", "use web service" ) );
            return ccresult;
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            String param= CompletionContext.get( CompletionContext.CONTEXT_PARAMETER_NAME, cc );
            switch (param) {
                case "ds":
                {
                    Map<String,String> dss= CDAWebDB.getInstance().getServiceProviderIds();
                    List<CompletionContext> ccresult= new ArrayList<>(dss.size());
                    for ( String ds:dss.keySet() ) {
                        CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, ds, this, null, ds, ds, false  );
                        ccresult.add(cc1);
                    }
                    return ccresult;
                }
                case "timerange":
                    {
                        URISplit split= URISplit.parse(cc.surl);
                        Map<String,String> params= URISplit.parseParams(split.params);
                        String ds= params.get("ds");
                        if ( ds!=null && ds.length()>0 ) {
                            ds= ds.toUpperCase();
                            CDAWebDB db= CDAWebDB.getInstance();
                            
                            String tr= db.getSampleTime(ds);
                            
                            List<CompletionContext> ccresult= new ArrayList<>();
                            String key= tr.replaceAll(" ","+");
                            CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, key, this, null, key, key, true  );
                            ccresult.add(cc1);
                            return ccresult;
                            
                        }       
                        break;
                    }
                case "id":
                    {
                        URISplit split= URISplit.parse(cc.surl);
                        Map<String,String> params= URISplit.parseParams(split.params);
                        String ds= params.get("ds");
                        if ( ds!=null && ds.length()>0 ) {
                            ds= ds.toUpperCase();
                            String master= CDAWebDB.getInstance().getMasterFile( ds.toLowerCase(), mon );
                            
                            File f= FileSystemUtil.doDownload( master, mon );
                            
                            Map<String,String> result;
                            
                            CDFReader cdf;
                            cdf = CdfDataSource.getCdfFile(f.toString());
                            result= org.autoplot.cdf.CdfUtil.getPlottable( cdf, true, 4);
                            
                            List<CompletionContext> ccresult= new ArrayList<>();
                            for ( Entry<String,String> e:result.entrySet() ) {
                                String key= e.getKey();
                                CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, key, this, null, key, e.getValue(), true  );
                                ccresult.add(cc1);
                            }
                            return ccresult;
                        }       break;
                    }
                case "ws":
                {
                    List<CompletionContext> ccresult= new ArrayList<>(10);
                    ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "T", "T", "use webservice" ) );
                    ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "F", "F", "use files for offline use" ) );
                    return ccresult;
                }
                default:
                    break;
            }
        }
        return new ArrayList<CompletionContext>() {};
    }

    @Override
    public <T> T getCapability(Class<T> clazz) {
        if ( clazz==TimeSeriesBrowse.class ) {
           return (T) new DefaultTimeSeriesBrowse();
        }
        return null;
    }


    @Override
    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        URISplit split= URISplit.parse(surl);
        Map<String,String> params= URISplit.parseParams(split.params);

        if ( !( params.containsKey("ds") && ( params.containsKey("id" ) || "T".equals( params.get("avail") ) ) && params.containsKey("timerange") ) ) return true;

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

        if ( params.get("ds").equals("") ) return true;

        if ( "T".equals( params.get("avail") ) ) {
            return false;
        } else {
            if ( params.get("id").equals("") ) return true;

            String slice1= params.get("slice1");
            if ( slice1!=null ) {
                try {
                    Integer.parseInt(slice1);
                } catch ( NumberFormatException ex ) {
                    problems.add("misformatted slice");
                    return true;
                }
            }
        
            return false;
        }
    }

    @Override
    public boolean supportsDiscovery() {
        return true;
    }

    @Override
    public boolean isFileResource() {
        return false;
    }

    @Override
    public String getDescription() {
        return "NASA/Goddard CDAWeb";
    }
    
}
