/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import org.das2.util.filesystem.FileSystem;

/**
 *
 * @author jbf
 */
public final class AutoplotSettings {
    private static final Logger logger= Logger.getLogger("apdss");
    private static AutoplotSettings instance;

    public synchronized static AutoplotSettings settings() {
        if ( instance==null ) {
            instance= new AutoplotSettings();
        }
        return instance;
    }

    private AutoplotSettings() {
        try {
            prefs = Preferences.userNodeForPackage( AutoplotSettings.class );
            addPropertyChangeListener(listener);
            loadPreferences();
        } catch ( Exception ex ) {
            logger.warning("Problem encountered when attempting to load user preferences, continuing with autoplot_data in $HOME");
            this.autoplotData= "${HOME}/autoplot_data";
            this.fscache= "${HOME}/autoplot_data/fscache";
        }
    }

    PropertyChangeListener listener= new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            if ( evt.getNewValue() instanceof String ) {
                prefs.put( evt.getPropertyName(),(String) evt.getNewValue());
            } else if ( evt.getNewValue() instanceof Boolean ) {
                prefs.putBoolean( evt.getPropertyName(), (Boolean)evt.getNewValue() );
            } else {
                throw new RuntimeException("unsupported property type needs to be implemented: "+evt.getPropertyName() + "  " + evt.getNewValue().getClass() );
            }
        }
    };

    static Preferences prefs;

    public void loadPreferences() {
        this.autoplotData= prefs.get( PROP_AUTOPLOTDATA, "${HOME}/autoplot_data" );
        this.fscache= prefs.get( PROP_FSCACHE, "${HOME}/autoplot_data/fscache" );
    }
    
    /**
     * autoplotData is the home where Autoplot's metadata is kept.
     * This includes history (bookmarks), add-on tools, and the
     * secondary vfsCache.
     */
    protected String autoplotData = ""; // see loadPreferences
    public static final String PROP_AUTOPLOTDATA = "autoplotData";

    public String getAutoplotData() {
        return autoplotData;
    }

    /**
     * fscache is where downloaded data is kept.  Note there is no automatic
     * code for unloading the cache.
     */
    private String fscache= "";// see loadPreferences
    public static final String PROP_FSCACHE = "fscache";

    public String getFscache() {
        return fscache;
    }

    public void setFscache(String val) {
        String old = this.fscache;
        if ( old.equals(val) ) return;
        String tval= val;
        tval= tval.replaceAll("\\$\\{HOME\\}", System.getProperty("user.home" ) );
        FileSystem.settings().setLocalCacheDir( new java.io.File(tval) );
        this.fscache = val;
        String home= System.getProperty("user.home");
        if ( val.startsWith(home) ) {
            val= "${HOME}" + val.substring( home.length() );
        }
        propertyChangeSupport.firePropertyChange(PROP_FSCACHE, old, val );
    }

    public String resolveProperty( String name ) {
        if ( name.equals("autoplotData") ) {
            return getAutoplotData().replace("${HOME}", System.getProperty("user.home") );
        } else if ( name.equals("fscache" ) ) {
            String result= getFscache();
            result= result.replace("${autoplotData}", getAutoplotData() );
            result= result.replace("${HOME}", System.getProperty("user.home") );
            return result;
        } else {
            throw new IllegalArgumentException("unable to resolve property: "+ name );
        }
    }

    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

}
