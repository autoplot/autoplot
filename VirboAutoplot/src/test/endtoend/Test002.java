/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.IOException;
import java.io.PrintWriter;
import org.virbo.autoplot.RenderType;
import org.virbo.autoplot.ScriptContext;
import org.virbo.autoplot.dom.Application;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;
import org.virbo.jythonsupport.Util;

/**
 * Test Autoplot including:
 *   use case: load wave file and add spectrogram.
 *   and more abstract vap file products
 * @author jbf
 */
public class Test002 {

    public static void main(String[] args) {
        try {
            ScriptContext.getDocumentModel().getOptions().setAutolayout(false);
            ScriptContext.getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");
            oldTests();
            testVaps();
            System.exit(0);  // TODO: something is firing up the event thread
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
    static long t0 = System.currentTimeMillis();

    public static void xxx(String id) {
        System.err.println("-- timer -- " + id + " --: " + (System.currentTimeMillis() - t0));
        t0 = System.currentTimeMillis();
    }

    private static void doTest(final String s, final String label) throws IOException, InterruptedException, Exception {

        ScriptContext.load(s);
        int width = ScriptContext.getDocumentModel().getCanvases(0).getWidth();
        int height = ScriptContext.getDocumentModel().getCanvases(0).getHeight();
        ScriptContext.setCanvasSize(width, height); // TODO: why?  I shouldn't have to set this...
        ScriptContext.writeToPng(label + ".png");

        System.err.printf("wrote to %s.png %dx%d\n", label, width, height);


    }

    private static void testVaps() throws Exception {
        String[] uris = new String[]{
            "000 file:///home/jbf/ct/hudson/vap/lon/thb_l2_esa_20080907_electrons_less.vap",
            "001 file:///home/jbf/ct/hudson/vap/energyCompareHydra.vap",
            "002 file:///home/jbf/ct/autoplot/demos/cdaweb/ISS_DOSANL_TEPC_2_burst.vap",
            "003 file:///home/jbf/ct/hudson/vap/hydra4.vap",
            "004 file:///home/jbf/ct/hudson/vap/autoSlice.vap", // legacy vap shows adapt slice
            "005 file:///home/jbf/ct/hudson/vap/jon-test_v1_07.vap",
            "006 file:///home/jbf/ct/hudson/vap/omni_1978_v1_07.vap",
            //mem "007 file:///home/jbf/ct/hudson/vap/Cluster1_HEEA_slices.vap",
            "008 file:///home/jbf/ct/hudson/vap/lanl/lanlGeoEpDemo4.vap",
            "009 file:///home/jbf/ct/hudson/vap/ninePanels.vap",
            "010 http://autoplot.org/data/autoplot-applet.vap",
            "011 file:///home/jbf/ct/hudson/vap/twoConnectorsOneDataSource.vap",
            "012 file:///home/jbf/ct/hudson/vap/cassini_kp.vap", // das2Server
            "013 file:///home/jbf/ct/autoplot/demos/science/de_eics_species.vap",
         //TODO: why does this rerange?   "014 file:///home/jbf/ct/hudson/vap/garageTemps_v1_07.vap",
            //mem "014 file:///home/jbf/ct/autoplot/demos/polarUvi.vap",
            "015 file:///home/jbf/ct/autoplot/demos/polar.vap",
            "020 file:///home/jbf/ct/hudson/vap/auto3.vap",
            "021 file:///home/jbf/ct/hudson/vap/auto4.vap",
            "022 file:///home/jbf/ct/hudson/vap/tt2000.vap",

        };


        for (String s : uris) {

            int count = Integer.parseInt(s.substring(0, 4).trim());
            s = s.substring(4);

            String label = String.format("test002_%03d", count);

            try {

                doTest(s, label);

            } catch (Exception ex) {
                PrintWriter pw = new PrintWriter(label + ".error");
                pw.println(s);
                pw.println("");
                ex.printStackTrace(pw);

                pw.close();

                ex.printStackTrace();

            }

            xxx(label + ": " + s);

        }
    }

    private static void oldTests() throws Exception, IOException, InterruptedException {

        QDataSet ds = Util.getDataSet("file:///home/jbf/ct/hudson/data.backup/wav/fireworks.wav");
        final Application dom = ScriptContext.getDocumentModel();
        dom.getCanvases(0).setFitted(false);
        ScriptContext.setCanvasSize(400, 800);
        ScriptContext.plot(0, Ops.autoHistogram(ds));
        dom.getPlots(0).getXaxis().getController().getDasAxis().setUseDomainDivider(true);
        dom.getPlotElements(0).setRenderType(RenderType.fillToZero);
        dom.getPlots(0).getYaxis().setLabel("auto histogram of!chttp://www.autoplot.org/data/fireworks.wav");
        QDataSet ds2 = Ops.autoHistogram(Ops.log10(Ops.fftWindow(ds, 512)));
        ScriptContext.plot(1, ds2);
        dom.getPlots(1).getXaxis().getController().getDasAxis().setUseDomainDivider(true);
        dom.getPlotElements(1).setRenderType(RenderType.fillToZero);
        dom.getPlots(1).getYaxis().setLabel("auto histogram of!cpower spectrum");
        dom.getPlots(1).getXaxis().setLabel("log(Ops.fftWindow(512)");
        ScriptContext.writeToPng("test002.png");

    }
}
