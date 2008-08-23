/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.jythonsupport;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Logger;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.Glob;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;

/**
 * Utilities for jython scripts in both the datasource and application contexts.
 * @author jbf
 */
public class Util {

    
    /**
     * load the data specified by URL into Autoplot's internal data model.  This will
     * block until the load is complete, and a ProgressMonitor object can be used to
     * monitor the load.
     * @param ds
     */
    public static QDataSet getDataSet(String surl, ProgressMonitor mon) throws Exception {
        URI url = DataSetURL.getURI(surl);
        DataSourceFactory factory = DataSetURL.getDataSourceFactory(url, new NullProgressMonitor());
        DataSource result = factory.getDataSource(DataSetURL.getWebURL(url));
        if (mon == null) {
            mon = new NullProgressMonitor();
        }
        QDataSet rds= result.getDataSet(mon == null ? new NullProgressMonitor() : mon);
        //Logger.getLogger("virbo.jythonsupport").fine( "created dataset #"+rds.getClass().gethashCode() );
        
        metadata= result.getMetaData( new NullProgressMonitor() );
        metadataSurl= surl;
        return DDataSet.copy(rds); // fixes a bug where a MutablePropertiesDataSet and WritableDataSet copy in coerce
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
            URI url = DataSetURL.getURI(surl);
            DataSourceFactory factory = DataSetURL.getDataSourceFactory(url, new NullProgressMonitor());
            DataSource result = factory.getDataSource(DataSetURL.getWebURL(url));
            if (mon == null) {
                mon = new NullProgressMonitor();
            }
            //result.getDataSet(mon);  some data sources may assume that getDataSet comes before getMetaData
            return result.getMetaData(mon);
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
     * returns a list of the files in the local or remote filesystem pointed to by surl.
     * print list( 'http://www.papco.org/data/de/eics/*' )
     *  --> '81355_eics_de_96s_v01.cdf', '81356_eics_de_96s_v01.cdf', '81357_eics_de_96s_v01.cdf', ...
     * @param surl
     * @return 
     * @throws java.net.MalformedURLException
     * @throws java.io.IOException
     */
    public static String[] list(String surl) throws MalformedURLException, IOException {
        String[] ss = FileSystem.splitUrl(surl);
        FileSystem fs = FileSystem.create(new URL(ss[2]));
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
