/*
 * GuiSupport.java
 *
 * Created on November 30, 2007, 5:04 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import ZoeloeSoft.projects.JFontChooser.JFontChooser;
import java.awt.Component;
import java.awt.Font;
import java.awt.Frame;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.MenuEvent;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.ComponentInputMap;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.InputMap;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileFilter;
import org.das2.components.propertyeditor.PropertyEditor;
import org.das2.datum.DatumRange;
import org.das2.datum.UnitsUtil;
import org.das2.event.DasMouseInputAdapter;
import org.das2.event.MouseModule;
import org.das2.event.PointSlopeDragRenderer;
import org.das2.graph.DasAxis;
import org.das2.graph.DasPlot;
import org.das2.util.Entities;
import org.das2.util.awt.GraphicsOutput;
import org.das2.util.awt.PdfGraphicsOutput;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.autoplot.bookmarks.Bookmark;
import org.virbo.autoplot.bookmarks.BookmarksException;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.autoplot.dom.Axis;
import org.virbo.autoplot.dom.BindingModel;
import org.virbo.autoplot.dom.Connector;
import org.virbo.autoplot.dom.DataSourceFilter;
import org.virbo.autoplot.dom.DomUtil;
import org.virbo.autoplot.dom.OptionsPrefsController;
import org.virbo.autoplot.dom.PlotElement;
import org.virbo.autoplot.dom.Plot;
import org.virbo.autoplot.dom.PlotController;
import org.virbo.autoplot.layout.LayoutConstants;
import org.virbo.autoplot.state.StatePersistence;
import org.virbo.autoplot.transferrable.ImageSelection;
import org.virbo.dataset.ArrayDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.DataSetSelector;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFormatEditorPanel;
import org.virbo.datasource.DataSourceRegistry;
import org.virbo.datasource.DataSourceUtil;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.DataSourceFormat;
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
public class GuiSupport {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.guisupport");

    AutoplotUI parent;

    public GuiSupport(AutoplotUI parent) {
        this.parent = parent;
    }

    public void doPasteDataSetURL() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable contents = clipboard.getContents(null);
        if ( contents==null ) {
            logger.fine("contents was null");
            return;
        }
        boolean hasTransferableText =
                contents.isDataFlavorSupported(DataFlavor.stringFlavor);
        String result = null;
        if (hasTransferableText) {
            try {
                result = (String) contents.getTransferData(DataFlavor.stringFlavor);
            } catch (UnsupportedFlavorException ex) {
                //highly unlikely since we are using a standard DataFlavor
                logger.log( Level.WARNING, ex.getMessage(), ex );
            } catch (IOException ex) {
                logger.log( Level.WARNING, ex.getMessage(), ex );
            }
        }
        if (result != null) {
            parent.dataSetSelector.setValue(result);
        }
    }

    public void doCopyDataSetURL() {
        StringSelection stringSelection = new StringSelection( DataSetURI.toUri(parent.dataSetSelector.getValue()).toASCIIString() );
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, new ClipboardOwner() {
            @Override
            public void lostOwnership(Clipboard clipboard, Transferable contents) {
            }
        });
    }

    public void doCopyDataSetImage() {
        Runnable run = new Runnable() {
            @Override
            public void run() {
                ImageSelection imageSelection = new ImageSelection();
                DasCanvas c = parent.applicationModel.canvas;
                Image i = c.getImage(c.getWidth(), c.getHeight());
                imageSelection.setImage(i);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(imageSelection, new ClipboardOwner() {
                    @Override
                    public void lostOwnership(Clipboard clipboard, Transferable contents) {
                    }
                });
            }
        };
        new Thread(run, "CopyDataSetToClipboardThread").start();
    }

    /**
     * attempt to get the Frame for the component, which may already be a Frame.
     * @param parent
     * @return
     */
    public static Frame getFrameForComponent( Component parent ) { 
        if ( !( parent instanceof Frame ) ) {
            parent= SwingUtilities.getWindowAncestor(parent);
        }
        if ( parent instanceof Frame ) {
            return (Frame)parent;
        } else {
            return null;
        }
    }

    public static void editPlotElement( ApplicationModel applicationModel, Component parent ) {
        
        Application dom = applicationModel.dom;

        AddPlotElementDialog dia = new AddPlotElementDialog( getFrameForComponent(parent), true);
        dia.getPrimaryDataSetSelector().setTimeRange(dom.getTimeRange());
        dia.getSecondaryDataSetSelector().setTimeRange(dom.getTimeRange());
        dia.getTertiaryDataSetSelector().setTimeRange(dom.getTimeRange());

        String suri= dom.getController().getFocusUri();

        setAddPlotElementUris( applicationModel, dom, dia, suri );

        dia.setTitle( "Editing Plot Element" ); 
        dia.setVisible(true);
        if (dia.isCancelled()) {
            return;
        }
        handleAddElementDialog(dia, dom, applicationModel);

    }

    private static void setAddPlotElementUris( ApplicationModel applicationModel,
            Application dom, AddPlotElementDialog dia, String suri ) {

        Pattern hasKidsPattern= Pattern.compile("vap\\+internal\\:(data_\\d+)(,(data_\\d+))?+(,(data_\\d+))?+");
        Matcher m= hasKidsPattern.matcher(suri);

        dia.getPrimaryDataSetSelector().setRecent(AutoplotUtil.getUrls(applicationModel.getRecent()));
        dia.getSecondaryDataSetSelector().setRecent(AutoplotUtil.getUrls(applicationModel.getRecent()));
        dia.getTertiaryDataSetSelector().setRecent(AutoplotUtil.getUrls(applicationModel.getRecent()));

        if ( m.matches() ) {
            int depCount= m.group(5)!=null ? 2 : ( m.group(3)!=null ? 1 : ( m.group(1)!=null ? 0 : -1 ) );
            dia.setDepCount(depCount);
            int[] groups;
            DataSetSelector[] selectors;
            selectors= new DataSetSelector[] { dia.getPrimaryDataSetSelector(),
                dia.getSecondaryDataSetSelector(),
                dia.getTertiaryDataSetSelector(), };
            if ( depCount==2 ) {
                groups= new int[] { 5,1,3 };
            } else if (depCount==1 ) {
                groups= new int[] { 3,1 };
            } else {
                groups= new int[] { 1 };
            }
            for ( int i=0; i<groups.length; i++ ) {
                DataSourceFilter dsf= (DataSourceFilter) DomUtil.getElementById( dom, m.group(groups[i]) );
                if ( dsf==null ) {
                    selectors[i].setValue( m.group(groups[i]) );
                } else if ( dsf.getUri().length()==0 ) {
                    selectors[i].setValue( m.group(groups[i]) ); //TODO: interesting branch that I hit on a telecon with Reiner.
                } else if ( dsf.getUri().startsWith("vap+internal:")) {
                    selectors[i].setValue( m.group(groups[i]) ); //TODO: does this work, multiple levels?
                } else {
                    selectors[i].setValue(dsf.getUri());
                }
            }
        } else {
            dia.getPrimaryDataSetSelector().setValue( suri );
        }
    }


    void addPlotElement() {

        ApplicationModel applicationModel = parent.applicationModel;
        DataSetSelector dataSetSelector = parent.dataSetSelector;
        Application dom = applicationModel.dom;

        AddPlotElementDialog dia = new AddPlotElementDialog( parent, true);
        dia.getPrimaryDataSetSelector().setTimeRange(dom.getTimeRange());
        dia.getSecondaryDataSetSelector().setTimeRange(dom.getTimeRange());
        dia.getTertiaryDataSetSelector().setTimeRange(dom.getTimeRange());

        String val= dataSetSelector.getValue();
        if ( val.startsWith("vap+internal:") ) {
            setAddPlotElementUris( applicationModel, dom, dia, val );
        } else {
            dia.getPrimaryDataSetSelector().setValue(val);
            dia.getSecondaryDataSetSelector().setValue(val);
            dia.getTertiaryDataSetSelector().setValue(val);
            dia.getPrimaryDataSetSelector().setRecent(AutoplotUtil.getUrls(applicationModel.getRecent()));
            dia.getSecondaryDataSetSelector().setRecent(AutoplotUtil.getUrls(applicationModel.getRecent()));
            dia.getTertiaryDataSetSelector().setRecent(AutoplotUtil.getUrls(applicationModel.getRecent()));
        }

        dia.setTitle( "Adding Plot Element" );
        dia.setVisible(true);
        if (dia.isCancelled()) {
            return;
        }
        handleAddElementDialog(dia, dom, applicationModel);

    }

    /**
     * dump the data using the DataSourceFormat object.
     * @param fds the dataset in its original form.
     * @param dsf the data source filter.
     * @param pe the plot element.
     * @param format format object.
     * @param uriOut output location, must be a local file.
     * @param dscontrol "plotElementTrim": load the data and trim to xaxis settings. "plotElement" data processed to make visible.
     * @throws IOException 
     */
    private void doDumpData( QDataSet fds, DataSourceFilter dsf, PlotElement pe, DataSourceFormat format, String uriOut, String dscontrol  ) throws IOException {

        logger.log(Level.FINE, "exporting data to {0} using format {1}", new Object[]{uriOut, format});

        ProgressMonitor mon=null;
        try {
            QDataSet ds= fds;

            if ( dsf.getController().getTsb()!=null ) {
                DataSource dss= dsf.getController().getDataSource();
                if ( dss==null ) {
                    logger.fine("looks like a TSB is used, but the data is not a time series, don't reload");
                } else {
                    dsf.getController().getTsb().setTimeResolution(null);
                    mon= DasProgressPanel.createFramed(parent, "reloading timeseries at native resolution");
                    ds= dss.getDataSet( mon );
                    if ( mon.isCancelled() ) {
                        parent.setStatus( "export data cancelled" );
                        return;
                    }
                    mon.finished(); //why?
                }
            }

            mon= DasProgressPanel.createFramed( parent, "formatting data" );
            if ( dscontrol.equals("plotElementTrim") ) {
                DasPlot p= pe.getController().getDasPlot();
                DatumRange xbounds= p.getXAxis().getDatumRange();
                QDataSet dsout=  pe.getController().getDataSet();
                if ( dsf.getController().getTsb()!=null ) {
                    //dsout= DataSetOps.processDataSet( pe.getComponent(), dsout, DasProgressPanel.createFramed(parent, "process TSB timeseries at native resolution") );
                    long t0= System.currentTimeMillis();
                    if ( SemanticOps.isRank2Waveform(dsout) ) {
                        dsout= DataSetOps.flattenWaveform(dsout);
                        //dsout= ArrayDataSet.copy( dsout );
                    }
                    dsout= SemanticOps.trim( dsout, xbounds, null );
                    format.formatData( uriOut, dsout, mon );
                    logger.log( Level.FINE, "format in {0} millis", (System.currentTimeMillis()-t0));
                } else {
                    long t0= System.currentTimeMillis();
                    if ( SemanticOps.isRank2Waveform(dsout) ) { // TODO: rough trim first, then precise trim would be much faster.
                        dsout= DataSetOps.flattenWaveform(dsout);
                        //dsout= ArrayDataSet.copy( dsout );
                    }
                    dsout= SemanticOps.trim( dsout, xbounds, null );
                    format.formatData( uriOut, dsout, mon );
                    logger.log( Level.FINE, "format in {0} millis", (System.currentTimeMillis()-t0));
                }
            } else if ( dscontrol.equals("plotElement") ) {
                long t0= System.currentTimeMillis();
                QDataSet dsout=  pe.getController().getDataSet();
                format.formatData( uriOut, dsout, mon );
                logger.log( Level.FINE, "format in {0} millis", (System.currentTimeMillis()-t0));
            } else {
                long t0= System.currentTimeMillis();
                format.formatData( uriOut, ds, mon );
                logger.log( Level.FINE, "format in {0} millis", (System.currentTimeMillis()-t0));
            }
            parent.setStatus("Wrote " + org.virbo.datasource.DataSourceUtil.unescape(uriOut) );
        } catch ( IllegalArgumentException ex ) {
            parent.applicationModel.getExceptionHandler().handle(ex);
            logger.log(Level.FINE, " ..caused exception: {0} using format {1}", new Object[]{uriOut, format});
            logger.log(Level.SEVERE, "exception "+uriOut, ex );
        } catch (RuntimeException ex ) {
            parent.applicationModel.getExceptionHandler().handleUncaught(ex);
            logger.log(Level.FINE, " ..caused exception: {0} using format {1}", new Object[]{uriOut, format});
            logger.log(Level.SEVERE,  "exception "+uriOut, ex );
        } catch (Exception ex) {
            parent.applicationModel.getExceptionHandler().handle(ex);
            logger.log(Level.FINE, " ..caused exception: {0} using format {1}", new Object[]{uriOut, format});
            logger.log(Level.SEVERE,  "exception "+uriOut, ex );
        }
        if ( mon!=null ) mon.finished();
    }

    Action getDumpDataAction2( final Application dom ) {
        return new AbstractAction("Export Data...") {
            @Override
            public void actionPerformed( ActionEvent e ) {
                final ExportDataPanel edp= new ExportDataPanel();
                edp.setDataSet(dom);

                final PlotElement pe= dom.getController().getPlotElement();
                final DataSourceFilter dsf= dom.getController().getDataSourceFilterFor( pe );
                QDataSet ds= dsf.getController().getDataSet();

                if (ds == null) {
                    JOptionPane.showMessageDialog(parent, "No Data to Export.");
                    return;
                }
//TODO: check extension.
                List<String> exts = DataSourceRegistry.getInstance().getFormatterExtensions();
                Collections.sort(exts);
                edp.getFormatDL().setModel( new DefaultComboBoxModel(exts.toArray()) );
                edp.getFormatDL().setRenderer( new DefaultListCellRenderer() {
                    @Override
                    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                        String ext= String.valueOf(value);
                        DataSourceFormat format= DataSourceRegistry.getInstance().getFormatByExt(ext);
                        Component parent= super.getListCellRendererComponent( list, value, index, isSelected, cellHasFocus );
                        if ( parent instanceof JLabel ) {
                            if ( format!=null ) {
                                ((JLabel)parent).setText( value.toString() + " " + format.getDescription() );
                            }
                        }
                        return parent;
                    }
                });
                Preferences prefs= Preferences.userNodeForPackage(AutoplotUI.class);
                String currentFileString = prefs.get("ExportDataCurrentFile", "");
                String currentExtString = prefs.get("ExportDataCurrentExt", ".txt");
                if ( !currentExtString.equals("") ) {
                    edp.getFormatDL().setSelectedItem(currentExtString);
                }
                if ( !currentFileString.equals("") ) {
                    URISplit split= URISplit.parse(currentFileString);
                    edp.getFilenameTF().setText(split.file);
                    edp.getFormatDL().setSelectedItem( "." + split.ext );
                    if ( currentFileString.contains("/") ) {
                        edp.setFile( currentFileString );
                        if ( split.params!=null && edp.getDataSourceFormatEditorPanel()!=null ) {
                            edp.getDataSourceFormatEditorPanel().setURI(currentFileString);
                        }
                    }
                }

                if ( dsf.getController().getTsb()!=null ) {
                    edp.setTsb(true);
                }
                
                if ( JOptionPane.showConfirmDialog( parent, edp, "Export Data", JOptionPane.OK_CANCEL_OPTION )==JOptionPane.OK_OPTION ) {
                     try {
                        String name= edp.getFilenameTF().getText();
                        String ext = (String)edp.getFormatDL().getSelectedItem();

                        if ( name.startsWith("file://") ) {
                            name= name.substring(7);
                        }
                        if ( name.startsWith("file:") ) {
                            name= name.substring(5);
                        }
                        String osName =System.getProperty("os.name", "applet" );
                        if ( osName.startsWith("Windows") && name.startsWith("/") && name.length()>3 && name.charAt(2)==':' ) {
                            name= name.substring(1); // windows gets file:///c:/foo.wav
                        }

                        URISplit split= URISplit.parse(name);
                        if ( !split.file.endsWith(ext) ) {
                            String s=  split.file;
                            boolean addExt= true;
                            for ( int i=0; i<exts.size(); i++  ) {
                                if ( s.endsWith(exts.get(i)) ) {
                                    addExt= false;
                                    ext= exts.get(i);
                                }
                            }
                            if ( addExt ) {
                                split.file= s+ext;
                            }
                            //name= URISplit.format(split);
                        }

                        // mimic JChooser logic.
                        String s1= split.file;
                        if ( s1.startsWith("file://") ) {
                            s1= s1.substring(7);
                        }
                        if ( s1.startsWith("file:") ) {
                            s1= s1.substring(5);
                        }

                        File ff= new File(s1);
                        name= ff.getAbsolutePath();


                        if (ext == null) {
                            ext = "";
                        }

                        final DataSourceFormat format = DataSourceRegistry.getInstance().getFormatByExt(ext);
                        if (format == null) {
                            JOptionPane.showMessageDialog(parent, "No formatter for extension: " + ext);
                            return;
                        }
                        
                        String s= URISplit.format(split);

                        DataSourceFormatEditorPanel opts= edp.getDataSourceFormatEditorPanel();
                        if ( opts!=null ) {
                            URISplit splitopts= URISplit.parse(opts.getURI());
                            URISplit splits= URISplit.parse(s);
                            splitopts.file= splits.file;
                            s= URISplit.format(splitopts); //TODO: this probably needs a lookin at.
                            name= DataSourceUtil.unescape(s);
                        }

                        prefs.put("ExportDataCurrentFile", name );
                        prefs.put("ExportDataCurrentExt", ext );

                        final QDataSet fds= ds;
                        final String uriOut= s;

                        final String formatControl;
                        if ( edp.isFormatPlotElement() ) {
                            formatControl= "plotElement";
                        } else if ( edp.isFormatPlotElementAndTrim() ) {
                            formatControl= "plotElementTrim";
                        } else if ( edp.isOriginalData() ) {
                            formatControl= "dataSourceFilter";
                        } else {
                            JOptionPane.showMessageDialog(parent, "Selected data cannot be exported to this format " + ext );
                            return;
                        }
                        
                        Runnable run= new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    doDumpData( fds,dsf,pe,format,uriOut,formatControl );
                                } catch ( IOException ex ) {
                                    parent.applicationModel.getExceptionHandler().handle(ex);
                                }
                            }
                        };

                        new Thread( run ).start();

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

    Action getDumpDataAction() {
        return new AbstractAction("Export Data...") {
            @Override
            public void actionPerformed(ActionEvent e) {

                final QDataSet dataSet = parent.applicationModel.dom
                        .getController().getPlotElement().getController().getDataSet();

                if (dataSet == null) {
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
                            if ( f.toString()==null ) return false;
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

                Preferences prefs = Preferences.userNodeForPackage(AutoplotUI.class);
                String currentFileString = prefs.get("DumpDataCurrentFile", "");

                String name = (String) dataSet.property(QDataSet.NAME);
                if (name != null) {
                    chooser.setSelectedFile(new File(name.toLowerCase()));
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
                        prefs.flush();

                        String s = chooser.getSelectedFile().toURI().toString();

                        String ext = DataSetURI.getExt(s);
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
                        format.formatData( s,dataSet, new DasProgressPanel("formatting data"));
                        parent.setStatus("Wrote " + org.virbo.datasource.DataSourceUtil.unescape(s) );

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
        return new AbstractAction("Reset Window...") {
            @Override
            public void actionPerformed( ActionEvent e ) {
                if ( parent.stateSupport.isDirty() ) {
                    String msg= "The application has been modified.  Do you want to save your changes?";
                    int result= JOptionPane.showConfirmDialog(parent,msg );
                    if ( result==JOptionPane.OK_OPTION ) {
                        result= parent.stateSupport.saveAs();
                        if ( result==JFileChooser.CANCEL_OPTION ) {
                            return;
                        }
                    } else if ( result==JOptionPane.CANCEL_OPTION || result==JOptionPane.CLOSED_OPTION ) {
                        return;
                    }
                }
                Runnable run= new Runnable() {
                    @Override
                    public void run() {
                        parent.dom.getController().reset();
                        parent.undoRedoSupport.resetHistory();
                        parent.applicationModel.setVapFile(null);
                        parent.stateSupport.close();
                        parent.tickleTimer.tickle();
                    }
                };

                // https://sourceforge.net/tracker/?func=detail&aid=3557440&group_id=199733&atid=970682
                new Thread(run).start(); // allow reset when all the request processor threads are full.  TODO: I'm not sure why this appeared to be the case.
                //RequestProcessor.invokeLater(run);
            }
        };
    }

    /**
     * clone the application into a new AutoplotUI
     * @return
     */
    ApplicationModel newApplication() {
        final ApplicationModel model = new ApplicationModel();
        model.setExceptionHandler( GuiSupport.this.parent.applicationModel.getExceptionHandler() );
        Runnable run= new Runnable() {
            @Override
            public void run() {
                model.addDasPeersToApp();
                AutoplotUI view = new AutoplotUI(model);
                view.setLocationRelativeTo(GuiSupport.this.parent);
                view.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
                java.awt.Point p= view.getLocation();
                p.translate( 20,20 );
                view.setLocation( p );
                view.setVisible(true);
                OptionsPrefsController opc= new OptionsPrefsController( model.dom.getOptions() );
                opc.loadPreferencesWithEvents();
            }
        };
        try {
            if ( SwingUtilities.isEventDispatchThread() ) {
                run.run();
            } else {
                SwingUtilities.invokeAndWait(run);
            }
        } catch ( InterruptedException ex ) {
            throw new RuntimeException(ex);
        } catch ( InvocationTargetException ex ) {
            throw new RuntimeException(ex);
        }
        return model;
    }

    /**
     * clone the application into a new AutoplotUI
     * @return
     */
    ApplicationModel cloneApplication() {
        final ApplicationModel model = new ApplicationModel();
        model.setExceptionHandler( GuiSupport.this.parent.applicationModel.getExceptionHandler() );
        Runnable run= new Runnable() {
            @Override
            public void run() {
                model.addDasPeersToApp();
                model.dom.getOptions().setDataVisible( parent.applicationModel.dom.getOptions().isDataVisible() ); // options has funny sync code and these must be set before AutoplotUI is constructed.
                model.dom.getOptions().setLayoutVisible( parent.applicationModel.dom.getOptions().isLayoutVisible() );
                AutoplotUI view = new AutoplotUI(model);
                view.setLocationRelativeTo(GuiSupport.this.parent);
                view.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
                java.awt.Point p= view.getLocation();
                p.translate( 20,20 );
                view.setLocation( p );
                view.setVisible(true);
                OptionsPrefsController opc= new OptionsPrefsController( model.dom.getOptions() );
                opc.loadPreferencesWithEvents();
            }
        };
        try {
            if ( SwingUtilities.isEventDispatchThread() ) {
                run.run();
            } else {
                SwingUtilities.invokeAndWait(run);
            }
        } catch ( InterruptedException ex ) {
            throw new RuntimeException(ex);
        } catch ( InvocationTargetException ex ) {
            throw new RuntimeException(ex);
        }
        model.dom.syncTo( parent.applicationModel.dom );
        return model;
    }
    
    public Action createNewApplicationAction() {
        return new AbstractAction("New Window") {
            @Override
            public void actionPerformed( ActionEvent e ) {
                newApplication();
            }
        };
    }

    public Action createCloneApplicationAction() {
        return new AbstractAction("Clone to New Window") {
            @Override
            public void actionPerformed( ActionEvent e ) {
                cloneApplication();
            }
        };
    }

    private static Map<String,RenderType> getRenderTypeForString( ) {
        Map<String,RenderType> tt= new LinkedHashMap();
        tt.put( "Scatter", RenderType.scatter );
        tt.put( "Color Scatter", RenderType.colorScatter );
        tt.put( "Series", RenderType.series );
        tt.put( "Stair Steps", RenderType.stairSteps );
        tt.put( "Fill To Zero", RenderType.fillToZero );
        tt.put( "Huge Scatter", RenderType.hugeScatter );
        tt.put( "Spectrogram", RenderType.spectrogram );
        tt.put( "Nearest Neighbor Spectrogram", RenderType.nnSpectrogram);
        tt.put( "Digital", RenderType.digital);
        tt.put( "Events Bar", RenderType.eventsBar);
        tt.put( "Image", RenderType.image);
        tt.put( "Pitch Angle Distribution", RenderType.pitchAngleDistribution);
        tt.put( "Orbit Plot", RenderType.orbitPlot );
        return tt;
    }

    public static JMenu createEZAccessMenu(final Plot plot) {

        JMenu result = new JMenu("Plot Style");
        JMenuItem mi;

        Map<String,RenderType> tt= getRenderTypeForString();

        //tt.put( "Contour Plot", RenderType.contour );  //this has issues, hide for now.
        //tt.remove( "Pitch Angle Distribution" ); // this requires a specific scheme of data, hide for now (rte_1765139930_20130112_134531)

        for ( Entry<String,RenderType> ee: tt.entrySet() ) {
            final Entry<String,RenderType> fee= ee;
            mi= new JCheckBoxMenuItem(new AbstractAction(fee.getKey()) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    PlotElement pe = plot.getController().getApplication().getController().getPlotElement();
                    pe.setRenderType(fee.getValue());
                }
            });
            result.add(mi);
            //group.add(mi);
        }
  
        return result;
    }

    protected void addKeyBindings(JPanel thisPanel) {
        thisPanel.getActionMap().put("UNDO", parent.undoRedoSupport.getUndoAction());
        thisPanel.getActionMap().put("REDO", parent.undoRedoSupport.getRedoAction());
        thisPanel.getActionMap().put("RESET_ZOOM", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                parent.applicationModel.resetZoom();
            }
        });
        thisPanel.getActionMap().put("INCREASE_FONT_SIZE", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                parent.applicationModel.increaseFontSize();
            }
        });
        thisPanel.getActionMap().put("DECREASE_FONT_SIZE", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                parent.applicationModel.decreaseFontSize();
            }
        });

        thisPanel.getActionMap().put("NEXT_PLOT_ELEMENT", new AbstractAction() {
            @Override
            public void actionPerformed( ActionEvent e ) {
                Application dom= parent.dom;
                PlotElement p= dom.getController().getPlotElement();
                int idx= Arrays.asList( dom.getPlotElements() ).indexOf(p);
                if ( idx==-1 ) idx=0;
                idx++;
                if ( idx==dom.getPlotElements().length ) idx=0;
                dom.getController().setPlotElement( dom.getPlotElements(idx) );
            }
        });

        thisPanel.getActionMap().put("PREV_PLOT_ELEMENT", new AbstractAction() {
            @Override
            public void actionPerformed( ActionEvent e ) {
                Application dom= parent.dom;
                PlotElement p= dom.getController().getPlotElement();
                int idx= Arrays.asList( dom.getPlotElements() ).indexOf(p);
                if ( idx==-1 ) idx=0;
                idx--;
                if ( idx==-1 ) idx= dom.getPlotElements().length-1;
                dom.getController().setPlotElement( dom.getPlotElements(idx) );
            }
        });

        thisPanel.getActionMap().put("SAVE", new AbstractAction() {
            @Override
            public void actionPerformed( ActionEvent e ) {
                parent.stateSupport.createSaveAction().actionPerformed(e);
            }
        });

        InputMap map = new ComponentInputMap(thisPanel);

        Toolkit tk= Toolkit.getDefaultToolkit();
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, tk.getMenuShortcutKeyMask() ), "UNDO");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, tk.getMenuShortcutKeyMask() ), "REDO");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, tk.getMenuShortcutKeyMask() ), "RESET_ZOOM");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, tk.getMenuShortcutKeyMask()), "DECREASE_FONT_SIZE");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, tk.getMenuShortcutKeyMask()), "INCREASE_FONT_SIZE");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, tk.getMenuShortcutKeyMask()), "INCREASE_FONT_SIZE");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, KeyEvent.SHIFT_DOWN_MASK | tk.getMenuShortcutKeyMask()), "INCREASE_FONT_SIZE");  // american keyboard
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, KeyEvent.CTRL_DOWN_MASK), "NEXT_PLOT_ELEMENT");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, KeyEvent.SHIFT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK), "PREV_PLOT_ELEMENT");
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, tk.getMenuShortcutKeyMask() ), "SAVE");
        thisPanel.setInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW, map);

    }

    protected void exportRecent(Component c) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                if ( f.toString()==null ) return false;
                return f.isDirectory() || f.getName().endsWith(".xml");
            }
            @Override
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
                FileOutputStream out=null;
                try {
                    out= new FileOutputStream(f);
                    Bookmark.formatBooks(out,parent.applicationModel.getRecent());
                } finally {
                    if ( out!=null ) out.close();
                }
            } catch (IOException e) {
                logger.log( Level.WARNING, e.getMessage(), e );
            }
        }
    }

    private static FileFilter getFileNameExtensionFilter(final String description, final String ext) {
        return new FileFilter() {
            @Override
            public boolean accept(File f) {
                if ( f.toString()==null ) return false;
                return f.isDirectory() || f.toString().endsWith(ext);
            }
            @Override
            public String getDescription() {
                return description;
            }
        };
    }

    private static File currentFile;

    public static Action getPrintAction( final Application app, final Component parent,final String ext) {
        return new AbstractAction("Print as "+ext.toUpperCase()) {
            @Override
            public void actionPerformed(ActionEvent e) {
                final JPanel decor;
                final DasCanvas canvas = app.getController().getDasCanvas();
                final JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Print to "+ext.toUpperCase());
                fileChooser.setFileFilter(getFileNameExtensionFilter( ext + " files", ext ));
                Preferences prefs = Preferences.userNodeForPackage(DasCanvas.class);
                String savedir = prefs.get("savedir", null);
                if (savedir != null) fileChooser.setCurrentDirectory(new File(savedir));
                if (currentFile != null) {
                    if ( currentFile.toString().endsWith("."+ext) ) {
                        fileChooser.setSelectedFile(currentFile);
                    } else {
                        fileChooser.setSelectedFile(new File( currentFile.toString()+"."+ext) );
                    }
                }
                if ( ext.equals("pdf") ) {
                    decor= new PdfOptionsPanel();
                    fileChooser.setAccessory(decor);
                } else {
                    decor= null;
                }
                int choice = fileChooser.showSaveDialog(parent);
                if (choice == JFileChooser.APPROVE_OPTION) {

                    String fname = fileChooser.getSelectedFile().toString();
                    if (!fname.toLowerCase().endsWith("."+ext)) fname += "."+ext;
                    final String ffname = fname;
                    prefs.put("savedir", new File(ffname).getParent());
                    currentFile = new File(ffname.substring(0, ffname.length() - 4));
                    Runnable run = new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if ( ext.equals("png") ) {
                                    canvas.writeToPng(ffname);
                                } else if ( ext.equals("pdf") ) {
                                    FileOutputStream out=null;
                                    try {
                                        out = new FileOutputStream(ffname);
                                        PdfGraphicsOutput go = new PdfGraphicsOutput();

                                        PdfOptionsPanel pdecor= (PdfOptionsPanel)decor;
                                        go.setGraphicsShapes( pdecor.fontsAsShapesCB.isSelected() );
                                        go.setOutputStream(out);
                                        if ( pdecor.manualWidthCB.isSelected() ) {
                                            double mant= Double.parseDouble(pdecor.widthTF.getText()); //TODO>: FormattedTextField
                                            String units= (String)pdecor.unitsCB.getSelectedItem();
                                            if ( units.equals("inches") ) {
                                                mant= mant * 72;
                                            } else if ( units.equals("centimeters")) {
                                                mant= mant * 72 / 2.54;
                                            }
                                            double aspect= canvas.getHeight() / (double)canvas.getWidth();
                                            go.setSize( canvas.getWidth(), canvas.getHeight() );
                                            canvas.prepareForOutput( (int)mant, (int)(mant*aspect));
                                        } else {
                                            go.setSize( canvas.getWidth(), canvas.getHeight() );
                                        }
                                        go.start();
                                        canvas.print(go.getGraphics());
                                        go.finish();
                                    } finally {
                                        if ( out!=null ) out.close();                                    
                                    }

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

    /**
     * allow user to pick out data from a vap file.
     * @param dom
     * @param plot
     * @param pelement
     * @param vap
     */
    private static void mergeVap( Application dom, Plot plot, PlotElement pelement, String vap ) {
        try {
            ImportVapDialog d = new ImportVapDialog();
            if ( vap.indexOf("?")!=-1 ) {
                int i= vap.indexOf("?");
                vap= vap.substring(0,i);
            }
            d.setVap(vap);
            if ( d.showDialog( SwingUtilities.getWindowAncestor( dom.getController().getDasCanvas() ) )==JOptionPane.OK_OPTION ) {
                String lock= "merging vaps";
                dom.getController().registerPendingChange( d,lock );
                dom.getController().performingChange( d, lock );
                List<String> uris= d.getSelectedURIs();
                for ( String uri: uris ) {
                    dom.getController().doplot( plot, pelement, uri );
                    pelement= null; //otherwise we'd clobber last dataset.
                }
                dom.getController().changePerformed( d, lock );
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    /**
     * Maybe import the bookmarks in response to the "bookmarks:..." URI.
     * @param bookmarksFile URL which refers to a local, HTTP, HTTPS, or FTP resource.
     */
    public void importBookmarks( String bookmarksFile )  {

        ImportBookmarksGui gui= new ImportBookmarksGui();
        gui.getBookmarksFilename().setText(bookmarksFile+" ?");
        gui.getRemote().setSelected(true);
        int r = JOptionPane.showConfirmDialog( parent, gui, "Import bookmarks file", JOptionPane.OK_CANCEL_OPTION );
        if (r == JOptionPane.OK_OPTION) {
            InputStream in = null;
            try {
                ProgressMonitor mon = DasProgressPanel.createFramed("importing bookmarks");
                if ( gui.getRemote().isSelected() ) {
                    parent.getBookmarksManager().getModel().addRemoteBookmarks(bookmarksFile);
                    parent.getBookmarksManager().reload();
                } else {
                    in = DataSetURI.getInputStream(DataSetURI.getURIValid(bookmarksFile), mon);
                    ByteArrayOutputStream boas=new ByteArrayOutputStream();
                    WritableByteChannel dest = Channels.newChannel(boas);
                    ReadableByteChannel src = Channels.newChannel(in);
                    DataSourceUtil.transfer(src, dest);
                    String sin= new String( boas.toByteArray() );
                    List<Bookmark> books= Bookmark.parseBookmarks(sin);
                    parent.getBookmarksManager().getModel().importList( books );
                }
                parent.setMessage( "imported bookmarks file "+bookmarksFile );
            } catch (BookmarksException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                parent.applicationModel.showMessage( "Semantic Error parsing "+bookmarksFile+ "\n"+ex.getMessage(), "Error in import bookmarks", JOptionPane.WARNING_MESSAGE );
            } catch (SAXException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                parent.applicationModel.showMessage( "XML Error parsing "+bookmarksFile+ "\n"+ex.getMessage(), "Error in import bookmarks", JOptionPane.WARNING_MESSAGE );
            } catch (URISyntaxException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            } catch (FileNotFoundException ex ) {
                parent.applicationModel.showMessage( "File not found: "+bookmarksFile, "Error in import bookmarks", JOptionPane.WARNING_MESSAGE );
            } catch (IOException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                parent.applicationModel.showMessage( "I/O Error with "+bookmarksFile, "Error in import bookmarks", JOptionPane.WARNING_MESSAGE );
            } finally {
                try {
                    if ( in!=null ) in.close();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
        }

    }


    private static void handleAddElementDialog(AddPlotElementDialog dia, Application dom, ApplicationModel applicationModel) {
        Plot plot = null;
        PlotElement pelement = null;
        int modifiers = dia.getModifiers();
        if ( (modifiers & KeyEvent.CTRL_MASK) == KeyEvent.CTRL_MASK && (modifiers & KeyEvent.SHIFT_MASK) == KeyEvent.SHIFT_MASK ) {
            // reserve this for plot above, which we'll add soon.
            plot = null;
            pelement = null;
        } else if ((modifiers & KeyEvent.CTRL_MASK) == KeyEvent.CTRL_MASK) {
            // new plot
            plot = null;
            pelement = null;
            //nothing
        } else if ((modifiers & KeyEvent.SHIFT_MASK) == KeyEvent.SHIFT_MASK) {
            // overplot
            plot = dom.getController().getPlot();
        } else {
            pelement = dom.getController().getPlotElement();
        }
        try {
            if (dia.getDepCount() == 0) {
                String val= dia.getPrimaryDataSetSelector().getValue();
                if ( val.endsWith(".vap") ) {
                    mergeVap(dom,plot, pelement, val);
                } else {
                    dom.getController().doplot(plot, pelement, val );
                }
            } else if (dia.getDepCount() == 1) {
                applicationModel.addRecent(dia.getPrimaryDataSetSelector().getValue());
                applicationModel.addRecent(dia.getSecondaryDataSetSelector().getValue());
                dom.getController().doplot(plot, pelement, dia.getSecondaryDataSetSelector().getValue(), dia.getPrimaryDataSetSelector().getValue());
            } else if (dia.getDepCount() == 2) {
                applicationModel.addRecent(dia.getPrimaryDataSetSelector().getValue());
                applicationModel.addRecent(dia.getSecondaryDataSetSelector().getValue());
                applicationModel.addRecent(dia.getTertiaryDataSetSelector().getValue());
                dom.getController().doplot(plot, pelement, dia.getSecondaryDataSetSelector().getValue(), dia.getTertiaryDataSetSelector().getValue(), dia.getPrimaryDataSetSelector().getValue());
            } else if (dia.getDepCount() == -1) {
                //if (pelement == null) {
                //    pelement = dom.getController().addPlotElement(plot, null);
                //}
            }
        } catch ( Exception ex ) { // TODO: the IllegalArgumentException is wrapped in a RuntimeException, I don't know why.  I should have MalformedURIException
            applicationModel.showMessage( ex.getMessage(), "Illegal Argument", JOptionPane.ERROR_MESSAGE );
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

        List<JMenuItem> expertMenuItems= new ArrayList();

        mouseAdapter.removeMenuItem("Properties");

        JMenuItem item;

        item= new JMenuItem(new AbstractAction("Axis Properties") {
            @Override
            public void actionPerformed(ActionEvent e) {
                PropertyEditor pp = new PropertyEditor(axis);
                pp.showDialog(dasAxis.getCanvas());
            }
        });
        mouseAdapter.addMenuItem(item);
        expertMenuItems.add(item);
        
        mouseAdapter.addMenuItem(new JSeparator());

        if (axis == plot.getXaxis()) {
            JMenu addPlotMenu = new JMenu("Add Plot");
            mouseAdapter.addMenuItem(addPlotMenu);

            item = new JMenuItem(new AbstractAction("Bound Plot Below") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    controller.copyPlot(plot, true, false, true);
                }
            });
            item.setToolTipText("add a new plot below.  The plot's x axis will be bound to this plot's x axis");
            addPlotMenu.add(item);
            expertMenuItems.add( addPlotMenu );
        }

        JMenu bindingMenu = new JMenu("Binding");

        mouseAdapter.addMenuItem(bindingMenu);

        if (axis == plot.getXaxis()) {
            item = new JMenuItem(new AbstractAction("Add Binding to Application Time Range") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    DatumRange dr= controller.getApplication().getTimeRange();
                    if ( dr==Application.DEFAULT_TIME_RANGE ) {
                        controller.getApplication().setTimeRange( dr.next() );
                        controller.getApplication().setTimeRange( dr.next().previous() ); // so it accepts the value and fires event
                    }
                    controller.bind(controller.getApplication(), Application.PROP_TIMERANGE, axis, Axis.PROP_RANGE);
                }
            });
            bindingMenu.add(item);
        }

        item = new JMenuItem(new AbstractAction("Add Binding to Plot Above") {
            @Override
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
        item = new JMenuItem(new AbstractAction("Add Binding to Plot Below") {
            @Override
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
        item = new JMenuItem(new AbstractAction("Add Binding to Plot to the Right") {
            @Override
            public void actionPerformed(ActionEvent e) {
                Plot dstPlot = controller.getNextPlotHoriz(plot,LayoutConstants.RIGHT);
                if (dstPlot == null) {
                    controller.setStatus("warning: no plot to the right");
                } else {
                    bindToPlotPeer(controller,dstPlot, plot, axis);
                }
            }
        });
        bindingMenu.add(item);
        item = new JMenuItem(new AbstractAction("Add Binding to Plot to the Left") {
            @Override
            public void actionPerformed(ActionEvent e) {
                Plot dstPlot = controller.getNextPlotHoriz(plot,LayoutConstants.LEFT);
                if (dstPlot == null) {
                    controller.setStatus("warning: no plot to the left");
                } else {
                    bindToPlotPeer(controller,dstPlot, plot, axis);
                }
            }
        });
        bindingMenu.add(item);

        item = new JMenuItem(new AbstractAction("Remove Bindings") {
            @Override            
            public void actionPerformed(ActionEvent e) {
                BindingModel[] bms= controller.getBindingsFor(axis);
                controller.unbind(axis);  // TODO: check for application timerange
                controller.setStatus("removed "+bms.length+" bindings");
            }
        });
        item.setToolTipText("remove any plot and plot element property bindings");
        bindingMenu.add(item);

        expertMenuItems.add(bindingMenu);

        JMenu connectorMenu = new JMenu("Connector");

        mouseAdapter.addMenuItem(connectorMenu);

        item = new JMenuItem(new AbstractAction("Add Connector to Plot Above") {
            @Override
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

        item = new JMenuItem(new AbstractAction("Add Connector to Plot Below") {
            @Override
            public void actionPerformed(ActionEvent e) {
                Plot dstPlot = controller.getPlotBelow(plot);
                if (dstPlot == null) {
                    controller.setStatus("warning: no plot below");
                } else {
                    controller.addConnector(plot,dstPlot);
                }
            }
        });
        connectorMenu.add(item);

        item = new JMenuItem(new AbstractAction("Delete Connectors") {
            @Override
            public void actionPerformed(ActionEvent e) {
                Application dom= plot.getController().getApplication();
                for (Connector c : DomUtil.asArrayList(dom.getConnectors())) {
                    if (c.getPlotA().equals(plot.getId()) || c.getPlotB().equals(plot.getId())) {
                        dom.getController().deleteConnector(c);
                    }
                }
                dom.getController().getCanvas().getController().getDasCanvas().repaint();
            }
        });
        
        connectorMenu.add(item);

        expertMenuItems.add(connectorMenu);

        if ( axis.getController().getDasAxis().isHorizontal() ) {
            item= new JMenuItem( new AbstractAction("Add Additional Ticks from...") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String uri= plot.getTicksURI();
                    if ( uri.startsWith("class:org.autoplot.tca.UriTcaSource:") ) {
                        uri= uri.substring("class:org.autoplot.tca.UriTcaSource:".length());
                    }
                    TcaElementDialog dia= new TcaElementDialog( (JFrame)SwingUtilities.getWindowAncestor( controller.getDasCanvas().getParent()), true );
                    dia.getPrimaryDataSetSelector().setValue(uri);
                    dia.setTitle( "Add additional ticks" );
                    dia.setVisible(true);
                    if (dia.isCancelled()) {
                        return;
                    }
                    uri= dia.getPrimaryDataSetSelector().getValue();
                    if ( uri.length()==0 ) {
                        plot.setTicksURI("");
                    } else {
                        plot.setTicksURI(uri);
                    }
                }
            });
            mouseAdapter.addMenuItem(item);
            expertMenuItems.add(item);

        }

        List<JMenuItem> expertMenuItemsList= new ArrayList( Arrays.asList( plotController.getExpertMenuItems() ) );
        expertMenuItemsList.addAll(expertMenuItems);

        plotController.setExpertMenuItems( expertMenuItemsList.toArray(new JMenuItem[expertMenuItemsList.size()] )  );

    }

    /**
     * Add items to the plot context menu, such as properties and add plot.
     * @param controller
     * @param plot
     * @param plotController
     * @param domPlot
     */
    static void addPlotContextMenuItems( final AutoplotUI app, final ApplicationController controller, final DasPlot plot, final PlotController plotController, final Plot domPlot) {

        plot.getDasMouseInputAdapter().addMouseModule(new MouseModule(plot, new PointSlopeDragRenderer(plot, plot.getXAxis(), plot.getYAxis()), "Slope"));

        plot.getDasMouseInputAdapter().removeMenuItem("Dump Data");
        plot.getDasMouseInputAdapter().removeMenuItem("Properties");

        JMenuItem item;

        List<JMenuItem> expertMenuItems= new ArrayList();

        JMenuItem mi;

        mi= new JMenuItem(new AbstractAction("Plot Properties") {
            @Override
            public void actionPerformed(ActionEvent e) {
                PropertyEditor pp = new PropertyEditor(domPlot);
                pp.showDialog(plot.getCanvas());
            }
        });
        plot.getDasMouseInputAdapter().addMenuItem(mi);
        expertMenuItems.add( mi );

        mi= new JMenuItem(new AbstractAction("Plot Element Properties") {
            @Override
            public void actionPerformed(ActionEvent e) {
                PlotElement p = controller.getPlotElement();
                PropertyEditor pp = new PropertyEditor(p);
                pp.showDialog(plot.getCanvas());
            }
        } );
        plot.getDasMouseInputAdapter().addMenuItem( mi );
        expertMenuItems.add( mi );


        JMenuItem panelPropsMenuItem= new JMenuItem(new AbstractAction("Plot Element Style Properties") {
            @Override
            public void actionPerformed(ActionEvent e) {
                PlotElement p = controller.getPlotElement();
                PropertyEditor pp = new PropertyEditor(p.getStyle());
                pp.showDialog(plot.getCanvas());
            }
        });
        plotController.setPlotElementPropsMenuItem(panelPropsMenuItem);        
        plot.getDasMouseInputAdapter().addMenuItem(panelPropsMenuItem);
        expertMenuItems.add(panelPropsMenuItem);

        plot.getDasMouseInputAdapter().addMenuItem(new JSeparator());

        final JMenu ezMenu= GuiSupport.createEZAccessMenu(domPlot);
        ezMenu.addMenuListener( new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                PlotElement pe= app.dom.getController().getPlotElement();
                QDataSet ds;
                if ( pe!=null ) {
                    ds= pe.getController().getDataSet();
                } else {
                    return;
                }
                Map<String,RenderType> tt= getRenderTypeForString();
                for ( int i=0; i<ezMenu.getItemCount(); i++ ) {
                    if ( ezMenu.getItem(i) instanceof JCheckBoxMenuItem ) {
                        JCheckBoxMenuItem mi= ((JCheckBoxMenuItem)ezMenu.getItem(i));
                        RenderType rt= tt.get( mi.getText() );
                        if ( rt.equals(pe.getRenderType()) ) {
                            mi.setSelected(true);
                        } else {
                            mi.setSelected(false);
                        }
                        if ( pe.getController().getParentPlotElement()!=null ) {
                            ds= pe.getController().getParentPlotElement().getController().getDataSet();
                            if ( ds==null || RenderType.acceptsData( rt, ds ) ) {
                                mi.setEnabled(true);
                            } else {
                                mi.setEnabled(false);
                            }
                        } else {
                            if ( ds==null || RenderType.acceptsData( rt, ds ) ) {
                                mi.setEnabled(true);
                            } else {
                                mi.setEnabled(false);
                            }
                        }
                    }
                }
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {                
            }

        });
        plot.getDasMouseInputAdapter().addMenuItem(ezMenu);
        expertMenuItems.add(ezMenu);

        JMenu addPlotMenu = new JMenu("Add Plot");
        plot.getDasMouseInputAdapter().addMenuItem(addPlotMenu);

        item = new JMenuItem(new AbstractAction("Copy Plot Elements Down") {
            @Override
            public void actionPerformed(ActionEvent e) {
                Runnable run= new Runnable() {
                    public void run() {
                        Plot newPlot= controller.copyPlotAndPlotElements(domPlot, null, false, false);
                        Application dom= domPlot.getController().getApplication();
                        List<BindingModel> bms= dom.getController().findBindings( dom, Application.PROP_TIMERANGE, domPlot.getXaxis(), Axis.PROP_RANGE );
                        if ( bms.size()>0 && UnitsUtil.isTimeLocation( newPlot.getXaxis().getRange().getUnits() ) ) {
                            controller.bind( controller.getApplication(), Application.PROP_TIMERANGE, newPlot.getXaxis(), Axis.PROP_RANGE );
                        }
                    }
                };
                SwingUtilities.invokeLater(run);
                //run.run();
            }
        });
        item.setToolTipText("make a new plot below, and copy the plot elements into it.  New plot is bound by the x axis.");
        addPlotMenu.add(item);

//        item = new JMenuItem(new AbstractAction("Copy Plot Elements Right") {
//
//            public void actionPerformed(ActionEvent e) {
//                DomOps.copyPlotAndPlotElements(domPlot,true,false,false,LayoutConstants.RIGHT);
//            }
//        });
//        item.setToolTipText("make a new plot to the right, and copy the plot elements into it.");
//        addPlotMenu.add(item);

        item = new JMenuItem(new AbstractAction("Context Overview") {
            @Override
            public void actionPerformed(ActionEvent e) {
                Runnable run= new Runnable() {
                    @Override
                    public void run() {
                        domPlot.getController().contextOverview();
                    }
                };
                SwingUtilities.invokeLater(run);
                //run.run();
            }
        });
        item.setToolTipText("make a new plot, and copy the plot elements into it.  The plot is not bound,\n" +
                "and a connector is drawn between the two.  The panel uris are bound as well.");
        
        addPlotMenu.add(item);
        item = new JMenuItem(new AbstractAction("New Location (URI)...") {
            @Override
            public void actionPerformed(ActionEvent e) {
                Runnable run= new Runnable() {
                    @Override
                    public void run() {
                        app.dom.getController().setPlot(domPlot);
                        app.support.addPlotElement();
                    }
                };
                SwingUtilities.invokeLater(run);
                //run.run();
            }
        });
        item.setToolTipText("change URI or add plot");
        addPlotMenu.add(item);


        expertMenuItems.add(addPlotMenu);

        JMenu editPlotMenu = new JMenu("Edit Plot");
        plot.getDasMouseInputAdapter().addMenuItem(editPlotMenu);
        controller.fillEditPlotMenu(editPlotMenu, domPlot);
        expertMenuItems.add(editPlotMenu);

        JMenu panelMenu = new JMenu("Edit Plot Element");
        plot.getDasMouseInputAdapter().addMenuItem(panelMenu);
        expertMenuItems.add(panelMenu);

        item = new JMenuItem(new AbstractAction("Move to Plot Above") {
            @Override
            public void actionPerformed(ActionEvent e) {
                PlotElement pelement = controller.getPlotElement();
                Plot plot = controller.getPlotFor(pelement);
                Plot dstPlot = controller.getPlotAbove(plot);
                if (dstPlot == null) {
                    dstPlot = controller.addPlot(LayoutConstants.ABOVE);
                    pelement.setPlotId(dstPlot.getId());
                } else {
                    pelement.setPlotId(dstPlot.getId());
                }
            }
        });
        panelMenu.add(item);
        expertMenuItems.add(panelMenu);

        item = new JMenuItem(new AbstractAction("Move to Plot Below") {
            @Override
            public void actionPerformed(ActionEvent e) {
                PlotElement pelement = controller.getPlotElement();
                Plot plot = controller.getPlotFor(pelement);
                Plot dstPlot = controller.getPlotBelow(plot);
                if (dstPlot == null) {
                    dstPlot = controller.addPlot(LayoutConstants.BELOW);
                    pelement.setPlotId(dstPlot.getId());
                    controller.bind(plot.getXaxis(), Axis.PROP_RANGE, dstPlot.getXaxis(), Axis.PROP_RANGE);
                } else {
                    pelement.setPlotId(dstPlot.getId());
                }
            }
        });
        panelMenu.add(item);
        expertMenuItems.add(item);

        item = new JMenuItem(new AbstractAction("Delete Plot Element") {
            @Override
            public void actionPerformed(ActionEvent e) {
                PlotElement pelement = controller.getPlotElement();
                if (controller.getApplication().getPlotElements().length < 2) {
                    DataSourceFilter dsf= controller.getDataSourceFilterFor(controller.getApplication().getPlotElements(0));
                    dsf.setUri("");
                    pelement.setLegendLabelAutomatically(""); //TODO: null should reset everything.
                    pelement.setActive(true);
                    return;
                }
                controller.deletePlotElement(pelement);
            }
        });
        panelMenu.add(item);
        expertMenuItems.add(item);

        item=  new JMenuItem(new AbstractAction("Move Plot Element Below Others") {
            @Override
            public void actionPerformed(ActionEvent e) {
                PlotElement pelement = controller.getPlotElement();
                Plot p= pelement.getController().getApplication().getController().getPlotFor(pelement);
                p.getController().toBottom(pelement);
            }
        });
        panelMenu.add(item);
        expertMenuItems.add(item);

        JMenuItem editDataMenu = new JMenuItem(new AbstractAction("Edit Data Source") {
            @Override
            public void actionPerformed(ActionEvent e) {
                GuiSupport.editPlotElement( controller.getApplicationModel(), plot );
            }
        });
        expertMenuItems.add(editDataMenu);

        plot.getDasMouseInputAdapter().addMenuItem(editDataMenu);

        plot.getDasMouseInputAdapter().addMenuItem(new JSeparator());

        plot.getDasMouseInputAdapter().addMenuItem(new JMenuItem(new AbstractAction("Reset Zoom") {
            @Override
            public void actionPerformed(ActionEvent e) {
                plotController.resetZoom(true, true, true);
            }
        }));

        plot.getDasMouseInputAdapter().addMenuItem(new JSeparator());

        plotController.setExpertMenuItems( expertMenuItems.toArray(new JMenuItem[expertMenuItems.size()] ) );

    }

    protected void doInspectVap() {

        JFileChooser chooser= new JFileChooser();
        FileFilter ff = new FileFilter() {

            @Override
            public boolean accept(File f) {
                if ( f.toString()==null ) return false;
                return f.toString().endsWith(".vap") || f.isDirectory();
            }

            @Override
            public String getDescription() {
                return "*.vap";
            }
        };
        chooser.addChoosableFileFilter(ff);

        chooser.setFileFilter(ff);
        chooser.setCurrentDirectory( new File( parent.stateSupport.getDirectory() ) );
        if ( JFileChooser.APPROVE_OPTION==chooser.showOpenDialog(parent) ) {
            try {
                final File f= chooser.getSelectedFile();
                final Application vap = (Application) StatePersistence.restoreState( f );
                PropertyEditor edit = new PropertyEditor(vap);
                edit.addSaveAction( new AbstractAction("Save") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        try {
                            StatePersistence.saveState(f, vap);
                        } catch (IOException ex) {
                            JOptionPane.showConfirmDialog( parent, "Unable to save to file: "+ f );
                        }
                    }
                });
                edit.showDialog(this.parent);
            } catch ( Exception ex ) {
                logger.log(Level.WARNING,ex.getMessage(),ex);
                JOptionPane.showMessageDialog( parent, "File does not appear to well-formatted .vap file" );
            }

        }
    }

    /**
     * show the pick font dialog.  The font chosen, is applied and returned, or null if cancel was pressed. 
     * 
     * @return
     */
    public static Font pickFont( Frame parent, ApplicationModel app ) {
        JFontChooser chooser = new JFontChooser( parent );
        String sci= Entities.decodeEntities("2 &times; 10E7  &aacute;");
        String greek= Entities.decodeEntities("Greek Symbols: &Alpha; &Beta; &Delta; &alpha; &beta; &delta; &pi; &rho; &omega;");
        String math= Entities.decodeEntities("Math Symbols: &sum; &plusmn;");

        chooser.setExampleText("Electron Differential Energy Flux\n2001-01-10 12:00\nExtended ASCII: "+sci+"\n"+greek+"\n"+math);
        chooser.setFontCheck( new JFontChooser.FontCheck() {
            @Override
            public String checkFont(Font c) {
                String font= PdfGraphicsOutput.ttfFromNameInteractive(c);
                if ( font==PdfGraphicsOutput.READING_FONTS ) {
                    return null;
                } else if ( font!=null ) {
                    return "PDF okay";
                } else {                    
                    return "Cannot be embedded in PDF";
                }
            }
        });
        chooser.setFont(app.getCanvas().getBaseFont());
        chooser.setLocationRelativeTo(parent);
        if (chooser.showDialog() == JFontChooser.OK_OPTION) {
            return setFont( app, chooser.getFont() );
        } else {
            return null;
        }
    }

    /**
     * encapsulates the goofy logic about setting the font.
     * @param app
     * @param nf
     * @return the Font actually used.
     */
    public static Font setFont( ApplicationModel app, Font nf ) {
        app.getCanvas().setBaseFont(nf);
        Font f = app.getCanvas().getFont();
        app.getDocumentModel().getOptions().setCanvasFont( DomUtil.encodeFont(f) );
        return f;
    }
    /**
     * raise the application window
     * http://stackoverflow.com/questions/309023/howto-bring-a-java-window-to-the-front
     * This is not working for me on Ubuntu 10.04.
     */
    public static void raiseApplicationWindow( java.awt.Frame frame ) {
        // http://stackoverflow.com/questions/309023/howto-bring-a-java-window-to-the-front
        frame.setVisible(true);
        int state = frame.getExtendedState();
        state &= ~JFrame.ICONIFIED;
        frame.setExtendedState(state);
        frame.setAlwaysOnTop(true); // security exception
        frame.toFront();
        frame.requestFocus();
        frame.setAlwaysOnTop(false); // security exception
    }

    public static MouseAdapter createExamplesPopup( final JTextField tf, final String [] labels, final String[] tooltips ) {
        return new MouseAdapter() {
            private JMenuItem createMenuItem( final JTextField componentTextField, final String insert, String doc ) {
                JMenuItem result= new JMenuItem( new AbstractAction( insert ) {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        String v= componentTextField.getText();
                        int i= componentTextField.getCaretPosition();
                        componentTextField.setText( v.substring(0,i) + insert + v.substring(i) );
                    }
                });
                if ( doc!=null ) result.setToolTipText(doc);
                return result;
            }
            void showPopup( MouseEvent ev ) {
                JPopupMenu processMenu;
                processMenu= new JPopupMenu();
                for ( int i=0; i<labels.length; i++ ) {
                    processMenu.add( createMenuItem( tf, labels[i], tooltips==null ? null : tooltips[i] ) );
                }
                processMenu.show(ev.getComponent(), ev.getX(), ev.getY());
            }

            @Override
            public void mousePressed(MouseEvent evt) {
                if ( evt.isPopupTrigger() ) {
                    showPopup(evt);
                }
            }

            @Override
            public void mouseReleased(MouseEvent evt) {
                if ( evt.isPopupTrigger() ) {
                    showPopup(evt);
                }
            }

        };
    }
}
