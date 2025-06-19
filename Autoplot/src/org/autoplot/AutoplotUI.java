/*
 * AutoplotUI.java
 *
 * Created on July 27, 2007, 6:32 AM
 */
package org.autoplot;

import java.awt.event.MouseEvent;
import java.net.URISyntaxException;
import javax.swing.Icon;
import org.autoplot.bookmarks.Bookmark;
import org.autoplot.bookmarks.BookmarksManager;
import com.cottagesystems.jdiskhog.JDiskHogPanel;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.AWTEvent;
import org.das2.components.DasProgressPanel;
import org.das2.components.TearoffTabbedPane;
import org.das2.dasml.DOMBuilder;
import org.das2.dasml.SerializeUtil;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.InconvertibleUnitsException;
import org.das2.util.AboutUtil;
import org.das2.util.ArgumentList;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.EventQueue;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetListener;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TooManyListenersException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.help.CSH;
import javax.jnlp.SingleInstanceListener;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.xml.parsers.ParserConfigurationException;
import org.autoplot.help.AutoplotHelpSystem;
import org.autoplot.pngwalk.CreatePngWalk;
import org.autoplot.pngwalk.PngWalkTool;
import org.das2.DasApplication;
import org.das2.components.propertyeditor.ColorEditor;
import org.das2.components.propertyeditor.PropertyEditor;
import org.das2.datum.Datum;
import org.das2.datum.TimeParser;
import org.das2.datum.Units;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasCanvasComponent;
import org.das2.graph.DasPlot;
import org.das2.system.RequestProcessor;
import org.das2.util.ExceptionHandler;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.FileSystemSettings;
import org.das2.util.filesystem.KeyChain;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.autoplot.bookmarks.BookmarksException;
import org.autoplot.bookmarks.BookmarksManagerModel;
import org.autoplot.bookmarks.DelayMenu;
import org.autoplot.dom.Application;
import org.autoplot.dom.ApplicationController;
import org.autoplot.dom.BindingModel;
import org.autoplot.dom.DataSourceFilter;
import org.autoplot.dom.DomNode;
import org.autoplot.dom.PlotElement;
import org.autoplot.scriptconsole.JythonScriptPanel;
import org.autoplot.scriptconsole.LogConsole;
import org.autoplot.server.RequestHandler;
import org.autoplot.server.RequestListener;
import org.autoplot.dom.Options;
import org.autoplot.dom.OptionsPrefsController;
import org.autoplot.dom.Plot;
import org.autoplot.dom.PlotController;
import org.autoplot.scriptconsole.GuiExceptionHandler;
import org.autoplot.state.UndoRedoSupport;
import org.autoplot.util.TickleTimer;
import org.das2.qds.DataSetAnnotations;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.datasource.DataSetSelector;
import org.autoplot.datasource.DataSetSelectorSupport;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceFactory;
import org.autoplot.datasource.HtmlResponseIOException;
import org.autoplot.datasource.ReferenceCache;
import org.autoplot.datasource.SourceTypesBrowser;
import org.autoplot.datasource.TimeRangeEditor;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.WindowManager;
import org.autoplot.dom.DomUtil;
import org.das2.qds.filters.AddFilterDialog;
import org.das2.qds.filters.FiltersChainPanel;
import org.autoplot.jythonsupport.ui.DataMashUp;
import org.autoplot.jythonsupport.ui.EditorTextPane;
import org.autoplot.layout.LayoutConstants;
import org.autoplot.state.StatePersistence;
import org.autoplot.util.AutoRangeHintsStringSchemeEditor;
import org.autoplot.util.DataSetSelectorStringSchemeEditor;
import org.autoplot.util.FontStringSchemeEditor;
import org.autoplot.util.LayoutStringSchemeEditor;
import org.autoplot.util.PlotDataMashupResolver;
import org.das2.components.GrannyTextEditor;
import org.das2.components.propertyeditor.TickValuesStringSchemeEditor;
import org.das2.graph.GraphUtil;
import org.das2.components.propertyeditor.SpecialColorsStringSchemeEditor;
import org.python.core.PyException;
import org.python.util.PythonInterpreter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * The Autoplot application GUI.  This is the entry point for the application, wrapping the internal
 * application model with conveniences like bookmarks, time range editors and history.
 * 
 * @author  jbf
 */
public final class AutoplotUI extends javax.swing.JFrame {
    private static final String TAB_SCRIPT = "script";
    private static final String TAB_CONSOLE = "console";

    private static Thread getShutdownHook( final ApplicationModel model ) {
        Runnable run= () -> {
            logger.fine("shutting down");
            if ( model.isHeadless() ) {
                return;
            }
            File f2= new File( AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA), "log/" );
            if ( !f2.exists() ) {
                boolean ok= f2.mkdirs();
                if ( !ok ) {
                    logger.log(Level.WARNING, "unable to create folder {0}", f2);
                }
            }
            File f= new File( f2, "last.vap" );
            //f.setWritable( true, true );
            try {
                StatePersistence.saveState( f, model.createState(true), "");
                if ( !f.setReadable( false, false ) ) logger.info("setReadable failed");
                if ( !f.setReadable( true, true ) ) logger.info("setReadable failed");
                if ( !f.setWritable( false, false ) ) logger.info("setWritable failed");
                if ( !f.setWritable( true, true ) ) logger.info("setWritable failed");
            } catch (IOException ex) {
                logger.log(Level.WARNING, "error while writing  {0}: {1}", new Object[] { f2, ex.toString() } );
            }
        };
        return new Thread( run, "apshutdown" );
    }

    private static void setupMacMenuBarSoon() {
        Runnable run= () -> {
            try ( InputStream ins= AutoplotUI.class.getResourceAsStream("macMenuBar.jy") ) {
                logger.fine("adding additional actions for mac menu bar.");
                PythonInterpreter interp= JythonUtil.createInterpreter( true, false ); // static okay
                interp.execfile(ins,"macMenuBar.jy");
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        };
        new Thread(run).start();
    }
    
    /**
     * Autoplot adds additional macros to granny text strings for annotations and titles.
     * @return editor with extra tab.
     */
    public static GrannyTextEditor newGrannyTextEditorWithMacros() {
        GrannyTextEditor edit= GraphUtil.newGrannyTextEditor();
        edit.addButton( "macros", "%{CONTEXT} with format", "%{CONTEXT,format=%d}" );
        edit.addButton( "macros", "%{CONTEXT} with time", "%{CONTEXT,format=$H:$M}" );
        edit.addButton( "macros", "%{CONTEXT} with name", "%{CONTEXT,name=pitch}" );
        edit.addButton( "macros", "%{TIMERANGE}", "%{TIMERANGE}" );
        return edit;
    }

    final String TAB_TOOLTIP_CANVAS = "<html>Canvas tab contains the plot and plot elements.<br>Click on plot elements to select.<br>%s</html>";
    final String TAB_TOOLTIP_AXES = "<html>Adjust selected plot axes.<br>%s<html>";
    final String TAB_TOOLTIP_LOGCONSOLE = "<html>Log console displays log messages and stdout/stderr.<br>%s</html>";
    final String TAB_TOOLTIP_STYLE = "<html>Adjust selected plot element's colors, shapes, and other style settings.<br>%s</html>";
    final String TAB_TOOLTIP_LAYOUT = "<html>Inspect the canvas layout and property bindings, and<br>provides access to all plot elements.<br>%s</html>";
    final String TAB_TOOLTIP_DATA = "<html>Specify valid ranges and apply additional operations to data.<br>%s</html>";
    final String TAB_TOOLTIP_METADATA = "<html>Inspect selected element's metadata and data statistics.<br>%s</html>";
    final String TAB_TOOLTIP_SCRIPT = "<html>Editor panel for Jython scripts and data sources.<br>%s</html>";
    final String TABS_TOOLTIP = "Right-click or drag to undock.";

    public static final String CARD_DATA_SET_SELECTOR = "dataCard"; // NOTE THIS IS NOT USED IN GUI CODE!
    public static final String CARD_TIME_RANGE_SELECTOR = "timeCard";

    TearoffTabbedPane tabs;
    transient ApplicationModel applicationModel;
    Application dom;
    transient PersistentStateSupport stateSupport;
    final transient UndoRedoSupport undoRedoSupport;
    TickleTimer tickleTimer;
    transient GuiSupport support;
    transient LayoutListener autoLayout;
    private boolean dsSelectTimerangeBound= false; // true if there is a binding between the app timerange and the dataSetSelector.
    
    // true means don't bring up an initial security dialog asking
    private boolean noAskParams;

    /**
     * the vap that is currently loading.  We keep track of this so we can push it to the top of the recent list.
     */
    String pendingVap= null; 
    
    // if non-null, then load this set of initial bookmarks.
    private String initialBookmarksUrl= null;
    
    String applicationName= "";
    
    private String apversion=null;
    
    private EventThreadResponseMonitor responseMonitor;
    
    /**
     * return the monitor, if enabled, so that logging can be enabled.
     * @return 
     */
    public EventThreadResponseMonitor getResponseMonitor() {
        return responseMonitor;
    }
    
    void setApplicationName(String id) {
        this.applicationName= id;
        if ( DomUtil.getElementById(dom, id)==null ) { // make sure there are no other nodes with this id.
            this.dom.setId(id);
        }
    }
    
    transient PersistentStateSupport.SerializationStrategy serStrategy = new PersistentStateSupport.SerializationStrategy() {
        @Override
        public Element serialize(Document document, ProgressMonitor monitor) {
            DOMBuilder builder = new DOMBuilder(applicationModel);
            Element element = builder.serialize(document, DasProgressPanel.createFramed("Serializing Application"));
            return element;
        }
        @Override
        public void deserialize(Document document, ProgressMonitor monitor) {
            Element element = document.getDocumentElement();
            SerializeUtil.processElement(element, applicationModel);
        }
    };
    
    private static final Logger logger = org.das2.util.LoggerManager.getLogger("autoplot.gui");
    private static final Logger resizeLogger= Logger.getLogger("autoplot.dom.canvas.resize");
    
    private JythonScriptPanel scriptPanel;
    private DataPanel dataPanel;
    private LayoutPanel layoutPanel;
    private JScrollPane layoutPanel1;
    private LogConsole logConsole;
    private JScrollPane logConsolePanel;
    private JPanel jythonScriptPanel;
    private transient RequestListener rlistener;
    private JDialog fontAndColorsDialog = null;
    private BookmarksManager bookmarksManager = null;
    private BookmarksManager toolsManager = null;
    private AutoplotHelpSystem helpSystem;
    private transient UriDropTargetListener dropListener;

    private static final String RESOURCES= "/org/autoplot/resources/";
    
    /**
     * yellow triangle with exclamation point, used to indicate warning condition.
     */
    public static final Icon WARNING_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"warning-icon.png") );
    /**
     * red stop sign with exclamation point, using to indicate error condition.
     */
    public static final Icon ERROR_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"error-icon.png") );
    /**
     * animated gif of swirling dots, used to indicate known busy state.
     */
    public static final Icon BUSY_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"spinner.gif") );
    /**
     * animated gif of swirling dots, used to indicate known busy state.
     */
    public static final Icon BUSY_OPAQUE_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"spinner_16.gif") );
    /**
     * not used.
     */
    public static final Icon READY_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"indProgress0.png") );
    /**
     * empty 16x16 image used to indicate normal status.
     */
    public static final Icon IDLE_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"idle-icon.png") );

    /**
     * ready message.
     */
    public static final String READY_MESSAGE= "ready";
    
    /**
     * the app has been asked to plot a URI
     */
    private static final String PENDING_CHANGE_PLOTURI= "plotUri";

    /**
     * the app has been launched with a URI
     */
    private static final String PENDING_CHANGE_INITIAL_URI= "initialUri";
    
    private TimeRangeEditor timeRangeEditor;
    private List<JComponent> expertMenuItems= new ArrayList(); // list of items to hide
    private JMenu expertMenu;

    private transient Timer apbusy= new Timer("apbusy", true);
            
    /**
     * utility for mucking around with the guis to figure out why it can't shrink.  It was because the JComboBox on the
     * timerange panel had long timeranges.
     */
    //public List ohno() {
    //    ArrayList l= new ArrayList();
    //    l.add( tabbedPanelContainer );
    //    l.add( statusLabel );
    //    l.add( timeRangePanel );
    //    l.add( tabs );
    //    return l;
    //}
    
    /** 
     * Creates new form AutoplotUI 
     * @param model the legacy model that backs the application.
     */
    public AutoplotUI(ApplicationModel model) {
                    
        apversion= APSplash.getVersion();
        if ( apversion.equals("untagged_version") ) {
            apversion= "(dev)";
        }
        if ( apversion.equals("(dev)") ) {
            apversion= "(dev"+getProcessId("")+")";
        }

        setIconImage( AutoplotUtil.getAutoplotIcon() );
        
        APSplash.checkTime("init 0");

        File toolsDir= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ), "tools" );
        File booksDir= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ), "bookmarks" );
        if ( !toolsDir.exists() ) {
            if ( !toolsDir.mkdirs() ) {
                logger.log(Level.WARNING, "unable to make directory: {0}", toolsDir);
            }
        }
        if ( !booksDir.exists() ) {
            if ( !booksDir.mkdirs() ) {
                logger.log(Level.WARNING, "unable to make directory: {0}", booksDir);
            }
        }
        
        if ( System.getProperty( "noCheckCertificate","true").equals("true") ) {
            if ( model.isSandboxed() ) {
                logger.warning( "unable to disable certificates because of sandbox");
                System.setProperty(SYSPROP_AUTOPLOT_DISABLE_CERTS, String.valueOf(false) );
            } else {
                AutoplotUtil.disableCertificates();
                System.setProperty(SYSPROP_AUTOPLOT_DISABLE_CERTS, String.valueOf(true) );
            }
        } else {
            System.setProperty(SYSPROP_AUTOPLOT_DISABLE_CERTS, String.valueOf(false) );
        }

        // Initialize help system now so it's ready for components to register IDs with
        AutoplotHelpSystem.initialize(getRootPane());
        helpSystem = AutoplotHelpSystem.getHelpSystem();

        DasApplication.getDefaultApplication().setMainFrame( this );

        if ( DasApplication.getDefaultApplication().isHeadless() ) {
            model.setExceptionHandler( DasApplication.getDefaultApplication().getExceptionHandler() );
        } else {
            model.setExceptionHandler( new GuiExceptionHandler() );
        }

        applicationModel = model;
        this.dom= model.getDom();
        ScriptContext2023 scriptContext= new ScriptContext2023();
        this.dom.getController().setScriptContext( scriptContext );
        
        if ( !scriptContext.isModelInitialized() ) {
            scriptContext.setApplicationModel(model);
            scriptContext.setView(this);
            scriptContext._setDefaultApp(this);
        }

        model.setResizeRequestListener( (int w, int h) -> resizeForCanvasSize(w, h) );

        APSplash.checkTime("init 10");
        
        support = new GuiSupport(this);

        applicationModel = model;
        undoRedoSupport = new UndoRedoSupport(applicationModel);
        undoRedoSupport.addPropertyChangeListener((PropertyChangeEvent evt) -> {
            SwingUtilities.invokeLater(() -> {
                refreshUndoRedoLabel();
            });
        });

        applicationModel.addPropertyChangeListener(ApplicationModel.PROP_VAPFILE, (PropertyChangeEvent evt) -> {
            updateFrameTitle();
        });

        undoRedoSupport.addPropertyChangeListener(UndoRedoSupport.PROP_DEPTH, (PropertyChangeEvent evt) -> {
            updateFrameTitle();
        });

        APSplash.checkTime("init 20");

        FileSystem.settings().addPropertyChangeListener(FileSystemSettings.PROP_OFFLINE, (PropertyChangeEvent evt) -> {
            updateFrameTitle();
        });
        
        if ( model.getExceptionHandler() instanceof GuiExceptionHandler ) {
            ((GuiExceptionHandler)model.getExceptionHandler()).setUndoRedoSupport(undoRedoSupport);
        }
        
        initComponents();
        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        //TODO: this needs to be explored more:  It gets the desired behavior where running an 
        //Autoplot script doesn't steal focus (see sftp:papco.org:/home/jbf/ct/autoplot/script/fun/jeremy/randImages.jy)
        //but makes it so URIs cannot be entered. https://sourceforge.net/tracker/index.php?func=detail&aid=3532217&group_id=199733&atid=970682
        //this.setFocusableWindowState(false);
                
        referenceCacheCheckBoxMenuItem.setSelected( System.getProperty( "enableReferenceCache", "true" ).equals("true") ); 
        
        expertMenuItems.add( editDomMenuItem );
        expertMenuItems.add( editDomSeparator );
        expertMenuItems.add( inspectVapFileMenuItem );
        expertMenuItems.add( renderingOptionsMenu );
        expertMenuItems.add( enableFeatureMenu );
        expertMenuItems.add( autoMenu );
        expertMenuItems.add( pngWalkMenuItem );
        expertMenuItems.add( createPngWalkMenuItem );
        expertMenuItems.add( createPngWalkSeparator );
        expertMenuItems.add( aggSeparator );
        expertMenuItems.add( aggregateMenuItem );
        expertMenuItems.add( decodeURLItem );

        if ( !"true".equals( System.getProperty("apple.laf.useScreenMenuBar") ) ) {
            jMenuBar1.add( Box.createHorizontalGlue() );
        }
        expertMenu= new JMenu("Expert");
        JMenuItem mi;
        mi= new JMenuItem( new AbstractAction( "Basic Mode") {
            @Override
            public void actionPerformed( ActionEvent e ) {
                org.das2.util.LoggerManager.logGuiEvent(e);               
                if ( isExpertMode() ) {
                    setExpertMode(false);
                }
            }
        });
        mi.setToolTipText("Basic mode allows for browsing products composed by data providers");
        expertMenu.add( mi );
        mi= new JMenuItem( new AbstractAction( "Expert Mode") {
            @Override
            public void actionPerformed( ActionEvent e ) {
                org.das2.util.LoggerManager.logGuiEvent(e);                              
                if ( isBasicMode() ) {
                    setExpertMode(true);
                }
            }
        });
        mi.setToolTipText("Expert allows composing new products and scripting");
        expertMenu.add( mi );
        expertMenu.setToolTipText("<html>Toggle between expert and basic mode.<br>Basic mode allows for browsing products composed by data providers<br>Expert allows composing new products and scripting");
        if ( !"true".equals( System.getProperty("apple.laf.useScreenMenuBar") ) ) {
            jMenuBar1.add( expertMenu );
        }

        KeyChain.getDefault().setParentGUI(this);
        
        APSplash.checkTime("init 25");

        timeRangeEditor = new TimeRangeEditor();
