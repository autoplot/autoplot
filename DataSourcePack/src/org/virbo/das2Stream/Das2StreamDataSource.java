/*
 * Das2StreamDataSource.java
 *
 * Created on April 2, 2007, 8:41 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.das2Stream;

import java.io.File;
import org.das2.client.DataSetStreamHandler;
import org.das2.stream.StreamException;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.stream.StreamTool;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.HashMap;
import org.virbo.dataset.QDataSet;
import org.das2.dataset.DataSetAdapter;
import org.das2.dataset.NoDataInIntervalException;
import org.das2.util.FileUtil;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSourceUtil;
import org.virbo.datasource.HtmlResponseIOException;
import org.virbo.datasource.URISplit;
import org.virbo.qstream.QDataSetStreamHandler;

/**
 *
 * @author jbf
 */
public class Das2StreamDataSource extends AbstractDataSource {

    /** Creates a new instance of Das2StreamDataSource */
    public Das2StreamDataSource(URI uri) throws IOException {
        super(uri);
    }

    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws FileNotFoundException, StreamException, IOException, org.virbo.qstream.StreamException, NoDataInIntervalException {

        InputStream in = DataSetURI.getInputStream(uri, mon);

        ReadableByteChannel channel = Channels.newChannel(in);

        URISplit split = URISplit.parse( uri );

        String ext= split.vapScheme;
        
        if ( ext.equals("vap+qds") || ext.equals("vap+qdst") ) {
            try {
                QDataSetStreamHandler h= new QDataSetStreamHandler();
                org.virbo.qstream.StreamTool.readStream(channel, h);

                QDataSet result;
                if ( params.get("arg_0")!=null ) {
                    result= h.getDataSet(params.get("arg_0"));
                } else {
                    result= h.getDataSet();
                }

                // check if we can flatten rank 2 join that comes from aggregation
                //if ( QDataSetStreamHandler.isFlattenableJoin(result) ) {
                //    result= h.flattenJoin(result);
                //}

                return result;
                
            } catch ( org.virbo.qstream.StreamException se ) {
                if ( se.toString().contains( "Expecting stream descriptor header" ) ) {
                    int i= se.toString().indexOf("beginning \n'");
                    if ( i>0 && se.toString().length()>i+12+5 ) {
                        String resp= se.toString().substring(i+12,i+12+5); //TODO: will this have problems with two-byte newlines (windows)
                        if ( DataSourceUtil.isHtmlStream(resp) ) {
                            throw new HtmlResponseIOException( "Expected QStream but got html: "+resp, DataSetURI.getWebURL(uri) );
                        } 
                    }
                } else if ( "NoDataInInterval".equals( se.getMessage() ) ) {
                    throw new NoDataInIntervalException(se.getMessage());
                } else if ( se.getCause()!=null && se.getCause() instanceof NoDataInIntervalException ) {
                    throw (NoDataInIntervalException)se.getCause();
                }
                throw se;
            }

        } else {
            try {
                HashMap<String,String> props = new HashMap<String,String>();
                props.put("file", DataSetURI.fromUri(uri) );

                DataSetStreamHandler handler = new DataSetStreamHandler(props, mon);

                System.err.println("Reading URI: "+uri);
                try {
                    StreamTool.readStream(channel, handler);
                } catch ( NullPointerException ex ) {
                    File ff= new File("/tmp/badd2s.d2s");
                    File infile= DataSetURI.getFile(uri,new NullProgressMonitor());
                    FileUtil.fileCopy(infile,ff);
                    logger.warning("bad stream written to /tmp/badd2s.d2s.  Note the data source was reading the stream directly.");
                    throw ex;
                }

                in.close();

                return DataSetAdapter.create(handler.getDataSet());
            } catch ( StreamException se ) {
                 if ( se.toString().contains( "Expecting stream descriptor header" ) ) {
                    int i= se.toString().indexOf("beginning \n'");
                    if ( i>0 && se.toString().length()>i+12+5 ) {
                        String resp= se.toString().substring(i+12,i+12+5); //TODO: will this have problems with two-byte newlines (windows)
                        if ( DataSourceUtil.isHtmlStream(resp) ) {
                            throw new HtmlResponseIOException( "Expected das2Stream but got html: "+resp, DataSetURI.getWebURL(uri) );
                        } 
                    }
                } else if ( "NoDataInInterval".equals( se.getMessage() ) ) {
                    throw new NoDataInIntervalException(se.getMessage());
                } else if ( se.getCause()!=null && se.getCause() instanceof NoDataInIntervalException ) {
                    throw (NoDataInIntervalException)se.getCause();
                }
                throw se; //TODO: check for HTML response
            }

        }

    }

}
