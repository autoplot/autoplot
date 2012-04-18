/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.pngwalk;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.TimeParser;
import org.das2.fsm.FileStorageModelNew;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.FileSystem.FileSystemOfflineException;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.DataSetURI;

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
    protected static int splitIndex(String surl) {
        int i= firstIndexOf( surl,Arrays.asList( "%Y","$Y","%y","$y","$(","%{","*") );
        if ( i!=-1 ) {
            i = surl.lastIndexOf('/', i);
        } else {
            i = surl.lastIndexOf('/');
        }
        return i;
    }

    public static boolean fileExists(String surl) throws FileSystemOfflineException, URISyntaxException {
        int i= splitIndex( surl );
        FileSystem fs;
        try {
            fs = FileSystem.create( DataSetURI.getResourceURI( surl.substring(0, i + 1) ) );
        } catch (UnknownHostException ex) {
            Logger.getLogger(WalkUtil.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
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
    public static List<URI> getFilesFor( String surl, String timeRange, List<DatumRange> timeRanges, boolean download, ProgressMonitor mon ) throws IOException, ParseException, URISyntaxException {
        DatumRange dr = null;
        if ( timeRange!=null && timeRange.trim().length()>0 ) dr= DatumRangeUtil.parseTimeRange(timeRange);

        int i = surl.indexOf('?');

        String sansArgs = i == -1 ? surl : surl.substring(0, i);

        i = splitIndex(sansArgs);
        FileSystem fs = FileSystem.create( DataSetURI.getResourceURI(sansArgs.substring(0, i+1)) );
        String spec= sansArgs.substring(i+1);

        spec= spec.replaceAll("\\*", ".*"); //GRR.  What if I put .* in there knowing it was a regex.
        spec= spec.replaceAll("\\?", ".");
        
        FileStorageModelNew fsm=null;
        if ( TimeParser.isSpec(spec) ) fsm= FileStorageModelNew.create( fs, spec );

        String[] ss;
        if ( fsm!=null ) {
            ss= fsm.getNamesFor(dr);
        } else {
            if ( spec.length()>0 && spec.substring(1).contains("/") ) throw new IllegalArgumentException("nested wildcards (*/*) not supported");
            ss= fs.listDirectory( "/", spec );
            Arrays.sort(ss);
        }
        
        List<URI> result= new ArrayList(ss.length);
        timeRanges.clear();

        String dirsuri= DataSetURI.fromUri(fs.getRootURI());

        for ( i = 0; i < ss.length; i++) {
            DatumRange dr2=null;
            if ( fsm!=null ) dr2= fsm.getRangeFor(ss[i]);
            if ( dr==null || dr2==null || dr.contains(dr2) ) {
                if ( fs.getFileObject(ss[i]).isLocal() ) {
                    //File f= fs.getFileObject(ss[i]).getFile();
                    result.add( new URI( DataSetURI.getResourceURI( dirsuri ).toString() + ss[i] ) ); // make file:/// match template. // bug 3055130 suspect
                } else {
                    result.add( fs.getRootURI().resolve(ss[i]) );
                }
                timeRanges.add(dr2);
            }
        }

        return result;
    }

    static String readFile(File pf) throws IOException {
        BufferedReader read= new BufferedReader( new FileReader(pf) );
        String s=read.readLine();
        StringBuffer result= new StringBuffer();
        while ( s!=null ) {
            result.append(s);
            result.append("\n");
            s=read.readLine();
        }
        read.close();
        return result.toString();
    }

    static void writeFile( File pf, String s ) throws IOException {
        FileWriter write=null;
        try {
            write= new FileWriter(pf);
            write.write(s);
        } finally {
            if ( write!=null ) write.close();
        }
    }

    /**
     * fast resize based on Java2D.  This is lower quality than ScalePerspectiveImageOp, but much faster.
     * @param originalImage
     * @param width
     * @param height
     * @return
     */
    public static BufferedImage resizeImage( BufferedImage originalImage, int width, int height ) {
        BufferedImage resizedImage = new BufferedImage( width, height, originalImage.getType() );
        Graphics2D g = resizedImage.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(originalImage, 0, 0, width, height, null);
        g.dispose();
        return resizedImage;
    }

}
