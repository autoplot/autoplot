/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.csv;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;

/**
 *
 * @author jbf
 */
public class CsvDataSourceFactory implements DataSourceFactory {

    public DataSource getDataSource(URI uri) throws Exception {
        return new CsvDataSource(uri);
    }

    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        return new ArrayList<CompletionContext>();
    }

    public boolean reject(String surl, ProgressMonitor mon) {
        return false;
    }

}
