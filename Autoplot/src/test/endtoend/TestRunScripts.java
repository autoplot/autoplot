
package test.endtoend;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.autoplot.bookmarks.Bookmark;
import org.autoplot.bookmarks.BookmarksException;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceUtil;
import org.das2.util.filesystem.HtmlUtil;
import org.das2.util.monitor.CancelledOperationException;
import org.das2.util.monitor.NullProgressMonitor;
import org.xml.sax.SAXException;

/**
 * Somehow there doesn't seem to be a test which runs a set of scripts in
 * a bookmarks file or website.  This takes the list in the first
 * argument, a bookmarks file, and runs through all the tests.  Each is
 * run with a fresh Autoplot session, possibly in parallel.
 * 
 * @author jbf
 */
public class TestRunScripts {
    
    private static int testid;
    private static String autoplotJar;
    private static String jre= System.getenv("JAVA_HOME") +"/bin/java";
    
    public static void main( String[] args ) throws IOException {
        //args= new String[] { 
        //    "/home/jbf/local/autoplot/autoplot.jar",
        //    "000",
        //    "https://github.com/autoplot/dev/blob/master/demos/tools/systemmonitor/systemmonitor.md"
        //};
            
        int exitCode=0;
        
        autoplotJar= args[0];
        testid= Integer.parseInt( args[1] );
        args= Arrays.copyOfRange( args, 2, args.length );
        for ( String s: args ) {
            try {
                runScriptsInFile(s);
            } catch ( Exception ex ) {
                exitCode=-1;
            }
        }
        
        System.exit(exitCode);
    }
    
    public static void runScriptsInFile( String uri ) {
        
        Map<String,Exception> exceptions= new LinkedHashMap();
        Map<String,Integer> exceptionNumbers= new LinkedHashMap();
        
        try {
            System.err.println("\n=======================");
            System.err.println("== from "+uri);
            System.err.println("=======================\n");
            
            int iid;
            if ( uri.endsWith(".xml") ) {
                File ff= DataSetURI.getFile( uri, new NullProgressMonitor() );
                iid= doBookmarks(ff,0,exceptions,exceptionNumbers);

            } else {
                iid= doHtml( new URL(uri),0,exceptions,exceptionNumbers );
            }
            
        } catch ( Exception e ) {
        
        }
        
        
    }
    
    /**
     * This uses "" for the shortId.
     * @param uri the URI to load
     * @param iid the index of the test.
     * @param doTest if true, then expect a match, otherwise an ex prefix is used to indicate there should not be a match
     * @param isPublic
     * @return
     * @throws Exception 
     */
    private static String do1( String uri, int iid, boolean doTest, boolean isPublic ) throws Exception {
        return do1( uri, "", iid, doTest, isPublic );
    }
   /**
     *
     * @param uri the URI to load
     * @param shortId empty or the short unique name for the test
     * @param iid the index of the test.
     * @param doTest if true, then expect a match, otherwise an ex prefix is used to indicate there should not be a match
     * @param isPublic if true, the URI can be printed in public logs and the image available on the web.
     * @return the ID of a product to test against a reference.
     * @throws Exception
     */
    private static String do1( String uri, String shortId, int iid, boolean doTest, boolean isPublic ) throws Exception {
        System.err.printf( "== %03d %03d %s ==\n", testid, iid, shortId );
        if ( isPublic ) {
            System.err.printf( "uri: %s\n", uri );
        } else {
            System.err.printf( "uri: (uri is not public)\n" );
        }

        String label;
        if ( shortId.length()>0 ) {
            label= String.format( "test%03d_%s", testid, shortId );
        } else {
            label= String.format( "test%03d_%03d", testid, iid );
        }

        double tsec,psec;
        long t0= System.currentTimeMillis();
        tsec= t0; // for non-vap non-uri
        psec= t0;
        
        // java -cp autoplot.jar --script=uri
        // --scriptExit is maybe a bug because otherwise Autoplot doesn't exit after
        String[] command= new String[] { jre, "-jar", autoplotJar, "--headless", "--scriptExit", "--script="+uri };
        
        System.err.println( String.join( " ", command ) );
        
        ProcessBuilder pb= new ProcessBuilder(command);
        pb.redirectError( new File( label+".err.txt" ) );
        Process p= pb.start();
        
        p.waitFor();
        
        if ( p.exitValue()!=0 ) {
            System.err.println("p.exitValue()=="+p.exitValue());
            throw new Exception("script got exception: "+uri);
        }

        return shortId;
    }
    
    
    private static int doBookmarks( File f, int iid, Map<String,Exception> exceptions, Map<String,Integer> exceptionNumbers ) throws IOException, SAXException, BookmarksException {
        List<Bookmark> books= Bookmark.parseBookmarks( f.toURI().toURL() );
        return doBookmarks( books, iid, exceptions, exceptionNumbers );
    } 
    
    private static int doBookmarks( List<Bookmark> books, int iid, Map<String,Exception> exceptions, Map<String,Integer> exceptionNumbers ) throws IOException, SAXException, BookmarksException {
        for ( Bookmark b: books ) {
            boolean hidden= b.isHidden();
            if ( !hidden ) {
                if ( b instanceof Bookmark.Folder ) {
                    if ( ( ( Bookmark.Folder ) b ).getRemoteUrl() != null ) {
                        System.err.println("Skipping remote bookmarks file "  + ( ( Bookmark.Folder ) b ).getRemoteUrl() );
                    } else {
                        iid= doBookmarks( ((Bookmark.Folder)b).getBookmarks(), iid, exceptions, exceptionNumbers );
                    }
                } else {
                    String uri= ((Bookmark.Item)b).getUri();
                    try {
                        do1(uri, iid, true, true );
                    } catch ( Exception ex ) {
                        exceptions.put( uri, ex );
                    } finally {
                        iid++;
                    }
                }
            } else {
                if ( b instanceof Bookmark.Folder ) {
                    System.err.println("Skipping hidden bookmark: \n\t"  + b.getTitle() + "\n\t"+b.getDescription() );                    
                } else if ( b instanceof Bookmark.Item ) {
                    System.err.println("Skipping hidden bookmark: \n\t"  + ((Bookmark.Item)b).getUri() + "\n\t" + b.getTitle() + "\n\t"+b.getDescription() );
                    iid++; // allow for temporarily disabling without affecting id numbers.
                }
            }
        }
        return iid;
    }
    
    private static int doHtml( URL url, int iid, Map<String,Exception> exceptions, Map<String,Integer> exceptionNumbers ) throws IOException, CancelledOperationException {
        File f= DataSetURI.getFile(url.toString());
        try (InputStream in = new FileInputStream(f)) {
            URL[] urls= HtmlUtil.getDirectoryListing(url,in,false);
            List<URL> result= new ArrayList<>();
            List<String> sresult= new ArrayList<>();
            for ( URL url1: urls ) {
                if ( url1.getFile().endsWith(".jy") ) {
                    sresult.add(url1.toString());
                }
            }
            for ( String suri : sresult ) {
                try {
                    do1(suri, iid, true, true );
                } catch (Exception ex) {
                    exceptions.put( suri, ex );
                    exceptionNumbers.put( suri, iid );
                } finally {
                    iid++;
                }
            }
            return iid;        
        }
    }

    
}
