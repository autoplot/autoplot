/*
 * AutoPlotMatisse.java
 *
 * Created on July 27, 2007, 6:32 AM
 */
package org.virbo.autoplot;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import javax.swing.Icon;
import org.virbo.autoplot.bookmarks.Bookmark;
import org.virbo.autoplot.bookmarks.BookmarksManager;
import com.cottagesystems.jdiskhog.JDiskHogPanel;
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
import org.das2.util.PersistentStateSupport;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.help.CSH;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import org.autoplot.pngwalk.PngWalkTool1;
import org.das2.DasApplication;
import org.das2.components.propertyeditor.PropertyEditor;
import org.das2.graph.DasPlot;
import org.das2.util.filesystem.FileSystem;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.virbo.autoplot.bookmarks.BookmarksManagerModel;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.autoplot.dom.DataSourceFilter;
import org.virbo.autoplot.dom.Panel;
import org.virbo.autoplot.scriptconsole.JythonScriptPanel;
import org.virbo.autoplot.scriptconsole.LogConsole;
import org.virbo.autoplot.server.RequestHandler;
import org.virbo.autoplot.server.RequestListener;
import org.virbo.autoplot.dom.Options;
import org.virbo.autoplot.dom.OptionsPrefsController;
import org.virbo.autoplot.dom.PlotController;
import org.virbo.autoplot.scriptconsole.GuiExceptionHandler;
import org.virbo.autoplot.state.StatePersistence;
import org.virbo.autoplot.state.UndoRedoSupport;
import org.virbo.autoplot.util.TickleTimer;
import org.virbo.datasource.DataSetSelector;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.DataSourceRegistry;
import org.virbo.datasource.SourceTypesBrowser;
import org.virbo.datasource.URISplit;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 *
 * @author  jbf
 */
public class AutoPlotUI extends javax.swing.JFrame {

    TearoffTabbedPane tabs;
    ApplicationModel applicationModel;
    Application dom;
    PersistentStateSupport stateSupport;
    UndoRedoSupport undoRedoSupport;
    TickleTimer tickleTimer;
    GuiSupport support;
    LayoutListener autoLayout;
    final String TABS_TOOLTIP = "right-click to undock";
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
    
    private static final Logger logger = Logger.getLogger("org.virbo.autoplot");
    private JythonScriptPanel scriptPanel;
    private DataPanel dataPanel;
    private LayoutPanel layoutPanel;
    private LogConsole logConsole;
    private RequestListener rlistener;
    private JDialog fontAndColorsDialog = null;
    private BookmarksManager bookmarksManager = null;
    private AutoplotHelpSystem helpSystem;

