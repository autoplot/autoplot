/*
 * GuiSupport.java
 *
 * Created on November 30, 2007, 5:04 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.awt.Component;
import java.awt.Frame;
import org.das2.components.DasProgressPanel;
import org.das2.graph.DasCanvas;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ComponentInputMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import org.das2.components.propertyeditor.PropertyEditor;
import org.das2.event.DasMouseInputAdapter;
import org.das2.event.MouseModule;
import org.das2.event.PointSlopeDragRenderer;
import org.das2.graph.DasAxis;
import org.das2.graph.DasPlot;
import org.virbo.autoplot.bookmarks.Bookmark;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.autoplot.dom.Axis;
import org.virbo.autoplot.dom.BindingModel;
import org.virbo.autoplot.dom.DataSourceController;
import org.virbo.autoplot.dom.DataSourceFilter;
import org.virbo.autoplot.dom.DomUtil;
import org.virbo.autoplot.dom.Panel;
import org.virbo.autoplot.dom.Plot;
import org.virbo.autoplot.dom.PlotController;
import org.virbo.autoplot.layout.LayoutConstants;
import org.virbo.autoplot.transferrable.ImageSelection;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetSelector;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSourceRegistry;
import org.virbo.datasource.datasource.DataSourceFormat;

/**
 *
 * @author jbf
 */
public class GuiSupport {

    AutoPlotUI parent;

    public GuiSupport(AutoPlotUI parent) {
        this.parent = parent;
    }

