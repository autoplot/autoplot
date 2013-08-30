/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.orbit;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.AbstractDataSourceFactory;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DefaultTimeSeriesBrowse;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.capability.TimeSeriesBrowse;

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
            
            List<CompletionContext> ccresult= new ArrayList<CompletionContext>();
            
            Map<String,String> names= new LinkedHashMap();
            names.put( "rbspa-pp",  "RBSP-A (Van Allen Probe A)" );
            names.put( "rbspb-pp",  "RBSP-B (Van Allen Probe B)" );
            names.put( "crres",     "CRRES" );
            names.put( "cassini",   "Cassini Spacecraft" );
            for ( Entry<String,String> n : names.entrySet() ) {
                CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, 
                        n.getKey(), this, "arg_0", n.getValue(), null, true );
                ccresult.add(cc1);
            }
            CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "timerange=", "timerange to plot" );
            ccresult.add(cc1);
            return ccresult;
        } else {
            return super.getCompletions(cc, mon); 
        }
        
    }

    @Override
    public <T> T getCapability(Class<T> clazz) {
        if ( clazz==TimeSeriesBrowse.class ) {
            return (T) new DefaultTimeSeriesBrowse();
        } else {
            return null;
        }
        
    }

    @Override
    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        URISplit split = URISplit.parse(surl);
        Map map = URISplit.parseParams(split.params);
        if ( DefaultTimeSeriesBrowse.reject( map, problems) ) {
            return true;
        } else {
            if ( !map.containsKey( URISplit.PARAM_ARG_0 ) ) {
                problems.add( "no spacecraft" );
                return true;
            }
        }
        return super.reject(surl, problems, mon); //To change body of generated methods, choose Tools | Templates.
    }
    
    
    
}
