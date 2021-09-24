/*
 * AsciiDataSourceEditorPanel.java
 *
 * Created on September 5, 2008, 3:47 PM
 */
package org.autoplot.ascii;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.net.URISyntaxException;
import org.autoplot.datasource.ui.TableRowHeader;
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
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import org.autoplot.help.AutoplotHelpSystem;
import org.das2.datum.TimeParser;
import org.das2.datum.TimeUtil;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.das2.qds.DataSetUtil;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceEditorPanel;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.ui.Util;
import org.das2.components.DasProgressLabel;
import org.das2.qds.util.AsciiHeadersParser;
import org.das2.qds.util.AsciiParser;
import org.das2.qds.util.AutoHistogram;
import org.das2.qds.util.DataSetBuilder;

/**
 * editor panel for managing ASCII table URIs.
 * @author  jbf
 */
public class AsciiTableDataSourceEditorPanel extends javax.swing.JPanel implements DataSourceEditorPanel {

    private static final Logger logger= Logger.getLogger("apdss.ascii");
    AsciiTableTableModel model;
    Map<Integer, String> columns;
    AsciiParser parser;
    boolean focusDep0 = false;
    TableCellRenderer defaultCellRenderer;
    boolean isRichHeader= false;
    boolean initializing= true;
    
    @Override
    public void markProblems(List<String> problems) {
        
    }

    private enum Tool {
        NONE, SKIPLINES, COLUMN, DEPEND_0, TIMEFORMAT, GUESSTIMEFORMAT, FILLVALUE, DEPEND_1
    }

    private final static String PARAMS_KEY_COMMENT="comment";
    
    Tool currentTool = Tool.NONE;
    JToggleButton currentToolButton;

    Action createToolAction(final String label, final Tool t) {
        assert ( t!=Tool.NONE );
        return new AbstractAction(label) {
            @Override
            public void actionPerformed(ActionEvent e) {
                if ( e.getSource() instanceof JToggleButton ) {
                    if ( currentTool!=Tool.NONE ) {
                        clearTool();
                        return;
                    }
                    if ( jTable1.getSelectionModel().isSelectionEmpty() ) {
                        jTable1.getSelectionModel().clearSelection();
                        jTable1.getColumnModel().getSelectionModel().clearSelection();
                        currentToolButton= ( JToggleButton ) e.getSource();
                        Util.enableComponents( (Container)jTabbedPane1.getSelectedComponent(), false, (Component)e.getSource() );
                        jTabbedPane1.setEnabled(false);
                        setCurrentTool(t);
                    } else {
                        currentToolButton= ( JToggleButton ) e.getSource();
                        currentTool = t;
                        setCurrentTool(t);
                        doSelect( t );
                        jTable1.getSelectionModel().clearSelection();
                        jTable1.getColumnModel().getSelectionModel().clearSelection();
                    }
                }
            }
        };
    }

    private static String getColumnsString( int[] cols, Map<Integer,String> columnsMap ) {
        boolean haveColumnNames = true;
        int last = cols[cols.length-1];
        String slast= columnsMap.get(last);
        if (slast == null) {
            haveColumnNames = false;
        }
        StringBuilder sb= new StringBuilder();
        int t0= -999;
        int r0= -999; 
        for ( int t: cols ) {
            if ( t-t0>1 ) {
                if ( t0>-999 ) sb.append(columnsMap.get(t0)).append(',');
                t0=t;
                r0=t;
            } else {
                if ( r0>-999 ) {
                    if ( haveColumnNames ) {
                        sb.append(columnsMap.get(r0)).append('-');
                    } else {
                        sb.append(columnsMap.get(r0)).append('-'); //TODO
                    }
                    r0=-999;
                }
            }
            t0= t;
        }
        if ( t0>-999 ) sb.append( columnsMap.get(t0) );
        return sb.toString();
    }
    
