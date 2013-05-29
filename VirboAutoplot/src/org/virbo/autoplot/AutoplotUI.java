/*
 * AutoplotUI.java
 *
 * Created on July 27, 2007, 6:32 AM
 */
package org.virbo.autoplot;

import java.awt.HeadlessException;
import java.awt.event.MouseEvent;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import javax.swing.Icon;
import org.virbo.autoplot.bookmarks.Bookmark;
import org.virbo.autoplot.bookmarks.BookmarksManager;
import com.cottagesystems.jdiskhog.JDiskHogPanel;
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
import java.awt.Dimension;
import java.awt.Event;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetListener;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TooManyListenersException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
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
import javax.swing.text.DefaultEditorKit;
import org.autoplot.help.AutoplotHelpSystem;
import org.autoplot.pngwalk.CreatePngWalk;
import org.autoplot.pngwalk.PngWalkTool1;
import org.das2.DasApplication;
import org.das2.components.propertyeditor.PropertyEditor;
import org.das2.datum.Datum;
import org.das2.datum.TimeParser;
import org.das2.datum.Units;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasCanvasComponent;
import org.das2.graph.DasPlot;
import org.das2.system.RequestProcessor;
import org.das2.util.ExceptionHandler;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.FileSystemSettings;
import org.das2.util.filesystem.KeyChain;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.virbo.autoplot.bookmarks.BookmarksManagerModel;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.autoplot.dom.BindingModel;
import org.virbo.autoplot.dom.DataSourceFilter;
import org.virbo.autoplot.dom.PlotElement;
import org.virbo.autoplot.scriptconsole.JythonScriptPanel;
import org.virbo.autoplot.scriptconsole.LogConsole;
import org.virbo.autoplot.server.RequestHandler;
import org.virbo.autoplot.server.RequestListener;
import org.virbo.autoplot.dom.Options;
import org.virbo.autoplot.dom.OptionsPrefsController;
import org.virbo.autoplot.dom.Plot;
import org.virbo.autoplot.dom.PlotController;
import org.virbo.autoplot.scriptconsole.GuiExceptionHandler;
import org.virbo.autoplot.state.UndoRedoSupport;
import org.virbo.autoplot.util.TickleTimer;
import org.virbo.datasource.AutoplotSettings;
import org.virbo.datasource.DataSetSelector;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSourceRegistry;
import org.virbo.datasource.HtmlResponseIOException;
import org.virbo.datasource.ReferenceCache;
import org.virbo.datasource.SourceTypesBrowser;
import org.virbo.datasource.TimeRangeEditor;
import org.virbo.datasource.URISplit;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * The Autoplot application GUI.  This is the entry point for the application, wrapping the internal
 * application model with conveniences like bookmarks, time range editors and history.
 * 
 * @author  jbf
 */
public class AutoplotUI extends javax.swing.JFrame {
    private static final String TAB_SCRIPT = "script";
    private static final String TAB_CONSOLE = "console";

    final String TAB_TOOLTIP_CANVAS = "<html>Canvas tab contains the plot and plot elements.<br>Click on plot elements to select.<br>%s</html>";
    final String TAB_TOOLTIP_AXES = "<html>Adjust selected plot axes.<br>%s<html>";
    final String TAB_TOOLTIP_LOGCONSOLE = "<html>Log console displays log messages and stdout/stderr.<br>%s</html>";
    final String TAB_TOOLTIP_STYLE = "<html>Adjust selected plot element's colors, shapes, and other style settings.<br>%s</html>";
    final String TAB_TOOLTIP_LAYOUT = "<html>Inspect the canvas layout and property bindings, and<br>provides access to all plot elements.<br>%s</html>";
    final String TAB_TOOLTIP_DATA = "<html>Specify valid ranges and apply additional operations to data.<br>%s</html>";
    final String TAB_TOOLTIP_METADATA = "<html>Inspect selected element's metadata and data statistics.<br>%s</html>";
    final String TAB_TOOLTIP_SCRIPT = "<html>Editor panel for Jython scripts and data sources.<br>%s</html>";
    final String TABS_TOOLTIP = "Right-click or drag to undock.";

    public static final String CARD_DATA_SET_SELECTOR = "card2";
    public static final String CARD_TIME_RANGE_SELECTOR = "card1";

    TearoffTabbedPane tabs;
    ApplicationModel applicationModel;
    Application dom;
    PersistentStateSupport stateSupport;
    UndoRedoSupport undoRedoSupport;
    TickleTimer tickleTimer;
    GuiSupport support;
    LayoutListener autoLayout;
    private boolean dsSelectTimerangeBound= false; // true if there is a binding between the app timerange and the dataSetSelector.

    // if non-null, then load this set of initial bookmarks.
    private String initialBookmarksUrl= null;

    transient PersistentStateSupport.SerializationStrategy serStrategy = new PersistentStateSupport.SerializationStrategy() {

        public Element serialize(Document document, ProgressMonitor monitor) {
            DOMBuilder builder = new DOMBuilder(applicationModel);
            Element element = builder.serialize(document, DasProgressPanel.createFramed("Serializing Application"));
            return element;
        }

        public void deserialize(Document document, ProgressMonitor monitor) {
            Element element = document.getDocumentElement();
            SerializeUtil.processElement(element, applicationModel);
        }
    };
    
    private static final Logger logger = org.das2.util.LoggerManager.getLogger("autoplot");
    private JythonScriptPanel scriptPanel;
    private DataPanel dataPanel;
    private LayoutPanel layoutPanel;
    private JScrollPane layoutPanel1;
    private LogConsole logConsole;
    private JScrollPane logConsolePanel;
    private JPanel jythonScriptPanel;
    private RequestListener rlistener;
    private JDialog fontAndColorsDialog = null;
    private BookmarksManager bookmarksManager = null;
    private AutoplotHelpSystem helpSystem;
    private UriDropTargetListener dropListener;