    private static String RESOURCES= "/org/virbo/autoplot/resources/";
    public static final Icon WARNING_ICON= new ImageIcon( AutoPlotUI.class.getResource(RESOURCES+"warning-icon.png") );
    public static final Icon ERROR_ICON= new ImageIcon( AutoPlotUI.class.getResource(RESOURCES+"error-icon.png") );
    public static final Icon BUSY_ICON= new ImageIcon( AutoPlotUI.class.getResource(RESOURCES+"spinner.gif") );
    public static final Icon READY_ICON= new ImageIcon( AutoPlotUI.class.getResource(RESOURCES+"indProgress0.png") );
    public static final Icon IDLE_ICON= new ImageIcon( AutoPlotUI.class.getResource(RESOURCES+"idle-icon.png") );
        
    
    /** Creates new form AutoPlotMatisse */
    public AutoPlotUI(ApplicationModel model) {

        // Initialize help system now so it's ready for components to register IDs with
        AutoplotHelpSystem.initialize(getRootPane());
        helpSystem = AutoplotHelpSystem.getHelpSystem();

        if ( DasApplication.getDefaultApplication().isHeadless() ) {
            model.setExceptionHandler( DasApplication.getDefaultApplication().getExceptionHandler() );
        } else {
            model.setExceptionHandler( new GuiExceptionHandler() );
        }

        ScriptContext.setApplicationModel(model);
        ScriptContext.setView(this);

        this.dom= model.getDocumentModel();
        
        support = new GuiSupport(this);

        applicationModel = model;
        undoRedoSupport = new UndoRedoSupport(applicationModel);
        undoRedoSupport.addPropertyChangeListener(new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                refreshUndoRedoLabel();
            }
        });
        
        initComponents();

        statusLabel.setIcon(IDLE_ICON);
        support.addKeyBindings((JPanel) getContentPane());

        dataSetSelector.setMonitorFactory( dom.getController().getMonitorFactory() );

        final ApplicationController appController= applicationModel.getDocumentModel().getController();

        appController.addDas2PeerChangeListener( new PropertyChangeListener() {
            public void propertyChange( PropertyChangeEvent e ) {
                PlotController plotController= (PlotController) e.getNewValue();
                ApplicationController controller= plotController.getApplication().getController();
                support.addPlotContextMenuItems( controller, plotController.getDasPlot(), plotController, plotController.getPlot() );
                support.addAxisContextMenuItems(  controller,  plotController.getDasPlot(), plotController,  plotController.getPlot(), plotController.getPlot().getXaxis());
                support.addAxisContextMenuItems( controller,  plotController.getDasPlot(), plotController,  plotController.getPlot(), plotController.getPlot().getYaxis());
                support.addAxisContextMenuItems(  controller,  plotController.getDasPlot(), plotController,  plotController.getPlot(), plotController.getPlot().getZaxis());
            }
        } );

        appController.addPropertyChangeListener( ApplicationController.PROP_FOCUSURI, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                dataSetSelector.setValue( appController.getFocusUri() );
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
                    dataSetSelector.setValue(stateSupport.getCurrentFile().toString());
                }
                super.focusGained(e);
            }
        });

        setIconImage(new ImageIcon(this.getClass().getResource("logoA16x16.png")).getImage());

        stateSupport = getPersistentStateSupport(this, applicationModel);

        fillFileMenu();

        List<String> urls = new ArrayList<String>();
        List<Bookmark> recent = applicationModel.getRecent();

        for (Bookmark b : recent) {
            urls.add(((Bookmark.Item) b).getUrl());
        }

        dataSetSelector.setRecent(urls);
        if (urls.size() > 1) {
            dataSetSelector.setValue(urls.get(urls.size() - 1));
        }

        applicationModel.addPropertyChangeListener(new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(ApplicationModel.PROPERTY_RECENT)) {
                    List<String> urls = new ArrayList<String>();
                    List<Bookmark> recent = applicationModel.getRecent();
                    for (Bookmark b : recent) {
                        urls.add(((Bookmark.Item) b).getUrl());
                    }
                    dataSetSelector.setRecent(urls);
                } else if (evt.getPropertyName().equals(ApplicationModel.PROPERTY_BOOKMARKS)) {
                    updateBookmarks();
                }
            }
        });

        autoLayout = new LayoutListener(model);

        addBindings();

        dataSetSelector.addPropertyChangeListener(DataSetSelector.PROPERTY_MESSAGE, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent e) {
                Runnable run = new Runnable() {

                    public void run() {
                        setStatus(dataSetSelector.getMessage());
                    }
                };
                //SwingUtilities.invokeLater(run);
                run.run();
            }
        });

        tabs = new TearoffTabbedPane();

        applicationModel.getCanvas().setFitted(true);
        JScrollPane scrollPane = new JScrollPane(applicationModel.getCanvas());
        tabs.insertTab("canvas", null, scrollPane, TABS_TOOLTIP, 0);
        tabs.validate();
        
        tabbedPanelContainer.add(tabs, BorderLayout.CENTER);

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
        
        updateBookmarks();
        addTools();

        pack();

        dom.getOptions().addPropertyChangeListener(optionsListener);
        
        applicationModel.getCanvas().resizeAllComponents();
        applicationModel.getCanvas().repaint();
        applicationModel.getCanvas().paintImmediately(0,0,1000,1000);

    }

    private Runnable addAxes() {
        return new Runnable() {
            public void run() {
                JComponent c= new AxisPanel(applicationModel);
                JScrollPane sp= new JScrollPane();
                sp.setViewportView(c);
                tabs.insertTab("axes", null, sp, TABS_TOOLTIP, 1);
            }
        };
    }

    private Runnable addStyle() {
        return new Runnable() {
            public void run() {
                tabs.insertTab("style", null, new PlotStylePanel(applicationModel), TABS_TOOLTIP, 2);
            }
        };
    }

    private void addFeatures( ApplicationModel model ) {
        if (model.getDocumentModel().getOptions().isLayoutVisible() ) {
            LayoutPanel lui= new LayoutPanel();
            lui.setApplication(dom);
            layoutPanel= lui;
            tabs.insertTab("layout",null, lui, TABS_TOOLTIP, tabs.getTabCount() );
        }

        if (model.getDocumentModel().getOptions().isDataVisible()) {
            final DataPanel dp= new DataPanel(dom);
            dataPanel= dp;
            tabs.insertTab("data", null, dp, TABS_TOOLTIP, tabs.getTabCount() );
        }

        final MetaDataPanel mdp = new MetaDataPanel(applicationModel);
        JScrollPane sp= new JScrollPane();
        sp.setViewportView(mdp);
        tabs.insertTab("metadata", null, sp, TABS_TOOLTIP, tabs.getTabCount() );

        if (model.getDocumentModel().getOptions().isScriptVisible()) {
            tabs.add("script", new JythonScriptPanel(applicationModel, this.dataSetSelector));
            scriptPanelMenuItem.setSelected(true);
        }
        if (model.getDocumentModel().getOptions().isLogConsoleVisible()) {
            initLogConsole();
        }
        tickleTimer = new TickleTimer(300, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {

                if ( dom.getController().isValueAdjusting() ) return; // don't listen to property changes during state transitions.

                undoRedoSupport.pushState(evt);
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
                logger.finer( "state change: "+evt );
                if (!stateSupport.isOpening() && !stateSupport.isSaving() && !applicationModel.isRestoringState()) { // TODO: list the props we want!
                    tickleTimer.tickle( evt.getActionCommand() + " from " + evt.getSource() );
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

        tabbedPanelContainer.validate();

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
        bc.addBinding( Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, src, BeanProperty.create(srcProperty), dst, BeanProperty.create(dstProperty) ));
    }

    private void addBindings() {

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
        bc.bind();

        this.dataSetSelector.addPropertyChangeListener("value", new PropertyChangeListener() { //one-way binding
            public void propertyChange(PropertyChangeEvent evt) {
                    applicationModel.setDataSourceURL(dataSetSelector.getValue());
            }
        });

    }

    public void plotUri( String uri ) {
        dataSetSelector.getEditor().setText(uri);
        dataSetSelector.setValue(uri);
        dataSetSelector.maybePlot(false);        
    }
    
    private void addBookmarks(JMenu bookmarksMenu, List<Bookmark> bookmarks) {

        for (int i = 0; i < bookmarks.size(); i++) {
            final Bookmark book = bookmarks.get(i);

            if (book instanceof Bookmark.Item) {
                JMenuItem mi = new JMenuItem(new AbstractAction(book.getTitle()) {

                    public void actionPerformed(ActionEvent e) {
                        plotUri(((Bookmark.Item) book).getUrl());
                    }
                });

                mi.setToolTipText(((Bookmark.Item) book).getUrl());
                if (book.getIcon() != null) {
                    mi.setIcon(AutoplotUtil.scaleIcon(book.getIcon(), -1, 16));
                }
                bookmarksMenu.add(mi);
            } else {
                Bookmark.Folder folder = (Bookmark.Folder) book;
                JMenu subMenu = new JMenu(book.getTitle());
                addBookmarks(subMenu, folder.getBookmarks());
                bookmarksMenu.add(subMenu);
            }
        }
    }

    private Action getAddPanelAction() {
        return new AbstractAction("Add Panel...") {
            public void actionPerformed(ActionEvent e) {
                support.addPanel();
            }
        };
    }

    private void fillFileMenu() {

        //fileMenu.add(support.createNewApplicationAction() );
        //fileMenu.add(support.createCloneApplicationAction() );

        fileMenu.add(support.createNewDOMAction());
        fileMenu.add( new JSeparator() );
        
        fileMenu.add(dataSetSelector.getOpenLocalAction());

        fileMenu.add(getAddPanelAction());

        fileMenu.add(dataSetSelector.getRecentMenu());

        fileMenu.add(stateSupport.createSaveAsAction());

        fileMenu.add(stateSupport.createSaveAction());
        fileMenu.add(new AbstractAction("Save With Data...") {

            public void actionPerformed(ActionEvent e) {
                applicationModel.setUseEmbeddedDataSet(true);
                stateSupport.saveAs();
            }
        });
        fileMenu.addSeparator();
        fileMenu.add(applicationModel.getCanvas().PRINT_ACTION);

        JMenu printToMenu = new JMenu("Print to");
        fileMenu.add(printToMenu);

        JMenuItem item = new JMenuItem( GuiSupport.getPrintAction(dom, "pdf" ) );
        item.setText("PDF...");
        printToMenu.add(item);

        item = new JMenuItem( GuiSupport.getPrintAction(dom, "svg" ) );
        item.setText("SVG...");
        printToMenu.add(item);

        item = new JMenuItem( GuiSupport.getPrintAction(dom, "png" ) );
        item.setText("PNG...");
        printToMenu.add(item);

        fileMenu.addSeparator();

        fileMenu.add(support.getDumpDataAction());

        fileMenu.addSeparator();
        fileMenu.add(stateSupport.createQuitAction());
    }

    private String browseLocal(String surl) {
        try {
            int i = surl.lastIndexOf("/");
            String surlDir;
            if (i <= 0 || surl.charAt(i - 1) == '/') {
                surlDir = surl;
            } else {
                surlDir = surl.substring(0, i);
            }
            File dir = DataSetURI.getFile(DataSetURI.getURL(surlDir), new NullProgressMonitor());
            JFileChooser chooser = new JFileChooser(dir);
            int r = chooser.showOpenDialog(this);
            String result;
            if (r == chooser.APPROVE_OPTION) {
                result = chooser.getSelectedFile().toString();
            } else {
                result = surl;
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void initLogConsole() throws SecurityException {
        logConsole = new LogConsole();
        logConsole.setScriptContext( Collections.singletonMap( "dom", (Object)applicationModel.dom ) ); // must cast or javac complains
        logConsole.turnOffConsoleHandlers();
        logConsole.logConsoleMessages(); // stderr, stdout logged to Logger "console"

        Handler h = logConsole.getHandler();
        Logger.getLogger("das2").setLevel(Level.INFO);
        Logger.getLogger("das2").addHandler(h);
        Logger.getLogger("virbo").setLevel(Level.ALL);
        Logger.getLogger("virbo").addHandler(h);
        Logger.getLogger("vap").setLevel(Level.ALL);
        Logger.getLogger("vap").addHandler(h);
        Logger.getLogger("console").setLevel(Level.ALL);
        Logger.getLogger("console").addHandler(h); // stderr, stdout

        setMessage("log console added");
        tabs.addTab("console", logConsole);
        applicationModel.getDocumentModel().getOptions().setLogConsoleVisible(true);

        if ( applicationModel.getExceptionHandler() instanceof GuiExceptionHandler ) {
            ((GuiExceptionHandler)applicationModel.getExceptionHandler()).setLogConsole(logConsole);
        }
        logConsoleMenuItem.setSelected(true);
    }

    private void initServer() {
        String result = JOptionPane.showInputDialog(this, "Select port for server.  This port will accept jython commands to control receive services from the application", 12345);
        int iport = Integer.parseInt(result);
        setupServer(iport, applicationModel);
    }

    private void stopServer() {
        rlistener.stopListening();
    }


    private ProgressMonitor getStatusBarProgressMonitor( final String finishMessage ) {
        return new NullProgressMonitor() {
                public void setProgressMessage(String message) {
                    setStatus(BUSY_ICON,message);
                }
                @Override
                public void finished() {
                    setStatus(IDLE_ICON,finishMessage);
                }

            };
    }

    private void plotUrl() {
        plotUrl( (String) dataSetSelector.getValue() );
    }

    private void plotUrl( String surl ) {
        try {
            Logger.getLogger("ap").fine("plotUrl("+surl+")");
            URISplit split= URISplit.parse(surl);
            ProgressMonitor mon= getStatusBarProgressMonitor("Finished "+surl);
            if ( !( split.file.endsWith(".vap")|| split.file.endsWith(".vapx") ) ) {
                if ( ! "true".equals(AutoplotUtil.getProperty("java.awt.headless", "false")) ) {
                    try {
                        DataSourceFactory sourcef = DataSetURI.getDataSourceFactory(DataSetURI.getURI(surl),mon);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    } catch (URISyntaxException ex) {
                        Logger.getLogger(AutoPlotUI.class.getName()).log(Level.SEVERE, null, ex);
                    } catch ( IllegalArgumentException ex ) {
                        SourceTypesBrowser browser= new SourceTypesBrowser();
                        browser.getDataSetSelector().setValue(DataSetURI.fromUri(DataSetURI.getResourceURI(surl)));
                        int r= JOptionPane.showConfirmDialog(this, browser,"Select Data Source Type",JOptionPane.OK_CANCEL_OPTION);
                        if ( r==JOptionPane.OK_OPTION ) {
                            surl= browser.getUri();
                            dataSetSelector.setValue(surl);
                        } else {
                            return;
                        }
                    }
                }
            }
            applicationModel.addRecent(surl);
            applicationModel.resetDataSetSourceURL(surl, mon );
        } catch (RuntimeException ex) {
            if ( ex.getCause()!=null && ex.getCause() instanceof IOException ) {
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
     * add a new plot and panel.  This is attached to control-enter.
     */
    private void plotAnotherUrl() {
        plotAnotherUrl( (String) dataSetSelector.getValue() );
    }

    private void plotAnotherUrl( final String surl ) {
        try {
            Logger.getLogger("ap").fine("plotAnotherUrl("+surl+")");
            applicationModel.addRecent(surl);
            Panel panel= dom.getController().addPanel( null,null );
            dom.getController().getDataSourceFilterFor(panel).setUri(surl);
            
        } catch (RuntimeException ex) {
            applicationModel.getExceptionHandler().handleUncaught(ex);
            setStatus(ERROR_ICON,ex.getMessage());
        }
    }

    /**
     * add a panel to the focus plot.  This is attached to shift-enter.
     */
    private void overplotAnotherUrl() {
        overplotAnotherUrl( (String) dataSetSelector.getValue() );
    }

    private void overplotAnotherUrl( final String surl ) {
        try {
            Logger.getLogger("ap").fine("overplotAnotherUrl("+surl+")");
            applicationModel.addRecent(surl);
            Panel panel= dom.getController().addPanel( dom.getController().getPlot() ,null );
            dom.getController().getDataSourceFilterFor(panel).setUri(surl);

        } catch (RuntimeException ex) {
            applicationModel.getExceptionHandler().handleUncaught(ex);
            setStatus(ERROR_ICON,ex.getMessage());
        }
    }

    public void setStatus(String message) {
        if ( message.startsWith("busy:" ) ) {
            setMessage( BUSY_ICON, message.substring(5).trim() );
            logger.info(message);
        } else if ( message.startsWith("warning:" ) ) {
            setMessage( WARNING_ICON, message.substring(8).trim() );
            logger.warning(message);
        } else if ( message.startsWith("error:" ) ) {
            setMessage( ERROR_ICON, message.substring(6).trim() );
            logger.severe(message);
        } else {
            logger.info(message);
            setMessage(message);
        }
        
    }
    
    public void setStatus( Icon icon, String message ) {
        if ( this.ERROR_ICON==icon ) {
            logger.severe(message);
        } else if ( this.WARNING_ICON==icon ) {
            logger.warning(message);
        } else {
            logger.info(message);
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
            bookmarksManager = new BookmarksManager(AutoPlotUI.this, false);

            Binding b= Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, applicationModel, BeanProperty.create( "bookmarks" ), bookmarksManager.getModel(), BeanProperty.create("list"));
            b.bind();

            bookmarksManager.getModel().addPropertyChangeListener(BookmarksManagerModel.PROP_BOOKMARK, new PropertyChangeListener() {

                public void propertyChange(PropertyChangeEvent evt) {
                    updateBookmarks();
                    Preferences prefs = Preferences.userNodeForPackage(ApplicationModel.class);
                    prefs.put("bookmarks", Bookmark.formatBooks(bookmarksManager.getModel().getList()));
                    try {
                        prefs.flush();
                    } catch (BackingStoreException ex) {
                        ex.printStackTrace();
                    }
                }
            });
        }
    }

    private void updateBookmarks() {

        JMenuItem item;
        List<Bookmark> bookmarks = applicationModel.getBookmarks();
        bookmarksMenu.removeAll();

        bookmarksMenu.add(new AbstractAction("Add Bookmark...") {

            public void actionPerformed(ActionEvent e) {
                Bookmark bookmark = applicationModel.addBookmark(dataSetSelector.getEditor().getText());
                maybeCreateBookmarksManager();
                bookmarksManager.setAddBookmark(bookmark);
                bookmarksManager.setVisible(true);
            }
        });

        bookmarksMenu.add(new AbstractAction("Manage Bookmarks") {

            public void actionPerformed(ActionEvent e) {
                maybeCreateBookmarksManager();
                bookmarksManager.setVisible(true);

            }
        });

        item= bookmarksMenu.add( new AbstractAction("Export Recent...") {
            public void actionPerformed(ActionEvent e) {
                support.exportRecent(AutoPlotUI.this);
            }
        } );
        item.setToolTipText("Export recent URIs to a bookmarks file.  (There is no method for importing recent URIs.)");

        bookmarksMenu.add(new JSeparator());
        item = new JMenuItem(new AbstractAction("Make Aggregation From URL") {

            public void actionPerformed(ActionEvent e) {
                String s = dataSetSelector.getValue();
                String agg = org.virbo.datasource.DataSourceUtil.makeAggregation(s);
                if (agg != null) {
                    dataSetSelector.setValue(agg);
                } else {
                    JOptionPane.showMessageDialog(AutoPlotUI.this, "Unable to create aggregation spec, couldn't find yyyymmdd.");
                }
            }
        });
        item.setToolTipText("<html>create aggregation template from the URL to combine a time series spanning multiple files</html>");
        bookmarksMenu.add(item);

        item = new JMenuItem(new AbstractAction("Decode URL") {

            public void actionPerformed(ActionEvent e) {
                String s = dataSetSelector.getEditor().getText();
                s = org.virbo.datasource.DataSourceUtil.unescape(s);
                dataSetSelector.getEditor().setText(s);
            }
        });
        item.setToolTipText("<html>decode escapes to correct URL</html>");
        bookmarksMenu.add(item);
        bookmarksMenu.add(new JSeparator());

        addBookmarks(bookmarksMenu, bookmarks);
    }

    public static PersistentStateSupport getPersistentStateSupport(final AutoPlotUI parent, final ApplicationModel applicationModel) {
        final PersistentStateSupport stateSupport = new PersistentStateSupport(parent, null, "vap") {

            protected void saveImpl(File f) throws IOException {
                applicationModel.doSave(f);
                applicationModel.addRecent(f.toURI().toString());
                parent.setStatus("saved " + f);
            }

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

        dataSetSelector = new org.virbo.datasource.DataSetSelector();
        statusLabel = new javax.swing.JLabel();
        tabbedPanelContainer = new javax.swing.JPanel();
        jMenuBar1 = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        editMenu = new javax.swing.JMenu();
        undoMenuItem = new javax.swing.JMenuItem();
        redoMenuItem = new javax.swing.JMenuItem();
        undoMultipleMenu = new javax.swing.JMenu();
        jSeparator2 = new javax.swing.JSeparator();
        editDomMenuItem = new javax.swing.JMenuItem();
        jMenuItem6 = new javax.swing.JMenuItem();
        jSeparator1 = new javax.swing.JSeparator();
        pasteDataSetURLMenuItem = new javax.swing.JMenuItem();
        copyDataSetURLMenuItem = new javax.swing.JMenuItem();
        copyImageMenuItem = new javax.swing.JMenuItem();
        viewMenu = new javax.swing.JMenu();
        resetZoomMenuItem = new javax.swing.JMenuItem();
        zoomInMenuItem = new javax.swing.JMenuItem();
        zoomOutMenuItem = new javax.swing.JMenuItem();
        optionsMenu = new javax.swing.JMenu();
        renderingOptionsMenu = new javax.swing.JMenu();
        textAntiAlias = new javax.swing.JCheckBoxMenuItem();
        drawAntiAliasMenuItem = new javax.swing.JCheckBoxMenuItem();
        specialEffectsMenuItem = new javax.swing.JCheckBoxMenuItem();
        overRenderingMenuItem = new javax.swing.JCheckBoxMenuItem();
        drawGridCheckBox = new javax.swing.JCheckBoxMenuItem();
        plotStyleMenu = new javax.swing.JMenu();
        fontsAndColorsMenuItem = new javax.swing.JMenuItem();
        jMenu1 = new javax.swing.JMenu();
        scriptPanelMenuItem = new javax.swing.JCheckBoxMenuItem();
        logConsoleMenuItem = new javax.swing.JCheckBoxMenuItem();
        serverCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        dataPanelCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        layoutPanelCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        jMenu5 = new javax.swing.JMenu();
        jMenuItem7 = new javax.swing.JMenuItem();
        jMenu2 = new javax.swing.JMenu();
        jMenuItem1 = new javax.swing.JMenuItem();
        jMenuItem2 = new javax.swing.JMenuItem();
        jMenu4 = new javax.swing.JMenu();
        autoRangingCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        autoLabellingCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        autoLayoutCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        bookmarksMenu = new javax.swing.JMenu();
        toolsMenu = new javax.swing.JMenu();
        jMenu3 = new javax.swing.JMenu();
        jMenuItem3 = new javax.swing.JMenuItem();
        jMenuItem4 = new javax.swing.JMenuItem();
        jSeparator3 = new javax.swing.JSeparator();
        pngWalkMenuItem = new javax.swing.JMenuItem();
        createPngWalkMenuItem = new javax.swing.JMenuItem();
        aggregateAllMenuItem = new javax.swing.JMenuItem();
        helpMenu = new javax.swing.JMenu();
        autoplotHelpMenuItem = new javax.swing.JMenuItem();
        jMenuItem5 = new javax.swing.JMenuItem();
        aboutDas2MenuItem = new javax.swing.JMenuItem();
        autoplotHomepageButton = new javax.swing.JMenuItem();
        aboutAutoplotMenuItem = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Autoplot");

        dataSetSelector.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSetSelectorActionPerformed(evt);
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

        fileMenu.setText("File");
        fileMenu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fileMenuActionPerformed(evt);
            }
        });
        jMenuBar1.add(fileMenu);

        editMenu.setText("Edit");
        editMenu.setToolTipText("Edit the DOM, which is the internal application state.\n");

        undoMenuItem.setAction(undoRedoSupport.getUndoAction());
        undoMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, java.awt.event.InputEvent.CTRL_MASK));
        undoMenuItem.setText("Undo");
        editMenu.add(undoMenuItem);

        redoMenuItem.setAction(undoRedoSupport.getRedoAction());
        redoMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Y, java.awt.event.InputEvent.CTRL_MASK));
        redoMenuItem.setText("Redo");
        editMenu.add(redoMenuItem);

        undoMultipleMenu.setText("Undo...");
        editMenu.add(undoMultipleMenu);
        editMenu.add(jSeparator2);

        editDomMenuItem.setText("Edit DOM");
        editDomMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editDomMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(editDomMenuItem);

        jMenuItem6.setText("Inspect Vap File...");
        jMenuItem6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem6ActionPerformed(evt);
            }
        });
        editMenu.add(jMenuItem6);
        editMenu.add(jSeparator1);

        pasteDataSetURLMenuItem.setText("Paste URL");
        pasteDataSetURLMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pasteDataSetURLMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(pasteDataSetURLMenuItem);

        copyDataSetURLMenuItem.setText("Copy URL");
        copyDataSetURLMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyDataSetURLMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(copyDataSetURLMenuItem);

        copyImageMenuItem.setText("Copy Image To Clipboard");
        copyImageMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyImageMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(copyImageMenuItem);

        jMenuBar1.add(editMenu);

        viewMenu.setText("View");

        resetZoomMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, java.awt.event.InputEvent.CTRL_MASK));
        resetZoomMenuItem.setText("Reset Zoom");
        resetZoomMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetZoomMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(resetZoomMenuItem);

        zoomInMenuItem.setText("Zoom In");
        zoomInMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomInMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(zoomInMenuItem);

        zoomOutMenuItem.setText("Zoom Out");
        zoomOutMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomOutMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(zoomOutMenuItem);

        jMenuBar1.add(viewMenu);

        optionsMenu.setText("Options");
        optionsMenu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                optionsMenuActionPerformed(evt);
            }
        });

        renderingOptionsMenu.setText("Rendering Options");

        textAntiAlias.setSelected(true);
        textAntiAlias.setText("Text Antialias");
        textAntiAlias.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                textAntiAliasActionPerformed(evt);
            }
        });
        renderingOptionsMenu.add(textAntiAlias);

        drawAntiAliasMenuItem.setSelected(true);
        drawAntiAliasMenuItem.setText("Graphics Antialias");
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
        renderingOptionsMenu.add(drawGridCheckBox);

        optionsMenu.add(renderingOptionsMenu);

        plotStyleMenu.setText("Plot Style");

        fontsAndColorsMenuItem.setText("Fonts and Colors...");
        fontsAndColorsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fontsAndColorsMenuItemActionPerformed(evt);
            }
        });
        plotStyleMenu.add(fontsAndColorsMenuItem);

        optionsMenu.add(plotStyleMenu);

        jMenu1.setText("Enable Feature");

        scriptPanelMenuItem.setText("Script Panel");
        scriptPanelMenuItem.setToolTipText("Script Panel adds a tab that displays scripts used for the jython data source.  It also provides a way to create new jython sources.");
        scriptPanelMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                scriptPanelMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(scriptPanelMenuItem);

        logConsoleMenuItem.setText("Log Console");
        logConsoleMenuItem.setToolTipText("add a tab that receives and displays messages posted to the java logging system.  ");
        logConsoleMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                logConsoleMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(logConsoleMenuItem);

        serverCheckBoxMenuItem.setText("Server");
        serverCheckBoxMenuItem.setToolTipText("<html>\nStart up back end server that allows commands to be send to Autoplot via a port. <br>\nSee <a href='http://vxoware.svn.sourceforge.net/viewvc/vxoware/autoplot/trunk/VirboAutoplot/src/org/virbo/autoplot/ScriptContext.java?view=markup'>ScriptContext</a>\n</html>");
        serverCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                serverCheckBoxMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(serverCheckBoxMenuItem);

        dataPanelCheckBoxMenuItem.setText("Data Panel");
        dataPanelCheckBoxMenuItem.setToolTipText("the \"data\" panel allows for explicitly setting valid range and fill values for the dataset, and additional controls for data reduction before plotting.\n");
        jMenu1.add(dataPanelCheckBoxMenuItem);

        layoutPanelCheckBoxMenuItem.setText("Layout Panel");
        layoutPanelCheckBoxMenuItem.setToolTipText("Enables the layout panel, which shows all the plots and panels in thier relative positions.\n");
        jMenu1.add(layoutPanelCheckBoxMenuItem);

        optionsMenu.add(jMenu1);

        jMenu5.setText("Plugins");

        jMenuItem7.setText("Add Data Source...");
        jMenuItem7.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem7ActionPerformed(evt);
            }
        });
        jMenu5.add(jMenuItem7);

        optionsMenu.add(jMenu5);

        jMenu2.setText("Text Size");

        jMenuItem1.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_EQUALS, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItem1.setText("Bigger");
        jMenuItem1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem1ActionPerformed(evt);
            }
        });
        jMenu2.add(jMenuItem1);

        jMenuItem2.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_MINUS, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItem2.setText("Smaller");
        jMenuItem2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem2ActionPerformed(evt);
            }
        });
        jMenu2.add(jMenuItem2);

        optionsMenu.add(jMenu2);

        jMenu4.setText("Auto");

        autoRangingCheckBoxMenuItem.setSelected(true);
        autoRangingCheckBoxMenuItem.setText("AutoRanging");
        autoRangingCheckBoxMenuItem.setToolTipText("allow automatic axis range setting.  Range is based on metadata hints and data range.");
        jMenu4.add(autoRangingCheckBoxMenuItem);

        autoLabellingCheckBoxMenuItem.setSelected(true);
        autoLabellingCheckBoxMenuItem.setText("AutoLabelling");
        autoLabellingCheckBoxMenuItem.setToolTipText("allow automatic setting of axis labels based on metadata. ");
        jMenu4.add(autoLabellingCheckBoxMenuItem);

        autoLayoutCheckBoxMenuItem.setSelected(true);
        autoLayoutCheckBoxMenuItem.setText("AutoLayout");
        autoLayoutCheckBoxMenuItem.setToolTipText("<html><p>Allow the application to reposition axes so labels are not clipped and unused space is reduced.  </P><p>Axes can be positioned manually by turning off this option, then hold shift down to enable plot corner drag anchors.</p></html>");
        autoLayoutCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                autoLayoutCheckBoxMenuItemActionPerformed(evt);
            }
        });
        jMenu4.add(autoLayoutCheckBoxMenuItem);

        optionsMenu.add(jMenu4);

        jMenuBar1.add(optionsMenu);

        bookmarksMenu.setText("Bookmarks");
        jMenuBar1.add(bookmarksMenu);

        toolsMenu.setText("Tools");

        jMenu3.setText("Cache");

        jMenuItem3.setText("Manage Files");
        jMenuItem3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem3ActionPerformed(evt);
            }
        });
        jMenu3.add(jMenuItem3);

        jMenuItem4.setText("Clear Cache");
        jMenuItem4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem4ActionPerformed(evt);
            }
        });
        jMenu3.add(jMenuItem4);

        toolsMenu.add(jMenu3);
        toolsMenu.add(jSeparator3);

        pngWalkMenuItem.setText("PNG Walk");
        pngWalkMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pngWalkMenuItemActionPerformed(evt);
            }
        });
        toolsMenu.add(pngWalkMenuItem);

        createPngWalkMenuItem.setText("Create PNG Walk...");
        createPngWalkMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                createPngWalkMenuItemActionPerformed(evt);
            }
        });
        toolsMenu.add(createPngWalkMenuItem);

        aggregateAllMenuItem.setText("Aggregate All");
        aggregateAllMenuItem.setToolTipText("Attempt to aggregate all the URIs on the product, keeping other DOM settings.\n");
        aggregateAllMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                aggregateAllMenuItemActionPerformed(evt);
            }
        });
        toolsMenu.add(aggregateAllMenuItem);

        jMenuBar1.add(toolsMenu);

        helpMenu.setText("Help");
        helpMenu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                helpMenuActionPerformed(evt);
            }
        });

        CSH.setHelpIDString(autoplotHelpMenuItem, "aphelp_main");
        autoplotHelpMenuItem.setText("Help Contents...");
        autoplotHelpMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                autoplotHelpMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(autoplotHelpMenuItem);

        jMenuItem5.setText("Release Notes");
        jMenuItem5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem5ActionPerformed(evt);
            }
        });
        helpMenu.add(jMenuItem5);

        aboutDas2MenuItem.setText("Das2 Homepage");
        aboutDas2MenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                aboutDas2MenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(aboutDas2MenuItem);

        autoplotHomepageButton.setText("Autoplot Homepage");
        autoplotHomepageButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                autoplotHomepageButtonActionPerformed(evt);
            }
        });
        helpMenu.add(autoplotHomepageButton);

        aboutAutoplotMenuItem.setText("About Autoplot");
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
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(dataSetSelector, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 669, Short.MAX_VALUE)
                .addContainerGap())
            .add(statusLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 693, Short.MAX_VALUE)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                .add(org.jdesktop.layout.GroupLayout.TRAILING, tabbedPanelContainer, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 693, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(dataSetSelector, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 535, Short.MAX_VALUE)
                .add(statusLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 23, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                    .add(46, 46, 46)
                    .add(tabbedPanelContainer, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 525, Short.MAX_VALUE)
                    .add(26, 26, 26)))
        );

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

    private void optionsMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_optionsMenuActionPerformed
