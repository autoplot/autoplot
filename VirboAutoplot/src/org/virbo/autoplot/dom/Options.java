/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.awt.Color;
import java.awt.Font;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import org.das2.system.NullPreferences;

/**
 * Bean for holding AP configuration options
 * @author jbf
 */
public class Options extends DomNode {

    Preferences prefs ;

    public static String encodeColor( Color c ) {
        return "#" + Integer.toHexString( c.getRGB() & 0xFFFFFF );
    }

    private String encodeFont(Font f) {
        return f.getFontName() + "-" + f.getSize();
    }
    
    public Options(  ) {
        prefs= new NullPreferences(); //applet support
    }
    
    public void loadPreferences() {
        prefs = Preferences.userNodeForPackage(Options.class);        
        scriptVisible = prefs.getBoolean(PROP_SCRIPTVISIBLE, scriptVisible);
        logConsoleVisible = prefs.getBoolean(PROP_LOGCONSOLEVISIBLE, logConsoleVisible);
        serverEnabled = prefs.getBoolean(PROP_SERVERENABLED, serverEnabled);
        canvasFont = Font.decode( prefs.get(PROP_CANVASFONT, encodeFont(canvasFont) ) );
        foreground = Color.decode(prefs.get(PROP_FOREGROUND, encodeColor(foreground) ) );
        background = Color.decode(prefs.get(PROP_BACKGROUND, encodeColor(background) ) );
    }
    
    protected Font guiFont = Font.decode("sans-12");
    public static final String PROP_GUIFONT = "guiFont";

    public Font getGuiFont() {
        return guiFont;
    }

    public void setGuiFont(Font guiFont) {
        Font oldGuiFont = this.guiFont;
        this.guiFont = guiFont;

        propertyChangeSupport.firePropertyChange(PROP_GUIFONT, oldGuiFont, guiFont);
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
    protected Font canvasFont = Font.decode("sans-12");
    public static final String PROP_CANVASFONT = "canvasFont";

    public Font getCanvasFont() {
        return canvasFont;
    }

    public void setCanvasFont(Font canvasFont) {
        Font oldCanvasFont = this.canvasFont;
        this.canvasFont = canvasFont;
        prefs.put(PROP_CANVASFONT, encodeFont(canvasFont));
        propertyChangeSupport.firePropertyChange(PROP_CANVASFONT, oldCanvasFont, canvasFont);
    }
    
    protected Color foreground = Color.black;
    public static final String PROP_FOREGROUND = "foreground";

    public Color getForeground() {
        return foreground;
    }

    public void setForeground(Color foreground) {
        Color oldForeground = this.foreground;
        this.foreground = new Color( foreground.getRGB() ); //otherwise can't serialize
        prefs.put(PROP_FOREGROUND, encodeColor(foreground) );
        propertyChangeSupport.firePropertyChange(PROP_FOREGROUND, oldForeground, foreground);
    }
    
    protected Color background = Color.white;
    public static final String PROP_BACKGROUND = "background";

    public Color getBackground() {
        return background;
    }

    public void setBackground(Color background) {
        Color oldBackground = this.background;
        this.background = new Color( background.getRGB() ); //otherwise can't serialize
        prefs.put(PROP_BACKGROUND, encodeColor(background) );
        propertyChangeSupport.firePropertyChange(PROP_BACKGROUND, oldBackground, background);
        
    }
    
    /**
     * Holds value of property color.
     */
    private Color color= Color.BLACK;
    
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
        this.color = color;
    }
    
    /**
     * Holds value of property fillColor.
     */
    private Color fillColor= Color.DARK_GRAY;
    
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
        this.fillColor = fillColor;
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
        boolean oldOverRendering = this.overRendering;
        this.overRendering = overRendering;
        propertyChangeSupport.firePropertyChange(PROP_OVERRENDERING, oldOverRendering, overRendering);
    }

    
    public void syncTo(DomNode n) {
        Options that= (Options)n;
        this.setBackground( that.getBackground() );
        this.setForeground(that.getForeground());
        this.setColor( that.getColor() );
        this.setFillColor( that.getFillColor() );
        this.setCanvasFont( that.getCanvasFont() );
        this.setLogConsoleVisible( that.isLogConsoleVisible() );
        this.setScriptVisible( that.isScriptVisible() );
        this.setServerEnabled( that.isServerEnabled() );
        this.setDrawGrid( that.isDrawGrid() );
        this.setDrawMinorGrid( that.isDrawMinorGrid() );
        this.setDrawAntiAlias( that.drawAntiAlias );
        this.setTextAntiAlias( that.textAntiAlias );
        this.setOverRendering( that.isOverRendering() );
    }

    public Map<String, String> diffs(DomNode node) {
        Options that= (Options)node;
        LinkedHashMap<String,String> result= new  LinkedHashMap<String,String>();

        boolean b;
        b=  that.color.equals(this.color) ;
        if ( !b ) result.put( "color", that.color + " to "+ this.color );
        
        b= that.getBackground() .equals( this.getBackground() );
        if ( !b ) result.put( "background", that.getBackground() + " to " + this.getBackground() );
        
        b= that.getForeground().equals( this.getForeground());
        if ( !b ) result.put( "foreground", that.getForeground()+ " to " + this.getForeground());
        b= that.getColor() .equals( this.getColor() );
        if ( !b ) result.put( "color", that.getColor() + " to " + this.getColor() );
        b= that.getFillColor() .equals( this.getFillColor() );
        if ( !b ) result.put( "fillColor", that.getFillColor() + " to " + this.getFillColor() );
        b= that.getCanvasFont() .equals( this.getCanvasFont() ) ;
        if ( !b ) result.put( "canvasFont", that.getCanvasFont() + " to " + this.getCanvasFont() ) ;
        b= that.isLogConsoleVisible()== this.isLogConsoleVisible() ;
        if ( !b ) result.put( "logConsoleVisible", that.isLogConsoleVisible() + " to " + this.isLogConsoleVisible() );
        b= that.isScriptVisible()==this.isScriptVisible() ;
        if ( !b ) result.put( "scriptVisible", that.isScriptVisible()+ " to " + this.isScriptVisible() );
        b= that.isServerEnabled()== this.isServerEnabled() ;
        if ( !b ) result.put( "serverEnabled", that.isServerEnabled()+ " to " +this.isServerEnabled() ) ;
        b= that.isOverRendering()== this.isOverRendering();
        if ( !b ) result.put( PROP_OVERRENDERING, that.isOverRendering()+ " to " +this.isOverRendering() ) ;
        return result;
    }

}
