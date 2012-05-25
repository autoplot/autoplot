/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.pngwalk;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.das2.components.DasProgressPanel;
import org.das2.util.DasPNGConstants;
import org.das2.util.DasPNGEncoder;
import org.das2.datum.TimeParser;
import org.das2.util.ArgumentList;
import org.das2.util.FileUtil;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.autoplot.ScriptContext;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.state.StatePersistence;

/**
 *
 * @author jbf
 */
public class CreatePngWalk {

    public static class Params {

        public String outputFolder;
        public String timeRangeStr;
        public String product;
        public String timeFormat;
        public boolean createThumbs;
    }

    private static final Logger logger= Logger.getLogger("vap.createPngWalk");

    private static BufferedImage myWriteToPng(String filename, ApplicationModel appmodel, Application ldom, int width, int height) throws InterruptedException, FileNotFoundException, IOException {
        OutputStream out=null;
        BufferedImage image=null;
        try {
            File outf= new File( filename );
            File parentf= outf.getParentFile();
            if ( parentf!=null && !parentf.exists() ) {
                if ( !parentf.mkdirs() ) {
                    throw new IllegalArgumentException("failed to make directories "+parentf);
                }
            }
            out= new java.io.FileOutputStream(filename);
            image = (BufferedImage) ldom.getCanvases(0).getController().getDasCanvas().getImage(width, height);
            DasPNGEncoder encoder = new DasPNGEncoder(); // 20120525: tested against ImageIO.write comparable time and space.
            encoder.addText(DasPNGConstants.KEYWORD_CREATION_TIME, new java.util.Date().toString());
            encoder.write(image, out);
        } finally {
            if ( out!=null ) out.close();
        }
        if ( image==null ) throw new IllegalArgumentException("image not assigned, this shouldn't happen.");
        return image;
    }

    public static void doBatch(String[] times, Application dom, Params params, ProgressMonitor mon) throws IOException, InterruptedException {

        if ( !( params.outputFolder.endsWith("/") || params.outputFolder.endsWith("\\") ) ) {
            params.outputFolder= params.outputFolder + "/";
        }
        File outputFolder=  new java.io.File(params.outputFolder);
        if ( !outputFolder.exists() && !outputFolder.mkdirs() ) {
            throw new IOException( "failed mkdirs: "+outputFolder);
        }
        if ( !outputFolder.canWrite() ) {
            throw new IOException( "unable to write to folder "+outputFolder );
        }

        if (params.createThumbs) {
            File thumbsFolder= new java.io.File(params.outputFolder,"thumbs400/" );
            if ( !thumbsFolder.exists() && !( thumbsFolder.mkdirs() ) ) {
                throw new IOException( "failed mkdirs: "+thumbsFolder );
            }
            if ( !thumbsFolder.canWrite() ) {
                throw new IOException( "unable to write to folder "+thumbsFolder );
            }
        } else {
            File thumbsFolder= new java.io.File(params.outputFolder,"thumbs400/" );
            if ( thumbsFolder.exists() ) {
                System.err.println("warning: thumbs folder already exists!");
            }
        }

        int n = times.length;
        mon.setTaskSize(n);
        mon.started();

        mon.setProgressMessage("initializing child application");

        ApplicationModel appmodel = new ApplicationModel();
        appmodel.addDasPeersToAppAndWait();
        
        Application dom2 = appmodel.getDocumentModel();

        mon.setProgressMessage("synchronize to this application");

        dom2.syncTo(dom, java.util.Arrays.asList("id"));
        //for (PlotElement p : dom2.getPlotElements()) {     // kludge for bug 2985891 since bug after cleanup.
        //    p.getController().doResetRenderType(p.getRenderType());
        //}
        //dom2.syncTo(dom, java.util.Arrays.asList("id"));

        mon.setProgressMessage("write " + params.product + ".vap");


        int thumbSize = 400;
        int w0 = dom2.getCanvases(0).getWidth();
        int h0 = dom2.getCanvases(0).getHeight();

        int thumbH = 0, thumbW = 0;
        if (params.createThumbs) {
            double aspect = 1. * w0 / h0;
            thumbH = (int) (Math.sqrt(Math.pow(thumbSize, 2) / (aspect * aspect + 1.)));
            thumbW = (int) (thumbH * aspect);
        }


        StatePersistence.saveState(new java.io.File( outputFolder, params.product + ".vap"), dom2, "");

        PrintWriter ff= new PrintWriter( new FileWriter( new java.io.File( outputFolder, params.product + ".pngwalk" ) ) );
        ff.println( "product=" + params.product );
        ff.println( "timeFormat=" + params.timeFormat );
        ff.close();
        
        dom2.getController().waitUntilIdle();

        mon.setProgressMessage("making images");

        TimeParser tp = TimeParser.create(params.timeFormat);

        long t0 = java.lang.System.currentTimeMillis();
        int count = 0;

        for (String i : times) {
            count = count + 1;
            if (mon.isCancelled()) {
                break;
            }
            mon.setTaskProgress(count);
            try {
                dom2.setTimeRange(tp.parse(i).getTimeRange());
            } catch (ParseException ex) {
                Logger.getLogger(CreatePngWalk.class.getName()).log(Level.SEVERE, null, ex);
            }
            mon.setProgressMessage(String.format("write " + params.product + "_%s.png", i));
            logger.log( Level.INFO, "write {0}_%s.png", params.product);

            appmodel.waitUntilIdle(false);
            BufferedImage image = myWriteToPng(String.format("%s%s_%s.png", params.outputFolder, params.product, i), appmodel, dom2, w0, h0);
            if (params.createThumbs) {
                BufferedImage thumb400 = ImageResize.getScaledInstance(image, thumbW, thumbH, RenderingHints.VALUE_INTERPOLATION_BILINEAR, true);
                File outf= new java.io.File(String.format("%sthumbs400/%s_%s.png", params.outputFolder, params.product, i) );
                File parentf= outf.getParentFile();
                if ( parentf!=null && !parentf.exists() ) {
                    if ( !parentf.mkdirs() ) {
                        throw new IllegalArgumentException("failed to make directories: "+parentf);
                    }
                }
                if ( !ImageIO.write(thumb400, "png", outf ) ) {
                    throw new IllegalArgumentException("no appropriate writer is found");
                }
            }
            double imagesPerSec = count * 1000. / (java.lang.System.currentTimeMillis() - t0);
            //etaSec= (n-count) / imagesPerSec
            //etaStr= org.das2.datum.DatumUtil.asOrderOneUnits( Units.seconds.createDatum(etaSec) )
            mon.setAdditionalInfo(String.format( Locale.US, "(%.1f/sec)", imagesPerSec));
        }
        mon.finished();
    }

