
package org.autoplot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
import org.das2.dataset.NoDataInIntervalException;
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
import static org.autoplot.ScriptContext.*;

import org.das2.util.ArgumentList;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.AbstractProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.SubTaskMonitor;
import org.das2.qds.DataSetOps;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFormat;
import org.autoplot.datasource.DataSourceRegistry;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.das2.qds.ops.Ops;
import org.das2.qstream.SimpleStreamFormatter;
import org.das2.util.FileUtil;

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
    private static final String FORM_HAPI_INFO = "hapi-info";
    private static final String FORM_HAPI_DATA = "hapi-data"; // deprecated
    private static final String FORM_HAPI_CSV = "hapi-csv";
    private static final String FORM_HAPI_DATA_BINARY = "hapi-data-binary"; // deprecated
    private static final String FORM_HAPI_BINARY = "hapi-binary";
    
    private static final Logger logger= LoggerManager.getLogger("autoplot.server");

    /**
     * Perform the data service.
     * @param timeRange the time range to send out, such as "May 2003", or "" for none, or null for none.
     * @param suri the data source to read in.  If this has TimeSeriesBrowse, then we can stream the data.
     * @param step step size, such as "24 hr" or "3600s".  If the URI contains $H, "3600s" is used.
     * @param stream if true, send data out as it is read.
     * @param format FORM_QDS, FORM_D2S, FORM_HAPI
     * @param mon progress monitor to monitor the stream.
     * @param out stream which receives the data.
     * @param ascii if true, use ascii types for qstreams and das2streams.
     * @param outEmpty for the streaming library, so we don't put progress out until we've output the initial header.
     * @throws Exception 
     */
    public static void doService( String timeRange, String suri, String step, boolean stream, String format, final PrintStream out, boolean ascii, Set outEmpty, ProgressMonitor mon ) throws Exception {
        
        long t0= System.currentTimeMillis();

        QDataSet ds;

        boolean someValid= false;

        boolean trimTimes= format.equals(FORM_HAPI_BINARY) || format.equals(FORM_HAPI_CSV) || format.equals(FORM_HAPI_DATA) || format.equals(FORM_HAPI_DATA_BINARY);
        
        if ( timeRange==null ) timeRange="";

        // peek to see if there is a timeRange within the URI, and make this equivalent to the case where timerange is specified.
        if ( timeRange.length()==0 ) {
            DataSource dss1= DataSetURI.getDataSource(suri);
            TimeSeriesBrowse tsb1= dss1.getCapability(TimeSeriesBrowse.class); // Note some Jyds scripts allow TSB to be present after the load.
            if ( tsb1!=null ) {
                timeRange= tsb1.getTimeRange().toString();
            }
        }
            
        if ( !timeRange.equals("")) {
            logger.fine("org.autoplot.jythonsupport.Util.getDataSet( suri,timeRange ):");
            logger.log(Level.FINE, "   suri={0}", suri);
            logger.log(Level.FINE, "   timeRange={0}", timeRange);

            DatumRange outer= DatumRangeUtil.parseTimeRange(timeRange);
            
            // see if there's a "native" step size to be aware of.  There's no way to do this the existing TSB capability, so we kludge for it to support RBSP at U. Iowa.
            DataSource dss= DataSetURI.getDataSource(suri);
            TimeSeriesBrowse tsb= dss.getCapability(TimeSeriesBrowse.class);
            if ( tsb!=null && suri.contains("$H") ) {
                step= "3600s";
            }
            
            Datum first= TimeUtil.prevMidnight( outer.min() );
            Datum next= first.add( Units.seconds.parse(step) );

            List<DatumRange> drs;
            if ( stream && ( format.equals(FORM_D2S) || format.equals(FORM_QDS) || format.equals(FORM_HAPI_DATA) || format.equals(FORM_HAPI_CSV) || format.equals(FORM_HAPI_DATA_BINARY)) ) {
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
                QDataSet ds1= null;
                try {
                    
                    ds1= org.autoplot.jythonsupport.Util.getDataSet(suri, dr.toString(), SubTaskMonitor.create( mon, i*10, (i+1)*10 ) );
                    
                } catch ( NoDataInIntervalException ex ) {
                    logger.log( Level.FINE, "no data trying to read "+dr, ex ); 
                    
                } catch ( FileNotFoundException ex ) {
                    logger.log( Level.FINE, "no files found trying to read "+dr, ex ); 
                    
                } catch ( Exception ex ) {
                    logger.log( Level.WARNING, "exception when trying to read "+dr, ex ); 
                }
                
                if ( ds1!=null && trimTimes ) {                                    
                    ds1= Ops.trim( ds1, outer );
                }
                
                logger.log( Level.FINE, "  --> {0} )", ds1 );
                if ( ds1!=null ) {
                    if ( !SemanticOps.isTimeSeries(ds1) ) { //automatically fall back to -nostream
                        logger.fine( String.format( "dataset doesn't appear to be a time series, reloading everything" ) );
                        ds1 = org.autoplot.jythonsupport.Util.getDataSet(suri, outer.toString(), SubTaskMonitor.create( mon, i*10, (i+1)*10 ) );
                        logger.log( Level.FINE, "  --> {0} )", ds1 );
                        writeData(format, out, ds1, ascii, stream );
                        someValid= true;
                        break;
                    }
                    if ( ds1.rank()==1 ) {
                        QDataSet xrange= Ops.extent( SemanticOps.xtagsDataSet(ds1) );
                        logger.log(Level.FINE, "loaded ds={0}  bounds: {1}", new Object[]{ds1, xrange});
                        logger.log( Level.FINE, "time at read done read of {0}= {1}\n", new Object[]{ dr.toString(), System.currentTimeMillis()-t0 } );
                    } else if ( ds1.rank()==2 || ds1.rank()==3 ) {
                        QDataSet range= DataSetOps.dependBounds( ds1 );
                        logger.log(Level.FINE, "loaded ds={0}  bounds: {1}", new Object[]{ds1, range});
                        logger.log( Level.FINE, "time at read done read of {0}= {1}\n", new Object[]{ dr.toString(), System.currentTimeMillis()-t0 } );
                    }
                    
                    writeData(format, out, ds1, ascii, stream );
                    
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
            logger.fine("org.autoplot.jythonsupport.Util.getDataSet( suri ):");
            logger.log( Level.FINE, "   suri={0}\n", suri );

            ds = org.autoplot.jythonsupport.Util.getDataSet(suri,mon);
            logger.log(Level.FINE, "loaded ds={0}", ds);
            
            if ( ds!=null ) {
                writeData(format, out, ds, false, false );
                someValid= true;
            }
        }

        logger.log( Level.FINE, "time to read (ms): {0}", System.currentTimeMillis()-t0 );

        if ( !someValid ) {
            switch (format) {
                case FORM_D2S:
                    {
                        String s;
                        if ( !stream ) {
                            s= "<stream><properties int:taskSize=\"00000010\" /></stream>";
                            out.printf( String.format( "[00]%06d%s", s.length(), s ) ); 
                        }
                        s= String.format( "<exception type=\"NoDataInInterval\" message='%s'/>\n", "no data found in "+timeRange );
                        out.printf( String.format( "[xx]%06d%s", s.length(), s ) );
                        break;
                    }
                case FORM_QDS:
                    {
                        String s= String.format( "<exception type=\"NoDataInInterval\" message='%s'/>\n", "no data found in "+timeRange );
                        out.printf( String.format( "[xx]%06d%s", s.length(), s ) );
                        break;
                    }
            }
        }
    }

    public AutoplotDataServer() {
        throw new IllegalArgumentException("AutoplotDataServer should not be instantiated");
    }

    private static void formatD2S( QDataSet data, OutputStream fo, boolean ascii, boolean stream ) {
        boolean binary = !ascii;
        switch (data.rank()) {
            case 3: {
                    TableDataSet tds = TableDataSetAdapter.create(data);
                    if (binary) {
                        TableUtil.dumpToDas2Stream( tds, Channels.newChannel(fo), false, !stream );
                    } else {
                        TableUtil.dumpToDas2Stream( tds, Channels.newChannel(fo), true, !stream );
                    }       
                    break;
                }
            case 2: {
                    TableDataSet tds = TableDataSetAdapter.create(data);
                    if (binary) {
                        TableUtil.dumpToDas2Stream( tds, Channels.newChannel(fo), false, !stream );
                    } else {
                        TableUtil.dumpToDas2Stream( tds, Channels.newChannel(fo), true, !stream );
                    }
                    break;
                }
            case 1:
                VectorDataSet vds = VectorDataSetAdapter.create(data);
                if (binary) {
                    VectorUtil.dumpToDas2Stream( vds, Channels.newChannel(fo), false, !stream );
                } else {
                    VectorUtil.dumpToDas2Stream( vds, Channels.newChannel(fo), true, !stream );
                }   
                break;
            default:
                break;
        }
    }

    private static void writeData( String format, OutputStream out, QDataSet ds, boolean ascii, boolean stream) throws Exception {
        switch (format) {
            case FORM_D2S:
                formatD2S( ds, out, ascii, stream );
                break;
            case FORM_QDS:
                if ( ds.property( QDataSet.DEPEND_1 )!=null && ds.property( QDataSet.BUNDLE_1 )!=null ) {
                    logger.info("dropping BUNDLE_1 when DEPEND_1 is present");
                    ds= Ops.maybeCopy(ds);
                    ((MutablePropertyDataSet)ds).putProperty(QDataSet.BUNDLE_1,null);
                }   new SimpleStreamFormatter().format(ds, out, ascii );
                break;
            case FORM_HAPI_INFO:
                {
                    final DataSourceFormat dsf = DataSourceRegistry.getInstance().getFormatByExt("hapi");
                    int irand= (int)( Math.round( Math.random() * 100000000 ) );
                    String n= String.format( "/tmp/ap-hapi/ads%09d", irand );
                    File file= new File( n+".hapi");
                    dsf.formatData( file.toString()+"?id=temp", ds, new NullProgressMonitor() );
                    File infoFile= new File( n+"/hapi/info/temp.json" );
                    FileInputStream fin= new FileInputStream(infoFile);
                    DataSourceUtil.transfer( fin, out, false );
                    FileUtil.deleteFileTree( new File(n) );
                    break;
                }
            case FORM_HAPI_DATA_BINARY:
            case FORM_HAPI_BINARY:
                {
                    final DataSourceFormat dsf = DataSourceRegistry.getInstance().getFormatByExt("hapi");
                    int irand= (int)( Math.round( Math.random() * 100000000 ) );
                    String n= String.format( "/tmp/ap-hapi/ads%09d", irand );
                    File file= new File( n+".hapi");
                    dsf.formatData( file.toString()+"?id=temp&format=binary", ds, new NullProgressMonitor() );
                    File binaryFile= new File(  n+"/hapi/data/temp.binary" );
                    FileInputStream fin= new FileInputStream(binaryFile);
                    DataSourceUtil.transfer( fin, out, false );
                    FileUtil.deleteFileTree( new File(n) );
                    break;
                }
            case FORM_HAPI_DATA:
            case FORM_HAPI_CSV:
                {
                    final DataSourceFormat dsf = DataSourceRegistry.getInstance().getFormatByExt("hapi");
                    int irand= (int)( Math.round( Math.random() * 100000000 ) );
                    String n= String.format( "/tmp/ap-hapi/ads%09d", irand );
                    File file= new File( n+".hapi");
                    dsf.formatData( file.toString()+"?id=temp", ds, new NullProgressMonitor() );
                    File csvFile= new File( n+"/hapi/data/temp.csv" );
                    FileInputStream fin= new FileInputStream(csvFile);
                    DataSourceUtil.transfer( fin, out, false );
                    FileUtil.deleteFileTree( new File(n) );
                    break;
                }
            case "dat":
            case "xls":
            case "bin":
                {
                    File file= File.createTempFile("autoplotDataServer", "."+format );
                    formatDataSet( ds, file.toString() );
                    FileInputStream fin= new FileInputStream(file);
                    DataSourceUtil.transfer( fin, out, false );
                    break;
                }
            default:
                throw new IllegalAccessException("bad format: "+format );
        }
    }


    public static class D2SMonitor extends AbstractProgressMonitor {
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
            if ( outEmpty.isEmpty() ) return;
            out.print( msg );
        }
    }

    /**
     * put a comment onto the stream no more often then once per second.
     */
    public static class QStreamMonitor extends AbstractProgressMonitor {
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

        System.setProperty("java.awt.headless","true");
        
        ArgumentList alm = new ArgumentList("AutoplotDataServer");
        alm.addOptionalSwitchArgument("uri", "u", "uri", "", "URI to plot");
        alm.addOptionalSwitchArgument("format", "f", "format", "", "output format qds, d2s (default=d2s if no filename) which support streaming, or xls bin dat hapi-info hapi-csv hapi-binary");
        alm.addOptionalSwitchArgument("outfile", "o", "outfile", DEFT_OUTFILE, "output filename or -, extension implies format.");
        alm.addOptionalSwitchArgument("timeRange", "t", "timeRange", "", "timerange for TimeSeriesBrowse datasources");
        alm.addOptionalSwitchArgument("timeStep", "s", "timeStep", "86400s", "atom step size for loading and sending, default is 86400s");
        alm.addOptionalSwitchArgument("cache", "c", "cache", "", "location where files are downloaded, default is $HOME/autoplot_data/cache");
        alm.addBooleanSwitchArgument( "nostream", "", "nostream","disable streaming, as with Bill's dataset which is X and Y table");
        alm.addBooleanSwitchArgument( "ascii", "a", "ascii", "request that ascii streams be sent instead of binary.");
        alm.addBooleanSwitchArgument( "noexit", "z", "noexit", "don't exit after running, for use with scripts." );
        alm.addBooleanSwitchArgument( "quiet", "q", "quiet", "don't print anything besides warning messages to stderr." );
        alm.addBooleanSwitchArgument( "enableResponseMonitor", null, "enableResponseMonitor", "monitor the event thread for long unresponsive pauses");        

        alm.requireOneOf(new String[]{"uri"});
        if ( !alm.process(args) ) {
            System.exit( alm.getExitCode() );
        }

        if ( alm.getBooleanValue("quiet") ) {
            // don't print anything.
        } else {
            System.err.println("org.autoplot.AutoplotDataServer 20160309 (Autoplot version " + APSplash.getVersion() + ")" );
        }
        
        alm.logPrefsSettings( logger );

        String suri = alm.getValue("uri");
        if ( suri.startsWith("'") && suri.endsWith("'") ) {
            suri= suri.substring( 1, suri.length()-1 );
        }

        String timeRange = alm.getValue("timeRange");

        String step = alm.getValue("timeStep");

        boolean ascii= alm.getBooleanValue("ascii");

        boolean stream= ! alm.getBooleanValue("nostream");

        
        //AutoplotUtil.maybeLoadSystemProperties();
        //if ( System.getProperty("enableResponseMonitor","false").equals("true")
        //                    || alm.getBooleanValue("enableResponseMonitor") ) {
        if ( alm.getBooleanValue("enableResponseMonitor") ) {
            EventThreadResponseMonitor emon= new EventThreadResponseMonitor();
            emon.start();
        }
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
            try (FileOutputStream fo = new FileOutputStream( ff )) {
                fo.write( "AutoplotDataServer is able to write a file\n".getBytes() );
            }

            FileSystem.settings().setLocalCacheDir(new File(cache));
            logger.log(Level.FINE, "using cache dir {0}", FileSystem.settings().getLocalCacheDir());
        } else {
            logger.log(Level.FINE, "using default cache dir {0}", FileSystem.settings().getLocalCacheDir());
        }

        if (suri.equals("")) {
            alm.printUsage();
            logger.fine("uri must be specified.");
            if ( !alm.getBooleanValue("noexit") ) System.exit(-1); else return;
        }

        String format = alm.getValue("format");
        String outfile = alm.getValue("outfile");

        if ( format.length()>0 && !format.startsWith("hapi") && !outfile.equals(DEFT_OUTFILE) ) {
            if ( !outfile.endsWith(format) ) {
                System.err.println("format="+format+" doesn't match outfile extension. outfile="+outfile );
                if ( !alm.getBooleanValue("noexit") ) System.exit(-2); else return;
            }
        }
        if (outfile.endsWith(".qds")) {
            format = FORM_QDS;
        } else if (outfile.endsWith(".d2s")) {
            format = FORM_D2S;
        } else if ( outfile.contains(".") ) {
            URISplit split= URISplit.parse(outfile);
            if ( !format.startsWith("hapi") ) {
                format= split.ext;
                if ( format==null ) {
                    split= URISplit.parse("file:///"+outfile);
                    format= split.ext;
                }
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
        Set outEmpty= new HashSet<>(); // nasty kludge to prevent logger from writing first.  This is a bug: qstreams at least should support this.

        if ( outfile.equals(DEFT_OUTFILE) ) {
             out= System.out;
        } else {
             out = new PrintStream(outfile);
        }

        if ( format.equals(FORM_D2S) && stream ) {
            mon= new D2SMonitor(out,outEmpty);
        } else if ( format.equals(FORM_QDS) && stream ) {
            mon= new QStreamMonitor(out,outEmpty);
        } else {
            logger.fine("no progress available because output is not d2s stream");
        }
        
        doService( timeRange, suri, step, stream, format,out, ascii, outEmpty, mon );
        out.close();
        
        if ( !alm.getBooleanValue("noexit") ) System.exit(0);

    }

}
