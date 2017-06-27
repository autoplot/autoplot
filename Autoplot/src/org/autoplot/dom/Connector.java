/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.dom;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Two plots are joined together to connect axes.  
 * The class is intended to be immutable, but because XMLDecoder is used it must be mutable.
 * @author jbf
 */
public class Connector extends DomNode  {
    
    /**
     * called during restore
     */
    public Connector() {
        this.plotA= "";
        this.plotB= "";
    }
    
    public Connector( String plotA, String plotB ) {
        this.plotA= plotA;
        this.plotB= plotB;
    }
    
    protected String plotA = null;
    public static final String PROP_PLOTA = "plotA";

    public String getPlotA() {
        return plotA;
    }

    public void setPlotA( String s ) {
        String old= this.plotB;
        this.plotA= s;
        propertyChangeSupport.firePropertyChange( PROP_PLOTA, old, s );
    }

    protected String plotB;
    public static final String PROP_PLOTB = "plotB";
    
    public String getPlotB() {
        return plotB;
    }
    
    public void setPlotB( String s ) {
        String old= this.plotB;
        this.plotB= s;
        propertyChangeSupport.firePropertyChange( PROP_PLOTB, old, s );
    }
    
    /**
     * true to turn off entirely the bottom curtain, so that
     * only the plot is connected.
     */
    private boolean bottomCurtain = true;
    public static final String PROP_BOTTOMCURTAIN = "bottomCurtain";

    public boolean isBottomCurtain() {
        return bottomCurtain;
    }

    public void setBottomCurtain(boolean bottomCurtain) {
        boolean oldBottomCurtain = this.bottomCurtain;
        this.bottomCurtain = bottomCurtain;
        propertyChangeSupport.firePropertyChange(PROP_BOTTOMCURTAIN, oldBottomCurtain, bottomCurtain);
    }

    /**
     * percent opacity of the bottom curtain.
     */
    private int curtainOpacityPercent = 80;
    public static final String PROP_CURTAINOPACITYPERCENT = "curtainOpacityPercent";

    public int getCurtainOpacityPercent() {
        return curtainOpacityPercent;
    }

    public void setCurtainOpacityPercent(int curtainOpacityPercent) {
        int oldCurtainOpacityPercent = this.curtainOpacityPercent;
        this.curtainOpacityPercent = curtainOpacityPercent;
        propertyChangeSupport.firePropertyChange(PROP_CURTAINOPACITYPERCENT, oldCurtainOpacityPercent, curtainOpacityPercent);
    }

    /**
     * true indicates the area between plots should be connected 
     */
    private boolean fill = false;
    public static final String PROP_FILL = "fill";

    public boolean isFill() {
        return fill;
    }

    public void setFill(boolean fill) {
        boolean oldFill = this.fill;
        this.fill = fill;
        propertyChangeSupport.firePropertyChange(PROP_FILL, oldFill, fill);
    }

    /**
     * the color for the filled area between plots
     */
    private Color fillColor = new Color(240, 240, 240);
    public static final String PROP_FILLCOLOR = "fillColor";

    public Color getFillColor() {
        return fillColor;
    }

    public void setFillColor(Color fillColor) {
        Color oldFillColor = this.fillColor;
        this.fillColor = fillColor;
        propertyChangeSupport.firePropertyChange(PROP_FILLCOLOR, oldFillColor, fillColor);
    }
    
    /**
     * the color of the filled area in the plot.  This can be none
     * to disable this.
     */
    private Color color = Color.LIGHT_GRAY;
    public static final String PROP_COLOR = "color";

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        Color oldColor = this.color;
        this.color = color;
        propertyChangeSupport.firePropertyChange(PROP_COLOR, oldColor, color);
    }

    /**
     * visible allows the component to be made invisible.
     */
    private boolean visible = true;
    public static final String PROP_VISIBLE = "visible";

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        boolean oldVisible = this.visible;
        this.visible = visible;
        propertyChangeSupport.firePropertyChange(PROP_VISIBLE, oldVisible, visible);
    }
    
    private ConnectorController controller = null;
    public static final String PROP_CONTROLLER = "controller";

    public ConnectorController getController() {
        return controller;
    }

    public void setController(ConnectorController controller) {
        ConnectorController oldController = this.controller;
        this.controller = controller;
        propertyChangeSupport.firePropertyChange(PROP_CONTROLLER, oldController, controller);
    }

    @Override
    public DomNode copy() {
        Connector result= (Connector)super.copy();
        result.controller= null;  // CanvasController
        // handle children
        return result;
    }

    @Override
    public void syncTo(DomNode n) {
        syncTo(n,new ArrayList<String>());
    }

    @Override
    public void syncTo(DomNode n,List<String> exclude ) {
        super.syncTo(n,exclude);
        DomUtil.syncTo( this, n, exclude );
    }

    @Override
    public List<Diff> diffs(DomNode node) {
        return DomUtil.getDiffs( this, node );
    }
    
    @Override
    public boolean equals(Object obj) {
        if ( obj==null || !(obj instanceof Connector) ) return false;
        Connector that= (Connector)obj;
        return that.plotA.equals(this.plotA) && that.plotB.equals(this.plotB);
    }

    @Override
    public int hashCode() {
        return plotA.hashCode() + plotB.hashCode();
    }

    @Override
    public String toString() {
        return plotA + " to " + plotB;
    }
    
    
}
