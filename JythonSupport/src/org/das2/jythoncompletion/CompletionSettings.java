/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.das2.jythoncompletion;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.das2.jythoncompletion.ui.CompletionImpl;
import org.das2.util.LoggerManager;

/**
 *
 * @author jbf
 */
public class CompletionSettings {

    private static final Logger logger= LoggerManager.getLogger( "jython.editor" );
    Preferences prefs= Preferences.userNodeForPackage( CompletionSettings.class );

    protected String docHome = "http://apps-pw.physics.uiowa.edu/hudson/job/autoplot-javadoc/ws/doc/";
    public static final String PROP_DOCHOME = "docHome";

    public String getDocHome() {
        return docHome;
    }

    public void setDocHome(String docHome) {
        String oldDocHome = docHome;
        this.docHome = docHome;
        propertyChangeSupport.firePropertyChange(PROP_DOCHOME, oldDocHome, docHome);
        prefs.put( PROP_DOCHOME, docHome );
        try {
            prefs.flush();
        } catch ( BackingStoreException ex ) {
            logger.log( Level.SEVERE, null, ex );
        }
    }

    public static final String PROP_TAB_IS_COMPLETION = "tabIsCompletion";

    private boolean tabIsCompletion = true;

    public boolean isTabIsCompletion() {
        return tabIsCompletion;
    }

    public void setTabIsCompletion(boolean tabIsCompletion) {
        boolean old= this.tabIsCompletion;
        this.tabIsCompletion = tabIsCompletion;
        propertyChangeSupport.firePropertyChange(PROP_TAB_IS_COMPLETION, old, tabIsCompletion );
        try {
            prefs.putBoolean( PROP_TAB_IS_COMPLETION, tabIsCompletion );
            prefs.flush();
        } catch ( BackingStoreException ex ) {
            logger.log( Level.SEVERE, null, ex );
        }
        CompletionImpl.get().setTabIsCompletion(tabIsCompletion);
    }

    protected boolean safeCompletions = true;
    public static final String PROP_SAFE_COMPLETIONS = "safeCompletions";

    public boolean isSafeCompletions() {
        return safeCompletions;
    }

    public void setSafeCompletions(boolean safeCompletions) {
        boolean old = safeCompletions;
        this.safeCompletions = safeCompletions;
        propertyChangeSupport.firePropertyChange(PROP_SAFE_COMPLETIONS, old, safeCompletions);
        prefs.putBoolean( PROP_SAFE_COMPLETIONS, safeCompletions );
        try {
            prefs.flush();
        } catch ( BackingStoreException ex ) {
            logger.log( Level.SEVERE, null, ex );
        }
    }
    
    private String editorFont = "sans-12";
    
    public static final String PROP_EDITORFONT = "editorFont";

    public String getEditorFont() {
        return editorFont;
    }

    public void setEditorFont(String editorFont) {
        String oldEditorFont = this.editorFont;
        this.editorFont = editorFont;
        propertyChangeSupport.firePropertyChange(PROP_EDITORFONT, oldEditorFont, editorFont);
        prefs.put( PROP_EDITORFONT, editorFont );
        try {
            prefs.flush();
        } catch ( BackingStoreException ex ) {
            logger.log( Level.SEVERE, null, ex );
        }
    }


    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    public void loadPreferences() {
        docHome= prefs.get( PROP_DOCHOME, "http://apps-pw.physics.uiowa.edu/hudson/job/autoplot-javadoc/ws/doc/" ) ;
        tabIsCompletion= prefs.getBoolean( PROP_TAB_IS_COMPLETION, true );
        editorFont= prefs.get( PROP_EDITORFONT, "sans-12" );
    }

}
