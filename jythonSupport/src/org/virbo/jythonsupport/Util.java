/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.jythonsupport;

import java.net.URI;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;

/**
 *
 * @author jbf
 */
public class Util {

    /**
     * 
     * @param url
     * @param mon
     * @return
     * @throws java.lang.Exception
     */
    public static QDataSet getDataSet(String surl, ProgressMonitor mon) throws Exception {
        URI url = new URI(surl);
        DataSourceFactory factory = DataSetURL.getDataSourceFactory(url, new NullProgressMonitor());
        DataSource result = factory.getDataSource( DataSetURL.getWebURL(url) );
        return result.getDataSet(mon == null ? new NullProgressMonitor() : mon);
    }

    public static QDataSet getDataSet(String surl) throws Exception {
        return getDataSet(surl, null);
    }
}
