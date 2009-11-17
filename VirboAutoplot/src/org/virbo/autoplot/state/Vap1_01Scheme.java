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
public class Vap1_01Scheme extends AbstractVapScheme {

    public String getId() {
        return "1.01";
    }

    public boolean resolveProperty(Element element, DomNode node) {
        return false; // current version.
    }

}
