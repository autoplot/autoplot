/*
 * AsciiDataSourceEditorPanel.java
 *
 * Created on September 5, 2008, 3:47 PM
 */
package org.virbo.ascii;

import java.awt.event.MouseEvent;
import java.net.URISyntaxException;
import org.virbo.datasource.ui.TableRowHeader;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import org.autoplot.help.AutoplotHelpSystem;
import org.das2.datum.TimeUtil;
import org.das2.util.filesystem.FileSystem;
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
import org.virbo.dsutil.AsciiHeadersParser;
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
        NONE, SKIPLINES, COLUMN, DEPEND_0, TIMEFORMAT, GUESSTIMEFORMAT, FILLVALUE,
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
            } else if ( tool==Tool.GUESSTIMEFORMAT ) {
                int row= jTable1.getSelectedRow();
                String val= (String) jTable1.getModel().getValueAt(row, col);
                timeFormatTextField.setText(val);
                guessTimeFormatButtonAP();
            } else if ( tool==Tool.FILLVALUE ) {
                int row= jTable1.getSelectedRow();
                String val= (String) jTable1.getModel().getValueAt(row, col);
                if ( val.contains("+") ) val= ""+Double.parseDouble(val);
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
                StringBuilder val= new StringBuilder( timeFormatTextField.getText() ); // don't clubber existing work
                val.append( jTable1.getModel().getValueAt(row, first) );
                for ( int icol= first+1; icol<=last; icol++ ) {
                    val.append( "+" ).append( jTable1.getModel().getValueAt(row, icol) );
                }
                timeFormatTextField.setText(val.toString());
                dep0timeCheckBox.setSelected(true);
                setDep0(columns.get(first));

            } else if ( tool==Tool.GUESSTIMEFORMAT ) {
                int row= jTable1.getSelectedRow();
                StringBuilder val= new StringBuilder(); // existing work is clubbered
                val.append( jTable1.getModel().getValueAt(row, first) );
                for ( int icol= first+1; icol<=last; icol++ ) {
                    val.append( "+" ).append( jTable1.getModel().getValueAt(row, icol) );
                }
                timeFormatTextField.setText(val.toString());
                dep0timeCheckBox.setSelected(true);
                setDep0(columns.get(first));
                guessTimeFormatButtonAP();
            }
        }
        Tool oldTool= tool;
        clearTool(); // otherwise we would respond to deselection event

        if ( oldTool!=Tool.NONE ) {
            jTable1.getSelectionModel().clearSelection();
            jTable1.getColumnModel().getSelectionModel().clearSelection();
        }
    }

    /** Creates new form AsciiDataSourceEditorPanel */
    public AsciiTableDataSourceEditorPanel() {
        initComponents();

        AutoplotHelpSystem.getHelpSystem().registerHelpID(this, "ascii_main");
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
        jTable1.getTableHeader().setReorderingAllowed(false);

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

        jTable1.getTableHeader().addMouseListener( new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                int i= jTable1.getTableHeader().columnAtPoint(e.getPoint());
                String name= columns.get(i);
                if ( currentTool==Tool.DEPEND_0 ) {
                    setDep0(name);
                    clearTool(); // otherwise we would respond to deselection event
                } else if ( currentTool==Tool.COLUMN ) {
                    setColumn(name);
                    clearTool(); // otherwise we would respond to deselection event
                }
            }
        } );

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
        jLabel9 = new javax.swing.JLabel();
        guessTimeFormatToggleButton = new javax.swing.JToggleButton();
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
        jSeparator1 = new javax.swing.JSeparator();
        bundleCheckBox = new javax.swing.JCheckBox();

        jTable1.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        jScrollPane1.setViewportView(jTable1);

        jLabel1.setText("Skip Lines:");
        jLabel1.setToolTipText("Skip this many lines before attempting to parse data.  Note if the first line contains parsable column labels, they will be used to identify each column.\n");

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
        jToggleButton1.setToolTipText("Select the first row to parse as data or column headers.");

        jLabel5.setText("Comment Prefix:");
        jLabel5.setToolTipText("Select a character that is the beginning of records that can be ignored.  \n");

        commentComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "# hash", "; semicolon", "none", "" }));
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
                .addContainerGap()
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(jLabel1)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(skipLinesTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 47, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(18, 18, 18)
                        .add(jToggleButton1))
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(jLabel5)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(commentComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 109, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(528, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(skipLinesTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jToggleButton1)
                    .add(jLabel1))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(commentComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel5))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("header", jPanel2);

        jLabel2.setText("Time Format:");
        jLabel2.setToolTipText("<html>Specify the format of time strings, such as \"%Y %m %d.\"<br>\nPluses join adjectent fields, and the droplist to the right<br>\ncan be used to select field types.  The Guess button attempts <br>\nto guess the format of selected fields.</html>\n");

        timeFormatFieldsComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "select field type...", "$Y: four digit year", "$y: two digit year", "$m: two-digit month", "$b: month name", "$d: two-digit day of month", "$j: three-digit day of year", "$H: two-digit hour", "$M: two-digit minute", "$S: two-digit second", "$(milli): three-digit milliseconds", "$(micro): three-digit microseconds", "$(ignore): ignore this field", " " }));
        timeFormatFieldsComboBox.setToolTipText("Use this droplist to select a field type, and the Select button copies the field type to the Time Format text field.");
        timeFormatFieldsComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timeFormatFieldsComboBoxActionPerformed(evt);
            }
        });

        timeFormatToggleButton.setAction(createToolAction( "timeFormat", Tool.TIMEFORMAT ));
        timeFormatToggleButton.setText("Select");
        timeFormatToggleButton.setToolTipText("Select column or range of columns which contain the times.  These column values will be copied into the time format and you will have to create a template.");

        jLabel9.setText("<html><em>Specify time format (e.g. $Y$m+$d) when times are not iso8601 or span multiple fields.</em></html>");

        guessTimeFormatToggleButton.setAction(createToolAction( "guessTimeFormat", Tool.GUESSTIMEFORMAT ));
        guessTimeFormatToggleButton.setText("Guess");
        guessTimeFormatToggleButton.setToolTipText("<html>Scan the selected columns and try to identify field types.<br> This uses the number of digits in each field to identify the type<br> (e.g. 3 digits implies day of year), so select the cells accordingly.</html>");
        guessTimeFormatToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                guessTimeFormatToggleButtonActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel3Layout = new org.jdesktop.layout.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jLabel9, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jPanel3Layout.createSequentialGroup()
                        .add(jLabel2)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(timeFormatTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 221, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(timeFormatFieldsComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 167, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(timeFormatToggleButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(guessTimeFormatToggleButton)))
                .addContainerGap(149, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel3Layout.createSequentialGroup()
                .add(6, 6, 6)
                .add(jLabel9, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(timeFormatTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(timeFormatFieldsComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(timeFormatToggleButton)
                    .add(jLabel2)
                    .add(guessTimeFormatToggleButton))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("times", jPanel3);

        jLabel3.setText("Column(s):");
        jLabel3.setToolTipText("Select the column to plot.  Multiple, adjacent columns can be plotted as a spectrogram by using the colon or dash character\n");

        jLabel4.setText("Depends On:");
        jLabel4.setToolTipText("Select the variable that is the independent parameter that Columns depends on.  Note ISO8601 times are handled, or use the \"times\" tab to specify a time format.\n");

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
        jToggleButton2.setToolTipText("Select column or range of columns");

        jToggleButton3.setAction(createToolAction( "depend0", Tool.DEPEND_0 ));
        jToggleButton3.setText("Select");
        jToggleButton3.setToolTipText("Select the column that is the independent variable on which the data values depend.");

        dep0timeCheckBox.setText("time");
        dep0timeCheckBox.setToolTipText("If selected, then the field should be parsed as a time.\n");

        jLabel6.setText("Fill Value:");
        jLabel6.setToolTipText("This numeric value will be treated as fill.  Note non-numeric values are automatically handled as fill.\n");

        jLabel7.setText("Valid Min:");
        jLabel7.setToolTipText("Numbers below this value are treated as invalid.\n");

        jLabel8.setText("Valid Max:");
        jLabel8.setToolTipText("Numbers above this value are treated as invalid.");

        fillValueTextField.setText(" ");
        fillValueTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                fillValueTextFieldFocusLost(evt);
            }
        });

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

        jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);

        bundleCheckBox.setText("bundle");
        bundleCheckBox.setToolTipText("<html>Range of columns should be treated as a bundle of parameters, like X, Y, Z.  When plotted, these will result in separate traces instead of a spectrogram.</html>");

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(jLabel3)
                    .add(jLabel4))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(dep0Columns, 0, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(columnsComboBox, 0, 166, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(bundleCheckBox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jToggleButton2))
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(dep0timeCheckBox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jToggleButton3)))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jSeparator1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(4, 4, 4)
                        .add(jLabel6)
                        .add(18, 18, 18)
                        .add(fillValueTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 69, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(5, 5, 5)
                        .add(jLabel7)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(validMinTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 69, Short.MAX_VALUE)))
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
                .addContainerGap())
        );

        jPanel1Layout.linkSize(new java.awt.Component[] {fillValueTextField, validMaxTextField, validMinTextField}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(columnsComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(jLabel3))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(dep0Columns, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(jLabel4)))
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(jLabel6)
                            .add(jToggleButton4)
                            .add(guessFillButton)
                            .add(fillValueTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(jLabel7)
                            .add(jLabel8)
                            .add(validMinTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(validMaxTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(6, 6, 6)
                        .add(jSeparator1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 55, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(jToggleButton2)
                            .add(bundleCheckBox))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(dep0timeCheckBox)
                            .add(jToggleButton3))))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("data", jPanel1);

        jTabbedPane1.setSelectedIndex(2);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 780, Short.MAX_VALUE)
            .add(jTabbedPane1)
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

