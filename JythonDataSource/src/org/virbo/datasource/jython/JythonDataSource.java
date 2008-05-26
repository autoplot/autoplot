/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource.jython;

import org.virbo.jythonsupport.PyQDataSetAdapter;
import java.io.FileInputStream;
import java.net.URL;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.Py;
import org.python.core.PyException;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.jythonsupport.JythonOps;

/**
 *
 * @author jbf
 */
public class JythonDataSource extends AbstractDataSource {

    public JythonDataSource(URL url) {
        super(url);
    }

    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        PythonInterpreter interp = new PythonInterpreter();
        Py.getAdapter().addPostClass(new PyQDataSetAdapter());

        interp.set("monitor", mon);
        interp.execfile(JythonDataSource.class.getResource("imports.py").openStream(), "imports.py");

        PyException causedBy=null;
        try {
            
            try {
                interp.execfile(new FileInputStream(super.getFile(new NullProgressMonitor())));
            } catch ( PyException ex ) {
                causedBy= ex;
            }
            String expr= super.getURL();
            int i= expr.indexOf("?");
            if ( i>-1 ) {
                expr= expr.substring(i+1);
            } else {
                expr= "result";
            }
            PyObject result = interp.eval(expr);

            QDataSet res;
            if (result instanceof PyList) {
                res = JythonOps.coerce((PyList) result);
            } else {
                res = (QDataSet) result.__tojava__(QDataSet.class);
            }

            return res;
            
        } catch (PyException ex) {
            
            throw new RuntimeException("PyException: " + ex + "\ncaused by:\n"+causedBy );
        }

    }
}
