/*
 * DataSourceFactory.java
 *
 * Created on May 4, 2007, 6:34 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.net.URI;
import java.util.List;
import org.das2.util.monitor.ProgressMonitor;

/**
 * 
 * @author jbf
 */
public interface DataSourceFactory {
    
    /**
     *return a dataSource for the url
     */
    DataSource getDataSource( URI uri ) throws Exception;
        
    /**
     * return a list of context-sensitive completions.  If an exception is thrown, then an
     * a error dialog is displayed for RuntimeExceptions.  Compile-time exceptions may be
     * displayed more gently, relying on getMessage to aid the human operator.
     */
    public List<CompletionContext> getCompletions( CompletionContext cc, ProgressMonitor mon ) throws Exception;

    /**
     * return additional tools for creating valid URIs, such as TimeSeriesBrowseEditor.  This may soon include
     * a file selector, and an automatic GUI created from the completions model.
     */
    public <T> T getCapability( Class<T> clazz );

    /**
     * quick check to see that an uri looks acceptable.
     *
     * since 2012b, this should provide a list of objects marking reasons for rejecting.  Though each object
     * is simply a marker, toString method of each should provide some meaningful information to developers.
     * This list will be passed into the editor if available.
     */
    public boolean reject( String surl, List<String> problems, ProgressMonitor mon ) ;
    
}
