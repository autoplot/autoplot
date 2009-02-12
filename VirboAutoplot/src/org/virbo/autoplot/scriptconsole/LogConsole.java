/*
 * LogConsole.java
 *
 * Created on June 19, 2008, 4:08 PM
 */
package org.virbo.autoplot.scriptconsole;

import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JFileChooser;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.Keymap;
import org.das2.system.RequestProcessor;
import org.python.core.PyException;
import org.python.util.PythonInterpreter;
import org.virbo.autoplot.JythonUtil;

/**
 * GUI for graphically handling log records.  This defines a Handler, and has
 * methods for turning off console logging. (Another class should be used to 
 * log stderr and stdout messages.)  Users can dump the records to a file for
 * remote analysis.
 * 
 * @author  jbf
 */
public class LogConsole extends javax.swing.JPanel {

    public static final int RECORD_SIZE_LIMIT = 1000;
    List<LogRecord> records = new LinkedList<LogRecord>();
    int eventThreadId = -1;
    int level = Level.INFO.intValue();
    NumberFormat nf = new DecimalFormat("00.000");
    private Timer timer2;
    PrintStream oldStdOut;
    PrintStream oldStdErr;
    PythonInterpreter interp = null;

    /** Creates new form LogConsole */
    public LogConsole() {
        initComponents();
        
        commandLineTextPane1.addActionListener( new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final String s = commandLineTextPane1.getText();
                RequestProcessor.invokeLater(new Runnable() {
                    public void run() {
                        try {
                            System.out.println("AP> "+s);
                            if (interp == null) {
                                commandLineTextPane1.setText("initializing interpretter...");
                                interp = JythonUtil.createInterpreter(true, false);
                                commandLineTextPane1.setText(s);
                            }
                            interp.exec(s);
                            commandLineTextPane1.setText("");
                        } catch (IOException ex) {
                            Logger.getLogger(LogConsole.class.getName()).log(Level.SEVERE, null, ex);
                        } catch ( PyException ex ) {
                            System.err.println(ex.toString());
                        }
                    }
                } );
            }
        });

        timer2 = new Timer(100, new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                update();
            }
        });
        timer2.setRepeats(false);
    }

    /**
     * create a handler that listens for log messages.  This handler is added
     * to the Loggers that should be displayed here.  Also, the log levels of
     * the Loggers should be set to ALL, since the filtering is done here.
     * For example:
     *         Handler h = lc.getHandler();
     *         Logger.getLogger("virbo").setLevel(Level.ALL);
     *         Logger.getLogger("virbo").addHandler(h);
     * @return handler for receiving messages.
     */
    public Handler getHandler() {
        Handler h = new Handler() {

            public synchronized void publish(LogRecord rec) {
                synchronized (LogConsole.this) {
                    //if ( !records.get(records.size()-1).equals(rec)) {
                    records.add(rec);
                    timer2.restart();
                    //timer.tickle();
                    if (eventThreadId == -1 && EventQueue.isDispatchThread()) {
                        eventThreadId = rec.getThreadID();
                    }
                //}
                }
                if (rec.getLevel().intValue() >= Level.WARNING.intValue()) {
                    LogConsole.this.oldStdErr.println(rec.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() throws SecurityException {
            }
        };
        h.setLevel(Level.ALL);
        return h;
    }

    /**
     * create loggers that log messages sent to System.err and System.out.
     * This is used with turnOffConsoleHandlers.
     * @see turnOffConsoleHandlers
     */
    public void logConsoleMessages() {
        Logger logger;
        LoggingOutputStream los;

        logger = Logger.getLogger("console.stdout");
        los = new LoggingOutputStream(logger, Level.INFO);
        oldStdOut = System.out;
        System.setOut(new PrintStream(los, true));

        logger = Logger.getLogger("console.stderr");
        los = new LoggingOutputStream(logger, Level.WARNING);
        oldStdErr = System.err;
        System.setErr(new PrintStream(los, true));
    }

    public void undoLogConsoleMessages() {
        System.setOut(oldStdOut);
        System.setErr(oldStdErr);
    }

    /**
     * iterate through the Handlers, looking for ConsoleHandlers, and turning
     * them off.
     * @see logConsoleMessages
     */
    public void turnOffConsoleHandlers() {
        for (Handler h : Logger.getLogger("").getHandlers()) {
            if (h instanceof ConsoleHandler) {
                h.setLevel(Level.OFF);
            }
        }
    }

    private synchronized void update() {
        final StringBuffer buf = new StringBuffer();

        int n = records.size();
        long t = n == 0 ? 0 : records.get(n - 1).getMillis();

        boolean timeStamps = timeStampsCheckBox.isSelected();
        boolean logLevels = logLevelCheckBox.isSelected();

        long lastT = 0;
        for (LogRecord rec : records) {
            if (rec.getLevel().intValue() >= level) {
                if (lastT != 0 && rec.getMillis() - lastT > 5000) {
                    buf.append("\n");
                }
                lastT = rec.getMillis();
                String recMsg = rec.getMessage();
                String prefix = "";
                if (loggerIDCheckBox.isSelected()) {
                    prefix += rec.getLoggerName() + " ";
                }
                if (timeStamps) {
                    prefix += nf.format((rec.getMillis() - t) / 1000.) + " ";
                }
                if (logLevels) {
                    prefix += rec.getLevel() + " ";
                }
                if (rec.getThreadID() == eventThreadId) {
                    prefix += "(GUI) ";
                }
                if (!prefix.equals("")) {
                    recMsg = prefix.trim() + ": " + recMsg;
                }
                buf.append(recMsg).append("\n");
            }
        }

        SwingUtilities.invokeLater(new Runnable() {

            public void run() {
                logTextArea.setText(buf.toString());
            }
        });

        while (records.size() > RECORD_SIZE_LIMIT) {
            records.remove(0);
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
        logTextArea = new javax.swing.JTextArea();
        actionsPanel = new javax.swing.JPanel();
        clearButton = new javax.swing.JButton();
        saveButton = new javax.swing.JButton();
        copyButton = new javax.swing.JButton();
        verbosityPanel = new javax.swing.JPanel();
        loggerIDCheckBox = new javax.swing.JCheckBox();
        logLevelCheckBox = new javax.swing.JCheckBox();
        timeStampsCheckBox = new javax.swing.JCheckBox();
        verbositySelect = new javax.swing.JComboBox();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        commandLineTextPane1 = new org.virbo.autoplot.scriptconsole.CommandLineTextPane();

        jScrollPane1.setAutoscrolls(true);

        logTextArea.setColumns(20);
        logTextArea.setRows(5);
        jScrollPane1.setViewportView(logTextArea);

        clearButton.setText("clear");
        clearButton.setToolTipText("clear all messages.  ");
        clearButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearButtonActionPerformed(evt);
            }
        });

        saveButton.setText("save...");
        saveButton.setToolTipText("Saves the records to file for use by software support team.");
        saveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveButtonActionPerformed(evt);
            }
        });

        copyButton.setText("copy");
        copyButton.setToolTipText("copy xml of log records into system clipboard, for pasting into email.\n");
        copyButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyButtonActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout actionsPanelLayout = new org.jdesktop.layout.GroupLayout(actionsPanel);
        actionsPanel.setLayout(actionsPanelLayout);
        actionsPanelLayout.setHorizontalGroup(
            actionsPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(actionsPanelLayout.createSequentialGroup()
                .add(12, 12, 12)
                .add(clearButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(saveButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(copyButton)
                .addContainerGap(18, Short.MAX_VALUE))
        );
        actionsPanelLayout.setVerticalGroup(
            actionsPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(actionsPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                .add(clearButton)
                .add(saveButton)
                .add(copyButton))
        );

        loggerIDCheckBox.setText("logger ID");
        loggerIDCheckBox.setToolTipText("identifies the logger posting the message");
        loggerIDCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loggerIDCheckBoxActionPerformed(evt);
            }
        });

        logLevelCheckBox.setText("log levels");
        logLevelCheckBox.setToolTipText("show the log level (verbosity) of the messages.");
        logLevelCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                logLevelCheckBoxActionPerformed(evt);
            }
        });

        timeStampsCheckBox.setText("timing");
        timeStampsCheckBox.setToolTipText("Show time of the message, in seconds before the most recent message.");
        timeStampsCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timeStampsCheckBoxActionPerformed(evt);
            }
        });

        verbositySelect.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "warnings", "informational", "debug", "all" }));
        verbositySelect.setSelectedIndex(1);
        verbositySelect.setToolTipText("filter messages by verbosity.");
        verbositySelect.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                verbositySelectActionPerformed(evt);
            }
        });

        jLabel1.setText("verbosity:");

        org.jdesktop.layout.GroupLayout verbosityPanelLayout = new org.jdesktop.layout.GroupLayout(verbosityPanel);
        verbosityPanel.setLayout(verbosityPanelLayout);
        verbosityPanelLayout.setHorizontalGroup(
            verbosityPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(verbosityPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(verbosityPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, verbosityPanelLayout.createSequentialGroup()
                        .add(loggerIDCheckBox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(timeStampsCheckBox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(logLevelCheckBox))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, verbosityPanelLayout.createSequentialGroup()
                        .add(jLabel1)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(verbositySelect, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 147, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        verbosityPanelLayout.setVerticalGroup(
            verbosityPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(verbosityPanelLayout.createSequentialGroup()
                .add(verbosityPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(verbositySelect, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel1))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(verbosityPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(timeStampsCheckBox)
                    .add(logLevelCheckBox)
                    .add(loggerIDCheckBox)))
        );

        jLabel2.setText("AP>");

        jScrollPane2.setViewportView(commandLineTextPane1);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(actionsPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(layout.createSequentialGroup()
                        .addContainerGap()
                        .add(jLabel2)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jScrollPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 336, Short.MAX_VALUE)))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(verbosityPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 652, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 309, Short.MAX_VALUE)
                .add(17, 17, 17)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(verbosityPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                            .add(jScrollPane2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(jLabel2))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(actionsPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
        );
    }// </editor-fold>//GEN-END:initComponents

private void clearButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearButtonActionPerformed
    records.removeAll(records);
    update();
}//GEN-LAST:event_clearButtonActionPerformed

private void verbositySelectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_verbositySelectActionPerformed
    String o = (String) verbositySelect.getSelectedItem();

    if (o.equals("warnings")) {
        level = Level.WARNING.intValue();
    } else if (o.equals("informational")) {
        level = Level.INFO.intValue();
    } else if (o.equals("debug")) {
        level = Level.FINER.intValue();
    } else if (o.equals("all")) {
        level = Level.ALL.intValue();
    } else {
        throw new RuntimeException("bad level string: " + o);
    }
    update();
}//GEN-LAST:event_verbositySelectActionPerformed

private void timeStampsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeStampsCheckBoxActionPerformed
    update();
}//GEN-LAST:event_timeStampsCheckBoxActionPerformed

private void logLevelCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_logLevelCheckBoxActionPerformed
    update();
}//GEN-LAST:event_logLevelCheckBoxActionPerformed

