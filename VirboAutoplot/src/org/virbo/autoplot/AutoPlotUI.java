/*
 * AutoPlotMatisse.java
 *
 * Created on July 27, 2007, 6:32 AM
 */
package org.virbo.autoplot;

import edu.uiowa.physics.pw.das.components.DasProgressPanel;
import edu.uiowa.physics.pw.das.components.TearoffTabbedPane;
import edu.uiowa.physics.pw.das.components.propertyeditor.PropertyEditor;
import edu.uiowa.physics.pw.das.dasml.DOMBuilder;
import edu.uiowa.physics.pw.das.dasml.SerializeUtil;
import edu.uiowa.physics.pw.das.datum.DatumRange;
import edu.uiowa.physics.pw.das.datum.DatumRangeUtil;
import edu.uiowa.physics.pw.das.util.ArgumentList;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;
import edu.uiowa.physics.pw.das.util.PersistentStateSupport;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
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
import javax.beans.binding.Binding;
import javax.beans.binding.BindingContext;
import javax.beans.binding.BindingConverter;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import org.virbo.autoplot.scriptconsole.JythonScriptPanel;
import org.virbo.autoplot.scriptconsole.LogConsole;
import org.virbo.autoplot.server.RequestHandler;
import org.virbo.autoplot.server.RequestListener;
import org.virbo.autoplot.state.Options;
import org.virbo.autoplot.state.UndoRedoSupport;
import org.virbo.autoplot.util.TickleTimer;
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
    PersistentStateSupport stateSupport;
    UndoRedoSupport undoRedoSupport;
    TickleTimer tickleTimer;
    GuiSupport support;

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
    Options options;
    private Logger logger= Logger.getLogger("virbo.autoplot");

    private Action getUndoAction() {
        return undoRedoSupport.getUndoAction();
    }

    private Action getRedoAction() {
        return undoRedoSupport.getRedoAction();
    }

    private Action getOpenFileAction() {
        return stateSupport.createOpenAction();
    }

    /** Creates new form AutoPlotMatisse */
    public AutoPlotUI(ApplicationModel model) {

        ScriptContext.setApplicationModel(model);
        ScriptContext.setView(this);

        support = new GuiSupport(this);

        applicationModel = model;
        undoRedoSupport = new UndoRedoSupport(applicationModel);

        initComponents();

        setIconImage(new ImageIcon(this.getClass().getResource("logoA16x16.png")).getImage());

        stateSupport = new PersistentStateSupport(this, null, "vap") {

            protected void saveImpl(File f) throws IOException {
                applicationModel.doSave(f);
                applicationModel.addRecent(f.toURI().toString());
                setStatus("saved "+f);
            }

            protected void openImpl(final File file) throws IOException {
                applicationModel.doOpen(file);
                setStatus("opened "+file);
            }
        };
        stateSupport.addPropertyChangeListener(new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent ev) {
                String label;
                if (stateSupport.isCurrentFileOpened()) {
                    label = stateSupport.getCurrentFile() + " " + (stateSupport.isDirty() ? "*" : "");
                    setMessage(label);
                }
            }
        });

        fillFileMenu();

        List<String> urls = new ArrayList();
        List<Bookmark> recent = applicationModel.getRecent();

        for (Bookmark b : recent) {
            urls.add(b.getUrl());
        }

        dataSetSelector.setRecent(urls);
        if (urls.size() > 1) {
            dataSetSelector.setValue(urls.get(urls.size() - 1));
            applicationModel.maybeSetInitialURL(urls.get(urls.size() - 1));
        }

        applicationModel.addPropertyChangeListener(new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(ApplicationModel.PROPERTY_RECENT)) {
                    List<String> urls = new ArrayList();
                    List<Bookmark> recent = applicationModel.getRecent();
                    for (Bookmark b : recent) {
                        urls.add(b.getUrl());
                    }
                    dataSetSelector.setRecent(urls);
                } else if (evt.getPropertyName().equals(ApplicationModel.PROPERTY_STATUS)) {
                    setStatus(applicationModel.getStatus());
                } else if (evt.getPropertyName().equals(ApplicationModel.PROPERTY_BOOKMARKS)) {
                    updateBookmarks();
                }
            }
        });

        dataSetSelector.registerActionTrigger(".*\\.vap", new AbstractAction("load vap") {

            public void actionPerformed(ActionEvent e) {
                try {
                    String vap = dataSetSelector.getValue();
                    setStatus("opening .vap file...");
                    applicationModel.doOpen(DataSetURL.getFile(DataSetURL.getURL(vap), new NullProgressMonitor()));
                    setStatus("opening .vap file... done");
                } catch (IOException ex) {
                    ex.printStackTrace();
                    setStatus(ex.getMessage());
                }
            }
        });

        applicationModel.canvas.getGlassPane().addKeyListener( new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if ( ( e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK ) == KeyEvent.CTRL_DOWN_MASK ) {
                    if ( e.getKeyCode()==KeyEvent.VK_Z ) {
                        undoRedoSupport.undo();
                    } else if ( e.getKeyCode()==KeyEvent.VK_Z ) {
                        undoRedoSupport.undo();
                    }
                }
            }
        } );
        
        addBindings();

        dataSetSelector.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent e) {
                Runnable run= new Runnable() {
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

        if ( model.options.isScriptVisible() ) {
            tabs.add("script", new JythonScriptPanel(applicationModel, this.dataSetSelector ) );
            scriptPanelMenuItem.setEnabled(false);
            scriptPanelMenuItem.setSelected(true);
        }
        if ( model.options.isLogConsoleVisible() ) initLogConsole();

        tickleTimer = new TickleTimer(300, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                undoRedoSupport.pushState(evt);
                stateSupport.markDirty();
                String t = undoRedoSupport.getUndoLabel();
                undoMenuItem.setEnabled(t != null);
                undoMenuItem.setText(t == null ? "Undo" : t);
                t = undoRedoSupport.getRedoLabel();
                redoMenuItem.setEnabled(t != null);
                if (t != null) {
                    redoMenuItem.setText(t == null ? "Redo" : t);
                }
            }
        });

        applicationModel.addPropertyChangeListener(new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(ApplicationModel.PROPERTY_FILL)) {
                    mdp.update();
                }
                if (!stateSupport.isOpening()) {
                    tickleTimer.tickle();
                }
            }
        });

        tabbedPanelContainer.add(tabs, BorderLayout.CENTER);

        tabbedPanelContainer.validate();

        updateBookmarks();

        pack();

    }

    private void addBindings() {

        BindingContext bc = new BindingContext();
        Binding b;

        BindingConverter conv = new BindingConverter() { // for debugging

            public Object sourceToTarget(Object value) {
                return value;
            }

            public Object targetToSource(Object value) {
                return value;
            }
        };

        b = bc.addBinding(applicationModel.canvas, "${antiAlias}", drawAntiAliasMenuItem, "selected");
        b = bc.addBinding(applicationModel.canvas, "${textAntiAlias}", textAntiAlias, "selected");
        this.dataSetSelector.addPropertyChangeListener("value",new PropertyChangeListener() { //one-way binding
            public void propertyChange(PropertyChangeEvent evt) {
                applicationModel.setDataSourceURL(dataSetSelector.getValue());
            }
        });

        b.setConverter(conv);
        bc.bind();
    }

    private void fillFileMenu() {

        fileMenu.add(dataSetSelector.getOpenLocalAction());
        fileMenu.add(dataSetSelector.getRecentMenu());
        fileMenu.add(stateSupport.createSaveAsAction());
        /*new AbstractAction( "save as" ) {
        public void actionPerformed( ActionEvent e ) {
        JFileChooser chooser= new JFileChooser();
        if ( chooser.showSaveDialog( AutoPlotMatisse.this )==JFileChooser.APPROVE_OPTION ) {
        stateSupport.
        }
        new SaveAsDialog( AutoPlotMatisse.this, stateSupport,  applicationModel, true ).setVisible(true);
        };
        } );
         */

        fileMenu.add(stateSupport.createSaveAction());
        fileMenu.add(new AbstractAction("Save With Data...") {
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser();
                applicationModel.setUseEmbeddedDataSet(true);
                stateSupport.createSaveAction().actionPerformed(e);
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
        LogConsole lc = new LogConsole();
        Handler h = lc.getHandler();
        Logger.getLogger("virbo").setLevel(Level.ALL);
        Logger.getLogger("virbo").addHandler(h);
        
        setMessage("log console added");
        tabs.addTab( "console", lc );
        applicationModel.options.setLogConsoleVisible(true);
        logConsoleMenuItem.setEnabled(false);
        logConsoleMenuItem.setSelected(true);
    }

    private void plotUrl() {
        try {
            Logger.getLogger("ap").info("plotUrl()");
            String surl = (String) dataSetSelector.getValue();
            applicationModel.addRecent(surl);
            applicationModel.resetDataSetSourceURL(surl, new NullProgressMonitor() {

                public void setProgressMessage(String message) {
                    setStatus(message);
                }
            });
        } catch (RuntimeException ex) {
            applicationModel.application.getExceptionHandler().handle(ex);
        }
    }

    public void setStatus(String message) {
        logger.info(message);
        setMessage(message);
        if ( message.equals("") ) {
            System.err.println("here");
        }
    }

    private void clearCache() {
        try {
            if (applicationModel.clearCache()) {
                setStatus("cache cleared");
            } else {
                setStatus("unable to clear cache");
                JOptionPane.showMessageDialog(this, "unable to clear cache");
            }
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "unable to clear cache: " + ex.getMessage());
        }
    }

    private void updateBookmarks() {
        List<Bookmark> bookmarks = applicationModel.getBookmarks();
        bookmarksMenu.removeAll();

        bookmarksMenu.add(new AbstractAction("Add Bookmark") {
            public void actionPerformed(ActionEvent e) {
                applicationModel.addBookmark( dataSetSelector.getValue() );
            }
        });

        bookmarksMenu.add(new AbstractAction("Manage Bookmarks") {

            public void actionPerformed(ActionEvent e) {
                BookmarksManager man = new BookmarksManager(AutoPlotUI.this, true);
                man.setList(applicationModel.getBookmarks());
                man.setVisible(true);
                applicationModel.setBookmarks(man.getList());
            }
        });

        bookmarksMenu.add( new JSeparator() );
        bookmarksMenu.add( new AbstractAction("Make Aggregation From URL") {
            public void actionPerformed(ActionEvent e) {
                String s= dataSetSelector.getValue();
                String agg= org.virbo.datasource.Util.makeAggregation(s);
                if ( agg!=null ) {
                    dataSetSelector.setValue(agg);
                } else {
                    JOptionPane.showMessageDialog( AutoPlotUI.this, "Unable to create aggregation spec, couldn't find yyyymmdd.");
                }
            }
        });
        bookmarksMenu.add(new AbstractAction("Reset Cache") {

            public void actionPerformed(ActionEvent e) {
                clearCache();
            }
        });

        bookmarksMenu.add(new JSeparator());

        for (int i = 0; i < bookmarks.size(); i++) {
            final Bookmark book = bookmarks.get(i);
            JMenuItem mi = new JMenuItem(new AbstractAction(book.getTitle()) {

                public void actionPerformed(ActionEvent e) {
                    dataSetSelector.setValue(book.getUrl());
                    dataSetSelector.maybePlot();
                }
            });
            mi.setToolTipText(book.getUrl());
            bookmarksMenu.add(mi);
        }
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
        jSeparator1 = new javax.swing.JSeparator();
        editModelMenuItem = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JSeparator();
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
        plotStyleMenu = new javax.swing.JMenu();
        fontsAndColorsMenuItem = new javax.swing.JMenuItem();
        jMenu1 = new javax.swing.JMenu();
        scriptPanelMenuItem = new javax.swing.JCheckBoxMenuItem();
        logConsoleMenuItem = new javax.swing.JCheckBoxMenuItem();
        bookmarksMenu = new javax.swing.JMenu();
        helpMenu = new javax.swing.JMenu();
        aboutAutoplotMenuItem = new javax.swing.JMenuItem();
        aboutDas2MenuItem = new javax.swing.JMenuItem();
        autoplotHomepageButton = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("VxOware Autoplot");

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

        undoMenuItem.setAction(getUndoAction());
        undoMenuItem.setText("Undo");
        editMenu.add(undoMenuItem);

        redoMenuItem.setAction(getRedoAction());
        redoMenuItem.setText("Redo");
        editMenu.add(redoMenuItem);
        editMenu.add(jSeparator1);

        editModelMenuItem.setText("Edit DOM");
        editModelMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editModelMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(editModelMenuItem);
        editMenu.add(jSeparator2);

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
        scriptPanelMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                scriptPanelMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(scriptPanelMenuItem);

        logConsoleMenuItem.setText("Log Console");
        logConsoleMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                logConsoleMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(logConsoleMenuItem);

        optionsMenu.add(jMenu1);

        jMenuBar1.add(optionsMenu);

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
                .add(dataSetSelector, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 636, Short.MAX_VALUE)
                .addContainerGap())
            .add(layout.createSequentialGroup()
                .add(statusLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 513, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(147, Short.MAX_VALUE))
            .add(tabbedPanelContainer, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 660, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(dataSetSelector, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(tabbedPanelContainer, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 385, Short.MAX_VALUE)
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
        plotUrl();
    }//GEN-LAST:event_dataSetSelectorActionPerformed

    private void fileMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fileMenuActionPerformed
// TODO add your handling code here:
    }//GEN-LAST:event_fileMenuActionPerformed

    private void zoomOutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomOutMenuItemActionPerformed
        DatumRange dr = DatumRangeUtil.rescale(applicationModel.getXAxis().getDatumRange(), -0.333, 1.333);
        applicationModel.getXAxis().setDatumRange(dr);
    }//GEN-LAST:event_zoomOutMenuItemActionPerformed

    private void zoomInMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomInMenuItemActionPerformed
        DatumRange dr = DatumRangeUtil.rescale(applicationModel.getXAxis().getDatumRange(), 0.25, 0.75);
        applicationModel.getXAxis().setDatumRange(dr);
    }//GEN-LAST:event_zoomInMenuItemActionPerformed

    private void resetZoomMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetZoomMenuItemActionPerformed
        applicationModel.resetZoom();
    }//GEN-LAST:event_resetZoomMenuItemActionPerformed

    private void fontsAndColorsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fontsAndColorsMenuItemActionPerformed
        new FontAndColorsDialog(this, true, applicationModel).setVisible(true);
    }//GEN-LAST:event_fontsAndColorsMenuItemActionPerformed

    private void specialEffectsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_specialEffectsMenuItemActionPerformed
        applicationModel.setSpecialEffects(specialEffectsMenuItem.isSelected());
    }//GEN-LAST:event_specialEffectsMenuItemActionPerformed

    private void drawAntiAliasMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_drawAntiAliasMenuItemActionPerformed
        applicationModel.setDrawAntiAlias(drawAntiAliasMenuItem.isSelected());
    }//GEN-LAST:event_drawAntiAliasMenuItemActionPerformed

    private void textAntiAliasActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_textAntiAliasActionPerformed
        applicationModel.getCanvas().setTextAntiAlias(textAntiAlias.isSelected());
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

            List<String> bi = Util.getBuildInfos();
            for (String ss : bi) {
                buffy.append("    <li>" + ss + "");
            }
            buffy.append("    </p></html>");

            System.err.println(buffy.toString());
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

