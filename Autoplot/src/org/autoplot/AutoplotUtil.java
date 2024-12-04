/*
 * AutoplotUtil.java
 *
 * Created on April 1, 2007, 4:02 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.autoplot; 

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.logging.Level;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.graph.DasColumn;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.TableModel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.das2.datum.EnumerationUnits;
import org.das2.graph.ContoursRenderer;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasColorBar;
import org.das2.graph.DasDevicePosition;
import org.das2.graph.DasPlot;
import org.das2.graph.DasRow;
import org.das2.graph.DefaultPlotSymbol;
import org.das2.graph.DigitalRenderer;
import org.das2.graph.EventsRenderer;
import org.das2.graph.HugeScatterRenderer;
import org.das2.graph.PitchAngleDistributionRenderer;
import org.das2.graph.PsymConnector;
import org.das2.graph.RGBImageRenderer;
import org.das2.graph.Renderer;
import org.das2.graph.SeriesRenderer;
import org.das2.graph.SpectrogramRenderer;
import org.das2.graph.SpectrogramRenderer.RebinnerEnum;
import org.das2.graph.StackedHistogramRenderer;
import org.das2.graph.TickCurveRenderer;
import org.das2.graph.VectorPlotRenderer;
import org.das2.system.RequestProcessor;
import org.das2.util.ExceptionHandler;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.LocalFileSystem;
import org.das2.util.filesystem.WebFileSystem;
import org.autoplot.bookmarks.Bookmark;
import org.autoplot.dom.Application;
import org.autoplot.dom.Axis;
import org.autoplot.dom.DataSourceFilter;
import org.autoplot.dom.DomUtil;
import org.autoplot.dom.Options;
import org.autoplot.dom.Plot;
import org.autoplot.dom.PlotElement;
import org.autoplot.dom.PlotElementController;
import org.das2.qds.DDataSet;
import org.das2.qds.DataSetAnnotations;
import org.das2.qds.QDataSet;
import org.das2.qds.DataSetUtil;
import org.das2.qds.SemanticOps;
import org.das2.qds.examples.Schemes;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.ReferenceCache;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.WindowManager;
import org.autoplot.datasource.capability.Caching;
import org.autoplot.dom.BindingModel;
import org.autoplot.dom.PlotController;
import org.das2.graph.BoundsRenderer;
import org.das2.graph.PolarPlotRenderer;
import org.das2.util.AboutUtil;
import org.das2.util.FileUtil;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Utility functions for Autoplot and other related applications. Note this
 * has no reference to the specific app AutoplotUI, because this is also used
 * in the applet which doesn't use AutoplotUI.
 * @author jbf
 */
public class AutoplotUtil {
    
    public static final int SERIES_SIZE_LIMIT = 80000;

    private final static Logger logger = org.das2.util.LoggerManager.getLogger("autoplot.gui");
    
    /**
     * absolute length limit for plots.  This is used to limit the elements used in autoranging, etc.
     */
    public final static int DS_LENGTH_LIMIT = 10000000;

    /**
     * return the bounding qube for the given render type.  This was stolen from Test022.
     * @param dataSet
     * @param renderType
     * @return bounding cube[3,2]
     * @throws Exception 
     */
    @Deprecated
    public static QDataSet bounds( QDataSet dataSet, RenderType renderType ) throws Exception {

        return AutoRangeUtil.bounds(dataSet, renderType);
    }
        
    /**
     * experiment to see if we can get an image of a dataset.  
     * This must be called from off of the event thread.
     * @param ds
     * @param width
     * @param height
     * @return the image
     * TODO: test me!
     */
    public static BufferedImage createImage( QDataSet ds, int width, int height ) {
        DasCanvas c= new DasCanvas( width, height );
        createPlot( c, ds, null, null );
        BufferedImage im= c.getImage( width, height );
        return im;
    }
        
    /**
     * Create a dasPlot that can be useful to scripts.
     * @param c the canvas for the plot, or null.
     * @param ds the dataset
     * @param recyclable the recyclable dasPlot, or null.
     * @param cb the colorbar, or null.
     * @return the DasPlot.
     */
    public static DasPlot createPlot(DasCanvas c, QDataSet ds, DasPlot recyclable, DasColorBar cb) {
        if ( c==null ) {
            if ( recyclable!=null ) {
                c= recyclable.getCanvas();
            } 
            if ( c==null ) {
                c= new DasCanvas(640,480);
            }
        }
        DasRow row;
        DasColumn col;
        DasPlot result;
        if (recyclable != null) {
            result = recyclable;
            row = result.getRow();
            col = result.getColumn();
        } else {
            row = DasRow.create(c);
            col = DasColumn.create(c);
            result = DasPlot.createDummyPlot();
            result.addRenderer( new SeriesRenderer() );
        }
        List<Renderer> recycleRends = Arrays.asList(result.getRenderers());

        RenderType type;
        Renderer rend1;
        Renderer oldRend= null;
        if ( ds!=null ) {
            type = AutoplotUtil.guessRenderType(ds);
            oldRend= recycleRends.get(0);
            rend1= maybeCreateRenderer( type, oldRend, cb, false);
            rend1.setDataSet(ds);
        } else {
            type = RenderType.series;
            rend1= new SeriesRenderer();
        }
        
        if ( RenderTypeUtil.needsColorbar(type) ) {
            if ( cb==null ) {
                cb= new DasColorBar( Datum.create(0), Datum.create(1), false );
                c.add( cb, row, col.createAttachedColumn( 1.03, 1.07 ) );
                rend1.setColorBar(cb);

            } else {
                cb.setVisible( true );  //okay, only since this is not used.
            }
        }

        try {
            if ( ds!=null ) {
                QDataSet bounds= bounds( ds, type );
                result.getXAxis().setDatumRange( DataSetUtil.asDatumRange( bounds.slice(0) ) );
                result.getYAxis().setDatumRange( DataSetUtil.asDatumRange( bounds.slice(1) ) );
                if ( cb!=null ) {
                    QDataSet ss= bounds.slice(2) ;
                    cb.setDatumRange( DataSetUtil.asDatumRange( ss ) );
                    cb.setLog( "log".equals( ss.property(QDataSet.SCALE_TYPE) ) );
                }
            }
        } catch ( Exception ex ) {
            logger.log( Level.SEVERE, null, ex );
        }
        
        if ( oldRend!=rend1 ) {
            result.removeRenderer(oldRend);
            result.addRenderer(rend1);
        }

        if ( recyclable==null ) {
            c.add(result, row, col);
            c.revalidate();
            c.validate();
        }
         
        result.resize();
        
        return result;
    }

    /**
     * create a new Icon that is a scaled instance of the first.  The image
     * should be a BufferedImage.
     * @param icon
     * @param w
     * @param h
     * @return
     */
    public static ImageIcon scaleIcon(ImageIcon icon, int w, int h) {
        double aspect = icon.getIconHeight() / (double) icon.getIconWidth();
        if (h == -1) {
            h = (int) (w * aspect);
        } else if (w == -1) {
            w = (int) (h / aspect);
        }
        BufferedImage image = (BufferedImage) icon.getImage();
        return new ImageIcon(scaleImage(image, w, h));
    }

    public static BufferedImage scaleImage(BufferedImage image, int w, int h) {
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = (Graphics2D) result.getGraphics();
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setTransform(AffineTransform.getScaleInstance(w / (double) image.getWidth(), h / (double) image.getHeight()));
        g2.drawImage(image, 0, 0, null);
        return result;
    }

    public static List<String> getUrls(List<Bookmark> recent) {
        List<String> urls = new ArrayList<>(recent.size());

        for (Bookmark b : recent) {
            if (b instanceof Bookmark.Item) {
                urls.add(((Bookmark.Item) b).getUri());
            }
        }
        return urls;
    }

    /**
     * Replace filename references within the DOM, and reset xrange.  This was 
     * the often-used ReplaceFile script.  This now follows focus.
     * @param parent focus for response dialogs.
     * @param dom the application
     */
    public static void replaceFile( Component parent, Application dom ) {

        DataSourceFilter dsf= dom.getController().getDataSourceFilter();
        
        if ( dsf.getUri()==null || dsf.getUri().equals("") ) {
           JOptionPane.showMessageDialog( parent, "Focus plot element has no data" );
        } else {

           if ( dsf.getUri().startsWith("vap+internal:") ){
               List<DataSourceFilter> pes= DomUtil.getParentsFor(dom, dsf.getUri());
               if ( pes.isEmpty() ) {
                   JOptionPane.showMessageDialog( parent, "Unable to resolve parents" );
                   return;
               }
               dsf= pes.get(0);
           }
           
           
           URISplit split= URISplit.parse( dsf.getUri() );
           
           if ( split.file==null || split.file.length()==0 ) {
               JOptionPane.showMessageDialog( parent, "<html>URI should refer to a file, but this doesn't: <br>"+dsf.getUri() );
               return;
           }

           Application dom2= (Application) dom.copy();
            
           String oldf= split.file;

           ReplaceFilePanel p= new ReplaceFilePanel();
           p.setCurrentFile(oldf);
           
           int result= AutoplotUtil.showConfirmDialog( parent,
            p, "Replace Filename", JOptionPane.OK_CANCEL_OPTION );

           if ( result==JOptionPane.OK_OPTION ) {

                for ( DataSourceFilter i: dom2.getDataSourceFilters() ) {
                    String oldf1= i.getUri();
                    String newf1= oldf1.replace( oldf, p.getSelectedFile() );
                    i.setUri( newf1 );
                }
                
                dom.syncTo(dom2);
                dom.getController().waitUntilIdle();

                // set focus and reset zoom x.
                dom.getController().setDataSourceFilter(dsf);
                resetZoomX(dom); 

            }
        }
    }

