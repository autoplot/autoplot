/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.TimeUtil;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AutoplotSettings;

/**
 *
 * @author jbf
 */
public class TryHistory {
    public static void main( String[] args ) throws FileNotFoundException, IOException, Exception {

        File f= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ) + "/bookmarks/history.txt" );

        BufferedReader reader= new BufferedReader( new FileReader(f) );
        String s= reader.readLine();

        String lastURI= "";
        Map<String,Integer> plottedURIs= new LinkedHashMap();

        int i=1;

        Map<String,List<String>> exceptions= new LinkedHashMap();
        long t00= System.currentTimeMillis();

        Datum firstTime=null;
        Datum lastTime=null;

        while ( s!=null ) {
            String[] ss= s.split("\t",2);
            String time= ss[0];
            String suri= ss[1];

            if ( firstTime==null ) {
                firstTime= TimeUtil.createValid(time);
            }
            lastTime= TimeUtil.createValid(time);

            //if ( i>50 ) break;

            Integer count= plottedURIs.get(suri);

            if ( count==null || count==0 ) {
                System.err.println("=== " + i + " =======================================================");
                System.err.println("readat: " + time);
                System.err.println("uri:    " + suri );

                long t0= System.currentTimeMillis();

                try {
                    QDataSet ds = org.virbo.jythonsupport.Util.getDataSet(suri, new NullProgressMonitor() );
                    System.err.println( "result: " + ds);
                } catch ( Exception ex ) {
                    String exs= ex.getMessage();
                    if ( exs==null ) exs= ex.toString();
                    if ( ! exs.equals( "Unsupported extension: vap") ) {
                        System.err.println( "exception: " + ex );
                        List<String> uris= exceptions.get( exs );
                        if ( uris==null ) {
                            uris= new ArrayList<String>();
                        }
                        uris.add( suri );
                        exceptions.put(exs,uris);
                    } else {
                        System.err.println( "ignoring vap files for now" );
                    }
                }
                System.err.println( "readtm: " + String.format("%9.2f", (System.currentTimeMillis()-t0)/1000. ).trim() + " sec");
                System.err.println( "tottim: " + String.format("%9.2f", (System.currentTimeMillis() - t00) / 1000.0 /60 ).trim() + " min");

                count=1;
                plottedURIs.put( suri, count );
                
            } else {
                count++;
                plottedURIs.put( suri, count );

            }

            i++;
            s= reader.readLine();
        }

        System.err.println( "\n===== Summary =================================================");
        DatumRange dr= new DatumRange( TimeUtil.prevMidnight(firstTime), TimeUtil.nextMidnight(lastTime) );
        System.err.println( "interval of history: " + dr );
        final double totSec = (System.currentTimeMillis() - t00) / 1000.0;
        System.err.println( "total time to read: " + String.format("%9.2f", totSec/60 ).trim() + " min");
        System.err.println( "total URIs read: "+ plottedURIs.size() );
        System.err.println( "URIs/sec: "+ String.format( "%9.2f", 1.*plottedURIs.size()/totSec ) );
        System.err.println( "Exceptions encountered: "+ exceptions.size() );

        System.err.println( "\n=== Exceptions ==============================================");
        for ( String ex: exceptions.keySet() ) {
            List<String> uris= exceptions.get(ex);
            System.err.println( ex );
            for ( String uri: uris ) {
                System.err.println( "  " + uri );
            }
            System.err.println("");
        }

    }

}
