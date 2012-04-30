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
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.das2.datum.EnumerationUnits;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.MetadataModel;
import org.virbo.datasource.URISplit;
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

    public DataSource getDataSource(URI uri) throws FileNotFoundException, IOException {
        return new AsciiTableDataSource(uri);
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
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "rank2=", "read in more than one column to create a rank 2 dataset."));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "bundle=", "read in more than one column to create a rank 2 bundle dataset."));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "depend1Labels=", "label each of the columns, bundling different data together in rank 2 dataset"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "depend1Values=", "values for each column, making a rank 2 table of values."));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "time="));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "timeFormat=",
                    "template for parsing time digits, default is ISO8601."));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, AsciiTableDataSource.PARAM_INTERVAL_TAG+"=",
                    "indicate how measurement intervals are tagged." ) );
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "depend0="));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "fill="));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "comment=",
                    "comment line prefix, default is hash (#)"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "headerDelim=",
                    "string indicating the end of the header (a regular expression)"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "validMin=",
                    "values less than this value are treated as fill."));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "validMax=",
                    "values greater than this value are treated as fill."));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "delim=",
                    "parse records by splitting on delimiter."));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "tail=",
                    "read the last n records."));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "eventListColumn=",
                    "read in the file as an event list, where the first two columns are UT times"));

            return result;
        } else if (cc.context == CompletionContext.CONTEXT_PARAMETER_VALUE) {
            String paramName = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            if (paramName.equals("skip")) {
                return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", "the number of lines to skip before attempting to parse."));
            } else if ( paramName.equals("headerDelim") ) {
                List<CompletionContext> result = new ArrayList<CompletionContext>();
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<string>" ) );
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "#####" ));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "DATA_UNTIL", "Cluster CEF uses these"));
                return result;
            } else if (paramName.equals("recCount")) {
                return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", "limit number of records to parse."));
            } else if (paramName.equals("rank2")) {
                List<CompletionContext> result = new ArrayList<CompletionContext>();
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", "number of columns to expect"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "1:", "all but first column"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "1:5", "second through 5th columns"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "-5:", "last five columns"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, ":", "all columns"));
                return result;
            } else if (paramName.equals("bundle")) {
                List<CompletionContext> result = new ArrayList<CompletionContext>();
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", "number of columns to expect"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "Bx-Bz", "three named columns"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "1:", "all but first column"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "1:5", "second through 5th columns"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "-5:", "last five columns"));
                return result;
            } else if (paramName.equals("depend1Labels")) {
                List<CompletionContext> result = new ArrayList<CompletionContext>();
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>:<int>", "labels for each column"));
                return result;
            } else if (paramName.equals("depend1Values")) {
                List<CompletionContext> result = new ArrayList<CompletionContext>();
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>:<int>", "values for each column"));
                return result;
            } else if (paramName.equals("column")) {
                List<CompletionContext> result = getFieldNames(cc, mon);
                return result;
            } else if (paramName.equals("fixedColumns")) {
                return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", "Hint at the number of columns to expect, then use fast parser that assumes fixed columns."));
            } else if (paramName.equals("time")) {
                List<CompletionContext> result = getFieldNames(cc, mon);
                return result;
            } else if (paramName.equals("intervalTag")) {
                List<CompletionContext> result = new ArrayList<CompletionContext>();
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "start","tag values indicate the start of measurement interval"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "center", "tag values indicate the start of measurement interval."));
                return result;
            } else if (paramName.equals("depend0")) {
                List<CompletionContext> result = getFieldNames(cc, mon);
                return result;
            } else if (paramName.equals("timeFormat")) {
                List<CompletionContext> result = new ArrayList<CompletionContext>();
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "$Y+$j+$H+$M","times can span multiple fields"));
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "ISO8601", "parse ISO8601 times in one field."));
                return result;
            } else if (paramName.equals("fill")) {
                return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<double>"));
            } else if (paramName.equals("validMin")) {
                return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<double>"));
            } else if (paramName.equals("validMax")) {
                return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<double>"));
            } else if (paramName.equals("tail")) {
                return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>"));
            } else if (paramName.equals("eventListColumn")) {
                List<CompletionContext> result = getFieldNames(cc, mon);
                if ( result.size()>2 ) result.subList( 2, result.size() );
                return result;
            } else {
                return Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }
    }

    public boolean reject(String surl, ProgressMonitor mon) {
        try {
            URISplit split = URISplit.parse(surl);

            Map<String, String> params = URISplit.parseParams(split.params);
            
            if ( params.get("rank2")!=null ) return false;
            if ( params.get("bundle")!=null ) return false;
            if ( params.get("group")!=null ) return false;
            if ( params.get("eventListColumn")!=null ) return false;

            String arg_0= params.get("arg_0");
            if ( arg_0!=null ) {
                if ( arg_0.equals("rank2") || arg_0.equals("bundle") ) return false;
            }

            File file = DataSetURI.getFile(split.resourceUri, mon);
            if ( !file.isFile() ) return true;
            
            List<CompletionContext> cc= getFieldNames( file, params, mon );

            if ( cc.size()<=2 ) {
                return false;
            } else {
                if ( params.get("column")!=null ) {
                    return false;
                } else {
                    return true;
                }
            }

        } catch (IOException ex) {
            return false;
        }
    }

    private List<CompletionContext> getFieldNames( File file, Map<String,String> params, ProgressMonitor mon ) throws IOException {

        AsciiParser parser = AsciiParser.newParser(5);
        if (params.containsKey("skip")) {
            parser.setSkipLines(Integer.parseInt((String) params.get("skip")));
        }
        if (params.containsKey("skipLines")) {
            parser.setSkipLines(Integer.parseInt((String) params.get("skipLines")));
        }
        if (params.containsKey("comment") ) {
            parser.setCommentPrefix(params.get("comment") );
        }
        if (params.containsKey("headerDelim") ) {
            parser.setHeaderDelimiter(params.get("headerDelim"));
        }
        DelimParser dp= parser.guessSkipAndDelimParser(file.toString());

        if ( params.containsKey("eventListColumn") ) {
            parser.setUnits( parser.getFieldIndex(params.get("eventListColumn")), new EnumerationUnits("events") );;
        }
        String line= parser.readFirstParseableRecord(file.toString());
        if ( line==null ) {
            //dp= parser.guessSkipAndDelimParser(file.toString());
            throw new IllegalArgumentException("unable to find parseable record");
        }
        
        String[] fields= new String[ dp.fieldCount() ];
        dp.splitRecord( line, fields );

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

    private List<CompletionContext> getFieldNames(CompletionContext cc, ProgressMonitor mon) throws IOException {

        Map<String,String> params = URISplit.parseParams(cc.params);
        File file = DataSetURI.getFile(cc.resourceURI, mon);

        return getFieldNames( file, params, mon );

    }

    public <T> T getCapability(Class<T> clazz) {
        return null;
    }
}
