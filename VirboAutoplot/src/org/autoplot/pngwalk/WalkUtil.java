/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.pngwalk;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.fsm.FileStorageModel;
import org.das2.util.FileUtil;
import org.das2.util.filesystem.FileSystem;
import org.virbo.aggragator.AggregatingDataSourceFactory;

/**
 *
 * @author jbf
 */
public class WalkUtil {

    private static int firstIndexOf( String str, List<String> targets ) {
        int i0= Integer.MAX_VALUE;
        for ( String t: targets ) {
            int i= str.indexOf(t);
            if ( i>-1 && i<i0 ) i0= i;
        }
        return i0==Integer.MAX_VALUE ? -1 : i0;
    }

    private static int splitIndex(String surl) {
        int i= firstIndexOf( surl,Arrays.asList( "%Y","$Y","%y","$y",".*") );
        if ( i!=-1 ) {
            i = surl.lastIndexOf('/', i);
        }
        return i;
    }

    /**
     * return an array of URLs that match the spec for the time range provided.
     *
     * @param surl an autoplot url with an aggregation specifier.
     * @param timeRange a string that is parsed to a time range, such as "2001", or null.
     * @return a list of URLs without the aggregation specifier.
     * @throws java.io.IOException if the remote folder cannot be listed.
     * @throws java.text.ParseException if the timerange cannot be parsed.
     */
    public static List<URL> getFilesFor( String surl, String timeRange, List<DatumRange> timeRanges ) throws IOException, ParseException {
        DatumRange dr = null;
        if ( timeRange!=null ) dr= DatumRangeUtil.parseTimeRange(timeRange);

        int i = surl.indexOf('?');

        String sansArgs = i == -1 ? surl : surl.substring(0, i);

        i = splitIndex(sansArgs);
        FileSystem fs = FileSystem.create( new URL(sansArgs.substring(0, i)) );
        String spec= sansArgs.substring(i).replaceAll("\\$", "%");
        FileStorageModel fsm=null;
        if ( spec.contains("%Y")||spec.contains("%y") ) fsm= FileStorageModel.create( fs, spec );

        String[] ss;
        if ( fsm!=null ) {
            ss= fsm.getNamesFor(dr);
        } else {
            if ( spec.substring(1).contains("/") ) throw new IllegalArgumentException("nested wildcards (.*/.*) not supported");
            ss= fs.listDirectory( "/", spec.substring(1) );
        }

        List<URL> result= new ArrayList(ss.length);
        timeRanges.clear();

        for ( i = 0; i < ss.length; i++) {
            DatumRange dr2=null;
            if ( fsm!=null ) dr2= fsm.getRangeFor(ss[i]);
            if ( dr==null || dr2==null || dr.contains(dr2) ) {
                result.add( new URL( fs.getRootURL(), ss[i]) );
                timeRanges.add(dr2);
            }
        }

        return result;
    }
}
