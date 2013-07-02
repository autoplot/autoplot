/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.orbit;

import java.net.URI;
import java.text.ParseException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Orbits;
import org.das2.datum.Units;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.URISplit;
import org.virbo.dsops.Ops;
import org.virbo.dsutil.DataSetBuilder;

/**
 *
 * @author jbf
 */
class OrbitDataSource extends AbstractDataSource {

    String sc;
    DatumRange tr;
    
    public OrbitDataSource(URI uri) {
        super(uri);
        sc= params.get( URISplit.PARAM_ARG_0 );
        String str= params.get( URISplit.PARAM_TIME_RANGE );
        if ( str!=null ) {
            try {
                tr= DatumRangeUtil.parseTimeRange(str);
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        Orbits o= Orbits.getOrbitsFor(sc);
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
