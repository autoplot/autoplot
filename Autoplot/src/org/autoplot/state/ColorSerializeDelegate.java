/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.state;

import java.awt.Color;
import java.text.ParseException;
import org.das2.graph.ColorUtil;
import org.das2.qstream.SerializeDelegate;

/**
 *
 * @author jbf
 */
public class ColorSerializeDelegate implements SerializeDelegate {

    public ColorSerializeDelegate() {
    }

    public String format(Object o) {
        return ColorUtil.encodeColor((Color)o);
    }

    public Object parse(String typeId, String s) throws ParseException {
        return ColorUtil.decodeColor(s);
    }

    public String typeId(Class clas) {
        return "color";
    }

}
