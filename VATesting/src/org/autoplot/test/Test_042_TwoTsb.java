/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.test;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.jemmy.ComponentChooser;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JPopupMenuOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.virbo.autoplot.AutoplotUI;
import org.virbo.autoplot.ScriptContext;
import static org.virbo.autoplot.ScriptContext.save;
import static org.virbo.autoplot.ScriptContext.writeToPng;
import org.virbo.autoplot.dom.Application;
import util.RegexComponentChooser;

/**
 * Test building a configuration with two TSBs listening to the context property of
 * each plot.
 * @author Jeremy Faden
 */
public class Test_042_TwoTsb implements Scenario {
    
    @Override
    public int runIt(Object o) {

        try {
            ScriptContext.createGui();
            
            ScriptContext.waitUntilIdle();
            
            AutoplotUI app= (AutoplotUI) ScriptContext.getViewWindow();
            
            JFrameOperator mainFrame = new JFrameOperator(app);

            new JTextFieldOperator( app.getDataSetSelector().getEditor() ).setText("http://autoplot.org/data/jyds/tsbNonTimeAxis.jyds?timerange=2000-01-03");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();
            
            Thread.sleep(10000);
            ScriptContext.waitUntilIdle();
            
            Application dom= ScriptContext.getDocumentModel();
            
            dom.getPlots(0).setDisplayLegend(true);
            dom.getPlotElements(0).setLegendLabel("%{PLOT_CONTEXT}");
            
            ScriptContext.waitUntilIdle();
            
            // small cheat, because we don't make the menu popup.
            org.das2.graph.DasPlot c= dom.getPlots(0).getController().getDasPlot();
            javax.swing.JPopupMenu menu= c.getDasMouseInputAdapter().getPrimaryPopupMenu();
            menu.show(app, 300, 300 );
            JPopupMenuOperator op= new JPopupMenuOperator( menu );
            op.pushMenu( new ComponentChooser[] { new RegexComponentChooser("Add Plot"),
                new RegexComponentChooser("Copy Plot Elements Down") } );
            
            Thread.sleep(1000);
            ScriptContext.waitUntilIdle();
            
            writeToPng("Test_042_TwoTsb.png"); // Leave artifacts for testing.
            save("Test_042_TwoTsb.vap");
            
            return(0);

        } catch (IOException ex) {
            Logger.getLogger(Test_042_TwoTsb.class.getName()).log(Level.SEVERE, null, ex);
            return(2);
        } catch (InterruptedException ex) {
            Logger.getLogger(Test_042_TwoTsb.class.getName()).log(Level.SEVERE, null, ex);
            return(3);
        }
    }
    
    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_042_TwoTsb"};
	org.netbeans.jemmy.Test.main(params);
    }
}
