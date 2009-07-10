/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.pngwalk;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.fsm.FileStorageModelNew;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.FileSystem.FileSystemOfflineException;
import org.das2.util.monitor.ProgressMonitor;

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

    /**
     * returns the last index of slash, splitting the FileSystem part from the template part.
     * @param surl
     * @return
     */
    private static int splitIndex(String surl) {
        int i= firstIndexOf( surl,Arrays.asList( "%Y","$Y","%y","$y",".*") );
        if ( i!=-1 ) {
            i = surl.lastIndexOf('/', i);
        } else {
            i = surl.lastIndexOf('/');
        }
        return i;
    }

    public static boolean fileExists(String surl) throws FileSystemOfflineException, MalformedURLException {
        int i= splitIndex( surl );
        FileSystem fs = FileSystem.create( new URL(surl.substring(0,i+1) ) );
        return fs.getFileObject(surl.substring(i+1)).exists();
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
    public static List<URL> getFilesFor( String surl, String timeRange, List<DatumRange> timeRanges, boolean download, ProgressMonitor mon ) throws IOException, ParseException {
        DatumRange dr = null;
        if ( timeRange!=null && timeRange.trim().length()>0 ) dr= DatumRangeUtil.parseTimeRange(timeRange);

        int i = surl.indexOf('?');

        String sansArgs = i == -1 ? surl : surl.substring(0, i);

        i = splitIndex(sansArgs);
        FileSystem fs = FileSystem.create( new URL(sansArgs.substring(0, i+1)) );
        String spec= sansArgs.substring(i+1).replaceAll("\\$", "%");

        spec= spec.replaceAll("\\*", ".*");
        spec= spec.replaceAll("\\?", ".");
        
        FileStorageModelNew fsm=null;
        if ( spec.contains("%Y")||spec.contains("%y") ) fsm= FileStorageModelNew.create( fs, spec );

        String[] ss;
        if ( fsm!=null ) {
            ss= fsm.getNamesFor(dr);
        } else {
            if ( spec.substring(1).contains("/") ) throw new IllegalArgumentException("nested wildcards (*/*) not supported");
            ss= fs.listDirectory( "/", spec );
        }

        Arrays.sort(ss);
        
        List<URL> result= new ArrayList(ss.length);
        timeRanges.clear();

        for ( i = 0; i < ss.length; i++) {
            DatumRange dr2=null;
            if ( fsm!=null ) dr2= fsm.getRangeFor(ss[i]);
            if ( dr==null || dr2==null || dr.contains(dr2) ) {
                if ( fs.getFileObject(ss[i]).isLocal() ) {
                    File f= fs.getFileObject(ss[i]).getFile();
                    result.add( f.toURI().toURL() );
                } else {
                    result.add( new URL( fs.getRootURL(), ss[i]) );
                }
                timeRanges.add(dr2);
            }
        }

        return result;
    }
}
