/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.orbit;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.AbstractDataSourceFactory;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSource;

/**
 * Orbit data sources added for demonstration purposes, but it will also be useful.
 * @author jbf
 */
public class OrbitDataSourceFactory extends AbstractDataSourceFactory {

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new OrbitDataSource(uri);
    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            List<CompletionContext> result= new ArrayList<CompletionContext>();
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "rbspa-pp", "RBSP-A (Van Allen Probe A)") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "rbspb-pp", "RBSP-B (Van Allen Probe B)") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "crres", "CRRES" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "cassini", "Cassini Spacecraft" ) );
            return result;
        } else {
            return super.getCompletions(cc, mon); //To change body of generated methods, choose Tools | Templates.
        }
        
    }
    
    
}
