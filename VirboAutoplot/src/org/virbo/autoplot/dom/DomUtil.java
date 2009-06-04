/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.beans.IndexedPropertyDescriptor;
import java.beans.PropertyDescriptor;
import java.beans.PropertyEditor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
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
import org.jdesktop.beansbinding.BeanProperty;
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

    /**
     * Either sets or gets the property at the expression.
     * Expressions like:
     *    timeRange
     *    plots[0].range
     * @param node
     * @param propertyName the value, (or the old value if we were setting it.)
     * @param getClass return the property class type instead of the value.
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

        return result; // TODO: when do we get here?
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
     * find the nodes matching this regex.
     * @param root
     * @param regex
     * @return
     */
    public static List<DomNode> findElementsById( DomNode root, String regex ) {
        Pattern p= Pattern.compile(regex);
        if (regex == null || regex.equals("")) {
            throw new IllegalArgumentException("id cannot be null or zero-length string");
        }
        List<DomNode> result= new ArrayList();
        if ( p.matcher(root.getId()).matches() ) result.add(root);
        for (DomNode n : root.childNodes()) {
            if ( p.matcher(n.getId()).matches() ) {
                result.add( n );
            }
            result.addAll( findElementsById( n, regex ) );
        }
        return result;
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
        } else if (diff instanceof ArrayNodeDiff) {
            ArrayNodeDiff d = (ArrayNodeDiff) diff;
            return new ArrayNodeDiff(childName + "." + d.propertyName, d.action, d.node, d.index, d.toIndex);
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

    /**
     * list the differences in two arrays of the same type of object.
     * This is the diffs that will make nodes2 look like nodes1.
     * Presently this just identies inserts and deletes.  If the objects
     * are DomNodes, then ids are used to match nodes.
     * @param property
     * @param node1
     * @param node2
     * @return
     */
    public static List<Diff> getArrayDiffs(String property, Object[] nodes1, Object[] nodes2) {
        List<Diff> result = new LinkedList<Diff>();

        List<Object> node1List = new ArrayList<Object>(Arrays.asList(nodes1));
        List<Object> node2List = new ArrayList<Object>(Arrays.asList(nodes2));

        List<Object> deleteList = new ArrayList<Object>();
        int i2=0;
        for ( Object o: nodes2 ) {
            if ( indexOf( node2List, o )!=i2 ) {
                deleteList.add( o );
                new IllegalArgumentException("two nodes have the same ID: "+o).printStackTrace();
            } // throw IllegalArgumentException("two nodes have the same ID: "+o);
            i2++;
        }
        for ( Object o: nodes2 ) {
            if ( indexOf( node1List, o )==-1 ) deleteList.add( o );
        }

        boolean isDomNode = DomNode.class.isAssignableFrom(nodes1.getClass().getComponentType());

        for (int i = 0; i < deleteList.size(); i++) {
            int idx = indexOf(node2List, deleteList.get(i));
            result.add(new ArrayNodeDiff(property, ArrayNodeDiff.Action.Delete, nodes2[idx], idx));
            node2List.remove(idx);
        }

        List<Object> addList = new ArrayList<Object>();
        for ( Object o: nodes1 ) {
            if ( indexOf( node2List, o )==-1 ) addList.add( o );
        }

        for (int i = 0; i < addList.size(); i++) {
            int idx=-1;
            idx = indexOf(node1List, addList.get(i));
            if (nodes1[idx] instanceof DomNode) {
                result.add(new ArrayNodeDiff(property, ArrayNodeDiff.Action.Insert, ((DomNode) nodes1[idx]).copy(), idx));
            } else {
                result.add(new ArrayNodeDiff(property, ArrayNodeDiff.Action.Insert, nodes1[idx], idx));
            }
            node2List.add(idx, nodes1[idx]);
        }

        //TODO: handle resort with Action.Move
        
        if (isDomNode) {
            for (int i = 0; i < node1List.size(); i++) {
                result.addAll(childDiffs(property + "[" + i + "]", getDiffs((DomNode)node1List.get(i), (DomNode)node2List.get(i))));
            }
        }

        return result;
    }

    /**
     * automatically detect the diffs between two DomNodes of the same type.
     * return the list of diffs that will make node2 look like node1.
     * The property "controller" is ignored.
     * @param node1
     * @param node2
     * @return
     */
    public static List<Diff> getDiffs(DomNode node1, DomNode node2) {
        return getDiffs(node1, node2, null);
    }

    /**
     *return the list of diffs that will make node2 look like node1.
     * @param node1
     * @param node2
     * @param exclude if non-null, exclude these properties.
     * @return
     */
    public static List<Diff> getDiffs(DomNode node1, DomNode node2, List<String> exclude) {
        String[] props = BeansUtil.getPropertyNames(node1.getClass());
        PropertyDescriptor[] pds = BeansUtil.getPropertyDescriptors(node1.getClass());

        List<Diff> diffs = new ArrayList<Diff>();
        for (int i = 0; i < props.length; i++) {
            if (props[i].equals("controller")) continue;
            if (exclude != null && exclude.contains(props[i])) continue;
            try {
                Object val1 = pds[i].getReadMethod().invoke(node1, new Object[0]);
                Object val2 = pds[i].getReadMethod().invoke(node2, new Object[0]);
                if (pds[i] instanceof IndexedPropertyDescriptor) {
                    diffs.addAll( getArrayDiffs(props[i], (DomNode[]) val1, (DomNode[]) val2) );
                } else if (DomNode.class.isAssignableFrom(pds[i].getReadMethod().getReturnType())) {
                    diffs.addAll( DomUtil.childDiffs(props[i], ((DomNode) val1).diffs((DomNode) val2)) );
                } else {
                    if (val1 != val2 && (val1 == null || !val1.equals(val2))) {
                        diffs.add(new PropertyChangeDiff(props[i], val2, val1));
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }

        }
        return diffs;
    }

    /**
     * sync node1 to node2 through introspection.  This only works for nodes
     * without controllers, etc.  It would be really nice to figure out how
     * to generalize this to include inserted nodes,etc.
     *
     * @param node1
     * @param node2
     * @return
     */
    public static void syncTo(DomNode node1, DomNode node2) {
        List<Diff> diffs = node2.diffs(node1);
        for (Diff d : diffs) {
            d.doDiff(node1);
        }
    }

    public static void syncTo(DomNode node1, DomNode node2, List<String> exclude) {
        List<Diff> diffs = node2.diffs(node1);
        for (Diff d : diffs) {
            if (!exclude.contains(d.propertyName())) d.doDiff(node1);
        }
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

}
