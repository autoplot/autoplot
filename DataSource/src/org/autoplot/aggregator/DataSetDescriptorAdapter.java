/*
 * DataSetDescriptorAdapter.java
 *
 * Created on October 25, 2007, 11:51 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.autoplot.aggregator;

import org.das2.DasException;
import org.das2.dataset.DataSet;
import org.das2.dataset.DataSetDescriptor;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.dataset.TableDataSetAdapter;
import org.das2.dataset.VectorDataSetAdapter;

/**
 * wrap aggregating DataSource into das2 DataSetDescriptor.
 * @author jbf
 */
public class DataSetDescriptorAdapter extends DataSetDescriptor {
    
    AggregatingDataSource source;
    
    /** Creates a new instance of DataSetDescriptorAdapter */
    public DataSetDescriptorAdapter( AggregatingDataSource source ) {
        this.source= source;
    }
    
    protected DataSet getDataSetImpl(Datum start, Datum end, Datum resolution, ProgressMonitor monitor) throws DasException {
        source.setViewRange( new DatumRange( start, end ) );
        try {
            org.das2.qds.QDataSet ds= source.getDataSet( monitor );
            if ( ds.rank()==1 ) {
                return VectorDataSetAdapter.create(ds);
            } else {
                return TableDataSetAdapter.create(ds);
            }
        } catch ( Exception e ) {
            throw new DasException(e.getMessage());
        }
    }
    
    public Units getXUnits() {
        return Units.us2000;
    }
    
}
