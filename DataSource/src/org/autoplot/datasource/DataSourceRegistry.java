/*
 * DataSourceRegistry.java
 *
 * Created on May 4, 2007, 6:27 AM
 *
 */
package org.autoplot.datasource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.autoplot.aggregator.AggregatingDataSourceFactory;

/**
 * The DataSourceRegistry keeps the map from extension (like .cdf) to 
 * the handler for .cdf files.  
 * @author jbf
 */
public class DataSourceRegistry {

    private static final Logger logger= Logger.getLogger("apdss.uri");

    private static volatile DataSourceRegistry instance;

    HashMap<String,Object> dataSourcesByExt;
    HashMap<String,Object> dataSourcesByMime;
    HashMap<String,Object> dataSourceFormatByExt;
    HashMap<String,Object> dataSourceFormatEditorByExt;
    HashMap<String,Object> dataSourceEditorByExt;
    HashMap<String,String> extToDescription;

    /** Creates a new instance of DataSourceRegistry */
    private DataSourceRegistry() {
        dataSourcesByExt = new HashMap<>();
        dataSourcesByMime = new HashMap<>();
        dataSourceFormatByExt= new HashMap<>();
        dataSourceEditorByExt= new HashMap<>();
        dataSourceFormatEditorByExt= new HashMap<>();
        extToDescription= new HashMap<>();
    }

    /**
     * get the single instance of this class.
     * @return the single instance of this class.
     */
    public static DataSourceRegistry getInstance() {
        DataSourceRegistry _instance = DataSourceRegistry.instance;
        if (_instance == null) {
            synchronized (DataSourceRegistry.class) {
                _instance = DataSourceRegistry.instance;
                if ( _instance==null ) {
                    DataSourceRegistry.instance = _instance = new DataSourceRegistry();
                }
            }
        }
        return _instance;
    }

    /**
     * get an instance of a class given the class name.
     * @param o the class name, e.g. org.autoplot.netCDF.HDF5DataSourceFormatEditorPanel
     * @return an instance of the class.
     */
    public static Object getInstanceFromClassName( String o ) {
        try {
            Class clas = Class.forName((String) o);
            Constructor constructor = clas.getDeclaredConstructor(new Class[]{});
            Object result = constructor.newInstance(new Object[]{});
            return result;
        } catch ( ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException | SecurityException | InvocationTargetException e ) {
            logger.log( Level.SEVERE, e.getMessage(), e );
            return null;
        }
    }
    /**
     * return a list of registered extensions the can format.  These will contain the dot prefix.
     * @return a list of registered extensions.
     */
    public List<String> getFormatterExtensions() {
        List<String> result= new ArrayList<>();
        for ( Object k: dataSourceFormatByExt.keySet() ) {
            result.add( (String)k );
        }
        return result;
    }


    /**
     * return a list of registered extensions.  These will contain the dot prefix.
     * @return a list of registered extensions. 
     */
    public List<String> getSourceExtensions() {
        List<String> result= new ArrayList<>();
        for ( Object k: dataSourcesByExt.keySet() ) {
            result.add( (String)k );
        }
        return result;
    }

    /**
     * return a list of registered extensions.  These will contain the dot prefix.
     * @return a list of registered extensions.
     */
    public List<String> getSourceEditorExtensions() {
        List<String> result= new ArrayList<>();
        for ( Object k: dataSourceEditorByExt.keySet() ) {
            result.add( (String)k );
        }
        return result;
    }

