
package org.autoplot.dom;

import java.awt.Component;
import java.beans.IntrospectionException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.graph.DasCanvasComponent;
import org.das2.util.LoggerManager;
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
            if ( dst instanceof DomNode ) {
                return src+"."+srcProp+" \u2194\t"+dst+"."+dstProp;
            } else if ( dst instanceof DasCanvasComponent ) {
                return src+"."+srcProp+" \u2194\t\""+((DasCanvasComponent)dst).getDasName()+"\"."+dstProp;
            } else if ( dst instanceof Component ) {
                return src+"."+srcProp+" \u2194\t\""+((Component)dst).getName()+"\"."+dstProp;
            } else {
                return src+"."+srcProp+" \u2194\t\""+dst+"\"."+dstProp;
            }
        }
    }

    /**
     * converter for objects which have a toString/parse pair.  This works for:
     * <ul>
     * <li>DatumRange
     * <li>Datum
     * <li>Color
     * </ul>
     * The convertForward method captures the class of the object.
     */
    public static final Converter toStringConverter= new Converter() {
        
        Class c= null;
        Object instance;
        
        @Override
        public Object convertForward(Object value) {
            instance= value;
            if ( c==null ) {
                c= instance.getClass();
            }
            if ( c==java.awt.Color.class ) {
                return org.das2.util.ColorUtil.nameForColor((java.awt.Color)value);
            } else {
                return value.toString();
            }
        }

        @Override
        public Object convertReverse(Object value) {
            if ( c.isAssignableFrom( org.das2.datum.Datum.class ) ) {
                try {
                    return org.das2.datum.DatumUtil.parse((String)value);
                } catch (ParseException ex) {
                    return instance;
                }
            } else if ( c.isAssignableFrom( org.das2.datum.DatumRange.class ) ) {
                try {
                    return org.das2.datum.DatumRangeUtil.parseTimeRange((String)value);
                } catch (ParseException ex) {
                    return instance;
                }
            } else if ( c.isAssignableFrom( java.awt.Color.class ) ) {
                return org.das2.util.ColorUtil.decodeColor((String)value);
            }
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

        private MyPropChangeListener( final Object p, final Method setter, final Method getter, final Converter c, 
                final boolean forward, final String srcProp, final String pprop ) {
            this.p= p;
            this.setter= setter;
            this.getter= getter;
            this.c= c;
            this.forward= forward;
            this.srcProp= srcProp;
            this.pprop= pprop;
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
                LoggerManager.logPropertyChangeEvent(evt);              
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
                        if ( Thread.currentThread().getStackTrace().length>200 ) {
                            System.err.println("Problem detected in stack trace, circular call indicated by stackTraceLength>200");
                            return; // put an end to it so it doesn't crash
                        }
                        if (forward) {
                            setter.invoke(p, c.convertForward(evt.getNewValue()));
                        } else {
                            setter.invoke(p, c.convertReverse(evt.getNewValue()));
                        }
                    }
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
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
        } catch (IntrospectionException ex) {
            throw new RuntimeException(ex);
            //logger.log(Level.SEVERE, ex.getMessage(), ex);
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

        if ( bi.dstSetter==null ) {
            throw new NullPointerException("unable to find setter for "+dstProp );
        }
        
        //copy the current settings before binding the two.
        try {
            Object val = bi.srcGetter.invoke(src);
            if (c != null) {
                val = c.convertForward(val);
            }
            try {
                bi.dstSetter.invoke(dst, val);
            } catch ( IllegalArgumentException ex ) {
                logger.info("IllegalArgumentException in bind");
            }
        } catch (IllegalArgumentException ex) {
            String msg= String.format( "failed to bind %s.%s to %s.%s", src, srcProp, dst, dstProp );
            throw new RuntimeException(msg,ex);
        } catch (InvocationTargetException | IllegalAccessException | RuntimeException ex) {
            String msg= String.format( "failed to bind %s.%s to %s.%s", src, srcProp, dst, dstProp );
            throw new RuntimeException(msg,ex);
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
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
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
            logger.log(Level.FINE, "adding binding to BindingImpls for {0} size={1} {2}", new Object[]{src, list.size(), src.hashCode()});
        }

    }

    /**
     * return true if the object is bound to something.  Note this is 
     * beyond the vap bindings, and includes the bindings used to implement
     * features like TimeSeriesBrowse.
     * @param node the dom node.
     * @param property the property name.
     * @return true if a binding is found.
     */
    public boolean isBound( Object node, String property) {
        List<BindingImpl> list = implBindingContexts.get(node);
        if (list == null) {
            return false;
        }
        for (BindingImpl bi : list) {
            if ( bi.dst==node && bi.dstProp.equals(property) ) {
                return true;
            } else if ( bi.src==node && bi.srcProp.equals(property) ) {
                return true;
            }
        }
        return false;
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
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
                bi.src.removePropertyChangeListener(bi.srcProp, bi.srcListener);
            }
            list.clear();
            implBindingContexts.remove(master);
            logger.log(Level.FINE, "remove binding to BindingImpls for {0} size={1} {2}", new Object[]{master, list.size(), implBindingContexts.size() });
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
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException ex) {
                        logger.log(Level.SEVERE, ex.getMessage(), ex);
                    }
                    bi.src.removePropertyChangeListener(bi.srcProp, bi.srcListener);
                    list2.remove(bi);
                }
            }
            logger.log(Level.FINE, "remove binding to BindingImpls for {0} size={1} {2}", new Object[]{master, list.size(), implBindingContexts.size() });
            if ( list2.isEmpty() ) {
                implBindingContexts.remove(master);
                //sources.remove(master);
            } else {
                implBindingContexts.put( master, list2 );
            }
        }
    }
    
    @Override
    public String toString() {
        return "== BindingSupport: ==\n"+implBindingContexts.size() ;
    }
    
    /**
     * print the status of all the bindings.
     */
    public void printStatus() {
        int total= 0;
        Map<Object,List<BindingImpl>> copy;
        synchronized (implBindingContexts) {
            copy= new HashMap(implBindingContexts);
            for ( Entry<Object,List<BindingImpl>> e: copy.entrySet() ) {
                e.setValue( new ArrayList(e.getValue()) );
            }
        }
        
        ArrayList keys= new ArrayList( copy.keySet() );
        Collections.sort(keys,new Comparator() {
            @Override
            public int compare(Object o1, Object o2) {
                if ( o1 instanceof DomNode && o2 instanceof DomNode ) { // they are
                    return ((DomNode)o1).getId().compareTo(((DomNode)o2).getId());
                } else {
                    return o1.toString().compareTo(o2.toString());
                }
            }
        });
        
        for ( Object key: keys ) {
            List<BindingImpl> value= copy.get(key);
            int s= value.size();
            System.err.println( "--- "+key+" ("+s+" bindings) ---");
            total+= s;
            for ( BindingImpl l: value ) {
                System.err.println( l );
            }
        }
        System.err.println( "\nBindingSupport contains "+copy.size()+" groups of "+total+" bindings." );
    }
    
    /**
     * return the total number of bindings implemented in this facility.
     * This was introduced to aid in debugging, when trying to identify memory 
     * leaks.  https://sourceforge.net/p/autoplot/bugs/1362/
     * @return the total number of bindings implemented in this facility.
     */
    public int totalBindings() {
        int total= 0;
        Map<Object,List<BindingImpl>> copy;
        synchronized (implBindingContexts) {
            copy= new HashMap(implBindingContexts);
            for ( Entry<Object,List<BindingImpl>> e: copy.entrySet() ) {
                e.setValue( new ArrayList(e.getValue()) );
            }
        }
        for ( Entry<Object,List<BindingImpl>> e: copy.entrySet() ) {
            int s= e.getValue().size();
            total+= s;
        }
        System.err.println( "\nBindingSupport contains "+copy.size()+" groups of "+total+" bindings." );
        return total;
    }
}