    /**
     * reload all the data.  This should not be called on the event thread.
     * @param dom
     */
    public static void reloadAll( Application dom ) {
        ReferenceCache.getInstance().reset();
        DataSetAnnotations.getInstance().reset();
        for ( DataSourceFilter dsf : dom.getDataSourceFilters() ) {
            if ( dsf.getUri()!=null && ! dsf.getUri().startsWith("vap+internal:") ) {
                final DataSourceFilter fdsf= dsf;
                Caching c= fdsf.getController().getCaching();
                if ( c!=null ) {
                    c.reset();
                }
                RequestProcessor.invokeLater( new Runnable() { 
                    @Override
                    public void run() {
                        fdsf.getController().update();
                    }
                } );
            } else {
                System.err.println( "not updating: " + dsf.getUri() );
            }
        }
        for ( Plot p: dom.getPlots() ) {
            if ( p.getTicksURI()!=null && p.getTicksURI().length()>0 ) {
                String oldTicksURI= p.getTicksURI();
                p.setTicksURI("");
                p.setTicksURI(oldTicksURI);
            }
        }
    }

    private static void doSearchToolTips1( final JComponent aThis, Pattern p, Map<Component,String> result  ) {
        String s= aThis.getToolTipText();
        boolean foundIt= false;
        if ( s!=null ) {
            if ( p.matcher(s).find() ) {
                result.put( aThis, s );
                foundIt= true;
            }
        }
        if ( !foundIt ) {
            s= null;
            if ( aThis instanceof javax.swing.JLabel ) {
                s= ((javax.swing.JLabel)aThis).getText();
            } else if ( aThis instanceof javax.swing.JMenuItem ) {
                s= ((javax.swing.JMenuItem)aThis).getText();
            } else if ( aThis instanceof javax.swing.JButton ) {
                s= ((javax.swing.JButton)aThis).getText();
            } else if ( aThis instanceof javax.swing.JCheckBox ) {
                s= ((javax.swing.JCheckBox)aThis).getText();
            } else if ( aThis instanceof JPanel ) {
                javax.swing.border.Border b= ((javax.swing.JPanel)aThis).getBorder();
                if ( b instanceof TitledBorder ) {
                    s= ((TitledBorder)b).getTitle();
                }
            }
            if ( s!=null && p.matcher(s).find() ) {
                result.put( aThis, s );
                //foundIt= true;
            }
        }
        for ( int i=0; i<aThis.getComponentCount(); i++ ) {
            Component kid= aThis.getComponent(i);
            if ( kid!=null && kid instanceof JComponent ) {
                doSearchToolTips1( (JComponent)kid, p, result );
            }
        }
        if ( aThis instanceof JMenu ) {
            JMenu m= (JMenu)aThis;
            for ( int i=0; i<m.getItemCount(); i++ ) {
                JMenuItem item= m.getItem(i);
                if ( item!=null ) doSearchToolTips1( item, p, result );
            }
        }
    }
    
