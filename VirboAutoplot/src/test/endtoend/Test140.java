/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;
import org.das2.util.Base64;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.autoplot.ScriptContext;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;

import static org.virbo.autoplot.ScriptContext.*;
import org.virbo.autoplot.bookmarks.Bookmark;
import org.virbo.autoplot.bookmarks.BookmarksException;
import org.virbo.datasource.DataSetURI;
import org.xml.sax.SAXException;

/**
 * download a list of URIs, then attempt to read from each one.  The
 * URI can be either a dataset or a .vap file, and "script:" "pngwalk:" and 
 * "bookmarks:" URIs are ignored.
 * 
 * @author jbf
 */
public class Test140 {
    
    private static int testid;
    
    /**
     * make sure name is unique by checking to see if the file exists and
     * modifying it until the name is unique.
     * @param name
     * @param ext .png
     * @param append if true, then tack on the extention.
     * @return the unique name
     */
    private static String getUniqueFilename( String name, String ext, boolean append ) {
      File ff= new File(name+ext);
      while ( ff.exists() ) {
         name= name + "_";
         ff= new File(name+".png");
      }  
      if ( append ) {
         return name+ext;
      } else {
         return name;
      }
    }
    /**
     *
     * @param uri the URI to load
     * @param iid the index of the test.
     * @param doTest if true, then expect a match, otherwise an ex prefix is used to indicate there should not be a match
     * @return the ID of a product to test against a reference.
     * @throws Exception
     */
    private static String do1( String uri, int iid, boolean doTest ) throws Exception {

        System.err.printf( "== %03d ==\n", iid );
        System.err.printf( "uri: %s\n", uri );

        String label= String.format( "test%03d_%03d", testid, iid );

        double tsec;
        long t0= System.currentTimeMillis();
        tsec= t0; // for non-vap non-uri
        
        QDataSet ds=null;
        if ( uri.endsWith(".vap") ) {
            // for vap files, load the vap and grab the first dataset.
            ScriptContext.load(uri);
            ds= getDocumentModel().getDataSourceFilters(0).getController().getDataSet();
            tsec= (System.currentTimeMillis()-t0)/1000.;
            if ( ds!=null ) {
                MutablePropertyDataSet hist= (MutablePropertyDataSet) Ops.autoHistogram(ds);
                hist.putProperty( QDataSet.LABEL, label );
                formatDataSet( hist, getUniqueFilename(label,".qds",true) );
            } else {
                throw new IllegalArgumentException("a dataset from the vap was null: "+uri );
            }
        } else if ( uri.startsWith("script:") ) {
            System.err.println("skipping script");
        } else if ( uri.startsWith("bookmarks:") ) {
            System.err.println("skipping bookmarks");
        } else if ( uri.startsWith("pngwalk:") ) {
            System.err.println("skipping pngwalk");
        } else {
            ds= org.virbo.jythonsupport.Util.getDataSet( uri );
            tsec= (System.currentTimeMillis()-t0)/1000.;
            if ( ds!=null ) {
                MutablePropertyDataSet hist= (MutablePropertyDataSet) Ops.autoHistogram(ds);
                hist.putProperty( QDataSet.TITLE, uri );

                hist.putProperty( QDataSet.LABEL, label );
                formatDataSet( hist, label+".qds");

                QDataSet dep0= (QDataSet) ds.property( QDataSet.DEPEND_0 );
                if ( dep0!=null ) {
                    MutablePropertyDataSet hist2= (MutablePropertyDataSet) Ops.autoHistogram(dep0);
                    formatDataSet( hist2, label+".dep0.qds");
                } else {
                    PrintWriter pw= new PrintWriter( label+".dep0.qds" );
                    pw.println("no dep0");
                    pw.close();
                }

                plot( ds );
                setCanvasSize( 450, 300 );
                int i= uri.lastIndexOf("/");

                getApplicationModel().waitUntilIdle(true);

                String fileUri= uri.substring(i+1);

                if ( !getDocumentModel().getPlotElements(0).getComponent().equals("") ) {
                    String dsstr= String.valueOf( getDocumentModel().getDataSourceFilters(0).getController().getDataSet() );
                    fileUri= fileUri + " " + dsstr +" " + getDocumentModel().getPlotElements(0).getComponent();
                }

                setTitle(fileUri);
                
            } else {
                throw new IllegalArgumentException("uri results in null dataset: "+uri );
                
            }
        }

        System.err.println( "dataset: "+ds );
        
        String result;

        String name;
        if ( doTest ) {            
            String id= URLEncoder.encode( uri, "US-ASCII" );
            id= id.replaceAll("%","_"); // make more human-ledgible, it doesn't need to be absolutely unique
            name= String.format( "test%03d_%s", testid, id );
            result= name;
        } else {
            name= String.format( "ex_test%03d_%03d", testid, iid );
            result= null;
        }
        
        String name1= getUniqueFilename( name, ".png", true );
        writeToPng( name1 );
        
        System.err.printf( "wrote to file: %s\n", name1 );
        System.err.printf( "Read in %9.3f seconds (%s): %s\n", tsec, label, uri );

        return result;
    }

