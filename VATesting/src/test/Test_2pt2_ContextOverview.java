/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JMenuItem;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.netbeans.jemmy.ComponentChooser;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.operators.*;
import org.virbo.autoplot.AutoplotUI;
import static org.virbo.autoplot.ScriptContext.*;
import org.virbo.autoplot.dom.Application;
import util.RegexComponentChooser;

/**
 * Do a context overview and some zooming.
 * @author jbf
 */
public class Test_2pt2_ContextOverview implements Scenario {

    @Override
    public int runIt(Object o) {

        try {
            createGui();
            
            Application dom= getDocumentModel();
            
            dom.getOptions().setAutolayout(false);
            
            AutoplotUI app = (AutoplotUI) getViewWindow();
            JFrameOperator mainFrame = new JFrameOperator(app);
            waitUntilIdle();

            Thread.sleep(3000); //give the menus a moment to initialize

            JMenuBarOperator menuop= new JMenuBarOperator(mainFrame);

            menuop.pushMenu( new ComponentChooser[] { new RegexComponentChooser("Bookmarks"),
            new RegexComponentChooser("Demos"), new RegexComponentChooser("Demo 5: .*") } );

            waitUntilIdle();

            DatumRange range0= dom.getPlots(0).getXaxis().getRange();
            System.err.println("after wait, data loaded. "+range0);

            save( "Test_2pt2_ContextOverview.000.vap" );

            waitUntilIdle();
             
            DatumRange dr;
            dr= DatumRangeUtil.rescale(dom.getPlots(0).getXaxis().getRange(), 0.2, 0.8 );

            System.err.println("rescale to "+dr);

            dom.getPlots(0).getXaxis().setRange( dr );
            Thread.sleep(5000); // get to work on hudson--not sure why
            
            dr= DatumRangeUtil.rescale(dom.getPlots(0).getYaxis().getRange(), 0.1, 0.9 );
            dom.getPlots(0).getYaxis().setRange( dr );

            Thread.sleep(5000); // get to work on hudson--not sure why

            writeToPng( "Test_2pt2_ContextOverview.001.png");


            // small cheat, because we don't make the menu popup.
            org.das2.graph.DasPlot c= dom.getPlots(0).getController().getDasPlot();
            javax.swing.JPopupMenu menu= c.getDasMouseInputAdapter().getPrimaryPopupMenu();
            menu.show(app, 300, 300 );
            JPopupMenuOperator op= new JPopupMenuOperator( menu );
            JMenuItem item= op.pushMenu( new ComponentChooser[] { new RegexComponentChooser("Add Plot"),
            new RegexComponentChooser("Context Overview") } );

            Thread.sleep(5000); // get to work on hudson--not sure why 

            writeToPng( "Test_2pt2_ContextOverview.002.png");

            boolean tbindings= dom.getBindings().length==13; // colorbar
            boolean trange= range0.equals( dom.getPlots(1).getXaxis().getRange() );

            if ( tbindings && trange ) {
                return 0;
            } else {
                return 1;
            }

        } catch (IOException ex) {
            Logger.getLogger(Test_2pt2_ContextOverview.class.getName()).log(Level.SEVERE, null, ex);
            return -2;
        } catch (InterruptedException ex) {
            Logger.getLogger(Test_2pt2_ContextOverview.class.getName()).log(Level.SEVERE, null, ex);
            return -1;
        }
        
    }

    public static void main(String[] argv) {
	String[] params = {"test.Test_2pt2_ContextOverview"};
	org.netbeans.jemmy.Test.main(params);
    }

}
