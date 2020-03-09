
package org.autoplot.matsupport;

import com.jmatio.io.MatFileReader;
import com.jmatio.types.MLArray;
import com.jmatio.types.MLNumericArray;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.URISplit;
import org.das2.util.monitor.ProgressMonitor;

/**
 * Matlab file reader
 * @author jbf
 */
public class MatDataSourceFactory extends AbstractDataSourceFactory {

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new MatDataSource(uri);
    }

    @Override
    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        try {
            URISplit split= URISplit.parse(surl);
            Map<String,String> params= URISplit.parseParams(split.params);
            String var= params.get(URISplit.PARAM_ARG_0);
            if ( var==null ) return true;
            File file = DataSetURI.getFile( split.file, mon );
            MatFileReader reader= new MatFileReader(file);
            Map<String,MLArray> content= reader.getContent();
            return !content.containsKey(var);
        } catch ( IOException ex ) {
            return true;
        }
    }
    
    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        if ( cc.context.equals(CompletionContext.CONTEXT_PARAMETER_NAME) ) {
            List<CompletionContext> ccresult= new ArrayList<>();
            File file= DataSetURI.getFile( cc.resourceURI, mon );
            MatFileReader reader= new MatFileReader(file);
            Map<String,MLArray> content= reader.getContent();
            for ( Entry<String,MLArray> e : content.entrySet() ) {
                if ( e.getValue() instanceof MLNumericArray ) {
                    CompletionContext cc1= new CompletionContext( 
                            CompletionContext.CONTEXT_PARAMETER_NAME,
                            e.getKey(), this, "arg_0", e.getKey()+" " +e.getValue(), "" );
                    ccresult.add(cc1);
                }
            }
            return ccresult;
        } else {
            return super.getCompletions(cc, mon);
        }
    }
    
    
    
}
