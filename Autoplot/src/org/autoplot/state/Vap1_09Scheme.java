/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.state;

import org.autoplot.dom.DomNode;
import org.w3c.dom.Element;

/**
 * in vap 1.09, we add the properties:<ul>
 * <li>scale property which can be bound.  Adding it breaks old Autoplots.
 * </ul>
 * @author jbf
 */
public class Vap1_09Scheme extends AbstractVapScheme {

    public String getId() {
        return "1.09";
    }

    public boolean resolveProperty(Element element, DomNode node) {
        return false;
    }

}