// TODO add your handling code here:
    }//GEN-LAST:event_optionsMenuActionPerformed

    private void dataSetSelectorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSetSelectorActionPerformed
        if ( ( evt.getModifiers() & KeyEvent.CTRL_MASK ) == KeyEvent.CTRL_MASK ) {
            plotAnotherUrl();
        } else if ( ( evt.getModifiers() & KeyEvent.SHIFT_MASK ) == KeyEvent.SHIFT_MASK )  {
            overplotAnotherUrl();
        } else {
            plotUrl();
        }
    }//GEN-LAST:event_dataSetSelectorActionPerformed

    private void fileMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fileMenuActionPerformed
// TODO add your handling code here:
    }//GEN-LAST:event_fileMenuActionPerformed

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
            StringBuffer buffy = new StringBuffer();

            buffy.append("<html>\n");
            URL aboutHtml = AutoPlotUI.class.getResource("aboutAutoplot.html");

            if ( aboutHtml!=null ) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(aboutHtml.openStream()));
                String s = reader.readLine();
                while (s != null) {
                    buffy.append(s + "");
                    s = reader.readLine();
                }
                reader.close();
            } 

            buffy.append("    <h2>Build Information:</h2>");
            buffy.append("<ul>");
            buffy.append("<li>release tag: " + AboutUtil.getReleaseTag() + "</li>");

            List<String> bi = Util.getBuildInfos();
            for (String ss : bi) {
                buffy.append("    <li>" + ss + "");
            }
            buffy.append("<ul>    </p></html>");

            JLabel label= new JLabel(buffy.toString());

            JOptionPane.showMessageDialog(this, label);

        } catch (IOException ex) {
        }

    }//GEN-LAST:event_aboutAutoplotMenuItemActionPerformed

    private void aboutDas2MenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aboutDas2MenuItemActionPerformed
        AutoplotUtil.openBrowser("http://www.das2.org");
    }//GEN-LAST:event_aboutDas2MenuItemActionPerformed

    private void autoplotHomepageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autoplotHomepageButtonActionPerformed
        AutoplotUtil.openBrowser("http://www.autoplot.org/");
}//GEN-LAST:event_autoplotHomepageButtonActionPerformed

    private void helpMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_helpMenuActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_helpMenuActionPerformed

