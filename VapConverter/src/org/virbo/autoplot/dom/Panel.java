/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.util.ArrayList;
import java.util.List;
import org.virbo.autoplot.RenderType;

/**
 *
 * @author jbf
 */
public class Panel extends DomNode {

    public Panel() {

    }

    /**
     * reference to the datasource of this panel.  Several panels can share the same data source.
     */
    protected String dataSourceFilterId;
    public static final String PROP_DATASOURCEFILTERID = "dataSourceFilterId";

    public String getDataSourceFilterId() {
        return dataSourceFilterId;
    }

    public void setDataSourceFilterId(String dataSourceFilterId) {
        String oldDataSourceFilterId = this.dataSourceFilterId;
        this.dataSourceFilterId = dataSourceFilterId;
        propertyChangeSupport.firePropertyChange(PROP_DATASOURCEFILTERID, oldDataSourceFilterId, dataSourceFilterId);
    }

    protected PanelStyle style = new PanelStyle();
    public static final String PROP_STYLE = "style";

    public PanelStyle getStyle() {
        return style;
    }

    public void setStyle(PanelStyle style) {
        PanelStyle oldStyle = this.style;
        this.style = style;
        propertyChangeSupport.firePropertyChange(PROP_STYLE, oldStyle, style);
    }
    
    /**
     * preferred settings for the panel.
     */
    protected Plot plotDefaults = new Plot();
    public static final String PROP_PLOT_DEFAULTS = "plotDefaults";

    public Plot getPlotDefaults() {
        return plotDefaults;
    }

    public void setPlotDefaults(Plot plot) {
        Plot oldPlot = this.plotDefaults;
        this.plotDefaults = plot;
        propertyChangeSupport.firePropertyChange(PROP_PLOT_DEFAULTS, oldPlot, plot);
    }
    protected RenderType renderType = RenderType.series;
    public static final String PROP_RENDERTYPE = "renderType";

    public RenderType getRenderType() {
        return renderType;
    }

    public void setRenderType(RenderType renderType) {
        RenderType oldRenderType = this.renderType;
        this.renderType = renderType;
        propertyChangeSupport.firePropertyChange(PROP_RENDERTYPE, oldRenderType, renderType);
        this.setAutoRenderType(false);
    }

    public void setRenderTypeAutomatically( RenderType renderType ) {
        RenderType oldRenderType = this.renderType;
        this.renderType = renderType;
        propertyChangeSupport.firePropertyChange(PROP_RENDERTYPE, oldRenderType, renderType);
        this.setAutoRenderType(true);
    }
    /**
     * id of the plotDefaults containing the panel.
     */
    protected String plotId = null;
    public static final String PROP_PLOTID = "plotId";

    public String getPlotId() {
        return plotId;
    }

    public void setPlotId(String plotId) {
        String oldPlotId = this.plotId;
        this.plotId = plotId;
        propertyChangeSupport.firePropertyChange(PROP_PLOTID, oldPlotId, plotId);
    }
    
    protected String parentPanel = "";
    /**
     * id of this panel's parent, which groups the panels into one abstract
     * plot.  The parent and children must share the same dataSourceFilter.
     */
    public static final String PROP_PARENTPANEL = "parentPanel";

    public String getParentPanel() {
        return parentPanel;
    }

    public void setParentPanel(String parentPanel) {
        String oldParentPanel = this.parentPanel;
        this.parentPanel = parentPanel;
        propertyChangeSupport.firePropertyChange(PROP_PARENTPANEL, oldParentPanel, parentPanel);
    }