//        Dimension d= new Dimension( 1000, (int)( timeRangeEditor.getFont().getSize()*1.7 ) );
//               
//        if ( "true".equals(System.getProperty("showTimeAndUriEditors")) ) {
//            timeUriPanel.remove(timeRangePanel);
//            timeUriPanel.setLayout( new BoxLayout(timeUriPanel, BoxLayout.Y_AXIS ) );
//            
//            timeUriPanel.removeAll();
//            timeUriPanel.add( Box.createVerticalStrut(4) );
//            JPanel rowInsetPanel= new JPanel();
//            rowInsetPanel.setPreferredSize( new Dimension( timeRangeEditor.getPreferredSize().width, d.height ) );
//            rowInsetPanel.setMaximumSize( new Dimension( 10000, timeRangeEditor.getFont().getSize() ) );
//            rowInsetPanel.setLayout( new BoxLayout(rowInsetPanel,BoxLayout.X_AXIS ) );
//            rowInsetPanel.add( Box.createHorizontalStrut(5) );
//            rowInsetPanel.add( timeRangeEditor );
//            rowInsetPanel.add( Box.createHorizontalStrut(5) );
//            timeUriPanel.add( rowInsetPanel );
//            rowInsetPanel= new JPanel();
//            rowInsetPanel.setLayout( new BoxLayout(rowInsetPanel,BoxLayout.X_AXIS ) );
//            rowInsetPanel.add( Box.createHorizontalStrut(5) );
//            rowInsetPanel.add( dataSetSelector );
//            rowInsetPanel.add( Box.createHorizontalStrut(5) );
//            timeUriPanel.add( rowInsetPanel  );
//            timeUriPanel.add( Box.createVerticalStrut(2) );
//            
//            timeUriPanel.setMinimumSize( new Dimension( timeUriPanel.getMinimumSize().width, d.height*2 ) );    
//            timeUriPanel.setSize( new Dimension( d.width, d.height*3 ) ); 
//
//            timeUriPanel.setMaximumSize( new Dimension( 10000, d.height*2 ) );
//            dataSetSelector.setAlignmentX( Component.RIGHT_ALIGNMENT );
//            dataSetSelector.setMaximumSize( new Dimension( 10000, d.height ) );
//            timeRangeEditor.setAlignmentX( Component.RIGHT_ALIGNMENT );
//            timeRangeEditor.setMaximumSize( new Dimension( 10000, d.height ) );
//            tabbedPanelContainer.setLocation( 0, 300 );
//            
//            this.revalidate();
//        } else {
//            timeRangePanel.add( timeRangeEditor, "card1" );  NONONO
//            timeRangePanel.setMinimumSize( new Dimension( timeUriPanel.getMinimumSize().width, d.height ) );
//            timeRangeEditor.setDataSetSelectorPeer(dataSetSelector);
//            timeRangeEditor.setAlternatePeer("Switch to Data Set Selector","card2"); NONONO
//            dataSetSelector.setAlternatePeer("Switch to Time Range Editor","card1"); NONONO
//        }
        
        Dimension d= timeRangeEditor.getMinimumSize();
        timeRangePanel.add( timeRangeEditor, CARD_TIME_RANGE_SELECTOR );
        timeRangeEditor.setMinimumSize( d );
        timeRangePanel.setMinimumSize( d );
        timeRangeEditor.setDataSetSelectorPeer(dataSetSelector);
        timeRangeEditor.setAlternatePeer("Switch to Data Set Selector", CARD_DATA_SET_SELECTOR );
        dataSetSelector.setAlternatePeer("Switch to Time Range Editor", CARD_TIME_RANGE_SELECTOR );
        dataSetSelector.setCardSelected(true);
        timeRangeEditor.addPropertyChangeListener(TimeRangeEditor.PROP_CARDSELECTED, (PropertyChangeEvent evt) -> {
            if ( evt.getNewValue().equals(Boolean.TRUE) ) {
                setEditorCard( CARD_TIME_RANGE_SELECTOR );
                dataSetSelector.setCardSelected( false );
            } else {
                setEditorCard( CARD_DATA_SET_SELECTOR );
                dataSetSelector.setCardSelected( true );
            }
        });
        dataSetSelector.addPropertyChangeListener(DataSetSelector.PROP_CARDSELECTED, (PropertyChangeEvent evt) -> {
            if ( evt.getNewValue().equals(Boolean.TRUE) ) {
                setEditorCard( CARD_DATA_SET_SELECTOR );
                timeRangeEditor.setCardSelected( false );
            } else {
                setEditorCard( CARD_TIME_RANGE_SELECTOR );
                timeRangeEditor.setCardSelected( true );
            }
        });
        uriTimeRangeToggleButton1.addPropertyChangeListener(UriTimeRangeToggleButton.PROP_POSITION, (PropertyChangeEvent evt) -> {
            if ( evt.getNewValue().equals(1) ) {
                setEditorCard( CARD_DATA_SET_SELECTOR );
                timeRangeEditor.setCardSelected( false );
            } else {
                setEditorCard( CARD_TIME_RANGE_SELECTOR );
                timeRangeEditor.setCardSelected( true );
            }
        });

        timeRangeEditor.setNoOneListeningRange( Application.DEFAULT_TIME_RANGE );
        timeRangeEditor.setRange( Application.DEFAULT_TIME_RANGE );

        dom.getController().addPropertyChangeListener( ApplicationController.PROP_FOCUSURI, timeRangeEditor.getUriFocusListener() );
        
        this.statusTextField.setBackground( new Color(0.f,0.f,0.f,0.f) );
        this.statusTextField.setOpaque(false);

        statusLabel.setIcon(IDLE_ICON);
        support.addKeyBindings((JPanel) getContentPane());

        APSplash.checkTime("init 30");

        dataSetSelector.setMonitorFactory( dom.getController().getMonitorFactory() );
        dataSetSelector.registerBrowseTrigger( "vap\\+internal:(.*)", new AbstractAction("internal") {
            @Override
            public void actionPerformed( ActionEvent ev ) {
                org.das2.util.LoggerManager.logGuiEvent(ev);
                GuiSupport.editPlotElement( applicationModel, AutoplotUI.this );
            }
        });

        dataSetSelector.registerActionTrigger( "bookmarks:(.*)", new AbstractAction( "bookmarks") {
            @Override
            public void actionPerformed( final ActionEvent ev ) {
                org.das2.util.LoggerManager.logGuiEvent(ev);
                Runnable run = () -> {
                    String bookmarksFile= dataSetSelector.getValue().substring("bookmarks:".length());
                    if ( bookmarksFile.endsWith("/") || bookmarksFile.endsWith(".")) { // normally reject method would trigger another completion
                        DataSetSelector source= (DataSetSelector)ev.getSource();
                        source.showFileSystemCompletions( true, false, "[^\\s]+[^\\s]+(\\.(?i)(xml)|(xml\\.gz))$" );
                    } else {
                        while ( getBookmarksManager()==null || getBookmarksManager().getModel()==null || getBookmarksManager().getModel().getList()==null ) {
                            logger.fine("waiting for bookmarks manager to be initialized");
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException ex) {
                                logger.log(Level.SEVERE, ex.getMessage(), ex);   
                            }
                        }
                        if ( ! getBookmarksManager().haveRemoteBookmark(bookmarksFile) ) {
                            support.importBookmarks( bookmarksFile );
                            applicationModel.addRecent(dataSetSelector.getValue());
                        } else {
                            setStatus( "remote bookmarks file is already imported"  );
                        }
                    }
                };
                new Thread( run, "bookmarksUri" ).start();
            }
        });
        dataSetSelector.registerBrowseTrigger( "bookmarks:(.*)", new AbstractAction( "bookmarks") {
            @Override
            public void actionPerformed( ActionEvent ev ) {
                org.das2.util.LoggerManager.logGuiEvent(ev);                
                DataSetSelector source= (DataSetSelector)ev.getSource();
                source.showFileSystemCompletions( false, true, "[^\\s]+(\\.(?i)(xml)|(xml\\.gz))$" );
            }
        });
        dataSetSelector.registerActionTrigger( "pngwalk:(.*)", new AbstractAction( "pngwalk") {
            @Override
            public void actionPerformed( ActionEvent ev ) { // TODO: underimplemented
                org.das2.util.LoggerManager.logGuiEvent(ev);                
                String pngwalk= dataSetSelector.getValue().substring("pngwalk:".length());
                if ( pngwalk.endsWith("/") || pngwalk.endsWith(".")) { // normally reject method would trigger another completion
                    DataSetSelector source= (DataSetSelector)ev.getSource();
                    source.showFileSystemCompletions( true, false, "[^\\s]+(\\.(?i)(jpg|png|gif))$" ); // we can't easily search for .pngwalk here because of inclfiles
                } else {
                    PngWalkTool.start( pngwalk, AutoplotUI.this);
                    applicationModel.addRecent(dataSetSelector.getValue());
                }
            }
        });
        dataSetSelector.registerBrowseTrigger( "pngwalk:(.*)", new AbstractAction( "pngwalk") {
            @Override
            public void actionPerformed( ActionEvent ev ) {
                org.das2.util.LoggerManager.logGuiEvent(ev);                
                DataSetSelector source= (DataSetSelector)ev.getSource();
                source.showFileSystemCompletions( true, false, "[^\\s]+(\\.(?i)(jpg|png|gif))$" );
                //do nothing
            }
        });
        dataSetSelector.registerActionTrigger( "(.*)\\.pngwalk", new AbstractAction( "pngwalk") {
            @Override
            public void actionPerformed( ActionEvent ev ) { // TODO: underimplemented
                org.das2.util.LoggerManager.logGuiEvent(ev);                
                applicationModel.addRecent(dataSetSelector.getValue());
                String pngwalk= dataSetSelector.getValue();
                PngWalkTool.start( pngwalk, AutoplotUI.this);
            }
        });
        dataSetSelector.registerActionTrigger( ".*(\\*).*\\.(png|jpg|gif)", new AbstractAction( "pngwalk") {
            @Override
            public void actionPerformed( ActionEvent ev ) { // TODO: underimplemented
                org.das2.util.LoggerManager.logGuiEvent(ev);                
                applicationModel.addRecent(dataSetSelector.getValue());
                String pngwalk= dataSetSelector.getValue();
                PngWalkTool.start( pngwalk, AutoplotUI.this);
            }
        });
        dataSetSelector.registerActionTrigger( ".*\\$x.*\\.(png|jpg|gif)", new AbstractAction( "pngwalk") {
            @Override
            public void actionPerformed( ActionEvent ev ) { // TODO: underimplemented
                org.das2.util.LoggerManager.logGuiEvent(ev);                
                applicationModel.addRecent(dataSetSelector.getValue());
                String pngwalk= dataSetSelector.getValue();
                PngWalkTool.start( pngwalk, AutoplotUI.this);
            }
        });
        dataSetSelector.registerActionTrigger( ".*\\$x.*\\$x\\.(png|jpg|gif)", new AbstractAction( "pngwalk") {
            @Override
            public void actionPerformed( ActionEvent ev ) { // TODO: underimplemented
                org.das2.util.LoggerManager.logGuiEvent(ev);                
                applicationModel.addRecent(dataSetSelector.getValue());
                String pngwalk= dataSetSelector.getValue();
                PngWalkTool.start( pngwalk, AutoplotUI.this);
            }
        });
        
        dataSetSelector.registerActionTrigger( "http.*/hapi(/?)(info\\?.*)?", new AbstractAction( "hapiServer") {
            @Override
            public void actionPerformed( final ActionEvent ev ) { 
                org.das2.util.LoggerManager.logGuiEvent(ev);                
                final String value= dataSetSelector.getValue();
                Pattern p= Pattern.compile("(http.*/hapi)(/?)(info\\?id=(.*))?");
                Matcher m= p.matcher(value);
                final String newValue;
                if ( m.matches() ) {
                    String id= m.group(4);
                    if ( id!=null ) {
                        newValue= "vap+hapi:" + m.group(1) + m.group(2)+"?id="+ id;
                    } else {
                        newValue= "vap+hapi:" + m.group(1);
                    }
                } else {
                    newValue= "vap+hapi:";
                }
                Runnable run= () -> {
                    dataSetSelector.setValue(newValue);
                    dataSetSelector.maybePlot( ev.getModifiers() );
                };
                SwingUtilities.invokeLater(run);
            }
        });  
        
        dataSetSelector.registerBrowseTrigger( "http.*/hapi(/?)(info\\?.*)?", new AbstractAction( "hapiServer") {
            @Override
            public void actionPerformed( final ActionEvent ev ) {
                org.das2.util.LoggerManager.logGuiEvent(ev);                
                final String value= dataSetSelector.getValue();
                Pattern p= Pattern.compile("(http.*/hapi)(/?)(info\\?id=(.*))?");
                Matcher m= p.matcher(value);
                final String newValue;
                if ( m.matches() ) {
                    String id= m.group(4);
                    if ( id!=null ) {
                        newValue= "vap+hapi:" + m.group(1) + "?id="+ id;
                    } else {
                        newValue= "vap+hapi:" + m.group(1);
                    }
                } else {
                    newValue= "vap+hapi:";
                }               
                Runnable run= () -> {
                    dataSetSelector.setValue(newValue);
                    dataSetSelector.maybePlot( ev.getModifiers() );
                };
                SwingUtilities.invokeLater(run);
            }
        });  
        dataSetSelector.registerActionTrigger( "http.*/hapi(/data\\?.*)?", new AbstractAction( "hapiServer") {
            @Override
            public void actionPerformed( final ActionEvent ev ) { 
                org.das2.util.LoggerManager.logGuiEvent(ev);                
                final String value= dataSetSelector.getValue();
                URISplit split= URISplit.parse(value);
                if ( split.file.endsWith("/data") ) {
                    split.file= split.file.substring(0,split.file.length()-5);
                }
                Map<String,String> params= URISplit.parseParams(split.params);
                if ( params.containsKey("time.min") && params.containsKey("time.max") ) {
                    params.put( "timerange", params.get("time.min")+"/"+params.get("time.max") );
                    params.remove("time.min");
                    params.remove("time.max");
                }
                //TODO-HAPI: time.min is being replaced in HAPI 3.0
                split.vapScheme= "vap+hapi";
                split.params= URISplit.formatParams(params);
                final String newValue= URISplit.format(split);
                Runnable run= () -> {
                    dataSetSelector.setValue(newValue);
                    dataSetSelector.maybePlot( ev.getModifiers() );
                };
                SwingUtilities.invokeLater(run);
            }
        });  
        
        dataSetSelector.registerBrowseTrigger( "http.*/hapi(/data\\?.*)?", new AbstractAction( "hapiServer") {
            @Override
            public void actionPerformed( final ActionEvent ev ) {
                org.das2.util.LoggerManager.logGuiEvent(ev);                
                final String value= dataSetSelector.getValue();
                URISplit split= URISplit.parse(value);
                if ( split.file.endsWith("/data") ) {
                    split.file= split.file.substring(0,split.file.length()-5);
                }
                Map<String,String> params= URISplit.parseParams(split.params);
                if ( params.containsKey("time.min") && params.containsKey("time.max") ) {
                    params.put( "timerange", params.get("time.min")+"/"+params.get("time.max") );
                    params.remove("time.min");
                    params.remove("time.max");
                }
                //TODO-HAPI: time.min is being replaced in HAPI 3.0
                split.vapScheme= "vap+hapi";
                split.params= URISplit.formatParams(params);
                final String newValue= URISplit.format(split);
                Runnable run= () -> {
                    dataSetSelector.setValue(newValue);
                    dataSetSelector.maybePlot( ev.getModifiers() );
                };
                SwingUtilities.invokeLater(run);
            }
        });  
                        
        dataSetSelector.registerActionTrigger( "(.*)\\.jy(\\?.*)?", new AbstractAction( TAB_SCRIPT) {
            @Override
            public void actionPerformed( ActionEvent ev ) {
                if ( ScriptContext.getViewWindow()==AutoplotUI.this ) {
                    org.das2.util.LoggerManager.logGuiEvent(ev);                    
                    runScript( dataSetSelector.getValue(), !AutoplotUI.this.noAskParams );
                } else {
                    org.das2.util.LoggerManager.logGuiEvent(ev);     
                    
//                    if ( JOptionPane.YES_OPTION==
//                            JOptionPane.showConfirmDialog( AutoplotUI.this, "Scripts can only be run from the main window.  Make this the main window?", 
//                                    "Set Main Window", JOptionPane.YES_NO_OPTION ) ) {
//                        ScriptContext.setApplication(AutoplotUI.this);
//                    }
                    runScript( dataSetSelector.getValue() );
                }
                dom.getController().setFocusUri(ApplicationController.VALUE_BLUR_FOCUS);
            }
        });

        /**
         * register the browse trigger to the same action, because we always browse.
         */
        dataSetSelector.registerBrowseTrigger( "(.*)\\.jy(\\?.*)?", new AbstractAction( TAB_SCRIPT ) {
            @Override
            public void actionPerformed( ActionEvent ev ) {
                if ( ScriptContext.getViewWindow()==AutoplotUI.this ) {
                    org.das2.util.LoggerManager.logGuiEvent(ev);                    
                    String s= dataSetSelector.getValue();
                    int i= dataSetSelector.getEditor().getCaretPosition();
                    if ( i==0 || i<s.length() || s.substring(i-1).contains("/") ) {
                            dataSetSelector.showCompletions();
                        return;
                    }
                    Map<String,String> args;
                    try {
                        URISplit split= URISplit.parse(s);        //bug 1408--note runScript doesn't account for changes made to the GUI.
                        args= URISplit.parseParams(split.params);
                        JythonRunListener runListener= makeJythonRunListener( AutoplotUI.this, split.resourceUri, true );
                        if ( JOptionPane.OK_OPTION==JythonUtil.invokeScriptSoon( split.resourceUri, dom, 
                                args, true, true, runListener, new NullProgressMonitor() ) ) {
                            split.params= URISplit.formatParams(args);
                            if ( split.params.trim().length()==0 ) split.params=null;
                            String history= URISplit.format(split);
                            dataSetSelector.setValue( history );
                            applicationModel.addRecent( history );
                        }
                    } catch ( IOException ex ) { 
                        throw new RuntimeException(ex);
                    }
                } else {
                    org.das2.util.LoggerManager.logGuiEvent(ev);  
                    //if ( JOptionPane.YES_OPTION==
                    //        JOptionPane.showConfirmDialog( AutoplotUI.this, "Scripts can only be run from the main window.  Make this the main window?", 
                    //                "Set Main Window", JOptionPane.YES_NO_OPTION ) ) {
                    //    ScriptContext.setApplicationModel(AutoplotUI.this.applicationModel);
                    //    ScriptContext.setView(AutoplotUI.this);
                    //}
                    runScript( dataSetSelector.getValue() );
                }
                dom.getController().setFocusUri(ApplicationController.VALUE_BLUR_FOCUS);                
            }
        });

        dataSetSelector.registerActionTrigger( "script:(.*)", new AbstractAction( TAB_SCRIPT) {
            @Override
            public void actionPerformed( ActionEvent ev ) {
                org.das2.util.LoggerManager.logGuiEvent(ev);                    
                String script = dataSetSelector.getValue().substring("script:".length());
                if ( !( script.endsWith(".jy") || script.endsWith(".JY") || script.endsWith(".py") || script.endsWith(".PY") ) ) {
                    DataSetSelector source= (DataSetSelector)ev.getSource();
                    source.showFileSystemCompletions( false, true, "[^\\s]+\\.jy" );
                } else {
                    applicationModel.addRecent(dataSetSelector.getValue());
                    runScript( script );
                }
                dom.getController().setFocusUri(ApplicationController.VALUE_BLUR_FOCUS);
            }
        });
        dataSetSelector.registerBrowseTrigger( "script:(.*)", new AbstractAction( "script") {
            @Override
            public void actionPerformed( ActionEvent ev ) {
                org.das2.util.LoggerManager.logGuiEvent(ev);        
                DataSetSelector source= (DataSetSelector)ev.getSource();
                String s= source.getValue();
                if ( s.endsWith(".jy") ) {
                    try {
                        URI uri= DataSetURI.getResourceURI(s);
                        JythonRunListener runListener= makeJythonRunListener( AutoplotUI.this, uri, true );
                        JythonUtil.invokeScriptSoon( uri, dom, 
                                new HashMap(), true, true, runListener, new NullProgressMonitor() );
                    } catch ( IOException ex ) {
                        throw new RuntimeException(ex);
                    }
                } else {
                    source.showFileSystemCompletions( false, true, "[^\\s]+\\.jy" );
                }
                dom.getController().setFocusUri(ApplicationController.VALUE_BLUR_FOCUS);            
                //do nothing
            }
        });

        dataSetSelector.registerBrowseTrigger( "vapfile:(.*)", new AbstractAction( "vapfile") {
            @Override
            public void actionPerformed( ActionEvent ev ) {
                org.das2.util.LoggerManager.logGuiEvent(ev);                    
                DataSetSelector source= (DataSetSelector)ev.getSource();
                source.showFileSystemCompletions( false, true, "[^\\s]+(\\.(?i)(vap)|(vap\\.gz))$" );
            }
        });
        dataSetSelector.registerActionTrigger( "vapfile:(.*)", new AbstractAction( "vapfile") {
            @Override
            public void actionPerformed( ActionEvent ev ) { // TODO: underimplemented
                org.das2.util.LoggerManager.logGuiEvent(ev);
                final String vapfile= dataSetSelector.getValue().substring(8);
                URISplit split= URISplit.parse(vapfile);
                if ( !( vapfile.endsWith(".xml") ) && ( split.params==null || split.params.length()==0 ) ) {
                    DataSetSelector source= (DataSetSelector)ev.getSource();
                    source.showFileSystemCompletions( false, true, "[^\\s]+\\.jy" );
                } else {
                    Runnable run= () -> {
                        applicationModel.addRecent(dataSetSelector.getValue());
                        InputStream in=null;
                        try {
                            if ( vapfile.startsWith("http:") || vapfile.startsWith("https:") ) {
                                in= new URL(vapfile).openStream();
                            } else {
                                in = DataSetURI.getInputStream( DataSetURI.toUri( vapfile ), new NullProgressMonitor() );
                            }
                            applicationModel.doOpenVap( in, null );
                        } catch ( IOException ex ) {
                            JOptionPane.showMessageDialog( AutoplotUI.this, "Unable to load: \n"+vapfile+"\n"+ex );
                        } finally {
                            try {
                                if ( in!=null ) in.close();
                            } catch ( IOException ex2 ) {
                                logger.log(Level.WARNING,null,ex2);
                            }
                        }
                        dom.getController().setFocusUri(ApplicationController.VALUE_BLUR_FOCUS);
                    };
                    RequestProcessor.invokeLater(run);
                }
            }
        });
        
        dataSetSelector.registerBrowseTrigger( ".*\\.vap(\\?.*)?", new AbstractAction("vap file" ){
            @Override
            public void actionPerformed(ActionEvent e) {
                String surl= dataSetSelector.getValue();
                //URISplit split= URISplit.parse(surl);
                boolean blurFocus= false;
                //if ( split.path.startsWith("file:") ) {
                    String result= DataSetSelectorSupport.browseLocalVap( dataSetSelector, surl);
                    if (result != null ) {
                        dataSetSelector.setValue(result);
                        dataSetSelector.maybePlot(false);
                        pendingVap= result;
                        blurFocus= true;
                    }
                //} else {
                //    JOptionPane.showMessageDialog( AutoplotUI.this, "Unable to inspect remote .vap files" );
                //}
                setCursor( Cursor.getDefaultCursor() );
                if ( blurFocus ) dom.getController().setFocusUri(ApplicationController.VALUE_BLUR_FOCUS);
            }
        });

        URISplit.setOtherSchemes( Arrays.asList( "script","pngwalk", "bookmarks","vapfile") );

        APSplash.checkTime("init 40");

        final ApplicationController appController= applicationModel.getDocumentModel().getController();

        appController.addDas2PeerChangeListener((PropertyChangeEvent e) -> {
            PlotController plotController= (PlotController) e.getNewValue();
            ApplicationController controller= plotController.getApplication().getController();
            GuiSupport.addPlotContextMenuItems( AutoplotUI.this, controller, plotController.getDasPlot(), plotController, plotController.getPlot() );
            GuiSupport.addAxisContextMenuItems(  controller,  plotController.getDasPlot(), plotController,  plotController.getPlot(), plotController.getPlot().getXaxis());
            GuiSupport.addAxisContextMenuItems( controller,  plotController.getDasPlot(), plotController,  plotController.getPlot(), plotController.getPlot().getYaxis());
            GuiSupport.addAxisContextMenuItems(  controller,  plotController.getDasPlot(), plotController,  plotController.getPlot(), plotController.getPlot().getZaxis());
        });

        appController.addPropertyChangeListener(ApplicationController.PROP_FOCUSURI, (PropertyChangeEvent evt) -> {
            SwingUtilities.invokeLater(() -> {
                if ( pendingVap==null ) { // non-null means we are loading something.
                    if ( !isBasicMode() ) {
                        dataSetSelector.setValue( appController.getFocusUri() );
                    }
                }
            });
        });
        dataSetSelector.setValue( dom.getController().getFocusUri() );
        
        appController.addPropertyChangeListener( ApplicationController.PROP_STATUS, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                setStatus(appController.getStatus());
            }
        } );
        
        APSplash.checkTime("init 50");

        setIconImage( AutoplotUtil.getAutoplotIcon() );
        APSplash.checkTime("init 50.5");
        updateFrameTitle();
        
        stateSupport = getPersistentStateSupport(this, applicationModel);
        
        applicationModel.getCanvas().addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                logger.fine("focus to canvas");
                if ( stateSupport==null ) {
                    System.err.println("stateSupport is null");
                    return;
                }
                if ( stateSupport.getCurrentFile() != null) {
                    dataSetSelector.setValue(stateSupport.getCurrentFile());
                }
                super.focusGained(e);
            }
        });
        
        fillFileMenu(); // init 51,52
        APSplash.checkTime("init 52.999");
        fillInitialBookmarksMenu();
        APSplash.checkTime("init 53");

        this.setName("autoplot");
        AppManager.getInstance().addApplication(this);
        AppManager.getInstance().addCloseCallback(this, "recordPositionSize", () -> {
            WindowManager.getInstance().recordWindowSizePosition(AutoplotUI.this);
            return true;
        });
        this.addWindowListener( AppManager.getInstance().getWindowListener(this,new AbstractAction("close") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);
                ScriptContext2023 s2c= dom.getController().getScriptContext();
                if ( AutoplotUI.this== s2c.getViewWindow()  ) {
                    dom.getController().getScriptContext().close();
                }
                //TODO: remove the following prefs.
                final Preferences prefs= AutoplotSettings.settings().getPreferences(ApplicationModel.class);
                long x= AutoplotUI.this.getLocation().x;
                long y= AutoplotUI.this.getLocation().y;
                logger.log( Level.FINE, "saving last location {0} {1}", new Object[]{x, y});
                prefs.putInt( "locationx", AutoplotUI.this.getLocation().x );
                prefs.putInt( "locationy", AutoplotUI.this.getLocation().y );
                prefs.putInt( "locationscreenwidth", java.awt.Toolkit.getDefaultToolkit().getScreenSize().width );
            }
        }) );
        
        final Logger resizeLogger= LoggerManager.getLogger("autoplot.dom.canvas.resize");
        
        this.addComponentListener( new ComponentListener() {
            @Override
            public void componentResized(ComponentEvent e) {
                //int w= AutoplotUI.this.getWidth();
                //int h= AutoplotUI.this.getHeight();
                //if ( w<430 && h>800 ) {
                //    System.err.println("here stop dimensions");
                //}
                resizeLogger.log(Level.FINER, "componentResized {0,number,#}x{1,number,#}", 
                        new Object[]{AutoplotUI.this.getWidth(), AutoplotUI.this.getHeight()});
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                resizeLogger.finest("componentMoved");
            }

            @Override
            public void componentShown(ComponentEvent e) {
                resizeLogger.finer("componentShown");
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                resizeLogger.finer("componentHidden");
            }
            
        });
        
        applicationModel.addPropertyChangeListener(ApplicationModel.PROP_VAPFILE, (PropertyChangeEvent e) -> {
            stateSupport.setCurrentFile( (String)e.getNewValue() );
        });

        applicationModel.addPropertyChangeListener((PropertyChangeEvent evt) -> {
            switch (evt.getPropertyName()) {
                case ApplicationModel.PROPERTY_RECENT:
                    final List<Bookmark> recent = applicationModel.getRecent();
                    SwingUtilities.invokeLater(() -> {
                        org.autoplot.bookmarks.Util.setRecent( dataSetSelector, recent );
                        //dataSetSelector.setRecent(urls);
            });
                    break;
                case ApplicationModel.PROPERTY_BOOKMARKS:
                    SwingUtilities.invokeLater(() -> {
                        updateBookmarks();
            });
                    break;
                default:
                    logger.log(Level.FINER, "no action needed near line 940: {0}", evt.getPropertyName());
            }
        });
        
        autoLayout = new LayoutListener(model);
        APSplash.checkTime("init 55");
        APSplash.checkTime("init 60");

        dataSetSelector.addPropertyChangeListener(DataSetSelector.PROPERTY_MESSAGE, (PropertyChangeEvent e) -> {
            setStatus(dataSetSelector.getMessage());
        });

        tabs = new TearoffTabbedPane();

        applicationModel.getCanvas().setFitted(true);
        JScrollPane scrollPane = new JScrollPane(applicationModel.getCanvas());
        scrollPane.getViewport().setBackground( new JLabel().getBackground() );
        tabs.insertTab("canvas", null, scrollPane, 
                String.format(  TAB_TOOLTIP_CANVAS, TABS_TOOLTIP), 0);
        tabs.validate();
        
        tabbedPanelContainer.add(tabs, BorderLayout.CENTER);

        tabs.requestFocus();
        APSplash.checkTime("init 70");
        
        final ApplicationModel fmodel= model;

        /// These were an attempt to improve startup time:
        //SwingUtilities.invokeLater(addAxes());
        //SwingUtilities.invokeLater(addStyle());
        //SwingUtilities.invokeLater(addLayout());
        //SwingUtilities.invokeLater( new Runnable() {
        //    public void run() {
        //        addFeatures(fmodel);
        //    }
        //});
        addAxes().run();
        APSplash.checkTime("init 72");
        addStyle().run();
        APSplash.checkTime("init 75");
        addFeatures(fmodel);
        APSplash.checkTime("init 77");
        
        List<String> uris = new ArrayList();
        List<Bookmark> recent = applicationModel.getRecent();
        APSplash.checkTime("init 80");

        recent.forEach((b) -> {
            uris.add(((Bookmark.Item) b).getUri());
        });
        dataSetSelector.setRecent(uris);
        //some other bug had been preventing this code from working.  I actually like the bug behavior better, where the value is
        //not the most recent one, so I'm commenting this out to restore this behavior.
//        if (uris.size() > 1) {
//            if ( dataSetSelector.getEditor().getText().equals("") ) {
//                dataSetSelector.getEditor().setText(uris.get(uris.size() - 1)); // avoid firing event
//            }
//        }

        //since bookmarks can contain remote folder, get these after making the gui.
        Runnable run= () -> {
            updateBookmarks();
            dataSetSelector.setPromptText("Enter data location or select a bookmark");
        };
        invokeLater( 1000, true, run );
        APSplash.checkTime("init 90");

        SwingUtilities.invokeLater(() -> {
            addTools();
            PropertyEditor.addStringEditor("tickValues", new TickValuesStringSchemeEditor() );
            PropertyEditor.addStringEditor("specialColors", new SpecialColorsStringSchemeEditor() );
            PropertyEditor.addStringEditor("autoRangeHints", new AutoRangeHintsStringSchemeEditor() );
            PropertyEditor.addStringEditor("label", newGrannyTextEditorWithMacros() );
            PropertyEditor.addStringEditor("title", newGrannyTextEditorWithMacros() );
            PropertyEditor.addStringEditor("org.autoplot.dom.Annotation","text", newGrannyTextEditorWithMacros() ); 
            PropertyEditor.addStringEditor("legendLabel", GraphUtil.newGrannyTextEditor() ); 
            PropertyEditor.addStringEditor("colorbarColumnPosition", new LayoutStringSchemeEditor(true, "H") );
            PropertyEditor.addStringEditor("top", new LayoutStringSchemeEditor(false, "T") );
            PropertyEditor.addStringEditor("bottom", new LayoutStringSchemeEditor(false, "B") );
            PropertyEditor.addStringEditor("right", new LayoutStringSchemeEditor(false, "R") );
            PropertyEditor.addStringEditor("left", new LayoutStringSchemeEditor(false, "L") );
            PropertyEditor.addStringEditor("font",new FontStringSchemeEditor());
            PropertyEditor.addStringEditor("ticksURI",new DataSetSelectorStringSchemeEditor());
            PropertyEditor.addStringEditor("uri",new DataSetSelectorStringSchemeEditor());
            PropertyEditor.addStringEditor("eventsListUri",new DataSetSelectorStringSchemeEditor());
        });
        
        addBindings();

        pack();

        dom.getOptions().addPropertyChangeListener(optionsListener);

        APSplash.checkTime("init 100");

//        if ( AutoplotUtil.getProperty("os.name","").startsWith("Mac OS") ) {
//            applicationModel.getCanvas().resizeAllComponents();
//            applicationModel.getCanvas().repaint();
//            applicationModel.getCanvas().paintImmediately(0,0,1000,1000);
//        }
        APSplash.checkTime("init 110");
        
        // jython is often slow to start up the first time, so go ahead and do this in the background.
        run= new Runnable() {
            @Override
            public String toString() { return "addInitializePython"; }
            @Override
            public void run() {
                try {
                    //initialize the python interpretter
                    JythonUtil.createInterpreter(true, false);
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
        };
        invokeLater( 10000, false, run );
    }
    
    /**
     * true indicates that certificate checking has been disabled.
     * @see https://sourceforge.net/p/autoplot/bugs/2383/
     */
    public static final String SYSPROP_AUTOPLOT_DISABLE_CERTS = "autoplot.disable.certs";
    
    /**
     * the release type, either non for unknown, or javaws, singlejar, exe, or dmg.
     * This should be set at the command line when java is started.
     * @see https://sourceforge.net/p/autoplot/bugs/2383/
     */
    public static final String SYSPROP_AUTOPLOT_RELEASE_TYPE = "autoplot.release.type";
            
    private Runnable addAxes() {
        return () -> {
            APSplash.checkTime("addAxes in");
            final JScrollPane sp= new JScrollPane();
            tabs.insertTab("axes", null, sp,
                    String.format(  TAB_TOOLTIP_AXES, TABS_TOOLTIP), 1);
            invokeLater( 2500, true, new Runnable() {
                @Override
                public String toString() { return "addAxesRunnable"; }
                @Override
                public void run() {
                    APSplash.checkTime("addAxes1 in");
                    final JComponent c= new AxisPanel(applicationModel);
                    SwingUtilities.invokeLater( new Runnable() {
                        @Override
                        public void run( ) { sp.setViewportView(c); }
                    } );
                    APSplash.checkTime("addAxes1 out");
                }
            });
            APSplash.checkTime("addAxes out");
        };
    }

    private Runnable addStyle() {
        return () -> {
            APSplash.checkTime("addStyle in");
            final JScrollPane sp= new JScrollPane();
            try {
                loadMyColors();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
            tabs.insertTab("style", null, sp,
                    String.format(  TAB_TOOLTIP_STYLE, TABS_TOOLTIP), 2);
            invokeLater( 2500, true, new Runnable() {
                @Override
                public String toString() { return "addStyle"; }
                @Override
                public void run() {
                    APSplash.checkTime("addStyle1 in");
                    final JComponent c= new PlotStylePanel(applicationModel);
                    SwingUtilities.invokeLater(() -> {
                        sp.setViewportView(c);
                    });
                    
                    APSplash.checkTime("addStyle1 out");
                }
            } );
            APSplash.checkTime("addStyle out");
        };
    }

//    /**
//     * this method is disabled from WebStart version, since it doesn't work with
//     * the security model.
//     * @throws HeadlessException
//     */
//    private void addDataSource() throws HeadlessException {
//        AddDataSourcePanel add = new AddDataSourcePanel();
//        int r = JOptionPane.showConfirmDialog(this, add, "Add Data Source", JOptionPane.OK_CANCEL_OPTION);
//        if (r == JOptionPane.OK_OPTION) {
//            String jar = add.getDataSetSelector().getValue();
//            if (jar.endsWith("jar")) {
//                try {
//                    DataSourceRegistry.getInstance().registerDataSourceJar(null, new URL(jar));
//                } catch (IOException ex) {
//                    logger.log(Level.SEVERE, ex.getMessage(), ex);
//                }
//            }
//        }
//    }


    /**
     * load the colors from the colors.txt file.  This expects any of the following
     * forms:
     * <pre>
     * 255 255 255 white
     * 100% 100% 100% white
     * 99% 99% 99% "Almost White"
     * 0xFFFFFF white
     * 0xFFFFFF
     * </pre>
     * @throws IOException 
     */
    private static void loadMyColors() throws IOException {
        File f= new File( new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ) ), "config" );
        if ( f.exists() ) {
            f= new File( f, "colors.txt" );
            if ( f.exists() && f.canRead() ) {
                try (  BufferedReader reader= new BufferedReader( new FileReader(f) ) ) {
                     String line= reader.readLine();
                     while ( line!=null ) {
                         try {
                             int i= line.indexOf('#');
                             if ( i>-1 ) line= line.substring(0,i);
                             String[] ss= line.trim().split("\\s+");
                             if ( ss.length==1 && ss[0].length()==0 ) {
                                 // ignore blank line.
                             } else if ( ss.length==1 ) {
                                 ColorEditor.addColor( Color.decode(ss[0]), ss[0] );  //0xFFFFFF
                             } else if ( ss.length==2 ) {
                                 ColorEditor.addColor( Color.decode(ss[0]), ss[1] );  //0xFFFFFF white
                             } else if ( ss.length>=4 ) {
                                 for ( int j=4; j<ss.length; j++ ) {
                                     ss[3]+= " " + ss[j];
                                 }
                                 if ( ss[3].startsWith("\"") && ss[3].endsWith("\"") ) {
                                     ss[3]= ss[3].substring(1,ss[3].length()-1);
                                 }
                                 if ( ss[0].endsWith("%") ) {  // 100% 100% 100% white 
                                     int rr= 255 * Integer.parseInt(ss[0].substring(0,ss[0].length()-1)) / 100;
                                     int gg= 255 * Integer.parseInt(ss[1].substring(0,ss[1].length()-1)) / 100;
                                     int bb= 255 * Integer.parseInt(ss[2].substring(0,ss[2].length()-1)) / 100;
                                     ColorEditor.addColor( new Color( rr, gg, bb ), ss[3] );
                                 } else {
                                     ColorEditor.addColor( new Color( // 255 255 255 white
                                         Integer.parseInt(ss[0]), 
                                         Integer.parseInt(ss[1]),
                                         Integer.parseInt(ss[2])), ss[3] );
                                 }
                             }
                         } catch ( NumberFormatException ex ) {
                             logger.log(Level.WARNING, "unable to parse color: {0}", line);
                         }
                         line= reader.readLine();
                     }
                }
            } else {
                try ( BufferedWriter write= new BufferedWriter( new FileWriter(f) ) ) {
                    write.append("# red green blue colorName\n");
                    write.append("# 255 255 255 white\n");
                    write.append("# 100% 100% 100% white\n");
                    write.append("# 0x8B0000 DarkRed\n");
                    write.close();
                }
            }
        }
    }

    /**
     * often one message causes another, so we can subsume these
     * @param messages
     * @return
     */
    private static List<String> cleanMessages( List<String> messages ) {
        messages= new ArrayList(messages); // make local copy to avoid concurrent modifications
        List<String> result= new LinkedList(messages);
        for ( String s: messages ) {
            if ( s==null ) {
                System.err.println("here null in cleanMessages");
            } else if ( s.equals("Delete Plot" ) ) {
                result.remove("Delete Plot Element");
            }
        }
        return result;
    }

    /**
     * return a place holder so that each tab's minimum size is set in case
     * one is torn off early.
     * 
     * @param label which panel is not initialized.
     * @return JPanel with minimum size
     */
    private JPanel getFeaturePanelPlaceHolder( String label ) {
        JPanel p= new JPanel( new BorderLayout() );
        p.add( new JLabel( String.format( "<html><i>%s not initialized</i></html>", label ) ), BorderLayout.NORTH );
        p.setMinimumSize( new Dimension(640,480) );
        p.setPreferredSize( p.getMinimumSize() );
        return p;
    }
    
    private void addFeatures( final ApplicationModel model ) {

        final JScrollPane flayoutPane;
        if (model.getDocumentModel().getOptions().isLayoutVisible() ) {
            flayoutPane= new JScrollPane();
            flayoutPane.setViewportView( getFeaturePanelPlaceHolder("layout") );
            tabs.insertTab("layout",null, flayoutPane,
                    String.format( TAB_TOOLTIP_LAYOUT, TABS_TOOLTIP), tabs.getTabCount() );
        } else {
            flayoutPane= null;
        }
        layoutPanel1= flayoutPane;

        final JScrollPane fdataPane;
        if (model.getDocumentModel().getOptions().isDataVisible()) {
            fdataPane= new JScrollPane();
            fdataPane.setViewportView( getFeaturePanelPlaceHolder("data") );
            tabs.insertTab("data", null, fdataPane,
                    String.format(  TAB_TOOLTIP_DATA, TABS_TOOLTIP), tabs.getTabCount() );
        } else {
            fdataPane= null;
        }

        final JScrollPane fmetadataPane= new JScrollPane();
        fmetadataPane.setViewportView( getFeaturePanelPlaceHolder("metadata") );
        tabs.insertTab("metadata", null, fmetadataPane,
                String.format(  TAB_TOOLTIP_METADATA, TABS_TOOLTIP), tabs.getTabCount() );

        invokeLater( 2230, true, new Runnable() { 
            @Override
            public String toString() { return "addLayout"; }
            @Override            
            public void run() {
                //long t0= System.currentTimeMillis();
APSplash.checkTime("init 249");
                if ( flayoutPane!=null ) {
                    final LayoutPanel lui= new LayoutPanel();
                    layoutPanel= lui;
                    SwingUtilities.invokeLater( new Runnable() { 
                        @Override
                        public void run() {
                            flayoutPane.setViewportView(lui);
                        } 
                    } );
                    lui.setApplication(dom);
                    lui.setApplicationModel(applicationModel);
APSplash.checkTime("init 250");
                }
            }
        } );
        invokeLater( 2350, true, new Runnable() {
            @Override
            public String toString() { return "addDataPanel"; }
            @Override
            public void run() {
                //System.err.println("  invokeLater set, layout panel "+(System.currentTimeMillis()-t0));
APSplash.checkTime("init 259");
                if ( fdataPane!=null ) {
                    final DataPanel dp= new DataPanel(AutoplotUI.this);
                    dataPanel= dp;
                    SwingUtilities.invokeLater( new Runnable() { 
                        @Override
                        public void run() {
                            fdataPane.setViewportView(dp);
                        } 
                    } );
APSplash.checkTime("init 260");
                }
            }
        } );
        invokeLater( 2470, true, new Runnable() { 
            @Override
            public String toString() { return "addMetadataPanel"; }
            @Override
            public void run() {
APSplash.checkTime("init 269");
                final MetadataPanel mdp = new MetadataPanel(applicationModel);
                SwingUtilities.invokeLater( new Runnable() { 
                    @Override
                    public void run() {
                        fmetadataPane.setViewportView(mdp);
                    }
                } );
APSplash.checkTime("init 270");
            }
        });

        if (model.getDocumentModel().getOptions().isScriptVisible()) {
            final DataSetSelector fdataSetSelector= this.dataSetSelector; // org.pushngpixels.tracing.TracingEventQueueJMX showed this was a problem.
            jythonScriptPanel= new JPanel( new BorderLayout() );
            jythonScriptPanel.setMinimumSize(new Dimension(640,480));
            jythonScriptPanel.setPreferredSize(new Dimension(640,480));
            tabs.addTab( TAB_SCRIPT, null, jythonScriptPanel,
                  String.format(  TAB_TOOLTIP_SCRIPT, TABS_TOOLTIP )  );
            invokeLater( 4000, true, new Runnable() {
                @Override
                public String toString() { return "addScriptPanel"; }
                @Override
                public void run() {
                    scriptPanel= new JythonScriptPanel( AutoplotUI.this, fdataSetSelector);
                    addEditorCustomActions(scriptPanel);
                    jythonScriptPanel.add(scriptPanel,BorderLayout.CENTER);
                    scriptPanelMenuItem.setSelected(true);
                    ExceptionHandler h= model.getExceptionHandler();
                    if ( h!=null && h instanceof GuiExceptionHandler ) {
                        ((GuiExceptionHandler)h).setScriptPanel(scriptPanel);
                    }
                }
            } );
        }
        if (model.getDocumentModel().getOptions().isLogConsoleVisible()) {
            logConsolePanel= new JScrollPane();
            logConsolePanel.setViewportView( getFeaturePanelPlaceHolder(TAB_CONSOLE) );

            tabs.addTab( TAB_CONSOLE, null, logConsolePanel,
                String.format(  TAB_TOOLTIP_LOGCONSOLE, TABS_TOOLTIP) );
            invokeLater( 4020, true, new Runnable() {
                @Override
                public String toString() { return "addLogConsole"; }
                @Override
                public void run() {
                    initLogConsole();
//                    logConsole.addPropertyChangeListener( LogConsole.PROP_LOGSTATUS, new PropertyChangeListener() {
//                        @Override
//                        public void propertyChange(PropertyChangeEvent evt) {
//                            if ( evt.getNewValue().equals( Level.WARNING ) ) {
//                                lbl.setBackground( Color.RED );
//                                lbl.setIcon(WARNING_ICON);
//                            } else if ( evt.getNewValue().equals( Level.INFO ) ) {
//                                lbl.setBackground( Color.DARK_GRAY );
//                                lbl.setIcon(BUSY_ICON);
//                            } else {
//                                lbl.setBackground( lblBackground0 );
//                                lbl.setIcon(null);
//                            }
//                        }
//                    });
                    logConsolePanel.setViewportView( logConsole );
                }
            }  );
        }


        tickleTimer = new TickleTimer(300, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {

                if ( dom.getController().isValueAdjusting() ) { // don't listen to property changes during state transitions.
                    tickleTimer.tickle("app value was adjusting");
                    return;
                }
                
                Map<Object,Object> changes= new LinkedHashMap();
                dom.getController().pendingChanges(changes);
                if ( changes.size()>0 ) {
                    tickleTimer.tickle("app had pending changes");
                    return;
                }

                List<String> messages= tickleTimer.getMessages();

                //for ( String s: messages ) {
                //    System.err.println("messages: "+s);
                //}
                
                if ( messages.size()>1 ) {
                    messages= cleanMessages(messages);
                    if ( messages.size()==1 ) {
                        undoRedoSupport.pushState(evt,messages.get(0)); // named undo operation
                    } else {
                        undoRedoSupport.pushState(evt,String.format("%d changes",messages.size())); 
                    }
                } else if ( messages.size()==1 ) {
                    if ( messages.get(0).contains(" from ") ) {
                        undoRedoSupport.pushState(evt);
                    } else {
                        undoRedoSupport.pushState(evt,messages.get(0)); // named undo operation
                    }
                } else {
                    //I've hit this state before when loading empty vap file: file:///home/jbf/ct/autoplot/script/demos/interpolateToCommonTags2.vap
                    undoRedoSupport.pushState(evt,"????");
                    logger.fine("tickleTimer contained no messages.");
                }

                stateSupport.markDirty();
                
                if ( pendingVap!=null ) {  // bug https://sourceforge.net/p/autoplot/bugs/1408/
                    model.addRecent( pendingVap );
                    dataSetSelector.setValue( pendingVap );
                    pendingVap= null;
                    
                }

                SwingUtilities.invokeLater(new Runnable() {
                    @Override            
                    public void run() {
                        refreshUndoRedoLabel();
                    }
                });
            }
        });

        applicationModel.dom.getController().addActionListener( new ActionListener() {
            @Override
            public void actionPerformed( ActionEvent evt ) {
                // No need to log, this is not human-generated.
                if ( dom.getController().isValueAdjusting() ) return;
                logger.log( Level.FINER, "state change: {0}", evt);
                if (!stateSupport.isOpening() && !stateSupport.isSaving() && !applicationModel.isRestoringState()) { // TODO: list the props we want!
                    if ( evt.getActionCommand().startsWith("label: ") ) {
                        tickleTimer.tickle( evt.getActionCommand().substring("label: ".length()) );
                    } else {
                        tickleTimer.tickle( evt.getActionCommand() + " from " + evt.getSource() );
                    }
                }
            }
        } );
        
        applicationModel.dom.getController().addPropertyChangeListener( ApplicationController.PROP_PLOT_ELEMENT, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ( evt.getNewValue()==null ) {
                    String current= AutoplotUI.this.stateSupport.getCurrentFile();
                    if ( current!=null ) {
                        AutoplotUI.this.dataSetSelector.setValue(current);
                    }
                }
            }
        });
        