private void scriptPanelMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_scriptPanelMenuItemActionPerformed
    applicationModel.getDocumentModel().getOptions().setScriptVisible(scriptPanelMenuItem.isSelected());
    if (scriptPanelMenuItem.isSelected() && scriptPanel == null) {
        scriptPanel = new JythonScriptPanel(applicationModel, this.dataSetSelector);
        tabs.insertTab("script", null, scriptPanel, TABS_TOOLTIP, 4);
    } else {
        JOptionPane.showMessageDialog(rootPane, "The feature will be disabled next time the application is run.");
    }
}//GEN-LAST:event_scriptPanelMenuItemActionPerformed

private void logConsoleMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_logConsoleMenuItemActionPerformed
    applicationModel.getDocumentModel().getOptions().setLogConsoleVisible(logConsoleMenuItem.isSelected());
    if (applicationModel.getDocumentModel().getOptions().isLogConsoleVisible() && logConsole == null) {
        initLogConsole();
    } else {
        if ( logConsole!=null ) {
            logConsole.undoLogConsoleMessages();
        }
        JOptionPane.showMessageDialog(rootPane, "The feature will be disabled next time the application is run.");
    }
}//GEN-LAST:event_logConsoleMenuItemActionPerformed

private void serverCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_serverCheckBoxMenuItemActionPerformed
    applicationModel.getDocumentModel().getOptions().setServerEnabled(serverCheckBoxMenuItem.isSelected());
    if (applicationModel.getDocumentModel().getOptions().isServerEnabled()) {
        initServer();
    } else {
        stopServer();
    }
}//GEN-LAST:event_serverCheckBoxMenuItemActionPerformed

