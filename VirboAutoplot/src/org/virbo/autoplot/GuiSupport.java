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
import org.das2.components.DasProgressPanel;
import org.das2.graph.DasCanvas;
import org.das2.graph.PsymConnector;
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
import java.io.IOException;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ComponentInputMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.filechooser.FileFilter;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.autoplot.dom.DataSourceController;
import org.virbo.autoplot.dom.Panel;
import org.virbo.autoplot.transferrable.ImageSelection;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSourceRegistry;
import org.virbo.datasource.URLSplit;
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

    Action getDumpDataAction() {
        return new AbstractAction("Export Data...") {

            public void actionPerformed(ActionEvent e) {
                DataSourceController dsc= parent.applicationModel.getDataSourceFilterController();
                
                if ( dsc.getFillDataSet()==null ) {
                    JOptionPane.showMessageDialog( parent, "No Data to Export.");
                    return;
                }
                
                JFileChooser chooser = new JFileChooser();
                                
                List<String> exts = DataSourceRegistry.getInstance().getFormatterExtensions();
                FileFilter deflt= null;
                for ( String ext : exts ) {
                    final String ex= ext;
                    final String desc= "";
                    FileFilter ff= new FileFilter() {
                        @Override
                        public boolean accept(File f) {
                            return f.toString().endsWith(ex) || f.isDirectory();
                        }

                        @Override
                        public String getDescription() {
                            return "*"+ex; // DANGER: this is parsed below
                        }
                    };
                    if ( ext.equals(".qds") ) {
                        deflt= ff;
                    }
                    chooser.addChoosableFileFilter(ff);
                }
                
                chooser.setFileFilter(deflt);
                
                Preferences prefs = Preferences.userNodeForPackage(AutoPlotUI.class);
                String currentFileString = prefs.get("DumpDataCurrentFile", "");
                
                if ( dsc.getFillDataSet()!=null ) {
                    String name= (String) dsc.getFillDataSet().property( QDataSet.NAME );
                    if ( name!=null ) chooser.setSelectedFile(new File(name.toLowerCase())); 
                }

                if (!currentFileString.equals("") && new File( currentFileString ).exists() ) {
                    File folder= new File( currentFileString ).getParentFile();
                    chooser.setCurrentDirectory(folder);
                    //chooser.setSelectedFile(new File(currentFileString));
                }
                
                int r = chooser.showSaveDialog(parent);
                if (r == JFileChooser.APPROVE_OPTION) {
                    try {
                        prefs.put("DumpDataCurrentFile", chooser.getSelectedFile().toString());
                        
                        String s=  chooser.getSelectedFile().toString();
                        
                        String ext= DataSetURL.getExt(s);
                        if ( ext==null ) ext= "";
                        
                        DataSourceFormat format= DataSourceRegistry.getInstance().getFormatByExt(ext);
                        if ( format==null ) {
                            if ( chooser.getFileFilter().getDescription().startsWith("*.") ) { 
                                ext= chooser.getFileFilter().getDescription().substring(1);
                                format= DataSourceRegistry.getInstance().getFormatByExt(ext);
                                if ( format==null ) {
                                    JOptionPane.showMessageDialog( parent, "No formatter for extension: "+ext );
                                    return;
                                } else {
                                    s= s+ext;
                                }
                            } else {
                                JOptionPane.showMessageDialog( parent, "No formatter for extension: "+ext );
                                return;
                            }
                        }
                        format.formatData( new File(s),new java.util.HashMap<String, String>(), 
                                dsc.getFillDataSet(), new DasProgressPanel("formatting data")  );
                        parent.setStatus("created file "+s);

                    } catch (IOException ex) {
                        parent.applicationModel.application.getExceptionHandler().handle(ex);
                    } catch ( Exception ex ) {
                        parent.applicationModel.application.getExceptionHandler().handle(ex);
                    }
                }
            }
        };
    }

    public static JMenu createEZAccessMenu( final Panel panel ) {

        JMenu result = new JMenu("plot style");
        result.add(new JMenuItem(new AbstractAction("scatter") {

            public void actionPerformed(ActionEvent e) {
                panel.setRenderType( ApplicationModel.RenderType.scatter );
            }
        }));

        result.add(new JMenuItem(new AbstractAction("colorScatter") {

            public void actionPerformed(ActionEvent e) {
                panel.setRenderType( ApplicationModel.RenderType.colorScatter );
            }
        }));
        
        result.add(new JMenuItem(new AbstractAction("series") {

            public void actionPerformed(ActionEvent e) {
                panel.setRenderType( ApplicationModel.RenderType.series );
            }
        }));

        result.add(new JMenuItem(new AbstractAction("histogram") {

            public void actionPerformed(ActionEvent e) {
                panel.setRenderType( ApplicationModel.RenderType.histogram );
            }
        }));

        result.add(new JMenuItem(new AbstractAction("fill to zero") {

            public void actionPerformed(ActionEvent e) {
                panel.setRenderType( ApplicationModel.RenderType.fill_to_zero );
            }
        }));

        result.add(new JMenuItem(new AbstractAction("spectrogram") {

            public void actionPerformed(ActionEvent e) {
                panel.setRenderType( ApplicationModel.RenderType.spectrogram );
            }
        }));

        return result;
    }
    
    protected void addKeyBindings(JPanel thisPanel) {
        thisPanel.getActionMap().put("UNDO", parent.undoRedoSupport.getUndoAction() );
        thisPanel.getActionMap().put("REDO", parent.undoRedoSupport.getRedoAction() );
        thisPanel.getActionMap().put("RESET_ZOOM", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                parent.applicationModel.resetZoom();
            }
        } );
        thisPanel.getActionMap().put("INCREASE_FONT_SIZE", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                parent.applicationModel.increaseFontSize();
            }                
        } );
        thisPanel.getActionMap().put("DECREASE_FONT_SIZE", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                parent.applicationModel.decreaseFontSize();
            }                
        } );
        
        InputMap map = new ComponentInputMap(thisPanel);
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK), "UNDO");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.CTRL_DOWN_MASK), "REDO");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK), "RESET_ZOOM");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, KeyEvent.CTRL_DOWN_MASK), "DECREASE_FONT_SIZE");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, KeyEvent.CTRL_DOWN_MASK), "INCREASE_FONT_SIZE");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, KeyEvent.CTRL_DOWN_MASK), "INCREASE_FONT_SIZE");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, KeyEvent.SHIFT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK), "INCREASE_FONT_SIZE");  // american keyboard
        thisPanel.setInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW, map);
        
    }
    
}
