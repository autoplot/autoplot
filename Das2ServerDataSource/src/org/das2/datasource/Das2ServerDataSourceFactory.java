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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
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
                Logger.getLogger(Das2ServerDataSourceFactory.class.getName()).log(Level.SEVERE, null, ex);
                throw new RuntimeException(ex);
            } finally {
                try {
                    reader.close();
                } catch (IOException ex) {
                    Logger.getLogger(Das2ServerDataSourceFactory.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        
        return datasetsList.get(surl);
        
    }

    public boolean reject(String surl, ProgressMonitor mon) {
        URISplit split= URISplit.parse( surl );
        Map params= URISplit.parseParams(split.params);
        return !( params.containsKey("start_time") && params.containsKey("end_time") && params.containsKey("dataset") );
    }

    public <T> T getCapability(Class<T> clazz) {
        if ( clazz== TimeSeriesBrowse.class ) {
           return (T) new Das2ServerTimeSeriesBrowse();
        }
        return null;
    }
}
