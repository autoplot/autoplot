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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Logger;
import org.das2.DasApplication;
import org.das2.util.filesystem.FileSystemSettings;
import org.das2.util.filesystem.LocalFileSystem;
import org.virbo.aggragator.AggregatingDataSourceEditorPanel;
import org.virbo.aggragator.AggregatingDataSourceFactory;
import org.virbo.datasource.datasource.DataSourceFormat;

/**
 *
 * Works with DataSourceRegistry to translate a URL into a DataSource.  Also,
 * will provide completions.
 *
 * @author jbf
 *
 */
public class DataSetURL {

    private static Logger logger= Logger.getLogger("virbo.datasource");
    static {
        DataSourceRegistry registry = DataSourceRegistry.getInstance();
        discoverFactories(registry);
        discoverRegistryEntries(registry);
    }
    

    static {
        FileSystem.registerFileSystemFactory("zip", new zipfs.ZipFileSystemFactory());
        FileSystem.registerFileSystemFactory("ftp", new FTPBeanFileSystemFactory());
        FileSystem.settings().setPersistence(FileSystemSettings.Persistence.EXPIRES);

        if (DasApplication.hasAllPermission()) {
            File apDataHome = new File(System.getProperty("user.home"), "autoplot_data");
            FileSystem.settings().setLocalCacheDir(apDataHome);
        }
    }
    static WeakHashMap<DataSource, DataSourceFactory> dsToFactory = new WeakHashMap<DataSource, DataSourceFactory>();

