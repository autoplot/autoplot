/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import org.autoplot.ScriptContext;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.DataSourceRegistry;
import org.autoplot.jythonsupport.Util;

/**
 * tests of the Java-based cdf reader
 * @author jbf
 */
public class Test032 {
    private static ScriptContext scriptContext= ScriptContext.getInstance();
    
    private static void test1_dump( QDataSet ds ) {
        if ( ds.rank()==4 ) {
            for ( int i0=0; i0<3; i0++ ) {
                System.err.printf("ds.value(%d,[0,1,2],0,0)=",i0);
                for ( int i1=0; i1<3; i1++ ) {
                    System.err.print( " " + ds.value( i0, i1, 0, 0 ) );
                }
                System.err.println("");
            }
            System.err.println("");
        } else if (ds.rank() == 3) {
            for ( int i0=0; i0<3; i0++ ) {
                System.err.printf("ds.value(%d,[0,1,2],0)=",i0);
                for ( int i1=0; i1<3; i1++ ) {
                    System.err.print( " " + ds.value( i0, i1, 0 ) );
                }
                System.err.println("");
            }
            System.err.println("");
        } else if ( ds.rank()==2 ) {
            for ( int i0=0; i0<3; i0++ ) {
                System.err.printf("ds.value(%d,[0,1,2],0)=",i0);
                for ( int i1=0; i1<3; i1++ ) {
                    System.err.print( " " + ds.value( i0, i1 ) );
                }
                System.err.println("");
            }
            System.err.println("");
        } else {
            throw new IllegalArgumentException("bad rank!");
        }
    }

    // verify Ecounts[1] is the same dataset as Ecounts|slice(1)
    public static void test1() throws Exception {
        String uri= "vap+cdfj:file:///home/jbf/ct/hudson/data.backup/cdf/l1_h0_mpa_20020202_v02.cdf?Ecounts";
        QDataSet ds1;
        long t0= System.currentTimeMillis();

        QDataSet ds2;
        ds2= Util.getDataSet( uri + "" );
        ds1= Util.getDataSet( uri + "[2]" );

        QDataSet Ecounts= Util.getDataSet("vap+cdf:file:///home/jbf/ct/hudson/data.backup/cdf/l1_h0_mpa_20020202_v02.cdf?Ecounts");
        
        System.err.println("Ecounts[0,0,0,0]= "+Ecounts.value(0,0,0,0) + "    = 2.8333332538604736 at rev 15298" );
        System.err.println("Ecounts[0,1,0,0]= "+Ecounts.value(0,1,0,0) + "    = 4.833333492279053 at rev 15298" );

        // the result should be Ecounts[Epoch=977,40,6,24].  This is what matches cdfedit (note cdfedit indexes start with 1).
        //   Ecounts[0,0,0,0]= 2.83
        //   Ecounts[0,1,0,0]= 4.83
        //   Ecounts[0,0,1,0]= 4.83
        //   Ecounts[0,0,0,1]= 3.83
        //   Ecounts[2,0,0,0]= 9.00
        //   Ecounts[2,1,0,0]= 7.00
        //   Ecounts[2,2,0,0]= 5.00
        //   Ecounts[2,0,1,0]= 1.92
        //   Ecounts[2,0,2,0]= 8.92
        //   Ecounts[2,0,0,1]= 4.00
        System.err.println( "ds2="+ds2 );

        ds2= ds2.slice(2);

        System.err.println( System.getProperty("java.version") + " " + System.getProperty("os.arch") );
        System.err.println( "Ecounts[2]=" + ds1 );
        test1_dump( ds1 );
        System.err.println( "Ecounts|slice(2)=" +ds2 );
        test1_dump( ds2 );

        System.err.println( "(Ecounts[2])[20,3,12]=" + ds1.value(20,3,12)  );
        System.err.println( "(Ecounts|slice(2))[20,3,12]=" +ds2.value(20,3,12) );

        System.err.println( "done in "+( System.currentTimeMillis()-t0) + " millis" );
    }

    // same test, but for rank 3 not rank 4.
    public static void test1_b() throws Exception {
        String uri= "vap+cdfj:file:///home/jbf/ct/hudson/data.backup/cdf/l1_h0_mpa_20020202_v02.cdf?Azangle";
        QDataSet ds1;
        long t0= System.currentTimeMillis();
        ds1= Util.getDataSet( uri + "[2]" );

        QDataSet ds2;
        ds2= Util.getDataSet( uri + "" );

        System.err.println( "ds2="+ds2 );

        ds2= ds2.slice(2);

        System.err.println( System.getProperty("java.version") + " " + System.getProperty("os.arch") );
        System.err.println( "Azangle[2]=" + ds1 );
        test1_dump( ds1 );
        System.err.println( "Azangle|slice(2)=" +ds2 );
        test1_dump( ds2 );

        System.err.println( "(Azangle[2])[20,12]=" + ds1.value(20,12)  );
        System.err.println( "(Azangle|slice(2))[20,12]=" +ds2.value(20,12) );

        System.err.println( "done in "+( System.currentTimeMillis()-t0) + " millis" );
    }


