package test;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.autoplot.dom.Application;

/**
 *
 * @author jbf
 */
public class TestExperiment20250617 {
    public static void doTest( Application dom ) {
        try {
            dom.getController().getScriptContext().peekAt(dom);
        } catch (IOException ex) {
            Logger.getLogger(TestExperiment20250617.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
