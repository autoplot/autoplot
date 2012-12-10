/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jbf
 */
public class PropertyChangeDiff implements Diff {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.dom");

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
                //throw new IllegalArgumentException("old value");
                //let's be loosey goosey on this because sometimes setting one property resets another
            }
            DomUtil.setPropertyValue( node, propertyName, newVal );
        } catch (IllegalAccessException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            logger.log(Level.SEVERE, null, ex);
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

    @Override
    public String toString() {
        String s = this.propertyName + " " + oldVal + " to " + newVal;
        if (s.length() > 30) {
            if ( newVal instanceof Boolean ) {
                s = this.propertyName + "=" + newVal;
            } else {
                s = this.propertyName;
            }
        }
        return s;
    }

    public String getLabel() {
        return toString();
    }

    public String getDescription() {
        return this.propertyName + " " + oldVal + " \u2192 " + newVal;
    }
}
