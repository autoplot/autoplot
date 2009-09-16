/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.idlsupport;

import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.dsops.Ops;

/**
 *
 * @author jbf
 */
public class APDataSet extends QDataSetBridge {

    private String surl;
    

    static {
        /* DataSourceRegistry registry = DataSourceRegistry.getInstance();
        registry.register( new AsciiTableDataSourceFactory(), ".dat" );
        registry.register( new AsciiTableDataSourceFactory(), ".bin" );
        registry.register( new Das2StreamDataSourceFactory(), ".qds" ); */
    }

    public APDataSet() {
        super();
        System.err.println("GetDataSet v1.2.1");
    }

    public void setDataSetURL(String surl) {
        this.surl = surl;
    }

    QDataSet getDataSet( ProgressMonitor mon ) throws Exception {
        DataSource dsource = DataSetURI.getDataSource(surl);

        QDataSet result = dsource.getDataSet( mon);

        return result;
    }

    public static void main(String[] args) {
        APDataSet qds = new APDataSet();
        qds.setDataSetURL("http://www.autoplot.org/data/autoplot.dat");
        qds.doGetDataSet( new NullProgressMonitor() );

        String n = qds.name();

        System.err.println(n);


    }

}
