package org.autoplot.state;

import java.beans.DefaultPersistenceDelegate;
import java.beans.Encoder;
import java.beans.Expression;
import java.beans.PropertyEditor;
import org.das2.beans.BeansUtil;

/**
 * PersistenceDelegate for nominal data (e.g. upper, lower)
 * @author jbf
 */
public class EnumPersistenceDelegate extends DefaultPersistenceDelegate {
    
    public EnumPersistenceDelegate() {
        
    }
    
    public Object findInstance( Class enumClass, String name ) {
        PropertyEditor edit= BeansUtil.findEditor(enumClass);
        edit.setAsText(name);
        return edit.getValue();
    }
    
    @Override
    protected Expression instantiate(Object oldInstance, Encoder out) {
        return new Expression( oldInstance, this.getClass(), "findInstance", new Object[] { oldInstance.getClass(), oldInstance.toString() } );
    }

    @Override
    protected void initialize(Class<?> type, Object oldInstance, Object newInstance, Encoder out) {
        super.initialize(type, oldInstance, newInstance, out);
    }

    @Override
    public void writeObject(Object oldInstance, Encoder out) {
        super.writeObject(oldInstance, out);
    }

    @Override
    protected boolean mutatesTo(Object oldInstance, Object newInstance) {
        boolean retValue;
        
        retValue = super.mutatesTo(oldInstance, newInstance);
        return retValue;
    }
    
}
