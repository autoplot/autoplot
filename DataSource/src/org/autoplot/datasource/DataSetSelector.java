/*
 * DataSetSelector.java
 *
 * Created on November 5, 2007, 6:04 AM
 */
package org.autoplot.datasource;

import java.awt.AWTKeyStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.MouseEvent;
import org.das2.DasApplication;
import java.util.logging.Level;
import javax.swing.text.BadLocationException;
import org.das2.util.monitor.ProgressMonitor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BoundedRangeModel;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.DefaultEditorKit;
import org.das2.components.DasProgressPanel;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.DomainDivider;
import org.das2.datum.DomainDividerUtil;
import org.das2.datum.OrbitDatumRange;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.system.MonitorFactory;
import org.das2.system.RequestProcessor;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.autoplot.datasource.DataSetURI.CompletionResult;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.autoplot.datasource.ui.PromptComboBoxEditor;
import org.autoplot.datasource.ui.PromptTextField;
import org.das2.qds.ops.Ops;
import org.das2.util.filesystem.Glob;
import org.das2.util.monitor.AlertNullProgressMonitor;

/**
 * Swing component for selecting dataset URIs.  This provides hooks for completions.
 *
 * @author  jbf
 */
public class DataSetSelector extends javax.swing.JPanel {
    public static final String PROP_RECENT = "recent";
    private static final int MAX_RECENT=20;
    
    public static final Icon BUSY_ICON= new javax.swing.ImageIcon( DataSetSelector.class.getResource("/org/autoplot/aggregator/spinner_16.gif"));
    public static final Icon FILEMAG_ICON= new javax.swing.ImageIcon( DataSetSelector.class.getResource("/org/autoplot/datasource/fileMag.png"));
    public static final Icon FILEMAG_BUSY_ICON= new javax.swing.ImageIcon( DataSetSelector.class.getResource("/resources/fileMagGray.png"));
    
    private Map<Object,Object> pendingChanges= new HashMap(); // lockObject->Client
    
    private final String MESSAGE_RECENT= "(application will put recent items here)"; // warning: this is repeat code.
    
    /**
     * the edit (inspect) button has been pressed.
     */
    private final Object PENDING_EDIT="edit";
    
    /**
     * the go button has been pressed.
     */
    private final Object PENDING_GO="gobutton"; 
    
    /**
     * we are downloading resources so we can check reject.
     */
    private final Object PENDING_CHECKING_REJECT="checkingReject";
    
    /**
     * a plotDataSetURL is going to be fired off.
     */
    private final Object PENDING_PLOT="plot";
    
