/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.das2.jythoncompletion;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.das2.jythoncompletion.ui.CompletionImpl;

/**
 *
 * @author jbf
 */
public class CompletionSettings {

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
            ex.printStackTrace();
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
            ex.printStackTrace();
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
            ex.printStackTrace();
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
    }

}
