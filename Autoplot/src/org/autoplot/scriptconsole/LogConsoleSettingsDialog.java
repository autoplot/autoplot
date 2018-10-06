
 /*
 * LogConsoleSettingsDialog.java
 *
 * Created on May 19, 2009, 7:32:38 AM
 */
package org.autoplot.scriptconsole;

import ZoeloeSoft.projects.JFontChooser.JFontChooser;
import java.awt.Color;
import java.awt.Component;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EventObject;
import java.util.HashSet;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.EventListenerList;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import org.das2.datum.LoggerManager;
import org.autoplot.AutoplotUtil;
import org.autoplot.datasource.AutoplotSettings;

/**
 * Settings GUI for the Log Console dialog. The log console is more complex than
 * it first seems, in that it is actually receiving messages from loggers, not
 * just stdout and stderr. (See
 * http://docs.oracle.com/javase/1.4.2/docs/guide/util/logging/overview.html,
 * but in short messages are sent to hierarchical named channels with verbosity
 * levels.)
 *
 * @author jbf
 */
public final class LogConsoleSettingsDialog extends javax.swing.JDialog {

    LogConsole console;

    /**
     * Just a list of all the log levels defined in {@link Level}
     */
    private static final Level[] LOG_LEVELS = { null,
        Level.OFF,
        Level.SEVERE,
        Level.WARNING,
        Level.INFO,
        Level.CONFIG,
        Level.FINE,
        Level.FINER,
        Level.FINEST,
        Level.ALL,
        };

    private class LogLevelComboBoxModel implements ComboBoxModel {

        private Logger logger;

        private LogLevelComboBoxModel(Logger logger) {
            if (logger == null) {
                throw new NullPointerException("logger must be non-null");
            }
            this.logger = logger;
        }

        @Override
        public void setSelectedItem(Object anItem) {
            Level level = (Level) anItem;
            logger.setLevel(level);
            //boolean invisible = true;
            StringBuilder err = new StringBuilder();
            for (Handler h : logger.getHandlers()) {
                if (level != null && h.getLevel().intValue() > level.intValue()) {
                    err.append("handler filters data: ").append(h).append("\n");
                }// else {
                 //   invisible = false;
                //}
            }
            //if (invisible) {//TODO: check parents
                //err.append( String.format( "No handlers (of %d) will show this log level: %s", logger.getHandlers().length, level ) );
            //}
            if (err.length() > 0) {
                AutoplotUtil.showMessageDialog(LogConsoleSettingsDialog.this,
                        err.toString(), "Misconfigured Logger", JOptionPane.OK_OPTION);
            }
        }

        @Override
        public Object getSelectedItem() {
            return logger.getLevel();
        }

        @Override
        public int getSize() {
            return LOG_LEVELS.length;
        }

        @Override
        public Object getElementAt(int index) {
            return LOG_LEVELS[index];
        }

        @Override
        public void addListDataListener(javax.swing.event.ListDataListener l) {
            //No need to implement for a static model
        }

        @Override
        public void removeListDataListener(javax.swing.event.ListDataListener l) {
            //No need to implement for a static model
        }
    }

    private static class LogLevelCellRenderer implements ListCellRenderer {

        private final Logger logger;
        private final ListCellRenderer delegate;
        private JComponent component;

        private LogLevelCellRenderer(ListCellRenderer delegate, Logger logger) {
            this.delegate = delegate;
            this.logger = logger;
            if (delegate instanceof JComponent) {
                this.component = (JComponent) delegate;
            }
        }

        @Override
        public Component getListCellRendererComponent(
                JList list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            //We just need to handle null as a special case
            if (value == null) {
                Logger anscestor = logger;
                if (logger.getParent() == null) { // root logger.
                    value = logger.getLevel();
                } else {
                    do {
                        anscestor = anscestor.getParent();
                        if (anscestor == null) {
                            value = "NULL"; // I don't think this happens...
                        } else {
                            value = anscestor.getLevel();
                        }
                    } while (value == null);
                    value = "INHERITED(" + value + ")";

                    if (component != null) {
                        String name = anscestor.getName();
                        if (name.equals("")) {
                            name = "<anonymous>";
                        }
                        component.setToolTipText("inherited from " + name);
                    }
                }
            } else {
                ((JComponent) delegate).setToolTipText(null);
            }
            return delegate.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            }

    }

