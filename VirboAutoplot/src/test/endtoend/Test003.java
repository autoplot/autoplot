/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import java.io.IOException;
import org.virbo.autoplot.ScriptContext;

/**
 *
 * @author jbf
 */
public class Test003 {
    public static void main(String[] args) throws InterruptedException, IOException, Exception {
        try {
            ScriptContext.plot( "file:/home/jbf/ct/lanl/hudson/geo_pitch_stack_test1_local.vap" );
            ScriptContext.setCanvasSize( 800, 600 );
            ScriptContext.writeToPng( "test003.png" );
            System.exit(0);  // TODO: something is firing up the event thread
        } catch ( RuntimeException ex ) {
            System.exit(1);
        }
    }
}
