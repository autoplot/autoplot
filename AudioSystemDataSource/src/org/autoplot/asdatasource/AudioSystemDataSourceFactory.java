/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.asdatasource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.URISplit;

/**
 * Read data directly from the desktop audio system, to create a useful source
 * of data for demonstration purposes.
 * @author jbf
 */
public class AudioSystemDataSourceFactory extends AbstractDataSourceFactory {

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new AudioSystemDataSource(uri);
    }

    @Override
    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        URISplit split= URISplit.parse(surl);
        Map<String,String> params= URISplit.parseParams(split.params);
        if ( params.get("len")==null ) {
            return true;
        }
        return super.reject(surl, problems, mon); //To change body of generated methods, choose Tools | Templates.
    }

    
    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            List<CompletionContext> result= new ArrayList<>();
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "len=", "sample length in seconds" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "spec=", "make spectrogram using FFT" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "rate=", "sampling rate in samples per second" ) );
            return result;
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            String paramName= CompletionContext.get( CompletionContext.CONTEXT_PARAMETER_NAME, cc );
            if ( paramName.equals("spec") ) {
                List<CompletionContext> result= new ArrayList<>();
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "128") );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "256") );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "512") );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "1024") );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "2048") );
                return result;
            } else if (paramName.equals("len")) {
                List<CompletionContext> result= new ArrayList<>();
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "1.0") );
                return result;
            } else if (paramName.equals("rate")) {
                List<CompletionContext> result= new ArrayList<>();
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "8000") );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "16000") );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "32000") );
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "64000") );
                return result;
            } else {
                return Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }

    }

    @Override
    public boolean isFileResource() {
        return false;
    }


}
