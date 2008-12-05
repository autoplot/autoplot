/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 *
 * @author jbf
 */
public class DebugPropertyChangeSupport extends PropertyChangeSupport {

    public DebugPropertyChangeSupport( Object bean ) {
        super(bean);
    }
    
    @Override
    public String toString() {
        PropertyChangeListener[] listeners= getPropertyChangeListeners();
        StringBuffer result= new StringBuffer(super.toString());
        for ( int i=0; i<listeners.length; i++ ) {
            result.append("\n"+listeners[i]);
        }
        return result.toString();
    }
    
}
