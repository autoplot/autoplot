/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.jythonsupport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.StringReader;
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
import org.virbo.datasource.AutoplotSettings;
import org.virbo.datasource.DataSetURI;

/**
 * Utilities to support Jython scripting.
 * @author jbf
 */
public class JythonUtil {

    private static final Logger logger= LoggerManager.getLogger("jython");

    /**
     * create an interpreter object configured for Autoplot contexts:
     *   * QDataSets are wrapped so that operators are overloaded.
     *   * a standard set of names are imported.
     * This also adds things to the python search path so imports will find them.
     * 
     * @param sandbox limit symbols to safe symbols for server.
     * @return PythonInterpreter ready for commands.
     * @throws java.io.IOException
     */
    public static InteractiveInterpreter createInterpreter(boolean sandbox) throws IOException {
        if ( PySystemState.cachedir==null ) {
            System.setProperty( "python.cachedir", System.getProperty("user.home")+"/autoplot_data/pycache" );
        }
        ///  http://www.gossamer-threads.com/lists/python/python/697524
        org.python.core.PySystemState pySys = new org.python.core.PySystemState();
        
        String[] loadClasses= new String[] { "glob.py", "autoplot.py", "autoplotapp.py" }; // these must be in the root of the interpretter search path.
        for ( String pysrc: loadClasses ) {
            if ( !pysrc.equals("glob.py") ) {
                String f= getLocalJythonAutoplotLib();
                if ( !pySys.path.contains( new PyString(f) ) ) {
                    pySys.path.insert(0,new PyString(f) );
                }
            } else {
                URL jarUrl= InteractiveInterpreter.class.getResource("/"+pysrc);
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
                    } else if ( jarUrl.toString().startsWith("file:/") ) {
                        File f= new File( jarUrl.getFile() );  //TODO: test on Windows
                        pySys.path.insert(0, new PyString( f.getParent() ));
                    } else {
                        String f= getLocalJythonLib();
                        pySys.path.insert(0, new PyString( f ));
                    }

                } else {
                    logger.log(Level.WARNING, "Couldn''t find jar containing {0}.  See https://sourceforge.net/p/autoplot/bugs/576/", pysrc);
                }
            }
        }

        InteractiveInterpreter interp = new InteractiveInterpreter( null, pySys );
        
        boolean loadAutoplotStuff= true;
        if ( loadAutoplotStuff ) {
            Py.getAdapter().addPostClass(new PyQDataSetAdapter());
            if ( Util.isLegacyImports() ) {
                URL imports= JythonOps.class.getResource("imports.py");
                if ( imports==null ) {
                    throw new RuntimeException("unable to locate imports.py on classpath");
                }
                interp.execfile(imports.openStream(), "imports.py");
            }
        }

        return interp;

    }

    /**
     * transfer the contents of in to out.  in and out are closed after the operation.
     * //TODO: other implementations of this exist...
     * @param in
     * @param out
     * @throws IOException 
     */
    private static void transferStream( InputStream in, OutputStream out ) throws IOException {
        byte[] buf= new byte[2048];
        int n=0;
        try {
            n= in.read(buf);
            while ( n>-1 ) {
                out.write(buf,0,n);
                n= in.read(buf);
            }
        } finally {
            out.close();
            in.close();
        }
    }
    
    /**
     * ensure that the file has a parent writable directory.
     * @param file
     * @return true if the folder could be made.
     */
    private static boolean makeHomeFor( File file ) {
        File f= file.getParentFile();
        if ( !f.exists() ) {
            return f.mkdirs();
        } else {
            return true;
        }
    }
    
    /**
     * copy everything out to autoplot_data/jython without going to web again.  The old
     * code showed issues where autoplot.org could not be resolved.
     * @return the item to add to the python search path.
     * @throws IOException 
     */
    private static String getLocalJythonLib() throws IOException {
        File ff2= new File( AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA ) );
        File ff3= new File( ff2.toString() + "/jython" );
        File ff4= new File( ff2.toString() + "/jython/zlib.py" );
        if ( ff4.exists() ) {
            return ff3.toString();
        }
        if ( !ff3.exists() ) {
            if ( !ff3.mkdirs() ) {
                throw new IOException("Unable to mkdirs "+ff3);
            }
        }
        InputStream inn= JythonUtil.class.getResourceAsStream("/pylisting.txt");
        BufferedReader r= new BufferedReader( new InputStreamReader(inn) );
        String s= r.readLine();
        while ( s!=null ) {
            File ff5= new File( ff3, s );
            logger.log(Level.FINER, "copy to local folder python code: {0}", s);
            InputStream in= JythonUtil.class.getResourceAsStream("/"+s);
            if ( s.contains("/") ) {
                if ( !makeHomeFor( ff5 ) ) {
                    throw new IOException("Unable to makeHomeFor "+ff5);
                }
            }
            FileOutputStream out= new FileOutputStream( ff5 ); // TODO: test this on Windows.
            try {
                transferStream(in,out);
            } finally {
                out.close();
                in.close();
                new File( ff3, s ).setReadOnly();
                new File( ff3, s ).setWritable( true, true );
            }
            s= r.readLine();
        }
        r.close();
        logger.fine("   ...done");
        return ff3.toString();
    }
        
    /**
     * copy the two python files specific to Autoplot into the user's autoplot_data/jython folder.
     * @return the item to add to the python search path.
     * @throws IOException 
     */
    private static String getLocalJythonAutoplotLib() throws IOException {
        File ff2= new File( AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA ) );
        File ff3= new File( ff2.toString() + "/jython" );
        File ff4= new File( ff3, "autoplot.py" );
        if ( ! ff4.exists() ) {
            logger.log(Level.FINE, "looking for {0}, but didn''t find it.", ff3);
            logger.log(Level.FINE,"doesn't seem like we have the right file, downloading...");
            if ( !ff3.exists() ) {
                if ( !ff3.mkdir() ) {
                    throw new IOException("Unable to mkdir "+ff3);
                }
            }
            String[] ss= new String[] { "autoplot.py", "autoplotapp.py" };
            for ( String s: ss ) {
                InputStream in= JythonUtil.class.getResourceAsStream("/"+s);
                FileOutputStream out= new FileOutputStream( new File( ff3, s ) );
                transferStream(in,out);
                out.close();
                in.close();
                new File( ff3, s ).setReadOnly();
                new File( ff3, s ).setWritable( true, true );
            }
        }
        logger.fine("   ...done");
        return ff3.toString();
    }
        
    /**
     * check the script that it doesn't redefine symbol names like "str"
     * @param code
     * @return true if an err is suspected.
     */
    public static boolean pythonLint( URI uri, List<String> errs ) throws IOException {
        LineNumberReader reader;

        File src = DataSetURI.getFile( uri, new NullProgressMonitor());
        reader = new LineNumberReader( new BufferedReader( new FileReader(src)) );
        
        try {

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

        } finally {
            reader.close();
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
         * T (TimeRange), A (String), F (Double or Integer), or R (URI)
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
     * @param reader A reader that has an open Jython file.  This will close the reader!
     * @return list of parameter descriptions, in the order they were encountered in the file.
     * @throws IOException
     */
    public static List<Param> getGetParams( BufferedReader reader ) throws IOException {
        
        String s;
        StringBuilder build= new StringBuilder();
        
        try {
            s= reader.readLine();

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
        } finally {
            reader.close();
        }
        
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
            } else if ( p.name.equals("timerange") ) {
                p.type= 'T';
                p.deft= p.deft.toString();
            } else {
                if ( p.deft instanceof String ) {
                    p.type= 'A';
                    p.deft= p.deft.toString();
                } else if ( p.deft instanceof PyString ) {
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
     * This code is not exact, for example (a,b)= (1,2) is not supported.  This 
     * code is run to support completions.
     * @param reader input to read.
     * @return the script as a string, with side-effects removed.
     */
    public static String removeSideEffects( String eval ) {
        BufferedReader reader= new BufferedReader( new StringReader( eval ) ) ;
        StringBuilder result= new StringBuilder();
        try {
            String s = reader.readLine();

            Pattern assignPattern= Pattern.compile("\\s*([_a-zA-Z][_a-zA-Z0-9]*)\\s*=.*(#(.*))?");
            Pattern defPattern= Pattern.compile("def .*");
            Pattern importPattern1= Pattern.compile("from .*");
            Pattern importPattern2= Pattern.compile("import .*");

            boolean inDef= false;

            boolean haveResult = false;
            while (s != null) {

                int comment= s.indexOf("#");
                if ( comment>-1 ) {
                    s= s.substring(0,comment);
                }
                
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
                    if ( inDef && s.trim().equals("pass") ) { // syntax error otherwise.
                        sideEffect= false;
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
        } catch ( IOException ex ) {
            logger.log( Level.WARNING, null, ex );
        } finally {
            try {
                reader.close();
            } catch ( IOException ex ) {
                logger.log( Level.WARNING, null, ex );
            }
        }
        return result.toString();
    }
}
