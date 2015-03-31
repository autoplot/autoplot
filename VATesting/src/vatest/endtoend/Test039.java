/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package vatest.endtoend;

import java.awt.BorderLayout;
import java.io.IOException;
import java.text.ParseException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import org.das2.datum.Datum;
import org.das2.datum.Units;
import org.das2.graph.DasAxis;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasColumn;
import org.das2.graph.DasPlot;
import org.das2.graph.DasRow;
import org.das2.graph.GraphUtil;
import org.das2.graph.Renderer;
import org.das2.graph.SeriesRenderer;
import org.das2.sdi.Adapter;
import org.das2.sdi.XYDataAdapter;
import org.das2.util.AboutUtil;
import org.das2.util.LoggerManager;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;
import sdi.data.FillDetector;
import sdi.data.UncertaintyProvider;
import sdi.data.XYData;
import sdi.data.XYMetadata;
import test.graph.QFunctionLarry;

/**
 * Test of Das2 aspects used as APL. test019 and test009 also test das2
 * internals.
 *
 * @author jbf
 */
public class Test039 {

    private static void testTCA() throws ParseException, IOException {
        int width = 500;
        int height = 400;

        JPanel panel = new JPanel();

        panel.setLayout(new BorderLayout());

        DasCanvas canvas = new DasCanvas(width, height);
        canvas.setAntiAlias(true);

        panel.add(canvas, BorderLayout.CENTER);

        // read data
        QDataSet yds = Ops.sin(Ops.linspace(0, 10, 1000));
        QDataSet tds = Ops.timegen("2010-01-01T00:00", "1 s", 1000);

        QDataSet ds = Ops.link(tds, yds);

        // here's some old das2 autoranging, works for this case
        DasAxis xaxis = GraphUtil.guessXAxis(ds);
        DasAxis yaxis = GraphUtil.guessYAxis(ds);

        DasPlot plot = new DasPlot(xaxis, yaxis);

        // here's autoplot as of 2005
        Renderer r = GraphUtil.guessRenderer(ds);
        plot.addRenderer(r);

        // ugh.  I need to make antialiased the default.  Right now it reads the property from $HOME/.dasrc
        if (r instanceof SeriesRenderer) {
            ((SeriesRenderer) r).setAntiAliased(true);
        }

        xaxis.setTcaFunction(new QFunctionLarry());

        xaxis.setDrawTca(true);

        canvas.add(plot, DasRow.create(canvas, null, "0%+2em", "100%-5em"),
                DasColumn.create(canvas, null, "0%+14em", "100%-4em"));

        canvas.setPrintingTag("APL $Y"); // this will cause a failure once per year...

        canvas.writeToPng("test039_tca.png");

        //JOptionPane.showMessageDialog(null,canvas);
    }

