/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.binarydatasource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.AbstractDataSourceFactory;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSource;

/**
 *
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
            List<CompletionContext> result= new ArrayList<CompletionContext>();
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "byteOffset=", "byte offset of the first record" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "byteLength=", "total number of bytes to read" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "fieldCount=", "specify record length based on field type" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "rank2=", "start and stop indeces for rank 2 data set" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "recCount=", "limit the number of records to read in" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "recLength=", "byte length of each record" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "recOffset=", "byte offset into each record") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "recFormat=", "specify field types and rec size in one string") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "column=", "byte offset into each record based on field type" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "type=") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "depend0=") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "depend0Offset=", "byte offset into each record for dep0") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "depend0Type=") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "validMin=") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "validMax=") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "byteOrder=", "endianess of the data" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "reportOffset=yes", "depend0 is offset into file, this is the legacy (2010) behavior"));
            return result;
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            String paramName= CompletionContext.get( CompletionContext.CONTEXT_PARAMETER_NAME, cc );
            if ( paramName.equals("byteOffset") ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>" ) );
            } else if ( paramName.equals("byteLength" ) ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>" ) );
            } else if ( paramName.equals("fieldCount" ) ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>" ) );
            } else if ( paramName.equals("byteLength" ) ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>" ) );
            } else if ( paramName.equals("recLength" ) ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>" ) );
            } else if ( paramName.equals("recCount" ) ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>" ) );
            } else if ( paramName.equals("recOffset" ) ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>" ) );
            } else if ( paramName.equals("validMin" ) ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<double>" ) );
            } else if ( paramName.equals("validMax" ) ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<double>" ) );
            } else if ( paramName.equals("column") ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>" ) );
            } else if ( paramName.equals("rank2") ) {
                List<CompletionContext> result= new ArrayList<CompletionContext>();
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>:<int>", "first,last (exclusive) fields" ) );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "0:", "return rank two to last field" ) );
                return result;
            } else if ( paramName.equals("type") ) {
                List<CompletionContext> result= new ArrayList<CompletionContext>();
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
                return result;
            } else if ( paramName.equals("depend0") ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>" ) );
            } else if ( paramName.equals("depend0Type") ) {
                List<CompletionContext> result= new ArrayList<CompletionContext>();
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
                return result;
            } else if ( paramName.equals("byteOrder") ) {
                List<CompletionContext> result= new ArrayList<CompletionContext>();
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "little", "(default) first byte has little significance") );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "big", "first byte has big significance") );
                return result;
            } else if ( paramName.equals("recFormat") ) {
                List<CompletionContext> result= new ArrayList<CompletionContext>();
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "d,13f", "double followed by 13 floats") );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "i,s,ub", "int, short, unsigned byte") );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "x,ub,ui", "skip byte, unsigned byte, unsigned int") );
                return result;
            } else {
                return Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }
    }

    
}
