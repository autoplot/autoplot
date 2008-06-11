/*
 * DataSetURL.java
 *
 * Created on March 31, 2007, 7:54 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.datasource;

import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.filesystem.FileObject;
import org.das2.util.filesystem.FileSystem;
import ftpfs.FTPBeanFileSystemFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.virbo.aggragator.AggregatingDataSourceFactory;

/**
 *
 * Works with DataSourceRegistry to translate a URL into a DataSource.  Also,
 * will provide completions.
 *
 * @author jbf
 *
 */

public class DataSetURL {
    
    private static int MAX_POSITIONAL_ARGS=10;
    
    static {
        DataSourceRegistry registry= DataSourceRegistry.getInstance();
        /*registry.register( new AsciiTableDataSourceFactory(), ".dat" );
        registry.register( new AsciiTableDataSourceFactory(), ".txt" );
        registry.register( ExcelSpreadsheetDataSource.getFactory(), ".xls" );
        registry.register( Das2StreamDataSource.getFactory(), ".d2s", "application/x-das2stream" );
        registry.register( Das2StreamDataSource.getFactory(), ".das2Stream", "application/x-das2stream" );
        registry.register( "org.virbo.netCDF.NetCDFDataSourceFactory", ".nc" );  // cdaweb calls cdf files "application/x-netcdf"
        registry.register( "org.virbo.netCDF.NetCDFDataSourceFactory", ".ncml" );  // cdaweb calls cdf files "application/x-netcdf"
        registry.register( DodsDataSource.getFactory(), ".dds" );
        registry.register( DodsDataSource.getFactory(), ".dods" );
        registry.register( DodsDataSource.getFactory(), ".html" );
        registry.register( "org.virbo.spase.SpaseRecordDataSourceFactory", ".xml" );*/
        discoverFactories(registry);
        discoverRegisteryEntries(registry);
    }
    static {
        FileSystem.registerFileSystemFactory( "ftp", new FTPBeanFileSystemFactory() );
    }

    
    public static class URLSplit {
             /*   path, the directory with http://www.example.com/data/
              *   file, the file, http://www.example.com/data/myfile.nc
              *   ext, the extenion, .nc
              *   params, myVariable
              */

        /**
         * the URL scheme, http, ftp, bin-http, etc.
         */
        public String scheme;
        
        /**
         * the directory with http://www.example.com/data/
         */
        public String path;
        
        /**
         * the file, http://www.example.com/data/myfile.nc
         */
        public String file;
        
        /**
         * the extenion, .nc
         */
        public String ext;
        
        /**
         * the params, myVariable&plot=0
         */
        public String params;
        
        public String toString() {
            return path + "\n"+ file + "\n"+ ext + "\n"+ params;
        }
    }
    
    public static String maybeAddFile( String surl ) {
        if ( surl.length()==0 ) return "file:/";
        
        String scheme;  // identify the scheme, if any.
        int i0= surl.indexOf(":");
        if ( i0==-1 ) {
            scheme= "";
        } else if ( i0==1 ) { // one letter scheme is assumed to be windows drive letter.
            scheme= "";
        } else {
            scheme= surl.substring(0,i0);
        }
        
        if ( scheme.equals("") ) {
            surl= "file://" + ( ( surl.charAt(0)=='/' ) ? surl : ( '/' + surl ) ); // Windows c:
            surl= surl.replaceAll("\\\\", "/" );
            surl= surl.replaceAll(" ", "%20" );
        } 
        
        return surl;
    }
    
