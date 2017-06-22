
package org.autoplot.imagedatasource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;

/**
 * Factory for ImageDataSource, which read in images into datasets.
 * @author jbf
 */
public class ImageDataSourceFactory implements DataSourceFactory {

    @Override
    public DataSource getDataSource(URI uri) throws Exception { 
        return new ImageDataSource(uri);
    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {

        List<CompletionContext> result = new ArrayList<>();

        if (cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "channel=", "channel to extract"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "rotate=", "rotate image clockwise in degrees.  Image size is not affected"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "blur=", "apply boxcar blur square kernel"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "fog=", "apply overlapping white translucent fog percent opaque"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "xaxis=", "apply a linear transform to label each column of the image [valmin,pixmin,valmax,pixmax]"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "yaxis=", "apply a linear transform to label each row of the image [valmin,pixmin,valmax,pixmax]"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "plotInfo=", "read the rich png metadata to get axes.  http://autoplot.org/developer.richPng"));
        } else if (cc.context == CompletionContext.CONTEXT_PARAMETER_VALUE) {
            String paramName = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            switch (paramName) {
                case "channel":
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "red" ) );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "green" ) );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "blue" ) );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "alpha" ) );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "greyscale" ) );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "hue" ) );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "saturation" ) );
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "value" ) );
                    break;
                case "rotate":
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "0", "rotate image clockwise in degrees" ) );
                    break;
                case "blur":
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "5", "apply boxcar blur square kernel"));
                    break;
                case "fog":
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "100", "apply fog with this opacity percent, based on 0,0 color"));
                    break;
                case "xaxis":
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "[valmin,pixmin,valmax,pixmax]", "add labels for each bin"));
                    break;
                case "yaxis":
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "[valmin,pixmin,valmax,pixmax]", "add labels for each bin"));
                    break;
                case "plotInfo":
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "0", "read the rich png metadata to get axes") );
                    break;
                default:
                    break;
            }
        }

        return result;

    }

    @Override
    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        return false;
    }

    @Override
    public <T> T getCapability(Class<T> clazz) {
        return null;
    }

    @Override
    public boolean supportsDiscovery() {
        return false;
    }
}
