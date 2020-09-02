
package org.autoplot.csv;

import com.csvreader.CsvReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;

/**
 * Factory for producing comma separated values file sources.
 * @author jbf
 */
public class CsvDataSourceFactory extends AbstractDataSourceFactory implements DataSourceFactory {

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new CsvDataSource(uri);
    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        if (cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {
            List<CompletionContext> result = new ArrayList<>();
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "column="));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "bundle=", "read in more than one column to create a rank 2 bundle dataset."));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "depend0="));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "validMin=",
                    "values less than this value are treated as fill."));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "validMax=",
                    "values greater than this value are treated as fill."));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "delim=",
                    "override the default delimiter (comma)."));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "skipLines=", "skip this many lines before parsing"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "recCount=", "the number of records to read in"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "recStart=", "skip this number of records"));
            return result;
        } else if (cc.context == CompletionContext.CONTEXT_PARAMETER_VALUE) {
            String paramName = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            if (paramName.equals("bundle")) {
                List<CompletionContext> result = new ArrayList<>();
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", "number of columns to expect"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "Bx-Bz", "three named columns"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "1:", "all but first column"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "1:5", "second through 5th columns"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "-5:", "last five columns"));
                return result;
            } else if ( paramName.equals("delim") ) {
                List<CompletionContext> result = new ArrayList<>();
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, ",", "force comma delimiter"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, ";", "force semicolon delimiter"));
                return result;
            } else if (paramName.equals("column")) {
                List<CompletionContext> result = getFieldNames(cc, mon);
                return result;
            } else if (paramName.equals("depend0")) {
                List<CompletionContext> result = getFieldNames(cc, mon);
                return result;
            } else if ( (paramName.equals("skip")) || paramName.equals("skipLines") ) {
                List<CompletionContext> result = new ArrayList<>();
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", "number of lines to skip"));
                return result;
            } else if ( paramName.equals("recCount") ) {
                return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", "limit number of records to parse."));

            } else if ( paramName.equals("recStart") ) {
                return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", "record number to start."));
                
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
        CsvReader reader= null;

        List<CompletionContext> result = new ArrayList<>();
        try {
            reader= new CsvReader( fr );

            String[] columnHeaders;
            columnHeaders = getColumnHeaders( reader);
            
            for (String s : columnHeaders) {
                String label= s;
                //if ( ! label.equals(fields[i]) && label.startsWith("field") ) label += " ("+fields[i]+")";

                result.add(new CompletionContext(
                        CompletionContext.CONTEXT_PARAMETER_VALUE,
                        s,
                        label, null ) ) ;
            }
        } finally {
            if ( reader!=null ) reader.close();
            fr.close();
        }
        
        return result;

    }
    
    /**
     * detect identifiers for columns.  Copied from AsciiParser.java
     * @see AsciiParser#COLUMN_ID_HEADER_PATTERN
     */
    private static final Pattern COLUMN_ID_HEADER_PATTERN = Pattern.compile("\\s*\"?([a-zA-Z][a-zA-Z _0-9]*)([\\(\\[]([a-zA-Z_\\.\\[\\-\\]0-9//\\*\\^]*)[\\)\\]])?\"?\\s*");    
    
    public static String[] getColumnHeaders( CsvReader reader) throws IOException {
        return getColumnHeaders( reader, false );
    }
    
    /**
     * get the column headers for each column, and possibly switch over
     * to using a semicolon as a field delimiter.
     * @param reader
     * @param returnData if true, when data is detected, they are returned instead.
     * @return
     * @throws IOException 
     */
    public static String[] getColumnHeaders( CsvReader reader, boolean returnData ) throws IOException {
        String[] columnHeaders;
        if ( reader.readHeaders() ) {
            //int ncol= reader.getHeaderCount();
            columnHeaders= reader.getHeaders();
            if ( !returnData ) {
                for ( int i=0; i<columnHeaders.length; i++ ) {
                    if ( !COLUMN_ID_HEADER_PATTERN.matcher(columnHeaders[i]).matches() ) {
                        columnHeaders[i]= "field"+i;
                    }
                }
            }
        } else {
            columnHeaders= new String[reader.getColumnCount()];
            for ( int i=0; i<columnHeaders.length; i++ ) {
                columnHeaders[i]= "field"+i;
            }
        }
        if ( columnHeaders.length==1 ) {
            String peek= reader.getRawRecord();
            String[] newHeaders= peek.split(";",-2);
            if ( newHeaders.length>1 ) {
                for ( int i=0; i<newHeaders.length; i++ ) {
                    String s= newHeaders[i];
                    s= s.trim();
                    if ( s.startsWith("\"") && s.endsWith("\"") ) {
                        s= s.substring(1,s.length()-1);
                    }
                    newHeaders[i]= s;
                }
                columnHeaders= newHeaders;
                reader.setDelimiter(';');
            }
        }
        return columnHeaders;
    }

}
