/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.beans.IntrospectionException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jdesktop.beansbinding.Converter;

/**
 * It is apparent that the overhead of BeansBinding is so great that a lightweight
 * binding engine would dramatically improve performance.  This encapsulates.
 * 
 * @author jbf
 */
public class BindingSupport {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot");
    protected BindingSupport() {
        implBindingContexts = new HashMap();
        //sources= new HashMap();
    }

    private static class BindingImpl {

        PropertyChangeListener srcListener;
        PropertyChangeListener dstListener;
        DomNode src;
        Object dst;
        String dstProp;
        String srcProp;
        Method dstSetter;
        Method srcSetter;
        Method dstGetter;
        Method srcGetter;
        @Override
        public String toString() {
            return src+"."+srcProp+"==dst."+dstProp;
        }
    }

    public static Converter toStringConverter= new Converter() {
        @Override
        public Object convertForward(Object value) {
            return value.toString();
        }

        @Override
        public Object convertReverse(Object value) {
            return value.toString();
        }
    };

    private static class MyPropChangeListener implements PropertyChangeListener {

        final Object p;
        final Method setter;
        final Method getter;
        final Converter c;
        final boolean forward;
        final String srcProp;
        final String pprop;

        private MyPropChangeListener( final Object p, final Method setter, final Method getter, final Converter c, final boolean forward, final String srcProp, final String pprop ) {
            this.p= p;
            this.setter= setter;
            this.getter= getter;
            this.c= c;
            this.forward= forward;
            this.srcProp= srcProp;
            this.pprop= pprop;
        }