    /**
     * the problem is how do you present the result?
     * @param aThis 
     */
    static void doSearchToolTips( final Container aThis ) {
        JPanel panel= new JPanel( new BorderLayout() );
        javax.swing.JTextField tf= new JTextField();
        panel.add( new JLabel("<html>Experimental Tooltips documentation search.  Enter the search keyword:"));
        panel.add( tf, BorderLayout.SOUTH );
        int i= JOptionPane.showConfirmDialog( aThis, panel, "Experimental Tooltips documentation search", JOptionPane.OK_CANCEL_OPTION );
        if ( i==JOptionPane.OK_OPTION ) {
            final String search= tf.getText();
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    Map<Component,String> result= new LinkedHashMap();
                    Pattern p= Pattern.compile(search,Pattern.CASE_INSENSITIVE);
                    for ( int i=0; i<aThis.getComponentCount(); i++ ) {
                        Component kid= aThis.getComponent(i);
                        if ( kid!=null && kid instanceof JComponent ) {
                            doSearchToolTips1( (JComponent)kid, p, result );
                        }
                    }
                    for ( Entry<Component,String> e: result.entrySet() ) {
                        System.err.println( e.getValue() );
                    }
                    if ( aThis instanceof JFrame ) {
                        JMenuBar mb= ((JFrame)aThis).getJMenuBar();
                        for ( int i=0; i<mb.getMenuCount(); i++ ) {
                            JMenu m= mb.getMenu(i);
                            if ( m!=null ) doSearchToolTips1(m,p,result);
                        }
                    }

                    JTable t= new JTable( result.size(), 2 );
                    t.setCellEditor(null);
                    t.setMinimumSize( new Dimension(800,480) );
                    t.setPreferredSize( new Dimension(800,480) );
                    
                    TableModel m= t.getModel();
                    t.getColumnModel().getColumn(0).setHeaderValue("Component");
                    t.getColumnModel().getColumn(0).setPreferredWidth(120);
                    t.getColumnModel().getColumn(0).setMaxWidth(120);
                    t.getColumnModel().getColumn(1).setHeaderValue("ToolTip");
                    
                    int i=0;
                    for ( Entry e: result.entrySet() ) {
                        String l= e.getKey().getClass().toString().replaceAll("class ", "" );
                        String tooltip= e.getValue().toString();
                        int j= l.lastIndexOf('.');
                        l= l.substring(j+1);
                        m.setValueAt( l, i, 0 );
                        m.setValueAt( tooltip, i, 1 );
                        if ( tooltip.startsWith("<html>" ) ) {
                            int linecount= tooltip.split("<br>").length;
                            t.setRowHeight(i,linecount*20);
                        } else {
                            t.setRowHeight(i,20 );
                        }
                        i++;
                    }
                    JScrollPane sp= new JScrollPane(t);

                    showConfirmDialog2( aThis, sp, "Tooltips search results", JOptionPane.OK_CANCEL_OPTION );
                    
               }
            };
            new Thread(run).start();
        }
    }

    /**
     * show a list of the filesystems
     * @param parent
     */
    protected static void doManageFilesystems( final Component parent ) {
        FileSystem[] fss= FileSystem.peekInstances();
        Arrays.sort(fss, new Comparator() {
            @Override
            public int compare(Object o1, Object o2) {
                return o1.toString().compareTo(o2.toString());
            }
        });
        final JPanel p= new JPanel();
        p.setLayout( new GridBagLayout() );
        GridBagConstraints c= new GridBagConstraints();
        c.gridy=0;
        c.fill = GridBagConstraints.HORIZONTAL;
        
        p.add( new JLabel("<html><em>Double-click on file system name to reset status</em>"),c );
        c.gridy++;
        
        for ( FileSystem fs: fss ) {
            c.weightx= 0.8;
            c.gridx= 0;
            c.anchor= GridBagConstraints.WEST;
            JLabel l= new JLabel( fs.toString() );
            p.add( l, c );
            final FileSystem ffs= fs;
            l.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if ( e.getClickCount()==2 ) {
                        Runnable run= new Runnable() {
                            @Override
                            public void run() {
                                FileSystem.reset(ffs);
                                try {
                                    FileSystem x= FileSystem.create(ffs.getRootURI());
                                } catch (FileSystem.FileSystemOfflineException | UnknownHostException | FileNotFoundException ex) {
                                    Logger.getLogger(AutoplotUtil.class.getName()).log(Level.SEVERE, null, ex);
                                }
                                Runnable run2= new Runnable() {
                                    @Override
                                    public void run() {
                                        SwingUtilities.getWindowAncestor(p).setVisible(false);
                                        doManageFilesystems( parent );        
                                    }
                                };
                                SwingUtilities.invokeLater(run2);
                            }
                        };    
                        new Thread(run).start();
                    }
                }
            });               
            c.weightx= 0.1;
            c.gridx= 1;
            c.anchor= GridBagConstraints.EAST;
            p.add( new JLabel("  "), c );
            c.weightx= 0.1;
            c.gridx= 2;
            c.anchor= GridBagConstraints.EAST;
            if ( fs instanceof LocalFileSystem ) {
                p.add( new JLabel( "ok" ), c );
            } else if ( fs instanceof WebFileSystem ) {
                String s= ((WebFileSystem)fs).isOffline() ? "offline" : "ok" ;
                if (((WebFileSystem)fs).isOffline()) {
                    s= "<html>"+s+"<br>"+((WebFileSystem)fs).getOfflineMessage();
                }
                l=new JLabel(s);
                p.add( l, c );
            }
            c.gridy++;
        }
        if ( fss.length==0 ) {
            p.add( new JLabel( "(no active filesystems)" ), c );
        }
        
        c.weighty= 1.;
        p.add( new JLabel(" "), c );
        
        JScrollPane pp=  new JScrollPane(p);
        pp.setPreferredSize( new Dimension(640,480) );
        
        pp.getVerticalScrollBar().setUnitIncrement( p.getFont().getSize() );
        AutoplotUtil.showMessageDialog( parent, pp, "Active Filesystems", JOptionPane.OK_OPTION );

    }

    /**
     * check to see if the user has the file HOME/autoplot_data/system.properties, which
     * if found will cause System.setProperty for each one.  This was introduced
     * to facilitate Craig with his testing of enableReferenceCache=true, and should
     * make it easier to provide generic extensions.
     */
    public static void maybeLoadSystemProperties() {
        File config= new File( new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ) ), "config" );
        if ( !config.exists() ) {
            if ( !config.mkdirs() ) {
                logger.log(Level.WARNING, "mkdir {0} failed", config);
                return;
            }
        }
        File propFile= new File( config, "system.properties" );
        if ( !propFile.exists() ) {
            try (PrintWriter w = new PrintWriter( new FileWriter( propFile ) )) {
                w.println("# Autoplot loads these system properties on startup.  See http://autoplot.org/systemProperties");
                w.println("");
                w.println("# reference cache allows some URIs to be resolved once per plot.");
                w.println("#enableReferenceCache=true");
                w.println("");
                w.println("# use new LANL-requested Nearest Neighbor rebinning that looks at bin boundaries.");
                w.println("#useLanlNearestNeighbor=true");
                w.println("");
                w.println("# do check on index and rank with commonly used datasets, at a slight performance cost.");
                w.println("#rangeChecking=true");
                w.println("");
                w.println("#email RTEs by default, instead of HTTP POST, when firewall prohibits put calls.");
                w.println("#autoplot.emailrte=true");
                w.println("");
                w.println("# use wget to download data instead of Java's built-in network protocols. Should be command line or empty.");
                w.println("#AP_WGET=/usr/local/wget");
                w.println("");
                w.println("# use curl to download data instead of Java's built-in network protocols. Should be command line or empty.");
                w.println("#AP_CURL=/usr/bin/curl");
                w.println("");
                w.println("# provide option in save dialog to embed data within a zip file.");
                w.println("#allowEmbedData=true");
                w.println("");
                w.println("# don't show icon in legend when there is only one renderer.");
                w.println("#reluctantLegendIcons=true");
                w.println("");
                w.println("# monitor the event thread for hangs.  See autoplot_data/log/, if the app hangs.");
                w.println("#enableResponseMonitor=true");
                w.println("");
                w.println("# turn off certificate checks.");
                w.println("#noCheckCertificate=true");
                w.println("");
                w.println("# use huge scatter for large data sets.");
                w.println("#useHugeScatter=true");
                w.println("");
                w.println("# HAPI cache location.");
                w.println("#HAPI_DATA=${HOME}/hapi_data");
                w.println("");
                w.println("# Enable HAPI Caching.");
                w.println("#hapiServerCache=true");
                w.println("");
                w.close();
            } catch ( IOException ex ) {
                logger.log(Level.WARNING, "write initial {0} failed.  {1}", new Object[] { propFile, ex } );
            }
        } else {
            logger.log( Level.FINER, "loading %s", propFile );
            Properties props= new Properties();
            try ( InputStream in=new FileInputStream(propFile) ) {
                props.load( in );
                for ( Entry p: props.entrySet()) {
                    logger.log( Level.FINEST, "%s=%s", new Object[] { p.getKey(), p.getValue() } );
                    if ( System.getProperty( (String)p.getKey() )==null ) { // command line should override.
                        System.setProperty( (String)p.getKey(), (String)p.getValue() );
                    }
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            } 
        }
    }

    /**
     * put in a place to call where the message is shown assuming the 
     * problem is on the user's end, but provide a button to inspect
     * the exception.
     * 
     * @param parent parent component to center the dialog.
     * @param string the message, a string or html code.
     * @param ex the wrapped exception
     * @param exh an exception handler to show the exception.
     */
    public static void showUserExceptionDialog( Component parent, String string, final Exception ex, final ExceptionHandler exh ) {
        JPanel p= new JPanel( new BorderLayout( ) );
        JLabel l1= new JLabel( string );
        p.add( l1, BorderLayout.CENTER );
        JPanel p1= new JPanel( new BorderLayout() );
        p1.add( new JButton( new AbstractAction("Details...") {
            @Override
            public void actionPerformed( ActionEvent ev ) {
                org.das2.util.LoggerManager.logGuiEvent(ev);        
                JComponent c= (JComponent)ev.getSource();
                JDialog dia= (JDialog) SwingUtilities.getWindowAncestor(c);
                dia.dispose();
                exh.handleUncaught(ex);
            }
        }), BorderLayout.EAST );
        p.add( p1, BorderLayout.SOUTH );
        JOptionPane.showMessageDialog( parent, p );
    }


    /**
     * this is a copy of the other autorange, lacking some of its hacks.  TODO: why?
     * This is not used.
     * @param hist
     * @param ds
     * @param properties
     * @return 
     * @see #autoRange(org.das2.qds.QDataSet, java.util.Map, boolean) 
     */
    @Deprecated
    public static AutoRangeUtil.AutoRangeDescriptor autoRange(QDataSet hist, QDataSet ds, Map properties) {
        return AutoRangeUtil.autoRange(hist, ds, properties);
    }

    /**
     * convert the legacy AutoRangeDescriptor to a QDataSet bounding cube.
     * The bounding cube is a rank 1, 2-element dataset with min and max as the
     * elements, and SCALE_TYPE="log" if the AutoRangeDescriptor log property was
     * true.
     * @param ard AutoRangeDescriptor.
     * @return a rank 1 bounding cube.
     */
    public static QDataSet toDataSet( AutoRangeUtil.AutoRangeDescriptor ard ) {
        DDataSet result= DDataSet.createRank1(2);
        Units u= ard.range.getUnits();
        if ( u==null ) u= Units.dimensionless;
        result.putValue(0,ard.range.min().doubleValue(u));
        result.putValue(1,ard.range.max().doubleValue(u));
        result.putProperty( QDataSet.BINS_0, "min,max" );
        if (ard.log) result.putProperty( QDataSet.SCALE_TYPE, "log" );
        if ( u!=Units.dimensionless ) result.putProperty(QDataSet.UNITS,u);
        return result;
    }

    public static boolean resetZoomY( Application dom ) {
        Plot plot= dom.getController().getPlot();
        return resetZoomY( dom, plot );
    }

    /**
     * @param dom
     * @param plot
     * @return 
     * @see PlotController#resetZoom(boolean, boolean, boolean) 
     */
    public static boolean resetZoomY( Application dom, Plot plot ) {
        boolean result= true;
        Axis axis= plot.getYaxis();

        List<PlotElement> pes= DomUtil.getPlotElementsFor( dom, plot );

        boolean alsoBindings= true; // See https://sourceforge.net/p/autoplot/bugs/2149/
        if ( alsoBindings ) {
            List<BindingModel> plots= DomUtil.findBindings( dom, plot.getYaxis(), Axis.PROP_RANGE );
            for ( BindingModel b : plots ) {
                Plot other;
                if ( b.getDstId().equals( plot.getYaxis().getId() ) ) {
                    Axis oa= (Axis)DomUtil.getElementById( dom, b.getSrcId() );
                    other= (Plot)DomUtil.getPlotForAxis( dom, oa );
                } else {
                    Axis oa= (Axis)DomUtil.getElementById( dom, b.getDstId() );
                    other= (Plot)DomUtil.getPlotForAxis( dom, oa );
                }
                pes.addAll( DomUtil.getPlotElementsFor( dom, other ) );
            }
        }
        
        DatumRange range= null;
        for ( PlotElement pe: pes ) {
            if ( pe.isActive()==false ) continue;
            QDataSet ds= pe.getController().getDataSet();
            if ( ds!=null ) {
                ds= SemanticOps.trim( ds, plot.getXaxis().getRange(), null );
                if ( ds.rank()>0 && ds.length()==0 ) break;
                if ( ds.rank()==0 || ( ds.rank()==1 && ds.length()==1 ) ) {
                    if ( ds.rank()==1 ) ds= ds.slice(0);
                    if ( range==null ) {
                        range= new DatumRange( DataSetUtil.asDatum(ds),DataSetUtil.asDatum(ds) );
                    } else {
                        range= DatumRangeUtil.union( range, DataSetUtil.asDatum(ds) );
                    }
                } else {
                    PlotElement pcopy1= (PlotElement)pe.copy();
                    PlotElementController.doAutoranging(pcopy1, Collections.singletonMap( QDataSet.SCALE_TYPE, (Object)( axis.isLog() ? "log" : "linear" ) ), ds, true ); // :) cast to Object!
                    if ( range==null ) {
                        range= pcopy1.getPlotDefaults().getYaxis().getRange();
                    } else {
                        range= DatumRangeUtil.union( range, pcopy1.getPlotDefaults().getYaxis().getRange() );
                    }
                }
            }
        }
        if ( range!=null ) axis.getController().setRangeAutomatically( range, axis.isLog() );
        PlotController.doHints( axis, axis.getAutoRangeHints() );
        
        return result;
    }

    public static boolean resetZoomX( Application dom ) {
        Plot plot= dom.getController().getPlot();
        return resetZoomX( dom, plot );
    } 
    
    /**
     * @see PlotController#resetZoom(boolean, boolean, boolean) 
     * @param dom
     * @param plot
     * @return 
     */
    public static boolean resetZoomX( Application dom, Plot plot ) {
        boolean result= true;
        Axis axis= plot.getXaxis();

        List<PlotElement> pes= DomUtil.getPlotElementsFor( dom, plot );

        boolean alsoBindings= true; // See https://sourceforge.net/p/autoplot/bugs/2149/
        if ( alsoBindings ) {
            List<BindingModel> plots= DomUtil.findBindings( dom, plot.getXaxis(), Axis.PROP_RANGE );
            for ( BindingModel b : plots ) {
                Plot other;
                if ( b.getDstId().equals( plot.getXaxis().getId() ) ) {
                    Object oo= DomUtil.getElementById( dom, b.getSrcId() );
                    if ( oo instanceof Axis ) {
                        Axis oa= (Axis)DomUtil.getElementById( dom, b.getSrcId() );
                        other= (Plot)DomUtil.getPlotForAxis( dom, oa );
                    } else {
                        // TODO: look for bindings through Application.timeRange.
                        continue;
                    }
                } else {
                    Object oo= DomUtil.getElementById( dom, b.getDstId() );
                    if ( oo instanceof Axis ) {
                        Axis oa= (Axis)oo;
                        other= (Plot)DomUtil.getPlotForAxis( dom, oa );
                    } else {
                        // TODO: look for bindings through Application.timeRange.
                        continue;
                    }
                }
                pes.addAll( DomUtil.getPlotElementsFor( dom, other ) );
            }
        }
                
        DatumRange range= null;
        for ( PlotElement pe: pes ) {
            if ( pe.isActive()==false ) continue;
            QDataSet ds= pe.getController().getDataSet();
            if ( ds!=null ) {
                ds= SemanticOps.trim( ds, null, plot.getYaxis().getRange() );
                if ( ds.length()==0 ) break;
                PlotElement pcopy1= (PlotElement)pe.copy(); // TODO: something ain't right below...
                PlotElementController.doAutoranging(
                        pcopy1, 
                        Collections.singletonMap( QDataSet.SCALE_TYPE, (Object)( axis.isLog() ? "log" : "linear" ) ), 
                        ds, 
                        true ); // :) cast to Object!
                if ( range==null ) {
                    range= pcopy1.getPlotDefaults().getXaxis().getRange();
                } else {
                    range= DatumRangeUtil.union( range, pcopy1.getPlotDefaults().getXaxis().getRange() );
                }
            }
        }
        if ( range!=null ) axis.getController().setRangeAutomatically( range, axis.isLog() );
        PlotController.doHints( axis, axis.getAutoRangeHints() );
        
        return result;
    }

    public static boolean resetZoomZ( Application dom ) {
        Plot plot= dom.getController().getPlot();
        return resetZoomZ( dom, plot );
    }

    /**
     * @see PlotController#resetZoom(boolean, boolean, boolean) 
     * @param dom
     * @param plot
     * @return 
     */
    public static boolean resetZoomZ( Application dom, Plot plot ) {

        boolean result= true;
        Axis axis= plot.getZaxis();

        List<PlotElement> pes= DomUtil.getPlotElementsFor( dom, plot );
        
        boolean alsoBindings= true; // See https://sourceforge.net/p/autoplot/bugs/2149/
        if ( alsoBindings ) {
            List<BindingModel> plots= DomUtil.findBindings( dom, plot.getZaxis(), Axis.PROP_RANGE );
            for ( BindingModel b : plots ) {
                Plot other;
                if ( b.getDstId().equals( plot.getZaxis().getId() ) ) {
                    Axis oa= (Axis)DomUtil.getElementById( dom, b.getSrcId() );
                    other= (Plot)DomUtil.getPlotForAxis( dom, oa );
                } else {
                    Axis oa= (Axis)DomUtil.getElementById( dom, b.getDstId() );
                    other= (Plot)DomUtil.getPlotForAxis( dom, oa );
                }
                pes.addAll( DomUtil.getPlotElementsFor( dom, other ) );
            }
        }
        
        DatumRange range= null;
        for ( PlotElement pe: pes ) {
            if ( pe.isActive()==false ) continue;
            if ( !RenderTypeUtil.needsColorbar(pe.getRenderType()) ) continue;
            QDataSet ds= pe.getController().getDataSet();
            if ( ds!=null ) {
                ds= SemanticOps.trim( ds, plot.getXaxis().getRange(), plot.getYaxis().getRange() );
                if ( ds.length()==0 ) break;
                    PlotElement pcopy1= (PlotElement)pe.copy();
                    PlotElementController.doAutoranging(pcopy1, Collections.singletonMap( QDataSet.SCALE_TYPE, (Object)( axis.isLog() ? "log" : "linear" ) ), ds, true ); // :) cast to Object!
                    if ( range==null ) {
                        range= pcopy1.getPlotDefaults().getZaxis().getRange();
                    } else {
                        range= DatumRangeUtil.union( range, pcopy1.getPlotDefaults().getZaxis().getRange() );
                    }
                }
            }
        if ( range!=null ) axis.getController().setRangeAutomatically( range, axis.isLog() );
        PlotController.doHints( axis, axis.getAutoRangeHints() );
        
        return result;
    }

    /**
     * Autorange using the dataset properties
     * @param ds the dataset, a non-bundle, to be autoranged.
     * @param properties Additional constraints for properties, such as SCALE_TYPE
     * @return  the range.
     * @see #autoRange(org.das2.qds.QDataSet, java.util.Map, boolean) 
     */
    @Deprecated
    public static AutoRangeUtil.AutoRangeDescriptor autoRange( QDataSet ds, Map properties ) {
        return AutoRangeUtil.autoRange(ds, properties);
    }

    /**
     * This early implementation of autoRange calculates the range of the
     * data, then locates the median to establish a linear or log scale type.
     * Very early on it tried to establish a robust range as well that would
     * exclude outliers.
     *
     * This should be rewritten to use the recently-implemented AutoHistogram,
     * which does an efficient, self-configuring, one-pass histogram of the data
     * that more effectively identifies the data range and outliers.
     *
     * TODO: This needs to be reworked. https://sourceforge.net/p/autoplot/bugs/1318/
     * 
     * @param ds The dataset, a non-bundle, to be autoranged.
     * @param properties Additional constraints for properties, such as SCALE_TYPE
     * @param ignoreDsProps Don't check ds for TYPICAL_MIN and SCALE_TYPE.  MONOTONIC is never ignored.
     * @return the range.
     * @deprecated 
     * @see AutoRangeUtil#autoRange
     */
    @Deprecated
    public static AutoRangeUtil.AutoRangeDescriptor autoRange(QDataSet ds, Map properties, boolean ignoreDsProps) {
        return AutoRangeUtil.autoRange(ds, properties, ignoreDsProps);
    }

    /**
     * open the URL in a browser.   Borrowed from http://www.centerkey.com/java/browser/.
     * @param url the URL.
     */
    public static void openBrowser(String url) {
        DataSourceUtil.openBrowser(url);
    }

    public static Document readDoc(InputStream is) throws SAXException, IOException, ParserConfigurationException {
        DocumentBuilder builder;
        builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        InputSource source = new InputSource(new InputStreamReader(is));
        Document document = builder.parse(source);

//        DOMParser parser = new org.apache.xerces.parsers.DOMParser();
//        
//        Reader in = new BufferedReader(new InputStreamReader(is));
//        InputSource input = new org.xml.sax.InputSource(in);
//        
//        parser.parse(input);
//        
//        Document document = parser.getDocument();
        return document;
    }
    

    /**
     * extract the properties from the dataset into the same format as metadata model returns.
     * @param ds
     * @return
     */
    public static Map<String, Object> extractProperties(QDataSet ds) {

        Map<String, Object> result = DataSetUtil.getProperties(ds);

        Object v;

        for (int i = 0; i < QDataSet.MAX_RANK; i++) {
            final String key = "DEPEND_" + i;
            if ((v = ds.property(key)) != null) {
                result.put(key, extractProperties((QDataSet) v));
            }
        }

        for (int i = 0; i < QDataSet.MAX_PLANE_COUNT; i++) {
            final String key = "PLANE_" + i;
            if ((v = ds.property(key)) != null) {
                result.put(key, extractProperties((QDataSet) v));
            } else {
                break;
            }

        }

        // grab at least the label and units from here
        if ( SemanticOps.isJoin(ds) && ds.length()>0 && ds.rank()==3 ) {
            QDataSet j1= (QDataSet)ds.slice(0).property(QDataSet.DEPEND_1);
            if ( j1!=null ) {
                Map<String,Object> h1= (Map<String, Object>) result.get(QDataSet.DEPEND_1);
                if ( h1==null ) h1= new HashMap();
                Object v1;
                v1= j1.property(QDataSet.LABEL);
                if ( v1!=null ) h1.put( QDataSet.LABEL, v1 );
                v1= j1.property(QDataSet.UNITS);
                if ( v1!=null ) h1.put( QDataSet.UNITS, v1 );
                result.put( QDataSet.DEPEND_1, h1 );
            }
        }

        return result;
    }

    /**
     * combine the two properties trees, using values from the first when both contain the same property.
     * @param properties
     * @param deflt
     * @return
     */
    public static Map<String, Object> mergeProperties(Map<String, Object> properties, Map<String, Object> deflt) {
        if (deflt == null)
            return properties;
        HashMap<String, Object> result = new HashMap<>(deflt);
        for (Entry<String, Object> entry : properties.entrySet()) {
            Object val = entry.getValue();
            String key = entry.getKey();
            if (val instanceof Map) {
                result.put(key, mergeProperties((Map<String, Object>) val, (Map<String, Object>) deflt.get(key)));
            } else {
                result.put(key, val);
            }
        }
        return result;
    }

    /**
     * support restricted security environment by checking permissions before 
     * checking property.
     * @param name
     * @param deft
     * @return
     */
    public static String getProperty(String name, String deft) {
        try {
            return System.getProperty(name, deft);
        } catch (SecurityException ex) {
            return deft;
        }
    }

    /**
     * set the device position, using spec string like "+5em,80%-5em"
     * @param row the row/column to modify
     * @param spec the spec
     * @throws java.text.ParseException
     */
    public static void setDevicePosition(DasDevicePosition row, String spec) throws ParseException {
        int i = spec.indexOf(',');
        if (i == -1)
            throw new IllegalArgumentException("spec must contain one comma");
        double[] ddmin = DasDevicePosition.parseLayoutStr(spec.substring(0, i));
        double[] ddmax = DasDevicePosition.parseLayoutStr(spec.substring(i + 1));
        row.setMinimum(ddmin[0]);
        row.setEmMinimum(ddmin[1]);
        row.setPtMinimum((int) ddmin[2]);
        row.setMaximum(ddmax[0]);
        row.setEmMaximum(ddmax[1]);
        row.setPtMaximum((int) ddmax[2]);
    }

    public static String formatDevicePosition(DasDevicePosition pos) {
        return DasDevicePosition.formatLayoutStr(pos, true) + ", " + DasDevicePosition.formatLayoutStr(pos, false);
    }

    private static boolean isVectorOrBundleIndex(QDataSet dep1) {
        boolean result = false;
        Units dep1Units = (Units) dep1.property(QDataSet.UNITS);
        if (dep1Units != null && dep1Units instanceof EnumerationUnits) {
            result = true;
        }

        if (dep1.property(QDataSet.COORDINATE_FRAME) != null) {
            result = true;
        }

        return result;
    }

    /**
     * @see org.autoplot.datasource.DataSourceUtil#guessRenderType(org.das2.qds.QDataSet), which will become the official version.
     * @see https://autoplot.org/developer.guessRenderType
     * @param fillds
     * @return
     */
    public static RenderType guessRenderType(QDataSet fillds) {
        RenderType spec;

        RenderType specPref= RenderType.spectrogram;
        Options o= new Options();
        Preferences prefs= AutoplotSettings.settings().getPreferences( o.getClass() );  //TODO: because this is static?
        boolean nn= prefs.getBoolean(Options.PROP_NEARESTNEIGHBOR,o.isNearestNeighbor());
        if ( nn ) {
            specPref = RenderType.nnSpectrogram;
        }
        
        boolean useHugeScatter= "true".equals( System.getProperty("useHugeScatter","true") );

        String srenderType= (String) fillds.property(QDataSet.RENDER_TYPE);
        if ( srenderType!=null ) {
            if ( srenderType.equals("time_series") ) { //TODO: CDAWeb time_series will be fixed to "series" once it can automatically reduce data.
                if ( useHugeScatter && fillds.length() > SERIES_SIZE_LIMIT) {
                    spec = RenderType.hugeScatter;
                } else {
                    spec = RenderType.series;
                }
                return spec;
            } else if ( srenderType.equals("waveform" ) ) {
                if ( useHugeScatter ) {
                    spec = RenderType.hugeScatter;
                } else {
                    spec = RenderType.series;
                }
                return spec;
            }
            try {
                if ( srenderType.equals("spectrogram") ) {
                    spec= specPref;
                } else {
                    spec = RenderType.valueOf(srenderType);
                }
                return spec;
            } catch (IllegalArgumentException e) {
                int i= srenderType.indexOf('>');
                if ( i>-1 ) {
                    try {
                        srenderType= srenderType.substring(0,i);
                        if ( srenderType.equals("spectrogram") ) {
                            // allow user preference here.
                            spec= specPref;
                        } else {
                            spec = RenderType.valueOf(srenderType.substring(0,i));
                        }
                        return spec;
                    } catch (IllegalArgumentException e2) {
                        System.err.println("unable to resolve render type for: "+srenderType + " in " +fillds );
                        //e.printStackTrace();  // okay.  we didn't recognize the render type
                    }
                }
            }
        }

        QDataSet dep1 = (QDataSet) fillds.property(QDataSet.DEPEND_1);
        QDataSet plane0 = (QDataSet) fillds.property(QDataSet.PLANE_0);
        QDataSet bundle1= (QDataSet) fillds.property(QDataSet.BUNDLE_1);
        QDataSet dep0= (QDataSet) fillds.property(QDataSet.DEPEND_0);
        
        if ( fillds.property( QDataSet.JOIN_0 )!=null ) {
            if ( fillds.length()==0 ) {
                return RenderType.series;
            }
            dep1 = (QDataSet) fillds.property(QDataSet.DEPEND_1,0);
            plane0 = (QDataSet) fillds.property(QDataSet.PLANE_0,0);
            bundle1= (QDataSet) fillds.property(QDataSet.BUNDLE_1,0);
        }

        if ( fillds.rank()==2 ) {
            if ( SemanticOps.isRank2Waveform(fillds) ) {
                if ( useHugeScatter ) {
                    return RenderType.hugeScatter;
                } else {
                    return RenderType.series;
                }
            }
        }

        if (fillds.rank() >= 2) {
            boolean trivialBundle= false; // is the bundle really describing anything?
            if ( dep1!=null && !isVectorOrBundleIndex(dep1) ) {
                if ( bundle1.length()>0 ) {
                    trivialBundle= true;
                    Units u0= (Units)bundle1.property(QDataSet.UNITS,0);
                    for ( int j=0; j<bundle1.length(); j++ ) {
                        if ( bundle1.property(QDataSet.NAME)!=null && bundle1.property(QDataSet.UNITS)!=u0 ) {
                            trivialBundle= false;
                        }
                    }
                }
            }
            if ( trivialBundle ) {
                return specPref;
//            if ( dep1!=null && !isVectorOrBundleIndex(dep1) ) {
//                spec = specPref; // favor spectrograms when we have a BUNDLE_1 and DEPEND_1.
//            } else if ( bundle1!=null || (dep1 != null && isVectorOrBundleIndex(dep1) ) ) {
            } else if ( ( bundle1!=null && bundle1.length()<30 ) || (dep1 != null && isVectorOrBundleIndex(dep1) ) ) {
                if ( useHugeScatter && fillds.length() > SERIES_SIZE_LIMIT) {
                    spec = RenderType.hugeScatter;
                } else {
                    spec = RenderType.series;
                }
                if ( bundle1!=null ) {
                    if ( bundle1.length()==3 && bundle1.property(QDataSet.DEPEND_0,2)!=null ) { // bad kludge
                        spec= RenderType.colorScatter;
                    } else if ( dep0==null && bundle1.length() == 3 && bundle1.property(QDataSet.DEPENDNAME_0, 2) != null) { // bad kludge
                        spec= RenderType.colorScatter;
                    } else if ( Schemes.isXYZScatter(fillds) && fillds.property(QDataSet.DEPEND_0)==null && !Schemes.isEventsList(fillds) ) { 
                        spec= RenderType.colorScatter;
                    } else if ( Schemes.isXYScatter(fillds) && fillds.property(QDataSet.DEPEND_0)==null ) { 
                        spec= RenderType.scatter;
                    } else if ( bundle1.length()==3 || bundle1.length()==4 || bundle1.length()==5 ) {
                        if ( Schemes.isEventsList(fillds) ) {
                            spec= RenderType.eventsBar;
                        } else {
                            Units u3= (Units) bundle1.property(QDataSet.UNITS,bundle1.length()-1);
                            if ( UnitsUtil.isOrdinalMeasurement(u3) ) {
                                spec= RenderType.digital;
                            }
                        }
                    } else {
                        Units u3= (Units) bundle1.property(QDataSet.UNITS,bundle1.length()-1);
                        if ( u3!=null && UnitsUtil.isOrdinalMeasurement(u3) ) {
                            spec= RenderType.eventsBar;
                        }
                    }
                }
            } else {
                int[] dims= DataSetUtil.qubeDims(fillds);
                if ( dep1==null && fillds.rank()==2 && fillds.length()>3 && fillds.length(0)<4 ) { // Vector quantities without labels. [3x3] is a left a matrix.
                    spec = RenderType.series;
                } else if ( fillds.rank()==2 && dims!=null && dims[0]==2 && dims[1]==2 ) {
                    spec = RenderType.bounds;
                } else if ( fillds.rank()==3 && dims!=null && dims[1]==2 && dims[2]==2 ) {
                    spec = RenderType.bounds;
                } else {
                    spec = specPref;
                }
            }
        } else if ( fillds.rank()==0 || fillds.rank()==1 && ( SemanticOps.isBundle(fillds) || Schemes.isComplexNumbers(fillds) ) ) {
            spec= RenderType.digital;

        } else if ( SemanticOps.getUnits(fillds) instanceof EnumerationUnits ) {
            if ( dep0==null ) {
                spec= RenderType.digital;
            } else {
                spec= RenderType.eventsBar;
            }
        } else {
            if ( useHugeScatter && fillds.length() > SERIES_SIZE_LIMIT) {
                spec = RenderType.hugeScatter;
            } else {
                spec = RenderType.series;
            }

            if (plane0 != null) {
                Units u = (Units) plane0.property(QDataSet.UNITS);
                if (u==null) u= Units.dimensionless;
                if (u != null && (UnitsUtil.isRatioMeasurement(u) || UnitsUtil.isIntervalMeasurement(u))) {
                    spec = RenderType.colorScatter;
                }
            }
        }

        return spec;
    }

    /**
     * The Double.parseDouble contains this javadoc describing how to test for a valid double.
     * @param myString
     * @return
     */
    public static boolean isParsableDouble( String myString ) {
	final String Digits	= "(\\p{Digit}+)";
        final String HexDigits  = "(\\p{XDigit}+)";
	// an exponent is 'e' or 'E' followed by an optionally
	// signed decimal integer.
	final String Exp	= "[eE][+-]?"+Digits;
	final String fpRegex	=
	    ("[\\x00-\\x20]*"+	// Optional leading "whitespace"
	     "[+-]?(" +	// Optional sign character
	     "NaN|" +		// "NaN" string
	     "Infinity|" +	// "Infinity" string

	     // A decimal floating-point string representing a finite positive
	     // number without a leading sign has at most five basic pieces:
	     // Digits . Digits ExponentPart FloatTypeSuffix
	     //
	     // Since this method allows integer-only strings as input
	     // in addition to strings of floating-point literals, the
	     // two sub-patterns below are simplifications of the grammar
	     // productions from the Java Language Specification, 2nd
	     // edition, section 3.10.2.

	     // Digits ._opt Digits_opt ExponentPart_opt FloatTypeSuffix_opt
	     "((("+Digits+"(\\.)?("+Digits+"?)("+Exp+")?)|"+

	     // . Digits ExponentPart_opt FloatTypeSuffix_opt
	     "(\\.("+Digits+")("+Exp+")?)|"+

       // Hexadecimal strings
       "((" +
        // 0[xX] HexDigits ._opt BinaryExponent FloatTypeSuffix_opt
        "(0[xX]" + HexDigits + "(\\.)?)|" +

        // 0[xX] HexDigits_opt . HexDigits BinaryExponent FloatTypeSuffix_opt
        "(0[xX]" + HexDigits + "?(\\.)" + HexDigits + ")" +

        ")[pP][+-]?" + Digits + "))" +
	     "[fFdD]?))" +
	     "[\\x00-\\x20]*");// Optional trailing "whitespace"

        return java.util.regex.Pattern.matches(fpRegex, myString);
    }

    /**
     * return a renderer that is configured for this renderType.
     * @param renderType
     * @param recyclable
     * @param colorbar
     * @param justRenderType if true, then just set the render type, other code will configure it.
     *    If true, presumably bindings will set the state.
     * @return
     */
    public static Renderer maybeCreateRenderer(
            RenderType renderType,
            Renderer recyclable, 
            DasColorBar colorbar, 
            boolean justRenderType ) {
        boolean conf= !justRenderType;

        if (renderType == RenderType.spectrogram) {
            SpectrogramRenderer result;
            if (recyclable != null && recyclable instanceof SpectrogramRenderer) {
                result= (SpectrogramRenderer) recyclable;
                if ( conf ) result.setRebinner(SpectrogramRenderer.RebinnerEnum.binAverage);
            } else {
                result = new SpectrogramRenderer(null, colorbar);
                result.setDataSetLoader(null);
            }
            if ( conf ) result.setRebinner( SpectrogramRenderer.RebinnerEnum.binAverage );
            return result;
        } else if (renderType == RenderType.nnSpectrogram) {
            SpectrogramRenderer result;
            RebinnerEnum nn;
            if ( "true".equals( System.getProperty("useLanlNearestNeighbor","false") ) ) {
                nn= SpectrogramRenderer.RebinnerEnum.lanlNearestNeighbor;
            } else {
                nn= SpectrogramRenderer.RebinnerEnum.nearestNeighbor;
            }
            if (recyclable != null && recyclable instanceof SpectrogramRenderer) {
                result= (SpectrogramRenderer) recyclable;
                if ( conf ) result.setRebinner(nn);
            } else {
                
                result = new SpectrogramRenderer(null, colorbar);
                result.setDataSetLoader(null);
                if ( conf ) result.setRebinner(nn);
                return result;
            }
            result.setRebinner( nn );
            return result;
        } else if (renderType == RenderType.hugeScatter) {
            if (recyclable != null && recyclable instanceof HugeScatterRenderer) {
                return recyclable;
            } else {
                HugeScatterRenderer result = new HugeScatterRenderer(null);
                result.setEnvelope(1); 
                result.setDataSetLoader(null);
                return result;
            }
        } else if ( renderType==RenderType.digital ) {
            if (recyclable != null && recyclable instanceof DigitalRenderer) {
                return recyclable;
            } else {
                Renderer result = new DigitalRenderer();
                result.setDataSetLoader(null);
                return result;
            }
        } else if ( renderType==RenderType.image ) {
            if (recyclable != null && recyclable instanceof RGBImageRenderer ) {
                return recyclable;
            } else {
                Renderer result = new RGBImageRenderer();
                result.setDataSetLoader(null);
                return result;
            }
        } else if ( renderType==RenderType.eventsBar ) {
            if (recyclable != null && recyclable instanceof EventsRenderer ) {
                return recyclable;
            } else {
                Renderer result = new EventsRenderer();
                result.setDataSetLoader(null);
                return result;
            }
        } else if ( renderType==RenderType.stackedHistogram ) {
            if (recyclable != null && recyclable instanceof StackedHistogramRenderer ) {
                return recyclable;
            } else {
                Renderer result = new StackedHistogramRenderer( colorbar);
                result.setDataSetLoader(null);
                return result;
            }
        } else if ( renderType==RenderType.vectorPlot ) {
            if (recyclable != null && recyclable instanceof VectorPlotRenderer ) {
                return recyclable;
            } else {
                Renderer result = new VectorPlotRenderer();
                result.setDataSetLoader(null);
                return result;
            }
        } else if ( renderType==RenderType.orbitPlot ) {
            if (recyclable != null && recyclable instanceof TickCurveRenderer ) {
                return recyclable;
            } else {
                Renderer result = new TickCurveRenderer();
                result.setDataSetLoader(null);
                return result;
            }
        } else if ( renderType==RenderType.contour ) {
            if (recyclable != null && recyclable instanceof ContoursRenderer ) {
                return recyclable;
            } else {
                Renderer result = new ContoursRenderer();
                result.setDataSetLoader(null);
                return result;
            }

        } else if ( renderType==RenderType.polar ) {
            if (recyclable != null && recyclable instanceof PolarPlotRenderer ) {
                return recyclable;
            } else {
                Renderer result = new PolarPlotRenderer( colorbar );
                result.setDataSetLoader(null);
                return result;
            }
            
        } else if ( renderType==RenderType.pitchAngleDistribution ) {
            if (recyclable != null && recyclable instanceof PitchAngleDistributionRenderer ) {
                return recyclable;
            } else {
                PitchAngleDistributionRenderer result = new PitchAngleDistributionRenderer(colorbar);
                result.setDataSetLoader(null);
                return result;
            }
        } else if ( renderType==RenderType.bounds ) {
            if (recyclable != null && recyclable instanceof BoundsRenderer ) {
                return recyclable;
            } else {
                BoundsRenderer result = new BoundsRenderer();
                result.setDataSetLoader(null);
                return result;
            }
        } else if ( renderType==RenderType.internal ) {
            return recyclable;
            
//        } else if ( renderType==RenderType.image ) {
//            if (recyclable != null && recyclable instanceof ImageRenderer) {
//                return recyclable;
//            } else {
//                Renderer result = new ImageRenderer();
//                result.setDataSetLoader(null);
//                colorbar.setVisible(false);
//                return result;
//            }
        } else {
            SeriesRenderer result;
            if (recyclable != null && recyclable instanceof SeriesRenderer) {
                result = (SeriesRenderer) recyclable;
            } else {
                result = new SeriesRenderer();
                result.setDataSetLoader(null);
            }

            if ( justRenderType ) return result;

            if (renderType == RenderType.colorScatter) {
                result.setColorBar(colorbar);
                result.setColorByDataSetId(QDataSet.PLANE_0); //schema: this should be the name of the dataset, or PLANE_x
            } else {
                result.setColorByDataSetId(""); //schema
            }

            if (renderType == RenderType.series) {
                result.setPsymConnector(PsymConnector.SOLID);
                result.setHistogram(false);
                result.setFillToReference(false);

            } else if (renderType == RenderType.scatter) {
                result.setPsymConnector(PsymConnector.NONE);
                result.setPsym( DefaultPlotSymbol.CIRCLES );
                result.setFillToReference(false);

            } else if (renderType == RenderType.colorScatter) {
                result.setPsymConnector(PsymConnector.NONE);
                result.setPsym( DefaultPlotSymbol.CIRCLES );
                result.setSymSize(3);
                result.setFillToReference(false);

            } else if (renderType == RenderType.stairSteps) {
                result.setPsymConnector(PsymConnector.SOLID);
                result.setFillToReference(true);
                result.setHistogram(true);

            } else if (renderType == RenderType.fillToZero) {
                result.setPsymConnector(PsymConnector.SOLID);
                result.setFillToReference(true);
                result.setHistogram(false);

            }

            return result;
        }

    }

    /**
     * return 64x64 pixel Autoplot Icon.
     * @return 
     */
    public static Image getAutoplotIcon() {
        return new ImageIcon(AutoplotUtil.class.getResource("/resources/logo64.png")).getImage();
    }
    
    public static Image getNoIcon() {
        return new ImageIcon(AutoplotUtil.class.getResource("/resources/logo64.png")).getImage();
    }

    public static Icon cancelIcon() {
        return new ImageIcon( AutoplotUtil.class.getResource("/com/cottagesystems/jdiskhog/resources/cancel14.png" ) );
    }

