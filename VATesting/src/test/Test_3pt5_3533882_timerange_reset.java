/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test;

import org.virbo.autoplot.dom.Application;
import java.text.ParseException;
import org.das2.datum.DatumRangeUtil;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.graph.DasAxis;
import org.netbeans.jemmy.ComponentChooser;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.operators.JListOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.virbo.autoplot.AutoplotUI;
import util.NameComponentChooser;
import util.RegexComponentChooser;
import static org.virbo.autoplot.ScriptContext.*;
/**
 *
 * @author jbf
 */
public class Test_3pt5_3533882_timerange_reset implements Scenario {

    public int runIt(Object o) {

        try {
            createGui();
            AutoplotUI app = (AutoplotUI) getViewWindow();
            JFrameOperator mainFrame = new JFrameOperator(app);
            waitUntilIdle();

            new JTextFieldOperator(app.getDataSetSelector().getEditor()).setText("vap+cdaweb:ds=C3_PP_CIS&id=V_p_xyz_gse__C3_PP_CIS&timerange=2005-09-07+through+2005-09-20");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();
            waitUntilIdle();

            Application dom= getDocumentModel();

            dom.setTimeRange( DatumRangeUtil.parseTimeRange("2005-09-08") );

            new JButtonOperator( mainFrame, new NameComponentChooser("browse") ).clickMouse();

            DialogOperator diaFrame = new DialogOperator( new RegexComponentChooser( "Editing .*") );

            new JListOperator( diaFrame ).selectItem(1); // rank 1 value easy to delete

            new JButtonOperator( diaFrame, "Plot Below" ).clickMouse();

            // delete this plotElement, then 
            dom.getController().setPlotElement( dom.getPlotElements(4) ); //cheat

            Util.pushContextMenu( dom.getPlots(1).getController().getDasPlot(),
                    new String[] { "Edit Plot Element", "Delete Plot Element" } );



            if ( true ) {
                return 0;
            } else {
                return 1;
            }

        } catch (ParseException ex) {
            Logger.getLogger(Test_3pt5_3533882_timerange_reset.class.getName()).log(Level.SEVERE, null, ex);
            return -2;
        } catch (InterruptedException ex) {
            Logger.getLogger(Test_3pt5_3533882_timerange_reset.class.getName()).log(Level.SEVERE, null, ex);
            return -1;
        }
        
    }

    public static void main(String[] argv) {
	String[] params = {"test.Test_3pt5_3533882_timerange_reset"};
	org.netbeans.jemmy.Test.main(params);
    }

}
