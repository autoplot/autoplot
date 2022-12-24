
package org.autoplot.dom;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeListenerProxy;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.LoggerManager;

/**
 * PropertyChangeSupport implementation that provides debugging information, 
 * such as toString.
 * @author jbf
 */
public class DebugPropertyChangeSupport extends PropertyChangeSupport {

    protected static final Logger logger= LoggerManager.getLogger("autoplot.dom");
            
    static long t0= System.currentTimeMillis();
    
    String myBean;
    public long t= System.currentTimeMillis() - t0;
    
    private static final boolean DEBUG= false;
    
    List<String> propNames= new ArrayList();
    String[] propNamesArray= new String[0];
    Map<String,StackTraceElement[]> sources= new HashMap<>();
    Map<String,Long> birthMilli= new HashMap<>();

    public DebugPropertyChangeSupport( Object bean ) {
        super(bean);
        myBean= bean.toString();
    }

    @Override
    public synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
        //TODO: danger--remove this from production code.
        if ( Arrays.asList(getPropertyChangeListeners()).contains( listener ) ) {
            return;
        }
        super.addPropertyChangeListener(listener);
        if ( DEBUG && listener!=null ) {
            final String key= listener.toString();
            //if ( key.startsWith("scaleListener") ) {
            //    System.err.println("+++ add scaleListener");
            //}
            propNames.add( key );
            propNamesArray= propNames.toArray( new String[propNames.size()] );
            sources.put( key, new Exception().getStackTrace() );
            if ( System.currentTimeMillis() - t0 < 50000 ) {
                birthMilli.put( key, 0L );
            } else {
                birthMilli.put( key, System.currentTimeMillis() );
           }
        }

    }

    @Override
    public synchronized void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        //TODO: danger--remove this from production code.
        if ( Arrays.asList(getPropertyChangeListeners()).contains( listener ) ) {
            return;
        }
        super.addPropertyChangeListener(propertyName, listener);
        if ( DEBUG && listener!=null ) {
            final String key= listener.toString()+ " " + propertyName;
            propNames.add( key );
            propNamesArray= propNames.toArray( new String[propNames.size()] );
            sources.put( key, new Exception().getStackTrace() );
            if ( System.currentTimeMillis() - t0 < 50000 ) {
                birthMilli.put( key, 0L );
            } else {
                birthMilli.put( key, System.currentTimeMillis() );
            }
        }
    }

    @Override
    public synchronized void removePropertyChangeListener(PropertyChangeListener listener) {
        super.removePropertyChangeListener(listener);
        if ( DEBUG && listener!=null ) {
            final String key = listener.toString();            
            //if ( key.startsWith("scaleListener") ) {
            //    System.err.println("--- rm scaleListener");
            //}
            propNames.remove(key);
            propNamesArray= propNames.toArray( new String[propNames.size()] );
            sources.remove(key);
            birthMilli.remove(key);
        }
        if ( DEBUG ) printOldListeners();
    }

    @Override
    public synchronized void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        super.removePropertyChangeListener(propertyName, listener);
        //TODO: possible bug: sometimes with TSBs listener is null.
        if ( DEBUG && listener!=null ) {
            final String key= listener.toString()+ " " + propertyName;
            propNames.remove( key );
            propNamesArray= propNames.toArray( new String[propNames.size()] );
            sources.remove( key );
            birthMilli.remove( key );
        }
        if ( DEBUG ) printOldListeners();
    }

    public void printListeners(String propName) {
        List<String> skipSource= Arrays.asList( "org.autoplot.dom.DebugPropertyChangeSupport","org.autoplot.dom.DomNodeController" );        
        PropertyChangeListener[] ll= super.getPropertyChangeListeners(propName);
        for ( PropertyChangeListener l: ll ) {
            if ( DEBUG ) {
                final String key= l.toString()+ " " + propName;
                StackTraceElement[] ee= sources.get( key );
                for ( StackTraceElement e : ee ) {
                    if ( !skipSource.contains((e.getClassName())) ) {
                        System.err.println(e);
                        break;
                    }
                }
            } else {
                System.err.println(l);
            }
        }
    }
    
    public void printListeners() {
        List<String> skipSource= Arrays.asList( "org.autoplot.dom.DebugPropertyChangeSupport","org.autoplot.dom.DomNodeController" );
        PropertyChangeListener[] ll= super.getPropertyChangeListeners();
        for ( PropertyChangeListener l: ll ) {
            if ( DEBUG ) {
                final String key= l.toString();
                StackTraceElement[] ee= sources.get( key );
                for ( StackTraceElement e : ee ) {
                    if ( !skipSource.contains((e.getClassName())) ) {
                        System.err.println(e);
                        break;
                    }
                }

            } else {
                System.err.println(l);
            }
        }
    }
    
    private void printOldListeners() {
//        long tnow= System.currentTimeMillis();
//        for ( Entry<String,Long> e: birthMilli.entrySet() ) {
//            if ( e.getValue()>0 && ( tnow-e.getValue() ) > 20000 ) {
//                StackTraceElement[] sts= sources.get(e.getKey());
//                System.err.println("== "+e.getKey()+" ("+( tnow-e.getValue() ) +"ms) ==");
//                int i=5;
//                for ( StackTraceElement st: sts ) {
//                    if ( i-- == 0 ) break;
//                    System.err.println( st.toString() );
//                }
//            } 
//        }
        }
    
    @Override
    public void firePropertyChange(final PropertyChangeEvent event) {
        try {
            super.firePropertyChange(event); 
        } catch ( ConcurrentModificationException ex ) {
            ex.printStackTrace(); // bug1962
        } catch ( NullPointerException ex ) {
            logger.log(Level.WARNING, "NullPointerException with propertyName {0}, new value={1}.", new Object[]{event.getPropertyName(), event.getNewValue()});
            logger.log( Level.SEVERE, ex.getMessage(), ex );
            //RuntimeException ex1= new NullPointerException( "NullPointerException with propertyName "+event.getPropertyName()+ ", new value=" + event.getNewValue()+".");
            //ex1.initCause(ex);
            //throw ex1;
        }
    }

    @Override
    public String toString() {
        PropertyChangeListener[] listeners= getPropertyChangeListeners();
        StringBuilder result= new StringBuilder(super.toString());
        for (PropertyChangeListener listener : listeners) {
            if (listener instanceof PropertyChangeListenerProxy) {
                PropertyChangeListenerProxy proxy = (PropertyChangeListenerProxy) listener;
                result.append("\n").append(proxy.getListener()).append(" (property ").append(proxy.getPropertyName()).append(")");
            } else {
                result.append("\n").append(listener);
            }
        }
        return result.toString();
    }
    
    /**
     * return a list of the explicit properties we are listening to.
     * @return 
     */
    public synchronized String[] getPropNames() {
        if ( DEBUG==false ) {
            throw new IllegalArgumentException("debug must be turned on for getPropNames");
        }
        return propNames.toArray( new String[propNames.size()] );
    }
    
}
