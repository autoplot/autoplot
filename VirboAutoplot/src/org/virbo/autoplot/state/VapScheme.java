/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.state;

import org.virbo.autoplot.dom.DomNode;
import org.w3c.dom.Element;

/**
 * interface allowing for handling forward compatibility.
 * @author jbf
 */
public interface VapScheme {
    void addUnresolvedProperty( Element element, DomNode node );
    boolean resolveProperty( Element element, DomNode node );
    Class getClass( String name );
    String getName( Class clas );
    String describeUnresolved();
    String getId();
}
