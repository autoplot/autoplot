
package org.autoplot.datasource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.das2.util.monitor.ProgressMonitor;

/**
 * Default implementations for many types of DataSourceFactory
 * @author jbf
 */
public abstract class AbstractDataSourceFactory implements DataSourceFactory {

    public AbstractDataSourceFactory() {
    }
    
    @Override
    public abstract DataSource getDataSource(java.net.URI uri) throws Exception;

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc,org.das2.util.monitor.ProgressMonitor mon) throws Exception {
        if ( cc.context.equals(CompletionContext.CONTEXT_PARAMETER_NAME ) )  {
            return Collections.singletonList( 
                new CompletionContext( 
                    CompletionContext.CONTEXT_PARAMETER_NAME, 
                    "filePollUpdates=", 
                    "every so many seconds check for file update and reload when file if updated.") );
        } else if ( cc.context.equals(CompletionContext.CONTEXT_PARAMETER_VALUE ) ) {
            String parmname= CompletionContext.get( CompletionContext.CONTEXT_PARAMETER_NAME, cc );
            if ( parmname.equals("filePollUpdates") ) {
                return Arrays.asList(
                        new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "1", "check for updates every second" ),
                        new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "10", "check for updates every ten seconds (for https)" ) );
            } else {
                return Collections.emptyList();
            }

        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public <T> T getCapability(Class<T> clazz) {
        return null;
    }

    @Override
    public boolean reject(String suri, List<String> problems, ProgressMonitor mon) {
        return false;
    }
    
    @Override
    public boolean supportsDiscovery() {
        return false;
    }
    
    @Override
    public boolean isFileResource() {
        return true;
    }
    
    @Override
    public String getDescription() {
        return "";
    }
}
