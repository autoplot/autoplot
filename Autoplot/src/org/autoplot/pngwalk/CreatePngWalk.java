package org.autoplot.pngwalk;

import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.das2.components.DasProgressPanel;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.util.DasPNGConstants;
import org.das2.util.DasPNGEncoder;
import org.das2.datum.TimeParser;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.datum.format.DatumFormatter;
import org.das2.datum.format.FormatStringFormatter;
import org.das2.util.ArgumentList;
import org.das2.util.ExceptionHandler;
import org.das2.util.FileUtil;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.ApplicationModel;
import org.autoplot.AutoplotUtil;
import org.autoplot.ScriptContext;
import org.autoplot.dom.Application;
import org.autoplot.dom.Plot;
import org.autoplot.state.StatePersistence;
import org.das2.qds.DataSetOps;
import org.das2.qds.DataSetUtil;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.URISplit;
import org.das2.qds.ops.Ops;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedWriter;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.autoplot.dom.Options;
import org.autoplot.dom.PlotElement;
import org.das2.datum.InconvertibleUnitsException;

/**
 * CreatePngWalk makes a sequence of images from a .vap file or the current
 * state. This is used with PngWalkTool to quickly flip through the images once
 * they are created. This was once a Python script, but it got complex enough
 * that it was useful to rewrite it in Java.
 *
 * @author jbf
 */
public class CreatePngWalk {

    /**
     * Get the list of times, which can be one of:<ul>
     * <li> rank 2 bins datasets T[index;min,max]
     * <li> dataset with rank 2 bins datasets Event[ T[index;min,max] ]
     * </ul>
     * This uses params.batchUri to get the URI that is resolved to control the
     * times. These times then need to be formatted to filenames, or if
     * params.batchUriName is "$o" then the output filename is explicitly
     * specified in the last column.
     *
     * @param params
     * @return array of strings: filename: timeRange
     * @throws IllegalArgumentException
     * @throws ParseException
     */
    private static String[] getListOfTimes(Params params, List<String> warnings) throws IllegalArgumentException, ParseException {
        String[] times;
        if (params.useBatchUri) {
            try {
                String uri = params.batchUri;
                QDataSet timesds = org.autoplot.jythonsupport.Util.getDataSet(uri);
                times = new String[timesds.length()];

                if (params.batchUriName.equals("")) {
                    if (!UnitsUtil.isTimeLocation(SemanticOps.getUnits(timesds))) {
                        if ((QDataSet) timesds.property(QDataSet.DEPEND_0) != null) {
                            timesds = (QDataSet) timesds.property(QDataSet.DEPEND_0);
                        } else if (SemanticOps.isBundle(timesds)) { // See EventsRenderer.makeCanonical
                            timesds = Ops.bundle(DataSetOps.unbundle(timesds, 0), DataSetOps.unbundle(timesds, 1));
                        } else {
                            throw new IllegalArgumentException("expected events list URI");
                        }
                    }
                    if (timesds.rank() != 2) {
                        timesds = Ops.createEvents(timesds);
                    }
                    if (timesds.rank() != 2) {
                        throw new IllegalArgumentException("expected bins dataset for times");
                    }

                    TimeParser tp = TimeParser.create(params.timeFormat);
                    for (int i = 0; i < times.length; i++) {
                        times[i] = tp.format(DataSetUtil.asDatumRange(timesds.slice(i))) + ": " + DataSetUtil.asDatumRange(timesds.slice(i)).toString();
                    }
                } else {
                    timesds = Ops.createEvents(timesds);
                    Units tu = ((Units) ((QDataSet) timesds.property(QDataSet.BUNDLE_1)).property(QDataSet.UNITS, 0));
                    Units eu = ((Units) ((QDataSet) timesds.property(QDataSet.BUNDLE_1)).property(QDataSet.UNITS, 3));
                    if (uri.endsWith(".txt")) { // hey it's just an orbits file...
                        logger.fine("reading events file to preserve identity of orbits.");
                        for (int i = 0; i < times.length; i++) {
                            String s1 = eu.createDatum(timesds.slice(i).value(3)).toString(); // orbit name
                            String s = s1 + ": " + "orbit:" + uri + ":" + s1;
                            times[i] = s;
                        }
                    } else {
                        logger.fine("reading events file as start/stop times.");
                        for (int i = 0; i < times.length; i++) {
                            times[i] = eu.createDatum(timesds.slice(i).value(3)).toString() + ": " + DatumRange.newDatumRange(timesds.slice(i).value(0), timesds.slice(i).value(1), tu); // TODO: this should be easier to code
                        }
                    }
                }
            } catch (Exception ex) {
                if (ex instanceof IllegalArgumentException) {
                    throw (IllegalArgumentException) ex;
                } else {
                    throw new IllegalArgumentException(ex);
                }
            }
        } else {
            times = ScriptContext.generateTimeRanges(params.timeFormat, params.timeRangeStr);
        }
        return times;
    }

    /**
     * return true if any data is visible.
     *
     * @param dom2
     * @return true if some data is visible.
     */
    private static boolean isDataVisible(Application dom2) {
        DatumRange tr = dom2.getTimeRange();
        boolean dataVisible = false;
        for (PlotElement pe : dom2.getPlotElements()) {
            QDataSet dsout = pe.getController().getDataSet();
            if (dsout == null) {
                continue;
            }
            try {
                dsout = SemanticOps.trim(dsout, tr, null);
            } catch (InconvertibleUnitsException ex) {
                // do nothing--I think it's a non-time-axis TSB.
            }
            if (dsout.length() == 0) {
                continue;
            }
            dataVisible = true;
            break;
        }
        return dataVisible;
    }