private void commentComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_commentComboBoxActionPerformed
    String value=  (String)commentComboBox.getSelectedItem();
    if ( value.equals("# hash" ) ) {        
        params.remove(PARAMS_KEY_COMMENT);
        parser.setCommentPrefix("#");
        update();
    } else if ( value.equals("none") || value.trim().equals("") ) {
        params.put(PARAMS_KEY_COMMENT,"");
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

private void guessTimeFormatButtonAP( ) {
    String text= timeFormatTextField.getText().trim();
    String[] ss= text.split("\\+");
    int curr= TimeUtil.YEAR;
    StringBuilder template= new StringBuilder();
    boolean giveUp= false;
    for ( int i=0; i<ss.length; i++ ) {
        String s= ss[i];
        boolean isInt= true;
        int intVal=-1;
        try {
            intVal= Integer.parseInt(s);
        } catch ( NumberFormatException ex ) {
            isInt= false;
        }
        int slen= s.length();
        int ocurr= curr;
        if ( s.startsWith("$" ) ) {
            giveUp= true;
        }
        if ( giveUp ) {
            // do nothing
        } else if ( curr==TimeUtil.YEAR ) {
            if ( slen==4 ) {
                template.append("$Y");
                curr= TimeUtil.MONTH;
            } else if ( slen==2 ) {
                template.append("$y");
                curr= TimeUtil.MONTH;
            } else if ( slen==8 ) {
                template.append("$Y$m$d");
                curr= TimeUtil.HOUR;
            } else if ( slen==7 ) {
                template.append("$Y$j");
                curr= TimeUtil.HOUR;
            }
        } else if ( curr==TimeUtil.MONTH ) {
            if ( slen<=2 && isInt && intVal<=12 ) {
                template.append("$m");
                curr= TimeUtil.DAY;
            } else if ( slen==3 ) {
                if ( Character.isDigit( s.charAt(0) ) ) {
                    template.append("$j");
                    curr= TimeUtil.HOUR;
                } else {
                    template.append("$b");
                    curr= TimeUtil.DAY;
                }
            } else if ( isInt && intVal>12 ) {
                template.append("$j");
                curr= TimeUtil.HOUR;
            }
        } else if ( curr==TimeUtil.DAY ) {
            if ( slen<=2 ) {
                template.append("$d");
                curr= TimeUtil.HOUR;
            }
        } else if ( curr==TimeUtil.HOUR ) {
            if ( slen<=2 ) {
                template.append("$H");
                curr= TimeUtil.MINUTE;
            }
        } else if ( curr==TimeUtil.MINUTE ) {
            if ( slen<=2 ) {
                template.append("$M");
                curr= TimeUtil.SECOND;
            }
        } else if ( curr==TimeUtil.SECOND ) {
            template.append("$S");
            curr= TimeUtil.MILLI;
        }
        if ( curr==ocurr ) {
            template.append(s);
            giveUp= true;
        }
        if ( i<ss.length-1 ) template.append("+");
    }
    timeFormatTextField.setText(template.toString());
}


private void fillValueTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_fillValueTextFieldFocusLost
    String val= fillValueTextField.getText();
    if ( val.trim().length()>0 ) {
        try {
            double d= Double.parseDouble(val);
            if ( val.contains("+") ) {
                fillValueTextField.setText(String.valueOf(d));
            }
        } catch (NumberFormatException ex ) {
            JOptionPane.showMessageDialog(this,"Only enter numbers here.  Non-numbers are treated as fill.");
        }
    }
}//GEN-LAST:event_fillValueTextFieldFocusLost

