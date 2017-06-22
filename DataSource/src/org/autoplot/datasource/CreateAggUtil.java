/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.datasource;

import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.datum.DatumRange;
import org.das2.datum.TimeParser;

/**
 * contain the code for making aggregations.  This is not used right now, just to store experimental code.
 * @author jbf
 */
public class CreateAggUtil {
    /**
     * return the replacement or null.  remove the used items.
     * @param s
     * @param search
     * @return
     */
    private static String replaceLast( String s, List<String> search, List<String> replaceWith, List<Integer> resolution ) {
        Map<String,Integer> found= new HashMap();
        Map<String,DatumRange> ftr= new HashMap();
        int last= -1;
        String flast= null;
        String frepl= null;
        int best= -1;
        int n= search.size();

        DatumRange tr= null;
        while (true ) {
            for ( int i=0; i<n; i++ ) {
                if ( search.get(i)==null ) continue; // search.get(i)==null means that search is no longer elagable.
                Matcher m= Pattern.compile(search.get(i)).matcher(s);
                int idx= -1;
                while ( m.find() ) idx= m.start();
                if ( idx>-1 ) {
                    found.put( search.get(i), idx );
                    if ( idx>last ) {
                        last= idx;
                        flast= search.get(i);
                        frepl= replaceWith.get(i);
                        best= i;
                    }
                }
            }
            String orig= s;
            if ( best>-1 ) {
                String s1= s.substring(0,last) + s.substring(last).replaceAll(flast, frepl);
                DatumRange tr1= null;
                boolean usable= true;
                try {
                    tr1 = TimeParser.create(s1).parse(orig).getTimeRange();
                    if ( tr==null ) {
                        tr= tr1;
                    } else {
                        if ( !tr1.contains(tr) ) {
                            usable= false;
                        }
                    }
                } catch (ParseException ex) {
                    Logger.getLogger(DataSourceUtil.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
                }
                if ( usable ) {
                    s= s1;
                }
                int res= resolution.get(best);
                int count=0;
                for ( int i=0; i<n; i++ ) {
                    if ( resolution.get(i)>res ) {
                        count++;
                        search.set(i,null);
                    }
                }

                if ( count==search.size() ) {
                    return s;
                } else {
                    best= -1;
                    last= -1; //search for courser resolutions
                }
            } else {
                return s;
            }
        }
    }

}
