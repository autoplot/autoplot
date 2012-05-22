/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.asdatasource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.AbstractDataSourceFactory;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSource;

/**
 * Read data directly from the Audiosystem.  This is for demonstration purposes.
 * @author jbf
 */
public class AudioSystemDataSourceFactory extends AbstractDataSourceFactory {

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new AudioSystemDataSource(uri);
    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            List<CompletionContext> result= new ArrayList<CompletionContext>();
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "spec=", "make spectrogram using FFT" ) );
            return result;
        } else if(cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {
            List<CompletionContext> result= new ArrayList<CompletionContext>();
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "len=", "sample length in seconds" ) );
            return result;
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            String paramName= CompletionContext.get( CompletionContext.CONTEXT_PARAMETER_NAME, cc );
            if ( paramName.equals("spec") ) {
                List<CompletionContext> result= new ArrayList<CompletionContext>();
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "128") );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "256") );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "512") );
                return result;
            } else if (paramName.equals("len")) {
                List<CompletionContext> result= new ArrayList<CompletionContext>();
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "1.0") );
                return result;
            } else {
                return Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }

    }


}
