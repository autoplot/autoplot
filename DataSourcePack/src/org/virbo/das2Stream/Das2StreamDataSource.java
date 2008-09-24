/*
 * Das2StreamDataSource.java
 *
 * Created on April 2, 2007, 8:41 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.das2Stream;

import org.das2.client.DataSetStreamHandler;
import org.das2.stream.StreamException;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.StreamTool;
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
import org.virbo.qstream.QDataSetStreamHandler;

/**
 *
 * @author jbf
 */
public class Das2StreamDataSource extends AbstractDataSource {

    /** Creates a new instance of Das2StreamDataSource */
    public Das2StreamDataSource(URL url) throws IOException {
        super(url);
    }

    public QDataSet getDataSet(ProgressMonitor mon) throws FileNotFoundException, StreamException, IOException, org.virbo.qstream.StreamException {

        InputStream in = DataSetURL.getInputStream(url, mon);

        ReadableByteChannel channel = Channels.newChannel(in);

        DataSetURL.URLSplit split = DataSetURL.parse(url.toString());
        
        if (split.ext.equals(".qds")) {
            QDataSetStreamHandler h= new QDataSetStreamHandler();
            org.virbo.qstream.StreamTool.readStream(channel, h);
            
            if ( params.get("arg_0")!=null ) {
                return h.getDataSet(params.get("arg_0"));
            } else {
                return h.getDataSet();
            }
            
        } else {

            HashMap props = new HashMap();
            props.put("file", url.toString());

            DataSetStreamHandler handler = new DataSetStreamHandler(props, mon);

            StreamTool.readStream(channel, handler);

            in.close();

            return DataSetAdapter.create(handler.getDataSet());
        }

    }

    public boolean asynchronousLoad() {
        return true;
    }

    public static DataSourceFactory getFactory() {
        return new DataSourceFactory() {

            public DataSource getDataSource(URL url) throws IOException {
                return new Das2StreamDataSource(url);
            }

            public List<CompletionContext> getCompletions(CompletionContext cc, org.das2.util.monitor.ProgressMonitor mon) {
                return Collections.emptyList();
            }

            public MetadataModel getMetadataModel(URL url) {
                return MetadataModel.createNullModel();
            }

            public boolean reject(String surl, ProgressMonitor mon) {
                return false;
            }

            public String urlForServer(String surl) {
                return surl; // TODO
            }
        };
    }
}
