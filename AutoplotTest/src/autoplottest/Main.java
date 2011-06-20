/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package autoplottest;

import java.awt.Window;
import java.awt.event.KeyEvent;
import javax.swing.JFrame;
import org.fest.swing.core.KeyPressInfo;
import org.fest.swing.fixture.FrameFixture;
import org.fest.swing.fixture.JComboBoxFixture;
import org.virbo.autoplot.ScriptContext;

/**
 *
 * @author jbf
 */
public class Main {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws InterruptedException {
        ScriptContext.createGui();
        Window w= ScriptContext.getViewWindow();


        FrameFixture ff= new FrameFixture((JFrame)w);

        ff.robot.settings().delayBetweenEvents(30);

        System.err.println( "delay="+ ff.robot.settings().delayBetweenEvents() );

        JComboBoxFixture cb= ff.comboBox();
        System.err.println( "selected item=" + cb.target.getSelectedItem() );
        String[] items= cb.contents();
        for ( int i=0; i<items.length; i++ ) {
            System.err.println( "  "+i+": " +items[i]);
        }

        //cb.replaceText("http://autoplot.org/data/");

        //cb.pressAndReleaseKeys( KeyEvent.VK_ENTER);
        //cb.pressAndReleaseKeys( KeyEvent.VK_ESCAPE );

        cb.replaceText("http://autoplot.org/data/image/Capture_00158.jpg");
        //cb.pressAndReleaseKeys( KeyEvent.VK_ENTER );

        cb.pressAndReleaseKey(  KeyPressInfo.keyCode(KeyEvent.VK_ENTER).modifiers(KeyEvent.CTRL_MASK) );

        ff.robot.waitForIdle();
        ScriptContext.getApplicationModel().waitUntilIdle(true);

        cb.replaceText("http://autoplot.org/data/image/Capture_00159.jpg");

        cb.pressAndReleaseKey(  KeyPressInfo.keyCode(KeyEvent.VK_ENTER).modifiers(KeyEvent.CTRL_MASK) );


    }

}
