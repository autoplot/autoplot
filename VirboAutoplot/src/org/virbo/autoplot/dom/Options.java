/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import org.das2.system.NullPreferences;

/**
 * Bean for holding AP configuration options
 * @author jbf
 */
public class Options extends DomNode {

    public static final String PROP_COLOR = "color";
    public static final String PROP_FILLCOLOR = "fillColor";
    Preferences prefs;

    public Options() {
        prefs = new NullPreferences(); //applet support
    }

    public void loadPreferences() {
        prefs = Preferences.userNodeForPackage(Options.class);
        autolabelling = prefs.getBoolean(PROP_AUTOLABELLING, autolabelling);
        autolayout = prefs.getBoolean(PROP_AUTOLAYOUT, autolayout);
        autoranging = prefs.getBoolean(PROP_AUTORANGING, autoranging);
        background = Color.decode(prefs.get(PROP_BACKGROUND, DomUtil.encodeColor(background)));
        canvasFont = prefs.get(PROP_CANVASFONT, canvasFont);
        color = Color.decode(prefs.get(PROP_COLOR, DomUtil.encodeColor(color)));
        drawAntiAlias = prefs.getBoolean(PROP_DRAWANTIALIAS, drawAntiAlias);
        drawGrid = prefs.getBoolean(PROP_DRAWGRID, drawGrid);
        drawMinorGrid = prefs.getBoolean(PROP_DRAWMINORGRID, drawMinorGrid);
        fillColor = Color.decode(prefs.get(PROP_FILLCOLOR, DomUtil.encodeColor(fillColor)));
        foreground = Color.decode(prefs.get(PROP_FOREGROUND, DomUtil.encodeColor(foreground)));
        logConsoleVisible = prefs.getBoolean(PROP_LOGCONSOLEVISIBLE, logConsoleVisible);
        overRendering = prefs.getBoolean(PROP_OVERRENDERING, overRendering);
        scriptVisible = prefs.getBoolean(PROP_SCRIPTVISIBLE, scriptVisible);
        serverEnabled = prefs.getBoolean(PROP_SERVERENABLED, serverEnabled);
        specialEffects = prefs.getBoolean(PROP_SPECIALEFFECTS, specialEffects);
        textAntiAlias = prefs.getBoolean(PROP_TEXTANTIALIAS, textAntiAlias);
    }

    protected boolean scriptVisible = false;
    public static final String PROP_SCRIPTVISIBLE = "scriptVisible";

    public boolean isScriptVisible() {
        return scriptVisible;
    }

    public void setScriptVisible(boolean scriptVisible) {
        boolean oldScriptVisible = this.scriptVisible;
        this.scriptVisible = scriptVisible;
        prefs.putBoolean(PROP_SCRIPTVISIBLE, scriptVisible);
        propertyChangeSupport.firePropertyChange(PROP_SCRIPTVISIBLE, oldScriptVisible, scriptVisible);
    }

    protected boolean logConsoleVisible = false;
    public static final String PROP_LOGCONSOLEVISIBLE = "logConsoleVisible";

    public boolean isLogConsoleVisible() {
        return logConsoleVisible;
    }

    public void setLogConsoleVisible(boolean logConsoleVisible) {
        boolean oldLogConsoleVisible = this.logConsoleVisible;
        this.logConsoleVisible = logConsoleVisible;
        prefs.putBoolean(PROP_LOGCONSOLEVISIBLE, logConsoleVisible);
        propertyChangeSupport.firePropertyChange(PROP_LOGCONSOLEVISIBLE, oldLogConsoleVisible, logConsoleVisible);
    }

    protected boolean serverEnabled = false;
    public static final String PROP_SERVERENABLED = "serverEnabled";

    public boolean isServerEnabled() {
        return serverEnabled;
    }

    public void setServerEnabled(boolean serverEnabled) {
        boolean oldServerEnabled = this.serverEnabled;
        this.serverEnabled = serverEnabled;
        prefs.putBoolean(PROP_SERVERENABLED, serverEnabled);
        propertyChangeSupport.firePropertyChange(PROP_SERVERENABLED, oldServerEnabled, serverEnabled);
    }

    protected String canvasFont = "sans-12";
    public static final String PROP_CANVASFONT = "canvasFont";

    public String getCanvasFont() {
        return canvasFont;
    }

    public void setCanvasFont(String canvasFont) {
        String oldCanvasFont = this.canvasFont;
        this.canvasFont = canvasFont;
        prefs.put(PROP_CANVASFONT, canvasFont);
        propertyChangeSupport.firePropertyChange(PROP_CANVASFONT, oldCanvasFont, canvasFont);
    }

