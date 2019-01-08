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
import org.netbeans.jemmy.JemmyProperties;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.TestOut;
import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JMenuBarOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JTabbedPaneOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.autoplot.AutoplotUI;
import org.autoplot.ScreenshotsTool;
import org.autoplot.ScriptContext;
import static org.autoplot.ScriptContext.getDocumentModel;
import static org.autoplot.ScriptContext.save;
import static org.autoplot.ScriptContext.writeToPng;
import org.autoplot.dom.Application;
import org.autoplot.scriptconsole.DumpRteExceptionHandler;
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

        JemmyProperties.setCurrentOutput(TestOut.getNullOutput());

        try {
            
            ScriptContext.getApplicationModel().setExceptionHandler( new DumpRteExceptionHandler() );
            org.das2.DasApplication.getDefaultApplication().setExceptionHandler( new DumpRteExceptionHandler() );

            ScriptContext.createGui();
            
            ScriptContext.waitUntilIdle();
            
            AutoplotUI app= (AutoplotUI) ScriptContext.getViewWindow();
            
            JFrameOperator mainFrame = new JFrameOperator(app);

            JMenuBarOperator menuBar = new JMenuBarOperator( mainFrame );
            menuBar.pushMenu("Options|Enable Feature|Data Panel", "|");
            
            ScriptContext.waitUntilIdle();            
            new JTextFieldOperator( app.getDataSetSelector().getEditor() ).setText("vap+cdf:http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hydra/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=2000-01-09");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();
            
            Thread.sleep(1000);
            ScriptContext.waitUntilIdle(); // clickMouse doesn't block, never has...
            
            Component data= new JTabbedPaneOperator( app.getTabs() ).selectPage("data");
            //Ask Kenzie how to add filter via buttons.
            
            Application dom = getDocumentModel();
            
            // why must I do this?  I would think that waitUntilIdle would catch this.
            while ( dom.getPlotElements(0).getController().getDataSet()==null ) {
                Thread.sleep(100);
            }
                    
            dom.getPlotElements(0).setComponent("|slice1(10)");
            Thread.sleep(1000);
            
            ScriptContext.waitUntilIdle(); 
            
            new JButtonOperator( mainFrame, new NameComponentChooser("inspect") ).clickMouse();

            DialogOperator diaFrame = new DialogOperator( new RegexComponentChooser( "Editing .*") );

            new JButtonOperator( diaFrame, "Plot Below" ).clickMouse();
            
            Thread.sleep(1000);
            ScriptContext.waitUntilIdle(); // clickMouse doesn't block, never has...
            
            dom.getPlotElements(1).setComponent("|slice0(8)");
            Thread.sleep(1000);
            
            ScriptContext.waitUntilIdle();
            
            dom.getController().setPlotElement( dom.getPlotElements(0) );
            
            ScriptContext.waitUntilIdle();
            Thread.sleep(1000); // so that the data screen settles on one state before the screenshot, I hope.
            
            BufferedImage image= ScreenshotsTool.getScreenShotNoPointer();
            //BufferedImage image= ScreenshotsTool.getScreenShot();
            image.getGraphics().clearRect( 150, image.getHeight()-1041, 300, 20 ); // the GUI title bar
            image.getGraphics().clearRect( 15, image.getHeight()-985, 620, 22 );   // the address bar
            
            ImageIO.write( image, "png", new File( "Test_052_FocusSwitching_Screen.png" ) );
            
            //System.err.println( "filter: " + app.getDataPanel().getFiltersChainPanel().getFilter() );
            System.err.println("Done!");
            
            writeToPng("Test_052_FocusSwitching.png"); // Leave artifacts for testing.
            save("Test_052_FocusSwitching.vap");
            
            return(0);
            
        } catch (IOException ex) {
            Logger.getLogger(Test_052_FocusSwitching.class.getName()).log(Level.SEVERE, null, ex);
            ex.printStackTrace();
            return(2);
        } catch (InterruptedException ex) {
            Logger.getLogger(Test_052_FocusSwitching.class.getName()).log(Level.SEVERE, null, ex);
            ex.printStackTrace();
            return(3);
        }
    }
    
    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_052_FocusSwitching"};
	org.netbeans.jemmy.Test.main(params);
    }
}
