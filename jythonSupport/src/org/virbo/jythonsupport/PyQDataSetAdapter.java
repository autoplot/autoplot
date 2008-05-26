/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.jythonsupport;

import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.core.adapter.PyObjectAdapter;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.QDataSet;

/**
 *
 * @author jbf
 */
public class PyQDataSetAdapter implements PyObjectAdapter {

    public boolean canAdapt(Object arg0) {
        return ( arg0 instanceof QDataSet )
                || ( arg0 instanceof PyList );
    }

    public PyObject adapt(Object arg0) {
        if ( arg0 instanceof PyList ) {
            PyList p= (PyList)arg0;
            return new PyQDataSet( adaptList(p) );
        } else {
            return new PyQDataSet((QDataSet) arg0);
        }
    }
    
    protected static QDataSet adaptList( PyList p ) {
        double[] j= new double[ p.size() ];        
            for ( int i=0; i<p.size(); i++ ) j[i]= ((Number)p.get(i)).doubleValue();
            QDataSet q= DDataSet.wrap( j );
            return q;
    }
    
}
