/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.excel;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.MetadataModel;

/**
 *
 * @author jbf
 */
public class ExcelSpreadsheetDataSourceFactory implements DataSourceFactory {

    public DataSource getDataSource(URL url) throws IOException {
        return new ExcelSpreadsheetDataSource(url);
    }

    public List<CompletionContext> getCompletions(CompletionContext cc) {
        List<CompletionContext> result = new ArrayList<CompletionContext>();
        if (cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "column"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "depend0"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "plane0"));
        } else if (cc.context == CompletionContext.CONTEXT_PARAMETER_VALUE) {
            String param = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            if (param.equals("column")) {
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "A[start:end]"));
            } else if (param.equals("depend0")) {
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "B[start:end]"));
            } else if (param.equals("plane0")) {
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "C[start:end]"));
            }
        }
        return result;
    }

    public MetadataModel getMetadataModel(URL url) {
        return MetadataModel.createNullModel();
    }

    public boolean reject(String surl) {
        return !surl.contains("column=");
    }

    public String urlForServer(String surl) {
        return surl; // TODO
    }
}
