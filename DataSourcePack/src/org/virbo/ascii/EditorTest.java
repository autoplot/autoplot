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
        AsciiTableDataSourceEditorPanel panel= new AsciiTableDataSourceEditorPanel();

        //String url= "file:///media/mini/data.backup/examples/dat/omni2_1965.dat?timeFormat=$Y+$j&skip=22";
        String url= "file:///media/mini/data.backup/examples/dat/A1050412.TXT";
        panel.setURI(url);

        JOptionPane.showConfirmDialog( null, panel );
        
        System.err.println( panel.getURI() ); // logger okay
        
        
    }
}
