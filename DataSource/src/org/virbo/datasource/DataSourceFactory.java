/*
 * DataSourceFactory.java
 *
 * Created on May 4, 2007, 6:34 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.net.URL;
import java.util.List;

/**
 * 
 * @author jbf
 */
public interface DataSourceFactory {
    
    /**
     *return a dataSource for the url
     */
    DataSource getDataSource( URL url ) throws Exception;
        
    /**
     * return a list of context-sensitive completions
     */
    List<CompletionContext> getCompletions( CompletionContext cc ) throws Exception;
    
    /**
     * present the editor for customizing this URL.
     */
   // String editPanel( String surl ) throws Exception;
    
    /**
     * quick check to see that an url looks acceptable.  This is introduced to 
     * get Bob's desired behavior, that hitting return after a CDF filename should
     * bring up the completions list.
     */
    boolean reject( String surl ) ;
    
}
