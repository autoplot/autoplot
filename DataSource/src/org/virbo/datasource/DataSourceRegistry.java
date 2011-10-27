/*
 * DataSourceRegistry.java
 *
 * Created on May 4, 2007, 6:27 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.datasource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 *
 * @author jbf
 */
public class DataSourceRegistry {

    private static DataSourceRegistry instance;
    HashMap<String,Object> dataSourcesByExt;
    HashMap<String,Object> dataSourcesByMime;
    HashMap<String,Object> dataSourceFormatByExt;
    HashMap<String,Object> dataSourceFormatEditorByExt;
    HashMap<String,Object> dataSourceEditorByExt;
    HashMap<String,String> extToDescription;

    /** Creates a new instance of DataSourceRegistry */
    private DataSourceRegistry() {
        dataSourcesByExt = new HashMap<String,Object>();
        dataSourcesByMime = new HashMap<String,Object>();
        dataSourceFormatByExt= new HashMap<String,Object>();
        dataSourceEditorByExt= new HashMap<String,Object>();
        dataSourceFormatEditorByExt= new HashMap<String,Object>();
        extToDescription= new HashMap<String,String>();
    }

    public static DataSourceRegistry getInstance() {
        if (instance == null) {
            instance = new DataSourceRegistry();
        }
        return instance;
    }

    public static Object getInstanceFromClassName( String o ) {
        try {
            Class clas = Class.forName((String) o);
            Constructor constructor = clas.getDeclaredConstructor(new Class[]{});
            Object result = constructor.newInstance(new Object[]{});
            return result;
        } catch ( Exception e ) {
            e.printStackTrace();
            return null;
        }
    }
    /**
     * return a list of registered extensions the can format.  These will contain the dot prefix.
     * @return
     */
    public List<String> getFormatterExtensions() {
        List<String> result= new ArrayList<String>();
        for ( Object k: dataSourceFormatByExt.keySet() ) {
            result.add( (String)k );
        }
        return result;
    }


    /**
     * return a list of registered extensions.  These will contain the dot prefix.
     * @return
     */
    public List<String> getSourceExtensions() {
        List<String> result= new ArrayList<String>();
        for ( Object k: dataSourcesByExt.keySet() ) {
            result.add( (String)k );
        }
        return result;
    }

    /**
     * return a list of registered extensions.  These will contain the dot prefix.
     * @return
     */
    public List<String> getSourceEditorExtensions() {
        List<String> result= new ArrayList<String>();
        for ( Object k: dataSourceEditorByExt.keySet() ) {
            result.add( (String)k );
        }
        return result;
    }