    /** Creates new form DataSetSelector */
    public DataSetSelector() {
        initComponents(); // of the 58milliseconds it takes to create the GUI, 52 are spent in here.
        dataSetSelectorComboBox.setEditor( new PromptComboBoxEditor("Enter data location") );
        plotItButton.setActionCommand("doplot");
        inspectButton.setActionCommand("inspect");
        
        editor = ((JTextField) dataSetSelectorComboBox.getEditor().getEditorComponent());        
        dataSetSelectorComboBox.addActionListener( new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                keyModifiers= ev.getModifiers();
                
            }
        });

        // macs have an annoying feature that macs select everything when focus is gained.
        // I would often loose all the text I'd typed in as I used the completions.
        // http://www.coderanch.com/t/488520/GUI/java/JTextField-highlighted-windows
        editor.addFocusListener( new FocusListener() {

            @Override
            public void focusGained(FocusEvent e) {
                int i= editor.getCaretPosition();
                editor.setSelectionStart(i);
                editor.setSelectionEnd(i);
            }

            @Override
            public void focusLost(FocusEvent e) {
                        
            }
        });
        
        addCompletionKeys();
        addAbouts();
        
        maybePlotTimer = new Timer(100, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                // some DataSource constructors do not return in interactive time, so create a new thread for now.
                LoggerManager.logGuiEvent(ev);
                try {
                    maybePlotImmediately();
                } finally {
                    pendingChanges.remove( PENDING_GO );
                }
            }
        });
        
        maybePlotTimer.setActionCommand("maybePlot");
        maybePlotTimer.setRepeats(false);

        editor.addMouseListener( new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                if ( e.isPopupTrigger() ) showPopup(e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if ( e.isPopupTrigger() ) showPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if ( e.isPopupTrigger() ) showPopup(e);
            }

        });
    }
    
    boolean needToAddKeys = true;
    /**
     * current completions task
     */
    Runnable completionsRunnable = null;
    ProgressMonitor completionsMonitor = null;
    JPopupMenu completionsPopupMenu = null;
    private JTextField editor;
    DataSetSelectorSupport support = new DataSetSelectorSupport(this);
    public static final String PROPERTY_MESSAGE = "message";
    static final Logger logger = LoggerManager.getLogger("apdss.gui.dss");
    MonitorFactory monitorFactory = null;
    Timer maybePlotTimer;
    int keyModifiers = 0;
    
    boolean playButton= true; // for popups, if false just allow okay/cancel

    /**
     * provide direct access to the editor component.
     * This should not be used, because it makes it more difficult to 
     * control and define the state.  
     * Use this to add listeners for example, but do not modify the value.
     * @return the text editor
     */
    public JTextField getEditor() {
        return editor;
    }

    /**
     * automated GUI testing needs access to subcomponents.
     * This provides access to the green play button that when pressed fires off a "go" event
     * @return the green go button
     */
    public JButton getGoButton() {
        return plotItButton;
    }

    /**
     * if this is false, then the editor dialogs will show only "okay" and
     * "plot below" is hidden.
     * @param t 
     */
    public void setPlayButton( boolean t ) {
        this.playButton= t;
    }
    
    /**
     * provide access to timer for GUI testing.
     * @return
     */
    public boolean isPendingChanges() {
        return maybePlotTimer.isRunning() || !pendingChanges.isEmpty();
    }

    /**
     * automated GUI testing needs access to subcomponents.
     * This provides access to the inspect/browse button that when pressed enters a GUI editor to graphically work on the URI.
     * @return the inspect/browse button
     */
    public JButton getBrowseButton() {
        return inspectButton;
    }

    private ProgressMonitor getMonitor() {
        return getMonitor("Please Wait", "unidentified task in progress");
    }

    private ProgressMonitor getMonitor(String label, String desc) {
        Window window= SwingUtilities.getWindowAncestor( this );
        return getMonitor( label, desc, window );
    }
    
    private ProgressMonitor getMonitor(String label, String desc, Window window ) {
        if (monitorFactory == null) {
            ProgressMonitor mon= DasApplication.getDefaultApplication().getMonitorFactory().getMonitor(label, desc);
            DasProgressPanel.maybeCenter( mon, window );
            return mon;
        } else {
            ProgressMonitor mon= monitorFactory.getMonitor(label, desc);
            DasProgressPanel.maybeCenter( mon, window );
            return mon;
        }
    }    

    public void setMonitorFactory(MonitorFactory factory) {
        this.monitorFactory = factory;
    }

    private void showPluginsScreen() {
        StringBuilder msg= new StringBuilder();
        msg.append("<html>Unable to use the address <br><br>").append(getValue())
                .append("<br><br>Qualify the address by adding a supported plugin id (e.g. vap+dat:)<br>")
                .append("or use an address that matches one of these triggers:<br><br>");
        for ( String at: actionTriggers.keySet() ) {
            msg.append( at ).append( "<br>" );
        }
        msg.append( "</html>" );

        JOptionPane.showMessageDialog( this, msg, "Unrecognized address", JOptionPane.OK_OPTION );

    }
    
    /**
     * check to see if any of the action triggers will handle the URI.
     * @param suri the URI.
     * @return  true if the URI was handled.
     */
    private boolean checkActionTriggers( String suri ) {
        for ( Entry<String,Action> e: actionTriggers.entrySet()) {
            String actionTriggerRegex= e.getKey();
            if (Pattern.matches(actionTriggerRegex, suri)) {
                logger.finest("matches action trigger");
                Action action = e.getValue();
                action.actionPerformed(new ActionEvent(this, 123, "dataSetSelect"));
                return true;
            }
        }
        return false;
    }
    
    /**
     * set the JTextField text on the event thread, and make sure that getText
     * calls would return the value.
     * @param text 
     */
    private void setTextInternal( final String text ) {
        Runnable run= new Runnable() {
            @Override
            public void run() {
                editor.setText( text );
            }
        };
        if ( SwingUtilities.isEventDispatchThread() ) {
            run.run();
        } else {
            SwingUtilities.invokeLater(run);
        }
        this.lastValue= text;
    }
    
    /**
     * return true if the action trigger would be handled.
     * @param suri the URI.
     * @return true if the action trigger would be handled.
     */
    public boolean hasActionTrigger( String suri ) {
        for (String actionTriggerRegex : actionTriggers.keySet()) {
            if (Pattern.matches(actionTriggerRegex, suri)) {
                logger.log(Level.FINEST, "matches action trigger: {0}", actionTriggerRegex);
                return true;
            }
        }
        return false;
    }

    private void maybePlotImmediatelyOffEvent( String surl ) {
        logger.log(Level.FINE, "maybePlotImmediatelyOffEvent( {0} )", surl);
        Pattern accept= acceptPattern==null ? null : Pattern.compile(acceptPattern);

        if ( !enableDataSource && ( accept==null || accept.matcher(surl).matches() ) ) { // just fire off an event, don't validate it or do completions.
            firePlotDataSetURL();
            return;
        }

        String ext= DataSetURI.getExt(surl);
        if ( ext!=null && ext.equals("vap") ) {
            firePlotDataSetURL();
            return;
        }

        if ( surl.contains(".vap&") && !surl.contains("?") ) {
            JOptionPane.showMessageDialog( getEditor(), "URI contains \".vap&\" and no ?, try \".vap?\"..." );
            return;
        }
        
        URISplit split= URISplit.parse(surl);

        String file= split.file;
        if ( file==null || file.equals("file:///") ) file="";  //TODO: kludge around bug in URISplit.

        try {

            if (file.endsWith("/") || file.contains("/?") 
                || ( file.endsWith(".zip") || file.contains(".zip?") || file.endsWith(".ZIP") || surl.contains(".ZIP?") )
                || ( file.endsWith(".tgz") || file.contains(".tgz?") || file.endsWith(".tar") || surl.contains(".tar?") || file.endsWith(".tar.gz") || surl.contains(".tar.gz?") ) ) { 
                //TODO: vap+cdaweb:file:///?ds=APOLLO12_SWS_1HR&id=NSpectra_1  should not reject if empty file?
                int carotpos = editor.getCaretPosition();
                setMessage("busy: getting filesystem completions.");
                showCompletions(surl, carotpos);
                
            } else if (file.endsWith("/..")) { // pop up one directory
                logger.fine("jump to parent directory");
                int carotpos = surl.lastIndexOf("/..");
                carotpos = surl.lastIndexOf('/', carotpos - 1);
                if (carotpos != -1) {
                    String sval= surl.substring(0, carotpos + 1);
                    dataSetSelectorComboBox.getEditor().setItem(sval);
                    dataSetSelectorComboBox.setSelectedItem(sval);
                    editor.setCaretPosition(carotpos+1);
                    setTextInternal(sval);
                    maybePlotImmediately();
                }
            } else {
                try {
                    //TODO: "plot http://autoplot.org/data.dat" has terrible feedback.
                    URI uri= DataSetURI.getURI(surl);
                    if ( uri==null ) {
                        setMessage("error: URI cannot be formed from \""+surl+"\"");
                        return;
                    }
                    DataSourceFactory f = DataSetURI.getDataSourceFactory(uri, getMonitor( "get factory", "get factory" ) );
                    if (f == null) {
                        SourceTypesBrowser browser= new SourceTypesBrowser();
                        URI resourceURI= DataSetURI.getResourceURI(surl);
                        if ( resourceURI==null ) {
                            showPluginsScreen();
                            return;
                        }
                        browser.getDataSetSelector().setValue(DataSetURI.fromUri(resourceURI));
                        int r= JOptionPane.showConfirmDialog(this, browser,"Select Data Source Type",JOptionPane.OK_CANCEL_OPTION);
                        if ( r==JOptionPane.OK_OPTION ) {
                            surl= browser.getUri();
                            setTextInternal(surl);
                            setValue(surl);
                            maybePlot(true);
                            return;
                        } else {
                            showPluginsScreen();
                            return;
                        }

                    }
                    
                    // dascat: When we first open the catalog we don't know if any particular
                    // source will have the time series browse property until we load the source
                    // description itself. --cwp
                    TimeSeriesBrowse tsb= f.getCapability( TimeSeriesBrowse.class ); // https://sourceforge.net/p/autoplot/bugs/1518/
                    if ( false ) { // TODO: experiment more with this code
                    if ( tsb!=null ) {
                        if ( timeRange!=null && UnitsUtil.isTimeLocation( timeRange.getUnits() ) && !timeRange.equals(DataSourceUtil.DEFAULT_TIME_RANGE) ) {
                            DatumRange newTr= tsb.getTimeRange();
                            if ( newTr!=null ) {
                                logger.fine("resetting TSB timeRange to URI range");
                                timeRange= tsb.getTimeRange();
                            }
                        }    
                    }
                    }
                    
                    String tsbProblem= "";
                    if ( tsb!=null ) {
                        try {
                            tsb.setURI(surl);
                            DatumRange tr= tsb.getTimeRange();
                            if ( tr!=null && tr.width().le(Units.seconds.createDatum(0) ) ) {
                                tsbProblem= "<html>Unable to parse timerange in <br>"+surl+"<br>See http://autoplot.org/help#Time_Parsing_.2F_Formatting";
                            }
                        } catch (ParseException | ArrayIndexOutOfBoundsException ex) {
                            tsbProblem= "<html>Unable to parse: "+surl+"<br>See http://autoplot.org/help#Time_Parsing_.2F_Formatting";
                        }
                    }
                    if ( tsbProblem.length()>0 ) logger.warning(tsbProblem);
                    
                    setMessage("busy: checking to see if uri looks acceptable");
                    
                    Window w= SwingUtilities.getWindowAncestor(this);
                    ProgressMonitor mon= getMonitor( "check URI", "check if URI is acceptable", w );
                    
                    //TODO: line up with parent.
                    List<String> problems= new ArrayList();
                    if (f.reject(surl, problems,mon)) { // This is the often-seen code that replaces the timerange in a URI. +#+#+
                        if ( tsb!=null ) {
                            if ( timeRange!=null && UnitsUtil.isTimeLocation( timeRange.getUnits() ) && !timeRange.equals(DataSourceUtil.DEFAULT_TIME_RANGE) ) {
                                try {
                                    tsb.setURI(surl);
                                    tsb.setTimeRange(timeRange);
                                    String suri= tsb.getURI();
                                    problems.remove( TimeSeriesBrowse.PROB_NO_TIMERANGE_PROVIDED ); 
                                    if ( !f.reject( suri, problems, mon) ) {
                                        setMessage("accepted uri after setting timerange");
                                        int modifiers= this.keyModifiers;
                                        setValue(suri);
                                        this.keyModifiers= modifiers;
                                        firePlotDataSetURL();
                                        return;    
                                    }
                                } catch ( ParseException | IllegalArgumentException ex ) {
                                    JOptionPane.showMessageDialog( plotItButton, ex.getMessage() );
                                    setMessage(ex.getMessage());  // $y$J would throw runtime exception.
                                    logger.log( Level.SEVERE, ex.getMessage(), ex );
                                    return;
                                }
                            }
                        }
                        setMessage("busy: uri rejected, inspecting resource for parameters");
                        browseSourceType(problems);
                    } else {
                        if ( mon.isCancelled() ) {
                            setMessage("download cancelled");
                            return;
                        }
                        boolean bug1098= true; // hold this change until next release.
                        if ( bug1098 ) {
                            if ( tsb!=null ) {
                                if ( timeRange!=null && !timeRange.equals(DataSourceUtil.DEFAULT_TIME_RANGE) && UnitsUtil.isTimeLocation( timeRange.getUnits() ) ) {
                                    try {
                                        tsb.setURI(surl);
                                        if ( tsb.getTimeRange()!=null && !timeRange.equals(tsb.getTimeRange() ) ) {
                                            timeRange= pickTimeRange( SwingUtilities.getWindowAncestor(this),
                                                    Arrays.asList( timeRange, tsb.getTimeRange() ),
                                                    Arrays.asList( "Current", "URI" ) );
                                            tsb.setTimeRange(timeRange);
                                        }
                                        String suri= tsb.getURI();
                                        //String suri= tsb.blurURI(); // this causes 1970-01-01 to pop up again...
                                        logger.log( Level.FINE, "resetting timerange to {0}", timeRange);
                                        setTextInternal(suri); // don't fire off event.
                                    } catch ( ParseException ex ) {
                                        logger.log( Level.SEVERE, ex.getMessage(), ex );
                                    }
                                }
                            }
                        } else {
                            logger.fine("bug1098 switch turned off, otherwise we would reset the timerange");
                        }
                        setMessage("busy: resolving uri to data set with plugin \"" + DataSourceRegistry.getInstance().describe(f,surl)+"\"");
                        firePlotDataSetURL();
                    }
                } catch (DataSetURI.NonResourceException ex) { // see if it's a folder.
                    int carotpos = surl.length();
                    setMessage("no extension or mime type, try filesystem completions");
                    showCompletions(surl, carotpos);
                } catch (IllegalArgumentException | URISyntaxException ex) {
                    setMessage(ex.getMessage());
                    logger.log( Level.SEVERE, ex.getMessage(), ex );
                    firePlotDataSetURL();
                }
            }
        } catch (IllegalArgumentException ex) {
            logger.log( Level.SEVERE, ex.getMessage(), ex );
            setMessage(ex.getMessage());
        } catch (IOException ex) {
            logger.log( Level.SEVERE, ex.getMessage(), ex );
            setMessage("warning: "+ex.getMessage());
        }
        
    }
    
    /**
     * See if we can resolve a plottable URI from the selector by identifying
     * the data source type and calling its reject method, or identifying the
     * file system type and getting completions on it.  The reject method
     * is used to identify URIs where we can assist in making it acceptable using
     * the "browseSourceType" method.
     *
     */
    private void maybePlotImmediately() {
        final String surl = getValue();
        logger.log(Level.FINE, "maybePlotImmediately({0})", surl);
        if (surl.equals("") ) { 
            logger.finest("empty value, returning");
            return;
        }

        if (surl.startsWith("vap+internal:")) {
            firePlotDataSetURL();
            return;
        }

        if ( checkActionTriggers(surl) ) {
            return;
        }
        
        Runnable run= new Runnable() {
            @Override
            public void run() {
                try {
                    maybePlotImmediatelyOffEvent( surl );
                } finally {
                    pendingChanges.remove( PENDING_CHECKING_REJECT );
                }
            }
        };
        
        pendingChanges.put( PENDING_CHECKING_REJECT, this );
        RequestProcessor.invokeLater(run);
        
    }

    /**
     * if the dataset requires parameters that aren't provided, then
     * show completion list.  Otherwise, fire off event.
     * @param allowModifiers turn off any modifiers like shift or control.
     */
    public void maybePlot(boolean allowModifiers) {
        logger.log(Level.FINE, "go {0}", getValue());

        if (!allowModifiers) {
            keyModifiers = 0;
        }
        
        pendingChanges.put( PENDING_GO, this );
        maybePlotTimer.restart();
    }

    /**
     * trigger a plot, allowing modifiers such as:
     *   0                    replace plot
     *   KeyEvent.CTRL_MASK   plot below
     *   KeyEvent.SHIFT_MASK  overplot
     *   KeyEvent.ALT_MASK    edit this URI.  (Only with recent history uses this for now, also bookmarks)
     * @param keyModifiers
     */
    public void maybePlot(int keyModifiers) {
        this.keyModifiers= keyModifiers;
        if ( (keyModifiers&KeyEvent.ALT_MASK ) == KeyEvent.ALT_MASK ) {
            browseSourceType();
        } else {
            maybePlot(true);
        }
    }

    /**
     * fire off the event that indicates a new URI has been entered.
     */
    private void firePlotDataSetURL() {
        pendingChanges.put( PENDING_PLOT, this );
        List<String> r = new ArrayList<>(getRecent());
        String value = getValue();
        lastValue= value;
        if (r.contains(value)) {
            r.remove(value); // move to top of the list by remove then add.
        }
        r.add(value);

        while ( r.size()>MAX_RECENT ) { // Note: this has no effect. TODO: why?
            r.remove(0);
        }

        SwingUtilities.invokeLater( new Runnable() {
            @Override
            public void run() {
                ActionEvent e = new ActionEvent(this, 123, "dataSetSelect", keyModifiers);
                fireActionListenerActionPerformed(e);
                pendingChanges.remove( PENDING_PLOT );
            }
        }  );
        
    }

    /**
     * there are two places in the code where FileNotFound messages 
     * are passed in with just the file name.  
     * @param msg the message, which might just be the filename.
     * @return the message clarified.
     */
    public static String maybeAddFileNotFound( String msg ) {
        if ( msg.startsWith("file:/") || msg.startsWith("http://") || msg.startsWith("https://" ) ) {  
            msg= FILE_NOT_FOUND + ": "+msg;
        }
        return msg;
    }
    
    public static final String FILE_NOT_FOUND= "File not found";
    
    /**
     * Some exceptions can be handled by the user, and the error needs to be 
     * communicated to them.  Typically this is going to present a friendlier
     * dialog to the user instead of the catch-all Runtime Exception Dialog.
     * @param ex
     * @return true if the exception was handled.
     */
    private boolean maybeHandleException(Exception ex) {
        String msg= ex.getMessage();
        if ( msg==null ) msg= ex.toString();
        msg= msg.trim();
        if ( ex instanceof FileNotFoundException && msg.length()==0 ) {
            msg= FILE_NOT_FOUND; // this may never happen, but to be sure...
        }
        if ( ( ex instanceof FileNotFoundException
                || ex.toString().contains("file not found")
                || ex.toString().contains("root does not exist") )
              && msg.length()>0 ) {
            msg= maybeAddFileNotFound(msg);
            setMessage(msg);
            if ( msg.startsWith( FILE_NOT_FOUND + ": ") ) {
                String[] ss= msg.split(":",2);
                msg= "<html>"+ss[0]+":<br>"+ss[1]+"</html>";
            }
            JOptionPane.showMessageDialog( DataSetSelector.this, msg, "No Such File", JOptionPane.WARNING_MESSAGE );
            return true;
        } else if ( ex instanceof HtmlResponseIOException ) {
            JPanel r= new javax.swing.JPanel( new BorderLayout() );
            final String link = ((HtmlResponseIOException)ex).getURL().toString();
            r.add( new JLabel( " " ), BorderLayout.NORTH );
            r.add( new JLabel( msg ) );
            JPanel se= new JPanel( new BorderLayout() );
            se.add( new JButton( new AbstractAction("View Page") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    org.das2.util.LoggerManager.logGuiEvent(e);
                    DataSourceUtil.openBrowser(link);
                }
            }), BorderLayout.EAST );
            r.add( se, BorderLayout.SOUTH );
            JOptionPane.showMessageDialog( DataSetSelector.this, r, "Content is HTML", JOptionPane.WARNING_MESSAGE );
            setMessage("" + ex.getMessage());
            return true;

        } else if ( ex instanceof EmptyFileException ) {
            JOptionPane.showMessageDialog( DataSetSelector.this, msg, "Empty File", JOptionPane.WARNING_MESSAGE );
            setMessage("" + ex.getMessage());
            return true;
        } else if ( ex instanceof UnrecognizedDataSourceException ) {
            JOptionPane.showMessageDialog( DataSetSelector.this, "<html>Unable to find data source plugin for:<br>"+msg, "Unrecognized data source", JOptionPane.WARNING_MESSAGE );
            setMessage("" + ex.getMessage());
            return true;
        }
        return false;
    }

    /**
     * round the timerange slightly to give a more human-friendly timerange that is equivalent.  DomainDividers are used
     * to find a nice time that could be divided into roughly 10 segments.
     * @param tr
     * @return
     */
    DatumRange quantizeTimeRange( DatumRange tr ) {
        DomainDivider dd= DomainDividerUtil.getDomainDivider(tr.min(),tr.max());
        while ( dd.boundaryCount(tr.min(),tr.max() )<50 ) {
            dd= dd.finerDivider(false);
        }
        while ( dd.boundaryCount(tr.min(),tr.max() )>200 ) {
            dd= dd.coarserDivider(false);
        }
        DatumRange maxR= dd.rangeContaining(tr.max());
        if ( maxR.min().equals(tr.max()) ) {
            return DatumRangeUtil.union( dd.rangeContaining(tr.min()), maxR.min() );
        } else {
            return DatumRangeUtil.union( dd.rangeContaining(tr.min()), maxR.max() );
        }
    }

    /**
     * show the initial parameters completions for the type, or the
     * editor, if that's available.
     * This can be called from the event thread.
     */
    public void browseSourceType() {
        browseSourceType( new ArrayList<String>() );
    }

    /**
     * show the initial parameters completions for the type, or the
     * editor, if that's available.
     * This can be called from the event thread.
     * @param problems we're entering this GUI because of problems with the URI, so mark these problems.  See DataSourceFactory.reject.
     */
    public void browseSourceType( final List<String> problems ) {
        String surl = ((String) dataSetSelectorComboBox.getEditor().getItem()).trim();

        logger.log(Level.FINE, "browseSourceType {0}", surl);
        
        // hooks for browsing, such as "vap+internal"
        for ( Entry<String,Action> e : browseTriggers.entrySet()) {
            String browseTriggerRegex = e.getKey();
            if (Pattern.matches(browseTriggerRegex, surl )) {
                logger.finest("matches browse trigger");
                Action action = e.getValue();
                action.actionPerformed( new ActionEvent(this, 123, "dataSetSelect") );
                return;
            }
        }
        
        if ( surl.startsWith("getDataSet(") || ( surl.startsWith("'") && surl.endsWith(")") ) ) {
            //TODO: repeated code
            //'https://spdf.gsfc.nasa.gov/pub/data/mms/mms1/fgm/srvy/l2/$Y/$m/mms1_fgm_srvy_l2_$Y$m$d_v$(v,sep).cdf?mms1_fgm_b_gsm_srvy_l2',tr)
            int i0= surl.indexOf("'");
            int i1= surl.lastIndexOf("'");
            if ( i1>surl.length()-20 ) {
                surl= surl.substring(i0+1,i1);
            } else {
                surl= surl.substring(i0+1);
            }
        }

        setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
        
        boolean wasRejected= false;
        DataSourceEditorPanel edit;
        try {
            edit = DataSourceEditorPanelUtil.getDataSourceEditorPanel(DataSetURI.getURIValid(surl));
            if ( edit!=null && edit.reject(surl) ) {
                // See https://sourceforge.net/p/autoplot/bugs/1729/
                //setCursor( Cursor.getDefaultCursor() );
                //JOptionPane.showMessageDialog( this, "<html>Unable to create editor for URI:<br>"+surl );
                //return;
                edit= null;
                wasRejected= true;          
                logger.log(Level.FINE, "wasRejected= true" );
            }
            if ( edit!=null ) {
                try {
                    Method m= edit.getClass().getDeclaredMethod( "setExpertMode", boolean.class );
                    m.invoke( edit, this.isExpertMode() );
                } catch ( NoSuchMethodException ex ) {
                    //logger.log( Level.SEVERE, "", ex ); //okay
                }
            } else {
                URISplit split= URISplit.parse(surl);
                if ( !".vap".equals(split.ext) ) { 
                    edit= new CompletionsDataSourceEditor();
                } else {
                    String result= DataSetSelectorSupport.browseLocalVap(this, surl);
                    if (result != null ) {
                        this.setValue(result);
                        this.maybePlot(false);
                    }
                    setCursor( Cursor.getDefaultCursor() );
                    return;
                }
            }
            
        } catch (URISyntaxException ex) {
            logger.log( Level.SEVERE, ex.getMessage(), ex );
            edit= null;
        } catch ( Exception ex ) {
            logger.log( Level.SEVERE, ex.getMessage(), ex );
            edit= null;
        }

        if (edit != null) {
            final Window window = SwingUtilities.getWindowAncestor(this);

            final DataSourceEditorPanel fedit= edit;
            final String fsurl= surl;

            if ( surl!=null && surl.startsWith("vap+internal:") ) {
                JOptionPane.showMessageDialog( window, "Internal URI cannot be edited" );
                setCursor( Cursor.getDefaultCursor() );
                return;
            }
            
            inspectButton.setIcon( FILEMAG_BUSY_ICON );
            inspectButton.setEnabled( false );
            
            Runnable run= new Runnable() {
                @Override
                public void run() {
                   String surl= fsurl;
                   if ( timeRange!=null && UnitsUtil.isTimeLocation(timeRange.getUnits()) ) {
                        try {
                            //For TSB capability, set the default value to the axis setting initially.  So here's the problem: to see if
                            // something has TSB, I need to a valid URI.  But I don't have a URI, that's why we are entering the editor.
                            // Let's kludge past this and add the capability to the editor...
                            DataSourceFactory dsf = DataSetURI.getDataSourceFactory( DataSetURI.getURI(surl), new NullProgressMonitor());
                            if ( dsf!=null ) {  //vap+internal:data_1,data_2
                                TimeSeriesBrowse tsb= dsf.getCapability( TimeSeriesBrowse.class );
                                if (tsb!=null && !timeRange.equals( DatumRangeUtil.parseTimeRangeValid("2010-01-01") ) ) { // TODO: nasty nasty kludge tries to avoid setting the time when it is arbitrary default time.
                                    tsb.setURI(surl);
                                    //DatumRange r= tsb.getTimeRange();
                                    //TODO: quantize timerange, so we don't get ranges with excessive resolution.  "vap+cdaweb:ds=AC_K0_SWE&id=Vp&timerange=2012-04-19+12:01+to+2012-04-20+00:01"
                                    //TODO: Chris pointed out this was causing him problems.  
                                    DatumRange tr2;
                                    if ( timeRange instanceof OrbitDatumRange ) {
                                        tr2= timeRange;
                                    } else {
                                        tr2= quantizeTimeRange( timeRange );
                                    }
                                    tsb.setTimeRange(tr2);
                                    surl= tsb.getURI();
                                }
                            }
                        } catch (ParseException | IOException | IllegalArgumentException | URISyntaxException ex ){
                            logger.log( Level.SEVERE, ex.getMessage(), ex );
                        }
                    }
                   
                    logger.log(Level.FINER, "browseSourceType after TSB {0}", surl);
                    
                    boolean proceed;
                    try {
                        ProgressMonitor mon= getMonitor("download file", "downloading file and preparing editor",window);
                        proceed = fedit.prepare(surl, window, mon );
                        if ( !proceed ) {
                            logger.finer("proceed=false");
                            clearBusyIcon();
                            setCursor( Cursor.getDefaultCursor() );
                            return;
                        }
                    } catch ( java.io.InterruptedIOException ex ) {
                        setMessage( "download cancelled" );  //TODO: check FTP
                        logger.finer("download cancelled");
                        clearBusyIcon();
                        setCursor( Cursor.getDefaultCursor() );
                        return;
                    } catch (Exception ex) {
                        logger.log(Level.FINER, "exception in prepare: {0}", ex.getMessage());
                        clearBusyIcon();
                        setCursor( Cursor.getDefaultCursor() );
                        if ( !maybeHandleException(ex) ) {
                            throw new RuntimeException(ex);
                        }                        
                        return;
                    }

                    try {
                        fedit.setURI(surl);
                    } catch ( Exception e ) {
                        e.printStackTrace();
                    }
                    
                    setCursor( Cursor.getDefaultCursor() );
                    
                    if ( surl.startsWith("vap+inline" ) ) {
                        // tiny kludge for Autoplot
                        if ( fedit.getClass().getName().equals("org.autoplot.inline.InlineDataSourceEditorPanel") ) {
                            if ( window.getClass().getName().equals("org.autoplot.AutoplotUI") ) {
                                //bug 2044: TODO: this should fire off events with the DataSourceEditor to make it available to Autoplot.
                            }
                        }
                    }
                    fedit.markProblems(problems);
                    
                    final String fsurl= surl;

                    Runnable run= getURIReviewDialog( fsurl, fedit, problems );
                    
                    SwingUtilities.invokeLater(run);

                }
            };
            Thread thread= new Thread(run);
            thread.start();

        } else {
            setCursor( Cursor.getDefaultCursor() );
            if ( !wasRejected ) {
                if (!surl.contains("?")) {
                    surl += "?";
                }
                setValue(surl);
            }
            
            int carotpos = surl.indexOf('?');
            if (carotpos == -1) {
                carotpos = surl.length();
            } else {
                carotpos += 1;
            }
            surl = surl.substring(0, carotpos);
            showCompletions(surl, carotpos);
        }
    }

    /**
     * add a listener for escape key press, which will setVisible(false) and
     * clear the dialog.
     * @param dialog 
     */
    public static void addCancelEscapeKey( final JDialog dialog ) {
        // add escape key listener to mean cancel.
        dialog.getRootPane().registerKeyboardAction( new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Component focus= dialog.getFocusOwner();
                if ( focus!=null && focus instanceof JTextField ) {
                    logger.finer("ignore escape pressed within a JTextField");
                    return;
                }
                if ( focus!=null && focus instanceof JRootPane ) {
                    logger.finer("ignore escape pressed within a JRootPane");
                    return;
                }
                dialog.setVisible(false);
                dialog.dispose(); 
            }
        }, KeyStroke.getKeyStroke( KeyEvent.VK_ESCAPE, 0 ), JComponent.WHEN_IN_FOCUSED_WINDOW );        
    }
    
    private Runnable getURIReviewDialog( final String fsurl, final DataSourceEditorPanel fedit, final List<String> problems ) {
        Runnable run= new Runnable() {
            @Override
            public void run() {
                try {
                    final DataSourceEditorDialog dialog;
                    Window window= SwingUtilities.getWindowAncestor(DataSetSelector.this); 
                    String title = "Editing URI " + fsurl;
                    if (window instanceof Frame) {
                        dialog = new DataSourceEditorDialog((Frame) window, fedit.getPanel(), true);
                    } else if (window instanceof Dialog) {  // TODO: Java 1.6 ModalityType.
                        dialog = new DataSourceEditorDialog((Dialog) window, fedit.getPanel(), true);
                    } else {
                        throw new RuntimeException("parent windowAncestor type is not supported.");
                    }
                    dialog.setTitle(title);
                    dialog.setProblems(problems);

                    if ( actionListenerList==null || actionListenerList.isEmpty() || playButton==false ) {
                        dialog.setPlayButton(false); // nothing is going to happen, so don't show play button.
                    } else {
                        dialog.setExpertMode(isExpertMode());
                    }

                    pendingChanges.put( PENDING_EDIT, DataSetSelector.this );

                    addCancelEscapeKey(dialog);

                    WindowManager.getInstance().showModalDialog(dialog);

                    if (!dialog.isCancelled()) {
                        String surl= fedit.getURI();                                
                        logger.log( Level.FINE, "dataSetSelectorComboBox.setSelectedItem(\"{0}\");", surl );
                        dataSetSelectorComboBox.setSelectedItem(surl);
                        logger.log( Level.FINE, "dataSetSelectorComboBox.getEditor().setItem(\"{0}\");", surl );
                        dataSetSelectorComboBox.getEditor().setItem(surl);

                        boolean bug1098= false; //TODO finish off this change.
                        if ( bug1098 ) {
                            DataSourceFactory dsf;
                            try {
                                dsf = DataSetURI.getDataSourceFactory( DataSetURI.getURI(surl), new NullProgressMonitor());
                                TimeSeriesBrowse tsb= dsf.getCapability( TimeSeriesBrowse.class );
                                tsb.setURI(surl);
                                DatumRange timeRangeNew= tsb.getTimeRange();
                                if ( !timeRangeNew.equals(timeRange) ) {
                                    logger.log(Level.FINE, "resetting timerange to {0}", timeRangeNew);
                                    timeRange= timeRangeNew;
                                }
                            } catch (ParseException | IOException | IllegalArgumentException | URISyntaxException ex) {
                                logger.log( Level.SEVERE, ex.getMessage(), ex );
                            }
                        }
                        keyModifiers = dialog.getModifiers();
                        maybePlot(true);
                    } else {
                        setMessage("editor cancelled");
                    }
                } finally {
                    pendingChanges.remove( PENDING_EDIT );
                    clearBusyIcon();
                }
                                
            }
        };
        return run;
    }
    
    /**
     * show completions, as if the scientist had hit tab
     */
    public void showCompletions() {
        JTextField tf= ((JTextField) dataSetSelectorComboBox.getEditor().getEditorComponent());
        final String surl = tf.getText();
        int carotpos = tf.getCaretPosition();
        setMessage("busy: getting completions");
        setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
        showCompletions(surl, carotpos);

    }
    
    /**
     * force hide the completions list.
     */
    public void hideCompletions() {
        if ( completionsPopupMenu!=null ) {
            completionsPopupMenu.setVisible(false);
        }
    }
    
    private void clearBusyIcon() {
        inspectButton.setIcon( FILEMAG_ICON );
        inspectButton.setEnabled(true);
    }

    /**
     * remove "vap+X:" from the URI, if it exists.
     * @param split
     */
    private static void maybeClearVap( URISplit split ) {
        if ( split.vapScheme!=null && split.vapScheme.equals("vap") ) {
            split.vapScheme=null;
            split.formatCarotPos-=4;
        }
    }
    
    private void showCompletions(String surl1, int carotpos1) {
        logger.log(Level.FINE, "showCompletions({0},{1})", new Object[]{surl1, carotpos1});
        inspectButton.setIcon( BUSY_ICON );
        String surl2= surl1.trim();
        int off= surl1.indexOf(surl2);
        String surl= surl2;
        int carotpos= Math.min( carotpos1 - off, surl2.length() );

        URISplit split = URISplit.parse(surl, carotpos, true);
        
        if ( surl1.startsWith("~") ) {
            surl= split.resourceUri.toString();
            carotpos= split.resourceUriCarotPos;
        }
        
        if ( surl.startsWith("vap ") ) {
            surl= "vap+"+surl.substring(4); // kludge to fix where plus was turned into space.
        }
        
        boolean shortFsCompletion= carotpos<6 && ( surl.startsWith("/") || ( surl.length()>1 && Character.isLetter(surl.charAt(0)) && surl.charAt(1)==':' ) );
        
        String ext= DataSetURI.getExt(surl);
        
        boolean haveSource= DataSourceRegistry.getInstance().hasSourceByExt(ext);
        
        boolean sourceNeedsNoFile= ext!=null && ( 
            ext.equals("inline") || ext.equals("cdaweb") || ext.equals("pdsppi") || 
            ext.equals("dc")
        );
        
        if ( ( split.file==null || split.resourceUriCarotPos > split.file.length() ) && haveSource || sourceNeedsNoFile ) {
            showFactoryCompletions( surl, carotpos );

        } else if ( carotpos==0 || (
                !surl.substring(0,carotpos).contains(":")
                && ( carotpos<4 && surl.substring(0, carotpos).equals( "vap".substring(0,carotpos ) )
                || ( surl.length()>3 && surl.substring(0, 3).equals( "vap" ) ) ) ) ) {
            showTypesCompletions( surl, carotpos );
        
        } else if ( carotpos<6 && !shortFsCompletion  ) {
            String home= "file://"+FileSystem.toCanonicalFolderName( System.getProperty("user.home") );
            String[] types= new String[] { "ftp://", "http://", "https://", "sftp://", "file:/", home };
            List<CompletionResult> result= new ArrayList<>();
            for (String type : types) {
                if (type.length() >= carotpos && surl.substring(0, carotpos).equals(type.substring(0, carotpos))) {
                    result.add(new CompletionResult(type, ""));
                }
            }
            clearBusyIcon();
            showCompletionsGui( "", result );

        } else if ( surl.startsWith("vap") && surl.substring(0,carotpos).split("\\:",-2).length==2 ) {
            String[] types= new String[] { "ftp://", "http://", "https://", "file:/", "sftp://"  };
            String[] sp= surl.substring(0,carotpos).split("\\:",-2);
            String test= sp[1];
            int testCarotpos= carotpos - ( sp[0].length() + 1 );
            List<CompletionResult> result= new ArrayList<>();
            for (String type : types) {
                if (type.length() >= testCarotpos && test.substring(0, testCarotpos).equals(type.substring(0, testCarotpos))) {
                    result.add(new CompletionResult(sp[0]+":" + type, ""));
                }
            }
            clearBusyIcon();
            showCompletionsGui( "", result );

        } else {
            if ( split.scheme!=null && split.scheme.equals("file") ) {
                if ( ".vap".equals(split.ext) && split.resourceUriCarotPos > split.file.length() ) {
                    showVapCompletions(URISplit.format(split), split.formatCarotPos);
                    return;
                }
                if ( !surl.startsWith("vap") ) maybeClearVap(split);
                showFileSystemCompletions(URISplit.format(split), split.formatCarotPos);
                return;
            }
            if ( !enableDataSource ) {
                split.formatCarotPos= split.formatCarotPos - ( split.vapScheme==null ? 0 : split.vapScheme.length() - 1 );
                split.vapScheme=null;
            }
            int firstSlashAfterHost = split.authority == null ? 0 : split.authority.length();
            if (split.resourceUriCarotPos <= firstSlashAfterHost) {
                if ( !surl.startsWith("vap") ) maybeClearVap(split);
                String doHost= URISplit.format(split);
                showHostCompletions(doHost, split.formatCarotPos);
            } else {
                if ( !surl.startsWith("vap") ) maybeClearVap(split);
                showFileSystemCompletions(URISplit.format(split), split.formatCarotPos);
            }

        }

    }

    private void calcAndShowCompletions( Runnable run ) {
        if (completionsRunnable != null) {
            completionsMonitor.cancel();
            completionsRunnable = null;
        }

        Window w= SwingUtilities.getWindowAncestor(this);
        completionsMonitor = getMonitor("completions","getting completions",w);
        //completionsMonitor.setLabel("getting completions");

        completionsRunnable= run;

        new Thread(completionsRunnable, "completionsThread").start();
    }

    private void showCompletionsGui( final String labelPrefix, List<CompletionResult> completions ) {

        CompletionsList.CompletionListListener listener = new CompletionsList.CompletionListListener() {
            @Override
            public void itemSelected(CompletionResult s1) {
                if ( s1==CompletionResult.SEPARATOR ) return; // this was a mistake
                //dataSetSelector.setSelectedItem(s1.completion);
                setValue(s1.completion);
                if (s1.maybePlot) {
                    maybePlot(true);
                }
            }
        };

        completionsPopupMenu = CompletionsList.fillPopupNew(completions, labelPrefix, new JScrollPopupMenu(), listener);
        //TODO Here's the plan: we will make the popupMenu be non-focusable, then delegate the Up,Down,Enter and Escape to it when the popup is showing.
        //completionsPopupMenu.setFocusable(true);
        completionsPopupMenu.registerKeyboardAction( new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                hideCompletions();
            }
        }, KeyStroke.getKeyStroke( KeyEvent.VK_ESCAPE, 0 ), JComponent.WHEN_IN_FOCUSED_WINDOW );
        setMessage("done getting completions");
        setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    int xpos2 = editor.getGraphics().getFontMetrics().stringWidth(labelPrefix);
                    BoundedRangeModel model = editor.getHorizontalVisibility();

                    int xpos = xpos2 - model.getValue();
                    xpos = Math.min(model.getExtent(), xpos);

                    if ( dataSetSelectorComboBox.isShowing() ) {
                        completionsPopupMenu.show(dataSetSelectorComboBox, xpos, dataSetSelectorComboBox.getHeight());
                    } else {
                        JOptionPane.showMessageDialog(dataSetSelectorComboBox, "<html>Completions for "+getValue()+"<br>are not available when the data set selector is not showing.</html>");
                    }
                    completionsRunnable = null;
                    
                } catch (NullPointerException ex) {
                    logger.log( Level.SEVERE, ex.getMessage(), ex ); // TODO: look into this

                }
            }
        } );

    }

