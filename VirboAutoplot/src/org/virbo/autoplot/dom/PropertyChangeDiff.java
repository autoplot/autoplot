/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jbf
 */
public class PropertyChangeDiff implements Diff {
    String propertyName;
    Object oldVal;
    Object newVal;

    PropertyChangeDiff( String propertyName, Object oldVal, Object newVal ) {
        this.propertyName= propertyName;
        this.oldVal= oldVal;
        this.newVal= newVal;
    }
    
    public void doDiff(DomNode node) {
        try {
            BeanInfo info = Introspector.getBeanInfo(node.getClass());
            for (PropertyDescriptor pd : info.getPropertyDescriptors()) {
                if ( pd.getName().equals(propertyName) ) {
                    Object oldVal= pd.getReadMethod().invoke(node);
                    if ( !( oldVal==this.oldVal || oldVal.equals(this.oldVal) ) ) {
                        throw new IllegalArgumentException("old value");
                    }
                    pd.getWriteMethod().invoke(node, newVal);
                }
            }
        } catch (IllegalAccessException ex) {
            Logger.getLogger(PropertyChangeDiff.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(PropertyChangeDiff.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(PropertyChangeDiff.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IntrospectionException ex) {
            Logger.getLogger(PropertyChangeDiff.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void undoDiff(DomNode node) {
        try {
            BeanInfo info = Introspector.getBeanInfo(node.getClass());
            for (PropertyDescriptor pd : info.getPropertyDescriptors()) {
                if ( pd.getName().equals(propertyName) ) {
                    Object newVal= pd.getReadMethod().invoke(node);
                    if ( !( newVal==this.newVal || newVal.equals(this.newVal) ) ) {
                        throw new IllegalArgumentException("new value");
                    }
                    pd.getWriteMethod().invoke(node, oldVal);
                }
            }
        } catch (IllegalAccessException ex) {
            Logger.getLogger(PropertyChangeDiff.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(PropertyChangeDiff.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(PropertyChangeDiff.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IntrospectionException ex) {
            Logger.getLogger(PropertyChangeDiff.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public String toString() {
        String s= this.propertyName + " " + oldVal + " to " + newVal;
        if ( s.length()>30 ) {
            s= this.propertyName;
        }
        return s;
    }
}
