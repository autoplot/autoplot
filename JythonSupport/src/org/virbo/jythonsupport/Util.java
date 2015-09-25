/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.jythonsupport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.dataset.NoDataInIntervalException;
import org.das2.datum.TimeParser;
import org.das2.fsm.FileStorageModel;
import org.das2.graph.DataLoader;
import org.das2.system.RequestProcessor;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.Glob;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.aggregator.AggregatingDataSourceFactory;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.JoinDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.dataset.WritableDataSet;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.DataSourceUtil;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.dsops.Ops;

/**
 * Utilities for Jython scripts in both the datasource and application contexts.
 * @author jbf
 */
public class Util {

    private static final Logger logger= LoggerManager.getLogger("jython.script");

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
     * @param timeRange the timerange to load, if the data supports time series browsing.
     * @param monitor progress monitor object.
     * @return dataset or null.
     * @throws java.lang.Exception plug-in readers can throw exception.
     */
    public static QDataSet getDataSet( String suri, DatumRange timeRange, ProgressMonitor monitor ) throws Exception {
        long t0= System.currentTimeMillis();
        logger.log( Level.FINE, "getDataSet(\"{0}\",DatumRangeUtil.parseTimeRange({1}),monitor)", new Object[]{suri, timeRange} );
        URI uri = DataSetURI.getURI(suri);
        DataSourceFactory factory = DataSetURI.getDataSourceFactory(uri, new NullProgressMonitor());
        DataSource result = factory.getDataSource( uri );
        if (monitor == null) {
            monitor = new NullProgressMonitor();
        }

        TimeSeriesBrowse tsb= result.getCapability( TimeSeriesBrowse.class );
        if ( tsb!=null ) {
            tsb.setTimeRange( timeRange );
        } else {
            logger.info("Warning: TimeSeriesBrowse capability not found, simply returning dataset.");
        }
        QDataSet rds= result.getDataSet(monitor);

        try {
            metadata= result.getMetadata( new NullProgressMonitor() );
        } catch ( Exception e ) {
            logger.log( Level.INFO, e.getMessage(), e );
        }
        metadataSurl= suri;

        if ( logger.isLoggable( Level.FINER ) ) {
            logger.finer( String.format( "read in %9.2f sec: ", (System.currentTimeMillis()-t0)/1000. ) );
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
        rds= ensureWritable(rds);
        return rds;
    }
    
    /**
     * load the data specified by URL into Autoplot's internal data model.  This will
     * block until the load is complete, and a ProgressMonitor object can be used to
     * monitor the load.
     * @param suri the data address to load.
     * @param mon null or a progress monitor to monitor the load
     * @return the dataset, or null.
     * @throws java.lang.Exception plug-in readers can throw exception.
     */
    public static QDataSet getDataSet(String suri, ProgressMonitor mon) throws Exception {
        long t0= System.currentTimeMillis();
        logger.log( Level.FINE, "getDataSet(\"{0}\",monitor)", suri );
        URI uri = DataSetURI.getURIValid(suri);
        DataSourceFactory factory = DataSetURI.getDataSourceFactory(uri, new NullProgressMonitor()); //TODO: NullProgressMonitor
        if ( factory==null ) throw new IllegalArgumentException("unsupported extension: "+suri);
        DataSource result = factory.getDataSource( uri );
        if (mon == null) {
            mon = new NullProgressMonitor();
        }
        QDataSet rds= result.getDataSet(mon);

        try {
            metadata= result.getMetadata( new NullProgressMonitor() );
        } catch ( Exception e ) {
            logger.log( Level.INFO, e.getMessage(), e );
        }
        metadataSurl= suri;

        if ( logger.isLoggable( Level.FINER ) ) {
            logger.finer( String.format( "read in %9.2f sec: ", (System.currentTimeMillis()-t0)/1000. ) );
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
        
        rds= ensureWritable(rds);
        return rds;
        
    }

    /**
     * experiment with multiple, simultaneous reads in Jython codes.  This will read all the data
     * at once, returning all data or throwing one of the exceptions.
     *
     * @param uris a list of URI strings.
     * @param mon monitor for the aggregate load.  Each uri is given equal shares of the task.
     * @return list of loaded data
     * @throws Exception if any of the loads reports an exception
     */
    public static List<QDataSet> getDataSets( List<String> uris, ProgressMonitor mon ) throws Exception {
        final ArrayList result= new ArrayList( uris.size() );
        for ( int i=0; i<uris.size(); i++ ) {
            final String uri= uris.get(i);
            final int fi= i;
            result.add(fi,null);
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    QDataSet ds;
                    try {
                        ds = getDataSet(uri);
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
            for ( int i=0; i<uris.size(); i++ ) {
                if ( result.get(i)==null ) {
                    blocking= true;
                }
            }
        }
        for ( int i=0; i<uris.size(); i++ ) {
            if ( result.get(i) instanceof Exception ) {
                throw ((Exception)result.get(i));
            }
        }
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
     * @param mon monitor 
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
     * load the data specified by URL into Autoplot's internal data model.  This will
     * block until the load is complete.
     * @param suri the data address to load.
     * @return data set for the URL.
     * @throws Exception depending on data source.
     */
    public static QDataSet getDataSet(String suri) throws Exception {
        return getDataSet(suri, new NullProgressMonitor() );
    }

    /**
     * load the data specified by URL into Autoplot's internal data model.  This will
     * block until the load is complete.
     * @param suri data URI like "http://autoplot.org/data/2010_061_17_41_40.txt?column=field8"
     * @param stimerange timerange like "2012-02-02/2012-02-03"
     * @return data set for the URI.
     * @throws Exception depending on data source.
     */
    public static QDataSet getDataSet(String suri, String stimerange ) throws Exception {
        return getDataSet(suri, stimerange, new NullProgressMonitor() );
    }

    /**
     * load the data specified by URL into Autoplot's internal data model.  This will
     * block until the load is complete.
     * @param suri data URI like "http://autoplot.org/data/2010_061_17_41_40.txt?column=field8"
     * @param timerange timerange object
     * @return data set for the URI.
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
    public static QDataSet getDataSet( String spec, InputStream in, ProgressMonitor mon ) throws Exception {
        logger.log( Level.FINE, "getDataSet(\"{0}\",InputStream)", new Object[]{spec} );
        String[] ss= spec.split(":",-2);
        String ext;
        int i= ss[0].indexOf("+");
        ext= (i==-1) ? ss[0] : ss[0].substring(i+1);
        File f= File.createTempFile("autoplot", "."+ext );

        ReadableByteChannel chin= Channels.newChannel(in);
        try {
            FileOutputStream fout= new FileOutputStream(f);    
            try {
                WritableByteChannel chout= fout.getChannel();
                DataSourceUtil.transfer(chin, chout);
            } finally {
                fout.close();
            }

            String virtUrl= ss[0]+":"+ f.toURI().toString() + ss[1];
            QDataSet ds= getDataSet(virtUrl,mon);
            return ds;
            
        } finally {
            chin.close();
        }
    }


    /**
     * returns an array of the files in the local or remote filesystem pointed to by surl.
     *
     * <p><blockquote><pre>
     * print listDirectory( 'http://autoplot.org/data/pngwalk/' )
     *  --> 'product.vap', 'product_20080101.png', 'product_20080102.png', ...
     * print listDirectory( 'http://autoplot.org/data/pngwalk/*.png' )
     *  --> 'product_20080101.png', 'product_20080102.png', ...
     * </pre></blockquote><p>
     * @param surl local or web directory.
     * @return an array of the files pointed to by surl.
     * @throws java.net.MalformedURLException
     * @throws java.net.URISyntaxException when surl is not well formed.
     * @throws java.io.IOException when listing cannot be done
     */
    public static String[] listDirectory(String surl) throws IOException, URISyntaxException {
        logger.log(Level.FINE, "listDirectory(\"{0}\")", surl);
        String[] ss = FileSystem.splitUrl(surl);
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
     *  uri= 'http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX'
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
     * return a list of completions.  This is useful in the IDL context
     * as well as Jython scripts.  This will perform the completion for where the carot is
     * at the end of the string.  Only completions where maybePlot indicates the URI is now 
     * valid are returned, so for example http://autoplot.org/data/somedata.cdf?noDep is not
     * returned and http://autoplot.org/data/somedata.cdf?Magnitude is.
     * @param file, for example http://autoplot.org/data/somedata.cdf?
     * @return list of completions, containing the entire URI.
     * @throws java.lang.Exception any exception thrown by the data source.
     */
    public static String[] getCompletions( String file ) throws Exception {
        List<DataSetURI.CompletionResult> cc= DataSetURI.getCompletions( file, file.length(), new NullProgressMonitor() );
        List<DataSetURI.CompletionResult> resultList= new ArrayList<DataSetURI.CompletionResult>();
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
    
    public static void main( String[] args ) throws Exception {
        DataSetURI.init();
        String uri= "file:///Users/jbf/data/rbsp-a_WFR-spectral-matrix_emfisis-Quick-Look_20120911_v1.2.6.cdf?BuBu[::40]";
        QDataSet ds= Util.getDataSet(uri);
        System.err.println(ds);
    }
}
