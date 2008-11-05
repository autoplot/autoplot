/*
 * ApplicationModel.java
 *
 * Created on April 1, 2007, 8:21 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.awt.Color;
import org.das2.dataset.DataSet;
import org.virbo.autoplot.bookmarks.Bookmark;
import java.util.logging.Level;
import org.das2.CancelledOperationException;
import org.das2.DasApplication;
import org.das2.beans.BeansUtil;
import org.das2.dataset.CacheTag;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.event.BoxRenderer;
import org.das2.event.BoxSelectionEvent;
import org.das2.event.BoxSelectionListener;
import org.das2.event.BoxSelectorMouseModule;
import org.das2.event.BoxZoomMouseModule;
import org.das2.event.MouseModule;
import org.das2.event.PointSlopeDragRenderer;
import org.das2.event.ZoomPanMouseModule;
import org.das2.graph.ColumnColumnConnector;
import org.das2.graph.DasAxis;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasColorBar;
import org.das2.graph.DasColumn;
import org.das2.graph.DasPlot;
import org.das2.graph.DasRow;
import org.das2.graph.DefaultPlotSymbol;
import org.das2.graph.FillStyle;
import org.das2.graph.PlotSymbol;
import org.das2.graph.PsymConnector;
import org.das2.graph.Renderer;
import org.das2.graph.SeriesRenderer;
import org.das2.graph.SpectrogramRenderer;
import org.virbo.qstream.StreamException;
import org.das2.system.RequestProcessor;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyEditor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.beans.binding.BindingContext;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.Timer;
import javax.xml.parsers.ParserConfigurationException;
import org.das2.util.Base64;
import org.das2.util.filesystem.FileSystem;
import org.virbo.autoplot.layout.LayoutUtil;
import org.virbo.autoplot.state.ApplicationState;
import org.virbo.autoplot.state.Options;
import org.virbo.autoplot.state.StatePersistence;
import org.virbo.autoplot.util.DateTimeDatumFormatter;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.DataSetAdapter;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.TableDataSetAdapter;
import org.virbo.dataset.TransposeRank2DataSet;
import org.virbo.dataset.VectorDataSetAdapter;
import org.virbo.dataset.WritableDataSet;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.URLSplit;
import org.virbo.datasource.capability.Caching;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.qstream.QDataSetStreamHandler;
import org.virbo.qstream.SimpleStreamFormatter;
import org.xml.sax.SAXException;

/**
 * Internal model of the application to separate model from view.
 * @author jbf
 */
public class ApplicationModel {

    private DataSource dataSource;
    private String surl;
    DasApplication application;
    DasCanvas canvas;
    DasPlot plot;
    DasPlot overviewPlot;
    ColumnColumnConnector overviewPlotConnector;
    SeriesRenderer seriesRend;
    SeriesRenderer overSeriesRend;
    SpectrogramRenderer spectrogramRend;
    SpectrogramRenderer overSpectrogramRend;
    DasColorBar colorbar;
    Timer tickleTimer;
    Timer updateTsbTimer;
    Options options;

    /**
     * guess the best dimension to slice by default, based on metadata.  Currently,
     * this looks for the names lat, lon, and angle.
     */
    private void guessSliceDimension() {
        int lat = -1, lon = -1;

        int[] slicePref = new int[]{1, 1, 1};
        for (int i = 0; i < depnames.size(); i++) {
            String n = depnames.get(i);
            if (n.equals("lat")) {
                slicePref[i] = 0;
                lat = i;
            }
            if (n.equals("lon")) {
                slicePref[i] = 0;
                lon = i;
            }
            if (n.equals("angle")) slicePref[i] = 2;
            if (n.equals("bundle")) slicePref[i] = 2;
        }

        int sliceIndex = 0;
        int bestSlice = 0;
        for (int i = 0; i < 3; i++) {
            if (slicePref[i] > bestSlice) {
                sliceIndex = i;
                bestSlice = slicePref[i];
            }
        }

        if (lat > -1 && lon > -1 && lat < lon) {
            this.transpose = true;
        }

        int oldSliceDimension = this.sliceDimension;
        this.sliceDimension = sliceIndex;
        propertyChangeSupport.firePropertyChange(PROP_SLICEDIMENSION, -1, sliceDimension);
    }

