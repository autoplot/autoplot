/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.graph.DasColorBar;
import org.das2.graph.DasPlot;
import org.das2.graph.DefaultPlotSymbol;
import org.das2.graph.PsymConnector;
import org.das2.graph.Renderer;
import org.das2.graph.SeriesRenderer;
import org.das2.graph.SpectrogramRenderer;
import org.das2.system.MutatorLock;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.autoplot.ApplicationModel.RenderType;
import org.virbo.autoplot.AutoplotUtil;
import org.virbo.dataset.DataSetAdapter;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.dsops.Ops;

/**
 * PanelController manages the Panel, for example resolving the datasource and loading the dataset.
 * @author jbf
 */
public class PanelController {

    private static final String PENDING_RESET_RANGE = "resetRanges";
    Logger logger = Logger.getLogger("vap.panelController");
    private Application dom;
    private ApplicationModel appmodel;  //TODO: get rid of this
    private Panel panel;
    private ChangesSupport changesSupport;
    private PropertyChangeSupport propertyChangeSupport = new DebugPropertyChangeSupport(this);
    /**
     * switch over between fine and course points.
     */
    public static int SYMSIZE_DATAPOINT_COUNT = 500;

    public PanelController(final ApplicationModel model, final Application dom, final Panel panel) {
        panel.controller = this;
        this.dom = dom;
        this.panel = panel;
        this.appmodel = model;
        this.changesSupport= new ChangesSupport(this.propertyChangeSupport);
        panel.addPropertyChangeListener(Panel.PROP_RENDERTYPE, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                setRenderType(panel.getRenderType());
            }
        });

        panel.addPropertyChangeListener(Panel.PROP_DATASOURCEFILTERID, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                resetDataSource();
            }
        });

    }

    private void resetDataSource() {
        assert (panel.getDataSourceFilterId() != null);
        final DataSourceFilter dsf = getDataSourceFilter();

        dsf.addPropertyChangeListener(DataSourceFilter.PROP_SLICEDIMENSION, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                setResetRanges(true);
            }
        });
        dsf.addPropertyChangeListener(DataSourceFilter.PROP_TRANSPOSE, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                setResetRanges(true);
            }
        });

        dsf.addPropertyChangeListener(Panel.PROP_RENDERTYPE, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                setRenderType(panel.getRenderType());
            }
        });

        setDataSourceFilterController(getDataSourceFilter().getController());
    }

    private Color deriveColor(Color color, int i) {

        float[] colorHSV = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        if (colorHSV[2] < 0.7f) {
            colorHSV[2] = 0.7f;
        }
        if (colorHSV[1] < 0.7f) {
            colorHSV[1] = 0.7f;
        }
        return Color.getHSBColor(i / 6.f, colorHSV[1], colorHSV[2]);
    }

    private boolean rendererAcceptsData( QDataSet fillDs ) {
        if ( fillDs.rank()==1 && getRenderer() instanceof SpectrogramRenderer ) {
            return false;
        } else if ( fillDs.rank()==2 && getRenderer() instanceof SeriesRenderer ) {
            return false;
        } else {
            return true;
        }
    }

    private void setDataSet(QDataSet fillDs) throws IllegalArgumentException {

        String label = null;
        if (!panel.getComponent().equals("") && fillDs.length() > 0) {
            String[] labels = SemanticOps.getComponentLabels(fillDs);

            if (panel.getComponent().equals("X")) {
                fillDs = DataSetOps.slice1(fillDs, 0);
                label = labels[0];
            } else if (panel.getComponent().equals("Y")) {
                fillDs = DataSetOps.slice1(fillDs, 1);
                label = labels[1];
            } else if (panel.getComponent().equals("Z")) {
                fillDs = DataSetOps.slice1(fillDs, 2);
                label = labels[2];
            } else {
                for ( int i=0; i<labels.length; i++ ) {
                    if ( labels[i].equals(panel.getComponent()) ) {
                        fillDs= DataSetOps.slice1(fillDs, i);
                        label = labels[i];
                    }
                }
            }
            if ( label==null ) {
                throw new IllegalArgumentException("not supported: " + panel.getComponent());
            }
        }

        if (getRenderer() != null) {
            if ( rendererAcceptsData(fillDs) ) {
                getRenderer().setDataSet(DataSetAdapter.createLegacyDataSet(fillDs));
                if ( label!=null ) ((SeriesRenderer) getRenderer()).setLegendLabel(label);
            } else {
                getRenderer().setException( new Exception( "renderer cannot plot "+fillDs ));
            }
        }

        final DataSourceFilter dsf = getDataSourceFilter();
        String reduceRankString = dsf.getController().getReduceDataSetString();
        if (dsf.getController().getReduceDataSetString() != null) {
            // kludge to update title
            String title = dom.getController().getPlot().getTitle(); //TODO: fix
            Pattern p = Pattern.compile("(.*)!c(.+)=(.+)");
            Matcher m = p.matcher(title);
            if (m.matches()) {
                title = m.group(1) + "!c" + reduceRankString;
                getDasPlot().setTitle(title);
            }
        }
    }

    public void setDataSourceFilterController(final DataSourceController dsc) {
        dsc.addPropertyChangeListener(DataSourceController.PROP_FILLDATASET, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                QDataSet fillDs = dsc.getFillDataSet();
                if (fillDs != null && resetRanges) {
                    doResetRanges(true);
                    setResetRanges(false);
                }
                if (fillDs == null) {
                    if (getRenderer() != null) {
                        getRenderer().setDataSet(null);
                    }
                } else {
                    if (!dom.getController().isValueAdjusting() && panel.getRenderType() == RenderType.series && fillDs.rank() == 2 && panel.getComponent().equals("")) {
                        MutatorLock lock = dom.getController().mutatorLock();
                        lock.lock();
                        String[] labels = SemanticOps.getComponentLabels(fillDs);
                        panel.setComponent(labels[0]);
                        Color c = panel.getStyle().getColor();
                        panel.getStyle().setColor(deriveColor(c, 0));
                        Plot domPlot = dom.getController().getPlotFor(panel);
                        for (int i = 1; i < fillDs.length(0); i++) {
                            Panel cpanel = dom.getController().copyPanel(panel, domPlot, dsc.dsf);
                            //Panel cpanel = dom.getController().getPanelsFor(cplot).get(0);
                            cpanel.getStyle().setColor(deriveColor(c, i));
                            cpanel.setComponent(labels[i]);
                            cpanel.setRenderType(panel.getRenderType()); // this creates the das2 SeriesRenderer.
                            cpanel.getController().setDataSet(fillDs);
                        }
                        lock.unlock();
                    }

                    setDataSet(fillDs);
                }
            }
        });

        dsc.addPropertyChangeListener(DataSourceController.PROP_DATASOURCE, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                setResetRanges(true);
            }
        });

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
     * true indicates the controller should autorange next time the fillDataSet is changed.
     */
    public static final String PROP_RESETRANGES = "resetRanges";
    protected boolean resetRanges = true;

    public boolean isResetRanges() {
        return resetRanges;
    }

    public void setResetRanges(boolean resetRanges) {
        boolean oldResetRanges = this.resetRanges;
        this.resetRanges = resetRanges;
        propertyChangeSupport.firePropertyChange(PROP_RESETRANGES, oldResetRanges, resetRanges);
    }

    public void setSuri(String surl) {
        //TODO: see how this is used.  Should all the other guys listening to this change as well?
        getDataSourceFilter().setSuri(surl);
    }
    protected Renderer renderer = null;

    protected Renderer getRenderer() {
        return renderer;
    }

    protected void setRenderer(Renderer renderer) {
        this.renderer = renderer;
        if (renderer instanceof SeriesRenderer) {
            bindToSeriesRenderer((SeriesRenderer) renderer);
            bindToSpectrogramRenderer(new SpectrogramRenderer(null, null));
        } else {
            bindToSpectrogramRenderer((SpectrogramRenderer) renderer);
            bindToSeriesRenderer(new SeriesRenderer());
        }
    }

    private synchronized void doResetRanges(boolean autorange) {

        changesSupport.performingChange(this, PENDING_RESET_RANGE);

        DataSourceFilter dsf = getDataSourceFilter();

        RenderType renderType = AutoplotUtil.getRenderType(dsf.getController().getFillDataSet());
        panel.setRenderType(renderType);

        this.setRenderer(autorange, true);

        Panel panelCopy = (Panel) panel.copy();

        doMetadata(panelCopy, autorange, true);

        doAutoranging(panelCopy);

        panel.syncTo(panelCopy);

        dom.getController().getPlotFor(panel).syncTo(panel.getPlotDefaults());

        setStatus("done, apply fill and autorange");
        changesSupport.changePerformed( this, PENDING_RESET_RANGE );
    }

    /**
     * extract properties from the data and metadata to get axis labels, fill values, and 
     * preconditions:
     *    fillData is set.  
     *    fillProperties is set.
     * postconditions:
     *    metadata is inspected to get axis labels, fill values, etc.
     *    renderType is determined and set.
     * @param autorange
     * @param interpretMetadata
     */
    public void doMetadata(Panel panelCopy, boolean autorange, boolean interpretMetadata) {
        final DataSourceFilter dsf = getDataSourceFilter();
        QDataSet fillDs = dsf.getController().getFillDataSet();
        Map<String, Object> properties = dsf.getController().getFillProperties();

        RenderType renderType = panel.getRenderType();


        /* begin interpret metadata */
        if (interpretMetadata && autorange) {

            if (dom.getOptions().isAutolabelling()) {
                panelCopy.getPlotDefaults().getXaxis().setLabel("");
                panelCopy.getPlotDefaults().getYaxis().setLabel("");
                panelCopy.getPlotDefaults().getZaxis().setLabel("");
                panelCopy.getPlotDefaults().setTitle("");
            }

            doInterpretMetadata(panelCopy, properties, panel.getRenderType());

            String reduceRankString = getDataSourceFilter().getController().getReduceDataSetString();
            if (dsf.getController().getReduceDataSetString() != null) {
                String title = panelCopy.getPlotDefaults().getTitle();
                title += "!c" + reduceRankString;
                panelCopy.getPlotDefaults().setTitle(title);
            }

            PanelUtil.unitsCheck(properties, fillDs); // DANGER--this may cause overplotting problems in the future by removing units

            panel.syncTo(panelCopy);

        } else {
            // kludge to support updating slice location report without autoranging.
            // I don't think it's coming into this dead code.
            if (dsf.getController().getReduceDataSetString() != null) {
                panelCopy.getPlotDefaults().setTitle("");
                doInterpretMetadata(panelCopy, properties, renderType);

            }
        }


    }

    private void doInterpretMetadata(Panel cpanel, Map properties, RenderType spec) {

        Object v;
        final Plot plotDefaults = cpanel.getPlotDefaults();

        if ((v = properties.get(QDataSet.TITLE)) != null) {
            plotDefaults.setTitle((String) v);
        }

        if (spec == RenderType.spectrogram) {
            if (dom.getOptions().isAutoranging() && (v = properties.get(QDataSet.SCALE_TYPE)) != null) {
                plotDefaults.getZaxis().setLog(v.equals("log"));
            }

            if (dom.getOptions().isAutolabelling() && (v = properties.get(QDataSet.LABEL)) != null) {
                plotDefaults.getZaxis().setLabel((String) v);
            }

            if (dom.getOptions().isAutolabelling() && (v = properties.get(QDataSet.DEPEND_1)) != null) {
                Map m = (Map) v;
                Object v2 = m.get(QDataSet.LABEL);
                if (v2 != null) {
                    plotDefaults.getYaxis().setLabel((String) v2);
                }

            }
        } else {
            if (dom.getOptions().isAutoranging() && (v = properties.get(QDataSet.SCALE_TYPE)) != null) {
                plotDefaults.getYaxis().setLog(v.equals("log"));
            }

            if (dom.getOptions().isAutolabelling() && (v = properties.get(QDataSet.LABEL)) != null) {
                plotDefaults.getYaxis().setLabel((String) v);
            }

            if (spec == RenderType.colorScatter) {
                v = properties.get(QDataSet.PLANE_0);
                if (v != null) {
                    Map m = (Map) v;
                    Object v2 = m.get(QDataSet.LABEL);
                    if (v2 != null) {
                        plotDefaults.getZaxis().setLabel((String) v2);
                    }
                } else {
                    plotDefaults.getZaxis().setLabel("");
                }
            } else {
                plotDefaults.getZaxis().setLabel("");
            }

        }

        if ((v = properties.get(QDataSet.DEPEND_0)) != null) {
            Map m = (Map) v;
            Object v2 = m.get(QDataSet.LABEL);
            if (dom.getOptions().isAutolabelling() && v2 != null) {
                plotDefaults.getXaxis().setLabel((String) v2);
            }

        }

    }

    private void guessCadence(MutablePropertyDataSet xds, QDataSet fillDs) {
        Units xunits = (Units) xds.property(QDataSet.UNITS);
        if (xunits == null) xunits = Units.dimensionless;

        Double cadence = DataSetUtil.guessCadence(xds, fillDs);
        if (cadence == null && !UnitsUtil.isTimeLocation(xunits)) {
            cadence = DataSetUtil.guessCadence(Ops.log(xds), null);
            if (cadence != null) {
                xds.putProperty(QDataSet.SCALE_TYPE, "log");
            }
        }
        xds.putProperty(QDataSet.CADENCE, cadence);
    }

    /**
     * this is the old updateFillSeries and updateFillSpectrogram code.  This calculates
     * ranges and preferred symbol settings, and puts the values in cpanel.plotDefaults.
     * @param cpanel
     * @param props
     * @param spec
     */
    private void doAutoranging(Panel panelCopy) {
        Map props = getDataSourceFilter().getController().getFillProperties();
        RenderType spec = panelCopy.getRenderType();

        if (props == null) {
            props = Collections.EMPTY_MAP;
        }

        QDataSet fillDs = getDataSourceFilter().getController().getFillDataSet();

        if (spec == RenderType.spectrogram) {

            QDataSet xds = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
            if (xds == null) {
                xds = DataSetUtil.indexGenDataSet(fillDs.length());
            }

            QDataSet yds = (QDataSet) fillDs.property(QDataSet.DEPEND_1);
            if (yds == null) {
                yds = DataSetUtil.indexGenDataSet(fillDs.length(0)); // QUBE
            }

            guessCadence((MutablePropertyDataSet) xds, fillDs);
            guessCadence((MutablePropertyDataSet) yds, null);

            AutoplotUtil.AutoRangeDescriptor xdesc = AutoplotUtil.autoRange(xds, (Map) props.get(QDataSet.DEPEND_0));

            AutoplotUtil.AutoRangeDescriptor ydesc = AutoplotUtil.autoRange(yds, (Map) props.get(QDataSet.DEPEND_1));

            AutoplotUtil.AutoRangeDescriptor desc = AutoplotUtil.autoRange(fillDs, props);

            panelCopy.getPlotDefaults().getZaxis().setRange(desc.range);
            panelCopy.getPlotDefaults().getZaxis().setLog(desc.log);
            panelCopy.getPlotDefaults().getXaxis().setLog(xdesc.log);
            panelCopy.getPlotDefaults().getXaxis().setRange(xdesc.range);
            panelCopy.getPlotDefaults().getYaxis().setLog(ydesc.log);
            panelCopy.getPlotDefaults().getYaxis().setRange(ydesc.range);

        } else {

            boolean isSeries;
            QDataSet depend0 = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
            isSeries = (depend0 == null || DataSetUtil.isMonotonic(depend0));
            if (isSeries) {
                panelCopy.getStyle().setSymbolConnector(PsymConnector.SOLID);
            } else {
                panelCopy.getStyle().setSymbolConnector(PsymConnector.NONE);
            }

            panelCopy.getStyle().setLineWidth(1.0f);

            AutoplotUtil.AutoRangeDescriptor desc = AutoplotUtil.autoRange(fillDs, props);

            panelCopy.getPlotDefaults().getYaxis().setLog(desc.log);
            panelCopy.getPlotDefaults().getYaxis().setRange(desc.range);

            QDataSet xds = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
            if (xds == null) {
                xds = DataSetUtil.indexGenDataSet(fillDs.length());
            }

            guessCadence((MutablePropertyDataSet) xds, fillDs);

            AutoplotUtil.AutoRangeDescriptor xdesc = AutoplotUtil.autoRange(xds, (Map) props.get(QDataSet.DEPEND_0));

            panelCopy.getPlotDefaults().getXaxis().setLog(xdesc.log);
            panelCopy.getPlotDefaults().getXaxis().setRange(xdesc.range);

            if (spec == RenderType.colorScatter) {
                AutoplotUtil.AutoRangeDescriptor zdesc = AutoplotUtil.autoRange((QDataSet) fillDs.property(QDataSet.PLANE_0),
                        (Map) props.get(QDataSet.PLANE_0));
                panelCopy.getPlotDefaults().getZaxis().setLog(zdesc.log);
                panelCopy.getPlotDefaults().getZaxis().setRange(zdesc.range);
                panelCopy.getPlotDefaults().getZaxis().setRange(zdesc.range);

            }

            if (fillDs.length() > 30000) {
                panelCopy.getStyle().setSymbolConnector(PsymConnector.NONE);
                panelCopy.getStyle().setSymbolSize(1.0);
            } else {
                panelCopy.getStyle().setPlotSymbol(DefaultPlotSymbol.CIRCLES);
                if (fillDs.length() > SYMSIZE_DATAPOINT_COUNT) {
                    panelCopy.getStyle().setSymbolSize(1.0);
                } else {
                    panelCopy.getStyle().setSymbolSize(3.0);
                }

            }

        }
    }

    public DasPlot getDasPlot() {
        return dom.getController().getPlotFor(panel).getController().getDasPlot();
    }

    private DasColorBar getColorbar() {
        return dom.getController().getPlotFor(panel).getController().getDasColorBar();
    }

    /**
     * return the data source and filter for this panel.
     * @return
     */
    public DataSourceFilter getDataSourceFilter() {
        return dom.getController().getDataSourceFilterFor(panel);
    }

    /**
     * preconditions:
     *   fill dataset has been calculated.
     *   renderer type has been identified.
     * postconditions:
     *   renderer has been identified.  If the existing renderer is usable, then it is reused.
     *   renderer is set to plotDefaults at fillDataSet.
     *   colorbar has been hidden or revealed.
     * @param autorange
     * @param interpretMetadata
     */
    public void setRenderer(boolean autorange, boolean interpretMetadata) {

        setRenderType(panel.getRenderType());

    }

    /**
     * used to explicitly set the rendering type.
     * If the panel's data isn't loaded then this has no effect.
     * @param renderType
     */
    public void setRenderType(RenderType renderType) {

        Renderer oldRenderer = getRenderer();
        Renderer newRenderer = AutoplotUtil.maybeCreateRenderer(renderType, oldRenderer, getColorbar());

        if (oldRenderer != newRenderer) {
            setRenderer(newRenderer);

            DasPlot plot = getDasPlot();

            if (oldRenderer != null) plot.removeRenderer(oldRenderer);
            plot.addRenderer(newRenderer);

            if (getDataSourceFilter().getController().getFillDataSet() != null) {
              setDataSet(getDataSourceFilter().getController().getFillDataSet());
            }
        }

    }

    public synchronized void bindToSeriesRenderer(SeriesRenderer seriesRenderer) {
        ApplicationController ac = this.dom.getController();

        ac.bind(panel, "style.lineWidth", seriesRenderer, "lineWidth");
        ac.bind(panel, "style.color", seriesRenderer, "color");
        ac.bind(panel, "style.symbolSize", seriesRenderer, "symSize");
        ac.bind(panel, "style.symbolConnector", seriesRenderer, "psymConnector");
        ac.bind(panel, "style.plotSymbol", seriesRenderer, "psym");
        ac.bind(panel, "style.fillColor", seriesRenderer, "fillColor");
        ac.bind(panel, "style.fillToReference", seriesRenderer, "fillToReference");
        ac.bind(panel, "style.reference", seriesRenderer, "reference");

    }

    public void bindToSpectrogramRenderer(SpectrogramRenderer spectrogramRenderer) {
        ApplicationController ac = this.dom.getController();

        ac.bind(panel, "style.rebinMethod", spectrogramRenderer, "rebinner");
        ac.bind(panel, "style.colortable", spectrogramRenderer, "colorBar.type");

    }

    public boolean isValueAdjusting() {
        return changesSupport.isValueAdjusting();
    }

    public boolean isPendingChanges() {
        return getDataSourceFilter().getController().isPendingChanges() || changesSupport.isPendingChanges();
    }

    private void setStatus(String string) {
        this.dom.getController().setStatus(string);
    }
}
