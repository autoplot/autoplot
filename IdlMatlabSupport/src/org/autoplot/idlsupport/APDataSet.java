/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.idlsupport;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;

/**
 * Extension to QDataSetBridge, which supports reading in data from Autoplot URIs.
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

    /**
     * 1.4.1 clean up Das2Server source so that monitor is only called once.
     */
    public APDataSet() {
        super();
        System.err.println("APDataSet v1.5.0");
        String j= System.getProperty("java.version");
        System.err.println("Java Version "+j);
    }

    /**
     * set the data source URL.  This is an alias for setDataSetURI.
     * @param surl suri the dataset location, such as http://autoplot.org/data/autoplot.dat
     * @deprecated use setDataSetURI, which takes the same argument.
     */
    public synchronized void setDataSetURL(String surl) {
        this.surl = surl;
        datasets.clear();
        names.clear();
    }

    /**
     * set the data source URI.  
     * @param suri the dataset URI, such as vap+dat:http://autoplot.org/data/autoplot.dat
     */
    public synchronized void setDataSetURI(String suri) {
        this.surl = suri;
        datasets.clear();
        names.clear();
    }

    /**
     * get the dataset in one load.
     * @param uri the URI to load.
     * @return 0 if everything went okay, non-zero if there was an error
     * @see QDataSetBridge#getException()
     */
    public int loadDataSet( String uri ) {
        setDataSetURI(uri);
        doGetDataSet();
        if ( exception!=null ) {
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * get the dataset in one load.
     * @param uri the URI to load.
     * @param mon a progress monitor
     * @return 0 if everything went okay, non-zero if there was an error
     * @see QDataSetBridge#getException()
     */
    public int loadDataSet( String uri, ProgressMonitor mon ) {
        setDataSetURI(uri);
        doGetDataSet(mon);
        if ( exception!=null ) {
            return 1;
        } else {
            return 0;
        }
    }
    
    /**
     * this is not called directly by clients in IDL or Matlab.  This is called by doGetDataSet.
     * @param mon
     * @return
     * @throws Exception
     */
    protected QDataSet getDataSet( ProgressMonitor mon ) throws Exception {
        if ( surl==null ) {
            throw new IllegalStateException("uri has not been set.");
        }
        URI uri= DataSetURI.getURI(surl);
        DataSourceFactory f= DataSetURI.getDataSourceFactory( uri, new NullProgressMonitor());

        List<String> problems= new ArrayList();
        if ( f.reject( surl, problems, mon ) ) {
            throw new Exception("URI was rejected by the datasource: "+surl +" rejected by "+ f );
        }

        DataSource dsource = f.getDataSource(uri);

        QDataSet result = dsource.getDataSet( mon);

        if ( result==null ) {
            throw new Exception("getDataSet did not result in dataset: "+surl );
        }
        return result;
    }

    @Override
    public String toString() {
        if ( surl==null ) {
            return "(uninitialized)";
        }
        
        QDataSet d= datasets.get( name );

        StringBuilder s= new StringBuilder();
        
        if ( this.surl!=null ) s.append(this.surl);
        if ( this.filter.length()>0 ) s.append(this.filter);
        for ( String name1: datasets.keySet() ) {
            QDataSet qds= datasets.get(name1);
            s.append( "\n" ).append( name1 ).append( ": " ).append( qds.toString() );
            for ( int i=0; i<QDataSet.MAX_RANK; i++ ) {
                if ( d.property("DEPEND_"+i) ==qds ) {
                    s .append( " (DEPEND_" ).append(i) .append(")");
                }
                if ( d.property("BUNDLE_"+i) ==qds ) {
                    s.append( " (BUNDLE_" ).append(i) .append(")");
                }
            }
        }
        for ( String n: sliceDep.keySet() ) {
            QDataSet ds1= (QDataSet)datasets.get(name).slice(0).property(sliceDep.get(n));
            s.append( "\nvia slice(0): " ).append(n).append( ": " ).append( ds1 ) .append( " (" ). append( sliceDep.get(n) ).append( ")" );
        }
        return s.toString();
    }
    public static void main(String[] args) {
        APDataSet qds = new APDataSet();
        qds.setDataSetURI("http://www.autoplot.org/data/autoplot.dat");
        qds.doGetDataSet( new NullProgressMonitor() );

        String n = qds.name();

        System.err.println(n);


    }

}
