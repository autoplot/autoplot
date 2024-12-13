
package org.autoplot.dom;

/**
 * super class for containing diffs.
 * @author jbf
 */
public interface Diff {
    /**
     * perform the difference on the node
     * @param node 
     */
    void doDiff( DomNode node );

    /**
     * perform the reverse of the difference
     * @param node 
     */
    void undoDiff( DomNode node );
    
    /**
     * name of the affected property, or comma-separated property names.  This
     * may include the path to the node, such as canvas.rows[1].left.
     * @return name of the affected property
     */
    String propertyName();
    
    /**
     * a label for the difference, to appear in lists.
     * @return 
     */
    String getLabel();
    
    /**
     * a one-line description of the difference, suitable for tool tips or labels.
     * @return 
     */
    String getDescription();
}