/**
     * create completions on hostnames based on cached resources.
     * @param surl
     * @param carotpos
     */
    private void showTypesCompletions(final String surl, final int carotpos) {

        calcAndShowCompletions( new Runnable() {
            @Override
            public void run() {
                String labelPrefix= "";
                List<CompletionResult> completions;
                try {
                    completions = DataSetURI.getTypesCompletions(surl, carotpos, getMonitor());
                    showCompletionsGui( labelPrefix, completions );
                } catch (Exception ex) {
                    logger.log( Level.SEVERE, ex.getMessage(), ex );
                    JOptionPane.showMessageDialog(DataSetSelector.this, "<html>URI Syntax Exception occurred:<br>" + ex.getLocalizedMessage() + "</html>", "I/O Exception", JOptionPane.WARNING_MESSAGE);
                } finally {
                    clearBusyIcon();
                }
                
            }
        } );

    }

    /**
     * create completions on hostnames based on cached resources.
     * @param surl
     * @param carotpos
     */
    private void showHostCompletions(final String surl, final int carotpos) {

        calcAndShowCompletions( new Runnable() {
            @Override
            public void run() {
                ProgressMonitor mon = getMonitor();

                List<CompletionResult> completions;

                URISplit split = URISplit.parse(surl);
                String surlDir = split.path;

                boolean hasScheme= split.scheme!=null;

                final String labelPrefix = ( surlDir == null ? "" : surlDir );

                try {
                    completions = DataSetURI.getHostCompletions(surl, carotpos, mon);
                } catch (IOException ex) {
                    setMessage(ex.toString());
                    JOptionPane.showMessageDialog(DataSetSelector.this, "<html>I/O Exception occurred:<br>" + ex.getLocalizedMessage() + "</html>", "I/O Exception", JOptionPane.WARNING_MESSAGE);
                    return;
                } finally {
                    clearBusyIcon();
                }

                String doHost= surl.substring(0,carotpos);
                if ( hasScheme && ( doHost.endsWith(".gov") || doHost.endsWith(".edu")
                        || doHost.endsWith(".com") || doHost.endsWith(".net") ) ) {
                    CompletionResult extra= new CompletionResult(doHost+"/", "explore this host");
                    boolean haveIt= false;
                    for (CompletionResult completion : completions) {
                        if (completion.completion.equals(extra.completion)) {
                            haveIt= true;
                        }
                    }
                    if ( !haveIt ) { 
                        completions= new ArrayList( completions );
                        completions.add( extra );
                    }
                }

                showCompletionsGui( labelPrefix, completions );
            }
        } );

    }

    /**
     * Wrap DataSetURI.getFileSystemCompletions for action triggers.
     * @param suggestFsAgg include aggregations it sees.  These are a guess.
     * @param suggestFiles include files as well as aggregations.
     * @param acceptRegex if non-null, filenames must match this regex.  See Pattern.compile
     */
    public void showFileSystemCompletions(  final boolean suggestFsAgg, final boolean suggestFiles, final String acceptRegex ) {
        final String surl= this.editor.getText();
        final int carotpos= this.editor.getCaretPosition();
        
        calcAndShowCompletions( new Runnable() {
            @Override
            public void run() {
                ProgressMonitor mon = getMonitor();

                List<CompletionResult> completions;

                String labelPrefix = surl.substring(0, carotpos);

                try {
                    completions = DataSetURI.getFileSystemCompletions( surl, carotpos, suggestFsAgg, suggestFiles, acceptRegex, mon);
                } catch (UnknownHostException ex ) {
                    logger.log( Level.SEVERE, ex.getMessage(), ex );
                    setMessage("Unknown host: "+ex.getLocalizedMessage());
                    JOptionPane.showMessageDialog(DataSetSelector.this, "<html>Unknown host:<br>" + ex.getLocalizedMessage() + "</html>", "Unknown Host Exception", JOptionPane.WARNING_MESSAGE);
                    return;
                } catch (IOException ex) {
                    logger.log( Level.SEVERE, ex.getMessage(), ex );
                    setMessage(ex.toString());
                    JOptionPane.showMessageDialog(DataSetSelector.this, "<html>I/O Exception occurred:<br>" + ex.getLocalizedMessage() + "</html>", "I/O Exception", JOptionPane.WARNING_MESSAGE);
                    return;
                } catch (URISyntaxException ex) {
                    logger.log( Level.SEVERE, ex.getMessage(), ex );
                    setMessage(ex.toString());
                    JOptionPane.showMessageDialog(DataSetSelector.this, "<html>URI Syntax Exception occurred:<br>" + ex.getLocalizedMessage() + "</html>", "I/O Exception", JOptionPane.WARNING_MESSAGE);
                    return;
                } finally {
                    clearBusyIcon();
                }

                int i2= labelPrefix.lastIndexOf('/');
                if ( i2!=-1 ) {
                    labelPrefix= labelPrefix.substring(0,i2+1);
                }

                showCompletionsGui( labelPrefix, completions );

            }
        } );
    }

    private void mergeLocalIntoRemote( List<CompletionResult> remote, List<CompletionResult> local ) {
        boolean sep= false;
        
        remote= new ArrayList( remote );
        
        List<String> remoteLabels= new ArrayList(remote.size());
        for (CompletionResult remote1 : remote) {
            remoteLabels.add(remote1.completion);
        }
        
        for ( CompletionResult l: local ) {
            if ( remoteLabels.contains(l.completion) ) {
                logger.log(Level.FINEST, "already contains {0}", l.completion);
            } else {
                if ( sep==false && remote.size()>0 ) {
                    remote.add(CompletionResult.SEPARATOR);
                    sep= true;
                }
                remote.add(l);
                logger.log(Level.FINEST, "appending {0}", l.completion);
            }
        }
    }
    
    private void showVapCompletions( final String format, final int formatCarotPos) {
        //URISplit split= URISplit.parse(format);
        calcAndShowCompletions( new Runnable() {
            @Override
            public void run() {
                List<CompletionResult> completions= new ArrayList<>();
                completions.add( new CompletionResult( "timerange", "reset the timerange") );
                completions.add( new CompletionResult( "plots[0].yaxis.range", "reset the yaxis range") );
                showCompletionsGui( format.substring(0,formatCarotPos), completions );

            }
        } );
   
    }

    private void showFileSystemCompletions(final String surl, final int carotpos) {
        if ( carotpos> surl.length() ) {
            throw new StringIndexOutOfBoundsException("index out of bounds: "+carotpos+" in \"" +surl + "\"" );
        }
        logger.log(Level.FINE, "entering showFileSystemCompletions({0},{1})", new Object[]{surl, carotpos});
        calcAndShowCompletions( new Runnable() {
            @Override
            public void run() {
                ProgressMonitor mon = getMonitor();

                List<CompletionResult> completions;

                String labelPrefix = surl.substring(0, carotpos);

                String surll= surl;
                int carotposl= carotpos;

                try {
                    String atrigger= checkActionTrigger( surl );
                    if ( atrigger!=null ) {
                        surll= surl.substring(atrigger.length()+1);
                        carotposl= carotposl-atrigger.length()-1;
                        if ( suggestFile.size()>0 ) {
                            completions = DataSetURI.getFileSystemCompletions(surll, carotposl, suggestFsAgg, suggestFile, acceptPattern, mon);
                        } else {
                            completions = DataSetURI.getFileSystemCompletions(surll, carotposl, suggestFsAgg, suggestFiles, acceptPattern, mon);
                        }
                        for (CompletionResult completion : completions) {
                            completion.completable = atrigger + ":" + completion.completable;
                            completion.completion = atrigger + ":" + completion.completion;
                            completion.maybePlot = false;
                        }
                    } else {
                        if ( suggestFile.size()>0 ) {
                            completions = DataSetURI.getFileSystemCompletions(surll, carotposl, suggestFsAgg, suggestFile, acceptPattern, mon);
                        } else {
                            completions = DataSetURI.getFileSystemCompletions(surll, carotposl, suggestFsAgg, suggestFiles, acceptPattern, mon);
                            if ( completions.isEmpty() || surll.startsWith("http:") || surll.startsWith("ftp:") ) {
                                List<CompletionResult> compl1= DataSetURI.getFileSystemCacheCompletions(surll, carotposl, suggestFsAgg, suggestFiles, acceptPattern, mon);
                                mergeLocalIntoRemote( completions, compl1 );
                            }
                        }
                    }
                } catch ( IllegalArgumentException ex ) {
                    if ( ex.getMessage().startsWith( "unsupported protocol" ) ) {
                        logger.log( Level.SEVERE, ex.getMessage(), ex );
                        setMessage("Unknown host: "+ex.getLocalizedMessage());
                        showUserExceptionDialog( DataSetSelector.this, "<html>Unsupported Protocol:<br>" + surl + "</html>", "Unsupported Protocol", ex, JOptionPane.WARNING_MESSAGE);
                        return;        
                    } else {
                        throw ex;
                    }
                } catch (UnknownHostException ex ) {
                    logger.log( Level.SEVERE, ex.getMessage(), ex );
                    setMessage("Unknown host: "+ex.getLocalizedMessage());
                    showUserExceptionDialog( DataSetSelector.this, "<html>Unknown host:<br>" + ex.getLocalizedMessage() + "</html>", "Unknown Host Exception", ex, JOptionPane.WARNING_MESSAGE);
                    return;
                } catch (IOException ex) {
                    logger.log( Level.SEVERE, ex.getMessage(), ex );
                    setMessage(ex.toString());
                    showUserExceptionDialog( DataSetSelector.this, "<html>I/O Exception occurred:<br>" + ex.getLocalizedMessage() + "</html>", "I/O Exception", ex, JOptionPane.WARNING_MESSAGE);
                    return;
                } catch (URISyntaxException ex) {
                    logger.log( Level.SEVERE, ex.getMessage(), ex );
                    setMessage(ex.toString());
                    showUserExceptionDialog( DataSetSelector.this, "<html>URI Syntax Exception occurred:<br>" + ex.getLocalizedMessage() + "</html>", "I/O Exception", ex, JOptionPane.WARNING_MESSAGE);
                    return;
                } finally {
                    clearBusyIcon();
                }

                int i2= labelPrefix.lastIndexOf('/');
                if ( i2!=-1 ) {
                    labelPrefix= labelPrefix.substring(0,i2+1);
                }

                showCompletionsGui( labelPrefix, completions );

            }
        } );

    }

    /**
     * show the message, assuming that it is something that it's something for the user to fix, but provide details button.
     * @param parent
     * @param msg
     * @param title
     * @param ex
     * @param messageType
     */
    public static void showUserExceptionDialog( Component parent, String msg, String title, final Exception ex, int messageType ) {
        JPanel p= new JPanel();
        p.add( new JLabel( msg ), BorderLayout.CENTER );
        JPanel buttons= new JPanel( );
        buttons.add( new JButton( new AbstractAction("Details...") {
            @Override
            public void actionPerformed( ActionEvent e ) {
                org.das2.util.LoggerManager.logGuiEvent(e);
                JComponent c= (JComponent)e.getSource();
                JDialog dia= (JDialog) SwingUtilities.getWindowAncestor(c);
                dia.dispose();
                FileSystem.getExceptionHandler().handle(ex);
            }
        } ), BorderLayout.EAST );
        p.add( buttons, BorderLayout.SOUTH );
        JOptionPane.showMessageDialog( parent, p, title, messageType );
    }


    /**
     * get the completions from the plug-in factory..
     */
    private void showFactoryCompletions(final String surl, final int carotpos) {
        logger.log(Level.FINE, "entering showFactoryCompletions({0},{1})", new Object[]{surl, carotpos});

        calcAndShowCompletions( new Runnable() {
            @Override
            public void run() {

                List<DataSetURI.CompletionResult> completions2;
                try {
                    completions2 = DataSetURI.getFactoryCompletions(surl, carotpos, completionsMonitor);
                    setMessage("done getting completions");
                    setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
                } catch (Exception ex ) {
                    if ( !maybeHandleException(ex) ) {
                        setMessage("" + ex.getClass().getName() + " " + ex.getMessage());
                        if ( ex instanceof RuntimeException ) {
                            throw (RuntimeException)ex;
                        } else {
                            throw new RuntimeException(ex);
                        }
                    }
                    return;
                } finally {
                    clearBusyIcon();
                }

                int n = Math.min( carotpos, editor.getText().length() );
                String labelPrefix;
                try {
                    labelPrefix = editor.getText(0, n);
                } catch (BadLocationException ex) {
                    throw new RuntimeException(ex);
                }

                showCompletionsGui(labelPrefix, completions2);

            }
        } );

    }

