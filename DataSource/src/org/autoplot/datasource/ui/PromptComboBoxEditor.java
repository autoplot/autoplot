
package org.autoplot.datasource.ui;

import javax.swing.plaf.basic.BasicComboBoxEditor;

/**
 * 
 * @author jbf
 */
public class PromptComboBoxEditor extends BasicComboBoxEditor {

    public PromptComboBoxEditor( String prompt ) {
        super();
        editor= new PromptTextField(prompt);
    }
    
    /**
     * set the tooltip
     * @param text
     */
    public void setTooltipText( String text ) {
        this.editor.setToolTipText(text);
    }
    
    /**
     * set the grey italicized text when the text field is empty.
     * @param prompt 
     */
    public void setPromptText( String prompt ) {
        ((PromptTextField)this.editor).setPromptText(prompt);
    }
    
}
