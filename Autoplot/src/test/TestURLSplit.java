/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test;

import java.io.File;
import java.util.List;
import org.das2.util.monitor.NullProgressMonitor;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.URISplit;

/**
 *
 * @author jbf
 */
public class TestURLSplit {
    public static void main( String[] args ) throws Exception {
        testContext( );
        test( 10, "http://goes.ngdc.noaa.gov/data/avg/2004/A1050412.TXT?skip=23&timeFormat=%y%m%d %H%M&column=E1&time=YYMMDD", 76 );
        test( 899,"vap:file:///c:/Documents+and+Settings/jbf/Desktop/Product+Summary.xls?sheet=nist+lo&firstRow=&column=B",93);
        test( 2,"vap:/home/jbf/mydata.qds", 3  );
        test( 89, "vap:file:///c:/Documents+and+Settings/jbf/Desktop/Product+Summary.xls?sheet=nist+lo&firstRow=56&column=B", 90 );
        testComplete( 89, "vap:file:///c:/Documents+and+Settings/jbf/Desktop/Product+Summary.xls?sheet=nist+lo&firstRow=56&column=B", 90 );
        testComplete( 8, "c:/Documents and Settings/jbf/Desktop/Product Summary.xls?sheet=nist lo&firstRow=56&column=B", 13 );
        test( 8,"c:/Documents and Settings/jbf/Desktop/Product Summary.xls?sheet=nist lo&firstRow=56&column=B",5);
        test( 1,"dat.file:/home/jbf/project/galileo/src/pws$y$j_ext.data?column=field5&timeFormat=$Y+$j+$H+$M+$S&time=field0&timerange=2000-may+to+2000-sep",0);
        test( 3,"vap+tsds:http://timeseries.org/get.cgi?StartDate=19630101&EndDate=20090103&ppd=-1&ext=tsml&out=bin&param1=OMNI_OMNI2-33-v0",0  );
        test( 4,"vap:/home/jbf/mydata.qds", 14  );
        test( 5,"/home/jbf/mydata.qds", 10  );
        test( 6,"vap+qds:file:///home/jbf/mydata.qds", 25    );
        test( 7,"vap+jdbc:jbdc:mysql://192.168.0.203:3306/temperaturedb?table=temperaturetable&depend0=time&temperature", 0  );
        test( 9,"tsds.http://timeseries.org/get.cgi?StartDate=20030101&EndDate=20080902&ppd=1&ext=bin&out=tsml&param1=OMNI_OMNIHR-26-v0", 12 );
    }

    private static void test(int id,String string, int carotpos  ) throws Exception {
        System.out.println("\n");
        System.out.println(string);
        char c= string.length()>carotpos ? string.charAt(carotpos) : 0;
        URISplit split= URISplit.parse(string, carotpos , true);
        System.out.println(split);
        if ( split.resourceUriCarotPos>=0 ) {
            char c1= split.surl.length()>split.resourceUriCarotPos ? split.surl.charAt(split.resourceUriCarotPos) : 0;
            System.out.println(""+c+"(@"+carotpos+") == "+c1 +"(@"+split.resourceUriCarotPos+"):  "+(c==c1) );
        }
        System.out.println( "carotPosAssert="+ ( split.formatCarotPos-split.resourceUriCarotPos == ( split.vapScheme==null ? 0 : split.vapScheme.length()+1 ) ) ) ;
    }
    
    private static void testComplete( int id, String string, int carotpos ) throws Exception {
        System.err.println("testComplete!!!!");
        System.err.println( string.substring(0,carotpos) );
        List<DataSetURI.CompletionResult> result= DataSetURI.getCompletions( string, carotpos, new NullProgressMonitor() );
        for ( DataSetURI.CompletionResult cr: result ) {
            System.err.print( "  "+ cr.label );
        }
        for ( int i=0; i<string.length(); i++ ) {
            result= DataSetURI.getCompletions( string, i, new NullProgressMonitor() );
            System.err.println( ""+id+"@"+i+":  "+result.size() +"   "+string.substring(0,i) );
        }
    }

    private static void testContext1( String t, String scontext, String expect ) throws Exception {
        if ( scontext.equals("") ) {
            System.err.println( URISplit.format(  URISplit.parse( t ) ) );
        } else {
            //URISplit context= URISplit.parse(scontext);
            //TODO: restore with context-enabled version System.err.println( URISplit.format(  URISplit.parse( t, context ) ) );
        }
    }
    private static void testContext( ) throws Exception {
        String pwd= new File("").toURI().toURL().toString();
        testContext1( "/file.dat", "", "file:///file.dat" );
        testContext1( "file.dat", "", pwd+"file.dat" );
        testContext1( "file.dat", "http://www.myweb.org/path", "http://www.myweb.org/path/file.dat" );
        testContext1( "/file.dat", "http://www.myweb.org/path", "http://www.myweb.org/file.dat" );
    }
}
