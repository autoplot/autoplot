
package org.autoplot.state;

import java.awt.Color;
import java.text.ParseException;
import org.das2.util.ColorUtil;
import org.das2.qstream.SerializeDelegate;

/**
 * Format the color as RGB or ARGB, like so: #000000.
 * This can be parsed using ColorUtil.decodeColor, names like "burntSienna" 
 * can be used in vaps as well.
 * @author jbf
 */
public class ColorSerializeDelegate implements SerializeDelegate {

    public ColorSerializeDelegate() {
    }

    @Override
    public String format(Object o) {
        return ColorUtil.encodeColor((Color)o);
    }

    @Override
    public Object parse(String typeId, String s) throws ParseException {
        return ColorUtil.decodeColor(s);
    }

    @Override
    public String typeId(Class clas) {
        return "color";
    }

}
