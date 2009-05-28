/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.util.ArrayList;
import java.util.List;
import org.virbo.autoplot.ApplicationModel.RenderType;

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


    PanelController controller;

    public PanelController getController() {
        return controller;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public DomNode copy() {
        Panel result = (Panel) super.copy();
        result.controller= null;
        result.style = (PanelStyle) style.copy();
        result.plotDefaults = (Plot) plotDefaults.copy();
        return result;
    }

    public List<Diff> diffs(DomNode node) {
        
        List<Diff> result = super.diffs(node);
        
        Panel that = (Panel) node;

        if ( !that.plotId.equals( this.plotId ) ) {
            result.add( new PropertyChangeDiff( "plotId", that.plotId, this.plotId ) );
        }

        if ( !that.legendLabel.equals( this.legendLabel ) ) {
            result.add( new PropertyChangeDiff( "legendLabel", that.legendLabel, this.legendLabel ) );
        }

        if ( !that.displayLegend==this.displayLegend ) {
            result.add( new PropertyChangeDiff( "displayLegend", that.displayLegend, this.displayLegend ) );
        }

        if ( !that.active==this.active ) {
            result.add( new PropertyChangeDiff( "active", that.active, this.active ) );
        }

        if ( !that.renderType.equals(this.renderType) ) {
            result.add( new PropertyChangeDiff( "renderType", that.renderType, this.renderType ) );
        }

        if ( !that.dataSourceFilterId.equals( this.dataSourceFilterId ) ) {
            result.add( new PropertyChangeDiff( "dataSourceFilterId", that.dataSourceFilterId, this.dataSourceFilterId ) );
        }

        if ( !that.component.equals( this.component ) ) {
            result.add( new PropertyChangeDiff(  PROP_COMPONENT, that.component, this.component ) );
        }
        
        result.addAll( DomUtil.childDiffs("style", this.getStyle().diffs(that.getStyle()) ) );
        result.addAll( DomUtil.childDiffs("plotDefaults", this.getPlotDefaults().diffs(that.getPlotDefaults()) ) );
   
        return result;
    }

    public void syncTo(DomNode n) {
        super.syncTo(n);
        syncTo( n, new ArrayList<String>() );
    }
    
    public void syncTo( DomNode n, List<String> exclude ) {
        super.syncTo(n,exclude);
        Panel that = (Panel) n;
        if ( !exclude.contains( PROP_PLOTID ) ) this.setPlotId(that.getPlotId());
        if ( !exclude.contains( PROP_DATASOURCEFILTERID ) ) this.setDataSourceFilterId(that.getDataSourceFilterId());
        if ( !exclude.contains( PROP_PARENTPANEL ) ) this.setParentPanel(that.getParentPanel());
        if ( !exclude.contains( PROP_COMPONENT ) ) this.setComponent(that.getComponent());
        if ( !exclude.contains( PROP_LEGENDLABEL ) ) this.setLegendLabel(that.getLegendLabel());
        if ( !exclude.contains( PROP_DISPLAYLEGEND ) ) this.setDisplayLegend(that.isDisplayLegend());
        if ( !exclude.contains( PROP_ACTIVE ) ) this.setActive(that.isActive());
        if ( !exclude.contains( PROP_RENDERTYPE ) ) this.setRenderType( that.getRenderType() );
        this.style.syncTo(that.style);
        this.plotDefaults.syncTo(that.plotDefaults);

    }

    @Override
    public String toString() {
        StringBuffer parenthetical= new StringBuffer( getLegendLabel()==null ? "" : getLegendLabel() );
        if ( !this.active ) {
            parenthetical.append( parenthetical.length()==0 ? "inactive" : ", inactive" );
        }
        String l= parenthetical.length()==0 ? "" : " ("+parenthetical.toString()+")";
        return super.toString() + l;
    }


}
