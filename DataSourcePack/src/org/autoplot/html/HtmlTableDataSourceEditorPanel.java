/*
 * AsciiDataSourceEditorPanel.java
 *
 * Created on September 5, 2008, 3:47 PM
 */
package org.autoplot.html;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
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
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceEditorPanel;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.ui.TableRowHeader;
import org.das2.qds.util.QDataSetTableModel;

/**
 *
 * @author  jbf
 */
public class HtmlTableDataSourceEditorPanel extends javax.swing.JPanel implements DataSourceEditorPanel {

    private static final Logger logger= LoggerManager.getLogger("apdss");
    
    Map<Integer, String> columns;
    boolean focusDepend0 = false;
    Map<String, String> params;
    URISplit split;

    @Override
    public boolean reject( String url ) throws IOException, URISyntaxException {
        split = URISplit.parse(url);
        FileSystem fs = FileSystem.create( DataSetURI.getWebURL( DataSetURI.toUri(split.path) ).toURI() );
        return fs.isDirectory( split.file.substring(split.path.length()) );
    }
    
    @Override
    public boolean prepare(String uri, Window parent, ProgressMonitor mon) throws Exception {
        split = URISplit.parse(uri);
        params = URISplit.parseParams(split.params);

        File f= DataSetURI.getFile( split.file, true, mon );
        DataSetURI.checkLength(f);
        return true;
    }

    @Override
    public void markProblems(List<String> problems) {
        
    }

    private enum Tool {
        NONE, FIRSTROW, COLUMN, DEPEND_0, TIMEFORMAT,
    }
    Tool currentTool = Tool.NONE;
    JToggleButton currentToolButton;

    public HtmlTableDataSourceEditorPanel() {
        initComponents();
        jTable1.setCellSelectionEnabled(true);

        jTable1.getColumnModel().getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) {
                    return;
                }

