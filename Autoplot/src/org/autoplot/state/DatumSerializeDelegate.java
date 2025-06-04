
package org.autoplot.state;

import java.text.ParseException;
import org.das2.datum.Datum;
import org.das2.datum.Units;
import org.das2.qstream.SerializeDelegate;

/**
 *
 * @author jbf
 */
public class DatumSerializeDelegate implements SerializeDelegate {

    @Override
    public String format(Object o) {
        Datum d= (Datum)o;
        Units u= (Units) d.getUnits();
        if ( u==null ) u= Units.dimensionless;
        String svalue= d.getFormatter().format(d, u); // we'll provide units context
        if ( svalue.contains(" ") ) {
            throw new RuntimeException("formatted value contains string: \""+svalue+"\"" );
        }
        if ( u==Units.dimensionless ) {
            return svalue + " (dimensionless)";
        } else {
            return ""+u+": "+svalue;
        }
    }

    @Override
    public Object parse(String typeId, String s) throws ParseException {
        s = s.trim();
        if ( s.endsWith(" (dimensionless)") ) {
            int i= s.indexOf(" (dimensionless)");
            return Units.dimensionless.parse(s.substring(0,i) );
        } else {
            int i = s.indexOf(':');
            Units u= Units.lookupUnits(s.substring(0,i) );
            return u.parse( s.substring(i+1) );
        }
    }

    @Override
    public String typeId(Class clas) {
        return "datum";
    }

}
