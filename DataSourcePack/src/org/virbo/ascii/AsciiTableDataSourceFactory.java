/*
 * AsciiTableDataSourceFactory.java
 *
 * Created on November 7, 2007, 11:41 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.ascii;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.MetadataModel;
import org.virbo.datasource.URLSplit;
import org.virbo.dsutil.AsciiParser;
import org.virbo.dsutil.AsciiParser.DelimParser;

/**
 *
 * @author jbf
 */
public class AsciiTableDataSourceFactory implements DataSourceFactory {

    /** Creates a new instance of AsciiTableDataSourceFactory */
    public AsciiTableDataSourceFactory() {
    }

    public DataSource getDataSource(URL url) throws FileNotFoundException, IOException {
        return new AsciiTableDataSource(url);
    }

    public String editPanel(String surl) throws Exception {
        return surl;
    }

    public MetadataModel getMetadataModel(URL url) {
        return MetadataModel.createNullModel();
    }

    public List<CompletionContext> getCompletions(CompletionContext cc, org.das2.util.monitor.ProgressMonitor mon) throws Exception {
        if (cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {
            List<CompletionContext> result = new ArrayList<CompletionContext>();
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "skip="));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "recCount="));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "column="));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "fixedColumns="));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "rank2="));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "time="));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "timeFormat=",
                    "template for parsing time digits, default is ISO8601."));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "depend0="));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "fill="));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "comment=",
                    "comment line prefix, default is hash (#)"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "validMin=",
                    "values less than this value are treated as fill."));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "validMax=",
                    "values greater than this value are treated as fill."));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "delim=",
                    "parse records by splitting on delimiter."));

            return result;
        } else if (cc.context == CompletionContext.CONTEXT_PARAMETER_VALUE) {
            String paramName = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            if (paramName.equals("skip")) {
                return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", "the number of lines to skip before attempting to parse."));
            } else if (paramName.equals("recCount")) {
                return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", "limit number of records to parse."));
            } else if (paramName.equals("rank2")) {
                List<CompletionContext> result = new ArrayList<CompletionContext>();
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", "number of columns to expect"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "1:", "all but first column"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "1:5", "second through 5th columns"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "-5:", "last five columns"));
                return result;
            } else if (paramName.equals("column")) {
                List<CompletionContext> result = getFieldNames(cc, mon);
                return result;
            } else if (paramName.equals("fixedColumns")) {
                return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", "Hint at the number of columns to expect, then use fast parser that assumes fixed columns."));
            } else if (paramName.equals("time")) {
                List<CompletionContext> result = getFieldNames(cc, mon);
                return result;
            } else if (paramName.equals("depend0")) {
                List<CompletionContext> result = getFieldNames(cc, mon);
                return result;
            } else if (paramName.equals("timeFormat")) {
                List<CompletionContext> result = new ArrayList<CompletionContext>();
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "%Y %j %H"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "ISO8601"));
                return result;
            } else if (paramName.equals("fill")) {
                return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<double>"));
            } else if (paramName.equals("validMin")) {
                return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<double>"));
            } else if (paramName.equals("validMax")) {
                return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<double>"));
            } else {
                return Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }
    }

    public boolean reject(String surl, ProgressMonitor mon) {
        return false;
    }

    public String urlForServer(String surl) {
        return surl;
    }

    private List<CompletionContext> getFieldNames(CompletionContext cc, ProgressMonitor mon) throws IOException {

        Map params = URLSplit.parseParams(cc.params);
        Object o;
        File file = DataSetURL.getFile(cc.resource, mon);

        AsciiParser parser = AsciiParser.newParser(5);
        if (params.containsKey("skip")) {
            parser.setSkipLines(Integer.parseInt((String) params.get("skip")));
        }
        
        String line= parser.readFirstRecord(file.toString());
        DelimParser dp= parser.guessDelimParser(line);
        String[] fields= dp.fields(line);

        String[] columns = parser.getFieldNames();
        List<CompletionContext> result = new ArrayList<CompletionContext>();
        
        for ( int i=0; i<columns.length; i++ ) {
            String s= columns[i];
            String label= s;
            if ( ! label.equals(fields[i]) && label.startsWith("field") ) label += " ("+fields[i]+")";
            
            result.add(new CompletionContext(
                    CompletionContext.CONTEXT_PARAMETER_VALUE, 
                    s,
                    label, null ) ) ;
        }
        return result;
    }
}
