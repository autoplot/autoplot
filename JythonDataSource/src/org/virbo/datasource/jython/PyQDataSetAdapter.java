/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource.jython;

import org.python.core.PyObject;
import org.python.core.adapter.PyObjectAdapter;
import org.virbo.dataset.QDataSet;

/**
 *
 * @author jbf
 */
public class PyQDataSetAdapter implements PyObjectAdapter {

    public boolean canAdapt(Object arg0) {
        return arg0 instanceof QDataSet;
    }

    public PyObject adapt(Object arg0) {
        return new PyQDataSet((QDataSet) arg0);
    }
    
    
}
