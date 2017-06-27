/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.state;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.qstream.SerializeDelegate;

/**
 *
 * @author jbf
 */
public class TypeSafeEnumSerializeDelegate implements SerializeDelegate {

    protected static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.dom");
    
    public TypeSafeEnumSerializeDelegate() {
    }

    public String format(Object o) {
        return o.toString();
    }

    public Object parse(String typeId, String description) throws ParseException {
        try {
            String sclass = typeId.substring(5);
            if ( sclass.startsWith("org.virbo.autoplot") ) {
                sclass= "org.autoplot"+sclass.substring(18);
            }
            Class type = Class.forName(sclass);

            if (!Modifier.isPublic(type.getModifiers()))
                throw new IllegalArgumentException("Could not instantiate instance of non-public class: " + sclass );
            for (Field field : type.getFields()) {
                int mod = field.getModifiers();
                if (Modifier.isPublic(mod) && Modifier.isStatic(mod) && Modifier.isFinal(mod) && (type == field.getDeclaringClass())) {
                    try {
                        if ( description.equals( field.get(null).toString() ) ) return field.get(null);
                    } catch (IllegalAccessException exception) {
                        throw new IllegalArgumentException("Could not get value of the field: " + field, exception);
                    }
                }
            }

        } catch (ClassNotFoundException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }

        throw new ParseException("unable to find type-safe enum field for "+description,0 );

    }

    public String typeId(Class clas) {
        String id= clas.getName();
        if ( id.equals("org.autoplot.MouseModuleType") ) {
            id= "org.virbo.autoplot.MouseModuleType";
        } else if ( id.equals("org.autoplot.RenderType")  ) {
            id= "org.virbo.autoplot.RenderType";
        }
        return "enum:"+id;
    }

}
