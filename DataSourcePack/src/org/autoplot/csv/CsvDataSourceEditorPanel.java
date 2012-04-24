/*
 * AsciiDataSourceEditorPanel.java
 *
 * Created on September 5, 2008, 3:47 PM
 */
package org.autoplot.csv;

import com.csvreader.CsvReader;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSourceEditorPanel;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.ui.TableRowHeader;

/**
 *
 * @author  jbf
 */          
public class CsvDataSourceEditorPanel extends javax.swing.JPanel implements DataSourceEditorPanel {

    Map<Integer, String> columns;
    List<String> headers= new ArrayList();
    boolean focusDepend0 = false;
    Map<String, String> params;
    URISplit split;

    public boolean reject( String url ) throws IOException, URISyntaxException {
        split = URISplit.parse(url);
        FileSystem fs = FileSystem.create( DataSetURI.getWebURL( DataSetURI.toUri(split.path) ).toURI() );
        if ( fs.isDirectory( split.file.substring(split.path.length()) ) ) {
            return true;
        }
        return false;
    }
    
    public boolean prepare(String uri, Window parent, ProgressMonitor mon) throws Exception {
        split = URISplit.parse(uri);
        params = URISplit.parseParams(split.params);

        DataSetURI.getFile(new URL(split.file), mon );
        return true;
    }

    private enum Tool {
        NONE, FIRSTROW, COLUMN, DEPEND_0, TIMEFORMAT,
    }
    Tool currentTool = Tool.NONE;
    JToggleButton currentToolButton;

    public CsvDataSourceEditorPanel() {
        initComponents();
        jTable1.setCellSelectionEnabled(true);

        jTable1.getColumnModel().getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) {
                    return;
                }

