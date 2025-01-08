
package org.autoplot.jythonsupport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.dataset.NoDataInIntervalException;
import org.das2.datum.TimeParser;
import org.das2.fsm.FileStorageModel;
import org.das2.system.RequestProcessor;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.Glob;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.aggregator.AggregatingDataSourceFactory;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.das2.qds.WritableDataSet;
import org.das2.qds.examples.Schemes;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.datasource.DataSetURI;
import static org.autoplot.datasource.DataSetURI.fromUri;
import static org.autoplot.datasource.DataSetURI.getFile;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.das2.qds.ops.Ops;
import org.das2.util.filesystem.FileObject;
import org.das2.util.filesystem.FileSystemUtil;
import org.das2.util.monitor.AlertNullProgressMonitor;
import org.das2.util.monitor.CancelledOperationException;
import org.python.core.Py;
import org.python.core.PyFunction;

/**
 * Utilities for Jython scripts in both the datasource and application contexts.
 * @author jbf
 */
public class Util {

    private static final Logger logger= LoggerManager.getLogger("jython.script");
    private static final Logger dslogger= LoggerManager.getLogger("jython.script.ds");
    
    /**
     * this returns a double indicating the current scripting version, found
     * at the top of autoplot2023.py in AUTOPLOT_DATA/jython/autoplot2023.py.  Do
     * not parse this number and expect it to work in future versions!
     * @return the version, such as v1.50.
     * @throws IOException 
     */
    public static String getAutoplotScriptingVersion() throws IOException {
        File ff2= new File( AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA ) );
        File ff3= new File( ff2.toString() + "/jython" );
        File ff4= new File( ff3, "autoplot2023.py" );
        String vers= null;
        
        Pattern versPattern= Pattern.compile("# autoplot2023.py v([\\d\\.]+) .*");  // must be parsable as a double.
                    
