/*
 * DatumRangePersistenceDelegate.java
 *
 * Created on August 8, 2007, 10:43 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.autoplot.state;

import org.das2.datum.Datum;
import org.das2.datum.Units;
import java.beans.Encoder;
import java.beans.Expression;
import java.beans.PersistenceDelegate;
import org.das2.datum.DatumUtil;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.TimeLocationUnits;
import org.das2.qds.SemanticOps;

/**
 *
 * @author jbf
 */
public class DatumPersistenceDelegate extends PersistenceDelegate {
    
    public DatumPersistenceDelegate()  {
    }

    @Override
    protected boolean mutatesTo(Object oldInstance, Object newInstance) {
        // super checks for non-null and same class type.
        return super.mutatesTo(oldInstance, newInstance) && oldInstance.equals(newInstance);
    }
    
    protected Expression instantiate(Object oldInstance, Encoder out) {

        Datum field= (Datum)oldInstance;
        Units u= field.getUnits();

        if ( u instanceof EnumerationUnits ) {
            return new Expression( field, this.getClass(), "newNominal", new Object[] { ((EnumerationUnits)u).toString(), field.toString() } );
        } else if ( u instanceof TimeLocationUnits ) {
            return new Expression( field, DatumUtil.class, "parseValue", new Object[] { field.toString() } );
        } else {
            return new Expression( field, this.getClass(), "newDatum", new Object[] { field.doubleValue(u), u.toString() } );
        }
        
    }
    
    public static Datum newDatum( double val, String units ) {
        Units u= SemanticOps.lookupUnits(units);
        return u.createDatum( val );
    }

    public static Datum newNominal( String scheme, String value ) {
        EnumerationUnits u= EnumerationUnits.create(scheme);
        //EnumerationUnits u= new EnumerationUnits(scheme);
        return u.createDatum(value);
    }
}
