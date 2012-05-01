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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.channels.Channels;
import java.util.List;
import org.das2.dataset.TableDataSet;
import org.das2.dataset.TableDataSetAdapter;
import org.das2.dataset.TableUtil;
import org.das2.dataset.VectorDataSet;
import org.das2.dataset.VectorDataSetAdapter;
import org.das2.dataset.VectorUtil;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import static org.virbo.autoplot.ScriptContext.*;

import org.das2.util.ArgumentList;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.AbstractProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.SubTaskMonitor;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.DataSourceUtil;
import org.virbo.datasource.URISplit;
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
public class AutoplotDataServer {

    public AutoplotDataServer() {
    }

    private static void formatD2S(QDataSet data, OutputStream fo) {
        boolean binary = true;
        if (data.rank() == 3) {
            TableDataSet tds = TableDataSetAdapter.create(data);
            if (binary) {
                TableUtil.dumpToDas2Stream( tds, Channels.newChannel(fo), false, false );
            } else {
                TableUtil.dumpToDas2Stream( tds, Channels.newChannel(fo), true, false );
            }
        } else if (data.rank() == 2) {
            TableDataSet tds = TableDataSetAdapter.create(data);
            if (binary) {
                TableUtil.dumpToDas2Stream( tds, Channels.newChannel(fo), false, false );
            } else {
                TableUtil.dumpToDas2Stream( tds, Channels.newChannel(fo), true, false );
            }
        } else if (data.rank() == 1) {
            VectorDataSet vds = VectorDataSetAdapter.create(data);
            if (binary) {
                VectorUtil.dumpToDas2Stream( vds, Channels.newChannel(fo), false, false );
            } else {
                VectorUtil.dumpToDas2Stream( vds, Channels.newChannel(fo), true, false );
            }
        }
    }

    private static void writeData( String format, OutputStream out, QDataSet ds ) throws Exception {
        if ( format.equals("d2s") ) {
            formatD2S( ds, out );
        } else if ( format.equals("qds") ) {
            new SimpleStreamFormatter().format(ds, out, true );
        } else if ( format.equals("dat") || format.equals("xls") || format.equals("bin") ) {
            File file= File.createTempFile( "autoplotDataServer", "."+format );
            formatDataSet( ds, file.toString() );
            FileInputStream fin= new FileInputStream(file);
            DataSourceUtil.transfer( fin, out );
        } else {
            throw new IllegalAccessException("bad format");
        }
    }