    /**
     * parameters specifying the creation of a pngwalk.
     */
    public static class Params {

        /**
         * output folder for the walk, e.g. /home/user/pngwalk/
         */
        public String outputFolder = null;

        /**
         * timerange to cover for the walk, e.g. 2012 through 2014.
         */
        public String timeRangeStr = null;

        /**
         * rescale to show context for each step, e.g. "0%,100%" or
         * "0%-1hr,100%+1hr"
         */
        public String rescalex = "0%,100%";

        /**
         * autorange dependent dimensions
         */
        public boolean autorange = true;

        /**
         * consider autorange flags for each axis.
         */
        public boolean autorangeFlags = true;

        /**
         * clone the dom and run on the copy, so the original Autoplot is free.
         */
        public boolean runOnCopy = true;

        /**
         * version tag to apply to each image, if non-null
         */
        public String version = null;

        /**
         * product name for the walk, e.g. product
         */
        public String product = null;

        /**
         * timeformat for the walk, e.g. $Y$m$d
         */
        public String timeFormat = null;

        /**
         * Uri that creates an events dataset, like
         * 'http://emfisis.physics.uiowa.edu/events/rbsp-a/burst/rbsp-a_burst_times_20130201.txt?eventListColumn=3'
         * or null for automatically generating names based on template.
         */
        public String batchUri = null;

        /**
         * if true, use the URI to source the list of events.
         */
        public boolean useBatchUri = false;

        /**
         * if non-null, use the name in the event list column instead of the
         * product and timeFormat. For example, the batch file contains lines
         * like: 2015-03-05T02:10 2015-03-05T02:14 marsex/event1 so this png
         * will have the name outputFolder + "marsex/event1" + ".png" This must
         * be either empty "" or "$o" for now.
         */
        public String batchUriName = "";

        /*
         * if true, the also create thumbs.
         */
        public boolean createThumbs = true;

        /**
         * if true, skip over products that appear to be created already.
         */
        public boolean update = false;

        /**
         * presently this is png or pdf
         */
        public String outputFormat = "png";

        /**
         * also write a .vap file
         */
        public boolean writeVap = true;

        /**
         * check that image contains data.
         */
        public boolean removeNoData = false;

        @Override
        public String toString() {
            return String.format("outputFolder=%s\ntimeRange=%s\nrescalex=%s\nautorange=%s\nversion=%s\nproduct=%s\ntimeFormat=%s\ncreateThumbs=%s\nupdate=%s\n",
                    outputFolder, timeRangeStr, rescalex, autorange, version, product, timeFormat, createThumbs, update);
        }
    }

    private static final Logger logger = org.das2.util.LoggerManager.getLogger("autoplot.pngwalk");

    private static BufferedImage myWriteToPng(String filename, Application ldom, int width, int height) throws InterruptedException, FileNotFoundException, IOException {
        OutputStream out = null;
        BufferedImage image = null;
        try {
            File outf = new File(filename);
            File parentf = outf.getParentFile();
            if (parentf != null && !parentf.exists()) {
                if (!parentf.mkdirs()) {
                    throw new IllegalArgumentException("failed to make directories " + parentf);
                }
            }
            out = new java.io.FileOutputStream(filename);
            image = (BufferedImage) ldom.getCanvases(0).getController().getDasCanvas().getImage(width, height);
            DasPNGEncoder encoder = new DasPNGEncoder(); // 20120525: tested against ImageIO.write comparable time and space.
            encoder.addText(DasPNGConstants.KEYWORD_CREATION_TIME, new java.util.Date().toString());
            encoder.addText(DasPNGConstants.KEYWORD_SOFTWARE, "Autoplot");
            encoder.addText(DasPNGConstants.KEYWORD_PLOT_INFO, ldom.getCanvases(0).getController().getDasCanvas().getImageMetadata());

            encoder.write(image, out);
        } finally {
            if (out != null) {
                out.close();
            }
        }
        if (image == null) {
            throw new IllegalArgumentException("image not assigned, this shouldn't happen.");
        }
        return image;
    }

    private static int returnCode1 = 0;

    /**
     * run the pngwalk for the list of times. The dom argument is copied so the
     * scientist can continue working while the pngwalk is run.
     *
     * @param times list of times to run. If a time contains a ": ", then the
     * first part is the label and after is the exact time.
     * @param readOnlyDom the dom to render for each time.
     * @param params outputFolder and spec.
     * @param mon progress monitor to provide feedback about the run.
     * @return 0 if any were successful, 10 otherwise.
     * @throws IOException
     * @throws InterruptedException
     */
    public static int doBatch(String[] times, Application readOnlyDom, Params params, ProgressMonitor mon) throws IOException, InterruptedException {
        return doBatch(Arrays.asList(times).iterator(), times.length, readOnlyDom, params, mon);
    }