    private static int doBookmarks( File f, int iid, Map<String,Exception> exceptions ) throws IOException, SAXException, BookmarksException {
        List<Bookmark> books= Bookmark.parseBookmarks( f.toURI().toURL() );
        return doBookmarks( books, iid, exceptions );
    } 
    
    private static int doBookmarks( List<Bookmark> books, int iid, Map<String,Exception> exceptions ) throws IOException, SAXException, BookmarksException {
        for ( Bookmark b: books ) {
            if ( b instanceof Bookmark.Folder ) {
                iid= doBookmarks( ((Bookmark.Folder)b).getBookmarks(), iid, exceptions );
            } else {
                String uri= ((Bookmark.Item)b).getUri();
                try {
                    if ( uri.endsWith(".vap") ) {
                        
                    } else {
                        do1( uri, iid, true );
                    }
                } catch ( Exception ex ) {
                    exceptions.put( uri, ex );
                } finally {
                    iid++;
                }
            }
        }
        return iid;
    }
    
    /**
     * list of URIs.  # comments.
     * @param f
     * @param iid
     * @param exceptions
     * @return
     * @throws IOException 
     */
    private static int doHistory( File f, int iid, Map<String,Exception> exceptions ) throws IOException {
        BufferedReader read= null;
        try {
            read= new BufferedReader( new FileReader(f) );
            String s= read.readLine();
            while ( s!=null ) {
                int i= s.indexOf("#");
                if ( i>-1 ) {
                    s= s.substring(0,i);
                }
                if ( s.trim().length()>0 ) {
                    String[] ss= s.split("\t");
                    String uri= ss[ss.length-1];
                    try {
                        do1( uri, iid, true );
                    } catch ( Exception ex ) {
                        exceptions.put( uri, ex );
                    } finally {
                        iid++;                
                    }
                }
                s= read.readLine();
            }
            return iid;
        } finally {
            if ( read!=null ) read.close();
        }
    }

    /**
     * Test the list of URIs in each the URL, making a trivial way to test
     * new lists of URIs.
     * args[0] the id. (e.g. 140.)  This allows different people to be responsible for different tests.
     * args[1:] are the URLs to load.
     * @param args
     * @throws Exception 
     */
    public static void main( String[] args ) throws Exception {
        //Logger l= LoggerManager.getLogger("apdss");
        //l.setLevel( Level.ALL );
        
        if ( args.length==0 ) {
            //args= new String[] { "140", "http://www-pw.physics.uiowa.edu/~jbf/autoplot/test140.txt", "http://www.sarahandjeremy.net/~jbf/temperatures2012.xml" };
            //args= new String[] { "140", "http://www-pw.physics.uiowa.edu/~jbf/autoplot/test140.txt " };
            //args= new String[] { "140", "http://www-pw.physics.uiowa.edu/~jbf/autoplot/test140_1.txt" };
            args= new String[] { "140", "http://sarahandjeremy.net/jeremy/rbsp/test/test140.txt" };
        }
        testid= Integer.parseInt( args[0] );
        int iid= 0;

        Map<String,Exception> exceptions= new LinkedHashMap();
        
        for ( int i=1; i<args.length; i++ ) {
            String uri= args[i];
            System.err.println("\n== from "+uri+" ==");
            
            File ff= DataSetURI.getFile( uri, new NullProgressMonitor() );
            if ( uri.endsWith(".xml") ) {
                iid= doBookmarks(ff,iid,exceptions);
            } else {
                iid= doHistory(ff,iid,exceptions);
            }
            iid= ( ( iid+1 ) / 100 + 1 ) * 100;
        }
        
        System.err.println("\n\n== Exceptions encountered ====");
        
        for ( Entry<String,Exception> e: exceptions.entrySet() ) {
            System.err.println("==");
            System.err.println(e.getKey());
            System.err.println(e.getValue());
            e.getValue().printStackTrace();
        }
        
        if ( exceptions.isEmpty() ) {
            System.err.println("(none)");
            System.exit(0);  // TODO: something is firing up the event thread
        } else {
            System.err.println("("+exceptions.size()+" exceptions)");
            System.exit(1);
        }
        
    }
}
