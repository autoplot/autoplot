/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.das2Stream;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.MetadataModel;

/**
 *
 * @author jbf
 */
public class Das2StreamDataSourceFactory implements DataSourceFactory {

    public DataSource getDataSource(URL url) throws IOException {
        return new Das2StreamDataSource(url);
    }

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
}
