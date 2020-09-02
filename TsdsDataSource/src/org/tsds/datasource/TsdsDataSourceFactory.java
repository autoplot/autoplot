
package org.tsds.datasource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.capability.TimeSeriesBrowse;

/**
 *
 * @author jbf
 */
public class TsdsDataSourceFactory extends AbstractDataSourceFactory implements DataSourceFactory {

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new TsdsDataSource(uri);
    }
    
    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {

        List<CompletionContext> result = new ArrayList<>();

        if (cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "param1=", "dataset identifier"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "StartDate=", "YYYYMMDD start time"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "EndTime=", "YYYYMMDD end time"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "ppd=", "number of points per day"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "filter=", "data reduction filter"));
        } else if (cc.context == CompletionContext.CONTEXT_PARAMETER_VALUE) {
            String paramName = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            if ( paramName.equals("filter") ) {
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "numbervalid", "number of points in each bin" ) );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "max", "maximum value in bin" ) );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "min", "minimum value in bin" ) );
            }
        }

        return result;

    }


    @Override
    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        URISplit split= URISplit.parse( surl );
        Map params= URISplit.parseParams(split.params);
        if ( params.isEmpty() ) {
            return true;
            //return !( surl.contains("tf_") && surl.contains("to_") ); // looks like a redirect url.
        } else {
            return !( params.containsKey("StartDate") && params.containsKey("param1") );
        }
    }

    @Override
    public <T> T getCapability(Class<T> clazz) {
        if ( clazz==TimeSeriesBrowse.class ) {
           return (T) new TsdsTimeSeriesBrowse();
        } else {
            return null;
        }
    }

    @Override
    public boolean isFileResource() {
        return false;
    }
        
}
