
package org.autoplot.idlsupport;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
        
        if ( root!=null ) {
            Object o= reader.readVar( buf, root );
            Map<String,Object> m= (Map<String,Object>)o;
            
            for ( Entry<String,Object> e: m.entrySet() ) {
                CompletionContext cc1= new CompletionContext( 
                    CompletionContext.CONTEXT_PARAMETER_NAME,
                    keyn, this, "arg_0", root + "." + key, "", true );
                ccresult.add(cc1);
            }            
        }
        if ( reader.isArray( buf, key ) ) {
            ArrayDesc desc= reader.readArrayDesc( buf, key );
            StringBuilder sqube= new StringBuilder("[").append(String.valueOf(desc.dims[0]));
            for ( int i=1; i<desc.ndims; i++ ) {
                sqube.append(",").append(String.valueOf(desc.dims[i]));
            }
            sqube.append("]");
            CompletionContext cc1= new CompletionContext( 
                    CompletionContext.CONTEXT_PARAMETER_NAME,
                    keyn, this, "arg_0", keyn+" " +sqube, "", true );
            ccresult.add(cc1);
        } else {
            CompletionContext cc1= new CompletionContext( 
                    CompletionContext.CONTEXT_PARAMETER_NAME,
                    keyn, this, "arg_0", keyn+" scalar", "", true );
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
            if ( cc.completable.contains(".") ) {
                int i= cc.completable.indexOf('.');
                String root= cc.completable.substring(0,i);
                Object o= reader.readVar( buf, root );
                Map<String,Object> m= (Map<String,Object>)o;
                for ( Entry<String,Object> e: m.entrySet() ) {
                    CompletionContext cc1= new CompletionContext( 
                        CompletionContext.CONTEXT_PARAMETER_NAME,
                        root + "." + e.getKey(), this, "arg_0", root + "." + e.getKey(), "", true );
                    ccresult.add(cc1);
                }
            } else {
                for ( int i=0; i<names.length; i++ ) {
                    addCompletions( reader, null, names[i], buf, ccresult );
                }
            }
            return ccresult;
        } else {
            return super.getCompletions(cc, mon);
        }
    }
    
    
    
}
