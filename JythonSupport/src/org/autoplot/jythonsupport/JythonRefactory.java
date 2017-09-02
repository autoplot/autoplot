
package org.autoplot.jythonsupport;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.util.LoggerManager;
import org.autoplot.datasource.DataSourceUtil;

/**
 * Provide one class that manages backwards compatibility as package names
 * are changed.  See https://sourceforge.net/p/autoplot/feature-requests/528/
 * @author jbf
 */
public class JythonRefactory {
    
    private static final Logger logger= LoggerManager.getLogger("jython.refactory");
            
//    /**
//     * map imports within file to the new names.
//     * @param f jython script
//     * @return new script.
//     */
//    public static File fixImports( File f ) throws IOException {
//        FileInputStream fin= new FileInputStream(f);
//        InputStream out= fixImports( fin );
//        File fileout= File.createTempFile( "autoplot.jythonrefactory", ".jy" );
//        fileout.deleteOnExit();
//        FileOutputStream fout= new FileOutputStream(fileout);
//        DataSourceUtil.transfer( out, fout );
//        return fileout;
//    }
    
    public static String fixImports( String s ) throws IOException {
        InputStream fin= new ByteArrayInputStream( s.getBytes(Charset.forName("US-ASCII")) );
        InputStream out= fixImports( fin );
        ByteArrayOutputStream baos= new ByteArrayOutputStream(s.length()*110/100);
        DataSourceUtil.transfer( out, baos );
        String result= baos.toString("US-ASCII");
        return result;
    }
    
    /**
     * return the reverse of the map.
     * @param map 
     * @return 
     */
    private static Map<String,String> reverseMap( Map<String,String> map ) {
        HashMap<String,String> result= new HashMap<>(map.size());
        for ( Entry<String,String> e: map.entrySet() ) {
            result.put( e.getValue(), e.getKey() );
        }
        return result;
    }
    
    private static final Map<String,String> forwardMap;
    
    static {
        // map of old name to new name.  Note org.virbo will match anything 
        // underneath it, but more specific maps are added for efficiency.
        HashMap<String,String> m= new HashMap<>();
        m.put("org.virbo.dataset", "org.das2.qds");
        m.put("org.qdataset", "org.das2.qds");
        m.put("org.virbo.dataset.examples", "org.das2.qds.examples" );
        m.put("org.virbo","org.autoplot");
        m.put("org.virbo.autoplot", "org.autoplot");
        m.put("org.virbo.autoplot.dom", "org.autoplot.dom");
        m.put("org.virbo.autoplot.bookmarks", "org.autoplot.bookmarks");
        m.put("org.virbo.autoplot.state", "org.autoplot.state");
        m.put("org.virbo.datasource", "org.autoplot.datasource");
        m.put("org.autoplot.bufferdataset", "org.das2.qds.buffer");
        m.put("org.virbo.dsutil", "org.das2.qds.util");
        m.put("org.virbo.dsops", "org.das2.qds.ops");
        m.put("org.virbo.filters", "org.das2.qds.filters");
        m.put("org.virbo.qstream", "org.das2.qstream");
        m.put("org.qstream.filter", "org.das2.qstream.filter");
        m.put("org.virbo.ascii", "org.autoplot.ascii");
        m.put("org.virbo.das2Stream", "org.autoplot.das2stream");
        m.put("org.virbo.spase", "org.autoplot.spase");
        m.put("org.virbo.imagedatasource", "org.autoplot.imagedatasource" );
        m.put("org.virbo.idlsupport", "org.autoplot.idlsupport" );
        m.put("org.virbo.jythonsupport", "org.autoplot.jythonsupport");
        m.put("org","org");  //mark that some things under org will change.
        m.put("autoplot","autoplot2017");
        m.put("autoplotapp","autoplotapp2017");
        //m.put("zipfs", "org.das2.util.filesystem.zipfs");
        //forwardMap = new HashMap<>();
        //forwardMap = reverseMap(m);   
        forwardMap = m;   
    }
    
    private static final Map<String,String> fullNameMap= new HashMap<>();    
    
    private static final Pattern IMPORT_REGEX= Pattern.compile("(\\s*)from(\\s+)([a-zA-Z0-9_.]+)(\\s+)import(\\s+)([a-zA-Z0-9_ ,\\*(]+)(\\s*)");
    private static final Pattern IMPORT_AS_REGEX= Pattern.compile("(\\s*)import(\\s+)([a-zA-Z0-9_.]+)(\\s*)((\\s+)as(\\s+)([a-zA-Z0-9_]+)(\\s*))?");
    
    private static String magicMatch( String p ) {
        String n="";
        String cl="";
        String[] ss= p.split("\\.",-2);
        int i= p.length();
        for ( int k=ss.length; k>0; k-- ) {
            String path= p.substring(0,i);
            n= forwardMap.get(path);
            if ( n==null ) {
                i= i-ss[k-1].length()-1;
            } else {
                cl= p.substring(i);
                p= path;
                break;
            }
        }
        return n+cl;
    }
    
