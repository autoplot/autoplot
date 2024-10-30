
package org.autoplot;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.dataset.DataSet;
import org.das2.dataset.TableDataSet;
import org.das2.dataset.VectorDataSet;
import org.das2.graph.DasCanvas;
import org.das2.util.DasPNGConstants;
import org.das2.util.DasPNGEncoder;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.das2.DasApplication;
import org.das2.util.awt.PdfGraphicsOutput;
import org.das2.util.monitor.NullProgressMonitor;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.Bindings;
import org.python.core.PyJavaInstance;
import org.autoplot.dom.Application;
import org.autoplot.dom.DataSourceFilter;
import org.autoplot.scriptconsole.ExitExceptionHandler;
import org.das2.dataset.DataSetAdapter;
import org.das2.datum.InconvertibleUnitsException;
import org.das2.event.BoxRenderer;
import org.das2.event.BoxSelectionEvent;
import org.das2.event.BoxSelectionListener;
import org.das2.event.BoxSelectorMouseModule;
import org.das2.event.MouseModule;
import org.das2.graph.DasPlot;
import org.das2.util.awt.SvgGraphicsOutput;
import org.das2.util.monitor.ProgressMonitor;
import org.jdesktop.beansbinding.Converter;
import org.python.core.Py;
import org.python.core.PyFunction;
import org.autoplot.ApplicationModel.ResizeRequestListener;
import org.autoplot.datasource.AnonymousDataSource;
import org.autoplot.dom.DomNode;
import org.autoplot.dom.DomUtil;
import org.autoplot.dom.Plot;
import org.autoplot.dom.PlotElement;
import org.autoplot.state.StatePersistence;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFormat;
import org.autoplot.datasource.FileSystemUtil;
import org.autoplot.datasource.GuiUtil;
import org.autoplot.datasource.URISplit;
import org.das2.components.DataPointRecorder;
import org.das2.datum.Units;
import org.das2.graph.DasColorBar;
import org.das2.graph.Painter;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.SemanticOps;
import org.das2.qds.ops.Ops;
import org.das2.qstream.SimpleStreamFormatter;
import org.das2.qstream.StreamException;
import org.das2.util.filesystem.FileSystem;

/**
 * ScriptContext provides the API to perform abstract functions with 
 * the application.  For example, 
 * 
 *<blockquote><pre><small>{@code
 ScriptContext.load('https://autoplot.org/data/somedata.dat')
 ScriptContext.writeToPdf('/tmp/foo.pdf')
 *}</small></pre></blockquote>
 * 
 * @author jbf
 */
public class ScriptContext2023 extends PyJavaInstance {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.script");
    private static final Logger resizeLogger= Logger.getLogger("autoplot.dom.canvas.resize");
    
    private ApplicationModel model = null;
    private Application dom= null;

    private static ScriptContext2023 instance;
    
    public static synchronized ScriptContext2023 getInstance() {
        if ( instance==null ) {
            instance= new ScriptContext2023();
        }
        return instance;
    }
    
    private ScriptContext2023() {
        logger.fine("new ScriptContext");
    }

