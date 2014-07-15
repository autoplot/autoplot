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
import dods.dap.NoSuchVariableException;
import dods.dap.Server.InvalidParameterException;
import dods.dap.parser.ParseException;
import dods.dap.parser.TokenMgrError;
import java.net.MalformedURLException;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.AbstractDataSourceFactory;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.URISplit;

/**
 * Reads OpenDAP streams.
 * @author jbf
 */
public class DodsDataSourceFactory extends AbstractDataSourceFactory implements DataSourceFactory {
    
    private static final Logger logger= LoggerManager.getLogger("apdss.opendap");
    
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
    
    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc,org.das2.util.monitor.ProgressMonitor mon) throws Exception {
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            String file= CompletionContext.get( CompletionContext.CONTEXT_FILE, cc );
            return getVars(file);
        } 
        
        return Collections.emptyList();
    }

    @Override
    public boolean reject( String surl, List<String> problems, ProgressMonitor mon) {
        if ( surl.contains("?") ) {
            return false;
        } else {
            try {
                URISplit split= URISplit.parse(surl);
                List<CompletionContext> cc = getVars(split.file);
                return cc.size() > 1;
            } catch ( DDSException ex ) {
                logger.log(Level.WARNING,null,ex);
                return true; // let someone else indicate the error.
            } catch (IOException ex) {
                logger.log(Level.WARNING,null,ex);
                return true; // let someone else indicate the error.
            } catch (ParseException ex) {
                logger.log(Level.WARNING,null,ex);
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
        
        for ( String var : vars ) {
            StringBuilder label = new StringBuilder(var);
            try {
                String[] deps = parser.getDepends(var);
                if ( deps!=null ) {
                    label.append("[").append(deps[0]);
                    for ( int k=1; k<deps.length; k++ ) {
                        label.append(",").append(deps[k]);
                    }
                    label.append("]");
                }
            } catch (NoSuchVariableException ex) {
                Logger.getLogger(DodsDataSourceFactory.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InvalidParameterException ex) {
                Logger.getLogger(DodsDataSourceFactory.class.getName()).log(Level.SEVERE, null, ex);
            }
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, var, this, "arg_0", null, label.toString(), true));
        }
        return result;
    }

}
