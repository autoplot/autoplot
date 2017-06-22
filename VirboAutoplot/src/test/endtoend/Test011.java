/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import org.das2.dataset.VectorDataSet;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.das2.qds.BundleDataSet;
import org.das2.qds.DataSetOps;
import org.das2.qds.DataSetUtil;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.QubeDataSetIterator;
import org.das2.dataset.VectorDataSetAdapter;
import org.das2.qds.SemanticOps;
import org.das2.qds.ops.Ops;
import org.das2.qstream.SimpleStreamFormatter;
import org.das2.qstream.StreamException;

/**
 * performance and function of QDataSet operations.
 * validate filters operations.
 * @author jbf
 */
public class Test011 {

    static long t0 = System.currentTimeMillis();

    private static void timer(String id) {
        System.out.printf("%s (millis): %5d \n", id, System.currentTimeMillis() - t0);
        t0 = System.currentTimeMillis();
    }

    public static void main(String[] args) throws Exception {
        testFilters();
        oldmain(args);
    }
    
    private static void testFilters() {
        boolean okay= true;
        if ( !DataSetOps.changesDimensions("|slice0(0)","") ) okay= false;
        if ( !DataSetOps.changesDimensions("|slice0(0)","|slice1(0)") )  okay= false;
        if ( !DataSetOps.changesDimensions("|slice0(0)","|slice1(0)") )  okay= false;
        if (  DataSetOps.changesDimensions("|slice0(0)|slice1(0)","|slice0(0)|slice1(0)") )  okay= false;
        if (  DataSetOps.changesDimensions("","|nop()") )  okay= false;
        if (  DataSetOps.changesDimensions("|nop()|slice0(0)","|slice0(0)") )  okay= false;
        if (  DataSetOps.changesDimensions("|slice0(0)","|nop()|slice0(0)") )  okay= false;
        if (  DataSetOps.changesDimensions("|slice0(0)","|slice0(0)|nop()") )  okay= false;
        if (  DataSetOps.changesDimensions("|slice0(0)","|smooth(5)|slice0(0)") )  okay= false;
        if (  DataSetOps.changesDimensions("|slice0(0)","|slice0(0)|smooth(5)") )  okay= false;
        if (  DataSetOps.changesDimensions("|smooth(5)|slice0(0)","|slice0(0)") )  okay= false;
        if (  DataSetOps.changesDimensions("|slice0(0)|smooth(5)","|slice0(0)") )  okay= false;
        if ( !okay ) throw new IllegalArgumentException("not okay");
    }
    
