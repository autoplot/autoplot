/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.ascii;

import javax.swing.JOptionPane;

/**
 *
 * @author jbf
 */
public class EditorTest {
    public static void main(String[] args ) {
        AsciiDataSourceEditorPanel panel= new AsciiDataSourceEditorPanel();
        
        panel.setUrl("file:///media/mini/data.backup/examples/dat/A1050412.TXT?skip=23");

        JOptionPane.showConfirmDialog( null, panel );
        
        System.err.println( panel.getUrl() );
        
        
    }
}