    /**
     * set up the uncaught exception handler for headless applications, like CreatePngWalk.
     */
    private void setUpHeadlessExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                logger.log(Level.SEVERE, "runtime exception: " + e, e);
                if (e instanceof InconvertibleUnitsException) {
                    // do nothing!!!  this is associated with the state change.  TODO: this should probably not be here, and it should be caught elsewhere.
                    return;
                }
                model.getExceptionHandler().handleUncaught(e);
            }
        });
    }
    
    /**
     * initialize the model and view.
     */
    private synchronized void maybeInitModel() {
        if (model == null) {
            model = new ApplicationModel();
            model.setExceptionHandler( new ExitExceptionHandler() );
            setUpHeadlessExceptionHandler();
            model.addDasPeersToAppAndWait();
            dom= model.getDocumentModel();
        }
        if ( view!=null ) {
            if ( SwingUtilities.isEventDispatchThread() ) {
                if ( !view.isVisible() ) view.setVisible(true);
            } else {
                SwingUtilities.invokeLater( new Runnable() {
                    @Override
                    public void run() {
                        if ( !view.isVisible() ) view.setVisible(true);
                    }
                } );
            }
        }
    }

    /**
     * set the current application for commands.  For example, to
     * have two windows plotting data, you would:
     * <pre>
     * plot([1,2,3])
     * popup=newWindow('popup')
     * setWindow(popup)
     * plot([3,2,1])
     * </pre>
     * @param app the application
     * @see #setWindow(org.autoplot.ApplicationModel) 
     * @see #newApplication(java.lang.String) 
     */
    public synchronized void setApplication( AutoplotUI app ) {
        setApplicationModel(app.applicationModel);
        setView(app);        
        appLookup.put( app.applicationModel, app );
    }
    
    /**
     * reset the script focus to the default application.
     */
    public synchronized void setDefaultApplication() {
        setApplication(defaultApp);
    }
    
    /**
     * Set the application model.  This is the simplest Autoplot implementation
     * existing, where there are no buttons, etc.
     * @param appm 
     * @see #newWindow(java.lang.String) 
     * @see #getWindow() which returns the current window.
     * @see #setApplication(org.autoplot.AutoplotUI) which creates another application.
     */
    public synchronized void setWindow( ApplicationModel appm ) {
        AutoplotUI app= appLookup.get(appm);
        if (app==null ) {
            view= null;
        } else {
            view= app;
        }
        setApplicationModel(appm);              
    }
    
    /**
     * return the focus application.
     * @return the focus application.
     */
    public synchronized AutoplotUI getApplication() {
        return view;
    }
    
    /**
     * return the internal handle for the window and dom within.
     * @return the internal handle for the application.
     */
    public synchronized ApplicationModel getWindow() {
        return model;
    }
    
    private static final Map<String,AutoplotUI> apps= new HashMap();
    private static final Map<String,ApplicationModel> applets= new HashMap();
    private static final Map<ApplicationModel,AutoplotUI> appLookup= new HashMap();
    
    /**
     * get or create the application identified by the name.  For example:
     *<blockquote><pre>
     *ds= ripples(20,20)
     *plot( 0, ds )
     *sliceWindow= newApplication('slice')
     *setApplication(sliceWindow)
     *plot( slice0(ds,0) ) 
     * </pre></blockquote>
     * Windows with this name will be reused.
     * @param id an identifier
     * @return the AutoplotUI.
     */
    public synchronized AutoplotUI newApplication( String id ) {
        AutoplotUI result= apps.get(id);
        if ( result!=null ) {
            if ( !AppManager.getInstance().isRunningApplication(result) ) {
                AutoplotUI app= apps.remove(id); //TODO: just listen for window events!
                appLookup.remove(app.applicationModel);
                result= null;
            }
        }
        if ( result==null ) {
            result= view.newApplication();
            result.setApplicationName(id);
            apps.put(id,result);
            appLookup.put(result.applicationModel,result);
        }
        return result;
    }
    
    /**
     * create a new window with the given location and size.
     * @param id identifier for the window
     * @param width the canvas width
     * @param height the canvas height
     * @param x the window upper-left location, if possible
     * @param y the window upper-left location, if possible
     * @return a handle (do not use this object, code will break) for the window.
     * @see #setWindow(org.autoplot.ApplicationModel) 
     */
    public synchronized ApplicationModel newWindow( final String id, int width, int height, int x, int y ) {
        ApplicationModel result= newWindow(id);
        Window window= SwingUtilities.getWindowAncestor(result.canvas);
        if ( window!=null ) {
        // assume it is fitted for now.  This is a gross over simplification, not considering scroll panes, etc.
            Dimension windowDimension= window.getSize();
            Dimension canvasDimension= model.canvas.getSize();
            if ( Math.abs( windowDimension.width - canvasDimension.width )>100 
                    || Math.abs( windowDimension.height - canvasDimension.height )>100 ) { 
                window.setSize( width + 2, height + 29 ); // safety.  Based on arbitrary window system (Gnome).
            } else {
                window.setSize( width + ( windowDimension.width - canvasDimension.width ), height +  ( windowDimension.height - canvasDimension.height ) ); 
            }
            window.setLocation( x, y );
        } else {
            model.canvas.setSize(width,height);
        }
        return result;
    }
    
    /**
     * set the window location
     * @param x the window upper-left location, if possible
     * @param y the window upper-left location, if possible
     */
    public synchronized void setWindowLocation( int x, int y ) {
        Window window= SwingUtilities.getWindowAncestor( getWindow().getCanvas() );
        if ( window!=null ) {
        // assume it is fitted for now.  This is a gross over simplification, not considering scroll panes, etc.
            window.setLocation( x, y );
        } else {
        }
    }
    
    /**
     * return a new Autoplot.
     * @param parent
     * @param title
     * @return 
     */
    public synchronized ApplicationModel newDialogWindow( Window parent, final String title ) {
        final JDialog p= new JDialog(parent,title);
        ApplicationModel result= new ApplicationModel();
        result.addDasPeersToApp();
        if ( !DasApplication.getDefaultApplication().isHeadless() ) {
            p.getContentPane().add(result.canvas);
            p.pack();
            p.setVisible(true);
        }
        result.setResizeRequestListener( new ResizeRequestListener() {
            @Override
            public double resize(int width, int height) {
                resizeLogger.log(Level.FINE, "resize1 ({0},{1})", new Object[]{width,height});
                if ( p!=null ) {
                    Dimension windowDimension= p.getSize();
                    Dimension canvasDimension= model.canvas.getSize();        
                    p.setSize( width + ( windowDimension.width - canvasDimension.width ), height +  ( windowDimension.height - canvasDimension.height ) ); 
                }
                model.canvas.setSize(width,height);
                return 1.;
            }                
        } );
        return result;
    }

    /**
     * create a new window.
     * @param id identifier for the window
     * @return a handle (do not use this object, code will break) for the window.
     * @see #setWindow(org.autoplot.ApplicationModel) 
     */
    public synchronized ApplicationModel newWindow( final String id ) {
        ApplicationModel result= applets.get(id);
        if ( result==null ) {
            result= new ApplicationModel();
            result.addDasPeersToApp();
            result.setName(id);
            applets.put(id,result);
            final JFrame j;
            if ( !DasApplication.getDefaultApplication().isHeadless() ) {
                j= new JFrame(id);
                j.setIconImage( AutoplotUtil.getAutoplotIcon() );
                j.getContentPane().add(result.canvas);
                j.pack();
                j.setVisible(true);
                j.addWindowListener(new WindowListener() {
                    @Override
                    public void windowOpened(WindowEvent e) {
                    }

                    @Override
                    public void windowClosing(WindowEvent e) {
                        applets.remove(id);
                    }

                    @Override
                    public void windowClosed(WindowEvent e) {
                        applets.remove(id);
                    }

                    @Override
                    public void windowIconified(WindowEvent e) {
                    }

                    @Override
                    public void windowDeiconified(WindowEvent e) {
                    }

                    @Override
                    public void windowActivated(WindowEvent e) {
                    }

                    @Override
                    public void windowDeactivated(WindowEvent e) {
                    }
                });
            } else {
                j= null;
            }
            result.setResizeRequestListener( new ResizeRequestListener() {
                @Override
                public double resize(int width, int height) {
                    resizeLogger.log(Level.FINE, "resize2 ({0},{1})", new Object[]{width,height});
                    if ( j!=null ) {
                        Dimension windowDimension= j.getSize();
                        Dimension canvasDimension= model.canvas.getSize();        
                        j.setSize( width + ( windowDimension.width - canvasDimension.width ), height +  ( windowDimension.height - canvasDimension.height ) ); 
                    }
                    model.canvas.setSize(width,height);
                    return 1.;
                }                
            } );
        }
        return result;
    }
            
    /**
     * set the focus for scripts.
     * @param m the application model.
     * @see setWindow, which should be used instead.
     */
    public void setApplicationModel(ApplicationModel m) {
        model = m;
        dom= m.getDocumentModel();
    }
    
    private AutoplotUI view = null;
    
    /**
     * keep track of the default application.
     */
    private AutoplotUI defaultApp= null; // kludge to get the first.

    /**
     * set the default application.  Jython codes should not call this.
     * @param app
     */
    protected void _setDefaultApp( AutoplotUI app ) {
        defaultApp= app;
        appLookup.put( app.applicationModel, app);
    }
    
    private synchronized void maybeInitView() {
        maybeInitModel();
        if (view == null) {
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    view = new AutoplotUI(model);
                    view.setVisible(true);
                    defaultApp= view;
                }
            };
            if ( SwingUtilities.isEventDispatchThread() ) {
                run.run();
            } else {
                try {
                    SwingUtilities.invokeAndWait(run);
                } catch (InterruptedException | InvocationTargetException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
        }
        view.setMessage( AutoplotUI.READY_MESSAGE );
    }

    /**
     * Used by AutoplotUI to set the view.
     * @param v the new view.
     */
    protected void setView(AutoplotUI v) {
        view = v;
    }

    /**
     * return the Window for the application, to be used for dialogs.
     * See createGui(), which creates the view.
     * @return
     */
    public Window getViewWindow() {
        return view;
    }

    private static OutputStream out = System.out;

    /**
     * resets the output stream.  This method is used internally, do not use
     * this routine.
     * @param out
     */
    public void _setOutputStream(OutputStream out) {
        ScriptContext2023.out = out;
    }
    
    /**
     * set the size of the canvas.  This is only used when the GUI is not used, and in
     * headless mode, otherwise the GUI controls the size of the canvas.
     * 
     * @param width the width of the canvas
     * @param height the height of the canvas
     */
    public void setCanvasSize( final int width, final int height) {
        maybeInitModel();
        Runnable run= new Runnable() {
            @Override
            public void run() {
                model.setCanvasSize(width, height);                
                model.getDocumentModel().getCanvases(0).setSize(width,height);
            }
        };
        if ( SwingUtilities.isEventDispatchThread() ) {
            run.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(run);
            } catch (InterruptedException | InvocationTargetException ex) {
                Logger.getLogger(ScriptContext2023.class.getName()).log(Level.SEVERE, null, ex);
            }
        }                
    }

    /**
     * set the internal model's url to surl.  The data set will be loaded,
     * and writeToPng and writeToSvg can be used in headless mode.
     * @param surl
     * @Deprecated use plot instead;
     */
    public void setDataSourceURL(String surl) {
        model.setDataSourceURL(surl);
        if ( !SwingUtilities.isEventDispatchThread() ) model.waitUntilIdle();
    }

    /**
     * bring up the autoplot with the specified URL.  Note the URI is resolved
     * asynchronously, so errors thrown during the load cannot be caught here.
     * See getDataSet to load data synchronously.
     * @param suri a URI or vap file
     */
    public void plot(String suri) {
        maybeInitModel();
        if ( view!=null && view.isExpertMode() ) {
            view.dataSetSelector.setValue(suri);
        }
        model.resetDataSetSourceURL(suri, new NullProgressMonitor());
        if ( !SwingUtilities.isEventDispatchThread() ) model.waitUntilIdle();
    }

    /**
     * plot one URI against another.  No synchronization is done, so beware.
     * Introduced for testing non-time axis TSBs.
     * @param surl1 the independent variable dataset (generally used for X)
     * @param surl2 the dependent variable dataset (generally used for Y, but can be rank 2).
     */
    public void plot(String surl1, String surl2) {
        maybeInitModel();
        if ( surl1.endsWith(".vap") || surl1.contains(".vap?")  ) {
            throw new IllegalArgumentException("cannot load vap here in two-argument plot");
        } else {
            DataSourceFilter dsf= model.getDocumentModel().getDataSourceFilters(0);
            List<PlotElement> pes= dom.getController().getPlotElementsFor(dsf);
            PlotElement pe;
            if ( pes.size()>0 ) pe= pes.get(0); else pe=null;
            Plot p= pe==null ? dom.getPlots(0) : dom.getController().getPlotFor(pe);
            dom.getController().doplot( p, pe, surl1, surl2 );
        }
        if ( !SwingUtilities.isEventDispatchThread() ) model.waitUntilIdle();
    }

    /**
     * bring up the autoplot with the specified URL.
     * @param chNum the data source number to reset the URI.
     * @param surl a URI to use
     */
    public void plot(int chNum, String surl) {
        maybeInitModel();
        model.setDataSet( chNum, null, surl );
        if ( !SwingUtilities.isEventDispatchThread() ) model.waitUntilIdle();
    }

    /**
     * bring up the plot with the specified URI.
     * @param chNum the data source number to reset the URI.
     * @param label for the plot.
     * @param surl a URI to use
     */
    public void plot(int chNum, String label, String surl) {
        maybeInitModel();
        model.setDataSet( chNum, label, surl );
        if ( !SwingUtilities.isEventDispatchThread() ) model.waitUntilIdle();
    }

    /**
     * plot the dataset in the first dataSource node.
     * @param ds QDataSet to plot
     */
    public void plot(QDataSet ds) {
        plot( 0, (String)null, ds );
    }

    /**
     * plot the dataset in the first dataSource node.
     * @param x QDataSet for the independent parameter
     * @param y QDataSet for the dependent parameter
     */
    public void plot( QDataSet x, QDataSet y )  {
        plot( 0, (String)null, x, y );
    }
    
    /**
     * plot the dataset in the first dataSource node.
     * @param x QDataSet for the independent parameter for the X values
     * @param y QDataSet for the independent parameter for the Y values
     * @param z Rank 1 or Rank 2 QDataSet for the dependent parameter
     */
    public void plot( QDataSet x, QDataSet y, QDataSet z ) {
        plot( 0, (String)null, x, y, z );
    }

    /**
     * plot the dataset in the specified dataSource node.
     * @param chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param ds dataset to plot.
     */
    public void plot( int chNum, QDataSet ds)  {
        plot( chNum, (String)null, ds );
    }

    /**
     * plot the dataset in the specified  dataSource node.
     * @param chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param x QDataSet for the independent parameter for the X values
     * @param y QDataSet for the independent parameter for the Y values
     */
    public void plot( int chNum, QDataSet x, QDataSet y )  {
        plot( chNum, (String)null, x, y );
    }

    /**
     * plot the dataset in the specified  dataSource node.
     * @param chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param x QDataSet for the independent parameter for the X values
     * @param y QDataSet for the independent parameter for the Y values
     * @param z Rank 1 or Rank 2 QDataSet for the dependent parameter
     */
    public void plot( int chNum, QDataSet x, QDataSet y, QDataSet z ) {
        plot( chNum, (String)null, x, y, z );
    }

    /**
     * returns a color table with the given name.  If the name is already registered,
     * then the registered colortable is returned.
     * using QDataSets as inputs to make it easier to use in scripts.
     * 
     * @param name the name for the colortable.   
     * @param index control points for the colors, or None
     * @param rgb dataset of ds[N,3] where N is the number of colors
     * @return object representing the color table.
     * @see rgbColorDataset
     */
    public static DasColorBar.Type makeColorTable( String name, QDataSet index, QDataSet rgb ) {
        boolean implicitWarn= false;
        if ( index==null ) {
            index= Ops.indgen(rgb.length());
            implicitWarn= true;
        }
        int[] iindex= new int[index.length()];
        int[] red= new int[rgb.length()];
        int[] green= new int[rgb.length()];
        int[] blue= new int[rgb.length()];

        int bottom= 0;
        int top= 0;
        if ( rgb.rank()==1 ) {
            if ( SemanticOps.getUnits(rgb)==Units.rgbColor ) {
                for ( int i=0; i<iindex.length; i++ ) {
                    iindex[i]= (int)Math.round(index.value(i));
                    red[i]= ( (int)rgb.value(i) & 0xFF0000 ) >> 16;
                    green[i]= ( (int)rgb.value(i) & 0x00FF00 ) >> 8;
                    blue[i]= ( (int)rgb.value(i) & 0x0000FF );
                    top= Math.max( top, iindex[i] );
                }
            } else {
                throw new IllegalArgumentException("only rank 2 bundle of R,G,B or rank 1 data with Units.rgbColor.");
            }
        } else {
            for ( int i=0; i<iindex.length; i++ ) {
                iindex[i]= (int)Math.round(index.value(i));
                red[i]= (int)Math.round(rgb.value(i,0));
                green[i]= (int)Math.round(rgb.value(i,1));
                blue[i]= (int)Math.round(rgb.value(i,2));
                top= Math.max( top, iindex[i] );
            }
        }
        if ( top>254 ) {
            if ( implicitWarn ) {
                throw new IllegalArgumentException("no more than 254 colors.");
            } else {
                throw new IllegalArgumentException("the top index must be less than 254.");
            }
        } 
        try {
            DasColorBar.Type t= DasColorBar.Type.parse( name );
            return t;
            
        } catch ( IllegalArgumentException e ) {
            logger.log(Level.FINE, "creating type \"{0}\"", name);
            int[] tt= DasColorBar.Type.makeColorTable( iindex, red, green, blue, top, bottom, top );
            return new DasColorBar.Type( name, tt );
        }
        
    }    
    
    private static MutablePropertyDataSet ensureMutable( QDataSet ds ) {
        if ( ds==null ) return null;
        return Ops.copy(ds);
    }
    
    private static void ensureImmutable( QDataSet ... dss ) {
        for ( QDataSet ds: dss ) {
            if ( ds==null ) continue;
            if ( ds instanceof MutablePropertyDataSet ) {
                MutablePropertyDataSet mpds= (MutablePropertyDataSet)ds;
                if ( !mpds.isImmutable() ) {
                    mpds.makeImmutable();
                }
            }
        }
    }
    
    /**
     * bring up the autoplot with the dataset
     * @param chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param label the label for the plot dependent parameter
     * @param ds the dataset to use.
     */
    public void plot( int chNum, String label, QDataSet ds) {
        maybeInitModel();
        MutablePropertyDataSet yds= ensureMutable(ds);
        model.setDataSet( chNum, label, yds );
        ensureImmutable(ds);
        if ( !SwingUtilities.isEventDispatchThread() ) model.waitUntilIdle();
    }

    /**
     * plot the dataset in the specified  dataSource node.
     * @param chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param label the label for the plot dependent parameter
     * @param x QDataSet for the independent parameter for the X values
     * @param y QDataSet for the independent parameter for the Y values
     */
    public void plot( int chNum, String label, QDataSet x, QDataSet y ) {
        plot( chNum, label, x, y, (String)null );
    }

    /**
     * plot the dataset in the specified  dataSource node.  Note this should not
     * be called from the event thread.
     * @param chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param label the label for the plot dependent parameter
     * @param x QDataSet for the independent parameter for the X values, or null.
     * @param y QDataSet for the independent parameter for the Y values, or null.
     * @param renderType string explicitly controlling the renderType and hints.
     */    
    public void plot( int chNum, String label, QDataSet x, QDataSet y, String renderType ) {
        plot( chNum, label, x, y, renderType, true );
    }

    /**
     * plot the dataset in the specified  dataSource node.  Note this should not
     * be called from the event thread.
     * @param chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param label the label for the plot dependent parameter
     * @param x QDataSet for the independent parameter for the X values, or null.
     * @param y QDataSet for the independent parameter for the Y values, or null.
     * @param renderType string explicitly controlling the renderType and hints.
     * @param reset reset by autoranging, autolabelling, etc.
     */    
    public void plot( int chNum, String label, QDataSet x, QDataSet y, String renderType, boolean reset ) {        
        maybeInitModel();
        if ( x==null && renderType==null ) {
            model.setDataSet( chNum, label, y, reset );
        } else {
            QDataSet xds= x;
            MutablePropertyDataSet yds= ensureMutable(y);
            if ( xds!=null && yds!=null ) {
                if ( yds.rank()==0 ) {
                    yds.putProperty( QDataSet.CONTEXT_0,xds );
                } else {
                    yds.putProperty( QDataSet.DEPEND_0,xds );
                }
            }
            if ( ( yds!=null ) && ( xds!=null || renderType!=null ) ) yds.putProperty( QDataSet.RENDER_TYPE, renderType ); // plot command calls this with all-null arguments, and we don't when RENDER_TYPE setting to be nulled.
            model.setDataSet( chNum, label, yds);
        }
        ensureImmutable(x,y);
        if ( !SwingUtilities.isEventDispatchThread() ) model.waitUntilIdle();
    }
    
    /**
     * plot the dataset in the specified  dataSource node.
     * @param chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param label the label for the plot dependent parameter
     * @param x QDataSet for the independent parameter for the X values
     * @param y QDataSet for the independent parameter for the Y values
     * @param z Rank 1 or Rank 2 QDataSet for the dependent parameter
     */
    public void plot( int chNum, String label, QDataSet x, QDataSet y, QDataSet z ) {
        maybeInitModel();
        QDataSet xds= x;
        
        if ( z==null ) throw new IllegalArgumentException("z is null");
        if ( z.rank()==1 ) {
            QDataSet zds= z;
            MutablePropertyDataSet yds= ensureMutable(y);
            if ( yds==null ) throw new IllegalArgumentException("y is null");
            yds.putProperty( QDataSet.DEPEND_0, xds );
            yds.putProperty( QDataSet.PLANE_0, zds );
            model.setDataSet(chNum, label, yds);
        } else {
            QDataSet yds= y;
            MutablePropertyDataSet zds= ensureMutable(z);
            if ( xds!=null ) zds.putProperty( QDataSet.DEPEND_0, xds );
            if ( yds!=null ) zds.putProperty( QDataSet.DEPEND_1, yds );
            model.setDataSet(chNum, label, zds);
        }
        ensureImmutable(x,y,z);
        if ( !SwingUtilities.isEventDispatchThread() ) model.waitUntilIdle();
    }

    /**
     * plot the dataset in the specified dataSource node, using the render type
     * specified.  The renderType parameter is a string identifier, and currently the following are
     * used: digital spectrogram nnSpectrogram hugeScatter series scatter colorScatter stairSteps
     * fillToZero digital image  pitchAngleDistribution eventsBar vectorPlot orbitPlot contour
     *<blockquote><pre><small>{@code
     *plot( 0, 'label', findgen(20), ripples(20), ripples(20), 'digital' )
     *from org.autoplot import RenderType
     *plot( 0, 'label', findgen(20), ripples(20), ripples(20), RenderType.digital.toString() )
     *}</small></pre></blockquote>
     *
     * @param chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param label the label for the dependent parameter
     * @param x QDataSet for the independent parameter for the X values
     * @param y QDataSet for the independent parameter for the Y values
     * @param z Rank 1 or Rank 2 QDataSet for the dependent parameter, or null.
     * @param renderType hint at the render type to use, such as "nnSpectrogram" or "digital", 
     */
    public void plot( int chNum, String label, QDataSet x, QDataSet y, QDataSet z, String renderType ) {
        maybeInitModel();
        QDataSet xds= x;
        MutablePropertyDataSet zds= ensureMutable(z);
        if ( zds==null ) {
            MutablePropertyDataSet yds= ensureMutable(y);
            if ( yds==null ) throw new IllegalArgumentException("y cannot be null if z is null");
            yds.putProperty( QDataSet.RENDER_TYPE, renderType );
            yds.putProperty( QDataSet.DEPEND_0, xds );
            model.setDataSet(chNum, label, yds);
        } else if ( zds.rank()==1 ) {           
            MutablePropertyDataSet yds= ensureMutable(y);
            if ( yds==null ) throw new IllegalArgumentException("y cannot be null if z is null");
            yds.putProperty( QDataSet.RENDER_TYPE, renderType );
            yds.putProperty( QDataSet.DEPEND_0, xds );
            yds.putProperty( QDataSet.PLANE_0, zds );
            model.setDataSet(chNum, label, yds);
        } else {
            QDataSet yds= y;
            zds.putProperty( QDataSet.RENDER_TYPE, renderType );
            if ( x!=null ) zds.putProperty( QDataSet.DEPEND_0, xds );
            if ( y!=null ) zds.putProperty( QDataSet.DEPEND_1, yds );
            model.setDataSet(chNum, label, zds);
        }
        ensureImmutable(x,y,z);
        if ( !SwingUtilities.isEventDispatchThread() ) model.waitUntilIdle();
    }

    /**
     * plot the dataset in the specified dataSource node, using the render type
     * specified.  The renderType parameter is a string identifier, and currently the following are
     * used: digital spectrogram nnSpectrogram hugeScatter series scatter colorScatter stairSteps
     * fillToZero digital image  pitchAngleDistribution eventsBar vectorPlot orbitPlot contour
     *<blockquote><pre><small>{@code
     *plot( 0, 'label', findgen(20), ripples(20), ripples(20), 'digital' )
     *from org.autoplot import RenderType
     *plot( 0, 'label', findgen(20), ripples(20), ripples(20), RenderType.digital.toString() )
     *}</small></pre></blockquote>
     *
     * @param chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param label the label for the dependent parameter
     * @param x QDataSet for the independent parameter for the X values
     * @param y QDataSet for the independent parameter for the Y values
     * @param z Rank 1 or Rank 2 QDataSet for the dependent parameter, or null.
     * @param renderType hint at the render type to use, such as "nnSpectrogram" or "digital", 
     * @param reset reset by autoranging, autolabelling, etc.
     */
    public void plot( int chNum, String label, QDataSet x, QDataSet y, QDataSet z, String renderType, boolean reset ) {
        maybeInitModel();
        QDataSet xds= x;
        MutablePropertyDataSet zds= ensureMutable(z);
        if ( zds==null ) {
            MutablePropertyDataSet yds= ensureMutable(y);
            if ( yds==null ) throw new IllegalArgumentException("y cannot be null if z is null");
            yds.putProperty( QDataSet.RENDER_TYPE, renderType );
            yds.putProperty( QDataSet.DEPEND_0, xds );
            model.setDataSet(chNum, label, yds, reset);
        } else if ( zds.rank()==1 ) {           
            MutablePropertyDataSet yds= ensureMutable(y);
            if ( yds==null ) throw new IllegalArgumentException("y cannot be null if z is null");
            yds.putProperty( QDataSet.RENDER_TYPE, renderType );
            yds.putProperty( QDataSet.DEPEND_0, xds );
            yds.putProperty( QDataSet.PLANE_0, zds );
            model.setDataSet(chNum, label, yds, reset);
        } else {
            QDataSet yds= y;
            zds.putProperty( QDataSet.RENDER_TYPE, renderType );
            if ( x!=null ) zds.putProperty( QDataSet.DEPEND_0, xds );
            if ( y!=null ) zds.putProperty( QDataSet.DEPEND_1, yds );
            model.setDataSet(chNum, label, zds, reset);
        }
        ensureImmutable(x,y,z);
        if ( !SwingUtilities.isEventDispatchThread() ) model.waitUntilIdle();
    }
    
    /**
     * "overplot" by adding another PlotElement to the plot and setting the data to this PlotElement.
     * @param chNum the focus 
     * @return the channel number for the new plot element.
     * @see #setLayout(int, int) 
     */
    public int addPlotElement( int chNum ) {
        maybeInitModel();
        
        DataSourceFilter dsf= dom.getDataSourceFilters(chNum);
        List<PlotElement> pes= dom.getController().getPlotElementsFor(dsf);
        if ( pes.isEmpty() ) throw new IllegalArgumentException("nothing plotted that is listening to this channel number.");
        String plotId= pes.get(0).getPlotId();
        Plot plot= (Plot)DomUtil.getElementById( dom, plotId );
                
        PlotElement pe= dom.getController().addPlotElement( plot, null, null );

        // we've added a new DataSourceFilter (and channel number), so identify this.
        dsf= dom.getController().getDataSourceFilterFor(pe);
        // figure out the channel number.
        int newChNum=-1;
        DataSourceFilter[] dsfs= dom.getDataSourceFilters();
        for ( int i=0; i<dsfs.length; i++ ) {
            if ( dsfs[i]==dsf ) {
                newChNum= i;
                break;
            }
        }
        return newChNum;
    }
    
    /**
     * add code that will respond to mouse events.  This will receive an 
     * event following the mouse release when a box is dragged out.
     *<blockquote><pre><small>{@code
def boxLookup( evt ):
    showMessageDialog( "<html>start: (%s,%s)<br>finish: (%s,%s)" %
        ( evt.getStartX(), evt.getStartY(), 
        evt.getFinishX(), evt.getFinishY() ) )
  
addMouseModule( dom.plots[0], 'Box Lookup', boxLookup )   
     *}</small></pre></blockquote>
     * @param plot the plot which will receive the events.
     * @param label a label for the mouse module.
     * @param listener the PyFunction to call with new events.
     * @return the mouse module.  
     * @see org.das2.event.MouseModule#setDragRenderer(org.das2.event.DragRenderer) setDragRenderer to see how to set how feedback can be provided.
     * @see org.das2.event.BoxSelectionEvent BoxSelectionEvent for the methods of the event.
     * 
     */
    public static MouseModule addMouseModule( Plot plot, String label, final PyFunction listener ) {
        DasPlot p= plot.getController().getDasPlot();
        BoxSelectorMouseModule mm= new BoxSelectorMouseModule( p, p.getXAxis(), p.getYAxis(), null, new BoxRenderer(p), label );
        BoxSelectionListener bsl= (BoxSelectionEvent e) -> {
            listener.__call__(Py.java2py(e));
        };
        mm.addBoxSelectionListener(bsl);
        p.getDasMouseInputAdapter().setPrimaryModule(mm);
        return mm;
    }
    
    /**
     * add code that will paint custom graphics on the canvas or on a plot.
     * The command will be invoked after all other painting is done, making
     * the decoration to be on top.  Note plots can only have one decoration,
     * and the Canvas can have any number.  Calling reset() will remove all
     * decorations.
     *<blockquote><pre><small>{@code
def paint(g):
    for i in xrange(0,1000,100):
        g.drawOval(500-i/2,500-i/2,i,i)

addTopDecoration( dom.canvases[0], paint )
     *}</small></pre></blockquote>
     * @param node the plot or canvas over which to plot
     * @param painter the PyFunction to call when painting
     * @see https://github.com/autoplot/dev/blob/master/demos/2020/20200229/demoAddBottomDecoration.jy
     * 
     */
    public static void addTopDecoration( DomNode node, final PyFunction painter ) {
        if ( !( node instanceof Plot ) && ! ( node instanceof org.autoplot.dom.Canvas ) ) {
            throw new IllegalArgumentException("first argument must be plot or canvas");
        }
        if ( node instanceof Plot ) {
            final Plot p= (Plot)node;
            Painter thep= new Painter() {
                @Override
                public void paint(Graphics2D g) {
                    painter.__call__(Py.java2py(g));
                }   
            };
            p.getController().getDasPlot().setTopDecorator(thep);
        } else if ( node instanceof org.autoplot.dom.Canvas ) {
            final org.autoplot.dom.Canvas c= (org.autoplot.dom.Canvas)node;
            Painter thep= new Painter() {
                @Override
                public void paint(Graphics2D g) {
                    painter.__call__(Py.java2py(g));
                }   
            };
            c.getController().getDasCanvas().addTopDecorator(thep);
        }
    }
    
    /**
     * add code that will paint custom graphics on the canvas or on a plot.
     * The command will be invoked after all other painting is done, making
     * the decoration to be on top.  Note plots can only have one decoration,
     * and the Canvas can have any number.  Calling reset() will remove all
     * decorations.
     *<blockquote><pre><small>{@code
def paint(g):
    g.color= Color.BLUE
    for i in xrange(0,1000,100):
        g.drawOval(500-i/2,500-i/2,i,i)

addBottomDecoration( dom.canvases[0], paint )
     *}</small></pre></blockquote>
     * @param node the plot or canvas over which to plot
     * @param painter the PyFunction to call when painting
     * @see https://github.com/autoplot/dev/blob/master/demos/2020/20200229/demoAddBottomDecoration.jy
     * 
     */
    public static void addBottomDecoration( DomNode node, final PyFunction painter ) {
        if ( !( node instanceof Plot ) && ! ( node instanceof org.autoplot.dom.Canvas ) ) {
            throw new IllegalArgumentException("first argument must be plot or canvas");
        }
        if ( node instanceof Plot ) {
            final Plot p= (Plot)node;
            Painter thep= new Painter() {
                @Override
                public void paint(Graphics2D g) {
                    painter.__call__(Py.java2py(g));
                }   
            };
            p.getController().getDasPlot().setBottomDecorator(thep);
        } else if ( node instanceof org.autoplot.dom.Canvas ) {
            final org.autoplot.dom.Canvas c= (org.autoplot.dom.Canvas)node;
            Painter thep= new Painter() {
                @Override
                public void paint(Graphics2D g) {
                    painter.__call__(Py.java2py(g));
                }   
            };
            c.getController().getDasCanvas().addBottomDecorator(thep);
        }
    }
    
    /**
     * return a component which can be used to accumulate data.  This is typically
     * inserted into its own tab with the addTab command.  This is set to
     * sort data in X as it comes in, and this can be disabled with setSorted(False)
     *<blockquote><pre><small>{@code
     *dpr= createDataPointRecorder()
     *addTab( 'digitizer', dpr )
     *}</small></pre></blockquote>
     * 
     * @return a new DataPointRecorder.
     * @see DataPointRecorder
     * @see https://github.com/autoplot/dev/blob/master/demos/digitizers/createDataPointRecorder.jy
     * 
     */
    public static DataPointRecorder createDataPointRecorder(  ) {
        DataPointRecorder result= new DataPointRecorder(true);
        JButton button= new JButton( "Export Data...");
        DataSource dss= new AnonymousDataSource() {
            @Override
            public QDataSet getDataSet(ProgressMonitor mon) throws Exception {
                return result.getDataPoints();
            }
        };
        button.setAction( ExportDataPanel.createExportDataAction( result, dss ) );
        result.addAccessory( button );
        return result;
    }
    
    /**
     * return a new dom in a minimal Autoplot application.  Use result.getCanvas()
     * to get the DasCanvas which can be added to other components.
     *<blockquote><pre><small>{@code
     *report= createApplicationModel()
     *addTab( 'report', report.canvas )
     *report.setDataSet(linspace(0,1,101))
     *}</small></pre></blockquote>
     * @param id
     * @return 
     */
    public static ApplicationModel createApplicationModel( final String id ) {
        ApplicationModel result= applets.get(id);
        if ( result==null ) {
            result= new ApplicationModel();
            result.addDasPeersToApp();
            result.setName(id);
            applets.put(id,result);
        }
        
        return result;
    }
        
    /**
     * set the Autoplot status bar string.  Use the prefixes "busy:", "warning:"
     * and "error:" to set icons.
     * @param message
     * @see #showMessageDialog(java.lang.String) 
     */
    public void setStatus( String message ) {
        dom.getController().setStatus(message);
    }
    
    /**
     * show a popup that you know the user will see.  Note HTML code will work.
     * @param message 
     */
    public void alert( String message ) {
        String m= message;
        int messageType= JOptionPane.INFORMATION_MESSAGE;
        if ( m.startsWith("warning:") ){
            m= m.substring(8).trim();
            messageType= JOptionPane.WARNING_MESSAGE;
        }
        
        JOptionPane.showMessageDialog( view, m, "Message", messageType );
        dom.getController().setStatus("warning: "+m);
    }
    
    /**
     * show a popup to the scientist, which they must acknowledge before this
     * returns.
     * @param message, possibly containing HTML.
     * @see #setStatus(java.lang.String) 
     */
    public void showMessageDialog( String message ) {
        if ( message.split("\n").length>15 ) {
            JScrollPane pane= new JScrollPane( new JTextArea(message) );
            pane.setPreferredSize( new Dimension(800,600) );
            pane.setMaximumSize( new Dimension(800,600) );
            JOptionPane.showMessageDialog( view, pane );
        } else {
            JOptionPane.showMessageDialog( view, message );
        }
    }

    
    /**
     * add a tab to the running application.  A new tab will be added with the
     * label, with the component added within a scroll pane.
     * @param label the label for the component.
     * @param c the component to add.
     * @see AutoplotUI#setLeftPanel(javax.swing.JComponent) setLeftPanel which adds to the GUI
     */
    public void addTab( final String label, final JComponent c  ) {
        Runnable run= new Runnable() {
            @Override
            public void run() {
                maybeInitView();
                int n= view.getTabs().getComponentCount();
                for ( int i=0; i<n; i++ ) {
                    final String titleAt = view.getTabs().getTitleAt(i);
                    if ( titleAt.equals(label) || titleAt.equals("("+label+")") ) { //DANGER view is model
                        view.getTabs().remove( i );
                        break;
                    }
                }
                if ( GuiUtil.hasScrollPane(c) ) {
                    view.getTabs().add(label,c);
                } else {
                    JScrollPane jsp= new JScrollPane();
                    jsp.getViewport().add(c);
                    view.getTabs().add(label,jsp);        
                }
            }
        };
        if ( SwingUtilities.isEventDispatchThread() ) {
            run.run();
        } else {
            SwingUtilities.invokeLater(run);
        }

    }
    
    /**
     * Set the style used to render the data using a string identifier:
     *   spectrogram, series, scatter, histogram, fill_to_zero, digital
     * @param name string name of the plot style.
     */
    public void setRenderStyle( String name ) {
        dom.getController().getPlotElement().setRenderType( RenderType.valueOf(name) );
    }

    /**
     * This is intended to be used with a debugger.  The developer should put
     * a breakpoint at the out.write statement, and then call peekAt from
     * the script.
     *
     * @param o any object we want to look at.
     * @throws java.io.IOException
     */
    public void peekAt(Object o) throws IOException {
        out.write(o.toString().getBytes());
    }

    /**
     * return the local filename from the string which can start with file://.
     **<blockquote><pre><small>{@code
     *    in: file://tmp/data/autoplot.png
     *   out: /tmp/data/autoplot.png
     *    in: test016_006.png
     *   out: test016_006.png
     *    in: http://autoplot.org/data/
     *    throws IllegalArgumentException
     *    in: file://tmp/data/autoplot.xls?sheet=sheet1
     *   out: /tmp/data/autoplot.xls?sheet=sheet1
     *    in: /tmp/has spaces.png
     *   out: /tmp/has spaces.png
     *    in: C:\Users\Documents and Settings\
     *   out: C:\Users\Documents and Settings\
     *}</small></pre></blockquote>
     * @param filename like "file:/tmp/data/autoplot.png"
     * @return  "/tmp/data/autoplot.png"
     * @throws IllegalArgumentException if the filename reference is not a local reference.
     */
    private static String getLocalFilename( String filename ) {
        String fp;
        String qp=null; // query part
        int iq= filename.indexOf('?');
        if ( iq>-1 ) {
            fp = filename.substring(0,iq);
            qp= filename.substring(iq);
        } else {
            fp= filename;
        }

        if ( filename.contains("/") || filename.contains("\\") ) {
            URISplit split= URISplit.parse(filename);
            if ( split.path==null ) {
                throw new IllegalArgumentException("something is wrong with the specified filename: "+filename);
            }
            if ( !"file".equals(split.scheme) ) {
                throw new IllegalArgumentException("cannot write to "+filename+ " because it must be local file");
            }
            if ( fp.startsWith("file:") ) {
                filename= split.file.substring(split.scheme.length()+1); //TODO: this is sloppy.
                if ( split.params!=null ) {
                    filename= filename + "?"+ split.params;
                }
                if ( filename.startsWith("///" ) ) filename= filename.substring(2);
                return filename;
            } else {
                if ( qp!=null ) {
                    return fp + qp;
                } else {
                    return fp;
                }
            }
        } else {
            String pwd= new File("").getAbsolutePath();
            return pwd + File.separator + filename;
        }
    }
    
    /**
     * write out the current canvas to a png file.
     * TODO: bug 557: this has issues with the size.  It's coded to get the size from
     *  the DOM, but if it is fitted and has a container it must get size from
     *  the container.  Use writeToPng( filename, width, height ) instead for now.
     *  See writeToPdf(String filename), which appears to have a fix for this that
     *  would affect how this is resolved.
     * Note for relative references, this will use the Java process present working directory (PWD) instead
     * of the PWD variable found in scripts
     * @param filename The name of a local file
     * @throws java.io.IOException
     */
    public void writeToPng(String filename) throws IOException {
        setStatus("writing to "+filename);
        if ( !( filename.endsWith(".png") || filename.endsWith(".PNG") ) ) {
            filename= filename + ".png";
        }
        filename= getLocalFilename(filename);
        waitUntilIdle();

        int width= model.getDocumentModel().getCanvases(0).getWidth();
        int height= model.getDocumentModel().getCanvases(0).getHeight();
        logger.log(Level.FINER, "writeToPng {0} by {1} {2}", new Object[]{width, height, filename});
        writeToPng( filename, width, height );
        File f= new File(filename);
        setStatus("wrote to "+f.getAbsolutePath());
    }

    private void maybeMakeParent( String filename ) throws IOException {
        filename= getLocalFilename(filename);
        File file= new File(filename);
        File parentFile= file.getParentFile();
        if ( parentFile!=null ) { // relative filenames are okay.
            if ( !parentFile.exists() ) {
                if ( !parentFile.mkdirs() ) {
                    throw new IOException( "unable to mkdir: "+file.getParentFile() );
                }
            }
        }
    }
    
    /**
     * The name of the script which results in the image, optionally with its arguments.
     * @see org.das2.util.DasPNGConstants
     */
    public static final String PNG_KEY_SCRIPT="AutoplotScriptURI";
    
    /**
     * The Autoplot .vap file which results in the image, optionally with "?" and modifiers.
     * @see org.das2.util.DasPNGConstants
     */
    public static final String PNG_KEY_VAP="AutoplotVap";
    
    /**
     * The Autoplot URI which results in the image.
     * @see org.das2.util.DasPNGConstants
     */
    public static final String PNG_KEY_URI="AutoplotURI";
        
    /**
     * write out the current canvas to a png file.
     * TODO: bug 557: this has issues with the size.  It's coded to get the size from
     *  the DOM, but if it is fitted and has a container it must get size from
     *  the container.  User writeToPng( filename, width, height ) instead for now.
     *  See writeToPdf(String filename), which appears to have a fix for this that
     *  would affect how this is resolved.
     * Note for relative references, this will use the Java process present working directory (PWD) instead
     * of the PWD variable found in scripts
     * @param filename The name of a local file
     * @param width the width in pixels of the png
     * @param height the height in pixels of the png
     * @throws java.io.IOException
     */
    public void writeToPng( String filename, int width, int height ) throws IOException {
        filename= getLocalFilename(filename);
        
        BufferedImage image = model.canvas.getImage( width, height );
        
        Logger llogger= Logger.getLogger("autoplot.scriptcontext.writeToPng");
        llogger.log(Level.FINE, "writeToPng({0},{1},{2})->{3},{4} image.", new Object[]{filename, width, height, image.getWidth(), image.getHeight()});
        Map<String,String> meta= new LinkedHashMap<>();
        meta.put( DasPNGConstants.KEYWORD_SOFTWARE, "Autoplot" );
        meta.put( DasPNGConstants.KEYWORD_PLOT_INFO, model.canvas.getImageMetadata() );
        writeToPng( image, filename, meta );
    }

    /**
     * write out the current canvas to a png file, using the given size and also insert
     * additional metadata.
     * Note for relative references, this will use the Java process present working directory (PWD) instead
     * of the PWD variable found in scripts
     * @param filename The name of a local file
     * @param width the width in pixels of the png
     * @param height the height in pixels of the png
     * @param metadata if non-null, then write name/values pairs into the PNG Metadata. "Creation Time", "Software" and "plotInfo" are always added.
     * @see 
     * @throws java.io.IOException
     */
    public void writeToPng( String filename, int width, int height, Map<String,String> metadata ) throws IOException {
        filename= getLocalFilename(filename);
        
        BufferedImage image = model.canvas.getImage( width, height );
        
        Logger llogger= Logger.getLogger("autoplot.scriptcontext.writeToPng");
        llogger.log(Level.FINE, "writeToPng({0},{1},{2})->{3},{4} image.", new Object[]{filename, width, height, image.getWidth(), image.getHeight()});
        Map<String,String> meta= new LinkedHashMap<>();
        meta.putAll(metadata);
        meta.put( DasPNGConstants.KEYWORD_SOFTWARE, "Autoplot" );
        meta.put( DasPNGConstants.KEYWORD_PLOT_INFO, model.canvas.getImageMetadata() );
        writeToPng( image, filename, meta );
    }
    
    /**
     * See also writeToPng( OutputStream out )
     * @param image the image to write out.
     * @param filename the name of the output file.
     * @param metadata if non-null, then write name/values pairs into the PNG Metadata. "Creation Time" is always added.  http://englishjavadrinker.blogspot.com/2010/09/png-keywords_12.html
     * @throws IOException
     */
    public void writeToPng( BufferedImage image, String filename, Map<String,String> metadata ) throws IOException {
        
        logger.log(Level.CONFIG, "writeToPng(image,{0},metadata)", new Object[]{filename});
        if ( !( filename.endsWith(".png") || filename.endsWith(".PNG") ) ) {
            filename= filename + ".png";
        }

        filename= getLocalFilename(filename);

        waitUntilIdle();
        
        maybeMakeParent(filename);
        
        final FileOutputStream out1 = new FileOutputStream(filename);

        DasPNGEncoder encoder = new DasPNGEncoder();
        encoder.addText(DasPNGConstants.KEYWORD_CREATION_TIME, new Date().toString());
        if ( metadata!=null ) {
            for ( Entry<String,String> m: metadata.entrySet() ) {
                encoder.addText( m.getKey(), m.getValue() );
            }
        }
        try {
            encoder.write( image, out1);
        } catch (IOException ioe) {
        } finally {
            try {
                out1.close();
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
    }

    /**
     * write out the current canvas to stdout.  This is introduced to support servers.
     * TODO: this has issues with the size.  See writeToPng(filename).
     * @param out the OutputStream accepting the data, which is not closed.
     * @throws java.io.IOException
     */
    public void writeToPng(OutputStream out) throws IOException {
        waitUntilIdle();

        DasCanvas c = model.getCanvas();
        int width= model.getDocumentModel().getCanvases(0).getWidth();
        int height= model.getDocumentModel().getCanvases(0).getHeight();

        BufferedImage image = c.getImage(width,height);

        DasPNGEncoder encoder = new DasPNGEncoder();
        encoder.addText(DasPNGConstants.KEYWORD_CREATION_TIME, new Date().toString());
        encoder.addText(DasPNGConstants.KEYWORD_SOFTWARE, "Autoplot" );
        encoder.addText(DasPNGConstants.KEYWORD_PLOT_INFO, c.getImageMetadata() );        

        encoder.write( image, out);

    }
    
    /**
     * write out the current canvas to a svg file.
     * Note for relative references, this will use the Java process present working directory (PWD) instead
     * of the PWD variable found in scripts
     * @param filename the local file to write the file.
     * @throws java.io.IOException
     */    
    public void writeToSvg( String filename ) throws IOException {
        setStatus("writing to "+filename);
        if ( !( filename.endsWith(".svg") || filename.endsWith(".SVG") ) ) {
            filename= filename + ".svg";
        }
        filename= getLocalFilename(filename);
        
        waitUntilIdle();
        int width= model.getDocumentModel().getCanvases(0).getWidth();
        int height= model.getDocumentModel().getCanvases(0).getHeight();
        model.getCanvas().setSize( width, height );
        model.getCanvas().validate();
        waitUntilIdle();

        maybeMakeParent(filename);

        model.getCanvas().writeToSVG(filename);
        setStatus("wrote to "+filename);        
    }

    /**
     * write out the current canvas to stdout.  This is introduced to support servers.
     * @param out the OutputStream accepting the data, which is not closed.
     * @throws java.io.IOException
     */
    public void writeToSvg(OutputStream out) throws IOException {
        waitUntilIdle();

        int width= model.getDocumentModel().getCanvases(0).getWidth();
        int height= model.getDocumentModel().getCanvases(0).getHeight();
        model.getCanvas().setSize( width, height );
        model.getCanvas().validate();
        waitUntilIdle();
        model.getCanvas().writeToGraphicsOutput( out, new SvgGraphicsOutput() );

    }
    
    /**
     * write out the current canvas to a pdf file.
     * TODO: this has issues with the size.  See writeToPng(filename).  It looks
     *   like this might be handled here
     * Note for relative references, this will use the Java process present working directory (PWD) instead
     * of the PWD variable found in scripts
     * @param filename the local file to write the file.
     * @throws java.io.IOException
     */
    public void writeToPdf(String filename) throws IOException {
        setStatus("writing to "+filename);
        if ( !( filename.endsWith(".pdf") || filename.endsWith(".PDF") ) ) {
            filename= filename + ".pdf";
        }
        filename= getLocalFilename(filename);
        
        waitUntilIdle();
        int width= model.getDocumentModel().getCanvases(0).getWidth();
        int height= model.getDocumentModel().getCanvases(0).getHeight();
        model.getCanvas().setSize( width, height );
        model.getCanvas().validate();
        waitUntilIdle();

        maybeMakeParent(filename);

        model.getCanvas().writeToPDF(filename);
        setStatus("wrote to "+filename);
    }

    /**
     * write out the current canvas to a pdf to the output stream.  This is to
     * support servers.
     * TODO: this has issues with the size.  See writeToPng(filename).  It looks
     *   like this might be handled here
     * @param out the OutputStream
     * @throws java.io.IOException
     */
    public void writeToPdf( OutputStream out ) throws IOException {
        waitUntilIdle();
        int width= model.getDocumentModel().getCanvases(0).getWidth();
        int height= model.getDocumentModel().getCanvases(0).getHeight();
        model.getCanvas().setSize( width, height );
        model.getCanvas().validate();
        waitUntilIdle();
        model.getCanvas().writeToGraphicsOutput( out, new PdfGraphicsOutput() );
    }

    /**
     * creates a BufferedImage from the provided DOM.  This blocks until the
     * image is ready.
     * @param applicationIn
     * @return the image
     */
    public static BufferedImage writeToBufferedImage( Application applicationIn ) {
        for ( DataSourceFilter dsf : applicationIn.getDataSourceFilters() ) {
            if ( dsf.getUri().equals("vap+internal:") ) {
                logger.fine("copy over vap+internal datasets.");
            }
        }
        ApplicationModel appmodel= new ApplicationModel();
        appmodel.addDasPeersToAppAndWait();
        appmodel.getDocumentModel().syncTo(applicationIn);

        DomUtil.copyOverInternalData( applicationIn, appmodel.getDocumentModel() );

        int height= applicationIn.getCanvases(0).getHeight();
        int width= applicationIn.getCanvases(0).getWidth();
        
        BufferedImage image= appmodel.getCanvas().getImage(width, height);
        
        return image;
    }

    /**
     * convenient method for getting an image from the current canvas.
     * @return
     */
    public BufferedImage writeToBufferedImage( ) {
        waitUntilIdle();
        
        int height= model.getDocumentModel().getCanvases(0).getHeight();
        int width= model.getDocumentModel().getCanvases(0).getWidth();

        BufferedImage image= model.getDocumentModel().getCanvases(0).getController().getDasCanvas().getImage(width, height);
        return image;
    }
    /**
     * return an array of URLs that match the spec for the time range provided.
     * For example,
     * <p><blockquote><pre>
     *  uri= 'http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX'
     *  xx= getTimeRangesFor( uri, '2000-jan', '$Y-$d-$m' )
     *  for x in xx:
     *    print x
     * </pre></blockquote><p>
     *
     * @param surl an Autoplot uri with an aggregation specifier.
     * @param timeRange a string that is parsed to a time range, such as "2001"
     * @param format format for the result, such as "%Y-%m-%d"
     * @return a list of URLs without the aggregation specifier.
     * @throws java.io.IOException if the remote folder cannot be listed.
     * @throws java.text.ParseException if the timerange cannot be parsed.
     */
    public static String[] getTimeRangesFor(String surl, String timeRange, String format) throws IOException, ParseException {
        return org.autoplot.jythonsupport.Util.getTimeRangesFor( surl, timeRange, format );
    }

    /**
     * Given a spec to format timeranges and a range to contain each timerange,
     * produce a list of all timeranges covering the range formatted with the
     * spec.  For example, <code>generateTimeRanges( "%Y-%m-%d", "Jun 2009" )</code> would result in
     * 2009-06-01, 2009-06-02, ..., 2009-06-30.
     * @param spec such as "%Y-%m".  Note specs like "%Y%m" will not be parsable.
     * @param srange range limiting the list, such as "2009"
     * @return a string array of formatted time ranges, such as [ "2009-01", "2009-02", ..., "2009-12" ]
     * @throws java.text.ParseException of the outer range cannot be parsed.
     */
    public static String[] generateTimeRanges( String spec, String srange ) throws ParseException {
        return org.autoplot.jythonsupport.Util.generateTimeRanges( spec, srange );
    }
    
    /**
     * return a list of completions.  This is useful in the IDL context
     * as well as Jython scripts.  This will perform the completion for where the carot is
     * at the end of the string.  Only completions where maybePlot indicates the URI is now 
     * valid are returned, so for example https://autoplot.org/data/somedata.cdf?noDep is not
     * returned and https://autoplot.org/data/somedata.cdf?Magnitude is.
     * Note this is included to continue support as this is described in https://autoplot.org/developer.idlMatlab.
     * @param file, for example https://autoplot.org/data/somedata.cdf?
     * @return list of completions, containing the entire URI.
     * @throws java.lang.Exception any exception thrown by the data source.
     */
    public static String[] getCompletions( String file ) throws Exception {
        return org.autoplot.jythonsupport.Util.getAllCompletions(file);
    }
    
    /**
     * sleep for so many milliseconds.  This is introduced to avoid the import,
     * which makes running scripts securely non-trivial.
     * @param millis number of milliseconds to pause execution
     */
    public static void sleep( int millis ) {
        org.autoplot.jythonsupport.Util.sleep( millis );
    }
    
    /**
     * Export the data into a format implied by the filename extension.  
     * See the export data dialog for additional parameters available for formatting.
     *
     * For example:
     *<blockquote><pre><small>{@code
     * ds= getDataSet('http://autoplot.org/data/somedata.cdf?BGSEc')
     * formatDataSet( ds, 'vap+dat:file:/home/jbf/temp/foo.dat?tformat=minutes&format=6.2f')
     *}</small></pre></blockquote>
     * 
     * @param ds
     * @param file local file name that is the target
     * @throws java.lang.Exception
     */
    public static void formatDataSet(QDataSet ds, String file) throws Exception {
        
        formatDataSet( ds, file, new NullProgressMonitor());

    }
    
    /**
     * Export the data into a format implied by the filename extension.  
     * See the export data dialog for additional parameters available for formatting.
     *
     * For example:
     *<blockquote><pre><small>{@code
     * ds= getDataSet('http://autoplot.org/data/somedata.cdf?BGSEc')
     * formatDataSet( ds, 'vap+dat:file:/home/jbf/temp/foo.dat?tformat=minutes&format=6.2f')
     * formatDataSet( ds, 'foo.dat' ) # also okay
     *}</small></pre></blockquote>
     * 
     * @param ds
     * @param file local file name that is the target.
     * @param monitor
     * @throws java.lang.Exception
     */
    public static void formatDataSet(QDataSet ds, String file, ProgressMonitor monitor ) throws Exception {

        if ( !file.startsWith("/") && !file.startsWith("vap+") ) {
            String s= getLocalFilename(file);
            file= s;
        }

        DataSourceFormat format;
        try {
            format = DataSetURI.getDataSourceFormat( DataSetURI.getURI(file) );
        } catch ( URISyntaxException ex ) {
            URISplit split= URISplit.parse(file); // fall back to the old logic
            URI uri = split.resourceUri; 
            format = DataSetURI.getDataSourceFormat( uri );
        }
        
        if (format == null) {
            throw new IllegalArgumentException("no format for extension: " + file);
        }

        format.formatData( file, ds, monitor );

    }
    
    
    /**
     * set the title of the plot.
     * @param title
     */
    public void setTitle(String title) {
        model.getDocumentModel().getController().getPlot().setTitle(title);
    }
    
    /**
     * create a model with a GUI presentation layer.  If the GUI is already 
     * created, then this does nothing.
     */
    public void createGui() {
        maybeInitView();
    }

    /**
     * returns the internal application model (the object that does all the 
     * business).  This provides access to the internal model for power users.
     * Note the applicationModel provides limited access, and the DOM now
     * provides full access to the application.
     * @return ApplicationModel object
     */
    public ApplicationModel getApplicationModel() {
        maybeInitModel();
        return model;
    }

    /**
     * provide way to see if the model is already initialized (e.g. for clone application)
     * @return true is the model is already initialized.
     */
    public boolean isModelInitialized() {
        return model!=null;
    }

    /**
     * binds two bean properties together.  Bindings are bidirectional, but
     * the initial copy is from src to dst.  In MVC terms, src should be the model
     * and dst should be a view.  The properties must fire property
     * change events for the binding mechanism to work.
     * 
     * As of v2014a_10, if the src is a DomNode and a child of the application, then use
     * dom.getController().bind so that the vap will contain the binding.
     * 
     * Example:
     *<blockquote><pre><small>{@code
     * model= getApplicationModel()
     * bind( model.getPlotDefaults(), "title", model.getPlotDefaults().getXAxis(), "label" )
     *}</small></pre></blockquote>
     * 
     * @see org.autoplot.dom.ApplicationController#bind(org.autoplot.dom.DomNode, java.lang.String, java.lang.Object, java.lang.String) which will save the binding to a vap.
     * @param src java bean such as model.getPlotDefaults()
     * @param srcProp a property name such as "title"
     * @param dst java bean such as model.getPlotDefaults().getXAxis()
     * @param dstProp a property name such as "label"
     */
    public void bind( Object src, String srcProp, Object dst, String dstProp ) {
        bind( src, srcProp, dst, dstProp, null );
    }
    
    /**
     * binds two bean properties together.  Bindings are bidirectional, but
     * the initial copy is from src to dst.  In MVC terms, src should be the model
     * and dst should be a view.  The properties must fire property
     * change events for the binding mechanism to work.
     * 
     * As of v2014a_10, if the src is a DomNode and a child of the application, then use
     * dom.getController().bind so that the vap will contain the binding.
     * 
     * Example:
     *<blockquote><pre><small>{@code
     * model= getApplicationModel()
     * bind( model.getPlotDefaults(), "title", model.getPlotDefaults().getXAxis(), "label" )
     *}</small></pre></blockquote>
     * 
     * @see org.autoplot.dom.ApplicationController#bind(org.autoplot.dom.DomNode, java.lang.String, java.lang.Object, java.lang.String, org.jdesktop.beansbinding.Converter) which will save the binding to a vap.
     * @param src java bean such as model.getPlotDefaults()
     * @param srcProp a property name such as "title"
     * @param dst java bean such as model.getPlotDefaults().getXAxis()
     * @param dstProp a property name such as "label"
     * @param c converter for the binding, or null.
     */    
    public void bind( Object src, String srcProp, Object dst, String dstProp, Converter c ) {
        if ( DasApplication.hasAllPermission() ) {
            if ( src instanceof DomNode && dom.getController().getElementById(((DomNode)src).getId())==src ) {
                DomNode srcNode= (DomNode)src;
                dom.getController().bind( srcNode, srcProp, dst, dstProp, c );
            } else {
                BeanProperty srcbp= BeanProperty.create(srcProp);
                Object value= srcbp.getValue(src);
                if ( value==null ) {
                    System.err.println("warning: src property "+srcProp+ " of "+src+" is null");
                }
                BeanProperty dstbp= BeanProperty.create(dstProp);
                dstbp.setValue(dst, value );
                dstbp.getValue(dst);
                Binding b= Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, src, srcbp, dst, dstbp );
                if ( c!=null ) b.setConverter(c);
                b.bind();
            }
        } else {
            System.err.println("bindings disabled in applet environment");
        }
    }
    
    /**
     * unbind the property
     * @param src 
     */
    public void unbind( DomNode src ) {
        dom.getController().unbind(src);
    }

    /**
     * unbind the property
     * @param src 
     * @param srcProp 
     * @param dst 
     * @param dstProp 
     */
    public void unbind( DomNode src, String srcProp, DomNode dst, String dstProp ) {
        dom.getController().unbind(src,srcProp,dst,dstProp);
    }
    

    /**
     * binds two bean properties together.  Bindings are bidirectional, but
     * the initial copy is from src to dst.  In MVC terms, src should be the model
     * and dst should be a view.  The properties must fire property
     * change events for the binding mechanism to work.
     * 
     * As of v2014a_10, if the src is a DomNode and a child of the application, then use
     * dom.getController().bind so that the vap will contain the binding.
     * 
     * Example:
     *<blockquote><pre><small>{@code
     * model= getApplicationModel()
     * bind( model.getPlotDefaults(), "title", model.getPlotDefaults().getXAxis(), "label" )
     *}</small></pre></blockquote>
     * 
     * @see org.autoplot.dom.ApplicationController#bind(org.autoplot.dom.DomNode, java.lang.String, java.lang.Object, java.lang.String, org.jdesktop.beansbinding.Converter) which will save the binding to a vap.
     * @param src java bean such as model.getPlotDefaults()
     * @param srcProp a property name such as "title"
     * @param dst java bean such as model.getPlotDefaults().getXAxis()
     * @param dstProp a property name such as "label"
     * @param c converter for the binding, or null.
     */    
    public void bindGuiSafe( final Object src, final String srcProp, 
        final Object dst, final String dstProp, final Converter c ) {
        Runnable run= new Runnable() {
            @Override
            public void run() {
                bind( src, srcProp, dst, dstProp, c );
            }
        };
        if ( SwingUtilities.isEventDispatchThread() ) {
            run.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(run);
            } catch ( InterruptedException|InvocationTargetException ex ) {
                logger.log( Level.WARNING, ex.getMessage(), ex );
            }
        }
    }

    
    /**
     * serializes the dataset to a QStream, a self-documenting, streaming format
     * useful for moving datasets.
     *
     * <p><blockquote><pre>
     *ds= getDataSet('http://autoplot.org/data/somedata.cdf?BGSEc')
     *from java.lang import System
     *dumpToQStream( ds, System.out, True )
     * </pre></blockquote></p>
     *
     * @param ds The dataset to stream.  Note all schemes should be streamable, but
     *   some bugs exist that may prevent this.
     * @param out stream, such as "System.out"
     * @param ascii use ascii transfer types, otherwise binary are used.
     * @throws java.io.IOException 
     */
    public static void dumpToQStream( QDataSet ds, OutputStream out, boolean ascii ) throws IOException {
        try {
            SimpleStreamFormatter f= new SimpleStreamFormatter();
            f.format( ds, out, ascii );
        } catch (StreamException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }


    /**
     * serializes the dataset to a das2stream, a well-documented, open, streaming
     * data format. (that's a joke.)  Das2Streams are the legacy stream format used
     * by the Plasma Wave Groups's server, and can serialize a limited set of
     * QDataSets.  QStreams were introduced to allow streaming of any QDataSet, see
     * dumpToQStream.
     * Currently, to keep the channel open, the stream is created in a buffer and 
     * then the buffer is sent.
     * TODO: write a stream-producing code that doesn't close the output stream.  (TODO: does it still do this?)
     * @param ds
     * @param ascii use ascii transfer types, otherwise binary are used.
     */
    public static void dumpToDas2Stream(QDataSet ds, boolean ascii) {
        try {
            ByteArrayOutputStream bufout = new ByteArrayOutputStream(10000);
            DataSet lds = DataSetAdapter.createLegacyDataSet(ds);
            if (ascii) {
                if (lds instanceof TableDataSet) {
                    org.das2.dataset.TableUtil.dumpToAsciiStream((TableDataSet) lds, bufout);
                } else {
                    org.das2.dataset.VectorUtil.dumpToAsciiStream((VectorDataSet) lds, bufout);
                }
            } else {
                if (lds instanceof TableDataSet) {
                    org.das2.dataset.TableUtil.dumpToBinaryStream((TableDataSet) lds, bufout);
                } else {
                    org.das2.dataset.VectorUtil.dumpToBinaryStream((VectorDataSet) lds, bufout);
                }

            }
            out.write(bufout.toByteArray());
        } catch (IOException ex) {
        }
    }

    /**
     * serializes the dataset to a das2stream, a well-documented, open, streaming
     * data format. (that's a joke.)  
     * Currently, to keep the channel open, the stream is created in a buffer and 
     * then the buffer is sent.  TODO: write a stream-producing code that doesn't
     * close the output stream.
     * @param ds
     * @param file the file target for the stream.
     * @param ascii use ascii transfer types.
     * @throws java.io.IOException
     */
    public static void dumpToDas2Stream(QDataSet ds, String file, boolean ascii) throws IOException {

        file= getLocalFilename(file);

        OutputStream bufout = new FileOutputStream(file);
        DataSet lds = DataSetAdapter.createLegacyDataSet(ds);
        if (ascii) {
            if (lds instanceof TableDataSet) {
                org.das2.dataset.TableUtil.dumpToAsciiStream((TableDataSet) lds, bufout);
            } else {
                org.das2.dataset.VectorUtil.dumpToAsciiStream((VectorDataSet) lds, bufout);
            }
        } else {
            if (lds instanceof TableDataSet) {
                org.das2.dataset.TableUtil.dumpToBinaryStream((TableDataSet) lds, bufout);
            } else {
                org.das2.dataset.VectorUtil.dumpToBinaryStream((VectorDataSet) lds, bufout);
            }
        }
    }

    /**
     * make the directory.  This must be a local file right now, but may start with "file://"
     * @param dir the directory
     */
    public static void mkdir( String dir ) {
        
        dir= getLocalFilename(dir);

        if ( !dir.endsWith("/") ) {
            throw new IllegalArgumentException("folder name must end in /");
        }

        File f= new File(dir);
        if ( !f.exists() ) {
            if ( !f.mkdirs() ) {
                throw new IllegalArgumentException("unable to make directory: "+f );
            }
        }
    }

    /**
     * get the document model (DOM).  This may initialize the model, in which
     * case defaults like the cache directory are set.
     * @return
     */
    public Application getDocumentModel() {
        maybeInitModel();
        return dom;
    }


    /**
     * wait until the application is idle.  This does a model.waitUntilIdle,
     * but also checks for the DataSetSelector for pending operations.
     */
    public void waitUntilIdle() {
        if ( view!=null ) {
            while ( view.getDataSetSelector().isPendingChanges() ) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
        }
        model.waitUntilIdle();
    }

    /**
     * wait until the application is idle.  The id is a convenience to 
     * developers when debugging, for example used to trigger breakpoints.
     * @param id string for debugging.
     *@see http://autoplot.org/data/tools/reloadAll.jy
     */
    public void waitUntilIdle( String id ) {
        logger.log(Level.INFO, "waitUntilIdle({0})", id);
        if ( view!=null ) {
            while ( view.getDataSetSelector().isPendingChanges() ) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    Logger.getLogger(ScriptContext2023.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        model.waitUntilIdle();
    }    

    /**
     * save the current state as a vap file
     * @param filename
     * @throws java.io.IOException
     */
    public void save( String filename ) throws IOException {
        maybeInitModel();
        
        filename= getLocalFilename(filename);
        
        if ( ! filename.endsWith(".vap") )
            throw new IllegalArgumentException("filename must end in vap");

        model.doSave( new File( filename ) );
    }

    /**
     * load the .vap file.  This is implemented by calling plot on the URI.
     * @param filename local or remote filename like http://autoplot.org/data/autoplot.vap
     * @throws java.io.IOException
     */
    public void load( String filename ) throws IOException {
        plot(filename);
    }
    
    /**
     * load a vap from a file and return the dom.
     * @param filename .vap file
     * @return Application
     * @throws java.io.IOException
     * @see #saveVap(org.autoplot.dom.Application, java.lang.String) 
     */
    public static Application loadVap( String filename ) throws IOException {
        try {
            File f= FileSystemUtil.doDownload( filename, new NullProgressMonitor() );
            return (Application) StatePersistence.restoreState( f );
        } catch (FileSystem.FileSystemOfflineException ex) {
            throw new IOException(ex);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
    
    /**
     * save the application dom to a file.
     * @param dom the application state
     * @param filename the file.
     * @throws IOException 
     * @see #loadVap(java.lang.String) 
     */
    public static void saveVap( Application dom, String filename ) throws IOException {
        StatePersistence.saveState( new File(filename), dom );
    }
    
    /**
     * make the layout more efficient by removing empty spaces and overlapping 
     * plots.
     */
    public void fixLayout() {
        org.autoplot.dom.DomOps.newCanvasLayout(dom);
    }
    
    /**
     * make a stack plot.
     * @param nrows number of rows (plots)
     */
    public void setLayout( int nrows ) {
        if ( nrows<1 ) throw new IllegalArgumentException("must be one or more rows");
        setLayout( nrows, 1 );
    }
    
    /**
     * reset the layout to have the given number of rows and columns.  This is
     * similar to the subplot command in Matlab and !p.multi in IDL.  Once 
     * additional plots are added, use the plot command with the index argument.
     * 
     * @param nrows number of rows
     * @param ncolumns number of columns
     */
    public void setLayout( int nrows, int ncolumns ) {
        if ( nrows<1 ) throw new IllegalArgumentException("must be one or more rows");
        if ( ncolumns<1 ) throw new IllegalArgumentException("must be one or more columns");        
        reset();
        DasCanvas c= dom.getCanvases(0).getController().getDasCanvas();
        Lock canvasLock= c.mutatorLock();
        Lock lock= dom.getController().mutatorLock();
        try {
            canvasLock.lock();
            lock.lock();
            Plot p= dom.getController().getPlot();
            dom.getController().addPlots( nrows, ncolumns, null );
            dom.getController().deletePlot(p);
        } finally {
            lock.unlock();
            canvasLock.unlock();
        }
        waitUntilIdle();
    }
    
    /**
     * adds a block of plots to the canvas below the focus plot. 
     * A plotElement is added for each plot as well. 
     * @param nrows number of rows
     * @param ncolumns number of columns
     * @param dir below, above, right, or left, or null (None in Jython) to replace the current plot.
     * @return the new plots.
     */
    public List<Plot> addPlots( int nrows, int ncolumns, String dir ) {
        if ( nrows<1 ) throw new IllegalArgumentException("must be one or more rows");
        if ( ncolumns<1 ) throw new IllegalArgumentException("must be one or more columns");
        Plot d= null;
        if ( dir==null ) {
            d= dom.getController().getPlot();
        }
        List<Plot> result= dom.getController().addPlots( nrows, ncolumns, dir );
        if ( dir==null ) {
            dom.getController().deletePlot(d);
        }
        if ( result.size()>0 ) dom.getController().setPlot(result.get(0));
        return result;
    }
    
    /**
     * make a single plot with so many plot elements.
     * @param nplotElement the number of plotElements on the one plot.
     * @see #setLayout(int, int) setLayout(int, int) which will create a grid of plots.
     * @see #addPlotElement(int) addPlotElement(int) which will an another plotElement (an overplot) to the ith position.
     */
    public void setLayoutOverplot( int nplotElement ) {
        if ( nplotElement<1 ) throw new IllegalArgumentException("must be one or more plots");
        reset();
        DasCanvas c= dom.getCanvases(0).getController().getDasCanvas();
        Lock canvasLock= c.mutatorLock();
        Lock lock= dom.getController().mutatorLock();
        try {
            canvasLock.lock();
            lock.lock();
            Plot p= dom.getController().getPlot();
            for ( int i=1; i<nplotElement; i++ ) {
                dom.getController().addPlotElement( p, null );
            }
        } finally {
            lock.unlock();
            canvasLock.unlock();
        }
    }
            
    /**
     * reset the application to its initial state.
     */
    public void reset() {
        maybeInitModel();
        dom.getController().reset();
        AutoplotUI ui= getApplication();
        if ( ui!=null ) {
            ui.getUndoRedoSupport().resetHistory();
        }
        
    }

    /**
     * called when the application closes so if we reopen it will be in a
     * good state.
     */
    protected void close() {
        model= null;
        view= null;
        out= null;
    }
}
