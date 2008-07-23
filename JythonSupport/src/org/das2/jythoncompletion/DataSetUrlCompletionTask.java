/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.das2.jythoncompletion;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import org.das2.jythoncompletion.support.CompletionResultSet;
import org.das2.jythoncompletion.support.CompletionTask;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSetURL.CompletionResult;
import org.virbo.datasource.DataSourceRegistry;

/**
 *
 * @author jbf
 */
class DataSetUrlCompletionTask implements CompletionTask {

    JTextComponent editor;
    
    public DataSetUrlCompletionTask(JTextComponent arg1) {
        this.editor= arg1;
    }

    public void cancel() {

    }

    public void query( CompletionResultSet arg0 ) {
        try {
            int i0 = Utilities.getRowStart(editor, editor.getCaretPosition());
            int i1 = Utilities.getRowEnd(editor, editor.getCaretPosition())-1; // trim end of line
            String line = editor.getText(i0, i1 - i0);
            int ipos = editor.getCaretPosition() - i0;
            i0 = line.lastIndexOf('\'', ipos-1);
            if (i0 == -1) {
                throw new IllegalArgumentException("expected single quote");
            }
	    i0+=1;
            i1 = line.indexOf('\'', ipos);
            if (i1 == -1) {
                i1 = line.length();
            }
            String surl1 = line.substring(i0, i1);
            int carotPos = ipos - i0;
            
            List<CompletionResult> rs;
             
            DataSetURL.URLSplit split = DataSetURL.parse(surl1);            
            if ( surl1.contains("?") || DataSourceRegistry.getInstance().hasSourceByExt(split.ext) ) {
                rs= DataSetURL.getCompletions3( surl1, carotPos,  new NullProgressMonitor() );
            } else {
                rs= DataSetURL.getFileSystemCompletions(surl1, carotPos, new NullProgressMonitor() );
            }
            
            for ( CompletionResult rs1:rs ) {
                arg0.addItem( new DataSetUrlCompletionItem(rs1) );
            }
        } catch (BadLocationException ex) {
            ex.printStackTrace();
            Logger.getLogger(DataSetUrlCompletionTask.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            ex.printStackTrace();
            Logger.getLogger(DataSetUrlCompletionTask.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void refresh( CompletionResultSet arg0 ) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
