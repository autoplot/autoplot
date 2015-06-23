/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.util.Arrays;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import org.virbo.jythonsupport.ui.DataMashUp;
import org.virbo.jythonsupport.ui.NamedURIListTool;

/**
 *
 * @author jbf
 */
public class TestDashup {
    public static void main( String[] args ) {
        //test1();
        test2();
    }
    
    public static void test2() {
        DataMashUp dmu= new DataMashUp();
        JDialog dia= new JDialog();
        dia.setResizable(true);
        dia.getContentPane().add(dmu);
        dia.pack();
        dia.setVisible(true);
        //JOptionPane.showConfirmDialog( null, dmu, "Data Mashup, or Dashup", JOptionPane.OK_OPTION );
    }
    
    public static void test1() {
        NamedURIListTool n= new NamedURIListTool();
        
        n.setIds( Arrays.asList( "data1", "data2" ) );
        n.setUris( Arrays.asList("http://autoplot.org/data/autoplot.cdf?","http://autoplot.org/data/autoplot.cdf?") );
        n.refresh();
        JOptionPane.showConfirmDialog(null,n);
    }
 
}