    /**
     * look for META-INF/org.autoplot.datasource.DataSourceFactory, create the
     * factory, then query for its extensions.  This is the orginal method
     * and is not used.
     */
    protected void discoverFactories() {

        DataSourceRegistry registry= this;

        // discover Factories on the path
        try {
            ClassLoader loader = DataSetURI.class.getClassLoader();
            Enumeration<URL> urls;
            if (loader == null) {
                urls = ClassLoader.getSystemResources("META-INF/org.autoplot.datasource.DataSourceFactory");
            } else {
                urls = loader.getResources("META-INF/org.autoplot.datasource.DataSourceFactory");
            }
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
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
                                    logger.log( Level.SEVERE, ex.getMessage(), ex );
                                }
                                try {
                                    Method m = c.getMethod("mimeTypes", new Class[0]);
                                    mimeTypes = (List<String>) m.invoke(f, new Object[0]);
                                } catch (NoSuchMethodException | InvocationTargetException ex) {
                                    logger.log( Level.SEVERE, ex.getMessage(), ex );
                                }
                            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
                                logger.log( Level.SEVERE, ex.getMessage(), ex );
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
                }
            }
        } catch (IOException e) {
            logger.log( Level.SEVERE, e.getMessage(), e );
        }
    }

    /**
     * returns a list of something to class, which is dependent on the client
     * @param urls
     * @return
     */
    private Map<String,String> readStuff( Iterator<URL> urls ) throws IOException {

        Map<String,String> result= new LinkedHashMap();
        while (urls.hasNext()) {
            URL url = urls.next();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                String s = reader.readLine();
                while (s != null) {
                    s = s.trim();
                    if (s.length() > 0) {
                        String[] ss = s.split("\\s");
                        for (int i = 1; i < ss.length; i++) {
                            result.put( ss[i], ss[0] );
                        }
                    }
                    s = reader.readLine();
                }
            }
        }
        return result;

    }
    /**
     * look for META-INF/org.autoplot.datasource.DataSourceFactory.extensions
     */
    protected void discoverRegistryEntries() {
        logger.fine("discoverRegistryEntries");
        DataSourceRegistry registry= this;
        try {
            ClassLoader loader = DataSetURI.class.getClassLoader();
            Enumeration<URL> urls;
            if (loader == null) {
                urls = ClassLoader.getSystemResources("META-INF/org.autoplot.datasource.DataSourceFactory.extensions");
            } else {
                urls = loader.getResources("META-INF/org.autoplot.datasource.DataSourceFactory.extensions");
            }
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                logger.log(Level.FINE, "loading {0}", url);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                    String s = reader.readLine();
                    while (s != null) {
                        s = s.trim();
                        if (s.length() > 0) {
                            String[] ss = s.split("\\s");
                            for (int i = 1; i < ss.length; i++) {
                                if ( ss[i].contains(".") ) {
                                    logger.warning("META-INF/org.autoplot.datasource.DataSourceFactory.extensions contains extension that contains period: ");
                                    logger.log(Level.WARNING, "{0} {1} in {2}", new Object[]{ss[0], ss[i], url});
                                    logger.warning("This sometimes happens when extension files are concatenated, so check that all are terminated by end-of-line");
                                    logger.warning("");
                                    throw new IllegalArgumentException("DataSourceFactory.extensions contains extension that contains period: "+url );
                                }
                                registry.registerExtension(ss[0], ss[i], null);
                            }
                        }
                        s = reader.readLine();
                    }
                }
            }


            if (loader == null) {
                urls = ClassLoader.getSystemResources("META-INF/org.autoplot.datasource.DataSourceFactory.mimeTypes");
            } else {
                urls = loader.getResources("META-INF/org.autoplot.datasource.DataSourceFactory.mimeTypes");
            }
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                logger.log(Level.FINE, "loading {0}", url);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
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
                }
            }


            if (loader == null) {
                urls = ClassLoader.getSystemResources("META-INF/org.autoplot.datasource.DataSourceFormat.extensions");
            } else {
                urls = loader.getResources("META-INF/org.autoplot.datasource.DataSourceFormat.extensions");
            }
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                logger.log(Level.FINE, "loading {0}", url);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                    String s = reader.readLine();
                    while (s != null) {
                        s = s.trim();
                        if (s.length() > 0) {
                            String[] ss = s.split("\\s");
                            for (int i = 1; i < ss.length; i++) {
                                if ( ss[i].contains(".") ) {
                                    logger.warning("META-INF/org.autoplot.datasource.DataSourceFormat.extensions contains extension that contains period: ");
                                    logger.log(Level.WARNING, "{0} {1} in {2}", new Object[]{ss[0], ss[i], url});
                                    logger.warning("This sometimes happens when extension files are concatenated, so check that all are terminated by end-of-line");
                                    logger.warning("");
                                    throw new IllegalArgumentException("DataSourceFactory.extensions contains extension that contains period: "+url );
                                }
                                registry.registerFormatter(ss[0], ss[i]);
                            }
                        }
                        s = reader.readLine();
                    }
                }
            }


            if (loader == null) {
                urls = ClassLoader.getSystemResources("META-INF/org.autoplot.datasource.DataSourceEditorPanel.extensions");
            } else {
                urls = loader.getResources("META-INF/org.autoplot.datasource.DataSourceEditorPanel.extensions");
            }
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                logger.log(Level.FINE, "loading {0}", url);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                    String s = reader.readLine();
                    while (s != null) {
                        s = s.trim();
                        if (s.length() > 0) {
                            String[] ss = s.split("\\s");
                            for (int i = 1; i < ss.length; i++) {
                                if ( ss[i].contains(".") ) {
                                    logger.warning("META-INF/org.autoplot.datasource.DataSourceEditorPanel.extensions contains extension that contains period: ");
                                    logger.log(Level.WARNING, "{0} {1} in {2}", new Object[]{ss[0], ss[i], url});
                                    logger.warning("This sometimes happens when extension files are concatenated, so check that all are terminated by end-of-line");
                                    logger.warning("");
                                    throw new IllegalArgumentException("DataSourceFactory.extensions contains extension that contains period: "+url );
                                }
                                registry.registerEditor(ss[0], ss[i]);
                            }
                        }
                        s = reader.readLine();
                    }
                }
            }

            if (loader == null) {
                urls = ClassLoader.getSystemResources("META-INF/org.autoplot.datasource.DataSourceFormatEditorPanel.extensions");
            } else {
                urls = loader.getResources("META-INF/org.autoplot.datasource.DataSourceFormatEditorPanel.extensions");
            }
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                logger.log(Level.FINE, "loading {0}", url);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                    String s = reader.readLine();
                    while (s != null) {
                        s = s.trim();
                        if (s.length() > 0) {
                            String[] ss = s.split("\\s");
                            for (int i = 1; i < ss.length; i++) {
                                if ( ss[i].contains(".") ) {
                                    logger.warning("META-INF/org.autoplot.datasource.DataSourceFormatEditorPanel.extensions contains extension that contains period: ");
                                    logger.log(Level.WARNING, "{0} {1} in {2}", new Object[]{ss[0], ss[i], url});
                                    logger.warning("This sometimes happens when extension files are concatenated, so check that all are terminated by end-of-line");
                                    logger.warning("");
                                    throw new IllegalArgumentException("DataSourceFactory.extensions contains extension that contains period: "+url );
                                }
                                registry.registerFormatEditor(ss[0], ss[i]);
                            }
                        }
                        s = reader.readLine();
                    }
                }
            }
        } catch (IOException e) {
            logger.log( Level.SEVERE, e.getMessage(), e );
        }

    }

    /**
     * Register a data source at runtime, allowing the user to 
     * override the internal extentions.  This allows, for example, a new version
     * of a data source to be compared to the production.
     * @param ext if non-null, use this extension instead.
     * @param jarFile the jar file, which must contain META-INF/org.autoplot.datasource.DataSourceFactory.extensions.
     * @throws IOException 
     */
    public void registerDataSourceJar( String ext, final URL jarFile ) throws IOException {
        URLClassLoader loader= (URLClassLoader) AccessController.doPrivileged(new PrivilegedAction() {
            @Override
            public Object run() {
                URLClassLoader load= new URLClassLoader( new URL[] {jarFile}, DataSourceRegistry.class.getClassLoader() );
                return load;
            }
        });

        Enumeration<URL> re= loader.getResources("META-INF/org.autoplot.datasource.DataSourceFactory.extensions");
        List<URL> rre= new ArrayList();
        while ( re.hasMoreElements() ) {
            URL u= re.nextElement();
            if ( u.toString().startsWith( "jar:"+ jarFile.toString() ) ) {
                rre.add(u);
            }
        }
        
        Map<String,String> stuff= readStuff( rre.iterator() );

        for ( Entry<String,String> ent: stuff.entrySet() ) {
            try {
                Class clas= loader.loadClass(ent.getValue());
                if ( ext!=null ) {
                    this.dataSourcesByExt.put( getExtension(ext), clas.getConstructor().newInstance());
                } else {
                    this.dataSourcesByExt.put( getExtension(ent.getKey()), clas.getConstructor().newInstance() );
                }
            } catch ( ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex ) {
                throw new IllegalArgumentException(ex);
            }

        }

    }


    /**
     * return true if the source is registered.
     * @param ext, for example ".cdf" or "cdf"
     * @return if the source is registered.
     */
    public boolean hasSourceByExt(String ext) {
        if ( ext==null ) return false;
        return dataSourcesByExt.get(getExtension(ext))!=null;
    }

    /**
     * return true if the source is registered by mime type.  
     * This is not used much.
     * @param mime, for example "application/x-das2stream"
     * @return true if the source is registered by mime type.
     */
    public boolean hasSourceByMime(String mime) {
        if ( mime==null ) return false;
        return dataSourcesByMime.get(mime)!=null;
    }

    /**
     * register the data source factory by extension
     * @param factory the factory (org.autoplot.foo.FooReaderFactory)
     * @param extension the extension (e.g. ".foo")
     */
    public void register(DataSourceFactory factory, String extension) {
        extension= getExtension(extension);
        dataSourcesByExt.put(extension, factory);
    }

    /**
     * register the data source factory by extension and mime
     * @param factory the factory (org.autoplot.foo.FooReaderFactory)
     * @param extension the extension (e.g. ".foo")
     * @param mime the mime type. (e.g. "x-application/foo")
     */
    public void register(DataSourceFactory factory, String extension, String mime) {
        extension= getExtension(extension);
        dataSourcesByExt.put(extension, factory);
        dataSourcesByMime.put(mime.toLowerCase(), factory);
    }

    /**
     * register the data source factory by extension.  The name of the
     * factory class is given, so that the class is not accessed until first
     * use.
     * @param className the class name of the factory. (e.g. "org.autoplot.cdf.CdfJavaDataSourceFactory")
     * @param extension the  extension (e.g. "cdf")
     * @param description a description of the format (e.g. "CDF files using java based reader")
     */
    public void registerExtension(String className, String extension, String description ) {
        extension= getExtension(extension);
        Object old= dataSourcesByExt.get(extension);
        if ( old!=null ) {
            String oldClassName= ( old instanceof String ) ? (String) old : old.getClass().getName() ;
            if ( !(oldClassName.equals(className)) ) {
                logger.log(Level.FINE, "extension {0} is already handled by {1}, replacing with {2}", new Object[]{extension, oldClassName, className});
            }
        }
        dataSourcesByExt.put(extension, className);
        if ( description!=null ) extToDescription.put( extension, description );
    }

    /**
     * register the data source factory by extension.  The name of the
     * factory class is given, so that the class is not accessed until first
     * use.
     * @param className the class name of the formatter
     * @param extension  the  extension (e.g. "cdf")
     */
    public void registerFormatter(String className, String extension) {
        if (extension.indexOf('.') != 0) extension= "."+extension;
        dataSourceFormatByExt.put(extension, className);
    }

    /**
     * register the data source editor by extension.  
     * @param className the class name of the editor (e.g. "org.autoplot.cdf.CdfDataSourceEditorPanel")
     * @param extension the  extension (e.g. "cdf")
     */    
    public void registerEditor( String className, String extension ) {
        extension= getExtension(extension);
        dataSourceEditorByExt.put(extension, className);
    }

    /**
     * register the data source format editor by extension.  This implements an
     * editor for formatting.
     * @param className the class name of the editor (e.g. "org.autoplot.cdf.CdfDataSourceFormatEditorPanel")
     * @param extension the  extension (e.g. "cdf")
     */    
    public void registerFormatEditor( String className, String extension ) {
        extension= getExtension(extension);
        dataSourceFormatEditorByExt.put(extension, className);
    }

    public void registerMimeType(String className, String mimeType) {
        dataSourcesByMime.put(mimeType, className);
    }

    /**
     * register the data source factory by extension and mime
     * @param className the class name of the factory. (e.g. "org.autoplot.cdf.CdfJavaDataSourceFactory")
     * @param extension the  extension (e.g. "cdf")
     * @param mime for example "application/x-das2stream"
     */
    public void register(String className, String extension, String mime) {
        extension= getExtension(extension);
        dataSourcesByExt.put(extension, className);
        dataSourcesByMime.put(mime.toLowerCase(), className);
    }

    private DataSourceFactory useJavaCdfForNative( String extension, Error ex ) {
        logger.fine("attempting to use java based reader to handle cdfn.");
        DataSourceFactory dsf=  getSource(".cdfj");
        if ( dsf!=null ) {
            dataSourcesByExt.put( extension, dsf ); //TODO: kludge for CDF
            dataSourceEditorByExt.put( extension, getDataSourceEditorByExt(".cdfj") );
            dataSourceFormatByExt.put( extension, getDataSourceFormatEditorByExt(".cdfj") );
            return dsf;
        } else {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * look up the source by its id.  If a filename is provided, then the
     * filename's extension is used, otherwise ".ext" or "ext" are accepted.
     * 
     * @param extension the extension, (e.g. ".cdf" or "/tmp/myfile.cdf")
     * @return the DataSourceFactory which will create the reader.
     */
    public synchronized DataSourceFactory getSource(String extension) {
        // dfc: Break here to find out how to autoset the mime-type from the path
        if ( extension==null ) return null;
        extension= getExtension(extension);
        Object o = dataSourcesByExt.get(extension);
        if (o == null) {
            return null;
        }

        DataSourceFactory result;
        if (o instanceof String) {
            try {
                if ( ((String)o).endsWith("DataSource") ) {
                    throw new IllegalArgumentException("DataSourceFactory names cannot end in DataSource: "+o);
                }
                Class clas = Class.forName((String) o);
                Constructor constructor = clas.getDeclaredConstructor(new Class[]{});
                result = (DataSourceFactory) constructor.newInstance(new Object[]{});
                dataSourcesByExt.put( extension, result ); // always use the same factory object.
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException(ex);
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException ex) {
                throw new RuntimeException(ex);
            } catch ( NoClassDefFoundError | UnsatisfiedLinkError ex ) {
                if ( extension.equals(".cdfn") || extension.equals(".cdf") ) {
                    result= useJavaCdfForNative(extension,ex);
                } else {
                    throw new RuntimeException(ex);
                }
            }
            // kludge in support to fall back to Java reader if the C-based one is not found.
             catch ( IllegalArgumentException | SecurityException | InvocationTargetException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            result = (DataSourceFactory) o;
        }
        return result;
    }

    /**
     * returns canonical extension for name by:
     * <ul> 
     * <li>add a dot when it's not there.
     * <li>clip off the filename part if it's there.
     * <li>force to lower case.
     * </ul>
     * @param name, such as "http://autoplot.org/data/autoplot.gif"
     * @return extension, such as ".gif"
     */
    protected static String getExtension( String name ) {
        if (name.indexOf('.') == -1 ) name= "."+name;
        if ( name.indexOf('.') > 0 ) {
            int i= name.lastIndexOf('.');
            name= name.substring(i);
        }
        int i=name.indexOf('?');
        if ( i!=-1 ) {
            name = name.substring(0,i );
        }
        i=name.indexOf('&'); // this is a whoops, they meant ?
        if ( i!=-1 ) {
            name = name.substring(0,i );
        }
        name= name.toLowerCase();
        return name;
    }
    
    /**
     * return the formatter based on the extension.
     * @param extension the extension, e.g. .cdf
     * @return the formatter found for this extension.
     */
    public DataSourceFormat getFormatByExt( String extension ) {
        if ( extension==null ) return null;
        extension= getExtension(extension);
        Object o = dataSourceFormatByExt.get(extension);
        if (o == null) {
            return null;
        }

        DataSourceFormat result;
        if (o instanceof String) {
            try {
                Class clas = Class.forName((String) o);
                Constructor constructor = clas.getDeclaredConstructor(new Class[]{});
                
                Object oresult= constructor.newInstance(new Object[]{});
                if ( oresult instanceof DataSourceFormat ) {
                    result = (DataSourceFormat) constructor.newInstance(new Object[]{});
                    logger.log(Level.FINE, "constructor for getFormat: {0}", clas);
                } else {
                    logger.log(Level.WARNING, "constructor of incorrect type for {0}, extension {1}", new Object[]{o, extension});
                    return null;
                }
            } catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException | SecurityException | InvocationTargetException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            result = (DataSourceFormat) o;
        }
        return result;
        
    }
    
    public synchronized DataSourceFactory getSourceByMime(String mime) {
        if ( mime==null ) return null;
        Object o = dataSourcesByMime.get(mime.toLowerCase());
        if (o == null) {
            return null;
        }

        DataSourceFactory result;
        if (o instanceof String) {
            try {
                Class clas = Class.forName((String) o);
                Constructor constructor = clas.getDeclaredConstructor(new Class[]{});
                result = (DataSourceFactory) constructor.newInstance(new Object[]{});
                dataSourcesByMime.put( mime.toLowerCase(), result );
            } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | IllegalArgumentException | SecurityException | InvocationTargetException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            result = (DataSourceFactory) o;
        }
        return result;
    }

    /**
     * returns a String of DataSourceEditor for the extention.  This should be
     * used via DataSourceEditorPanelUtil. (This is introduced to remove the
     * dependence on the swing library for clients that don't wish to use swing.)
     * @param ext
     * @return
     */
    public synchronized Object getDataSourceEditorByExt( String ext ) {
        return this.dataSourceEditorByExt.get(getExtension(ext));
    }

    public synchronized Object getDataSourceFormatEditorByExt( String ext ) {
        return this.dataSourceFormatEditorByExt.get(getExtension(ext));
    }

    /**
     * return the extension for the factory.
     * @param factory
     * @return
     */
    String getExtensionFor(DataSourceFactory factory) {
        for (  Entry<String,Object> ent: this.dataSourcesByExt.entrySet() ) {
            String key= ent.getKey();
            if ( ent.getValue()==factory ) return key;
        }
        return null;
    }

    public static String getPluginsText() {
        StringBuilder buf = new StringBuilder();
        buf.append("<html>");
        {
            buf.append("<h1>Plugins by Extension:</h1>");
            Map<String,Object> m = DataSourceRegistry.getInstance().dataSourcesByExt;
            Set<Entry<String,Object>> ss1= m.entrySet();
            List<Entry<String,Object>> ss= new ArrayList(ss1);
            Collections.sort( ss, new Comparator() {
                @Override
                public int compare(Object o1, Object o2) {
                    Entry<String,Object> s1= (Entry<String,Object>)o1;
                    Entry<String,Object> s2= (Entry<String,Object>)o2;
                    return s1.getKey().compareTo(s2.getKey());
                }
            });
            for ( Entry<String,Object> e: ss ) {
                String k= e.getKey();
                buf.append("").append(k).append(": ").append(e.getValue()).append("<br>");
            }
        }
        {
            buf.append("<h1>Plugins by Mime Type:</h1>");
            Map<String,Object> m = DataSourceRegistry.getInstance().dataSourcesByMime;
            for ( Entry<String,Object> e: m.entrySet() ) {
                String k= e.getKey();
                buf.append("").append(k).append(": ").append(e.getValue()).append("<br>");
            }
        }
        buf.append("</html>");
        return buf.toString();
    }

    public static List<CompletionContext> getPlugins() {
        List<CompletionContext> result= new ArrayList();

        Map<String,Object> m = DataSourceRegistry.getInstance().dataSourcesByExt;
        for ( Entry<String,Object> e : m.entrySet() ) {
            String k = e.getKey();
            result.add( new CompletionContext( CompletionContext.CONTEXT_AUTOPLOT_SCHEME, "vap+"+k.substring(1)+":" ) );
        }
        return result;
    }

    
    /**
     * return a description of the data source, if available.
     * TODO: in the export data GUI, there's a bunch of these coded by hand.
     * @param vapext
     * @return
     */
    public static String getDescriptionFor(String vapext) {
        if ( vapext.startsWith("vap+cdaweb") ) {
            return "CDAWeb database at NASA/SPDF";
        } else if ( vapext.startsWith("vap+das2server") ) {
            return "Das2Server";
        } else if (vapext.startsWith("vap+dc")){
            return "Federated das2 catalog";
        } else if ( vapext.startsWith("vap+inline") ) {
            return "Data encoded within the URI";
        } else {
            return null;
        }
    }

    /**
     * returns true if the vap scheme requires a resource URL.  For example,
     * vap+cdf: needs a resource URI (the file) but vap+inline doesn't.
     * @param vapScheme the scheme part of the Autoplot URI, or a URI.
     * @return true if the vapScheme needs a URL.
     */
    public boolean hasResourceUri(String vapScheme) {
        int i= vapScheme.indexOf(':');
        if ( i>0 ) vapScheme= vapScheme.substring(0,i);
        boolean noUri= vapScheme.equals("vap+cdaweb") || vapScheme.equals("vap+inline" ) || 
                       vapScheme.equals("vap+pdsppi") || vapScheme.equals("vap+dc") /* dascat */; 
        return !noUri;
    }
    
    
    /**
     * returns true if the vap scheme is known to require an order to the 
     * parameters.  This was introduced to support makeCanonical, which would
     * like to sort the URI parameters so the order does not matter, but then
     * you cannot do this operation with vap+inline which is essentially a 
     * program where the order matters.
     * 
     * @param vapScheme, like "vap+cdf:" or "vap+internal:", or entire URI.
     * @return true if the order of parameters matters.
     */
    public boolean hasParamOrder( String vapScheme) {
        if ( vapScheme.startsWith("vap+inline:") ) {
            return true;
        } else return vapScheme.startsWith("vap+internal:");
    }

    /**
     * return a description of the data source.
     * @param f the factory
     * @param uri the uri
     * @return "aggregation" or "cdf" etc.
     */
    String describe(DataSourceFactory f,String uri) {
        if ( f instanceof AggregatingDataSourceFactory ) {
            return "aggregation";
        } else {
            String ext= getExtension(uri);
            String s= extToDescription.get( ext );
            if ( s!=null ) {
                return s;
            } else {
                String s2= f.getDescription();
                if ( s2.length()!=0 ) {
                    return s2;
                } else {
                    return ext;
                }
            }
        }
    }

}
