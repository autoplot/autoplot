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
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.TimeUtil;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.AutoplotSettings;

/**
 *
 * @author jbf
 */
public class TryHistory {
    public static void main( String[] args ) throws FileNotFoundException, IOException, Exception {

        File f= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ) + "/bookmarks/history.txt" );

        File fout= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ) + "/bookmarks/tryhistory.log" );

        BufferedReader reader= new BufferedReader( new FileReader(f) );
        String s= reader.readLine();

        PrintStream out= new PrintStream( fout );

        //count the number of lines
        int lineCount=1;
        while ( s!=null ) {
            lineCount++;
            s= reader.readLine();
        }
        reader.close();

        reader= new BufferedReader( new FileReader(f) );
        s= reader.readLine();

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
                out.println("=== " + i + "/" + lineCount + " ===================================================");
                out.println("readat: " + time);
                out.println("uri:    " + suri );

                long t0= System.currentTimeMillis();

                try {
                    QDataSet ds = org.autoplot.jythonsupport.Util.getDataSet(suri, new NullProgressMonitor() );
                    out.println( "result: " + ds);
                } catch ( Exception ex ) {
                    String exs= ex.getMessage();
                    if ( exs==null ) exs= ex.toString();
                    if ( ! exs.equals( "Unsupported extension: vap") ) {
                        out.println( "exception: " + ex );
                        List<String> uris= exceptions.get( exs );
                        if ( uris==null ) {
                            uris= new ArrayList<String>();
                        }
                        uris.add( suri );
                        exceptions.put(exs,uris);
                    } else {
                        out.println( "ignoring vap files for now" );
                    }
                }
                out.println( "readtm: " + String.format(Locale.US, "%9.2f", (System.currentTimeMillis()-t0)/1000. ).trim() + " sec");
                out.println( "tottim: " + String.format(Locale.US, "%9.2f", (System.currentTimeMillis() - t00) / 1000.0 /60 ).trim() + " min");

                count=1;
                plottedURIs.put( suri, count );
                
            } else {
                count++;
                plottedURIs.put( suri, count );

            }

            i++;
            s= reader.readLine();
        }
        reader.close();

        out.println( "\n===== Summary =================================================");
        DatumRange dr= new DatumRange( TimeUtil.prevMidnight(firstTime), TimeUtil.nextMidnight(lastTime) );
        out.println( "interval of history: " + dr );
        final double totSec = (System.currentTimeMillis() - t00) / 1000.0;
        out.println( "total time to read: " + String.format( Locale.US, "%9.2f", totSec/60 ).trim() + " min");
        out.println( "total URIs read: "+ plottedURIs.size() );
        out.println( "URIs/sec: "+ String.format( Locale.US, "%9.2f", 1.*plottedURIs.size()/totSec ) );
        out.println( "Exceptions encountered: "+ exceptions.size() );

        out.println( "\n=== Exceptions ==============================================");
        for ( Entry<String,List<String>> en: exceptions.entrySet() ) {
            List<String> uris= exceptions.get(en.getKey());
            out.println( en.getKey() );
            for ( String uri: uris ) {
                out.println( "  " + uri );
            }
            out.println("");
        }

    }

}
