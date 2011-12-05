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

    static class Param {
        String name;
        String label; // the label for the variable used in the script
        Object deft;
        String doc;
        char type; // A (String) or F (Double)
    }

    protected static Map<String,Param> getParams( URI uri, ProgressMonitor mon ) throws IOException {
        BufferedReader reader= null;

        File src = DataSetURI.getFile(uri, new NullProgressMonitor());

        Map<String,Param> result= new LinkedHashMap();

        try {
            reader = new LineNumberReader( new BufferedReader( new FileReader(src)) );

            String vnarg= "\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*"; // any variable name  VERIFIED
            String sarg= "\\s*\\'([a-zA-Z_][a-zA-Z0-9_]*)\\'\\s*"; // any variable name  VERIFIED
            String aarg= "\\s*(\\'[^\\']+\\')\\s*"; // any argument
            String farg= "\\s*([0-9\\.\\+-eE]+)\\s*"; // any float variable name

            Pattern p= Pattern.compile( vnarg+"=\\s*getParam\\("+sarg+"\\,"+aarg+"(\\,"+aarg + ")?\\).*" );
            Pattern fp= Pattern.compile(vnarg+"=\\s*getParam\\("+sarg+"\\,"+farg+"(\\,"+aarg + ")?\\).*" );

            String line= reader.readLine();
            while ( line!=null ) {
                Matcher m= p.matcher(line);
                if ( !m.matches() ) {
                    m= fp.matcher(line);
                }

                if ( m.matches() ) {
                    Param parm= new Param();

                    parm.name= m.group(2); // exists in the URI space

                    parm.label= m.group(1);  // exists in the Script space.  Why different.  Shouldn't be allowed, but this is required.

                    parm.doc= m.group(5); // might be null

                    if ( m.group(3)==null ) {
                        System.err.println("error handle");
                    } else {
                        parm.type= m.group(3).startsWith("'") ? 'A' : 'F';
                        String sval= parm.type=='A' ? m.group(3).substring(1,m.group(3).length()-1) : m.group(3);
                        parm.deft= parm.type=='F' ? Double.parseDouble( sval ) : sval;
                    }

                    result.put( parm.name, parm );

                }
                line= reader.readLine();
            }
            reader.close();

        } finally {
            try {
                reader.close();
            } catch (IOException ex) {
                Logger.getLogger(JythonEditorPanel.class.getName()).log(Level.SEVERE, null, ex);
            }
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
                Map<String,Param> po2= getParams( cc.resourceURI, new NullProgressMonitor() );
                for ( String n: po2.keySet() ) {
                    Param parm= po2.get(n);
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
                Map<String,Param> po2= getParams( cc.resourceURI, new NullProgressMonitor() );
                Param pp= po2.get(paramName);
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
                if ( split.scheme.equals("inline") ) {
                    return false;
                }
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

    protected static Map<String,String> getParameters( String surl, ProgressMonitor mon ) throws IOException {
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
    }
    
    ExceptionListener listener;
    
    public void addExeceptionListener( ExceptionListener listener ) {
        this.listener= listener;
    }
}
