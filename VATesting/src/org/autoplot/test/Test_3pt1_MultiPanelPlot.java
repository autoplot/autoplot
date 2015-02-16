/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.test;


import java.awt.Point;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.netbeans.jemmy.Scenario;
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
import org.virbo.autoplot.AutoplotUI;
import org.virbo.autoplot.ScriptContext;
import static org.virbo.autoplot.ScriptContext.save;
import static org.virbo.autoplot.ScriptContext.writeToPng;
import util.RegexComponentChooser;

/**
 *
 * @author kenziemclouth
 */
public class Test_3pt1_MultiPanelPlot implements Scenario {
    
        @Override
    public int runIt(Object o) {

        try {
            ScriptContext.createGui();
            
            AutoplotUI app= (AutoplotUI) ScriptContext.getViewWindow();
            
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
            
            Point clickPoint= tallerB.getLocation();
            clickPoint= SwingUtilities.convertPoint( tallerB.getSource().getParent(), clickPoint, mainFrame.getSource() );
            mainFrame.clickForPopup(clickPoint.x+50, clickPoint.y-100 );
            
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
            
            //for pc
            mainFrame.clickMouse(clickPoint.x+50, clickPoint.y-130, 2);
            mainFrame.clickForPopup(clickPoint.x+50, clickPoint.y-130);
            mainFrame.clickForPopup(clickPoint.x+50, clickPoint.y-130);
            
            JPopupMenuOperator popup1 = new JPopupMenuOperator();
            popup1.pushMenuNoBlock("Plot|Delete", "|");
            
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
            component.setText("slice0(0)");
            Thread.sleep(400);
            domTable.selectCell(domTable.findCellRow("plotElements[0]"),0);
            Thread.sleep(400);
            
            
            //Plot Element 2
            domTable.selectCell(domTable.findCellRow("plotElements[1]"), 0);
            domTable.selectCell(domTable.findCellRow("component", true, true),1);
            domTable.selectCell(domTable.findCellRow("component", true, true),1);
            component = new JTextFieldOperator(domTable);
            component.setText("slice0(1)");
            Thread.sleep(400);
            domTable.selectCell(domTable.findCellRow("dataSourceFilterId", true, true),1);
            new JTextFieldOperator(domTable).setText("data_1");
            domTable.selectCell(domTable.findCellRow("plotElements[1]"), 0);
            Thread.sleep(400);
            
            //Plot Element 3
            domTable.selectCell(domTable.findCellRow("plotElements[2]"), 0);
            domTable.selectCell(domTable.findCellRow("component", true, true),1);
            domTable.selectCell(domTable.findCellRow("component", true, true),1);
            component = new JTextFieldOperator(domTable);
            component.setText("slice0(2)");
            Thread.sleep(400);
            domTable.selectCell(domTable.findCellRow("dataSourceFilterId", true, true),1);
            dataSourceFilt = new JTextFieldOperator(domTable);
            dataSourceFilt.setText("data_1");
            domTable.selectCell(domTable.findCellRow("plotElements[2]"), 0);
            Thread.sleep(400);
            
            //Plot Element 4
            domTable.selectCell(domTable.findCellRow("plotElements[3]"), 0);
            domTable.selectCell(domTable.findCellRow("component", true, true),1);
            domTable.selectCell(domTable.findCellRow("component", true, true),1);
            component = new JTextFieldOperator(domTable);
            component.setText("slice0(3)");
            Thread.sleep(400);
            domTable.selectCell(domTable.findCellRow("dataSourceFilterId", true, true),1);
            dataSourceFilt = new JTextFieldOperator(domTable);
            dataSourceFilt.setText("data_1");
            domTable.selectCell(domTable.findCellRow("plotElements[3]"), 0);
            Thread.sleep(400);
            
            //Plot Element 5
            domTable.selectCell(domTable.findCellRow("plotElements[4]"), 0);
            domTable.selectCell(domTable.findCellRow("component", true, true),1);
            domTable.selectCell(domTable.findCellRow("component", true, true),1);
            component = new JTextFieldOperator(domTable);
            component.setText("slice0(4)");
            Thread.sleep(400);
            domTable.selectCell(domTable.findCellRow("dataSourceFilterId", true, true),1);
            dataSourceFilt = new JTextFieldOperator(domTable);
            dataSourceFilt.setText("data_1");
            domTable.selectCell(domTable.findCellRow("plotElements[4]"), 0);
            Thread.sleep(400);
            
            //Plot Element 6
            domTable.selectCell(domTable.findCellRow("plotElements[5]"), 0);
            domTable.selectCell(domTable.findCellRow("component", true, true),1);
            domTable.selectCell(domTable.findCellRow("component", true, true),1);
            component = new JTextFieldOperator(domTable);
            component.setText("slice0(5)");
            Thread.sleep(400);
            domTable.selectCell(domTable.findCellRow("dataSourceFilterId", true, true),1);
            dataSourceFilt = new JTextFieldOperator(domTable);
            dataSourceFilt.setText("data_1");
            domTable.selectCell(domTable.findCellRow("plotElements[5]"), 0);
            Thread.sleep(400);
            
            new JButtonOperator(domProps, "Apply").clickMouse();
            new JButtonOperator(domProps, "OK").clickMouse();
            
            ScriptContext.waitUntilIdle();
            while ( domProps.isVisible() ) {
                Thread.sleep(100);  // Why does the press take so long???
            }
            
            //Add Hidden Plot to bind all the plot X and Y axes together
            mainFrame.clickForPopup(clickPoint.x+200, clickPoint.y-100 );
            popup = new JPopupMenuOperator();
            popup.pushMenuNoBlock("Canvas|Add Hidden Plot...", "|");
            Thread.sleep(200);
            
            DialogOperator addhiddenplot = new DialogOperator( new RegexComponentChooser( "Add hidden plot for binding.*") );
            new JCheckBoxOperator(addhiddenplot, 2).setSelected(false);
            new JButtonOperator(addhiddenplot, "OK").clickMouse();
            
            ScriptContext.waitUntilIdle();
            while ( addhiddenplot.isVisible() ) {
                Thread.sleep(100);  // Why does the press take so long???
            }
            
            new JTabbedPaneOperator( app.getTabs() ).selectPage("canvas");
            Thread.sleep(5000);  
            
            System.err.println("Done!");
            
            writeToPng("Test_3pt1_MultiPanelPlot.png"); // Leave artifacts for testing.
            save("Test_3pt1_MultiPanelPlot.vap");
            
            return(0);
        } catch (InterruptedException ex) {
            Logger.getLogger(Test_3pt1_MultiPanelPlot.class.getName()).log(Level.SEVERE, null, ex);
            return(1);
        } catch (IOException ex) {
            Logger.getLogger(Test_3pt1_MultiPanelPlot.class.getName()).log(Level.SEVERE, null, ex);
            return(2);
        }
    }
    
    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_3pt1_MultiPanelPlot"};
	org.netbeans.jemmy.Test.main(params);
    }
    
}
