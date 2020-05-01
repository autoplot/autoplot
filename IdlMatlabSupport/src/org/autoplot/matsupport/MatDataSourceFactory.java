
package org.autoplot.matsupport;

import com.jmatio.io.MatFileReader;
import com.jmatio.types.MLArray;
import com.jmatio.types.MLNumericArray;
import com.jmatio.types.MLStructure;
import java.io.File;
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
        URISplit split= URISplit.parse(surl);
        Map<String,String> params= URISplit.parseParams(split.params);
        String var= params.get(URISplit.PARAM_ARG_0);
        if ( var==null ) return true;
        return false;
    }
    
    private void addCompletions( String root, String key, MLArray array, List<CompletionContext> ccresult ) {
        String keyn= root==null ? key : root + "." + key;
        if ( array instanceof MLNumericArray ) {
            CompletionContext cc1= new CompletionContext( 
                    CompletionContext.CONTEXT_PARAMETER_NAME,
                    keyn, this, "arg_0", keyn+" " +array, "" );
            ccresult.add(cc1);
        } else if ( array instanceof MLStructure ) {
            MLStructure mls= (MLStructure)array;
            String[] tagnames= mls.getFieldNames().toArray(new String[mls.getAllFields().size()]);
            MLArray[] aas= mls.getAllFields().toArray(new MLArray[mls.getAllFields().size()]);
            for ( int i=0; i<tagnames.length; i++ ) {
                MLArray a= aas[i];
                String n= tagnames[i];
                addCompletions( keyn, n, a, ccresult);                
            }
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
                addCompletions( null, e.getKey(), e.getValue(), ccresult );
            }
            return ccresult;
        } else {
            return super.getCompletions(cc, mon);
        }
    }
    
    
    
}
