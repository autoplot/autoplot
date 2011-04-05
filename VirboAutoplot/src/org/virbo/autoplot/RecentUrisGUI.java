/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * RecentUrisGUI.java
 *
 * Created on Apr 5, 2011, 10:26:30 AM
 */

package org.virbo.autoplot;

import java.awt.Component;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.text.ParseException;
import java.util.Iterator;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import org.das2.components.DasProgressPanel;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.TimeParser;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.AutoplotSettings;

/**
 *
 * @author jbf
 */
public class RecentUrisGUI extends javax.swing.JPanel {

    String selectedURI=null;

    /** Creates new form RecentUrisGUI */
    public RecentUrisGUI() {
        initComponents();
        jTree1.setModel(new MyTreeModel());
        jTree1.setCellRenderer( new MyCellRenderer() );
        Object r= jTree1.getModel().getRoot();
        jTree1.expandPath( new TreePath( new Object[] { r, jTree1.getModel().getChild( r,0 ) } ) );
        jTree1.addTreeSelectionListener( new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                String[] pp= (String[]) e.getPath().getLastPathComponent();
                selectedURI= pp[1];
            }
        });
        
    }

    public String getSelectedURI() {
        return selectedURI;
    }

    public static void main( String[] args ) {
        RecentUrisGUI t= new RecentUrisGUI();
        int i= JOptionPane.showConfirmDialog( null, t );
        if ( i==JOptionPane.OK_OPTION ) {
            System.err.println( t.getSelectedURI() );
        }
    }

    class MyCellRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            if (value instanceof String[]) {
                value = ((String[])value)[1];
            }
            return super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        }
    }
    
    class MyTreeModel implements TreeModel {

        private final Object root= new Object();

        private TreeMap<Datum,String[]> uris;

        private DatumRange[] list;
        int listlen=6;

        MyTreeModel() {
            try {
                Datum now = TimeUtil.now();
                list = new DatumRange[listlen];
                list[0] = new DatumRange(TimeUtil.prevMidnight(now), TimeUtil.nextMidnight(now));
                list[1] = list[0].previous();
                list[2] = new DatumRange(TimeUtil.prevWeek(list[1].min()), list[1].min());
                list[3] = new DatumRange(TimeUtil.prev(TimeUtil.MONTH, list[2].min()), list[2].min());
                list[4] = new DatumRange(TimeUtil.prev(TimeUtil.QUARTER, list[3].min()), list[3].min());
                list[5] = new DatumRange(Datum.create(0, Units.t1970), list[4].min());
                File f2 = new File(AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA), "bookmarks/");
                if (!f2.exists()) {
                    boolean ok = f2.mkdirs();
                    if (!ok) {
                        throw new RuntimeException("unable to create folder " + f2);
                    }
                }
                uris= new TreeMap<Datum, String[]>();
                TimeParser tp= TimeParser.create( TimeParser.TIMEFORMAT_Z);

                String lastURI= null;

                final File f3 = new File(f2, "history.txt");
                Scanner scan = new Scanner(f3);
                while (scan.hasNextLine()) {
                    String line = scan.nextLine();
                    String[] ss= line.split("\\s+",2);
                    try {
                        if ( !ss[1].equals(lastURI) ) {
                            uris.put(tp.parse(ss[0]).getTimeDatum(), ss);
                            lastURI= ss[1];
                        }
                    } catch (ParseException ex) {
                        Logger.getLogger(RecentUrisGUI.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

            } catch (FileNotFoundException ex) {
                Logger.getLogger(RecentUrisGUI.class.getName()).log(Level.SEVERE, null, ex);
            }


        }

        public Object getRoot() {
            return root;
        }

        public Object getChild( Object parent, int index ) {
            if ( parent==root ) {
                return list[index];
            }else if (parent instanceof DatumRange) {
                DatumRange range = (DatumRange)parent;
                SortedMap<Datum,String[]> submap = uris.subMap(range.min(), range.max());
                Iterator i= submap.values().iterator();
                for ( int j=0; j<index; j++ ) {
                    i.next();
                }
                return i.next();
            } else {
                return new IndexOutOfBoundsException("no child at index="+index);
            }
        }

        public int getChildCount( Object parent ) {
            if ( parent==root ) {
                return list.length;
            } else if (parent instanceof DatumRange) {
                DatumRange range = (DatumRange)parent;
                SortedMap<Datum,String[]> submap = uris.subMap(range.min(), range.max());
                return submap.size();
            } else {
                return 0;
            }
        }

        public boolean isLeaf(Object node) {
            return node instanceof String[];
        }

        public void valueForPathChanged(TreePath path, Object newValue) {
            
        }

        public int getIndexOfChild(Object parent, Object child) {
            for ( int i=0; i<getChildCount(parent); i++ ) {
                if ( getChild(parent,i)==child ) {
                    return i;
                }
            }
            return -1;
        }

        public void addTreeModelListener(TreeModelListener l) {
            
        }

        public void removeTreeModelListener(TreeModelListener l) {
            
        }

    }



    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        jTree1 = new javax.swing.JTree();

        jTree1.setRootVisible(false);
        jScrollPane1.setViewportView(jTree1);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTree jTree1;
    // End of variables declaration//GEN-END:variables

}
