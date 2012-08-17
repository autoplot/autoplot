/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.cefdatasource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.cefdatasource.CefReaderHeader.ParamStruct;
import org.virbo.datasource.AbstractDataSourceFactory;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;

/**
 *
 * @author jbf
 */
public class CefDataSourceFactory extends AbstractDataSourceFactory {

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new CefDataSource(uri);
    }

    private List<String> getPlottable(URI uri, ProgressMonitor mon ) throws IOException {
        File f = DataSetURI.getFile(uri, mon );
        ReadableByteChannel in = Channels.newChannel(new FileInputStream(f));
        CefReaderHeader reader = new CefReaderHeader();
        Cef cef = reader.read(in);
        Map<String, ParamStruct> h = cef.parameters;
        List<String> result = new ArrayList<String>();
        result.addAll(h.keySet());
        return result;
    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc,org.das2.util.monitor.ProgressMonitor mon) {
        List<CompletionContext> result = new ArrayList<CompletionContext>();

        if (cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {
            try {
                String surl = CompletionContext.get(CompletionContext.CONTEXT_FILE, cc);
                List<String> plottable = getPlottable( DataSetURI.getResourceURI(surl), mon );
                for (String s : plottable) {
                    result.add(new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, s, this, "arg_0" ,null ,null, true ));
                }

            } catch (MalformedURLException ex) {
                throw new RuntimeException(ex);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

        }
        return result;
    }
    
    @Override
    public boolean reject( String surl, List<String> problems,ProgressMonitor mon ) {
        return ! surl.contains("?") || surl.indexOf("?")==surl.length()-1;
    }


    
    
}
