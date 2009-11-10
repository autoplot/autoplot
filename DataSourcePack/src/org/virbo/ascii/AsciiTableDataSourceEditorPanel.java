/*
 * AsciiDataSourceEditorPanel.java
 *
 * Created on September 5, 2008, 3:47 PM
 */
package org.virbo.ascii;

import org.virbo.datasource.ui.TableRowHeader;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSourceEditorPanel;
import org.virbo.datasource.URISplit;
import org.virbo.dsutil.AsciiParser;
import org.virbo.dsutil.AutoHistogram;
import org.virbo.dsutil.DataSetBuilder;

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

    private enum Tool {
        NONE, SKIPLINES, COLUMN, DEPEND_0, TIMEFORMAT, FILLVALUE,
    }

    private final static String PARAMS_KEY_COMMENT="comment";
    
    Tool currentTool = Tool.NONE;
    JToggleButton currentToolButton;

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

    /**
     * do the actions for the tool based on the current selection.
     * @param tool
     */
    private void doSelect( Tool tool  ) {
        switch (tool) {
            case SKIPLINES:
                if (jTable1.getSelectedRow() != -1) {
                    setSkipLines(jTable1.getSelectedRow());
                    clearTool();
                }
                break;
        }

        if (jTable1.getColumnModel().getSelectedColumnCount() == 0) {

        } else if (jTable1.getColumnModel().getSelectedColumnCount() == 1) {
            int col = jTable1.getColumnModel().getSelectedColumns()[0];
            String name = columns.get(col);
            if (name == null) {
                name = "field" + col;
            }
            if ( tool==Tool.DEPEND_0 ) {
                setDep0(name);
            } else if ( tool==Tool.COLUMN ) {
                setColumn(name);
            } else if ( tool==Tool.TIMEFORMAT ) {
                int row= jTable1.getSelectedRow();
                String val= timeFormatTextField.getText() + (String) jTable1.getModel().getValueAt(row, col);
                timeFormatTextField.setText(val);
            } else if ( tool==Tool.FILLVALUE ) {
                int row= jTable1.getSelectedRow();
                String val= (String) jTable1.getModel().getValueAt(row, col);
                fillValueTextField.setText(val);
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

            if (tool==Tool.DEPEND_0 ) {
            } else if ( tool==Tool.COLUMN ) {
                if (haveColumnNames) {
                    setColumn(sfirst + "-" + slast);
                } else {
                    setColumn("" + first + ":" + (last + 1));
                }
            } else if ( tool==Tool.TIMEFORMAT ) {
                int row= jTable1.getSelectedRow();
                String val= timeFormatTextField.getText();
                val+= jTable1.getModel().getValueAt(row, 0);
                for ( int icol= first+1; icol<=last; icol++ ) {
                    val+= "+" + jTable1.getModel().getValueAt(row, icol);
                }
                timeFormatTextField.setText(val);
                dep0timeCheckBox.setSelected(true);
                setDep0(columns.get(first));
            }
        }
        if ( tool!=Tool.NONE ) {
            jTable1.getSelectionModel().clearSelection();
            jTable1.getColumnModel().getSelectionModel().clearSelection();
        }
        clearTool();
    }

    /** Creates new form AsciiDataSourceEditorPanel */
    public AsciiTableDataSourceEditorPanel() {
        initComponents();
        columnsComboBox.requestFocusInWindow();

        parser = new AsciiParser();
        model = new AsciiTableTableModel();
        model.setParser(parser);

        jTable1.setModel(model);
        defaultCellRenderer = jTable1.getDefaultRenderer(Object.class);
        jTable1.setDefaultRenderer(Object.class, new ColSpanTableCellRenderer());

        jScrollPane1.setRowHeaderView( new TableRowHeader(jTable1) );

        model.addPropertyChangeListener(new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent e) {
                if (columns != null) {
                    updateColumns();
                }
                jTable1.repaint();

            }
        });

        jTable1.setCellSelectionEnabled(true);

        jTable1.getColumnModel().getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            public void valueChanged(ListSelectionEvent e) {
                if ( e.getValueIsAdjusting() ) return;
                doSelect( currentTool );
            }
        });


        jTable1.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) return;
                doSelect( currentTool );
            }
        });

        BindingGroup bc = new BindingGroup();

        bc.addBinding(Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, this, BeanProperty.create("skipLines"), this.skipLinesTextField, BeanProperty.create("value")));
        bc.addBinding(Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, this, BeanProperty.create("column"), this.columnsComboBox, BeanProperty.create("selectedItem")));
        bc.addBinding(Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, this, BeanProperty.create("dep0"), this.dep0Columns, BeanProperty.create("selectedItem")));

        bc.bind();
    }

    private void clearTool() {
        if ( currentTool!=Tool.NONE ) {
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
        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel2 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        skipLinesTextField = new javax.swing.JFormattedTextField();
        jToggleButton1 = new javax.swing.JToggleButton();
        jLabel5 = new javax.swing.JLabel();
        commentComboBox = new javax.swing.JComboBox();
        jPanel3 = new javax.swing.JPanel();
        timeFormatTextField = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        timeFormatFieldsComboBox = new javax.swing.JComboBox();
        timeFormatToggleButton = new javax.swing.JToggleButton();
        jPanel1 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        dep0Columns = new javax.swing.JComboBox();
        columnsComboBox = new javax.swing.JComboBox();
        jToggleButton2 = new javax.swing.JToggleButton();
        jToggleButton3 = new javax.swing.JToggleButton();
        dep0timeCheckBox = new javax.swing.JCheckBox();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        fillValueTextField = new javax.swing.JTextField();
        validMinTextField = new javax.swing.JTextField();
        validMaxTextField = new javax.swing.JTextField();
        jToggleButton4 = new javax.swing.JToggleButton();
        guessFillButton = new javax.swing.JButton();

        jTable1.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        jScrollPane1.setViewportView(jTable1);

        jLabel1.setText("Skip Lines:");

        skipLinesTextField.setText("11");
        skipLinesTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                skipLinesTextFieldFocusGained(evt);
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                skipLinesTextFieldFocusLost(evt);
            }
        });

        jToggleButton1.setAction(createToolAction( "skiplines", Tool.SKIPLINES ));
        jToggleButton1.setText("Select");
        jToggleButton1.setToolTipText("select the first row to parse as data or column headers");

        jLabel5.setText("Comment Prefix:");

        commentComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "none", "# hash", "; semicolon", " " }));
        commentComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                commentComboBoxActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel2Layout = new org.jdesktop.layout.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(jLabel1)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(skipLinesTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 35, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jToggleButton1))
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(jLabel5)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(commentComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 109, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(587, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel1)
                    .add(skipLinesTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jToggleButton1))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel5)
                    .add(commentComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(19, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("header", jPanel2);

        jLabel2.setText("time format:");

        timeFormatFieldsComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "select field type...", "iso8601: the times should be iso8601, and do not span a delimiter", "$Y: four digit year", "$y: two digit year", "$m: two-digit month", "$b: month name", "$d: two-digit day of month", "$j: three-digit day of year", "$H: two-digit hour", "$M: two-digit minute", "$S: two-digit second", "$(milli): three-digit milliseconds", "$(micro): three-digit microseconds", "$(ignore): ignore this field", " " }));
        timeFormatFieldsComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timeFormatFieldsComboBoxActionPerformed(evt);
            }
        });

        timeFormatToggleButton.setAction(createToolAction( "timeFormat", Tool.TIMEFORMAT ));
        timeFormatToggleButton.setText("Select");
        timeFormatToggleButton.setToolTipText("Select column or range of columns which contain the times.  These column values will be copied into the time format and you will have to create a template.");
        timeFormatToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timeFormatToggleButtonActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel3Layout = new org.jdesktop.layout.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .add(jLabel2)
                .add(2, 2, 2)
                .add(timeFormatTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 231, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(timeFormatFieldsComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 167, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(timeFormatToggleButton)
                .addContainerGap(259, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel3Layout.createSequentialGroup()
                .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(timeFormatTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel2)
                    .add(timeFormatFieldsComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(timeFormatToggleButton))
                .addContainerGap(52, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("times", jPanel3);

        jLabel3.setText("Column:");

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
        jToggleButton2.setToolTipText("Select column or range of columns");

        jToggleButton3.setAction(createToolAction( "depend0", Tool.DEPEND_0 ));
        jToggleButton3.setText("Select");
        jToggleButton3.setToolTipText("select the column that is the independent variable on which the data values depend.");

        dep0timeCheckBox.setText("time");
        dep0timeCheckBox.setToolTipText("if selected, then the field should be parsed as a time.\n");

        jLabel6.setText("Fill value:");

        jLabel7.setText("Valid Min:");

        jLabel8.setText("Valid Max:");

        fillValueTextField.setText(" ");

        validMinTextField.setText("   ");

        validMaxTextField.setText("    ");
        validMaxTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                validMaxTextFieldActionPerformed(evt);
            }
        });

        jToggleButton4.setAction(createToolAction( "fillValue", Tool.FILLVALUE ));
        jToggleButton4.setText("Select");
        jToggleButton4.setToolTipText("Click on a value to be treated as fill (invalid)");

        guessFillButton.setText("Guess");
        guessFillButton.setToolTipText("try to guess the fill value by looking for an outlier.");
        guessFillButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                guessFillButtonActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(jLabel4)
                    .add(jLabel3))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(dep0Columns, 0, 157, Short.MAX_VALUE)
                    .add(columnsComboBox, 0, 157, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(jToggleButton2)
                        .add(76, 76, 76)
                        .add(jLabel6)
                        .add(18, 18, 18)
                        .add(fillValueTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 69, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(dep0timeCheckBox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jToggleButton3)
                        .add(18, 18, 18)
                        .add(jLabel7)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(validMinTextField)))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(jLabel8)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(validMaxTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 68, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(jToggleButton4)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(guessFillButton)))
                .add(126, 126, 126))
        );

        jPanel1Layout.linkSize(new java.awt.Component[] {fillValueTextField, validMaxTextField, validMinTextField}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel3)
                    .add(columnsComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jToggleButton2)
                    .add(jLabel6)
                    .add(jToggleButton4)
                    .add(guessFillButton)
                    .add(fillValueTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel4)
                    .add(dep0Columns, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(dep0timeCheckBox)
                    .add(jToggleButton3)
                    .add(jLabel7)
                    .add(jLabel8)
                    .add(validMinTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(validMaxTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(17, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("data", jPanel1);

        jTabbedPane1.setSelectedIndex(2);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 819, Short.MAX_VALUE)
            .add(jTabbedPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 819, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(jTabbedPane1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 112, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 342, Short.MAX_VALUE))
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

private void skipLinesTextFieldFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_skipLinesTextFieldFocusGained
}//GEN-LAST:event_skipLinesTextFieldFocusGained

private void skipLinesTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_skipLinesTextFieldFocusLost
}//GEN-LAST:event_skipLinesTextFieldFocusLost

private void timeFormatFieldsComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeFormatFieldsComboBoxActionPerformed
    String s= (String) timeFormatFieldsComboBox.getSelectedItem();
    int i= s.indexOf(":");
    String insert= s.substring(0,i);
    int i0= timeFormatTextField.getCaret().getDot();
    int i1= timeFormatTextField.getCaret().getMark();
    if ( i1<i0 ) {
        int t= i0;
        i0= i1;
        i1= t;
    }
    String text= timeFormatTextField.getText();
    String n= text.substring(0,i0) + insert + text.substring(i1);
    timeFormatTextField.setText(n);
    timeFormatFieldsComboBox.setSelectedIndex(0);
}//GEN-LAST:event_timeFormatFieldsComboBoxActionPerformed