private void editModelMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editModelMenuItemActionPerformed
    PropertyEditor edit = new PropertyEditor(this.applicationModel);
    edit.showDialog(this);
}//GEN-LAST:event_editModelMenuItemActionPerformed

private void scriptPanelMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_scriptPanelMenuItemActionPerformed
    if ( !applicationModel.options.isScriptVisible() ) {
        tabs.insertTab("script", null, new JythonScriptPanel(applicationModel, this.dataSetSelector ), TABS_TOOLTIP, 4  );
        applicationModel.options.setScriptVisible(true);
    }
    scriptPanelMenuItem.setEnabled(false);
}//GEN-LAST:event_scriptPanelMenuItemActionPerformed

private void logConsoleMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_logConsoleMenuItemActionPerformed
    if ( !applicationModel.options.isLogConsoleVisible() ) {
       initLogConsole();
    }
    logConsoleMenuItem.setEnabled(false);
}//GEN-LAST:event_logConsoleMenuItemActionPerformed

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
            model.options.setScriptVisible(true);
        }

        if (alm.getBooleanValue("logConsole")) {
            model.options.setLogConsoleVisible(true);
        }
        
        if ( alm.getBooleanValue("nativeLAF") ) {
            try
            {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        
        java.awt.EventQueue.invokeLater(new Runnable() {

            public void run() {
                final AutoPlotUI app = new AutoPlotUI(model);

                if (!alm.getValue("port").equals("-1")) {
                    int iport = Integer.parseInt(alm.getValue("port"));
                    app.setupServer(iport, model);
                }

                Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {

                    public void uncaughtException(Thread t, Throwable e) {
                        Logger.getLogger("virbo.autoplot").severe("runtime exception: "+e);
                        app.setStatus("caught exception: " + e.toString() );
                        model.application.getExceptionHandler().handleUncaught(e);
                    }
                });
                app.setVisible(true);
                if (initialURL != null) {
                    app.dataSetSelector.setValue(initialURL);
                    app.dataSetSelector.maybePlot();
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

        final RequestListener rlistener = new RequestListener();
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
    private javax.swing.JMenu editMenu;
    private javax.swing.JMenuItem editModelMenuItem;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JMenuItem fontsAndColorsMenuItem;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JCheckBoxMenuItem logConsoleMenuItem;
    private javax.swing.JMenu optionsMenu;
    private javax.swing.JMenuItem pasteDataSetURLMenuItem;
    private javax.swing.JMenu plotStyleMenu;
    private javax.swing.JMenuItem redoMenuItem;
    private javax.swing.JMenu renderingOptionsMenu;
    private javax.swing.JMenuItem resetZoomMenuItem;
    private javax.swing.JCheckBoxMenuItem scriptPanelMenuItem;
    private javax.swing.JCheckBoxMenuItem specialEffectsMenuItem;
    private javax.swing.JLabel statusLabel;
    private javax.swing.JPanel tabbedPanelContainer;
    private javax.swing.JCheckBoxMenuItem textAntiAlias;
    private javax.swing.JMenuItem undoMenuItem;
    private javax.swing.JMenu viewMenu;
    private javax.swing.JMenuItem zoomInMenuItem;
    private javax.swing.JMenuItem zoomOutMenuItem;
    // End of variables declaration//GEN-END:variables
}
