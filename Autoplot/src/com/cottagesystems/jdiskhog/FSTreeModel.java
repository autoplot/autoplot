/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cottagesystems.jdiskhog;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import org.autoplot.datasource.AutoplotSettings;

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
            this.windows= System.getProperty("os.name").toLowerCase(Locale.US).startsWith("win");
        }

        public File getFile() {
            return f;
        }

        boolean windows= true;
        
        private String fromWindows( String s ) {
            if ( windows ) {
                return s.replace('\\','/');
            } else {
                return s;
            }
        }
        
        private String getName() {
            if (abs) {
                return fromWindows( f.toString() );
            } else {
                if (parentFile == null) {
                    return fromWindows( f.getName() );
                } else {
                    return fromWindows( f.toString().substring(parentFile.toString().length() + 1) );
                }
            }
        }

        @Override
        public String toString() {
            if (f.isFile()) {
                return getName() + " " + nf.format(f.length() / 1000000.) + " MB";
            } else {
                if (f.exists()) {
                    Long usages = model.usage(f);
                    if (usages == null) {
                        return getName() + "                    ";
                    } else {
                        return getName() + " " + nf.format(model.usage(f)/1000.) + " MB";
                    }
                } else {
                    return getName() + " 0 MB";
                }
            }
        }
    }
    DiskUsageModel model;
    File root;
    Map<File, File[]> listings = new HashMap<>();
    Comparator comparator;

//    private Object getTreeNodeFor( int i ) {
//        try {
//            Object child= getChild( getRoot(), i );
//            return child;
//        } catch ( NullPointerException ex ) {
//            return null;
//        }
//    }
    
    public FSTreeModel( DiskUsageModel model, File root ) {
        this.model = model;
//        model.addPropertyChangeListener( new PropertyChangeListener() {
//            @Override
//            public void propertyChange(PropertyChangeEvent evt) {
//                TreeModelEvent ev=null;
//                if ( evt.getPropertyName().equals("readyFolderCount") ) {
//                    System.err.println(evt);
//                    Object child= getTreeNodeFor( (Integer)evt.getNewValue() );
//                    if ( child!=null ) {
//                        ev= new TreeModelEvent( FSTreeModel.this, new Object[] { child } ); 
//                    }
//                }
//                
//                if ( ev!=null ) {
//                    for (TreeModelListener tml : listeners ) {
//                        tml.treeNodesChanged( ev );
//                    }
//                }
//            }
//        });
//        
        this.root = root;
        Preferences prefs= AutoplotSettings.settings().getPreferences( FSTreeModel.class );
        String sort= prefs.get( "fsTreeSort", "size" );
        switch (sort) {
            case "size":
                this.comparator= fileSizeComparator;
                break;
            case "alpha":
                this.comparator= alphaComparator;
                break;
            default:
                System.err.println("bad fsTreeSort value: "+sort);
                this.comparator= fileSizeComparator;
                break;
        }
    }
    
    public void setComparator( Comparator c ) {
        logger.log(Level.FINE, "set comparator to {0}", c);
        Comparator old= this.comparator;
        this.comparator= c;
        if ( old!=c ) {
            Preferences prefs= AutoplotSettings.settings().getPreferences( FSTreeModel.class );
            prefs.put( "fsTreeSort", c==fileSizeComparator ? "size" : "alpha" );
            try {
                prefs.flush();
            } catch ( BackingStoreException ex ) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
            System.err.println("reset comparator to "+c );
            listings = new HashMap<>();
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

    @Override
    public Object getRoot() {
        return new TreeNode(root, null, true);
    }

    @Override
    public Object getChild(Object parent, int index) {
        File fparent = ((TreeNode) parent).getFile();
        File[] ff = listings.get(fparent);

        File ff1 = ff[index];
        while (ff1.isDirectory()) {
            File[] ffs = ff1.listFiles(); 
            if ( ffs!=null && ffs.length == 1 && ffs[0].isDirectory()) {
                ff1 = ffs[0];
            } else {
                break;
            }
        }

        return new TreeNode(ff1, fparent);
    }
    
    public Comparator fileSizeComparator = new Comparator() {
        @Override
        public String toString() {
            return "fileSize";
        }

        @Override
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

    public static final Comparator alphaComparator = new Comparator() {

        @Override
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

        @Override
        public String toString() {
            return "alpha";
        }
    };

    @Override
    public int getChildCount(Object parent) {
        final File f = ((TreeNode) parent).getFile();
        if (!f.exists()) {
            return 0;
        }
        if ( f.isFile() ) {
            return 0;
        }
        File[] ff = listings.get(f);
        if (ff == null) {
            ff = f.listFiles();
            assert ff!=null;
            if (hideListingFile) {
                List<File> lff = new ArrayList(Arrays.asList(ff));
                lff.remove(new File(f, ".listing"));
                ff = lff.toArray(new File[lff.size()]);
            }

            if ( comparator!=null ) {
                logger.log(Level.FINER, "sorting by comparator: {0}", comparator);
                Arrays.sort(ff, comparator );
            }
            listings.put(f, ff);
        }
        return ff.length;
    }

    @Override
    public boolean isLeaf(Object node) {
        File f = ((TreeNode) node).getFile();
        return f.isFile();
    }

    @Override
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

    @Override
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

    @Override
    public void addTreeModelListener(TreeModelListener l) {
        listeners.add(l);
    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
        listeners.remove(l);
    }
}
