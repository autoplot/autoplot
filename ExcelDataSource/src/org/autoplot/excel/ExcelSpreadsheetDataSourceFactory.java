
package org.autoplot.excel;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;
import org.autoplot.datasource.MetadataModel;
import org.autoplot.datasource.URISplit;

/**
 *
 * @author jbf
 */
public class ExcelSpreadsheetDataSourceFactory extends AbstractDataSourceFactory implements DataSourceFactory {

    private static final String FIRST_ROW_DOC = "the row that contains the either the first record of data, or data column headings.  1 is the first row.";

    @Override
    public DataSource getDataSource(URI uri) throws IOException {
        return new ExcelSpreadsheetDataSource(uri);
    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, org.das2.util.monitor.ProgressMonitor mon) throws IOException {
        List<CompletionContext> result = new ArrayList<>();
        if (cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "column="));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "depend0="));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "plane0="));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "sheet="));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "firstRow=", FIRST_ROW_DOC));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "recCount=", "limit number of records to read"));
        } else if (cc.context == CompletionContext.CONTEXT_PARAMETER_VALUE) {
            String param = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            switch (param) {
                case "column":
                    result.addAll(toCC(CompletionContext.CONTEXT_PARAMETER_VALUE, getColumns(cc, mon), null));
                    break;
                case "depend0":
                    result.addAll(toCC(CompletionContext.CONTEXT_PARAMETER_VALUE, getColumns(cc, mon), null));
                    break;
                case "plane0":
                    result.addAll(toCC(CompletionContext.CONTEXT_PARAMETER_VALUE, getColumns(cc, mon), null));
                    break;
                case "sheet":
                    result.addAll(toCC(CompletionContext.CONTEXT_PARAMETER_VALUE, getSheets(cc, mon), "worksheet source"));
                    break;
                case "firstRow":
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", FIRST_ROW_DOC));
                    break;
                case "recCount":
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", FIRST_ROW_DOC));
                    break;
                default:
                    break;
            }
        }
        return result;
    }

    List<CompletionContext> toCC(Object context, List<String> results, String doc) {
        List<CompletionContext> result = new ArrayList<>();
        for (String s : results) {
            result.add(new CompletionContext(context, URISplit.uriEncode(s), s, doc));
        }
        return result;
    }

    private HSSFWorkbook getWorkbook(URI uri, ProgressMonitor mon) throws IOException {
        File file = DataSetURI.getFile(uri, mon);
        InputStream in = new FileInputStream(file);
        HSSFWorkbook wb=null;
        try {
            POIFSFileSystem fs = new POIFSFileSystem(in);
            wb = new HSSFWorkbook(fs);
        } finally {
            in.close();
        }
        return wb;
    }

    
    private List<String> getSheets(CompletionContext cc, ProgressMonitor mon) throws IOException {
        HSSFWorkbook wb = getWorkbook(cc.resourceURI, mon);
        return ExcelUtil.getSheets(wb,cc, mon);
    } 
    

    /**
     * inspect the first row for columns.  Strings may be picked up as labels if the
     * next row contains values.
     * @param cc
     * @param mon
     * @return
     * @throws java.io.IOException
     */
    private List<String> getColumns( CompletionContext cc, ProgressMonitor mon) throws IOException {
        HSSFWorkbook wb = getWorkbook(cc.resourceURI, mon);
        Map params = URISplit.parseParams(cc.params);
        return new LinkedList<>( ExcelUtil.getColumns(wb,  (String) params.get("sheet"),  (String) params.get("firstRow"), mon).values() );
    }

    public MetadataModel getMetadataModel(URL url) {
        return MetadataModel.createNullModel();
    }

    @Override
    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        return !surl.contains("column=");
    }

    public String urlForServer(String surl) {
        return surl; // TODO
    }

    @Override
    public String getDescription() {
        return "Excel Spreadsheets (not .xlsx)";
    }

    
}
