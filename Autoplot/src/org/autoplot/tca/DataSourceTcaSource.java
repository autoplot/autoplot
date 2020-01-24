
package org.autoplot.tca;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.AbstractQFunction;
import org.das2.qds.BundleDataSet;
import org.das2.qds.DDataSet;
import org.das2.qds.DataSetUtil;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.autoplot.dom.Application;
import org.autoplot.dom.DataSourceFilter;
import org.das2.qds.ops.Ops;

/**
 * Allow Autoplot DataSources to lookup datasets.  The filter within the 
 * DataSourceFilter is then applied to the data.
 * @author jbf
 */
public class DataSourceTcaSource extends AbstractQFunction {

    TimeSeriesBrowse tsb;
    boolean needToRead;
    QDataSet ds;
    QDataSet tlim;
    QDataSet bundleDs;
    DataSource dss;
    QDataSet error;
    QDataSet errorNoDs;
    QDataSet nonValueDs;
    //QDataSet nonMonoDs;
    QDataSet initialError;

    static final Logger logger= org.das2.util.LoggerManager.getLogger( "autoplot.tca.uritcasource" );
    
    // cache the example input so we only attempt read once.
    private MutablePropertyDataSet exampleInput=null;

    public DataSourceTcaSource( DataSourceFilter node ) throws Exception {

        EnumerationUnits eu= new EnumerationUnits("UriTcaSource");
        error= DataSetUtil.asDataSet( eu.createDatum("Error") );
        errorNoDs= DataSetUtil.asDataSet( eu.createDatum("No Data") );
        nonValueDs= DataSetUtil.asDataSet( eu.createDatum(" ") );
        //nonMonoDs= DataSetUtil.asDataSet( eu.createDatum("Non Mono") );

        DataSource dss1;
        try {
            dss1= node.getController().getDataSource();
            initialError= null;
            this.tsb= dss1.getCapability( TimeSeriesBrowse.class );
            this.dss= dss1;
            this.needToRead= true;
        } catch ( Exception lex ) {
            logger.log( Level.WARNING, lex.getMessage(), lex );
            initialError= DataSetUtil.asDataSet( eu.createDatum(lex.toString()) );
        }
        
    }

    private void doRead( ) throws Exception {
        ProgressMonitor mon= new NullProgressMonitor(); // DasProgressPanel.createFramed("loading data");

        if ( this.tsb!=null ) {
            logger.log(Level.FINE, "reading TCAs from TSB {0}", this.tsb.getURI());
        } else {
            logger.log(Level.FINE, "reading TCAs from {0}", dss);
        }
        needToRead= false; // clear the flag in case there is an exception.
        ds= dss.getDataSet( mon );
        
        if ( ds==null ) {
            logger.log(Level.FINE, "doRead getDataSet got null ");
            
        } else {
            logger.log(Level.FINE, "doRead got: {0}", ds);

            QDataSet dep0= SemanticOps.xtagsDataSet(ds);
            if ( !DataSetUtil.isMonotonicAndIncreasing(dep0) ) {
                logger.warning("TCA contains data which is not monotonically increasing");
                if ( dep0.value(0)>dep0.value(dep0.length()-1) ) {
                    ds= Ops.copy( Ops.reverse(ds) );
                    dep0= SemanticOps.xtagsDataSet(ds);
                    if ( !DataSetUtil.isMonotonicAndIncreasing(dep0) ) {
                        logger.warning("reversed TCA dataset still contains non-monotonic tags");
                        ds= Ops.ensureMonotonicAndIncreasingWithFill(ds);
                    } else {
                        logger.info("reversing TCA dataset makes tags monotonically increasing.");
                    }
                } else {
                    logger.warning("removing non-monotonically increasing tags of TCA dataset.");
                    ds= Ops.ensureMonotonicAndIncreasingWithFill(ds);
                }
            }

            tlim= DataSetUtil.guessCadenceNew( SemanticOps.xtagsDataSet(ds), ds );

            if ( this.tsb!=null ) {
                DatumRange dr= this.tsb.getTimeRange();
                QDataSet ext= Ops.extent( SemanticOps.xtagsDataSet(ds), null );
                double d0= DatumRangeUtil.normalize( dr, DataSetUtil.asDatum( ext.slice(0) ) );
                double d1= DatumRangeUtil.normalize( dr, DataSetUtil.asDatum( ext.slice(1) ) );
                logger.log(Level.FINE, "normalized after load: {0}-{1}", new Object[]{d0, d1});
            }
            bundleDs= (QDataSet)ds.property(QDataSet.BUNDLE_1);
            if ( bundleDs==null ) {
                if ( ds.rank()==1 ) { // just a single param, go ahead and support this.
                    DDataSet bds1= DDataSet.createRank2(1,0);
                    String name= (String) ds.property(QDataSet.NAME);
                    String label= (String) ds.property(QDataSet.LABEL);
                    bds1.putProperty( QDataSet.NAME, 0, name==null ? "ds0" : name );
                    bds1.putProperty( QDataSet.LABEL, 0, label==null ? ( name==null ? "" : name ) : label );
                    if ( ds.property(QDataSet.VALID_MIN)!=null ) bds1.putProperty( QDataSet.VALID_MIN, 0, ds.property(QDataSet.VALID_MIN) );
                    if ( ds.property(QDataSet.VALID_MAX)!=null ) bds1.putProperty( QDataSet.VALID_MAX, 0, ds.property(QDataSet.VALID_MAX) );
                    if ( ds.property(QDataSet.FILL_VALUE)!=null ) bds1.putProperty( QDataSet.FILL_VALUE, 0, ds.property(QDataSet.FILL_VALUE) );
                    bundleDs= bds1;
                } else {
                    DDataSet bds1= DDataSet.createRank2(ds.length(0),0);
                    QDataSet dep1= (QDataSet) ds.property(QDataSet.DEPEND_1);
                    Units u= dep1==null ? Units.dimensionless : SemanticOps.getUnits(dep1);
                    for ( int i=0; i<ds.length(0); i++ ) {
                        String name= ( dep1!=null ? u.createDatum(dep1.value(i)).toString() : (String)ds.property(QDataSet.NAME) );
                        String label= (String) ds.property(QDataSet.LABEL);
                        bds1.putProperty( QDataSet.NAME, i, "ds"+i );
                        bds1.putProperty( QDataSet.LABEL, i, label==null ?  ( name==null ? "" : name ) : label );
                    }
                    bundleDs= bds1;
                }
            }
            
        }
        if ( this.tsb==null ) { // jython scripts can get a TimeSeriesBrowse after the first read.
            tsb= dss.getCapability( TimeSeriesBrowse.class );
        }

    }

