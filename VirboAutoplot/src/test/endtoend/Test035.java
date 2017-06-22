/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.List;
import org.autoplot.bookmarks.Bookmark;


/**
 * Test to test import of bookmarks files and other app functions.  This was
 * introduced to easy transition of bookmarks format
 * @author jbf
 */
public class Test035 {

    private static String home= TestSupport.TEST_DATA_SMALL;

    private static void format( List<Bookmark> books, String file ) throws IOException {
        File f= new File( file );

        String ss= Bookmark.formatBooks(books);

        PrintStream fout = new PrintStream(f);
        fout.print(ss);
        fout.close();

    }

    public static void main( String[] args ) throws Exception {

        long t0= System.currentTimeMillis();

        List<Bookmark> book;

        book= Bookmark.parseBookmarks( new URL( home + "bookmarks/autoplot_spot6_2010.xml" ) );
        format( book, "test035_001_autoplot_spot6_2010.xml" );
        System.err.printf( "test 001: done in %9.2f sec\n", ( System.currentTimeMillis()-t0 ) / 1000. );

        book= Bookmark.parseBookmarks( new URL( "http://autoplot.org/data/demos.xml" ) );
        format( book, "test035_002_demos.xml" );
        System.err.printf( "test 002: done in %9.2f sec\n", ( System.currentTimeMillis()-t0 ) / 1000. );

        //book= Bookmark.parseBookmarks( new URL( home + "bookmarks/rw_20111003.xml" ) );
        //format( book, "test035_002_rw_20111003.xml" );

        System.exit(0);
    }
}
