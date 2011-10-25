/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.das2.jythoncompletion.nbadapt;

import javax.swing.text.AbstractDocument;
import javax.swing.text.Element;


/**
 * This dummy class is used to minimize changes to Netbeans code.  It will never
 * be instantiated.
 * @author jbf
 */
public class BaseDocument extends AbstractDocument {

    public BaseDocument() {
        super(null);
    }

    public void atomicLock() {
        
    }

    public void atomicUnlock() {
        
    }

    
    @Override
    public Element getDefaultRootElement() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Element getParagraphElement(int pos) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
