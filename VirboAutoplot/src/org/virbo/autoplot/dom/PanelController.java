/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.virbo.autoplot.ApplicationModel.RenderType;
import org.virbo.autoplot.AutoplotUtil;
import org.virbo.autoplot.util.DateTimeDatumFormatter;
import org.virbo.dataset.DataSetAdapter;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;

/**
 * PanelController manages the Panel, for example resolving the datasource and loading the dataset.
 * @author jbf
 */
public class PanelController {

    private static final String PENDING_RESET_RANGE = "resetRanges";
    Logger logger = Logger.getLogger("vap.panelController");
    private Application dom;
    private Panel panel;
    private Set<String> pendingChanges = new HashSet<String>();
    private PropertyChangeSupport propertyChangeSupport = new DebugPropertyChangeSupport(this);
    /**
     * switch over between fine and course points.
     */
    public static int SYMSIZE_DATAPOINT_COUNT = 500;

    public PanelController(final Application dom, final Panel panel) {
        panel.controller = this;
        this.dom = dom;
        this.panel = panel;

        panel.addPropertyChangeListener(Panel.PROP_RENDERTYPE, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                setRenderType(panel.getRenderType());
            }
        });


        final DataSourceFilter dsf = panel.getDataSourceFilter();

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

        dsf.addPropertyChangeListener(DataSourceFilter.PROP_FILLDATASET, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                QDataSet fillDs = dsf.getFillDataSet();
                if (fillDs != null && resetRanges) {
                    doResetRanges(true);
                    setResetRanges(false);
                }
                if (fillDs == null) {
                    if (getRenderer() != null) {
                        getRenderer().setDataSet(null);
                    }
                } else {
                    if (getRenderer() != null) {
                        getRenderer().setDataSet(DataSetAdapter.createLegacyDataSet(fillDs));
                    }

                    final DataSourceFilter dsf = panel.getDataSourceFilter();
                    String reduceRankString = dsf.getReduceDataSetString();
                    if (dsf.getReduceDataSetString() != null) { // kludge to update title
                        String title = dom.getPlot().getTitle();
                        Pattern p = Pattern.compile("(.*)!c(.+)=(.+)");
                        Matcher m = p.matcher(title);
                        if (m.matches()) {
                            title = m.group(1) + "!c" + reduceRankString;
                            getPlot().setTitle(title);
                        }
                    }
                }
            }
        });

        dsf.addPropertyChangeListener(DataSourceFilter.PROP_DATASOURCE, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                setResetRanges(true);
            }
        });

        dsf.addPropertyChangeListener(Panel.PROP_RENDERTYPE, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                setRenderType(panel.getRenderType());
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
        panel.getDataSourceFilter().setSuri(surl);
    }
    protected Renderer renderer = null;

    protected Renderer getRenderer() {
        return renderer;
    }

    protected void setRenderer(Renderer renderer) {
        this.renderer = renderer;
        if (renderer instanceof SeriesRenderer) {
            panel.bindTo((SeriesRenderer) renderer);
            panel.bindTo(new SpectrogramRenderer(null, null));
        } else {
            panel.bindTo((SpectrogramRenderer) renderer);
            panel.bindTo(new SeriesRenderer());
        }
    }

    private synchronized void doResetRanges(boolean autorange) {

        pendingChanges.add(PENDING_RESET_RANGE);

        DataSourceFilter dsf = panel.getDataSourceFilter();

        RenderType renderType = AutoplotUtil.getRenderType(dsf.getFillDataSet());
        panel.setRenderType(renderType);

        this.setRenderer(autorange, true);

        Panel panelCopy = (Panel) panel.copy();

        doMetadata(panelCopy, autorange, true);

        doAutoranging(panelCopy);

        panel.syncTo(panelCopy);

        dom.getController().getPlotFor(panel).syncTo(panel.getPlotDefaults());

        setStatus("done, apply fill and autorange");
        pendingChanges.remove(PENDING_RESET_RANGE);
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
        final DataSourceFilter dsf = panel.getDataSourceFilter();
        QDataSet fillDs = dsf.getFillDataSet();
        Map<String, Object> properties = dsf.getFillProperties();

        RenderType renderType = panel.getRenderType();


        /* begin interpret metadata */
        if (interpretMetadata && autorange) {

            if (dom.isAutolabelling()) {
                panelCopy.getPlotDefaults().getXaxis().setLabel("");
                panelCopy.getPlotDefaults().getYaxis().setLabel("");
                panelCopy.getPlotDefaults().getZaxis().setLabel("");
                panelCopy.getPlotDefaults().setTitle("");
            }

            doInterpretMetadata(panelCopy, properties, panel.getRenderType());

            String reduceRankString = panel.getDataSourceFilter().getReduceDataSetString();
            if (dsf.getReduceDataSetString() != null) {
                String title = panelCopy.getPlotDefaults().getTitle();
                title += "!c" + reduceRankString;
                panelCopy.getPlotDefaults().setTitle(title);
            }

            PanelUtil.unitsCheck(properties, fillDs); // DANGER--this may cause overplotting problems in the future by removing units

            panel.syncTo(panelCopy);

        } else {
            // kludge to support updating slice location report without autoranging.
            // I don't think it's coming into this dead code.
            if (dsf.getReduceDataSetString() != null) {
                panelCopy.getPlotDefaults().setTitle("");
                doInterpretMetadata(panelCopy, properties, renderType);

            }
        }


    }

    private void doInterpretMetadata(Panel cpanel, Map properties, RenderType spec) {

        Object v;
        if ((v = properties.get(QDataSet.TITLE)) != null) {
            cpanel.getPlotDefaults().setTitle((String) v);
        }

        if (spec == RenderType.spectrogram) {
            if (dom.isAutoranging() && (v = properties.get(QDataSet.SCALE_TYPE)) != null) {
                cpanel.getPlotDefaults().getZaxis().setLog(v.equals("log"));
            }

            if (dom.isAutolabelling() && (v = properties.get(QDataSet.LABEL)) != null) {
                cpanel.getPlotDefaults().getZaxis().setLabel((String) v);
            }

            if (dom.isAutolabelling() && (v = properties.get(QDataSet.DEPEND_1)) != null) {
                Map m = (Map) v;
                Object v2 = m.get(QDataSet.LABEL);
                if (v2 != null) {
                    cpanel.getPlotDefaults().getYaxis().setLabel((String) v2);
                }

            }
        } else {
            if (dom.isAutoranging() && (v = properties.get(QDataSet.SCALE_TYPE)) != null) {
                cpanel.getPlotDefaults().getYaxis().setLog(v.equals("log"));
            }

            if (dom.isAutolabelling() && (v = properties.get(QDataSet.LABEL)) != null) {
                cpanel.getPlotDefaults().getYaxis().setLabel((String) v);
            }

            cpanel.getPlotDefaults().getZaxis().setLabel("");
        }

        if ((v = properties.get(QDataSet.DEPEND_0)) != null) {
            Map m = (Map) v;
            Object v2 = m.get(QDataSet.LABEL);
            if (dom.isAutolabelling() && v2 != null) {
                cpanel.getPlotDefaults().getXaxis().setLabel((String) v2);
            }

        }

    }

    private void guessCadence( MutablePropertyDataSet xds, QDataSet fillDs ) {
        Units xunits= (Units) xds.property( QDataSet.UNITS );
            if ( xunits==null ) xunits= Units.dimensionless;

            Double cadence = DataSetUtil.guessCadence(xds, fillDs);
            if ( cadence==null && !UnitsUtil.isTimeLocation(xunits)) {
                cadence= DataSetUtil.guessCadence( Ops.log(xds),null );
                if ( cadence!=null ) {
                    ((MutablePropertyDataSet) xds).putProperty( QDataSet.SCALE_TYPE, "log" );
                }
            }
            ((MutablePropertyDataSet) xds).putProperty(QDataSet.CADENCE, cadence);   
    }
    
    /**
     * this is the old updateFillSeries and updateFillSpectrogram code.  This calculates
     * ranges and preferred symbol settings, and puts the values in cpanel.plotDefaults.
     * @param cpanel
     * @param props
     * @param spec
     */
    private void doAutoranging(Panel cpanel) {
        Map props = cpanel.getDataSourceFilter().getFillProperties();
        RenderType spec = cpanel.getRenderType();

        if (props == null) {
            props = Collections.EMPTY_MAP;
        }

        QDataSet fillDs = panel.getDataSourceFilter().getFillDataSet();

        if (spec == RenderType.spectrogram) {

            QDataSet xds = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
            if (xds == null) {
                xds = DataSetUtil.indexGenDataSet(fillDs.length());
            }

            QDataSet yds = (QDataSet) fillDs.property(QDataSet.DEPEND_1);
            if (yds == null) {
                yds = DataSetUtil.indexGenDataSet(fillDs.length(0)); // QUBE
            }
            
            guessCadence( (MutablePropertyDataSet)xds, fillDs ); 
            guessCadence( (MutablePropertyDataSet)yds, null );
            
            AutoplotUtil.AutoRangeDescriptor xdesc = AutoplotUtil.autoRange(xds, (Map) props.get(QDataSet.DEPEND_0));

            AutoplotUtil.AutoRangeDescriptor ydesc = AutoplotUtil.autoRange(yds, (Map) props.get(QDataSet.DEPEND_1));

            AutoplotUtil.AutoRangeDescriptor desc = AutoplotUtil.autoRange(fillDs, props);

            cpanel.getPlotDefaults().getZaxis().setRange(desc.range);
            cpanel.getPlotDefaults().getZaxis().setLog(desc.log);
            cpanel.getPlotDefaults().getXaxis().setLog(xdesc.log);
            cpanel.getPlotDefaults().getXaxis().setRange(xdesc.range);
            cpanel.getPlotDefaults().getYaxis().setLog(ydesc.log);
            cpanel.getPlotDefaults().getYaxis().setRange(ydesc.range);

        } else {

            boolean isSeries;
            QDataSet depend0 = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
            isSeries = (depend0 == null || DataSetUtil.isMonotonic(depend0));
            if (isSeries) {
                cpanel.getStyle().setSymbolConnector(PsymConnector.SOLID);
            } else {
                cpanel.getStyle().setSymbolConnector(PsymConnector.NONE);
            }

            cpanel.getStyle().setLineWidth(1.0f);

            AutoplotUtil.AutoRangeDescriptor desc = AutoplotUtil.autoRange(fillDs, props);

            QDataSet xds = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
            if (xds == null) {
                xds = DataSetUtil.indexGenDataSet(fillDs.length());
            }

            guessCadence( (MutablePropertyDataSet)xds, fillDs);

            AutoplotUtil.AutoRangeDescriptor xdesc = AutoplotUtil.autoRange(xds, (Map) props.get(QDataSet.DEPEND_0));

            cpanel.getPlotDefaults().getYaxis().setLog(desc.log);
            cpanel.getPlotDefaults().getYaxis().setRange(desc.range);

            cpanel.getPlotDefaults().getXaxis().setLog(xdesc.log);
            cpanel.getPlotDefaults().getXaxis().setRange(xdesc.range);

            if (fillDs.length() > 30000) {
                cpanel.getStyle().setSymbolConnector(PsymConnector.NONE);
                cpanel.getStyle().setSymbolSize(1.0);
            } else {
                cpanel.getStyle().setPlotSymbol(DefaultPlotSymbol.CIRCLES);
                if (fillDs.length() > SYMSIZE_DATAPOINT_COUNT) {
                    cpanel.getStyle().setSymbolSize(1.0);
                } else {
                    cpanel.getStyle().setSymbolSize(3.0);
                }

            }

        }
    }

    /**
     * get the plotDefaults object for this panel.
     * @return
     */
    public DasPlot getPlot() {
        return dom.getController().getPlotFor(panel).getController().getDasPlot();
    }

    private DasColorBar getColorbar() {
        return dom.getController().getPlotFor(panel).getController().getDasColorBar();
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
     * @param renderType
     */
    public void setRenderType(RenderType renderType) {

        // getRenderers sets the dataset.
        List<Renderer> rs = AutoplotUtil.getRenderers(panel.getDataSourceFilter().getFillDataSet(),
                renderType, Collections.singletonList(getRenderer()), getColorbar());

        assert rs.size() == 1;

        setRenderer(rs.get(0));

        DasPlot plot = getPlot();

        Renderer[] rends = plot.getRenderers();
        for (int i = 0; i < rends.length; i++) {
            plot.removeRenderer(rends[i]);
        }
        plot.addRenderer(rs.get(0));

    }

    public boolean isValueAdjusting() {
        return false;
    }

    public boolean isPendingChanges() {
        return panel.getDataSourceFilter().getController().isPendingChanges() || pendingChanges.size() > 0;
    }

    private void setStatus(String string) {
        this.dom.getController().setStatus(string);
    }
}
