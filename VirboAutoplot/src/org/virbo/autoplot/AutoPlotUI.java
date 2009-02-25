/*
 * AutoPlotMatisse.java
 *
 * Created on July 27, 2007, 6:32 AM
 */
package org.virbo.autoplot;

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
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
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
import org.virbo.autoplot.dom.Panel;
import org.virbo.autoplot.scriptconsole.JythonScriptPanel;
import org.virbo.autoplot.scriptconsole.LogConsole;
import org.virbo.autoplot.server.RequestHandler;
import org.virbo.autoplot.server.RequestListener;
import org.virbo.autoplot.dom.Options;
import org.virbo.autoplot.state.UndoRedoSupport;
import org.virbo.autoplot.util.TickleTimer;
import org.virbo.datasource.DataSetSelector;
import org.virbo.datasource.DataSetURL;
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
    PersistentStateSupport.SerializationStrategy serStrategy = new PersistentStateSupport.SerializationStrategy() {

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
    
    private Logger logger = Logger.getLogger("virbo.autoplot");
    private JythonScriptPanel scriptPanel;
    private LogConsole logConsole;
    private RequestListener rlistener;
    private JDialog fontAndColorsDialog = null;
    private BookmarksManager bookmarksManager = null;
    
    private static String RESOURCES= "/org/virbo/autoplot/resources/";
    public static final Icon WARNING_ICON= new ImageIcon( AutoPlotUI.class.getResource(RESOURCES+"warning-icon.png") );
    public static final Icon ERROR_ICON= new ImageIcon( AutoPlotUI.class.getResource(RESOURCES+"error-icon.png") );
    public static final Icon BUSY_ICON= new ImageIcon( AutoPlotUI.class.getResource(RESOURCES+"spinner_16.gif") );
    public static final Icon READY_ICON= new ImageIcon( AutoPlotUI.class.getResource(RESOURCES+"indProgress0.png") );
    public static final Icon IDLE_ICON= new ImageIcon( AutoPlotUI.class.getResource(RESOURCES+"idle-icon.png") );
        
    
    /** Creates new form AutoPlotMatisse */
    public AutoPlotUI(ApplicationModel model) {

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

        stateSupport = AutoplotUtil.getPersistentStateSupport(this, applicationModel);

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

        dataSetSelector.registerActionTrigger(".*\\.vap", new AbstractAction("load vap") {

            public void actionPerformed(ActionEvent e) {
                try {
                    String vap = dataSetSelector.getValue();
                    setStatus(BUSY_ICON,"opening .vap file " + vap + "...");
                    applicationModel.doOpen(DataSetURL.getFile(DataSetURL.getURL(vap), new NullProgressMonitor()));
                    dataSetSelector.setValue(vap);
                    setStatus("opening .vap file " + vap + "... done");
                } catch (IOException ex) {
                    ex.printStackTrace();
                    setStatus(ERROR_ICON,ex.getMessage());
                }
            }
        });

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
        tabs.insertTab("plot", null, scrollPane, TABS_TOOLTIP, 0);

        //tabs.insertTab("plot", null, applicationModel.getCanvas(), TOOLTIP, 0);
        tabs.insertTab("axes", null, new AxisPanel(applicationModel), TABS_TOOLTIP, 1);
        tabs.insertTab("style", null, new PlotStylePanel(applicationModel), TABS_TOOLTIP, 2);

        final MetaDataPanel mdp = new MetaDataPanel(applicationModel);
        tabs.insertTab("metadata", null, mdp, TABS_TOOLTIP, 3);

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
                
                //tickleTimer.getMessages();
                undoRedoSupport.pushState(evt);
                stateSupport.markDirty();
                
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        refreshUndoRedoLabel();
                    }
                });
            }
        });

        applicationModel.dom.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                //String propName = evt.getPropertyName();
                //if (propName.equals("bounds") || propName.equals("pendingChanges") || propName.equals("recent") || propName.equals("status") || propName.equals("ticks") || propName.contains("PerMillisecond")) return;
                if ( dom.getController().isValueAdjusting() ) return;
                logger.finer( "state change: "+evt.getPropertyName() );
                if (!stateSupport.isOpening() && !stateSupport.isSaving() && !applicationModel.isRestoringState()) { // TODO: list the props we want!
                    tickleTimer.tickle(evt.getPropertyName() + " from " + evt.getSource());
                }
            }
        });

        tabbedPanelContainer.add(tabs, BorderLayout.CENTER);

        tabbedPanelContainer.validate();

        updateBookmarks();

        pack();

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
        bind( bc, dom.getOptions(), Options.PROP_OVERRENDERING, overRenderingCheckBox, "selected" );
        bind( bc, dom.getOptions(), Options.PROP_DRAWGRID, drawGridCheckBox, "selected" );
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

    private void fillFileMenu() {

        fileMenu.add(dataSetSelector.getOpenLocalAction());
        fileMenu.add(dataSetSelector.getRecentMenu());
        fileMenu.add(stateSupport.createSaveAsAction());

        fileMenu.add(stateSupport.createSaveAction());
        fileMenu.add(new AbstractAction("Save With Data...") {

            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser();
                applicationModel.setUseEmbeddedDataSet(true);
                stateSupport.saveAs();
            }
        });
        fileMenu.addSeparator();
        fileMenu.add(applicationModel.getCanvas().PRINT_ACTION);

        JMenu printToMenu = new JMenu("Print to");
        fileMenu.add(printToMenu);

        JMenuItem item = new JMenuItem(applicationModel.getCanvas().SAVE_AS_PDF_ACTION);
        item.setText("PDF...");
        printToMenu.add(item);

        item = new JMenuItem(applicationModel.getCanvas().SAVE_AS_SVG_ACTION);
        item.setText("SVG...");
        printToMenu.add(item);

        item = new JMenuItem(applicationModel.getCanvas().SAVE_AS_PNG_ACTION);
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
            File dir = DataSetURL.getFile(DataSetURL.getURL(surlDir), new NullProgressMonitor());
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

    private void plotUrl() {
        try {
            Logger.getLogger("ap").info("plotUrl()");
            final String surl = (String) dataSetSelector.getValue();
            applicationModel.addRecent(surl);
            applicationModel.resetDataSetSourceURL(surl, new NullProgressMonitor() {
                public void setProgressMessage(String message) {
                    setStatus(BUSY_ICON,message);
                }
                @Override
                public void finished() {
                    setStatus(IDLE_ICON,"finished "+surl);
                }
                
            });
        } catch (RuntimeException ex) {
            applicationModel.application.getExceptionHandler().handle(ex);
            setStatus(ERROR_ICON,ex.getMessage());
        }
    }

    private void plotAnotherUrl() {
        try {
            Logger.getLogger("ap").info("plotAnotherUrl()");
            final String surl = (String) dataSetSelector.getValue();
            applicationModel.addRecent(surl);
            Panel panel= dom.getController().addPanel( null ,null );
            dom.getController().getDataSourceFilterFor(panel).setUri(surl);
            
        } catch (RuntimeException ex) {
            applicationModel.application.getExceptionHandler().handle(ex);
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

        bookmarksMenu.add( new AbstractAction("Export Recent...") {
            public void actionPerformed(ActionEvent e) {
                support.exportRecent(AutoPlotUI.this);
            }
        } );

        bookmarksMenu.add(new JSeparator());
        JMenuItem item = new JMenuItem(new AbstractAction("Make Aggregation From URL") {

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

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        tabbedPanelContainer = new javax.swing.JPanel();
        dataSetSelector = new org.virbo.datasource.DataSetSelector();
        statusLabel = new javax.swing.JLabel();
        jMenuBar1 = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        editMenu = new javax.swing.JMenu();
        undoMenuItem = new javax.swing.JMenuItem();
        redoMenuItem = new javax.swing.JMenuItem();
        undoMultipleMenu = new javax.swing.JMenu();
        jSeparator2 = new javax.swing.JSeparator();
        editDomMenuItem = new javax.swing.JMenuItem();
        jSeparator1 = new javax.swing.JSeparator();
        pasteDataSetURLMenuItem = new javax.swing.JMenuItem();
        copyDataSetURLMenuItem = new javax.swing.JMenuItem();
        copyImageMenuItem = new javax.swing.JMenuItem();
        viewMenu = new javax.swing.JMenu();
        resetZoomMenuItem = new javax.swing.JMenuItem();
        zoomInMenuItem = new javax.swing.JMenuItem();
        zoomOutMenuItem = new javax.swing.JMenuItem();
        optionsMenu = new javax.swing.JMenu();
        overRenderingCheckBox = new javax.swing.JMenu();
        textAntiAlias = new javax.swing.JCheckBoxMenuItem();
        drawAntiAliasMenuItem = new javax.swing.JCheckBoxMenuItem();
        specialEffectsMenuItem = new javax.swing.JCheckBoxMenuItem();
        jCheckBoxMenuItem1 = new javax.swing.JCheckBoxMenuItem();
        drawGridCheckBox = new javax.swing.JCheckBoxMenuItem();
        plotStyleMenu = new javax.swing.JMenu();
        fontsAndColorsMenuItem = new javax.swing.JMenuItem();
        jMenu1 = new javax.swing.JMenu();
        scriptPanelMenuItem = new javax.swing.JCheckBoxMenuItem();
        logConsoleMenuItem = new javax.swing.JCheckBoxMenuItem();
        serverCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        jMenu2 = new javax.swing.JMenu();
        jMenuItem1 = new javax.swing.JMenuItem();
        jMenuItem2 = new javax.swing.JMenuItem();
        jMenu3 = new javax.swing.JMenu();
        jMenuItem3 = new javax.swing.JMenuItem();
        jMenuItem4 = new javax.swing.JMenuItem();
        bookmarksMenu = new javax.swing.JMenu();
        helpMenu = new javax.swing.JMenu();
        aboutAutoplotMenuItem = new javax.swing.JMenuItem();
        jMenuItem5 = new javax.swing.JMenuItem();
        aboutDas2MenuItem = new javax.swing.JMenuItem();
        autoplotHomepageButton = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Autoplot");

        tabbedPanelContainer.setLayout(new java.awt.BorderLayout());

        dataSetSelector.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSetSelectorActionPerformed(evt);
            }
        });

        statusLabel.setFont(statusLabel.getFont().deriveFont(statusLabel.getFont().getSize()-2f));
        statusLabel.setText("starting...");

        fileMenu.setText("File");
        fileMenu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fileMenuActionPerformed(evt);
            }
        });
        jMenuBar1.add(fileMenu);

        editMenu.setText("Edit");

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

        overRenderingCheckBox.setText("Rendering Options");

        textAntiAlias.setSelected(true);
        textAntiAlias.setText("Text Antialias");
        textAntiAlias.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                textAntiAliasActionPerformed(evt);
            }
        });
        overRenderingCheckBox.add(textAntiAlias);

        drawAntiAliasMenuItem.setSelected(true);
        drawAntiAliasMenuItem.setText("Graphics Antialias");
        drawAntiAliasMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                drawAntiAliasMenuItemActionPerformed(evt);
            }
        });
        overRenderingCheckBox.add(drawAntiAliasMenuItem);

        specialEffectsMenuItem.setText("Special Effects");
        specialEffectsMenuItem.setToolTipText("Enable animated axes and other visual clues");
        specialEffectsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                specialEffectsMenuItemActionPerformed(evt);
            }
        });
        overRenderingCheckBox.add(specialEffectsMenuItem);

        jCheckBoxMenuItem1.setSelected(true);
        jCheckBoxMenuItem1.setText("Over-Rendering");
        jCheckBoxMenuItem1.setToolTipText("Render (and load) data outside plot bounds to improve appearance.");
        overRenderingCheckBox.add(jCheckBoxMenuItem1);

        drawGridCheckBox.setSelected(true);
        drawGridCheckBox.setText("Draw Grid");
        overRenderingCheckBox.add(drawGridCheckBox);

        optionsMenu.add(overRenderingCheckBox);

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

        optionsMenu.add(jMenu1);

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

        jMenuBar1.add(optionsMenu);

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

        jMenuBar1.add(jMenu3);

        bookmarksMenu.setText("Bookmarks");
        jMenuBar1.add(bookmarksMenu);

        helpMenu.setText("Help");
        helpMenu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                helpMenuActionPerformed(evt);
            }
        });

        aboutAutoplotMenuItem.setText("About Autoplot");
        aboutAutoplotMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                aboutAutoplotMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(aboutAutoplotMenuItem);

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

        jMenuBar1.add(helpMenu);

        setJMenuBar(jMenuBar1);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(dataSetSelector, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 670, Short.MAX_VALUE)
                .addContainerGap())
            .add(tabbedPanelContainer, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 694, Short.MAX_VALUE)
            .add(layout.createSequentialGroup()
                .add(statusLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 513, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(dataSetSelector, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(tabbedPanelContainer, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 398, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(statusLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 23, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
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
        dom.getController().getPlot().getController().resetZoom();
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
            URL aboutHtml = AutoPlotUI.class.getResource("aboutAutoplot.html");

            BufferedReader reader = new BufferedReader(new InputStreamReader(aboutHtml.openStream()));
            String s = reader.readLine();
            while (s != null) {
                buffy.append(s + "");
                s = reader.readLine();
            }
            reader.close();

            buffy.append("    <h2>Build Information:</h2>");
            buffy.append("<ul>");
            buffy.append("<li>release tag: " + AboutUtil.getReleaseTag() + "</li>");

            List<String> bi = Util.getBuildInfos();
            for (String ss : bi) {
                buffy.append("    <li>" + ss + "");
            }
            buffy.append("<ul>    </p></html>");

            JOptionPane.showMessageDialog(this, buffy.toString());

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
            String surl = "http://www.autoplot.org/autoplot/index.php/Autoplot_Change_Log#" + release;
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

    /**
     * @param args the command line arguments
     */
    public static void main(final String args[]) {

        final ArgumentList alm = new ArgumentList("AutoPlotUI");
        alm.addOptionalPositionArgument(0, "URL", null, "initial URL to load");
        alm.addOptionalPositionArgument(1, "bookmarks", null, "bookmarks to load");
        alm.addOptionalSwitchArgument("port", "p", "port", "-1", "enable scripting via this port");
        alm.addBooleanSwitchArgument("scriptPanel", "s", "scriptPanel", "enable script panel");
        alm.addBooleanSwitchArgument("logConsole", "l", "logConsole", "enable log console");
        alm.addBooleanSwitchArgument("nativeLAF", "n", "nativeLAF", "use the system look and feel");
        alm.process(args);

        System.err.println("welcome to autoplot");
        Logger.getLogger("ap").info("welcome to autoplot ");
        final ApplicationModel model = new ApplicationModel();
        final String initialURL;
        final String bookmarks;

        if (alm.getValue("URL") != null) {
            initialURL = alm.getValue("URL");
            Logger.getLogger("ap").info("setting initial URL to >>>" + initialURL + "<<<");

            bookmarks = alm.getValue("bookmarks");

        } else {
            initialURL = null;
            bookmarks = null;
        }

        if (alm.getBooleanValue("scriptPanel")) {
            model.getDocumentModel().getOptions().setScriptVisible(true);
        }

        if (alm.getBooleanValue("logConsole")) {
            model.getDocumentModel().getOptions().setLogConsoleVisible(true);
        }

        if (alm.getBooleanValue("nativeLAF")) {
            try {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        java.awt.EventQueue.invokeLater(new Runnable() {

            public void run() {
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
                        model.application.getExceptionHandler().handleUncaught(e);
                    }
                });

                app.setVisible(true);
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
                                List<Bookmark> book = Bookmark.parseBookmarks(doc);
                                model.setBookmarks(book);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                                model.getCanvas().getApplication().getExceptionHandler().handle(ex);
                            }
                        }
                    };
                    new Thread(run, "LoadBookmarksThread").start();

                }
                
                app.setStatus("ready");

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
        this.statusLabel.setIcon( icon );
        this.statusLabel.setText(message);
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem aboutAutoplotMenuItem;
    private javax.swing.JMenuItem aboutDas2MenuItem;
    private javax.swing.JMenuItem autoplotHomepageButton;
    private javax.swing.JMenu bookmarksMenu;
    private javax.swing.JMenuItem copyDataSetURLMenuItem;
    private javax.swing.JMenuItem copyImageMenuItem;
    protected org.virbo.datasource.DataSetSelector dataSetSelector;
    private javax.swing.JCheckBoxMenuItem drawAntiAliasMenuItem;
    private javax.swing.JCheckBoxMenuItem drawGridCheckBox;
    private javax.swing.JMenuItem editDomMenuItem;
    private javax.swing.JMenu editMenu;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JMenuItem fontsAndColorsMenuItem;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JCheckBoxMenuItem jCheckBoxMenuItem1;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenu jMenu2;
    private javax.swing.JMenu jMenu3;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JMenuItem jMenuItem2;
    private javax.swing.JMenuItem jMenuItem3;
    private javax.swing.JMenuItem jMenuItem4;
    private javax.swing.JMenuItem jMenuItem5;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JCheckBoxMenuItem logConsoleMenuItem;
    private javax.swing.JMenu optionsMenu;
    private javax.swing.JMenu overRenderingCheckBox;
    private javax.swing.JMenuItem pasteDataSetURLMenuItem;
    private javax.swing.JMenu plotStyleMenu;
    private javax.swing.JMenuItem redoMenuItem;
    private javax.swing.JMenuItem resetZoomMenuItem;
    private javax.swing.JCheckBoxMenuItem scriptPanelMenuItem;
    private javax.swing.JCheckBoxMenuItem serverCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem specialEffectsMenuItem;
    private javax.swing.JLabel statusLabel;
    private javax.swing.JPanel tabbedPanelContainer;
    private javax.swing.JCheckBoxMenuItem textAntiAlias;
    private javax.swing.JMenuItem undoMenuItem;
    private javax.swing.JMenu undoMultipleMenu;
    private javax.swing.JMenu viewMenu;
    private javax.swing.JMenuItem zoomInMenuItem;
    private javax.swing.JMenuItem zoomOutMenuItem;
    // End of variables declaration//GEN-END:variables
}
