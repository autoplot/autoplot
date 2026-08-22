/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.html;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.text.html.parser.ParserDelegator;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.URISplit;
import org.das2.qds.QDataSet;
import org.das2.util.monitor.NullProgressMonitor;

/**
 *
 * @author jbf
 */
public class HtmlTableDataSourceFactory extends AbstractDataSourceFactory {

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new HtmlTableDataSource( uri );
    }

    /**
     * return a list of the tables, with column and human readable description after.
     * @param resourceURI
     * @return a list of the tables, with column and human readable description after.
     * @throws java.io.IOException 
     */
    public static List<String> getTables( URI resourceURI ) throws java.io.IOException {
        File f= DataSetURI.downloadResourceAsTempFile( resourceURI.toURL(), new NullProgressMonitor() );

        BufferedReader reader = new BufferedReader( new FileReader(f));

        HtmlParserCallback callback = new HtmlParserCallback(  );

        new ParserDelegator().parse( reader, callback, true );

        List<String> tables= new ArrayList(callback.getTables());

        return tables;

    }
    
    /**
     * return the columns of the table
     * @param resourceURI
     * @param table
     * @return
     * @throws IOException 
     */
    public static List<String> getColumns( URI resourceURI, String table ) throws IOException {
        File f= DataSetURI.downloadResourceAsTempFile( resourceURI.toURL(), new NullProgressMonitor() );

        BufferedReader reader = new BufferedReader( new FileReader(f));

        HtmlParserCallback callback = new HtmlParserCallback(  );

        new ParserDelegator().parse( reader, callback, true );
        callback.setTable(table);
        
        QDataSet qds= callback.getDataSet();
        
        List<String> result= new ArrayList(); // TODO: improve by getting column labels, etc.
        for ( int i=0; i<qds.length(0); i++ ) {
            result.add(String.valueOf(i));
        }
        return result;
    }
    
    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            List<CompletionContext> result = new ArrayList<CompletionContext>();
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "column=", "the name (or number) of the column to plot") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "table=", "the table name (or number) of the table" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "units=", "units for the column" ) );
            return result;
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            List<CompletionContext> result = new ArrayList<CompletionContext>();
            String paramName = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            if ( paramName.equals("table") ) {    
                List<String> tables= getTables(cc.resourceURI);
                for ( String t : tables ) {
                    int i= t.indexOf(":");
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, t.substring(0,i), t.substring(i+1).trim(), t ) );
                }
                return result;
            } else if ( paramName.equals("column") ) {   
                Map<String,String> params = URISplit.parseParams(cc.params);
                String table= params.get("table");
                if ( table==null ) {
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "0", "the first column" ) );
                } else {
                    List<String> columns= getColumns( cc.resourceURI, table );
                    for ( String t : columns ) {
                        int i= t.indexOf(":");
                        if ( i==-1 ) {
                            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, t, t, t ) );
                        } else {
                            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, t.substring(0,i), t.substring(i+1).trim(), t ) );
                        }
                    }
                }
                return result;
            } else if ( paramName.equals("units") ) {
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "dollars", "U.S. dollars", "" ) );
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "year", "Year", "" ) );
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "nT", "example units for the data"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "enum", "the data is nominal data, not numeric"));    
                return result;
            } else {
                return Collections.emptyList();
            }
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