private void jMenuItem1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem1ActionPerformed
    applicationModel.increaseFontSize();
}//GEN-LAST:event_jMenuItem1ActionPerformed

private void jMenuItem2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem2ActionPerformed
    applicationModel.decreaseFontSize();
}//GEN-LAST:event_jMenuItem2ActionPerformed

private void jMenuItem3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem3ActionPerformed
    JDiskHogPanel panel = new JDiskHogPanel( this );
    panel.scan(FileSystem.settings().getLocalCacheDir());
    JDialog dia = new JDialog(this, "Manage Cache", true);
    dia.add(panel);
    dia.pack();
    dia.setVisible(true);

//JOptionPane.showMessageDialog(this, panel, "Manage Cache", JOptionPane.DEFAULT_OPTION, null);
}//GEN-LAST:event_jMenuItem3ActionPerformed

private void jMenuItem4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem4ActionPerformed
    clearCache();
}//GEN-LAST:event_jMenuItem4ActionPerformed

private void jMenuItem5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem5ActionPerformed
    try {
        String release = AboutUtil.getReleaseTag();
        if (release != null) {
            String surl = "http://www.autoplot.org/Autoplot_Change_Log#" + release;
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
    edit.showDialog(this);
}//GEN-LAST:event_editDomMenuItemActionPerformed

private void statusLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_statusLabelMouseClicked
    statusLabel.setText("");
    statusLabel.setIcon(null);
}//GEN-LAST:event_statusLabelMouseClicked