        public void propertyChange(PropertyChangeEvent evt) {
                try {
                    if (c == null) {
                        Object oldValue= getter.invoke( p );
                        if ( oldValue==null ) {
                            System.err.println("oldValue is null!!!");
                        }
                        if ( oldValue!=null && oldValue.equals(evt.getNewValue() ) ) return;
                        if ( new Exception().getStackTrace().length > 300 ) {
                            System.err.println("setter: "+setter);
                            System.err.println("old:" + evt.getOldValue() + "  new:"+evt.getNewValue() );
                            System.err.println("this is that bad state, where bindings get us into a infinite loop!");
                        } else {
                            setter.invoke(p, evt.getNewValue());
                        }
                    } else {
                        if ( Thread.currentThread().getStackTrace().length>100 ) {
                            System.err.println("Problem detected in stack trace, circular call indicated by stackTraceLength>100");
                            return; // put an end to it so it doesn't crash
                        }
                        if (forward) {
                            setter.invoke(p, c.convertForward(evt.getNewValue()));
                        } else {
                            setter.invoke(p, c.convertReverse(evt.getNewValue()));
                        }
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } catch (IllegalArgumentException e) {
                    throw new RuntimeException(e);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
        }

    }

    private PropertyChangeListener propListener(final Object p, final Method setter, final Method getter, final Converter c, final boolean forward, final String srcProp, final String pprop ) {
        return new MyPropChangeListener( p, setter, getter, c, forward, srcProp, pprop );
    }
    
    final Map<Object, List<BindingImpl>> implBindingContexts; // these are for controllers to use.
    //final Map<Object, StackTraceElement[] > sources;

    public String capitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1)) &&
                Character.isUpperCase(name.charAt(0))) {
            return name;
        }
        char chars[] = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }


    private void lookupGetterSetter(Object src, String propName, BindingImpl bi) {
        try {
            Class c = src.getClass();
            PropertyDescriptor pd = new PropertyDescriptor(propName, c);
            Method setter = pd.getWriteMethod();
            Method getter = pd.getReadMethod();
            if (src == bi.src) {
                bi.srcSetter = setter;
                bi.srcGetter = getter;
            } else {
                bi.dstSetter = setter;
                bi.dstGetter = getter;
            }
            return;
        } catch (IntrospectionException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * bind the two object properties together using a lightweight binding (introspection and
     * property change listener, rather than beans binding.
     *
     * @param src
     * @param srcProp  name of the property, which may not refer to a path (a.b.c)
     * @param dst
     * @param dstProp  name of the property, which may not refer to a path (a.b.c)
     * @param c bean converter for converting the property.
     * @throws IllegalArgumentException if a property contains a dot.
     */
    public void bind(DomNode src, String srcProp, Object dst, String dstProp, Converter c) {
        if (srcProp.contains(".")) {
            throw new IllegalArgumentException("src property name cannot contain periods: " + srcProp);
        }
        if (dstProp.contains(".")) {
            throw new IllegalArgumentException("dst property name cannot contain periods: " + dstProp);
        }


        BindingImpl bi = new BindingImpl();
        bi.dst = dst;
        bi.src = src;
        bi.srcProp= srcProp;
        bi.dstProp= dstProp;

        lookupGetterSetter(src, srcProp, bi);
        lookupGetterSetter(dst, dstProp, bi);

        //copy the current settings before binding the two.
        try {
            Object val = bi.srcGetter.invoke(src);
            if (c != null) {
                val = c.convertForward(val);
            }
            bi.dstSetter.invoke(dst, val);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException(ex);
        } catch (InvocationTargetException ex) {
            throw new RuntimeException(ex);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        } catch (RuntimeException ex) {
            throw ex;
        }

        // add the listeners that bind.
        PropertyChangeListener srcListener = propListener(dst, bi.dstSetter, bi.dstGetter, c, true, srcProp, dstProp );
        src.addPropertyChangeListener(srcProp, srcListener);
        PropertyChangeListener dstListener = propListener(src, bi.srcSetter, bi.srcGetter, c, false, dstProp, srcProp );

        bi.dstListener = dstListener;
        bi.srcListener = srcListener;

        try {
            Method apcl = dst.getClass().getMethod("addPropertyChangeListener", String.class, PropertyChangeListener.class);
            apcl.invoke(dst, dstProp, dstListener);
        } catch (IllegalAccessException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (NoSuchMethodException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (SecurityException ex) {
            logger.log(Level.SEVERE, null, ex);
        }

        synchronized (implBindingContexts) {
            List<BindingImpl> list = implBindingContexts.get(src);
            if (list == null) {
                list = new ArrayList();
                implBindingContexts.put(src, list);
                //sources.put(src,new Exception().getStackTrace());
                //System.err.println("implBindingContexts.size="+implBindingContexts.size());
            }
            list.add(bi);
        }

    }

    public void unbind(DomNode master) {
        synchronized (implBindingContexts) {
            List<BindingImpl> list = implBindingContexts.get(master);
            if (list == null) {
                return;
            }
//            for ( Entry<Object,List<BindingImpl>> e: implBindingContexts.entrySet() ) {
//                System.err.println("=="+e.getKey()+"==");
//                System.err.println(""+e.getValue().size()+"items: " +e.getValue());
//            }
            for (BindingImpl bi : list) {
                try {
                    Method apcl = bi.dst.getClass().getMethod("removePropertyChangeListener", String.class, PropertyChangeListener.class);
                    apcl.invoke(bi.dst, bi.dstProp, bi.dstListener);
                } catch (IllegalAccessException ex) {
                    logger.log(Level.SEVERE, null, ex);
                } catch (IllegalArgumentException ex) {
                    logger.log(Level.SEVERE, null, ex);
                } catch (InvocationTargetException ex) {
                    logger.log(Level.SEVERE, null, ex);
                } catch (NoSuchMethodException ex) {
                    logger.log(Level.SEVERE, null, ex);
                } catch (SecurityException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
                bi.src.removePropertyChangeListener(bi.srcProp, bi.srcListener);
            }
            list.clear();
            implBindingContexts.remove(master);
            //sources.remove(master); // leave in code for future testing to find leaks.
        }
    }

    public void unbind( DomNode master, String property, Object dst, String dstProp ) {
        synchronized (implBindingContexts) {
            List<BindingImpl> list = implBindingContexts.get(master);
            if (list == null) {
                return;
            }
            List<BindingImpl> list2= new ArrayList(list);
            for (BindingImpl bi : list) {
                if ( bi.srcProp.equals(property) && bi.dst==dst && bi.dstProp.equals(dstProp) ) {
                    try {
                        Method apcl = bi.dst.getClass().getMethod("removePropertyChangeListener", String.class, PropertyChangeListener.class);
                        apcl.invoke(bi.dst, bi.dstProp, bi.dstListener);
                    } catch (IllegalAccessException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    } catch (IllegalArgumentException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    } catch (InvocationTargetException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    } catch (NoSuchMethodException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    } catch (SecurityException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                    bi.src.removePropertyChangeListener(bi.srcProp, bi.srcListener);
                    list2.remove(bi);
                }
            }
            if ( list2.isEmpty() ) {
                implBindingContexts.remove(master);
                //sources.remove(master);
            } else {
                implBindingContexts.put( master, list2 );
            }
        }
    }
}
