
package org.autoplot.idlsupport;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.URISplit;
import org.autoplot.idlsupport.ReadIDLSav.ArrayDesc;
import org.das2.util.monitor.ProgressMonitor;

/**
 * Factory for reading IDLSave files.
 * @author jbf
 */
public class IdlsavDataSourceFactory extends AbstractDataSourceFactory {

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new IdlsavDataSource(uri);
    }

    @Override
    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        URISplit split= URISplit.parse(surl);
        Map<String,String> params= URISplit.parseParams(split.params);
        String var= params.get(URISplit.PARAM_ARG_0);
        return var==null;
    }
    
    private void addCompletions( ReadIDLSav reader, String root, String key, ByteBuffer buf, List<CompletionContext> ccresult ) throws IOException {
        String keyn= root==null ? key : root + "." + key;
        
        if ( reader.isArray( buf, key ) ) {
            ArrayDesc desc= reader.readArrayDesc( buf, key );
            StringBuilder sqube= new StringBuilder("[").append(String.valueOf(desc.dims[0]));
            for ( int i=1; i<desc.ndims; i++ ) {
                sqube.append(",").append(String.valueOf(desc.dims[i]));
            }
            sqube.append("]");
            CompletionContext cc1= new CompletionContext( 
                    CompletionContext.CONTEXT_PARAMETER_NAME,
                    keyn, this, "arg_0", keyn+" " +sqube, "" );
            ccresult.add(cc1);
        } else {
            CompletionContext cc1= new CompletionContext( 
                    CompletionContext.CONTEXT_PARAMETER_NAME,
                    keyn, this, "arg_0", keyn+" scalar", "" );
            ccresult.add(cc1);
        }

    }
    
    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        if ( cc.context.equals(CompletionContext.CONTEXT_PARAMETER_NAME) ) {
            List<CompletionContext> ccresult= new ArrayList<>();
            File file= DataSetURI.getFile( cc.resourceURI, mon );
            ByteBuffer buf= ReadIDLSav.readFileIntoByteBuffer(file);
            String[] names= new ReadIDLSav().readVarNames(buf);
            ReadIDLSav reader= new ReadIDLSav();
            for ( int i=0; i<names.length; i++ ) {
                addCompletions( reader, null, names[i], buf, ccresult );
            }
            return ccresult;
        } else {
            return super.getCompletions(cc, mon);
        }
    }
    
    
    
}