    private static final String RESOURCES= "/org/virbo/autoplot/resources/";
    public static final Icon WARNING_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"warning-icon.png") );
    public static final Icon ERROR_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"error-icon.png") );
    public static final Icon BUSY_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"spinner.gif") );
    public static final Icon BUSY_OPAQUE_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"spinner_16.gif") );
    public static final Icon READY_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"indProgress0.png") );
    public static final Icon IDLE_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"idle-icon.png") );

    public static final String READY_MESSAGE= "ready";

    private TimeRangeEditor timeRangeEditor;
    private List<JComponent> expertMenuItems= new ArrayList(); // list of items to hide
    private JMenu expertMenu;

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
    
    /** Creates new form AutoPlotMatisse */
    public AutoplotUI(ApplicationModel model) {
        
        setIconImage( AutoplotUtil.getAutoplotIcon() );
        
        APSplash.checkTime("init 0");

        // Initialize help system now so it's ready for components to register IDs with
        AutoplotHelpSystem.initialize(getRootPane());
        helpSystem = AutoplotHelpSystem.getHelpSystem();

        DasApplication.getDefaultApplication().setMainFrame( this );

        if ( DasApplication.getDefaultApplication().isHeadless() ) {
            model.setExceptionHandler( DasApplication.getDefaultApplication().getExceptionHandler() );
        } else {
            model.setExceptionHandler( new GuiExceptionHandler() );
        }

        if ( !ScriptContext.isModelInitialized() ) {
            ScriptContext.setApplicationModel(model);
            ScriptContext.setView(this);
        }

        model.setResizeRequestListener( new ApplicationModel.ResizeRequestListener() {
            public double resize(int w,int h) {
                return resizeForCanvasSize(w, h);
            }
        });

        APSplash.checkTime("init 10");

        this.dom= model.getDocumentModel();
        
        support = new GuiSupport(this);

        applicationModel = model;
        undoRedoSupport = new UndoRedoSupport(applicationModel);
        undoRedoSupport.addPropertyChangeListener(new PropertyChangeListener() {

        public void propertyChange(PropertyChangeEvent evt) {
                SwingUtilities.invokeLater( new Runnable() { public void run() { refreshUndoRedoLabel(); } } );
            }
        });

        applicationModel.addPropertyChangeListener( ApplicationModel.PROP_VAPFILE, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                updateFrameTitle();
            }
        });

        undoRedoSupport.addPropertyChangeListener( UndoRedoSupport.PROP_DEPTH, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                updateFrameTitle();
            }
        } );

        APSplash.checkTime("init 20");

        FileSystem.settings().addPropertyChangeListener( FileSystemSettings.PROP_OFFLINE, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                updateFrameTitle();
            }
        });
        
        if ( model.getExceptionHandler() instanceof GuiExceptionHandler ) {
            ((GuiExceptionHandler)model.getExceptionHandler()).setUndoRedoSupport(undoRedoSupport);
        }
        
        initComponents();
        //TODO: this needs to be explored more:  It gets the desired behavior where running an 
        //Autoplot script doesn't steal focus (see sftp:papco.org:/home/jbf/ct/autoplot/script/fun/jeremy/randImages.jy)
        //but makes it so URIs cannot be entered. https://sourceforge.net/tracker/index.php?func=detail&aid=3532217&group_id=199733&atid=970682
        //this.setFocusableWindowState(false);
          
        if ( false ) {
        Toolkit.getDefaultToolkit().addAWTEventListener( new AWTEventListener() {
            Component focus;
            public void eventDispatched(AWTEvent event) {
                Component c= AutoplotUI.this.getFocusOwner();
                if ( c!=focus ) {
                    System.err.println(c);
                    focus= c;
                    if ( focus instanceof JButton ) {
                        System.err.printf(">>>%s<<<\n",((JButton)focus).getText());
                    }
                }
                //System.err.println( event.getSource() );
            }
        }, FocusEvent.FOCUS_GAINED );
        }
        
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

        jMenuBar1.add( Box.createHorizontalGlue() );
        expertMenu= new JMenu("Expert");
        JMenuItem mi;
        mi= new JMenuItem( new AbstractAction( "Basic Mode") {
           public void actionPerformed( ActionEvent e ) {
               setExpertMode(false);
           }
        });
        mi.setToolTipText("Basic mode allows for browsing products composed by data providers");
        expertMenu.add( mi );
        mi= new JMenuItem( new AbstractAction( "Expert Mode") {
           public void actionPerformed( ActionEvent e ) {
               setExpertMode(true);
           }
        });
        mi.setToolTipText("Expert allows composing new products and scripting");
        expertMenu.add( mi );
        expertMenu.setToolTipText("<html>Toggle between expert and basic mode.<br>Basic mode allows for browsing products composed by data providers<br>Expert allows composing new products and scripting");
        jMenuBar1.add( expertMenu );

        KeyChain.getDefault().setParentGUI(this);
        
        APSplash.checkTime("init 25");

        timeRangeEditor = new TimeRangeEditor();

        Dimension d= timeRangeEditor.getMinimumSize();
        timeRangePanel.add( timeRangeEditor, "card1" );
        timeRangeEditor.setMinimumSize( d );
        timeRangePanel.setMinimumSize( d );
        timeRangeEditor.setDataSetSelectorPeer(dataSetSelector);
        timeRangeEditor.setAlternatePeer("Switch to Data Set Selector","card2");
        dataSetSelector.setAlternatePeer("Switch to Time Range Editor","card1");

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
            public void actionPerformed( ActionEvent ev ) {
                GuiSupport.editPlotElement( applicationModel, AutoplotUI.this );
            }
        });

        dataSetSelector.registerActionTrigger( "bookmarks:(.*)", new AbstractAction( "bookmarks") {
            public void actionPerformed( ActionEvent ev ) {
                String bookmarksFile= dataSetSelector.getValue().substring("bookmarks:".length());
                if ( bookmarksFile.endsWith("/") || bookmarksFile.endsWith(".")) { // normally reject method would trigger another completion
                    DataSetSelector source= (DataSetSelector)ev.getSource();
                    source.showFileSystemCompletions( true, false, "[^\\s]+[^\\s]+(\\.(?i)(xml)|(xml\\.gz))$" );
                } else {
                    while ( getBookmarksManager()==null || getBookmarksManager().getModel()==null || getBookmarksManager().getModel().getList()==null ) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ex) {
                            logger.log(Level.SEVERE, null, ex);
                        }
                    }
                    if ( ! getBookmarksManager().haveRemoteBookmark(bookmarksFile) ) {
                        support.importBookmarks( bookmarksFile );
                        applicationModel.addRecent(dataSetSelector.getValue());
                    } else {
                        setStatus( "remote bookmarks file is already imported"  );
                    }
                }
            }
        });
        dataSetSelector.registerBrowseTrigger( "bookmarks:(.*)", new AbstractAction( "bookmarks") {
            public void actionPerformed( ActionEvent ev ) {
                DataSetSelector source= (DataSetSelector)ev.getSource();
                source.showFileSystemCompletions( false, true, "[^\\s]+(\\.(?i)(xml)|(xml\\.gz))$" );
            }
        });
        dataSetSelector.registerActionTrigger( "pngwalk:(.*)", new AbstractAction( "pngwalk") {
            public void actionPerformed( ActionEvent ev ) { // TODO: underimplemented
                String pngwalk= dataSetSelector.getValue().substring("pngwalk:".length());
                if ( pngwalk.endsWith("/") || pngwalk.endsWith(".")) { // normally reject method would trigger another completion
                    DataSetSelector source= (DataSetSelector)ev.getSource();
                    source.showFileSystemCompletions( true, false, "[^\\s]+(\\.(?i)(jpg|png|gif))$" ); // we can't easily search for .pngwalk here because of inclfiles
                } else {
                    PngWalkTool1.start( pngwalk, AutoplotUI.this);
                    applicationModel.addRecent(dataSetSelector.getValue());
                }
            }
        });
        dataSetSelector.registerBrowseTrigger( "pngwalk:(.*)", new AbstractAction( "pngwalk") {
            public void actionPerformed( ActionEvent ev ) {
                DataSetSelector source= (DataSetSelector)ev.getSource();
                source.showFileSystemCompletions( true, false, "[^\\s]+(\\.(?i)(jpg|png|gif))$" );
                //do nothing
            }
        });
        dataSetSelector.registerActionTrigger( "(.*)\\.pngwalk", new AbstractAction( "pngwalk") {
            public void actionPerformed( ActionEvent ev ) { // TODO: underimplemented
                applicationModel.addRecent(dataSetSelector.getValue());
                String pngwalk= dataSetSelector.getValue();
                PngWalkTool1.start( pngwalk, AutoplotUI.this);
            }
        });

        dataSetSelector.registerActionTrigger( "(.*)\\.jy", new AbstractAction( TAB_SCRIPT) {
            public void actionPerformed( ActionEvent ev ) {
                applicationModel.addRecent(dataSetSelector.getValue());
                runScript( dataSetSelector.getValue() );
            }
        });

        dataSetSelector.registerActionTrigger( "script:(.*)", new AbstractAction( TAB_SCRIPT) {
            public void actionPerformed( ActionEvent ev ) {
                String script = dataSetSelector.getValue().substring("script:".length());
                if ( !( script.endsWith(".jy") || script.endsWith(".JY") || script.endsWith(".py") || script.endsWith(".PY") ) ) {
                    DataSetSelector source= (DataSetSelector)ev.getSource();
                    source.showFileSystemCompletions( false, true, "[^\\s]+\\.jy" );
                } else {
                    applicationModel.addRecent(dataSetSelector.getValue());
                    runScript( script );
                }
            }
        });
        dataSetSelector.registerBrowseTrigger( "script:(.*)", new AbstractAction( "script") {
            public void actionPerformed( ActionEvent ev ) {
                DataSetSelector source= (DataSetSelector)ev.getSource();
                source.showFileSystemCompletions( false, true, "[^\\s]+\\.jy" );
                //do nothing
            }
        });

        dataSetSelector.registerBrowseTrigger( "vapfile:(.*)", new AbstractAction( "vapfile") {
            public void actionPerformed( ActionEvent ev ) {
                DataSetSelector source= (DataSetSelector)ev.getSource();
                source.showFileSystemCompletions( false, true, "[^\\s]+(\\.(?i)(vap)|(vap\\.gz))$" );
            }
        });
        dataSetSelector.registerActionTrigger( "vapfile:(.*)", new AbstractAction( "valfile") {
            public void actionPerformed( ActionEvent ev ) { // TODO: underimplemented
                String vapfile= dataSetSelector.getValue().substring(8);
                if ( !( vapfile.endsWith(".xml") ) ) {
                    DataSetSelector source= (DataSetSelector)ev.getSource();
                    source.showFileSystemCompletions( false, true, "[^\\s]+\\.jy" );
                } else {
                    applicationModel.addRecent(dataSetSelector.getValue());
                    try {
                        InputStream in = DataSetURI.getInputStream( DataSetURI.toUri( vapfile ), new NullProgressMonitor() );
                        applicationModel.doOpen( in, null );
                    } catch ( IOException ex ) {
                        JOptionPane.showConfirmDialog(AutoplotUI.this, "Unable to load: "+vapfile );
                    }
                }
            }
        });

        URISplit.setOtherSchemes( Arrays.asList( "script","pngwalk", "bookmarks","vapfile") );

        APSplash.checkTime("init 40");

        final ApplicationController appController= applicationModel.getDocumentModel().getController();

        appController.addDas2PeerChangeListener( new PropertyChangeListener() {
            public void propertyChange( PropertyChangeEvent e ) {
                PlotController plotController= (PlotController) e.getNewValue();
                ApplicationController controller= plotController.getApplication().getController();
                GuiSupport.addPlotContextMenuItems( AutoplotUI.this, controller, plotController.getDasPlot(), plotController, plotController.getPlot() );
                GuiSupport.addAxisContextMenuItems(  controller,  plotController.getDasPlot(), plotController,  plotController.getPlot(), plotController.getPlot().getXaxis());
                GuiSupport.addAxisContextMenuItems( controller,  plotController.getDasPlot(), plotController,  plotController.getPlot(), plotController.getPlot().getYaxis());
                GuiSupport.addAxisContextMenuItems(  controller,  plotController.getDasPlot(), plotController,  plotController.getPlot(), plotController.getPlot().getZaxis());
            }
        } );

        appController.addPropertyChangeListener( ApplicationController.PROP_FOCUSURI, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                SwingUtilities.invokeLater( new Runnable() { public void run() {
                    dataSetSelector.setValue( appController.getFocusUri() );
                } } );
            }
        } );
        dataSetSelector.setValue( dom.getController().getFocusUri() );
        
        appController.addPropertyChangeListener( ApplicationController.PROP_STATUS, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                setStatus(appController.getStatus());
            }
        } );
        
        
        applicationModel.getCanvas().addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                logger.fine("focus to canvas");
                if (stateSupport.getCurrentFile() != null) {
                    dataSetSelector.setValue(stateSupport.getCurrentFile());
                }
                super.focusGained(e);
            }
        });

        APSplash.checkTime("init 50");

        setIconImage( AutoplotUtil.getAutoplotIcon() );

        stateSupport = getPersistentStateSupport(this, applicationModel);

        fillFileMenu();
        fillInitialBookmarksMenu();
        APSplash.checkTime("init 53");

        AppManager.getInstance().addApplication(this);
        this.addWindowListener( AppManager.getInstance().getWindowListener(this,new AbstractAction("close") {
            public void actionPerformed(ActionEvent e) {
                if ( AutoplotUI.this==ScriptContext.getViewWindow()  ) {
                    ScriptContext.close();
                }
                final Preferences prefs= Preferences.userNodeForPackage(ApplicationModel.class);
                long x= AutoplotUI.this.getLocation().x;
                long y= AutoplotUI.this.getLocation().y;
                logger.log( Level.FINE, "saving last location {0} {1}", new Object[]{x, y});
                prefs.putInt( "locationx", AutoplotUI.this.getLocation().x );
                prefs.putInt( "locationy", AutoplotUI.this.getLocation().y );
                prefs.putInt( "locationscreenwidth", java.awt.Toolkit.getDefaultToolkit().getScreenSize().width );
            }
        }) );
        
        applicationModel.addPropertyChangeListener( ApplicationModel.PROP_VAPFILE, new PropertyChangeListener() {
            public void propertyChange( PropertyChangeEvent e ) {
                stateSupport.setCurrentFile( (String)e.getNewValue() );
            }
        });

        applicationModel.addPropertyChangeListener(new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(ApplicationModel.PROPERTY_RECENT)) {
                    final List<String> urls = new ArrayList<String>();
                    List<Bookmark> recent = applicationModel.getRecent();
                    for (Bookmark b : recent) {
                        urls.add(((Bookmark.Item) b).getUri());
                    }
                    SwingUtilities.invokeLater(
                        new Runnable() { public void run() {
                            dataSetSelector.setRecent(urls);
                        }
                    } );
                } else if (evt.getPropertyName().equals(ApplicationModel.PROPERTY_BOOKMARKS)) {
                    SwingUtilities.invokeLater(
                        new Runnable() { public void run() {
                            updateBookmarks();
                        }
                    } );
                }
            }
        });
        
        autoLayout = new LayoutListener(model);
        APSplash.checkTime("init 55");
        APSplash.checkTime("init 60");

        dataSetSelector.addPropertyChangeListener(DataSetSelector.PROPERTY_MESSAGE, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent e) {
                setStatus(dataSetSelector.getMessage());
            }
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
        addStyle().run();
        addFeatures(fmodel);

        List<String> uris = new ArrayList<String>();
        List<Bookmark> recent = applicationModel.getRecent();
        APSplash.checkTime("init 80");

        for (Bookmark b : recent) {
            uris.add(((Bookmark.Item) b).getUri());
        }
        dataSetSelector.setRecent(uris);
        //some other bug had been preventing this code from working.  I actually like the bug behavior better, where the value is
        //not the most recent one, so I'm commenting this out to restore this behavior.
