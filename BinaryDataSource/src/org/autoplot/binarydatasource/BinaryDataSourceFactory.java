
package org.autoplot.binarydatasource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSource;

/**
 * Factory for BinaryDataSource, which reads data from binary files.
 * @author jbf
 */
public class BinaryDataSourceFactory extends AbstractDataSourceFactory {

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new BinaryDataSource( uri );
    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) {
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            List<CompletionContext> result= new ArrayList<>();
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "byteOffset=", "byte offset of the first record" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "byteLength=", "total number of bytes to read (limit 2G)" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "fieldCount=", "specify record length based on field type" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "rank2=", "start and stop indices for rank 2 data set" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "recCount=", "limit the number of records to read in" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "recLength=", "byte length of each record" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "recOffset=", "byte offset into each record") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "recFormat=", "specify field types and rec size in one string") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "column=", "byte offset into each record based on field type" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "type=") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "depend0=") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "depend0Offset=", "byte offset into each record for dep0.  If the first 8 bytes is the timetag, then this would be zero (which is also the default).") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "depend0Type=") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "depend0Units=", "support timetags like 'seconds since 2001-001'" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "validMin=") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "validMax=") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "fillValue=", "value indicating invalid data.") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "units=", "indicating unit type, like cmps or TT2000.") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "byteOrder=", "endianess of the data" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "reportOffset=T", "depend0 is byte offset into file, this is the legacy (2010) behavior"));
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "format=", "specify format"));
            return result;
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            String paramName= CompletionContext.get( CompletionContext.CONTEXT_PARAMETER_NAME, cc );
            switch (paramName) {
                case "byteOffset":
                    return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>" ) );
                case "byteLength":
                    return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>" ) );
                case "fieldCount":
                    return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>" ) );
                case "recLength":
                    return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>" ) );
                case "recCount":
                    return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>" ) );
                case "recOffset":
                    return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>" ) );
                case "validMin":
                    return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<double>" ) );
                case "validMax":
                    return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<double>" ) );
                case "units":
                {
                    List<CompletionContext> result= new ArrayList<>();
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "cdfTT2000", "CDF time tags") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "cmps", "cm per second") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "V/m", "Volts per meter") );
                    return result;
                }
                case "fillValue":
                {
                    List<CompletionContext> result= new ArrayList<>();
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<double>", "invalid value") );
                    return result;
                }
                case "column":
                    return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>" ) );
                case "rank2":
                {
                    List<CompletionContext> result= new ArrayList<>();
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>:<int>", "first,last (exclusive) fields" ) );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "0:", "return rank two to last field" ) );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, ":", "as many as will fit in one record" ) );
                    return result;
                }
                case "type":
                {
                    List<CompletionContext> result= new ArrayList<>();
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "double") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "float") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "long") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "int") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "uint") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "truncatedFloat") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "vaxFloat") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "short") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "ushort") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "byte") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "ubyte") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "nybble", "four-bit integers") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "time24", "ISO8601 time in 24 ASCII characters (16-24 allowed)") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "ascii8", "Formatted number in 8 ASCII characters (1-24 allowed)") );
                    return result;
                }
                case "depend0":
                    return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>" ) );
                case "depend0Type":
                {
                    List<CompletionContext> result= new ArrayList<>();
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "double") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "float") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "long") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "int") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "uint") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "truncatedFloat") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "vaxFloat") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "short") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "ushort") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "byte") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "ubyte") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "time24", "ISO8601 time in 24 ASCII characters (16-24 allowed)") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "ascii8", "Formatted number in 8 ASCII characters (1-24 allowed)") );
                    return result;
                }
                case "depend0Units":
                {
                    List<CompletionContext> result= new ArrayList<>();
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "seconds since 2001-001T00:00") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "ms since 2001-001T00:00") );
                    return result;
                }
                case "byteOrder":
                {
                    List<CompletionContext> result= new ArrayList<>();
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "little", "(default) first byte has little significance") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "big", "first byte has big significance") );
                    return result;
                }
                case "recFormat":
                {
                    List<CompletionContext> result= new ArrayList<>();
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "d,13f", "double followed by 13 floats") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "i,s,ub", "int, short, unsigned byte") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "x,ub,ui", "skip byte, unsigned byte, unsigned int") );
                    return result;
                }
                case "format":
                {
                    List<CompletionContext> result= new ArrayList<>();
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "%d", "format as integer") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "%c", "format as character") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "%x", "format as hexidecimal") );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "%.1f", "format as double with one decimal") );
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
    public String getDescription() {
        return "Binary Tables within files";
    }
    
}
