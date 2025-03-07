
package org.autoplot.json;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.URISplit;
import org.das2.util.monitor.ProgressMonitor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Very simple start to a JSONJ stream 
 * @author jbf
 */
public class JSONDataSourceFactory extends AbstractDataSourceFactory {

    private List<CompletionContext> getFieldNames( CompletionContext cc, JSONArray jarray, List<CompletionContext> result ) throws JSONException {
        for ( int i=0; i<jarray.length(); i++ ) {
            String s= "field"+i;
            String val= String.valueOf( jarray.get(i) );
            result.add(new CompletionContext(
                CompletionContext.CONTEXT_PARAMETER_VALUE,
                s,
                s+" ("+val+")", null ) );
        }
        return result;
    }
    
    private List<CompletionContext> getFieldNames( CompletionContext cc, JSONObject jobj, List<CompletionContext> result ) throws JSONException {
        Iterator<String> keys= jobj.keys();
        while ( keys.hasNext() ) {
            String s= keys.next();
            String val= String.valueOf( jobj.get(s) );
            CompletionContext cc1;
            if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
                cc1=
                    new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, s, this, "arg_0", s + " ("+val+")", null, true );
            } else {
                cc1=
                    new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, s, this, null, s + " ("+val+")", null, true );
            }
            result.add( cc1 );   
        }
        return result;
    }
    
    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        File f= DataSetURI.getFile( cc.surl, mon );
        JSONObject jobj=null;
        JSONArray jarray=null;

        List<CompletionContext> result = new ArrayList<>();
        
        try ( InputStream ins= new FileInputStream(f) ) {
            JSONJIterator jiter= new JSONJIterator(ins);
            if ( jiter.hasNext() ) {
                Object obj= jiter.next();
                if ( obj instanceof JSONArray ) {
                    jarray= (JSONArray)obj;
                } else if ( obj instanceof JSONObject ) {
                    jobj= (JSONObject)obj;
                }
            }
        } // this should close after reading on record.
        
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            if ( jarray!=null ) {
                result = getFieldNames( cc, jarray, result );
            } else if ( jobj!=null ) {
                result= getFieldNames( cc, jobj, result );
            } else {
                throw new IllegalArgumentException("Expected to see JSONArray or JSONObject in: " + cc.surl );
            }
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "depend0=", "Name of the independent variable."));
            return result;   
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            String paramName = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            switch ( paramName ) {
                case "depend0": {
                    if ( jarray!=null ) {
                        result = getFieldNames( cc, jarray, result );
                    } else if ( jobj!=null ) {
                        result= getFieldNames( cc, jobj, result );
                    } else {
                        throw new IllegalArgumentException("Expected to see JSONArray or JSONObject in: " + cc.surl );
                    }
                }
            }
            return result;
        } else {
            throw new IllegalArgumentException("not implemented l78");
        }
    }

    @Override
    public boolean reject(String suri, List<String> problems, ProgressMonitor mon) {
        URISplit split = URISplit.parse(suri);
        return split.params==null;
    }

    
    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new JSONDataSource(uri);
    }
    
}
