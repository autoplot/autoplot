/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.awt.Color;
import java.beans.IndexedPropertyDescriptor;
import java.beans.PropertyDescriptor;
import java.beans.PropertyEditor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.beans.BeansUtil;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumUtil;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;

import org.jdesktop.beansbinding.Converter;

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
    public static String abbreviateRight(String s, int len) {
        if (s == null) return "<null>";
        if (s.length() > len) {
            s = "..." + s.substring(s.length() - len);
        }
        return s;
    }

    static List<String> childProperties(List<String> exclude, String string) {
        ArrayList<String> result = new ArrayList<String>();
        int n = string.length() + 1;
        for (String e : exclude) {
            if (e.startsWith(string + ".")) {
                result.add(e.substring(n));
            }
        }
        return result;
    }

    /**
     * Either sets or gets the property at the expression.
     * Expressions like:
     *    timeRange
     *    plots[0].range
     * @param node
     * @param propertyName the value, (or the old value if we were setting it.)
     * @param getClass return the property class type instead of the value.
     * @throws IllegalArgumentException if the property cannot be found
     * @return propertyDescriptor or null.
     */
    private static Object setGetPropertyInt( DomNode node, String propertyName, boolean setit, boolean getClass, Object value ) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        String[] props= propertyName.split("\\.",-2);
        int iprop=0;
        Pattern indexedPattern= Pattern.compile("([a-zA-Z_]+)\\[(\\d+)\\]");
        Object thisNode= node;
        PropertyDescriptor result=null;
        while ( iprop< props.length ) {
            String prop1= props[iprop];
            Matcher m= indexedPattern.matcher(prop1);
            PropertyDescriptor[] pds= BeansUtil.getPropertyDescriptors(thisNode.getClass());
            PropertyDescriptor prop;
            if ( m.matches() ) {
                String name= m.group(1);
                int idx= Integer.valueOf(m.group(2));
                for ( int i=0; i<pds.length; i++ ) {
                    if ( pds[i].getName().equals(name) ) {
                        Object thisValue= ((IndexedPropertyDescriptor)pds[i]).getIndexedReadMethod().invoke( thisNode, idx );
                        if ( iprop==props.length-1 ) {
                            if ( setit ) {
                                ((IndexedPropertyDescriptor)pds[i]).getIndexedWriteMethod().invoke( thisNode, idx, value );
                            } else if ( getClass ) {
                                return ((IndexedPropertyDescriptor)pds[i]).getPropertyType();
                            } else {
                                return thisValue;
                            }
                        }
                        thisNode= thisValue;
                        break;
                    }
                }
            } else {
                String name= prop1;
                for ( int i=0; i<pds.length; i++ ) {
                    if ( pds[i].getName().equals(name) ) {
                        Object thisValue= (pds[i]).getReadMethod().invoke( thisNode );
                        if ( iprop==props.length-1 ) {
                            if ( setit ) {
                                (pds[i]).getWriteMethod().invoke( thisNode, value );
                            } else if ( getClass ) {
                                return (pds[i]).getPropertyType();
                            } else {
                                return thisValue;
                            }
                        }
                        thisNode= thisValue;
                        break;
                    }
                }
            }
            iprop++;
        }

        if ( result==null ) throw new IllegalArgumentException( "unable to find property \""+propertyName+"\" in "+node );
        return result;
    }

    public static Object getPropertyValue(DomNode node, String propertyName) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        return setGetPropertyInt( node, propertyName, false,false, null );
    }

    public static void setPropertyValue(DomNode node, String propertyName, Object val ) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        setGetPropertyInt( node, propertyName, true,false, val );
    }

    public static Class getPropertyType( DomNode node, String propertyName ) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        return (Class)setGetPropertyInt( node, propertyName, false, true, null );
    }

    /**
     * return the list of nodes (Plots) that is this row.
     */
    static List<DomNode> rowUsages(Application app, String rowId) {
        List<DomNode> result = new ArrayList<DomNode>();
        for (Plot p : app.getPlots()) {
            if (p.getRowId().equals(rowId)) {
                result.add(p);
            }
        }
        return result;
    }

    private static DatumRange round(DatumRange range) {
        Datum w = range.width();
        String s;
        double d;
        Datum w0 = DatumUtil.asOrderOneUnits(w);
        Datum base = w0;
        Units hu = w0.getUnits();
        if (range.getUnits().isConvertableTo(Units.us2000)) {
            base = TimeUtil.prevMidnight(range.min());
        } else {
            base = w.getUnits().createDatum(0);
        }
        double min10 = Math.round((range.min().subtract(base)).doubleValue(w0.getUnits()));
        double max10 = Math.round((range.max().subtract(base)).doubleValue(w0.getUnits()));
        return new DatumRange(base.add(Datum.create(min10, hu)), base.add(Datum.create(max10, hu)));
    }

    public static String describe(DatumRange init, DatumRange fin) {
        if (init.getUnits().isConvertableTo(fin.getUnits())) {
            String scaleString = "";
            if (UnitsUtil.isTimeLocation(fin.getUnits())) {
                Datum scale = DatumUtil.asOrderOneUnits(round(fin).width());
                scaleString = " to " + scale;
            }

            if (init.contains(fin)) {
                return "zoom in" + scaleString;
            } else if (fin.contains(init)) {
                return "zoom out" + scaleString;
            } else if (init.intersects(fin)) {
                return "pan"; //+ ( init.min().lt(fin.min() ) ? "right" : "left" ); duh--need to know axis orientation
            } else {
                return "scan"; // + ( init.min().lt(fin.min() ) ? "right" : "left" );
            }
        } else {
            return "" + round(init) + " -> " + round(fin);
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
     * returns the index by ID, not by equals.  Equals cannot be 
     * overriden, because property change diffs, etc.
     * @param nodes
     * @param node
     * @return the index or -1.
     */
    public static int indexOf(List<Object> nodes, Object node) {
        boolean isDomNode = node instanceof DomNode;
        if (!isDomNode) {
            return nodes.indexOf(node);
        } else {
            for (int i = 0; i < nodes.size(); i++) {
                DomNode n1 = (DomNode) nodes.get(i);
                if (n1 == node) return i;
                String id = n1.getId();
                if (!id.equals("") && id.equals(((DomNode) node).id)) {
                    return i;
                }
            }
        }
        return -1;
    }


    public static String encodeColor(java.awt.Color c) {
        return "#" + Integer.toHexString(c.getRGB() & 0xFFFFFF);
    }

    public static String encodeFont(java.awt.Font f) {
        String style="-";
        if ( f.isBold() ) style+="bold";
        if ( f.isItalic() ) style+="italic";
        String result= f.getFamily();
        if ( style.length()>1 ) result+= style;
        return result + "-" + f.getSize();
    }

    public static final Converter STRING_TO_FONT= new Converter() {
        @Override
        public Object convertForward(Object value) {
            return java.awt.Font.decode((String)value);
        }

        @Override
        public Object convertReverse(Object value) {
            return encodeFont((java.awt.Font)value);
        }
    };

    public static final Converter STRING_TO_COLOR= new Converter() {
        @Override
        public Object convertForward(Object value) {
            return java.awt.Color.decode((String)value);
        }

        @Override
        public Object convertReverse(Object value) {
            return encodeColor( (java.awt.Color)value);
        }
    };

    public static final Converter AUTO_TO_COLOR= new Converter() {
        @Override
        public Object convertForward(Object value) {
            boolean b= ((Boolean)value).booleanValue();
            return b ? Color.WHITE : Color.LIGHT_GRAY;
        }

        @Override
        public Object convertReverse(Object value) {
            return Color.WHITE.equals(value);
        }
    };

}
