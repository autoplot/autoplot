/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.state;

import org.autoplot.dom.DomNode;
import org.w3c.dom.Element;

/**
 * in vap1.07, we add the properties:
 *    plots[:].context
 *    plots[:].ticksURI
 * @author jbf
 */
public class Vap1_07Scheme extends AbstractVapScheme {

    public String getId() {
        return "1.07";
    }

    public boolean resolveProperty(Element element, DomNode node) {
        return false;
    }

}
