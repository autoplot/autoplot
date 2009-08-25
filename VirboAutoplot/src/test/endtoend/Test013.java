/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Random;
import org.das2.client.DataSetStreamHandler;
import org.das2.dataset.DataSet;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.FDataSet;
import static org.virbo.autoplot.ScriptContext.*;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.QubeDataSetIterator;
import org.virbo.dataset.WritableDataSet;
import org.virbo.dsops.Ops;
import org.virbo.qstream.QDataSetStreamHandler;
import org.virbo.qstream.SimpleStreamFormatter;
import org.virbo.qstream.StreamException;
import org.virbo.qstream.StreamTool;
import test.BundleBinsDemo;

/**
 * Test013--QStream correctness and performance
 * @author jbf
 */
public class Test013 {

    static long t0= System.currentTimeMillis();

    public static void xxx(String id) {
        System.err.println("-- timer -- " + id + " --: "+ ( System.currentTimeMillis()-t0) );
        t0= System.currentTimeMillis();
    }

    /**
     * format the data to ascii, then attempt to parse it.
     * @param ds
     * @param file
     * @throws FileNotFoundException
     * @throws IOException
     * @throws StreamException
     */
    private static void formatParse( QDataSet ds, String file) throws FileNotFoundException, IOException, StreamException {
        SimpleStreamFormatter format = new SimpleStreamFormatter();
        format.format(ds, new FileOutputStream(file), true);
        QDataSetStreamHandler handler = new QDataSetStreamHandler();
        StreamTool.readStream(Channels.newChannel(new FileInputStream(file)), handler);
        QDataSet qds = handler.getDataSet();
    }

    private static QDataSet test1() throws ParseException, StreamException, IOException {
        QDataSet ds = Ops.timegen("2003-09-09", "1 " + Units.days, 11);
        formatParse( ds, "test013_test1.qds" );
        return ds;
    }

    private static QDataSet test1_5() throws ParseException, StreamException, IOException {
        QDataSet ds= Ops.labels(new String[]{"B-GSM,X", "B-GSM,Y", "B-GSM,Z"});
        formatParse( ds, "test013_test1_5.qds" );
        return ds;
    }

    private static QDataSet test2() throws ParseException, StreamException, IOException  {
        MutablePropertyDataSet tags = (MutablePropertyDataSet) Ops.timegen("2003-09-09", "1 " + Units.days, 11);
        tags.putProperty( QDataSet.NAME, "time");
        MutablePropertyDataSet ds = (MutablePropertyDataSet) Ops.findgen(11, 3);
        ds.putProperty(QDataSet.DEPEND_0, tags);
        ds.putProperty(QDataSet.NAME,"B_GSM");

        MutablePropertyDataSet labels= (MutablePropertyDataSet) Ops.labels(new String[]{"B-GSM-X", "B-GSM-Y", "B-GSM-Z"});
        labels.putProperty(QDataSet.NAME, "dimLabels");
        ds.putProperty(QDataSet.DEPEND_1,labels );

        formatParse( ds, "test013_test2.qds" );

        return ds;
    }


    private static QDataSet test3() throws ParseException, StreamException, IOException  {
        MutablePropertyDataSet tags = (MutablePropertyDataSet) Ops.timegen("2003-09-09", "13.86 " + Units.seconds, 11 );
        tags.putProperty( QDataSet.NAME, "time");

        MutablePropertyDataSet ds = (MutablePropertyDataSet) Ops.multiply( Ops.pow( Ops.replicate(1e5,11,3), Ops.randomu(12345,11,3) ), Ops.randomu(12345, 11, 3) );
        ds.putProperty(QDataSet.DEPEND_0, tags);
        ds.putProperty(QDataSet.NAME,"B_GSM");

        MutablePropertyDataSet mode = (MutablePropertyDataSet) Ops.floor( Ops.multiply( Ops.randomu(12345, 11 ), Ops.replicate(4,11) ) );
        EnumerationUnits u= new EnumerationUnits("quality");
        u.createDatum( 0, "Good" );
        u.createDatum( 1, "Better" );
        u.createDatum( 2, "Best" );
        u.createDatum( 3, "Perfect" );
        mode.putProperty( QDataSet.UNITS, u );
        mode.putProperty( QDataSet.DEPEND_0, tags );
        mode.putProperty( QDataSet.NAME, "quality" );

        ds.putProperty(QDataSet.DEPEND_0, tags);
        ds.putProperty(QDataSet.NAME,"B_GSM");


        MutablePropertyDataSet labels= (MutablePropertyDataSet) Ops.labels(new String[]{"B-GSM-X", "B-GSM-Y", "B-GSM-Z"});
        labels.putProperty(QDataSet.NAME, "dimLabels");
        ds.putProperty(QDataSet.DEPEND_1,labels );

        ds.putProperty( QDataSet.PLANE_0, mode );

        formatParse( ds, "test013_test3.qds" );

        return ds;
    }