private void timeFormatToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeFormatToggleButtonActionPerformed
    // TODO add your handling code here:
}//GEN-LAST:event_timeFormatToggleButtonActionPerformed

private void commentComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_commentComboBoxActionPerformed
    String value=  (String)commentComboBox.getSelectedItem();
    if ( value.equals("none") || value.trim().equals("") ) {
        params.remove(PARAMS_KEY_COMMENT);
        parser.setCommentPrefix(null);
        update();
    } else {
        int i= value.indexOf(" ");
        String prefix= (i==-1) ? value : value.substring(0,i);
        params.put( PARAMS_KEY_COMMENT, prefix );
        parser.setCommentPrefix(prefix);
        update();
    }
}//GEN-LAST:event_commentComboBoxActionPerformed

private int[] getDataColumns() {
    int [] result= new int[2];
    String scol= column;
    String ecol= column;
    if ( column.contains("-") ) {
        int i= column.indexOf("-");
        ecol= column.substring(i+1);
    }
    for ( Entry<Integer,String> e: columns.entrySet() ) {
        if ( e.getValue().equals(scol)) {
            result[0]= e.getKey();
        }
        if ( e.getValue().equals(ecol)) {
            result[1]= e.getKey();
        }
    }
    return result;
}

private void guessFillButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_guessFillButtonActionPerformed
    fillValueTextField.setText( "moment..." );
    guessFillButton.setEnabled(false);
    Runnable run= new Runnable() {
        public void run() {

            ProgressMonitor mon= null;
            try {
                DataSetBuilder builder= new DataSetBuilder( 1, 100 );

                int[] cols= getDataColumns();

                mon= new NullProgressMonitor();
    //            mon= DasProgressPanel.createFramed("parsing file");

                mon.setTaskSize(model.getRowCount());
                mon.started();
                for ( int i=0; i<model.getRowCount(); i++ ) {
                    mon.setTaskProgress(i);
                    for ( int j=cols[0]; j<=cols[1]; j++ ) {
                        try {
                            builder.putValue(-1, Double.parseDouble(String.valueOf(model.getValueAt(i, j))));
                            builder.nextRecord();
                        } catch (NumberFormatException numberFormatException) {
                        }
                    }
                }
                mon.finished();

                AutoHistogram ah= new AutoHistogram();
                QDataSet hist= ah.doit(builder.getDataSet());

                Map<Double,Integer> outliers= (Map<Double, Integer>) DataSetUtil.getUserProperty( hist, AutoHistogram.USER_PROP_OUTLIERS );
                if ( outliers!=null &&  outliers.size()==1 ) {
                    fillValueTextField.setText( String.valueOf(outliers.keySet().iterator().next()) );
                } else {
                    fillValueTextField.setText( "" );
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            } finally {
                if ( mon!=null ) mon.finished();
                guessFillButton.setEnabled(true);
            }
        }
    };
    new Thread(run).start();
}//GEN-LAST:event_guessFillButtonActionPerformed

