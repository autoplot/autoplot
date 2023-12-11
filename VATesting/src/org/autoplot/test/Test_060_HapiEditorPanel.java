/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.test;

import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.LoggerManager;
import org.netbeans.jemmy.JemmyProperties;
import org.netbeans.jemmy.Scenario;
import org.netbeans.jemmy.TestOut;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.autoplot.AutoplotUI;
import org.autoplot.ScreenshotsTool;
import org.autoplot.ScriptContext2023;
import org.autoplot.datasource.DataSetSelector;
import org.autoplot.util.jemmy.NameComponentChooser;
import org.netbeans.jemmy.operators.DialogOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JListOperator;
import util.RegexComponentChooser;

/**
 * tests of the HapiEditorPanel, after discovering broken functionality.
 * @author jbf
 */
public class Test_060_HapiEditorPanel implements Scenario {
    private static final ScriptContext2023 scriptContext= ScriptContext2023.getInstance();

    private static final Logger logger= LoggerManager.getLogger("vatesting");
    
    private void wait( int millis ) {
        try {
            Thread.sleep(millis);
        } catch ( InterruptedException ex ) {
            throw new RuntimeException(ex);
        }
    }
       
    private void waitUntilIdle( int millis ) {
        try {
            Thread.sleep(millis);
        } catch ( InterruptedException ex ) {
            throw new RuntimeException(ex);
        }
        scriptContext.waitUntilIdle();
    }
    
    @Override
    public int runIt(Object o) {

        // hide Jemmy output
        JemmyProperties.setCurrentOutput(TestOut.getNullOutput());
            
        try {

            Util.reportLogger(logger);
            
            scriptContext.createGui();
            
            AutoplotUI app= (AutoplotUI) scriptContext.getViewWindow();
            //ScreenshotsTool st= new ScreenshotsTool( scriptContext.getApplication(), "Test_060_HapiEditorPanel/", true );
              
            JFrameOperator mainFrame= new JFrameOperator(app);
            
            DataSetSelector dss= app.getDataSetSelector();
            new JTextFieldOperator( dss.getEditor() ).setText("vap+hapi:http://jfaden.net/HapiServerDemo/hapi?id=Iowa+City+Conditions&parameters=Time,Precip&timerange=2018-08-28+through+2018-09-08");
            new JButtonOperator( mainFrame, new NameComponentChooser("inspect") ).clickMouse();

            DialogOperator diaFrame = new DialogOperator( new RegexComponentChooser( "Editing .*") );
            //st.takePicture(61,"This is the HAPI editor panel with id and parameters set for URI.");
            wait(3000);
            new JButtonOperator( diaFrame, "Plot" ).clickMouse();
            waitUntilIdle(1000);
            scriptContext.writeToPng("Test_060_HapiEditorPanel_1.png");
            new JButtonOperator( mainFrame, new NameComponentChooser("inspect") ).clickMouse();
            wait(1000);
            diaFrame = new DialogOperator( new RegexComponentChooser( "Editing .*") );
            //st.takePicture(69,"This is the HAPI editor panel with id and parameters set.");
            wait(1000);
            new JListOperator( diaFrame ).selectItem("Iowa City Forecast"); 
            wait(1000);
            new JButtonOperator( diaFrame, "Plot Below" ).clickMouse();
            waitUntilIdle(1000);
            //st.takePicture(90,"Two plots.");
            scriptContext.writeToPng("Test_060_HapiEditorPanel_2.png");
            //st.requestFinish(true);
            
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return 0;
    }
     
    public static void main(String[] argv) {
	String[] params = {"org.autoplot.test.Test_060_HapiEditorPanel"};
	org.netbeans.jemmy.Test.main(params);
    }
}
