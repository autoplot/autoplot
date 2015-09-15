/*
 * ApplicationModel.java
 *
 * Created on April 1, 2007, 8:21 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Dimension;
import org.virbo.datasource.AutoplotSettings;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import org.virbo.autoplot.bookmarks.Bookmark;
import java.util.logging.Level;
import org.das2.DasApplication;
import org.das2.graph.DasCanvas;
import org.virbo.qstream.StreamException;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.xml.parsers.ParserConfigurationException;
import org.das2.beans.BeansUtil;
import org.das2.components.DasProgressPanel;
import org.das2.components.propertyeditor.EnumerationEditor;
import org.das2.datum.Datum;
import org.das2.datum.TimeParser;
import org.das2.datum.Units;
import org.das2.event.DasUpdateEvent;
import org.das2.util.ExceptionHandler;
import org.das2.util.Base64;
import org.das2.util.FileUtil;
import org.das2.util.filesystem.FileSystem;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.autoplot.dom.Canvas;
import org.virbo.autoplot.dom.CanvasUtil;
import org.virbo.autoplot.dom.DataSourceController;
import org.virbo.autoplot.dom.DataSourceFilter;
import org.virbo.autoplot.dom.DomUtil;
import org.virbo.autoplot.dom.PlotElement;
import org.virbo.autoplot.dom.Plot;
import org.virbo.autoplot.dom.Row;
import org.virbo.autoplot.layout.LayoutUtil;
import org.virbo.autoplot.state.StatePersistence;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.capability.Caching;
import org.virbo.qstream.QDataSetStreamHandler;
import org.virbo.qstream.SerializeDelegate;
import org.virbo.qstream.SerializeRegistry;
import org.virbo.qstream.SimpleStreamFormatter;
import org.xml.sax.SAXException;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.Bindings;
import org.virbo.autoplot.bookmarks.BookmarksException;
import org.virbo.autoplot.dom.Axis;
import org.virbo.autoplot.dom.BindingModel;
import org.virbo.autoplot.dom.CanvasController;
import org.virbo.datasource.HtmlResponseIOException;
import org.virbo.datasource.Version;
/**
 * Internal model of the application to separate model from view.
 * Note this is the legacy model that still remains from the first implementation
 * of Autoplot, and represents the most simple application.
 * @author jbf
 */
public class ApplicationModel {

    DasApplication application;
    DasCanvas canvas;
    Timer tickleTimer;
    final Application dom;
    private ExceptionHandler exceptionHandler;
    boolean applet= false;

    public void setApplet( boolean v ) {
        this.applet= v;
    }

    public boolean isApplet() {
        return this.applet;
    }
    
