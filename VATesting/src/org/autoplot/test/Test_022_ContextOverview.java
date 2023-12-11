/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.test;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JMenuItem;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.LoggerManager;
import org.das2.datum.Units;
import org.netbeans.jemmy.ComponentChooser;
import org.netbeans.jemmy.JemmyProperties;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.TestOut;
import org.netbeans.jemmy.operators.*;
import org.autoplot.AutoplotUI;
import org.autoplot.ScriptContext2023;
import static org.autoplot.ScriptContext2023.*;
import org.autoplot.dom.Application;
import org.autoplot.dom.BindingModel;
import util.RegexComponentChooser;

/**
 * Do a context overview and some zooming.
 * @author jbf
 */
public class Test_022_ContextOverview implements Scenario {

    private static final Logger logger= LoggerManager.getLogger("vatesting");
    private static final ScriptContext2023 scriptContext= ScriptContext2023.getInstance();
    
    @Override
    public int runIt(Object o) {

        JemmyProperties.setCurrentOutput(TestOut.getNullOutput());

        try {
            scriptContext.createGui();
            
            Application dom= scriptContext.getDocumentModel();
            
            dom.getOptions().setAutolayout(false);
            scriptContext.waitUntilIdle();
            sleep(1000);
            
            AutoplotUI app = (AutoplotUI) scriptContext.getViewWindow();
            JFrameOperator mainFrame = new JFrameOperator(app);
            sleep(3000); // remote bookmarks can take a little while to load
            scriptContext.waitUntilIdle();
            System.err.println("here line 52");
            JMenuBarOperator menuop= new JMenuBarOperator(mainFrame);
            System.err.println("here line 54");
            JMenuItem check= menuop.pushMenu( 
                    new ComponentChooser[] { new RegexComponentChooser("Bookmarks"),
                    new RegexComponentChooser("Demos"), new RegexComponentChooser("Demo 5: .*") } );
            
            System.err.println("here line 59");
            Util.waitUntilBusy(2000,app.getDom());
            System.err.println("here line 61");
            scriptContext.waitUntilIdle();

            DatumRange range0= dom.getPlots(0).getXaxis().getRange();
            
            if ( range0.getUnits().isConvertibleTo(Units.dimensionless) ) {
                scriptContext.waitUntilIdle();  // why???
                ((AutoplotUI)scriptContext.getViewWindow()).getDataSetSelector().isPendingChanges();
                range0= dom.getPlots(0).getXaxis().getRange();
            }
            
            if ( range0.getUnits().isConvertibleTo(Units.dimensionless) ) {
                System.err.println("** xaxis is not a time axis");
                return 1;
            } else {
                System.err.println("after wait, data loaded. "+range0);
            }

            scriptContext.save( "Test_022_ContextOverview.000.vap" );

            scriptContext.waitUntilIdle();
             
            DatumRange dr;
            dr= DatumRangeUtil.rescale(dom.getPlots(0).getXaxis().getRange(), 0.2, 0.8 );

            System.err.println("rescale to "+dr);

            dom.getPlots(0).getXaxis().setRange( dr );
            Thread.sleep(1000); // get to work on hudson--not sure why
            
            dr= DatumRangeUtil.rescale(dom.getPlots(0).getYaxis().getRange(), 0.1, 0.9 );
            dom.getPlots(0).getYaxis().setRange( dr );

            Thread.sleep(1000); // get to work on hudson--not sure why

            scriptContext.writeToPng( "Test_022_ContextOverview.001.png");


            // small cheat, because we don't make the menu popup.
            org.das2.graph.DasPlot c= dom.getPlots(0).getController().getDasPlot();
            javax.swing.JPopupMenu menu= c.getDasMouseInputAdapter().getPrimaryPopupMenu();
            menu.show(app, 300, 300 );
            JPopupMenuOperator op= new JPopupMenuOperator( menu );
            JMenuItem item= op.pushMenu( new ComponentChooser[] { new RegexComponentChooser("Add Plot"),
            new RegexComponentChooser("Context Overview") } );

            Thread.sleep(5000); // get to work on hudson--not sure why 

            scriptContext.writeToPng( "Test_022_ContextOverview.002.png");

            boolean tbindings= dom.getBindings().length==13; // colorbar //TODO: WHY????
            boolean trange= range0.equals( dom.getPlots(1).getXaxis().getRange() );

            System.err.println("= Bindings ("+dom.getBindings().length+")=");
            for ( BindingModel binding : dom.getBindings()) {
                System.err.println("  " + binding);
            }
            
            Thread.sleep(1000); 
            
            if ( tbindings && trange ) {
                return 0;
            } else {
                System.err.println("** fail because one of the following is not true:");
                if ( !tbindings ) {
                    System.err.println("** tbindings="+tbindings+"\tdom.getBindings().length should be 13, it is "+dom.getBindings().length );
                } else if ( dom.getBindings().length==16 ) {
                    System.err.println("  WHY???");
                } 
                if ( !trange ) System.err.println("** trange="+trange+ "\t"+range0+" should equal "+ dom.getPlots(1).getXaxis().getRange() );
                return 1;
            }

        } catch (IOException ex) {
           logger.log(Level.SEVERE, ex.getMessage(), ex);
            return -2;
        } catch (InterruptedException ex) {
           logger.log(Level.SEVERE, ex.getMessage(), ex);
            return -1;
        }
        
    }

    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_022_ContextOverview"};
	org.netbeans.jemmy.Test.main(params);
    }

}
