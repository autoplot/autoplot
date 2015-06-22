/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.util.Arrays;
import javax.swing.JOptionPane;
import org.virbo.jythonsupport.ui.NamedURIListTool;

/**
 *
 * @author jbf
 */
public class TestDashup {
       public static void main( String[] args ) {
        NamedURIListTool n= new NamedURIListTool();
        
        n.setIds( Arrays.asList( "data1", "data2" ) );
        n.setUris( Arrays.asList("http://autoplot.org/data/autoplot.org?","http://autoplot.org/data/autoplot.org?") );
        n.refresh();
        JOptionPane.showConfirmDialog(null,n);
    }
 
}
