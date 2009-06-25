/*
 * ApplicationModel.java
 *
 * Created on April 1, 2007, 8:21 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.text.ParseException;
import org.virbo.autoplot.bookmarks.Bookmark;
import java.util.logging.Level;
import org.das2.DasApplication;
import org.das2.graph.DasCanvas;
import org.virbo.qstream.StreamException;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;
import java.awt.Font;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.Timer;
import javax.xml.parsers.ParserConfigurationException;
import org.das2.beans.BeansUtil;
import org.das2.components.propertyeditor.EnumerationEditor;
import org.das2.system.ExceptionHandler;
import org.das2.util.Base64;
import org.das2.util.filesystem.FileSystem;
import org.jdesktop.beansbinding.BeanProperty;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.autoplot.dom.Canvas;
import org.virbo.autoplot.dom.DataSourceController;
import org.virbo.autoplot.dom.DataSourceFilter;
import org.virbo.autoplot.dom.DomUtil;
import org.virbo.autoplot.dom.Panel;
import org.virbo.autoplot.dom.Row;
import org.virbo.autoplot.state.StatePersistence;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.URLSplit;
import org.virbo.datasource.capability.Caching;
import org.virbo.qstream.QDataSetStreamHandler;
import org.virbo.qstream.SerializeDelegate;
import org.virbo.qstream.SerializeRegistry;
import org.virbo.qstream.SimpleStreamFormatter;
import org.xml.sax.SAXException;

/**
 * Internal model of the application to separate model from view.
 * @author jbf
 */
public class ApplicationModel {