private void validMaxTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_validMaxTextFieldActionPerformed
    // TODO add your handling code here:
}//GEN-LAST:event_validMaxTextFieldActionPerformed

    URISplit split = null;
    Map<String,String> params;

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
        Rectangle rect = jTable1.getCellRect(Math.max(0, getSkipLines() - 3), 0, true);
        parser.setSkipLines(skipLines);
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

    public void setURI(String url) {
        try {
            split = URISplit.parse(url);
            params = URISplit.parseParams(split.params);

            File f = DataSetURI.getFile(new URL(split.file), new NullProgressMonitor());
            setFile(f);

            int tab=2;

            if (params.containsKey("skipLines")) {
                setSkipLines(Integer.parseInt(params.get("skipLines")));
            }
            if (params.containsKey("skip")) {
                setSkipLines(Integer.parseInt(params.get("skip")));
            }

            if (params.containsKey("column")) {
                setColumn(params.get("column"));
            }
            if (params.containsKey("rank2")) {
                setColumn(params.get("rank2"));
            }

            if (params.containsKey("depend0")) {
                setDep0(params.get("depend0"));
            }

            if (params.containsKey("time")) {
                setDep0(params.get("time"));
                dep0timeCheckBox.setSelected(true);
            }

            if ( params.containsKey("timeFormat") ) {
                timeFormatTextField.setText(params.get("timeFormat"));
            }

            fillValueTextField.setText( getParam(params, "fill" ));
            validMinTextField.setText( getParam(params, "validMin" ));
            validMaxTextField.setText( getParam(params, "validMax" ));

            jTabbedPane1.setSelectedIndex(tab);
            update();

        } catch (IOException ex) {
            Logger.getLogger(AsciiTableDataSourceEditorPanel.class.getName()).log(Level.SEVERE, null, ex);
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }

    }

    private String getParam( Map<String,String> params, String name ) {
        String result= params.get(name);
        if ( result==null ) return ""; else return result;
    }

    /**
     * insert or remove the parameter if it is empty.
     */
    private void setParam( Map<String,String> params, String name, String s ) {
        s= s.trim();
        if ( s.length()>0 ) {
            params.put(name,s);
        } else {
            params.remove(name);
        }
    }

    public String getURI() {

        String args = "";
        if ( skipLines > 0 ) {
            params.put("skipLines", "" + skipLines);
        } else {
            params.remove("skipLines");
        }
        params.remove("skip");

        if (!this.getDep0().equals("")) {
            if ( dep0timeCheckBox.isSelected() ) {
                params.put("time", this.getDep0());
            } else {
                params.put("depend0", this.getDep0());
            }
        } else {
            params.remove("time");
            params.remove("depend0");
        }

        if (getColumn().contains(":") || getColumn().contains("-")) {
            params.remove("column");
            params.put("rank2", getColumn());
        } else {
            params.put("column", getColumn());
            params.remove("rank2");
        }

        setParam( params, "timeFormat", timeFormatTextField.getText() );
        setParam( params, "fill", fillValueTextField.getText() );
        setParam( params, "validMin", validMinTextField.getText() );
        setParam( params, "validMax", validMaxTextField.getText() );

        split.params = URISplit.formatParams(params);

        return URISplit.format(split);

    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    public javax.swing.JComboBox columnsComboBox;
    public javax.swing.JComboBox commentComboBox;
    public javax.swing.JComboBox dep0Columns;
    public javax.swing.JCheckBox dep0timeCheckBox;
    public javax.swing.JTextField fillValueTextField;
    public javax.swing.JButton guessFillButton;
    public javax.swing.JLabel jLabel1;
    public javax.swing.JLabel jLabel2;
    public javax.swing.JLabel jLabel3;
    public javax.swing.JLabel jLabel4;
    public javax.swing.JLabel jLabel5;
    public javax.swing.JLabel jLabel6;
    public javax.swing.JLabel jLabel7;
    public javax.swing.JLabel jLabel8;
    public javax.swing.JPanel jPanel1;
    public javax.swing.JPanel jPanel2;
    public javax.swing.JPanel jPanel3;
    public javax.swing.JScrollPane jScrollPane1;
    public javax.swing.JTabbedPane jTabbedPane1;
    public javax.swing.JTable jTable1;
    public javax.swing.JToggleButton jToggleButton1;
    public javax.swing.JToggleButton jToggleButton2;
    public javax.swing.JToggleButton jToggleButton3;
    public javax.swing.JToggleButton jToggleButton4;
    public javax.swing.JFormattedTextField skipLinesTextField;
    public javax.swing.JComboBox timeFormatFieldsComboBox;
    public javax.swing.JTextField timeFormatTextField;
    public javax.swing.JToggleButton timeFormatToggleButton;
    public javax.swing.JTextField validMaxTextField;
    public javax.swing.JTextField validMinTextField;
    // End of variables declaration//GEN-END:variables

    private void updateColumns() {
        for (int i = 0; i < jTable1.getColumnCount(); i++) {
            String label;
            if (i < columns.size()) {
                label = columns.get(i);
            } else {
                label = "x"; // hopefully transient
            }
            jTable1.getColumnModel().getColumn(i).setHeaderValue(label);
        }
        jTable1.getTableHeader().repaint();

    }

    private void update() {
        try {
            AsciiParser.DelimParser p = parser.guessSkipAndDelimParser(file.toString());
            if ( parser == null) {
                //               throw new IllegalArgumentException("no records found");
                return;
            }
            
            model.setRecParser(p);
            columns = getColumnNames();

            updateColumns();

            String lcol = getColumn();
            int icol = jTable1.getSelectedColumn();
            columnsComboBox.setModel(new DefaultComboBoxModel(columns.values().toArray()));
            columnsComboBox.setSelectedItem(getColumn());
            if (icol != -1) {
                setColumn(columns.get(icol));
            } else {
                setColumn(lcol);
            }

            List<String> dep0Values = new ArrayList<String>(columns.values());
            String ldep0 = getDep0();
            dep0Values.add(0, "");
            dep0Columns.setModel(new DefaultComboBoxModel(dep0Values.toArray()));
            dep0Columns.setSelectedItem(ldep0);

            String comment="";
            if ( params.containsKey( PARAMS_KEY_COMMENT ) ) {
              comment=   params.get( PARAMS_KEY_COMMENT );
              if ( comment.trim().length()==0 ) {
                  comment= "none";
              }
            }

            ComboBoxModel model= commentComboBox.getModel();
            for ( int i=0; i<model.getSize(); i++ ) {
                if ( ((String)model.getElementAt(i)).startsWith(comment) ) {
                    commentComboBox.setSelectedIndex(i);
                }
            }

        } catch (IOException ex) {
            Logger.getLogger(AsciiTableDataSourceEditorPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
