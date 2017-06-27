/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import org.autoplot.AutoplotUtil;
import org.autoplot.RenderType;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;

/**
 * Test for autoranging for various QDataSets.  This now uses AutoplotUtil.bounds, which was extracted from here to
 * mimic the Autoplot code for autoranging.
 * @author jbf
 */
public class Test022 {

    private static boolean doTest( QDataSet ds, QDataSet bounds ) throws Exception {
        QDataSet tbounds= AutoplotUtil.bounds(ds,RenderType.spectrogram);
        System.err.println( "tbounds=" + tbounds );
        if ( bounds!=null ) System.err.println( "bounds=" + bounds );
        return true;
    }


//    private static void dumpRank3Ds( QDataSet ds ) {
//        for ( int i=0; i<ds.length(); i++ ) {
//            QDataSet slice= ds.slice(i);
//            System.err.println( "--- " + slice + " ---");
//            for ( int j=0; j<slice.length(); j++ ) {
//                for ( int k=0; k<slice.length(j); k++ ) {
//                    System.err.print( " \t" + slice.value(j,k) );
//                }
//                System.err.println("");
//            }
//        }
//    }

    /**
     * test code for identifying dataset schemes
     */
    private static void testSchemes( ) {
        QDataSet ds;
        System.err.println( "---" );
        ds= TestSupport.sampleDataRank1(99);
        System.err.println( ds );
        System.err.println( "x: "+SemanticOps.xtagsDataSet( ds ) );
        System.err.println( "y: "+SemanticOps.ytagsDataSet( ds ) );

        System.err.println( "---" );
        ds= TestSupport.sampleDataRank2(99,20);
        System.err.println( ds );
        System.err.println( "x: "+SemanticOps.xtagsDataSet( ds ) );
        System.err.println( "y: "+SemanticOps.ytagsDataSet( ds ) );

        System.err.println( "---" );
        ds= TestSupport.sampleQube1( 3.4, 4.5, 22, 32 );
        System.err.println( ds );
        System.err.println( "x: "+SemanticOps.xtagsDataSet( ds ) );
        System.err.println( "y: "+SemanticOps.ytagsDataSet( ds ) );

        System.err.println( "---" );
        ds= TestSupport.sampleRank3Join();
        System.err.println( ds );
        System.err.println( "x: "+SemanticOps.xtagsDataSet( ds ) );
        System.err.println( "y: "+SemanticOps.ytagsDataSet( ds ) );
        
    }

    public static void main(String[] args)  {
        try {
            doTest( TestSupport.sampleDataRank1(100), null );
            //dumpRank3Ds( TestSupport.sampleRank3Join() );
            doTest( TestSupport.sampleRank3Join(), null );
            testSchemes();

        } catch ( Exception ex ) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
