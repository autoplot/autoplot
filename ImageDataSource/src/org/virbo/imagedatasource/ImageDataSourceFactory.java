/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.imagedatasource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;

/**
 * Factory for ImageDataSource, which read in images into datasets.
 * @author jbf
 */
public class ImageDataSourceFactory implements DataSourceFactory {

    public DataSource getDataSource(URI uri) throws Exception {
        return new ImageDataSource(uri);
    }

    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {

        List<CompletionContext> result = new ArrayList<CompletionContext>();

        if (cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "channel=", "channel to extract"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "rotate=", "rotate image clockwise in degrees.  Image size is not affected"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "blur=", "apply boxcar blur square kernel"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "fog=", "apply overlapping white translucent fog percent opaque"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "xaxis=", "apply a linear transform to label each column of the image"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "yaxis=", "apply a linear transform to label each column of the image"));
        } else if (cc.context == CompletionContext.CONTEXT_PARAMETER_VALUE) {
            String paramName = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            if (paramName.equals("channel")) {
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "red" ) );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "green" ) );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "blue" ) );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "greyscale" ) );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "hue" ) );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "saturation" ) );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "value" ) );
            } else if ( paramName.equals("rotate") ) {
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "0", "rotate image clockwise in degrees" ) );
            } else if ( paramName.equals("blur") ) {
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "5", "apply boxcar blur square kernel"));
            } else if ( paramName.equals("fog") ) {
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "100", "apply fog with this opacity percent, based on 0,0 color"));
            } else if ( paramName.equals("xaxis") ) {
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "[valmin,pixmin,valmax,pixmax]", "add labels for each bin"));
            } else if ( paramName.equals("yaxis") ) {
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "[valmin,pixmin,valmax,pixmax]", "add labels for each bin"));
            }
        }

        return result;

    }

    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        return false;
    }

    public <T> T getCapability(Class<T> clazz) {
        return null;
    }

    public boolean supportsDiscovery() {
        return false;
    }
}
