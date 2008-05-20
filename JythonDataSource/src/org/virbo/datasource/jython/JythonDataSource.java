/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource.jython;

import java.io.FileInputStream;
import java.net.URL;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.Py;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;

/**
 *
 * @author jbf
 */
public class JythonDataSource extends AbstractDataSource {

    public JythonDataSource( URL url ) {
        super(url);
    }
    
    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        PythonInterpreter interp =  new PythonInterpreter();
        Py.getAdapter().addPostClass( new PyQDataSetAdapter() );
        
        interp.set( "monitor", mon );
        interp.execfile( JythonDataSource.class.getResource("imports.py").openStream(), "imports.py" );

        interp.execfile( new FileInputStream( super.getFile( new NullProgressMonitor() ) ) );
        
        String expr= super.params.get("arg_0");
        if ( expr==null ) expr="result";
        
        PyObject result= interp.eval( expr );
        
        if ( result instanceof PyList ) {
            return  PyQDataSetAdapter.adaptList((PyList)result);
        } else {
            QDataSet res= (QDataSet) result.__tojava__( QDataSet.class );
            return res;
        }
    }

}
