/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.state;

import java.beans.Encoder;
import java.beans.Expression;
import java.beans.PersistenceDelegate;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * http://weblogs.java.net/blog/malenkov/archive/2006/08/how_to_encode_t.html
 * @author jbf
 */
public class TypeSafeEnumPersistenceDelegate extends PersistenceDelegate {
    protected boolean mutatesTo( Object oldInstance, Object newInstance ) {
        return oldInstance == newInstance;
    }

    protected Expression instantiate( Object oldInstance, Encoder out ) {
        Class type = oldInstance.getClass();
        if ( !Modifier.isPublic( type.getModifiers() ) )
            throw new IllegalArgumentException( "Could not instantiate instance of non-public class: " + oldInstance );

        for ( Field field : type.getFields() ) {
            int mod = field.getModifiers();
            if ( Modifier.isPublic( mod ) && Modifier.isStatic( mod ) && Modifier.isFinal( mod ) && ( type == field.getDeclaringClass() ) ) {
                try {
                    if ( oldInstance == field.get( null ) )
                        return new Expression( oldInstance, field, "get", new Object[]{null} );
                } catch ( IllegalAccessException exception ) {
                    throw new IllegalArgumentException( "Could not get value of the field: " + field, exception );
                }
            }
        }
        throw new IllegalArgumentException( "Could not instantiate value: " + oldInstance );
    }
}
