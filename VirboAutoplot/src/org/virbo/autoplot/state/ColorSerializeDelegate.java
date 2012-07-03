/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.state;

import java.awt.Color;
import java.text.ParseException;
import org.virbo.qstream.SerializeDelegate;

/**
 *
 * @author jbf
 */
public class ColorSerializeDelegate implements SerializeDelegate {

    public ColorSerializeDelegate() {
    }

    public String format(Object o) {
        return String.format( "#%06x",((Color)o).getRGB() & 0xFFFFFF );
        //return "#" + Integer.toHexString(((Color)o).getRGB() & 0xFFFFFF);
    }

    public Object parse(String typeId, String s) throws ParseException {
        return java.awt.Color.decode((String)s);
    }

    public String typeId(Class clas) {
        return "color";
    }

}
