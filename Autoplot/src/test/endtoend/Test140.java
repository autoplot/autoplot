
package test.endtoend;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import javax.imageio.ImageIO;
import org.autoplot.AutoplotUtil;
import org.das2.util.filesystem.HtmlUtil;
import org.das2.util.monitor.CancelledOperationException;
import org.das2.util.monitor.NullProgressMonitor;
import org.autoplot.ScriptContext;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;

import static org.autoplot.ScriptContext.*;
import org.autoplot.bookmarks.Bookmark;
import org.autoplot.bookmarks.BookmarksException;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.dom.Application;
import org.autoplot.dom.DataSourceFilter;
import org.autoplot.state.StatePersistence;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.URISplit;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.qds.SemanticOps;
import org.das2.system.DefaultMonitorFactory;
import org.das2.system.MonitorFactory;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
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
    
    private static void listAllPendingTasks() {
        MonitorFactory mf= getDocumentModel().getController().getMonitorFactory();
        if ( mf instanceof DefaultMonitorFactory ) {
            DefaultMonitorFactory dmf= (DefaultMonitorFactory)mf;
            DefaultMonitorFactory.MonitorEntry[] mes= dmf.getMonitors();
            for ( DefaultMonitorFactory.MonitorEntry me: mes ) {
                ProgressMonitor m= me.getMonitor();
                if ( !( m.isCancelled() || m.isFinished() ) ) {
                    System.err.println( m );  // sometimes we can catch one!
                }
            }
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
    private static String do1( String uri, int iid, boolean doTest, boolean isPublic ) throws Exception {

        System.err.printf( "== %03d %03d ==\n", testid, iid );
        if ( isPublic ) {
            System.err.printf( "uri: %s\n", uri );
        } else {
            System.err.printf( "uri: (uri is not public)\n" );
        }

        String label= String.format( "test%03d_%03d", testid, iid );

        double tsec,psec;
        long t0= System.currentTimeMillis();
        tsec= t0; // for non-vap non-uri
        psec= t0;
        
        QDataSet ds=null;
        if ( uri.endsWith(".vap") || uri.contains(".vap?timerange=") ) {
            if ( isPublic ) {
                URISplit split= URISplit.parse(uri);
                try ( InputStream in = DataSetURI.getInputStream( split.resourceUri, new NullProgressMonitor() ) ) {
                    Application dom= StatePersistence.restoreState( in, URISplit.parseParams( split.params ) );
                    for ( DataSourceFilter dsf : dom.getDataSourceFilters() ) {
                        System.err.printf( "  %s: %s\n", dsf.getId(), dsf.getUri() );
                    }
                    System.err.println( "  timerange: "+ dom.getTimeRange() );
                }
            }
                    
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
            ds= org.autoplot.jythonsupport.Util.getDataSet( uri );
            tsec= (System.currentTimeMillis()-t0)/1000.;
            if ( ds!=null ) {
                if ( isPublic ) {

                    Units u= SemanticOps.getUnits(ds);
                    if ( !UnitsUtil.isNominalMeasurement(u) ) {
                        MutablePropertyDataSet hist= (MutablePropertyDataSet) Ops.autoHistogram(ds);
                        hist.putProperty( QDataSet.TITLE, uri );

                        hist.putProperty( QDataSet.LABEL, label );
                        formatDataSet( hist, label+".qds");        
                    }

                    QDataSet dep0= (QDataSet) ds.property( QDataSet.DEPEND_0 );
                    if ( dep0!=null ) {
                        MutablePropertyDataSet hist2= (MutablePropertyDataSet) Ops.autoHistogram(dep0);
                        formatDataSet( hist2, label+".dep0.qds");
                    } else {
                        try (PrintWriter pw = new PrintWriter( label+".dep0.qds" )) {
                            pw.println("no dep0");
                        }
                    }
                } else {
                    System.err.println("TODO Turkey: Make a hash of the .qds of the data");
                }

                listAllPendingTasks();
                
                reset();
                plot( ds );
                setCanvasSize( 450, 300 );
                getDocumentModel().getOptions().setCanvasFont("sans-10");
                getDocumentModel().getCanvases(0).getMarginColumn().setLeft("5.0em");
                getDocumentModel().getCanvases(0).getMarginColumn().setRight("100.00%-10.0em");
                
                int i= uri.lastIndexOf("/");

                getApplicationModel().waitUntilIdle();

                String fileUri= uri.substring(i+1);

                if ( !getDocumentModel().getPlotElements(0).getComponent().equals("") ) {
                    String dsstr= String.valueOf( getDocumentModel().getDataSourceFilters(0).getController().getDataSet() );
                    fileUri= fileUri + " " + dsstr +" " + getDocumentModel().getPlotElements(0).getComponent();
                }

                setTitle(fileUri);
                
            } else {
                throw new IllegalArgumentException("uri results in null dataset: "+uri );
                
            }
            psec= (System.currentTimeMillis()-t0)/1000.;
        }

        if ( isPublic ) {
            System.err.println( "dataset: "+ds );
        } else {
            System.err.println( "dataset: (data is not public)" );
        }
        
        String result;

        String name;
        if ( doTest ) {            
            String id= URLEncoder.encode( uri, "US-ASCII" );
            id= id.replaceAll("%3A", "" );
            id= id.replaceAll("%2F%2F", "_" );
            id= id.replaceAll("%[0-9A-F][0-9A-F]","_");
            //id= id.replaceAll("%2F","_");
            //id= id.replaceAll("%3F","_");
            //id= id.replaceAll("%26","_");
            //id= id.replaceAll("%7E","_"); // twiddle
            //id= id.replaceAll("%2B","_"); // colon
            //id= id.replaceAll("=","_");
            if ( id.length()>150 ) { // ext4 filename length limits...
                id= id.substring(0,150) + "..." + String.format( "%016d", id.hashCode() );
            }
            if ( !isPublic ) {
                id= String.format("%08x",id.hashCode());
            }
            name= String.format( "test%03d_%s", testid, id );
            result= name;
        } else {
            name= String.format( "ex_test%03d_%03d", testid, iid );
            result= null;
        }
        
        String name1= getUniqueFilename( name, ".png", true );
        
        if ( isPublic ) {
            writeToPng( name1 );
        } else {
            writeToPng( "/home/jbf/ct/hudson/private/test/"+name1 );
            int width= getApplicationModel().getDocumentModel().getController().getCanvas().getWidth();
            int height= getApplicationModel().getDocumentModel().getController().getCanvas().getHeight();
            BufferedImage image = getApplicationModel().getDocumentModel().getController().getCanvas().getController().getDasCanvas().getImage( width, height );
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", outputStream);
            byte[] data = outputStream.toByteArray();
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(data);
            byte[] hash = md.digest();
            try (PrintStream bout = new PrintStream( new FileOutputStream( getUniqueFilename( name, ".txt", true ) ) )) {
                for ( byte b: hash ) {
                    bout.println(String.format("%03d",b));
                }
            }
        }
        
        if ( isPublic ) {
            System.err.printf( "wrote to file: %s\n", name1 );
            System.err.printf( "Read in %9.3f seconds (%s): %s\n", tsec, label, uri );
            System.err.printf( "Plot in %9.3f seconds (%s): %s\n", psec, label, uri );
        } else {
            System.err.printf( "wrote to file: %s\n", "/home/jbf/ct/hudson/private/test/"+name1  );
            System.err.printf( "Read in %9.3f seconds (%s): %s\n", tsec, label, "(uri is not public)" );
            System.err.printf( "Plot in %9.3f seconds (%s): %s\n", psec, label, "(uri is not public)" );            
        }

        if ( uri.endsWith(".vap") || uri.contains(".vap?timerange=") ) {
            reset();
            ScriptContext.getDocumentModel().getOptions().setColor( Color.BLACK );
            ScriptContext.getDocumentModel().getOptions().setBackground( Color.WHITE );
        }
        
        return result;
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
        try (InputStream in = url.openStream()) {
            URL[] urls= HtmlUtil.getDirectoryListing(url,in,false);
            List<URL> result= new ArrayList<>();
            List<String> sresult= new ArrayList<>();
            for ( URL url1: urls ) {
                if ( url1.getFile().endsWith(".vap") || url1.getFile().contains("autoplot.jnlp?") ) {
                    String s= url1.toString();
                    if ( s.startsWith("http://autoplot.org/autoplot.jnlp?") ) {
                        s= s.substring(34);
                    }
                    s= s.replaceAll(" ","+");
                    s= DataSourceUtil.unescape(s);
                    sresult.add(s);
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
    
    /**
     * list of URIs.  # comments.
     * @param f
     * @param iid
     * @param exceptions
     * @return
     * @throws IOException 
     */
    private static int doHistory( File f, int iid, Map<String,Exception> exceptions, Map<String,Integer> exceptionNumbers ) throws IOException {
        try (BufferedReader read = new BufferedReader( new InputStreamReader( new FileInputStream(f), "UTF-8" ) )) {
            String s= read.readLine();
            System.err.println(">> doHistory " +s);
            while ( s!=null ) {
                int i= s.indexOf('#');
                if ( i>-1 ) {
                    s= s.substring(0,i);
                }
                s= s.trim();
                if ( s.length()>0 ) {
                    //if ( iid==17 ) {
                    //    System.err.println("Here at doHistory #"+iid+": "+s);
                    //}
                    String[] ss= s.split("\t");
                    String uri= ss[ss.length-1].trim();
                    
                    // private URIs should be <id>TAB<URI> or <character>SPACE<URI>
                    boolean publc= true;
                    if ( ss.length>1 ) {
                        boolean isPrivate= ss[ss.length-2].trim().startsWith("x");
                        publc= !isPrivate;
                    }
                    if ( uri.startsWith("x ") ) {
                        uri= uri.substring(2).trim();
                        publc= false;
                    }
                    try {
                        do1(uri, iid, true, publc );
                    } catch ( Exception ex ) {
                        exceptions.put( uri, ex );
                        exceptionNumbers.put( uri, iid );
                    } finally {
                        iid++;                
                    }
                }
                s= read.readLine();
            }
            return iid;
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
        
        //LoggerManager.readConfiguration( AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA) + "config/logging.properties" );
        //Logger.getLogger("autoplot.dom.canvas").info("info level");
        //Logger.getLogger("autoplot.dom.canvas").finer("finer level");
        
        System.err.println("disable certificate checking");
        AutoplotUtil.disableCertificates();
        
        System.err.println("home (prefs): " + System.getProperty("user.home") );
        System.err.println("autoplot_data: "+AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA));
        System.err.println("fscache: "+AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_FSCACHE));
        System.err.println("reading logger configuration from System.getProperty(\"java.util.logging.config.file\"): " + System.getProperty("java.util.logging.config.file") );
        LoggerManager.readConfiguration();
        
        if ( args.length==0 ) {
            //args= new String[] { "140", "http://www-pw.physics.uiowa.edu/~jbf/autoplot/test140.txt", "http://www.sarahandjeremy.net/~jbf/temperatures2012.xml" };
            //args= new String[] { "140", "http://www-pw.physics.uiowa.edu/~jbf/autoplot/test140.txt " };
            //args= new String[] { "140", "http://www-pw.physics.uiowa.edu/~jbf/autoplot/test140_1.txt" };
            //args= new String[] { "140", "http://sarahandjeremy.net/jeremy/rbsp/test/test140.txt" };
            //args= new String[] { "143", "http://www.rbsp-ect.lanl.gov/science/VAP_Files.php" };
            //args= new String[] { "144", "http://autoplot.org/developer.vapModifiers" };
            //args= new String[] { "145", "http://sarahandjeremy.net/~jbf/" };
            //args= new String[] { "146", "http://sarahandjeremy.net/jeremy/autoplot/tests/test140/html/RBSP%20ECT%20Data%20Products.html" };
            //args= new String[] { "142", "http://jfaden.net/~jbf/autoplot/test142.txt" };
            //args= new String[] { "147", "http://autoplot.org//developer.listOfUris" };
            //args= new String[] { "148", "http://www-pw.physics.uiowa.edu/~jbf/pdsppi/examples/pdsppi.xml" };
            //args= new String[] { "149", "http://sarahandjeremy.net/~jbf/" };
            args= new String[] { "099", "/home/jbf/ct/hudson/test099.txt" };
        }
        testid= Integer.parseInt( args[0] );
        int iid= 0;

        Map<String,Exception> exceptions= new LinkedHashMap();
        Map<String,Integer> exceptionNumbers= new LinkedHashMap();
        
        ScriptContext.getDocumentModel().getOptions().setAutolayout(false);

        for ( int i=1; i<args.length; i++ ) {
            String uri= args[i];
            System.err.println("\n=======================");
            System.err.println("== from "+uri);
            System.err.println("=======================\n");
            
            if ( uri.endsWith(".xml") ) {
                File ff= DataSetURI.getFile( uri, new NullProgressMonitor() );
                iid= doBookmarks(ff,iid,exceptions,exceptionNumbers);
            } else if ( uri.endsWith(".txt") ) {
                File ff= DataSetURI.getFile( uri, new NullProgressMonitor() );
                iid= doHistory(ff,iid,exceptions,exceptionNumbers);
            } else {
                iid= doHtml( new URL(uri),iid,exceptions,exceptionNumbers );
            }
            iid= ( ( iid+1 ) / 100 + 1 ) * 100;
        }
        
        System.err.println("\n\n=== Exceptions encountered ==============");
        
        for ( Entry<String,Exception> e: exceptions.entrySet() ) {
            System.err.println( String.format( "== %4d: %s ==", exceptionNumbers.get(e.getKey()), e.getKey() ) );
            e.getValue().printStackTrace();
        }
        
        if ( exceptions.isEmpty() ) {
            System.err.println("(none)\n\n");
            System.exit(0);  // TODO: something is firing up the event thread
        } else {
            System.err.println("("+exceptions.size()+" exceptions)\n\n");
            System.exit(1);
        }
        
        System.err.println("\n\n==============");
        
    }
}
