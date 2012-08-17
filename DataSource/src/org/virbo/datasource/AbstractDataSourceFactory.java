/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource;

import java.util.Collections;
import java.util.List;
import org.das2.util.monitor.ProgressMonitor;

/**
 *
 * @author jbf
 */
public abstract class AbstractDataSourceFactory implements DataSourceFactory {

    public AbstractDataSourceFactory() {
    }
    
    public abstract DataSource getDataSource(java.net.URI uri) throws Exception;

    public List<CompletionContext> getCompletions(CompletionContext cc,org.das2.util.monitor.ProgressMonitor mon) throws Exception {
        return Collections.emptyList();
    }

    public <T> T getCapability(Class<T> clazz) {
        return null;
    }

    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        return false;
    }
    
}