    private static QDataSet test4_rank3() throws ParseException, StreamException, IOException {
        DDataSet ds= (DDataSet) Ops.dindgen( 3, 4, 5 );
        ds.putValue( 1, 2, 3, 0.05 );
        SimpleStreamFormatter format = new SimpleStreamFormatter();

        formatParse( ds, "test013_test4_rank3.qds" );
        return ds;
    }

    private static QDataSet test0_rank2()  throws ParseException, StreamException, IOException {
        DDataSet ds= (DDataSet) Ops.dindgen( 3, 4 );
        String file= "test013_test0_rank2.qds";
        formatParse(ds, file);

        return ds;
    }

    private static QDataSet test5() throws StreamException, IOException {
        DDataSet ds= (DDataSet) Ops.dindgen( 5 );
        String file= "test013_test5.qds";
        formatParse(ds,file);
        return ds;
    }

    /**
     * "city skyline" dataset with mode changes.
     * @return
     * @throws org.das2.stream.StreamException
     * @throws java.io.IOException
     * @throws javax.xml.parsers.ParserConfigurationException
     */
    private static QDataSet test6() throws StreamException, IOException {
        QDataSet result= null;
        result= Ops.join( result, Ops.dindgen( 5 ) );
        result= Ops.join( result, Ops.dindgen( 5 ) );
        result= Ops.join( result, Ops.dindgen( 5 ) );
        result= Ops.join( result, Ops.dindgen( 4 ) );
        result= Ops.join( result, Ops.dindgen( 4 ) );
        result= Ops.join( result, Ops.dindgen( 4 ) );
        result= Ops.join( result, Ops.dindgen( 4 ) );

        formatParse( result, "test013_test6.qds");

        return result;
    }

    /**
     * test performance of formatting  200K records with 15 planes.
     * @return
     */
    private static QDataSet test7() throws StreamException, IOException {
        int nrec= 190000;

        long t0= System.currentTimeMillis();


        FDataSet result= FDataSet.createRank1( nrec );
        funData( result, 9.2, 0.01, 0, false );
        DDataSet dep0= DDataSet.createRank1( nrec );
        funData( dep0, 10000, 0.01, 0, true );
        result.putProperty( QDataSet.DEPEND_0, dep0 );
        Random rand= new Random(12345);
        for ( int i=0; i<13; i++ ) {
            FDataSet planeds= FDataSet.createRank1( nrec );
            funData( planeds, rand.nextDouble()*100, rand.nextDouble()*10, 0, false );
            planeds.putProperty( QDataSet.NAME, "myplane_"+i );
            result.putProperty( "PLANE_"+i, planeds );
        }
        System.err.println( "generated data in  "+ ( System.currentTimeMillis()-t0) );

        t0= System.currentTimeMillis();
        System.err.println( "formatting... " );

        SimpleStreamFormatter format = new SimpleStreamFormatter();
        format.format( result, new FileOutputStream("test013_test7.qds"), false );

        System.err.println( "time: "+ ( System.currentTimeMillis()-t0) );

        return result;
    }

    private static void testBundle() throws StreamException, FileNotFoundException, IOException {
        QDataSet ds= BundleBinsDemo.demo1();
        formatParse( ds, "test013_testBundle.qds" );
    }

    private static void funData( WritableDataSet ds, double start, double res, int seed, boolean mono ) {
        Random rand= new Random(seed);
        if ( !mono ) {
            QubeDataSetIterator it= new QubeDataSetIterator(ds);
            while ( it.hasNext() ) {
                it.next();
                it.putValue( ds, start );
                start+= res * ( rand.nextDouble() - 0.5 );
            }
        } else {
            QubeDataSetIterator it= new QubeDataSetIterator(ds);
            while ( it.hasNext() ) {
                it.next();
                it.putValue( ds, start );
                start+= res;
            }
        }
    }

