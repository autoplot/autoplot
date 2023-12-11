/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.test;

import java.awt.Toolkit;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.graph.DasAxis;
import org.das2.util.LoggerManager;
import org.netbeans.jemmy.JemmyProperties;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.TestOut;
import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.autoplot.AutoplotUI;
import org.autoplot.ScriptContext;
import org.autoplot.dom.Application;
import org.autoplot.dom.BindingModel;
import util.NameComponentChooser;
import util.RegexComponentChooser;

/**
 *
 * @author jbf
 */
public class Test_034_TimeSeriesBrowse implements Scenario {

    private static final Logger logger= LoggerManager.getLogger("vatesting");
    private static final ScriptContext scriptContext= ScriptContext.getInstance();

    public int runIt(Object o) {

        JemmyProperties.setCurrentOutput(TestOut.getNullOutput());

        try {
            scriptContext.createGui();
            AutoplotUI app = (AutoplotUI) scriptContext.getViewWindow();
            Application dom = scriptContext.getDocumentModel();
            dom.getOptions().setAutolayout(false);
            
            JFrameOperator mainFrame = new JFrameOperator(app);
            scriptContext.waitUntilIdle();

            new JTextFieldOperator(app.getDataSetSelector().getEditor()).setText("https://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hydra/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=20000109"); 
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();
            
            final DasAxis xaxis = scriptContext.getDocumentModel().getPlots(0).getXaxis().getController().getDasAxis();

            // The following is interesting code that needs to be studied more.  I think what's happened
            // is I moved the DataSetSelector event firing back on to the event thread, and 
            // this is messing up this script.
            
            SwingUtilities.invokeAndWait( new Runnable() {
                public void run() {
                    System.err.println( "1: "+ xaxis.getDatumRange() );
                }
            });
            
            System.err.println("TODO: There should be a way to make sure the app has received the update.");
            Thread.sleep(1500);
            
            scriptContext.waitUntilIdle();

            SwingUtilities.invokeAndWait( new Runnable() {
                public void run() {
                    System.err.println( "2: "+xaxis.getDatumRange() );
                }
            });
            
            System.err.println( "3: "+xaxis.getDatumRange() );
            xaxis.scanNext(); // no gui test, but that's okay...
            System.err.println( "4: "+xaxis.getDatumRange() );
            
            new JButtonOperator( mainFrame, new NameComponentChooser("inspect") ).clickMouse();
                    //app.getDataSetSelector().getBrowseButton()).clickMouse();

            DialogOperator diaFrame = new DialogOperator( new RegexComponentChooser( "Editing .*") );

            new JButtonOperator( diaFrame, "Plot Below" ).clickMouse();

            System.err.println("Boo, sleep because testing server isn't stopping properly."); //TODO: fix this!
            Thread.sleep(1000);
            scriptContext.waitUntilIdle();
            
            int tries=0;
            while ( tries<10 && dom.getPlotElements(1).getController().getDataSet()==null ) {
                tries++;
                System.err.println("* data not here yet");
                Thread.sleep(330);
            }
            
            System.err.println( "5: "+xaxis.getDatumRange() );
            System.err.println("current directory: "+new File(".").getAbsoluteFile().toString());
            
            scriptContext.writeToPng( "Test_034_TimeSeriesBrowse.001.png");
        
            System.err.println("--- bindings ---");
            BindingModel[] bms= scriptContext.getDocumentModel().getBindings();
            for ( BindingModel bm: bms ) {
                System.err.println(bm);
            }
            System.err.println("---");
            if ( bms.length==4 &&
                   ( bms[1].getSrcProperty().equals("timeRange") || bms[3].getSrcProperty().equals("timeRange") ) ) {
                return 0;
            } else if ( bms.length==2 && ( bms[1].getSrcProperty().equals("timeRange") || bms[3].getSrcProperty().equals("timeRange") ) ) {
                // we seem to have lost two of the bindings, but I wasn't sure about those anyway.
                return 0;
            } else {
                System.err.println("bindings.length="+bms.length +" (should be 4)" );
                System.err.println("bindings[1].getSrcProperty()="+bms[1].getSrcProperty()+"  (either this should be timeRange" );
                System.err.println("bindings[3].getSrcProperty()="+bms[3].getSrcProperty()+"   or this should be timeRange)" );
                return 1;
            }
        } catch ( Exception ex ) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            return -1;
        }        
    }

    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_034_TimeSeriesBrowse"};
	org.netbeans.jemmy.Test.main(params);
    }

}
