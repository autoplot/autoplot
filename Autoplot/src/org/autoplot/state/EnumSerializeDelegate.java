/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.state;

import java.beans.PropertyEditor;
import java.text.ParseException;
import org.das2.beans.BeansUtil;
import org.das2.components.propertyeditor.Displayable;
import org.das2.qstream.SerializeDelegate;

/**
 *
 * @author jbf
 */
public class EnumSerializeDelegate implements SerializeDelegate {

    public EnumSerializeDelegate() {
    }

    public String format(Object o) {
        return o.toString();
    }

    public Object parse(String typeId, String description) throws ParseException {
        try {
            Displayable d;
            String sclass = typeId.substring(12);
            PropertyEditor edit = BeansUtil.findEditor(Class.forName(sclass));
            edit.setAsText(description);
            return edit.getValue();
        } catch (ClassNotFoundException ex) {
            throw new ParseException("class not found: "+typeId,0);
        }

    }

    public String typeId(Class clas) {
        return "displayable:"+clas.getCanonicalName();
    }

}