    /**
     * must have all valid
     * @param result
     * @return
     */
    private boolean isValid( QDataSet result ) {
        QDataSet wds= DataSetUtil.weightsDataSet(result);
        if ( wds.rank()==0 ) {
            return wds.value()>0;
        } else {
            boolean valid= true;
            for ( int i=0; i<wds.length(); i++ ) {
                valid= valid && wds.value(i)>0;
            }
            return valid;
        }
    }

    /**
     * This will set the focus range for the TimeSeriesBrowse, if available, 
     * and then call each tick individually.
     * @param parms
     * @return 
     */
    @Override
    public synchronized QDataSet values( QDataSet parms ) {
        if ( initialError!=null ) {
            if ( ds==null ) {
                return new BundleDataSet( error );
            }
        }
                
        QDataSet tt= Ops.copy( Ops.unbundle(parms, 0 ) );
        QDataSet dtt= Ops.diff( tt );
        QDataSet gcd;
        try {
            gcd= DataSetUtil.gcd( dtt, Ops.divide( dtt.slice(0),100 ) );
        } catch ( IllegalArgumentException ex ) {
            ex.printStackTrace();
            gcd= Ops.reduceMin( dtt, 0 );
        }
            
        Datum d;
        DatumRange dr= null; // calculate the bounding DatumRange for all params.
        for ( int i=0; i<parms.length(); i++ ) {
            d= DataSetUtil.asDatum( parms.slice(i).slice(0) );
            dr= DatumRangeUtil.union( dr, d );
        }
        Datum neededResolution= DataSetUtil.asDatum( gcd ).divide(2); // something arbitrarily close.
        
        logger.log(Level.FINE, "loading TCAs at {0} (gcd={1})", new Object[]{neededResolution, gcd});
            
        if ( tsb!=null ) {
            DatumRange timeRange= tsb.getTimeRange();
            Datum resolution= tsb.getTimeResolution();
            tsb.setTimeRange(dr);
            if ( timeRange==null || !timeRange.contains(dr) || ( resolution!=null && neededResolution.lt(resolution) ) ) {
                tsb.setTimeResolution( neededResolution );
                needToRead= true;
            }
        }
        return super.values(parms); // just loop over them, calling value for each, as we did before.
    }
    