    /**
     * run the pngwalk for the list of times. The dom argument is copied so the
     * scientist can continue working while the pngwalk is run.
     *
     * @param times iterator with each time.
     * @param size size, if known, or -1 if not known.
     * @param readOnlyDom the dom to render for each time.
     * @param params outputFolder and spec.
     * @param mon progress monitor to provide feedback about the run.
     * @return 0 if any were successful, 10 otherwise.
     * @throws IOException
     * @throws InterruptedException
     */
    public static int doBatch(Iterator<String> times, int size, Application readOnlyDom, Params params, ProgressMonitor mon) throws IOException, InterruptedException {
        final ArrayList<String> pngFilenameArrayThumbs = new ArrayList();
        final ArrayList<String> pngFilenameArrayBig = new ArrayList();
        final ArrayList<String> timeLabels = new ArrayList();

        int returnCodeAll = 10;

        logger.log(Level.CONFIG, "CreatePngWalk.doBatch with params {0}", params);
        if (!(params.outputFolder.endsWith("/") || params.outputFolder.endsWith("\\"))) {
            params.outputFolder = params.outputFolder + "/";
        }
        File outputFolder = new java.io.File(params.outputFolder);
        if (!outputFolder.exists() && !outputFolder.mkdirs()) {
            throw new IOException("failed mkdirs: " + outputFolder);
        }
        if (!outputFolder.canWrite()) {
            throw new IOException("unable to write to folder " + outputFolder);
        }

        if (params.update) {
            File f = new java.io.File(outputFolder, params.product + ".lock");
            if (!f.exists()) {
                logger.info("creating lock file so multiple processes can work on pngwalk.");
                if (!f.createNewFile()) {
                    logger.info("failed to create lock file, some work may be redone.");
                }
            }
        }

        if (params.createThumbs) {
            File thumbsFolder = new java.io.File(params.outputFolder, "thumbs400/");
            if (!thumbsFolder.exists() && !(thumbsFolder.mkdirs())) {
                throw new IOException("failed mkdirs: " + thumbsFolder);
            }
            if (!thumbsFolder.canWrite()) {
                throw new IOException("unable to write to folder " + thumbsFolder);
            }
        } else {
            File thumbsFolder = new java.io.File(params.outputFolder, "thumbs400/");
            if (thumbsFolder.exists()) {
                System.err.println("warning: thumbs folder already exists!");
            }
        }

        int n = size;
        mon.setTaskSize(n);
        mon.started();

        try {
            mon.setProgressMessage("initializing child application");

            TimeParser tp = TimeParser.create(params.timeFormat);
            Application dom = (Application) readOnlyDom.copy();
            dom.getOptions().syncToAll(readOnlyDom.getOptions(), new ArrayList());

            if (!times.hasNext()) {
                throw new IllegalArgumentException("there must be at least one time");
            }

            String atime = times.next();
            try {
                int ic = atime.indexOf(": ");
                String exactTime;
                if (ic > -1) { // rfe batchfile time.
                    exactTime = atime.substring(ic + 2);
                } else {
                    exactTime = atime;
                }
                // set the initial timerange to avoid an extraneous load.
                if (params.useBatchUri) {
                    DatumRange tr1 = DatumRangeUtil.parseTimeRange(exactTime);
                    dom.setTimeRange(tr1);
                } else {
                    DatumRange tr1 = tp.parse(exactTime).getTimeRange();
                    dom.setTimeRange(tr1);
                }
            } catch (ParseException ex) {
                throw new RuntimeException(ex);
            }

            Application dom2;
            int w0, h0;

            if (params.runOnCopy) {
                ApplicationModel appmodel = new ApplicationModel();
                appmodel.addDasPeersToAppAndWait();

                dom2 = appmodel.getDocumentModel();

                mon.setProgressMessage("synchronize to this application");

                dom2.getCanvases(0).setHeight(dom.getCanvases(0).getHeight());
                dom2.getCanvases(0).setWidth(dom.getCanvases(0).getWidth());
                w0 = dom2.getCanvases(0).getWidth();
                h0 = dom2.getCanvases(0).getHeight();
                dom2.getCanvases(0).getController().getDasCanvas().setSize(w0, h0);
                dom2.getCanvases(0).getController().getDasCanvas().revalidate();

                dom2.syncTo(dom, java.util.Arrays.asList("id"));
                dom2.getController().waitUntilIdle();
                dom2.syncTo(dom, java.util.Arrays.asList("id")); // work around bug where someone resets the margin column http://jfaden.net:8080/hudson/job/autoplot-test033/5786/artifact/
                dom2.getOptions().syncToAll(readOnlyDom.getOptions(), new ArrayList()); // 1165 grid overlay

            } else {
                dom2 = readOnlyDom;
                w0 = dom2.getCanvases(0).getWidth();
                h0 = dom2.getCanvases(0).getHeight();
            }

            ApplicationModel appmodel = dom2.getController().getApplicationModel();

            int thumbSize = 400;

            int thumbH = 0, thumbW = 0;
            if (params.createThumbs) {
                double aspect = 1. * w0 / h0;
                thumbH = (int) (Math.sqrt(Math.pow(thumbSize, 2) / (aspect * aspect + 1.)));
                thumbW = (int) (thumbH * aspect);
            }

            // Write out the vap file to product.vap
            if (params.writeVap) {
                mon.setProgressMessage("write " + params.product + ".vap");
                logger.log(Level.FINE, "write {0}.vap", params.product);
                StatePersistence.saveState(new java.io.File(outputFolder, params.product + ".vap"), dom2, "");
            }

            String vap = new java.io.File(outputFolder, params.product + ".vap").toString();
            StringBuilder build = new StringBuilder();
            build.append(String.format("JAVA -cp autoplot.jar org.autoplot.pngwalk.CreatePngWalk "));

            try (PrintWriter ff = new PrintWriter(new FileWriter(new java.io.File(outputFolder, params.product + ".pngwalk")))) {  // Write out the parameters used to create this pngwalk in product.pngwalk

                build.append("--vap=").append(vap).append(" ");
                build.append("--outputFolder=").append(params.outputFolder).append(" ");

                ff.println("# set the following line to the location of the pngwalk");
                ff.println("baseurl=.");
                ff.println("product=" + params.product);
                build.append("--product=").append(params.product).append(" ");
                ff.println("timeFormat=" + params.timeFormat);
                build.append("--timeFormat='").append(params.timeFormat).append("' ");

                if (params.useBatchUri == false) {
                    ff.println("timeRange=" + params.timeRangeStr);
                    build.append("--timeRange='").append(params.timeRangeStr).append("' ");
                }

                if (params.batchUriName.equals("$o")) {
                    ff.println("# the filePattern may need editing, depending on extension and subdirectories.");
                    ff.println("filePattern=*.png");
                }

                if (params.useBatchUri) {
                    if (params.batchUri != null && !params.batchUri.equals("")) {
                        ff.println("batchUri=" + params.batchUri);
                        build.append("--batchUri=").append(params.batchUri).append(" ");
                    }
                    if (!params.batchUriName.equals("")) {
                        ff.println("batchUriName=" + params.batchUri);
                        build.append("--batchUriName=").append(params.batchUri).append(" ");
                    }
                }

                if (params.rescalex != null && !params.rescalex.equals("0%,100%")) {
                    ff.println("rescalex=" + params.rescalex);
                    build.append("--rescalex=").append(params.rescalex).append(" ");
                }
                if (params.autorange) {
                    ff.println("autorange=" + params.autorange);
                    build.append("--autorange=").append(params.autorange).append(" ");
                }
                if (params.autorangeFlags) {
                    ff.println("autorangeFlags=" + params.autorangeFlags);
                    build.append("--autorangeFlags=").append(params.autorangeFlags).append(" ");
                }
                if (params.version != null && params.version.trim().length() > 0) {
                    ff.println("version=" + params.version);
                    build.append("--version=").append(params.version);
                }

                if (!params.outputFormat.equals("png")) {
                    ff.println("outputFormat=" + params.outputFormat);
                    build.append("--outputFormat=").append(params.outputFormat);
                }

            }

            if (!(mon instanceof NullProgressMonitor)) { // only show in interactive session
                System.err.println(build.toString());
            }

            dom2.getController().waitUntilIdle();

            mon.setProgressMessage("making images");

            List<Long> t0s = new LinkedList<>();

            int count = 0;

            appmodel.setExceptionHandler(new ExceptionHandler() {
                @Override
                public void handle(Throwable t) {
                    logger.log(Level.WARNING, null, t);
                    returnCode1 = 11;
                }

                @Override
                public void handleUncaught(Throwable t) {
                    logger.log(Level.WARNING, null, t);
                    returnCode1 = 12;
                }
            });

            //LoggerManager.setEnableTimers(true);
            //LoggerManager.setTimerLogfile("/tmp/foo.autoplot.txt");
            String currentTimeLabel;

            boolean firstTime = true;

            int countRecent = 20;  // approx number in past minute

            do {

                t0s.add(System.currentTimeMillis());
                while (t0s.size() > countRecent) {
                    t0s.remove(0);
                }

                if (!firstTime) {
                    atime = times.next();
                }

                //LoggerManager.resetTimer();
                returnCode1 = 0;

                int ic = atime.indexOf(": ");
                String exactTime = null;
                if (ic > -1) { // rfe batchfile time.
                    exactTime = atime.substring(ic + 2);
                    atime = atime.substring(0, ic);
                }

                //LoggerManager.markTime("455");
                String filename = getFilename(params, "", atime);

                /**
                 * Code for adding images into global arrayList for use in HTML
                 * method
                 *
                 * @author Armond Luthens
                 * @date 09/21/2015
                 */
                pngFilenameArrayThumbs.add(getRelativeFilename(params, "thumbs100", atime));
                pngFilenameArrayBig.add(getRelativeFilename(params, "", atime));

                //LoggerManager.markTime("469");
                count = count + 1;
                if (mon.isCancelled()) {
                    break;
                }
                mon.setTaskProgress(count);

                FileChannel fileChannel = null;
                File outTemp = new File(filename + ".lock");

                if (params.update) {
                    File lockFile = new File(params.outputFolder + params.product + ".lock");
                    FileLock lock = null;
                    if (lockFile.exists()) {
                        Path p = Paths.get(lockFile.toURI());
                        fileChannel = FileChannel.open(p, StandardOpenOption.WRITE);
                        lock = fileChannel.lock();
                    }
                    File out = new File(filename);
                    if (out.exists() || outTemp.exists()) {
                        mon.setProgressMessage(String.format("skipping %s", filename));
                        logger.log(Level.FINE, String.format("skipping %s", filename));
                        if (lock != null) {
                            lock.release();
                            fileChannel.close();
                        }
                        if (firstTime) { // resetting zoomY and zoomZ can cause the labels and bounds to change.  Turn off autoranging.
                            dom2.getOptions().setAutolayout(false);
                            appmodel.waitUntilIdle();
                            firstTime = false;
                        }
                        continue;
                    } else {
                        if (!outTemp.createNewFile()) {
                            logger.log(Level.WARNING, "unable to make new file: {0}", outTemp);
                        }
                    }
                    if (lock != null) {
                        lock.release();
                        fileChannel.close();
                    }
                } else {
                    if (!outTemp.createNewFile()) {
                        logger.log(Level.WARNING, "unable to make new file: {0}", outTemp);
                    }
                }

                //LoggerManager.markTime("486");
                try {
                    DatumRange dr;
                    if (exactTime == null) {
                        dr = tp.parse(atime).getTimeRange();
                    } else {
                        dr = DatumRangeUtil.parseTimeRange(exactTime);
                    }
                    if (params.rescalex != null) {
                        String rescalex = params.rescalex.trim();
                        if (rescalex.length() > 0 && !params.rescalex.equals("0%,100%")) {
                            dr = DatumRangeUtil.rescale(dr, params.rescalex);
                        }
                    }
                    currentTimeLabel = dr.toString();
                    timeLabels.add(currentTimeLabel);

                    if (!dom2.getTimeRange().equals(dr)) { // don't even call it for one png--I don't think it matters.
                        dom2.setTimeRange(dr);
                    }

                } catch (ParseException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
                mon.setProgressMessage(String.format("write %s", filename));
                logger.log(Level.FINE, String.format("write %s", filename));

                //LoggerManager.markTime("514");
                appmodel.waitUntilIdle();
                //LoggerManager.markTime("516");

                if (params.autorange) {
                    if (params.autorangeFlags) {
                        for (Plot p : dom2.getPlots()) {
                            if (p.getYaxis().isAutoRange()) {
                                AutoplotUtil.resetZoomY(dom2, p);
                            }
                            if (p.getZaxis().isAutoRange()) {
                                AutoplotUtil.resetZoomZ(dom2, p);
                            }
                        }
                    } else {
                        for (Plot p : dom2.getPlots()) {
                            dom2.getController().setPlot(p);
                            AutoplotUtil.resetZoomY(dom2);
                            AutoplotUtil.resetZoomZ(dom2);
                        }
                    }
                }
                //LoggerManager.markTime("526");
                appmodel.waitUntilIdle();

                //LoggerManager.markTime("529");
                if (firstTime) { // resetting zoomY and zoomZ can cause the labels and bounds to change.  Turn off autoranging.
                    dom2.getOptions().setAutolayout(false);
                    appmodel.waitUntilIdle();
                    firstTime = false;
                }

                if (params.removeNoData) {
                    if (!isDataVisible(dom2)) {
                        logger.log(Level.FINE, "No data found for \"{0}\"", atime);
                        if (!outTemp.delete()) {
                           logger.log(Level.WARNING, "unable to delete {0}", outTemp);
                        }
                        continue;
                    }
                }

                BufferedImage image = null;

                //LoggerManager.markTime("538");
                try {
                    if (params.outputFormat.equals("png")) {
                        image = myWriteToPng(filename, dom2, w0, h0);
                    } else {
                        dom2.getCanvases(0).getController().getDasCanvas().writeToPDF(filename);
                    }
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, "unable to write file " + filename, ex);
                    throw new IOException("unable to write file " + filename, ex);
                }
                //LoggerManager.markTime("548");

                if (returnCode1 == 0) {
                    returnCodeAll = 0;
                } else if (returnCodeAll == 10) {
                    returnCodeAll = returnCode1;
                }

                if (params.createThumbs && params.outputFormat.equals("png")) {
                    BufferedImage thumb400 = ImageResize.getScaledInstance(image, thumbW, thumbH, RenderingHints.VALUE_INTERPOLATION_BILINEAR, true);
                    File outf = new java.io.File(getFilename(params, "thumbs400", atime));
                    File parentf = outf.getParentFile();
                    if (parentf != null && !parentf.exists()) {
                        if (!parentf.mkdirs()) {
                            throw new IllegalArgumentException("failed to make directories: " + parentf);
                        }
                    }
                    if (!ImageIO.write(thumb400, "png", outf)) {
                        throw new IllegalArgumentException("no appropriate writer is found");
                    }
                    BufferedImage thumb100 = ImageResize.getScaledInstance(thumb400, thumbW / 4, thumbH / 4, RenderingHints.VALUE_INTERPOLATION_BILINEAR, true);
                    outf = new java.io.File(getFilename(params, "thumbs100", atime));
                    parentf = outf.getParentFile();
                    if (parentf != null && !parentf.exists()) {
                        if (!parentf.mkdirs()) {
                            throw new IllegalArgumentException("failed to make directories: " + parentf);
                        }
                    }
                    if (!ImageIO.write(thumb100, "png", outf)) {
                        throw new IllegalArgumentException("no appropriate writer is found");
                    }
                }

                //LoggerManager.markTime("581");
                double imagesPerSec = t0s.size() * 1000. / (java.lang.System.currentTimeMillis() - t0s.get(0));
                double etaSec = (n - count) / imagesPerSec;
                String etaStr = "";
                if (count > 3) {
                    Datum eta = org.das2.datum.DatumUtil.asOrderOneUnits(Units.seconds.createDatum(etaSec));
                    DatumFormatter df;
                    df = new FormatStringFormatter("%.1f", true);
                    etaStr = String.format(Locale.US, ", eta %s", df.format(eta));
                }
                if (imagesPerSec < 1.0) {
                    mon.setAdditionalInfo(String.format(Locale.US, "(%.1f/min%s)", imagesPerSec * 60, etaStr));
                } else {
                    mon.setAdditionalInfo(String.format(Locale.US, "(%.1f/sec%s)", imagesPerSec, etaStr));
                }

                if (!outTemp.delete()) {
                    logger.log(Level.WARNING, "unable to delete {0}", outTemp);
                }

                //LoggerManager.markTime("597");
            } while (times.hasNext());

            //LoggerManager.setEnableTimers(false);
            if (!mon.isCancelled()) {
                writeHTMLFile(params, pngFilenameArrayThumbs, pngFilenameArrayBig, timeLabels);
            }

        } finally {
            if (!mon.isFinished()) {
                mon.finished();
            }
        }
        return returnCodeAll;

    }

