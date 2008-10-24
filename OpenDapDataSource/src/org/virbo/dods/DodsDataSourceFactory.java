/*
 * DodsDataSourceFactory.java
 *
 * Created on May 14, 2007, 10:04 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.dods;

import org.virbo.metatree.IstpMetadataModel;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
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
public class DodsDataSourceFactory implements DataSourceFactory {
    
    /** Creates a new instance of DodsDataSourceFactory */
    public DodsDataSourceFactory() {
    }
    
    public DataSource getDataSource(URL url) throws IOException {
        return new DodsDataSource( url );
    }
    
    public List<CompletionContext> getCompletions(CompletionContext cc,org.das2.util.monitor.ProgressMonitor mon) throws Exception {
        List<CompletionContext> result= new ArrayList<CompletionContext>();
        
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            String file= CompletionContext.get( CompletionContext.CONTEXT_FILE, cc );
            int i= file.lastIndexOf('.');
            String sMyUrl= file.substring(0,i);
            URL url;
            
            url = new URL(sMyUrl + ".dds");
            
            MyDDSParser parser= new MyDDSParser();
            parser.parse( url.openStream() );
            
            String[] vars= parser.getVariableNames();
            for ( int j=0; j<vars.length;j++ ) {
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, vars[j], this, "arg_0" ,null ,null, true )    );
            }
            
        }
        
        return result;
    }
    
    public MetadataModel getMetadataModel(URL url) {
        //if ( url.toString().contains(".cdf.") ) {
            return new IstpMetadataModel();
        //} else {
        //    return MetadataModel.createNullModel();
       // }
    }
    
    public boolean reject( String surl, ProgressMonitor mon) {
        return ! surl.contains("?");
    }

    public String urlForServer(String surl) {
        return surl; // TODO
    }
}