/*        applicationModel.dom.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ( dom.getController().isValueAdjusting() ) return;
                logger.finer( "state change: "+evt.getPropertyName() );
                if (!stateSupport.isOpening() && !stateSupport.isSaving() && !applicationModel.isRestoringState()) { // TODO: list the props we want!
                    tickleTimer.tickle(evt.getPropertyName() + " from " + evt.getSource());
                }
            }
        }); */

        //TODO: perhaps keep a track of dirty URI in dataset selector
        applicationModel.dom.getController().addPropertyChangeListener( ApplicationController.PROP_DATASOURCEFILTER,
                new PropertyChangeListener() {
            @Override
            public void propertyChange( PropertyChangeEvent evt ) {
                DataSourceFilter dsf= (DataSourceFilter) evt.getNewValue();

                if ( dsf==null ) {
                    dataSetSelector.setValue("");
                } else {
                    String uri= dsf.getUri();
                    if ( uri!=null ) {
                        if ( pendingVap==null && !isBasicMode() ) {
                            dataSetSelector.setValue(uri);
                        }
                    } else {
                        dataSetSelector.setValue("");
                    }
                }
            }
        });

        System.err.println("ready in just a few seconds...");
        setMessage("ready in just a few seconds...");
        
    }

    protected void refreshUndoRedoLabel() {
        assert EventQueue.isDispatchThread();

        String t = undoRedoSupport.getUndoLabel();

        undoMenuItem.setEnabled(t != null);
        undoMenuItem.setText(t == null ? "Undo" : t);
        undoMenuItem.setToolTipText( t==null ? "" : undoRedoSupport.getUndoDescription() );
        
        String tt = undoRedoSupport.getRedoLabel();

        redoMenuItem.setEnabled(tt != null);
        redoMenuItem.setText(tt == null ? "Redo" : tt);
        redoMenuItem.setToolTipText( tt==null ? "" : undoRedoSupport.getRedoDescription() );

        undoRedoSupport.refreshUndoMultipleMenu(undoMultipleMenu);
        
    }

    
    /** 
     * provide access to the tabs so that component can be added
     * @return TabbedPane.
     */
    public TearoffTabbedPane getTabs() {
        return this.tabs;
    }

    private void bind( BindingGroup bc, Object src, String srcProperty, Object dst, String dstProperty ) {
        BeanProperty srcbp= BeanProperty.create(srcProperty);
        if ( !srcbp.isReadable(src) ) {
            System.err.println("not readable: "+ srcProperty + " of "+ src );
        }
        BeanProperty dstbp= BeanProperty.create(dstProperty);
        if ( !dstbp.isReadable(dst) ) {
            System.err.println("not readable: "+ dstProperty + " of "+ dst );
        }
        bc.addBinding( Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, src, srcbp, dst, dstbp ));
    }

    private void addBindings() {

        Runnable run= new Runnable() {
            @Override
            public String toString() { return "bindings"; }
            @Override
            public void run() {
                logger.fine("adding bindings");
                BindingGroup bc = new BindingGroup();
                bind( bc, dom.getOptions(), Options.PROP_DRAWGRID, drawGridCheckBox, "selected" );
                bind( bc, dom.getOptions(), Options.PROP_AUTOLABELLING, autoLabellingCheckBoxMenuItem, "selected" );
                bind( bc, dom.getOptions(), Options.PROP_AUTOLAYOUT, autoLayoutCheckBoxMenuItem, "selected" );
                bind( bc, dom.getOptions(), Options.PROP_AUTORANGING, autoRangingCheckBoxMenuItem, "selected" );
                bind( bc, dom.getOptions(), Options.PROP_DATAVISIBLE, dataPanelCheckBoxMenuItem, "selected" );
                bind( bc, dom.getOptions(), Options.PROP_LAYOUTVISIBLE, layoutPanelCheckBoxMenuItem, "selected" );
                bind( bc, dom.getOptions(), Options.PROP_DAY_OF_YEAR, doyCB, "selected" );
                bind( bc, dom.getOptions(), Options.PROP_NEARESTNEIGHBOR, nnCb, "selected" );
                bind( bc, dom.getOptions(), Options.PROP_USE_TIME_RANGE_EDITOR, timeRangeSelectorMenuItem, "selected" );
                // be more precise about this now.  bind( bc, dom, Application.PROP_TIMERANGE, dataSetSelector, DataSetSelector.PROP_TIMERANGE );
                bind( bc, dom, Application.PROP_TIMERANGE, timeRangeEditor, "range" );
                bind( bc, dom.getOptions(), Options.PROP_DAY_OF_YEAR, timeRangeEditor, "useDoy" );
                bind( bc, dom.getOptions(), Options.PROP_PRINTINGTAG, dom.getCanvases(0).getController().getDasCanvas(), "printingTag" ); 
                bc.bind();

                dom.addPropertyChangeListener( Application.PROP_BINDINGS, new PropertyChangeListener() {
                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        BindingModel[] bms= dom.getBindings();
                        boolean isBound=false;
                        for (BindingModel bm : bms) {
                            if (bm.getSrcProperty().equals("timeRange")) {
                                isBound= true;
                            }
                        }
                        if ( isBound && !dsSelectTimerangeBound ) { // dataSetSelector has a timerange so that entering the datasource GUI will use the current timerange.
                            dom.getController().bind( dom, Application.PROP_TIMERANGE, dataSetSelector, DataSetSelector.PROP_TIMERANGE );
                            dsSelectTimerangeBound= true;
                        } else if ( !isBound && dsSelectTimerangeBound ) {
                            dom.getController().unbind( dom, Application.PROP_TIMERANGE, dataSetSelector, DataSetSelector.PROP_TIMERANGE );
                            dataSetSelector.setTimeRange(null);
                            dsSelectTimerangeBound= false;
                        }
                    }
                });
                
                dom.addPropertyChangeListener( Application.PROP_EVENTSLISTURI, new PropertyChangeListener() {
                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        logger.finer("events list URI property change");
                        final String uri = (String)evt.getNewValue();
                        if ( uri.trim().length()>0 ){
                            Runnable run= new Runnable() {
                                @Override
                                public void run() {
                                    logger.log(Level.FINEST, "resetting events list URI to {0}", uri);
                                    EventsListToolUtil.setEventsListURI( AutoplotUI.this, uri );
                                }
                            };
                            if ( EventQueue.isDispatchThread() ) {
                                run.run();
                            } else {
                                SwingUtilities.invokeLater(run);
                                System.err.println("resetting events list URI later");
                            }
                        }
                    }
                    
                });

            }
        };
        invokeLater(-1,false,run);

        this.dataSetSelector.addPropertyChangeListener("value", new PropertyChangeListener() { //one-way binding
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                    applicationModel.setDataSourceURL(dataSetSelector.getValue());
            }
        });
    }

    /**
     * Attempt to plot the given URI, which may be rejected and its
     * editor invoked.  This is the equivalent of typing in the URI in the 
     * address bar and pressing the Go (green arrow) button.
     * @param uri the Autoplot URI.
     */
    public void plotUri( String uri ) {
        dataSetSelector.setValue(uri);
        dataSetSelector.maybePlot(false);        
    }
    

    private Action getAddPlotElementAction() {
        return new AbstractAction("Add Plot...") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                support.addPlotElement("Add Plot");
            }
        };
    }

    /**
     * make this public before AGU.  Set the editor to the URI, then call this.
     */
    public void enterAddPlotElementDialog() {
        support.addPlotElement("Add Bookmarked Plot");
    }
        
    JMenu addDataFromMenu= null;

    private synchronized JMenu getAddDataFromMenu( ) {
        JMenu result= new JMenu( "Add Plot From" );
        result.add( new JMenuItem("looking up discoverable sources...") );
        addDataFromMenu= result;
        RequestProcessor.invokeLater( new Runnable() {
            @Override
            public void run() {
                fillAddDataFromMenu();
            }
        });
        return result;
    }

    
    /**
     * add the URI to the discovery use file bookmarks/discovery.txt
     * @param uri 
     */
    private void addToDiscoveryUseSoon( final String uri ) {
        Runnable run= new Runnable() {
            @Override
            public void run() {
                // always tack on the URI to history.dat file
                File f2= new File( AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA), "bookmarks/" );    
                final File f3 = new File( f2, "discovery.txt");
                FileWriter out3=null;
                try {
                    out3 = new FileWriter( f3, true );                
                    TimeParser tp= TimeParser.create( TimeParser.TIMEFORMAT_Z );
                    Datum now= Units.t1970.createDatum( System.currentTimeMillis()/1000. );
                    out3.append( tp.format( now, null) + "\t" + uri + "\n" );
                } catch ( IOException ex ) {
                    logger.log(Level.SEVERE,ex.getMessage(),ex);
                } finally {
                    if ( out3!=null ) try {
                        out3.close();
                    } catch (IOException ex1) {
                        logger.log(Level.SEVERE, ex1.getMessage(), ex1);
                    }
                }
            }
        };
        new Thread(run).start();
    }
    
    private void fillAddDataFromMenuImmediately(final List<String> exts) {
        addDataFromMenu.removeAll();
        for ( String ext: exts ) {
            if ( ext.equals("file:") ) {
                Action a= new AbstractAction( "Local File...") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        addToDiscoveryUseSoon("file:");
                        dataSetSelector.getOpenLocalAction().actionPerformed(e);
                    }
                };
                addDataFromMenu.add( new JMenuItem( a ) );
            } else {
                if ( ext.startsWith(".") ) ext= ext.substring(1);
                final String fext= ext;
                Action a= new AbstractAction( ext+"..." ) {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        org.das2.util.LoggerManager.logGuiEvent(e);                                
                        try {
                            String uri = "vap+" + fext + ":";
                            String refuri= (String) dataSetSelector.getValue();
                            if ( refuri.toLowerCase().startsWith(uri) ) {
                                addToDiscoveryUseSoon(refuri);
                                dataSetSelector.browseSourceType();
                            } else {
                                addToDiscoveryUseSoon(uri);
                                dataSetSelector.setValue(uri);
                                dataSetSelector.maybePlot( true );
                            }
                        } catch (Exception ex) {
                            logger.log(Level.SEVERE, ex.getMessage(), ex);
                        }
                    }
                };                
                JMenuItem item= new JMenuItem( a );
                addDataFromMenu.add(item);
            }
        }
    }
    
    /**
     * look up the discoverable extensions--the sources that can be added with
     * just "vap+<ext>:" because we can enter a GUI right away. 
     */
    private void fillAddDataFromMenu() {
        final List<String> exts= DataSetURI.getDiscoverableExtensions();
        exts.add("file:"); // special marker for local files.
        File f= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ) + "/bookmarks/discovery.txt" );
        if ( f.exists()&& f.canRead()) {
            BufferedReader reader=null;
            try {
                reader= new BufferedReader( new FileReader(f) );
                String s= reader.readLine();
                while ( s!=null ) {
                    if ( s.length()>29 ) {
                        String ss= s.substring(25,29);
                        switch (ss) {
                            case "file":
                                {
                                    String ex1= "file:";
                                    if ( exts.contains(ex1) ) {
                                        exts.remove(ex1);
                                        exts.add(0,ex1);
                                    }       break;
                                }
                            case "vap+":
                                {
                                    int i= s.indexOf(':',29);
                                    String ex1= "."+s.substring(29,i);
                                    if ( exts.contains(ex1) ) {
                                        exts.remove(ex1);
                                        exts.add(0,ex1);
                                    }       break;
                                }
                        }
                    }
                    s= reader.readLine();
                }
            } catch ( IOException ex ) {
            } finally {
                if ( reader!=null ) {
                    try {
                        reader.close();
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                }
            }
        }
        
        Runnable run= new Runnable() {
            @Override
            public void run() {
                fillAddDataFromMenuImmediately(exts);
            }
        };
        SwingUtilities.invokeLater(run);
    }

    private void fillInitialBookmarksMenu() {

        JMenuItem mi;
        mi= new JMenuItem( "Manage and Browse..." );
        mi.setEnabled(false);
        bookmarksMenu.add( mi ); // take up some space in case it is reset while it's open.

        mi= new JMenuItem( "Add bookmark..." );
        mi.setEnabled(false);
        bookmarksMenu.add( mi );

        bookmarksMenu.add( new JSeparator() );
        
        mi= new JMenuItem("Loading..." );
        mi.setToolTipText("Loading initial bookmarks file...");
        bookmarksMenu.add(mi);

        for ( int i=0; i<5; i++ ) {
            mi= new JMenuItem(" ");
            bookmarksMenu.add(mi);
        }

    }

    private void fillFileMenu() {
        List<JComponent> expertItems= new ArrayList();
        javax.swing.JMenuItem mi;
        
        mi= new JMenuItem(support.createNewApplicationAction());
        mi.setToolTipText("Create another window");
        fileMenu.add( mi );
        
        mi= new JMenuItem(support.createCloneApplicationAction());
        mi.setToolTipText("Duplicate to another new window");
        expertItems.add( mi );        
        fileMenu.add( mi );

        mi= new JMenuItem( support.createNewDOMAction() );
        mi.setToolTipText("Reset application to initial state");
        fileMenu.add(mi);

        fileMenu.add( new JSeparator() );
APSplash.checkTime("init 51");
        fileMenu.add( getAddDataFromMenu() );
APSplash.checkTime("init 52");
        mi= new JMenuItem(getAddPlotElementAction() );
        mi.setToolTipText("Add a new plot or overplot to the application");
        expertItems.add(mi);
        fileMenu.add(mi);
APSplash.checkTime("init 52.3");
        mi= new JMenuItem( new AbstractAction( "Open URI History..." ) {
              @Override
              public void actionPerformed( ActionEvent e ) {
                  org.das2.util.LoggerManager.logGuiEvent(e);                                
                  RecentUrisDialog dia= new RecentUrisDialog( (java.awt.Frame)SwingUtilities.getWindowAncestor(fileMenu), true );
                  dia.setExpertMode( isExpertMode() );
                  
                  WindowManager.getInstance().showModalDialog(dia);
                  
                  if (dia.isCancelled()) {
                    return;
                  }
                  String suri= dia.getSelectedURI();
                  if ( suri!=null ) {
                      dataSetSelector.setValue(suri);
                      dataSetSelector.maybePlot( dia.getModifiers());
                  }
              }
        } );
        mi.setToolTipText("Open URI history dialog");
APSplash.checkTime("init 52.4");
mi.setIcon( new ImageIcon( getClass().getResource("/resources/history.png") ) );
APSplash.checkTime("init 52.5");
        fileMenu.add( mi );

APSplash.checkTime("init 52.7");
        fileMenu.add( new JSeparator() );

        mi= new JMenuItem(dataSetSelector.getOpenLocalVapAction() );
        mi.setToolTipText("Open local .vap application state file");
        fileMenu.add(mi);

        mi= new JMenuItem(stateSupport.createSaveAsAction() );
        mi.setToolTipText("Save the application state to a file");
        fileMenu.add(mi);

        mi= new JMenuItem(stateSupport.createSaveAction());
        mi.setToolTipText("Save the application state to a file");
        mi.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_S, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() ));
        fileMenu.add(mi);

        fileMenu.addSeparator();
APSplash.checkTime("init 52.8");
        
        JMenu printToMenu = new JMenu("Print to");
        printToMenu.setToolTipText("Print to file");
        fileMenu.add(printToMenu);
        
        Component focus= AutoplotUI.this;
        JMenuItem item = new JMenuItem( GuiSupport.getPrintAction(dom, focus, "pdf" ) );
        item.setText("PDF...");
        printToMenu.add(item);

        item = new JMenuItem( GuiSupport.getPrintAction(dom, focus, "svg" ) );
        item.setText("SVG...");
        printToMenu.add(item);

        item = new JMenuItem( GuiSupport.getPrintAction(dom, focus, "png" ) );
        item.setText("PNG...");
        printToMenu.add(item);

        AbstractAction printAction= new AbstractAction( "Printer...") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                                
                applicationModel.getCanvas().makeCurrent();
                DasCanvas.PRINT_ACTION.actionPerformed(e);
            }
        };
        mi= new JMenuItem( printAction );
        mi.setToolTipText("Print to printer");
        printToMenu.add( mi );
        
        fileMenu.addSeparator();

        //mi= new JMenuItem( support.getDumpDataAction() );
        //mi.setToolTipText("Export the data that has the focus");
        //fileMenu.add( mi );

        item = new JMenuItem( support.getDumpDataAction( dom ) );
        item.setToolTipText("Export the data that has the focus");
        expertItems.add(item);
        fileMenu.add( item );

        item = new JMenuItem( support.getDumpAllDataAction( dom ) );
        item.setToolTipText("Export the all data on the canvas");
        expertItems.add(item);
        fileMenu.add( item );