    public static void oldmain(String[] args) throws Exception {
        try {
            timer("reset");

            QDataSet ds = Ops.findgen(4000, 30);
            timer("ds=findgen(4000,30)");

            for (int i = 0; i < 4000; i++) {
                String cmd = "|nop()"; //String.format("_s0(%d)", i );
                DataSetOps.sprocess(cmd, ds, null);
            }

            timer("sprocess ds 4000 times");

            QDataSet ds2 = Ops.fftPower(ds);
            System.err.println(ds2);

            timer("fftPower(ds)");

            Ops.add(ds, ds);
            timer("Ops.add( ds,ds )");

            QDataSet ds3 = Ops.zeros(40000, 256);
            timer("Ops.zeroes(40000,256)");

            double total = 0;
            for (int i = 0; i < ds3.length(); i++) {
                for (int j = 0; j < ds3.length(0); j++) {
                    total += ds3.value(i, j);
                }
            }
            timer("access each ds3.value(i,j)");

            total = 0;
            QubeDataSetIterator it = new QubeDataSetIterator(ds3);
            while (it.hasNext()) {
                it.next();
                total += it.getValue(ds3);
            }
            timer("iterator over ds3 to access");

            QDataSet rank1 = TestSupport.sampleDataRank1(10000000);
            timer("rank1= TestSupport.sampleDataRank1(10000000)");

            total = 0;

            for (int i = 0; i < rank1.length(); i++) {
                total += rank1.value(i);
            }
            System.err.println(total);
            timer("access each of rank1.value(i)");

            Ops.histogram(ds, -10, 10, 1.0);
            timer("simple histogram of rank1");

            QDataSet hist = Ops.autoHistogram(rank1);

            timer("autoHistogram of rank1");
            SimpleStreamFormatter ff = new SimpleStreamFormatter();
            FileOutputStream out= new FileOutputStream("test011_000.qds");
            try {
                ff.format(hist, out, true);
            } finally {
                out.close();
            }
            timer("format autoHistogram of rank1");

            double min = Double.MAX_VALUE;
            double max = Double.MIN_VALUE;
            QDataSet wds = DataSetUtil.weightsDataSet(rank1);
            for (int i = 0; i < rank1.length(); i++) {
                if (wds.value(i) == 0) {
                    continue;
                }
                double d = rank1.value(i);
                if (d < min) {
                    min = d;
                }
                if (d > max) {
                    max = d;
                }
            }
            timer("range(rank1)=" + min + " to " + max);

            VectorDataSet vds = VectorDataSetAdapter.create(rank1);
            DatumRange dr = org.das2.dataset.DataSetUtil.yRange(vds);

            timer("range(vds(rank1))=" + dr);

            // test bundle of rank 0 datasets.
            {
                BundleDataSet bds = BundleDataSet.createRank0Bundle();
                MutablePropertyDataSet mds;
                mds = DataSetUtil.asDataSet(0, Units.t2000);
                mds.putProperty(QDataSet.NAME, "time");
                mds.putProperty(QDataSet.FORMAT, "%1$tFT");
                bds.bundle(mds);
                mds = DataSetUtil.asDataSet(1000, Units.eV);
                mds.putProperty(QDataSet.NAME, "channelEnergy");
                mds.putProperty(QDataSet.FORMAT, "%9.3f");
                bds.bundle(mds);
                mds = DataSetUtil.asDataSet(90, Units.degrees);
                mds.putProperty(QDataSet.NAME, "pitchAngle");
                mds.putProperty(QDataSet.FORMAT, "%5.0f");
                bds.bundle(mds);
                mds = DataSetUtil.asDataSet(1e7, Units.dimensionless);
                mds.putProperty(QDataSet.NAME, "flux");
                mds.putProperty(QDataSet.CONTEXT_0, bds);
                mds.putProperty(QDataSet.FORMAT, "%9.2f");
                mds.putProperty(QDataSet.TITLE, DataSetUtil.format(mds));

                System.err.println("test011_001: " + DataSetUtil.format(bds));
                System.err.println("test011_001: " + DataSetUtil.format(mds));

                ff = new SimpleStreamFormatter();
                FileOutputStream fo= new FileOutputStream("test011_001.qds");
                try {
                    ff.format(bds, fo, true);
                } finally {
                    fo.close();
                }
            }

            timer("test bundle of rank 0");


            //test bundle of rank 1 datasets.
            {
                BundleDataSet bds = BundleDataSet.createRank1Bundle();
                MutablePropertyDataSet mds;
                mds = (MutablePropertyDataSet) Ops.linspace(0, 9 * 86400, 10);
                mds.putProperty(QDataSet.UNITS, Units.t2000);
                mds.putProperty(QDataSet.NAME, "time");
                mds.putProperty(QDataSet.FORMAT, "%1$tFT");
                bds.bundle(mds);
                mds = (MutablePropertyDataSet) Ops.replicate(1000, 10);
                mds.putProperty(QDataSet.UNITS, Units.eV);
                mds.putProperty(QDataSet.NAME, "channelEnergy");
                mds.putProperty(QDataSet.FORMAT, "%9.3f");
                bds.bundle(mds);
                mds = (MutablePropertyDataSet) Ops.linspace(0, 180, 10);
                mds.putProperty(QDataSet.UNITS, Units.degrees);
                mds.putProperty(QDataSet.NAME, "pitchAngle");
                mds.putProperty(QDataSet.FORMAT, "%5.0f");
                bds.bundle(mds);

                System.err.println("test011_002: " + DataSetUtil.format(bds));
                ff = new SimpleStreamFormatter();
                try (FileOutputStream fo = new FileOutputStream("test011_002.qds")) {
                    ff.format(bds, fo, true);
                }

            }

            timer("test bundle of rank 1");

            Units u= Units.lookupUnits("[foos]");
            Units u2= Units.lookupUnits("foos");
            if ( !u.isConvertibleTo(u2) ) {
                throw new IllegalArgumentException("[foos] is not convertable to foos");
            }

            timer("test units");

            QDataSet r;

            ds= Ops.ripples(20);
            r= Ops.where( Ops.eq( Ops.valid( ds ), DataSetUtil.asDataSet(0)) );
            System.err.println(r.slice(0));

            ds= Ops.ripples(20,20);
            r= Ops.where( Ops.lt( ds, DataSetUtil.asDataSet(0) ) );
            System.err.println(r);

            timer("test where");

            if (true) {
                System.exit(0);
            } else {
                System.exit(1);
            }

        } catch (RuntimeException ex) {
            ex.printStackTrace();
            System.exit(1);
        } catch (IOException ex ) {
            ex.printStackTrace();
            System.exit(1);
        } catch (StreamException ex ) {
            ex.printStackTrace();
            System.exit(1);

        }
    }
}