    public ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    public void setExceptionHandler(ExceptionHandler eh) {
        this.exceptionHandler= eh;
        DasApplication.getDefaultApplication().setExceptionHandler(exceptionHandler);
        FileSystem.setExceptionHandler(exceptionHandler);
        String cl= eh.getClass().getName();
        if ( cl.equals("org.virbo.autoplot.scriptconsole.GuiExceptionHandler") ) { // support applet, which doesn't know about Gui...
            try {
                Method m= eh.getClass().getMethod("setApplicationModel", ApplicationModel.class);
                m.invoke(eh, this);
                //((GuiExceptionHandler)eh).setApplicationModel(this);
            } catch (IllegalAccessException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            } catch (IllegalArgumentException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            } catch (InvocationTargetException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            } catch (NoSuchMethodException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            } catch (SecurityException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
    }

    /**
     * show a message to the user.
     * @param message
     * @param title
     * @param messageType JOptionPane.WARNING_MESSAGE, JOptionPane.INFORMATION_MESSAGE, JOptionPane.PLAIN_MESSAGE,
     */
    public void showMessage( String message, String title, int messageType ) {
        if (  ! "true".equals(AutoplotUtil.getProperty("java.awt.headless", "false") ) ) {
            Component p= SwingUtilities.getRoot(canvas);
            if ( p==null ) {
                if ( messageType==JOptionPane.WARNING_MESSAGE ) {
                    System.err.println( "WARNING: "+ title + ": " + message  );
                } else if ( messageType==JOptionPane.INFORMATION_MESSAGE ) {
                    System.err.println( "INFO: "+ title + ": " + message  );
                } else {
                    System.err.println( title + ": " + message  );
                }
            } else {
                JOptionPane.showMessageDialog( p, message, title, messageType );
            }
        } else {
            if ( messageType==JOptionPane.WARNING_MESSAGE ) {
                System.err.println( "WARNING: "+ title + ": " + message  );
            } else if ( messageType==JOptionPane.INFORMATION_MESSAGE ) {
                System.err.println( "INFO: "+ title + ": " + message  );
            } else {
                System.err.println( title + ": " + message  );
            }
        }
    }

    static final Logger logger = org.das2.util.LoggerManager.getLogger("autoplot");

    public static final String PREF_RECENT = "recent";
    public static final String PROPERTY_RECENT = PREF_RECENT;
    public static final String PROPERTY_BOOKMARKS = "bookmarks";
    private static final int MAX_RECENT = 20;

    private boolean dontRecordHistory= false;
    
    public ApplicationModel() {

        DataSetURI.init();
        dom = new Application();

        if ( DasApplication.getDefaultApplication().isHeadless() ) {
            logger.fine("history.txt is not being recorded in headless mode");
            dontRecordHistory= true;
        }
    }

    private String name = "";

    public static final String PROP_NAME = "name";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        String oldName = this.name;
        this.name = name;
        propertyChangeSupport.firePropertyChange(PROP_NAME, oldName, name);
    }

    /**
     * addDasPeers should be called from the event thread.  This is intended to support old code that
     * was loose about this with minimal impact on code.  
     */
    public void addDasPeersToAppAndWait() {
        if ( SwingUtilities.isEventDispatchThread() ) {
            addDasPeersToApp();
        } else {
            Runnable run= new Runnable() {
                public void run() {
                    addDasPeersToApp();
                }
            };
            try {
                SwingUtilities.invokeAndWait(run);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            } catch (InvocationTargetException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * This needs to be called after the application model is initialized and
     * preferably from the event thread.
     */
    public void addDasPeersToApp() {
        if ( !applet ) {
            BeansUtil.registerEditor(RenderType.class, EnumerationEditor.class);
            BeansUtil.registerEditor(MouseModuleType.class, EnumerationEditor.class);
        }

        new ApplicationController(this, dom);

        canvas = dom.getController().addCanvas();

        this.application = canvas.getApplication();

        dom.getController().addPlotElement(null, null);
    }

    public DasCanvas getCanvas() {
        return dom.getController().getDasCanvas();
    }
    PropertyChangeListener timeSeriesBrowseListener;
    Caching caching = null;
    ProgressMonitor mon = null;

    /**
     * just plot this dataset.  No capabilities, no urls.  Metadata is set to
     * allow inspection of dataset.
     * @param ds
     */
    void setDataSet(QDataSet ds) {
        dom.getController().getPlotElement().getController().setResetRanges(true);
        dom.getController().getDataSourceFilter().getController().setDataSource(null);
        dom.getController().getDataSourceFilter().setUri("vap+internal:");
        dom.getController().getDataSourceFilter().getController().setDataSetInternal(null); // clear out properties and metadata
        dom.getController().getDataSourceFilter().getController().setDataSetInternal(ds);
    }

    /**
     * just plot this dataset using the specified dataSourceFilter index.  plotElements and dataSourceFilters
     * are added until the index exists.  This is introduced to support jython scripting, but may be
     * useful elsewhere.
     * @param chNum the index of the DataSourceFilter to use.
     * @param label label for the dataset's plotElements, if non-null.
     * @param ds the dataset to plot.
     */
    public void setDataSet( int chNum, String label, QDataSet ds ) {
        while ( dom.getDataSourceFilters().length <= chNum ) {
            Plot p= CanvasUtil.getMostBottomPlot(dom.getController().getCanvas());
            dom.getController().setPlot(p);
            dom.getController().addPlotElement( null, null  );
        }
        DataSourceFilter dsf= dom.getDataSourceFilters(chNum);
        List<PlotElement> elements= dom.getController().getPlotElementsFor( dsf );
        //for ( PlotElement pe: elements ) {
        //    pe.getController().setResetPlotElement(true); //TODO: I would think this would be set anyway with the new datasource.
        //    pe.getController().setResetComponent(true);
        //}
        dsf.getController().setDataSource(null);
        dsf.setUri("vap+internal:");
        dsf.getController().setDataSetInternal(null); // clear out properties and metadata
        dsf.getController().setDataSetInternal(ds);
        if ( label!=null ) {
            for ( PlotElement pe: elements ) {
                pe.setLegendLabel(label);
                pe.setDisplayLegend(true);
            }
        }
    }

    /**
     * just plot this dataset using the specified dataSourceFilter index.  plotElements and dataSourceFilters
     * are added until the index exists.  This is introduced to support jython scripting, but may be
     * useful elsewhere.
     * @param chNum the index of the DataSourceFilter to use.
     * @param label label for the dataset's plotElements, if non-null.
     * @param suri the data source id to plot.
     */
    public void setDataSet( int chNum, String label, String suri ) {
        while ( dom.getDataSourceFilters().length <= chNum ) {
            Plot p= CanvasUtil.getMostBottomPlot(dom.getController().getCanvas());
            dom.getController().setPlot(p);
            dom.getController().addPlotElement( null, null  );
        }
        DataSourceFilter dsf= dom.getDataSourceFilters(chNum);
        List<PlotElement> elements= dom.getController().getPlotElementsFor( dsf );
        for ( PlotElement pe: elements ) {
            pe.getController().setResetPlotElement(true);
            pe.getController().setResetComponent(true);
        }
        dsf.getController().setDataSource(null);
        dsf.setUri(suri);
        if ( label!=null ) {
            for ( PlotElement pe: elements ) {
                pe.setLegendLabel(label);
                pe.setDisplayLegend(true);
            }
        }
    }

    /**
     * just set the focus to the given dataSourceFilter index.  plotElements and dataSourceFilters
     * are added until the index exists.  This is introduced to support code where we reenter
     * autoplot with the position switch, and we can to then call maybePlot so that completions can
     * happen.
     * @param chNum the index of the DataSourceFilter to use.
     */
    public void setFocus( int chNum ) {
        while ( dom.getDataSourceFilters().length <= chNum ) {
            Plot p= CanvasUtil.getMostBottomPlot(dom.getController().getCanvas());
            dom.getController().setPlot(p);
            dom.getController().addPlotElement( null, null  );
        }
        DataSourceFilter dsf= dom.getDataSourceFilters(chNum);
        dom.getController().setDataSourceFilter(dsf);
    }

    public void setDataSource(DataSource dataSource) {
        dom.getController().getDataSourceFilter().getController().resetDataSource(false, dataSource);
    }

    public DataSource dataSource() {
        return dom.getController().getDataSourceFilter().getController().getDataSource();
    }

    public synchronized void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(propertyName, listener);
    }

    public synchronized void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    public synchronized void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
    }

    public synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }



    /**
     * Create a dataSource object and set Autoplot to display this datasource.
     * A dataSource object is created by DataSetURI._getDataSource, which looks
     * at registered data sources to get a factory object, then the datasource is
     * created with the factory object.
     *
     * Preconditions: Any or no datasource is set.
     * Postconditions: A dataSource object is created and Autoplot is set to
     *  plot the datasource.  A thread has been started that will load the dataset.
     *  In headless mode, the dataset has been loaded synchronously.
     *
     * @param surl the new data source URL.
     * @param mon progress monitor which is just used to convey messages.
     */
    protected void resetDataSetSourceURL(String surl, ProgressMonitor mon) {

        if (surl == null) {
            return;
        }  // not really supported

        URISplit split = URISplit.parse(surl);
        surl = URISplit.format(split);
        //surl = DataSetURI.maybeAddFile(surl);

        try {
            if ( split.file!=null && ( split.file.endsWith(".vap") || split.file.endsWith(".vapx" ) ) ) {
                try {
                    surl= surl.replaceAll("\\\\", "/");
                    URI uri = DataSetURI.getURIValid(surl);
                    mon.started();
                    mon.setProgressMessage("loading vap file");
                    this.getDocumentModel().getController().setFocusUri(surl);
                    File openable = DataSetURI.getFile(uri, application.getMonitorFactory().getMonitor(canvas, "loading vap", ""));
                    if (split.params != null) {
                        LinkedHashMap<String, String> params = URISplit.parseParams(split.params);
                        if ( params.containsKey("timerange") && !params.containsKey("timeRange") ) {
                            params.put("timeRange", params.remove("timerange") );
                        }
                        params.put("PWD",split.path);
                        doOpenVap(openable, params);
                    } else {
                        LinkedHashMap<String, String> params = new LinkedHashMap();
                        params.put("PWD",split.path);
                        doOpenVap(openable, params);
                    }
                    mon.setProgressMessage("done loading vap file");
                    mon.finished();
                    addRecent( surl );
                } catch (HtmlResponseIOException ex ) {
                    // we know the URL here, so rethrow it.
                    URL url= ex.getURL();
                    if ( url==null ) {
                        url= new URL( DataSetURI.getURIValid(surl).getSchemeSpecificPart() );
                    }
                    HtmlResponseIOException neww= new HtmlResponseIOException(ex.getMessage(),url);
                    throw new RuntimeException(neww);
                } catch (IOException ex) {
                    mon.finished();
                    throw new RuntimeException(ex);
                }
            } else {
                dom.getController().setFocusUri(null);
                dom.getController().setFocusUri(surl);
                getDataSourceFilterController().resetSuri(surl, mon);
            }
        } catch ( RuntimeException e ) {
            throw e;
            
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Plot this URI, without checking to see if the URI is valid by 
     * checking reject of the data source.
     * When using the AutoplotUI, it is preferable to use AutoplotUI.plotUri, 
     * which will have the effect of typing in the URI and hitting the "go" 
     * arrow button.
     * @throws RuntimeException when _getDataSource throws Exception
     */
    public void setDataSourceURL(String surl) {
        String oldVal = dom.getController().getDataSourceFilter().getUri();
        if (surl == null && oldVal == null) {
            return;
        }

        if (surl != null && surl.equals(oldVal)) {
            return;
        }

        resetDataSetSourceURL(surl, new NullProgressMonitor());
    }

    public String getDataSourceURL() {
        return dom.getController().getDataSourceFilter().getUri();
    }
    protected List<Bookmark> recent = null;
    protected List<Bookmark> bookmarks = null;

    public List<Bookmark> getRecent() {
        if (recent != null) return recent;

        String nodeName= "recent";

        File f2= new File( AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA), "bookmarks/" );
        if ( !f2.exists() ) {
            boolean ok= f2.mkdirs();
            if ( !ok ) {
                throw new RuntimeException("unable to create folder "+ f2 );
            }
        }

        final File f = new File( f2, nodeName + ".xml");
        if ( f.exists() ) {
            try {
                recent = Bookmark.parseBookmarks( AutoplotUtil.readDoc(new FileInputStream(f)).getDocumentElement(), 0 );
            } catch (BookmarksException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                return new ArrayList<Bookmark>();
            } catch (SAXException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                return new ArrayList<Bookmark>();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                return new ArrayList<Bookmark>();
            } catch (ParserConfigurationException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                return new ArrayList<Bookmark>();
            }

        } else {

            Preferences prefs = Preferences.userNodeForPackage(ApplicationModel.class);
            String srecent = prefs.get(PREF_RECENT,"");

            if (srecent.equals("") || !srecent.startsWith("<")) {
                String srecenturl = AutoplotUtil.getProperty("autoplot.default.recent", "");
                if (!srecenturl.equals("")) {
                    try {
                        URL url = new URL(srecenturl);
                        recent = Bookmark.parseBookmarks(AutoplotUtil.readDoc(url.openStream()).getDocumentElement());
                        prefs.put(PREF_RECENT, Bookmark.formatBooks(recent));
                        try {
                            prefs.flush();
                        } catch (BackingStoreException ex) {
                            logger.log(Level.SEVERE,ex.getMessage(),ex);
                        }
                    } catch (BookmarksException e) {
                        logger.log(Level.SEVERE,e.getMessage(),e);
                        return new ArrayList<Bookmark>();
                    } catch (MalformedURLException e) {
                        logger.log(Level.SEVERE,e.getMessage(),e);
                        return new ArrayList<Bookmark>();
                    } catch (IOException e) {
                        logger.log(Level.SEVERE,e.getMessage(),e);
                        return new ArrayList<Bookmark>();
                    } catch (SAXException e) {
                        logger.log(Level.SEVERE,e.getMessage(),e);
                        return new ArrayList<Bookmark>();
                    } catch (ParserConfigurationException e) {
                        logger.log(Level.SEVERE,e.getMessage(),e);                        
                        return new ArrayList<Bookmark>();
                    }
                } else {
                    return new ArrayList<Bookmark>();
                }
            } else {
                try {
                    recent = Bookmark.parseBookmarks(AutoplotUtil.readDoc(new ByteArrayInputStream(srecent.getBytes())).getDocumentElement());
                } catch (BookmarksException e) {
                    logger.log(Level.SEVERE,e.getMessage(),e);
                    return new ArrayList<Bookmark>();

                } catch (SAXException e) {
                    logger.log(Level.SEVERE,e.getMessage(),e);
                    return new ArrayList<Bookmark>();

                } catch (IOException e) {
                    logger.log(Level.SEVERE,e.getMessage(),e);
                    return new ArrayList<Bookmark>();

                } catch (ParserConfigurationException e) {
                    logger.log(Level.SEVERE,e.getMessage(),e);
                    return new ArrayList<Bookmark>();

                }
            }
            addRecent(""); // cause the new format to be written.
        }

        return recent;
    }

    /**
     * read the default bookmarks in, or those from the user's "bookmarks" pref node.  
     * @return the bookmarks of the legacy user.
     */
    public List<Bookmark> getLegacyBookmarks() {
        Preferences prefs = Preferences.userNodeForPackage(ApplicationModel.class);
        String sbookmark = prefs.get("bookmarks", "");

        if (sbookmark.equals("") || !sbookmark.startsWith("<")) {
            String surl = AutoplotUtil.getProperty("autoplot.default.bookmarks", "http://autoplot.org/data/demos.xml");
            if (!surl.equals("")) {
                try {
                    URL url = new URL(surl);
                    bookmarks = Bookmark.parseBookmarks(AutoplotUtil.readDoc(url.openStream()).getDocumentElement());
                } catch (Exception e) {
                    logger.log(Level.SEVERE,e.getMessage(),e);
                    return new ArrayList<Bookmark>();
                }
            }
        } else {
            try {
                bookmarks = Bookmark.parseBookmarks(AutoplotUtil.readDoc(new ByteArrayInputStream(sbookmark.getBytes())).getDocumentElement());
            } catch (SAXException e) {
                logger.log(Level.SEVERE,e.getMessage(),e);
                return new ArrayList<Bookmark>();
            } catch (Exception e) {
                logger.log(Level.SEVERE,e.getMessage(),e);
                return new ArrayList<Bookmark>();
            }
        }
        return bookmarks;
    }

    /**
     * record exceptions the same way we would record successful plots.
     * Right now this only records exceptions for user=jbf...
     * 
     * @param surl the URI we were trying to plot.
     * @param exx the exception we got instead.
     */
    public void addException( String surl, Exception exx ) {
        if ( !DasApplication.hasAllPermission() ) {
            return;
        }

        if ( !( "true".equals( System.getProperty(Version.PROP_ENABLE_LOG_EXCEPTIONS) ) ) ) {
            return;
        }
        
        logger.fine("logging exception because of experimental.features");

        File f2= new File( AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA), "bookmarks/" );
        if ( !f2.exists() ) {
            boolean ok= f2.mkdirs();
            if ( !ok ) {
                throw new RuntimeException("unable to create folder "+ f2 );
            }
        }

        final File f3 = new File( f2, "exceptions.txt" );
        FileWriter out3=null;
        try {
            out3 = new FileWriter( f3, true );
            TimeParser tp= TimeParser.create( TimeParser.TIMEFORMAT_Z );
            Datum now= Units.t1970.createDatum( System.currentTimeMillis()/1000. );
            out3.append( "=== " + tp.format( now, null) + " ===\n" );
            out3.append( surl + "\n" );
            StringWriter sw= new StringWriter();
            PrintWriter pw= new PrintWriter( sw );
            exx.printStackTrace(pw);
            out3.append( sw.toString() );
            out3.append("\n");
            out3.close();

        } catch ( IOException ex ) {
            logger.log( Level.SEVERE, "exception: "+surl, ex );
            if ( out3!=null ) try {
                out3.close();
            } catch (IOException ex1) {
                logger.log(Level.SEVERE, "exception: "+ex1.getMessage(), ex1);
            }
        }

    }

    /**
     * suppress repeats of the same URI.  https://sourceforge.net/p/autoplot/bugs/1184/
     */
    String lastRecent= ""; 
    long lastRecentTime= 0;
    long lastRecentCount= 1; // number of times we used this uri.
    
    /**
     * add the URI to the recently used list, and to the user's
     * autoplot_data/bookmarks/history.txt.  No interpretation is done
     * as of June 2011, and pngwalk: and script: uris are acceptable.
     * @param surl
     */
    public void addRecent(String surl) {

        if ( !DasApplication.hasAllPermission() ) {
            return;
        }

        if ( surl.contains("nohistory=true") ) {
            logger.fine("Not logging URI because it contains nohistory=true");
            return;
        } 

        if ( dontRecordHistory ) {
            logger.finest("Not logging URI because history is turned off");
            return;
        }
        
        if ( recent==null ) recent= new ArrayList<Bookmark>(); // kludge for rpwg TODO: why is this null?
        List oldValue = Collections.unmodifiableList(recent);
        ArrayList<Bookmark> newValue = new ArrayList<Bookmark>(recent);

        // check for new timerange in TSB.
        if ( surl.startsWith("vap+") ) {
            String lookfor= DataSetURI.blurTsbUri(surl);
            if ( lookfor!=null ) {
                List<Bookmark> rm= new ArrayList<Bookmark>();
                for ( Bookmark b: newValue ) {
                    if ( b instanceof Bookmark.Item ) {
                        String suri1= ((Bookmark.Item)b).getUri();
                        String suri11= DataSetURI.blurTsbUri(suri1);
                        if ( lookfor.equals(suri11) ) {
                            rm.add(b);
                        }
                    }
                }
                if ( rm.size()>0 ) {
                    logger.log(Level.FINE, "removing {0} other TSB uris", rm.size());
                    for ( Bookmark o: rm ) {
                        newValue.remove(o);
                    }
                }
            }
        }

        if ( !surl.equals("") ) {
            Bookmark book = new Bookmark.Item(surl);
            if (newValue.contains(book)) { // move it to the front of the list
                newValue.remove(book);
            }

            newValue.add(book);
        }
        
        while (newValue.size() > MAX_RECENT) {
            newValue.remove(0);
        }


        if ( surl.equals(lastRecent) ) { // suppress repeats until the surl changes.
            lastRecentTime= System.currentTimeMillis();
            lastRecentCount++;
            
        } else {        
            String nodeName= "recent";

            File f2= new File( AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA), "bookmarks/" );
            if ( !f2.exists() ) {
                boolean ok= f2.mkdirs();
                if ( !ok ) {
                    throw new RuntimeException("unable to create folder "+ f2 );
                }
            }

            final File f = new File( f2, nodeName + ".xml");

            OutputStream out= null;
            try {
                out= new FileOutputStream(f);
                Bookmark.formatBooks(out,newValue);
            } catch ( IOException ex ) {
                logger.log(Level.SEVERE,ex.getMessage(),ex);
            } finally {
                try {
                    if ( out!=null ) out.close();
                } catch ( IOException ex ) {
                    logger.log(Level.SEVERE,ex.getMessage(),ex);
                }
            }
        
            // always tack on the URI to history.dat file
            final File f3 = new File( f2, "history.txt");
            FileWriter out3=null;
            try {
                out3 = new FileWriter( f3, true );                
                TimeParser tp= TimeParser.create( TimeParser.TIMEFORMAT_Z );
                long lnow= System.currentTimeMillis();
                if ( lastRecent!=null && lastRecentCount>1 ) {
                    Datum then= Units.t1970.createDatum(lastRecentTime/1000.);
                    out3.append( tp.format( then ) + "\t" + lastRecent + "\n" ); 
                } 
                lastRecent= surl;
                lastRecentTime= lnow;
                lastRecentCount= 1;
                Datum now= Units.t1970.createDatum( System.currentTimeMillis()/1000. );
                out3.append( tp.format( now, null) + "\t" + surl + "\n" );
                out3.close();
            } catch ( IOException ex ) {
                logger.log(Level.SEVERE,ex.getMessage(),ex);
                if ( out3!=null ) try {
                    out3.close();
                } catch (IOException ex1) {
                    logger.log(Level.SEVERE, ex1.getMessage(), ex1);
                }
            }
        }

        this.recent = newValue;
        propertyChangeSupport.firePropertyChange(PROPERTY_RECENT, oldValue, recent);
    }