    /**
     * Creates new form LogConsoleSettingsDialog
     * @param parent the dialog parent, used to position the GUI.
     * @param modal specifies whether dialog blocks user input to other top-level windows when shown. If true, the modality type property is set to DEFAULT_MODALITY_TYPE, otherwise the dialog is modeless.
     * @param console the log console for viewing the loggers.
     */
    public LogConsoleSettingsDialog(java.awt.Frame parent, boolean modal, LogConsole console) {
        super(parent, modal);
        setTitle("Log Console Settings");
        initComponents();
        setLocationRelativeTo(parent);

        initLogTable();

        this.console = console;

        searchForTextField.setText(console.getSearchText());
        timeStampsCheckBox.setSelected(console.showTimeStamps);
        logLevelCheckBox.setSelected(console.showLevel);
        loggerIDCheckBox.setSelected(console.showLoggerId);
        threadsCB.setSelected(console.showThreads);
        showOnlyHighlitedCB.setSelected(console.isShowOnlyHighlited());
    }

    /**
     * A logger can have a null level, meaning its parent's level should be used.
     * @param anscestor the logger.
     * @return the Level.
     */
    private Level getLoggerMindingInheritance(Logger anscestor) {
        Level value = anscestor.getLevel();

        while (value == null) {
            anscestor = anscestor.getParent();
            value = anscestor.getLevel();
        }

        return value;
    }

        
    static class TimeTaggingCellRenderer implements TableCellRenderer {

        //JComponent component = null;
        //TableCellRenderer delegate;

        public TimeTaggingCellRenderer(TableCellRenderer delegate) {
            //this.component = null;
            //this.delegate = delegate;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object lvalue, boolean isSelected, boolean hasFocus, int row, int column) {
            Logger logger = (Logger) lvalue;
            long time;
            if ( logger instanceof org.das2.util.LoggerManager.TimeTaggingLogger ) {
                time= ((org.das2.util.LoggerManager.TimeTaggingLogger)logger).getLastTime();
                return new JLabel(String.valueOf( System.currentTimeMillis()-time ) );
            } else {
                return new JLabel("???");
            }
        }

    }

    private static Color slightlyDarker( Color c ) {
        return new Color(Math.max((int)(c.getRed() * 0.90), 0),
                     Math.max((int)(c.getGreen()*0.90), 0),
                     Math.max((int)(c.getBlue() *0.90), 0),
                     c.getAlpha());
    }

    static class MyCellRenderer implements TableCellRenderer {

        TableCellRenderer delegate;
        //Color backgroundColor;
        //Color oddColor= Color.LIGHT_GRAY;
        //Color selectedColor;
        //Color selectedBackgroundColor;
        //Color focusColor;
        //Color focusBackgroundColor;
        
        public MyCellRenderer(TableCellRenderer delegate) {
            this.delegate = delegate;            
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object lvalue, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c=  delegate.getTableCellRendererComponent(
                    table, lvalue, isSelected, hasFocus, row, column);
            if ( row % 2 == 0 ) {
                if ( isSelected ) {
                    c.setBackground( table.getSelectionBackground() );
                } else {
                    c.setBackground( table.getBackground() );
                }                
            } else {
                if ( isSelected ) {
                    c.setBackground( slightlyDarker(table.getSelectionBackground()) );
                } else {
                    c.setBackground( slightlyDarker(table.getBackground()) );
                }
            }
            return c;
        }

    }
    
    static class LevelCellRenderer implements TableCellRenderer {

        TableCellRenderer delegate;
        //Color backgroundColor;
        //Color oddColor= Color.LIGHT_GRAY;
        //Color selectedColor;
        //Color selectedBackgroundColor;
        //Color focusColor;
        //Color focusBackgroundColor;      
        public LevelCellRenderer(TableCellRenderer delegate) {
            this.delegate = delegate;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object lvalue, boolean isSelected, boolean hasFocus, int row, int column) {
            Logger logger = (Logger) lvalue;
            Object value = logger.getLevel();
            //We just need to handle null as a special case
            if (value == null) {
                Logger anscestor = logger;
                if (logger.getParent() == null) { // root logger.
                    value = logger.getLevel();
                } else {
                    do {
                        anscestor = anscestor.getParent();
                        if (anscestor == null) {
                            new Exception("anscestor is null").printStackTrace();
                            value = "NULL"; // I don't think this happens...
                        } else {
                            value = anscestor.getLevel();
                        }
                    } while (value == null);
                    value = "INHERITED(" + value + ")";

                }
            } else {
                ((JComponent) delegate).setToolTipText(null);
            }
            Component c=  delegate.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            if ( row % 2 == 0 ) {
                if ( isSelected ) {
                    c.setBackground( table.getSelectionBackground() );
                } else {
                    c.setBackground( table.getBackground() );
                }                
            } else {
                if ( isSelected ) {
                    c.setBackground( slightlyDarker(table.getSelectionBackground()) );
                } else {
                    c.setBackground( slightlyDarker(table.getBackground()) );
                }
            }
            return c;
        }

    }

