/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.test;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.AboutUtil;
import org.netbeans.jemmy.ComponentChooser;
import org.netbeans.jemmy.JemmyProperties;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.TestOut;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JPopupMenuOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.autoplot.AutoplotUI;
import org.autoplot.ScreenshotsTool;
import org.autoplot.ScriptContext2023;
import org.autoplot.dom.Application;
import util.RegexComponentChooser;

/**
 * Test building a configuration with two TSBs listening to the context property of
 * each plot.
 * @author Jeremy Faden
 */
public class Test_042_TwoTsb implements Scenario {
    private static final ScriptContext2023 scriptContext= ScriptContext2023.getInstance();

    @Override
    public int runIt(Object o) {

        try {
            System.err.println( AboutUtil.getReleaseTag() );
            List<String> bis= AboutUtil.getBuildInfos();
            bis.stream().forEach((s) -> {
                System.err.println(s);
            });
            System.err.println( "build.jenkinsURL: " +AboutUtil.getJenkinsURL() );
        } catch (IOException ex) {
            Logger.getLogger(Test_042_TwoTsb.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        JemmyProperties.setCurrentOutput(TestOut.getNullOutput());

        try {
            scriptContext.createGui();
            
            scriptContext.waitUntilIdle();
            
            ScreenshotsTool st= new ScreenshotsTool( scriptContext.getApplication(), "Test_042_TwoTsb/", true );
                    
            AutoplotUI app= (AutoplotUI) scriptContext.getViewWindow();
            
            JFrameOperator mainFrame = new JFrameOperator(app);

            new JTextFieldOperator( app.getDataSetSelector().getEditor() ).setText("http://autoplot.org/data/jyds/tsbNonTimeAxis.jyds?timerange=2000-01-03");
            new JButtonOperator(app.getDataSetSelector().getGoButton()).clickMouse();

            Thread.sleep(1000);
            scriptContext.waitUntilIdle();
            
            st.takePicture( 52, "Here we've plotted a dataset with TSB, or the Time Series Browse capability.  Changing the time will load more data." );
            
            Application dom= scriptContext.getDocumentModel();
            
            dom.getPlotElements(0).setDisplayLegend(true);
            dom.getPlotElements(0).setLegendLabel("%{PLOT_CONTEXT}");
            
            scriptContext.waitUntilIdle();
            
            st.takePicture( 60, "The hidden time range control is in the 'context' property of the plot.  The macro %{PLOT_CONTEXT} is used to show it." );
            
            // small cheat, because we don't make the menu popup.
            org.das2.graph.DasPlot c= dom.getPlots(0).getController().getDasPlot();
            javax.swing.JPopupMenu menu= c.getDasMouseInputAdapter().getPrimaryPopupMenu();
            menu.show(app, 300, 300 );
            JPopupMenuOperator op= new JPopupMenuOperator( menu );
            op.pushMenu( new ComponentChooser[] { new RegexComponentChooser("Add Plot"),
                new RegexComponentChooser("Copy Plot Elements Down") } );
            
            Thread.sleep(1000);
            scriptContext.waitUntilIdle();

            st.takePicture( 73, "Copy plots down will copy the plot, and correctly connect the TSB capabilty of the lower plot as well." );
            
            scriptContext.writeToPng("Test_042_TwoTsb.png"); // Leave artifacts for testing.
            scriptContext.save("Test_042_TwoTsb.vap");
            
            Thread.sleep(1000);
            
            dom.setTimeRange( dom.getTimeRange().next() );
            scriptContext.writeToPng("Test_042_TwoTsb_2.png"); // Leave artifacts for testing.
            scriptContext.save("Test_042_TwoTsb_2.vap");
            st.takePicture( 84, "Advancing dom.timeRange loads data for the next day." );
            
            dom.setTimeRange( dom.getTimeRange().rescale(0,0.5) );
            scriptContext.writeToPng("Test_042_TwoTsb_3.png"); // Leave artifacts for testing.
            scriptContext.save("Test_042_TwoTsb_3.vap");
            st.takePicture( 88, "Setting dom.timeRange to a partial day loads just the data within the interval." );
            
            dom.setTimeRange( dom.getTimeRange().rescale(-0.5,0.5) );
            scriptContext.writeToPng("Test_042_TwoTsb_4.png"); // Leave artifacts for testing.
            scriptContext.save("Test_042_TwoTsb_4.vap");
            st.takePicture( 93, "Setting dom.timeRange to cross a day boundary loads two partial days." );
            
            System.err.println("Sleep 2 seconds to see if that fixes last image");
            Thread.sleep(2000);
            
            st.requestFinish(true);
            
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
