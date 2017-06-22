/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.excel;

import javax.swing.JDialog;
import javax.swing.JFrame;

/**
 *
 * @author jbf
 */
public class EditorTest {
    public static void main(String[] args ) {
        ExcelSpreadsheetDataSourceEditorPanel panel= new ExcelSpreadsheetDataSourceEditorPanel();
        
        panel.setURI("file:///media/mini/data.backup/examples/xls/iowaCitySales2006-2008.xls");
        //panel.setUrl("file:///c:/Documents and Settings/jbf/Desktop/Product Summary.xls?sheet=nist+lo&firstRow=53&column=Phase_Angle&depend0=Time");
        JDialog dia= new JDialog( (JFrame)null, "Excel Customizer", true );
        dia.setResizable(true);
        
        dia.add( panel );
        dia.pack();
        
        dia.setVisible(true);
        
        
        System.err.println( panel.getURI() );
        
        
    }
}
