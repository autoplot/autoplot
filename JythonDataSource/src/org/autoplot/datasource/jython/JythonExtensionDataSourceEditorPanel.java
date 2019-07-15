
package org.autoplot.datasource.jython;

import java.awt.Window;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceEditorPanel;
import org.autoplot.datasource.URISplit;
import org.das2.util.monitor.ProgressMonitor;

/**
 *
 * @author jbf
 */
public class JythonExtensionDataSourceEditorPanel implements DataSourceEditorPanel {

    JythonEditorPanel edit;
    String uri;
    
    @Override
    public boolean reject(String uri) throws Exception {
        edit= new JythonEditorPanel();
        String jydsUri= JythonExtensionDataSourceFactory.getJydsUri( DataSetURI.getURI(uri) );
        return edit.reject(jydsUri);
    }

    @Override
    public boolean prepare(String uri, Window parent, ProgressMonitor mon) throws Exception {
        String jydsUri= JythonExtensionDataSourceFactory.getJydsUri( DataSetURI.getURI(uri) );
        return edit.prepare(jydsUri, parent, mon);
    }

    @Override
    public void setURI(String uri) {
        try {
            this.uri= uri;
            String jydsUri= JythonExtensionDataSourceFactory.getJydsUri( DataSetURI.getURI(uri) );
            edit.setURI(jydsUri);
        } catch ( URISyntaxException ex ) { // this would have happened by now, in reject.
            throw new IllegalArgumentException(ex);
        }
    }

    @Override
    public void markProblems(List<String> problems) {
        
    }

    @Override
    public JPanel getPanel() {
        return edit.getPanel();
    }

    @Override
    public String getURI() {
        String jydsUri= edit.getURI();
        URISplit split= URISplit.parse(jydsUri);
        Map<String,String> params= URISplit.parseParams(split.params);
        params.remove(JythonDataSource.PARAM_SCRIPT);
        split.params= URISplit.formatParams(params);
        URISplit mySplit= URISplit.parse(uri);
        split.vapScheme= mySplit.vapScheme;
        return URISplit.format(split);
    }
    
}
