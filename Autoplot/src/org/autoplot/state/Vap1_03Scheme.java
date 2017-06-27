/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.state;

import org.autoplot.dom.DomNode;
import org.w3c.dom.Element;

/**
 *
 * @author jbf
 */
public class Vap1_03Scheme extends AbstractVapScheme {

    public String getId() {
        return "1.03";
    }

    public boolean resolveProperty(Element element, DomNode node) {
        return false;
    }

}
