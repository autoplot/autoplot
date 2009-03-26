/*
 * ApplicationModel.java
 *
 * Created on April 1, 2007, 8:21 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import org.virbo.autoplot.bookmarks.Bookmark;
import java.util.logging.Level;
import org.das2.DasApplication;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.graph.DasAxis;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasPlot;
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
import java.util.List;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.Timer;
import javax.xml.parsers.ParserConfigurationException;
import org.das2.beans.BeansUtil;
import org.das2.components.propertyeditor.EnumerationEditor;
import org.das2.util.Base64;
import org.das2.util.filesystem.FileSystem;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.autoplot.dom.DataSourceController;
import org.virbo.autoplot.dom.Panel;
import org.virbo.autoplot.state.StatePersistence;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.URLSplit;
import org.virbo.datasource.capability.Caching;
import org.virbo.qstream.QDataSetStreamHandler;
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

    public enum RenderType {
        spectrogram, hugeScatter, series, scatter, colorScatter, histogram, fill_to_zero,
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

        BeansUtil.registerEditor( RenderType.class, EnumerationEditor.class );
        
        DataSetURL.init();

        dom= new Application();
        new ApplicationController( this, dom );
        
        canvas = dom.getController().addCanvas();

        this.application = canvas.getApplication();

        dom.getController().addPanel(null,null);

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
        dom.getController().getDataSourceFilter().getController().setDataSetInternal(null); // clear out properties and metadata
        dom.getController().getDataSourceFilter().getController().setDataSetInternal(ds);
    }


    public void setDataSource(DataSource dataSource) {
        dom.getController().getDataSourceFilter().getController().setDataSource(dataSource);
    }

    public DataSource dataSource() {
        return dom.getController().getDataSourceFilter().getController()._getDataSource();
    }

    /**
     * set the plot range, minding the isotropic property.
     * TODO: the DasAxis.Lock never worked its way into the MVC version.  Find it, then delete this.
     * @param plot
     * @param xdesc
     * @param ydesc
     */
    private void setPlotRange(DasPlot plot,
            AutoplotUtil.AutoRangeDescriptor xdesc, AutoplotUtil.AutoRangeDescriptor ydesc) {

        if (dom.getController().getPlot().isIsotropic() && xdesc.range.getUnits().isConvertableTo(ydesc.range.getUnits()) && xdesc.log == false && ydesc.log == false) {

            DasAxis axis;
            AutoplotUtil.AutoRangeDescriptor desc; // controls the range

            DasAxis otherAxis;
            AutoplotUtil.AutoRangeDescriptor otherDesc; // controls the range

            if (plot.getXAxis().getDLength() < plot.getYAxis().getDLength()) {
                axis = plot.getXAxis();
                desc = xdesc; // controls the range

                otherAxis = plot.getYAxis();
                otherDesc = ydesc; // controls the range

            } else {
                axis = plot.getYAxis();
                desc = ydesc; // controls the range

                otherAxis = plot.getXAxis();
                otherDesc = xdesc; // controls the range                

            }

            axis.setLog(false);
            otherAxis.setLog(false);
            Datum ratio = desc.range.width().divide(axis.getDLength());
            DatumRange otherRange = otherDesc.range;
            Datum otherRatio = otherRange.width().divide(otherAxis.getDLength());
            DasAxis.Lock lock = otherAxis.mutatorLock(); // prevent other isotropic code from kicking in.

            lock.lock();
            double expand = (ratio.divide(otherRatio).doubleValue(Units.dimensionless) - 1) / 2;
            if (Math.abs(expand) > 0.0001) {
                DatumRange newOtherRange = DatumRangeUtil.rescale(otherRange, 0 - expand, 1 + expand);
                otherAxis.resetRange(newOtherRange);
            } else {
                otherAxis.resetRange(otherRange);
            }
            axis.resetRange(desc.range);
            lock.unlock();
        } else {
            plot.getXAxis().setLog(xdesc.log);
            plot.getXAxis().resetRange(xdesc.range);
            plot.getYAxis().setLog(ydesc.log);
            plot.getYAxis().resetRange(ydesc.range);
        }

    }


    public synchronized void addPropertyChangeListener(java.beans.PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public synchronized void removePropertyChangeListener(java.beans.PropertyChangeListener listener) {
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

        surl = URLSplit.format(URLSplit.parse(surl));
        //surl = DataSetURL.maybeAddFile(surl);

        try {
            if (surl.endsWith(".vap")) {
                try {
                    URL url = DataSetURL.getURL(surl);
                    mon.started();
                    mon.setProgressMessage("loading vap file");
                    File openable = DataSetURL.getFile(url, application.getMonitorFactory().getMonitor( canvas, "loading vap", ""));
                    doOpen(openable);
                    mon.setProgressMessage("done loading vap file");
                    mon.finished();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(canvas, "<html>Unable to open resource: <br>" + surl);
                }
            } else {
                getDataSourceFilterController().resetSuri(surl, mon );
                
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
        if ( surl == null && oldVal == null) {
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
            Bookmark.Item old= (Bookmark.Item) newValue.get( newValue.indexOf(item) );
            item= old;  // preserve titles and other future metadata.
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
        dom.getController().getPlot().getController().resetZoom();
    }

    void increaseFontSize() {
        Font f = this.dom.getOptions().getCanvasFont();
        f = f.deriveFont(f.getSize2D() * 1.1f);
        this.dom.getOptions().setCanvasFont(f);

    }

    void decreaseFontSize() {
        Font f = this.dom.getOptions().getCanvasFont();
        f = f.deriveFont(f.getSize2D() / 1.1f);
        this.dom.getOptions().setCanvasFont(f);
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
    public void restoreState( Application state, boolean deep, boolean forceFill) {
        this.dom.syncTo(state);
    }

    void doSave(File f) throws IOException {
        StatePersistence.saveState(f, createState(true));
        setUseEmbeddedDataSet(false);
    }

    void doOpen(File f) throws IOException {
        Application state = (Application) StatePersistence.restoreState(f);
        logger.fine( "" + this.dom.diffs(state) );
        restoreState(state, true, true);
        setUseEmbeddedDataSet(false);
        propertyChangeSupport.firePropertyChange("file", null, f);
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
            if ( dom.getController().getDataSourceFilter().getController().getDataSet() == null) {
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
        logger.fine("enter waitUntilIdle, pendingChanges="+ dom.getController().isPendingChanges() );
        while ( dom.getController().isPendingChanges() ) {
            Thread.sleep(30);
        }
        logger.fine("waiting for canvas" );
        canvas.waitUntilIdle();
        logger.fine("done waiting" );
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

