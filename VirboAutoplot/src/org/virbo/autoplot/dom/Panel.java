/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import org.virbo.autoplot.ApplicationModel.RenderType;

/**
 *
 * @author jbf
 */
public class Panel extends DomNode {

    public Panel() {
        PropertyChangeListener childListener = new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                Panel.this.propertyChangeSupport.firePropertyChange(promoteChild(evt));
            }
        };
        style.addPropertyChangeListener(childListener);

    }

    private PropertyChangeEvent promoteChild(PropertyChangeEvent ev) {
        String childName;
        if (ev.getSource() == style) {
            childName = "style";
        } else if (ev.getSource() == plotDefaults) {
            childName = "plotDefaults";
        } else {
            throw new IllegalArgumentException("child not found");
        }
        return new PropertyChangeEvent(this, childName + "." + ev.getPropertyName(), ev.getOldValue(), ev.getNewValue());
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
    protected RenderType renderType = RenderType.spectrogram;
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

    
    
    PanelController controller;

    public PanelController getController() {
        return controller;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    public DomNode copy() {
        Panel result = (Panel) super.copy();
        result.controller= null;
        result.style = (PanelStyle) style.copy();
        result.plotDefaults = (Plot) plotDefaults.copy();
        return result;
    }

    public List<Diff> diffs(DomNode node) {
        
        List<Diff> result = new ArrayList<Diff>();
        Panel that = (Panel) node;

        boolean b;

        if ( !that.plotId.equals( this.plotId ) ) {
            result.add( new PropertyChangeDiff( "plotId", that.plotId, this.plotId ) );
        }
        if ( !that.dataSourceFilterId.equals( this.dataSourceFilterId ) ) {
            result.add( new PropertyChangeDiff( "dataSourceFilterId", that.dataSourceFilterId, this.dataSourceFilterId ) );
        }
        
        result.addAll( DomUtil.childDiffs("style", this.getStyle().diffs(that.getStyle()) ) );
        result.addAll( DomUtil.childDiffs("plotDefaults", this.getPlotDefaults().diffs(that.getPlotDefaults()) ) );
   
        return result;
    }

    public void syncTo(DomNode n) {
        Panel that = (Panel) n;
        this.setPlotId(that.getPlotId());
        this.setDataSourceFilterId( that.getDataSourceFilterId() );
        this.style.syncTo(that.style);
        this.plotDefaults.syncTo(that.plotDefaults);
    }
    
    public void syncTo( DomNode n, List<String> exclude ) {
        Panel that = (Panel) n;
        if ( !exclude.contains("plotId") ) this.setPlotId(that.getPlotId());
        if ( !exclude.contains("dataSourceFilterId") ) this.setDataSourceFilterId(that.getDataSourceFilterId());
        this.style.syncTo(that.style);
        this.plotDefaults.syncTo(that.plotDefaults);
    }
    
}