    /**
     * create the filename for the time.
     *
     * @param params the parameters
     * @param thumbdir "" or "thumbs100" or "thumbs400"
     * @param atime the time "20150822"
     * @return
     * @throws IllegalArgumentException
     */
    private static String getFilename(Params params, String thumbdir, String atime) throws IllegalArgumentException {
        String filename;
        if (thumbdir.length() > 0 && !thumbdir.endsWith("/")) {
            thumbdir = thumbdir + "/";
        }
        if (params.useBatchUri && params.batchUriName.equals("$o")) {
            String name = atime; // really?
            // sometimes we want capitalized extention.
            String outputFormat = params.outputFormat;
            if (name.toLowerCase().endsWith(params.outputFormat)) {
                outputFormat = name.substring(name.length() - outputFormat.length());
                name = name.substring(0, name.length() - (params.outputFormat.length() + 1));
            }
            filename = String.format("%s%s%s.%s", params.outputFolder, thumbdir, name, outputFormat);
        } else if (params.useBatchUri && !params.batchUriName.equals("")) {
            throw new IllegalArgumentException("batchUriName must be \"\" or \"$o\"");
        } else {
            String vers = (params.version == null || params.version.trim().length() == 0) ? "" : "_" + params.version.trim();
            filename = String.format("%s%s%s_%s%s.%s", params.outputFolder, thumbdir, params.product, atime, vers, params.outputFormat);
        }
        return filename;
    }