APSplash.checkTime("init 52.9");
        JSeparator dumpSep= new JSeparator();
        expertItems.add( dumpSep );

        //fileMenu.add( new )
        //fileMenu.add( GuiSupport.getExportDataAction(AutoplotUI.this) );


        fileMenu.add( dumpSep );
        //fileMenu.addSeparator();


        fileMenu.add( new AbstractAction( "Close" ) {
            @Override
            public void actionPerformed( ActionEvent ev ) {
                org.das2.util.LoggerManager.logGuiEvent(ev);
                boolean isQuit= false;
                if ( AppManager.getInstance().getApplicationCount()==1 ) {
                    int opt= JOptionPane.showConfirmDialog( AutoplotUI.this,
                            "Quit application?", "Quit Autoplot", JOptionPane.YES_NO_CANCEL_OPTION );
                    switch (opt) {
                        case JOptionPane.YES_OPTION:
                            //normal route
                            if ( AppManager.getInstance().requestQuit() ) {
                                isQuit= true;
                            } else {
                                return;
                            }   break;
                        case JOptionPane.NO_OPTION:
                            AutoplotUI.this.dom.getController().reset();
                            return;
                        default:
                            return;
                    }
                }
                
                if ( !isQuit ) {
                    if ( AppManager.getInstance().closeApplication(AutoplotUI.this) ) {
                        AutoplotUI.this.dispose();
                        ScriptContext.close();
                    }
                } else {
                    ScriptContext.close();
                }
            }
        });

        fileMenu.add( new AbstractAction( "Quit" ) {
            @Override
            public void actionPerformed( ActionEvent ev ) {
                org.das2.util.LoggerManager.logGuiEvent(ev);
                if ( AppManager.getInstance().requestQuit() ) {
                    AutoplotUI.this.dispose();
                    if ( logConsole!=null ) logConsole.undoLogConsoleMessages();
                    AppManager.getInstance().quit();
                }
            }
        });

        expertMenuItems.addAll( expertItems );
    }

    /**
     * Reset one of the actions in the File menu.  There don't appear to be any uses of 
     * this odd function, so it might have been used with a script. 
     * @param name the name of the action.
     * @param a the new action.
     */
    public void resetAction( String name, Action a ) {
        for ( int i=0; i<fileMenu.getItemCount(); i++ ) {
            JMenuItem item= fileMenu.getItem(i);
            if ( item!=null && item.getText().equals(name) ) {
                fileMenu.getItem(i).setAction(a);
            }
        }
    }


    private JPanel initLogConsole() throws SecurityException {
        logConsole = new LogConsole();
        logConsole.setScriptContext( Collections.singletonMap( "dom", (Object)applicationModel.dom ) ); // must cast or javac complains
        if ( scriptPanel!=null ) {
            logConsole.addConsoleListener( scriptPanel.getConsoleListener() );
        }
        logConsole.turnOffConsoleHandlers();
        logConsole.logConsoleMessages(); // stderr, stdout logged to Logger "console"

        // This is that place that sets up loggers.  Why do we do this???  It prevents -Djava.util.logging.config.file from working.
        Handler h = logConsole.getHandler();

        Logger.getLogger("").addHandler(h);
        
        setMessage("log console added");
       // tabs.addTab("console", null, logConsole,
       //         String.format(  TAB_TOOLTIP_LOGCONSOLE, TABS_TOOLTIP) );
        applicationModel.getDocumentModel().getOptions().setLogConsoleVisible(true);

        if ( applicationModel.getExceptionHandler() instanceof GuiExceptionHandler ) {
            ((GuiExceptionHandler)applicationModel.getExceptionHandler()).setLogConsole(logConsole);
        }
        logConsoleMenuItem.setSelected(true);
        return logConsole;
    }

    private void initServer() {
        String result = JOptionPane.showInputDialog(this, "<html>Select port for server.  This port will <br>"
                + "accept Jython commands to control and <br>receive services from the application", 12345);
        if ( result==null ) return;
        int iport = Integer.parseInt(result);
        setupServer(iport, applicationModel);
    }

    private void stopServer() {
        if ( rlistener!=null ) rlistener.stopListening();
        rlistener= null;
        updateFrameTitle();
    }

    
    private ProgressMonitor getStatusBarProgressMonitor( final String finishMessage ) {
        return new NullProgressMonitor() {
            @Override
            public void setProgressMessage(String message) {
                setStatus(BUSY_ICON,message);
            }
            @Override
            public void finished() {
                setStatus(IDLE_ICON,finishMessage);
            }
        };
    }

    /**
     * this must not be called on the event thread.
     * @param surl 
     */
    private void plotUrlImmediately( String surl ) {
        try {
            logger.log(Level.FINE, "plotUrl({0})", surl);
            URISplit split= URISplit.parse(surl);
            ProgressMonitor mon= getStatusBarProgressMonitor("Finished loading "+surl);
            if ( split.file==null || !( split.file.endsWith(".vap")|| split.file.endsWith(".vapx") ) ) {
                if ( ! "true".equals(AutoplotUtil.getProperty("java.awt.headless", "false")) ) { // findbugs DLS_DEAD_LOCAL_STORE okay
                    try {
                        DataSetURI.getDataSourceFactory(DataSetURI.getURIValid(surl),mon);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    } catch (URISyntaxException ex) {
                        logger.log(Level.SEVERE, ex.getMessage(), ex);
                        String s= "<li>scheme:arguments</li>"
                                + "vap+cdaweb:ds=AC_H3_SWI&id=SW_type<br>"
                                + "<li>scheme:URL?scheme-arguments</li>"
                                + "vap+dat:https://autoplot.org/data/somedata.dat?column=field1<br>"
                                + "<li>URL?scheme-arguments</li>"
                                + "https://autoplot.org/data/somedata.dat?column=field1</br>";

                        JOptionPane.showMessageDialog( this, "<html><p>URI Syntax Error found when parsing.</p>"+s, "URI Syntax Error", JOptionPane.OK_OPTION );
                        return;
                    } catch ( IllegalArgumentException ex ) {
                        SourceTypesBrowser browser= new SourceTypesBrowser();
                        browser.getDataSetSelector().setValue(DataSetURI.fromUri(DataSetURI.getResourceURI(surl)));
                        int r= AutoplotUtil.showConfirmDialog(this, browser,"Select Data Source Type",JOptionPane.OK_CANCEL_OPTION);
                        if ( r==JOptionPane.OK_OPTION ) {
                            surl= browser.getUri();
                            dataSetSelector.getValue(); //TODO: this needs review
                            dataSetSelector.setValue(surl);
                            dataSetSelector.maybePlot(true);
                            return;
                        } else {
                            return;
                        }
                    }
                }
            }
            applicationModel.resetDataSetSourceURL(surl, mon );
            if ( split.file!=null && ( split.file.endsWith(".vap") || split.file.endsWith(".vapx" ) ) ) {
                tickleTimer.tickle(); 
                pendingVap= surl;
            } else {
                applicationModel.addRecent(surl);
            }
        } catch (RuntimeException ex) {
            if ( ex.getCause()!=null && ex.getCause() instanceof HtmlResponseIOException ) {
                handleHtmlResponse( surl, ex );
            } else if ( ex.getCause()!=null && ex.getCause() instanceof IOException ) {
                setStatus(ERROR_ICON,"Unable to open URI: " + surl);
                AutoplotUtil.showUserExceptionDialog( this, 
                        "<html>Unable to open URI: <br>" + surl+"<br><br>"+ex.getCause(),
                        ex, applicationModel.getExceptionHandler() );
            } else {
                applicationModel.getExceptionHandler().handleUncaught(ex);
                String msg= ex.getMessage();
                if ( msg==null ) msg= ex.toString();
                setStatus(ERROR_ICON,msg);
            }
        }
        
    }
    
    /**
     * setEventsListURI the unexpected html response dialog, with a button to look at the HTML response.
     * @param surl the URL 
     * @param ex the exception
     * @return 
     */
    private void handleHtmlResponse( String surl, Exception ex ) {
        setStatus(ERROR_ICON,"HTML response from URI: " + surl);
        HtmlResponseIOException htmlEx;
        if ( ex instanceof HtmlResponseIOException ) {
            htmlEx= (HtmlResponseIOException)ex;
        } else {
            htmlEx= ((HtmlResponseIOException)ex.getCause());
        }
        if ( htmlEx.getURL()!=null ) {
            final String link= htmlEx.getURL().toString();
            JPanel p= new JPanel( new BorderLayout( ) );
            p.add( new JLabel(  "<html>Unable to open URI: <br>" + surl+"<br><br>"+htmlEx.getMessage()+ "<br><br>" ), BorderLayout.CENTER );
            JPanel p1= new JPanel( new BorderLayout() );
            p1.add( new JButton( new AbstractAction("View in Browser") {
                @Override
                public void actionPerformed( ActionEvent ev ) {
                    org.das2.util.LoggerManager.logGuiEvent(ev);                
                    AutoplotUtil.openBrowser(link);
                }
            }), BorderLayout.EAST );
            p.add( p1, BorderLayout.SOUTH );
            JOptionPane.showMessageDialog( this, p, "HTML response", JOptionPane.ERROR_MESSAGE );
        } else {
            JOptionPane.showMessageDialog( this, "<html>Unable to open URI: <br>" + surl+"<br><br>"+htmlEx, "HTML response", JOptionPane.ERROR_MESSAGE );
        }
    }
    
    private void plotUrl( final String surl ) {
        Map<Object,Object> changes= new HashMap();
        dom.getController().pendingChanges(changes);
        dom.getController().registerPendingChange( this, PENDING_CHANGE_PLOTURI );
        Runnable run= new Runnable() {
            @Override
            public void run() {
                dom.getController().performingChange( AutoplotUI.this, PENDING_CHANGE_PLOTURI );
                plotUrlImmediately(surl);
                dom.getController().changePerformed( AutoplotUI.this, PENDING_CHANGE_PLOTURI );
            }
        };
        if ( false && SwingUtilities.isEventDispatchThread() ) {
            logger.fine("plotUrl on different thread");
            new Thread(run).start();
        } else {
            logger.fine("plotUrl on this thread");
            run.run();
        }
    }


    /**
     * add a new plot and plotElement.  This is attached to control-enter.
     */
    private void plotAnotherUrl() {
        plotAnotherUrl((String) dataSetSelector.getValue(), null );
    }

    /**
     * 
     * @param surl
     * @param options null or a map containing direction.
     */
    private void plotAnotherUrl( final String surl, Map<String,Object> options) {
        try {
            logger.log(Level.FINE, "plotAnotherUrl({0})", surl);
            if ( options!=null && LayoutConstants.ABOVE==options.get("direction") ) {
                Plot domPlot = dom.getController().addPlot(LayoutConstants.ABOVE);
                PlotElement panel= dom.getController().addPlotElement( domPlot,null );
                dom.getController().getDataSourceFilterFor(panel).setUri(surl);
                dom.getController().setPlotElement(panel);
            } else {
                PlotElement panel= dom.getController().addPlotElement( null,null );
                dom.getController().getDataSourceFilterFor(panel).setUri(surl);
                dom.getController().setPlotElement(panel);
            }
            
        } catch (RuntimeException ex) {
            applicationModel.getExceptionHandler().handleUncaught(ex);
            setStatus(ERROR_ICON,ex.getMessage());
        }
    }

    /**
     * add a plotElement to the focus plot.  This is attached to shift-enter.
     */
    private void overplotAnotherUrl() {
        overplotAnotherUrl( (String) dataSetSelector.getValue() );
    }

    private void overplotAnotherUrl( final String surl ) {
        try {
            logger.log(Level.FINE, "overplotAnotherUrl({0})", surl);
            PlotElement panel= dom.getController().addPlotElement( dom.getController().getPlot() ,null );
            dom.getController().getDataSourceFilterFor(panel).setUri(surl);
            dom.getController().setPlotElement(panel);
            
        } catch (RuntimeException ex) {
            applicationModel.getExceptionHandler().handleUncaught(ex);
            setStatus(ERROR_ICON,ex.getMessage());
        }
    }

    /**
     * reset to the size in defaults.
     */
    public void resizeForDefaultCanvasSize() {
        int width= dom.getOptions().getWidth();
        int height= dom.getOptions().getHeight();
        logger.log(Level.FINE, "resize canvas to {0,number,#}x{1,number,#}", new Object[]{width, height});
        resizeForCanvasSize( width,height );
        width= dom.getCanvases(0).getWidth();
        height= dom.getCanvases(0).getHeight();
        logger.log(Level.FINE, "final size of canvas: {0,number,#}x{1,number,#}", new Object[]{width, height});           
    }
    
    int windowExtraWidth= 0; 
    int windowExtraHeight= 0; 
    
    /**
     * resize the outer GUI attempting to get a fitted canvas size.  This fixes the
     * problem where a loaded vap doesn't appear as it does when it was saved because
     * the canvas is resized.
     * 
     * @param w the width of the canvas
     * @param h the height of the canvas
     * @return nominal scale factor
     */
    public double resizeForCanvasSize( int w, int h ) {
        return resizeForCanvasSize( w, h, windowExtraWidth, windowExtraHeight );
    }
    
    /**
     * resize the outer GUI attempting to get a fitted canvas size.  This fixes the
     * problem where a loaded vap doesn't appear as it does when it was saved because
     * the canvas is resized.
     * @param w the width of the canvas
     * @param h the height of the canvas
     * @param extraW extra width needed by the GUI
     * @param extraH extra height needed by the GUI
     * @return 
     */
    public double resizeForCanvasSize( int w, int h, int extraW, int extraH ) {
        resizeLogger.log(Level.FINE, "resizeForCanvasSize({0,number,#},{1,number,#},{2,number,#},{3,number,#})", 
                new Object[]{w, h, extraW, extraH});
        Component parentToAdjust;
        if ( SwingUtilities.isDescendingFrom( applicationModel.getCanvas(), this ) ) {
            parentToAdjust= this;
        } else {
            parentToAdjust= SwingUtilities.getWindowAncestor(applicationModel.getCanvas());
        }
        boolean fitted= this.applicationModel.dom.getCanvases(0).isFitted();
        Dimension dout= parentToAdjust.getSize();
        resizeLogger.log(Level.FINER, "old parentToAdjust.getSize: {0,number,#}x{1,number,#}", 
                new Object[]{dout.width, dout.height});
        Dimension din= this.applicationModel.getCanvas().getSize();
        Dimension desiredAppSize= new Dimension();

        GraphicsConfiguration gc= getGraphicsConfiguration();
        Dimension screenSize = gc.getBounds().getSize(); // support multiple displays
        //Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        
        boolean maximize= false;
        
        if ( fitted ) {
            int maximizedPixelGain= 0; // number of pixels gained by maximizing. Windows doesn't draw borders when window is maximized.
            String osName= System.getProperty("os.name");
            if ( osName.startsWith("Windows") ) { // TODO: figure out how to measure this.
                maximizedPixelGain= 8;
            } else if ( osName.startsWith("Linux") ) {
                maximizedPixelGain= 10;
            } 
            
            if ( extraW>=0 && extraH>=0 ) {
                windowExtraHeight= extraH; // should be 61 for detacted window in Linux 2024.
                windowExtraWidth= extraW;
            } else {
                resizeLogger.fine("ignoring impossible width or height");
            }
                        
            resizeLogger.log(Level.FINER, "windowExtraWidth={0} windowExtraHeight={1}", 
                    new Object[] { windowExtraWidth, windowExtraHeight } );
            desiredAppSize.width= w + windowExtraWidth ;
            desiredAppSize.height= h + windowExtraHeight;
            
            if ( w > screenSize.getWidth() - maximizedPixelGain && 
                    w < screenSize.getWidth() ) {
                maximize= true;
            }
            
        } else {
            desiredAppSize.width= dout.width;
            desiredAppSize.height= dout.height;
        }

        double scale= 1.0;

        boolean oldFitted= this.applicationModel.dom.getCanvases(0).isFitted();

        if ( w<640 || h<480 ) {
            this.applicationModel.dom.getCanvases(0).setFitted(false);
            this.applicationModel.dom.getCanvases(0).setHeight(h);
            this.applicationModel.dom.getCanvases(0).setWidth(w);
        } else if ( maximize ) {
            if ( parentToAdjust instanceof JFrame ) {
                ((JFrame)parentToAdjust).setExtendedState( JFrame.MAXIMIZED_BOTH );
                resizeLogger.log(Level.FINER, "resizeForCanvasSize parentToAdjust maximized");
                setStatus("Window maximized to approximate original size");
            } else {
                this.applicationModel.dom.getCanvases(0).setFitted(false);
                this.applicationModel.dom.getCanvases(0).setHeight(h);
                this.applicationModel.dom.getCanvases(0).setWidth(w);
                resizeLogger.log(Level.FINER, "resizeForCanvasSize resets canvas fitted=false {0,number,#}x{1,number,#}", 
                        new Object[]{ w, h } );
            }
            
        } else if ( desiredAppSize.width>screenSize.getWidth() || desiredAppSize.height>screenSize.getHeight() ) {
            int i;
            
            String defaultOption= System.getProperty("resizeOption","");
            logger.log(Level.FINE, "System property resizeOption set to \"{0}\"", defaultOption);
            if ( defaultOption.equals("") ) {
                String[] options= new String[] { "Scale to fit display", "Use scrollbars","Always use scrollbars" };
                i= JOptionPane.showOptionDialog( this, "Canvas size doesn't fit well on this display.  See http://autoplot.org/resizeOption", "Incompatible Canvas Size", 
                        JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE, ERROR_ICON,
                        options, options[1] );
            } else {
                i= defaultOption.equals("scrollbars") ? 1 : 0;
            }
            if ( i!= JOptionPane.CLOSED_OPTION ) {
                if ( i==0 ) {
                    double aspect= 1.0*h/w;
                    int nw, nh;
                    if ( 1.0 * screenSize.getHeight() / screenSize.getWidth() > aspect ) {
                        nw= screenSize.width * 4 / 5 ;
                        nh= (int)( nw * aspect );
                    } else {
                        int controlsHeight= 160;
                        nh= ( screenSize.height-controlsHeight ); // accommodate GUI controls
                        nw= (int)( nh / aspect );
                    }
                    scale= (double)nw/w;
                    //Font newFont= f.deriveFont( f.getSize2D() * scale );
                    //this.applicationModel.dom.getCanvases(0).setFont(newFont.toString());
                    int newW=  nw + ( dout.width - din.width );
                    int newH=  nh + ( dout.height - din.height );
                    parentToAdjust.setSize( newW, newH );
                    resizeLogger.log(Level.FINE, "resizeForCanvasSize parentToAdjust.setSize (scaling): {0,number,#}x{1,number,#}", 
                            new Object[]{ newW, newH });

                } else if ( i==1 ) { // scrollbars option.
                    this.applicationModel.dom.getCanvases(0).setFitted(false);
                    this.applicationModel.dom.getCanvases(0).setHeight(h);
                    this.applicationModel.dom.getCanvases(0).setWidth(w);
                    resizeLogger.log(Level.FINE, "resizeForCanvasSize (scrollbars) {0,number,#}x{1,number,#}", new Object[]{ w, h });
                    
                } else if ( i==2 ) { // always scrollbars
                    System.setProperty("resizeOption", "scrollbars");
                    this.applicationModel.dom.getCanvases(0).setFitted(false);
                    this.applicationModel.dom.getCanvases(0).setHeight(h);
                    this.applicationModel.dom.getCanvases(0).setWidth(w);
                    resizeLogger.log(Level.FINE, "resizeForCanvasSize (scrollbars) {0,number,#}x{1,number,#}", new Object[]{ w, h });
                    
                }
            }
        } else {
            resizeLogger.log(Level.FINE, "resizeForCanvasSize parentToAdjust.setSize  {0,number,#}x{1,number,#}", 
                    new Object[]{ desiredAppSize.width, desiredAppSize.height });
            parentToAdjust.setSize( desiredAppSize.width, desiredAppSize.height );
            if ( parentToAdjust.getSize().getWidth()!=desiredAppSize.width ) {
                this.applicationModel.dom.getCanvases(0).setFitted(false);
                setStatus("warning: unable to resize to requested size, using scrollbars.");
            }
            this.applicationModel.dom.getCanvases(0).setHeight(h);
            this.applicationModel.dom.getCanvases(0).setWidth(w);
            resizeLogger.log(Level.FINE, "resizeForCanvasSize set canvas size to  {0,number,#}x{1,number,#}", new Object[]{ w, h });
        }
        if ( oldFitted==true && this.applicationModel.dom.getCanvases(0).isFitted()==false ) {
            setStatus("warning: canvas is no longer fitted, see options->plot style->canvas size");
        }

        resizeLogger.log(Level.FINE, "resizeForCanvasSize exiting, scale={0}", scale);
        
        return scale;
    }

    @Override
    public void setSize(int width, int height) {
        resizeLogger.log(Level.FINE, "AutoplotUI.setSize({0},{1})", new Object[]{width, height});
        super.setSize(width, height);
    }

    @Override
    public void setSize(Dimension d) {
        resizeLogger.log(Level.FINE, "AutoplotUI.setSize({0})", d);
        super.setSize(d); 
    }
    
    

    /**
     * set the status message, with "busy:" or "warning:" prefix.
     * @param message the message to display
     */    
    public void setStatus(String message) {
        if ( message.startsWith("busy:" ) ) {
            setMessage( BUSY_ICON, message.substring(5).trim() );
            logger.fine(message);
        } else if ( message.startsWith("warning:" ) ) {
            setMessage( WARNING_ICON, message.substring(8).trim() );
            logger.warning(message);
        } else if ( message.startsWith("error:" ) ) {
            setMessage( ERROR_ICON, message.substring(6).trim() );
            logger.severe(message);
        } else {
            logger.fine(message);
            setMessage(message);
        }
        
    }
    
    /**
     * set the status message and icon.  Unlike setMessage, the
     * message is logged on autoplot.gui.  Since 2013/09/18,e only WARNING_ICON is used now, and the busy status is set by checking the application controller 
     * nodes for locks.
     * @param icon the icon to display to the left of the message.  The icon is only used when it is WARNING_ICON.
     * @param message the message to display
     */
    public void setStatus( Icon icon, String message ) {
        if ( ERROR_ICON==icon ) {
            logger.severe(message);
        } else if ( WARNING_ICON==icon ) {
            logger.warning(message);
        } else {
            logger.fine(message);
        }
        setMessage(icon,message);
    }

    private void clearCache() {
        if ( AutoplotUtil.showConfirmDialog(this, "delete all cached files?", "clear cache", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            try {
                if (applicationModel.clearCache()) {
                    setStatus("cache cleared");
                } else {
                    setStatus(ERROR_ICON,"unable to clear cache");
                    JOptionPane.showMessageDialog(this, "unable to clear cache");
                }
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, "unable to clear cache: " + ex.getMessage());
            }
        }
    }

    private void maybeCreateToolsManager() {
        if ( toolsManager==null ) {
            toolsManager= new BookmarksManager(AutoplotUI.this, false, "Tools");
            toolsManager.setTitle("Tools Manager");
            toolsManager.setPrefNode("tools");
            //toolsManager.

            Binding b= Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, applicationModel, BeanProperty.create( "tools" ), toolsManager.getModel(), BeanProperty.create("list")); 
            b.bind();

            toolsManager.getModel().addPropertyChangeListener(BookmarksManagerModel.PROP_BOOKMARK, new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    updateToolsBookmarks();
                }
            });
        }
    }

    private void updateToolsBookmarks() {

        if ( toolsManager==null ) {
            maybeCreateToolsManager();
            toolsManager.getModel().addPropertyChangeListener( BookmarksManagerModel.PROP_LIST, new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    SwingUtilities.invokeLater( new Runnable() { 
                        @Override
                        public void run() {
                            toolsManager.updateBookmarks( toolsMenu, "userSep", AutoplotUI.this, AutoplotUI.this.dataSetSelector );
                        } 
                    } );
                }
            });
        }

        Runnable run= new Runnable() { 
            @Override
            public void run() {            
                toolsManager.setPrefNode("tools"); 
            } 
        };
        invokeLater( -1, false, run );

    }
    
    /**
     * access the tools manager for this application.
     * @return 
     */
    protected BookmarksManager getToolsManager() {
        return this.toolsManager;
    }
        
    
    private void maybeCreateBookmarksManager() {
        if (bookmarksManager == null) {
            bookmarksManager = new BookmarksManager(AutoplotUI.this, false, "Bookmarks");

            Binding b= Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, applicationModel, BeanProperty.create( "bookmarks" ), bookmarksManager.getModel(), BeanProperty.create("list"));
            b.bind();

            bookmarksManager.getModel().addPropertyChangeListener(BookmarksManagerModel.PROP_BOOKMARK, new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    updateBookmarks();
                }
            });
        }
    }

    protected BookmarksManager getBookmarksManager() {
        return this.bookmarksManager;
    }
    
    private void updateBookmarks() {

        if ( bookmarksManager==null ) {
            maybeCreateBookmarksManager();
            bookmarksManager.getModel().addPropertyChangeListener( BookmarksManagerModel.PROP_LIST, new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    SwingUtilities.invokeLater( new Runnable() { 
                        @Override
                        public void run() {
                            bookmarksManager.updateBookmarks( bookmarksMenu, AutoplotUI.this, AutoplotUI.this.dataSetSelector );
                        }
                    } );
                }
            });
        }

        if ( !bookmarksManager.hasPrefNode("bookmarks") ) {
            if ( bookmarksManager.hasPrefNode("autoplot") ) {
                bookmarksManager.setPrefNode("autoplot");
                if ( bookmarksManager.getModel().getList()==null ) {
                    setStatus("importing legacy bookmarks");
                    bookmarksManager.setPrefNode("autoplot");
                    List<Bookmark> bookmarks = applicationModel.getLegacyBookmarks();
                    bookmarksManager.getModel().setList(bookmarks);
                }
                bookmarksManager.resetPrefNode("bookmarks");
            } else { // new user state
                setStatus("busy: loading initial demo bookmarks");
                List<Bookmark> bookmarks = applicationModel.getLegacyBookmarks();
                bookmarksManager.getModel().setList(bookmarks);
                bookmarksManager.resetPrefNode("bookmarks");
                setStatus("done loading initial demo bookmarks");
            }
        }

        Runnable run= new Runnable() { 
            @Override
            public void run() {            
                bookmarksManager.setPrefNode("bookmarks"); 
                if ( initialBookmarksUrl!=null ) {
                    loadInitialBookmarks(initialBookmarksUrl);
                    initialBookmarksUrl= null;
                }
            }
        };
        invokeLater( -1, false, run );