    /**
     * returns the explicit extension, or the file extension if found, or null.
     * The extension will not contain a period.
     * @param surl
     * @return the extension found, or null if no period is found in the filename.
     */
    public static String getExt(String surl) {
        String explicitExt = getExplicitExt(surl);
        if (explicitExt != null) {
            return explicitExt;
        } else {
            URLSplit split = URLSplit.parse(surl);
            if (split.file != null) {

                int i0 = split.file.lastIndexOf('/');
                if (i0 == -1) return null;
                int i1 = split.file.lastIndexOf('.');
                if (i1!=-1 && i1>i0) {
                    return split.file.substring(i1 + 1);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
    }

    /**
     * return the extension prefix of the URI, if specified.  
     * @param surl
     * @return null or an extension like "tsds"
     */
    public static String getExplicitExt(String surl) {
        URLSplit split = URLSplit.parse(surl);
        int i = split.vapScheme.indexOf("+");
        if (i != -1) {
            return split.vapScheme.substring(i + 1);
        } else {
            return null;
        }
    }

    /**
     * split the url string (http://www.example.com/data/myfile.nc?myVariable) into:
     *   path, the directory with http://www.example.com/data/
     *   file, the file, http://www.example.com/data/myfile.nc
     *   ext, the extenion, .nc
     *   params, myVariable or null
     * @deprecated use URLSplit.parse(surl);
     */
    public static URLSplit parse(String surl) {
        return URLSplit.parse(surl);
    }

    /**
     * @deprecated use URLSplit.format(split);
     */
    public static String format(URLSplit split) {
        return URLSplit.format(split);
    }

    /**
     * get the data source for the URL.
     * @throws IllegalArgumentException if the url extension is not supported.
     */
    public static DataSource getDataSource(URI uri) throws Exception {
        DataSourceFactory factory = getDataSourceFactory(uri, new NullProgressMonitor());
        URI ruri = getResourceURI(uri);
        DataSource result = factory.getDataSource(ruri.toURL());  // TODO: this doesn't support jdbc:mysql:...
        dsToFactory.put(result, factory);
        return result;

    }

    public static DataSource getDataSource(String surl) throws Exception {
        return getDataSource(getURI(surl));
    }

    /**
     * Prefix the URL with the datasource extension if necessary, so that
     * the URL would resolve to the dataSource.  This is to support TimeSeriesBrowse,
     * and any time a resouce URL must be understood out of context.
     * 
     * @param surl
     * @param dataSource
     * @return
     */
    public static String getDataSourceUri(DataSource ds) {
        DataSourceFactory factory = dsToFactory.get(ds);
        if (factory instanceof AggregatingDataSourceFactory) {
            return ds.getURL();
        }
        if (factory == null) {
            return ds.getURL();  // nothing we can do
        } else {
            URLSplit split = URLSplit.parse(ds.getURL());
            String fext;
            fext = DataSourceRegistry.getInstance().getExtensionFor(factory).substring(1);
            if (DataSourceRegistry.getInstance().hasSourceByExt(split.ext)) {
                DataSourceFactory f2 = DataSourceRegistry.getInstance().getSource(split.ext);
                if (!factory.getClass().isInstance(f2)) {
                    split.vapScheme = "vap+"+fext;
                }
            } else {
                split.vapScheme = "vap+"+fext;
            }
            return URLSplit.format(split);
        }
    }

    public static boolean isAggregating(String surl) {
        int iquest = surl.indexOf("?");
        if (iquest == -1) iquest = surl.length();
        int ipercy = surl.lastIndexOf("%Y", iquest);
        if (ipercy == -1) ipercy = surl.lastIndexOf("%25", iquest);
        if (ipercy == -1) ipercy = surl.lastIndexOf("$Y", iquest);
        if (ipercy == -1) ipercy = surl.lastIndexOf("%y", iquest);
        if (ipercy == -1) ipercy = surl.lastIndexOf("$y", iquest);
        if (ipercy != -1) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * returns the URI to be interpretted by the DataSource.
     * @param uri, the URI understood in the context of all datasources.  This should contain "vap" or "vap+" for the scheme.
     * @return the URI for the datasource resource, or null if it is not valid.
     */
    public static URI getResourceURI(URI uri) {
        URLSplit split = URLSplit.parse(uri.toString());
        return split.resourceUri;
    }

    /**
     * returns the URI to be interpretted by the DataSource.
     * @param uri, the URI understood in the context of all datasources.  This should contain "vap" or "vap+" for the scheme.
     * @return the URI for the datasource resource, or null if it is not valid.
     */
    public static URI getResourceURI(String surl) {
        URLSplit split = URLSplit.parse(surl);
        return split.resourceUri;
    }

    /**
     * returns a downloadable URL from the surl, perhaps popping off the 
     * data source specifier.  This assumes that the resource is a URL,
     * and getResourceURI().toURL() should be used to handle all cases.
     * 
     * @param surl
     * @return
     */
    public static URL getWebURL(URI url) {
        try {
            URL rurl = getResourceURI(url).toURL();
            String surl = rurl.toString();
            surl = surl.replaceAll("%20", "+");
            return new URL(surl);

        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }

    }

    // mark the special case where a resource is actually a folder.
    public static class NonResourceException extends IllegalArgumentException {

        public NonResourceException(String msg) {
            super(msg);
        }
    }

    /**
     * for now, just hide the URI stuff from clients, let's not mess with factories
     * @param url
     * @return
     */
    public static DataSourceFormat getDataSourceFormat(URI uri) {
        int i = uri.getScheme().indexOf(".");
        String ext;

        if (i != -1) {
            ext = uri.getScheme().substring(0, i);

        } else {
            URL url = getWebURL(uri);

            String file = url.getPath();
            i = file.lastIndexOf(".");
            ext = i == -1 ? "" : file.substring(i);
        }
        return DataSourceRegistry.getInstance().getFormatByExt(ext);

    }

    /**
     * @param uri
     * @return an EditorPanel or null if one is not found.
     */
    public static DataSourceEditorPanel getDataSourceEditorPanel( URI uri ) {
        String surl= uri.toString();
        String ext = DataSetURL.getExt(surl);

        if (isAggregating(uri.toString())) {
            return new AggregatingDataSourceEditorPanel();
        } 
        
        DataSourceEditorPanel edit = DataSourceRegistry.getInstance().getEditorByExt(ext);
        return edit;
    }

    /**
     * get the datasource factory for the URL.
     */
    public static DataSourceFactory getDataSourceFactory(
            URI uri, ProgressMonitor mon) throws IOException, IllegalArgumentException {

        if (isAggregating(uri.toString())) {
            return new AggregatingDataSourceFactory();
        }

        String ext = DataSetURL.getExplicitExt(uri.toString());
        if (ext != null) {
            return DataSourceRegistry.getInstance().getSource(ext);
        }

        URI resourceUri;
        try {
            String resourceSuri= uri.getRawSchemeSpecificPart();
            //resourceSuri= resourceSuri.replaceAll("%", "%25");
            resourceUri = new URI( resourceSuri );
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
        ext = DataSetURL.getExt(uri.toASCIIString());
        if ( ext==null ) ext= "";
        
        DataSourceFactory factory = null;

        // see if we can identify it by ext, to avoid the head request.
        factory = DataSourceRegistry.getInstance().getSource(ext);

        if ( factory == null && (resourceUri.getScheme().equals("http") || resourceUri.getScheme().equals("https")) ) { // get the mime type
            URL url= resourceUri.toURL();
            mon.setTaskSize(-1);
            mon.started();
            mon.setProgressMessage("doing HEAD request to find dataset type");
            URLConnection c = url.openConnection();
            String mime = c.getContentType();
            if (mime == null) {
                throw new IOException("failed to connect");
            }
            String cd = c.getHeaderField("Content-Disposition"); // support VxOWare
            if (cd != null) {
                int i0 = cd.indexOf("filename=\"");
                i0 += "filename=\"".length();
                int i1 = cd.indexOf("\"", i0);
                String filename = cd.substring(i0, i1);
                i0 = filename.lastIndexOf(".");
                ext = filename.substring(i0);
            }

            mon.finished();
            factory = DataSourceRegistry.getInstance().getSourceByMime(mime);
        }

// maybe it was actually a directory


        if (factory == null) {
            if (ext.equals("")) {
                throw new NonResourceException("resource has no extension or mime type");
            } else {
                factory = DataSourceRegistry.getInstance().getSource(ext);
            }
        }

        if (factory == null) {
            throw new IllegalArgumentException("Unsupported extension: " + ext);
        }
        return factory;
    }

    /**
     *
     * split the parameters into name,value pairs.
     *
     * items without equals (=) are inserted as "arg_N"=name.
     * @deprecated use URLSplit.parseParams
     */
    public static LinkedHashMap<String, String> parseParams(String params) {
        return URLSplit.parseParams(params);
    }

    /**
     * @deprecated use URLSplit.parseParams
     */
    public static String formatParams(Map parms) {
        return URLSplit.formatParams(parms);
    }

    public static InputStream getInputStream(URL url, ProgressMonitor mon) throws IOException {
        URLSplit split = URLSplit.parse(url.toString());

        try {
            FileSystem fs = FileSystem.create(getWebURL(new URI(split.path)));
            FileObject fo = fs.getFileObject(split.file.substring(split.path.length()));
            if (!fo.isLocal()) {
                Logger.getLogger("virbo.dataset").info("downloading file " + fo.getNameExt());
            }
            return fo.getInputStream(mon);

        } catch (URISyntaxException ex) {
            throw new IOException("URI Syntax Exception: " + ex.getMessage());
        }
    }

    /**
     * return a file reference for the url.  This is initially to fix the problem
     * for Windows where new URL( "file://c:/myfile.dat" ).getPath() -> "/myfile.dat".
     *
     */
    public static File getFile(URL url, ProgressMonitor mon) throws IOException {

        URLSplit split = URLSplit.parse(url.toString());

        try {
            FileSystem fs = FileSystem.create(getWebURL(new URI(split.path)));
            String filename= split.file.substring(split.path.length());
            if ( fs instanceof LocalFileSystem ) filename= DataSourceUtil.unescape( filename );
            FileObject fo = fs.getFileObject(filename);
            if (!fo.isLocal()) {
                logger.fine("downloading file " + fo.getNameExt());
            } else {
                logger.fine("using local copy of "+fo.getNameExt());
            }
            File tfile = fo.getFile(mon);
            return tfile;
        } catch (URISyntaxException ex) {
            throw new IOException("URI Syntax Exception: " + ex.getMessage());
        }
    }

    /**
     * canonical method for getting the Autoplot URI.  If no protocol is specified, then file:// is
     * used.  Note URIs may contain prefix like bin.http://www.cdf.org/data.cdf.  The
     * result will start with an Autoplot sceme like "vap:" or "vap+cdf:"
     * 
     */
    public static URI getURI(String surl) throws URISyntaxException {
        URLSplit split = URLSplit.maybeAddFile(surl,0);
        surl= split.surl;
        if ( split.vapScheme!=null ) surl= split.vapScheme+":"+surl;
        if (surl.endsWith("://")) {
            surl += "/";  // what strange case is this?
        }
        //boolean isAlreadyEscaped = split.surl.contains("%25") || split.surl.contains("%20") || split.surl.contains("+"); // TODO: cheesy
        //if (!isAlreadyEscaped) {
            surl = surl.replaceAll("%([^0-9])", "%25$1");
            surl = surl.replaceAll("<", "%3C");
            surl = surl.replaceAll(">", "%3E");
            surl = surl.replaceAll(" ", "+");
        //}
        surl= URLSplit.format( URLSplit.parse(surl) ); // add "vap:" if it's not there
        URI result = new URI(surl);
        return result;
    }

    /**
     * canonical method for getting the URL.  These will always be web-downloadable 
     * URLs.
     */
    public static URL getURL(String surl) throws MalformedURLException {
        try {
            URI uri = getURI(surl);
            return getWebURL(uri);
        } catch (URISyntaxException ex) {
            throw new MalformedURLException(ex.getMessage());
        }
    }

    public static class CompletionResult {

        public String completion;
        public String doc;
        public String completable;
        public String label;
        public boolean maybePlot;

        protected CompletionResult(String completion, String doc) {
            this(completion, doc, null, false);
        }

        protected CompletionResult(String completion, String doc, boolean maybePlot) {
            this(completion, doc, null, false);
        }

        protected CompletionResult(String completion, String doc, String completable, boolean maybePlot) {
            this(completion, null, doc, null, false);
        }

        protected CompletionResult(String completion, String label, String doc, String completable, boolean maybePlot) {
            this.completion = completion;
            this.completable = completable;
            this.label = label == null ? completable : label;
            this.doc = doc;
            this.maybePlot = maybePlot;
        }
    }

    public static List<CompletionResult> getCompletions(final String surl, final int carotpos, ProgressMonitor mon) throws Exception {
        URLSplit split = URLSplit.parse(surl,carotpos);
        if ( split.file==null || ( split.carotPos > split.file.length() )
                && DataSourceRegistry.getInstance().hasSourceByExt( DataSetURL.getExt(surl) ) ) {
            return getFactoryCompletions(URLSplit.format(split), split.formatCarotPos, mon );
        } else {
            int firstSlashAfterHost = split.authority == null ? 0 : split.authority.length();
            if ( split.carotPos <= firstSlashAfterHost) {
                return getHostCompletions(URLSplit.format(split), split.formatCarotPos, mon );
            } else {
                return getFileSystemCompletions(URLSplit.format(split), split.formatCarotPos, mon );
            }

        }
    }
            
    public static List<CompletionResult> getHostCompletions(final String surl, final int carotpos, ProgressMonitor mon) throws IOException {
        URLSplit split = URLSplit.parse(surl.substring(0, carotpos));
        
        String prefix;
        String surlDir;
        if ( split.path==null ) {
            prefix = "";
            surlDir = "";
        } else {
            prefix = split.file.substring(split.path.length());
            surlDir = split.path;
        }

        mon.setLabel("getting list of cache hosts");

        File cacheF = new File(FileSystem.settings().getLocalCacheDir(), split.scheme);

        if ( !cacheF.exists() ) return Collections.emptyList();
        String[] s = cacheF.list();

        boolean foldCase = true;
        if (foldCase) {
            prefix = prefix.toLowerCase();
        }

        List<DataSetURL.CompletionResult> completions = new ArrayList<DataSetURL.CompletionResult>(s.length);
        for (int j = 0; j < s.length; j++) {
            String scomp = foldCase ? s[j].toLowerCase() : s[j];
            if (scomp.startsWith(prefix)) {
                String result1 = s[j] + "/";
                // drill down single entries, since often the root doesn't provide a list.
                String[] s2 = new File(cacheF, result1).list();
                while (s2.length == 1 && new File(cacheF, result1 + "/" + s2[0]).isDirectory()) {
                    result1 += s2[0] + "/";
                    s2 = new File(cacheF, result1).list();
                }
                completions.add(new DataSetURL.CompletionResult(surlDir + result1, result1, null, surl.substring(0, carotpos), true));
            }
        }

        // check for single completion that is just a folder name with /.
        if (completions.size() == 1) {
            if ((completions.get(0)).equals(surlDir + prefix + "/")) {
                // maybe we should do something special.
            }
        }

        return completions;
    }

    public static List<CompletionResult> getFileSystemCompletions(final String surl, final int carotpos, ProgressMonitor mon) throws IOException, URISyntaxException {
        URLSplit split = URLSplit.parse(surl.substring(0, carotpos));
        String prefix = URLSplit.uriDecode( split.file.substring(split.path.length()) );
        String surlDir = URLSplit.uriDecode(split.path);

        mon.setLabel("getting remote listing");

        FileSystem fs = null;
        String[] s;

        fs = FileSystem.create(new URL( surlDir ) );

        s = fs.listDirectory("/");

        Arrays.sort(s);

        boolean foldCase = Boolean.TRUE.equals(fs.getProperty(fs.PROP_CASE_INSENSITIVE));
        if (foldCase) {
            prefix = prefix.toLowerCase();
        }

        List<DataSetURL.CompletionResult> completions = new ArrayList<DataSetURL.CompletionResult>(s.length);
        for (int j = 0; j < s.length; j++) {
            String scomp = foldCase ? s[j].toLowerCase() : s[j];
            if (scomp.startsWith(prefix)) {
                if (s[j].endsWith("contents.html")) {
                    s[j] = s[j].substring(0, s[j].length() - "contents.html".length());
                } // kludge for dods
                // Hack for .zip archives:
                if (s[j].endsWith(".zip")) s[j] = s[j] + "/";
                String uriSafe = s[j].replaceAll(" ", "%20");
                completions.add(new DataSetURL.CompletionResult(surlDir + uriSafe, s[j], null, surl.substring(0, carotpos), true));
            }
        }

        // check for single completion that is just a folder name with /.
        if (completions.size() == 1) {
            if ((completions.get(0)).equals(surlDir + prefix + "/")) {
                // maybe we should do something special.
            }
        }

        return completions;
    }

    public static List<CompletionResult> getFactoryCompletions(String surl1, int carotPos, ProgressMonitor mon) throws Exception {
        CompletionContext cc = new CompletionContext();
        int qpos = surl1.lastIndexOf('?', carotPos);

        cc.surl = surl1;
        cc.surlpos = carotPos; //carotPos


        List<CompletionResult> result = new ArrayList<CompletionResult>();

        if (qpos != -1 && qpos < carotPos) { // in query section
            if (qpos == -1) {
                qpos = surl1.length();
            }

            int eqpos = surl1.lastIndexOf('=', carotPos - 1);
            int amppos = surl1.lastIndexOf('&', carotPos - 1);
            if (amppos == -1) {
                amppos = qpos;
            }

            if (eqpos > amppos) {
                cc.context = CompletionContext.CONTEXT_PARAMETER_VALUE;
                cc.completable = surl1.substring(eqpos + 1, carotPos);
                cc.completablepos = carotPos - (eqpos + 1);
            } else {
                cc.context = CompletionContext.CONTEXT_PARAMETER_NAME;
                cc.completable = surl1.substring(amppos + 1, carotPos);
                cc.completablepos = carotPos - (amppos + 1);
                if (surl1.length() > carotPos && surl1.charAt(carotPos) != '&') {  // insert implicit "&"
                    surl1 = surl1.substring(0, carotPos) + '&' + surl1.substring(carotPos);
                }

            }
        } else {
            cc.context = CompletionContext.CONTEXT_FILE;
            qpos = surl1.indexOf('?', carotPos);
            if (qpos == -1) {
                cc.completable = surl1;
            } else {
                cc.completable = surl1.substring(0, qpos);
            }

            cc.completablepos = carotPos;
        }

        URLSplit split = URLSplit.parse(surl1);

        if (cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {
            
            DataSourceFactory factory = getDataSourceFactory( getURI(surl1), new NullProgressMonitor());
            if (factory == null) {
                throw new IllegalArgumentException("unable to find data source factory");
            }

            URI uri = DataSetURL.getURI(CompletionContext.get(CompletionContext.CONTEXT_FILE, cc));

            cc.resource = DataSetURL.getWebURL(uri);
            cc.params = split.params;

            List<CompletionContext> completions = factory.getCompletions(cc, mon);

            // identify the implicit parameter names
            Map params = URLSplit.parseParams(split.params);
            boolean hasImplicit = false;
            for (int i = 0; i < 3; i++) {
                String arg = (String) params.get("arg_" + i);
                if (arg != null) {
                    for (CompletionContext cc1 : completions) {
                        if (cc1.context == CompletionContext.CONTEXT_PARAMETER_NAME && cc1.implicitName != null && cc1.completable.equals(arg)) {
                            params.put(cc1.implicitName, arg);
                            hasImplicit = true;
                        }

                    }
                }
            }
            if (!hasImplicit) {  // TODO: we still don't have this right.  We want to replace the key that was mistaken for a positional argument for a named parameter.                
                for (int i = 0; i < 3; i++) {
                    params.remove("arg_" + i);
                }

            }

            int i = 0;
            for (CompletionContext cc1 : completions) {
                String paramName = cc1.implicitName != null ? cc1.implicitName : cc1.completable;
                if (paramName.indexOf("=") != -1) {
                    paramName = paramName.substring(0, paramName.indexOf("="));
                }

                boolean dontYetHave = !params.containsKey(paramName);
                boolean startsWith = cc1.completable.startsWith(cc.completable);
                if (startsWith) {
                    LinkedHashMap paramsCopy = new LinkedHashMap(params);
                    if (cc1.implicitName != null) {
                        paramsCopy.put(cc1.implicitName, cc1.completable);
                    } else {
                        paramsCopy.put(cc1.completable, null);
                    }

                    String ss = split.vapScheme + ":" + split.file + "?" + URLSplit.formatParams(paramsCopy);
                    
                    if (dontYetHave == false) {
                        continue;  // skip it
                    }
                    result.add(new CompletionResult(  ss, cc1.label, cc1.doc, surl1.substring(0, carotPos), cc1.maybePlot));
                    i = i + 1;
                }

            }
            return result;

        } else if (cc.context == CompletionContext.CONTEXT_PARAMETER_VALUE) {
            URI uri = DataSetURL.getURI(CompletionContext.get(CompletionContext.CONTEXT_FILE, cc));
            DataSourceFactory factory = getDataSourceFactory( getURI(surl1), mon);

            cc.resource = DataSetURL.getWebURL(uri);
            cc.params = split.params;

            if (factory == null) {
                throw new IllegalArgumentException("unable to find data source factory");
            }

            List<CompletionContext> completions = factory.getCompletions(cc, mon);

            int i = 0;
            for (CompletionContext cc1 : completions) {
                if (cc1.completable.startsWith(cc.completable)) {
                    String ss = split.vapScheme + ":" + CompletionContext.insert(cc, cc1);
                    result.add(new CompletionResult(ss, cc1.label, cc1.doc, surl1.substring(0, carotPos), cc1.maybePlot));
                    i = i + 1;
                }

            }
            return result;

        } else {
            try {

                mon.setProgressMessage("listing directory");
                mon.started();
                String surl = CompletionContext.get(CompletionContext.CONTEXT_FILE, cc);

                int i = surl.lastIndexOf("/", carotPos - 1);
                String surlDir;  // name of surl, including only folders, ending with /.

                if (i <= 0) {
                    surlDir = surl;
                } else if (surl.charAt(i - 1) == '/') { // '//'
                    surlDir = surl.substring(0, i + 1);
                } else {
                    surlDir = surl.substring(0, i + 1);
                }

                URI url = getURI(surlDir);
                String prefix = surl.substring(i + 1, carotPos);
                FileSystem fs = FileSystem.create(getWebURL(url));
                String[] s = fs.listDirectory("/");
                mon.finished();
                for (int j = 0; j < s.length; j++) {
                    if (s[j].startsWith(prefix)) {
                        CompletionContext cc1 = new CompletionContext(CompletionContext.CONTEXT_FILE, surlDir + s[j]);
                        result.add(new CompletionResult(CompletionContext.insert(cc, cc1), cc1.label, cc1.doc, surl1.substring(0, carotPos), true));
                    }

                }
            } catch (MalformedURLException ex) {
                result = Collections.singletonList(new CompletionResult("Malformed URI", "Something in the URL prevents processing", surl1.substring(0, carotPos), false));
            } catch (FileSystem.FileSystemOfflineException ex) {
                result = Collections.singletonList(new CompletionResult("FileSystem offline", "FileSystem is offline.", surl1.substring(0, carotPos), false));
            } finally {
                mon.finished();
            }
            return result;
        }

    }

    private static void discoverFactories(DataSourceRegistry registry) {

        // discover Factories on the path
        try {
            ClassLoader loader = DataSetURL.class.getClassLoader();
            Enumeration<URL> urls;
            if (loader == null) {
                urls = ClassLoader.getSystemResources("META-INF/org.virbo.datasource.DataSourceFactory");
            } else {
                urls = loader.getResources("META-INF/org.virbo.datasource.DataSourceFactory");
            }
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
                                extensions = (List<String>) m.invoke(f, new Object[0]);
                            } catch (NoSuchMethodException ex) {
                            } catch (InvocationTargetException ex) {
                                ex.printStackTrace();
                            }
                            try {
                                Method m = c.getMethod("mimeTypes", new Class[0]);
                                mimeTypes = (List<String>) m.invoke(f, new Object[0]);
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
                                registry.registerExtension(factoryClassName, e, null);
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

    private static void discoverRegistryEntries(DataSourceRegistry registry) {
        try {
            ClassLoader loader = DataSetURL.class.getClassLoader();
            Enumeration<URL> urls;
            if (loader == null) {
                urls = ClassLoader.getSystemResources("META-INF/org.virbo.datasource.DataSourceFactory.extensions");
            } else {
                urls = loader.getResources("META-INF/org.virbo.datasource.DataSourceFactory.extensions");
            }
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
                String s = reader.readLine();
                while (s != null) {
                    s = s.trim();
                    if (s.length() > 0) {
                        String[] ss = s.split("\\s");
                        for (int i = 1; i < ss.length; i++) {
                            registry.registerExtension(ss[0], ss[i], null);
                        }
                    }
                    s = reader.readLine();
                }
                reader.close();
            }
            
            
            if (loader == null) {
                urls = ClassLoader.getSystemResources("META-INF/org.virbo.datasource.DataSourceFactory.mimeTypes");
            } else {
                urls = loader.getResources("META-INF/org.virbo.datasource.DataSourceFactory.mimeTypes");
            }
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
                String s = reader.readLine();
                while (s != null) {
                    s = s.trim();
                    if (s.length() > 0) {
                        String[] ss = s.split("\\s");
                        for (int i = 1; i < ss.length; i++) {
                            registry.registerMimeType(ss[0], ss[i]);
                        }
                    }
                    s = reader.readLine();
                }
                reader.close();
            }
            
            
            if (loader == null) {
                urls = ClassLoader.getSystemResources("META-INF/org.virbo.datasource.DataSourceFormat.extensions");
            } else {
                urls = loader.getResources("META-INF/org.virbo.datasource.DataSourceFormat.extensions");
            }
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
                String s = reader.readLine();
                while (s != null) {
                    s = s.trim();
                    if (s.length() > 0) {
                        String[] ss = s.split("\\s");
                        for (int i = 1; i < ss.length; i++) {
                            registry.registerFormatter(ss[0], ss[i]);
                        }
                    }
                    s = reader.readLine();
                }
                reader.close();
            }

            
            if (loader == null) {
                urls = ClassLoader.getSystemResources("META-INF/org.virbo.datasource.DataSourceEditorPanel.extensions");
            } else {
                urls = loader.getResources("META-INF/org.virbo.datasource.DataSourceEditorPanel.extensions");
            }
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
                String s = reader.readLine();
                while (s != null) {
                    s = s.trim();
                    if (s.length() > 0) {
                        String[] ss = s.split("\\s");
                        for (int i = 1; i < ss.length; i++) {
                            registry.registerEditor(ss[0], ss[i]);
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
}

