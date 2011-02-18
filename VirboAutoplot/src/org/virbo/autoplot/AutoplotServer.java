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
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import org.das2.dataset.AverageTableRebinner;
import org.das2.dataset.RebinDescriptor;
import org.das2.dataset.TableDataSet;
import org.das2.dataset.TableDataSetAdapter;
import org.das2.dataset.TableUtil;
import org.das2.dataset.VectorDataSet;
import org.das2.dataset.VectorDataSetAdapter;
import org.das2.dataset.VectorUtil;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.graph.DasCanvas;
import static org.virbo.autoplot.ScriptContext.*;

import org.das2.util.ArgumentList;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.AbstractProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.autoplot.dom.Application;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;
import org.virbo.qstream.SimpleStreamFormatter;

/**
 * Provide simple services to support command-line servers.
 * This has the following uses:
 *   1. image server to convert Autoplot URIs into images (U. Michigan)
 *   2. data server for (U. Iowa P.W. Group) converts URIs into streams of data
 *        (qstream or older das2stream).
 * 
 * @author jbf
 */
public class AutoplotServer {

    public AutoplotServer() {
    }

    public static void main(String[] args) throws Exception {

        System.err.println("org.virbo.autoplot.AutoplotServer " + APSplash.getVersion() + " 20110216_1700");

        ArgumentList alm = new ArgumentList("AutoplotServer");
        alm.addBooleanSwitchArgument("foo", "x", "foo", "test test");
        alm.addOptionalSwitchArgument("uri", "u", "uri", "", "URI to plot");
        alm.addOptionalSwitchArgument("vap", "v", "vap", "", "VAP to plot");
        alm.addOptionalSwitchArgument("width", "w", "width", "-1", "width of result (dflt=700)");
        alm.addOptionalSwitchArgument("height", "h", "height", "-1", "height of result (dflt=400)");
        alm.addOptionalSwitchArgument("canvas.aspect", "a", "canvas.aspect", "", "aspect ratio");
        alm.addOptionalSwitchArgument("format", "f", "format", "png", "output format png, pdf, qds, d2s (dflt=png)");
        alm.addOptionalSwitchArgument("outfile", "o", "outfile", "-", "output filename or -");
        alm.addOptionalSwitchArgument("timeRange", "t", "timeRange", "", "timerange for TimeSeriesBrowse datasources");
        alm.addOptionalSwitchArgument("resolution", "r", "resolution", "", "resolution requirement for timerange request");
        alm.addOptionalSwitchArgument("cache", "c", "cache", "", "location where files are downloaded, default is $HOME/autoplot_data/cache");

        alm.requireOneOf(new String[]{"uri", "vap"});
        alm.process(args);

        String suri = alm.getValue("uri");
        String vap = alm.getValue("vap");

        String timeRange = alm.getValue("timeRange");
        //String timeRange="2010-01-04";
        String resolution = alm.getValue("resolution");

        //initialize the application
        Application dom = getDocumentModel();

        String cache = alm.getValue("cache");
        if (!cache.equals("")) {
            File fcache = new File(cache);
            if (!fcache.mkdirs()) {
                System.err.println("unable to make dirs for cache=" + fcache);
            }
            if (!fcache.canWrite()) {
                System.err.println("unable to write to cache=" + fcache);
            }

            File ff= new File( fcache, "testCache.empty" );
            FileOutputStream fo= new FileOutputStream( ff );
            fo.write( "AutoplotServer is able to write a file\n".getBytes() );
            fo.close();

            FileSystem.settings().setLocalCacheDir(new File(cache));
            System.err.println("using cache dir " + FileSystem.settings().getLocalCacheDir());
        } else {
            System.err.println("using default cache dir " + FileSystem.settings().getLocalCacheDir() );
        }

        if (suri.equals("") && vap.equals("")) {
            alm.printUsage();
            System.err.println("Either uri or vap must be specified.");
            System.exit(-1);
        }

        int width = Integer.parseInt(alm.getValue("width"));
        int height = Integer.parseInt(alm.getValue("height"));
        String scanvasAspect = alm.getValue("canvas.aspect");
        String format = alm.getValue("format");
        String outfile = alm.getValue("outfile");

        if (outfile.endsWith(".pdf")) {
            format = "pdf";
        } else if (outfile.endsWith(".qds")) {
            format = "qds";
        } else if (outfile.endsWith(".d2s")) {
            format = "d2s";
        }

        // do dimensions
        if ("".equals(scanvasAspect)) {
            if (width == -1) {
                width = 700;
            }
            if (height == -1) {
                height = 400;
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

        if (vap == null) {
            dom.getController().getCanvas().setWidth(width);
            dom.getController().getCanvas().setHeight(height);
            DasCanvas c = dom.getController().getCanvas().getController().getDasCanvas();
            c.prepareForOutput(width, height); // KLUDGE, resize all components for TimeSeriesBrowse
        }

        Application model = getDocumentModel();

        QDataSet ds = null;

        if (!vap.equals("")) {
            load(vap);
            DasCanvas c = dom.getController().getCanvas().getController().getDasCanvas();
            c.prepareForOutput(width, height); // KLUDGE, resize all components for TimeSeriesBrowse
        } else {
            if ( format.equals("qds") || format.equals("d2s") ) {
                ProgressMonitor mon= new NullProgressMonitor();

                final PrintStream out= outfile.equals("-") ? System.out : System.out;
                mon= new AbstractProgressMonitor() {
                    @Override
                    public void setTaskProgress(long position) throws IllegalArgumentException {
                        String msg= String.format( "[xx]000060<comment type=\"taskProgress\" value=\"%08d\" source=\"\" />\n", position );
                        out.print( msg );
                    }
                    @Override
                    public void setTaskSize(long taskSize) {
                        String msg2= String.format( "[00]000058<stream><properties int:taskSize=\"%08d\" /></stream>\n", taskSize );
                        out.print( msg2 );
                    }
                };

                if (!timeRange.equals("")) {
                    System.err.println("org.virbo.jythonsupport.Util.getDataSet( suri,timeRange, new NullProgressMonitor() ):");
                    System.err.printf("   suri=%s\n", suri);
                    System.err.printf("   timeRange=%s\n", timeRange);

                    ds = org.virbo.jythonsupport.Util.getDataSet(suri, timeRange, mon );
                } else {
                    System.err.println("org.virbo.jythonsupport.Util.getDataSet( suri ):");
                    System.err.printf("   suri=%s\n", suri);
                    ds = org.virbo.jythonsupport.Util.getDataSet(suri,mon);
                }

                System.err.println("loaded ds=" + ds);
                if (!resolution.equals("")) {
                    if (ds == null) {
                        throw new IllegalArgumentException("ds is null, resolution is only usable with qds mode");
                    }
                    QDataSet dep0 = (QDataSet) ds.property(QDataSet.DEPEND_0);
                    Datum res = Units.seconds.parse(resolution);

                    AverageTableRebinner rebin = new AverageTableRebinner();
                    DatumRange tr;
                    if (timeRange.equals("")) {
                        tr = DataSetUtil.asDatumRange(Ops.extent(dep0), true);
                    } else {
                        tr = DatumRangeUtil.parseTimeRange(timeRange);
                    }
                    int nbin = (int) Math.ceil(tr.width().divide(res).doubleValue(Units.dimensionless));
                    RebinDescriptor ddx = new RebinDescriptor(tr.min(), tr.max(), nbin, false);
                    ds = rebin.rebin(ds, ddx, null);
                }

            } else {
                if (!timeRange.equals("")) {
                    System.err.println("timeRange parameter has no effect in this mode (format=" + format + ")");
                }
                plot(suri);
            }
        }

        if (format.equals("png")) {
            if (outfile.equals("-")) {
                model.getCanvases(0).setWidth(width);
                model.getCanvases(0).setHeight(height);
                writeToPng(System.out);
            } else {
                writeToPng(outfile, width, height);
            }
        } else if (format.equals("pdf")) {
            if (outfile.equals("-")) {
                model.getCanvases(0).setWidth(width);
                model.getCanvases(0).setHeight(height);
                writeToPdf(System.out);
            } else {
                model.getCanvases(0).setWidth(width);
                model.getCanvases(0).setHeight(height);
                writeToPdf(outfile);
            }
        } else if (format.equals("d2s")) {
            if (ds == null) {
                if (outfile.equals("-")) {
                    System.out.printf("[00]%6.6i<exception message='%s'/>\n", outfile.length() + 32, outfile);
                } else {
                    FileOutputStream fout = new FileOutputStream(outfile);
                    fout.write(String.format("[00]%6.6i<exception message='%s'/>\n", outfile.length() + 32, outfile).getBytes());
                }
            } else {
                if (outfile.equals("-")) {
                    System.err.println("formatting das2stream to System.out");
                    formatD2S( ds, System.out );
                } else {
                    System.err.println("formatting das2stream to file " + outfile);
                    FileOutputStream fout = new FileOutputStream(outfile);
                    formatD2S( ds, fout );
                }
            }
        } else if (format.equals("qds")) {
            if (ds == null) {
                if (outfile.equals("-")) {
                    System.out.print(":00:000000<exception/>TODO: format this\n");
                } else {
                    FileOutputStream fout = new FileOutputStream(outfile);
                    fout.write(":00:000000<exception/>TODO: format this\n".getBytes());
                }
            } else {
                if (outfile.equals("-")) {
                    System.err.println("formatting qstream to System.out");
                    new SimpleStreamFormatter().format(ds, System.out, true);
                } else {
                    System.err.println("formatting qstream to file " + outfile);
                    FileOutputStream fout = new FileOutputStream(outfile);
                    new SimpleStreamFormatter().format(ds, fout, true);
                }
            }
        }

        System.exit(0);

    }

    private static void formatD2S(QDataSet data, OutputStream fo) {
        boolean binary = false;
        if (data.rank() == 3) {
            TableDataSet tds = TableDataSetAdapter.create(data);
            if (binary) {
                TableUtil.dumpToBinaryStream(tds, fo);
            } else {
                TableUtil.dumpToAsciiStream(tds, fo);
            }
        } else if (data.rank() == 2) {
            TableDataSet tds = TableDataSetAdapter.create(data);
            if (binary) {
                TableUtil.dumpToBinaryStream(tds, fo);
            } else {
                TableUtil.dumpToAsciiStream(tds, fo);
            }
        } else if (data.rank() == 1) {
            VectorDataSet vds = VectorDataSetAdapter.create(data);
            if (binary) {
                VectorUtil.dumpToBinaryStream(vds, fo);
            } else {
                VectorUtil.dumpToAsciiStream(vds, fo);
            }
        }
    }
}
