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

/**
 *
 * @author jbf
 */
public class BindingPersistenceDelegate extends PersistenceDelegate {
    
    public BindingPersistenceDelegate()  {
    }

    protected Expression instantiate(Object oldInstance, Encoder out) {        
        BindingModel field= (BindingModel)oldInstance;
        return new Expression( field, this.getClass(), "newBindingModel", new Object[] { field.toString() } );
    }
    
    public static BindingModel newBindingModel( String description ) {
        Pattern p= Pattern.compile("(.+?)\\.(.+?) +to +(.+?)\\.(.+?) +\\((.+)\\)");
        Matcher m= p.matcher(description);
        if ( m.matches() ) {
            BindingModel bm= new BindingModel( m.group(5), m.group(1), m.group(2), m.group(3), m.group(4) );
            return bm;
        } else {
            throw new IllegalArgumentException("Poorly formatted binding: "+description);
        }
    }

    protected void initialize(Class<?> type, Object oldInstance, Object newInstance, Encoder out) {
        super.initialize(type, oldInstance, newInstance, out);
    }
}
