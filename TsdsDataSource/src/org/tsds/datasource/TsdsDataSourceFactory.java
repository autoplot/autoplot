/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.tsds.datasource;

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
import org.virbo.datasource.URLSplit;

/**
 *
 * @author jbf
 */
public class TsdsDataSourceFactory implements DataSourceFactory {

    public DataSource getDataSource(URI uri) throws Exception {
        return new TsdsDataSource(uri);
    }
    
    Map<String, List<String>> datasetsList = null;

    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        //http://www-pw.physics.uiowa.edu/das/das2Server
        //?dataset=das2_1/voyager1/pws/sa-4s-pf.new
        //&start_time=2004-01-01&end_time=2004-01-06&server=dataset&ascii=1

        List<CompletionContext> result = new ArrayList<CompletionContext>();

        if (cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "param1=", "dataset identifier"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "StartDate=", "YYYYMMDD start time"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "EndTime=", "YYYYMMDD end time"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "ppd=", "number of points per day"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "filter=", "data reduction filter"));
        } else if (cc.context == CompletionContext.CONTEXT_PARAMETER_VALUE) {
            String paramName = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            if (paramName.equals("dataset")) {
                //TODO: this is leftover,dead code from Das2ServerDataSourceFactory.
                URL url= cc.resource;
                List<String> dss= getDatasetsList( url.toString() );
                for ( String ds: dss ) {
                    if ( ds.startsWith(cc.completable) ) {
                        result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, ds ) );
                    }
                }
            } else if ( paramName.equals("filter") ) {
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "numbervalid", "number of points in each bin" ) );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "max", "maximum value in bin" ) );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "min", "minimum value in bin" ) );
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
                Logger.getLogger(TsdsDataSourceFactory.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            } finally {
                try {
                    reader.close();
                } catch (IOException ex) {
                    Logger.getLogger(TsdsDataSourceFactory.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        
        return datasetsList.get(surl);
        
    }

    public boolean reject(String surl, ProgressMonitor mon) {
        URLSplit split= URLSplit.parse( surl );
        Map params= URLSplit.parseParams(split.params);
        if ( params.equals("") ) {
            return !( surl.contains("tf_") && surl.contains("to_") ); // looks like a redirect url.
        } else {
            return !( params.containsKey("StartDate") && params.containsKey("EndDate") && params.containsKey("param1") );
        }
    }
}