                doSelect(currentTool);
            }
        });

        jTable1.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) {
                    return;
                }
                doSelect( currentTool );
            }
        });

        jScrollPane1.setRowHeaderView(new TableRowHeader(jTable1));

        jTable1.getTableHeader().setReorderingAllowed(false);

        jTable1.getTableHeader().addMouseListener( new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                int col= jTable1.getTableHeader().columnAtPoint(e.getPoint());
                String name= columns.get(col);
                if (name == null) {
                    name = "field" + String.valueOf(col);
                }
                if ( currentTool==Tool.DEPEND_0 ) {
                    params.put(PROP_DEP0, name);
                    dep0Columns.setSelectedItem(name);
                    clearTool(); // otherwise we would respond to deselection event
                } else if ( currentTool==Tool.COLUMN ) {
                    params.put(PROP_COLUMN, name);
                    columnsComboBox.setSelectedItem(name);
                    clearTool(); // otherwise we would respond to deselection event
                }
            }
        } );

    }

    private void doSelect( Tool tool ) {
        if (tool == Tool.FIRSTROW) {
            if (jTable1.getSelectedRow() > 0) {
                params.put(PROP_FIRST_ROW, String.valueOf(jTable1.getSelectedRow() + 1));
            } else {
                params.remove(PROP_FIRST_ROW);
            }

        } else if (jTable1.getColumnModel().getSelectedColumnCount() == 0) {
        } else if (jTable1.getColumnModel().getSelectedColumnCount() == 1) {
            int col = jTable1.getColumnModel().getSelectedColumns()[0];
            String name = columns.get(col);
            if (name == null) {
                name = "field" + String.valueOf(col);
            }
            if (currentTool == Tool.DEPEND_0) {
                params.put(PROP_DEP0, name);
                dep0Columns.setSelectedItem(name);
            } else if (currentTool == Tool.COLUMN) {
                params.put(PROP_COLUMN, name);
                columnsComboBox.setSelectedItem(name);
                params.remove(PROP_BUNDLE);
            }

        } else {
            int[] cols = jTable1.getColumnModel().getSelectedColumns();
            int first = cols[0];
            int last = cols[cols.length - 1];
            String sfirst = columns.get(first);
            if (sfirst == null) {
                sfirst = "" + first;
            }
            boolean haveColumnNames = true;
            String slast = columns.get(last);
            if (slast == null) {
                slast = "" + last;
                haveColumnNames = false;
            }

            if (currentTool == Tool.DEPEND_0) {
            } else if (currentTool == Tool.COLUMN) {
                if (haveColumnNames) {
                    params.put(PROP_BUNDLE, sfirst + "-" + slast);
                } else {
                    params.put(PROP_BUNDLE, "" + first + ":" + (last + 1));
                }
                params.remove(PROP_COLUMN);
                columnsComboBox.setSelectedItem(params.get(PROP_BUNDLE) );
            }
        }
        clearTool();
    }

    Action createToolAction(final String label, final Tool t) {
        return new AbstractAction(label) {

            public void actionPerformed(ActionEvent e) {
                if ( e.getSource() instanceof JToggleButton ) {
                    if ( jTable1.getSelectionModel().isSelectionEmpty() ) {
                        jTable1.getSelectionModel().clearSelection();
                        jTable1.getColumnModel().getSelectionModel().clearSelection();
                        currentToolButton= ( JToggleButton ) e.getSource();
                        currentTool = t;
                    } else {
                        currentToolButton= ( JToggleButton ) e.getSource();
                        currentTool = t;
                        doSelect( t );
                        jTable1.getSelectionModel().clearSelection();
                        jTable1.getColumnModel().getSelectionModel().clearSelection();
                    }
                }


            }
        };
    }

    private void clearTool() {
        if (currentTool != Tool.NONE) {
            currentTool = Tool.NONE;
            currentToolButton.setSelected(false);
            currentToolButton = null;
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
        jTable1 = new javax.swing.JTable();
        jPanel1 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        dep0Columns = new javax.swing.JComboBox();
        columnsComboBox = new javax.swing.JComboBox();
        jToggleButton2 = new javax.swing.JToggleButton();
        jToggleButton3 = new javax.swing.JToggleButton();

        jTable1.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        jScrollPane1.setViewportView(jTable1);

        jLabel3.setText("Column:");
        jLabel3.setToolTipText("Select the column to plot");

        jLabel4.setText("Depends On:");

        dep0Columns.setEditable(true);
        dep0Columns.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        dep0Columns.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                dep0ColumnsItemStateChanged(evt);
            }
        });
        dep0Columns.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                dep0ColumnsFocusGained(evt);
            }
        });

        columnsComboBox.setEditable(true);
        columnsComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        columnsComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                columnsComboBoxItemStateChanged(evt);
            }
        });
        columnsComboBox.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                columnsComboBoxFocusGained(evt);
            }
        });

        jToggleButton2.setAction(createToolAction( "column", Tool.COLUMN ));
        jToggleButton2.setText("Select");
        jToggleButton2.setToolTipText("Select the column to plot by pushing this button and then clicking on a column of the table.\n");

        jToggleButton3.setAction(createToolAction( "depend0", Tool.DEPEND_0 ));
        jToggleButton3.setText("Select");
        jToggleButton3.setToolTipText("Select the column containing the indepenent variable to plot against by pressing this button and then clicking on the table.\n");

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(jLabel4)
                    .add(jLabel3))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(dep0Columns, 0, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(columnsComboBox, 0, 190, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jToggleButton2)
                    .add(jToggleButton3))
                .addContainerGap(507, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel3)
                    .add(columnsComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jToggleButton2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel4)
                    .add(dep0Columns, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jToggleButton3, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 24, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(12, Short.MAX_VALUE))
        );

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 846, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 381, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents


    private void resetTable( ) {
        if (file == null) {
            return;
        }

        String uri= getURI();

        URISplit split= URISplit.parse(uri);
        Map<String,String> params= URISplit.parseParams(split.params);

        try {
            File ff= DataSetURI.getFile( DataSetURI.toUri(split.file), new NullProgressMonitor() );

            CsvReader reader= new CsvReader( ff.toString() );

            reader.readHeaders();
            int ncol= reader.getHeaderCount();

            headers= new ArrayList<String>();
            
            headers.addAll( Arrays.asList(reader.getHeaders()) );

            for ( int i=0; i<headers.size(); i++ ) {
                try {
                    Integer.parseInt(headers.get(i));
                    headers.set( i, "field"+i );
                } catch (NumberFormatException ex ) {
                    if ( headers.get(i).trim().length()==0 ) {
                        headers.set( i, "field"+i );
                    }
                }
            }

            
            DefaultTableModel tmodel= new DefaultTableModel( headers.toArray( new String[headers.size()] ), 20 );

            int line=0;
            while ( reader.readRecord() && line<20 ) {
                for ( int j=0; j<ncol; j++ ) {
                    tmodel.setValueAt( reader.get(j), line, j );
                }
                line++;
            }

            columns= new HashMap<Integer, String>();
            for ( int i=0; i<ncol; i++ ) {
                columns.put( i, headers.get(i) );
            }

            reader.close();

            this.jTable1.setModel( tmodel );

            String[] hh= new String[headers.size()+1];
            hh[0]="";
            for ( int i=0; i<headers.size(); i++ ) {
                hh[i+1]= headers.get(i);
            }
            columnsComboBox.setModel( new DefaultComboBoxModel( hh ) );
            String column= params.get(PROP_COLUMN);
            if ( column!=null ) this.columnsComboBox.setSelectedItem(params.get(PROP_COLUMN));
            String bundle= params.get(PROP_BUNDLE);
            if ( bundle!=null ) this.columnsComboBox.setSelectedItem(params.get(PROP_BUNDLE));

            dep0Columns.setModel( new DefaultComboBoxModel( hh ) );
            String depend0column= params.get(PROP_DEP0);
            if ( depend0column!=null ) this.dep0Columns.setSelectedItem(params.get(PROP_DEP0));

        } catch (IOException ex) {
            columnsComboBox.setModel( new DefaultComboBoxModel( new String[] { "(no records found)" } ) );
            DefaultTableModel dtm= new DefaultTableModel(1,1);
            dtm.setValueAt( "no records found", 0, 0 );
            this.jTable1.setModel( dtm );

            ex.printStackTrace();
        }
        

    }

