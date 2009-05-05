/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.graph.DasColorBar;
import org.das2.graph.DasPlot;
import org.das2.graph.DefaultPlotSymbol;
import org.das2.graph.ImageVectorDataSetRenderer;
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
import org.virbo.dataset.RankZeroDataSet;
import org.virbo.dataset.SemanticOps;

/**
 * PanelController manages the Panel, for example resolving the datasource and loading the dataset.
 * @author jbf
 */
public class PanelController {

    private static final String PENDING_RESET_RANGE = "resetRanges";
    private static final String PENDING_SET_DATASET= "setDataSet";

    Logger logger = Logger.getLogger("vap.panelController");
    private Application dom;
    private ApplicationModel appmodel;  //TODO: get rid of this
    private Panel panel;
    /** when additional panels are automatically added, this panel keeps the original and 
     * perhaps default settings for the child panels.
     */
    private Panel parentPanel;
    private List<Panel> childPanels;
    private ChangesSupport changesSupport;
    private PropertyChangeSupport propertyChangeSupport = new DebugPropertyChangeSupport(this);
    private DataSourceFilter dsf; // This is the one we are listening to.
    /**
     * switch over between fine and course points.
     */
    public static final int SYMSIZE_DATAPOINT_COUNT = 500;
    public static final int LARGE_DATASET_COUNT = 30000;

    public PanelController(final ApplicationModel model, final Application dom, final Panel panel) {
        panel.controller = this;
        this.dom = dom;
        this.panel = panel;
        this.appmodel = model;
        this.changesSupport = new ChangesSupport(this.propertyChangeSupport,this);
        panel.addPropertyChangeListener(Panel.PROP_RENDERTYPE, panelListener);
        panel.addPropertyChangeListener(Panel.PROP_DATASOURCEFILTERID, panelListener);
    }