//        addBookmarks(bookmarksMenu, bookmarks);
    }

    public static PersistentStateSupport getPersistentStateSupport(final AutoplotUI parent, final ApplicationModel applicationModel) {
        final PersistentStateSupport stateSupport = new PersistentStateSupport(parent, null, "vap") {

            @Override
            protected void saveImpl(File f,String scheme,Map<String,Object> options) throws IOException {
                applicationModel.doSave(f,scheme,options);
                applicationModel.addRecent( DataSetURI.fromFile( f ));
                parent.setStatus("saved " + f);
            }

            @Override
            protected void openImpl(final File file) throws IOException {
                applicationModel.doOpen(file);
                parent.setStatus("opened " + file);
            }
        };

        stateSupport.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent ev) {
                String label;
                if (stateSupport.isCurrentFileOpened()) {
                    label = stateSupport.getCurrentFile() + " " + (stateSupport.isDirty() ? "*" : "");
                    parent.setMessage(label);
                }
            }
        });

        return stateSupport;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        bindingGroup = new org.jdesktop.beansbinding.BindingGroup();

        addressBarButtonGroup = new javax.swing.ButtonGroup();
        statusLabel = new javax.swing.JLabel();
        tabbedPanelContainer = new javax.swing.JPanel();
        statusTextField = new javax.swing.JTextField();
        timeRangePanel = new javax.swing.JPanel();
        dataSetSelector = new org.autoplot.datasource.DataSetSelector();
        uriTimeRangeToggleButton1 = new org.autoplot.UriTimeRangeToggleButton();
        jMenuBar1 = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        editMenu = new javax.swing.JMenu();
        undoMenuItem = new javax.swing.JMenuItem();
        redoMenuItem = new javax.swing.JMenuItem();
        undoMultipleMenu = new javax.swing.JMenu();
        editDomSeparator = new javax.swing.JSeparator();
        editDomMenuItem = new javax.swing.JMenuItem();
        editOptions = new javax.swing.JMenuItem();
        inspectVapFileMenuItem = new javax.swing.JMenuItem();
        jSeparator1 = new javax.swing.JSeparator();
        pasteDataSetURLMenuItem = new javax.swing.JMenuItem();
        copyDataSetURLMenuItem = new javax.swing.JMenuItem();
        copyImageMenuItem = new javax.swing.JMenuItem();
        viewMenu = new javax.swing.JMenu();
        addressBarMenu = new javax.swing.JMenu();
        dataSetSelectorMenuItem = new javax.swing.JRadioButtonMenuItem();
        timeRangeSelectorMenuItem = new javax.swing.JRadioButtonMenuItem();
        textSizeMenu = new javax.swing.JMenu();
        jMenuItem1 = new javax.swing.JMenuItem();
        jMenuItem2 = new javax.swing.JMenuItem();
        resetFontMI = new javax.swing.JMenuItem();
        addSizeMenu = new javax.swing.JMenu();
        resetAppSize = new javax.swing.JMenuItem();
        jSeparator4 = new javax.swing.JPopupMenu.Separator();
        resetZoomMenu = new javax.swing.JMenu();
        resetZoomMenuItem = new javax.swing.JMenuItem();
        resetXMenuItem = new javax.swing.JMenuItem();
        resetYMenuItem = new javax.swing.JMenuItem();
        resetZMenuItem = new javax.swing.JMenuItem();
        jMenu1 = new javax.swing.JMenu();
        zoomInMenuItem = new javax.swing.JMenuItem();
        zoomOutMenuItem = new javax.swing.JMenuItem();
        optionsMenu = new javax.swing.JMenu();
        renderingOptionsMenu = new javax.swing.JMenu();
        drawGridCheckBox = new javax.swing.JCheckBoxMenuItem();
        doyCB = new javax.swing.JCheckBoxMenuItem();
        nnCb = new javax.swing.JCheckBoxMenuItem();
        plotStyleMenu = new javax.swing.JMenu();
        fontsAndColorsMenuItem = new javax.swing.JMenuItem();
        canvasSizeMenuItem = new javax.swing.JMenuItem();
        enableFeatureMenu = new javax.swing.JMenu();
        scriptPanelMenuItem = new javax.swing.JCheckBoxMenuItem();
        logConsoleMenuItem = new javax.swing.JCheckBoxMenuItem();
        serverCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        dataPanelCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        layoutPanelCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        referenceCacheCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        autoMenu = new javax.swing.JMenu();
        autoRangingCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        autoLabellingCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        autoLayoutCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        additionalOptionsMI = new javax.swing.JMenuItem();
        saveOptionsMenuItem = new javax.swing.JMenuItem();
        bookmarksMenu = new javax.swing.JMenu();
        toolsMenu = new javax.swing.JMenu();
        cacheMenu = new javax.swing.JMenu();
        jMenuItem3 = new javax.swing.JMenuItem();
        jMenuItem4 = new javax.swing.JMenuItem();
        jMenuItem7 = new javax.swing.JMenuItem();
        workOfflineCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        resetMemoryCachesMI = new javax.swing.JMenuItem();
        manageFilesystemsMI = new javax.swing.JMenuItem();
        jSeparator3 = new javax.swing.JSeparator();
        pngWalkMenuItem = new javax.swing.JMenuItem();
        createPngWalkMenuItem = new javax.swing.JMenuItem();
        runBatchMenuItem = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JPopupMenu.Separator();
        jMenuItem6 = new javax.swing.JMenuItem();
        fixLayoutMenuItem = new javax.swing.JMenuItem();
        createPngWalkSeparator = new javax.swing.JSeparator();
        aggregateMenuItem = new javax.swing.JMenuItem();
        replaceFileMenuItem = new javax.swing.JMenuItem();
        mashDataMenuItem = new javax.swing.JMenuItem();
        filtersMenuItem = new javax.swing.JMenuItem();
        aggSeparator = new javax.swing.JSeparator();
        decodeURLItem = new javax.swing.JMenuItem();
        reloadAllMenuItem = new javax.swing.JMenuItem();
        toolsUserSep = new javax.swing.JPopupMenu.Separator();
        helpMenu = new javax.swing.JMenu();
        autoplotHelpMenuItem = new javax.swing.JMenuItem();
        gettingStartedMenuItem = new javax.swing.JMenuItem();
        jMenuItem5 = new javax.swing.JMenuItem();
        aboutDas2MenuItem = new javax.swing.JMenuItem();
        autoplotHomepageButton = new javax.swing.JMenuItem();
        searchToolTipsMenuItem = new javax.swing.JMenuItem();
        exceptionReport = new javax.swing.JMenuItem();
        aboutAutoplotMenuItem = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("Autoplot");
        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                formMouseClicked(evt);
            }
        });

        statusLabel.setFont(statusLabel.getFont().deriveFont(statusLabel.getFont().getSize()-2f));
        statusLabel.setText("starting...");
        statusLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                statusLabelMouseClicked(evt);
            }
        });

        tabbedPanelContainer.setLayout(new java.awt.BorderLayout());

        statusTextField.setEditable(false);
        statusTextField.setFont(statusTextField.getFont().deriveFont(statusTextField.getFont().getSize()-2f));
        statusTextField.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));

        org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, statusLabel, org.jdesktop.beansbinding.ELProperty.create("${text}"), statusTextField, org.jdesktop.beansbinding.BeanProperty.create("text"));
        bindingGroup.addBinding(binding);

        statusTextField.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                statusTextFieldMouseClicked(evt);
            }
        });

        timeRangePanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        timeRangePanel.setLayout(new java.awt.CardLayout());

        dataSetSelector.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        dataSetSelector.setPromptText("<html><i>Just a moment...</i></html>");
        dataSetSelector.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSetSelectorActionPerformed(evt);
            }
        });
        timeRangePanel.add(dataSetSelector, "dataCard");

        org.jdesktop.layout.GroupLayout uriTimeRangeToggleButton1Layout = new org.jdesktop.layout.GroupLayout(uriTimeRangeToggleButton1);
        uriTimeRangeToggleButton1.setLayout(uriTimeRangeToggleButton1Layout);
        uriTimeRangeToggleButton1Layout.setHorizontalGroup(
            uriTimeRangeToggleButton1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 0, Short.MAX_VALUE)
        );
        uriTimeRangeToggleButton1Layout.setVerticalGroup(
            uriTimeRangeToggleButton1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 28, Short.MAX_VALUE)
        );

        fileMenu.setText("File");
        jMenuBar1.add(fileMenu);

        editMenu.setText("Edit");

        undoMenuItem.setAction(undoRedoSupport.getUndoAction());
        undoMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
        );
        undoMenuItem.setText("Undo");
        undoMenuItem.setToolTipText("Undo the last operation");
        editMenu.add(undoMenuItem);

        redoMenuItem.setAction(undoRedoSupport.getRedoAction());
        redoMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y,Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        redoMenuItem.setText("Redo");
        redoMenuItem.setToolTipText("Redo the last undone operation");
        editMenu.add(redoMenuItem);

        undoMultipleMenu.setText("Undo...");
        editMenu.add(undoMultipleMenu);
        editMenu.add(editDomSeparator);

        editDomMenuItem.setText("Edit DOM");
        editDomMenuItem.setToolTipText("Edit the application state using the property editor");
        editDomMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editDomMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(editDomMenuItem);

        editOptions.setText("Options...");
        editOptions.setToolTipText("Edit user options like background colors and fonts");
        editOptions.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editOptionsActionPerformed(evt);
            }
        });
        editMenu.add(editOptions);

        inspectVapFileMenuItem.setText("Inspect Vap File...");
        inspectVapFileMenuItem.setToolTipText("View a vap file from a local disk in the property editor");
        inspectVapFileMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                inspectVapFileMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(inspectVapFileMenuItem);
        editMenu.add(jSeparator1);

        pasteDataSetURLMenuItem.setText("Paste URI");
        pasteDataSetURLMenuItem.setToolTipText("Paste a data address in the system clipboard into the address bar");
        pasteDataSetURLMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pasteDataSetURLMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(pasteDataSetURLMenuItem);

        copyDataSetURLMenuItem.setText("Copy URI");
        copyDataSetURLMenuItem.setToolTipText("Copy the data address in the address bar into the system clipboard");
        copyDataSetURLMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyDataSetURLMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(copyDataSetURLMenuItem);

        copyImageMenuItem.setText("Copy Image To Clipboard");
        copyImageMenuItem.setToolTipText("Copy the canvas image into the system clipboard.");
        copyImageMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyImageMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(copyImageMenuItem);

        jMenuBar1.add(editMenu);

        viewMenu.setText("View");

        addressBarMenu.setText("Address Bar");

        dataSetSelectorMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_D, java.awt.event.InputEvent.CTRL_MASK));
        addressBarButtonGroup.add(dataSetSelectorMenuItem);
        dataSetSelectorMenuItem.setSelected(true);
        dataSetSelectorMenuItem.setText("Data Set Selector");
        dataSetSelectorMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSetSelectorMenuItemActionPerformed(evt);
            }
        });
        addressBarMenu.add(dataSetSelectorMenuItem);

        timeRangeSelectorMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, java.awt.event.InputEvent.CTRL_MASK));
        addressBarButtonGroup.add(timeRangeSelectorMenuItem);
        timeRangeSelectorMenuItem.setText("Time Range Selector");
        timeRangeSelectorMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timeRangeSelectorMenuItemActionPerformed(evt);
            }
        });
        addressBarMenu.add(timeRangeSelectorMenuItem);

        viewMenu.add(addressBarMenu);

        textSizeMenu.setText("Text Size");

        jMenuItem1.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS,Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        jMenuItem1.setText("Bigger");
        jMenuItem1.setToolTipText("Make canvas font bigger");
        jMenuItem1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem1ActionPerformed(evt);
            }
        });
        textSizeMenu.add(jMenuItem1);

        jMenuItem2.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS,Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
        );
        jMenuItem2.setText("Smaller");
        jMenuItem2.setToolTipText("Make canvas font smaller");
        jMenuItem2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem2ActionPerformed(evt);
            }
        });
        textSizeMenu.add(jMenuItem2);

        resetFontMI.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0,Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
        );
        resetFontMI.setText("Reset to 100%");
        resetFontMI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetFontMIActionPerformed(evt);
            }
        });
        textSizeMenu.add(resetFontMI);

        viewMenu.add(textSizeMenu);

        addSizeMenu.setText("App Size");

        resetAppSize.setText("Reset to default size");
        resetAppSize.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetAppSizeActionPerformed(evt);
            }
        });
        addSizeMenu.add(resetAppSize);

        viewMenu.add(addSizeMenu);
        viewMenu.add(jSeparator4);

        resetZoomMenu.setText("Reset Zoom");

        resetZoomMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, Event.CTRL_MASK|Event.SHIFT_MASK ));
        resetZoomMenuItem.setText("Reset Zoom");
        resetZoomMenuItem.setToolTipText("Revert to the original axis settings");
        resetZoomMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetZoomMenuItemActionPerformed(evt);
            }
        });
        resetZoomMenu.add(resetZoomMenuItem);

        resetXMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, Event.CTRL_MASK|Event.SHIFT_MASK ));
        resetXMenuItem.setText("Reset X");
        resetXMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetXMenuItemActionPerformed(evt);
            }
        });
        resetZoomMenu.add(resetXMenuItem);

        resetYMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, Event.CTRL_MASK|Event.SHIFT_MASK ));
        resetYMenuItem.setText("Reset Y");
        resetYMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetYMenuItemActionPerformed(evt);
            }
        });
        resetZoomMenu.add(resetYMenuItem);

        resetZMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Event.CTRL_MASK|Event.SHIFT_MASK ));
        resetZMenuItem.setText("Reset Z");
        resetZMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetZMenuItemActionPerformed(evt);
            }
        });
        resetZoomMenu.add(resetZMenuItem);

        viewMenu.add(resetZoomMenu);

        jMenu1.setText("Zoom");
        jMenu1.setToolTipText("Note zooming can be done by dragging ranges with the mouse, or use the mouse wheel.");

        zoomInMenuItem.setText("Zoom In");
        zoomInMenuItem.setToolTipText("zoom in on the X axis");
        zoomInMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomInMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(zoomInMenuItem);

        zoomOutMenuItem.setText("Zoom Out");
        zoomOutMenuItem.setToolTipText("zoom out the X axis");
        zoomOutMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomOutMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(zoomOutMenuItem);

        viewMenu.add(jMenu1);

        jMenuBar1.add(viewMenu);

        optionsMenu.setText("Options");

        renderingOptionsMenu.setText("Rendering Options");

        drawGridCheckBox.setSelected(true);
        drawGridCheckBox.setText("Draw Grid");
        drawGridCheckBox.setToolTipText("Draw gridlines at major ticks");
        drawGridCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                drawGridCheckBoxActionPerformed(evt);
            }
        });
        renderingOptionsMenu.add(drawGridCheckBox);

        doyCB.setText("Day of Year Labels");
        doyCB.setToolTipText("Use Day of Year instead of Year-Month-Day for labels");
        doyCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                doyCBActionPerformed(evt);
            }
        });
        renderingOptionsMenu.add(doyCB);

        nnCb.setText("Nearest Neighbor Spectrograms");
        nnCb.setToolTipText("Use Nearest Neighbor rebinning for new spectrograms");
        nnCb.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nnCbActionPerformed(evt);
            }
        });
        renderingOptionsMenu.add(nnCb);

        optionsMenu.add(renderingOptionsMenu);

        plotStyleMenu.setText("Plot Style");

        fontsAndColorsMenuItem.setText("Fonts and Colors...");
        fontsAndColorsMenuItem.setToolTipText("Edit canvas font and colors");
        fontsAndColorsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fontsAndColorsMenuItemActionPerformed(evt);
            }
        });
        plotStyleMenu.add(fontsAndColorsMenuItem);

        canvasSizeMenuItem.setText("Canvas Size...");
        canvasSizeMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                canvasSizeMenuItemActionPerformed(evt);
            }
        });
        plotStyleMenu.add(canvasSizeMenuItem);

        optionsMenu.add(plotStyleMenu);

        enableFeatureMenu.setText("Enable Feature");

        scriptPanelMenuItem.setText("Script Panel");
        scriptPanelMenuItem.setToolTipText("Script Panel adds a tab that displays scripts used for the jython data source.  It also provides a way to create new jython sources.");
        scriptPanelMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                scriptPanelMenuItemActionPerformed(evt);
            }
        });
        enableFeatureMenu.add(scriptPanelMenuItem);

        logConsoleMenuItem.setText("Log Console");
        logConsoleMenuItem.setToolTipText("Add a tab that receives and displays messages posted to the java logging system.  ");
        logConsoleMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                logConsoleMenuItemActionPerformed(evt);
            }
        });
        enableFeatureMenu.add(logConsoleMenuItem);

        serverCheckBoxMenuItem.setText("Server");
        serverCheckBoxMenuItem.setToolTipText("<html> Start up back end server that allows commands to be send to Autoplot via a port. </html>");
        serverCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                serverCheckBoxMenuItemActionPerformed(evt);
            }
        });
        enableFeatureMenu.add(serverCheckBoxMenuItem);

        dataPanelCheckBoxMenuItem.setText("Data Panel");
        dataPanelCheckBoxMenuItem.setToolTipText("The data panel allows for explicitly setting valid range and fill values for the dataset, and additional controls for data reduction before plotting. ");
        enableFeatureMenu.add(dataPanelCheckBoxMenuItem);

        layoutPanelCheckBoxMenuItem.setText("Layout Panel");
        layoutPanelCheckBoxMenuItem.setToolTipText("Enables the layout panel, which shows all the plots and plot elements in thier relative positions.\n");
        enableFeatureMenu.add(layoutPanelCheckBoxMenuItem);

        referenceCacheCheckBoxMenuItem.setSelected(true);
        referenceCacheCheckBoxMenuItem.setText("Reference Caching");
        referenceCacheCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                referenceCacheCheckBoxMenuItemActionPerformed(evt);
            }
        });
        enableFeatureMenu.add(referenceCacheCheckBoxMenuItem);

        optionsMenu.add(enableFeatureMenu);

        autoMenu.setText("Auto");

        autoRangingCheckBoxMenuItem.setSelected(true);
        autoRangingCheckBoxMenuItem.setText("AutoRanging");
        autoRangingCheckBoxMenuItem.setToolTipText("Allow automatic axis range setting.  Range is based on metadata hints and data range.");
        autoMenu.add(autoRangingCheckBoxMenuItem);

        autoLabellingCheckBoxMenuItem.setSelected(true);
        autoLabellingCheckBoxMenuItem.setText("AutoLabelling");
        autoLabellingCheckBoxMenuItem.setToolTipText("Allow automatic setting of axis labels based on metadata. ");
        autoMenu.add(autoLabellingCheckBoxMenuItem);

        autoLayoutCheckBoxMenuItem.setSelected(true);
        autoLayoutCheckBoxMenuItem.setText("AutoLayout");
        autoLayoutCheckBoxMenuItem.setToolTipText("<html><p>Allow the application to reposition axes so labels are not clipped and unused space is reduced.  </P><p>Axes can be positioned manually by turning off this option, then hold shift down to enable plot corner drag anchors.</p></html>");
        autoLayoutCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                autoLayoutCheckBoxMenuItemActionPerformed(evt);
            }
        });
        autoMenu.add(autoLayoutCheckBoxMenuItem);

        optionsMenu.add(autoMenu);

        additionalOptionsMI.setText("Additional Options...");
        additionalOptionsMI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                additionalOptionsMIActionPerformed(evt);
            }
        });
        optionsMenu.add(additionalOptionsMI);

        saveOptionsMenuItem.setText("Save Options");
        saveOptionsMenuItem.setToolTipText("Save options for future sessions.");
        saveOptionsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveOptionsMenuItemActionPerformed(evt);
            }
        });
        optionsMenu.add(saveOptionsMenuItem);

        jMenuBar1.add(optionsMenu);

        bookmarksMenu.setText("Bookmarks");
        jMenuBar1.add(bookmarksMenu);

        toolsMenu.setText("Tools");

        cacheMenu.setText("Cache");

        jMenuItem3.setText("Manage Cached Files...");
        jMenuItem3.setToolTipText("Manage cache of downloaded data files.");
        jMenuItem3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem3ActionPerformed(evt);
            }
        });
        cacheMenu.add(jMenuItem3);

        jMenuItem4.setText("Clear Cache");
        jMenuItem4.setToolTipText("Delete all downloaded data files.");
        jMenuItem4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem4ActionPerformed(evt);
            }
        });
        cacheMenu.add(jMenuItem4);

        jMenuItem7.setText("Move Cache...");
        jMenuItem7.setToolTipText("Move file cache to new location");
        jMenuItem7.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem7ActionPerformed(evt);
            }
        });
        cacheMenu.add(jMenuItem7);

        workOfflineCheckBoxMenuItem.setText("Work Offline");
        workOfflineCheckBoxMenuItem.setToolTipText("Only use previously downloaded files. ");
        workOfflineCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                workOfflineCheckBoxMenuItemActionPerformed(evt);
            }
        });
        cacheMenu.add(workOfflineCheckBoxMenuItem);

        resetMemoryCachesMI.setText("Reset Memory Caches");
        resetMemoryCachesMI.setToolTipText("Reset the internal state of the filesystems, re-listing them and resetting offline status.");
        resetMemoryCachesMI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetMemoryCachesMIActionPerformed(evt);
            }
        });
        cacheMenu.add(resetMemoryCachesMI);

        manageFilesystemsMI.setText("Manage Filesystems");
        manageFilesystemsMI.setToolTipText("Show the active filesystems and their status.");
        manageFilesystemsMI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                manageFilesystemsMIActionPerformed(evt);
            }
        });
        cacheMenu.add(manageFilesystemsMI);

        toolsMenu.add(cacheMenu);
        toolsMenu.add(jSeparator3);

        pngWalkMenuItem.setText("PNG Walk Viewer");
        pngWalkMenuItem.setToolTipText("Bring up the PNG Walk tool to browse a set of images.");
        pngWalkMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pngWalkMenuItemActionPerformed(evt);
            }
        });
        toolsMenu.add(pngWalkMenuItem);

        createPngWalkMenuItem.setText("Create PNG Walk...");
        createPngWalkMenuItem.setToolTipText("Create a series of images, and start the PNG Walk tool to browse the images.");
        createPngWalkMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                createPngWalkMenuItemActionPerformed(evt);
            }
        });
        toolsMenu.add(createPngWalkMenuItem);

        runBatchMenuItem.setText("Run Batch...");
        runBatchMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runBatchMenuItemActionPerformed(evt);
            }
        });
        toolsMenu.add(runBatchMenuItem);
        toolsMenu.add(jSeparator2);

        jMenuItem6.setText("Events List");
        jMenuItem6.setToolTipText("Use an events list to control time range");
        jMenuItem6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem6ActionPerformed(evt);
            }
        });
        toolsMenu.add(jMenuItem6);

        fixLayoutMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, java.awt.event.InputEvent.CTRL_MASK));
        fixLayoutMenuItem.setText("Fix Layout...");
        fixLayoutMenuItem.setToolTipText("Run new layout routine that removes spaces between plots");
        fixLayoutMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fixLayoutMenuItemActionPerformed(evt);
            }
        });
        toolsMenu.add(fixLayoutMenuItem);
        toolsMenu.add(createPngWalkSeparator);

        aggregateMenuItem.setText("Aggregate...");
        aggregateMenuItem.setToolTipText("Combine files into a time series");
        aggregateMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                aggregateMenuItemActionPerformed(evt);
            }
        });
        toolsMenu.add(aggregateMenuItem);

        replaceFileMenuItem.setText("Replace File...");
        replaceFileMenuItem.setToolTipText("<html>Replace the file with a new one.  This assumes that any parameters used to load the file<br>should be preserved, and axis settings should be preserved.  We re-range on the x-axis, since often we are switching to a new interval in time.</html>\n");
        replaceFileMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                replaceFileMenuItemActionPerformed(evt);
            }
        });
        toolsMenu.add(replaceFileMenuItem);

        mashDataMenuItem.setText("Mash Data...");
        mashDataMenuItem.setToolTipText("Combine data from several sources.");
        mashDataMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mashDataMenuItemActionPerformed(evt);
            }
        });
        toolsMenu.add(mashDataMenuItem);

        filtersMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/autoplot/resources/pipeMag2.png"))); // NOI18N
        filtersMenuItem.setText("Additional Operations...");
        filtersMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                filtersMenuItemActionPerformed(evt);
            }
        });
        toolsMenu.add(filtersMenuItem);
        toolsMenu.add(aggSeparator);

        decodeURLItem.setText("Decode URL");
        decodeURLItem.setToolTipText("Decode the URL escapes to correct the URL\n");
        decodeURLItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                decodeURLItemActionPerformed(evt);
            }
        });
        toolsMenu.add(decodeURLItem);

        reloadAllMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L, java.awt.event.InputEvent.CTRL_MASK));
        reloadAllMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/autoplot/resources/reload.png"))); // NOI18N
        reloadAllMenuItem.setText("Reload All Data");
        reloadAllMenuItem.setToolTipText("Reload all data, updating to get any changes.  Axis settings and labels should remain the same.");
        reloadAllMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                reloadAllMenuItemActionPerformed(evt);
            }
        });
        toolsMenu.add(reloadAllMenuItem);

        toolsUserSep.setToolTipText("User items below here");
        toolsUserSep.setName("userSep"); // NOI18N
        toolsMenu.add(toolsUserSep);

        jMenuBar1.add(toolsMenu);

        helpMenu.setText("Help");
        helpMenu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                helpMenuActionPerformed(evt);
            }
        });

        CSH.setHelpIDString(autoplotHelpMenuItem, "aphelp_main");
        autoplotHelpMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F1, 0));
        autoplotHelpMenuItem.setText("Help Contents...");
        autoplotHelpMenuItem.setToolTipText("Start up help system");
        autoplotHelpMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                autoplotHelpMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(autoplotHelpMenuItem);

        gettingStartedMenuItem.setText("Getting Started...");
        gettingStartedMenuItem.setToolTipText("Bring up the getting started dialog");
        gettingStartedMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                gettingStartedMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(gettingStartedMenuItem);

        jMenuItem5.setText("Release Notes");
        jMenuItem5.setToolTipText("View release notes");
        jMenuItem5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem5ActionPerformed(evt);
            }
        });
        helpMenu.add(jMenuItem5);

        aboutDas2MenuItem.setText("Das2 Homepage");
        aboutDas2MenuItem.setToolTipText("Browse the Das2 homepage, which provides graphics and interactivity.");
        aboutDas2MenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                aboutDas2MenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(aboutDas2MenuItem);

        autoplotHomepageButton.setText("Autoplot Homepage");
        autoplotHomepageButton.setToolTipText("Browse the Autoplot homepage");
        autoplotHomepageButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                autoplotHomepageButtonActionPerformed(evt);
            }
        });
        helpMenu.add(autoplotHomepageButton);

        searchToolTipsMenuItem.setText("Search Tooltips...");
        searchToolTipsMenuItem.setToolTipText("Experimental search all GUI tooltips");
        searchToolTipsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchToolTipsMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(searchToolTipsMenuItem);

        exceptionReport.setText("Provide Feedback...");
        exceptionReport.setToolTipText("Send feedback to application support");
        exceptionReport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exceptionReportActionPerformed(evt);
            }
        });
        helpMenu.add(exceptionReport);

        aboutAutoplotMenuItem.setText("About Autoplot");
        aboutAutoplotMenuItem.setToolTipText("Show information about this release");
        aboutAutoplotMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                aboutAutoplotMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(aboutAutoplotMenuItem);

        jMenuBar1.add(helpMenu);

        setJMenuBar(jMenuBar1);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(layout.createSequentialGroup()
                        .add(12, 12, 12)
                        .add(timeRangePanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 708, Short.MAX_VALUE))
                    .add(layout.createSequentialGroup()
                        .add(statusLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 16, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(4, 4, 4)
                        .add(statusTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 700, Short.MAX_VALUE)))
                .addContainerGap())
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                .add(org.jdesktop.layout.GroupLayout.TRAILING, tabbedPanelContainer, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 732, Short.MAX_VALUE))
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                .add(layout.createSequentialGroup()
                    .add(uriTimeRangeToggleButton1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 11, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(0, 721, Short.MAX_VALUE)))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .add(timeRangePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 31, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 695, Short.MAX_VALUE)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(statusLabel)
                    .add(statusTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                    .add(48, 48, 48)
                    .add(tabbedPanelContainer, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 686, Short.MAX_VALUE)
                    .add(20, 20, 20)))
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                .add(layout.createSequentialGroup()
                    .addContainerGap()
                    .add(uriTimeRangeToggleButton1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 28, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(714, Short.MAX_VALUE)))
        );

        bindingGroup.bind();

        pack();
    }// </editor-fold>//GEN-END:initComponents
    private void copyImageMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyImageMenuItemActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        support.doCopyDataSetImage();
    }//GEN-LAST:event_copyImageMenuItemActionPerformed

    private void copyDataSetURLMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyDataSetURLMenuItemActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        support.doCopyDataSetURL();
    }//GEN-LAST:event_copyDataSetURLMenuItemActionPerformed

    private void pasteDataSetURLMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pasteDataSetURLMenuItemActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        support.doPasteDataSetURL();
    }//GEN-LAST:event_pasteDataSetURLMenuItemActionPerformed

    /**
     * Action performed when the green "play" button is pressed.
     * @param uri the URI
     * @param modifiers the modifiers, such as KeyEvent.CTRL_MASK and KeyEvent.SHIFT_MASK.
     */
    protected void doPlotGoButton( final String uri, final int modifiers ) {
        org.das2.util.LoggerManager.getLogger("gui").log(Level.FINE, "plot URI \"{0}\"", uri);
        ExceptionHandler eh= applicationModel.getExceptionHandler();
        if ( eh instanceof GuiExceptionHandler ) {
            ((GuiExceptionHandler)eh).setFocusURI(uri);
        }
        Runnable run= new Runnable() {
            @Override
            public void run() {
                if ( AutoplotUI.this.isExpertMode() ) {
                    if ( ( modifiers & KeyEvent.CTRL_MASK ) == KeyEvent.CTRL_MASK ) {
                        if ( ( modifiers & KeyEvent.SHIFT_MASK ) == KeyEvent.SHIFT_MASK ) {
                            String uri= (String) dataSetSelector.getValue();
                            plotAnotherUrl( uri, Collections.singletonMap( "direction", LayoutConstants.ABOVE ));
                        } else {
                            plotAnotherUrl();
                        }
                    } else if ( ( modifiers & KeyEvent.SHIFT_MASK ) == KeyEvent.SHIFT_MASK )  {
                        overplotAnotherUrl();
                    } else {
                        plotUrl(uri);
                    }
                } else {
                    dom.getController().reset();
                    plotUrl( uri );
                }
            }
        };
        //run.run(); // simulate old code.
        new Thread(run,"dataSetSelectThread").start();        
    }
    
    private void dataSetSelectorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSetSelectorActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        String uri= (String) dataSetSelector.getValue();
        int modifiers= evt.getModifiers();
        doPlotGoButton( uri, modifiers );
    }//GEN-LAST:event_dataSetSelectorActionPerformed

    private void zoomOutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomOutMenuItemActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        DasPlot p= dom.getController().getPlot().getController().getDasPlot();
        DatumRange dr = DatumRangeUtil.rescale( p.getXAxis().getDatumRange(), -0.333, 1.333);
        p.getXAxis().setDatumRange(dr);
    }//GEN-LAST:event_zoomOutMenuItemActionPerformed

    private void zoomInMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomInMenuItemActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        DasPlot p= dom.getController().getPlot().getController().getDasPlot();
        DatumRange dr = DatumRangeUtil.rescale(p.getXAxis().getDatumRange(), 0.25, 0.75);
        p.getXAxis().setDatumRange(dr);
    }//GEN-LAST:event_zoomInMenuItemActionPerformed

    private void resetZoomMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetZoomMenuItemActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        dom.getController().getPlot().getController().resetZoom(true, true, true);
        if ( !AutoplotUtil.resetZoomX(dom)) {
            System.err.println("unable to zoom x");
        }
        if ( !AutoplotUtil.resetZoomY(dom)) {
            System.err.println("unable to zoom y");
        }
        if ( !AutoplotUtil.resetZoomZ(dom)) {
            System.err.println("unable to zoom z");
        }
    }//GEN-LAST:event_resetZoomMenuItemActionPerformed

    private void fontsAndColorsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fontsAndColorsMenuItemActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        if (fontAndColorsDialog == null) fontAndColorsDialog = new FontAndColorsDialog(this, false, applicationModel);
        fontAndColorsDialog.setVisible(true);
    }//GEN-LAST:event_fontsAndColorsMenuItemActionPerformed

    private void aboutAutoplotMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aboutAutoplotMenuItemActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        about( );
    }//GEN-LAST:event_aboutAutoplotMenuItemActionPerformed

    /**
     * show the about dialog, which has version information.
     */
    public void about() {
        String releaseTag="?";
        
        JTextPane jtp= new JTextPane();
        try {
            releaseTag = AboutUtil.getReleaseTag();
            String bufStr= AutoplotUtil.getAboutAutoplotHtml( this.applicationModel );

            jtp.setContentType("text/html");
            jtp.read(new StringReader(bufStr), null);
            jtp.setEditable(false);
            jtp.addHyperlinkListener( new HyperlinkListener() {
                @Override
                public void hyperlinkUpdate(HyperlinkEvent e) {
                    if ( e.getEventType()==HyperlinkEvent.EventType.ACTIVATED ) {
                        AutoplotUtil.openBrowser( e.getURL().toString() );
                    }
                }
            } );
            final JPopupMenu menu= new JPopupMenu();
            JMenuItem copyItem = menu.add(new DefaultEditorKit.CopyAction());
            copyItem.setText("Copy");
            jtp.addMouseListener( new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        if (menu != null) {
                            menu.show(e.getComponent(), e.getX(), e.getY());
                        }
                    }
                }
                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        if (menu != null) {
                            menu.show(e.getComponent(), e.getX(), e.getY());
                        }
                    }
                }
            });
        } catch ( IOException ex ) {
            jtp.setText(ex.getMessage());
        }
        //JLabel label= new JLabel(buffy.toString());
        JScrollPane pane= new JScrollPane(jtp,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED );
        pane.getVerticalScrollBar().setUnitIncrement( 12 );
        pane.setPreferredSize(new java.awt.Dimension( 640 + 50,480));
        AutoplotUtil.showMessageDialog(this, pane, "About Autoplot "+releaseTag, JOptionPane.INFORMATION_MESSAGE );
        
    }

    private void aboutDas2MenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aboutDas2MenuItemActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        AutoplotUtil.openBrowser("https://das2.org");
    }//GEN-LAST:event_aboutDas2MenuItemActionPerformed

    private void autoplotHomepageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autoplotHomepageButtonActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        AutoplotUtil.openBrowser("https://autoplot.org/");
}//GEN-LAST:event_autoplotHomepageButtonActionPerformed

    private void helpMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_helpMenuActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        // TODO add your handling code here:
    }//GEN-LAST:event_helpMenuActionPerformed

private void scriptPanelMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_scriptPanelMenuItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    applicationModel.getDocumentModel().getOptions().setScriptVisible(scriptPanelMenuItem.isSelected());
    if (scriptPanelMenuItem.isSelected() && jythonScriptPanel == null) {
        jythonScriptPanel= new JPanel( new BorderLayout() );
        scriptPanel = new JythonScriptPanel( AutoplotUI.this, this.dataSetSelector);        
        if ( logConsole!=null ) {
            logConsole.addConsoleListener( scriptPanel.getConsoleListener() );
        }
        addEditorCustomActions( scriptPanel );
        jythonScriptPanel.add(scriptPanel, BorderLayout.CENTER );
        tabs.insertTab(TAB_SCRIPT, null, jythonScriptPanel,
                String.format(  TAB_TOOLTIP_SCRIPT, TABS_TOOLTIP), 4);
        ExceptionHandler h= AutoplotUI.this.getApplicationModel().getExceptionHandler();
        if ( h!=null && h instanceof GuiExceptionHandler ) {
            ((GuiExceptionHandler)h).setScriptPanel(scriptPanel);
        }
    } else if ( scriptPanelMenuItem.isSelected() && jythonScriptPanel!=null ) {
        tabs.insertTab(TAB_SCRIPT, null, jythonScriptPanel,
                String.format(  TAB_TOOLTIP_SCRIPT, TABS_TOOLTIP), 4);
    } else {
        tabs.remove( jythonScriptPanel );
    }
    setStatus( "Use [menubar]->Options->Save Options to use this setting in future sessions.");
}//GEN-LAST:event_scriptPanelMenuItemActionPerformed

private void logConsoleMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_logConsoleMenuItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    applicationModel.getDocumentModel().getOptions().setLogConsoleVisible(logConsoleMenuItem.isSelected());
    if ( logConsoleMenuItem.isSelected() && logConsolePanel == null) {
        logConsolePanel= new JScrollPane();
        tabs.addTab(TAB_CONSOLE, null, logConsolePanel,
            String.format(  TAB_TOOLTIP_LOGCONSOLE, TABS_TOOLTIP) );
        initLogConsole();
        logConsolePanel.setViewportView( logConsole );
    } else if ( logConsoleMenuItem.isSelected() && logConsolePanel!=null ) {
        tabs.addTab(TAB_CONSOLE, null, logConsolePanel,
            String.format(  TAB_TOOLTIP_LOGCONSOLE, TABS_TOOLTIP) );
    } else {
        if ( logConsoleMenuItem.isSelected() && logConsolePanel!=null ) {
            logConsole.undoLogConsoleMessages();
        }
        tabs.remove(logConsolePanel);
    }
    setStatus( "Use [menubar]->Options->Save Options to use this setting in future sessions.");
}//GEN-LAST:event_logConsoleMenuItemActionPerformed

private void serverCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_serverCheckBoxMenuItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    applicationModel.getDocumentModel().getOptions().setServerEnabled(serverCheckBoxMenuItem.isSelected());
    if (applicationModel.getDocumentModel().getOptions().isServerEnabled()) {
        initServer();
    } else {
        stopServer();
    }
    serverCheckBoxMenuItem.setSelected( rlistener!=null );
    serverCheckBoxMenuItem.setToolTipText( rlistener==null ? null : ( "listening on port " + rlistener.getPort() ) );
    applicationModel.getDocumentModel().getOptions().setServerEnabled( rlistener!=null );
}//GEN-LAST:event_serverCheckBoxMenuItemActionPerformed

private void jMenuItem1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem1ActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    applicationModel.increaseFontSize();
}//GEN-LAST:event_jMenuItem1ActionPerformed

private void jMenuItem2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem2ActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    applicationModel.decreaseFontSize();
}//GEN-LAST:event_jMenuItem2ActionPerformed

private void jMenuItem3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem3ActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    final JDiskHogPanel panel = new JDiskHogPanel( this );
    JDialog dia = new JDialog(this, "Manage Cache", true);
    dia.add(panel);
    dia.pack();
    RequestProcessor.invokeLater(() -> {
        panel.scan( new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_FSCACHE ) ) );
    });
    dia.setLocationRelativeTo( this );
    dia.setVisible(true);
    if ( panel.isGoPressed() ) {
        panel.doPlotSelected();
    }
//JOptionPane.showMessageDialog(this, plotElement, "Manage Cache", JOptionPane.DEFAULT_OPTION, null);
}//GEN-LAST:event_jMenuItem3ActionPerformed

private void jMenuItem4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem4ActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    clearCache();
}//GEN-LAST:event_jMenuItem4ActionPerformed

private void jMenuItem5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem5ActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    try {
        String release = AboutUtil.getReleaseTag();
        if ( release.equals("(dev)") ) {
            release= "devel";
        }
        String surl = "http://autoplot.org/jnlp/" + release;
        AutoplotUtil.openBrowser(surl);
    } catch (IOException ex) {
        throw new RuntimeException(ex);
    }
}//GEN-LAST:event_jMenuItem5ActionPerformed

private void editDomMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editDomMenuItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    PropertyEditor edit= new PropertyEditor(applicationModel.dom);
    edit.showDialog(this,"DOM Properties",new ImageIcon(this.getClass().getResource("/resources/logo16.png")).getImage());
}//GEN-LAST:event_editDomMenuItemActionPerformed

private void statusLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_statusLabelMouseClicked
    //statusLabel.setText("");
    statusLabel.setIcon(IDLE_ICON);
}//GEN-LAST:event_statusLabelMouseClicked

private void inspectVapFileMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_inspectVapFileMenuItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    this.support.doInspectVap();
}//GEN-LAST:event_inspectVapFileMenuItemActionPerformed

private void autoLayoutCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autoLayoutCheckBoxMenuItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    if ( autoLayoutCheckBoxMenuItem.isSelected() ) {
        applicationModel.doAutoLayout();
    }
}//GEN-LAST:event_autoLayoutCheckBoxMenuItemActionPerformed

private void autoplotHelpMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autoplotHelpMenuItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    helpSystem.displayHelpFromEvent(evt);
}//GEN-LAST:event_autoplotHelpMenuItemActionPerformed

private void pngWalkMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pngWalkMenuItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    PngWalkTool.start( null, this);
}//GEN-LAST:event_pngWalkMenuItemActionPerformed

private void createPngWalkMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_createPngWalkMenuItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    //JythonUtil.invokeScriptSoon( AutoplotUI.class.getResource("/scripts/pngwalk/makePngWalk.jy"), applicationModel.dom, null );
    Runnable run= new Runnable() {
        @Override
        public void run() {
            try {
                CreatePngWalk.doIt( applicationModel.dom, null );
            } catch ( IOException ex ) {
                logger.log( Level.SEVERE, ex.getMessage(), ex );
                setStatus( AutoplotUI.ERROR_ICON,"Unable to create PNG Walk: " + ex.getMessage() );
                applicationModel.showMessage( "<html>Unable to create PNG Walk:<br>"+ex.getMessage(), "PNG Walk Error", JOptionPane.WARNING_MESSAGE );
            } catch ( ParseException | InterruptedException ex) {
                logger.log( Level.SEVERE, ex.getMessage(), ex );
                throw new RuntimeException(ex);
                // this mimics the jython behavior
            }
        }
    };
    RequestProcessor.invokeLater(run);
}//GEN-LAST:event_createPngWalkMenuItemActionPerformed

private void aggregateMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aggregateMenuItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    AggregateUrisDialog dia= new AggregateUrisDialog( dom, dataSetSelector );
    //JOptionPane.showConfirmDialog( rootPane, dia, "Aggregate URIs", JOptionPane.OK_CANCEL_OPTION ); //TODO: OKAY button is confusing, but how to hide it?
    dia.showDialog();
    //AggregateUrisDialog2 dia= new AggregateUrisDialog2( dom, dataSetSelector );
    //if ( JOptionPane.OK_OPTION==JOptionPane.showConfirmDialog( rootPane, dia, "Aggregate URIs", JOptionPane.OK_CANCEL_OPTION ) ) { //TODO: OKAY button is confusing, but how to hide it?
    //    dia.doAction();
    //}
}//GEN-LAST:event_aggregateMenuItemActionPerformed

private void decodeURLItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_decodeURLItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    String s = dataSetSelector.getValue();
    s = org.autoplot.datasource.DataSourceUtil.unescape(s);
    dataSetSelector.setValue(s);
}//GEN-LAST:event_decodeURLItemActionPerformed

private void statusTextFieldMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_statusTextFieldMouseClicked
    statusLabelMouseClicked(evt);
}//GEN-LAST:event_statusTextFieldMouseClicked

private void gettingStartedMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_gettingStartedMenuItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    GettingStartedPanel gettingStartedPanel= new GettingStartedPanel();

    int result= JOptionPane.showConfirmDialog( this, gettingStartedPanel, "Getting Started", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE );
    if ( result==JOptionPane.OK_OPTION ) {
        String uri= gettingStartedPanel.getInitialUri().trim();
        if ( uri.length()>0 ) this.plotUri(uri);
    }
}//GEN-LAST:event_gettingStartedMenuItemActionPerformed

private void exceptionReportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exceptionReportActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    ExceptionHandler eh= applicationModel.getExceptionHandler();
    if ( eh==null || !( eh instanceof GuiExceptionHandler ) ) {
        new GuiExceptionHandler().submitFeedback(new RuntimeException("user-generated comment"));
    } else {
        ((GuiExceptionHandler)eh).submitFeedback(new RuntimeException("user-generated comment"));
    }
}//GEN-LAST:event_exceptionReportActionPerformed

private void jMenuItem7ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem7ActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    MoveCacheDialog dia= new MoveCacheDialog();
    if ( JOptionPane.showConfirmDialog( this, dia, "Move Cache", JOptionPane.OK_CANCEL_OPTION )==JOptionPane.OK_OPTION ) {
        final String newv= dia.getNewDir().getText();
        if ( !newv.equals( AutoplotSettings.settings().getFscache() ) ) {
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    //String old= AutoplotSettings.settings().getFscache(); findbugs DLS_DEAD_LOCAL_STORE
                    File fnewv= new File(newv);
                    if ( !fnewv.exists() ) {
                        if ( !fnewv.mkdirs() ) {
                            JOptionPane.showMessageDialog( AutoplotUI.this, "Unable to move cache, couldn't create new folder.");
                            return;
                        }
                    }
                    applicationModel.moveCache(fnewv);
                }
            };
            new Thread(run).start();
        }
    }
}//GEN-LAST:event_jMenuItem7ActionPerformed

private void canvasSizeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_canvasSizeMenuItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    CanvasSizePanel p= new CanvasSizePanel();
    p.getResizeRadioButton().setSelected( dom.getCanvases(0).isFitted() );
    p.getFixedRadioButton().setSelected( !dom.getCanvases(0).isFitted() ); // ???
    p.updateSizeEnabled(); // ???
    p.getHeightTextField().setValue( dom.getCanvases(0).getHeight() );
    p.getWidthTextField().setValue( dom.getCanvases(0).getWidth() );
    if ( AutoplotUtil.showConfirmDialog( this,p,"Set Canvas Size",JOptionPane.OK_CANCEL_OPTION )==JOptionPane.OK_OPTION ) {
        if ( p.getResizeRadioButton().isSelected() ) {
            dom.getCanvases(0).setFitted(true);
        } else {
            dom.getCanvases(0).setWidth( (Integer)p.getWidthTextField().getValue() );
            dom.getCanvases(0).setHeight( (Integer)p.getHeightTextField().getValue() );
            dom.getCanvases(0).setFitted(false);
        }
    }
}//GEN-LAST:event_canvasSizeMenuItemActionPerformed

    private String editorCard = CARD_DATA_SET_SELECTOR;

    public static final String PROP_EDITORCARD = "editorCard";

    public String getEditorCard() {
        return editorCard;
    }

    public void setEditorCard(String editorCard) {
        String oldEditorCard = this.editorCard;
        this.editorCard = editorCard;
        switchToEditorCard( editorCard );
        firePropertyChange(PROP_EDITORCARD, oldEditorCard, editorCard);
    }

    public void switchToEditorCard( String selector ) {
        //String old= timeRangeEditor.isCardSelected() ? CARD_TIME_RANGE_SELECTOR : CARD_DATA_SET_SELECTOR;
        //if ( old.equals(selector) ) {
        //    return;
        //}
        logger.log(Level.FINE, "switch to selector: {0}", selector);
        if ( selector==null ) {
            throw new IllegalArgumentException("null passed in for selector");
        }
        ((CardLayout)timeRangePanel.getLayout()).show( timeRangePanel, selector );
        switch (selector) {
            case CARD_TIME_RANGE_SELECTOR:
                uriTimeRangeToggleButton1.setPosition( 0 );
                dataSetSelector.setCardSelectedNoEventKludge(false);
                timeRangeEditor.setCardSelected(true);
                break;
            case CARD_DATA_SET_SELECTOR:
                uriTimeRangeToggleButton1.setPosition( 1 );
                timeRangeEditor.setCardSelectedNoEventKludge(false);
                dataSetSelector.setCardSelected(true);
                break;
            default:
                throw new IllegalArgumentException("huh card?");
        }
        uriTimeRangeToggleButton1.setPosition( CARD_TIME_RANGE_SELECTOR.equals(selector) ? 1 : 0 );
        dom.getOptions().setUseTimeRangeEditor(CARD_TIME_RANGE_SELECTOR.equals(selector));
    }

