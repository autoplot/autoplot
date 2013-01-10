
package org.virbo.autoplot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.channels.Channels;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.AbstractProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.SubTaskMonitor;
import org.virbo.dataset.ArrayDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.DataSourceUtil;
import org.virbo.datasource.URISplit;
import org.virbo.dsops.Ops;
import org.virbo.qstream.SimpleStreamFormatter;

/**
 * Data server for U. Iowa P.W. Group converts URIs into streams of data.  These would typically
 * be qstream or older das2stream format, but the code (apparently) supports .xls, .dat, and .bin as well.
 *
 * See also AutoplotServer, which serves images.
 * @author jbf
 */
public class AutoplotDataServer {
    private static final String DEFT_OUTFILE = "-";
    private static final String FORM_D2S = "d2s";
    private static final String FORM_QDS = "qds";

    private static final Logger logger= LoggerManager.getLogger("autoplot.server");

    public AutoplotDataServer() {
        throw new IllegalArgumentException("AutoplotDataServer should not be instantiated");
    }

    private static void formatD2S( QDataSet data, OutputStream fo, boolean ascii) {
        boolean binary = !ascii;
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

    private static void writeData( String format, OutputStream out, QDataSet ds, boolean ascii ) throws Exception {
        if ( format.equals(FORM_D2S) ) {
            formatD2S( ds, out, ascii );
        } else if ( format.equals(FORM_QDS) ) {
            if ( ds.property( QDataSet.DEPEND_1 )!=null && ds.property( QDataSet.BUNDLE_1 )!=null ) {
                logger.info("dropping BUNDLE_1 when DEPEND_1 is present");
                ds= ArrayDataSet.maybeCopy(ds);
                ((ArrayDataSet)ds).putProperty(QDataSet.BUNDLE_1,null);
            }
            new SimpleStreamFormatter().format(ds, out, ascii );
        } else if ( format.equals("dat") || format.equals("xls") || format.equals("bin") ) {
            File file= File.createTempFile( "autoplotDataServer", "."+format );
            formatDataSet( ds, file.toString() );
            FileInputStream fin= new FileInputStream(file);
            DataSourceUtil.transfer( fin, out );
        } else {
            throw new IllegalAccessException("bad format");
        }
    }


    private static class D2SMonitor extends AbstractProgressMonitor {
        PrintStream out;
        Set outEmpty;
        D2SMonitor( OutputStream out, Set outEmpty ) {
            this.out= new PrintStream(out);
            this.outEmpty= outEmpty;
        }
        long lastUpdateTime= -1;
        @Override
        public void setTaskSize(long taskSize) {
            String msg2= String.format( "[00]000056<stream><properties int:taskSize=\"%08d\" /></stream>\n", taskSize );
            out.print( msg2 );
        }
        @Override
        public void setTaskProgress(long position) throws IllegalArgumentException {
            long tnow= System.currentTimeMillis();
            if ( this.getTaskProgress()==position && ( tnow-lastUpdateTime < 10000 ) ) return;
            lastUpdateTime= tnow;
            super.setTaskProgress(position);
            String msg= String.format(  "[xx]000059<comment type=\"taskProgress\" value=\"%08d\" source=\"\" />\n", position );
            out.print( msg );
        }
    }

    /**
     * put a comment onto the stream no more often then once per second.
     */
    private static class QStreamMonitor extends AbstractProgressMonitor {
        PrintStream out;
        Set outEmpty; // if this is empty then out is empty.

        QStreamMonitor( OutputStream out, Set outEmpty ) {
            this.out= new PrintStream(out);
            this.outEmpty= outEmpty;
        }
        long lastUpdateTime= -1;
        @Override
        public void setTaskSize(long taskSize) {
            super.setTaskSize(taskSize);
        }
        @Override
        public void setTaskProgress(long position) throws IllegalArgumentException {
            long tnow= System.currentTimeMillis();
            super.setTaskProgress(position);
            if ( this.getTaskProgress()==position && ( tnow-lastUpdateTime < 1000 ) ) return;
            lastUpdateTime= tnow;
            //TODO: check that \n on windows doesn't put out 10-13.
            String comment= String.format( "<comment type='taskProgress' message='%d of %d'/>\n", getTaskProgress(), getTaskSize() );
            String msg= String.format(  "[xx]%06d%s", comment.length(), comment );
            if ( outEmpty.isEmpty() ) return;
            out.print( msg );
        }
    }

    public static void main(String[] args) throws Exception {

        long t0= System.currentTimeMillis();

        System.err.println("org.virbo.autoplot.AutoplotDataServer 20120922 " + APSplash.getVersion() );

        ArgumentList alm = new ArgumentList("AutoplotDataServer");
        alm.addOptionalSwitchArgument("uri", "u", "uri", "", "URI to plot");
        alm.addOptionalSwitchArgument("format", "f", "format", "", "output format qds, d2s (default=d2s if no filename)");
        alm.addOptionalSwitchArgument("outfile", "o", "outfile", DEFT_OUTFILE, "output filename or -");
        alm.addOptionalSwitchArgument("timeRange", "t", "timeRange", "", "timerange for TimeSeriesBrowse datasources");
        alm.addOptionalSwitchArgument("timeStep", "s", "timeStep", "86400s", "atom step size for loading and sending, default is 86400s");
        alm.addOptionalSwitchArgument("cache", "c", "cache", "", "location where files are downloaded, default is $HOME/autoplot_data/cache");
        alm.addBooleanSwitchArgument("nostream",  "", "nostream","disable streaming, as will Bill's dataset which is X and Y table");
        alm.addBooleanSwitchArgument( "ascii", "a", "ascii", "request that ascii streams be sent instead of binary.");

        alm.requireOneOf(new String[]{"uri"});
        alm.process(args);

        alm.logPrefsSettings( logger );

        String suri = alm.getValue("uri");

        String timeRange = alm.getValue("timeRange");

        String step = alm.getValue("timeStep");

        boolean ascii= alm.getBooleanValue("ascii");

        boolean stream= ! alm.getBooleanValue("nostream");

        //initialize the application.  We don't use the object, but this
        //will allow us to reset the cache position.
        getDocumentModel();

        // set up the cache.
        String cache = alm.getValue("cache");
        if (!cache.equals("")) {
            File fcache = new File(cache);
            if ( !fcache.exists() && !fcache.mkdirs()) {
                logger.log(Level.FINE, "unable to make dirs for cache={0}", fcache);
            }
            if (!fcache.canWrite()) {
                logger.log(Level.FINE, "unable to write to cache={0}", fcache);
            }

            File ff= new File( fcache, "testCache.empty" );
            FileOutputStream fo= new FileOutputStream( ff );
            fo.write( "AutoplotDataServer is able to write a file\n".getBytes() );
            fo.close();

            FileSystem.settings().setLocalCacheDir(new File(cache));
            logger.log(Level.FINE, "using cache dir {0}", FileSystem.settings().getLocalCacheDir());
        } else {
            logger.log(Level.FINE, "using default cache dir {0}", FileSystem.settings().getLocalCacheDir());
        }

        if (suri.equals("")) {
            alm.printUsage();
            logger.fine("uri must be specified.");
            System.exit(-1);
        }

        String format = alm.getValue("format");
        String outfile = alm.getValue("outfile");

        if ( format.length()>0 && !outfile.equals(DEFT_OUTFILE) ) {
            if ( !outfile.endsWith(format) ) {
                System.err.println("format="+format+" doesn't match outfile extension. outfile="+outfile );
                System.exit(-2);
            }
        }
        if (outfile.endsWith(".qds")) {
            format = FORM_QDS;
        } else if (outfile.endsWith(".d2s")) {
            format = FORM_D2S;
        } else if ( outfile.contains(".") ) {
            URISplit split= URISplit.parse(outfile);
            format= split.ext;
            if ( format==null ) {
                split= URISplit.parse("file:///"+outfile);
                format= split.ext;
            }
        }
        if ( format.length()==0 ) { // implement default.
            format= FORM_D2S;
        }

        if ( format.startsWith(".") ) {
            format= format.substring(1);
        }

        ProgressMonitor mon= new NullProgressMonitor();

        final PrintStream out;
        Set outEmpty= new HashSet<Object>(); // nasty kludge to prevent logger from writing first.  This is a bug: qstreams at least should support this.

        if ( outfile.equals(DEFT_OUTFILE) ) {
             out= System.out;
        } else {
             out = new PrintStream(outfile);
        }

        if ( format.equals(FORM_D2S) ) {
            mon= new D2SMonitor(out,outEmpty);
        } else if ( format.equals(FORM_QDS) ) {
            mon= new QStreamMonitor(out,outEmpty);
        } else {
            logger.fine("no progress available because output is not d2s stream");
        }
        
        QDataSet ds = null;

        boolean someValid= false;

        logger.log( Level.FINE, "time read args and prep={0}", ((System.currentTimeMillis() - t0)));

        if (!timeRange.equals("")) {
            logger.fine("org.virbo.jythonsupport.Util.getDataSet( suri,timeRange, new NullProgressMonitor() ):");
            System.err.printf("   suri=%s\n", suri);
            System.err.printf("   timeRange=%s\n", timeRange);

            DatumRange outer= DatumRangeUtil.parseTimeRange(timeRange);

            Datum first= TimeUtil.prevMidnight( outer.min() );
            Datum next= first.add( Units.seconds.parse(step) );

            List<DatumRange> drs;
            if ( stream && ( format.equals(FORM_D2S) || format.equals(FORM_QDS) ) ) {
                drs= DatumRangeUtil.generateList( outer, new DatumRange( first, next ) );
            } else {
                // dat xls cannot stream...
                drs= Collections.singletonList(outer);
            }

            int i=0;
            mon.setTaskSize(10*drs.size());

            mon.setTaskProgress( 5 );
            for ( DatumRange dr: drs ) {
                logger.log( Level.FINER, "time at read start read of {0}= {1}", new Object[] { dr.toString(), System.currentTimeMillis()-t0 } );

                //make sure URIs with time series browse have a timerange in the URI.  Otherwise we often crash on the above line...
                //TODO: find a way to test for this and give a good error message.
                logger.fine( String.format( "getDataSet('%s','%s')", suri, dr ) );
                QDataSet ds1 = org.virbo.jythonsupport.Util.getDataSet(suri, dr.toString(), SubTaskMonitor.create( mon, i*10, (i+1)*10 ) );
                logger.log( Level.FINE, "  --> {0} )", ds1 );
                if ( ds1!=null ) {
                    if ( ds1.rank()==1 ) {
                        QDataSet xrange= Ops.extent( SemanticOps.xtagsDataSet(ds1) );
                        logger.log(Level.FINE, "loaded ds={0}  bounds: {1}", new Object[]{ds1, xrange});
                        logger.log( Level.FINE, "time at read done read of {0}= {1}\n", new Object[]{ dr.toString(), System.currentTimeMillis()-t0 } );
                    } else if ( ds1.rank()==2 || ds1.rank()==3 ) {
                        QDataSet range= DataSetOps.dependBounds( ds1 );
                        logger.log(Level.FINE, "loaded ds={0}  bounds: {1}", new Object[]{ds1, range});
                        logger.log( Level.FINE, "time at read done read of {0}= {1}\n", new Object[]{ dr.toString(), System.currentTimeMillis()-t0 } );
                    }
                    writeData( format, out, ds1, ascii );
                    outEmpty.add("out is no longer empty");
                    someValid= true;
                }
                i++;
                mon.setTaskProgress(i*10);
                logger.log( Level.FINER, "time at write to output channel {0}= {1}\n",  new Object[] { dr.toString(), System.currentTimeMillis()-t0 } );

            }
            mon.finished();

        } else {
            // TODO: consider the virtue of allowing this, where a timerange is not specified.
            logger.fine("org.virbo.jythonsupport.Util.getDataSet( suri ):");
            logger.log( Level.FINE, "   suri={0}\n", suri );

            ds = org.virbo.jythonsupport.Util.getDataSet(suri,mon);
            logger.log(Level.FINE, "loaded ds={0}", ds);
            
            if ( ds!=null ) {
                writeData( format, out, ds, false );
                someValid= true;
            }
        }

        logger.log( Level.FINE, "time done read all= {0}", System.currentTimeMillis()-t0 );

        if ( !someValid ) {
             if ( format.equals(FORM_D2S) ) {
                 out.printf("[00]%06d<exception message='%s'/>\n", outfile.length() + 32, "no data found" );
             }
        }

        System.exit(0);

    }

}
