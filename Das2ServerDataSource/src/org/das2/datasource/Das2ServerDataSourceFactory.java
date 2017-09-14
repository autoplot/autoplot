/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.das2.datasource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.capability.TimeSeriesBrowse;

/**
 * DataSourceFactory for communicating with Das2servers.
 * @author jbf
 */
public class Das2ServerDataSourceFactory implements DataSourceFactory {

    private static final Logger logger= LoggerManager.getLogger("apdss.das2server");

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new Das2ServerDataSource(uri);
    }
    Map<String, List<String>> datasetsList = null;

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        //http://www-pw.physics.uiowa.edu/das/das2Server
        //?dataset=das2_1/voyager1/pws/sa-4s-pf.new
        //&start_time=2004-01-01&end_time=2004-01-06&server=dataset&ascii=1

        List<CompletionContext> result = new ArrayList<>();

        if (cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "dataset=", "dataset identifier"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "start_time=", "ISO8601 start time"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "end_time=", "ISO8601 end time"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "intrinsic=true", "do not reduce on server"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "interval=", "cadence in seconds for TCAs"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "item=", "item number for TCAs"));
        } else if (cc.context == CompletionContext.CONTEXT_PARAMETER_VALUE) {
            String paramName = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            if (paramName.equals("dataset")) {
                URI uri= cc.resourceURI;
                if ( uri==null ) throw new IllegalArgumentException("expected das2server location");
                List<String> dss= getDatasetsList( uri.toString() ); // bug 3055130 okay
                for ( String ds: dss ) {
                    if ( ds.startsWith(cc.completable) ) {
                        int i= ds.indexOf('|');
                        if ( i==-1 ) {
                            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, ds ) );
                        } else {
                            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, ds.substring(0,i), ds.substring(i+1).trim() ) );
                        }
                    }
                }
            }
        }

        return result;

    }

    private synchronized List<String> getDatasetsList(String surl) {
        if (datasetsList == null) {
            datasetsList = new HashMap<>();
        }
        List<String> result = datasetsList.get(surl);
        
        if (result == null) {
            BufferedReader reader = null;
            try {
                URL url = new URL(surl + "?server=list");
                reader = new BufferedReader(new InputStreamReader(url.openStream(),"US-ASCII"));
                String s = reader.readLine();
                ArrayList<String> list = new ArrayList<>();
                while (s != null) {
                    list.add(s);
                    s = reader.readLine();
                }
                datasetsList.put( surl, list );
            } catch (IOException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                throw new RuntimeException(ex);
            } finally {
                try {
                    if ( reader!=null ) reader.close();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
        }
        
        return datasetsList.get(surl);
        
    }

    public static final String PROB_DS= "Dataset ID is not specified";
    public static final String PROB_TIMERANGE= "Timerange is not specified";
    
    /**
     * Indicate if a URI is acceptable.
     * <pre>
     * {@code
     * vap+das2server:http://www-pw.physics.uiowa.edu/das/das2Server?galileo/pws/EDPosition.dsdf&timerange=2001-10-17
     * }
     * </pre>
     * @param surl
     * @param problems
     * @param mon
     * @return true if it is not acceptable.
     */
    @Override
    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        URISplit split= URISplit.parse( surl );
        Map<String,String> params= URISplit.parseParams(split.params);
        String ds= params.get("dataset");
        if ( params.get("arg_0")!=null ) {
            ds=  params.get("arg_0");
        }
        if ( ds==null || ds.length()==0 ) {
            problems.add( PROB_DS );
            return true;
        }
        if ( ds.endsWith("/") ) {
            problems.add( PROB_DS );
            return true;
        }
        String str= params.get("timerange");
        if ( str!=null ) {
            str= str.replaceAll("\\+"," ");
            try {
                DatumRange tr= DatumRangeUtil.parseTimeRange( str );
                params.put( "start_time", tr.min().toString() );
                params.put( "end_time", tr.max().toString() );    
            } catch (ParseException ex) {
                logger.log(Level.WARNING, "unable to parse timerange {0}", str);
            }
        }
        if ( !( params.containsKey("start_time") && params.containsKey("end_time") ) ) {
            problems.add( PROB_TIMERANGE );
            return true;
        }
        return false;
    }

    @Override
    public boolean supportsDiscovery() {
        return true; //TODO: completions should support this.
    }

    
    @Override
    public <T> T getCapability(Class<T> clazz) {
        if ( clazz== TimeSeriesBrowse.class ) {
           return (T) new Das2ServerTimeSeriesBrowse();
        }
        return null;
    }
}
