/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.test;


import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.jemmy.JemmyProperties;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.TestOut;
import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JMenuBarOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.operators.JSpinnerOperator;
import org.netbeans.jemmy.operators.JTabbedPaneOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.netbeans.jemmy.operators.JListOperator;
import org.netbeans.jemmy.operators.JScrollPaneOperator;
import org.autoplot.AutoplotUI;
import org.autoplot.ScriptContext2023;
import util.RegexComponentChooser;

/** 
 *   1) Plot vap+inline:ripples(100,100)+randn(100)/50+outerProduct(ones(100),randn(110)/50)
 *   2) Enter data tab
 *   3) Add a filter by clicking (+) button
 *   4) Add slice0 filter and set operations test field to "10"
 *   5) Add "add filter" and set parameter to 10.
 *   6) After each filter is added, go to canvas view to show changes to canvas.
 * 
 * @author kenziemclouth
 */
public class Test_032_FilterChainGUI implements Scenario {
    private static final ScriptContext2023 scriptContext= ScriptContext2023.getInstance();
        
    @Override
    public int runIt(Object o) {

        JemmyProperties.setCurrentOutput(TestOut.getNullOutput());

        try {
            scriptContext.createGui();
            
            AutoplotUI app= (AutoplotUI) scriptContext.getViewWindow();
            
            JFrameOperator mainFrame = new JFrameOperator(app);
        
            // wait for the application to be in the "ready" state.
            new JLabelOperator(mainFrame).waitText( AutoplotUI.READY_MESSAGE );
            
            Thread.sleep(500);
            
            // plot test dataset
            new JTextFieldOperator(app.getDataSetSelector().getEditor()).setText("vap+inline:ripples(100,110)+randn(100)/50+outerProduct(ones(100),randn(110)/50)");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();
            
            if ( app.getTabs().getTabByTitle("data")==null ) {
                new JMenuBarOperator( mainFrame ).pushMenu("Options|Enable Feature|Data Panel", "|");
            }
            
            Thread.sleep(500);
            new JLabelOperator(mainFrame).waitText( AutoplotUI.READY_MESSAGE );
            
            JTabbedPaneOperator tabsPane = new JTabbedPaneOperator( app.getTabs() );
            tabsPane.selectPage("data");
            

            JScrollPaneOperator scrollPane = new JScrollPaneOperator(mainFrame, 1);
            
            JButtonOperator subAdd = new JButtonOperator(scrollPane);
            subAdd.pushNoBlock();
            Thread.sleep(1000);
            
            //add the "slice0()" filter      
            DialogOperator addFilterFrame = new DialogOperator( new RegexComponentChooser( "Add Operation") );           
            new JTabbedPaneOperator( addFilterFrame ).selectPage("Alphabetical");
            JListOperator filterList = new JListOperator(addFilterFrame);
            filterList.selectItem(filterList.findItemIndex("Slice0", true, false));
            Thread.sleep(500);
            new JButtonOperator(addFilterFrame,"OK").clickMouse();         
            
            for( int i = 0; i<10 ; i = i + 1)
            {
                new JSpinnerOperator(scrollPane).getIncreaseOperator().clickMouse(1);
                Thread.sleep(100);
            }
            
            Thread.sleep(500);
            
            //Display canvas to show changes made my filter
            tabsPane.selectPage("canvas");
            Thread.sleep(1000);
            tabsPane.selectPage("data");
            
            new JButtonOperator(scrollPane).pushNoBlock();
            
            //add the "add" filter
            addFilterFrame = new DialogOperator( new RegexComponentChooser( "Add Operation") );
            new JTabbedPaneOperator( addFilterFrame ).selectPage("Alphabetical");
            filterList = new JListOperator(addFilterFrame);
            Thread.sleep(500);
            filterList.selectItem(filterList.findItemIndex("Add", true, false)); //select Add
            Thread.sleep(1000);
            new JButtonOperator(addFilterFrame,"OK").clickMouse();
            Thread.sleep(500);
            new JTextFieldOperator(scrollPane, "1.").setText("10");
            Thread.sleep(500);
            
            //Display canvas to show changes made my filter
            tabsPane.selectPage("canvas");
            Thread.sleep(5000);
            tabsPane.selectPage("data");
            Thread.sleep(2000);
            
            System.err.println("Done!");
            
            scriptContext.writeToPng("Test_032_FilterChainGUI.png"); // Leave artifacts for testing.
            scriptContext.save("Test_032_FilterChainGUI.vap");
            
            
            return(0);
        } catch (InterruptedException ex) {
            Logger.getLogger(Test_032_FilterChainGUI.class.getName()).log(Level.SEVERE, null, ex);
            return(1);
        } catch (IOException ex) {
            Logger.getLogger(Test_032_FilterChainGUI.class.getName()).log(Level.SEVERE, null, ex);
            return(2);
        }
        
    }
    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_032_FilterChainGUI"};
	org.netbeans.jemmy.Test.main(params);
    }
    
}
