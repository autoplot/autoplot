/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.cdaweb;

import javax.swing.JOptionPane;

/**
 *
 * @author jbf
 */
public class Test {
    public static void main(String[] args ) {
        CDAWebEditorPanel p= new CDAWebEditorPanel();
        p.setURI("vap+cdaweb:");
        JOptionPane.showInputDialog(p);
    }

}
