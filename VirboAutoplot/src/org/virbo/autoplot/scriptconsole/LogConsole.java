/*
 * LogConsole.java
 *
 * Created on June 19, 2008, 4:08 PM
 */
package org.virbo.autoplot.scriptconsole;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
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
import java.util.regex.Pattern;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.xml.parsers.ParserConfigurationException;
import org.das2.jythoncompletion.JythonCompletionTask;
import org.das2.jythoncompletion.JythonInterpreterProvider;
import org.das2.jythoncompletion.ui.CompletionImpl;
import org.das2.system.RequestProcessor;
import org.python.core.PyException;
import org.python.util.PythonInterpreter;
import org.virbo.autoplot.JythonUtil;
import org.xml.sax.SAXException;

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
    boolean showLoggerId = false;
    boolean showTimeStamps = false;
    boolean showLevel = false;
    LogConsoleSettingsDialog settingsDialog = null;
    NumberFormat nf = new DecimalFormat("00.000");
    private Timer timer2;
    PrintStream oldStdOut;
    PrintStream oldStdErr;
    PythonInterpreter interp = null;

    /** Creates new form LogConsole */
    public LogConsole() {
        initComponents();

        commandLineTextPane1.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                final String s = commandLineTextPane1.getText();
                RequestProcessor.invokeLater(new Runnable() {

                    public void run() {
                        try {
                            System.out.println("AP> " + s);
                            if (interp == null) {
                                commandLineTextPane1.setText("initializing interpretter...");
                                interp = JythonUtil.createInterpreter(true, false);
                                commandLineTextPane1.setText(s);
                            }
                            interp.exec(s);
                            commandLineTextPane1.setText("");
                        } catch (IOException ex) {
                            Logger.getLogger(LogConsole.class.getName()).log(Level.SEVERE, null, ex);
                            commandLineTextPane1.setText("");
                        } catch (PyException ex) {
                            System.err.println(ex.toString());
                            commandLineTextPane1.setText("");
                        }
                    }
                });
            }
        });


        this.commandLineTextPane1.putClientProperty(JythonCompletionTask.CLIENT_PROPERTY_INTERPRETER_PROVIDER, new JythonInterpreterProvider() {

            public PythonInterpreter createInterpreter() throws java.io.IOException {
                if (interp == null) {
                    String s = commandLineTextPane1.getText();
                    commandLineTextPane1.setText("initializing interpretter...");
                    interp = JythonUtil.createInterpreter(true, false);
                    commandLineTextPane1.setText(s);
                }
                return interp;
            }
        });

        timer2 = new Timer(100, new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                update();
            }
        });
        timer2.setRepeats(false);

    }
    protected String searchText = "";
    public static final String PROP_SEARCHTEXT = "searchText";

    public String getSearchText() {
        return searchText;
    }

    public void setSearchText(String searchText) {
        String oldSearchText = this.searchText;
        this.searchText = searchText;
        update();
        firePropertyChange(PROP_SEARCHTEXT, oldSearchText, searchText);
    }

    private synchronized LogConsoleSettingsDialog getSettingsDialog() {
        if (this.settingsDialog == null) {
            settingsDialog = new LogConsoleSettingsDialog((JFrame) SwingUtilities.getWindowAncestor(this), false, this);
        }
        return settingsDialog;
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
        System.err.println("turning off console log, look for messages in LogConsole");
        for (Handler h : Logger.getLogger("").getHandlers()) {
            if (h instanceof ConsoleHandler) {
                h.setLevel(Level.OFF);
            }
        }
    }

    public synchronized void update() {
        try {
            final StringBuffer buf = new StringBuffer();
            int n = records.size();
            long t = n == 0 ? 0 : records.get(n - 1).getMillis();
            boolean timeStamps = showTimeStamps;
            boolean logLevels = showLevel;
            String st = searchText;
            if (st != null && st.length() == 0) st = null;
            Pattern p = null;
            if (st != null) p = Pattern.compile(st);
            StyledDocument doc = logTextArea.getStyledDocument();
            doc.remove(0, doc.getLength());
            long lastT = 0;

            MutableAttributeSet highlistAttr= new SimpleAttributeSet();
            StyleConstants.setBackground( highlistAttr, Color.ORANGE );
            
            for (LogRecord rec : records) {
                if (rec.getLevel().intValue() >= level) {
                    if (lastT != 0 && rec.getMillis() - lastT > 5000) {
                        //buf.append("\n");
                        doc.insertString(doc.getLength(), "\n", null);
                    }
                    lastT = rec.getMillis();
                    String recMsg = rec.getMessage();
                    String prefix = "";
                    if (showLoggerId) {
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

                    AttributeSet attr= null;
                    if (st != null && p.matcher(recMsg).find()) {
                        attr= highlistAttr;
                    }
                    try {
                        //buf.append(recMsg).append("\n");
                        recMsg+= "\n";
                        doc.insertString(doc.getLength(), recMsg, attr );
                    } catch (BadLocationException ex) {
                        Logger.getLogger(LogConsole.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            SwingUtilities.invokeLater(new Runnable() {

                public void run() {
                    //logTextArea.setText(buf.toString());
                }
            });
            while (records.size() > RECORD_SIZE_LIMIT) {
                records.remove(0);
            }
        } catch (BadLocationException ex) {
            Logger.getLogger(LogConsole.class.getName()).log(Level.SEVERE, null, ex);
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

        actionsPanel = new javax.swing.JPanel();
        clearButton = new javax.swing.JButton();
        saveButton = new javax.swing.JButton();
        copyButton = new javax.swing.JButton();
        verbosityPanel = new javax.swing.JPanel();
        jButton1 = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        commandLineTextPane1 = new org.virbo.autoplot.scriptconsole.CommandLineTextPane();
        jScrollPane1 = new javax.swing.JScrollPane();
        logTextArea = new javax.swing.JTextPane();

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

        jButton1.setText("Console Settings...");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout verbosityPanelLayout = new org.jdesktop.layout.GroupLayout(verbosityPanel);
        verbosityPanel.setLayout(verbosityPanelLayout);
        verbosityPanelLayout.setHorizontalGroup(
            verbosityPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, verbosityPanelLayout.createSequentialGroup()
                .addContainerGap(132, Short.MAX_VALUE)
                .add(jButton1))
        );
        verbosityPanelLayout.setVerticalGroup(
            verbosityPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, verbosityPanelLayout.createSequentialGroup()
                .addContainerGap(35, Short.MAX_VALUE)
                .add(jButton1))
        );

        jLabel2.setText("AP>");

        commandLineTextPane1.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                commandLineTextPane1FocusGained(evt);
            }
        });
        jScrollPane2.setViewportView(commandLineTextPane1);

        jScrollPane1.setViewportView(logTextArea);

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
                        .add(jScrollPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 352, Short.MAX_VALUE)))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(verbosityPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 674, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 310, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
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
    records.clear();
    update();
}//GEN-LAST:event_clearButtonActionPerformed