    protected Color foreground = Color.black;
    public static final String PROP_FOREGROUND = "foreground";

    public Color getForeground() {
        return foreground;
    }

    public void setForeground(Color foreground) {
        Color oldForeground = this.foreground;
        this.foreground = new Color(foreground.getRGB()); //otherwise can't serialize
        prefs.put(PROP_FOREGROUND, DomUtil.encodeColor(foreground));
        propertyChangeSupport.firePropertyChange(PROP_FOREGROUND, oldForeground, foreground);
    }

    protected Color background = Color.white;
    public static final String PROP_BACKGROUND = "background";

    public Color getBackground() {
        return background;
    }

    public void setBackground(Color background) {
        Color oldBackground = this.background;
        this.background = new Color(background.getRGB()); //otherwise can't serialize
        prefs.put(PROP_BACKGROUND, DomUtil.encodeColor(background));
        propertyChangeSupport.firePropertyChange(PROP_BACKGROUND, oldBackground, background);
    }

    /**
     * Holds value of property color.
     */
    private Color color = Color.BLACK;

    /**
     * Getter for property color.
     * @return Value of property color.
     */
    public Color getColor() {
        return this.color;
    }

    /**
     * Setter for property color.
     * @param color New value of property color.
     */
    public void setColor(Color color) {
        Color oldColor = this.color;
        this.color = new Color(color.getRGB()); //otherwise can't serialize
        prefs.put(PROP_COLOR, DomUtil.encodeColor(color));
        propertyChangeSupport.firePropertyChange(PROP_COLOR, oldColor, color);
    }

    /**
     * Holds value of property fillColor.
     */
    private Color fillColor = Color.DARK_GRAY;

    /**
     * Getter for property fillColor.
     * @return Value of property fillColor.
     */
    public Color getFillColor() {
        return this.fillColor;
    }

    /**
     * Setter for property fillColor.
     * @param fillColor New value of property fillColor.
     */
    public void setFillColor(Color fillColor) {
        Color oldFillColor = this.fillColor;
        this.fillColor = new Color(fillColor.getRGB()); //otherwise can't serialize
        prefs.put(PROP_FILLCOLOR, DomUtil.encodeColor(fillColor));
        propertyChangeSupport.firePropertyChange(PROP_FILLCOLOR, oldFillColor, fillColor);
    }

    protected boolean specialEffects = false;
    /**
     * property specialEffects
     * enables/disables things like animated axes, etc.
     */
    public static final String PROP_SPECIALEFFECTS = "specialEffects";

    public boolean isSpecialEffects() {
        return specialEffects;
    }

    public void setSpecialEffects(boolean specialEffects) {
        boolean oldSpecialEffects = this.specialEffects;
        this.specialEffects = specialEffects;
        prefs.putBoolean(PROP_SPECIALEFFECTS, specialEffects);
        propertyChangeSupport.firePropertyChange(PROP_SPECIALEFFECTS, oldSpecialEffects, specialEffects);
    }

    /**
     * property drawAntiAlias
     * should plot symbols and lines be anti aliased?
     */
    protected boolean drawAntiAlias = true;
    public static final String PROP_DRAWANTIALIAS = "drawAntiAlias";

    public boolean isDrawAntiAlias() {
        return drawAntiAlias;
    }

    public void setDrawAntiAlias(boolean drawAntiAlias) {
        boolean oldDrawAntiAlias = this.drawAntiAlias;
        this.drawAntiAlias = drawAntiAlias;
        prefs.putBoolean(PROP_DRAWANTIALIAS, drawAntiAlias);
        propertyChangeSupport.firePropertyChange(PROP_DRAWANTIALIAS, oldDrawAntiAlias, drawAntiAlias);
    }

    /**
     * property textAntiAlias
     * should text labels be anti aliased?
     */
    protected boolean textAntiAlias = true;
    public static final String PROP_TEXTANTIALIAS = "textAntiAlias";

    public boolean isTextAntiAlias() {
        return textAntiAlias;
    }

    public void setTextAntiAlias(boolean textAntiAlias) {
        boolean oldTextAntiAlias = this.textAntiAlias;
        this.textAntiAlias = textAntiAlias;
        prefs.putBoolean(PROP_TEXTANTIALIAS, textAntiAlias);
        propertyChangeSupport.firePropertyChange(PROP_TEXTANTIALIAS, oldTextAntiAlias, textAntiAlias);
    }

    protected boolean drawGrid = false;
    public static final String PROP_DRAWGRID = "drawGrid";