private void jMenuItem6ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem6ActionPerformed
    JFileChooser chooser= new JFileChooser();
    chooser.setCurrentDirectory( stateSupport.getCurrentFile().getParentFile() );
    if ( stateSupport.isCurrentFileOpened() ) {
        chooser.setSelectedFile( stateSupport.getCurrentFile() );
    }
    if ( JFileChooser.APPROVE_OPTION==chooser.showOpenDialog(this) ) {
            try {
                Application vap = (Application) StatePersistence.restoreState( chooser.getSelectedFile() );
                PropertyEditor edit = new PropertyEditor(vap);
                edit.showDialog(edit);
                
            } catch (IOException ex) {
                Logger.getLogger(AutoPlotUI.class.getName()).log(Level.SEVERE, null, ex);
            }

    }
}//GEN-LAST:event_jMenuItem6ActionPerformed

private void autoLayoutCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autoLayoutCheckBoxMenuItemActionPerformed
    if ( autoLayoutCheckBoxMenuItem.isSelected() ) {
        applicationModel.doAutoLayout();
    }
}//GEN-LAST:event_autoLayoutCheckBoxMenuItemActionPerformed

private void autoplotHelpMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autoplotHelpMenuItemActionPerformed
    helpSystem.displayHelpFromEvent(evt);
}//GEN-LAST:event_autoplotHelpMenuItemActionPerformed

