/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.state;

import org.autoplot.dom.Connector;
import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.qstream.SerializeDelegate;

/**
 *
 * @author jbf
 */
public class ConnectorSerializeDelegate implements SerializeDelegate {

    public ConnectorSerializeDelegate() {
    }

    public String format(Object o) {
        return o.toString();
    }

    public Object parse(String typeId, String description) throws ParseException {
        Pattern p= Pattern.compile("(.+?) to (.+?)");
        Matcher m= p.matcher(description);
        if ( m.matches() ) {
            Connector c= new Connector( m.group(1), m.group(2) );
            return c;
        } else {
            throw new IllegalArgumentException("Poorly formatted connector: "+description);
        }

    }

    public String typeId(Class clas) {
        return "connector";
    }

}
