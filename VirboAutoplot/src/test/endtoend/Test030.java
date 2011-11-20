/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.QDataSet;
import org.virbo.jythonsupport.Util;
import static org.virbo.autoplot.ScriptContext.*;

/**
 * tests of metadata representation in the ascii file parser, that provides a means
 * for putting metadata into ascii file headers.
 *
 * http://sourceforge.net/tracker/?func=detail&aid=3169739&group_id=199733&atid=970685
 * JSON-encoded metadata support in ASCII files
 *
 * @author jbf
 */
public class Test030 {

    /**
     * this reads the ascii file into a bundle and tries to plot the last one.
     * This removes most of the dependence on the AsciiDataSource.
     *
     * @param id
     * @param uri
     */
    public static void doTestBundle( int id, String uri ) throws Exception {

        System.err.printf( "reading %s...\n", uri );
        QDataSet ds= Util.getDataSet( uri );
        System.err.printf( "   read %s.\n", String.valueOf(ds) );

        QDataSet bds= (QDataSet) ds.property(QDataSet.BUNDLE_1);

        QDataSet x= DataSetOps.unbundle(ds,0);
        QDataSet y= DataSetOps.unbundle(ds,bds.length()-1);
        plot( x, y );
        setTitle( uri );

        String label= String.format( "test030_%03d", id );

//        if ( id==2 ) {
//            QDataSet Rx_geo = DataSetOps.unbundle( ds, "R_geo" );
//            System.err.println(Rx_geo.property(QDataSet.DEPEND_1));
//            QDataSet lshell= DataSetOps.unbundle( ds, "L" );
//            System.err.println(lshell.property(QDataSet.DEPEND_1));
//        }

        writeToPng( label+".png" );
        //((MutablePropertyDataSet)bundle1).putProperty( QDataSet.LABEL, uri );
        formatDataSet( ds, label+".qds");
        formatDataSet( ds, label+".txt");


    }

    public static void doTest( int id, String uri ) throws Exception {

        System.err.printf( "reading %s...\n", uri );
        QDataSet ds= Util.getDataSet( uri );
        System.err.printf( "   read %s.\n", String.valueOf(ds) );

        plot( ds );
        setTitle( uri );

        String label= String.format( "test030_%03d", id );

        writeToPng( label+".png" );
        //((MutablePropertyDataSet)bundle1).putProperty( QDataSet.LABEL, uri );
        formatDataSet( ds, label+".qds");
        formatDataSet( ds, label+".txt");


    }

    /**
     * we had a bug caused by the * in L*.
     * @throws Exception
     */
    private static void unbundleBug001() throws Exception {
        System.err.println("=======================");
        QDataSet ds2= Util.getDataSet( TestSupport.TEST_DATA + "dat/headers/CRRES_mod.txt?column=Lstar" );
        System.err.println("=======================");
        QDataSet ds1= Util.getDataSet( TestSupport.TEST_DATA + "dat/headers/CRRES_mod.txt?column=L" );
        System.err.println("=======================");
        System.err.println( ds1 );
        System.err.println( ds2 );

    }

    public static void main(String[] args) throws Exception  {
        try {
            doTestBundle( 2, TestSupport.TEST_DATA + "dat/headers/CRRES_mod.txt?rank2" );
            
            doTestBundle( 0, TestSupport.TEST_DATA + "dat/headers/proton_density.dat?rank2" );

            //TODO: DEPEND_0 is lost here.
            doTest( 1, TestSupport.TEST_DATA + "dat/headers/proton_density.dat?column=Proton_Density" );

            doTestBundle( 2, TestSupport.TEST_DATA + "dat/headers/CRRES_mod.txt?rank2" );

            unbundleBug001();
            
        } catch ( Exception ex ) {
            ex.printStackTrace();
            System.exit(1);
        }
        System.exit(0);  // TODO: something is firing up the event thread
    }
    
}
