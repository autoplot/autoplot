/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.test;

import org.autoplot.dom.Application;
import java.text.ParseException;
import org.das2.datum.DatumRangeUtil;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.LoggerManager;
import org.netbeans.jemmy.JemmyProperties;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.TestOut;
import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JListOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.autoplot.AutoplotUI;
import org.autoplot.ScriptContext2023;
import util.NameComponentChooser;
import util.RegexComponentChooser;
/**
 *
 * @author jbf
 */
public class Test_035_3533882_timerange_reset implements Scenario {

    private static final Logger logger= LoggerManager.getLogger("vatesting");
    private static final ScriptContext2023 scriptContext= ScriptContext2023.getInstance();

    public int runIt(Object o) {

        JemmyProperties.setCurrentOutput(TestOut.getNullOutput());


        try {
            scriptContext.createGui();
            AutoplotUI app = (AutoplotUI) scriptContext.getViewWindow();

            Application dom= scriptContext.getDocumentModel();
            dom.getOptions().setAutolayout(false);
            
            JFrameOperator mainFrame = new JFrameOperator(app);
            scriptContext.waitUntilIdle();

            new JTextFieldOperator(app.getDataSetSelector().getEditor()).setText("vap+cdaweb:ds=C3_PP_CIS&id=V_p_xyz_gse__C3_PP_CIS&timerange=2005-09-07+through+2005-09-20");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();
            scriptContext.waitUntilIdle();


            dom.setTimeRange( DatumRangeUtil.parseTimeRange("2005-09-08") );

            new JButtonOperator( mainFrame, new NameComponentChooser("inspect") ).clickMouse();

            DialogOperator diaFrame = new DialogOperator( new RegexComponentChooser( "Editing .*") );

            new JListOperator( diaFrame ).selectItem(1); // rank 1 value easy to delete

            new JButtonOperator( diaFrame, "Plot Below" ).clickMouse();

            scriptContext.waitUntilIdle();
            
            // delete this plotElement, then 
            dom.getController().setPlotElement( dom.getPlotElements(4) ); //cheat

            Util.pushContextMenu( dom.getPlots(1).getController().getDasPlot(),
                    new String[] { "Edit Plot Element", "Delete Plot Element" } );

            scriptContext.writeToPng("Test_035_3533882_timerange_reset.png");
            scriptContext.save("Test_035_3533882_timerange_reset.vap");

            if ( true ) {
                return 0;
            } else {
                return 1;
            }

        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            return 1;
        } catch (ParseException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            return -2;
        }
        
    }

    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_035_3533882_timerange_reset"};
	org.netbeans.jemmy.Test.main(params);
    }

}
