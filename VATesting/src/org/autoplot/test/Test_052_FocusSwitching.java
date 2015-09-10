/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.test;

import java.awt.Component;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JMenuBarOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JTabbedPaneOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.virbo.autoplot.AutoplotUI;
import org.virbo.autoplot.ScreenshotsTool;
import org.virbo.autoplot.ScriptContext;
import static org.virbo.autoplot.ScriptContext.getDocumentModel;
import static org.virbo.autoplot.ScriptContext.save;
import static org.virbo.autoplot.ScriptContext.writeToPng;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.scriptconsole.DumpRteExceptionHandler;
import util.NameComponentChooser;
import util.RegexComponentChooser;

/**
 * Demonstrate bug where switching between plots would reset filters
 * https://sourceforge.net/p/autoplot/bugs/1375/
 * @author Jeremy Faden
 */
public class Test_052_FocusSwitching implements Scenario {
    
    @Override
    public int runIt(Object o) {

        try {
            
            ScriptContext.getApplicationModel().setExceptionHandler( new DumpRteExceptionHandler() );
            
            Runnable run= new Runnable() {
                public void run() {
                    throw new RuntimeException("Wow Man!");
                }
            };

            SwingUtilities.invokeLater(run);
            
            ScriptContext.createGui();
            
            ScriptContext.waitUntilIdle();
            
            AutoplotUI app= (AutoplotUI) ScriptContext.getViewWindow();
            
            JFrameOperator mainFrame = new JFrameOperator(app);

            JMenuBarOperator menuBar = new JMenuBarOperator( mainFrame );
            menuBar.pushMenu("Options|Enable Feature|Data Panel", "|");
            
            ScriptContext.waitUntilIdle();            
            new JTextFieldOperator( app.getDataSetSelector().getEditor() ).setText("vap+cdf:http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hydra/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=2000-01-09");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();
            
            ScriptContext.waitUntilIdle();
            
            Component data= new JTabbedPaneOperator( app.getTabs() ).selectPage("data");
            //Ask Kenzie how to add filter via buttons.
            
            Application dom = getDocumentModel();
            dom.getPlotElements(0).setComponent("|slice1(10)");
            
            new JButtonOperator( mainFrame, new NameComponentChooser("browse") ).clickMouse();

            DialogOperator diaFrame = new DialogOperator( new RegexComponentChooser( "Editing .*") );

            new JButtonOperator( diaFrame, "Plot Below" ).clickMouse();
            
            Thread.sleep(100);
            ScriptContext.waitUntilIdle();
            
            dom.getPlotElements(1).setComponent("|slice0(8)");
            
            dom.getController().setPlotElement( dom.getPlotElements(0) );
            
            ScriptContext.waitUntilIdle();
            
            BufferedImage image= ScreenshotsTool.getScreenShotNoPointer();
            //BufferedImage image= ScreenshotsTool.getScreenShot();
            
            ImageIO.write( image, "png", new File( "Test_5pt2_FocusSwitching_Screen.png" ) );
            
            System.err.println( "filter: " + app.getDataPanel().getFiltersChainPanel().getFilter() );
            System.err.println("Done!");
            
            writeToPng("Test_5pt2_FocusSwitching.png"); // Leave artifacts for testing.
            save("Test_5pt2_FocusSwitching.vap");
            
            return(0);
            
        } catch (IOException ex) {
            Logger.getLogger(Test_052_FocusSwitching.class.getName()).log(Level.SEVERE, null, ex);
            return(2);
        } catch (InterruptedException ex) {
            Logger.getLogger(Test_052_FocusSwitching.class.getName()).log(Level.SEVERE, null, ex);
            return(3);
        }
    }
    
    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_052_FocusSwitching"};
	org.netbeans.jemmy.Test.main(params);
    }
}
