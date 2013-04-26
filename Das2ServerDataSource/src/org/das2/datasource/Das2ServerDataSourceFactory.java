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
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.capability.TimeSeriesBrowse;

/**
 *
 * @author jbf
 */
public class Das2ServerDataSourceFactory implements DataSourceFactory {

    private static final Logger logger= LoggerManager.getLogger("apdss.das2server");

    public DataSource getDataSource(URI uri) throws Exception {
        return new Das2ServerDataSource(uri);
    }
    Map<String, List<String>> datasetsList = null;

    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        //http://www-pw.physics.uiowa.edu/das/das2Server
        //?dataset=das2_1/voyager1/pws/sa-4s-pf.new
        //&start_time=2004-01-01&end_time=2004-01-06&server=dataset&ascii=1

        List<CompletionContext> result = new ArrayList<CompletionContext>();

        if (cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "dataset=", "dataset identifier"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "start_time=", "ISO8601 start time"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "end_time=", "ISO8601 end time"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "resolution=", "resolution in seconds"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "interval=", "cadence in seconds for TCAs"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "item=", "item number for TCAs"));
        } else if (cc.context == CompletionContext.CONTEXT_PARAMETER_VALUE) {
            String paramName = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            if (paramName.equals("dataset")) {
                URI uri= cc.resourceURI;
                List<String> dss= getDatasetsList( uri.toString() ); // bug 3055130 okay
                for ( String ds: dss ) {
                    if ( ds.startsWith(cc.completable) ) {
                        result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, ds ) );
                    }
                }
            }
        }

        return result;

    }

    synchronized List<String> getDatasetsList(String surl) {
        if (datasetsList == null) {
            datasetsList = new HashMap<String, List<String>>();
        }
        List<String> result = datasetsList.get(surl);
        
        if (result == null) {
            BufferedReader reader = null;
            try {
                URL url = new URL(surl + "?server=list");
                reader = new BufferedReader(new InputStreamReader(url.openStream()));
                String s = reader.readLine();
                ArrayList<String> list = new ArrayList<String>();
                while (s != null) {
                    list.add(s);
                    s = reader.readLine();
                }
                datasetsList.put( surl, list );
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
                throw new RuntimeException(ex);
            } finally {
                try {
                    reader.close();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
        }
        
        return datasetsList.get(surl);
        
    }

    /**
     * Indicate if a URI is acceptable.
     * vap+das2server:http://www-pw.physics.uiowa.edu/das/das2Server?galileo/pws/EDPosition.dsdf&timerange=2001-10-17
     * @param surl
     * @param problems
     * @param mon
     * @return true if it is not acceptable.
     */
    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        URISplit split= URISplit.parse( surl );
        Map<String,String> params= URISplit.parseParams(split.params);
        String ds= params.get("dataset");
        if ( params.get("arg_0")!=null ) {
            ds=  params.get("arg_0");
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
        } else {
            
        }
        return !( params.containsKey("start_time") && params.containsKey("end_time") && ds!=null && ds.length()>0 );
    }

    public <T> T getCapability(Class<T> clazz) {
        if ( clazz== TimeSeriesBrowse.class ) {
           return (T) new Das2ServerTimeSeriesBrowse();
        }
        return null;
    }
}