//        if (uris.size() > 1) {
//            if ( dataSetSelector.getEditor().getText().equals("") ) {
//                dataSetSelector.getEditor().setText(uris.get(uris.size() - 1)); // avoid firing event
//            }
//        }

        //since bookmarks can contain remote folder, get these after making the gui.
        Runnable run= new Runnable() {
            public void run() {
                updateBookmarks();
            }
        };
        invokeLater( 1000, true, run );
        APSplash.checkTime("init 90");

        addTools();
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
            public void run() {
                try {
                    //initialize the python interpretter
                    JythonUtil.createInterpreter(true, false);
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
        };
        invokeLater( 10000, false, run );
    }

    private Runnable addAxes() {
        return new Runnable() {
            public void run() {
  APSplash.checkTime("addAxes in");
                final JScrollPane sp= new JScrollPane();
                tabs.insertTab("axes", null, sp,
                        String.format(  TAB_TOOLTIP_AXES, TABS_TOOLTIP), 1);
                invokeLater( 2500, false, new Runnable() {
                    @Override
                    public String toString() { return "addAxesRunnable"; }
                    public void run() {
  APSplash.checkTime("addAxes1 in");
                        final JComponent c= new AxisPanel(applicationModel);
                        SwingUtilities.invokeLater( new Runnable() {
                            public void run( ) { sp.setViewportView(c); }
                        } );
  APSplash.checkTime("addAxes1 out");
                    }
                });
  APSplash.checkTime("addAxes out");
            }
        };
    }

    private Runnable addStyle() {
        return new Runnable() {
            public void run() {
  APSplash.checkTime("addStyle in");
                final JScrollPane sp= new JScrollPane();
                tabs.insertTab("style", null, sp,
                        String.format(  TAB_TOOLTIP_STYLE, TABS_TOOLTIP), 2);
                invokeLater( 2500, false, new Runnable() {
                    @Override
                    public String toString() { return "addStyle"; }
                    public void run() {
  APSplash.checkTime("addStyle1 in");
                        final JComponent c= new PlotStylePanel(applicationModel);
                        SwingUtilities.invokeLater( new Runnable() {
                            public void run( ) { sp.setViewportView(c); }
                        } );
  APSplash.checkTime("addStyle1 out");
                    }
                } );
  APSplash.checkTime("addStyle out");
            }
        };
    }

    /**
     * this method is disabled from WebStart version, since it doesn't work with
     * the security model.
     * @throws HeadlessException
     */
    private void addDataSource() throws HeadlessException {
        AddDataSourcePanel add = new AddDataSourcePanel();
        int r = JOptionPane.showConfirmDialog(this, add, "Add Data Source", JOptionPane.OK_CANCEL_OPTION);
        if (r == JOptionPane.OK_OPTION) {
            String jar = add.getDataSetSelector().getValue();
            if (jar.endsWith("jar")) {
                try {
                    DataSourceRegistry.getInstance().registerDataSourceJar(null, new URL(jar));
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
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
        List<String> result= new LinkedList<String>(messages);
        for ( String s: messages ) {
            if ( s.equals("Delete Plot" ) ) {
                result.remove("Delete Plot Element");
            }
        }
        return result;
    }

    private void addFeatures( ApplicationModel model ) {

        final JScrollPane flayoutPane;
        if (model.getDocumentModel().getOptions().isLayoutVisible() ) {
            flayoutPane= new JScrollPane();
            tabs.insertTab("layout",null, flayoutPane,
                    String.format( TAB_TOOLTIP_LAYOUT, TABS_TOOLTIP), tabs.getTabCount() );
        } else {
            flayoutPane= null;
        }
        layoutPanel1= flayoutPane;

        final JScrollPane fdataPane;
        if (model.getDocumentModel().getOptions().isDataVisible()) {
            fdataPane= new JScrollPane();
            tabs.insertTab("data", null, fdataPane,
                    String.format(  TAB_TOOLTIP_DATA, TABS_TOOLTIP), tabs.getTabCount() );
        } else {
            fdataPane= null;
        }

        final JScrollPane fmetadataPane= new JScrollPane();
        tabs.insertTab("metadata", null, fmetadataPane,
                String.format(  TAB_TOOLTIP_METADATA, TABS_TOOLTIP), tabs.getTabCount() );

        invokeLater( 3530, false, new Runnable() {
            @Override
            public String toString() { return "addLayout"; }
            public void run() {
                //long t0= System.currentTimeMillis();
APSplash.checkTime("init 249");
                if ( flayoutPane!=null ) {
                    final LayoutPanel lui= new LayoutPanel();
                    layoutPanel= lui;
                    SwingUtilities.invokeLater( new Runnable() { public void run() {
                        flayoutPane.setViewportView(lui);
                    } } );
                    lui.setApplication(dom);
APSplash.checkTime("init 250");
                }
            }
        } );
        invokeLater( 3550, false, new Runnable() {
            @Override
            public String toString() { return "addDataPanel"; }
            public void run() {
                //System.err.println("  invokeLater set, layout panel "+(System.currentTimeMillis()-t0));
APSplash.checkTime("init 259");
                if ( fdataPane!=null ) {
                    final DataPanel dp= new DataPanel(dom);
                    dataPanel= dp;
                    dp.doBindings();
                    SwingUtilities.invokeLater( new Runnable() { public void run() {
                        fdataPane.setViewportView(dp);
                    } } );
APSplash.checkTime("init 260");
                }
            }
        } );
        invokeLater( 3570, false, new Runnable() { 
            @Override
            public String toString() { return "addMetadataPanel"; }
            public void run() {
APSplash.checkTime("init 269");
                final MetadataPanel mdp = new MetadataPanel(applicationModel);
                SwingUtilities.invokeLater( new Runnable() { public void run() {
                    fmetadataPane.setViewportView(mdp);
                } } );
APSplash.checkTime("init 270");
            }
        });

        if (model.getDocumentModel().getOptions().isScriptVisible()) {
            final DataSetSelector fdataSetSelector= this.dataSetSelector; // org.pushngpixels.tracing.TracingEventQueueJMX showed this was a problem.
            jythonScriptPanel= new JPanel( new BorderLayout() );
            tabs.addTab( TAB_SCRIPT, null, jythonScriptPanel,
                  String.format(  TAB_TOOLTIP_SCRIPT, TABS_TOOLTIP )  );
            invokeLater( 4000, true, new Runnable() {
                @Override
                public String toString() { return "addScriptPanel"; }
                public void run() {
                    scriptPanel= new JythonScriptPanel(applicationModel, fdataSetSelector);
                    SwingUtilities.invokeLater( new Runnable() { public void run() {
                       jythonScriptPanel.add(scriptPanel,BorderLayout.CENTER);
                       scriptPanelMenuItem.setSelected(true);
                    } } );
                }
            } );
        }
        if (model.getDocumentModel().getOptions().isLogConsoleVisible()) {
            logConsolePanel= new JScrollPane();
            tabs.addTab( TAB_CONSOLE, null, logConsolePanel,
                String.format(  TAB_TOOLTIP_LOGCONSOLE, TABS_TOOLTIP) );
            invokeLater( 4020, true, new Runnable() {
                @Override
                public String toString() { return "addLogConsole"; }
                public void run() {
                    initLogConsole();
                    SwingUtilities.invokeLater( new Runnable() { public void run() {
                        logConsolePanel.setViewportView( logConsole );
                    } } );
                }
            }  );
        }


        tickleTimer = new TickleTimer(300, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {

                if ( dom.getController().isValueAdjusting() ) { // don't listen to property changes during state transitions.
                    tickleTimer.tickle("app value was adjusting");
                    return;
                }
                
                Map<Object,Object> changes= new LinkedHashMap<Object,Object>();
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
                        //undoRedoSupport.pushState(evt,messages.get(0));
                        undoRedoSupport.pushState(evt,null); // TODO: named undo operations.  fix findbugs DB_DUPLICATE_BRANCHES
                    }
                } else if ( messages.size()==1 ) {
                    if ( messages.get(0).contains(" from ") ) {
                        undoRedoSupport.pushState(evt);
                    } else {
                        undoRedoSupport.pushState(evt,messages.get(0)); // named undo operation
                    }
                } else {
                    //I've hit this state before when loading empty vap file: file:///home/jbf/ct/autoplot/script/demos/interpolateToCommonTags2.vap
                    logger.fine("tickleTimer contained no messages.");
                }

                stateSupport.markDirty();

                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        refreshUndoRedoLabel();
                    }
                });
            }
        });

        applicationModel.dom.getController().addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent evt ) {
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
            public void propertyChange( PropertyChangeEvent evt ) {
                DataSourceFilter dsf= (DataSourceFilter) evt.getNewValue();

                if ( dsf==null ) {
                    dataSetSelector.setValue("");
                } else {
                    String uri= dsf.getUri();
                    if ( uri!=null ) {
                        dataSetSelector.setValue(uri);
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
            public void run() {
                BindingGroup bc = new BindingGroup();
                bind( bc, dom.getOptions(), Options.PROP_DRAWANTIALIAS, drawAntiAliasMenuItem, "selected" );
                bind( bc, dom.getOptions(), Options.PROP_TEXTANTIALIAS, textAntiAlias, "selected") ;
                bind( bc, dom.getOptions(), Options.PROP_SPECIALEFFECTS, specialEffectsMenuItem, "selected" );
                bind( bc, dom.getOptions(), Options.PROP_OVERRENDERING, overRenderingMenuItem, "selected" );
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
                bc.bind();

                dom.addPropertyChangeListener( Application.PROP_BINDINGS, new PropertyChangeListener() {
                    public void propertyChange(PropertyChangeEvent evt) {
                        BindingModel[] bms= dom.getBindings();
                        boolean isBound=false;
                        for ( int i=0; i<bms.length; i++ ) {
                            if ( bms[i].getSrcProperty().equals("timeRange") ) {
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

            }
        };
        invokeLater(-1,true,run);

        this.dataSetSelector.addPropertyChangeListener("value", new PropertyChangeListener() { //one-way binding
            public void propertyChange(PropertyChangeEvent evt) {
                    applicationModel.setDataSourceURL(dataSetSelector.getValue());
            }
        });
    }

    public void plotUri( String uri ) {
        dataSetSelector.setValue(uri);
        dataSetSelector.maybePlot(false);        
    }
    

    private Action getAddPanelAction() {
        return new AbstractAction("Add Plot...") {
            public void actionPerformed(ActionEvent e) {
                support.addPlotElement();
            }
        };
    }

    JMenu addDataFromMenu= null;

    private synchronized JMenu getAddDataFromMenu( ) {
        JMenu result= new JMenu( "Add Plot From" );
        result.add( new JMenuItem("looking up discoverable sources...") );
        addDataFromMenu= result;
        RequestProcessor.invokeLater( new Runnable() {
            public void run() {
                fillAddDataFromMenu();
            }
        });
        return result;
    }

    private void fillAddDataFromMenuImmediately(final List<String> exts) {
        addDataFromMenu.removeAll();
        for ( String ext: exts ) {
            if ( ext.startsWith(".") ) ext= ext.substring(1);
            final String fext= ext;
            Action a= new AbstractAction( ext+"..." ) {
                public void actionPerformed(ActionEvent e) {
                    try {
                        String uri = "vap+" + fext + ":";
                        String refuri= (String) dataSetSelector.getEditor().getText();
                        if ( refuri.startsWith(uri) ) {
                            dataSetSelector.browseSourceType();
                        } else {
                            dataSetSelector.setValue(uri);
                            dataSetSelector.maybePlot( true );
                        }
                    } catch (Exception ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                }
            };
            JMenuItem item= new JMenuItem( a );
            addDataFromMenu.add(item);
        }
    }
    
    /**
     * look up the discoverable extensions--the sources that can be added with
     * just "vap+<ext>:" because we can enter a GUI right away.  This should be
     * called off the event thread, and will fill the addDataFromMenu.
     */
    private void fillAddDataFromMenu() {
        final List<String> exts= DataSetURI.getDiscoverableExtensions();
        Runnable run= new Runnable() {
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

        mi= new JMenuItem(" ");
        bookmarksMenu.add(mi);

    }

    private void fillFileMenu() {
        List<JComponent> expertItems= new ArrayList();

        expertItems.add( new JMenuItem(support.createNewApplicationAction()) );
        expertItems.add( new JMenuItem(support.createCloneApplicationAction()) );
        fileMenu.add( expertItems.get(0) );
        fileMenu.add( expertItems.get(1) );

        javax.swing.JMenuItem mi= new JMenuItem( support.createNewDOMAction() );
        expertItems.add(mi);
        mi.setToolTipText("Reset application to initial state");
        fileMenu.add(mi);

        fileMenu.add( new JSeparator() );
APSplash.checkTime("init 51");
        fileMenu.add( getAddDataFromMenu() );
APSplash.checkTime("init 52");
        mi= new JMenuItem(getAddPanelAction() );
        mi.setToolTipText("Add a new plot or overplot to the application");
        expertItems.add(mi);
        fileMenu.add(mi);

        mi= new JMenuItem( new AbstractAction( "Open URI History..." ) {
              public void actionPerformed( ActionEvent e ) {
                  RecentUrisDialog dia= new RecentUrisDialog( (java.awt.Frame)SwingUtilities.getWindowAncestor(fileMenu), true );
                  dia.setExpertMode( isExpertMode() );
                  dia.setVisible(true);
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
        mi.setIcon( new ImageIcon( getClass().getResource("/resources/history.png") ) );

        fileMenu.add( mi );

        mi= new JMenuItem(dataSetSelector.getOpenLocalAction() );
        mi.setToolTipText("Open local data file");
        expertItems.add(mi);
        fileMenu.add(mi);

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
//        fileMenu.add(new AbstractAction("Save With Data...") {
//
//            public void actionPerformed(ActionEvent e) {
//                applicationModel.setUseEmbeddedDataSet(true);
//                stateSupport.saveAs();
//            }
//        });
        fileMenu.addSeparator();

        //TODO: decorate print action to set focus.
        AbstractAction printAction= new AbstractAction( "Print...") {
            public void actionPerformed(ActionEvent e) {
                applicationModel.getCanvas().makeCurrent();
                DasCanvas.PRINT_ACTION.actionPerformed(e);
            }
        };
        mi= new JMenuItem( printAction );
        mi.setToolTipText("Print to printer");
        fileMenu.add( mi );

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

        fileMenu.addSeparator();

        //mi= new JMenuItem( support.getDumpDataAction() );
        //mi.setToolTipText("Export the data that has the focus");
        //fileMenu.add( mi );

        item = new JMenuItem( support.getDumpDataAction2( dom ) );
        item.setToolTipText("Export the data that has the focus");
        expertItems.add(item);
        fileMenu.add( item );

        JSeparator dumpSep= new JSeparator();
        expertItems.add( dumpSep );

        //fileMenu.add( new )
        //fileMenu.add( GuiSupport.getExportDataAction(AutoplotUI.this) );


        fileMenu.add( dumpSep );
        //fileMenu.addSeparator();


        fileMenu.add( new AbstractAction( "Close" ) {
            public void actionPerformed( ActionEvent ev ) {
                if ( AppManager.getInstance().getApplicationCount()==1 ) {
                    int opt= JOptionPane.showConfirmDialog( AutoplotUI.this,
                            "Quit application?", "Quit Autoplot", JOptionPane.YES_NO_CANCEL_OPTION );
                    if ( opt==JOptionPane.YES_OPTION ) {
                        //normal route
                        if ( AppManager.getInstance().requestQuit() ) {
                            
                        } else {
                            return;
                        }
                    } else if ( opt==JOptionPane.NO_OPTION ) {
                        AutoplotUI.this.dom.getController().reset();
                        return;
                    } else {
                        return;
                    }
                }
                AutoplotUI.this.dispose();
                ScriptContext.close();
                AppManager.getInstance().closeApplication(AutoplotUI.this);
            }
        });

        fileMenu.add( new AbstractAction( "Quit" ) {
            public void actionPerformed( ActionEvent ev ) {
                if ( AppManager.getInstance().requestQuit() ) {
                    AutoplotUI.this.dispose();
                    AppManager.getInstance().quit();
                }
            }
        });

        expertMenuItems.addAll( expertItems );
    }

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
        logConsole.turnOffConsoleHandlers();
        logConsole.logConsoleMessages(); // stderr, stdout logged to Logger "console"

        Handler h = logConsole.getHandler();
        Logger.getLogger("das2").setLevel(Level.INFO);  // see http://www.autoplot.org/developer.logging
        Logger.getLogger("das2").addHandler(h);
        Logger.getLogger("datum").setLevel(Level.INFO);
        Logger.getLogger("datum").addHandler(h);
        Logger.getLogger("qdataset").setLevel(Level.INFO);
        Logger.getLogger("qdataset").addHandler(h);
        Logger.getLogger("autoplot").setLevel(Level.INFO);
        Logger.getLogger("autoplot").addHandler(h);
        Logger.getLogger("apdss").setLevel(Level.INFO);
        Logger.getLogger("apdss").addHandler(h);
        Logger.getLogger("jython").setLevel(Level.INFO);
        Logger.getLogger("jython").addHandler(h);
        Logger.getLogger("console").setLevel(Level.INFO);
        Logger.getLogger("console").addHandler(h); // stderr, stdout

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
        String result = JOptionPane.showInputDialog(this, "Select port for server.  This port will accept Jython commands to control and receive services from the application", 12345);
        if ( result==null ) return;
        int iport = Integer.parseInt(result);
        setupServer(iport, applicationModel);
    }

    private void stopServer() {
        if ( rlistener!=null ) rlistener.stopListening();
        rlistener= null;
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

    private void plotUrl( String surl ) {
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
                        logger.log(Level.SEVERE, null, ex);
                        String s= "<li>scheme:arguments</li>"
                                + "vap+cdaweb:ds=AC_H3_SWI&id=SW_type<br>"
                                + "<li>scheme:URL?scheme-arguments</li>"
                                + "vap+dat:http://autoplot.org/data/somedata.dat?column=field1<br>"
                                + "<li>URL?scheme-arguments</li>"
                                + "http://autoplot.org/data/somedata.dat?column=field1</br>";

                        JOptionPane.showMessageDialog( this, "<html><p>URI Syntax Error found when parsing.</p>"+s, "URI Syntax Error", JOptionPane.OK_OPTION );
                        return;
                    } catch ( IllegalArgumentException ex ) {
                        SourceTypesBrowser browser= new SourceTypesBrowser();
                        browser.getDataSetSelector().setValue(DataSetURI.fromUri(DataSetURI.getResourceURI(surl)));
                        int r= JOptionPane.showConfirmDialog(this, browser,"Select Data Source Type",JOptionPane.OK_CANCEL_OPTION);
                        if ( r==JOptionPane.OK_OPTION ) {
                            surl= browser.getUri();
                            dataSetSelector.getEditor().setText(surl);
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
            }
        } catch (RuntimeException ex) {
            if ( ex.getCause()!=null && ex.getCause() instanceof HtmlResponseIOException ) {
                setStatus(ERROR_ICON,"Html response from URI: " + surl);
                HtmlResponseIOException htmlEx= ((HtmlResponseIOException)ex.getCause());
                if ( htmlEx.getURL()!=null ) {
                    final String link= htmlEx.getURL().toString();
                    JPanel p= new JPanel( new BorderLayout( ) );
                    p.add( new JLabel(  "<html>Unable to open URI: <br>" + surl+"<br><br>"+ex.getCause().getMessage()+ "<br><a href=\""+link+"\">"+link+"</a><br>" ), BorderLayout.CENTER );
                    JPanel p1= new JPanel( new BorderLayout() );
                    p1.add( new JButton( new AbstractAction("View Page") {
                        public void actionPerformed( ActionEvent ev ) {
                            AutoplotUtil.openBrowser(link);
                        }
                    }), BorderLayout.EAST );
                    p.add( p1, BorderLayout.SOUTH );
                    JOptionPane.showMessageDialog( this, p );
                } else {
                    JOptionPane.showMessageDialog( this, "<html>Unable to open URI: <br>" + surl+"<br><br>"+ex.getCause() );
                }
            } else if ( ex.getCause()!=null && ex.getCause() instanceof IOException ) {
                setStatus(ERROR_ICON,"Unable to open URI: " + surl);
                JOptionPane.showMessageDialog( this, "<html>Unable to open URI: <br>" + surl+"<br><br>"+ex.getCause() );
            } else {
                applicationModel.getExceptionHandler().handleUncaught(ex);
                String msg= ex.getMessage();
                if ( msg==null ) msg= ex.toString();
                setStatus(ERROR_ICON,msg);
            }
        }
    }


    /**
     * add a new plot and plotElement.  This is attached to control-enter.
     */
    private void plotAnotherUrl() {
        plotAnotherUrl( (String) dataSetSelector.getValue() );
    }

    private void plotAnotherUrl( final String surl ) {
        try {
            logger.log(Level.FINE, "plotAnotherUrl({0})", surl);
            PlotElement panel= dom.getController().addPlotElement( null,null );
            dom.getController().getDataSourceFilterFor(panel).setUri(surl);
            dom.getController().setPlotElement(panel);
            
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
     * resize the outer GUI attempting to get a fitted canvas size.  This fixes the
     * problem where a loaded vap doesn't appear as it does when it was saved because
     * the canvas is resized.
     * 
     * @param w
     * @param h
     * @return nominal scale factor
     */
    public double resizeForCanvasSize( int w, int h ) {
        
        Component parentToAdjust;
        if ( SwingUtilities.isDescendingFrom( applicationModel.getCanvas(), this ) ) {
            parentToAdjust= this;
        } else {
            parentToAdjust= SwingUtilities.getWindowAncestor(applicationModel.getCanvas());
        }
        Dimension dout= parentToAdjust.getSize();
        Dimension din= this.applicationModel.getCanvas().getSize();

        dout.width= dout.width + (  w - din.width );
        dout.height= dout.height + ( h - din.height );

        //GraphicsConfiguration gc= getGraphicsConfiguration();
        //Dimension screenSize = gc.getBounds().getSize();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

        double scale= 1.0;

        if ( w<640 || h<480 ) {
            this.applicationModel.dom.getCanvases(0).setFitted(false);
            setStatus("warning: canvas is no longer fitted, see options->plot style->canvas size");
            this.applicationModel.dom.getCanvases(0).setHeight(h);
            this.applicationModel.dom.getCanvases(0).setWidth(w);
            
        } else if ( dout.width>screenSize.getWidth() || dout.height>screenSize.getHeight() ) {

            String[] options= new String[] { "Scale to fit display", "Use scrollbars" };
            int i= JOptionPane.showOptionDialog( this, "Canvas size doesn't fit well on this display.", "Incompatible Canvas Size", JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE, ERROR_ICON,
                    options, options[1] );
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
                    parentToAdjust.setSize( nw + (  w - din.width ) , nh + ( h - din.height ) );

                } else if ( i==1 ) {
                    this.applicationModel.dom.getCanvases(0).setFitted(false);
                    setStatus("warning: canvas is no longer fitted, see options->plot style->canvas size");
                    this.applicationModel.dom.getCanvases(0).setHeight(h);
                    this.applicationModel.dom.getCanvases(0).setWidth(w);
                }
            }
        } else {
            // there's actually a new bug here, at least on Linux you can't resize to > one screen...
            parentToAdjust.setSize( dout.width, dout.height );
            if ( parentToAdjust.getSize().getWidth()!=dout.width ) {
                this.applicationModel.dom.getCanvases(0).setFitted(false);
                setStatus("warning: unable to resize to requested size, using scrollbars.");
                this.applicationModel.dom.getCanvases(0).setHeight(h);
                this.applicationModel.dom.getCanvases(0).setWidth(w);
            }
        }
        return scale;
    }

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
        if (JOptionPane.showConfirmDialog(this, "delete all cached files?", "clear cache", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
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

    private void maybeCreateBookmarksManager() {
        if (bookmarksManager == null) {
            bookmarksManager = new BookmarksManager(AutoplotUI.this, false);

            Binding b= Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, applicationModel, BeanProperty.create( "bookmarks" ), bookmarksManager.getModel(), BeanProperty.create("list"));
            b.bind();

            bookmarksManager.getModel().addPropertyChangeListener(BookmarksManagerModel.PROP_BOOKMARK, new PropertyChangeListener() {
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
                public void propertyChange(PropertyChangeEvent evt) {
                    SwingUtilities.invokeLater( new Runnable() { public void run() {
                        bookmarksManager.updateBookmarks( bookmarksMenu, AutoplotUI.this.dataSetSelector );
                    } } );
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
                setStatus("loading initial demo bookmarks");
                List<Bookmark> bookmarks = applicationModel.getLegacyBookmarks();
                bookmarksManager.getModel().setList(bookmarks);
                bookmarksManager.resetPrefNode("bookmarks");
            }
        }

        Runnable run= new Runnable() { public void run() {
            bookmarksManager.setPrefNode("bookmarks"); 
            if ( initialBookmarksUrl!=null ) {
                loadInitialBookmarks(initialBookmarksUrl);
                initialBookmarksUrl= null;
            }

        } };
        invokeLater( -1, false, run );

//        addBookmarks(bookmarksMenu, bookmarks);
    }

    public static PersistentStateSupport getPersistentStateSupport(final AutoplotUI parent, final ApplicationModel applicationModel) {
        final PersistentStateSupport stateSupport = new PersistentStateSupport(parent, null, "vap") {

            @Override
            protected void saveImpl(File f,String scheme) throws IOException {
                applicationModel.doSave(f,scheme);
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

        persistentStateSupport1 = new org.das2.dasml.PersistentStateSupport();
        addressBarButtonGroup = new javax.swing.ButtonGroup();
        statusLabel = new javax.swing.JLabel();
        tabbedPanelContainer = new javax.swing.JPanel();
        statusTextField = new javax.swing.JTextField();
        timeRangePanel = new javax.swing.JPanel();
        dataSetSelector = new org.virbo.datasource.DataSetSelector();
        jMenuBar1 = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        editMenu = new javax.swing.JMenu();
        undoMenuItem = new javax.swing.JMenuItem();
        redoMenuItem = new javax.swing.JMenuItem();
        undoMultipleMenu = new javax.swing.JMenu();
        editDomSeparator = new javax.swing.JSeparator();
        editDomMenuItem = new javax.swing.JMenuItem();
        EditOptions = new javax.swing.JMenuItem();
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
        textAntiAlias = new javax.swing.JCheckBoxMenuItem();
        drawAntiAliasMenuItem = new javax.swing.JCheckBoxMenuItem();
        specialEffectsMenuItem = new javax.swing.JCheckBoxMenuItem();
        overRenderingMenuItem = new javax.swing.JCheckBoxMenuItem();
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
        autoMenu = new javax.swing.JMenu();
        autoRangingCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        autoLabellingCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        autoLayoutCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
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
        jSeparator2 = new javax.swing.JPopupMenu.Separator();
        fixLayoutMenuItem = new javax.swing.JMenuItem();
        createPngWalkSeparator = new javax.swing.JSeparator();
        aggregateMenuItem = new javax.swing.JMenuItem();
        replaceFileMenuItem = new javax.swing.JMenuItem();
        aggSeparator = new javax.swing.JSeparator();
        decodeURLItem = new javax.swing.JMenuItem();
        reloadAllMenuItem = new javax.swing.JMenuItem();
        helpMenu = new javax.swing.JMenu();
        autoplotHelpMenuItem = new javax.swing.JMenuItem();
        gettingStartedMenuItem = new javax.swing.JMenuItem();
        jMenuItem5 = new javax.swing.JMenuItem();
        aboutDas2MenuItem = new javax.swing.JMenuItem();
        autoplotHomepageButton = new javax.swing.JMenuItem();
        searchToolTipsMenuItem = new javax.swing.JMenuItem();
        exceptionReport = new javax.swing.JMenuItem();
        aboutAutoplotMenuItem = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Autoplot");

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
        dataSetSelector.setPromptText("Enter data location or select a bookmark");
        dataSetSelector.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSetSelectorActionPerformed(evt);
            }
        });
        timeRangePanel.add(dataSetSelector, "card2");

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

        EditOptions.setText("Options...");
        EditOptions.setToolTipText("Edit user options like background colors and fonts");
        EditOptions.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                EditOptionsActionPerformed(evt);
            }
        });
        editMenu.add(EditOptions);

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

        viewMenu.add(textSizeMenu);
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

        textAntiAlias.setSelected(true);
        textAntiAlias.setText("Text Antialias");
        textAntiAlias.setToolTipText("Enable/Disable Text Antialiasing");
        textAntiAlias.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                textAntiAliasActionPerformed(evt);
            }
        });
        renderingOptionsMenu.add(textAntiAlias);

        drawAntiAliasMenuItem.setSelected(true);
        drawAntiAliasMenuItem.setText("Graphics Antialias");
        drawAntiAliasMenuItem.setToolTipText("Enable/Disable Graphics Antialiasing");
        drawAntiAliasMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                drawAntiAliasMenuItemActionPerformed(evt);
            }
        });
        renderingOptionsMenu.add(drawAntiAliasMenuItem);

        specialEffectsMenuItem.setText("Special Effects");
        specialEffectsMenuItem.setToolTipText("Enable animated axes and other visual clues");
        specialEffectsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                specialEffectsMenuItemActionPerformed(evt);
            }
        });
        renderingOptionsMenu.add(specialEffectsMenuItem);

        overRenderingMenuItem.setSelected(true);
        overRenderingMenuItem.setText("Over-Rendering");
        overRenderingMenuItem.setToolTipText("Render (and load) data outside plot bounds to improve appearance.");
        renderingOptionsMenu.add(overRenderingMenuItem);

        drawGridCheckBox.setSelected(true);
        drawGridCheckBox.setText("Draw Grid");
        drawGridCheckBox.setToolTipText("Draw gridlines at major ticks");
        renderingOptionsMenu.add(drawGridCheckBox);

        doyCB.setText("Day of Year Labels");
        doyCB.setToolTipText("Use Day of Year instead of Year-Month-Day for labels");
        renderingOptionsMenu.add(doyCB);

        nnCb.setText("Nearest Neighbor Spectrograms");
        nnCb.setToolTipText("Use Nearest Neighbor rebinning for new spectrograms");
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
        toolsMenu.add(jSeparator2);

        fixLayoutMenuItem.setText("Fix Layout");
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
        toolsMenu.add(aggSeparator);

        decodeURLItem.setText("Decode URL");
        decodeURLItem.setToolTipText("Decode the URL escapes to correct the URL\n");
        decodeURLItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                decodeURLItemActionPerformed(evt);
            }
        });
        toolsMenu.add(decodeURLItem);

        reloadAllMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/virbo/autoplot/resources/reload.png"))); // NOI18N
        reloadAllMenuItem.setText("Reload All Data");
        reloadAllMenuItem.setToolTipText("Reload all data, updating to get any changes.  Axis settings and labels should remain the same.");
        reloadAllMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                reloadAllMenuItemActionPerformed(evt);
            }
        });
        toolsMenu.add(reloadAllMenuItem);

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
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .add(timeRangePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 31, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 603, Short.MAX_VALUE)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(statusLabel)
                    .add(statusTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                    .add(48, 48, 48)
                    .add(tabbedPanelContainer, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 596, Short.MAX_VALUE)
                    .add(20, 20, 20)))
        );

        bindingGroup.bind();

        pack();
    }// </editor-fold>//GEN-END:initComponents
    private void copyImageMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyImageMenuItemActionPerformed
        support.doCopyDataSetImage();
    }//GEN-LAST:event_copyImageMenuItemActionPerformed

    private void copyDataSetURLMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyDataSetURLMenuItemActionPerformed
        support.doCopyDataSetURL();
    }//GEN-LAST:event_copyDataSetURLMenuItemActionPerformed

    private void pasteDataSetURLMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pasteDataSetURLMenuItemActionPerformed
        support.doPasteDataSetURL();
    }//GEN-LAST:event_pasteDataSetURLMenuItemActionPerformed

    private void dataSetSelectorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSetSelectorActionPerformed
        String uri= (String) dataSetSelector.getValue();
        ((GuiExceptionHandler)applicationModel.getExceptionHandler()).setFocusURI(uri);
        if ( this.isExpertMode() ) {
            if ( ( evt.getModifiers() & KeyEvent.CTRL_MASK ) == KeyEvent.CTRL_MASK ) {
                plotAnotherUrl();
            } else if ( ( evt.getModifiers() & KeyEvent.SHIFT_MASK ) == KeyEvent.SHIFT_MASK )  {
                overplotAnotherUrl();
            } else {
                plotUrl(uri);
            }
        } else {
            dom.getController().reset();
            plotUrl( uri );
        }
    }//GEN-LAST:event_dataSetSelectorActionPerformed

    private void zoomOutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomOutMenuItemActionPerformed
        DasPlot p= dom.getController().getPlot().getController().getDasPlot();
        DatumRange dr = DatumRangeUtil.rescale( p.getXAxis().getDatumRange(), -0.333, 1.333);
        p.getXAxis().setDatumRange(dr);
    }//GEN-LAST:event_zoomOutMenuItemActionPerformed

    private void zoomInMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomInMenuItemActionPerformed
        DasPlot p= dom.getController().getPlot().getController().getDasPlot();
        DatumRange dr = DatumRangeUtil.rescale(p.getXAxis().getDatumRange(), 0.25, 0.75);
        p.getXAxis().setDatumRange(dr);
    }//GEN-LAST:event_zoomInMenuItemActionPerformed

    private void resetZoomMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetZoomMenuItemActionPerformed
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
        if (fontAndColorsDialog == null) fontAndColorsDialog = new FontAndColorsDialog(this, false, applicationModel);
        fontAndColorsDialog.setVisible(true);
    }//GEN-LAST:event_fontsAndColorsMenuItemActionPerformed

    private void specialEffectsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_specialEffectsMenuItemActionPerformed
        applicationModel.getDocumentModel().getOptions().setSpecialEffects(specialEffectsMenuItem.isSelected());
    }//GEN-LAST:event_specialEffectsMenuItemActionPerformed

    private void drawAntiAliasMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_drawAntiAliasMenuItemActionPerformed
        applicationModel.getDocumentModel().getOptions().setDrawAntiAlias(drawAntiAliasMenuItem.isSelected());
    }//GEN-LAST:event_drawAntiAliasMenuItemActionPerformed

    private void textAntiAliasActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_textAntiAliasActionPerformed
        applicationModel.getDocumentModel().getOptions().setTextAntiAlias(textAntiAlias.isSelected());
    }//GEN-LAST:event_textAntiAliasActionPerformed

    private void aboutAutoplotMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aboutAutoplotMenuItemActionPerformed
        try {
            StringBuilder buffy = new StringBuilder();

            buffy.append("<html>\n");
            URL aboutHtml = AutoplotUI.class.getResource("aboutAutoplot.html");

            if ( aboutHtml!=null ) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(aboutHtml.openStream()));
                String s = reader.readLine();
                while (s != null) {
                    buffy.append(s);
                    s = reader.readLine();
                }
                reader.close();
            } 

            buffy.append("<h2>Build Information:</h2>");
            buffy.append("<ul>");
            buffy.append("<li>release tag: ").append(AboutUtil.getReleaseTag()).append("</li>");

            List<String> bi = Util.getBuildInfos();
            for (String ss : bi) {
                buffy.append("    <li>").append(ss);
            }
            buffy.append("</ul>" );

            buffy.append( "<h2>Open Source Components:</h2>");
            buffy.append( "Autoplot uses many open-source components, such as: <br>");
            buffy.append( "OpenDAP, jsyntaxpane, Jython, CDF, FITS, NetCDF, POI HSSF (Excel), Batik (SVG), iText (PDF), JSON, JavaCSV, JPG Metadata Extractor, das2, JDiskHog");        
            
            buffy.append("<h2>Runtime Information:</h2>");

            String javaVersion = System.getProperty("java.version"); // applet okay
            String arch = System.getProperty("os.arch"); // applet okay
            java.text.DecimalFormat nf = new java.text.DecimalFormat("0.0");
            String mem = nf.format(Runtime.getRuntime().maxMemory()   / 1000000 );
            String tmem= nf.format(Runtime.getRuntime().totalMemory() / 1000000 );
            String fmem= nf.format(Runtime.getRuntime().freeMemory()  / 1000000 );
            String pwd= new File("foo.txt").getAbsoluteFile().getParent();
            String aboutContent = "<ul>" +
                "<li>Java version: " + javaVersion +
                "<li>max memory (Mb): " + mem +
                "<li>total memory (Mb): " + tmem +
                "<li>free memory (Mb): " + fmem +
                "<li>arch: " + arch +
                "<li>pwd: " + pwd +
                "</ul>";
            buffy.append( aboutContent );
            
            buffy.append("</html>");


            JTextPane jtp= new JTextPane();
            jtp.setContentType("text/html");
            jtp.read(new StringReader(buffy.toString()), null);
            jtp.setEditable(false);

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

            //JLabel label= new JLabel(buffy.toString());
            JScrollPane pane= new JScrollPane(jtp,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED );
            pane.getVerticalScrollBar().setUnitIncrement( 12 );
            pane.setPreferredSize(new java.awt.Dimension(jtp.getPreferredSize().width + 50,480));

            AutoplotUtil.showMessageDialog(this, pane, "About Autoplot "+AboutUtil.getReleaseTag(), JOptionPane.INFORMATION_MESSAGE );

        } catch (IOException ex) {
            logger.log( Level.SEVERE, "", ex );
        }

    }//GEN-LAST:event_aboutAutoplotMenuItemActionPerformed

    private void aboutDas2MenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aboutDas2MenuItemActionPerformed
        AutoplotUtil.openBrowser("http://das2.org");
    }//GEN-LAST:event_aboutDas2MenuItemActionPerformed

    private void autoplotHomepageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autoplotHomepageButtonActionPerformed
        AutoplotUtil.openBrowser("http://autoplot.org/");
}//GEN-LAST:event_autoplotHomepageButtonActionPerformed

    private void helpMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_helpMenuActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_helpMenuActionPerformed

