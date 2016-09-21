
package org.virbo.datasource;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.DatumRange;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.aggregator.AggregatingDataSourceFactory;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.capability.Streaming;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.dsops.Ops;

/**
 * Introduce class to hold code for iterating through any dataset.
 * @author jbf
 */
public class RecordIterator implements Iterator<QDataSet>  {

    int index;
    int lastIndex;
    QDataSet src;
    
    private static final Logger logger= Logger.getLogger("apdss.recordIterator");
    
    /**
     * this will change as serial DataSources are available.
     * @param suri
     * @param timeRange
     * @param monitor
     * @return
     * @throws URISyntaxException
     * @throws Exception 
     */
    private QDataSet getDataSet( String suri, DatumRange timeRange, ProgressMonitor monitor ) throws URISyntaxException, Exception {
        logger.log( Level.FINE, "getDataSet(\"{0}\",DatumRangeUtil.parseTimeRange({1}),monitor)", new Object[]{suri, timeRange} );
        URI uri = DataSetURI.getURI(suri);
        DataSourceFactory factory = DataSetURI.getDataSourceFactory(uri, new NullProgressMonitor());
        DataSource result = factory.getDataSource( uri );
        if (monitor == null) {
            monitor = new NullProgressMonitor();
        }

        Streaming streaming= result.getCapability( Streaming.class );
        if ( streaming!=null ) {
            logger.fine("this data could be streamed");
        }
        
        TimeSeriesBrowse tsb= result.getCapability( TimeSeriesBrowse.class );
        if ( tsb!=null ) {
            tsb.setTimeRange( timeRange );
        } else {
            logger.fine("TimeSeriesBrowse capability not found, simply returning dataset.");
        }
        QDataSet rds= result.getDataSet(monitor);
        
        if ( rds==null && factory instanceof AggregatingDataSourceFactory ) {
            logger.info("strange condition where occasional null is returned because of reference caching.  This needs to be studied more.");
            monitor = new NullProgressMonitor();
            monitor.setLabel("strange condition where occasional null...");
            rds= result.getDataSet(monitor);  //TODO nasty kludge, just try reading again...
        }
        
        return rds;        
    }
    
    public RecordIterator( String uri, DatumRange dr ) {
        try {
            QDataSet ds= getDataSet( uri, dr, new NullProgressMonitor() );
            QDataSet dep0= (QDataSet) ds.property(QDataSet.DEPEND_0);
            if ( dep0!=null ) {
                if ( ds.rank()==1 ) {
                    this.src= Ops.bundle( dep0, ds );
                } else if ( ds.rank()==2 ) {
                    this.src= Ops.bundle( dep0, Ops.unbundle(ds,0) );
                    for ( int i=1; i<ds.length(0); i++ ) {
                        this.src= Ops.bundle( this.src, Ops.unbundle(ds,i) );
                    }
                }
            } else {
                this.src= ds;
            }
            constrainDepend0(dr);
        } catch (Exception ex) {
            Logger.getLogger(RecordIterator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
        
    /**
     * limit the data returned such that only data within the datum range
     * provided are returned.
     * @param dr 
     */
    public final void constrainDepend0( DatumRange dr ) {
        index= 0;
        lastIndex= src.length();
        QDataSet dep0= Ops.slice1( this.src, 0 );
        QDataSet findeces= Ops.findex( dep0, dr );
        this.index= (int)Math.ceil( findeces.value(0) );
        this.lastIndex= (int)Math.ceil( findeces.value(1) );
        this.index= Math.max(0,this.index);
        this.lastIndex= Math.min(src.length(),this.lastIndex);
    }
    
    /**
     * get a subset or rearrange the fields of each record.
     * @param sort 
     */
    public final void resortFields( int[] sort ) {
        src= DataSetOps.applyIndex( src, 1, Ops.dataset(sort), true );
    }
    
    @Override
    public boolean hasNext() {
        return this.index < this.lastIndex;
    }

    @Override
    public QDataSet next() {
        return src.slice(index++);
    }
    
}
