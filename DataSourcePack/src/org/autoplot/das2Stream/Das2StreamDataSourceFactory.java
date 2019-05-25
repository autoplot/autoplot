/* This Java package, org.autoplot.das2Stream is part of the Autoplot application
 *
 * Copyright (C) 2018 Jeremy Faden <faden@cottagesystems.com>
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
            } else {
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME,"useOldD2sParser=") );
            }
        } else if ( cc.context==cc.CONTEXT_PARAMETER_VALUE ) {
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE,"T") );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE,"F") );
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
