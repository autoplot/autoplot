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
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DefaultTimeSeriesBrowse;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.dsops.Ops;

/**
 * read in orbits as events file.
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
                tsb.setURI(uri.toASCIIString());
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
        addCability(TimeSeriesBrowse.class, tsb );
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
