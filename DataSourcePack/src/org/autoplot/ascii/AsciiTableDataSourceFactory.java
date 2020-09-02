
package org.autoplot.ascii;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.UnitsUtil;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;
import org.autoplot.datasource.MetadataModel;
import org.autoplot.datasource.URISplit;
import org.das2.qds.util.AsciiParser;
import org.das2.qds.util.AsciiParser.DelimParser;

/**
 * Factory for AsciiTableDataSource readers for the ASCII table reader.
 * @author jbf
 */
public class AsciiTableDataSourceFactory extends AbstractDataSourceFactory implements DataSourceFactory {

    /** Creates a new instance of AsciiTableDataSourceFactory */
    public AsciiTableDataSourceFactory() {
    }

    @Override
    public DataSource getDataSource(URI uri) throws FileNotFoundException, IOException {
        return new AsciiTableDataSource(uri);
    }

    public String editPanel(String surl) throws Exception {
        return surl;
    }

    public MetadataModel getMetadataModel(URL url) {
        return MetadataModel.createNullModel();
    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, org.das2.util.monitor.ProgressMonitor mon) throws Exception {
        if (cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {
            List<CompletionContext> result = new ArrayList<>();
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "skipLines=", "the number of lines to skip before attempting to parse"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "recCount=", "the number of records to read in"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "recStart=", "skip this number of records"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "column=", "the column to read in"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "units=", "units of the data"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "ordinal=fpe,fuh", "set of ordinals that appear in this column"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "fixedColumns=", "use the fixed columns parser"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "columnCount=", "only use records with this many columns") );
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
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "depend0Units="));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "fill="));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "comment=",
                    "comment line prefix, default is hash (#)"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "headerDelim=",
                    "string indicating the end of the header (a regular expression)"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "pattern=",
                    "regular expression for each record, and data from matching groups are plotted."));
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
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "where=",
                    "add constraint by another field's value"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "title=",
                    "title for the dataset"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "label=",
                    "label for the dataset"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "X=",
                    "values typically displayed in horizontal dimension"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "Y=",
                    "values typically displayed in vertical dimension"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "Z=",
                    "values typically color coded"));
            return result;
        } else if (cc.context == CompletionContext.CONTEXT_PARAMETER_VALUE) {
            String paramName = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            switch (paramName) {
                case "skip":
                case "skipLines":
                    return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", "the number of lines to skip before attempting to parse."));
                case "headerDelim": {
                    List<CompletionContext> result = new ArrayList<>();
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<string>" ) );
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "#####" ));
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "DATA_UNTIL", "Cluster CEF uses these"));
                    return result;
                }
                case "pattern": {
                    List<CompletionContext> result = new ArrayList<>();
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, ".+:(\\d+).*", "load the one or more integers" ) );
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, ".+:(?<vv>[0-9e\\.\\-]+).*", "name the float field vv" ) );
                    return result;
                }
                case "recCount":
                    return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", "limit number of records to parse"));
                case "recStart":
                    return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", "skip this number of records"));
                case "columnCount":
                    return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", "number of columns to expect"));
                    
                case "rank2": {
                    List<CompletionContext> result = new ArrayList<>();
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", "number of columns to expect"));
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "1:", "all but first column"));
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "1:5", "second through 5th columns"));
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "-5:", "last five columns"));
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, ":", "all columns"));
                    return result;
                }
                case "bundle": {
                    List<CompletionContext> result = new ArrayList<>();
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", "number of columns to expect"));
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "Bx-Bz", "three named columns"));
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "1:", "all but first column"));
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "1:5", "second through 5th columns"));
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "-5:", "last five columns"));
                    return result;
                }
                case "depend1Labels": {
                    List<CompletionContext> result = new ArrayList<>();
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>:<int>", "labels for each column"));
                    return result;
                }
                case "depend1Values": {
                    List<CompletionContext> result = new ArrayList<>();
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>:<int>", "values for each column"));
                    return result;
                }
                case "column": {
                    List<CompletionContext> result = getFieldNames(cc, mon);
                    return result;
                }
                case "units":  {
                    List<CompletionContext> result = new ArrayList<>();
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "nT", "example units for the data"));
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "enum", "the data is nominal data, not numeric"));
                    return result;
                }
                case "fixedColumns":
                    return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>", "Hint at the number of columns to expect, then use fast parser that assumes fixed columns."));
                case "time": {
                    List<CompletionContext> result = getFieldNames(cc, mon);
                    return result;
                }
                case "intervalTag": {
                    List<CompletionContext> result = new ArrayList<>();
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "start","tag values indicate the start of measurement interval"));
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "center", "tag values indicate the start of measurement interval."));
                    return result;
                }
                case "depend0": {
                    List<CompletionContext> result = getFieldNames(cc, mon);
                    return result;
                }
                case "depend0Units": {
                    List<CompletionContext> result = new ArrayList<>();
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "ms", "units for the x tags"));
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "hours+since+2015-01-01T00:00", "units for the x tags"));
                    return result;
                }
                case "timeFormat": {
                    List<CompletionContext> result = new ArrayList<>();
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "$Y+$j+$H+$M","times can span multiple fields"));
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "ISO8601", "parse ISO8601 times in one field."));
                    return result;
                }
                case "fill":
                    return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<double>"));
                case "validMin":
                    return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<double>"));
                case "validMax":
                    return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<double>"));
                case "tail":
                    return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>"));
                case "eventListColumn": {
                    List<CompletionContext> result = getFieldNames(cc, mon);
                    if ( result.size()>2 ) result= result.subList( 2, result.size() );
                    return result;
                }
                case "where": { // TODO: a fun project would be to make completions for this that look in the file...
                    List<CompletionContext> result = new ArrayList<>();
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "field17.gt(1)","where the double value in field17 is greater than 17 "));
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "field5.eq(off)", "where the nominal data in field5 is equal to \"off\""));
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "field0.le(2000-01-01T00:00)", "where the nominal data in field5 is equal to \"off\""));
                    return result;
                }
                case "X": {
                    List<CompletionContext> result = getFieldNames(cc, mon);
                    return result;
                }
                case "Y": {
                    List<CompletionContext> result = getFieldNames(cc, mon);
                    return result;
                }
                case "Z": {
                    List<CompletionContext> result = getFieldNames(cc, mon);
                    return result;
                }                
                default:
                    return Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        try {
            URISplit split = URISplit.parse(surl);

            Map<String, String> params = URISplit.parseParams(split.params);
            
            if ( params.get("rank2")!=null ) return false;
            if ( params.get("bundle")!=null ) return false;
            if ( params.get("group")!=null ) return false;
            if ( params.get("eventListColumn")!=null ) return false;
            if ( params.get("Z")!=null && params.get("Z").length()>0 ) return false;
            if ( params.get("Y")!=null && params.get("Y").length()>0  ) return false;
            
            String arg_0= params.get("arg_0");
            if ( arg_0!=null ) {
                if ( arg_0.equals("rank2") || arg_0.equals("bundle") ) return false;
            }

            if ( split.resourceUri==null ) {
                return true;
            }
            
            File file = DataSetURI.getFile(split.resourceUri, mon);
            if ( !file.isFile() ) return true;
            
            List<CompletionContext> cc= getFieldNames(file, params);

            if ( cc.size()<=2 ) {
                return false;
            } else {
                if ( params.get("column")!=null ) {
                    return false; 
                } else {
                    //TODO bug1490: there's a bug here, because not all the rich ascii fields appear in the completions list.
                    for (CompletionContext cc1 : cc) {
                        if (cc1.completable.equals(arg_0)) {
                            return false;
                        }
                    }
                    if ( cc.size()>0 && cc.size()<7 ) {  // kludge where the last completion will be for eventListColumn when this could be done automatically. // bug1900.
                        CompletionContext lastCC= cc.get(cc.size()-1);
                        if ( lastCC.context==CompletionContext.CONTEXT_PARAMETER_NAME 
                                && lastCC.completable.equals("eventListColumn") ) {
                            return false;
                        }
                    }
                    return true;
                }
            }

        } catch (IOException ex) {
            return false;
        }
    }

    /**
     * return the list of fields names found in the ASCII file.
     * @param uri the URI, containing modifiers like skip.
     * @param mon progress monitor for the download
     * @return the list of field names
     * @throws IOException 
     */
    public static List<String> getFieldNames( String uri, ProgressMonitor mon ) throws IOException {
        URISplit split= URISplit.parse(uri);
        Map<String,String> params = URISplit.parseParams(split.params);
        File file = DataSetURI.getFile( split.resourceUri, mon);
        List<CompletionContext> cc= getFieldNames(file, params);
        List<String> result= new ArrayList<>(cc.size());
        for ( CompletionContext c: cc ) {
            result.add( c.label );
        }
        return result;
    }
    
    private static List<CompletionContext> getFieldNames( File file, Map<String,String> params) throws IOException {

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

        parser.guessSkipAndDelimParser(file.toString());
        
        if ( params.containsKey("eventListColumn") ) {
            int i= parser.getFieldIndex(params.get("eventListColumn"));
            if ( i!=-1 ) parser.setUnits( i, new EnumerationUnits("events") );
        }
        String line= parser.readFirstParseableRecord(file.toString());
        if ( line==null ) {
            //dp= parser.guessSkipAndDelimParser(file.toString());
            throw new IllegalArgumentException("unable to find parseable record");
        }
        
        DelimParser dp= parser.guessSkipAndDelimParser(file.toString());
        
        if ( dp==null ) {
            throw new IllegalArgumentException("unable to find delimited columns");
        }
        
        String[] fields= new String[ dp.fieldCount() ];
        dp.splitRecord( line, fields );

        String[] columns = parser.getFieldNames();
        List<CompletionContext> result = new ArrayList<>();

        for ( int i=0; i<columns.length; i++ ) {
            String s= columns[i];
            String label= s;
            if ( ! label.equals(fields[i]) && label.startsWith("field") ) label += " ("+fields[i]+")";

            result.add(new CompletionContext(
                    CompletionContext.CONTEXT_PARAMETER_VALUE,
                    s,
                    label, null ) ) ;
        }
        

        Map<String,String> richFields= parser.getRichFields();
        for ( Entry<String,String> e: richFields.entrySet() ) {
            result.add(new CompletionContext(
                    CompletionContext.CONTEXT_PARAMETER_NAME,
                    e.getValue(),
                    e.getKey(), null ) );
        }
        
        if ( parser.getFieldCount()>2 && UnitsUtil.isTimeLocation(parser.getUnits(0)) && UnitsUtil.isTimeLocation(parser.getUnits(1) ) ) {
            result.add(new CompletionContext(
                    CompletionContext.CONTEXT_PARAMETER_NAME,
                    "eventListColumn",
                    "eventListColumn", null ) ) ;
        }
        
        return result;

    }

    private List<CompletionContext> getFieldNames(CompletionContext cc, ProgressMonitor mon) throws IOException {

        Map<String,String> params = URISplit.parseParams(cc.params);
        File file = DataSetURI.getFile(cc.resourceURI, mon);

        return getFieldNames(file, params);

    }

    @Override
    public String getDescription() {
        return "ASCII Tables";
    }

}