    private static void formatBenchmark() throws ParseException, IOException, StreamException {
        int nrec = 100000;
        MutablePropertyDataSet tags = (MutablePropertyDataSet) Ops.timegen("2003-09-09", "1 " + Units.days, nrec);
        tags.putProperty(QDataSet.NAME, "time");

        MutablePropertyDataSet ds = (MutablePropertyDataSet) Ops.randomn(12345,nrec, 3);
        ds.putProperty(QDataSet.DEPEND_0, tags);
        ds.putProperty(QDataSet.NAME, "B_GSM");

        MutablePropertyDataSet labels = (MutablePropertyDataSet) Ops.findgen(3);
        labels.putProperty(QDataSet.NAME, "dimLabels");
        ds.putProperty(QDataSet.DEPEND_1, labels);

        for (int j = 0; j < 2; j++) {
            boolean ascii = j == 0;

            SimpleStreamFormatter format = new SimpleStreamFormatter();

            for (int i = 0; i < 3; i++) {
                long t0 = System.currentTimeMillis();

                String filename = ascii ? "test013_benchmark1.qds" : "test013_benchmark1.binary.qds";
                FileOutputStream fo= new FileOutputStream(filename);
                format.format( ds, fo, ascii );
                fo.close();

                System.err.println("Time to write " + nrec + " records: " + (System.currentTimeMillis() - t0));
            }

        }

    }

    public static void parseBenchmark() throws FileNotFoundException, StreamException, org.das2.stream.StreamException {
        readAsciiQds();
        readBinaryQds();
    }

    private static void readStream( File f ) throws FileNotFoundException, StreamException, org.das2.stream.StreamException {
        String ext= f.toString().substring(f.toString().lastIndexOf(".") ); // URI okay

        if ( ext.equals(".qds") ) {
            long t0 = System.currentTimeMillis();
            InputStream in = new FileInputStream(f);
            QDataSetStreamHandler handler = new QDataSetStreamHandler();
            StreamTool.readStream(Channels.newChannel(in), handler);
            QDataSet qds = handler.getDataSet();
            System.err.println("Time to read " + qds.length() + " records: " + (System.currentTimeMillis() - t0));

        } else {
            long t0 = System.currentTimeMillis();
            InputStream in = new FileInputStream(f);
            DataSetStreamHandler handler = new DataSetStreamHandler( new HashMap(), new NullProgressMonitor() );
            org.das2.util.StreamTool.readStream(Channels.newChannel(in), handler);
            DataSet ds = handler.getDataSet();
            System.err.println("Time to read " + ds.getXLength() + " records: " + (System.currentTimeMillis() - t0));
        }
    }


    private static void readAsciiQds() throws FileNotFoundException, StreamException, org.das2.stream.StreamException {

        File f = new File("test013_benchmark1.qds");

        for (int i = 0; i < 5; i++) {
            readStream(f);
        }
    }


    private static void readBinaryQds() throws FileNotFoundException, StreamException, org.das2.stream.StreamException {

        File f = new File("test013_benchmark1.binary.qds");

        for (int i = 0; i < 5; i++) {
            readStream(f);
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException, Exception {
        try {

            MutablePropertyDataSet ds;

            xxx("init");

            test0_rank2();
            xxx("test0_rank2()");

            test1();
            xxx("test1");

            test1_5();
            xxx("test1_5");

            test2();
            xxx("test2");

            test3();
            xxx("test3");

            test4_rank3();
            xxx("test4_rank3");

            test5();
            xxx("test5");

            //test6();
            xxx("test6 disabled until bug is resolved");

            test7();
            xxx("test7");

            testBundle();
            xxx("testBundle");
            
            formatBenchmark();
            xxx("formatBenchmark");

            parseBenchmark();
            xxx("parseBenchmark");

            ds= TestSupport.sampleDataRank2(100000,30);
            ds.putProperty( QDataSet.DEPEND_0, Ops.timegen( "2009-08-25T10:00", "13.6 ms", 100000 ) );
            ds.putProperty( QDataSet.DEPEND_1, Ops.exp10( Ops.linspace( 1.01, 3.4, 30 ) ) );

            xxx("created fake rank2");

            formatDataSet( ds, "test013_001.qds?type=binary" );

            xxx("test013_001.qds");

            formatDataSet( ds, "test013_002.qds" );

            xxx("test013_002.qds");

            System.exit(0);  // TODO: something is firing up the event thread
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
