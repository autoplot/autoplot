/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.jythonsupport;

import java.io.BufferedReader;
import java.io.File;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.python.core.Py;
import org.python.core.PyException;
import org.python.core.PyFloat;
import org.python.core.PyInteger;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PySystemState;
import org.python.util.InteractiveInterpreter;
import org.python.util.PythonInterpreter;
import org.virbo.datasource.DataSetURI;

/**
 *
 * @author jbf
 */
public class JythonUtil {

    private static final Logger logger= LoggerManager.getLogger("jython");

    /**
     * create an interpreter object configured for Autoplot contexts:
     *   * QDataSets are wrapped so that operators are overloaded.
     *   * a standard set of names are imported.
     *   
     * @param sandbox limit symbols to safe symbols for server.
     * @return PythonInterpreter ready for commands.
     * @throws java.io.IOException
     */
    public static InteractiveInterpreter createInterpreter(boolean sandbox) throws IOException {
        if ( PySystemState.cachedir==null ) {
            System.setProperty( "python.cachedir", System.getProperty("user.home")+"/autoplot_data/pycache" );
        }

        //r.
        ///  http://www.gossamer-threads.com/lists/python/python/697524
        org.python.core.PySystemState pySys = new org.python.core.PySystemState();
        URL jarUrl= InteractiveInterpreter.class.getResource("/glob.py");
        if ( jarUrl!=null ) {
            String jarFile= jarUrl.toString();

            if ( jarFile.startsWith("jar:file:") && jarFile.contains("!") ) {
                int i= jarFile.indexOf("!");
                String jar= jarFile.substring(9,i);
                File ff= new File(jar);
                if ( ff.exists() ) {
                    pySys.path.insert(0, new PyString(jar));
                } else {
                    String f= getLocalJythonLib();
                    pySys.path.insert(0, new PyString( f ));
                }
            } else {
                String f= getLocalJythonLib();
                pySys.path.insert(0, new PyString( f ));
            }
            
        } else {
            logger.warning("Not adding Lib stuff!!!  See https://sourceforge.net/tracker/index.php?func=detail&aid=3134982&group_id=199733&atid=970682");
        }

        InteractiveInterpreter interp = new InteractiveInterpreter( null, pySys );
        
        boolean loadAutoplotStuff= true;
        if ( loadAutoplotStuff ) {
            Py.getAdapter().addPostClass(new PyQDataSetAdapter());
            URL imports= JythonOps.class.getResource("imports.py");
            if ( imports==null ) {
                throw new RuntimeException("unable to locate imports.py on classpath");
            }
            interp.execfile(imports.openStream(), "imports.py");
        }

        return interp;

    }

    private static String getLocalJythonLib() throws IOException {
        File ff2= FileSystem.settings().getLocalCacheDir();
        File ff= new File( ff2.toString() + "/http/autoplot.org/jnlp-lib/jython-lib-2.2.1.jar" );
        if ( ! ff.exists() ) {
            logger.log(Level.WARNING, "looking for {0}, but didn''t find it.", ff);
            logger.warning("doesn't seem like we have the right file, downloading...");
            File f= DataSetURI.getFile( new URL("http://autoplot.org/jnlp-lib/jython-lib-2.2.1.jar"), new NullProgressMonitor() );
            ff= f;
        }
        logger.fine("   ...done");
        return ff.toString();
    }

