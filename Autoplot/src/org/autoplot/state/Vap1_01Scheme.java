/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.state;

import org.autoplot.dom.DomNode;
import org.autoplot.dom.PlotElement;
import org.autoplot.dom.PlotElementStyle;
import org.w3c.dom.Element;

/**
 *
 * @author jbf
 */
public class Vap1_01Scheme extends AbstractVapScheme {

    public String getId() {
        return "1.01";
    }

    public boolean resolveProperty(Element element, DomNode node) {
        return false;
    }

    @Override
    public Class getClass(String clasName) {
        Class claz= super.getClass(clasName);
        if ( claz!=null ) return claz;
        if ( clasName.equals("PanelStyle") ) {
            return PlotElementStyle.class;
        } else if ( clasName.equals("Panel") ) {
            return PlotElement.class;
        }
        throw new IllegalArgumentException("unrecognized class: "+clasName);
    }


}
