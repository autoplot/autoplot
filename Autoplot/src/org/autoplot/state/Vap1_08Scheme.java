/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.state;

import org.autoplot.dom.DomNode;
import org.w3c.dom.Element;

/**
 * in vap 1.08, we add the properties:
 * Autoranging property is restored.  In v1.07 no VAP file could have ranges that would be 
 * set after the data was loaded.
 * @author jbf
 */
public class Vap1_08Scheme extends AbstractVapScheme {

    public String getId() {
        return "1.08";
    }

    public boolean resolveProperty(Element element, DomNode node) {
        return false;
    }

}
