/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

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
import org.virbo.dataset.AbstractQFunction;
import org.virbo.dataset.BundleDataSet;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.dsops.Ops;

/**
 * Allow Autoplot URIs to supply data to label plots.
 *
 *   class:org.autoplot.tca.AutoplotTCASource:vap+file:/tmp/foo.txt?rank2=field1-field4&depend0=field0
 *   class:org.autoplot.tca.AutoplotTCASource:vap+dat:file:/home/jbf/project/autoplot/data/dat/rockets/21139_E_field.txt?skipLines=1&depend0=field0&rank2=field3-field4
 * @author jbf
 */
public class UriTcaSource extends AbstractQFunction {

    TimeSeriesBrowse tsb;
    boolean needToRead;
    QDataSet ds;
    QDataSet bundleDs;
    DataSource dss;
    Exception ex;
    QDataSet error;
    QDataSet errorNoDs;
    QDataSet nonValueDs;
    QDataSet nonMonoDs;
    QDataSet initialError;

    static final Logger logger= org.das2.util.LoggerManager.getLogger( "autoplot.tca.uritcasource" );

    public UriTcaSource( String uri ) throws Exception {
        logger.log(Level.FINE, "new tca source: {0}", uri);
        if ( uri.startsWith("class:org.autoplot.tca.UriTcaSource:") ) {
            throw new IllegalArgumentException("pass a URI to this, not class:org.autoplot.tca.UriTcaSource");
        }

        EnumerationUnits eu= new EnumerationUnits("UriTcaSource");
        error= DataSetUtil.asDataSet( eu.createDatum("Error") );
        errorNoDs= DataSetUtil.asDataSet( eu.createDatum("No Data") );
        nonValueDs= DataSetUtil.asDataSet( eu.createDatum(" ") );
        nonMonoDs= DataSetUtil.asDataSet( eu.createDatum("Non Mono") );

        DataSource dss1;
        try {
            dss1= DataSetURI.getDataSource(uri);
            initialError= null;
            this.tsb= dss1.getCapability( TimeSeriesBrowse.class );
            this.dss= dss1;
            this.needToRead= true;
        } catch ( Exception ex ) {
            ex.printStackTrace();
            initialError= DataSetUtil.asDataSet( eu.createDatum(ex.toString()) );
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
        if ( this.tsb==null ) { // jython scripts can get a TimeSeriesBrowse after the first read.
            tsb= dss.getCapability( TimeSeriesBrowse.class );
        }
        logger.log(Level.FINE, "  doRead got: {0}", ds);

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
                    double check= DatumRangeUtil.normalize( dr, DataSetUtil.asDatumRange(context).min() );
                    if ( check<-100 || check>200 ) {
                        System.err.println("check suppressed bad read...");
                        context=null;
                    }
                    if ( context!=null ) dr= DatumRangeUtil.union( dr, DataSetUtil.asDatumRange(context,true) );
                    tsb.setTimeRange(dr);
                }
            }
        }
        
        try {
            if ( read ) {
                doRead();
                read= false;
            }
            if ( ds==null ) {
                return new BundleDataSet( errorNoDs );
            }

            QDataSet dep0= SemanticOps.xtagsDataSet(ds);
            QDataSet d0= parm.slice(0);

            if ( !SemanticOps.isMonotonic(dep0 ) ) {
                logger.fine("dataset dependence is not monotonic");
                return new BundleDataSet( nonMonoDs );
            }
            QDataSet findex= Ops.findex( dep0, d0 ); // TODO: param.slice(0) does findex support rank 0?

            QDataSet result;
            if ( findex.value()>=-0.5 && findex.value()<dep0.length()-0.5 ) {
                int ii= (int)( findex.value() + 0.5 ); // nearest neighbor
                result= ds.slice(ii);
                if ( !isValid(result) ) { // pick a relavant near neighbor
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
                }

            } else {

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
                    } else {
                        if ( tsb.getTimeRange().contains(DataSetUtil.asDatum(d0)) ) {
                            BundleDataSet result1= new BundleDataSet( nonValueDs );
                            for ( int i=1; i<ds.length(0); i++ ) {
                                result1.bundle(nonValueDs);
                            }
                            result= result1;
                        } else {
                            BundleDataSet result1= new BundleDataSet( error );
                            for ( int i=1; i<ds.length(0); i++ ) {
                                result1.bundle(error);
                            }
                            result= result1;
                        }
                    }
                }
            }

            if ( result.rank()==0 ) {
                result= new BundleDataSet( result );
            }

            ((MutablePropertyDataSet)result).putProperty( QDataSet.BUNDLE_0, bundleDs );

            return result;
            //

        } catch ( Exception ex ) {
            ex.printStackTrace(); //TODO: user never sees this...
            return new BundleDataSet( error );
        }

    }

    public synchronized QDataSet exampleInput() {

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

        return ret;
        
    }

}
