/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.state;

import java.awt.Color;
import java.awt.Font;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.prefs.Preferences;

/**
 * Bean for holding AP configuration options
 * @author jbf
 */
public class Options {

    final Preferences prefs = Preferences.userNodeForPackage(Options.class);

    private String encodeColor( Color c ) {
        return "#" + Integer.toHexString( c.getRGB() & 0xFFFFFF );
    }
    
    public Options(  ) {
        
    }
    
    public void loadPreferences() {
        scriptVisible = prefs.getBoolean(PROP_SCRIPTVISIBLE, scriptVisible);
        logConsoleVisible = prefs.getBoolean(PROP_LOGCONSOLEVISIBLE, logConsoleVisible);
        serverEnabled = prefs.getBoolean(PROP_SERVERENABLED, serverEnabled);
        canvasFont = prefs.get(PROP_CANVASFONT, canvasFont);
        foreground = Color.decode(prefs.get(PROP_FOREGROUND, encodeColor(foreground) ) );
        background = Color.decode(prefs.get(PROP_BACKGROUND, encodeColor(background) ) );
    }
    
    protected Font guiFont = null;
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
    protected String canvasFont = "";
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
    
    
    
    //  - End of Properties -------------------------------------------------- ///
    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }
}
