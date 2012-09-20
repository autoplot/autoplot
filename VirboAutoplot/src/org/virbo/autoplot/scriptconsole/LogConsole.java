/*
 * LogConsole.java
 *
 * Created on June 19, 2008, 4:08 PM
 */
package org.virbo.autoplot.scriptconsole;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.KeyStroke;
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
import org.python.core.PyNone;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import org.virbo.autoplot.GuiSupport;
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

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot");

    public static final int RECORD_SIZE_LIMIT = 1000;
    List<LogRecord> records = new LinkedList<LogRecord>();
    int eventThreadId = -1;
    int level = Level.ALL.intValue(); // let each logger do the filtering now.
    boolean showLoggerId = false;
    boolean showTimeStamps = false;
    boolean showLevel = false;

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
                            maybeInitializeInterpreter();
                            try {
                                PyObject po= interp.eval(s);
                                if ( !( po instanceof PyNone ) ) interp.exec("print repr(" + s +")" ); 
                            } catch (PyException ex ) {
                                interp.exec(s);
                            }
                            commandLineTextPane1.setText("");
                        } catch (IOException ex) {
                            logger.log(Level.SEVERE, null, ex);
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
                maybeInitializeInterpreter();
                return interp;
            }
        });

        timer2 = new Timer(100, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                update();
            }
        });
        timer2.setRepeats(false);

        final javax.swing.JTextPane ftxt= this.logTextArea;

        this.logTextArea.getActionMap().put( "biggerFont", new AbstractAction( "Text Size Bigger" ) {
            public void actionPerformed( ActionEvent e ) {
               Font f= ftxt.getFont();
               float size= f.getSize2D();
               float step= size < 14 ? 1 : 2;
               ftxt.setFont( f.deriveFont( Math.min( 40, size + step ) ) );
            }
        } );

        this.logTextArea.getActionMap().put( "smallerFont", new AbstractAction( "Text Size Smaller" ) {
            public void actionPerformed( ActionEvent e ) {
               Font f= ftxt.getFont();
               float size= f.getSize2D();
               float step= size < 14 ? 1 : 2;
               ftxt.setFont( f.deriveFont( Math.max( 4, size - step ) ) );
            }
        } );

        Toolkit tk= Toolkit.getDefaultToolkit();
        this.logTextArea.getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_EQUALS, tk.getMenuShortcutKeyMask() ), "biggerFont" );
        this.logTextArea.getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_MINUS, tk.getMenuShortcutKeyMask() ), "smallerFont" );

    }

    private void maybeInitializeInterpreter( ) throws IOException {
        if (interp == null) {
            String s = commandLineTextPane1.getText();
            int ipos= commandLineTextPane1.getCaretPosition();
            commandLineTextPane1.setText("initializing interpretter...");
            interp = JythonUtil.createInterpreter(true, false);
            if ( scriptContext!=null ) {
                for ( Entry<String,Object> e: scriptContext.entrySet() ) {
                    interp.set( e.getKey(), e.getValue() );
                }
            }
            commandLineTextPane1.setText(s);
            commandLineTextPane1.setCaretPosition(ipos);
        }
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


    public synchronized void setShowLoggerId(boolean selected) {
        this.showLoggerId= selected;
    }

    public synchronized void setShowTimeStamps( boolean selected ) {
        this.showTimeStamps= selected;
    }


    public synchronized void setShowLevel( boolean selected ) {
        this.showLevel= selected;
    }

    public synchronized void setLevel( int level ) {
        this.level= level;
    }

    protected Map<String, Object> scriptContext = null;
    public static final String PROP_SCRIPTCONTEXT = "scriptContext";

    public Map<String, Object> getScriptContext() {
        return scriptContext;
    }

    public void setScriptContext(Map<String, Object> scriptContext) {
        Map<String, Object> oldScriptContext = this.scriptContext;
        this.scriptContext = scriptContext;
        firePropertyChange(PROP_SCRIPTCONTEXT, oldScriptContext, scriptContext);
    }

    private synchronized LogConsoleSettingsDialog getSettingsDialog() {
        LogConsoleSettingsDialog settingsDialog = new LogConsoleSettingsDialog( GuiSupport.getFrameForComponent(this), true, this);
        return settingsDialog;
    }

    private Handler handler;

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
    public synchronized Handler getHandler() {
        if ( handler==null ) {
            handler = new Handler() {
                public synchronized void publish(LogRecord rec) {
                    synchronized (LogConsole.this) {
                        Object[] parms= rec.getParameters();

                        String recMsg;
                        if ( parms==null || parms.length==0 ) {
                            recMsg = rec.getMessage();
                        } else {
                            recMsg = MessageFormat.format( rec.getMessage(), parms );
                        }
                        LogRecord copy= new LogRecord( rec.getLevel(), recMsg ); //bug 3479791: just flatten this, so we don't have to format it each time
                        copy.setLoggerName(rec.getLoggerName());
                        copy.setMillis(rec.getMillis());
                        records.add(copy);
                        timer2.restart();
                        if (eventThreadId == -1 && EventQueue.isDispatchThread()) {
                            eventThreadId = rec.getThreadID();
                        }
                    }
                    if (rec.getLevel().intValue() >= Level.WARNING.intValue()) {
                        if (LogConsole.this.oldStdErr != null) {
                            String recMsg;
                            String msg= rec.getMessage();
                            Object[] parms= rec.getParameters();
                            if ( parms==null || parms.length==0 ) {
                                recMsg = msg;
                            } else {
                                recMsg = MessageFormat.format( msg, parms );
                            }
                            LogConsole.this.oldStdErr.println( recMsg );
                        }
                    }
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() throws SecurityException {
                }

                @Override
                public String toString() {
                    return "LogConsole.Handler";
                }
            };
            handler.setLevel(Level.ALL);
        }
        return handler;
    }
    private static boolean alreadyLoggingStdout = false;

    /**
     * create loggers that log messages sent to System.err and System.out.
     * This is used with turnOffConsoleHandlers.  This checks to see if
     * stderr and stdout are already logging, for example when a second application
     * is launched in the same jvm.
     * 
     * @see turnOffConsoleHandlers
     */
    public synchronized void logConsoleMessages() {
        Logger logger;
        LoggingOutputStream los;

        if (alreadyLoggingStdout) {
            System.err.println("already logging stdout and stderr");
            return;
        } else {
            alreadyLoggingStdout = true;
        }

        logger = org.das2.util.LoggerManager.getLogger("console.stdout");
        los = new LoggingOutputStream(logger, Level.INFO);
        oldStdOut = System.out;
        System.setOut(new PrintStream(los, true));

        logger = org.das2.util.LoggerManager.getLogger("console.stderr");
        los = new LoggingOutputStream(logger, Level.WARNING);
        oldStdErr = System.err;
        System.setErr(new PrintStream(los, true));
    }

    public synchronized void undoLogConsoleMessages() {
        System.setOut(oldStdOut);
        System.setErr(oldStdErr);
    }

    /**
     * iterate through the Handlers, looking for ConsoleHandlers, and turning
     * them off.
     * @see logConsoleMessages
     */
    public void turnOffConsoleHandlers() {
        System.err.println("turning off default log, look for messages in console tab.");
        for (Handler h : Logger.getLogger("").getHandlers()) {
            if (h instanceof ConsoleHandler) {
                h.setLevel(Level.OFF);
            }
        }
    }

    /**
     * note this is generally called from a timer that coalesces events.  But
     * may be called explicitly in response to a user event as well.
     */
    public synchronized void update() {
        try {
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

            MutableAttributeSet highlistAttr = new SimpleAttributeSet();
            StyleConstants.setBackground(highlistAttr, Color.ORANGE);

            for (LogRecord rec : records) {
                if (rec.getLevel().intValue() >= level) {
                    if (lastT != 0 && rec.getMillis() - lastT > 5000) {
                        //buf.append("\n");
                        doc.insertString(doc.getLength(), "\n", null);
                    }
                    lastT = rec.getMillis();
                    
                    String recMsg;
                    String msg= rec.getMessage();
                    Object[] parms= rec.getParameters();
                    if ( parms==null || parms.length==0 ) {
                        recMsg = msg;
                    } else {
                        recMsg = MessageFormat.format( msg, parms );
                    }
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

                    AttributeSet attr = null;
                    if (st != null && p.matcher(recMsg).find()) {
                        attr = highlistAttr;
                    }
                    try {
                        //buf.append(recMsg).append("\n");
                        recMsg += "\n";
                        doc.insertString(doc.getLength(), recMsg, attr);
                    } catch (BadLocationException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                }
            }
            while (records.size() > RECORD_SIZE_LIMIT) {
                records.remove(0);
            }
        } catch (BadLocationException ex) {
            logger.log(Level.SEVERE, null, ex);
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
        jLabel2 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        commandLineTextPane1 = new org.virbo.autoplot.scriptconsole.CommandLineTextPane();
        jScrollPane1 = new javax.swing.JScrollPane();
        logTextArea = new javax.swing.JTextPane();
        jButton1 = new javax.swing.JButton();

        clearButton.setText("Clear");
        clearButton.setToolTipText("clear all messages.  ");
        clearButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearButtonActionPerformed(evt);
            }
        });

        saveButton.setText("Save As...");
        saveButton.setToolTipText("saves the records to file for use by software support team.");
        saveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveButtonActionPerformed(evt);
            }
        });

        copyButton.setText("Copy");
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

        jLabel2.setText("AP>");

        commandLineTextPane1.setToolTipText("enter jython commands here to control the application, for example \"plot([1,2,3])\"");
        commandLineTextPane1.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                commandLineTextPane1FocusGained(evt);
            }
        });
        jScrollPane2.setViewportView(commandLineTextPane1);

        logTextArea.setEditable(false);
        jScrollPane1.setViewportView(logTextArea);

        jButton1.setText("Console Settings...");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(actionsPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 319, Short.MAX_VALUE)
                .add(jButton1)
                .addContainerGap())
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(jLabel2)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 590, Short.MAX_VALUE)
                .addContainerGap())
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 654, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 320, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(jLabel2)
                    .add(jScrollPane2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(actionsPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jButton1)))
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
                logger.log(Level.SEVERE, null, ex);
            } catch (SAXException ex) {
                logger.log(Level.SEVERE, null, ex);
            } catch (FileNotFoundException ex) {
                logger.log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            } finally {
                try {
                    fo.close();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
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
                logger.log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            } finally {
                try {
                    fo.close();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
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
    // End of variables declaration//GEN-END:variables
}
