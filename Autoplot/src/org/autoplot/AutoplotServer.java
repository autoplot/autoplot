
package org.autoplot;

import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.Units;
import org.das2.graph.DasCanvas;
import static org.autoplot.ScriptContext.*;
import org.autoplot.datasource.DataSetURI;

import org.das2.util.ArgumentList;
import org.autoplot.dom.Application;
import org.autoplot.datasource.URISplit;
import org.autoplot.dom.DomUtil;
import org.autoplot.dom.Plot;

/**
 * Server for producing images from Autoplot URIs, first requested by U. Michigan.
 * @author jbf
 */
public class AutoplotServer {
    
    private static final Logger logger= Logger.getLogger("autoplot.server");
    
    public AutoplotServer() {
    }
        
    public static void main(String[] args) throws Exception {
          
        System.err.println("org.autoplot.AutoplotServer 20180726");

        Util.addFonts();
        
        ArgumentList alm= new ArgumentList("AutoplotServer");
        alm.addOptionalSwitchArgument("uri", "u", "uri", "", "URI to plot, or if .vap then rescale to width and height");
        alm.addOptionalSwitchArgument("vap", "v", "vap", "", "VAP to plot without scaling.");
        alm.addOptionalSwitchArgument("width", "w", "width", "-1", "width of result (default=700)");
        alm.addOptionalSwitchArgument("height", "h", "height", "-1", "height of result (default=400)");
        alm.addOptionalSwitchArgument("canvas.aspect", "a", "canvas.aspect", "", "aspect ratio" );
        alm.addOptionalSwitchArgument("format", "f", "format", "png", "output format png or pdf (default=png)");
        alm.addOptionalSwitchArgument("outfile", "o", "outfile", "-", "output filename or -");
        alm.addOptionalSwitchArgument("timeRange", "r", "timeRange", "", "set this to the timerange, instead of the range within the vap" );
        alm.addBooleanSwitchArgument( "enableResponseMonitor", null, "enableResponseMonitor", "monitor the event thread for long unresponsive pauses");
        alm.addBooleanSwitchArgument( "noexit", "z", "noexit", "don't exit after running, for use with scripts." );
        alm.addBooleanSwitchArgument( "nomessages", "q", "nomessages", "don't show message bubbles.");
        alm.addBooleanSwitchArgument( "autorange", null, "autorange", "autorange the Y and Z axes of each plot in the vap");
        alm.addBooleanSwitchArgument( "autorangeFlags", null, "autorangeFlags", "autorange the Y and Z axes of each plot where the autorange flag is set in the vap");
        alm.addBooleanSwitchArgument( "rescaleFonts", null, "rescaleFonts", "when the .vap is rescaled, also scale the fonts");
        
        alm.requireOneOf( new String[] { "uri", "vap" } );
        
        if ( !alm.process(args) ) {
            System.exit( alm.getExitCode() );
        }
        
        logger.log(Level.FINE, "process command line options");

        String suri = alm.getValue("uri");
        String vap = alm.getValue("vap");

        boolean rescaleFonts= alm.getBooleanValue("rescaleFonts" );

        if ( suri.equals("") && vap.equals("") ) {
            alm.printUsage();
            System.err.println("Either uri or vap must be specified.");
            System.exit(-1);
        }

        String timeRange= alm.getValue("timeRange").trim();
        
        int width = Integer.parseInt(alm.getValue("width"));
        int height = Integer.parseInt(alm.getValue("height"));
        
        if ( width==-1 && vap.equals("") ) {
            URISplit split= URISplit.parse(suri);
            if ( ".vap".equals(split.ext) ) {
                logger.warning("use --vap=file.vap to preserve width and height");
            }
        }
                
        String scanvasAspect = alm.getValue("canvas.aspect");
        String format= alm.getValue("format");
        String outfile= alm.getValue("outfile");

        boolean autorange= alm.getBooleanValue("autorange");
        boolean autorangeFlags= alm.getBooleanValue("autorangeFlags");
        
        if ( autorangeFlags ) autorange= true;
        
        if ( outfile.endsWith(".pdf") ) format= "pdf";

        //AutoplotUtil.maybeLoadSystemProperties();
        //if ( System.getProperty("enableResponseMonitor","false").equals("true")
        //                    || alm.getBooleanValue("enableResponseMonitor") ) {
        if ( alm.getBooleanValue("enableResponseMonitor") ) {
            EventThreadResponseMonitor emon= new EventThreadResponseMonitor();
            emon.start();
        }
        
        logger.log(Level.FINE, "getDocumentModel");
        Application dom= getDocumentModel();
        
        // do dimensions
        if ("".equals(scanvasAspect)) {
            if ( vap.length()==0 ) {
                if (width == -1) width = 700;
                if (height == -1) height = 400;
            }
        } else {
            double aspect = Units.dimensionless.parse(scanvasAspect).doubleValue(Units.dimensionless);
            if (width == -1 && height != -1) {
                width = (int) (height * aspect);
            }
            if (height == -1 && width != -1) {
                height = (int) (width / aspect);
            }
        }
        
        double scale; // the scale factor should we want to rescale fonts.
        
        Map<String,String> meta= new HashMap<>(); // store metadata like remote URI or VAP
        
        if ( !vap.equals("") ) {
            logger.log(Level.FINE, "about to load the vap {0}", vap);

            vap= URISplit.makeAbsolute( System.getProperty("user.dir"), vap );
            
            if ( timeRange.length()>0 ) {
                vap= URISplit.putParam( vap, "timerange", timeRange );
            }

            Application readOnlyDom= loadVap(vap); // read again to get options.
            load(vap);
            
            if ( vap.startsWith("http") ) {
                meta.put( PNG_KEY_VAP, vap );
            }
            
            //dom.syncTo( readOnlyDom );
            dom.getOptions().syncToAll( readOnlyDom.getOptions(), new ArrayList<>() );
            
            logger.log(Level.FINE, "vap is loaded and printable with data loaded");
            
            if ( width==-1 && height==-1) {
                width= dom.getController().getCanvas().getWidth();
                height= dom.getController().getCanvas().getHeight();
            } else if ( width==-1 ) {
                double aspect= ((double)dom.getController().getCanvas().getHeight()) / dom.getController().getCanvas().getWidth();
                width= (int)( height / aspect );
            } else if ( height==-1 ) {
                double aspect= ((double)dom.getController().getCanvas().getHeight()) / dom.getController().getCanvas().getWidth();
                height= (int)( width * aspect );
            }
            DasCanvas c = dom.getController().getCanvas().getController().getDasCanvas();
            
            Application dom2= getDocumentModel();
            
            boolean isAutoranged= false;
            if ( autorange ) {
                if ( autorangeFlags) {
                    for ( Plot p: dom2.getPlots() ) {
                        if ( p.getYaxis().isAutoRange() ) {
                            AutoplotUtil.resetZoomY(dom2,p);
                            isAutoranged= true;
                        }
                        if ( p.getZaxis().isAutoRange() ) {
                            AutoplotUtil.resetZoomZ(dom2,p);
                            isAutoranged= true;
                        }
                    }
                } else {
                    for ( Plot p: dom2.getPlots() ) {
                        dom2.getController().setPlot(p);
                        AutoplotUtil.resetZoomY(dom2);
                        AutoplotUtil.resetZoomZ(dom2);
                        isAutoranged= true;
                    }
                }
            }
            logger.log(Level.FINE, "axes were autoranged: {0}", isAutoranged);
            
            scale=  ((double)width) / dom.getController().getCanvas().getWidth();
            if ( scale!=1.0 ) rescaleFonts= true;
            
            dom.getController().getCanvas().setWidth(width);
            dom.getController().getCanvas().setHeight(height);
            dom.getController().getCanvas().getController().getDasCanvas().setSize(width, height);
            
            c.prepareForOutput(width, height); // KLUDGE, resize all components for TimeSeriesBrowse
            
        } else {
            dom.getController().getCanvas().setWidth(width);
            dom.getController().getCanvas().setHeight(height);
            DasCanvas c = dom.getController().getCanvas().getController().getDasCanvas();
            c.prepareForOutput(width, height); // KLUDGE, resize all components for TimeSeriesBrowse
            
            suri= URISplit.makeAbsolute( System.getProperty("user.dir"), suri );
            
            if ( !suri.startsWith("/") && !suri.startsWith("file:" ) ) {
                meta.put( PNG_KEY_URI, suri );
            }
            
            logger.log(Level.FINE, "plot uri {0}", suri);
            
            plot(suri);
            
            logger.log(Level.FINE, "done plot {0}", suri); 
            
            scale= ((double)width) / dom.getCanvases(0).getWidth();
        }
        
        if ( rescaleFonts ) {
            Application state= getDocumentModel();
            Font f= Font.decode( state.getCanvases(0).getFont() );
            Font newFont= f.deriveFont( f.getSize2D() * (float)scale );
            state.getCanvases(0).getController().getDasCanvas().setBaseFont(newFont);
            Font f2= state.getCanvases(0).getController().getDasCanvas().getFont();
            state.getOptions().setCanvasFont( DomUtil.encodeFont(f2) );
        }

        logger.fine("get the model which provides the canvas");
        
        Application model= getDocumentModel();
        if ( alm.getBooleanValue("nomessages" ) ) {
            model.getOptions().setPrintingLogLevel(Level.OFF);
        }

        switch (format) {
            case "png":
                if ( outfile.equals("-") ) {
                    model.getCanvases(0).setWidth(width);
                    model.getCanvases(0).setHeight(height);
                    writeToPng( System.out );
                } else {
                    logger.log(Level.INFO, "write to {0}", outfile);
                    writeToPng( outfile, width, height, meta );
                    System.err.println("write to "+ outfile);
                }
                break;
            case "pdf":
                if ( outfile.equals("-") ) {
                    model.getCanvases(0).setWidth(width);
                    model.getCanvases(0).setHeight(height);
                    writeToPdf( System.out );
                } else {
                    model.getCanvases(0).setWidth(width);
                    model.getCanvases(0).setHeight(height);
                    logger.log(Level.INFO, "write to {0}", outfile);
                    System.err.println("write to "+ outfile);
                    writeToPdf( outfile );
                }   
                break;
            default:
                throw new IllegalArgumentException("only pdf and png are supported: "+format);
        }

        logger.fine("about to exit");
        
        if ( !alm.getBooleanValue("noexit") ) {
            System.exit(0);
        }

    }
}