    /**
     * create a spectrogram with time axis and log y axis.
     *
     * @throws IOException
     */
    public static void demoSpectrogram() throws IOException {
        try {
            // read data
            QDataSet yds = Ops.pow(10, Ops.linspace(3, 8, 30));
            QDataSet tds = Ops.timegen("2015-03-31T00:00", "30 min", 48);
            Ops.randomSeed(5334);
            QDataSet zz = Ops.randn(48, 30);
            zz = Ops.link(tds, yds, zz);

            // here's some old das2 autoranging, works for this case
            DasAxis xaxis = GraphUtil.guessXAxis(zz);
            DasAxis yaxis = new DasAxis(
                    Datum.create(1e2, Units.dimensionless),
                    Datum.create(1e9, Units.dimensionless), DasAxis.VERTICAL, true);
            DasPlot plot = new DasPlot(xaxis, yaxis);

            Renderer r = GraphUtil.guessRenderer(zz);
            plot.addRenderer(r);
            plot.setPreviewEnabled(true); // this should probably be the default now.

            DasCanvas canvas = new DasCanvas(600, 400);
            canvas.add(plot,
                    DasRow.create(canvas, null, "0%+2em", "100%-5em"),
                    DasColumn.create(canvas, null, "0%+4em", "100%-4em"));
            canvas.setPrintingTag("demoSpectrogram");

            canvas.writeToPng("test039_spectrogram.png");

            if (!headless) {
                JOptionPane.showMessageDialog(null, canvas);
            }

        } catch (ParseException ex) {
            Logger.getLogger(Test039.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    /**
     * demonstrate code to explicitly set tick locations and add tick location.
     *
     * @throws IOException
     */
    public static void demoTicks() throws IOException {
        try {

            QDataSet yds = Ops.pow(10, Ops.linspace(3, 8, 30));
            QDataSet tds = Ops.timegen("2015-03-31T00:00", "30 min", 48);
            Ops.randomSeed(5334);
            QDataSet zz = Ops.randn(48, 30);
            zz = Ops.link(tds, yds, zz);

            DasAxis xaxis = GraphUtil.guessXAxis(zz);
            DasAxis yaxis = new DasAxis(
                    Datum.create(1e2, Units.dimensionless),
                    Datum.create(1e9, Units.dimensionless), DasAxis.VERTICAL, true);
            DasPlot plot = new DasPlot(xaxis, yaxis);

            double[] ticks = new double[]{1e4, 1e6, 1e8};
            yaxis.setTickV(ticks, ticks);

            Renderer r = GraphUtil.guessRenderer(zz);
            plot.addRenderer(r);
            plot.setPreviewEnabled(true); // this should probably be the default now.

            DasCanvas canvas = new DasCanvas(600, 400);
            canvas.add(plot,
                    DasRow.create(canvas, null, "0%+2em", "100%-5em"),
                    DasColumn.create(canvas, null, "0%+7em", "100%-4em"));
            canvas.setPrintingTag("demoTicks");

            canvas.writeToPng("test039_demoTicks.png");

            if (!headless) {
                JOptionPane.showMessageDialog(null, canvas);
            }

        } catch (ParseException ex) {
            Logger.getLogger(Test039.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    /**
     * Javadoc for SDI is at:
     * http://sarahandjeremy.net/jeremy/autoplot/ScienceDataInterfaces/javadoc/
     * 
     * Javadoc for QDataSet adapter is at:
     * http://sarahandjeremy.net/jeremy/autoplot/QDataSetScienceDataInterfaces/javadoc/
     * 
     * @throws Exception 
     */
    private static void demoSdi1() throws Exception {
        XYMetadata xymetadata = new XYMetadata() {

            @Override
            public sdi.data.Units getXUnits() {
                return new sdi.data.Units("seconds since 2015-04-01");
            }

            @Override
            public sdi.data.Units getYUnits() {
                return new sdi.data.Units("eV");
            }

            @Override
            public String getXName() {
                return "x";
            }

            @Override
            public String getYName() {
                return "y";
            }

            @Override
            public String getXLabel() {
                return "x";
            }

            @Override
            public String getYLabel() {
                return "y";
            }

            @Override
            public String getName() {
                return "demo";
            }
        };

        XYData xydata = new XYData() {

            @Override
            public Optional<FillDetector> getFillDetector() {
                return Optional.empty();
            }

            @Override
            public Optional<UncertaintyProvider> getXUncertProvider() {
                return Optional.empty();
            }

            @Override
            public Optional<UncertaintyProvider> getYUncertProvider() {
                return Optional.empty();
            }

            @Override
            public XYMetadata getMetadata() {
                return xymetadata;
            }

            @Override
            public int size() {
                return 40;
            }

            @Override
            public double getX(int i) {
                return 3600 + 3600 * i; // "seconds since 2015-04-01"
            }

            @Override
            public double getY(int i) {
                return Math.pow(10, 2 + Math.sin(getX(i) / 3600));
            }
        };

        QDataSet ds = XYDataAdapter.adapt(xydata);

        DasAxis xaxis = GraphUtil.guessXAxis(ds);
        DasAxis yaxis = new DasAxis(
                Datum.create(1e0, Units.eV),
                Datum.create(1e4, Units.eV), DasAxis.VERTICAL, true);
        DasPlot plot = new DasPlot(xaxis, yaxis);

        Renderer r = GraphUtil.guessRenderer(ds);
        plot.addRenderer(r);
        plot.setPreviewEnabled(true); // this should probably be the default now.

        DasCanvas canvas = new DasCanvas(600, 400);
        canvas.add(plot,
                DasRow.create(canvas, null, "0%+2em", "100%-5em"),
                DasColumn.create(canvas, null, "0%+7em", "100%-4em"));
        canvas.setPrintingTag("demoSdi1");

        canvas.writeToPng("test039_demoSdi1.png");

        if (!headless) {
            JOptionPane.showMessageDialog(null, canvas);
        }

    }

    private static final boolean headless = "true".equals(System.getProperty("java.awt.headless"));

    /**
     * run this in testing with no arguments and in headless mode. If this is
     * not headless, then some tests will stop so the result can be inspected
     * manually. A single argument can be provided to run a particular test.
     *
     * @param args length zero or one string array which would contain
     * "spectrogram" or "ticks" etc.
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        String s = AboutUtil.getAboutHtml();
        String[] ss = s.split("\\<br\\>");
        for (String sss : ss) {
            System.err.println(sss);
        }

        LoggerManager.getLogger("das2.graphics.axis").setLevel(Level.ALL);

        if (args.length == 0 || args[0].contains("tca")) {
            testTCA();
        }

        if (args.length == 0 || args[0].contains("spectrogram")) {
            demoSpectrogram();
        }

        if (args.length == 0 || args[0].contains("ticks")) {
            demoTicks();
        }

        if (args.length == 0 || args[0].contains("sdi1")) {
            demoSdi1();
        }

        System.exit(0);

    }
}