    /**
     * remove any bindings and listeners
     */
    void unbindDsf() {
        dsf.removePropertyChangeListener(DataSourceFilter.PROP_SLICEDIMENSION, dsfListener);
        dsf.removePropertyChangeListener(DataSourceFilter.PROP_TRANSPOSE, dsfListener);
        dsf.getController().removePropertyChangeListener(DataSourceController.PROP_FILLDATASET, fillDataSetListener);
        dsf.getController().removePropertyChangeListener(DataSourceController.PROP_DATASOURCE, dataSourceDataSetListener);
    }
    PropertyChangeListener dsfListener = new PropertyChangeListener() {

        public String toString() {
            return "" + PanelController.this;
        }

        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName().equals(DataSourceFilter.PROP_SLICEDIMENSION) || evt.getPropertyName().equals(DataSourceFilter.PROP_TRANSPOSE)) {
                setResetRanges(true);
            }
        }
    };
    PropertyChangeListener panelListener = new PropertyChangeListener() {

        public String toString() {
            return "" + PanelController.this;
        }

        public void propertyChange(PropertyChangeEvent evt) {
            logger.fine("panelListener: "+evt.getPropertyName()+" "+evt.getOldValue()+"->"+evt.getNewValue());
            if (evt.getPropertyName().equals(Panel.PROP_RENDERTYPE)) {
                if ( !dom.panels.contains(panel) ) {  //TODO: I think this can be removed. The applicationController was preventing the panel/panelController from being garbage collected.
                    return;
                }
                setRenderType(panel.getRenderType());
                setResetRenderer(false);
            } else if (evt.getPropertyName().equals(Panel.PROP_DATASOURCEFILTERID)) {
                resetDataSource();
            }
        }
    };

    private void resetDataSource() {
        if (dsf != null) {
            unbindDsf();  
            List<DomNode> usages= DomUtil.dataSourceUsages(dom, dsf.getId() );
            if ( usages.size()==0 ) {
                dom.getController().deleteDataSourceFilter(dsf);
            }
        }

        assert (panel.getDataSourceFilterId() != null);
        if ( panel.getDataSourceFilterId().equals("") ) return;
        
        dsf = dom.getController().getDataSourceFilterFor(panel);

        if ( dsf==null ) {
            throw new NullPointerException("couldn't find the data for this panel");
        } else {
            dsf.addPropertyChangeListener(DataSourceFilter.PROP_SLICEDIMENSION, dsfListener);
            dsf.addPropertyChangeListener(DataSourceFilter.PROP_TRANSPOSE, dsfListener);
        }
        setDataSourceFilterController(getDataSourceFilter().getController());
    }

    private Color deriveColor( Color color, int i ) {

        float[] colorHSV = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        if (colorHSV[2] < 0.7f) {
            colorHSV[2] = 0.7f;
        }
        if (colorHSV[1] < 0.7f) {
            colorHSV[1] = 0.7f;
        }
        return Color.getHSBColor(i / 6.f, colorHSV[1], colorHSV[2]);
    }

    private boolean rendererAcceptsData(QDataSet fillDs) {
        if ( getRenderer() instanceof SpectrogramRenderer ) {
            return fillDs.rank()>1;
        } else if ( getRenderer() instanceof SeriesRenderer) {
            return fillDs.rank()==1;
        } else if ( getRenderer() instanceof ImageVectorDataSetRenderer ) {
            return fillDs.rank()==1;
        } else {
            return true;
        }
    }

    private void setDataSet(QDataSet fillDs) throws IllegalArgumentException {

        // since we might delete sibling panels here, make sure each panel is still part of the application
        if (!Arrays.asList(dom.getPanels()).contains(panel)) {
            return;
        }

        String label = null;

        if (!panel.getComponent().equals("") && fillDs.length() > 0 && fillDs.rank() == 2 && fillDs.property(QDataSet.DEPEND_1)!=null ) {
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
                for (int i = 0; i < labels.length; i++) {
                    if (labels[i].equals(panel.getComponent())) {
                        fillDs = DataSetOps.slice1(fillDs, i);
                        label = labels[i];
                    }
                }
            }
            if (label == null && !isPendingChanges() ) {
                throw new IllegalArgumentException("component not found: " + panel.getComponent());
            }
        }

        if (getRenderer() != null) {
            if (rendererAcceptsData(fillDs)) {
                getRenderer().setDataSet(DataSetAdapter.createLegacyDataSet(fillDs));
            } else {
                getRenderer().setException(new Exception("renderer cannot plot " + fillDs));
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
    PropertyChangeListener fillDataSetListener = new PropertyChangeListener() {

        public String toString() {
            return "" + PanelController.this;
        }

        public synchronized void propertyChange(PropertyChangeEvent evt) {
            changesSupport.performingChange( this, PENDING_SET_DATASET );
            if (!Arrays.asList(dom.getPanels()).contains(panel)) {
                return;  // TODO: kludge, I was deleted. I think this can be removed now.  The applicationController was preventing GC.
            }
            QDataSet fillDs = dsf.getController().getFillDataSet();
            logger.fine( "got new dataset: "+fillDs);
            if ( fillDs!=null ) {
                if ( resetRanges ) {
                    if ( parentPanel==null ) {
                        RenderType renderType = AutoplotUtil.getRenderType(fillDs);
                        panel.setRenderType(renderType);
                        resetPanel(fillDs,renderType);
                    } else {
                        dom.getController().deletePanel(panel);
                    }
                    
                } else if ( resetRenderer ) {
                    setRenderType(panel.getRenderType());
                    setResetRenderer(false);
                }
            }

            if (fillDs == null) {
                if (getRenderer() != null) {
                    getRenderer().setDataSet(null);
                }
            } else {
                setDataSet(fillDs);
            }
            changesSupport.changePerformed( this, PENDING_SET_DATASET );
        }
    };

    private void resetPanel(QDataSet fillDs,RenderType renderType) {
        logger.finest("resetPanel..."+fillDs+" "+renderType);
        if (renderer != null) {
            renderer.setActive(true);
        }
        
        if (childPanels != null) {
            for (Panel p : this.childPanels) {
                if ( dom.panels.contains(p) ) {  // kludge to avoid runtime exception.  Why is it deleted twice?
                    dom.getController().deletePanel(p);
                }
            }
        }

        if (fillDs != null) {
            doResetRanges(true);
            setResetRanges(false);

            // add additional panels when it's a bundle of rank1 datasets.
            if (!dom.getController().isValueAdjusting()) {
                if ( fillDs.rank() == 2 && ( renderType != RenderType.spectrogram ) && fillDs.length(0) < 12) {
                    MutatorLock lock = dom.getController().mutatorLock();
                    lock.lock();
                    renderer.setActive(false);
                    String[] labels = SemanticOps.getComponentLabels(fillDs);
                    Color c = panel.getStyle().getColor();
                    Color fc= panel.getStyle().getFillColor();
                    Plot domPlot = dom.getController().getPlotFor(panel);
                    List<Panel> cp = new ArrayList<Panel>(fillDs.length(0));
                    for (int i = 0; i < fillDs.length(0); i++) {
                        Panel cpanel = dom.getController().copyPanel(panel, domPlot, dsf);
                        cpanel.getController().getRenderer().setActive(false);
                        cp.add(cpanel);
                        cpanel.getController().parentPanel = panel;
                        cpanel.getStyle().setColor(deriveColor(c, i));
                        cpanel.getStyle().setFillColor( deriveColor(fc,i).brighter() );
                        cpanel.setComponent(labels[i]);
                        cpanel.setDisplayLegend(true);
                        cpanel.setLegendLabel(labels[i].trim());
                        cpanel.setRenderType(panel.getRenderType()); // this creates the das2 SeriesRenderer.
                        cpanel.getController().setDataSet(fillDs);
                    }
                    for ( Panel cpanel: cp ) {
                        cpanel.getController().getRenderer().setActive(true);
                    }
                    renderer.setActive(false);
                    PanelController.this.childPanels = cp;
                    lock.unlock();
                }
            } //!dom.getController().isValueAdjusting()
        } //fillDs != null
    }

    /**
     * When the data source changes, we will need to autorange so that the axis
     * units are set correctly and things are in a consistent state.  One exception
     * to this is when we are doing state transistions with save/load redo/undo, where
     * we need to avoid autoranging.  Note a kludge in ApplicationController sync
     * sets resetRanges to false after the load.
     */
    PropertyChangeListener dataSourceDataSetListener = new PropertyChangeListener() {

        public String toString() {
            return "" + PanelController.this;
        }

        public void propertyChange(PropertyChangeEvent evt) {
            setResetRanges(true);
        }
    };

    public void setDataSourceFilterController(final DataSourceController dsc) {
        dsc.addPropertyChangeListener(DataSourceController.PROP_FILLDATASET, fillDataSetListener);
        dsc.addPropertyChangeListener(DataSourceController.PROP_DATASOURCE, dataSourceDataSetListener);
    }

    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(propertyName, listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }
    /**
     * true indicates the controller should autorange next time the fillDataSet is changed.
     */
    public static final String PROP_RESETRANGES = "resetRanges";
    protected boolean resetRanges = false;

    public boolean isResetRanges() {
        return resetRanges;
    }

    public void setResetRanges(boolean resetRanges) {
        boolean oldResetRanges = this.resetRanges;
        this.resetRanges = resetRanges;
        propertyChangeSupport.firePropertyChange(PROP_RESETRANGES, oldResetRanges, resetRanges);
    }

    /**
     * true indicates the controller should install a new renderer to implement the
     * renderType selection.
     */
    public static final String PROP_RESETRENDERER = "resetRenderer";
    protected boolean resetRenderer = false;

    public boolean isResetRenderer() {
        return resetRenderer;
    }

    public void setResetRenderer(boolean resetRenderer) {
        boolean oldResetRenderer = this.resetRenderer;
        this.resetRenderer = resetRenderer;
        propertyChangeSupport.firePropertyChange(PROP_RESETRENDERER, oldResetRenderer, resetRenderer);
    }


    protected Renderer renderer = null;

    public Renderer getRenderer() {
        return renderer;
    }

    public void setRenderer(Renderer renderer) {
        this.renderer = renderer;
        if (renderer instanceof SeriesRenderer) {
            bindToSeriesRenderer((SeriesRenderer) renderer);
            bindToSpectrogramRenderer(new SpectrogramRenderer(null, null));
        } else if (renderer instanceof SpectrogramRenderer) {
            bindToSpectrogramRenderer((SpectrogramRenderer) renderer);
            bindToSeriesRenderer(new SeriesRenderer());
        } else if (renderer instanceof ImageVectorDataSetRenderer) {
            bindToImageVectorDataSetRenderer((ImageVectorDataSetRenderer) renderer);
        } else {
            bindToSpectrogramRenderer(new SpectrogramRenderer(null, null));
            bindToSeriesRenderer(new SeriesRenderer());
        }
        ApplicationController ac = this.dom.getController();
        ac.bind(panel, Panel.PROP_LEGENDLABEL, renderer, Renderer.PROP_LEGENDLABEL);
        ac.bind(panel, Panel.PROP_DISPLAYLEGEND, renderer, Renderer.PROP_DRAWLEGENDLABEL);
        ac.bind(panel, Panel.PROP_ACTIVE, renderer, Renderer.PROP_ACTIVE );
    }

    private synchronized void doResetRanges( boolean autorange ) {
        logger.finest("doResetRanges...");
        changesSupport.performingChange(this, PENDING_RESET_RANGE);

        setRenderType(panel.getRenderType());
        setResetRenderer(false);
        Plot plot = dom.getController().getPlotFor(panel);

        Panel panelCopy = (Panel) panel.copy();
        panelCopy.getPlotDefaults().syncTo(plot);

        if (dom.getOptions().isAutolabelling()) {
            doMetadata(panelCopy, autorange, true);
        }

        if (dom.getOptions().isAutoranging()) {
            doAutoranging(panelCopy);
        }

        panel.syncTo(panelCopy);

        plot.syncTo(panel.getPlotDefaults());

        setStatus("done, apply fill and autorange");
        changesSupport.changePerformed(this, PENDING_RESET_RANGE);
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
    private void doMetadata(Panel panelCopy, boolean autorange, boolean interpretMetadata) {
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
        String legendLabel= null;
        if ((v = properties.get(QDataSet.NAME)) != null) {
            legendLabel= (String)v;
        }
        if ((v = properties.get(QDataSet.LABEL)) != null) {
            legendLabel= (String)v;
        }
        if ( legendLabel!=null ) {
            cpanel.setLegendLabel((String) legendLabel);
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
        } else { // hugeScatter okay
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
        if ( xds.length()<2 ) return;

        RankZeroDataSet cadence = DataSetUtil.guessCadenceNew(xds, fillDs);
        if ( cadence!=null && "log".equals(cadence.property(QDataSet.SCALE_TYPE) ) ) {
            xds.putProperty( QDataSet.SCALE_TYPE, "log" );
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

            QDataSet hist= getDataSourceFilter().getController().getHistogram();
            AutoplotUtil.AutoRangeDescriptor desc;
            if ( false && hist!=null ) {
                desc= AutoplotUtil.autoRange( hist, fillDs, props );
            } else {
                desc = AutoplotUtil.autoRange( fillDs, props );
            }

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

            QDataSet hist= getDataSourceFilter().getController().getHistogram();
            AutoplotUtil.AutoRangeDescriptor desc;
            if ( false && hist!=null ) {
                desc= AutoplotUtil.autoRange( hist, fillDs, props );
            } else {
                desc = AutoplotUtil.autoRange( fillDs, props );
            }
            
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

            if (fillDs.length() > LARGE_DATASET_COUNT) {
                panelCopy.getStyle().setSymbolConnector(PsymConnector.NONE);
                panelCopy.getStyle().setPlotSymbol(DefaultPlotSymbol.CIRCLES);
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

    /**
     * return the plot containing this panel's renderer, or null.
     * @return the plot containing this panel's renderer, or null.
     */
    public DasPlot getDasPlot() {
        Plot p= dom.getController().getPlotFor(panel);
        if ( p==null ) return null;
        return p.getController().getDasPlot();
    }

    private DasColorBar getColorbar() {
        Plot p= dom.getController().getPlotFor(panel);
        if ( p==null ) throw new IllegalArgumentException("no plot found for panel");
        return p.getController().getDasColorBar();
    }

    /**
     * return the data source and filter for this panel.
     * @return
     */
    public DataSourceFilter getDataSourceFilter() {
        return dsf;
    }

    /**
     * used to explicitly set the rendering type.  This installs a das2 renderer
     * into the plot to implement the render type.
     * @param renderType
     */
    public void setRenderType(RenderType renderType) {
        if (this.parentPanel != null && !this.parentPanel.renderType.equals(renderType)) {
            this.parentPanel.setRenderType(renderType);
            return;
        }

        if (childPanels != null) { // kludge
            if (renderType == RenderType.spectrogram) {
                //RenderType.spectrogram changes the rank of the view, so we need to
                //treat it specially.

                this.childPanels = null;
                this.setResetRanges(true);
                this.resetPanel(dsf.getController().getFillDataSet(),renderType);

            } else {
                for (Panel p : this.childPanels) {
                    p.setRenderType(renderType);
                }
            }
        }

        Renderer oldRenderer = getRenderer();
        Renderer newRenderer = AutoplotUtil.maybeCreateRenderer(renderType, oldRenderer, getColorbar());

        QDataSet fillDs= dsf.getController().getFillDataSet();
        if ( newRenderer instanceof SeriesRenderer && fillDs!=null ) {
            QDataSet d= (QDataSet) fillDs.property(QDataSet.DEPEND_0);
            ((SeriesRenderer)newRenderer).setCadenceCheck( (d.property(QDataSet.CADENCE)!=null ) );
        }

        if (oldRenderer != newRenderer) {
            setRenderer(newRenderer);

            DasPlot plot = getDasPlot();

            if (oldRenderer != null) {
                plot.removeRenderer(oldRenderer);
            }
            plot.addRenderer(newRenderer);
            logger.finest("plot.addRenderer "+plot+" "+newRenderer);

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
        ac.bind(panel, "style.antiAliased", seriesRenderer, "antiAliased");

    }

    public void bindToSpectrogramRenderer(SpectrogramRenderer spectrogramRenderer) {
        ApplicationController ac = this.dom.getController();

        ac.bind(panel, "style.rebinMethod", spectrogramRenderer, "rebinner");
        ac.bind(panel, "style.colortable", spectrogramRenderer, "colorBar.type");

    }

    public void bindToImageVectorDataSetRenderer(ImageVectorDataSetRenderer renderer) {
        ApplicationController ac = this.dom.getController();
        ac.bind(panel, "style.color", renderer, "color");
        ac.bind(panel, Panel.PROP_LEGENDLABEL, renderer, Renderer.PROP_LEGENDLABEL);
        ac.bind(panel, Panel.PROP_DISPLAYLEGEND, renderer, Renderer.PROP_DRAWLEGENDLABEL);
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

    public String toString() {
        return "" + this.panel + " controller";
    }
}
