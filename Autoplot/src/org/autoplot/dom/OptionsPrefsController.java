
package org.autoplot.dom;

import java.awt.Color;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.autoplot.ApplicationModel;
import org.das2.util.LoggerManager;
import org.autoplot.MouseModuleType;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.util.MigratePreference;
import org.das2.graph.DasColorBar;
import org.fuin.utils4j.PropertiesFilePreferences;

/**
 * listen to an Options class and manage storage and retrieval from persistent
 * storage.
 * @author jbf
 */
public final class OptionsPrefsController {

    Preferences prefs;
    Options options;

    private static final Logger logger= LoggerManager.getLogger( "autoplot.dom" );

    /**
     * write the current options out to persistent preferences, so that they will
     * be used next session.  Note the persistent preferences is a Java Preference
     * stored in ~/.java/... but it is being migrated to
     * AUTOPLOT_DATA/config/options.preferences.
     */
    public void copyOptionsToPersistentPreferences() {
        logger.fine("copy options to persistent preferences storage.");
        prefs.put( Options.PROP_BACKGROUND, DomUtil.encodeColor(options.getBackground()) );
        prefs.put( Options.PROP_FOREGROUND, DomUtil.encodeColor(options.getForeground()) );
        prefs.put( Options.PROP_COLOR, DomUtil.encodeColor( options.getColor() ) );
        prefs.put( Options.PROP_FILLCOLOR, DomUtil.encodeColor( options.getFillColor() ) );
        prefs.put( Options.PROP_COLORTABLE, options.getColortable().getListLabel() );
        prefs.put( Options.PROP_CANVASFONT, options.getCanvasFont() );
        prefs.putInt(Options.PROP_WIDTH, options.getWidth() );
        prefs.putInt(Options.PROP_HEIGHT, options.getHeight());
        prefs.put( Options.PROP_LINE_THICKNESS, options.getLineThickness() );
        prefs.putBoolean( Options.PROP_FLIPCOLORBARLABEL, options.isFlipColorbarLabel() );
        prefs.putBoolean( Options.PROP_DRAWGRID, options.isDrawGrid() );
        prefs.putBoolean( Options.PROP_DRAWMINORGRID, options.isDrawMinorGrid() );
        
        prefs.putBoolean( Options.PROP_OVERRENDERING, options.isOverRendering() );
        prefs.putBoolean( Options.PROP_SCRIPTVISIBLE, options.isScriptVisible() );
        prefs.putBoolean( Options.PROP_LOGCONSOLEVISIBLE, options.isLogConsoleVisible() );
        prefs.putBoolean( Options.PROP_DATAVISIBLE, options.isDataVisible() );
        prefs.putBoolean( Options.PROP_LAYOUTVISIBLE, options.isLayoutVisible() );
        // note this is never saved... options.setServerEnabled ( prefs.getBoolean(Options.PROP_SERVERENABLED, options.serverEnabled) );
        prefs.putBoolean( Options.PROP_SPECIALEFFECTS, options.isSpecialEffects() );
        prefs.putBoolean( Options.PROP_TEXTANTIALIAS, options.isTextAntiAlias() );
        prefs.putBoolean( Options.PROP_DAY_OF_YEAR, options.isDayOfYear() );

        prefs.putBoolean( Options.PROP_NEARESTNEIGHBOR, options.isNearestNeighbor() );
        try {
            prefs.put( Options.PROP_MOUSEMODULE, options.mouseModule.toString() );
        } catch ( IllegalArgumentException ex ) {
            logger.log( Level.SEVERE, ex.getMessage(), ex );
        }
        prefs.putBoolean( Options.PROP_FLIPCOLORBARLABEL, options.isFlipColorbarLabel() );
        prefs.put( Options.PROP_TICKLEN, options.getTicklen() );
        prefs.put( Options.PROP_LINE_THICKNESS, options.getLineThickness() );
        prefs.putFloat( Options.PROP_MULTILINETEXTALIGNMENT, options.getMultiLineTextAlignment() );
        prefs.put( Options.PROP_PRINTINGTAG, options.getPrintingTag() );
        prefs.put(Options.PROP_PRINTINGLOGLEVEL, options.getPrintingLogLevel().toString() );
        prefs.put(Options.PROP_DISPLAYLOGLEVEL, options.getDisplayLogLevel().toString() );
        prefs.putInt( Options.PROP_LOGMESSAGETIMEOUTSEC, options.getLogMessageTimeoutSec() );
        
        try {
            prefs.flush();
        } catch (BackingStoreException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * create a new controller with preferences for the options class.
     * @param model the application model
     * @param options the options node of that application.
     */
    public OptionsPrefsController( ApplicationModel model, Options options) {
        String f= AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA );
        File config= new File( f, "config" );
        //transition from Java-prefs (which is opaque to most scientists) to autoplot_data/config area.  This should be removed by Oct 2019.
        Preferences p1= new PropertiesFilePreferences( config, "options.properties" );
        Preferences p2= AutoplotSettings.getPreferences(options.getClass());
        prefs = new MigratePreference(p2,p1);
        this.options= options;
        this.loadPersistentPreferences= !model.isHeadless();        
        options.setController( this );
    }
    
    private boolean loadPersistentPreferences;

    public static final String PROP_LOADPERSISTENTPREFERENCES = "loadPersistentPreferences";

    public boolean isLoadPersistentPreferences() {
        return loadPersistentPreferences;
    }

    public void setLoadPersistentPreferences(boolean loadPersistentPreferences) {
        this.loadPersistentPreferences = loadPersistentPreferences;
    }

    /**
     * load the preferences which persist between sessions into dom.options, 
     * firing events as they are set.  Note for headless mode and non-application
     * modes, this has no effect.
     * @see #loadPreferences() loadPreferences which does not fire events.
     */
    public void loadPreferencesWithEvents( ) {
        if ( !loadPersistentPreferences ) {
            logger.fine("persistent preferences are disabled");
            return;
        }
        logger.fine("loading preferences into dom.options (and firing events).");
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
        options.setWidth ( prefs.getInt(Options.PROP_WIDTH, options.getWidth() ) );
        options.setHeight ( prefs.getInt(Options.PROP_HEIGHT, options.getHeight() ) );
        options.setColor ( Color.decode(prefs.get(Options.PROP_COLOR, DomUtil.encodeColor(options.color))) );
        options.setDrawAntiAlias ( prefs.getBoolean(Options.PROP_DRAWANTIALIAS, options.drawAntiAlias) );
        options.setDrawGrid ( prefs.getBoolean(Options.PROP_DRAWGRID, options.drawGrid) );
        options.setDrawMinorGrid ( prefs.getBoolean(Options.PROP_DRAWMINORGRID, options.drawMinorGrid) );
        options.setFillColor ( Color.decode(prefs.get(Options.PROP_FILLCOLOR, DomUtil.encodeColor(options.fillColor))) );
        options.setColortable( DasColorBar.Type.parse( prefs.get(Options.PROP_COLORTABLE, options.colortable.getListLabel() )) );
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
        if ( !loadPersistentPreferences ) {
            logger.fine("persistent preferences are disabled");
            return;
        }        
        logger.fine("loading preferences into dom.options");
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
        options.width= prefs.getInt(Options.PROP_WIDTH, options.getWidth() );
        options.height= prefs.getInt(Options.PROP_HEIGHT, options.getHeight() );
        options.color = Color.decode(prefs.get(Options.PROP_COLOR, DomUtil.encodeColor(options.color)));
        options.drawAntiAlias = prefs.getBoolean(Options.PROP_DRAWANTIALIAS, options.drawAntiAlias);
        options.drawGrid = prefs.getBoolean(Options.PROP_DRAWGRID, options.drawGrid);
        options.drawMinorGrid = prefs.getBoolean(Options.PROP_DRAWMINORGRID, options.drawMinorGrid);
        options.fillColor = Color.decode(prefs.get(Options.PROP_FILLCOLOR, DomUtil.encodeColor(options.fillColor)));
        options.colortable= DasColorBar.Type.parse( prefs.get(Options.PROP_COLORTABLE, options.colortable.getListLabel() ));
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