    /**
     * read in the stream, replacing import statements with new packages.
     * @param in the input stream containing Jython code.
     * @return new stream, approximately the same length and same number of lines.
     * @throws IOException 
     */
    public static InputStream fixImports( InputStream in ) throws IOException {
        long t0= System.currentTimeMillis();
        
        boolean affected= false;
        
        LineNumberReader reader= new LineNumberReader( new InputStreamReader(in) );
        String line= reader.readLine(); 
        ByteArrayOutputStream baos= new ByteArrayOutputStream(10000);
        PrintStream writer= new PrintStream( baos );
        while ( line!=null ) {
            Matcher m;
            //if ( line.contains( "org.virbo.jythonsupport.ui.Util.FormData" ) ) {
            //    System.err.println("here114");
            //}
            
            int ibs= line.indexOf(".BoxSelected");
            if ( ibs>-1 ) {
                line= line.substring(0,ibs) + ".boxSelected" + line.substring(ibs+12);
                affected= true;
                logger.log(Level.WARNING, "fixImports found use of old .BoxSelected method" );
            }
            
            m= IMPORT_REGEX.matcher(line);
            if ( m.matches() ) {
                String p= m.group(3);
                String cl= null;
                String n=null;
                String[] ss= p.split("\\.",-2);
                int i= p.length();
                for ( int k=ss.length; k>0; k-- ) {
                    String path= p.substring(0,i);
                    n= forwardMap.get(path);
                    if ( n==null ) {
                        i= i-ss[k-1].length()-1;
                    } else {
                        cl= p.substring(i);
                        break;
                    }
                }
                if ( n!=null ) {
                    writer.print( m.group(1) );
                    writer.print( "from" );
                    writer.print( m.group(2) );
                    writer.print( n );
                    if ( cl!=null ) {
                        writer.print( cl );
                    }
                    writer.print( m.group(4) );
                    writer.print( "import" );
                    writer.print( m.group(5) );
                    writer.print( m.group(6) );
                    writer.print( m.group(7) );
                    writer.println();
                    if ( cl!=null ) {
                        if ( !p.equals( n+cl ) ) {
                            affected= true;
                        }
                    } else {
                        if ( !p.equals( n ) ) {
                            affected= true;
                        }
                    }
                } else {
                    writer.println(line);
                }
            } else {
                m= IMPORT_AS_REGEX.matcher(line);
                if ( m.matches() ) {
                    String p= m.group(3);
                    String cl= null;
                    String n=null;
                    String[] ss= p.split("\\.",-2);
                    int i= p.length();
                    for ( int k=ss.length; k>0; k-- ) {
                        String path= p.substring(0,i);
                        n= forwardMap.get(path);
                        if ( n==null ) {
                            i= i-ss[k-1].length()-1;
                        } else {
                            cl= p.substring(i);
                            p= path;
                            break;
                        }
                    }
                    if ( n!=null ) {
                        writer.print( m.group(1) );
                        writer.print( "import" );
                        writer.print( m.group(2) );
                        writer.print( n );
                        if ( cl!=null ) {
                            writer.print( cl );
                        }
                        writer.print( m.group(4) );
                        if ( m.group(5)!=null ) { // as clause
                            writer.print( m.group(5) ); 
                        } else {
                            for ( int k=ss.length; k>0; k-- ) {
                                String path= p.substring(0,i);
                                n= forwardMap.get(path);
                                if ( n!=null && n.equals(p) ) {
                                    fullNameMap.put( p, n );
                                }
                            }
                            fullNameMap.put( p+cl, n+cl );
                        }
                        writer.println();
                        if ( !p.equals(n) ) {
                            affected= true;
                        }
                    } else {
                        writer.println(line);
                    }
                } else {
                    if ( fullNameMap.size()>0 ) {
                        for ( Entry<String,String> e: fullNameMap.entrySet() ) {
                            Pattern identifierP= Pattern.compile("([a-zA-Z0-9\\.\\_]+)");
                            String skey= e.getKey(); //BUG need to prefix with space or chindex=0.
                            int i= line.indexOf(skey);
                            while ( i>-1 ) {
                                Matcher matcher= identifierP.matcher(line.substring(i));
                                if ( matcher.find() ) { // should be true
                                    String mehave= matcher.group(1);
                                    String mewant= magicMatch(mehave);
                                    line= line.replace( mehave, mewant );
                                    i= line.indexOf( skey, i+mewant.length() );
                                    if ( !mehave.equals(mewant ) ) {
                                        affected= true;
                                    }
                                } else {
                                    logger.warning("something has gone terribly wrong at JythonRefactory line 233");
                                    i= -1; //
                                }
                            }
                            
                        }
                    }
                    writer.println(line);
                }
            }
            line= reader.readLine(); 
        }
        if (affected) {
            logger.log(Level.WARNING, "fixImports in {0}ms, affected={1}.  Code contains imports with old (\"virbo\") names.", new Object[] { System.currentTimeMillis()-t0, affected } );
        } else {
            logger.log(Level.FINE, "fixImports in {0}ms, affected={1}", new Object[] { System.currentTimeMillis()-t0, affected } );
        }
        return new ByteArrayInputStream( baos.toByteArray() );
    }
    
    public static void main( String[] args ) throws IOException {
        //File f= fixImports( new File( "/home/jbf/ct/autoplot/rfe/528/examples/rfe528.okay.jy") );
        //URL url = new URL( "file:///home/jbf/project/juno/svn/studies/jbf/trajPlot/finalPlotSouth.jy" );
        //URL url = new URL( "file:///home/jbf/project/juno/svn/studies/jbf/u/george/20170207/junoPolarPlot.jy");
        //URL url = new URL("http://jfaden.net/~jbf/autoplot/rfe/528/rfe528.20160909.okay.jy");
        
        System.err.println( magicMatch("org.virbo.autoplot.RenderType") );
        
        //URL url = new URL("http://jfaden.net/~jbf/autoplot/rfe/528/orgImport.jy");
        URL url = new URL("http://emfisis.physics.uiowa.edu/team/jyds/filterParm.jyds");
        //URL url= new URL("file:/home/jbf/ct/hudson/script/test037/3577243.jy");
        InputStream in= fixImports( url.openStream() );
        BufferedReader r= new BufferedReader(new InputStreamReader(in));
        String line;
        while ( (line= r.readLine())!=null ) {
            System.err.println(line);
        }

    }
}
