/*
 * SpaseRecordDataSourceFactory.java
 *
 * Created on October 8, 2007, 6:57 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.spase;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;

/**
 *
 * @author jbf
 */
public class SpaseRecordDataSourceFactory implements DataSourceFactory {
    
    private static final Logger logger= LoggerManager.getLogger("apdss");
    
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
    
    
    public boolean reject( String surl, List<String> problems, ProgressMonitor mon ) throws IllegalArgumentException {
        
        try {
            File f= DataSetURI.getFile( surl, mon);
            
            Object type= new XMLTypeCheck().calculateType(f);

            if ( type==null ) {
                return true;
            } else {
                return false;
            }
            
        } catch ( Exception ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            return true;
        }
    }

    public String urlForServer(String surl) {
        return surl; //TODO
    }

    public <T> T getCapability(Class<T> clazz) {
        return null;
    }
    
}