private void guessTimeFormatToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_guessTimeFormatToggleButtonActionPerformed
    // TODO add your handling code here:
}//GEN-LAST:event_guessTimeFormatToggleButtonActionPerformed

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

        String[] lcolumns = parser.getFieldNames();
        Map<Integer, String> result = new LinkedHashMap<Integer, String>();
        for (int i = 0; i < lcolumns.length; i++) {
            result.put(i, lcolumns[i]);
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
        int i= column.indexOf(": "); // allow field1:field5 but not "L: field4-field18"
        if ( i>-1 ) {
            return column.substring(0,i);
        } else {
            return column;
        }
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

    public boolean reject( String url ) throws IOException, URISyntaxException {
        split = URISplit.parse(url);
        FileSystem fs = FileSystem.create( DataSetURI.toUri(split.path) );
        if ( fs.isDirectory( split.file.substring(split.path.length()) ) ) {
            return true;
        }
        return false;
    }

    public boolean prepare( String url, java.awt.Window parent, ProgressMonitor mon) throws Exception {
        split = URISplit.parse(url);
        DataSetURI.getFile( DataSetURI.toUri(split.file), mon );
        return true;
    }

    public void setURI(String url) {
        try {
            split = URISplit.parse(url);
            params = URISplit.parseParams(split.params);

            File f = DataSetURI.getFile( DataSetURI.toUri(split.file), new NullProgressMonitor() );
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
            if (params.containsKey("bundle")) {
                setColumn(params.get("bundle"));
                bundleCheckBox.setSelected(true);
            }
            String arg0= params.get("arg_0"); // typically we're going to specify a range.
            if ( arg0!=null ) {
                String col= ":";
                if ( arg0.equals("rank2") ) {
                    setColumn(col);
                } else if ( arg0.equals("bundle") ) {
                    setColumn(col);
                    bundleCheckBox.setSelected(true);
                }
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
            checkHeaders();

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

        if ( skipLines > 0 ) {
            params.put("skipLines", "" + skipLines);
        } else {
            params.remove("skipLines");
        }
        params.remove("skip");

        if (!this.getDep0().equals("")) {
            if ( dep0timeCheckBox.isSelected() ) {
                params.put("time", this.getDep0());
                params.remove("depend0");
            } else {
                params.put("depend0", this.getDep0());
                params.remove("time");
            }
        } else {
            params.remove("time");
            params.remove("depend0");
        }

        params.remove("arg_0");

        if (getColumn().contains(":") || getColumn().contains("-")) {
            params.remove("column");
            if ( bundleCheckBox.isSelected() ) {
                params.put("bundle", getColumn());
            } else {
                params.put("rank2", getColumn());
            }
        } else {
            params.put("column", getColumn());
            params.remove("rank2");
            params.remove("bundle");
        }
        params.remove("group");

        setParam( params, "timeFormat", timeFormatTextField.getText() );
        setParam( params, "fill", fillValueTextField.getText() );
        setParam( params, "validMin", validMinTextField.getText() );
        setParam( params, "validMax", validMaxTextField.getText() );

        split.params = URISplit.formatParams(params);

        return URISplit.format(split);

    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    public javax.swing.JCheckBox bundleCheckBox;
    public javax.swing.JComboBox columnsComboBox;
    public javax.swing.JComboBox commentComboBox;
    public javax.swing.JComboBox dep0Columns;
    public javax.swing.JCheckBox dep0timeCheckBox;
    public javax.swing.JTextField fillValueTextField;
    public javax.swing.JButton guessFillButton;
    public javax.swing.JToggleButton guessTimeFormatToggleButton;
    public javax.swing.JLabel jLabel1;
    public javax.swing.JLabel jLabel2;
    public javax.swing.JLabel jLabel3;
    public javax.swing.JLabel jLabel4;
    public javax.swing.JLabel jLabel5;
    public javax.swing.JLabel jLabel6;
    public javax.swing.JLabel jLabel7;
    public javax.swing.JLabel jLabel8;
    public javax.swing.JLabel jLabel9;
    public javax.swing.JPanel jPanel1;
    public javax.swing.JPanel jPanel2;
    public javax.swing.JPanel jPanel3;
    public javax.swing.JScrollPane jScrollPane1;
    public javax.swing.JSeparator jSeparator1;
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
        int n= jTable1.getColumnCount();
        int wide= n<5 ? 210 : 170;
        int norm= n<5 ? 100 : 70;
        int narrow= n<5 ? 50 : 50;

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
            } else if ( s.length()<5 ) {
                jTable1.getColumnModel().getColumn(i).setPreferredWidth(narrow); 
            } else {
                jTable1.getColumnModel().getColumn(i).setPreferredWidth(norm);
            }
        }
        jTable1.getTableHeader().repaint();

    }

    private void checkHeaders() {
        try {
            AsciiParser.DelimParser p = parser.guessSkipAndDelimParser(file.toString());
            if (p == null) {
                //               throw new IllegalArgumentException("no records found");
                return;
            }
            model.setRecParser(p);
            columns = getColumnNames();
            ParseException richHeaderWarn = null;
            boolean isRichHeader = AsciiParser.isRichHeader(p.header);
            if (isRichHeader) {
                try {
                    String[] columns1=  new String[p.fieldCount()];
                    for ( int i=0; i<columns1.length; i++ )  columns1[i]="";
                    AsciiHeadersParser.parseMetadata(p.header,columns1,columns1);
                } catch (ParseException ex) {
                    Logger.getLogger(AsciiTableDataSourceEditorPanel.class.getName()).log(Level.SEVERE, null, ex);
                    richHeaderWarn = ex;
                }
            }
            if (richHeaderWarn != null) {
                JOptionPane.showMessageDialog( this.jTable1,
                        "<html>Rich headers are JSON headers that provide additional information about the parameters in the text file.<br>"
                        + "There was an error when parsing the headers.<br><br>"+richHeaderWarn.toString()+"</html>", "Rich Headers parser error detected",
                        JOptionPane.OK_OPTION );
            }
        } catch (IOException ex) {
            Logger.getLogger(AsciiTableDataSourceEditorPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void update() {
        try {
            AsciiParser.DelimParser p = parser.guessSkipAndDelimParser(file.toString());
            if ( p == null) {
                //               throw new IllegalArgumentException("no records found");
                return;
            }
            
            model.setRecParser(p);

            Map<Integer,String> list= new LinkedHashMap();
            columns = getColumnNames();

            Map<String,String> xx= parser.getRichFields();
            int ij= columns.size();
            for ( Entry<String,String> s: xx.entrySet() ) {
                list.put( ij, s.getKey() );
                ij= ij+1;
            }

            list.putAll(columns);

            updateColumns();

            String lcol = getColumn();
            int icol = jTable1.getSelectedColumn();
            columnsComboBox.setModel(new DefaultComboBoxModel(list.values().toArray()));
            columnsComboBox.setSelectedItem(getColumn());
            if (icol != -1) {
                setColumn(columns.get(icol));
            } else {
                setColumn(lcol);
            }

            List<String> dep0Values = new ArrayList<String>(list.values());
            String ldep0 = getDep0();
            dep0Values.add(0, "");
            dep0Columns.setModel(new DefaultComboBoxModel(dep0Values.toArray()));
            dep0Columns.setSelectedItem(ldep0);

            String comment="#";
            if ( params.containsKey( PARAMS_KEY_COMMENT ) ) {
              comment=   params.get( PARAMS_KEY_COMMENT );
              if ( comment.trim().length()==0 ) {
                  comment= "none";
              }
            }

            ComboBoxModel model1= commentComboBox.getModel();
            for ( int i=0; i<model1.getSize(); i++ ) {
                if ( ((String)model1.getElementAt(i)).startsWith(comment) ) {
                    commentComboBox.setSelectedIndex(i);
                }
            }

        } catch (IOException ex) {
            Logger.getLogger(AsciiTableDataSourceEditorPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