    /**
     * check the script that it doesn't redefine symbol names like "str"
     * @param code
     * @return true if an err is suspected.
     */
    public static boolean pythonLint( URI uri, List<String> errs ) throws IOException {
        LineNumberReader reader= null;

        File src = DataSetURI.getFile( uri, new NullProgressMonitor());

        try {
            reader = new LineNumberReader( new BufferedReader( new FileReader(src)) );

            String vnarg= "\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*"; // any variable name  VERIFIED

            Pattern assign= Pattern.compile( vnarg+"=.*" );

            InteractiveInterpreter interp= createInterpreter(true);

            String line= reader.readLine();
            while ( line!=null ) {
                Matcher m= assign.matcher(line);
                if ( m.matches() ) {
                    String vname= m.group(1);
                    try {
                        PyObject po= interp.eval(vname);
                        errs.add( "" + reader.getLineNumber() + ": "+ vname + "=" + po.__repr__() );
                    } catch ( PyException ex ) {
                        // this is what we want
                    }
                }
                line= reader.readLine();
            }
            reader.close();

        } finally {
            try {
                if ( reader!=null ) reader.close();
            } catch (IOException ex) {
                Logger.getLogger(JythonUtil.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return errs.size()>0;


    }

    public static class Param {
        public String name;
        public String label; // the label for the variable used in the script
        public Object deft;
        public String doc;
        public List<Object> enums;  // the allowed values
        /**
         * A (String) or F (Double or Integer) or R (URI)
         * Note a string with the values enumerated either T or F is treated as a boolean.
         */
        public char type;
    }

    /**
     * scrape through the script looking for documentation declarations
     * returns an array, possibly containing:
     *   LABEL few words
     *   TITLE sentence
     *   DESCRIPTION short paragraph
     */
     public static Map<String,String> getDocumentation( BufferedReader reader ) throws IOException {

         Map<String,String> result= new HashMap<String, String>();
         String s= reader.readLine();
         Pattern p= Pattern.compile("#\\s*([a-zA-Z]+)\\s*:(.*)");
         while ( s!=null ) {
             Matcher m= p.matcher(s);
             if ( m.matches() ) {
                 String prop= m.group(1).toUpperCase();
                 String value= m.group(2).trim();
                 if ( prop.equals("LABEL") || prop.equals("TITLE") || prop.equals("DESCRIPTION" ) ) {
                     result.put( prop, value );
                 }
            }
            s=  reader.readLine();
        }

        reader.close();
        return result;
        
     }

    /**
     * <p>scrape through the script looking for getParam calls.  These are executed, and we
     * get labels and infer types from the defaults.  For example,<br>
     * <tt>getParam( 'foo', 3.0 )</tt> will always return a real and<br>
     * <tt>getParam( 'foo', 3 )</tt> will always return an integer.<br>
     * 
     * Other examples include:<br>
     * <tt>getParam( 'foo', 'A', '', [ 'A', 'B' ] )</tt> constrains the values to A or B<br>
     * </p>
     * <p>
     * Thinking about the future, people have asked that human-ready labels be fixed to list selections. 
     * Constraints should be added to number parameters to specify ranges.  And last it would
     * be nice to specify when a parameter is ignored by the script (dA is not used is mode B is active).
     * <br>
     * <tt>getParam( 'foo', 3, '', { 'min':0, 'max':10 } )</tt> might (Not implemented) constrain ranges<br>
     * <tt>getParam( 'sc', 'A', '', [ 'A', 'B' ], { 'A':'big one', 'B':'little one' } )</tt> might (Not implemented) allow labels<br>
     * <tt>getParam( 'foo', 'dA', '', [], { '_ignoreIf':'sc==B' } )</tt> might (Not implemented) allow groups to be disabled when not active<br>
     * </p>
     * <p>A few things the Autoplot script developer must know:
     * <ul>
     * <li>getParam calls can only contain literals, and each must be executable as if it were the only line of code.  This may be relaxed in the future.
     * <li>the entire getParam line must be on one line.  This too may be relaxed.
     * </ul>
     * </p>
     * 
     * @param reader A reader that has an open Jython file.
     * @return list of parameter descriptions, in the order they were encountered in the file.
     * @throws IOException
     */
    public static List<Param> getGetParams( BufferedReader reader ) throws IOException {
        String s= reader.readLine();

        StringBuilder build= new StringBuilder();
        Pattern getParamPattern= Pattern.compile("\\s*([_a-zA-Z][_a-zA-Z0-9]*)\\s*=\\s*getParam\\(\\.*");
        build.append( "sort_=[]\n");
        while (s != null) {
           int ic= s.indexOf("#");
           if ( ic>-1 ) s= s.substring(0,ic);
           Matcher m= getParamPattern.matcher(s);
           if ( m.matches() || s.contains("getParam") ) {
               build.append(s).append("\n");
               int i= s.indexOf("=");
               String v= s.substring(0,i).trim();
               build.append("sort_.append( \'").append(v).append( "\')\n");
           }
           s = reader.readLine();
        }

        reader.close();
        
        String params= build.toString();

        String myCheat= "def getParam( name, deflt, doc='', enums=[] ):\n  return [ name, deflt, doc, enums ]\n";

        String prog= myCheat + params ;

        PythonInterpreter interp;
        try {
            interp= new PythonInterpreter();
            interp.exec(prog);
        } catch ( PyException ex ) {
            return new ArrayList();
        }

        PyList sort= (PyList) interp.get( "sort_" );

        List<Param> result= new ArrayList();
        for ( int i=0; i<sort.__len__(); i++ ) {

            PyList oo= (PyList) interp.get( (String)sort.get(i));
            Param p= new Param();
            p.label= (String) sort.get(i);   // should be name in the script
            if ( p.label.startsWith("__") ) continue;  // __doc__, __main__ symbols defined by Jython.
            p.name= oo.__getitem__(0).toString(); // name in the URI
            p.deft= oo.__getitem__(1);
            p.doc= oo.__getitem__(2).toString();
            if (  oo.__getitem__(3) instanceof PyList ) {
                PyList pyList= ((PyList)oo.__getitem__(3));
                List<Object> enums= new ArrayList(pyList.size());
                for ( int j=0; j<pyList.size(); j++ ) {
                    enums.add(j,pyList.get(j));
                }
                p.enums= enums;
            }

            if ( p.name.equals("resourceUri") ) {
                p.name= "resourceURI"; //  I will regret allowing for this sloppiness...
            }
            if ( p.name.equals("resourceURI") ) {
                p.type= 'R';
                p.deft= p.deft.toString();
            } else {
                if ( p.deft instanceof String ) {
                    p.type= 'A';
                    p.deft= p.deft.toString();
                } else if ( p.deft instanceof PyInteger ) {
                    p.type= 'F';
                    p.deft= ((PyInteger)p.deft).__tojava__(int.class);
                } else if ( p.deft instanceof PyFloat ) {
                    p.type= 'F';
                    p.deft= ((PyFloat)p.deft).__tojava__(double.class);
                }
            }
            result.add(p);
        }

        return result;
    }

    /**
     * scrape script for local variables, looking for assignments.
     * @param script
     * @return
     */
    public static Map getLocals( BufferedReader reader ) throws IOException {
        
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

    /**
     * return python code that is equivalent, except it has no side-effects like plotting.
     * This code is not exact, for example (a,b)= (1,2) is not supported.
     * @param reader input to read.
     * @return the script as a string, with side-effects removed.
     */
    public static String removeSideEffects( BufferedReader reader ) throws IOException {

        String s = reader.readLine();

        Pattern assignPattern= Pattern.compile("\\s*([_a-zA-Z][_a-zA-Z0-9]*)\\s*=.*(#(.*))?");
        Pattern defPattern= Pattern.compile("def .*");
        Pattern importPattern1= Pattern.compile("from .*");
        Pattern importPattern2= Pattern.compile("import .*");

        boolean inDef= false;

        StringBuilder result= new StringBuilder();

        boolean haveResult = false;
        while (s != null) {

            boolean sideEffect= true;

            if ( inDef==false ) {
                Matcher defm= defPattern.matcher(s);
                if ( defm.matches() ) {
                    inDef= true;
                    sideEffect= false;
                }
            } else {
                if ( s.length()>0 && !Character.isWhitespace(s.charAt(0)) ) {
                    Matcher defm= defPattern.matcher(s);
                    inDef=  defm.matches();
                    if ( inDef ) sideEffect= false; //TODO: what about blank line, this isn't an "END"
                }
            }

            if ( !inDef ) {
                Matcher m= assignPattern.matcher(s);
                if ( m.matches() ) {
                    sideEffect= false;
                } else if ( importPattern1.matcher(s).matches() ) {
                    sideEffect= false;
                } else if ( importPattern2.matcher(s).matches() ) {
                    sideEffect= false;
                }
            }

            if ( !sideEffect ) {
                result.append( s ).append("\n");
            }

            s = reader.readLine();
        }
        reader.close();
        return result.toString();
    }
}
