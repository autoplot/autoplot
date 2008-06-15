/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource;

import java.net.URL;
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
    
    public abstract DataSource getDataSource(URL url) throws Exception;

    public List<CompletionContext> getCompletions(CompletionContext cc,org.das2.util.monitor.ProgressMonitor mon) throws Exception {
        return Collections.emptyList();
    }

    public boolean reject(String surl, ProgressMonitor mon) {
        return false;
    }
    
    /**
     * return a list of file extensions handled by the factory, such as "wav"
     * @return
     */
    public List<String> extensions() {
        throw new IllegalArgumentException("Implement AbstractDataSourceFactory.extensions()");
    }
    
    /**
     * 
     * @return
     */
    public List<String> mimeTypes() {
        throw new IllegalArgumentException("Implement AbstractDataSourceFactory.extensions()");
    }
}
