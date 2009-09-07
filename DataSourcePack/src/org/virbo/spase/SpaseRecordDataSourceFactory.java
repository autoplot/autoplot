/*
 * SpaseRecordDataSourceFactory.java
 *
 * Created on October 8, 2007, 6:57 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.spase;

import java.net.URI;
import java.util.List;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;

/**
 *
 * @author jbf
 */
public class SpaseRecordDataSourceFactory implements DataSourceFactory {
    
    /** Creates a new instance of SpaseRecordDataSourceFactory */
    public SpaseRecordDataSourceFactory() {
    }
    
    public DataSource getDataSource(URI uri) throws Exception {
        return new SpaseRecordDataSource(uri);
    }
    
    public List<CompletionContext> getCompletions(CompletionContext cc,org.das2.util.monitor.ProgressMonitor mon) throws Exception {
        return java.util.Collections.emptyList();
    }
    
    public String editPanel(String surl) throws Exception {
        return surl;
    }
    
    
    public boolean reject( String surl, ProgressMonitor mon ) {
        return false;
    }

    public String urlForServer(String surl) {
        return surl; //TODO
    }
    
}