private void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveButtonActionPerformed
    if ((evt.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK) {
        JFileChooser chooser = new JFileChooser();
        if (JFileChooser.APPROVE_OPTION == chooser.showOpenDialog(this)) {
            FileInputStream fo = null;
            try {
                fo = new FileInputStream(chooser.getSelectedFile());
                records = LogConsoleUtil.deserializeLogRecords(fo);
            } catch (ParserConfigurationException ex) {
                Logger.getLogger(LogConsole.class.getName()).log(Level.SEVERE, null, ex);
            } catch (SAXException ex) {
                Logger.getLogger(LogConsole.class.getName()).log(Level.SEVERE, null, ex);
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
    } else {
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

private void commandLineTextPane1FocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_commandLineTextPane1FocusGained
    CompletionImpl impl = CompletionImpl.get();
    impl.startPopup(this.commandLineTextPane1);
}//GEN-LAST:event_commandLineTextPane1FocusGained

private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
    getSettingsDialog().setVisible(true);
}//GEN-LAST:event_jButton1ActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel actionsPanel;
    private javax.swing.JButton clearButton;
    private org.virbo.autoplot.scriptconsole.CommandLineTextPane commandLineTextPane1;
    private javax.swing.JButton copyButton;
    private javax.swing.JButton jButton1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTextPane logTextArea;
    private javax.swing.JButton saveButton;
    private javax.swing.JPanel verbosityPanel;
    // End of variables declaration//GEN-END:variables
}
