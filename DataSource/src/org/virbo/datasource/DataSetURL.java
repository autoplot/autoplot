/*
 * DataSetURL.java
 *
 * Created on March 31, 2007, 7:54 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.datasource;

import java.util.logging.Level;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.filesystem.FileObject;
import org.das2.util.filesystem.FileSystem;
import ftpfs.FTPBeanFileSystemFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
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
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Logger;
import org.das2.DasApplication;
import org.das2.util.filesystem.FileSystemSettings;
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

    static {
        DataSourceRegistry registry = DataSourceRegistry.getInstance();
        discoverFactories(registry);
        discoverRegisteryEntries(registry);
    }
    

    static {
        FileSystem.registerFileSystemFactory("ftp", new FTPBeanFileSystemFactory());
        FileSystem.settings().setPersistence(FileSystemSettings.Persistence.EXPIRES);
        
        if ( DasApplication.hasAllPermission() ) {
            File apDataHome= new File(System.getProperty("user.home"), "autoplot_data");
            FileSystem.settings().setLocalCacheDir( apDataHome );
        }
    }
    
    static WeakHashMap<DataSource, DataSourceFactory> dsToFactory = new WeakHashMap<DataSource, DataSourceFactory>();

    public static String maybeAddFile(String surl) {
        if (surl.length() == 0) {
            return "file:/";
        }
        String scheme;  // identify the scheme, if any.
        int i0 = surl.indexOf(":");
        if (i0 == -1) {
            scheme = "";
        } else if (i0 == 1) { // one letter scheme is assumed to be windows drive letter.
            scheme = "";
        } else {
            scheme = surl.substring(0, i0);
        }

        if (scheme.equals("")) {
            surl = "file://" + ((surl.charAt(0) == '/') ? surl : ('/' + surl)); // Windows c:
            surl = surl.replaceAll("\\\\", "/");
            surl = surl.replaceAll(" ", "%20");
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
    public static URLSplit parse(String surl) {
        URI uri;

        surl = maybeAddFile(surl);

        int h = surl.indexOf(":/");
        String scheme = surl.substring(0, h);

        URL url = null;
        try {
            if (scheme.contains(".")) {
                int j = scheme.indexOf(".");

                url = new URL(surl.substring(j + 1));
            } else {
                url = new URL(surl);
            }
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
            return null;
        }

        String file = url.getPath();
        int i = file.lastIndexOf(".");
        String ext = i == -1 ? "" : file.substring(i);

        String params = null;

        int fileEnd;
        // check for just one ?
        i = surl.indexOf("?");
        if (i != -1) {
            fileEnd = i;
            params = surl.substring(i + 1);
            i = surl.indexOf("?", i + 1);
            if (i != -1) {
                throw new IllegalArgumentException("too many ??'s!");
            }
        } else {
            fileEnd = surl.length();
        }

        i = surl.lastIndexOf("/");
        String surlDir = surl.substring(0, i);

        int i2 = surl.indexOf("://");

        URLSplit result = new URLSplit();
        result.scheme = scheme;
        result.path = surlDir + "/";
        result.file = surl.substring(0, fileEnd);
        result.ext = ext;
        result.params = params;

        return result;


    }

    public static String format(URLSplit split) {
        String result = split.file;
        if (split.params != null) {
            result += "?" + split.params;
        }
        return result;
    }

    /**
     * get the data source for the URL.
     * @throws IllegalArgumentException if the url extension is not supported.
     */
    public static DataSource getDataSource(URI uri) throws Exception {
        DataSourceFactory factory = getDataSourceFactory(uri, new NullProgressMonitor());
        URL url = getWebURL(uri);
        DataSource result = factory.getDataSource(url);
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
            URLSplit split = DataSetURL.parse(ds.getURL());
            String fext;
            fext = DataSourceRegistry.getInstance().getExtensionFor(factory).substring(1);
            if (DataSourceRegistry.getInstance().hasSourceByExt(split.ext)) {
                DataSourceFactory f2 = DataSourceRegistry.getInstance().getSource(split.ext);
                if (!factory.getClass().isInstance(f2)) {
                    split.file = fext + "." + split.file;
                }
            } else {
                split.file = fext + "." + split.file;
            }
            return DataSetURL.format(split);
        }
    }

    private static boolean isAggregating(String surl) {
        int iquest = surl.indexOf("?");
        int ipercy = surl.indexOf("%Y");
        if (ipercy == -1) {
            ipercy = surl.indexOf("%25");
        }
        if (ipercy != -1 && (iquest == -1 || ipercy < iquest)) {
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
    public static URL getWebURL(URI url) {
        try {
            int i = url.getScheme().indexOf(".");
            String surl;
            if (i == -1) {
                try {
                    surl = url.toURL().toString();
                } catch (MalformedURLException ex) {
                    throw new RuntimeException(ex);
                }
            } else {
                String s = url.toString();
                surl = s.substring(i + 1);
            }
            surl = surl.replaceAll("%25", "%");
            surl = surl.replaceAll("%20", " ");
            //surl = URLDecoder.decode(surl, "US-ASCII");
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
     * get the datasource factory for the URL.
     */
    public static DataSourceFactory getDataSourceFactory(
            URI uri, ProgressMonitor mon) throws IOException, IllegalArgumentException {

        if (isAggregating(uri.toString())) {
            return new AggregatingDataSourceFactory();
        }

        int i = uri.getScheme().indexOf(".");
        if (i != -1) {
            String ext = uri.getScheme().substring(0, i);
            return DataSourceRegistry.getInstance().getSource(ext);
        }

        URL url = uri.toURL();

        String file = url.getPath();
        i = file.lastIndexOf(".");
        String ext = i == -1 ? "" : file.substring(i);

        // check for just one ?
        String surl = url.toString();
        i = surl.indexOf("?");
        if (i != -1) {
            i = surl.indexOf("?", i + 1);
            if (i != -1) {
                throw new IllegalArgumentException("too many ??'s!");
            }
        }

        DataSourceFactory factory = null;

        // see if we can identify it by ext, to avoid the head request.
        factory = DataSourceRegistry.getInstance().getSource(ext);

        if (factory == null && url.getProtocol().equals("http")) { // get the mime type
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

    private static int indexOf(String s, char ch, char ignoreBegin, char ignoreEnd) {
        int i = s.indexOf(ch);
        int i0 = s.indexOf(ignoreBegin);
        int i1 = s.indexOf(ignoreEnd);
        if (i != -1 && i0 < i && i < i1) {
            i = -1;
        }
        return i;
    }

    /**
     *
     * split the parameters into name,value pairs.
     *
     * items without equals (=) are inserted as "arg_N"=name.
     */
    public static LinkedHashMap<String, String> parseParams(String params) {
        LinkedHashMap result = new LinkedHashMap();
        if (params == null) {
            return result;
        }
        if (params.trim().equals("")) {
            return result;
        }
        String[] ss = params.split("&");

        int argc = 0;

        for (int i = 0; i < ss.length; i++) {
            int j = indexOf(ss[i], '=', '(', ')');
            String name,
                    value;
            if (j == -1) {
                name = ss[i];
                value = "";
                result.put("arg_" + (argc++), name);
            } else {
                name = ss[i].substring(0, j);
                value = ss[i].substring(j + 1);
                result.put(name, value);
            }
        }
        return result;
    }

    public static String formatParams(Map parms) {
        StringBuffer result = new StringBuffer("");
        for (Iterator i = parms.keySet().iterator(); i.hasNext();) {
            String key = (String) i.next();
            if (key.startsWith("arg_")) {
                if (!parms.get(key).equals("")) {
                    result.append("&" + parms.get(key));
                }
            } else {
                String value = (String) parms.get(key);
                if (value != null) {
                    result.append("&" + key + "=" + value);
                } else {
                    result.append("&" + key);
                }
            }
        }
        return (result.length() == 0) ? "" : result.substring(1);
    }

    public static InputStream getInputStream(URL url, ProgressMonitor mon) throws IOException {
        URLSplit split = parse(url.toString());

        String proto = url.getProtocol();
        if (proto.equals("file")) {
            String surl = url.toString();
            int idx1 = surl.indexOf("?");
            if (idx1 == -1) {
                idx1 = surl.length();
            }
            surl = surl.substring(0, idx1);

            String sfile;
            int idx0 = surl.indexOf("file:///");
            if (idx0 == -1) {
                idx0 = surl.indexOf("file:/");
                sfile = surl.substring(idx0 + 5);
            } else {
                sfile = surl.substring(idx0 + 7);
            }
            sfile = URLDecoder.decode(sfile, "US-ASCII");
            return new FileInputStream(new File(sfile));

        } else {
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
    }

    /**
     * return a file reference for the url.  This is initially to fix the problem
     * for Windows where new URL( "file://c:/myfile.dat" ).getPath() -> "/myfile.dat".
     *
     */
    public static File getFile(URL url, ProgressMonitor mon) throws IOException {

        URLSplit split = parse(url.toString());

        String proto = url.getProtocol();
        if (proto.equals("file")) {
            String surl = url.toString();
            int idx1 = surl.indexOf("?");
            if (idx1 == -1) {
                idx1 = surl.length();
            }
            surl = surl.substring(0, idx1);

            String sfile;
            int idx0 = surl.indexOf("file:///");
            if (idx0 == -1) {
                idx0 = surl.indexOf("file:/");
                sfile = surl.substring(idx0 + 5);
            } else {
                sfile = surl.substring(idx0 + 7);
            }
            sfile = URLDecoder.decode(sfile, "US-ASCII");
            return new File(sfile);

        } else {
            try {
                FileSystem fs = FileSystem.create(getWebURL(new URI(split.path)));
                FileObject fo = fs.getFileObject(split.file.substring(split.path.length()));
                if (!fo.isLocal()) {
                    Logger.getLogger("virbo.dataset").info("downloading file " + fo.getNameExt());
                }
                File tfile = fo.getFile(mon);
                return tfile;
            } catch (URISyntaxException ex) {
                throw new IOException("URI Syntax Exception: " + ex.getMessage());
            }
        }
    }

    /**
     * canonical method for getting the URI.  If no protocol is specified, then file:// is
     * used.  Note URIs may contain prefix like bin.http://www.cdf.org/data.cdf.
     * 
     */
    public static URI getURI(String surl) throws URISyntaxException {
        surl = maybeAddFile(surl);
        if (surl.endsWith("://")) {
            surl += "/";  // what strange case is this?
        }
        boolean isAlreadyEscaped = surl.contains("%20"); // TODO: cheesy
        if (!isAlreadyEscaped) {
            surl = surl.replaceAll("%", "%25");
            surl = surl.replaceAll(" ", "%20");
        }
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

    public static List<CompletionResult> getFileSystemCompletions(final String surl, final int carotpos, ProgressMonitor mon) throws IOException {
        URLSplit split = DataSetURL.parse(surl.substring(0, carotpos));
        String prefix = split.file.substring(split.path.length());
        String surlDir = split.path;

        mon.setLabel("getting remote listing");

        FileSystem fs = null;
        String[] s;

        fs = FileSystem.create(DataSetURL.getWebURL(URI.create(split.path)));

        s = fs.listDirectory("/");

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
                completions.add(new DataSetURL.CompletionResult(surlDir + s[j], s[j], null, surl.substring(0, carotpos), true));
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

    public static List<CompletionResult> getCompletions3(String surl1, int carotPos, ProgressMonitor mon) throws Exception {
        CompletionContext cc = new CompletionContext();
        int qpos = surl1.lastIndexOf('?', carotPos);

        cc.surl = surl1;
        cc.surlpos = carotPos;


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

        URLSplit split = DataSetURL.parse(surl1);

        if (cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {

            DataSourceFactory factory = getDataSourceFactory(DataSetURL.getURI(CompletionContext.get(CompletionContext.CONTEXT_FILE, cc)), new NullProgressMonitor());
            if (factory == null) {
                throw new IllegalArgumentException("unable to find data source factory");
            }

            URI uri = DataSetURL.getURI(CompletionContext.get(CompletionContext.CONTEXT_FILE, cc));

            cc.resource = DataSetURL.getWebURL(uri);
            cc.params = split.params;

            List<CompletionContext> completions = factory.getCompletions(cc, mon);

            // identify the implicit parameter names
            Map params = DataSetURL.parseParams(split.params);
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

                    String ss = split.file + "?" + DataSetURL.formatParams(paramsCopy);
                    //String ss= CompletionContext.insert( cc, cc1 );
                    if (dontYetHave == false) {
                        continue;  // skip it
                    }
                    result.add(new CompletionResult(ss, cc1.label, cc1.doc, surl1.substring(0, carotPos), cc1.maybePlot));
                    i = i + 1;
                }

            }
            return result;

        } else if (cc.context == CompletionContext.CONTEXT_PARAMETER_VALUE) {
            URI uri = DataSetURL.getURI(CompletionContext.get(CompletionContext.CONTEXT_FILE, cc));
            DataSourceFactory factory = getDataSourceFactory(uri, mon);

            cc.resource = DataSetURL.getWebURL(uri);
            cc.params = split.params;

            if (factory == null) {
                throw new IllegalArgumentException("unable to find data source factory");
            }

            List<CompletionContext> completions = factory.getCompletions(cc, mon);

            int i = 0;
            for (CompletionContext cc1 : completions) {
                if (cc1.completable.startsWith(cc.completable)) {
                    String ss = CompletionContext.insert(cc, cc1);
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
            ClassLoader loader= DataSetURL.class.getClassLoader();
            Enumeration<URL> urls;
            if ( loader==null ) {
                urls= ClassLoader.getSystemResources("META-INF/org.virbo.datasource.DataSourceFactory");
            } else {
                urls= loader.getResources("META-INF/org.virbo.datasource.DataSourceFactory");
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

    private static void discoverRegisteryEntries(DataSourceRegistry registry) {
        try {
            ClassLoader loader= DataSetURL.class.getClassLoader();
            Enumeration<URL> urls;
            if ( loader==null ) {
                urls= ClassLoader.getSystemResources("META-INF/org.virbo.datasource.DataSourceFactory.extensions");
            } else {
                urls= loader.getResources("META-INF/org.virbo.datasource.DataSourceFactory.extensions");
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
            if ( loader==null ) {
                urls= ClassLoader.getSystemResources("META-INF/org.virbo.datasource.DataSourceFactory.mimeTypes");
            } else {
                urls= loader.getResources("META-INF/org.virbo.datasource.DataSourceFactory.mimeTypes");
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
            if ( loader==null ) {
                urls= ClassLoader.getSystemResources("META-INF/org.virbo.datasource.DataSourceFormat.extensions");
            } else {
                urls= loader.getResources("META-INF/org.virbo.datasource.DataSourceFormat.extensions");
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
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /** call this to trigger initialization */
    public static void init() {
    }
}

