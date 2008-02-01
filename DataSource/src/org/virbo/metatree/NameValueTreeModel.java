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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

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

        String name;
        String value;
        boolean hasKids;

        StringPropertyNode(String name, String value) {
            this.name = name;
            this.value = value;
            this.hasKids = value.length() > 50;
        }

        public String toString() {
            if (!hasKids) {
                return "" + name + "=" + value;
            } else {
                return "" + name + "=" + value.substring(0, 50) + " ...";
            }
        }

        public int childCount() {
            return hasKids ? value.length() / 50 + 1 : 0;
        }

        public Object getChild(int i) {
            int i0 = i * 50;
            int i1 = Math.min((i + 1) * 50, value.length());
            while (i0 > 0 && i0 < value.length() && !Character.isWhitespace(value.charAt(i0 - 1))) {
                i0++;
            }
            while (i1 < value.length() && !Character.isWhitespace(value.charAt(i1))) {
                i1++;
            }
            return value.substring(i0, i1);
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
            return "" + name + "= MAP with " + value.size()+ " pairs";
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

    static Object createNode(String name, Object value) {
        if (value.getClass().isArray()) {
            return new ArrayPropertyNode(name, value);
        } else if (value instanceof String) {
            return new StringPropertyNode(name, (String) value);
        } else if (value instanceof Map) {
            return new MapPropertyNode(name, (Map) value);
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
        ArrayList nodes = new ArrayList(map.size());

        for (Iterator i = map.keySet().iterator(); i.hasNext();) {
            Object key = i.next();
            nodes.add(createNode((String) key, map.get(key)));
        }

        return new NameValueTreeModel(root, nodes);
    }

    public String toString() {
        return String.valueOf(this.root) + "(" + nodes.size() + "key/value pairs)";
    }
}
