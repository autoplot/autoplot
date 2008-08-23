/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cottagesystems.jdiskhog;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 *
 * @author jbf
 */
public class FSTreeModel implements TreeModel {

    DecimalFormat nf = new DecimalFormat();

    class TreeNode {

        File parentFile;
        File f;

        TreeNode(File f) {
            this( f, null );
        }
        
        TreeNode( File f, File parentFile ) {
            this.f= f;
            this.parentFile= parentFile;
        }

        public File getFile() {
            return f;
        }

        private String getName() {
            if ( parentFile==null ) {
                return f.getName();
            } else {
                return f.toString().substring( parentFile.toString().length()+1 );
            }
        }
        
        public String toString() {
            if (f.isFile()) {
                return getName() + " " + nf.format(f.length() / 1000) + " KB";
            } else {
                Long usages = model.usage(f);
                if (usages == null) {
                    return getName() + " ??? KB";
                } else {
                    return getName() + " " + nf.format(model.usage(f)) + " KB";
                }
            }
        }
    }
    DiskUsageModel model;
    File root;
    Map<File, File[]> listings = new HashMap<File, File[]>();

    FSTreeModel(DiskUsageModel model, File root) {
        this.model = model;
        this.root = root;
    }

    public Object getRoot() {
        return new TreeNode(root);
    }

    public Object getChild(Object parent, int index) {
        File fparent= ((TreeNode) parent).getFile();
        File[] ff = listings.get(fparent);

        File reportParent= null;
        
        File ff1 = ff[index];
        while ( ff1.isDirectory() ) {
            File[] ffs = ff1.listFiles();
            if (ffs.length == 1) {
                ff1 = ffs[0];
            } else {
                break;
            }
            reportParent= fparent;
        }

        return new TreeNode(ff1,fparent);
    }

    public synchronized int getChildCount(Object parent) {
        final File f = ((TreeNode) parent).getFile();
        File[] ff = listings.get(f);
        if (ff == null) {
            ff = f.listFiles();
            Arrays.sort(ff, new Comparator() {

                public int compare(Object o1, Object o2) {
                    long s1, s2;
                    File f1 = (File) o1, f2 = (File) o2;

                    if (f1 == null || f2 == null) {
                        System.err.println("" + f + " lists null!");
                        return -1;
                    }


                    if (f1.isFile()) {
                        s1 = f1.length() / 1000;
                    } else {
                        Long l = model.usage(f1);
                        if (l == null) {
                            s1 = 0;
                        } else {
                            s1 = l;
                        }
                    }

                    if (f2.isFile()) {
                        s2 = f2.length() / 1000;
                    } else {
                        Long l = model.usage(f2);
                        if (l == null) {
                            s2 = 0;
                        } else {
                            s2 = l;
                        }
                    }

                    if (f1.isFile() && f2.isFile()) {
                        String key1 = ((File) o1).getName();
                        String key2 = ((File) o2).getName();
                        return key1.compareTo(key2);

                    } else if (f1.isDirectory() && f2.isDirectory()) {
                        return s1 < s2 ? 1 : -1;
                    } else {
                        return f1.isDirectory() ? -1 : 1;
                    }

                }
            });
            listings.put(f, ff);
        }
        return ff.length;
    }

    public boolean isLeaf(Object node) {
        File f = ((TreeNode) node).getFile();
        return f.isFile();
    }

    public void valueForPathChanged(TreePath path, Object newValue) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public File getFile(TreePath context) {
        Object node = context.getPathComponent(context.getPathCount() - 1);
        return ((TreeNode) node).getFile();
    }

    public int getIndexOfChild(Object parent, Object child) {
        File f = ((TreeNode) parent).getFile();
        File fchild = ((TreeNode) child).getFile();
        File[] ff = listings.get(f);
        for (int i = 0; i < ff.length; i++) {
            if (ff[i].equals(fchild)) {
                return i;
            }
        }
        throw new IllegalArgumentException("bad child");
    }
    ArrayList listeners = new ArrayList();

    public void addTreeModelListener(TreeModelListener l) {
        listeners.add(l);
    }

    public void removeTreeModelListener(TreeModelListener l) {
        listeners.remove(l);
    }
}
