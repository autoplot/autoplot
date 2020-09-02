
package org.autoplot.datasource;

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
        return Collections.emptyList();
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
