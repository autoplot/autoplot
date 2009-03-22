/*
 * AsciiDataSourceEditorPanel.java
 *
 * Created on September 5, 2008, 3:47 PM
 */
package org.virbo.ascii;

import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JPanel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import org.das2.util.monitor.NullProgressMonitor;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSourceEditorPanel;
import org.virbo.datasource.URLSplit;
import org.virbo.dsutil.AsciiParser;
import org.virbo.dsutil.AsciiParser.DelimParser;

/**
 *
 * @author  jbf
 */
public class AsciiTableDataSourceEditorPanel extends javax.swing.JPanel implements DataSourceEditorPanel {

    AsciiTableTableModel model;
    Map<Integer, String> columns;
    AsciiParser parser;
    boolean focusDep0 = false;
    TableCellRenderer defaultCellRenderer;

    /** Creates new form AsciiDataSourceEditorPanel */
    public AsciiTableDataSourceEditorPanel() {
        initComponents();

        parser = new AsciiParser();
        model = new AsciiTableTableModel();

        jTable1.setModel(model);
        defaultCellRenderer = jTable1.getDefaultRenderer(Object.class);
        jTable1.setDefaultRenderer(Object.class, new ColSpanTableCellRenderer());

        model.addPropertyChangeListener(new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent e) {
                if ( columns!=null ) {
                    for (int i = 0; i < model.getColumnCount(); i++) {
                        String label= columns.get(i);
                        jTable1.getColumnModel().getColumn(i).setHeaderValue(label);
                    }
                    jTable1.getTableHeader().repaint();
                }
                jTable1.repaint();

            }
        });

        jTable1.setCellSelectionEnabled(true);

        jTable1.getColumnModel().getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            public void valueChanged(ListSelectionEvent e) {
                if (jTable1.getColumnModel().getSelectedColumnCount() == 1) {
                    int col = jTable1.getColumnModel().getSelectedColumns()[0];
                    String name = columns.get(col);
                    if (name == null) {
                        name = "field" + col;
                    }
                    if (focusDep0) {
                        setDep0(name);
                    } else {
                        setColumn(name);
                    }
                } else {
                    int[] cols = jTable1.getColumnModel().getSelectedColumns();
                    int first= cols[0];
                    int last= cols[cols.length-1];
                    String sfirst = columns.get(first);
                    if (sfirst == null) {
                        sfirst = "" + first;
                    }
                    boolean haveColumnNames= true;
                    String slast = columns.get(last);
                    if (slast == null) {
                        slast = "" + last;
                        haveColumnNames= false;
                    }

                    if (focusDep0) {
                    } else {
                        if ( haveColumnNames ) {
                            setColumn( sfirst + "-" + slast );
                        } else {
                            setColumn( "" + first + ":" + (last+1) );
                        }
                    }
                }
            }
        });


        jTable1.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            public void valueChanged(ListSelectionEvent e) {
                setSkipLines(jTable1.getSelectedRow());
            }
        });

        BindingGroup bc = new BindingGroup();

        bc.addBinding(Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, this, BeanProperty.create("skipLines"), this.skipLinesTextField, BeanProperty.create("value")));
        bc.addBinding(Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, this, BeanProperty.create("column"), this.columnsComboBox, BeanProperty.create("selectedItem")));
        bc.addBinding(Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, this, BeanProperty.create("dep0"), this.dep0Columns, BeanProperty.create("selectedItem")));

        bc.bind();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();
        skipLinesTextField = new javax.swing.JFormattedTextField();
        jLabel3 = new javax.swing.JLabel();
        columnsComboBox = new javax.swing.JComboBox();
        jLabel4 = new javax.swing.JLabel();
        dep0Columns = new javax.swing.JComboBox();

        jLabel1.setText("Skip Lines:");

        jScrollPane1.setViewportView(jTable1);

        skipLinesTextField.setText("11");

        jLabel3.setText("Column:");

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

        jLabel4.setText("Depends On:");

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

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(jLabel4)
                    .add(layout.createSequentialGroup()
                        .add(jLabel1)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(skipLinesTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 35, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(18, 18, 18)
                        .add(jLabel3)))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(dep0Columns, 0, 175, Short.MAX_VALUE)
                    .add(columnsComboBox, 0, 175, Short.MAX_VALUE))
                .add(434, 434, 434))
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 819, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel1)
                    .add(skipLinesTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel3)
                    .add(columnsComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel4)
                    .add(dep0Columns, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 382, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

private void columnsComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_columnsComboBoxItemStateChanged
    setColumn((String) columnsComboBox.getSelectedItem());
}//GEN-LAST:event_columnsComboBoxItemStateChanged

private void dep0ColumnsItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_dep0ColumnsItemStateChanged
    setDep0((String) dep0Columns.getSelectedItem());
}//GEN-LAST:event_dep0ColumnsItemStateChanged

private void columnsComboBoxFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_columnsComboBoxFocusGained
    focusDep0 = false;
}//GEN-LAST:event_columnsComboBoxFocusGained