private void scriptPanelMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_scriptPanelMenuItemActionPerformed
    applicationModel.getDocumentModel().getOptions().setScriptVisible(scriptPanelMenuItem.isSelected());
    if (scriptPanelMenuItem.isSelected() && jythonScriptPanel == null) {
        jythonScriptPanel= new JPanel( new BorderLayout() );
        scriptPanel = new JythonScriptPanel(applicationModel, this.dataSetSelector);
        jythonScriptPanel.add(scriptPanel, BorderLayout.CENTER );
        tabs.insertTab(TAB_SCRIPT, null, jythonScriptPanel,
                String.format(  TAB_TOOLTIP_SCRIPT, TABS_TOOLTIP), 4);
    } else if ( scriptPanelMenuItem.isSelected() && jythonScriptPanel!=null ) {
        tabs.insertTab(TAB_SCRIPT, null, jythonScriptPanel,
                String.format(  TAB_TOOLTIP_SCRIPT, TABS_TOOLTIP), 4);
    } else {
        tabs.remove( jythonScriptPanel );
    }
}//GEN-LAST:event_scriptPanelMenuItemActionPerformed

private void logConsoleMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_logConsoleMenuItemActionPerformed
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
}//GEN-LAST:event_logConsoleMenuItemActionPerformed

private void serverCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_serverCheckBoxMenuItemActionPerformed
    applicationModel.getDocumentModel().getOptions().setServerEnabled(serverCheckBoxMenuItem.isSelected());
    if (applicationModel.getDocumentModel().getOptions().isServerEnabled()) {
        initServer();
    } else {
        stopServer();
        JOptionPane.showMessageDialog( rootPane, "<html>The server will not be stopped completely.<br>https://sourceforge.net/tracker/?func=detail&aid=3441071&group_id=199733&atid=970682" );
    }
    serverCheckBoxMenuItem.setSelected( rlistener!=null );
    applicationModel.getDocumentModel().getOptions().setServerEnabled( rlistener!=null );
}//GEN-LAST:event_serverCheckBoxMenuItemActionPerformed

