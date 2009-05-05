/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.beans.IndexedPropertyDescriptor;
import java.beans.PropertyDescriptor;
import java.beans.PropertyEditor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.das2.beans.BeansUtil;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumUtil;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.jdesktop.beansbinding.BeanProperty;

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
     * return a list of nodes (panels and dataSourceFilters) that use the DataSourceFilter.
     * @param app
     * @param plotId
     * @return
     */
    static List<DomNode> dataSourceUsages(Application app, String id) {
        List<DomNode> result = new ArrayList<DomNode>();
        for (Panel p : app.getPanels()) {
            if (p.getDataSourceFilterId().equals(id)) {
                result.add(p);
            }
        }
        for (DataSourceFilter dsf : app.getDataSourceFilters()) {
            for (DataSourceFilter dsfp : dsf.getController().getParentSources()) {
                if (dsfp.getId().equals(id)) {
                    result.add(dsf);
                }
            }
        }
        return result;
    }

    static boolean hasProperty(DomNode result, String propertyName) {
        return BeanProperty.create(propertyName).isReadable(result);
    }

    static void setProperty( DomNode result, String propertyName, Object value ) {
        BeanProperty.create(propertyName).setValue( result, value );
    }

    /**
     * return the list of nodes (Plots) that is this row.
     */
    static List<DomNode> rowUsages( Application app, String rowId ) {
        List<DomNode> result = new ArrayList<DomNode>();
        for ( Plot p: app.getPlots() ) {
            if ( p.getRowId().equals(rowId) ) {
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
     * return the node with this id, or null if the id is not found.
     * @param root
     * @param id
     * @return
     */
    public static DomNode getElementById(DomNode root, String id) {
        if (id == null || id.equals("")) {
            throw new IllegalArgumentException("id cannot be null or zero-length string");
        }
        if (root.getId().equals(id)) return root;
        for (DomNode n : root.childNodes()) {
            if (n.getId().equals(id)) {
                return n;
            } else {
                DomNode nn = getElementById(n, id);
                if (nn != null) return nn;
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
    public static <T> ArrayList<T> asArrayList(T... a) {
        return new ArrayList<T>(Arrays.asList(a));
    }

    /**
     * return the child diff in the context of the parent node.  May return
     * null if the diff cannot be described.
     * @param childName
     * @param diff
     * @return
     */
    public static Diff childDiff(String childName, Diff diff) {
        if (diff instanceof PropertyChangeDiff) {
            PropertyChangeDiff pcd = (PropertyChangeDiff) diff;
            return new PropertyChangeDiff(childName + "." + pcd.propertyName, pcd.oldVal, pcd.newVal);
        } else if (diff instanceof ArrayNodeDiff ) {
            ArrayNodeDiff d = (ArrayNodeDiff) diff;
            return new ArrayNodeDiff( childName + "." + d.propertyName, d.action, d.node, d.index, d.toIndex );
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
    public static List<Diff> childDiffs(String childName, List<Diff> diffs) {
        ArrayList<Diff> result = new ArrayList<Diff>();
        for (Diff diff : diffs) {
            Diff r1 = childDiff(childName, diff);
            if (r1 != null) result.add(r1);
        }
        return result;
    }

    /**
     * returns the index by ID, not by equals.  Equals cannot be 
     * overriden, because property change diffs, etc.
     * @param nodes
     * @param node
     * @return the index or -1.
     */
    public static int indexOf( List<DomNode> nodes, DomNode node ) {
        for ( int i=0; i<nodes.size(); i++ ) {
            DomNode n1= nodes.get(i);
            if ( n1==node ) return i;
            String id= n1.getId();
            if ( !id.equals("") && id.equals(node.id) ) {
                return i;
            }
        }
        return -1;
    }

    /**
     * list the differences in two arrays of the same type of object.
     * Presently this just identies inserts and deletes.
     * @param property
     * @param node1
     * @param node2
     * @return
     */
    public static List<Diff> getArrayDiffs(String property, DomNode[] nodes1, DomNode[] nodes2) {
        List<Diff> result = new LinkedList<Diff>();

        int i1= 0;
        int i2= 0;

        List<DomNode> deleteList= new ArrayList<DomNode>( Arrays.asList(nodes1) );
        deleteList.removeAll(Arrays.asList(nodes2));

        List<DomNode> node1List= new ArrayList<DomNode>( Arrays.asList(nodes1) );
        List<DomNode> node2List= new ArrayList<DomNode>( Arrays.asList(nodes2) );
        
        for ( int i=0; i<deleteList.size(); i++ ) {
            int idx= indexOf( node1List, deleteList.get(i) );
            result.add( new ArrayNodeDiff( property, ArrayNodeDiff.Action.Delete, nodes1[idx].copy(), idx ) );
            node1List.remove(idx);
        }

        List<DomNode> addList= new ArrayList<DomNode>( Arrays.asList(nodes2) );
        addList.removeAll(Arrays.asList(nodes1));

        for ( int i=0; i<addList.size(); i++ ) {
            int idx= indexOf( node2List, addList.get(i) );
            result.add( new ArrayNodeDiff( property, ArrayNodeDiff.Action.Insert, nodes2[idx].copy(), idx ) );
            node1List.add(idx,nodes2[idx]);
        }

        for ( int i=0; i<node1List.size(); i++ ) {
            result.addAll( childDiffs( property+"["+i+"]", getDiffs( node1List.get(i), nodes2[i] ) ) );
        }
        
        return result;
    }

    /**
     * automatically detect the diffs between two DomNodes of the same type.
     * The property "controller" is ignored.
     * @param node1
     * @param node2
     * @return
     */
    public static List<Diff> getDiffs(DomNode node1, DomNode node2) {
        return getDiffs( node1, node2, null );
    }

    /**
     *
     * @param node1
     * @param node2
     * @param exclude if non-null, exclude these properties.
     * @return
     */
    public static List<Diff> getDiffs( DomNode node1, DomNode node2, List<String> exclude) {
        String[] props = BeansUtil.getPropertyNames(node1.getClass());
        PropertyDescriptor[] pds= BeansUtil.getPropertyDescriptors(node1.getClass());

        List<Diff> diffs = new ArrayList<Diff>();
        for (int i = 0; i < props.length; i++) {
            if ( props[i].equals("controller") ) continue;
            if ( exclude!=null && exclude.contains(props[i]) ) continue;
            try {
                Object val1 = pds[i].getReadMethod().invoke(node1, new Object[0] );
                Object val2 = pds[i].getReadMethod().invoke(node2, new Object[0] );
                if ( pds[i] instanceof IndexedPropertyDescriptor ) {
                    diffs.addAll( getArrayDiffs(props[i], (DomNode[]) val1, (DomNode[]) val2) );
                } else if ( DomNode.class.isAssignableFrom( pds[i].getReadMethod().getReturnType() ) ) {
                    diffs.addAll( DomUtil.childDiffs( props[i], ((DomNode)val1).diffs((DomNode)val2) ) );
                } else {
                    if ( val1!=val2 && ( val1==null || !val1.equals(val2) ) ) {
                        diffs.add(new PropertyChangeDiff(props[i], val1, val2));
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }

        }
        return diffs;
    }

    /**
     * sync node1 to node2 through introspection.
     * @param node1
     * @param node2
     * @return
     */
    public static void syncTo(DomNode node1, DomNode node2) {
        List<Diff> diffs = node1.diffs(node2);
        for (Diff d : diffs) {
            d.doDiff(node1);
        }
    }

    public static void syncTo(DomNode node1, DomNode node2, List<String> exclude) {
        List<Diff> diffs = node1.diffs(node2);
        for (Diff d : diffs) {
            if ( !exclude.contains( d.propertyName() ) ) d.doDiff(node1);
        }
    }

}
