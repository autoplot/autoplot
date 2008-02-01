/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.cefdatasource;

import edu.uiowa.physics.pw.das.util.NullProgressMonitor;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.virbo.cefdatasource.CefReaderHeader.ParamStruct;
import org.virbo.datasource.AbstractDataSourceFactory;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;

/**
 *
 * @author jbf
 */
public class CefDataSourceFactory extends AbstractDataSourceFactory {

    @Override
    public DataSource getDataSource(URL url) throws Exception {
        return new CefDataSource(url);
    }

    private List<String> getPlottable(URL url) throws IOException {
        File f = DataSetURL.getFile(url, new NullProgressMonitor() );
        ReadableByteChannel in = Channels.newChannel(new FileInputStream(f));
        CefReaderHeader reader = new CefReaderHeader();
        Cef cef = reader.read(in);
        Map<String, ParamStruct> h = cef.parameters;
        List<String> result = new ArrayList<String>();
        result.addAll(h.keySet());
        return result;
    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc) {
        List<CompletionContext> result = new ArrayList<CompletionContext>();

        if (cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {
            try {
                String surl = CompletionContext.get(CompletionContext.CONTEXT_FILE, cc);
                List<String> plottable = getPlottable(new URL(surl));
                for (String s : plottable) {
                    result.add(new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, s, this, "arg_0" ));
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
    public List<String> extensions() {
        return Collections.singletonList("cef");
    }

    @Override
    public List<String> mimeTypes() {
        return Collections.emptyList();
    }
    
    @Override
    public boolean reject( String surl ) {
        return ! surl.contains("?") || surl.indexOf("?")==surl.length()-1;
    }
    
}