private void jMenuItem1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem1ActionPerformed
    applicationModel.increaseFontSize();
}//GEN-LAST:event_jMenuItem1ActionPerformed

private void jMenuItem2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem2ActionPerformed
    applicationModel.decreaseFontSize();
}//GEN-LAST:event_jMenuItem2ActionPerformed

private void jMenuItem3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem3ActionPerformed
    final JDiskHogPanel panel = new JDiskHogPanel( this );
    JDialog dia = new JDialog(this, "Manage Cache", true);
    dia.add(panel);
    dia.pack();
    RequestProcessor.invokeLater( new Runnable() {
        public void run() {
            panel.scan( new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_FSCACHE ) ) );
        }
    });
    dia.setLocationRelativeTo( this );
    dia.setVisible(true);
    if ( panel.isGoPressed() ) {
        panel.doPlotSelected();
    }
//JOptionPane.showMessageDialog(this, plotElement, "Manage Cache", JOptionPane.DEFAULT_OPTION, null);
}//GEN-LAST:event_jMenuItem3ActionPerformed

private void jMenuItem4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem4ActionPerformed
    clearCache();
}//GEN-LAST:event_jMenuItem4ActionPerformed

private void jMenuItem5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem5ActionPerformed
    try {
        String release = AboutUtil.getReleaseTag();
        if (release != null) {
            String surl = "http://autoplot.org/Autoplot_Change_Log#" + release;
            AutoplotUtil.openBrowser(surl);
        } else {
            JOptionPane.showMessageDialog(this, "This is an untagged release.");
        }
    } catch (IOException ex) {
        throw new RuntimeException(ex);
    }
}//GEN-LAST:event_jMenuItem5ActionPerformed

private void editDomMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editDomMenuItemActionPerformed
    PropertyEditor edit= new PropertyEditor(applicationModel.dom);
    edit.showDialog(this,"DOM Properties",new ImageIcon(this.getClass().getResource("logoA16x16.png")).getImage());
}//GEN-LAST:event_editDomMenuItemActionPerformed

private void statusLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_statusLabelMouseClicked
    //statusLabel.setText("");
    statusLabel.setIcon(null);
    statusTextField.setText("");
}//GEN-LAST:event_statusLabelMouseClicked

private void inspectVapFileMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_inspectVapFileMenuItemActionPerformed
    this.support.doInspectVap();
}//GEN-LAST:event_inspectVapFileMenuItemActionPerformed

private void autoLayoutCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autoLayoutCheckBoxMenuItemActionPerformed
    if ( autoLayoutCheckBoxMenuItem.isSelected() ) {
        applicationModel.doAutoLayout();
    }
}//GEN-LAST:event_autoLayoutCheckBoxMenuItemActionPerformed

private void autoplotHelpMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autoplotHelpMenuItemActionPerformed
    helpSystem.displayHelpFromEvent(evt);
}//GEN-LAST:event_autoplotHelpMenuItemActionPerformed

private void pngWalkMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pngWalkMenuItemActionPerformed
   PngWalkTool1.start( null, this);
}//GEN-LAST:event_pngWalkMenuItemActionPerformed

