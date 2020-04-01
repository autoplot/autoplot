/*
 * AsciiDataSourceEditorPanel.java
 *
 * Created on September 5, 2008, 3:47 PM
 */
package org.autoplot.csv;

import com.csvreader.CsvReader;
import java.awt.Color;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

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
import org.autoplot.ascii.AsciiTableTableModel;
import org.autoplot.ascii.ColSpanTableCellRenderer;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceEditorPanel;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.ui.TableRowHeader;
import org.das2.qds.util.AsciiParser;
import org.das2.qds.util.AsciiParser.RecordParser;
import org.das2.qds.util.DataSetBuilder;

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

    private static final Logger logger= LoggerManager.getLogger("apdss.csv");

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

    public void markProblems(List<String> problems) {

    }

    private enum Tool {
        NONE, FIRSTROW, COLUMN, DEPEND_0, TIMEFORMAT,
    }
    Tool currentTool = Tool.NONE;
    JToggleButton currentToolButton;

    /**
     * initializer 
     */
    public CsvDataSourceEditorPanel() {
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
        jLabel1 = new javax.swing.JLabel();
        skipTextField = new javax.swing.JFormattedTextField();
        jLabel2 = new javax.swing.JLabel();
        delimComboBox = new javax.swing.JComboBox<>();

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

        jLabel1.setText("Skip:");
        jLabel1.setToolTipText("Number of lines to skip before parsing");

        skipTextField.setText("0");
        skipTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                skipTextFieldActionPerformed(evt);
            }
        });
        skipTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                skipTextFieldFocusLost(evt);
            }
        });

        jLabel2.setText("Delim:");

        delimComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { ", (comma)", "; (semicolon)" }));

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
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(jToggleButton2)
                        .add(18, 18, 18)
                        .add(jLabel1))
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(jToggleButton3)
                        .add(18, 18, 18)
                        .add(jLabel2)))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(delimComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(skipTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 55, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(338, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel3)
                    .add(columnsComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jToggleButton2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel1)
                    .add(skipTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel4)
                    .add(dep0Columns, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jToggleButton3, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 24, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel2)
                    .add(delimComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 862, Short.MAX_VALUE)
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


    private void loadTable( Reader f, File file ) {
        try {
            AsciiParser parser = new AsciiParser();
            AsciiTableTableModel model = new AsciiTableTableModel();
            model.setParser(parser);
            this.jTable1.setModel(model);
            model.setFile(file);
            jTable1.setDefaultRenderer(Object.class, new ColSpanTableCellRenderer());
            AsciiParser.DelimParser p= parser.guessSkipAndDelimParser( file.getAbsolutePath() );
            if ( p==null ) {
                model.setRecParser( new RecordParser() {
                    @Override
                    public boolean tryParseRecord(String line, int irec, DataSetBuilder builder) {
                        return false;
                    }

                    @Override
                    public int fieldCount() {
                        return 1;
                    }

                    @Override
                    public int fieldCount(String line) {
                        return 1;
                    }

                    @Override
                    public boolean splitRecord(String line, String[] fields) {
                        fields[0]= line;
                        return true;
                    }

                    @Override
                    public String readNextRecord(BufferedReader reader) throws IOException {
                        return reader.readLine();
                    }
                });
            } else {
                model.setRecParser(p);
            }
                    
        } catch (IOException ex) {
            Logger.getLogger(CsvDataSourceEditorPanel.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private static void updateColumns( javax.swing.JTable jTable1, Map<Integer,String> columns ) {
        int n= jTable1.getColumnCount();
        int wide= n<5 ? 210 : 170;
        int normwide= n<5 ? 140 : 110;
        int norm= n<5 ? 100 : 70;
        int narrow= n<5 ? 60 : 50;

        for (int i = 0; i < jTable1.getColumnCount(); i++) {
            String label;
            if (i < columns.size()) {
                label = columns.get(i);
            } else {
                label = "x"; // hopefully transient
            }
            jTable1.getColumnModel().getColumn(i).setHeaderValue(label);

            int nrow= jTable1.getRowCount();
            Object o= jTable1.getValueAt(nrow-1,i);
            String s= String.valueOf(o);
            if ( s.length()>16 ) { // times
                jTable1.getColumnModel().getColumn(i).setPreferredWidth(wide);
            } else if ( s.length()>11 ) {
                jTable1.getColumnModel().getColumn(i).setPreferredWidth(normwide); 
            } else if ( s.length()<5 ) {
                jTable1.getColumnModel().getColumn(i).setPreferredWidth(narrow); 
            } else {
                jTable1.getColumnModel().getColumn(i).setPreferredWidth(norm);
            }
        }
        jTable1.getTableHeader().repaint();

    }

    private void resetTable( ) {
        if (file == null) {
            return;
        }

        try {
            File ff= DataSetURI.getFile( DataSetURI.toUri(split.file), new NullProgressMonitor() );

            BufferedReader breader= new BufferedReader(new InputStreamReader( new FileInputStream(ff) ) );
            String skip= params.get("skip");
            if ( skip!=null && skip.length()>0 ) { try {
                int iskip= Integer.parseInt(skip);  // TODO: getIntegerParam( "skip", -1, "min=0,max=100" );
                for ( int i=0; i<iskip; i++ ) {
                    breader.readLine();
                }
            } catch ( IOException | NumberFormatException ex ) {
                logger.log( Level.WARNING,  ex.getMessage(), ex );
            } }

            loadTable(breader,ff); // use the AsciiTableReader's parser
            breader.close();

            breader= new BufferedReader(new InputStreamReader( new FileInputStream(ff) ) );
            skip= params.get("skip");
            if ( skip!=null && skip.length()>0 ) { try {
                int iskip= Integer.parseInt(skip);  // TODO: getIntegerParam( "skip", -1, "min=0,max=100" );
                for ( int i=0; i<iskip; i++ ) {
                    breader.readLine();
                }
            } catch ( IOException | NumberFormatException ex ) {
                logger.log( Level.WARNING, ex.getMessage(), ex );
            } }
            CsvReader reader= new CsvReader( breader );

            String sdelimiter= params.get("delim");
            if ( sdelimiter==null ) sdelimiter= ",";
            if ( sdelimiter.equals("COMMA") ) sdelimiter= ",";
            if ( sdelimiter.equals("SEMICOLON") ) sdelimiter= ";";
        
            char delimiter= sdelimiter.charAt(0);
            if ( delimiter!=',' ) reader.setDelimiter(delimiter);
            
            String[] columnHeaders = CsvDataSourceFactory.getColumnHeaders(reader);
            
            int ncol= columnHeaders.length;
            if ( ncol>jTable1.getModel().getColumnCount() ) {
                ncol= jTable1.getModel().getColumnCount();
            }

            headers= new ArrayList<>();
            
            headers.addAll( Arrays.asList(columnHeaders) );

            columns= new HashMap<>();
            for ( int i=0; i<ncol; i++ ) {
                columns.put( i, headers.get(i) );
                jTable1.getColumnModel().getColumn(i).setHeaderValue( headers.get(i) );
            }
            updateColumns(jTable1, columns);

            reader.close();

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

            logger.log( Level.WARNING, ex.getMessage(), ex );
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

private void skipTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_skipTextFieldActionPerformed
    params.put( "skip", skipTextField.getText() );
    resetTable();
}//GEN-LAST:event_skipTextFieldActionPerformed

private void skipTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_skipTextFieldFocusLost
    if ( !skipTextField.getText().equals(params.get("skip") ) ) {
        params.put( "skip", skipTextField.getText() );
        resetTable();
    }
}//GEN-LAST:event_skipTextFieldFocusLost

/**
 * the current file
 */
protected File file = null;

/**
 * the current file
 */
    public static final String PROP_FILE = "file";

    /**
     * return the current file
     * @return  the current file
     */
    public File getFile() {
        return file;
    }

    /**
     * set the current file
     * @param file the current file
     * @throws IOException 
     */
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
    public static final String PROP_DELIM = "delim";

    @Override
    public JPanel getPanel() {
        return this;
    }

//    private int getIntValue(String name, int def) {
//        if (params.get(name) == null) {
//            return def;
//        } else {
//            return Integer.parseInt(params.get(name));
//        }
//    }

    @Override
    public void setURI(String url) {
        try {
            split = URISplit.parse(url);
            params = URISplit.parseParams(split.params);

            if ("".equals( params.get(PROP_DELIM) ) ) params.remove(PROP_DELIM);
            if ( params.get(PROP_DELIM)!=null ) {
                String delim= params.get(PROP_DELIM);
                if ( delim.equals("COMMA") ) delim=",";
                if ( delim.equals("SEMICOLON") ) delim=";";
                delimComboBox.setSelectedIndex( delim.equals(";") ? 1 : 0 );
            }
            
            File f = DataSetURI.getFile(new URL(split.file), new NullProgressMonitor());
            setFile(f);

            if ( "".equals( params.get(PROP_COLUMN) ) ) params.remove(PROP_COLUMN);
            if ( "".equals( params.get(PROP_BUNDLE) ) ) params.remove(PROP_BUNDLE);
            if ( "".equals( params.get(PROP_DEP0 ) ) ) params.remove(PROP_DEP0);

            if ( params.get(PROP_COLUMN)!=null ) columnsComboBox.setSelectedItem( params.get(PROP_COLUMN) );
            if ( params.get(PROP_BUNDLE)!=null ) columnsComboBox.setSelectedItem( params.get(PROP_BUNDLE) );
            if ( params.get(PROP_DEP0)!=null ) dep0Columns.setSelectedItem( params.get(PROP_DEP0) );
            
            if ( params.get("skip")!=null ) {
                try {
                    skipTextField.setValue( Integer.parseInt(params.get("skip")) );
                } catch ( NumberFormatException ex ) {
                    skipTextField.setValue( params.get("skip") );
                    skipTextField.setBackground( Color.YELLOW );
                }
            }
            
            resetTable();
            
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }

    }

    @Override
    public String getURI() {

        String delim= String.valueOf( delimComboBox.getSelectedItem() );
        if ( delim.charAt(0)==';' ) {
            params.put("delim",delim.substring(0,1) );
        }
        split.params = params.isEmpty() ? null : URISplit.formatParams(params);
        
        return URISplit.format(split);
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    public javax.swing.JComboBox columnsComboBox;
    public javax.swing.JComboBox<String> delimComboBox;
    public javax.swing.JComboBox dep0Columns;
    public javax.swing.JLabel jLabel1;
    public javax.swing.JLabel jLabel2;
    public javax.swing.JLabel jLabel3;
    public javax.swing.JLabel jLabel4;
    public javax.swing.JPanel jPanel1;
    public javax.swing.JScrollPane jScrollPane1;
    public javax.swing.JTable jTable1;
    public javax.swing.JToggleButton jToggleButton2;
    public javax.swing.JToggleButton jToggleButton3;
    public javax.swing.JFormattedTextField skipTextField;
    // End of variables declaration//GEN-END:variables
}
