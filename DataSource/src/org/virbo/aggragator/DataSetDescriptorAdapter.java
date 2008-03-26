/*
 * DataSetDescriptorAdapter.java
 *
 * Created on October 25, 2007, 11:51 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.aggragator;

import edu.uiowa.physics.pw.das.DasException;
import edu.uiowa.physics.pw.das.dataset.DataSet;
import edu.uiowa.physics.pw.das.dataset.DataSetDescriptor;
import edu.uiowa.physics.pw.das.datum.Datum;
import edu.uiowa.physics.pw.das.datum.DatumRange;
import edu.uiowa.physics.pw.das.datum.Units;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.TableDataSetAdapter;
import org.virbo.dataset.VectorDataSetAdapter;

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
            org.virbo.dataset.QDataSet ds= source.getDataSet( monitor );
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
