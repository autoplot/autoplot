/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.beans.PropertyEditor;
import javax.beans.binding.Binding;
import javax.beans.binding.BindingContext;
import org.das2.beans.BeansUtil;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumUtil;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;

/**
 *
 * @author jbf
 */
public class DomUtil {
    /**
     * trim the string on the left, leaving the right visible.
     * @param s
     * @return "..."+s.substring()
     */
    public static String abbreviateRight( String s, int len ) {
        if ( s==null ) return "<null>";
        if ( s.length()>len ) {
            s= "..."+s.substring(s.length()-len);
        }
        return s;
    }

    private static DatumRange round( DatumRange range ) {
        Datum w= range.width();
        String s;
        double d;
        Datum w0= DatumUtil.asOrderOneUnits(w);
        Datum base= w0;
        Units hu= w0.getUnits();
        if ( range.getUnits().isConvertableTo(Units.us2000) ) {
            base= TimeUtil.prevMidnight(range.min());
        } else {
            base= w.getUnits().createDatum(0);
        }
        double min10= Math.round( ( range.min().subtract(base) ).doubleValue(w0.getUnits()));
        double max10= Math.round( ( range.max().subtract(base) ).doubleValue(w0.getUnits()));
        return new DatumRange( base.add( Datum.create( min10, hu ) ), base.add( Datum.create(max10,hu) ) );
    }
    
    public static String describe( DatumRange init, DatumRange fin ) {
        if ( init.getUnits().isConvertableTo( fin.getUnits() ) ) {
            String scaleString="";
            if ( UnitsUtil.isTimeLocation(fin.getUnits() ) ) {
                Datum scale= DatumUtil.asOrderOneUnits( round(fin).width() );
                scaleString= " to "+scale;
            }

            if ( init.contains(fin) ) {
                return "zoom in"+ scaleString;
            } else if ( fin.contains(init) ) {
                return "zoom out"+ scaleString;
            } else if ( init.intersects(fin) ) {
                return "pan"; //+ ( init.min().lt(fin.min() ) ? "right" : "left" ); duh--need to know axis orientation
            } else {
                return "scan"; // + ( init.min().lt(fin.min() ) ? "right" : "left" );
            }
        } else {
            return ""+round(init)+" -> "+ round(fin);
        }


    }

    public static Object parseObject(Object context, String s) {
        PropertyEditor edit = BeansUtil.findEditor(context.getClass());
        if (edit == null) {
            return context;
        }

        edit.setValue(context);
        edit.setAsText(s);
        Object result = edit.getValue();
        return result;
    }

    public static String formatObject(Object obj) {
        PropertyEditor edit = BeansUtil.findEditor(obj.getClass());
        if (edit == null) {
            return "";
        }

        edit.setValue(obj);
        String result = edit.getAsText();
        return result;
    }
    
    /** 
     * add binding, possibly validating 
     * @param bc
     * @param model
     * @param propertyName expression like ${name} or just name.
     * @param bound
     * @param boundPropertyName
     * @return
     */
    public static Binding addBinding( BindingContext bc, Object model, String propertyName, Object bound, String boundPropertyName ) {
        if ( !propertyName.startsWith("${" ) ) {
            propertyName= "${" + propertyName + "}";
        }
        return bc.addBinding( model, propertyName, bound, boundPropertyName );
    }
    
    public static DomNode getElementById( DomNode root, String id) {
        for ( DomNode n: root.childNodes() ) {
            if ( n.getId().equals(id) ) {
                return n;
            } else {
                DomNode nn=  getElementById(n,id) ;
                if ( nn!=null ) return nn;
            } 
        }
        return null;
    }
}
