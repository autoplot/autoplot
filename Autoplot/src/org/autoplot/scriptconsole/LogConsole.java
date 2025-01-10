/*
 * LogConsole.java
 *
 * Created on June 19, 2008, 4:08 PM
 */
package org.autoplot.scriptconsole;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.xml.parsers.ParserConfigurationException;
import org.autoplot.jythonsupport.JythonRefactory;
import org.das2.jythoncompletion.JythonCompletionTask;
import org.das2.jythoncompletion.JythonInterpreterProvider;
import org.das2.jythoncompletion.ui.CompletionImpl;
import org.das2.system.RequestProcessor;
import org.das2.util.LoggerManager;
import org.python.core.PyException;
import org.python.core.PyNone;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import org.autoplot.GuiSupport;
import org.autoplot.JythonUtil;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.help.Util;
import org.autoplot.jythonsupport.ui.EditorTextPane;
import org.autoplot.util.TickleTimer;
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

    public static final int RECORD_SIZE_LIMIT = 4000;
    List<LogRecord> records = new LinkedList<>();
    int eventThreadId = -1;
    int level = Level.ALL.intValue(); // let each logger do the filtering now.
    boolean showLoggerId = false;
    boolean showTimeStamps = false;
    boolean showLevel = false;
    boolean showThreads = false;
    
    NumberFormat nf = new DecimalFormat("00.000");
    private Timer timer2;
    PrintStream oldStdOut;
    PrintStream oldStdErr;
    PythonInterpreter interp = null;

    /** Creates new form LogConsole */
    public LogConsole() {
        initComponents(); 
        
        String f= AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA );
        File config= new File( new File(f), "config" );
        Properties p= new Properties();
        if ( config.exists() ) {
            try {
                File syntaxPropertiesFile= new File( config, "jsyntaxpane.properties" );
                logger.log(Level.FINE, "Resetting editor colors using {0}", syntaxPropertiesFile );
                if ( syntaxPropertiesFile.exists() ) {
                    try ( FileInputStream in = new FileInputStream( syntaxPropertiesFile ) ) {
                        p.load( in );
                    }
                }
            } catch (FileNotFoundException ex) {
                logger.log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
            logTextArea.setBackground( Color.decode( p.getProperty("Background", "0xFFFFFF") ) );
            String foreground= p.getProperty("Style.DEFAULT", "0x000000");
            int i= foreground.indexOf(",");
            if ( i>-1 ) {
                foreground= foreground.substring(0,i);
            }
            logTextArea.setForeground( Color.decode( foreground ) );
        }


        commandLineTextPane1.addActionListener((ActionEvent e) -> {
            LoggerManager.logGuiEvent(e);
            final String s = commandLineTextPane1.getText();
            RequestProcessor.invokeLater(() -> {
                try {
                    String s1= maybeRemovePrompts(s);
                    s1= JythonRefactory.fixImports(s1);
                    System.out.println("AP> " + s1);
                    maybeInitializeInterpreter();
                    try {
                        PyObject po= interp.eval(s1);
                        if ( !( po instanceof PyNone ) ) interp.exec("print '" + po.__str__() +"'" ); // JythonRefactory okay
                    } catch (PyException ex ) {
                        interp.exec(s1);// JythonRefactory okay
                    }
                    commandLineTextPane1.setText("");
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                    commandLineTextPane1.setText("");
                } catch (PyException ex) {
                    System.err.println(ex.toString());
                    commandLineTextPane1.setText("");
                }
            });
        });

        
        this.commandLineTextPane1.putClientProperty(JythonCompletionTask.CLIENT_PROPERTY_INTERPRETER_PROVIDER, new JythonInterpreterProvider() {
            @Override
            public PythonInterpreter createInterpreter() throws java.io.IOException {
                maybeInitializeInterpreter();
                return interp;
            }
        });
        
        this.logTextArea.addMouseListener( new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if ( e.getButton()==MouseEvent.BUTTON1 && e.getClickCount()==2 && e.isShiftDown() ) {
                    int caret= logTextArea.viewToModel( new Point( e.getX(), e.getY() ) );
                    try {
                        String word= org.das2.jythoncompletion.Utilities.getWordAt( logTextArea, caret );
                        if ( word.startsWith("http:") || word.startsWith("https:") ) {
                            Util.openBrowser(word);
                        }
                    } catch (BadLocationException ex) {
                        Logger.getLogger(LogConsole.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        } );

        timer2 = new Timer(300, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if ( LogConsole.this.isShowing() ) {
                    update();
                } else {
                    timer2.restart();
                }
            }
        });
        timer2.setRepeats(false);

        final javax.swing.JTextPane ftxt= this.logTextArea;

        this.logTextArea.getActionMap().put( "biggerFont", new AbstractAction( "Text Size Bigger" ) {
            @Override
            public void actionPerformed( ActionEvent e ) {
               Font f= ftxt.getFont();
               float size= f.getSize2D();
               float step= size < 14 ? 1 : 2;
               ftxt.setFont( f.deriveFont( Math.min( 40, size + step ) ) );
            }
        } );

        this.logTextArea.getActionMap().put( "smallerFont", new AbstractAction( "Text Size Smaller" ) {
            @Override
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

    /**
     * remove prompts which are sometimes copied into mouse buffers.
     * This detects "AP> ", ">>> ", and "... ".
     * @param s the text entered.
     * @return the text without prefix.
     */
    public static String maybeRemovePrompts( String s ) {
        String[] ss= s.split("\n",-2);
        for ( int i=0; i<ss.length; i++ ) {
            String s1= ss[i];
            if ( s1.startsWith("AP> ") ) {
                ss[i]= s1.substring(4);
            } else if ( s1.startsWith(">>> ") ) {
                ss[i]= s1.substring(4);
            } else if ( s1.startsWith("... ") ) {
                ss[i]= s1.substring(4);
            }
        }
        return String.join( "\n", ss );
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
    
    Pattern searchTextPattern = null;
            
    protected String searchText = "";
    public static final String PROP_SEARCHTEXT = "searchText";

    public String getSearchText() {
        return searchText;
    }
    
    private Level logStatus = Level.ALL;

    /**
     * status is the highest LOG Level seen in the past 300ms.
     */
    public static final String PROP_LOGSTATUS = "logStatus";

    public Level getLogStatus() {
        return logStatus;
    }

    private boolean isRegex( String s ) {
        try {
            Pattern.compile(s);
            return true;
        } catch ( PatternSyntaxException ex ) {
            return false;
        }
    }
    
    public void setSearchText(String searchText) {
        String oldSearchText = this.searchText;
        this.searchText = searchText;
        if ( searchText != null && searchText.length()>0 ) {
            try {
                if ( isRegex( searchText ) ) {
                    searchTextPattern = Pattern.compile(searchText);
                    
                } else {
                    searchTextPattern = Pattern.compile(Pattern.quote(searchText));
                }
            } catch ( PatternSyntaxException ex ) {
                //searchTextPattern = Pattern.compile(Pattern.quote(searchText));
            }
        } else {
            searchTextPattern = null;
        }
        logTextArea.setToolTipText(null);
        apLabel.setToolTipText(null);
        update();
        firePropertyChange(PROP_SEARCHTEXT, oldSearchText, searchText);
    }

    private boolean showOnlyHighlited = false;

    public static final String PROP_SHOWONLYHIGHLITED = "showOnlyHighlited";

    public boolean isShowOnlyHighlited() {
        return showOnlyHighlited;
    }

    public void setShowOnlyHighlited(boolean showOnlyHighlited) {
        boolean oldShowOnlyHighlited = this.showOnlyHighlited;
        this.showOnlyHighlited = showOnlyHighlited;
        update();
        firePropertyChange(PROP_SHOWONLYHIGHLITED, oldShowOnlyHighlited, showOnlyHighlited);
    }

    

    public void setShowLoggerId(boolean selected) {
        this.showLoggerId= selected;
    }

    public void setShowTimeStamps( boolean selected ) {
        this.showTimeStamps= selected;
    }


    public void setShowLevel( boolean selected ) {
        this.showLevel= selected;
    }

    public void setLevel( int level ) {
        this.level= level;
    }

    public void setShowThreads(boolean selected) {
        this.showThreads= selected;
    }
    
    protected JTextPane getLogTextArea() {
        return this.logTextArea;
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

    private LogConsoleSettingsDialog getSettingsDialog() {
        LogConsoleSettingsDialog settingsDialog = new LogConsoleSettingsDialog( GuiSupport.getFrameForComponent(this), true, this);
        return settingsDialog;
    }

    private volatile Handler handler;

    private Handler newHandler() {
        return new Handler() {
                @Override
                public void publish(LogRecord rec) {
                    Object[] parms= rec.getParameters();

                    String recMsg;
                    String rm1= rec.getMessage();
                    if ( rm1!=null ) {
                        switch (rec.getMessage()) {
                            case "ENTRY {0}":
                                recMsg= "ENTRY " + rec.getSourceClassName() + "." +rec.getSourceMethodName() + " {0}";
                                break;
                            case "ENTRY":
                                recMsg= "ENTRY " + rec.getSourceClassName() + "." +rec.getSourceMethodName();
                                break;
                            case "RETURN {0}":
                                recMsg= "RETURN " + rec.getSourceClassName() + "." +rec.getSourceMethodName() + " {0}";
                                break;
                            case "RETURN":
                                recMsg= "RETURN " + rec.getSourceClassName() + "." +rec.getSourceMethodName();
                                break;
                            default:
                                recMsg = rec.getMessage();
                                break;
                        }
                    } else {
                        recMsg= null;
                    }
                    
                    if ( parms!=null && parms.length>0 ) {
                        try {
                            recMsg = MessageFormat.format( recMsg, parms );
                        } catch ( NullPointerException ex ) {
                            recMsg= String.valueOf( rec.getMessage() ); //TODO: fix this log message! bug https://sourceforge.net/p/autoplot/bugs/1194/
                        }
                    }
                    if ( ( recMsg==null || recMsg.length()<4 ) && rec.getThrown()!=null ) {
                        recMsg= rec.getThrown().toString();
                        //TODO: consider if "debug" property should be set instead.  Also it would be nice to digest this for jython errors.
                        rec.getThrown().printStackTrace();
                        // This is interesting--I've wondered where the single-line-message items have been coming from...
                    } else {
                        // no message.  
                        int i=0;
                    }
                    
                    if ( consoleListeners.size()>0 && recMsg!=null ) {
                        ActionEvent actionEvent= new ActionEvent( this, recMsg.hashCode(), recMsg );
                        consoleListeners.forEach((al) -> {
                            al.actionPerformed(actionEvent);
                        });
                    }
                    
                    if ( searchTextPattern!=null && searchTextPattern.matcher(recMsg).find() ) {
                        // secret feature that the stack trace of the last highlited text will be 
                        // shown as a tooltip of the "AP>" prompt.
                        ByteArrayOutputStream baos= new ByteArrayOutputStream();
                        try (PrintWriter pw = new PrintWriter(baos)) {
                            new Exception().printStackTrace(pw);
                        }
                        try {
                            String s= (baos.toString("US-ASCII"));
                            String[] ss= s.split("\n");
                            StringBuilder sb= new StringBuilder("<html><b>Stack trace at last highlite match:</b>");
                            for ( String s1: ss ) {
                                sb.append(s1).append("<br>");
                            }
                            sb.append("</html>");
                            String msg= sb.toString();
                            apLabel.setToolTipText(msg); // Shh! Secret feature...
                        } catch (UnsupportedEncodingException ex) {
                            logger.log( Level.WARNING, ex.getMessage(), ex );
                        }
                        LogConsoleUtil.checkBreakpoint(); // put a breakpoint in this code to stop.
                    }
                    LogRecord copy= new LogRecord( rec.getLevel(), recMsg ); //bug 3479791: just flatten this, so we don't have to format it each time
                    copy.setLoggerName(rec.getLoggerName());
                    copy.setMillis(rec.getMillis());
                    synchronized (LogConsole.this) {
                        records.add(copy);
                        while (records.size() > RECORD_SIZE_LIMIT) {
                            records.remove(0);
                        }
                        timer2.restart();
                        if (eventThreadId == -1 && EventQueue.isDispatchThread()) {
                            eventThreadId = rec.getThreadID();
                        }
                    }
                    if (rec.getLevel().intValue() >= Level.WARNING.intValue()) {
                        if (LogConsole.this.oldStdErr != null) {
                            String recMsg1;
                            String msg= rec.getMessage();
                            if ( parms==null || parms.length==0 ) {
                                recMsg1 = msg;
                            } else {
                                recMsg1 = MessageFormat.format( msg, parms );
                            }
                            LogConsole.this.oldStdErr.println( recMsg1 );
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
        }
    
    /**
     * create a handler that listens for log messages.  This handler is added
     * to the Loggers that should be displayed here.  Also, the log levels of
     * the Loggers should be set to ALL, since the filtering is done here.
     * For example:
     * <blockquote><pre><small>{@code
     *    Handler h = lc.getHandler();
     *    Logger.getLogger("autoplot").setLevel(Level.ALL);
     *    Logger.getLogger("autoplot").addHandler(h);
     *}</small></pre></blockquote>
     * @return handler for receiving messages.
     */
    public Handler getHandler() {
        Handler h = this.handler;
        if ( h==null ) {
            synchronized ( this ) {
                h = this.handler;
                if ( h==null ) {
                    h = newHandler();
                    this.handler = h;
                    h.setLevel(Level.ALL);
                }
            }
        }
        return h;
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
    public void logConsoleMessages() {
        Logger llogger;
        LoggingOutputStream los;

        if (alreadyLoggingStdout) {
            System.err.println("already logging stdout and stderr");
            return;
        } else {
            alreadyLoggingStdout = true;
        }

        llogger = org.das2.util.LoggerManager.getLogger("console.stdout");
        los = new LoggingOutputStream(llogger, Level.INFO);
        oldStdOut = System.out;
        System.setOut(new PrintStream(los, true));

        llogger = org.das2.util.LoggerManager.getLogger("console.stderr");
        los = new LoggingOutputStream(llogger, Level.WARNING);
        oldStdErr = System.err;
        System.setErr(new PrintStream(los, true));
    }

    /**
     * remove this hook for listening to stdout and stderr messages.
     */
    public void undoLogConsoleMessages() {
        if ( oldStdOut!=null ) 
            System.setOut(oldStdOut);
        if ( oldStdErr!=null )
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

    Map<String,Long> entryTimes= new HashMap<>();    
    
    private String getRecMsg( long baseTime, LogRecord rec ) {
        
        long t= baseTime;
        boolean timeStamps = showTimeStamps;
        boolean logLevels = showLevel;
        boolean threads= showThreads;

        String recMsg;
        String msg= rec.getMessage();
        Object[] parms= rec.getParameters();
        if ( parms==null || parms.length==0 ) {
            recMsg = msg;
        } else {
            recMsg = MessageFormat.format( msg, parms );
        }
        if ( recMsg==null ) {
            // recMessage was null, but we don't dare log this.
            recMsg="null";  //  I see this when profiling.
        }
        if ( recMsg.startsWith("ENTRY ") ) {
            entryTimes.put(recMsg.substring(6),rec.getMillis());
        } else if ( recMsg.startsWith("RETURN ") ) {
            Long t1= entryTimes.remove( recMsg.substring(7) );
            if ( t1!=null ) {
                recMsg= recMsg+" ("+(rec.getMillis()-t1)+"ms)";
            }
        }
        String prefix = "";
        if ( rec.getMessage()==null ) {
            if ( rec.getThrown()!=null ) {
                recMsg= recMsg + rec.getThrown().toString();
            }
            prefix= prefix+";";
        }
        if (showLoggerId) {
            prefix += rec.getLoggerName() + " ";
        }
        if (timeStamps) {
            prefix += nf.format(( t - rec.getMillis() ) / 1000.) + " "; // the minus sign was unproductive and messed up the formatting. 
        }
        if (logLevels) {
            prefix += rec.getLevel() + " ";
        }
        if (threads) {
            if (rec.getThreadID() == eventThreadId) { 
                prefix += "(GUI) ";
            } else {
                prefix += rec.getThreadID();
            }
        } 
        if (!prefix.equals("")) {
            recMsg = prefix.trim() + ": " + recMsg;
        }
        return recMsg;
    }
    
    private TickleTimer logStatusTimer= new TickleTimer(1000,new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            Level oldLogStatus= logStatus;
            logStatus= Level.ALL;
            firePropertyChange( PROP_LOGSTATUS, oldLogStatus, logStatus );
        }
    } );
    
    /**
     * note this is generally called from a timer that coalesces events.  But
     * may be called explicitly in response to a user event as well.  
     * This should be called on the event thread!
     */
    public void update() {
        List<LogRecord> lrecords;
        synchronized ( this ) {
            lrecords= new ArrayList<>(records);
        }
        try {
            //long t0= System.currentTimeMillis();
            int n = lrecords.size();
            String st = searchText;
            if (st != null && st.length() == 0) st = null;
            Pattern p = searchTextPattern;
            StyledDocument doc = logTextArea.getStyledDocument();
            doc.remove(0, doc.getLength());
            //long lastT = 0; // this was used to indicate a pause in processing, but it was confusing more than anything else.

            MutableAttributeSet highlistAttr = new SimpleAttributeSet();
            StyleConstants.setBackground(highlistAttr, Color.ORANGE);
            
            long t = n == 0 ? 0 : lrecords.get(n-1).getMillis();
            
            entryTimes= new HashMap<>();    
            
            for (LogRecord rec : lrecords) {
                if (rec.getLevel().intValue() >= level) {
//                    if (lastT != 0 && rec.getMillis() - lastT > 5000) { // TODO replace this with a GUI element, like a divider line on the right.
//                        //buf.append("\n");
//                        doc.insertString(doc.getLength(), "\n", null);
//                    }
                    //lastT = rec.getMillis();
                    
                    if ( getLogStatus().intValue()<rec.getLevel().intValue() ) {
                        Level oldLogStatus= logStatus;
                        logStatus= rec.getLevel();
                        logStatusTimer.tickle();
                        firePropertyChange( PROP_LOGSTATUS, oldLogStatus, logStatus );
                    }
                    
                    String recMsg= getRecMsg(t,rec);
                    
                    AttributeSet attr = null;
                    if (st != null && p!=null && p.matcher(recMsg).find()) {
                        attr = highlistAttr;
                    }
                    try {
                        //buf.append(recMsg).append("\n");
                        recMsg += "\n";
                        if ( showOnlyHighlited && st!=null ) {
                            if ( attr!=null ) {
                                doc.insertString( doc.getLength(), recMsg, null );
                            }
                        } else {
                            doc.insertString(doc.getLength(), recMsg, attr); // There's a deadlock here.   
                        }
                    } catch (BadLocationException ex) {
                        logger.log(Level.SEVERE, ex.getMessage(), ex);
                    }
                }
            }        
            
        } catch (BadLocationException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
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
        apLabel = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        commandLineTextPane1 = new org.autoplot.scriptconsole.CommandLineTextPane();
        jScrollPane1 = new javax.swing.JScrollPane();
        logTextArea = new javax.swing.JTextPane() {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return getUI().getPreferredSize(this).width
                <= getParent().getSize().width;
            }
        };
        jButton1 = new javax.swing.JButton();

        clearButton.setText("Clear");
        clearButton.setToolTipText("clear all messages.  ");
        clearButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearButtonActionPerformed(evt);
            }
        });

        saveButton.setText("Save As...");
        saveButton.setToolTipText("saves the records to file for use by software support team.  (Ctrl+ will load in previously externally saved records.)");
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

        apLabel.setText("AP>");

        commandLineTextPane1.setToolTipText("enter jython commands here to control the application, for example \"plot(dataset([1,2,3]))\"");
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
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 560, Short.MAX_VALUE)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(actionsPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 159, Short.MAX_VALUE)
                        .add(jButton1))
                    .add(layout.createSequentialGroup()
                        .addContainerGap()
                        .add(apLabel)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jScrollPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 484, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 319, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(8, 8, 8)
                        .add(apLabel))
                    .add(jScrollPane2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(actionsPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jButton1)))
        );
    }// </editor-fold>//GEN-END:initComponents

private void clearButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearButtonActionPerformed
    LoggerManager.logGuiEvent(evt);        
    records.clear();
    update();
}//GEN-LAST:event_clearButtonActionPerformed

private void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveButtonActionPerformed
    LoggerManager.logGuiEvent(evt);        
    if ((evt.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK) {
        JFileChooser chooser = new JFileChooser();
        chooser.addChoosableFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.toString().endsWith(".xml");
            }
            @Override
            public String getDescription() {
                return "xml files";
            }
        });
        if (JFileChooser.APPROVE_OPTION == chooser.showOpenDialog(this)) {
            FileInputStream fo = null;
            try {
                fo = new FileInputStream(chooser.getSelectedFile());
                records = LogConsoleUtil.deserializeLogRecords(fo);
            } catch (ParserConfigurationException | SAXException | IOException ex ) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            } finally {
                try {
                    if ( fo!=null ) fo.close();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
        }
    } else {
        JFileChooser chooser = new JFileChooser();
        chooser.addChoosableFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.toString().endsWith(".xml") || f.toString().endsWith(".txt");
            }
            @Override
            public String getDescription() {
                return "xml files or txt files";
            }
        });
        if (JFileChooser.APPROVE_OPTION == chooser.showSaveDialog(this)) {
            File f= chooser.getSelectedFile();            
            List<LogRecord> copy= new ArrayList(records);
            try ( FileOutputStream fo=  new FileOutputStream(chooser.getSelectedFile()) ) {
                if ( f.toString().endsWith(".xml") ) {
                    LogConsoleUtil.serializeLogRecords(copy, fo);
                } else {
                    try ( BufferedWriter write= new BufferedWriter( new OutputStreamWriter(fo) ) ) {
                        if ( copy.size()>0 ) {
                            long t= copy.get(copy.size()-1).getMillis();
                            for ( LogRecord rec: copy ) {
                                if (rec.getLevel().intValue() >= level) {        
                                    String recMsg= getRecMsg(t,rec);
                                    recMsg += "\n";
                                    write.write( recMsg );
                                }
                            }
                        }
                    }
                }
            } catch (FileNotFoundException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
    }
}//GEN-LAST:event_saveButtonActionPerformed

private void copyButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyButtonActionPerformed
    LoggerManager.logGuiEvent(evt);        
    try {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1000);
        LogConsoleUtil.serializeLogRecords(records, out);
        out.close();
        StringSelection stringSelection = new StringSelection(out.toString());
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, new ClipboardOwner() {
            @Override
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
    LoggerManager.logGuiEvent(evt);        
    getSettingsDialog().setVisible(true);
}//GEN-LAST:event_jButton1ActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel actionsPanel;
    private javax.swing.JLabel apLabel;
    private javax.swing.JButton clearButton;
    private org.autoplot.scriptconsole.CommandLineTextPane commandLineTextPane1;
    private javax.swing.JButton copyButton;
    private javax.swing.JButton jButton1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTextPane logTextArea;
    private javax.swing.JButton saveButton;
    // End of variables declaration//GEN-END:variables

    private List<ActionListener> consoleListeners= new ArrayList<>();
    
    /**
     * add method for listening to the console messages.  Note this
     * may change!
     * @param listener 
     */
    public void addConsoleListener( ActionListener listener ) {
        this.consoleListeners.add( listener );
    }
}
