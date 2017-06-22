/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.das2Stream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;
import org.autoplot.datasource.MetadataModel;
import org.autoplot.datasource.URISplit;
import org.das2.qstream.QDataSetStreamHandler;
import org.das2.qstream.StreamException;

/**
 *
 * @author jbf
 */
public class Das2StreamDataSourceFactory implements DataSourceFactory {

    public DataSource getDataSource(URI uri) throws IOException {
        return new Das2StreamDataSource(uri);
    }

    public List<CompletionContext> getCompletions(CompletionContext cc,org.das2.util.monitor.ProgressMonitor mon) throws IOException, StreamException {
        List<CompletionContext> result= new ArrayList<CompletionContext>();
        if ( cc.context==cc.CONTEXT_PARAMETER_NAME ) {
            if ( DataSetURI.fromUri( cc.resourceURI ).endsWith(".qds") ) {
                result.add( new CompletionContext(
                        CompletionContext.CONTEXT_PARAMETER_NAME,
                        "", this, "arg_0",
                        "", "default dataset", true ) );
                Map<String,String> params= getNames( cc, mon );
                for ( Entry<String,String> e: params.entrySet() ) {
                    //result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, s ) );
                    result.add( new CompletionContext(
                        CompletionContext.CONTEXT_PARAMETER_NAME,
                        e.getKey(), this, "arg_0",
                        e.getValue(), null, true ) );
                }
            }
        }
        return result;
    }

    public MetadataModel getMetadataModel(URL url) {
        return MetadataModel.createNullModel();
    }

    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        return false;
    }
    
    
    private Map<String,String> getNames( CompletionContext cc, ProgressMonitor mon ) throws IOException, StreamException {
        
        File file= DataSetURI.getFile( cc.resourceURI, mon  );
        
        QDataSetStreamHandler h= new QDataSetStreamHandler();
        h.setReadPackets(false); // don't read any records, just scan for datasets.
        org.das2.qstream.StreamTool.readStream( Channels.newChannel(new FileInputStream(file) ), h );
            
        return h.getDataSetNamesAndDescriptions();
        
    }

    public <T> T getCapability(Class<T> clazz) {
        return null;
    }

    public boolean supportsDiscovery() {
        return false;
    }


}
