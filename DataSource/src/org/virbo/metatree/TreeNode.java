/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.metatree;

/**
 *
 * @author jbf
 */
public interface TreeNode {

    int childCount();

    Object getChild(int i);

    boolean isLeaf();

    String toString();

}