    @SuppressWarnings("unchecked")
    private Map sliceProperties(Map properties, int sliceDimension) {
        Map result = new LinkedHashMap(properties);
        List<Object> deps = new ArrayList(3);
        for (int i = 0; i < 3; i++) {
            deps.add(i, properties.get("DEPEND_" + i));
        }

        deps.remove(sliceDimension);
        deps.add(2, null);

        for (int i = 0; i < 3; i++) {
            result.put("DEPEND_" + i, deps.get(i));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map transposeProperties(Map properties) {
        Map result = new LinkedHashMap(properties);
        result.put("DEPEND_1", properties.get("DEPEND_0"));
        result.put("DEPEND_0", properties.get("DEPEND_1"));
        return result;

    }

    /**
     * check to make sure properties units are consistent with dataset units,
     * otherwise we'll have problems with units conversions.
     * @param properties
     * @param dataset
     */
    private void unitsCheck(Map properties, QDataSet dataset) {
        Units u0 = (Units) dataset.property(QDataSet.UNITS);
        Units u1 = (Units) properties.get(QDataSet.UNITS);
        if (u0 == null || u1 == null || !u0.isConvertableTo(u1)) {
            properties.put(QDataSet.UNITS, u0);
        }
        for (int i = 0; i < QDataSet.MAX_RANK; i++) {
            QDataSet dep = (QDataSet) dataset.property("DEPEND_" + i);
            Map depprops = (Map) properties.get("DEPEND_" + i);
            if (dep != null && depprops != null) {
                unitsCheck(depprops, dep);
            }
        }
    }

    enum RenderType {

        spectrogram, series, scatter, histogram, fill_to_zero,
    }
    /**
     * the one and only displayed dataset
     */
    QDataSet dataset;
    /**
     * the dataset with fill applied
     */
    QDataSet fillDataset;
    /**
     * zooming in is allowed to display context plot
     */
    boolean allowAutoContext = false;
    DatumRange originalXRange;
    DatumRange originalYRange;
    DatumRange originalZRange;
    static final Logger logger = Logger.getLogger("virbo.autoplot");
    private int threadRunning = 0;
    private final int UPDATE_FILL_THREAD_RUNNING = 1;
    private final int TICKLE_TIMER_THREAD_RUNNING = 2;

    private synchronized void markThreadRunning(int threadRunningMask) {
        this.threadRunning = this.threadRunning | threadRunningMask;
    }

    private synchronized void clearThreadRunning(int threadRunningMask) {
        this.threadRunning = this.threadRunning & (~threadRunningMask);
    }
    /**
     * true if running in headless environment
     */
    boolean headless;
    /**
     * data source has been requested
     */
    public static String PROPERTY_DATASOURCE = "dataSource";
    /**
     * data source URL can change because of TSB without data source changing.
     */
    public static String PROPERTY_DATASOURCE_URL = "dataSourceURL";
    /**
     * dataset with fill data has been recalculated
     */
    public static String PROPERTY_FILL = "fill";
    public static String PROPERTY_FILE = "file";
    public static String PROPERTY_RECENT = "recent";
    public static String PROPERTY_BOOKMARKS = "bookmarks";
    /**
     * status message updates
     */
    public static String PROPERTY_STATUS = "status";
    public static int SYMSIZE_DATAPOINT_COUNT = 500;
    private PropertyChangeListener listener = new PropertyChangeListener() {

        public void propertyChange(PropertyChangeEvent e) {
            if (e.getSource() instanceof DasAxis) {
                DasAxis axis = (DasAxis) e.getSource();
                // we can safely ignore these events.
                if (((DasAxis) e.getSource()).valueIsAdjusting()) {
                    return;
                }
                if (isotropic) {
                    checkIsotropic(axis);
                }
            }
            if (e.getPropertyName().equals("datumRange") && e.getSource() == plot.getXAxis()) {
                if (autoOverview && allowAutoContext && originalXRange != null && originalXRange.getUnits().isConvertableTo(plot.getXAxis().getUnits()) && originalXRange.contains(plot.getXAxis().getDatumRange())) {
                    setShowContextOverview(true);
                }
            }
            propertyChangeSupport.firePropertyChange(e);
        }
    };

    private void checkIsotropic(DasAxis axis) {
        if ((axis == plot.getXAxis() || axis == plot.getYAxis()) && plot.getXAxis().getUnits().isConvertableTo(plot.getYAxis().getUnits()) && !plot.getXAxis().isLog() && !plot.getYAxis().isLog()) {
            DasAxis otherAxis = plot.getYAxis();
            if (axis == plot.getYAxis()) {
                otherAxis = plot.getXAxis();
            }
            Datum ratio = axis.getDatumRange().width().divide(axis.getDLength());
            DatumRange otherRange = otherAxis.getDatumRange();
            Datum otherRatio = otherRange.width().divide(otherAxis.getDLength());
            double expand = (ratio.divide(otherRatio).doubleValue(Units.dimensionless) - 1) / 2;
            if (Math.abs(expand) > 0.0001) {
                DatumRange newOtherRange = DatumRangeUtil.rescale(otherRange, 0 - expand, 1 + expand);
                otherAxis.setDatumRange(newOtherRange);
            }
        }
    }
    private static final int MAX_RECENT = 20;

    public ApplicationModel() {

        DataSetURL.init();

        headless = "true".equals(AutoplotUtil.getProperty("java.awt.headless", "false"));

        options = new Options();
        if (!headless && DasApplication.hasAllPermission()) options.loadPreferences();

        canvas = new DasCanvas();

        if (options.getCanvasFont().equals("")) {
            canvas.setBaseFont(canvas.getBaseFont().deriveFont(Font.ITALIC, 18.f));
        } else {
            canvas.setBaseFont(Font.decode(options.getCanvasFont()));
        }

        canvas.setForeground(options.getForeground());
        canvas.setBackground(options.getBackground());

        canvas.setPrintingTag("");

        canvas.addPropertyChangeListener(listener);

        this.application = canvas.getApplication();

        createDasPlot();
        try {
            parseFillValidRange("", "");
        } catch (ParseException ex) {
            ex.printStackTrace();
        }

        tickleTimer = new Timer(100, new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                startUpdateUnitsThread();
                clearThreadRunning(TICKLE_TIMER_THREAD_RUNNING);
            }
        });
        tickleTimer.setRepeats(false);

        updateTsbTimer = new Timer(100, new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                updateTsb(false);
            }
        });
        updateTsbTimer.setRepeats(false);

    }

    private void createDasPlot() {
        DatumRange x = DatumRange.newDatumRange(0, 10, Units.dimensionless);
        DatumRange y = DatumRange.newDatumRange(0, 1000, Units.dimensionless);
        DasAxis xaxis = new DasAxis(x.min(), x.max(), DasAxis.HORIZONTAL);
        DasAxis yaxis = new DasAxis(y.min(), y.max(), DasAxis.VERTICAL);
        DasRow row = new DasRow(canvas, null, 0, 1, 2, -3, 0, 0);
        DasColumn col = new DasColumn(canvas, null, 0, 1, 5, -3, 0, 0);
        plot = new DasPlot(xaxis, yaxis);
        canvas.add(plot, row, col);

        //plot.getRow().setPosition( 0, .50 );
        overviewPlot =
                DasPlot.createPlot(DatumRange.newDatumRange(0, 10, Units.dimensionless),
                DatumRange.newDatumRange(0, 1000, Units.dimensionless));
        canvas.add(overviewPlot, DasRow.create(null, plot.getRow(), "100%+5em", "100%+8em"), plot.getColumn());
        //overviewPlot.setVisible(false);

        BoxSelectorMouseModule overviewZoom = new BoxSelectorMouseModule(overviewPlot,
                overviewPlot.getXAxis(),
                overviewPlot.getYAxis(),
                null,
                new BoxRenderer(overviewPlot), "zoom");
        overviewPlot.getMouseAdapter().setPrimaryModule(overviewZoom);

        overviewZoom.addBoxSelectionListener(new BoxSelectionListener() {

            public void BoxSelected(BoxSelectionEvent e) {
                DatumRange dr = e.getXRange();
                plot.getXAxis().setDatumRange(dr);
            }
        });

        // the axes need to know about the plot, so they can do reset axes units properly.
        plot.getXAxis().setPlot(plot);
        plot.getYAxis().setPlot(plot);

        plot.getMouseAdapter().addMouseModule(new MouseModule(plot, new PointSlopeDragRenderer(plot, plot.getXAxis(), plot.getYAxis()), "Slope"));

        plot.getMouseAdapter().removeMenuItem("Dump Data");

        plot.getMouseAdapter().addMenuItem(new JMenuItem(new AbstractAction("Reset Zoom") {

            public void actionPerformed(ActionEvent e) {
                resetZoom();
            }
        }));

        plot.getMouseAdapter().addMenuItem(GuiSupport.createEZAccessMenu(this));

        plot.addPropertyChangeListener(listener);
        plot.getXAxis().addPropertyChangeListener(listener);
        plot.getYAxis().addPropertyChangeListener(listener);

        BoxZoomMouseModule boxmm = (BoxZoomMouseModule) plot.getMouseAdapter().getModuleByLabel("Box Zoom");
        plot.getMouseAdapter().setPrimaryModule(boxmm);

        //plot.getMouseAdapter().addMouseModule( new AnnotatorMouseModule(plot) ) ;

        seriesRend = new SeriesRenderer();
        seriesRend.setDataSetLoader(null);

        seriesRend.setColor(canvas.getForeground());

        overSeriesRend = new SeriesRenderer();
        overSeriesRend.setColor(canvas.getForeground());

        plot.addRenderer(seriesRend);
        overviewPlot.addRenderer(overSeriesRend);
        overviewPlot.setPreviewEnabled(true);

        overviewPlotConnector =
                new ColumnColumnConnector(canvas, plot,
                DasRow.create(null, plot.getRow(), "0%", "100%+2em"), overviewPlot);
        overviewPlotConnector.setBottomCurtain(true);
        overviewPlotConnector.setCurtainOpacityPercent(80);
        overviewPlotConnector.getMouseAdapter().setPrimaryModule(overviewZoom);
        overviewPlotConnector.getMouseAdapter().setSecondaryModule(new ColumnColumnConnectorMouseModule(plot, overviewPlot));

        overviewPlotConnector.setVisible(false);
        overviewPlot.setVisible(false);

        DatumRange colorRange = new DatumRange(0, 100, Units.dimensionless);
        colorbar = new DasColorBar(colorRange.min(), colorRange.max(), false);
        colorbar.setFillColor(new java.awt.Color(0, true));
        //colorbar.setVisible(false);

        canvas.add(overviewPlotConnector);

        colorbar.addPropertyChangeListener(listener);

        spectrogramRend = new SpectrogramRenderer(null, colorbar);
        spectrogramRend.addPropertyChangeListener(listener);

        spectrogramRend.setActive(false);

        overSpectrogramRend = new SpectrogramRenderer(null, colorbar);
        plot.addRenderer(spectrogramRend);
        overviewPlot.addRenderer(overSpectrogramRend);

        canvas.add(colorbar, plot.getRow(), DasColorBar.getColorBarColumn(plot.getColumn()));
        colorbar.setVisible(false);

        overSeriesRend.setActive(false);
        overSpectrogramRend.setActive(false);

        seriesRend.setColorBar(colorbar);
        seriesRend.setColorByDataSetId(QDataSet.PLANE_0);

        seriesRend.setAntiAliased(true);

        seriesRend.addPropertyChangeListener(listener);

        if (!headless) {
            boxmm.setAutoUpdate(true);
        }

        MouseModule zoomPan = new ZoomPanMouseModule(plot, plot.getXAxis(), plot.getYAxis());
        plot.getMouseAdapter().setSecondaryModule(zoomPan);

        MouseModule zoomPanX = new ZoomPanMouseModule(plot.getXAxis(), plot.getXAxis(), null);
        plot.getXAxis().getMouseAdapter().setSecondaryModule(zoomPanX);

        zoomPanX =
                new ZoomPanMouseModule(overviewPlot.getXAxis(), overviewPlot.getXAxis(), null);
        overviewPlot.getXAxis().getMouseAdapter().setSecondaryModule(zoomPanX);

        MouseModule zoomPanY = new ZoomPanMouseModule(plot.getYAxis(), null, plot.getYAxis());
        plot.getYAxis().getMouseAdapter().setSecondaryModule(zoomPanY);

        MouseModule zoomPanZ = new ZoomPanMouseModule(colorbar, null, colorbar);
        colorbar.getMouseAdapter().setSecondaryModule(zoomPanZ);

        setSpecialEffects(false);
        canvas.revalidate();


    }

    /**
     * used to explicitly set the rendering type.
     * @param renderType
     */
    public void setRenderType(RenderType renderType) {
        WritableDataSet fillDs = (WritableDataSet) this.fillDataset;

        if (renderType == RenderType.spectrogram) {

            setRenderer(spectrogramRend, overSpectrogramRend);

        } else if (renderType == RenderType.series) {

            seriesRend.setPsymConnector(PsymConnector.SOLID);
            seriesRend.setHistogram(false);
            seriesRend.setFillToReference(false);

            setRenderer(seriesRend, overSeriesRend);

        } else if (renderType == RenderType.scatter) {
            seriesRend.setPsymConnector(PsymConnector.NONE);
            seriesRend.setFillToReference(false);

            setRenderer(seriesRend, overSeriesRend);

        } else if (renderType == RenderType.histogram) {
            seriesRend.setPsymConnector(PsymConnector.SOLID);
            seriesRend.setFillToReference(true);
            seriesRend.setHistogram(true);

            setRenderer(seriesRend, overSeriesRend);

        } else if (renderType == RenderType.fill_to_zero) {
            seriesRend.setPsymConnector(PsymConnector.SOLID);
            seriesRend.setFillToReference(true);
            seriesRend.setHistogram(false);

            setRenderer(seriesRend, overSeriesRend);

        } else {
            throw new IllegalArgumentException("not supported: " + renderType);

        }


    }

    public void setRenderType(RenderType renderType, boolean autorange, boolean interpretMetadata, WritableDataSet fillDs, Map properties) {

        if (renderType == RenderType.spectrogram) {
            updateFillSpec(fillDs, autorange, interpretMetadata ? properties : Collections.EMPTY_MAP);
            seriesRend.setDataSet(null);
            setRenderer(spectrogramRend, overSpectrogramRend);

        } else {
            updateFillSeries(fillDs, autorange, interpretMetadata ? properties : Collections.EMPTY_MAP);
            if (fillDs.rank() == 2) {  // SeriesRenderer rank 3 must have solid lines.
                seriesRend.setPsymConnector(PsymConnector.SOLID);
            }

            spectrogramRend.setDataSet(null);

            setRenderer(seriesRend, overSeriesRend);

        }

    }

    protected static RenderType getRenderType(QDataSet fillds) {
        RenderType spec = fillds.rank() >= 2 ? RenderType.spectrogram : RenderType.series;

        QDataSet dep1 = (QDataSet) fillds.property(QDataSet.DEPEND_1);

        if (fillds.rank() == 2 && dep1 != null && isVectorOrBundleIndex(dep1)) {
            spec = RenderType.series;
        }

        return spec;
    }

    /**
     * return the renderers that should be used to render the data.  More than one renderer can be returned 
     * to support plotting vector components.
     * @param ds
     * @param type
     * @param recyclable Reuse these if possible to reduce jitter.  May be null.
     * @return
     */
    protected static List<Renderer> getRenderers(QDataSet ds, RenderType renderType, List<Renderer> recyclable, DasColorBar colorbar) {
        if (recyclable == null) recyclable = Collections.emptyList();
        if (renderType == RenderType.spectrogram) {
            if (recyclable != null && recyclable.size() == 1 && recyclable.get(0) instanceof SpectrogramRenderer) {
                recyclable.get(0).setDataSet(TableDataSetAdapter.create(ds));
                return recyclable;
            } else {
                Renderer result = new SpectrogramRenderer(null, colorbar);
                result.setDataSet(TableDataSetAdapter.create(ds));
                return Collections.singletonList(result);
            }
        } else {
            List<Renderer> result;
            if (ds.rank() == 1) {
                if (recyclable != null && recyclable.size() == 1 && recyclable.get(0) instanceof SeriesRenderer) {
                    result = recyclable;
                } else {
                    result = Collections.singletonList((Renderer) new SeriesRenderer());
                }
                result.get(0).setDataSet(VectorDataSetAdapter.create(ds));
            } else {
                int dim = ds.length(0);
                Color color = Color.black; // TODO: this will change.
                result = new ArrayList<Renderer>();
                for (int i = 0; i < dim; i++) {
                    SeriesRenderer rend1 = new SeriesRenderer();
                    float[] colorHSV = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
                    if (colorHSV[2] < 0.7f) {
                        colorHSV[2] = 0.7f;
                    }
                    if (colorHSV[1] < 0.7f) {
                        colorHSV[1] = 0.7f;
                    }
                    rend1.setColor(Color.getHSBColor(i / 6.f, colorHSV[1], colorHSV[2]));
                    rend1.setFillColor(Color.getHSBColor(i / 6.f, colorHSV[1], colorHSV[2]));
                    rend1.setDataSet(VectorDataSetAdapter.create(DataSetOps.slice1(ds, i)));
                    result.add(rend1);
                }
            }

            for (Renderer rend1 : result) {
                SeriesRenderer seriesRend = (SeriesRenderer) rend1;
                if (renderType == RenderType.series) {

                    seriesRend.setPsymConnector(PsymConnector.SOLID);
                    seriesRend.setHistogram(false);
                    seriesRend.setFillToReference(false);

                } else if (renderType == RenderType.scatter) {
                    seriesRend.setPsymConnector(PsymConnector.NONE);
                    seriesRend.setFillToReference(false);

                } else if (renderType == RenderType.histogram) {
                    seriesRend.setPsymConnector(PsymConnector.SOLID);
                    seriesRend.setFillToReference(true);
                    seriesRend.setHistogram(true);

                } else if (renderType == RenderType.fill_to_zero) {
                    seriesRend.setPsymConnector(PsymConnector.SOLID);
                    seriesRend.setFillToReference(true);
                    seriesRend.setHistogram(false);

                }
            }
            return result;
        }
    }

    protected void setRenderer(Renderer rend, Renderer overRend) {
        Renderer[] rends = plot.getRenderers();
        for (int i = 0; i < rends.length; i++) {
            if (rends[i] != rend) rends[i].setActive(false);
        }
        rend.setActive(true);

        rends = overviewPlot.getRenderers();
        for (int i = 0; i < rends.length; i++) {
            if (rends[i] != overRend) rends[i].setActive(false);
        }
        overRend.setActive(true);

    }

    public DasCanvas getCanvas() {
        return canvas;
    }
    PropertyChangeListener timeSeriesBrowseListener;
    TimeSeriesBrowse tsb = null;
    Caching caching = null;
    ProgressMonitor mon = null;

    /**
     * just plot this dataset.  No capabilities, no urls.  Metadata is set to
     * allow inspection of dataset.
     * @param ds
     */
    void setDataSet(QDataSet ds) {
        this.setDataSource(null);
        this.setDataSourceURL(null);
        if (timeSeriesBrowseListener != null) {
            this.plot.getXAxis().removePropertyChangeListener(timeSeriesBrowseListener);
            timeSeriesBrowseListener = null;
        }
        setDataSetInternal(ds, true);
    }

    /**
     * set the new dataset, do autoranging and autolabelling.
     * 
     * preconditions: autoplot is displaying any dataset.  A new DataSource has
     *  been set, but the dataset is generally not from the DataSource.
     *   
     * postconditions: the dataset is set, labels are set, axes are set.  Labels
     *  reset might have triggered a timer that will redo layout.
     * 
     * @param ds
     * @param autorange if false, autoranging will not be done.  if false, autoranging
     *   might be done.
     */
    private void setDataSetInternal(QDataSet ds, boolean autorange) {

        List<String> problems = new ArrayList<String>();

        if (ds != null && !DataSetUtil.validate(ds, problems)) {
            StringBuffer message = new StringBuffer("data set is invalid:\n");
            for (String s : problems) {
                message.append(s + "\n");
            }
            JOptionPane.showMessageDialog(canvas, message);
            return;
        }

        this.dataset = ds;
        this.embedDsDirty = true;

        if (dataset == null) {
            seriesRend.setDataSet(null);
            spectrogramRend.setDataSet(null);
            return;
        }

        setStatus("apply fill and autorange");

        String[] depNames = new String[3];
        for (int i = 0; i < dataset.rank(); i++) {
            depNames[i] = "dim" + i;
            QDataSet dep0 = (QDataSet) dataset.property("DEPEND_" + i);
            if (dep0 != null) {
                String dname = (String) dep0.property(QDataSet.NAME);
                if (dname != null) {
                    depNames[i] = dname;
                }
            }
        }

        logger.fine("dep names: " + Arrays.asList(depNames));
        setDepnames(Arrays.asList(depNames));
        if (dataset.rank() > 2) guessSliceDimension();

        updateFill(autorange, true);

        originalXRange = plot.getXAxis().getDatumRange();
        originalYRange = plot.getYAxis().getDatumRange();
        originalZRange = colorbar.getDatumRange();
        if (autoOverview && !autoRangeSuppress) {
            setShowContextOverview(false);
        }
        if (!autoRangeSuppress) {
            allowAutoContext = true;
        }

        propertyChangeSupport.firePropertyChange(PROPERTY_DATASOURCE, null, null);
        if (autoRangeSuppress) {
            autoRangeSuppress = false;
        }
        setStatus("done, apply fill and autorange");

    }

    public void updateTsb(boolean autorange) {
        if (tsb == null) {
            return;
        }
        if (UnitsUtil.isTimeLocation(ApplicationModel.this.plot.getXAxis().getUnits())) {

            // CacheTag "tag" identifies what we have already
            QDataSet ds = dataset;
            QDataSet dep0 = ds == null ? null : (QDataSet) ds.property(QDataSet.DEPEND_0);
            CacheTag tag = dep0 == null ? null : (CacheTag) dep0.property(QDataSet.CACHE_TAG);

            DatumRange visibleRange = ApplicationModel.this.plot.getXAxis().getDatumRange();

            Datum newResolution = visibleRange.width().divide(ApplicationModel.this.plot.getXAxis().getDLength());

            // don't waste time by chasing after 10% of a dataset.
            DatumRange newRange = visibleRange;
            newRange = DatumRangeUtil.rescale(newRange, 0.1, 0.9);

            CacheTag newCacheTag = new CacheTag(newRange, newResolution);

            if (tag == null || !tag.contains(newCacheTag)) {
                if (plot.isOverSize()) {
                    visibleRange = DatumRangeUtil.rescale(visibleRange, -0.3, 1.3);
                }
                tsb.setTimeRange(visibleRange);
                tsb.setTimeResolution(newResolution);
                String surl;
                surl = DataSetURL.getDataSourceUri(dataSource);
                // check the registry for URLs, compare to surl, append prefix if necessary.
                if (surl.equals(this.surl)) {
                    logger.fine("we do no better with tsb");
                } else {
                    update(autorange, autorange);
                    String oldVal = this.surl;
                    this.surl = surl;
                    propertyChangeSupport.firePropertyChange(PROPERTY_DATASOURCE_URL, oldVal, surl);
                }
            }
        }
    }

    public void setDataSource(DataSource dataSource) {

        DataSource oldSource = this.dataSource;
        this.dataSource = dataSource;

        if (dataSource == null) {
            caching = null;
            tsb = null;
            this.surl = null;

            if (timeSeriesBrowseListener != null) {
                this.plot.getXAxis().removePropertyChangeListener(timeSeriesBrowseListener);
                timeSeriesBrowseListener = null;
            }

        } else {

            caching = dataSource.getCapability(Caching.class);
            tsb = dataSource.getCapability(TimeSeriesBrowse.class);

            if (tsb != null) {

                if (timeSeriesBrowseListener != null) {
                    this.plot.getXAxis().removePropertyChangeListener(timeSeriesBrowseListener);
                }

                boolean setTsbInitialResolution = true;
                if (setTsbInitialResolution) {
                    DatumRange timeRange = tsb.getTimeRange();
                    this.plot.getXAxis().resetRange(timeRange);
                    updateTsb(true);
                }

                timeSeriesBrowseListener = new PropertyChangeListener() {

                    public void propertyChange(PropertyChangeEvent e) {
                        if (plot.getXAxis().valueIsAdjusting()) {
                            return;
                        }
                        if (e.getPropertyName().equals("datumRange")) {
                            updateTsbTimer.restart();
                        }
                    }
                };

                this.plot.getXAxis().addPropertyChangeListener(timeSeriesBrowseListener);

                if (oldSource == null || !oldSource.equals(dataSource)) {
                    propertyChangeSupport.firePropertyChange(PROPERTY_DATASOURCE, oldSource, dataSource);
                }

                return;

            } else {
                if (timeSeriesBrowseListener != null) {
                    this.plot.getXAxis().removePropertyChangeListener(timeSeriesBrowseListener);
                    timeSeriesBrowseListener = null;
                }
                if (oldSource == null || !oldSource.equals(dataSource)) {
                    update(true, true);
                    propertyChangeSupport.firePropertyChange(PROPERTY_DATASOURCE, oldSource, dataSource);
                }
            }
        }


    }

    public DataSource dataSource() {
        return this.dataSource;
    }

    public List getDataSources() {
        return new ArrayList(Collections.singleton(dataSource));
    }

    /**
     * set the plot range, minding the isotropic property.
     * @param plot
     * @param xdesc
     * @param ydesc
     */
    private void setPlotRange(DasPlot plot,
            AutoplotUtil.AutoRangeDescriptor xdesc, AutoplotUtil.AutoRangeDescriptor ydesc) {

        if (isotropic && xdesc.range.getUnits().isConvertableTo(ydesc.range.getUnits()) && xdesc.log == false && ydesc.log == false) {

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

    /**
     * 
     * @param fillDs
     * @param autoRange
     * @param props, explicit settings from metadata
     */
    private void updateFillSpec(WritableDataSet fillDs, boolean autoRange, Map props) {
        if (props == null) {
            props = Collections.EMPTY_MAP;
        }

        QDataSet xds = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
        if (xds == null) {
            xds = DataSetUtil.indexGenDataSet(fillDs.length());
        }

        QDataSet yds = (QDataSet) fillDs.property(QDataSet.DEPEND_1);
        if (yds == null) {
            yds = DataSetUtil.indexGenDataSet(fillDs.length(0)); // QUBE

        }

        double cadence = DataSetUtil.guessCadence(xds, null);
        ((MutablePropertyDataSet) xds).putProperty(QDataSet.CADENCE, cadence);

        colorbar.setVisible(true);

        AutoplotUtil.AutoRangeDescriptor xdesc = AutoplotUtil.autoRange(xds, (Map) props.get(QDataSet.DEPEND_0));

        AutoplotUtil.AutoRangeDescriptor ydesc = AutoplotUtil.autoRange(yds, (Map) props.get(QDataSet.DEPEND_1));

        if (autoranging && autoRange && !autoRangeSuppress) {

            AutoplotUtil.AutoRangeDescriptor desc = AutoplotUtil.autoRange(fillDs, props);

            colorbar.setLog(desc.log);
            colorbar.resetRange(desc.range);

            Rectangle bounds = colorbar.getBounds();
            if (bounds.width + bounds.x > canvas.getWidth()) {
                int dx = canvas.getWidth() - (bounds.width + bounds.x);
                int oldx = plot.getColumn().getDMaximum();
                plot.getColumn().setDMaximum(oldx + dx);
            }

            setPlotRange(plot, xdesc, ydesc);

        }

        setPlotRange(overviewPlot, xdesc, ydesc);

        spectrogramRend.setDataSet(DataSetAdapter.createLegacyDataSet(fillDs));
        overSpectrogramRend.setDataSet(DataSetAdapter.createLegacyDataSet(fillDs));

    }

    private void updateFillSeries(WritableDataSet fillDs, boolean autoRange, Map props) {

        if (seriesRend.getDataSet() != null && seriesRend.getDataSet().getXLength() > 30000) {
            // hide slow intermediate states
            seriesRend.setActive(false);
        }

        QDataSet xds = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
        if (xds == null) {
            xds = DataSetUtil.indexGenDataSet(fillDs.length());
        }

        double cadence = DataSetUtil.guessCadence(xds, fillDs);
        ((MutablePropertyDataSet) xds).putProperty(QDataSet.CADENCE, cadence);

        if (autoranging && autoRange && !autoRangeSuppress) {

            boolean isSeries;
            QDataSet depend0 = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
            isSeries =
                    (depend0 == null || DataSetUtil.isMonotonic(depend0));
            if (isSeries) {
                seriesRend.setPsymConnector(PsymConnector.SOLID);
            } else {
                seriesRend.setPsymConnector(PsymConnector.NONE);
            }

            overSeriesRend.setPsymConnector(seriesRend.getPsymConnector());

            seriesRend.setLineWidth(1.0f);

        }

        AutoplotUtil.AutoRangeDescriptor desc = AutoplotUtil.autoRange(fillDs, props);
        AutoplotUtil.AutoRangeDescriptor xdesc = AutoplotUtil.autoRange(xds, (Map) props.get(QDataSet.DEPEND_0));

        if (autoranging && autoRange && !autoRangeSuppress) {
            plot.getYAxis().setLog(desc.log);
            plot.getYAxis().resetRange(desc.range);

            plot.getXAxis().setLog(xdesc.log);
            plot.getXAxis().resetRange(xdesc.range);

            if (fillDs.length() > 30000) {
                seriesRend.setPsymConnector(PsymConnector.NONE);
                seriesRend.setSymSize(1.0);
            } else {
                seriesRend.setPsym(DefaultPlotSymbol.CIRCLES);
                seriesRend.setFillStyle(FillStyle.STYLE_FILL);
                if (fillDs.length() > SYMSIZE_DATAPOINT_COUNT) {
                    seriesRend.setSymSize(1.0);
                    overSeriesRend.setSymSize(1.0);
                } else {
                    seriesRend.setSymSize(3.0);
                    overSeriesRend.setSymSize(2.0);
                }

            }

        }

        overviewPlot.getYAxis().resetRange(desc.range);
        overviewPlot.getXAxis().resetRange(xdesc.range);
        overviewPlot.getXAxis().setLog(xdesc.log);

        colorbar.setVisible(fillDs.property(QDataSet.PLANE_0) != null);

        try {
            seriesRend.setDataSet(DataSetAdapter.createLegacyDataSet(fillDs));
            overSeriesRend.setDataSet(DataSetAdapter.createLegacyDataSet(fillDs));
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
        }

    }

    private void startUpdateUnitsThread() {
        Runnable run = new Runnable() {

            public void run() {
                updateFill(true, false);
                clearThreadRunning(UPDATE_FILL_THREAD_RUNNING);
            }
        };
        markThreadRunning(UPDATE_FILL_THREAD_RUNNING);
        new Thread(run, "updateFillThread").start();
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

    private String describe(String defaultName, QDataSet dep, int index) {
        String name = defaultName;

        String name1 = (String) dep.property(QDataSet.NAME);
        if (name1 != null) name = name1;

        Units u = (Units) dep.property(QDataSet.UNITS);
        if (u == null) u = Units.dimensionless;
        Datum value = u.createDatum(dep.value(index));

        if (UnitsUtil.isTimeLocation(u)) {
            return "" + value;
        } else {
            return name + "=" + value;
        }

    }

    /**
     * the fill parameters have changed, so update the auto range stats.
     * This should not be run on the AWT event thread!
     * @param autorange if false, then no autoranging is done.
     */
    protected void updateFill(boolean autorange, boolean interpretMetadata) {
        logger.fine("enter updateFill");

        if (dataset == null) {
            return;
        }

        Map properties = new HashMap(); // QDataSet properties.

        properties = AutoplotUtil.extractProperties(dataset);
        if (dataSource != null) {
            properties = AutoplotUtil.mergeProperties(dataSource.getProperties(), properties);
        }

        WritableDataSet fillDs;

        String reduceRankString = null;

        if (dataset.rank() == 3) {

            QDataSet ds;
            QDataSet dep;
            if (this.sliceDimension == 2) {
                int index = Math.min(dataset.length(0, 0) - 1, sliceIndex);
                ds = DataSetOps.slice2(dataset, index);
                dep = (QDataSet) dataset.property(QDataSet.DEPEND_2);
            } else if (this.sliceDimension == 1) {
                int index = Math.min(dataset.length(0) - 1, sliceIndex);
                ds = DataSetOps.slice1(dataset, index);
                dep = (QDataSet) dataset.property(QDataSet.DEPEND_1);
            } else if (this.sliceDimension == 0) {
                int index = Math.min(dataset.length() - 1, sliceIndex);
                ds = DataSetOps.slice0(dataset, index);
                dep = (QDataSet) dataset.property(QDataSet.DEPEND_0);
            } else {
                throw new IllegalStateException("sliceDimension");
            }

            List<String> names = getDepnames();
            if (dep == null) {
                reduceRankString = names.get(sliceDimension) + "=" + sliceIndex;
            } else {
                reduceRankString = describe(names.get(sliceDimension), dep, sliceIndex);
            }

            properties = sliceProperties(properties, this.sliceDimension);

            if (transpose) {
                ds = new TransposeRank2DataSet(ds);
                properties = transposeProperties(properties);
            }

            fillDs = DDataSet.copy(ds);

        } else {
            fillDs = DDataSet.copy(dataset);

        }

        RenderType renderType = getRenderType(fillDs);

        ApplicationState newState = this.createState(false);

        /* begin interpret metadata */
        if (interpretMetadata && !autoRangeSuppress && autorange) {

            if (autolabelling) {
                newState.setXLabel("");
                newState.setYLabel("");
                newState.setZLabel("");
                newState.setTitle("");
            }

            newState.setValidRange("");
            newState.setFill("");

            doInterpretMetadata(newState, properties, renderType);

            if (reduceRankString != null) {
                String title = newState.getTitle();
                title += "!c" + reduceRankString;
                newState.setTitle(title);
            }

            unitsCheck(properties, fillDs); // DANGER--this may cause overplotting problems in the future by removing units

            this.autoRangeSuppress = true; // prevent setValidRange and setFill from reranging.
            this.restoreState(newState, false, false);
            this.autoRangeSuppress = false;

        } else {
            // kludge to support updating slice location report without autoranging.
            if (reduceRankString != null) {
                newState.setTitle("");
                doInterpretMetadata(newState, properties, renderType);
                plot.setTitle(newState.getTitle() + "!c" + reduceRankString);
            }
        }

        /*  begin fill dataset  */
        double vmin = Double.NEGATIVE_INFINITY, vmax = Double.POSITIVE_INFINITY, fill = Double.NaN;

        try {
            double[] vminMaxFill = parseFillValidRangeInternal(this.validRange, this.sfill);
            vmin = vminMaxFill[0];
            vmax = vminMaxFill[1];
            fill = vminMaxFill[2];
        } catch (ParseException ex) {
            ex.printStackTrace();
        }
        // check the dataset for fill data, inserting canonical fill values.
        AutoplotUtil.applyFillValidRange(fillDs, vmin, vmax, fill);

        setRenderType(renderType, autorange, interpretMetadata, fillDs, properties);

        if (autorange) {
            if (plot.getXAxis().getUnits().isConvertableTo(Units.us2000)) {
                plot.getXAxis().setUserDatumFormatter(new DateTimeDatumFormatter());
            } else {
                plot.getXAxis().setUserDatumFormatter(null);
            }
            if (plot.getYAxis().getUnits().isConvertableTo(Units.us2000)) {
                plot.getYAxis().setUserDatumFormatter(new DateTimeDatumFormatter());
            } else {
                plot.getYAxis().setUserDatumFormatter(null);
            }
        }

        fillDataset = fillDs;

        propertyChangeSupport.firePropertyChange(PROPERTY_FILL, null, null);
    }

    private void doInterpretMetadata(ApplicationState state, Map properties, RenderType spec) {

        Object v;
        if (autolabelling && (v = properties.get(DDataSet.TITLE)) != null) {
            state.setTitle((String) v);
        }

        if ((v = properties.get(DDataSet.FILL_VALUE)) != null) {
            state.setFill(String.valueOf(v));
        }

        Double vmin = (Double) properties.get(DDataSet.VALID_MIN);
        Double vmax = (Double) properties.get(DDataSet.VALID_MAX);
        if (vmin != null || vmax != null) {
            if (vmin == null) {
                vmin = -1e38;
            }
            if (vmax == null) {
                vmax = 1e38;
            }
            state.setValidRange("" + vmin + " to " + vmax);
        }

        if (spec == RenderType.spectrogram) {
            if (autoranging && (v = properties.get(DDataSet.SCALE_TYPE)) != null) {
                state.setZlog(v.equals("log"));
            }

            if (autolabelling && (v = properties.get(DDataSet.LABEL)) != null) {
                state.setZLabel((String) v);
            }

            if (autolabelling && (v = properties.get(DDataSet.DEPEND_1)) != null) {
                Map m = (Map) v;
                Object v2 = m.get(DDataSet.LABEL);
                if (v2 != null) {
                    state.setYLabel((String) v2);
                }

            }
        } else {
            if (autoranging && (v = properties.get(DDataSet.SCALE_TYPE)) != null) {
                state.setYlog(v.equals("log"));
            }

            if (autolabelling && (v = properties.get(DDataSet.LABEL)) != null) {
                state.setYLabel((String) v);
            }

            state.setZLabel("");
        }

        if ((v = properties.get(DDataSet.DEPEND_0)) != null) {
            Map m = (Map) v;
            Object v2 = m.get(DDataSet.LABEL);
            if (autolabelling && v2 != null) {
                state.setXLabel((String) v2);
            }

        }

    }

    /**
     * do update on this thread, ensuring that only one data load is occuring at a
     * time.  Note if a dataSource doesn't check mon.isCancelled(), then processing
     * will block until the old load is done.
     * @param autorange
     * @param interpretMeta
     */
    private synchronized void updateImmediately(final boolean autorange, boolean interpretMeta) {
        /*** here is the data load ***/
        setStatus("loading dataset");

        if (dataSource != null) {

            QDataSet dataset = loadDataSet(0);
            setStatus("done loading dataset");
            setDataSetInternal(dataset, autorange);
        } else {
            setDataSetInternal(null, autorange);
        }

        if (tsb != null) {
            //ApplicationModel.this.setDataSourceURL( tsb.getURL().toString() );
            String oldsurl = ApplicationModel.this.surl;
            ApplicationModel.this.surl = tsb.getURL().toString();
            if (oldsurl != null) {
                String eext = DataSetURL.getExplicitExt(oldsurl);
                if (eext != null) ApplicationModel.this.surl = eext + "." + ApplicationModel.this.surl;
            }
            ApplicationModel.this.propertyChangeSupport.firePropertyChange(PROPERTY_DATASOURCE, oldsurl, ApplicationModel.this.surl);
        }

        setStatus("ready");

    }

    /**
     * update the model and view using the new DataSource to create a new dataset,
     * then inspecting the dataset to decide on axis settings.
     * @param autorange if false, then no autoranging is done, just the fill part.
     */
    public synchronized void update(final boolean autorange, final boolean interpretMeta) {
        dataset = null;

        Runnable run = new Runnable() {

            public void run() {
                updateImmediately(autorange, interpretMeta);

            }
        };

        if (dataSource != null && dataSource.asynchronousLoad() && !headless) {
            logger.info("invoke later do load");
            if (mon != null) {
                System.err.println("double load!");
                if (mon != null) mon.cancel();
            }
            RequestProcessor.invokeLater(run);
        } else {
            run.run();
        }
    }

    /**
     * load the data set from the DataSource.
     */
    public QDataSet loadDataSet(int i) {
        ProgressMonitor mymon;
        if (i != 0) {
            throw new IllegalArgumentException("only one dataset supported");
        }
        if (dataset == null) {
            mymon = application.getMonitorFactory().getMonitor(plot, "loading data", "loading " + dataSource);
            this.mon = mymon;
            try {
                dataset = dataSource.getDataSet(mymon);
                embedDsDirty = true;
            } catch (InterruptedIOException ex) {
                seriesRend.setException(ex);
                seriesRend.setDataSet(null);
                spectrogramRend.setException(ex);
                spectrogramRend.setDataSet(null);
            } catch (CancelledOperationException ex) {
                seriesRend.setException(ex);
                seriesRend.setDataSet(null);
                spectrogramRend.setException(ex);
                spectrogramRend.setDataSet(null);
            } catch (Exception e) {
                application.getExceptionHandler().handle(e);
            } finally {
                // don't trust the data sources to call finished when an exception occurs.
                mymon.finished();
                if (mymon == this.mon) {
                    this.mon = null;
                } else {
                    System.err.println("not my mon, somebody better delete it!");
                }
            }
        }
        return dataset;
    }

    public synchronized void addPropertyChangeListener(java.beans.PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public synchronized void removePropertyChangeListener(java.beans.PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    /**
     * Create a dataSource object and set autoplot to display this datasource.
     * A dataSource object is created by DataSetURL.getDataSource, which looks
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
        this.surl = surl;
        if (surl == null) {
            return;
        }  // not really supported

        surl = DataSetURL.maybeAddFile(surl);

        try {
            if (surl.endsWith(".vap")) {
                try {
                    URL url = new URL(surl);
                    mon.setProgressMessage("loading vap file");
                    File openable = DataSetURL.getFile(url, application.getMonitorFactory().getMonitor(plot, "loading vap", ""));
                    doOpen(openable);
                    mon.setProgressMessage("done loading vap file");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(canvas, "<html>Unable to open resource: <br>" + surl);
                }
            } else {
                mon.setProgressMessage("getting data source " + surl);

                if (caching != null) {
                    if (caching.satisfies(surl)) {
                        caching.resetURL(surl);
                        update(true, true);
                        return;
                    }
                }

                DataSource source = DataSetURL.getDataSource(surl);
                setDataSource(source);

                mon.setProgressMessage("done getting data source");
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * @throws RuntimeException when getDataSource throws Exception
     */
    public void setDataSourceURL(String surl) {
        String oldVal = this.surl;
        if (surl == null && this.surl == null) {
            return;
        }

        if (surl != null && surl.equals(this.surl)) {
            return;
        }

        propertyChangeSupport.firePropertyChange(PROPERTY_DATASOURCE_URL, oldVal, surl);
        resetDataSetSourceURL(surl, new NullProgressMonitor());
    }

    public String getDataSourceURL() {
        return surl;
    }

    /**
     * @return double[3], vmin, vmax, fill.
     */
    private double[] parseFillValidRangeInternal(String validRange, String sfill) throws ParseException {
        double[] result = new double[3];
        if (!"".equals(validRange.trim())) {
            DatumRange vrange = DatumRangeUtil.parseDatumRange(validRange, Units.dimensionless);
            result[0] = vrange.min().doubleValue(Units.dimensionless);
            result[1] = vrange.max().doubleValue(Units.dimensionless);
        } else {
            result[0] = -1 * Double.MAX_VALUE;
            result[1] = Double.MAX_VALUE;
        }

        if (!"".equals(sfill.trim())) {
            result[2] = Double.parseDouble(sfill);
        } else {
            result[2] = Double.NaN;
        }

        return result;
    }

    /** calculate units object that implements validRange and sfill
     */
    public void parseFillValidRange(String validRange, String sfill) throws ParseException {
        double[] validFill = parseFillValidRangeInternal(validRange, sfill);

        if (dataSource != null) {
            if (!this.autoRangeSuppress) {
                markThreadRunning(TICKLE_TIMER_THREAD_RUNNING);
                tickleTimer.restart();
            }

        }

        propertyChangeSupport.firePropertyChange(PROPERTY_FILL, null, null);
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
                    recent = Bookmark.parseBookmarks(AutoplotUtil.readDoc(url.openStream()));
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
                recent = Bookmark.parseBookmarks(AutoplotUtil.readDoc(new ByteArrayInputStream(srecent.getBytes())));
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
                    bookmarks = Bookmark.parseBookmarks(AutoplotUtil.readDoc(url.openStream()));
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
                bookmarks = Bookmark.parseBookmarks(AutoplotUtil.readDoc(new ByteArrayInputStream(sbookmark.getBytes())));
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
        URLSplit split= URLSplit.parse(surl);
        String autoTitle= split.file.substring( split.path.length() ) ;
        if ( autoTitle.length()==0 ) autoTitle= surl;
        item.setTitle( autoTitle );
        
        List<Bookmark> oldValue = Collections.unmodifiableList(new ArrayList<Bookmark>());
        List<Bookmark> newValue = new ArrayList<Bookmark>(bookmarks);
        if (newValue.contains(surl)) { // move it to the front of the list
            newValue.remove(surl);
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

    void setSpecialEffects(boolean b) {
        plot.setPreviewEnabled(true);
        plot.getXAxis().setAnimated(b);
        plot.getYAxis().setAnimated(b);
        colorbar.setAnimated(b);
    }

    void setDrawAntiAlias(boolean b) {
        this.getCanvas().setAntiAlias(b);
        this.seriesRend.setAntiAliased(b);
    }

    void resetZoom() {
        updateFill(true, false);
    }

    void increaseFontSize() {
        Font f = this.canvas.getBaseFont();
        f = f.deriveFont(f.getSize2D() * 1.1f);
        this.canvas.setBaseFont(f);
        this.options.setCanvasFont(Options.getFontLabel(f));

    }

    void decreaseFontSize() {
        Font f = this.canvas.getBaseFont();
        f = f.deriveFont(f.getSize2D() / 1.1f);
        this.canvas.setBaseFont(f);
        this.options.setCanvasFont(Options.getFontLabel(f));
    }

    DasAxis getXAxis() {
        return plot.getXAxis();
    }

    /**
     * creates an ApplicationState object representing the current state.
     * @param deep if true, do a deeper, more expensive gathering of state.  In the initial implementation, this calculates the embededded dataset.
     * @return ApplicationState object
     */
    public ApplicationState createState(boolean deep) {

        ApplicationState state = new ApplicationState();

        state.setSurl(surl);

        state.setXrange(plot.getXAxis().getDatumRange());
        state.setXlog(plot.getXAxis().isLog());

        state.setYrange(plot.getYAxis().getDatumRange());
        state.setYlog(plot.getYAxis().isLog());

        state.setZrange(colorbar.getDatumRange());
        state.setZlog(colorbar.isLog());

        state.setColortable(colorbar.getType().toString());

        state.setLineWidth(seriesRend.getLineWidth());
        state.setSymbolSize(seriesRend.getSymSize());

        state.setValidRange(validRange);
        state.setFill(sfill);

        state.getOptions().setBackground(canvas.getBackground());
        state.getOptions().setForeground(canvas.getForeground());
        state.getOptions().setColor(seriesRend.getColor());
        state.getOptions().setFillColor(seriesRend.getFillColor());
        state.getOptions().setCanvasFont(Options.getFontLabel(canvas.getFont()));

        state.setFillToReference(seriesRend.isFillToReference());
        state.setReference(formatObject(seriesRend.getReference()));

        state.setPlotSymbol(formatObject(seriesRend.getPsym()));
        state.setSymbolConnector(formatObject(seriesRend.getPsymConnector()));

        state.setShowContextOverview(isShowContextOverview());
        state.setAutoOverview(isAutoOverview());

        state.setAutoranging(isAutoranging());
        state.setAutolabelling(isAutolabelling());
        state.setAutolayout(isAutolayout());
        state.setIsotropic(isIsotropic());

        state.setColumn(AutoplotUtil.formatDevicePosition(plot.getColumn()));
        state.setRow1(AutoplotUtil.formatDevicePosition(plot.getRow()));
        state.setRow2(AutoplotUtil.formatDevicePosition(overviewPlot.getRow()));

        state.setUseEmbeddedDataSet(isUseEmbeddedDataSet());

        if (deep && isUseEmbeddedDataSet()) {
            state.setEmbeddedDataSet(getEmbeddedDataSet());
        }

        state.setTitle(plot.getTitle());
        state.setYLabel(plot.getYAxis().getLabel());
        state.setXLabel(plot.getXAxis().getLabel());
        state.setZLabel(colorbar.getLabel());

        state.setCanvasSize(this.canvas.getSize());
        state.setCanvasFitted(this.canvas.isFitted());

        return state;
    }

    /**
     * set the application state.
     * @param state
     * @param deep if true, then unpack the dataset as well.
     * @param forceFill, force a data load
     */
    public void restoreState(ApplicationState state, boolean deep, boolean forceFill) {

        autoRangeSuppress = true;

        try {
            if (state.getSurl() != null) {
                if (forceFill) {
                    this.surl = ""; // TODO: why
                }

                setDataSourceURL(state.getSurl());
            }

        } catch (Exception e) {
            autoRangeSuppress = false; // it's le'

        }

        if (state.getXrange() != null) {
            plot.getXAxis().resetRange(state.getXrange());
        }

        plot.getXAxis().setLog(state.isXlog());

        if (state.getYrange() != null) {
            plot.getYAxis().resetRange(state.getYrange());
        }

        plot.getYAxis().setLog(state.isYlog());

        if (state.getZrange() != null) {
            colorbar.resetRange(state.getZrange());
        }

        colorbar.setLog(state.isZlog());

        if (state.getColortable() != null) {
            colorbar.setType(DasColorBar.Type.parse(state.getColortable()));
        }

        seriesRend.setLineWidth(state.getLineWidth());
        seriesRend.setSymSize(state.getSymbolSize());

        if (state.getValidRange() != null) {
            setValidRange(state.getValidRange());
        }

        setFill(state.getFill());

        if (state.getOptions().getBackground() != null) {
            canvas.setBackground(state.getOptions().getBackground());
        }

        if (state.getOptions().getForeground() != null) {
            canvas.setForeground(state.getOptions().getForeground());
        }

        if (state.getOptions().getColor() != null) {
            seriesRend.setColor(state.getOptions().getColor());
        }

        if (state.getOptions().getFillColor() != null) {
            seriesRend.setFillColor(state.getOptions().getFillColor());
        }

        if (!state.getOptions().getCanvasFont().equals("")) {
            canvas.setFont(Font.decode(state.getOptions().getCanvasFont()));
        }

        seriesRend.setFillToReference(state.isFillToReference());
        if (state.getReference() != null) {
            seriesRend.setReference((Datum) parseObject(seriesRend.getReference(), state.getReference()));
        }

        if (state.getPlotSymbol() != null) {
            seriesRend.setPsym((PlotSymbol) parseObject(seriesRend.getPsym(), state.getPlotSymbol()));
        }

        if (state.getSymbolConnector() != null) {
            seriesRend.setPsymConnector((PsymConnector) parseObject(seriesRend.getPsymConnector(), state.getSymbolConnector()));
        }

        setShowContextOverview(state.isShowContextOverview());
        setAutoOverview(state.isAutoOverview());
        setAutoranging(state.isAutoranging());
        setAutolabelling(state.isAutolabelling());
        setAutolayout(state.isAutolayout());
        setIsotropic(state.isIsotropic());

        try {
            if (state.getColumn() != null) AutoplotUtil.setDevicePosition(plot.getColumn(), state.getColumn());
            if (state.getRow1() != null) AutoplotUtil.setDevicePosition(plot.getRow(), state.getRow1());
            if (state.getRow2() != null) AutoplotUtil.setDevicePosition(overviewPlot.getRow(), state.getRow2());
        } catch (ParseException ex) {
            throw new RuntimeException(ex);
        }

        setUseEmbeddedDataSet(state.isUseEmbeddedDataSet());

        if (deep && state.isUseEmbeddedDataSet() && !"".equals(state.getEmbeddedDataSet())) {
            setEmbeddedDataSet(state.getEmbeddedDataSet());
        }

        plot.setTitle(state.getTitle());

        plot.getXAxis().setLabel(state.getXLabel());
        plot.getYAxis().setLabel(state.getYLabel());
        colorbar.setLabel(state.getZLabel());

        canvas.setFitted(state.isCanvasFitted());
        canvas.setSize(state.getCanvasSize());
    }

    private Object parseObject(Object context, String s) {
        PropertyEditor edit = BeansUtil.findEditor(context.getClass());
        if (edit == null) {
            return context;
        }

        edit.setValue(context);
        edit.setAsText(s);
        Object result = edit.getValue();
        return result;
    }

    private static String formatObject(Object obj) {
        PropertyEditor edit = BeansUtil.findEditor(obj.getClass());
        if (edit == null) {
            return "";
        }

        edit.setValue(obj);
        String result = edit.getAsText();
        return result;
    }

    void doSave(File f) throws IOException {
        StatePersistence.saveState(f, createState(true));
        setUseEmbeddedDataSet(false);
    }

    void doOpen(File f) throws IOException {
        ApplicationState state = (ApplicationState) StatePersistence.restoreState(f);
        restoreState(state, true, true);
        setUseEmbeddedDataSet(false);
        allowAutoContext = true;
        propertyChangeSupport.firePropertyChange("file", null, f);
    }
    /**
     * Holds value of property validRange.
     */
    private String validRange = "";

    /**
     * Getter for property validRange.
     * @return Value of property validRange.
     */
    public String getValidRange() {
        return this.validRange;
    }

    /**
     * Setter for property validRange.
     * @param validRange New value of property validRange.
     */
    public void setValidRange(String validRange) {
        if (validRange.equals(this.validRange)) {
            return;
        }

        String oldValue = this.validRange;
        this.validRange = validRange;
        try {
            parseFillValidRange(validRange, sfill);
        } catch (ParseException ex) {
            ex.printStackTrace();
        }

        propertyChangeSupport.firePropertyChange("validRange", oldValue, validRange);
    }
    private String sfill = "";

    /**
     * Getter for property fill.
     * @return Value of property fill.
     */
    public String getFill() {
        return this.sfill;
    }

    /**
     * Setter for property fill.
     * @param fill New value of property fill.
     */
    public void setFill(String fill) {
        if (fill.equals(this.sfill)) {
            return;
        }

        this.sfill = fill;
        try {
            parseFillValidRange(validRange, fill);
        } catch (ParseException ex) {
            ex.printStackTrace();
        }

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
            if (dataset == null) {
                embedDs = "";
                return;
            }

            org.das2.dataset.DataSet ds;

            ByteArrayOutputStream out = new ByteArrayOutputStream(10000);
            //DeflaterOutputStream dos= new DeflaterOutputStream(out);
            OutputStream dos = out;

            SimpleStreamFormatter format = new SimpleStreamFormatter();
            format.format(dataset, dos, false);

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
            setDataSetInternal(handler.getDataSet(), false);

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
     * Holds value of property showContextOverview.
     */
    private boolean showContextOverview;
    /**
     * Utility field used by bound properties.
     */
    private java.beans.PropertyChangeSupport propertyChangeSupport = new java.beans.PropertyChangeSupport(this);

    public boolean isShowContextOverview() {
        return this.showContextOverview;
    }
    private static final double OVERVIEW_PERCENT = 0.60;

    public void setShowContextOverview(boolean showContextOverview) {
        boolean oldShowContextOverview = this.showContextOverview;
        this.showContextOverview = showContextOverview;
        DasRow row = plot.getRow();
        row.setMaximum(showContextOverview ? OVERVIEW_PERCENT : 1.00);

        if (showContextOverview && !overviewBindingsDone) {
            doOverviewBindings();
        }

        overviewPlot.setVisible(showContextOverview);
        overviewPlotConnector.setVisible(showContextOverview);
        allowAutoContext = false;
        canvas.repaint();
        propertyChangeSupport.firePropertyChange("showContextOverview", new Boolean(oldShowContextOverview), new Boolean(showContextOverview));

    }
    private boolean autoOverview = false;
    public static final String PROP_AUTOOVERVIEW = "autoOverview";

    public boolean isAutoOverview() {
        return this.autoOverview;
    }

    public void setAutoOverview(boolean newautoOverview) {
        boolean oldautoOverview = autoOverview;
        this.autoOverview = newautoOverview;
        propertyChangeSupport.firePropertyChange(PROP_AUTOOVERVIEW, oldautoOverview, newautoOverview);
    }
    private boolean autoranging = true;
    public static final String PROP_AUTORANGING = "autoranging";

    public boolean isAutoranging() {
        return this.autoranging;
    }

    public void setAutoranging(boolean newautoranging) {
        boolean oldautoranging = autoranging;
        this.autoranging = newautoranging;
        propertyChangeSupport.firePropertyChange(PROP_AUTORANGING, oldautoranging, newautoranging);
    }
    protected boolean autolabelling = true;
    public static final String PROP_AUTOLABELLING = "autolabelling";

    public boolean isAutolabelling() {
        return autolabelling;
    }

    public void setAutolabelling(boolean autolabelling) {
        boolean oldAutolabelling = this.autolabelling;
        this.autolabelling = autolabelling;
        propertyChangeSupport.firePropertyChange(PROP_AUTOLABELLING, oldAutolabelling, autolabelling);
    }
    protected boolean autolayout = true;

    public boolean isAutolayout() {
        return autolayout;
    }

    public void setAutolayout(boolean autolayout) {
        this.autolayout = autolayout;
        if (autolayout) {
            LayoutUtil.autolayout(this.canvas, this.plot.getRow(), this.plot.getColumn());
        }
    }

    void maybeSetInitialURL(String surl) {
        if (this.surl == null) {
            this.surl = surl;
        }

    }
    private boolean overviewBindingsDone = false;

    private synchronized void doOverviewBindings() {
        overviewBindingsDone = true;
        BindingContext bc = new BindingContext();
        bc.addBinding(seriesRend, "${color}", overSeriesRend, "color");
        bc.addBinding(seriesRend, "${active}", overSeriesRend, "active");
        bc.addBinding(spectrogramRend, "${active}", overSpectrogramRend, "active");
        bc.addBinding(spectrogramRend, "${rebinner}", overSpectrogramRend, "rebinner");
        bc.addBinding(plot.getYAxis(), "${log}", overviewPlot.getYAxis(), "log");
        bc.bind();

    }
    /**
     * Holds value of property status.
     */
    private String status;

    /**
     * Getter for property status.
     * @return Value of property status.
     */
    public String getStatus() {
        return this.status;
    }

    private void setStatus(String status) {
        logger.info(status);
        String oldVal = this.status;
        this.status = status;
        propertyChangeSupport.firePropertyChange(PROPERTY_STATUS, oldVal, status);
    }
    /**
     * Holds value of property isotropic.
     */
    private boolean isotropic;

    /**
     * Getter for property isotropic.
     * @return Value of property isotropic.
     */
    public boolean isIsotropic() {
        return this.isotropic;
    }

    /**
     * Setter for property isotropic.
     * @param isotropic New value of property isotropic.
     */
    public void setIsotropic(boolean isotropic) {
        boolean oldIsotropic = this.isotropic;
        this.isotropic = isotropic;
        if (isotropic) {
            checkIsotropic(plot.getXAxis());
        }

        propertyChangeSupport.firePropertyChange("isotropic", new Boolean(oldIsotropic), new Boolean(isotropic));
    }
    private int sliceDimension = 2;
    public static final String PROP_SLICEDIMENSION = "sliceDimension";

    public int getSliceDimension() {
        return this.sliceDimension;
    }

    public void setSliceDimension(int newsliceDimension) {
        if (newsliceDimension < 0 || newsliceDimension > 2) {
            return;
        }
        int oldsliceDimension = sliceDimension;
        this.sliceIndex = 0;
        this.sliceDimension = newsliceDimension;
        updateFill(true, true);
        propertyChangeSupport.firePropertyChange(PROP_SLICEDIMENSION, oldsliceDimension, newsliceDimension);
    }
    private int sliceIndex = 1;
    public static final String PROP_SLICEINDEX = "sliceIndex";

    /**
     * Get the value of sliceIndex
     *
     * @return the value of sliceIndex
     */
    public int getSliceIndex() {
        return this.sliceIndex;
    }

    /**
     * Set the value of sliceIndex
     *
     * @param newsliceIndex new value of sliceIndex
     */
    public void setSliceIndex(int newsliceIndex) {
        int oldsliceIndex = sliceIndex;
        this.sliceIndex = newsliceIndex;
        updateFill(false, false);
        propertyChangeSupport.firePropertyChange(PROP_SLICEINDEX, oldsliceIndex, newsliceIndex);
    }

    /**
     * return the maximum allowed slice index, plus one.
     * @return
     */
    public int getMaxSliceIndex(int sliceDimension) {
        if (dataset == null) return 0;
        if (sliceDimension == 0) return dataset.length();
        int[] qube = DataSetUtil.qubeDims(dataset);
        if (qube == null || qube.length <= sliceDimension) {
            return 0;
        } else {
            return qube[sliceDimension];
        }
    }
    private boolean transpose = false;
    public static final String PROP_TRANSPOSE = "transpose";

    public void setTranspose(boolean val) {
        boolean oldVal = this.transpose;
        this.transpose = val;
        updateFill(true, true);
        propertyChangeSupport.firePropertyChange(PROP_TRANSPOSE, oldVal, val);
    }

    public boolean isTranspose() {
        return this.transpose;
    }
    private List<String> depnames = Arrays.asList(new String[]{"first", "second", "last"});
    public static final String PROP_DEPNAMES = "depnames";

    public List<String> getDepnames() {
        return this.depnames;
    }

    public void setDepnames(List<String> newdepnames) {
        List<String> olddepnames = depnames;
        this.depnames = newdepnames;
        propertyChangeSupport.firePropertyChange(PROP_DEPNAMES, olddepnames, newdepnames);
    }

    public DasPlot getPlot() {
        return this.plot;
    }

    public DasPlot getOverviewPlot() {
        return this.overviewPlot;
    }

    public DasColorBar getColorBar() {
        return this.colorbar;
    }

    /**
     * wait for autoplot to settle.
     */
    public void waitUntilIdle(boolean runtimeException) throws InterruptedException {
        while (threadRunning != 0) {
            Thread.sleep(30);
        }
        canvas.waitUntilIdle();
    }
}

