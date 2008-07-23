/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.state;

import java.awt.Font;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Bean for holding AP configuration options
 * @author jbf
 */
public class Options {
    
    public Options() {
        
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
        propertyChangeSupport.firePropertyChange(PROP_SERVERENABLED, oldServerEnabled, serverEnabled);
    }

    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

}
