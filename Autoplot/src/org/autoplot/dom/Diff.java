/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.dom;

/**
 * super class for containing diffs.
 * @author jbf
 */
public interface Diff {
    void doDiff( DomNode node );
    void undoDiff( DomNode node );
    /**
     * name of the affected property, or comma-separated property names.  This
     * may include the path to the node, such as canvas.rows[1].left.
     * @return
     */
    String propertyName();
    String getLabel();
    String getDescription();
}
