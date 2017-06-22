/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.spase;

import org.w3c.dom.Node;

/**
 *
 * @author jbf
 */

/**
 * provide separation from DOM objects by decorating them.
 */
public class TreeNode {
    Node domNode;
    TreeNode( Node domNode ) {
        this.domNode= domNode;
    }
    public String toString() {
        String val;
        if ( isLeaf() ) {
            val= domNode.getNodeName() + "= \"" + domNode.getFirstChild().getNodeValue()+"\"";
        } else {
            val= domNode.getNodeName();
        }
        return val;
    }
    public Node getDomNode() {
        return domNode;
    }

    boolean isLeaf() {
        return domNode.getChildNodes().getLength()==1 && domNode.getFirstChild().getNodeType()==Node.TEXT_NODE;
    }
}