    public boolean isDrawGrid() {
        return drawGrid;
    }

    public void setDrawGrid(boolean drawGrid) {
        boolean oldDrawGrid = this.drawGrid;
        this.drawGrid = drawGrid;
        prefs.putBoolean(PROP_DRAWGRID, drawGrid);
        propertyChangeSupport.firePropertyChange(PROP_DRAWGRID, oldDrawGrid, drawGrid);
    }

    protected boolean drawMinorGrid = false;
    public static final String PROP_DRAWMINORGRID = "drawMinorGrid";

    public boolean isDrawMinorGrid() {
        return drawMinorGrid;
    }

    public void setDrawMinorGrid(boolean drawMinorGrid) {
        boolean oldDrawMinorGrid = this.drawMinorGrid;
        this.drawMinorGrid = drawMinorGrid;
        prefs.putBoolean(PROP_DRAWMINORGRID, drawMinorGrid);
        propertyChangeSupport.firePropertyChange(PROP_DRAWMINORGRID, oldDrawMinorGrid, drawMinorGrid);
    }

    /**
     * overRendering is a hint to the plots that the data outside the visible axis bounds should be rendered
     * so that operations like pan are more fluid.
     */
    public static final String PROP_OVERRENDERING = "overRendering";
    protected boolean overRendering = false;

    public boolean isOverRendering() {
        return overRendering;
    }

    public void setOverRendering(boolean overRendering) {
        boolean oldValue = this.overRendering;
        this.overRendering = overRendering;
        prefs.putBoolean(PROP_OVERRENDERING, overRendering);
        propertyChangeSupport.firePropertyChange(PROP_OVERRENDERING, oldValue, overRendering);
    }

 
    private boolean autoranging = true;
    public static final String PROP_AUTORANGING = "autoranging";

    public boolean isAutoranging() {
        return this.autoranging;
    }

    public void setAutoranging(boolean newautoranging) {
        boolean oldautoranging = autoranging;
        this.autoranging = newautoranging;
        prefs.putBoolean(PROP_AUTORANGING, autoranging);
        propertyChangeSupport.firePropertyChange(PROP_AUTORANGING, oldautoranging, newautoranging);
    }

    protected boolean autolabelling = true;
    public static final String PROP_AUTOLABELLING = "autolabelling";

    public boolean isAutolabelling() {
        return autolabelling;
    }

    public void setAutolabelling(boolean autolabelling) {
        boolean oldAutolabelling = this.autolabelling;
        this.autolabelling = autolabelling;
        prefs.putBoolean(PROP_AUTOLABELLING, autolabelling);
        propertyChangeSupport.firePropertyChange(PROP_AUTOLABELLING, oldAutolabelling, autolabelling);
    }

    protected boolean autolayout = true;
    public static final String PROP_AUTOLAYOUT = "autolayout";

    public boolean isAutolayout() {
        return autolayout;
    }

    public void setAutolayout(boolean autolayout) {
        boolean oldAutolayout = this.autolayout;
        this.autolayout = autolayout;
        prefs.putBoolean(PROP_AUTOLAYOUT, autolayout);
        propertyChangeSupport.firePropertyChange(PROP_AUTOLAYOUT, oldAutolayout, autolayout);
    }


    public void syncTo( DomNode n,List<String> exclude ) {
        super.syncTo(n,exclude);
        Options that = (Options) n;
        if ( !exclude.contains(PROP_BACKGROUND) ) this.setBackground(that.getBackground());
        if ( !exclude.contains(PROP_FOREGROUND) ) this.setForeground(that.getForeground());
        if ( !exclude.contains(PROP_COLOR) )this.setColor(that.getColor());
        if ( !exclude.contains(PROP_FILLCOLOR) )this.setFillColor(that.getFillColor());
        if ( !exclude.contains(PROP_CANVASFONT) )this.setCanvasFont(that.getCanvasFont());
        if ( !exclude.contains(PROP_LOGCONSOLEVISIBLE) )this.setLogConsoleVisible(that.isLogConsoleVisible());
        if ( !exclude.contains(PROP_SCRIPTVISIBLE) )this.setScriptVisible(that.isScriptVisible());
        if ( !exclude.contains(PROP_SERVERENABLED) )this.setServerEnabled(that.isServerEnabled());
        if ( !exclude.contains(PROP_DRAWGRID) )this.setDrawGrid(that.isDrawGrid());
        if ( !exclude.contains(PROP_DRAWMINORGRID) )this.setDrawMinorGrid(that.isDrawMinorGrid());
        if ( !exclude.contains(PROP_DRAWANTIALIAS) )this.setDrawAntiAlias(that.drawAntiAlias);
        if ( !exclude.contains(PROP_TEXTANTIALIAS) )this.setTextAntiAlias(that.textAntiAlias);
        if ( !exclude.contains(PROP_OVERRENDERING) )this.setOverRendering(that.overRendering);
        if ( !exclude.contains(PROP_AUTOLABELLING) )this.setAutolabelling(that.autolabelling);
        if ( !exclude.contains(PROP_AUTORANGING) )this.setAutoranging(that.autoranging);
        if ( !exclude.contains(PROP_AUTOLAYOUT) )this.setAutolayout(that.autolayout);
    }

