
package org.autoplot.datasource.jython;

import java.net.URI;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceRegistry;
import org.das2.qds.QDataSet;
import org.das2.util.monitor.ProgressMonitor;

/**
 * Provide mechanism where data source can be completely defined in a .jyds
 * script, but the file extension triggers the script.
 * @author jbf
 */
public class JythonExtensionDataSource extends AbstractDataSource {

    public JythonExtensionDataSource(URI uri) {
        super(uri);
    }

    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {
    
        String jydsUri= JythonExtensionDataSourceFactory.getJydsUri(uri);
        JythonDataSourceFactory jdsf= (JythonDataSourceFactory)DataSourceRegistry.getInstance().getSource(".jyds");
        
        JythonDataSource jyds= (JythonDataSource) jdsf.getDataSource( DataSetURI.getURI(jydsUri) );
        return jyds.getDataSet(mon);
        
    }
    
}
