/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.imagedatasource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;

/**
 *
 * @author jbf
 */
public class ImageDataSourceFactory implements DataSourceFactory {

    public DataSource getDataSource(URI uri) throws Exception {
        return new ImageDataSource(uri);
    }
    Map<String, List<String>> datasetsList = null;

    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        //http://www-pw.physics.uiowa.edu/das/das2Server
        //?dataset=das2_1/voyager1/pws/sa-4s-pf.new
        //&start_time=2004-01-01&end_time=2004-01-06&server=dataset&ascii=1

        List<CompletionContext> result = new ArrayList<CompletionContext>();

        if (cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "channel=", "channel to extract"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "rotate=", "rotate image clockwise in degrees.  Image size is not affected"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "blur=", "apply boxcar blur square kernel"));
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
            }
        }

        return result;

    }

    public boolean reject(String surl, ProgressMonitor mon) {
        return false;
    }

    public <T> T getCapability(Class<T> clazz) {
        return null;
    }
}