    /**
     * create the filename for the time.
     *
     * @param params the parameters
     * @param thumbdir "" or "thumbs100" or "thumbs400"
     * @param atime the time "20150822"
     * @return
     * @throws IllegalArgumentException
     */
    private static String getRelativeFilename(Params params, String thumbdir, String atime) throws IllegalArgumentException {
        String filename;
        if (thumbdir.length() > 0 && !thumbdir.endsWith("/")) {
            thumbdir = thumbdir + "/";
        }
        if (params.useBatchUri && params.batchUriName.equals("$o")) {
            String name = atime; // really?
            // sometimes we want capitalized extention.
            String outputFormat = params.outputFormat;
            if (name.toLowerCase().endsWith(params.outputFormat)) {
                outputFormat = name.substring(name.length() - outputFormat.length());
                name = name.substring(0, name.length() - (params.outputFormat.length() + 1));
            }
            filename = String.format("%s%s.%s", thumbdir, name, outputFormat);
        } else if (params.useBatchUri && !params.batchUriName.equals("")) {
            throw new IllegalArgumentException("batchUriName must be \"\" or \"$o\"");
        } else {
            String vers = (params.version == null || params.version.trim().length() == 0) ? "" : "_" + params.version.trim();
            filename = String.format("%s%s_%s%s.%s", thumbdir, params.product, atime, vers, params.outputFormat);
        }
        return filename;
    }

