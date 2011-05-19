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
import java.util.Arrays;
import java.util.Map;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.Glob;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.WritableDataSet;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.DataSourceUtil;
import org.virbo.datasource.capability.TimeSeriesBrowse;

/**
 * Utilities for Jython scripts in both the datasource and application contexts.
 * @author jbf
 */
public class Util {

    /**
     * load the data specified by URL into Autoplot's internal data model.  This will
     * block until the load is complete, and a ProgressMonitor object can be used to
     * monitor the load.
     *
     * This adds a timeRange parameter so that TimeSeriesBrowse-capable datasources
     * can be used from AutoplotServer.
     *
     * @param ds
     */
    public static QDataSet getDataSet( String surl, String stimeRange, ProgressMonitor mon ) throws Exception {
        URI uri = DataSetURI.getURI(surl);
        DataSourceFactory factory = DataSetURI.getDataSourceFactory(uri, new NullProgressMonitor());
        DataSource result = factory.getDataSource( uri );
        if (mon == null) {
            mon = new NullProgressMonitor();
        }

        QDataSet qds;

        TimeSeriesBrowse tsb= result.getCapability( TimeSeriesBrowse.class );
        if ( tsb!=null ) {
            DatumRange timeRange= DatumRangeUtil.parseTimeRange(stimeRange);
            tsb.setTimeRange( timeRange );
        } else {
            System.err.println("Warning: TimeSeriesBrowse capability not found, simply returning dataset.");
        }
        QDataSet rds= result.getDataSet(mon);
        //Logger.getLogger("virbo.jythonsupport").fine( "created dataset #"+rds.getClass().gethashCode() );

        try {
            metadata= result.getMetadata( new NullProgressMonitor() );
        } catch ( Exception e ) {

        }
        metadataSurl= surl;

        if ( rds==null ) return null;
        if ( rds instanceof WritableDataSet && DataSetUtil.isQube(rds) ) {
            return rds;
        } else {
            if ( DataSetUtil.isQube(rds) ) {
                return DDataSet.copy(rds); // fixes a bug where a MutablePropertiesDataSet and WritableDataSet copy in coerce
            } else {
                System.err.println("unable to copy read-only dataset, which may cause problems elsewhere.");
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
    public static QDataSet getDataSet(String surl, ProgressMonitor mon) throws Exception {
        URI uri = DataSetURI.getURIValid(surl);
        DataSourceFactory factory = DataSetURI.getDataSourceFactory(uri, new NullProgressMonitor());
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
        metadataSurl= surl;

        if ( rds==null ) return null;
        if ( rds instanceof WritableDataSet && DataSetUtil.isQube(rds) ) {
            return rds;
        } else {
            if ( DataSetUtil.isQube(rds) ) {
                return DDataSet.copy(rds); // fixes a bug where a MutablePropertiesDataSet and WritableDataSet copy in coerce
            } else {
                System.err.println("unable to copy read-only dataset, which may cause problems elsewhere.");
                //TODO: document this.
                //TODO: fix this.
                return rds;
            }
        }
    }
    
    // cache the last metadata url.
    private static Map<String, Object> metadata;
    private static String metadataSurl;

    /**
     * load the metadata for the url.  This can be called independently from getDataSet,
     * and data sources should not assume that getDataSet is called before getMetaData.
     * Some may, in which case a bug report should be submitted.
     * @param surl
     * @param mon
     * @return metadata tree created by the data source.
     * @throws java.lang.Exception
     */
    public static Map<String, Object> getMetaData(String surl, ProgressMonitor mon) throws Exception {
        if (surl.equals(metadataSurl)) {
            return metadata;
        } else {
            URI url = DataSetURI.getURIValid(surl);
            DataSourceFactory factory = DataSetURI.getDataSourceFactory(url, new NullProgressMonitor());
            DataSource result = factory.getDataSource(DataSetURI.getResourceURI(url));
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
     * @param surl
     * @return data set for the URL.
     * @throws Exception depending on data source.
     */
    public static QDataSet getDataSet(String surl) throws Exception {
        return getDataSet(surl, null);
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
//        System.err.println("======================================================");
//        System.err.println("list( String ) command that lists files is deprecated--use listDirectory( String ) instead.");
//        System.err.println("native python list command will be available soon.  Contact faden @ cottagesystems.com if you need assistance.");
//        System.err.println("  sleeping for 3 seconds.");
//        System.err.println("======================================================");
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
}
