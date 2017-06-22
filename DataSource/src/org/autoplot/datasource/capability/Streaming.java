
package org.autoplot.datasource.capability;

import java.util.Iterator;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.QDataSet;

/**
 * allows the records to be read in as they are available.  Note
 * when the Iterator hasNext method may block until more records are 
 * available.
 * @author jbf
 */
public interface Streaming {
    
    /**
     * provide an iterator that will provide access to each slice of the data
     * set.  It should be understood that each record returned should be
     * join-able to the previous records.  
     * @param mon the monitor.  assert monitor.finished()==(!result.hasNext())
     * @return a dataset iterator.
     * @throws Exception 
     */
    Iterator<QDataSet> streamDataSet( ProgressMonitor mon ) throws Exception;
}
