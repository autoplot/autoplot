/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

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
    
    public void setTooltipText( String text ) {
        this.editor.setToolTipText(text);
    }
    
}