private void jMenuItem7ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem7ActionPerformed
    AddDataSourcePanel add= new AddDataSourcePanel();
    int r= JOptionPane.showConfirmDialog( this, add, "Add Data Source",  JOptionPane.OK_CANCEL_OPTION );
    if ( r==JOptionPane.OK_OPTION ) {
        String jar= add.getDataSetSelector().getValue();
        if ( jar.endsWith("jar") ) {
            try {
                DataSourceRegistry.getInstance().registerDataSourceJar(null, new URL(jar));
            } catch (IOException ex) {
                Logger.getLogger(AddDataSourcePanel.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }
}//GEN-LAST:event_jMenuItem7ActionPerformed

private void pngWalkMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pngWalkMenuItemActionPerformed
    new PngWalkTool1().start( null, this);
}//GEN-LAST:event_pngWalkMenuItemActionPerformed

private void createPngWalkMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_createPngWalkMenuItemActionPerformed
    JythonUtil.invokeScriptSoon( AutoPlotUI.class.getResource("/scripts/pngwalk/makePngWalk.jy"), applicationModel.dom, null );
}//GEN-LAST:event_createPngWalkMenuItemActionPerformed

private void aggregateAllMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aggregateAllMenuItemActionPerformed
    JythonUtil.invokeScriptSoon( AutoPlotUI.class.getResource("/scripts/aggregateAll.jy"), applicationModel.dom, null );
}//GEN-LAST:event_aggregateAllMenuItemActionPerformed

