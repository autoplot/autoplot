/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.IOException;
import org.virbo.autoplot.RenderType;
import org.virbo.autoplot.ScriptContext;
import org.virbo.autoplot.dom.Application;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;
import org.virbo.jythonsupport.Util;

/**
 * Test Autoplot use case: load wave file and add spectrogram
 * @author jbf
 */
public class Test002 {

    public static void main(String[] args) throws InterruptedException, IOException, Exception {
        try {
            ScriptContext.getDocumentModel().getOptions().setAutolayout(false);
            ScriptContext.getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");

            //ScriptContext.createGui();
            QDataSet ds = Util.getDataSet("http://www.autoplot.org/data/fireworks.wav");
            final Application dom = ScriptContext.getDocumentModel();
            dom.getCanvases(0).setFitted(false);
            ScriptContext.setCanvasSize(400, 800);
            ScriptContext.plot(0, Ops.autoHistogram(ds));
            dom.getPlots(0).getXaxis().getController().getDasAxis().setUseDomainDivider(true);
            dom.getPanels(0).setRenderType( RenderType.fillToZero );
            dom.getPlots(0).getYaxis().setLabel("auto histogram of!chttp://www.autoplot.org/data/fireworks.wav");

            QDataSet ds2= Ops.autoHistogram( Ops.log10( Ops.fftWindow(ds, 512) ) ) ;
            ScriptContext.plot(1, ds2 );
            dom.getPlots(1).getXaxis().getController().getDasAxis().setUseDomainDivider(true);
            dom.getPanels(1).setRenderType( RenderType.fillToZero );
            dom.getPlots(1).getYaxis().setLabel("auto histogram of!cpower spectrum");
            dom.getPlots(1).getXaxis().setLabel("log(Ops.fftWindow(512)");
            //ScriptContext.getDocumentModel().getPlots(0).setTitle("auto histogram of http://www.autoplot.org/data/fireworks.wav");
            ScriptContext.writeToPng("test002.png");
            System.exit(0);  // TODO: something is firing up the event thread
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