    class MyEditor implements TableCellEditor {

        JComboBox cb;
        Logger value;

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            this.value = (Logger) value;
            ComboBoxModel m = new LogLevelComboBoxModel((Logger) value);
            cb = new JComboBox(m);
            cb.setRenderer(new LogLevelCellRenderer(cb.getRenderer(), this.value));
            return cb;
        }

        @Override
        public Object getCellEditorValue() {
            return this.value;
            //return cb.getSelectedItem();
        }

        @Override
        public boolean isCellEditable(EventObject anEvent) {
            return true;
        }

        @Override
        public boolean shouldSelectCell(EventObject anEvent) {
            return true;
        }

        @Override
        public boolean stopCellEditing() {
            fireEditingStopped();
            return true;
        }

        @Override
        public void cancelCellEditing() {
            fireEditingCanceled();
        }

        private final EventListenerList listeners = new EventListenerList();
        private ChangeEvent evt;

        private void fireEditingStopped() {
            Object[] l = listeners.getListenerList();
            for (int i = 0; i < l.length; i += 2) {
                if (l[i] == CellEditorListener.class) {
                    CellEditorListener cel = (CellEditorListener) l[i + 1];
                    if (evt == null) {
                        evt = new ChangeEvent(this);
                    }
                    cel.editingStopped(evt);
                }
            }
        }

        private void fireEditingCanceled() {
            Object[] l = listeners.getListenerList();
            for (int i = 0; i < l.length; i += 2) {
                if (l[i] == CellEditorListener.class) {
                    CellEditorListener cel = (CellEditorListener) l[i + 1];
                    if (evt == null) {
                        evt = new ChangeEvent(this);
                    }
                    cel.editingCanceled(evt);
                }
            }
        }

        @Override
        public void addCellEditorListener(CellEditorListener l) {
            listeners.add(CellEditorListener.class, l);
        }

