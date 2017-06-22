
package org.autoplot.datasource;

import java.util.HashMap;
import java.util.Map;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;

/**
 * Maps various metadata models to set of canonical name/value pairs
 * See QDataSet properties for list of properties.
 * @author jbf
 */
public abstract class MetadataModel {
    
    public static MetadataModel createNullModel() {
        return new MetadataModel() {
            @Override
            public Map<String, Object> properties(Map<String,Object> meta) {
                return new HashMap<>();
            }  
        };
    }
    /**
     * method for copying tree when the tree does not provide random access.
     * @param src
     * @return 
     */
    public static TreeModel copyTree( TreeModel src ) {
        MutableTreeNode destPos= new DefaultMutableTreeNode( src.getRoot() );
        return copyTree( "", src, src.getRoot(), new DefaultTreeModel( destPos ), destPos );
    }
    
    private static TreeModel copyTree( String prefix, TreeModel src, Object srcPos, DefaultTreeModel dest, MutableTreeNode destPos ) {
        
        String myPath= prefix + "/" + String.valueOf(srcPos);
        
        int  cc;
        cc = src.getChildCount(srcPos);
        for( int i=0; i < cc; i++) {
            Object child = src.getChild( srcPos, i );
            MutableTreeNode cchild= new DefaultMutableTreeNode(child);
            if (src.isLeaf(child)) {
                dest.insertNodeInto( cchild, destPos, i );
            } else {
                dest.insertNodeInto( cchild, destPos, i );
                copyTree( myPath, src, child, dest, cchild );
            }
        }
        
        return dest;
    }
    
    private static int indexOfChild( TreeNode node, String value ) {
        for ( int j=0; j<node.getChildCount(); j++ ) {
            TreeNode child= node.getChildAt( j );
            String schild= child.toString();
            if ( schild.equals(value) ) {
                return j;
            }
        }
        return -1;
    }

    
    /**
     * drills down through the Maps.  This returns value.
     * @param tree
     * @param path
     * @return 
     */
    public static String getNodeValue( Map<String,Object> tree, String[] path ) {

        Object o= tree.get( path[1] );
        
        for ( int i=2; i<path.length; i++ ) {
            Map<String,Object> subTree= (Map<String,Object>) o;
            o= subTree.get(path[i]);
        }
        
        return String.valueOf(o);
    }
    

    /**
     * drills down through the Maps.  This returns value.
     * @param tree
     * @param path
     * @return 
     */
    public static Object getNode( Map<String,Object> tree, String[] path ) {

        Object o= tree.get( path[1] );
        
        for ( int i=2; i<path.length; i++ ) {
            Map<String,Object> subTree= (Map<String,Object>) o;
            o= subTree.get(path[i]);
        }
        
        return o;
    }
    
    
    /**
     * assumes the leaves of the tree are "name=value" pairs.  This returns value.
     * @param tree
     * @param path
     * @return the node value.
     */
    public static String getNodeValue( TreeModel tree, String[] path ) {
        
        if ( !path[0].equals(tree.getRoot().toString()) ) throw new IllegalArgumentException("root node didn't match");
        int index= indexOfChild( (TreeNode)tree.getRoot(), path[1] );
        if ( index==-1 ) throw new IllegalArgumentException("node didn't match: "+path[0]);
        Object pos= tree.getChild( tree.getRoot(), index );
        
        for ( int i=2; i<path.length-1; i++ ) {
            index= indexOfChild( (TreeNode)pos, path[i] );
            if ( index==-1 ) throw new IllegalArgumentException("node didn't match: "+path[i]);
            pos= tree.getChild( pos, index );
        }
        
        String result= null;
        
        String leafName= path[path.length-1];
        for ( int j=0; j<tree.getChildCount(pos); j++ ) {
            Object child= tree.getChild( pos, j );
            String schild= child.toString();
            if ( schild.startsWith( leafName ) ) {
                if ( schild.charAt( leafName.length() ) =='=' ) {
                    result= schild.substring(leafName.length()+1);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Derive QDataSet properties from inspection of the metadata tree.
     * DEPEND_0, etc are Map&lt;String,Object&gt;.
     * @param meta model provided by DataSource
     * @return Map with properties such as QDataSet.TITLE
     */
    public abstract Map<String,Object> properties( Map<String,Object> meta );
    
    public String getLabel() {
        return "";
    }
}
