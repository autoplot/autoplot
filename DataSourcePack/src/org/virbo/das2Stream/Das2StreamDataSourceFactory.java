/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.das2Stream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.MetadataModel;
import org.virbo.datasource.URISplit;
import org.virbo.qstream.QDataSetStreamHandler;
import org.virbo.qstream.StreamException;

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
                List<String> params= getNames( cc, mon );
                for ( String s: params ) {
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, s ) );
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
    
    
    private List<String> getNames( CompletionContext cc, ProgressMonitor mon ) throws IOException, StreamException {
        
        Map params= URISplit.parseParams( cc.params );
        Object o;
        File file= DataSetURI.getFile( cc.resourceURI, mon  );
        
        QDataSetStreamHandler h= new QDataSetStreamHandler();
        h.setReadPackets(false); // don't read any records, just scan for datasets.
        org.virbo.qstream.StreamTool.readStream( Channels.newChannel(new FileInputStream(file) ), h );
            
        return h.getDataSetNames();
        
    }

    public <T> T getCapability(Class<T> clazz) {
        return null;
    }


}
