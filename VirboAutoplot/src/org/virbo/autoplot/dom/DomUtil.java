/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.beans.PropertyEditor;
import java.lang.Class;
import java.lang.Class;
import java.lang.Class;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    static List<String> childProperties(List<String> exclude, String string) {
        ArrayList<String> result= new ArrayList<String>();
        int n= string.length()+1;
        for ( String e: exclude ) {
            if ( e.startsWith(string+".") ) {
                result.add(e.substring(n));
            }
        }
        return result;
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
     * return the node with this id.
     * @param root
     * @param id
     * @return
     */
    public static DomNode getElementById( DomNode root, String id) {
        if ( id==null || id.equals("") ) {
            throw new IllegalArgumentException("id cannot be null or zero-length string");
        }
        if ( root.getId().equals(id) ) return root;
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
    
    /**
     * Just like Arrays.toList, but copies into ArrayList so elements may be inserted.
     * @param <T>
     * @param a
     * @return ArrayList that can have elements inserted
     */
    public static <T> ArrayList<T> asArrayList( T... a ) {
        return new ArrayList<T>( Arrays.asList( a ) );
    }
    
    /**
     * return the child diff in the context of the parent node.  May return
     * null if the diff cannot be described.
     * @param childName
     * @param diff
     * @return
     */
    public static Diff childDiff( String childName, Diff diff ) {
        if ( diff instanceof PropertyChangeDiff ) {
            PropertyChangeDiff pcd= (PropertyChangeDiff)diff;
            return new PropertyChangeDiff( childName+"."+pcd.propertyName, pcd.oldVal, pcd.newVal );
        } else if ( diff instanceof InsertNodeDiff ) {
            InsertNodeDiff d= (InsertNodeDiff)diff;
            return new InsertNodeDiff(childName+"."+d.propertyName, d.node, d.index);
        } else if ( diff instanceof DeleteNodeDiff ) {
            DeleteNodeDiff d= (DeleteNodeDiff)diff;
            return new DeleteNodeDiff(childName+"."+d.propertyName, d.node, d.index);
        } else {
            return null;
        }
    }
            /**
     * return the child diff in the context of the parent node.  May return
     * null if the diff cannot be described.
     * @param childName
     * @param diff
     * @return
     */
    public static List<Diff> childDiffs( String childName, List<Diff> diffs ) {
        ArrayList<Diff> result= new ArrayList<Diff>();
        for ( Diff diff: diffs ) {
            Diff r1= childDiff( childName, diff );
            if ( r1!=null ) result.add(r1);
        }
        return result;
    }
        
}
