/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

/**
 * super class for containing diffs.
 * @author jbf
 */
public interface Diff {
    void doDiff( DomNode node );
    void undoDiff( DomNode node );
    
}