    public void syncTo(DomNode n) {
        this.syncTo(n, new ArrayList<String>() );
    }

    public List<Diff> diffs(DomNode node) {
        Options that = (Options) node;

        List<Diff> result = super.diffs(node);

        boolean b;

        b = that.isAutolabelling()==this.isAutolabelling();
        if (!b) result.add(new PropertyChangeDiff(PROP_AUTOLABELLING, that.isAutolabelling(), this.isAutolabelling()));
        b = that.isAutolayout()==this.isAutolayout();
        if (!b) result.add(new PropertyChangeDiff(PROP_AUTOLAYOUT, that.isAutolayout(), this.isAutolayout()));
        b = that.isAutoranging()==this.isAutoranging();
        if (!b) result.add(new PropertyChangeDiff(PROP_AUTORANGING, that.isAutoranging(), this.isAutoranging()));
        b = that.getBackground().equals(this.getBackground());
        if (!b) result.add(new PropertyChangeDiff(PROP_BACKGROUND, that.getBackground(), this.getBackground()));
        b = that.getCanvasFont().equals(this.getCanvasFont());
        if (!b) result.add(new PropertyChangeDiff(PROP_CANVASFONT, that.getCanvasFont(), this.getCanvasFont()));
        b = that.getColor().equals(this.getColor());
        if (!b) result.add(new PropertyChangeDiff(PROP_COLOR, that.getColor(), this.getColor()));
        b = that.isDrawAntiAlias()==this.isDrawAntiAlias();
        if (!b) result.add(new PropertyChangeDiff(PROP_DRAWANTIALIAS, that.isDrawAntiAlias(), this.isDrawAntiAlias()));
        b = that.isDrawGrid()==this.isDrawGrid();
        if (!b) result.add(new PropertyChangeDiff(PROP_DRAWGRID, that.isDrawGrid(), this.isDrawGrid()));
        b = that.isDrawMinorGrid()==this.isDrawMinorGrid();
        if (!b) result.add(new PropertyChangeDiff(PROP_DRAWMINORGRID, that.isDrawMinorGrid(), this.isDrawMinorGrid()));
        b = that.getFillColor().equals(this.getFillColor());
        if (!b) result.add(new PropertyChangeDiff(PROP_FILLCOLOR, that.getFillColor(), this.getFillColor()));
        b = that.getForeground().equals(this.getForeground());
        if (!b) result.add(new PropertyChangeDiff(PROP_FOREGROUND, that.getForeground(), this.getForeground()));
        b = that.isLogConsoleVisible() == this.isLogConsoleVisible();
        if (!b) result.add(new PropertyChangeDiff(PROP_LOGCONSOLEVISIBLE, that.isLogConsoleVisible(), this.isLogConsoleVisible()));
        b = that.isOverRendering() == this.isOverRendering();
        if (!b) result.add(new PropertyChangeDiff(PROP_OVERRENDERING, that.isOverRendering(), this.isOverRendering()));
        b = that.isScriptVisible() == this.isScriptVisible();
        if (!b) result.add(new PropertyChangeDiff(PROP_SCRIPTVISIBLE, that.isScriptVisible(), this.isScriptVisible()));
        b = that.isServerEnabled() == this.isServerEnabled();
        if (!b) result.add(new PropertyChangeDiff(PROP_SERVERENABLED, that.isServerEnabled(), this.isServerEnabled()));
        b = that.isSpecialEffects() == this.isSpecialEffects();
        if (!b) result.add(new PropertyChangeDiff(PROP_SPECIALEFFECTS, that.isSpecialEffects(), this.isSpecialEffects()));
        b = that.isTextAntiAlias() == this.isTextAntiAlias();
        if (!b) result.add(new PropertyChangeDiff(PROP_TEXTANTIALIAS, that.isTextAntiAlias(), this.isTextAntiAlias()));
        return result;
    }

}
