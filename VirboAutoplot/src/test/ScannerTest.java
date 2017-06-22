/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.LineNumberInputStream;
import java.util.Scanner;
import java.util.regex.Pattern;
import org.autoplot.ScriptContext;
import org.das2.qds.DDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.DataSetBuilder;

/**
 *
 * @author jbf
 */
public class ScannerTest {
    private static String TEST_FILE;

    public static void main(String[] args) throws FileNotFoundException, Exception {

        TEST_FILE = "/home/jbf/project/cassini/BobsFiles/data_look_asc"; // findbugs
        File file= new File( TEST_FILE);

        int nds= 16;

        DataSetBuilder ttag = new DataSetBuilder( 1, 100);
        LineNumberInputStream lnr = new LineNumberInputStream(new FileInputStream(file));
        Scanner s = new Scanner(lnr);

        s.next("sizes");
        int nfreq= s.nextInt();
        s.nextInt();  // skip this mysterious number

        s.next("freqs");

        DDataSet freqs= DDataSet.createRank1(nfreq);

        for ( int i=0; i<nfreq; i++ ) {
             freqs.putValue(i,s.nextDouble());
        }

        Pattern p= Pattern.compile("time");
        String n = s.next(p);

        Pattern pid= Pattern.compile("\\S+");

        DataSetBuilder data = new DataSetBuilder( 3, 100, nds, nfreq );

        String[] labels= new String[nds];

        int irec=0;

        while ( n!=null ) {

            ttag.putValue(irec, s.nextDouble() );

            System.err.printf( "%s %s\n", n, ttag.toString() );

            for ( int ids= 0; ids<nds; ids++ ) {

                String dsid= s.next(pid);
                if ( dsid.equals("kmat") ) {
                    dsid= dsid + "__" + s.nextInt();
                }

                labels[ids]= dsid;

                for ( int i=0; i<nfreq; i++ ) {
                    double d= s.nextDouble();
                    data.putValue( irec, ids, i, d );
                }

            }

            if ( s.hasNext(p) ) {
                n = s.next(p);
                irec++;
            } else {
                n = null;
            }

        }

        ttag.putProperty( QDataSet.NAME, "Time" );
        QDataSet ttagds= ttag.getDataSet();
        freqs.putProperty( QDataSet.NAME, "Freq" );
        
        data.putProperty( QDataSet.DEPEND_0, ttagds );
        data.putProperty( QDataSet.DEPEND_1, Ops.labels(labels) );
        data.putProperty( QDataSet.DEPEND_2, freqs );
        data.putProperty( QDataSet.NAME, "Data" );

        QDataSet datads= data.getDataSet();

        ScriptContext.formatDataSet(datads,"file:///home/jbf/tmp/foo.qds?type=binary");
    }
}
