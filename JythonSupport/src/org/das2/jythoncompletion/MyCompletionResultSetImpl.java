/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.das2.jythoncompletion;

import java.util.ArrayList;
import java.util.List;
import org.das2.jythoncompletion.support.CompletionItem;
import org.das2.jythoncompletion.ui.CompletionResultSetImpl;

/**
 *
 * @author jbf
 */
public class MyCompletionResultSetImpl  {
    List<CompletionItem> results= new ArrayList<CompletionItem>();
    
    void addItem( CompletionItem item ) {
        results.add(item);
    }
    
    void finish() {
        
    }
    
    public List<CompletionItem> getResults() {
        return this.results;
    }
    
}
