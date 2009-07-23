/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.IOException;
import org.virbo.autoplot.ScriptContext;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;
import org.virbo.jythonsupport.Util;

/**
 *
 * @author jbf
 */
public class Test002 {

    public static void main(String[] args) throws InterruptedException, IOException, Exception {
        try {
            QDataSet ds = Util.getDataSet("http://www.autoplot.org/data/fireworks.wav");
            ScriptContext.setCanvasSize(400, 800);
            ScriptContext.plot(0, Ops.autoHistogram(ds));
            ScriptContext.plot(1, Ops.autoHistogram( Ops.fftWindow(ds, 512) ) );
            //ScriptContext.getDocumentModel().getPlots(0).setTitle("auto histogram of http://www.autoplot.org/data/fireworks.wav");
            ScriptContext.writeToPng("test002.png");
            System.exit(0);  // TODO: something is firing up the event thread
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