private void dataSetSelectorMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSetSelectorMenuItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    if ( dataSetSelectorMenuItem.isSelected() ) {
        setEditorCard( CARD_DATA_SET_SELECTOR);
    }
}//GEN-LAST:event_dataSetSelectorMenuItemActionPerformed

private void timeRangeSelectorMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeRangeSelectorMenuItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    if ( timeRangeSelectorMenuItem.isSelected() ) {
        setEditorCard( CARD_TIME_RANGE_SELECTOR );
    }
}//GEN-LAST:event_timeRangeSelectorMenuItemActionPerformed

private void editOptionsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editOptionsActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    PropertyEditor edit= new PropertyEditor(applicationModel.dom.getOptions());
    edit.showDialog(this,"DOM Options",new ImageIcon(this.getClass().getResource("/resources/logo16.png")).getImage());
}//GEN-LAST:event_editOptionsActionPerformed

private void fixLayoutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fixLayoutMenuItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    Application dom0= (Application)dom.copy();
    FixLayoutPanel flp= new FixLayoutPanel();
    flp.setPreview(dom);
    if ( JOptionPane.OK_OPTION==
            JOptionPane.showConfirmDialog( this, flp, "Fix Layout Options",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE ) ) {
        dom.syncTo(dom0);
        final Map<String,String> options=flp.getOptions();
        Runnable run= () -> {
            org.autoplot.dom.DomOps.fixLayout(dom,options);
        };
        new Thread(run,"canvas layout").start();
    } else {
        dom.syncTo(dom0);
    }

}//GEN-LAST:event_fixLayoutMenuItemActionPerformed

private void resetXMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetXMenuItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    if ( !AutoplotUtil.resetZoomX(dom)) {
        System.err.println("unable to zoom x");
    }
}//GEN-LAST:event_resetXMenuItemActionPerformed

private void resetYMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetYMenuItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    if ( !AutoplotUtil.resetZoomY(dom)) {
        System.err.println("unable to zoom y");
    }
}//GEN-LAST:event_resetYMenuItemActionPerformed

private void resetZMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetZMenuItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    if ( !AutoplotUtil.resetZoomZ(dom)) {
        System.err.println("unable to zoom z");
    }
}//GEN-LAST:event_resetZMenuItemActionPerformed

private void replaceFileMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_replaceFileMenuItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    Runnable run= new Runnable() {
        @Override
        public void run() {
            AutoplotUtil.replaceFile( AutoplotUI.this,dom );
        }
    };
    RequestProcessor.invokeLater(run);
}//GEN-LAST:event_replaceFileMenuItemActionPerformed

private void reloadAllMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadAllMenuItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
   // Reload All Data
    RequestProcessor.invokeLater(new Runnable() {
        @Override
        public void run() {
            AutoplotUtil.reloadAll(dom);
        }
    } );
}//GEN-LAST:event_reloadAllMenuItemActionPerformed

private void workOfflineCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_workOfflineCheckBoxMenuItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    final boolean workOffline= workOfflineCheckBoxMenuItem.isSelected();
    FileSystem.settings().setOffline( workOffline );
    RequestProcessor.invokeLater(new Runnable() { 
        @Override
        public void run() {
            FileSystem.reset();
            setMessage( workOffline ? "Now working offline" : "Working online");
        }
    } );
}//GEN-LAST:event_workOfflineCheckBoxMenuItemActionPerformed

private void searchToolTipsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchToolTipsMenuItemActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    AutoplotUtil.doSearchToolTips(this);
}//GEN-LAST:event_searchToolTipsMenuItemActionPerformed

private void manageFilesystemsMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_manageFilesystemsMIActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    AutoplotUtil.doManageFilesystems(this);
}//GEN-LAST:event_manageFilesystemsMIActionPerformed

private void resetMemoryCachesMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetMemoryCachesMIActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);
    logger.fine("Resetting FileSystems...");
    Runnable run= new Runnable() {
        @Override
        public void run() {
           ReferenceCache.getInstance().reset(); // you just have to know this is what it is doing for now...
           FileSystem.reset();
           setMessage("FileSystem memory caches reset");
        }
    };
    RequestProcessor.invokeLater(run);
}//GEN-LAST:event_resetMemoryCachesMIActionPerformed

    private void referenceCacheCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_referenceCacheCheckBoxMenuItemActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        if ( referenceCacheCheckBoxMenuItem.isSelected() ) {
            System.setProperty( "enableReferenceCache", "true" );
            setMessage( "Reference Cache is enabled" );
        } else {
            ReferenceCache.getInstance().printStatus();
            ReferenceCache.getInstance().reset();
            DataSetAnnotations.getInstance().reset();
            System.setProperty( "enableReferenceCache", "false" );
            setMessage( "Reference Cache is disabled." );
        }
    }//GEN-LAST:event_referenceCacheCheckBoxMenuItemActionPerformed

    private void additionalOptionsMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_additionalOptionsMIActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        OptionsDialog p= new OptionsDialog();
        p.setOptions( applicationModel.dom.getOptions() );
        if ( AutoplotUtil.showConfirmDialog( this, p, "Additional Options", JOptionPane.OK_CANCEL_OPTION )==JOptionPane.OK_OPTION ) {
            p.copyOptions( applicationModel.dom.getOptions() );
        }
    }//GEN-LAST:event_additionalOptionsMIActionPerformed

    
    private void jMenuItem6ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem6ActionPerformed
        EventsListToolUtil.show( this );
    }//GEN-LAST:event_jMenuItem6ActionPerformed

    private void formMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseClicked
        Rectangle dssBounds= getDataSetSelector().getBounds();
        dssBounds= SwingUtilities.convertRectangle( getDataSetSelector(), dssBounds, this );
        if ( evt.getX() < dssBounds.x &&
                evt.getY() > dssBounds.y &&
                evt.getY() < dssBounds.y + dssBounds.height ) {
                        
            if ( evt.getY()< dssBounds.y + dssBounds.height/2 ) {
                setEditorCard( CARD_DATA_SET_SELECTOR );
            } else {
                setEditorCard( CARD_TIME_RANGE_SELECTOR );
            }
        }
    }//GEN-LAST:event_formMouseClicked

    private void filtersMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filtersMenuItemActionPerformed
        String filter= dom.getController().getPlotElement().getComponent();
        if ( filter.length()==0 ) {
            AddFilterDialog dia= new AddFilterDialog();
            if ( JOptionPane.OK_OPTION==AutoplotUtil.showConfirmDialog( this, dia, "Add Operation", JOptionPane.OK_CANCEL_OPTION ) ) {
                filter= dia.getValue();
            } else {
                return;
            }
        }
        FiltersChainPanel fcp= new FiltersChainPanel();
        fcp.setFilter(filter);
        DataSourceFilter dsf= dom.getController().getDataSourceFilterFor(dom.getController().getPlotElement());
        if ( dsf!=null ) {
            fcp.setInput(dsf.getController().getFillDataSet());
            fcp.setFilter(filter);
        }
        if ( JOptionPane.OK_OPTION==AutoplotUtil.showConfirmDialog( this, fcp, "Edit Operations", JOptionPane.OK_CANCEL_OPTION ) ) {
            dom.getController().getPlotElement().setComponent(fcp.getFilter());
        }
    }//GEN-LAST:event_filtersMenuItemActionPerformed

    private void resetFontMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetFontMIActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        applicationModel.resetFontSize();
    }//GEN-LAST:event_resetFontMIActionPerformed

    private void mashDataMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mashDataMenuItemActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        
        String uri= dataSetSelector.getValue();
        
        if ( uri.trim().length()>0 ) {
            URISplit split= URISplit.parse(uri);
            if ( split.vapScheme==null || !split.vapScheme.equals("vap+inline") ) {
                if ( "vap+internal".equals(split.vapScheme) ) {
                    String[] ss= split.path.split(",",-2);
                    StringBuilder urib= new StringBuilder( "vap+inline:" );
                    for (String s : ss) {
                        DomNode n;
                        String uri1;
                        try {
                            n= dom.getElementById(s);                            
                            if ( n instanceof DataSourceFilter ) {
                                DataSourceFilter dsf= (DataSourceFilter)n;
                                uri1= dsf.getUri();
                            } else {
                                uri1= "";
                            }
                        } catch ( IllegalArgumentException ex ) {
                            uri1= "";
                        }
                        urib.append(s).append("=getDataSet('").append(uri1).append("')&");
                    }
                    urib.append("link(").append(split.path).append(")");
                    uri= urib.toString();
                } else {
                    uri= "vap+inline:ds=getDataSet('"+uri+"')";
                }
            }
        }

        if ( uri.length()>0 ) {
            dataSetSelector.setValue(uri);
            dataSetSelector.maybePlot( KeyEvent.ALT_MASK );
        } else {
            final DataMashUp dm= new DataMashUp();
            dm.setResolver( new PlotDataMashupResolver(dm) );

            if ( JOptionPane.OK_OPTION==AutoplotUtil.showConfirmDialog( this, dm, "Data Mash Up", JOptionPane.OK_CANCEL_OPTION ) ) {
                dataSetSelector.setValue(dm.getAsJythonInline());
                dataSetSelector.maybePlot(0);
            }
        }
    }//GEN-LAST:event_mashDataMenuItemActionPerformed

    private void runBatchMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runBatchMenuItemActionPerformed
        RunBatchTool mmm= new RunBatchTool(dom);
        final JDialog dia= new JDialog( this, "Run Batch Tool" );
        dia.getRootPane().registerKeyboardAction((ActionEvent e) -> {
            org.das2.util.LoggerManager.logGuiEvent(e);
            dia.setVisible(false);
            dia.dispose();
        }, KeyStroke.getKeyStroke( KeyEvent.VK_ESCAPE, 0 ), JComponent.WHEN_IN_FOCUSED_WINDOW );       

        dia.setJMenuBar( mmm.getMenuBar() );
        dia.setContentPane(mmm);
        dia.pack();
        dia.setLocationRelativeTo(this);
        dia.setVisible(true);
    }//GEN-LAST:event_runBatchMenuItemActionPerformed

    private void resetAppSizeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetAppSizeActionPerformed
        ScriptContext.setCanvasSize( 724, 656 ); // this is the arbitrary size of the app when its size is now saved.
    }//GEN-LAST:event_resetAppSizeActionPerformed

    private void saveOptionsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveOptionsMenuItemActionPerformed
        dom.getOptions().setWidth( dom.getCanvases(0).getWidth() );
        dom.getOptions().setHeight( dom.getCanvases(0).getHeight() );
        dom.getOptions().getController().copyOptionsToPersistentPreferences();
    }//GEN-LAST:event_saveOptionsMenuItemActionPerformed

    private void drawGridCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_drawGridCheckBoxActionPerformed
        setMessage("Use Options->Save Options to make the change persist between sessions.");
    }//GEN-LAST:event_drawGridCheckBoxActionPerformed

    private void doyCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_doyCBActionPerformed
        setMessage("Use Options->Save Options to make the change persist between sessions.");
    }//GEN-LAST:event_doyCBActionPerformed

    private void nnCbActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nnCbActionPerformed
        setMessage("New spectrograms will be affected.  Use Options->Save Options to make the change persist between sessions.");
    }//GEN-LAST:event_nnCbActionPerformed

    
private transient PropertyChangeListener optionsListener= new PropertyChangeListener() {
    @Override
    public void propertyChange( PropertyChangeEvent ev ) {
        switch (ev.getPropertyName()) {
            case Options.PROP_LAYOUTVISIBLE: 
                makeLayoutVisible((Boolean)ev.getNewValue());
                break;
            case Options.PROP_DATAVISIBLE: 
                makeDataVisible((Boolean)ev.getNewValue());
                break;
            case Options.PROP_USE_TIME_RANGE_EDITOR:
                if ( Boolean.TRUE.equals(ev.getNewValue()) ) {
                    setEditorCard( CARD_TIME_RANGE_SELECTOR );
                } else {
                    setEditorCard( CARD_DATA_SET_SELECTOR );
                }
                break;
            default:
                logger.log(Level.FINER, "option requires no action: {0}", ev.getPropertyName());
        }
    }
};

private void makeDataVisible( final boolean newValue ) {
    Runnable run= new Runnable() {
        @Override
        public void run() {
            makeDataVisibleImmediately(newValue);
        }
    };
    if ( SwingUtilities.isEventDispatchThread() ) {
        run.run();
    } else {
        try {
            SwingUtilities.invokeAndWait(run);
        } catch (InterruptedException | InvocationTargetException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
}

private void makeDataVisibleImmediately( boolean newValue ) {
    if ( !SwingUtilities.isEventDispatchThread() ) {
        throw new IllegalArgumentException("should be run on the event thread");
    }
    if ( Boolean.TRUE.equals(newValue) ) {
        if ( dataPanel == null ) {
            dataPanel = new DataPanel( AutoplotUI.this );
        }
        int idx= tabs.indexOfTab("metadata");
        if ( idx==-1 ) idx=  tabs.getTabCount();
        JScrollPane jsp = new JScrollPane();
        jsp.setViewportView(dataPanel);
        tabs.insertTab("data", null, jsp,
                String.format( TAB_TOOLTIP_DATA, TABS_TOOLTIP ), idx );
        setStatus( "Use [menubar]->Options->Save Options to make data tab visible in future sessions.");
    } else {
        if ( dataPanel!=null ) {
            Component dataPanelComponent= dataPanel.getParent();
            if ( dataPanelComponent!=null ) dataPanelComponent= dataPanelComponent.getParent();
            if ( dataPanelComponent!=null ) {
                tabs.remove(dataPanel.getParent().getParent());
            }
        }
    }      
}

private void makeLayoutVisible( final boolean newValue ) {
    Runnable run= new Runnable() {
        @Override
        public void run() {
            makeLayoutVisibleImmediately(newValue);
        }
    };
    if ( SwingUtilities.isEventDispatchThread() ) {
        run.run();
    } else {
        try {
            SwingUtilities.invokeAndWait(run);
        } catch (InterruptedException | InvocationTargetException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
}

private void makeLayoutVisibleImmediately( boolean newValue ) {
    if ( Boolean.TRUE.equals(newValue) ) {
        if ( layoutPanel == null ) {
            layoutPanel = new LayoutPanel();
            layoutPanel.setApplication(dom);
            layoutPanel.setApplicationModel(applicationModel);                        
        }
        int idx= tabs.indexOfTab("style");
        if ( idx==-1 ) idx=  tabs.getTabCount();
        JScrollPane jsp = new JScrollPane();
        jsp.setViewportView(layoutPanel);
        tabs.insertTab("layout", null, jsp,
                String.format( TAB_TOOLTIP_LAYOUT, TABS_TOOLTIP ), idx+1 );
        setStatus( "Use [menubar]->Options->Save Options to make layout tab visible in future sessions.");
    } else {
        if ( layoutPanel!=null ) tabs.remove(layoutPanel.getParent().getParent());
    }    
}

/**
 * return the processID (pid), or the fallback if the pid cannot be found.
 * @param fallback the string (null is okay) to return when the pid cannot be found.
 * @return the process id or the fallback provided by the caller.
 */
public static String getProcessId(final String fallback) {
    return AutoplotUtil.getProcessId(fallback);
}

private void updateFrameTitle() {
    final String suri= applicationModel.getVapFile();

    final String title0= "Autoplot "+apversion;
    final String isoffline= FileSystem.settings().isOffline() ? " (offline)" : "";

    final String server= rlistener==null ? "" : ( " (port="+rlistener.getPort()+")" );
    
    final String s32bit= AutoplotUtil.is32bit ? " (32bit)" : "";
    
    final String theTitle;
    
    String apname= this.applicationName.length()==0 ? "" : this.applicationName + " - ";
    
    if ( suri==null ) {
        theTitle= apname + title0 + isoffline + server + s32bit + AutoplotUtil.javaVersionWarning;
    } else {
        URISplit split= URISplit.parse(suri);

        boolean dirty= undoRedoSupport.getDepth()>1;
        if ( split.path!=null && split.file!=null ) {
            String titleStr= split.file.substring( split.path.length() ) + ( dirty ? "*" : "" );
            theTitle= apname + titleStr + " - " + title0 + isoffline + server+ s32bit;
        } else {
            //I was seeing null pointer exceptions here--see rte_1590234331_20110328_153705_wsk.xml.  I suspect this is Windows.
            logger.log(Level.WARNING, "Unable to get path from: {0}", suri);
            theTitle= apname + "???" + " - " + title0 + isoffline + server+ s32bit;
        }
    }    
    Runnable run= new Runnable() {
        @Override
        public void run() {
            setTitle( theTitle );
        }
    };
    SwingUtilities.invokeLater(run);
}

    /**
     * raise the application window
     * http://stackoverflow.com/questions/309023/howto-bring-a-java-window-to-the-front
     * @param frame the frame
     */
    public static void raiseApplicationWindow( java.awt.Frame frame ) {
        GuiSupport.raiseApplicationWindow(frame);
    }

    /**
     * create a new application.  This is a convenience method for scripts.
     * @return 
     */
    public AutoplotUI newApplication() {
        ApplicationModel model= support.newApplication();
        return (AutoplotUI)model.application.getMainFrame();
    }
    
    /**
     * add a listener to the webstart interface so that there is only one running Autoplot at a time.  This
     * registers a SingleInstanceListener with webstart, which will prompt the user to add a new plot or to
     * replace the current one.
     * @param alm
     * @param model
     */
    private static void addSingleInstanceListener(final ArgumentList alm, final AutoplotUI app ) {
        javax.jnlp.SingleInstanceService sis;
        try {
            sis = (javax.jnlp.SingleInstanceService) javax.jnlp.ServiceManager.lookup( "javax.jnlp.SingleInstanceService" );
        } catch (javax.jnlp.UnavailableServiceException ex) {
            sis = null;
        }

        if ( sis==null ) {
            logger.fine("not running with webstart");
            return;
        }

        final SingleInstanceListener sisL = new SingleInstanceListener() {

            @Override
            public void newActivation(String[] argv) {
                if ( logger.isLoggable(Level.FINE) ) {
                    logger.fine("single instance listener argv:" );
                    for ( int i=0; i<argv.length; i++ ) {
                        logger.log(Level.FINE, " argv[{0}]: {1}\n", new Object[]{i, argv[i]});
                    }
                }

                for ( int i=0; i<argv.length; i++ ) {  // kludge for java webstart, which uses "-open" not "--open"
                   if ( argv[i].equals("-print") ) argv[i]="--print";
                   if ( argv[i].equals("-open") ) argv[i]="--open";
                }

                if ( !alm.process(argv) ) {
                    System.exit( alm.getExitCode() );
                }

                final JFrame frame = (JFrame) ScriptContext.getViewWindow();
                if ( frame!=null ) {
                     raiseApplicationWindow(frame);
                }

                String suri;
                if (alm.getValue("URI") != null) {
                    suri = alm.getValue("URI").trim();
                } else if ( alm.getValue("open") !=null ) {
                    suri = alm.getValue("open").trim();
                } else {
                    suri = null;
                }
                
                if ( suri!=null && suri.length()>1 ) {
                    logger.log(Level.FINE, "setting initial URI to >>>{0}<<<", suri );
                }

                String pos= alm.getValue("position");
                app.handleSingleInstanceURI(suri, pos);

            }
        };
        sis.addSingleInstanceListener(sisL);
    }

    /**
     * extract the code that handles the single instance so that we can model it for debugging.
     * @param suri the reentry URI 
     * @param pos support the --position=3 switch to support servers.
     */
    public void handleSingleInstanceURI( String suri, String pos ) {
        final AutoplotUI app= this; // refactor from static class. TODO: remove this is unnecessary...
        boolean raise=false;
        
        if ( suri!=null && ( suri.startsWith("pngwalk:") || suri.endsWith(".pngwalk") || suri.contains(".pngwalk?") ) ) {
            //TODO: check other prefixes...
            PngWalkTool.start( suri, app );
            app.applicationModel.addRecent(app.dataSetSelector.getValue());
            return;
        }
        

        if ( suri!=null && app.dataSetSelector.hasActionTrigger(suri) ) {
            app.dataSetSelector.setValue(suri);
            app.dataSetSelector.maybePlot(false); // allow for completions
            return;
        }        
 
        if ( suri!=null && suri.length()>1 ) { // check for relative filenames 
            try {
                suri= URISplit.makeAbsolute( new File(".").getCanonicalPath(), suri );
            } catch ( IOException ex ) {
                throw new RuntimeException(ex);
            }
        }

        if ( pos!=null ) {
            app.applicationModel.setFocus( Integer.parseInt(pos) );
            if ( suri!=null ) app.dataSetSelector.setValue(suri);
            app.dataSetSelector.maybePlot(false); // allow for completions

        } else {
            if (suri == null) {
                int action = JOptionPane.showConfirmDialog(ScriptContext.getViewWindow(), "<html>Autoplot is already running.<br>Start another window?", "Reenter Autoplot", JOptionPane.YES_NO_OPTION);
                if (action == JOptionPane.YES_OPTION) {
                    app.support.newApplication();
                } else {
                    raise= true;
                }
            } else {
                String msg;
                String ssuri= suri;
                if ( ssuri.length()>80 ) {
                    ssuri= DataSetURI.abbreviateForHumanComsumption( ssuri, 80 );
                }
                if ( app.isExpertMode() ) {
                        msg= String.format(
                        "<html>Autoplot is already running. Autoplot can use this address in a new window, <br>"
                        + "or replace the current plot with the new URI, possibly entering the editor, <br>"
                        + "or always enter the editor to inspect and insert the plot below.<br>"
                        + "View in new window, replace, or add plot, using<br>%s?", ssuri );
                } else {
                        msg= String.format(
                        "<html>Autoplot is already running. Autoplot can use this address in a new window, <br>"
                        + "or replace the current plot with the new URI, possibly entering the editor, <br>"
                        + "or always enter the editor to inspect before plotting.<br>"
                        + "View in new window, replace, or add plot, using<br>%s?", ssuri );
                }
                String action = (String) JOptionPane.showInputDialog( ScriptContext.getViewWindow(),
                        msg,
                        "Incorporate New URI", JOptionPane.QUESTION_MESSAGE, new javax.swing.ImageIcon(getClass().getResource("/resources/logo64.png")),
                        new String[] { "New Window", "Replace", "Add Plot" }, "Add Plot" );
                if ( action!=null ) {
                    switch (action) {
                        case "Replace":
                            app.plotUri(suri);
                            raise= true;
                            break;
                        case "Add Plot":
                            app.dataSetSelector.setValue(suri);
                            app.dataSetSelector.maybePlot( KeyEvent.ALT_MASK ); // enter the editor
                            raise= true;
                            break;
                        case "New Window":
                            AutoplotUI ui2= app.newApplication();
                            ui2.plotUri(suri);
                            break;
                        default:
                            throw new IllegalArgumentException("One of [New Window, Replace,  Add Plot] expected: " + action );
                    }
                } else {
                    raise= true;
                }
            }
        }
        if ( raise ) {
            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    raiseApplicationWindow(app);
                }
            });
        }

    }

    private static void runScriptImmediately( AutoplotUI app, 
            ApplicationModel model, String script, 
            List<String> scriptArgs, boolean quit, 
            String testPngFilename ) {
        try {
            String pwd= URISplit.parse(script).path;
            JythonUtil.runScript( model, script, scriptArgs.toArray(new String[scriptArgs.size()]), pwd );

            if ( testPngFilename!=null && testPngFilename.length()>0 ) {
                logger.log(Level.FINE, "Writing to {0}", testPngFilename);
                Logger.getLogger("autoplot.scriptcontext.writeToPng").setLevel(Level.FINER);
                Logger.getLogger("autoplot.scriptcontext.writeToPng").fine("Logging at FINE");
                ScriptContext.writeToPng(testPngFilename);
            }

            if ( app!=null ) app.setStatus( READY_MESSAGE );
            if ( quit ) { 
                if ( app!=null && app.logConsole!=null ) app.logConsole.undoLogConsoleMessages();
                AppManager.getInstance().quit();
            }
        } catch ( IOException ex ) {
            if ( quit ) {
                logger.log( Level.WARNING, ex.getMessage(), ex ); 
                System.err.println( ex.getMessage() );
                AppManager.getInstance().quit(1);
            } else {
                model.getExceptionHandler().handle(ex);
            }
        } catch ( RuntimeException ex ) {
            if ( quit ) {
                logger.log( Level.WARNING, ex.getMessage(), ex ); 
                System.err.println( ex.getMessage() );
                System.err.println("isAllowExit: "+AppManager.getInstance().isAllowExit());
                AppManager.getInstance().quit(16);
            } else {
                model.getExceptionHandler().handle(ex);
            }
        }       
    }
    
    /**
     * get the runnable for the script.
     * @param app the application UI, if not headless.
     * @param model the application model containing the dom.
     * @param script the name of the script, which can be relative to PWD.
     * @param scriptArgs arguments passed to the script, each is name=value.
     * @param quit if true then quit this application.
     * @param outputPngName if non-null, then write canvas to this png name.
     * @return the runnable.
     */
    private static Runnable getRunScriptRunnable( final AutoplotUI app, 
            final ApplicationModel model, final String script, 
            final List<String> scriptArgs, final boolean quit, 
            final String testPngFilename ) {
        Runnable r= new Runnable() {
            @Override
            public String toString() { return "runScriptRunnable"; }
            @Override
            public void run() {
                runScriptImmediately( app, model, script, scriptArgs, quit, testPngFilename );
            }
        };
        return r;
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main( String args[] ) {

        //http://today.java.net/pub/a/today/2007/08/30/debugging-swing.html
        //http://today.java.net/today/2007/08/30/tracing.zip
        //Toolkit.getDefaultToolkit().getSystemEventQueue().push(new org.pushingpixels.tracing.TracingEventQueueJMX());
        //Toolkit.getDefaultToolkit().getSystemEventQueue().push(new org.pushingpixels.tracing.TracingEventQueue());

        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
            @Override
            public void eventDispatched(AWTEvent event) {
                if ( event instanceof ActionEvent ) {
                    LoggerManager.logGuiEvent((ActionEvent)event);
                }
            }
        }, AWTEvent.KEY_EVENT_MASK );
        
        //if ( Runtime.getRuntime().totalMemory() < 256E6 ) { // 
        //    throw new IllegalArgumentException("Autoplot needs at least 256MB of memory to run.");
        //}
        
        //I get a message on the stdout and sometimes as a popup containing:
        //   (at java.util.TimSort.mergeHi(TimSort.java:895))
        //http://stackoverflow.com/questions/13575224/comparison-method-violates-its-general-contract-timsort-and-gridlayout 
        //https://sourceforge.net/p/autoplot/bugs/1159/
        System.setProperty("java.util.Arrays.useLegacyMergeSort", "true"); // see also jnlp which sets the property.
        
        Util.addFonts();
                
        { // read in the file $HOME/autoplot_data/config/logging.properties, if it exists.
            File f1= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ), "config" );
            File f2= new File( f1, "logging.properties" );
            if ( f2.exists() ) {
                if ( !f2.canRead() ) logger.log(Level.WARNING, "Unable to read {0}", f2);
                InputStream in=null;
                try {
                    logger.log(Level.FINE, "Reading {0}", f2);
                    in= new FileInputStream(f2);
                    LogManager.getLogManager().readConfiguration(in);
                } catch ( IOException ex ) {
                    logger.log(Level.WARNING, "IOException during read of {0}", f2);
                } finally {
                    try {
                        if ( in!=null ) in.close();
                    } catch ( IOException ex ) {
                        logger.log(Level.WARNING, "IOException during close of {0}", f2);
                    }
                }                
            }
        }
        
        // A Mar 11, 2014 email in Jeremy's inbox describes why this is needed for secure jnlp files.
        if ( System.getProperty( "autoplot.default.bookmarks" )==null ) {
            String s= System.getProperty( "jnlp.autoplot.default.bookmarks" );
            if ( s!=null ) {
                System.setProperty( "autoplot.default.bookmarks", s );
            }
        }
        if ( System.getProperty( "java.util.logging.config.file" )==null ) {
            String s= System.getProperty( "jnlp.java.util.logging.config.file" );
            if ( s!=null ) {
                System.setProperty( "java.util.logging.config.file", s );
            }
        }        
        
        
        if ( logger.isLoggable( Level.FINE ) ) {
            logger.fine("==arguments==");
            for ( int i=0; i<args.length; i++ ) {
                logger.log(Level.FINE, "arg{0}: {1}", new Object[]{i, args[i]});
            }
            logger.fine("==end,arguments==");
        }
        
        final ArgumentList alm = new ArgumentList("AutoplotUI");
        alm.addOptionalPositionArgument(0, "URI", null, "initial URI to load");
        alm.addOptionalSwitchArgument("position", null, "position", null, "plot position for the URI, an integer indicating which data position to update.");
        alm.addOptionalSwitchArgument("bookmarks", null, "bookmarks", null, "bookmarks to load");
        alm.addOptionalSwitchArgument("port", "p", "port", "-1", "enable scripting via this port (deprecated, use server instead)");
        alm.addBooleanSwitchArgument("scriptPanel", null, "scriptPanel", "enable script panel");
        alm.addBooleanSwitchArgument("logConsole", "l", "logConsole", "enable log console");
        alm.addOptionalSwitchArgument("nativeLAF", "n", "nativeLAF", ArgumentList.TRUE, "use the system look and feel (T or F)");
        alm.addOptionalSwitchArgument("flatLAF", null, "flatLAF", ArgumentList.FALSE, "use the OS-independent Flat look and feel (T or F)");
        alm.addOptionalSwitchArgument("macUseScreenMenuBar",null,"macUseScreenMenuBar",ArgumentList.FALSE, "use Mac menu bar (T or F)");
        alm.addOptionalSwitchArgument("open", "o", "open", null, "open this URI (to support javaws)");
        alm.addOptionalSwitchArgument("print", null, "print", "", "print this URI (to support javaws)");
        alm.addOptionalSwitchArgument("script", "s", "script", "", "run this script after starting.  " +
                "Arguments following are " +
                "passed into the script as sys.argv");
        alm.addBooleanSwitchArgument( "scriptExit",null,"scriptExit","force exit after running the script, setting exit status to non-zero for exception");
        alm.addOptionalSwitchArgument("testPngFilename", null, "testPngFilename", "", "write canvas to this png file after script is run" );
        alm.addOptionalSwitchArgument("outputFile", null, "outputFile", "", "Write canvas to png or pdf output file" );
        alm.addOptionalSwitchArgument("runBatch", "", "runBatch", "", "Run the Run Batch Tool .batch and exit.");
        
        alm.addOptionalSwitchArgument("autoLayout",null,"autoLayout",ArgumentList.TRUE,"turn on/off initial autolayout setting");
        alm.addOptionalSwitchArgument("mode","m","mode","expert","start in basic (browse,reduced) mode or expert mode" );
        //alm.addOptionalSwitchArgument("exit", null, "exit", "0", "exit after running script" );
        alm.addBooleanSwitchArgument( "enableResponseMonitor", null, "enableResponseMonitor", "monitor the event thread for long unresponsive pauses");
        alm.addBooleanSwitchArgument( "samp", null, "samp", "enable SAMP connection for use with European Space Agency applications and websites");
        alm.addOptionalSwitchArgument( "server", null, "server", "-1", "start server at the given port listening to commands. (Replaces port)");
        alm.addBooleanSwitchArgument( "nop", null, "nop", "no operation, to be a place holder for jnlp script.");
        alm.addBooleanSwitchArgument( "headless", null, "headless", "run in headless mode" );
        alm.addBooleanSwitchArgument( "noAskParams", null, "noAskParams", "don't ask for parameters when running a script");
        alm.addBooleanSwitchArgument( "sandbox", null, "sandbox", "enable sandbox, which limits which disks are used." );
        alm.addBooleanSwitchArgument( "version", null, "version", "print the version" );
        
       for ( int i=0; i<args.length; i++ ) {  // kludge for java webstart, which uses "-open" not "--open"
           if ( args[i].equals("-print") ) args[i]="--print";
           if ( args[i].equals("-open") ) {
               args[i]="--open";
               if ( args.length>i+1 && args[i+1].length()<3 ) { // Linux/Mint launcher passes in %U when there is no file argument.
                   logger.fine("ignoring -open argument with less than three character URI.");
                   args[i+1]= "";
               }
           }
        }

        final List<String> scriptArgs= new ArrayList();

        for ( int i=1; i<args.length; i++ ) { // grab any arguments after --script and hide them from the processor.
            if ( args.length>i && ( args[i-1].startsWith("--script") || args[i-1].equals("-s") ) ) {
                List<String> apArgs= new ArrayList();
                if ( args[i-1].length()>8 && args[i-1].charAt(8)=='=' ) {
                    for ( int j=0; j<i; j++ ) {
                        apArgs.add(args[j]);
                    }
                    for ( int j=i; j<args.length; j++ ) {
                        if ( args[j].startsWith("--testPngFilename") ) throw new IllegalArgumentException("--testPngFilename needs to come before --script");
                        scriptArgs.add(args[j]);
                    }        
                } else {
                    for ( int j=0; j<=i; j++ ) {
                        apArgs.add(args[j]);
                    }        
                    for ( int j=i+1; j<args.length; j++ ) {
                        if ( args[j].startsWith("--testPngFilename") ) throw new IllegalArgumentException("--testPngFilename needs to come before --script");
                        scriptArgs.add(args[j]);
                    }
                }
                args= apArgs.toArray( new String[ apArgs.size() ] );
                break;
            }
        }
        
        final String[] fargs= args;
        
        if ( !alm.process(args) ) {
            System.exit( alm.getExitCode() );
        }

        String tag;
        try {
            tag = AboutUtil.getReleaseTag(APSplash.class);            
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
            tag= "???";
        }
        
        if ( alm.getBooleanValue("version") ) {    
            System.err.println("Autoplot version "+tag );
            return;
        }
                
        if ( alm.getBooleanValue("sandbox") ) {
            logger.warning("sandbox is still experimental and may be further restricted.");
            Sandbox.enterSandbox();
        }
        
        if ( alm.getBooleanValue("headless") ) {
            System.setProperty("java.awt.headless","true");
        }
        final boolean headless= "true".equals( System.getProperty("java.awt.headless") ) ;
        
        AutoplotUtil.maybeLoadSystemProperties();
        AutoplotUtil.maybeInitializeEditorColors();
                        
        String welcome= "welcome to autoplot";

        String pid= AutoplotUtil.getProcessId("???");
        if ( tag.equals("(dev)") ) {
            welcome+=" ("+tag.substring(1,4)+"-"+pid+")";
        } else {
            welcome+=" ("+tag+")";
        }
        System.setProperty("http.agent", "Autoplot-"+tag );

        System.err.println(welcome);
        logger.info(welcome);
        final ApplicationModel model = new ApplicationModel();
        
        if ( alm.getBooleanValue("sandbox") ) {
            model.setSandboxed(true);
        }
                
        String initialURL;
        final String bookmarks;
        
        if (alm.getValue("URI") != null) {
            initialURL = alm.getValue("URI").trim();
        } else if ( alm.getValue("open") !=null ) {
            initialURL = alm.getValue("open").trim();
        } else {
            initialURL = null;
        }
        
        // it's easy to forget the -- in --open=. Check for this and give a nice error.
        if ( initialURL!=null ) {
            if ( initialURL.startsWith("open=") ) {
                JOptionPane.showMessageDialog( null, "<html>open= switch is missing -- prefix: should be<br>--"+initialURL, "open= switch is missing -- ", JOptionPane.ERROR_MESSAGE );
            }
        }
        
        if ( initialURL!=null && initialURL.length()>1 ) { // check for relative filenames 
            int i= initialURL.indexOf(':');
            logger.log(Level.FINE, "setting initial URI to >>>{0}<<<", initialURL);
            if ( i==-1 || i>8 ) { // it's a file, no http:
                boolean isAbsolute= initialURL.startsWith("/") || initialURL.startsWith("\\") || ( initialURL.length()>2 && initialURL.charAt(1)==':' );
                if ( !isAbsolute ) {
                    try {
                        String pwd= new File(".").getCanonicalPath();
                        if ( pwd.length()>2 ) {
                            if ( "Windows".equals(System.getProperty("os.family")) ) {
                                pwd= pwd + "\\";
                                initialURL= pwd + initialURL;
                            } else {
                                initialURL = URISplit.makeAbsolute( pwd, initialURL );
                            }   
                        } else {
                            initialURL= pwd + initialURL;
                        }
                    } catch ( IOException ex ) {
                        logger.log( Level.WARNING, null, ex );
                    }
                }
            }
        }
        
        final String outputFile= alm.getValue("outputFile");
        if ( !outputFile.equals("") ) {
            if ( !outputFile.endsWith(".pdf") && !outputFile.endsWith(".png") ) {
                throw new IllegalArgumentException("outputFile must end with .png or .pdf");
            }
        }
        
        final String runBatch=alm.getValue("runBatch");
        if ( !runBatch.equals("") ) {
            System.err.println("runBatch is not supported.  Run the GUI and"
                    + " use the Run Batch GUI.");
        }
        
        final String finitialURL= initialURL;
        
        bookmarks= alm.getValue("bookmarks");

        if (alm.getBooleanValue("scriptPanel")) {
            logger.fine("enable scriptPanel");
            model.getDom().getOptions().setScriptVisible(true);
        }

        if (alm.getBooleanValue("logConsole")) {
            logger.fine("enable scriptPanel");
            model.getDom().getOptions().setLogConsoleVisible(true);
        }

        logger.fine("add shutdown hook");
        Runtime.getRuntime().addShutdownHook(getShutdownHook(model));

        boolean nativeLAF= alm.getBooleanValue("nativeLAF");
        if ( alm.getBooleanValue("macUseScreenMenuBar") ) {
            logger.fine("use Mac menu bar");
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            nativeLAF= true;
        }
        
        if ( alm.getBooleanValue("macUseScreenMenuBar") && System.getProperty("os.name").startsWith("Mac") ) {
            URL r= AutoplotUI.class.getResource("macMenuBar.jy");
            logger.log(Level.INFO, "running soon: {0}", r);
            setupMacMenuBarSoon();
        }
        
        if ( !headless ) {
            try {
                if ( System.getProperty("flatLAF","false").equals("true") ) {
                    UIManager.setLookAndFeel( new FlatLightLaf() );
                    UIManager.put( "TabbedPane.selectedBackground", Color.white );
                    UIManager.put( "ScrollBar.showButtons", true );
                        
                } else {
                                    
                    if ( alm.getBooleanValue("flatLAF")==true ) {
                        UIManager.setLookAndFeel( new FlatLightLaf() );
                        UIManager.put( "TabbedPane.selectedBackground", Color.white );
                        UIManager.put( "ScrollBar.showButtons", true );
                    } else if ( nativeLAF ) {
                        logger.fine("nativeLAF");
                        String s= javax.swing.UIManager.getSystemLookAndFeelClassName();
                        javax.swing.UIManager.setLookAndFeel(s);
                    }
                }
                
            } catch ( ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e ) {
                logger.log( Level.SEVERE, e.getMessage(), e );
            }
        }
        
        logger.fine("invokeLater()");

        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public String toString() {
                return "initAutoplotRunnable";
            }
            @Override
            public void run() {
                //long t0= System.currentTimeMillis();

                logger.fine("enter invokeLater");

                if ( ! headless ) {
                    logger.addHandler( APSplash.getInstance().getLogHandler() );
                    APSplash.showSplash();
                }
APSplash.checkTime("init -100");
                //TODO: it's strange that there are two places where this code is called.
                OptionsPrefsController opc= new OptionsPrefsController( model, model.dom.getOptions() );
                opc.loadPreferencesWithEvents();

                if ( !alm.getBooleanValue("autoLayout") ) { // Chris had a vap that autolayout was mucking with.
                   logger.fine("set autoLayout");
                    model.getDocumentModel().getOptions().setAutolayout(false);
                }

APSplash.checkTime("init -90");
                if ( ! headless ) {
                    APSplash.showSplash();
                }

                model.addDasPeersToApp();
APSplash.checkTime("init -80");
                // display the splash again, in case it didn't paint the first time
                if ( !headless ) {
                    APSplash.showSplash();
                }
APSplash.checkTime("init -70");
                final AutoplotUI app;
                if ( headless ) {
                    app= null;
                } else {
                    app= new AutoplotUI(model);

                    app.createDropTargetListener( app.dataSetSelector );

                    WindowManager.getInstance().recallWindowSizePosition(app);
                    //Preferences prefs= AutoplotSettings.settings().getPreferences( AutoplotUI.class );
                    //int posx= prefs.getInt( "locationx", app.getLocation().x );
                    //int posy= prefs.getInt( "locationy", app.getLocation().y );
                    //if ( posx!= app.getLocation().x || posy!=app.getLocation().y ) {
                    //    boolean scncheck= java.awt.Toolkit.getDefaultToolkit().getScreenSize().width==prefs.getInt( "locationscreenwidth", 0 );
                    //    if ( scncheck ) { // don't position if the screen size changes.
                    //        app.setLocation( posx, posy );
                    //    }
                    //}
APSplash.checkTime("init 200");
                    boolean addSingleInstanceListener= true;
                    if ( addSingleInstanceListener ) {
                        addSingleInstanceListener( alm, app );
                    }
                    if ( alm.getBooleanValue("samp") ) {
                        org.autoplot.AddSampListener.addSampListener( app );
                        app.setMessage("SAMP listener started");
                    }
                    app.noAskParams= alm.getBooleanValue("noAskParams");
                }
                
APSplash.checkTime("init 210");

                if ( !headless && finitialURL!=null && app!=null ) {
                    app.dom.getController().registerPendingChange( app, PENDING_CHANGE_INITIAL_URI );
                }

                final boolean port= !alm.getValue("port").equals("-1");
                if ( port ) {
                    System.err.println("port keyword is deprecated, use --server="+port+" instead");
                    if ( app==null ) {
                        throw new IllegalArgumentException("Server cannot be used in headless mode");
                    }
                    int iport;
                    iport = Integer.parseInt(alm.getValue("port"));
                    app.setupServer(iport, model);
                    model.getDocumentModel().getOptions().setServerEnabled(true);
                }
                
                final boolean server= !alm.getValue("server").equals("-1");
                if ( server ) {
                    if ( app==null ) {
                        throw new IllegalArgumentException("Server cannot be used in headless mode");
                    }
                    int iport;
                    iport = Integer.parseInt(alm.getValue("server"));
                    app.setupServer(iport, model);
                    model.getDocumentModel().getOptions().setServerEnabled(true);
                }
                                

                boolean doCatchUncaughtExceptions= true; // for debugging, this can be turned off.  Note the requestProcessor system also catches exceptions.
                if ( doCatchUncaughtExceptions ) {
                    Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                        @Override
                        public void uncaughtException(Thread t, Throwable e) {
    //                        logger.severe("runtime exception: " + e);
                            logger.log(Level.SEVERE, "runtime exception: " + e, e);
                            if ( app!=null ) { // headless mode
                                app.setStatus(ERROR_ICON,"caught exception: " + e.toString());
                            }
                            if (e instanceof InconvertibleUnitsException) {
                                // do nothing!!!  this is associated with the state change
                                return;
                            }
                            model.getExceptionHandler().handleUncaught(e);
                        }
                    });
                }

