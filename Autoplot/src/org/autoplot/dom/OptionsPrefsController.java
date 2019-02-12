
package org.autoplot.dom;

import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.das2.util.LoggerManager;
import org.autoplot.MouseModuleType;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.util.TickleTimer;

/**
 * listen to an Options class and keep prefs up to date.
 * @author jbf
 */
public class OptionsPrefsController {

    Preferences prefs;
    Options options;

    private static final Logger logger= LoggerManager.getLogger( "autoplot.dom" );

    TickleTimer flushTimer= new TickleTimer( 300, new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt,"flushTimer");  
            try {
                prefs.flush();
            } catch (BackingStoreException ex) {
                logger.log(Level.FINE, ex.getMessage(), ex);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex1) {

                }                    
                try {
                    prefs.flush();
                } catch (BackingStoreException ex1) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex1);
                }
            }
        }
    });

    PropertyChangeListener listener= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LoggerManager.logPropertyChangeEvent(evt,"listener");  
            if ( evt.getPropertyName().equals("id") ) return;
            if ( evt.getNewValue() instanceof String ) {
                prefs.put( evt.getPropertyName(),(String) evt.getNewValue());
            } else if ( evt.getNewValue() instanceof Boolean ) {
                prefs.putBoolean( evt.getPropertyName(), (Boolean)evt.getNewValue() );
            } else if ( evt.getNewValue() instanceof Color ) {
                prefs.put( evt.getPropertyName(), DomUtil.encodeColor((Color)evt.getNewValue() ) );
            } else if ( evt.getNewValue() instanceof Enum ) {
                prefs.put( evt.getPropertyName(), evt.getNewValue().toString() );
            } else if ( evt.getNewValue() instanceof Level ) {
                prefs.put( evt.getPropertyName(), evt.getNewValue().toString() );
            } else if ( evt.getNewValue() instanceof Integer ) {
                prefs.putInt(evt.getPropertyName(), ((Integer)evt.getNewValue()).intValue() );
            } else if ( evt.getNewValue() instanceof Double ) {
                prefs.putDouble(evt.getPropertyName(), ((Double)evt.getNewValue()).doubleValue() );
            } else if ( evt.getNewValue() instanceof Float ) {
                prefs.putFloat(evt.getPropertyName(), ((Float)evt.getNewValue()).floatValue() );
            } else {
                throw new RuntimeException("unsupported property type needs to be implemented: "+evt.getPropertyName() + "  " + evt.getNewValue().getClass() );
            }
            logger.log( Level.FINE, "options node change requires flush: {0}={1}", new Object[] { evt.getPropertyName(), evt.getNewValue().toString()} );
            flushTimer.tickle();
        }
    };

    /**
     * create a new controller with preferences for the options class.
     * @param options
     */
    public OptionsPrefsController( Options options ) {
        prefs = AutoplotSettings.settings().getPreferences(options.getClass());
        this.options= options;
        options.addPropertyChangeListener( listener );
    }

    public void loadPreferencesWithEvents( ) {
        options.setAutolabelling ( prefs.getBoolean(Options.PROP_AUTOLABELLING, options.autolabelling) );
        options.setAutolayout ( prefs.getBoolean(Options.PROP_AUTOLAYOUT, options.autolayout) );
        options.setAutoranging ( prefs.getBoolean(Options.PROP_AUTORANGING, options.autoranging) );
        if ( !options.autoranging ) {
            System.err.println("Autorange default was false, enabling it now.");
            options.setAutoranging( true );
        }
        if ( !options.autolayout ) {
            System.err.println("Autolayout default was false, enabling it now.");
            options.setAutolayout( true );
        }
        if ( !options.autolabelling ) {
            System.err.println("Autolabelling default was false, enabling it now.");
            options.setAutolabelling( true );
        }
        options.setBackground ( Color.decode(prefs.get(Options.PROP_BACKGROUND, DomUtil.encodeColor(options.background))) );
        options.setCanvasFont ( prefs.get(Options.PROP_CANVASFONT, options.canvasFont) );
        options.setColor ( Color.decode(prefs.get(Options.PROP_COLOR, DomUtil.encodeColor(options.color))) );
        options.setDrawAntiAlias ( prefs.getBoolean(Options.PROP_DRAWANTIALIAS, options.drawAntiAlias) );
        options.setDrawGrid ( prefs.getBoolean(Options.PROP_DRAWGRID, options.drawGrid) );
        options.setDrawMinorGrid ( prefs.getBoolean(Options.PROP_DRAWMINORGRID, options.drawMinorGrid) );
        options.setFillColor ( Color.decode(prefs.get(Options.PROP_FILLCOLOR, DomUtil.encodeColor(options.fillColor))) );
        options.setForeground ( Color.decode(prefs.get(Options.PROP_FOREGROUND, DomUtil.encodeColor(options.foreground))) );
        options.setLogConsoleVisible ( prefs.getBoolean(Options.PROP_LOGCONSOLEVISIBLE, options.logConsoleVisible) );
        options.setOverRendering ( prefs.getBoolean(Options.PROP_OVERRENDERING, options.overRendering) );
        options.setScriptVisible ( prefs.getBoolean(Options.PROP_SCRIPTVISIBLE, options.scriptVisible) );
        options.setDataVisible ( prefs.getBoolean(Options.PROP_DATAVISIBLE, options.dataVisible) );
        options.setLayoutVisible ( prefs.getBoolean(Options.PROP_LAYOUTVISIBLE, options.layoutVisible ));
        options.setServerEnabled ( prefs.getBoolean(Options.PROP_SERVERENABLED, options.serverEnabled) );
        options.setSpecialEffects ( prefs.getBoolean(Options.PROP_SPECIALEFFECTS, options.specialEffects) );
        options.setTextAntiAlias ( prefs.getBoolean(Options.PROP_TEXTANTIALIAS, options.textAntiAlias) );
        options.setDayOfYear( prefs.getBoolean(Options.PROP_DAY_OF_YEAR,options.dayOfYear) );
        options.setNearestNeighbor( prefs.getBoolean(Options.PROP_NEARESTNEIGHBOR,options.nearestNeighbor) );
        try {
            options.setMouseModule( MouseModuleType.valueOf( prefs.get(Options.PROP_MOUSEMODULE, options.mouseModule.toString() ) ) );
        } catch ( IllegalArgumentException ex ) {
            logger.log( Level.SEVERE, ex.getMessage(), ex );
        }
        options.setFlipColorbarLabel( prefs.getBoolean(Options.PROP_FLIPCOLORBARLABEL,options.flipColorbarLabel ) );
        options.setTicklen( prefs.get(Options.PROP_TICKLEN, options.ticklen ) );
        options.setLineThickness( prefs.get(Options.PROP_LINE_THICKNESS, options.lineThickness ) );
        options.setMultiLineTextAlignment( prefs.getFloat( Options.PROP_MULTILINETEXTALIGNMENT, options.multiLineTextAlignment ) );
        options.setPrintingTag( prefs.get(Options.PROP_PRINTINGTAG, options.printingTag ) );
        options.setPrintingLogLevel( Level.parse( prefs.get(Options.PROP_PRINTINGLOGLEVEL, options.printingLogLevel.toString() ) ) );
        options.setDisplayLogLevel( Level.parse( prefs.get(Options.PROP_DISPLAYLOGLEVEL, options.displayLogLevel.toString() ) ) );
        options.setLogMessageTimeoutSec( prefs.getInt( Options.PROP_LOGMESSAGETIMEOUTSEC, options.logMessageTimeoutSec ) );
    }

    public void loadPreferences() {
        options.autolabelling = prefs.getBoolean(Options.PROP_AUTOLABELLING, options.autolabelling);
        options.autolayout = prefs.getBoolean(Options.PROP_AUTOLAYOUT, options.autolayout);
        options.autoranging = prefs.getBoolean(Options.PROP_AUTORANGING, options.autoranging);
        if ( !options.autoranging ) {
            System.err.println("Autorange default was false, enabling it now.");
            options.autoranging= true;
        }
        if ( !options.autolayout ) {
            System.err.println("Autolayout default was false, enabling it now.");
            options.autolayout= true;
        }
        if ( !options.autolabelling ) {
            System.err.println("Autolabelling default was false, enabling it now.");
            options.autolabelling= true;
        }
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
        options.dayOfYear= prefs.getBoolean(Options.PROP_DAY_OF_YEAR,options.dayOfYear);
        options.nearestNeighbor= prefs.getBoolean(Options.PROP_NEARESTNEIGHBOR,options.nearestNeighbor);
        try {
            options.setMouseModule( MouseModuleType.valueOf( prefs.get(Options.PROP_MOUSEMODULE, options.mouseModule.toString() ) ) );
        } catch ( IllegalArgumentException ex ) {
            logger.log( Level.SEVERE, ex.getMessage(), ex );
        }
        options.flipColorbarLabel= prefs.getBoolean(Options.PROP_FLIPCOLORBARLABEL,options.flipColorbarLabel );
        options.ticklen= prefs.get(Options.PROP_TICKLEN, options.ticklen );
        options.lineThickness= prefs.get(Options.PROP_LINE_THICKNESS, options.lineThickness );
        options.multiLineTextAlignment= prefs.getFloat(Options.PROP_MULTILINETEXTALIGNMENT, options.multiLineTextAlignment );
        options.printingTag= prefs.get(Options.PROP_PRINTINGTAG, options.printingTag );
        options.printingLogLevel= Level.parse( prefs.get(Options.PROP_PRINTINGLOGLEVEL, options.printingLogLevel.toString() ) );
        options.displayLogLevel= Level.parse( prefs.get(Options.PROP_DISPLAYLOGLEVEL, options.displayLogLevel.toString() ) );
        options.logMessageTimeoutSec= prefs.getInt( Options.PROP_LOGMESSAGETIMEOUTSEC, options.logMessageTimeoutSec );
    }
}