    /**
     * look for META-INF/org.virbo.datasource.DataSourceFactory, create the
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

    /**
     * returns a list of something to class, which is dependent on the client
     * @param urls
     * @return
     */
    private Map<String,String> readStuff( Iterator<URL> urls ) throws IOException {

        Map<String,String> result= new LinkedHashMap();
        while (urls.hasNext()) {
            URL url = urls.next();
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
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
            reader.close();
        }
        return result;

    }
    /**
     * look for META-INF/org.virbo.datasource.DataSourceFactory.extensions
     */
    protected void discoverRegistryEntries() {
        DataSourceRegistry registry= this;
        try {
            ClassLoader loader = DataSetURI.class.getClassLoader();
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

            if (loader == null) {
                urls = ClassLoader.getSystemResources("META-INF/org.virbo.datasource.DataSourceFormatEditorPanel.extensions");
            } else {
                urls = loader.getResources("META-INF/org.virbo.datasource.DataSourceFormatEditorPanel.extensions");
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
                            registry.registerFormatEditor(ss[0], ss[i]);
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

    public void registerDataSourceJar( String ext, URL jarFile ) throws IOException {
        URLClassLoader loader= new URLClassLoader( new URL[] {jarFile}, DataSourceRegistry.class.getClassLoader() );

        Enumeration<URL> re= loader.getResources("META-INF/org.virbo.datasource.DataSourceFactory.extensions");
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
            } catch ( ClassNotFoundException ex ) {
                throw new IllegalArgumentException(ex);
            } catch ( NoSuchMethodException ex ) {
                throw new IllegalArgumentException(ex);
            } catch (InstantiationException ex) {
                throw new IllegalArgumentException( ex);
            } catch (IllegalAccessException ex) {
                throw new IllegalArgumentException(ex);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(ex);
            } catch (InvocationTargetException ex) {
                throw new IllegalArgumentException(ex);
            }

        }

    }


    public boolean hasSourceByExt(String ext) {
        if ( ext==null ) return false;
        return dataSourcesByExt.get(getExtension(ext))!=null;
    }

    public boolean hasSourceByMime(String mime) {
        if ( mime==null ) return false;
        return dataSourcesByMime.get(mime)!=null;
    }

    /**
     * register the data source factory by extension
     */
    public void register(DataSourceFactory factory, String extension) {
        extension= getExtension(extension);
        dataSourcesByExt.put(extension, factory);
    }

    /**
     * register the data source factory by extension and mime
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
     */
    public void registerExtension(String className, String extension, String description ) {
        extension= getExtension(extension);
        Object old= dataSourcesByExt.get(extension);
        if ( old!=null ) {
            String oldClassName= ( old instanceof String ) ? (String) old : old.getClass().getName() ;
            if ( !(oldClassName.equals(className)) ) {
                System.err.println("extension "+extension+ " is already handled by "+oldClassName + ", replacing with "+className );
            }
        }
        dataSourcesByExt.put(extension, className);
        if ( description!=null ) extToDescription.put( extension, description );
    }

    /**
     * register the data source factory by extension.  The name of the
     * factory class is given, so that the class is not accessed until first
     * use.
     */
    public void registerFormatter(String className, String extension) {
        if (extension.indexOf('.') != 0) extension= "."+extension;
        dataSourceFormatByExt.put(extension, className);
    }

    public void registerEditor( String className, String extension ) {
        extension= getExtension(extension);
        dataSourceEditorByExt.put(extension, className);
    }

    public void registerFormatEditor( String className, String extension ) {
        extension= getExtension(extension);
        dataSourceFormatEditorByExt.put(extension, className);
    }

    public void registerMimeType(String className, String mimeType) {
        dataSourcesByMime.put(mimeType, className);
    }

    /**
     * register the data source factory by extension and mime
     */
    public void register(String className, String extension, String mime) {
        extension= getExtension(extension);
        dataSourcesByExt.put(extension, className);
        dataSourcesByMime.put(mime.toLowerCase(), className);
    }

    /**
     * look up the source by its id.  If a filename is provided, then the
     * filename's extension is used, otherwise ".<ext>" or "<ext>" are accepted.
     * 
     * @param extension
     * @return
     */
    public synchronized DataSourceFactory getSource(String extension) {
        if ( extension==null ) return null;
        extension= getExtension(extension);
        Object o = dataSourcesByExt.get(extension);
        if (o == null) {
            return null;
        }

        DataSourceFactory result;
        if (o instanceof String) {
            try {
                Class clas = Class.forName((String) o);
                Constructor constructor = clas.getDeclaredConstructor(new Class[]{});
                result = (DataSourceFactory) constructor.newInstance(new Object[]{});
                dataSourcesByExt.put( extension, result ); // always use the same factory object.
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException(ex);
            } catch (NoSuchMethodException ex) {
                throw new RuntimeException(ex);
            } catch (InstantiationException ex) {
                throw new RuntimeException(ex);
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
            } catch ( UnsatisfiedLinkError ex ) { // kludge in support to fall back to Java reader if the C-based one is not found.
                if ( extension.equals(".cdf") ) {
                    System.err.println("attempting to use java based reader to handle cdf.");
                    DataSourceFactory dsf=  getSource(".cdfj");
                    if ( dsf!=null ) {
                        dataSourcesByExt.put( extension, dsf ); //TODO: kludge for CDF
                        dataSourceEditorByExt.put( extension, getDataSourceEditorByExt(".cdfj") );
                        dataSourceFormatByExt.remove( extension );
                        return dsf;
                    } else {
                        throw new RuntimeException(ex);
                    }
                } else {
                    throw new RuntimeException(ex);
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        } else {
            result = (DataSourceFactory) o;
        }
        return result;
    }

    /**
     * returns canonical extension for name by:
     *   add a dot when it's not there.
     *   clip off the filename part if it's there.
     *   force to lower case.
     * @param name, such as "http://autoplot.org/data/autoplot.gif"
     * @return extension, such as ".gif"
     */
    protected static String getExtension( String name ) {
        if (name.indexOf('.') == -1 ) name= "."+name;
        if ( name.indexOf('.') > 0 ) {
            int i= name.lastIndexOf('.');
            name= name.substring(i);
        }
        int i=name.indexOf("?");
        if ( i!=-1 ) {
            name = name.substring(0,i );
        }
        i=name.indexOf("&"); // this is a whoops, they meant ?
        if ( i!=-1 ) {
            name = name.substring(0,i );
        }
        name= name.toLowerCase();
        return name;
    }
    
    /**
     * return the formatter based on the extension.
     * @param extension
     * @return
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
                result = (DataSourceFormat) constructor.newInstance(new Object[]{});
            } catch (Exception ex) {
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
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException(ex);
            } catch (NoSuchMethodException ex) {
                throw new RuntimeException(ex);
            } catch (InstantiationException ex) {
                throw new RuntimeException(ex);
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
            } catch (Exception ex) {
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
        return this.dataSourceEditorByExt.get(ext);
    }

    public synchronized Object getDataSourceFormatEditorByExt( String ext ) {
        return this.dataSourceFormatEditorByExt.get(ext);
    }

    /**
     * return the extension for the factory.
     * @param factory
     * @return
     */
    String getExtensionFor(DataSourceFactory factory) {
        for (  String ext: this.dataSourcesByExt.keySet() ) {
            if ( dataSourcesByExt.get(ext)==factory ) return ext;
        }
        return null;
    }

    public static String getPluginsText() {
        StringBuffer buf = new StringBuffer();
        buf.append("<html>");
        {
            buf.append("<h1>Plugins by Extension:</h1>");
            Map m = DataSourceRegistry.getInstance().dataSourcesByExt;
            for (Object k : m.keySet()) {
                buf.append("" + k + ": " + m.get(k) + "<br>");
            }
        }
        {
            buf.append("<h1>Plugins by Mime Type:</h1>");
            Map m = DataSourceRegistry.getInstance().dataSourcesByMime;
            for (Object k : m.keySet()) {
                buf.append("" + k + ": " + m.get(k) + "<br>");
            }
        }
        buf.append("</html>");
        return buf.toString();
    }

    public static List<CompletionContext> getPlugins() {
        List<CompletionContext> result= new ArrayList();

        Map m = DataSourceRegistry.getInstance().dataSourcesByExt;
        for (Object k : m.keySet()) {
            result.add( new CompletionContext( CompletionContext.CONTEXT_AUTOPLOT_SCHEME, "vap+"+k.toString().substring(1)+":" ) );
        }
        return result;
    }

}
