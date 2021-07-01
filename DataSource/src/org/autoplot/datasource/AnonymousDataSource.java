
package org.autoplot.datasource;

import java.util.Map;
import org.das2.qds.QDataSet;
import org.das2.util.monitor.ProgressMonitor;

/**
 * wrapper for implementations which can provide a QDataSet
 * @author jbf
 */
public abstract class AnonymousDataSource implements DataSource {
    
    public AnonymousDataSource() {
    }

    @Override
    public abstract QDataSet getDataSet(ProgressMonitor mon) throws Exception;
    
    @Override
    public boolean asynchronousLoad() {
        return true;
    }

    @Override
    public MetadataModel getMetadataModel() {
        return null;
    }

    @Override
    public Map<String, Object> getMetadata(ProgressMonitor mon) throws Exception {
        return null;
    }

    @Override
    public String getURI() {
        return "vap+anonymous:";
    }

    @Override
    public Map<String, Object> getProperties() {
        return null;
    }

    @Override
    public <T> T getCapability(Class<T> clazz) {
        return null;
    }

}
