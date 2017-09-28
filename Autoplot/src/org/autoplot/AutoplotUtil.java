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
import org.das2.datum.InconvertibleUnitsException;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.UnknownHostException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Properties;
import java.util.regex.Pattern;
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
import org.das2.datum.DomainDivider;
import org.das2.datum.DomainDividerUtil;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.TimeLocationUnits;
import org.das2.datum.TimeUtil;
import org.das2.datum.UnitsConverter;
import org.das2.graph.ContoursRenderer;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasColorBar;
import org.das2.graph.DasDevicePosition;
import org.das2.graph.DasPlot;
import org.das2.graph.DasRow;
import org.das2.graph.DefaultPlotSymbol;
import org.das2.graph.DigitalRenderer;
import org.das2.graph.EventsRenderer;
import org.das2.graph.ImageVectorDataSetRenderer;
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
import org.das2.util.LoggerManager;
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
import org.das2.qds.DRank0DataSet;
import org.das2.qds.DataSetAnnotations;
import org.das2.qds.QDataSet;
import org.das2.qds.DataSetOps;
import org.das2.qds.DataSetUtil;
import org.das2.qds.JoinDataSet;
import org.das2.qds.QubeDataSetIterator;
import org.das2.qds.RankZeroDataSet;
import org.das2.qds.SemanticOps;
import org.das2.qds.examples.Schemes;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.ReferenceCache;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.WindowManager;
import org.autoplot.datasource.capability.Caching;
import org.das2.graph.BoundsRenderer;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.AutoHistogram;
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

        
    private static void setRange( DDataSet range, DatumRange drange, boolean log ) {
        range.putProperty( QDataSet.UNITS, drange.getUnits() );
        range.putValue( 0,drange.min().doubleValue( drange.getUnits() ) );
        range.putValue( 1,drange.max().doubleValue( drange.getUnits() ) );
        if ( log ) range.putProperty( QDataSet.SCALE_TYPE, "log" );
    }
    
    /**
     * return the bounding qube for the given render type.  This was stolen from Test022.
     * @param dataSet
     * @param renderType
     * @return bounding cube[3,2]
     * @throws Exception 
     */
    public static QDataSet bounds( QDataSet dataSet, RenderType renderType ) throws Exception {

        DDataSet xrange= DDataSet.createRank1(2);
        DDataSet yrange= DDataSet.createRank1(2);
        DDataSet zrange= DDataSet.createRank1(2);

        JoinDataSet result= new JoinDataSet(2);
        result.join( xrange );
        result.join( yrange );
        result.join( zrange );

        Map props= new HashMap();

        if (renderType == RenderType.spectrogram || renderType==RenderType.nnSpectrogram ) {

            QDataSet xds = (QDataSet) dataSet.property(QDataSet.DEPEND_0);
            if (xds == null) {
                if ( dataSet.property(QDataSet.JOIN_0)!=null ) {
                    JoinDataSet ds= new JoinDataSet(2);
                    for ( int i=0; i<dataSet.length(); i++ ) {
                        ds.join((QDataSet)dataSet.property(QDataSet.DEPEND_0,i));
                    }
                    xds = ds;
                } else {
                    xds = DataSetUtil.indexGenDataSet(dataSet.length());
                }
            }

            QDataSet yds = (QDataSet) dataSet.property(QDataSet.DEPEND_1);
            Map<String,Object> yprops= (Map) props.get(QDataSet.DEPEND_1);
            if (yds == null) {
                if ( dataSet.property(QDataSet.JOIN_0)!=null ) {
                    JoinDataSet ds= new JoinDataSet(2);
                    for ( int i=0; i<dataSet.length(); i++ ) {
                        //QDataSet qds= dataSet.slice(i); //TODO: this needs work.
                        //String f= new File("foo.qds").getAbsolutePath();
                        //ScriptContext.formatDataSet( dataSet, f );
                        ds.join((QDataSet)dataSet.property(QDataSet.DEPEND_1,i));
                    }
                    yds = ds;
                } else if ( dataSet.rank()>1 ) {
                    yds = DataSetUtil.indexGenDataSet(dataSet.length(0)); //TODO: QUBE assumed
                } else {
                    yds = DataSetUtil.indexGenDataSet(10); // later the user will get a message "renderer cannot plot..."
                    yprops= null;
                }
            }

            AutoplotUtil.AutoRangeDescriptor xdesc = AutoplotUtil.autoRange(xds, (Map) props.get(QDataSet.DEPEND_0));

            AutoplotUtil.AutoRangeDescriptor ydesc = AutoplotUtil.autoRange(yds, yprops );

            //QDataSet hist= getDataSourceFilter().controller.getHistogram();
            AutoplotUtil.AutoRangeDescriptor desc;

            desc = AutoplotUtil.autoRange( dataSet, props );


            setRange( zrange, desc.range, desc.log );
            setRange( xrange, xdesc.range, xdesc.log );
            setRange( yrange, ydesc.range, ydesc.log );

        } else {

            AutoplotUtil.AutoRangeDescriptor ydesc;

            QDataSet depend0;

            if ( SemanticOps.isBundle(dataSet) ) {
                ydesc= AutoplotUtil.autoRange( DataSetOps.unbundle(dataSet, 1 ), props );
                depend0= DataSetOps.unbundle(dataSet,0);
            } else {
                ydesc = AutoplotUtil.autoRange( dataSet, props );
                depend0 = (QDataSet) dataSet.property(QDataSet.DEPEND_0);
            }

            setRange( yrange, ydesc.range, ydesc.log );

            QDataSet xds= depend0;
            if (xds == null) {
                xds = DataSetUtil.indexGenDataSet(dataSet.length());
            }

            AutoplotUtil.AutoRangeDescriptor xdesc = AutoplotUtil.autoRange(xds, (Map) props.get(QDataSet.DEPEND_0));

            setRange( xrange, xdesc.range, xdesc.log );

            if (renderType == RenderType.colorScatter) {
                AutoplotUtil.AutoRangeDescriptor zdesc;
                if ( dataSet.property(QDataSet.BUNDLE_1)!=null ) {
                    zdesc= AutoplotUtil.autoRange((QDataSet) DataSetOps.unbundle( dataSet, 2 ),null);
                } else {
                    QDataSet plane0= (QDataSet) dataSet.property(QDataSet.PLANE_0);
                    zdesc= AutoplotUtil.autoRange(plane0,
                        (Map) props.get(QDataSet.PLANE_0));
                }

                setRange( zrange, zdesc.range, zdesc.log );

            }

        }

        for ( int i=0; i<result.length(); i++  ) {
           Units u= (Units) result.property(QDataSet.UNITS,i);
           if ( u!=null ) {
               DatumRange dr= DatumRange.newDatumRange( result.value(i,0), result.value(i,1), u );
               logger.log(Level.FINER, "{0}: {1}", new Object[]{i, dr});
           } else {
               logger.log(Level.FINER, "{0}: {1},{2}", new Object[]{i, result.value(i,0), result.value(i,1)});
           }
           
        }

        return result;
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
                w.close();
            } catch ( IOException ex ) {
                logger.log(Level.WARNING, "write initial {0} failed.  {1}", new Object[] { propFile, ex } );
            }
        } else {
            logger.log( Level.FINER, "loading %s", propFile );
            Properties props= new Properties();
            InputStream in=null;
            try {
                in = new FileInputStream(propFile);
                props.load( in );
                in.close();
                for ( Entry p: props.entrySet()) {
                    logger.log( Level.FINEST, "%s=%s", new Object[] { p.getKey(), p.getValue() } );
                    System.setProperty( (String)p.getKey(), (String)p.getValue() );
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            } finally {
                if ( in!=null ) {
                    try {
                        in.close();
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, ex.getMessage(), ex);
                    }
                }
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
     * legacy class for describing the results of the autorange routine.
     * Note that QDataSet bounding cubes provide the same functionality.
     */
    public static class AutoRangeDescriptor {

        public DatumRange range;
        public boolean log;
        private double robustMin;
        private double robustMax;
        private double median;

        @Override
        public String toString() {
            return "" + range + " " + (log ? "log" : "");
        }
    }

    private static DatumRange getRange(Number min, Number max, Units units) {
        if (units != null && UnitsUtil.isTimeLocation(units)) {
            if (min == null) min = Units.mj1958.convertDoubleTo(units, -100000);
            if (max == null) max = Units.mj1958.convertDoubleTo(units, 100000);
        } else {
            if (min == null) min = Double.NEGATIVE_INFINITY;
            if (max == null) max = Double.POSITIVE_INFINITY;
            if (units == null) units = Units.dimensionless;
        }
        if ( UnitsUtil.isTimeLocation(units) ) {
            TimeLocationUnits tu= (TimeLocationUnits) units;
            if ( ! tu.isValid(min.doubleValue() ) ) min= tu.validMin();
            if ( ! tu.isValid(max.doubleValue() ) ) max= tu.validMax();
            return new DatumRange( min.doubleValue(), max.doubleValue(), units );
        } else {
            try {
                return new DatumRange(min.doubleValue(), max.doubleValue(), units);
            } catch ( IllegalArgumentException ex ) {
                System.err.println("here here");
                throw ex;
            }
        }
    }

    private static DatumRange makeDimensionless(DatumRange dr) {
        Units u = dr.getUnits();
        return new DatumRange(dr.min().doubleValue(u),
                dr.max().doubleValue(u),
                Units.dimensionless);
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
    public static AutoRangeDescriptor autoRange(QDataSet hist, QDataSet ds, Map properties) {

        Logger logger1= LoggerManager.getLogger("qdataset.ops.autorange");
                
        logger1.log(Level.FINE, "enter autoRange {0}", ds);
        logger1.entering("org.virbo.autoplot.AutoplotUtil", "autoRange" );

        Units u = (Units) ds.property(QDataSet.UNITS);
        if (u == null) {
            u = Units.dimensionless;
        }

        AutoRangeDescriptor result = new AutoRangeDescriptor();

        double[] dd;

        boolean mono = Boolean.TRUE.equals(ds.property(QDataSet.MONOTONIC)) || null != ds.property(QDataSet.CADENCE);

        long total = (Long) ((Map) hist.property(QDataSet.USER_PROPERTIES)).get(AutoHistogram.USER_PROP_TOTAL);

        double median = Double.NaN;

        if (mono) {
            RankZeroDataSet cadence = DataSetUtil.guessCadenceNew(ds, null);
            if (cadence == null || cadence.value() > Double.MAX_VALUE / 100)
                cadence = DRank0DataSet.create(0.);
            if (ds.length() > 1) {
                double min = Math.min(ds.value(0), ds.value(ds.length() - 1));
                double max = Math.max(ds.value(0), ds.value(ds.length() - 1));
                double dcadence = Math.abs(cadence.value());
                if ("log".equals(cadence.property(QDataSet.SCALE_TYPE))) {
                    Units cu = (Units) cadence.property(QDataSet.UNITS);
                    double factor = (cu.convertDoubleTo(Units.percentIncrease, dcadence) + 100) / 100.;
                    dd = new double[]{min / factor, max * factor};
                } else {
                    dd = new double[]{min - dcadence, max + dcadence};
                }
            } else {
                if (UnitsUtil.isTimeLocation(u)) {
                    dd = new double[]{0, Units.days.createDatum(1).doubleValue(u.getOffsetUnits())};
                } else {
                    dd = new double[]{0, 1};
                }
            }
            median = (dd[0] + dd[1]) / 2;
        } else {
            dd = new double[]{Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            QDataSet dep0 = (QDataSet) hist.property(QDataSet.DEPEND_0);
            QDataSet cadence = DataSetUtil.guessCadenceNew(dep0, null);
            int tot = 0;
            for (int i = 0; i < hist.length(); i++) {
                tot += hist.value(i);
                if (dd[0] == Double.NEGATIVE_INFINITY && hist.value(i) > 0) {
                    dd[0] = dep0.value(i);
                }
                if (hist.value(i) > 0) {
                    dd[1] = dep0.value(i) + cadence.value();  // TODO: log10
                }
                if (tot >= total / 2) {
                    median = dep0.value(i);
                }
            }
        }

        if (total < 3) {
            result.median = median;
            result.range = DatumRange.newDatumRange(dd[0], dd[1], u);
            result.robustMin = dd[0];
            result.robustMax = dd[1];

        } else {
            result.median = median;
            result.robustMin = dd[0];
            result.robustMax = dd[1];

            double nomMin, nomMax;
            if (mono) {
                nomMin = ds.value(0);
                nomMax = ds.value(ds.length() - 1);
            } else {
                nomMin = dd[0];
                nomMax = dd[1];
            }

            // lin/log logic: in which space is ( median - min5 ) more equal to ( max5 - median )?  Also, max5 / min5 > 1e3
            double clin = (nomMax - result.median) / (result.median - nomMin);
            if (clin > 1.0) {
                clin = 1 / clin;
            }
            double clog = (nomMax / result.median) / Math.abs(result.median / nomMin);
            if (clog > 1.0) {
                clog = 1 / clog;
            }

            if (clog > clin && nomMax / nomMin > 1e2) {
                result.log = true;
            }

            result.range = DatumRange.newDatumRange(result.robustMin, result.robustMax, u);

        }

        if ("log".equals(ds.property(QDataSet.SCALE_TYPE))) {
            result.log = true;
        }

        // interpret properties, looking for hints about scale type and ranges.
        if (properties != null) {
            String log1 = (String) properties.get(QDataSet.SCALE_TYPE);
            if (log1 != null) {
                result.log = log1.equals("log");
            }
            Number tmin = (Number) properties.get(QDataSet.TYPICAL_MIN);
            Number tmax = (Number) properties.get(QDataSet.TYPICAL_MAX);
            DatumRange range = getRange(
                    (Number) properties.get(QDataSet.TYPICAL_MIN),
                    (Number) properties.get(QDataSet.TYPICAL_MAX),
                    (Units) properties.get(QDataSet.UNITS));
            // see if the typical extent is consistent with extent seen.  If the
            // typical extent won't hide the data's structure, then use it.
            if ((tmin != null || tmax != null)) {
                double d1, d2;
                if (result.log) {
                    try {
                        Datum dd1 = result.range.min().ge(range.min()) ? result.range.min() : range.min();
                        Datum dd2 = result.range.max().ge(range.min()) ? result.range.max() : range.min();
                        d1 = DatumRangeUtil.normalizeLog(range, dd1);
                        d2 = DatumRangeUtil.normalizeLog(range, dd2);
                    } catch (InconvertibleUnitsException ex) {
                        range = makeDimensionless(range);
                        result.range = makeDimensionless(result.range);
                        Datum dd1 = result.range.min().ge(range.min()) ? result.range.min() : range.min();
                        Datum dd2 = result.range.max().ge(range.min()) ? result.range.max() : range.min();
                        d1 = DatumRangeUtil.normalizeLog(range, dd1);
                        d2 = DatumRangeUtil.normalizeLog(range, dd2);
                    }
                } else {
                    try {
                        d1 = DatumRangeUtil.normalize(range, result.range.min());
                        d2 = DatumRangeUtil.normalize(range, result.range.max());
                    } catch (InconvertibleUnitsException ex) {
                        range = makeDimensionless(range);
                        result.range = makeDimensionless(result.range);
                        d1 = DatumRangeUtil.normalize(range, result.range.min());
                        d2 = DatumRangeUtil.normalize(range, result.range.max());
                    }
                }
                if (d2 - d1 > 0.1 // the stats range occupies 10% of the typical range
                        && d2 > 0. // and the stats max is greater than the typical range min()
                        && d1 < 1.) {  // and the stats min is less then the typical range max().
                    result.range = range;
                    // just use the metadata settings.

                    logger1.exiting("org.virbo.autoplot.AutoplotUtil", "autoRange" );
                    return result; // DANGER--EXIT POINT

                }
            }

        }
        
         // round out to frame the data with empty space, so that the data extent is known.
        if (UnitsUtil.isRatioMeasurement(u) || UnitsUtil.isIntervalMeasurement(u)) {
            if (result.log) {
                if (result.robustMin <= 0.0)
                    result.robustMin = result.robustMax / 1e3;
                result.range = DatumRange.newDatumRange(Math.pow(10,Math.floor(Math.log10(result.robustMin))),
                        Math.pow(10,Math.ceil(Math.log10(result.robustMax))), u);
            } else {
                result.range = DatumRange.newDatumRange(result.robustMin, result.robustMax, u);
                if (result.robustMin < result.robustMax)
                    result.range = DatumRangeUtil.rescale(result.range, -0.05, 1.05);
                if (result.robustMin == 0 && result.robustMax == 0)
                    result.range = DatumRange.newDatumRange(-0.1, 1.0, u);
            }
        } else {
            result.range = DatumRange.newDatumRange(result.robustMin, result.robustMax, u);
        }
        
        logger1.exiting("org.virbo.autoplot.AutoplotUtil", "autoRange" );
        return result;
    }

    /**
     * convert the legacy AutoRangeDescriptor to a QDataSet bounding cube.
     * The bounding cube is a rank 1, 2-element dataset with min and max as the
     * elements, and SCALE_TYPE="log" if the AutoRangeDescriptor log property was
     * true.
     * @param ard AutoRangeDescriptor.
     * @return a rank 1 bounding cube.
     */
    public static QDataSet toDataSet( AutoRangeDescriptor ard ) {
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

        boolean result= true;
        Axis axis= dom.getController().getPlot().getYaxis();

        List<PlotElement> pes= dom.getController().getPlotElementsFor(plot);

        DatumRange range= null;
        for ( PlotElement pe: pes ) {
            if ( pe.isActive()==false ) continue;
            QDataSet ds= pe.getController().getDataSet();
            if ( ds!=null ) {
                ds= SemanticOps.trim( ds, plot.getXaxis().getRange(), null );
                PlotElement pcopy1= (PlotElement)pe.copy();
                PlotElementController.doAutoranging(pcopy1, Collections.singletonMap( QDataSet.SCALE_TYPE, (Object)( axis.isLog() ? "log" : "linear" ) ), ds, true ); // :) cast to Object!
                if ( range==null ) {
                    range= pcopy1.getPlotDefaults().getYaxis().getRange();
                } else {
                    range= DatumRangeUtil.union( range, pcopy1.getPlotDefaults().getYaxis().getRange() );
                }
            }
        }
        if ( range!=null ) axis.setRange(range);

        return result;
    }

    public static boolean resetZoomX( Application dom ) {
        Plot plot= dom.getController().getPlot();

        boolean result= true;
        Axis axis= dom.getController().getPlot().getXaxis();

        List<PlotElement> pes= dom.getController().getPlotElementsFor(plot);

        DatumRange range= null;
        for ( PlotElement pe: pes ) {
            if ( pe.isActive()==false ) continue;
            QDataSet ds= pe.getController().getDataSet();
            if ( ds!=null ) {
                ds= SemanticOps.trim( ds, null, plot.getYaxis().getRange() );
                PlotElement pcopy1= (PlotElement)pe.copy(); // TODO: something ain't right below...
                PlotElementController.doAutoranging(pcopy1, Collections.singletonMap( QDataSet.SCALE_TYPE, (Object)( axis.isLog() ? "log" : "linear" ) ), ds, true ); // :) cast to Object!
                if ( range==null ) {
                    range= pcopy1.getPlotDefaults().getXaxis().getRange();
                } else {
                    range= DatumRangeUtil.union( range, pcopy1.getPlotDefaults().getXaxis().getRange() );
                }
            }
        }
        if ( range!=null ) axis.setRange(range);

        return result;
    }


    public static boolean resetZoomZ( Application dom ) {
        Plot plot= dom.getController().getPlot();

        boolean result= true;
        Axis axis= dom.getController().getPlot().getZaxis();

        List<PlotElement> pes= dom.getController().getPlotElementsFor(plot);

        DatumRange range= null;
        for ( PlotElement pe: pes ) {
            if ( pe.isActive()==false ) continue;
            if ( !RenderTypeUtil.needsColorbar(pe.getRenderType()) ) continue;
            QDataSet ds= pe.getController().getDataSet();
            if ( ds!=null ) {
                ds= SemanticOps.trim( ds, plot.getXaxis().getRange(), plot.getYaxis().getRange() );
                PlotElement pcopy1= (PlotElement)pe.copy();
                PlotElementController.doAutoranging(pcopy1, Collections.singletonMap( QDataSet.SCALE_TYPE, (Object)( axis.isLog() ? "log" : "linear" ) ), ds, true ); // :) cast to Object!
                if ( range==null ) {
                    range= pcopy1.getPlotDefaults().getZaxis().getRange();
                } else {
                    range= DatumRangeUtil.union( range, pcopy1.getPlotDefaults().getZaxis().getRange() );
                }
            }
        }
        if ( range!=null ) axis.setRange(range);

        return result;
    }

    /**
     * Autorange using the dataset properties
     * @param ds the dataset, a non-bundle, to be autoranged.
     * @param properties Additional constraints for properties, such as SCALE_TYPE
     * @return  the range.
     * @see #autoRange(org.das2.qds.QDataSet, java.util.Map, boolean) 
     */
    public static AutoRangeDescriptor autoRange( QDataSet ds, Map properties ) {
        return autoRange( ds, properties, false );
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
     */
    public static AutoRangeDescriptor autoRange(QDataSet ds, Map properties, boolean ignoreDsProps) {

        Logger logger1= LoggerManager.getLogger("qdataset.ops.autorange");
        
        logger1.entering( "org.virbo.autoplot.AutoplotUtil", "autoRange", ds );

        Units u = (Units) ds.property(QDataSet.UNITS);
        if (u == null) {
            if ( ds.property(QDataSet.JOIN_0)!=null ) {
                if ( ds.length()==0 ) throw new IllegalArgumentException("dataset is empty");
                u = (Units) ds.property(QDataSet.UNITS,0);
            } 
            if ( u==null ) {
                u = Units.dimensionless;
            }
        }

        AutoRangeDescriptor result = new AutoRangeDescriptor();

        // handle ordinal units by simply returning the range.
        if ( UnitsUtil.isOrdinalMeasurement(u) || UnitsUtil.isNominalMeasurement(u) ) {
            QDataSet ext= Ops.extent(ds);
            result.range= DataSetUtil.asDatumRange(ext,true);
            result.robustMin= result.range.min().doubleValue(u);
            result.robustMax= result.range.max().doubleValue(u);
            logger1.exiting("org.virbo.autoplot.AutoplotUtil", "autoRange", ds );
            return result;
        }

        
        double[] dd; // two-element array that is the min and max of the data.

        boolean mono = Boolean.TRUE.equals(ds.property(QDataSet.MONOTONIC));
        if ( null != ds.property(QDataSet.CADENCE) ) {
            if ( DataSetUtil.isMonotonic(ds) ) {
                mono= true;
            }
        }
        if ( ds.rank()!=1 ) mono= false; //TODO: bins scheme
        
        // these are from the dataset metadata.
        AutoRangeDescriptor typical= null;

        // the autoranging will be in log space only if the data are not time locations.
        boolean isLog= "log".equals(ds.property(QDataSet.SCALE_TYPE)) && !UnitsUtil.isTimeLocation(u);
        boolean isLin= "linear".equals(ds.property(QDataSet.SCALE_TYPE)) || UnitsUtil.isTimeLocation(u);

        if ( !ignoreDsProps ) {
            Number typicalMin= (Number)ds.property(QDataSet.TYPICAL_MIN);
            Number typicalMax= (Number)ds.property(QDataSet.TYPICAL_MAX);
            if ( typicalMin!=null && typicalMax!=null ) { // TODO: support just typicalMin or typicalMax...
                typical= new AutoRangeDescriptor();
                typical.range= new DatumRange( typicalMin.doubleValue(), typicalMax.doubleValue(), u );
                typical.log= isLog;
            }
        }

        if ( properties!=null && "log".equals(properties.get(QDataSet.SCALE_TYPE)) && !UnitsUtil.isTimeLocation(u) ) {
            isLog= true;
        }

        if ( typical==null && SemanticOps.isJoin(ds) ) {
            result.range= null;
            result.robustMax= -1* Double.MAX_VALUE;
            result.robustMin= Double.MAX_VALUE;

            Units units=null;
            UnitsConverter uc= UnitsConverter.IDENTITY;
            for ( int i=0; i<ds.length(); i++ ) {
                AutoRangeDescriptor r1= autoRange( ds.slice(i), properties, false );
                if ( units==null ) {
                    units= r1.range.getUnits();
                } else {
                    uc= r1.range.getUnits().getConverter(units);
                }
                result.range= result.range==null ? r1.range : DatumRangeUtil.union( result.range, r1.range );
                if ( r1.log ) result.log= true;
            }
            result.robustMin= result.range.min().doubleValue(result.range.getUnits());
            result.robustMax= result.range.max().doubleValue(result.range.getUnits());
            logger1.exiting("org.virbo.autoplot.AutoplotUtil", "autoRange", ds );
            return result;
        }

        if (mono && ds.rank()==1 ) { //TODO: support bins scheme
            RankZeroDataSet cadence = DataSetUtil.guessCadenceNew(ds, null);
            QDataSet wds= DataSetUtil.weightsDataSet(ds); // use weights rather than checking for fill and valid range.  The weights datset will reflect this information.
            if (cadence == null || cadence.value() > Double.MAX_VALUE / 100)
                cadence = DRank0DataSet.create(0.);
            if (ds.length() > 1) {
                int firstValid=0;
                while ( firstValid<wds.length() && wds.value(firstValid)==0 ) firstValid++;
                if ( firstValid==wds.length() ) throw new IllegalArgumentException("data contains no valid measurements");
                int lastValid=wds.length()-1;
                while ( lastValid>=0 && wds.value(lastValid)==0 ) lastValid--;
                if ( ( lastValid-firstValid+1 ) == 0 ) {
                    logger1.fine("special case where monotonic dataset contains no valid data");
                    if (UnitsUtil.isTimeLocation(u)) {
                        dd = new double[]{0, Units.days.createDatum(1).doubleValue(u.getOffsetUnits())};
                    } else {
                        dd = new double[]{0, 1};
                    }
                } else {
                    double min = Math.min(ds.value(firstValid), ds.value(lastValid));
                    double max = Math.max(ds.value(firstValid), ds.value(lastValid));
                    double dcadence = Math.abs(cadence.value());
                    if ( isLog ) {
                        Units cu = (Units) cadence.property(QDataSet.UNITS);
                        if ( cu==null ) cu= Units.dimensionless;
                        if ( UnitsUtil.isRatiometric(cu) ) {
                            double factor = (cu.convertDoubleTo(Units.percentIncrease, dcadence) + 100) / 100.;
                            dd = new double[]{min / factor, max * factor};
                        } else {
                            if ( cu.isConvertibleTo(u.getOffsetUnits() ) ) { // TODO: we need separate code to make datasets valid
                                dcadence= cu.convertDoubleTo( u.getOffsetUnits(), dcadence );
                                dd = new double[]{min - dcadence, max + dcadence};
                                if ( dd[0]<0 ) {
                                    dd[0]= min / 2.; // this is a fall-back mode
                                }
                            } else {
                                dd = new double[]{min, max};
                            }
                        }
                    } else {
                        dd = new double[]{min - dcadence, max + dcadence};
                        try {
                            logger1.log(Level.FINEST, "range of monotonic set by min to max, extended by cadence: {0}", DatumRange.newDatumRange( dd[0], dd[1], u ));
                        } catch ( RuntimeException ex ) {
                            // don't muck up the production release with unforeseen runtime exception.  TODO: remove me.
                        }
                    }
                }
            } else if ( ds.length()==1 ) {
                dd = simpleRange(ds);
                //QDataSet ddds= study445FastRange(ds);
                //dd = new double[] { ddds.value(0), ddds.value(1) };
                if (UnitsUtil.isTimeLocation(u)) {
                    dd = new double[]{ dd[0], dd[0]+Units.days.createDatum(1).doubleValue(u.getOffsetUnits()) };
                } else {
                    dd = new double[]{ dd[0], dd[0]+1};
                }
            } else {
                //dd = simpleRange(ds);
                if (UnitsUtil.isTimeLocation(u)) {
                    dd = new double[]{0, Units.days.createDatum(1).doubleValue(u.getOffsetUnits())};
                } else {
                    dd = new double[]{0, 1};
                }
            }
        } else {
            // find min and max of three-point medians
            try {
                dd = simpleRange(ds);
                //QDataSet ddds= study445FastRange(ds);
                //dd = new double[] { ddds.value(0), ddds.value(1) };
                logger1.log(Level.FINEST, "simpleRange(ds)= {0} - {1}", new Object[]{dd[0], dd[1]});
                if ( Units.dimensionless.isFill(dd[0]) ) dd[0]= dd[0] / 100; // kludge for LANL_1991_080_H0_SOPA_ESP_19920308_V01.cdf?FEDO
                if ( Units.dimensionless.isFill(dd[1]) ) dd[1]= dd[1] / 100; // work around 2009 bug where DatumRanges cannot contain -1e31.
            } catch (IllegalArgumentException ex) {
                logger.log(Level.WARNING,ex.getMessage(),ex);
                if (UnitsUtil.isTimeLocation(u)) {
                    dd = new double[]{0, Units.days.createDatum(1).doubleValue(u.getOffsetUnits())};
                } else {
                    dd = new double[]{0, 1};
                }
            }
        }

        // bad things happen if we have time locations that don't vary, so here's some special code to avoid that.
        if ( UnitsUtil.isTimeLocation(u) && dd[0]==dd[1] ) {  // round out to a day if the times are the same.
            if ( dd[0]<=-1e29 ) throw new IllegalArgumentException("timetags are all invalid ");
            Units du= u.getOffsetUnits();
            double d= Units.days.convertDoubleTo( du, 1. );
            dd[0]= Math.floor( dd[0] / d ) * d;
            dd[1]= dd[0] + d;
        }


        double median;
        int total;
        double positiveMin;
        boolean isHist= false;

        if (dd[0] == dd[1]) {
            if (dd[0] == 0) {
                dd[0] = -1;
                dd[1] = +1;
            } else if (dd[0] > 0) {
                dd[0] = 0;
            } else {
                dd[1] = 0;
            }
            median = (dd[0] + dd[1]) / 2;
            positiveMin= ( dd[0] + ( dd[1]-dd[0] ) * 0.1 ); //???
            total = ds.length(); // only non-zero is checked.
        } else {
            // find the median by looking at the histogram.  If the dataset should be log, then the data will bunch up in the lowest bins.
            isHist= "stairSteps".equals( ds.property( QDataSet.RENDER_TYPE) ); // nasty bit of code
            double binSize= (dd[1] - dd[0]) * 0.01;
            QDataSet hist = DataSetOps.histogram(ds, dd[0] - binSize/2, dd[1] + binSize/2, (dd[1] - dd[0]) / 100);
            positiveMin= ((Double) hist.property("positiveMin"));
            total = 0;
            for (int i = 0; i < hist.length(); i++) {
                total += hist.value(i);
            }
            median = u.getFillDouble();
            int total50 = 0;
            for (int i = 0; i < hist.length(); i++) {
                total50 += hist.value(i);
                if (total50 >= total / 2) {
                    median = ((QDataSet) hist.property(QDataSet.DEPEND_0)).value(i);
                    break;
                }
            }
        }

        if (total < 3) {
            result.median = median;
            result.robustMin = dd[0];
            result.robustMax = dd[1];

            if ( UnitsUtil.isTimeLocation(u) ) {
                double dmin= TimeUtil.createTimeDatum( 1000, 1, 1, 0, 0, 0, 0 ).doubleValue(u); // years from 1000A.D.
                double dmax= TimeUtil.createTimeDatum( 9000, 1, 1, 0, 0, 0, 0 ).doubleValue(u); // years to 9000A.D.
                if ( result.robustMin>dmax ) result.robustMin= dmax;
                if ( result.robustMin<dmin ) result.robustMin= dmin;
                if ( result.robustMax>dmax ) result.robustMax= dmax;
                if ( result.robustMax<dmin ) result.robustMax= dmin;
            }

            result.range = DatumRange.newDatumRange(result.robustMin, result.robustMax, u);

        } else {
            result.median = median;
            result.robustMin = dd[0];
            result.robustMax = dd[1];

            double nomMin, nomMax;
            
            if (mono) {
                // nomMin= dd[0]; nomMax=dd[1]   //TODO: the following two lines assume there is no fill and it is monotonically increasing.
                nomMin = ds.value(0);
                nomMax = ds.value(ds.length() - 1);
            } else {
                nomMin = dd[0];
                nomMax = dd[1];
            }

            // lin/log logic: in which space is ( median - nomMin ) more equal to ( nomMax - median )?  Also, nomMax / nomMin > 1e3
            double clin = (nomMax - result.median) / (result.median - nomMin);
            if (clin > 1.0) {
                clin = 1 / clin;
            }
            if ( !isLin && result.median>0 && !org.das2.datum.UnitsUtil.isTimeLocation(u) ) {
                double clog = (nomMax / result.median) / Math.abs(result.median / nomMin);
                if (clog > 1.0) {
                    clog = 1 / clog;
                }

                if (clog > clin && nomMax / nomMin > 1e2) {
                    isLog = true;
                }
            }

            //double normalMedianLog = ( result.median / positiveMin ) / ( nomMax / positiveMin );

            if ( !isLin && !isHist && result.median==0 && nomMin==0 && nomMax/positiveMin>1e3 ) {  // this is where they are bunched up at zero.
                isLog= true;
                result.robustMin= positiveMin/10;
            }

            if ( UnitsUtil.isTimeLocation(u) ) {
                double dmin= TimeUtil.createTimeDatum( 1000, 1, 1, 0, 0, 0, 0 ).doubleValue(u); // years from 1000A.D.
                double dmax= TimeUtil.createTimeDatum( 9000, 1, 1, 0, 0, 0, 0 ).doubleValue(u); // years to 9000A.D.
                if ( result.robustMin>dmax ) result.robustMin= dmax;
                if ( result.robustMin<dmin ) result.robustMin= dmin;
                if ( result.robustMax>dmax ) result.robustMax= dmax;
                if ( result.robustMax<dmin ) result.robustMax= dmin;
            } 
            result.range = DatumRange.newDatumRange(result.robustMin, result.robustMax, u);

        }

        result.log = isLog;

        // interpret properties, looking for hints about scale type and ranges.
        if ( properties != null ) {

            Number tmin = (Number) properties.get(QDataSet.TYPICAL_MIN);
            Number tmax = (Number) properties.get(QDataSet.TYPICAL_MAX);

            Units uu=  (Units) properties.get(QDataSet.UNITS);
            if ( uu==null ) uu= Units.dimensionless;
            if ( UnitsUtil.isIntervalOrRatioMeasurement(uu) ) {

                Datum ftmin=  uu.createDatum( tmin==null ? -1 * Double.MAX_VALUE : tmin );

                if ( isLog && tmin!=null && tmin.doubleValue()<=0 ) {
    //                tmin= new Double( result.range.min().doubleValue(result.range.getUnits()) );
    //                if ( tmin.doubleValue()<0 ) {
                      tmin= tmax.doubleValue() / 1e4; // this used to happen in IstpMetadataModel
    //                }
                }

                DatumRange range = getRange( tmin, tmax, uu );

                // see if the typical extent is consistent with extent seen.  If the
                // typical extent won't hide the data's structure, then use it.
                if ((tmin != null && tmax != null)) {
                    double d1, d2;
                    if (result.log) {
                        if ( ftmin.doubleValue(uu)<=0 ) ftmin= uu.createDatum(1e-38);
                        Datum limit= ftmin;
                        try {
                            Datum dd1 = result.range.min().ge(limit) ? result.range.min() : limit; // these represent the range seen, guard against min
                            Datum dd2 = result.range.max().ge(limit) ? result.range.max() : limit;
                            d1 = DatumRangeUtil.normalizeLog(range, dd1);
                            d2 = DatumRangeUtil.normalizeLog(range, dd2);
                        } catch (InconvertibleUnitsException ex) {
                            range = makeDimensionless(range);
                            result.range = makeDimensionless(result.range);
                            Datum dd1 = result.range.min().ge(range.min()) ? result.range.min() : range.min();
                            Datum dd2 = result.range.max().ge(range.min()) ? result.range.max() : range.min();
                            d1 = DatumRangeUtil.normalizeLog(range, dd1);
                            d2 = DatumRangeUtil.normalizeLog(range, dd2);
                        }
                        if ( d2>1.2 && d2<2.0 ) { // see if we can save TYPICAL_MIN by doubling range
                            logger1.log(Level.FINE, "TYPICAL_MAX rejected because max ({0}) outside the value of TYPICAL range ({1})", new Object[]{ result.range.max(), range } );
                            range= DatumRangeUtil.rescaleLog( range, 0, 1.333 );
                            DatumRange range2= DatumRangeUtil.rescaleLog( range, 0, 2 );
                            d2= d2/1.333;
                            d1= d1/1.333;
                            logger1.fine("adjusting TYPICAL_MAX from metadata, multiply by 1.2");
                            if ( d2>1.2 && d2<2.0 ) { // do what we used to do.
                                range= range2;
                                d2= d2*1.333/2;
                                d1= d1*1.333/2;
                                logger1.fine("adjusting TYPICAL_MAX from metadata, multiply by 2.0");
                            }
                        }
                        if ( d1<-4 && d2>0  ) { //often with log we get "1 count" averages that are very small (demo2: po_h0_hyd_$Y$m$d_v01.cdf)
                            logger1.fine("rejecting statistical range because min is too small.");
                            result.range = range;
                            result.robustMin= range.min().doubleValue(result.range.getUnits());
                            result.robustMax= range.max().doubleValue(result.range.getUnits());
                            d1= 0;
                            d2= 1;
                        }
                    } else {
                        try {
                            d1 = DatumRangeUtil.normalize(range, result.range.min());
                            d2 = DatumRangeUtil.normalize(range, result.range.max());
                        } catch (InconvertibleUnitsException ex) {
                            range = makeDimensionless(range);
                            result.range = makeDimensionless(result.range);
                            d1 = DatumRangeUtil.normalize(range, result.range.min());
                            d2 = DatumRangeUtil.normalize(range, result.range.max());
                        }
                        if ( d2>1.2 && d2<2.0 ) { // see if we can save TYPICAL_MIN by doubling range //TODO: I don't understand this...
                            range= DatumRangeUtil.rescale( range, 0, 2 );
                            d2= d2/2;
                            d1= d1/2;
                            logger1.fine("adjusting TYPICAL_MAX from metadata, multiply by 2.0");
                        }
                    }
                    if (d2 - d1 > 0.1    // the stats range occupies 10% of the typical range
                            && d2 > 0.   // and the stats max is greater than the typical range min()
                            && d2 < 1.14  // and the top isn't clipping data badly  //TODO: we really need to be more robust about this.  hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ION_DIFFERENTIAL_ENERGY_FLUX&timerange=20000109 was failing because a small number of points was messing this up.
                            && d1 > -0.1 // and the bottom isn't clipping data badly
                            && d1 < 1.   // and the stats min is less then the typical range max().
                            && uu.isConvertibleTo( u ) ) {  // and we ARE talking about the same thing
                        result.range = range;
                        // just use the metadata settings.
                        logger1.fine("using TYPICAL_MIN, TYPICAL_MAX from metadata");
                        logger1.exiting("org.virbo.autoplot.AutoplotUtil", "autoRange", ds );
                        return result; // DANGER--EXIT POINT
                    } else {
                        logger1.log(Level.FINE, "TYPICAL_MIN={0} and TYPICAL_MAX={1} from metadata rejected because it clipped or squished the data {2}", new Object[]{tmin.toString(), tmax.toString(), result.range});
                    }
                }
            }
        }

        // round out to frame the data with empty space, so that the data extent is known.
        if (UnitsUtil.isIntervalOrRatioMeasurement(u) ) {
            if (result.log) {
                if (result.robustMax <= 0.0 || Double.isNaN(result.robustMax) ) result.robustMax = 1000;
                if (result.robustMin <= 0.0 || Double.isNaN(result.robustMin) ) result.robustMin = result.robustMax / 1e3;
                Datum min= u.createDatum(result.robustMin);
                Datum max= u.createDatum(result.robustMax );
                DomainDivider div= DomainDividerUtil.getDomainDivider(
                        min, max, true );
                while ( div.boundaryCount( min, max ) > 40 ) {
                    div= div.coarserDivider(false);
                }
                while ( div.boundaryCount( min, max ) < 20 ) {
                    div= div.finerDivider(true);
                }
                result.range = new DatumRange( div.rangeContaining(min).min(), div.rangeContaining(max).max() );
            } else if ( UnitsUtil.isTimeLocation(u) ) {
                if ( result.range.min().doubleValue( Units.us2000 ) > -6.3113480E15 ) {  //TODO: Julian has yr1800 limit.
                    if ( result.range.width().value()==0. ) {
                        result.range = new DatumRange( result.range.min(), result.range.min().add( Units.seconds.createDatum(1) ) );
                    } else {
                        DomainDivider div= DomainDividerUtil.getDomainDivider( result.range.min(), result.range.max() );
                        while ( div.boundaryCount( result.range.min(), result.range.max() ) > 40 ) {
                            div= div.coarserDivider(false);
                        }
                        while ( div.boundaryCount( result.range.min(), result.range.max() ) < 20 ) {
                            div= div.finerDivider(true);
                        }
                        result.range = new DatumRange(
                                div.rangeContaining(result.range.min()).min(),
                                div.rangeContaining(result.range.max()).max() );
                    }
                }

            } else {
                result.range = DatumRange.newDatumRange(result.robustMin, result.robustMax, u);
                if (result.robustMin < result.robustMax)
                    result.range = DatumRangeUtil.rescale(result.range, -0.05, 1.05);
                if (result.robustMin == 0 && result.robustMax == 0)
                    result.range = DatumRange.newDatumRange(-0.1, 1.0, u);
            }
        } else {
            result.range = DatumRange.newDatumRange(result.robustMin, result.robustMax, u);
        }

        logger1.exiting("org.virbo.autoplot.AutoplotUtil", "autoRange", ds );

        if ( typical!=null ) {
            if ( result.log && typical.log ) {
                if ( typical.range.min().doubleValue(typical.range.getUnits() )<=0 ) typical.range= new DatumRange( result.range.min(), typical.range.max() );
                if ( result.range.intersects( typical.range ) ) {
                    double overlap= DatumRangeUtil.normalizeLog( result.range, typical.range.max() )
                            - DatumRangeUtil.normalizeLog( result.range, typical.range.min() );
                    if ( overlap>0.01 && overlap<100 ) return typical;
                }
            } else {
                if ( typical.log==false ) {
                    if ( result.range.intersects( typical.range ) ) {
                        double overlap=
                                DatumRangeUtil.normalize( result.range, typical.range.max() )
                                - DatumRangeUtil.normalize( result.range, typical.range.min() );
                        if ( overlap>0.01 && overlap<100 ) return typical;
                    }
                }
            }
        }        
        return result;
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
     * return simple extent by only including points consistent with adjacent points.
     * also considers delta_plus, delta_minus properties.
     * TODO: /home/jbf/ct/autoplot/script/study/rfe445_speed/verifyExtentSimpleRange.jy showed that this was 25% slower than extent.
     * TODO: this is almost 800% slower than study445FastRange (above), which shows DataSetIterator is slow.
     * 
     * @param ds rank N dataset
     * @return double[min,max].
     */
    private static double[] simpleRange(QDataSet ds) {
        QDataSet max = ds;
        QDataSet min = ds;
        Units u = (Units) ds.property(QDataSet.UNITS);
        if (u == null) u = Units.dimensionless;

        QDataSet delta;
        delta = (QDataSet) ds.property(QDataSet.DELTA_PLUS);
        if (delta != null) {
            max = Ops.add(ds, delta);
        } else {
            delta=  (QDataSet) ds.property(QDataSet.BIN_PLUS);
            if ( delta!=null ) {
                max = Ops.add(ds, delta);
            }
        }

        delta = (QDataSet) ds.property(QDataSet.DELTA_MINUS);
        if (delta != null) {
            min = Ops.subtract(ds, delta);
        } else {
            delta=  (QDataSet) ds.property(QDataSet.BIN_MINUS);
            if ( delta!=null ) {
                min = Ops.subtract(ds, delta);
            }
        }

        QDataSet wmin = DataSetUtil.weightsDataSet(min);
        QDataSet wmax = DataSetUtil.weightsDataSet(max);
        QDataSet wds= DataSetUtil.weightsDataSet(ds);
        QubeDataSetIterator it = new QubeDataSetIterator(ds);
        double[] result = new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        int i = 0;

        while (i < DS_LENGTH_LIMIT && it.hasNext()) {
            it.next();
            i++;
            if ( it.getValue(wds)==0 ) continue;
            double maxv= it.getValue(max);
            if ( Double.isInfinite( maxv ) ) continue;
            if (it.getValue(wmin) > 0.)
                result[0] = Math.min(result[0], it.getValue(min));
            if (it.getValue(wmax) > 0.)
                result[1] = Math.max(result[1], maxv );
        }

        if (result[0] == Double.POSITIVE_INFINITY) {  // no valid data!
            if (UnitsUtil.isTimeLocation(u)) {
                result[0] = Units.t2000.convertDoubleTo(u, 0.);
                result[1] = Units.t2000.convertDoubleTo(u, 86400); // avoid bug where rounding error in formatting of newDatumRange(0,1,t2000) resulted in invalid datm
            } else {
                result[0] = 0.;
                result[1] = 1.;
            }
        }
        return result;
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
     * @see http://autoplot.org/developer.guessRenderType
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

        String srenderType= (String) fillds.property(QDataSet.RENDER_TYPE);
        if ( srenderType!=null ) {
            if ( srenderType.equals("time_series") ) { //TODO: CDAWeb time_series will be fixed to "series" once it can automatically reduce data.
                if (fillds.length() > SERIES_SIZE_LIMIT) {
                    spec = RenderType.hugeScatter;
                } else {
                    spec = RenderType.series;
                }
                return spec;
            } else if ( srenderType.equals("waveform" ) ) {
                spec = RenderType.hugeScatter;
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
                return RenderType.hugeScatter;
            }
        }

        if (fillds.rank() >= 2) {
//            if ( dep1!=null && !isVectorOrBundleIndex(dep1) ) {
//                spec = specPref; // favor spectrograms when we have a BUNDLE_1 and DEPEND_1.
//            } else if ( bundle1!=null || (dep1 != null && isVectorOrBundleIndex(dep1) ) ) {
            if ( ( bundle1!=null && bundle1.length()<30 ) || (dep1 != null && isVectorOrBundleIndex(dep1) ) ) {
                if (fillds.length() > SERIES_SIZE_LIMIT) {
                    spec = RenderType.hugeScatter;
                } else {
                    spec = RenderType.series;
                }
                if ( bundle1!=null ) {
                    if ( bundle1.length()==3 && bundle1.property(QDataSet.DEPEND_0,2)!=null ) { // bad kludge
                        spec= RenderType.colorScatter;
                    } else if (bundle1.length() == 3 && bundle1.property(QDataSet.DEPENDNAME_0, 2) != null) { // bad kludge
                        spec= RenderType.colorScatter;
                    } else if ( bundle1.length()==3 && fillds.property(QDataSet.DEPEND_0)==null && bundle1.property(QDataSet.CONTEXT_0,2)!=null ) {  // this is more consistent with PlotElementController code.
                        spec= RenderType.colorScatter;
                    } else if ( bundle1.length()==3 || bundle1.length()==4 || bundle1.length()==5 ) {
                        if ( Schemes.isEventsList(fillds) ) {
                            spec= RenderType.eventsBar;
                        } else {
                            Units u3= (Units) bundle1.property(QDataSet.UNITS,bundle1.length()-1);
                            if ( UnitsUtil.isOrdinalMeasurement(u3) ) {
                                spec= RenderType.digital;
                            } else {
                                spec = RenderType.series;
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
                if ( dep1==null && fillds.rank()==2 && fillds.length()>3 && fillds.length(0)<4 ) { // Vector quantities without labels. [3x3] is a left a matrix.
                    spec = RenderType.series;
                } else {
                    spec = specPref;
                }
            }
        } else if ( fillds.rank()==0 || fillds.rank()==1 && SemanticOps.isBundle(fillds) ) {
            spec= RenderType.digital;

        } else if ( SemanticOps.getUnits(fillds) instanceof EnumerationUnits ) {
            QDataSet dep0= (QDataSet) fillds.property(QDataSet.DEPEND_0);
            if ( dep0==null ) {
                spec= RenderType.digital;
            } else {
                spec= RenderType.eventsBar;
            }
        } else {
            if (fillds.length() > SERIES_SIZE_LIMIT) {
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
            if (recyclable != null && recyclable instanceof ImageVectorDataSetRenderer) {
                return recyclable;
            } else {
                ImageVectorDataSetRenderer result = new ImageVectorDataSetRenderer(null);
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
        return new ImageIcon(AutoplotUtil.class.getResource("/logo64x64.png")).getImage();
    }
    
    public static Image getNoIcon() {
        return new ImageIcon(AutoplotUtil.class.getResource("/logo64x64.png")).getImage();
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

}
