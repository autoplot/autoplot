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
public class PlotElement extends DomNode {

    public PlotElement() {

    }

    /**
     * reference to the datasource of this element.  Several elements can share the same data source.
     */
    protected String dataSourceFilterId="";
    public static final String PROP_DATASOURCEFILTERID = "dataSourceFilterId";

    public String getDataSourceFilterId() {
        return dataSourceFilterId;
    }

    public void setDataSourceFilterId(String dataSourceFilterId) {
        String oldDataSourceFilterId = this.dataSourceFilterId;
        this.dataSourceFilterId = dataSourceFilterId;
        propertyChangeSupport.firePropertyChange(PROP_DATASOURCEFILTERID, oldDataSourceFilterId, dataSourceFilterId);
    }

    protected PlotElementStyle style = new PlotElementStyle();
    public static final String PROP_STYLE = "style";

    public PlotElementStyle getStyle() {
        return style;
    }

    public void setStyle(PlotElementStyle style) {
        PlotElementStyle oldStyle = this.style;
        this.style = style;
        propertyChangeSupport.firePropertyChange(PROP_STYLE, oldStyle, style);
    }
    
    /**
     * preferred settings for the element.
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
     * additional properties for the renderer, e.g. rend+series:fill=red,above,5.0V;grey,below,0.0V
     */
    protected String renderControl = "";
    public static final String PROP_RENDERCONTROL = "renderControl";

    public String getRenderControl() {
        return renderControl;
    }

    public void setRenderControl(String renderUri) {
        String oldRenderUri = this.renderControl;
        this.renderControl = renderUri;
        propertyChangeSupport.firePropertyChange(PROP_RENDERCONTROL, oldRenderUri, renderUri);
    }


    /**
     * cadence check disabled will always connect valid points.
     */
    protected boolean cadenceCheck = true;
    public static final String PROP_CADENCECHECK = "cadenceCheck";

    public boolean isCadenceCheck() {
        return cadenceCheck;
    }

    public void setCadenceCheck(boolean cadenceCheck) {
        boolean oldCadenceCheck = this.cadenceCheck;
        this.cadenceCheck = cadenceCheck;
        propertyChangeSupport.firePropertyChange(PROP_CADENCECHECK, oldCadenceCheck, cadenceCheck);
    }


    /**
     * id of the plotDefaults containing the element.
     */
    protected String plotId = "";
    public static final String PROP_PLOTID = "plotId";

    public String getPlotId() {
        return plotId;
    }

    public void setPlotId(String plotId) {
        String oldPlotId = this.plotId;
        this.plotId = plotId;
        propertyChangeSupport.firePropertyChange(PROP_PLOTID, oldPlotId, plotId);
    }
    
    protected String parent = "";
    /**
     * id of this plotElement's parent, which groups the plotElements into one abstract
     * plot.  The parent and children must share the same dataSourceFilter.
     */
    public static final String PROP_PARENT = "parent";

    public String getParent() {
        return parent;
    }

    public void setParent(String parent) {
        String old = this.parent;
        this.parent = parent;
        propertyChangeSupport.firePropertyChange(PROP_PARENT, old, parent);
    }


    /**
     * selects the component of the dataset to plot, such as "X" or "MAGNITUDE".  These
     * component names come from the dataset labels.  Canonical names are 
     * X, Y, Z, MAGNITUDE.  A use case to consider is automatic coordinate frame 
     * conversions.
     * If this starts with a pipe (|), then this is an "sprocess" string to run on
     * the data, such as "|slice0(0)" or "|histogram()"
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

    /**
     * Set the property, and also set the autoComponent property.  Note setComponent will clear the property.
     * TODO: this should probably be in the controller.
     * @param component
     */
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
     * This is also used to determine if child elements should be added automatically.
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
     * display the plot.  This is allows elements to be disabled without removing them from the application.
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


    PlotElementController controller;