private PropertyChangeListener optionsListener= new PropertyChangeListener() {
    public void propertyChange( PropertyChangeEvent ev ) {
        if ( ev.getPropertyName().equals(Options.PROP_LAYOUTVISIBLE) ) {
            if ( Boolean.TRUE==ev.getNewValue() ) {
                if ( layoutPanel == null ) {
                    layoutPanel = new LayoutPanel();
                    layoutPanel.setApplication(dom);
                }
                int idx= tabs.indexOfTab("style");
                if ( idx==-1 ) idx=  tabs.getTabCount();
                tabs.insertTab("layout", null, layoutPanel, TABS_TOOLTIP, idx+1 );
            } else {
                if ( layoutPanel!=null ) tabs.remove(layoutPanel);
            }
        } else if ( ev.getPropertyName().equals(Options.PROP_DATAVISIBLE ) ) {
            if ( Boolean.TRUE==ev.getNewValue() ) {
                if ( dataPanel == null ) {
                    dataPanel = new DataPanel(applicationModel.dom);
                }
                int idx= tabs.indexOfTab("metadata");
                if ( idx==-1 ) idx=  tabs.getTabCount();
                tabs.insertTab("data", null, dataPanel, TABS_TOOLTIP, idx );
            } else {
                if ( dataPanel!=null ) tabs.remove(dataPanel);
            }
        }
    }
};


    /**
     * @param args the command line arguments
     */
    public static void main( String args[] ) {

        final ArgumentList alm = new ArgumentList("AutoPlotUI");
        alm.addOptionalPositionArgument(0, "URL", null, "initial URL to load");
        alm.addOptionalSwitchArgument("bookmarks", null, "bookmarks", null, "bookmarks to load");
        alm.addOptionalSwitchArgument("port", "p", "port", "-1", "enable scripting via this port");
        alm.addBooleanSwitchArgument("scriptPanel", "s", "scriptPanel", "enable script panel");
        alm.addBooleanSwitchArgument("logConsole", "l", "logConsole", "enable log console");
        alm.addBooleanSwitchArgument("nativeLAF", "n", "nativeLAF", "use the system look and feel");
        alm.addOptionalSwitchArgument("open", "o", "open", "", "open this URI");
        alm.addOptionalSwitchArgument("print", null, "print", "", "print this URI");
        alm.addOptionalSwitchArgument("script", null, "script", "", "run this script after starting.  " +
                "Arguments following are " +
                "passed into the script as sys.argv");
       for ( int i=0; i<args.length; i++ ) {  // kludge for java webstart, which uses "-open" not "--open"
           if ( args[i].equals("-print") ) args[i]="--print";
           if ( args[i].equals("-open") ) args[i]="--open";
        }

        final List<String> scriptArgs= new ArrayList<String>();

        for ( int i=1; i<args.length; i++ ) { // grab any arguments after --script and hide them from the processor.
            if ( args.length>i && args[i-1].equals("--script") ) {
                List<String> apArgs= new ArrayList<String>();
                for ( int j=0; j<=i; j++ ) {
                    apArgs.add(args[j]);
                }
                for ( int j=i; j<args.length; j++ ) {
                    scriptArgs.add(args[j]);
                }
                args= apArgs.toArray( new String[ apArgs.size() ] );
                break;
            }
        }
        alm.process(args);

        String welcome= "welcome to autoplot";
        String tag;
        try {
            tag = AboutUtil.getReleaseTag(APSplash.class);
            if ( tag!=null ) welcome+=" ("+tag+")";
        } catch (IOException ex) {
            Logger.getLogger(AutoPlotUI.class.getName()).log(Level.SEVERE, null, ex);
        }

        System.err.println(welcome);
        logger.info(welcome);
        final ApplicationModel model = new ApplicationModel();
        final String initialURL;
        final String bookmarks;

        if (alm.getValue("URL") != null) {
            initialURL = alm.getValue("URL");
            logger.fine("setting initial URL to >>>" + initialURL + "<<<");

            bookmarks = alm.getValue("bookmarks");
            
        } else if ( alm.getValue("open") !=null ) {
            initialURL = alm.getValue("open");
            logger.fine("setting initial URL to >>>" + initialURL + "<<<");
            bookmarks= null;
            
        } else {
            initialURL = null;
            bookmarks = null;
        }

        if (alm.getBooleanValue("scriptPanel")) {
            logger.fine("enable scriptPanel");
            model.getDocumentModel().getOptions().setScriptVisible(true);
        }

        if (alm.getBooleanValue("logConsole")) {
            logger.fine("enable scriptPanel");
            model.getDocumentModel().getOptions().setLogConsoleVisible(true);
        }

        if (alm.getBooleanValue("nativeLAF")) {
            logger.fine("nativeLAF");
            try {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        logger.fine("invokeLater()");
        
        java.awt.EventQueue.invokeLater(new Runnable() {

            public void run() {
                logger.fine("enter invokeLater");

                logger.addHandler( APSplash.getInstance().getLogHandler() );
                if ( !"true".equals( System.getProperty("java.awt.headless") ) ) {
                    APSplash.showSplash();
                } else {
                    System.err.println("this is autoplot"+APSplash.getVersion());
                }

                OptionsPrefsController opc= new OptionsPrefsController( model.dom.getOptions() );
                opc.loadPreferences();
                
                model.addDasPeersToApp();

                final AutoPlotUI app = new AutoPlotUI(model);

                if (!alm.getValue("port").equals("-1")) {
                    int iport = Integer.parseInt(alm.getValue("port"));
                    app.setupServer(iport, model);
                    model.getDocumentModel().getOptions().setServerEnabled(true);
                }

                Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {

                    public void uncaughtException(Thread t, Throwable e) {
//                        Logger.getLogger("virbo.autoplot").severe("runtime exception: " + e);
                        Logger.getLogger("virbo.autoplot").log(Level.SEVERE, "runtime exception: " + e, e);

                        app.setStatus(ERROR_ICON,"caught exception: " + e.toString());
                        if (e instanceof InconvertibleUnitsException) {
                            // do nothing!!!  this is associated with the state change
                            return;
                        }
                        model.getExceptionHandler().handleUncaught(e);
                    }
                });

                logger.fine("UI.setVisible(true)");
                app.setVisible(true);
                logger.fine("UI is visible");
                APSplash.getInstance().setVisible(false);
                
                if (initialURL != null) {
                    app.dataSetSelector.setValue(initialURL);
                    app.dataSetSelector.maybePlot(false);
                }
                
                if (bookmarks != null) {
                    Runnable run = new Runnable() {
                        public void run() {
                            try {
                                final URL url = new URL(bookmarks);
                                Document doc = AutoplotUtil.readDoc(url.openStream());
                                List<Bookmark> book = Bookmark.parseBookmarks(doc.getDocumentElement());
                                model.setBookmarks(book);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                                model.getExceptionHandler().handleUncaught(ex);
                            }
                        }
                    };
                    new Thread(run, "LoadBookmarksThread").start();

                }

                final String script= alm.getValue("script");
                if ( !script.equals("") ) {
                    app.setStatus("running script "+script);
                    Runnable run= new Runnable() {
                        public void run() {
                            try {
                                JythonUtil.runScript( model, script, scriptArgs.toArray(new String[scriptArgs.size()]) );
                                app.setStatus("ready");
                            } catch (IOException ex) {
                                throw new IllegalArgumentException(ex);
                            }   
                        }
                    };
                    new Thread(run).start();
                } else {
                    app.setStatus("ready");
                }

            }
        });
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
                    Socket socket= rlistener.getSocket();
                    SocketAddress addr=  socket.getRemoteSocketAddress();
                    System.err.println(addr);
                    rhandler.handleRequest(rlistener.getSocket().getInputStream(), model, rlistener.getSocket().getOutputStream());
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
        rlistener.startListening();
    }

    public void setMessage(String message) {
        this.statusLabel.setIcon( IDLE_ICON );
        this.statusLabel.setText(message);
    }
    
    public void setMessage( Icon icon, String message ) {
        String myMess= message;
        if ( message==null ) message= "<null>"; // TODO: fix this later
        if ( myMess.length()>100 ) myMess= myMess.substring(0,100)+"...";
        this.statusLabel.setIcon( icon );
        this.statusLabel.setText(myMess);
        this.statusLabel.setToolTipText(message);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem aboutAutoplotMenuItem;
    private javax.swing.JMenuItem aboutDas2MenuItem;
    private javax.swing.JMenuItem aggregateAllMenuItem;
    private javax.swing.JCheckBoxMenuItem autoLabellingCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem autoLayoutCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem autoRangingCheckBoxMenuItem;
    private javax.swing.JMenuItem autoplotHelpMenuItem;
    private javax.swing.JMenuItem autoplotHomepageButton;
    private javax.swing.JMenu bookmarksMenu;
    private javax.swing.JMenuItem copyDataSetURLMenuItem;
    private javax.swing.JMenuItem copyImageMenuItem;
    private javax.swing.JMenuItem createPngWalkMenuItem;
    private javax.swing.JCheckBoxMenuItem dataPanelCheckBoxMenuItem;
    protected org.virbo.datasource.DataSetSelector dataSetSelector;
    private javax.swing.JCheckBoxMenuItem drawAntiAliasMenuItem;
    private javax.swing.JCheckBoxMenuItem drawGridCheckBox;
    private javax.swing.JMenuItem editDomMenuItem;
    private javax.swing.JMenu editMenu;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JMenuItem fontsAndColorsMenuItem;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenu jMenu2;
    private javax.swing.JMenu jMenu3;
    private javax.swing.JMenu jMenu4;
    private javax.swing.JMenu jMenu5;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JMenuItem jMenuItem2;
    private javax.swing.JMenuItem jMenuItem3;
    private javax.swing.JMenuItem jMenuItem4;
    private javax.swing.JMenuItem jMenuItem5;
    private javax.swing.JMenuItem jMenuItem6;
    private javax.swing.JMenuItem jMenuItem7;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JCheckBoxMenuItem layoutPanelCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem logConsoleMenuItem;
    private javax.swing.JMenu optionsMenu;
    private javax.swing.JCheckBoxMenuItem overRenderingMenuItem;
    private javax.swing.JMenuItem pasteDataSetURLMenuItem;
    private javax.swing.JMenu plotStyleMenu;
    private javax.swing.JMenuItem pngWalkMenuItem;
    private javax.swing.JMenuItem redoMenuItem;
    private javax.swing.JMenu renderingOptionsMenu;
    private javax.swing.JMenuItem resetZoomMenuItem;
    private javax.swing.JCheckBoxMenuItem scriptPanelMenuItem;
    private javax.swing.JCheckBoxMenuItem serverCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem specialEffectsMenuItem;
    private javax.swing.JLabel statusLabel;
    private javax.swing.JPanel tabbedPanelContainer;
    private javax.swing.JCheckBoxMenuItem textAntiAlias;
    private javax.swing.JMenu toolsMenu;
    private javax.swing.JMenuItem undoMenuItem;
    private javax.swing.JMenu undoMultipleMenu;
    private javax.swing.JMenu viewMenu;
    private javax.swing.JMenuItem zoomInMenuItem;
    private javax.swing.JMenuItem zoomOutMenuItem;
    // End of variables declaration//GEN-END:variables

    private void addTools() {
        List<Bookmark> tools= loadTools();
        if ( tools.size()>0 ) {
            toolsMenu.add( new JSeparator() );
        }
        for ( Bookmark t: tools ) {
            final Bookmark tt= t;
            toolsMenu.add( new AbstractAction(t.getTitle()) {
                public void actionPerformed(ActionEvent e) {
                    try {
                        String surl = ((Bookmark.Item) tt).getUrl();
                        JythonUtil.invokeScriptSoon(DataSetURI.getURL(surl),applicationModel.getDocumentModel(),getStatusBarProgressMonitor("done running script") );
                    } catch (MalformedURLException ex) {
                        Logger.getLogger(AutoPlotUI.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            } );
        }
    }

    private List<Bookmark> loadTools() {
        List<Bookmark> tools= new ArrayList();
        File toolsDir= new File( FileSystem.settings().getLocalCacheDir(), "tools" );
        if ( toolsDir.exists() ) {
            File[] ff= toolsDir.listFiles();
            for ( int i=0; i<ff.length; i++ ) {
                Bookmark book= new Bookmark.Item(ff[i].toURI().toString());
                String toolLabel= ff[i].getName();
                // read header comments for label and description.
                try {
                    BufferedReader reader = new BufferedReader(new FileReader(ff[i]));
                    String s = reader.readLine();
                    while (s != null) {
                        if ( s.startsWith("#") ) {
                            if ( s.startsWith("# label:" ) ) {
                               toolLabel= s.substring(9).trim();
                            }
                        } else {
                            break;
                        }
                        s = reader.readLine();
                    }
                    reader.close();
                } catch (IOException iOException) {
                }
                book.setTitle(toolLabel);
                tools.add(book);
            }
        }
        return tools;
    }
}
