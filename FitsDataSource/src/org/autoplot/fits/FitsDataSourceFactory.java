/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.fits;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.das2.util.monitor.ProgressMonitor;
import org.eso.fits.FitsException;
import org.eso.fits.FitsFile;
import org.eso.fits.FitsHDUnit;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;

/**
 *
 * @author jbf
 */
public class FitsDataSourceFactory extends AbstractDataSourceFactory {

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new FitsDataSource(uri);
    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, org.das2.util.monitor.ProgressMonitor mon) throws FitsException {
        List<CompletionContext> result = new ArrayList<CompletionContext>();

        if (cc.context.equals(CompletionContext.CONTEXT_PARAMETER_NAME)) {
            try {
                String surl = CompletionContext.get(CompletionContext.CONTEXT_FILE, cc);
                Set<String> plottable = getPlottable( DataSetURI.toUri(surl), mon).keySet();
                for (String s : plottable) {
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, s, this, "arg_0"));
                }

            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

        }
        return result;
    }

    protected static Map<String,Integer> getPlottable(URI uri, ProgressMonitor mon) throws IOException, FitsException {
        File f = DataSetURI.getFile(uri, mon);
        FitsFile file = new FitsFile(f);

        Map<String,Integer> result= new LinkedHashMap<String,Integer>();
        
        int nhdu= file.getNoHDUnits();
        for ( int i=0; i<nhdu; i++ ) {
            FitsHDUnit hdu= file.getHDUnit(i);
            result.put( hdu.getHeader().getName(), i );
        }
        return result;
    }

    @Override
    public String getDescription() {
        return "NASA Fits files";
    }
    
    
}
