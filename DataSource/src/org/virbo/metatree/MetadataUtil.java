/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.metatree;

import java.text.ParseException;
import org.das2.datum.NumberUnits;
import org.das2.datum.Units;
import java.util.LinkedHashMap;
import java.util.Map;
import org.das2.datum.Basis;
import org.das2.datum.Datum;
import org.das2.datum.TimeLocationUnits;
import org.das2.datum.TimeUtil;
import org.das2.datum.UnitsConverter.ScaleOffset;
import org.das2.datum.UnitsUtil;
import org.w3c.dom.Node;

/**
 *
 * @author jbf
 */
public class MetadataUtil {
    /**
     * lookupUnits canonical units object, or allocate one.
     * @param units string identifier.
     * @return canonical units object.
     */
    public static synchronized Units lookupUnits(String units) {
        Units result;
        try {
            result= Units.getByName(units);
        } catch ( IllegalArgumentException ex ) {
            if ( units.equals("sec") ) {   // begin, giant table of kludges
                result= Units.seconds;
            } else if ( units.equals("msec") ) {  // CDF
                result= Units.milliseconds;
            } else {
                result= new NumberUnits( units );
            }
        }
        return result;
    }
    
    /**
     * return canonical das2 unit for colloquial time.
     * @param string
     * @return
     */
    public static Units lookupTimeLengthUnit(String s) throws ParseException {
        s= s.toLowerCase().trim();
        if ( s.startsWith("sec") ) {
            return Units.seconds;
        } else if ( s.startsWith("ms") || s.startsWith("millisec") ) {
            return Units.milliseconds;
        } else if ( s.equals("hr") || s.startsWith("hour") ) {
            return Units.hours;
        } else if ( s.equals("mn") || s.startsWith("min") ) {
            return Units.minutes;
        } else if ( s.startsWith("us") || s.startsWith("\u00B5s" ) || s.startsWith("micros")) {
            return Units.microseconds;
        } else if ( s.startsWith("ns") || s.startsWith("nanos" ) ) {
            return Units.nanoseconds;
        } else if ( s.startsWith("d") ) {
            return Units.days;
        } else {
            throw new ParseException("failed to identify unit: "+s,0);
        }
    }
    
    /**
     * lookupUnits canonical units object, or allocate one.  If one is
     * allocated, then parse for "<unit> since <datum>"
     * @param timeUnits
     * @return
     */
    public static synchronized Units lookupTimeUnits( String units ) throws ParseException {
        Units result;
        try {
            result= Units.getByName(units);
            return result;
        } catch ( IllegalArgumentException ex ) {
            String[] ss= units.split("since");
            String soffsetUnits= ss[0];
            Units offsetUnits= lookupTimeLengthUnit(ss[0]);
            Datum datum= TimeUtil.create(ss[1]);
            String canonicalName = "" + offsetUnits + " since "+ datum;
            Basis basis= new Basis( "since "+ datum, "since "+ datum, Basis.since2000, datum.doubleValue(Units.us2000), Units.us2000.getOffsetUnits() );
            result= new TimeLocationUnits( canonicalName, canonicalName, offsetUnits, basis );
            result.registerConverter( Units.us2000, 
                    new ScaleOffset( 
                    offsetUnits.convertDoubleTo(Units.microseconds, 1.0), 
                    datum.doubleValue(Units.us2000) ) );
            return result;
        }
    }
    
    /**
     * converts tree model node into canonical Map<String,Object>.  Branch nodes
     * are HashMap<String,Object> as well.
     * @param node
     * @return
     */
    public static Map<String,Object> toMetaTree( Node node ) {
        Map<String,Object> result= new LinkedHashMap<String,Object>();
        Node child= node.getFirstChild();
        while ( child!=null ) {
            Object value;
            if ( child.hasChildNodes() ) {
                value= toMetaTree( child );
            } else {
                value= child.getNodeValue();
            }
            result.put( child.getNodeName(), value );
            child= child.getNextSibling();
        }
        return result;
    }
    
    public static Node getNode( Node node, String[] path ) {
        int i=0;
        Node child= node.getFirstChild();
        while ( i<path.length ) {
            if ( child==null ) throw new IllegalArgumentException("couldn't find node");
            Object value;
            if ( child.getNodeName().equals( path[i] ) ) {
                node= child;
                child= node.getFirstChild();
                i++;
            } else {
                child= child.getNextSibling();
            }
        }
        return node;
    }
    
    /**
     * returns the object at the path, or null if the path position doesn't exist.
     * @param tree
     * @param path
     * @return Object at the node, if it exists.
     */
    public static Object getNode( Map<String,Object> tree, String[] path ) {
        int i=0;
        Object child= tree.get( path[0] );
        i++;
        while ( i<path.length ) {
            if ( child==null ) return null;
            if ( !(child instanceof Map) ) return null;
            Object value;
            
            child= ((Map)child).get( path[i] );
            i++;
        }
        return child;
    }
    
}
