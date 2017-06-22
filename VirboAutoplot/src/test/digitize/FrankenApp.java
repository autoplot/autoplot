/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.digitize;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.das2.qds.QDataSet;
import static org.das2.qds.ops.Ops.*;

/**
 * I need to port some of Sebastian's routines into Java so I can debug them.
 * 
 * @author jbf
 */
public class FrankenApp {
    public static Map<String,List> edgeDetection( QDataSet spectra_trim, List<Integer> time_index, List<Integer> freq_index ) {
        
    int r = 0;
    int t = time_index.get(0);
    QDataSet span = spectra_trim.slice(0); //[0,:];
    List<String> tags= new ArrayList();

    double low = total( span.trim( freq_index.get(r)-2, freq_index.get(r)+1 ) );
    double high = total( span.trim( freq_index.get(r)+1, freq_index.get(r)+2+1 ) );
    
    double threshold = (float)( (high+low)/2. );
    
    System.err.println( "low=" + low );
    System.err.println( "high=" + high );
    System.err.println( "thresh=" + threshold );
    
    for ( int islice=0; islice< spectra_trim.length(); islice++  ) {
        QDataSet s= spectra_trim.slice(islice);
        List<Integer> collect = new ArrayList();
        collect.add( freq_index.get(r) );

        for ( int i=0; i<10; i++ ) {
            if ( log10( s.value(freq_index.get(r)+i+1) ) >= log10( s.value(freq_index.get(r)+i) ) ) {
                collect.add( freq_index.get(r)+i+1 );
            } else {
                break;
            }
        }
        for ( int i=-9; i<1; i++ ) {
            if ( log10( s.value(freq_index.get(r)+i-1) ) <= log10( s.value(freq_index.get(r)+i) ) ) {
                collect.add( freq_index.get(r)+i+1 );
            } else {
                break;
            }
        }
        
        int avg_freq = (int)( round( divide( total(collect), collect.size() ) ).value() );
        low = total( s.trim( freq_index.get(r)-2, freq_index.get(r)+1 ) );
        high = total( s.trim( freq_index.get(r)+1, freq_index.get(r)+2+1 ) );
        double ratio = (double)( (high+low)/2. );
        
        if ( ratio > threshold ) {
            if ( freq_index.get(r)-1 != avg_freq ) {
                int avg = (int)( (freq_index.get(r)-1+avg_freq)/2. );
                freq_index.add(avg);
            } else {
                freq_index.add(avg_freq);
            }
        } else {
            if ( freq_index.get(r)+1 != avg_freq ) {
                int avg = (int)( (freq_index.get(r)+1+avg_freq)/2. );
                freq_index.add(avg);
            } else {
                freq_index.add(avg_freq);
            }
        }
        time_index.add(t);
        tags.add( "fpe");
        tags.add( "edit");
        r+= 1;
        t+= 1;
    }
    
    time_index= time_index.subList(0,time_index.size());
    freq_index= freq_index.subList(0,time_index.size());
    
    Map<String,List> result= new HashMap();
    result.put( "time_index", time_index );
    result.put( "freq_index", freq_index );
    result.put( "tags", tags );
    return result;
    }
}
