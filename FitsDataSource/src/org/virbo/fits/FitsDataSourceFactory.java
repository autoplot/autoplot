/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.fits;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.das2.util.monitor.ProgressMonitor;
import org.eso.fits.FitsException;
import org.eso.fits.FitsFile;
import org.eso.fits.FitsHDUnit;
import org.virbo.datasource.AbstractDataSourceFactory;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;

/**
 *
 * @author jbf
 */
public class FitsDataSourceFactory extends AbstractDataSourceFactory {

    @Override
    public DataSource getDataSource(URL url) throws Exception {
        return new FitsDataSource(url);
    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, org.das2.util.monitor.ProgressMonitor mon) throws FitsException {
        List<CompletionContext> result = new ArrayList<CompletionContext>();

        if (cc.context.equals(CompletionContext.CONTEXT_PARAMETER_NAME)) {
            try {
                String surl = CompletionContext.get(CompletionContext.CONTEXT_FILE, cc);
                Set<String> plottable = getPlottable(new URL(surl), mon).keySet();
                for (String s : plottable) {
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, s, this, "arg_0"));
                }

            } catch (MalformedURLException ex) {
                throw new RuntimeException(ex);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

        }
        return result;
    }

    protected static Map<String,Integer> getPlottable(URL url, ProgressMonitor mon) throws IOException, FitsException {
        File f = DataSetURL.getFile(url, mon);
        FitsFile file = new FitsFile(f);

        Map<String,Integer> result= new LinkedHashMap<String,Integer>();
        
        int nhdu= file.getNoHDUnits();
        for ( int i=0; i<nhdu; i++ ) {
            FitsHDUnit hdu= file.getHDUnit(i);
            result.put( hdu.getHeader().getName(), i );
        }
        return result;
    }
}
