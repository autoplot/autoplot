
package org.autoplot.datasource.jython;

import java.net.URI;
import java.util.Map;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceRegistry;
import org.autoplot.datasource.URISplit;
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
    
        String script= JythonExtensionDataSourceFactory.getScriptForResource(uri);
        
        URISplit split= URISplit.parse(uri);

        JythonDataSourceFactory jdsf= (JythonDataSourceFactory)DataSourceRegistry.getInstance().getSource(".jyds");
        
        Map<String,String> params= URISplit.parseParams(split.params);
        if ( params.containsKey("script") ) throw new IllegalArgumentException("URI cannot contain keyword 'script'");
        params.put( JythonDataSource.PARAM_SCRIPT, script );
        
        split.params= URISplit.formatParams(params);
        String thisUri= URISplit.format(split);
        
        JythonDataSource jyds= (JythonDataSource) jdsf.getDataSource( DataSetURI.getURI(thisUri) );
        return jyds.getDataSet(mon);
        
    }
    
}
