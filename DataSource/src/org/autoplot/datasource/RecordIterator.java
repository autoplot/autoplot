
package org.autoplot.datasource;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.DatumRange;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.aggregator.AggregatingDataSourceFactory;
import org.das2.qds.DataSetOps;
import org.das2.qds.DataSetUtil;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.capability.Streaming;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.DataSetBuilder;

/**
 * Introduce class to hold code for iterating through any dataset.  
 * This will detect the time series browse capability and streaming.
 * So if the data source is already streaming, then this is trivial.  If
 * not, then it will request chunks of data from the time series browse
 * and handle them one chunk at a time.  And additional trim can be used,
 * see constrainDepend0.
 * @author jbf
 */
public class RecordIterator implements Iterator<QDataSet>  {

    int index;
    int lastIndex=-1; // -1 means we don't know, no constraint.
    QDataSet src=null;
    
    Iterator<QDataSet> streamingIterator=null;
    private DatumRange depend0Constraint=null;    
    QDataSet nextRecord=null; // when depend0Constraint is set, we need to read to the next record in the hasNext method.
            
    QDataSet sortDataSet=null;

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
                    QDataSet dep1= (QDataSet) ds.property(QDataSet.DEPEND_1);
                    this.src= Ops.bundle( null, dep0 );
                    for ( int i=0; i<ds.length(0); i++ ) {
                        this.src= Ops.bundle( this.src, Ops.unbundle(ds,i) );
                    }
                    if ( dep1!=null && dep1.rank()==2 ) {
                        for ( int i=0; i<dep1.length(0); i++ ) {
                            this.src= Ops.bundle( this.src, Ops.unbundle(dep1,i) );
                        }
                    }                    
                } else if ( ds.rank()>2 ) { // flatten the rank>2 to rank=2.
                    int[] qube= DataSetUtil.qubeDims(ds.slice(0));
                    QDataSet dep2= (QDataSet) ds.property(QDataSet.DEPEND_2);
                    if ( dep2!=null && dep2.rank()==3 ) {
                        dep2= Ops.reform( dep2, dep2.length(), new int[] { DataSetUtil.product(qube) } );                                                
                    } 
                    ds= Ops.reform( ds, ds.length(), new int[] { DataSetUtil.product(qube) } );                    
                    this.src= Ops.bundle( dep0, Ops.slice1(ds,0) );
                    for ( int i=1; i<ds.length(0); i++ ) {
                        this.src= Ops.bundle( this.src, Ops.slice1(ds,i) );
                    }
                    if ( dep2!=null ) {
                        this.src= Ops.bundle( this.src, Ops.slice1(dep2,0) );
                        for ( int i=1; i<dep2.length(0); i++ ) {
                            this.src= Ops.bundle( this.src, Ops.slice1(dep2,i) );
                        }
                    }
                }
            } else {
                this.src= ds;
            }
            
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
            this.depend0Constraint= dr;
            if ( streamingIterator!=null ) {
                if ( streamingIterator.hasNext() ) {
                    logger.finer("advancing streamingIterator to first record");
                    nextRecord= streamingIterator.next();
                    nextRecord= normalize(nextRecord);
                    QDataSet dep0= nextRecord.slice(0);
                    while ( DataSetUtil.asDatum(dep0).lt( dr.min() ) && streamingIterator.hasNext() ) {
                        nextRecord= streamingIterator.next();
                        nextRecord= normalize(nextRecord);
                        dep0= nextRecord.slice(0);
                    }
                    if ( depend0Constraint==null || DataSetUtil.asDatum(dep0).ge( depend0Constraint.max() ) ) {
                        nextRecord= null;
                    }
                    index= -1;
                } else {
                    logger.finer("have streamingIterator, but hasNext()=false");
                }
            } else {
                logger.finer("not streaming, src=null");
                nextRecord= null;
            }
            return;
        } else {
            logger.finer("src does not equal null");
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
            return nextRecord != null;            
        } else {
            return this.index < this.lastIndex;
        }
    }

    /**
     * look for CONTEXT_0 and make this the first element of the bundle.
     * @param result
     * @return 
     */
    private static QDataSet normalize( QDataSet result ) {
        QDataSet dep0= (QDataSet) result.property(QDataSet.CONTEXT_0);
        if ( dep0!=null ) {
            switch (result.rank()) {
                case 0:
                    result= Ops.bundle( dep0, result );
                    break;
                case 1:
                    QDataSet d= Ops.bundle( dep0, result.slice(0) );
                    for ( int j=1; j<result.length(); j++ ) {
                        d= Ops.bundle( d, result.slice(j));
                    }   
                    result= d;
                    break;
                default:
                    throw new IllegalArgumentException("rank>2 streaming not supported");
            }
        }
        return result;
    }
    
    @Override
    public QDataSet next() {
        if ( this.streamingIterator!=null ) {
            QDataSet result= nextRecord;
            if ( this.sortDataSet!=null ) {
                result= DataSetOps.applyIndex( result, 0, sortDataSet, true );
            }
            if ( streamingIterator.hasNext() ) {
                QDataSet nextRecord1= streamingIterator.next();
                nextRecord1= normalize(nextRecord1);
                QDataSet dep0= (QDataSet) nextRecord1.slice(0);
                if ( depend0Constraint==null || DataSetUtil.asDatum(dep0).lt( depend0Constraint.max() ) ) {
                    nextRecord= nextRecord1;
                } else {
                    nextRecord= null;
                }
            } else {
                nextRecord= null;
            }
            return result;
        } else {
            return src.slice(index++);
        }
    }
    
    @Override
    public void remove() {  //JAVA7: this can be removed when Java 8 is required.
        // do nothing.
    }
    
    /**
     * do the opposite function, collect all the records and return a dataset.
     * @param qds
     * @return 
     */
    public static QDataSet collect( Iterator<QDataSet> qds ) {
        QDataSet rec= qds.next();
        DataSetBuilder b;
        DataSetBuilder dep0b= new DataSetBuilder(1,100);
        switch ( rec.rank() ) {
            case 0:
                b= new DataSetBuilder(1,100);
                break;
            case 1:
                b= new DataSetBuilder(2,100,rec.length());
                break;                
            case 2:
                b= new DataSetBuilder(2,100,rec.length(),rec.length(0));
                break;                
            case 3:
                b= new DataSetBuilder(2,100,rec.length(),rec.length(0),rec.length(1));
                break; 
            default:
                throw new IllegalArgumentException("bad rank");
        }
        b.nextRecord(rec);
        QDataSet dep0= (QDataSet)rec.property(QDataSet.CONTEXT_0);
        if ( dep0!=null ) dep0b.nextRecord();
        while ( qds.hasNext() ) {
            rec= qds.next();
            b.nextRecord(rec);
            dep0= (QDataSet)rec.property(QDataSet.CONTEXT_0);
            if ( dep0!=null ) dep0b.nextRecord();
        }
        MutablePropertyDataSet result= b.getDataSet();
        if ( dep0b.getLength()>0 ) {
            result.putProperty( QDataSet.DEPEND_0, dep0b.getDataSet());
        }
        return result;
    }
}
