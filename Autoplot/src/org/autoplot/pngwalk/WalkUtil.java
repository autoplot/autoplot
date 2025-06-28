
package org.autoplot.pngwalk;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.DatumRange;
import org.das2.datum.TimeParser;
import org.das2.fsm.FileStorageModel;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.FileSystem.FileSystemOfflineException;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.DataSetURI;
import org.das2.util.filesystem.FileSystemUtil;
import org.das2.util.filesystem.LocalFileSystem;

/**
 *
 * @author jbf
 */
public class WalkUtil {
    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.pngwalk");
    
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
     * See also FileStorageModel.splitIndex and AggregatingDataSource.splitIndex
     * @param surl
     * @return the index of the last character of the url.
     * @see FileStorageModel#splitIndex(java.lang.String) which does the same thing.
     */
    protected static int splitIndex(String surl) {
        int i= firstIndexOf( surl,Arrays.asList( "%Y","$Y","%y","$y","$(","%{","*","$x") );
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
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            return false;
        } catch ( FileNotFoundException ex ) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            return false;
        }
        return fs.getFileObject(surl.substring(i+1)).exists();
    }


    /**
     * encode strange characters like umlauts, but not slashes.
     */
    private static String makeSafe( String s ) {
        try {
            String result= URLEncoder.encode( s, "UTF-8" );
            result= result.replaceAll("\\%2F","/");
            result= result.replaceAll("\\%7E","~");
            return result;
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * return an array of URIs that match the spec for the timerange 
     * (if provided), limiting the search to this range.
     *
     * @param surl an Autoplot URI with an aggregation specifier.
     * @param timeRange a string that is parsed to a time range, such as 2001, or null. 
     * @param timeRanges list which is populated
     * @param download (is not used)
     * @param mon progress monitor
     * @return a list of URIs without the aggregation specifier.
     * @throws java.io.IOException if the remote folder cannot be listed.
     * @throws java.text.ParseException if the timerange cannot be parsed.
     * @throws java.net.URISyntaxException when the surl cannot be resolved to a web address.
     */
    public static List<URI> getFilesFor( String surl, DatumRange timeRange, List<DatumRange> timeRanges, boolean download, ProgressMonitor mon ) throws IOException, ParseException, URISyntaxException {
        DatumRange dr = timeRange;
        
        int i = surl.indexOf('?');

        String sansArgs = i == -1 ? surl : surl.substring(0, i);

        i = splitIndex(sansArgs);
        URI surls= DataSetURI.getResourceURI(sansArgs.substring(0, i+1));
        FileSystem fs = FileSystem.create( surls );
        String spec= sansArgs.substring(i+1);

        spec= spec.replaceAll("\\*", ".*"); //GRR.  What if I put .* in there knowing it was a regex.
        spec= spec.replaceAll("\\?", ".");
        
        FileStorageModel fsm=null;
        if ( TimeParser.isSpec(spec) ) fsm= FileStorageModel.create( fs, spec );

        String[] ss;
        if ( fsm!=null ) {
            ss= fsm.getNamesFor(dr);
        } else {
            //if ( spec.length()>0 && spec.substring(1).contains("/") ) throw new IllegalArgumentException("nested wildcards (*/*) not supported");
            spec= spec.replaceAll("\\$x", ".*");
            spec= spec.replaceAll("\\$\\(x\\;?.*\\)", ".*");
            ss= fs.listDirectoryDeep( "/", spec );
            Arrays.sort(ss);
        }
        
        List<URI> result= new ArrayList(ss.length);
        timeRanges.clear();
        
        for ( i = 0; i < ss.length; i++) {
            DatumRange dr2=null;
            if ( fsm!=null ) dr2= fsm.getRangeFor(ss[i]);
            if ( dr==null || dr2==null || dr.intersects(dr2) ) {
                if ( fs instanceof LocalFileSystem ) {
                    File f= fs.getFileObject(ss[i]).getFile();
                    result.add( f.toURI() );
                } else {
                    if ( ss[i].startsWith("/") ) {
                        result.add( fs.getRootURI().resolve( "./" + FileSystemUtil.uriEncode( ss[i].substring(1) ) ) );
                    } else {
                        result.add( fs.getRootURI().resolve( "./" + FileSystemUtil.uriEncode( ss[i] ) ) );
                    }
                }
                timeRanges.add(dr2);
            }
        }

        return result;
    }

    static String readFile(File pf) throws IOException {
        BufferedReader read=null;
        StringBuilder result= new StringBuilder();
        try {
            read= new BufferedReader( new FileReader(pf) );
            String s=read.readLine();
            while ( s!=null ) {
                result.append(s);
                result.append("\n");
                s=read.readLine();
            }
        } finally {
            if ( read!=null ) read.close();
        }
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
        if ( Math.abs(originalImage.getWidth()-width)<2 && Math.abs(originalImage.getHeight()-height)<2 ) { // allow for rounding errors
            return originalImage;
        }
        BufferedImage resizedImage = new BufferedImage( width, height, originalImage.getType() );
        Graphics2D g = resizedImage.createGraphics();
        g.fillRect(0,0,width,height);
        if ( originalImage.getWidth()>65 ) {
            g.setComposite(AlphaComposite.Src);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        }
        g.drawImage(originalImage, 0, 0, width, height, null);
        g.dispose();
        return resizedImage;
    }

}