    /** 
     * return a list of items matching filter.
     * @param filter String like "*.jy"
     * @param limit maximum number of items to return
     * @return 
     */
    public Map<String,String> getRecent( String filter, final int limit ) {
        File f2= new File( AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA), "bookmarks/" );
        if ( !f2.exists() ) {
            boolean ok= f2.mkdirs();
            if ( !ok ) {
                throw new RuntimeException("unable to create folder "+ f2 );
            }
        }
        
        // always tack on the URI to history.dat file
        final File f3 = new File( f2, "history.txt");
        
        Pattern p= org.das2.util.filesystem.Glob.getPattern(filter);
        
        LinkedHashMap<String,String> result= new LinkedHashMap<String, String>() {
            @Override
            protected boolean removeEldestEntry(Entry<String, String> eldest) {
               return size() > limit;
            }
        };
        
        BufferedReader reader=null;
        try {
            reader= new BufferedReader( new FileReader(f3) );
            String line= reader.readLine();
            while ( line!=null ) {            
                String[] ss= line.split("\\s+",2);
                if ( ss.length>1 ) {
                    if ( p.matcher(ss[1]).matches() ) {
                        result.put( ss[1], ss[0] );
                    }
                }
                line= reader.readLine();
            }
        } catch ( IOException ex ) {
            logger.log(Level.SEVERE,ex.getMessage(),ex);
            
        } finally {
            if ( reader!=null ) try {
                reader.close();
            } catch (IOException ex1) {
                logger.log(Level.SEVERE, ex1.getMessage(), ex1);
            }
        }