                doSelect(currentTool);
            }
        });

        jTable1.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            @Override
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
                    name = String.valueOf((char)('A' + col));
                }
                if ( currentTool==Tool.DEPEND_0 ) {
                    params.put(PROP_DEP0, name);
                    //dep0Columns.setSelectedItem(name);
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
            firstRowTextField.setValue(jTable1.getSelectedRow() + 1);

        } else if (jTable1.getColumnModel().getSelectedColumnCount() == 0) {
        } else if (jTable1.getColumnModel().getSelectedColumnCount() == 1) {
            int col = jTable1.getColumnModel().getSelectedColumns()[0];
            String name = columns.get(col);
            if (name == null) {
                name = String.valueOf((char)('A' + col));
            }
            if (currentTool == Tool.DEPEND_0) {
                params.put(PROP_DEP0, name);
                //dep0Columns.setSelectedItem(name);

            } else if (currentTool == Tool.COLUMN) {
                params.put(PROP_COLUMN, name);
                columnsComboBox.setSelectedItem(name);

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
                    params.put(PROP_COLUMN, sfirst + "-" + slast);
                } else {
                    params.put(PROP_COLUMN, "" + first + ":" + (last + 1));
                }
            }
        }
        clearTool();
    }

    Action createToolAction(final String label, final Tool t) {
        return new AbstractAction(label) {

            @Override
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
        columnsComboBox = new javax.swing.JComboBox();
        jToggleButton2 = new javax.swing.JToggleButton();
        jLabel2 = new javax.swing.JLabel();
        tableComboBox = new javax.swing.JComboBox();
        jLabel1 = new javax.swing.JLabel();
        firstRowTextField = new javax.swing.JFormattedTextField();
        jToggleButton1 = new javax.swing.JToggleButton();
        jLabel5 = new javax.swing.JLabel();

        jTable1.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        jScrollPane1.setViewportView(jTable1);

        jLabel3.setText("Column:");
        jLabel3.setToolTipText("Select the column to plot");

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

        jLabel2.setText("Table:");
        jLabel2.setToolTipText("Select the table within the html to use.  Note tables are often used for layout on web pages, so \nthere will probably be more tables than expected.\n");

        tableComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        tableComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                tableComboBoxItemStateChanged(evt);
            }
        });

        jLabel1.setText("First Row:");
        jLabel1.setEnabled(false);

        firstRowTextField.setText("jFormattedTextField1");
        firstRowTextField.setEnabled(false);

        jToggleButton1.setAction(createToolAction("firstRow", Tool.FIRSTROW));
        jToggleButton1.setText("Select");
        jToggleButton1.setToolTipText("Select the first row to start the data by clicking on this button then a row of the table.\n");
        jToggleButton1.setEnabled(false);

        jLabel5.setFont(jLabel5.getFont().deriveFont(jLabel5.getFont().getSize()-2f));
        jLabel5.setText("Note the HTML parser has never been able to parse an independent param.  See File->Add Plot.");

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(31, 31, 31)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(jLabel3)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(columnsComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 190, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jToggleButton2)
                        .add(36, 36, 36)
                        .add(jLabel2)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(tableComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 158, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(jLabel5, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 568, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(18, 18, 18)
                        .add(jLabel1)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(firstRowTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 96, Short.MAX_VALUE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jToggleButton1)))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel3)
                    .add(columnsComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jToggleButton2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel2)
                    .add(tableComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jToggleButton1)
                    .add(firstRowTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel1)
                    .add(jLabel5)))
        );

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 854, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 393, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void tableComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_tableComboBoxItemStateChanged
        final String ltable = getSelectedTable();
        params.put(PROP_TABLE, ltable);
        setTable(ltable);
    }//GEN-LAST:event_tableComboBoxItemStateChanged

    private void columnsComboBoxFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_columnsComboBoxFocusGained

    }//GEN-LAST:event_columnsComboBoxFocusGained

    private void columnsComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_columnsComboBoxItemStateChanged
        params.put(PROP_COLUMN, (String) columnsComboBox.getSelectedItem());
    }//GEN-LAST:event_columnsComboBoxItemStateChanged

    private String getSelectedTable() {
        String s= (String)tableComboBox.getSelectedItem();
        int i= s.indexOf(":");
        if ( i==-1 ) i= s.length();
        return s.substring(0,i);
    }
    
    private void setSelectedTable( String t ) {
        for ( int j=0; j<tableComboBox.getItemCount(); j++ ) {
            String s= (String)tableComboBox.getItemAt(j);
            int i= s.indexOf(":");
            if ( i==-1 ) i= s.length();
            if ( s.substring(0,i).equals(t) ) {
                tableComboBox.setSelectedIndex(j);
            }
        }
    }
    
    private void resetFile() {
        if (file == null) {
            return;
        }

        String uri= getURI();
        URISplit lsplit= URISplit.parse(uri);
        Map<String,String> lparams= URISplit.parseParams(lsplit.params);

        HtmlTableDataSource parser;
        try {
            parser=  new HtmlTableDataSource(DataSetURI.getURIValid(uri));

            tableComboBox.setModel( new DefaultComboBoxModel( parser.getTables().toArray( )) );
            String ltable= lparams.get("table");
            if ( ltable!=null ) {
                setSelectedTable( ltable );
            } else {
                tableComboBox.setSelectedIndex(0);
                params.put( "table", getSelectedTable() );
            }
        } catch ( URISyntaxException | IOException ex ) {
            logger.log( Level.WARNING, ex.toString(), ex );
        }
    }

    private void resetTable( ) {
        if (file == null) {
            return;
        }

        String uri= getURI();

        URISplit lsplit= URISplit.parse(uri);
        Map<String,String> lparams= URISplit.parseParams(lsplit.params);

        QDataSet tds;
        HtmlTableDataSource parser;
        try {
            parser=  new HtmlTableDataSource(DataSetURI.getURIValid(uri));

            tds = parser.getTable(new NullProgressMonitor());

            this.jTable1.setModel( new QDataSetTableModel(tds) );

            if ( tds.length()>0 ) {
                String[] columnNames= new String[tds.length(0)];
                columns= new LinkedHashMap();
                for ( int i=0; i<tds.length(0); i++ ) {
                    columnNames[i]= ""+i;
                    jTable1.getColumnModel().getColumn(i).setHeaderValue(columnNames[i]);
                    columns.put( i, ""+i );
                }
                columnsComboBox.setModel( new DefaultComboBoxModel( columnNames ) );

            } else {
                columnsComboBox.setModel( new DefaultComboBoxModel( new String[] { "(no records found)" } ) );
            }
            String column= lparams.get("column");
            if ( column!=null ) this.columnsComboBox.setSelectedItem(lparams.get("column"));

        } catch (URISyntaxException | IOException ex) {
            columnsComboBox.setModel( new DefaultComboBoxModel( new String[] { "(no records found)" } ) );
            DefaultTableModel dtm= new DefaultTableModel(1,1);
            dtm.setValueAt( "no records found", 0, 0 );
            this.jTable1.setModel( dtm );

            logger.log( Level.WARNING, ex.toString(), ex );
        }

    }
    
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

    /**
     * reset everything to this table.
     * @param table 
     */
    public void setTable(String table) {
        String oldTable = this.table;
        this.table = table;
        resetTable();
        firePropertyChange(PROP_TABLE, oldTable, table);
    }
    
    public static final String PROP_FIRST_ROW = "firstRow";
    public static final String PROP_COLUMN = "column";
    public static final String PROP_DEP0 = "depend0";

    @Override
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

    @Override
    public void setURI(String url) {
        try {
            split = URISplit.parse(url);
            params = URISplit.parseParams(split.params);

            File f = DataSetURI.getFile( split.file, true, new NullProgressMonitor());
            DataSetURI.checkLength(f);
            setFile(f);

            if ( params.get(PROP_TABLE)!=null ) {
                setSelectedTable( params.get(PROP_TABLE) );
                setTable( getSelectedTable() );
            } 

            if ( params.get(PROP_COLUMN)!=null ) columnsComboBox.setSelectedItem( params.get(PROP_COLUMN) );
            //if ( params.get(PROP_DEP0)!=null ) dep0Columns.setSelectedItem( params.get(PROP_DEP0) );

            firstRowTextField.setValue(getIntValue(PROP_FIRST_ROW, 1));

            resetFile();
            resetTable();
            
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }

    }

    @Override
    public String getURI() {

        split.params = URISplit.formatParams(params);

        return URISplit.format(split);
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    public javax.swing.JComboBox columnsComboBox;
    public javax.swing.JFormattedTextField firstRowTextField;
    public javax.swing.JLabel jLabel1;
    public javax.swing.JLabel jLabel2;
    public javax.swing.JLabel jLabel3;
    public javax.swing.JLabel jLabel5;
    public javax.swing.JPanel jPanel1;
    public javax.swing.JScrollPane jScrollPane1;
    public javax.swing.JTable jTable1;
    public javax.swing.JToggleButton jToggleButton1;
    public javax.swing.JToggleButton jToggleButton2;
    public javax.swing.JComboBox tableComboBox;
    // End of variables declaration//GEN-END:variables
}
