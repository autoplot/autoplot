/* This Java package, org.autoplot.das2Stream is part of the Autoplot application
 *
 * Copyright (C) 2007 Jeremy Faden <faden@cottagesystems.com>
 * 
 * Autoplot is free software; you can redistribute it and/or modify it under the 
 * terms of the GNU General Public License version 2 as published by the Free
 * Software Foundation, with the additional Classpath exception below.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License version 2
 * along with this library; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 * Classpath Exception
 * -------------------
 * The copyright holders designate this particular java package as subject to the
 * "Classpath" exception as provided here.
 *
 * Linking this package statically or dynamically with other modules is making a
 * combined work based on this package.  Thus, the terms and conditions of the GNU
 * General Public License cover the whole combination.
 *
 * As a special exception, the copyright holders of this package give you
 * permission to link this package with independent modules to produce an
 * application, regardless of the license terms of these independent modules, and
 * to copy and distribute the resulting application under terms of your choice,
 * provided that you also meet, for each independent module, the terms and
 * conditions of the license of that module.  An independent module is a module
 * which is not derived from or based on this package.  If you modify this package,
 * you may extend this exception to your version of the package, but you are not
 * obligated to do so.  If you do not wish to do so, delete this exception
 * statement from your version.
 */

package org.autoplot.das2Stream;

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
import java.util.Map;
import java.util.logging.Logger;
import org.das2.dataset.DataSet;
import org.das2.qds.QDataSet;
import org.das2.dataset.DataSetAdapter;
import org.das2.dataset.NoDataInIntervalException;
import org.das2.util.FileUtil;
import org.das2.util.monitor.NullProgressMonitor;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.HtmlResponseIOException;
import org.autoplot.datasource.URISplit;
import org.das2.qds.ops.Ops;
import org.das2.qstream.QDataSetStreamHandler;

/**
 *
 * @author jbf,cwp
 */
public class Das2StreamDataSource extends AbstractDataSource {
    
    private static final Logger logger= Logger.getLogger("apdss.d2s");
	 
    /** 
     * Creates a new instance of Das2StreamDataSource
     * @param uri the URI.
     * @throws java.io.IOException 
     */
    public Das2StreamDataSource(URI uri) throws IOException {
        super(uri);
    }

    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws FileNotFoundException, StreamException, IOException, org.das2.qstream.StreamException, NoDataInIntervalException {

        InputStream in = DataSetURI.getInputStream(uri, mon);

        ReadableByteChannel channel = Channels.newChannel(in);

        URISplit split = URISplit.parse( uri );

        String ext= split.vapScheme;
        
        boolean useOldD2sParser= "T".equals( getParam( "useOldD2sParser", "T") );
        
        if ( ext.equals("vap+qds") || ext.equals("vap+qdst") ) {
            try {
                QDataSetStreamHandler h= new QDataSetStreamHandler();
                org.das2.qstream.StreamTool.readStream(channel, h);

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
                
            } catch ( org.das2.qstream.StreamException se ) {
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
        } else if ( useOldD2sParser ) {
            
            HashMap<String,String> props = new HashMap<>();
            props.put("file", DataSetURI.fromUri(uri) );

            DataSetStreamHandler handler = new DataSetStreamHandler(props, mon);
            try {
                StreamTool.readStream(channel, handler);
            } catch ( NullPointerException ex ) {
                if ( "Linux".equals(System.getProperty("os.name")) ) {                        
                    File ff= new File("/tmp/badd2s.d2s");
                    File infile= DataSetURI.getFile(uri,new NullProgressMonitor());
                    FileUtil.fileCopy(infile,ff);
                    logger.warning("bad stream written to /tmp/badd2s.d2s.  Note the data source was reading the stream directly.");
                }
                throw ex;
            }

            in.close();

            DataSet r= handler.getDataSet();
            if ( r!=null ) {
                return DataSetAdapter.create(r);
            } else {
                return null;
            }
                
        } else {
            try {
                
                org.das2.client.QDataSetStreamHandler handler = new org.das2.client.QDataSetStreamHandler();
                
                try {
                    StreamTool.readStream(channel, handler);
                } catch ( NullPointerException ex ) {
                    if ( "Linux".equals(System.getProperty("os.name")) ) {                        
                        File ff= new File("/tmp/badd2s.d2s");
                        File infile= DataSetURI.getFile(uri,new NullProgressMonitor());
                        FileUtil.fileCopy(infile,ff);
                        logger.warning("bad stream written to /tmp/badd2s.d2s.  Note the data source was reading the stream directly.");
                    }
                    throw ex;
                }

                in.close();

                QDataSet r= handler.getDataSet();
                Map<String,Object> userProps= new HashMap<>();
                userProps.put( "file", uri );
                r= Ops.putProperty( r, QDataSet.USER_PROPERTIES, userProps );
                return r;
                
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
