/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource;

import java.net.URL;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author jbf
 */
public abstract class AbstractDataSourceFactory implements DataSourceFactory {

    public abstract DataSource getDataSource(URL url) throws Exception;

    public List<CompletionContext> getCompletions(CompletionContext cc) {
        return Collections.emptyList();
    }

    public MetadataModel getMetadataModel(URL url) {
        return MetadataModel.createNullModel();
    }

    public boolean reject(String surl) {
        return false;
    }

    public String urlForServer(String surl) {
        return surl; // TODO
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