    public void doPasteDataSetURL() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable contents = clipboard.getContents(null);
        boolean hasTransferableText =
                (contents != null) &&
                contents.isDataFlavorSupported(DataFlavor.stringFlavor);
        String result = null;
        if (hasTransferableText) {
            try {
                result = (String) contents.getTransferData(DataFlavor.stringFlavor);
            } catch (UnsupportedFlavorException ex) {
                //highly unlikely since we are using a standard DataFlavor
                System.out.println(ex);
                ex.printStackTrace();
            } catch (IOException ex) {
                System.out.println(ex);
                ex.printStackTrace();
            }
        }
        if (result != null) {
            parent.dataSetSelector.setValue(result);
        }
    }

    public void doCopyDataSetURL() {
        StringSelection stringSelection = new StringSelection(parent.dataSetSelector.getValue());
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, new ClipboardOwner() {

            public void lostOwnership(Clipboard clipboard, Transferable contents) {
            }
        });
    }

    public void doCopyDataSetImage() {
        Runnable run = new Runnable() {

            public void run() {
                ImageSelection imageSelection = new ImageSelection();
                DasCanvas c = parent.applicationModel.canvas;
                Image i = c.getImage(c.getWidth(), c.getHeight());
                imageSelection.setImage(i);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(imageSelection, new ClipboardOwner() {

                    public void lostOwnership(Clipboard clipboard, Transferable contents) {
                    }
                });
            }
        };
        new Thread(run, "CopyDataSetToClipboardThread").start();
    }

    public static void editPanel( ApplicationModel applicationModel, Component parent ) {
        
        Application dom = applicationModel.dom;

        AddPanelDialog dia = new AddPanelDialog( (Frame)SwingUtilities.getWindowAncestor(parent), true);

        String suri= dom.getController().getFocusUri();
        Pattern hasKidsPattern= Pattern.compile("vap\\+internal\\:(data_\\d+)(,(data_\\d+))?+(,(data_\\d+))?+");
        Matcher m= hasKidsPattern.matcher(suri);

        dia.getPrimaryDataSetSelector().setRecent(AutoplotUtil.getUrls(applicationModel.getRecent()));
        dia.getSecondaryDataSetSelector().setRecent(AutoplotUtil.getUrls(applicationModel.getRecent()));
        dia.getTertiaryDataSetSelector().setRecent(AutoplotUtil.getUrls(applicationModel.getRecent()));

        if ( m.matches() ) {
            int depCount= m.group(5)!=null ? 2 : ( m.group(3)!=null ? 1 : ( m.group(1)!=null ? 0 : -1 ) );
            dia.setDepCount(depCount);
            if ( m.group(1)!=null ) {
                DataSourceFilter dsf= (DataSourceFilter) DomUtil.getElementById( dom, m.group(1) );
                dia.getPrimaryDataSetSelector().setValue(dsf.getUri());
            }
            if ( m.group(3)!=null ) {
                DataSourceFilter dsf= (DataSourceFilter) DomUtil.getElementById( dom, m.group(3) );
                dia.getSecondaryDataSetSelector().setValue(dsf.getUri());
                
            }
            if ( m.group(5)!=null ) {
                DataSourceFilter dsf= (DataSourceFilter) DomUtil.getElementById( dom, m.group(5) );
                dia.getTertiaryDataSetSelector().setValue(dsf.getUri());
            }
        } else {
            dia.getPrimaryDataSetSelector().setValue( suri );
        }
        
        dia.setVisible(true);
        if (dia.isCancelled()) {
            return;
        }
        handleAddPanelDialog(dia, dom, applicationModel);

    }

    void addPanel() {

        ApplicationModel applicationModel = parent.applicationModel;
        DataSetSelector dataSetSelector = parent.dataSetSelector;
        Application dom = applicationModel.dom;

        AddPanelDialog dia = new AddPanelDialog((Frame) SwingUtilities.getWindowAncestor(parent), true);
        dia.getPrimaryDataSetSelector().setValue(dataSetSelector.getValue());
        dia.getSecondaryDataSetSelector().setValue(dataSetSelector.getValue());
        dia.getTertiaryDataSetSelector().setValue(dataSetSelector.getValue());
        dia.getPrimaryDataSetSelector().setRecent(AutoplotUtil.getUrls(applicationModel.getRecent()));
        dia.getSecondaryDataSetSelector().setRecent(AutoplotUtil.getUrls(applicationModel.getRecent()));
        dia.getTertiaryDataSetSelector().setRecent(AutoplotUtil.getUrls(applicationModel.getRecent()));

        dia.setVisible(true);
        if (dia.isCancelled()) {
            return;
        }
        handleAddPanelDialog(dia, dom, applicationModel);

    }

    Action getDumpDataAction() {
        return new AbstractAction("Export Data...") {

            public void actionPerformed(ActionEvent e) {
                DataSourceController dsc = parent.applicationModel.getDataSourceFilterController();

                if (dsc.getFillDataSet() == null) {
                    JOptionPane.showMessageDialog(parent, "No Data to Export.");
                    return;
                }

                JFileChooser chooser = new JFileChooser();

                List<String> exts = DataSourceRegistry.getInstance().getFormatterExtensions();
                FileFilter deflt = null;
                for (String ext : exts) {
                    final String ex = ext;
                    final String desc = "";
                    FileFilter ff = new FileFilter() {

                        @Override
                        public boolean accept(File f) {
                            return f.toString().endsWith(ex) || f.isDirectory();
                        }

                        @Override
                        public String getDescription() {
                            return "*" + ex; // DANGER: this is parsed below
                        }
                    };
                    if (ext.equals(".qds")) {
                        deflt = ff;
                    }
                    chooser.addChoosableFileFilter(ff);
                }

                chooser.setFileFilter(deflt);

                Preferences prefs = Preferences.userNodeForPackage(AutoPlotUI.class);
                String currentFileString = prefs.get("DumpDataCurrentFile", "");

                if (dsc.getFillDataSet() != null) {
                    String name = (String) dsc.getFillDataSet().property(QDataSet.NAME);
                    if (name != null) {
                        chooser.setSelectedFile(new File(name.toLowerCase()));
                    }
                }

                if (!currentFileString.equals("") && new File(currentFileString).exists()) {
                    File folder = new File(currentFileString).getParentFile();
                    chooser.setCurrentDirectory(folder);
                //chooser.setSelectedFile(new File(currentFileString));
                }

                int r = chooser.showSaveDialog(parent);
                if (r == JFileChooser.APPROVE_OPTION) {
                    try {
                        prefs.put("DumpDataCurrentFile", chooser.getSelectedFile().toString());

                        String s = chooser.getSelectedFile().toString();

                        String ext = DataSetURL.getExt(s);
                        if (ext == null) {
                            ext = "";
                        }

                        DataSourceFormat format = DataSourceRegistry.getInstance().getFormatByExt(ext);
                        if (format == null) {
                            if (chooser.getFileFilter().getDescription().startsWith("*.")) {
                                ext = chooser.getFileFilter().getDescription().substring(1);
                                format = DataSourceRegistry.getInstance().getFormatByExt(ext);
                                if (format == null) {
                                    JOptionPane.showMessageDialog(parent, "No formatter for extension: " + ext);
                                    return;
                                } else {
                                    s = s + ext;
                                }
                            } else {
                                JOptionPane.showMessageDialog(parent, "No formatter for extension: " + ext);
                                return;
                            }
                        }
                        format.formatData(new File(s), new java.util.HashMap<String, String>(),
                                dsc.getFillDataSet(), new DasProgressPanel("formatting data"));
                        parent.setStatus("Wrote file " + s);

                    } catch (IOException ex) {
                        parent.applicationModel.getExceptionHandler().handle(ex);
                    } catch ( IllegalArgumentException ex ) {
                        parent.applicationModel.getExceptionHandler().handle(ex);
                    } catch (RuntimeException ex ) {
                        parent.applicationModel.getExceptionHandler().handleUncaught(ex);
                    } catch (Exception ex) {
                        parent.applicationModel.getExceptionHandler().handle(ex);
                    }
                }
            }
        };
    }

    public Action createNewDOMAction() {
        return new AbstractAction("Reset Application...") {
            public void actionPerformed( ActionEvent e ) {
                if ( parent.stateSupport.isDirty() ) {
                    String msg= "The application has been modified.  Do you want to save your changes?";
                    int result= JOptionPane.showConfirmDialog(parent,msg );
                    if ( result==JOptionPane.OK_OPTION ) {
                        parent.stateSupport.saveAs();
                    } else if ( result==JOptionPane.CANCEL_OPTION || result==JOptionPane.CLOSED_OPTION ) {
                        return;
                    }
                }
                parent.dom.getController().reset();
            }
        };
    }

    public Action createNewApplicationAction() {
        return new AbstractAction("New Application") {
            public void actionPerformed( ActionEvent e ) {
                ApplicationModel model = new ApplicationModel();
                model.setExceptionHandler( parent.applicationModel.getExceptionHandler() );
                model.addDasPeersToApp();
                AutoPlotUI view = new AutoPlotUI(model);
                view.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
                view.setVisible(true);
            }
        };
    }

    public Action createCloneApplicationAction() {
        return new AbstractAction("Clone Application") {
            public void actionPerformed( ActionEvent e ) {
                ApplicationModel model = new ApplicationModel();
                model.setExceptionHandler( model.getExceptionHandler() );
                model.addDasPeersToApp();
                AutoPlotUI view = new AutoPlotUI(model);
                view.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
                view.setVisible(true);
                model.dom.syncTo( parent.applicationModel.dom );
            }
        };
    }
    public static JMenu createEZAccessMenu(final Plot plot) {

        JMenu result = new JMenu("Plot Style");
        result.add(new JMenuItem(new AbstractAction("Scatter") {

            public void actionPerformed(ActionEvent e) {
                Panel panel = plot.getController().getApplication().getController().getPanel();
                panel.setRenderType(RenderType.scatter);
            }
        }));

        result.add(new JMenuItem(new AbstractAction("Color Scatter") {

            public void actionPerformed(ActionEvent e) {
                Panel panel = plot.getController().getApplication().getController().getPanel();
                panel.setRenderType(RenderType.colorScatter);
            }
        }));

        result.add(new JMenuItem(new AbstractAction("Series") {

            public void actionPerformed(ActionEvent e) {
                Panel panel = plot.getController().getApplication().getController().getPanel();
                panel.setRenderType(RenderType.series);
            }
        }));

        result.add(new JMenuItem(new AbstractAction("Stair Steps") {

            public void actionPerformed(ActionEvent e) {
                Panel panel = plot.getController().getApplication().getController().getPanel();
                panel.setRenderType(RenderType.stairSteps);
            }
        }));

        result.add(new JMenuItem(new AbstractAction("Fill To Zero") {

            public void actionPerformed(ActionEvent e) {
                Panel panel = plot.getController().getApplication().getController().getPanel();
                panel.setRenderType(RenderType.fillToZero);
            }
        }));

        result.add(new JMenuItem(new AbstractAction("Huge Scatter") {

            public void actionPerformed(ActionEvent e) {
                Panel panel = plot.getController().getApplication().getController().getPanel();
                panel.setRenderType(RenderType.hugeScatter);
            }
        }));

        result.add(new JMenuItem(new AbstractAction("Spectrogram") {

            public void actionPerformed(ActionEvent e) {
                Panel panel = plot.getController().getApplication().getController().getPanel();
                panel.setRenderType(RenderType.spectrogram);
            }
        }));

        result.add(new JMenuItem(new AbstractAction("Nearest Neighbor Spectrogram") {

            public void actionPerformed(ActionEvent e) {
                Panel panel = plot.getController().getApplication().getController().getPanel();
                panel.setRenderType(RenderType.nnSpectrogram);
            }
        }));

        result.add(new JMenuItem(new AbstractAction("Digital") {

            public void actionPerformed(ActionEvent e) {
                Panel panel = plot.getController().getApplication().getController().getPanel();
                panel.setRenderType(RenderType.digital);
            }
        }));

        return result;
    }

    protected void addKeyBindings(JPanel thisPanel) {
        thisPanel.getActionMap().put("UNDO", parent.undoRedoSupport.getUndoAction());
        thisPanel.getActionMap().put("REDO", parent.undoRedoSupport.getRedoAction());
        thisPanel.getActionMap().put("RESET_ZOOM", new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                parent.applicationModel.resetZoom();
            }
        });
        thisPanel.getActionMap().put("INCREASE_FONT_SIZE", new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                parent.applicationModel.increaseFontSize();
            }
        });
        thisPanel.getActionMap().put("DECREASE_FONT_SIZE", new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                parent.applicationModel.decreaseFontSize();
            }
        });

        thisPanel.getActionMap().put("NEXT_PANEL", new AbstractAction() {
            public void actionPerformed( ActionEvent e ) {
                Application dom= parent.dom;
                Panel p= dom.getController().getPanel();
                int idx= Arrays.asList( dom.getPanels() ).indexOf(p);
                if ( idx==-1 ) idx=0;
                idx++;
                if ( idx==dom.getPanels().length ) idx=0;
                dom.getController().setPanel( dom.getPanels(idx) );
            }
        });

        thisPanel.getActionMap().put("PREV_PANEL", new AbstractAction() {
            public void actionPerformed( ActionEvent e ) {
                Application dom= parent.dom;
                Panel p= dom.getController().getPanel();
                int idx= Arrays.asList( dom.getPanels() ).indexOf(p);
                if ( idx==-1 ) idx=0;
                idx--;
                if ( idx==-1 ) idx= dom.getPanels().length-1;
                dom.getController().setPanel( dom.getPanels(idx) );
            }
        });

        InputMap map = new ComponentInputMap(thisPanel);
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK), "UNDO");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.CTRL_DOWN_MASK), "REDO");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK), "RESET_ZOOM");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, KeyEvent.CTRL_DOWN_MASK), "DECREASE_FONT_SIZE");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, KeyEvent.CTRL_DOWN_MASK), "INCREASE_FONT_SIZE");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, KeyEvent.CTRL_DOWN_MASK), "INCREASE_FONT_SIZE");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, KeyEvent.SHIFT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK), "INCREASE_FONT_SIZE");  // american keyboard
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, KeyEvent.CTRL_DOWN_MASK), "NEXT_PANEL");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, KeyEvent.SHIFT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK), "PREV_PANEL");
        thisPanel.setInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW, map);

    }

    protected void exportRecent(Component c) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {

            public boolean accept(File f) {
                return f.isDirectory() || f.getName().endsWith(".xml");
            }

            public String getDescription() {
                return "bookmarks files (*.xml)";
            }
        });
        int r = chooser.showSaveDialog(c);
        if (r == JFileChooser.APPROVE_OPTION) {
            try {
                File f = chooser.getSelectedFile();
                if (!f.toString().endsWith(".xml")) {
                    f = new File(f.toString() + ".xml");
                }
                String format = Bookmark.formatBooks(parent.applicationModel.getRecent());
                FileOutputStream out = new FileOutputStream(f);
                out.write(format.getBytes());
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static FileFilter getFileNameExtensionFilter(final String description, final String ext) {
        return new FileFilter() {

            public boolean accept(File f) {
                return f.isDirectory() || f.toString().endsWith(ext);
            }

            public String getDescription() {
                return description;
            }
        };
    }

    private static File currentFile;

    public static Action getPrintAction( final Application app,final String ext) {
        return new AbstractAction("Print as "+ext.toUpperCase()) {
            public void actionPerformed(ActionEvent e) {
                final DasCanvas canvas = app.getController().getDasCanvas();
                final JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Print to "+ext.toUpperCase());
                fileChooser.setFileFilter(getFileNameExtensionFilter( ext + " files", ext ));
                Preferences prefs = Preferences.userNodeForPackage(DasCanvas.class);
                String savedir = prefs.get("savedir", null);
                if (savedir != null)
                    fileChooser.setCurrentDirectory(new File(savedir));
                if (currentFile != null)
                    fileChooser.setSelectedFile(currentFile);
                int choice = fileChooser.showSaveDialog(canvas);
                if (choice == JFileChooser.APPROVE_OPTION) {

                    String fname = fileChooser.getSelectedFile().toString();
                    if (!fname.toLowerCase().endsWith("."+ext)) fname += "."+ext;
                    final String ffname = fname;
                    prefs.put("savedir", new File(ffname).getParent());
                    currentFile = new File(ffname.substring(0, ffname.length() - 4));
                    Runnable run = new Runnable() {
                        public void run() {
                            try {
                                if ( ext.equals("png") ) {
                                    canvas.writeToPng(ffname);
                                } else if ( ext.equals("pdf") ) {
                                    canvas.writeToPDF(ffname);
                                } else if ( ext.equals("svg") ) {
                                    canvas.writeToSVG(ffname);
                                }
                                app.getController().setStatus("wrote to " + ffname);
                            } catch (java.io.IOException ioe) {
                                org.das2.util.DasExceptionHandler.handle(ioe);
                            }
                        }
                    };
                    new Thread(run, "writePrint").start();
                }
            }
        };
    }

    private static void handleAddPanelDialog(AddPanelDialog dia, Application dom, ApplicationModel applicationModel) {
        Plot plot = null;
        Panel panel = null;
        int modifiers = dia.getModifiers();
        if ((modifiers & KeyEvent.CTRL_MASK) == KeyEvent.CTRL_MASK) {
            // new plot
            plot = null;
            panel = null;
            //nothing
        } else if ((modifiers & KeyEvent.SHIFT_MASK) == KeyEvent.SHIFT_MASK) {
            // overplot
            plot = dom.getController().getPlot();
        } else {
            panel = dom.getController().getPanel();
        }
        if (dia.getDepCount() == 0) {
            applicationModel.addRecent(dia.getPrimaryDataSetSelector().getValue());
            dom.getController().doplot(plot, panel, dia.getPrimaryDataSetSelector().getValue());
        } else if (dia.getDepCount() == 1) {
            applicationModel.addRecent(dia.getPrimaryDataSetSelector().getValue());
            applicationModel.addRecent(dia.getSecondaryDataSetSelector().getValue());
            dom.getController().doplot(plot, panel, dia.getSecondaryDataSetSelector().getValue(), dia.getPrimaryDataSetSelector().getValue());
        } else if (dia.getDepCount() == 2) {
            applicationModel.addRecent(dia.getPrimaryDataSetSelector().getValue());
            applicationModel.addRecent(dia.getSecondaryDataSetSelector().getValue());
            applicationModel.addRecent(dia.getTertiaryDataSetSelector().getValue());
            dom.getController().doplot(plot, panel, dia.getSecondaryDataSetSelector().getValue(), dia.getTertiaryDataSetSelector().getValue(), dia.getPrimaryDataSetSelector().getValue());
        } else if (dia.getDepCount() == -1) {
            if (panel == null) {
                panel = dom.getController().addPanel(plot, null);
            }
        }
    }

    /**
     * support for binding two plot axes.
     * @param dstPlot
     * @param plot
     * @param axis
     * @throws java.lang.IllegalArgumentException
     */
    private static void bindToPlotPeer( final ApplicationController controller, Plot dstPlot, Plot plot, Axis axis) throws IllegalArgumentException {
        Axis targetAxis;
        if (plot.getXaxis() == axis) {
            targetAxis = dstPlot.getXaxis();
        } else if (plot.getYaxis() == axis) {
            targetAxis = dstPlot.getYaxis();
        } else if (plot.getZaxis() == axis) {
            targetAxis = dstPlot.getZaxis();
        } else {
            throw new IllegalArgumentException("this axis and plot don't go together");
        }
        axis.setLog( targetAxis.isLog() );
        axis.setRange( targetAxis.getRange() );
        controller.bind(targetAxis, Axis.PROP_LOG, axis, Axis.PROP_LOG); //set log first since we might tweak range accordingly.
        controller.bind(targetAxis, Axis.PROP_RANGE, axis, Axis.PROP_RANGE);
    }



    protected static void addAxisContextMenuItems( final ApplicationController controller, final DasPlot dasPlot, final PlotController plotController, final Plot plot, final Axis axis) {

        final DasAxis dasAxis = axis.getController().getDasAxis();
        final DasMouseInputAdapter mouseAdapter = dasAxis.getDasMouseInputAdapter();

        mouseAdapter.removeMenuItem("Properties");

        JMenuItem item;

        mouseAdapter.addMenuItem(new JMenuItem(new AbstractAction("Axis Properties") {

            public void actionPerformed(ActionEvent e) {
                PropertyEditor pp = new PropertyEditor(axis);
                pp.showDialog(dasAxis.getCanvas());
            }
        }));

        mouseAdapter.addMenuItem(new JSeparator());

        if (axis == plot.getXaxis()) {
            JMenu addPlotMenu = new JMenu("Add Plot");
            mouseAdapter.addMenuItem(addPlotMenu);

            item = new JMenuItem(new AbstractAction("Bound Plot Below") {

                public void actionPerformed(ActionEvent e) {
                    controller.copyPlot(plot, true, false, true);
                }
            });
            item.setToolTipText("add a new plot below.  The plot's x axis will be bound to this plot's x axis");
            addPlotMenu.add(item);

        }

        item = new JMenuItem(new AbstractAction("Remove Bindings") {

            public void actionPerformed(ActionEvent e) {
                BindingModel[] bms= controller.getBindingsFor(axis);
                controller.unbind(axis);  // TODO: check for application timerange
                controller.setStatus("removed "+bms.length+" bindings");
            }
        });
        item.setToolTipText("remove any plot and panel property bindings");
        mouseAdapter.addMenuItem(item);


        JMenu bindingMenu = new JMenu("Add Binding");

        mouseAdapter.addMenuItem(bindingMenu);

        if (axis == plot.getXaxis()) {
            item = new JMenuItem(new AbstractAction("Bind to Application Time Range") {

                public void actionPerformed(ActionEvent e) {
                    controller.bind(controller.getApplication(), Application.PROP_TIMERANGE, axis, Axis.PROP_RANGE);
                }
            });
            bindingMenu.add(item);
        }


        item = new JMenuItem(new AbstractAction("Bind to Plot Above") {

            public void actionPerformed(ActionEvent e) {
                Plot dstPlot = controller.getPlotAbove(plot);
                if (dstPlot == null) {
                    controller.setStatus("warning: no plot above");
                } else {
                    bindToPlotPeer(controller,dstPlot, plot, axis);
                }
            }
        });
        bindingMenu.add(item);
        item = new JMenuItem(new AbstractAction("Bind to Plot Below") {

            public void actionPerformed(ActionEvent e) {
                Plot dstPlot = controller.getPlotBelow(plot);
                if (dstPlot == null) {
                    controller.setStatus("warning: no plot below");
                } else {
                    bindToPlotPeer(controller,dstPlot, plot, axis);
                }
            }
        });
        bindingMenu.add(item);

        JMenu connectorMenu = new JMenu("Add Connector");

        mouseAdapter.addMenuItem(connectorMenu);

        item = new JMenuItem(new AbstractAction("Connector to Plot Above") {

            public void actionPerformed(ActionEvent e) {
                Plot dstPlot = controller.getPlotAbove(plot);
                if (dstPlot == null) {
                    controller.setStatus("warning: no plot above");
                } else {
                    controller.addConnector(dstPlot, plot);
                }
            }
        });
        connectorMenu.add(item);
    }

    static void addPlotContextMenuItems( final ApplicationController controller, final DasPlot plot, final PlotController plotController, final Plot domPlot) {
        plot.getDasMouseInputAdapter().addMouseModule(new MouseModule(plot, new PointSlopeDragRenderer(plot, plot.getXAxis(), plot.getYAxis()), "Slope"));

        plot.getDasMouseInputAdapter().removeMenuItem("Dump Data");
        plot.getDasMouseInputAdapter().removeMenuItem("Properties");

        JMenuItem item;

        plot.getDasMouseInputAdapter().addMenuItem(new JMenuItem(new AbstractAction("Plot Properties") {

            public void actionPerformed(ActionEvent e) {
                PropertyEditor pp = new PropertyEditor(domPlot);
                pp.showDialog(plot.getCanvas());
            }
        }));

       JMenuItem panelPropsMenuItem= new JMenuItem(new AbstractAction("Panel Style Properties") {
            public void actionPerformed(ActionEvent e) {
                Panel p = controller.getPanel();
                PropertyEditor pp = new PropertyEditor(p.getStyle());
                pp.showDialog(plot.getCanvas());
            }
        });
        plotController.setPanelPropsMenuItem(panelPropsMenuItem);
        
        plot.getDasMouseInputAdapter().addMenuItem(panelPropsMenuItem);

        plot.getDasMouseInputAdapter().addMenuItem(new JSeparator());

        JMenu addPlotMenu = new JMenu("Add Plot");
        plot.getDasMouseInputAdapter().addMenuItem(addPlotMenu);

        item = new JMenuItem(new AbstractAction("Copy Panels") {

            public void actionPerformed(ActionEvent e) {
                controller.copyPlotAndPanels(domPlot, null, true, false);
            }
        });
        item.setToolTipText("make a new plot, and copy the panels into it.  New plot is bound by the x axis.");
        addPlotMenu.add(item);

        item = new JMenuItem(new AbstractAction("Context Overview") {

            public void actionPerformed(ActionEvent e) {
                Plot that = controller.copyPlotAndPanels(domPlot, null, false, false);
                controller.bind(domPlot.getZaxis(), Axis.PROP_RANGE, that.getZaxis(), Axis.PROP_RANGE);
                controller.bind(domPlot.getZaxis(), Axis.PROP_LOG, that.getZaxis(), Axis.PROP_LOG);
                controller.bind(domPlot.getZaxis(), Axis.PROP_LABEL, that.getZaxis(), Axis.PROP_LABEL);
                controller.addConnector(domPlot, that);
                that.getController().resetZoom(true, true, true);
            }
        });

        item.setToolTipText("make a new plot, and copy the panels into it.  The plot is not bound,\n" +
                "and a connector is drawn between the two.  The panel uris are bound as well.");
        addPlotMenu.add(item);

        JMenu editPlotMenu = new JMenu("Edit Plot");
        plot.getDasMouseInputAdapter().addMenuItem(editPlotMenu);

        controller.fillEditPlotMenu(editPlotMenu, domPlot);

        JMenu panelMenu = new JMenu("Edit Panel");

        plot.getDasMouseInputAdapter().addMenuItem(panelMenu);

        item = new JMenuItem(new AbstractAction("Move to Plot Above") {

            public void actionPerformed(ActionEvent e) {
                Panel panel = controller.getPanel();
                Plot plot = controller.getPlotFor(panel);
                Plot dstPlot = controller.getPlotAbove(plot);
                if (dstPlot == null) {
                    dstPlot = controller.addPlot(LayoutConstants.ABOVE);
                    panel.setPlotId(dstPlot.getId());
                    controller.bind(plot.getXaxis(), Axis.PROP_RANGE, dstPlot.getXaxis(), Axis.PROP_RANGE);
                } else {
                    panel.setPlotId(dstPlot.getId());
                }
            }
        });
        panelMenu.add(item);

        item = new JMenuItem(new AbstractAction("Move to Plot Below") {

            public void actionPerformed(ActionEvent e) {
                Panel panel = controller.getPanel();
                Plot plot = controller.getPlotFor(panel);
                Plot dstPlot = controller.getPlotBelow(plot);
                if (dstPlot == null) {
                    dstPlot = controller.addPlot(LayoutConstants.BELOW);
                    panel.setPlotId(dstPlot.getId());
                    controller.bind(plot.getXaxis(), Axis.PROP_RANGE, dstPlot.getXaxis(), Axis.PROP_RANGE);
                } else {
                    panel.setPlotId(dstPlot.getId());
                }
            }
        });
        panelMenu.add(item);

        item = new JMenuItem(new AbstractAction("Delete Panel") {

            public void actionPerformed(ActionEvent e) {
                Panel panel = controller.getPanel();
                controller.deletePanel(panel);
            }
        });
        panelMenu.add(item);

        JMenuItem editDataMenu = new JMenuItem(new AbstractAction("Edit Data Source") {
            public void actionPerformed(ActionEvent e) {
                GuiSupport.editPanel( controller.getApplicationModel(), plot );
            }
        });

        plot.getDasMouseInputAdapter().addMenuItem(editDataMenu);

        plot.getDasMouseInputAdapter().addMenuItem(new JSeparator());

        plot.getDasMouseInputAdapter().addMenuItem(new JMenuItem(new AbstractAction("Reset Zoom") {

            public void actionPerformed(ActionEvent e) {
                plotController.resetZoom(true, true, true);
            }
        }));


        plot.getDasMouseInputAdapter().addMenuItem(GuiSupport.createEZAccessMenu(domPlot));
    }

}
