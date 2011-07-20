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
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.JoinDataSet;
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
    QDataSet ds;
    QDataSet bundleDs;
    DataSource dss;
    Exception ex;
    QDataSet error;
    QDataSet errorNoDs;
    QDataSet nonValueDs;
    QDataSet nonMonoDs;

    static final Logger logger= Logger.getLogger( "virbo.autoplot.uritcasource" );

    public UriTcaSource( String uri ) throws Exception {
        logger.log(Level.FINE, "new tca source: {0}", uri);
        if ( uri.startsWith("class:org.autoplot.tca.UriTcaSource:") ) {
            throw new IllegalArgumentException("pass a URI to this, not class:org.autoplot.tca.UriTcaSource");
        }
        DataSource dss1= DataSetURI.getDataSource(uri);
        this.tsb= dss1.getCapability( TimeSeriesBrowse.class );
        this.dss= dss1;
        EnumerationUnits eu= new EnumerationUnits("UriTcaSource");
        error= DataSetUtil.asDataSet( eu.createDatum("Error") );
        errorNoDs= DataSetUtil.asDataSet( eu.createDatum("No Data") );
        nonValueDs= DataSetUtil.asDataSet( eu.createDatum(" ") );
        nonMonoDs= DataSetUtil.asDataSet( eu.createDatum("Non Mono") );
    }

    private void doRead( ) throws Exception {
        ProgressMonitor mon= new NullProgressMonitor(); // DasProgressPanel.createFramed("loading data");

        if ( this.tsb!=null ) {
            logger.log(Level.FINE, "reading TCAs from TSB {0}", this.tsb.getURI());
        } else {
            logger.log(Level.FINE, "reading TCAs from {0}", dss);
        }
        ds= dss.getDataSet( mon );
        bundleDs= (QDataSet)ds.property(QDataSet.BUNDLE_1);
        if ( bundleDs==null ) {
            if ( ds.rank()==1 ) { // just a single param, go ahead and support this.
                DDataSet bds1= DDataSet.createRank2(1,0);
                String name= (String) ds.property(QDataSet.NAME);
                String label= (String) ds.property(QDataSet.LABEL);
                bds1.putProperty( QDataSet.NAME, 0, name==null ? "ds0" : name );
                bds1.putProperty( QDataSet.LABEL, 0, label==null ? ( name==null ? "" : name ) : label );
                bundleDs= bds1;
            } else {
                DDataSet bds1= DDataSet.createRank2(ds.length(0),0);
                QDataSet dep1= (QDataSet) ds.property(QDataSet.DEPEND_1);
                Units u= dep1==null ? Units.dimensionless : SemanticOps.getUnits(dep1);
                for ( int i=0; i<ds.length(0); i++ ) {
                    String c= "";
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
        System.err.println("  doRead got: "+ds );

    }


    public QDataSet value(QDataSet parm) {
        Datum d= DataSetUtil.asDatum( parm.slice(0) );
        QDataSet context= (QDataSet) parm.property( QDataSet.CONTEXT_0 ); // should be a bins dimension

        boolean read= false;
        if ( tsb==null ) {
            read= false;
        } else {
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
                return new JoinDataSet( errorNoDs );
            }

            QDataSet dep0= SemanticOps.xtagsDataSet(ds);
            QDataSet d0= parm.slice(0);

            if ( !SemanticOps.isMonotonic(dep0 ) ) {
                logger.fine("dataset dependence is not monotonic");
                return new JoinDataSet( nonMonoDs );
            }
            QDataSet findex= Ops.findex( dep0, d0 ); // TODO: param.slice(0) does findex support rank 0?

            if ( findex.value()>=-0.5 && findex.value()<dep0.length()-0.5 ) {
                int ii= (int)( findex.value() + 0.5 ); // nearest neighbor
                QDataSet result= ds.slice(ii);
                if ( result.rank()==0 ) {
                    result= new JoinDataSet( result );
                }
                ((MutablePropertyDataSet)result).putProperty( QDataSet.BUNDLE_0, bundleDs );
                return result;
            } else if ( findex.value()>-1 && findex.value()<0 ) { // class:org.autoplot.tca.UriTcaSource:vap+dat:file:/home/jbf/project/autoplot/data/dat/rockets/21139_E_field.txt?skipLines=1&depend0=field0&rank2=field3-field4  at 190
                findex= Ops.findex( dep0, d0 );
                QDataSet result= ds.slice(0);
                if ( result.rank()==0 ) {
                    result= new JoinDataSet( result );
                }
                ((MutablePropertyDataSet)result).putProperty( QDataSet.BUNDLE_0, bundleDs );
                return result;

            } else {
                if ( tsb==null ) {
                    JoinDataSet result=  new JoinDataSet( nonValueDs );
                    for ( int i=1; i<ds.length(0); i++ ) {
                        result.join(nonValueDs);
                    }
                    return result;
                } else {
                    return new JoinDataSet( error );
                }
            }
            //

        } catch ( Exception ex ) {
            ex.printStackTrace();
            return new JoinDataSet( error );
        }

    }

    public synchronized QDataSet exampleInput() {

        Datum t0;
        Units tu;
        String label;
        if ( this.tsb!=null ) {
            t0= this.tsb.getTimeRange().min();
            tu= t0.getUnits();
            label= "Time";
        } else {
            try {
                if ( ds==null ) {
                    doRead();
                }
                QDataSet dep0= (QDataSet) ds.property(QDataSet.DEPEND_0);
                t0= DataSetUtil.asDatum( dep0.slice(0) );
                tu= t0.getUnits();
                label= "???";
            } catch ( Exception ex ) {
                throw new RuntimeException(ex);
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
