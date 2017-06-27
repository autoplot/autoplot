/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.state;

import org.autoplot.dom.DomNode;
import org.w3c.dom.Element;

/**
 * interface allowing for handling forward compatibility.  This is really
 * simplified, and really only serves to rename properties.  (By design it should
 * handle more complex transformations, but this didn't work, and we use xslt
 * instead.)
 * 
 * @author jbf
 */
public interface VapScheme {
    public void addUnresolvedProperty( Element element, DomNode node , Exception exception);
    boolean resolveProperty( Element element, DomNode node );
    Class getClass( String name );
    String getName( Class clas );
    String describeUnresolved();
    String getId();
}
