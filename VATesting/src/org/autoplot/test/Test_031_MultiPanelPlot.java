/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.test;


import java.awt.Point;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.netbeans.jemmy.JemmyProperties;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.TestOut;
import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JMenuBarOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JCheckBoxOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.operators.JPopupMenuOperator;
import org.netbeans.jemmy.operators.JTabbedPaneOperator;
import org.netbeans.jemmy.operators.JTableOperator;
import org.netbeans.jemmy.operators.JTextComponentOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.autoplot.AutoplotUI;
import org.autoplot.ScriptContext;
import static org.autoplot.ScriptContext.save;
import static org.autoplot.ScriptContext.writeToPng;
import org.autoplot.dom.Application;
import util.RegexComponentChooser;

/**
 *This more advanced example shows how to create a 2x3 stack of 6 plots to look at the components of a fits file.
 *
 *   1) Add 2x3 empty plots using layout tab, plots context menu, Canvas->AddPlots
 *   2) Edit DOM, plotElements[*].dataSourceFilterId='data_1' so they all plot the same data
 *   3) Edit DOM, plotElements[*].component slice0(i) to plot each element
 *   4) layout tab, plots context menu, Canvas->Add Hidden Plot to bind all the plot X and Y axes together
 *   5) demo XY binding, all Z axes should be automatically zoomed to just their slice. 
 *   
 * @author kenziemclouth
 */
public class Test_031_MultiPanelPlot implements Scenario {
    