        if ( ff4.exists() ) {
            try (BufferedReader r = new BufferedReader( new FileReader( ff4 ) )) {
                String line= r.readLine();
                if ( line!=null ) {
                    Matcher m= versPattern.matcher(line);
                    if ( m.matches() ) {
                        vers= m.group(1);
                    }
                }
            }
        }
        if ( vers==null ) {
            throw new IllegalArgumentException("unable to get the scripting version");
        }
        return "v"+vers;
    }
    
    /**
     * throw an exception if the scripting version cannot be supported.  These
     * versions are numeric--so note that v1.7 is newer than v1.50, and for this
     * reason versions should always be vNNN.NN.
     * @param v 
     * @throws IllegalArgumentException 
     */
    public static void requireAutoplotScriptingVersion(String v) {
        Pattern p= Pattern.compile("v(\\d+)\\.(\\d\\d)");
        Matcher m= p.matcher(v);
        if ( m.matches() ) {
            try {
                int major= Integer.parseInt( m.group(1) );
                int minor= Integer.parseInt( m.group(2) );
                String current= getAutoplotScriptingVersion();
                Matcher m2= p.matcher(current);
                if ( m2.matches() ) {
                    if ( Integer.parseInt(m2.group(1))<major ) {
                        throw new IllegalArgumentException("Autoplot scripting version not supported: "+v+", current is "+current);
                    } else {
                        if ( Integer.parseInt(m2.group(1))<minor ) {
                            throw new IllegalArgumentException("Autoplot scripting version not supported: "+v+", current is "+current);
                        }
                    }
                } else {
                    throw new IllegalArgumentException("Autoplot scripting version not supported: "+v+", current is "+current);
                }
            } catch (IOException ex) {
                throw new IllegalArgumentException("unable to resolve scripting version number supported by Autoplot.",ex);
            }
        } else {
            throw new IllegalArgumentException("invalid version number, which must be vN.NN");
        }
    }

    
    /**
     * load the data specified by URI into Autoplot's internal data model.  This will
     * block until the load is complete, and a ProgressMonitor object can be used to
     * monitor the load.
     *
     * This adds a timeRange parameter so that TimeSeriesBrowse-capable datasources
     * can be used from AutoplotServer.
     *
     * @param suri the URI of the dataset, such as "http://autoplot.org/data/2010_061_17_41_40.txt?column=field8"
     * @param stimeRange a string representing the timerange to load, such as 2012-02-02/2012-02-03
     * @param mon progress monitor object.
     * @return QDataSet from the load.
     * @throws java.lang.Exception plug-in readers can throw exception.
     */
    public static QDataSet getDataSet( String suri, String stimeRange, ProgressMonitor mon ) throws Exception {
        if ( stimeRange==null ) {
            throw new IllegalArgumentException("stimeRange cannot be null");
        }
        DatumRange timeRange= DatumRangeUtil.parseTimeRange(stimeRange);
        return getDataSet( suri, timeRange, mon );
    }

    /**
     * return a dataset that we know is writable.  If the dataset is Writable and isImmutable is false, then 
     * the dataset is returned.
     * @param rds any dataset.
     * @return a writable dataset that is either the original one 
     */
    private static WritableDataSet ensureWritable( QDataSet rds ) {
        if ( rds instanceof WritableDataSet && (((WritableDataSet)rds).isImmutable()==false) ) {
            return ((WritableDataSet)rds);
        } else {
            return Ops.copy(rds);
        }
    }
    
    /**
     * load the data specified by URI into Autoplot's internal data model.  This will
     * block until the load is complete, and a ProgressMonitor object can be used to
     * monitor the load.
     *
     * This adds a timeRange parameter so that TimeSeriesBrowse-capable datasources
     * can be used from AutoplotServer.
     *
     * @param suri the URI of the dataset, such as "http://autoplot.org/data/2010_061_17_41_40.txt?column=field8"
     * @param timeRange null or the timerange to load, if the data supports time series browsing.
     * @param monitor progress monitor object, or null (None in Jython).
     * @return null or the dataset for the URI.
     * @throws java.lang.Exception plug-in readers can throw exception.
     */
    public static QDataSet getDataSet( String suri, DatumRange timeRange, ProgressMonitor monitor ) throws Exception {
        long t0= System.currentTimeMillis();
        dslogger.log( Level.FINE, "getDataSet(\"{0}\",DatumRangeUtil.parseTimeRange({1}),monitor)", new Object[]{suri, timeRange} );
        URI uri = DataSetURI.getURI(suri);
        DataSourceFactory factory = DataSetURI.getDataSourceFactory(uri, new NullProgressMonitor());
        if ( factory==null ) {
            throw new IllegalArgumentException("Unable to identify data source to handle URI: "+suri );
        }
        DataSource result = factory.getDataSource( uri );
        if (monitor == null) {
            monitor = new NullProgressMonitor();
        }
        
        TimeSeriesBrowse tsb= result.getCapability( TimeSeriesBrowse.class );
        if ( tsb==null ) {
            tsb= factory.getCapability( TimeSeriesBrowse.class );
        }
        
        if ( tsb!=null && timeRange!=null ) {
            tsb.setTimeRange( timeRange );
        } else {
            logger.fine("TimeSeriesBrowse capability not found, simply returning dataset.");
        }
        QDataSet rds= result.getDataSet(monitor);

        try {
            metadata= result.getMetadata( new NullProgressMonitor() );
        } catch ( Exception e ) {
            logger.log( Level.INFO, e.getMessage(), e );
        }
        metadataSurl= suri;

        if ( logger.isLoggable( Level.FINER ) ) {
            logger.finer( String.format(  Locale.US, "read in %9.2f sec: ", (System.currentTimeMillis()-t0)/1000. ) );
            logger.finer( String.format( "  uri: %s", suri ) );
            logger.finer( String.format( "  ds: %s", String.valueOf(rds) ) );
            if ( logger.isLoggable( Level.FINEST ) ) {
                if ( rds!=null ) {
                    QDataSet xds= SemanticOps.xtagsDataSet(rds);
                    QDataSet xextent= Ops.extent(xds);
                    QDataSet yextent= Ops.extent(rds);
                    logger.finest( String.format( "  extent x: %s y: %s", String.valueOf(xextent), String.valueOf(yextent) ) );
                } else {
                }
            }
        }
        
        if ( rds==null && factory instanceof AggregatingDataSourceFactory ) {
            logger.info("strange condition where occasional null is returned because of reference caching.  This needs to be studied more.");
            monitor = new NullProgressMonitor();
            monitor.setLabel("strange condition where occasional null...");
            rds= result.getDataSet(monitor);  //TODO nasty kludge, just try reading again...
        }
        
        if ( rds==null ) return null;

        if ( tsb!=null ) {
            if ( !Schemes.isTimeSeries(rds) && timeRange!=null ) {
                logger.fine("trim data to timerange");
                rds= DataSourceUtil.trimScatterToTimeRange( rds, timeRange );
            }
        }
        
        rds= ensureWritable(rds);
        return rds;
    }
    
    /**
     * load the data specified by URL into Autoplot's internal data model.  This will
     * block until the load is complete, and a ProgressMonitor object can be used to
     * monitor the load.
     * @param suri the URI of the dataset, such as "http://autoplot.org/data/2010_061_17_41_40.txt?column=field8"
     * @param mon a progress monitor to monitor the load, or null (None in Jython)
     * @return the dataset, or null.
     * @throws java.lang.Exception plug-in readers can throw exception.
     */
    public static QDataSet getDataSet(String suri, ProgressMonitor mon) throws Exception {
        long t0= System.currentTimeMillis();
        dslogger.log( Level.FINE, "getDataSet(\"{0}\",monitor)", suri );
        URI uri = DataSetURI.getURIValid(suri);
        DataSourceFactory factory = DataSetURI.getDataSourceFactory(uri, new NullProgressMonitor()); //TODO: NullProgressMonitor
        if ( factory==null ) throw new IllegalArgumentException("unsupported extension: "+suri);
        DataSource result = factory.getDataSource( uri );
        if (mon == null) {
            mon = new NullProgressMonitor();
        }
        QDataSet rds= result.getDataSet(mon);
        if ( !mon.isFinished() ) {
            if ( !mon.isStarted() ) mon.started();
            mon.finished();
        }

        try {
            metadata= result.getMetadata( new NullProgressMonitor() );
        } catch ( Exception e ) {
            logger.log( Level.INFO, e.getMessage(), e );
        }
        metadataSurl= suri;

        if ( logger.isLoggable( Level.FINER ) ) {
            logger.finer( String.format( Locale.US,  "read in %9.2f sec: ", (System.currentTimeMillis()-t0)/1000. ) );
            logger.finer( String.format( "  uri: %s", suri ) );
            logger.finer( String.format( "  ds: %s", String.valueOf(rds) ) );
            if ( logger.isLoggable( Level.FINEST ) ) {
                if ( rds!=null ) {
                    QDataSet xds= SemanticOps.xtagsDataSet(rds);
                    QDataSet xextent= Ops.extent(xds);
                    QDataSet yextent= Ops.extent(rds);
                    logger.finest( String.format( "  extent x: %s y: %s", String.valueOf(xextent), String.valueOf(yextent) ) );
                } else {
                }
            }
        }

        if ( rds==null ) return null;
        
        TimeSeriesBrowse tsb= result.getCapability(TimeSeriesBrowse.class);
        if ( tsb!=null ) {
            if ( !Schemes.isTimeSeries(rds) ) {
                logger.fine("trim data to timerange");
                rds= DataSourceUtil.trimScatterToTimeRange( rds, tsb.getTimeRange() );
            }
        }

        rds= ensureWritable(rds);
        return rds;
        
    }

    /**
     * load multiple uris simultaneously.  This will read all the data
     * at once, returning all data or throwing one of the exceptions.
     *
     * @param uris a list of URI strings.
     * @param mon monitor for the aggregate load.  TODO: Each uri should given equal shares of the task.
     * @return list of loaded data
     * @throws Exception if any of the loads reports an exception
     */
    public static List<QDataSet> getDataSets( List<String> uris, ProgressMonitor mon ) throws Exception {
        return getDataSets( uris, null, mon );
    }
    
    /**
     * load multiple uris simultaneously.  This will read all the data
     * at once, returning all data or throwing one of the exceptions.
     *
     * @param uris a list of URI strings.
     * @param timerange the timerange to load, and the reader may return data from a longer interval.
     * @param mon monitor for the aggregate load.  TODO: Each uri should given equal shares of the task.
     * @return list of loaded data
     * @throws Exception if any of the loads reports an exception
     */
    public static List<QDataSet> getDataSets( List<String> uris, DatumRange timerange, ProgressMonitor mon ) throws Exception {
        if ( mon==null ) mon= new NullProgressMonitor();
        final ArrayList result= new ArrayList( uris.size() );
        final ProgressMonitor[] monitors= new ProgressMonitor[uris.size()];
        mon.setTaskSize(10*uris.size());
        mon.started();
        for ( int i=0; i<uris.size(); i++ ) {
            final String uri= uris.get(i);
            final int fi= i;
            result.add(fi,null);
            monitors[i]= new NullProgressMonitor();
            final ProgressMonitor thisProgressMonitor= monitors[i];
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    QDataSet ds;
                    try {
                        ds = getDataSet(uri,timerange,thisProgressMonitor);
                        if ( ds==null ) {
                            throw new NoDataInIntervalException("data returned was null");
                        } else {
                            result.set(fi,ds);
                        }
                    } catch (Exception ex) {
                        Logger.getLogger(Util.class.getName()).log(Level.SEVERE, null, ex);
                        result.set(fi,ex);
                    }
                }
            };
            RequestProcessor.invokeLater(run);
        }
        boolean blocking= true;
        while ( blocking ) {
            Thread.sleep(250);
            blocking= false;
            int taskProgress= 0;
            //System.err.println("===");
            for ( int i=0; i<uris.size(); i++ ) {
                //System.err.println( "" + monitors[i].getTaskProgress() +" / " + monitors[i].getTaskSize() );
                if ( monitors[i].getTaskSize()>0 ) {
                    taskProgress+= ( 10. * monitors[i].getTaskProgress() ) / monitors[i].getTaskSize();
                } else if ( monitors[i].isFinished() ) {
                    taskProgress+= 10;
                } else {
                    taskProgress+= 2;
                }
                if ( result.get(i)==null ) {
                    blocking= true;
                }
            }
            //System.err.println( "" + taskProgress +" / " + mon.getTaskSize() + " sum" );
            mon.setTaskProgress(taskProgress);
        }
        
        Exception e=null;
        for ( int i=0; i<uris.size(); i++ ) {
            if ( e==null && result.get(i) instanceof Exception ) {
                e= ((Exception)result.get(i));
            }
        }
        mon.finished();
        if ( e!=null ) {
            throw e;
        } else {
            return result;
        }
    }
    
    /**
     * run the python jobs in parallel.
     * @param job a python function which takes one argument
     * @param argument list of arguments to invoke.
     * @param mon monitor for the group of processes
     * @return list of results for each call of the function.
     * @throws java.lang.Exception if any of the jobs throw an exception.
     */
    public static List<Object> runInParallel( 
            final PyFunction job, 
            final List<Object> argument, 
            ProgressMonitor mon ) throws Exception {
        logger.entering("org.autoplot.jythonsupport.Util", "runInParallel");
        if ( mon==null ) mon= new NullProgressMonitor();
        
        final List<Callable<Object>> callables= new ArrayList<>(argument.size());
        final List<Object> result= new ArrayList<>(argument.size());
        final List<Exception> exceptions= new ArrayList<>(argument.size());
        
        mon.setTaskSize( argument.size()*100 );
        mon.started();
        
        for ( int i=0; i<argument.size(); i++ ) {
            final int I= i;
            result.add( I, null );
            exceptions.add( I,null );
            callables.add( I, new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    try {
                        Object result1= job.__call__( Py.java2py(argument.get(I)) );
                        result.set( I, result1 );
                        return result1;
                    } catch ( Exception e ) {
                        exceptions.set( I, e );
                        return null;
                    }
                }
            } );
        }
                    
        ExecutorService executor= Executors.newCachedThreadPool();
        List<Callable<Object>> tasks= callables;
        List<Future<Object>> futures= executor.invokeAll(tasks);
        
        int pendingJobs;
        do {
            pendingJobs= 0;
            boolean allDone= true;
            for ( int i=0; i<result.size(); i++ ) {
                Future f= futures.get(i);
                if ( !f.isDone() && !f.isCancelled() ) {
                    pendingJobs++;
                }
            }
            mon.setTaskProgress( (argument.size()-pendingJobs)*100 );
            if ( allDone ) break;
        } while ( pendingJobs>0 && !mon.isCancelled() );
        
        if ( mon.isCancelled() ) {
            throw new CancelledOperationException( "parallel task cancelled");
        }
        
        mon.finished();
        
        for ( int i=0; i<result.size(); i++ ) {
            if ( exceptions.get(i)!=null ) {
                logger.log( Level.WARNING, exceptions.get(i).getMessage(), exceptions.get(i) );
                logger.throwing( "org.autoplot.jythonsupport.Util", "runInParallel", exceptions.get(i) );
                throw exceptions.get(i);
            }
        }
        logger.exiting("org.autoplot.jythonsupport.Util", "runInParallel");
        return result;
    }
            
    /**
     * returns the dataSource for the given URI.  This will include capabilities, like TimeSeriesBrowse.
     * @param suri the data address to load.
     * @return the DataSource to load the URI.
     * @throws Exception
     */
    public static DataSource getDataSource( String suri ) throws Exception {
        logger.log( Level.FINE, "getDataSet({0})", suri );
        URI uri = DataSetURI.getURIValid(suri);
        DataSourceFactory factory = DataSetURI.getDataSourceFactory(uri, new NullProgressMonitor());
        DataSource result = factory.getDataSource( uri );
        return result;
    }
    
    /**
     * get the TimeSeriesBrowse capability, if available.  Null (None) is returned if it is not found.
     * @param ds the data source.
     * @return the TimeSeriesBrowse if available, or null (None)
     */
    public static TimeSeriesBrowse getTimeSeriesBrowse( DataSource ds ) {
        TimeSeriesBrowse tsb= ds.getCapability( TimeSeriesBrowse.class );
        return tsb;
    }

    // cache the last metadata url.
    private static Map<String, Object> metadata;
    private static String metadataSurl;


    /**
     * load the metadata for the url.  This can be called independently from getDataSet,
     * and data sources should not assume that getDataSet is called before getMetaData.
     * Some may, in which case a bug report should be submitted.
     * 
     * The metadata is a tree of name/value pairs, for human consumption, and
     * used when a particular metadata model is expects.
     * 
     * @param suri the data address to load.
     * @param mon monitor, or null (None in Jython) for no feedback.
     * @return metadata tree created by the data source.
     * @throws java.lang.Exception
     */
    public static Map<String, Object> getMetadata(String suri, ProgressMonitor mon) throws Exception {
        logger.log( Level.FINE, "getMetadata(\"{0}\",monitor)", suri );

        if (suri.equals(metadataSurl)) {
            return metadata;
        } else {
            URI url = DataSetURI.getURIValid(suri);
            DataSourceFactory factory = DataSetURI.getDataSourceFactory(url, new NullProgressMonitor());
            DataSource result = factory.getDataSource(url);
            if (mon == null) {
                mon = new NullProgressMonitor();
            }
            //result.getDataSet(mon);  some data sources may assume that getDataSet comes before getMetaData
            return result.getMetadata(mon);
        }
    }



    /**
     * load the data specified by URI into Autoplot's internal data model.  This will
     * block until the load is complete.
     * @param suri the URI of the dataset, such as "http://autoplot.org/data/2010_061_17_41_40.txt?column=field8"
     * @return null or dataset for the URI.
     * @throws Exception depending on data source.
     */
    public static QDataSet getDataSet(String suri) throws Exception {
        return getDataSet(suri, new NullProgressMonitor() );
    }

    /**
     * load the data specified by URI into Autoplot's internal data model.  This will
     * block until the load is complete.
     * @param suri the URI of the dataset, such as "http://autoplot.org/data/2010_061_17_41_40.txt?column=field8"
     * @param stimerange timerange like "2012-02-02/2012-02-03"
     * @return null or data set for the URI.
     * @throws Exception depending on data source.
     */
    public static QDataSet getDataSet(String suri, String stimerange ) throws Exception {
        return getDataSet(suri, stimerange, new NullProgressMonitor() );
    }

    /**
     * load the data specified by URI into Autoplot's internal data model.  This will
     * block until the load is complete.
     * @param suri the URI of the dataset, such as "http://autoplot.org/data/2010_061_17_41_40.txt?column=field8"
     * @param timerange timerange object
     * @return null or data set for the URI.
     * @throws Exception depending on data source.
     */    
    public static QDataSet getDataSet(String suri, DatumRange timerange ) throws Exception {
        return getDataSet(suri, timerange, new NullProgressMonitor() );
    }
    
    /**
     * load data from the input stream into Autoplot internal data model.  This
     * will block until the load is complete.  This works by creating a temporary
     * file and then using the correct reader to read the data.  When the data source
     * is able to read directly from a stream, no temporary file is created.  Currently
     * this always loads to a file, and therefore does not support applets.
     * 
     * This may have been introduced to support scripts, but it's not clear who uses it.
     *
     * @param spec the extension and any parsing parameters, such as "vap+bin:?recLength=2000&rank2=1:"
     * @param in the input stream
     * @param mon a progress monitor.
     * @return QDataSet the dataset or null.
     * @throws java.lang.Exception
     */
    public static QDataSet getDataSetFromStream( String spec, InputStream in, ProgressMonitor mon ) throws Exception {
        logger.log( Level.FINE, "getDataSet(\"{0}\",InputStream)", new Object[]{spec} );
        String[] ss= spec.split(":",-2);
        String ext;
        int i= ss[0].indexOf("+");
        ext= (i==-1) ? ss[0] : ss[0].substring(i+1);
        File f= File.createTempFile("autoplot", "."+ext );

        try (ReadableByteChannel chin = Channels.newChannel(in)) {
            try (FileOutputStream fout = new FileOutputStream(f)) {
                WritableByteChannel chout= fout.getChannel();
                DataSourceUtil.transfer(chin, chout);
            }

            String virtUrl= ss[0]+":"+ f.toURI().toString() + ss[1];
            QDataSet ds= getDataSet(virtUrl,mon);
            return ds;
            
        }
    }


    /**
     * returns an array of the files in the local or remote filesystem pointed to by suri.  The files are returned
     * without the path, and directories are marked with a trailing slash character.  Windows a forward 
     * slash is still used, even though a back slash is more conventional.  When the suri ends in slash, all 
     * entries in the directory are listed, and when it ends in a file glob, all matching files are returned.
     *
     * <p><blockquote><pre>
     * print listDirectory( 'http://autoplot.org/data/pngwalk/' )
     *  --> 'product.vap', 'product_20080101.png', 'product_20080102.png', ...
     * print listDirectory( 'http://autoplot.org/data/pngwalk/*.png' )
     *  --> 'product_20080101.png', 'product_20080102.png', ...
     * </pre></blockquote><p>
     * @param suri local or web directory.
     * @return an array of the files pointed to by surl.
     * @throws java.net.MalformedURLException
     * @throws java.net.URISyntaxException when surl is not well formed.
     * @throws java.io.IOException when listing cannot be done
     */
    public static String[] listDirectory(String suri) throws IOException, URISyntaxException {
        logger.log(Level.FINE, "listDirectory(\"{0}\")", suri);
        String[] ss = FileSystem.splitUrl(suri);
        FileSystem fs = FileSystem.create( DataSetURI.toUri(ss[2]));
        String glob = ss[3].substring(ss[2].length());
        String[] result;
        if (glob.length() == 0) {
            result = fs.listDirectory("/");
        } else {
            result = fs.listDirectory("/", Glob.getRegex(glob));
        }
        Arrays.sort(result);
        return result;
    }
    
        
    /**
     * return an array of URLs that match the spec for the time range provided.
     * For example,
     * <p><blockquote><pre>
     *  uri= 'https://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX'
     *  xx= getTimeRangesFor( uri, '2000-jan', '$Y-$d-$m' )
     *  for x in xx:
     *    print x
     * </pre></blockquote><p>
     *
     * @param surl an Autoplot uri with an aggregation specifier.
     * @param timeRange a string that is parsed to a time range, such as "2001"
     * @param format format for the result, such as "%Y-%m-%d"
     * @return a list of URLs without the aggregation specifier.
     * @throws java.io.IOException if the remote folder cannot be listed.
     * @throws java.text.ParseException if the timerange cannot be parsed.
     */
    public static String[] getTimeRangesFor(String surl, String timeRange, String format) throws IOException, ParseException {
        DatumRange dr = DatumRangeUtil.parseTimeRange(timeRange);
        FileStorageModel fsm = AggregatingDataSourceFactory.getFileStorageModel(surl);
        TimeParser tf = TimeParser.create(format);

        String[] ss = fsm.getNamesFor(dr);
        String[] result = new String[ss.length];

        for (int i = 0; i < ss.length; i++) {
            DatumRange dr2 = fsm.getRangeFor(ss[i]);
            result[i] = tf.format(dr2.min(), dr2.max());
        }

        return result;
    }
    
    /**
     * Given a spec to format timeranges and a range to contain each timerange,
     * produce a list of all timeranges covering the range formatted with the
     * spec.  For example, <code>generateTimeRanges( "%Y-%m-%d", "Jun 2009" )</code> would result in
     * 2009-06-01, 2009-06-02, ..., 2009-06-30.  This is limited to create no more than 
     * 100000 elements.
     * 
     * @param spec such as "%Y-%m".  Note specs like "%Y%m" will not be parsable.
     * @param srange range limiting the list, such as "2009"
     * @return a string array of formatted time ranges, such as [ "2009-01", "2009-02", ..., "2009-12" ]
     * @see DatumRangeUtil#parseTimeRangeValid(java.lang.String) to convert to DatumRange objects.
     * @throws java.text.ParseException of the outer range cannot be parsed.
     */
    public static String[] generateTimeRanges( String spec, String srange ) throws ParseException {
        TimeParser tp= TimeParser.create(spec);
        DatumRange range= DatumRangeUtil.parseTimeRange(srange);

        String sstart;
        try {
            sstart= tp.format( range.min(), null );
        } catch ( Exception ex ) { // orbit files have limited range
            DatumRange dr= tp.getValidRange();
            DatumRange dd= DatumRangeUtil.sloppyIntersection(range, dr);
            if ( dd.width().value()==0 ) {
                return new String[0]; // no intersection
            }
            sstart= tp.format( dd.min(), null );
        }

        tp.parse(sstart);
        DatumRange curr= tp.getTimeRange();
        
        if ( curr.width().value()==0 ) {
            throw new IllegalArgumentException("spec first interval width is 0., something has gone terribly wrong.");
        }
        
        int countLimit= 1000000;
        int approxCount= (int)( 1.01 * range.width().divide(curr.width()).value() ); // extra 1% for good measure.

        if ( approxCount>countLimit*1.03 ) {
            throw new IllegalArgumentException("too many intervals would be created, this is limited to about 1000000 intervals.");
        }
        
        List<String> result= new ArrayList<String>( approxCount );
        
        if ( !range.intersects(curr) ) { // Sebastian has a strange case that failed, see 
            curr= curr.next();
        }
        
        while ( range.intersects(curr) ) {
            String scurr= tp.format( curr.min(), curr.max() );
            result.add( scurr );
            DatumRange oldCurr= curr;
            curr= curr.next();
            if ( oldCurr.equals(curr) ) { // orbits return next() that is this at the ends.
                break;
            }
        }
        return result.toArray( new String[result.size()] );

    }
    
    /**
     * return true if the file exists.  
     * This is introduced to avoid imports of java.io.File.
     * @param file file or local file Autoplot URI
     * @return true if the file exists.
     */
    public static boolean fileExists( String file ) {
        file= file.trim();
        if ( file.startsWith("file:") ) {
            file= file.substring(5);
        } else if ( file.startsWith("http:") || file.startsWith("https:") || file.startsWith("ftp://") || file.startsWith("sftp://") ) {
            try {
                URI fileUri= new URI(file);
                URI parent= FileSystemUtil.getParentUri(fileUri);
                FileSystem fs= FileSystem.create(parent);
                FileObject fo= fs.getFileObject( parent.relativize(fileUri).getPath() );
                return fo.exists();
            } catch (URISyntaxException | FileSystem.FileSystemOfflineException | UnknownHostException | FileNotFoundException ex) {
                return false;
            }
        }
        return new File(file).exists();
    }
    
    /**
     * return true if the file can be read.
     * This is introduced to avoid imports of java.io.File.
     * @param file the file or directory.
     * @return true if the file can be read.
     */
    public static boolean fileCanRead( String file ) {
        if ( file.startsWith("file:") ) {
            file= file.substring(5);
        } else if ( file.startsWith("http:") || file.startsWith("https:") || file.startsWith("ftp://") || file.startsWith("sftp://") ) {
            try {
                URI fileUri= new URI(file);
                URI parent= FileSystemUtil.getParentUri(fileUri);
                FileSystem fs= FileSystem.create(parent);
                FileObject fo= fs.getFileObject( parent.relativize(fileUri).getPath() );
                return fo.exists();
            } catch (URISyntaxException | FileSystem.FileSystemOfflineException | UnknownHostException | FileNotFoundException ex) {
                return false;
            }
        }
        return new File(file).canRead();
    }
    
    /**
     * read the preferences into a map.  These are name=value pairs
     * and anything following a pound symbol (#) is ignored.  Anything
     * before the equal sign is trimmed, so "x=2" and "x = 2" have 
     * the same interpretation.
     * 
     * This has a number of TODOs, namely:<ul>
     * <li> allow quoted values, and hashes within quotes.
     * <li> allow defaults to be specified.
     * <li> allow ini files to be used as well.
     * <li> allow json files to be used as well.
     * </ul>
     * %{PWD} is replaced with the directory of the config file.
     * 
     * @param suri the location of files which are name value pairs.
     * @return a map of string to object.
     * @throws IOException 
     * @since Autoplot v2022a_1
     */
    public static Map<String,Object> readConfiguration( String suri ) throws IOException {
        Map<String,Object> result= new LinkedHashMap<>();
        URISplit split= URISplit.parse(suri);
        File f= getFile(suri,false,new AlertNullProgressMonitor("loading configuration"));
        try ( BufferedReader reader= new BufferedReader( new FileReader(f) ) ) {
            String line;
            while ( ( line = reader.readLine() ) !=null ) {
                int i= line.indexOf('#');
                if ( i>-1 ) line = line.substring(0,i);
                line = line.trim();
                if ( line.length()==0 ) continue;
                i= line.indexOf('=');
                String value= line.substring(i+1).trim();
                if ( value.contains("%{PWD}") ) {
                    value= value.replace("%{PWD}", split.path );
                }
                result.put( line.substring(0,i).trim(), value );
            }
        }
        return result;
    }

    /**
     * return a list of completions.  This is useful in the IDL context
     * as well as Jython scripts.  This will perform the completion for where the carot is
     * at the end of the string.  Only completions where maybePlot indicates the URI is now 
     * valid are returned, so for example http://autoplot.org/data/somedata.cdf?noDep is not
     * returned and http://autoplot.org/data/somedata.cdf?Magnitude is.
     * @param file for example http://autoplot.org/data/somedata.cdf?
     * @return list of completions, containing the entire URI.
     * @throws java.lang.Exception any exception thrown by the data source.
     */
    public static String[] getCompletions( String file ) throws Exception {
        List<DataSetURI.CompletionResult> cc= DataSetURI.getCompletions( file, file.length(), new NullProgressMonitor() );
        List<DataSetURI.CompletionResult> resultList= new ArrayList<>();
        for (DataSetURI.CompletionResult cc1 : cc) {
            if (cc1.maybePlot == true) {
                resultList.add(cc1);
            }
        }

        String[] result= new String[resultList.size()];
        for ( int i=0; i<resultList.size(); i++ ) {
            result[i]= resultList.get(i).completion;
        }

        return result;
    }

    /**
     * return a list of all completions, even if they are not complete.  
     * This is useful in the IDL context
     * as well as Jython scripts.  This will perform the completion for where the carot is
     * at the end of the string.  All completions are returned, so for example 
     * http://autoplot.org/data/somedata.cdf?noDep is returned as well as
     * http://autoplot.org/data/somedata.cdf?Magnitude.
     * @param file for example http://autoplot.org/data/somedata.cdf?
     * @return list of completions, containing the entire URI.
     * @throws java.lang.Exception any exception thrown by the data source.
     */
    public static String[] getAllCompletions( String file ) throws Exception {
        List<DataSetURI.CompletionResult> cc= DataSetURI.getCompletions( file, file.length(), new NullProgressMonitor() );
        List<DataSetURI.CompletionResult> resultList= new ArrayList<>();
        for (DataSetURI.CompletionResult cc1 : cc) {
            resultList.add(cc1);
        }

        String[] result= new String[resultList.size()];
        for ( int i=0; i<resultList.size(); i++ ) {
            result[i]= resultList.get(i).completion;
        }

        return result;
    }
    
    /**
     * sleep for so many milliseconds.  This is introduced to avoid the import,
     * which makes running scripts securely non-trivial.
     * @param millis number of milliseconds to pause execution
     */
    public static void sleep( int millis ) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * return true if we should do the imports as before, where all of Autoplot is
     * imported with each session.  This is used to ease migration.
     * @return true if the old behavior should be used.
     */
    public static boolean isLegacyImports() {
        return true;
    }
    
    /**
     * only split on the delimiter when we are not within the exclude delimiters.  For example,
     * <code>
     * x=getDataSet("http://autoplot.org/data/autoplot.cdf?Magnitude&noDep=T")&y=getDataSet('http://autoplot.org/data/autoplot.cdf?BGSEc&slice1=2')&sqrt(x)
     * </code>
     * @param s the string to split.
     * @param delim the delimiter to split on, for example the ampersand (&).
     * @param exclude1 for example the single quote (')
     * @param exclude2 for example the double quote (")  Note URIs don't support these anyway.
     * @return the split.
     * 
     * This is a copy of another code.
     */
    public static String[] guardedSplit( String s, char delim, char exclude1, char exclude2 ) {    
        if ( delim=='_') throw new IllegalArgumentException("_ not allowed for delim");
        StringBuilder scopyb= new StringBuilder(s.length());
        char inExclude= (char)0;
        
        for ( int i=0; i<s.length(); i++ ) {
            char c= s.charAt(i);
            if ( inExclude==0 ) {
                if ( c==exclude1 || c==exclude2 ) inExclude= c;
            } else {
                if ( c==inExclude ) inExclude= 0;
            }
            if ( inExclude>(char)0 ) c='_';
            scopyb.append(c);            
        }
        String[] ss= scopyb.toString().split(String.valueOf(delim),-2);
        
        int i1= 0;
        for ( int i=0; i<ss.length; i++ ) {
            int i2= i1+ss[i].length();
            ss[i]= s.substring(i1,i2);
            i1= i2+1;
        } 
        return ss;
    }

    public static String popString( String line ) {
        boolean doubleQuotes= line.indexOf("\"")==0 && line.lastIndexOf("\"")==line.length()-1;
        boolean singleQuotes;
        if ( !doubleQuotes ) {
            singleQuotes= line.indexOf("'")==0 && line.lastIndexOf("'")==line.length()-1;
        } else {
            singleQuotes= false;
        }
        if ( line.length()>1 && ( doubleQuotes || singleQuotes ) ) {
            return line.substring(1,line.length()-1);
        } else {
            return line;
        }
    }
    
    public static void main( String[] args ) throws Exception {
        DataSetURI.init();
        String uri= "file:///Users/jbf/data/rbsp-a_WFR-spectral-matrix_emfisis-Quick-Look_20120911_v1.2.6.cdf?BuBu[::40]";
        QDataSet ds= Util.getDataSet(uri);
        System.err.println(ds);
    }
}
