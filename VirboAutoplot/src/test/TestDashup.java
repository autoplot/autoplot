/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.util.Arrays;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import org.autoplot.datasource.DataSetSelector;
import org.autoplot.jythonsupport.ui.DataMashUp;
import org.autoplot.jythonsupport.ui.NamedURIListTool;

/**
 *
 * @author jbf
 */
public class TestDashup {
    public static void main( String[] args ) {
        test1();
        DataSetSelector.setDefaultRecent( Arrays.asList( "http://autoplot.org/data/autoplot.csv?","http://autoplot.org/data/autoplot.cdf?") );
        //test3();
    }
    
    public static void test3() {
        DataMashUp dmu= new DataMashUp();
        dmu.setAsJythonInline("vap+inline:ds1=getDataSet('http://autoplot.org/data/autoplot.cdf?Magnitude')&ds2=getDataSet('http://autoplot.org/data/autoplot.cdf?BGSM&slice1=0')&add(ds1,ds2)");
        System.err.println(dmu.getAsJythonInline());
    }
    
    public static void test2() {
        DataMashUp dmu= new DataMashUp();
        dmu.setIds( Arrays.asList( "x", "y" ) );
        dmu.setUris( Arrays.asList("http://autoplot.org/data/autoplot.cdf?Magnitude","http://autoplot.org/data/autoplot.cdf?BGSEc&slice1=2") );
        JDialog dia= new JDialog();
        dia.setResizable(true);
        dia.getContentPane().add(dmu);
        dia.setModal(true);
        dia.pack();
        dia.setVisible(true);
        
        System.err.println( dmu.getAsJythonInline() );
        
        //JOptionPane.showConfirmDialog( null, dmu, "Data Mashup, or Dashup", JOptionPane.OK_OPTION );
    }
    
    public static void test1() {
        NamedURIListTool n= new NamedURIListTool();
        n.setIds( Arrays.asList( "data1", "data2" ) );
        n.setUris( Arrays.asList("http://autoplot.org/data/autoplot.cdf?","http://autoplot.org/data/autoplot.cdf?") );
        n.refresh();
        while ( true ) {
            JOptionPane.showConfirmDialog(null,n);
            System.err.println("----");
            for ( String s: n.getUris() ) {
                System.err.println( s );
            }
            
        }
    }
 
    
}