APSplash.checkTime("init 220");
                if ( !headless ) {
                    logger.fine("UI.setVisible(true)");
                    Runnable repaintRunnable= new Runnable() {
                        @Override
                        public String toString() {
                            return "repaintRunnable";
                        }
                        @Override                        
                        public void run() {
                            if ( app!=null ) {
                                app.applicationModel.canvas.repaint();
                                if ( finitialURL==null || ! ( finitialURL.startsWith("pngwalk:") 
                                        || finitialURL.endsWith(".pngwalk") 
                                        || finitialURL.contains(".pngwalk?" ) ) ) app.setVisible(true);
                            }
                            //if ( alm.getBooleanValue("eventThreadMonitor") ) new EventThreadResponseMonitor().start();
                        }
                    };
                    repaintRunnable.run();
                    //SwingUtilities.invokeLater(repaintRunnable);

                    if ( System.getProperty("enableResponseMonitor","false").equals("true")
                            || alm.getBooleanValue("enableResponseMonitor") ) {
                        EventThreadResponseMonitor emon= new EventThreadResponseMonitor();
                        if ( app!=null ) {
                            emon.addToMap( GuiExceptionHandler.UNDO_REDO_SUPPORT, app.undoRedoSupport );
                            emon.addToMap( GuiExceptionHandler.APP_MODEL, app.applicationModel );
                        }
                        emon.start();
                        app.responseMonitor= emon;
                    }
                    
                    logger.fine("UI is visible");
                    APSplash.hideSplash();
                    logger.removeHandler( APSplash.getInstance().getLogHandler() );

                    if ( alm.getValue("mode").equals("basic") ) {
                        if ( app!=null ) app.setExpertMode(false);
                    }

                }
