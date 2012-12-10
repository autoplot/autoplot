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
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
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
import org.virbo.autoplot.dom.ChangesSupport.DomLock;

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
     * return a list of nodes (plotElements and dataSourceFilters) that use the DataSourceFilter.
     * @param app
     * @param plotId
     * @return
     */
    static List<DomNode> dataSourceUsages(Application app, String id) {
        List<DomNode> result = new ArrayList<DomNode>();
        for (PlotElement p : app.getPlotElements()) {
            if (p.getDataSourceFilterId().equals(id)) {
                result.add(p);
            }
        }
        for (DataSourceFilter dsf : app.getDataSourceFilters()) {
            for (DataSourceFilter dsfp : dsf.getController().getParentSources()) {
                if ( dsfp==null ) continue;
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

        if ( !setit && result==null ) throw new IllegalArgumentException( "unable to find property \""+propertyName+"\" in "+node );
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
            return "" + round(init) + " \u2192 " + round(fin);
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
        if (id == null ) {
            throw new IllegalArgumentException("id cannot be null");
        }
        if ( id.equals("") ) {
            throw new IllegalArgumentException("id be zero-length string");
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
            result.add(new ArrayNodeDiff(property, ArrayNodeDiff.Action.Delete, node2List.get(idx), idx));
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

    /**
     * returns true if all the plotElements are a parent and its children.
     * @param elementsIn
     * @return
     */
    public static boolean oneFamily( List<PlotElement> elementsIn ) {
        if ( elementsIn.size()==0 ) return false;
        List<PlotElement> elements= new ArrayList(elementsIn);
        PlotElement pe= elements.get(0);
        if ( pe.getController().getParentPlotElement()!=null ) {
            pe= pe.getController().getParentPlotElement();
        }
        elements.remove(pe);
        elements.removeAll( pe.getController().getChildPlotElements() );

        if ( elements.size()==0 ) return true; else return false;

    }

    /**
     * returns true if the dom is valid, throws a runtime exception otherwise
     * @param dom
     * @return
     */
    public static boolean validateDom( Application application, List<String> problems ) {

        DomLock lock=null;

        if ( application.getController()!=null ) {
           lock= application.getController().mutatorLock();
           lock.lock("Validate DOM");
        }

        try {
            for ( int i=0; i<application.getBindings().length; i++ ) {
                BindingModel b= application.getBindings(i);
                if ( getElementById( application, b.getSrcId() )==null ) {
                    problems.add("unable to find source "+b.getSrcId()+" for binding "+i );
                }
                if ( getElementById( application, b.getDstId() )==null ) {
                    problems.add("unable to find dest "+b.getSrcId()+" for binding "+i );
                }
            }

            for ( int i=0; i<application.getDataSourceFilters().length; i++ ) {
                DataSourceFilter dsf= application.getDataSourceFilters(i);
                String uri= dsf.getUri();
                if ( uri==null ) continue;
                if ( uri.startsWith("vap+internal:") && uri.length()>13 ) {
                    String[] dep= uri.substring(13).split(",");
                    for ( int j=0; j<dep.length; j++ ) {
                        if ( getElementById( application,dep[j])==null )
                            problems.add("unable to find dsf "+dep[j]+" for dsf "+dsf.getId() );
                    }
                }
            }

            for ( int i=0; i<application.getPlotElements().length; i++ ) {
                PlotElement p= application.getPlotElements(i);
                if ( getElementById(application, p.getPlotId() )==null )
                    problems.add("unable to find plot "+p.getPlotId()+" for plot element "+p.getId() );
                if ( getElementById(application, p.getDataSourceFilterId() )==null )
                    problems.add("unable to find data "+p.getDataSourceFilterId()+" for plot element "+p.getId());
            }


            for ( int i=0; i<application.getPlots().length; i++ ) {
                Plot p= application.getPlots(i);
                if ( getElementById(application, p.getRowId() )==null )
                    problems.add("unable to find row "+p.getRowId()+" for plot "+p.getId() );
                if ( getElementById(application, p.getColumnId() )==null )
                    problems.add("unable to find column "+p.getColumnId()+" for plot  "+p.getId());
            }

        } finally {
            if ( lock!=null ) {
                lock.unlock();
            }
        }
        
        return problems.size()==0;
    }

    /**
     * returns true if the dom structure changes.  For example the number of
     * plotElements changes, then this returns true.  If only the range of
     * an axis changes, then return false;
     * 2012-01-11: check axis units after failure in test002_003 showed that old dataset was used for autoranging.
     * @param dom
     * @param state
     * @return true if the dom structure changes.
     */
    public static boolean structureChanges(Application dom, Application state) {
        if ( dom.bindings.size()!=state.bindings.size() ) return true;
        if ( dom.connectors.size()!=state.connectors.size() ) return true;
        if ( dom.dataSourceFilters.size()!=state.dataSourceFilters.size() ) return true;
        if ( dom.plots.size()!=state.plots.size() ) return true;
        if ( dom.plotElements.size()!=state.plotElements.size() ) return true;
        for ( int i=0; i<dom.plots.size(); i++ ) {
            Plot pd= dom.plots.get(i);
            Plot ps= state.plots.get(i);
            if ( ! pd.getXaxis().getRange().getUnits().isConvertableTo( ps.getXaxis().getRange().getUnits() ) ) return true;
            if ( ! pd.getYaxis().getRange().getUnits().isConvertableTo( ps.getYaxis().getRange().getUnits() ) ) return true;
            if ( ! pd.getZaxis().getRange().getUnits().isConvertableTo( ps.getZaxis().getRange().getUnits() ) ) return true;
        }
        return false;
    }

    /**
     * This does not use controllers.
     * @param application
     * @param plot
     * @return
     */
    public static List<PlotElement> getPlotElementsFor( Application application, Plot plot ) {
        String id = plot.getId();
        List<PlotElement> result = new ArrayList<PlotElement>();
        for (PlotElement p : application.getPlotElements()) {
            if (p.getPlotId().equals(id)) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * allow verification that the node has a property.  I killed an hour with a 
     * bug where I was using "timerange" instead of "timeRange"...
     * Always use the constants: Application.PROP_TIMERANGE
     * @param node1
     * @param property
     * @return
     */
    public static boolean nodeHasProperty(DomNode node1, String property) {
        String[] props = BeansUtil.getPropertyNames(node1.getClass());
        PropertyDescriptor[] pds = BeansUtil.getPropertyDescriptors(node1.getClass());
        for ( int i=0; i<props.length; i++ ) {
            if ( props[i].equals(property) ) return true;
        }
        return false;
    }


    /**
     * plugs values from USER_PROPERTIES or METADATA into template string.
     * @param template template, for example the title or label.
     * @param root USER_PROPERTIES, METADATA, etc.
     * @param props properties tree.
     * @return
     */
    public static String resolveProperties( String template, String root, Map<String,Object> props ) {
        try {
            int i= template.indexOf("%{"+root+".");
            int n= root.length()+3;
            while ( i>-1 ) {
                int i2= template.indexOf("}",i);
                String propName= template.substring(i+n,i2);
                int i3= propName.indexOf(".");
                Map<String,Object> props1= props;
                while ( i3>-1 ) {
                    String propName1= propName.substring(0,i3);
                    props1= (Map<String, Object>) props.get(propName1);
                    propName= propName1;
                    i3= propName.indexOf(".");
                }
                String prop= String.valueOf( props1.get(propName) );
                template= template.substring(0,i) + prop + template.substring(i2+1);
                i= template.indexOf("%{"+root+".",i);
            }
        } catch ( Exception ex ) {
            ex.printStackTrace();
        }
        return template;
    }

}
