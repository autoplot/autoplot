/*
 * GuiSupport.java
 *
 * Created on November 30, 2007, 5:04 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.autoplot;

import java.awt.Component;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.HeadlessException;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
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
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.autoplot.bookmarks.Bookmark;
import org.autoplot.bookmarks.BookmarksException;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.datasource.DataSetSelector;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;
import org.autoplot.datasource.DataSourceFormat;
import org.autoplot.datasource.DataSourceFormatEditorPanel;
import org.autoplot.datasource.DataSourceRegistry;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.WindowManager;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.autoplot.dom.Annotation;
import org.autoplot.dom.Application;
import org.autoplot.dom.ApplicationController;
import org.autoplot.dom.Axis;
import org.autoplot.dom.BindingModel;
import org.autoplot.dom.Canvas;
import org.autoplot.dom.ChangesSupport.DomLock;
import org.autoplot.dom.Connector;
import org.autoplot.dom.DataSourceFilter;
import org.autoplot.dom.DomUtil;
import org.autoplot.dom.Plot;
import org.autoplot.dom.PlotController;
import org.autoplot.dom.PlotElement;
import org.autoplot.layout.LayoutConstants;
import org.autoplot.renderer.BoundsStylePanel;
import org.autoplot.renderer.ColorScatterStylePanel;
import org.autoplot.renderer.ContourStylePanel;
import org.autoplot.renderer.DigitalStylePanel;
import org.autoplot.renderer.EventsStylePanel;
import org.autoplot.renderer.HugeScatterStylePanel;
import org.autoplot.renderer.ImageStylePanel;
import org.autoplot.renderer.InternalStylePanel;
import org.autoplot.renderer.OrbitStylePanel;
import org.autoplot.renderer.PitchAngleDistributionStylePanel;
import org.autoplot.renderer.SeriesStylePanel;
import org.autoplot.renderer.SpectrogramStylePanel;
import org.autoplot.renderer.StackedHistogramStylePanel;
import org.autoplot.state.StatePersistence;
import org.autoplot.transferrable.ImageSelection;
import org.autoplot.util.GraphicsUtil;
import org.das2.DasApplication;
import org.das2.components.DasProgressPanel;
import org.das2.components.propertyeditor.PropertyEditor;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.event.DasMouseInputAdapter;
import org.das2.event.MouseModule;
import org.das2.event.PointSlopeDragRenderer;
import org.das2.graph.DasAxis;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasPlot;
import org.das2.qds.DataSetOps;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.das2.system.RequestProcessor;
import org.das2.util.Entities;
import org.das2.util.awt.PdfGraphicsOutput;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.xml.sax.SAXException;
import ZoeloeSoft.projects.JFontChooser.JFontChooser;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import org.autoplot.dom.Column;
import org.autoplot.dom.DomNode;
import org.autoplot.dom.Row;
import org.autoplot.renderer.AnnotationEditorPanel;
import org.das2.components.propertyeditor.EnumerationEditor;
import org.das2.datum.Datum;
import org.das2.graph.DasColorBar;

/**
 * Extra methods to support AutoplotUI.
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
            } catch (UnsupportedFlavorException | IOException ex) {
                //highly unlikely since we are using a standard DataFlavor
                logger.log( Level.WARNING, ex.getMessage(), ex );
            }
        }
        if (result != null) {
            parent.dataSetSelector.setValue(result);
        }
    }

    /**
     * copy the current URI to the system clipboard.
     */
    public void doCopyDataSetURL() {
        setClipboard( DataSetURI.toUri(parent.dataSetSelector.getValue()).toString() );
    }
    
    /**
     * set the system clipboard (cut-n-paste mouse buffer).
     * @param s 
     */
    public static void setClipboard( String s ) {
        StringSelection stringSelection = new StringSelection( s );
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
    
    
    /**
     * return a GUI controller for the RenderType.
     * @param renderType the render type, such as RenderType.colorScatter
     * @return the GUI controller.
     */
    public static PlotStylePanel.StylePanel getStylePanel( RenderType renderType ) { 
        PlotStylePanel.StylePanel editorPanel;
        if ( null == renderType ) {
            //TODO: consider generic style panel that is based on completions of Renderer control.
            editorPanel= new SeriesStylePanel( );
            return editorPanel;
        } 
        switch (renderType) {
            case spectrogram:
            case nnSpectrogram:
                editorPanel= new SpectrogramStylePanel( );
                break;
            case pitchAngleDistribution:
                editorPanel= new PitchAngleDistributionStylePanel( );
                break;
            case polar:
                editorPanel= new ColorScatterStylePanel( );
                break;
            case hugeScatter:
                editorPanel= new HugeScatterStylePanel( );
                break;
            case colorScatter:
                editorPanel= new ColorScatterStylePanel( );
                break;
            case contour:
                editorPanel= new ContourStylePanel( );
                break;
            case internal:
                editorPanel= new InternalStylePanel( );
                break;
            case bounds:
                editorPanel= new BoundsStylePanel( );
                break;
            case digital:
                editorPanel= new DigitalStylePanel( );
                break;
            case orbitPlot:
                editorPanel= new OrbitStylePanel( );
                break;
            case eventsBar:
                editorPanel= new EventsStylePanel( );
                break;
            case stackedHistogram:
                editorPanel= new StackedHistogramStylePanel( );
                break;
            case image:
                editorPanel= new ImageStylePanel( );
                break;
            default:
                //TODO: consider generic style panel that is based on completions of Renderer control.
                editorPanel= new SeriesStylePanel( );
                break;
        }
        return editorPanel;
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
        
        WindowManager.getInstance().showModalDialog( dia );
        
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
            switch (depCount) {
                case 2:
                    groups= new int[] { 5,1,3 };
                    break;
                case 1:
                    groups= new int[] { 3,1 };
                    break;
                default:
                    groups= new int[] { 1 };
                    break;
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
                    dia.setFilter(i,dsf.getFilters());                               
                }
            }
        } else {
            dia.getPrimaryDataSetSelector().setValue( suri );
        }
    }


    /**
     * enter dialog to possibly add a plot to the vap, or combine multiple 
     * URIs combined into an internal dataset.
     * @param title title for the popup.
     */
    void addPlotElement( String title ) {

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

        if ( title==null ) {
            title= "Adding Plot Element";
        }
        dia.setTitle( title );
        
        WindowManager.getInstance().showModalDialog(dia);

        if (dia.isCancelled()) {
            return;
        }
        handleAddElementDialog(dia, dom, applicationModel);

    }
    
    /**
     * same as addPlotElement, but a future version may allow the timerange to be set, combining things into one
     * GUI.
     * @param title title for the popup.
     * @param furi the URI.
     */
    void addPlotElementFromBookmark( String title, String furi ) {
        DataSourceFactory factory=null;
        try {
            factory = DataSetURI.getDataSourceFactory( DataSetURI.getURI(furi), new NullProgressMonitor() );
        } catch (IOException | IllegalArgumentException | URISyntaxException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        assert factory!=null; // we checked this earlier.
        TimeSeriesBrowse tsb= factory.getCapability( TimeSeriesBrowse.class );
        DatumRange uriRange= null;
        if ( tsb!=null ) {
            try {
                tsb.setURI(furi);
                uriRange= tsb.getTimeRange();
            } catch (ParseException ex) {
                Logger.getLogger(AutoplotUI.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        Application dom= parent.getDom();
        String uri= furi;
        DatumRange dr;
        if ( !(dom.getTimeRange()==Application.DEFAULT_TIME_RANGE) && !dom.getTimeRange().equals( uriRange ) ) {
            
            dr= DataSetSelector.pickTimeRange( parent, 
                Arrays.asList( dom.getTimeRange(), uriRange ),
                Arrays.asList( "Current", "URI" )
                );
            if ( dr!=uriRange ) {
                try {
                    uri= DataSourceUtil.setTimeRange(uri,dom.getTimeRange(),new NullProgressMonitor());
                } catch (URISyntaxException | IOException | ParseException ex) {
                    Logger.getLogger(AutoplotUI.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        ApplicationModel applicationModel = parent.applicationModel;

        AddPlotElementDialog dia = new AddPlotElementDialog( parent, true);
        dia.getPrimaryDataSetSelector().setTimeRange(dom.getTimeRange());
        dia.getSecondaryDataSetSelector().setTimeRange(dom.getTimeRange());
        dia.getTertiaryDataSetSelector().setTimeRange(dom.getTimeRange());

        String val= uri;
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

        if ( title==null ) {
            title= "Adding Plot Element";
        }
        dia.setTitle( title );
        
        WindowManager.getInstance().showModalDialog(dia);

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
                    mon= DasProgressPanel.createFramed(parent, "reloading data at native resolution");
                    ds= dss.getDataSet( mon );
                    if ( mon.isCancelled() ) {
                        parent.setStatus( "export data cancelled" );
                        return;
                    }
                    if ( !mon.isFinished() ) mon.finished(); // in cause the getDataSet method fails to call finished.
                }
            }

            mon= DasProgressPanel.createFramed( parent, "formatting data" );
            switch (dscontrol) {
                case "plotElementTrim":
                    {
                        // see also CreatePngWalk's isDataVisible
                        DasPlot p= pe.getController().getDasPlot();
                        DatumRange xbounds= p.getXAxis().getDatumRange();
                        QDataSet dsout=  pe.getController().getDataSet();
                        //dsout= DataSetOps.processDataSet( pe.getComponent(), dsout, DasProgressPanel.createFramed(parent, "process TSB timeseries at native resolution") );
                        long t0= System.currentTimeMillis();
                        if ( SemanticOps.isRank2Waveform(dsout) ) {
                            dsout= DataSetOps.flattenWaveform(dsout);
                            //dsout= ArrayDataSet.copy( dsout );
                        }       
                        dsout= SemanticOps.trim( dsout, xbounds, null );
                        format.formatData( uriOut, dsout, mon );
                        logger.log( Level.FINE, "format in {0} millis", (System.currentTimeMillis()-t0));
                        break;
                    }
                case "plotElement":
                    {
                        long t0= System.currentTimeMillis();
                        QDataSet dsout=  pe.getController().getDataSet();
                        format.formatData( uriOut, dsout, mon );
                        logger.log( Level.FINE, "format in {0} millis", (System.currentTimeMillis()-t0));
                        break;
                    }
                default:
                    {
                        long t0= System.currentTimeMillis();
                        format.formatData( uriOut, ds, mon );
                        logger.log( Level.FINE, "format in {0} millis", (System.currentTimeMillis()-t0));
                        break;
                    }
            }
            parent.setStatus("Wrote " + org.autoplot.datasource.DataSourceUtil.unescape(uriOut) );
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
        if ( mon!=null && !mon.isFinished() ) mon.finished(); // in case they forgot the tidy up.
    }

    /**
     * provide action to allow users to export a dataset to formats that support this.
     * @param dom
     * @return 
     */
    Action getDumpDataAction( final Application dom ) {
        return new AbstractAction("Export Data...") {
            @Override
            public void actionPerformed( ActionEvent e ) {
                org.das2.util.LoggerManager.logGuiEvent(e);
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
                Preferences prefs= AutoplotSettings.settings().getPreferences(AutoplotUI.class);
                String currentFileString = prefs.get("ExportDataCurrentFile", "");
                String currentExtString = prefs.get("ExportDataCurrentExt", ".txt");
                if ( !currentExtString.equals("") ) {
                    edp.getFormatDL().setSelectedItem(currentExtString);
                }
                if ( !currentFileString.equals("") ) {
                    URISplit split= URISplit.parse(currentFileString);
                    edp.getFilenameTF().setText(split.file);
                    edp.getFormatDL().setSelectedItem( "." + split.ext );
                    if ( currentFileString.contains("/") && ( currentFileString.startsWith("file:") || currentFileString.startsWith("/") ) ) {
                        edp.setFile( currentFileString );
                        if ( split.params!=null && edp.getDataSourceFormatEditorPanel()!=null ) {
                            edp.getDataSourceFormatEditorPanel().setURI(currentFileString);
                        }
                    }
                }

                if ( dsf.getController().getTsb()!=null ) {
                    edp.setTsb(true);
                }
                
                if ( AutoplotUtil.showConfirmDialog2( parent, edp, "Export Data", JOptionPane.OK_CANCEL_OPTION )==JOptionPane.OK_OPTION ) {
                     try {
                        String name= edp.getFilenameTF().getText();
                        if ( name.startsWith(" ") || name.startsWith("\\t") ) {
                            AutoplotUtil.showMessageDialog( parent, "<html>The file name cannot start with space or tab<br>\""+name+"\"", "Bad filename", JOptionPane.WARNING_MESSAGE );
                            return;
                        }
                        String ext = edp.getExtension();
                        String file;
                        try {
                            file= edp.getFilename();
                        } catch ( IllegalArgumentException ex ) {
                            JOptionPane.showMessageDialog(parent, ex.getMessage() );
                            return;
                        }

                        if (ext == null) {
                            ext = "";
                        }

                        URISplit split= URISplit.parse(file);
                        
                        final DataSourceFormat format = DataSourceRegistry.getInstance().getFormatByExt(ext); //OKAY
                        if (format == null) {
                            JOptionPane.showMessageDialog(parent, "No formatter for extension: " + ext);
                            return;
                        }
                        
                        String s= URISplit.format(split);

                        // this can also support aggregations.
                        final DataSourceFormat formata= DataSetURI.getDataSourceFormat( new URI(s) );
                        
                        DataSourceFormatEditorPanel opts= edp.getDataSourceFormatEditorPanel();
                        if ( opts!=null ) { // See PlotElementController.java line 3141, where this code is repeated.
                            URISplit splitopts= URISplit.parse(opts.getURI());
                            if ( splitopts.params!=null && splitopts.params.length()==0 ) {
                                splitopts.params= null;
                            }
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
                                    doDumpData( fds,dsf,pe,formata,uriOut,formatControl );
                                } catch ( IOException ex ) {
                                    parent.applicationModel.getExceptionHandler().handle(ex);
                                }
                            }
                        };

                        new Thread( run ).start();

                    } catch ( IllegalArgumentException | HeadlessException | URISyntaxException ex) {
                        parent.applicationModel.getExceptionHandler().handle(ex);

                    } catch (RuntimeException ex ) {
                        parent.applicationModel.getExceptionHandler().handleUncaught(ex);
                    }

                }
            }
        };
    }

    /**
     * provide action to allow users to export a dataset to formats that support this.
     * @param dom
     * @return 
     */
    Action getDumpAllDataAction( final Application dom ) {
        return new AbstractAction("Export All Data...") {
            @Override
            public void actionPerformed( ActionEvent e ) {
                org.das2.util.LoggerManager.logGuiEvent(e);
                final ExportDataBundle edw= new ExportDataBundle();

                ArrayList<String> uris= new ArrayList<>();
                for ( DataSourceFilter dsf: dom.getDataSourceFilters() ) {
                    uris.add(dsf.getUri());
                }
                
                edw.setUris( uris.toArray(new String[uris.size()]) );
                
                if ( AutoplotUtil.showConfirmDialog( parent, edw, "Export All", JOptionPane.OK_CANCEL_OPTION )==JOptionPane.OK_OPTION ) {
                    try {
                        ScriptContext.formatDataSet( edw.getDataSet(), edw.getUri() );
                        parent.setStatus("Wrote " + org.autoplot.datasource.DataSourceUtil.unescape(edw.getUri()) );
                    } catch (Exception ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                }
            }
        };
    }
    
    
    public Action createNewDOMAction() {
        return new AbstractAction("Reset Window...") {
            @Override
            public void actionPerformed( ActionEvent e ) {
                org.das2.util.LoggerManager.logGuiEvent(e);
                if ( parent.stateSupport.isDirty() ) {
                    String msg= "The application has been modified.  Do you want to save your changes?";
                    int result= JOptionPane.showConfirmDialog( parent, msg, "Application Modified", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE );
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
                        parent.resizeForCanvasSize(parent.dom.getOptions().getWidth(), parent.dom.getOptions().getHeight()); 
                        parent.dom.getController().reset();
                        parent.undoRedoSupport.resetHistory();
                        parent.applicationModel.setVapFile(null);
                        parent.stateSupport.close();
                        parent.tickleTimer.tickle("resetWindow801");
                        if ( parent.isExpertMode() ) {
                            parent.setEditorCard(AutoplotUI.CARD_DATA_SET_SELECTOR);
                        }
                        Runnable run2= new Runnable() {
                            @Override
                            public void run() {
                                parent.resizeForDefaultCanvasSize();
                            }
                        };
                        SwingUtilities.invokeLater(run2);

                    }
                };

                // https://sourceforge.net/tracker/?func=detail&aid=3557440&group_id=199733&atid=970682
                new Thread(run).start(); 
                
            }
        };
    }

    /**
     * create a new AutoplotUI
     * @return the new ApplicationModel
     */
    ApplicationModel newApplication() {
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
                java.awt.Point p= view.getLocation();
                p.translate( 20,20 );
                view.setLocation( p );
                view.setVisible(true);
                view.setMessage("ready");
                AutoplotUI.checkStatusLoop(view);
            }
        };
        try {
            if ( SwingUtilities.isEventDispatchThread() ) {
                run.run();
            } else {
                SwingUtilities.invokeAndWait(run);
            }
        } catch ( InterruptedException | InvocationTargetException ex ) {
            throw new RuntimeException(ex);
        }
        return model;
    }

    /**
     * clone the application into a new AutoplotUI
     * @return the new application
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
                view.setMessage("ready");
                AutoplotUI.checkStatusLoop(view);
                Canvas size= parent.applicationModel.dom.getCanvases(0);
                int extraWidth= GuiSupport.this.parent.getWindowExtraWidth();
                int extraHeight= GuiSupport.this.parent.getWindowExtraHeight();
                view.resizeForCanvasSize( size.getWidth(), size.getHeight(), extraWidth, extraHeight  );
                }
            };
        try {
            if ( SwingUtilities.isEventDispatchThread() ) {
                run.run();
            } else {
                SwingUtilities.invokeAndWait(run);
            }
        } catch ( InterruptedException | InvocationTargetException ex ) {
            throw new RuntimeException(ex);
        }
        model.dom.syncTo( parent.applicationModel.dom );
        DomUtil.copyOverInternalData( parent.applicationModel.dom, model.dom );
        return model;
    }
    
    public Action createNewApplicationAction() {
        return new AbstractAction("New Window") {
            @Override
            public void actionPerformed( ActionEvent e ) {
                org.das2.util.LoggerManager.logGuiEvent(e);
                newApplication();
            }
        };
    }

    public Action createCloneApplicationAction() {
        return new AbstractAction("Duplicate in New Window") {
            @Override
            public void actionPerformed( ActionEvent e ) {
                org.das2.util.LoggerManager.logGuiEvent(e);        
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
        tt.put( "Bounds", RenderType.bounds );
        tt.put( "Contour Plot", RenderType.contour);
        tt.put( "Stacked Histogram", RenderType.stackedHistogram);
        return tt;
    }

    public static JMenu createEZAccessMenu(final Plot plot) {

        JMenu result = new JMenu("Plot Element Type");
        result.setToolTipText("Plot Element Type was formerly the Plot Style menu");
        JMenuItem mi;

        result.setName(plot.getId()+"_ezaccessmenu");
        
        Map<String,RenderType> tt= getRenderTypeForString();

        //tt.put( "Contour Plot", RenderType.contour );  //this has issues, hide for now.
        //tt.remove( "Pitch Angle Distribution" ); // this requires a specific scheme of data, hide for now (rte_1765139930_20130112_134531)

        for ( Entry<String,RenderType> ee: tt.entrySet() ) {
            final Entry<String,RenderType> fee= ee;
            mi= new JCheckBoxMenuItem(new AbstractAction(fee.getKey()) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    org.das2.util.LoggerManager.logGuiEvent(e);
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
                org.das2.util.LoggerManager.logGuiEvent(e);
                parent.applicationModel.resetZoom();
            }
        });
        thisPanel.getActionMap().put("INCREASE_FONT_SIZE", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);
                parent.applicationModel.increaseFontSize();
            }
        });
        thisPanel.getActionMap().put("DECREASE_FONT_SIZE", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);
                parent.applicationModel.decreaseFontSize();
            }
        });

        thisPanel.getActionMap().put("NEXT_PLOT_ELEMENT", new AbstractAction() {
            @Override
            public void actionPerformed( ActionEvent e ) {
                org.das2.util.LoggerManager.logGuiEvent(e);
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
                org.das2.util.LoggerManager.logGuiEvent(e);
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
                org.das2.util.LoggerManager.logGuiEvent(e);
                parent.stateSupport.createSaveAction().actionPerformed(e);
            }
        });
        thisPanel.getActionMap().put("RELOAD_ALL", new AbstractAction() {
            @Override
            public void actionPerformed( ActionEvent e ) {
                org.das2.util.LoggerManager.logGuiEvent(e);
                RequestProcessor.invokeLater( new Runnable() { 
                    @Override
                    public void run() {
                        AutoplotUtil.reloadAll(parent.dom);
                    }
                } );
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
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, tk.getMenuShortcutKeyMask() ), "RELOAD_ALL");
        thisPanel.setInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW, map);

    }

    protected void exportRecent(Component c) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter( new FileNameExtensionFilter("bookmarks files", "xml" ) );
        int r = chooser.showSaveDialog(c);
        if (r == JFileChooser.APPROVE_OPTION) {
            try {
                File f = chooser.getSelectedFile();
                if (!f.toString().endsWith(".xml")) {
                    f = new File(f.toString() + ".xml");
                }
                try (FileOutputStream out = new FileOutputStream(f)) {
                    Bookmark.formatBooks(out,parent.applicationModel.getRecent());
                }
            } catch (IOException e) {
                logger.log( Level.WARNING, e.getMessage(), e );
            }
        }
    }

    /**
     * get simple filter based on extension for use with JFileChooser.
     * @param description descriptions, like "png image file"
     * @param ext file extension, like ".png"
     * @return the FileFilter 
     */
    public static FileFilter getFileNameExtensionFilter(final String description, final String ext) {
        return new FileFilter() {
            @Override
            public boolean accept(File f) {
                String s= f.getName();
                return f.isDirectory() || s.endsWith(ext);
            }
            @Override
            public String getDescription() {
                return description;
            }
        };
    }

    private static File currentFile;

    /**
     * return an action which will send the canvas to the printer. 
     * @param app app containing the canvas
     * @param parent the focus dialog
     * @param ext extention like "svg" or "pdf" or "png"
     * @return 
     */
    public static Action getPrintAction( final Application app, final Component parent,final String ext) {
        return new AbstractAction("Print as "+ext.toUpperCase()) {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                final JPanel decor;
                final DasCanvas canvas = app.getController().getDasCanvas();
                final JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Print to "+ext.toUpperCase());
                fileChooser.setFileFilter( new FileNameExtensionFilter( ext + " files", ext )) ;
                Preferences prefs = AutoplotSettings.settings().getPreferences(DasCanvas.class);
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
                                switch (ext) {
                                    case "png":
                                        canvas.writeToPng(ffname);
                                        break;
                                    case "pdf":
                                        try ( FileOutputStream out = new FileOutputStream(ffname) ){
                                            
                                            PdfGraphicsOutput go = new PdfGraphicsOutput();

                                            PdfOptionsPanel pdecor= (PdfOptionsPanel)decor; // findbugs OKAY
                                            go.setGraphicsShapes( pdecor.fontsAsShapesCB.isSelected() );
                                            go.setOutputStream(out);
                                            if ( pdecor.manualWidthCB.isSelected() ) {
                                                double mant= Double.parseDouble(pdecor.widthTF.getText()); //TODO>: FormattedTextField
                                                String units= (String)pdecor.unitsComboBox.getSelectedItem();
                                                switch (units) {
                                                    case "inches":
                                                        mant= mant * 72;
                                                        break;
                                                    case "centimeters":
                                                        mant= mant * 72 / 2.54;
                                                        break;
                                                    default:
                                                        throw new IllegalArgumentException("implementation error: "+units);
                                                }
                                                // mant is the number of pixels width.
                                                
                                                int ppi= (int)( canvas.getWidth() * 72 / mant );
                                                go.setPixelsPerInch( ppi );
                                                go.setSize( canvas.getWidth(), canvas.getHeight() );
                                            } else if ( pdecor.getPixelsPerInch().length()>0 ) {
                                                int ppi= Integer.parseInt(pdecor.getPixelsPerInch());
                                                go.setPixelsPerInch(ppi);
                                                go.setSize( canvas.getWidth(), canvas.getHeight() );
                                            } else {
                                                go.setSize( canvas.getWidth(), canvas.getHeight() );
                                            }
                                            go.start();
                                            canvas.print(go.getGraphics());
                                            go.finish();
                                        }   
                                        break;
                                    case "svg":
                                        canvas.writeToSVG(ffname);
                                        break;
                                    default:
                                        throw new IllegalArgumentException("implementation error: "+ext);
                                }
                                app.getController().setStatus("wrote to " + ffname);
                            } catch (java.io.IOException ioe) {
                                DasApplication.getDefaultApplication().getExceptionHandler().handle(ioe);
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
            if ( vap.contains("?") ) {
                int i= vap.indexOf('?');
                vap= vap.substring(0,i);
            }
            d.setVap(vap);
            if ( d.showDialog( SwingUtilities.getWindowAncestor( dom.getController().getDasCanvas() ) )==JOptionPane.OK_OPTION ) {
                String lock= "merging vaps";
                dom.getController().registerPendingChange( d,lock );
                try {
                    dom.getController().performingChange( d, lock );
                    List<String> uris= d.getSelectedURIs();
                    for ( String uri: uris ) {
                        dom.getController().doplot( plot, pelement, uri );
                        pelement= null; //otherwise we'd clobber last dataset.
                    }
                } finally {
                    dom.getController().changePerformed( d, lock );
                }
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


    private static void handleAddElementDialog( final AddPlotElementDialog dia, final Application dom, final ApplicationModel applicationModel) {
        Plot plot = null;
        PlotElement pelement = null;
        int modifiers = dia.getModifiers();
        if ( (modifiers & KeyEvent.CTRL_MASK) == KeyEvent.CTRL_MASK && (modifiers & KeyEvent.SHIFT_MASK) == KeyEvent.SHIFT_MASK ) {
            // reserve this for plot above, which we'll add soon.
            plot = dom.getController().addPlot(LayoutConstants.ABOVE);
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
        final Plot lplot= plot;
        final PlotElement lpelement= pelement;

        final String[] uris;
        final String[] filters;
        switch (dia.getDepCount()) {
            case 0:
                uris= new String[] {  dia.getPrimaryDataSetSelector().getValue() };
                filters= new String[] { dia.getPrimaryFilters() };
            break;  
            case 1:
                uris= new String[] {  dia.getSecondaryDataSetSelector().getValue(), dia.getPrimaryDataSetSelector().getValue() };
                filters= new String[] { dia.getSecondaryFilters(), dia.getPrimaryFilters() };
            break;  
            case 2:
                uris= new String[] {  dia.getSecondaryDataSetSelector().getValue(), dia.getTertiaryDataSetSelector().getValue(), dia.getPrimaryDataSetSelector().getValue() };
                filters= new String[] { dia.getSecondaryFilters(), dia.getTertiaryFilters(), dia.getPrimaryFilters() };
            break;  
            default:
                throw new IllegalArgumentException("this can't happen");
        }                        
        
        int depCount= dia.getDepCount();
        
        final String lock= "plotWithSlice";
        dom.getController().registerPendingChange( dom, lock );
        
        try {
            Runnable run;
            switch (depCount) {
                case 0:
                    applicationModel.addRecent(uris[0]);
                    String val= uris[0];
                    if ( val.endsWith(".vap") ) {
                        try {
                            mergeVap(dom,lplot, lpelement, val);
                        } finally {
                            dom.getController().changePerformed( dom, lock );
                        }
                    } else {
                        final String lval= val;
                        run= new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    String uri= lval;
                                    DataSourceFactory factory = DataSetURI.getDataSourceFactory( DataSetURI.getURI(uri), new NullProgressMonitor() );
                                    if ( factory==null ) {
                                        if ( uri.startsWith("vap+internal:") ) { // allow testing.
                                            DataSourceFilter dsf= dom.getController().addDataSourceFilter();
                                            dsf.setUri( uri );
                                            dom.getController().addPlotElement( lplot, dsf );
                                            return;
                                        } else {
                                            throw new IllegalArgumentException("unable to resolve URI: "+uri);
                                        }
                                    }
                                    List<String> problems= new ArrayList<>();
                                    while ( factory.reject( uri, problems, new NullProgressMonitor() ) ) {
                                        dia.setTitle("Add Plot, URI was rejected...");
                                        
                                        dia.setMessagesLabelText("The URI was rejected.  Verify that it is correct using the inspect button (right).");
                                        
                                        WindowManager.getInstance().showModalDialog(dia);

                                        if ( dia.isCancelled() ) {
                                            return;
                                        }
                                        String val= dia.getPrimaryDataSetSelector().getValue();
                                        uri= val;
                                    }
                                    PlotElement pelement= dom.getController().doplot(lplot, lpelement, lval );
                                    DataSourceFilter dsf= (DataSourceFilter)DomUtil.getElementById( dom, pelement.getDataSourceFilterId() );
                                    if ( dia.getPrimaryFilters().length()>0 ) dsf.setFilters(dia.getPrimaryFilters());
   
                                } catch ( IOException | URISyntaxException ex ) {
                                    applicationModel.showMessage( ex.getMessage(), "Illegal Argument", JOptionPane.ERROR_MESSAGE );
                                } finally {
                                    dom.getController().changePerformed( dom, lock );
                                }
                            }
                        };
                        //new Thread(run).start(); //
                        run.run();
                    }
                    break;
                case 1:
                    applicationModel.addRecent(uris[0]);
                    applicationModel.addRecent(uris[1]);
                    run= new Runnable() {
                        @Override
                        public void run() {
                            try {
                                dom.getController().performingChange( dom, lock );
                                PlotElement pele= dom.getController().doplot(lplot, lpelement, uris[0], uris[1] );
                                DataSourceFilter dsf= (DataSourceFilter)DomUtil.getElementById( dom, pele.getDataSourceFilterId() );
                                List<DataSourceFilter> dsfs= DomUtil.getParentsFor( dom, dsf.getUri() );
                                if ( dsfs.size()==2 && dsfs.get(0)!=null && dsfs.get(1)!=null ) {
                                    if ( filters[0].length()>0 ) dsfs.get(0).setFilters( filters[0] );
                                    if ( filters[1].length()>0 ) dsfs.get(1).setFilters( filters[1] );
                                }                    
                                dom.getController().setFocusUri( dom.getController().getDataSourceFilterFor(pele).getUri());
                            } finally {
                                dom.getController().changePerformed( dom, lock );
                            }
                        }
                    };
                    new Thread(run).start();
                    break;
                case 2:
                    applicationModel.addRecent(uris[0]);
                    applicationModel.addRecent(uris[1]);
                    applicationModel.addRecent(uris[2]);
                    run= new Runnable() {
                        @Override
                        public void run() {            
                            try {
                                dom.getController().performingChange( dom, lock );
                                PlotElement pele= dom.getController().doplot(lplot, lpelement, uris[0], uris[1], uris[2] );
                                DataSourceFilter dsf= (DataSourceFilter)DomUtil.getElementById( dom, pele.getDataSourceFilterId() );
                                List<DataSourceFilter> dsfs= DomUtil.getParentsFor( dom, dsf.getUri() );
                                if ( dsfs.size()==3 && dsfs.get(0)!=null && dsfs.get(1)!=null && dsfs.get(2)!=null ) {
                                    if ( filters[0].length()>0 ) dsfs.get(0).setFilters( filters[0] );
                                    if ( filters[1].length()>0 ) dsfs.get(1).setFilters( filters[1] );
                                    if ( filters[2].length()>0 ) dsfs.get(2).setFilters( filters[2] );
                                } 
                                dom.getController().setFocusUri( dom.getController().getDataSourceFilterFor(pele).getUri());
                            } finally {
                                dom.getController().changePerformed( dom, lock );
                            }
                                
                        }
                    };
                    new Thread(run).start();
                    break;
            //if (pelement == null) {
            //    pelement = dom.getController().addPlotElement(plot, null);
            //}
                case -1:
                    dom.getController().changePerformed( dom, lock );
                    break;
                default:
                    dom.getController().changePerformed( dom, lock );
                    break;
            }
        } catch ( IllegalArgumentException ex ) { // TODO: the IllegalArgumentException is wrapped in a RuntimeException, I don't know why.  I should have MalformedURIException
            applicationModel.showMessage( ex.getMessage(), "Illegal Argument", JOptionPane.ERROR_MESSAGE );
        } 
    }

    /**
     * support for binding two plot axes.
     * Set log first since we might tweak range accordingly.
     * @param dstPlot
     * @param plot
     * @param axis
     * @param props null is old range and log.  list of properties to bind
     * @throws java.lang.IllegalArgumentException
     */
    private static void bindToPlotPeer( final ApplicationController controller, Plot dstPlot, Plot plot, Axis axis, String[] props) throws IllegalArgumentException {
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
        if ( props==null ) {
            axis.setLog( targetAxis.isLog() );
            axis.setRange( targetAxis.getRange() );
            props= new String[] { Axis.PROP_LOG, Axis.PROP_RANGE };
        }
        for ( String p : props ) {
            controller.bind(targetAxis, p, axis, p ); 
        }
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
                org.das2.util.LoggerManager.logGuiEvent(e);                
                PropertyEditor pp = new PropertyEditor(axis);
                pp.showDialog(dasAxis.getCanvas());
            }
        });
        mouseAdapter.addMenuItem(item);
        expertMenuItems.add(item);
        
        mouseAdapter.addMenuItem(new JSeparator());

        item= new JMenuItem( new AbstractAction("Reset Zoom" ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                if ( plot.getZaxis()==axis ) {
                    AutoplotUtil.resetZoomZ( controller.getApplication(), plot );
                } else {
                    if ( axis==plot.getXaxis() ) {
                        AutoplotUtil.resetZoomX( controller.getApplication(), plot );
                    } else {
                        AutoplotUtil.resetZoomY( controller.getApplication(), plot );
                    }
                }
            }            
        } );
        mouseAdapter.addMenuItem(item);
                
        if (axis == plot.getXaxis()) {
            JMenu addPlotMenu = new JMenu("Add Plot");
            mouseAdapter.addMenuItem(addPlotMenu);

            item = new JMenuItem(new AbstractAction("Bound Plot Below") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    org.das2.util.LoggerManager.logGuiEvent(e);                    
                    controller.copyPlot(plot, true, false, true);
                }
            });
            item.setToolTipText("add a new plot below.  The plot's x axis will be bound to this plot's x axis");
            addPlotMenu.add(item);
            expertMenuItems.add( addPlotMenu );
        }

        if (axis == plot.getZaxis()) {
            JMenuItem addPlotMenu = new JMenuItem( new AbstractAction("Set Color Table...") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    org.das2.util.LoggerManager.logGuiEvent(e);
                    EnumerationEditor edit = new EnumerationEditor();
                    DasColorBar.Type type0= ((DasColorBar)dasAxis).getType();
                    edit.setValue( type0 );
                    edit.addPropertyChangeListener( new PropertyChangeListener() {
                        @Override
                        public void propertyChange(PropertyChangeEvent evt) {
                            ((DasColorBar)dasAxis).setType((DasColorBar.Type) edit.getValue());
                        }
                    });
                    edit.setValue( ((DasColorBar)dasAxis).getType() );
                    if ( JOptionPane.OK_OPTION
                            !=JOptionPane.showConfirmDialog( dasAxis.getCanvas(), edit.getCustomEditor(), "Set Color Table", JOptionPane.OK_CANCEL_OPTION ) ) {
                        ((DasColorBar)dasAxis).setType(type0);
                    }
                }
            });
            item.setToolTipText("reset the colorbar");
            mouseAdapter.addMenuItem(addPlotMenu);
            expertMenuItems.add( addPlotMenu );
        }
        
        JMenu bindingMenu = new JMenu("Binding");

        mouseAdapter.addMenuItem(bindingMenu);

        if (axis == plot.getXaxis()) {
            item = new JMenuItem(new AbstractAction("Add Binding to Application Time Range") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    org.das2.util.LoggerManager.logGuiEvent(e);                
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

        item = new JMenuItem(new AbstractAction("Bind Range to Plot Above") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                Plot dstPlot = controller.getPlotAbove(plot);
                if (dstPlot == null) {
                    controller.setStatus("warning: no plot above");
                } else {
                    bindToPlotPeer(controller,dstPlot, plot, axis, null );
                }
            }
        });
        bindingMenu.add(item);
        item = new JMenuItem(new AbstractAction("Bind Range to Plot Below") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                Plot dstPlot = controller.getPlotBelow(plot);
                if (dstPlot == null) {
                    controller.setStatus("warning: no plot below");
                } else {
                    bindToPlotPeer(controller,dstPlot, plot, axis, null );
                }
            }
        });
        bindingMenu.add(item);
        item = new JMenuItem(new AbstractAction("Bind Range to Plot to the Right") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                Plot dstPlot = controller.getNextPlotHoriz(plot,LayoutConstants.RIGHT);
                if (dstPlot == null) {
                    controller.setStatus("warning: no plot to the right");
                } else {
                    bindToPlotPeer(controller,dstPlot, plot, axis, null );
                }
            }
        });
        bindingMenu.add(item);
        item = new JMenuItem(new AbstractAction("Bind Range to Plot to the Left") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                Plot dstPlot = controller.getNextPlotHoriz(plot,LayoutConstants.LEFT);
                if (dstPlot == null) {
                    controller.setStatus("warning: no plot to the left");
                } else {
                    bindToPlotPeer(controller,dstPlot, plot, axis, null );
                }
            }
        });
        bindingMenu.add(item);

        item = new JMenuItem(new AbstractAction("Bind Scale to Plot Above") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                Plot dstPlot = controller.getPlotAbove(plot);
                if (dstPlot == null) {
                    controller.setStatus("warning: no plot above");
                } else {
                    bindToPlotPeer(controller,dstPlot, plot, axis, new String[] { Axis.PROP_LOG, Axis.PROP_SCALE });
                }
            }
        });
        bindingMenu.add(item);
        
        item = new JMenuItem(new AbstractAction("Bind Scale to Plot Below") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                Plot dstPlot = controller.getPlotBelow(plot);
                if (dstPlot == null) {
                    controller.setStatus("warning: no plot below");
                } else {
                    bindToPlotPeer(controller,dstPlot, plot, axis, new String[] { Axis.PROP_LOG, Axis.PROP_SCALE });
                }
            }
        });
        bindingMenu.add(item);

        item = new JMenuItem(new AbstractAction("Bind Scale to Opposite Axis") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                controller.bind( plot.getXaxis(), Axis.PROP_LOG, plot.getYaxis(), Axis.PROP_LOG );
                controller.bind( plot.getXaxis(), Axis.PROP_SCALE, plot.getYaxis(), Axis.PROP_SCALE );
            }
        });
        bindingMenu.add(item);
        
        item = new JMenuItem(new AbstractAction("Remove Bindings") {
            @Override            
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
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
                org.das2.util.LoggerManager.logGuiEvent(e);                
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
                org.das2.util.LoggerManager.logGuiEvent(e);                
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
                org.das2.util.LoggerManager.logGuiEvent(e);                
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
                    org.das2.util.LoggerManager.logGuiEvent(e);                
                    String uri= plot.getTicksURI();
                    if ( uri.startsWith("class:org.autoplot.tca.UriTcaSource:") ) {
                        uri= uri.substring("class:org.autoplot.tca.UriTcaSource:".length());
                    }
                    if ( UnitsUtil.isTimeLocation( axis.getController().getDasAxis().getDatumRange().getUnits() ) ) {
                        String nuri= DataSetURI.resetUriTsbTime(uri,axis.getController().getDasAxis().getDatumRange());
                        if ( nuri!=null && !nuri.equals(uri) ) {
                            uri= nuri;
                        }
                    }
                    TcaElementDialog dia= new TcaElementDialog( (JFrame)SwingUtilities.getWindowAncestor( controller.getDasCanvas().getParent()), true );
                    dia.getPrimaryDataSetSelector().setValue(uri);
                    dia.getPrimaryDataSetSelector().setTimeRange( axis.getController().getDasAxis().getDatumRange() );
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
        
        item= new JMenuItem( new AbstractAction("Reset axis units to..." ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                Units u= dasAxis.getUnits();
                Units[] uu= u.getConvertibleUnits();
                List<Units> uus= new ArrayList<>( Arrays.asList(uu) );
                
                // offer to change to a unit which is convertible to the data.
                List<PlotElement> pes= DomUtil.getPlotElementsFor( controller.getApplication(), plot );
                for ( PlotElement pe : pes ) {
                    Units u1;
                    if ( dasAxis.isHorizontal() ) {
                        u1= pe.getPlotDefaults().getXaxis().getRange().getUnits();
                    } else if ( dasPlot.getYAxis()==dasAxis ) {
                        u1= pe.getPlotDefaults().getYaxis().getRange().getUnits();
                    } else {
                        u1= pe.getPlotDefaults().getZaxis().getRange().getUnits();
                    }
                    if ( !uus.contains(u1) && !u1.equals(u) 
                            && UnitsUtil.isIntervalOrRatioMeasurement(u1) ) uus.add(u1);
                }
                
                uu= uus.toArray(new Units[uus.size()]);
                
                Component p= (JFrame)SwingUtilities.getWindowAncestor( controller.getDasCanvas().getParent());
                
                if ( uu.length<1 ) {
                    JOptionPane.showMessageDialog( p, "No conversions found from \""+u+"\"");
                } else {
                    JPanel p1= new JPanel();
                    p1.setLayout( new BoxLayout(p1,BoxLayout.Y_AXIS) );
                    p1.setAlignmentY(0.f);
                    p1.setAlignmentX(0.f);
                    JLabel l= new JLabel("Axis units are \""+u+"\"");
                    l.setAlignmentX(0.f);
                    p1.add(l);
                    l= new JLabel("Reset axis units to:");
                    l.setAlignmentX(0.f);
                    p1.add(l);
                    JComboBox cb= new JComboBox(uu);
                    cb.setSelectedItem(u);
                    cb.setAlignmentX(0.f);
                    p1.add(cb);
                    if ( JOptionPane.OK_OPTION==JOptionPane.showConfirmDialog( p,
                                p1,
                                "Reset axis units", JOptionPane.OK_CANCEL_OPTION ) ) {
                        Units nu= (Units)cb.getSelectedItem();
                        if ( nu.isConvertibleTo(u) ) {
                            axis.getController().resetAxisUnits(nu);
                        } else {
                            DatumRange oldRange= dasAxis.getDatumRange();
                            DatumRange newRange= new DatumRange( oldRange.min().value(), oldRange.max().value(), nu );
                            axis.getController().resetAxisRange(newRange);
                        }
                    }
                }
            }            
        } );
        mouseAdapter.addMenuItem(item);
        expertMenuItems.add(item);
            
        List<JMenuItem> expertMenuItemsList= new ArrayList( Arrays.asList( plotController.getExpertMenuItems() ) );
        expertMenuItemsList.addAll(expertMenuItems);

        plotController.setExpertMenuItems( expertMenuItemsList.toArray(new JMenuItem[expertMenuItemsList.size()] )  );

    }
    
    private static boolean isStringVap( String s ) {
        return s.startsWith("<?xml");
    }
            
    /**
     * replace the plot with the plot stored in the clipboard.  This plot
     * in the clipboard is simply a one-plot .vap file.  
     * 
     * @param app component parent for dialogs.
     * @param controller the application controller where we 
     * @param newP the plotElements are added to this plot 
     * @throws HeadlessException 
     * @see #insertStringVapIntoPlot(java.awt.Component, org.autoplot.dom.ApplicationController, org.autoplot.dom.Plot, java.lang.String) 
     */
    public static void pasteClipboardIntoPlot( final Component app, 
            final ApplicationController controller, 
            final Plot newP ) throws HeadlessException {
        try {
            Clipboard clpbrd= Toolkit.getDefaultToolkit().getSystemClipboard();
            final String thevap;
            if ( clpbrd.isDataFlavorAvailable(DataFlavor.stringFlavor) ) {
                thevap= (String) clpbrd.getData(DataFlavor.stringFlavor);
                if ( !isStringVap(thevap) ) {
                    JOptionPane.showMessageDialog(app,
                            "Use \"Edit Plot\"->\"Copy Plot to Clipboard\"<br>(Pasted content should be XML.)");
                    return;
                }
            } else {
                JOptionPane.showMessageDialog(app,
                        "Use \"Edit Plot\"->\"Copy Plot to Clipboard\"");
                return;
            }
            
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    try {
                        insertStringVapIntoPlot( app, controller, newP, thevap );
                    } catch ( IllegalArgumentException ex ) {
                        JOptionPane.showMessageDialog(app,"Use \"Edit Plot\"->\"Copy Plot to Clipboard\"<br>(Pasted content is not XML containing a plot.)");
                    } catch (HeadlessException | IOException ex) {
                        Logger.getLogger(GuiSupport.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            };
            
            SwingUtilities.invokeLater(run);

        } catch (UnsupportedFlavorException | IOException ex) {
            Logger.getLogger(GuiSupport.class.getName()).log(Level.SEVERE, null, ex);
        }
    }  

    /**
     * make the plot <code>newP</code> reflect the state in String 
     * <code>theVap</code>.  This should be called from the event thread.
     * @param app GUI component used as the client for the lock.
     * @param controller
     * @param targetPlot the plot to insert plot elements, which will be recycled to implement the plot in the .vap
     * @param theVap string containing a single-plot vap or multiple plots which share a row and column, and inset.
     * @throws HeadlessException
     * @throws IOException 
     * @throws IllegalArgumentException if the string is not a vap.
     * @see DomUtil#getPlotAsString(org.autoplot.dom.Application, org.autoplot.dom.Plot) 
     */
    private static void insertStringVapIntoPlot( 
            Component app, 
            ApplicationController controller, 
            Plot targetPlot, 
            String theVap ) throws HeadlessException, IOException, IllegalArgumentException {
        Application state;
        
        state = (Application)StatePersistence.restoreState(new ByteArrayInputStream(theVap.getBytes()));
        
        Object lockObject= "pasteClipboard";
        
        controller.registerPendingChange( app, lockObject );
        DomLock lock= controller.mutatorLock();
        
        try {
            lock.lock("pasting plot");
            controller.performingChange( app, lockObject );
            
            List<PlotElement> pes= controller.getPlotElementsFor(targetPlot);
            pes.forEach((pe) -> {
                controller.deletePlotElement(pe);
            });

            Plot srcPlot= state.getPlots(0);
            List<String> exclude= Arrays.asList(Plot.PROP_ID,
                    Axis.PROP_DRAWTICKLABELS, Axis.PROP_VISIBLE, Axis.PROP_OPPOSITE );
            if ( targetPlot.getXaxis().getRange().getUnits().isConvertibleTo( srcPlot.getXaxis().getRange().getUnits() ) ) {
                if ( !targetPlot.getXaxis().isAutoRange() ) { // TODO: or if it is bound to the application timerange and others are controlling it.
                    srcPlot.getXaxis().setRange(targetPlot.getXaxis().getRange());
                    srcPlot.getXaxis().setLog(targetPlot.getXaxis().isLog());
                }
            }
            targetPlot.getXaxis().syncTo( srcPlot.getXaxis(), exclude );
            
            if ( targetPlot.getYaxis().getRange().getUnits().isConvertibleTo( srcPlot.getYaxis().getRange().getUnits() ) ) {
                if ( !targetPlot.getYaxis().isAutoRange() ) {
                    srcPlot.getYaxis().setRange(targetPlot.getYaxis().getRange());
                    srcPlot.getYaxis().setLog(targetPlot.getYaxis().isLog());
                }
            }
            targetPlot.getYaxis().syncTo( srcPlot.getYaxis(), exclude );
            
            exclude= Arrays.asList(Plot.PROP_ID,
                    Plot.PROP_ROWID,Plot.PROP_COLUMNID,
                    Plot.PROP_TICKS_URI,
                    Plot.PROP_EPHEMERIS_LABELS,Plot.PROP_EPHEMERISLINECOUNT,
                    Plot.PROP_XAXIS, Plot.PROP_YAXIS );
            targetPlot.syncTo( srcPlot,exclude );
            targetPlot.setTicksURI("");
            targetPlot.setEphemerisLabels("");
            targetPlot.setEphemerisLineCount(-1);

            Map<String,String> nameMap= new HashMap<>();
            nameMap.put( srcPlot.getId(), targetPlot.getId() );
            nameMap.put( srcPlot.getXaxis().getId(), targetPlot.getXaxis().getId() );
            nameMap.put( srcPlot.getYaxis().getId(), targetPlot.getYaxis().getId() );
            nameMap.put( srcPlot.getZaxis().getId(), targetPlot.getZaxis().getId() );
            
            // check to see if there are any other plots sharing the same row and column.
            for ( int i=1; i<state.getPlots().length; i++ ) {
                Plot p= state.getPlots(i);
                if ( p==srcPlot ) continue;
                if ( p.getRowId().equals( srcPlot.getRowId() ) &&
                    p.getColumnId().equals( srcPlot.getColumnId() ) ) {
                    Row r= DomUtil.getRow( controller.getApplication(), targetPlot.getRowId() );
                    Column c= DomUtil.getColumn( controller.getApplication(), targetPlot.getColumnId() );
                    Plot newPlot= controller.addPlot( r, c );
                    exclude= Arrays.asList(Plot.PROP_ID, Plot.PROP_ROWID,Plot.PROP_COLUMNID );
                    newPlot.syncTo( state.getPlots(i), exclude );
                    nameMap.put( state.getPlots(i).getId(), newPlot.getId() );
                    nameMap.put( state.getPlots(i).getXaxis().getId(), newPlot.getXaxis().getId() );
                    nameMap.put( state.getPlots(i).getYaxis().getId(), newPlot.getYaxis().getId() );
                    nameMap.put( state.getPlots(i).getZaxis().getId(), newPlot.getZaxis().getId() );
                }
            }
            
            // check to see if there are any inset plots sharing the same parent row and parent column.
            for ( int i=1; i<state.getPlots().length; i++ ) {
                Plot p= state.getPlots(i);
                if ( p==srcPlot ) continue;
                Row r= DomUtil.getRow( state, p.getRowId() );
                Column c= DomUtil.getColumn( state, p.getColumnId() );
                if ( r.getParent().equals( srcPlot.getRowId() ) &&
                    c.getParent().equals( srcPlot.getColumnId() ) ) {
                    Row newRow=  controller.getCanvas().getController().addRow();
                    Column newColumn= controller.getCanvas().getController().addColumn();
                    newRow.syncTo( r, Arrays.asList(Row.PROP_ID,Row.PROP_PARENT) );
                    newRow.setParent( targetPlot.getRowId() );
                    newColumn.syncTo( c, Arrays.asList(Column.PROP_ID,Column.PROP_PARENT) );
                    newColumn.setParent( targetPlot.getColumnId() );
                    Plot newPlot= controller.addPlot( newRow, newColumn );
                    exclude= Arrays.asList(Plot.PROP_ID, Plot.PROP_ROWID,Plot.PROP_COLUMNID );
                    newPlot.syncTo( p, exclude );
                    nameMap.put( p.getId(), newPlot.getId() );
                    nameMap.put( p.getXaxis().getId(), newPlot.getXaxis().getId() );
                    nameMap.put( p.getYaxis().getId(), newPlot.getYaxis().getId() );
                    nameMap.put( p.getZaxis().getId(), newPlot.getZaxis().getId() );
                }
            }

            // if everything else is bound, then bind this one too.
            Application dom= controller.getApplication();
            for ( int i=0; i<state.getBindings().length; i++ ) {
                BindingModel bm= state.getBindings(i);
                String newSrc= nameMap.get( bm.getSrcId() );
                String newDst= nameMap.get( bm.getDstId() );
                DomNode src= dom.getController().getElementById(newSrc);
                DomNode dst= dom.getController().getElementById(newDst);
                controller.bind( src, bm.getSrcProperty(), dst, bm.getDstProperty() );
            }
            
            boolean doBindX= dom.getController().findBindings( dom, Application.PROP_TIMERANGE ).size()>0 &&
                    dom.getController().findBindings( targetPlot, Plot.PROP_CONTEXT ).isEmpty() &&
                    UnitsUtil.isTimeLocation( targetPlot.getXaxis().getRange().getUnits() ) &&
                    UnitsUtil.isTimeLocation( dom.getTimeRange().getUnits() );
            if ( doBindX ) {
                targetPlot.getXaxis().setRange( dom.getTimeRange() );
                controller.getApplication().getController().bind( dom, Application.PROP_TIMERANGE, targetPlot.getXaxis(), Axis.PROP_RANGE );
            } else {
                if ( dom.getController().findBindings( dom, Application.PROP_TIMERANGE, targetPlot, Plot.PROP_CONTEXT ).size()==1 &&
                    UnitsUtil.isTimeLocation( targetPlot.getXaxis().getRange().getUnits() ) ) {
                    dom.setTimeRange( targetPlot.getXaxis().getRange() );
                    controller.getApplication().getController().unbind( dom, Application.PROP_TIMERANGE, targetPlot, Plot.PROP_CONTEXT );
                    controller.getApplication().getController().bind( dom, Application.PROP_TIMERANGE, targetPlot.getXaxis(), Axis.PROP_RANGE );
                }
            }
            
            //List<DataSourceFilter> unresolved= new ArrayList<>();
            for ( int i=0; i<state.getDataSourceFilters().length; i++ ) {
                DataSourceFilter newDsf= controller.addDataSourceFilter();
                DataSourceFilter stateDsf= state.getDataSourceFilters(i);
                if ( stateDsf.getUri().startsWith("vap+internal:") ) {
                    //unresolved.add(stateDsf);
                } else {
                    newDsf.syncTo(state.getDataSourceFilters(i),Collections.singletonList("id"));   
                    state.setDataSourceFilters(i,null); // mark as done
                }
                nameMap.put( stateDsf.getId(), newDsf.getId() );
            }
            for ( int i=0; i<state.getDataSourceFilters().length; i++ ) { //implement vap+internal stuff, which are the remaining ones.
                DataSourceFilter stateDsf= state.getDataSourceFilters(i);
                if ( stateDsf!=null ) {
                    String uri= stateDsf.getUri();
                    String[] children= uri.substring(13).split(",");
                    StringBuilder sb= new StringBuilder( "vap+internal:" );
                    for ( int j=0; j<children.length; j++ ) {
                        if (j>0) sb.append(",");
                        sb.append( nameMap.get(children[j]) );
                    }
                    stateDsf.setUri(sb.toString());
                    DataSourceFilter newDsf= (DataSourceFilter)DomUtil.getElementById( controller.getApplication(), nameMap.get(stateDsf.getId()) );
                    newDsf.syncTo( stateDsf,Collections.singletonList("id"));   
                }
            }
            Application theApp= controller.getApplication();
            for ( int i=0; i<state.getPlotElements().length; i++ ) {
                PlotElement pe1= state.getPlotElements(i);
                DataSourceFilter dsf1= 
                        (DataSourceFilter) DomUtil.getElementById( theApp,nameMap.get(pe1.getDataSourceFilterId()) );
                Plot plot1= (Plot) DomUtil.getElementById( theApp, nameMap.get(pe1.getPlotId()) );
                List<PlotElement> recyclePes= controller.getPlotElementsFor(plot1);
                PlotElement pe;
                if ( i<recyclePes.size() ) {
                    pe= recyclePes.get(i);
                    pe.setDataSourceFilterId( dsf1.getId() );
                    pe.setPlotId( plot1.getId() );
                } else {
                    pe= controller.addPlotElement( plot1, dsf1 );
                }
                pe.syncTo( pe1, Arrays.asList( "id", "plotId", "dataSourceFilterId") );
                if ( i==0 ) {
                    plot1.setAutoBinding(true); // kludge
                    plot1.getController().setAutoBinding(true); // TODO: check on why there are two autoBinding properties
                }
                pe.getController().setResetPlotElement(false);
                pe.getController().setResetRanges(false); // See https://sourceforge.net/p/autoplot/bugs/2249/
            }
        } finally {
            controller.changePerformed( app, lockObject );
            lock.unlock();
        }
    }
        
    /**
     * allow plot elements from the clipboard single-plot .vap into the target plot.
     * This will just insert new plot elements which point at the same data source.
     * This was added to make it easier to add reference lines (Fce, Fuh) to
     * spectrograms of each B-field component.
     * @param app component parent for dialogs.
     * @param controller the application controller where we 
     * @param targetPlot the plotElements are added to this plot 
     * @throws HeadlessException 
     */
    public static void pasteClipboardPlotElementsIntoPlot( Component app, ApplicationController controller, Plot targetPlot )  {
        try {
            Clipboard clpbrd= Toolkit.getDefaultToolkit().getSystemClipboard();
            String s;
            if ( clpbrd.isDataFlavorAvailable(DataFlavor.stringFlavor) ) {
                s= (String) clpbrd.getData(DataFlavor.stringFlavor);
                if ( !isStringVap(s) ) {
                    JOptionPane.showMessageDialog(app,"<html>Use \"Edit Plot\"->\"Copy Plot to Clipboard\"<br>(Pasted content should be XML.)");
                    return;
                }
            } else {
                JOptionPane.showMessageDialog(app,"<html>Use \"Edit Plot\"->\"Copy Plot to Clipboard\"<br>(Content should be a string.)");
                return;
            }
            
            Application state;
            try {
                state= (Application)StatePersistence.restoreState(new ByteArrayInputStream(s.getBytes()));
            } catch ( IllegalArgumentException ex ) {
                JOptionPane.showMessageDialog(app,"<html>Use \"Edit Plot\"->\"Copy Plot to Clipboard\"<br>("+ex.getMessage()+")" );
                return;
            }
            
            PlotElement[] pes= state.getPlotElements();
            
            JPanel panel= new JPanel();
            //panel.setLayout( new BoxLayout(panel,BoxLayout.Y_AXIS) );
            JCheckBox[] cbs= new JCheckBox[pes.length];
            GridLayout gl= new GridLayout( pes.length, 2 );
            panel.setLayout(gl);
            for ( int i=0; i<pes.length; i++ ) {
                javax.swing.Icon icon= GraphicsUtil.guessIconFor(pes[i]);
                cbs[i]= new JCheckBox( "", true);
                panel.add( cbs[i] );
                
                if ( icon!=null ) {
                    JLabel x= new JLabel(pes[i].getRenderType().toString() ) ;
                    x.setIcon(icon);
                    panel.add( x ); 
                } else {
                    panel.add( new JLabel(pes[i].getRenderType().toString() ) ); 
                }
                
            }
            if ( JOptionPane.showConfirmDialog( app, panel, "Add Plot Elements", JOptionPane.OK_CANCEL_OPTION )==JOptionPane.OK_OPTION ) {
                boolean[] selected= new boolean[pes.length];
                for ( int i=0; i<pes.length; i++ ) {
                    selected[i]= cbs[i].isSelected();
                }
                doPasteClipboardPlotElementsIntoPlot(app, controller, state, pes, selected, targetPlot );
            }
            
        } catch (UnsupportedFlavorException | IOException ex) {
            Logger.getLogger(GuiSupport.class.getName()).log(Level.SEVERE, null, ex);
        }
    }  
    
    /**
     * do not use, this is introduced for testing.
     * @param client just used as lock object
     * @param controller the drop target's controller.
     * @param state the DOM we are merging in.
     * @param pes the plotElements from state.
     * @param selected 
     * @param targetPlot
     */
    public static void doPasteClipboardPlotElementsIntoPlot( Object client, ApplicationController controller, Application state, PlotElement[] pes, boolean[] selected, Plot targetPlot) {
        Object lockObject = "addPlotElements";        

        controller.registerPendingChange(client, lockObject);
        DomLock lock = controller.mutatorLock();

        try {
            lock.lock("pasting plot elements");
            controller.performingChange(client, lockObject);

            Map<String, String> nameMap = new HashMap<>();

            for (int i = 0; i < state.getDataSourceFilters().length; i++) {
                DataSourceFilter stateDsf = state.getDataSourceFilters(i);
                List<PlotElement> pes1 = DomUtil.getPlotElementsFor(state, stateDsf);
                boolean inUse = false;
                for (PlotElement pe1 : pes1) {
                    for (int j = 0; j < pes.length; j++) {
                        if (pes[j] == pe1) {
                            if (selected[j]) {
                                inUse = true;
                            }
                        }
                    }
                }
                if (inUse) {
                    DataSourceFilter newDsf = controller.addDataSourceFilter();

                    if (stateDsf.getUri().startsWith("vap+internal:")) {
                        //unresolved.add(stateDsf);
                    } else {
                        newDsf.syncTo(stateDsf, Collections.singletonList("id"));
                        state.setDataSourceFilters(i, null); // mark as done
                    }
                    nameMap.put(stateDsf.getId(), newDsf.getId());
                }
            }
            //TODO: vap+internal:data_1,data_2
            for (int i = 0; i < pes.length; i++) {
                if (selected[i]) {
                    PlotElement peNew = controller.addPlotElement(targetPlot, null, null);
                    peNew.syncTo(pes[i], Arrays.asList("id", "plotId", "dataSourceFilterId"));
                    String mappedName= nameMap.get(pes[i].getDataSourceFilterId());
                    if ( mappedName!=null ) {
                        peNew.setDataSourceFilterId(mappedName);
                    } else {
                        logger.warning("no DSF ID mapping--something has gone horribly wrong.");
                    }
                    peNew.getController().setResetPlotElement(false); // this seems a bit of a kludge.  Also resetting the ID (to mappedName) resets this flag.
                    peNew.getController().setResetRanges(false);
                }
            }
        } finally {
            controller.changePerformed(client, lockObject);
            lock.unlock();
        }

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
                org.das2.util.LoggerManager.logGuiEvent(e);                
                PropertyEditor pp = new PropertyEditor(domPlot);
                pp.showDialog(plot.getCanvas());
            }
        });
        plot.getDasMouseInputAdapter().addMenuItem(mi);
        expertMenuItems.add( mi );

        mi= new JMenuItem(new AbstractAction("Plot Element Properties") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);
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
                org.das2.util.LoggerManager.logGuiEvent(e);
                PlotElement p = controller.getPlotElement();
                PlotElement oldP= (PlotElement)p.copy();
                PlotStylePanel.StylePanel editorPanel= getStylePanel( p.getRenderType() );
                editorPanel.doElementBindings(p);
                if ( JOptionPane.CANCEL_OPTION==AutoplotUtil.showConfirmDialog( app, editorPanel, p.getRenderType() + " Style", JOptionPane.OK_CANCEL_OPTION ) ) {
                    p.syncTo(oldP);
                }
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
                org.das2.util.LoggerManager.logGuiEvent(e);
                Runnable run= new Runnable() {
                    @Override
                    public void run() {
                        Plot newPlot= controller.copyPlotAndPlotElements(domPlot, null, false, false);
                        Application dom= domPlot.getController().getApplication();
                        List<BindingModel> bms= dom.getController().findBindings( dom, Application.PROP_TIMERANGE, domPlot.getXaxis(), Axis.PROP_RANGE );
                        if ( bms.size()>0 && UnitsUtil.isTimeLocation( newPlot.getXaxis().getRange().getUnits() ) ) {
                            controller.bind( controller.getApplication(), Application.PROP_TIMERANGE, newPlot.getXaxis(), Axis.PROP_RANGE );
                        }
                    }
                };
                new Thread(run,"copyPlotElementsDown").start();
            }
        });
        item.setToolTipText("make a new plot below, and copy the plot elements into it.  New plot is bound by the x axis.");
        addPlotMenu.add(item);

        item = new JMenuItem( new  AbstractAction("Paste Plot From Clipboard") {
            @Override
            public void actionPerformed(ActionEvent e) {
                Plot newP= controller.addPlot( domPlot, LayoutConstants.BELOW );
                pasteClipboardIntoPlot(app,controller,newP);
            }
        });
        item.setToolTipText("Paste the plot in the system clipboard.");
        addPlotMenu.add(item);
            
        item = new JMenuItem( new AbstractAction("Add Inset Plot") {
            @Override
            public void actionPerformed(ActionEvent e) {
                controller.addPlot( "50%,100%-2em", "2em,50%" );
            }
        } );
        item.setToolTipText("Add a plot at an arbitrary position.");
        addPlotMenu.add(item);

        item = new JMenuItem( new AbstractAction("Add Right Axis Plot") {
            @Override
            public void actionPerformed(ActionEvent e) {
                Plot p= controller.addPlot( domPlot, null );
                PlotElement pe= controller.addPlotElement( p, null );
                logger.log(Level.FINE, "created new plotElement: {0}", pe);
                p.getYaxis().setOpposite( true );
                controller.bind( domPlot.getXaxis(), Axis.PROP_RANGE, p.getXaxis(), Axis.PROP_RANGE  );
            }
        } );
        item.setToolTipText("Add a plot in the same position but with its own axis on right side.");
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
                org.das2.util.LoggerManager.logGuiEvent(e);
                Runnable run= new Runnable() {
                    @Override
                    public void run() {
                        domPlot.getController().contextOverview();
                    }
                };
                new Thread(run,"contextOverview").start();
            }
        });
        item.setToolTipText("make a new plot, and copy the plot elements into it.  The plot is not bound,\n" +
                "and a connector is drawn between the two.  The panel uris are bound as well.");
        
        addPlotMenu.add(item);
        item = new JMenuItem(new AbstractAction("New Location (URI)...") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);
                Runnable run= new Runnable() {
                    @Override
                    public void run() {
                        app.dom.getController().setPlot(domPlot);
                        app.support.addPlotElement("New Location (URI)");
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
                org.das2.util.LoggerManager.logGuiEvent(e);
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

        item = new JMenuItem(new AbstractAction("Insert New Plot Above") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);
                PlotElement pelement = controller.getPlotElement();
                Plot dstPlot = controller.addPlot(LayoutConstants.ABOVE);
                pelement.setPlotId(dstPlot.getId());
            }
        });
        panelMenu.add(item);
        expertMenuItems.add(panelMenu);
        
        item = new JMenuItem(new AbstractAction("Insert New Plot Below") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);
                PlotElement pelement = controller.getPlotElement();
                Plot dstPlot = controller.addPlot(LayoutConstants.BELOW);
                pelement.setPlotId(dstPlot.getId());
            }
        });
        panelMenu.add(item);
        expertMenuItems.add(panelMenu);
        

        item = new JMenuItem(new AbstractAction("Move to Plot Below") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);
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
                org.das2.util.LoggerManager.logGuiEvent(e);
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
                org.das2.util.LoggerManager.logGuiEvent(e);
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
                org.das2.util.LoggerManager.logGuiEvent(e);
                GuiSupport.editPlotElement( controller.getApplicationModel(), plot );
            }
        });
        expertMenuItems.add(editDataMenu);

        plot.getDasMouseInputAdapter().addMenuItem(editDataMenu);

        plot.getDasMouseInputAdapter().addMenuItem(new JSeparator());

        plot.getDasMouseInputAdapter().addMenuItem(new JMenuItem(new AbstractAction("Reset Zoom") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);
                plotController.resetZoom(true, true, true);
            }
        }));

        plot.getDasMouseInputAdapter().addMenuItem(new JMenuItem(new AbstractAction("Add Annotation...") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);
                AnnotationEditorPanel p= new AnnotationEditorPanel();
                Annotation ann= new Annotation();
                ann.setPlotId( domPlot.getId() );
                Datum datax= plot.getXAxis().invTransform( plot.getDasMouseInputAdapter().getMousePressPositionOnCanvas().x );
                Datum datay= plot.getYAxis().invTransform( plot.getDasMouseInputAdapter().getMousePressPositionOnCanvas().y );
                ann.setPointAtX( datax );
                ann.setPointAtY( datay );
                ann.setXrange( DatumRange.newRange( datax, datax ) );
                ann.setYrange( DatumRange.newRange( datay, datay ) );
                p.doBindings(ann);
                if ( JOptionPane.OK_OPTION==AutoplotUtil.showConfirmDialog( app, p, "Add Annotation", JOptionPane.OK_CANCEL_OPTION ) ) {
                    controller.addAnnotation( ann );
                }
            }
        }));
        
        plot.getDasMouseInputAdapter().addMenuItem(new JSeparator());

        plotController.setExpertMenuItems( expertMenuItems.toArray(new JMenuItem[expertMenuItems.size()] ) );

        plot.getDasMouseInputAdapter().setMenuLabel(domPlot.getId());        

    }

    protected void doInspectVap() {
        Preferences prefs = AutoplotSettings.settings().getPreferences( AutoplotSettings.class);

        String currentDirectory = prefs.get( AutoplotSettings.PREF_LAST_OPEN_VAP_FOLDER, prefs.get(AutoplotSettings.PREF_LAST_OPEN_FOLDER, new File(System.getProperty("user.home")).toString() ) );
        String lcurrentFile=  prefs.get( AutoplotSettings.PREF_LAST_OPEN_VAP_FILE, "" );
        
        JFileChooser chooser= new JFileChooser(currentDirectory);
        if ( lcurrentFile.length()>0 ) {
            chooser.setSelectedFile( new File( lcurrentFile ) );
        }
        
        FileFilter ff = new FileNameExtensionFilter("vap files","vap");
        chooser.addChoosableFileFilter(ff);
        chooser.setFileFilter(ff);
        
        if ( JFileChooser.APPROVE_OPTION==chooser.showOpenDialog(parent) ) {
            try {
                final File f= chooser.getSelectedFile();
                prefs.put(AutoplotSettings.PREF_LAST_OPEN_VAP_FOLDER, f.getParent() );
                prefs.put( AutoplotSettings.PREF_LAST_OPEN_VAP_FILE, f.toString() );
                final Application vap = (Application) StatePersistence.restoreState( f );
                PropertyEditor edit = new PropertyEditor(vap);
                edit.addSaveAction( new AbstractAction("Save") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        org.das2.util.LoggerManager.logGuiEvent(e);
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
     * @param parent dialog parent.
     * @param app the applicationModel with canvas.
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
                Object font= PdfGraphicsOutput.ttfFromNameInteractive(c);
                if ( font==PdfGraphicsOutput.READING_FONTS ) {
                    return "Checking which fonts are embeddable...";
                } else if ( font!=null ) {
                    return "PDF okay";
                } else {                    
                    return "Can not be embedded in PDF";
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
     * @param frame
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

    /**
     * legacy code for adding examples to a text field.
     * @param tf
     * @param labels
     * @param tooltips
     * @return 
     */
    public static MouseAdapter createExamplesPopup( final JTextField tf, final String [] labels, final String[] tooltips ) {
        return new MouseAdapter() {
            private JMenuItem createMenuItem( final JTextField componentTextField, final String insert, String doc ) {
                JMenuItem result= new JMenuItem( new AbstractAction( insert ) {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        org.das2.util.LoggerManager.logGuiEvent(e);
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
