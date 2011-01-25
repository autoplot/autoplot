/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.state;

import org.virbo.autoplot.dom.DomNode;
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
        boolean resolved= false;
        return resolved;
    }

    @Override
    public Class getClass(String clasName) {
        Class claz= super.getClass(clasName);
        if ( claz!=null ) return claz;
        throw new IllegalArgumentException("unrecognized class: "+clasName);
    }

}
