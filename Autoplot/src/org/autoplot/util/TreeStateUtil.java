
package org.autoplot.util;

/**
 *
 * @author jbf
 */
import javax.swing.*;
import javax.swing.tree.*;
import java.util.*;

public class TreeStateUtil {

    /**
     * Save expanded paths as strings.
     * @param tree the tree
     * @return set representing expanded paths
     */
    public static Set<String> saveExpandedPaths(JTree tree) {
        Set<String> result = new HashSet<>();

        TreeModel model = tree.getModel();
        if ( model==null ) return Collections.emptySet();
        
        Object root = model.getRoot();

        Enumeration<TreePath> e =
                tree.getExpandedDescendants(new TreePath(root));

        if (e != null) {
            while (e.hasMoreElements()) {
                TreePath tp = e.nextElement();
                result.add(pathToString(tp));
            }
        }

        return result;
    }

    
    /**
     * Restore expanded paths after model switch.
     * @param tree
     * @param expandedPaths
     */
    public static void restoreExpandedPaths(
            JTree tree,
            Set<String> expandedPaths) {

        TreeModel model = tree.getModel();
        Object root = model.getRoot();

        expandMatching(tree,
                new TreePath(root),
                expandedPaths);
    }

    private static void expandMatching(
            JTree tree,
            TreePath parent,
            Set<String> expandedPaths) {

        String s = pathToString(parent);

        if (expandedPaths.contains(s) ) {
            tree.expandPath(parent);
        }

        Object node = parent.getLastPathComponent();
        TreeModel model = tree.getModel();

        int count = model.getChildCount(node);

        for (int i = 0; i < count; i++) {
            Object child = model.getChild(node, i);

            expandMatching(tree,
                    parent.pathByAddingChild(child),
                    expandedPaths);
        }
    }

    /**
     * Convert TreePath to stable string.
     */
    private static String pathToString(TreePath path) {
        Object[] objs = path.getPath();

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < objs.length; i++) {
            if (i > 0) sb.append("/");
            if (i==0 ) {
                sb.append("dataset"); // kludge for Autoplot: ignore the URI which is the root.
            } else {
                String s= objs[i].toString();
                int ieq= s.indexOf("=");
                if ( ieq>-1 ) {
                    s= s.substring(0,ieq);
                }
                sb.append(s);
            }
        }

        return sb.toString();
    }
}