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
import org.das2.datum.TimeParser;
import org.das2.fsm.FileStorageModelNew;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.Glob;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.aggregator.AggregatingDataSourceFactory;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.WritableDataSet;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.DataSourceFormat;
import org.virbo.datasource.DataSourceUtil;
import org.virbo.datasource.capability.TimeSeriesBrowse;

/**
 * Utilities for Jython scripts in both the datasource and application contexts.
 * @author jbf
 */
public class Util {

    private static final Logger logger= LoggerManager.getLogger("jython");

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
     * @param monitor progress monitor object.
     */
    public static QDataSet getDataSet( String suri, String stimeRange, ProgressMonitor mon ) throws Exception {
        logger.log( Level.FINE, "getDataSet({0},{1})", new Object[]{suri, stimeRange} );
        DatumRange timeRange= DatumRangeUtil.parseTimeRange(stimeRange);
        return getDataSet( suri, timeRange, mon );
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
     */
    public static QDataSet getDataSet( String suri, DatumRange timeRange, ProgressMonitor monitor ) throws Exception {
        long t0= System.currentTimeMillis();
        logger.log( Level.FINE, "getDataSet({0},{1})", new Object[]{suri, timeRange} );
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
        //Logger.getLogger("virbo.jythonsupport").fine( "created dataset #"+rds.getClass().gethashCode() );

        try {
            metadata= result.getMetadata( new NullProgressMonitor() );
        } catch ( Exception e ) {

        }
        metadataSurl= suri;

        logger.fine( String.format( "read in %9.2f sec: %s", (System.currentTimeMillis()-t0)/1000., suri ) );
        if ( rds==null ) return null;
        if ( rds instanceof WritableDataSet && DataSetUtil.isQube(rds) ) {
            return rds;
        } else {
            if ( DataSetUtil.isQube(rds) ) {
                return DDataSet.copy(rds); // fixes a bug where a MutablePropertiesDataSet and WritableDataSet copy in coerce
            } else {
                logger.info("unable to copy read-only dataset, which may cause problems elsewhere.");
                //TODO: document this.
                //TODO: fix this.
                return rds;
            }
        }
    }

    
    /**
     * load the data specified by URL into Autoplot's internal data model.  This will
     * block until the load is complete, and a ProgressMonitor object can be used to
     * monitor the load.
     * @param ds
     */
    public static QDataSet getDataSet(String suri, ProgressMonitor mon) throws Exception {
        logger.log( Level.FINE, "getDataSet({0})", suri );
        URI uri = DataSetURI.getURIValid(suri);
        DataSourceFactory factory = DataSetURI.getDataSourceFactory(uri, new NullProgressMonitor());
        if ( factory==null ) throw new IllegalArgumentException("unsupported extension: "+suri);
        DataSource result = factory.getDataSource( uri );
        if (mon == null) {
            mon = new NullProgressMonitor();
        }
        QDataSet rds= result.getDataSet(mon);
        //Logger.getLogger("virbo.jythonsupport").fine( "created dataset #"+rds.getClass().gethashCode() );

        try {
            metadata= result.getMetadata( new NullProgressMonitor() );
        } catch ( Exception e ) {
            
        }
        metadataSurl= suri;

        if ( rds==null ) return null;
        if ( rds instanceof WritableDataSet && DataSetUtil.isQube(rds) ) {
            return rds;
        } else {
            if ( DataSetUtil.isQube(rds) ) {
                return DDataSet.copy(rds); // fixes a bug where a MutablePropertiesDataSet and WritableDataSet copy in coerce
            } else {
                logger.info("unable to copy read-only dataset, which may cause problems elsewhere.");
                //TODO: document this.
                //TODO: fix this.
                return rds;
            }
        }
    }

    /**
     * returns the dataSource for the given URI.  This will include capabilities, like TimeSeriesBrowse.
     * @param suri
     * @return
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
     * @return
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
     * @param suri
     * @param mon
     * @return metadata tree created by the data source.
     * @throws java.lang.Exception
     */
    public static Map<String, Object> getMetadata(String suri, ProgressMonitor mon) throws Exception {
        logger.log( Level.FINE, "getMetadata({0})", suri );

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
     * @param suri
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
     * @param stimeRange timerange like "2012-02-02/2012-02-03"
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
     * @param timeRange timerange object
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
     * @param ext the extension and any parsing parameters, such as "vap+bin:?recLength=2000&rank2=1:"
     * @param in
     * @return
     * @throws java.lang.Exception
     */
    public static QDataSet getDataSet( String spec, InputStream in, ProgressMonitor mon ) throws Exception {
        logger.log( Level.FINE, "getDataSet({0},InputStream)", new Object[]{spec} );
        String[] ss= spec.split(":",-2);
        String ext;
        int i= ss[0].indexOf("+");
        ext= (i==-1) ? ss[0] : ss[0].substring(i+1);
        File f= File.createTempFile("autoplot", "."+ext );

        ReadableByteChannel chin= Channels.newChannel(in);
        WritableByteChannel chout= new FileOutputStream(f).getChannel();
        DataSourceUtil.transfer(chin, chout);

        String virtUrl= ss[0]+":"+ f.toURI().toString() + ss[1];
        QDataSet ds= getDataSet(virtUrl,mon);
        return ds;
    }
//
//    /**
//     *
//     * @param surl
//     * @return
//     * @throws IOException
//     * @throws URISyntaxException
//     * @deprecated use listDirectory instead
//     */
//    public static String[] list( String surl ) throws IOException, URISyntaxException {
//        logger.info("======================================================");
//        logger.info("list( String ) command that lists files is deprecated--use listDirectory( String ) instead.");
//        logger.info("native python list command will be available soon.  Contact faden @ cottagesystems.com if you need assistance.");
//        logger.info("  sleeping for 3 seconds.");
//        logger.info("======================================================");
//        try {
//            Thread.sleep(3000);
//        } catch (InterruptedException ex) {
//            Logger.getLogger(Util.class.getName()).log(Level.SEVERE, null, ex);
//        }
//        return listDirectory( surl );
//    }

    /**
     * returns a list of the files in the local or remote filesystem pointed to by surl.
     * print listDirectory( 'http://autoplot.org/data/pngwalk/' )
     *  --> 'product.vap', 'product_20080101.png', 'product_20080102.png', ...
     * print listDirectory( 'http://autoplot.org/data/pngwalk/*.png' )
     *  --> 'product_20080101.png', 'product_20080102.png', ...
     *
     * @param surl
     * @return 
     * @throws java.net.MalformedURLException
     * @throws java.io.IOException
     */
    public static String[] listDirectory(String surl) throws IOException, URISyntaxException {
        logger.log(Level.FINE, "listDirectory({0})", surl);
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
        FileStorageModelNew fsm = AggregatingDataSourceFactory.getFileStorageModel(surl);
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
     * 2009-06-01, 2009-06-02, ..., 2009-06-30.
     * @param spec such as "%Y-%m".  Note specs like "%Y%m" will not be parsable.
     * @param srange range limiting the list, such as "2009"
     * @return a string array of formatted time ranges, such as [ "2009-01", "2009-02", ..., "2009-12" ]
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
        List<String> result= new ArrayList<String>();
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
     * Export the data into a format implied by the filename extension.  
     * See the export data dialog for additional parameters available for formatting.
     *
     * For example:
     * <p><blockquote><pre>
     * ds= getDataSet('http://autoplot.org/data/somedata.cdf?BGSEc')
     * formatDataSet( ds, 'vap+dat:file:/home/jbf/temp/foo.dat?tformat=minutes&format=6.2f')
     * </pre></blockquote></p>
     * 
     * @param ds
     * @param file local file name that is the target
     * @throws java.lang.Exception
     */
    public static void formatDataSet(QDataSet ds, String file) throws Exception {
        if (!file.contains(":/")) {
            file = new File(file).getCanonicalFile().toString();
        }
        URI uri = DataSetURI.getURIValid(file);

        DataSourceFormat format = DataSetURI.getDataSourceFormat(uri);
        
        if (format == null) {
            throw new IllegalArgumentException("no format for extension: " + file);
        }

        format.formatData( DataSetURI.fromUri(uri), ds, new NullProgressMonitor());

    }
        
    /**
     * return true if we should do the imports as before, where all of Autoplot is
     * imported with each session.  This is used to ease migration.
     * @return 
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
