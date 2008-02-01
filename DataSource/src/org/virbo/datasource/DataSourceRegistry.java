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
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

/**
 *
 * @author jbf
 */
public class DataSourceRegistry {

    private static DataSourceRegistry instance;
    HashMap dataSourcesByExt;
    HashMap dataSourcesByMime;

    /** Creates a new instance of DataSourceRegistry */
    private DataSourceRegistry() {
        dataSourcesByExt = new HashMap();
        dataSourcesByMime = new HashMap();
    }

    public static DataSourceRegistry getInstance() {
        if (instance == null) {
            instance = new DataSourceRegistry();
        }
        return instance;
    }

    /**
     * register the data source factory by extension
     */
    public void register(DataSourceFactory factory, String extension) {
        if (extension.indexOf('.') != 0) extension= "."+extension;
        dataSourcesByExt.put(extension, factory);
    }

    /**
     * register the data source factory by extension and mime
     */
    public void register(DataSourceFactory factory, String extension, String mime) {
        if (extension.indexOf('.') != 0) extension= "."+extension;
        dataSourcesByExt.put(extension, factory);
        dataSourcesByMime.put(mime.toLowerCase(), factory);
    }

    /**
     * register the data source factory by extension.  The name of the
     * factory class is given, so that the class is not accessed until first
     * use.
     */
    public void register(String className, String extension) {
        if (extension.indexOf('.') != 0) extension= "."+extension;
        dataSourcesByExt.put(extension, className);
    }

    public void registerMimeType(String className, String mimeType) {
        dataSourcesByMime.put(mimeType, className);
    }

    /**
     * register the data source factory by extension and mime
     */
    public void register(String className, String extension, String mime) {
        if (extension.indexOf('.') != 0) extension= "."+extension;
        dataSourcesByExt.put(extension, className);
        dataSourcesByMime.put(mime.toLowerCase(), className);
    }

    public DataSourceFactory getSource(String extension) {
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

    public DataSourceFactory getSourceByMime(String mime) {
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
}
