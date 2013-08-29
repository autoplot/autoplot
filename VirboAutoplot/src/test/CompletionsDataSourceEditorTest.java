/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.awt.Dialog;
import javax.swing.JDialog;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.datasource.CompletionsDataSourceEditor;

/**
 * demonstrate the Completions-based GUI.  The gui queries completions and produces droplists and buttons based
 * on that.
 * @author jbf
 */
public class CompletionsDataSourceEditorTest {
    public static void main( String[] args ) throws Exception {
        
        String uri= "vap+dat:http://autoplot.org/data/autoplot.dat";
        
        JDialog parent= new JDialog( (Dialog)null, true  );
        
        CompletionsDataSourceEditor edit= new CompletionsDataSourceEditor();
        if ( true ) { // edit.reject( uri ) ) {
            if ( edit.prepare( uri, parent, new NullProgressMonitor() ) ) {
                edit.setURI(uri);
                parent.getContentPane().add(edit.getPanel());
                parent.pack();
                parent.setVisible(true);
                System.err.println( edit.getURI() );
            }            
        }
    }    
}