private void createPngWalkMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_createPngWalkMenuItemActionPerformed
    //JythonUtil.invokeScriptSoon( AutoplotUI.class.getResource("/scripts/pngwalk/makePngWalk.jy"), applicationModel.dom, null );
    Runnable run= new Runnable() {
        public void run() {
            try {
                CreatePngWalk.doIt( applicationModel.dom, null );
            } catch ( IOException ex ) {
                setStatus( AutoplotUI.ERROR_ICON,"Unable to create PNG Walk: " + ex.getMessage() );
                applicationModel.showMessage( "<html>Unable to create PNG Walk:<br>"+ex.getMessage(), "PNG Walk Error", JOptionPane.WARNING_MESSAGE );
                logger.log( Level.SEVERE, "", ex );
            } catch ( Exception ex) {
                logger.log( Level.SEVERE, "", ex );
                throw new RuntimeException(ex);
                // this mimics the jython behavior
            }
        }
    };
    RequestProcessor.invokeLater(run);
}//GEN-LAST:event_createPngWalkMenuItemActionPerformed

private void aggregateMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aggregateMenuItemActionPerformed
    AggregateUrisDialog dia= new AggregateUrisDialog( dom, dataSetSelector );
    //JOptionPane.showConfirmDialog( rootPane, dia, "Aggregate URIs", JOptionPane.OK_CANCEL_OPTION ); //TODO: OKAY button is confusing, but how to hide it?
    dia.showDialog();
    //AggregateUrisDialog2 dia= new AggregateUrisDialog2( dom, dataSetSelector );
    //if ( JOptionPane.OK_OPTION==JOptionPane.showConfirmDialog( rootPane, dia, "Aggregate URIs", JOptionPane.OK_CANCEL_OPTION ) ) { //TODO: OKAY button is confusing, but how to hide it?
    //    dia.doAction();
    //}
}//GEN-LAST:event_aggregateMenuItemActionPerformed

private void decodeURLItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_decodeURLItemActionPerformed
    String s = dataSetSelector.getEditor().getText();
    s = org.virbo.datasource.DataSourceUtil.unescape(s);
    dataSetSelector.getEditor().setText(s);
}//GEN-LAST:event_decodeURLItemActionPerformed

private void statusTextFieldMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_statusTextFieldMouseClicked
    statusLabelMouseClicked(evt);
}//GEN-LAST:event_statusTextFieldMouseClicked

private void gettingStartedMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_gettingStartedMenuItemActionPerformed
    GettingStartedPanel gettingStartedPanel= new GettingStartedPanel();

    int result= JOptionPane.showConfirmDialog( this, gettingStartedPanel, "Getting Started", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE );
    if ( result==JOptionPane.OK_OPTION ) {
        String uri= gettingStartedPanel.getInitialUri().trim();
        if ( uri.length()>0 ) this.plotUri(uri);
    }
}//GEN-LAST:event_gettingStartedMenuItemActionPerformed

private void exceptionReportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exceptionReportActionPerformed
    ExceptionHandler eh= applicationModel.getExceptionHandler();
    if ( eh==null || !( eh instanceof GuiExceptionHandler ) ) {
        new GuiExceptionHandler().submitRuntimeException(new RuntimeException("user-generated comment"), false);
    } else {
        ((GuiExceptionHandler)eh).submitRuntimeException(new RuntimeException("user-generated comment"), false);
    }
}//GEN-LAST:event_exceptionReportActionPerformed

private void jMenuItem7ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem7ActionPerformed
    MoveCacheDialog dia= new MoveCacheDialog();
    if ( JOptionPane.showConfirmDialog( this, dia, "Move Cache", JOptionPane.OK_CANCEL_OPTION )==JOptionPane.OK_OPTION ) {
        final String newv= dia.getNewDir().getText();
        if ( !newv.equals( AutoplotSettings.settings().getFscache() ) ) {
            Runnable run= new Runnable() {
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
    CanvasSizePanel p= new CanvasSizePanel();
    p.getResizeRadioButton().setSelected( dom.getCanvases(0).isFitted() );
    p.getHeightTextField().setValue( dom.getCanvases(0).getHeight() );
    p.getWidthTextField().setValue( dom.getCanvases(0).getWidth() );
    if ( JOptionPane.showConfirmDialog( this,p,"Set Canvas Size",JOptionPane.OK_CANCEL_OPTION )==JOptionPane.OK_OPTION ) {
        if ( p.getResizeRadioButton().isSelected() ) {
            dom.getCanvases(0).setFitted(true);
        } else {
            dom.getCanvases(0).setWidth( (Integer)p.getWidthTextField().getValue() );
            dom.getCanvases(0).setHeight( (Integer)p.getHeightTextField().getValue() );
            dom.getCanvases(0).setFitted(false);
        }
    }
}//GEN-LAST:event_canvasSizeMenuItemActionPerformed

private void dataSetSelectorMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSetSelectorMenuItemActionPerformed
    if ( dataSetSelectorMenuItem.isSelected() ) {
        ((CardLayout)timeRangePanel.getLayout()).show( timeRangePanel, CARD_DATA_SET_SELECTOR);
        dom.getOptions().setUseTimeRangeEditor(false);
    }
}//GEN-LAST:event_dataSetSelectorMenuItemActionPerformed

private void timeRangeSelectorMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeRangeSelectorMenuItemActionPerformed
    if ( timeRangeSelectorMenuItem.isSelected() ) {
        ((CardLayout)timeRangePanel.getLayout()).show( timeRangePanel, CARD_TIME_RANGE_SELECTOR);
        dom.getOptions().setUseTimeRangeEditor(true);
    }
}//GEN-LAST:event_timeRangeSelectorMenuItemActionPerformed

private void EditOptionsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_EditOptionsActionPerformed
    PropertyEditor edit= new PropertyEditor(applicationModel.dom.getOptions());
    edit.showDialog(this,"DOM User Options",new ImageIcon(this.getClass().getResource("logoA16x16.png")).getImage());
}//GEN-LAST:event_EditOptionsActionPerformed

private void fixLayoutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fixLayoutMenuItemActionPerformed
    Runnable run= new Runnable() {
        public void run() {
            org.virbo.autoplot.dom.DomOps.newCanvasLayout(dom);
        }
    };
    new Thread(run,"canvas layout").start();
}//GEN-LAST:event_fixLayoutMenuItemActionPerformed

private void resetXMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetXMenuItemActionPerformed
    if ( !AutoplotUtil.resetZoomX(dom)) {
        System.err.println("unable to zoom x");
    }
}//GEN-LAST:event_resetXMenuItemActionPerformed

private void resetYMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetYMenuItemActionPerformed
    if ( !AutoplotUtil.resetZoomY(dom)) {
        System.err.println("unable to zoom y");
    }
}//GEN-LAST:event_resetYMenuItemActionPerformed

private void resetZMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetZMenuItemActionPerformed
    if ( !AutoplotUtil.resetZoomZ(dom)) {
        System.err.println("unable to zoom z");
    }
}//GEN-LAST:event_resetZMenuItemActionPerformed

private void replaceFileMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_replaceFileMenuItemActionPerformed
    final Component source= (Component)evt.getSource();
    Runnable run= new Runnable() {
        public void run() {
            AutoplotUtil.replaceFile( source,dom );
        }
    };
    RequestProcessor.invokeLater(run);
}//GEN-LAST:event_replaceFileMenuItemActionPerformed

private void reloadAllMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadAllMenuItemActionPerformed
   // Reload All Data
    RequestProcessor.invokeLater( new Runnable() { public void run() {
        AutoplotUtil.reloadAll(dom);
    } } );
}//GEN-LAST:event_reloadAllMenuItemActionPerformed

private void workOfflineCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_workOfflineCheckBoxMenuItemActionPerformed
    final boolean workOffline= workOfflineCheckBoxMenuItem.isSelected();
    FileSystem.settings().setOffline( workOffline );
    RequestProcessor.invokeLater( new Runnable() { public void run() {
        FileSystem.reset();
        setMessage( workOffline ? "Now working offline" : "Working online");
    } } );
}//GEN-LAST:event_workOfflineCheckBoxMenuItemActionPerformed

private void searchToolTipsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchToolTipsMenuItemActionPerformed
    AutoplotUtil.doSearchToolTips(this);
}//GEN-LAST:event_searchToolTipsMenuItemActionPerformed

private void manageFilesystemsMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_manageFilesystemsMIActionPerformed
    AutoplotUtil.doManageFilesystems(this);
}//GEN-LAST:event_manageFilesystemsMIActionPerformed

private void resetMemoryCachesMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetMemoryCachesMIActionPerformed
    logger.fine("Resetting FileSystems...");
    Runnable run= new Runnable() {
        public void run() {
           ReferenceCache.getInstance().reset(); // you just have to know this is what it is doing for now...
           FileSystem.reset();
           setMessage("FileSystem memory caches reset");
        }
    };
    RequestProcessor.invokeLater(run);
}//GEN-LAST:event_resetMemoryCachesMIActionPerformed

private transient PropertyChangeListener optionsListener= new PropertyChangeListener() {
    public void propertyChange( PropertyChangeEvent ev ) {
        if ( ev.getPropertyName().equals(Options.PROP_LAYOUTVISIBLE) ) {
            if ( Boolean.TRUE.equals(ev.getNewValue()) ) {
                if ( layoutPanel == null ) {
                    layoutPanel = new LayoutPanel();
                    layoutPanel.setApplication(dom);
                }
                int idx= tabs.indexOfTab("style");
                if ( idx==-1 ) idx=  tabs.getTabCount();
                JScrollPane jsp = new JScrollPane();
                jsp.setViewportView(layoutPanel);
                tabs.insertTab("layout", null, jsp,
                        String.format( TAB_TOOLTIP_LAYOUT, TABS_TOOLTIP ), idx+1 );
            } else {
                if ( layoutPanel!=null ) tabs.remove(layoutPanel.getParent().getParent());
            }
        } else if ( ev.getPropertyName().equals(Options.PROP_DATAVISIBLE ) ) {
            if ( Boolean.TRUE.equals(ev.getNewValue()) ) {
                if ( dataPanel == null ) {
                    dataPanel = new DataPanel(applicationModel.dom);
                }
                int idx= tabs.indexOfTab("metadata");
                if ( idx==-1 ) idx=  tabs.getTabCount();
                JScrollPane jsp = new JScrollPane();
                jsp.setViewportView(dataPanel);
                tabs.insertTab("data", null, jsp,
                        String.format( TAB_TOOLTIP_DATA, TABS_TOOLTIP ), idx );
            } else {
                if ( dataPanel!=null ) tabs.remove(dataPanel.getParent().getParent());
            }
        } else if ( ev.getPropertyName().equals(Options.PROP_USE_TIME_RANGE_EDITOR ) ) {
            if ( Boolean.TRUE.equals(ev.getNewValue()) ) {
                ((CardLayout)timeRangePanel.getLayout()).show( timeRangePanel, CARD_TIME_RANGE_SELECTOR );
            } else {
                ((CardLayout)timeRangePanel.getLayout()).show( timeRangePanel, CARD_DATA_SET_SELECTOR );
            }
        }
    }
};

