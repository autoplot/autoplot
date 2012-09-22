
package org.virbo.autoplot;

import org.das2.datum.Units;
import org.das2.graph.DasCanvas;
import static org.virbo.autoplot.ScriptContext.*;

import org.das2.util.ArgumentList;
import org.virbo.autoplot.dom.Application;


/**
 * Server for producing images from Autoplot URIs, first requested by U. Michigan.
 * @author jbf
 */
public class AutoplotServer {
    
    public AutoplotServer() {
    }
        
    public static void main(String[] args) throws Exception {

        System.err.println("org.virbo.autoplot.AutoplotServer 20120922");

        ArgumentList alm= new ArgumentList("AutoplotServer");
        alm.addBooleanSwitchArgument("foo", "x", "foo", "test test");
        alm.addOptionalSwitchArgument("uri", "u", "uri", "", "URI to plot");
        alm.addOptionalSwitchArgument("vap", "v", "vap", "", "VAP to plot");
        alm.addOptionalSwitchArgument("width", "w", "width", "-1", "width of result (dflt=700)");
        alm.addOptionalSwitchArgument("height", "h", "height", "-1", "height of result (dflt=400)");
        alm.addOptionalSwitchArgument("canvas.aspect", "a", "canvas.aspect", "", "aspect ratio" );
        alm.addOptionalSwitchArgument("format", "f", "format", "png", "output format png or pdf (dflt=png)");
        alm.addOptionalSwitchArgument("outfile", "o", "outfile", "-", "output filename or -");
        alm.addBooleanSwitchArgument( "noexit", "z", "noexit", "don't exit after running, for use with scripts." );
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
        String scanvasAspect = alm.getValue("canvas.aspect");
        String format= alm.getValue("format");
        String outfile= alm.getValue("outfile");

        if ( outfile.endsWith(".pdf") ) format= "pdf";

        Application dom= getDocumentModel();
        
        // do dimensions
        if ("".equals(scanvasAspect)) {
            if (width == -1) width = 700;
            if (height == -1) height = 400;
        } else {
            double aspect = Units.dimensionless.parse(scanvasAspect).doubleValue(Units.dimensionless);
            if (width == -1 && height != -1)
                width = (int) (height * aspect);
            if (height == -1 && width != -1)
                height = (int) (width / aspect);
        }
        
        if ( vap!=null && !vap.equals("") ) {
            load(vap);
            DasCanvas c = dom.getController().getCanvas().getController().getDasCanvas();
            c.prepareForOutput(width, height); // KLUDGE, resize all components for TimeSeriesBrowse
        } else {
            dom.getController().getCanvas().setWidth(width);
            dom.getController().getCanvas().setHeight(height);
            DasCanvas c = dom.getController().getCanvas().getController().getDasCanvas();
            c.prepareForOutput(width, height); // KLUDGE, resize all components for TimeSeriesBrowse
            plot(suri);
        }

        Application model= getDocumentModel();

        if ( format.equals("png") ) {
            if ( outfile.equals("-") ) {
                model.getCanvases(0).setWidth(width);
                model.getCanvases(0).setHeight(height);
                writeToPng( System.out );
            } else {
                writeToPng( outfile, width, height );
            }
        } else if ( format.equals("pdf") ) {
            if ( outfile.equals("-") ) {
                model.getCanvases(0).setWidth(width);
                model.getCanvases(0).setHeight(height);
                writeToPdf( System.out );
            } else {
                model.getCanvases(0).setWidth(width);
                model.getCanvases(0).setHeight(height);
                writeToPdf( outfile );
            }
        }

        if ( !alm.getBooleanValue("noexit") ) {
            System.exit(0);
        }

    }
}
