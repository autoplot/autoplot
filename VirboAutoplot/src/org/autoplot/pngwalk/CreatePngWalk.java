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
import java.text.ParseException;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.das2.DasApplication;
import org.das2.components.DasProgressPanel;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.util.DasPNGConstants;
import org.das2.util.DasPNGEncoder;
import org.das2.datum.TimeParser;
import org.das2.datum.TimeUtil;
import org.das2.datum.UnitsUtil;
import org.das2.util.ArgumentList;
import org.das2.util.ExceptionHandler;
import org.das2.util.FileUtil;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.autoplot.AutoplotUtil;
import org.virbo.autoplot.ScriptContext;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.Plot;
import org.virbo.autoplot.state.StatePersistence;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.dsops.Ops;

/**
 * MakePngWalk code implemented in Java.  This was once a Python script, but it got complex enough that it was useful to
 * rewrite it in Java.
 * @author jbf
 */
public class CreatePngWalk {

    /**
     * Get the list of times, which can be one of:
     *   * rank 2 bins datasets  T[index;min,max]
     *   * dataset with rank 2 bins datasets   Event[ T[index;min,max] ]
     * This uses params.batchUri to get the URI that is resolved to control the times.  These
     * times then need to be formatted to filenames, 
     * 
     * @param params
     * @return
     * @throws IllegalArgumentException
     * @throws ParseException 
     */
    private static String[] getListOfTimes( Params params ) throws IllegalArgumentException, ParseException {
        String[] times;
        if ( params.useBatchUri ) {
            try {
                QDataSet timesds= org.virbo.jythonsupport.Util.getDataSet( params.batchUri );
                if ( !UnitsUtil.isTimeLocation( SemanticOps.getUnits(timesds) ) ) {
                    if ( (QDataSet) timesds.property(QDataSet.DEPEND_0)!=null ) {
                        timesds= (QDataSet) timesds.property(QDataSet.DEPEND_0);
                    } else if ( SemanticOps.isBundle(timesds) ) { // See EventsRenderer.makeCanonical
                        timesds= Ops.bundle( DataSetOps.unbundle(timesds,0), DataSetOps.unbundle(timesds,1) ); 
                    } else {
                        throw new IllegalArgumentException("expected [UTC,UTC] or [UTC,UTC,:]");
                    }
                }
                if ( timesds.rank()!=2 ) {
                    throw new IllegalArgumentException("expected bins dataset for times");
                }
                times= new String[timesds.length()];
                TimeParser tp= TimeParser.create(params.timeFormat);
                boolean jeggyMode=false;
                for ( int i=0; i<times.length; i++ ) {
                    if ( jeggyMode ) {
                        times[i]= tp.format( DataSetUtil.asDatumRange( timesds.slice(i) ) ) + ": "+DataSetUtil.asDatumRange( timesds.slice(i) ).toString();
                    } else {
                        times[i]= tp.format( DataSetUtil.asDatumRange( timesds.slice(i) ) );
                        Datum w0= DataSetUtil.asDatumRange( timesds.slice(i) ).width();
                        Datum w1= tp.parse(times[i]).getTimeRange().width();
                        if ( w1.multiply(2).lt(w0) ) {
                            if ( i==0 ) {
                                logger.fine("timeformat poorly represents the time, flipping into jeggy mode...");
                                jeggyMode= true;
                                times[i]= tp.format( DataSetUtil.asDatumRange( timesds.slice(i) ) ) + ": "+DataSetUtil.asDatumRange( timesds.slice(i) ).toString();
                            } else {
                                throw new IllegalArgumentException("timeformat poorly represents the time.");
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                if ( ex instanceof IllegalArgumentException ){
                    throw (IllegalArgumentException)ex;
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
     * parameters specifying the creation of a pngwalk.
     */
    public static class Params {

        /**
         *  output folder for the walk, e.g. /home/user/pngwalk/
         */
        public String outputFolder=null;

        /**
         *  timerange to cover for the walk, e.g. 2012 through 2014.
         */
        public String timeRangeStr=null;

        /**
         *  rescale to show context for each step, e.g. "0%,100%" or "0%-1hr,100%+1hr"
         */
        public String rescalex= "0%,100%";

        /**
         * autorange dependent dimensions
         */
        public boolean autorange= false;

        /**
         *  version tag to apply to each image, if non-null
         */
        public String version= null;

        /**
         * product name for the walk, e.g. product
         */
        public String product=null;

        /**
         * timeformat for the walk, e.g. $Y$m$d
         */
        public String timeFormat=null;

        /**
         * Uri that creates an events dataset, like 
         * 'http://emfisis.physics.uiowa.edu/events/rbsp-a/burst/rbsp-a_burst_times_20130201.txt?eventListColumn=3'
         * or null for automatically generating names based on template.
         */
        public String batchUri=null;
        
        /**
         * if true, use the URI to source the list of events.
         */
        public boolean useBatchUri= false;
        
        /*
         * if true, the also create thumbs.
         */
        public boolean createThumbs= true;

        /**
         * if true, skip over products that appear to be created already.
         */
        public boolean update= false;

        @Override
        public String toString() {
            return String.format( "outputFolder=%s\ntimeRange=%s\nrescalex=%s\nautorange=%s\nversion=%s\nproduct=%s\ntimeFormat=%s\ncreateThumbs=%s\nupdate=%s\n",
                    outputFolder, timeRangeStr, rescalex, autorange, version, product, timeFormat, createThumbs, update  );
        }
    }

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.pngwalk");

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
            encoder.addText(DasPNGConstants.KEYWORD_SOFTWARE, "Autoplot" );
            encoder.addText(DasPNGConstants.KEYWORD_PLOT_INFO, ldom.getCanvases(0).getController().getDasCanvas().getImageMetadata() );        

            encoder.write(image, out);
        } finally {
            if ( out!=null ) out.close();
        }
        if ( image==null ) throw new IllegalArgumentException("image not assigned, this shouldn't happen.");
        return image;
    }

    private static int returnCode1= 0;
    
    /**
     * run the pngwalk for the list of times.  The dom argument is copied so the
     * scientist can continue working while the pngwalk is run.
     * @param times list of times to run.  If a time contains a ": ", then the first part is the label and after is the exact time.
     * @param readOnlyDom the dom to render for each time.
     * @param params outputFolder and spec.
     * @param mon progress monitor to provide feedback about the run.
     * @return 0 if any were successful, 10 otherwise.
     * @throws IOException
     * @throws InterruptedException 
     */
    public static int doBatch( String[] times, Application readOnlyDom, Params params, ProgressMonitor mon ) throws IOException, InterruptedException {

        int returnCodeAll= 10;
        
        logger.log( Level.CONFIG, "CreatePngWalk.doBatch with params {0}", params);
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

        dom2.syncTo( readOnlyDom, java.util.Arrays.asList("id") );

        mon.setProgressMessage("write " + params.product + ".vap");
        logger.log(Level.FINE, "write {0}.vap", params.product);


        int thumbSize = 400;
        int w0 = dom2.getCanvases(0).getWidth();
        int h0 = dom2.getCanvases(0).getHeight();

        int thumbH = 0, thumbW = 0;
        if (params.createThumbs) {
            double aspect = 1. * w0 / h0;
            thumbH = (int) (Math.sqrt(Math.pow(thumbSize, 2) / (aspect * aspect + 1.)));
            thumbW = (int) (thumbH * aspect);
        }

        // Write out the vap file to product.vap
        StatePersistence.saveState(new java.io.File( outputFolder, params.product + ".vap"), dom2, "");

        String vap= new java.io.File( outputFolder, params.product + ".vap").toString();
        StringBuilder build= new StringBuilder();
        build.append( String.format( "JAVA -cp autoplot.jar org.autoplot.pngwalk.CreatePngWalk " ) );

        // Write out the parameters used to create this pngwalk in product.pngwalk
        PrintWriter ff=null;
        try {
            ff= new PrintWriter( new FileWriter( new java.io.File( outputFolder, params.product + ".pngwalk" ) ) );

            build.append("--vap=").append(vap).append( " ");
            build.append("--outputFolder=").append(params.outputFolder).append( " ");
            ff.println( "product=" + params.product );
            build.append("--product=").append(params.product).append( " ");
            ff.println( "timeFormat=" + params.timeFormat );
            build.append("--timeFormat='").append(params.timeFormat).append( "' ");
            ff.println( "timeRange=" + params.timeRangeStr );
            build.append("--timeRange='").append(params.timeRangeStr).append( "' ");
            if ( params.batchUri!=null ) {
                ff.println( "batchUri=" + params.batchUri );
                build.append("--batchUri=").append(params.batchUri).append( " ");
            }
            if ( params.rescalex!=null && !params.rescalex.equals("0%,100%") ) {
                ff.println( "rescalex="+ params.rescalex );
                build.append("--rescalex=").append(params.rescalex).append( " ");
            }
            if ( params.autorange ) {
                ff.println( "autorange="+ params.autorange );
                build.append("--autorange=").append(params.autorange).append( " ");
            }
            if ( params.version!=null && params.version.trim().length()>0 ) {
                ff.println( "version="+ params.version );
                build.append("--version=").append( params.version);
            }
        } finally {
            if ( ff!=null ) ff.close();
        }
         
        if ( !( mon instanceof NullProgressMonitor ) ) { // only show in interactive session
            System.err.println( build.toString() );
        }
        
        dom2.getController().waitUntilIdle();

        mon.setProgressMessage("making images");

        TimeParser tp = TimeParser.create(params.timeFormat);

        long t0 = java.lang.System.currentTimeMillis();
        int count = 0;

        String vers= ( params.version==null || params.version.trim().length()==0 ) ? "" : "_"+params.version.trim();

        
        appmodel.setExceptionHandler( new ExceptionHandler() {
            @Override
            public void handle(Throwable t) {
                t.printStackTrace();
                returnCode1= 11;
            }
            @Override
            public void handleUncaught(Throwable t) {
                t.printStackTrace();
                returnCode1= 12;
            }
        });
        
        for ( String atime : times ) {

            returnCode1= 0;
            
            int ic= atime.indexOf(": ");
            String exactTime= null; 
            if ( ic>-1 ) { // rfe batchfile time.
                exactTime= atime.substring(ic+2);
                atime= atime.substring(0,ic);
            }
            
            String filename= String.format("%s%s_%s%s.png", params.outputFolder, params.product, atime, vers );

            count = count + 1;
            if (mon.isCancelled()) {
                break;
            }
            mon.setTaskProgress(count);

            if ( params.update ) {
                File out= new File( filename );
                if ( out.exists() ) {
                    mon.setProgressMessage( String.format("skipping %s", filename ) );
                    logger.log( Level.FINE, String.format("skipping %s", filename ) );
                    continue;
                }
            }


            try {
                DatumRange dr;
                if ( exactTime==null ) {
                    dr= tp.parse(atime).getTimeRange();
                } else {
                    dr= DatumRangeUtil.parseTimeRange(exactTime);
                }
                if ( params.rescalex!=null && !params.rescalex.equals("0%,100%") ) {
                    dr= DatumRangeUtil.rescale( dr,params.rescalex );
                }
                dom2.setTimeRange(dr);

            } catch (ParseException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
            mon.setProgressMessage( String.format("write %s", filename ) );
            logger.log( Level.FINE, String.format("write %s", filename ) );

            appmodel.waitUntilIdle(false);
            if ( params.autorange ) {
                for ( Plot p: dom2.getPlots() ) {
                    dom2.getController().setPlot(p);
                    AutoplotUtil.resetZoomY(dom2);
                    AutoplotUtil.resetZoomZ(dom2);
                }

            }

            appmodel.waitUntilIdle(false);

            if ( atime.equals(times[0]) ) { // resetting zoomY and zoomZ can cause the labels and bounds to change.  Turn off autoranging.
                dom2.getOptions().setAutolayout(false);
                appmodel.waitUntilIdle(false);
            }
            
            BufferedImage image = myWriteToPng( filename, appmodel, dom2, w0, h0);

            if ( returnCode1==0 ) {
                returnCodeAll= 0;
            } else if ( returnCodeAll==10 ) {
                returnCodeAll= returnCode1;
            }
            
            if (params.createThumbs) {
                BufferedImage thumb400 = ImageResize.getScaledInstance(image, thumbW, thumbH, RenderingHints.VALUE_INTERPOLATION_BILINEAR, true);
                File outf= new java.io.File(String.format("%sthumbs400/%s_%s%s.png", params.outputFolder, params.product, atime, vers ) );
                File parentf= outf.getParentFile();
                if ( parentf!=null && !parentf.exists() ) {
                    if ( !parentf.mkdirs() ) {
                        throw new IllegalArgumentException("failed to make directories: "+parentf);
                    }
                }
                if ( !ImageIO.write(thumb400, "png", outf ) ) {
                    throw new IllegalArgumentException("no appropriate writer is found");
                }
                BufferedImage thumb100 = ImageResize.getScaledInstance(thumb400, thumbW/4, thumbH/4, RenderingHints.VALUE_INTERPOLATION_BILINEAR, true);
                outf= new java.io.File(String.format("%sthumbs100/%s_%s%s.png", params.outputFolder, params.product, atime, vers ) );
                parentf= outf.getParentFile();
                if ( parentf!=null && !parentf.exists() ) {
                    if ( !parentf.mkdirs() ) {
                        throw new IllegalArgumentException("failed to make directories: "+parentf);
                    }
                }
                if ( !ImageIO.write(thumb100, "png", outf ) ) {
                    throw new IllegalArgumentException("no appropriate writer is found");
                }
            }
            double imagesPerSec = count * 1000. / (java.lang.System.currentTimeMillis() - t0);
            //etaSec= (n-count) / imagesPerSec
            //etaStr= org.das2.datum.DatumUtil.asOrderOneUnits( Units.seconds.createDatum(etaSec) )
            mon.setAdditionalInfo(String.format( Locale.US, "(%.1f/sec)", imagesPerSec));
        }
        mon.finished();
        
        return returnCodeAll;
    }

    /**
     * run the pngwalk. If the params are null, then prompt the user with a GUI.
     * The pngwalk is run by resetting the timeRange field of the vap to each step
     * of the sequence.
     * @param dom the state from which a pngwalk is to be produced.
     * @param params a parameters structure (e.g. batch processing) or null.
     * @return an integer exit code where 0=success, 10=bad time format, 11=caught exception, 12=uncaught exception
     * @throws ParseException
     * @throws IOException
     * @throws InterruptedException 
     */
    public static int doIt(Application dom, Params params) throws ParseException, IOException, InterruptedException {
        int status= 0;
        
        if (params == null) {

            CreatePngWalkDialog p = new CreatePngWalkDialog();

            if (JOptionPane.showConfirmDialog(ScriptContext.getViewWindow(), p, "PngWalk Options", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                
                p.writeDefaults();

                params= p.getParams();

                File ff= new File( params.outputFolder );
                if ( p.getOverwriteCb().isSelected() && ff.exists() ) {
                    FileUtil.deleteFileTree(ff);
                }

                ProgressMonitor mon;
                if (ScriptContext.getViewWindow() == null) {
                    mon = new NullProgressMonitor();
                    System.err.println("ScriptContext.getViewWindow is null, running quietly in the background.");
                } else {
                    mon = DasProgressPanel.createFramed(ScriptContext.getViewWindow(), "running batch");
                }

                TimeParser tp= TimeParser.create(params.timeFormat);
                if ( !tp.isNested() ) {
                    JOptionPane.showMessageDialog( ScriptContext.getViewWindow(), "<html>Time spec must have fields nested: $Y,$m,$d, etc,<br>not "+params.timeFormat + " ." );
                    return -1;
                }

                String[] times = getListOfTimes( params );

                status= doBatch( times, dom, params, mon );

                String url;
                if (!mon.isCancelled()) {
                    if (params.outputFolder.charAt(1) == ':') {
                        url = "file:/" + params.outputFolder;
                    } else {
                        url = "file:" + params.outputFolder;
                    }

                    if (ScriptContext.getViewWindow() != null) {
                        logger.log(Level.FINE, "version=\"{0}\"", String.valueOf(params.version));
                        String vers= ( params.version==null || params.version.trim().length()==0 ) ? "" : "_"+params.version.trim();
                        final String st= url + params.product + "_" + params.timeFormat + vers + ".png";
                        SwingUtilities.invokeLater( new Runnable() {
                            public void run() {
                                PngWalkTool1.start( st, ScriptContext.getViewWindow() );
                            }
                        } );
                    }
                }
            }

        } else {
            
            String[] times = getListOfTimes( params );
            
            ProgressMonitor mon;
            if (ScriptContext.getViewWindow() == null) {
                if ( "true".equals( System.getProperty("java.awt.headless","false") ) ) {
                    mon = new NullProgressMonitor();
                } else {
                    mon = DasProgressPanel.createFramed( "running batch" );
                }
            } else {
                mon = DasProgressPanel.createFramed(ScriptContext.getViewWindow(), "running batch");
            }

            status= doBatch(times, dom, params, mon);

        }
        return status;
    }

    /**
     * command-line support for creating PNGWalks.  When PNGWalks are created 
     * interactively in Autoplot, this is used as well.
     * @param args see the code for the argument list.
     * @throws InterruptedException
     * @throws ParseException
     * @throws IOException 
     */
    public static void main( String[] args ) throws InterruptedException, ParseException, IOException {
        
        System.err.println("CreatePngWalk 20121008");
        final ArgumentList alm = new ArgumentList("CreatePngWalk");
        alm.addOptionalSwitchArgument( "timeFormat", "f", "timeFormat", "$Y$m$d", "timeformat for png files, e.g. $Y is year, $j is day of year");
        alm.addOptionalSwitchArgument( "timeRange", "r", "timeRange", "", "time range to cover, e.g. 2011 through 2012" );
        alm.requireOneOf( new String[] { "timeRange","batchUri" } );
        alm.addOptionalSwitchArgument( "batchUri", "b", "batchUri", "", "optionally provide list of timeranges" );
        alm.addOptionalSwitchArgument( "createThumbs", "t", "createThumbs", "y", "create thumbnails, y (default) or n" );
        alm.addOptionalSwitchArgument( "product", "n", "product", "product", "product name in each filename (default=product)");
        alm.addOptionalSwitchArgument( "outputFolder", "o", "outputFolder", "pngwalk", "location of root of pngwalk");
        alm.addSwitchArgument( "vap", "v", "vap", "vap file or URI to plot");
        alm.addOptionalSwitchArgument( "rescalex", null, "rescalex", "0%,100%", "rescale factor, such as '0%-1hr,100%+1hr', to provide context to each image");
        alm.addOptionalSwitchArgument( "version", null, "version", null, "additional version string to add to each filename, like v1.0");
        alm.addBooleanSwitchArgument( "autorange", null, "autorange", "rerange dependent dimensions Y and Z");
        alm.addBooleanSwitchArgument( "update", null, "update", "only calculate missing images");
        alm.addBooleanSwitchArgument( "testException", null, "testException", "throw a runtime exception to test exit code");
        alm.process(args);

        if ( alm.getBooleanValue("testException") ) {
            throw new RuntimeException("--textException on command line, throwing exception");
            // verified, 20130627.
            // java -cp autoplot.jar org.autoplot.pngwalk.CreatePngWalk --vap=x --testException
            // echo $? -> 1
            // Note, no files found does not yeild non-zero exit code!
        }
        
        Params params= new Params();
        params.createThumbs= alm.getValue("createThumbs").equals("y");
        params.outputFolder= alm.getValue("outputFolder");
        params.product= alm.getValue("product");
        params.timeFormat= alm.getValue("timeFormat");
        params.timeRangeStr= alm.getValue("timeRange");
        params.rescalex= alm.getValue("rescalex");
        params.version= alm.getValue("version");
        params.autorange= alm.getBooleanValue("autorange");
        params.update= alm.getBooleanValue("update");
        params.batchUri= alm.getValue("batchUri");
        String vap= alm.getValue("vap");
        ScriptContext.plot(vap);

        int status= doIt( ScriptContext.getDocumentModel(), params );

        System.exit(status); // something starts up thread that prevents java from exiting.
    }
}
