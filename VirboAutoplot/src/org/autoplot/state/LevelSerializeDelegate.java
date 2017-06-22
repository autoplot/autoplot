/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.state;

import java.text.ParseException;
import java.util.logging.Level;
import org.das2.qstream.SerializeDelegate;

/**
 *
 * @author jbf
 */
class LevelSerializeDelegate implements SerializeDelegate {

    public LevelSerializeDelegate() {
    }

    @Override
    public String format(Object o) {
        return o.toString();
    }

    @Override
    public Object parse(String typeId, String s) throws ParseException {
        return Level.parse(s);
    }

    @Override
    public String typeId(Class clas) {
        return "level";
    }
    
}
