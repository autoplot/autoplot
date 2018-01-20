
package org.autoplot;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.Units;
import org.das2.graph.DasCanvas;
import static org.autoplot.ScriptContext.*;

import org.das2.util.ArgumentList;
import org.autoplot.dom.Application;
import org.autoplot.datasource.URISplit;

/**
 * Server for producing images from Autoplot URIs, first requested by U. Michigan.
 * @author jbf
 */
public class AutoplotServer {
    
    private static final Logger logger= Logger.getLogger("autoplot.server");
    
    public AutoplotServer() {
    }
        
    public static void main(String[] args) throws Exception {
          
        System.err.println("org.autoplot.AutoplotServer 20180120");

        Util.addFonts();
        
        ArgumentList alm= new ArgumentList("AutoplotServer");
        alm.addOptionalSwitchArgument("uri", "u", "uri", "", "URI to plot");
        alm.addOptionalSwitchArgument("vap", "v", "vap", "", "VAP to plot");
        alm.addOptionalSwitchArgument("width", "w", "width", "-1", "width of result (default=700)");
        alm.addOptionalSwitchArgument("height", "h", "height", "-1", "height of result (default=400)");
        alm.addOptionalSwitchArgument("canvas.aspect", "a", "canvas.aspect", "", "aspect ratio" );
        alm.addOptionalSwitchArgument("format", "f", "format", "png", "output format png or pdf (default=png)");
        alm.addOptionalSwitchArgument("outfile", "o", "outfile", "-", "output filename or -");
        alm.addBooleanSwitchArgument( "enableResponseMonitor", null, "enableResponseMonitor", "monitor the event thread for long unresponsive pauses");
        alm.addBooleanSwitchArgument( "noexit", "z", "noexit", "don't exit after running, for use with scripts." );
        alm.addBooleanSwitchArgument( "nomessages", "q", "nomessages", "don't show message bubbles.");
        alm.requireOneOf( new String[] { "uri", "vap" } );
        
        alm.process(args);

        String suri = alm.getValue("uri");
        String vap = alm.getValue("vap");

        if ( suri.equals("") && vap.equals("") ) {
            alm.printUsage();
            System.err.println("Either uri or vap must be specified.");
            System.exit(-1);
        }

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

        if ( outfile.endsWith(".pdf") ) format= "pdf";

        //AutoplotUtil.maybeLoadSystemProperties();
        //if ( System.getProperty("enableResponseMonitor","false").equals("true")
        //                    || alm.getBooleanValue("enableResponseMonitor") ) {
        if ( alm.getBooleanValue("enableResponseMonitor") ) {
            EventThreadResponseMonitor emon= new EventThreadResponseMonitor();
            emon.start();
        }
        
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
        
        if ( !vap.equals("") ) {
            logger.log(Level.FINE, "about to load the vap {0}", vap);
            
            load(vap);
            
            logger.log(Level.FINE, "vap is loaded");
            
            if ( width==-1 || height==-1) {
                width= dom.getController().getCanvas().getWidth();
                height= dom.getController().getCanvas().getHeight();
            }
            DasCanvas c = dom.getController().getCanvas().getController().getDasCanvas();
            
            c.prepareForOutput(width, height); // KLUDGE, resize all components for TimeSeriesBrowse
        } else {
            dom.getController().getCanvas().setWidth(width);
            dom.getController().getCanvas().setHeight(height);
            DasCanvas c = dom.getController().getCanvas().getController().getDasCanvas();
            c.prepareForOutput(width, height); // KLUDGE, resize all components for TimeSeriesBrowse
            
            logger.log(Level.FINE, "plot uri {0}", suri);
            
            plot(suri);
            
            logger.log(Level.FINE, "done plot {0}", suri);
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
                    writeToPng( outfile, width, height );
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
