
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
                s+"("+val+")", null ) );
        }
        return result;
    }
    
    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        File f= DataSetURI.getFile( cc.surl, mon );
        try ( InputStream ins= new FileInputStream(f) ) {
            JSONJIterator jiter= new JSONJIterator(ins);
            if ( jiter.hasNext() ) {
                Object obj= jiter.next();
                List<CompletionContext> result = new ArrayList<CompletionContext>();
                if ( obj instanceof JSONArray ) {
                    result = getFieldNames( cc, (JSONArray)obj, result );
                    return result;
                } else if ( obj instanceof JSONObject ) {
                    JSONObject jobj= (JSONObject)obj;
                    Iterator<String> keys= jobj.keys();
                    while ( keys.hasNext() ) {
                        String s= keys.next();
                        String val= String.valueOf( jobj.get(s) );
                        result.add(new CompletionContext(
                            CompletionContext.CONTEXT_PARAMETER_VALUE,
                            s,
                            s+"("+val+")", null ) );   
                    }
                    return result;
                }
            }
        }
        throw new IllegalArgumentException("File is empty: "+cc.surl);
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