    @Override
    public synchronized QDataSet value(QDataSet parm) {

        if ( initialError!=null ) {
            if ( ds==null ) {
                return new BundleDataSet( error );
            }
        }

        Datum d= DataSetUtil.asDatum( parm.slice(0) );
        QDataSet context= (QDataSet) parm.property( QDataSet.CONTEXT_0, 0 ); // should be a bins dimension
        QDataSet deltaMinus= (QDataSet)parm.property(QDataSet.DELTA_MINUS,0);
        QDataSet deltaPlus= (QDataSet)parm.property(QDataSet.DELTA_PLUS,0);
        boolean read= needToRead;
        if ( tsb!=null ) {
            DatumRange dr= tsb.getTimeRange();

            if ( !DatumRangeUtil.sloppyContains( dr, d ) ) {
                while ( d.ge( dr.max() ) ) {
                    dr= dr.next();
                    read= true;
                }
                while ( d.lt( dr.min() ) ) {
                    dr= dr.previous();
                    read= true;
                }
                if ( read ) {
                    if ( context!=null ) {
                        double check= DatumRangeUtil.normalize( dr, DataSetUtil.asDatumRange(context).min() );
                        if ( check<-100 || check>200 ) {
                            System.err.println("check suppressed bad read...");
                            context=null;
                        }
                    }
                    if ( context!=null ) dr= DatumRangeUtil.union( dr, DataSetUtil.asDatumRange(context,true) );
                    tsb.setTimeRange(dr);
                }
            }
        }
        
        try {
            if ( read ) {
                doRead();
                logger.log( Level.FINER, "loaded dataset: {0} {1} ", new Object[]{ tsb!=null ? tsb.getTimeRange() : "", ds } );
            }
            if ( ds==null ) {
                BundleDataSet result= new BundleDataSet( errorNoDs );
                ((MutablePropertyDataSet)result).putProperty( QDataSet.UNITS, errorNoDs.property(QDataSet.UNITS) );
                return result;
            }
            
            QDataSet dep0= SemanticOps.xtagsDataSet(ds);
            QDataSet d0= parm.slice(0);
            
            QDataSet findex;
            if ( dep0.length()==1 ) {
                findex= Ops.dataset(0);
            } else {
                findex= Ops.findex( dep0, d0 );
                if ( Math.abs( findex.value() % 1.0 ) > 0.1 ) {
                    logger.log(Level.FINE, "interpolating to calculate tick for {0}", d);
                }
            }
            
            QDataSet result;
            if ( findex.value()>=-0.5 && findex.value()<dep0.length()-0.5 ) {
                int ii= (int)( findex.value() + 0.5 ); // nearest neighbor
                result= ds.slice(ii); 
                if ( !isValid(result) ) { // pick a relavant near neighbor
                    if ( deltaPlus==null ) {
                        deltaPlus= DataSetUtil.asDataSet( SemanticOps.getUnits(dep0).getOffsetUnits().createDatum(0) );
                    }
                    if ( deltaMinus==null ) {
                        deltaMinus= deltaPlus;
                    }
                    findex= Ops.findex( dep0, Ops.subtract( d0, deltaMinus ) );
                    int imin= (int)( findex.value() + 0.5 );
                    if ( imin<0 ) imin=0;
                    findex= Ops.findex( dep0, Ops.add( d0, deltaPlus ) );
                    int imax= (int)( findex.value() + 0.5 );
                    if ( imax>=dep0.length() ) imax= dep0.length()-1;
                    int irad= Math.max( ii-imin, imax-ii );
                    for ( int iiii= 1; iiii<irad; iiii++ ) {
                        if ( ii-iiii >= imin ) {
                            result= ds.slice(ii-iiii);
                            if ( isValid(result) ) {
                                break;
                            }
                        }
                        if ( ii+iiii <= imax ) {
                            result= ds.slice(ii+iiii);
                            if ( isValid(result) ) {
                                break;
                            }
                        }
                    }
                } else {
                    
                    logger.log( Level.FINER, "findex={0} for {1} {2}", new Object[]{findex, d0, result});
                    if ( deltaPlus!=null ) {
                        QDataSet delta= Ops.magnitude( Ops.subtract( d0, dep0.slice(ii) ) );
                        if ( Ops.gt( delta, tlim ).value()==1 ) {
                            BundleDataSet result1=  new BundleDataSet( nonValueDs );
                            for ( int i=1; i<ds.length(0); i++ ) {
                                result1.bundle(nonValueDs);
                            }
                            result= result1;
                        }
                    }
                    
                }

            } else {

                if ( deltaPlus==null ) deltaPlus= DataSetUtil.asDataSet( SemanticOps.getUnits(dep0).getOffsetUnits().createDatum(0) );
                if ( deltaMinus==null ) deltaMinus= deltaPlus;
                if ( findex.value()>dep0.length()-1 && ( Ops.ge( Ops.add( dep0.slice(dep0.length()-1), deltaMinus ), d0 ).value()==1 ) ) {
                    result= ds.slice(dep0.length()-1);

                } else if ( findex.value()<0 && ( Ops.le( Ops.subtract( dep0.slice(0), deltaPlus ), d0 ).value()==1 ) ) {
                    result= ds.slice(0);

                } else {
                    if ( tsb==null ) {
                        BundleDataSet result1=  new BundleDataSet( nonValueDs );
                        for ( int i=1; i<ds.length(0); i++ ) {
                            result1.bundle(nonValueDs);
                        }
                        result= result1;
                        ((MutablePropertyDataSet)result).putProperty( QDataSet.UNITS, nonValueDs.property(QDataSet.UNITS) );
                    } else {
                        if ( tsb.getTimeRange().contains(DataSetUtil.asDatum(d0)) ) {
                            BundleDataSet result1= new BundleDataSet( nonValueDs );
                            for ( int i=1; i<ds.length(0); i++ ) {
                                result1.bundle(nonValueDs);
                            }
                            result= result1;
                            ((MutablePropertyDataSet)result).putProperty( QDataSet.UNITS, nonValueDs.property(QDataSet.UNITS) );
                        } else {
                            logger.log( Level.INFO, "tick {0} is outside bounds of loaded data ({1}) {2}",
                                    new Object[]{DataSetUtil.asDatum(d0), tsb.getTimeRange(), ds });
                            BundleDataSet result1= new BundleDataSet( error );
                            for ( int i=1; i<ds.length(0); i++ ) {
                                result1.bundle(error);
                            }
                            result= result1;
                            ((MutablePropertyDataSet)result).putProperty( QDataSet.UNITS, error.property(QDataSet.UNITS) );
                        }
                    }
                }
                return result;
            }

            if ( result.rank()==0 ) {
                result= new BundleDataSet( result );
            }

            ((MutablePropertyDataSet)result).putProperty( QDataSet.BUNDLE_0, bundleDs );

            return result;
            //

        } catch ( Exception lex ) {
            logger.log( Level.WARNING, lex.getMessage(), lex );
            lex.printStackTrace();
            return new BundleDataSet( error );
        }

    }

