/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.idlsupport;

import java.net.URI;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;

/**
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
        System.err.println("APDataSet v1.2.3");
    }

    /**
     * set the data source URL.  This is an alias for setDataSetURI.
     * @param surl suri the dataset location, such as http://autoplot.org/data/autoplot.dat
     */
    public void setDataSetURL(String surl) {
        this.surl = surl;
        datasets.clear();
        names.clear();
    }

    /**
     * set the data source URI.  
     * @param suri the dataset URI, such as vap+dat:http://autoplot.org/data/autoplot.dat
     */
    public void setDataSetURI(String suri) {
        this.surl = suri;
        datasets.clear();
        names.clear();
    }

    public QDataSet getDataSet( ProgressMonitor mon ) throws Exception {
        URI uri= DataSetURI.toUri(surl);
        DataSourceFactory f= DataSetURI.getDataSourceFactory( uri, new NullProgressMonitor());

        if ( f.reject( surl, mon ) ) {
            System.err.println( "URI was rejected by the datasource: "+f );
            return null;
        }

        DataSource dsource = f.getDataSource(uri);

        QDataSet result = dsource.getDataSet( mon);

        return result;
    }

    @Override
    public String toString() {
        QDataSet d= datasets.get( name );

        String s= this.surl;
        for ( String name1: datasets.keySet() ) {
            QDataSet qds= datasets.get(name1);
            s+= "\n" + name1 + ": " +qds;
            for ( int i=0; i<QDataSet.MAX_RANK; i++ ) {
                if ( d.property("DEPEND_"+i) ==qds ) {
                    s += " (DEPEND_"+i+")";
                }
                if ( d.property("BUNDLE_"+i) ==qds ) {
                    s += " (BUNDLE_"+i+")";
                }
            }
        }
        for ( String n: sliceDep.keySet() ) {
            QDataSet ds1= (QDataSet)datasets.get(name).slice(0).property(sliceDep.get(n));
            s+= "\nvia slice(0): " + n + ": " + ds1 + " (" + sliceDep.get(n) + ")";
        }
        return s;
    }
    public static void main(String[] args) {
        APDataSet qds = new APDataSet();
        qds.setDataSetURL("http://www.autoplot.org/data/autoplot.dat");
        qds.doGetDataSet( new NullProgressMonitor() );

        String n = qds.name();

        System.err.println(n);


    }

}
