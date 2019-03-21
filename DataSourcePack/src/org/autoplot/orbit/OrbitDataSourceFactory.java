/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.orbit;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DefaultTimeSeriesBrowse;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.das2.datum.Orbits;

/**
 * Orbit data sources added for demonstration purposes, but it will also be useful for showing
 * reference orbits.  An example URI is "vap+orbit:rbspa-pp&timerange=2004"
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
            
            List<CompletionContext> ccresult= new ArrayList<>();
                        
            Map<String,String> names= Orbits.getSpacecraftIdExamples();
            for ( Entry<String,String> n : names.entrySet() ) {
                CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, 
                        n.getKey(), this, "arg_0", n.getValue(), null, true );
                ccresult.add(cc1);
            }
            CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "timerange=", "timerange to plot" );
            ccresult.add(cc1);
            return ccresult;
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            String paramName = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            if ( paramName.equals("timerange") ) {
                return Arrays.asList( new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<timerange>") );
            } else {
                return super.getCompletions(cc, mon);
            }
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
