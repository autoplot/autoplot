/*
 * DatumRangePersistenceDelegate.java
 *
 * Created on August 8, 2007, 10:43 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.autoplot.state;

import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import java.beans.DefaultPersistenceDelegate;
import java.beans.Encoder;
import java.beans.Expression;
import java.beans.PersistenceDelegate;
import org.das2.datum.Datum;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.UnitsUtil;
import org.virbo.metatree.MetadataUtil;

/**
 *
 * @author jbf
 */
public class DatumRangePersistenceDelegate extends PersistenceDelegate {
    
    public DatumRangePersistenceDelegate()  {
    }

    private Datum abs( Datum w ) {
        return w.getUnits().createDatum( Math.abs(w.doubleValue(w.getUnits() )) );
    }

    private boolean sloppyEquals( Object o1, Object o2 ) {
        if ( o1==o2 ) return true;
        if ( !( o1 instanceof DatumRange ) || !(o2 instanceof DatumRange ) ) {
            return false;
        }
        DatumRange dr1= (DatumRange) o1;
        DatumRange dr2= (DatumRange) o2;

        if ( !dr1.getUnits().isConvertableTo(dr2.getUnits()) ) return false;
        Datum w= dr1.width();
        if ( w.doubleValue( w.getUnits() )==0 ) {
            return dr1.equals(dr2) && dr2.width()==w;
        } else {
            Datum err= w.divide(1000);
            return abs( dr1.min().subtract(dr2.min()) ).lt( err ) && abs( dr1.min().subtract(dr2.min()) ).lt(err) ;
        }

    }

    @Override
    protected boolean mutatesTo(Object oldInstance, Object newInstance) {
        // super checks for non-null and same class type.
        return super.mutatesTo(oldInstance, newInstance) && sloppyEquals( oldInstance,newInstance);
    }


    protected Expression instantiate(Object oldInstance, Encoder out) {        
        DatumRange field= (DatumRange)oldInstance;
        Units u= field.getUnits();
        if ( UnitsUtil.isTimeLocation(u) ) {
            return new Expression( field, this.getClass(), "newTimeRange", new Object[] { field.toString() } );
        } else {
            return new Expression( field, this.getClass(), "newDatumRange", new Object[] { field.min().doubleValue(u), field.max().doubleValue(u), u.toString() } );
        }
        
    }
    
    public static DatumRange newDatumRange( double min, double max, String units ) {
        Units u= MetadataUtil.lookupUnits(units);
        return DatumRange.newDatumRange( min, max, u );
    }

    public static DatumRange newTimeRange( String stimeRange ) {
        return DatumRangeUtil.parseTimeRangeValid(stimeRange);
    }

    protected void initialize(Class<?> type, Object oldInstance, Object newInstance, Encoder out) {
        super.initialize(type, oldInstance, newInstance, out);
    }
}