    /**
     * run the pngwalk. If the params are null, then prompt the user with a GUI.
     * The pngwalk is run by resetting the timeRange field of the vap to each
     * step of the sequence.
     *
     * @param dom the state from which a pngwalk is to be produced.
     * @param params a parameters structure (e.g. batch processing) or null.
     * @return an integer exit code where 0=success, 10=bad time format,
     * 11=caught exception, 12=uncaught exception
     * @throws ParseException
     * @throws IOException
     * @throws InterruptedException
     */
    public static int doIt(Application dom, Params params) throws ParseException, IOException, InterruptedException {
        int status = 0;

        Window viewWindow= ScriptContext.getInstance().getViewWindow();

        if (params == null) {

            CreatePngWalkDialog p = new CreatePngWalkDialog();
                
            if (AutoplotUtil.showConfirmDialog( viewWindow, p, "Create PngWalk Options", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {

                p.writeDefaults();

                params = p.getParams();

                File ff = new File(params.outputFolder);
                if (p.getOverwriteCb().isSelected() && ff.exists()) {
                    FileUtil.deleteFileTree(ff);
                }

                ProgressMonitor mon;
                if (viewWindow == null) {
                    mon = new NullProgressMonitor();
                    System.err.println("ScriptContext.getViewWindow is null, running quietly in the background.");
                } else {
                    mon = DasProgressPanel.createFramed(viewWindow, "running batch");
                }

                if (params.timeFormat.length() > 0) {
                    TimeParser tp = TimeParser.create(params.timeFormat);
                    if (!tp.isNested()) {
                        JOptionPane.showMessageDialog(viewWindow, "<html>Time spec must have fields nested: $Y,$m,$d, etc,<br>not " + params.timeFormat + " .");
                        return -1;
                    }
                }

                String[] times = getListOfTimes(params, new ArrayList());

                status = doBatch(times, dom, params, mon);

                String url;
                if (!mon.isCancelled()) {
                    url = new File(params.outputFolder).toURI().toString();

                    if (viewWindow != null && params.outputFormat.equals("png")) {
                        logger.log(Level.FINE, "version=\"{0}\"", String.valueOf(params.version));
                        String vers = (params.version == null || params.version.trim().length() == 0) ? "" : "_" + params.version.trim();
                        String st1;
                        if (params.batchUriName.length() == 0) {
                            st1 = url + params.product + "_" + params.timeFormat + vers + ".png";
                        } else {
                            st1 = url + "*.png";
                        }
                        final String st = st1;
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                PngWalkTool.start(st, viewWindow);
                            }
                        });
                    } else if (viewWindow != null) {
                        String vers = (params.version == null || params.version.trim().length() == 0) ? "" : "_" + params.version.trim();
                        final String st = url + params.product + "_" + params.timeFormat + vers + "." + params.outputFormat;
                        JOptionPane.showMessageDialog(viewWindow, "<html>Files created:<br>" + st);
                    }
                }
            }

        } else {

            String[] times = getListOfTimes(params, new ArrayList());

            ProgressMonitor mon;
            if (viewWindow == null) {
                if ("true".equals(System.getProperty("java.awt.headless", "false"))) {
                    mon = new NullProgressMonitor();
                } else {
                    mon = DasProgressPanel.createFramed("running batch");
                }
            } else {
                mon = DasProgressPanel.createFramed(viewWindow, "running batch");
            }

            status = doBatch(times, dom, params, mon);

        }
        return status;
    }

    /**
     * Method to write HTML file of all the pictures to give a gallery view
     *
     * @author Armond Luthens
     * @param params
     * @param pngFilenameArrayThumbs
     * @param pngFilenameArrayBig
     * @param timeLabels
     *
     */
    public static void writeHTMLFile(Params params, ArrayList<String> pngFilenameArrayThumbs, ArrayList<String> pngFilenameArrayBig, ArrayList<String> timeLabels) {

        if (params.update || (timeLabels.size() != pngFilenameArrayBig.size())) {
            logger.info("skipping create HTML step because of partial run");
            return;
        }

        String filePath = params.outputFolder + "" + params.product + ".html";
        //String filePath = "pngImagePage2.html";
        File f = new File(filePath);

        String htmlOpen = "<html>\n";
        String htmlHead = "\t<head><title>PNG Gallery " + params.product + "</title></head>\n";
        String htmlBody = "\t<body style=\"background-color: #6B6B6B; margin:0;\">\n";
        String htmlClose1 = "\t\t</div>\n";
        String htmlClose2 = "\t</body>\n";
        String htmlClose3 = "</html>";

        //String pageHeaderOpen= "\t\t<div style=\"padding:20px; top: 0px; margin-right=0px; background-color:black; color:white;height:30px;\">\n\t\t\t"
        //            + "<strong>" + params.product + "_"+ params.timeFormat + "</strong>\n" + "\t\t</div>\n";
        String pageHeaderOpen = "\t\t<div style=\"padding:20px; top: 0px; margin-right:0px; background-color:black; color:white;height:30px;\">\n\t\t\t"
                + "<strong>" + "PNG WALK" + "</strong>\n" + "\t\t</div>\n";
        String addImageString;
        String htmlAnchorStringOpen = "\t\t\t\t<a href=\"";
        String htmlAnchorStringClose = "\">\n";
        String htmlImageStringOpen = "\t\t\t\t<img src=\"";
        String htmlImageStringClose = "\" style=\"width:72px;height:68px;margin-left:10px;margin-bottom:10px;\"></a>\n";
        String htmlImageCaptionOpen = "\t\t\t\t<figcaption style=\"color: white; text-align:center;\">";
        String htmlImageCaptionClose = "\t\t\t\t</figcaption>\n";
        String htmlImageContainer = "\t\t<div style=\"background-color: #6B6B6B;margin-left:20px;\">\n";
        String htmlFigureOpen = "\t\t\t<figure style=\"width:78px; float:left;\">\n";
        String htmlFigureClose = "\t\t\t</figure>\n";

        String currentPngFilename;
        String currentPngFilenameBIG;
        String fileNameToDisplay;
        String bigImageLink;
        String fullImageCaption;
        int count = 0;

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(f))) {
            bw.write(htmlOpen);
            bw.write(htmlHead);
            bw.write(htmlBody);
            bw.write(pageHeaderOpen);
            bw.write(htmlImageContainer);

            for (String pngFilenameArray1 : pngFilenameArrayThumbs) {

                currentPngFilename = pngFilenameArray1;
                //System.out.println("image file path: " + currentPngFilename);

                currentPngFilenameBIG = pngFilenameArrayBig.get(count);
                fileNameToDisplay = timeLabels.get(count);
                count++;

                bigImageLink = htmlAnchorStringOpen + currentPngFilenameBIG + htmlAnchorStringClose;
                addImageString = htmlImageStringOpen + currentPngFilename + htmlImageStringClose; //insert image into html code
                fullImageCaption = htmlImageCaptionOpen + fileNameToDisplay + htmlImageCaptionClose; //insert corresponding date for image into html code

                bw.write(htmlFigureOpen);
                bw.write(bigImageLink);
                bw.write(addImageString);
                bw.write(fullImageCaption);
                bw.write(htmlFigureClose);
            }
            bw.write(htmlClose1);
            bw.write(htmlClose2);
            bw.write(htmlClose3);
        } catch (IOException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    /**
     * command-line support for creating PNGWalks. When PNGWalks are created
     * interactively in Autoplot, this is used as well.
     *
     * @param args see the code for the argument list.
     * @throws InterruptedException
     * @throws ParseException
     * @throws IOException
     */
    public static void main(String[] args) throws InterruptedException, ParseException, IOException {

        System.err.println("CreatePngWalk 20200819");
        final ArgumentList alm = new ArgumentList("CreatePngWalk");
        alm.addOptionalSwitchArgument("timeFormat", "f", "timeFormat", "$Y$m$d", "timeformat for png files, e.g. $Y is year, $j is day of year");
        alm.addOptionalSwitchArgument("timeRange", "r", "timeRange", "", "time range to cover, e.g. 2011 through 2012");
        alm.requireOneOf(new String[]{"timeRange", "batchUri"});
        alm.addOptionalSwitchArgument("batchUri", "b", "batchUri", "", "optionally provide list of timeranges");
        alm.addOptionalSwitchArgument("batchUriName", null, "batchUriName", "", "use $o to use the filename in the batch file");
        alm.addOptionalSwitchArgument("createThumbs", "t", "createThumbs", "y", "create thumbnails, y (default) or n");
        alm.addOptionalSwitchArgument("product", "n", "product", "product", "product name in each filename (default=product)");
        alm.addOptionalSwitchArgument("outputFolder", "o", "outputFolder", "pngwalk", "location of root of pngwalk");
        alm.addOptionalSwitchArgument("outputFormat", null, "outputFormat", "png", "output format png or pdf");
        alm.addOptionalSwitchArgument("vap", "v", "vap", null, "vap file to plot");
        alm.addOptionalSwitchArgument("uri", "u", "uri", null, "single URI plotted");
        alm.requireOneOf(new String[]{"vap", "uri"});
        alm.addOptionalSwitchArgument("rescalex", null, "rescalex", "0%,100%", "rescale factor, such as '0%-1hr,100%+1hr', to provide context to each image");
        alm.addOptionalSwitchArgument("version", null, "version", null, "additional version string to add to each filename, like v1.0");
        alm.addBooleanSwitchArgument("autorange", null, "autorange", "rerange dependent dimensions Y and Z");
        alm.addBooleanSwitchArgument("autorangeFlags", null, "autorangeFlags", "only autorange axes with autorange=true");
        alm.addBooleanSwitchArgument("update", null, "update", "only calculate missing images");
        alm.addBooleanSwitchArgument("removeNoData", null, "removeNoData", "don't produce images which have no visible data.");
        alm.addBooleanSwitchArgument("testException", null, "testException", "throw a runtime exception to test exit code");

        if (!alm.process(args)) {
            System.exit(alm.getExitCode());
        }

        if (alm.getBooleanValue("testException")) {
            throw new RuntimeException("--textException on command line, throwing exception");
            // verified, 20130627.
            // java -cp autoplot.jar org.autoplot.pngwalk.CreatePngWalk --vap=x --testException
            // echo $? -> 1
            // Note, no files found does not yeild non-zero exit code!
        }

        if (System.getProperty("noCheckCertificate", "true").equals("true")) {
            logger.fine("disabling HTTP certificate checks.");
            try {
                TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[0];
                        }

                        @Override
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }

                    }
                };

                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

                // Create all-trusting host name verifier
                HostnameVerifier allHostsValid = new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                };

                HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

            } catch (NoSuchAlgorithmException | KeyManagementException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }

        Params params = new Params();
        params.createThumbs = alm.getValue("createThumbs").equals("y");
        params.outputFolder = alm.getValue("outputFolder");
        params.product = alm.getValue("product");
        params.timeFormat = alm.getValue("timeFormat");
        params.timeRangeStr = alm.getValue("timeRange");
        params.rescalex = alm.getValue("rescalex");
        params.version = alm.getValue("version");
        params.autorange = alm.getBooleanValue("autorange");
        params.autorangeFlags = alm.getBooleanValue("autorangeFlags");
        params.update = alm.getBooleanValue("update");
        params.batchUri = alm.getValue("batchUri");
        params.removeNoData = alm.getBooleanValue("removeNoData");
        if (params.batchUri != null && params.batchUri.length() > 0) {
            params.useBatchUri = true;
            params.batchUriName = alm.getValue("batchUriName");
        }
        params.outputFormat = alm.getValue("outputFormat");

        Application dom;

        String vap = alm.getValue("vap");
        if (vap != null) {
            if ((vap.length() > 2 && vap.charAt(1) == ':')) {
                logger.fine("reference appears to be absolute (Windows)");
            } else {
                vap = URISplit.makeAbsolute(new File(".").getAbsolutePath(), vap);
            }
            dom = (Application) StatePersistence.restoreState(new File(vap));
        } else {
            String uri = alm.getValue("uri");
            ScriptContext scriptContext= ScriptContext.getInstance();
            scriptContext.setCanvasSize(800, 600);
            scriptContext.plot(uri);
            dom = scriptContext.getDocumentModel();
        }

        if (vap != null && vap.contains(params.outputFolder)) {
            params.writeVap = false;
        }

        int status = doIt(dom, params);

        System.exit(status); // something starts up thread that prevents java from exiting.
    }
}