private void dep0ColumnsFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_dep0ColumnsFocusGained
    focusDep0 = true;
}//GEN-LAST:event_dep0ColumnsFocusGained
    URLSplit split = null;
    protected File file = null;
    public static final String PROP_FILE = "file";

    public File getFile() {
        return file;
    }

    public void setFile(File file) throws IOException {
        this.file = file;
        this.model.setFile(file);

    }
    protected int skipLines = 0;
    public static final String PROP_FIRST_ROW = "skipLines";

    public Map<Integer, String> getColumnNames() throws IOException {

        if (skipLines > 0) {
            parser.setSkipLines(skipLines);
        }

        String line = parser.readFirstRecord(file.toString());
        DelimParser dp = parser.guessDelimParser(line);
        String[] columns = parser.getFieldNames();
        Map<Integer, String> result = new LinkedHashMap<Integer, String>();
        for (int i = 0; i < columns.length; i++) {
            result.put(i, columns[i]);
        }
        return result;
    }

    public int getSkipLines() {
        return skipLines;
    }

    public void setSkipLines(int row) {
            int oldRow = this.skipLines;
            this.skipLines = row;
            Rectangle rect = jTable1.getCellRect(getSkipLines(), 0, true);
            update();
            jTable1.scrollRectToVisible(rect);
            firePropertyChange(PROP_FIRST_ROW, oldRow, row);
    }
    protected String column = "field0";
    public static final String PROP_COLUMN = "column";

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        String oldColumn = this.column;
        this.column = column;
        firePropertyChange(PROP_COLUMN, oldColumn, column);
    }

    protected String dep0 = "";
    public static final String PROP_DEP0 = "dep0";

    public String getDep0() {
        return dep0;
    }

    public void setDep0(String dep0) {
        String oldDep0 = this.dep0;
        this.dep0 = dep0;
        firePropertyChange(PROP_DEP0, oldDep0, dep0);
    }

    public JPanel getPanel() {
        return this;
    }

    public void setUrl(String url) {
        try {
            split = URLSplit.parse(url);
            Map<String, String> params = URLSplit.parseParams(split.params);

            File f = DataSetURL.getFile(new URL(split.file), new NullProgressMonitor());
            setFile(f);

            if (params.containsKey("skipLines")) {
                setSkipLines(Integer.parseInt(params.get("skipLines")));
            }
            if (params.containsKey("skip")) {
                setSkipLines(Integer.parseInt(params.get("skip")));
            }

            if (params.containsKey("column")) {
                setColumn(params.get("column"));
            }
            if (params.containsKey("rank2") ) {
                setColumn(params.get("rank2"));
            }

            if (params.containsKey("depend0")) {
                setDep0(params.get("depend0"));
            }

            update();

        } catch (IOException ex) {
            Logger.getLogger(AsciiTableDataSourceEditorPanel.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public String getUrl() {
        Map<String, String> params = URLSplit.parseParams(split.params);
        String args = "";
        if (skipLines > 1) {
            params.put("skipLines", "" + skipLines);
        }
        if (params.get("skip") !=null ) {
            params.remove("skip");
        }
        if (!this.getDep0().equals("")) {
            params.put("depend0", this.getDep0());
        }

        if ( getColumn().contains(":") || getColumn().contains("-") ) {
            params.remove("column");
            params.put( "rank2", getColumn() );
        } else {
            params.put("column", getColumn());
        }

        split.params = URLSplit.formatParams(params);

        return URLSplit.format(split);

    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    public javax.swing.JComboBox columnsComboBox;
    public javax.swing.JComboBox dep0Columns;
    public javax.swing.JLabel jLabel1;
    public javax.swing.JLabel jLabel3;
    public javax.swing.JLabel jLabel4;
    public javax.swing.JScrollPane jScrollPane1;
    public javax.swing.JTable jTable1;
    public javax.swing.JFormattedTextField skipLinesTextField;
    // End of variables declaration//GEN-END:variables

    private void update() {
        try {
            String line = model.getLine(this.skipLines);
            if (line == null || parser == null) {
                //               throw new IllegalArgumentException("no records found");
                return;
            }
            AsciiParser.DelimParser p = parser.guessDelimParser(line);
            model.setRecParser(p);
            columns = getColumnNames();

            String lcol= getColumn();
            int icol = jTable1.getSelectedColumn();
            columnsComboBox.setModel(new DefaultComboBoxModel(columns.values().toArray()));
            columnsComboBox.setSelectedItem(getColumn());
            if (icol != -1) {
                setColumn(columns.get(icol));
            } else {
                setColumn(lcol);
            }
            
            List<String> dep0Values = new ArrayList<String>(columns.values());
            String ldep0= getDep0();
            dep0Values.add(0, "");
            dep0Columns.setModel(new DefaultComboBoxModel(dep0Values.toArray()));
            dep0Columns.setSelectedItem(ldep0);
        } catch (IOException ex) {
            Logger.getLogger(AsciiTableDataSourceEditorPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
