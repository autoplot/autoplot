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
import org.autoplot.datasource.AutoplotSettings;
import org.das2.jythoncompletion.ui.CompletionImpl;
import org.das2.util.LoggerManager;

/**
 *
 * @author jbf
 */
public class CompletionSettings {

    private static final Logger logger= LoggerManager.getLogger( "jython.editor" );
    Preferences prefs= AutoplotSettings.settings().getPreferences( CompletionSettings.class );

    private String docHome = "https://cottagesystems.com/~jbf/autoplot/doc/"; // // see repeat code in loadPreferences
    
    public static final String PROP_DOCHOME = "docHome";

    public String getDocHome() {
        if ( !docHome.endsWith("/") ) {
            return docHome+"/";
        } else {
            return docHome;
        }
    }

    public void setDocHome(String docHome) {
        String oldDocHome = docHome;
        this.docHome = docHome;
        propertyChangeSupport.firePropertyChange(PROP_DOCHOME, oldDocHome, docHome);
        prefs.put( PROP_DOCHOME, docHome );
        try {
            prefs.flush();
        } catch ( BackingStoreException ex ) {
            logger.log( Level.SEVERE, ex.getMessage(), ex );
        }
    }

    public static final String PROP_TAB_IS_COMPLETION = "tabIsCompletion";

    private boolean tabIsCompletion = true; // // see repeat code in loadPreferences

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
            logger.log( Level.SEVERE, ex.getMessage(), ex );
        }
        CompletionImpl.get().setTabIsCompletion(tabIsCompletion);
    }

    protected boolean safeCompletions = true;
    public static final String PROP_SAFE_COMPLETIONS = "safeCompletions";

    /**
     * completions should be based on a refactored code which avoids slow commands like "getDataSet".
     * @return 
     */
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
            logger.log( Level.SEVERE, ex.getMessage(), ex );
        }
    }
    
    private String editorFont = "sans-12"; // // see repeat code in loadPreferences
    
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
            logger.log( Level.SEVERE, ex.getMessage(), ex );
        }
    }
    
    private String documentationPaneSize = "640x480"; // // see repeat code in loadPreferences

    public static final String PROP_DOCUMENTATIONPANESIZE = "documentationPaneSize";

    public String getDocumentationPaneSize() {
        return documentationPaneSize;
    }

    public void setDocumentationPaneSize(String documentationPaneSize) {
        String oldDocumentationPaneSize = this.documentationPaneSize;
        this.documentationPaneSize = documentationPaneSize;
        propertyChangeSupport.firePropertyChange(PROP_DOCUMENTATIONPANESIZE, oldDocumentationPaneSize, documentationPaneSize);
        prefs.put( PROP_DOCUMENTATIONPANESIZE, documentationPaneSize );
        try {
            prefs.flush();
        } catch ( BackingStoreException ex ) {
            logger.log( Level.SEVERE, ex.getMessage(), ex );
        }
    }

    private boolean showTabs = true;

    public static final String PROP_SHOWTABS = "showTabs";

    public boolean isShowTabs() {
        return showTabs;
    }

    public void setShowTabs(boolean showTabs) {
        boolean oldShowTabs = this.showTabs;
        this.showTabs = showTabs;
        propertyChangeSupport.firePropertyChange(PROP_SHOWTABS, oldShowTabs, showTabs);
        prefs.putBoolean(PROP_SHOWTABS, showTabs );
        try {
            prefs.flush();
        } catch ( BackingStoreException ex ) {
            logger.log( Level.SEVERE, ex.getMessage(), ex );
        }
    }

    private boolean tabIsSpaces;

    /**
     * If true, then the tab keystroke inserts four spaces.
     */
    public static final String PROP_TABISSPACES = "tabIsSpaces";

    public boolean isTabIsSpaces() {
        return tabIsSpaces;
    }

    public void setTabIsSpaces(boolean tabIsSpaces) {
        boolean oldTabIsSpaces = this.tabIsSpaces;
        this.tabIsSpaces = tabIsSpaces;
        propertyChangeSupport.firePropertyChange(PROP_TABISSPACES, oldTabIsSpaces, tabIsSpaces);
    }


    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    public void loadPreferences() {
        docHome= prefs.get( PROP_DOCHOME, "https://cottagesystems.com/~jbf/autoplot/doc/" );
        tabIsCompletion= prefs.getBoolean( PROP_TAB_IS_COMPLETION, true );
        tabIsSpaces= prefs.getBoolean( PROP_TABISSPACES, false );
        showTabs= prefs.getBoolean( PROP_SHOWTABS, true );
        editorFont= prefs.get( PROP_EDITORFONT, "sans-12" );
        documentationPaneSize= prefs.get( PROP_DOCUMENTATIONPANESIZE, "640x480" );
    }

}