    DasApplication application;
    DasCanvas canvas;
    Timer tickleTimer;
    Application dom;
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
    }

    static final Logger logger = Logger.getLogger("virbo.autoplot");
    /**
     * dataset with fill data has been recalculated
     */
    public static final String PROPERTY_FILL = "fill";
    public static final String PROPERTY_FILE = "file";
    public static final String PROPERTY_RECENT = "recent";
    public static final String PROPERTY_BOOKMARKS = "bookmarks";
    private static final int MAX_RECENT = 20;

    public ApplicationModel() {

        BeansUtil.registerEditor(RenderType.class, EnumerationEditor.class);

        DataSetURL.init();

        dom = new Application();

    }

    /**
     * better if called from swing thread
     */
    public void addDasPeersToApp() {
        new ApplicationController(this, dom);

        canvas = dom.getController().addCanvas();

        this.application = canvas.getApplication();

        dom.getController().addPanel(null, null);
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
        dom.getController().getPanel().getController().setResetRanges(true);
        dom.getController().getDataSourceFilter().getController()._setDataSource(null);
        dom.getController().getDataSourceFilter().setUri("vap+internal:");
        dom.getController().getDataSourceFilter().getController().setDataSetInternal(null); // clear out properties and metadata
        dom.getController().getDataSourceFilter().getController().setDataSetInternal(ds);
    }

    /**
     * just plot this dataset using the specified dataSourceFilter index.  panels and dataSourceFilters
     * are added until the index exists.  This is introduced to support jython scripting, but may be
     * useful elsewhere.
     * @param chNum the index of the DataSourceFilter to use.
     * @param ds
     */
    public void setDataSet( int chNum, String label, QDataSet ds ) {
        while ( dom.getDataSourceFilters().length <= chNum ) {
            dom.getController().addPanel( null, null  );
        }
        DataSourceFilter dsf= dom.getDataSourceFilters(chNum);
        List<Panel> panels= dom.getController().getPanelsFor( dsf );
        for ( Panel p: panels ) {
            p.getController().setResetPanel(true);
            if ( label!=null ) {
                p.setLegendLabel(label);
                p.setDisplayLegend(true);
            }
        }
        dsf.getController()._setDataSource(null);
        dsf.setUri("vap+internal:");
        dsf.getController().setDataSetInternal(null); // clear out properties and metadata
        dsf.getController().setDataSetInternal(ds);
    }

    public void setDataSource(DataSource dataSource) {
        dom.getController().getDataSourceFilter().getController().setDataSource(false, dataSource);
    }

    public DataSource dataSource() {
        return dom.getController().getDataSourceFilter().getController()._getDataSource();
    }

    public void addPropertyChangeListener(java.beans.PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(java.beans.PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    /**
     * Create a dataSource object and set autoplot to display this datasource.
     * A dataSource object is created by DataSetURL._getDataSource, which looks
     * at registered data sources to get a factory object, then the datasource is
     * created with the factory object.
     *
     * Preconditions: Any or no datasource is set.
     * Postconditions: A dataSource object is created and autoplot is set to
     *  plot the datasource.  A thread has been started that will load the dataset.
     *  In headless mode, the dataset has been loaded sychronously.
     *
     * @param surl the new data source URL.
     * @param mon progress monitor which is just used to convey messages.
     */
    protected void resetDataSetSourceURL(String surl, ProgressMonitor mon) {

        if (surl == null) {
            return;
        }  // not really supported

        URLSplit split = URLSplit.parse(surl);
        surl = URLSplit.format(split);
        //surl = DataSetURL.maybeAddFile(surl);

        try {
            if (split.file.endsWith(".vap")) {
                try {
                    URL url = DataSetURL.getURL(surl);
                    mon.started();
                    mon.setProgressMessage("loading vap file");
                    File openable = DataSetURL.getFile(url, application.getMonitorFactory().getMonitor(canvas, "loading vap", ""));
                    if (split.params != null) {
                        LinkedHashMap<String, String> params = URLSplit.parseParams(split.params);
                        doOpen(openable, params);
                    } else {
                        doOpen(openable);
                    }
                    mon.setProgressMessage("done loading vap file");
                    mon.finished();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(canvas, "<html>Unable to open resource: <br>" + surl);
                }
            } else {
                getDataSourceFilterController().resetSuri(surl, mon);

            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
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
        Preferences prefs = Preferences.userNodeForPackage(ApplicationModel.class);
        String srecent = prefs.get("recent", "");

        if (srecent.equals("") || !srecent.startsWith("<")) {
            String srecenturl = AutoplotUtil.getProperty("autoplot.default.recent", "http://www.cottagesystems.com/virbo/apps/autoplot/recent.xml");
            if (!srecenturl.equals("")) {
                try {
                    URL url = new URL(srecenturl);
                    recent = Bookmark.parseBookmarks(AutoplotUtil.readDoc(url.openStream()).getDocumentElement());
                    prefs.put("recent", Bookmark.formatBooks(recent));
                    try {
                        prefs.flush();
                    } catch (BackingStoreException ex) {
                        ex.printStackTrace();
                    }
                } catch (MalformedURLException e) {
                    return new ArrayList<Bookmark>();
                } catch (IOException e) {
                    return new ArrayList<Bookmark>();
                } catch (SAXException e) {
                    return new ArrayList<Bookmark>();
                } catch (ParserConfigurationException e) {
                    return new ArrayList<Bookmark>();
                }
            }
        } else {
            try {
                recent = Bookmark.parseBookmarks(AutoplotUtil.readDoc(new ByteArrayInputStream(srecent.getBytes())).getDocumentElement());
            } catch (SAXException e) {
                return new ArrayList<Bookmark>();

            } catch (IOException e) {
                return new ArrayList<Bookmark>();

            } catch (ParserConfigurationException e) {
                return new ArrayList<Bookmark>();

            }
        }
        return recent;
    }

    public List<Bookmark> getBookmarks() {
        if (bookmarks != null) return bookmarks;
        Preferences prefs = Preferences.userNodeForPackage(ApplicationModel.class);
        String sbookmark = prefs.get("bookmarks", "");

        if (sbookmark.equals("") || !sbookmark.startsWith("<")) {
            String surl = AutoplotUtil.getProperty("autoplot.default.bookmarks", "http://www.autoplot.org/data/demos.xml");
            if (!surl.equals("")) {
                try {
                    URL url = new URL(surl);
                    bookmarks = Bookmark.parseBookmarks(AutoplotUtil.readDoc(url.openStream()).getDocumentElement());
                    prefs.put("bookmarks", Bookmark.formatBooks(recent));
                    try {
                        prefs.flush();
                    } catch (BackingStoreException ex) {
                        ex.printStackTrace();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return new ArrayList<Bookmark>();
                }
            }
        } else {
            try {
                bookmarks = Bookmark.parseBookmarks(AutoplotUtil.readDoc(new ByteArrayInputStream(sbookmark.getBytes())).getDocumentElement());
            } catch (SAXException e) {
                System.err.println(sbookmark);
                e.printStackTrace();
                return new ArrayList<Bookmark>();
            //throw new RuntimeException(e);
            } catch (Exception e) {
                System.err.println(sbookmark);
                e.printStackTrace();
                return new ArrayList<Bookmark>();
            }
        }
        return bookmarks;
    }

    public void setBookmarks(List<Bookmark> list) {
        List oldValue = bookmarks;
        bookmarks = list;
        Preferences prefs = Preferences.userNodeForPackage(ApplicationModel.class);
        prefs.put("bookmarks", Bookmark.formatBooks(list));
        try {
            prefs.flush();
        } catch (BackingStoreException ex) {
            ex.printStackTrace();
        }
        propertyChangeSupport.firePropertyChange(PROPERTY_BOOKMARKS, oldValue, bookmarks);
    }

    public void addRecent(String surl) {
        List oldValue = Collections.unmodifiableList(recent);
        ArrayList<Bookmark> newValue = new ArrayList<Bookmark>(recent);
        Bookmark book = new Bookmark.Item(surl);
        if (newValue.contains(book)) { // move it to the front of the list
            newValue.remove(book);
        }

        newValue.add(book);
        while (newValue.size() > MAX_RECENT) {
            newValue.remove(0);
        }

        Preferences prefs = Preferences.userNodeForPackage(ApplicationModel.class);
        String s = Bookmark.formatBooks(newValue);
        while (s.length() > Preferences.MAX_VALUE_LENGTH) {
            newValue.remove(0);
            s = Bookmark.formatBooks(newValue);
        }
        prefs.put("recent", s);

        try {
            prefs.flush();
        } catch (BackingStoreException ex) {
            ex.printStackTrace();
        }
        this.recent = newValue;
        propertyChangeSupport.firePropertyChange(PROPERTY_RECENT, oldValue, recent);
    }

    public Bookmark addBookmark(final String surl) {

        Bookmark.Item item = new Bookmark.Item(surl);
        URLSplit split = URLSplit.parse(surl);
        String autoTitle = split.file.substring(split.path.length());
        if (autoTitle.length() == 0) autoTitle = surl;
        item.setTitle(autoTitle);

        List<Bookmark> oldValue = Collections.unmodifiableList(new ArrayList<Bookmark>());
        List<Bookmark> newValue = new ArrayList<Bookmark>(bookmarks);

        if (newValue.contains(item)) { // move it to the front of the list
            Bookmark.Item old = (Bookmark.Item) newValue.get(newValue.indexOf(item));
            item = old;  // preserve titles and other future metadata.
            newValue.remove(old);
        }

        newValue.add(item);

        Preferences prefs = Preferences.userNodeForPackage(ApplicationModel.class);
        prefs.put("bookmarks", Bookmark.formatBooks(newValue));

        try {
            prefs.flush();
        } catch (BackingStoreException ex) {
            ex.printStackTrace();
        }
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
     * set the application state.
     * @param state
     * @param deep if true, then unpack the dataset as well.
     * @param forceFill, force a data load
     */
    public void restoreState(Application state, boolean deep, boolean forceFill) {
        if (forceFill) {
            for (DataSourceFilter dsf : this.dom.getDataSourceFilters()) {
                if (dsf.getUri() != null && !dsf.getUri().startsWith("vap+internal:")) {
                    dsf.setUri(null);
                    dsf.getController().setDataSource(true, null);
                }
            }
        }
        this.dom.syncTo(state);
    }

    void doSave(File f) throws IOException {
        StatePersistence.saveState(f, createState(true));
        setUseEmbeddedDataSet(false);
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
    }

    /**
     * open the serialized DOM, apply additional modifications to the DOM, then
     * sync the application to this.
     * @param f
     * @param paramz
     * @throws java.io.IOException
     */
    void doOpen(File f, LinkedHashMap<String, String> deltas) throws IOException {

        if ( f.length()==0 ) throw new IllegalArgumentException("zero-length file: "+f);

        Application state = (Application) StatePersistence.restoreState(f);
        makeValid( state );

        if (deltas != null) {
            for (Entry<String, String> e : deltas.entrySet()) {
                logger.finest("applying to vap " + e.getKey() + "=" + e.getValue());
                String node = e.getKey();
                String sval = e.getValue();
                BeanProperty prop = BeanProperty.create(node);
                if (!prop.isWriteable(state)) {
                    logger.warning("the node " + node + " of " + state + " is not writable");
                    continue;
                }
                Class c = prop.getWriteType(state);
                SerializeDelegate sd = SerializeRegistry.getDelegate(c);
                if (sd == null) {
                    System.err.println("unable to find serialize delegate for " + c.getCanonicalName());
                    continue;
                }
                Object val;
                try {
                    val = sd.parse(sd.typeId(c), sval);
                    prop.setValue(state, val);
                } catch (ParseException ex) {
                    Logger.getLogger(ApplicationModel.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        //logger.fine("" + state.diffs(this.dom));
        restoreState(state, true, true);
        setUseEmbeddedDataSet(false);

        propertyChangeSupport.firePropertyChange("file", null, f);
    }

    void doOpen(File f) throws IOException {
        doOpen(f,null);
    }

    /**
     * Holds value of property autoRangeSuppress.
     */
    private boolean autoRangeSuppress;

    /**
     * Getter for property autoRangeSuppress.
     * @return Value of property autoRangeSuppress.
     */
    public boolean isAutoRangeSuppress() {
        return this.autoRangeSuppress;
    }

    /**
     * Setter for property autoRangeSuppress.
     * @param autoRangeSuppress New value of property autoRangeSuppress.
     */
    public void setAutoRangeSuppress(boolean autoRangeSuppress) {
        this.autoRangeSuppress = autoRangeSuppress;
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
            Logger.getLogger(ApplicationModel.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(ApplicationModel.class.getName()).log(Level.SEVERE, null, ex);
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
            Logger.getLogger(ApplicationModel.class.getName()).log(Level.SEVERE, null, ex);
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

        local = FileSystem.settings().getLocalCacheDir();
        if (local != null) {
            return Util.deleteFileTree(local);
        } else {
            return true;
        }
    }
    /**
     * Utility field used by bound properties.
     */
    private java.beans.PropertyChangeSupport propertyChangeSupport = new java.beans.PropertyChangeSupport(this);

    /**
     * wait for autoplot to settle.
     */
    public void waitUntilIdle(boolean runtimeException) throws InterruptedException {
        logger.fine("enter waitUntilIdle, pendingChanges=" + dom.getController().isPendingChanges());
        while (dom.getController().isPendingChanges()) {
            Thread.sleep(30);
        }
        logger.fine("waiting for canvas");
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

