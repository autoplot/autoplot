
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
import org.virbo.dataset.BundleDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
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
    int lastIndex=-1; // -1 means we don't know, no constraint.
    QDataSet src=null;
    
    Iterator<QDataSet> streamingIterator=null;
    QDataSet sortDataSet=null;
    
    int recordCount=0;
    
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
        if ( factory==null ) {
            throw new IllegalArgumentException("no data source factory found for URI: "+ uri );
        }
        
        TimeSeriesBrowse tsb= factory.getCapability(TimeSeriesBrowse.class);   // see if we can allow for URIs without timeranges.
        if ( tsb!=null ) {
            tsb.setURI(suri);
            tsb.setTimeRange( timeRange ); 
            uri= new URI( tsb.getURI() );
        }
        
        DataSource result = factory.getDataSource( uri );
        if (monitor == null) {
            monitor = new NullProgressMonitor();
        }

        Streaming streaming= result.getCapability( Streaming.class );
        if ( streaming!=null ) {
            logger.fine("this data could be streamed");
        }
        
        tsb= result.getCapability( TimeSeriesBrowse.class );
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
    
    /**
     * create a new RecordIterator for the given URI and time range.
     * @param suri the data URI
     * @param timeRange the time range
     * @throws Exception if the data read throws an exception.
     */
    public RecordIterator( String suri, DatumRange timeRange ) throws Exception {
        this( suri, timeRange, true );
    }
    
    /**
     * create a new RecordIterator for the given URI and time range.
     * @param suri the data URI
     * @param timeRange the time range
     * @param allowStream if false, then don't use the streaming capability, even when it is available.
     * @throws Exception if the data read throws an exception.
     */
    public RecordIterator( String suri, DatumRange timeRange, boolean allowStream ) throws Exception {
        
        URI uri = DataSetURI.getURI(suri);
        DataSourceFactory factory = DataSetURI.getDataSourceFactory(uri, new NullProgressMonitor());
        
        if ( factory==null ) {
            throw new IllegalArgumentException("no data source factory found for URI: "+ uri );
        }
        
        TimeSeriesBrowse tsb= factory.getCapability(TimeSeriesBrowse.class);   // see if we can allow for URIs without timeranges.
        if ( tsb!=null ) {
            tsb.setURI(suri);
            tsb.setTimeRange( timeRange ); 
            uri= new URI( tsb.getURI() );
        }
        
        DataSource result = factory.getDataSource( uri );
    
        Streaming streaming = result.getCapability( Streaming.class );
        
        if ( streaming!=null && allowStream ) {
            streamingIterator= streaming.streamDataSet( new NullProgressMonitor() );
                    
        } else {
            
            QDataSet ds;
            try {
                ds= getDataSet( uri.toString(), timeRange, new NullProgressMonitor() );
            } catch ( Exception ex ) {
                throw ex; // breakpoint here
            }
            if ( ds==null ) {
                this.index= 0;
                this.lastIndex=0;
                return;
            }
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
            constrainDepend0(timeRange);
            
        }
    }
        
    /**
     * limit the data returned such that only data within the datum range
     * provided are returned.
     * @param dr the timerange.
     * @throws IllegalArgumentException if the data has a depend0 which is not monotonic.
     */
    public final void constrainDepend0( DatumRange dr ) {
        if ( this.src==null ) {
            return;
        }
        if ( this.src.length()==0 ) {
            return;
        }
        index= 0;
        lastIndex= src.length();
        QDataSet dep0= Ops.slice1( this.src, 0 );
        if ( DataSetUtil.isMonotonic(dep0) ) {
            QDataSet findeces= Ops.findex( dep0, dr );
            this.index= (int)Math.ceil( findeces.value(0) );
            this.lastIndex= (int)Math.ceil( findeces.value(1) );
            this.index= Math.max(0,this.index);
            this.lastIndex= Math.min(src.length(),this.lastIndex);
        } else {
            throw new IllegalArgumentException("data dep0 is not monotonic");
        }
    }
    
    /**
     * get a subset or rearrange the fields of each record.
     * @param sort 
     */
    public final void resortFields( int[] sort ) {
        if ( this.src!=null ) {
            src= DataSetOps.applyIndex( src, 1, Ops.dataset(sort), true );
        } else if ( this.streamingIterator!=null ) {
            this.sortDataSet= Ops.dataset(sort);
        }
    }
    
    @Override
    public boolean hasNext() {
        if ( this.streamingIterator!=null ) {
            if ( this.index>0 ) {
                logger.finer("skipping "+this.index+" records");
                for ( int i=0; i<this.index && this.streamingIterator.hasNext() ; i++ ) {
                    streamingIterator.next();
                }
                this.lastIndex -= this.index;
                this.index= 0;
            }
            return ( ( this.lastIndex<0 || recordCount<this.lastIndex ) && this.streamingIterator.hasNext() );
            
        } else {
            return this.index < this.lastIndex;
        }
    }

    @Override
    public QDataSet next() {
        if ( this.streamingIterator!=null ) {
            if ( this.index>0 ) {
                for ( int i=0; i<this.index && this.streamingIterator.hasNext() ; i++ ) {
                    streamingIterator.next();
                }
            }
            QDataSet nextRecord= streamingIterator.next();
            QDataSet dep0= (QDataSet) nextRecord.property(QDataSet.CONTEXT_0);
            if ( dep0!=null ) {
                switch (nextRecord.rank()) {
                    case 0:
                        nextRecord= Ops.bundle( dep0, nextRecord );
                        break;
                    case 1:
                        QDataSet d= Ops.bundle( dep0, nextRecord.slice(0) );
                        for ( int j=1; j<nextRecord.length(); j++ ) {
                            d= Ops.bundle( d, nextRecord.slice(j));
                        }   
                        nextRecord= d;
                        break;
                    default:
                        throw new IllegalArgumentException("rank>2 streaming not supported");
                }
            }
            if ( this.sortDataSet!=null ) {
                nextRecord= DataSetOps.applyIndex( src, 1, sortDataSet, true );
            }
            recordCount++;
            return nextRecord;
        } else {
            return src.slice(index++);
        }
    }
    
    @Override
    public void remove() {  //JAVA7: this can be removed when Java 8 is required.
        // do nothing.
    }
}
