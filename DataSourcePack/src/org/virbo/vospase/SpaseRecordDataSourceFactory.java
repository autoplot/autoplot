/*
 * SpaseRecordDataSourceFactory.java
 *
 * Created on October 8, 2007, 6:57 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.vospase;

import java.net.URL;
import java.util.List;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.MetadataModel;

/**
 *
 * @author jbf
 */
public class SpaseRecordDataSourceFactory implements DataSourceFactory {
    
    /** Creates a new instance of SpaseRecordDataSourceFactory */
    public SpaseRecordDataSourceFactory() {
    }
    
    public DataSource getDataSource(URL url) throws Exception {
        
        return new VoSpaseRecordDataSource(url);
    }
    
    public List<CompletionContext> getCompletions(CompletionContext cc,org.das2.util.monitor.ProgressMonitor mon) throws Exception {
        return java.util.Collections.emptyList();
    }
    
    public String editPanel(String surl) throws Exception {
        return surl;
    }
    
    public MetadataModel getMetadataModel(URL url) {
        return new SpaseMetadataModel();
    }
    
    public boolean reject( String surl ,ProgressMonitor mon ) {
        return false;
    }

    public String urlForServer(String surl) {
        return surl; // TODO
    }
    
}
