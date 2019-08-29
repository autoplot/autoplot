/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.state;

import java.awt.Color;
import java.text.ParseException;
import org.das2.util.ColorUtil;
import org.das2.qstream.SerializeDelegate;

/**
 * Format the color as RGB or ARGB, like so: #000000.
 * This can be parsed using ColorUtil.decodeColor, do names like "burntSienna" 
 * can be used in vaps as well.
 * @author jbf
 */
public class ColorSerializeDelegate implements SerializeDelegate {

    public ColorSerializeDelegate() {
    }

    public String format(Object o) {
        Color color= (Color)o;
        if ( color.getAlpha()<255 ) {
            return "#" + Integer.toHexString(color.getRGB());
        } else {
            return "#" + Integer.toHexString(color.getRGB() & 0xFFFFFF);
            //return "#" + Integer.toHexString( color.getAlpha() ) + Integer.toHexString(color.getRGB() & 0xFFFFFF);
        }
        //return ColorUtil.encodeColor((Color)o);
    }

    public Object parse(String typeId, String s) throws ParseException {
        return ColorUtil.decodeColor(s);
    }

    public String typeId(Class clas) {
        return "color";
    }

}
