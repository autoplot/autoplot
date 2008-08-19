/*
 * Das2StreamDataSource.java
 *
 * Created on April 2, 2007, 8:41 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.das2Stream;

import edu.uiowa.physics.pw.das.client.DataSetStreamHandler;
import edu.uiowa.physics.pw.das.stream.StreamException;
import org.das2.util.monitor.ProgressMonitor;
import edu.uiowa.physics.pw.das.util.StreamTool;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.DataSetAdapter;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.MetadataModel;

/**
 *
 * @author jbf
 */
public class Das2StreamDataSource extends AbstractDataSource {
    
    /** Creates a new instance of Das2StreamDataSource */
    public Das2StreamDataSource( URL url ) throws IOException {
        super( url );
    }
    
    
    public QDataSet getDataSet(ProgressMonitor mon) throws FileNotFoundException, StreamException, IOException {
        
        InputStream in = DataSetURL.getInputStream( url, mon );
        
        ReadableByteChannel channel = Channels.newChannel(in);
        
        HashMap props= new HashMap();
        props.put( "file", url.toString() );
        
        DataSetStreamHandler handler = new DataSetStreamHandler( props, mon );
        
        StreamTool.readStream(channel, handler);
        
        in.close();
        
        return DataSetAdapter.create( handler.getDataSet() );
        
    }
    
    public boolean asynchronousLoad() {
        return true;
    }
    
    public static DataSourceFactory getFactory() {
        return new DataSourceFactory() {
            public DataSource getDataSource(URL url) throws IOException {
                return new Das2StreamDataSource( url );
            }
            public List<CompletionContext> getCompletions( CompletionContext cc ,org.das2.util.monitor.ProgressMonitor mon ) {
                return Collections.emptyList();
            }
            public MetadataModel getMetadataModel( URL url ) {
                return MetadataModel.createNullModel();
            }
            public boolean reject( String surl ,ProgressMonitor mon ) {
                return false;
            }
            public String urlForServer( String surl ) {
                return surl; // TODO
            }
        };
    }
    
}
