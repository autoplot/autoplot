/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource.wav;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.virbo.datasource.AbstractDataSourceFactory;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSource;

/**
 *
 * @author jbf
 */
public class WavDataSourceFactory extends AbstractDataSourceFactory {

    @Override
    public DataSource getDataSource(URL url) throws Exception {
        return new WavDataSource(url);
    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc) {
        List<CompletionContext> result= new ArrayList<CompletionContext>();
        if ( cc.context.equals(CompletionContext.CONTEXT_PARAMETER_NAME ) ) {
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "offset" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "length" ) );
        } else if ( cc.context.equals(CompletionContext.CONTEXT_PARAMETER_VALUE ) ) {
            String paramName= CompletionContext.get( CompletionContext.CONTEXT_PARAMETER_NAME, cc );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<double>" ) );
        }
        return result;
    }

    public List<String> extensions() {
        return Collections.singletonList("wav");
    }

    public List<String> mimeTypes() {
        return Collections.emptyList();
    }
   
}
