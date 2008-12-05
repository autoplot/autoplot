/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.beans.binding.BindingContext;
import org.das2.graph.DasColorBar;
import org.das2.graph.DasPlot;

/**
 *
 * @author jbf
 */
public class Plot extends DomNode {

    public Plot() {
        PropertyChangeListener childListener = new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                Plot.this.propertyChangeSupport.firePropertyChange( promoteChild(evt) );
            }
        };
        xaxis.addPropertyChangeListener(childListener);
        yaxis.addPropertyChangeListener(childListener);
        zaxis.addPropertyChangeListener(childListener);
    }
    
    private PropertyChangeEvent promoteChild( PropertyChangeEvent ev ) {
        String childName;
        if ( ev.getSource()==xaxis ) {
            childName= "xaxis";
        } else if ( ev.getSource()==yaxis ) {
            childName= "yaxis";
        } else if ( ev.getSource()==zaxis ) {
            childName= "zaxis";
        } else {
            throw new IllegalArgumentException("child not found");
        }
        return new PropertyChangeEvent( this, childName+"."+ev.getPropertyName(), ev.getOldValue(), ev.getNewValue() );
    }
    
    protected Axis xaxis = new Axis();
    public static final String PROP_XAXIS = "xaxis";

    public Axis getXaxis() {
        return xaxis;
    }

    public void setXaxis(Axis xaxis) {
        Axis oldXaxis = this.xaxis;
        this.xaxis = xaxis;
        propertyChangeSupport.firePropertyChange(PROP_XAXIS, oldXaxis, xaxis);
    }
    protected Axis yaxis = new Axis();
    public static final String PROP_YAXIS = "yaxis";

    public Axis getYaxis() {
        return yaxis;
    }

    public void setYaxis(Axis yaxis) {
        Axis oldYaxis = this.yaxis;
        this.yaxis = yaxis;
        propertyChangeSupport.firePropertyChange(PROP_YAXIS, oldYaxis, yaxis);
    }
    protected Axis zaxis = new Axis();
    public static final String PROP_ZAXIS = "zaxis";

    public Axis getZaxis() {
        return zaxis;
    }

    public void setZaxis(Axis zaxis) {
        Axis oldZaxis = this.zaxis;
        this.zaxis = zaxis;
        propertyChangeSupport.firePropertyChange(PROP_ZAXIS, oldZaxis, zaxis);
    }
    
    
    protected String title = "";
    /**
     * title for the plot. 
     */
    public static final String PROP_TITLE = "title";

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        String oldTitle = this.title;
        this.title = title;
        propertyChangeSupport.firePropertyChange(PROP_TITLE, oldTitle, title);
    }

    protected boolean isotropic = false;

    public static final String PROP_ISOTROPIC = "isotropic";

    public boolean isIsotropic() {
        return isotropic;
    }

    public void setIsotropic(boolean isotropic) {
        boolean oldIsotropic = this.isotropic;
        this.isotropic = isotropic;
        propertyChangeSupport.firePropertyChange(PROP_ISOTROPIC, oldIsotropic, isotropic);
    }
    
    
    
    protected PlotController controller;
    
    public PlotController getController() {
        return controller;
    }

    @Override
    public DomNode copy() {
        Plot result= (Plot) super.copy();
        result.controller= null;
        return result;
    }
    
    
    public void syncTo(DomNode n) {
        Plot that = (Plot) n;
        this.setTitle( that.getTitle() );
        this.setIsotropic( that.isIsotropic() );
        this.xaxis.syncTo(that.getXaxis());
        this.yaxis.syncTo(that.getYaxis());
        this.zaxis.syncTo(that.getZaxis());
    }
    
    BindingContext plotBindingContext = null;

    public synchronized void bindTo(DasPlot p) {
        if (plotBindingContext != null) plotBindingContext.unbind();
        plotBindingContext = new BindingContext();
        plotBindingContext.addBinding( this, "${title}", p, "title" );
        plotBindingContext.bind();
        xaxis.bindTo(p.getXAxis());
        yaxis.bindTo(p.getYAxis());
    }
    BindingContext colorbarBindingContext = null;

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    public synchronized void bindTo(DasColorBar colorbar) {
        if (colorbarBindingContext != null) colorbarBindingContext.unbind();
        colorbarBindingContext = new BindingContext();
        colorbarBindingContext.addBinding(this, "${zaxis.range}", colorbar, "datumRange");
        colorbarBindingContext.addBinding(this, "${zaxis.log}", colorbar, "log");
        colorbarBindingContext.addBinding(this, "${zaxis.label}", colorbar, "label" );
        colorbarBindingContext.bind();
    }

    public Map<String, String> diffs(DomNode node) {

        Plot that = (Plot) node;
        LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();

        boolean b;
        
        b=  that.title.equals(this.title) ;
        if ( !b ) result.put("title", that.title + " to " + this.title );
        
        Map<String, String> diffs1 = this.getXaxis().diffs(that.getXaxis());
        for (String k : diffs1.keySet()) {
            result.put("xaxis." + k, diffs1.get(k));
        }

        diffs1 = this.getYaxis().diffs(that.getYaxis());
        for (String k : diffs1.keySet()) {
            result.put("yaxis." + k, diffs1.get(k));
        }

        diffs1 = this.getZaxis().diffs(that.getZaxis());
        for (String k : diffs1.keySet()) {
            result.put("zaxis." + k, diffs1.get(k));
        }
        return result;
    }
}
