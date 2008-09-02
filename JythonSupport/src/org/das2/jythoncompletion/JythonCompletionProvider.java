/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.das2.jythoncompletion;

import javax.swing.text.JTextComponent;
import org.das2.jythoncompletion.support.CompletionProvider;
import org.das2.jythoncompletion.support.CompletionTask;

/**
 *
 * @author jbf
 */
public class JythonCompletionProvider implements CompletionProvider {

    CompletionSettings settings;
    
    private JythonCompletionProvider() {
        settings= new CompletionSettings();
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
        return new JythonCompletionTask( arg1 );
    }

    public int getAutoQueryTypes( JTextComponent arg0, String arg1 ) {
        return 0;
    }


}