    /**
     * split the url string (http://www.example.com/data/myfile.nc?myVariable) into:
     *   path, the directory with http://www.example.com/data/
     *   file, the file, http://www.example.com/data/myfile.nc
     *   ext, the extenion, .nc
     *   params, myVariable or null
     */
    public static URLSplit parse( String surl ) {
        URL url;
        try {
            surl= maybeAddFile( surl );
            url = new URL(surl);
            
            String file=  url.getPath();
            int i= file.lastIndexOf(".");
            String ext= i==-1 ? "" : file.substring(i);
            
            String params=null;
            
            int fileEnd;
            // check for just one ?
            i= surl.indexOf( "?" );
            if ( i != -1 ) {
                fileEnd= i;
                params= surl.substring(i+1);
                i= surl.indexOf("?",i+1);
                if ( i!=-1 ) {
                    throw new IllegalArgumentException("too many ??'s!");
                }
            } else {
                fileEnd= surl.length();
            }
            
            i= surl.lastIndexOf("/");
            String surlDir= surl.substring(0,i);
            
            int i2= surl.indexOf("://");
            
            URLSplit result= new URLSplit();
            result.scheme= url.getProtocol();
            result.path= surlDir+"/";
            result.file= surl.substring(0,fileEnd);
            result.ext= ext;
            result.params=params;
            
            return result;
            
        } catch (MalformedURLException ex) {
            return null;
            
        }
        
    }
    
    public static String format(URLSplit split) {
        String result= split.file;
        if ( split.params!=null ) result+="?"+split.params;
        return result;
    }
    /**
     * get the data source for the URL.
     * @throws IllegalArgumentException if the url extension is not supported.
     */
    public static DataSource getDataSource( URI uri ) throws Exception {
        DataSourceFactory factory = getDataSourceFactory(uri, new NullProgressMonitor());
        URL url= getWebURL(uri);
        DataSource result= factory.getDataSource( url );
        
        return result;
        
    }
    
    public static DataSource getDataSource( String surl ) throws Exception {
        return getDataSource( getURI(surl) );
    }
    