        return result;
        
    }
    
    /**
     * //TODO: this should not be called.
     * @deprecated.  Use BookmarksManager.addBookmark.
     */
    public Bookmark addBookmark(final String surl) {

        Bookmark.Item item = new Bookmark.Item(surl);
        URISplit split = URISplit.parse(surl);
        String autoTitle = split.file==null ? surl : split.file.substring(split.path.length());
        if (autoTitle.length() == 0) autoTitle = surl;
        item.setTitle(autoTitle);

        List<Bookmark> oldValue = Collections.unmodifiableList(new ArrayList<Bookmark>());
        if ( bookmarks==null ) bookmarks= new ArrayList<Bookmark>();
        List<Bookmark> newValue = new ArrayList<Bookmark>(bookmarks);

        if (newValue.contains(item)) { // move it to the front of the list
            Bookmark.Item old = (Bookmark.Item) newValue.get(newValue.indexOf(item));
            item = old;  // preserve titles and other future metadata.
            newValue.remove(old);
        }

        newValue.add(item);

        ApplicationModel.this.bookmarks = newValue;
        propertyChangeSupport.firePropertyChange(PROPERTY_BOOKMARKS, oldValue, bookmarks);

        return item;
    }

    public void exit() {
    }

    void resetZoom() {
        dom.getController().getPlot().getController().resetZoom(true, true, true);
    }

    private int stepForSize( int size ) {
        int step;
        if ( size<20 ) {
            step=1;
        } else if ( size<40 ) {
            step=2;
        } else {
            step=4;
        }
        return step;
    }
    void increaseFontSize() {
        Font f = Font.decode( this.dom.getOptions().getCanvasFont() );
        int size= f.getSize();
        int step= stepForSize(size);
        f = f.deriveFont((float)size+step);
        this.dom.getOptions().setCanvasFont(DomUtil.encodeFont(f));

    }

    void decreaseFontSize() {
        Font f = Font.decode( this.dom.getOptions().getCanvasFont() );
        int size= f.getSize();
        int step= stepForSize(size);
        f = f.deriveFont((float)size-step);
        this.dom.getOptions().setCanvasFont(DomUtil.encodeFont(f));
    }
    
    void resetFontSize() {
        Font f = Font.decode( this.dom.getOptions().getCanvasFont() );
        Font defaultFont = UIManager.getDefaults().getFont("TextPane.font");
        int size= defaultFont.getSize();
        f = f.deriveFont((float)size);
        this.dom.getOptions().setCanvasFont(DomUtil.encodeFont(f));
    }

    /**
     * creates an ApplicationState object representing the current state.
     * @param deep if true, do a deeper, more expensive gathering of state.  In the initial implementation, this calculates the embededded dataset.
     * @return ApplicationState object
     */
    public Application createState(boolean deep) {

        Application state = (Application) dom.copy();

        return state;
    }


    /**
     * resizes the image to fit within w,h in several iterations
     * @param im
     * @param w
     * @param h
     * @return
     */
    public static BufferedImage resizeImageTo( BufferedImage im, int hf ) {
        int h0= im.getHeight();
        double aspect= 1. * h0 / im.getWidth();
        int h;

        BufferedImage thumb=null;

        h= hf * (int) Math.pow( 2, ( (int) Math.ceil( Math.log10( 1. * h0 / hf ) / Math.log10(2) ) ) ); // first scale up.

        if ( h==h0 ) {
            h= h0/2;
        }

        while ( h>=hf ) {
            thumb= new BufferedImage( (int)(h/aspect), h, BufferedImage.TYPE_INT_ARGB );
            Graphics2D g= ((Graphics2D)thumb.getGraphics());
            g.setRenderingHint( RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );

            AffineTransform tx = new AffineTransform();
            double scale= 1.0 * h / h0;
            tx.scale(scale, scale);
            g.drawImage( im, tx, null );

            if ( h==hf ) break;

            h0= h;
            h= ( hf < h0/2 ) ? h0/2 : hf;

            im= thumb;
        }

        return thumb;

    }

    /**
     * quick and dirty method for widening lines.
     * @param im
     */
    public void thickenLines( BufferedImage im ) {
        // thicken lines
        int bc= im.getRGB(0,0);
        for ( int i=0; i<im.getWidth()-4; i++ ) {
            for ( int j=0; j<im.getHeight()-4; j++ ) {
                int c0= im.getRGB( i,j );
                if ( c0==bc ) {
                    int c= im.getRGB(i+1,j);
                    if ( c!=bc ) {
                        im.setRGB( i,j,c );
                    }
                    c= im.getRGB(i+2,j);
                    if ( c!=bc ) {
                        im.setRGB( i,j,c );
                    }
                    c= im.getRGB(i+3,j);
                    if ( c!=bc ) {
                        im.setRGB( i,j,c );
                    }
                    c= im.getRGB(i+4,j);
                    if ( c!=bc ) {
                        im.setRGB( i,j,c );
                    }
                    c= im.getRGB(i,j+1);
                    if ( c!=bc ) {
                        im.setRGB( i,j,c );
                    }
                    c= im.getRGB(i,j+2);
                    if ( c!=bc ) {
                        im.setRGB( i,j,c );
                    }
                    c= im.getRGB(i,j+3);
                    if ( c!=bc ) {
                        im.setRGB( i,j,c );
                    }
                    c= im.getRGB(i,j+4);
                    if ( c!=bc ) {
                        im.setRGB( i,j,c );
                    }
                }
            }
        }

    }

    /**
     * return a thumbnail for the state.  TODO: multiple steps produces better result.  See http://www.philreeve.com/java_high_quality_thumbnails.php
     * @return
     */
    public BufferedImage getThumbnail( int height ) {

        if ( getCanvas().getWidth()==0 ) {
            return null;
        }

        if ( SwingUtilities.isEventDispatchThread() ) {
            throw new IllegalStateException( "must not be called on the EventQueue");
        }

        AWTEvent ev= Toolkit.getDefaultToolkit().getSystemEventQueue().peekEvent(DasUpdateEvent.DAS_UPDATE_EVENT_ID);
        if ( ev!=null ) {
            logger.fine("bug 3574147: not getting thumbnail, because it would cause hang.");
            return null; // bug 3574147
        }

        BufferedImage im= (BufferedImage) getCanvas().getImageNonPrint( getCanvas().getWidth(), getCanvas().getHeight() );
        if ( im==null ) return null;

        if ( im.getHeight() / height > 3 ) {
            thickenLines(im);
        }

        BufferedImage thumb= resizeImageTo( im, height );
        return thumb;
    }

    /**
     * set the application state.
     * @param state
     */
    public void restoreState(Application state) {
        boolean resetFocus= DomUtil.structureChanges( state, this.dom );
        this.dom.syncTo(state);
        if ( resetFocus ) {
            this.dom.getController().setPlot( this.dom.getPlots(0) );
        }
    }

    void doSave(File f) throws IOException {
        StatePersistence.saveState(f, createState(true), "");
        setUseEmbeddedDataSet(false);

        setVapFile( DataSetURI.fromFile(f) );
        addRecent( DataSetURI.fromFile(f) );
    }

    void doSave(File f, String scheme ) throws IOException {
        StatePersistence.saveState(f, createState(true), scheme);
        setUseEmbeddedDataSet(false);

        setVapFile( DataSetURI.fromFile(f) );
        addRecent( DataSetURI.fromFile(f) );
    }

    /**
     * fix the state to make it valid, to the extent that this is possible.
     * For example, old vap files didn't specify rows, so we add rows to make
     * it.  Note the mechanism used to save old states doesn't allow for importing,
     * since it's tied to classes in the running JRE.  It would be non-trivial
     * to implement this.  So we do this for now.
     * 
     * @param state
     */
    private void makeValid( Application state ) {
        if ( state.getController()!=null ) throw new IllegalArgumentException("state must not have controller");
        // check to see if rows need to be made

        Canvas c= state.getCanvases(0);
        if ( c.getMarginRow().getId().equals("") ) c.getMarginRow().setId("marginRow_0");
        if ( c.getMarginColumn().getId().equals("") ) c.getMarginColumn().setId("marginColumn_0");

        if ( state.getPlots(0).getRowId().equals("") ) {
            int n= state.getPlots().length;
            Row[] rows= new Row[n];
            for ( int i=0; i<n; i++ ) {
                Row r= new Row();
                r.setBottom( ""+((i+1)*10000/100./n)+"%-2.0em" );
                r.setTop( ""+((i)*10000/100./n)+"%+2.0em" );
                r.setParent( c.getMarginRow().getId() );
                r.setId("row_"+i);
                state.getPlots(i).setRowId(r.getId());
                state.getPlots(i).setColumnId(c.getMarginColumn().getId());
                rows[i]= r;
            }
            c.setRows(rows);
        }

        for ( BindingModel m: state.getBindings() ) {
            Object src= DomUtil.getElementById( state, m.getSrcId() );
            if ( src==null ) {
                System.err.println("invalid binding:" + m + ", unable to find source node: "+ m.getSrcId() );
                continue;
            }
            Object dst= DomUtil.getElementById( state, m.getDstId() );
            if ( dst==null ) {
                System.err.println("invalid binding:" + m + ", unable to find destination node: "+ m.getDstId() );
                continue;
            }
            BeanProperty srcProp= BeanProperty.create(m.getSrcProperty());
            BeanProperty dstProp= BeanProperty.create(m.getDstProperty());
            Object srcVal= srcProp.getValue(src);
            Object dstVal= dstProp.getValue(dst);
            if ( srcVal==null && dstVal==null ) {
                continue; // not sure what to make of this state, shouldn't happen.
            }
            if ( srcVal==null || dstVal==null ) {
                continue; // findbugs NP_NULL_ON_SOME_PATH
            }
            if ( !srcVal.equals(dstVal) ) {
                if ( dst instanceof Axis && m.getDstProperty().equals("range") && ((Axis)dst).isAutoRange() ) {
                    logger.log( Level.FINE, "fixing inconsistent vap where bound values were not equal: {0}.{1}!={2}.{3}", 
                            new Object[]{m.getSrcId(), m.getSrcProperty(), m.getDstId(), m.getDstProperty()});
                } else {
                    logger.log( Level.WARNING, "fixing inconsistent vap where bound values were not equal: {0}.{1}!={2}.{3}", 
                            new Object[]{m.getSrcId(), m.getSrcProperty(), m.getDstId(), m.getDstProperty()});
                }
                BeanProperty.create(m.getDstProperty()).setValue(dst,srcVal);
            }
        }
    }

    /**
     * we need to way to implement bindings, since we may mutate the state
     * before syncing to it.  This makes the state more valid and avoids
     * bugs like 
     * https://sourceforge.net/tracker/?func=detail&aid=3017554&group_id=199733&atid=970682
     * @param state
     */
    private void doBindings( Application state ) {
        for ( BindingModel m: state.getBindings() ) {
            Object src= DomUtil.getElementById( state, m.getSrcId() );
            Object dst= DomUtil.getElementById( state, m.getDstId() );
            Binding binding = Bindings.createAutoBinding(
                    UpdateStrategy.READ_WRITE,
                    src,
                    BeanProperty.create(m.getSrcProperty()),
                    dst,
                    BeanProperty.create(m.getDstProperty() ) );
            binding.bind();
        }
    }

    /**
     * Load the vap file at f, apply additional modifications to the DOM, then
     * sync the application to this.  Deltas with names in all caps (e.g. PWD or FILE) 
     * will be applied to the vap, looking for %{PWD} or %{FILE}:
     * <li>PWD will be set to the current working directory of the vap file.
     * @param f a .vap file.
     * @param deltas list property name, property value pairs to apply to the
     *   vap DOM after it's loaded.  
     * @throws java.io.IOException
     */
    public void doOpenVap( File f, LinkedHashMap<String, String> deltas) throws IOException {
        if ( !f.exists() ) throw new IllegalArgumentException("no such file: "+f);
        if ( f.length()==0 ) throw new IllegalArgumentException("zero-length file: "+f);

        InputStream in=null;
        try {
            in= new FileInputStream(f);

            doOpenVap( in,deltas );

            setVapFile( f.toString() );
            
        } finally {
            if ( in!=null ) in.close();
        }

    }

    /**
     * listen for requests to change the size.  w,h is the new size.
     */
    public static interface ResizeRequestListener {
        double resize( int w, int h );
    }

    ResizeRequestListener resizeRequestListener=null;

    /**
     * set the code which will handle resize requests, for example if
     * a .vap is loaded.
     * @param listener the listener.
     */
    public void setResizeRequestListener( ResizeRequestListener listener ) {
        this.resizeRequestListener= listener;
    }

    /**
     * set the canvas size, to the extent that this is possible.
     * @param width
     * @param height 
     */
    public void setCanvasSize( int width, int height ) {
        if ( this.resizeRequestListener!=null ) {
            resizeRequestListener.resize(width, height );
        } else {
            if ( !DasApplication.getDefaultApplication().isHeadless() ) {
                Window w=SwingUtilities.getWindowAncestor( this.canvas );
                // assume it is fitted for now.  This is a gross over simplification, not considering scroll panes, etc.
                if ( w!=null ) {
                    Dimension windowDimension= w.getSize();
                    Dimension canvasDimension= this.canvas.getSize();
                    w.setSize( width + ( windowDimension.width - canvasDimension.width ), height +  ( windowDimension.height - canvasDimension.height ) ); 
                }
            }
        
            canvas.setSize(new Dimension(width, height));
        }
    }
    
    
    /**
     * set the location of the application container.  This will generally
     * have some small offset from the canvas.
     * @param x the upper-left corner location.
     * @param y the upper-left corner location.
     */
    public void setLocation( int x, int y ) {
        Window w=SwingUtilities.getWindowAncestor( this.canvas );
        w.setLocation( x, y );
    }
    
    /**
     * open the serialized DOM, apply additional modifications to the DOM, then
     * sync the application to this.  Deltas with names in all caps (e.g. PWD or FILE) 
     * will be applied to the vap, looking for %{PWD} or %{FILE}:
     * <li>PWD will be set to the current working directory of the vap file.
     * @param in an InputStream containing a vap file xml representation of the dom.  This is not closed.
     * @param deltas list property name, property value pairs to apply to the
     *   vap DOM after it's loaded.  
     * @throws java.io.IOException
     */
    public void doOpenVap( InputStream in, LinkedHashMap<String, String> deltas) throws IOException {

        Application state = (Application) StatePersistence.restoreState(in);
        makeValid( state );

        if (deltas != null) {
            doBindings( state );

            for (Entry<String, String> e : deltas.entrySet()) {
                logger.log(Level.FINEST, "applying to vap {0}={1}", new Object[]{e.getKey(), e.getValue()});
                String node = e.getKey();
                String sval = e.getValue();

//                BeanProperty prop = BeanProperty.create(node);
//                if (!prop.isWriteable(state)) {
//                    logger.warning("the node " + node + " of " + state + " is not writable");
//                    continue;
//                }
//                Class c = prop.getWriteType(state);
                if ( Character.isUpperCase( node.charAt(0) ) ) {
                    DomUtil.applyMacro( state, "%{"+node+"}", sval );
                    
                } else {
                    Class c;
                    try {
                        c = DomUtil.getPropertyType(state, node);
                    } catch (IllegalAccessException ex) {
                        logger.log(Level.SEVERE, ex.getMessage(), ex);
                        continue;
                    } catch (IllegalArgumentException ex) {
                        logger.log(Level.SEVERE, ex.getMessage(), ex);
                        continue;
                    } catch (InvocationTargetException ex) {
                        logger.log(Level.SEVERE, ex.getMessage(), ex);
                        continue;
                    }
                    SerializeDelegate sd = SerializeRegistry.getDelegate(c);
                    if (sd == null) {
                        System.err.println("unable to find serialize delegate for " + c.getCanonicalName());
                        continue;
                    }
                    Object val;
                    try {
                        // pop off any single-quotes used to delimit strings in URLs.
                        if ( c==String.class && sval.length()>1 && sval.startsWith("'") && sval.endsWith("'") ) {
                            sval= sval.substring(1,sval.length()-1);
                        }
                        val = sd.parse(sd.typeId(c), sval);
    //                    prop.setValue(state, val);
                        DomUtil.setPropertyValue(state, node, val);
                    } catch (IllegalAccessException ex) {
                        logger.log(Level.SEVERE, ex.getMessage(), ex);
                    } catch (IllegalArgumentException ex) {
                        logger.log(Level.SEVERE, ex.getMessage(), ex);
                    } catch (InvocationTargetException ex) {
                        logger.log(Level.SEVERE, ex.getMessage(), ex);
                    } catch (ParseException ex) {
                        IOException ioex= new IOException( ex.getMessage() );
                        throw ioex;
                        //logger.log(Level.SEVERE, ex.getMessage(), ex);
                    }
                }
            }
        }

        // for now, we reset when loading to make things more robust.  This
        // should be removed eventually and we can go back to only applying 
        // deltas.
        if ( DomUtil.structureChanges( this.dom, state ) ) {
            this.dom.getController().reset();
        }

        if ( this.resizeRequestListener!=null ) {
            double scale= resizeRequestListener.resize( state.getCanvases(0).getWidth(), state.getCanvases(0).getHeight() );
            Font f= Font.decode( state.getCanvases(0).getFont() );
            Font newFont= f.deriveFont( f.getSize2D() * (float)scale );
            logger.log(Level.FINE, "shrinking font to {0}", newFont.toString());
            // GuiSupport.setFont( this, newFont );  // this is in-lined to support AutoplotApplet.
            getCanvas().setBaseFont(newFont);
            Font f2 = getCanvas().getFont();
            getDocumentModel().getOptions().setCanvasFont( DomUtil.encodeFont(f2) );
            state.getCanvases(0).setFont( DomUtil.encodeFont(newFont) );
            state.getCanvases(0).setFitted(dom.getCanvases(0).isFitted());
            state.getCanvases(0).setWidth( dom.getCanvases(0).getWidth());
            state.getCanvases(0).setHeight( dom.getCanvases(0).getHeight());
        }
        
        //logger.fine("" + state.diffs(this.dom));
        restoreState(state);
        setUseEmbeddedDataSet(false);

    }

    void doOpen(File f) throws IOException {
        doOpenVap(f,null);
    }

    protected String vapFile = null;
    public static final String PROP_VAPFILE = "vapFile";

    public String getVapFile() {
        return vapFile;
    }

    public void setVapFile(String vapFile) {
        String old= this.vapFile;
        this.vapFile = vapFile;
        if ( vapFile==null ) { // always fire off an event.
            propertyChangeSupport.firePropertyChange(PROP_VAPFILE, old, null);
        } else {
            propertyChangeSupport.firePropertyChange(PROP_VAPFILE, null, vapFile);
        }
    }

    /**
     * trigger autolayout, which adjusts the margins so that labels aren't cut off.  Note
     * LayoutListener has similar code.
     */
    public void doAutoLayout() {
        ApplicationModel model= this;
        CanvasController controller= model.dom.getController().getCanvas().getController();
        ApplicationController applicationController= this.getDocumentModel().getController();
        controller.registerPendingChange(this,LayoutListener.PENDING_CHANGE_AUTOLAYOUT);
        controller.performingChange(this,LayoutListener.PENDING_CHANGE_AUTOLAYOUT);
        LayoutUtil.autolayout( applicationController.getDasCanvas(), applicationController.getRow(), applicationController.getColumn() );
        controller.changePerformed(this,LayoutListener.PENDING_CHANGE_AUTOLAYOUT);
    }

    /**
     * when true, we are in the process of restoring a state.  Changes should not
     * be pushed to the undo stack.
     */
    private boolean restoringState = false;

    public boolean isRestoringState() {
        return restoringState;
    }

    public void setRestoringState(boolean b) {
        this.restoringState = b;
    }
    String embedDs = "";
    boolean embedDsDirty = false;

    public String getEmbeddedDataSet() {
        if (isUseEmbeddedDataSet() && embedDsDirty) {
            packEmbeddedDataSet();
        }

        return embedDs;
    }

    private void packEmbeddedDataSet() {

        try {
            if (dom.getController().getDataSourceFilter().getController().getDataSet() == null) {
                embedDs = "";
                return;
            }

            org.das2.dataset.DataSet ds;

            ByteArrayOutputStream out = new ByteArrayOutputStream(10000);
            //DeflaterOutputStream dos= new DeflaterOutputStream(out);
            OutputStream dos = out;

            SimpleStreamFormatter format = new SimpleStreamFormatter();
            format.format(dom.getController().getDataSourceFilter().getController().getDataSet(), dos, false);

            dos.close();

            byte[] data = Base64.encodeBytes(out.toByteArray()).getBytes();

            embedDs = new String(data);
            embedDsDirty = false;
        } catch (StreamException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    public void setEmbeddedDataSet(String dataset) {
        this.embedDs = dataset;

        if (useEmbeddedDataSet && !embedDsDirty) {
            unpackEmbeddedDataSet();
        }

    }

    private void unpackEmbeddedDataSet() {
        if (embedDs == null || embedDs.equals("")) {
            return;
        }

        byte[] data = Base64.decode(embedDs);
        InputStream in = new ByteArrayInputStream(data);
        //InflaterChannel ich= new InflaterChannel( Channels.newChannel(in) );
        ReadableByteChannel ich = Channels.newChannel(in);

        QDataSetStreamHandler handler = new QDataSetStreamHandler();
        try {
            org.virbo.qstream.StreamTool.readStream(ich, handler);
            getDataSourceFilterController().setDataSetInternal(handler.getDataSet());

        } catch (org.virbo.qstream.StreamException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }

    }
    boolean useEmbeddedDataSet = false;

    public boolean isUseEmbeddedDataSet() {
        return useEmbeddedDataSet;
    }

    public void setUseEmbeddedDataSet(boolean use) {
        this.useEmbeddedDataSet = use;
        if (use && !embedDsDirty) { // don't overwrite the dataset we loaded since then

            unpackEmbeddedDataSet();
        }

    }

    /**
     * remove all cached downloads.
     * Currently, this is implemented by deleting the das2 fsCache area.
     * @throws IllegalArgumentException if the delete operation fails
     */
    boolean clearCache() throws IllegalArgumentException {
        File local;

        local = new File( AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_FSCACHE) );
        boolean okay= true;
        Set<String> exclude= new HashSet();
        exclude.add("ro_cache.txt");
        exclude.add("keychain.txt");
        okay= okay && FileUtil.deleteFileTree( new File(local,"http"), exclude );
        okay= okay && FileUtil.deleteFileTree( new File(local,"https"), exclude );
        okay= okay && FileUtil.deleteFileTree( new File(local,"ftp"), exclude );
        okay= okay && FileUtil.deleteFileTree( new File(local,"zip"), exclude );
        okay= okay && FileUtil.deleteFileTree( new File(local,"vfsCache"), exclude );
        okay= okay && FileUtil.deleteFileTree( new File(local,"fscache"), exclude ); // future
        return okay;
        //return Util.deleteFileTree(local);
    }

    /**
     * move the cache.
     * @param n new location folder.
     * @return true if successful.
     */
    boolean moveCache( File n ) {
        File local = new File( AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_FSCACHE) );

        ProgressMonitor mon1= DasProgressPanel.createFramed( SwingUtilities.getWindowAncestor(getCanvas()), "Moving Cache..." );
        mon1.started();
        
        boolean y= Util.copyFileTree( local, n );
        mon1.finished();
        if ( y ) {
            //y= Util.deleteFileTree(local);
            JOptionPane.showMessageDialog( this.getCanvas(), "<html>File cache moved to<br>"+n+".<br>The old cache ("+local+") still contains data<br>and should manually be deleted.</html>", "Files moved", JOptionPane.PLAIN_MESSAGE );
            AutoplotSettings.settings().setFscache(n.toString());
        } else {
            JOptionPane.showMessageDialog( this.getCanvas(), "<html>Some problem occured, so the cache remains at the old location.</html>", "move files failed", JOptionPane.WARNING_MESSAGE );
        }
        return y;
    }

    /**
     * Utility field used by bound properties.
     */
    private final java.beans.PropertyChangeSupport propertyChangeSupport = new java.beans.PropertyChangeSupport(this);

    /**
     * wait for Autoplot to settle, waiting for pending changes in the application controller and canvas.
     * @param runtimeException ignored.
     * @deprecated use waitUntilIdle() instead.
     */
    public void waitUntilIdle(boolean runtimeException) {
        waitUntilIdle(); 
    }

    /**
     * wait for Autoplot to settle, waiting for pending changes in the application controller and canvas.
     */    
    public void waitUntilIdle() {
        logger.log(Level.FINE, "enter waitUntilIdle, pendingChanges={0}", dom.getController().isPendingChanges());
        while ( dom.getController().isPendingChanges() ) {
            dom.getController().waitUntilIdle();
            logger.fine("waiting for canvas");
            canvas.waitUntilIdle();
        }
        canvas.waitUntilIdle();
        logger.fine("done waiting");
    }
    
    public Application getDocumentModel() {
        return dom;
    }

    /**
     * see ScriptPanelSupport
     * @return
     */
    public DataSourceController getDataSourceFilterController() {
        return dom.getController().getDataSourceFilter().getController();
    }

}

