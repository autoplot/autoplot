
package org.autoplot;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Dimension;
import org.autoplot.datasource.AutoplotSettings;
import java.lang.reflect.InvocationTargetException;
import org.autoplot.bookmarks.Bookmark;
import java.util.logging.Level;
import org.das2.DasApplication;
import org.das2.graph.DasCanvas;
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
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
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
import org.das2.util.FileUtil;
import org.das2.util.filesystem.FileSystem;
import org.autoplot.dom.Application;
import org.autoplot.dom.ApplicationController;
import org.autoplot.dom.CanvasUtil;
import org.autoplot.dom.DataSourceController;
import org.autoplot.dom.DataSourceFilter;
import org.autoplot.dom.DomUtil;
import org.autoplot.dom.PlotElement;
import org.autoplot.dom.Plot;
import org.autoplot.layout.LayoutUtil;
import org.autoplot.state.StatePersistence;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.capability.Caching;
import org.xml.sax.SAXException;
import org.autoplot.bookmarks.BookmarksException;
import org.autoplot.dom.CanvasController;
import org.autoplot.datasource.HtmlResponseIOException;
import org.autoplot.datasource.Version;
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

    /**
     * an early version of Autoplot worked as an applet.
     * @return 
     */
    public boolean isApplet() {
        return this.applet;
    }
    
    public ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    /**
     * set the code that will handle uncaught exceptions.  This could be a
     * GUI that shows the user the problem and submits bug reports.  This could
     * also simply call exit with a non-zero return code for headless use.
     * @param eh 
     */
    public void setExceptionHandler(ExceptionHandler eh) {
        this.exceptionHandler= eh;
        DasApplication.getDefaultApplication().setExceptionHandler(exceptionHandler);
        FileSystem.setExceptionHandler(exceptionHandler);
        String cl= eh.getClass().getName();
        if ( cl.equals("org.autoplot.scriptconsole.GuiExceptionHandler") ) { // support applet, which doesn't know about Gui...
            try {
                Method m= eh.getClass().getMethod("setApplicationModel", ApplicationModel.class);
                m.invoke(eh, this);
                //((GuiExceptionHandler)eh).setApplicationModel(this);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException ex) {
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
                switch (messageType) {
                    case JOptionPane.WARNING_MESSAGE:
                        System.err.println( "WARNING: "+ title + ": " + message  );
                        break;
                    case JOptionPane.INFORMATION_MESSAGE:
                        System.err.println( "INFO: "+ title + ": " + message  );
                        break;
                    default:
                        System.err.println( title + ": " + message  );
                        break;
                }
            } else {
                JOptionPane.showMessageDialog( p, message, title, messageType );
            }
        } else {
            switch (messageType) {
                case JOptionPane.WARNING_MESSAGE:
                    System.err.println( "WARNING: "+ title + ": " + message  );
                    break;
                case JOptionPane.INFORMATION_MESSAGE:
                    System.err.println( "INFO: "+ title + ": " + message  );
                    break;
                default:
                    System.err.println( title + ": " + message  );
                    break;
            }
        }
    }

    private static final Logger logger = org.das2.util.LoggerManager.getLogger("autoplot");

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
                @Override
                public void run() {
                    addDasPeersToApp();
                }
            };
            try {
                SwingUtilities.invokeAndWait(run);
            } catch (InterruptedException | InvocationTargetException ex) {
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
    public void setDataSet(QDataSet ds) {
        dom.getController().getPlotElement().getController().setResetRanges(true);
        dom.getController().getDataSourceFilter().getController().setDataSource(null);
        dom.getController().getDataSourceFilter().setUri("vap+internal:");
        dom.getController().getDataSourceFilter().setFilters("");
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
        dsf.setFilters("");
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
     * Autoplot with the position switch, and we can to then call maybePlot so that completions can
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
     * The focus dataSourceFilterController is called and passed the URI, if
     * it is not a .vap file.
     * A dataSource object is created by DataSetURI.getDataSource, which looks
     * at registered data sources to get a factory object, then the datasource is
     * created with the factory object.
     *
     * @param suri the new data source URI.
     * @param mon progress monitor which is just used to convey messages.
     * @see org.autoplot.dom.DataSourceController#resetSuri(java.lang.String, org.das2.util.monitor.ProgressMonitor) 
     */
    protected void resetDataSetSourceURL(String suri, ProgressMonitor mon) {

        if (suri == null) {
            return;
        }  // not really supported

        URISplit split = URISplit.parse(suri);
        suri = URISplit.format(split);
        //surl = DataSetURI.maybeAddFile(surl);

        try {
            if ( split.file!=null && ( split.file.endsWith(".vap") || split.file.endsWith(".vapx" ) ) ) {
                try {
                    suri= suri.replaceAll("\\\\", "/");
                    URI uri = DataSetURI.getURIValid(suri);
                    mon.started();
                    mon.setProgressMessage("loading vap file");
                    this.getDocumentModel().getController().setFocusUri(suri);
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
                    addRecent( suri );
                } catch (HtmlResponseIOException ex ) {
                    // we know the URL here, so rethrow it.
                    URL url= ex.getURL();
                    if ( url==null ) {
                        url= new URL( DataSetURI.getURIValid(suri).getSchemeSpecificPart() );
                    }
                    HtmlResponseIOException neww= new HtmlResponseIOException(ex.getMessage(),url);
                    throw new RuntimeException(neww);
                } catch (IOException ex) {
                    mon.finished();
                    throw new RuntimeException(ex);
                }
            } else {
                dom.getController().setFocusUri(null);
                dom.getController().setFocusUri(suri);
                getDataSourceFilterController().resetSuri(suri, mon);
            }
        } catch ( RuntimeException e ) {
            throw e;
            
        } catch (URISyntaxException | MalformedURLException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Plot this URI, without checking to see if the URI is valid by 
     * checking reject of the data source.
     * When using the AutoplotUI, it is preferable to use AutoplotUI.plotUri, 
     * which will have the effect of typing in the URI and hitting the "go" 
     * arrow button.
     * @param suri
     * @throws RuntimeException when _getDataSource throws Exception
     */
    public void setDataSourceURL(String suri) {
        String oldVal = dom.getController().getDataSourceFilter().getUri();
        if (suri == null && oldVal == null) {
            return;
        }

        if (suri != null && suri.equals(oldVal)) {
            return;
        }

        resetDataSetSourceURL(suri, new NullProgressMonitor());
    }

    public String getDataSourceURL() {
        return dom.getController().getDataSourceFilter().getUri();
    }
    protected List<Bookmark> recent = null;
    protected List<Bookmark> bookmarks = null;

    /**
     * get the recent URIs, from bookmarks/bookmarks.recent.xml
     * @return the recent URIs
     */
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
            } catch (BookmarksException | SAXException | IOException | ParserConfigurationException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                return new ArrayList<>();
            }

        } else {

            Preferences prefs = AutoplotSettings.settings().getPreferences(ApplicationModel.class);
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
                    } catch (BookmarksException | SAXException | ParserConfigurationException | IOException e) {
                        logger.log(Level.SEVERE,e.getMessage(),e);
                        return new ArrayList<>();
                    }
                } else {
                    return new ArrayList<>();
                }
            } else {
                try {
                    recent = Bookmark.parseBookmarks(AutoplotUtil.readDoc(new ByteArrayInputStream(srecent.getBytes())).getDocumentElement());
                } catch (BookmarksException | SAXException | IOException | ParserConfigurationException e) {
                    logger.log(Level.SEVERE,e.getMessage(),e);
                    return new ArrayList<>();

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
        Preferences prefs = AutoplotSettings.settings().getPreferences(ApplicationModel.class);
        String sbookmark = prefs.get("bookmarks", "");

        if (sbookmark.equals("") || !sbookmark.startsWith("<")) {
            String surl = AutoplotUtil.getProperty("autoplot.default.bookmarks", "http://autoplot.org/data/demos.xml");
            if (!surl.equals("")) {
                try {
                    URL url = new URL(surl);
                    bookmarks = Bookmark.parseBookmarks(AutoplotUtil.readDoc(url.openStream()).getDocumentElement());
                } catch (IOException | SAXException | ParserConfigurationException | BookmarksException e) {
                    logger.log(Level.SEVERE,e.getMessage(),e);
                    return new ArrayList<>();
                }
            }
        } else {
            try {
                bookmarks = Bookmark.parseBookmarks(AutoplotUtil.readDoc(new ByteArrayInputStream(sbookmark.getBytes())).getDocumentElement());
            } catch (SAXException e) {
                logger.log(Level.SEVERE,e.getMessage(),e);
                return new ArrayList<>();
            } catch (IOException | ParserConfigurationException | BookmarksException e) {
                logger.log(Level.SEVERE,e.getMessage(),e);
                return new ArrayList<>();
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
        try ( FileWriter out3= new FileWriter( f3, true ) ) {
            TimeParser tp= TimeParser.create( TimeParser.TIMEFORMAT_Z );
            Datum now= Units.t1970.createDatum( System.currentTimeMillis()/1000. );
            out3.append( "=== " + tp.format( now, null) + " ===\n" );
            out3.append( surl + "\n" );
            StringWriter sw= new StringWriter();
            PrintWriter pw= new PrintWriter( sw );
            exx.printStackTrace(pw);
            out3.append( sw.toString() );
            out3.append("\n");

        } catch ( IOException ex ) {
            logger.log( Level.SEVERE, "exception: "+surl, ex );
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
     * @param suri
     */
    public void addRecent(String suri) {

        if ( !DasApplication.hasAllPermission() ) {
            return;
        }

        if ( suri.contains("nohistory=true") ) {
            logger.fine("Not logging URI because it contains nohistory=true");
            return;
        } 

        if ( dontRecordHistory ) {
            logger.finest("Not logging URI because history is turned off");
            return;
        }
        
        if ( recent==null ) recent= new ArrayList<>(); // kludge for rpwg TODO: why is this null?
        List oldValue = Collections.unmodifiableList(recent);
        ArrayList<Bookmark> newValue = new ArrayList<>(recent);

        // check for new timerange in TSB.
        if ( suri.startsWith("vap+") ) {
            String lookfor= DataSetURI.blurTsbUri(suri);
            if ( lookfor!=null ) {
                List<Bookmark> rm= new ArrayList<>();
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

        if ( !suri.equals("") ) {
            Bookmark book = new Bookmark.Item(suri);
            if (newValue.contains(book)) { // move it to the front of the list
                newValue.remove(book);
            }

            newValue.add(book);
        }
        
        while (newValue.size() > MAX_RECENT) {
            newValue.remove(0);
        }


        if ( suri.equals(lastRecent) ) { // suppress repeats until the surl changes.
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
            try ( FileWriter out3= new FileWriter( f3, true ) ) {
                TimeParser tp= TimeParser.create( TimeParser.TIMEFORMAT_Z );
                long lnow= System.currentTimeMillis();
                if ( lastRecent!=null && lastRecentCount>1 ) {
                    Datum then= Units.t1970.createDatum(lastRecentTime/1000.);
                    out3.append( tp.format( then ) + "\t" + lastRecent + "\n" ); 
                } 
                lastRecent= suri;
                lastRecentTime= lnow;
                lastRecentCount= 1;
                Datum now= Units.t1970.createDatum( System.currentTimeMillis()/1000. );
                out3.append( tp.format( now, null) + "\t" + suri + "\n" );
            } catch ( IOException ex ) {
                logger.log( Level.WARNING, ex.getMessage(), ex );
            }
        }

        this.recent = newValue;
        propertyChangeSupport.firePropertyChange(PROPERTY_RECENT, oldValue, recent);
    }

    /** 
     * return a list of items matching filter, in a LinkedHashMap.  Note
     * the map is a LinkedHashMap, which preserves the order, and the
     * last element is the more recently used.
     * @param filter String like "*.jy"
     * @param limit maximum number of items to return
     * @return LinkedHashMap, ordered by time, mapping URI to time.
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
    
    public void exit() {
    }

    void resetZoom() {
        dom.getController().getPlot().getController().resetZoom(true, true, true);
    }

    /**
     * amount to step for the given font size.
     * @param size
     * @return 
     */
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
     * resizes the image to fit within w,h in several iterations.
     * @param im the large image
     * @param hf the height in pixels
     * @return the image of the given size
     */
    private static BufferedImage resizeImageTo( BufferedImage im, int hf ) {
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
    private void thickenLines( BufferedImage im ) {
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
     * @param height the height in pixels
     * @return the thumbnail, or null if one cannot be created
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

        int w= getCanvas().getPreferredSize().width;
        int h= getCanvas().getPreferredSize().height;
        BufferedImage im= (BufferedImage) getCanvas().getImageNonPrint( w,h );

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
        setVapFile( DataSetURI.fromFile(f) );
        addRecent( DataSetURI.fromFile(f) );
    }

    void doSave(File f, String scheme ) throws IOException {
        StatePersistence.saveState(f, createState(true), scheme);
        setVapFile( DataSetURI.fromFile(f) );
        addRecent( DataSetURI.fromFile(f) );
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
     * @throws IllegalArgumentException if there is no file, or if the file is empty.
     */
    public void doOpenVap( File f, LinkedHashMap<String, String> deltas) throws IOException {
        if ( !f.exists() ) throw new IllegalArgumentException("no such file: "+f);
        if ( f.length()==0 ) throw new IllegalArgumentException("zero-length file: "+f);

        Preferences prefs= AutoplotSettings.settings().getPreferences( AutoplotSettings.class);
        prefs.put( AutoplotSettings.PREF_LAST_OPEN_VAP_FILE, f.getAbsolutePath() );
        prefs.put( AutoplotSettings.PREF_LAST_OPEN_VAP_FOLDER, f.getParent() );        
        
        try (InputStream in = new FileInputStream(f)) {

            doOpenVap( in,deltas );

            setVapFile( f.toString() );
            
        }

    }

    /**
     * listen for requests to change the size.  w,h is the new size.
     */
    public static interface ResizeRequestListener {
        double resize( int w, int h );
    }

    private ResizeRequestListener resizeRequestListener=null;

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
     * @param width width in pixels
     * @param height height in pixels
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

        Application state = StatePersistence.restoreState(in, deltas);

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
        
        for ( Plot p: dom.getPlots() ) {
            boolean resetx= p.getXaxis().isAutoRange() && ! p.getXaxis().getAutoRangeHints().trim().isEmpty();
            boolean resety= p.getYaxis().isAutoRange() && ! p.getYaxis().getAutoRangeHints().trim().isEmpty();
            boolean resetz= p.getZaxis().isAutoRange() && ! p.getZaxis().getAutoRangeHints().trim().isEmpty();
            if ( resetx || resety|| resetz ) {
                p.getController().resetZoom(resetx, resety, resetz);
            }
        }

    }

    /**
     * AutoplotUI calls this to open a file.
     * @param f the file
     * @throws IOException 
     */
    protected void doOpen(File f) throws IOException {
        doOpenVap(f,null);
    }

    protected String vapFile = null;
    public static final String PROP_VAPFILE = "vapFile";

    public String getVapFile() {
        return vapFile;
    }

    /**
     * handy property to contain the current file name.
     * @param vapFile 
     */
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
    
    // embed dataset stuff removed, since this is done with a zip file vap now.
    
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
        
        boolean y= Util.copyFileTree( local, n, 0, mon1 );
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
    
    /**
     * return the dom containing the state of this application
     * @return 
     */
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