//    private static int styleFromMessageType(int messageType) {
//        switch (messageType) {
//        case JOptionPane.ERROR_MESSAGE:
//            return JRootPane.ERROR_DIALOG;
//        case JOptionPane.QUESTION_MESSAGE:
//            return JRootPane.QUESTION_DIALOG;
//        case JOptionPane.WARNING_MESSAGE:
//            return JRootPane.WARNING_DIALOG;
//        case JOptionPane.INFORMATION_MESSAGE:
//            return JRootPane.INFORMATION_DIALOG;
//        case JOptionPane.PLAIN_MESSAGE:
//        default:
//            return JRootPane.PLAIN_DIALOG;
//        }
//    }
//
//   private JDialog createDialog(Component parentComponent, String title,
//            int style)
//            throws HeadlessException {
//
//        final JDialog dialog;
//
////        Window window = JOptionPane.getWindowForComponent(parentComponent);
////        if (window instanceof Frame) {
////            dialog = new JDialog((Frame)window, title, true);
////        } else {
////            dialog = new JDialog((Dialog)window, title, true);
////        }
//// 	if (window instanceof SwingUtilities.SharedOwnerFrame) {
////	    WindowListener ownerShutdownListener =
////		(WindowListener)SwingUtilities.getSharedOwnerFrameShutdownListener();
//// 	    dialog.addWindowListener(ownerShutdownListener);
//// 	}
////        initDialog(dialog, style, parentComponent);
////        return dialog;
//        return null;
//    }

    /**
     * wrapper for displaying messages.  This will eventually use the Autoplot icon, etc.
     * This should be called, not JOptionPane.showMessageDialog(...)
     * @param parentComponent
     * @param message, String or Component for the message.
     * @param title
     * @param messageType, like JOptionPane.ERROR_MESSAGE, JOptionPane.INFORMATION_MESSAGE, JOptionPane.WARNING_MESSAGE, JOptionPane.QUESTION_MESSAGE, or JOptionPane.PLAIN_MESSAGE
     */
    public static void showMessageDialog( Component parentComponent, Object message, String title, int messageType ) {
        //JOptionPane.showMessageDialog( parent, message, title, messageType );
        //JOptionPane.showOptionDialog( parent, message, title, JOptionPane.DEFAULT_OPTION, messageType, null, null, null);

        if ( message instanceof Component ) {
            final Component editorPane= (Component)message;
            // Sandip Chitale's solution
            // https://blogs.oracle.com/scblog/entry/tip_making_joptionpane_dialog_resizable
            // TIP: Make the JOptionPane resizable using the HierarchyListener.  
            editorPane.addHierarchyListener(new HierarchyListener() {
                @Override
                public void hierarchyChanged(HierarchyEvent e) {
                    Window window = SwingUtilities.getWindowAncestor(editorPane);
                    if (window instanceof Dialog) {
                        final Dialog dialog = (Dialog)window;
                        if (!dialog.isResizable()) {
                            SwingUtilities.invokeLater( new Runnable() { 
                                @Override
                                public void run() {
                                    dialog.setResizable(true);
                                }
                            } );
                        }
                    }
                }
            });        
        }
        
        JOptionPane.showMessageDialog( parentComponent, message, title, messageType, new ImageIcon( AutoplotUtil.getAutoplotIcon() ) );

    }
    
    /**
     * Wrapper for displaying ok,cancel dialogs.  
     * If the message is a component, then the dialog will be resizeable.
     * @param parentComponent determines the Frame in which the dialog is displayed; if null, or if the parentComponent has no Frame, a default Frame is used
     * @param message the String or GUI component to display
     * @param title the title string for the dialog
     * @param optionType an int designating the options available on the dialog: YES_NO_OPTION, YES_NO_CANCEL_OPTION, or OK_CANCEL_OPTION
     * @return JOptionPane.OK_OPTION, JOptionPane.CANCEL_OPTION, etc.
     */
    public static int showConfirmDialog( Component parentComponent, Object message, String title, int optionType ) {
        if ( optionType!=JOptionPane.OK_CANCEL_OPTION ) {
            return JOptionPane.showConfirmDialog( parentComponent, message, title, optionType );
        } else {
            return showConfirmDialog2( parentComponent, message, title, optionType );
        }
    }
    
    /**
     * new okay/cancel dialog that is resizable and is made with a simple dialog.
     * @param parent
     * @param omessage String or Component.
     * @param title
     * @param optionType.  This must be OK_CANCEL_OPTION or YES_NO_CANCEL_OPTION
     * @return JOptionPane.OK_OPTION, JOptionPane.CANCEL_OPTION.
     */
    public static int showConfirmDialog2( Component parent, Object omessage, String title, int optionType ) {
        return WindowManager.showConfirmDialog( parent, omessage, title, optionType );
    }

    public static final boolean is32bit;
    
    public static final String javaVersionWarning;

    static {
        String s= System.getProperty("sun.arch.data.model");
        if ( s==null ) { // GNU 1.5? 
            s= System.getProperty("os.arch");
            is32bit = !s.contains("64");
        } else {
            is32bit = s.equals("32");
        }     
        String javaVersion=  System.getProperty("java.version"); // applet okay

        Pattern p= Pattern.compile("(\\d+\\.\\d+)\\.\\d+\\_(\\d+)");
        Matcher m= p.matcher(javaVersion);
        if ( m.matches() ) {
            double major= Double.parseDouble( m.group(1) );
            int minor= Integer.parseInt( m.group(2) );
            if ( major<1.8 || ( major==1.8 && minor<102 ) ) {
                javaVersionWarning= " (oldJRE)";
            } else {
                javaVersionWarning= "";
            }
        } else {
            javaVersionWarning= "";
        }
    }    

    /**
     * return the processID (pid), or the fallback if the pid cannot be found.
     * @param fallback the string (null is okay) to return when the pid cannot be found.
     * @return the process id or the fallback provided by the caller.
     * //TODO: Java9 has method for accessing process ID.
     */
    public static String getProcessId(final String fallback) {
        // Note: may fail in some JVM implementations
        // therefore fallback has to be provided

        // something like '<pid>@<hostname>', at least in SUN / Oracle JVMs
        final String jvmName = ManagementFactory.getRuntimeMXBean().getName();
        final int index = jvmName.indexOf('@');

        if (index < 1) {
            // part before '@' empty (index = 0) / '@' not found (index = -1)
            return fallback;
        }

        try {
            return Long.toString(Long.parseLong(jvmName.substring(0, index)));
        } catch (NumberFormatException e) {
            // ignore
        }
        return fallback;
    }
    
    /**
     * return an HTML page showing the current system environment.
     * @param model
     * @return string containing HTML
     * @throws IOException 
     */
    public static String getAboutAutoplotHtml( ApplicationModel model) throws IOException {
        StringBuilder buffy = new StringBuilder();

        buffy.append("<html>\n");
        URL aboutHtml = AutoplotUI.class.getResource("aboutAutoplot.html");

        String releaseTag= AboutUtil.getReleaseTag();
        
        if ( aboutHtml!=null ) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(aboutHtml.openStream()))) {
                String s = reader.readLine();
                while (s != null) {
                    s= s.replaceAll( "\\#\\{tag\\}", releaseTag );
                    buffy.append(s);
                    s = reader.readLine();
                }
            }
        } 

        buffy.append("<h2>Build Information:</h2>");
        buffy.append("<ul>");
        buffy.append("<li>release tag: ").append(AboutUtil.getReleaseTag()).append("</li>");
        buffy.append("<li>build url: ").append(AboutUtil.getJenkinsURL()).append("</li>");

        List<String> bi = Util.getBuildInfos();
        for (String ss : bi) {
            buffy.append("    <li>").append(ss);
        }
        buffy.append("</ul>" );

        buffy.append( "<h2>Open Source Components:</h2>");
        buffy.append( "Autoplot uses many open-source components, such as: <br>");
        buffy.append( "jsyntaxpane, Jython, Netbeans (Jython completion), OpenDAP, CDF, FITS, NetCDF, " 
                + "POI HSSF (Excel), Batik (SVG), iText (PDF), JSON, JavaCSV, JPG Metadata Extractor, Imgscalr, utils4j, JDiskHog, and Das2.");        

        buffy.append("<h2>Runtime Information:</h2>");

        String javaVersion = System.getProperty("java.version"); // applet okay
        String arch = System.getProperty("os.arch"); // applet okay
        java.text.DecimalFormat nf = new java.text.DecimalFormat("0.0");
        String mem = nf.format(Runtime.getRuntime().maxMemory()   / 1000000 );
        String tmem= nf.format(Runtime.getRuntime().totalMemory() / 1000000 );
        String fmem= nf.format(Runtime.getRuntime().freeMemory()  / 1000000 );
        String nmem= "???";
        try {
            // taken from https://svn.apache.org/repos/asf/flume/trunk/flume-ng-core/src/main/java/org/apache/flume/tools/DirectMemoryUtils.java
            Class<?> VM = Class.forName("sun.misc.VM");
            Method maxDirectMemory = VM.getDeclaredMethod("maxDirectMemory", new Class[0] );
            Object result = maxDirectMemory.invoke(null, (Object[])null);
            if (result != null && result instanceof Long) {
                nmem= nf.format( ((Long)result) / 1000000 );
            }       
        } catch ( ClassNotFoundException ex ) {
            // do nothing, show ??? for native.
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(AutoplotUI.class.getName()).log(Level.SEVERE, null, ex);
        }
        String pwd= new File("foo.txt").getAbsoluteFile().getParent();
        String pid= getProcessId("???");
        String host= InetAddress.getLocalHost().getHostName();
        String memWarning="";
        if ( ( Runtime.getRuntime().maxMemory() / 1000000 )<700 ) {
            memWarning= "<li> Available RAM is low, severely limiting capabilities (<a href=\"http://autoplot.org/lowMem\">info</a>)";
        }
        String bits= is32bit ? "32" : "64";
        String bitsWarning;
        bitsWarning= is32bit ? "(<a href=\"https://autoplot.org/32bit\">severely limiting capabilities</a>)" : "(recommended)";

        String javaVersionWarning= "";
        Pattern p= Pattern.compile("(\\d+\\.\\d+)\\.\\d+\\_(\\d+)");
        Matcher m= p.matcher(javaVersion);
        if ( m.matches() ) {
            double major= Double.parseDouble( m.group(1) );
            int minor= Integer.parseInt( m.group(2) );
            if ( major<1.8 || ( major==1.8 && minor<102 ) ) {
                javaVersionWarning= "(<a href=\"https://autoplot.org/javaVersion\">limiting access to CDAWeb</a>)";
            } else {
                javaVersionWarning= "(recommended)";
            }
        }

        String autoplotData= AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA );
        String fscache= AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_FSCACHE );
        
        String sandbox;
        if ( model.isSandboxed() ) {
            SecurityManager sm= System.getSecurityManager();
            if ( sm!=null ) {
                if ( sm==Sandbox.getSandboxManager() ) {
                    sandbox= "true";
                } else {
                    sandbox= "true, but not sandbox security manager.";
                }
            } else {
                sandbox= "true, BUT NO SECURITY MANAGER IS PRESENT";
            }
        } else {
            sandbox= "false";
        }
                        
        String aboutContent = "<ul>" +
            "<li>Java version: " + javaVersion + " " + javaVersionWarning + 
            memWarning +    
            "<li>Java home: "+ System.getProperty("java.home") +            
            "<li>max memory (MB): " + mem + " (memory available to process)" +
            "<li>total memory (MB): " + tmem + " (amount allocated to the process)" +
            "<li>free memory (MB): " + fmem + " (amount available before more must be allocated)" + 
            "<li>native memory limit (MB): " + nmem + " (amount of native memory available to the process)" +
            "<li>sandbox: " + sandbox +
            "<li>noCheckCertificates: " + ( HttpsURLConnection.getDefaultHostnameVerifier()==allHostsValid ) +
            "<li>arch: " + arch +
            "<li>release type: " + System.getProperty( AutoplotUI.SYSPROP_AUTOPLOT_RELEASE_TYPE,"???") +
            "<li>" + bits + " bit Java " + bitsWarning  +
            "<li>hostname: "+ host +
            "<li>pid: " + pid +
            "<li>pwd: " + pwd +
            "<li>autoplotData: "+ autoplotData +
            "<li>fscache: "+ fscache +
            "</ul>";
        buffy.append( aboutContent );

        buffy.append("</html>");
        return buffy.toString();

    }
    
    private static final TrustManager[] trustAllCerts = new TrustManager[]{
        new X509TrustManager() {
            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[0];
            }
            @Override
            public void checkClientTrusted(X509Certificate[] certs, String authType) {  }
            @Override
            public void checkServerTrusted(X509Certificate[] certs, String authType) {  }
            @Override
            public String toString() {
                return "AutoplotTrustAllTrustManager";
            }
        }
    };
    
    // Create all-trusting host name verifier
    private static final HostnameVerifier allHostsValid = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
        @Override
        public String toString() {
            return "AutoplotTrustAllHostnamesHostnameManager";
        }
    };
            
    /**
     * disable certificate checking.  A TrustManager and HostnameVerifier which trusts all
     * names and certs is installed.
     */
    public static void disableCertificates() {
        logger.info("disabling HTTP certificate checks.");
        try {
            System.setProperty(AutoplotUI.SYSPROP_AUTOPLOT_DISABLE_CERTS, String.valueOf(true) );
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
            
        } catch (NoSuchAlgorithmException | KeyManagementException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    public static void maybeInitializeEditorColors() {
        File config= new File( new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ) ), "config" );
        if ( !config.exists() ) {
            if ( !config.mkdirs() ) {
                logger.log(Level.WARNING, "mkdir {0} failed", config);
                return;
            }
        }
        File propFile= new File( config, "jsyntaxpane.properties" );
        if ( !propFile.exists() ) {
            try (PrintWriter w = new PrintWriter( new FileWriter( propFile ) )) {                                
                w.println("TokenMarker.Color = 0xffeeaa");
                w.println("PairMarker.Color = 0xffbb77");
                w.println("LineNumbers.Foreground = 0x333300");
                w.println("LineNumbers.Background = 0xeeeeff");
                w.println("LineNumbers.CurrentBack = 0xccccee");
                w.println("CaretColor = 0x000000");
                w.println("Background = 0xFFFFFF");
                w.println("SelectionColor = 0x556677");
                w.println("# These are the various Attributes for each TokenType.");
                w.println("# The keys of this map are the TokenType Strings, and the values are:");
                w.println("# color (hex, or integer), Font.Style attribute");
                w.println("# Style is one of: 0 = plain, 1=bold, 2=italic, 3=bold/italic");
                w.println("Style.OPERATOR = 0x000000, 0");
                w.println("Style.DELIMITER = 0x000000, 1");
                w.println("Style.KEYWORD = 0x3333ee, 0");
                w.println("Style.KEYWORD2 = 0x3333ee, 3");
                w.println("Style.TYPE = 0x000000, 2");
                w.println("Style.TYPE2 = 0x000000, 1");
                w.println("Style.TYPE3 = 0x000000, 3");
                w.println("Style.STRING = 0xcc6600, 0");
                w.println("Style.STRING2 = 0xcc6600, 1");
                w.println("Style.NUMBER = 0x999933, 1");
                w.println("Style.REGEX = 0xcc6600, 0");
                w.println("Style.IDENTIFIER = 0x000000, 0");
                w.println("Style.COMMENT = 0x339933, 2");
                w.println("Style.COMMENT2 = 0x339933, 3");
                w.println("Style.DEFAULT = 0x000000, 0");
                w.println("Style.WARNING = 0xCC0000, 0");
                w.println("Style.ERROR = 0xCC0000, 3");
                w.close();
            } catch ( IOException ex ) {
                logger.log(Level.WARNING, "write initial {0} failed.  {1}", new Object[] { propFile, ex } );
            }
        }
    }

}
