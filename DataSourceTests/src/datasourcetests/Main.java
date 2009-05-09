/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package datasourcetests;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
import org.virbo.dsops.Ops;
import org.virbo.qstream.SimpleStreamFormatter;
import org.virbo.qstream.StreamException;
import org.virbo.qstream.StreamTool;

/**
 *
 * @author jbf
 */
public class Main {

    /**
     * returns the location of local example input data, ends with slash.
     * @return
     */
    public static String getLocalRoot() {
        URL url= Main.class.getResource("Main.class");
        File f= new File( url.getPath() );
        f= f.getParentFile().getParentFile().getParentFile().getParentFile();
        f= new File( f, "input" );
        return f.toURI().toString() + "/";
    }

    public static void dumpResult( String id, QDataSet ds ) throws IOException, StreamException {
        URL url= Main.class.getResource("Main.class");
        File f= new File( url.getPath() );
        f= f.getParentFile().getParentFile().getParentFile().getParentFile();
        f= new File( f, "output/"+id+".qds" );
        new SimpleStreamFormatter().format( ds, new FileOutputStream(f), true );
    }


    public boolean doTest001() throws Exception {
        String uri= getLocalRoot() + "x.dat";
        DataSource ds= DataSetURL.getDataSource(uri);
        QDataSet result= ds.getDataSet( new ConsoleProgressMonitor() );
        QDataSet ah= Ops.autoHistogram(result);
        QDataSet ahdep0= Ops.autoHistogram((QDataSet)result.property(QDataSet.DEPEND_0));
        dumpResult( "test001", ah );
        dumpResult( "test001_0", ahdep0 );
        return true;
    }

    public boolean doTest002() throws Exception {
        String uri= getLocalRoot() + "mistakeYear.dat";
        DataSource ds= DataSetURL.getDataSource(uri);
        QDataSet result= ds.getDataSet( new ConsoleProgressMonitor() );
        QDataSet ah= Ops.autoHistogram(result);
        QDataSet ahdep0= Ops.autoHistogram((QDataSet)result.property(QDataSet.DEPEND_0));
        dumpResult( "test002", ah );
        dumpResult( "test002_0", ahdep0 );
        return true;
    }

    public boolean doTest003() throws Exception {
        String uri= getLocalRoot() + "twoColumnTime.dat";
        DataSource ds= DataSetURL.getDataSource(uri);
        QDataSet result= ds.getDataSet( new ConsoleProgressMonitor() );
        QDataSet ah= Ops.autoHistogram(result);
        QDataSet ahdep0= Ops.autoHistogram((QDataSet)result.property(QDataSet.DEPEND_0));
        dumpResult( "test003", ah );
        dumpResult( "test003_0", ahdep0 );
        return true;
    }

    public boolean doTest004() throws Exception{
        DataSource ds= DataSetURL.getDataSource("http://vmo.nasa.gov/mission/ampte_cce/mag/sa_mag_84237.dat?time=field0&timeFormat=$y+$j+$H+$M+$S&column=field10");
        QDataSet result= ds.getDataSet( new ConsoleProgressMonitor() );
        QDataSet ah= Ops.autoHistogram(result);
        QDataSet ahdep0= Ops.autoHistogram((QDataSet)result.property(QDataSet.DEPEND_0));
        dumpResult( "test004", ah );
        dumpResult( "test004_0", ahdep0 );
        return true;
    }

    public boolean doTest005() throws Exception {
        DataSource ds= DataSetURL.getDataSource("vap+tsds:http://timeseries.org/get.cgi?StartDate=20000101&EndDate=20071231&ext=bin&out=tsml&ppd=24&param1=SourceAcronym_Subset1-1-v0");
        QDataSet result= ds.getDataSet( new ConsoleProgressMonitor() );
        QDataSet ah= Ops.autoHistogram(result);
        QDataSet ahdep0= Ops.autoHistogram((QDataSet)result.property(QDataSet.DEPEND_0));
        dumpResult( "test005", ah );
        dumpResult( "test005_0", ahdep0 );
        return true;
    }
    
    public void doTests() throws Exception {
        doTest001();
        doTest002();
        doTest003();
        doTest004();
        doTest005();
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        new Main().doTests();
    }

}
