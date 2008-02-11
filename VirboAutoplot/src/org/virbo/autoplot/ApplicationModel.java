/*
 * ApplicationModel.java
 *
 * Created on April 1, 2007, 8:21 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import edu.uiowa.physics.pw.das.DasApplication;
import edu.uiowa.physics.pw.das.beans.BeansUtil;
import edu.uiowa.physics.pw.das.client.DataSetStreamHandler;
import edu.uiowa.physics.pw.das.dataset.CacheTag;
import edu.uiowa.physics.pw.das.dataset.DataSetStreamProducer;
import edu.uiowa.physics.pw.das.datum.Datum;
import edu.uiowa.physics.pw.das.datum.DatumRange;
import edu.uiowa.physics.pw.das.datum.DatumRangeUtil;
import edu.uiowa.physics.pw.das.datum.EnumerationUnits;
import edu.uiowa.physics.pw.das.datum.Units;
import edu.uiowa.physics.pw.das.datum.UnitsUtil;
import edu.uiowa.physics.pw.das.event.BoxRenderer;
import edu.uiowa.physics.pw.das.event.BoxSelectionEvent;
import edu.uiowa.physics.pw.das.event.BoxSelectionListener;
import edu.uiowa.physics.pw.das.event.BoxSelectorMouseModule;
import edu.uiowa.physics.pw.das.event.BoxZoomMouseModule;
import edu.uiowa.physics.pw.das.event.MouseModule;
import edu.uiowa.physics.pw.das.event.PointSlopeDragRenderer;
import edu.uiowa.physics.pw.das.event.ZoomPanMouseModule;
import edu.uiowa.physics.pw.das.graph.ColumnColumnConnector;
import edu.uiowa.physics.pw.das.graph.DasAxis;
import edu.uiowa.physics.pw.das.graph.DasCanvas;
import edu.uiowa.physics.pw.das.graph.DasColorBar;
import edu.uiowa.physics.pw.das.graph.DasColumn;
import edu.uiowa.physics.pw.das.graph.DasPlot;
import edu.uiowa.physics.pw.das.graph.DasRow;
import edu.uiowa.physics.pw.das.graph.DefaultPlotSymbol;
import edu.uiowa.physics.pw.das.graph.FillStyle;
import edu.uiowa.physics.pw.das.graph.PlotSymbol;
import edu.uiowa.physics.pw.das.graph.PsymConnector;
import edu.uiowa.physics.pw.das.graph.Renderer;
import edu.uiowa.physics.pw.das.graph.SeriesRenderer;
import edu.uiowa.physics.pw.das.graph.SpectrogramRenderer;
import edu.uiowa.physics.pw.das.stream.StreamException;
import edu.uiowa.physics.pw.das.system.RequestProcessor;
import edu.uiowa.physics.pw.das.util.DasProgressMonitor;
import edu.uiowa.physics.pw.das.util.NullProgressMonitor;
import edu.uiowa.physics.pw.das.util.StreamTool;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyEditor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.beans.binding.BindingContext;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import org.apache.xerces.utils.Base64;
import org.virbo.autoplot.state.ApplicationState;
import org.virbo.autoplot.state.StatePersistence;
import org.virbo.autoplot.util.TickleTimer;
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
import org.virbo.datasource.DataSourceRegistry;
import org.virbo.datasource.capability.TimeSeriesBrowse;
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
    TickleTimer tickleTimer;
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
    double vmin;
    double vmax;
    double fill;
    static final Logger logger = Logger.getLogger("virbo.autoplot");
    /**
     * true if running in headless environment
     */
    boolean headless;
    /**
     * data source has been requested
     */
    public static String PROPERTY_DATASOURCE = "dataSource";
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
                if (allowAutoContext && originalXRange != null && originalXRange.getUnits().isConvertableTo(plot.getXAxis().getUnits()) && originalXRange.contains(plot.getXAxis().getDatumRange())) {
                    setShowContextOverview(true);
                }
            }
            firePropertyChangeListenerPropertyChange(e);
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

        headless = "true".equals(System.getProperty("java.awt.headless"));

        canvas = new DasCanvas();
        canvas.setFont(canvas.getFont().deriveFont(Font.ITALIC, 18.f));
        canvas.addPropertyChangeListener(listener);

        this.application = canvas.getApplication();

        createDasPlot();

        tickleTimer = new TickleTimer(100, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals("running") && evt.getOldValue().equals(Boolean.TRUE) && evt.getNewValue().equals(Boolean.FALSE)) {
                    startUpdateUnitsThread();
                }
            }
        });
        try {
            parseFillValidRange("", "");
        } catch (ParseException ex) {
            ex.printStackTrace();
        }
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
        overviewPlot = DasPlot.createPlot(DatumRange.newDatumRange(0, 10, Units.dimensionless),
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

        plot.getMouseAdapter().addMouseModule(new MouseModule(plot, new PointSlopeDragRenderer(plot, plot.getXAxis(), plot.getYAxis()), "slope"));

        plot.getMouseAdapter().addMenuItem(new JMenuItem(new AbstractAction("reset zoom") {

            public void actionPerformed(ActionEvent e) {
                updateFill(true);
            }
        }));

        plot.addPropertyChangeListener(listener);
        plot.getXAxis().addPropertyChangeListener(listener);
        plot.getYAxis().addPropertyChangeListener(listener);

        BoxZoomMouseModule boxmm = (BoxZoomMouseModule) plot.getMouseAdapter().getModuleByLabel("Box Zoom");
        plot.getMouseAdapter().setPrimaryModule(boxmm);

        //plot.getMouseAdapter().addMouseModule( new AnnotatorMouseModule(plot) ) ;

        seriesRend = new SeriesRenderer();
        seriesRend.setDataSetLoader(null);

        overSeriesRend = new SeriesRenderer();
        
        plot.addRenderer(seriesRend);
        overviewPlot.addRenderer(overSeriesRend);
        overviewPlot.setPreviewEnabled(true);

        overviewPlotConnector = new ColumnColumnConnector(canvas, plot,
                DasRow.create(null, plot.getRow(), "0%", "100%+2em"), overviewPlot);
        overviewPlotConnector.setBottomCurtain(true);
        overviewPlotConnector.setCurtainOpacityPercent(80);
        overviewPlotConnector.getMouseAdapter().setPrimaryModule(overviewZoom);
        overviewPlotConnector.getMouseAdapter().setSecondaryModule(new ColumnColumnConnectorMouseModule(plot.getXAxis(), overviewPlot.getXAxis()));

        overviewPlotConnector.setVisible(false);
        overviewPlot.setVisible(false);

        DatumRange colorRange = new DatumRange(0, 100, Units.dimensionless);
        colorbar = new DasColorBar(colorRange.min(), colorRange.max(), false);
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
        seriesRend.setColorByDataSetId("plane0");

        seriesRend.setAntiAliased(true);

        seriesRend.addPropertyChangeListener(listener);

        if (!headless) {
            boxmm.setAutoUpdate(true);
        }

        MouseModule zoomPan = new ZoomPanMouseModule(plot.getXAxis(), plot.getYAxis());
        plot.getMouseAdapter().setSecondaryModule(zoomPan);

        MouseModule zoomPanX = new ZoomPanMouseModule(plot.getXAxis(), null);
        plot.getXAxis().getMouseAdapter().setSecondaryModule(zoomPanX);

        zoomPanX = new ZoomPanMouseModule(overviewPlot.getXAxis(), null);
        overviewPlot.getXAxis().getMouseAdapter().setSecondaryModule(zoomPanX);

        MouseModule zoomPanY = new ZoomPanMouseModule(null, plot.getYAxis());
        plot.getYAxis().getMouseAdapter().setSecondaryModule(zoomPanY);

        MouseModule zoomPanZ = new ZoomPanMouseModule(null, colorbar);
        colorbar.getMouseAdapter().setSecondaryModule(zoomPanZ);

        setSpecialEffects(false);
        canvas.revalidate();

        if (!headless) {
            doOverviewBindings();
        }

    }

    protected void setRenderer(Renderer rend, Renderer overRend) {
        Renderer[] rends = plot.getRenderers();
        for (int i = 0; i < rends.length; i++) {
            rends[i].setActive(rends[i] == rend);
        }
        rends = overviewPlot.getRenderers();
        for (int i = 0; i < rends.length; i++) {
            rends[i].setActive(rends[i] == overRend);
        }
    }

    public DasCanvas getCanvas() {
        return canvas;
    }
    PropertyChangeListener timeSeriesBrowseListener;
    TimeSeriesBrowse tsb = null;

    public void setDataSource(DataSource ds) {
        DataSource oldSource = this.dataSource;
        this.dataSource = ds;

        if ((tsb = ds.getCapability(TimeSeriesBrowse.class)) != null) {
            timeSeriesBrowseListener = new PropertyChangeListener() {

                public void propertyChange(PropertyChangeEvent e) {
                    if (plot.getXAxis().valueIsAdjusting()) {
                        return;
                    }
                    if (e.getPropertyName().equals("datumRange")) {
                        DatumRange newRange = ApplicationModel.this.plot.getXAxis().getDatumRange();
                        if (tsb == null) {
                            return;
                        }
                        if (UnitsUtil.isTimeLocation(newRange.getUnits())) {
                            QDataSet ds = (QDataSet) ApplicationModel.this.dataset;
                            QDataSet dep0 = ds == null ? null : (QDataSet) ds.property(QDataSet.DEPEND_0);
                            CacheTag tag = dep0 == null ? null : (CacheTag) dep0.property(QDataSet.CACHE_TAG);
                            if (tag == null || !tag.getRange().contains(newRange)) {
                                tsb.setTimeRange(newRange);
                                setAutoRangeSuppress(true);
                                update();
                            }
                        }
                    }
                }
            };
            this.plot.getXAxis().addPropertyChangeListener(timeSeriesBrowseListener);
        } else {
            if (timeSeriesBrowseListener != null) {
                this.plot.getXAxis().removePropertyChangeListener(timeSeriesBrowseListener);
                timeSeriesBrowseListener = null;
            }
        }
        if (oldSource == null || !oldSource.equals(ds)) {
            update();
            firePropertyChangeListenerPropertyChange(PROPERTY_DATASOURCE, oldSource, ds);
        }
    }

    public DataSource dataSource() {
        return this.dataSource;
    }

    public List getDataSources() {
        return new ArrayList(Collections.singleton(dataSource));
    }

    /**
     * rewrite the dataset so that fill values are set by the valid range and fill
     * controls.
     */
    private WritableDataSet applyFillValidRange(QDataSet ds) {
        WritableDataSet result = DDataSet.copy(ds);
        Units u = (Units) ds.property(QDataSet.UNITS);

        if (u == null) {
            u = Units.dimensionless;
        }
        if (ds.rank() == 1) {
            for (int i = 0; i < ds.length(); i++) {
                double d = ds.value(i);
                if (d == fill || d <= vmin || d >= vmax) {
                    result.putValue(i, u.getFillDouble());
                }
            }
        } else if (ds.rank() == 2) {
            for (int i0 = 0; i0 < ds.length(); i0++) {
                for (int i1 = 0; i1 < ds.length(i0); i1++) {
                    double d = ds.value(i0, i1);
                    if (d == fill || d <= vmin || d >= vmax) {
                        result.putValue(i0, i1, u.getFillDouble());
                    }
                }
            }
        } else {
            for (int i0 = 0; i0 < ds.length(); i0++) {
                for (int i1 = 0; i1 < ds.length(i0); i1++) {
                    for (int i2 = 0; i2 < ds.length(i0, i1); i2++) {
                        double d = ds.value(i0, i1, i2);
                        if (d == fill || d <= vmin || d >= vmax) {
                            result.putValue(i0, i1, i2, u.getFillDouble());
                        }
                    }
                }
            }
        }

        result.putProperty(QDataSet.UNITS, u);

        return result;
    }

    private double guessCadence(QDataSet xds, QDataSet yds) {
        if ( yds==null ) yds= DataSetUtil.indexGenDataSet(xds.length());
        assert (xds.length() == yds.length());
        Units u = (Units) yds.property(QDataSet.UNITS);
        if (u == null) {
            u = Units.dimensionless;
        }
        double cadence = Double.MAX_VALUE;

        // calculate average cadence for consistent points.  Preload to avoid extra branch.
        double cadenceS = Double.MAX_VALUE;
        int cadenceN = 1;

        int i = 0;
        double x0 = 0;
        while (i < xds.length() && !u.isValid(yds.value(i))) {
            i++;
        }
        if (i < yds.length()) {
            x0 = xds.value(i);
        }
        for (i++; i < xds.length(); i++) {
            if (u.isValid(yds.value(i))) {
                double cadenceAvg;
                cadenceAvg = cadenceS / cadenceN;
                cadence = Math.abs(xds.value(i) - x0);
                if ((xds.value(i) - x0) < 0.5 * cadenceAvg) {
                    cadenceS = cadence;
                    cadenceN = 1;
                } else if ((xds.value(i) - x0) < 1.5 * cadenceAvg) {
                    cadenceS += cadence;
                    cadenceN += 1;
                }
                x0 = xds.value(i);
            }
        }
        return cadenceS / cadenceN;
    }

    private void updateFillSpec(WritableDataSet fillDs,boolean autoRange) {
        QDataSet xds = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
        if (xds == null) {
            xds = DataSetUtil.indexGenDataSet(fillDs.length());
            fillDs.putProperty(QDataSet.DEPEND_0, xds);
        }
        QDataSet yds = (QDataSet) fillDs.property(QDataSet.DEPEND_1);
        if (yds == null) {
            yds = DataSetUtil.indexGenDataSet(fillDs.length(0)); // QUBE
            fillDs.putProperty(QDataSet.DEPEND_1, yds);
        }

        double cadence = guessCadence(xds, null );
        ((MutablePropertyDataSet) xds).putProperty(QDataSet.CADENCE, cadence);

        spectrogramRend.setDataSet(null);
        colorbar.setVisible(true);

        AutoplotUtil.AutoRangeDescriptor xdesc = AutoplotUtil.autoRange(xds);
        AutoplotUtil.AutoRangeDescriptor ydesc = AutoplotUtil.autoRange(yds);

        if ( autoRange && !autoRangeSuppress) {

            AutoplotUtil.AutoRangeDescriptor desc = AutoplotUtil.autoRange(fillDs);

            colorbar.setLog(desc.log);
            colorbar.resetRange(desc.range);

            Rectangle bounds = colorbar.getBounds();
            if (bounds.width + bounds.x > canvas.getWidth()) {
                int dx = canvas.getWidth() - (bounds.width + bounds.x);
                int oldx = plot.getColumn().getDMaximum();
                plot.getColumn().setDMaximum(oldx + dx);
            }

            plot.getXAxis().setLog(xdesc.log);
            plot.getXAxis().resetRange(xdesc.range);
            plot.getYAxis().setLog(ydesc.log);
            plot.getYAxis().resetRange(ydesc.range);

        }

        overviewPlot.getYAxis().resetRange(ydesc.range);
        overviewPlot.getYAxis().setLog(ydesc.log);
        overviewPlot.getXAxis().resetRange(xdesc.range);
        overviewPlot.getXAxis().setLog(xdesc.log);


        spectrogramRend.setDataSet(TableDataSetAdapter.create(fillDs));
        overSpectrogramRend.setDataSet(TableDataSetAdapter.create(fillDs));
    }

    private void updateFillSeries(WritableDataSet fillDs,boolean autoRange) {

        seriesRend.setDataSet(null);

        QDataSet xds = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
        if (xds == null) {
            xds = DataSetUtil.indexGenDataSet(fillDs.length());
            fillDs.putProperty(QDataSet.DEPEND_0, xds);
        }

        double cadence = guessCadence(xds, fillDs);
        ((MutablePropertyDataSet) xds).putProperty(QDataSet.CADENCE, cadence);

        if ( autoRange && !autoRangeSuppress) {

            boolean isSeries;
            QDataSet depend0 = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
            isSeries = (depend0 == null || DataSetUtil.isMonotonic(depend0));
            if (isSeries) {
                seriesRend.setPsymConnector(PsymConnector.SOLID);
            } else {
                seriesRend.setPsymConnector(PsymConnector.NONE);
            }
            overSeriesRend.setPsymConnector(seriesRend.getPsymConnector());

            seriesRend.setLineWidth(1.0f);

        }

        AutoplotUtil.AutoRangeDescriptor desc = AutoplotUtil.autoRange(fillDs);
        AutoplotUtil.AutoRangeDescriptor xdesc = AutoplotUtil.autoRange(xds);

        if (!autoRangeSuppress) {
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
            seriesRend.setDataSet(VectorDataSetAdapter.create(fillDs));
            overSeriesRend.setDataSet(VectorDataSetAdapter.create(fillDs));
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
        }
    }

    private void startUpdateUnitsThread() {
        Runnable run = new Runnable() {

            public void run() {
                updateFill(true);
            }
        };
        new Thread(run, "updateFillThread").start();
    }

    /**
     * the fill parameters have changed, so update the auto range stats.
     * This should not be run on the AWT event thread!
     */
    private void updateFill( boolean autorange ) {
        if (dataset == null) {
            return;
        }
        WritableDataSet fillDs = applyFillValidRange(dataset);

        boolean spec = fillDs.rank() == 2;
        QDataSet dep1 = (QDataSet) fillDs.property(QDataSet.DEPEND_1);
        if (spec && dep1 != null) {
            Units dep1Units = (Units) dep1.property(QDataSet.UNITS);
            if (dep1Units instanceof EnumerationUnits) {
                spec = false;
                fillDs = DDataSet.copy(DataSetOps.slice1(fillDs, 0));
            }
        }

        if (fillDs.rank() == 3) {
            
            QDataSet ds;
            if ( this.sliceDimension==2 ) {
                int index = Math.min( fillDs.length(0, 0) - 1, sliceIndex );
                ds = DataSetOps.slice2(fillDs, index);
            } else if ( this.sliceDimension==1 ) {
                int index = Math.min( fillDs.length(0) - 1, sliceIndex );
                ds = DataSetOps.slice1(fillDs, index);
            } else if ( this.sliceDimension==0 ) {
                int index = Math.min( fillDs.length() - 1, sliceIndex );
                ds = DataSetOps.slice0(fillDs, index);
            } else throw new IllegalStateException("sliceDimension");
            if ( transpose ) {
                ds = new TransposeRank2DataSet(ds);
            }
            fillDs = DDataSet.copy(ds);
            spec = true;
        }

        if (spec) {
            updateFillSpec(fillDs,autorange);
            setRenderer(spectrogramRend, overSpectrogramRend);
        } else {
            updateFillSeries(fillDs,autorange);
            setRenderer(seriesRend, overSeriesRend);
        }

        fillDataset = fillDs;

        firePropertyChangeListenerPropertyChange(PROPERTY_FILL, null, null);
    }

    private void doInterpretMetadata() {
        Map properties = dataSource.getProperties();
        Object v;
        if ((v = properties.get(DDataSet.TITLE)) != null) {
            plot.setTitle((String) v);
        } else {
            plot.setTitle("");
        }
        if ((v = properties.get(DDataSet.VALID_RANGE)) != null) {
            setValidRange(String.valueOf(v));
        }
        if ((v = properties.get(DDataSet.SCALE_TYPE)) != null) {
            if (spectrogramRend.isActive()) {
                this.colorbar.setLog(v.equals("log"));
            } else {
                this.plot.getYAxis().setLog(v.equals("log"));
            }
        }
    }

    /**
     *
     */
    public void update() {
        List dataSources = getDataSources();
        dataset = null;
        Runnable run = new Runnable() {

            public void run() {
                /*** here is the data load ***/
                Logger.getLogger("ap").info("loading dataset");
                setStatus("loading dataset");
                
                QDataSet dataset = loadDataSet(0);
                if (dataset == null) {
                    seriesRend.setDataSet(null);
                    spectrogramRend.setDataSet(null);
                    return;
                }

                Logger.getLogger("ap").info("update fill");
                setStatus("apply fill and autorange");
                updateFill(true);

                if (interpretMetadata) {
                    doInterpretMetadata();
                }

                originalXRange = plot.getXAxis().getDatumRange();
                originalYRange = plot.getYAxis().getDatumRange();
                originalZRange = colorbar.getDatumRange();
                if (!autoRangeSuppress) {
                    setShowContextOverview(false);
                }
                if (!autoRangeSuppress) {
                    allowAutoContext = true;
                }

                Logger.getLogger("ap").info("fire datasource property change");
                setStatus("");
                firePropertyChangeListenerPropertyChange(PROPERTY_DATASOURCE, null, null);
                if (autoRangeSuppress) {
                    autoRangeSuppress = false;
                }
            }
        };

        if (dataSource.asynchronousLoad() && !headless) {
            Logger.getLogger("ap").info("invoke later do load");
            RequestProcessor.invokeLater(run);
        } else {
            run.run();
        }
    }

    /**
     * load the data set from the DataSource.
     */
    public QDataSet loadDataSet(int i) {
        if (i != 0) {
            throw new IllegalArgumentException("only one dataset supported");
        }
        QDataSet result;
        if (dataset == null) {
            DasProgressMonitor mon = application.getMonitorFactory().getMonitor(plot, "loading data", "loading " + dataSource);
            try {
                dataset = dataSource.getDataSet(mon);
                embedDsDirty = true;

            } catch (Exception e) {
                application.getExceptionHandler().handle(e);
            } finally {
                // don't trust the data sources to call finished when an exception occurs.
                mon.finished();
            }
        }
        return dataset;
    }
    /**
     * Utility field holding list of PropertyChangeListeners.
     */
    private transient java.util.ArrayList propertyChangeListenerList;

    /**
     * Registers PropertyChangeListener to receive events.
     * @param listener The listener to register.
     */
    public synchronized void addPropertyChangeListener(java.beans.PropertyChangeListener listener) {
        if (propertyChangeListenerList == null) {
            propertyChangeListenerList = new java.util.ArrayList();
        }
        propertyChangeListenerList.add(listener);
    }

    /**
     * Removes PropertyChangeListener from the list of listeners.
     * @param listener The listener to remove.
     */
    public synchronized void removePropertyChangeListener(java.beans.PropertyChangeListener listener) {
        if (propertyChangeListenerList != null) {
            propertyChangeListenerList.remove(listener);
        }
    }

    /**
     * Notifies all registered listeners about the event.
     *
     * @param event The event to be fired
     */
    private void firePropertyChangeListenerPropertyChange(String propertyName, Object oldValue, Object newValue) {
        java.beans.PropertyChangeEvent event;
        event = new PropertyChangeEvent(this, propertyName, oldValue, newValue);
        firePropertyChangeListenerPropertyChange(event);
    }

    /**
     * Notifies all registered listeners about the event.
     *
     * @param event The event to be fired
     */
    private void firePropertyChangeListenerPropertyChange(PropertyChangeEvent event) {
        java.util.ArrayList list;
        synchronized (this) {
            if (propertyChangeListenerList == null) {
                return;
            }
            list = (java.util.ArrayList) propertyChangeListenerList.clone();
        }
        for (int i = 0; i < list.size(); i++) {
            ((java.beans.PropertyChangeListener) list.get(i)).propertyChange(event);
        }
    }

    /**
     * TODO: document me
     */
    protected void resetDataSetSourceURL(String surl, DasProgressMonitor mon) {
        this.surl = surl;
        if (surl == null) {
            return;
        }  // not really supported

        surl = DataSetURL.maybeAddFile(surl);

        DataSetURL.URLSplit split = DataSetURL.parse(surl);

        try {
            URL url = new URL(surl);
            if (surl.endsWith(".vap")) {
                try {
                    Logger.getLogger("ap").info("loading vap file");
                    mon.setProgressMessage("loading vap file");
                    File openable = DataSetURL.getFile(url, application.getMonitorFactory().getMonitor(plot, "loading vap", ""));
                    doOpen(openable);
                    Logger.getLogger("ap").info("done loading vap file");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(canvas, "<html>Unable to open resource: <br>" + surl);
                }
            } else {
                Logger.getLogger("ap").info("getting data source");
                mon.setProgressMessage("getting data source " + surl);
                //setStatus( "getting data source "+url );
                DataSource source = DataSetURL.getDataSource(url);
                setDataSource(source);
                //setStatus( "getting data source... done" );
                mon.setProgressMessage("done getting data source");
                Logger.getLogger("ap").info("done getting data source");
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
        if (surl == this.surl || (surl != null && surl.equals(this.surl))) {
            return;
        }
        propertyChangeSupport.firePropertyChange("dataSourceURL", oldVal, surl);
        resetDataSetSourceURL(surl, new NullProgressMonitor());
    }

    public String getDataSourceURL() {
        return surl;
    }

    /** calculate units object that implements validRange and sfill
     */
    public void parseFillValidRange(String validRange, String sfill) throws ParseException {
        if (!"".equals(validRange.trim())) {
            DatumRange vrange = DatumRangeUtil.parseDatumRange(validRange, Units.dimensionless);
            vmin = vrange.min().doubleValue(Units.dimensionless);
            vmax = vrange.max().doubleValue(Units.dimensionless);
        } else {
            vmin = -1 * Double.MAX_VALUE;
            vmax = Double.MAX_VALUE;
        }

        if (!"".equals(sfill.trim())) {
            fill = Double.parseDouble(sfill);
        } else {
            fill = Double.NaN;
        }

        if (dataSource != null) {
            if (!this.autoRangeSuppress) {
                tickleTimer.tickle();
            }
        }

        firePropertyChangeListenerPropertyChange(PROPERTY_FILL, null, null);
    }
    protected List<Bookmark> recent = new LinkedList<Bookmark>();
    protected List<Bookmark> bookmarks = new ArrayList<Bookmark>();

    public List<Bookmark> getRecent() {
        Preferences prefs = Preferences.userNodeForPackage(ApplicationModel.class);
        String srecent = prefs.get("recent", "");

        if (srecent.equals("") || !srecent.startsWith("<")) {
            String srecenturl = System.getProperty("autoplot.default.recent", "http://www.cottagesystems.com/virbo/apps/autoplot/recent.xml");
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
                }
            }
        } else {
            try {
                recent = Bookmark.parseBookmarks(AutoplotUtil.readDoc(new ByteArrayInputStream(srecent.getBytes())));
            } catch (SAXException e) {
                return new ArrayList<Bookmark>();

            } catch (IOException e) {
                return new ArrayList<Bookmark>();

            }
        }

        return recent;

    }

    public List<Bookmark> getBookmarks() {
        Preferences prefs = Preferences.userNodeForPackage(ApplicationModel.class);
        String sbookmark = prefs.get("bookmarks", "");

        if (sbookmark.equals("") || !sbookmark.startsWith("<")) {
            String surl = System.getProperty("autoplot.default.bookmarks", "http://www.cottagesystems.com/virbo/apps/autoplot/bookmarks.xml");
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
        firePropertyChangeListenerPropertyChange(PROPERTY_BOOKMARKS, oldValue, bookmarks);
    }

    private static String strjoin(Collection<String> c, String delim) {
        StringBuffer result = new StringBuffer();
        for (String s : c) {
            if (result.length() > 0) {
                result.append(delim);
            }
            result.append(s);
        }
        return result.toString();
    }

    public void addRecent(String surl) {
        List oldValue = Collections.unmodifiableList(recent);
        Bookmark book = new Bookmark(surl);
        if (recent.contains(book)) { // move it to the front of the list
            recent.remove(book);
        }
        recent.add(book);
        while (recent.size() > MAX_RECENT) {
            recent.remove(0);
        }
        Preferences prefs = Preferences.userNodeForPackage(ApplicationModel.class);
        prefs.put("recent", Bookmark.formatBooks(recent));
        try {
            prefs.flush();
        } catch (BackingStoreException ex) {
            ex.printStackTrace();
        }
        firePropertyChangeListenerPropertyChange(PROPERTY_RECENT, oldValue, recent);
    }

    public void addBookmark(String surl) {
        List oldValue = Collections.unmodifiableList(new ArrayList());
        if (bookmarks.contains(surl)) { // move it to the front of the list
            bookmarks.remove(surl);
        }
        bookmarks.add(new Bookmark(surl));

        Preferences prefs = Preferences.userNodeForPackage(ApplicationModel.class);
        prefs.put("bookmarks", Bookmark.formatBooks(bookmarks));
        try {
            prefs.flush();
        } catch (BackingStoreException ex) {
            ex.printStackTrace();
        }
        firePropertyChangeListenerPropertyChange(PROPERTY_BOOKMARKS, oldValue, bookmarks);
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
        updateFill(true);
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

        state.setBackgroundColor(canvas.getBackground());
        state.setForegroundColor(canvas.getForeground());

        state.setColor(seriesRend.getColor());

        state.setFillColor(seriesRend.getFillColor());
        state.setFillToReference(seriesRend.isFillToReference());
        state.setReference(formatObject(seriesRend.getReference()));

        state.setPlotSymbol(formatObject(seriesRend.getPsym()));
        state.setSymbolConnector(formatObject(seriesRend.getPsymConnector()));

        state.setShowContextOverview(isShowContextOverview());

        state.setUseEmbeddedDataSet(isUseEmbeddedDataSet());

        if (deep && isUseEmbeddedDataSet()) {
            state.setEmbeddedDataSet(getEmbeddedDataSet());
        }

        return state;
    }

    public void restoreState(ApplicationState state, boolean deep) {

        autoRangeSuppress = true;

        try {
            if (state.getSurl() != null) {
                this.surl = "";
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

        if (state.getBackgroundColor() != null) {
            canvas.setBackground(state.getBackgroundColor());
        }
        if (state.getForegroundColor() != null) {
            canvas.setForeground(state.getForegroundColor());
        }

        if (state.getColor() != null) {
            seriesRend.setColor(state.getColor());
        }

        if (state.getFillColor() != null) {
            seriesRend.setFillColor(state.getFillColor());
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

        setUseEmbeddedDataSet(state.isUseEmbeddedDataSet());

        if (deep && state.isUseEmbeddedDataSet() && !"".equals(state.getEmbeddedDataSet())) {
            setEmbeddedDataSet(state.getEmbeddedDataSet());
        }

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
        restoreState(state, true);
        setUseEmbeddedDataSet(false);
        allowAutoContext = true;
        firePropertyChangeListenerPropertyChange("file", null, f);
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
        String oldValue = this.validRange;
        this.validRange = validRange;
        try {
            parseFillValidRange(validRange, sfill);
        } catch (ParseException ex) {
            ex.printStackTrace();
        }
        firePropertyChangeListenerPropertyChange("validRange", oldValue, validRange);
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
    String embedDs = "";
    boolean embedDsDirty = false;

    public String getEmbeddedDataSet() {
        if (isUseEmbeddedDataSet() && embedDsDirty) {
            packEmbeddedDataSet();
        }
        return embedDs;
    }

    private void packEmbeddedDataSet() {
        if (dataset == null) {
            embedDs = "";
            return;
        }

        edu.uiowa.physics.pw.das.dataset.DataSet ds;
        if (dataset.rank() == 1) {
            ds = VectorDataSetAdapter.create(dataset);
        } else {
            ds = TableDataSetAdapter.create(dataset);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(10000);
        DataSetStreamProducer producer = new DataSetStreamProducer();
        producer.setDataSet(ds);
        producer.setCompressed(true);
        producer.setAsciiTransferTypes(false);
        producer.writeStream(out);

        byte[] data = Base64.encode(out.toByteArray());
        embedDs = new String(data);
        embedDsDirty = false;
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

        byte[] data = Base64.decode(embedDs.getBytes());
        InputStream in = new ByteArrayInputStream(data);

        ReadableByteChannel channel = Channels.newChannel(in);

        HashMap props = new HashMap();

        DataSetStreamHandler handler = new DataSetStreamHandler(props, new NullProgressMonitor());
        try {
            StreamTool.readStream(channel, handler);
            this.dataset = DataSetAdapter.create(handler.getDataSet());
            updateFill(true);
        } catch (StreamException ex) {
            ex.printStackTrace();
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
     * deletes all files and folders below root, and root, just as "rm -r" would.
     * TODO: check links
     * @throws IllegalArgumentException if it is unable to delete a file
     * @return true if the operation was successful.
     */
    private boolean deleteFileTree(File root) throws IllegalArgumentException {
        if (!root.exists()) {
            return true;
        }
        File[] children = root.listFiles();
        boolean success = true;
        for (int i = 0; i < children.length; i++) {
            if (children[i].isDirectory()) {
                success = success && deleteFileTree(children[i]);
            } else {
                success = success && children[i].delete();
                if (!success) {
                    throw new IllegalArgumentException("unable to delete file " + children[i]);
                }
            }
        }
        success = success && (!root.exists() || root.delete());
        if (!success) {
            throw new IllegalArgumentException("unable to delete folder " + root);
        }
        return success;
    }

    /**
     * remove all cached downloads.
     * Currently, this is implemented by deleting the das2 fsCache area.
     * @throws IllegalArgumentException if the delete operation fails
     */
    boolean clearCache() throws IllegalArgumentException {
        File local;

        if (System.getProperty("user.name").equals("Web")) {
            local = new File("/tmp");
        } else {
            local = new File(System.getProperty("user.home"));
        }
        local = new File(local, ".das2/fsCache/wfs/");

        return deleteFileTree(local);
    }
    /**
     * Holds value of property showContextOverview.
     */
    private boolean showContextOverview;
    /**
     * Utility field used by bound properties.
     */
    private java.beans.PropertyChangeSupport propertyChangeSupport = new java.beans.PropertyChangeSupport(this);

    /**
     * Getter for property showContextOverview.
     * @return Value of property showContextOverview.
     */
    public boolean isShowContextOverview() {
        return this.showContextOverview;
    }
    private static final double OVERVIEW_PERCENT = 0.60;

    /**
     * Setter for property showContextOverview.
     * @param showContextOverview New value of property showContextOverview.
     */
    public void setShowContextOverview(boolean showContextOverview) {
        boolean oldShowContextOverview = this.showContextOverview;
        this.showContextOverview = showContextOverview;
        DasRow row = plot.getRow();
        row.setMaximum(showContextOverview ? OVERVIEW_PERCENT : 1.00);
        overviewPlot.setVisible(showContextOverview);
        overviewPlotConnector.setVisible(showContextOverview);
        allowAutoContext = false;
        canvas.repaint();
        firePropertyChangeListenerPropertyChange("showContextOverview", new Boolean(oldShowContextOverview), new Boolean(showContextOverview));

    }
    /**
     * Holds value of property interpretMetadata.
     */
    private boolean interpretMetadata = true;

    /**
     * Getter for property interpretMetadata.
     * @return Value of property interpretMetadata.
     */
    public boolean isInterpretMetadata() {
        return this.interpretMetadata;
    }

    /**
     * Setter for property interpretMetadata.
     * @param interpretMetadata New value of property interpretMetadata.
     */
    public void setInterpretMetadata(boolean interpretMetadata) {
        boolean oldInterpretMetadata = this.interpretMetadata;
        this.interpretMetadata = interpretMetadata;
        propertyChangeSupport.firePropertyChange("interpretMetadata", new Boolean(oldInterpretMetadata), new Boolean(interpretMetadata));
    }

    void maybeSetInitialURL(String surl) {
        if (this.surl == null) {
            this.surl = surl;
        }
    }

    private void doOverviewBindings() {
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
        String oldVal = this.status;
        this.status = status;
        firePropertyChangeListenerPropertyChange(PROPERTY_STATUS, oldVal, status);
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
        
    private int sliceDimension= 0;

    public static final String PROP_SLICEDIMENSION = "sliceDimension";

    public int getSliceDimension() {
        return this.sliceDimension;
    }

    public void setSliceDimension(int newsliceDimension) {
        int oldsliceDimension = sliceDimension;
        this.sliceDimension = newsliceDimension;
        updateFill(true);
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
        updateFill(false);
        propertyChangeSupport.firePropertyChange(PROP_SLICEINDEX, oldsliceIndex, newsliceIndex);
    }
    
    private boolean transpose= false;
    public static final String PROP_TRANSPOSE = "transpose";
    
    public void setTranspose( boolean val ) {
        boolean oldVal= this.transpose;
        this.transpose= val;
        updateFill(true);
        propertyChangeSupport.firePropertyChange(PROP_TRANSPOSE, oldVal, val );
    }

    public boolean isTranspose() {
        return this.transpose;
    }
}