        @Override
        public void removeCellEditorListener(CellEditorListener l) {
            listeners.add(CellEditorListener.class, l);
        }

    }

    private void initLogTable() {
        HashSet otherLoggers = new HashSet(org.das2.util.LoggerManager.getLoggers());
        otherLoggers.addAll(org.das2.datum.LoggerManager.getLoggers());
        String[] sloggers = (String[]) otherLoggers.toArray(new String[otherLoggers.size()]);
        Arrays.sort(sloggers);
        
        DefaultTableModel m ;
        if ( org.das2.util.LoggerManager.isUseTimeTaggingLoggers() ) {
            m = new DefaultTableModel(new String[]{"name", "level", "time"}, sloggers.length);
        } else {
            m = new DefaultTableModel(new String[]{"name", "level"}, sloggers.length);
        }
        
        int irow = 0;
        for (String slogger : sloggers) {
            m.setValueAt(slogger, irow, 0);
            Logger logger = Logger.getLogger(slogger);
            m.setValueAt(logger, irow, 1);
            if ( org.das2.util.LoggerManager.isUseTimeTaggingLoggers() ) {
                m.setValueAt(logger, irow, 2);
            }
            irow++;
        }
        jTable1.setAutoCreateRowSorter(true);
        jTable1.setModel(m);
        jTable1.getColumnModel().getColumn(0).setCellRenderer(new MyCellRenderer(new DefaultTableCellRenderer()));
        jTable1.getColumnModel().getColumn(1).setCellRenderer(new LevelCellRenderer(new DefaultTableCellRenderer()));
        jTable1.getColumnModel().getColumn(1).setCellEditor(new MyEditor());
        if (org.das2.util.LoggerManager.isUseTimeTaggingLoggers() ) {
            jTable1.getColumnModel().getColumn(2).setCellRenderer(new TimeTaggingCellRenderer(new DefaultTableCellRenderer()));
        }
        
        TableRowSorter<TableModel> rowSorter = new TableRowSorter<TableModel>(m);
        jTable1.setRowSorter(rowSorter);
        rowSorter.setComparator(1, new Comparator() {
            @Override
            public int compare(Object o1, Object o2) {
                Logger l1 = (Logger) o1;
                Logger l2 = (Logger) o2;
                Level level1 = getLoggerMindingInheritance(l1);
                Level level2 = getLoggerMindingInheritance(l2);
                return level2.intValue() - level1.intValue();
            }
        });

    }

    private void updateSearchText() {
        console.setSearchText(searchForTextField.getText());
        console.setShowOnlyHighlited(showOnlyHighlitedCB.isSelected());
        try {
            Pattern.compile(searchForTextField.getText());
            highliteLinesLabel.setText( "Highlite Lines Matching:" );
        } catch ( PatternSyntaxException ex ) {
            highliteLinesLabel.setText( "Highlite Lines Containing:" );
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        loggerIDCheckBox = new javax.swing.JCheckBox();
        timeStampsCheckBox = new javax.swing.JCheckBox();
        logLevelCheckBox = new javax.swing.JCheckBox();
        jLabel1 = new javax.swing.JLabel();
        highliteLinesLabel = new javax.swing.JLabel();
        searchForTextField = new javax.swing.JTextField();
        jButton1 = new javax.swing.JButton();
        threadsCB = new javax.swing.JCheckBox();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();
        consoleFontButton = new javax.swing.JButton();
        showOnlyHighlitedCB = new javax.swing.JCheckBox();
        saveLoggerConfigButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        loggerIDCheckBox.setText("logger name");
        loggerIDCheckBox.setToolTipText("identifies the logger posting the message");
        loggerIDCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loggerIDCheckBoxActionPerformed(evt);
            }
        });

        timeStampsCheckBox.setText("timing");
        timeStampsCheckBox.setToolTipText("Show time of the message, in seconds before the most recent message.");
        timeStampsCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timeStampsCheckBoxActionPerformed(evt);
            }
        });

        logLevelCheckBox.setText("log levels");
        logLevelCheckBox.setToolTipText("show the log level (verbosity) of the messages.");
        logLevelCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                logLevelCheckBoxActionPerformed(evt);
            }
        });

        jLabel1.setText("Verbosity of the logger channels:");
        jLabel1.setToolTipText("<html>Autoplot uses Java Logging, which allows messages to be sent to named channels with a verbosity level.   Set verbosity to finer levels to see messages intended for developers.");

        highliteLinesLabel.setText("Highlite Lines Matching:");
        highliteLinesLabel.setToolTipText("Enter a regular expression.  Lines containing this will be highlited.");

        searchForTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                searchForTextFieldFocusLost(evt);
            }
        });
        searchForTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchForTextFieldActionPerformed(evt);
            }
        });

        jButton1.setText("Okay");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        threadsCB.setText("threads");
        threadsCB.setToolTipText("Show unique number for each execution thread.  The GUI event thread is labelled \"GUI.\"");
        threadsCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                threadsCBActionPerformed(evt);
            }
        });

        jTable1.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        jScrollPane2.setViewportView(jTable1);

        consoleFontButton.setText("Console Font...");
        consoleFontButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                consoleFontButtonActionPerformed(evt);
            }
        });

        showOnlyHighlitedCB.setText("Show only these lines");
        showOnlyHighlitedCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showOnlyHighlitedCBActionPerformed(evt);
            }
        });

        saveLoggerConfigButton.setText("Save...");
        saveLoggerConfigButton.setToolTipText("Save this configuration to persistent settings.");
        saveLoggerConfigButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveLoggerConfigButtonActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(jScrollPane2)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, layout.createSequentialGroup()
                        .add(highliteLinesLabel)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(searchForTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 172, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(showOnlyHighlitedCB)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 223, Short.MAX_VALUE)
                        .add(jButton1))
                    .add(org.jdesktop.layout.GroupLayout.LEADING, layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                            .add(org.jdesktop.layout.GroupLayout.LEADING, jLabel1)
                            .add(org.jdesktop.layout.GroupLayout.LEADING, layout.createSequentialGroup()
                                .add(loggerIDCheckBox)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(timeStampsCheckBox)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(logLevelCheckBox)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(threadsCB)
                                .add(18, 18, 18)
                                .add(consoleFontButton)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(saveLoggerConfigButton)))
                        .add(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 251, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(timeStampsCheckBox)
                    .add(logLevelCheckBox)
                    .add(loggerIDCheckBox)
                    .add(threadsCB)
                    .add(consoleFontButton)
                    .add(saveLoggerConfigButton))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(highliteLinesLabel)
                    .add(searchForTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jButton1)
                    .add(showOnlyHighlitedCB))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void loggerIDCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loggerIDCheckBoxActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        console.setShowLoggerId(loggerIDCheckBox.isSelected());
        console.update();
}//GEN-LAST:event_loggerIDCheckBoxActionPerformed

    private void timeStampsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeStampsCheckBoxActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        console.setShowTimeStamps(timeStampsCheckBox.isSelected());
        console.update();
}//GEN-LAST:event_timeStampsCheckBoxActionPerformed

    private void logLevelCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_logLevelCheckBoxActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        console.setShowLevel(logLevelCheckBox.isSelected());
        console.update();
}//GEN-LAST:event_logLevelCheckBoxActionPerformed

    private void searchForTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchForTextFieldActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        updateSearchText();
}//GEN-LAST:event_searchForTextFieldActionPerformed

    private void searchForTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_searchForTextFieldFocusLost
        updateSearchText();
    }//GEN-LAST:event_searchForTextFieldFocusLost

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        this.setVisible(false);
    }//GEN-LAST:event_jButton1ActionPerformed

    private void threadsCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_threadsCBActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        console.setShowThreads(threadsCB.isSelected());
        console.update();
    }//GEN-LAST:event_threadsCBActionPerformed

    private void consoleFontButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_consoleFontButtonActionPerformed
        JFontChooser chooser = new JFontChooser( null );

        chooser.setFont( console.getLogTextArea().getFont() );
        chooser.setLocationRelativeTo(console);
        if (chooser.showDialog() == JFontChooser.OK_OPTION) {
            console.getLogTextArea().setFont( chooser.getFont() );
        } 
    }//GEN-LAST:event_consoleFontButtonActionPerformed

    private void showOnlyHighlitedCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showOnlyHighlitedCBActionPerformed
        updateSearchText();
    }//GEN-LAST:event_showOnlyHighlitedCBActionPerformed

    private void saveLoggerConfigButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveLoggerConfigButtonActionPerformed
        final StringBuilder b= new StringBuilder();
        b.append("handlers = java.util.logging.ConsoleHandler\n");
        b.append("java.util.logging.ConsoleHandler.level = ALL\n");
        
        b.append("\n");
        for ( String s: org.das2.util.LoggerManager.getLoggers() ) {
            Logger l= org.das2.util.LoggerManager.getLogger(s);
            if ( l.isLoggable(Level.CONFIG) ) {
                Level level= l.getLevel();
                if ( level!=null ) {
                    b.append(s).append(".level=").append(l.getLevel().toString()).append("\n");
                }
            }
        }
        for ( String s: org.das2.datum.LoggerManager.getLoggers() ) { // Note there are two LoggerManagers!
            Logger l= org.das2.datum.LoggerManager.getLogger(s);
            if ( l.isLoggable(Level.FINE) ) {
                Level level= l.getLevel();
                if ( level!=null ) {
                    b.append(s).append(".level=").append(l.getLevel().toString()).append("\n");
                }
            }
        }
        if ( JOptionPane.OK_OPTION==JOptionPane.showConfirmDialog( rootPane, "Save settings to AUTOPLOT_DATA/config/logging.properties?", "Save Logging", JOptionPane.OK_CANCEL_OPTION ) ) {
            new Thread( new Runnable() {
                @Override
                public void run() {
                    String apd= AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA);
                    File dd= new File( new File( new File( apd ), "config" ), "logging.properties" );
                    try ( FileOutputStream out= new FileOutputStream(dd) ) {
                        out.write( b.toString().getBytes() );
                    } catch (IOException ex) {
                        Logger.getLogger("autoplot.jython.console").log(Level.SEVERE, null, ex);
                    }
                }
            }, "writeLoggerSettings" ).start();
        }
    }//GEN-LAST:event_saveLoggerConfigButtonActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        Logger l= org.das2.util.LoggerManager.getLogger("autoplot.first");
        l.finest("hi there");
        org.das2.util.LoggerManager.getLogger("qdataset");
        LoggerManager.getLogger("qdataset.first");
        LoggerManager.getLogger("qdataset.second");
        LoggerManager.getLogger("qdataset.second").setLevel(Level.FINE);
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                LogConsole console = new LogConsole();
                LogConsoleSettingsDialog dialog = new LogConsoleSettingsDialog(new javax.swing.JFrame(), true, console);
                dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent e) {
                        System.exit(0);
                    }
                });
                dialog.setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton consoleFontButton;
    private javax.swing.JLabel highliteLinesLabel;
    private javax.swing.JButton jButton1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTable jTable1;
    private javax.swing.JCheckBox logLevelCheckBox;
    private javax.swing.JCheckBox loggerIDCheckBox;
    private javax.swing.JButton saveLoggerConfigButton;
    private javax.swing.JTextField searchForTextField;
    private javax.swing.JCheckBox showOnlyHighlitedCB;
    private javax.swing.JCheckBox threadsCB;
    private javax.swing.JCheckBox timeStampsCheckBox;
    // End of variables declaration//GEN-END:variables

}
