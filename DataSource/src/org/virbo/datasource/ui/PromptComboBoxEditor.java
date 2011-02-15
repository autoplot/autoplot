/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource.ui;

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
    
}
