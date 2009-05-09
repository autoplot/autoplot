/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

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

    PropertyChangeDiff(String propertyName, Object oldVal, Object newVal) {
        this.propertyName = propertyName;
        this.oldVal = oldVal;
        this.newVal = newVal;
    }

    private static void doSet(DomNode node, String propertyName, Object oldVal1, Object newVal) {
        try {
            Object oldVal =  DomUtil.getPropertyValue(node, propertyName);
            if (!(oldVal == oldVal1 || oldVal.equals(oldVal1))) {
                throw new IllegalArgumentException("old value");
            }
            DomUtil.setPropertyValue( node, propertyName, newVal );
        } catch (IllegalAccessException ex) {
            Logger.getLogger(PropertyChangeDiff.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(PropertyChangeDiff.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(PropertyChangeDiff.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void doDiff(DomNode node) {
        doSet(node, propertyName, oldVal, newVal);
    }

    public void undoDiff(DomNode node) {
        doSet(node, propertyName, newVal, oldVal);
    }

    public String propertyName() {
        return propertyName;
    }

    public String toString() {
        String s = this.propertyName + " " + oldVal + " to " + newVal;
        if (s.length() > 30) {
            s = this.propertyName;
        }
        return s;
    }
}
