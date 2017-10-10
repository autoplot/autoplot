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
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;

/**
 * Factory for producing comma separated values file sources.
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
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "delim=",
                    "override the default delimiter (comma)."));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "skipLines=", "skip this many lines before parsing"));
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
            } else if ( paramName.equals("delim") ) {
                List<CompletionContext> result = new ArrayList<CompletionContext>();
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
        CsvReader reader= null;

        List<CompletionContext> result = new ArrayList<CompletionContext>();
        try {
            reader= new CsvReader( fr );

            String[] columnHeaders;
            columnHeaders = getColumnHeaders( reader);
            
            for ( int i=0; i<columnHeaders.length; i++ ) {
                String s= columnHeaders[i];
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
     * get the column headers for each column, and possibly switch over
     * to using a semicolon as a field delimiter.
     * @param reader
     * @return
     * @throws IOException 
     */
    public static String[] getColumnHeaders( CsvReader reader) throws IOException {
        String[] columnHeaders;
        if ( reader.readHeaders() ) {
            //int ncol= reader.getHeaderCount();
            columnHeaders= reader.getHeaders();
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

    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        return false;
    }

    public <T> T getCapability(Class<T> clazz) {
        return null;
    }

    public boolean supportsDiscovery() {
        return false;
    }

}
