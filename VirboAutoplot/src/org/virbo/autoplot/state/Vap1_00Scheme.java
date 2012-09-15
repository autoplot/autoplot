/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.state;

import java.text.ParseException;
import java.util.logging.Level;
import org.virbo.autoplot.dom.Axis;
import org.virbo.autoplot.dom.DomNode;
import org.virbo.autoplot.dom.PlotElementStyle;
import org.w3c.dom.Element;

/**
 *
 * @author jbf
 */
public class Vap1_00Scheme extends AbstractVapScheme {

    public String getId() {
        return "1.00";
    }

    public boolean resolveProperty(Element element, DomNode node) {
        String name= element.getAttribute("name");
        Object value;
        try {
            value = SerializeUtil.getLeafNode(element);
        } catch (ParseException ex) {
            org.virbo.autoplot.Util.logger.log(Level.SEVERE, null, ex);
            return false;
        }

        boolean resolved= false;
        if ( node instanceof Axis ) {
            Axis a= (Axis)node;
            if ( name.equals("autorange") ) {
                a.setAutoRange( (Boolean)value );
                resolved= true;
            }
            if ( name.equals("autolabel") ) {
                a.setAutoLabel( (Boolean)value );
                resolved= true;
            }

        }
        return resolved;
    }

    @Override
    public Class getClass(String clasName) {
        Class claz= super.getClass(clasName);
        if ( claz!=null ) return claz;
        if ( clasName.equals("PanelStyle") ) {
            return PlotElementStyle.class;
        } 
        throw new IllegalArgumentException("unrecognized class: "+clasName);
    }

}
