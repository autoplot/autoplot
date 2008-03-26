/*
 * AsciiTableDataSourceFactory.java
 *
 * Created on November 7, 2007, 11:41 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.ascii;

import org.das2.util.monitor.NullProgressMonitor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.MetadataModel;

/**
 *
 * @author jbf
 */
public class AsciiTableDataSourceFactory implements DataSourceFactory {
    
    /** Creates a new instance of AsciiTableDataSourceFactory */
    public AsciiTableDataSourceFactory() {
    }
    
    public DataSource getDataSource(URL url) throws FileNotFoundException, IOException {
        return new AsciiTableDataSource( url );
    }

    public String editPanel(String surl) throws Exception {
        return surl;
    }

    public MetadataModel getMetadataModel(URL url) {
        return MetadataModel.createNullModel();
    }

    public List<CompletionContext> getCompletions(CompletionContext cc) throws Exception {
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            List<CompletionContext> result= new ArrayList<CompletionContext>();
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "skip=" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "column=" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "fixedColumns=" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "rank2" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "time=") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "timeFormat=" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "depend0=") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "fill=" ) );
            return result;
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            String paramName= CompletionContext.get( CompletionContext.CONTEXT_PARAMETER_NAME, cc );
            if ( paramName.equals("skip") ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>" ) );
            } else if ( paramName.equals("rank2" ) ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>" ) );
            } else if ( paramName.equals("column") ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "field<int>" ) );
            } else if ( paramName.equals("fixedColumns") ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>" ) );
            } else if ( paramName.equals("time") ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<columnName>", this, null, "Identify column to be parsed as time, and is by default the absissa" ) );
            } else if ( paramName.equals("depend0") ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<columnName>" ) );
            } else if ( paramName.equals("timeFormat") ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "%Y %j %H" ) );
            } else if ( paramName.equals("fill") ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<double>" ) );
            } else {
                return Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }
    }
    
    public boolean reject( String surl ) {
        return false;
    }

    public String urlForServer(String surl) {
        return surl;
    }

}
