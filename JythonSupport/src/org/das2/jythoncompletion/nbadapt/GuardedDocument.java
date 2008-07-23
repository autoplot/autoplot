/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.das2.jythoncompletion.nbadapt;

/**
 *
 * @author jbf
 */
public class GuardedDocument extends BaseDocument {
    public boolean isPosGuarded(int caretOffset) {
        return false;
    }
}
