
package org.autoplot.datasource;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import org.autoplot.util.MigratePreference;
import org.das2.util.filesystem.FileSystem;

/**
 * Autoplot's settings, stored in Java preferences, include
 * things like the last folder opened.  Also this code handles
 * property resolution like ${HOME}/autoplot_data.
 * @author jbf
 */
public final class AutoplotSettings {
    private static final Logger logger= Logger.getLogger("apdss");
    private static AutoplotSettings instance;

    public static final String PREF_LAST_OPEN_FOLDER = "last_open_folder";
    public static final String PREF_RECENTLY_OPENED_FILES = "recently_opened_files";
    public static final String PREF_LAST_OPEN_VAP_FOLDER= "last_open_vap_folder";
    public static final String PREF_LAST_OPEN_VAP_FILE= "last_open_vap_file";
    
    public synchronized static AutoplotSettings settings() {
        if ( instance==null ) {
            instance= new AutoplotSettings();
        }
        return instance;
    }

    private AutoplotSettings() {
        try {
            prefs = getPreferences( AutoplotSettings.class );
            addPropertyChangeListener(listener);
            loadPreferences();
        } catch ( Exception ex ) {
            logger.warning("Problem encountered when attempting to load user preferences, continuing with autoplot_data in $HOME");
            this.autoplotData= "${HOME}/autoplot_data";
            this.fscache= this.autoplotData + "/fscache";
        }
    }

    PropertyChangeListener listener= new PropertyChangeListener() {
        @Override
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

    /**
     * load the preferences,
     * which include the location of the autoplot_data directory and fscache.
     * The system property AUTOPLOT_DATA will override the user preference 
     * autoplotData which is by default "${HOME}/autoplot_data", and
     * can be set on the command line.  The environment variable AUTOPLOT_DATA
     * will also override the default location.
     * AUTOPLOT_FSCACHE is the location of the remote
     * file mirror storing lots of data and can be moved separately.
     */
    public void loadPreferences() {
        this.autoplotData= prefs.get( PROP_AUTOPLOTDATA, "${HOME}/autoplot_data" );
        String p= System.getProperty("AUTOPLOT_DATA");
        if ( p==null ) {
            p= System.getenv("AUTOPLOT_DATA");
            if ( p!=null ) {
                logger.log(Level.FINE, "AUTOPLOT_DATA environment variable used to set AUTOPLOT_DATA={0}", p);
            }
        } else {
            logger.log(Level.FINE, "AUTOPLOT_DATA system property (-D on cmd line) used to set AUTOPLOT_DATA={0}", p);
        }
        if ( p!=null ) {
            File f= new File(p);
            try {
                if (f.getCanonicalPath().equals(f.getAbsolutePath()) ) {
                    logger.log(Level.FINE, "Canonical path is not equal to path, may be a link: {0}", f);
                }
                this.autoplotData= f.getAbsolutePath();
            } catch (IOException ex) {
                this.autoplotData= p;
            }
        }
        this.fscache= prefs.get( PROP_FSCACHE, this.autoplotData+"/fscache" );
        p= System.getProperty("AUTOPLOT_FSCACHE");
        if ( p!=null ) this.fscache= p;
        if ( p==null ) {
            p= System.getenv("AUTOPLOT_FSCACHE");
            if ( p!=null ) {
                logger.log(Level.FINE, "AUTOPLOT_FSCACHE environment variable used to set AUTOPLOT_FSCACHE={0}", p);
            }
        } else {
            logger.log(Level.FINE, "AUTOPLOT_FSCACHE system property (-D on cmd line) used to set AUTOPLOT_FSCACHE={0}", p);
        }
    }
    
    /**
     * wrapper that will handle legacy (v2016a) prefs, and will allow migration
     * to ~/autoplot_data/config area.
     * @param c the class requesting preferences.
     * @return the preferences object this class should use.
     */
    public Preferences getPreferences( Class c ) {
        String s= c.getPackage().getName();
        Preferences p1= Preferences.userRoot().node("/"+s.replace('.','/'));
        switch (s) {
            case "org.autoplot.dom":
                s= "org.virbo.autoplot.dom";
                break;
            case "org.autoplot":
                s= "org.virbo.autoplot";
                break;
            case "org.autoplot.scriptconsole":
                s= "org.virbo.autoplot.scriptconsole";
                break;
            case "org.autoplot.datasource":
                s= "org.virbo.datasource";
                break;
            default:
                break;
        }
        Preferences p2= Preferences.userRoot().node("/"+s.replace('.','/'));
        return new MigratePreference(p1,p2); // use org.autoplot before org.virbo.
        
    }
    
    /**
     * autoplotData is the home where Autoplot's metadata is kept.
     * This includes history (bookmarks), add-on tools, and the
     * secondary vfsCache.
     */
    protected String autoplotData = ""; // see loadPreferences
    public static final String PROP_AUTOPLOTDATA = "autoplotData";

    /**
     * return the location where Autoplot is storing its data.  This
     * includes bookmarks, history, and the file cache, and 
     * is typically "${HOME}/autoplot_data"
     * @return the user's Autoplot data folder.
     */
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

    /**
     * cache the resolved autoplotData property.
     */
    private String resolvedAutoplotData= null;
    
    private static String maybeFixWindows( String n ) {
        if ( System.getProperty("os.name").toLowerCase().startsWith("win") ) {
            return n.replace('\\','/');
        } else {
            return n;
        }
    }
    
    /**
     * resolve the property, resolving references.  For example, ${HOME} 
     * is replaced with System.getProperty("user.home").
     * @param name the name to resolve, such as PROP_AUTOPLOTDATA or PROP_FSCACHE
     * @return the value with references resolved.
     * TODO: this should always make result end in slash...
     */
    public String resolveProperty( String name ) {
        if ( name.equals("autoplotData") ) {
            String l= resolvedAutoplotData;
            if ( l!=null ) return l;
            String s=  getAutoplotData().replace("${HOME}", maybeFixWindows( System.getProperty("user.home") ) );
            File f= new File(s);
            f= f.getAbsoluteFile();
            resolvedAutoplotData= f.toString();
            return maybeFixWindows(f.toString());
        } else if ( name.equals("fscache" ) ) {
            String result= getFscache();
            result= result.replace("${autoplotData}", resolveProperty("autoplotData") );
            result= result.replace("${HOME}", maybeFixWindows( System.getProperty("user.home") ) );
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