    public static void main(String[] args) throws Exception {

        long t0= System.currentTimeMillis();

        System.err.println("org.virbo.autoplot.AutoplotDataServer " + APSplash.getVersion() + " 20110902");

        ArgumentList alm = new ArgumentList("AutoplotServer");
        alm.addBooleanSwitchArgument("foo", "x", "foo", "test test");
        alm.addOptionalSwitchArgument("uri", "u", "uri", "", "URI to plot");
        alm.addOptionalSwitchArgument("format", "f", "format", "d2s", "output format qds, d2s (dflt=d2s)");
        alm.addOptionalSwitchArgument("outfile", "o", "outfile", "-", "output filename or -");
        alm.addOptionalSwitchArgument("timeRange", "t", "timeRange", "", "timerange for TimeSeriesBrowse datasources");
        alm.addOptionalSwitchArgument("cache", "c", "cache", "", "location where files are downloaded, default is $HOME/autoplot_data/cache");

        alm.requireOneOf(new String[]{"uri"});
        alm.process(args);

        String suri = alm.getValue("uri");

        String timeRange = alm.getValue("timeRange");

        String step = "86400 s";

        //initialize the application.  We don't use the object, but this
        //will allow us to reset the cache position.
        getDocumentModel();

        // set up the cache.
        String cache = alm.getValue("cache");
        if (!cache.equals("")) {
            File fcache = new File(cache);
            if ( !fcache.exists() && !fcache.mkdirs()) {
                System.err.println("unable to make dirs for cache=" + fcache);
            }
            if (!fcache.canWrite()) {
                System.err.println("unable to write to cache=" + fcache);
            }

            File ff= new File( fcache, "testCache.empty" );
            FileOutputStream fo= new FileOutputStream( ff );
            fo.write( "AutoplotDataServer is able to write a file\n".getBytes() );
            fo.close();

            FileSystem.settings().setLocalCacheDir(new File(cache));
            System.err.println("using cache dir " + FileSystem.settings().getLocalCacheDir());
        } else {
            System.err.println("using default cache dir " + FileSystem.settings().getLocalCacheDir() );
        }

        if (suri.equals("")) {
            alm.printUsage();
            System.err.println("uri must be specified.");
            System.exit(-1);
        }

        String format = alm.getValue("format");
        String outfile = alm.getValue("outfile");

        if (outfile.endsWith(".qds")) {
            format = "qds";
        } else if (outfile.endsWith(".d2s")) {
            format = "d2s";
        } else if ( outfile.contains(".") ) {
            URISplit split= URISplit.parse(outfile);
            format= split.ext;
            if ( format==null ) {
                split= URISplit.parse("file:///"+outfile);
                format= split.ext;
            }
        }

        if ( format.startsWith(".") ) {
            format= format.substring(1);
        }

        ProgressMonitor mon= new NullProgressMonitor();

        final PrintStream out;

        if ( outfile.equals("-") ) {
             out= System.out;
        } else {
             out = new PrintStream(outfile);
        }

        mon= new AbstractProgressMonitor() {
            long lastUpdateTime= -1;
            public void setTaskSize(long taskSize) {
                String msg2= String.format( "[00]000056<stream><properties int:taskSize=\"%08d\" /></stream>\n", taskSize );
                out.print( msg2 );
            }
            public void setTaskProgress(long position) throws IllegalArgumentException {
                long tnow= System.currentTimeMillis();
                if ( this.getTaskProgress()==position && ( tnow-lastUpdateTime < 10000 ) ) return;
                lastUpdateTime= tnow;
                super.setTaskProgress(position);
                String msg= String.format(  "[xx]000059<comment type=\"taskProgress\" value=\"%08d\" source=\"\" />\n", position );
                out.print( msg );
            }
        };

        if ( !format.equals("d2s") ) {
            System.err.println("no progress available because output is not d2s stream");
            mon= new NullProgressMonitor();
        }

        QDataSet ds = null;

        boolean someValid= false;

        System.err.println( "time read args and prep=" + (( System.currentTimeMillis()-t0 ) ) );

        if (!timeRange.equals("")) {
            System.err.println("org.virbo.jythonsupport.Util.getDataSet( suri,timeRange, new NullProgressMonitor() ):");
            System.err.printf("   suri=%s\n", suri);
            System.err.printf("   timeRange=%s\n", timeRange);

            DatumRange outer= DatumRangeUtil.parseTimeRange(timeRange);

            Datum first= TimeUtil.prevMidnight( outer.min() );
            Datum next= first.add( Units.seconds.parse(step) );

            List<DatumRange> drs= DatumRangeUtil.generateList( outer, new DatumRange( first, next ) );

            int i=0;
            mon.setTaskSize(10*drs.size());

            mon.setTaskProgress( 5 );
            for ( DatumRange dr: drs ) {
                System.err.printf( "time read start read of %s= %d\n", dr.toString(), System.currentTimeMillis()-t0 );

                //make sure URIs with time series browse have a timerange in the URI.  Otherwise we often crash on the above line...
                //TODO: find a way to test for this and give a good error message.
                System.err.println( String.format( "getDataSet('%s','%s')", suri, dr ) );
                QDataSet ds1 = org.virbo.jythonsupport.Util.getDataSet(suri, dr.toString(), SubTaskMonitor.create( mon, i*10, (i+1)*10 ) );
                if ( ds1!=null ) {
                    if ( ds1.rank()==1 ) {
                        QDataSet xrange= Ops.extent( SemanticOps.xtagsDataSet(ds1) );
                        System.err.println("loaded ds="+ds1 + "  bounds: "+xrange );
                        System.err.printf( "time read done read of %s= %d\n", dr.toString(), System.currentTimeMillis()-t0 );
                    } else if ( ds1.rank()==2 || ds1.rank()==3 ) {
                        QDataSet range= DataSetOps.dependBounds( ds1 );
                        System.err.println("loaded ds="+ds1 + "  bounds: "+range );
                        System.err.printf( "time read done read of %s= %d\n", dr.toString(), System.currentTimeMillis()-t0 );
                    }
                    writeData( format, out, ds1 );
                    someValid= true;
                }
                i++;
                mon.setTaskProgress(i*10);
                System.err.printf( "time write to output channel %s= %d\n", dr.toString(), System.currentTimeMillis()-t0 );

            }
            mon.finished();

        } else {
            System.err.println("org.virbo.jythonsupport.Util.getDataSet( suri ):");
            System.err.printf("   suri=%s\n", suri);

            ds = org.virbo.jythonsupport.Util.getDataSet(suri,mon);
            System.err.println("loaded ds="+ds );
            
            if ( ds!=null ) {
                writeData( format, out, ds );
                someValid= true;
            }
        }

        System.err.printf( "time done read all= %d\n", System.currentTimeMillis()-t0 );

        if ( !someValid ) {
             if ( format.equals("d2s") ) {
                 System.out.printf("[00]%06d<exception message='%s'/>\n", outfile.length() + 32, "no data found" );
             }
        }

        System.exit(0);

    }

}