private void loggerIDCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loggerIDCheckBoxActionPerformed
    update();
}//GEN-LAST:event_loggerIDCheckBoxActionPerformed

private void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveButtonActionPerformed
    JFileChooser chooser = new JFileChooser();
    if (JFileChooser.APPROVE_OPTION == chooser.showSaveDialog(this)) {
        FileOutputStream fo = null;
        try {
            fo = new FileOutputStream(chooser.getSelectedFile());
            LogConsoleUtil.serializeLogRecords(records, fo);
            fo.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(LogConsole.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(LogConsole.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fo.close();
            } catch (IOException ex) {
                Logger.getLogger(LogConsole.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}//GEN-LAST:event_saveButtonActionPerformed

private void copyButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyButtonActionPerformed
    try {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1000);
        LogConsoleUtil.serializeLogRecords(records, out);
        out.close();
        StringSelection stringSelection = new StringSelection(out.toString());
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, new ClipboardOwner() {

            public void lostOwnership(Clipboard clipboard, Transferable contents) {
            }
        });
    } catch (IOException ex) {
        throw new RuntimeException(ex);
    }
}//GEN-LAST:event_copyButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel actionsPanel;
    private javax.swing.JButton clearButton;
    private org.virbo.autoplot.scriptconsole.CommandLineTextPane commandLineTextPane1;
    private javax.swing.JButton copyButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JCheckBox logLevelCheckBox;
    private javax.swing.JTextArea logTextArea;
    private javax.swing.JCheckBox loggerIDCheckBox;
    private javax.swing.JButton saveButton;
    private javax.swing.JCheckBox timeStampsCheckBox;
    private javax.swing.JPanel verbosityPanel;
    private javax.swing.JComboBox verbositySelect;
    // End of variables declaration//GEN-END:variables
}