 // Ecounts isn't the same if it comes from java.
    public static void test2() throws Exception {

        QDataSet ds1;

        long t0= System.currentTimeMillis();

        ds1= Util.getDataSet( "vap+cdfj:file:///home/jbf/ct/hudson/data.backup/cdf/l1_h0_mpa_20020202_v02.cdf?Ecounts[2]" );

        QDataSet ds2;
        ds2= Util.getDataSet("vap+cdf:file:///home/jbf/ct/hudson/data.backup/cdf/l1_h0_mpa_20020202_v02.cdf?Ecounts[2]" );

        System.err.println( System.getProperty("java.version") + " " + System.getProperty("os.arch") );
        System.err.println( "java Ecounts[2].slice(20)=" + ds1.slice(20) );
        test1_dump( ds1.slice(20) );
        System.err.println( "   c Ecounts[2].slice(20)=" + ds2.slice(20) );
        test1_dump( ds2.slice(20) );

        System.err.println( "done in "+( System.currentTimeMillis()-t0) + " millis" );
    }

    // verify rank 3 data is the same in C and in Java
    public static void test2_b() throws Exception {

        QDataSet ds1;

        long t0= System.currentTimeMillis();

        ds1= Util.getDataSet( "vap+cdfj:file:///home/jbf/ct/hudson/data.backup/cdf/l1_h0_mpa_20020202_v02.cdf?Azangle[2]" );

        QDataSet ds2;
        ds2= Util.getDataSet("vap+cdf:file:///home/jbf/ct/hudson/data.backup/cdf/l1_h0_mpa_20020202_v02.cdf?Azangle[2]" );

        System.err.println( System.getProperty("java.version") + " " + System.getProperty("os.arch") );
        System.err.println( "java Azangle[2].slice(20)=" + ds1.slice(20) );
        test1_dump( ds1 );
        System.err.println( "   c Azangle[2].slice(20)=" + ds2.slice(20) );
        test1_dump( ds2 );

        System.err.println( "done in "+( System.currentTimeMillis()-t0) + " millis" );
    }

     // verify on fake dataset
    public static void test3() throws Exception {

        long t0= System.currentTimeMillis();

        QDataSet ds1;
        QDataSet ds2;

        System.err.println( "== col major slice(2) ==" );

        ds1= Util.getDataSet( "vap+cdfj:file:///home/jbf/ct/hudson/data.backup/cdf/testCdfColMajor.cdf?rank4float" );
        ds2= Util.getDataSet(  "vap+cdf:file:///home/jbf/ct/hudson/data.backup/cdf/testCdfColMajor.cdf?rank4float" );

        //test1_dump( ds1 );
        
        System.err.println( "java =" + ds1 );
        System.err.println( "   c =" + ds2 );

        System.err.println( "=== java " + ds1 + " ===");
        System.err.println( "   c =" + ds2 );

        System.err.println( "=== java " + ds1 + " ===");
        test1_dump( ds1.slice(2) );
        System.err.println( "===    c " + ds2 + " ===");
        test1_dump( ds1.slice(2) );
        
        System.err.println( "   c =" + ds2 );
        test1_dump( ds2.slice(2) );

        System.err.println( "== row major slice(2) ==" );

        ds1= Util.getDataSet( "vap+cdfj:file:///home/jbf/ct/hudson/data.backup/cdf/testCdfRowMajor.cdf?rank4float" );
        ds2= Util.getDataSet(  "vap+cdf:file:///home/jbf/ct/hudson/data.backup/cdf/testCdfRowMajor.cdf?rank4float" );

        System.err.println( "java =" + ds1 );
        test1_dump( ds1.slice(2) );
        System.err.println( "   c =" + ds2 );
        test1_dump( ds2.slice(2) );

        System.err.println( "done in "+( System.currentTimeMillis()-t0) + " millis" );
    }

    public static void main( String[] args ) throws Exception {

        System.err.println( System.getProperty("java.version") + " " + System.getProperty("os.arch") );
        System.err.println( "C-based library version: (No Longer Installed)" );

        test1();
        test1_b();
        test2();
        test2_b();
        test3();

        if ( true ) {
            scriptContext.getDocumentModel().getOptions().setAutolayout(false);
            scriptContext.getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");

            DataSourceRegistry.getInstance().registerExtension( "org.autoplot.cdf.CdfJavaDataSourceFactory", "cdf", "CDF files using java based reader" );

            Test012.main( new String[] { "032" } );

        }

    }
}