        @Override
    public int runIt(Object o) {

        try {
            ScriptContext.createGui();
            
            AutoplotUI app= (AutoplotUI) ScriptContext.getViewWindow();
            
            JemmyProperties.setCurrentOutput(TestOut.getNullOutput());
            
            JFrameOperator mainFrame = new JFrameOperator(app);
            
            // wait for the application to be in the "ready" state.
            new JLabelOperator(mainFrame).waitText( AutoplotUI.READY_MESSAGE );
            
            Thread.sleep(500);
            
            JMenuBarOperator menuBar = new JMenuBarOperator( mainFrame );
            menuBar.pushMenu("Options|Enable Feature|Layout Panel", "|");
            
            ScriptContext.waitUntilIdle();
            
            new JTabbedPaneOperator( app.getTabs() ).selectPage("layout");
            
            ScriptContext.waitUntilIdle();
            
            JButtonOperator tallerB = new JButtonOperator( mainFrame, "Taller" );
            tallerB.clickMouse(4); // This is the number of clicks, for example 2 is double-click.
            
            System.err.println( tallerB.getSource().getParent() );
            System.err.println( "clickPoint: "+ tallerB.getSource().getParent() );
            Point clickPoint= tallerB.getSource().getParent().getLocation();
            clickPoint= SwingUtilities.convertPoint( tallerB.getSource().getParent(), clickPoint, mainFrame.getSource() );
            mainFrame.clickForPopup(clickPoint.x+50, clickPoint.y+50 );
            
            JPopupMenuOperator popup = new JPopupMenuOperator();
            popup.pushMenuNoBlock("Plot|Add Plots", "|"); // I think because this is a "modal" dialog.
            
            Thread.sleep(200);

            DialogOperator frame = new DialogOperator( new RegexComponentChooser( "Add Plots") );
                       
            JTextComponentOperator size = new JTextComponentOperator( frame, 1 ); 
            size.enterText("2"); // enterText, not setText, or the values don't commit. (Huh.)
            JTextComponentOperator size1 = new JTextComponentOperator( frame, 0 ); 
            size1.enterText("3");
            new JButtonOperator(frame,"OK").clickMouse();
            
            ScriptContext.waitUntilIdle();

            while ( frame.isVisible() ) {
                Thread.sleep(100);  // Why does the press take so long???
            }
            
            ScriptContext.plot(1,"vap+fits:http://autoplot.org/data/hsi_qlimg_5050601_001.fits"); // small cheat
            
            //for mac 
            //mainFrame.clickMouse(clickPoint.x+50, clickPoint.y-130); 
            //mainFrame.clickMouse(clickPoint.x+150, clickPoint.y-110); 
            
            //System.err.println( "***** clickPoint= x:"+ (clickPoint.x+50) + " y:"+ (clickPoint.y-130)  );
            
            //for pc
            //mainFrame.clickMouse(clickPoint.x+50, clickPoint.y-130, 2);
            //mainFrame.clickForPopup(clickPoint.x+50, clickPoint.y-130);
            //mainFrame.clickForPopup(clickPoint.x+50, clickPoint.y-130);
            
            //JPopupMenuOperator popup1 = new JPopupMenuOperator();
            //popup1.pushMenuNoBlock("Plot|Delete", "|");
            
            Application dom= app.getDocumentModel();
            dom.getController().deletePlot( dom.getPlots(0) );
            
            Thread.sleep(1000);
            // wait for the application to be in the "ready" state
            ScriptContext.waitUntilIdle();
            
            //Open DOM Properties
            menuBar.pushMenu("Edit|Edit DOM", "|");
            DialogOperator domProps = new DialogOperator( new RegexComponentChooser( "DOM Properties") );
            
            JTableOperator domTable = new JTableOperator( domProps);
            JTextFieldOperator dataSourceFilt;
            
            //Open Plot Elements Tree
            domTable.selectCell(domTable.findCellRow("plotElements[]"), 0);

            
            //Plot Element 1
            domTable.selectCell(domTable.findCellRow("plotElements[0]"), 0);
            domTable.selectCell(domTable.findCellRow("component", true, true),1);
            JTextFieldOperator component = new JTextFieldOperator(domTable);
            component.setText("|slice0(0)");
            Thread.sleep(400);
            domTable.selectCell(domTable.findCellRow("plotElements[0]"),0);
            Thread.sleep(400);
            
            for( int i = 1; i<6 ; i = i + 1)
            {
                domTable.selectCell(domTable.findCellRow("plotElements[" + i + "]"), 0);
                domTable.selectCell(domTable.findCellRow("dataSourceFilterId", true, true),1);
                new JTextFieldOperator(domTable).setText("data_1");
                Thread.sleep(200);
                domTable.selectCell(domTable.findCellRow("component", true, true),1);
                domTable.selectCell(domTable.findCellRow("component", true, true),1);
                component = new JTextFieldOperator(domTable);
                component.setText("|slice0(" + i + ")");
                domTable.selectCell(domTable.findCellRow("plotElements[" + i + "]"), 0);
                Thread.sleep(200);

            } 
            
            
          
            Thread.sleep(400);
            
            new JButtonOperator(domProps, "Apply").clickMouse();
            new JButtonOperator(domProps, "OK").clickMouse();
            
            ScriptContext.waitUntilIdle();
            while ( domProps.isVisible() ) {
                Thread.sleep(100);  // Why does the press take so long???
            }
            
            Thread.sleep(1000);
            
            //Add Hidden Plot to bind all the plot X and Y axes together
            System.err.println( "***** clickPoint AHP= X:"+ (clickPoint.x+60) + " Y:"+ (clickPoint.y-130 ) );
            
            mainFrame.requestFocus();
            Thread.sleep(100);
            
            //mainFrame.clickForPopup(clickPoint.x+200, clickPoint.y );
            //Thread.sleep(500);
            
            //popup = new JPopupMenuOperator();
            //popup.pushMenuNoBlock("Canvas|Add Hidden Plot...", "|");
            //Thread.sleep(200);
            
//            DialogOperator addhiddenplot = new DialogOperator( new RegexComponentChooser( "Add hidden plot for binding.*") );
//            new JCheckBoxOperator(addhiddenplot, 2).setSelected(false);
//            new JButtonOperator(addhiddenplot, "OK").clickMouse();
//            
//            ScriptContext.waitUntilIdle();
//            while ( addhiddenplot.isVisible() ) {
//                Thread.sleep(100);  // Why does the press take so long???
//            }
//            
//            new JTabbedPaneOperator( app.getTabs() ).selectPage("canvas");
//            Thread.sleep(5000);  
//            
//            System.err.println("Done!");
//            
            writeToPng("Test_031_MultiPanelPlot.png"); // Leave artifacts for testing.
            save("Test_031_MultiPanelPlot.vap");
            
            return(0);
        } catch (InterruptedException ex) {
            Logger.getLogger(Test_031_MultiPanelPlot.class.getName()).log(Level.SEVERE, null, ex);
            return(1);
        } catch (IOException ex) {
            Logger.getLogger(Test_031_MultiPanelPlot.class.getName()).log(Level.SEVERE, null, ex);
            return(2);
        }
    }
    
    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_031_MultiPanelPlot"};
	org.netbeans.jemmy.Test.main(params);
    }
    
}
