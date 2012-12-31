/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.csv;

import com.csvreader.CsvReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.URISplit;

/**
 *
 * @author jbf
 */
public class CsvDataSourceFactory implements DataSourceFactory {

    public DataSource getDataSource(URI uri) throws Exception {
        return new CsvDataSource(uri);
    }

    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        if (cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {
            List<CompletionContext> result = new ArrayList<CompletionContext>();
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "column="));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "bundle=", "read in more than one column to create a rank 2 bundle dataset."));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "depend0="));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "validMin=",
                    "values less than this value are treated as fill."));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "validMax=",
                    "values greater than this value are treated as fill."));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "skip=", "skip this many lines before parsing"));
            return result;
        } else if (cc.context == CompletionContext.CONTEXT_PARAMETER_VALUE) {
            String paramName = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            if (paramName.equals("bundle")) {
                List<CompletionContext> result = new ArrayList<CompletionContext>();
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", "number of columns to expect"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "Bx-Bz", "three named columns"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "1:", "all but first column"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "1:5", "second through 5th columns"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "-5:", "last five columns"));
                return result;
            } else if (paramName.equals("column")) {
                List<CompletionContext> result = getFieldNames(cc, mon);
                return result;
            } else if (paramName.equals("depend0")) {
                List<CompletionContext> result = getFieldNames(cc, mon);
                return result;
            } else if (paramName.equals("skip")) {
                List<CompletionContext> result = new ArrayList<CompletionContext>();
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", "number of lines to skip"));
                return result;
            } else {
                return Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }

    }


    private List<CompletionContext> getFieldNames(CompletionContext cc, ProgressMonitor mon) throws IOException {

        //Map<String,String> params = URISplit.parseParams(cc.params);
        File f = DataSetURI.getFile(cc.resourceURI, mon);

        FileReader fr= new FileReader(f);
        CsvReader reader= new CsvReader( fr );

        String[] columns;
        if ( reader.readHeaders() ) {
            //int ncol= reader.getHeaderCount();
            columns= reader.getHeaders();
        } else {
            columns= new String[reader.getColumnCount()];
            for ( int i=0; i<columns.length; i++ ) {
                columns[i]= "field"+i;
            }
        }

        List<CompletionContext> result = new ArrayList<CompletionContext>();
        for ( int i=0; i<columns.length; i++ ) {
            String s= columns[i];
            String label= s;
            //if ( ! label.equals(fields[i]) && label.startsWith("field") ) label += " ("+fields[i]+")";

            result.add(new CompletionContext(
                    CompletionContext.CONTEXT_PARAMETER_VALUE,
                    s,
                    label, null ) ) ;
        }

        reader.close();
        fr.close();
        
        return result;

    }

    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        return false;
    }

    public <T> T getCapability(Class<T> clazz) {
        return null;
    }

}
