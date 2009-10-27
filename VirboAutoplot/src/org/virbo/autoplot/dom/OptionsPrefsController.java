/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.prefs.Preferences;

/**
 * listen to an Options class and keep prefs up to date.
 * @author jbf
 */
public class OptionsPrefsController {

    Preferences prefs;
    Options options;

    PropertyChangeListener listener= new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            if ( evt.getNewValue() instanceof String ) {
                prefs.put( evt.getPropertyName(),(String) evt.getNewValue());
            } else if ( evt.getNewValue() instanceof Boolean ) {
                prefs.putBoolean( evt.getPropertyName(), (Boolean)evt.getNewValue() );
            } else if ( evt.getNewValue() instanceof Color ) {
                prefs.put( evt.getPropertyName(), DomUtil.encodeColor((Color)evt.getNewValue() ) );
            } else {
                throw new RuntimeException("unsupported property type needs to be implemented: "+evt.getPropertyName() + "  " + evt.getNewValue().getClass() );
            }
        }
    };

    /**
     * create a new controller with preferences for the options class.
     * @param options
     */
    public OptionsPrefsController( Options options ) {
        prefs = Preferences.userNodeForPackage( options.getClass() );
        this.options= options;
        options.addPropertyChangeListener( listener );
    }

    public void loadPreferences() {
        options.autolabelling = prefs.getBoolean(Options.PROP_AUTOLABELLING, options.autolabelling);
        options.autolayout = prefs.getBoolean(Options.PROP_AUTOLAYOUT, options.autolayout);
        options.autoranging = prefs.getBoolean(Options.PROP_AUTORANGING, options.autoranging);
        options.background = Color.decode(prefs.get(Options.PROP_BACKGROUND, DomUtil.encodeColor(options.background)));
        options.canvasFont = prefs.get(Options.PROP_CANVASFONT, options.canvasFont);
        options.color = Color.decode(prefs.get(Options.PROP_COLOR, DomUtil.encodeColor(options.color)));
        options.drawAntiAlias = prefs.getBoolean(Options.PROP_DRAWANTIALIAS, options.drawAntiAlias);
        options.drawGrid = prefs.getBoolean(Options.PROP_DRAWGRID, options.drawGrid);
        options.drawMinorGrid = prefs.getBoolean(Options.PROP_DRAWMINORGRID, options.drawMinorGrid);
        options.fillColor = Color.decode(prefs.get(Options.PROP_FILLCOLOR, DomUtil.encodeColor(options.fillColor)));
        options.foreground = Color.decode(prefs.get(Options.PROP_FOREGROUND, DomUtil.encodeColor(options.foreground)));
        options.logConsoleVisible = prefs.getBoolean(Options.PROP_LOGCONSOLEVISIBLE, options.logConsoleVisible);
        options.overRendering = prefs.getBoolean(Options.PROP_OVERRENDERING, options.overRendering);
        options.scriptVisible = prefs.getBoolean(Options.PROP_SCRIPTVISIBLE, options.scriptVisible);
        options.dataVisible = prefs.getBoolean(Options.PROP_DATAVISIBLE, options.dataVisible);
        options.layoutVisible = prefs.getBoolean(Options.PROP_LAYOUTVISIBLE, options.layoutVisible);
        options.serverEnabled = prefs.getBoolean(Options.PROP_SERVERENABLED, options.serverEnabled);
        options.specialEffects = prefs.getBoolean(Options.PROP_SPECIALEFFECTS, options.specialEffects);
        options.textAntiAlias = prefs.getBoolean(Options.PROP_TEXTANTIALIAS, options.textAntiAlias);
    }
}
