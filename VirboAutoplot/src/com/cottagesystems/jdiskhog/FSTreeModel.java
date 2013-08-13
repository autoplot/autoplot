/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cottagesystems.jdiskhog;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 *
 * @author jbf
 */
public class FSTreeModel implements TreeModel {

    DecimalFormat nf = new DecimalFormat();
    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.jdiskhog");

    class TreeNode {

        File parentFile;
        File f;
        boolean abs;

        TreeNode(File f) {
            this(f, null);
        }

        TreeNode(File f, File parentFile) {
            this(f, parentFile, false);
        }

        TreeNode(File f, File parentFile, boolean abs) {
            this.f = f;
            this.parentFile = parentFile;
            this.abs = abs;
        }

        public File getFile() {
            return f;
        }

        private String getName() {
            if (abs) {
                return f.toString();
            } else {
                if (parentFile == null) {
                    return f.getName();
                } else {
                    return f.toString().substring(parentFile.toString().length() + 1);
                }
            }
        }

        public String toString() {
            if (f.isFile()) {
                return getName() + " " + nf.format(f.length() / 1000) + " KB";
            } else {
                if (f.exists()) {
                    Long usages = model.usage(f);
                    if (usages == null) {
                        return getName() + " ??? KB";
                    } else {
                        return getName() + " " + nf.format(model.usage(f)) + " KB";
                    }
                } else {
                    return getName() + " 0 KB";
                }
            }
        }
    }
    DiskUsageModel model;
    File root;
    Map<File, File[]> listings = new HashMap<File, File[]>();
    Comparator comparator;

    FSTreeModel( DiskUsageModel model, File root ) {
        this.model = model;
        this.root = root;
        Preferences prefs= Preferences.userNodeForPackage( FSTreeModel.class );
        String sort= prefs.get( "fsTreeSort", "size" );
        if ( sort.equals("size") ) {
            this.comparator= fileSizeComparator;
        } else if ( sort.equals("alpha" ) ) {
            this.comparator= alphaComparator;
        } else {
            System.err.println("bad fsTreeSort value: "+sort);
            this.comparator= fileSizeComparator;
        }
    }
    
    public void setComparator( Comparator c ) {
        System.err.println("set comparator to "+c );
        Comparator old= this.comparator;
        this.comparator= c;
        if ( old!=c ) {
            Preferences prefs= Preferences.userNodeForPackage( FSTreeModel.class );
            prefs.put( "fsTreeSort", c==fileSizeComparator ? "size" : "alpha" );
            try {
                prefs.flush();
            } catch ( BackingStoreException ex ) {
                logger.log( Level.SEVERE, null, ex );
            }
            System.err.println("reset comparator to "+c );
            listings = new HashMap<File, File[]>();
            fireTreeStructureChanged();
        }
    }

    public Comparator getComparator() {
        return this.comparator;
    }
    
    protected void fireTreeStructureChanged( ) {
        TreeModelEvent e = new TreeModelEvent(this,new Object[] {root});
        for (TreeModelListener tml : listeners ) {
            tml.treeStructureChanged(e);
        }
    }

    public boolean hideListingFile = false;

    public boolean isHideListingFile() {
        return hideListingFile;
    }

    public void setHideListingFile(boolean hideListingFile) {
        this.hideListingFile = hideListingFile;
    }

    public Object getRoot() {
        return new TreeNode(root, null, true);
    }

    public Object getChild(Object parent, int index) {
        File fparent = ((TreeNode) parent).getFile();
        File[] ff = listings.get(fparent);

        File reportParent = null;

        File ff1 = ff[index];
        while (ff1.isDirectory()) {
            File[] ffs = ff1.listFiles();
            if (ffs.length == 1 && ffs[0].isDirectory()) {
                ff1 = ffs[0];
            } else {
                break;
            }
            reportParent = fparent;
        }

        return new TreeNode(ff1, fparent);
    }
    
    public Comparator fileSizeComparator = new Comparator() {
        public String toString() {
            return "fileSize";
        }

        public int compare(Object o1, Object o2) {
            long s1, s2;
            File f1 = (File) o1, f2 = (File) o2;

            if (f1 == null || f2 == null) {
                //System.err.println("" + f + " lists null!");
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
    };

    public static Comparator alphaComparator = new Comparator() {

        public int compare(Object o1, Object o2) {
            File f1 = (File) o1, f2 = (File) o2;

            if (f1 == null || f2 == null) {
                //System.err.println("" + f + " lists null!");
                return -1;
            }

            String key1 = ((File) o1).getName();
            String key2 = ((File) o2).getName();
            return key1.compareTo(key2);
        }

        public String toString() {
            return "alpha";
        }
    };

    public synchronized int getChildCount(Object parent) {
        final File f = ((TreeNode) parent).getFile();
        if (!f.exists()) {
            return 0;
        }
        File[] ff = listings.get(f);
        if (ff == null) {
            ff = f.listFiles();
            if (hideListingFile) {
                List<File> lff = new ArrayList(Arrays.asList(ff));
                lff.remove(new File(f, ".listing"));
                ff = lff.toArray(new File[lff.size()]);
            }

            logger.log(Level.FINER, "sorting by comparator: {0}", comparator);
            Arrays.sort(ff, comparator );
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

    private boolean isParentOf(File parent, File child) {
        try {
            return child.getCanonicalPath().startsWith(parent.getCanonicalPath());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public int getIndexOfChild(Object parent, Object child) {
        File f = ((TreeNode) parent).getFile();
        File fchild = ((TreeNode) child).getFile();
        File[] ff = listings.get(f);
        for (int i = 0; i < ff.length; i++) {
            if (ff[i].equals(fchild) || isParentOf(ff[i], fchild)) {
                return i;
            }
        }
        throw new IllegalArgumentException("bad child");
    }
    ArrayList<TreeModelListener> listeners = new ArrayList();

    public void addTreeModelListener(TreeModelListener l) {
        listeners.add(l);
    }

    public void removeTreeModelListener(TreeModelListener l) {
        listeners.remove(l);
    }
}
