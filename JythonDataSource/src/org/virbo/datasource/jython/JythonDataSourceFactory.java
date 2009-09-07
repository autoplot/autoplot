/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource.jython;

import java.beans.ExceptionListener;
import java.io.BufferedReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.virbo.jythonsupport.PyQDataSet;
import org.virbo.jythonsupport.PyQDataSetAdapter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.Py;
import org.python.core.PyList;
import org.python.core.PyStringMap;
import org.python.util.PythonInterpreter;
import org.virbo.datasource.AbstractDataSourceFactory;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.URLSplit;
import org.virbo.jythonsupport.JythonOps;

/**
 *
 * @author jbf
 */
public class JythonDataSourceFactory extends AbstractDataSourceFactory {

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        JythonDataSource result = new JythonDataSource(uri,this);
        return result;
    }

    private Map<String, Object> getNames(URL url, ProgressMonitor mon) throws Exception {
        PythonInterpreter interp = new PythonInterpreter();
        Py.getAdapter().addPostClass(new PyQDataSetAdapter());

        interp.set("monitor", mon);
        interp.execfile(JythonOps.class.getResource("imports.py").openStream(), "imports.py");

        File src = DataSetURL.getFile(url, new NullProgressMonitor());

        URLSplit split = URLSplit.parse(url.toString());
        Map<String, String> params = URLSplit.parseParams(split.params);
        try {
            interp.exec("params=dict()");
            for (String s : params.keySet()) {
                if (!s.equals("arg_0")) {
                    interp.exec("params['" + s + "']=" + params.get(s));
                }
            }

            interp.execfile(new FileInputStream(src));

            PyStringMap map = ((PyStringMap) interp.getLocals());
            PyList list = map.keys();

            HashMap result = new HashMap();

            for (int i = 0; i < list.__len__(); i++) {
                String key = (String) list.get(i);
                Object o = map.get(Py.newString(key));
                if (o instanceof PyQDataSet) {
                    result.put(key, o);
                }
            }
            return result;
        } finally {
            mon.finished();
        }


    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        Map<String, Object> po = getNames( cc.resource, mon);
        List<CompletionContext> result = new ArrayList<CompletionContext>();
        for (String n : po.keySet()) {
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, n, this, "arg_0", null, null));
        }
        return result;
    }

    @Override
    public boolean reject(String surl, ProgressMonitor mon) {
        if (surl.contains("?")) {
            return false;
        } else {
            try {
                File src = DataSetURL.getFile(DataSetURL.getURL(surl), new NullProgressMonitor());
                BufferedReader reader = new BufferedReader(new FileReader(src));
                String s = reader.readLine();
                boolean haveResult = false;
                while (s != null) {
                    if (s.trim().startsWith("result")) {
                        haveResult = true;
                        break;
                    }
                    if (s.trim().startsWith("data")) {
                        haveResult = true;
                        break;
                    }
                    s = reader.readLine();
                }
                reader.close();
                return !haveResult;
            } catch (IOException ex) {
                Logger.getLogger(JythonDataSourceFactory.class.getName()).log(Level.SEVERE, null, ex);
                return true;
            }
        }

    }
    
    
    ExceptionListener listener;
    
    public void addExeceptionListener( ExceptionListener listener ) {
        this.listener= listener;
    }
}
