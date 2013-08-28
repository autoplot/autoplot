/*
 * NameValueTreeModel.java
 *
 * Created on July 31, 2007, 11:45 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.metatree;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import org.virbo.dataset.QDataSet;
import org.virbo.dsutil.PropertiesTreeModel;

/**
 *
 * @author jbf
 */
public class NameValueTreeModel implements TreeModel {

    final Object root;
    List nodes;

    public interface TreeNode {
        int childCount();
        Object getChild(int i);
        boolean isLeaf();
        String toString();
    }

    static class StringPropertyNode implements TreeNode {
        private static final int LINE_LEN = 133;

        String name;
        String value;
        List<Integer> splits;
        boolean hasKids;

        StringPropertyNode(String name, String value) {
            this.name = name;
            value= value.trim();
            this.value = value;
            if ( value.length()>LINE_LEN || value.contains("\n") ) {
                splits= new ArrayList<Integer>();
                int ii=0;
                while ( ii<value.length() ) {
                    int i= value.indexOf("\n",ii);
                    if ( (i-ii)>LINE_LEN || i==-1 ) {
                        if ( value.length()-ii < LINE_LEN ) {
                            ii= value.length();
                        } else {
                            i= value.lastIndexOf(" ",ii+LINE_LEN);
                            if ( i==-1 ) {
                                ii= ii+LINE_LEN; 
                            } else if ( i>ii ) {
                                ii= i+1;
                            } else {
                                ii= value.length(); // couldn't find a break
                            }
                        }
                    } else {
                        ii= i+1;
                    }
                    if ( ii<value.length() ) splits.add(ii);
                }
            }
            this.hasKids = splits != null;
        }

        @Override
        public String toString() {
            if (!hasKids) {
                return "" + name + "=" + value;
            } else {
                return "" + name + "=" + value.substring(0, splits.get(0) ) + " ...";
            }
        }

        public int childCount() {
            return hasKids ? splits.size() + 1 : 0;
        }

        public Object getChild(int i) {
            if ( i==0 ) {
                return value.substring( 0,splits.get(i) );
            } else if ( i==splits.size() ) {
                return value.substring(splits.get(i-1));
            } else {
                return value.substring( splits.get(i-1), splits.get(i) );
            }
        }

        public boolean isLeaf() {
            return !hasKids;
        }
    }

    static class ArrayPropertyNode implements TreeNode {

        String name;
        Object value;
        boolean hasKids;

        ArrayPropertyNode(String name, Object value) {
            this.name = name;
            this.value = value;
            this.hasKids = true;
        }

        public String toString() {
            return "" + name + "=[" + Array.getLength(value) + "]";
        }

        public int childCount() {
            return Array.getLength(value);
        }

        public Object getChild(int i) {
            return createNode("[" + i + "]", Array.get(value, i));
        }

        public boolean isLeaf() {
            return !hasKids;
        }
    }

    static class MapPropertyNode implements TreeNode {

        String name;
        Map value;
        boolean hasKids;
        String[] keys;

        MapPropertyNode(String name, Map<String,Object> value) {
            this.name = name;
            this.value = value;
            this.keys= (String[]) this.value.keySet().toArray( new String[ value.size() ] );
            this.hasKids = this.keys.length>0;
        }

        public String toString() {
            return "" + name ;
        }

        public int childCount() {
            return value.size();
        }

        public Object getChild(int i) {
            String key= keys[i];
            return createNode( key, value.get(key)  );
        }

        public boolean isLeaf() {
            return !hasKids;
        }
    }

    static class TreeNodeAdapter implements TreeNode {
        javax.swing.tree.TreeNode node;
        String name;
        TreeNodeAdapter( String name, javax.swing.tree.TreeNode node ) {
            this.node= node;
            this.name= name;
        }
        public int childCount() {
            return node.getChildCount();
        }

        public Object getChild(int i) {
            return new TreeNodeAdapter( null, node.getChildAt(i) );
        }

        public boolean isLeaf() {
            return node.isLeaf();
        }
        public String toString() {
            if ( name!=null ) {
                return name + "=" + node.toString();
            } else {
                return node.toString();
            }
        }

    }

    static Object createNode(String name, Object value) {
        if ( value==null ) {
            return new StringPropertyNode(name, "null" );
        } else if (value.getClass().isArray()) {
            return new ArrayPropertyNode(name, value);
        } else if (value instanceof String) {
            String svalue= (String) value;
            if ( svalue.length()>800 ) {
                svalue= svalue.substring(0,797) + "...";
            }
            return new StringPropertyNode(name, svalue);
        } else if (value instanceof Map) {
            return new MapPropertyNode(name, (Map) value);
        } else if (value instanceof QDataSet ) {
            PropertiesTreeModel model= new PropertiesTreeModel((QDataSet)value,100);
            return new TreeNodeAdapter( name, (javax.swing.tree.TreeNode)model.getRoot() );
        } else {
            return new StringPropertyNode(name, String.valueOf(value));
        }
    }

    public Object getRoot() {
        return root;
    }

    public Object getChild(Object parent, int index) {
        if (parent == root) {
            return nodes.get(index);
        } else {
            TreeNode p = (TreeNode) parent;
            return p.getChild(index);
        }
    }

    public int getChildCount(Object parent) {
        if (parent == root) {
            return nodes.size();
        } else {
            TreeNode p = (TreeNode) parent;
            return p.childCount();
        }
    }

    public boolean isLeaf(Object parent) {
        if (parent == root) {
            return false;
        } else if (parent instanceof TreeNode) {
            TreeNode p = (TreeNode) parent;
            return p.isLeaf();
        } else {
            return true;
        }
    }

    public void valueForPathChanged(TreePath path, Object newValue) {
    }

    public int getIndexOfChild(Object parent, Object child) {
        return 0;
    }

    public void addTreeModelListener(TreeModelListener l) {
    }

    public void removeTreeModelListener(TreeModelListener l) {
    }

    /** Creates a new instance of NameValueTreeModel */
    private NameValueTreeModel(Object root, List nodes) {
        this.nodes = nodes;
        this.root = root;
    }

    public static NameValueTreeModel create(Object root, List names, List values) {
        ArrayList nodes = new ArrayList(names.size());
        for (int i = 0; i < names.size(); i++) {
            nodes.add(createNode((String) names.get(i), values.get(i)));
        }
        return new NameValueTreeModel(root, nodes);
    }

    /**
     * creates a new tree from a hash map.
     * @param root a node to use as the root.  Often this will just be a string identifying the properties set like "metadata(cdf)"
     * @param map Map that are translated to nodes.  Most value types result in <name>=<value>, while values that are
     *    Maps may be handled recursively.
     * @return
     */
    public static NameValueTreeModel create(Object root, Map map) {
        List nodes;
        if ( map!=null ) {
            nodes = new ArrayList(map.size());
            
            for ( Iterator i = map.entrySet().iterator(); i.hasNext();) {
                Entry e= (Entry)i.next();
                nodes.add( createNode( String.valueOf(e.getKey()), e.getValue() ) );
            }
        } else {
            nodes= Collections.EMPTY_LIST;
        }

        return new NameValueTreeModel( root, nodes );
    }

    public String toString() {
        return String.valueOf(this.root) + "(" + nodes.size() + "key/value pairs)";
    }
}
