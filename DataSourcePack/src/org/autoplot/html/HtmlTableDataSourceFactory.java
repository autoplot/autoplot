/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.html;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.URISplit;

/**
 *
 * @author jbf
 */
public class HtmlTableDataSourceFactory extends AbstractDataSourceFactory {

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new HtmlTableDataSource( uri );
    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            List<CompletionContext> result = new ArrayList<CompletionContext>();
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "column=", "the name (or number) of the column to plot") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "table=", "the table name (or number) of the table" ) );
            return result;
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        URISplit split = URISplit.parse(surl);

        Map<String, String> params = URISplit.parseParams(split.params);

        if ( params.get("column")==null ) return true;
        if ( params.get("table")==null ) return true;

        return false;

    }


}