private void updateFrameTitle() {
    final String suri= applicationModel.getVapFile();

    String v= APSplash.getVersion();
    if ( v.equals("untagged_version") ) {
        v= "(dev)";
    }
    final String title0= "Autoplot "+v;
    final String isoffline= FileSystem.settings().isOffline() ? " (offline)" : "";

    final String theTitle;
    if ( suri==null ) {
        theTitle=  title0 + isoffline;
    } else {
        URISplit split= URISplit.parse(suri);

        boolean dirty= undoRedoSupport.getDepth()>1;
        if ( split.path!=null && split.file!=null ) {
            String titleStr= split.file.substring( split.path.length() ) + ( dirty ? "*" : "" );
            theTitle= titleStr + " - " + title0 + isoffline;
        } else {
            //I was seeing null pointer exceptions here--see rte_1590234331_20110328_153705_wsk.xml.  I suspect this is Windows.
            logger.log(Level.WARNING, "Unable to get path from: {0}", suri);
            theTitle= "???" + " - " + title0 + isoffline;
        }
    }
    Runnable run= new Runnable() {
        public void run() {
            setTitle( theTitle );
        }
    };
    SwingUtilities.invokeLater(run);
}

    /**
     * raise the application window
     * http://stackoverflow.com/questions/309023/howto-bring-a-java-window-to-the-front
     */
    public static void raiseApplicationWindow( java.awt.Frame frame ) {
        GuiSupport.raiseApplicationWindow(frame);
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

            public void newActivation(String[] argv) {
                System.err.println( "single instance listener argv:" );
                for ( int i=0; i<argv.length; i++ ) {
                    System.err.printf( " argv[%d]: %s\n", i, argv[i] );
                }

                for ( int i=0; i<argv.length; i++ ) {  // kludge for java webstart, which uses "-open" not "--open"
                   if ( argv[i].equals("-print") ) argv[i]="--print";
                   if ( argv[i].equals("-open") ) argv[i]="--open";
                }

                alm.process(argv);
                boolean raise= false;

                final JFrame frame = (JFrame) ScriptContext.getViewWindow();
                if ( frame!=null ) {
                     raiseApplicationWindow(frame);
                }

                String url;
                if (alm.getValue("URI") != null) {
                    url = alm.getValue("URI");
                    logger.log(Level.FINE, "setting initial URI to >>>{0}<<<", url );
                } else if ( alm.getValue("open") !=null ) {
                    url = alm.getValue("open");
                    logger.log(Level.FINE, "setting initial URI to >>>{0}<<<", url );
                } else {
                    url = null;
                }

                if ( url!=null && ( url.startsWith("pngwalk:") || url.endsWith(".pngwalk") || url.contains(".pngwalk?") ) ) {
                    //TODO: check other prefixes...
                    PngWalkTool1.start( url, app );
                    app.applicationModel.addRecent(app.dataSetSelector.getValue());
                    return;
                }

                String pos= alm.getValue("position");

                if ( pos!=null ) {
                    app.applicationModel.setFocus( Integer.parseInt(pos) );
                    if ( url!=null ) app.dataSetSelector.setValue(url);
                    app.dataSetSelector.maybePlot(false); // allow for completions
                    
                } else {
                    if (url == null) {
                        int action = JOptionPane.showConfirmDialog(ScriptContext.getViewWindow(), "<html>Autoplot is already running.<br>Start another window?", "Reenter Autoplot", JOptionPane.YES_NO_OPTION);
                        if (action == JOptionPane.YES_OPTION) {
                            app.support.newApplication();
                        } else {
                            raise= true;
                        }
                    } else {
                        String msg;
                        if ( app.isExpertMode() ) {
                                msg= String.format(
                                "<html>Autoplot is already running. Autoplot can use this address in a new window, <br>"
                                + "or replace the current plot with the new URI, possibly entering the editor, <br>"
                                + "or always enter the editor to inspect and insert the plot below.<br>"
                                + "Replace or Inspect, replacing data with<br>%s?", url );
                        } else {
                                msg= String.format(
                                "<html>Autoplot is already running. Autoplot can use this address in a new window, <br>"
                                + "or replace the current plot with the new URI, possibly entering the editor <br>"
                                + "or always enter the editor to inspect before plotting.<br>"
                                + "Replace or Inspect, replacing data with<br>%s?", url );
                        }
                        String action = (String) JOptionPane.showInputDialog( ScriptContext.getViewWindow(),
                                msg,
                                "Replace URI", JOptionPane.QUESTION_MESSAGE, new javax.swing.ImageIcon(getClass().getResource("/logo64x64.png")),
                                new String[] { "New Window", "Replace", "Add Plot" }, "Add Plot" );
                        if ( action!=null ) {
                            if (action.equals("Replace")) {
                                app.dataSetSelector.setValue(url);
                                app.dataSetSelector.maybePlot(false); // allow for completions
                                raise= true;
                            } else if (action.equals("Add Plot")) {
                                app.dataSetSelector.setValue(url);
                                app.dataSetSelector.maybePlot( KeyEvent.ALT_MASK ); // enter the editor
                                raise= true;
                            } else if (action.equals("New Window")) {
                                ApplicationModel nmodel = app.support.newApplication();
                                DataSetSelector sel= ((AutoplotUI)nmodel.application.getMainFrame()).dataSetSelector;
                                sel.setValue(url);
                                sel.maybePlot(false);
                                nmodel.setDataSourceURL(url);
                            }
                        } else {
                            raise= true;
                        }
                    }
                }
                if ( raise ) {
                    if ( frame!=null ) {
                        EventQueue.invokeLater(new Runnable() {
                            public void run() {
                                raiseApplicationWindow(frame);
                            }
                        });
                    }
                }
            }
        };
        sis.addSingleInstanceListener(sisL);
    }


    private static Runnable getRunScriptRunnable( final AutoplotUI app, 
            final ApplicationModel model, final String script, 
            final List<String> scriptArgs, final boolean quit  ) {
        Runnable r= new Runnable() {
            @Override
            public String toString() { return "runScriptRunnable"; }
            public void run() {
                try {
                    ScriptContext.setApplicationModel(model); // initialize
                    JythonUtil.runScript( model, script, scriptArgs.toArray(new String[scriptArgs.size()]) );
                    if ( app!=null ) app.setStatus( READY_MESSAGE );
                    if ( quit ) { //TODO: headless doesn't seem to work
                        AppManager.getInstance().quit();
                    }
                } catch (IOException ex) {
                    throw new IllegalArgumentException(ex);
                } catch ( Exception ex ) {
                    if ( quit ) {
                        ex.printStackTrace();
                        AppManager.getInstance().quit(1);
                    }
                }
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

        final ArgumentList alm = new ArgumentList("AutoplotUI");
        alm.addOptionalPositionArgument(0, "URI", null, "initial URI to load");
        alm.addOptionalSwitchArgument("position", null, "position", null, "plot position for the URI, an integer indicating which data position to update.");
        alm.addOptionalSwitchArgument("bookmarks", null, "bookmarks", null, "bookmarks to load");
        alm.addOptionalSwitchArgument("port", "p", "port", "-1", "enable scripting via this port");
        alm.addBooleanSwitchArgument("scriptPanel", "s", "scriptPanel", "enable script panel");
        alm.addBooleanSwitchArgument("logConsole", "l", "logConsole", "enable log console");
        alm.addOptionalSwitchArgument("nativeLAF", "n", "nativeLAF", alm.TRUE, "use the system look and feel (T or F)");
        alm.addOptionalSwitchArgument("open", "o", "open", null, "open this URI (to support javaws)");
        alm.addOptionalSwitchArgument("print", null, "print", "", "print this URI (to support javaws)");
        alm.addOptionalSwitchArgument("script", null, "script", "", "run this script after starting.  " +
                "Arguments following are " +
                "passed into the script as sys.argv");
        alm.addOptionalSwitchArgument("autoLayout",null,"autoLayout",alm.TRUE,"turn on/off initial autolayout setting");
        alm.addOptionalSwitchArgument("mode","m","mode","expert","start in basic (browse,reduced) mode or expert mode" );
        //alm.addOptionalSwitchArgument("exit", null, "exit", "0", "exit after running script" );
        alm.addBooleanSwitchArgument( "eventThreadMonitor", null, "eventThreadMonitor", "monitor the event thread for long unresponsive pauses");

        alm.addBooleanSwitchArgument( "samp", null, "samp", "enable SAMP connection for use with the Cluster Final Archive");
       for ( int i=0; i<args.length; i++ ) {  // kludge for java webstart, which uses "-open" not "--open"
           if ( args[i].equals("-print") ) args[i]="--print";
           if ( args[i].equals("-open") ) args[i]="--open";
        }

        final List<String> scriptArgs= new ArrayList<String>();

        for ( int i=1; i<args.length; i++ ) { // grab any arguments after --script and hide them from the processor.
            if ( args.length>i && args[i-1].startsWith("--script") ) {
                List<String> apArgs= new ArrayList<String>();
                if ( args[i-1].length()>8 && args[i-1].charAt(8)=='=' ) {
                    for ( int j=0; j<i; j++ ) {
                        apArgs.add(args[j]);
                    }
                    for ( int j=i; j<args.length; j++ ) {
                        scriptArgs.add(args[j]);
                    }        
                } else {
                    for ( int j=0; j<=i; j++ ) {
                        apArgs.add(args[j]);
                    }        
                    for ( int j=i+1; j<args.length; j++ ) {
                        scriptArgs.add(args[j]);
                    }
                }
                args= apArgs.toArray( new String[ apArgs.size() ] );
                break;
            }
        }
        alm.process(args);

        AutoplotUtil.maybeLoadSystemProperties();
                        
        String welcome= "welcome to autoplot";
        String tag;
        try {
            tag = AboutUtil.getReleaseTag(APSplash.class);
            if ( tag!=null ) {
                welcome+=" ("+tag+")";
                System.setProperty("http.agent", "Autoplot "+tag );
            } else {
                System.setProperty("http.agent", "Autoplot dev" );
            }
            
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }

        System.err.println(welcome);
        logger.info(welcome);
        final ApplicationModel model = new ApplicationModel();
        final String initialURL;
        final String bookmarks;

        if (alm.getValue("URI") != null) {
            initialURL = alm.getValue("URI");
            logger.log(Level.FINE, "setting initial URI to >>>{0}<<<", initialURL);
        } else if ( alm.getValue("open") !=null ) {
            initialURL = alm.getValue("open");
            logger.log(Level.FINE, "setting initial URI to >>>{0}<<<", initialURL);
        } else {
            initialURL = null;
        }

        // it's easy to forget the -- in --open=. Check for this and give a nice error.
        if ( initialURL!=null ) {
            if ( initialURL.startsWith("open=") ) {
                JOptionPane.showMessageDialog( null, "<html>open= switch is missing -- prefix: should be<br>--"+initialURL, "open= switch is missing -- ", JOptionPane.ERROR_MESSAGE );
            }
        }

        bookmarks= alm.getValue("bookmarks");

        if (alm.getBooleanValue("scriptPanel")) {
            logger.fine("enable scriptPanel");
            model.getDocumentModel().getOptions().setScriptVisible(true);
        }

        if (alm.getBooleanValue("logConsole")) {
            logger.fine("enable scriptPanel");
            model.getDocumentModel().getOptions().setLogConsoleVisible(true);
        }

        final boolean headless=  "true".equals( System.getProperty("java.awt.headless") ) ;

        if ( !headless && alm.getBooleanValue("nativeLAF")) {
            logger.fine("nativeLAF");
            try {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                logger.log( Level.SEVERE, "", e );
            }
        }

        logger.fine("invokeLater()");

        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public String toString() {
                return "initAutoplotRunnable";
            }

            public void run() {
                //long t0= System.currentTimeMillis();

                logger.fine("enter invokeLater");

                if ( ! headless ) {
                    logger.addHandler( APSplash.getInstance().getLogHandler() );
                    APSplash.showSplash();
                } else {
                    System.err.println("this is autoplot "+APSplash.getVersion());
                }
APSplash.checkTime("init -100");
                OptionsPrefsController opc= new OptionsPrefsController( model.dom.getOptions() );
                opc.loadPreferences();

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

                    Preferences prefs= Preferences.userNodeForPackage( AutoplotUI.class );
                    int posx= prefs.getInt( "locationx", app.getLocation().x );
                    int posy= prefs.getInt( "locationy", app.getLocation().y );
                    if ( posx!= app.getLocation().x || posy!=app.getLocation().y ) {
                        boolean scncheck= java.awt.Toolkit.getDefaultToolkit().getScreenSize().width==prefs.getInt( "locationscreenwidth", 0 );
                        if ( scncheck ) { // don't position if the screen size changes.
                            app.setLocation( posx, posy );
                        }
                    }
APSplash.checkTime("init 200");
                    boolean addSingleInstanceListener= true;
                    if ( addSingleInstanceListener ) {
                        addSingleInstanceListener( alm, app );
                    }
                    if ( alm.getBooleanValue("samp") ) {
                        //JythonUtil.invokeScriptSoon( AutoplotUI.class.getResource("/scripts/addCfaListener.jy") );
                        org.autoplot.external.AddCfaSampListener.addCfaSampListener( app.dataSetSelector );
                    }

                }

APSplash.checkTime("init 210");
                final boolean server= !alm.getValue("port").equals("-1");
                if ( server ) {
                    if ( app==null ) {
                        throw new IllegalArgumentException("Server cannot be used in headless mode");
                    }
                    int iport;
                    iport = Integer.parseInt(alm.getValue("port"));
                    app.setupServer(iport, model);
                    model.getDocumentModel().getOptions().setServerEnabled(true);
                }

                Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {

                    public void uncaughtException(Thread t, Throwable e) {
//                        logger.severe("runtime exception: " + e);
                        logger.log(Level.SEVERE, "runtime exception: " + e, e);

                        app.setStatus(ERROR_ICON,"caught exception: " + e.toString());
                        if (e instanceof InconvertibleUnitsException) {
                            // do nothing!!!  this is associated with the state change
                            return;
                        }
                        model.getExceptionHandler().handleUncaught(e);
                    }
                });

APSplash.checkTime("init 220");
                if ( !headless ) {
                    logger.fine("UI.setVisible(true)");
                    SwingUtilities.invokeLater( new Runnable() {
                        @Override
                        public String toString() {
                            return "repaintRunnable";
                        }
                        public void run() {
                            if ( app!=null ) {
                                app.applicationModel.canvas.repaint();
                                if ( initialURL==null || ! ( initialURL.startsWith("pngwalk:") 
                                        || initialURL.endsWith(".pngwalk") 
                                        || initialURL.contains(".pngwalk?" ) ) ) app.setVisible(true);
                            }
                            if ( alm.getBooleanValue("eventThreadMonitor") ) new EventThreadResponseMonitor().start();
                        }
                    } );


                    logger.fine("UI is visible");
                    APSplash.hideSplash();
                    logger.removeHandler( APSplash.getInstance().getLogHandler() );

                    if ( alm.getValue("mode").equals("basic") ) {
                        if ( app!=null ) app.setExpertMode(false);
                    }

                }
APSplash.checkTime("init 230");
                if ( !headless && initialURL!=null) {
                    if ( app!=null ) app.dataSetSelector.setValue(initialURL);
                    if ( app!=null ) app.dataSetSelector.maybePlot(false);
                }
                
                if ( bookmarks!=null ) {
                    if ( app!=null ) app.initialBookmarksUrl= bookmarks;
                }

                final String script= alm.getValue("script");
                if ( !script.equals("") ) {
                    if ( app!=null ) app.setStatus("running script "+script);
                    Runnable run= getRunScriptRunnable( app, model, script, scriptArgs, headless && !server );
                    new Thread(run,"batchRunScriptThread").start();
                } else {
APSplash.checkTime("init 240");
                    if ( app!=null ) app.setStatus( READY_MESSAGE );
                }

                //System.err.println("initAutoplot took (ms): "+(System.currentTimeMillis()-t0) );
            }
        });
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
            logger.log(Level.SEVERE, null, ex);
        }
        applicationModel.getCanvas().setDropTarget(dropTarget);
        for ( DasCanvasComponent cc: applicationModel.getCanvas().getCanvasComponents() ) {
            if ( cc instanceof DasPlot ) { // we need to add to existing plots.
                DropTarget dropTarget1 = new DropTarget();
                dropTarget1.setComponent(cc);
                try {
                    dropTarget1.addDropTargetListener( dropListener );
                } catch (TooManyListenersException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
                cc.setDropTarget(dropTarget1);
            }
        }

        applicationModel.dom.getCanvases(0).getController().setDropTargetListener(dropListener);

    }

    public DropTargetListener getDropTargetListener() {
        return dropListener;
    }

    /**
     * initializes a SocketListener that accepts jython scripts that affect
     * the application state.  This implements the "--port" option.
     * @param port
     * @param model
     */
    private void setupServer(int port, final ApplicationModel model) {

        rlistener = new RequestListener();
        rlistener.setPort(port);
        final RequestHandler rhandler = new RequestHandler();

        rlistener.addPropertyChangeListener(RequestListener.PROP_REQUESTCOUNT, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                try {
                    if ( rlistener==null ) {
                        logger.log( Level.FINE, "the server is no longer listening");
                        return;
                    }
                    Socket socket= rlistener.getSocket();
                    logger.log(Level.FINE, "connection from {0}", socket);
                    rhandler.handleRequest( socket.getInputStream(), model, socket.getOutputStream());
                } catch (IOException ex) {
                    logger.log( Level.SEVERE, "", ex );
                }
            }
        });
        rlistener.startListening();
    }

    public void setMessage(String message) {
        setMessage( IDLE_ICON, message );
    }
    
    public void setMessage( Icon icon, String message ) {
        if ( message==null ) message= "<null>"; // TODO: fix this later
        String myMess= message;
        //if ( myMess.length()>100 ) myMess= myMess.substring(0,100)+"...";
        myMess= myMess.replaceAll("\n","");

        final String fmyMess= myMess;
        final String fmessage= message;
        final Icon ficon= icon;

        SwingUtilities.invokeLater( new Runnable() {  //TODO: we should be a little careful here, we don't want to post thousands of runnables to the event thread.
            public void run() {
                try {
                    statusLabel.setIcon( ficon );
                    statusTextField.setText(fmyMess);
                    statusTextField.setToolTipText(fmessage);
                } catch ( Exception e ) {
                    logger.log( Level.SEVERE, "", e ); // rte_0759798375_20121111_205149_*.xml
                }
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem EditOptions;
    private javax.swing.JMenuItem aboutAutoplotMenuItem;
    private javax.swing.JMenuItem aboutDas2MenuItem;
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
    protected org.virbo.datasource.DataSetSelector dataSetSelector;
    private javax.swing.JRadioButtonMenuItem dataSetSelectorMenuItem;
    private javax.swing.JMenuItem decodeURLItem;
    private javax.swing.JCheckBoxMenuItem doyCB;
    private javax.swing.JCheckBoxMenuItem drawAntiAliasMenuItem;
    private javax.swing.JCheckBoxMenuItem drawGridCheckBox;
    private javax.swing.JMenuItem editDomMenuItem;
    private javax.swing.JSeparator editDomSeparator;
    private javax.swing.JMenu editMenu;
    private javax.swing.JMenu enableFeatureMenu;
    private javax.swing.JMenuItem exceptionReport;
    private javax.swing.JMenu fileMenu;
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
    private javax.swing.JMenuItem jMenuItem7;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JPopupMenu.Separator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JPopupMenu.Separator jSeparator4;
    private javax.swing.JCheckBoxMenuItem layoutPanelCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem logConsoleMenuItem;
    private javax.swing.JMenuItem manageFilesystemsMI;
    private javax.swing.JCheckBoxMenuItem nnCb;
    private javax.swing.JMenu optionsMenu;
    private javax.swing.JCheckBoxMenuItem overRenderingMenuItem;
    private javax.swing.JMenuItem pasteDataSetURLMenuItem;
    private org.das2.dasml.PersistentStateSupport persistentStateSupport1;
    private javax.swing.JMenu plotStyleMenu;
    private javax.swing.JMenuItem pngWalkMenuItem;
    private javax.swing.JMenuItem redoMenuItem;
    private javax.swing.JMenuItem reloadAllMenuItem;
    private javax.swing.JMenu renderingOptionsMenu;
    private javax.swing.JMenuItem replaceFileMenuItem;
    private javax.swing.JMenuItem resetMemoryCachesMI;
    private javax.swing.JMenuItem resetXMenuItem;
    private javax.swing.JMenuItem resetYMenuItem;
    private javax.swing.JMenuItem resetZMenuItem;
    private javax.swing.JMenu resetZoomMenu;
    private javax.swing.JMenuItem resetZoomMenuItem;
    private javax.swing.JCheckBoxMenuItem scriptPanelMenuItem;
    private javax.swing.JMenuItem searchToolTipsMenuItem;
    private javax.swing.JCheckBoxMenuItem serverCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem specialEffectsMenuItem;
    private javax.swing.JLabel statusLabel;
    private javax.swing.JTextField statusTextField;
    private javax.swing.JPanel tabbedPanelContainer;
    private javax.swing.JCheckBoxMenuItem textAntiAlias;
    private javax.swing.JMenu textSizeMenu;
    private javax.swing.JPanel timeRangePanel;
    private javax.swing.JRadioButtonMenuItem timeRangeSelectorMenuItem;
    private javax.swing.JMenu toolsMenu;
    private javax.swing.JMenuItem undoMenuItem;
    private javax.swing.JMenu undoMultipleMenu;
    private javax.swing.JMenu viewMenu;
    private javax.swing.JCheckBoxMenuItem workOfflineCheckBoxMenuItem;
    private javax.swing.JMenuItem zoomInMenuItem;
    private javax.swing.JMenuItem zoomOutMenuItem;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration//GEN-END:variables

    private void sleep( int millis ) {
        if ( millis==0 ) return;
        try {
            if ( SwingUtilities.isEventDispatchThread() ) {
                throw new IllegalArgumentException("delay on event thread");
            }
            if ( millis==-1 ) millis= 500;
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * invoke the runnable after at least delayMillis.  If evt is true, then
     * put the runnable on the event thread after.
     * @param delayMillis -1 for default delay, 0 for none, or the positive number of milliseconds
     * @param evt true means run on the event thread.
     * @param run
     */
    private void invokeLater( final int delayMillis, final boolean evt, final Runnable run ) {
        Runnable sleepRun= new Runnable() {
            public void run() {
                sleep(delayMillis);
                if ( evt ) {
                    SwingUtilities.invokeLater(run);
                } else {
                    run.run();
                }
            }
        };
        RequestProcessor.invokeLater(sleepRun);
    }

    private void addTools() {
        RequestProcessor.invokeLater( new Runnable() {
            public void run() {
                sleep(-1);
                reloadTools();
            }
        });
    }

    private void runTool( String suri, int modifiers ) throws MalformedURLException {
        if ( !( ( modifiers & ActionEvent.CTRL_MASK ) == ActionEvent.CTRL_MASK ) ) {
            JythonUtil.invokeScriptSoon(DataSetURI.getURL(suri),applicationModel.getDocumentModel(),getStatusBarProgressMonitor("done running script") );
        } else {
            plotUri("script:"+ suri );
        }

        
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

        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                for ( Bookmark t: tools ) {
                    final Bookmark tt= t;
                    final String suri = ((Bookmark.Item) tt).getUri();
                    Action a= new AbstractAction(t.getTitle()) {
                        public void actionPerformed(ActionEvent e) {
                            try {
                                runTool( suri, e.getModifiers() );
                            } catch (MalformedURLException ex) {
                                logger.log(Level.SEVERE, null, ex);
                            }
                        }
                    };
                    JMenuItem ji= new JMenuItem(a);
                    ji.setToolTipText( "<html>"+ suri + "<br>press ctrl to inspect" );
                    toolsMenu.add( ji );
                }
            }
        } );
    }

    /**
     * load the jython scripts in the users AUTOPLOT_DATA/tools directory.
     * @return a list of bookmarks, each with the tool's URI and title set to the #LABEL in the script.
     */
    private List<Bookmark> loadTools() {
        List<Bookmark> tools= new ArrayList();
        File toolsDir= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ), "tools" );
        if ( toolsDir.exists() ) {
            File[] ff= toolsDir.listFiles();
            for ( int i=0; i<ff.length; i++ ) {
                if ( ff[i].getName().toLowerCase().endsWith(".jy") ) {
                    Bookmark book= new Bookmark.Item(ff[i].toURI().toString());
                    String toolLabel= ff[i].getName();
                    // read header comments for label and description.
                    try {
                        BufferedReader reader = new BufferedReader(new FileReader(ff[i]));
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
                        reader.close();
                    } catch (IOException iOException) {
                        logger.log( Level.SEVERE, "", iOException );
                    }
                    book.setTitle(toolLabel);
                    tools.add(book);
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
    public UndoRedoSupport getUndoRedoSupport() {
        return this.undoRedoSupport;
    }

    public DataSetSelector getDataSetSelector() {
        return this.dataSetSelector;
    }

    public TimeRangeEditor getTimeRangeEditor() {
        return this.timeRangeEditor;
    }

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
        dataSetSelector.getEditor().setEditable(expert);
        dataSetSelector.getEditor().setEnabled(expert);
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
        
        final boolean fexpert= expert;
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                if ( !fexpert ) {
                    ((CardLayout)timeRangePanel.getLayout()).show( timeRangePanel, CARD_TIME_RANGE_SELECTOR);
                    dom.getOptions().setUseTimeRangeEditor(true);
                } else {
                    ((CardLayout)timeRangePanel.getLayout()).show( timeRangePanel, CARD_DATA_SET_SELECTOR);
                    dom.getOptions().setUseTimeRangeEditor(false);
                }
            }
        } );
        

    }

    public boolean isExpertMode() {
        return expertMenuItems.get(0).isVisible()==true;
    }

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
        } catch (Exception ex) {
            logger.log( Level.SEVERE, "", ex );
            applicationModel.getExceptionHandler().handleUncaught(ex);
        }
    }

    private void askRunScript( RunScriptPanel pp, final URI resourceUri, final File ff ) throws IOException {
        int r = AutoplotUtil.showConfirmDialog(AutoplotUI.this, pp, "Load script", JOptionPane.OK_CANCEL_OPTION);
        final boolean doCpTo;
        if ( r==JOptionPane.OK_OPTION ) {
            if ( pp.getToolsCB().isSelected() ) {
                doCpTo= true;
            } else {
                doCpTo= false;
            }
            if ( scriptPanel!=null ) {
                if ( ! scriptPanel.isDirty() ) {
                    scriptPanel.loadFile(ff);
                }
            }
            applicationModel.addRecent(dataSetSelector.getValue());
            Runnable run= new Runnable() {
                public void run() {
                    if ( doCpTo  ) {
                        File tools= new File( AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA), "tools" );
                        File cpTo= new File( tools,ff.getName() );
                        try {
                        if ( !Util.copyFile( ff, cpTo ) ) {
                            setStatus("warning: unable to copy file");
                        } else {
                            setStatus("copied file to "+cpTo );
                            reloadTools();
                        }
                        File log= new File( tools, "scripts.txt" );
                        FileWriter out3 = new FileWriter( log, true );
                        TimeParser tp= TimeParser.create( TimeParser.TIMEFORMAT_Z );
                        Datum now= Units.t1970.createDatum( System.currentTimeMillis()/1000. );
                        out3.append( tp.format( now, null) + "\t" + ff.getName() + "\t" + resourceUri + "\n" );
                        out3.close();
                        } catch ( IOException ex ) {
                            logger.log( Level.WARNING,null,ex);
                        }
                    } else {

                    }
                    RunScriptPanel.runScript( applicationModel, ff, new DasProgressPanel("Running script "+ff ) );
                }
            };
            new Thread(run,"runScript").start();
        }

    }
    
    private void runScript( String script ) {
        try {
            final URISplit split= URISplit.parse(script);
            final File ff = DataSetURI.getFile(DataSetURI.getURI(script), new DasProgressPanel("downloading script"));
            final RunScriptPanel pp = new RunScriptPanel();
            pp.loadFile(ff);
            Runnable run= new Runnable() {
                public void run() {
                    try {
                        askRunScript( pp, split.resourceUri, ff );
                    } catch ( IOException ex ) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                }
            };
            SwingUtilities.invokeLater(run);
        } catch (URISyntaxException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

}
