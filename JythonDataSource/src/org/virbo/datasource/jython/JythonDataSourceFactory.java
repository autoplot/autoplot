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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.Py;
import org.python.core.PyList;
import org.python.core.PyStringMap;
import org.python.parser.PythonGrammar;
import org.python.parser.ReaderCharStream;
import org.python.util.PythonInterpreter;
import org.virbo.datasource.AbstractDataSourceFactory;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.URISplit;
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

        File src = DataSetURI.getFile(url, new NullProgressMonitor());

        URISplit split = URISplit.parse(url.toString());
        Map<String, String> params = URISplit.parseParams(split.params);
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
        List<CompletionContext> result = new ArrayList<CompletionContext>();
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            String ext= cc.resource.toString();
            int i= ext.lastIndexOf(".");
            if ( i!=-1 ) ext= ext.substring(i+1);
            if ( ext.equals(".jyds" ) || ext.equals("jy") || ext.equals("py") ) {
                Map<String, Object> po = getNames( cc.resource, mon);
                for (String n : po.keySet()) {
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, n, this, "arg_0", null, null));
                }
            } else {
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "script=", "the name of the python script to run"));
            }
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            String paramName = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            if ( paramName.equals("script") ) {
                //TODO: filesystem completions.
            }
        }
        return result;
    }

    @Override
    public boolean reject(String surl, ProgressMonitor mon) {
        if (surl.contains("?")) {
            return false;
        } else {
            try {
                File src = DataSetURI.getFile(DataSetURI.getURL(surl), new NullProgressMonitor());
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

    protected static Map<String,String> getParameters( String surl, ProgressMonitor mon ) {
        try {
            File src = DataSetURI.getFile(DataSetURI.getURL(surl), new NullProgressMonitor());
            BufferedReader reader = new BufferedReader(new FileReader(src));
            String s = reader.readLine();

            Pattern assignPattern= Pattern.compile("\\s*([_a-zA-Z][_a-zA-Z0-9]*)\\s*=.*(#(.*))?");
            Pattern defPattern= Pattern.compile("def .*");

            boolean inDef= false;

            Map<String,String> result= new LinkedHashMap<String, String>(); // from ID to description

            boolean haveResult = false;
            while (s != null) {

                if ( inDef==false ) {
                    Matcher defm= defPattern.matcher(s);
                    if ( defm.matches() ) {
                        inDef= true;
                    }
                } else {
                    if ( s.length()>0 && !Character.isWhitespace(s.charAt(0)) ) {
                        Matcher defm= defPattern.matcher(s);
                        inDef=  defm.matches();
                    }
                }

                if ( !inDef ) {
                    Matcher m= assignPattern.matcher(s);
                    if ( m.matches() ) {
                        if ( m.group(3)!=null ) {
                            result.put(m.group(1), m.group(3) );
                        } else {
                            result.put(m.group(1), s );
                        }
                    }
                }

                s = reader.readLine();
            }
            reader.close();
            return result;
        } catch (IOException ex) {
            Logger.getLogger(JythonDataSourceFactory.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }
    
    ExceptionListener listener;
    
    public void addExeceptionListener( ExceptionListener listener ) {
        this.listener= listener;
    }
}