    public static void doIt(Application dom, Params params) throws ParseException, IOException, InterruptedException {
        if (params == null) {
            CreatePngWalkDialog p = new CreatePngWalkDialog();
            if (JOptionPane.showConfirmDialog(ScriptContext.getViewWindow(), p, "PngWalk Options", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                
                p.writeDefaults();
                
                params = new Params();
                params.outputFolder = p.getOutputFolderTf().getText();
                if ( !( params.outputFolder.endsWith("/") || params.outputFolder.endsWith("\\") ) ) {
                    params.outputFolder= params.outputFolder + "/";
                }
                params.timeRangeStr = p.getTimeRangeTf().getText();
                params.product = p.getFlnRootTf().getText();
                params.timeFormat = p.getTimeFormatTf().getText();
                params.createThumbs = p.getCreateThumbsCb().isSelected();

                File ff= new File( params.outputFolder );
                if ( p.getOverwriteCb().isSelected() && ff.exists() ) {
                    FileUtil.deleteFileTree(ff);
                }

                ProgressMonitor mon;
                if (ScriptContext.getViewWindow() == null) {
                    mon = new org.das2.util.monitor.NullProgressMonitor();
                    System.err.println("ScriptContext.getViewWindow is null, running quietly in the background.");
                } else {
                    mon = DasProgressPanel.createFramed(ScriptContext.getViewWindow(), "running batch");
                }

                TimeParser tp= TimeParser.create(params.timeFormat);
                if ( !tp.isNested() ) {
                    JOptionPane.showMessageDialog( ScriptContext.getViewWindow(), "<html>Time spec must have fields nested: $Y,$m,$d, etc,<br>not "+params.timeFormat + " ." );
                    return;
                }

                String[] times = ScriptContext.generateTimeRanges(params.timeFormat, params.timeRangeStr);

                doBatch(times, dom, params, mon);

                String url;
                if (!mon.isCancelled()) {
                    if (params.outputFolder.charAt(1) == ':') {
                        url = "file:/" + params.outputFolder;
                    } else {
                        url = "file:" + params.outputFolder;
                    }

                    if (ScriptContext.getViewWindow() != null) {
                        final String st= url + params.product + "_" + params.timeFormat + ".png";
                        SwingUtilities.invokeLater( new Runnable() {
                            public void run() {
                                PngWalkTool1.start( st, ScriptContext.getViewWindow() );
                            }
                        } );
                    }
                }
            }

        } else {
            String[] times = ScriptContext.generateTimeRanges(params.timeFormat, params.timeRangeStr);

            ProgressMonitor mon;
            if (ScriptContext.getViewWindow() == null) {
                mon = new org.das2.util.monitor.NullProgressMonitor();
            } else {
                mon = DasProgressPanel.createFramed(ScriptContext.getViewWindow(), "running batch");
            }

            doBatch(times, dom, params, mon);

        }

    }

    public static void main( String[] args ) throws InterruptedException, ParseException, IOException {
        final ArgumentList alm = new ArgumentList("CreatePngWalk");
        alm.addOptionalSwitchArgument( "timeFormat", "f", "timeFormat", "$Y$m$d", "timeformat for png files, e.g. $Y is year, $j is day of year");
        alm.addSwitchArgument( "timeRange", "r", "timeRange", "time range to cover, e.g. 2011 through 2012" );
        alm.addOptionalSwitchArgument( "createThumbs", "t", "createThumbs", "y", "create thumbnails, y (default) or n" );
        alm.addOptionalSwitchArgument( "product", "n", "product", "product", "product name in each filename (default=product)");
        alm.addOptionalSwitchArgument( "outputFolder", "o", "outputFolder", "pngwalk", "location of root of pngwalk");
        alm.addSwitchArgument( "vap", "v", "vap", "vap file or URI to plot");
        alm.process(args);

        Params params= new Params();
        params.createThumbs= alm.getValue("createThumbs").equals("y");
        params.outputFolder= alm.getValue("outputFolder");
        params.product= alm.getValue("product");
        params.timeFormat= alm.getValue("timeFormat");
        params.timeRangeStr= alm.getValue("timeRange");
        
        String vap= alm.getValue("vap");
        ScriptContext.plot(vap);

        doIt( ScriptContext.getDocumentModel(), params );

        System.exit(0); // something starts up thread that prevents java from exiting.
    }
}
