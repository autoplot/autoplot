
package org.autoplot.dom;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.beans.IndexedPropertyDescriptor;
import java.beans.IntrospectionException;
import java.beans.PropertyChangeSupport;
import java.beans.PropertyDescriptor;
import java.beans.PropertyEditor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.beans.BeansUtil;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumUtil;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.util.LoggerManager;
import org.jdesktop.beansbinding.Converter;
import org.autoplot.dom.ChangesSupport.DomLock;
import org.autoplot.state.StatePersistence;
import org.das2.components.propertyeditor.Displayable;
import org.das2.graph.DasColumn;
import org.das2.graph.DasDevicePosition;
import org.das2.qds.QDataSet;
import org.das2.util.ColorUtil;

/**
 * operations for the DOM, such as search-for-node and child properties
 * @author jbf
 */
public class DomUtil {

    private static final Logger logger= LoggerManager.getLogger( "autoplot.dom.util");
    
    /**
     * trim the string on the left, leaving the right visible.
     * @param s the string
     * @param len the number of characters
     * @return "..."+s.substring(s.length() - len)
     */
    public static String abbreviateRight(String s, int len) {
        if (s == null) return "<null>";
        if (s.length() > len) {
            s = "..." + s.substring(s.length() - len);
        }
        return s;
    }

    static List<String> childProperties(List<String> exclude, String string) {
        ArrayList<String> result = new ArrayList<>();
        int n = string.length() + 1;
        for (String e : exclude) {
            if (e.startsWith(string + ".")) {
                result.add(e.substring(n));
            }
        }
        return result;
    }

    /**
     * return a list of nodes (plotElements and dataSourceFilters) that use the 
     * DataSourceFilter. 
     * @param app the dom
     * @param id the node identifier
     * @return
     */
    static List<DomNode> dataSourceUsages(Application app, String id) {
        List<DomNode> result = new ArrayList<>();
        for (PlotElement p : app.getPlotElements()) {
            if (p.getDataSourceFilterId().equals(id)) {
                result.add(p);
            }
        }
        for (DataSourceFilter dsf : app.getDataSourceFilters()) {
            if ( dsf.getUri().startsWith("vap+internal:") ) {
                String[] ss= dsf.getUri().substring(13).split(",",-2);
                for ( String s: ss ) {
                    if ( s.equals(id) && !result.contains(dsf) ) {
                        result.add(dsf);
                    }
                }
            }
        }
        return result;
    }

    /**
     * move the plot element within the dom below the plot element.
     * @param dom the dom (with no controllers)
     * @param referenceId the plot element which is the reference
     * @param pId the plot element to move
     */
    public static void moveToJustBelow( Application dom, String referenceId, String pId ) {
        PlotElement reference= (PlotElement) getElementById( dom, referenceId );
        PlotElement p= (PlotElement) getElementById( dom, pId );
        
        if ( reference==p ) throw new IllegalArgumentException("reference and p are the same plot element");
        if ( !reference.getPlotId().equals(p.getPlotId()) ) throw new IllegalArgumentException("reference and p must be in the same plot");
            
        List<PlotElement> newPes= new ArrayList( Arrays.asList(dom.getPlotElements()) );
            
        // find the topmost element of plot.
        int top;
        for ( top=newPes.size()-1; top>=0; top-- ) {
            if ( newPes.get(top)==reference ) break;
        }

        int ploc;
        for ( ploc=0; ploc<newPes.size(); ploc++ ) {
            if ( newPes.get(ploc)==p ) break;
        }

        newPes.remove(p);
        newPes.add( top-1, p );

        dom.setPlotElements(newPes.toArray( new PlotElement[newPes.size()] ));

    }
    
    /**
     * move the plot element within the dom below the plot element.
     * @param dom the dom (with no controllers)
     * @param referenceId the plot element which is the reference
     * @param pId the plot element to move
     */
    public static void moveToJustAbove( Application dom, String referenceId, String pId ) {
        PlotElement reference= (PlotElement) getElementById( dom, referenceId );
        PlotElement p= (PlotElement) getElementById( dom, pId );
        if ( reference==p ) throw new IllegalArgumentException("reference and p are the same plot element");
        if ( !reference.getPlotId().equals(p.getPlotId()) ) throw new IllegalArgumentException("reference and p must be in the same plot");
            
        List<PlotElement> newPes= new ArrayList( Arrays.asList(dom.getPlotElements()) );
            
        // find the topmost element of plot.
        int top;
        for ( top=newPes.size()-1; top>=0; top-- ) {
            if ( newPes.get(top)==reference ) break;
        }

        int ploc;
        for ( ploc=0; ploc<newPes.size(); ploc++ ) {
            if ( newPes.get(ploc)==p ) break;
        }

        newPes.remove(p);
        if ( ploc<top ) {
            newPes.add( top, p );
        } else {
            newPes.add( top+1, p );
        }

        dom.setPlotElements(newPes.toArray( new PlotElement[newPes.size()] ));

    }
    
    /**
     * Either sets or gets the property at the expression.
     * Expressions like:
     *<blockquote><pre><small>{@code
     *    timeRange
     *    plots[0].range
     *}</small></pre></blockquote>
     * 
     * @param node the node containing the property
     * @param propertyName the value, (or the old value if we were setting it.)
     * @param getClass return the property class type instead of the value.
     * @throws IllegalArgumentException if the property cannot be found
     * @return propertyDescriptor or null.
     */
    private static Object setGetPropertyInternal( DomNode node, String propertyName, boolean setit, boolean getClass, Object value ) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        String[] props= propertyName.split("\\.",-2);
        int iprop=0;
        Pattern indexedPattern= Pattern.compile("([a-zA-Z_]+)\\[(\\d+)\\]");
        Object thisNode= node;
        
