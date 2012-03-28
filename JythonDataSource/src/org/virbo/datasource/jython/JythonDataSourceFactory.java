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
import java.io.LineNumberReader;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.Py;
import org.python.core.PyList;
import org.python.core.PyStringMap;
import org.python.util.PythonInterpreter;
import org.virbo.datasource.AbstractDataSourceFactory;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.URISplit;
import org.virbo.jythonsupport.JythonOps;
import org.virbo.jythonsupport.JythonUtil;

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

    private Map<String, Object> getNames(URI uri, ProgressMonitor mon) throws Exception {
        PythonInterpreter interp = new PythonInterpreter();
        Py.getAdapter().addPostClass(new PyQDataSetAdapter());

        interp.set("monitor", mon);
        interp.execfile(JythonOps.class.getResource("imports.py").openStream(), "imports.py");

        File src = DataSetURI.getFile(uri, new NullProgressMonitor());

        URISplit split = URISplit.parse(uri);
        Map<String, String> params = URISplit.parseParams(split.params);
        try {
            interp.exec("params=dict()");
            for ( Entry<String,String> e : params.entrySet()) {
                String s= e.getKey();
                if (!s.equals("arg_0")) {
                    interp.exec("params['" + s + "']=" + e.getValue() );
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

    protected static Map<String,JythonUtil.Param> getParams( URI uri, ProgressMonitor mon ) throws IOException {

        URISplit split= URISplit.parse(uri);
        Map<String,String> params= URISplit.parseParams(split.params);
        String furi;
        if ( params.containsKey("script") ) {
            furi= params.get("script");
        } else {
            furi= split.resourceUri.toString();
        }

        File src = DataSetURI.getFile(furi, mon );

        List<JythonUtil.Param> r2= JythonUtil.getGetParams( new BufferedReader( new FileReader(src) ) );

        Map<String,JythonUtil.Param> result= new LinkedHashMap();

        for ( JythonUtil.Param r : r2 ) {
            result.put( r.name, r );
        }

        return result;

    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        List<CompletionContext> result = new ArrayList<CompletionContext>();
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            String ext= DataSetURI.fromUri(cc.resourceURI);
            int i= ext.lastIndexOf(".");
            if ( i!=-1 ) ext= ext.substring(i+1);
            if ( ext.equals("jyds" ) || ext.equals("jy") || ext.equals("py") ) {
                Map<String, Object> po = getNames( cc.resourceURI, mon);
                for (String n : po.keySet()) {
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, n, this, "arg_0", null, null));
                }
                Map<String,JythonUtil.Param> po2= getParams( cc.resourceURI, new NullProgressMonitor() );
                for ( String n: po2.keySet() ) {
                    JythonUtil.Param parm= po2.get(n);
                    if ( parm.doc==null ) parm.doc="";
                    if ( !parm.name.equals(parm.label) ) {
                        parm.doc+= " (named "+parm.label+" in the script)";
                    }
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, n+"=", n + " default is '"+ po2.get(n).deft + "'", po2.get(n).doc ) );
                }

            } else {
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "script=", "the name of the python script to run"));
            }
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            String paramName = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            if ( paramName.equals("script") ) {
                //TODO: filesystem completions.
            } else {
                Map<String,JythonUtil.Param> po2= getParams( cc.resourceURI, new NullProgressMonitor() );
                JythonUtil.Param pp= po2.get(paramName);
                if ( pp!=null ) {
                    if ( pp.deft instanceof Number ) {
                        result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, String.valueOf(pp.deft), paramName + " default is '"+ pp.deft + "'", pp.doc ) );
                    } else {
                        result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, String.format( "'%s'", pp.deft ), paramName + " default is '"+ pp.deft + "'", pp.doc ) );
                    }
                }
            }
        }
        return result;
    }

    @Override
    public boolean reject(String surl, ProgressMonitor mon) {
        if (surl.contains("?")) {
            URISplit split= URISplit.parse(surl);
            if ( split.params.length()>0 ) {
                return false;
            } else {
                return true;
            }
        } else {
            try {
                URISplit split= URISplit.parse(surl);
                if ( split.scheme!=null && split.scheme.equals("inline") ) {
                    return false;
                }
                URL url= DataSetURI.getURL(surl);
                if ( url==null ) {
                    return true;
                }

                File src = DataSetURI.getFile( url, new NullProgressMonitor() );
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

    /**
     * Scrape through the Jython script looking for assignments.  Ideally, this would just be a list of plottable
     * result parameters, but that's impossible without actually running the script.
     * @param surl
     * @param mon
     * @return
     * @throws IOException
     */
    protected static Map<String,String> getResultParameters( String surl, ProgressMonitor mon ) throws IOException {
        File src = DataSetURI.getFile(DataSetURI.getURL(surl),mon);
        BufferedReader reader = new BufferedReader(new FileReader(src));
        String s = reader.readLine();

        Pattern assignPattern= Pattern.compile("\\s*([_a-zA-Z][_a-zA-Z0-9]*)\\s*=(.*)(#(.*))?");
        Pattern defPattern= Pattern.compile("def .*");

        boolean inDef= false;

        Map<String,String> result= new LinkedHashMap<String, String>(); // from ID to description

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
                    String rhs= m.group(2);
                    if ( rhs.contains("getParam(") ) {
                        // reject
                    } else {
                        if ( m.group(4)!=null ) {
                            result.put(m.group(1), m.group(4) );
                        } else {
                            result.put(m.group(1), s );
                        }
                    }
                }
            }

            s = reader.readLine();
        }
        reader.close();
        return result;
    }
    
    ExceptionListener listener;
    
    public void addExeceptionListener( ExceptionListener listener ) {
        this.listener= listener;
    }
}