    private static boolean isAggregating( String surl ) {
        int iquest= surl.indexOf("?");
        int ipercy= surl.indexOf("%Y");
        if ( ipercy==-1 ) ipercy= surl.indexOf("%25");
        if ( ipercy != -1 && ( iquest==-1 || ipercy < iquest ) ) {
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * returns a downloadable URL from the surl, perhaps popping off the 
     * data source specifier.
     * 
     * @param surl
     * @return
     */
    public static URL getWebURL( URI url ) {
        int i= url.getScheme().indexOf(".");
        if ( i==-1 ) {
            try {
                return url.toURL();
            } catch (MalformedURLException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            String s= url.toString();
            try {
                return new URL( s.substring(i+1) );
            } catch ( MalformedURLException ex ) {
                throw new RuntimeException(ex);
            }
        }
    }
    
        
    /**
     * get the datasource factory for the URL.
     */
    public static DataSourceFactory getDataSourceFactory( URI uri, ProgressMonitor mon) throws IOException, IllegalArgumentException {
        

        if ( isAggregating( uri.toString() ) ) {
            return new AggregatingDataSourceFactory();
        }

        int i=  uri.getScheme().indexOf(".");
        if ( i!=-1 ) {
            String ext= uri.getScheme().substring(0,i);
            return DataSourceRegistry.getInstance().getSource( ext );
        }
        
        URL url= uri.toURL();
                
        String file= url.getPath();
        i= file.lastIndexOf(".");
        String ext= i==-1 ? "" : file.substring(i);
        
        // check for just one ?
        String surl= url.toString();
        i= surl.indexOf( "?" );
        if ( i != -1 ) {
            i= surl.indexOf("?",i+1);
            if ( i!=-1 ) {
                throw new IllegalArgumentException("too many ??'s!");
            }
        }
        
        DataSourceFactory factory= null;
        
        // see if we can identify it by ext, to avoid the head request.
        factory= DataSourceRegistry.getInstance().getSource( ext );
        
        if ( factory==null && url.getProtocol().equals("http") ) { // get the mime type
            mon.setTaskSize(-1);
            mon.started();
            mon.setProgressMessage("doing HEAD request to find dataset type");
            URLConnection c= url.openConnection();
            String mime= c.getContentType();
            if ( mime==null ) throw new IOException("failed to connect");
            String cd= c.getHeaderField("Content-Disposition"); // support VxOWare
            if ( cd!=null ) {
                int i0= cd.indexOf("filename=\"");
                i0+="filename=\"".length();
                int i1= cd.indexOf("\"",i0);
                String filename= cd.substring(i0,i1);
                i0= filename.lastIndexOf(".");
                ext= filename.substring(i0);
            }
            
            mon.finished();
            factory= DataSourceRegistry.getInstance().getSourceByMime( mime );
        }
        
        if ( factory==null ) factory= DataSourceRegistry.getInstance().getSource( ext );
        if ( factory==null ) {
            throw new IllegalArgumentException( "Unsupported extension: "+ext );
        }
        return factory;
    }
    
    private static int indexOf( String s, char ch, char ignoreBegin, char ignoreEnd ) {
        int i= s.indexOf(ch);
        int i0= s.indexOf(ignoreBegin);
        int i1= s.indexOf(ignoreEnd);
        if ( i!=-1 && i0<i && i<i1 ) {
            i=-1;
        }
        return i;
    }
    
    /**
     *
     * split the parameters into name,value pairs.
     *
     * items without equals (=) are inserted as "arg_N"=name.
     */
    public static LinkedHashMap<String,String> parseParams( String params ) {
        LinkedHashMap result= new LinkedHashMap();
        if ( params==null ) return result;
        if ( params.trim().equals("") ) return result;
        
        String[] ss= params.split( "&" );
        
        int argc=0;
        
        for ( int i=0;i<ss.length; i++ ) {
            int j= indexOf( ss[i], '=', '(', ')' );
            String name, value;
            if ( j==-1 ) {
                name= ss[i];
                value="";
                result.put( "arg_"+(argc++), name );
            } else {
                name= ss[i].substring(0,j);
                value= ss[i].substring(j+1);
                result.put(name,value);
            }
        }
        return result;
    }
    
    public static String formatParams(Map parms) {
        StringBuffer result= new StringBuffer("");
        for ( Iterator i=parms.keySet().iterator(); i.hasNext(); ) {
            String key= (String) i.next();
            if ( key.startsWith("arg_") ) {
                if ( !parms.get(key).equals("") ) {
                    result.append( "&" + parms.get(key) );
                }
            } else {
                String value= (String)parms.get(key);   
                if ( value!=null ) result.append("&"+key+"="+value);
            }
        }
        return ( result.length()==0 ) ? "" : result.substring(1);
    }
    
    
    /**
     * return a file reference for the url.  This is initially to fix the problem
     * for Windows where new URL( "file://c:/myfile.dat" ).getPath() -> "/myfile.dat".
     * This may eventually be how remote files are downloaded as well, and
     * may block until the file is downloaded.
     * Linux: file:/home/jbf/fun/realEstate/to1960.latlon.xls?column=C[1:]&depend0=H[1:]
     *
     */
    public static File getFile( URL url, ProgressMonitor mon ) throws IOException {
        
        URLSplit split= parse( url.toString() );
        
        String proto= url.getProtocol();
        if ( proto.equals("file") ) {
            String surl= url.toString();
            int idx1=surl.indexOf("?");
            if ( idx1 == -1 ) idx1= surl.length();
            surl= surl.substring(0,idx1);
            
            String sfile;
            int idx0=surl.indexOf("file:///");
            if( idx0==-1 ) {
                idx0= surl.indexOf("file:/");
                sfile= surl.substring( idx0+5 );
            } else {
                sfile= surl.substring( idx0+7 );
            }
            sfile= URLDecoder.decode(sfile, "US-ASCII");
            return new File( sfile );
            
        } else {
            try {
                FileSystem fs= FileSystem.create( getWebURL( new URI( split.path ) ) ) ;
                FileObject fo= fs.getFileObject( split.file.substring( split.path.length() ) );
            
                File tfile= fo.getFile(mon);
                return tfile;
            } catch ( URISyntaxException ex ) {
                throw new IOException(ex);
            }
        }
    }
    
    /**
     * canonical method for getting the URI.  If no protocol is specified, then file:// is
     * used.  Note URIs may contain prefix like bin.http://www.cdf.org/data.cdf.
     */
    public static URI getURI( String surl ) throws URISyntaxException {
        surl= maybeAddFile( surl );
        if ( surl.endsWith( "://" ) ) surl+= "/";  // what strange case is this?
        surl= surl.replaceAll("%", "%25");
        surl= surl.replaceAll(" ", "%20");
        URI result= new URI( surl );
        return result;
    }
    
    /**
     * canonical method for getting the URL.  These will always be web-downloadable 
     * URLs.
     */
    public static URL getURL( String surl ) throws MalformedURLException {
        try {
            URI uri= getURI( surl );
            return getWebURL( uri );
        } catch ( URISyntaxException ex ) {
            throw new MalformedURLException(ex.getMessage());
        }
    }
    
    public static List getExamples() {
        List result= new ArrayList();
        result.add( "L:/ct/virbo/sampexTimeL/sampex.dat?fixedColumns=90&rank2");
        result.add( "http://www.sarahandjeremy.net:8080/thredds/dodsC/testAll/poes_n15_20060111.nc.dds?proton_6_dome_16_MeV" );
        result.add( "http://www.sarahandjeremy.net:8080/thredds/dodsC/test/poesaggLittle.nc.dds?proton_6_dome_16_MeV[0:10:400000]" );
        result.add( "http://cdaweb.gsfc.nasa.gov/cgi-bin/opendap/nph-dods/istp_public/data/genesis/3dl2_gim/2003/genesis_3dl2_gim_20030501_v01.cdf.dds?Proton_Density" );
        result.add( "file://C:/iowaCitySales2004-2006.latlong.xls?column=M[1:]" );
        result.add( "file://c:/Documents and Settings/jbf/My Documents/xx.d2s" );
        result.add( "L:/fun/realEstate/to1960.latlon.xls?column=C[1:]&depend0=H[1:]" );
        result.add( "L:/fun/realEstate/to1960.latlon.xls?column=M[1:]&depend0=N[1:]&plane0=C[1:]" );
        result.add( "L:/ct/virbo/autoplot/data/610008002FE00410.20060901.das2Stream" );
        result.add( "P:/poes/poes_n15_20060212.nc?proton-6_dome_16_MeV" );
        result.add( "L:/ct/virbo/autoplot/data/asciiTab.dat" );
        result.add( "L:/ct/virbo/autoplot/data/2490lintest90005.dat" );
        result.add( "http://www.sarahandjeremy.net:8080/thredds/dodsC/test/LanlGPSAgg.nc.dds?FEIO[0:1:1000][0:0]" );
        result.add( "file://P:/cdf/fast/tms/1996/fa_k0_tms_19961021_v01.cdf?L");
        return result;
    }
    
    // "L:/ct/virbo/sampexTimeL/sampex.dat?fixedColumns=90&rank2"
    //                                          ^        ^
    public static String[] getCompletions2( String surl1, int carotPos, ProgressMonitor mon ) throws Exception {
        CompletionContext cc= new CompletionContext();
        int qpos=surl1.lastIndexOf( '?', carotPos );
        
        cc.surl= surl1;
        cc.surlpos= carotPos;
        
        DataSetURL.URLSplit split= DataSetURL.parse( surl1 );
        
        if ( qpos != -1 && qpos < carotPos ) {
            if ( qpos==-1 ) qpos= surl1.length();
            int eqpos= surl1.lastIndexOf('=',carotPos-1 );
            int amppos= surl1.lastIndexOf('&',carotPos-1 );
            if ( amppos==-1 ) amppos=qpos;
            if ( eqpos>amppos ) {
                cc.context= CompletionContext.CONTEXT_PARAMETER_VALUE;
                cc.completable= surl1.substring( eqpos+1, carotPos );
                cc.completablepos= carotPos-(eqpos+1);
            } else {
                cc.context= CompletionContext.CONTEXT_PARAMETER_NAME;
                cc.completable= surl1.substring( amppos+1, carotPos );
                cc.completablepos= carotPos-(amppos+1);
            }
        } else {
            cc.context= CompletionContext.CONTEXT_FILE;
            qpos= surl1.indexOf( '?', carotPos );
            if ( qpos==-1 ) {
                cc.completable= surl1;
            } else {
                cc.completable= surl1.substring(0,qpos);
            }
            cc.completablepos= carotPos;
        }
        
        if ( cc.context== CompletionContext.CONTEXT_PARAMETER_NAME ) {
            
            DataSourceFactory factory= getDataSourceFactory( DataSetURL.getURI( CompletionContext.get( CompletionContext.CONTEXT_FILE, cc ) ) , new NullProgressMonitor());
            if ( factory==null ) {
                throw new IllegalArgumentException("unable to find data source factory");
            }
            
            List<CompletionContext> completions= factory.getCompletions( cc , mon );
            
            // identify the implicit parameter names
            Map params= DataSetURL.parseParams( split.params );
            for ( int i=0; i<3; i++ ) {
                String arg= (String)params.get("arg_"+i);
                if ( arg!=null ) {
                    for ( CompletionContext cc1:completions  ) {
                        if ( cc1.context==CompletionContext.CONTEXT_PARAMETER_NAME && cc1.implicitName!=null && cc1.completable.equals(arg) ) {
                            params.put( cc1.implicitName, arg );
                        }
                    }
                }
            }
                        
            List<String> result= new ArrayList<String>();
            int i=0;
            for ( CompletionContext cc1:completions ) {
                String paramName= cc1.implicitName!=null ? cc1.implicitName : cc1.completable;
                if ( paramName.indexOf("=")!=-1 ) paramName= paramName.substring(0,paramName.indexOf("="));
                boolean dontYetHave= !params.containsKey(paramName) ;
                boolean startsWith= cc1.completable.startsWith(cc.completable);
                if ( startsWith ) {
                    HashMap paramsCopy= new LinkedHashMap(params);
                    if ( cc1.implicitName!=null ) {
                        paramsCopy.put( cc1.implicitName, cc1.completable );
                    } else {
                        paramsCopy.put( paramName, "" );
                    }
                    String ss= split.file + "?" + DataSetURL.formatParams(paramsCopy);
                    //String ss= CompletionContext.insert( cc, cc1 );
                    if ( dontYetHave==false ) continue;  // skip it
                    result.add( ss );
                    i=i+1;
                }
            }
            return result.toArray( new String[result.size()] );
            
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            
            DataSourceFactory factory= getDataSourceFactory( DataSetURL.getURI( CompletionContext.get( CompletionContext.CONTEXT_FILE, cc ) ) , mon );
            if ( factory==null ) {
                throw new IllegalArgumentException("unable to find data source factory");
            }
            
            List<CompletionContext> completions= factory.getCompletions( cc , mon );
            List<String> result= new ArrayList<String>();
            int i=0;
            for ( CompletionContext cc1:completions ) {
                if ( cc1.completable.startsWith(cc.completable) ) {
                    String ss= CompletionContext.insert( cc, cc1 );
                    result.add( ss );
                    i=i+1;
                }
            }
            return result.toArray( new String[result.size()] );
            
        } else {
            String[] result;
            try {
                
                mon.setProgressMessage("listing directory");
                mon.started();
                String surl= CompletionContext.get( CompletionContext.CONTEXT_FILE, cc );
                
                int i= surl.lastIndexOf("/",carotPos);
                String surlDir;
                if ( i<=0 || surl.charAt(i-1)=='/' ) {
                    surlDir=surl;
                } else {
                    surlDir= surl.substring(0,i);
                }
                URI url= getURI(surlDir);
                String prefix= surl.substring(i+1,carotPos);
                FileSystem fs= FileSystem.create( getWebURL(url) );
                String[] s= fs.listDirectory("/");
                mon.finished();
                List<String> result1= new ArrayList<String>(s.length);
                for ( int j=0; j<s.length; j++ ) {
                    if ( s[j].startsWith(prefix) ) {
                        CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_FILE, surlDir+"/"+s[j] );
                        result1.add( CompletionContext.insert( cc, cc1 ) );
                    }
                }
                result= result1.toArray( new String[result1.size()] );
            } catch ( MalformedURLException ex ) {
                result= new String[] { "Malformed URI" };
            } catch ( FileSystem.FileSystemOfflineException ex) {
                result= new String[] {"FileSystem offline" };
            }
            return result;
        }
        
    }

    private static void discoverFactories( DataSourceRegistry registry ) {

        // discover Factories on the path
        try {
            Enumeration<URL> urls = DataSetURL.class.getClassLoader().getResources("META-INF/org.virbo.datasource.DataSourceFactory");
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
                String s = reader.readLine();
                while (s != null) {
                    if (s.trim().length() > 0) {
                        List<String> extensions = null;
                        List<String> mimeTypes = null;
                        String factoryClassName = s;
                        try {
                            Class c = Class.forName(factoryClassName);
                            DataSourceFactory f = (DataSourceFactory) c.newInstance();
                            try {
                                Method m = c.getMethod("extensions", new Class[0]);
                                extensions = (List<String>) m.invoke( f, new Object[0] );
                            } catch (NoSuchMethodException ex) {
                            } catch (InvocationTargetException ex) {
                                ex.printStackTrace();
                            }
                            try {
                                Method m = c.getMethod("mimeTypes", new Class[0]);
                                mimeTypes = (List<String>) m.invoke( f, new Object[0] );
                            } catch (NoSuchMethodException ex) {
                            } catch (InvocationTargetException ex) {
                                ex.printStackTrace();
                            }
                        } catch (ClassNotFoundException ex) {
                            ex.printStackTrace();
                        } catch (InstantiationException ex) {
                            ex.printStackTrace();
                        } catch (IllegalAccessException ex) {
                            ex.printStackTrace();
                        }

                        if (extensions != null) {
                            for (String e : extensions) {
                                registry.register(factoryClassName, e);
                            }
                        }

                        if (mimeTypes != null) {
                            for (String m : mimeTypes) {
                                registry.registerMimeType(factoryClassName, m);
                            }
                        }
                    }
                    s = reader.readLine();
                }
                reader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static void discoverRegisteryEntries(DataSourceRegistry registry) {
        try {
            Enumeration<URL> urls = DataSetURL.class.getClassLoader().getResources("META-INF/org.virbo.datasource.DataSourceFactory.extensions");
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
                String s = reader.readLine().trim();
                while (s != null) {
                    if ( s.length() > 0 ) {
                        String[] ss= s.split("\\s");
                        for ( int i=1; i<ss.length; i++ ) {
                            registry.register( ss[0], ss[i] );
                        }
                    }
                    s = reader.readLine();
                }
                reader.close();
            }
            
            urls = DataSetURL.class.getClassLoader().getResources("META-INF/org.virbo.datasource.DataSourceFactory.mimeTypes");
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
                String s = reader.readLine().trim();
                while (s != null) {
                    if ( s.length() > 0 ) {
                        String[] ss= s.split("\\s");
                        for ( int i=1; i<ss.length; i++ ) {
                            registry.registerMimeType( ss[0], ss[i] );
                        }
                    }
                    s = reader.readLine();
                }
                reader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }        
    }
    
    /** call this to trigger initialization */
    public static void init() {
        
    }
    
/*    public static String[] getCompletions( String surl , ProgressMonitor mon) throws Exception {
        String[] result;
        try {
            
            URLSplit split= parse( surl );
            if ( surl.contains("?") ) {
                int i= surl.indexOf("?");
                DataSourceFactory factory= getDataSourceFactory( Util.newURL( Util.FS_URL, surl ) , new NullProgressMonitor());
                mon.setProgressMessage("getting completions for "+factory);
                mon.started();
                
                result = factory.getCompletions(surl.substring(0,i+1));
                
            } else {
                try {
                    mon.setProgressMessage("listing directory");
                    mon.started();
                    int i= surl.lastIndexOf("/");
                    String surlDir;
                    if ( i<=0 || surl.charAt(i-1)=='/' ) {
                        surlDir=surl;
                    } else {
                        surlDir= surl.substring(0,i);
                    }
                    URL url= getURL(surlDir);
                    String prefix= surl.substring(i+1);
                    FileSystem fs= FileSystem.create(url);
                    String[] s= fs.listDirectory("/");
                    List<String> result1= new ArrayList<String>(s.length);
                    for ( int j=0; j<s.length; j++ ) {
                        if ( s[j].startsWith(prefix) ) {
                            result1.add(surlDir+"/"+s[j]);
                        }
                    }
                    result= result1.toArray( new String[result1.size()] );
                } catch ( MalformedURLException ex ) {
                    result= new String[] { "Malformed URL" };
                } catch ( FileSystem.FileSystemOfflineException ex) {
                    result= new String[] {"FileSystem offline" };
                }
            }
        } catch ( Exception e ) {
            e.printStackTrace();
            result= new String[] { e.getMessage() };
        } finally {
            mon.finished();
        }
        return result;
    }
    */
}

