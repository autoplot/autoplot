/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.binarydatasource;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSourceFactory;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSource;

/**
 *
 * @author jbf
 */
public class BinaryDataSourceFactory extends AbstractDataSourceFactory {

    @Override
    public DataSource getDataSource(URL url) throws Exception {
        return new BinaryDataSource( url );
    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) {
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            List<CompletionContext> result= new ArrayList<CompletionContext>();
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "byteOffset=", "byte offset of the first record" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "byteLength=", "total number of bytes to read" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "fieldCount=", "specify record length based on field type" ) );
            //result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "rank2" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "recLength=", "byte length of each record" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "recOffset=", "byte offset into each record") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "time=") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "timeFormat=" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "column=", "byte offset into each record based on field type" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "type=") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "depend0=") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "depend0Offset=", "byte offset into each record for dep0") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "depend0Type=") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "validMin=") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "validMax=") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "byteOrder=", "endianess of the data" ) );
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
            } else if ( paramName.equals("recOffset" ) ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>" ) );
            } else if ( paramName.equals("validMin" ) ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<double>" ) );
            } else if ( paramName.equals("validMax" ) ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<double>" ) );
            } else if ( paramName.equals("column") ) {
                return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<int>" ) );
            } else if ( paramName.equals("type") ) {
                List<CompletionContext> result= new ArrayList<CompletionContext>();
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "double") ); 
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "float") ); 
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "long") ); 
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "int") ); 
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "short") ); 
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
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "short") ); 
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "byte") ); 
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "ubyte") ); 
                return result;
            } else if ( paramName.equals("byteOrder") ) {
                List<CompletionContext> result= new ArrayList<CompletionContext>();
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "little") ); 
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "big") ); 
                return result;
            } else {
                return Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }
    }

    
}
