/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.das2.jythoncompletion;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.das2.jythoncompletion.support.AsyncCompletionQuery;
import org.das2.jythoncompletion.support.AsyncCompletionTask;
import org.das2.jythoncompletion.support.CompletionProvider;
import org.das2.jythoncompletion.support.CompletionResultSet;
import org.das2.jythoncompletion.support.CompletionTask;
import org.python.core.PyException;

/**
 *
 * @author jbf
 */
public class JythonCompletionProvider implements CompletionProvider {

    CompletionSettings settings;
    
    private JythonCompletionProvider() {
        settings= new CompletionSettings();
        settings.loadPreferences();


    }
    
    private static JythonCompletionProvider instance;
    
    public static synchronized JythonCompletionProvider getInstance() {
        if ( instance==null ) instance= new JythonCompletionProvider();
        return instance;
    }
    
    public CompletionSettings settings() {
        return settings;
    }
    
    public CompletionTask createTask( int arg0, JTextComponent arg1 ) {
        final CompletionTask syncTask= new JythonCompletionTask( arg1 );
        return new AsyncCompletionTask( new AsyncCompletionQuery() {
            @Override
            protected void query(CompletionResultSet resultSet, Document doc, int caretOffset) {
                try {
                    syncTask.query(resultSet);
                } catch ( PyException ex ) {
                    if ( resultSet.isFinished() ) {
                        setMessage("warning: "+ex.toString());
                    } else {
                        resultSet.addItem( new MessageCompletionItem(ex.getMessage()) );
                    }
                }
            }
        });
    }

    public int getAutoQueryTypes( JTextComponent arg0, String arg1 ) {
        return 0;
    }

    protected String message = null;
    public static final String PROP_MESSAGE = "message";

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        String oldMessage = this.message;
        this.message = message;
        propertyChangeSupport.firePropertyChange(PROP_MESSAGE, oldMessage, message);
    }
    
    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    public synchronized void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(propertyName, listener);
    }

    public synchronized void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
    }


}
