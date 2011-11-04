/*
 * DodsDataSourceFactory.java
 *
 * Created on May 14, 2007, 10:04 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.dods;

import dods.dap.DDSException;
import dods.dap.parser.ParseException;
import dods.dap.parser.TokenMgrError;
import java.net.MalformedURLException;
import org.virbo.metatree.IstpMetadataModel;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.MetadataModel;
import org.virbo.datasource.URISplit;

/**
 *
 * @author jbf
 */
public class DodsDataSourceFactory implements DataSourceFactory {
    
    /** Creates a new instance of DodsDataSourceFactory */
    public DodsDataSourceFactory() {
    }
    
    public DataSource getDataSource(URI uri) throws IOException {
        try {
            return new DodsDataSource( uri );
        } catch ( NoSuchElementException ex ) {
            throw new RuntimeException( "Does not appear to be a DDS: "+uri, ex );
        }
    }
    
    public List<CompletionContext> getCompletions(CompletionContext cc,org.das2.util.monitor.ProgressMonitor mon) throws Exception {
        
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            String file= CompletionContext.get( CompletionContext.CONTEXT_FILE, cc );
            return getVars(file);
        } 
        
        return Collections.emptyList();
    }
    
    public MetadataModel getMetadataModel(URL url) {
        //if ( url.toString().contains(".cdf.") ) {
            return new IstpMetadataModel();
        //} else {
        //    return MetadataModel.createNullModel();
       // }
    }
    
    public boolean reject( String surl, ProgressMonitor mon) {
        if ( surl.contains("?") ) {
            return false;
        } else {
            try {
                URISplit split= URISplit.parse(surl);
                List<CompletionContext> cc = getVars(split.file);
                return cc.size() > 1;
            } catch ( Throwable ex ) {
                ex.printStackTrace();
                return true; // let someone else indicate the error.
            }
        }
        
    }

    private List<CompletionContext> getVars( String file ) throws DDSException, IOException, MalformedURLException, ParseException {
        List<CompletionContext> result= new ArrayList<CompletionContext>();
        
        int i = file.lastIndexOf('.');
        String sMyUrl = file.substring(0, i);
        URL url;

        url = new URL(sMyUrl + ".dds");

        MyDDSParser parser = new MyDDSParser();
        try {
            parser.parse(url.openStream());
        } catch ( TokenMgrError ex  ) {
            throw new ParseException("Does not appear to be a DDS: "+url);
        } catch (RuntimeException ex ) {
            throw new ParseException("Does not appear to be a DDS: "+url);
        }

        String[] vars = parser.getVariableNames();
        for (int j = 0; j < vars.length; j++) {
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, vars[j], this, "arg_0", null, null, true));
        }
        return result;
    }

    public <T> T getCapability(Class<T> clazz) {
        return null;
    }
}