        while ( iprop< props.length ) {
            String prop1= props[iprop];
            Matcher m= indexedPattern.matcher(prop1);
            PropertyDescriptor[] pds= BeansUtil.getPropertyDescriptors(thisNode.getClass());
            if ( m.matches() ) {
                String name= m.group(1);
                int idx= Integer.parseInt(m.group(2));
                for (PropertyDescriptor pd : pds) {
                    if (pd.getName().equals(name)) {
                        Object thisValue = ((IndexedPropertyDescriptor) pd).getIndexedReadMethod().invoke(thisNode, idx);
                        if (iprop==props.length-1) {
                            if (setit) {
                                ((IndexedPropertyDescriptor) pd).getIndexedWriteMethod().invoke(thisNode, idx, value);
                            } else if (getClass) {
                                return ((IndexedPropertyDescriptor) pd).getPropertyType();
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
                for (PropertyDescriptor pd : pds) {
                    if (pd.getName().equals(name)) {
                        Object thisValue = (pd).getReadMethod().invoke(thisNode);
                        if (iprop==props.length-1) {
                            if (setit) {
                                (pd).getWriteMethod().invoke(thisNode, value);
                            } else if (getClass) {
                                return (pd).getPropertyType();
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

        if ( !setit ) throw new IllegalArgumentException( "unable to find property \""+propertyName+"\" in "+node );
        return null;
    }

    /**
     * get the node property value
     * @param node the dom node
     * @param propertyName the property name
     * @return the node property value
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     * @throws InvocationTargetException 
     */
    public static Object getPropertyValue(DomNode node, String propertyName) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        return setGetPropertyInternal( node, propertyName, false,false, null );
    }

    /**
     * set the property value
     * @param node the dom node
     * @param propertyName the property name
     * @param val the new value
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     * @throws InvocationTargetException 
     */
    public static void setPropertyValue(DomNode node, String propertyName, Object val ) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        setGetPropertyInternal( node, propertyName, true,false, val );
    }

    /**
     * get the property type, e.g. Datum.class or [Lorg.virbo.autoplot.dom.Canvas (array of Canvases.)
     * @param node the dom node
     * @param propertyName the property name
     * @return the property type
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     * @throws InvocationTargetException 
     */
    public static Class getPropertyType( DomNode node, String propertyName ) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        return (Class)setGetPropertyInternal( node, propertyName, false, true, null );
    }

    /**
     * return the list of nodes (Plots or Annotations) that use this row.
     * @param dom
     * @param rowId the row id.
     * @return list of nodes
     */
    static List<DomNode> rowUsages(Application dom, String rowId) {
        List<DomNode> result = new ArrayList<>();
        for (Plot p : dom.getPlots()) {
            if (p.getRowId().equals(rowId)) {
                result.add(p);
            }
        }
        for (Annotation a : dom.getAnnotations()) {
            if (a.getRowId().equals(rowId)) {
                result.add(a);
            }
        }
        return result;
    }

    /**
     * return the list of nodes (Plots or Annotations) that use this column.
     * @param dom
     * @param columnId the column id.
     * @return list of nodes
     */
    static List<DomNode> columnUsages(Application dom, String columnId) {
        List<DomNode> result = new ArrayList<>();
        for (Plot p : dom.getPlots()) {
            if (p.getColumnId().equals(columnId)) {
                result.add(p);
            }
        }
        for (Annotation a : dom.getAnnotations()) {
            if (a.getColumnId().equals(columnId)) {
                result.add(a);
            }
        }
        return result;
    }
    
    private static DatumRange round(DatumRange range) {
        Datum w = range.width();
        Datum w0 = DatumUtil.asOrderOneUnits(w);
        Datum base;
        Units hu = w0.getUnits();
        if (range.getUnits().isConvertibleTo(Units.us2000)) {
            base = TimeUtil.prevMidnight(range.min());
        } else {
            base = w.getUnits().createDatum(0);
        }
        double min10 = Math.round((range.min().subtract(base)).doubleValue(w0.getUnits()));
        double max10 = Math.round((range.max().subtract(base)).doubleValue(w0.getUnits()));
        return new DatumRange(base.add(Datum.create(min10, hu)), base.add(Datum.create(max10, hu)));
    }

    /**
     * describe the change in axis range.  These include:<ul>
     * <li>zoom in, zoom out - one range completely contains the other.
     * <li>pan - the range is adjusted but partially overlaps
     * <li>scan - the range is adjusted so that the two do not intersect.
     * </ul>
     * @param init the initial range
     * @param fin the final range
     * @return the human consumable string, e.g. "zoom out"
     */
    public static String describe(DatumRange init, DatumRange fin) {
        if (init.getUnits().isConvertibleTo(fin.getUnits())) {
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

//  This was abondoned because it's too difficult to do automatically.
//    /**
//     * attempt to parse the diff formatted to the string.  Some example diffs:
//     * 'delete row_5 from Canvases[0].rows @ 2;'
//     * 'Canvases[0].marginColumn.left +8.0em \u2192 +7.0em'
//     * 'plots[0].xaxis.range 2017-09-07 23:00 to 2017-09-09 00:00 \u2192 0.0 to 100.0 ;'
//     * This is a bit of an exercise to see if all the needed information is available.
//     * @param s
//     * @param dom
//     * @return appropriate diff object
//     */
//    public static Diff parseDiff( String s, Application dom ) {
//        Pattern p1= Pattern.compile("(insert|delete) (.+) (from|into) (.+) @ (\\d+)");
//        Matcher m1= p1.matcher(s);
//        if ( m1.matches() ) {
//            if ( m1.group(1).equals("delete") ) {
//                return new ArrayNodeDiff( m1.group(3), ArrayNodeDiff.Action.Delete, dom, Integer.parseInt(m1.group(4)) );
//            } else {
//                return new ArrayNodeDiff( m1.group(3), ArrayNodeDiff.Action.Insert, dom, Integer.parseInt(m1.group(4)) );
//            }
//        } else {
//            Pattern p2= Pattern.compile("((.+)\\.([a-zA-Z]+)) (.+) \u2192 (.+)");
//            Matcher m2= p2.matcher(s);
//            if ( m2.matches() ) {
//                String sparent= m2.group(2);
//                DomNode parent= getElementByAddress( dom, sparent );
//                try {
//                    Class c= getPropertyType( parent, m2.group(3) );
//                    Object old= c.
//                    //Object parent=
//                    //return new PropertyChangeDiff( m2.group(2), Object oldVal, Object newVal) 
//                } catch (IllegalAccessException ex) {
//                    Logger.getLogger(DomUtil.class.getName()).log(Level.SEVERE, null, ex);
//                } catch (IllegalArgumentException ex) {
//                    Logger.getLogger(DomUtil.class.getName()).log(Level.SEVERE, null, ex);
//                } catch (InvocationTargetException ex) {
//                    Logger.getLogger(DomUtil.class.getName()).log(Level.SEVERE, null, ex);
//                }
//            }
//        }
//        return null;
//    }
            
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
     * like getElementById, but return null if the id is an empty string.
     * @param root
     * @param id
     * @param allowEmpty
     * @return 
     */
    public static DomNode getElementById(DomNode root, String id, boolean allowEmpty ) {
        if ( id.equals("") && allowEmpty ) {
            return null;
        } else {
            return getElementById( root, id );
        }
    }

    /**
     * return the node with this id, or null if the id is not found.
     * @param root the root, such as the dom or the canvas.
     * @param id the id of the node.
     * @return the node with this id, or null if the id is not found.
     * @see #getElementByAddress(org.autoplot.dom.DomNode, java.lang.String) 
     */
    public static DomNode getElementById(DomNode root, String id) {
        if (id == null ) {
            throw new IllegalArgumentException("id cannot be null");
        }
        if ( id.equals("") ) {
            throw new IllegalArgumentException("id cannot be zero-length string");
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
     * return the node at the address, for example "plots[2].xaxis" of an application.
     * @param domNode the initial dom node, like dom.
     * @param address the address, like plots[2].xaxis
     * @return the domNode at the address.
     * @see #getElementById(org.autoplot.dom.DomNode, java.lang.String) y
     */
    public static DomNode getElementByAddress( DomNode domNode, String address ) {
        String[] ss= address.split("\\.");
        for ( String s: ss ) {
            try {
                int index;
                Pattern p= Pattern.compile("([a-zA-Z]+)(\\[(\\d+)\\])?");
                Matcher m= p.matcher(s);
                if ( m.matches() ) {
                    if ( m.group(2)!=null ) {
                        index= Integer.parseInt(m.group(3));
                    } else {
                        index= -1;
                    }
                } else {
                    throw new IllegalArgumentException("regex doesn't match");
                }
                String prop= m.group(1);
                Class c = domNode.getClass();
                Object o;
                if ( index>-1 ) {
                    IndexedPropertyDescriptor pd = new IndexedPropertyDescriptor(prop, c);
                    Method getter = pd.getIndexedReadMethod();
                    o= getter.invoke( domNode, index );
                } else {
                    PropertyDescriptor pd = new PropertyDescriptor(prop, c);
                    Method getter = pd.getReadMethod();
                    o= getter.invoke( domNode );
                }
                if ( !( o instanceof DomNode ) ) {
                    throw new IllegalArgumentException("address is not that of a node (is it a property?)");
                }
                domNode= (DomNode)o;

            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | IntrospectionException ex) {
                throw new IllegalArgumentException(ex);
            }
        }
        return domNode;
    }
    
    /**
     * return null or the plot using the axis.
     * @param app the application
     * @param oa the axis
     * @return null or the plot using the axis
     */
    public static Plot getPlotForAxis( Application app, Axis oa ) {
        for ( Plot p: app.getPlots() ) {
            if ( p.getXaxis()==oa ) {
                return p;
            } else if ( p.getYaxis()==oa ) {
                return p;
            } else if ( p.getZaxis()==oa ) {
                return p;                
            }
        }
        return null;
    }
    
    /**
     * find the nodes matching this regex.
     * @param root the node to start at.
     * @param regex the regular expression.
     * @return the nodes.
     */
    public static List<DomNode> findElementsById( DomNode root, String regex ) {
        if (regex == null || regex.equals("")) {
            throw new IllegalArgumentException("id cannot be null or zero-length string");
        }
        Pattern p= Pattern.compile(regex);
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
        return new ArrayList<>(Arrays.asList(a));
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
     * @param diffs
     * @return
     */
    public static List<Diff> childDiffs(String childName, List<Diff> diffs) {
        ArrayList<Diff> result = new ArrayList<>();
        for (Diff diff : diffs) {
            Diff r1 = childDiff(childName, diff);
            if (r1 != null) result.add(r1);
        }
        return result;
    }

    /**
     * returns the index by ID, not by equals.  Equals cannot be 
     * overriden, because property change diffs, etc.
     * @param nodes list of nodes
     * @param node the node to search for
     * @return the index or -1.
     */
    public static int indexOf(List<Object> nodes, Object node) {
        if ( node==null ) throw new NullPointerException("node is null");
        boolean isDomNode = node instanceof DomNode;
        if (!isDomNode) {
            return nodes.indexOf(node);
        } else {
            String findId= ((DomNode) node).id;
            for (int i = 0; i < nodes.size(); i++) {
                DomNode n1 = (DomNode) nodes.get(i);
                if (n1 == node) return i;
                String id = n1.getId();
                if (!id.equals("") && id.equals(findId)) {
                    return i;
                }
            }
        }
        return -1;
    }
        
    /**
     * delete the data source filter.
     * @param application
     * @param dsf the data source filter to remove.
     * @see org.autoplot.dom.ApplicationController#deleteDataSourceFilter(org.autoplot.dom.DataSourceFilter) 
     */
    public static void deleteDataSourceFilter( Application application, DataSourceFilter dsf ) {
        if (!application.dataSourceFilters.contains(dsf)) {
            logger.fine("dsf wasn't part of the application");
            return;
        }
        if (application.dataSourceFilters.size() < 2) {
            throw new IllegalArgumentException("last plot cannot be deleted");
        }
        
        List<DomNode> plotElements = dataSourceUsages( application, dsf.id );
        if (plotElements.size() > 0) {
            throw new IllegalArgumentException("application plot elements use dsf");
        }

        for ( DataSourceFilter dsf1: application.getDataSourceFilters() ) {
            String uri= dsf1.getUri();
            if ( uri.startsWith("vap+internal:") ) {
                if ( uri.contains(dsf.id) ) {
                    throw new IllegalArgumentException("dsf is used as parent of "+dsf1.getId() );
                }
            }
        }
        
        ArrayList<DataSourceFilter> alsoRemove= new ArrayList<>();
        
        // look for orphaned parents
        if ( dsf.getUri().startsWith("vap+internal:") ) {
            List<DataSourceFilter> parents= getParentsFor(application, dsf.getUri() );
            for (DataSourceFilter pdf : parents) {
                if ( pdf==null ) continue;
                String dsfId = pdf.getId();
                List<DomNode> usages = DomUtil.dataSourceUsages(application, dsfId);
                usages.remove(dsf);
                if (usages.isEmpty()) {
                    alsoRemove.add(pdf);
                }
            }
        }

        List<DataSourceFilter> dsfs = new ArrayList<>(Arrays.asList(application.getDataSourceFilters()));
        dsfs.remove(dsf);
        dsfs.removeAll(alsoRemove);

        application.setDataSourceFilters(dsfs.toArray(new DataSourceFilter[dsfs.size()]));

    }

    /**
     * list the differences in two arrays of the same type of object.
     * This is the diffs that will make nodes2 look like nodes1.
     * Presently this just identifies inserts and deletes.  If the objects
     * are DomNodes, then ids are used to match nodes.
     * @param property name used to identify the difference in the result.
     * @param nodes1 list of nodes
     * @param nodes2 list of nodex
     * @return list of Diffs between the lists.
     */
    public static List<Diff> getArrayDiffs(String property, Object[] nodes1, Object[] nodes2) {
        List<Diff> result = new LinkedList<>();

        List<Object> node1List = new ArrayList<>(Arrays.asList(nodes1));
        List<Object> node2List = new ArrayList<>(Arrays.asList(nodes2));

        List<Object> deleteList = new ArrayList<>();
        int i2=0;
        for ( Object o: nodes2 ) {
            if ( indexOf( node2List, o )!=i2 ) {
                deleteList.add( o );
                logger.log( Level.WARNING, "two nodes have the same ID: {0}", o);
            } // throw IllegalArgumentException("two nodes have the same ID: "+o);
            i2++;
        }
        for ( Object o: nodes2 ) {
            if ( indexOf( node1List, o )==-1 ) deleteList.add( o );
        }

        boolean isDomNode = DomNode.class.isAssignableFrom(nodes1.getClass().getComponentType());

        for ( Object deleteList1 : deleteList ) {
            int idx = indexOf(node2List, deleteList1);
            result.add(new ArrayNodeDiff(property, ArrayNodeDiff.Action.Delete, node2List.get(idx), idx));
            node2List.remove(idx);
        }

        List<Object> addList = new ArrayList<>();
        for ( Object o: nodes1 ) {
            if ( indexOf( node2List, o )==-1 ) addList.add( o );
        }

        for (Object addList1 : addList) {
            int idx;
            idx = indexOf(node1List, addList1);
            if (nodes1[idx] instanceof DomNode) {
                result.add(new ArrayNodeDiff(property, ArrayNodeDiff.Action.Insert, ((DomNode) nodes1[idx]).copy(), idx));
            } else {
                result.add(new ArrayNodeDiff(property, ArrayNodeDiff.Action.Insert, nodes1[idx], idx));
            }
            node2List.add(idx, nodes1[idx]);
        }

        if ( node1List.size()!=node2List.size() ) {
            logger.warning("2057: bug where two nodes have the duplicate ID detected.");
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
     * return the list of diffs that will make node2 look like node1.  This
     * assumes that the two nodes are not modified by another thread.
     * @param node1 a node
     * @param node2 a node
     * @param exclude if non-null, exclude these properties.
     * @return
     */
    public static List<Diff> getDiffs(DomNode node1, DomNode node2, List<String> exclude) {
        String[] props = BeansUtil.getPropertyNames(node1.getClass());
        PropertyDescriptor[] pds = BeansUtil.getPropertyDescriptors(node1.getClass());
        
        List<Diff> diffs = new ArrayList<>();
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
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                logger.log(Level.WARNING,ex.getMessage(),ex);
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
            boolean b= ((Boolean)value);
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
        if ( elementsIn.isEmpty() ) return false;
        List<PlotElement> elements= new ArrayList(elementsIn);
        PlotElement pe= elements.get(0);
        if ( pe.getController().getParentPlotElement()!=null ) {
            pe= pe.getController().getParentPlotElement();
        }
        elements.remove(pe);
        elements.removeAll( pe.getController().getChildPlotElements() );

        return elements.isEmpty();

    }
    
    /**
     * return the parent DataSourceFilters for uris like vap+internal:data_1,data_2
     * Note this was the only way to combine 
     * data before the "Data Mashup Tool" was introduced, and the Mashup tool
     * is probably a better way to do this.
     * @param dom the dom
     * @param uri the uri, like vap+internal:data_1,data_2
     * @return the DataSourceFilters
     */
    public static List<DataSourceFilter> getParentsFor( Application dom, String uri ) {
        String parents= uri.substring(13); // "vap+internal:".length
        if ( parents.trim().length()==0 ) return Collections.emptyList();
        String[] dep= parents.split(",");
        List<DataSourceFilter> result= new ArrayList();
        for (String dep1 : dep) {
            result.add((DataSourceFilter) getElementById(dom, dep1));
        }
        return result;
    }

    private static void checkIds( HashSet<String> ids, DomNode[] n, List<String> problems ) {
        for (DomNode n1 : n) {
            if (ids.contains(n1.id)) {
                problems.add("multiple nodes have the same id: " + n1.id);
            }
            ids.add(n1.id);
        }
    }
    
    /**
     * returns true if the dom is valid, throws a runtime exception otherwise
     * @param application the dom
     * @param problems descriptions of the problems will be inserted here
     * @return true if the dom is valid, throws a runtime exception otherwise
     * @see #deleteDuplicateIds(org.autoplot.dom.Application) 
     * @see #checkUniqueIdsAndReferences(org.autoplot.dom.Application, java.util.List) 
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
                    for (String dep1 : dep) {
                        if (getElementById(application, dep1) == null) {
                            problems.add("unable to find dsf " + dep1 + " for dsf " + dsf.getId());
                        }
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
            
            HashSet<String> ids= new HashSet<>();
            checkIds( ids, application.getPlots(), problems );
            checkIds( ids, application.getPlotElements(), problems );
            checkIds( ids, application.getDataSourceFilters(), problems );
            checkIds( ids, application.getAnnotations(), problems );

        } finally {
            if ( lock!=null ) {
                lock.unlock();
            }
        }
        
        return problems.isEmpty();
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
            if ( ! pd.getXaxis().getRange().getUnits().isConvertibleTo( ps.getXaxis().getRange().getUnits() ) ) return true;
            if ( ! pd.getYaxis().getRange().getUnits().isConvertibleTo( ps.getYaxis().getRange().getUnits() ) ) return true;
            if ( ! pd.getZaxis().getRange().getUnits().isConvertibleTo( ps.getZaxis().getRange().getUnits() ) ) return true;
        }
        return false;
    }

    /**
     * Return the plot elements that contained within a plot.
     * @param application the dom for a plot.
     * @param plot the plot containing plot elements.
     * @return the plot elements contained by the plot.
     */
    public static List<PlotElement> getPlotElementsFor( Application application, Plot plot ) {
        String id = plot.getId();
        List<PlotElement> result = new ArrayList<>();
        for (PlotElement p : application.getPlotElements()) {
            if (p.getPlotId().equals(id)) {
                result.add(p);
            }
        }
        return result;
    }
    
    /**
     * Return the plot elements that contained within a plot.
     * @param application the dom for a plot.
     * @param dsf the data source filter used by one (or more) plot element.
     * @return the plot elements using the dataSourceFilter.
     */
    public static List<PlotElement> getPlotElementsFor( Application application, DataSourceFilter dsf ) {
        String id = dsf.getId();
        return getPlotElementsFor( application, id );
    } 
    
    private static List<PlotElement> getPlotElementsFor( Application application, String id ) {
        List<PlotElement> result = new ArrayList<>();
        for (PlotElement p : application.getPlotElements()) {
            if (p.getDataSourceFilterId().equals(id)) {
                result.add(p);
            }
        }
        for ( DataSourceFilter dsf: application.getDataSourceFilters() ) {
            if ( dsf==null ) {
                logger.finer("found dsf that is null, which is done sometimes to mark as done.  Ignoring.");
                continue;
            }
            String uri= dsf.getUri();
            if ( uri.startsWith("vap+internal:") ) {
                String[] ss=  uri.substring(13).split(",");
                for (String s : ss) {
                    if (s.equals(id)) {
                        List<PlotElement> pes1= getPlotElementsFor(application,dsf.getId());
                        result.addAll(pes1);
                    }
                }
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
        for (String prop : props) {
            if (prop.equals(property)) {
                return true;
            }
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
                int i2= template.indexOf('}',i);
                String propName= template.substring(i+n,i2);
                int i3= propName.indexOf('.');
                Map<String,Object> props1= props;
                while ( i3>-1 ) {
                    String propName1= propName.substring(0,i3);
                    props1= (Map<String, Object>) props.get(propName1);
                    propName= propName.substring(i3+1);
                    i3= propName.indexOf('.');
                }
                String prop= String.valueOf( props1.get(propName) );
                template= template.substring(0,i) + prop + template.substring(i2+1);
                i= template.indexOf("%{"+root+".",i);
            }
        } catch ( Exception ex ) {
            logger.log(Level.INFO, "unable to resolve template: {0}", template);
            logger.log(Level.FINE,null,ex);
        }
        return template;
    }

    /**
     * convert the layout to the pixel position if the top of the row.  Note
     * this is not trivial, because the parent must be considered as well.
     * @param dom the layout containing the row, and canvases[0] is used.
     * @param row the row
     * @param position a string like "100%-1em+1px"
     * @return the position in pixels
     */
    public static int getRowPositionPixels( Application dom, Row row, String position  ) {
        Canvas c= dom.getCanvases(0);
        Font f= Font.decode(c.getFont());
        double em= f.getSize2D();
        String parent= row.getParent();
        double dpos;
        if ( parent.length()>0 ) {
            DomNode n= getElementById( dom, parent );
            Row parentRow= (Row) n;
            int pmin= getRowPositionPixels( dom, parentRow, parentRow.getTop() );
            int pmax= getRowPositionPixels( dom, parentRow, parentRow.getBottom() );
            dpos= pmin + DasColumn.parseLayoutStr( position, em, pmax-pmin, -1 );
        } else {
            dpos= DasColumn.parseLayoutStr( position, em, c.getHeight(), -1 );
        }
        return (int)dpos;
    }
    
    /**
     * convert the layout to the pixel position if the left side of the column.  The one
     * and only canvas is used for the width.  Note
     * this is not trivial, because the parent must be considered as well.
     * @param dom the layout containing the column, and canvases[0] is used.
     * @param col the column
     * @param  position a string like "100%-5em"
     * @return the position in pixels.
     */
    public static int getColumnPositionPixels( Application dom, Column col, String position ) {
        Canvas c= dom.getCanvases(0);
        Font f= Font.decode(c.getFont());
        String parent= col.getParent();
        double dpos;
        if ( parent.length()>0 ) {
            DomNode n= getElementById( dom, parent );
            Column parentColumn= (Column) n; // there was a bug where addPlots(3,3) would use a row for the parent, not the column.
            int pmin= getColumnPositionPixels( dom, parentColumn, parentColumn.getLeft() );
            int pmax= getColumnPositionPixels( dom, parentColumn, parentColumn.getRight() );
            dpos= pmin + DasColumn.parseLayoutStr( position, f.getSize2D(), pmax-pmin, -1 );
        } else {
            dpos= DasColumn.parseLayoutStr( position, f.getSize2D(), c.getWidth(), -1 );
        }
        return (int)dpos;
    }
    
    /**
     * return the bounds for the plot, including the space needed for the title but not the space
     * needed for the axes.  This is not intuitively difficult to do, but since one Row is generally
     * relative to another row, this is not trivial.
     * @param dom the layout containing the plot
     * @param p the plot
     * @return the bounds 
     * @see #getRowPositionPixels(org.autoplot.dom.Application, org.autoplot.dom.Row, java.lang.String) 
     * @see #getColumnPositionPixels(org.autoplot.dom.Application, org.autoplot.dom.Column, java.lang.String) 
     */
    public static Rectangle getBoundsForPlot( Application dom, Plot p ) {
        Row row= (Row)getElementById( dom, p.getRowId() );
        Column col= (Column)getElementById( dom, p.getColumnId() );
        int c0= (int)getColumnPositionPixels( dom, col, col.getLeft() );
        int c1= (int)getColumnPositionPixels( dom, col, col.getRight() );
        int r0= (int)getRowPositionPixels( dom, row, row.getTop() );
        int titleHeightLines= p.getTitle().trim().split("\n|\\<br\\>|\\!c",2).length;
        int ems= Font.decode(dom.getCanvases(0).font).getSize(); //TODO: verify this is ems
        r0= r0-titleHeightLines*ems;
        int r1= (int)getRowPositionPixels( dom, row, row.getBottom() );
        return new Rectangle( c0, r0, c1-c0, r1-r0 );
    }
    
    /**
     * return the bounds for the xaxis, including the space needed for the label.  The ephemeris (TCAs)
     * lines needed is not known, so five lines are assumed.  
     * @param dom the layout containing the plot
     * @param p the plot
     * @return the bounds 
     * @see #getRowPositionPixels(org.autoplot.dom.Application, org.autoplot.dom.Row, java.lang.String) 
     * @see #getColumnPositionPixels(org.autoplot.dom.Application, org.autoplot.dom.Column, java.lang.String) 
     */
    public static Rectangle getBoundsForXAxis( Application dom, Plot p ) {
        Axis xaxis= p.getXaxis();
        int emToPix= Font.decode(dom.getCanvases(0).font).getSize(); 
        Row row= (Row)getElementById( dom, p.getRowId() );
        Column col= (Column)getElementById( dom, p.getColumnId() );
        int c0= (int)getColumnPositionPixels( dom, col, col.getLeft() );
        int c1= (int)getColumnPositionPixels( dom, col, col.getRight() );
        int r1= (int)getRowPositionPixels( dom, row, row.getBottom() );
        int r0= r1;
        
        double[] tickLenEmPx;
        try {
            tickLenEmPx = DasDevicePosition.parseLayoutStr(dom.getOptions().getTicklen());
        } catch (ParseException ex) {
            tickLenEmPx= new double[] { 0,0,0 };
        }
        // make independent from row layout for initialization, ignoring normalized length.
        int tickLen = Math.max( 0, (int) ( Math.round( tickLenEmPx[1]*emToPix + tickLenEmPx[2] ) ) ); 
        
        int axisLines;
        if ( xaxis.isVisible() ) {
            if ( xaxis.isDrawTickLabels() ) { 
                axisLines= 1;
                String label= xaxis.getLabel().trim();
                if ( label.length()>0 ) axisLines += ( 1 + label.split("\n|\\<br\\>|\\!c",2).length ); // 1 is for odd gap.
                if ( p.getTicksURI().trim().length()>0 ) {
                    if ( p.getEphemerisLineCount()>-1 ) {
                        axisLines+= p.getEphemerisLineCount();
                    } else if ( p.getEphemerisLabels().trim().length()>0 ) {
                        axisLines+= p.getEphemerisLabels().split(";").length;
                    } else {
                        int nominalNumberOfTicksLines= 5;
                        axisLines+= nominalNumberOfTicksLines;
                    }
                }
            } else {
                axisLines= 0;
            }
            r1= r1 + axisLines*emToPix + tickLen;
        }
        return new Rectangle( c0, r0, c1-c0, r1-r0 );
    }
        
    /**
     * return the bounds for the colorbar zaxis, including the space needed for the label.
     * @param dom the layout containing the plot
     * @param p the plot
     * @return the bounds 
     * @see #getRowPositionPixels(org.autoplot.dom.Application, org.autoplot.dom.Row, java.lang.String) 
     * @see #getColumnPositionPixels(org.autoplot.dom.Application, org.autoplot.dom.Column, java.lang.String) 
     */
    public static Rectangle getBoundsForZAxis( Application dom, Plot p ) {
        Axis zaxis= p.getZaxis();
        Row row= (Row)getElementById( dom, p.getRowId() );
        Column col= (Column)getElementById( dom, p.getColumnId() );
        int c0= (int)getColumnPositionPixels( dom, col, col.getRight() );
        int c1= c0;
        int r0= (int)getRowPositionPixels( dom, row, row.getTop() );
        int r1= (int)getRowPositionPixels( dom, row, row.getBottom() );
        int axisLines= 5 + zaxis.getLabel().trim().split("\n|\\<br\\>|\\!c",2).length;
        if ( p.getTicksURI().trim().length()>0 ) {
            int nominalNumberOfTicksLines= 5;
            axisLines+= nominalNumberOfTicksLines;
        }
        int ems= Font.decode(dom.getCanvases(0).font).getSize(); //TODO: verify this is ems
        c1= c1+axisLines*ems;
        return new Rectangle( c0, r0, c1-c0, r1-r0 );
    }
            
    /**
     * Find the binding, if it exists.  All bindingImpls are symmetric, so the src and dst order is ignored in this
     * search.
     * @param dom the dom tree
     * @param src
     * @param srcProp
     * @param dst
     * @param dstProp
     * @return the BindingModel or null if it doesn't exist.
     */
    public static BindingModel findBinding( Application dom, DomNode src, String srcProp, DomNode dst, String dstProp) {
        List<BindingModel> results= findBindings( dom, src, srcProp, dst, dstProp );
        if ( results.isEmpty() ) {
            return null;
        } else {
            return results.get(0);  // TODO: this should be a singleton.
        }
    }


    /**
     * returns a list of bindings of the node for the property
     * @param dom the dom tree
     * @param src the node to which or from which a binding exists
     * @param srcProp the property name of the binding.
     * @return
     */
    public static List<BindingModel> findBindings( Application dom, DomNode src, String srcProp ) {
        List<BindingModel> bindings= findBindings( dom, src, srcProp, null, null );
        List<BindingModel> bindings2= findBindings( dom, null, null, src, srcProp );
        bindings2.removeAll(bindings);
        bindings.addAll(bindings2);
        return bindings;
    }

    /**
     * Find the bindings that match given constraints.  If a property name or node is null, then the
     * search is unconstrained.
     * @param dom the dom tree
     * @param src
     * @param srcProp
     * @param dst
     * @param dstProp
     * @return the BindingModel or null if it doesn't exist.
     */
    public static List<BindingModel> findBindings( Application dom, DomNode src, String srcProp, DomNode dst, String dstProp) {
        List<BindingModel> result= new ArrayList();
        for (BindingModel b : dom.getBindings()) {
            try {
                if (  ( src==null || b.getSrcId().equals(src.getId()) )
                        && ( dst==null || b.getDstId().equals(dst.getId()) )
                        && ( srcProp==null || b.getSrcProperty().equals(srcProp) )
                        && ( dstProp==null || b.getDstProperty().equals(dstProp) ) ){
                    result.add(b);
                } else if ( ( dst==null || b.getSrcId().equals(dst.getId()) )
                        && ( src==null || b.getDstId().equals(src.getId()) )
                        && ( dstProp==null || b.getSrcProperty().equals(dstProp) )
                        && ( srcProp==null || b.getDstProperty().equals(srcProp) ) ) {
                    result.add(b);
                }
            } catch (NullPointerException ex) {
                throw ex;
            }

        }
        return result;
    }

    /**
     * print a list of who is listening to the property
     * @param o the dom node or dom node controller.
     * @param prop the property name.
     */
    public static void findListeners( Object o, String prop ) {
        if ( o instanceof DomNode ) {
            PropertyChangeSupport pcs= ((DomNode)o).propertyChangeSupport;
            if ( pcs instanceof DebugPropertyChangeSupport ) { 
                ((DebugPropertyChangeSupport)pcs).printListeners(prop);
            }
        } else if ( o instanceof DomNodeController ) {
            PropertyChangeSupport pcs= ((DomNodeController)o).propertyChangeSupport;
            if ( pcs instanceof DebugPropertyChangeSupport ) { 
                ((DebugPropertyChangeSupport)pcs).printListeners(prop);
            }
        } else {
            System.err.println( "Not supported: "+o );
        }
    }
    
    /**
     * Look through the state property values for references to ${PWD}
     * and replace them with sval.
     * @param state the domNode, typically starting from the Application root.
     * @param node %{PWD}
     * @param sval /tmp
     */
    public static void applyMacro( DomNode state, String node, String sval) {
        String[] props = BeansUtil.getPropertyNames(state.getClass());
        PropertyDescriptor[] pds = BeansUtil.getPropertyDescriptors(state.getClass());

        for (int i = 0; i < props.length; i++) {
            if (props[i].equals("controller")) continue;
            try {
                if ( pds[i] instanceof IndexedPropertyDescriptor) {
                    Method m=  pds[i].getReadMethod();
                    if ( m!=null ) {
                        Object vals1 = m.invoke(state, new Object[0]);
                        for ( int j=0; j<Array.getLength(vals1); j++ ) {
                            Object val1= Array.get( vals1, j );
                            if ( val1 instanceof DomNode  ) {
                                applyMacro( (DomNode)val1, node, sval );
                            } else if ( val1 instanceof String ) {
                                System.err.println( val1 );
                                String sval1= (String)val1;
                                if ( sval1.contains( node ) ) {
                                    sval1= sval1.replaceAll( node, sval );
                                    pds[i].getWriteMethod().invoke( state, sval1 );
                                }
                            }
                        }
                    }
                } else {
                    Method m=  pds[i].getReadMethod();
                    if ( m!=null ) {
                        Object val1 = m.invoke(state, new Object[0]);
                        if ( val1 instanceof DomNode  ) {
                            applyMacro( (DomNode)val1, node, sval );
                        } else if ( val1 instanceof String ) {
                            String sval1= (String)val1;
                            if ( sval1.contains( node ) ) { 
                                sval1= sval1.replace( node, sval );  //TODO: convert %{} to regex.
                                pds[i].getWriteMethod().invoke( state, sval1 );
                            }
                        }
                    }
                }
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | ArrayIndexOutOfBoundsException ex) {
                logger.log( Level.WARNING, ex.getMessage(), ex );
            }

        }
    }

    /**
     * this was made public for bug 1520.  This returns a 1-plot dom, but 
     * this may change.
     * We find all plots sharing the same row and column as the plot.
     * @param application
     * @param domPlot
     * @return 
     */
    public static String getPlotAsString(Application application, Plot domPlot) {
        Application newApp= new Application();
        List<Plot> plots= new ArrayList<>();
        for ( Plot p: application.getPlots() ) {
            if ( p.getRowId().equals(domPlot.getRowId()) &&
                p.getColumnId().equals(domPlot.getColumnId()) ) {
                plots.add(p);
            }
        }
        newApp.setPlots( plots.toArray(new Plot[0]) );
        List<PlotElement> pes= new ArrayList<>();
        List<DataSourceFilter> dsfs= new ArrayList<>();
        for ( Plot p: plots ) {
            List<PlotElement> pes1= getPlotElementsFor( application, p );
            pes.addAll(pes1);
            List<DataSourceFilter> dsfs1= getDataSourceFiltersFor( application, p );
            dsfs.addAll(dsfs1);
        }
        newApp.setPlotElements(pes.toArray(new PlotElement[0]) );
        newApp.setDataSourceFilters( dsfs.toArray(new DataSourceFilter[0]) );
        newApp.setCanvases(application.getCanvases());
        newApp.setId( application.id+"_"+domPlot.id );
        
        ByteArrayOutputStream baos= new ByteArrayOutputStream(1000);
        try {
            StatePersistence.saveState( baos, newApp, "" );
            return baos.toString("UTF-8");
            
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * return the list of DataSourceFilters for a plot.
     * @param dom the application
     * @param p the plot
     * @return new list of dataSourceFilters
     */
    public static List<DataSourceFilter> getDataSourceFiltersFor( Application dom, Plot p) {
        List<DataSourceFilter> dsfs= new ArrayList<>();
        List<PlotElement> pes= getPlotElementsFor(dom, p);
        for ( PlotElement pe: pes ) {
            DataSourceFilter dsf= (DataSourceFilter)getElementById( dom, pe.getDataSourceFilterId() );
            dsfs.add( dsf );
            if ( dsf.getUri().startsWith("vap+internal:") ) {
                dsfs.addAll( getParentsFor(dom, dsf.getUri() ) );
            }
        }
        return dsfs;
    }
    
    private static ArrayList<String> vapToJython( ArrayList<String> jython, String nodeAddress, PropertyChangeDiff pcd ) {
        
        String propertyName= pcd.propertyName;
        if ( propertyName.endsWith("scale") ) {
            return jython;
        } else if ( propertyName.endsWith("autoLabel") ) {
            return jython;
        } else if ( propertyName.startsWith("options") ) {
            return jython;
        }
                        
        Object o= pcd.newVal;
        String s;
        if ( o instanceof String ) {
            s= "'"+o+"'";
        } else if ( o instanceof Boolean ) {
            s= o.toString();
            s= String.valueOf( Character.toUpperCase(s.charAt(0)) ) + s.substring(1);
        } else if ( o instanceof Color ) {
            Color c= (Color)o;
            s= "color('"+ColorUtil.nameForColor(c)+"')";
        } else if ( o instanceof DatumRange ) {
            s= "datumRange('"+o+"')";
        } else if ( o instanceof Datum ) {
            s= "datum('"+o+"')";
        } else if ( o instanceof Enum ) {
            String sclaz= ((Enum) o).getDeclaringClass().getCanonicalName();
            jython.add( "import " + sclaz );
            s= "" + sclaz + "."+ o;
            //s= String.valueOf( Character.toUpperCase(s.charAt(0)) ) + s.substring(1);
        } else if ( o instanceof Displayable ) {
            String sclaz= ((Displayable) o).getClass().getCanonicalName();
            jython.add( "import " + sclaz );
            s= "" + sclaz + "."+ o.toString().toUpperCase();
            //s= String.valueOf( Character.toUpperCase(s.charAt(0)) ) + s.substring(1);
        } else {
            s= String.valueOf(o);
        }
        jython.add( nodeAddress + "." + pcd.propertyName + " = " + s );

        return jython;
    }
    
    private static ArrayList<String> vapToJython( String nodeAddress, DomNode src, DomNode dst ) {
        ArrayList<String> jython= new ArrayList<>();
        
        List<Diff> diffs= dst.diffs(src);
        for ( Diff d: diffs ) {
            if ( d instanceof PropertyChangeDiff ) {
                jython = vapToJython( jython, nodeAddress, (PropertyChangeDiff)d );
//            } else if ( d instanceof )
            } else  {
                throw new IllegalArgumentException("only property change diffs!");
            }
        }
        return jython;
    }
    
    /**
     * Ivar requested a vap-to-Jython converter.
     * @param app0
     * @param app
     * @return 
     */
    public static String[] vapToJython( Application app0, Application app ) {
        ArrayList<String> jython= new ArrayList<>();
        List<Diff> diffs= app0.diffs(app);
        for ( Diff d: diffs ) {
            if ( d instanceof PropertyChangeDiff ) {
                PropertyChangeDiff pcd= (PropertyChangeDiff)d;
                jython = vapToJython( jython, "dom", pcd );
                
            } else if ( d instanceof ArrayNodeDiff ) {
                ArrayNodeDiff and= (ArrayNodeDiff)d;
                if ( null!=and.action ) switch (and.action) {
                    case Insert:
                        if ( and.node instanceof Annotation ) {
                            jython.add( "from org.autoplot.dom import Annotation" );
                            jython.add( "dom.controller.addAnnotation(Annotation())" );
                            jython.addAll( vapToJython( "dom.annotations["+and.index+"]", new Annotation(), (DomNode)and.node ) );
                        } else if ( and.node instanceof Plot ) {
                            jython.add( "from org.autoplot.dom import Plot" );
                            jython.add( "dom.controller.addPlot(Plot())" );
                            jython.addAll( vapToJython( "dom.plots["+and.index+"]", new Plot(), (DomNode)and.node ) );
                        } else if ( and.node instanceof Row ) {
                        } else if ( and.node instanceof Column ) {
                        } else if ( and.node instanceof DataSourceFilter ) {
                            jython.add( "from org.autoplot.dom import DataSourceFilter" );
                            jython.add( "dom.controller.addDataSourceFilter()" );
                            jython.addAll( vapToJython( "dom.dataSourceFilters["+and.index+"]", new DataSourceFilter(), (DomNode)and.node ) );
                        } else if ( and.node instanceof PlotElement ) {
                            jython.add( "from org.autoplot.dom import PlotElement" );
                            jython.add( "dom.controller.addPlotElement(None,None)" );
                            jython.addAll( vapToJython( "dom.plotElements["+and.index+"]", new PlotElement(), (DomNode)and.node ) );
                        } else if ( and.node instanceof BindingModel ) {
                            BindingModel bm= (BindingModel)and.node;
                            jython.add( "bind( dom.getElementById('" + bm.srcId +"'), '" + bm.srcProperty + "' ,dom.getElementById('" +  bm.dstId + "'), '"+ bm.dstProperty + "' )" );
                        } else {
                            jython.add( "insert " + d.toString());
                        }
                        break;
                    case Delete:
                        jython.add( "delete " + d.toString());
                        break;
                    case Move:
                        jython.add( "move " + d.toString());
                        break;
                    default:
                        break;
                }
            } else { 
                jython.add( d.toString());
            }
        }
        return jython.toArray( new String[jython.size()] );
    }

    /**
     * print a one-line representation of the layout, showing canvas dimensions,
     * font size, margin row and column, and the first row.
     * @param c
     * @return 
     */
    public static String layoutToString( Canvas c ) {
        Row arow= c.getRows(0);
        return String.format( "\u2610 %dx%d %dpt %s %s %s",
                c.width,
                c.height,
                Font.decode(c.font).getSize(), 
                "||" + c.marginColumn.getLeft()+","+c.marginColumn.getRight(), 
                "=" + c.marginRow.getTop()+","+c.marginRow.getBottom(), 
                "=" + arow.getTop() + "," + arow.getBottom() );
    }

    /**
     * if there are duplicate ids, we can delete them to at least make the vap valid.
     * @param state 
     */
    public static void deleteDuplicateIds(Application state) {
        HashSet<String> s;
        
        if ( state.controller!=null ) {
            throw new IllegalArgumentException("application can not have a controller.");
        }
        
        s = new HashSet<>();
        List<Annotation> ann= new ArrayList<>();
        for ( Annotation a: state.getAnnotations() ) {
            if ( !s.contains(a.id) ) {
                ann.add(a);
            }
        }
        state.setAnnotations(ann.toArray(new Annotation[ann.size()]));
        
        s = new HashSet<>();
        List<DataSourceFilter> ff= new ArrayList<>();
        for ( DataSourceFilter a: state.getDataSourceFilters() ) {
            if ( !s.contains(a.id) ) {
                ff.add(a);
            }
            s.add(a.id);
        }
        state.setDataSourceFilters(ff.toArray(new DataSourceFilter[ff.size()]));

        s = new HashSet<>();
        List<PlotElement> pes= new ArrayList<>();
        for ( PlotElement a: state.getPlotElements() ) {
            if ( !s.contains(a.id) ) {
                pes.add(a);
            }
            s.add(a.id);
        }
        state.setPlotElements(pes.toArray(new PlotElement[pes.size()]));
        
        s = new HashSet<>();
        List<Plot> ps= new ArrayList<>();
        for ( Plot a: state.getPlots() ) {
            if ( !s.contains(a.id) ) {
                ps.add(a);
            }
            s.add(a.id);
        }
        state.setPlots(ps.toArray(new Plot[ps.size()]));

    }
    
          
    /**
     * detect where ids are not unique
     * @param dom
     * @param problems
     * @return 
     * @see #deleteDuplicateIds which will make the vap valid, at least.
     * @see #validateDom(org.autoplot.dom.Application, java.util.List) 
     */
    public static List<String> checkUniqueIdsAndReferences( Application dom, List<String> problems ) {
        // check that all ids within the .vap are unique
        Map<String,DomNode> ids= new HashMap<>();
        for ( DomNode n: dom.dataSourceFilters ) {
            DomNode n1= ids.get(n.id);
            if ( n1!=null ) {
                problems.add( "DataSourceFilter id is already taken by "+n1+"." );
            } else {
                ids.put( n.id, n );
            }
        }
        List<Column> cc= new ArrayList( dom.getCanvases(0).columns );
        cc.add( dom.getCanvases(0).marginColumn );
        for ( Column n: cc ) {
            DomNode n1= ids.get(n.id);
            if ( n1!=null ) {
                problems.add( "Column id is already taken by "+n1+"." );
            } else {
                ids.put( n.id, n );
            }
            if ( n.getParent().equals(dom.getCanvases(0).marginRow.id) ) {
                problems.add( "Column parent is a row: "+n.id+"." );
            }
        }
        List<Row> rr= new ArrayList( dom.getCanvases(0).rows );
        rr.add( dom.getCanvases(0).marginRow );
        for ( Row n: rr ) {
            DomNode n1= ids.get(n.id);
            if ( n1!=null ) {
                problems.add( "Row id is already taken by "+n1+"." );
            } else {
                ids.put( n.id, n );
            }
        }        
        for ( Plot n: dom.plots ) {
            DomNode n1= ids.get(n.id);
            if ( n1!=null ) {
                problems.add( "Plot id is already taken by "+n1+"." );
            } else {
                ids.put( n.id, n );
            }
            if ( ids.get( n.getRowId() )==null ) {
                problems.add( "PlotElement refers to row '"+n.getRowId()+"' which is not found: "+n );
            }
            if ( ids.get( n.getColumnId() )==null ) {
                problems.add( "PlotElement refers to column '"+n.getColumnId()+"' which is not found: "+n );
            }
        }
        for ( PlotElement pe: dom.plotElements ) {
            DomNode n1= ids.get(pe.id);
            if ( n1!=null ) {
                problems.add( "PlotElement id is already taken by "+n1+"." );
            } else {
                ids.put( pe.id, pe );
            }
            if ( ids.get(pe.plotId)==null ) {
                problems.add( "PlotElement refers to plot '"+pe.plotId+"' which is not found: "+pe );
            }
            if ( ids.get(pe.dataSourceFilterId)==null ) {
                problems.add( "PlotElement refers to dataSourceFilter '"+pe.dataSourceFilterId+"' which is not found: "+pe );
            }
        }        
        for ( Connector c: dom.connectors ) {
            DomNode n1= ids.get(c.id);
            if ( n1!=null ) {
                problems.add( "Connector id is already taken by "+n1+"." );
            } else {
                ids.put( c.id, c );
            }
            if ( ids.get(c.plotA)==null ) {
                problems.add( "Connector refers to plot '"+c.plotA +"' which is not found: "+c );
            }
            if ( ids.get(c.plotB)==null ) {
                problems.add( "Connector refers to plot '"+c.plotB +"' which is not found: "+c );
            }            
        }
        for ( Annotation a: dom.annotations ) {
            DomNode n1= ids.get(a.id);
            if ( n1!=null ) {
                problems.add( "Annotation id is already taken by "+n1+"." );
            } else {
                ids.put( a.id, a );
            }
            if ( a.getPlotId().length()>0 && ids.get(a.getPlotId())==null ) {
                problems.add( "Annotation refers to plot '"+a.getPlotId() +"' which is not found: "+a );
            }
            if ( a.getColumnId().trim().length()>0 && ids.get(a.getColumnId())==null ) {
                problems.add( "Annotation refers to column '"+a.getColumnId() +"' which is not found: "+a );
            }
            if ( a.getRowId().trim().length()>0 && ids.get(a.getRowId())==null ) {
                problems.add( "Annotation refers to row '"+a.getRowId() +"' which is not found: "+a );
            }
        }
        return problems;
    }

    /**
     * copy over "vap+internal:" data, where data is found in the controller but no URI represents it.
     * This is to support https://sourceforge.net/p/autoplot/bugs/2332/, where we now copy over any data
     * we find as well.
     * @param srcdom the src dom with controllers and possibly internal data.
     * @param dstdom the dst dom with controllers.
     */
    public static void copyOverInternalData(Application srcdom, Application dstdom) {
        DataSourceFilter[] srcdsfs= srcdom.getDataSourceFilters();
        DataSourceFilter[] dstdsfs= dstdom.getDataSourceFilters();
        if ( srcdsfs.length!=dstdsfs.length ) {
            throw new IllegalArgumentException("src and dest doms must be the same length.");
        }
        if ( dstdsfs.length>0 && dstdsfs[0].getController()==null ) {
            logger.warning("destination does not have controllers, internal data in src will be ignored.");
            return;
        }
        for ( int i=0; i<srcdsfs.length; i++ ) {
            if ( srcdsfs[i].getUri().equals("vap+internal:") && srcdsfs[i].getController()!=null ) {
                QDataSet ds= srcdsfs[i].getController().getFillDataSet();
                dstdsfs[i].getController().setDataSetInternal(ds);
            }
        }
        
    }
}