private void dep0ColumnsItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_dep0ColumnsItemStateChanged
    String v= (String) dep0Columns.getSelectedItem();
    if ( v.equals("") ) {
        params.remove(PROP_DEP0 );
    } else {
        params.put(PROP_DEP0, v );
    }
}//GEN-LAST:event_dep0ColumnsItemStateChanged

private void dep0ColumnsFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_dep0ColumnsFocusGained
}//GEN-LAST:event_dep0ColumnsFocusGained

private void columnsComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_columnsComboBoxItemStateChanged
    String v= (String) columnsComboBox.getSelectedItem();
    if ( v.equals("") ) {
        params.remove(PROP_COLUMN );
        params.remove(PROP_BUNDLE );
    } else {
        if ( v.contains("-") || v.contains(":") ) {
            params.put(PROP_BUNDLE, v );
            params.remove(PROP_COLUMN);
        } else {
            params.put(PROP_COLUMN, v );
            params.remove(PROP_BUNDLE );
        }
    }
}//GEN-LAST:event_columnsComboBoxItemStateChanged

private void columnsComboBoxFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_columnsComboBoxFocusGained
}//GEN-LAST:event_columnsComboBoxFocusGained
    protected File file = null;
    public static final String PROP_FILE = "file";

    public File getFile() {
        return file;
    }

    public void setFile(File file) throws IOException {
        this.file = file;

        resetTable( );

    }
    protected String table;
    public static final String PROP_TABLE = "table";

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        String oldTable = this.table;
        this.table = table;
        resetTable();
        firePropertyChange(PROP_TABLE, oldTable, table);
    }
    
    public static final String PROP_FIRST_ROW = "firstRow";
    public static final String PROP_COLUMN = "column";
    public static final String PROP_BUNDLE = "bundle";
    public static final String PROP_DEP0 = "depend0";

    public JPanel getPanel() {
        return this;
    }

    private int getIntValue(String name, int def) {
        if (params.get(name) == null) {
            return def;
        } else {
            return Integer.parseInt(params.get(name));
        }
    }

    public void setURI(String url) {
        try {
            split = URISplit.parse(url);
            params = URISplit.parseParams(split.params);

            File f = DataSetURI.getFile(new URL(split.file), new NullProgressMonitor());
            setFile(f);

            if ( "".equals( params.get(PROP_COLUMN) ) ) params.remove(PROP_COLUMN);
            if ( "".equals( params.get(PROP_BUNDLE) ) ) params.remove(PROP_BUNDLE);
            if ( "".equals( params.get(PROP_DEP0 ) ) ) params.remove(PROP_DEP0);

            if ( params.get(PROP_COLUMN)!=null ) columnsComboBox.setSelectedItem( params.get(PROP_COLUMN) );
            if ( params.get(PROP_BUNDLE)!=null ) columnsComboBox.setSelectedItem( params.get(PROP_BUNDLE) );
            if ( params.get(PROP_DEP0)!=null ) dep0Columns.setSelectedItem( params.get(PROP_DEP0) );

            resetTable();
            
        } catch (IOException ex) {
            Logger.getLogger(CsvDataSourceEditorPanel.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public String getURI() {

        split.params = URISplit.formatParams(params);

        return URISplit.format(split);
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    public javax.swing.JComboBox columnsComboBox;
    public javax.swing.JComboBox dep0Columns;
    public javax.swing.JLabel jLabel3;
    public javax.swing.JLabel jLabel4;
    public javax.swing.JPanel jPanel1;
    public javax.swing.JScrollPane jScrollPane1;
    public javax.swing.JTable jTable1;
    public javax.swing.JToggleButton jToggleButton2;
    public javax.swing.JToggleButton jToggleButton3;
    // End of variables declaration//GEN-END:variables
}
