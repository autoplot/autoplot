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
public class DataSetUrlCompletionProvider implements CompletionProvider {

    public CompletionTask createTask(int arg0, JTextComponent arg1) {
        return new DataSetUrlCompletionTask( arg1 );
    }

    public int getAutoQueryTypes(JTextComponent arg0, String arg1) {
        throw new UnsupportedOperationException("Not supported yet.");
    }


}