    public PlotElementController getController() {
        return controller;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public DomNode copy() {
        PlotElement result = (PlotElement) super.copy();
        result.controller= null;
        result.style = (PlotElementStyle) style.copy();
        result.plotDefaults = (Plot) plotDefaults.copy();
        result.cadenceCheck= cadenceCheck;
        return result;
    }

    @Override
    public List<Diff> diffs(DomNode node) {
        
        List<Diff> result = super.diffs(node);
        
        PlotElement that = (PlotElement) node;

        if ( !that.plotId.equals( this.plotId ) ) {
            result.add( new PropertyChangeDiff( PROP_PLOTID, that.plotId, this.plotId ) );
        }

        if ( !that.legendLabel.equals( this.legendLabel ) ) {
            result.add( new PropertyChangeDiff( PROP_LEGENDLABEL, that.legendLabel, this.legendLabel ) );
        }

        if ( that.autoLabel!=this.autoLabel ) {
            result.add( new PropertyChangeDiff( PROP_AUTOLABEL, that.autoLabel, this.autoLabel ) );
        }

        if ( that.autoRenderType!=this.autoRenderType ) {
            result.add( new PropertyChangeDiff( PROP_AUTORENDERTYPE, that.autoRenderType, this.autoRenderType ) );
        }

        if ( that.autoComponent!=this.autoComponent ) {
            result.add( new PropertyChangeDiff( PROP_AUTOCOMPONENT, that.autoComponent, this.autoComponent ) );
        }
        if ( !that.displayLegend==this.displayLegend ) {
            result.add( new PropertyChangeDiff( PROP_DISPLAYLEGEND, that.displayLegend, this.displayLegend ) );
        }

        if ( !that.active==this.active ) {
            result.add( new PropertyChangeDiff( PROP_ACTIVE, that.active, this.active ) );
        }

        if ( !that.renderType.equals(this.renderType) ) {
            result.add( new PropertyChangeDiff( PROP_RENDERTYPE, that.renderType, this.renderType ) );
        }

        if ( !( that.renderControl.equals(this.renderControl) )  ) {
            result.add( new PropertyChangeDiff(  PROP_RENDERCONTROL, that.renderControl, this.renderControl ) );
        }

        if ( !that.dataSourceFilterId.equals( this.dataSourceFilterId ) ) {
            result.add( new PropertyChangeDiff( PROP_DATASOURCEFILTERID, that.dataSourceFilterId, this.dataSourceFilterId ) );
        }

        if ( !that.component.equals( this.component ) ) {
            result.add( new PropertyChangeDiff(  PROP_COMPONENT, that.component, this.component ) );
        }

        if ( !( that.cadenceCheck==this.cadenceCheck )  ) {
            result.add( new PropertyChangeDiff(  PROP_CADENCECHECK, that.cadenceCheck, this.cadenceCheck ) );
        }

        //TODO: we don't do anything with parent property, seems we should have code like the
        //   following.  It could be super.diffs is really doing all the work.
        //if ( !that.parent.equals( this.parent ) ) {
        //    result.add( new PropertyChangeDiff(  PROP_PARENT, that.parent, this.parent ) );
        //}
        
        result.addAll( DomUtil.childDiffs( PROP_STYLE, this.getStyle().diffs(that.getStyle()) ) );
        result.addAll( DomUtil.childDiffs( PROP_PLOT_DEFAULTS, this.getPlotDefaults().diffs(that.getPlotDefaults()) ) );
   
        return result;
    }

    @Override
    public void syncTo(DomNode n) {
        super.syncTo(n);
        syncTo( n, new ArrayList<String>() );
    }
    
    @Override
    public void syncTo( DomNode n, List<String> exclude ) {
        super.syncTo(n,exclude);
        PlotElement that = (PlotElement) n;
        if ( !exclude.contains( PROP_PLOTID ) ) this.setPlotId(that.getPlotId());
        if ( !exclude.contains( PROP_DATASOURCEFILTERID ) ) this.setDataSourceFilterId(that.getDataSourceFilterId());
        if ( !exclude.contains( PROP_PARENT ) ) this.setParent(that.getParent());
        if ( !exclude.contains( PROP_LEGENDLABEL ) ) this.setLegendLabel(that.getLegendLabel());
        if ( !exclude.contains( PROP_DISPLAYLEGEND ) ) this.setDisplayLegend(that.isDisplayLegend());
        if ( !exclude.contains( PROP_ACTIVE ) ) this.setActive(that.isActive());
        if ( !exclude.contains( PROP_RENDERTYPE ) ) this.setRenderType( that.getRenderType() );
        if ( !exclude.contains( PROP_RENDERCONTROL ) ) this.setRenderControl( that.getRenderControl() );
        if ( !exclude.contains( PROP_AUTOLABEL ) ) this.setAutoLabel(that.isAutoLabel());
        if ( !exclude.contains( PROP_AUTORENDERTYPE ) ) this.setAutoRenderType(that.isAutoRenderType());
        if ( !exclude.contains( PROP_STYLE ) ) this.style.syncTo(that.style,exclude); // possibly exclude id's.
        if ( !exclude.contains( PROP_PLOT_DEFAULTS ) )this.plotDefaults.syncTo(that.plotDefaults,exclude);
        if ( !exclude.contains( PROP_COMPONENT ) ) this.setComponent(that.getComponent());
        if ( !exclude.contains( PROP_AUTOCOMPONENT ) ) this.setAutoComponent(that.isAutoComponent());
        if ( !exclude.contains( PROP_CADENCECHECK ) ) this.setCadenceCheck(that.isCadenceCheck() );
    }

    @Override
    public String toString() {
        StringBuilder parenthetical= new StringBuilder( getLegendLabel()==null ? "" : getLegendLabel() );
        if ( !this.active ) {
            parenthetical.append( parenthetical.length()==0 ? "inactive" : ", inactive" );
        }
        String l= parenthetical.length()==0 ? "" : " ("+parenthetical.toString()+")";
        return super.toString() + l;
    }


}
