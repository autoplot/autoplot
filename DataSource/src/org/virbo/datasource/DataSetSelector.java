/*
 * DataSetSelector.java
 *
 * Created on November 5, 2007, 6:04 AM
 */
package org.virbo.datasource;

import java.awt.AWTKeyStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dialog;
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
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BoundedRangeModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.DefaultEditorKit;
import org.das2.components.DasProgressPanel;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.DomainDivider;
import org.das2.datum.DomainDividerUtil;
import org.das2.datum.UnitsUtil;
import org.das2.system.MonitorFactory;
import org.das2.system.RequestProcessor;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.datasource.DataSetURI.CompletionResult;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.datasource.ui.PromptComboBoxEditor;
import org.virbo.datasource.ui.PromptTextField;
import org.virbo.dsops.Ops;

/**
 * Swing Component for selecting dataset URIs.  This provides hooks for completions.
 *
 * @author  jbf
 */
public class DataSetSelector extends javax.swing.JPanel {
    public static final String PROP_RECENT = "recent";
    private static int MAX_RECENT=20;
    private Map<Object,Object> pendingChanges= new HashMap(); // lockObject->Client
    private Object PENDING_EDIT="edit";
    private Object PENDING_PLOT="plot"; 
    
    /** Creates new form DataSetSelector */
    public DataSetSelector() {
        initComponents(); // of the 58milliseconds it takes to create the GUI, 52 are spent in here.
        dataSetSelector.setEditor( new PromptComboBoxEditor("Enter data location") );

        editor = ((JTextField) dataSetSelector.getEditor().getEditorComponent());        
        dataSetSelector.addActionListener( new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                keyModifiers= e.getModifiers();
                
            }
        });

        addCompletionKeys();
        addAbouts();
        
        maybePlotTimer = new Timer(100, new ActionListener() {

            public void actionPerformed(ActionEvent ev) {
                // some DataSource constructors do not return in interactive time, so create a new thread for now.
                Runnable run = new Runnable() {

                    public void run() {
                        try {
                            maybePlotImmediately();
                        } finally {
                            pendingChanges.remove( PENDING_PLOT );
                        }
                    }
                };
                RequestProcessor.invokeLater(run);
            }
        });
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
    JTextField editor;
    DataSetSelectorSupport support = new DataSetSelectorSupport(this);
    public static final String PROPERTY_MESSAGE = "message";
    static final Logger logger = Logger.getLogger("apdss.gui.dss");
    MonitorFactory monitorFactory = null;
    Timer maybePlotTimer;
    int keyModifiers = 0;


    /**
     * provide direct access to the editor component.
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
     * provide access to timer for GUI testing.
     * @return
     */
    public boolean isPendingChanges() {
        if ( maybePlotTimer.isRunning() || !pendingChanges.isEmpty() ) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * automated GUI testing needs access to subcomponents.
     * This provides access to the inspect/browse button that when pressed enters a GUI editor to graphically work on the URI.
     * @return the inspect/browse button
     */
    public JButton getBrowseButton() {
        return browseButton;
    }

    private ProgressMonitor getMonitor() {
        return getMonitor("Please Wait", "unidentified task in progress");
    }

    private ProgressMonitor getMonitor(String label, String desc) {
        if (monitorFactory == null) {
            return DasApplication.getDefaultApplication().getMonitorFactory().getMonitor(label, desc);
        } else {
            return monitorFactory.getMonitor(label, desc);
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
     * See if we can resolve a plottable URI from the selector by identifying
     * the data source type and calling its reject method, or identifying the
     * file system type and getting completions on it.  The reject method
     * is used to identify URIs where we can assist in making it acceptable using
     * the "browseSourceType" method.
     *
     */
    private void maybePlotImmediately() {
        String surl = getValue();
        if (surl.equals("") ) { 
            logger.finest("empty value, returning");
            return;
        }

        if (surl.startsWith("vap+internal:")) {
            firePlotDataSetURL();
            return;
        }

        for (String actionTriggerRegex : actionTriggers.keySet()) {
            if (Pattern.matches(actionTriggerRegex, surl)) {
                logger.finest("matches action trigger");
                Action action = actionTriggers.get(actionTriggerRegex);
                action.actionPerformed(new ActionEvent(this, 123, "dataSetSelect"));
                return;
            }
        }

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

        URISplit split= URISplit.parse(surl);

        String file= split.file;
        if ( file==null || file.equals("file:///") ) file="";  //TODO: kludge around bug in URISplit.

        try {

            if (file.endsWith("/") || file.contains("/?") || ( file.endsWith(".zip") || file.contains(".zip?") || file.endsWith(".ZIP") || surl.contains(".ZIP?") ) ) { 
                //TODO: vap+cdaweb:file:///?ds=APOLLO12_SWS_1HR&id=NSpectra_1  should not reject if empty file?
                int carotpos = editor.getCaretPosition();
                setMessage("busy: getting filesystem completions.");
                showCompletions(surl, carotpos);

            } else if (file.endsWith("/..")) { // pop up one directory
                int carotpos = surl.lastIndexOf("/..");
                carotpos = surl.lastIndexOf("/", carotpos - 1);
                if (carotpos != -1) {
                    setValue(surl.substring(0, carotpos + 1));
                    dataSetSelector.getEditor().setItem(surl.substring(0, carotpos + 1));
                    editor.setCaretPosition(carotpos+1);
                    maybePlotImmediately();
                }
            } else {
                try {
                    URI uri= DataSetURI.getURI(surl);
                    if ( uri==null ) {
                        setMessage("error: URI cannot be formed from \""+surl+"\"");
                        return;
                    }
                    DataSourceFactory f = DataSetURI.getDataSourceFactory(uri, getMonitor());
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
                            getEditor().setText(surl);
                            setValue(surl);
                            maybePlot(true);
                            return;
                        } else {
                            showPluginsScreen();
                            return;
                        }

                    }
                    setMessage("busy: checking to see if uri looks acceptable");
                    String surl1 = surl;
                    ProgressMonitor mon= getMonitor();
                    List<String> problems= new ArrayList();
                    if (f.reject(surl1, problems,mon)) {
                        TimeSeriesBrowse tsb= f.getCapability( TimeSeriesBrowse.class );
                        if ( tsb!=null ) {
                            if ( timeRange!=null && UnitsUtil.isTimeLocation( timeRange.getUnits() ) ) {
                                try {
                                    tsb.setURI(surl1);
                                    tsb.setTimeRange(timeRange);
                                    String suri= tsb.getURI();
                                    problems.remove( TimeSeriesBrowse.PROB_NO_TIMERANGE_PROVIDED ); 
                                    if ( !f.reject( suri, new ArrayList<String>(), mon) ) {
                                        setMessage("accepted uri after setting timerange");
                                        int modifiers= this.keyModifiers;
                                        setValue(suri);
                                        this.keyModifiers= modifiers;
                                        firePlotDataSetURL();
                                        return;    
                                    }
                                } catch ( ParseException ex ) {
                                    JOptionPane.showMessageDialog( plotItButton, ex.getMessage() );
                                    setMessage(ex.getMessage());  // $y$J would throw runtime exception.
                                    logger.log( Level.SEVERE, "", ex );
                                    return;
                                } catch ( IllegalArgumentException ex ) {
                                    JOptionPane.showMessageDialog( plotItButton, ex.getMessage() );
                                    setMessage(ex.getMessage());  // $y$J would throw runtime exception.
                                    logger.log( Level.SEVERE, "", ex );
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
                        setMessage("resolving uri to data set as " + DataSourceRegistry.getInstance().getExtensionFor(f));
                        firePlotDataSetURL();
                    }
                } catch (DataSetURI.NonResourceException ex) { // see if it's a folder.
                    int carotpos = surl.length();
                    setMessage("no extension or mime type, try filesystem completions");
                    showCompletions(surl, carotpos);
                } catch (IllegalArgumentException ex) {
                    setMessage(ex.getMessage());
                    logger.log( Level.SEVERE, "", ex );
                    firePlotDataSetURL();
                } catch (URISyntaxException ex) {
                    setMessage(ex.getMessage());
                    logger.log( Level.SEVERE, "", ex );
                    firePlotDataSetURL();
                }
            }
        } catch (IllegalArgumentException ex) {
            logger.log( Level.SEVERE, "", ex );
            setMessage(ex.getMessage());
        } catch (IOException ex) {
            logger.log( Level.SEVERE, "", ex );
            setMessage(ex.getMessage());
        }

    }

    /**
     * if the dataset requires parameters that aren't provided, then
     * show completion list.  Otherwise, fire off event.
     */
    public void maybePlot(boolean allowModifiers) {
        logger.log(Level.FINE, "go {0}", getValue());

        if (!allowModifiers) {
            keyModifiers = 0;
        }
        
        pendingChanges.put( PENDING_PLOT, this );
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

    private void firePlotDataSetURL() {
        List<String> r = new ArrayList<String>(getRecent());
        String value = getValue();
        lastValue= value;
        if (r.contains(value)) {
            r.remove(value); // move to top of the list by remove then add.
        }
        r.add(value);

        while ( r.size()>MAX_RECENT ) {
            r.remove(0);
        }

        ActionEvent e = new ActionEvent(this, 123, "dataSetSelect", keyModifiers);
        fireActionListenerActionPerformed(e);

    }

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
        if ( msg==null ) msg="";
        if ( ex instanceof FileNotFoundException && msg.length()==0 ) {
            msg= "File not found"; // this may never happen, but to be sure...
        }
        if ( ( ex instanceof FileNotFoundException
                || ex.toString().contains("file not found")
                || ex.toString().contains("root does not exist") )
              && msg.length()>0 ) {
            if ( msg.startsWith("File not found: ") ) {
                String[] ss= msg.split(":",2);
                msg= "<html>"+ss[0]+":<br>"+ss[1]+"</html>";
            }
            JOptionPane.showMessageDialog( DataSetSelector.this, msg, "No Such File", JOptionPane.WARNING_MESSAGE );
            setMessage("" + ex.getMessage());
            return true;
        } else if ( ex instanceof HtmlResponseIOException ) {
            JPanel r= new javax.swing.JPanel( new BorderLayout() );
            final String link = ((HtmlResponseIOException)ex).getURL().toString();
            r.add( new JLabel( " " ), BorderLayout.NORTH );
            r.add( new JLabel( msg ) );
            JPanel se= new JPanel( new BorderLayout() );
            se.add( new JButton( new AbstractAction("View Page") {
                public void actionPerformed(ActionEvent e) {
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
        while ( dd.boundaryCount(tr.min(),tr.max() )<2 ) {
            dd= dd.finerDivider(false);
        }
        while ( dd.boundaryCount(tr.min(),tr.max() )>12 ) {
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
        setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
        String surl = ((String) dataSetSelector.getEditor().getItem()).trim();

        boolean wasRejected= false;
        DataSourceEditorPanel edit = null;
        try {
            edit = DataSourceEditorPanelUtil.getDataSourceEditorPanel(DataSetURI.getURIValid(surl));
            if ( edit!=null && edit.reject(surl) ) {
                edit= null;
                wasRejected= true;
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
                    //experiment with GUI based on completions.
                    edit= new CompletionsDataSourceEditor();
                } else {
                    JOptionPane.showMessageDialog( DataSetSelector.this, "Unable to inspect .vap files" );
                }
            }
            
        } catch (URISyntaxException ex) {
            Logger.getLogger(DataSetSelector.class.getName()).log(Level.SEVERE, null, ex);
            edit= null;
        } catch ( Exception ex ) {
            Logger.getLogger(DataSetSelector.class.getName()).log(Level.SEVERE, null, ex);
            edit= null;
        }

        if (edit != null) {
            final Window window = SwingUtilities.getWindowAncestor(this);

            final DataSourceEditorPanel fedit= edit;
            final String fsurl= surl;

            if ( surl!=null && surl.startsWith("vap+internal:") ) {
                JOptionPane.showMessageDialog( window, "Internal URI cannot be edited" );
                return;
            }
            
            Runnable run= new Runnable() {
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
                                    DatumRange tr2=  quantizeTimeRange( timeRange );
                                    tsb.setTimeRange(tr2);
                                    surl= tsb.getURI();
                                }
                            }
                        } catch (ParseException ex ){
                            Logger.getLogger(DataSetSelector.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (IOException ex) {
                            Logger.getLogger(DataSetSelector.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (IllegalArgumentException ex) {
                            Logger.getLogger(DataSetSelector.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (URISyntaxException ex) {
                            Logger.getLogger(DataSetSelector.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }

                    boolean proceed;
                    try {
                        proceed = fedit.prepare(surl, window, getMonitor("download file", "downloading file to preparing editor"));
                        if ( !proceed ) return;
                    } catch ( java.io.InterruptedIOException ex ) {
                        setMessage( "download cancelled" );  //TODO: check FTP
                        return;
                    } catch (Exception ex) {
                        if ( !maybeHandleException(ex) ) {
                            throw new RuntimeException(ex);
                        }
                        return;
                    } finally {
                        setCursor( Cursor.getDefaultCursor() );
                    }

                    fedit.setURI(surl);
                    fedit.markProblems(problems);
                    
                    final String fsurl= surl;

                    Runnable run= new Runnable() {
                        public void run() {
                            DataSourceEditorDialog dialog;

                            String title = "Editing URI " + fsurl;
                            if (window instanceof Frame) {
                                dialog = new DataSourceEditorDialog((Frame) window, fedit.getPanel(), true);
                            } else if (window instanceof Dialog) {  // TODO: Java 1.6 ModalityType.
                                dialog = new DataSourceEditorDialog((Dialog) window, fedit.getPanel(), true);
                            } else {
                                throw new RuntimeException("parent windowAncestor type is not supported.");
                            }
                            dialog.setTitle(title);

                            if ( actionListenerList==null || actionListenerList.isEmpty() ) {
                                dialog.setPlayButton(false); // nothing is going to happen, so don't show play button.
                            } else {
                                dialog.setExpertMode(isExpertMode());
                            }

                            pendingChanges.put( PENDING_EDIT, DataSetSelector.this );
                            dialog.setVisible(true);
                            if (!dialog.isCancelled()) {
                                logger.log( Level.FINE, "dataSetSelector.setSelectedItem(\"{0}\");", fedit.getURI() );
                                dataSetSelector.setSelectedItem(fedit.getURI());
                                keyModifiers = dialog.getModifiers();
                                maybePlot(true);
                                pendingChanges.remove( PENDING_EDIT );
                            } else {
                                setMessage("editor cancelled");
                                pendingChanges.remove( PENDING_EDIT );
                            }
                        } };
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
            
            int carotpos = surl.indexOf("?");
            if (carotpos == -1) {
                carotpos = surl.length();
            } else {
                carotpos += 1;
            }
            surl = surl.substring(0, carotpos);
            showCompletions(surl, carotpos);
        }
    }

    public void showCompletions() {
        JTextField tf= ((JTextField) dataSetSelector.getEditor().getEditorComponent());
        final String surl = tf.getText();
        int carotpos = tf.getCaretPosition();
        setMessage("busy: getting completions");
        showCompletions(surl, carotpos);

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
        String surl2= surl1.trim();
        int off= surl1.indexOf(surl2);
        final String surl= surl2;
        final int carotpos= Math.min( carotpos1 - off, surl2.length() );

        URISplit split = URISplit.parse(surl, carotpos, true);

        boolean shortFsCompletion= carotpos<6 && ( surl.startsWith("/") || ( surl.length()>1 && Character.isLetter(surl.charAt(0)) && surl.charAt(1)==':' ) );

        boolean haveSource= DataSourceRegistry.getInstance().hasSourceByExt(DataSetURI.getExt(surl));
        if ( ( split.file==null || split.resourceUriCarotPos > split.file.length() ) && haveSource ) {
            showFactoryCompletions( surl, carotpos );

        } else if ( carotpos==0 || (
                !surl.substring(0,carotpos).contains(":")
                && ( carotpos<4 && surl.substring(0, carotpos).equals( "vap".substring(0,carotpos ) )
                || ( surl.length()>3 && surl.substring(0, 3).equals( "vap" ) ) ) ) ) {
            showTypesCompletions( surl, carotpos );
        
        } else if ( carotpos<6 && !shortFsCompletion  ) {
            String[] types= new String[] { "ftp://", "http://", "https://", "file:/", "sftp://" };
            List<CompletionResult> result= new ArrayList<CompletionResult>();
            for ( int i=0; i<types.length; i++ ) {
                if ( types[i].length()>= carotpos &&
                        surl.substring(0, carotpos).equals(types[i].substring(0,carotpos) ) ) {
                    result.add( new CompletionResult(types[i],"") );
                }
            }

            showCompletionsGui( "", result );

        } else if ( surl.startsWith("vap") && surl.substring(0,carotpos).split("\\:",-2).length==2 ) {
            String[] types= new String[] { "ftp://", "http://", "https://", "file:/", "sftp://"  };
            String[] sp= surl.substring(0,carotpos).split("\\:",-2);
            String test= sp[1];
            int testCarotpos= carotpos - ( sp[0].length() + 1 );
            List<CompletionResult> result= new ArrayList<CompletionResult>();
            for ( int i=0; i<types.length; i++ ) {
                if ( types[i].length()>= testCarotpos &&
                        test.substring(0, testCarotpos).equals(types[i].substring(0,testCarotpos) ) ) {
                    result.add( new CompletionResult(sp[0]+":"+types[i],"") );
                }
            }
            showCompletionsGui( "", result );

        } else {
            if ( split.scheme!=null && split.scheme.equals("file") ) {
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

        completionsMonitor = getMonitor();
        completionsMonitor.setLabel("getting completions");

        completionsRunnable= run;

        new Thread(completionsRunnable, "completionsThread").start();
    }

    private void showCompletionsGui( final String labelPrefix, List<CompletionResult> completions ) {

        CompletionsList.CompletionListListener listener = new CompletionsList.CompletionListListener() {

            public void itemSelected(CompletionResult s1) {
                if ( s1==CompletionResult.SEPARATOR ) return; // this was a mistake
                //dataSetSelector.setSelectedItem(s1.completion);
                setValue(s1.completion);
                if (s1.maybePlot) {
                    maybePlot(true);
                }
            }
        };

        completionsPopupMenu = CompletionsList.fillPopupNew(completions, labelPrefix, new JPopupMenu(), listener);

        setMessage("done getting completions");

        SwingUtilities.invokeLater(new Runnable() {

            public void run() {
                try {
                    int xpos2 = editor.getGraphics().getFontMetrics().stringWidth(labelPrefix);
                    BoundedRangeModel model = editor.getHorizontalVisibility();

                    int xpos = xpos2 - model.getValue();
                    xpos = Math.min(model.getExtent(), xpos);

                    if ( dataSetSelector.isShowing() ) {
                        completionsPopupMenu.show(dataSetSelector, xpos, dataSetSelector.getHeight());
                    } else {
                        JOptionPane.showMessageDialog( dataSetSelector, "<html>Completions for "+getValue()+"<br>are not available when the data set selector is not showing.</html>");
                    }
                    completionsRunnable = null;
                    
                } catch (NullPointerException ex) {
                    logger.log( Level.SEVERE, "", ex ); // TODO: look into this

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

            public void run() {

                String labelPrefix= "";
                List<CompletionResult> completions;
                try {
                    completions = DataSetURI.getTypesCompletions(surl, carotpos, getMonitor());
                    showCompletionsGui( labelPrefix, completions );
                } catch (Exception ex) {
                    Logger.getLogger(DataSetSelector.class.getName()).log(Level.SEVERE, null, ex);
                    JOptionPane.showMessageDialog(DataSetSelector.this, "<html>URI Syntax Exception occurred:<br>" + ex.getLocalizedMessage() + "</html>", "I/O Exception", JOptionPane.WARNING_MESSAGE);
                    return;
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

            public void run() {
                ProgressMonitor mon = getMonitor();

                List<CompletionResult> completions = null;

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
                }

                String doHost= surl.substring(0,carotpos);
                if ( hasScheme && ( doHost.endsWith(".gov") || doHost.endsWith(".edu")
                        || doHost.endsWith(".com") || doHost.endsWith(".net") ) ) {
                    CompletionResult extra= new CompletionResult(doHost+"/", "explore this host");
                    boolean haveIt= false;
                    for ( int i=0; i<completions.size(); i++ ) {
                        if ( completions.get(i).completion.equals(extra.completion) ) haveIt= true;
                    }
                    if ( !haveIt ) { 
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

            public void run() {
                ProgressMonitor mon = getMonitor();

                List<CompletionResult> completions = null;

                String labelPrefix = surl.substring(0, carotpos);

                try {
                    completions = DataSetURI.getFileSystemCompletions( surl, carotpos, suggestFsAgg, suggestFiles, acceptRegex, mon);
                } catch (UnknownHostException ex ) {
                    logger.log( Level.SEVERE, "", ex );
                    setMessage("Unknown host: "+ex.getLocalizedMessage());
                    JOptionPane.showMessageDialog(DataSetSelector.this, "<html>Unknown host:<br>" + ex.getLocalizedMessage() + "</html>", "Unknown Host Exception", JOptionPane.WARNING_MESSAGE);
                    return;
                } catch (IOException ex) {
                    logger.log( Level.SEVERE, "", ex );
                    setMessage(ex.toString());
                    JOptionPane.showMessageDialog(DataSetSelector.this, "<html>I/O Exception occurred:<br>" + ex.getLocalizedMessage() + "</html>", "I/O Exception", JOptionPane.WARNING_MESSAGE);
                    return;
                } catch (URISyntaxException ex) {
                    logger.log( Level.SEVERE, "", ex );
                    setMessage(ex.toString());
                    JOptionPane.showMessageDialog(DataSetSelector.this, "<html>URI Syntax Exception occurred:<br>" + ex.getLocalizedMessage() + "</html>", "I/O Exception", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                int i2= labelPrefix.lastIndexOf("/");
                if ( i2!=-1 ) {
                    labelPrefix= labelPrefix.substring(0,i2+1);
                }

                showCompletionsGui( labelPrefix, completions );

            }
        } );
    }

    private void mergeLocalIntoRemote( List<CompletionResult> remote, List<CompletionResult> local ) {
        boolean sep= false;
        List<String> remoteLabels= new ArrayList(remote.size());
        for ( int i=0; i<remote.size(); i++ ) {
            remoteLabels.add(remote.get(i).completion);
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
                logger.log(Level.FINEST, "appening {0}", l.completion);
            }
        }
    }
    
    private void showFileSystemCompletions(final String surl, final int carotpos) {

        calcAndShowCompletions( new Runnable() {

            public void run() {
                ProgressMonitor mon = getMonitor();

                List<CompletionResult> completions = null;

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
                        for ( int i=0; i<completions.size(); i++ ) {
                            completions.get(i).completable= atrigger + ":" + completions.get(i).completable;
                            completions.get(i).completion= atrigger + ":" + completions.get(i).completion;
                            completions.get(i).maybePlot= false;
                        }
                    } else {
                        if ( suggestFile.size()>0 ) {
                            completions = DataSetURI.getFileSystemCompletions(surll, carotposl, suggestFsAgg, suggestFile, acceptPattern, mon);
                        } else {
                            completions = DataSetURI.getFileSystemCompletions(surll, carotposl, suggestFsAgg, suggestFiles, acceptPattern, mon);
                            if ( completions.isEmpty() || surll.startsWith("http:") ) {
                                List<CompletionResult> compl1= DataSetURI.getFileSystemCacheCompletions(surll, carotposl, suggestFsAgg, suggestFiles, acceptPattern, mon);
                                mergeLocalIntoRemote( completions, compl1 );
                            }
                        }
                    }
                } catch (UnknownHostException ex ) {
                    logger.log( Level.SEVERE, "", ex );
                    setMessage("Unknown host: "+ex.getLocalizedMessage());
                    showUserExceptionDialog( DataSetSelector.this, "<html>Unknown host:<br>" + ex.getLocalizedMessage() + "</html>", "Unknown Host Exception", ex, JOptionPane.WARNING_MESSAGE);
                    return;
                } catch (IOException ex) {
                    logger.log( Level.SEVERE, "", ex );
                    setMessage(ex.toString());
                    showUserExceptionDialog( DataSetSelector.this, "<html>I/O Exception occurred:<br>" + ex.getLocalizedMessage() + "</html>", "I/O Exception", ex, JOptionPane.WARNING_MESSAGE);
                    return;
                } catch (URISyntaxException ex) {
                    logger.log( Level.SEVERE, "", ex );
                    setMessage(ex.toString());
                    showUserExceptionDialog( DataSetSelector.this, "<html>URI Syntax Exception occurred:<br>" + ex.getLocalizedMessage() + "</html>", "I/O Exception", ex, JOptionPane.WARNING_MESSAGE);
                    return;
                }

                int i2= labelPrefix.lastIndexOf("/");
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
     * @param messageType
     */
    public void showUserExceptionDialog( Component parent, String msg, String title, final Exception ex, int messageType ) {
        JPanel p= new JPanel();
        p.add( new JLabel( msg ), BorderLayout.CENTER );
        JPanel buttons= new JPanel( );
        buttons.add( new JButton( new AbstractAction("Details...") {
            public void actionPerformed( ActionEvent e ) {
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

        calcAndShowCompletions( new Runnable() {

            public void run() {

                List<DataSetURI.CompletionResult> completions2;
                try {
                    completions2 = DataSetURI.getFactoryCompletions(surl, carotpos, completionsMonitor);
                    setMessage("done getting completions");
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

        ActionMap map = dataSetSelector.getActionMap();
        map.put("complete", new AbstractAction("completionsPopup") {

            public void actionPerformed(ActionEvent ev) {
                String context= (String) dataSetSelector.getEditor().getItem();
                //String context = (String) dataSetSelector.getSelectedItem();  // This is repeated code.  See browseButtonActionPerformed.
                if ( context==null ) context= "";

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

            public void actionPerformed(ActionEvent ev) {
                setValue(getEditor().getText());
                keyModifiers = ev.getModifiers();
                maybePlot(true);
            }
        });

        dataSetSelector.setActionMap(map);
        final JTextField tf = (JTextField) dataSetSelector.getEditor().getEditorComponent();
        tf.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                dataSetSelector.setSelectedItem(tf.getText());
                keyModifiers = e.getModifiers();
                maybePlot(true);
            }
        });

        Set<AWTKeyStroke> trav= Collections.emptySet();
        setFocusTraversalKeys( KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, trav );
        setFocusTraversalKeys( KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, trav );

        InputMap imap = SwingUtilities.getUIInputMap(dataSetSelector, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_MASK), "complete");
        imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0 ), "complete");
        imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_MASK), "plot");
        imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK), "plot");
        //imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_MASK), "smallerFont");
        //imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, InputEvent.CTRL_MASK), "biggerFont");
        //imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_MASK), "biggerFont");
        needToAddKeys = false;
    }
    private Action ABOUT_PLUGINS_ACTION = new AbstractAction("About Plugins") {

        public void actionPerformed(ActionEvent e) {
            String about = DataSetSelectorSupport.getPluginsText();

            JOptionPane.showMessageDialog(DataSetSelector.this, about);
        }
    };

    public final void addAbouts() {
        final String regex = "about:(.*)";
        registerActionTrigger(regex, new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                String ss = DataSetSelector.this.getValue();
                Pattern p = Pattern.compile(regex);
                Matcher m = p.matcher(ss);
                if (!m.matches()) {
                    throw new IllegalArgumentException("huh?");
                }
                String arg = m.group(1);
                if (arg.equals("plugins")) {
                    ABOUT_PLUGINS_ACTION.actionPerformed(e);
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
                int i= s.indexOf(":");
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

        browseButton = new javax.swing.JButton();
        plotItButton = new javax.swing.JButton();
        dataSetSelector = new javax.swing.JComboBox();

        browseButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/virbo/datasource/fileMag.png"))); // NOI18N
        browseButton.setToolTipText("<html>Inspect this resource.<br>\nFor folder names, this enters the file system browser, or shows a list of remote folders.<br>\nFor files, this will enter an editor panel for the resource, or show a list of parameter options.<br>\n</html>\n\n");
        browseButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        browseButton.setName("browse"); // NOI18N
        browseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseButtonActionPerformed(evt);
            }
        });

        plotItButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/virbo/datasource/go.png"))); // NOI18N
        plotItButton.setToolTipText("<html>Plot this data location, or URI.<br>\nCtrl modifier: plot the dataset by adding a new plot<br>\nShift modifier: plot the dataset as an overplot<br>\nAlt modifier: inspect this resource.<br>");
        plotItButton.setMaximumSize(new java.awt.Dimension(20, 20));
        plotItButton.setMinimumSize(new java.awt.Dimension(20, 20));
        plotItButton.setName("go"); // NOI18N
        plotItButton.setPreferredSize(new java.awt.Dimension(20, 20));
        plotItButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                plotItButtonActionPerformed(evt);
            }
        });

        dataSetSelector.setEditable(true);
        dataSetSelector.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "(application will put recent items here)" }));
        dataSetSelector.setToolTipText("Enter data source address");
        dataSetSelector.setMinimumSize(new java.awt.Dimension(20, 20));
        dataSetSelector.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                dataSetSelectorMouseClicked(evt);
            }
        });
        dataSetSelector.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent evt) {
                dataSetSelectorPopupMenuCanceled(evt);
            }
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent evt) {
                dataSetSelectorPopupMenuWillBecomeInvisible(evt);
            }
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent evt) {
            }
        });
        dataSetSelector.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                dataSetSelectorItemStateChanged(evt);
            }
        });
        dataSetSelector.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSetSelectorActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(dataSetSelector, 0, 320, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(plotItButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 26, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(browseButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 20, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
        );

        layout.linkSize(new java.awt.Component[] {browseButton, plotItButton}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                        .add(plotItButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(browseButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 19, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(dataSetSelector, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        layout.linkSize(new java.awt.Component[] {browseButton, dataSetSelector, plotItButton}, org.jdesktop.layout.GroupLayout.VERTICAL);

        browseButton.getAccessibleContext().setAccessibleDescription("inspect contents of file or directory");
    }// </editor-fold>//GEN-END:initComponents
    private void dataSetSelectorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSetSelectorActionPerformed
        // this is not used because focus lost causes event fire.  Instead we listen to the JTextField.
    }//GEN-LAST:event_dataSetSelectorActionPerformed

    private void plotItButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_plotItButtonActionPerformed
        keyModifiers = evt.getModifiers();
        setValue(getEditor().getText());
        maybePlot(true);
    }//GEN-LAST:event_plotItButtonActionPerformed

    private void browseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browseButtonActionPerformed
        String context = ((String) dataSetSelector.getEditor().getItem()).trim();
        if ( context==null ) context= "";
        String ext = context==null ? "" : DataSetURI.getExt(context);
        final String fcontext= context;

        // hooks for browsing, such as "vap+internal"
        for (String browseTriggerRegex : browseTriggers.keySet()) {
            if (Pattern.matches(browseTriggerRegex, context)) {
                logger.finest("matches browse trigger");
                Action action = browseTriggers.get(browseTriggerRegex);
                action.actionPerformed( new ActionEvent(this, 123, "dataSetSelect") );
                return;
            }
        }

        if ( enableDataSource && ( context.trim().length()==0 || context.trim().equals("vap+") ) ) {
            showCompletions();

        } else if ( enableDataSource &&  ( (!context.contains("/?") && context.contains("?")) || DataSourceRegistry.getInstance().hasSourceByExt(ext) ) ) {
            browseSourceType();

        } else {
            final URISplit split = URISplit.parse(context);
            if ( split.scheme!=null && ( split.scheme.equals("file")
                    || split.scheme.equals("http") || split.scheme.equals("https")
                    || split.scheme.equals("ftp") || split.scheme.equals("sftp" ) ) ) {
                try {
                    if (FileSystemUtil.resourceExists(context)  && FileSystemUtil.resourceIsFile(context) ) {
                        if ( !FileSystemUtil.resourceIsLocal(context) ) {
                            Runnable run= new Runnable() {
                                public void run() {
                                    ProgressMonitor mon= DasProgressPanel.createFramed(
                                        SwingUtilities.getWindowAncestor(DataSetSelector.this),
                                        "downloading "+split.file.substring(split.path.length()) );
                                    try {
                                        FileSystemUtil.doDownload(fcontext, mon);
                                    } catch (Exception ex) {
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
                                String asuri= org.virbo.datasource.DataSourceUtil.makeAggregation(suri,suris);
                                suri= asuri;
                            }
                            setValue(suri);
                            maybePlot(false);

                            //dataSetSelector.setSelectedItem(suri);
                        }
                    } else {
                        showCompletions();
                    }
                } catch (IOException ex) {
                    FileSystem.getExceptionHandler().handle(ex);
                } catch (URISyntaxException ex) {
                    FileSystem.getExceptionHandler().handle(ex);
                }
            } else {
                showCompletions();
            }
        }
    }//GEN-LAST:event_browseButtonActionPerformed

private void dataSetSelectorItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_dataSetSelectorItemStateChanged
    if (doItemStateChange && evt.getStateChange() == ItemEvent.SELECTED) {
        maybePlot(false);
    }
}//GEN-LAST:event_dataSetSelectorItemStateChanged
    private boolean popupCancelled = false;

private void dataSetSelectorPopupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent evt) {//GEN-FIRST:event_dataSetSelectorPopupMenuWillBecomeInvisible
    if (popupCancelled == false) {
        if ( (keyModifiers&KeyEvent.ALT_MASK ) == KeyEvent.ALT_MASK ) {
            browseSourceType();
        } else {
            maybePlot(true);
        }
    }
    popupCancelled = false;
}//GEN-LAST:event_dataSetSelectorPopupMenuWillBecomeInvisible

private void dataSetSelectorMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_dataSetSelectorMouseClicked
}//GEN-LAST:event_dataSetSelectorMouseClicked

private void dataSetSelectorPopupMenuCanceled(javax.swing.event.PopupMenuEvent evt) {//GEN-FIRST:event_dataSetSelectorPopupMenuCanceled
    popupCancelled = true;
}//GEN-LAST:event_dataSetSelectorPopupMenuCanceled

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton browseButton;
    private javax.swing.JComboBox dataSetSelector;
    private javax.swing.JButton plotItButton;
    // End of variables declaration//GEN-END:variables

    /**
     * this returns the value of the datasetselector, rather than what is pending in the editor.  The problem is pending
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
        String val= (String)this.dataSetSelector.getEditor().getItem(); //TODO why not use this if selectedItem is null?
        //String val= (String)this.dataSetSelector.getSelectedItem(); //TODO: check this vs getEditor().getItem() on different platforms
        if ( val==null ) {
            return "";
        } else {
            return val.trim();
        }
    }
    private boolean doItemStateChange = false;

    /**
     * Set the current value for the editor.  This does not fire an event, so call maybePlot() to accept the value.
     * @param value New value of property value.
     */
    public void setValue(String value) {
        //String oldvalue= this.editor.getText();
        this.lastValue= value;
        if (value == null) {
            value="";
        }
        value= URISplit.makeColloquial( value );
        doItemStateChange = false;
        this.dataSetSelector.setSelectedItem(value);
        this.dataSetSelector.repaint();
        this.editor.setText(value);
        //we can't fire because of overflow...  firePropertyChange( "value", oldvalue, value );
    //doItemStateChange = true;
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
        java.util.ArrayList list;
        synchronized (this) {
            if (actionListenerList == null) {
                return;
            }
            list = (java.util.ArrayList) actionListenerList.clone();
        }
        for (int i = 0; i < list.size(); i++) {
            ((java.awt.event.ActionListener) list.get(i)).actionPerformed(event);
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
            recent = new ArrayList<String>();
        }
        return this.recent;
    }

    /**
     * Setter for property recent.  Should be called from the event thread.
     * @param recent New value of property recent.
     */
    public void setRecent(List<String> recent) {
        List<String> oldRecent = this.recent;
        this.recent = recent;
        String value = editor.getText();
        ArrayList<String> r = new ArrayList<String>(recent);
        Collections.reverse(r);
        dataSetSelector.setModel(new DefaultComboBoxModel(r.toArray()));
        editor.setText(value); // don't show most recent one.
        support.refreshRecentFilesMenu();
        firePropertyChange( PROP_RECENT, oldRecent, recent);
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
    Map<String, Action> actionTriggers = new LinkedHashMap<String, Action>();
    protected boolean plotItButtonVisible = true;
    public static final String PROP_PLOTITBUTTONVISIBLE = "plotItButtonVisible";

    Map<String,Action> browseTriggers = new LinkedHashMap<String,Action>();

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
     * This is how we allow .vap files to be in the datasetSelector.  We register
     * a pattern for which an action is invoked.
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
     * allows the dataSetSelector to be used to select files.
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
    
    public DatumRange getTimeRange() {
        return timeRange;
    }

    /**
     * set default timeRange when aggregation is used, or for dialogs.
     * null is allowed, indicating there is no focus timerange
     * @param timeRange
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


    private List<String> suggestFile= new ArrayList();

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

        fontMenu.add( new AbstractAction( "Big" ) {
            public void actionPerformed(ActionEvent ev) {
                Font f= getEditor().getFont();
                int size= 16;
                if ( size>4 && size<18 ) {
                    Font nf= f.deriveFont( (float)size );
                    dataSetSelector.setFont(nf);
                }
            }
        });

        fontMenu.add( new AbstractAction( "Normal" ) {
            public void actionPerformed(ActionEvent ev) {
                Font f= getEditor().getFont();
                int size= getParent().getFont().getSize();
                if ( size>4 && size<18 ) {
                    Font nf= f.deriveFont( (float)size );
                    dataSetSelector.setFont(nf);
                }
            }
        });

        fontMenu.add( new AbstractAction( "Small" ) {
            public void actionPerformed(ActionEvent ev) {
                Font f= getEditor().getFont();
                int size= 8;
                if ( size>4 && size<18 ) {
                    Font nf= f.deriveFont( (float)size );
                    dataSetSelector.setFont(nf);
                }
            }
        });

        result.add(fontMenu);

        if ( this.alternatePeerCard!=null ) {
            result.add( new JSeparator() );
            result.add( new AbstractAction( alternatePeer ) {
                public void actionPerformed(ActionEvent ev) {
                    Container trp= DataSetSelector.this.getParent();
                    ((CardLayout)DataSetSelector.this.getParent().getLayout()).show( trp, alternatePeerCard );
                }
            } );
        }


        return result;

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
    private boolean isExpertMode() {
        return expertMode;
    }

    public void setExpertMode( boolean expert ) {
        this.expertMode= expert;
        if ( expert ) {
            this.plotItButton.setToolTipText("<html>Plot this data location, or URI.<br> Ctrl modifier: plot the dataset by adding a new plot<br> Shift modifier: plot the dataset as an overplot<br> ");
        } else {
            this.plotItButton.setToolTipText("<html>Plot this data location, or URI.<br> ");
        }
    }

}
