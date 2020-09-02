
package org.autoplot.hapi;

import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.das2.datum.DatumRangeUtil;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DefaultTimeSeriesBrowse;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.capability.TimeSeriesBrowse;

/**
 * Constructor for HAPI data sources.
 * @author jbf
 */
public class HapiDataSourceFactory extends AbstractDataSourceFactory {

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new HapiDataSource(uri);
    }

    @Override
    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        URISplit split= URISplit.parse(surl);
        String server= split.file;
        if ( server==null ) {
            problems.add("server is not identified");
        } else {
            if ( !server.endsWith("hapi") ) problems.add("server name must end in /hapi");
        }
        LinkedHashMap<String,String> params= URISplit.parseParams(split.params);
        String id= params.get("id");
        String timerange= params.get( URISplit.PARAM_TIME_RANGE );
        if ( id==null ) problems.add("the parameter id is needed");
        if ( timerange==null ) {
            problems.add("the timerange is needed");
        } else {
            try {
                DatumRangeUtil.parseTimeRange(timerange);
            } catch ( ParseException ex ) {
                problems.add("timerange cannot be parsed");
            }
        }
        return problems.size()>0;
    }

    
    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        List<CompletionContext> result = new ArrayList<>();
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "id=", "dataset identifier"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "timerange=", "time range"));
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            String paramName = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            if (paramName.equals("id")) {
                URI uri= cc.resourceURI;
                if ( uri==null ) throw new IllegalArgumentException("expected das2server location");
                List<String> dss= HapiServer.getCatalogIds(uri.toURL()); 
                for ( String ds: dss ) {
                    if ( ds.startsWith(cc.completable) ) {
                        result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, ds ) );
                    }
                }
            } 
        }
        return result;
    }

    @Override
    public <T> T getCapability(Class<T> clazz) {
        if ( clazz==TimeSeriesBrowse.class ) {
            return (T) new DefaultTimeSeriesBrowse();
        } else {
            return super.getCapability(clazz); //To change body of generated methods, choose Tools | Templates.
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
    
}
