/*
 * JythonLauncher.java
 *
 * Created on November 1, 2007, 3:04 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import java.io.File;
import java.io.FileOutputStream;
import org.das2.dataset.AverageTableRebinner;
import org.das2.dataset.RebinDescriptor;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.graph.DasCanvas;
import static org.virbo.autoplot.ScriptContext.*;

import org.das2.util.ArgumentList;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.autoplot.dom.Application;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;
import org.virbo.qstream.SimpleStreamFormatter;


/**
 * Provide simple services to support command-line servers.
 * This has the following uses:
 *   1. image server to convert Autoplot URIs into images (U. Michigan)
 *   2. data server for (U. Iowa P.W. Group)
 * 
 * @author jbf
 */
public class AutoplotServer {
    
    public AutoplotServer() {
    }
        
    public static void main(String[] args) throws Exception {

        System.err.println("org.virbo.autoplot.AutoplotServer "+APSplash.getVersion());

        ArgumentList alm= new ArgumentList("AutoplotServer");
        alm.addBooleanSwitchArgument("foo", "x", "foo", "test test");
        alm.addOptionalSwitchArgument("uri", "u", "uri", "", "URI to plot");
        alm.addOptionalSwitchArgument("vap", "v", "vap", "", "VAP to plot");
        alm.addOptionalSwitchArgument("width", "w", "width", "-1", "width of result (dflt=700)");
        alm.addOptionalSwitchArgument("height", "h", "height", "-1", "height of result (dflt=400)");
        alm.addOptionalSwitchArgument("canvas.aspect", "a", "canvas.aspect", "", "aspect ratio" );
        alm.addOptionalSwitchArgument("format", "f", "format", "png", "output format png or pdf or qds (dflt=png)");
        alm.addOptionalSwitchArgument("outfile", "o", "outfile", "-", "output filename or -");
        alm.addOptionalSwitchArgument("timeRange", "t", "timeRange", "", "timerange for TimeSeriesBrowse datasources" );
        alm.addOptionalSwitchArgument("resolution", "r", "resolution", "", "resolution requirement for timerange request");
        alm.addOptionalSwitchArgument("cache", "c", "cache", "", "location where files are downloaded, default is $HOME/autoplot_data/cache");
        
        alm.requireOneOf( new String[] { "uri", "vap" } );
        alm.process(args);

        String suri = alm.getValue("uri");
        String vap = alm.getValue("vap");

        String timeRange= alm.getValue("timeRange");
        //String timeRange="2010-01-04";
        String resolution= alm.getValue("resolution");

        String cache= alm.getValue("cache");
        if ( !cache.equals("") ) {
            File fcache= new File( cache );
            if ( !fcache.mkdirs() ) {
                System.err.println( "unable to make dirs for cache="+fcache );
            }
            if ( !fcache.canWrite() ) {
                System.err.println( "unable to write to cache="+fcache );
            }
            FileSystem.settings().setLocalCacheDir( new File( cache ) );
            System.err.println( "using cache dir "+FileSystem.settings().getLocalCacheDir() );
        }

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
        if ( outfile.endsWith(".qds") ) format= "qds";
        
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
        
        if (vap == null) {
            dom.getController().getCanvas().setWidth(width);
            dom.getController().getCanvas().setHeight(height);
            DasCanvas c = dom.getController().getCanvas().getController().getDasCanvas();
            c.prepareForOutput(width, height); // KLUDGE, resize all components for TimeSeriesBrowse
        }

        Application model= getDocumentModel();

        QDataSet ds=null;

        if ( !vap.equals("") ) {
            load(vap);
            DasCanvas c = dom.getController().getCanvas().getController().getDasCanvas();
            c.prepareForOutput(width, height); // KLUDGE, resize all components for TimeSeriesBrowse
        } else {
            if ( format.equals("qds") ) {
                if ( !timeRange.equals("") ) {
                    System.err.println( "org.virbo.jythonsupport.Util.getDataSet( suri,timeRange, new NullProgressMonitor() ):");
                    System.err.printf( "   suri=%s\n", suri );
                    System.err.printf( "   timeRange=%s\n", timeRange );

                    ds= org.virbo.jythonsupport.Util.getDataSet(suri,timeRange, new NullProgressMonitor() );
                } else {
                    System.err.println( "org.virbo.jythonsupport.Util.getDataSet( suri ):");
                    System.err.printf( "   suri=%s\n", suri );
                    ds= org.virbo.jythonsupport.Util.getDataSet(suri);
                }

                System.err.println("loaded ds=" + ds );
                if ( !resolution.equals("") ) {
                    if ( ds==null ) {
                        throw new IllegalArgumentException("ds is null, resolution is only usable with qds mode");
                    }
                    QDataSet dep0= (QDataSet) ds.property(QDataSet.DEPEND_0);
                    Datum res= Units.seconds.parse(resolution);

                    AverageTableRebinner rebin= new AverageTableRebinner();
                    DatumRange tr;
                    if ( timeRange.equals("") ) {
                        tr= DataSetUtil.asDatumRange( Ops.extent( dep0 ),true );
                    } else {
                        tr= DatumRangeUtil.parseTimeRange(timeRange);
                    }
                    int nbin= (int)Math.ceil( tr.width().divide(res).doubleValue( Units.dimensionless ) );
                    RebinDescriptor ddx= new RebinDescriptor( tr.min(), tr.max(), nbin, false );
                    ds= rebin.rebin( ds, ddx, null );
                }

            } else {
                if ( !timeRange.equals("") )  {
                    System.err.println("timeRange parameter has no effect in this mode (format="+format+")");
                }
                plot(suri);
            }
        }

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
        } else if ( format.equals("qds") ) {
            if ( ds==null ) {
                if ( outfile.equals("-") ) {
                    System.out.print( ":00:000000<exception/>TODO: format this\n");
                } else {
                    FileOutputStream fout= new FileOutputStream(outfile);
                    fout.write( ":00:000000<exception/>TODO: format this\n".getBytes());
                }
            } else {
                if ( outfile.equals("-") ) {
                    System.err.println("formatting stream to System.out");
                    new SimpleStreamFormatter().format( ds, System.out, true );
                } else {
                    System.err.println("formatting stream to file "+outfile);
                    FileOutputStream fout= new FileOutputStream(outfile);
                    new SimpleStreamFormatter().format( ds, fout, true );
                }
            }
        }

        System.exit(0);

    }
}
