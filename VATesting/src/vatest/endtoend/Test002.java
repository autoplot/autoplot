/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package vatest.endtoend;

import java.io.IOException;
import java.io.PrintWriter;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.err;
import static java.lang.System.exit;
import static org.virbo.autoplot.RenderType.fillToZero;
import static org.virbo.autoplot.ScriptContext.getDocumentModel;
import static org.virbo.autoplot.ScriptContext.load;
import static org.virbo.autoplot.ScriptContext.plot;
import static org.virbo.autoplot.ScriptContext.setCanvasSize;
import static org.virbo.autoplot.ScriptContext.writeToPng;
import org.virbo.autoplot.dom.Application;
import org.virbo.dataset.QDataSet;
import static org.virbo.dsops.Ops.autoHistogram;
import static org.virbo.dsops.Ops.fftWindow;
import static org.virbo.dsops.Ops.log10;
import static org.virbo.jythonsupport.Util.getDataSet;
import static vatest.endtoend.VATestSupport.TEST_DATA;
import static vatest.endtoend.VATestSupport.TEST_VAP;

/**
 * Test Autoplot including:
 *   use case: load wave file and add spectrogram.
 *   and more abstract vap file products
 * @author jbf
 */
public class Test002 {

    public static void main(String[] args) {
        try {
            getDocumentModel().getOptions().setAutolayout(false);
            getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");
            oldTests();
            testVaps();
            exit(0);  // TODO: something is firing up the event thread
        } catch (Exception ex) {
            ex.printStackTrace();
            exit(1);
        }
    }
    static long t0 = currentTimeMillis();

    public static void xxx(String id) {
        err.println("-- timer -- " + id + " --: " + (currentTimeMillis() - t0));
        t0 = currentTimeMillis();
    }

    private static void doTest(final String s, final String label) throws IOException, InterruptedException, Exception {

        load(s);
        int width = getDocumentModel().getCanvases(0).getWidth();
        int height = getDocumentModel().getCanvases(0).getHeight();
        setCanvasSize(width, height); // TODO: why?  I shouldn't have to set this...
        writeToPng(label + ".png");

        err.printf("wrote to %s.png %dx%d\n", label, width, height);


    }

    private static void testVaps() throws Exception {
        String[] uris = new String[]{
            "000 " + TEST_VAP + "lon/thb_l2_esa_20080907_electrons_less.vap",
            "001 " + TEST_VAP + "energyCompareHydra.vap",
            ///"002 file:///home/jbf/ct/autoplot/demos/cdaweb/ISS_DOSANL_TEPC_2_burst.vap",
            "003 " + TEST_VAP + "hydra4.vap",
            "004 " + TEST_VAP + "autoSlice.vap", // legacy vap shows adapt slice
            "005 " + TEST_VAP + "jon-test_v1_07.vap",
            "006 " + TEST_VAP + "omni_1978_v1_07.vap",
            //mem "007 file:///home/jbf/ct/hudsonvap/Cluster1_HEEA_slices.vap",
            "008 " + TEST_VAP + "lanl/lanlGeoEpDemo4.vap",
            "009 " + TEST_VAP + "ninePanels.vap",
            "010 " + TEST_VAP + "test002_10.vap",
            "011 " + TEST_VAP + "twoConnectorsOneDataSource.v1_07.vap",
            "012 " + TEST_VAP + "cassini_kp.vap", // das2Server
            //Test 013 uses CDAWEB data
            "013 " + TEST_VAP + "de_eics_species.vap",
         //TODO: why does this rerange?   "014 file:///home/jbf/ct/hudsonvap/garageTemps_v1_07.vap",
            //mem "014 file:///home/jbf/ct/autoplot/demos/polarUvi.vap",
            "015 " + TEST_VAP + "polar.vap",
            "020 " + TEST_VAP + "auto3.vap",
            "021 " + TEST_VAP + "auto4.v1_07.vap",
            "022 " + TEST_VAP + "tt2000.vap",
            "023 " + TEST_VAP + "seth/multiScheme.vap",
            //TODO: Test 24 uses local 1wire data source
            //"024 " + TEST_VAP + "demos/eventsBarForAvailability3.vap",
            "025 " + TEST_VAP + "seth/mageis_rank2_labels.vap",
            //This fails when user tomcat6 tries to run it.  disable for now.
            //"026 " + TEST_VAP + "rbsp/efwComponents.vap", // not getting units...
        };


        for (String s : uris) {

            int count = parseInt(s.substring(0, 4).trim());
            s = s.substring(4);

            err.printf( "=== test002_%03d %s ===\n", count, s );

            String label = format("test002_%03d", count);

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

        QDataSet ds = getDataSet(TEST_DATA + "wav/fireworks.wav");
        final Application dom = getDocumentModel();
        dom.getCanvases(0).setFitted(false);
        setCanvasSize(400, 800);
        plot(0, autoHistogram(ds));
        dom.getPlots(0).getXaxis().getController().getDasAxis().setUseDomainDivider(true);
        dom.getPlotElements(0).setRenderType(fillToZero);
        dom.getPlots(0).getYaxis().setLabel("auto histogram of!chttp://www.autoplot.org/data/fireworks.wav");
        QDataSet ds2 = autoHistogram(log10(fftWindow(ds, 512)));
        plot(1, ds2);
        dom.getPlots(1).getXaxis().getController().getDasAxis().setUseDomainDivider(true);
        dom.getPlotElements(1).setRenderType(fillToZero);
        dom.getPlots(1).getYaxis().setLabel("auto histogram of!cpower spectrum");
        dom.getPlots(1).getXaxis().setLabel("log(Ops.fftWindow(512)");
        writeToPng("test002.png");

    }
}
