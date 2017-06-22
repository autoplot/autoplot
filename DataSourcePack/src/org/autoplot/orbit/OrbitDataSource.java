/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.orbit;

import java.net.URI;
import java.text.ParseException;
import java.util.logging.Level;
import org.das2.datum.DatumRange;
import org.das2.datum.Orbits;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.DefaultTimeSeriesBrowse;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.das2.qds.ops.Ops;

/**
 * Read in orbits as events file.  This expects URIs like
 * "vap+orbit:rbspa-pp&timerange=2014" 
 * @author jbf
 */
class OrbitDataSource extends AbstractDataSource {

    String sc;
    TimeSeriesBrowse tsb;
    
    public OrbitDataSource(URI uri) {
        super(uri);
        tsb= new DefaultTimeSeriesBrowse();
        sc= params.get( URISplit.PARAM_ARG_0 );
        String str= params.get( URISplit.PARAM_TIME_RANGE );
        if ( str!=null ) {
            try {
                tsb.setURI(uri.toString());
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
        addCapability(TimeSeriesBrowse.class, tsb );
    }

    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        Orbits o= Orbits.getOrbitsFor(sc);
        DatumRange tr= tsb.getTimeRange();
        String s= o.getOrbit(tr.min());
        
        QDataSet result= null;
        while ( s!=null ) {
            DatumRange dr= o.getDatumRange(s);
            if ( dr.min().lt(tr.max())) {
                result= Ops.createEvent( result, dr.toString(), 0x808080, s );
                s= o.next(s);
            } else {
                break;
            }
        }
        if ( result!=null ) {
            ((MutablePropertyDataSet)result).putProperty( QDataSet.RENDER_TYPE, "eventsBar>orbitMode=T");
        }
        return result;
    }
    
    
}
