/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.tca;

import java.text.ParseException;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.dataset.AbstractQFunction;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DRank0DataSet;
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
 * @author jbf
 */
public class UriTcaSource extends AbstractQFunction {

    TimeSeriesBrowse tsb;
    QDataSet ds;
    DataSource dss;
    Exception ex;
    QDataSet error;
    QDataSet errorNoDs;

    public UriTcaSource( String uri ) throws Exception {
        DataSource dss= DataSetURI.getDataSource(uri);
        this.tsb= dss.getCapability( TimeSeriesBrowse.class );
        this.dss= dss;
        EnumerationUnits eu= new EnumerationUnits("UriTcaSource");
        error= DataSetUtil.asDataSet( eu.createDatum("Error") );
        errorNoDs= DataSetUtil.asDataSet( eu.createDatum("No Data") );
    }

    public QDataSet value(QDataSet parm) {
        Datum d= DataSetUtil.asDatum( parm.slice(0) );
        DatumRange dr= tsb.getTimeRange();
        boolean read= false;
        if ( !dr.contains(d) ) {
            while ( d.ge( dr.max() ) ) {
                dr= dr.next();
                read= true;
            }
            while ( d.lt( dr.min() ) ) {
                dr= dr.previous();
                read= true;
            }
            if ( read ) tsb.setTimeRange(dr);
        }
        try {
            if ( read ) {
                ds= dss.getDataSet( new NullProgressMonitor() );
            }
            if ( ds==null ) {
                return new JoinDataSet( errorNoDs );
            }

            QDataSet dep0= SemanticOps.xtagsDataSet(ds);
            QDataSet d0= parm.slice(0);
            QDataSet findex= Ops.findex( dep0, d0 ); // TODO: param.slice(0) does findex support rank 0?

            if ( findex.value()>=0 && findex.value()<=dep0.length()-1 ) {
                int ii= (int)(findex.value());
                double aa= findex.value() - ii;
                QDataSet result= ( aa<0.5 ) ? ds.slice(ii) : ds.slice(ii+1);
                return result;
            } else {
                return new JoinDataSet( error );
            }
            //

        } catch ( Exception ex ) {
            return new JoinDataSet( error );
        }

    }

    public QDataSet exampleInput() {

        Datum t0= this.tsb.getTimeRange().min();

        DDataSet inputDescriptor = DDataSet.createRank2(1,0);
	inputDescriptor.putProperty(QDataSet.LABEL, 0, "Time");
        inputDescriptor.putProperty(QDataSet.UNITS, 0, t0.getUnits() );

        QDataSet q = DataSetUtil.asDataSet(t0);

        MutablePropertyDataSet ret = (MutablePropertyDataSet) Ops.bundle(null,q);
        inputDescriptor.putProperty( QDataSet.CADENCE, DataSetUtil.asDataSet( Units.seconds.createDatum(1)) ) ;
        ret.putProperty(QDataSet.BUNDLE_0,inputDescriptor);

        return ret;
        
    }

}