    /**
     * selects the component of the dataset to plot, such as "X" or "MAGNITUDE".  These
     * component names come from the dataset labels.  Canonical names are 
     * X, Y, Z, MAGNITUDE.  A use case to consider is automatic coordinate frame 
     * conversions.
     */
    protected String component="";
    public static final String PROP_COMPONENT = "component";

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        String oldComponent = this.component;
        this.component = component;
        propertyChangeSupport.firePropertyChange(PROP_COMPONENT, oldComponent, component);
        this.setAutoComponent(false);
    }

    public void setComponentAutomatically( String component ) {
        String oldComponent = this.component;
        this.component = component;
        propertyChangeSupport.firePropertyChange(PROP_COMPONENT, oldComponent, component);
        this.setAutoComponent(true);
    }
    
     /**
     * A label to (optionally) display in the plot legend.  This string will be
     * rendered by the GrannyTextRenderer.
     */
    protected String legendLabel="";
    public static final String PROP_LEGENDLABEL = "legendLabel";

    public String getLegendLabel() {
        return legendLabel;
    }

    public void setLegendLabel(String legendLabel) {
        String oldLegendLabel = this.legendLabel;
        this.legendLabel = legendLabel;
        propertyChangeSupport.firePropertyChange(PROP_LEGENDLABEL, oldLegendLabel, legendLabel);
        this.setAutoLabel( false );
    }

    public void setLegendLabelAutomatically( String legendLabel ) {
        String oldLegendLabel = this.legendLabel;
        this.legendLabel = legendLabel;
        propertyChangeSupport.firePropertyChange(PROP_LEGENDLABEL, oldLegendLabel, legendLabel);
        this.setAutoLabel( true );
    }

    /**
     * display the plot legend.
     */
    protected boolean displayLegend = false;
    public static final String PROP_DISPLAYLEGEND = "displayLegend";

    public boolean isDisplayLegend() {
        return displayLegend;
    }
    
    public void setDisplayLegend(boolean displayLegend) {
        boolean oldDisplayLegend = this.displayLegend;
        this.displayLegend = displayLegend;
        propertyChangeSupport.firePropertyChange(PROP_DISPLAYLEGEND, oldDisplayLegend, displayLegend);
    }

    /**
     * true indicates the axis label hasn't been changed manually and may/should be set automatically.
     */
    public static final String PROP_AUTOLABEL = "autoLabel";
    protected boolean autoLabel = false;

    public boolean isAutoLabel() {
        return autoLabel;
    }

    public void setAutoLabel(boolean autolabel) {
        boolean oldAutolabel = this.autoLabel;
        this.autoLabel = autolabel;
        propertyChangeSupport.firePropertyChange(PROP_AUTOLABEL, oldAutolabel, autolabel);
    }

    /**
     * true indicates that the renderType hasn't been changed manually and may/should be set automatically.
     */
    public static final String PROP_AUTORENDERTYPE = "autoRenderType";

    protected boolean autoRenderType = false;

    public boolean isAutoRenderType() {
        return autoRenderType;
    }

    public void setAutoRenderType(boolean autoRenderType) {
        boolean oldAutoRenderType = this.autoRenderType;
        this.autoRenderType = autoRenderType;
        propertyChangeSupport.firePropertyChange(PROP_AUTORENDERTYPE, oldAutoRenderType, autoRenderType);
    }

    /**
     * true indicates that the component hasn't been changed manually and may/should be set automatically.
     * This is also used to determine if child panels should be added automatically.
     */
    protected boolean autoComponent = false;
    public static final String PROP_AUTOCOMPONENT = "autoComponent";

    public boolean isAutoComponent() {
        return autoComponent;
    }

    public void setAutoComponent(boolean autoComponent) {
        boolean oldAutoComponent = this.autoComponent;
        this.autoComponent = autoComponent;
        propertyChangeSupport.firePropertyChange(PROP_AUTOCOMPONENT, oldAutoComponent, autoComponent);
    }

    /**
     * display the plot.  This is allows panels to be disabled without removing them from the application.
     */
    protected boolean active = true;
    public static final String PROP_ACTIVE = "active";

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        boolean oldActive = this.active;
        this.active = active;
        propertyChangeSupport.firePropertyChange(PROP_ACTIVE, oldActive, active);
    }



}
