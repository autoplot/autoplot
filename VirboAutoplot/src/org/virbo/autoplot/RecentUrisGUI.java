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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.TimeParser;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
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
                Object o= e.getPath().getLastPathComponent();
                if ( o instanceof String[] ) {
                    String[] pp= (String[]) o;
                    selectedURI= pp[1];
                }
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

    private String filter;
    void setFilter(String filter) {
        this.filter= filter;
        update();
    }

    private void update() {
        jTree1.setModel( new MyTreeModel() );
    }

    class MyCellRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            if (value instanceof String[]) {
                value = ((String[])value)[1];
            } else if ( value instanceof DatumRange ) {
                int count= tree.getModel().getChildCount(value);
                value= String.format( "%s (%d)", value, count );
            }
            return super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        }
    }
    
    final class MyTreeModel implements TreeModel {

        private final Object root= new Object();

        private TreeMap<Datum,String[]> uris;

        private DatumRange[] list;
        private boolean[] skip;

        MyTreeModel() {
            try {
                Datum now = TimeUtil.now();
                list = new DatumRange[8];
                list[0] = new DatumRange(TimeUtil.prevMidnight(now), TimeUtil.nextMidnight(now));
                list[1] = list[0].previous();
                list[2] = new DatumRange(TimeUtil.prevWeek(list[1].min()), list[1].min());
                list[3] = new DatumRange(TimeUtil.prev(TimeUtil.MONTH, list[2].min()), list[2].min());
                list[4] = new DatumRange(TimeUtil.prev(TimeUtil.QUARTER, list[3].min()), list[3].min());
                list[5] = new DatumRange(TimeUtil.prev(TimeUtil.QUARTER, list[4].min()), list[4].min());
                list[6] = new DatumRange(TimeUtil.prev(TimeUtil.HALF_YEAR, list[5].min()), list[5].min());
                list[7] = new DatumRange(Datum.create(0, Units.t1970), list[6].min());
                File f2 = new File(AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA), "bookmarks/");
                if (!f2.exists()) {
                    boolean ok = f2.mkdirs();
                    if (!ok) {
                        throw new RuntimeException("unable to create folder " + f2);
                    }
                }
                uris= new TreeMap<Datum, String[]>();
                TimeParser tp= TimeParser.create( TimeParser.TIMEFORMAT_Z);

                LinkedHashMap<String,String> daysURIs= new LinkedHashMap<String,String>(); // things we've already displayed

                String filt= RecentUrisGUI.this.filter;
                if ( filt!=null && filt.length()==0 ) filt=null;

                long tzOffsetMs= Calendar.getInstance().getTimeZone().getRawOffset();

                String midnight= tp.format( Units.t1970.createDatum(0).subtract( tzOffsetMs,Units.milliseconds ),
                        null );
                final File f3 = new File(f2, "history.txt");
                Scanner scan = new Scanner(f3);
                while (scan.hasNextLine()) {
                    String line = scan.nextLine();
                    String[] ss= line.split("\\s+",2);
                    try {
                        if ( filt!=null && !ss[1].contains(filt) ) {
                            continue;
                        }
                        if ( ss[0].compareTo( midnight )>0 || !scan.hasNextLine() ) {
                            Datum tlocal= null;
                            for ( Iterator<String> ii= daysURIs.keySet().iterator(); ii.hasNext(); ) {
                                String uri= ii.next();
                                tlocal= tp.parse(daysURIs.get(uri)).getTimeDatum().add(tzOffsetMs,Units.milliseconds);
                                uris.put( tlocal, new String[] { tp.format(tlocal,null), uri } );
                            }
                            daysURIs= new LinkedHashMap<String,String>();
                            if ( tlocal==null ) tlocal= tp.parse(ss[0]).getTimeDatum().add(tzOffsetMs,Units.milliseconds);
                            midnight= tp.format( TimeUtil.nextMidnight( tlocal ).subtract(tzOffsetMs,Units.milliseconds), null );
                        }
                        daysURIs.remove( ss[1] );
                        daysURIs.put( ss[1], ss[0] );

                    } catch (ParseException ex) {
                        Logger.getLogger(RecentUrisGUI.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                skip= new boolean[8];

                // remove empty elements.
                int newListLen= list.length;
                for ( int i=0; i<list.length; i++ ) {
                    if ( getChildCount(list[i])==0 ) {
                        skip[i]=true;
                        newListLen--;
                    } else {
                        skip[i]= false;
                    }
                }

                if ( newListLen==0 ) {
                    newListLen= 1;
                    skip[0] = false;
                }
                DatumRange[] newlist = new DatumRange[newListLen];
                int j=0;
                for ( int i=0; i<list.length; i++ ) {
                    if ( !skip[i] ) {
                        newlist[j]= list[i];
                        j++;
                    }
                }
                list= newlist;
                

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
            return ( node instanceof String[] ) || getChildCount(node)==0;
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

    protected JTree getTree() {
        return this.jTree1;
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