    /**
     * do the actions for the tool based on the current selection.
     * @param tool
     */
    private void doSelect( Tool tool  ) {
        if ( tool==Tool.SKIPLINES ) {
            if (jTable1.getSelectedRow() != -1) {
                setSkipLines(jTable1.getSelectedRow());
                clearTool();
            }
        }

        switch (jTable1.getColumnModel().getSelectedColumnCount()) {
            case 0:
                break;
            case 1:
                int col = jTable1.getColumnModel().getSelectedColumns()[0];
                String name=null;
                if ( columns!=null ) {
                    name = columns.get(col);
                }   
                if (name == null) name = "field" + col;
                if ( tool==Tool.DEPEND_0 ) {
                    setDep0(name);
                } else if ( tool==Tool.COLUMN ) {
                    setColumn(name);
                } else if ( tool==Tool.TIMEFORMAT ) {
                    int row= jTable1.getSelectedRow();
                    String val= timeFormatCB.getSelectedItem().toString() + (String) jTable1.getModel().getValueAt(row, col);
                    timeFormatCB.setSelectedItem(val);
                } else if ( tool==Tool.GUESSTIMEFORMAT ) {
                    int row= jTable1.getSelectedRow();
                    String val= (String) jTable1.getModel().getValueAt(row, col);
                    timeFormatCB.setSelectedItem(val);
                    guessTimeFormatButtonAP(row,col,col);
                } else if ( tool==Tool.FILLVALUE ) {
                    int row= jTable1.getSelectedRow();
                    String val= (String) jTable1.getModel().getValueAt(row, col);
                    if ( val.contains("+") ) val= ""+Double.parseDouble(val);
                    fillValueTextField.setText(val);
                }   
                break;
            default:
                int[] cols = jTable1.getColumnModel().getSelectedColumns();
                String scols= getColumnsString( cols, columns );

                if (tool==Tool.DEPEND_0 ) {
                    
                } else if ( tool==Tool.COLUMN ) {
                    setColumn(scols);
                } else if ( tool==Tool.DEPEND_1 ) {
                    dep1Values.setSelectedItem(scols);
                } else if ( tool==Tool.TIMEFORMAT ) {
                    int row= jTable1.getSelectedRow();
                    int first = cols[0];
                    int last = cols[cols.length - 1];
                    StringBuilder val= new StringBuilder( timeFormatCB.getSelectedItem().toString() ); // don't clubber existing work
                    val.append( jTable1.getModel().getValueAt(row, first) );
                    for ( int icol= first+1; icol<=last; icol++ ) {
                        val.append( "+" ).append( jTable1.getModel().getValueAt(row, icol) );
                    }
                    timeFormatCB.setSelectedItem(val.toString());
                    dep0timeCheckBox.setSelected(true);
                    setDep0(columns.get(first));
                    
                } else if ( tool==Tool.GUESSTIMEFORMAT ) {
                    int first = cols[0];
                    int last = cols[cols.length - 1];
                    int row= jTable1.getSelectedRow();
                    StringBuilder val= new StringBuilder(); // existing work is clubbered
                    val.append( jTable1.getModel().getValueAt(row, first) );
                    for ( int icol= first+1; icol<=last; icol++ ) {
                        val.append( "+" ).append( jTable1.getModel().getValueAt(row, icol) );
                    }
                    timeFormatCB.setSelectedItem(val.toString());
                    dep0timeCheckBox.setSelected(true);
                    setDep0(columns.get(first));
                    guessTimeFormatButtonAP(row,first,last);
                }
                break;
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
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                if (columns != null) {
                    updateColumns(jTable1,columns);
                }
                jTable1.repaint();

            }
        });

        jTable1.setCellSelectionEnabled(true);
        jTable1.getTableHeader().setReorderingAllowed(false);

        jTable1.getColumnModel().getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if ( e.getValueIsAdjusting() ) return;
                doSelect( currentTool );
            }
        });
        
        jTable1.getTableHeader().addMouseListener( new MouseAdapter() {
            @Override
            public void mouseClicked( MouseEvent e ) {
                int col= jTable1.columnAtPoint( e.getPoint() );
                String name=null;
                if ( columns!=null ) {
                    name = columns.get(col);
                }   
                if (name == null) name = "field" + col;
                if ( null!=currentTool ) switch (currentTool) {
                    case COLUMN:
                        setColumn(name);
                        break;
                    case DEPEND_0:
                        setDep0(name);
                        break;
                    case NONE:
                        setColumn(name);
                        break;
                    default:
                        break;
                }
            }
        });


        jTable1.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
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
                Runnable run= new Runnable() {
                    @Override
                    public void run() {
                        clearTool();
                    }
                };
                SwingUtilities.invokeLater(run);
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
            if ( !jTabbedPane1.isEnabled() ) {
                jTabbedPane1.setEnabled(true);
                Util.enableComponents( (Container)jTabbedPane1.getSelectedComponent(), true, null );
            }
            currentTool = Tool.NONE;
            currentToolButton.setSelected(false);
            currentToolButton = null;
            timesStatusLabel.setText("");
            dataStatusLabel.setText("");
            headerLabel.setText("");
        }
    }
    
    /**
     * set the current tool
     * @param tool 
     */
    private void setCurrentTool( Tool tool ) {
        this.currentTool= tool;
        String message= "set tool "+tool;
        if ( null!=tool ) switch (tool) {
            case FILLVALUE:
                message= "Click on value to use as fill (missing) value.";
                break;
            case GUESSTIMEFORMAT:
                message= "Select cells which should be used to guess time format.";
                break;
            case TIMEFORMAT:
                message= "Select cells to copy, which must then be converted to time format template.";
                break;
            case SKIPLINES:
                message= "Click on the row of the first record.";
                break;
            case DEPEND_0:
                message= "Click on the first column which is the independent parameter, often time.";
                break;
            case DEPEND_1:
                message= "Drag to select the cells which identify the column values, used for the Y-values in a spectrogram.";
                break;
            case COLUMN:
                message= "Click on the column to use.";
                break;
            case NONE:
                message= "";
                break;
            default:
                break;
        }
        this.dataStatusLabel.setText( message );
        this.timesStatusLabel.setText( message );
        this.headerLabel.setText( message );
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        bindingGroup = new org.jdesktop.beansbinding.BindingGroup();

        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel2 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        skipLinesTextField = new javax.swing.JFormattedTextField();
        jToggleButton1 = new javax.swing.JToggleButton();
        jLabel5 = new javax.swing.JLabel();
        commentComboBox = new javax.swing.JComboBox();
        headerLabel = new javax.swing.JLabel();
        jLabel14 = new javax.swing.JLabel();
        dep1Values = new javax.swing.JComboBox();
        jToggleButton5 = new javax.swing.JToggleButton();
        jPanel3 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        timeFormatFieldsComboBox = new javax.swing.JComboBox();
        timeFormatToggleButton = new javax.swing.JToggleButton();
        jLabel9 = new javax.swing.JLabel();
        guessTimeFormatToggleButton = new javax.swing.JToggleButton();
        timesStatusLabel = new javax.swing.JLabel();
        timeFormatCB = new javax.swing.JComboBox<>();
        dataPanel = new javax.swing.JPanel();
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
        dataStatusLabel = new javax.swing.JLabel();
        jPanel4 = new javax.swing.JPanel();
        jLabel10 = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        titleTextField = new javax.swing.JTextField();
        labelTextField = new javax.swing.JTextField();
        jLabel12 = new javax.swing.JLabel();
        jLabel13 = new javax.swing.JLabel();
        unitsTF = new javax.swing.JTextField();
        depend0unitsCB = new javax.swing.JComboBox<>();
        jPanel5 = new javax.swing.JPanel();
        whereCB = new javax.swing.JCheckBox();
        whereParamList = new javax.swing.JComboBox<>();
        whereOp = new javax.swing.JComboBox<>();
        whereValueCB = new javax.swing.JComboBox<>();
        xyzPanel = new javax.swing.JPanel();
        xComboBox = new javax.swing.JComboBox<>();
        xCheckBox = new javax.swing.JCheckBox();
        yCheckBox = new javax.swing.JCheckBox();
        yComboBox = new javax.swing.JComboBox<>();
        zCheckBox = new javax.swing.JCheckBox();
        zComboBox = new javax.swing.JComboBox<>();
        jLabel15 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();
        jLayeredPane1 = new javax.swing.JLayeredPane();

        setName("asciiTableDataSourceEditorPanel"); // NOI18N

        jLabel1.setText("Skip Lines:");
        jLabel1.setToolTipText("Skip this many lines before attempting to parse data.  Note if the first line contains parsable column labels, they will be used to identify each column.\n");

        skipLinesTextField.setText("11");
        skipLinesTextField.setToolTipText("");
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
        jToggleButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jToggleButton1ActionPerformed(evt);
            }
        });

        jLabel5.setText("Comment Prefix:");
        jLabel5.setToolTipText("Select a character that is the beginning of records that can be ignored.  \n");

        commentComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "# hash", "; semicolon", "none", "" }));
        commentComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                commentComboBoxActionPerformed(evt);
            }
        });

        jLabel14.setText("Column Values:");
        jLabel14.setToolTipText("Select the columns which will be the Y-axis values for each row of a spectrogram.  ");

        dep1Values.setEditable(true);
        dep1Values.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        jToggleButton5.setAction(createToolAction( "depend1", Tool.DEPEND_1 ));
        jToggleButton5.setText("Select");

        org.jdesktop.layout.GroupLayout jPanel2Layout = new org.jdesktop.layout.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(headerLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(jPanel2Layout.createSequentialGroup()
                                .add(jLabel5)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(commentComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 109, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                            .add(jPanel2Layout.createSequentialGroup()
                                .add(jLabel1)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(skipLinesTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 47, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                .add(18, 18, 18)
                                .add(jToggleButton1)
                                .add(38, 38, 38)
                                .add(jLabel14)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(dep1Values, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 181, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(jToggleButton5)))
                        .add(0, 180, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(skipLinesTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jToggleButton1)
                    .add(jLabel1)
                    .add(jLabel14)
                    .add(dep1Values, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jToggleButton5))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(commentComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel5))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(headerLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 24, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("header", jPanel2);

        jLabel2.setText("Time Format:");
        jLabel2.setToolTipText("<html>Specify the format of time strings, such as \"%Y %m %d.\"<br>\nPluses join adjacent fields, and the droplist to the right<br>\ncan be used to select field types.  The Guess button attempts <br>\nto guess the format of selected fields.</html>\n");

        timeFormatFieldsComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "select field type...", "$Y: four digit year", "$y: two digit year", "$m: two-digit month", "$b: month name", "$d: two-digit day of month", "$j: three-digit day of year", "$H: two-digit hour", "$M: two-digit minute", "$S: two-digit second", "$(milli): three-digit milliseconds", "$(micro): three-digit microseconds", "$x: ignore this field", "$(subsec;places=6): microseconds", "$(subsec;places=9): nanoseconds", "+: field separator" }));
        timeFormatFieldsComboBox.setToolTipText("Use this droplist to select a field type, and the Select button copies the field type to the Time Format text field.");
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

        jLabel9.setText("<html><i>Specify time format (e.g. $Y$m+$d) when times are not ISO8601 or span multiple fields.</i></html>");

        guessTimeFormatToggleButton.setAction(createToolAction( "guessTimeFormat", Tool.GUESSTIMEFORMAT ));
        guessTimeFormatToggleButton.setText("Guess");
        guessTimeFormatToggleButton.setToolTipText("<html>This will scan the selected columns and try to identify field types.<br> This uses the number of digits in each field to identify the type<br> (e.g. 3 digits implies day of year), so select the cells accordingly.</html>");
        guessTimeFormatToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                guessTimeFormatToggleButtonActionPerformed(evt);
            }
        });

        timesStatusLabel.setText(" ");

        timeFormatCB.setEditable(true);
        timeFormatCB.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "$Y$m+$d", "$Y+$m+$S", "$Y-$j+$H:$M:$S", "ISO8601" }));
        timeFormatCB.setSelectedIndex(-1);
        timeFormatCB.setSelectedItem("");

        org.jdesktop.layout.GroupLayout jPanel3Layout = new org.jdesktop.layout.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel3Layout.createSequentialGroup()
                .add(24, 24, 24)
                .add(timesStatusLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
            .add(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jLabel9, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 678, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jPanel3Layout.createSequentialGroup()
                        .add(jLabel2)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(timeFormatCB, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 353, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(timeFormatFieldsComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 167, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(timeFormatToggleButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(guessTimeFormatToggleButton)))
                .addContainerGap(45, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel3Layout.createSequentialGroup()
                .add(6, 6, 6)
                .add(jLabel9, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(timeFormatFieldsComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(timeFormatToggleButton)
                    .add(jLabel2)
                    .add(guessTimeFormatToggleButton)
                    .add(timeFormatCB, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(timesStatusLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 15, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
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

        org.jdesktop.layout.GroupLayout dataPanelLayout = new org.jdesktop.layout.GroupLayout(dataPanel);
        dataPanel.setLayout(dataPanelLayout);
        dataPanelLayout.setHorizontalGroup(
            dataPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(dataPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(dataPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(dataPanelLayout.createSequentialGroup()
                        .add(dataPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                            .add(jLabel3)
                            .add(jLabel4))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(dataPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                            .add(dep0Columns, 0, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .add(columnsComboBox, 0, 166, Short.MAX_VALUE))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(dataPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                            .add(dataPanelLayout.createSequentialGroup()
                                .add(bundleCheckBox)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(jToggleButton2))
                            .add(dataPanelLayout.createSequentialGroup()
                                .add(dep0timeCheckBox)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .add(jToggleButton3)
                                .add(1, 1, 1)))
                        .add(jSeparator1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(dataPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(dataPanelLayout.createSequentialGroup()
                                .add(4, 4, 4)
                                .add(jLabel6))
                            .add(dataPanelLayout.createSequentialGroup()
                                .add(5, 5, 5)
                                .add(jLabel7)))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(dataPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(validMinTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 94, Short.MAX_VALUE)
                            .add(fillValueTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 116, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(dataPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(dataPanelLayout.createSequentialGroup()
                                .add(jToggleButton4)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(guessFillButton))
                            .add(dataPanelLayout.createSequentialGroup()
                                .add(jLabel8)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(validMaxTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 87, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 28, Short.MAX_VALUE))
                    .add(dataStatusLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        dataPanelLayout.linkSize(new java.awt.Component[] {validMaxTextField, validMinTextField}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        dataPanelLayout.setVerticalGroup(
            dataPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(dataPanelLayout.createSequentialGroup()
                .add(dataPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(dataPanelLayout.createSequentialGroup()
                        .add(dataPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(columnsComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(jLabel3))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(dataPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(dep0Columns, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(jLabel4)))
                    .add(dataPanelLayout.createSequentialGroup()
                        .add(dataPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(jLabel6)
                            .add(jToggleButton4)
                            .add(guessFillButton)
                            .add(fillValueTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(dataPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(jLabel7)
                            .add(jLabel8)
                            .add(validMinTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(validMaxTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                    .add(dataPanelLayout.createSequentialGroup()
                        .add(6, 6, 6)
                        .add(jSeparator1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 55, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(dataPanelLayout.createSequentialGroup()
                        .add(dataPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(jToggleButton2)
                            .add(bundleCheckBox))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(dataPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(dep0timeCheckBox)
                            .add(jToggleButton3))))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(dataStatusLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("data", dataPanel);

        jLabel10.setText("Title:");
        jLabel10.setToolTipText("Title for the data.");

        jLabel11.setText("Label:");
        jLabel11.setToolTipText("Short label for the data");

        titleTextField.setText(" ");

        jLabel12.setText("Units:");
        jLabel12.setToolTipText("units for the data");

        jLabel13.setText("Depend Units:");
        jLabel13.setToolTipText("Units of the depend0 column (typically x-axis), such as \"seconds\" or \"nanoseconds since 2000-01-01T12:00\"  Droplist shows examples.");

        depend0unitsCB.setEditable(true);
        depend0unitsCB.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "seconds since 2010-01-01T00:00", "nanoseconds since 2000-01-01T12:00", "days since 1999-12-31T00:00", "seconds", "hr", "mjd", " " }));
        depend0unitsCB.setToolTipText("units used to interpret the x tags (depends on)");

        org.jdesktop.layout.GroupLayout jPanel4Layout = new org.jdesktop.layout.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel4Layout.createSequentialGroup()
                        .add(jLabel10)
                        .add(18, 18, 18)
                        .add(titleTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 326, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(jPanel4Layout.createSequentialGroup()
                        .add(jLabel11)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(labelTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 116, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .add(36, 36, 36)
                .add(jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel4Layout.createSequentialGroup()
                        .add(jLabel13)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(depend0unitsCB, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(jPanel4Layout.createSequentialGroup()
                        .add(jLabel12)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(unitsTF, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 150, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel4Layout.createSequentialGroup()
                .add(6, 6, 6)
                .add(jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel10)
                    .add(titleTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel12)
                    .add(unitsTF, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                        .add(jLabel11)
                        .add(labelTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                        .add(jLabel13)
                        .add(depend0unitsCB, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("labels", jPanel4);

        whereCB.setText("Only load data where:");

        whereParamList.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, whereCB, org.jdesktop.beansbinding.ELProperty.create("${selected}"), whereParamList, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        whereParamList.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                whereParamListActionPerformed(evt);
            }
        });

        whereOp.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { ".eq", ".ne", ".ge", ".gt", ".le", ".lt", ".within", ".matches" }));

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, whereCB, org.jdesktop.beansbinding.ELProperty.create("${selected}"), whereOp, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        whereValueCB.setEditable(true);

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, whereCB, org.jdesktop.beansbinding.ELProperty.create("${selected}"), whereValueCB, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        org.jdesktop.layout.GroupLayout jPanel5Layout = new org.jdesktop.layout.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel5Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(whereParamList, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 145, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(whereCB))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(whereOp, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(whereValueCB, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(410, Short.MAX_VALUE))
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .add(whereCB)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel5Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(whereParamList, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(whereOp, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(whereValueCB, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(28, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("subset", jPanel5);

        xComboBox.setEditable(true);
        xComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        xCheckBox.setText("X:");

        yCheckBox.setText("Y:");

        yComboBox.setEditable(true);
        yComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        zCheckBox.setText("Z:");

        zComboBox.setEditable(true);
        zComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        jLabel15.setText("Select the columns which will be displayed as X, Y, and Z (typically color) coordinates.");

        org.jdesktop.layout.GroupLayout xyzPanelLayout = new org.jdesktop.layout.GroupLayout(xyzPanel);
        xyzPanel.setLayout(xyzPanelLayout);
        xyzPanelLayout.setHorizontalGroup(
            xyzPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(xyzPanelLayout.createSequentialGroup()
                .add(xyzPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(xyzPanelLayout.createSequentialGroup()
                        .add(12, 12, 12)
                        .add(xCheckBox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(xComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 134, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(18, 18, 18)
                        .add(yCheckBox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(yComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(18, 18, 18)
                        .add(zCheckBox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(zComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(xyzPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .add(jLabel15)))
                .addContainerGap(239, Short.MAX_VALUE))
        );

        xyzPanelLayout.linkSize(new java.awt.Component[] {xComboBox, yComboBox, zComboBox}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        xyzPanelLayout.setVerticalGroup(
            xyzPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(xyzPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(jLabel15)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(xyzPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(xComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(xCheckBox)
                    .add(yCheckBox)
                    .add(yComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(zCheckBox)
                    .add(zComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(28, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("xyz", xyzPanel);

        jTable1.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        jScrollPane1.setViewportView(jTable1);

        org.jdesktop.layout.GroupLayout jLayeredPane1Layout = new org.jdesktop.layout.GroupLayout(jLayeredPane1);
        jLayeredPane1.setLayout(jLayeredPane1Layout);
        jLayeredPane1Layout.setHorizontalGroup(
            jLayeredPane1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 100, Short.MAX_VALUE)
        );
        jLayeredPane1Layout.setVerticalGroup(
            jLayeredPane1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 100, Short.MAX_VALUE)
        );

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jScrollPane1)
            .add(jTabbedPane1)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                .add(layout.createSequentialGroup()
                    .add(0, 341, Short.MAX_VALUE)
                    .add(jLayeredPane1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(0, 341, Short.MAX_VALUE)))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(jTabbedPane1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 112, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 354, Short.MAX_VALUE))
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                .add(layout.createSequentialGroup()
                    .add(0, 0, Short.MAX_VALUE)
                    .add(jLayeredPane1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(0, 0, Short.MAX_VALUE)))
        );

        bindingGroup.bind();
    }// </editor-fold>//GEN-END:initComponents

    private void whereParamListActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_whereParamListActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_whereParamListActionPerformed

    private void guessFillButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_guessFillButtonActionPerformed
        fillValueTextField.setText(MSG_MOMENT);
        guessFillButton.setEnabled(false);
        dataStatusLabel.setText("looking for outliers which could be interpretted as fill.");
        Runnable run= new Runnable() {
            @Override
            public void run() {
                DasProgressLabel mon= null;
                String resultText=null;
                try {
                    DataSetBuilder builder= new DataSetBuilder( 1, 100 );

                    int[] cols= getDataColumns();

                    mon= new DasProgressLabel("looking for outliers which could be interpretted as fill");
                    mon.setLabelComponent(dataStatusLabel);
                    //            mon= DasProgressPanel.createFramed("parsing file");
                    
                    final int rowCount = model.getRowCount();
                    mon.setTaskSize(rowCount);
                    mon.started();
                    for ( int i=0; i<rowCount; i++ ) {
                        mon.setTaskProgress(i);
                        for ( int j=cols[0]; j<=cols[1]; j++ ) {
                            try {
                                builder.putValue(-1, Double.parseDouble(String.valueOf(model.getValueAt(i,j))));
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
                        resultText= String.valueOf(outliers.keySet().iterator().next());
                    } else {
                        resultText= "";
                    }
                } catch (IllegalArgumentException ex) {
                    resultText= "";
                    throw ex;
                } finally {
                    if ( mon!=null ) {
                        mon.finished();
                        mon.setLabelComponent(null);
                    }
                    final String fresultText= resultText;
                    Runnable run= new Runnable() {
                        @Override
                        public void run() {
                            fillValueTextField.setText( fresultText );
                            dataStatusLabel.setText("                    ");
                            guessFillButton.setEnabled(true);
                        }
                    };
                    SwingUtilities.invokeLater(run);
                }
            }
        };
        new Thread(run).start();
    }//GEN-LAST:event_guessFillButtonActionPerformed
    private static final String MSG_MOMENT = "moment...";

    private void validMaxTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_validMaxTextFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_validMaxTextFieldActionPerformed

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

    private void columnsComboBoxFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_columnsComboBoxFocusGained
        focusDep0 = false;
    }//GEN-LAST:event_columnsComboBoxFocusGained

    private void columnsComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_columnsComboBoxItemStateChanged
        setColumn((String) columnsComboBox.getSelectedItem());
    }//GEN-LAST:event_columnsComboBoxItemStateChanged

    private void dep0ColumnsFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_dep0ColumnsFocusGained
        focusDep0 = true;
    }//GEN-LAST:event_dep0ColumnsFocusGained

    private void dep0ColumnsItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_dep0ColumnsItemStateChanged
        setDep0((String) dep0Columns.getSelectedItem());
    }//GEN-LAST:event_dep0ColumnsItemStateChanged

    private void guessTimeFormatToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_guessTimeFormatToggleButtonActionPerformed
        if ( jTable1.getSelectedColumn()==-1 ) {
            timesStatusLabel.setText("select time columns, and the time format is guessed (4 digits implies $Y, 3 implies $j, etc)");
        }   else {
            int[] jj= jTable1.getSelectedColumns();
            int imin= Integer.MAX_VALUE;
            int imax= Integer.MIN_VALUE;
            for ( int j: jj ) {
                if ( j<imin ) imin=j;
                if ( j>imax ) imax=j;
            }
            timesStatusLabel.setText("selected time columns ("+imin+" though "+imax+") were used to infer time");
        }
    }//GEN-LAST:event_guessTimeFormatToggleButtonActionPerformed

    private void timeFormatToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeFormatToggleButtonActionPerformed
        if ( jTable1.getSelectedColumn()==-1 ) {
            timesStatusLabel.setText("select by dragging the data columns that together form the time");
        }   else {
            int[] jj= jTable1.getSelectedColumns();
            int imin= Integer.MAX_VALUE;
            int imax= Integer.MIN_VALUE;
            for ( int j: jj ) {
                if ( j<imin ) imin=j;
                if ( j>imax ) imax=j;
            }
            timesStatusLabel.setText("selected time columns ("+imin+" though "+imax+") were copied to format");
        }
    }//GEN-LAST:event_timeFormatToggleButtonActionPerformed

    private void timeFormatFieldsComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeFormatFieldsComboBoxActionPerformed
        String s= (String) timeFormatFieldsComboBox.getSelectedItem();
        int i= s.indexOf(":");
        String insert= s.substring(0,i);
        int i0= ((JTextField)timeFormatCB.getEditor().getEditorComponent()).getCaret().getDot();
        int i1= ((JTextField)timeFormatCB.getEditor().getEditorComponent()).getCaret().getMark();
        if ( i1<i0 ) {
            int t= i0;
            i0= i1;
            i1= t;
        }
        String text= timeFormatCB.getSelectedItem().toString();
        String n= text.substring(0,i0) + insert + text.substring(i1);
        timeFormatCB.setSelectedItem(n);
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

    private void jToggleButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButton1ActionPerformed
        headerLabel.setText("select the first column of data or column headings");
    }//GEN-LAST:event_jToggleButton1ActionPerformed

    private void skipLinesTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_skipLinesTextFieldFocusLost

    }//GEN-LAST:event_skipLinesTextFieldFocusLost

    private void skipLinesTextFieldFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_skipLinesTextFieldFocusGained

    }//GEN-LAST:event_skipLinesTextFieldFocusGained

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

private boolean isIso8601TimeField0() {
    int i= getSkipLines();
    int nl= this.jTable1.getRowCount();
    String text1= String.valueOf( this.jTable1.getValueAt( i,0 ) );
    String text2= String.valueOf( this.jTable1.getValueAt( nl>(i+1) ? (i+1) : i, 0 ) );
    return !text1.equals(text2) && TimeParser.isIso8601String(text1) && TimeParser.isIso8601String(text2);
}

/**
 * This scans through a column, looking for the maximum value and the number
 * of digits.  Given the current digits it has already then guesses what the format
 * would be for this column. For example, if it already knows the year and finds
 * the column has three-digit numbers varying from 0 to 365, it will guess that this 
 * column is $j (day of year).  This guess is added to the template and the new
 * current digit is returned.
 * @param example used to get delimiters
 * @param column the table column
 * @param current TimeUtil.YEAR, etc.
 * @param template the template where we add $Y etc.
 * @return the new digit we are looking for.
 */
private int guessTimeFormatColumn( String example, int column, int current, StringBuilder template ) {
    int i= getSkipLines();
    int nl= this.jTable1.getRowCount();
    int step=1;
    
    if ( nl>10000 ) nl=10000;
    
    int digits=0;
    int max=-999999999;
    int min=999999999;
    for ( ; i<nl; i+=step ) {
        String s= String.valueOf( this.jTable1.getValueAt( i, column ) );
        int slen= s.length();
        boolean isNumber= false;
        try {
            int value= (int)Double.parseDouble(s);
            isNumber= true;
            if ( value>max ) max= value;
            if ( value<min ) min= value;
        } catch ( NumberFormatException ex ) {
        }
        if ( this.model.isRecord( i ) ) {
            if ( slen>digits ) {
                if ( isNumber ) digits= slen;
            }
        }
    }
    switch (current) {
        case TimeUtil.YEAR:
            switch (digits) {
                case 2:
                    template.append("$y");
                    return TimeUtil.MONTH;
                case 4:
                    template.append("$Y");
                    return TimeUtil.MONTH;
                case 5:
                    template.append("$Y$j");
                    return TimeUtil.HOUR;
                case 6:
                    template.append("$y$m$d");
                    return TimeUtil.HOUR;
                case 8:
                    if ( !Character.isDigit( example.charAt(4) ) ) {
                        template.append("$Y").append(example.charAt(4)).append("$j");
                        return TimeUtil.HOUR;
                    } else {
                        template.append("$Y$m$d");
                        return TimeUtil.HOUR;
                    }
                case 10:
                    if ( !Character.isDigit( example.charAt(4) ) ) {
                        template.append("$Y").append(example.charAt(4)).append("$m").append(example.charAt(7)).append("$d");
                        return TimeUtil.HOUR;
                    } else {
                        template.append("$X");
                        return current;
                    }
                default:
                    template.append("$X");
                    return current;
            }
        case TimeUtil.MONTH:
            if ( min==999999999 ) {
                template.append("$b");
                return TimeUtil.DAY;
            } else if ( max<=12 ) {
                template.append("$m");
                return TimeUtil.DAY;
            } else if ( max<=366 ) {
                template.append("$j");
                return TimeUtil.HOUR;
            } else {
                template.append("$x");
                return current;
            }
        case TimeUtil.DAY:
            if ( max<=31 ) {
                template.append("$d");
                return TimeUtil.HOUR;
            } else {
                template.append("$x");
                return current;
            }
        case TimeUtil.HOUR:
            if ( digits<3 && max<25 ) {
                template.append("$H");
                return TimeUtil.MINUTE;
            } else if ( digits==4 ) {
                template.append("$H$M");
                return TimeUtil.SECOND;
            } else if ( digits==5 && !Character.isDigit(example.charAt(2)) ) {
                template.append("$H").append(example.charAt(2)).append("$M");
                return TimeUtil.SECOND;
            } else if ( digits==6 ) {
                template.append("$H$M$S");
                return TimeUtil.MILLI;
            } else if ( digits==7 && !Character.isDigit(example.charAt(2)) ) {
                template.append("$H").append(example.charAt(2)).append("$M").append(example.charAt(5)).append("$S");
                return TimeUtil.MILLI;
            } else if ( digits==8 && !Character.isDigit(example.charAt(2)) ) {
                template.append("$H").append(example.charAt(2)).append("$M").append(example.charAt(5)).append("$S");
                return TimeUtil.MILLI;
            }       
            break;
        case TimeUtil.MINUTE:
            template.append("$M");
            return TimeUtil.SECOND;
        case TimeUtil.SECOND:
            template.append("$S");
            return TimeUtil.MILLI;
        case TimeUtil.MILLI:
            template.append("$(milli)");
            return TimeUtil.MICRO;
        default:
            break;
    }
    template.append("$X");
    return current;
}

private void guessTimeFormatButtonAP( int row, int first, int last ) {
    int curr= TimeUtil.YEAR;
    StringBuilder template= new StringBuilder();
    if ( first==last ) {
        String example= String.valueOf( jTable1.getValueAt(row,first) );
        if ( TimeParser.isIso8601String(example) ) {
           timeFormatCB.setSelectedItem("ISO8601");
           return; 
        } else {
            logger.fine( "time does not appear to be ISO8601" );
        }
    }
    for ( int i=first; i<=last; i++ ) {
        String example= String.valueOf( jTable1.getValueAt(row,i) );
        curr= guessTimeFormatColumn( example, i, curr, template );
        if ( i<last ) {
            template.append("+");
        }
    }
    timeFormatCB.setSelectedItem(template.toString());
}


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
        Map<Integer, String> result = new LinkedHashMap<>();
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
    protected String column = "";
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
        if ( initializing==false ) {
            this.labelTextField.setText("");
            this.titleTextField.setText("");
            this.unitsTF.setText("");
            this.depend0unitsCB.setSelectedItem("");
        }
        firePropertyChange(PROP_COLUMN, oldColumn, column);
    }
    protected String dep0 = "";
    public static final String PROP_DEP0 = "dep0";

    public String getDep0() {
        int i= dep0.indexOf(": "); // allow DEPEND_0 to contain : label for consistency.
        if ( i>-1 ) {
            return dep0.substring(0,i);
        } else {
            return dep0;
        }
    }

    public void setDep0(String dep0) {
        String oldDep0 = this.dep0;
        this.dep0 = dep0;
        firePropertyChange(PROP_DEP0, oldDep0, dep0);
    }

    @Override
    public JPanel getPanel() {
        return this;
    }

    @Override
    public boolean reject( String url ) throws IOException, URISyntaxException {
        split = URISplit.parse(url);
        FileSystem fs = FileSystem.create( DataSetURI.toUri(split.path) );
        return fs.isDirectory( split.file.substring(split.path.length()) );
    }

    @Override
    public boolean prepare( String url, java.awt.Window parent, ProgressMonitor mon) throws Exception {
        split = URISplit.parse(url);
        DataSetURI.getFile( DataSetURI.toUri(split.file), mon );
        return true;
    }

    @Override
    public void setURI(String url) {
        try {
            split = URISplit.parse(url);
            params = URISplit.parseParams(split.params);

            File f = DataSetURI.getFile( DataSetURI.toUri(split.file), new NullProgressMonitor() );
            setFile(f);

            Component selectedTab= dataPanel;
                    
            if (params.containsKey("skipLines")) {
                setSkipLines(Integer.parseInt(params.get("skipLines")));
            }
            if (params.containsKey("skip")) {
                setSkipLines(Integer.parseInt(params.get("skip")));
            }

            if (params.containsKey("column")) {
                setColumn(params.get("column"));
            }
            if ( params.containsKey("arg_0") ) {
                setColumn(params.get("arg_0"));
            }
            if (params.containsKey("rank2")) {
                setColumn(params.get("rank2"));
            }
            if (params.containsKey("bundle")) {
                setColumn(params.get("bundle"));
                bundleCheckBox.setSelected(true);
            }
            if (params.containsKey("title")) {
                titleTextField.setText(params.get("title"));
            }
            if (params.containsKey("label")) {
                labelTextField.setText(params.get("label"));
            }
            if ( params.containsKey("units") ) {
                unitsTF.setText(params.get("units"));
            }
            if ( params.containsKey("depend0Units") ) {
                String depend0Units= params.get("depend0Units");
                depend0Units= depend0Units.replaceAll("\\+", " " );
                depend0unitsCB.setSelectedItem(depend0Units);
            } else {
                depend0unitsCB.setSelectedItem("");
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
            if ( params.containsKey("depend1Values") ) {
                dep1Values.setSelectedItem(params.get("depend1Values"));
            } else {
                dep1Values.setSelectedItem("");
            }

            if (params.containsKey("depend0")) {
                setDep0(params.get("depend0"));
            }

            if (params.containsKey("time")) {
                setDep0(params.get("time"));
                dep0timeCheckBox.setSelected(true);
            }
            if ( params.containsKey("X") ) {
                xCheckBox.setSelected(true);
                xComboBox.setSelectedItem(params.get("X"));
                selectedTab= xyzPanel;
            } else {
                xComboBox.setSelectedItem("");
            }
            if ( params.containsKey("Y") ) {
                yCheckBox.setSelected(true);
                yComboBox.setSelectedItem(params.get("Y"));
            } else {
                yComboBox.setSelectedItem("");
            }
            if ( params.containsKey("Z") ) {
                zCheckBox.setSelected(true);
                zComboBox.setSelectedItem(params.get("Z"));
            } else {
                zComboBox.setSelectedItem("");
            }
            
            if ( params.containsKey("timeFormat") ) {
                timeFormatCB.setSelectedItem(params.get("timeFormat"));
            }

            fillValueTextField.setText( getParam(params, "fill" ));
            validMinTextField.setText( getParam(params, "validMin" ));
            validMaxTextField.setText( getParam(params, "validMax" ));
            
            jTabbedPane1.setSelectedComponent(selectedTab);
            update();
            checkHeaders();

            if ( columns!=null ) {
                whereParamList.setModel( new DefaultComboBoxModel( columns.values().toArray() ) );
                String where= getParam( params, "where" );
                if ( where!=null && where.length()>0 ) {
                    whereCB.setSelected(true);
                    int i= where.indexOf(".");
                    if ( i>-1 ) {
                        whereParamList.setSelectedItem(where.substring(0,i)); 
                        int i0= where.indexOf("(");
                        int i1= where.indexOf(")",i0);
                        whereOp.setSelectedItem(where.substring(i,i0));
                        whereValueCB.setSelectedItem( where.substring(i0+1,i1).replaceAll("\\+"," "));
                    }
                } else {
                    whereCB.setSelected(false);
                }
            } else {
                whereParamList.setModel( new DefaultComboBoxModel( new String[] { "not available" } ) );
                whereCB.setSelected(false);
            }
            initializing= false;

        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
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

    @Override
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
        params.remove("column");

        if (getColumn().contains(":") || getColumn().contains("-")) {
            if ( bundleCheckBox.isSelected() ) {
                params.put("bundle", getColumn());
                params.remove("rank2");
            } else {
                params.put("rank2", getColumn());
                params.remove("bundle");
            }
        } else {
            if ( isIso8601TimeField0() ) {
                if ( this.getDep0().equals("") || this.getDep0().equals("field0") ) {
                    params.put("arg_0", getColumn());
                    params.remove("depend0");
                } else {
                    params.put("column", getColumn());
                }
            } else {
                if ( false && isRichHeader ) { // This doesn't work because reject expects column https://sourceforge.net/p/autoplot/bugs/1490/
                    params.put( URISplit.PARAM_ARG_0, getColumn());
                } else {
                    String s= getColumn();
                    if ( s.trim().length()>0 ) {
                        params.put("column", s);
                    }
                }
            }
            params.remove("rank2");
            params.remove("bundle");
        }
        params.remove("group");
        
        String s= dep1Values.getSelectedItem()==null ? "" : (String)dep1Values.getSelectedItem();
        if ( s.length()>0 ) {
            setParam( params, "depend1Values", s );
        } else {
            params.remove("depend1Values");
        }

        if ( xCheckBox.isSelected() ) {
            setParam( params, "X", xComboBox.getSelectedItem().toString() );
        } else {
            params.remove("X");
        }
        if ( yCheckBox.isSelected() ) {
            setParam( params, "Y", yComboBox.getSelectedItem().toString() );
        } else {
            params.remove("Y");
        }
        if ( zCheckBox.isSelected() ) {
            setParam( params, "Z", zComboBox.getSelectedItem().toString() );
        } else {
            params.remove("Z");
        }
        
        setParam( params, "title", titleTextField.getText() );
        setParam( params, "label", labelTextField.getText() );
        setParam( params, "units", unitsTF.getText() );
        if ( depend0unitsCB.getSelectedItem()!=null && depend0unitsCB.getSelectedItem().toString().trim().length()>0 ) {
            String depend0Units= depend0unitsCB.getSelectedItem().toString();
            depend0Units= depend0Units.replaceAll(" ", "+");
            setParam( params, "depend0Units", depend0Units );
        } else {
            params.remove("depend0Units");
        }
        if ( timeFormatCB.getSelectedItem()!=null ) {
            if ( !timeFormatCB.getSelectedItem().toString().equals("ISO8601") ) {
                setParam( params, "timeFormat", timeFormatCB.getSelectedItem().toString() );
            } else {
                params.remove("timeFormat");
            }
        } else {
            params.remove("timeFormat");
        }
        String s2= fillValueTextField.getText().trim();
        if ( !s2.equals(MSG_MOMENT) && s2.length()>0 ) {
            setParam( params, "fill", s );
        }
        setParam( params, "validMin", validMinTextField.getText() );
        setParam( params, "validMax", validMaxTextField.getText() );

        if ( whereCB.isSelected() ) {
            setParam( params, "where", String.format( "%s%s(%s)", whereParamList.getSelectedItem(), whereOp.getSelectedItem(), whereValueCB.getSelectedItem().toString().replaceAll(" ","+") ) );
        } else {
            setParam( params, "where", "" );
        }
        
        split.params = URISplit.formatParams(params);
        if ( split.params!=null && split.params.length()==0 ) split.params= null; //https://sourceforge.net/p/autoplot/bugs/1913/

        return URISplit.format(split);

    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    public javax.swing.JCheckBox bundleCheckBox;
    public javax.swing.JComboBox columnsComboBox;
    public javax.swing.JComboBox commentComboBox;
    public javax.swing.JPanel dataPanel;
    public javax.swing.JLabel dataStatusLabel;
    public javax.swing.JComboBox dep0Columns;
    public javax.swing.JCheckBox dep0timeCheckBox;
    public javax.swing.JComboBox dep1Values;
    public javax.swing.JComboBox<String> depend0unitsCB;
    public javax.swing.JTextField fillValueTextField;
    public javax.swing.JButton guessFillButton;
    public javax.swing.JToggleButton guessTimeFormatToggleButton;
    public javax.swing.JLabel headerLabel;
    public javax.swing.JLabel jLabel1;
    public javax.swing.JLabel jLabel10;
    public javax.swing.JLabel jLabel11;
    public javax.swing.JLabel jLabel12;
    public javax.swing.JLabel jLabel13;
    public javax.swing.JLabel jLabel14;
    public javax.swing.JLabel jLabel15;
    public javax.swing.JLabel jLabel2;
    public javax.swing.JLabel jLabel3;
    public javax.swing.JLabel jLabel4;
    public javax.swing.JLabel jLabel5;
    public javax.swing.JLabel jLabel6;
    public javax.swing.JLabel jLabel7;
    public javax.swing.JLabel jLabel8;
    public javax.swing.JLabel jLabel9;
    public javax.swing.JLayeredPane jLayeredPane1;
    public javax.swing.JPanel jPanel2;
    public javax.swing.JPanel jPanel3;
    public javax.swing.JPanel jPanel4;
    public javax.swing.JPanel jPanel5;
    public javax.swing.JScrollPane jScrollPane1;
    public javax.swing.JSeparator jSeparator1;
    public javax.swing.JTabbedPane jTabbedPane1;
    public javax.swing.JTable jTable1;
    public javax.swing.JToggleButton jToggleButton1;
    public javax.swing.JToggleButton jToggleButton2;
    public javax.swing.JToggleButton jToggleButton3;
    public javax.swing.JToggleButton jToggleButton4;
    public javax.swing.JToggleButton jToggleButton5;
    public javax.swing.JTextField labelTextField;
    public javax.swing.JFormattedTextField skipLinesTextField;
    public javax.swing.JComboBox<String> timeFormatCB;
    public javax.swing.JComboBox timeFormatFieldsComboBox;
    public javax.swing.JToggleButton timeFormatToggleButton;
    public javax.swing.JLabel timesStatusLabel;
    public javax.swing.JTextField titleTextField;
    public javax.swing.JTextField unitsTF;
    public javax.swing.JTextField validMaxTextField;
    public javax.swing.JTextField validMinTextField;
    public javax.swing.JCheckBox whereCB;
    public javax.swing.JComboBox<String> whereOp;
    public javax.swing.JComboBox<String> whereParamList;
    public javax.swing.JComboBox<String> whereValueCB;
    public javax.swing.JCheckBox xCheckBox;
    public javax.swing.JComboBox<String> xComboBox;
    public javax.swing.JPanel xyzPanel;
    public javax.swing.JCheckBox yCheckBox;
    public javax.swing.JComboBox<String> yComboBox;
    public javax.swing.JCheckBox zCheckBox;
    public javax.swing.JComboBox<String> zComboBox;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration//GEN-END:variables

    private static void updateColumns( javax.swing.JTable jTable1, Map<Integer,String> columns ) {
        int n= jTable1.getColumnCount();
        int wide= n<5 ? 210 : 170;
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
            } else if ( s.length()<5 ) {
                jTable1.getColumnModel().getColumn(i).setPreferredWidth(narrow); 
            } else {
                jTable1.getColumnModel().getColumn(i).setPreferredWidth(norm);
            }
        }
        jTable1.getTableHeader().repaint();

    }

    /**
     * check for JSON Rich ASCII header.
     */
    private void checkHeaders() {
        try {
            AsciiParser.DelimParser p = parser.guessSkipAndDelimParser(file.toString());
            if (p == null) {
                //               throw new IllegalArgumentException("no records found");
                return;
            }
            model.setRecParser(p);
            columns = getColumnNames();
            Exception richHeaderWarn = null;
            isRichHeader = AsciiParser.isRichHeader(p.header);
            if (isRichHeader) {
                try {
                    String[] columns1=  new String[p.fieldCount()];
                    for ( int i=0; i<columns1.length; i++ )  columns1[i]="";
                    AsciiHeadersParser.parseMetadata(p.header,columns1,columns1);
                } catch (ParseException | IllegalArgumentException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
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
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    private void update() {
        try {
            AsciiParser.DelimParser p = parser.guessSkipAndDelimParser(file.toString());
            if ( p == null) {
                p= new AsciiParser().getDelimParser( 2, "\\s+" );
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

            updateColumns(jTable1,columns);

            String lcol = getColumn();
            int icol = jTable1.getSelectedColumn();
            columnsComboBox.setModel(new DefaultComboBoxModel(list.values().toArray()));
            columnsComboBox.setSelectedItem(getColumn());
            if (icol != -1) {
                setColumn(columns.get(icol));
            } else {
                setColumn(lcol);
            }

            String depend1Values= dep1Values.getSelectedItem()==null ? "" : dep1Values.getSelectedItem().toString();
            dep1Values.setModel(new DefaultComboBoxModel( new String[] { "", "field1-field"+(columns.size()-1) } ) );
            dep1Values.setSelectedItem(depend1Values);
            
            List<String> dep0Values = new ArrayList<>(list.values());
            String ldep0 = getDep0();
            dep0Values.add(0, "");
            dep0Columns.setModel(new DefaultComboBoxModel(dep0Values.toArray()));
            dep0Columns.setSelectedItem(ldep0);

            Object s;
            s= xComboBox.getSelectedItem();
            xComboBox.setModel( new DefaultComboBoxModel(list.values().toArray()) );
            xComboBox.setSelectedItem(s);
            
            s= yComboBox.getSelectedItem();
            yComboBox.setModel( new DefaultComboBoxModel(list.values().toArray()) );
            yComboBox.setSelectedItem(s);
            
            s= zComboBox.getSelectedItem();
            zComboBox.setModel( new DefaultComboBoxModel(list.values().toArray()) );
            zComboBox.setSelectedItem(s);
            
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
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }
}
