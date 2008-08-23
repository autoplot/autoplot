/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.scriptconsole;

import java.awt.event.MouseEvent;
import javax.swing.JTextPane;

/**
 *
 * @author jbf
 */
public class EditorTextPane extends JTextPane {
    
    private EditorAnnotationsSupport support= new EditorAnnotationsSupport( this );

    @Override
    public String getToolTipText( MouseEvent event ) {
        return support.getToolTipText(event);
    }

    public void setEditorAnnotationsSupport( EditorAnnotationsSupport support ) {
        this.support= support;
    }
    
    public EditorAnnotationsSupport getEditorAnnotationsSupport() {
        return support;
    }
}
