/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.jythonsupport;

import java.net.URI;
import java.util.logging.Logger;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.DDataSet;
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
     * load the dataset identified by the URL.  Execution will block until the data
     * is loaded.
     * @param surl dataSource identifier
     * @param mon monitor for the loading, or null (None in jython).
     * @return QDataSet that is the data from the DataSource.
     * @throws java.lang.Exception
     */
    public static QDataSet getDataSet(String surl, ProgressMonitor mon) throws Exception {
        Logger.getLogger("virbo.jythonsupport").fine( "getDataSet("+surl+")");
        URI url = DataSetURL.getURI(surl);
        DataSourceFactory factory = DataSetURL.getDataSourceFactory(url, new NullProgressMonitor());
        DataSource result = factory.getDataSource( DataSetURL.getWebURL(url) );
        QDataSet rds= result.getDataSet(mon == null ? new NullProgressMonitor() : mon);
        //Logger.getLogger("virbo.jythonsupport").fine( "created dataset #"+rds.getClass().gethashCode() );
        return DDataSet.copy(rds); // fixes a bug where a MutablePropertiesDataSet and WritableDataSet copy in coerce
    }

    /**
     * load the dataset identified by the URL.  Execution will block until the data
     * is loaded.
     * @param surl dataSource identifier
     * @return QDataSet that is the data from the DataSource.
     * @throws java.lang.Exception, depending on the data source type.
     */
    public static QDataSet getDataSet(String surl) throws Exception {
        return getDataSet(surl, null);
    }
}
