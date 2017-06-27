/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.state;

import org.autoplot.dom.BindingModel;
import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.qstream.SerializeDelegate;

/**
 *
 * @author jbf
 */
public class BindingModelSerializeDelegate implements SerializeDelegate {

    public BindingModelSerializeDelegate() {
    }

    public String format(Object o) {
        return o.toString();
    }

    public Object parse(String typeId, String s) throws ParseException {
        Pattern p= Pattern.compile("(.+?)\\.(.+?) +to +(.+?)\\.(.+?) +\\((.+)\\)");
        Matcher m= p.matcher(s);
        if ( m.matches() ) {
            BindingModel bm= new BindingModel( m.group(5), m.group(1), m.group(2), m.group(3), m.group(4) );
            return bm;
        } else {
            throw new IllegalArgumentException("Poorly formatted binding: "+s);
        }

    }

    public String typeId(Class clas) {
        return "propertyBinding";
    }

}