//    private int stepForSize( int size ) {
//        int step;
//        if ( size<20 ) {
//            step=1;
//        } else if ( size<40 ) {
//            step=2;
//        } else {
//            step=4;
//        }
//        return step;
//    }
    
    /**
     * THIS MUST BE CALLED AFTER THE COMPONENT IS ADDED.  
     * This is so ENTER works properly.
     */
    public final void addCompletionKeys() {

        ActionMap map = dataSetSelectorComboBox.getActionMap();
        map.put("complete", new AbstractAction("completionsPopup") {
            @Override
            public void actionPerformed(ActionEvent ev) {
                org.das2.util.LoggerManager.logGuiEvent(ev);
                String context= (String) dataSetSelectorComboBox.getEditor().getItem();
                //String context = (String) dataSetSelectorComboBox.getSelectedItem();  // This is repeated code.  See browseButtonActionPerformed.
                if ( context==null ) context= "";
                Component c= dataSetSelectorComboBox.getEditor().getEditorComponent();
                if ( c instanceof JTextField ) { // which it is...
                    int i= ((JTextField)c).getCaretPosition();
                    context= context.substring(0,i);
                }

                // hooks for browsing, such as "vap+internal"
                for (String browseTriggerRegex : browseTriggers.keySet()) {
                    if (Pattern.matches(browseTriggerRegex, context)) {
                        logger.finest("matches browse trigger");
                        Action action = browseTriggers.get(browseTriggerRegex);
                        action.actionPerformed( new ActionEvent(DataSetSelector.this, 123, "dataSetSelect") );
                        return;
                    }
                }

                showCompletions();
            }
        });

        map.put("plot", new AbstractAction("plotUrl") {
            @Override
            public void actionPerformed(ActionEvent ev) {
                org.das2.util.LoggerManager.logGuiEvent(ev);                
                setValue(getEditor().getText());
                keyModifiers = ev.getModifiers();
                maybePlot(true);
            }
        });

        dataSetSelectorComboBox.setActionMap(map);
        final JTextField tf = (JTextField) dataSetSelectorComboBox.getEditor().getEditorComponent();
        tf.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                org.das2.util.LoggerManager.logGuiEvent(ev);                
                dataSetSelectorComboBox.setSelectedItem(tf.getText());
                keyModifiers = ev.getModifiers();
                try {
                    setValue(getEditor().getText());
                    maybePlot(true);
                } catch ( IllegalArgumentException ex ) {
                    JOptionPane.showMessageDialog( DataSetSelector.this, ex.getMessage(), "Unable to parse URI", JOptionPane.WARNING_MESSAGE );
                }
            }
        });

        Set<AWTKeyStroke> trav= Collections.emptySet();
        setFocusTraversalKeys( KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, trav );
        setFocusTraversalKeys( KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, trav );

        InputMap imap = SwingUtilities.getUIInputMap(dataSetSelectorComboBox, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_MASK), "complete");
        imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0 ), "complete");
        imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_MASK), "plot");
        imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK), "plot");

        needToAddKeys = false;
    }
    private final Action ABOUT_PLUGINS_ACTION = new AbstractAction("About Plugins") {
        @Override
        public void actionPerformed(ActionEvent e) {
            org.das2.util.LoggerManager.logGuiEvent(e);            
            String about = DataSetSelectorSupport.getPluginsText();
            JOptionPane.showMessageDialog(DataSetSelector.this, about);
        }
    };

    public final void addAbouts() {
        final String regex = "about:(.*)";
        registerActionTrigger(regex, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);
                String ss = DataSetSelector.this.getValue();
                Pattern p = Pattern.compile(regex);
                Matcher m = p.matcher(ss);
                if (!m.matches()) {
                    throw new IllegalArgumentException("huh?");
                }
                String arg = m.group(1);
                switch (arg) {
                    case "plugins":
                        ABOUT_PLUGINS_ACTION.actionPerformed(e);
                        break;
                    case "classpath":
                        try {
                            org.das2.util.LoggerManager.logGuiEvent(e);
                            StringBuilder result= new StringBuilder("<html>");
                            ClassLoader cl = ClassLoader.getSystemClassLoader();
                            if ( cl instanceof URLClassLoader ) {
                                URL[] urls = ((URLClassLoader)cl).getURLs();
                                for(URL url: urls){
                                    result.append(url.toString()).append("<br>");
                                }
                            }
                            JTextPane jtp= new JTextPane();
                            jtp.setContentType("text/html");
                            jtp.read( new StringReader(result.toString()), null);
                            jtp.setEditable(false);
                            JScrollPane pane= new JScrollPane(jtp);
                            pane.setPreferredSize( new Dimension(640,480) );
                            pane.setVerticalScrollBarPolicy( JScrollPane.VERTICAL_SCROLLBAR_ALWAYS );
                            JOptionPane.showMessageDialog(DataSetSelector.this, pane );
                        } catch (IOException ex) {
                            Logger.getLogger(DataSetSelector.class.getName()).log(Level.SEVERE, null, ex);
                        }  
                        break;
                    default:
                        JOptionPane.showMessageDialog(DataSetSelector.this, "about:plugins or about:classpath" );
                        break;
                }
            }
        });
    }

    /**
     * see if "script:" can be removed
     * @param surl
     * @return
     */
    private String checkActionTrigger(String surl) {
        for ( String s: actionTriggers.keySet() ) {
            if ( surl.matches(s) ) {
                int i= s.indexOf(':');
                if ( i>-1 ) {
                    String tr= s.substring(0,i);
                    if ( Ops.safeName(tr).equals(tr) ) {
                        return tr;
                    }
                }
            }
        }
        return null;
    }


    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        inspectButton = new javax.swing.JButton();
        plotItButton = new javax.swing.JButton();
        dataSetSelectorComboBox = new javax.swing.JComboBox();

        setMaximumSize(new java.awt.Dimension(1000, 27));

        inspectButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/resources/fileMag.png"))); // NOI18N
        inspectButton.setToolTipText("<html>Inspect this resource.<br>\nFor folder names, this enters the file system browser, or shows a list of remote folders.<br>\nFor files, this will enter an editor panel for the resource, or show a list of parameter options.<br>\n</html>\n\n");
        inspectButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        inspectButton.setMaximumSize(new java.awt.Dimension(27, 27));
        inspectButton.setMinimumSize(new java.awt.Dimension(27, 27));
        inspectButton.setName("inspect"); // NOI18N
        inspectButton.setPreferredSize(new java.awt.Dimension(27, 27));
        inspectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                inspectButtonActionPerformed(evt);
            }
        });

        plotItButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/resources/go.png"))); // NOI18N
        plotItButton.setToolTipText("<html>Play button plots this data location, or URI<br>\nThis may also load a .vap file or run a script, depending on the text entered.<br>\nCtrl modifier: plot the dataset by adding a new plot<br>\nShift modifier: plot the dataset as an overplot<br>\nAlt modifier: inspect this resource.<br>");
        plotItButton.setMaximumSize(new java.awt.Dimension(27, 27));
        plotItButton.setMinimumSize(new java.awt.Dimension(27, 27));
        plotItButton.setName("go"); // NOI18N
        plotItButton.setPreferredSize(new java.awt.Dimension(27, 27));
        plotItButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                plotItButtonActionPerformed(evt);
            }
        });

        dataSetSelectorComboBox.setEditable(true);
        dataSetSelectorComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "(application will put recent items here)" }));
        dataSetSelectorComboBox.setToolTipText("Enter data source address");
        dataSetSelectorComboBox.setMaximumSize(new java.awt.Dimension(2000, 27));
        dataSetSelectorComboBox.setMinimumSize(new java.awt.Dimension(100, 27));
        dataSetSelectorComboBox.setPreferredSize(new java.awt.Dimension(300, 27));
        dataSetSelectorComboBox.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent evt) {
            }
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent evt) {
                dataSetSelectorComboBoxPopupMenuWillBecomeInvisible(evt);
            }
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent evt) {
                dataSetSelectorComboBoxPopupMenuCanceled(evt);
            }
        });

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(dataSetSelectorComboBox, 0, 386, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(plotItButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 26, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(inspectButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 20, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
        );

        layout.linkSize(new java.awt.Component[] {inspectButton, plotItButton}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(plotItButton, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(inspectButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 20, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .add(dataSetSelectorComboBox, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        layout.linkSize(new java.awt.Component[] {dataSetSelectorComboBox, inspectButton, plotItButton}, org.jdesktop.layout.GroupLayout.VERTICAL);

        inspectButton.getAccessibleContext().setAccessibleDescription("inspect contents of file or directory");
    }// </editor-fold>//GEN-END:initComponents

    private void plotItButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_plotItButtonActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);            
        keyModifiers = evt.getModifiers();
        try {
            setValue(getEditor().getText());
            maybePlot(true);
        } catch ( IllegalArgumentException ex ) {
            JOptionPane.showMessageDialog( DataSetSelector.this, ex.getMessage(), "Unable to parse URI", JOptionPane.WARNING_MESSAGE );
        }
    }//GEN-LAST:event_plotItButtonActionPerformed

    private void inspectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_inspectButtonActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);            
        String context = ((String) dataSetSelectorComboBox.getEditor().getItem()).trim();
        if ( context.startsWith("vap ") ) context= "vap+"+context.substring(4);
        String ext = DataSetURI.getExt(context);
        final String fcontext= context;

        // hooks for browsing, such as "vap+internal"
        for ( Entry<String,Action> e: browseTriggers.entrySet()) {
            String browseTriggerRegex= e.getKey();
            if (Pattern.matches(browseTriggerRegex, context)) {
                logger.finest("matches browse trigger");
                Action action = e.getValue();
                action.actionPerformed( new ActionEvent(this, 123, "dataSetSelect") );
                return;
            }
        }
        
        boolean doBrowseSourceType= false;
        if ( enableDataSource ) {
            if ( !context.contains("/?") && context.contains("?") ) {
                doBrowseSourceType= true;
            } 
            // see if the xml or json file is handled by a different source.
            if ( ext!=null && ( ext.equals( DataSetURI.RECOGNIZE_FILE_EXTENSION_JSON ) || ext.equals( DataSetURI.RECOGNIZE_FILE_EXTENSION_XML) ) ) {
                try {
                    File f= DataSetURI.getFile(context,new AlertNullProgressMonitor("download on event thread"));
                    String ext2= DataSourceRecognizer.guessDataSourceType(f);
                    if ( ext2!=null ) {
                        ext= ext2;
                    }                    
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
            
            if ( DataSourceRegistry.getInstance().hasSourceByExt(ext) ) {
                doBrowseSourceType= true;
            }
        }
        

        if ( enableDataSource && ( context.trim().length()==0 || context.trim().equals("vap+") ) ) {
            showCompletions();

        } else if ( doBrowseSourceType ) {
            browseSourceType();

        } else {
            final URISplit split = URISplit.parse(context);
            if ( split.scheme!=null && ( split.scheme.equals("file")
                    || split.scheme.equals("http") || split.scheme.equals("https")
                    || split.scheme.equals("ftp") || split.scheme.equals("sftp" ) ) ) {
                try {
                    if ( enableDataSource && FileSystemUtil.resourceExists(context)  && FileSystemUtil.resourceIsFile(context) ) {
                        if ( !FileSystemUtil.resourceIsLocal(context) ) {
                            Runnable run= new Runnable() {
                                @Override
                                public void run() {
                                    ProgressMonitor mon= DasProgressPanel.createFramed(
                                        SwingUtilities.getWindowAncestor(DataSetSelector.this),
                                        "downloading "+split.file.substring(split.path.length()) );
                                    try {
                                        FileSystemUtil.doDownload(fcontext, mon);
                                    } catch (IOException | URISyntaxException ex) {
                                        FileSystem.getExceptionHandler().handle(ex);
                                    }
                                    browseSourceType();
                                }
                            };
                            RequestProcessor.invokeLater(run);
                        } else {
                            browseSourceType();
                        }
                    } else if (split.scheme.equals("file")) {
                        JFileChooser chooser = new JFileChooser( new File( DataSetURI.toUri(split.path) ) );
                        chooser.setMultiSelectionEnabled(true);
                        if ( acceptPattern!=null ) {
                            final Pattern p= Pattern.compile(acceptPattern);
                            chooser.setFileFilter( new FileFilter() {
                                @Override
                                public boolean accept(File f) {
                                    return f!=null && ( f.isDirectory() || p.matcher(f.toString()).matches() );
                                }

                                @Override
                                public String getDescription() {
                                    return "files matching "+Glob.getGlobFromRegex(acceptPattern);
                                }
                            });
                        }
                        int result = chooser.showOpenDialog(this);
                        if (result == JFileChooser.APPROVE_OPTION) {
                            File[] ff=  chooser.getSelectedFiles();
                            File f= chooser.getSelectedFile();
                            String suri;
                            suri= DataSetURI.newUri(context, f.toString());
                            if ( ff.length>1 ) {  // let's try to aggregate
                                String[] suris= new String[ff.length];
                                for ( int i=0; i<suris.length; i++ ) {
                                    suris[i]= DataSetURI.newUri(context, ff[i].toString());
                                }
                                String asuri= org.autoplot.datasource.DataSourceUtil.makeAggregation(suri,suris);
                                suri= asuri;
                            }
                            setValue(suri);
                            maybePlot(false);

                            //dataSetSelectorComboBox.setSelectedItem(suri);
                        }
                    } else {
                        showCompletions();
                    }
                } catch (IOException | URISyntaxException ex) {
                    FileSystem.getExceptionHandler().handle(ex);
                }
            } else {
                showCompletions();
            }
        }
    }//GEN-LAST:event_inspectButtonActionPerformed
    private boolean popupCancelled = false;

private void dataSetSelectorComboBoxPopupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent evt) {//GEN-FIRST:event_dataSetSelectorComboBoxPopupMenuWillBecomeInvisible
    if (popupCancelled == false) {
        if ( (keyModifiers&KeyEvent.ALT_MASK ) == KeyEvent.ALT_MASK ) {
            browseSourceType();
        } else {
            maybePlot(true);
        }
    }
    popupCancelled = false;
}//GEN-LAST:event_dataSetSelectorComboBoxPopupMenuWillBecomeInvisible

private void dataSetSelectorComboBoxPopupMenuCanceled(javax.swing.event.PopupMenuEvent evt) {//GEN-FIRST:event_dataSetSelectorComboBoxPopupMenuCanceled
    popupCancelled = true;
}//GEN-LAST:event_dataSetSelectorComboBoxPopupMenuCanceled

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox dataSetSelectorComboBox;
    private javax.swing.JButton inspectButton;
    private javax.swing.JButton plotItButton;
    // End of variables declaration//GEN-END:variables

    /**
     * this returns the value of the dataSetSelectorComboBox, rather than what is pending in the editor.  The problem is pending
     * operations in the text editor will cause the value to be clobbered.
     * @return
     */
    protected String getLastValue() {
        return lastValue;
    }

    private String lastValue= null;

    /**
     * Getter for property value.
     * TODO: this should really be redone, returning the value property.
     * @return Value of property value.
     */
    public String getValue() {
        String val= (String)this.dataSetSelectorComboBox.getEditor().getItem(); //TODO why not use this if selectedItem is null?
        //String val= (String)this.dataSetSelectorComboBox.getSelectedItem(); //TODO: check this vs getEditor().getItem() on different platforms
        if ( val==null ) {
            return "";
        } else {
            if ( val.startsWith("file://" ) && val.length()>7 && val.charAt(7)!='/' ) { // kludge for Windows
                val= "file:/" + val.substring(7); //https://sourceforge.net/p/autoplot/bugs/1383/
            }
            return val.trim();
        }
    }

    /**
     * Set the current value for the editor.  This does not fire an event, 
     * so call maybePlot() to accept the value.
     * @param value the new URI.
     */
    public void setValue(String value) {
        logger.log(Level.FINE, "setValue to \"{0}\"", value);
        if ( value!=null ) value= value.trim();
        //String oldvalue= this.editor.getText();
        this.lastValue= value;
        if (value == null) {
            value="";
        }
        value= URISplit.makeColloquial( value );
        setTextInternal(value);
    }
    /**
     * Holds value of property browseTypeExt.
     */
    private String browseTypeExt;

    /**
     * Getter for property browseTypeExt.
     * @return Value of property browseTypeExt.
     */
    public String getBrowseTypeExt() {
        return this.browseTypeExt;
    }

    /**
     * Setter for property browseTypeExt.
     * @param browseTypeExt New value of property browseTypeExt.
     */
    public void setBrowseTypeExt(String browseTypeExt) {
        String oldBrowseTypeExt = this.browseTypeExt;
        this.browseTypeExt = browseTypeExt;
        firePropertyChange("browseTypeExt", oldBrowseTypeExt, browseTypeExt);
    }

    protected boolean hidePlayButton = false;
    public static final String PROP_HIDEPLAYBUTTON = "hidePlayButton";

    public boolean isHidePlayButton() {
        return hidePlayButton;
    }

    public void setHidePlayButton(boolean hidePlayButton) {
        boolean oldHidePlayButton = this.hidePlayButton;
        this.hidePlayButton = hidePlayButton;
        plotItButton.setVisible( !hidePlayButton );
        firePropertyChange(PROP_HIDEPLAYBUTTON, oldHidePlayButton, hidePlayButton);
    }

    /**
     * rather than allowing clients to add buttons, which would make
     * Matisse GUI builder less useful here, clients can replace the
     * behavior of the play button.
     * @param icon
     * @param action 
     */
    public void replacePlayButton( Icon icon, AbstractAction action ) {
       
       this.plotItButton.setAction(action);
       this.plotItButton.setIcon(icon);
       this.plotItButton.revalidate();
       this.plotItButton.setText("");
       this.plotItButton.setToolTipText( String.valueOf( action.getValue( AbstractAction.NAME ) ) );
       this.plotItButton.setVisible(true);
       
       this.plotItButtonVisible= false;
       
    }
            
    /**
     * Utility field holding list of ActionListeners.
     */
    private transient java.util.ArrayList actionListenerList;

    /**
     * Registers ActionListener to receive events.
     * @param listener The listener to register.
     */
    public synchronized void addActionListener(java.awt.event.ActionListener listener) {
        if (actionListenerList == null) {
            actionListenerList = new java.util.ArrayList();
        }
        actionListenerList.add(listener);
    }

    /**
     * Removes ActionListener from the list of listeners.
     * @param listener The listener to remove.
     */
    public synchronized void removeActionListener(java.awt.event.ActionListener listener) {
        if (actionListenerList != null) {
            actionListenerList.remove(listener);
        }
    }

    /**
     * Notifies all registered listeners about the event.
     *
     * @param event The event to be fired
     */
    private void fireActionListenerActionPerformed(java.awt.event.ActionEvent event) {
        java.util.ArrayList<ActionListener> list;
        synchronized (this) {
            if (actionListenerList == null) {
                return;
            }
            list = (java.util.ArrayList) actionListenerList.clone();
        }
        for (ActionListener list1 : list) {
            list1.actionPerformed(event);
        }
    }
    /**
     * Holds value of property recent.
     */
    private List<String> recent;

    /**
     * Getter for property recent.
     * @return Value of property recent.
     */
    public List<String> getRecent() {
        if (this.recent == null) {
            recent = new ArrayList<>();
        }
        return this.recent;
    }

    /**
     * Setter for property recent.  Should be called from the event thread.
     * This sets defaultRecent as well, so other clients can get a list of 
     * recent values.
     * @param recent New value of property recent.
     * @see #getDefaultRecent()
     */
    public void setRecent(List<String> recent) {
        List<String> oldRecent = this.recent;
        this.recent = recent;
        String value = editor.getText();
        ArrayList<String> r = new ArrayList<>(recent);
        if ( r.size()>0 && !value.equals(MESSAGE_RECENT)) {
            if ( !r.get(r.size()-1).equals(value) ) {
                r.add(value);
            }
        }
        Collections.reverse(r);
        dataSetSelectorComboBox.setModel(new DefaultComboBoxModel(r.toArray()));
        //editor.setText(value); // don't show most recent one.
        support.refreshRecentFilesMenu();
        firePropertyChange( PROP_RECENT, oldRecent, recent);
        defaultRecent= recent;
    }
    
    private static List<String> defaultRecent;
    
    /**
     * allow clients (e.g. Autoplot) to set a list of recent that new
     * instances will use.
     * @param recent the list
     */
    public static void setDefaultRecent( List<String> recent ) {
        defaultRecent= recent;
    }
    
    public static List<String> getDefaultRecent() {
        if ( defaultRecent==null ) {
            throw new IllegalArgumentException("defaultRecent has not been set");
        }
        return defaultRecent;
    }
    
    /**
     * Holds value of property message.
     */
    private String message;

    /**
     * Getter for property message.
     * @return Value of property message.
     */
    public String getMessage() {
        return this.message;
    }

    /**
     * Setter for property message.
     * @param message New value of property message.
     */
    public void setMessage(String message) {
        String oldMessage = this.message;
        this.message = message;
        firePropertyChange(PROPERTY_MESSAGE, oldMessage, message);
    }
    Map<String, Action> actionTriggers = new LinkedHashMap<>();
    protected boolean plotItButtonVisible = true;
    public static final String PROP_PLOTITBUTTONVISIBLE = "plotItButtonVisible";

    Map<String,Action> browseTriggers = new LinkedHashMap<>();

    public boolean isPlotItButtonVisible() {
        return plotItButtonVisible;
    }

    public void setPlotItButtonVisible(boolean plotItButtonVisible) {
        boolean oldPlotItButtonVisible = this.plotItButtonVisible;
        this.plotItButtonVisible = plotItButtonVisible;
        this.plotItButton.setVisible(plotItButtonVisible);
        firePropertyChange(PROP_PLOTITBUTTONVISIBLE, oldPlotItButtonVisible, plotItButtonVisible);
    }

    /**
     * This is how we allow .vap files to be in the dataSetSelector.  We register
     * a pattern for which an action is invoked.
     * @param regex regular expression for the trigger.
     * @param action the action to take.
     */
    public void registerActionTrigger(String regex, Action action) {
        actionTriggers.put(regex, action);
    }

    public void registerBrowseTrigger( String regex, Action action) {
        browseTriggers.put(regex, action);
    }

    public Action getOpenLocalAction() {
        return support.openLocalAction();
    }

    public Action getOpenLocalVapAction() {
        return support.openLocalVapAction();
    }

    public JMenu getRecentMenu() {
        return support.recentMenu();
    }

    /**
     * allows the dataSetSelectorComboBox to be used to select files.
     * @param b
     */
    public void setDisableDataSources(boolean b) {
        this.enableDataSource= !b;
    }

    protected boolean enableDataSource = true;
    public static final String PROP_ENABLEDATASOURCE = "enableDataSource";

    public boolean isEnableDataSource() {
        return enableDataSource;
    }

    /**
     * delegate down to datasource when doing completions.
     * @param enableDataSource
     */
    public void setEnableDataSource(boolean enableDataSource) {
        boolean oldEnableDataSource = this.enableDataSource;
        this.enableDataSource = enableDataSource;
        firePropertyChange(PROP_ENABLEDATASOURCE, oldEnableDataSource, enableDataSource);
    }

    private DatumRange timeRange=null;
    public static final String PROP_TIMERANGE = "timeRange";
    
    /**
     * get the timerange associated with this focus dataset.  This is typically
     * the same as the xaxis range.
     * @return the timerange associated with this focus dataset.
     */
    public DatumRange getTimeRange() {
        return timeRange;
    }

    /**
     * set default timeRange when aggregation is used, or for dialogs.
     * null is allowed, indicating there is no focus timerange
     * @param timerange
     */
    public void setTimeRange(DatumRange timerange) {
        DatumRange oldRange= this.timeRange;
        this.timeRange = timerange;
        firePropertyChange(PROP_TIMERANGE, oldRange, timerange );
    }


    /**
     * if true, then suggest aggregations as well.
     */
    protected boolean suggestFsAgg = true;
    public static final String PROP_SUGGESTFSAGG = "suggestFsAgg";

    public boolean isSuggestFsAgg() {
        return suggestFsAgg;
    }

    public void setSuggestFsAgg(boolean suggestFsAgg) {
        boolean oldSuggestFsAgg = this.suggestFsAgg;
        this.suggestFsAgg = suggestFsAgg;
        firePropertyChange(PROP_SUGGESTFSAGG, oldSuggestFsAgg, suggestFsAgg);
    }

    /**
     * if true, then suggest files in file system completions.  For example,
     * we may disable this so we only see aggregations.
     */
    protected boolean suggestFiles = true;
    public static final String PROP_SUGGESTFILES = "suggestFiles";

    public boolean isSuggestFiles() {
        return suggestFiles;
    }

    public void setSuggestFiles(boolean suggestFiles) {
        boolean oldSuggestFiles = this.suggestFiles;
        this.suggestFiles = suggestFiles;
        firePropertyChange(PROP_SUGGESTFILES, oldSuggestFiles, suggestFiles);
    }


    private final List<String> suggestFile= new ArrayList();

    /**
     * show completions for this regex.
     * @param template
     */
    public void addSuggestFile( String template ) {
        suggestFile.add(template);
    }


    private String acceptPattern=null;

    public String getAcceptPattern() {
        return acceptPattern;
    }

    /**
     * pattern for filenames allowed.  null means anything allowed.
     * @param acceptPattern
     */
    public void setAcceptPattern( String acceptPattern ) {
        this.acceptPattern = acceptPattern;
    }
    
    public void setPromptText(String text) {
        if (text==null) {
            throw new NullPointerException("Prompt text can't be null; use empty string instead.");
        }
        ((PromptTextField)getEditor()).setPromptText(text);
    }

    public String getPromptText() {
        return ((PromptTextField)getEditor()).getPromptText();
    }

    private void showPopup( MouseEvent e ) {
        getPopupMenu().show( editor, e.getX(), e.getY() );
    }

    private JPopupMenu getPopupMenu() {
        JPopupMenu result= new JPopupMenu();
        JMenuItem cutItem = result.add(new DefaultEditorKit.CutAction());
        cutItem.setText("Cut");
        JMenuItem copyItem = result.add(new DefaultEditorKit.CopyAction());
        copyItem.setText("Copy");
        JMenuItem pasteItem = result.add(new DefaultEditorKit.PasteAction());
        pasteItem.setText("Paste");

        result.add( new JSeparator() );

        JMenu fontMenu= new JMenu( "Font Size" );

        fontMenu.add(new AbstractAction( "Big" ) {
            @Override
            public void actionPerformed(ActionEvent ev) {
                org.das2.util.LoggerManager.logGuiEvent(ev);            
                Font f= getEditor().getFont();
                int size= 16;
                if ( size>4 && size<18 ) {
                    Font nf= f.deriveFont( (float)size );
                    dataSetSelectorComboBox.setFont(nf);
                }
            }
        });

        fontMenu.add(new AbstractAction( "Normal" ) {
            @Override
            public void actionPerformed(ActionEvent ev) {
                org.das2.util.LoggerManager.logGuiEvent(ev);            
                Font f= getEditor().getFont();
                int size= getParent().getFont().getSize();
                if ( size>4 && size<18 ) {
                    Font nf= f.deriveFont( (float)size );
                    dataSetSelectorComboBox.setFont(nf);
                }
            }
        });

        fontMenu.add(new AbstractAction( "Small" ) {
            @Override
            public void actionPerformed(ActionEvent ev) {
                org.das2.util.LoggerManager.logGuiEvent(ev);            
                Font f= getEditor().getFont();
                int size= 8;
                if ( size>4 && size<18 ) {
                    Font nf= f.deriveFont( (float)size );
                    dataSetSelectorComboBox.setFont(nf);
                }
            }
        });

        result.add(fontMenu);

        if ( this.alternatePeerCard!=null ) {
            result.add( new JSeparator() );
            result.add( new AbstractAction( alternatePeer ) {
                @Override
                public void actionPerformed(ActionEvent ev) {
                    org.das2.util.LoggerManager.logGuiEvent(ev);                
                    Container trp= DataSetSelector.this.getParent();
                    if ( trp.getLayout() instanceof CardLayout ) {
                        //((CardLayout)trp.getLayout()).show( trp, alternatePeerCard );
                        setCardSelected(false);
                    }
                }
            } );
        }


        return result;

    }
    
    private boolean cardSelected = false;

    public static final String PROP_CARDSELECTED = "cardSelected";

    /**
     * added to listen to changes, but this must also be set externally to switch back.
     * @return 
     */
    public boolean isCardSelected() {
        return cardSelected;
    }

    public void setCardSelected(boolean cardSelected) {
        boolean oldCardSelected = this.cardSelected;
        this.cardSelected = cardSelected;
        firePropertyChange(PROP_CARDSELECTED, oldCardSelected, cardSelected);
    }
     
    public void setCardSelectedNoEventKludge(boolean cardSelected) {
        boolean oldCardSelected = this.cardSelected;
        this.cardSelected = cardSelected;
    }
        
    public static void main( String[] args ) {
        DataSetSelectorDemo.main(args);
    }

    private String alternatePeer;
    private String alternatePeerCard;

    public void setAlternatePeer( String title, String card ) {
        this.alternatePeer= title;
        this.alternatePeerCard= card;
    }

    private boolean expertMode= true;
    public boolean isExpertMode() {
        return expertMode;
    }

    public void setExpertMode( boolean expert ) {
        getEditor().setEditable(expert);
        getEditor().setEnabled(expert);        
        this.expertMode= expert;
        if ( expert ) {
            this.plotItButton.setToolTipText("<html>Plot this data location, or URI.<br> Ctrl modifier: plot the dataset by adding a new plot<br> Shift modifier: plot the dataset as an overplot<br> ");
        } else {
            this.plotItButton.setToolTipText("<html>Plot this data location, or URI.<br> ");
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        dataSetSelectorComboBox.setEnabled(enabled);
        plotItButton.setEnabled(enabled);
        inspectButton.setEnabled(enabled);
        super.setEnabled(enabled); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * allow the user to pick one of two times, when it is ambiguous what they want.
     * @param parent null or the component to focus.
     * @param timeRange1
     * @param timeRange2 
     * @return the timerange selected.
     */
    public static DatumRange pickTimeRange( Component parent, DatumRange timeRange1, DatumRange timeRange2 ) {
        if ( timeRange2 ==null ) return timeRange1;
        JPanel p= new JPanel();
        p.setLayout( new BoxLayout( p, BoxLayout.Y_AXIS ) );
        p.add( new JLabel("<html>The URI contains a time different than the application<br>time range.  Which should be used?</html>") );
        ButtonGroup bg= new ButtonGroup();
        String s1= timeRange1.toString();
        JRadioButton b1= new JRadioButton( s1 );
        p.add(b1);
        bg.add(b1);
        bg.setSelected(b1.getModel(),true);
        String s2= timeRange2 .toString();
        JRadioButton b2= new JRadioButton( s2 );
        p.add(b2);
        bg.add(b2);
        JOptionPane.showMessageDialog( parent, p, "Which Range?", JOptionPane.QUESTION_MESSAGE );
        if ( b1.isSelected() ) {
            LoggerManager.getLogger("gui").log(Level.FINE, "user picked first timerange {0}", s1);
            return timeRange1;
        } else {
            LoggerManager.getLogger("gui").log(Level.FINE, "user picked second timerange {0}", s2);
            return timeRange2 ;
        }
    }
    
    /**
     * return true if all the time ranges are for a similar time, where they
     * differ by no more than 10%.
     * @param timeRanges
     * @return 
     */
    private static boolean allSimilarTimes( List<DatumRange> timeRanges ) {
        if ( timeRanges.size()<2 ) return true;
        DatumRange dr1= timeRanges.get(0);
        if ( !UnitsUtil.isIntervalOrRatioMeasurement( dr1.getUnits()) ) throw new IllegalArgumentException("data must be numeric or location");
        if ( dr1.width().value()==0 ) return false; 
        boolean result= true;
        for ( int i=1; result && i<timeRanges.size(); i++ ) {
            DatumRange dr2= timeRanges.get(i);
            double d1= DatumRangeUtil.normalize( dr1, dr2.min() );
            if ( !( d1>-0.05 && d1<0.05 ) ) {
                result= false;
            } else {
                double d2= DatumRangeUtil.normalize( dr1, dr2.max() );
                if ( !( d2>0.95 || d2<1.05 ) ) {
                    result= false;
                }
            }
        }
        return result;
    }
    
    /**
     * Allow the user to pick one of a set of times, when it is ambiguous what they want.
     * @param parent null or the component to focus.
     * @param timeRange list of time ranges, which may also contain null.
     * @param labels for each time range.
     * @return the time range selected.
     */
    public static DatumRange pickTimeRange( Component parent, List<DatumRange> timeRange, List<String> labels ) {
        timeRange= new ArrayList<>(timeRange); // make mutable.
        labels= new ArrayList<>(labels);
        for ( int i=timeRange.size()-1; i>=0; i-- ) {
            if ( timeRange.get(i)==null ) {
                timeRange.remove(i);
                labels.remove(i);
            }
        }
        if ( timeRange.size()==1 ) return timeRange.get(0);
        
        if ( allSimilarTimes(timeRange) ) {
            return timeRange.get(timeRange.size()-1);
        }
        
        JPanel p= new JPanel();
        p.setLayout( new BoxLayout( p, BoxLayout.Y_AXIS ) );
        p.add( new JLabel("<html>The URI contains a time different than the current<br>application time range.  Which should be used?</html>") );
        ButtonGroup bg= new ButtonGroup();
        List<JRadioButton> buttons= new ArrayList<>();
        for ( int i=0; i<timeRange.size(); i++ ) {
            String s1= "<html>" + timeRange.get(i).toString() + " <i>("+labels.get(i)+")";
            JRadioButton b1= new JRadioButton( s1 );
            p.add(b1);
            bg.add(b1);
            if ( i==0 ) bg.setSelected(b1.getModel(),true);
            buttons.add(b1);
        }
        JOptionPane.showMessageDialog( parent, p, "Which Range?", JOptionPane.QUESTION_MESSAGE );
        for ( int i=0; i<timeRange.size(); i++ ) {
            if ( buttons.get(i).isSelected() ) {
                LoggerManager.getLogger("gui").log(Level.FINE, "user picked {0} timerange {1}", new Object[] { i, timeRange.get(i) } );
                return timeRange.get(i);
            }
        }
        return null;
    }

    /**
     * This makes sense to me, where you can just add a URL to the selector.
     * I haven't looked at this class in ten years, and it's bizarre how complex
     * it is.  I'm making this a method so that this is done in one place.  This
     * contains the logic which was in the pngwalkTool.  If 
     * there are other things to be done, it will be done here.
     * @param suri 
     */
    public void addToRecent(String suri ) {
        List<String> urls = new ArrayList<>();
        List<String> recent = new ArrayList(getRecent());
        recent.removeAll( Collections.singleton( suri ) );
        for (String b : recent) {
            urls.add( b );
        }
        urls.add( suri );
        setRecent(urls);     
    }
    
}