APSplash.checkTime("init 230");
                boolean useInitialURL= false;
                if ( !headless && finitialURL!=null) {
                    if ( app!=null ) {
                        app.dataSetSelector.setValue(finitialURL);
                        app.dataSetSelector.maybePlot(false);
                        useInitialURL= true;
                    }
                }
                
                if ( bookmarks!=null ) {
                    if ( app!=null ) app.initialBookmarksUrl= bookmarks;
                }
                
                String script_= alm.getValue("script");
                
                if ( !useInitialURL ) {
                    if ( script_.equals("") && finitialURL!=null ) {
                        if ( finitialURL.startsWith("script:") ) {
                            script_= finitialURL.substring(7);
                        } else if ( finitialURL.endsWith(".jy") ) {
                            script_= finitialURL;
                        }
                    }
                }

                final String script= script_;
                
                if ( !script.equals("") ) {
                    if ( headless ) {
                        model.setExceptionHandler(new ExceptionHandler() {
                            @Override
                            public void handle(Throwable t) {
                                t.printStackTrace();
                            }

                            @Override
                            public void handleUncaught(Throwable t) {
                                t.printStackTrace();
                            }
                        } );
                        
                    }
                    String s= URISplit.makeAbsolute( new File(".").getAbsolutePath(), script );
                    
                    if ( app!=null ) app.setStatus("running script "+s);
                    
                    if ( scriptArgs.contains("--help") ) {
                        try {
                            printScriptUsage(fargs,s,scriptArgs,System.out);
                        } catch ( IOException ex ) {
                            System.err.println("Unable to retrieve script: "+s);
                        }
                        System.exit(-1);
                    }
                    boolean scriptExit= alm.getBooleanValue("scriptExit");
                    
                    ScriptContext2023 scriptContext= new ScriptContext2023();
                    model.dom.getController().setScriptContext( scriptContext );
                    
                    Runnable run= getRunScriptRunnable(app, 
                            model, 
                            s, 
                            scriptArgs, 
                            scriptExit || ( headless && !server ), 
                            alm.getValue("testPngFilename") );
                    new Thread(run,"batchRunScriptThread").start();
                    
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(AutoplotUI.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    
                } else {
APSplash.checkTime("init 240");
                    if ( app!=null ) app.setStatus( READY_MESSAGE );
                }
                
                if ( app!=null ) {
                    checkStatusLoop(app);
                }
                
//                Runnable resetRun= new Runnable() {
//                    @Override
//                    public void run() {
//                        app.resizeForDefaultCanvasSize();
//                    }
//                };
//                SwingUtilities.invokeLater(resetRun);
                
                if ( !headless && finitialURL!=null) {
                    if ( app!=null ) {
                        Runnable run = new Runnable() {
                            @Override
                            public void run() {
                                app.dom.getController().performingChange( app, PENDING_CHANGE_INITIAL_URI);
                                app.dom.getController().changePerformed( app, PENDING_CHANGE_INITIAL_URI);                
                            }
                        };
                        SwingUtilities.invokeLater(run);
                    }
                }
                
                if ( outputFile.length()>0 ) {
                    if ( headless ) {
                        Runnable run= new Runnable() {
                            public void run() {
                                if ( outputFile.length()>0 ) {
                                    ScriptContext.plot(finitialURL);
                                    ScriptContext.waitUntilIdle();
                                    Application domm= ScriptContext.getDocumentModel();
                                    String format= "";
                                    if ( outputFile.endsWith(".pdf") ) format= "pdf";
                                    if ( outputFile.endsWith(".png") ) format= "png";
                                    if ( format.equals("") ) throw new IllegalArgumentException("outputFile must end with .png or .pdf");
                                    int height= domm.getCanvases()[0].getHeight();
                                    int width=  domm.getCanvases()[0].getWidth();
                                    switch (format) {
                                        case "png":
                                            logger.log(Level.INFO, "write to {0}", outputFile);
                                            try {
                                                ScriptContext.writeToPng( outputFile, width, height, Collections.emptyMap() );
                                            } catch (IOException ex) {
                                                logger.log(Level.SEVERE, null, ex);
                                            }
                                            break;
                                        case "pdf":                                
                                            try {
                                                ScriptContext.writeToPdf( outputFile );
                                            } catch (IOException ex) {
                                                logger.log(Level.SEVERE, null, ex);
                                            }
                                            break;
                                    }
                                    System.exit(0);
                                }
                            }
                        };
                        new Thread(run).start();
                    } else {
                        throw new IllegalArgumentException("write to png or pdf must be called with --headless");
                    }
                }
                
            };

        } );
    }

    /**
     * print a usage document to the print stream.
     * @param args Autoplot's args
     * @param s the script URI
     * @param scriptArgs
     * @param out
     * @throws IOException 
     */
    private static void printScriptUsage( String[] args, String s, List<String> scriptArgs, PrintStream out) throws IOException {
        File f= DataSetURI.getFile(s,new NullProgressMonitor());
        String script= org.autoplot.jythonsupport.JythonUtil.readScript( new FileReader(f) );
        //List<org.autoplot.jythonsupport.JythonUtil.Param> parms= org.autoplot.jythonsupport.JythonUtil.getGetParams( script );
        org.autoplot.jythonsupport.JythonUtil.ScriptDescriptor sd= org.autoplot.jythonsupport.JythonUtil.describeScript(script,null);
        
        String label= sd.getLabel();
        if ( label.length()==0 ) {
            out.println("# "+f.getName());
        } else {
            out.println("# "+label);
        }
        if ( sd.getTitle().length()>0 ) {
            out.println( sd.getTitle() );
        }
        if ( sd.getDescription().length()>0 ) {
            out.println( sd.getDescription() );
        }
        out.println("Usage: <AUTOPLOT> " + Util.strjoin( Arrays.asList(args), " " ) + " [args]");
        for ( org.autoplot.jythonsupport.Param p: sd.getParams() ) {
            String l;
            Object deft;
            if ( p.deft.toString().trim().contains(" ") ) {
                deft= "'"+p.deft+"'";
            } else {
                deft= p.deft;
            }
            l= ""+p.name+"="+deft+"\t"+p.doc;
            if ( p.enums!=null ) {
                l= l + " (one of: "+ p.enums.toString()+ ")";
            }
            out.println( "  "+l );
        }
    }
    
    Icon currentIcon; // the current icon on the status bar
    String currentIconTooltip;  // the current tooltip on the status bar
    
    /**
     * update the current icon and tooltip text on the event thread.
     */
    private transient Runnable updateIconRunnable= new Runnable() {
        @Override
        public void run() {
            statusLabel.setToolTipText(currentIconTooltip);
            if ( statusLabel.getIcon()!=WARNING_ICON ) {
                statusLabel.setIcon(currentIcon);
            }
        }
    };
    
    /**
     * periodically scan the application for nodes that are busy, and indicate
     * with a busy swirl icon if the app is busy.  This now uses app.apbusy to
     * schedule the checks.
     *
     * @param app
     */
    protected static void checkStatusLoop(final AutoplotUI app) {
        //final long t0 = System.currentTimeMillis();

        TimerTask run = new TimerTask() {
            @Override
            public String toString() {
                return "apPendingChangesMonitor";
            }
            @Override
            public void run() {
                LinkedHashMap<Object, Object> changes = new LinkedHashMap();
                app.dom.getController().pendingChanges(changes); // TODO: there's a NullPointerException when this is run with --script.
                //dom.getController().getCanvas().getController().getDasCanvas().pendingChanges(changes);
                if (app.statusLabel.getIcon() == WARNING_ICON) {
                // wait for setMessage to clear this.
                } else {
                    if (!changes.isEmpty()) {
                        app.currentIcon = BUSY_ICON;
                        String chstr = "";
                        for (Entry<Object, Object> e : changes.entrySet()) {
                            String client = String.valueOf(e.getValue());
                            int ist = client.indexOf('(');
                            int ien = client.lastIndexOf(')');
                            if (ist != -1) {
                                client = client.substring(0, ist) + client.substring(ien + 1);
                            }
                            if (chstr.equals("")) {
                                chstr = "* " + e.getKey() + " (" + client + ")";
                            } else {
                                chstr = chstr + "\n" + "* " + e.getKey() + " (" + client + ")";
                            }
                        }
                        app.currentIconTooltip = chstr;
                    } else {
                        app.currentIcon = IDLE_ICON;
                        app.currentIconTooltip = null;
                        Window w= SwingUtilities.getWindowAncestor(app.dom.getCanvases(0).getController().getDasCanvas());
                        int windowExtraHeight = w.getHeight() - app.dom.getCanvases(0).getHeight();
                        int windowExtraWidth= w.getWidth() - app.dom.getCanvases(0).getWidth();        
                        if ( windowExtraWidth>=0 && windowExtraHeight>=0 ) {
                            app.windowExtraHeight= windowExtraHeight;
                            app.windowExtraWidth= windowExtraWidth;
                            resizeLogger.log(Level.FINER, "reset windowExtraWidth and windowExtraHeight to {0},{1}", new Object[]{app.windowExtraWidth, app.windowExtraHeight});
                        }                    
                    }
                }
                app.dom.getController().setPendingChangeCount( changes.size() );
                boolean update = false;
                if (app.currentIcon != app.statusLabel.getIcon()) {
                    update = true;
                }
                String currentStatusLabel = app.statusLabel.getToolTipText();
                if (app.currentIconTooltip != currentStatusLabel || (app.currentIconTooltip != null && !app.currentIconTooltip.equals(currentStatusLabel))) {
                    update = true;
                }
                if (update) {
                    //app.dom.getController().setPendingChangeCount( changes.size() );
                    try {
                        SwingUtilities.invokeAndWait(app.updateIconRunnable);
                    } catch (InterruptedException | InvocationTargetException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                }
                //System.err.println("apbusy "+(System.currentTimeMillis()-t0)/1000. );

            }
        };
        app.apbusy.schedule(run, 500, 200);
        
    }
    
    /**
     * return the extra pixels needed by the GUI for borders and address bar.
     * @return the extra pixels needed by the GUI for borders and address bar.
     */
    public int getWindowExtraWidth() {
        return windowExtraWidth;
    }
    
    /**
     * return the extra pixels needed by the GUI for borders and address bar.
     * @return the extra pixels needed by the GUI for borders and address bar.
     */
    public int getWindowExtraHeight() {
        return windowExtraHeight;
    }
    
    /**
     * add a drop listener so that URIs can be dropped on to plots.  This should be added to
     * plots as they are created.
     *
     * @param dataSetSelector
     */
    void createDropTargetListener(DataSetSelector dataSetSelector) {

        dropListener= new UriDropTargetListener( dataSetSelector, applicationModel ) ;

        DropTarget dropTarget = new DropTarget();
        dropTarget.setComponent(applicationModel.canvas);
        try {
            dropTarget.addDropTargetListener( dropListener );
        } catch (TooManyListenersException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
        applicationModel.getCanvas().setDropTarget(dropTarget);
        for ( DasCanvasComponent cc: applicationModel.getCanvas().getCanvasComponents() ) {
            if ( cc instanceof DasPlot ) { // we need to add to existing plots.
                DropTarget dropTarget1 = new DropTarget();
                dropTarget1.setComponent(cc);
                try {
                    dropTarget1.addDropTargetListener( dropListener );
                } catch (TooManyListenersException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
                cc.setDropTarget(dropTarget1);
            }
        }

        applicationModel.dom.getCanvases(0).getController().setDropTargetListener(dropListener);

    }

    
    /**
     * provide access to the dropTargetListener.  Presumably this was added for testing.
     * @return the dropListener.
     */
    public DropTargetListener getDropTargetListener() {
        return dropListener;
    }
    
    /**
     * provide access to the universal application model.
     * @return access to the universal application model.
     */
    public ApplicationModel getApplicationModel() {
        return applicationModel;
    }

    /**
     * initializes a SocketListener that accepts jython scripts that affect
     * the application state.  This implements the "--port" option.
     * @param port the port for the application, often 12345.
     * @param model the internal model.
     */
    private void setupServer(int port, final ApplicationModel model) {

        rlistener = new RequestListener();
        rlistener.setPort(port);
        final RequestHandler rhandler = new RequestHandler();

        rlistener.addPropertyChangeListener(RequestListener.PROP_REQUESTCOUNT, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                try {
                    if ( rlistener==null ) {
                        logger.log( Level.FINE, "the server is no longer listening");
                        return;
                    }
                    Socket socket= rlistener.getSocket();
                    if ( !socket.getInetAddress().isLoopbackAddress() ) {
                        logger.log(Level.FINE, "connection from {0}", socket);
                        socket.getOutputStream().write("\nConnections to Autoplot are only allowed from localhost\n\n".getBytes());
                        socket.close();
                    } else {
                        logger.log(Level.FINE, "connection from {0}", socket);
                        rhandler.handleRequest(socket.getInputStream(), model, socket.getOutputStream(), rlistener);
                        org.das2.util.LoggerManager.getLogger("autoplot.server").log(Level.INFO, "disconnect @ {0}", new Date( System.currentTimeMillis() ));
                        serverCheckBoxMenuItem.setSelected(false);
                        if ( rlistener!=null && !rlistener.isListening() ) {
                            stopServer();
                        }
                    }
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
        });
        rlistener.addPropertyChangeListener(RequestListener.PROP_PORT, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                updateFrameTitle();
            }
        } );
        rlistener.startListening();
        serverCheckBoxMenuItem.setSelected(true);
        serverCheckBoxMenuItem.setToolTipText("server listening on port "+port);
        updateFrameTitle();
    }

    /**
     * set the message in the lower left corner of the application.
     * @param message the message to display
     */
    public void setMessage(String message) {
        setMessage( IDLE_ICON, message );
    }
    
    /**
     * Set the message in the lower left corner of the application, possibly with the AutoplotUI.WARNING_ICON.  
     * Since 2013/09/18,e only WARNING_ICON is used now, and the busy status is set by checking the application controller 
     * nodes for locks.
     * @param icon the icon to display to the left of the message.  The icon is only used when it is WARNING_ICON.
     * @param message the message to display
     */
    public void setMessage( Icon icon, String message ) {
        if ( message==null ) message= "<null>"; 
        String myMess= message;
        //if ( myMess.length()>100 ) myMess= myMess.substring(0,100)+"...";
        myMess= myMess.replaceAll("\n","");

        final String fmyMess= myMess;
        final String fmessage= message;
        final Icon ficon= icon;

        Runnable run= new Runnable() {  //TODO: we should be a little careful here, we don't want to post thousands of runnables to the event thread.
            @Override
            public void run() {
                try {
                    if ( ficon==WARNING_ICON ) {
                        statusLabel.setIcon( ficon );
                    } else {
                        if ( statusLabel.getIcon()==WARNING_ICON ) {
                            statusLabel.setIcon(BUSY_ICON);
                        }
                    }
                    try {
                        statusTextField.setText(fmyMess);
                        statusTextField.setToolTipText(fmessage);
                    } catch ( ArrayIndexOutOfBoundsException e ) {
                        logger.log( Level.SEVERE, e.getMessage(), e ); // rte_0759798375_20121111_205149_*.xml
                    } catch ( java.lang.AssertionError e ) {
                        logger.log( Level.SEVERE, e.getMessage(), e ); // rte_1865701214_20230125_111810_*.xml
                    }                    
                } catch ( Exception e ) {
                    logger.log( Level.SEVERE, e.getMessage(), e ); // rte_0759798375_20121111_205149_*.xml
                }
            }
        };
        if ( SwingUtilities.isEventDispatchThread() ) {
            run.run();
        } else {
            SwingUtilities.invokeLater( run );
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem aboutAutoplotMenuItem;
    private javax.swing.JMenuItem aboutDas2MenuItem;
    private javax.swing.JMenu addSizeMenu;
    private javax.swing.JMenuItem additionalOptionsMI;
    private javax.swing.ButtonGroup addressBarButtonGroup;
    private javax.swing.JMenu addressBarMenu;
    private javax.swing.JSeparator aggSeparator;
    private javax.swing.JMenuItem aggregateMenuItem;
    private javax.swing.JCheckBoxMenuItem autoLabellingCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem autoLayoutCheckBoxMenuItem;
    private javax.swing.JMenu autoMenu;
    private javax.swing.JCheckBoxMenuItem autoRangingCheckBoxMenuItem;
    private javax.swing.JMenuItem autoplotHelpMenuItem;
    private javax.swing.JMenuItem autoplotHomepageButton;
    private javax.swing.JMenu bookmarksMenu;
    private javax.swing.JMenu cacheMenu;
    private javax.swing.JMenuItem canvasSizeMenuItem;
    private javax.swing.JMenuItem copyDataSetURLMenuItem;
    private javax.swing.JMenuItem copyImageMenuItem;
    private javax.swing.JMenuItem createPngWalkMenuItem;
    private javax.swing.JSeparator createPngWalkSeparator;
    private javax.swing.JCheckBoxMenuItem dataPanelCheckBoxMenuItem;
    protected org.autoplot.datasource.DataSetSelector dataSetSelector;
    private javax.swing.JRadioButtonMenuItem dataSetSelectorMenuItem;
    private javax.swing.JMenuItem decodeURLItem;
    private javax.swing.JCheckBoxMenuItem doyCB;
    private javax.swing.JCheckBoxMenuItem drawGridCheckBox;
    private javax.swing.JMenuItem editDomMenuItem;
    private javax.swing.JSeparator editDomSeparator;
    private javax.swing.JMenu editMenu;
    private javax.swing.JMenuItem editOptions;
    private javax.swing.JMenu enableFeatureMenu;
    private javax.swing.JMenuItem exceptionReport;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JMenuItem filtersMenuItem;
    private javax.swing.JMenuItem fixLayoutMenuItem;
    private javax.swing.JMenuItem fontsAndColorsMenuItem;
    private javax.swing.JMenuItem gettingStartedMenuItem;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JMenuItem inspectVapFileMenuItem;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JMenuItem jMenuItem2;
    private javax.swing.JMenuItem jMenuItem3;
    private javax.swing.JMenuItem jMenuItem4;
    private javax.swing.JMenuItem jMenuItem5;
    private javax.swing.JMenuItem jMenuItem6;
    private javax.swing.JMenuItem jMenuItem7;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JPopupMenu.Separator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JPopupMenu.Separator jSeparator4;
    private javax.swing.JCheckBoxMenuItem layoutPanelCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem logConsoleMenuItem;
    private javax.swing.JMenuItem manageFilesystemsMI;
    private javax.swing.JMenuItem mashDataMenuItem;
    private javax.swing.JCheckBoxMenuItem nnCb;
    private javax.swing.JMenu optionsMenu;
    private javax.swing.JMenuItem pasteDataSetURLMenuItem;
    private javax.swing.JMenu plotStyleMenu;
    private javax.swing.JMenuItem pngWalkMenuItem;
    private javax.swing.JMenuItem redoMenuItem;
    private javax.swing.JCheckBoxMenuItem referenceCacheCheckBoxMenuItem;
    private javax.swing.JMenuItem reloadAllMenuItem;
    private javax.swing.JMenu renderingOptionsMenu;
    private javax.swing.JMenuItem replaceFileMenuItem;
    private javax.swing.JMenuItem resetAppSize;
    private javax.swing.JMenuItem resetFontMI;
    private javax.swing.JMenuItem resetMemoryCachesMI;
    private javax.swing.JMenuItem resetXMenuItem;
    private javax.swing.JMenuItem resetYMenuItem;
    private javax.swing.JMenuItem resetZMenuItem;
    private javax.swing.JMenu resetZoomMenu;
    private javax.swing.JMenuItem resetZoomMenuItem;
    private javax.swing.JMenuItem runBatchMenuItem;
    private javax.swing.JMenuItem saveOptionsMenuItem;
    private javax.swing.JCheckBoxMenuItem scriptPanelMenuItem;
    private javax.swing.JMenuItem searchToolTipsMenuItem;
    private javax.swing.JCheckBoxMenuItem serverCheckBoxMenuItem;
    private javax.swing.JLabel statusLabel;
    private javax.swing.JTextField statusTextField;
    private javax.swing.JPanel tabbedPanelContainer;
    private javax.swing.JMenu textSizeMenu;
    private javax.swing.JPanel timeRangePanel;
    private javax.swing.JRadioButtonMenuItem timeRangeSelectorMenuItem;
    private javax.swing.JMenu toolsMenu;
    private javax.swing.JPopupMenu.Separator toolsUserSep;
    private javax.swing.JMenuItem undoMenuItem;
    private javax.swing.JMenu undoMultipleMenu;
    private org.autoplot.UriTimeRangeToggleButton uriTimeRangeToggleButton1;
    private javax.swing.JMenu viewMenu;
    private javax.swing.JCheckBoxMenuItem workOfflineCheckBoxMenuItem;
    private javax.swing.JMenuItem zoomInMenuItem;
    private javax.swing.JMenuItem zoomOutMenuItem;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration//GEN-END:variables

    /**
     * invoke the runnable after at least delayMillis.  If evt is true, then
     * put the runnable on the event thread after.
     * @param delayMillis -1 for default delay, 0 for none, or the positive number of milliseconds
     * @param evt if true, run on the event thread, otherwise use the timer thread.
     * @param run the runnable.
     */
    private void invokeLater( int delayMillis, final boolean evt, final Runnable run ) {
        TimerTask sleepRun= new TimerTask() {
            @Override      
            public void run() {
                //sleep(delayMillis);
                if ( evt ) {
                    SwingUtilities.invokeLater(run);
                } else {
                    run.run();
                }
            }
        };
        if ( delayMillis==-1 ) delayMillis= 500;
        //RequestProcessor.invokeLater(sleepRun);
        apbusy.schedule( sleepRun, delayMillis );
    }

    private void addTools() {
        TimerTask addToolsRun= new TimerTask() {
            @Override            
            public void run() {
                reloadTools();
            }
        };
        apbusy.schedule( addToolsRun, 500 );
    }

    /**
     * looks for and adds tools on a new thread.
     */
    public void reloadTools() {

        int isep=-1;
        // remove existing menu items for user tools.
        for ( int i=0; i<toolsMenu.getMenuComponentCount(); i++ ) {
            Component c= toolsMenu.getMenuComponent(i);
            if ( c instanceof JSeparator && "userSep".equals(c.getName()) ) {
                isep=i;
                break;
            }
        }

        if ( isep>-1 ) {
            for ( int i=toolsMenu.getMenuComponentCount()-1; i>isep; i-- ) {
                toolsMenu.remove( toolsMenu.getMenuComponent(i) );
            }
        }

        final List<Bookmark> tools= loadTools();
        if ( tools.size()>0 && isep==-1 ) {
            JSeparator userSep= new JSeparator();
            userSep.setName("userSep"); // so we can find it later
            toolsMenu.add( userSep );
        }

        Runnable run= new Runnable() {
            @Override
            public void run() {
                maybeCreateToolsManager();
                toolsManager.getModel().addPropertyChangeListener(BookmarksManagerModel.PROP_LIST, new PropertyChangeListener() {
                @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        SwingUtilities.invokeLater(new Runnable() { 
                            @Override
                            public void run() {
                                toolsManager.updateBookmarks( toolsMenu, "userSep", AutoplotUI.this, AutoplotUI.this.dataSetSelector );
                            }
                        } );
                    }
                });
                toolsManager.updateBookmarks( toolsMenu, "userSep", AutoplotUI.this, dataSetSelector ); 
            }
        };
        
        if ( SwingUtilities.isEventDispatchThread() ) {
            run.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(run);
            } catch (InterruptedException | InvocationTargetException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
        
    }

    /**
     * load the jython scripts in the users AUTOPLOT_DATA/tools directory.
     * @return a list of bookmarks, each with the tool's URI and title set to the #LABEL in the script.
     */
    private List<Bookmark> loadTools() {
        List<Bookmark> tools= new ArrayList();
        File toolsDir= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ), "tools" );
        File booksDir= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ), "bookmarks" );
        
        if ( !toolsDir.exists() ) {
            // don't import old tools if they don't exist.
            return Collections.emptyList();
        }
        
        if ( booksDir.exists() ) {
            File toolsFile= new File( booksDir, "tools.xml" );
            if ( toolsFile.exists() ) {
                try {
                    logger.fine("found tools.xml, use it instead of old logic.");
                    return Bookmark.parseBookmarks(toolsFile.toURI().toURL());
                } catch ( IOException | SAXException | BookmarksException ex ) {
                    
                }
            } else {
                File[] ff= toolsDir.listFiles();
                if ( ff==null ) {
                    logger.log(Level.WARNING, "unable to read tools folder: {0}", toolsDir);
                    ff= new File[0];
                }
                for (File ff1 : ff) {
                    if (ff1.getName().toLowerCase().endsWith(".jy")) {
                        Bookmark book = new Bookmark.Item(ff1.toURI().toString());
                        String toolLabel = ff1.getName();
                        // read header comments for label and description.
                        try {
                            try (BufferedReader reader = new BufferedReader(new FileReader(ff1))) {
                                String s = reader.readLine();
                                while (s != null) {
                                    if ( s.startsWith("#") ) {
                                        if ( s.startsWith("# label:" ) ) {
                                            toolLabel= s.substring(8).trim();
                                        } else if ( s.startsWith("# LABEL:" ) ) {
                                            toolLabel= s.substring(8).trim();
                                        } else if ( s.startsWith("#LABEL:" ) ) {
                                            toolLabel= s.substring(7).trim();
                                        }
                                    } else {
                                        break;
                                    }
                                    s = reader.readLine();
                                }
                            }
                        }catch (IOException ex) {
                            logger.log( Level.SEVERE, ex.getMessage(), ex );
                        }
                        book.setTitle(toolLabel);
                        tools.add(book);
                    }   
                }
                
                InputStream ins=null;
                try {
                    URL url = new URL("https://autoplot.org/data/tools.xml");
                    ins= url.openStream();
                    Document doc = AutoplotUtil.readDoc(ins);
                    List<Bookmark> importBook = Bookmark.parseBookmarks(doc.getDocumentElement());
                    tools.addAll(importBook);
                } catch ( IOException | SAXException | ParserConfigurationException | BookmarksException ex ) {
                    logger.log(Level.SEVERE,null,ex);
                } finally {
                    if ( ins!=null ) try {
                        ins.close();
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                }
            
                FileOutputStream fout=null;
                try {
                    fout= new FileOutputStream(toolsFile);
                    Bookmark.formatBooks( fout, tools);
                } catch (FileNotFoundException ex) {
                    throw new RuntimeException(ex);
                } finally {
                    if ( fout!=null ) {
                        try {
                            fout.close();
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                }
            }
        } else {
            if ( !toolsDir.mkdirs() ) {
                System.err.println("failed to make tools directory");
            }
            //File f= new File( toolsDir, "README.txt" );
            //BufferedWriter fw= new BufferedWriter( new FileWriter(f) );
            //fw.write("Scripts in this directory will appear under the tools menu.\n",0);
        }
        return tools;
    }
    
    /**
     * provide access to the undoRedoSupport.  Presumably this was added for testing.
     * @return the undoRedoSupport.
     */
    public UndoRedoSupport getUndoRedoSupport() {
        return this.undoRedoSupport;
    }

    /**
     * return the current state of this application window.  Note this is
     * the actual and not a copy, so it should not be modified.
     * @return the application state.
     * @see #getDom() 
     */
    public Application getDocumentModel() {
        return this.applicationModel.getDocumentModel();
    }
    
    /**
     * return the dom (application state) associated with this application.
     * @return the dom associated with this application.
     */
    public Application getDom() {
        return this.applicationModel.getDocumentModel();
    }
    
    /**
     * provide access to the dataSetSelector which browses and resets the plot URIs.
     * @return the dataSetSelector
     */
    public DataSetSelector getDataSetSelector() {
        return this.dataSetSelector;
    }

    /**
     * provide access to the timeRangeEditor which controls dom.timerange.
     * @return the timeRangeEditor
     */
    public TimeRangeEditor getTimeRangeEditor() {
        return this.timeRangeEditor;
    }
    
    /**
     * return the data panel (for testing).
     * @return the data panel.
     */
    public DataPanel getDataPanel() {
        return dataPanel;
    }

    JComponent leftPanel=null;
    
    /**
     * add the component (typically a JPanel) to the left
     * side of the application.
     * @param c null or the component to add
     * @see ScriptContext#addTab(java.lang.String, javax.swing.JComponent) 
     * @see #clearLeftPanel() 
     * @see #setRightPanel(javax.swing.JComponent) 
     * @see #setBottomPanel(javax.swing.JComponent) 
     */
    public void setLeftPanel( final JComponent c ) {
        if ( c==null ) throw new NullPointerException("use clearLeftPanel");
        Runnable run= new Runnable() {
            @Override
            public void run() {
                if ( leftPanel!=null ) tabbedPanelContainer.remove(leftPanel);
                JScrollPane p= new JScrollPane();
                p.setViewportView(c);
                tabbedPanelContainer.add( p, BorderLayout.WEST );
                leftPanel= p;
                revalidate();
            }
        };
        SwingUtilities.invokeLater(run);
    }

    /**
     * remove any extra component added to the left of the tabs.  This calls invokeLater to make sure
     * the event is on the event thread.
     * @see #setLeftPanel(javax.swing.JComponent) 
     */
    public void clearLeftPanel() {
        Runnable run= new Runnable() {
            @Override
            public void run() {
                if ( leftPanel!=null ) tabbedPanelContainer.remove(leftPanel);
                leftPanel= null;
                revalidate();
            }
        };
        SwingUtilities.invokeLater(run);
    }
    
    JComponent rightPanel= null;
    
    /**
     * add the component (typically a JPanel) to the right
     * side of the application.
     * @param c  null or the component to add
     * @see #setLeftPanel(javax.swing.JComponent) 
     */
    public void setRightPanel( final JComponent c ) {
        if ( c==null ) throw new NullPointerException("use clearRightPanel");
        Runnable run= new Runnable() {
            @Override
            public void run() {
                if ( rightPanel!=null ) tabbedPanelContainer.remove(rightPanel);
                JScrollPane p= new JScrollPane();
                p.setViewportView(c);
                tabbedPanelContainer.add( p, BorderLayout.EAST );
                rightPanel= p;
                revalidate();
            }
        };
        SwingUtilities.invokeLater(run);
    }

    /**
     * remove any extra component added to the right of the tabs.
     */
    public void clearRightPanel() {
        Runnable run= new Runnable() {
            @Override
            public void run() {
                if ( rightPanel!=null ) tabbedPanelContainer.remove(rightPanel);
                rightPanel= null;
                revalidate();
            } 
        };
        SwingUtilities.invokeLater(run);
    }
    
    JComponent bottomPanel= null;
    
    /**
     * add the component (typically a JPanel) below the tabs and above the 
     * status indicator
     * @param c  null or the component to add
     * @see #setLeftPanel(javax.swing.JComponent) 
     */
    public void setBottomPanel( final JComponent c ) {
        if ( c==null ) throw new NullPointerException("use clearBottomPanel");
        Runnable run= new Runnable() {
            @Override
            public void run() {
                if ( bottomPanel!=null ) tabbedPanelContainer.remove(bottomPanel);
                JScrollPane p= new JScrollPane();
                p.setViewportView(c);
                tabbedPanelContainer.add( p, BorderLayout.SOUTH );
                bottomPanel= p;
                revalidate();
            }
        };
        SwingUtilities.invokeLater(run);
    }

    /**
     * remove any extra component added below the tabs.
     */
    public void clearBottomPanel() {
        Runnable run= new Runnable() {
            @Override
            public void run() {
                if ( bottomPanel!=null ) tabbedPanelContainer.remove(bottomPanel);
                bottomPanel= null;
                revalidate();
            }
        };
        SwingUtilities.invokeLater(run);
    }
    
    /**
     * turn on basic mode, where users can only use the app for browsing existing products.
     */
    public void basicMode( ) {
        setExpertMode(false);
    }

    /**
     * set the application expert mode flag to restrict the app for browsing.
     * @param expert
     */
    public void setExpertMode( boolean expert ) {
        this.autoMenu.setVisible(expert);
        for ( JComponent mi: expertMenuItems ) {
            mi.setVisible(expert);
        }
        expertMenu.setText( expert ? "Expert" : "Basic" );
        dataSetSelector.setExpertMode(expert);
        if ( dataPanel!=null ) {
            dataPanel.setExpertMode(expert);
        }
        for ( Plot p: dom.getPlots() ) {
            p.getController().setExpertMode(expert);
        }
        if ( jythonScriptPanel!=null ) {
            if ( expert ) {
                tabs.add( TAB_SCRIPT, jythonScriptPanel );
            } else {
                tabs.remove( jythonScriptPanel );
            }
        }
        if ( logConsolePanel!=null ) {
            if ( expert ) {
                tabs.add( "console", logConsolePanel );
            } else {
                tabs.remove( logConsolePanel );
            }
        }
        if ( layoutPanel1!=null ) {
            if ( expert ) {
                tabs.add( "layout", layoutPanel1 );
            } else {
                tabs.remove( layoutPanel1 );
            }
        }
        dataSetSelector.setExpertMode(expert);
        if ( expert ) {
            addDataFromMenu.setText("Add Plot From");
        } else {
            addDataFromMenu.setText("Load Plot From");
        }
        
        final boolean fexpert= expert;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if ( !fexpert ) {
                    setEditorCard( CARD_TIME_RANGE_SELECTOR );
                } else {
                    setEditorCard( CARD_DATA_SET_SELECTOR );
                }
            }
        } );
        

    }

    /**
     * true if the GUI is in expert mode, showing more functions for users comfortable with the application.
     * @return true if the GUI is in expert mode
     */
    public boolean isExpertMode() {
        return expertMenuItems.get(0).isVisible()==true;
    }

    /**
     * true if the GUI is in basic mode, hiding functions for new users.  In basic mode users can only browse existing
     * products.
     * @return true if the GUI is in basic mode
     */
    public boolean isBasicMode() {
        return expertMenuItems.get(0).isVisible()==false;
    }

    /**
     * support legacy --bookmarks command line option.
     * @param bookmarks
     */
    private void loadInitialBookmarks( String bookmarks ) {
        try {
            final URL url = new URL(bookmarks);
            System.err.println("Reading bookmarks from "+url);
            Document doc = AutoplotUtil.readDoc(url.openStream());
            List<Bookmark> b= Bookmark.parseBookmarks(doc.getDocumentElement());  // findbugs DLS_DEAD_LOCAL_STORE fixed
            BookmarksManagerModel mm= bookmarksManager.getModel();
            List<Bookmark> l= mm.getList();
            mm.mergeList(b,l);
            mm.setList(l);
        } catch (IOException | SAXException | ParserConfigurationException | BookmarksException ex) {
            logger.log( Level.SEVERE, ex.getMessage(), ex );
            applicationModel.getExceptionHandler().handleUncaught(ex);
        }
    }

    /**
     * install the script into the tools folder.  This is run on a 
     * separate thread, so it will not be installed immediately.  The
     * tool script is read in to get the title and label.
     * @param ff
     * @param resourceUri 
     */
    protected void installTool( final File ff, URI resourceUri ) {
        try {
            String scriptUri= new URI( "script", resourceUri.toString(), null ).toString();
            final Bookmark b= toolsManager.addBookmark(scriptUri);
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    //TODO: there's a problem that the PWD has been lost.  Map<String,String> env= new HashMap<>();
                    try ( BufferedReader reader= new BufferedReader( new FileReader(ff) ) ) {
                        Map<String,String> doc= org.autoplot.jythonsupport.JythonUtil.getDocumentation( reader, resourceUri);
                        String title= doc.get( "TITLE" );
                        if ( title!=null ) b.setDescription(title); //TODO: bookmarks use inconsistent names... 
                        String label= doc.get( "LABEL" );
                        if ( label==null && title!=null && title.length()<40 ) label= title;
                        if ( label!=null ) b.setTitle(label);
                        String iconURl= doc.get("ICONURL");
                        if ( iconURl!=null ) {
                            try {
                                ImageIcon icon= new ImageIcon( new URL(iconURl) );
                                b.setIcon(icon);
                            } catch ( IOException ex ) {
                                logger.log( Level.WARNING, ex.getMessage(), ex );
                            }
                        }
                        Window w= ScriptContext.getViewWindow();
                        if ( w instanceof AutoplotUI ) {
                            ((AutoplotUI)w).reloadTools();
                        }
                    } catch ( IOException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                }
            };
            new Thread(run).start();
        } catch (URISyntaxException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
    
//    private void askRunScript( RunScriptPanel pp, final URI resourceUri, final File ff ) throws IOException {
//        int r = AutoplotUtil.showConfirmDialog(AutoplotUI.this, pp, "Load script", JOptionPane.OK_CANCEL_OPTION);
//        final boolean doCpTo;
//        if ( r==JOptionPane.OK_OPTION ) {
//            if ( pp.getToolsCB().isSelected() ) {
//                doCpTo= true;
//            } else {
//                doCpTo= false;
//            }
//            if ( scriptPanel!=null ) {
//                if ( ! scriptPanel.isDirty() ) {
//                    scriptPanel.loadFile(ff);
//                }
//            }
//            applicationModel.addRecent(dataSetSelector.getValue());
//            Runnable run= new Runnable() {
//                public void run() {
//                    if ( doCpTo  ) {
//                        installTool(ff,resourceUri);
//                    } else {
//
//                    }
//                    RunScriptPanel.runScript( applicationModel, ff, new DasProgressPanel("Running script "+ff ) );
//                }
//            };
//            new Thread(run,"runScript").start();
//        }
//
//    }
    
    /**
     * run the script, using the reference on the tools menu.  The security is going to be a bit different soon.
     * This should be called from the event thread because it creates GUI components.
     * @param script the URI of the script to run
     */
    public void runScriptTools( final String script ) {
        if ( ScriptContext.getViewWindow()==AutoplotUI.this ) {
            runScript( script );
        } else {
            //if ( JOptionPane.YES_OPTION==
            //        JOptionPane.showConfirmDialog( AutoplotUI.this, "Scripts can only be run from the main window.  Make this the main window?", 
            //                "Set Main Window", JOptionPane.YES_NO_OPTION ) ) {
                ScriptContext.setApplication(AutoplotUI.this);
            //}
            runScript( script );
        }
    }
        
    /**
     * present the "Run Script" dialog, asking the user to review the 
     * script before running it.
     * This should be called from the event thread because it creates GUI components.
     * @param script the URI of the script to run
     */
    private void runScript( final String script ) {
        runScript( script, true );
    }
    
    /**
     * present the "Run Script" dialog, asking the user to review the 
     * script before running it.
     * This should be called from the event thread because it creates GUI components.
     * @param script the URI of the script to run
     */
    private void runScript( final String script, final boolean askParams ) {
        try {
            final URISplit split= URISplit.parse(script);
            
            if ( split.path==null ) {
                JOptionPane.showMessageDialog( AutoplotUI.this, "Unable to run script because path is missing: "+script, "script missing path", JOptionPane.OK_OPTION );
                return;
            }
            //final File ff = DataSetURI.getFile(DataSetURI.getURI(script), DasProgressPanel.createFramed(AutoplotUI.this,"downloading script"));
            final RunScriptPanel pp = new RunScriptPanel();
            final HashMap params= URISplit.parseParams(split.params);
            pp.loadFileSoon(AutoplotUI.this,script);

            final DasProgressPanel mon= DasProgressPanel.createFramed(AutoplotUI.this,"Running script "+script );
            File tools= new File( AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA), "tools" );
                        
            boolean isTool= split.path.contains(tools.toString()); // here is the trust...
            Bookmark trust= toolsManager==null ? null : BookmarksManager.findBookmarkByUri( toolsManager.getModel().getList(), script, 1 );
            isTool = isTool || trust!=null;
            
            final boolean fisTool= isTool;
            
            mon.addPropertyChangeListener( DasProgressPanel.PROP_FINISHED, new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    Runnable run= new Runnable() {
                        @Override
                        public void run() {
                            if ( script.endsWith("createScreenShot.jy") ) {
                                logger.fine("kludge to avoid getting createScreenShot.jy in data set selector");
                            } else {
                                getDataSetSelector().setValue(script);
                            }
                        }
                    };
                    SwingUtilities.invokeLater(run);
                }
            });
            
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    try {
                        boolean doShowScript= !split.resourceUri.toString().endsWith("createScreenShot.jy");
                        JythonRunListener runListener= makeJythonRunListener( AutoplotUI.this, split.resourceUri, doShowScript );
                        int res= JythonUtil.invokeScriptSoon( split.resourceUri, dom, 
                                params, askParams, !fisTool, runListener, mon );
                        if ( res==JOptionPane.OK_OPTION ) {
                            split.params= params.isEmpty() ? null : URISplit.formatParams(params);
                            dom.getController().getApplicationModel().addRecent(URISplit.format(split));
                            //TODO: bug 1408: can we not somehow set the address bar URI here?
                        }
                        //askRunScript( pp, split.resourceUri, ff );
                    } catch ( IOException ex ) {
                        logger.log(Level.SEVERE, ex.getMessage(), ex);
                    }
                }
            };
            new Thread(run,"downloadReviewScript").start();
        } catch (HtmlResponseIOException ex ) {
            handleHtmlResponse(script, ex);
        } catch (IOException ex) {
            setMessage(WARNING_ICON,ex.getMessage());
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    /**
     * give the user a chance to review the bookmark before using it.
     * The user has selected the URI via the bookmarks and we will show it to
     * them before using it.
     * (rfe336)
     * @param uri the URI from the bookmarks or history.
     * @param modifiers key modifiers like KeyEvent.CTRL_MASK for plot below.
     */
    public void reviewBookmark( String uri, final int modifiers ) {
        final DataSetSelector sel= this.dataSetSelector;
        if ( uri.contains(".vap" ) ) {
            // ask the user if they want to use the .vap
            sel.setValue(uri);
            if ( JOptionPane.OK_OPTION==AutoplotUtil.showConfirmDialog( this, "Use vap file "+uri +"?", "Use Bookmarked .vap File", JOptionPane.OK_CANCEL_OPTION ) ) {
                sel.setValue(uri);
                sel.maybePlot(modifiers);
            }
        } else if ( uri.endsWith(".jy" ) || uri.contains(".jy?") ) {
            // scripts have a different dialog where they are accepted.
            sel.setValue(uri);
            sel.maybePlot(modifiers);
        } else {
            final String furi= uri;
            Runnable run= new Runnable() { 
                @Override
                public void run() {
                    // see if the uri would be rejected, and setEventsListURI the editor.
                    sel.setValue(furi);
                    DataSourceFactory factory=null;
                    try {
                        factory = DataSetURI.getDataSourceFactory( DataSetURI.getURI(furi), new NullProgressMonitor() );
                    } catch (IOException | IllegalArgumentException | URISyntaxException ex) {
                        Logger.getLogger(DelayMenu.class.getName()).log(Level.SEVERE, null, ex);
                    }

                    if ( factory==null ) {
                        logger.fine("unable to find factory when I expected to see uri");
                        sel.setValue(furi);
                        sel.maybePlot(modifiers); // have the user deal with the bad uri, and support plugins.
                    } else {
                        List<String> problems= new ArrayList();
                        if ( factory.reject( furi, problems, new NullProgressMonitor() )) {
                            sel.maybePlot( KeyEvent.ALT_MASK ); // this should enter the editor as before
                        } else {
                            Runnable run= new Runnable() {
                                @Override
                                public void run() {
                                    support.addPlotElementFromBookmark( "Add Bookmarked URI", furi ); 
                                }
                            };
                            SwingUtilities.invokeLater(run);
                            
                            //uri= DataSourceUtil.setTimeRange(uri,dom.getTimeRange(),mon);
                            //enterAddPlotElementDialog(); // fall back, make the user deal with bad uri
                        }

                    }
                }
            };
            new Thread( run ).start();
       }
    }
    
    /**
     * access tickle timer, which triggers when things change.  This will go away!
     * @return 
     */
    public TickleTimer getTickleTimer() {
        return this.tickleTimer;
    }
    
    /**
     * access the editor for scripts, if available.  This was initially added 
     * to provide a way to experiment with setting editor colors, but might be 
     * useful for other purposes.  
     * @return null or the editor panel
     * @see #getScriptPanel() which returns the panel
     */
    public EditorTextPane getScriptEditor() {
        if ( this.scriptPanel!=null ) {
            return this.scriptPanel.getEditorPanel();
        } else {
            return null;
        }
    }

    /**
     * Return the script editor panel.  Until v2020a_2 and 20200202a, this 
     * returned the EditorTextPane rather than the tab itself.  This is 
     * inconsistent with other calls.  For example:
     * <pre>
     * s= getApplication().getScriptPanel().getFilename() 
     * print( 'script editor filename:' )
     * print( s )
     * </pre>
     * @see #getScriptEditor() which returns the editor itself.
     * @return the editor panel in the script tab.
     */
    public JythonScriptPanel getScriptPanel() {
        return this.scriptPanel;
    }
    
//
//    /**
//     * temporary to debug https://sourceforge.net/p/autoplot/bugs/1520/
//     * @return 
//     */
//    public JLabel getStatusLabel() {
//        return statusLabel;
//    }
//    
//    /**
//     * temporary to debug https://sourceforge.net/p/autoplot/bugs/1520/
//     * @return 
//     */
//    public javax.swing.JTextField getStatusTextField() {
//        return statusTextField;
//    }

    private void addEditorCustomActions(final JythonScriptPanel scriptPanel) {
        JMenuItem mi= new JMenuItem( new AbstractAction("Editor Bookmarks") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);
                    BookmarksManager bm= new BookmarksManager(AutoplotUI.this,true,"editor");
                    bm.setPrefNode("editor");
                    bm.setVisible(true);
                    scriptPanel.doRebuildMenu();
                    addEditorCustomActions( scriptPanel );
                }
            } );
        mi.setToolTipText( "add scripts for editor actions" );

        scriptPanel.addSettingsMenuItem( mi );
        
        File fdir= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ), "bookmarks" );
        final File f= new File( fdir, "editor.xml" );
        if ( f.exists() ) {
            JMenu j= new JMenu("Custom Actions");
            Runnable run= new Runnable() {
                public void run() {
                try {
                List<Bookmark> bs= Bookmark.parseBookmarks(f.toURI().toURL());
                DelayMenu.calculateMenu(j, bs, (ActionEvent e) -> {
                    final String cmd= e.getActionCommand();
                    Runnable run= () -> {
                        try {
                            File f1 = DataSetURI.getFile( cmd, new NullProgressMonitor() );
                            final Map<String,Object> env= new HashMap<>();
                            URISplit split= URISplit.parse(cmd);
                            env.put( "PWD", split.path );
                            env.put( "dom", AutoplotUI.this.getDom() );
                            //ScriptContext.setApplication(AutoplotUI.this);
                            JythonUtil.invokeScriptNow(env, f1);
                        }catch (IOException ex) {
                            logger.log(Level.SEVERE, null, ex);
                        }
                    };
                    new Thread( run, "ScriptAction" ).start();
                });
                for ( Component c: j.getMenuComponents() ) {
                    if ( c instanceof JMenuItem ) {
                        scriptPanel.addMenuItem((JMenuItem)c);
                    }
                }
                                } catch (MalformedURLException ex) {
                Logger.getLogger(AutoplotUI.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException | SAXException | BookmarksException ex) {
                Logger.getLogger(AutoplotUI.class.getName()).log(Level.SEVERE, null, ex);
            }
                }
                };
                new Thread(run).start();

        }

    }
    
    /**
     * a reference to the app's JythonScriptPanel was leaking out and preventing the AutoplotServlet from being
     * compiled.  This hides the app stuff from JythonUtil.
     * @param app
     * @param uri
     * @param doShowScript
     * @return 
     */
    private static JythonRunListener makeJythonRunListener( AutoplotUI app, final URI uri, boolean doShowScript ) {
        JythonRunListener runListener= new JythonRunListener() {
            @Override
            public void runningScript(File file) {
                if ( app.scriptPanel==null ) return;
                if ( ! app.scriptPanel.isDirty() && doShowScript ) { // makeTool==false means it's already a tool
                    try {
                        if ( file!=null ) app.scriptPanel.loadFile(file);
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                }
                app.scriptPanel.setRunningScript(file);
            }

            @Override
            public void exceptionEncountered(File fn,PyException ex) {
                if ( app.scriptPanel==null ) return;
                try {
                    File file = DataSetURI.getFile( uri, new NullProgressMonitor() ); 
                    if ( file.equals( fn ) ) {
                        app.scriptPanel.getAnnotationsSupport().annotateError( ex, 0 );
                    }
                } catch (BadLocationException | IOException ex1) {
                    logger.log(Level.SEVERE, null, ex1);
                }
            }
            
        };
        return runListener;
    }
    
//    /**
//     * invoke the Jython script on another thread.  Script parameters can be passed in, and the scientist can be 
//     * provided a dialog to set the parameters.  Note this will return before the script is actually
//     * executed, and monitor should be used to detect that the script is finished.
//     * This should be called from the event thread!
//     * @param uri the resource URI of the script (without parameters).
//     * @param file the file which has been downloaded.
//     * @param dom if null, then null is passed into the script and the script must not use dom.
//     * @param params values for parameters, or null.
//     * @param askParams if true, query the scientist for parameter settings.
//     * @param makeTool if true, offer to put the script into the tools area for use later (only if askParams).
//     * @param scriptPanel null or place to mark error messages and to mark as running a script.
//     * @param mon1 monitor to detect when script is finished.  If null, then a NullProgressMonitor is created.
//     * @return JOptionPane.OK_OPTION of the script is invoked.
//     * @throws java.io.IOException
//     */
//    public static int invokeScriptSoon( 
//            final URI uri, 
//            final File file, 
//            final Application dom, 
//            Map<String,String> params, 
//            boolean askParams, 
//            final boolean makeTool, 
//            final JythonScriptPanel scriptPanel,
//            ProgressMonitor mon1) throws IOException {        
//        JythonRunListener runListener= makeJythonRunListener( app, uri, makeTool );
//                        
//        return JythonUtil.invokeScriptSoon( uri, file, dom, params, askParams, makeTool, runListener, mon1 );
//    }    
}
