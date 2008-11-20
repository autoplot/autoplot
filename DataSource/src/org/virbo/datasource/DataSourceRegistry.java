/*
 * DataSourceRegistry.java
 *
 * Created on May 4, 2007, 6:27 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.datasource;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.virbo.datasource.datasource.DataSourceFormat;

/**
 *
 * @author jbf
 */
public class DataSourceRegistry {

    private static DataSourceRegistry instance;
    HashMap<String,Object> dataSourcesByExt;
    HashMap<String,Object> dataSourcesByMime;
    HashMap<String,Object> dataSourceFormatByExt;
    HashMap<String,String> extToDescription;

    /** Creates a new instance of DataSourceRegistry */
    private DataSourceRegistry() {
        dataSourcesByExt = new HashMap<String,Object>();
        dataSourcesByMime = new HashMap<String,Object>();
        dataSourceFormatByExt= new HashMap<String,Object>();
        extToDescription= new HashMap<String,String>();
    }

    public static DataSourceRegistry getInstance() {
        if (instance == null) {
            instance = new DataSourceRegistry();
        }
        return instance;
    }

    public List<String> getFormatterExtensions() {
        List<String> result= new ArrayList<String>();
        for ( Object k: dataSourceFormatByExt.keySet() ) {
            result.add( (String)k );
        }
        return result;
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
    private static String getExtension( String name ) {
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
    
}
