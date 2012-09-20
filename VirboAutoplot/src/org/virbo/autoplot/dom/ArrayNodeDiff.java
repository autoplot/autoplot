/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.beans.BeanInfo;
import java.beans.IndexedPropertyDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.virbo.autoplot.LogNames;

/**
 * represent and implement array actions like insert, delete and move node.
 * @author jbf
 */
public class ArrayNodeDiff implements Diff {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger(LogNames.AUTOPLOT_DOM);

    String propertyName;
    Object node;
    int index;
    int toIndex;

    public enum Action { Insert, Delete, Move }

    Action action;
    
    protected ArrayNodeDiff( String propertyName, Action action, Object node, int index ) {
        this.propertyName= propertyName;
        this.action= action;
        this.node= node;
        this.index= index;
    }

    protected ArrayNodeDiff( String propertyName, Action action, Object node, int index, int toIndex ) {
        this.propertyName= propertyName;
        this.action= action;
        this.node= node;
        this.index= index;
        this.toIndex= toIndex;
    }
    /**
     * encapsulate the ugly introspection stuff.  Creates a new array with
     * the element deleted.
     * @param o an array of some type.
     * @param index
     * @return
     */
    static Object deleteElement( Object o, int index ) {
        Class c= o.getClass();
        if ( !c.isArray() ) throw new IllegalArgumentException("expected an array: "+o );
        final int length = Array.getLength(o);
        Object result= Array.newInstance( c.getComponentType(),length-1);
        for ( int i=0; i<index; i++ ) {
            Array.set( result, i, Array.get(o, i) );
        }
        for ( int i=index; i<length-1; i++ ) {
            Array.set( result, i, Array.get(o, i+1) );
        }
        return result;
    }

    /**
     * encapsulate the ugly introspection stuff.  Creates a new array with
     * the element added.
     * @param o an array of some type.
     * @param element
     * @param index
     * @return
     */
    static Object insertElement( Object o, Object element, int index ) {
        Class c= o.getClass();
        if ( !c.isArray() ) throw new IllegalArgumentException("expected an array: "+o );
        final int length = Array.getLength(o);
        Object result= Array.newInstance(c.getComponentType(),length+1);
        for ( int i=0; i<index; i++ ) {
            Array.set( result, i, Array.get(o, i) );
        }
        Array.set( result, index, element );
        for ( int i=index+1; i<length+1; i++ ) {
            Array.set( result, i, Array.get(o, i-1) );
        }
        return result;
    }


    private static void doAction( DomNode node, String propertyName, Object element, Action action, int arg0, int arg1 ) {
        try {
            BeanInfo info = Introspector.getBeanInfo(node.getClass());
            PropertyDescriptor pd=null;
            for (PropertyDescriptor pd1 : info.getPropertyDescriptors()) {
                if (pd1.getName().equals(propertyName)) {
                    pd= pd1;
                    break;
                }
            }
            if ( pd==null ) throw new IllegalArgumentException("failed to find property "+propertyName + " in "+node);
            if ( !( pd instanceof IndexedPropertyDescriptor ) ) throw new IllegalArgumentException("expected indexed property");
            IndexedPropertyDescriptor ipd= (IndexedPropertyDescriptor)pd;

            Object array= ipd.getReadMethod().invoke(node);
            Object newArray;
            if ( action==Action.Delete ) {
                newArray= deleteElement( array, arg0 );
            } else if ( action==Action.Insert ) {
                newArray= insertElement( array, element, arg0 );
            } else if ( action==Action.Move ) {
                newArray= deleteElement( array, arg0 );
                if ( arg1>arg0 ) arg1--;
                newArray= insertElement( array, element, arg1 );
                
            } else {
                throw new IllegalArgumentException("unimplemented action: "+action);
            }
            ipd.getWriteMethod().invoke(node, newArray);

        } catch (IllegalAccessException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (IntrospectionException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    public void doDiff( DomNode node ) {
        doAction( node, this.propertyName, this.node, this.action, this.index, this.toIndex );
    }

    public void undoDiff(DomNode node) {
        if ( action==Action.Delete ) {
            doAction( node, this.propertyName, this.node, Action.Insert, this.index, this.toIndex );
        } else if ( action==Action.Insert ) {
            doAction( node, this.propertyName, this.node, Action.Delete, this.index, this.toIndex );
        } else if ( action==Action.Move ) {
            doAction( node, this.propertyName, this.node, Action.Move, this.toIndex, this.index );
        }
    }

    public String propertyName() {
        return propertyName;
    }

    public Action getAction() {
        return action;
    }

    public Object getNode() {
        return node;
    }

    public String toString() {
        if ( action==Action.Delete ) {
            return "delete "+node + " from "+propertyName+" @ " +index;
        } else if ( action==Action.Insert ) {
            return "insert "+node + " into "+propertyName+" @ " +index;
        } else if ( action==Action.Move ) {
            return "move "+node + "."+ propertyName + " from " +index +" to "+ toIndex;
        } else {
            return super.toString();
        }
    }

    public String getLabel() {
        return toString();
    }

    public String getDescription() {
        return toString();
    }

}