    @Override
    public synchronized QDataSet exampleInput() {

        if ( exampleInput!=null ) {
            return exampleInput;
        }
        
        Datum t0;
        Units tu;
        String label;

        if ( initialError!=null ) {
            label= "???";
            tu= Units.us2000;
            t0= tu.createDatum(0);
        } else if ( this.tsb!=null ) {
            t0= this.tsb.getTimeRange().min();
            tu= t0.getUnits();
            label= "Time";
            if ( needToRead ) { // we need to verify that this TSB will return time for its independent parameter
                try {
                    doRead();
                    QDataSet dep0= (QDataSet) ds.property(QDataSet.DEPEND_0);
                    if ( dep0!=null ) {
                        t0= DataSetUtil.asDatum( dep0.slice(0) );
                        tu= t0.getUnits();
                        label= "???";
                    }
                } catch (Exception ex) {
                    if ( ex instanceof RuntimeException ) {
                       throw (RuntimeException)ex;
                    } else {
                        throw new RuntimeException(ex);
                    }
                }
            }
        } else {
            try {
                if ( needToRead ) {
                    doRead();
                }
                QDataSet dep0= (QDataSet) ds.property(QDataSet.DEPEND_0);
                if ( dep0==null ) {
                    throw new RuntimeException("Unable to locate independent variable, expecting to find DEPEND_0");
                }
                t0= DataSetUtil.asDatum( dep0.slice(0) );
                tu= t0.getUnits();
                label= "???";
            } catch ( Exception ex ) {
                if ( ex instanceof RuntimeException ) {
                    throw (RuntimeException)ex;
                } else {
                    throw new RuntimeException(ex);
                }
            }

        }

        DDataSet inputDescriptor = DDataSet.createRank2(1,0);
	inputDescriptor.putProperty(QDataSet.LABEL, 0, label );
        inputDescriptor.putProperty(QDataSet.UNITS, 0, tu );

        QDataSet q = DataSetUtil.asDataSet(t0);

        MutablePropertyDataSet ret = (MutablePropertyDataSet) Ops.bundle(null,q);
        inputDescriptor.putProperty( QDataSet.CADENCE, DataSetUtil.asDataSet( Units.seconds.createDatum(1)) ) ;
        ret.putProperty(QDataSet.BUNDLE_0,inputDescriptor);

        this.exampleInput= ret;
        
        return ret;
        
    }

}
