/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.servletutil;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.autoplot.dom.Application;
import org.autoplot.dom.DataSourceFilter;
import org.autoplot.state.StatePersistence;
import org.autoplot.datasource.URISplit;

/**
 * At the RPW Group, we need to rewrite the vap so that the username is known to the Das2ServerDataSource.
 * @author jbf
 */
public class InsertDas2UserIntoVap {
    

    /**
     * insert extra parameters into the URIs, where the vapScheme is found.
     * @param vap the original vap
     * @param o the new vap, which will be written
     * @param vapScheme the URI type to match.  E.g. "vap+das2server"
     * @param extraParams the parameters to insert into the matching URIs.
     * @throws IOException 
     */
    public static void insertExtraParams( File vap, File o, String vapScheme, Map<String,String> extraParams ) throws IOException {
        Application app= (Application) StatePersistence.restoreState(vap);
        DataSourceFilter[] dsfs= app.getDataSourceFilters();
        for ( DataSourceFilter dsf: dsfs ) {
            String uri= dsf.getUri();
            if ( uri!=null ) {
                URISplit split= URISplit.parse(uri);
                Map<String,String> parms= URISplit.parseParams(split.params);
                parms.putAll( extraParams );
                split.params= URISplit.formatParams(parms);
                String newUri= URISplit.format(split);
                dsf.setUri(newUri);
            }
        }
        app.setDataSourceFilters(dsfs);
        StatePersistence.saveState( o, app );
    }
}
