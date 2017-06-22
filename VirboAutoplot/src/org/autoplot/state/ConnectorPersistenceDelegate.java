/*
 * DatumRangePersistenceDelegate.java
 *
 * Created on August 8, 2007, 10:43 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.autoplot.state;

import java.beans.Encoder;
import java.beans.Expression;
import java.beans.PersistenceDelegate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.autoplot.dom.BindingModel;
import org.autoplot.dom.Connector;

/**
 *
 * @author jbf
 */
public class ConnectorPersistenceDelegate extends PersistenceDelegate {
    
    public ConnectorPersistenceDelegate()  {
    }

    protected Expression instantiate(Object oldInstance, Encoder out) {        
        Connector field= (Connector)oldInstance;
        return new Expression( field, this.getClass(), "newConnector", new Object[] { field.toString() } );
    }
    
    public static Connector newConnector( String description ) {
        Pattern p= Pattern.compile("(.+?) to (.+?)");
        Matcher m= p.matcher(description);
        if ( m.matches() ) {
            Connector c= new Connector( m.group(1), m.group(2) );
            return c;
        } else {
            throw new IllegalArgumentException("Poorly formatted connector: "+description);
        }
    }

    protected void initialize(Class<?> type, Object oldInstance, Object newInstance, Encoder out) {
        super.initialize(type, oldInstance, newInstance, out);
    }
}
