/*
 * UnitsPersistenceDelegate.java
 *
 * Created on August 8, 2007, 11:04 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.autoplot.state;

import org.das2.datum.Units;
import java.beans.DefaultPersistenceDelegate;
import java.beans.Encoder;
import java.beans.Expression;
import java.beans.PropertyEditor;
import org.das2.beans.BeansUtil;
import org.das2.components.propertyeditor.Displayable;

/**
 *
 * @author jbf
 */
public class EnumPersistenceDelegate extends DefaultPersistenceDelegate {
    
    /** Creates a new instance of UnitsPersistenceDelegate */
    public EnumPersistenceDelegate() {
        
    }
    
    public Object findInstance( Class enumClass, String name ) {
        Displayable d;
        PropertyEditor edit= BeansUtil.findEditor(enumClass);
        edit.setAsText(name);
        return edit.getValue();
    }
    
    protected Expression instantiate(Object oldInstance, Encoder out) {
        Expression retValue;
        
        return new Expression( oldInstance, this.getClass(), "findInstance", new Object[] { oldInstance.getClass(), oldInstance.toString() } );
    }

    protected void initialize(Class<?> type, Object oldInstance, Object newInstance, Encoder out) {
        super.initialize(type, oldInstance, newInstance, out);
    }

    public void writeObject(Object oldInstance, Encoder out) {
        super.writeObject(oldInstance, out);
    }

    protected boolean mutatesTo(Object oldInstance, Object newInstance) {
        boolean retValue;
        
        retValue = super.mutatesTo(oldInstance, newInstance);
        return retValue;
    }
    
}
