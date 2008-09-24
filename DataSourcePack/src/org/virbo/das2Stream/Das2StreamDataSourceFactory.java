/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.das2Stream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.MetadataModel;
import org.virbo.dsutil.AsciiParser;
import org.virbo.qstream.QDataSetStreamHandler;
import org.virbo.qstream.StreamException;

/**
 *
 * @author jbf
 */
public class Das2StreamDataSourceFactory implements DataSourceFactory {

    public DataSource getDataSource(URL url) throws IOException {
        return new Das2StreamDataSource(url);
    }

    public List<CompletionContext> getCompletions(CompletionContext cc,org.das2.util.monitor.ProgressMonitor mon) throws IOException, StreamException {
        List<CompletionContext> result= new ArrayList<CompletionContext>();
        if ( cc.context==cc.CONTEXT_PARAMETER_NAME ) {
            if ( cc.resource.toString().endsWith(".qds") ) {
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

    public boolean reject(String surl, ProgressMonitor mon) {
        return false;
    }
    
    
    private List<String> getNames( CompletionContext cc, ProgressMonitor mon ) throws IOException, StreamException {
        
        Map params= DataSetURL.parseParams( cc.params );
        Object o;
        File file= DataSetURL.getFile( cc.resource, mon  );
        
        QDataSetStreamHandler h= new QDataSetStreamHandler();
        h.setReadPackets(false); // don't read any records, just scan for datasets.
        org.virbo.qstream.StreamTool.readStream( Channels.newChannel(new FileInputStream(file) ), h );
            
        return h.getDataSetNames();
        
    }


}
