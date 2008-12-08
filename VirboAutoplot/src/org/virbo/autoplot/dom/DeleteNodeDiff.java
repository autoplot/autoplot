/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

/**
 *
 * @author jbf
 */
public class DeleteNodeDiff implements Diff {
        
    String propertyName;
    Object node;
    int index;
    
    public DeleteNodeDiff( String propertyName, Object node, int index ) {
        this.propertyName= propertyName;
        this.node= node;
        this.index= index;
    }
            
    public void doDiff(DomNode node) {
        
    }

    public void undoDiff(DomNode node) {
        
    }
    
    public String toString() {
        return "delete "+node + " from "+propertyName+" @ " +index;
    }

}
